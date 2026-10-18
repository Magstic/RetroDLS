package mobilebae;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reports MIDI bank/program requests and actual DLS instrument resolution. */
final class InstrumentDiagnostics {
    private InstrumentDiagnostics() { }

    static String report(DlsBank bank, MidiSong song) {
        PreviewRenderer renderer = new PreviewRenderer(bank, 22050, 1);
        LinkedHashMap<String, SelectionUse> selections = new LinkedHashMap<String, SelectionUse>();
        SelectionUse[] active = new SelectionUse[16];
        for (MidiEvent event : song.events) {
            int channelIndex = event.channel;
            if (event.status == 0xF0) {
                int previousMode = renderer.systemMode;
                renderer.handle(event);
                if (renderer.systemMode != previousMode) {
                    // A mode reset invalidates the channel's selected program.
                    java.util.Arrays.fill(active, null);
                }
                continue;
            }
            if (channelIndex < 0 || channelIndex >= 16) {
                continue;
            }
            int high = event.status & 0xF0;
            ChannelState ch = renderer.channels[channelIndex];
            if (high == 0xB0) {
                renderer.controller(ch, event.data1, event.data2);
            } else if (high == 0xC0) {
                renderer.programChange(ch, event.data1);
                active[channelIndex] = record(selections, ch, renderer.systemMode);
            } else if (high == 0x90 && event.data2 > 0) {
                if (!ch.programSelected) {
                    renderer.programChange(ch, ch.program);
                    active[channelIndex] = record(selections, ch, renderer.systemMode);
                }
                // CC#0/CC#32 after Program Change must not re-label
                // note-ons: the instrument selection is latched until the
                // next Program Change (or a mode reset).
                SelectionUse use = active[channelIndex];
                if (use == null) {
                    use = record(selections, ch, renderer.systemMode);
                    active[channelIndex] = use;
                }
                use.notes++;
                if (ch.selectedInstrument != null) {
                    int key = bank.percussionKeyAliasFor(ch.selectedBankSelector, event.data1);
                    int regionKey = bank.hasPercussionKeyAliasesFor(ch.selectedBankSelector)
                            ? key : SynthesisSupport.clamp(key + renderer.globalCoarseSemitones(), 0, 127);
                    if (ch.selectedInstrument.regionFor(regionKey, event.data2) != null
                            && !(renderer.filterVibration && ch.program == SynthesisSupport.VIBRATION_PROGRAM)) {
                        use.mappedNotes++;
                    }
                }
            }
        }
        StringBuilder out = new StringBuilder();
        out.append("DLS: ").append(bank.sourceName).append('\n');
        out.append("MIDI: ").append(song.sourceName).append('\n');
        out.append("Bank MSB/LSB = CC#0/CC#32; PC = program (0-127)\n");
        out.append("Channel | Mode | Requested MSB/LSB/PC | Matched MSB/LSB/PC | Notes matched/total\n");
        int unmatched = 0;
        for (SelectionUse use : selections.values()) {
            out.append(String.format("%7d | %4s | %3d/%3d/%3d          | ",
                    use.channelNumber, modeName(use.mode), use.msb, use.lsb, use.program));
            if (use.instrument == null) {
                out.append("MISSING            ");
            } else {
                out.append(String.format("%3d/%3d/%3d            ",
                        use.instrument.bankMsb, use.instrument.bankLsb, use.instrument.program));
            }
            out.append("| ").append(use.mappedNotes).append('/').append(use.notes);
            if (use.instrument != null &&
                    (use.instrument.bankMsb != use.msb || use.instrument.bankLsb != use.lsb
                            || use.instrument.program != use.program)) {
                out.append(" (translated/aliased)");
            }
            out.append('\n');
            unmatched += use.notes - use.mappedNotes;
        }
        out.append("Unmapped note-on events: ").append(unmatched).append('\n');
        out.append("A matched region proves a sample is selected, not that it is audible or perceptually correct.\n");
        return out.toString();
    }

    private static SelectionUse record(Map<String, SelectionUse> selections, ChannelState ch, int mode) {
        String key = ch.index + ":" + mode + ":" + ch.bankMsb + ":" + ch.bankLsb + ":" + ch.program;
        SelectionUse use = selections.get(key);
        if (use == null) {
            use = new SelectionUse(ch.index + 1, mode, ch.bankMsb, ch.bankLsb, ch.program,
                    ch.selectedInstrument);
            selections.put(key, use);
        }
        return use;
    }

    private static String modeName(int mode) {
        switch (mode) {
            case 1: return "GM1";
            case 2: return "GM2";
            case 3: return "GS";
            case 4: return "XG";
            default: return "AUTO";
        }
    }

    private static final class SelectionUse {
        final int channelNumber, mode, msb, lsb, program;
        final Instrument instrument;
        int notes, mappedNotes;
        SelectionUse(int channelNumber, int mode, int msb, int lsb, int program,
                     Instrument instrument) {
            this.channelNumber = channelNumber;
            this.mode = mode;
            this.msb = msb;
            this.lsb = lsb;
            this.program = program;
            this.instrument = instrument;
        }
    }
}
