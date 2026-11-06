package dev.rhine.terminal;

/** Shared background for the live wallpaper and desktop preview. */
public final class HomeBackgroundScene {
    private HomeBackgroundScene() {}

    public static final String VERTEX_SHADER = """
        #version 300 es
        precision highp float;
        void main() {
            vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
            gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
        }
        """;

    public static final String FRAGMENT_SHADER = """
        #version 300 es
        precision highp float;
        uniform vec2 uSize;
        uniform float uTime;
        uniform float uReveal;
        uniform sampler2D uTelemetry;
        uniform float uTelemetryOn;
        uniform vec4 uEdgeInsets;
        out vec4 fragColor;

        float stroke(float distancePx, float halfWidth) {
            return 1.0 - smoothstep(halfWidth, halfWidth + .85, abs(distancePx));
        }

        void main() {
            vec2 screen = vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y) / uSize;
            vec2 uv = (screen-uEdgeInsets.xy)/(1.0-uEdgeInsets.xy-uEdgeInsets.zw);
            vec2 p = uv*uSize;
            float unit = min(uSize.x, uSize.y) / 1600.0;
            vec2 local = min(p, uSize - p);
            float edge = min(local.x, local.y) / unit;
            float rails = smoothstep(.10, .64, uReveal);
            float details = smoothstep(.57, 1.0, uReveal);
            float sweep = smoothstep(.04, .50, uReveal);
            float vignette = 1.0 - smoothstep(.10, .68, length((uv - .5) * vec2(1.0, .84)));
            float nearEdge = 1.0 - smoothstep(100.0, 390.0, edge);
            vec3 ink = vec3(.040, .050, .043) + vec3(.009, .012, .008) * vignette;
            ink += vec3(.025, .027, .016) * nearEdge * sweep;

            if (edge < 324.0) {
                float along = local.x < local.y ? p.y : p.x;
                float longTick = step(.92, fract(along / (unit * 74.0)));
                float minorTick = step(.89, fract(along / (unit * 18.5)));
                float rule = stroke(edge - 25.0, .45) * .18;
                rule += stroke(edge - 297.0, .32) * .20;
                rule += stroke(edge - 43.0, 1.0 + longTick * 2.0) * minorTick * .45;
                float scan = 1.0 - smoothstep(.008, .034, abs(fract(along / (unit * 580.0) - uTime / 6.0) - .5));
                rule += stroke(edge - 297.0, .47) * scan * .53;
                ink += vec3(.175, .189, .120) * rule * rails;
            }

            float cross = smoothstep(.35, .53, uReveal);
            float live = .82 + .18 * cos(uTime * .62831853);
            float run = 66.0 + 27.0 * sin(uTime * .31415927);
            float centerX = abs(p.x - .5 * uSize.x) / unit;
            float centerY = (p.y - .5 * uSize.y) / unit;
            float segment = smoothstep(111.0, 116.0, centerX) * (1.0 - smoothstep(116.0 + run * cross, 123.0 + run * cross, centerX));
            float guide = stroke(centerY, .25) * segment;
            guide += stroke(centerX - 115.0, .34) * (1.0 - smoothstep(5.0, 7.0, abs(centerY)));
            ink += vec3(.054, .064, .046) * guide * cross * live;

            if (uTelemetryOn > .5) {
                float x = p.x / unit;
                if (x >= 266.0 && x < 291.0 && local.y > 312.0 * unit) {
                    float offset = fract(p.y / (unit * 756.0) - uTime / 20.0);
                    vec2 glyph = vec2(offset, (291.0 - x) / 25.0);
                    float alpha = texture(uTelemetry, glyph).a;
                    float fade = smoothstep(312.0, 376.0, local.y / unit);
                    ink = mix(ink, vec3(.51, .55, .37), alpha * fade * details * .72);
                }
            }

            if (uEdgeInsets.y > 0.0) {
                float margin = smoothstep(0.0,.02,min(min(uv.x,uv.y),min(1.0-uv.x,1.0-uv.y)));
                ink=mix(vec3(.040,.050,.043),ink,margin);
            }
            fragColor = vec4(ink, 1.0);
        }
        """;
}
