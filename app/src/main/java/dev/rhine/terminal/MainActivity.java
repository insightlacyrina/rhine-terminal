package dev.rhine.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.IntConsumer;

import static dev.rhine.terminal.InstrumentUi.*;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private InstrumentUi ui;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("terminal", MODE_PRIVATE);
        ui = new InstrumentUi(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout content = column();
        int screenWidth = getResources().getConfiguration().screenWidthDp;
        int contentWidth = screenWidth > 1040 ? ui.dp(1040) : -1;
        scroll.addView(content, new FrameLayout.LayoutParams(contentWidth, -2, Gravity.CENTER_HORIZONTAL));
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int left = insets.getSystemWindowInsetLeft(), right = insets.getSystemWindowInsetRight();
            int top = insets.getSystemWindowInsetTop(), bottom = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    left = Math.max(left, cutout.getSafeInsetLeft());
                    right = Math.max(right, cutout.getSafeInsetRight());
                    top = Math.max(top, cutout.getSafeInsetTop());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
            }
            content.setPadding(ui.dp(20) + left, ui.dp(18) + top, ui.dp(20) + right, ui.dp(24) + bottom);
            return insets;
        });

        addHeader(content);
        LinearLayout wallpapers = content;
        LinearLayout settings = content;
        if (screenWidth >= 700) {
            LinearLayout columns = new LinearLayout(this);
            columns.setBaselineAligned(false);
            wallpapers = column();
            settings = column();
            columns.addView(wallpapers, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1);
            right.setMarginStart(ui.dp(24));
            columns.addView(settings, right);
            content.addView(columns);
        }

        section(wallpapers, "锁屏壁纸");
        addWallpaperActions(wallpapers, false);
        section(wallpapers, "主屏壁纸");
        addWallpaperActions(wallpapers, true);
        choice(wallpapers, "主屏风格", new String[]{"石墨终端", "暖白样本盒", "石墨样本盒", "深色边缘环带"},
                new String[]{CarbonHomeRenderer.STYLE, "light", "dark_gallery", "dark"}, "home_scene");
        toggle(wallpapers, "深色边缘环带状态文字", "home_telemetry", true);
        toggle(wallpapers, "解锁后展开", "home_unfold", true);

        section(settings, "个性化");
        choice(settings, "排版模式", new String[]{"平板模式", "手机模式（一加 13）"},
                new String[]{"tablet", "phone"}, "wallpaper_layout");
        TextView displayName = valueText(LockDisplayName.read(prefs));
        LinearLayout nameRow = settingRow(settings, "锁屏称呼");
        addValue(nameRow, displayName);
        nameRow.setOnClickListener(view -> showNameEditor(displayName));
        toggle(settings, "显示主题时钟", "archive_clock", true);
        toggle(settings, "预览音效", "preview_sound", false);

        section(settings, "显示");
        selector(settings, "目标帧率", new String[]{"120 帧", "60 帧"},
                prefs.getInt("target_fps", 120) <= 60 ? 1 : 0,
                index -> prefs.edit().putInt("target_fps", index == 0 ? 120 : 60).apply());
        toggle(settings, "设备运动", "motion", false);
        toggle(settings, "减少动态效果", "reduced_motion", false);

        section(settings, "权限");
        LinearLayout failure = settingRow(settings, "系统密码失败变色");
        addValue(failure, valueText("›"));
        failure.setOnClickListener(view -> startActivity(new Intent(this, FailureAccessActivity.class)));
    }

    private void addHeader(LinearLayout parent) {
        View accent = new View(this);
        accent.setBackgroundColor(ACCENT);
        parent.addView(accent, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(3)));

        LinearLayout plate = new LinearLayout(this);
        plate.setGravity(Gravity.CENTER_VERTICAL);
        plate.setMinimumHeight(ui.dp(92));
        plate.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        plate.setBackground(ui.plate(PANEL, RULE));
        TextView brand = ui.text("RHINE TERMINAL", 27, INK, true);
        brand.setMaxLines(2);
        brand.setLetterSpacing(.035f);
        plate.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        View emblem = new View(this) {
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float mark = getWidth();
                NiriWordmark.draw(canvas, 0, (getHeight() - mark * .38f) * .5f, mark, INK, PANEL);
            }
        };
        LinearLayout.LayoutParams emblemSize = new LinearLayout.LayoutParams(ui.dp(53), ui.dp(26));
        emblemSize.setMarginStart(ui.dp(14));
        plate.addView(emblem, emblemSize);
        parent.addView(plate, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addWallpaperActions(LinearLayout parent, boolean home) {
        LinearLayout row = new LinearLayout(this);
        row.setBaselineAligned(false);
        Button preview = ui.button(home ? "主屏预览" : "锁屏预览", false);
        row.addView(preview, new LinearLayout.LayoutParams(0, -1, 1));
        preview.setOnClickListener(view -> startActivity(new Intent(this, PreviewActivity.class)
                .putExtra(PreviewActivity.EXTRA_HOME, home)));

        Button apply = ui.button(home ? "设置主屏壁纸" : "设置锁屏壁纸", true);
        LinearLayout.LayoutParams applySize = new LinearLayout.LayoutParams(0, -1, 1);
        applySize.setMarginStart(ui.dp(8));
        row.addView(apply, applySize);
        apply.setOnClickListener(view -> openWallpaperPicker(home ? HomeWallpaperService.class : RhineWallpaperService.class));
        addRow(parent, row);
    }

    private void showNameEditor(TextView current) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(LockDisplayName.MAX_LENGTH)});
        input.setText(LockDisplayName.read(prefs));
        input.selectAll();
        LinearLayout body = column();
        body.setPadding(ui.dp(24), 0, ui.dp(24), 0);
        body.addView(input, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("锁屏称呼")
                .setView(body)
                .setNeutralButton("恢复默认", (d, which) -> setName(current, LockDisplayName.DEFAULT))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, which) -> setName(current, input.getText().toString()))
                .create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }

    private void setName(TextView current, String raw) {
        String name = LockDisplayName.normalize(raw);
        prefs.edit().putString(LockDisplayName.KEY, name).apply();
        current.setText(name);
    }

    private void openWallpaperPicker(Class<?> service) {
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(this, service));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException unavailable) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, "此设备未提供动态壁纸选择器", Toast.LENGTH_LONG).show();
            }
        }
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private void section(LinearLayout parent, String name) {
        TextView heading = ui.text(name, 18, INK, true);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        heading.setPadding(0, ui.dp(18), 0, ui.dp(10));
        parent.addView(heading);
    }

    private void addRow(LinearLayout parent, View row) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.bottomMargin = ui.dp(5);
        parent.addView(row, size);
    }

    private LinearLayout settingRow(LinearLayout parent, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ui.dp(52));
        row.setFocusable(true);
        row.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8));
        row.setBackground(ui.control(PANEL, RULE));
        row.addView(ui.text(title, 15, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        addRow(parent, row);
        return row;
    }

    private TextView valueText(String label) {
        TextView value = ui.text(label, 14, ACCENT, false);
        value.setMaxWidth(ui.dp(158));
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        value.setMaxLines(2);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return value;
    }

    private void addValue(LinearLayout row, TextView value) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, -2);
        size.setMarginStart(ui.dp(12));
        row.addView(value, size);
    }

    private void toggle(LinearLayout parent, String title, String key, boolean fallback) {
        LinearLayout row = settingRow(parent, title);
        Switch control = new Switch(this);
        control.setChecked(prefs.getBoolean(key, fallback));
        control.setContentDescription(title);
        row.addView(control);
        row.setOnClickListener(view -> control.toggle());
        control.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(key, checked).apply());
    }

    private void choice(LinearLayout parent, String title, String[] labels, String[] values, String key) {
        String saved = prefs.getString(key, values[0]);
        int selected = 0;
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(saved)) selected = index;
        }
        selector(parent, title, labels, selected, index -> prefs.edit().putString(key, values[index]).apply());
    }

    private void selector(LinearLayout parent, String title, String[] labels, int selected, IntConsumer save) {
        LinearLayout row = settingRow(parent, title);
        TextView value = valueText(labels[selected]);
        addValue(row, value);
        int[] current = {selected};
        row.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, current[0], (dialog, which) -> {
                    save.accept(which);
                    current[0] = which;
                    value.setText(labels[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show());
    }
}
