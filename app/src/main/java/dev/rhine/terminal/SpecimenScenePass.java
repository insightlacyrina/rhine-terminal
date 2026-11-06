package dev.rhine.terminal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLUtils;
import java.io.IOException;
import java.io.InputStream;

import static android.opengl.GLES30.*;

/** Shared gallery draw for both wallpapers and the lock's desktop transition. */
final class SpecimenScenePass {
    private int program, vao, texture, sizeLocation, timeLocation, revealLocation, darkLocation;
    private int flightLocation, desatLocation, phoneLocation;

    void initialize(Context context) {
        initialize(context, false);
    }

    void initialize(Context context, boolean snapshot) {
        program = GpuTerminalRenderer.linkProgram(
                snapshot ? LockSnapshotScene.VERTEX_SHADER : LightHomeScene.VERTEX_SHADER,
                snapshot ? LockSnapshotScene.FRAGMENT_SHADER : LightHomeScene.FRAGMENT_SHADER);
        sizeLocation = glGetUniformLocation(program, "uSize");
        timeLocation = glGetUniformLocation(program, "uTime");
        revealLocation = glGetUniformLocation(program, "uReveal");
        darkLocation = glGetUniformLocation(program, "uDark");
        phoneLocation = glGetUniformLocation(program, "uPhone");
        if (snapshot) {
            flightLocation = glGetUniformLocation(program, "uFlight");
            desatLocation = glGetUniformLocation(program, "uDesat");
        }
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "uCardTex"), 0);
        int[] names = new int[1];
        glGenVertexArrays(1, names, 0);
        vao = names[0];
        glGenTextures(1, names, 0);
        texture = names[0];
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        try (InputStream asset = context.getAssets().open("rhine-lock/scene/card.png")) {
            Bitmap card = BitmapFactory.decodeStream(asset);
            if (card == null) throw new IOException("Cannot decode the upstream specimen artwork");
            try {
                GLUtils.texImage2D(GL_TEXTURE_2D, 0, card, 0);
                glGenerateMipmap(GL_TEXTURE_2D);
            } finally {
                card.recycle();
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load the upstream specimen artwork", failure);
        }
    }

    void draw(int x, int y, int width, int height, float time, float reveal, boolean dark, boolean phoneLayout) {
        bind(x, y, width, height, time, reveal, dark, phoneLayout);
        finish();
    }

    void drawSnapshot(int width, int height, float time, ArchiveTimeline.FrameState frame, boolean dark, boolean phoneLayout) {
        bind(0, 0, width, height, time, 1, dark, phoneLayout);
        glUniform4f(flightLocation, frame.snapshotTravel, frame.snapshot, frame.snapshotScan, frame.snapshotCorners);
        glUniform1f(desatLocation, frame.snapshotDesat);
        finish();
    }

    private void bind(int x, int y, int width, int height, float time, float reveal, boolean dark, boolean phoneLayout) {
        glViewport(x, y, width, height);
        glUseProgram(program);
        glBindVertexArray(vao);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glUniform2f(sizeLocation, width, height);
        glUniform1f(timeLocation, time);
        glUniform1f(revealLocation, reveal);
        glUniform1f(darkLocation, dark ? 1f : 0f);
        glUniform1f(phoneLocation, phoneLayout ? 1f : 0f);
    }

    private void finish() {
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
    }

    void release(boolean glAvailable) {
        if (glAvailable) {
            if (program != 0) glDeleteProgram(program);
            if (vao != 0) glDeleteVertexArrays(1, new int[]{vao}, 0);
            if (texture != 0) glDeleteTextures(1, new int[]{texture}, 0);
        }
        program = vao = texture = 0;
    }
}
