package app.mora.audio;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.util.Map;

/** Read-only USB descriptor inspection. Does not claim endpoint ownership or stream audio. */
final class UsbHostInspector {
    static UsbDevice findAudioStreamingDevice(UsbManager manager) {
        if (manager == null) return null;
        for (UsbDevice device : manager.getDeviceList().values()) {
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface intf = device.getInterface(i);
                if (intf.getInterfaceClass() == 1 && intf.getInterfaceSubclass() == 2) return device;
            }
        }
        return null;
    }

    /** Opens the permitted device briefly, reads raw descriptors, then closes it immediately. */
    static String inspectRawDescriptors(UsbManager manager, UsbDevice device) {
        if (manager == null || device == null) return "USB manager/perangkat tidak tersedia.";
        if (!manager.hasPermission(device)) return "Izin USB belum diberikan. Raw descriptor tidak dibaca.";
        UsbDeviceConnection connection = null;
        try {
            connection = manager.openDevice(device);
            if (connection == null) return "Gagal membuka perangkat USB untuk membaca descriptor.";
            byte[] raw = connection.getRawDescriptors();
            StringBuilder report = new StringBuilder(UsbAudioDescriptorParser.parse(raw));
            java.util.Map<Integer, Integer> selectedClockPins = new java.util.HashMap<>();
            java.util.Set<Integer> queriedSelectors = new java.util.HashSet<>();
            boolean selectorHeaderWritten = false;
            // Resolve selectors in order so inactive nested branches are never queried.
            for (int pass = 0; pass < 256; pass++) {
                java.util.List<UsbAudioDescriptorParser.ClockSelector> selectors =
                    UsbAudioDescriptorParser.findPlaybackClockSelectors(raw, selectedClockPins);
                boolean attemptedSelector = false;
                for (UsbAudioDescriptorParser.ClockSelector selector : selectors) {
                    if (!queriedSelectors.add(selector.entityId)) continue;
                    attemptedSelector = true;
                    if (!selectorHeaderWritten) {
                        report.append("\n\n=== UAC2 CLOCK SELECTOR (GET_CUR ONLY) ===");
                        selectorHeaderWritten = true;
                    }
                    report.append("\nClock Selector ID=").append(selector.entityId)
                        .append(", inputs=").append(selector.inputClockIds.length);
                    if (selector.controlAccess != 1 && selector.controlAccess != 3) {
                        report.append(" [control tidak readable]");
                        continue;
                    }
                    byte[] currentPin = new byte[1];
                    int index = (selector.entityId << 8) | (selector.interfaceNumber & 0xff);
                    int result = safeControlTransfer(connection, 0xA1, 0x81, 0x0100, index,
                        currentPin, 1, 1000);
                    if (result != 1) {
                        report.append(" [GET_CUR gagal/tidak didukung: ").append(result).append(']');
                        continue;
                    }
                    int pin = currentPin[0] & 0xff;
                    if (pin < 1 || pin > selector.inputClockIds.length) {
                        report.append(" [GET_CUR mengembalikan pin di luar rentang: ").append(pin).append(']');
                        continue;
                    }
                    selectedClockPins.put(selector.entityId, pin);
                    report.append(" [pin aktif=").append(pin)
                        .append(", Clock Entity ID=").append(selector.inputClockIds[pin - 1])
                        .append(']');
                }
                if (!attemptedSelector) break;
            }
            java.util.List<UsbAudioDescriptorParser.ClockSource> clocks =
                UsbAudioDescriptorParser.findPlaybackClockSources(raw, selectedClockPins);
            if (!clocks.isEmpty()) {
                report.append("\n\n=== UAC2 CLOCK FREQUENCY (READ ONLY) ===");
                for (UsbAudioDescriptorParser.ClockSource clock : clocks) {
                    report.append("\nClock Source ID=").append(clock.entityId)
                        .append(", AC interface=").append(clock.interfaceNumber);
                    readClockFrequency(connection, clock, report);
                }
            }
            return report.toString();
        } catch (RuntimeException error) {
            return "Gagal membaca raw USB descriptor: " + error.getClass().getSimpleName()
                + ": " + String.valueOf(error.getMessage());
        } finally {
            if (connection != null) {
                try { connection.close(); } catch (RuntimeException ignored) { }
            }
        }
    }

    /**
     * Reads UAC2 clock frequency GET_RANGE/GET_CUR only. No SET request is ever sent.
     * A bounded range buffer prevents malformed devices from causing unbounded allocation.
     */
    private static void readClockFrequency(UsbDeviceConnection connection,
            UsbAudioDescriptorParser.ClockSource clock, StringBuilder out) {
        final int maxRanges = 32;
        final int requestType = 0xA1; // Device-to-host, class, interface
        final int getCur = 0x81;
        final int getRange = 0x82;
        final int samplingFrequencyControl = 0x01;
        final int value = samplingFrequencyControl << 8;
        final int index = (clock.entityId << 8) | (clock.interfaceNumber & 0xff);

        byte[] current = new byte[4];
        int currentLength = safeControlTransfer(connection, requestType, getCur, value, index,
            current, current.length, 1000);
        if (currentLength == 4) {
            out.append("\n  Current clock: ").append(readU32(current, 0)).append(" Hz");
        } else {
            out.append("\n  GET_CUR tidak didukung/gagal (result=").append(currentLength).append(')');
        }

        byte[] range = new byte[2 + (maxRanges * 12)];
        int transferred = safeControlTransfer(connection, requestType, getRange, value, index,
            range, range.length, 1500);
        if (transferred < 2) {
            out.append("\n  GET_RANGE tidak didukung/gagal (result=").append(transferred).append(')');
            return;
        }
        int declaredRanges = readU16(range, 0);
        int availableRanges = Math.min(declaredRanges, Math.max(0, (transferred - 2) / 12));
        out.append("\n  GET_RANGE: ").append(declaredRanges).append(" subrange(s)");
        for (int i = 0; i < availableRanges; i++) {
            int base = 2 + (i * 12);
            long min = readU32(range, base);
            long max = readU32(range, base + 4);
            long resolution = readU32(range, base + 8);
            out.append("\n    ").append(min);
            if (max != min) out.append("..").append(max);
            out.append(" Hz");
            if (resolution != 0) out.append(" (step ").append(resolution).append(" Hz)");
        }
        if (declaredRanges > availableRanges) {
            out.append("\n  Range list truncated/unavailable: parsed ")
                .append(availableRanges).append(" of ").append(declaredRanges);
        }
    }

    private static int safeControlTransfer(UsbDeviceConnection connection, int requestType,
            int request, int value, int index, byte[] buffer, int length, int timeoutMs) {
        try {
            return connection.controlTransfer(requestType, request, value, index,
                buffer, length, timeoutMs);
        } catch (RuntimeException error) {
            // One unsupported/malformed device request must not abort the entire scan.
            return -1;
        }
    }

    private static int readU16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static long readU32(byte[] bytes, int offset) {
        return ((long) bytes[offset] & 0xffL)
            | (((long) bytes[offset + 1] & 0xffL) << 8)
            | (((long) bytes[offset + 2] & 0xffL) << 16)
            | (((long) bytes[offset + 3] & 0xffL) << 24);
    }

    static String inspect(UsbManager manager) {
        if (manager == null) return "USB Host tidak tersedia pada perangkat ini.";
        Map<String, UsbDevice> devices = manager.getDeviceList();
        if (devices == null || devices.isEmpty()) {
            return "Tidak ada perangkat USB terhubung. Sambungkan DAC melalui OTG lalu periksa kembali.";
        }

        StringBuilder out = new StringBuilder("=== USB HOST / DESCRIPTOR SCAN ===\n");
        for (UsbDevice device : devices.values()) {
            out.append("\nPerangkat: ").append(safe(device.getProductName()))
                .append("\nVendor ID: ").append(String.format("0x%04X", device.getVendorId()))
                .append("  Product ID: ").append(String.format("0x%04X", device.getProductId()))
                .append("\nDevice class: ").append(device.getDeviceClass())
                .append("  Subclass: ").append(device.getDeviceSubclass())
                .append("  Interfaces: ").append(device.getInterfaceCount()).append('\n');

            boolean audioStreaming = false;
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface intf = device.getInterface(i);
                int cls = intf.getInterfaceClass();
                int sub = intf.getInterfaceSubclass();
                if (cls == 1 && sub == 2) audioStreaming = true;
                out.append("  Interface ").append(i)
                    .append(": class=").append(cls)
                    .append(" subclass=").append(sub)
                    .append(" protocol=").append(intf.getInterfaceProtocol())
                    .append(" alternateSetting=").append(intf.getAlternateSetting())
                    .append(" endpoints=").append(intf.getEndpointCount()).append('\n');
                for (int e = 0; e < intf.getEndpointCount(); e++) {
                    UsbEndpoint ep = intf.getEndpoint(e);
                    out.append("    Endpoint address=").append(String.format("0x%02X", ep.getAddress()))
                        .append(" direction=").append(ep.getDirection() == 0 ? "OUT" : "IN")
                        .append(" type=").append(transferName(ep.getType()))
                        .append(" maxPacket=").append(ep.getMaxPacketSize())
                        .append(" interval=").append(ep.getInterval()).append('\n');
                }
            }
            out.append("USB AudioStreaming interface: ").append(audioStreaming ? "terdeteksi" : "tidak terdeteksi")
                .append("\nAkses USB: ").append(manager.hasPermission(device) ? "izin sudah ada" : "izin belum diminta")
                .append('\n');
        }
        out.append("\nCatatan: scan descriptor hanya tahap identifikasi. Belum membuka interface/endpoint,")
            .append(" belum melakukan streaming PCM, dan bukan bukti mode exclusive atau bit-perfect.");
        return out.toString();
    }

    private static String transferName(int type) {
        switch (type) {
            case UsbConstants.USB_ENDPOINT_XFER_ISOC: return "isochronous";
            case UsbConstants.USB_ENDPOINT_XFER_BULK: return "bulk";
            case UsbConstants.USB_ENDPOINT_XFER_INT: return "interrupt";
            default: return "control/unknown";
        }
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "(nama tidak tersedia)" : value;
    }

    private UsbHostInspector() { }
}
