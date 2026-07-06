package mobilebae;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/** Minimal AWT launcher for the existing DLS/MIDI playback and WAV export flow. */
public final class MobileBaeGui extends Frame {
    private static final Preferences PREFS = Preferences.userNodeForPackage(MobileBaeGui.class);
    private static final Color BG = new Color(240, 240, 240);

    private final TextField dlsField = new TextField("", 34);
    private final TextField midiField = new TextField("", 34);
    private final Choice sampleRateChoice = new Choice();
    private final TextField maxSecondsField = new TextField("auto", 8);
    private final TextField polyphonyField = new TextField("256", 8);
    private final Checkbox reverbBox = new Checkbox("Reverb", true);
    private final Checkbox chorusBox = new Checkbox("Chorus", true);
    private final Checkbox filterVibrationBox = new Checkbox("Filter (Vibration)", true);
    private final TextArea status = new TextArea("", 18, 34, TextArea.SCROLLBARS_VERTICAL_ONLY);
    private final ProgressCanvas progress = new ProgressCanvas();
    private final WaveformCanvas waveform = new WaveformCanvas();
    private final Label percentLabel = new Label("0%");
    private final Button playButton = new Button("Play");
    private final Button stopButton = new Button("Stop");
    private final Button exportButton = new Button("Export WAV");

    private volatile boolean stopRequested;
    private volatile SourceDataLine activeLine;
    private volatile long lastWaveformMillis;
    private volatile int lastProgressPercent = -1;
    private Thread worker;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> new MobileBaeGui().setVisible(true));
    }

    public MobileBaeGui() {
        super("Retro DLS");
        setSize(820, 520);
        setMinimumSize(new Dimension(760, 460));
        setLayout(new BorderLayout(10, 10));
        setBackground(BG);

        sampleRateChoice.add("22050");
        sampleRateChoice.add("32000");
        sampleRateChoice.add("44100");
        sampleRateChoice.add("48000");
        sampleRateChoice.select("22050");

        add(mainPanel(), BorderLayout.CENTER);
        add(buttons(), BorderLayout.SOUTH);
        stopButton.setEnabled(false);
        loadPreferences();
        log("Ready. Choose a DLS bank and MIDI file.");

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                savePreferences();
                stopWork();
                dispose();
            }
        });
    }

    private Panel mainPanel() {
        Panel main = new Panel(new BorderLayout(12, 0));
        main.setBackground(BG);
        main.add(leftPanel(), BorderLayout.CENTER);
        main.add(statusPanel(), BorderLayout.EAST);
        return main;
    }

    private Panel leftPanel() {
        Panel left = new Panel(new BorderLayout(0, 10));
        left.setBackground(BG);
        left.add(filePanel(), BorderLayout.NORTH);

        Panel body = new Panel(new BorderLayout(0, 8));
        body.setBackground(BG);
        body.add(new Label("Playback Parameters"), BorderLayout.NORTH);
        body.add(parameterPanel(), BorderLayout.CENTER);
        left.add(body, BorderLayout.CENTER);
        return left;
    }

    private Panel filePanel() {
        Panel files = new Panel(new GridLayout(2, 1, 0, 6));
        files.setBackground(BG);
        files.add(fileRow("DLS Bank:", dlsField, "Browse...", () -> chooseDls()));
        files.add(fileRow("MIDI File:", midiField, "Browse...", () -> chooseMidi()));
        return files;
    }

    private Panel parameterPanel() {
        Panel panel = new Panel(new BorderLayout(0, 8));
        panel.setBackground(BG);

        Panel options = new Panel(new GridLayout(3, 2, 18, 6));
        options.setBackground(BG);
        options.add(fieldCell("Sample Rate:", sampleRateChoice));
        options.add(checkCell(reverbBox));
        options.add(fieldCell("Max Seconds:", maxSecondsField));
        options.add(checkCell(chorusBox));
        options.add(fieldCell("Polyphony:", polyphonyField));
        options.add(checkCell(filterVibrationBox));
        panel.add(options, BorderLayout.NORTH);

        Panel wave = new Panel(new BorderLayout(0, 4));
        wave.setBackground(BG);
        wave.add(new Label("Realtime Waveform"), BorderLayout.NORTH);
        wave.add(waveform, BorderLayout.CENTER);
        panel.add(wave, BorderLayout.CENTER);
        return panel;
    }

    private Panel statusPanel() {
        Panel panel = new Panel(new BorderLayout(0, 8));
        panel.setPreferredSize(new Dimension(300, 1));
        panel.setBackground(BG);

        Panel logPanel = new Panel(new BorderLayout(0, 4));
        logPanel.setBackground(BG);
        logPanel.add(new Label("Status"), BorderLayout.NORTH);
        status.setEditable(false);
        status.setBackground(Color.white);
        logPanel.add(status, BorderLayout.CENTER);
        panel.add(logPanel, BorderLayout.CENTER);

        Panel progressRow = new Panel(new BorderLayout(8, 0));
        progressRow.setBackground(BG);
        progressRow.add(progress, BorderLayout.CENTER);
        percentLabel.setAlignment(Label.RIGHT);
        progressRow.add(percentLabel, BorderLayout.EAST);
        panel.add(progressRow, BorderLayout.SOUTH);
        return panel;
    }

    private Panel buttons() {
        Panel row = new Panel(new FlowLayout(FlowLayout.RIGHT, 14, 10));
        row.setBackground(BG);
        playButton.addActionListener(e -> start(false));
        stopButton.addActionListener(e -> stopWork());
        exportButton.addActionListener(e -> start(true));
        Button exit = new Button("Exit");
        exit.addActionListener(e -> {
            savePreferences();
            stopWork();
            dispose();
        });
        row.add(playButton);
        row.add(stopButton);
        row.add(exportButton);
        row.add(exit);
        return row;
    }

    private Panel fileRow(String label, TextField field, String buttonText, final Runnable action) {
        Panel row = new Panel(new BorderLayout(8, 0));
        row.setBackground(BG);
        Label l = new Label(label);
        l.setPreferredSize(new Dimension(86, 24));
        row.add(l, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        Button button = new Button(buttonText);
        button.setPreferredSize(new Dimension(88, 26));
        button.addActionListener(e -> action.run());
        row.add(button, BorderLayout.EAST);
        return row;
    }

    private Panel fieldCell(String label, Component component) {
        Panel row = new Panel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setBackground(BG);
        Label l = new Label(label);
        l.setPreferredSize(new Dimension(96, 24));
        component.setPreferredSize(new Dimension(100, 24));
        row.add(l);
        row.add(component);
        return row;
    }

    private Panel checkCell(Checkbox box) {
        Panel row = new Panel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setBackground(BG);
        row.add(box);
        return row;
    }

    private void chooseDls() {
        File file = choose(FileDialog.LOAD, "Open DLS Bank", "*.dls");
        if (file != null) {
            dlsField.setText(file.getAbsolutePath());
            savePreferences();
            log("DLS Bank: " + file.getAbsolutePath());
        }
    }

    private void chooseMidi() {
        File file = choose(FileDialog.LOAD, "Open MIDI File", "*.mid;*.midi");
        if (file != null) {
            midiField.setText(file.getAbsolutePath());
            savePreferences();
            log("MIDI File: " + file.getAbsolutePath());
        }
    }

    private File choose(int mode, String title, String pattern) {
        FileDialog dialog = new FileDialog(this, title, mode);
        dialog.setFile(pattern);
        dialog.setVisible(true);
        return dialog.getFile() == null ? null : new File(dialog.getDirectory(), dialog.getFile());
    }

    private void start(final boolean export) {
        if (worker != null && worker.isAlive()) {
            log("Already running.");
            return;
        }
        final File dls = existingFile(dlsField.getText(), "DLS bank");
        final File midi = existingFile(midiField.getText(), "MIDI file");
        if (dls == null || midi == null) {
            return;
        }

        final File out = export ? chooseExport(midi) : null;
        if (export && out == null) {
            return;
        }
        if (out != null && out.getParentFile() != null) {
            PREFS.put("exportDir", out.getParentFile().getAbsolutePath());
        }

        final int sampleRate = sampleRate();
        final String maxSecondsText = maxSecondsField.getText().trim();
        final int polyphony = positiveInt(polyphonyField, 256, "Polyphony");
        final boolean reverb = reverbBox.getState();
        final boolean chorus = chorusBox.getState();
        final boolean filterVibration = filterVibrationBox.getState();

        savePreferences();
        stopRequested = false;
        setBusy(true);
        setProgress(0);
        clearWaveform();

        worker = new Thread(() -> {
            try {
                DlsBank bank = MobileBae.loadDls(dls.toPath());
                log("Loading DLS bank: " + dls.getAbsolutePath());
                log("Instruments: " + bank.instruments.size() + "  Regions: " + bank.regionCount()
                        + "  Waves: " + bank.waves.size());

                MidiSong song = MobileBae.loadMidi(midi.toPath());
                log("Loading MIDI file: " + midi.getAbsolutePath());
                log("Events: " + song.events.size() + "  PPQ: " + song.division + "  Type: " + song.format);

                PcmStream stream = MobileBae.openStream(bank, song, sampleRate, maxSeconds(maxSecondsText, song),
                        reverb, chorus, polyphony, filterVibration, null);
                if (export) {
                    exportWav(stream, sampleRate, out);
                } else {
                    play(stream, sampleRate);
                }
            } catch (Exception ex) {
                log((export ? "Export" : "Playback") + " failed: " + ex.getMessage());
            } finally {
                activeLine = null;
                setBusy(false);
            }
        }, export ? "retro-dls-gui-export" : "retro-dls-gui-play");
        worker.setDaemon(true);
        worker.start();
    }

    private File chooseExport(File midi) {
        FileDialog dialog = new FileDialog(this, "Export WAV", FileDialog.SAVE);
        String dir = PREFS.get("exportDir", "");
        if (dir.length() == 0 && midi.getParentFile() != null) {
            dir = midi.getParentFile().getAbsolutePath();
        }
        if (dir.length() != 0) {
            dialog.setDirectory(dir);
        }
        dialog.setFile(defaultOutputName(midi.getName()));
        dialog.setVisible(true);
        return dialog.getFile() == null ? null : new File(dialog.getDirectory(), dialog.getFile());
    }

    private void play(PcmStream stream, int sampleRate) throws Exception {
        AudioFormat format = new AudioFormat(sampleRate, 16, 2, true, false);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
        activeLine = line;

        short[] pcm = new short[stream.blockFrames() * 2];
        byte[] bytes = new byte[pcm.length * 2];
        log("Playing...");
        try {
            line.open(format, bytes.length * 4);
            line.start();
            while (!stopRequested && !stream.finished()) {
                int frames = stream.read(pcm, 0, stream.blockFrames());
                showWaveform(pcm, frames);
                for (int i = 0, j = 0; i < frames * 2; i++) {
                    short sample = pcm[i];
                    bytes[j++] = (byte) sample;
                    bytes[j++] = (byte) (sample >>> 8);
                }
                line.write(bytes, 0, frames * 4);
                setProgress(stream.positionFrames() / (double) stream.totalFrames());
            }
            if (!stopRequested) {
                line.drain();
                setProgress(1);
                log("Playback complete.");
            } else {
                log("Playback stopped.");
            }
        } finally {
            line.stop();
            line.close();
        }
    }

    private void exportWav(PcmStream stream, int sampleRate, File out) throws Exception {
        short[] all = new short[stream.totalFrames() * 2];
        short[] chunk = new short[stream.blockFrames() * 2];
        int frame = 0;
        log("Rendering...");
        while (!stopRequested && frame < stream.totalFrames()) {
            int read = stream.read(chunk, 0, Math.min(stream.blockFrames(), stream.totalFrames() - frame));
            if (read == 0) {
                break;
            }
            showWaveform(chunk, read);
            System.arraycopy(chunk, 0, all, frame * 2, read * 2);
            frame += read;
            setProgress(frame / (double) stream.totalFrames());
        }
        if (stopRequested) {
            log("Stopped before writing WAV.");
            return;
        }
        Files.write(out.toPath(), MobileBae.wavBytes(all, sampleRate));
        setProgress(1);
        log("Rendering complete: " + out.getAbsolutePath());
    }

    private void stopWork() {
        stopRequested = true;
        SourceDataLine line = activeLine;
        if (line != null) {
            line.stop();
            line.flush();
        }
        if (worker != null && worker.isAlive()) {
            log("Stop requested.");
        }
    }

    private void setBusy(final boolean busy) {
        EventQueue.invokeLater(() -> {
            playButton.setEnabled(!busy);
            exportButton.setEnabled(!busy);
            stopButton.setEnabled(busy);
        });
    }

    private void setProgress(final double value) {
        final double clamped = Math.max(0, Math.min(1, value));
        final int percent = (int) Math.round(clamped * 100);
        if (percent == lastProgressPercent && percent != 0 && percent != 100) {
            return;
        }
        lastProgressPercent = percent;
        EventQueue.invokeLater(() -> {
            progress.setValue(clamped);
            percentLabel.setText(percent + "%");
        });
    }

    private void clearWaveform() {
        lastWaveformMillis = 0;
        lastProgressPercent = -1;
        EventQueue.invokeLater(() -> waveform.clear());
    }

    private void showWaveform(short[] stereo, int frames) {
        if (frames <= 0 || System.currentTimeMillis() - lastWaveformMillis < 50) {
            return;
        }
        lastWaveformMillis = System.currentTimeMillis();
        final int points = Math.min(160, frames);
        final int[] values = new int[points];
        for (int i = 0; i < points; i++) {
            int frame = i * frames / points;
            values[i] = (stereo[frame * 2] + stereo[frame * 2 + 1]) / 2;
        }
        EventQueue.invokeLater(() -> waveform.setSamples(values));
    }

    private File existingFile(String text, String label) {
        File file = new File(text.trim());
        if (!file.isFile()) {
            log(label + " does not exist: " + text);
            return null;
        }
        return file;
    }

    private String defaultOutputName(String name) {
        int dot = name.lastIndexOf('.');
        return (dot < 0 ? name : name.substring(0, dot)) + ".wav";
    }

    private int sampleRate() {
        return Integer.parseInt(sampleRateChoice.getSelectedItem());
    }

    private int maxSeconds(String text, MidiSong song) {
        if (text.length() == 0 || "auto".equalsIgnoreCase(text)) {
            return MobileBae.defaultMaxSeconds(song);
        }
        try {
            int value = Integer.parseInt(text);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the CLI-compatible automatic default.
        }
        EventQueue.invokeLater(() -> maxSecondsField.setText("auto"));
        log("Max Seconds must be positive or auto; using CLI default.");
        return MobileBae.defaultMaxSeconds(song);
    }

    private int positiveInt(TextField field, int fallback, String label) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Restore the known CLI default below.
        }
        field.setText(String.valueOf(fallback));
        log(label + " must be positive; using " + fallback + ".");
        return fallback;
    }

    private void log(final String message) {
        EventQueue.invokeLater(() -> status.append(message + "\n"));
    }

    private void loadPreferences() {
        dlsField.setText(PREFS.get("dls", ""));
        midiField.setText(PREFS.get("midi", ""));
        selectSampleRate(PREFS.get("sampleRate", "22050"));
        maxSecondsField.setText(PREFS.get("maxSeconds", "auto"));
        polyphonyField.setText(PREFS.get("polyphony", "256"));
        reverbBox.setState(PREFS.getBoolean("reverb", true));
        chorusBox.setState(PREFS.getBoolean("chorus", true));
        filterVibrationBox.setState(PREFS.getBoolean("filterVibration", true));
    }

    private void savePreferences() {
        PREFS.put("dls", dlsField.getText().trim());
        PREFS.put("midi", midiField.getText().trim());
        PREFS.put("sampleRate", sampleRateChoice.getSelectedItem());
        PREFS.put("maxSeconds", maxSecondsField.getText().trim());
        PREFS.put("polyphony", polyphonyField.getText().trim());
        PREFS.putBoolean("reverb", reverbBox.getState());
        PREFS.putBoolean("chorus", chorusBox.getState());
        PREFS.putBoolean("filterVibration", filterVibrationBox.getState());
        try {
            PREFS.flush();
        } catch (BackingStoreException ignored) {
            // Preferences are best-effort UI state; audio rendering must not depend on them.
        }
    }

    private void selectSampleRate(String value) {
        for (int i = 0; i < sampleRateChoice.getItemCount(); i++) {
            if (sampleRateChoice.getItem(i).equals(value)) {
                sampleRateChoice.select(i);
                return;
            }
        }
        sampleRateChoice.select("22050");
    }

    private static final class ProgressCanvas extends Canvas {
        private double value;

        ProgressCanvas() {
            setPreferredSize(new Dimension(230, 20));
            setBackground(Color.white);
        }

        void setValue(double value) {
            this.value = Math.max(0, Math.min(1, value));
            repaint();
        }

        public void update(Graphics g) {
            paint(g);
        }

        public void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(Color.white);
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(50, 150, 80));
            g.fillRect(1, 1, (int) ((w - 2) * value), h - 2);
            g.setColor(new Color(150, 150, 150));
            g.drawRect(0, 0, w - 1, h - 1);
        }
    }

    private static final class WaveformCanvas extends Canvas {
        private int[] samples = new int[0];

        WaveformCanvas() {
            setPreferredSize(new Dimension(420, 72));
            setBackground(new Color(28, 34, 42));
        }

        void clear() {
            samples = new int[0];
            repaint();
        }

        void setSamples(int[] samples) {
            this.samples = samples;
            repaint();
        }

        public void update(Graphics g) {
            paint(g);
        }

        public void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(getBackground());
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(75, 82, 90));
            g.drawLine(0, h / 2, w, h / 2);
            g.setColor(new Color(88, 190, 137));
            if (samples.length == 0) {
                g.drawString("Waiting for audio...", 12, 22);
                return;
            }
            int lastX = 0;
            int lastY = h / 2;
            for (int i = 0; i < samples.length; i++) {
                int x = samples.length == 1 ? 0 : i * (w - 1) / (samples.length - 1);
                int y = h / 2 - (samples[i] * (h / 2 - 8) / 32768);
                g.drawLine(lastX, lastY, x, y);
                lastX = x;
                lastY = y;
            }
        }
    }
}
