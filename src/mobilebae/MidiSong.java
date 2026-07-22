package mobilebae;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parsed MIDI event list with timing metadata. */
public final class MidiSong {
    public final String sourceName;
    public final int format;
    public final int division;
    public final List<MidiEvent> events;
    public final long lengthMicros;

    MidiSong(String sourceName, int format, int division, List<MidiEvent> events, long lengthMicros) {
        this.sourceName = sourceName;
        this.format = format;
        this.division = division;
        this.events = Collections.unmodifiableList(new ArrayList<MidiEvent>(events));
        this.lengthMicros = lengthMicros;
    }

    public int countStatus(int highNibble) {
        highNibble &= 0xF0;
        int count = 0;
        for (MidiEvent event : events) {
            if ((event.status & 0xF0) == highNibble) {
                count++;
            }
        }
        return count;
    }

    public int realNoteOnCount() {
        int count = 0;
        for (MidiEvent event : events) {
            if ((event.status & 0xF0) == 0x90 && event.data2 > 0) {
                count++;
            }
        }
        return count;
    }
}
