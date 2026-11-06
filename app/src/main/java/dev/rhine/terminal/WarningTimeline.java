package dev.rhine.terminal;

/** One rapid inversion, a readable hold, and a single recovery. Shared with the desktop harness. */
public final class WarningTimeline {
    public static final float ENTER_SECONDS = .10f;
    public static final float HOLD_UNTIL_SECONDS = 2.66f;
    public static final float END_SECONDS = 3.22f;
    public static final float LOOP_SECONDS = 13f;
    private WarningTimeline() {}

    public static float intensity(float seconds) {
        if (seconds < 0 || seconds >= END_SECONDS) return 0;
        if (seconds < ENTER_SECONDS) return smooth(seconds / ENTER_SECONDS);
        if (seconds < HOLD_UNTIL_SECONDS) return 1;
        return 1 - smooth((seconds - HOLD_UNTIL_SECONDS) / (END_SECONDS - HOLD_UNTIL_SECONDS));
    }

    private static float smooth(float x) { return x*x*(3-2*x); }
}
