package dev.rhine.terminal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import static android.opengl.GLES30.*;

/** Draws the home scene on the EGL thread. The telemetry texture changes only with system state. */
final class HomeWallpaperRenderer {
    static final int BASE_COLOR = 0xff0b0e0b;
    static final long UNFOLD_NANOS = 1_150_000_000L;
    private static final double SCULPTURE_PERIOD = Math.PI * 200.0;
    private static final long TELEMETRY_INTERVAL = 2_000_000_000L;
    private final Context context;
    private final PowerManager power;
    private CarbonHomeRenderer carbon;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private Bitmap textBitmap;
    private Canvas textCanvas;
    private int background, sculpture, backgroundVao, sculptureVao, vertices, indices, telemetry;
    private int backgroundSize, backgroundTime, backgroundReveal, telemetrySampler, telemetryOn, backgroundInsets;
    private final SpecimenScenePass gallery = new SpecimenScenePass();
    private int sculptureSize, sculptureTime, sculptureReveal, sculptureTilt, sculptureInsets, indexCount;
    private long firstFrame, lastTelemetry;
    private int lastBattery = Integer.MIN_VALUE, lastThermal = Integer.MIN_VALUE;
    private boolean lastPlugged, lastSaver;

    HomeWallpaperRenderer(Context context) {
        this.context = context;
        power = context.getSystemService(PowerManager.class);
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        text.setColor(Color.WHITE);
    }

    void initialize(boolean carbonConsole) {
        if (carbonConsole) {
            carbon = new CarbonHomeRenderer(context);
            carbon.initialize();
        } else {
            initializeGallery();
        }
    }

    private void initializeGallery() {
        try {
            gallery.initialize(context);
            background = GpuTerminalRenderer.linkProgram(HomeBackgroundScene.VERTEX_SHADER, HomeBackgroundScene.FRAGMENT_SHADER);
            sculpture = GpuTerminalRenderer.linkProgram(HomeSculptureScene.VERTEX_SHADER, HomeSculptureScene.FRAGMENT_SHADER);
            backgroundSize = glGetUniformLocation(background, "uSize");
            backgroundInsets = glGetUniformLocation(background, "uEdgeInsets");
            backgroundTime = glGetUniformLocation(background, "uTime");
            backgroundReveal = glGetUniformLocation(background, "uReveal");
            telemetrySampler = glGetUniformLocation(background, "uTelemetry");
            telemetryOn = glGetUniformLocation(background, "uTelemetryOn");
            sculptureSize = glGetUniformLocation(sculpture, "uSize");
            sculptureInsets = glGetUniformLocation(sculpture, "uEdgeInsets");
            sculptureTime = glGetUniformLocation(sculpture, "uTime");
            sculptureReveal = glGetUniformLocation(sculpture, "uReveal");
            sculptureTilt = glGetUniformLocation(sculpture, "uTilt");

            int[] ids = new int[2];
            glGenVertexArrays(2, ids, 0);
            backgroundVao = ids[0];
            sculptureVao = ids[1];
            glGenBuffers(2, ids, 0);
            vertices = ids[0];
            indices = ids[1];
            HomeSculptureScene.Mesh mesh = HomeSculptureScene.createMesh();
            indexCount = mesh.indexCount;
            glBindVertexArray(sculptureVao);
            glBindBuffer(GL_ARRAY_BUFFER, vertices);
            glBufferData(GL_ARRAY_BUFFER, mesh.vertices.remaining() * Float.BYTES, mesh.vertices, GL_STATIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indices);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, mesh.indices.remaining() * Integer.BYTES, mesh.indices, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 4, GL_FLOAT, false, HomeSculptureScene.STRIDE_BYTES, 0);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, HomeSculptureScene.STRIDE_BYTES, 4 * Float.BYTES);
            glBindVertexArray(0);

            glGenTextures(1, ids, 0);
            telemetry = ids[0];
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, telemetry);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            textBitmap = Bitmap.createBitmap(1024, 40, Bitmap.Config.ARGB_8888);
            textCanvas = new Canvas(textBitmap);
            GLUtils.texImage2D(GL_TEXTURE_2D, 0, textBitmap, 0);
            int error = glGetError();
            if (error != GL_NO_ERROR) throw new IllegalStateException("Home GLES initialization: " + error);
            Log.i("RhineGL", "Home renderer ready, triangles=" + indexCount / 3 + ", GPU=" + glGetString(GL_RENDERER));
        } catch (RuntimeException error) {
            release(true);
            throw error;
        }
    }

    void render(long frame, int width, int height, long unfoldStart, boolean fromVisible, boolean reduced,
                boolean showTelemetry, boolean lightHome, boolean darkGallery, boolean carbonConsole, int battery, boolean plugged, boolean phoneLayout) {
        if (firstFrame == 0) firstFrame = frame;
        double elapsed = reduced ? 0 : Math.max(0, frame - firstFrame) * 1e-9;
        float reveal = 1f;
        if (!reduced && unfoldStart != 0) {
            float t = (frame - unfoldStart) / (float) UNFOLD_NANOS;
            reveal = ArchiveTimeline.homeReveal(t, fromVisible);
        }

        if (carbonConsole) {
            if (carbon == null) {
                release(true);
                initialize(true);
            }
            carbon.render(frame,width,height,reduced,reveal,phoneLayout);
            return;
        }
        if (carbon != null) {
            release(true);
            initializeGallery();
        }

        glViewport(0, 0, width, height);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(true);
        if (lightHome || darkGallery) {
            gallery.draw(0,0,width,height,(float)elapsed,reveal,darkGallery,phoneLayout);
            return;
        }
        glClear(GL_DEPTH_BUFFER_BIT);
        boolean portrait = height > width;
        float sideInset = phoneLayout ? (portrait ? .02f : .04f) : 0;
        float topInset = phoneLayout ? (portrait ? .083f : .075f) : 0;
        float bottomInset = phoneLayout ? (portrait ? .115f : .09f) : 0;
        glUseProgram(background);
        glBindVertexArray(backgroundVao);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, telemetry);
        if (showTelemetry) updateTelemetry(frame, battery, plugged);
        glUniform2f(backgroundSize, width, height);
        glUniform4f(backgroundInsets, sideInset, topInset, sideInset, bottomInset);
        glUniform1f(backgroundTime, (float) (elapsed % 120.0));
        glUniform1f(backgroundReveal, reveal);
        glUniform1i(telemetrySampler, 0);
        glUniform1f(telemetryOn, showTelemetry ? 1f : 0f);
        glDrawArrays(GL_TRIANGLES, 0, 3);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glUseProgram(sculpture);
        glBindVertexArray(sculptureVao);
        glUniform2f(sculptureSize, width, height);
        glUniform4f(sculptureInsets, sideInset, topInset, sideInset, bottomInset);
        glUniform1f(sculptureTime, (float) (elapsed % SCULPTURE_PERIOD));
        glUniform1f(sculptureReveal, reveal);
        glUniform2f(sculptureTilt, 0f, 0f);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        glDisable(GL_DEPTH_TEST);
    }

    private void updateTelemetry(long frame, int battery, boolean plugged) {
        if (lastTelemetry != 0 && frame - lastTelemetry < TELEMETRY_INTERVAL
                && battery == lastBattery && plugged == lastPlugged) return;
        lastTelemetry = frame;
        boolean saver = power != null && power.isPowerSaveMode();
        int thermal = Build.VERSION.SDK_INT >= 29 && power != null ? power.getCurrentThermalStatus() : -1;
        if (battery == lastBattery && plugged == lastPlugged && saver == lastSaver && thermal == lastThermal) return;
        lastBattery = battery;
        lastPlugged = plugged;
        lastSaver = saver;
        lastThermal = thermal;
        String level = battery < 0 ? "UNAVAILABLE" : battery + "%";
        String supply = battery < 0 ? "UNAVAILABLE" : plugged ? "EXTERNAL" : "BATTERY";
        String line = "RHINE LAB    /    BATTERY " + level + "    /    SUPPLY " + supply
                + "    /    THERMAL " + thermalText(thermal) + "    /    MODE " + (saver ? "SAVER" : "STANDARD");
        textBitmap.eraseColor(Color.TRANSPARENT);
        text.setTextSize(18f);
        float measured = text.measureText(line);
        if (measured > 986f) text.setTextSize(18f * 986f / measured);
        textCanvas.drawText(line, 12f, 20f - (text.ascent() + text.descent()) / 2f, text);
        GLUtils.texSubImage2D(GL_TEXTURE_2D, 0, 0, 0, textBitmap);
    }

    private static String thermalText(int thermal) {
        switch (thermal) {
            case 0: return "NORMAL";
            case 1: return "LIGHT";
            case 2: return "MODERATE";
            case 3: return "SEVERE";
            case 4: return "CRITICAL";
            case 5: return "EMERGENCY";
            case 6: return "SHUTDOWN";
            default: return "UNAVAILABLE";
        }
    }

    static void drawFallback(Canvas canvas, Paint paint, boolean phoneLayout) {
        canvas.drawColor(BASE_COLOR);
        float edge = Math.min(canvas.getWidth(), canvas.getHeight()) * .065f;
        float side = phoneLayout ? canvas.getWidth() * .03f : 0;
        float top = phoneLayout ? canvas.getHeight() * .08f : 0;
        float bottom = phoneLayout ? canvas.getHeight() * .11f : 0;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, edge * .12f));
        paint.setColor(0xff252b1b);
        canvas.drawRoundRect(edge + side, edge + top, canvas.getWidth() - edge - side, canvas.getHeight() - edge - bottom,
                edge, edge, paint);
    }

    void pause() { if (carbon != null) carbon.pause(); }

    void frameSubmitted(long now) { if (carbon != null) carbon.frameSubmitted(now); }

    void release(boolean glAvailable) {
        if (carbon != null) carbon.release(glAvailable);
        carbon = null;
        gallery.release(glAvailable);
        if (glAvailable) {
            if (background != 0) glDeleteProgram(background);
            if (sculpture != 0) glDeleteProgram(sculpture);
            glDeleteVertexArrays(2, new int[]{backgroundVao, sculptureVao}, 0);
            glDeleteBuffers(2, new int[]{vertices, indices}, 0);
            glDeleteTextures(1, new int[]{telemetry}, 0);
        }
        if (textBitmap != null) textBitmap.recycle();
        textCanvas = null;
        textBitmap = null;
        background = sculpture = backgroundVao = sculptureVao = vertices = indices = telemetry = 0;
    }
}
