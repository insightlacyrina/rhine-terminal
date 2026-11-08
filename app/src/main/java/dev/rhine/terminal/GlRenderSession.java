package dev.rhine.terminal;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Owns EGL and the surface on a dedicated looper. All GL calls happen on that looper. */
final class GlRenderSession {
    private static final String TAG = "RhineGL";
    private final Context context;
    private final SharedPreferences prefs;
    private final KeyguardManager keyguard;
    private final PowerManager power;
    private final boolean home;
    private final Paint homeFallbackPaint;
    private final HandlerThread thread = new HandlerThread("Rhine-render");
    private final Handler handler;
    private Choreographer choreographer;
    private Surface surface;
    private int width, height;
    private boolean active, posted, waiting, fallback, locked;
    private volatile boolean requestedActive, closed, lockPreview;
    private int requestedRate=120;
    private volatile float inputX, inputY;
    private volatile int battery = -1;
    private volatile boolean plugged;
    private boolean showHomeTelemetry = true, lightHome = true, darkGallery, carbonConsole;
    private boolean phoneLayout;
    private String lockDisplayName = LockDisplayName.DEFAULT;
    private final ArchiveTimeline.FrameState lockFrame = new ArchiveTimeline.FrameState();
    private final LockIdleTimeline lockIdle = new LockIdleTimeline();
    private final HomeDeviceTelemetry lockTelemetry;
    private ArchivePreviewAudio audio;
    private boolean soundIdle, soundDenied;
    private long wakeEpoch, pulseEpoch, demoEpoch, pendingAlert;
    private int denyLevel, biometricDemo;
    private long biometricDemoEpoch;
    private boolean pendingNative, wakePending, demoPending;
    private boolean homeUnfoldPending;
    private boolean homeFrameSubmitted, homeUnfoldFromVisible;
    private long homeUnfoldStart;
    private long lastKeyguard, lastFrame, statsStart;
    private int frames, samples, hintedRate = -1;
    private final long[] durations = new long[1024], sorted = new long[1024];
    private volatile String stats = "等待画面";
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private GpuTerminalRenderer gpu;
    private HomeWallpaperRenderer homeGpu;
    private ArchiveFallback cpu;
    private final Choreographer.FrameCallback callback = this::frame;
    private final Runnable wake = () -> { waiting=false; schedule(); };

    GlRenderSession(Context context) {
        this(context, false);
    }

    GlRenderSession(Context context, boolean home) {
        this.context = context.getApplicationContext();
        this.home = home;
        homeFallbackPaint = home ? new Paint(Paint.ANTI_ALIAS_FLAG) : null;
        prefs = this.context.getSharedPreferences("terminal", Context.MODE_PRIVATE);
        keyguard = (KeyguardManager) this.context.getSystemService(Context.KEYGUARD_SERVICE);
        power = (PowerManager) this.context.getSystemService(Context.POWER_SERVICE);
        lockTelemetry = home ? null : new HomeDeviceTelemetry(this.context);
        thread.start(); handler = new Handler(thread.getLooper());
        handler.post(() -> choreographer = Choreographer.getInstance());
    }
    void setSurface(Surface value, int w, int h) {
        if(closed) return;
        handler.post(() -> {
            if (surface != value) { tearDown(); surface = value; }
            width = w; height = h;
            if (gpu != null && makeCurrent()) gpu.resize(width, height);
            schedule();
        });
    }
    void setActive(boolean value) {
        if(closed) return;
        requestedActive=value;
        handler.post(() -> {
            if (value && !active && !home) {
                wakePending = true;
                lockIdle.reset(System.nanoTime());
            }
            active=value;
            readPreferences();
            if (lockTelemetry != null) lockTelemetry.setActive(value);
            if (audio != null) audio.setActive(value);
            if(value) schedule(); else cancel();
        });
    }
    void setLockPreview(boolean preview) { lockPreview = preview; }
    void setInput(float x, float y, int battery) { inputX = x; inputY = y; this.battery = battery; }
    void setBattery(int level, boolean externalPower) { battery = level; plugged = externalPower; }

    void unfoldHome() {
        unfoldHome(false);
    }

    void unfoldHome(boolean automatic) {
        if (!home || closed) return;
        handler.post(() -> {
            if (!active || requestedRate == 1) return;
            homeUnfoldPending = true;
            homeUnfoldFromVisible = automatic && homeFrameSubmitted;
            lastFrame = 0;
            handler.removeCallbacks(wake);
            waiting = false;
            schedule();
        });
    }
    void pulse() {
        if (closed || home) return;
        handler.post(() -> {
            pulseEpoch = System.nanoTime();
            lockIdle.interact(pulseEpoch);
            lastFrame = 0;
            handler.removeCallbacks(wake);
            waiting = false;
            schedule();
        });
    }
    void playArchiveDemo() {
        if (closed || home) return;
        handler.post(() -> {
            demoPending = true;
            biometricDemoEpoch = 0;
            lastFrame = 0;
            handler.removeCallbacks(wake);
            waiting = false;
            schedule();
        });
    }
    void interact() {
        if (!closed && !home) handler.post(() -> lockIdle.interact(System.nanoTime()));
    }
    void playBiometricDemo(int source) {
        if (closed || !lockPreview || home) return;
        handler.post(() -> {
            demoEpoch=0;
            demoPending=false;
            biometricDemo=source;
            biometricDemoEpoch=System.nanoTime();
            if (audio != null) audio.stop();
            soundDenied=false;
            lastFrame=0;
            handler.removeCallbacks(wake);
            waiting=false;
            schedule();
        });
    }
    void triggerNativeFailure(long when) {
        if(closed) return;
        handler.post(() -> {
            if (pendingAlert != 0 && when < pendingAlert) return;
            denyLevel = pendingAlert != 0 && when - pendingAlert < 15_000_000_000L ? 2 : 1;
            pendingAlert=when; pendingNative=true;
            demoEpoch=biometricDemoEpoch=0;
            demoPending=false;
            if (audio != null) audio.stop();
            soundDenied=false;
            lastFrame=0; handler.removeCallbacks(wake); waiting=false; schedule();
        });
    }
    void clearNativeAlert() {
        if(closed) return;
        handler.post(() -> { if (pendingNative) { pendingAlert=0; pendingNative=false; } });
    }
    String getStats() { return stats; }
    void refresh() {
        if(closed) return;
        handler.post(() -> { readPreferences(); handler.removeCallbacks(wake); waiting=false; schedule(); });
    }
    /** The caller retains Surface ownership; wait for the render looper to stop using it. */
    boolean destroySurface() {
        CountDownLatch done = new CountDownLatch(1);
        if(!handler.post(() -> { try { cancel(); tearDown(); surface=null; } finally { done.countDown(); } })) return true;
        try {
            boolean completed = done.await(1000, TimeUnit.MILLISECONDS);
            if (!completed) Log.e(TAG, "Surface teardown timed out; render thread may be blocked in driver");
            return completed;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }
    void close() {
        if(closed)return;
        closed=true; requestedActive=false;
        destroySurface();
        if (lockTelemetry != null) lockTelemetry.close();
        handler.post(() -> {
            if (audio != null) { audio.release(); audio=null; }
            active=false;
            thread.quitSafely();
        });
    }
    private void readPreferences() {
        requestedRate = readTargetFps();
        phoneLayout = "phone".equals(prefs.getString("wallpaper_layout", "tablet"));
        if (home) {
            showHomeTelemetry = prefs.getBoolean("home_telemetry", true);
            String scene = prefs.getString("home_scene", CarbonHomeRenderer.STYLE);
            darkGallery = "dark_gallery".equals(scene);
            carbonConsole = CarbonHomeRenderer.STYLE.equals(scene);
            lightHome = !carbonConsole && !darkGallery && !"dark".equals(scene);
        } else {
            lockDisplayName = LockDisplayName.read(prefs);
            boolean sound = prefs.getBoolean("preview_sound", false);
            if (sound && audio == null) {
                audio = new ArchivePreviewAudio(context);
                audio.setActive(active);
            } else if (!sound && audio != null) {
                audio.release(); audio = null;
            }
        }
    }
    private int readTargetFps() {
        if (prefs.getBoolean("reduced_motion", false)) return 1;
        if (power != null && power.isPowerSaveMode()) return 30;
        return prefs.getInt("target_fps", 120) <= 60 ? 60 : 120;
    }
    private void hintRate() {
        int target = fallback ? 0 : requestedRate<=1 ? 0 : requestedRate;
        if (Build.VERSION.SDK_INT >= 30 && surface != null && surface.isValid() && hintedRate != target) {
            try {
                if(Build.VERSION.SDK_INT>=31) surface.setFrameRate(target,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
                else surface.setFrameRate(target,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                hintedRate=target;
            }
            catch (IllegalArgumentException | IllegalStateException e) { Log.w(TAG, "Frame rate hint rejected", e); }
        }
    }
    private void schedule() {
        if (!requestedActive || !active || surface == null || !surface.isValid() || width <= 0 || height <= 0 || choreographer == null || posted || waiting) return;
        int fps = frameRate();
        if(fps<=30 && lastFrame!=0) {
            long wait=(lastFrame+1_000_000_000L/fps-System.nanoTime()-5_000_000L)/1_000_000L;
            if(wait>2) { waiting=true; handler.postDelayed(wake,wait); return; }
        }
        hintRate(); posted = true; choreographer.postFrameCallback(callback);
    }
    private void cancel() {
        if (homeGpu != null) homeGpu.pause();
        if(posted&&choreographer!=null) choreographer.removeFrameCallback(callback);
        handler.removeCallbacks(wake); posted=waiting=false; lastFrame=statsStart=0; frames=samples=0;
        homeUnfoldPending = false;
        homeFrameSubmitted = false;
        homeUnfoldStart = 0;
        demoEpoch = 0;
        demoPending = false;
        biometricDemoEpoch = 0;
        lockIdle.reset(System.nanoTime());
        soundIdle=soundDenied=false;
        if (lockTelemetry != null) lockTelemetry.setActive(false);
        if (audio != null) audio.setActive(false);
    }
    private int frameRate() {
        if (!fallback) return requestedRate;
        return home ? 1 : Math.min(15, requestedRate);
    }
    private void frame(long nanos) {
        posted = false;
        if (!requestedActive || !active || surface == null || !surface.isValid()) return;
        int fps = frameRate();
        if (lastFrame != 0 && nanos - lastFrame < (long)(1_000_000_000.0 / fps * .94)) { schedule(); return; }
        lastFrame = nanos;
        long begin = android.os.Debug.threadCpuTimeNanos();
        if (!home && nanos - lastKeyguard >= 250_000_000L) {
            boolean current = keyguard != null && keyguard.isKeyguardLocked();
            if (current != locked) lockIdle.reset(nanos);
            locked = current;
            lastKeyguard = nanos;
        }
        if (!home) {
            readLockFrame(nanos);
            syncAudio();
        }
        boolean showLocked = lockPreview || locked;
        boolean presented = false;
        try {
            if (!fallback && gpu == null && homeGpu == null && !initGl()) useFallback();
            if (fallback) {
                Canvas canvas = null;
                try { canvas = surface.lockCanvas(null); if (canvas != null) {
                    if (home) {
                        ArchiveFallback.drawHome(canvas, homeFallbackPaint, lightHome, darkGallery || carbonConsole, phoneLayout);
                    } else {
                        cpu.draw(canvas,lockFrame,battery,plugged,showLocked,prefs.getBoolean("archive_clock",true),lockDisplayName,phoneLayout,
                                lockTelemetry.latest(), nanos/1_000_000L);
                    }
                    presented=true;
                } }
                finally { if (canvas != null) surface.unlockCanvasAndPost(canvas); }
            } else {
                if (home) {
                    if (homeUnfoldPending) {
                        homeUnfoldStart = nanos;
                        homeUnfoldPending = false;
                    }
                    homeGpu.render(nanos, width, height, homeUnfoldStart, homeUnfoldFromVisible, requestedRate == 1,
                            showHomeTelemetry, lightHome, darkGallery, carbonConsole, battery, plugged, phoneLayout);
                } else {
                    gpu.setTelemetry(lockTelemetry.latest());
                    gpu.render(nanos, inputX, inputY, showLocked, battery, plugged, requestedRate == 1, lockFrame, lockDisplayName, phoneLayout);
                }
                EGLExt.eglPresentationTimeANDROID(display, eglSurface, nanos);
                presented = EGL14.eglSwapBuffers(display, eglSurface);
                if (!presented) {
                    int error = EGL14.eglGetError(); Log.w(TAG, "eglSwapBuffers failed: 0x" + Integer.toHexString(error));
                    tearDown(); // Recreate context and renderer on the next frame, including context loss.
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Rendering failed; switching to Canvas fallback", e);
            tearDown();
            useFallback();
        }
        if (presented) {
            if (home) {
                homeFrameSubmitted = true;
                if (homeGpu != null) homeGpu.frameSubmitted(nanos);
            }
            record(nanos, android.os.Debug.threadCpuTimeNanos() - begin);
        }
        schedule();
    }
    private void readLockFrame(long nanos) {
        lockFrame.reset();
        if (wakePending) {
            wakeEpoch = nanos; wakePending = false;
            if (audio != null) audio.playEntry();
        }
        if (demoPending) {
            demoEpoch = nanos; demoPending = false;
            if (audio != null) audio.play();
        }
        long eventAge = (nanos - pendingAlert) / 1_000_000L;
        long hold = denyLevel == 2 ? ArchiveTimeline.SECOND_HOLD_MS : ArchiveTimeline.FIRST_HOLD_MS;
        if (pendingAlert != 0 && eventAge >= 0 && eventAge < hold) {
            lockFrame.deny(denyLevel,pendingNative,eventAge*.001f);
            lockIdle.reset(nanos);
            return;
        }
        long visualAge=(nanos-biometricDemoEpoch)/1_000_000L;
        if (biometricDemoEpoch!=0 && visualAge>=0 && visualAge<ArchiveTimeline.FIRST_HOLD_MS) {
            lockFrame.deny(2,false,visualAge*.001f);
            lockFrame.biometricDemo=biometricDemo;
            lockIdle.reset(nanos);
            return;
        }
        if (requestedRate != 1) {
            long recoveryAge = pendingAlert != 0 && eventAge >= hold ? eventAge - hold : Long.MAX_VALUE;
            if (biometricDemoEpoch != 0 && visualAge >= ArchiveTimeline.FIRST_HOLD_MS) {
                recoveryAge = Math.min(recoveryAge, visualAge - ArchiveTimeline.FIRST_HOLD_MS);
            }
            if (recoveryAge < 600) {
                ArchiveTimeline.recover(lockFrame, recoveryAge);
                lockIdle.reset(nanos);
                return;
            }
        }
        if (demoEpoch != 0 && nanos >= demoEpoch) {
            long age = (nanos - demoEpoch) / 1_000_000L;
            ArchiveTimeline.demo(lockFrame, age);
            lockIdle.reset(nanos);
            if (age < ArchiveTimeline.DEMO_MS) return;
            demoEpoch = 0;
        }
        boolean idleEnabled = (lockPreview || locked) && prefs.getBoolean("idle_black", true);
        lockIdle.apply(lockFrame,nanos,idleEnabled,requestedRate==1);
        if (requestedRate == 1 || lockFrame.idleBlack) return;
        long age = (nanos - wakeEpoch) / 1_000_000L;
        if (wakeEpoch != 0 && age >= 0 && age < ArchiveTimeline.WAKE_MS) {
            ArchiveTimeline.wake(lockFrame, age, false);
        }
        if (pulseEpoch != 0) lockFrame.pulse = ArchiveTimeline.pulse((nanos - pulseEpoch) / 1_000_000L);
    }

    private void syncAudio() {
        boolean darkIdle = lockFrame.idleBlack && lockFrame.denyMix > .99f;
        boolean denied = lockFrame.stage == ArchiveTimeline.DENIED;
        if (audio != null && demoEpoch == 0) {
            if (darkIdle && !soundIdle) audio.playStandby();
            if (!darkIdle && soundIdle) audio.playWake();
            if (denied && !soundDenied) audio.playDenial();
            if (!denied && soundDenied) audio.playRecover();
        }
        soundIdle=darkIdle;
        soundDenied=denied;
    }

    private void useFallback() {
        fallback = true;
        if (!home) cpu = new ArchiveFallback(context);
    }
    private boolean initGl() {
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (display == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display, new int[2], 0, new int[2], 0)) throw new IllegalStateException("EGL display initialization failed");
            // Avoid a full-screen multisample resolve on every 120 Hz frame. The ribs use analytic AA.
            EGLConfig config = chooseConfig(24);
            if (config == null) config = chooseConfig(16);
            if (config == null) throw new IllegalStateException("No suitable GLES 3 config");
            eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
            if (eglContext == EGL14.EGL_NO_CONTEXT) throw new IllegalStateException("GLES 3 context unavailable");
            eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, new int[]{EGL14.EGL_NONE}, 0);
            if (eglSurface == EGL14.EGL_NO_SURFACE || !makeCurrent()) throw new IllegalStateException("EGL window surface unavailable");
            EGL14.eglSwapInterval(display, 1);
            if (home) {
                homeGpu = new HomeWallpaperRenderer(context);
                homeGpu.initialize(carbonConsole);
            } else {
                gpu = new GpuTerminalRenderer(context);
                gpu.initialize();
                gpu.resize(width, height);
            }
            Log.i(TAG, "GLES 3 renderer initialized"); return true;
        } catch (RuntimeException e) { Log.e(TAG, "GLES initialization failed; using low-rate Canvas fallback", e); tearDown(); return false; }
    }
    private EGLConfig chooseConfig(int depthBits) {
        int[] attrib = {EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, depthBits, EGL14.EGL_STENCIL_SIZE, 0, EGL14.EGL_SAMPLE_BUFFERS, 0,
                EGL14.EGL_SAMPLES, 0, EGL14.EGL_NONE};
        EGLConfig[] configs = new EGLConfig[1]; int[] n = new int[1];
        return EGL14.eglChooseConfig(display, attrib, 0, configs, 0, 1, n, 0) && n[0] > 0 ? configs[0] : null;
    }
    private boolean makeCurrent() { return display != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT && eglSurface != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext); }
    private void tearDown() {
        if (homeGpu != null) {
            try { homeGpu.release(makeCurrent()); }
            catch (RuntimeException e) { Log.w(TAG, "Home GL release failed", e); }
            homeGpu = null;
        }
        if (gpu != null) { try { if (makeCurrent()) gpu.release(); } catch (RuntimeException e) { Log.w(TAG, "GL release failed", e); } gpu = null; }
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface);
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext);
            EGL14.eglTerminate(display);
        }
        display = EGL14.EGL_NO_DISPLAY; eglSurface = EGL14.EGL_NO_SURFACE; eglContext = EGL14.EGL_NO_CONTEXT;
        fallback = false; cpu = null; hintedRate = -1;
    }
    private void record(long nanos, long elapsed) {
        if (statsStart == 0) statsStart = nanos;
        frames++; if (samples < durations.length) durations[samples++] = elapsed;
        if (nanos - statsStart < 5_000_000_000L) return;
        System.arraycopy(durations, 0, sorted, 0, samples); Arrays.sort(sorted, 0, samples);
        double actual = Math.max(0,frames-1) * 1e9 / (nanos - statsStart);
        if (lockTelemetry != null) lockTelemetry.setSubmittedFps((float)actual);
        double p50 = samples == 0 ? 0 : sorted[(samples-1)/2] / 1e6;
        double p95 = samples == 0 ? 0 : sorted[(int)((samples-1)*.95)] / 1e6;
        stats = String.format(Locale.US,"提交 %.1f fps · CPU p50 %.1f / p95 %.1f ms%s",actual,p50,p95,fallback?" · 兼容模式":"");
        Log.i(TAG,String.format(Locale.US,"scene=%s target=%d submitted_fps=%.2f cpu_p50_ms=%.3f cpu_p95_ms=%.3f submitted=%d fallback=%s size=%dx%d",home?"home":"lock",requestedRate,actual,p50,p95,frames,fallback,width,height));
        statsStart = nanos; frames = samples = 0;
    }
}
