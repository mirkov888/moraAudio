package app.mora.audio;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Explicit, short-lived UAC interface ownership probe.
 *
 * Claims the AudioStreaming interface at alternate setting 0, switches through advertised
 * isochronous OUT alternate settings, then restores alt 0 and releases the interface.
 * It deliberately does not submit endpoint requests or transfer PCM.
 */
final class UsbAudioInterfaceProbe {
    private UsbAudioInterfaceProbe() { }

    static String probe(UsbManager manager, UsbDevice device) {
        if (manager == null || device == null) return "USB manager/perangkat tidak tersedia.";
        if (!manager.hasPermission(device)) return "Izin USB belum tersedia.";
        UsbDeviceConnection connection = null;
        UsbInterface base = null;
        boolean claimed = false;
        StringBuilder report = new StringBuilder("=== USB AUDIO INTERFACE OWNERSHIP PROBE ===\n");
        try {
            int streamingInterfaceId = -1;
            List<UsbInterface> alternates = new ArrayList<>();
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface candidate = device.getInterface(i);
                if (candidate.getInterfaceClass() == 1 && candidate.getInterfaceSubclass() == 2) {
                    if (streamingInterfaceId == -1) streamingInterfaceId = candidate.getId();
                    if (candidate.getId() == streamingInterfaceId) alternates.add(candidate);
                }
            }
            if (alternates.isEmpty()) return "Tidak ditemukan interface AudioStreaming.";
            for (UsbInterface candidate : alternates) {
                if (candidate.getAlternateSetting() == 0) {
                    base = candidate;
                    break;
                }
            }
            if (base == null) return "Alternate setting 0 tidak ditemukan; probe dibatalkan aman.";

            connection = manager.openDevice(device);
            if (connection == null) return "Gagal membuka perangkat USB.";
            report.append("Interface ID=").append(streamingInterfaceId).append('\n');
            claimed = connection.claimInterface(base, false);
            if (!claimed) {
                report.append("claimInterface(alt 0): GAGAL. Interface mungkin sedang digunakan Android/audio service.\n");
                return report.toString();
            }
            report.append("claimInterface(alt 0): OK\n");

            int tested = 0;
            for (UsbInterface alternate : alternates) {
                int setting = alternate.getAlternateSetting();
                if (setting == 0) continue;
                boolean hasIsoOut = false;
                for (int e = 0; e < alternate.getEndpointCount(); e++) {
                    android.hardware.usb.UsbEndpoint endpoint = alternate.getEndpoint(e);
                    if (endpoint.getDirection() == android.hardware.usb.UsbConstants.USB_DIR_OUT
                            && endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_ISOC) {
                        hasIsoOut = true;
                        break;
                    }
                }
                if (!hasIsoOut) {
                    report.append("alt ").append(setting).append(": dilewati (tidak ada isochronous OUT).\n");
                    continue;
                }
                boolean switched = false;
                try {
                    switched = connection.setInterface(alternate);
                } catch (RuntimeException ignored) {
                    // Report the failed setting and continue with safe restoration.
                }
                report.append("SET_INTERFACE alt ").append(setting).append(": ")
                    .append(switched ? "OK" : "GAGAL").append('\n');
                if (switched) tested++;
                boolean restored = false;
                try {
                    restored = connection.setInterface(base);
                } catch (RuntimeException ignored) {
                    // releaseInterface/close below remains the final cleanup boundary.
                }
                report.append("Pulihkan alt 0: ").append(restored ? "OK" : "GAGAL").append('\n');
                if (!restored) {
                    report.append("Probe dihentikan agar tidak mencoba alternate setting lain dalam state yang tidak pasti.\n");
                    break;
                }
            }
            report.append("Alternate setting berhasil diuji: ").append(tested)
                .append("\nTransfer PCM: TIDAK dilakukan.");
            return report.toString();
        } catch (RuntimeException error) {
            return report.append("Probe gagal: ").append(error.getClass().getSimpleName())
                .append(": ").append(String.valueOf(error.getMessage())).toString();
        } finally {
            if (connection != null) {
                if (claimed && base != null) {
                    try { connection.setInterface(base); } catch (RuntimeException ignored) { }
                    try { connection.releaseInterface(base); } catch (RuntimeException ignored) { }
                }
                try { connection.close(); } catch (RuntimeException ignored) { }
            }
        }
    }
}
