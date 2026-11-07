package dev.rhine.terminal;

import java.util.function.BooleanSupplier;

/** Conservative, short Z-axis impulse detector. Times are monotonic nanoseconds, acceleration m/s². */
public final class SoftTapDetector {
    private static final long QUIET_NS = 120_000_000L, MAX_PULSE_NS = 200_000_000L;
    private static final long COOLDOWN_NS = 800_000_000L;
    private long lastTime, quietSince, candidateAt, lastTrigger;
    private float gx, gy, gz, noise;
    private boolean initialized;

    public void reset() {
        initialized = false;
        noise = 0;
        lastTime = quietSince = candidateAt = lastTrigger = 0;
    }

    /** Share the cooldown with a real touch or wallpaper command. */
    public boolean acceptDirect(long now) {
        if (lastTrigger != 0 && now - lastTrigger < COOLDOWN_NS) return false;
        lastTrigger = now;
        candidateAt = 0;
        return true;
    }

    public boolean sample(long now, float x, float y, float z, BooleanSupplier armed) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) { reset(); return false; }
        if (!initialized || now <= lastTime || now - lastTime > 250_000_000L) {
            gx=x; gy=y; gz=z; noise=0; lastTime=quietSince=now; candidateAt=0; initialized=true;
            return false;
        }
        float dt = (now-lastTime)*1e-9f;
        lastTime=now;
        float dx=x-gx, dy=y-gy, dz=z-gz;
        float lateral=(float)Math.sqrt(dx*dx+dy*dy), total=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        // Track gravity slowly enough to keep a tap visible, quickly enough to follow device pose.
        float alpha=dt/(.35f+dt);
        gx+=alpha*dx; gy+=alpha*dy; gz+=alpha*dz;
        if (candidateAt != 0) {
            if (now-candidateAt > MAX_PULSE_NS || total > 6f || lateral > 3f) {
                candidateAt=0; quietSince=now;
            } else if (total < .38f) {
                candidateAt=0; quietSince=now;
                return armed.getAsBoolean() && acceptDirect(now);
            }
            return false;
        }
        if (total < .38f) {
            if (quietSince == 0) quietSince=now;
            noise += Math.min(1f, dt / .6f) * (total - noise);
        } else {
            if (quietSince != 0 && now-quietSince >= QUIET_NS && Math.abs(dz) >= Math.max(.58f, noise*4f)
                    && Math.abs(dz) >= lateral*1.35f && total <= 5f && lateral < 2.2f
                    && (lastTrigger == 0 || now-lastTrigger >= COOLDOWN_NS)) candidateAt=now;
            quietSince=0;
        }
        return false;
    }
}
