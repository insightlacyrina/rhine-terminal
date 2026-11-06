package dev.rhine.terminal;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** A beveled, solid ribbon following a rounded rectangular path around the display. */
public final class HomeSculptureScene {
    public static final int STRIDE_BYTES = 6 * Float.BYTES;
    public static final int LONGITUDINAL_STEPS = 512;
    private static final float[][] PROFILE = {
        {-1,-.70f},{-.98f,-.94f},{-.94f,-1},{0,-1},{.94f,-1},{.98f,-.94f},{1,-.70f},
        {1,.70f},{.98f,.94f},{.94f,1},{0,1},{-.94f,1},{-.98f,.94f},{-1,.70f}
    };

    private HomeSculptureScene() {}

    public static final class Mesh {
        public final FloatBuffer vertices;
        public final IntBuffer indices;
        public final int indexCount;

        private Mesh(FloatBuffer vertices, IntBuffer indices, int indexCount) {
            this.vertices = vertices;
            this.indices = indices;
            this.indexCount = indexCount;
        }
    }

    /** Packs perimeter fraction, profile q/r, reserved band, and the outward profile normal. */
    public static Mesh createMesh() {
        int cross = PROFILE.length;
        int rings = LONGITUDINAL_STEPS + 1;
        int count = LONGITUDINAL_STEPS * cross * 6;
        FloatBuffer vertices = ByteBuffer.allocateDirect(rings * cross * STRIDE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        IntBuffer indices = ByteBuffer.allocateDirect(count * Integer.BYTES)
                .order(ByteOrder.nativeOrder()).asIntBuffer();
        for (int i = 0; i < rings; i++) {
            float perimeter = i / (float) LONGITUDINAL_STEPS;
            for (int j = 0; j < cross; j++) {
                float[] previous = PROFILE[(j + cross - 1) % cross];
                float[] next = PROFILE[(j + 1) % cross];
                float normalQ = next[1] - previous[1];
                float normalR = previous[0] - next[0];
                float length = (float) Math.hypot(normalQ, normalR);
                vertices.put(perimeter).put(PROFILE[j][0]).put(PROFILE[j][1]).put(0f)
                        .put(normalQ / length).put(normalR / length);
            }
        }
        for (int i = 0; i < LONGITUDINAL_STEPS; i++) {
            for (int j = 0; j < cross; j++) {
                int a = i * cross + j;
                int b = i * cross + (j + 1) % cross;
                int c = a + cross;
                int d = b + cross;
                indices.put(a).put(b).put(c).put(b).put(d).put(c);
            }
        }
        vertices.flip();
        indices.flip();
        return new Mesh(vertices, indices, count);
    }

    public static final String VERTEX_SHADER = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec4 aSurface;
        layout(location=1) in vec2 aProfileNormal;
        uniform vec2 uSize;
        uniform float uTime;
        uniform float uReveal;
        uniform vec2 uTilt;
        out vec3 vNormal;
        out vec2 vRibbon;
        out float vDepth;
        out float vWarmth;
        const float PI=3.14159265359;
        const float TAU=6.28318530718;
        const float INSET=115.0;
        const float RADIUS=140.0;

        void corner(vec2 center,float angle,out vec2 point,out vec2 tangent) {
            vec2 radial=vec2(cos(angle),sin(angle));
            point=center+radial*RADIUS;
            tangent=vec2(-radial.y,radial.x);
        }

        // Clockwise traversal in logical pixels, with tangent and curvature for the normal.
        void perimeter(vec2 size,float s,out vec2 point,out vec2 tangent,
                       out float curvature,out float pathLength) {
            float low=INSET+RADIUS;
            vec2 high=size-vec2(low);
            float horizontal=high.x-low;
            float vertical=high.y-low;
            float arc=RADIUS*PI*.5;
            pathLength=2.0*(horizontal+vertical)+4.0*arc;
            float distanceAlong=fract(s)*pathLength;
            curvature=0.0;
            if(distanceAlong<horizontal) {
                point=vec2(low+distanceAlong,INSET); tangent=vec2(1,0); return;
            }
            distanceAlong-=horizontal;
            if(distanceAlong<arc) {
                corner(vec2(high.x,low),distanceAlong/RADIUS-PI*.5,point,tangent);
                curvature=1.0/RADIUS; return;
            }
            distanceAlong-=arc;
            if(distanceAlong<vertical) {
                point=vec2(size.x-INSET,low+distanceAlong); tangent=vec2(0,1); return;
            }
            distanceAlong-=vertical;
            if(distanceAlong<arc) {
                corner(high,distanceAlong/RADIUS,point,tangent);
                curvature=1.0/RADIUS; return;
            }
            distanceAlong-=arc;
            if(distanceAlong<horizontal) {
                point=vec2(high.x-distanceAlong,size.y-INSET); tangent=vec2(-1,0); return;
            }
            distanceAlong-=horizontal;
            if(distanceAlong<arc) {
                corner(vec2(low,high.y),distanceAlong/RADIUS+PI*.5,point,tangent);
                curvature=1.0/RADIUS; return;
            }
            distanceAlong-=arc;
            if(distanceAlong<vertical) {
                point=vec2(INSET,high.y-distanceAlong); tangent=vec2(0,-1); return;
            }
            distanceAlong-=vertical;
            corner(vec2(low),distanceAlong/RADIUS+PI,point,tangent);
            curvature=1.0/RADIUS;
        }

        void main() {
            // A 1600px reference short side keeps the border proportional in either orientation.
            vec2 size=uSize*(1600.0/max(1.0,min(uSize.x,uSize.y)));
            float s=aSurface.x,q=aSurface.y,r=aSurface.z;
            vec2 point,tangent;
            float curvature,pathLength;
            perimeter(size,s,point,tangent,curvature,pathLength);
            vec2 inward=vec2(-tangent.y,tangent.x);
            float phase=TAU*fract(s);
            float a=phase*6.0-uTime*.31;
            float b=phase*11.0+uTime*.19;
            float c=phase*3.0-uTime*.13;
            float sa=sin(a),ca=cos(a),sb=sin(b),cb=cos(b),sc=sin(c),cc=cos(c);
            float twist=.31+1.16*sa+.46*sb+.31*sc;
            float twistSlope=TAU*(6.96*ca+5.06*cb+.93*cc);
            float ct=cos(twist),st=sin(twist);
            float halfWidth=72.0+18.0*sin(c+.9)+7.0*sin(a*2.0-.3);
            float widthSlope=TAU*(54.0*cos(c+.9)+84.0*cos(a*2.0-.3));
            float halfThickness=3.2;
            // A shallow crosswise bow catches soft light across the broad faces.
            float camberStrength=8.5+3.5*sc;
            float profileDepth=r*halfThickness+(1.0-q*q)*camberStrength;
            float profileDepthSlope=(1.0-q*q)*10.5*cc*TAU;
            vec2 tilt=clamp(uTilt,vec2(-1),vec2(1));
            float inwardTilt=3.0*dot(tilt,inward);
            float inset=13.0*sin(c-.4)+7.0*sin(b+.7)+inwardTilt
                        -350.0*(1.0-clamp(uReveal,0.0,1.0));
            float insetSlope=TAU*(39.0*cos(c-.4)+77.0*cos(b+.7))
                             -3.0*dot(tilt,tangent)*curvature*pathLength;
            float depth=18.0*sin(a-.3)+9.0*sin(b+.6);
            float depthSlope=TAU*(108.0*cos(a-.3)+99.0*cos(b+.6));
            float offset=inset+q*halfWidth*ct-profileDepth*st;
            float z=depth+q*halfWidth*st+profileDepth*ct;
            vec2 xy=point+inward*offset;

            float offsetSlope=insetSlope+q*(widthSlope*ct-halfWidth*st*twistSlope)
                              -profileDepthSlope*st-profileDepth*ct*twistSlope;
            float zSlope=depthSlope+q*(widthSlope*st+halfWidth*ct*twistSlope)
                         +profileDepthSlope*ct-profileDepth*st*twistSlope;
            vec3 along=vec3(tangent*pathLength*(1.0-curvature*offset)+inward*offsetSlope,zSlope);
            vec3 wide=vec3(inward*ct,st);
            vec3 thick=vec3(-inward*st,ct);
            vec3 across=wide*(-aProfileNormal.y*halfWidth)
                       +thick*(aProfileNormal.x*halfThickness+2.0*q*aProfileNormal.y*camberStrength);
            vNormal=normalize(cross(across,along));
            vRibbon=vec2(s,q);
            vDepth=z;
            vWarmth=.5+.5*sin(c+.7);
            gl_Position=vec4(xy.x/size.x*2.0-1.0,1.0-xy.y/size.y*2.0,-z/260.0,1.0);
        }
        """;

    public static final String FRAGMENT_SHADER = """
        #version 300 es
        precision highp float;
        uniform vec2 uSize;
        in vec3 vNormal;
        in vec2 vRibbon;
        in float vDepth;
        in float vWarmth;
        out vec4 fragColor;

        void main() {
            vec2 edge=min(gl_FragCoord.xy,uSize-gl_FragCoord.xy);
            // Safety bound for alternate aspect ratios and future motion changes.
            if(min(edge.x,edge.y)>.1875*min(uSize.x,uSize.y)) discard;
            vec3 n=normalize(vNormal);
            const vec3 key=vec3(-.350,-.480,.804);
            const vec3 fill=vec3(.771,.548,.325);
            float facing=dot(n,key);
            float wrap=facing*.5+.5;
            float bounce=max(dot(n,fill),0.0);
            float light=.38+.47*max(facing,0.0)+.15*wrap+.12*bounce;
            float ao=.78+.22*smoothstep(-48.0,46.0,vDepth);
            float side=abs(vRibbon.y);
            vec3 base=mix(vec3(.213,.237,.177),vec3(.344,.343,.269),vWarmth*.74);
            float repeat=vRibbon.x*460.0;
            float ribWidth=max(fwidth(repeat),.025);
            float rib=1.0-smoothstep(.057,.057+ribWidth,abs(fract(repeat)-.5));
            rib*=1.0-smoothstep(.29,.68,ribWidth);
            base*=1.0-.20*rib;
            float seam=1.0-smoothstep(.012,.016+fwidth(vRibbon.y),abs(vRibbon.y-.84));
            base=mix(base,vec3(.417,.354,.202),seam*.68);
            base=mix(base,vec3(.125,.142,.096),smoothstep(.945,1.0,side)*.50);
            float rim=1.0-abs(n.z);
            vec3 color=base*light*ao+vec3(.038,.040,.025)*rim*rim;
            fragColor=vec4(color,1.0);
        }
        """;
}
