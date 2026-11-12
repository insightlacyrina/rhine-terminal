package dev.rhine.terminal;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.opengl.GLES30;
import android.opengl.GLUtils;

/** Small independently refreshed texture for the scrolling device log. */
final class GpuLockTelemetry {
    private static final String FRAGMENT = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uLog;
        uniform float uOpacity;
        out vec4 frag;
        void main() { frag=texture(uLog,vUv)*uOpacity; }
        """;
    private int program, texture, opacityLocation, x, y, w, h;
    private final RectF region = new RectF();
    private Bitmap bitmap;
    private Canvas canvas;
    private String[] lastSnapshot;
    private long lastUpdate=Long.MIN_VALUE;
    private boolean lastDark, lastPhone;
    private int screenW, screenH;

    void draw(ArchiveLockOverlay overlay, int vao, int width, int height, boolean phone,
              boolean dark, String[] snapshot, long elapsedMs, float opacity) {
        if (snapshot==null || opacity<=.001f) return;
        if (program==0) {
            program=GpuTerminalRenderer.linkProgram(LockComposition.VERTEX,FRAGMENT);
            opacityLocation=GLES30.glGetUniformLocation(program,"uOpacity");
            GLES30.glUseProgram(program);
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"uLog"),0);
            int[] names=new int[1]; GLES30.glGenTextures(1,names,0); texture=names[0];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
        }
        if (bitmap==null || screenW!=width || screenH!=height || lastPhone!=phone) {
            if(bitmap!=null) bitmap.recycle();
            ArchiveLockOverlay.telemetryBounds(width,height,phone,region);
            x=Math.max(0,(int)Math.floor(region.left)-2);
            y=Math.max(0,(int)Math.floor(region.top)-2);
            w=Math.min(width-x,(int)Math.ceil(region.right)+2-x);
            h=Math.min(height-y,(int)Math.ceil(region.bottom)+2-y);
            bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            bitmap.setDensity(Bitmap.DENSITY_NONE); canvas=new Canvas(bitmap);
            screenW=width; screenH=height; lastPhone=phone; lastUpdate=Long.MIN_VALUE;
        }
        if (lastUpdate==Long.MIN_VALUE || elapsedMs<lastUpdate || elapsedMs-lastUpdate>=33
                || lastSnapshot!=snapshot || lastDark!=dark) {
            bitmap.eraseColor(0);
            canvas.save(); canvas.translate(-x,-y);
            overlay.drawTelemetry(canvas,width,height,phone,dark,snapshot,elapsedMs);
            canvas.restore();
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
            if(lastUpdate==Long.MIN_VALUE) GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,bitmap,0);
            else GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D,0,0,0,bitmap);
            lastUpdate=elapsedMs; lastSnapshot=snapshot; lastDark=dark;
        }
        GLES30.glViewport(x,height-y-h,w,h);
        GLES30.glUseProgram(program); GLES30.glBindVertexArray(vao);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
        GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_ONE,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glUniform1f(opacityLocation,opacity);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);
        GLES30.glDisable(GLES30.GL_BLEND); GLES30.glViewport(0,0,width,height);
    }
    void release() {
        if(program!=0) GLES30.glDeleteProgram(program);
        if(texture!=0) GLES30.glDeleteTextures(1,new int[]{texture},0);
        program=texture=0; lastUpdate=Long.MIN_VALUE; lastSnapshot=null;
        if(bitmap!=null) { bitmap.recycle(); bitmap=null; canvas=null; }
    }
}
