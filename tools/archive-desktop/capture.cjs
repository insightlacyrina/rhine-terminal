const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const fs = require("fs"),
  path = require("path"),
  { pathToFileURL } = require("url");
(async () => {
  const root = path.resolve(__dirname, "../..");
  const delivery = path.join(root, "outputs");
  const wallpaperDir =
    process.env.RHINE_WALLPAPER_DIR || path.join(delivery, "wallpapers-v0.5.1");
  const reportDir =
    process.env.RHINE_REPORT_DIR ||
    path.join(delivery, "validation-v0.5.1/browser");
  fs.mkdirSync(wallpaperDir, { recursive: true });
  fs.mkdirSync(reportDir, { recursive: true });
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROME_BIN || undefined,
    args: ["--use-angle=metal", "--enable-webgl", "--ignore-gpu-blocklist"],
  });
  let page = await browser.newPage({ viewport: { width: 1280, height: 900 } }),
    errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  try {
    await page.goto(
      pathToFileURL(
        process.env.RHINE_PREVIEW_HTML ||
          path.join(delivery, "RhineTerminal-0.5.1-preview.html"),
      ).href,
    );
    await page.waitForFunction(() => window.archive);
    await page.evaluate(() => archive.stop());
    let report = {
      renderer: await page.evaluate(() => archive.renderer),
      triangles: await page.evaluate(() => archive.triangles),
      hashes: await page.evaluate(() => archive.hashes),
      outputs: [],
      errors,
    };
    await page.selectOption("#orientation", "portrait");
    await page.selectOption("#sceneSelect", "home-dark");
    const selectedDark = await page.locator("#sceneSelect").inputValue();
    await page.selectOption("#stageSelect", "11000");
    const selectedStage = await page.locator("#clock").textContent();
    await page.locator("#seek").fill("4000");
    const scrubbed = await page.locator("#clock").textContent();
    await page.locator("#demo").click();
    const demoPlaying = await page.locator("#play").textContent();
    report.controls = {
      selectedDark,
      selectedStage,
      scrubbed,
      demoPlaying,
      orientation: await page.locator("#orientation").inputValue(),
    };
    await page.evaluate(() => archive.stop());
    if (
      selectedDark !== "home-dark" ||
      selectedStage !== "11.0s" ||
      scrubbed !== "4.0s" ||
      demoPlaying !== "暂停" ||
      report.controls.orientation !== "portrait"
    )
      throw Error("Preview control check failed");
    for (let kind of ["lock", "home", "home-dark"])
      for (let orientation of ["landscape", "portrait"]) {
        let png = await page.evaluate(
          ({ kind, orientation }) => {
            archive.draw(kind, orientation, 2000, false, 0);
            return document
              .querySelector("#scene")
              .toDataURL("image/png")
              .split(",")[1];
          },
          { kind, orientation },
        );
        let name = `${kind}-${orientation}-${orientation === "landscape" ? "2560x1600" : "1600x2560"}.png`;
        fs.writeFileSync(
          path.join(wallpaperDir, name),
          Buffer.from(png, "base64"),
        );
        report.outputs.push({
          name,
          error: await page.evaluate(() => archive.error()),
          benchmark: await page.evaluate(
            ({ kind, orientation }) => archive.benchmark(kind, orientation, 12),
            { kind, orientation },
          ),
        });
      }
    for (let orientation of ["landscape", "portrait"])
      for (let ms of [500, 2000, 4000, 6000, 11000, 16000, 18000]) {
        const png = await page.evaluate(
          ({ ms, orientation }) => {
            archive.draw("lock", orientation, ms, true, ms / 1000);
            return document
              .querySelector("#scene")
              .toDataURL("image/png")
              .split(",")[1];
          },
          { ms, orientation },
        );
        fs.writeFileSync(
          path.join(reportDir, `${orientation}-stage-${ms}.png`),
          Buffer.from(png, "base64"),
        );
      }
    fs.writeFileSync(
      path.join(reportDir, "report.json"),
      JSON.stringify(report, null, 2),
    );
    console.log(JSON.stringify(report, null, 2));
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
