package dev.rhine.terminal;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;

/** Local preview soundtrack. Wallpaper services never create an audio player. */
final class ArchivePreviewAudio {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SoundPool sound;
    private final int bed, denied, resolved;
    private int bedStream;
    private int cueStream;

    ArchivePreviewAudio(Context context) {
        sound = new SoundPool.Builder().setMaxStreams(2)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).build();
        bed = sound.load(context,R.raw.archive_bed,1);
        denied = sound.load(context,R.raw.archive_denied,1);
        resolved = sound.load(context,R.raw.archive_resolved,1);
    }

    void play() {
        stop();
        bedStream = sound.play(bed,.34f,.34f,1,-1,1);
        main.postDelayed(() -> cue(denied, .63f, 1), ArchiveTimeline.DENY_ONE);
        main.postDelayed(() -> cue(denied, .75f, .91f), ArchiveTimeline.DENY_TWO);
        main.postDelayed(() -> cue(resolved, .65f, 1), ArchiveTimeline.EXIT_AT);
        for (int i = 1; i <= 4; i++) {
            float level = .34f*(1-i/4f);
            main.postDelayed(() -> sound.setVolume(bedStream,level,level),ArchiveTimeline.DEMO_MS-480+i*110);
        }
        main.postDelayed(this::stop,ArchiveTimeline.DEMO_MS);
    }

    private void cue(int id, float level, float rate) {
        cueStream = sound.play(id,level,level,2,0,rate);
    }

    void stop() {
        main.removeCallbacksAndMessages(null);
        if (bedStream != 0) sound.stop(bedStream);
        if (cueStream != 0) sound.stop(cueStream);
        bedStream = cueStream = 0;
    }

    void release() {
        stop();
        sound.release();
    }
}
