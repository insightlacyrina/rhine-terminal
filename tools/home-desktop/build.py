"""Build the desktop preview from the Android shader strings and mesh."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

here = Path(__file__).resolve().parent
project = here.parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--output", type=Path, default=project / "build/home-preview.html")
output = parser.parse_args().output
source = project / "app/src/main/java/dev/rhine/terminal"
jdk = os.environ.get("JAVA_HOME")


def java_tool(name):
    return str(Path(jdk) / "bin" / name) if jdk else name


filenames = (
    "vertex.glsl", "fragment.glsl", "vertices.bin", "indices.bin",
    "background-vertex.glsl", "background-fragment.glsl",
)
with tempfile.TemporaryDirectory(prefix="rhine-home-") as temp:
    subprocess.run([
        java_tool("javac"), "-d", temp, str(source / "HomeSculptureScene.java"),
        str(source / "HomeBackgroundScene.java"), str(here / "Extract.java"),
    ], check=True)
    subprocess.run([java_tool("java"), "-cp", temp, "Extract", temp], check=True)
    assets = {name: (Path(temp) / name).read_bytes() for name in filenames}

data = {
    "vertex": assets["vertex.glsl"].decode(),
    "fragment": assets["fragment.glsl"].decode(),
    "vertices": base64.b64encode(assets["vertices.bin"]).decode(),
    "indices": base64.b64encode(assets["indices.bin"]).decode(),
    "bgVertex": assets["background-vertex.glsl"].decode(),
    "bgFragment": assets["background-fragment.glsl"].decode(),
    "hashes": {name: hashlib.sha256(content).hexdigest() for name, content in assets.items()},
}
payload = json.dumps(data, separators=(",", ":")).replace("<", "\\u003c")
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text((here / "template.html").read_text().replace("__DATA__", payload))
print(output, data["hashes"])
