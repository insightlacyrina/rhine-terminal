package dev.rhine.terminal;

import android.app.Activity;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.Build;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Foreground preview, with a visual-only warning demonstration. */
public final class PreviewActivity extends Activity implements SurfaceHolder.Callback {
    static final String EXTRA_HOME = "dev.rhine.terminal.HOME_PREVIEW";
    private GlRenderSession session;
    private SurfaceView view;
    private TextView stats;
    private final Runnable statsUpdate=new Runnable() {
        @Override public void run() {
            if (!resumed || stats == null) return;
            String value = session.getStats();
            int end = value.indexOf(" ·");
            stats.setText(end > 3 && value.startsWith("提交 ") ? value.substring(3, end) : "— fps");
            stats.setContentDescription(value);
            stats.postDelayed(this, 1000);
        }
    };
    private boolean resumed, ready, screenOn = true, home;
    private int battery = -1;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) screenOn = false;
            else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) screenOn = true;
            else if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) setBattery(intent);
            else if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(intent.getAction())) session.refresh();
            update();
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        home = getIntent().getBooleanExtra(EXTRA_HOME, false);
        session = new GlRenderSession(this, home);
        session.setLockPreview(!home);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        android.content.SharedPreferences prefs=getSharedPreferences("terminal",MODE_PRIVATE);
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        lp.preferredRefreshRate=prefs.getBoolean("reduced_motion",false)?0:power.isPowerSaveMode()?30:prefs.getInt("target_fps",120)<=60?60:120;
        getWindow().setAttributes(lp);
        FrameLayout frame = new FrameLayout(this);
        view = new SurfaceView(this) {
            @Override public boolean performClick() { super.performClick(); return true; }
        };
        view.getHolder().addCallback(this); frame.addView(view);
        if (!home) view.setOnTouchListener((v, event) -> {
            session.interact();
            if (!getSharedPreferences("terminal", MODE_PRIVATE).getBoolean("reduced_motion", false) && view.getWidth() > 0 && view.getHeight() > 0 && (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE)) {
                float x = Math.max(-.2f, Math.min(.2f, (event.getX() / view.getWidth() - .5f) * .4f));
                float y = Math.max(-.2f, Math.min(.2f, (event.getY() / view.getHeight() - .5f) * .4f));
                session.setInput(x, y, battery);
            }
            if (event.getActionMasked()==MotionEvent.ACTION_DOWN&&!getSharedPreferences("terminal",MODE_PRIVATE).getBoolean("reduced_motion",false)) session.pulse();
            if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL) session.setInput(0,0,battery);
            if (event.getActionMasked() == MotionEvent.ACTION_UP) v.performClick();
            return true;
        });
        InstrumentUi ui = new InstrumentUi(this);
        final int margin=ui.dp(16), gap=ui.dp(8), height=ui.dp(48);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams topPosition=new FrameLayout.LayoutParams(-1,height,Gravity.TOP);
        topPosition.setMargins(margin,margin,margin,0);
        frame.addView(top,topPosition);
        stats=ui.button("— fps",false);
        stats.setTextSize(13);
        stats.setTextColor(InstrumentUi.ACCENT);
        top.addView(stats,new LinearLayout.LayoutParams(-2,height));
        stats.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("性能数据").setMessage(session.getStats()).setPositiveButton("关闭",null).show());
        TextView close=ui.button("退出预览",false);
        LinearLayout.LayoutParams closePosition=new LinearLayout.LayoutParams(-2,height);
        closePosition.setMarginStart(gap);
        top.addView(close,closePosition);
        close.setOnClickListener(v -> finish());
        LinearLayout bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.END);
        FrameLayout.LayoutParams bottomPosition=new FrameLayout.LayoutParams(-2,height,Gravity.BOTTOM|Gravity.END);
        bottomPosition.setMargins(margin,0,margin,margin);
        frame.addView(bottom,bottomPosition);
        if (!home) {
            TextView denial=ui.button("拒绝演示",false);
            LinearLayout.LayoutParams denialPosition=new LinearLayout.LayoutParams(-2,height);
            denialPosition.setMargins(0,0,gap,0);
            bottom.addView(denial,denialPosition);
            denial.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                    .setTitle("选择演示")
                    .setItems(new String[]{"人脸识别失败（演示）","指纹识别失败（演示）",
                            "指纹传感器短触（演示）"},(dialog,item) -> {
                        session.playBiometricDemo(item+1);
                    }).show());
        }
        TextView demo=ui.button(home ? "演示样本展开" : "完整动效演示",true);
        bottom.addView(demo,new LinearLayout.LayoutParams(-2,height));
        demo.setOnClickListener(v -> {
            if (home) session.unfoldHome();
            else {
                session.playArchiveDemo();
            }
        });
        frame.setOnApplyWindowInsetsListener((v,insets) -> {
            int safeLeft=insets.getSystemWindowInsetLeft(), safeRight=insets.getSystemWindowInsetRight();
            int safeTop=insets.getSystemWindowInsetTop(), safeBottom=insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT>=28 && insets.getDisplayCutout()!=null) {
                android.view.DisplayCutout cutout=insets.getDisplayCutout();
                safeLeft=Math.max(safeLeft,cutout.getSafeInsetLeft()); safeRight=Math.max(safeRight,cutout.getSafeInsetRight());
                safeTop=Math.max(safeTop,cutout.getSafeInsetTop()); safeBottom=Math.max(safeBottom,cutout.getSafeInsetBottom());
            }
            if (Build.VERSION.SDK_INT>=29) {
                android.graphics.Insets gestures=insets.getSystemGestureInsets();
                safeLeft=Math.max(safeLeft,gestures.left); safeRight=Math.max(safeRight,gestures.right);
                safeBottom=Math.max(safeBottom,gestures.bottom);
            }
            int left=margin+safeLeft, right=margin+safeRight;
            topPosition.setMargins(left,margin+safeTop,right,0);
            bottomPosition.setMargins(left,0,right,margin+safeBottom);
            top.setLayoutParams(topPosition);
            bottom.setLayoutParams(bottomPosition);
            return insets;
        });
        setContentView(frame);
        IntentFilter screen = new IntentFilter(Intent.ACTION_SCREEN_ON); screen.addAction(Intent.ACTION_SCREEN_OFF); screen.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        screen.addAction(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, screen, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(screenReceiver, screen);
    }
    private void update() {
        PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
        session.setActive(resumed && ready && screenOn && power.isInteractive());
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        screenOn = ((PowerManager)getSystemService(POWER_SERVICE)).isInteractive();
        Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i != null) setBattery(i);
        else if (!home) session.setInput(0, 0, battery);
        update(); stats.removeCallbacks(statsUpdate); stats.post(statsUpdate);
    }
    private void setBattery(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        battery = level >= 0 && scale > 0 ? Math.min(100, level * 100 / scale) : -1;
        session.setBattery(battery, intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0);
    }
    @Override protected void onPause() { resumed = false; stats.removeCallbacks(statsUpdate); update(); super.onPause(); }
    @Override protected void onDestroy() { unregisterReceiver(screenReceiver); session.close(); super.onDestroy(); }
    @Override public void surfaceCreated(SurfaceHolder holder) { ready = true; android.graphics.Rect r = holder.getSurfaceFrame(); session.setSurface(holder.getSurface(), r.width(), r.height()); update(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) { session.setSurface(holder.getSurface(), w, h); update(); }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { ready = false; update(); session.destroySurface(); }
}
