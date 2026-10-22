// Renders the brand images from icon.svg: the 512 px store icon and the logo. The logo is the
// icon's land on its own, as the Survey of Israel's 1978 map printed it, with the game's dotted
// red line running out of it under the name to its blue target.
// Run: node render.js (needs Playwright, as ../promo does).
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const here = __dirname;
const font = f => 'data:font/ttf;base64,' +
  fs.readFileSync(path.resolve(here, '../../app/src/main/res/font', f)).toString('base64');
const svg = fs.readFileSync(path.join(here, 'icon.svg'), 'utf8');
// The launcher shows the middle 72 of the 108 unit canvas, so the store icon does too.
const icon = size => svg.replace('viewBox="0 0 108 108" width="108" height="108"',
  `viewBox="18 18 72 72" width="${size}" height="${size}" style="display:block"`);
// The icon's layers in make_icon.py's order: sea, neighbors, their edge, then the land's own.
const land = svg.split('\n').filter(l => l.trim().startsWith('<path')).slice(3).join('');

const BLUE = '#1E5A82', RED = '#C8352B', PAPER = '#F8F3E8';

const logo = `
<style>
  @font-face { font-family: FrankRuhl; src: url(${font('frank_ruhl_bold.ttf')}); }
  @font-face { font-family: Rubik; src: url(${font('rubik_regular.ttf')}); }
  body { margin: 0; width: 1200px; height: 460px; background: ${PAPER}; direction: rtl;
         display: flex; align-items: center; justify-content: center; gap: 40px; }
  h1 { font: 700 132px/1 FrankRuhl; color: ${BLUE}; margin: 0; }
  p { font: 400 38px/1.3 Rubik; color: #4D463A; margin: 0; position: absolute; top: 100%; right: 0; white-space: nowrap; }
</style>
<svg width="150" height="380" viewBox="40 21 28 66">${land}</svg>
<div style="position:relative;padding-bottom:62px">
  <h1>על המפה</h1>
  <svg width="560" height="60" style="position:absolute;bottom:0;right:0;overflow:visible">
    <g fill="${RED}">${Array.from({ length: 26 }, (_, i) => `<circle cx="${544 - i * 20}" cy="26" r="5"/>`).join('')}</g>
    <circle cx="24" cy="26" r="17" fill="${PAPER}" stroke="${BLUE}" stroke-width="9"/>
    <circle cx="24" cy="26" r="7" fill="${BLUE}"/>
  </svg>
  <p>בונים מפה של ישראל בראש, 5 דקות ביום</p>
</div>`;

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setViewportSize({ width: 512, height: 512 });
  await page.setContent(`<body style="margin:0">${icon(512)}</body>`);
  await page.screenshot({ path: path.join(here, 'icon-512.png') });
  await page.setViewportSize({ width: 1200, height: 460 });
  await page.setContent(logo);
  await page.evaluate(() => Promise.all(['132px FrankRuhl', '38px Rubik'].map(f => document.fonts.load(f, 'אב'))));
  await page.screenshot({ path: path.join(here, 'logo.png') });
  await browser.close();
})();
