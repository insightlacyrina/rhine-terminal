// SPDX-License-Identifier: AGPL-3.0-or-later
// Derived from frontend/src/lib/scene/shaders.ts (gradeFragment),
// Rhine Lock reference commit a4df68c6bd9e01ecb7660659becf495957e57faa.
package dev.rhine.terminal;

/** Full-frame scene disturbance for the denial sequence; Canvas text is composited later. */
public final class LockBlackoutScene {
    private LockBlackoutScene() {}

    public static final String FRAGMENT_SHADER = """
        #version 300 es
        precision highp float;
        uniform sampler2D uScene;
        uniform vec2 uSize;
        uniform float uSeconds, uBurst, uSeverity;
        out vec4 fragColor;
        float hash2(vec2 p) {
            vec3 q=fract(vec3(p.xyx)*.1031); q+=dot(q,q.yzx+33.33);
            return fract((q.x+q.y)*q.z);
        }
        vec2 safeUv(vec2 p) { return clamp(p,vec2(.001),vec2(.999)); }
        void main() {
            vec2 uv=gl_FragCoord.xy/max(uSize,vec2(1));
            float g=clamp(uSeverity,0.0,1.0), tick=floor(uSeconds*28.0);
            float band=floor(uv.y*46.0);
            float on=step(1.0-g*.55,hash2(vec2(band,tick)));
            uv.x+=(hash2(vec2(band*1.7,tick+3.0))-.5)*.11*g*on;
            float br=floor(uv.y*12.0),bc=floor(uv.x*9.0);
            float block=step(1.0-g*.18,hash2(vec2(br+bc*13.0,tick)));
            uv+=(vec2(hash2(vec2(bc,tick)),hash2(vec2(br,tick)))-.5)*.04*block;
            vec2 d=uv-.5; float ca=g*.012;
            vec4 base=texture(uScene,safeUv(uv));
            vec3 col=vec3(texture(uScene,safeUv(uv+d*ca)).r,base.g,texture(uScene,safeUv(uv-d*ca)).b);
            float scan=step(.5,fract(uv.y*uSize.y*.5));
            col*=1.0-g*.18*scan;
            col+=g*.06*(hash2(vec2(floor(uv.y*uSize.y),tick))-.5);
            fragColor=vec4(clamp(col,0.0,1.0),base.a);
        }
        """;

}
