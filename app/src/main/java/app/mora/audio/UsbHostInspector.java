package app.mora.audio;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.util.Map;

/** Read-only USB descriptor inspection. Does not claim endpoint ownership or stream audio. */
final class UsbHostInspector {
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
                    .append(" endpoints=").append(intf.getEndpointCount()).append('\n');
                for (int e = 0; e < intf.getEndpointCount(); e++) {
                    UsbEndpoint ep = intf.getEndpoint(e);
                    out.append("    Endpoint address=").append(String.format("0x%02X", ep.getAddress()))
                        .append(" direction=").append(ep.getDirection() == 0 ? "OUT" : "IN")
                        .append(" type=").append(ep.getType())
                        .append(" maxPacket=").append(ep.getMaxPacketSize()).append('\n');
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

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "(nama tidak tersedia)" : value;
    }

    private UsbHostInspector() { }
}
