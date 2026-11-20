package app.mora.audio;

/**
 * Defensive, read-only parser for the USB configuration descriptor stream returned by
 * UsbDeviceConnection.getRawDescriptors(). It never claims interfaces or starts transfers.
 */
final class UsbAudioDescriptorParser {
    private static final int DESC_INTERFACE = 0x04;
    private static final int DESC_ENDPOINT = 0x05;
    private static final int DESC_CS_INTERFACE = 0x24;

    static String parse(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return "Raw descriptor: kosong/tidak tersedia.";
        }

        StringBuilder out = new StringBuilder("=== RAW USB AUDIO DESCRIPTORS ===\n");
        int offset = 0;
        int currentInterface = -1;
        int currentAlt = -1;
        int currentClass = -1;
        int currentSubclass = -1;
        int currentProtocol = -1;
        int channels = -1;
        int subslot = -1;
        int bitResolution = -1;
        boolean foundAudio = false;
        boolean foundStreaming = false;
        int descriptorCount = 0;

        while (offset + 2 <= raw.length) {
            int length = u8(raw, offset);
            int type = u8(raw, offset + 1);
            if (length < 2 || offset + length > raw.length) {
                out.append("Descriptor malformed/truncated pada offset ").append(offset)
                    .append(" (length=").append(length).append("). Parsing dihentikan aman.\n");
                break;
            }
            descriptorCount++;

            if (type == DESC_INTERFACE && length >= 9) {
                currentInterface = u8(raw, offset + 2);
                currentAlt = u8(raw, offset + 3);
                currentClass = u8(raw, offset + 5);
                currentSubclass = u8(raw, offset + 6);
                currentProtocol = u8(raw, offset + 7);
                channels = -1;
                subslot = -1;
                bitResolution = -1;
                if (currentClass == 1) {
                    foundAudio = true;
                    if (currentSubclass == 2) {
                        foundStreaming = true;
                        out.append("\n[AudioStreaming] interface=").append(currentInterface)
                            .append(" alt=").append(currentAlt)
                            .append(" protocol=").append(protocolName(currentProtocol))
                            .append(" endpoints=").append(u8(raw, offset + 4)).append('\n');
                    } else if (currentSubclass == 1) {
                        out.append("\n[AudioControl] interface=").append(currentInterface)
                            .append(" protocol=").append(protocolName(currentProtocol)).append('\n');
                    }
                }
            } else if (type == DESC_CS_INTERFACE && currentClass == 1 && length >= 3) {
                int subtype = u8(raw, offset + 2);
                if (currentSubclass == 1 && subtype == 0x01 && length >= 5) {
                    int version = u16(raw, offset + 3);
                    out.append("  AC header: UAC ").append(versionName(version))
                        .append(" (bcdADC=0x").append(hex4(version)).append(")\n");
                } else if (currentSubclass == 2 && subtype == 0x01) {
                    if (currentProtocol == 0x20 && length >= 16) {
                        channels = u8(raw, offset + 10);
                    }
                    out.append("  AS General: terminalLink=").append(u8(raw, offset + 3));
                    if (currentProtocol == 0x20 && length >= 17) {
                        long formats = u32(raw, offset + 6);
                        out.append(" channels=").append(channels)
                            .append(" bmFormats=0x").append(Long.toHexString(formats));
                    }
                    out.append('\n');
                } else if (currentSubclass == 2 && subtype == 0x02 && length >= 6) {
                    int formatType = u8(raw, offset + 3);
                    if (currentProtocol == 0x20 || currentProtocol == 0x30) {
                        subslot = u8(raw, offset + 4);
                        bitResolution = u8(raw, offset + 5);
                        out.append("  Format Type ").append(formatType)
                            .append(": subslot=").append(subslot).append(" byte, bitResolution=")
                            .append(bitResolution).append(" bit");
                        if (channels > 0) out.append(", channels=").append(channels);
                        out.append("\n  Sample rate: tidak tercantum pada descriptor format UAC2/UAC3;")
                            .append(" perlu query clock entity/control range.\n");
                    } else if (formatType == 1 && length >= 8) {
                        channels = u8(raw, offset + 4);
                        subslot = u8(raw, offset + 5);
                        bitResolution = u8(raw, offset + 6);
                        int rateCount = u8(raw, offset + 7);
                        out.append("  Format Type I: channels=").append(channels)
                            .append(", subslot=").append(subslot).append(" byte, bitResolution=")
                            .append(bitResolution).append(" bit\n  Sample rate: ");
                        if (rateCount == 0) {
                            if (length >= 14) {
                                out.append(u24(raw, offset + 8)).append("..")
                                    .append(u24(raw, offset + 11)).append(" Hz (range)\n");
                            } else {
                                out.append("range descriptor terpotong\n");
                            }
                        } else {
                            int available = Math.min(rateCount, (length - 8) / 3);
                            if (available == 0) out.append("tidak tersedia");
                            for (int i = 0; i < available; i++) {
                                if (i > 0) out.append(", ");
                                out.append(u24(raw, offset + 8 + (i * 3))).append(" Hz");
                            }
                            if (available < rateCount) out.append(" (descriptor terpotong)");
                            out.append('\n');
                        }
                    } else {
                        out.append("  Format descriptor: type=").append(formatType)
                            .append(", parsing detail belum didukung untuk protokol ini.\n");
                    }
                } else if (currentSubclass == 1 && subtype == 0x0A) {
                    out.append("  Clock Source entity terdeteksi (ID=")
                        .append(length > 3 ? u8(raw, offset + 3) : -1)
                        .append("); rate perlu dibaca melalui control request.\n");
                }
            } else if (type == DESC_ENDPOINT && currentClass == 1 && currentSubclass == 2 && length >= 7) {
                int address = u8(raw, offset + 2);
                int attributes = u8(raw, offset + 3);
                int packet = u16(raw, offset + 4);
                out.append("  Endpoint 0x").append(hex2(address))
                    .append(" transfer=").append(transferName(attributes & 0x03))
                    .append(" sync=").append(syncName((attributes >> 2) & 0x03))
                    .append(" maxPacket=").append(packet)
                    .append(" interval=").append(u8(raw, offset + 6)).append('\n');
            }

            offset += length;
        }

        out.append("\nDescriptor bytes: ").append(raw.length)
            .append(", parsed descriptors: ").append(descriptorCount)
            .append("\nUSB Audio class: ").append(foundAudio ? "terdeteksi" : "tidak terdeteksi")
            .append("\nAudioStreaming: ").append(foundStreaming ? "terdeteksi" : "tidak terdeteksi");
        if (foundStreaming) {
            out.append("\nCatatan: data ini hasil parsing descriptor mentah, bukan hasil negosiasi clock/format.")
                .append(" UAC2/UAC3 sample-rate range belum di-query.");
        }
        out.append("\nBelum claimInterface, belum SET_INTERFACE, belum transfer PCM,")
            .append(" dan belum membuktikan exclusive playback/bit-perfect.");
        return out.toString();
    }

    private static int u8(byte[] b, int p) { return b[p] & 0xff; }
    private static int u16(byte[] b, int p) { return u8(b, p) | (u8(b, p + 1) << 8); }
    private static int u24(byte[] b, int p) { return u8(b, p) | (u8(b, p + 1) << 8) | (u8(b, p + 2) << 16); }
    private static long u32(byte[] b, int p) {
        return ((long) u8(b, p)) | ((long) u8(b, p + 1) << 8)
            | ((long) u8(b, p + 2) << 16) | ((long) u8(b, p + 3) << 24);
    }
    private static String hex2(int value) { return String.format(java.util.Locale.ROOT, "%02X", value); }
    private static String hex4(int value) { return String.format(java.util.Locale.ROOT, "%04X", value); }
    private static String protocolName(int p) {
        if (p == 0x00) return "1.0/legacy";
        if (p == 0x20) return "2.0";
        if (p == 0x30) return "3.0";
        return "unknown(0x" + hex2(p) + ")";
    }
    private static String versionName(int v) {
        return ((v >> 8) & 0xff) + "." + String.format(java.util.Locale.ROOT, "%02d", v & 0xff);
    }
    private static String transferName(int type) {
        switch (type) {
            case 1: return "isochronous";
            case 2: return "bulk";
            case 3: return "interrupt";
            default: return "control";
        }
    }
    private static String syncName(int sync) {
        switch (sync) {
            case 1: return "asynchronous";
            case 2: return "adaptive";
            case 3: return "synchronous";
            default: return "none";
        }
    }
    private UsbAudioDescriptorParser() { }
}
