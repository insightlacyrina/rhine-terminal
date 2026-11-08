package dev.rhine.terminal;

import static android.opengl.GLES30.*;

/** Processes the stage and sculpture together, before compositing the readable archive HUD. */
final class LockBlackoutPass {
    private static final int MAX_EDGE = 1664;
    private int program, framebuffer, color, depth, bufferWidth, bufferHeight;
    private int sizeLocation, secondsLocation, burstLocation, severityLocation;

    boolean begin(int width,int height) {
        float scale=Math.min(1,MAX_EDGE/(float)Math.max(width,height));
        int w=Math.max(1,Math.round(width*scale)), h=Math.max(1,Math.round(height*scale));
        if (program==0) {
            program=GpuTerminalRenderer.linkProgram(LockComposition.VERTEX,LockBlackoutScene.FRAGMENT_SHADER);
            sizeLocation=glGetUniformLocation(program,"uSize");
            secondsLocation=glGetUniformLocation(program,"uSeconds");
            burstLocation=glGetUniformLocation(program,"uBurst");
            severityLocation=glGetUniformLocation(program,"uSeverity");
            glUseProgram(program);
            glUniform1i(glGetUniformLocation(program,"uScene"),0);
        }
        if (framebuffer==0 || bufferWidth!=w || bufferHeight!=h) {
            releaseBuffer();
            int[] name=new int[1];
            glGenFramebuffers(1,name,0); framebuffer=name[0];
            glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
            glGenTextures(1,name,0); color=name[0];
            glBindTexture(GL_TEXTURE_2D,color);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,null);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);
            glGenRenderbuffers(1,name,0); depth=name[0];
            glBindRenderbuffer(GL_RENDERBUFFER,depth);
            glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT16,w,h);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depth);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) {
                glBindFramebuffer(GL_FRAMEBUFFER,0);
                releaseBuffer();
                return false;
            }
            bufferWidth=w;
            bufferHeight=h;
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
        }
        glViewport(0,0,w,h);
        return true;
    }

    void present(int width,int height,int vao,float seconds,float burst,float strength,boolean recovering) {
        glBindFramebuffer(GL_FRAMEBUFFER,0);
        glViewport(0,0,width,height);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glUseProgram(program);
        glBindVertexArray(vao);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D,color);
        glUniform2f(sizeLocation,width,height);
        glUniform1f(secondsLocation,seconds);
        glUniform1f(burstLocation,0);
        glUniform1f(severityLocation,Math.max(0,Math.min(1,strength)));
        glDrawArrays(GL_TRIANGLES,0,3);
    }

    private void releaseBuffer() {
        if (framebuffer!=0) glDeleteFramebuffers(1,new int[]{framebuffer},0);
        if (color!=0) glDeleteTextures(1,new int[]{color},0);
        if (depth!=0) glDeleteRenderbuffers(1,new int[]{depth},0);
        framebuffer=color=depth=bufferWidth=bufferHeight=0;
    }

    void release() {
        releaseBuffer();
        if (program!=0) glDeleteProgram(program);
        program=0;
    }
}
