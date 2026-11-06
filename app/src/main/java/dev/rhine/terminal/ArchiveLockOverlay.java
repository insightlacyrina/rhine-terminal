package dev.rhine.terminal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;

/** A transparent, procedural archive layer. It never implements authentication. */
public final class ArchiveLockOverlay {
    public static final class DeviceState {
        public final String deviceLabel;
        public final int batteryPercent;
        public final boolean charging;
        public final boolean locked;
        public DeviceState(String deviceLabel, int batteryPercent, boolean charging, boolean locked) {
            this.deviceLabel = deviceLabel == null || deviceLabel.trim().isEmpty() ? "ANDROID TABLET" : deviceLabel;
            this.batteryPercent = batteryPercent;
            this.charging = charging;
            this.locked = locked;
        }
    }
    private static final int INK=0xff171a17, PAPER=0xfff4efec, MUTED=0xff737570;
    private static final int LIME=0xffc3fa3d, ORANGE=0xfff26e12, COPPER=0xffa85d37;
    private final Paint P=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface LIGHT, BOLD, MONO;
    private final Bitmap smoke, hex;
    private final BitmapShader hexShader;
    public ArchiveLockOverlay() { this(null); }
    public ArchiveLockOverlay(Context context) {
        LIGHT=font(context,"Montserrat-Light.ttf",Typeface.create("sans-serif-light",Typeface.NORMAL));
        BOLD=font(context,"Montserrat-Bold.ttf",Typeface.create("sans-serif",Typeface.BOLD));
        MONO=font(context,"JetBrainsMono-Regular.ttf",Typeface.create("monospace",Typeface.NORMAL));
        smoke=bitmap(context,"smoke.jpg"); hex=bitmap(context,"hex.png");
        hexShader=hex==null?null:new BitmapShader(hex,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT);
    }
    private static Typeface font(Context context,String name,Typeface fallback) {
        if(context==null)return fallback;
        try { return Typeface.createFromAsset(context.getAssets(),"rhine-lock/fonts/"+name); }
        catch(RuntimeException e) { return fallback; }
    }
    private static Bitmap bitmap(Context context,String name) {
        if(context==null)return null;
        try(java.io.InputStream in=context.getAssets().open("rhine-lock/scene/"+name)) {
            return BitmapFactory.decodeStream(in);
        } catch(java.io.IOException | RuntimeException e) { return null; }
    }

    /** Render over the scene on one render thread. Stage and timing come from the caller. */
    public void draw(Canvas c, float width, float height, int stage, int level, boolean nativeFailure, String device, int batteryPct, boolean plugged, boolean locked, boolean showClock) {
        draw(c,width,height,stage,level,nativeFailure,device,batteryPct,plugged,locked,showClock,0,LockDisplayName.DEFAULT);
    }

    void draw(Canvas c,float width,float height,int stage,int level,boolean nativeFailure,
              String device,int batteryPct,boolean plugged,boolean locked,boolean showClock,int biometricDemo,
              String displayName) {
        DeviceState state = new DeviceState(device, batteryPct, plugged, locked);
        if (width<=0 || height<=0) return;

        boolean portrait=height>width;
        boolean denied=stage==ArchiveTimeline.DENIED;
        boolean second=level>1;
        boolean scan=stage==ArchiveTimeline.SCAN;
        final float alpha=1f;
        float u=Math.min(width,height);
        c.save();
        int ink=withAlpha(denied?PAPER:INK,alpha), muted=withAlpha(denied?0xffb8a79a:MUTED,alpha);
        float x=width*(portrait?.085f:.045f), y=height*(portrait?.265f:.16f);
        float panelX=width*(portrait?.085f:.47f), panelY=height*(portrait?.555f:.67f);
        float panelW=width*(portrait?.83f:.43f), panelH=height*(portrait?.135f:.13f);
        if (state.locked) {
            float lockRail=width*(portrait?.25f:.223f);
            line(c,lockRail,0,lockRail,height,denied?0x998f8379:0xff252522,Math.max(1,u*.0007f));
        }
        float text=Math.max(10,u*(portrait?.017f:.014f));
        float micro=Math.max(8,u*(portrait?.011f:.0095f));
        line(c,x,y+u*.046f,x+u*.18f,y+u*.046f,ink,Math.max(1,u*.001f));
        text(c,"RHINE LAB",x,y+u*.022f,text*1.56f,BOLD,ink);
        text(c,"INSTRUMENT ARCHIVE",x,y+u*.066f,micro,MONO,muted);
        float railX=x-u*.024f;
        line(c,railX,height*(portrait?.25f:.132f),railX,height*(portrait?.68f:.708f),
                withAlpha(denied?PAPER:INK,.24f),Math.max(1,u*.00062f));
        for (int i=0;i<3;i++) {
            float tickY=y+u*(.24f+.022f*i);
            line(c,railX,tickY,railX+u*.009f,tickY,muted,Math.max(1,u*.0007f));
        }
        if (stage == ArchiveTimeline.FRAME) {
            if (showClock) clock(c,width,height,portrait,u,ink,muted);
            if (state.locked) lockMark(c,width,height,portrait,denied,u,displayName);
            c.restore();
            return;
        }
        float sealX=portrait?width*.116f:x+u*.055f;
        float sealY=portrait?height*.33f:y+u*.15f;
        float sealHalf=u*(portrait?.030f:.065f);
        rect(c,sealX-sealHalf,sealY-sealHalf,sealX+sealHalf,sealY+sealHalf,withAlpha(denied?0xff36221b:INK,alpha));
        seal(c,sealX,sealY,sealHalf*.71f,alpha,denied?ORANGE:PAPER);
        if (stage >= ArchiveTimeline.IDLE) {
            float fieldX=width*(portrait?.17f:.155f);
            text(c,"FIELD RECORD",fieldX,height*(portrait?.323f:.25f),micro,MONO,muted);
            text(c,"CERAMIC STUDY",fieldX,height*(portrait?.337f:.273f),text*.88f,BOLD,ink);
            float anchorX=width*(portrait?.56f:.326f), anchorY=height*(portrait?.352f:.235f);
            line(c,width*(portrait?.29f:.225f),height*(portrait?.336f:.27f),anchorX,anchorY,
                    withAlpha(denied?PAPER:INK,.35f*alpha),Math.max(1,u*.0006f));
            circle(c,anchorX,anchorY,u*.002f,muted);
        }
        if (showClock) clock(c,width,height,portrait,u,ink,muted);
        if (stage>=1) {
            float a=alpha;
            round(c,panelX,panelY,panelX+panelW,panelY+panelH,u*.008f,withAlpha(denied?0xff29231f:PAPER,.80f*a));
            strokeRect(c,panelX,panelY,panelX+panelW,panelY+panelH,withAlpha(denied?PAPER:INK,.21f*a),Math.max(1,u*.0006f));
            float inset=u*.018f, row=panelY+u*.032f;
            text(c,"DEVICE / TELEMETRY",panelX+inset,row,micro,MONO,muted);
            String index="PLATE 04";
            P.setTypeface(MONO); P.setTextSize(micro);
            text(c,index,panelX+panelW-inset-P.measureText(index),row,micro,MONO,muted);
            line(c,panelX,panelY,panelX+u*.072f,panelY,withAlpha(denied?ORANGE:INK,.75f),Math.max(2,u*.0015f));
            line(c,panelX+inset,row+u*.015f,panelX+panelW-inset,row+u*.015f,withAlpha(denied?PAPER:INK,.23f*a),Math.max(1,u*.0006f));
            String deviceName=state.deviceLabel.toUpperCase(Locale.US);
            if ("SM-X710".equals(deviceName)) deviceName = "GALAXY TAB S9";
            if(deviceName.length()>24) deviceName=deviceName.substring(0,24);
            P.setTypeface(BOLD); P.setTextSize(text);
            while(deviceName.length()>1 && P.measureText(deviceName)>panelW*.62f-inset) deviceName=deviceName.substring(0,deviceName.length()-1);
            text(c,deviceName,panelX+inset,row+u*.041f,text,BOLD,withAlpha(denied?PAPER:INK,a));
            String power=state.batteryPercent<0?"BATTERY --":String.format(Locale.US,"BATTERY %d%%",Math.min(100,state.batteryPercent));
            if(state.charging) power+=" / EXT POWER";
            text(c,power,panelX+inset,row+u*.075f,Math.max(micro,u*.013f),MONO,withAlpha(denied?0xffb8a79a:MUTED,a));
            line(c,panelX+panelW*.68f,row+u*.022f,panelX+panelW*.68f,panelY+panelH-u*.014f,withAlpha(denied?PAPER:INK,.18f*a),Math.max(1,u*.0006f));
            float dotX=panelX+panelW-inset-u*.008f, dotY=row+u*.038f;
            circle(c,dotX,dotY,u*.0045f,withAlpha(denied?COPPER:LIME,a));
            String status=state.locked?"LOCKED":"UNLOCKED";
            P.setTypeface(MONO); P.setTextSize(micro);
            text(c,status,dotX-u*.012f-P.measureText(status),dotY+micro*.35f,micro,MONO,withAlpha(denied?PAPER:INK,a));
        }
        if(scan) {
            float cx=width*(portrait?.66f:.43f), cy=height*(portrait?.46f:.48f);
            float trackY=cy+u*.108f, half=u*.0715f;
            text(c,"VISUAL SCAN",cx-half,trackY-u*.012f,micro,MONO,INK);
            rect(c,cx-half,trackY-u*.001f,cx+half,trackY+u*.001f,0x55737570);
        }
        if (stage==ArchiveTimeline.EXIT) {
            text(c,"VISUAL DEMO / RELEASE",width*(portrait?.085f:.045f),height*(portrait?.725f:.845f),
                    u*.013f,MONO,ink);
        }
        if(denied) {
            honeycomb(c,width,height,portrait,u);
            warning(c,width,height,portrait,second,nativeFailure,biometricDemo,u);
        }
        if (state.locked) lockMark(c,width,height,portrait,denied,u,displayName);
        c.restore();
    }
    /** Visual identification only: the system remains responsible for unlocking. */
    private void lockMark(Canvas c,float w,float h,boolean portrait,boolean denied,float u,String displayName) {
        float l=w*(portrait?.12f:.155f), r=w*(portrait?.53f:.352f);
        float t=h*(portrait?.36f:.31f), b=h*(portrait?.49f:.55f);
        float rail=w*(portrait?.25f:.223f);
        rect(c,l,t,r,b,denied?0xff0b0d0c:0xff0c0f0e);
        if(smoke!=null) {
            float ratio=(r-l)/(b-t), sourceRatio=smoke.getWidth()/(float)smoke.getHeight();
            int sw=smoke.getWidth(), sh=smoke.getHeight();
            if(sourceRatio>ratio) sw=Math.max(1,(int)(sh*ratio)); else sh=Math.max(1,(int)(sw/ratio));
            int sx=(smoke.getWidth()-sw)/2, sy=(smoke.getHeight()-sh)/2;
            P.setColor(Color.WHITE); P.setAlpha(65); P.setFilterBitmap(true);
            c.drawBitmap(smoke,new Rect(sx,sy,sx+sw,sy+sh),new RectF(l,t,r,b),P);
            P.setAlpha(255);
        }
        for (int i=0;i<36;i++) {
            float yy=t+(b-t)*(i+.5f)/36f;
            float x1=l+(r-l)*((i*17%43)/100f);
            float x2=Math.min(r,x1+(r-l)*(.12f+(i%7)*.055f));
            line(c,x1,yy,x2,yy,0x0a9a9f99,Math.max(1,u*.00045f));
        }
        float tagW=(r-l)*.28f, tagH=Math.min((b-t)*.12f,u*.024f);
        rect(c,rail-tagW*.5f,t-tagH,rail+tagW*.5f,t,denied?0xff292824:0xffe9e7e1);
        centered(c,"LOCKED",rail,t-tagH*.25f,tagH*.60f,MONO,denied?0xffa9a29b:0xff52544f);
        float cx=(l+r)*.5f, cardH=b-t;
        float logoW=Math.min((r-l)*.56f,cardH*.96f);
        NiriWordmark.draw(c,cx-logoW*.5f,t+cardH*.57f-logoW*.38f,logoW,denied?0xffc8c7c1:0xfff2f1eb,0xff0c0f0e);
        centered(c,"NIRI · SEALED SECTION",cx,t+cardH*.73f,Math.min((r-l)*.045f,cardH*.075f),MONO,denied?0xff777a74:0xff92958f);
        circle(c,r-(r-l)*.085f,t+cardH*.15f,Math.max(1,u*.004f),denied?0xff555550:0xff666b65);
        int main=denied?0xffbcb3aa:INK, sub=denied?0xff9b928a:0xff6f716b;
        fitCentered(c,displayName+"，你的会话已锁定。请出示授权口令",w*.5f,h*(portrait?.78f:.88f),u*(portrait?.027f:.021f),w*(portrait?.78f:.60f),BOLD,main);
        fitCentered(c,displayName+", YOUR SESSION IS LOCKED. PRESENT YOUR AUTHORIZATION KEY.",w*.5f,h*(portrait?.80f:.90f),u*(portrait?.012f:.010f),w*(portrait?.78f:.60f),MONO,sub);
    }
    private void centered(Canvas c,String s,float x,float y,float size,Typeface face,int color) {
        P.setTypeface(face); P.setTextSize(size);
        text(c,s,x-P.measureText(s)*.5f,y,size,face,color);
    }
    private void fitCentered(Canvas c,String s,float x,float y,float size,float maxWidth,Typeface face,int color) {
        P.setTypeface(face); P.setTextSize(size);
        float measured=P.measureText(s);
        if (measured>maxWidth) size*=maxWidth/measured;
        centered(c,s,x,y,size,face,color);
    }
    private void clock(Canvas c,float w,float h,boolean portrait,float u,int ink,int muted) {
        Date now=new Date();
        String time=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(now);
        String date=new SimpleDateFormat("EEE  ·  dd MMM yyyy",Locale.getDefault()).format(now).toUpperCase(Locale.getDefault());
        float right=w*(portrait?.91f:.94f), baseline=h*(portrait?.185f:.185f);
        float size=u*(portrait?.09f:.105f);
        P.setTypeface(LIGHT); P.setTextSize(size);
        text(c,time,right-P.measureText(time),baseline,size,LIGHT,ink);
        float small=u*.011f; P.setTypeface(MONO); P.setTextSize(small);
        float tracking=u*.0012f, cursor=right-P.measureText(date)-tracking*(date.length()-1);
        for(int i=0;i<date.length();i++) {
            String letter=date.substring(i,i+1);
            text(c,letter,cursor,baseline+u*.028f,small,MONO,muted);
            cursor+=P.measureText(letter)+tracking;
        }
    }
    private void warning(Canvas c,float w,float h,boolean portrait,boolean second,boolean nativeFailure,int biometricDemo,float u) {
        float bw=u*.30f, bh=u*.16f;
        float cx=portrait?w*.69f:Math.max(w*.466f,w*.352f+bw*.5f+u*.012f);
        float cy=h*(portrait?.47f:.5f);
        rect(c,cx-bw/2,cy-bh/2,cx+bw/2,cy+bh/2,Color.BLACK);
        strokeRect(c,cx-bw/2,cy-bh/2,cx+bw/2,cy+bh/2,ORANGE,Math.max(2,u*.0015f));
        String source = nativeFailure ? "PIN / PASSWORD" : switch (biometricDemo) {
            case 1 -> "DEMO / FACE";
            case 2 -> "DEMO / FINGERPRINT";
            case 3 -> "DEMO / SHORT CONTACT";
            default -> "DEMO";
        };
        text(c,source,cx-bw*.43f,cy-bh*.20f,u*.012f,MONO,ORANGE);
        text(c,"ACCESS DENIED",cx-bw*.43f,cy+bh*.12f,u*.023f,BOLD,0xfff2e4d8);
        text(c,second?"RESTRICTED / SIGNAL":"RESTRICTED",cx-bw*.43f,cy+bh*.34f,u*.010f,BOLD,ORANGE);
    }
    /** A quiet, unframed diagnostic texture in the empty left margin. */
    private void honeycomb(Canvas c,float w,float h,boolean portrait,float u) {
        if(hexShader==null)return;
        float left=w*(portrait?.045f:.025f), top=h*(portrait?.52f:.59f);
        float right=Math.min(w*(portrait?.24f:.15f),left+u*.22f), bottom=top+u*.13f;
        // Preserve the source PNG alpha; a soft mask removes any rectangular plate edge.
        int layer=c.saveLayer(left,top,right,bottom,null);
        P.setStyle(Paint.Style.FILL); P.setColor(Color.WHITE); P.setAlpha(51); P.setShader(hexShader);
        c.drawRect(left,top,right,bottom,P); P.setShader(null); P.setAlpha(255);
        P.setShader(new LinearGradient(left,top,right,top,
                new int[]{0x00ffffff,0xffffffff,0xffffffff,0x00ffffff},
                new float[]{0f,.22f,.72f,1f},Shader.TileMode.CLAMP));
        P.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawRect(left,top,right,bottom,P); P.setXfermode(null); P.setShader(null);
        c.restoreToCount(layer);
    }
    private void seal(Canvas c,float x,float y,float r,float alpha,int color) {
        P.setStyle(Paint.Style.STROKE); P.setStrokeWidth(Math.max(1,r*.032f)); P.setColor(withAlpha(color,alpha));
        c.drawCircle(x,y,r,P); c.drawCircle(x,y,r*.72f,P);
        Path path=new Path(); path.moveTo(x,y-r*.59f);path.lineTo(x+r*.50f,y+r*.30f);path.lineTo(x-r*.50f,y+r*.30f);path.close();c.drawPath(path,P);
        c.drawLine(x-r*.36f,y,x+r*.36f,y,P); P.setStyle(Paint.Style.FILL);
    }
    private static float clamp(float v){return Math.max(0,Math.min(1,v));}
    private static int withAlpha(int c,float a){return (Math.round(clamp(a)*Color.alpha(c))<<24)|(c&0xffffff);}
    private void rect(Canvas c,float l,float t,float r,float b,int color){P.setStyle(Paint.Style.FILL);P.setColor(color);c.drawRect(l,t,r,b,P);}
    private void round(Canvas c,float l,float t,float r,float b,float rad,int color){P.setStyle(Paint.Style.FILL);P.setColor(color);c.drawRoundRect(l,t,r,b,rad,rad,P);}
    private void strokeRect(Canvas c,float l,float t,float r,float b,int color,float sw){P.setStyle(Paint.Style.STROKE);P.setStrokeWidth(sw);P.setColor(color);c.drawRect(l,t,r,b,P);P.setStyle(Paint.Style.FILL);}
    private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float sw){P.setStyle(Paint.Style.STROKE);P.setStrokeWidth(sw);P.setColor(color);c.drawLine(x1,y1,x2,y2,P);P.setStyle(Paint.Style.FILL);}
    private void circle(Canvas c,float x,float y,float r,int color){P.setStyle(Paint.Style.FILL);P.setColor(color);c.drawCircle(x,y,r,P);}
    private void text(Canvas c,String s,float x,float y,float size,Typeface type,int color){P.setStyle(Paint.Style.FILL);P.setTypeface(type);P.setTextSize(size);P.setColor(color);c.drawText(s,x,y,P);}
}
