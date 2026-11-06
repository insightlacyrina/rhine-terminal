#!/usr/bin/env python3
"""Build a standalone preview from the *actual* Android shader and mesh sources."""
from pathlib import Path
import argparse, base64, hashlib, json, os, subprocess, tempfile

here=Path(__file__).resolve().parent
project=here.parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--output',type=Path,default=project/'build/desktop-preview.html')
args=parser.parse_args()
java_home=os.environ.get('JAVA_HOME')
def jdk(name):
    return str(Path(java_home)/'bin'/name) if java_home else name

source=project/'app/src/main/java/dev/rhine/terminal'
files={'vertex':'vertex.glsl','fragment':'fragment.glsl','planeVertex':'plane-vertex.glsl',
       'planeFragment':'plane-fragment.glsl','vertices':'vertices.bin','indices':'indices.bin'}
with tempfile.TemporaryDirectory(prefix='rhine-preview-') as temp:
    subprocess.run([jdk('javac'),'-d',temp,str(source/'SculptureScene.java'),str(source/'WarningTimeline.java'),str(here/'Extract.java')],check=True)
    subprocess.run([jdk('java'),'-cp',temp,'Extract',temp],check=True)
    extracted={name:(Path(temp)/filename).read_bytes() for name,filename in files.items()}
    timeline=json.loads((Path(temp)/'timeline.json').read_text())
    instances=int((Path(temp)/'instances.txt').read_text())
data={'shaders':{name:extracted[name].decode() for name in ('vertex','fragment','planeVertex','planeFragment')},
      'vertices':base64.b64encode(extracted['vertices']).decode(),
      'indices':base64.b64encode(extracted['indices']).decode(),
      'instances':instances,
      'timeline':timeline,
      'hashes':{name:hashlib.sha256(value).hexdigest() for name,value in extracted.items()}}
args.output.parent.mkdir(parents=True,exist_ok=True)
args.output.write_text((here/'template.html').read_text().replace('__DATA__',json.dumps(data,separators=(',',':')).replace('<','\\u003c')))
print(json.dumps({'output':str(args.output),'hashes':data['hashes'],'timeline':timeline},indent=2))
