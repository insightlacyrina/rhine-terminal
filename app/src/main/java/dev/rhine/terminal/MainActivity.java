package dev.rhine.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
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
    private int selectedTab;
    private LinearLayout wallpaperPage, settingsPage;
    private ScrollView pages;
    private TextView wallpaperTab, settingsTab;
    private View wallpaperLine, settingsLine;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("terminal", MODE_PRIVATE);
        ui = new InstrumentUi(this);
        selectedTab = state == null ? 0 : state.getInt("selected_tab", 0);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout shell = column();
        android.content.res.Configuration configuration = getResources().getConfiguration();
        int screenWidth = configuration.screenWidthDp;
        int contentWidth = screenWidth > 1040 ? ui.dp(1040) : -1;
        root.addView(shell, new FrameLayout.LayoutParams(contentWidth, -1, Gravity.CENTER_HORIZONTAL));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
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
            shell.setPadding(ui.dp(20) + left, ui.dp(18) + top, ui.dp(20) + right, bottom);
            return insets;
        });

        boolean compact = configuration.screenHeightDp < 500;
        addHeader(shell, compact);
        addTabs(shell, compact);
        pages = new ScrollView(this);
        pages.setFillViewport(true);
        pages.setVerticalScrollBarEnabled(false);
        LinearLayout content = column();
        content.setPadding(0, 0, 0, ui.dp(24));
        pages.addView(content);
        shell.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        wallpaperPage = column();
        settingsPage = column();
        content.addView(wallpaperPage);
        content.addView(settingsPage);
        boolean wide = screenWidth >= 700;
        LinearLayout plates = new LinearLayout(this);
        plates.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        plates.setBaselineAligned(false);
        wallpaperPage.addView(plates);
        addWallpaperPlate(plates, false, wide);
        addWallpaperPlate(plates, true, wide);
        LinearLayout settings = settingsPage;
        section(settings, "主屏");
        choice(settings, "主屏风格", new String[]{"石墨终端", "暖白样本盒", "石墨样本盒", "深色边缘环带"},
                new String[]{CarbonHomeRenderer.STYLE, "light", "dark_gallery", "dark"}, "home_scene");
        toggle(settings, "深色边缘环带状态文字", "home_telemetry", true);
        toggle(settings, "解锁后展开", "home_unfold", true);

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
        selectTab(selectedTab);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("selected_tab", selectedTab);
        super.onSaveInstanceState(out);
    }

    private void addHeader(LinearLayout parent, boolean compact) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.TOP);
        TextView brand = ui.text("RHINE\nTERMINAL", compact ? 25 : 32, INK, true);
        brand.setLineSpacing(-ui.dp(3), 1f);
        brand.setLetterSpacing(-.035f);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        View badge = new View(this) {
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float width = getWidth() * .68f;
                NiriWordmark.draw(canvas, (getWidth() - width) * .5f,
                        (getHeight() - width * .38f) * .5f, width, INK, BG);
            }
        };
        badge.setContentDescription("NIRI");
        badge.setBackground(ui.plate(BG, INK));
        header.addView(badge, new LinearLayout.LayoutParams(ui.dp(58), ui.dp(58)));
        parent.addView(header, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addTabs(LinearLayout parent, boolean compact) {
        LinearLayout tabs = new LinearLayout(this);
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1, ui.dp(compact ? 48 : 56));
        size.topMargin=ui.dp(compact ? 8 : 16);
        parent.addView(tabs,size);
        LinearLayout[] cells={column(),column()};
        TextView[] labels={ui.text("壁纸",19,INK,true),ui.text("设置",19,MUTED,true)};
        View[] lines={new View(this),new View(this)};
        for(int i=0;i<2;i++) {
            labels[i].setGravity(Gravity.CENTER);
            cells[i].addView(labels[i],new LinearLayout.LayoutParams(-1,0,1));
            cells[i].addView(lines[i],new LinearLayout.LayoutParams(-1,ui.dp(2)));
            tabs.addView(cells[i],new LinearLayout.LayoutParams(0,-1,1));
            final int index=i;
            cells[i].setOnClickListener(v -> selectTab(index));
            cells[i].setFocusable(true);
        }
        wallpaperTab=labels[0]; settingsTab=labels[1]; wallpaperLine=lines[0]; settingsLine=lines[1];
    }

    private void selectTab(int index) {
        selectedTab=index;
        if(wallpaperPage==null) return;
        wallpaperPage.setVisibility(index==0?View.VISIBLE:View.GONE);
        settingsPage.setVisibility(index==1?View.VISIBLE:View.GONE);
        wallpaperTab.setTextColor(index==0?INK:MUTED); settingsTab.setTextColor(index==1?INK:MUTED);
        wallpaperLine.setBackgroundColor(index==0?ACCENT:RULE); settingsLine.setBackgroundColor(index==1?ACCENT:RULE);
        wallpaperTab.setSelected(index==0); settingsTab.setSelected(index==1);
        if (pages != null) pages.scrollTo(0, 0);
    }

    private void addWallpaperPlate(LinearLayout parent, boolean home, boolean wide) {
        LinearLayout plate=column();
        plate.setBackground(ui.plate(BG, 0xff777772));
        LinearLayout.LayoutParams plateSize=wide?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2);
        plateSize.topMargin=ui.dp(20);
        if(wide && home) plateSize.setMarginStart(ui.dp(16));
        parent.addView(plate,plateSize);
        FrameLayout artwork = new FrameLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int artworkHeight = Math.round(MeasureSpec.getSize(widthSpec) * .64f);
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(artworkHeight, MeasureSpec.EXACTLY));
            }
        };
        artwork.setForeground(ui.plate(Color.TRANSPARENT, 0xff777772));
        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription(null);
        try (java.io.InputStream stream=getAssets().open(home?"ui/home-art.webp":"ui/lock-art.webp")) {
            image.setImageDrawable(Drawable.createFromStream(stream,null));
        } catch (java.io.IOException ignored) { image.setBackgroundColor(PANEL); }
        artwork.addView(image,new FrameLayout.LayoutParams(-1,-1));
        View marks=new View(this) {
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                p.setColor(0xffbbb8b0); p.setStrokeWidth(ui.dp(1));
                float m=ui.dp(13), n=ui.dp(7), w=getWidth(), h=getHeight();
                for (int i = 0; i < 4; i++) {
                    float x = (i & 1) == 0 ? m : w - m;
                    float y = i < 2 ? m : h - m;
                    canvas.drawLine(x-n,y,x+n,y,p);
                    canvas.drawLine(x,y-n,x,y+n,p);
                }
            }
        };
        artwork.addView(marks,new FrameLayout.LayoutParams(-1,-1));
        plate.addView(artwork,new LinearLayout.LayoutParams(-1,-2));
        View divider=new View(this); divider.setBackgroundColor(0xff777772);
        plate.addView(divider,new LinearLayout.LayoutParams(-1,ui.dp(1)));
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(12));
        TextView title=ui.text(home?"主屏":"锁屏",24,INK,true);
        row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        View vertical=new View(this); vertical.setBackgroundColor(0xff777772);
        LinearLayout.LayoutParams rule=new LinearLayout.LayoutParams(ui.dp(1),ui.dp(42));
        rule.setMarginEnd(ui.dp(12)); row.addView(vertical,rule);
        Button preview=outlineButton("预览",false);
        preview.setContentDescription(home?"主屏预览":"锁屏预览");
        row.addView(preview,new LinearLayout.LayoutParams(ui.dp(72),ui.dp(48)));
        preview.setOnClickListener(v -> startActivity(new Intent(this,PreviewActivity.class).putExtra(PreviewActivity.EXTRA_HOME,home)));
        Button apply=outlineButton("应用",true);
        apply.setContentDescription(home?"应用主屏壁纸":"应用锁屏壁纸");
        LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(ui.dp(72),ui.dp(48)); action.setMarginStart(ui.dp(8));
        row.addView(apply,action);
        apply.setOnClickListener(v -> openWallpaperPicker(home?HomeWallpaperService.class:RhineWallpaperService.class));
        plate.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }

    private Button outlineButton(String label,boolean accent) {
        Button button=ui.button(label,false);
        button.setTextColor(accent?ACCENT:INK);
        button.setBackground(ui.control(BG,accent?ACCENT:INK));
        return button;
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
