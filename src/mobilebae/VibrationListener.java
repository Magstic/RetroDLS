package mobilebae;

/** Callback for vibration note events exposed by Retro DLS rendering. */
public interface VibrationListener {
    void vibration(long micros, int channel, int key, int velocity, boolean on);
}
