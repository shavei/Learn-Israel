// Renders the brand images from icon.svg: the 512 px store icon and the logo lockup.
// Run: node render.js (needs Playwright, as ../promo does).
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const here = __dirname;
const font = f => 'data:font/ttf;base64,' +
  fs.readFileSync(path.resolve(here, '../../app/src/main/res/font', f)).toString('base64');
// The launcher shows the middle 72 of the 108 unit canvas, so the store icon does too.
const icon = (size, radius) => fs.readFileSync(path.join(here, 'icon.svg'), 'utf8')
  .replace('viewBox="0 0 108 108" width="108" height="108"',
    `viewBox="18 18 72 72" width="${size}" height="${size}" style="border-radius:${radius}px;display:block"`);

const logo = `
<style>
  @font-face { font-family: FrankRuhl; src: url(${font('frank_ruhl_bold.ttf')}); }
  @font-face { font-family: Rubik; src: url(${font('rubik_regular.ttf')}); }
  body { margin: 0; width: 1200px; height: 420px; background: #F8F3E8; direction: rtl;
         display: flex; align-items: center; gap: 56px; padding: 0 90px; box-sizing: border-box; }
  .icon { box-shadow: 0 6px 18px rgba(33, 30, 25, .18); border-radius: 56px; }
  h1 { font: 700 132px/1 FrankRuhl; color: #1E5A82; margin: 0 0 18px; }
  p { font: 400 40px/1.3 Rubik; color: #4D463A; margin: 0; }
</style>
<div class="icon">${icon(240, 56)}</div>
<div><h1>על המפה</h1><p>בונים מפה של ישראל בראש, 5 דקות ביום</p></div>`;

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setViewportSize({ width: 512, height: 512 });
  await page.setContent(`<body style="margin:0">${icon(512, 0)}</body>`);
  await page.screenshot({ path: path.join(here, 'icon-512.png') });
  await page.setViewportSize({ width: 1200, height: 420 });
  await page.setContent(logo);
  await page.evaluate(() => Promise.all(['132px FrankRuhl', '40px Rubik'].map(f => document.fonts.load(f, 'אב'))));
  await page.screenshot({ path: path.join(here, 'logo.png') });
  await browser.close();
})();
