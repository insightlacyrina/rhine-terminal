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
    private final RectF telemetryRegion=new RectF();
    private String[] telemetrySource;
    private final String[] telemetryLines=new String[14];
    private int telemetryCount;
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
        draw(c,width,height,stage,level,nativeFailure,device,batteryPct,plugged,locked,showClock,biometricDemo,displayName,false);
    }

    void draw(Canvas c,float width,float height,int stage,int level,boolean nativeFailure,
              String device,int batteryPct,boolean plugged,boolean locked,boolean showClock,int biometricDemo,
              String displayName,boolean phoneLayout) {
        draw(c,width,height,stage,level,nativeFailure,device,batteryPct,plugged,locked,showClock,biometricDemo,displayName,phoneLayout,null,0);
    }

    void draw(Canvas c,float width,float height,int stage,int level,boolean nativeFailure,
              String device,int batteryPct,boolean plugged,boolean locked,boolean showClock,int biometricDemo,
              String displayName,boolean phoneLayout,String[] telemetry,long elapsedMillis) {
        draw(c,width,height,stage,level,nativeFailure,device,batteryPct,plugged,locked,showClock,biometricDemo,displayName,phoneLayout,telemetry,elapsedMillis,false);
    }

    void draw(Canvas c,float width,float height,int stage,int level,boolean nativeFailure,
              String device,int batteryPct,boolean plugged,boolean locked,boolean showClock,int biometricDemo,
              String displayName,boolean phoneLayout,String[] telemetry,long elapsedMillis,boolean darkPalette) {
        if (phoneLayout) {
            drawPhone(c,width,height,stage,level,nativeFailure,device,batteryPct,plugged,locked,showClock,biometricDemo,displayName,darkPalette);
            if(stage>=ArchiveTimeline.IDLE) drawTelemetry(c,width,height,true,stage==ArchiveTimeline.DENIED||darkPalette,telemetry,elapsedMillis);
            return;
        }
        DeviceState state = new DeviceState(device, batteryPct, plugged, locked);
        if (width<=0 || height<=0) return;

        boolean portrait=height>width;
        boolean denied=stage==ArchiveTimeline.DENIED;
        boolean second=level>1;
        boolean scan=stage==ArchiveTimeline.SCAN;
        final float alpha=1f;
        float u=Math.min(width,height);
        c.save();
        int ink=withAlpha(denied||darkPalette?PAPER:INK,alpha), muted=withAlpha(denied||darkPalette?0xffb8a79a:MUTED,alpha);
        float x=width*(portrait?.085f:.045f), y=height*(portrait?.265f:.16f);
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
            if (showClock) clock(c,width,height,portrait,u,ink,muted,false);
            if (state.locked) lockMark(c,width,height,portrait,denied||darkPalette,u,displayName);
            c.restore();
            return;
        }
        float sealX=portrait?width*.116f:x+u*.055f;
        float sealY=portrait?height*.33f:y+u*.15f;
        float sealHalf=u*(portrait?.030f:.065f);
        rect(c,sealX-sealHalf,sealY-sealHalf,sealX+sealHalf,sealY+sealHalf,withAlpha(denied?0xff36221b:INK,alpha));
        seal(c,sealX,sealY,sealHalf*.71f,alpha,denied?ORANGE:PAPER);
        if (stage >= ArchiveTimeline.IDLE) {
            float fieldX=width*(portrait?.48f:.155f);
            text(c,"FIELD RECORD",fieldX,height*(portrait?.323f:.25f),micro,MONO,muted);
            text(c,"CERAMIC STUDY",fieldX,height*(portrait?.337f:.273f),text*.88f,BOLD,ink);
            float anchorX=width*(portrait?.82f:.326f), anchorY=height*(portrait?.352f:.235f);
            float leaderX=portrait?fieldX+P.measureText("CERAMIC STUDY")+u*.012f:width*.225f;
            line(c,leaderX,height*(portrait?.336f:.27f),anchorX,anchorY,
                    withAlpha(denied?PAPER:INK,.35f*alpha),Math.max(1,u*.0006f));
            circle(c,anchorX,anchorY,u*.002f,muted);
        }
        if (showClock) clock(c,width,height,portrait,u,ink,muted,false);
        if (stage>=1) identity(c,width,height,false,portrait,denied||darkPalette,displayName,state.locked);
        if(scan) {
            // Tablet portrait places the scanner below the identity block and above the caption.
            float cx=width*(portrait?.69f:.43f), cy=height*(portrait?.65f:.48f);
            float scanUnit=portrait?Math.min(u,height*.61f):u;
            float trackY=cy+scanUnit*.108f, half=scanUnit*.0715f;
            text(c,"VISUAL SCAN",cx-half,trackY-scanUnit*.012f,micro,MONO,INK);
            rect(c,cx-half,trackY-scanUnit*.001f,cx+half,trackY+scanUnit*.001f,0x55737570);
        }
        if (stage==ArchiveTimeline.EXIT) {
            text(c,"VISUAL DEMO / RELEASE",width*(portrait?.48f:.045f),height*(portrait?.725f:.845f),
                    u*.013f,MONO,ink);
        }
        if(denied) {
            honeycomb(c,width,height,portrait,u);
            warning(c,width,height,portrait,second,nativeFailure,biometricDemo,u);
        }
        if (state.locked) lockMark(c,width,height,portrait,denied||darkPalette,u,displayName);
        if(stage>=ArchiveTimeline.IDLE) drawTelemetry(c,width,height,false,denied||darkPalette,telemetry,elapsedMillis);
        c.restore();
    }
    /** Dedicated compact phone layout; the top and lower system areas stay clear. */
    private void drawPhone(Canvas c,float w,float h,int stage,int level,boolean nativeFailure,
                           String device,int battery,boolean plugged,boolean locked,boolean showClock,
                           int biometricDemo,String displayName,boolean darkPalette) {
        if(w<=0 || h<=0) return;
        boolean portrait=h>w, denied=stage==ArchiveTimeline.DENIED;
        float u=Math.min(w,h), x=w*(portrait?.08f:.055f), top=h*(portrait?.265f:.25f);
        int ink=denied||darkPalette?PAPER:INK, muted=denied||darkPalette?0xffb8a79a:MUTED;
        float micro=Math.max(9,u*.016f), text=Math.max(12,u*.024f);
        c.save();
        float rail=w*(portrait?.27f:.13f);
        if(locked) line(c,rail,0,rail,h,denied?0x998f8379:0xff252522,Math.max(1,u*.001f));
        text(c,"RHINE LAB",x,top,text*1.25f,BOLD,ink);
        text(c,"INSTRUMENT ARCHIVE",x,top+u*.035f,micro,MONO,muted);
        line(c,x,top+u*.045f,x+u*.25f,top+u*.045f,ink,Math.max(1,u*.001f));
        if(showClock) clock(c,w,h,portrait,u,ink,muted,true);
        if(stage==ArchiveTimeline.FRAME) { c.restore(); return; }
        float l=w*(portrait?.08f:.06f), r=w*(portrait?.40f:.275f);
        float t=h*(portrait?.385f:.36f), b=h*(portrait?.49f:.64f);
        if(locked) {
            rect(c,l,t,r,b,0xff0c0f0e);
            if(smoke!=null) { P.setAlpha(80); c.drawBitmap(smoke,null,new RectF(l,t,r,b),P); P.setAlpha(255); }
            float logo=Math.min((r-l)*.60f,(b-t)*.9f);
            NiriWordmark.draw(c,(l+r-logo)*.5f,(t+b)*.5f-logo*.38f,logo,0xfff2f1eb,0xff0c0f0e);
            centered(c,"NIRI · SEALED SECTION",(l+r)*.5f,b-u*.016f,Math.min(micro,(r-l)*.045f),MONO,0xff92958f);
            float tagW=u*.11f, tagH=u*.023f;
            rect(c,rail-tagW*.5f,t-tagH,rail+tagW*.5f,t,denied?0xff292824:0xffe9e7e1);
            centered(c,"LOCKED",rail,t-tagH*.24f,tagH*.60f,MONO,muted);
        }
        identity(c,w,h,true,portrait,denied||darkPalette,displayName,locked);
        if(stage==ArchiveTimeline.SCAN) {
            float cx=w*(portrait?.65f:.47f), cy=h*(portrait?.44f:.48f);
            text(c,"VISUAL SCAN",cx-u*.0715f,cy+u*.096f,micro,MONO,ink);
        }
        if(denied) {
            drawHoneycomb(c,w*.045f,h*(portrait?.512f:.69f),w*(portrait?.27f:.135f),u*.105f);
            phoneWarning(c,w,h,portrait,level>1,nativeFailure,biometricDemo,u);
        }
        if(locked) {
            float cy=h*(portrait?.71f:.83f), max=w*(portrait?.84f:.72f);
            fitCentered(c,displayName+"，你的会话已锁定。请出示授权口令",w*.5f,cy,u*.028f,max,BOLD,ink);
            fitCentered(c,displayName+", YOUR SESSION IS LOCKED. PRESENT YOUR AUTHORIZATION KEY.",w*.5f,cy+u*.035f,u*.013f,max,MONO,muted);
        }
        c.restore();
    }
    private void identity(Canvas c,float w,float h,boolean phone,boolean portrait,boolean dark,String name,boolean locked) {
        float u=Math.min(w,h), x=w*(portrait?.48f:.68f), right=w*.95f;
        float center=h*.5f, block=u*(portrait?.19f:.17f), top=center-block*.5f;
        int foreground=dark?PAPER:INK, background=dark?PAPER:INK, inverse=dark?INK:PAPER;
        float rail=Math.max(1,u*.001f), label=Math.max(8,u*(phone?.013f:.011f));
        line(c,x,top,x,top+block,foreground,rail);
        float left=x+u*.014f, tagH=u*.024f, tagW=u*.098f;
        rect(c,left,top,left+tagW,top+tagH,background);
        text(c,"OPERATOR",left+u*.007f,top+tagH*.72f,label,BOLD,inverse);
        String session=locked?"SESSION LOCKED":"SESSION OPEN";
        text(c,session,left+tagW+u*.012f,top+tagH*.72f,label,MONO,foreground);
        float statusEnd=left+tagW+u*.024f+P.measureText(session);
        line(c,statusEnd,top+tagH*.45f,right,top+tagH*.45f,withAlpha(foreground,.40f),Math.max(1,u*.0006f));
        float plateTop=top+u*.035f, plateH=u*.071f, plateW=Math.min(right-left,u*(portrait?.25f:.23f));
        rect(c,left,plateTop,left+plateW,plateTop+plateH,background);
        String shown=name==null||name.isEmpty()?LockDisplayName.DEFAULT:name;
        float nameSize=u*.042f;
        P.setTypeface(BOLD); P.setTextSize(nameSize);
        float max=plateW-u*.020f, measure=P.measureText(shown);
        if(measure>max) nameSize=Math.max(u*.016f,nameSize*max/measure);
        P.setTextSize(nameSize);
        int count=P.breakText(shown,true,max,null);
        P.setColor(inverse); P.setStyle(Paint.Style.FILL);
        c.drawText(shown,0,count,left+u*.010f,plateTop+plateH*.70f,P);
        float metaY=top+block-u*.043f;
        circle(c,left+u*.004f,metaY-u*.003f,u*.0035f,dark?LIME:0xffaaca2e);
        String information="PERSONAL INFORMATION LOADED";
        P.setTypeface(MONO); P.setTextSize(label*.9f);
        float informationSize=Math.min(label*.9f,label*.9f*(right-left-u*.013f)/P.measureText(information));
        text(c,information,left+u*.013f,metaY,informationSize,MONO,foreground);
        float detailY=top+block-u*.012f;
        rect(c,left,detailY-u*.005f,left+u*.004f,detailY-u*.001f,foreground);
        text(c,"NIRI-03",left+u*.013f,detailY,label*.9f,MONO,foreground);
        line(c,left+u*.09f,detailY-u*.003f,right,detailY-u*.003f,withAlpha(foreground,.35f),Math.max(1,u*.0006f));
    }

    /** Shared by the Canvas log and its cropped GLES texture. */
    static void telemetryBounds(float w,float h,boolean phone,RectF bounds) {
        if (h<=w) {
            bounds.set(w*.024f,h*.57f,w*.212f,h*.79f);
        } else if (phone) {
            bounds.set(w*.035f,h*.505f,w*.255f,h*.565f);
        } else {
            bounds.set(w*.085f,h*.705f,w*.235f,h*.835f);
        }
    }

    /** Draw a clipped, bounded live log. The caller samples away from the render thread. */
    void drawTelemetry(Canvas c,float w,float h,boolean phone,boolean denied,String[] snapshot,long elapsedMillis) {
        if(snapshot==null || snapshot.length==0) return;
        if(snapshot!=telemetrySource) {
            telemetrySource=snapshot;
            telemetryCount=0;
            // Keep only the requested live readings, retaining each sample's N/A values.
            int[] selected={3,4,5,6,7,8,9,10,11,13,14,15};
            for(int index:selected) if(index<snapshot.length && snapshot[index]!=null) {
                String value=snapshot[index].trim();
                int space=value.indexOf(' ');
                if(space>0) value=value.substring(0,space)+"  "+value.substring(space).trim();
                telemetryLines[telemetryCount++]=value;
            }
        }
        if(telemetryCount==0) return;
        boolean portrait=h>w;
        float u=Math.min(w,h);
        telemetryBounds(w,h,phone,telemetryRegion);
        float x=telemetryRegion.left, right=telemetryRegion.right;
        float top=telemetryRegion.top, bottom=telemetryRegion.bottom;
        float size=Math.max(7f,u*(portrait?.008f:.0085f));
        float row=size*1.58f;
        int ink=denied?0xffd8c7b8:0xff252522;
        int muted=denied?0xffb3a095:0xff737570;
        c.save();
        c.clipRect(x,top,right,bottom);
        text(c,"COMMAND ENTERED",x,top+size,size*.88f,MONO,ink);
        float listTop=top+size*2.2f;
        float available=bottom-listTop;
        float cycle=telemetryCount*row;
        // The bounded live snapshot moves quickly; system sampling remains once a second.
        float shift=(elapsedMillis%Math.max(1L,telemetryCount*320L))/320f*row;
        c.clipRect(x,listTop-size*.75f,right,bottom);
        P.setTypeface(MONO); P.setTextSize(size);
        for(int i=0;i<telemetryCount;i++) {
            float y=listTop+i*row-shift;
            if(y<listTop-row) y+=cycle;
            if(y>bottom+row || y<listTop-row) continue;
            float fade=Math.max(.35f,Math.min(1f,(y-listTop+row)/(Math.max(row,available))*.65f+.35f));
            circle(c,x+size*.13f,y-size*.27f,Math.max(.6f,size*.095f),withAlpha(ink,fade));
            String item=telemetryLines[i];
            int count=P.breakText(item,true,Math.max(0,right-x-size*.6f),null);
            P.setColor(withAlpha(muted,fade)); P.setStyle(Paint.Style.FILL);
            c.drawText(item,0,count,x+size*.6f,y,P);
        }
        c.restore();
    }
    private void phoneWarning(Canvas c,float w,float h,boolean portrait,boolean second,boolean nativeFailure,int biometricDemo,float u) {
        float cx=w*(portrait?.65f:.47f), cy=h*(portrait?.64f:.48f);
        String source=nativeFailure?"PIN / PASSWORD":biometricDemo==1?"DEMO / FACE":biometricDemo==2?"DEMO / FINGERPRINT":biometricDemo==3?"DEMO / SHORT CONTACT":"DEMO";
        statusPopup(c,cx,cy,u,source,"ACCESS DENIED",second?"RESTRICTED / SIGNAL":"RESTRICTED",true);
    }
    /** Visual identification only: the system remains responsible for unlocking. */
    private void lockMark(Canvas c,float w,float h,boolean portrait,boolean denied,float u,String displayName) {
        float l=w*(portrait?.085f:.155f), r=w*(portrait?.415f:.352f);
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
        float captionWidth=w*(portrait?.46f:.60f);
        fitCentered(c,displayName+"，你的会话已锁定。请出示授权口令",w*.5f,h*(portrait?.78f:.88f),u*(portrait?.027f:.021f),captionWidth,BOLD,main);
        fitCentered(c,displayName+", YOUR SESSION IS LOCKED. PRESENT YOUR AUTHORIZATION KEY.",w*.5f,h*(portrait?.80f:.90f),u*(portrait?.012f:.010f),captionWidth,MONO,sub);
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
    private void clock(Canvas c,float w,float h,boolean portrait,float u,int ink,int muted,boolean phoneLayout) {
        Date now=new Date();
        String time=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(now);
        String date=new SimpleDateFormat("EEE  ·  dd MMM yyyy",Locale.getDefault()).format(now).toUpperCase(Locale.getDefault());
        float right=w*(portrait?.91f:.94f), baseline=h*(phoneLayout&&!portrait?.26f:.185f);
        float size=u*(portrait?.09f:.105f);
        P.setTypeface(LIGHT); P.setTextSize(size);
        text(c,time,right-P.measureText(time),baseline,size,LIGHT,ink);
        float small=u*(phoneLayout?.017f:.011f); P.setTypeface(MONO); P.setTextSize(small);
        float tracking=u*.0012f, cursor=right-P.measureText(date)-tracking*(date.length()-1);
        for(int i=0;i<date.length();i++) {
            String letter=date.substring(i,i+1);
            text(c,letter,cursor,baseline+u*.028f,small,MONO,muted);
            cursor+=P.measureText(letter)+tracking;
        }
    }
    private void warning(Canvas c,float w,float h,boolean portrait,boolean second,boolean nativeFailure,int biometricDemo,float u) {
        float bw=u*.30f;
        float cx=portrait?w*.69f:Math.max(w*.466f,w*.352f+bw*.5f+u*.012f);
        float cy=h*(portrait?.64f:.5f);
        String source = nativeFailure ? "PIN / PASSWORD" : switch (biometricDemo) {
            case 1 -> "DEMO / FACE";
            case 2 -> "DEMO / FINGERPRINT";
            case 3 -> "DEMO / SHORT CONTACT";
            default -> "DEMO";
        };
        statusPopup(c,cx,cy,u,source,"ACCESS DENIED",second?"RESTRICTED / SIGNAL":"RESTRICTED",false);
    }

    void drawIdlePopup(Canvas c,float w,float h,boolean phone) {
        boolean portrait=h>w;
        float u=Math.min(w,h);
        float cx=phone?w*(portrait?.65f:.47f):portrait?w*.69f:Math.max(w*.466f,w*.352f+u*.162f);
        float cy=h*(portrait?.64f:phone?.48f:.5f);
        statusPopup(c,cx,cy,u,"STANDBY","SESSION IDLE","TOUCH TO RESUME",phone);
    }

    private void statusPopup(Canvas c,float cx,float cy,float u,String source,String title,String detail,boolean phone) {
        float bw=u*.30f, bh=u*.16f;
        rect(c,cx-bw/2,cy-bh/2,cx+bw/2,cy+bh/2,Color.BLACK);
        strokeRect(c,cx-bw/2,cy-bh/2,cx+bw/2,cy+bh/2,ORANGE,Math.max(2,u*.0015f));
        text(c,source,cx-bw*.43f,cy-bh*.20f,u*(phone?.014f:.012f),MONO,ORANGE);
        text(c,title,cx-bw*.43f,cy+bh*.12f,u*.023f,BOLD,0xfff2e4d8);
        text(c,detail,cx-bw*.43f,cy+bh*.34f,u*(phone?.011f:.010f),BOLD,ORANGE);
    }
    /** A quiet, unframed diagnostic texture in the empty left margin. */
    private void honeycomb(Canvas c,float w,float h,boolean portrait,float u) {
        float left=w*(portrait?.085f:.025f), top=h*(portrait?.61f:.59f);
        float right=Math.min(w*(portrait?.235f:.15f),left+u*.22f);
        drawHoneycomb(c,left,top,right,portrait?Math.min(u*.13f,h*.075f):u*.13f);
    }
    private void drawHoneycomb(Canvas c,float left,float top,float right,float height) {
        if(hexShader==null)return;
        float bottom=top+height;
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
