package dev.rhine.terminal;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Local system snapshots are sampled off the render thread and never written to storage. */
final class HomeDeviceTelemetry {
    private static final long INTERVAL_MS = 1000;
    private static final double GIB = 1024.0 * 1024 * 1024;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US);
    private static final IntentFilter BATTERY = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private final Context context;
    private final ActivityManager activity;
    private final DisplayManager displays;
    private final PowerManager power;
    private final ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
    private final HandlerThread thread = new HandlerThread("Rhine-device-samples");
    private final Handler worker;
    private final Runnable sampler = this::sample;
    private volatile String[] latest;
    private volatile float submittedFps = Float.NaN;
    private boolean active;

    HomeDeviceTelemetry(Context context) {
        this.context = context.getApplicationContext();
        activity = context.getSystemService(ActivityManager.class);
        displays = context.getSystemService(DisplayManager.class);
        power = context.getSystemService(PowerManager.class);
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    String[] latest() { return latest; }
    void setSubmittedFps(float fps) { submittedFps = fps; }

    synchronized void setActive(boolean value) {
        if (active == value) return;
        active = value;
        worker.removeCallbacks(sampler);
        if (value) worker.post(sampler);
    }

    void close() {
        setActive(false);
        thread.quitSafely();
    }

    private void sample() {
        synchronized (this) {
            if (!active) return;
        }
        String[] snapshot = capture();
        synchronized (this) {
            if (!active) return;
            latest = snapshot;
            worker.removeCallbacks(sampler);
            worker.postDelayed(sampler, INTERVAL_MS);
        }
    }

    private String[] capture() {
        if (activity != null) activity.getMemoryInfo(memory);
        Intent battery = Build.VERSION.SDK_INT >= 33
                ? context.registerReceiver(null, BATTERY, Context.RECEIVER_NOT_EXPORTED)
                : context.registerReceiver(null, BATTERY);
        int level = extra(battery, BatteryManager.EXTRA_LEVEL);
        int scale = extra(battery, BatteryManager.EXTRA_SCALE);
        int temp = extra(battery, BatteryManager.EXTRA_TEMPERATURE);
        int volts = extra(battery, BatteryManager.EXTRA_VOLTAGE);
        Display display = displays == null ? null : displays.getDisplay(Display.DEFAULT_DISPLAY);
        float hz = display == null ? Float.NaN : display.getRefreshRate();
        Display.Mode mode = display == null ? null : display.getMode();
        float fps = submittedFps;
        long uptime = SystemClock.elapsedRealtime()/1000;
        return new String[]{
                "$ rhine probe --local",
                "["+LocalTime.now().format(CLOCK)+"] DEVICE SNAPSHOT",
                "  device.model     "+Build.MODEL+"  /  API "+Build.VERSION.SDK_INT,
                "  ram.available    "+gib(memory.availMem),
                "  ram.kernel       "+gib(memory.totalMem),
                "  ram.pressure     "+(memory.totalMem==0 ? "N/A" : memory.lowMemory ? "LOW MEMORY" : "NORMAL"),
                "  battery.level    "+(scale>0 && level>=0 ? level*100/scale+" %" : "N/A"),
                "  power.source     "+source(extra(battery,BatteryManager.EXTRA_PLUGGED)),
                "  battery.state    "+state(extra(battery,BatteryManager.EXTRA_STATUS)),
                "  battery.temp     "+(temp!=Integer.MIN_VALUE ? String.format(Locale.US,"%.1f C",temp*.1) : "N/A"),
                "  battery.voltage  "+(volts>0 ? volts+" mV" : "N/A"),
                "  thermal.status   "+thermal(),
                "  display.mode     "+(mode==null ? "N/A" : mode.getPhysicalWidth()+" x "+mode.getPhysicalHeight()),
                "  display.hz       "+(Float.isFinite(hz) && hz>0 ? String.format(Locale.US,"%.2f Hz",hz) : "N/A"),
                "  wallpaper.fps    "+(Float.isFinite(fps) ? String.format(Locale.US,"%.1f SUBMITTED",fps) : "SAMPLING"),
                String.format(Locale.US,"  system.uptime    %02dd %02d:%02d:%02d",uptime/86400,uptime/3600%24,uptime/60%60,uptime%60)
        };
    }

    private String thermal() {
        if (Build.VERSION.SDK_INT < 29 || power == null) return "N/A";
        switch (power.getCurrentThermalStatus()) {
            case PowerManager.THERMAL_STATUS_NONE: return "NONE";
            case PowerManager.THERMAL_STATUS_LIGHT: return "LIGHT";
            case PowerManager.THERMAL_STATUS_MODERATE: return "MODERATE";
            case PowerManager.THERMAL_STATUS_SEVERE: return "SEVERE";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "CRITICAL";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "EMERGENCY";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "SHUTDOWN";
            default: return "N/A";
        }
    }

    private static String gib(long bytes) {
        return bytes>0 ? String.format(Locale.US,"%.2f GiB",bytes/GIB) : "N/A";
    }

    private static int extra(Intent intent, String key) {
        return intent == null ? Integer.MIN_VALUE : intent.getIntExtra(key,Integer.MIN_VALUE);
    }

    private static String source(int plug) {
        if (plug == 0) return "BATTERY";
        if (plug == Integer.MIN_VALUE) return "N/A";
        if ((plug & BatteryManager.BATTERY_PLUGGED_USB) != 0) return "USB";
        if ((plug & BatteryManager.BATTERY_PLUGGED_AC) != 0) return "AC";
        if ((plug & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) return "WIRELESS";
        return "EXTERNAL";
    }

    private static String state(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "CHARGING";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "DISCHARGING";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "IDLE";
            case BatteryManager.BATTERY_STATUS_FULL: return "FULL";
            default: return "N/A";
        }
    }
}
