package dev.rhine.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Explicit opt-in only: the app never launches device-admin enrollment automatically. */
public final class FailureAccessActivity extends Activity {
    private static final int REQUEST_ADMIN = 129;
    private DevicePolicyManager manager;
    private ComponentName admin;
    private SharedPreferences prefs;
    private TextView status;
    private Button enable, disable;
    private boolean pending;
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private boolean adminActive() { return manager != null && manager.isAdminActive(admin); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        manager = (DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, NativeFailureReceiver.class);
        prefs = getSharedPreferences("terminal", MODE_PRIVATE);
        pending = state != null && state.getBoolean("pending_admin", false);
        getWindow().setStatusBarColor(0xff090c0b); getWindow().setNavigationBarColor(0xff090c0b);
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setBackgroundColor(0xff090c0b); column.setPadding(dp(24), dp(32), dp(24), dp(24));
        TextView title = text("系统密码失败变色", 24, 0xffe6ebda); column.addView(title);
        TextView explanation = text("默认关闭。可选启用设备管理器的“监控登录尝试”权限，让壁纸在系统报告本机 PIN 或密码输入失败时短暂变色。应用不会读取或保存密码，也不请求锁定、清除或重置设备的权限。\n\n此功能不包含指纹或其他生物识别失败。事件是否送达由系统决定，三星 One UI 的表现尚未验证。可以随时在此关闭，或在系统设置中移除设备管理权限。", 15, 0xffb2bca9); explanation.setPadding(0, dp(18), 0, dp(16)); explanation.setLineSpacing(dp(4), 1f); column.addView(explanation);
        status = text("", 14, 0xffddeb70); status.setPadding(0, dp(4), 0, dp(16)); column.addView(status);
        enable = button("阅读并启用"); column.addView(enable); enable.setOnClickListener(v -> review());
        disable = button("关闭并移除设备管理权限"); column.addView(disable); disable.setOnClickListener(v -> disable());
        Button close = button("返回"); column.addView(close); close.setOnClickListener(v -> finish());
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(column); setContentView(scroll); refresh();
    }
    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view; }
    private Button button(String value) { Button view = new Button(this); view.setText(value); view.setAllCaps(false); view.setMinHeight(dp(52)); return view; }
    private void review() {
        new AlertDialog.Builder(this).setTitle("启用可选设备管理权限？")
                .setMessage("系统将显示设备管理权限确认页面。此功能只使用监控登录尝试权限，用于在系统报告本机 PIN 或密码失败时触发壁纸变色；不读取密码，不检测指纹失败。系统和设备可能不提供事件。您可以随时关闭并移除权限。")
                .setNegativeButton("取消", null).setPositiveButton("继续", (dialog, which) -> {
                    if (adminActive()) { prefs.edit().putBoolean("opt_native_failure", true).apply(); refresh(); return; }
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "接收系统报告的 PIN 或密码失败事件，供壁纸短暂变色。不会读取密码。");
                    pending = true;
                    try { startActivityForResult(intent, REQUEST_ADMIN); }
                    catch (ActivityNotFoundException | SecurityException e) { pending = false; Toast.makeText(this, "无法打开系统授权页面", Toast.LENGTH_LONG).show(); }
                }).show();
    }
    private void disable() {
        prefs.edit().putBoolean("opt_native_failure", false).apply();
        NativeFailureReceiver.clear();
        pending = false;
        try { if (adminActive()) manager.removeActiveAdmin(admin); }
        catch (SecurityException e) { Toast.makeText(this, "请在系统设置中移除设备管理权限", Toast.LENGTH_LONG).show(); }
        refresh();
    }
    private void refresh() {
        boolean active = adminActive(), opted = prefs.getBoolean("opt_native_failure", false);
        status.setText(active && opted ? "状态：已启用" : active ? "状态：未启用（系统权限仍存在）" : "状态：关闭");
        enable.setEnabled(!(active && opted)); disable.setEnabled(active || opted);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_ADMIN && pending) {
            pending = false;
            if (result == RESULT_OK && adminActive()) prefs.edit().putBoolean("opt_native_failure", true).apply();
            refresh();
        }
    }
    @Override protected void onResume() { super.onResume(); if (status != null) refresh(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("pending_admin", pending); super.onSaveInstanceState(state); }
}
