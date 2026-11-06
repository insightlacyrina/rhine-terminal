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
        uniform float uSeconds;
        uniform float uBurst;
        uniform float uSeverity;
        out vec4 fragColor;

        float g_hash(vec2 p) {
            vec3 p3 = fract(vec3(p.xyx) * 0.1031);
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }
        vec2 safeUv(vec2 p) { return clamp(p, vec2(0.001), vec2(0.999)); }
        void main() {
            vec2 res = max(uSize, vec2(1.0));
            vec2 uv0 = gl_FragCoord.xy / res;
            vec2 uv = uv0;
            float t = max(uSeconds, 0.0);
            float g = clamp(uSeverity, 0.0, 1.0) * smoothstep(0.18, 0.29, t);
            float burst = clamp(uBurst, 0.0, 1.0);
            float tick = floor(t * 28.0);
            // Keep the opening movement coherent before the independently torn bands.
            float impulse = (1.0 - smoothstep(0.02, 0.185, t)) * burst;
            uv += vec2(0.085, 0.018) * impulse;
            if (g > 0.001) {
                float band = floor(uv.y * 46.0);
                float on = step(1.0 - g * 0.55, g_hash(vec2(band, tick)));
                uv.x += (g_hash(vec2(band * 1.7, tick + 3.0)) - 0.5) * 0.11 * g * on;
                float br = floor(uv.y * 12.0), bc = floor(uv.x * 9.0);
                float block = step(1.0 - g * 0.18, g_hash(vec2(br + bc * 13.0, tick)));
                uv += (vec2(g_hash(vec2(bc, tick)), g_hash(vec2(br, tick))) - 0.5) * 0.04 * block;
                // A restrained wide displacement preserves the existing broad-band character.
                float broad = floor(uv0.y * 11.0);
                float bandActive = step(0.48, g_hash(vec2(broad * 7.3, tick)));
                uv.x += (g_hash(vec2(broad * 2.9, tick + 11.0)) - 0.5) * 0.025 * g * bandActive;
            }
            vec2 d = uv - 0.5;
            float ca = g * 0.012 + impulse * 0.008;
            vec4 base = texture(uScene, safeUv(uv));
            vec3 col = vec3(texture(uScene, safeUv(uv + d * ca)).r,
                            base.g,
                            texture(uScene, safeUv(uv - d * ca)).b);
            float scan = step(0.5, fract(uv.y * res.y * 0.5));
            col *= 1.0 - g * 0.18 * scan;
            col += g * 0.06 * (g_hash(vec2(floor(uv.y * res.y), tick)) - 0.5);
            float grain = g_hash(uv0 * res + fract(floor(t * 24.0) * 0.137) * 1000.0) - 0.5;
            col += grain * 0.028 * g;
            fragColor = vec4(clamp(col, 0.0, 1.0), base.a);
        }
        """;
}
