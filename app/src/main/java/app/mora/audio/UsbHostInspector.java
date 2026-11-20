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
            java.util.List<UsbAudioDescriptorParser.ClockSource> clocks =
                UsbAudioDescriptorParser.findPlaybackClockSources(raw);
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
        int currentLength = connection.controlTransfer(requestType, getCur, value, index,
            current, current.length, 1000);
        if (currentLength == 4) {
            out.append("\n  Current clock: ").append(readU32(current, 0)).append(" Hz");
        } else {
            out.append("\n  GET_CUR tidak didukung/gagal (result=").append(currentLength).append(')');
        }

        byte[] range = new byte[2 + (maxRanges * 12)];
        int transferred = connection.controlTransfer(requestType, getRange, value, index,
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
