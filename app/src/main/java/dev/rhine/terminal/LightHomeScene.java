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
        float segmentDistance(vec2 p,vec2 a,vec2 b) {
            vec2 edge=b-a;
            return length(p-a-edge*clamp(dot(p-a,edge)/dot(edge,edge),0.,1.));
        }
        float fineLine(float distance,float pixel) {
            return 1.-smoothstep(pixel*.35,pixel*1.25,distance);
        }
        float atom(vec2 p,vec2 center,float radius,float pixel) {
            return 1.-smoothstep(radius,radius+pixel,length(p-center));
        }
        vec2 turn(vec2 p,float angle) {
            float c=cos(angle),s=sin(angle);
            return vec2(c*p.x-s*p.y,s*p.x+c*p.y);
        }
        // Two isolated fragments occupy the open margin; neither is tiled.
        float ringFragment(vec2 p,vec2 center,float angle,float scale,float pixel) {
            vec2 q=turn(p-center,angle)/scale;
            if (max(abs(q.x),abs(q.y))>1.6) return 0.;
            const vec2 nodes[5]=vec2[5](vec2(-.64,-.29),vec2(-.05,-.74),
                    vec2(.56,-.30),vec2(.33,.47),vec2(-.49,.57));
            float distance=1.;
            float dots=0.;
            for(int i=0;i<5;i++) {
                distance=min(distance,segmentDistance(q,nodes[i],nodes[(i+1)%5]));
                dots=max(dots,atom(q,nodes[i],.040,pixel/scale));
            }
            vec2 tip=vec2(1.25,-.66);
            distance=min(distance,segmentDistance(q,nodes[2],tip));
            float openAtom=fineLine(abs(length(q-tip)-.065)*scale,pixel);
            return max(max(fineLine(distance*scale,pixel),dots),openAtom);
        }
        float chainFragment(vec2 p,vec2 center,float angle,float scale,float pixel) {
            vec2 q=turn(p-center,angle)/scale;
            if (max(abs(q.x),abs(q.y))>1.35) return 0.;
            vec2 a=vec2(-.37,-.73),b=vec2(-.58,-.05),c=vec2(.05,.34),d=vec2(.58,.06);
            float distance=min(segmentDistance(q,a,b),segmentDistance(q,b,c));
            distance=min(distance,segmentDistance(q,c,d));
            float dots=max(atom(q,a,.048,pixel/scale),atom(q,b,.043,pixel/scale));
            dots=max(dots,atom(q,c,.040,pixel/scale));
            float openAtom=fineLine(abs(length(q-d)-.060)*scale,pixel);
            return max(max(fineLine(distance*scale,pixel),dots),openAtom);
        }
        float trajectoryY(float x,float phase) {
            return .36-.19*x-.22*x*x+.025*sin(phase+.7);
        }
        vec3 scientificField(vec2 p,float portrait,float dark,float unit) {
            float pixel=1./unit;
            // Every animated coordinate closes over the same two-minute period.
            float phase=6.28318530718*mod(uTime,120.)/120.;
            float x=p.x+.014*sin(phase);
            float y=-.14-.34*x-.38*x*x*x+.012*cos(phase);
            float slope=-.34-1.14*x*x;
            float distance=(p.y-y)/sqrt(1.+slope*slope);
            float field=fineLine(abs(distance),pixel)*.025;
            field+=fineLine(abs(distance-.13),pixel)*.012;
            // A broad, shallow spill makes the field readable as part of the stage.
            field+=(1.-smoothstep(.01,.18,abs(distance-.045)))*.008;
            float lower=(p.y-(.53-.31*x-.23*x*x*x))/sqrt(1.+pow(-.31-.69*x*x,2.));
            field+=fineLine(abs(lower),pixel)*.016;

            float trailSlope=-.19-.44*p.x;
            float trailDistance=abs(p.y-trajectoryY(p.x,phase))/sqrt(1.+trailSlope*trailSlope);
            float dashes=1.-smoothstep(.26,.32,abs(fract(p.x*95.+.2*sin(phase))-.5));
            float trailFade=1.-smoothstep(.48,.95,abs(p.x+.23));
            field+=fineLine(trailDistance,pixel)*dashes*trailFade*.040;
            float headX=.60*sin(phase+.65);
            field+=atom(p,vec2(headX,trajectoryY(headX,phase)),pixel*1.45,pixel)*.075;

            vec2 aspect=uSize/unit;
            vec2 ringCenter=(mix(vec2(.14,.77),vec2(.18,.72),portrait)-.5)*aspect;
            vec2 chainCenter=(mix(vec2(.86,.17),vec2(.84,.20),portrait)-.5)*aspect;
            ringCenter+=vec2(sin(phase+.3),cos(phase+.3))*.007;
            chainCenter+=vec2(cos(phase-.4),sin(phase-.4))*.005;
            field+=ringFragment(p,ringCenter,.18+.045*sin(phase),.049,pixel)*.070;
            field+=chainFragment(p,chainCenter,-.28+.035*cos(phase),.034,pixel)*.065;
            float margin=smoothstep(.075,.15,vUv.y)*(1.-smoothstep(.91,.98,vUv.y));
            vec3 contrast=mix(vec3(.75,.73,.69),vec3(.58,.61,.57),dark);
            return contrast*min(field*margin,.09);
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
            // Subtle field lines and two scattered fragments share both gallery themes.
            vec3 field=scientificField(p,portrait,dark,unit);
            col+=mix(-field,field,dark);
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
