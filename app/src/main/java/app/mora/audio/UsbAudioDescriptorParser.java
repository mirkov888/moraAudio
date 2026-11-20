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
                    if (currentProtocol == 0x20 && length >= 16) {
                        long formats = u32(raw, offset + 6);
                        out.append(" channels=").append(channels)
                            .append(" bmFormats=0x").append(Long.toHexString(formats));
                    }
                    out.append('\n');
                } else if (currentSubclass == 2 && subtype == 0x02 && length >= 6) {
                    int formatType = u8(raw, offset + 3);
                    if (currentProtocol == 0x20) {
                        subslot = u8(raw, offset + 4);
                        bitResolution = u8(raw, offset + 5);
                        out.append("  Format Type ").append(formatType)
                            .append(": subslot=").append(subslot).append(" byte, bitResolution=")
                            .append(bitResolution).append(" bit");
                        if (channels > 0) out.append(", channels=").append(channels);
                        out.append("\n  Sample rate: tidak tercantum pada descriptor format UAC2;")
                            .append(" perlu query clock entity/control range.\n");
                    } else if (currentProtocol == 0x30) {
                        out.append("  UAC3 format descriptor: detail parsing belum didukung.\n");
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
                .append(" UAC2 clock frequency dibaca terpisah melalui control request jika didukung.");
        }
        out.append("\n\n").append(describeClockTopology(raw));
        out.append("\nBelum claimInterface, belum SET_INTERFACE, belum transfer PCM,")
            .append(" dan belum membuktikan exclusive playback/bit-perfect.");
        return out.toString();
    }

    static final class ClockSource {
        final int entityId;
        final int interfaceNumber;
        final int controlAccess;
        ClockSource(int entityId, int interfaceNumber, int controlAccess) {
            this.entityId = entityId;
            this.interfaceNumber = interfaceNumber;
            this.controlAccess = controlAccess;
        }
    }

    static final class ClockSelector {
        final int entityId;
        final int interfaceNumber;
        final int controlAccess;
        final int[] inputClockIds;
        ClockSelector(int entityId, int interfaceNumber, int controlAccess, int[] inputClockIds) {
            this.entityId = entityId;
            this.interfaceNumber = interfaceNumber;
            this.controlAccess = controlAccess;
            this.inputClockIds = inputClockIds;
        }
    }

    /** Returns UAC2 clock sources whose frequency control advertises host-readable access. */
    static java.util.List<ClockSource> findReadableClockSources(byte[] raw) {
        java.util.List<ClockSource> result = new java.util.ArrayList<>();
        if (raw == null) return result;
        int offset = 0;
        int interfaceNumber = -1;
        int interfaceClass = -1;
        int interfaceSubclass = -1;
        int protocol = -1;
        while (offset + 2 <= raw.length) {
            int length = u8(raw, offset);
            int type = u8(raw, offset + 1);
            if (length < 2 || offset + length > raw.length) break;
            if (type == DESC_INTERFACE && length >= 9) {
                interfaceNumber = u8(raw, offset + 2);
                interfaceClass = u8(raw, offset + 5);
                interfaceSubclass = u8(raw, offset + 6);
                protocol = u8(raw, offset + 7);
            } else if (type == DESC_CS_INTERFACE && interfaceClass == 1
                    && interfaceSubclass == 1 && protocol == 0x20
                    && length >= 8 && u8(raw, offset + 2) == 0x0A) {
                int entityId = u8(raw, offset + 3);
                int access = u8(raw, offset + 5) & 0x03;
                // 01 = read-only, 11 = read/write. Never issue SET requests here.
                if (entityId != 0 && (access == 1 || access == 3)) {
                    result.add(new ClockSource(entityId, interfaceNumber, access));
                }
            }
            offset += length;
        }
        return result;
    }



    /**
     * Returns only readable Clock Sources on an unambiguous path from an AS terminalLink.
     * Multi-input Clock Selectors are intentionally not traversed until their selected pin
     * has been queried. This prevents unrelated or inactive clock entities being presented
     * as the playback clock.
     */
    static java.util.List<ClockSelector> findPlaybackClockSelectors(byte[] raw) {
        java.util.List<ClockSelector> result = new java.util.ArrayList<>();
        if (raw == null || raw.length == 0) return result;
        java.util.Map<Integer, ClockEntity> entities = parseClockEntities(raw);
        java.util.Set<Integer> reachable = new java.util.HashSet<>();
        int offset = 0, cls = -1, sub = -1, protocol = -1;
        while (offset + 2 <= raw.length) {
            int length = u8(raw, offset), type = u8(raw, offset + 1);
            if (length < 2 || offset + length > raw.length) break;
            if (type == DESC_INTERFACE && length >= 9) {
                cls = u8(raw, offset + 5); sub = u8(raw, offset + 6);
                protocol = u8(raw, offset + 7);
            } else if (type == DESC_CS_INTERFACE && cls == 1 && sub == 2
                    && protocol == 0x20 && length >= 4 && u8(raw, offset + 2) == 0x01) {
                collectReachableSelectors(u8(raw, offset + 3), entities,
                    new java.util.HashSet<Integer>(), reachable);
            }
            offset += length;
        }
        for (int id : reachable) {
            ClockEntity entity = entities.get(id);
            if (entity != null && entity.subtype == 0x0B && entity.sources.length > 1) {
                result.add(new ClockSelector(entity.id, entity.interfaceNumber,
                    entity.controlAccess, entity.sources.clone()));
            }
        }
        return result;
    }

    private static void collectReachableSelectors(int id, java.util.Map<Integer, ClockEntity> entities,
            java.util.Set<Integer> visited, java.util.Set<Integer> result) {
        if (!visited.add(id)) return;
        ClockEntity entity = entities.get(id);
        if (entity == null) return;
        if (entity.subtype == 0x0B) result.add(id);
        for (int source : entity.sources) {
            if (source != 0) collectReachableSelectors(source, entities, visited, result);
        }
    }

    private static java.util.Map<Integer, ClockEntity> parseClockEntities(byte[] raw) {
        java.util.Map<Integer, ClockEntity> entities = new java.util.LinkedHashMap<>();
        int offset = 0, intf = -1, cls = -1, sub = -1, protocol = -1;
        while (offset + 2 <= raw.length) {
            int length = u8(raw, offset), type = u8(raw, offset + 1);
            if (length < 2 || offset + length > raw.length) break;
            if (type == DESC_INTERFACE && length >= 9) {
                intf = u8(raw, offset + 2); cls = u8(raw, offset + 5);
                sub = u8(raw, offset + 6); protocol = u8(raw, offset + 7);
            } else if (type == DESC_CS_INTERFACE && cls == 1 && sub == 1
                    && protocol == 0x20 && length >= 4) {
                int subtype = u8(raw, offset + 2), id = u8(raw, offset + 3);
                if (subtype == 0x0A && length >= 8) {
                    entities.put(id, new ClockEntity(id, subtype, intf,
                        u8(raw, offset + 5) & 3, new int[0]));
                } else if (subtype == 0x0B && length >= 8) {
                    int pins = u8(raw, offset + 4);
                    if (pins > 0 && length >= 7 + pins) {
                        int[] sources = new int[pins];
                        for (int i = 0; i < pins; i++) sources[i] = u8(raw, offset + 5 + i);
                        entities.put(id, new ClockEntity(id, subtype, intf,
                            u8(raw, offset + 5 + pins) & 3, sources));
                    }
                } else if (subtype == 0x0C && length >= 7) {
                    entities.put(id, new ClockEntity(id, subtype, intf,
                        u8(raw, offset + 5) & 3, new int[]{u8(raw, offset + 4)}));
                } else if (subtype == 0x02 && length >= 8) {
                    entities.put(id, new ClockEntity(id, subtype, intf, 0,
                        new int[]{u8(raw, offset + 7)}));
                } else if (subtype == 0x03 && length >= 9) {
                    entities.put(id, new ClockEntity(id, subtype, intf, 0,
                        new int[]{u8(raw, offset + 8)}));
                }
            }
            offset += length;
        }
        return entities;
    }

    static java.util.List<ClockSource> findPlaybackClockSources(byte[] raw) {
        return findPlaybackClockSources(raw, java.util.Collections.<Integer, Integer>emptyMap());
    }

    /** selectedPins maps Clock Selector entity IDs to their 1-based GET_CUR pin values. */
    static java.util.List<ClockSource> findPlaybackClockSources(byte[] raw,
            java.util.Map<Integer, Integer> selectedPins) {
        java.util.List<ClockSource> readable = findReadableClockSources(raw);
        java.util.List<ClockSource> result = new java.util.ArrayList<>();
        if (raw == null || raw.length == 0 || readable.isEmpty()) return result;
        java.util.Map<Integer, Integer> subtypes = new java.util.HashMap<>();
        java.util.Map<Integer, int[]> edges = new java.util.HashMap<>();
        java.util.List<Integer> terminalLinks = new java.util.ArrayList<>();
        int offset = 0, cls = -1, sub = -1, protocol = -1;
        while (offset + 2 <= raw.length) {
            int length = u8(raw, offset), type = u8(raw, offset + 1);
            if (length < 2 || offset + length > raw.length) break;
            if (type == DESC_INTERFACE && length >= 9) {
                cls = u8(raw, offset + 5); sub = u8(raw, offset + 6);
                protocol = u8(raw, offset + 7);
            } else if (type == DESC_CS_INTERFACE && cls == 1 && protocol == 0x20
                    && length >= 4) {
                int subtype = u8(raw, offset + 2), id = u8(raw, offset + 3);
                if (sub == 1) {
                    if (subtype == 0x02 && length >= 8) {
                        subtypes.put(id, subtype); edges.put(id, new int[]{u8(raw, offset + 7)});
                    } else if (subtype == 0x03 && length >= 9) {
                        subtypes.put(id, subtype); edges.put(id, new int[]{u8(raw, offset + 8)});
                    } else if (subtype == 0x0C && length >= 7) {
                        subtypes.put(id, subtype); edges.put(id, new int[]{u8(raw, offset + 4)});
                    } else if (subtype == 0x0B && length >= 8) {
                        int pins = u8(raw, offset + 4);
                        if (pins > 0 && length >= 7 + pins) {
                            int[] sources = new int[pins];
                            for (int i = 0; i < pins; i++) sources[i] = u8(raw, offset + 5 + i);
                            subtypes.put(id, subtype); edges.put(id, sources);
                        }
                    } else if (subtype == 0x0A && length >= 8) {
                        subtypes.put(id, subtype);
                    }
                } else if (sub == 2 && subtype == 0x01) {
                    terminalLinks.add(u8(raw, offset + 3));
                }
            }
            offset += length;
        }
        java.util.Set<Integer> reachable = new java.util.HashSet<>();
        for (int terminalId : terminalLinks) {
            collectReachableClockSources(terminalId, subtypes, edges,
                new java.util.HashSet<Integer>(), reachable, selectedPins);
        }
        for (ClockSource clock : readable) if (reachable.contains(clock.entityId)) result.add(clock);
        return result;
    }

    private static void collectReachableClockSources(int id, java.util.Map<Integer, Integer> subtypes,
            java.util.Map<Integer, int[]> edges, java.util.Set<Integer> visited,
            java.util.Set<Integer> result, java.util.Map<Integer, Integer> selectedPins) {
        if (visited.contains(id)) return;
        Integer subtype = subtypes.get(id);
        if (subtype == null) return;
        if (subtype == 0x0A) { result.add(id); return; }
        int[] next = edges.get(id);
        if (next == null || next.length == 0) return;
        if (subtype == 0x0B && next.length > 1) {
            Integer pin = selectedPins == null ? null : selectedPins.get(id);
            if (pin == null || pin < 1 || pin > next.length) return;
            next = new int[]{next[pin - 1]};
        } else if (next.length != 1) return;
        java.util.Set<Integer> branch = new java.util.HashSet<>(visited);
        branch.add(id);
        collectReachableClockSources(next[0], subtypes, edges, branch, result, selectedPins);
    }

    private static final class ClockEntity {
        final int id, subtype, interfaceNumber, controlAccess;
        final int[] sources;
        ClockEntity(int id, int subtype, int interfaceNumber, int controlAccess, int[] sources) {
            this.id=id; this.subtype=subtype; this.interfaceNumber=interfaceNumber;
            this.controlAccess=controlAccess; this.sources=sources;
        }
    }

    /** Resolves clock references from AS terminalLink; selector branches remain explicitly ambiguous. */
    static String describeClockTopology(byte[] raw) {
        if (raw == null || raw.length == 0) return "UAC2 clock topology: descriptor kosong.";
        java.util.Map<Integer, ClockEntity> entities = new java.util.LinkedHashMap<>();
        java.util.List<Integer> terminals = new java.util.ArrayList<>();
        int offset=0, intf=-1, cls=-1, sub=-1, protocol=-1;
        while (offset+2 <= raw.length) {
            int length=u8(raw,offset), type=u8(raw,offset+1);
            if (length<2 || offset+length>raw.length) break;
            if (type==DESC_INTERFACE && length>=9) {
                intf=u8(raw,offset+2); cls=u8(raw,offset+5);
                sub=u8(raw,offset+6); protocol=u8(raw,offset+7);
            } else if (type==DESC_CS_INTERFACE && cls==1 && protocol==0x20 && length>=4) {
                int subtype=u8(raw,offset+2), id=u8(raw,offset+3);
                if (sub==1) {
                    if (subtype==0x0A && length>=8) {
                        entities.put(id,new ClockEntity(id,subtype,intf,u8(raw,offset+5)&3,new int[0]));
                    } else if (subtype==0x0B && length>=8) {
                        int pins=u8(raw,offset+4);
                        if (pins>0 && length>=7+pins) {
                            int[] sources=new int[pins];
                            for(int i=0;i<pins;i++) sources[i]=u8(raw,offset+5+i);
                            entities.put(id,new ClockEntity(id,subtype,intf,
                                u8(raw,offset+5+pins)&3,sources));
                        }
                    } else if (subtype==0x0C && length>=7) {
                        entities.put(id,new ClockEntity(id,subtype,intf,
                            u8(raw,offset+5)&3,new int[]{u8(raw,offset+4)}));
                    } else if (subtype==0x02 && length>=8) {
                        entities.put(id,new ClockEntity(id,subtype,intf,0,new int[]{u8(raw,offset+7)}));
                    } else if (subtype==0x03 && length>=9) {
                        entities.put(id,new ClockEntity(id,subtype,intf,0,new int[]{u8(raw,offset+8)}));
                    }
                } else if (sub==2 && subtype==0x01) {
                    terminals.add(u8(raw,offset+3));
                }
            }
            offset+=length;
        }
        if(terminals.isEmpty()) return "UAC2 clock topology: AudioStreaming terminalLink tidak ditemukan.";
        StringBuilder out=new StringBuilder("=== UAC2 CLOCK ENTITY TOPOLOGY ===");
        for(int terminalId:terminals) {
            out.append("\nAudioStreaming terminalLink=").append(terminalId).append(": ");
            ClockEntity terminal=entities.get(terminalId);
            if(terminal==null || terminal.sources.length==0 || terminal.sources[0]==0) {
                out.append("terminal/clock reference tidak ditemukan."); continue;
            }
            java.util.List<String> paths=new java.util.ArrayList<>();
            resolveClockPath(terminal.sources[0],entities,new java.util.HashSet<Integer>(),paths,"");
            if(paths.isEmpty()) out.append("jalur clock tidak dapat di-resolve.");
            else for(int i=0;i<paths.size();i++) {
                if(i>0) out.append(" | ");
                out.append(paths.get(i));
            }
        }
        out.append("\nSelector multi-input belum dianggap aktif; pin terpilih wajib dibaca dengan GET_CUR.");
        return out.toString();
    }

    private static void resolveClockPath(int id, java.util.Map<Integer,ClockEntity> entities,
            java.util.Set<Integer> visited, java.util.List<String> paths, String prefix) {
        if(visited.contains(id)) { paths.add(prefix+"cycle pada entity "+id); return; }
        ClockEntity e=entities.get(id);
        if(e==null) { paths.add(prefix+"entity "+id+" tidak ditemukan"); return; }
        java.util.Set<Integer> next=new java.util.HashSet<>(visited); next.add(id);
        if(e.subtype==0x0A) {
            boolean readable=e.controlAccess==1 || e.controlAccess==3;
            paths.add(prefix+"Clock Source "+id+(readable
                ? " [frequency readable, AC interface "+e.interfaceNumber+"]"
                : " [frequency control tidak readable]"));
        } else if(e.subtype==0x0B) {
            if(e.sources.length==1) resolveClockPath(e.sources[0],entities,next,paths,
                prefix+"Clock Selector "+id+" -> ");
            else {
                StringBuilder b=new StringBuilder(prefix+"Clock Selector "+id+
                    " [AMBIGUOUS, candidates=");
                for(int i=0;i<e.sources.length;i++) { if(i>0)b.append(','); b.append(e.sources[i]); }
                paths.add(b.append("; selected pin belum dibaca]").toString());
            }
        } else if(e.subtype==0x0C) {
            resolveClockPath(e.sources[0],entities,next,paths,prefix+"Clock Multiplier "+id+" -> ");
        } else {
            resolveClockPath(e.sources[0],entities,next,paths,prefix+"Terminal "+id+" -> ");
        }
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
