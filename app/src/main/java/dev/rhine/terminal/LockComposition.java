package dev.rhine.terminal;

/** The full-screen stage and transparent HUD use the same screen coordinates in GLES and WebGL. */
public final class LockComposition {
    private LockComposition() {}

    public static final String VERTEX = """
        #version 300 es
        out vec2 vUv;
        void main() {
            vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));
            vUv=vec2(p.x,1.0-p.y);
            gl_Position=vec4(p*2.0-1.0,0,1);
        }
        """;

    public static final String STAGE = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        out vec4 frag;
        uniform vec2 uSize;
        uniform float uDeny, uDenyMix, uSeconds, uBirth, uGlitch;
        void main() {
            vec2 p=vUv, px=p*uSize;
            float s=min(uSize.x,uSize.y);
            bool portrait=uSize.y>uSize.x;
            float grain=fract(52.9829189*fract(dot(floor(px),vec2(.06711056,.00583715))))-.5;
            vec2 d=(p-vec2(portrait?.5:.43,.48))*vec2(.94,.83);
            float vignette=smoothstep(.07,.68,length(d));
            vec3 paper=vec3(.925,.918,.902)*(1.0-.13*vignette);
            vec2 shadow=(p-vec2(portrait?.5:.385,portrait?.49:.59))*vec2(2.5,8.0);
            paper*=1.0-.09*exp(-dot(shadow,shadow))*uBirth;
            if(uDenyMix>0.001) {
                vec3 dark=vec3(.043,.043,.047)+vec3(.012,.007,.003)*(1.0-vignette);
                float tear=fract(floor(px.y*.11)*.618033+floor(uSeconds*13.0)*.363);
                float strip=step(uDeny>1.5?.87:.96,tear);
                float scan=1.0-smoothstep(.16,.40,abs(fract(px.y*.20)-.5));
                dark+=vec3(.12,.043,.014)*strip*(.12+.75*uGlitch);
                dark-=vec3(.012)*scan*(.25+.75*uGlitch);
                vec2 edge=min(px,uSize-px);
                float line=1.0-smoothstep(1.5,3.1,min(edge.x,edge.y));
                dark=mix(dark,vec3(.949,.431,.071),line*.96);
                paper=mix(paper,dark,uDenyMix);
            }
            frag=vec4(paper+grain*.012,1);
        }
        """;

    public static final String HUD = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        out vec4 frag;
        uniform sampler2D uUi;
        uniform vec2 uSize;
        uniform vec4 uUiPhases;
        uniform float uScan, uScanHead, uScanOpacity, uScanSeconds, uWarningAge, uPhone;
        float ringLine(float r, float radius, float pixel) {
            return 1.0-smoothstep(.52*pixel,1.48*pixel,abs(r-radius));
        }
        float satellite(vec2 p, float radius, float angle, float width, float pixel) {
            return 1.0-smoothstep(width,width+pixel,length(p-radius*vec2(cos(angle),sin(angle))));
        }
        void main() {
            bool portrait=uSize.y>uSize.x;
            vec2 p=vUv;
            vec4 phases=clamp(uUiPhases,0.0,1.0);
            vec2 cardMin=portrait?vec2(.12,.342):vec2(.155,.281);
            vec2 cardMax=portrait?vec2(.53,.49):vec2(.352,.55);
            vec2 panelMin=portrait?vec2(.085,.555):vec2(.47,.67);
            vec2 panelMax=portrait?vec2(.915,.690):vec2(.90,.80);
            if (uPhone>.5) {
                cardMin=portrait?vec2(.08,.375):vec2(.06,.34);
                cardMax=portrait?vec2(.40,.49):vec2(.275,.64);
                panelMin=portrait?vec2(.08,.575):vec2(.67,.51);
                panelMax=portrait?vec2(.92,.66):vec2(.94,.73);
            }
            bool inCard=all(greaterThanEqual(p,cardMin-.001)) && all(lessThanEqual(p,cardMax+.001));
            bool inPanel=all(greaterThanEqual(p,panelMin-.001)) && all(lessThanEqual(p,panelMax+.001));
            bool inCaption=p.y>(uPhone>.5?(portrait?.69:.80):(portrait?.751:.848)) && p.x>(uPhone>.5?.07:.24) && p.x<(uPhone>.5?.93:.76);
            float opacity=inCard?phases.y:inPanel?phases.z:inCaption?phases.w:phases.x;
            vec4 ui=texture(uUi,p)*opacity;
            float s=min(uSize.x,uSize.y);
            if (uWarningAge>=0.0) {
                float x=portrait?uSize.x*.69:max(uSize.x*.466,uSize.x*.352+s*.162);
                if (uPhone>.5) x=uSize.x*(portrait?.65:.47);
                vec2 q=(p*uSize-vec2(x,uSize.y*(uPhone>.5?(portrait?.44:.48):(portrait?.47:.50))))/s;
                if (abs(q.x)<.145 && abs(q.y)<.075) {
                    if (q.y>.004) ui.rgb*=q.y>.036 ? step(.50,uWarningAge) : step(.26,uWarningAge);
                    float y=q.y+.052;
                    float sides=abs(abs(q.x)-.72*(y+.011));
                    float outline=1.0-smoothstep(.00055,.00155,min(sides,abs(y-.010)));
                    float icon=outline*step(-.011,y)*step(y,.010)*step(abs(q.x),.015);
                    icon=max(icon,step(abs(q.x),.0012)*(step(-.004,y)*step(y,.003)+step(abs(y-.007),.001)));
                    float blink=mix(.25,1.0,step(fract(uWarningAge/.86),.70));
                    ui.rgb=mix(ui.rgb,vec3(.95,.43,.07)*ui.a,icon*blink);
                }
            }
            vec4 marks=vec4(0);
            vec4 sweep=vec4(0);
            if(uScanOpacity>0.0) {
                vec2 center=uPhone>.5?vec2(portrait?.65:.47,portrait?.44:.48):vec2(portrait?.66:.43,portrait?.46:.48);
                float appear=.92+.08*uScanOpacity;
                vec2 scanPoint=(vUv-center)*uSize/(s*appear);
                float r=length(scanPoint);
                float a=atan(scanPoint.y,scanPoint.x)/6.2831853;
                float pixel=1.0/s;
                float outer=ringLine(r,.082,pixel)*step(fract(a+1.0/6.0-uScanSeconds/9.0),5.0/6.0);
                float middle=ringLine(r,.064,pixel*1.45)*step(fract(a-5.0/9.0+uScanSeconds/3.8),7.0/36.0);
                float inner=ringLine(r,.029,pixel)*step(fract(2.0*(a-1.0/36.0+uScanSeconds/2.6)),2.0/3.0);
                float spin=6.2831853*uScanSeconds/7.0;
                float light=max(satellite(scanPoint,.082,spin,.0024,pixel),
                                satellite(scanPoint,.082,spin+3.14159265,.0017,pixel));
                light=max(light,satellite(scanPoint,.050,-1.5707963+6.2831853*uScanSeconds/4.2,.0019,pixel));
                vec2 bar=(vUv-center)*uSize/s-vec2(0.0,.108);
                float track=(1.0-smoothstep(.0006,.0015,abs(bar.y)))*
                    step(-.0715,bar.x)*step(bar.x,mix(-.0715,.0715,uScan));
                float ink=max(max(outer,middle),max(inner,track));
                float coverage=max(ink*.78,light)*uScanOpacity;
                vec3 amber=vec3(.82,.40,.09);
                marks=vec4(mix(vec3(.15,.14,.12),amber,light)*coverage,coverage);
                float sweepPhase=.5-.5*cos(uScanHead*6.2831853);
                float dy=abs((vUv.y-mix(panelMin.y+.034,panelMax.y-.008,sweepPhase))*uSize.y);
                float panelSpan=step(panelMin.x+.011,vUv.x)*step(vUv.x,panelMax.x-.011);
                float slab=(1.0-smoothstep(.5,2.2,dy))*panelSpan*.62*uScanOpacity;
                sweep=vec4(amber*slab,slab);
            }
            vec4 layer=ui+marks*(1.0-ui.a);
            frag=sweep+layer*(1.0-sweep.a);
        }
        """;
}
