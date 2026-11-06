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
import android.widget.FrameLayout;
import android.widget.TextView;

/** Foreground preview, with a visual-only warning demonstration. */
public final class PreviewActivity extends Activity implements SurfaceHolder.Callback {
    static final String EXTRA_HOME = "dev.rhine.terminal.HOME_PREVIEW";
    private GlRenderSession session;
    private SurfaceView view;
    private TextView stats;
    private ArchivePreviewAudio audio;
    private final Runnable statsUpdate=new Runnable() {
        @Override public void run() { if(resumed&&stats!=null) { stats.setText(session.getStats()); stats.postDelayed(this,1000); } }
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
        if (!home && prefs.getBoolean("preview_sound",false)) {
            audio = new ArchivePreviewAudio(this);
            setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        }
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        lp.preferredRefreshRate=prefs.getBoolean("reduced_motion",false)?0:power.isPowerSaveMode()?30:prefs.getInt("target_fps",120)<=60?60:120;
        getWindow().setAttributes(lp);
        FrameLayout frame = new FrameLayout(this);
        view = new SurfaceView(this) {
            @Override public boolean performClick() { super.performClick(); return true; }
        };
        view.getHolder().addCallback(this); frame.addView(view);
        if (!home) view.setOnTouchListener((v, event) -> {
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
        int d = (int)(getResources().getDisplayMetrics().density * 12);
        TextView close = button("✕  退出预览", d);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END); cp.setMargins(d, d * 2, d, d); frame.addView(close, cp); close.setOnClickListener(v -> finish());
        stats=button("等待画面",d); stats.setTextSize(11);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START); sp.setMargins(d,d*2,d,d); frame.addView(stats,sp);
        TextView demo = button(home ? "演示样本展开" : "完整动效演示", d);
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | (home ? Gravity.CENTER_HORIZONTAL : Gravity.END));
        dp.setMargins(d, d, d, d * 2);
        frame.addView(demo, dp);
        demo.setOnClickListener(v -> {
            if (home) session.unfoldHome();
            else {
                session.playArchiveDemo();
                if (audio != null) audio.play();
            }
        });
        if (!home) {
            TextView denial = button("黑化触发演示", d);
            FrameLayout.LayoutParams denialPosition = new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);
            denialPosition.setMargins(d,d,d,d*2);
            frame.addView(denial,denialPosition);
            denial.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                    .setTitle("选择演示")
                    .setItems(new String[]{"人脸识别失败（演示）","指纹识别失败（演示）",
                            "指纹传感器短触（演示）"},(dialog,item) -> {
                        if (audio!=null) audio.stop();
                        session.playBiometricDemo(item+1);
                    }).show());
        }
        setContentView(frame);
        IntentFilter screen = new IntentFilter(Intent.ACTION_SCREEN_ON); screen.addAction(Intent.ACTION_SCREEN_OFF); screen.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        screen.addAction(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, screen, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(screenReceiver, screen);
    }
    private TextView button(String text, int pad) {
        TextView result = new TextView(this); result.setText(text); result.setTextColor(0xffe6ebda); result.setTextSize(14); result.setGravity(Gravity.CENTER); result.setBackgroundColor(0xcc18211b); result.setPadding(pad, pad, pad, pad); return result;
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
    @Override protected void onPause() { if (audio != null) audio.stop(); resumed = false; stats.removeCallbacks(statsUpdate); update(); super.onPause(); }
    @Override protected void onDestroy() { if (audio != null) audio.release(); unregisterReceiver(screenReceiver); session.close(); super.onDestroy(); }
    @Override public void surfaceCreated(SurfaceHolder holder) { ready = true; android.graphics.Rect r = holder.getSurfaceFrame(); session.setSurface(holder.getSurface(), r.width(), r.height()); update(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) { session.setSurface(holder.getSurface(), w, h); update(); }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { ready = false; update(); session.destroySurface(); }
}
