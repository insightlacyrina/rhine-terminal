package dev.rhine.terminal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import static android.opengl.GLES30.*;

/** Bounded in-memory console; all methods run on the owning EGL thread. */
final class HomeConsoleLayer {
    private static final int HISTORY=128, PENDING=256, MAX_GLYPHS=7000;
    private static final int AW=512, AH=512, CW=32, CH=48;
    private static final long STEP=62_500_000L;
    private static final String VS="#version 300 es\nlayout(location=0) in vec4 rect; layout(location=1) in vec4 tex; out vec2 uv; uniform float offsetY; void main(){int id=gl_VertexID; vec2 c=vec2((id==2||id==3||id==5)?1.:0.,(id==1||id==4||id==5)?1.:0.); vec2 p=mix(rect.xy,rect.zw,c); uv=mix(tex.xy,tex.zw,c); gl_Position=vec4(p.x,p.y+offsetY,0.,1.);}";
    private static final String FS="#version 300 es\nprecision mediump float; in vec2 uv; uniform sampler2D atlas; uniform vec4 color; uniform float solid; uniform vec4 bounds; uniform float radius; uniform float ring; out vec4 outColor; void main(){if(solid>0.5){vec2 q=abs(gl_FragCoord.xy-(bounds.xy+bounds.zw)*0.5)-(bounds.zw-bounds.xy)*0.5+radius; float d=length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius; if(d>0.0||(ring>0.0&&d< -ring))discard;} float a=mix(texture(atlas,uv).a,1.,solid);outColor=vec4(color.rgb,color.a*a);}";
    private final String[] history=new String[HISTORY], pending=new String[PENDING];
    private final FloatBuffer data=ByteBuffer.allocateDirect((MAX_GLYPHS+256)*8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private int hs,hc,ps,pc,program,vao,vbo,atlas,color,solid,offsetY,bounds,radius,ring;
    private int layoutWidth,layoutHeight,bodyStart,bodyCount,promptStart,promptCount,headerCount;
    private int scissorX,scissorY,scissorWidth,scissorHeight;
    private float lineHeight,panelLeft,panelRight,panelTop,panelBottom,panelBorder,panelRadius;
    private boolean geometryDirty=true;
    private long nextLine,lastArrival;
    private final Typeface consoleFace;
    HomeConsoleLayer() { this(null); }
    HomeConsoleLayer(Context context) {
        Typeface face=Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL);
        if(context!=null) {
            try { face=Typeface.createFromAsset(context.getAssets(),"rhine-lock/fonts/JetBrainsMono-Regular.ttf"); }
            catch(RuntimeException ignored) { }
        }
        consoleFace=face;
    }

    void initialize() {
        program=GpuTerminalRenderer.linkProgram(VS,FS);
        color=glGetUniformLocation(program,"color"); solid=glGetUniformLocation(program,"solid"); offsetY=glGetUniformLocation(program,"offsetY"); bounds=glGetUniformLocation(program,"bounds"); radius=glGetUniformLocation(program,"radius"); ring=glGetUniformLocation(program,"ring");
        int[] ids=new int[1];
        glGenVertexArrays(1,ids,0); vao=ids[0]; glGenBuffers(1,ids,0); vbo=ids[0];
        glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glEnableVertexAttribArray(0); glEnableVertexAttribArray(1);
        glVertexAttribDivisor(0,1); glVertexAttribDivisor(1,1);
        glGenTextures(1,ids,0); atlas=ids[0]; glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,atlas);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        Bitmap bitmap=Bitmap.createBitmap(AW,AH,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG); paint.setColor(Color.WHITE);
        paint.setTypeface(consoleFace); paint.setTextSize(29f);
        for(int c=32;c<=126;c++){int i=c-32;canvas.drawText(String.valueOf((char)c),(i%16)*CW+2,(i/16)*CH+34,paint);}
        GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0); bitmap.recycle();
        glUseProgram(program); glUniform1i(glGetUniformLocation(program,"atlas"),0); glBindVertexArray(0);
    }
    /** Queue real observations. Oldest waiting observations are dropped if full. */
    void submit(String[] lines) {
        if(lines==null)return;
        for(String line:lines){
            if(line==null||line.length()==0)continue;
            if(pc==PENDING){pending[ps]=null;ps=(ps+1)%PENDING;pc--;}
            pending[(ps+pc++)%PENDING]=line;
        }
    }
    private void advance(long now,boolean reduced){
        if(pc==0){nextLine=0;return;}
        if(nextLine==0)nextLine=now;
        int limit=reduced?PENDING:8;
        while(pc>0&&(reduced||now>=nextLine)&&limit-->0){
            String line=pending[ps];pending[ps]=null;ps=(ps+1)%PENDING;pc--;
            if(hc==HISTORY){history[hs]=null;hs=(hs+1)%HISTORY;hc--;}
            history[(hs+hc++)%HISTORY]=line;lastArrival=reduced?0:now;nextLine+=STEP;geometryDirty=true;
        }
        if(pc==0)nextLine=0;else if(!reduced&&now-nextLine>STEP*8)nextLine=now+STEP;
    }
    void render(long frameNanos,int width,int height,boolean reducedMotion){
        if(program==0||width<=0||height<=0)return;
        advance(frameNanos,reducedMotion);
        glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,atlas);
        if(geometryDirty||width!=layoutWidth||height!=layoutHeight) rebuildGeometry(width,height);
        glUniform1f(offsetY,0f);glUniform1f(solid,1f);
        glUniform4f(bounds,panelLeft,height-panelBottom,panelRight,height-panelTop);glUniform1f(radius,panelRadius);glUniform1f(ring,panelBorder);
        glUniform4f(color,.21f,.27f,.19f,.75f);drawRange(0,1);
        glUniform4f(bounds,panelLeft+panelBorder,height-panelBottom+panelBorder,panelRight-panelBorder,height-panelTop-panelBorder);
        glUniform1f(radius,Math.max(0f,panelRadius-panelBorder));glUniform1f(ring,0f);
        glUniform4f(color,.027f,.064f,.041f,.20f);drawRange(1,1);
        glUniform1f(solid,0f);glUniform4f(color,.62f,.71f,.56f,.85f);drawRange(2,headerCount);
        glEnable(GL_SCISSOR_TEST);glScissor(scissorX,scissorY,scissorWidth,scissorHeight);
        float shift=0f;
        if(!reducedMotion&&lastArrival!=0){float t=Math.max(0f,Math.min(1f,(frameNanos-lastArrival)/(float)STEP));shift=lineHeight*(1f-t);}
        glUniform1f(offsetY,-2f*shift/height);
        glUniform4f(color,.73f,.80f,.70f,.92f);drawRange(bodyStart,bodyCount);
        glUniform4f(color,.76f,.84f,.45f,.98f);drawRange(promptStart,promptCount);
        glDisable(GL_SCISSOR_TEST);glDisable(GL_BLEND);glBindVertexArray(0);
    }

    /** Regenerate only for incoming rows and viewport changes, never for motion. */
    private void rebuildGeometry(int width,int height){
        layoutWidth=width;layoutHeight=height;geometryDirty=false;
        boolean landscape=width>=height;
        float left=width*(landscape?.04f:.05f),right=width*(landscape?.50f:.95f);
        float top=height*(landscape?.135f:.46f),bottom=height*.90f;
        float fh=Math.max(9f,Math.min(24f,height*(landscape?.016f:.012f)));
        float charWidth=fh*.62f,padding=Math.max(8f,fh*1.3f), headerHeight=fh*2.2f;
        lineHeight=fh*1.2f;
        float border=Math.max(1f,height*.001f);
        panelLeft=left;panelRight=right;panelTop=top;panelBottom=bottom;panelBorder=border;panelRadius=Math.max(3f,fh*.55f);
        scissorX=Math.max(0,(int)(left+padding));scissorY=Math.max(0,(int)(height-bottom+padding));
        scissorWidth=Math.max(0,(int)(right-left-2*padding));scissorHeight=Math.max(0,(int)(bottom-top-headerHeight-padding));
        data.clear();
        quad(left,top,right,bottom,0,0,0,0,width,height);
        quad(left+border,top+border,right-border,bottom-border,0,0,0,0,width,height);
        String header="RHINE LAB / DEVICE CONSOLE   LOCAL";
        int cols=Math.max(0,(int)((right-left-2*padding)/charWidth));
        int headerLength=Math.min(header.length(),cols);
        for(int i=0;i<headerLength;i++) glyph(header.charAt(i),left+padding+i*charWidth,top+padding*.75f,charWidth,fh,width,height);
        headerCount=headerLength;
        int visible=Math.min(hc,(int)((bottom-top-headerHeight-padding)/lineHeight)+2);
        int total=0;
        bodyStart=2+headerCount;
        for(int pass=0;pass<2;pass++){
            int count=0;
            for(int n=0;n<visible;n++){
                String line=history[(hs+hc-1-n+HISTORY)%HISTORY];
                boolean prompt=line.charAt(0)=='$'||line.charAt(0)=='>'||line.charAt(0)=='#';
                if(prompt!=(pass==1))continue;
                float y=bottom-padding-fh-n*lineHeight;int length=Math.min(line.length(),cols);
                for(int j=0;j<length&&total<MAX_GLYPHS;j++){
                    glyph(line.charAt(j),left+padding+j*charWidth,y,charWidth,fh,width,height);total++;count++;
                }
            }
            if(pass==0){bodyCount=count;promptStart=bodyStart+bodyCount;}
            else promptCount=count;
        }
        upload();
    }
    private void glyph(char ch,float x,float y,float charWidth,float fh,int width,int height){
        if(ch<32||ch>126)ch='?';int c=ch-32;
        float u=(c%16)*CW/(float)AW,v=(c/16)*CH/(float)AH;
        quad(x,y,x+charWidth*1.6f,y+fh*1.6f,u,v,u+CW/(float)AW,v+CH/(float)AH,width,height);
    }
    private void upload(){
        data.flip();
        // Re-specification provides fresh driver storage instead of stalling on an in-flight draw.
        glBufferData(GL_ARRAY_BUFFER,data.remaining()*4,data,GL_STREAM_DRAW);
    }
    private void drawRange(int start,int count){
        if(count==0)return;
        int byteOffset=start*8*4;
        glVertexAttribPointer(0,4,GL_FLOAT,false,8*4,byteOffset);
        glVertexAttribPointer(1,4,GL_FLOAT,false,8*4,byteOffset+4*4);
        glDrawArraysInstanced(GL_TRIANGLES,0,6,count);
    }
    private void quad(float x0,float y0,float x1,float y1,float u0,float v0,float u1,float v1,int w,int h){
        float a=2f*x0/w-1f,b=1f-2f*y0/h,c=2f*x1/w-1f,d=1f-2f*y1/h;
        data.put(a).put(b).put(c).put(d).put(u0).put(v0).put(u1).put(v1);
    }
    void release(){int[] ids=new int[1];if(atlas!=0){ids[0]=atlas;glDeleteTextures(1,ids,0);atlas=0;}if(vbo!=0){ids[0]=vbo;glDeleteBuffers(1,ids,0);vbo=0;}if(vao!=0){ids[0]=vao;glDeleteVertexArrays(1,ids,0);vao=0;}if(program!=0){glDeleteProgram(program);program=0;}}
}
