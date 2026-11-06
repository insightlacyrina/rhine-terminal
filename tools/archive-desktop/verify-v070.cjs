const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');

const project = path.resolve(__dirname, '../..');
const previews = path.resolve(process.env.RHINE_PREVIEW_DIR || path.join(project, 'outputs'));
const reportDir = path.resolve(process.env.RHINE_REPORT_DIR || path.join(project, 'outputs/validation-v0.7/browser'));

(async () => {
  fs.mkdirSync(reportDir, { recursive: true });
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROME_BIN || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
  });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1050 } });
  const errors = [], externalRequests = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('request', request => {
    if (!request.url().startsWith('file:') && !request.url().startsWith('data:')) externalRequests.push(request.url());
  });
  const version = process.env.RHINE_PREVIEW_VERSION || '0.7.0';
  await page.goto(pathToFileURL(path.join(previews, `RhineTerminal-${version}-lock-preview.html`)).href);
  await page.waitForFunction(() => window.archive?.assetsReady());
  await page.evaluate(() => archive.stop());
  const honeycomb = await page.evaluate(() => {
    const original = CanvasRenderingContext2D.prototype.drawImage;
    const seen = [];
    CanvasRenderingContext2D.prototype.drawImage = function (source, ...args) {
      if (source instanceof HTMLCanvasElement && source.width < 500 && source.height < 300) seen.push(source);
      return original.call(this, source, ...args);
    };
    archive.draw('lock', 'landscape', 2000, true, 2);
    const idleCount = seen.length;
    archive.draw('lock', 'landscape', 5500, true, 5.5);
    const denialCount = seen.length;
    archive.draw('lock', 'landscape', 2000, true, 2);
    archive.draw('lock', 'landscape', 5500, true, 5.5);
    const reused = seen.length === 2 && seen[0] === seen[1];
    const size = seen[0] ? [seen[0].width, seen[0].height] : [];
    CanvasRenderingContext2D.prototype.drawImage = original;
    return { idleCount, denialCount, reused, size };
  });
  const frames = [];
  for (const orientation of ['landscape', 'portrait']) {
    const samples = version === '0.7.0'
      ? [0, 500, 2000, 5100, 5125, 5500, 6000, 10450, 11000, 16000, 17000]
      : [0, 460, 900, 2000, 3860, 5125, 5500, 6000, 7780, 8110, 11000, 16000, 16300, 17000];
    for (const ms of samples) {
      const result = await page.evaluate(({ orientation, ms }) => {
        archive.draw('lock', orientation, ms, true, ms / 1000);
        return {
          png: document.querySelector('#scene').toDataURL().split(',')[1],
          error: archive.error(),
          fonts: ['RhineLight', 'RhineBold', 'RhineMono'].map(font => document.fonts.check(`12px ${font}`)),
        };
      }, { orientation, ms });
      fs.writeFileSync(path.join(reportDir, `lock-${orientation}-${ms}.png`), Buffer.from(result.png, 'base64'));
      frames.push({ orientation, ms, error: result.error, fonts: result.fonts });
    }
  }
  const homes = [];
  for (const orientation of ['landscape', 'portrait']) {
    for (const kind of ['home', 'home-dark']) {
      const result = await page.evaluate(({kind, orientation}) => {
        archive.draw(kind, orientation, 2000, true, 2);
        return {png: document.querySelector('#scene').toDataURL().split(',')[1], error: archive.error()};
      }, {kind, orientation});
      fs.writeFileSync(path.join(reportDir, `${kind}-${orientation}.png`), Buffer.from(result.png, 'base64'));
      homes.push({kind, orientation, error: result.error});
    }
  }
  const instances = await page.evaluate(() => archive.instances);
  const lockHashes = await page.evaluate(() => archive.hashes);
  const blackoutSha256 = lockHashes['blackout.glsl'];
  await page.goto(pathToFileURL(path.join(previews, `RhineTerminal-${version}-carbon-preview.html`)).href);
  await page.waitForFunction(() => carbonPreview?.state().ready && carbonPreview.state().fontReady);
  await page.waitForTimeout(300);
  const carbon = [];
  for (const orientation of ['landscape', 'portrait']) {
    await page.screenshot({ path: path.join(reportDir, `carbon-${orientation}.png`) });
    carbon.push(await page.evaluate(() => carbonPreview.state()));
    if (orientation === 'landscape') await page.locator('#orientation').click();
  }
  const carbonHashes = await page.evaluate(() => carbonPreview.hashes);
  const sharedHomeShader = lockHashes['home-fragment.glsl'] === carbonHashes.fragment && lockHashes['home-vertex.glsl'] === carbonHashes.vertex;
  const report = { frames, homes, carbon, honeycomb, instances, sharedHomeShader, blackoutSha256, errors, externalRequests };
  fs.writeFileSync(path.join(reportDir, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report));
  await browser.close();
  if (errors.length || externalRequests.length || frames.some(frame => frame.error || frame.fonts.some(ok => !ok)) ||
      homes.some(home => home.error) || !sharedHomeShader || instances !== 400 || carbon.some(state => state.error || !state.ready || !state.fontReady) || honeycomb.idleCount !== 0 ||
      honeycomb.denialCount !== 1 || !honeycomb.reused) process.exit(1);
})().catch(error => { console.error(error); process.exit(1); });
