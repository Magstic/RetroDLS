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

/** Loop, tuning, and attenuation metadata for a region or wave. */
public final class SampleInfo extends SynthesisSupport {
    public boolean present;
    public int loopMode = LOOP_NONE;
    public int unityNote = 60;
    public int fineTuneCents = 0;
    public int attenuation = 0;
    public int loopStart = 0;
    public int loopEndInclusive = -1;

    SampleInfo effectiveWith(SampleInfo fallback) {
        if (present) {
            return this;
        }
        return fallback == null ? this : fallback;
    }
}
