# Desktop archive proof

Use JDK 17 or newer and set `JAVA_HOME` if it is not the default. The generated HTML embeds shader strings, mesh, and 5 ms timeline/motion samples extracted from the Java sources; no server is needed.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home python3 tools/archive-desktop/build.py
PLAYWRIGHT_MODULE=/path/to/playwright CHROME_BIN=/path/to/chrome node tools/archive-desktop/capture.cjs
PLAYWRIGHT_MODULE=/path/to/playwright CHROME_BIN=/path/to/chrome node tools/archive-desktop/video.cjs /tmp/rhine-idle.h264 2880
ffmpeg -r 120 -i /tmp/rhine-idle.h264 -c:v copy -video_track_timescale 12000 -color_primaries bt709 -color_trc bt709 -colorspace bt709 -movflags +faststart outputs/wallpapers-v0.5.1/lock-idle-2560x1600-120fps.mp4
```

The default output is `outputs/RhineTerminal-0.7.1-lock-preview.html`. Build both previews and run `verify-v070.cjs` with Playwright and `RHINE_PREVIEW_VERSION=0.7.1` to capture the lock timeline and three home variants in both orientations. Set `RHINE_PREVIEW_DIR` and `RHINE_REPORT_DIR` to select input and output directories. The seam regression `verify-seam.cjs` reads both the 0.7.0 and 0.7.1 lock previews from the input directory and compares GPU positions and normals at the curve wrap.

The older capture and video scripts accept `RHINE_PREVIEW_HTML` to select a preview. They export lock, warm home and dark home renders at 2560×1600 or 1600×2560. Set `RHINE_WALLPAPER_DIR` and `RHINE_REPORT_DIR` to choose where to save evidence. The video script requires Chrome WebCodecs with H.264 High 5.2 and samples 2,880 frames without the HUD. The upstream sculpture motion is continuous. Browser overlay text is simulated. Benchmarks measure desktop draw and readback.
