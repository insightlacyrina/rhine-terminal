#!/usr/bin/env python3
"""Build self-contained, resolution-independent archive wallpaper artwork."""
from pathlib import Path
import math
import argparse

parser=argparse.ArgumentParser()
parser.add_argument('--output',type=Path,default=Path.cwd()/'outputs'/'wallpapers-v0.5'/'svg')
OUT=parser.parse_args().output
OUT.mkdir(parents=True,exist_ok=True)

def svg_open(w,h,dark=False):
    bg0,bg1=('#222420','#111210') if dark else ('#e4e0da','#c9c4bd')
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">
<defs>
<radialGradient id="background" cx="48%" cy="45%" r="76%"><stop stop-color="{bg0}"/><stop offset="1" stop-color="{bg1}"/></radialGradient>
<linearGradient id="glass" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#fff9ec" stop-opacity=".35"/><stop offset=".5" stop-color="#e7dfca" stop-opacity=".09"/><stop offset="1" stop-color="#fff8e3" stop-opacity=".26"/></linearGradient>
<linearGradient id="metal" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#f2d594"/><stop offset=".48" stop-color="#b8873f"/><stop offset="1" stop-color="#775323"/></linearGradient>
<linearGradient id="ceramic" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#f1e8df"/><stop offset=".54" stop-color="#e6ddd4"/><stop offset="1" stop-color="#a9a19a"/></linearGradient>
</defs><path fill="url(#background)" d="M0 0H{w}V{h}H0Z"/>'''

def home(w,h,dark):
    s=min(w,h); portrait=h>w
    out=[svg_open(w,h,dark)]
    # Diffuse, deliberately quiet ceramic silhouette, behind all objects.
    cx=w*.94; cy=h*.46
    opacity='.10' if dark else '.20'
    out.append(f'<g fill="none" stroke="{ "#a69c8f" if dark else "#f3ebe0" }" opacity="{opacity}" stroke-width="{s*.065:.1f}"><path d="M {cx-s*.44:.1f} {cy:.1f} C {cx-s*.47:.1f} {cy-s*.25:.1f} {cx-s*.14:.1f} {cy-s*.22:.1f} {cx:.1f} {cy:.1f} S {cx+s*.43:.1f} {cy+s*.25:.1f} {cx+s*.44:.1f} {cy:.1f} S {cx+s*.16:.1f} {cy-s*.24:.1f} {cx:.1f} {cy:.1f} S {cx-s*.44:.1f} {cy+s*.24:.1f} {cx-s*.44:.1f} {cy:.1f}"/></g>')
    x0,dx,y0,dy=(.26,.137,.64,.055) if portrait else (.55,.079,.60,.065)
    scale=s*(.24 if portrait else .245)
    # Home vertex shader maps UV to top-left coordinates.
    for i in range(5):
        x=(x0+dx*i)*w; y=(y0+dy*i)*h
        out.append(f'<g transform="translate({x:.2f} {y:.2f}) scale({scale:.3f})">')
        out.append('<ellipse cx=".08" cy=".27" rx=".59" ry=".10" fill="#000" opacity=".13"/>')
        # Exact cuboid vectors from shader, in screen coordinates.
        out.append('<path d="M-.31 -.255 L.58 -.201 L.58 .109 L-.31 .055Z" fill="#bfb29b" fill-opacity=".13" stroke="#b7a98d" stroke-opacity=".35" stroke-width=".004"/>')
        out.append('<path d="M-.48 -.11 L-.31 -.255 L.58 -.201 L.41 -.056Z" fill="url(#glass)" stroke="#fff3d5" stroke-opacity=".42" stroke-width=".004"/>')
        out.append('<path d="M.41 -.056 L.58 -.201 L.58 .109 L.41 .254Z" fill="#d4cbb8" fill-opacity=".13" stroke="#fff3d5" stroke-opacity=".32" stroke-width=".004"/>')
        out.append('<ellipse cx=".055" cy=".035" rx=".22" ry=".089" fill="none" stroke="#221a0e" stroke-opacity=".23" stroke-width=".037"/>')
        out.append('<ellipse cx=".045" cy=".025" rx=".22" ry=".089" fill="none" stroke="url(#metal)" stroke-width=".036"/><ellipse cx=".045" cy=".025" rx=".202" ry=".072" fill="none" stroke="#f9e4aa" stroke-opacity=".5" stroke-width=".006"/>')
        out.append('<path d="M-.48 -.11 L.41 -.056 L.41 .254 L-.48 .20Z" fill="url(#glass)" stroke="#fff5dc" stroke-opacity=".65" stroke-width=".006"/>')
        out.append('<path d="M-.46 -.09 L.39 -.038 M-.45 -.08 L-.45 .18 M.39 -.038 L.39 .235" fill="none" stroke="#fff9e9" stroke-opacity=".45" stroke-width=".004"/>')
        out.append('<path d="M-.305 -.174 L-.205 -.168 L-.175 -.194 L-.275 -.200Z" fill="#bbed3c" opacity=".90"/>')
        out.append('</g>')
    out.append('</svg>')
    return '\n'.join(out)

def lock(w,h):
    s=min(w,h); portrait=h>w
    cx=w*(.52 if portrait else .44); cy=h*(.46 if portrait else .495)
    diameter=s*(.88 if portrait else 1.09)
    focal=1/math.tan(math.radians(31.5)/2)
    profile=[(-.88,-.76),(-.76,-1),(.76,-1),(.88,-.76),(.12,.76),(-.12,.76)]
    step=2*math.pi/224
    def rotate(v,axis,angle):
        x,y,z=v; a=math.radians(angle); c=math.cos(a); q=math.sin(a)
        return (x,y*c-z*q,y*q+z*c) if axis=='x' else ((x*c+z*q,y,-x*q+z*c) if axis=='y' else (x*c-y*q,x*q+y*c,z))
    def transform(v):
        for axis,angle in [('z',43 if portrait else -11),('y',10),('x',-8)]: v=rotate(v,axis,angle)
        return v
    def project(v):
        x,y,z=v; f=diameter*.5*focal/(4.4-z)
        return cx+x*f,cy-y*f
    def sub(a,b): return tuple(x-y for x,y in zip(a,b))
    def cross(a,b): return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
    def norm(a):
        mag=math.sqrt(sum(x*x for x in a)) or 1
        return tuple(x/mag for x in a)
    faces=[]
    for i in range(224):
        a=(i+.5)*step; sa,ca=math.sin(a),math.cos(a); s2,c2=math.sin(2*a),math.cos(2*a)
        center=(.94*sa+.13*math.sin(2*a+.32),.53*s2+.105*ca+.068*math.sin(3*a),.39*ca+.10*s2)
        derivative=(.94*ca+.26*math.cos(2*a+.32),1.06*c2-.105*sa+.204*math.cos(3*a),-.39*sa+.20*c2)
        d=math.hypot(derivative[0],derivative[1]); radial=(-derivative[1]/d,derivative[0]/d,0)
        # Match the runtime full-turn frame so the asymmetric profile closes.
        twist=a-math.tau*.25+.59*s2+.34; ct,st=math.cos(twist),math.sin(twist)
        wide=(radial[0]*ct,radial[1]*ct,st); thick=(-radial[0]*st,-radial[1]*st,ct)
        width=.186*(1+.22*sa+.09*c2)
        ends=[]
        for along in (-.39*step,.39*step):
            ends.append([transform(tuple(center[k]+derivative[k]*along+wide[k]*q*width+thick[k]*r*.070 for k in range(3))) for q,r in profile])
        polys=[ends[0][::-1],ends[1]]
        for j in range(6): polys.append([ends[0][j],ends[1][j],ends[1][(j+1)%6],ends[0][(j+1)%6]])
        for poly in polys:
            n=norm(cross(sub(poly[1],poly[0]),sub(poly[2],poly[0])))
            # Closed faces are painted back-to-front. Soft diffuse lighting separates bevels.
            light=max(0,min(1,.55+.34*(n[0]*-.32+n[1]*.68+n[2]*.66)))
            base=int(167+72*light); color=f'#{base:02x}{max(0,base-8):02x}{max(0,base-15):02x}'
            coords=' '.join(f'{x:.2f},{y:.2f}' for x,y in map(project,poly))
            faces.append((sum(p[2] for p in poly)/len(poly),f'<polygon points="{coords}" fill="{color}"/>'))
    return '\n'.join([svg_open(w,h)]+[p[1] for p in sorted(faces)]+['</svg>'])

for name,w,h in [('landscape',2560,1600),('portrait',1600,2560)]:
    for mode in ('warm','dark'):
        (OUT/f'home-{mode}-{name}.svg').write_text(home(w,h,mode=='dark'))
    (OUT/f'lock-ivory-{name}.svg').write_text(lock(w,h))
