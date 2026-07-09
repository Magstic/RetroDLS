package mobilebae;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/** CLI and public facade for loading DLS/MIDI data, rendering PCM, playback, and WAV export. */
public final class MobileBae {
    public static final int LOOP_NONE = SynthesisSupport.LOOP_NONE;
    public static final int LOOP_FORWARD = SynthesisSupport.LOOP_FORWARD;
    public static final int VIBRATION_PROGRAM = SynthesisSupport.VIBRATION_PROGRAM;

    private MobileBae() {
    }

    public static void main(String[] args) throws Exception {
        String usage = "usage: Retro DLS <bank.dls> <song.mid> <out.wav|--play> [sampleRate] [maxSeconds] [--polyphony voices] [--reverb|--no-reverb] [--chorus|--no-chorus] [--filter-vibration|--no-filter-vibration]";
        if (args.length == 0) {
            MobileBaeGui.main(args);
            return;
        }
        if (args.length < 3) {
            throw new IllegalArgumentException(usage);
        }
        int sampleRate = 22050;
        Integer secondsArg = null;
        int polyphony = PreviewRenderer.ORDINARY_VOICE_LIMIT;
        boolean reverbEnabled = true;
        boolean chorusEnabled = true;
        boolean filterVibration = true;
        int numericArg = 0;
        for (int i = 3; i < args.length; i++) {
            String arg = args[i];
            if ("--reverb".equals(arg)) {
                reverbEnabled = true;
            } else if ("--no-reverb".equals(arg)) {
                reverbEnabled = false;
            } else if ("--chorus".equals(arg)) {
                chorusEnabled = true;
            } else if ("--no-chorus".equals(arg)) {
                chorusEnabled = false;
            } else if ("--filter-vibration".equals(arg)) {
                filterVibration = true;
            } else if ("--no-filter-vibration".equals(arg)) {
                filterVibration = false;
            } else if ("--polyphony".equals(arg)) {
                if (++i >= args.length) {
                    throw new IllegalArgumentException(usage);
                }
                polyphony = Integer.parseInt(args[i]);
            } else if (arg.startsWith("--polyphony=")) {
                polyphony = Integer.parseInt(arg.substring("--polyphony=".length()));
            } else {
                if (arg.startsWith("--") || numericArg >= 2) {
                    throw new IllegalArgumentException(usage);
                }
                int value = Integer.parseInt(arg);
                if (numericArg == 0) {
                    sampleRate = value;
                } else {
                    secondsArg = value;
                }
                numericArg++;
            }
        }
        DlsBank bank = loadDls(Paths.get(args[0]));
        MidiSong song = loadMidi(Paths.get(args[1]));
        int seconds = secondsArg != null ? secondsArg : defaultMaxSeconds(song);
        if ("--play".equals(args[2])) {
            playRealtime(bank, song, sampleRate, seconds, reverbEnabled, chorusEnabled, polyphony, filterVibration, null);
        } else {
            Files.write(Paths.get(args[2]), wavBytes(renderPreview(bank, song, sampleRate, seconds, reverbEnabled, chorusEnabled, polyphony, filterVibration, null), sampleRate));
        }
    }


    public static DlsBank loadDls(Path path) throws IOException {
        return DlsParser.parse(Files.readAllBytes(path), path.toString());
    }

    public static MidiSong loadMidi(Path path) throws IOException {
        return MidiParser.parse(Files.readAllBytes(path), path.toString());
    }

    static int defaultMaxSeconds(MidiSong song) {
        long seconds = (song.lengthMicros + 999999L) / 1000000L + 1L;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, seconds));
    }

    public static short[] renderPreview(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds) {
        return renderPreview(bank, song, sampleRate, maxSeconds, true);
    }

    public static short[] renderPreview(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, int polyphony) {
        return renderPreview(bank, song, sampleRate, maxSeconds, true, true, polyphony);
    }

    static short[] renderPreview(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, boolean effectsEnabled) {
        return renderPreview(bank, song, sampleRate, maxSeconds, effectsEnabled, effectsEnabled);
    }

    static short[] renderPreview(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, boolean reverbEnabled, boolean chorusEnabled) {
        return renderPreview(bank, song, sampleRate, maxSeconds, reverbEnabled, chorusEnabled, PreviewRenderer.ORDINARY_VOICE_LIMIT);
    }

    static short[] renderPreview(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, boolean reverbEnabled, boolean chorusEnabled, int polyphony) {
        return renderPreview(bank, song, sampleRate, maxSeconds, reverbEnabled, chorusEnabled, polyphony, true, null);
    }

    static short[] renderPreview(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, boolean reverbEnabled, boolean chorusEnabled,
                                 int polyphony, boolean filterVibration, VibrationListener vibrationListener) {
        PcmStream stream = openStream(bank, song, sampleRate, maxSeconds, reverbEnabled, chorusEnabled, polyphony, filterVibration, vibrationListener);
        short[] out = new short[stream.totalFrames() * 2];
        int frame = 0;
        while (frame < stream.totalFrames()) {
            int read = stream.read(out, frame, stream.totalFrames() - frame);
            if (read == 0) {
                break;
            }
            frame += read;
        }
        return out;
    }

    public static PcmStream openStream(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds) {
        return openStream(bank, song, sampleRate, maxSeconds, true);
    }

    public static void playRealtime(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds) throws Exception {
        playRealtime(bank, song, sampleRate, maxSeconds, true, true);
    }

    public static void playRealtime(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, int polyphony) throws Exception {
        playRealtime(bank, song, sampleRate, maxSeconds, true, true, polyphony);
    }

    static void playRealtime(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, boolean reverbEnabled, boolean chorusEnabled) throws Exception {
        playRealtime(bank, song, sampleRate, maxSeconds, reverbEnabled, chorusEnabled, PreviewRenderer.ORDINARY_VOICE_LIMIT);
    }

    static void playRealtime(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, boolean reverbEnabled, boolean chorusEnabled, int polyphony) throws Exception {
        playRealtime(bank, song, sampleRate, maxSeconds, reverbEnabled, chorusEnabled, polyphony, true, null);
    }

    static void playRealtime(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, boolean reverbEnabled, boolean chorusEnabled,
                             int polyphony, boolean filterVibration, VibrationListener vibrationListener) throws Exception {
        PcmStream stream = openStream(bank, song, sampleRate, maxSeconds, reverbEnabled, chorusEnabled, polyphony, filterVibration, vibrationListener);
        AudioFormat format = new AudioFormat(sampleRate, 16, 2, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
            short[] pcm = new short[stream.blockFrames() * 2];
            byte[] bytes = new byte[pcm.length * 2];
            line.open(format, bytes.length * 4);
            line.start();
            while (!stream.finished()) {
                int frames = stream.read(pcm, 0, stream.blockFrames());
                for (int i = 0, j = 0; i < frames * 2; i++) {
                    short sample = pcm[i];
                    bytes[j++] = (byte) sample;
                    bytes[j++] = (byte) (sample >>> 8);
                }
                line.write(bytes, 0, frames * 4);
            }
            line.drain();
        }
    }

    static PcmStream openStream(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, boolean effectsEnabled) {
        return openStream(bank, song, sampleRate, maxSeconds, effectsEnabled, effectsEnabled);
    }

    public static PcmStream openStream(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, int polyphony) {
        return openStream(bank, song, sampleRate, maxSeconds, true, true, polyphony);
    }

    static PcmStream openStream(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, boolean reverbEnabled, boolean chorusEnabled) {
        return openStream(bank, song, sampleRate, maxSeconds, reverbEnabled, chorusEnabled, PreviewRenderer.ORDINARY_VOICE_LIMIT);
    }

    static PcmStream openStream(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, boolean reverbEnabled, boolean chorusEnabled, int polyphony) {
        return openStream(bank, song, sampleRate, maxSeconds, reverbEnabled, chorusEnabled, polyphony, true, null);
    }

    public static PcmStream openStream(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, int polyphony,
                                       boolean filterVibration, VibrationListener vibrationListener) {
        return openStream(bank, song, sampleRate, maxSeconds, true, true, polyphony, filterVibration, vibrationListener);
    }

    static PcmStream openStream(DlsBank bank, MidiSong song, int sampleRate, int maxSeconds, boolean reverbEnabled, boolean chorusEnabled,
                                int polyphony, boolean filterVibration, VibrationListener vibrationListener) {
        if (sampleRate <= 0 || maxSeconds <= 0) {
            throw new IllegalArgumentException("sampleRate and maxSeconds must be positive");
        }
        if (polyphony <= 0) {
            throw new IllegalArgumentException("polyphony must be positive");
        }
        return new PcmStream(new PreviewRenderer(bank, sampleRate, maxSeconds, reverbEnabled, chorusEnabled,
                polyphony, filterVibration, vibrationListener), song);
    }

    static int songChildTailInput(MidiSong song) {
        return PreviewRenderer.childTailInput(song);
    }

    public static byte[] wavBytes(short[] stereoPcm, int sampleRate) throws IOException {
        if ((stereoPcm.length & 1) != 0) {
            throw new IllegalArgumentException("stereo PCM must contain left/right pairs");
        }
        int dataBytes = stereoPcm.length * 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataBytes);
        SynthesisSupport.ascii(out, "RIFF");
        SynthesisSupport.le32(out, 36 + dataBytes);
        SynthesisSupport.ascii(out, "WAVEfmt ");
        SynthesisSupport.le32(out, 16);
        SynthesisSupport.le16(out, 1);
        SynthesisSupport.le16(out, 2);
        SynthesisSupport.le32(out, sampleRate);
        SynthesisSupport.le32(out, sampleRate * 4);
        SynthesisSupport.le16(out, 4);
        SynthesisSupport.le16(out, 16);
        SynthesisSupport.ascii(out, "data");
        SynthesisSupport.le32(out, dataBytes);
        for (short s : stereoPcm) {
            SynthesisSupport.le16(out, s);
        }
        return out.toByteArray();
    }
}
