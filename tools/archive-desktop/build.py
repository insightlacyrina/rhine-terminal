#!/usr/bin/env python3
"""Generate a standalone WebGL2 preview using the Android mesh and shaders."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parents[1]
SOURCE = PROJECT / "app/src/main/java/dev/rhine/terminal"
FILES = (
    "vertex.glsl", "fragment.glsl", "stage-vertex.glsl", "stage-fragment.glsl",
    "hud-fragment.glsl", "home-vertex.glsl", "home-fragment.glsl", "snapshot-vertex.glsl", "snapshot-fragment.glsl",
    "vertices.bin", "indices.bin", "blackout.glsl", "instances.txt",
)


def java_tool(name):
    java_home = os.environ.get("JAVA_HOME")
    return str(Path(java_home) / "bin" / name) if java_home else name


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path,
                        default=PROJECT / "outputs/RhineTerminal-0.7.1-lock-preview.html")
    output = parser.parse_args().output
    with tempfile.TemporaryDirectory(prefix="rhine-archive-") as temp:
        subprocess.run([
            java_tool("javac"), "-d", temp, str(SOURCE / "SculptureScene.java"),
            str(SOURCE / "LockComposition.java"), str(SOURCE / "LightHomeScene.java"),
            str(SOURCE / "LockSnapshotScene.java"),
            str(SOURCE / "ArchiveTimeline.java"), str(SOURCE / "LockBlackoutScene.java"), str(HERE / "TimelineExtract.java"),
            str(HERE / "Extract.java"),
        ], check=True)
        subprocess.run([java_tool("java"), "-cp", temp, "Extract", temp], check=True)
        subprocess.run([java_tool("java"), "-cp", temp, "dev.rhine.terminal.TimelineExtract",
                        str(Path(temp) / "timeline.json")], check=True)
        timeline = json.loads((Path(temp) / "timeline.json").read_text())
        raw = {name: (Path(temp) / name).read_bytes() for name in FILES}
    wordmark = (PROJECT / "assets/niri-wordmark.svg").read_text(encoding="utf-8")
    data = {
        "wordmark": wordmark,
        "embedded": {name: base64.b64encode((PROJECT / "app/src/main/assets/rhine-lock" / path).read_bytes()).decode() for name, path in {"card":"scene/card.png", "smoke":"scene/smoke.jpg", "hex":"scene/hex.png", "light":"fonts/Montserrat-Light.ttf", "bold":"fonts/Montserrat-Bold.ttf", "mono":"fonts/JetBrainsMono-Regular.ttf"}.items()},
        "timeline": timeline["timeline"],
        "motion": timeline["motion"],
        "assets": {name: base64.b64encode(content).decode() if name.endswith(".bin")
                   else content.decode() for name, content in raw.items()},
        "sha256": {name: hashlib.sha256(content).hexdigest() for name, content in raw.items()},
    }
    payload = json.dumps(data, separators=(",", ":")).replace("<", "\\u003c")
    output.parent.mkdir(parents=True, exist_ok=True)
    template = (HERE / "template.html").read_text(encoding="utf-8")
    license_root = PROJECT / "app/src/main/assets/rhine-lock/fonts"
    notices = (PROJECT / "app/src/main/assets/rhine-lock/NOTICE.txt").read_text(encoding="utf-8")
    notices += "\n" + (PROJECT / "app/src/main/assets/rhine-lock/AGPL-3.0.txt").read_text(encoding="utf-8")
    for name in ("OFL-Montserrat.txt", "OFL-JetBrainsMono.txt"):
        notices += "\n" + (license_root / name).read_text(encoding="utf-8")
    output.write_text("<!--\n" + notices.replace("--", "- -") + "\n-->\n" + template.replace("__DATA__", payload), encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
