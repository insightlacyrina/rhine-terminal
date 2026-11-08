/* Sculpture rendering derived from rhine-lock, AGPL-3.0-or-later,
 * https://github.com/tmc5csxr9m-cpu/rhine-lock, commit a4df68c6bd9e01ecb7660659becf495957e57faa. */
package dev.rhine.terminal;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

/** GLES 3 renderer. Its caller owns EGL, the surface, presentation timing, and this GL thread. */
public final class GpuTerminalRenderer {
    private static final String TAG="RhineGpu";
    private static final int MAX_UI_EDGE=4096;
    private static final int MAX_UI_PIXELS=8_388_608;
    private final ArchiveLockOverlay overlay;
    private final LockBlackoutPass blackout = new LockBlackoutPass();
    private final GpuLockTelemetry telemetryPass = new GpuLockTelemetry();
    private String[] telemetry;
    private final SpecimenScenePass snapshot = new SpecimenScenePass();
    private final Context context;
    private final SharedPreferences prefs;
    private final String device = android.os.Build.MODEL;
    private final float[] model = new float[16], view = new float[16], projection = new float[16];
    private final float[] viewModel = new float[16], mvp = new float[16];
    private final int[] names = new int[2];
    private int width, height, uiWidth, uiHeight, textureLimit=MAX_UI_EDGE;
    private int sculptureProgram, stageProgram, hudProgram;
    private int vao, vbo, ebo, planeVao, uiTexture, indexCount;
    private int mvpLocation, modelLocation, timeLocation, alertLocation, pulseLocation;
    private int birthLocation, verifyLocation, peelLocation, disorderLocation, idleBlackLocation;
    private int stageSize, stageDeny, stageDenyMix, stageSeconds, stageBirth, stageGlitch;
    private int hudTexture, hudSize, hudPhases, hudScan, hudScanHead, hudScanOpacity, hudScanSeconds, hudWarningAge, hudPhone;
    private Bitmap uiBitmap;
    private Canvas uiCanvas;
    private long lastUiMinute = Long.MIN_VALUE, animationEpoch = Long.MIN_VALUE;
    private boolean lastLocked, lastPlugged, lastClock, initialized;
    private int lastStage, lastSeverity, lastBattery, lastBiometricDemo;
    private boolean lastNative, lastPhone, lastDarkPalette, lastIdlePopup;
    private String lastDisplayName;

    public GpuTerminalRenderer(Context context) {
        this.context = context;
        overlay = new ArchiveLockOverlay(context);
        prefs = context.getSharedPreferences("terminal", Context.MODE_PRIVATE);
    }

    public void setTelemetry(String[] snapshot) { telemetry=snapshot; }

    /** Invoke with a current GLES 3 context; this also handles a newly recreated context. */
    public void initialize() {
        // A recreated EGL context has a fresh object namespace; never delete stale names in it.
        initialized=false;
        try {
            sculptureProgram = linkProgram(SculptureScene.VERTEX_SHADER, SculptureScene.FRAGMENT_SHADER);
            stageProgram = linkProgram(LockComposition.VERTEX, LockComposition.STAGE);
            hudProgram = linkProgram(LockComposition.VERTEX, LockComposition.HUD);
            snapshot.initialize(context,true);
            mvpLocation = location(sculptureProgram, "uMvp");
            modelLocation = location(sculptureProgram, "uModel");
            timeLocation = location(sculptureProgram, "uTime");
            alertLocation = location(sculptureProgram, "uAlert");
            pulseLocation = location(sculptureProgram, "uPulse");
            birthLocation = location(sculptureProgram, "uBirth");
            verifyLocation = location(sculptureProgram, "uVerify");
            peelLocation = location(sculptureProgram, "uExit");
            disorderLocation = location(sculptureProgram, "uDisorder");
            idleBlackLocation = location(sculptureProgram, "uIdleBlack");
            stageSize = location(stageProgram, "uSize");
            stageDeny = location(stageProgram, "uDeny");
            stageDenyMix = location(stageProgram, "uDenyMix");
            stageSeconds = location(stageProgram, "uSeconds");
            stageBirth = location(stageProgram, "uBirth");
            stageGlitch = location(stageProgram, "uGlitch");
            hudTexture = location(hudProgram, "uUi");
            hudSize = location(hudProgram, "uSize");
            hudPhases = location(hudProgram, "uUiPhases");
            hudScan = location(hudProgram, "uScan");
            hudScanHead = location(hudProgram, "uScanHead");
            hudScanOpacity = location(hudProgram, "uScanOpacity");
            hudScanSeconds = location(hudProgram, "uScanSeconds");
            hudWarningAge = location(hudProgram, "uWarningAge");
            hudPhone = location(hudProgram, "uPhone");
            GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE,names,0);
            textureLimit=Math.min(MAX_UI_EDGE,names[0]);
            SculptureScene.Mesh mesh=SculptureScene.createMesh();
            indexCount=mesh.indexCount;
            GLES30.glGenVertexArrays(1,names,0); vao=names[0];
            GLES30.glBindVertexArray(vao);
            GLES30.glGenBuffers(2,names,0); vbo=names[0]; ebo=names[1];
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,mesh.vertices.remaining()*Float.BYTES,
                    mesh.vertices,GLES30.GL_STATIC_DRAW);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,ebo);
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,mesh.indices.remaining()*Integer.BYTES,
                    mesh.indices,GLES30.GL_STATIC_DRAW);
            GLES30.glEnableVertexAttribArray(0);
            GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,SculptureScene.STRIDE_BYTES,0);
            GLES30.glEnableVertexAttribArray(1);
            GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,SculptureScene.STRIDE_BYTES,3*Float.BYTES);
            GLES30.glGenVertexArrays(1,names,0); planeVao=names[0];
            GLES30.glBindVertexArray(0);
            GLES30.glGenTextures(1,names,0); uiTexture=names[0];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,uiTexture);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
            Matrix.setLookAtM(view,0,0,0,4.4f,0,0,0,0,1,0);
            Matrix.perspectiveM(projection,0,31.5f,1,.1f,12);
            lastUiMinute=Long.MIN_VALUE;
            initialized=true;
            int error=GLES30.glGetError();
            if(error!=GLES30.GL_NO_ERROR) throw new IllegalStateException("GLES initialization error 0x"+Integer.toHexString(error));
            Log.i(TAG,"GLES3 upstream sculpture ready: "+SculptureScene.FIN_COUNT
                    +" fins, "+(indexCount/3*SculptureScene.FIN_COUNT)+" triangles; renderer="+
                    GLES30.glGetString(GLES30.GL_RENDERER));
        } catch(RuntimeException failure) {
            release();
            throw failure;
        }
    }

    public void resize(int w,int h) {
        if(w<=0||h<=0) return;
        if(width==w&&height==h&&uiBitmap!=null) return;
        width=w; height=h;
        // Keep vector/Canvas text at 1:1 on the 2560×1600 tablet surface.
        float scale=Math.min(1f,textureLimit/(float)Math.max(w,h));
        scale=Math.min(scale,(float)Math.sqrt(MAX_UI_PIXELS/((double)w*h)));
        uiWidth=Math.max(1,Math.round(w*scale));
        uiHeight=Math.max(1,Math.round(h*scale));
        if(uiBitmap!=null) uiBitmap.recycle();
        uiBitmap=Bitmap.createBitmap(uiWidth,uiHeight,Bitmap.Config.ARGB_8888);
        uiBitmap.setDensity(Bitmap.DENSITY_NONE);
        uiCanvas=new Canvas(uiBitmap);
        lastUiMinute=Long.MIN_VALUE;
    }

    public void render(long frameNanos, float tiltX, float tiltY, boolean locked,
                       int batteryPct, boolean plugged, boolean reduced, ArchiveTimeline.FrameState frame,
                       String displayName, boolean phoneLayout) {
        if (!initialized || width <= 0 || height <= 0 || uiBitmap == null) return;
        if (animationEpoch == Long.MIN_VALUE || frameNanos < animationEpoch) animationEpoch = frameNanos;
        float time = reduced ? 0 : (float)((frameNanos-animationEpoch)*1e-9);
        boolean clock = prefs.getBoolean("archive_clock", true);
        long minute = System.currentTimeMillis() / 60000L;
        boolean darkPalette=frame.denyMix>.5f;
        boolean idlePopup=frame.idleBlack && darkPalette;
        if (lastUiMinute != minute || lastStage != frame.stage || lastSeverity != frame.intensity
                || lastLocked != locked || lastBattery != batteryPct || lastPlugged != plugged
                || lastClock != clock || lastNative != frame.nativeFailure || lastBiometricDemo != frame.biometricDemo
                || !displayName.equals(lastDisplayName) || lastPhone != phoneLayout || lastDarkPalette != darkPalette
                || lastIdlePopup != idlePopup) {
            uiBitmap.eraseColor(0);
            uiCanvas.save();
            uiCanvas.scale(uiWidth/(float)width,uiHeight/(float)height);
            overlay.draw(uiCanvas,width,height,frame.stage,frame.intensity,frame.nativeFailure,
                    device,batteryPct,plugged,locked,clock,frame.biometricDemo,displayName,phoneLayout,null,0,darkPalette);
            if (idlePopup) overlay.drawIdlePopup(uiCanvas,width,height,phoneLayout);
            uiCanvas.restore();
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, uiTexture);
            if (lastUiMinute == Long.MIN_VALUE) GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,uiBitmap,0);
            else GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D,0,0,0,uiBitmap);
            lastUiMinute=minute; lastStage=frame.stage; lastSeverity=frame.intensity;
            lastLocked=locked; lastBattery=batteryPct; lastPlugged=plugged; lastClock=clock;
            lastNative=frame.nativeFailure;
            lastBiometricDemo=frame.biometricDemo;
            lastDisplayName=displayName; lastPhone=phoneLayout; lastDarkPalette=darkPalette;
            lastIdlePopup=idlePopup;
        }

        GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(true);
        GLES30.glViewport(0,0,width,height);
        GLES30.glBindVertexArray(planeVao);
        if (frame.stage != ArchiveTimeline.HOME) {
            boolean recovering = frame.intensity == 0 && frame.recoveryGlitch > .005f;
            float glitch=reduced ? 0 : Math.max(frame.burst,frame.recoveryGlitch);
            boolean postProcess = glitch>.001f && blackout.begin(width,height);
            GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT);
            GLES30.glUseProgram(stageProgram);
            GLES30.glUniform2f(stageSize,width,height);
            GLES30.glUniform1f(stageDeny,frame.intensity);
            GLES30.glUniform1f(stageDenyMix,reduced ? (darkPalette ? 1 : 0) : frame.denyMix);
            GLES30.glUniform1f(stageSeconds,reduced ? 0 : frame.seconds);
            GLES30.glUniform1f(stageBirth,frame.birth);
            GLES30.glUniform1f(stageGlitch,glitch);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);
            if (frame.birth > 0) drawSculpture(time, reduced ? 0 : tiltX, reduced ? 0 : tiltY, frame, reduced, phoneLayout);
            if (postProcess) {
                blackout.present(width,height,planeVao,frame.seconds,frame.burst,glitch,recovering);
            }
            if (frame.foundation > 0 || frame.card > 0 || frame.telemetry > 0 || frame.caption > 0) drawHud(frame,reduced,phoneLayout);
            if (frame.stage>=ArchiveTimeline.IDLE && telemetry!=null)
                telemetryPass.draw(overlay,planeVao,width,height,phoneLayout,darkPalette,
                        telemetry,(frameNanos-animationEpoch)/1_000_000L,frame.telemetry);
        }
        if (frame.snapshot > .001f) drawSnapshot(time, frame, phoneLayout);
    }

    private void drawSculpture(float time, float tx, float ty, ArchiveTimeline.FrameState frame, boolean reduced, boolean phoneLayout) {
        Matrix.setIdentityM(model,0);
        Matrix.translateM(model,0,tx*.025f,-ty*.025f,0);
        Matrix.rotateM(model,0,16f+ty*9f+(float)Math.sin(time*.11f)*3,1,0,0);
        Matrix.rotateM(model,0,tx*12f+(float)Math.sin(time*.07f)*9-(1-frame.birth)*32,0,1,0);
        boolean portrait = height > width;
        Matrix.rotateM(model,0,portrait ? 43f : -2f,0,0,1);
        Matrix.multiplyMM(viewModel,0,view,0,model,0);
        Matrix.multiplyMM(mvp,0,projection,0,viewModel,0);
        int side = Math.min(width,height);
        int diameter = Math.round(side*(phoneLayout ? (portrait ? .73f : 1.05f) : (portrait ? .88f : 1.09f))*(1f+frame.peel*.13f));
        float cx = width*(phoneLayout ? (portrait ? .65f : .47f) : (portrait ? .52f : .44f)), cy = height*(phoneLayout ? (portrait ? .44f : .48f) : (portrait ? .46f : .495f));
        // Preserve the square projection's apparent framing without clipping to that square.
        float xScale = diameter / (float)width, yScale = diameter / (float)height;
        float xOffset = 2*cx/width-1, yOffset = 1-2*cy/height;
        for (int i=0; i<16; i+=4) {
            mvp[i] = mvp[i]*xScale + mvp[i+3]*xOffset;
            mvp[i+1] = mvp[i+1]*yScale + mvp[i+3]*yOffset;
        }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        GLES30.glUseProgram(sculptureProgram);
        GLES30.glBindVertexArray(vao);
        GLES30.glUniformMatrix4fv(mvpLocation,1,false,mvp,0);
        GLES30.glUniformMatrix4fv(modelLocation,1,false,model,0);
        GLES30.glUniform1f(timeLocation,time);
        GLES30.glUniform1f(alertLocation,reduced ? (frame.denyMix>.5f ? 1 : 0) : frame.denyMix);
        GLES30.glUniform1f(pulseLocation,reduced ? 0 : frame.pulse);
        GLES30.glUniform1f(birthLocation,frame.birth);
        GLES30.glUniform1f(verifyLocation,frame.scan);
        GLES30.glUniform1f(peelLocation,frame.peel);
        GLES30.glUniform1f(disorderLocation,reduced ? 0 : frame.disorder);
        GLES30.glUniform1f(idleBlackLocation,frame.idleBlack ? 1 : 0);
        GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES,indexCount,GLES30.GL_UNSIGNED_INT,0,SculptureScene.FIN_COUNT);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
    }

    private void drawHud(ArchiveTimeline.FrameState frame, boolean reduced, boolean phoneLayout) {
        GLES30.glUseProgram(hudProgram);
        GLES30.glBindVertexArray(planeVao);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,uiTexture);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_ONE,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glUniform1i(hudTexture,0);
        GLES30.glUniform1f(hudPhone,phoneLayout?1:0);
        GLES30.glUniform2f(hudSize,width,height);
        GLES30.glUniform4f(hudPhases,frame.foundation,frame.card,frame.telemetry,frame.caption);
        GLES30.glUniform1f(hudScan,frame.scan);
        GLES30.glUniform1f(hudScanHead,frame.scanHead);
        GLES30.glUniform1f(hudScanOpacity,frame.scanOpacity);
        GLES30.glUniform1f(hudScanSeconds,reduced ? 0 : frame.seconds);
        GLES30.glUniform1f(hudWarningAge,frame.intensity==0 ? -1 : (reduced ? 2 : frame.seconds));
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);
        GLES30.glDisable(GLES30.GL_BLEND);
    }

    private void drawSnapshot(float time, ArchiveTimeline.FrameState frame, boolean phoneLayout) {
        // The synthetic snapshot contains only the theme's gallery.
        String style = prefs.getString("home_scene", CarbonHomeRenderer.STYLE);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_ONE,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        snapshot.drawSnapshot(width,height,time,frame,"dark_gallery".equals(style) || CarbonHomeRenderer.STYLE.equals(style),phoneLayout);
        GLES30.glDisable(GLES30.GL_BLEND);
    }

    public void release() {
        initialized=false;
        blackout.release();
        telemetryPass.release();
        snapshot.release(true);
        if (sculptureProgram!=0) GLES30.glDeleteProgram(sculptureProgram);
        if (stageProgram!=0) GLES30.glDeleteProgram(stageProgram);
        if (hudProgram!=0) GLES30.glDeleteProgram(hudProgram);
        if(vbo!=0||ebo!=0) { names[0]=vbo; names[1]=ebo; GLES30.glDeleteBuffers(2,names,0); }
        if(vao!=0||planeVao!=0) { names[0]=vao; names[1]=planeVao; GLES30.glDeleteVertexArrays(2,names,0); }
        if(uiTexture!=0) { names[0]=uiTexture; GLES30.glDeleteTextures(1,names,0); }
        sculptureProgram=stageProgram=hudProgram=vao=vbo=ebo=planeVao=uiTexture=0;
        if(uiBitmap!=null) { uiBitmap.recycle(); uiBitmap=null; uiCanvas=null; }
        lastUiMinute=Long.MIN_VALUE;
    }

    private static int location(int program, String name) {
        return GLES30.glGetUniformLocation(program,name);
    }

    private static int compileShader(int type,String source) {
        int shader=GLES30.glCreateShader(type);
        if(shader==0) throw new IllegalStateException("Unable to create GLES shader");
        GLES30.glShaderSource(shader,source);
        GLES30.glCompileShader(shader);
        int[] status=new int[1];
        GLES30.glGetShaderiv(shader,GLES30.GL_COMPILE_STATUS,status,0);
        if(status[0]==0) {
            String log=GLES30.glGetShaderInfoLog(shader);
            GLES30.glDeleteShader(shader);
            throw new IllegalStateException("Sculpture shader compile failed: "+log);
        }
        return shader;
    }

    static int linkProgram(String vertexSource,String fragmentSource) {
        int vertex=compileShader(GLES30.GL_VERTEX_SHADER,vertexSource);
        int fragment=0,program=0;
        try {
            fragment=compileShader(GLES30.GL_FRAGMENT_SHADER,fragmentSource);
            program=GLES30.glCreateProgram();
            GLES30.glAttachShader(program,vertex); GLES30.glAttachShader(program,fragment);
            GLES30.glLinkProgram(program);
            int[] status=new int[1];
            GLES30.glGetProgramiv(program,GLES30.GL_LINK_STATUS,status,0);
            if(status[0]==0) throw new IllegalStateException("Sculpture program link failed: "+GLES30.glGetProgramInfoLog(program));
            return program;
        } catch(RuntimeException failure) {
            if(program!=0) GLES30.glDeleteProgram(program);
            throw failure;
        } finally {
            GLES30.glDeleteShader(vertex);
            if(fragment!=0) GLES30.glDeleteShader(fragment);
        }
    }
}
