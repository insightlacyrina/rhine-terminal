package dev.rhine.terminal;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

/** Original procedural artwork. No franchise images, logos, typefaces, or 3D assets. */
public final class TerminalRenderer {
    private static final int PAPER=0xffe7e7e1, INK=0xff20231f, MUTED=0xff74786e;
    private static final int RULE=0xffbcbfb4, GOLD=0xffb48c42, GREEN=0xff93ac67, ORANGE=0xffc7753c;
    private final SharedPreferences prefs;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF rect=new RectF();
    private final Typeface regular=Typeface.create("sans-serif",Typeface.NORMAL);
    private final Typeface light=Typeface.create("sans-serif-thin",Typeface.NORMAL);
    private final Typeface bold=Typeface.create("sans-serif",Typeface.BOLD);
    private final Typeface mono=Typeface.create("monospace",Typeface.NORMAL);
    private final Face[] faces=new Face[384];
    private final float[][] points=new float[4][3];
    private final DateTimeFormatter timeFormat=DateTimeFormatter.ofPattern("HH:mm",Locale.US);
    private final DateTimeFormatter dateFormat=DateTimeFormatter.ofPattern("yyyy.MM.dd  /  EEEE",Locale.US);
    private long lastSecond=Long.MIN_VALUE;
    private ZoneId zone;
    private String time="",date="",seconds="";
    private int accent=GREEN;
    private String state="NOMINAL";
    private boolean demo;
    private boolean overlayOnly;
    private int warningKind;
    private float logicalWidth=1600,logicalHeight=1000,sculptureX=650,sculptureY=541,sculptureRadius=178;

    public TerminalRenderer(Context context) {
        prefs=context.getSharedPreferences("terminal",Context.MODE_PRIVATE);
        for(int i=0;i<faces.length;i++) faces[i]=new Face();
    }

    /** Cached 2D interface; the resident GPU mesh replaces the old CPU slat renderer. */
    public void drawOverlay(Canvas c,int width,int height,long uptimeMillis,float tiltX,float tiltY,boolean locked,int batteryPct) {
        drawOverlay(c,width,height,uptimeMillis,tiltX,tiltY,locked,batteryPct,0);
    }

    public void drawOverlay(Canvas c,int width,int height,long uptimeMillis,float tiltX,float tiltY,boolean locked,int batteryPct,int warning) {
        overlayOnly=true;
        warningKind=warning;
        try { draw(c,width,height,uptimeMillis,tiltX,tiltY,locked,batteryPct); }
        finally { overlayOnly=false; warningKind=0; }
    }

    /** Compatibility animation for devices where no GLES window surface can be created. */
    void drawFallbackAlert(Canvas c,int width,int height,long uptime,float tx,float ty,boolean locked,int battery,float alert,boolean nativeFailure) {
        draw(c,width,height,uptime,tx,ty,locked,battery);
        if (alert<=0) return;
        int alpha=Math.round(alert*235);
        c.drawColor((alpha<<24)|0x00111410);
        float s=Math.min(width,height)/1000f;
        int orange=(Math.round(alert*255)<<24)|0x00d18347;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,3*s));p.setColor(orange);
        c.drawRect(4*s,4*s,width-4*s,height-4*s,p);p.setStyle(Paint.Style.FILL);
        String label=nativeFailure?"WARNING / DEVICE CREDENTIAL REJECTED":"WARNING / DEMO";
        p.setTypeface(mono);p.setTextSize(24*s);
        text(c,label,(width-p.measureText(label))/2,height*.52f,24*s,mono,orange);
    }

    /** Pixel placement shared by the native GPU sculpture and the cached interface. */
    public float[] sculptureViewport(int width,int height) {
        float scale=Math.min(width/logicalWidth,height/logicalHeight);
        return new float[]{(width-logicalWidth*scale)/2+sculptureX*scale,
                (height-logicalHeight*scale)/2+sculptureY*scale,sculptureRadius*scale};
    }

    public void draw(Canvas c,int width,int height,long uptimeMillis,float tiltX,float tiltY,boolean locked,int batteryPct) {
        long now=System.currentTimeMillis();
        ZoneId currentZone=ZoneId.systemDefault();
        if(now/1000!=lastSecond || !currentZone.equals(zone)) {
            lastSecond=now/1000; zone=currentZone;
            ZonedDateTime z=Instant.ofEpochMilli(now).atZone(zone);
            time=timeFormat.format(z); date=dateFormat.format(z).toUpperCase(Locale.US);
            seconds=String.format(Locale.US,"%02d",z.getSecond());
        }
        boolean reduced=prefs.getBoolean("reduced_motion",false);
        double t=reduced?0:uptimeMillis/1000.0;
        String requested=prefs.getString("state","auto");
        demo=warningKind==1||(!overlayOnly&&"alert_demo".equals(requested));
        if(warningKind==2){state="PIN / DENIED";accent=ORANGE;}
        else if(demo){state="WARNING / DEMO";accent=ORANGE;}
        else if("scan".equals(requested) || ("auto".equals(requested) && uptimeMillis%32000<5200 && !reduced)) {state="ANALYSIS";accent=GOLD;}
        else {state="NOMINAL";accent=GREEN;}
        String layout=prefs.getString("layout","auto");
        boolean landscape="landscape".equals(layout)||(!"portrait".equals(layout)&&width>height*1.1f);
        float w=landscape?1600:1000,h=landscape?1000:1600;
        logicalWidth=w;logicalHeight=h;
        c.drawColor(PAPER);
        c.save();
        float s=Math.min(width/w,height/h);
        c.translate((width-w*s)/2,(height-h*s)/2); c.scale(s,s);
        texture(c,w,h);
        if(landscape && prefs.getBoolean("native_right",true) && !prefs.getBoolean("show_clock",false)) landscapeCompanion(c,w,h,t,tiltX,tiltY,locked,batteryPct);
        else if(landscape) landscape(c,w,h,t,tiltX,tiltY,locked,batteryPct);
        else if(prefs.getBoolean("native_right",true) && !prefs.getBoolean("show_clock",false)) portraitCompanion(c,w,h,t,tiltX,tiltY,locked,batteryPct);
        else portrait(c,w,h,t,tiltX,tiltY,locked,batteryPct);
        c.restore();
    }

    private void texture(Canvas c,float w,float h) {
        p.setShader(new LinearGradient(0,0,w,h,new int[]{0xfff1f0eb,PAPER,0xffdaddd3},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);p.setShader(null);
        for(int x=32;x<w;x+=32) for(int y=32;y<h;y+=32) box(c,x,y,1,1,0x17727768);
        for(int i=0;i<6;i++) {float y=h-40-i*8;line(c,40,y,65+i*6,y,0x5074796c,.7f);}
        line(c,w-34,42,w-34,h-42,RULE,.7f);
        for(int y=42;y<h-42;y+=10) line(c,w-34,y,w-(y%50==42?43:39),y,RULE,.7f);
    }

    private float safe(float h){return Math.max(.08f,Math.min(.35f,prefs.getFloat("safe_top",.20f)))*h;}

    /** Leaves the actual Samsung landscape clock and widget field untouched at the right. */
    private void landscapeCompanion(Canvas c,float w,float h,double t,float tx,float ty,boolean locked,int battery) {
        float header=safe(h)+16,shift=(header-216)*.5f,top=362+shift;
        if(prefs.getBoolean("native_dark",true)) {
            box(c,864,304,624,409,0xff252920);
            line(c,864,304,1488,304,0xff5e6852,1);
            label(c,"SYSTEM / ONE UI",884,334,10,0xffa5ad9d);
            label(c,"NATIVE CLOCK / WIDGETS",884,699,9,0xffa5ad9d);
            box(c,1464,690,7,7,GREEN);
        }
        brand(c,64,header,1);
        label(c,"R / L     RESEARCH SYSTEMS",625,header-27,12,MUTED);
        text(c,"ANALYSIS OS",625,header+9,25,regular,INK);
        line(c,64,header+42,header<240?1536:850,header+42,INK,1);
        label(c,"01 — PERSONNEL",64,header+66,10,MUTED);
        label(c,"02 — CONTINUOUS ANALYSIS",465,header+66,10,MUTED);
        if(header<240)label(c,"03 — SYSTEM / WIDGET FIELD",1202,header+66,10,MUTED);
        text(c,"Research,",61,top+40,58,regular,INK);
        text(c,"in motion.",61,top+106,58,regular,INK);
        label(c,date,64,top+146,12,MUTED);
        float y=Math.min(top+190,604);
        box(c,64,y,322,27,INK);
        label(c,"PERSONNEL / ARCHIVE 001",74,y+18,9,PAPER);
        String name=prefs.getString("operator","OPERATOR");
        if(name==null||name.trim().isEmpty())name="OPERATOR";
        name=name.trim();if(name.length()>18)name=name.substring(0,18);
        p.setTypeface(bold);p.setTextSize(28);float size=Math.min(28,28*322/Math.max(1,p.measureText(name)));
        text(c,name,64,y+66,size,bold,INK);
        line(c,64,y+85,386,y+85,RULE,1);
        label(c,"SYSTEM SECURITY",64,y+111,10,MUTED);
        text(c,"ONE UI KEYGUARD",64,y+141,21,regular,INK);
        box(c,64,y+160,6,6,accent);
        label(c,locked?"SESSION LOCKED":"SESSION ACTIVE",80,y+168,10,MUTED);
        label(c,"DEVICE / "+(battery>=0?battery+"%":"—"),64,y+199,10,MUTED);
        float radius=Math.min(178,(615-header)/2);
        ringScene(c,650,header+147+radius,radius,t,tx,ty);
        label(c,"FIG. 01 / ANNULAR STUDY",465,811,10,MUTED);
        statusBar(c,64,825,1472,locked,battery,t);
        footer(c,64,952,1472);
    }

    private void landscape(Canvas c,float w,float h,double t,float tx,float ty,boolean locked,int battery) {
        float header=safe(h)+16, shift=(header-216)*.5f;
        brand(c,64,header,1);
        label(c,"R / L     RESEARCH SYSTEMS",625,header-27,12,MUTED);
        text(c,"ANALYSIS OS",625,header+9,25,regular,INK);
        line(c,64,header+42,1536,header+42,INK,1);
        label(c,"01 — ENVIRONMENT",64,header+66,10,MUTED);
        label(c,"02 — CONTINUOUS ANALYSIS",560,header+66,10,MUTED);
        label(c,"03 — PERSONNEL ARCHIVE",1202,header+66,10,MUTED);
        float top=362+shift;
        if(prefs.getBoolean("show_clock",false)) {
            text(c,time,54,top+106,158,light,INK);
            label(c,seconds,432,top+103,18,GOLD);
            label(c,date,64,top+146,12,MUTED);
        } else {
            text(c,"Research,",61,top+40,58,regular,INK);
            text(c,"in motion.",61,top+106,58,regular,INK);
            label(c,date,64,top+146,12,MUTED);
        }
        line(c,64,top+181,428,top+181,RULE,1);
        label(c,"FIELD / 007",64,top+207,11,MUTED);
        text(c,"A study of",64,top+246,28,regular,INK);
        text(c,"continuous form.",64,top+282,28,regular,INK);
        label(c,"MORPHOLOGY  /  ROTATIONAL FIELD",64,top+315,10,MUTED);
        label(c,"LIVE PROCEDURAL STUDY  ·  001",64,top+338,10,MUTED);
        float radius=(615-header)/2;
        ringScene(c,806,header+147+radius,radius,t,tx,ty);
        label(c,"FIG. 01",562,811,11,INK);
        label(c,"ANNULAR STRUCTURE / REAL-TIME",668,811,10,MUTED);
        identity(c,1202,top+6,334,locked,battery,Math.min(1,(805-top-6)/402));
        statusBar(c,64,825,1472,locked,battery,t);
        footer(c,64,952,1472);
    }

    private void portrait(Canvas c,float w,float h,double t,float tx,float ty,boolean locked,int battery) {
        float header=safe(h)+12;
        brand(c,64,header,1.12f);
        label(c,"ANALYSIS OS / 01",651,header-20,14,INK);
        label(c,"RESEARCH SYSTEMS",651,header+8,11,MUTED);
        line(c,64,header+58,936,header+58,INK,1);
        label(c,"01 — CONTINUOUS ANALYSIS",64,header+87,11,MUTED);
        float radius=Math.min(274,(1390-header-100-370)/2);
        float cy=header+100+radius;
        ringScene(c,500,cy,radius,t,tx,ty);
        label(c,"FIG. 01  /  ANNULAR STRUCTURE",64,cy+radius+45,11,MUTED);
        label(c,"REAL-TIME",808,cy+radius+45,10,MUTED);
        float top=cy+radius+78;
        line(c,64,top-22,936,top-22,RULE,1);
        if(prefs.getBoolean("show_clock",false)) {
            text(c,time,56,top+94,135,light,INK);
            label(c,date,64,top+131,11,MUTED);
        } else {
            text(c,"Research,",62,top+42,48,regular,INK);
            text(c,"in motion.",62,top+99,48,regular,INK);
            label(c,date,64,top+139,10,MUTED);
        }
        identity(c,566,top,370,locked,battery,.72f);
        statusBar(c,64,1390,872,locked,battery,t);
        footer(c,64,1542,872);
    }

    private void portraitCompanion(Canvas c,float w,float h,double t,float tx,float ty,boolean locked,int battery) {
        float header=safe(h)+12;
        if(prefs.getBoolean("native_dark",true)) {
            box(c,556,615,350,178,0xff252920);
            box(c,318,800,588,202,0xff252920);
            label(c,"ONE UI",574,636,8,0xffa5ad9d);
            label(c,"SYSTEM COMPONENTS",336,990,8,0xffa5ad9d);
        }
        brand(c,64,header,1.12f);
        label(c,"ANALYSIS OS / 01",651,header-20,14,INK);
        label(c,"RESEARCH SYSTEMS",651,header+8,11,MUTED);
        line(c,64,header+58,header<500?936:500,header+58,INK,1);
        label(c,"01 — CONTINUOUS ANALYSIS",64,header+87,11,MUTED);
        float radius=Math.min(150,(800-header-58-94)/2);
        ringScene(c,300,header+105+radius,radius,t,tx,ty);
        label(c,"FIG. 01 / ANNULAR STUDY",64,794,10,MUTED);
        float top=1058;
        line(c,64,top-22,936,top-22,RULE,1);
        text(c,"Research,",62,top+42,48,regular,INK);
        text(c,"in motion.",62,top+99,48,regular,INK);
        label(c,date,64,top+139,10,MUTED);
        identity(c,566,top,370,locked,battery,.72f);
        statusBar(c,64,1390,872,locked,battery,t);
        footer(c,64,1542,872);
    }

    private void brand(Canvas c,float x,float y,float scale) {
        c.save();c.translate(x,y-42*scale);c.scale(scale,scale);
        // Abstract field glyph, newly drawn for this project.
        box(c,0,0,7,46,INK); box(c,12,0,7,32,INK);box(c,24,0,7,20,INK);
        line(c,38,0,38,46,RULE,1);
        text(c,"RHINE",52,23,27,bold,INK);
        label(c,"L A B  /  FIELD DIVISION",53,43,9,MUTED);
        c.restore();
    }

    private void identity(Canvas c,float x,float y,float width,boolean locked,int battery,float scale) {
        c.save(); c.translate(x,y);c.scale(scale,scale);float w=width/scale;
        box(c,0,0,w,30,INK);
        label(c,"PERSONNEL / ARCHIVE 001",12,20,10,0xffeceee5);
        box(c,0,50,44,44,INK);
        text(c,"01",7,81,25,light,PAPER);
        label(c,"OPERATOR",60,62,10,MUTED);
        String name=prefs.getString("operator","OPERATOR");
        if(name==null||name.trim().isEmpty())name="OPERATOR";
        name=name.trim(); if(name.length()>18)name=name.substring(0,18);
        float nameSize=26;
        p.setTypeface(bold);p.setTextSize(nameSize);
        if(p.measureText(name)>w-60)nameSize*= (w-60)/p.measureText(name);
        text(c,name,60,94,nameSize,bold,INK);
        line(c,0,118,w,118,RULE,1);
        label(c,"SESSION",0,147,10,MUTED);
        text(c,locked?"LOCKED":"ACTIVE",0,180,26,regular,INK);
        box(c,w-10,159,8,8,accent);
        label(c,"DEVICE / POWER",0,215,10,MUTED);
        text(c,battery>=0?String.format(Locale.US,"%03d",battery)+"%":"—",0,246,24,light,INK);
        for(int i=0;i<20;i++)box(c,i*(w/20),261,w/20-3,4,battery>i*5?accent:0xffc9ccc0);
        line(c,0,286,w,286,RULE,1);
        label(c,"SYSTEM KEYGUARD",0,310,10,MUTED);
        label(c,"AUTHENTICATION / ONE UI",0,334,11,INK);
        label(c,"USE DEVICE FINGERPRINT OR PIN",0,355,9,MUTED);
        // Barcode expresses a decorative archive index only.
        for(int i=0;i<68;i++)if((i*13%17)<10)box(c,i*(w/68),382,(i%3==0?2:1),20,0xff74786e);
        c.restore();
    }

    private void ringScene(Canvas c,float cx,float cy,float r,double t,float tx,float ty) {
        sculptureX=cx;sculptureY=cy;sculptureRadius=r;
        // Instrument plane: stationary 2D annotations surround a depth-sorted slat surface.
        stroke(c,cx-r-22,cy-r-22,cx+r+22,cy+r+22,0x40a2a897,.8f);
        for(int i=0;i<72;i++) {
            double a=i*Math.PI/36;float rr=r+28, len=i%6==0?12:4;
            line(c,cx+(float)Math.cos(a)*rr,cy+(float)Math.sin(a)*rr,cx+(float)Math.cos(a)*(rr+len),cy+(float)Math.sin(a)*(rr+len),i%6==0?MUTED:RULE,.8f);
        }
        line(c,cx-r-47,cy,cx-r-18,cy,RULE,1);line(c,cx+r+18,cy,cx+r+47,cy,RULE,1);
        line(c,cx,cy-r-47,cx,cy-r-18,RULE,1);line(c,cx,cy+r+18,cx,cy+r+47,RULE,1);
        label(c,"Y",cx+8,cy-r-35,9,MUTED);label(c,"X",cx+r+35,cy-8,9,MUTED);
        p.setShader(new android.graphics.RadialGradient(cx+12,cy+r*.65f,r*.88f,new int[]{0x28767c69,0x00767c69},null,Shader.TileMode.CLAMP));
        c.drawOval(cx-r,cy+r*.38f,cx+r,cy+r*.96f,p);p.setShader(null);
        if(overlayOnly) {
            line(c,cx+r*.50f,cy-r*.42f,cx+r*.89f,cy-r*.69f,MUTED,.8f);
            label(c,"A—01",cx+r*.89f-22,cy-r*.72f,9,MUTED);
            return;
        }
        float yaw=(float)(t*.105)+tx*.4f, pitch=.83f+ty*.26f;
        int n=0;
        // Each faceted segment is a ribbon section, one pale main band and a precise gold inner band.
        for(int band=0;band<3;band++) {
            for(int i=0;i<128;i++) {
                float a=(float)(i*Math.PI*2/128),b=a+.038f;
                float radius=r*(band==0?.76f:band==1?.56f:.92f);
                float breadth=r*(band==0?.20f:band==1?.055f:.025f);
                point(points[0],a,-breadth,radius,yaw,pitch,t,band);
                point(points[1],a,breadth,radius,yaw,pitch,t,band);
                point(points[2],b,breadth,radius,yaw,pitch,t,band);
                point(points[3],b,-breadth,radius,yaw,pitch,t,band);
                Face f=faces[n++];f.z=0;
                for(int k=0;k<4;k++) {
                    float perspective=950/(950+points[k][2]);
                    f.xy[k*2]=cx+points[k][0]*perspective;
                    f.xy[k*2+1]=cy+points[k][1]*perspective;
                    f.z+=points[k][2]/4;
                }
                float shade=(float)(.50+.28*Math.cos(a-1.1)+.15*Math.sin(a*2+yaw));
                if(band==1)f.color=blend(0xff615330,0xffdcc58e,shade);
                else if(band==2)f.color=blend(0xff747c68,0xffd3d8c9,shade);
                else f.color=blend(0xff687362,0xfffcfcf4,shade);
            }
        }
        Arrays.sort(faces,(a,b)->Float.compare(b.z,a.z));
        for(Face f:faces) {
            path.reset();path.moveTo(f.xy[0],f.xy[1]);for(int k=1;k<4;k++)path.lineTo(f.xy[k*2],f.xy[k*2+1]);path.close();
            p.setStyle(Paint.Style.FILL);p.setColor(f.color);c.drawPath(path,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.55f);p.setColor(0x54747d68);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        }
        if("ANALYSIS".equals(state)) {
            float scan=(float)((t*.12)%1);
            float sy=cy-r+scan*r*2;
            c.save();c.clipRect(cx-r-9,cy-r-9,cx+r+9,cy+r+9);
            p.setShader(new LinearGradient(0,sy-40,0,sy,new int[]{0x00b48c42,0x30b48c42},null,Shader.TileMode.CLAMP));
            c.drawRect(cx-r,sy-40,cx+r,sy,p);p.setShader(null);
            line(c,cx-r,sy,cx+r,sy,0x80b48c42,1);c.restore();
        }
        box(c,cx-3,cy-3,6,6,accent);
        line(c,cx+r*.50f,cy-r*.42f,cx+r*.89f,cy-r*.69f,MUTED,.8f);
        label(c,"A—01",cx+r*.89f-22,cy-r*.72f,9,MUTED);
    }

    private void point(float[] dest,float a,float edge,float radius,float yaw,float pitch,double t,int band) {
        double twist=a*2+t*.16+band*.85;
        float rad=radius+edge*(float)Math.cos(twist);
        float x=rad*(float)Math.cos(a), z=rad*(float)Math.sin(a);
        float y=edge*(float)Math.sin(twist)+(float)Math.sin(a*3+t*.20)*radius*.06f;
        float xx=x*(float)Math.cos(yaw)+z*(float)Math.sin(yaw);
        float zz=-x*(float)Math.sin(yaw)+z*(float)Math.cos(yaw);
        dest[0]=xx;dest[1]=y*(float)Math.cos(pitch)-zz*(float)Math.sin(pitch);
        dest[2]=y*(float)Math.sin(pitch)+zz*(float)Math.cos(pitch);
    }

    private void statusBar(Canvas c,float x,float y,float w,boolean locked,int battery,double t) {
        box(c,x,y,w,86,INK);
        box(c,x+16,y+18,4,50,accent);
        label(c,"TERMINAL / STATUS",x+35,y+27,10,0xffa5ad9d);
        text(c,locked?"SESSION LOCKED":"SESSION ACTIVE",x+34,y+59,w>1000?27:23,regular,0xffeeeeE6);
        float sx=x+w*.57f;
        box(c,sx,y+23,6,6,accent);
        label(c,state,sx+16,y+30,11,accent);
        label(c,warningKind==2?"SYSTEM / DEVICE CREDENTIAL REJECTED":demo?"VISUAL TEST / NO SECURITY EVENT":"RENDER / ONLINE   ·   LOCAL ONLY",sx,y+58,9,0xffa5ad9d);
        for(int i=0;i<18;i++)box(c,x+w-82+i*3,y+18,1,50,(i*7%13<7)?0xffb0b7a6:0xff485041);
    }

    private void footer(Canvas c,float x,float y,float w) {
        line(c,x,y-23,x+w,y-23,RULE,1);
        label(c,"R / L",x,y,12,INK);
        label(c,"FIELD DIVISION  /  PERSONAL TERMINAL",x+62,y,9,MUTED);
        String end=demo?"DEMONSTRATION MODE":"CONTINUITY IS A FORM OF DISCOVERY.";
        p.setTypeface(mono);p.setTextSize(9);float width=p.measureText(end);
        label(c,end,x+w-width,y,9,MUTED);
    }

    private int blend(int a,int b,float f) {
        f=Math.max(0,Math.min(1,f));return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*f),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*f),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*f));
    }
    private void label(Canvas c,String s,float x,float y,float size,int color){text(c,s,x,y,size,mono,color);}
    private void text(Canvas c,String s,float x,float y,float size,Typeface face,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTypeface(face);p.setTextSize(size);c.drawText(s,x,y,p);}
    private void box(Canvas c,float x,float y,float w,float h,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRect(x,y,x+w,y+h,p);}
    private void line(Canvas c,float x,float y,float x2,float y2,int color,float width){p.setStyle(Paint.Style.STROKE);p.setColor(color);p.setStrokeWidth(width);c.drawLine(x,y,x2,y2,p);p.setStyle(Paint.Style.FILL);}
    private void stroke(Canvas c,float l,float t,float r,float b,int color,float width){p.setStyle(Paint.Style.STROKE);p.setColor(color);p.setStrokeWidth(width);c.drawOval(l,t,r,b,p);p.setStyle(Paint.Style.FILL);}
    private static final class Face {final float[] xy=new float[8];float z;int color;}
}
