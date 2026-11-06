#!/usr/bin/env python3
"""Build the standalone preview from LightHomeScene's exact Java text blocks."""
import argparse, base64, hashlib, json, re, textwrap
from pathlib import Path

here = Path(__file__).resolve().parent
project = here.parents[1]
source = project / 'app/src/main/java/dev/rhine/terminal/LightHomeScene.java'
image = project / 'app/src/main/assets/rhine-lock/scene/card.png'
java = source.read_text()
def extract(name):
    match = re.search(r'\b' + name + r'\s*=\s*"""\s*\n(.*?)\n\s*"""\s*;', java, re.S)
    if not match:
        raise ValueError('Cannot find Java text block ' + name)
    return textwrap.dedent(match.group(1)) + '\n'
vertex, fragment = extract('VERTEX_SHADER'), extract('FRAGMENT_SHADER')
raw = image.read_bytes()
payload = {'vertex': vertex, 'fragment': fragment, 'image': 'data:image/png;base64,' + base64.b64encode(raw).decode(),
           'font': base64.b64encode((project / 'app/src/main/assets/rhine-lock/fonts/JetBrainsMono-Regular.ttf').read_bytes()).decode(), 'hashes': {'vertex': hashlib.sha256(vertex.encode()).hexdigest(), 'fragment': hashlib.sha256(fragment.encode()).hexdigest(), 'image': hashlib.sha256(raw).hexdigest()}}
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=project / 'outputs/RhineTerminal-0.7.1-carbon-preview.html')
output = parser.parse_args().output
output.parent.mkdir(parents=True, exist_ok=True)
license_text = '\n'.join((project / 'app/src/main/assets/rhine-lock' / name).read_text() for name in ['NOTICE.txt', 'AGPL-3.0.txt', 'fonts/OFL-JetBrainsMono.txt']).replace('--', '- -')
output.write_text('<!--\n' + license_text + '\n-->\n' + (here / 'template.html').read_text().replace('__DATA__', json.dumps(payload, separators=(',', ':')).replace('<', '\\u003c')))
print(output, payload['hashes'])
