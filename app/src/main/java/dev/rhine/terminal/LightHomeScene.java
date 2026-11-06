// SPDX-License-Identifier: AGPL-3.0-or-later
// Card path and carve adapted from Rhine Lock shaders.ts, revision a4df68c6bd9e01ecb7660659becf495957e57faa.
package dev.rhine.terminal;

/** Shared desktop scene. The specimen proportions and artwork are from Rhine Lock. */
public final class LightHomeScene {
    private LightHomeScene() {}
    public static final String VERTEX_SHADER = """
        #version 300 es
        precision highp float;
        out vec2 vUv;
        void main() {
            vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));
            vUv=vec2(p.x,1.0-p.y);
            gl_Position=vec4(p*2.0-1.0,0.0,1.0);
        }
        """;
    public static final String FRAGMENT_SHADER = """
        #version 300 es
        precision highp float;
        uniform vec2 uSize;
        uniform float uTime, uReveal, uDark, uPhone;
        uniform sampler2D uCardTex;
        in vec2 vUv;
        out vec4 fragColor;

        // The same path, tangent, basis and roll as the upstream card stream.
        vec3 rl_path(float s) {
            return vec3(-338.0+52.0*s+34.0*sin(s*3.6),
                        -150.0+205.0*s+18.0*sin(s*5.0),430.0-350.0*s);
        }
        vec3 rotateAxis(vec3 v,vec3 axis,float a) {
            return v*cos(a)+cross(axis,v)*sin(a)+axis*dot(axis,v)*(1.0-cos(a));
        }
        float chemistry(vec2 p) {
            vec2 q=fract(p*6.4)-.5, a=abs(q);
            float hex=abs(max(a.y,.8660254*a.x+.5*a.y)-.226);
            float ring=1.0-smoothstep(.006,.016,hex);
            float bond=(1.0-smoothstep(.004,.012,abs(q.y)))*smoothstep(.255,.29,q.x);
            float atom=1.0-smoothstep(.017,.028,length(q-vec2(.41,0.0)));
            return max(ring,max(bond*.58,atom*.77));
        }
        float noise(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
        float cardPhase(float index) {
            // Stage.ts advances 0.55 card slots per second. The five-card
            // loop fades at both ends, so the wrapped specimen never jumps.
            return fract((index+.5+uTime*.55)/5.0);
        }
        float streamFade(float s) {
            return smoothstep(0.0,.07,s)*(1.0-smoothstep(.88,1.0,s));
        }
        vec2 cardCenter(float s, float portrait, float appear) {
            vec2 landscape=mix(vec2(.53,.485),vec2(.862,.686),s);
            vec2 upright=mix(vec2(.23,.295),vec2(.75,.473),s);
            vec2 drift=vec2(sin(s*6.2831853+uTime*.22),cos(s*6.2831853+uTime*.19))*.002;
            vec2 phoneWide=mix(vec2(.73,.32),vec2(.89,.66),s);
            vec2 phoneTall=mix(vec2(.19,.25),vec2(.78,.39),s);
            vec2 base=mix(mix(landscape,upright,portrait),mix(phoneWide,phoneTall,portrait),uPhone);
            return base+vec2(.025,.018)*(1.0-appear)+drift;
        }
        // Softbox directions and colours adapted from the upstream studio probe.
        vec3 studio(vec3 direction) {
            float roof=smoothstep(.63,.94,dot(direction,vec3(0.,.989,.148)));
            float strip=smoothstep(.77,.99,dot(direction,vec3(-.876,.341,.341)));
            float kicker=smoothstep(.78,.99,dot(direction,vec3(.794,.234,-.561)));
            return roof*vec3(1.,.985,.96)*.11+strip*vec3(.93,.96,1.)*.09+
                   kicker*vec3(1.,.93,.84)*.07;
        }
        // Oriented cuboid intersection: the front and acrylic edge are real surfaces.
        bool hitBox(vec3 ro, vec3 rd, vec3 halfSize, out float t, out vec3 normal) {
            vec3 inv=1.0/rd;
            vec3 a=(-halfSize-ro)*inv, b=(halfSize-ro)*inv;
            vec3 lo=min(a,b), hi=max(a,b);
            float nearT=max(max(lo.x,lo.y),lo.z), farT=min(min(hi.x,hi.y),hi.z);
            if (farT<max(nearT,0.0)) return false;
            t=nearT;
            vec3 h=ro+rd*t;
            vec3 edge=abs(abs(h)-halfSize);
            normal=edge.x<edge.y && edge.x<edge.z?vec3(sign(h.x),0.,0.):
                   edge.y<edge.z?vec3(0.,sign(h.y),0.):vec3(0.,0.,sign(h.z));
            return true;
        }
        void main() {
            float unit=min(uSize.x,uSize.y);
            vec2 p=(vUv-.5)*uSize/unit;
            float portrait=1.0-smoothstep(.92,1.08,uSize.x/uSize.y);
            float dark=clamp(uDark,0.,1.);
            float vignette=smoothstep(.1,1.15,length(p*vec2(.75,1.)));
            vec2 studioCenter=(vUv-vec2(.69,.38))*vec2(1.8,2.1);
            float spill=exp(-dot(studioCenter,studioCenter)*2.4);
            vec3 col=mix(vec3(.925,.918,.902)*(1.0-vignette*.12),
                         vec3(.043,.043,.047)*(1.0-vignette*.32)+spill*vec3(.020,.019,.017),dark);
            // The quiet graphite motif never intrudes on the status area.
            float motif=chemistry(p+vec2(.12,-.08+uTime*.008))*dark*.025*smoothstep(.10,.20,vUv.y);
            if (uPhone>.5) motif*=1.0-smoothstep(.82,.92,vUv.y);
            col+=motif*vec3(.56,.62,.50);
            col+=(noise(gl_FragCoord.xy)-.5)*.003;
            // The actual upstream artwork is mapped onto the 104:74.88:3.2 card.
            vec3 ro=vec3(p,3.0), rd=vec3(0.00001,0.00001,-1.0);
            float nearest=1e4;
            // Cheap soft contact shadows before surface intersections.
            for(int i=0;i<5;i++) {
                float f=float(i);
                float s=cardPhase(f);
                float appear=smoothstep(.25+f*.07,.55+f*.07,uReveal)*streamFade(s);
                vec2 uvCenter=cardCenter(s,portrait,appear);
                vec2 center=(uvCenter-.5)*uSize/unit;
                vec2 d=(p-center-vec2(.012,.018))/vec2(.16,.115);
                if (abs(d.x)<1.8 && abs(d.y)<1.8) {
                    float shadow=exp(-dot(d,d)*2.8)*appear;
                    col*=1.0-shadow*mix(.14,.30,dark);
                }
            }
            vec3 result=col;
            for(int i=0;i<5;i++) {
                float f=float(i), s=cardPhase(f);
                float appear=smoothstep(.25+f*.07,.55+f*.07,uReveal)*streamFade(s);
                vec2 uvCenter=cardCenter(s,portrait,appear);
                vec2 center=(uvCenter-.5)*uSize/unit;
                float width=mix(mix(.30,.272,portrait),mix(.24,.30,portrait),uPhone)*max(appear,.001);
                // Skip path trigonometry and intersection outside each card's bounds.
                if (abs(p.x-center.x)>width*.78 || abs(p.y-center.y)>width*.78 || appear<.001) continue;
                vec3 halfSize=vec3(width*.5,width*.36,width*(3.2/104.0)*.5);
                vec3 T=normalize(rl_path(s+.002)-rl_path(s-.002));
                vec3 F=normalize(T*.55+vec3(.42,.18,.78));
                vec3 W=normalize(cross(vec3(0.,1.,0.),F));
                vec3 H=cross(F,W);
                float roll=-.35+s*1.15+sin(uTime*.6+f)*.02;
                W=rotateAxis(W,F,roll);
                H=rotateAxis(H,F,roll);
                mat3 rotation=mat3(W,H,F);
                vec3 origin=transpose(rotation)*(ro-vec3(center,s*.125));
                vec3 direction=transpose(rotation)*rd;
                float t; vec3 n;
                if(hitBox(origin,direction,halfSize,t,n) && t<nearest) {
                    vec3 local=origin+direction*t;
                    vec2 corner=abs(local.xy)/width-vec2(.5,.36)+.05;
                    float distance=length(max(corner,0.0))+min(max(corner.x,corner.y),0.0)-.05;
                    if(distance>.00001) continue;
                    nearest=t;
                    vec3 wn=rotation*n;
                    float face=step(.5,abs(n.z));
                    vec2 uv=vec2(local.x/width+.5,.5-local.y/(width*.72));
                    float lod=max(0.0,log2(512.0/(width*unit*max(.40,F.z))));
                    vec3 art=textureLod(uCardTex,clamp(uv,0.,1.),lod).rgb;
                    vec3 key=vec3(-.379,.788,.485), fill=vec3(.853,.174,-.4925);
                    float diffuse=.80+.16*max(dot(wn,key),0.)+.08*max(dot(wn,fill),0.);
                    float glint=pow(max(dot(reflect(-key,wn),vec3(0.,0.,1.)),0.),24.);
                    vec2 uvEdge=min(uv,1.0-uv);
                    float bevel=1.0-smoothstep(.006,.032,min(uvEdge.x,uvEdge.y));
                    float reflectionStripe=(uv.x+.32*uv.y-.36)*9.0;
                    float reflection=exp(-reflectionStripe*reflectionStripe);
                    vec3 softbox=studio(reflect(vec3(0.,0.,-1.),wn));
                    vec3 frosted=art*diffuse+vec3(1.0,.97,.92)*(glint*.08+reflection*.037)+softbox*.36;
                    frosted=mix(frosted,vec3(.85,.86,.83)+softbox,bevel*.38);
                    vec3 edge=mix(vec3(.45,.50,.49),vec3(.91,.92,.89),.35+glint*.45)+softbox;
                    float coverage=mix(1.0,smoothstep(0.,1.15/(width*unit),-distance),face);
                    result=mix(result,mix(edge,frosted,face),coverage);
                }
            }
            fragColor=vec4(result,1.);
        }
        """;
}
