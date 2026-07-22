package mobilebae;

import static mobilebae.SynthesisSupport.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Standard MIDI parser and tempo map conversion. */
final class MidiParser {
    final byte[] data;
    final String sourceName;
    int order;

    MidiParser(byte[] data, String sourceName) {
        this.data = data;
        this.sourceName = sourceName;
    }

    static MidiSong parse(byte[] data, String sourceName) {
        return new MidiParser(data, sourceName).parse();
    }

    MidiSong parse() {
        require(0, 14);
        if (!fourcc(0).equals("MThd")) {
            throw error(0, "MIDI must start with MThd");
        }
        int headerSize = be32(4);
        if (headerSize < 6) {
            throw error(4, "short MThd");
        }
        int format = be16(8);
        int tracks = be16(10);
        int division = be16(12);
        if ((format == 0 && tracks != 1) || (format != 0 && format != 1)
                || tracks < 1 || division <= 0 || (division & 0x8000) != 0) {
            throw error(8, "unsupported MIDI format or SMPTE division");
        }
        int p = 8 + headerSize;
        List<MidiEvent> events = new ArrayList<MidiEvent>();
        int foundTracks = 0;
        while (foundTracks < tracks && p < data.length) {
            require(p, 8);
            String chunk = fourcc(p);
            if (!chunk.equals("MTrk")) {
                int skipSize = be32(p + 4);
                p = checkedEnd(p + 8, skipSize);
                continue;
            }
            int size = be32(p + 4);
            int start = p + 8;
            int end = checkedEnd(start, size);
            parseTrack(foundTracks, start, end, division, events);
            foundTracks++;
            p = end;
        }
        if (foundTracks != tracks) {
            throw error(p, "missing MTrk");
        }
        Collections.sort(events, new Comparator<MidiEvent>() {
            public int compare(MidiEvent a, MidiEvent b) {
                int byTick = Long.compare(a.tick, b.tick);
                if (byTick != 0) {
                    return byTick;
                }
                int byTrack = a.track - b.track;
                return byTrack != 0 ? byTrack : a.order - b.order;
            }
        });

        List<List<MidiEvent>> trackEvents = new ArrayList<List<MidiEvent>>();
        for (int i = 0; i < tracks; i++) {
            trackEvents.add(new ArrayList<MidiEvent>());
        }
        List<TempoEntry> tempoMap = new ArrayList<TempoEntry>();
        long maxEndTick = 0;
        for (MidiEvent event : events) {
            if (event.status == 0xFF && event.metaType == 0x51 && event.payload.length == 3) {
                tempoMap.add(new TempoEntry(event.tick, tempoOf(event)));
            } else if (event.status == 0xFF && event.metaType == 0x2F) {
                maxEndTick = Math.max(maxEndTick, event.tick);
            }
            trackEvents.get(event.track).add(event);
        }

        long lengthMicros = tempoMapMicros(tempoMap, maxEndTick, division);
        List<MidiEvent> runtimeEvents = new ArrayList<MidiEvent>(events.size());
        int[] cursor = new int[tracks];
        long currentUsec = 0;
        long currentTickQ8 = 0;
        int currentTempo = 500000;
        int remaining = events.size();
        for (int track = 0; track < tracks; track++) {
            List<MidiEvent> list = trackEvents.get(track);
            while (cursor[track] < list.size() && list.get(cursor[track]).tick == 0) {
                MidiEvent event = list.get(cursor[track]++);
                event.micros = 0;
                runtimeEvents.add(event);
                remaining--;
                if (event.status == 0xFF && event.metaType == 0x51 && event.payload.length == 3) {
                    currentTempo = tempoOf(event);
                }
            }
        }
        while (remaining > 0) {
            currentUsec += DEFAULT_RENDER_PERIOD_MS * 1000L;
            long scaled = (long) DEFAULT_RENDER_PERIOD_MS * 1000L * division;
            scaled &= ~7L;
            long deltaTickQ8 = (32L * scaled) / (currentTempo >> 3);
            if (deltaTickQ8 == 0) {
                throw error(12, "MIDI runtime tick advance stalled");
            }
            currentTickQ8 += deltaTickQ8;
            long targetTick = currentTickQ8 >> 8;
            for (int track = 0; track < tracks; track++) {
                List<MidiEvent> list = trackEvents.get(track);
                while (cursor[track] < list.size() && list.get(cursor[track]).tick <= targetTick) {
                    MidiEvent event = list.get(cursor[track]++);
                    event.micros = currentUsec;
                    runtimeEvents.add(event);
                    remaining--;
                    if (event.status == 0xFF && event.metaType == 0x51 && event.payload.length == 3) {
                        currentTempo = tempoOf(event);
                    }
                }
            }
        }
        return new MidiSong(sourceName, format, division, runtimeEvents, lengthMicros);
    }

    void parseTrack(int track, int start, int end, int division, List<MidiEvent> events) {
        int p = start;
        int running = 0;
        long tick = 0;
        boolean ended = false;
        while (p < end) {
            Vlq delta = readVlq(p, end);
            p = delta.next;
            long deltaTicks = delta.value;
            if (deltaTicks > 80L * division && p + 3 == end
                    && (data[p] & 0xFF) == 0xFF && (data[p + 1] & 0xFF) == 0x2F && data[p + 2] == 0) {
                deltaTicks = 80L * division;
            }
            tick += deltaTicks;
            if (p >= end) {
                throw error(p, "missing event status");
            }
            int status = data[p++] & 0xFF;
            int first = -1;
            if (status < 0x80) {
                if (running == 0) {
                    throw error(p - 1, "running status without previous status");
                }
                first = status;
                status = running;
            } else if (status < 0xF0) {
                running = status;
            }

            if (status == 0xFF) {
                if (p >= end) {
                    throw error(p, "short meta event");
                }
                int metaType = data[p++] & 0xFF;
                Vlq len = readVlq(p, end);
                p = len.next;
                if (p + len.value > end) {
                    throw error(p, "meta event exceeds track");
                }
                byte[] payload = Arrays.copyOfRange(data, p, (int) (p + len.value));
                p += (int) len.value;
                if (metaType == 0x51 && payload.length != 3) {
                    throw error(p, "bad tempo meta length");
                }
                if (metaType == 0x2F && payload.length != 0) {
                    throw error(p, "bad end-of-track length");
                }
                events.add(new MidiEvent(tick, track, order++, 0xFF, -1, -1, -1, metaType, payload));
                if (metaType == 0x2F) {
                    ended = true;
                    if (p != end) {
                        throw error(p, "end-of-track is not final");
                    }
                }
                continue;
            }

            if (status == 0xF0) {
                int payloadStart = p;
                while (p < end && (data[p] & 0xFF) != 0xF7) {
                    p++;
                }
                if (p == end) {
                    throw error(payloadStart, "unterminated SysEx event");
                }
                int payloadEnd = p;
                p++;
                int count = payloadEnd - payloadStart;
                int actualStart = count > 0 && (data[payloadStart] & 0xFF) == count ? payloadStart + 1 : payloadStart;
                events.add(new MidiEvent(tick, track, order++, status, -1, -1, -1, -1,
                        Arrays.copyOfRange(data, actualStart, payloadEnd)));
                running = 0;
                continue;
            }

            if (status == 0xF8 || status == 0xFA || status == 0xFB
                    || status == 0xFC || status == 0xFE) {
                events.add(new MidiEvent(tick, track, order++, status, -1, 0, 0, -1, null));
                continue;
            }
            if (status >= 0xF0) {
                throw error(p - 1, "unsupported system event");
            }

            int high = status & 0xF0;
            int channel = status & 0x0F;
            int d1;
            int d2 = 0;
            if (first >= 0) {
                d1 = first;
            } else {
                if (p >= end) {
                    throw error(p, "short channel event");
                }
                d1 = data[p++] & 0x7F;
            }
            if (high != 0xC0 && high != 0xD0) {
                if (p >= end) {
                    throw error(p, "short channel event");
                }
                d2 = data[p++] & 0x7F;
            }
            events.add(new MidiEvent(tick, track, order++, status, channel, d1, d2, -1, null));
        }
        if (!ended) {
            throw error(end, "track missing final FF 2F 00");
        }
    }

    Vlq readVlq(int p, int end) {
        long value = 0;
        for (int i = 0; i < 4; i++) {
            if (p >= end) {
                throw error(p, "short VLQ");
            }
            int b = data[p++] & 0xFF;
            value = (value << 7) + (b & 0x7F);
            if ((b & 0x80) == 0) {
                return new Vlq(value, p);
            }
        }
        throw error(p, "VLQ exceeds 4 bytes");
    }

    int clampTempo(int value) {
        if (value < 29296) {
            return 29296;
        }
        return value > 15000000 ? 15000000 : value;
    }

    int tempoOf(MidiEvent event) {
        return clampTempo(((event.payload[0] & 0xFF) << 16)
                | ((event.payload[1] & 0xFF) << 8) | (event.payload[2] & 0xFF));
    }

    long tempoMapMicros(List<TempoEntry> tempoMap, long targetTick, int division) {
        long tick = 0;
        long micros = 0;
        int tempo = 500000;
        for (TempoEntry entry : tempoMap) {
            if (entry.tick > targetTick) {
                break;
            }
            micros += (entry.tick - tick) * tempo / division;
            tick = entry.tick;
            tempo = entry.tempo;
        }
        return micros + (targetTick - tick) * tempo / division;
    }

    int be16(int p) {
        require(p, 2);
        return ((data[p] & 0xFF) << 8) | (data[p + 1] & 0xFF);
    }

    int be32(int p) {
        require(p, 4);
        return ((data[p] & 0xFF) << 24) | ((data[p + 1] & 0xFF) << 16)
                | ((data[p + 2] & 0xFF) << 8) | (data[p + 3] & 0xFF);
    }

    String fourcc(int p) {
        require(p, 4);
        return new String(new char[]{(char) data[p], (char) data[p + 1], (char) data[p + 2], (char) data[p + 3]});
    }

    int checkedEnd(int start, int size) {
        long end = (long) start + size;
        if (end < start || end > data.length) {
            throw error(start, "chunk exceeds file");
        }
        return (int) end;
    }

    void require(int p, int len) {
        if (p < 0 || len < 0 || p + len < p || p + len > data.length) {
            throw error(p, "unexpected EOF");
        }
    }

    IllegalArgumentException error(int p, String message) {
        return new IllegalArgumentException(sourceName + " @0x" + Integer.toHexString(Math.max(0, p)) + ": " + message);
    }
}
final class TempoEntry {
    final long tick;
    final int tempo;

    TempoEntry(long tick, int tempo) {
        this.tick = tick;
        this.tempo = tempo;
    }
}
final class Vlq {
    final long value;
    final int next;

    Vlq(long value, int next) {
        this.value = value;
        this.next = next;
    }
}
