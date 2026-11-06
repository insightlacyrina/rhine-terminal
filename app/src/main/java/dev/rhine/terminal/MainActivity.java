package dev.rhine.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(9, 12, 11), PANEL = Color.rgb(21, 27, 23), INK = Color.rgb(230, 235, 218), MUTED = Color.rgb(146, 158, 139), ACCENT = Color.rgb(221, 235, 112);
    private SharedPreferences prefs;
    private LinearLayout content;
    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("terminal", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(24), dp(22), dp(24), dp(36));
        scroll.addView(content); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> { content.setPadding(dp(24), dp(22) + insets.getSystemWindowInsetTop(), dp(24), dp(36) + insets.getSystemWindowInsetBottom()); return insets; });
        TextView kicker = text("R H I N E  /  T E R M I N A L", 11, ACCENT, true); content.addView(kicker);
        TextView title = text("终端壁纸", 32, INK, true); title.setPadding(0, dp(12), 0, dp(6)); content.addView(title);
        TextView intro = text("陶瓷档案锁屏 · 暖灰样本主屏", 14, MUTED, false); intro.setLineSpacing(dp(3), 1f); content.addView(intro);
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.VERTICAL); actions.setPadding(0, dp(24), 0, dp(18)); content.addView(actions);
        Button preview = button("锁屏预览 / 完整动效演示", false); actions.addView(preview); preview.setOnClickListener(v -> startActivity(new Intent(this, PreviewActivity.class)));
        Button apply = button("设置锁屏壁纸  ↗", false); LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(52)); ap.topMargin = dp(10); actions.addView(apply, ap);
        apply.setOnClickListener(v -> openWallpaperPicker(RhineWallpaperService.class));
        addHomeActions();
        LinearLayout nativeCard = card();
        nativeCard.addView(text("系统密码失败变色（可选）", 15, INK, true));
        TextView nativeHint = text("默认关闭；需主动授权设备管理权限。仅限系统提供的 PIN 或密码失败事件。", 12, MUTED, false);
        nativeHint.setPadding(0, dp(5), 0, dp(10)); nativeHint.setLineSpacing(dp(2), 1f); nativeCard.addView(nativeHint);
        Button nativeOptions = button("查看说明与授权", false); nativeCard.addView(nativeOptions);
        nativeOptions.setOnClickListener(v -> startActivity(new Intent(this, FailureAccessActivity.class)));
        label("个性化", "PERSONALIZATION");
        lockDisplayNameSetting();
        toggle("显示主题时钟", "默认开启。三星原生时钟可在系统锁屏编辑中由你移除。", "archive_clock", true);
        toggle("预览音效", "在完整动效演示中播放环境音与状态提示音。", "preview_sound", false);
        toggle("设备运动", "轻微倾斜时移动画面；仅在壁纸可见时使用传感器", "motion", false);
        toggle("减少动态效果", "降低动画频率并关闭运动响应", "reduced_motion", false);
        label("画面", "DISPLAY");
        frameRateChoice();
        TextView note = text("壁纸与锁屏的显示方式由设备系统决定。壁纸不会替代系统锁屏或解锁方式。", 12, MUTED, false); note.setLineSpacing(dp(3), 1f); note.setPadding(0, dp(20), 0, 0); content.addView(note);
    }

    private void lockDisplayNameSetting() {
        LinearLayout row = card();
        TextView current = text(LockDisplayName.read(prefs), 14, MUTED, false);
        current.setSingleLine(true);
        current.setPadding(0, 0, 0, dp(8));
        row.addView(current);
        Button edit = button("锁屏称呼", false);
        row.addView(edit);
        edit.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(LockDisplayName.MAX_LENGTH)});
            input.setHint(LockDisplayName.DEFAULT);
            input.setText(LockDisplayName.read(prefs));
            input.selectAll();
            LinearLayout body = new LinearLayout(this);
            body.setPadding(dp(24), 0, dp(24), 0);
            body.addView(input, new LinearLayout.LayoutParams(-1, -2));
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("锁屏称呼")
                    .setMessage("留空恢复 ZSY。")
                    .setView(body)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("保存", (d, which) -> {
                        String name = LockDisplayName.normalize(input.getText().toString());
                        prefs.edit().putString(LockDisplayName.KEY, name).apply();
                        current.setText(name);
                    })
                    .create();
            dialog.setOnShowListener(d -> {
                input.requestFocus();
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            });
            dialog.show();
        });
    }

    private void addHomeActions() {
        label("主屏壁纸", "SPECIMEN GALLERY");
        LinearLayout home = card();
        TextView description = text("碳黑终端显示设备巡检数据，右侧是五个亚克力样本盒。应用时选「主屏幕」。", 12, MUTED, false);
        description.setPadding(0, 0, 0, dp(12));
        home.addView(description);
        Button preview = button("主屏预览 / 展开演示", true);
        home.addView(preview);
        preview.setOnClickListener(v -> startActivity(new Intent(this, PreviewActivity.class)
                .putExtra(PreviewActivity.EXTRA_HOME, true)));
        Button apply = button("设置主屏壁纸  ↗", false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.topMargin = dp(8);
        home.addView(apply, params);
        apply.setOnClickListener(v -> openWallpaperPicker(HomeWallpaperService.class));
        choice("主屏风格", new String[]{"石墨终端", "暖白样本盒", "石墨样本盒", "深色边缘环带"}, new String[]{CarbonHomeRenderer.STYLE, "light", "dark_gallery", "dark"}, "home_scene");
        toggle("深色主屏状态文字", "仅在深色环带风格中显示设备状态。", "home_telemetry", true);
        toggle("解锁后展开", "收到系统解锁后信号且壁纸可见时，播放约 1.15 秒的展开。", "home_unfold", true);
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
    private TextView text(String s, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextColor(color); t.setTextSize(size); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private void frameRateChoice() {
        LinearLayout rate=card(); rate.addView(text("目标帧率",15,INK,true));
        TextView hint=text("120 帧需系统和屏幕支持；省电和减少动态效果时会降低刷新频率。",12,MUTED,false);
        hint.setPadding(0,dp(5),0,dp(6)); rate.addView(hint);
        Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"120 帧 / 流畅","60 帧 / 节能"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); spinner.setContentDescription("目标帧率");
        spinner.setSelection(prefs.getInt("target_fps",120)<=60?1:0); rate.addView(spinner);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { prefs.edit().putInt("target_fps",position==0?120:60).apply(); }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
    private GradientDrawable shape(int fill, int stroke) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(12)); d.setStroke(dp(1), stroke); return d; }
    private Button button(String s, boolean primary) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(primary ? BG : INK); b.setBackground(shape(primary ? ACCENT : PANEL, primary ? ACCENT : Color.rgb(65, 77, 58))); b.setMinHeight(dp(52)); b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(52))); return b; }
    private void label(String title, String en) { LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(18), 0, dp(10)); TextView t = text(title, 17, INK, true); row.addView(t, new LinearLayout.LayoutParams(0, -2, 1)); row.addView(text(en, 10, MUTED, true)); content.addView(row); }
    private LinearLayout card() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16), dp(15), dp(16), dp(15)); c.setBackground(shape(PANEL, Color.rgb(40, 49, 39))); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(8); content.addView(c, p); return c; }
    private void toggle(String title, String hint, String key, boolean def) { LinearLayout c = card(); LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); c.addView(row); LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); row.addView(words, new LinearLayout.LayoutParams(0, -2, 1)); words.addView(text(title, 15, INK, true)); TextView h = text(hint, 12, MUTED, false); h.setPadding(0, dp(5), dp(10), 0); h.setLineSpacing(dp(2), 1f); words.addView(h); Switch sw = new Switch(this); sw.setChecked(prefs.getBoolean(key, def)); sw.setContentDescription(title); row.addView(sw); sw.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(key, checked).apply()); }
    private void choice(String title, String[] labels, String[] values, String key) { LinearLayout c = card(); c.addView(text(title, 15, INK, true)); Spinner spinner = new Spinner(this); ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) { @Override public View getView(int position, View convertView, android.view.ViewGroup parent) { TextView v = (TextView) super.getView(position, convertView, parent); v.setTextColor(INK); v.setPadding(0, dp(10), 0, dp(4)); return v; } }; adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter); String current = prefs.getString(key, "auto"); for (int i = 0; i < values.length; i++) if (values[i].equals(current)) spinner.setSelection(i); c.addView(spinner); spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() { public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { prefs.edit().putString(key, values[position]).apply(); } public void onNothingSelected(android.widget.AdapterView<?> parent) {} }); }
}
