const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const fs = require("fs"),
  path = require("path"),
  { pathToFileURL } = require("url");
(async () => {
  let root = path.resolve(__dirname, "../.."),
    out = process.argv[2] || "/tmp/rhine-test.h264",
    total = +(process.argv[3] || 120),
    b = await chromium.launch({
      headless: true,
      executablePath: process.env.CHROME_BIN || undefined,
      args: ["--use-angle=metal", "--enable-webgl", "--ignore-gpu-blocklist"],
    });
  let p = await b.newPage();
  let stream = fs.createWriteStream(out),
    writes = [];
  await p.exposeFunction(
    "saveChunk",
    (s) =>
      new Promise((resolve, reject) =>
        stream.write(Buffer.from(s, "base64"), (e) =>
          e ? reject(e) : resolve(),
        ),
      ),
  );
  await p.goto(
    pathToFileURL(
      process.env.RHINE_PREVIEW_HTML ||
        path.join(root, "outputs/RhineTerminal-0.5.1-preview.html"),
    ).href,
  );
  await p.waitForFunction(() => window.archive);
  let result = await p.evaluate(async (total) => {
    archive.stop();
    let pending = [],
      encError = null,
      chunks = 0;
    let enc = new VideoEncoder({
      output: (chunk) => {
        let a = new Uint8Array(chunk.byteLength);
        chunk.copyTo(a);
        let s = "";
        for (let i = 0; i < a.length; i += 32768)
          s += String.fromCharCode(...a.subarray(i, i + 32768));
        pending.push(window.saveChunk(btoa(s)));
        chunks++;
      },
      error: (e) => (encError = e.message),
    });
    enc.configure({
      codec: "avc1.640034",
      width: 2560,
      height: 1600,
      bitrate: 30000000,
      framerate: 120,
      hardwareAcceleration: "prefer-hardware",
      avc: { format: "annexb" },
    });
    let start = performance.now();
    for (let i = 0; i < total; i++) {
      archive.draw("lock", "landscape", 2000, false, i / 120);
      let frame = new VideoFrame(document.querySelector("#scene"), {
        timestamp: Math.round((i * 1000000) / 120),
        duration: Math.round(1000000 / 120),
      });
      enc.encode(frame, { keyFrame: i % 240 === 0 });
      frame.close();
      if (enc.encodeQueueSize > 8)
        await new Promise((r) =>
          enc.addEventListener("dequeue", r, { once: true }),
        );
      if (encError) throw Error(encError);
    }
    await enc.flush();
    enc.close();
    await Promise.all(pending);
    return {
      frames: total,
      chunks,
      elapsedSeconds: (performance.now() - start) / 1000,
      error: archive.error(),
    };
  }, total);
  await new Promise((r) => stream.end(r));
  console.log(JSON.stringify(result));
  await b.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
