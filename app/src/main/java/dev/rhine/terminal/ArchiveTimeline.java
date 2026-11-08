/* Derived from rhine-lock frontend/src/App.svelte and lib/transition.ts,
 * AGPL-3.0-or-later, https://github.com/tmc5csxr9m-cpu/rhine-lock,
 * commit a4df68c6bd9e01ecb7660659becf495957e57faa. */
package dev.rhine.terminal;

/** Visual-only choreography. System authentication never enters this sequence. */
public final class ArchiveTimeline {
    public static final int FRAME = 0, PANELS = 1, IDLE = 2, SCAN = 3, DENIED = 4, EXIT = 5, HOME = 6;
    public static final long WAKE_MS = 2150, DEMO_MS = 18200, FIRST_HOLD_MS = 2600, SECOND_HOLD_MS = 2000;
    public static final int PULSE_ONE = 2500, SCAN_ONE = 3100, DENY_ONE = 5100, RECOVER_ONE = 7700;
    public static final int SCAN_TWO = 8800, DENY_TWO = 10450, RECOVER_TWO = 12450;
    public static final int SCAN_THREE = 13600, EXIT_AT = 15250, HOME_AT = 16650;
    public static final float IDLE_SECONDS = 24;
    private static final double LOOP_PHASE = 2 * Math.PI / IDLE_SECONDS;
    private static final double PHASE_ORIGIN = .68 * Math.sin(-.38) + .23 * Math.sin(.60);

    private ArchiveTimeline() {}

    public static final class FrameState {
        public int stage, intensity;
        public float birth, pulse, scan, scanHead, scanOpacity, peel, snapshot, seconds, burst;
        public float foundation, card, telemetry, caption;
        public float snapshotTravel, snapshotScan, snapshotCorners, snapshotDesat, denyMix, recoveryGlitch;
        public float disorder;
        public boolean idleBlack;
        boolean nativeFailure;
        int biometricDemo;

        public void reset() {
            stage = IDLE;
            intensity = 0;
            birth = foundation = card = telemetry = caption = 1;
            pulse = scan = scanHead = scanOpacity = peel = snapshot = seconds = burst = 0;
            snapshotTravel = snapshotCorners = snapshotDesat = denyMix = recoveryGlitch = 0;
            snapshotScan = -1;
            disorder = 0;
            idleBlack = false;
            nativeFailure = false;
            biometricDemo = 0;
        }

        void deny(int severity, boolean system, float time) {
            stage = DENIED;
            intensity = severity;
            seconds = time;
            denyMix = 1 - (float)Math.exp(-28 * Math.max(0, time));
            recoveryGlitch = 0;
            nativeFailure = system;
            birth = foundation = card = telemetry = caption = 1;
            pulse = scan = scanHead = scanOpacity = peel = snapshot = 0;
            // Upstream holds a single 300 ms tear, then follows zero at 14/s.
            // Disorder is a separate slow 3.2/s shape response, never camera recoil.
            burst = time < .3f ? 1 : (float)Math.exp(-14 * (time - .3f));
            disorder = .12f * (1 - (float)Math.exp(-3.2f * Math.max(0, time)));
            pulse = .5f * ArchiveTimeline.pulse((long)(time * 1000));
        }
    }

    static void wake(FrameState frame, long ms, boolean withSnapshot) {
        frame.stage = ms < 560 ? FRAME : ms < 1030 ? PANELS : IDLE;
        float reveal = outCubic(progress(ms, withSnapshot ? 480 : 700, withSnapshot ? 1350 : 1500));
        frame.birth = cosine(progress(ms, withSnapshot ? 120 : 450, withSnapshot ? 2020 : 2150));
        frame.foundation = Math.min(1, reveal * 2.2f);
        frame.card = withSnapshot ? smooth(progress(ms,880,1150)) : clamp(reveal * 1.8f - .2f);
        frame.telemetry = Math.min(1, reveal * 2f);
        frame.caption = Math.min(1, reveal * 2f);
        if (withSnapshot) {
            frame.snapshot = 1 - smooth(progress(ms,950,1200));
            frame.snapshotTravel = inOutCubic(progress(ms,0,1050));
            frame.snapshotScan = (ms - 80) / 440f;
            frame.snapshotCorners = 1 - smooth(progress(ms,560,860));
            frame.snapshotDesat = smooth(progress(ms,60,600));
        }
    }

    /** Resume the ready palette after the held denial, including upstream's recovery tear. */
    public static void recover(FrameState frame, long ageMs) {
        float age = Math.max(0, ageMs) * .001f;
        frame.seconds = age;
        frame.denyMix = (float)Math.exp(-28 * age);
        frame.recoveryGlitch = ageMs < 260 ? .45f : .45f * (float)Math.exp(-14 * (age - .260f));
        frame.disorder = .12f * (float)Math.exp(-3.2f * age);
    }

    public static void demo(FrameState frame, long ms) {
        if (ms < 0 || ms >= DEMO_MS) return;
        if (ms < WAKE_MS) wake(frame, ms, true);
        else if (ms < SCAN_ONE) frame.pulse = pulse(ms - PULSE_ONE);
        else if (ms < DENY_ONE) verify(frame, ms, SCAN_ONE);
        else if (ms < RECOVER_ONE) frame.deny(1, false, (ms - DENY_ONE) * .001f);
        else if (ms < SCAN_TWO) { recover(frame, ms - RECOVER_ONE); frame.pulse = pulse(ms - 8100); }
        else if (ms < DENY_TWO) verify(frame, ms, SCAN_TWO);
        else if (ms < RECOVER_TWO) frame.deny(2, false, (ms - DENY_TWO) * .001f);
        else if (ms < SCAN_THREE) { recover(frame, ms - RECOVER_TWO); frame.pulse = pulse(ms - 12900); }
        else if (ms < EXIT_AT) verify(frame, ms, SCAN_THREE);
        else {
            long exitAge = ms - EXIT_AT;
            frame.stage = ms >= HOME_AT ? HOME : EXIT;
            float ui = 1 - smooth(progress(exitAge,180,600));
            float dissolve = progress(exitAge,220,1020);
            frame.peel = dissolve * dissolve;
            frame.telemetry = frame.caption = frame.foundation = ui;
            frame.card = 1 - smooth(progress(exitAge,420,620));
            frame.snapshot = smooth(progress(exitAge,380,600));
            frame.snapshotTravel = 1 - inOutCubic(progress(exitAge,560,1220));
            frame.snapshotCorners = smooth(progress(exitAge,700,950)) * (1 - smooth(progress(exitAge,1120,1220)));
            frame.snapshotDesat = 1 - smooth(progress(exitAge,600,1150));
        }
    }

    private static void verify(FrameState frame, long ms, long start) {
        float seconds = Math.max(0, ms - start) * .001f;
        frame.stage = SCAN;
        frame.scan = .92f * (1 - (float)Math.exp(-1.4f * seconds));
        frame.scanHead = frame.scan;
        frame.scanOpacity = 1 - (float)Math.exp(-6f * seconds);
        frame.seconds = seconds;
    }

    static float pulse(long age) {
        if (age < 0 || age >= 1400) return 0;
        float attack = outCubic(progress(age,0,155));
        float decay = 1 - quintic(progress(age,155,1400));
        return attack * decay;
    }

    /** Continuous periodic retiming. Speed varies without reversing at the loop seam. */
    public static float motionTime(double seconds) {
        double t = seconds % IDLE_SECONDS;
        double phase = t * LOOP_PHASE;
        return (float)(t + .68 * Math.sin(2 * phase - .38) + .23 * Math.sin(5 * phase + .60) - PHASE_ORIGIN);
    }

    public static float homeReveal(float t, boolean fromVisible) {
        if (!fromVisible) {
            return .64f * outCubic(progress(t,0,.46f)) + .36f * quintic(progress(t,.28f,1));
        }
        return 1 - .29f * quintic(progress(t,0,.24f)) + .29f * quintic(progress(t,.31f,1));
    }

    private static float progress(float value, float start, float end) {
        return Math.max(0f,Math.min(1f,(value-start)/(end-start)));
    }

    private static float clamp(float x) { return Math.max(0, Math.min(1, x)); }
    private static float cosine(float t) { return .5f - .5f * (float)Math.cos(Math.PI * t); }
    private static float inOutCubic(float t) {
        return t < .5f ? 4*t*t*t : 1 - (float)Math.pow(-2*t+2,3)/2;
    }
    private static float smooth(float t) { return t*t*(3-2*t); }
    private static float quintic(float t) { return t*t*t*(t*(6*t-15)+10); }
    private static float outCubic(float t) { return 1-(1-t)*(1-t)*(1-t); }
}
