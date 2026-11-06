package dev.rhine.terminal;

import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

/** Independent main-screen wallpaper. A recent user-present signal can unfold the visible scene. */
public final class HomeWallpaperService extends WallpaperService {
    @Override public Engine onCreateEngine() { return new HomeEngine(); }

    private final class HomeEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private static final long WAKE_WINDOW_MS = 45_000;
        private static final long ENTRANCE_WINDOW_MS = 1_200;
        private final GlRenderSession session = new GlRenderSession(HomeWallpaperService.this, true);
        private final SharedPreferences prefs = getSharedPreferences("terminal", MODE_PRIVATE);
        private final PowerManager power = getSystemService(PowerManager.class);
        private final KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        private final Handler main = new Handler(Looper.getMainLooper());
        private boolean surfaceReady, visible, screenOn, active, receiverRegistered;
        private long wakeMillis = -1, pendingUnfold = -1;
        private final Runnable retryUnfold = () -> maybeUnfold(active);

        private final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                long now = SystemClock.elapsedRealtime();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    screenOn = false;
                    wakeMillis = pendingUnfold = -1;
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    screenOn = true;
                    wakeMillis = now;
                } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    if (wakeMillis >= 0 && now - wakeMillis <= WAKE_WINDOW_MS) pendingUnfold = now;
                    wakeMillis = -1;
                } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                    setBattery(intent);
                } else if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(action)) {
                    session.refresh();
                }
                updateRunning();
            }
        };

        @Override public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            screenOn = power.isInteractive();
            setOffsetNotificationsEnabled(false);
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            Intent initialBattery;
            if (Build.VERSION.SDK_INT >= 33) initialBattery = registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else initialBattery = registerReceiver(receiver, filter);
            receiverRegistered = true;
            if (initialBattery != null && Intent.ACTION_BATTERY_CHANGED.equals(initialBattery.getAction())) setBattery(initialBattery);
            prefs.registerOnSharedPreferenceChangeListener(this);
            if (Build.VERSION.SDK_INT >= 27) notifyColorsChanged();
        }

        private void setBattery(Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int percent = level >= 0 && scale > 0 ? Math.min(100, level * 100 / scale) : -1;
            session.setBattery(percent, intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0);
        }

        @Override public void onVisibilityChanged(boolean nowVisible) {
            visible = nowVisible;
            updateRunning();
        }

        @Override public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            surfaceReady = true;
            Rect frame = holder.getSurfaceFrame();
            session.setSurface(holder.getSurface(), frame.width(), frame.height());
            updateRunning();
        }

        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            session.setSurface(holder.getSurface(), width, height);
            updateRunning();
        }

        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            updateRunning();
            session.destroySurface();
            super.onSurfaceDestroyed(holder);
        }

        @Override public WallpaperColors onComputeColors() {
            if (Build.VERSION.SDK_INT < 27) return null;
            String scene = prefs.getString("home_scene",CarbonHomeRenderer.STYLE);
            if (CarbonHomeRenderer.STYLE.equals(scene)) {
                return new WallpaperColors(Color.valueOf(0xff0b0b0c),Color.valueOf(0xff293229),null);
            }
            if ("dark_gallery".equals(scene)) {
                return new WallpaperColors(Color.valueOf(0xff0b0b0c),Color.valueOf(0xffdcdad1),null);
            }
            if (!"dark".equals(scene)) {
                return new WallpaperColors(Color.valueOf(0xffeceae6),Color.valueOf(0xffcbc8bf),null);
            }
            return new WallpaperColors(Color.valueOf(HomeWallpaperRenderer.BASE_COLOR),
                    Color.valueOf(0xff3c4130), null);
        }

        private void updateRunning() {
            boolean running = visible && surfaceReady && screenOn && power.isInteractive();
            if (active != running) {
                active = running;
                session.setActive(running);
            }
            maybeUnfold(running);
        }

        private void maybeUnfold(boolean running) {
            main.removeCallbacks(retryUnfold);
            if (pendingUnfold < 0) return;
            boolean appliesToHome = Build.VERSION.SDK_INT < 34 || (getWallpaperFlags() & WallpaperManager.FLAG_SYSTEM) != 0;
            if (SystemClock.elapsedRealtime() - pendingUnfold > ENTRANCE_WINDOW_MS || isPreview()
                    || !appliesToHome || !prefs.getBoolean("home_unfold", true)) {
                pendingUnfold = -1;
                return;
            }
            if (!running || keyguard == null) return;
            if (!keyguard.isKeyguardLocked()) {
                pendingUnfold = -1;
                session.unfoldHome(true);
            } else {
                main.postDelayed(retryUnfold, 80);
            }
        }

        @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            if (Build.VERSION.SDK_INT >= 27 && "home_scene".equals(key)) notifyColorsChanged();
            session.refresh();
        }

        @Override public void onDestroy() {
            main.removeCallbacks(retryUnfold);
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            if (receiverRegistered) unregisterReceiver(receiver);
            session.close();
            super.onDestroy();
        }
    }
}
