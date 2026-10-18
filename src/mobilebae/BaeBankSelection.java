package mobilebae;

/**
 * Instrument-bank selector recovered from mBAE_base14.dll, 1.2.0 installer.
 *
 * Native function 0x02266B00 computes the 14-bit internal selector:
 * bits 13..7 = bank MSB, bits 6..0 = bank LSB.
 * The five native system modes are represented by 0..4, not inferred from
 * external MIDI specifications.
 */
final class BaeBankSelection {
    static final int DRUM_BANK = 120 << 7;
    static final int MELODIC_BANK = 121 << 7;

    private BaeBankSelection() { }

    /** Recover the native 0x02266B00 behavior for ordinary channel MIDI events. */
    static int select(int mode, int channel, int bankMsb, int bankLsb) {
        return select(mode, channel, bankMsb, bankLsb, 0xFF, 0xFF);
    }

    /** Native per-channel explicit overrides at offsets +0x0E and +0x0F. */
    static int select(int mode, int channel, int bankMsb, int bankLsb,
                      int overrideMsb, int overrideLsb) {
        // 0x02266B2D..0x02266B56: the override is active only if both
        // bytes are not 0xFF, and it takes precedence over mode routing.
        if ((overrideMsb & 0xFF) != 0xFF && (overrideLsb & 0xFF) != 0xFF) {
            return ((overrideMsb & 0x7F) << 7) | (overrideLsb & 0x7F);
        }
        int msb = bankMsb & 0x7F;
        int lsb = bankLsb & 0x7F;
        int base = channel == 9 ? DRUM_BANK : MELODIC_BANK;
        switch (mode) {
            case 1: // GM system on, mode 0x02266B6D
            case 3: // GS reset, same basic selection branch
                return base;
            case 2: // GM2, mode 0x02266C0F: true 14-bit Bank Select
                return (msb << 7) | lsb;
            case 4: // XG, mode 0x02266B85, 128-entry routing table
                // The exact table has drum tags only at 126 and 127.
                return msb == 126 || msb == 127 ? DRUM_BANK : MELODIC_BANK;
            case 0: // Auto, mode 0x02266BB4
                // Explicit GM2 bank selectors bypass GS/GM1 bank translation.
                if (msb == 120 || msb == 121) {
                    return (msb << 7) | lsb;
                }
                // GS-style: controller 0 supplies the variation if LSB is zero.
                if (msb != 0 && lsb == 0) {
                    return base | msb;
                }
                // GM2-like LSB variation with a zero MSB.
                if (msb == 0 && lsb != 0) {
                    return base | lsb;
                }
                // Both controllers nonzero but MSB not 120/121: base bank.
                return base;
            default:
                return 0;
        }
    }
}
