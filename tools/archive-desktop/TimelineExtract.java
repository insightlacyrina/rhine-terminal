package dev.rhine.terminal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Samples Android's choreography and periodic motion for the standalone proof. */
public final class TimelineExtract {
    public static void main(String[] args) throws Exception {
        StringBuilder out = new StringBuilder("{\"timeline\":[");
        ArchiveTimeline.FrameState s = new ArchiveTimeline.FrameState();
        for (long ms = 0; ms <= ArchiveTimeline.DEMO_MS; ms += 5) {
            s.reset();
            ArchiveTimeline.demo(s, Math.min(ms, ArchiveTimeline.DEMO_MS - 1));
            if (ms != 0) out.append(',');
            out.append(String.format(Locale.US, "[%d,%d,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f]",
                    s.stage, s.intensity, s.birth, s.pulse, s.scan, s.peel, s.snapshot,
                    s.seconds, s.scanHead, s.scanOpacity, s.foundation, s.card,
                    s.telemetry, s.caption, s.burst, s.snapshotTravel, s.snapshotScan,
                    s.snapshotCorners, s.snapshotDesat, s.denyMix, s.recoveryGlitch, s.disorder));
        }
        out.append("],\"motion\":[");
        for (int ms = 0; ms <= 24000; ms += 5) {
            if (ms != 0) out.append(',');
            out.append(String.format(Locale.US, "%.7f", (ms == 24000 ? 24.0f : ArchiveTimeline.motionTime(ms / 1000.0))));
        }
        out.append("]}");
        Files.writeString(Path.of(args[0]), out.toString());
    }
}
