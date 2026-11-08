package dev.rhine.terminal;

/** Visual inactivity only. This clock never infers an authentication result. */
final class LockIdleTimeline {
    static final long TIMEOUT_NS = 5_000_000_000L;
    static final long TRANSIENT_NS = 500_000_000L;
    static final long RECOVERY_NS = 550_000_000L;
    private long lastInteraction, darkSince, recoverSince;
    private float recoverMix;

    void reset(long now) {
        lastInteraction = now;
        darkSince = recoverSince = 0;
        recoverMix = 0;
    }

    void interact(long now) {
        if (darkSince != 0) {
            recoverMix = 1 - (float)Math.exp(-28 * Math.max(0, now - darkSince) * 1e-9);
            recoverSince = now;
            darkSince = 0;
        }
        lastInteraction = now;
    }

    void apply(ArchiveTimeline.FrameState frame, long now, boolean enabled, boolean reduced) {
        if (!enabled || lastInteraction == 0) {
            reset(now);
            return;
        }
        // A delayed display callback can carry a timestamp older than the touch
        // that was just processed. Preserve its recovery instead of resetting it.
        now = Math.max(now, lastInteraction);
        if (recoverSince != 0) {
            float t = Math.max(0, now - recoverSince) / (float)RECOVERY_NS;
            if (t < 1) {
                float ease = t*t*t*(t*(6*t - 15) + 10);
                frame.idleBlack = true;
                frame.denyMix = reduced ? 0 : recoverMix * (1 - ease);
                return;
            }
            recoverSince = 0;
        }
        if (now - lastInteraction < TIMEOUT_NS) return;
        if (darkSince == 0) darkSince = lastInteraction + TIMEOUT_NS;
        long age = Math.max(0, now - darkSince);
        frame.idleBlack = true;
        frame.seconds = age * 1e-9f;
        frame.denyMix = reduced ? 1 : 1 - (float)Math.exp(-28 * frame.seconds);
        if (!reduced && age < TRANSIENT_NS) {
            float t = age / (float)TRANSIENT_NS;
            frame.burst = .42f * (float)Math.sin(Math.PI * t) * (1 - t) * (1 - t);
        }
    }
}
