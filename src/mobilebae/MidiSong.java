package mobilebae;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/** Parsed MIDI event list with timing metadata. */
public final class MidiSong extends SynthesisSupport {
    public final String sourceName;
    public final int format;
    public final int division;
    public final List<MidiEvent> events;
    public final long lengthMicros;

    MidiSong(String sourceName, int format, int division, List<MidiEvent> events, long lengthMicros) {
        this.sourceName = sourceName;
        this.format = format;
        this.division = division;
        this.events = Collections.unmodifiableList(events);
        this.lengthMicros = lengthMicros;
    }

    public int countStatus(int highNibble) {
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
