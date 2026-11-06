// SPDX-License-Identifier: AGPL-3.0-or-later
// Snapshot flight adapted from Rhine Lock frontend/src/lib/transition.ts.
package dev.rhine.terminal;

/** Projects the theme's gallery onto the upstream reversible flight path. */
public final class LockSnapshotScene {
    private LockSnapshotScene() {}

    public static final String VERTEX_SHADER = LightHomeScene.VERTEX_SHADER.replace("vUv", "vScreenUv");
    public static final String FRAGMENT_SHADER = LightHomeScene.FRAGMENT_SHADER
            .replace("in vec2 vUv;", """
                    in vec2 vScreenUv;
                    vec2 vUv;
                    uniform vec4 uFlight;
                    uniform float uDesat;
                    """)
            .replace("void main()", "void drawGallery()") + """
            void main() {
                float e=clamp(uFlight.x,0.0,1.0), opacity=uFlight.y, scan=uFlight.z, corners=uFlight.w;
                bool portrait=uSize.y>uSize.x;
                vec2 target=portrait?vec2(.325,.425):vec2(.2535,.430);
                float scale=exp(log(portrait?.13:.197)*e);
                vec2 control=vec2(mix(.5,target.x,.45),min(.5,target.y)-.16);
                float back=1.0-e;
                vec2 center=(back*back*.5+2.0*back*e*control+e*e*target)*uSize;
                vec2 pixel=vScreenUv*uSize-center;
                float arc=sin(3.14159265*e);
                float tilt=-.2268928*arc, depth=1.125*min(uSize.x,uSize.y);
                float x=pixel.x/max(.1,cos(tilt)-pixel.x*sin(tilt)/depth);
                vec2 card=vec2(x,pixel.y*(1.0+x*sin(tilt)/depth));
                vec2 halfSize=uSize*(.5*scale);
                float radius=min(min(halfSize.x,halfSize.y),18.0*arc);
                vec2 bound=abs(card)-halfSize+radius;
                float edge=length(max(bound,0.0))+min(max(bound.x,bound.y),0.0)-radius;
                float shadow=.34*arc*(1.0-smoothstep(0.0,70.0,max(0.0,edge-10.0*arc)))*opacity;
                float cover=(1.0-smoothstep(-.7,.7,edge))*opacity;
                if (cover<=.001) {
                    if (shadow<=.001) discard;
                    fragColor=vec4(vec3(.08,.065,.05)*shadow,shadow);
                    return;
                }
                vUv=clamp(card/(uSize*scale)+.5,0.0,1.0);
                drawGallery();
                vec3 color=fragColor.rgb;
                float luminance=dot(color,vec3(.2126,.7152,.0722));
                color=mix(color,vec3(luminance),.55*uDesat)*(1.0-.08*uDesat);
                if (scan>=0.0 && scan<=1.0) {
                    float line=1.0-smoothstep(.7,3.0,abs(vUv.y-scan)*uSize.y*scale);
                    color+=vec3(.36,.31,.20)*line*sin(3.14159265*scan);
                }
                vec2 inset=halfSize-abs(card);
                float mark=(1.0-smoothstep(.75,1.85,min(inset.x,inset.y)-4.0))
                          *smoothstep(2.0,4.0,min(inset.x,inset.y))
                          *(1.0-smoothstep(14.0,16.0,max(inset.x,inset.y)));
                color=mix(color,vec3(.80,.75,.61),mark*corners);
                fragColor=vec4(color*cover+vec3(.08,.065,.05)*shadow*(1.0-cover),
                               cover+shadow*(1.0-cover));
            }
            """;
}
