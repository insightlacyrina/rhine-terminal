package dev.rhine.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.WindowInsets;
import android.widget.ScrollView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import static dev.rhine.terminal.InstrumentUi.*;

/** Explicit opt-in only: the app never launches device-admin enrollment automatically. */
public final class FailureAccessActivity extends Activity {
    private static final int REQUEST_ADMIN = 129;
    private DevicePolicyManager manager;
    private ComponentName admin;
    private SharedPreferences prefs;
    private TextView status;
    private Button enable, disable;
    private boolean pending;
    private InstrumentUi ui;
    private boolean adminActive() { return manager != null && manager.isAdminActive(admin); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        manager = (DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, NativeFailureReceiver.class);
        prefs = getSharedPreferences("terminal", MODE_PRIVATE);
        pending = state != null && state.getBoolean("pending_admin", false);
        ui = new InstrumentUi(this);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
            scroll.requestApplyInsets();
        }
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(ui.dp(24), ui.dp(28), ui.dp(24), ui.dp(32));
        int width = getResources().getConfiguration().screenWidthDp > 640 ? ui.dp(640) : -1;
        scroll.addView(column, new FrameLayout.LayoutParams(width, -2, Gravity.CENTER_HORIZONTAL));

        TextView title = ui.text("系统密码失败变色", 26, INK, true);
        title.setPadding(0, 0, 0, ui.dp(16));
        column.addView(title);
        View rule = new View(this); rule.setBackgroundColor(RULE);
        column.addView(rule, new LinearLayout.LayoutParams(-1, ui.dp(1)));

        TextView explanation = ui.text("默认关闭。启用后，应用会请求设备管理器的“监控登录尝试”权限，在系统报告本机 PIN 或密码输入失败时短暂改变壁纸颜色。\n\n应用不会读取或保存密码，也不请求锁定、清除或重置设备的权限。此功能不检测指纹或人脸失败，事件能否送达由系统决定。可随时关闭并移除设备管理权限。", 16, INK, false);
        explanation.setPadding(0, ui.dp(24), 0, ui.dp(24));
        explanation.setLineSpacing(ui.dp(4), 1f);
        column.addView(explanation);
        status = ui.text("", 15, ACCENT, false);
        status.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16));
        status.setBackground(ui.plate(PANEL, RULE));
        column.addView(status);

        enable = ui.button("阅读并启用", true);
        addButton(column, enable); enable.setOnClickListener(v -> review());
        disable = ui.button("关闭并移除设备管理权限", false);
        addButton(column, disable); disable.setOnClickListener(v -> disable());
        Button close = ui.button("返回", false);
        addButton(column, close); close.setOnClickListener(v -> finish());
        setContentView(scroll); refresh();
    }
    private void addButton(LinearLayout column, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = ui.dp(12); column.addView(button, params);
    }
    private void review() {
        new AlertDialog.Builder(this).setTitle("启用可选设备管理权限？")
                .setMessage("继续后，系统将显示设备管理权限确认页面。")
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
        enable.setAlpha(enable.isEnabled() ? 1f : .45f); disable.setAlpha(disable.isEnabled() ? 1f : .45f);
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
