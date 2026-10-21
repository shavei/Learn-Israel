const { chromium } = require('playwright');
const path = require('path'), fs = require('fs');
(async () => {
  const mode = process.argv[2]; // "preview" or "frames"
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 } });
  await page.goto('file://' + path.resolve('promo.html'));
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(500);
  if (mode === 'preview') {
    const times = process.argv.slice(3).map(Number);
    for (const t of times) { await page.evaluate(t => render(t), t); await page.screenshot({ path: `preview_${t}.png` }); }
  } else {
    fs.mkdirSync('frames', { recursive: true });
    const fps = 30, dur = await page.evaluate(() => DURATION);
    const n = Math.round(dur * fps);
    for (let i = 0; i < n; i++) {
      await page.evaluate(t => render(t), i / fps);
      await page.screenshot({ path: `frames/f${String(i).padStart(5,'0')}.png` });
    }
    console.log('frames', n);
  }
  await browser.close();
})();
