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

/** Single MIDI event normalized to absolute ticks and microseconds. */
public final class MidiEvent extends SynthesisSupport {
    public final long tick;
    public long micros;
    public final int track;
    public final int order;
    public final int status;
    public final int channel;
    public final int data1;
    public final int data2;
    public final int metaType;
    public final byte[] payload;

    MidiEvent(long tick, int track, int order, int status, int channel, int data1, int data2,
                      int metaType, byte[] payload) {
        this.tick = tick;
        this.track = track;
        this.order = order;
        this.status = status;
        this.channel = channel;
        this.data1 = data1;
        this.data2 = data2;
        this.metaType = metaType;
        this.payload = payload == null ? new byte[0] : payload;
    }

    public boolean isNoteOn() {
        return (status & 0xF0) == 0x90 && data2 != 0;
    }

    public boolean isNoteOff() {
        return (status & 0xF0) == 0x80 || ((status & 0xF0) == 0x90 && data2 == 0);
    }
}
