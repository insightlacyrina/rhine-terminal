package dev.rhine.terminal;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;
import android.widget.TextView;

/** App controls use the graphite, paper and lime palette of the archive. */
final class InstrumentUi {
    static final int BG = 0xff0b0b0d;
    static final int PANEL = 0xff151619;
    static final int RULE = 0xff35383a;
    static final int INK = 0xffeeeae4;
    static final int MUTED = 0xffb7b8b0;
    static final int ACCENT = 0xffc3fa3d;

    private final Context context;
    private final Typeface heading;

    InstrumentUi(Context context) {
        this.context = context;
        Typeface font;
        try {
            font = Typeface.createFromAsset(context.getAssets(), "rhine-lock/fonts/Montserrat-Bold.ttf");
        } catch (RuntimeException unavailable) {
            font = Typeface.DEFAULT_BOLD;
        }
        heading = font;
    }

    int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    TextView text(String label, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(heading);
        return view;
    }

    GradientDrawable plate(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    Drawable control(int fill, int stroke) {
        return new RippleDrawable(ColorStateList.valueOf(0x30c3fa3d), plate(fill, stroke), null);
    }

    Button button(String label, boolean primary) {
        Button view = new Button(context);
        view.setText(label);
        view.setAllCaps(false);
        view.setTypeface(heading);
        view.setTextSize(15);
        view.setTextColor(primary ? BG : INK);
        view.setBackground(control(primary ? ACCENT : PANEL, primary ? ACCENT : RULE));
        view.setStateListAnimator(null);
        view.setMinHeight(dp(48));
        view.setPadding(dp(10), dp(10), dp(10), dp(10));
        return view;
    }
}
