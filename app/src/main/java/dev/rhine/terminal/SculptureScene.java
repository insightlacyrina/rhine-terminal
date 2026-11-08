/* Derived from rhine-lock frontend/src/lib/scene/{Stage.ts,shaders.ts},
 * Copyright the rhine-lock contributors, AGPL-3.0-or-later.
 * https://github.com/tmc5csxr9m-cpu/rhine-lock, commit a4df68c6bd9e01ecb7660659becf495957e57faa.
 * Native GLES adaptation. */
package dev.rhine.terminal;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class SculptureScene {
    private SculptureScene() {}
    public static final int STRIDE_BYTES = 6 * Float.BYTES;
    public static final int FIN_COUNT = 400;
    public static final class Mesh {
        public final FloatBuffer vertices;
        public final IntBuffer indices;
        public final int indexCount;
        private Mesh(FloatBuffer vertices, IntBuffer indices, int count) {
            this.vertices=vertices; this.indices=indices; this.indexCount=count;
        }
    }
    /** Unit box with independent face normals, equivalent to Three.js BoxGeometry. */
    public static Mesh createMesh() {
        float[] v = {
            .5f,-.5f,-.5f, 1,0,0,  .5f,.5f,-.5f, 1,0,0,  .5f,.5f,.5f, 1,0,0,  .5f,-.5f,.5f, 1,0,0,
            -.5f,-.5f,.5f, -1,0,0,  -.5f,.5f,.5f, -1,0,0,  -.5f,.5f,-.5f, -1,0,0,  -.5f,-.5f,-.5f, -1,0,0,
            -.5f,.5f,-.5f, 0,1,0,  -.5f,.5f,.5f, 0,1,0,  .5f,.5f,.5f, 0,1,0,  .5f,.5f,-.5f, 0,1,0,
            -.5f,-.5f,.5f, 0,-1,0,  -.5f,-.5f,-.5f, 0,-1,0,  .5f,-.5f,-.5f, 0,-1,0,  .5f,-.5f,.5f, 0,-1,0,
            .5f,-.5f,.5f, 0,0,1,  .5f,.5f,.5f, 0,0,1,  -.5f,.5f,.5f, 0,0,1,  -.5f,-.5f,.5f, 0,0,1,
            -.5f,-.5f,-.5f, 0,0,-1,  -.5f,.5f,-.5f, 0,0,-1,  .5f,.5f,-.5f, 0,0,-1,  .5f,-.5f,-.5f, 0,0,-1
        };
        FloatBuffer vertices=ByteBuffer.allocateDirect(v.length*Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertices.put(v).flip();
        IntBuffer indices=ByteBuffer.allocateDirect(36*Integer.BYTES).order(ByteOrder.nativeOrder()).asIntBuffer();
        for(int f=0;f<6;f++) { int i=f*4; indices.put(i).put(i+1).put(i+2).put(i).put(i+2).put(i+3); }
        indices.flip();
        return new Mesh(vertices,indices,36);
    }
    public static final String VERTEX_SHADER = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPosition;
        layout(location=1) in vec3 aNormal;
        uniform mat4 uMvp, uModel;
        uniform float uTime, uPulse, uBirth, uVerify, uExit, uAlert, uDisorder;
        out vec3 vLocal, vNormal, vPosition;
        out float vSeed, vPulse, vParam, vTip;
        const float uCount=400.0, uRadius=285.0, uAspect=0.95, uDepth=95.0, uTwist=2.0;
        const float uThickness=4.2, uLean=0.62, uCorner=0.14, uWidth=84.0, uHeight=84.0;
        #define uFlow (uTime*1.6)
        #define uSpin (uTime*0.05)
        #define uAssemble uBirth
        #define uDissolve uExit
        #define uAlign (uVerify*0.5)
        
        float rl_hash11(float p) {
            p = fract(p * 0.1031);
            p *= p + 33.33;
            p *= p + p;
            return fract(p);
        }
        vec3 rl_rotateAxis(vec3 v, vec3 axis, float a) {
            float s = sin(a), c = cos(a);
            return v * c + cross(axis, v) * s + axis * dot(axis, v) * (1.0 - c);
        }
        vec3 rl_toLinear(vec3 c) { return c * (c * (c * 0.305306011 + 0.682171111) + 0.012522878); }
        
        
        
        vec3 rl_curve(float t) {
            float s = sin(t), c = cos(t);
            // Gerono figure-eight; the branches separate in depth so the band passes over itself
            return vec3(uRadius * c, uRadius * uAspect * s * c, uDepth * s);
        }
        float rl_easeOutBack(float x) {
            float c1 = 1.70158, shifted = x - 1.0;
            return 1.0 + shifted * shifted * ((c1 + 1.0) * shifted + c1);
        }
        
        void rl_finTransform(in vec3 lp, in vec3 nrm, out vec3 worldPos, out vec3 worldNrm) {
            float idx = float(gl_InstanceID);
            float seed = rl_hash11(idx * 1.618 + 4.7);
            float t = 6.2831853 * fract((idx + uFlow) / uCount);
        
            vec3 p = rl_curve(t);
            vec3 T = normalize(rl_curve(t + 0.002) - rl_curve(t - 0.002));
            vec3 ref = vec3(0.0, 0.0, 1.0);
            vec3 N0 = normalize(ref - dot(ref, T) * T);
            vec3 B0 = cross(T, N0);
        
            // The Android touch timeline supplies a single traveling pulse.
            float wavefront = sin(t * 0.5 - uTime * 1.8326);
            float pulse = min(uPulse * exp(-wavefront * wavefront * 6.25), 1.4);
        
            float wave = sin(2.0 * t - uTime * 0.55) * 0.55 + sin(5.0 * t + uTime * 0.31) * 0.12;
            float phi = uTwist * t + wave + uSpin + pulse * 1.3;
            // Integer winding closes the tilted fin frame. Scan alignment must keep that winding.
            phi += uAlign * sin(1.5707963 - phi);
            phi += (seed - 0.5) * uDisorder * 3.0 + sin(uTime * 9.0 + seed * 40.0) * uDisorder * 0.2;
        
            vec3 W = cos(phi) * N0 + sin(phi) * B0;
            vec3 H = cross(T, W);
            float lean = uLean + sin(t * 3.0 + uTime * 0.4) * 0.12;
            vec3 Tl = cos(lean) * T + sin(lean) * H;
            H = cross(Tl, W);
            T = Tl;
        
            // assembly: drawn like the logo's pen stroke from the crossing
            float along = fract(t / 6.2831853 - 0.25);
            float window = 0.085;
            float local = clamp((uAssemble * (1.0 + window) - along) / window, 0.0, 1.0);
            float settle = 1.0 - pow(1.0 - local, 3.0);
            float grow = local > 0.0 ? rl_easeOutBack(local) : 0.0;
            float swing = (1.0 - settle) * 1.1;
            W = rl_rotateAxis(W, T, swing);
            H = rl_rotateAxis(H, T, swing);
        
            // unlock: cards peel off the band in a wave from the crossing and fly
            // outward, tumbling, while they shrink away
            float peel = clamp(uDissolve * 1.6 - along * 0.6 - seed * 0.25, 0.0, 1.0);
            float peelE = peel * peel * (3.0 - 2.0 * peel);
            vec3 axis = normalize(vec3(seed - 0.5, 0.6, 0.3 + seed));
            W = rl_rotateAxis(W, axis, peelE * (2.0 + seed * 4.0));
            H = rl_rotateAxis(H, axis, peelE * (2.0 + seed * 4.0));
            vec3 Tt = cross(W, H);
        
            vec3 size = vec3(uThickness, uWidth * (1.0 + pulse * 0.35), uHeight * (1.0 + pulse * 0.9)) * grow;
            size *= 1.0 - peelE * 0.85;
        
            vec3 radial = normalize(vec3(p.x, p.y * 1.4, p.z + 120.0));
            vec3 centre = p + H * (pulse * 7.0 + (1.0 - settle) * 16.0)
                        + (radial * 520.0 + vec3(0.0, 0.0, 380.0)) * peelE * (0.6 + seed);
        
            worldPos = centre + Tt * lp.x * size.x + W * lp.y * size.y + H * lp.z * size.z;
            worldNrm = normalize(Tt * nrm.x + W * nrm.y + H * nrm.z);
        
            float dt = along - uAssemble * (1.0 + window) + window * 0.5;
            vTip = exp(-dt * dt * 2600.0) * (1.0 - smoothstep(0.9, 1.0, uAssemble));
            vLocal = lp;
            vSeed = seed;
            vPulse = pulse;
            vParam = t / 6.2831853;
        }
        
        void main() {
            vec3 p, n;
            rl_finTransform(aPosition,aNormal,p,n);
            p /= 285.0;
            vec4 world=uModel*vec4(p,1.0);
            vPosition=world.xyz;
            vNormal=mat3(uModel)*n;
            gl_Position=uMvp*vec4(p,1.0);
        }
        """;

    public static final String FRAGMENT_SHADER = """
        #version 300 es
        precision highp float;
        in vec3 vLocal, vNormal, vPosition;
        in float vSeed, vPulse, vParam, vTip;
        uniform float uAlert, uTime, uVerify, uIdleBlack;
        out vec4 fragColor;
        
        float rl_hash11(float p) {
            p = fract(p * 0.1031);
            p *= p + 33.33;
            p *= p + p;
            return fract(p);
        }
        vec3 rl_rotateAxis(vec3 v, vec3 axis, float a) {
            float s = sin(a), c = cos(a);
            return v * c + cross(axis, v) * s + axis * dot(axis, v) * (1.0 - c);
        }
        vec3 rl_toLinear(vec3 c) { return c * (c * (c * 0.305306011 + 0.682171111) + 0.012522878); }
        
        void main() {
        
            {
                vec2 rc = max(abs(vLocal.yz) - vec2(0.5 - 0.14), 0.0);
                if (length(rc) > 0.14) discard;
            }
        
            vec2 rq = abs(vLocal.yz) * 2.0;
            float rm = max(rq.x, rq.y);
            float onFace = step(0.49, abs(vLocal.x));
            float bevel = smoothstep(0.86, 1.0, rm);
            float rim = onFace * smoothstep(0.90, 0.94, rm) * (1.0 - smoothstep(0.97, 0.995, rm));
        
            vec3 rlPaper = rl_toLinear(vec3(0.935, 0.895, 0.830));
            vec3 rlGraphite = rl_toLinear(vec3(0.06, 0.062, 0.068));
            vec3 rlBody = mix(rlPaper, rlGraphite, uAlert) * (0.95 + vSeed * 0.07);
        
            vec3 n=normalize(vNormal);
            if (!gl_FrontFacing) n=-n;
            vec3 key=normalize(vec3(-0.379,0.788,0.485));
            vec3 fill=normalize(vec3(0.853,0.174,-0.4925));
            float diffuse=max(dot(n,key),0.0), bounce=max(dot(n,fill),0.0);
            vec3 view=normalize(vec3(0.0,0.0,4.4)-vPosition);
            float spec=pow(max(dot(reflect(-key,n),view),0.0),mix(10.0,24.0,bevel));
            vec3 orange=rl_toLinear(vec3(1.0,0.482,0.0));
            vec3 lime=rl_toLinear(vec3(0.725,0.894,0.196));
            float scan=smoothstep(0.55,1.0,sin(vParam*6.2831853*3.0-uTime*2.4));
            // The held black rest keeps the graphite finish without the denial scan.
            float warning=uAlert*(1.0-clamp(uIdleBlack,0.0,1.0));
            vec3 glow=rim*orange*(0.25+1.6*scan)*warning;
            glow+=vPulse*rim*mix(lime*0.9,orange*1.4,warning)*(1.0-uIdleBlack);
            glow+=uVerify*rim*orange*0.8*step(0.55,fract(vParam*18.0-uTime*1.2))*(1.0-uIdleBlack);
            glow+=vTip*rim*vec3(1.0,0.92,0.8)*1.4;
            vec3 linear=rlBody*(0.48+0.66*diffuse+0.12*bounce)+vec3(spec*0.12)+glow;
            // Analytic studio reflections approximate the upstream rough dielectric
            // environment on dark fins without an HDR cubemap or a second pass.
            // Broad faces use a softer lobe; the upstream bevel is less rough.
            vec3 reflected=reflect(-view,n);
            float softbox=pow(max(dot(reflected,normalize(vec3(-0.48,0.72,0.50))),0.0),mix(7.0,18.0,bevel));
            float fillbox=pow(max(dot(reflected,normalize(vec3(0.70,0.22,0.68))),0.0),mix(5.0,12.0,bevel));
            float fresnel=pow(1.0-max(dot(n,view),0.0),5.0);
            vec3 reflectedLight=vec3(0.085,0.082,0.078)*softbox
                              +vec3(0.030,0.035,0.043)*fillbox
                              +vec3(0.012,0.014,0.018)*fresnel;
            linear+=reflectedLight*clamp(uAlert,0.0,1.0);
            linear=max(linear,vec3(0.0));
            fragColor=vec4(clamp(pow(linear,vec3(1.0/2.2)),0.0,1.0),1.0);
        }
        """;

    public static final String PLANE_VERTEX_SHADER = """
        #version 300 es
        precision highp float;
        out vec2 vUv;
        void main() {
            vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));
            vUv=p;
            gl_Position=vec4(p*2.0-1.0,0,1);
        }
        """;

    public static final String PLANE_FRAGMENT_SHADER = """
        #version 300 es
        precision highp float;
        uniform sampler2D uUi;
        uniform float uAlert;
        uniform float uTime;
        uniform vec4 uViewport;
        in vec2 vUv;
        out vec4 fragColor;
        void main() {
            vec3 paper=texture(uUi,vec2(vUv.x,1.0-vUv.y)).rgb;
            // Normal state is one texture read; reserve the full palette transform for the effect.
            if(uAlert<=.001) { fragColor=vec4(paper,1.0); return; }
            float luminance=dot(paper,vec3(.2126,.7152,.0722));
            float ink=1.0-smoothstep(.22,.78,luminance);
            vec3 dark=mix(vec3(.035,.043,.039),vec3(.76,.78,.72),ink*.93);
            float saturation=max(paper.r,max(paper.g,paper.b))-min(paper.r,min(paper.g,paper.b));
            dark=mix(dark,vec3(.64,.29,.105),smoothstep(.08,.28,saturation)*.8);
            // This exact palette swatch is reserved for a dark backplate below white One UI text.
            float nativeBackplate=1.0-smoothstep(.003,.007,distance(paper,vec3(37.0,41.0,32.0)/255.0));
            dark=mix(dark,vec3(.055,.060,.050),nativeBackplate);
            vec3 color=mix(paper,dark,uAlert);
            vec2 pixel=vUv*uViewport.xy;
            vec2 edge=min(pixel,uViewport.xy-pixel);
            float border=1.0-smoothstep(1.0,2.8,min(edge.x,edge.y));
            color=mix(color,vec3(.61,.255,.085),border*uAlert);
            fragColor=vec4(color,1.0);
        }
        """;
}
