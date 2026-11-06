package dev.rhine.terminal;

import android.content.Context;
import android.util.Log;

import static android.opengl.GLES30.*;

/** Graphite specimen gallery with an independent local data stream. */
final class CarbonHomeRenderer {
    static final String STYLE = "carbon_console";
    private final Context context;
    private final SpecimenScenePass gallery = new SpecimenScenePass();
    private final HomeConsoleLayer console;
    private HomeDeviceTelemetry telemetry;
    private String[] lastSnapshot;
    private int frames;
    private long fpsStart, start;

    CarbonHomeRenderer(Context context) {
        this.context = context;
        console = new HomeConsoleLayer(context);
    }

    void initialize() {
        try {
            gallery.initialize(context);
            console.initialize();
            int error=glGetError();
            if (error!=GL_NO_ERROR) throw new IllegalStateException("Carbon GLES initialization: "+error);
            telemetry=new HomeDeviceTelemetry(context);
            Log.i("RhineGL","Carbon console ready: upstream card, GPU="+glGetString(GL_RENDERER));
        } catch (RuntimeException failure) {
            release(true);
            throw new IllegalStateException("Could not initialize carbon console",failure);
        }
    }

    void render(long now,int width,int height,boolean reduced,float reveal) {
        if (start==0) start=now;
        telemetry.setActive(true);
        String[] snapshot=telemetry.latest();
        if (snapshot!=lastSnapshot) {
            lastSnapshot=snapshot;
            console.submit(snapshot);
        }
        glDisable(GL_BLEND);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        gallery.draw(0,0,width,height,reduced ? 0 : (now-start)*1e-9f,reveal,true);
        console.render(now,width,height,reduced);
    }

    void frameSubmitted(long now) {
        if (fpsStart==0) {
            fpsStart=now;
            frames=0;
        } else {
            frames++;
            long elapsed=now-fpsStart;
            if (elapsed>=1_000_000_000L) {
                telemetry.setSubmittedFps(frames*1_000_000_000f/elapsed);
                fpsStart=now;
                frames=0;
            }
        }
    }

    void pause() {
        if (telemetry==null) return;
        telemetry.setActive(false);
        telemetry.setSubmittedFps(Float.NaN);
        fpsStart=0;
        frames=0;
    }

    void release(boolean glAvailable) {
        if (telemetry!=null) telemetry.close();
        telemetry=null;
        if (glAvailable) console.release();
        gallery.release(glAvailable);
    }
}
