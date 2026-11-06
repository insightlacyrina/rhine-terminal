const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const assert = require('node:assert/strict');

const [preview, evidence] = process.argv.slice(2);
if (!preview || !evidence) throw Error('Usage: node test.cjs preview.html evidence-directory');
fs.mkdirSync(evidence, { recursive: true });

(async () => {
    const browser = await chromium.launch({
        headless: true,
        executablePath: process.env.CHROME_BIN || undefined,
        args: [...(process.platform === 'darwin' ? ['--use-angle=metal'] : []), '--enable-webgl', '--ignore-gpu-blocklist'],
    });
    const report = { environment: 'desktop Chrome WebGL2; not Android or display presentation timing', runs: [] };
    const probe = page => page.evaluate(() => {
        const c = document.getElementById('scene');
        const gl = c.getContext('webgl2');
        const rgba = new Uint8Array(4);
        gl.readPixels(24, c.height >> 1, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, rgba);
        return [...rgba];
    });
    try {
        for (const [width, height] of [[2560, 1600], [1600, 2560]]) {
            const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: 1 });
            const errors = [];
            page.on('pageerror', e => errors.push(e.message));
            page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
            await page.goto(pathToFileURL(path.resolve(preview)).href);
            await page.waitForFunction(() => window.rhine && rhine.getState().drawn > 4, { timeout: 15000 });
            await page.waitForTimeout(1700);
            const normal = await page.evaluate(async () => ({
                state: rhine.getState(), renderer: rhine.renderer, triangleCount: rhine.triangleCount,
                parity: await rhine.verify(), hashes: rhine.hashes, timeline: rhine.timeline,
            }));
            const lightPixel = await probe(page);
            await page.screenshot({ path: path.join(evidence, `normal-${width}x${height}.png`) });
            await page.click('#pause');
            const paused = await page.evaluate(() => rhine.getState());
            await page.waitForTimeout(250);
            assert.equal((await page.evaluate(() => rhine.getState())).drawn, paused.drawn);
            await page.evaluate(() => { rhine.trigger(); rhine.setTime(rhine.getState().effectTime + .3); });
            const warning = await page.evaluate(() => rhine.getState());
            assert.equal(warning.alert, 1);
            const darkPixel = await probe(page);
            await page.screenshot({ path: path.join(evidence, `warning-${width}x${height}.png`) });
            await page.evaluate(() => rhine.setTime(rhine.getState().effectTime + 3.2));
            const recovery = await page.evaluate(() => rhine.getState());
            const recoveredPixel = await probe(page);
            assert.equal(recovery.alert, 0);
            assert.ok(lightPixel[0] > 185 && darkPixel[0] < 75 && recoveredPixel[0] > 185, 'background must invert and recover');
            await page.screenshot({ path: path.join(evidence, `recovery-${width}x${height}.png`) });
            const gpuNormal = await page.evaluate(() => rhine.gpuBenchmark(100, false));
            const gpuWarning = await page.evaluate(() => rhine.gpuBenchmark(100, true));
            const gpuSerialNormal = await page.evaluate(() => rhine.gpuBenchmark(60, false, true));
            const gpuSerialWarning = await page.evaluate(() => rhine.gpuBenchmark(60, true, true));
            const synchronousReadback = await page.evaluate(() => rhine.benchmark(40));
            const glError = (await page.evaluate(() => rhine.getState())).glError;
            assert.equal(glError, 0);
            assert.deepEqual(errors, []);
            assert.ok(normal.parity);
            report.runs.push({ dimensions: [width, height], normal, warning, recovery, lightPixel, darkPixel, recoveredPixel,
                pauseStable: true, gpuNormal, gpuWarning, gpuSerialNormal, gpuSerialWarning, synchronousReadback, glError, errors });
            await page.close();
        }
        const page = await browser.newPage({ viewport: { width: 1000, height: 700 } });
        await page.goto(pathToFileURL(path.resolve(preview)).href);
        await page.waitForFunction(() => window.rhine && rhine.getState().drawn > 4);
        await page.selectOption('#target', '60');
        await page.click('#motion');
        const start = await page.evaluate(() => rhine.getState());
        await page.waitForTimeout(250);
        assert.equal((await page.evaluate(() => rhine.getState())).elapsed, start.elapsed);
        await page.click('#demo');
        await page.waitForFunction(() => rhine.getState().alert === 1);
        const reducedWarning = await page.evaluate(() => rhine.getState());
        assert.equal(reducedWarning.elapsed, start.elapsed, 'warning remains available with sculpture motion disabled');
        await page.waitForFunction(() => rhine.getState().alert === 0, { timeout: 6000 });
        const cdp = await page.context().newCDPSession(page);
        await cdp.send('Page.setWebLifecycleState', { state: 'frozen' });
        await page.waitForTimeout(200);
        await cdp.send('Page.setWebLifecycleState', { state: 'active' });
        report.controls = { target60: start.target === 60, reducedMotionFreezesSculpture: true, manualWarningAndRecovery: true, freezeResume: true };
        assert.ok(report.controls.target60);
    } finally {
        await browser.close();
        fs.writeFileSync(path.join(evidence, 'browser-report.json'), JSON.stringify(report, null, 2));
    }
    for (const r of report.runs) console.log(JSON.stringify({ size: r.dimensions, triangles: r.normal.triangleCount,
        rAF: r.normal.state.fps, gpuNormal: r.gpuNormal, gpuWarning: r.gpuWarning, serialNormal: r.gpuSerialNormal, serialWarning: r.gpuSerialWarning, readback: r.synchronousReadback,
        pixels: [r.lightPixel, r.darkPixel, r.recoveredPixel] }));
    console.log('Desktop visual, GPU and controls tests passed.');
})().catch(e => { console.error(e); process.exit(1); });
