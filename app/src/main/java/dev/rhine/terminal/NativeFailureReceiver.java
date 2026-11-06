package dev.rhine.terminal;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Process;
import android.os.UserHandle;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.LongConsumer;

/** Optional watch-login callback; never receives or stores credential contents.
 * https://developer.android.com/reference/android/app/admin/DeviceAdminReceiver#onPasswordFailed(android.content.Context,%20android.content.Intent,%20android.os.UserHandle)
 */
public final class NativeFailureReceiver extends DeviceAdminReceiver {
    private static final CopyOnWriteArraySet<LongConsumer> listeners=new CopyOnWriteArraySet<>();
    private static volatile long lastFailureNanos;

    static void subscribe(LongConsumer listener) {
        listeners.add(listener);
        long last=lastFailureNanos;
        if(last!=0 && WarningTimeline.intensity((System.nanoTime()-last)*1e-9f)>0) listener.accept(last);
    }
    static void unsubscribe(LongConsumer listener) { listeners.remove(listener); }
    static void clear() { lastFailureNanos=0; for(LongConsumer listener:listeners) listener.accept(0); }

    @Override public void onPasswordFailed(Context context, Intent intent, UserHandle user) {
        if (user == null || !user.equals(Process.myUserHandle())) return;
        DevicePolicyManager manager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (manager == null || !manager.isAdminActive(new ComponentName(context, NativeFailureReceiver.class))) return;
        SharedPreferences prefs = context.getSharedPreferences("terminal", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("opt_native_failure", false)) return;
        // Transient, same-process signal only: no attempt count, timestamp, or credential on disk.
        long when=System.nanoTime();
        lastFailureNanos=when;
        for(LongConsumer listener:listeners) listener.accept(when);
    }
    @Override public void onDisabled(Context context, Intent intent) {
        context.getSharedPreferences("terminal", Context.MODE_PRIVATE).edit()
                .putBoolean("opt_native_failure", false).apply();
        clear();
        super.onDisabled(context, intent);
    }
}
