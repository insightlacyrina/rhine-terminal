package dev.rhine.terminal;

/** Conservative, short Z-axis impulse detector. Times are monotonic nanoseconds, acceleration m/s². */
public final class SoftTapDetector {
    private static final long QUIET_NS = 140_000_000L, MAX_PULSE_NS = 150_000_000L;
    private static final long COOLDOWN_NS = 800_000_000L;
    private long lastTime, quietSince, candidateAt, lastTrigger;
    private float gx, gy, gz;
    private boolean initialized;

    public void reset() {
        initialized = false;
        lastTime = quietSince = candidateAt = lastTrigger = 0;
    }

    /** Share the cooldown with a real touch or wallpaper command. */
    public boolean acceptDirect(long now) {
        if (lastTrigger != 0 && now - lastTrigger < COOLDOWN_NS) return false;
        lastTrigger = now;
        candidateAt = 0;
        return true;
    }

    public boolean sample(long now, float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) { reset(); return false; }
        if (!initialized || now <= lastTime || now - lastTime > 250_000_000L) {
            gx=x; gy=y; gz=z; lastTime=quietSince=now; candidateAt=0; initialized=true;
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
            } else if (total < .8f) {
                candidateAt=0; quietSince=now;
                return acceptDirect(now);
            }
            return false;
        }
        if (total < .8f) {
            if (quietSince == 0) quietSince=now;
        } else {
            if (quietSince != 0 && now-quietSince >= QUIET_NS && Math.abs(dz) >= 1.35f
                    && Math.abs(dz) >= lateral*1.2f && total <= 6f && lateral < 3f
                    && (lastTrigger == 0 || now-lastTrigger >= COOLDOWN_NS)) candidateAt=now;
            quietSince=0;
        }
        return false;
    }
}
