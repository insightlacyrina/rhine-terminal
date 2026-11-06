// Read-only GPU regression: instrument only the phase expression in the exported vertex shader.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const project = path.resolve(__dirname, '../..');
const previews = path.resolve(process.env.RHINE_PREVIEW_DIR || path.join(project, 'outputs'));
const reportDir = path.resolve(process.env.RHINE_REPORT_DIR || path.join(project, 'outputs/validation-v0.7.1/browser'));
function payload(version) {
  const html = fs.readFileSync(path.join(previews, `RhineTerminal-${version}-lock-preview.html`), 'utf8');
  const start = html.indexOf('const DATA = ') + 'const DATA = '.length;
  const end = html.indexOf(',\n      A = DATA.assets', start);
  if (start < 13 || end < 0) throw Error(`Cannot read preview payload ${version}`);
  return JSON.parse(html.slice(start, end));
}
(async () => {
  const baseline = payload('0.7.0'), fixed = payload('0.7.1');
  const browser = await chromium.launch({headless:true, executablePath:process.env.CHROME_BIN || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
  const page = await browser.newPage();
  const report = await page.evaluate(({baseline, fixed}) => {
    const canvas = document.createElement('canvas'), gl = canvas.getContext('webgl2');
    if (!gl) throw Error('WebGL2 unavailable');
    const cases = [
      {name:'idle', birth:1, verify:0, pulse:0, alert:0, exit:0, time:1.7},
      {name:'verify-partial', birth:1, verify:.5, pulse:0, alert:0, exit:0, time:1.7},
      {name:'verify-full', birth:1, verify:1, pulse:0, alert:0, exit:0, time:1.7},
      {name:'touch-pulse', birth:1, verify:0, pulse:1, alert:0, exit:0, time:1.7},
      {name:'denied', birth:1, verify:0, pulse:0, alert:1, exit:0, time:1.7},
      {name:'release', birth:1, verify:0, pulse:0, alert:0, exit:.7, time:1.7},
    ];
    function compile(type, source) {
      const s=gl.createShader(type); gl.shaderSource(s,source); gl.compileShader(s);
      if (!gl.getShaderParameter(s,gl.COMPILE_STATUS)) throw Error(gl.getShaderInfoLog(s)); return s;
    }
    function run(data) {
      const original=data.assets['vertex.glsl'];
      const target='float t = 6.2831853 * fract((idx + uFlow) / uCount);';
      if (original.split(target).length !== 2) throw Error('Expected exactly one phase expression');
      const shader=original.replace('#version 300 es', '#version 300 es\nuniform float uTestPhase;').replace(target, 'float t = uTestPhase;');
      const program=gl.createProgram(); gl.attachShader(program,compile(gl.VERTEX_SHADER,shader));
      gl.attachShader(program,compile(gl.FRAGMENT_SHADER,'#version 300 es\nprecision highp float; out vec4 color; void main(){color=vec4(1.0);}'));
      gl.transformFeedbackVaryings(program,['vPosition','vNormal'],gl.INTERLEAVED_ATTRIBS); gl.linkProgram(program);
      if (!gl.getProgramParameter(program,gl.LINK_STATUS)) throw Error(gl.getProgramInfoLog(program));
      const bytes=Uint8Array.from(atob(data.assets['vertices.bin']),c=>c.charCodeAt(0));
      const vao=gl.createVertexArray();gl.bindVertexArray(vao);const vb=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,vb);gl.bufferData(gl.ARRAY_BUFFER,bytes,gl.STATIC_DRAW);
      gl.enableVertexAttribArray(0);gl.vertexAttribPointer(0,3,gl.FLOAT,false,24,0);gl.enableVertexAttribArray(1);gl.vertexAttribPointer(1,3,gl.FLOAT,false,24,12);
      const count=bytes.length/24, buffer=gl.createBuffer(), feedback=gl.createTransformFeedback();gl.bindTransformFeedback(gl.TRANSFORM_FEEDBACK,feedback);gl.bindBuffer(gl.TRANSFORM_FEEDBACK_BUFFER,buffer);gl.bufferData(gl.TRANSFORM_FEEDBACK_BUFFER,count*6*4,gl.DYNAMIC_READ);gl.bindBufferBase(gl.TRANSFORM_FEEDBACK_BUFFER,0,buffer);
      gl.useProgram(program);const identity=new Float32Array([1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]);
      for(const key of ['uMvp','uModel'])gl.uniformMatrix4fv(gl.getUniformLocation(program,key),false,identity);
      function sample(phase,c) {
        for(const [key,value] of Object.entries({uTestPhase:phase,uTime:c.time,uBirth:c.birth,uVerify:c.verify,uPulse:c.pulse,uAlert:c.alert,uExit:c.exit}))gl.uniform1f(gl.getUniformLocation(program,key),value);
        gl.enable(gl.RASTERIZER_DISCARD);gl.beginTransformFeedback(gl.POINTS);gl.drawArraysInstanced(gl.POINTS,0,count,1);gl.endTransformFeedback();gl.disable(gl.RASTERIZER_DISCARD);
        const values=new Float32Array(count*6);gl.bindBuffer(gl.TRANSFORM_FEEDBACK_BUFFER,buffer);gl.getBufferSubData(gl.TRANSFORM_FEEDBACK_BUFFER,0,values);return values;
      }
      function diff(a,b) {let position=0,normal=0;for(let i=0;i<a.length;i+=6){position=Math.max(position,Math.hypot(a[i]-b[i],a[i+1]-b[i+1],a[i+2]-b[i+2]));normal=Math.max(normal,Math.hypot(a[i+3]-b[i+3],a[i+4]-b[i+4],a[i+5]-b[i+5]));}return {position,normal};}
      const results=cases.map(c=>{const zero=sample(0,c),end=sample(6.2831853,c),near=sample(6.2831853*(399/400),c);return {case:c.name,seam:diff(zero,end),oneFin:diff(zero,near)};});
      const error=gl.getError();gl.deleteProgram(program);return {vertexCount:count,results,error};
    }
    return {baseline:run(baseline),fixed:run(fixed),renderer:gl.getParameter(gl.RENDERER)};
  }, {baseline, fixed});
  const hash = data => crypto.createHash('sha256').update(data.assets['vertex.glsl']).digest('hex');
  report.method='Production vertex shader from each preview; only phase is overridden. Same instance ID and all 24 cube vertices; identity model. Seam 0 vs 2pi, neighbor 0 vs 399/400 turn; π/2 assembly/peel front is excluded.';
  report.units='World positions normalized by 285; normals are unit vectors. Error is maximum Euclidean difference across vertices.';
  report.baseline.shaderSha256=hash(baseline); report.fixed.shaderSha256=hash(fixed);
  report.thresholds={position:0.0001,normal:0.0001};
  report.baselineFails=report.baseline.results.some(r=>r.seam.position>report.thresholds.position||r.seam.normal>report.thresholds.normal);
  report.fixedPasses=report.fixed.results.every(r=>r.seam.position<report.thresholds.position&&r.seam.normal<report.thresholds.normal);
  fs.mkdirSync(reportDir,{recursive:true});fs.writeFileSync(path.join(reportDir,'seam-report.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify(report));await browser.close();
  if (!report.baselineFails || !report.fixedPasses || report.baseline.error || report.fixed.error) process.exit(1);
})().catch(e=>{console.error(e);process.exit(1)});
