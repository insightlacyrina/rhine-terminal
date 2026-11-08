/* SPDX-License-Identifier: AGPL-3.0-or-later
 * Cues, gains and timing adapted from rhine-lock frontend/src/lib/audio.ts,
 * App.svelte and lib/lock.svelte.ts at a4df68c6bd9e01ecb7660659becf495957e57faa.
 * https://github.com/tmc5csxr9m-cpu/rhine-lock
 */
package dev.rhine.terminal;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Opt-in interface cues. The host stops playback whenever its surface is hidden. */
final class ArchivePreviewAudio {
    private static final float SFX_VOLUME = .7f;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler sequence = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final NotificationManager notifications;
    private final SoundPool sound;
    private final Map<String, Cue> cues = new HashMap<>();
    private final Set<Integer> loaded = new HashSet<>();
    private final Map<String, Long> pending = new HashMap<>();
    private final Random random = new Random();
    private final int[] streams = new int[8];
    private int streamIndex, processStream;
    private boolean active = true, released, processing;

    private static final class Cue {
        final int[] samples;
        final float gain;
        final boolean pitchSpread;

        Cue(int[] samples, float gain, boolean pitchSpread) {
            this.samples = samples;
            this.gain = gain;
            this.pitchSpread = pitchSpread;
        }
    }

    private final Runnable monitorProcessing = new Runnable() {
        @Override public void run() {
            if (processStream == 0) return;
            if (!canPlay()) setProcessingNow(false);
            else main.postDelayed(this, 200);
        }
    };

    ArchivePreviewAudio(Context context) {
        Context app = context.getApplicationContext();
        audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        notifications = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        sound = new SoundPool.Builder().setMaxStreams(6)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        sound.setOnLoadCompleteListener((pool, sample, status) -> main.post(() -> {
            if (released) return;
            if (status != 0) {
                Log.w("RhineAudio", "Interface cue could not be loaded");
                return;
            }
            loaded.add(sample);
            Cue process = cues.get("process");
            if (processing && process != null && process.samples[0] == sample) startProcessing();
            flushPending();
        }));
        main.post(() -> {
            if (released) return;
            add(app, "key", .62f, true, R.raw.rhine_key1, R.raw.rhine_key2, R.raw.rhine_key3);
            add(app, "erase", .6f, false, R.raw.rhine_erase);
            add(app, "request", .85f, false, R.raw.rhine_request);
            add(app, "process", .8f, false, R.raw.rhine_process);
            add(app, "warning", .92f, false, R.raw.rhine_warning);
            add(app, "glitch", .7f, false, R.raw.rhine_glitch1, R.raw.rhine_glitch2, R.raw.rhine_glitch3);
            add(app, "recover", .75f, false, R.raw.rhine_recover);
            add(app, "nav", .66f, true, R.raw.rhine_nav);
            add(app, "toggle", .7f, false, R.raw.rhine_toggle);
            add(app, "arm", .8f, false, R.raw.rhine_arm);
            add(app, "execute", .85f, false, R.raw.rhine_execute);
            add(app, "standby", .7f, false, R.raw.rhine_standby);
            add(app, "wake", .72f, false, R.raw.rhine_wake);
            add(app, "access", .7f, false, R.raw.rhine_access);
            add(app, "draw", .85f, false, R.raw.rhine_draw);
            add(app, "blip", .7f, false, R.raw.rhine_blip1, R.raw.rhine_blip2);
            add(app, "assemble", .9f, false, R.raw.rhine_assemble);
            add(app, "reveal", .78f, false, R.raw.rhine_reveal);
            add(app, "choose", .82f, false, R.raw.rhine_choose);
            add(app, "error", .7f, false, R.raw.rhine_error);
            // Shipped by the upstream theme alongside its currently used cue table.
            add(app, "open", .7f, false, R.raw.rhine_open);
            add(app, "close", .7f, false, R.raw.rhine_close);
            add(app, "switch", .7f, false, R.raw.rhine_switch);
        });
    }

    private void add(Context context, String name, float gain, boolean pitchSpread, int... resources) {
        int[] samples = new int[resources.length];
        for (int i = 0; i < resources.length; i++) samples[i] = sound.load(context, resources[i], 1);
        cues.put(name, new Cue(samples, gain, pitchSpread));
    }

    /** Upstream plain entry used when no desktop snapshot is available. */
    void playEntry() {
        main.post(() -> {
            stopNow();
            if (!active || released) return;
            playCueNow("access");
            at(60, () -> playCueNow("draw"));
            at(450, () -> playCueNow("assemble"));
            at(700, () -> playCueNow("reveal"));
        });
    }

    /** Complete visual demo, following the upstream snapshot entry and exit cues. */
    void play() {
        main.post(() -> {
            stopNow();
            if (!active || released) return;
            playCueNow("access");
            at(60, () -> playCueNow("blip"));
            at(120, () -> playCueNow("assemble"));
            at(480, () -> playCueNow("reveal"));
            scanAt(ArchiveTimeline.SCAN_ONE);
            at(ArchiveTimeline.DENY_ONE, this::denialNow);
            at(ArchiveTimeline.RECOVER_ONE, () -> playCueNow("recover"));
            scanAt(ArchiveTimeline.SCAN_TWO);
            at(ArchiveTimeline.DENY_TWO, this::denialNow);
            at(ArchiveTimeline.RECOVER_TWO, () -> playCueNow("recover"));
            scanAt(ArchiveTimeline.SCAN_THREE);
            at(ArchiveTimeline.EXIT_AT, () -> {
                setProcessingNow(false);
                playCueNow("choose");
            });
            at(ArchiveTimeline.EXIT_AT + 560, () -> playCueNow("reveal"));
            at(ArchiveTimeline.DEMO_MS, this::stopNow);
        });
    }

    private void scanAt(long when) {
        at(when, () -> {
            playCueNow("request");
            setProcessingNow(true);
        });
    }

    private void at(long delay, Runnable event) { sequence.postDelayed(event, delay); }

    void playCue(String name) { main.post(() -> playCueNow(name)); }
    void setProcessing(boolean value) { main.post(() -> setProcessingNow(value)); }
    void playDenial() { main.post(this::denialNow); }
    void playRecover() { playCue("recover"); }
    void playStandby() { playCue("standby"); }
    void playWake() { playCue("wake"); }

    /** A preview-only denial followed by the same recovery cue as the full demo. */
    void playDenialDemo(long holdMs) {
        main.post(() -> {
            stopNow();
            if (!active || released) return;
            denialNow();
            at(Math.max(0, holdMs), () -> playCueNow("recover"));
        });
    }

    private void denialNow() {
        setProcessingNow(false);
        playCueNow("warning");
        playCueNow("glitch");
    }

    private void playCueNow(String name) {
        if (!canPlay()) return;
        Cue cue = cues.get(name);
        if (cue == null) return;
        int first = random.nextInt(cue.samples.length);
        for (int i = 0; i < cue.samples.length; i++) {
            int sample = cue.samples[(first + i) % cue.samples.length];
            if (!loaded.contains(sample)) continue;
            float level = amplitude(SFX_VOLUME * cue.gain);
            float rate = cue.pitchSpread ? .97f + random.nextFloat() * .06f : 1;
            int stream = sound.play(sample, level, level, 1, 0, rate);
            if (stream != 0) {
                if (streams[streamIndex] != 0) sound.stop(streams[streamIndex]);
                streams[streamIndex] = stream;
                streamIndex = (streamIndex + 1) % streams.length;
            }
            return;
        }
        // Decode can finish just after the first visible frame. Coalesce repeated
        // cold-start cues and discard them once they would fall out of sync.
        pending.put(name, SystemClock.uptimeMillis() + 120);
    }

    private void flushPending() {
        long now = SystemClock.uptimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now > entry.getValue() || !canPlay()) {
                iterator.remove();
                continue;
            }
            Cue cue = cues.get(entry.getKey());
            for (int sample : cue.samples) {
                if (!loaded.contains(sample)) continue;
                String name = entry.getKey();
                iterator.remove();
                playCueNow(name);
                break;
            }
        }
    }

    private void setProcessingNow(boolean value) {
        processing = value && active && !released;
        if (processing) startProcessing();
        else {
            main.removeCallbacks(monitorProcessing);
            if (processStream != 0) sound.stop(processStream);
            processStream = 0;
        }
    }

    private void startProcessing() {
        Cue cue = cues.get("process");
        if (processStream != 0 || cue == null || !loaded.contains(cue.samples[0]) || !canPlay()) return;
        float level = amplitude(SFX_VOLUME * cue.gain);
        processStream = sound.play(cue.samples[0], level, level, 1, -1, 1);
        if (processStream != 0) main.postDelayed(monitorProcessing, 200);
    }

    private boolean canPlay() {
        if (!active || released || audio == null || audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL
                || audio.getMode() != AudioManager.MODE_NORMAL || audio.isMusicActive()
                || audio.getStreamVolume(AudioManager.STREAM_SYSTEM) == 0) return false;
        int filter = notifications == null ? NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                : notifications.getCurrentInterruptionFilter();
        return filter == NotificationManager.INTERRUPTION_FILTER_ALL
                || filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
    }

    /** Upstream's perceptual volume curve spans 42 dB. */
    private static float amplitude(float value) {
        return value <= .001f ? 0 : (float)Math.pow(10, (value - 1) * 42 / 20);
    }

    void setActive(boolean value) {
        main.post(() -> {
            active = value;
            if (!value) stopNow();
        });
    }

    void stop() { main.post(this::stopNow); }

    private void stopNow() {
        sequence.removeCallbacksAndMessages(null);
        pending.clear();
        setProcessingNow(false);
        for (int i = 0; i < streams.length; i++) {
            if (streams[i] != 0) sound.stop(streams[i]);
            streams[i] = 0;
        }
        streamIndex = 0;
    }

    void release() {
        main.post(() -> {
            if (released) return;
            active = false;
            stopNow();
            released = true;
            loaded.clear();
            sound.release();
        });
    }
}
