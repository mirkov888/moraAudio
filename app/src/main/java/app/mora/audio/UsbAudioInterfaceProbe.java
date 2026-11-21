package app.mora.audio;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-forcing UAC playback interface diagnostic.
 *
 * Opens the USB device, records the playback interface/alternate-setting topology, then makes
 * one claimInterface(base, false) attempt. It never detaches a kernel driver, changes an
 * alternate setting, submits endpoint requests, or transfers PCM.
 */
final class UsbAudioInterfaceProbe {
    private UsbAudioInterfaceProbe() { }

    static String probe(UsbManager manager, UsbDevice device) {
        long startedAt = android.os.SystemClock.elapsedRealtime();
        String result = probeInternal(manager, device);
        return result + "\nDurasi probe: "
            + (android.os.SystemClock.elapsedRealtime() - startedAt) + " ms";
    }

    private static String probeInternal(UsbManager manager, UsbDevice device) {
        StringBuilder report = new StringBuilder("=== USB AUDIO INTERFACE OWNERSHIP PROBE ===\n");
        if (manager == null || device == null) return report.append("USB manager/perangkat tidak tersedia.").toString();
        if (!manager.hasPermission(device)) return report.append("Izin USB belum tersedia.").toString();

        UsbDeviceConnection connection = null;
        UsbInterface playbackBase = null;
        int playbackId = -1;
        List<UsbInterface> playbackAlternates = new ArrayList<>();
        report.append("Perangkat: ").append(String.valueOf(device.getProductName())).append('\n')
            .append("VID:PID: ").append(String.format(java.util.Locale.ROOT, "%04X:%04X",
                device.getVendorId(), device.getProductId())).append('\n')
            .append("Device name: ").append(device.getDeviceName()).append('\n')
            .append("Interface total: ").append(device.getInterfaceCount()).append('\n');

        try {
            // First collect every AudioStreaming interface. Android commonly enumerates alt 0
            // before alt 1..N, so determine the playback interface number before grouping;
            // otherwise alt 0 is accidentally skipped while playbackId is still -1.
            List<UsbInterface> audioStreamingInterfaces = new ArrayList<>();
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface candidate = device.getInterface(i);
                if (candidate.getInterfaceClass() != 1 || candidate.getInterfaceSubclass() != 2) continue;
                audioStreamingInterfaces.add(candidate);
                boolean hasIsoOut = false;
                for (int e = 0; e < candidate.getEndpointCount(); e++) {
                    UsbEndpoint endpoint = candidate.getEndpoint(e);
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT
                            && endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_ISOC) {
                        hasIsoOut = true;
                        break;
                    }
                }
                report.append("AS candidate: id=").append(candidate.getId())
                    .append(", alt=").append(candidate.getAlternateSetting())
                    .append(", endpoints=").append(candidate.getEndpointCount())
                    .append(", isochronous OUT=").append(hasIsoOut).append('\n');
                if (playbackId == -1 && hasIsoOut) playbackId = candidate.getId();
            }

            if (playbackId == -1) {
                return report.append("Tidak ditemukan interface AudioStreaming playback dengan endpoint isochronous OUT.").toString();
            }
            // Second pass includes alt 0 even when it appeared before the first playback alt.
            for (UsbInterface candidate : audioStreamingInterfaces) {
                if (candidate.getId() == playbackId) playbackAlternates.add(candidate);
            }
            if (playbackAlternates.isEmpty()) {
                return report.append("Interface playback terdeteksi tetapi alternate setting tidak dapat dikelompokkan.").toString();
            }
            for (UsbInterface candidate : playbackAlternates) {
                report.append("Playback interface ID=").append(candidate.getId())
                    .append(", alt=").append(candidate.getAlternateSetting())
                    .append(", endpoints=").append(candidate.getEndpointCount()).append('\n');
                for (int e = 0; e < candidate.getEndpointCount(); e++) {
                    UsbEndpoint endpoint = candidate.getEndpoint(e);
                    report.append("  endpoint address=0x")
                        .append(Integer.toHexString(endpoint.getAddress()).toUpperCase(java.util.Locale.ROOT))
                        .append(" direction=")
                        .append(endpoint.getDirection() == UsbConstants.USB_DIR_OUT ? "OUT" : "IN")
                        .append(" type=").append(endpoint.getType())
                        .append(" maxPacket=").append(endpoint.getMaxPacketSize()).append('\n');
                }
                if (candidate.getAlternateSetting() == 0) playbackBase = candidate;
            }
            if (playbackBase == null) {
                return report.append("Playback alt 0 tidak ditemukan; tidak ada perubahan interface dilakukan.").toString();
            }

            report.append("USB permission: OK\n");
            connection = manager.openDevice(device);
            if (connection == null) {
                return report.append("openDevice: GAGAL (null). Tidak ada claim atau perubahan interface dilakukan.").toString();
            }
            report.append("openDevice: OK\n");
            int fd = connection.getFileDescriptor();
            report.append("File descriptor: ").append(fd >= 0 ? "valid" : "invalid")
                .append(" (").append(fd).append(")\n");
            byte[] raw = connection.getRawDescriptors();
            report.append("Raw descriptor dari koneksi: ")
                .append(raw == null ? "tidak tersedia" : raw.length + " byte").append('\n');

            // This is deliberately non-forcing. Do not retry with force=true on failure.
            boolean claimed;
            try {
                claimed = connection.claimInterface(playbackBase, false);
                report.append("claimInterface(playback alt 0, force=false): ")
                    .append(claimed ? "BERHASIL" : "GAGAL (false)").append('\n');
            } catch (RuntimeException claimError) {
                report.append("claimInterface(playback alt 0): EXCEPTION ")
                    .append(claimError.getClass().getSimpleName()).append(": ")
                    .append(String.valueOf(claimError.getMessage())).append('\n');
                claimed = false;
            }

            if (!claimed) {
                report.append("SET_INTERFACE: TIDAK dicoba.\n")
                    .append("Endpoint transfer: TIDAK dicoba.\n")
                    .append("PCM streaming: TIDAK dilakukan.\n")
                    .append("Interpretasi: claim ditolak; API Android tidak memberi alasan spesifik. ")
                    .append("Kemungkinan interface sedang dimiliki audio service/HAL, atau penolakan host/driver. ")
                    .append("Hasil ini tidak membuktikan DAC tidak mendukung exclusive playback.\n")
                    .append("Keselamatan: force=true dan pelepasan driver kernel sengaja tidak digunakan.");
                return report.toString();
            }

            report.append("Claim berhasil. Untuk probe diagnostik ini, alt setting tidak diubah ")
                .append("agar tidak mengganggu routing audio sistem.\n")
                .append("releaseInterface: ");
            boolean released = false;
            try {
                released = connection.releaseInterface(playbackBase);
            } catch (RuntimeException releaseError) {
                report.append("EXCEPTION ").append(releaseError.getClass().getSimpleName())
                    .append(": ").append(String.valueOf(releaseError.getMessage()));
            }
            if (released) report.append("OK");
            else if (report.charAt(report.length() - 1) == ' ') report.append("GAGAL/false");
            report.append('\n');
            return report.toString();
        } catch (RuntimeException error) {
            return report.append("Probe exception: ").append(error.getClass().getSimpleName())
                .append(": ").append(String.valueOf(error.getMessage())).toString();
        } finally {
            if (connection != null) {
                try { connection.close(); } catch (RuntimeException ignored) { }
            }
        }
    }
}
