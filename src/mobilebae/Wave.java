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

/** Decoded PCM wave data plus its sample metadata. */
public final class Wave extends SynthesisSupport {
    public final int index;
    public final int formatTag;
    public final int channels;
    public final int sampleRate;
    public final int bitsPerSample;
    public final int frames;
    public final int factFrames;
    public final short[] pcm;
    public final SampleInfo sample;

    Wave(int index, int formatTag, int channels, int sampleRate, int bitsPerSample,
                 int frames, int factFrames, short[] pcm, SampleInfo sample) {
        this.index = index;
        this.formatTag = formatTag;
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.bitsPerSample = bitsPerSample;
        this.frames = frames;
        this.factFrames = factFrames;
        this.pcm = pcm;
        this.sample = sample;
    }
}
