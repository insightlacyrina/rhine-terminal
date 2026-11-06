const { chromium } = require('playwright');
const { pathToFileURL } = require('url');
const path = require('path');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 const page=await browser.newPage({viewport:{width:1440,height:1050},deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const file=path.resolve(__dirname,'../../../../outputs/RhineTerminal-0.6.0-carbon-preview.html');
 await page.goto(pathToFileURL(file).href);await page.waitForFunction(()=>window.carbonPreview?.state().ready);await page.waitForTimeout(1100);
 const first=await page.evaluate(()=>carbonPreview.state());await page.waitForTimeout(350);const second=await page.evaluate(()=>carbonPreview.state());
 await page.screenshot({path:path.join(__dirname,'landscape.png')});
 await page.locator('#pause').click();const stopped=await page.evaluate(()=>carbonPreview.state());await page.waitForTimeout(250);const stopped2=await page.evaluate(()=>carbonPreview.state());
 await page.locator('#orientation').click();await page.screenshot({path:path.join(__dirname,'portrait.png')});const portrait=await page.evaluate(()=>carbonPreview.state());
 await page.locator('#pause').click();await page.waitForTimeout(250);const resumed=await page.evaluate(()=>carbonPreview.state());
 console.log(JSON.stringify({first,second,stopped,stopped2,portrait,resumed,errors},null,2));await browser.close();
 if(errors.length||!first.ready||second.rows<=first.rows||stopped2.rows!==stopped.rows||portrait.landscape||resumed.t<=portrait.t||second.error)process.exit(1);
})().catch(e=>{console.error(e);process.exit(1)});
