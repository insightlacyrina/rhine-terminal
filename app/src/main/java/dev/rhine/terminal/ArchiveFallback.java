package dev.rhine.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;

/** Lightweight Android Canvas fallback for devices that cannot create a GLES window surface. */
final class ArchiveFallback {
    private final ArchiveLockOverlay overlay;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path loop = new Path();

    ArchiveFallback() {
        this(null);
    }

    ArchiveFallback(Context context) {
        overlay = new ArchiveLockOverlay(context);
    }

    void draw(Canvas canvas, ArchiveTimeline.FrameState frame, int battery, boolean plugged,
              boolean locked, boolean clock, String displayName) {
        if (frame.stage == ArchiveTimeline.HOME) {
            drawHome(canvas,paint,true,false);
            return;
        }
        int w=canvas.getWidth(), h=canvas.getHeight();
        float side=Math.min(w,h);
        boolean portrait=h>w;
        boolean denied=frame.intensity>0;
        canvas.drawColor(denied ? 0xff0e0c0d : 0xffe4dfdc);
        loop.reset();
        for (int i=0;i<=96;i++) {
            double a=2*Math.PI*i/96;
            float curveX = (float)Math.cos(a);
            float curveY = (float)(.95*Math.sin(a)*Math.cos(a) + .11*Math.sin(a));
            float x=w*(portrait?.51f:.45f)+side*.36f*(portrait ? .73f*curveX+.68f*curveY : curveX);
            float y=h*(portrait?.46f:.495f)-side*.36f*(portrait ? .68f*curveX-.73f*curveY : curveY);
            if (i==0) loop.moveTo(x,y); else loop.lineTo(x,y);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(side*.031f);
        paint.setColor(denied?0xff57301e:0xffb4aca4);
        canvas.drawPath(loop,paint);
        paint.setStrokeWidth(side*.021f);
        paint.setColor(denied?0xff89472b:0xfff1e8df);
        canvas.drawPath(loop,paint);
        if (denied) {
            paint.setColor(0xfff26e12); paint.setStrokeWidth(2.4f);
            canvas.drawRect(1,1,w-1,h-1,paint);
        }
        paint.setStyle(Paint.Style.FILL);
        overlay.draw(canvas,w,h,frame.stage,frame.intensity,frame.nativeFailure,Build.MODEL,battery,plugged,locked,clock,frame.biometricDemo,displayName);
    }

    static void drawHome(Canvas canvas, Paint paint, boolean light, boolean darkGallery) {
        if (!light && !darkGallery) {
            HomeWallpaperRenderer.drawFallback(canvas,paint);
            return;
        }
        canvas.drawColor(darkGallery?0xff0b0b0c:0xffe4dfdc);
        int w=canvas.getWidth(), h=canvas.getHeight();
        float s=Math.min(w,h);
        boolean portrait=h>w;
        for (int i=4;i>=0;i--) {
            float x=w*((portrait?.25f:.57f)+i*(portrait?.125f:.077f));
            float y=h*((portrait?.48f:.57f)+i*(portrait?.057f:.052f));
            canvas.save();
            canvas.rotate(11f-i*11f,x,y);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffe9e6dd);
            canvas.drawRoundRect(x-s*.12f,y-s*.082f,x+s*.12f,y+s*.082f,s*.009f,s*.009f,paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s*.0013f);
            paint.setColor(0xffadaaa1);
            canvas.drawRoundRect(x-s*.12f,y-s*.082f,x+s*.12f,y+s*.082f,s*.009f,s*.009f,paint);
            canvas.restore();
        }
    }
}
