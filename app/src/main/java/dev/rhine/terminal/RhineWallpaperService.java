package dev.rhine.terminal;

import android.app.WallpaperManager;
import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.util.Log;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/** Draws only while the wallpaper host is visible and the display is interactive. */
public final class RhineWallpaperService extends WallpaperService {
    @Override public Engine onCreateEngine() { return new TerminalEngine(); }

    final class TerminalEngine extends Engine implements SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {
        private final GlRenderSession session = new GlRenderSession(RhineWallpaperService.this);
        private final SharedPreferences prefs = getSharedPreferences("terminal", MODE_PRIVATE);
        private final PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        private final SensorManager sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        private final KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        private final SoftTapDetector tapDetector = new SoftTapDetector();
        private final BooleanSupplier tapArmed = this::tapAllowed;
        private final Sensor accelerometer = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        private final Sensor rotation = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        private boolean visible, surfaceReady, sensorActive, tapSensorActive, registered, screenOn = true;
        private int battery = -1;
        private long lastTapStatusNanos;
        private float tiltX, tiltY, touchX, touchY;
        private String lastLifecycle = "", lastTapState = "";
        private final float[] matrix = new float[9], remapped = new float[9], angles = new float[3];
        private final LongConsumer nativeFailure = this::onNativeFailure;
        private final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) screenOn = false;
                else if (Intent.ACTION_SCREEN_ON.equals(action)) screenOn = true;
                if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    battery = level >= 0 && scale > 0 ? Math.min(100,level * 100 / scale) : -1;
                    session.setBattery(battery,intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0);
                }
                updateRunning();
            }
        };
        @Override public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            session.setLockPreview(isPreview());
            setTouchEventsEnabled(true);
            screenOn = power.isInteractive();
            prefs.registerOnSharedPreferenceChangeListener(this);
            if(!isPreview()) NativeFailureReceiver.subscribe(nativeFailure);
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(receiver, filter);
            registered = true;
            if (Build.VERSION.SDK_INT >= 27) notifyColorsChanged();
        }
        @Override public void onVisibilityChanged(boolean nowVisible) { visible = nowVisible; updateRunning(); }
        @Override public void onWallpaperFlagsChanged(int flags) { super.onWallpaperFlagsChanged(flags); updateRunning(); }
        @Override public void onSurfaceCreated(SurfaceHolder holder) { super.onSurfaceCreated(holder); surfaceReady = true; android.graphics.Rect r = holder.getSurfaceFrame(); session.setSurface(holder.getSurface(), r.width(), r.height()); updateRunning(); }
        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) { super.onSurfaceChanged(holder, format, width, height); session.setSurface(holder.getSurface(), width, height); updateRunning(); }
        @Override public void onSurfaceDestroyed(SurfaceHolder holder) { surfaceReady = false; updateRunning(); session.destroySurface(); super.onSurfaceDestroyed(holder); }
        private boolean running() { return visible && surfaceReady && screenOn && power.isInteractive(); }
        private void updateRunning() {
            String lifecycle = "visible=" + visible + " screenOn=" + screenOn + " surfaceReady=" + surfaceReady + " preview=" + isPreview() + " running=" + running();
            if (!lifecycle.equals(lastLifecycle)) { Log.i("RhineWallpaper", lifecycle); lastLifecycle = lifecycle; }
            boolean reduced = prefs.getBoolean("reduced_motion", false);
            boolean useSensor = running() && prefs.getBoolean("motion", false) && !reduced && rotation != null;
            if (useSensor && !sensorActive) {
                sensorActive = sensors.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI);
            } else if (!useSensor && sensorActive) {
                sensors.unregisterListener(this, rotation); sensorActive = false; tiltX = tiltY = 0;
            }
            boolean useTap = running() && !isPreview() && !reduced && accelerometer != null;
            if (useTap && !tapSensorActive) {
                tapDetector.reset();
                tapSensorActive = sensors.registerListener(this, accelerometer, 10_000);
            } else if (!useTap && tapSensorActive) {
                sensors.unregisterListener(this, accelerometer); tapSensorActive = false;
                tapDetector.reset();
            }
            logTapState();
            session.setInput(tiltX + touchX, tiltY + touchY, battery);
            session.setActive(running());
            session.refresh();
        }
        private boolean tapAllowed() {
            // Lock flags can be unset or delayed when an OEM reuses the system wallpaper engine.
            // The running engine and live keyguard check gate every detected impulse instead.
            return running() && !isPreview() && !prefs.getBoolean("reduced_motion", false)
                    && keyguard != null && keyguard.isKeyguardLocked();
        }
        private void logTapState() {
            int flags = Build.VERSION.SDK_INT >= 34 ? getWallpaperFlags() : -1;
            String state="registered="+tapSensorActive+" armed="+(tapSensorActive && tapAllowed())
                    +" running="+running()+" preview="+isPreview()+" flags="+flags;
            if (!state.equals(lastTapState)) { Log.i("RhineTap", "source=sensor "+state); lastTapState=state; }
        }
        private void directPulse(String source) {
            boolean allowed=running() && !prefs.getBoolean("reduced_motion", false);
            boolean accepted=allowed && tapDetector.acceptDirect(SystemClock.elapsedRealtimeNanos());
            Log.i("RhineTap", "source="+source+" armed="+allowed+" accepted="+accepted);
            if (accepted) session.pulse();
        }
        @Override public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            if (WallpaperManager.COMMAND_TAP.equals(action)) directPulse("command");
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }
        @Override public WallpaperColors onComputeColors() {
            if (Build.VERSION.SDK_INT >= 31) return new WallpaperColors(Color.valueOf(0xffe4dfdc), null, null, WallpaperColors.HINT_SUPPORTS_DARK_TEXT);
            if (Build.VERSION.SDK_INT >= 27) {
                Bitmap sample = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
                sample.eraseColor(0xffe4dfdc);
                WallpaperColors colors = WallpaperColors.fromBitmap(sample);
                sample.recycle();
                return colors;
            }
            return null;
        }
        @Override public void onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) directPulse("touch");
            if (!prefs.getBoolean("reduced_motion", false) && (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE)) {
                android.graphics.Rect bounds = getSurfaceHolder().getSurfaceFrame();
                if (bounds.width() > 0 && bounds.height() > 0) {
                    touchX = Math.max(-.2f, Math.min(.2f, (event.getX() / bounds.width() - .5f) * .4f));
                    touchY = Math.max(-.2f, Math.min(.2f, (event.getY() / bounds.height() - .5f) * .4f));
                }
            }
            if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL) touchX=touchY=0;
            session.setInput(tiltX + touchX, tiltY + touchY, battery);
            super.onTouchEvent(event);
        }
        @Override public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                if (tapSensorActive && event.values.length >= 3) {
                    if (event.timestamp - lastTapStatusNanos >= 500_000_000L) {
                        lastTapStatusNanos = event.timestamp;
                        logTapState();
                    }
                    if (tapDetector.sample(event.timestamp, event.values[0], event.values[1], event.values[2], tapArmed)) {
                        Log.i("RhineTap", "source=sensor armed=true accepted=true");
                        session.pulse();
                    }
                }
                return;
            }
            if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
            SensorManager.getRotationMatrixFromVector(matrix, event.values);
            int rotation = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            int x = SensorManager.AXIS_X, y = SensorManager.AXIS_Y;
            switch (rotation) {
                case Surface.ROTATION_90: x = SensorManager.AXIS_Y; y = SensorManager.AXIS_MINUS_X; break;
                case Surface.ROTATION_180: x = SensorManager.AXIS_MINUS_X; y = SensorManager.AXIS_MINUS_Y; break;
                case Surface.ROTATION_270: x = SensorManager.AXIS_MINUS_Y; y = SensorManager.AXIS_X; break;
                default: break;
            }
            SensorManager.remapCoordinateSystem(matrix, x, y, remapped);
            SensorManager.getOrientation(remapped, angles);
            tiltX = Math.max(-1f, Math.min(1f, angles[2]));
            tiltY = Math.max(-1f, Math.min(1f, angles[1]));
            session.setInput(tiltX + touchX, tiltY + touchY, battery);
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        private void onNativeFailure(long eventNanos) {
            if(eventNanos==0 || !prefs.getBoolean("opt_native_failure",false)) { session.clearNativeAlert(); return; }
            if(isPreview()) return;
            if(Build.VERSION.SDK_INT>=34 && (getWallpaperFlags()&WallpaperManager.FLAG_LOCK)==0) return;
            session.triggerNativeFailure(eventNanos);
        }
        @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            if("opt_native_failure".equals(key)&&!preferences.getBoolean(key,false)) session.clearNativeAlert();
            updateRunning();
        }
        @Override public void onDestroy() {
            visible = false; updateRunning();
            session.close();
            NativeFailureReceiver.unsubscribe(nativeFailure);
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            if (registered) { unregisterReceiver(receiver); registered = false; }
            super.onDestroy();
        }
    }
}
