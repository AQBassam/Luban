// Renders the site's screenshots into site/www/assets/img/. Nothing in them comes from a real TV:
// the screensaver is Luban's own page (dist/luban.html) with demo settings and a fixed clock, and the two native
// screens (home, settings code) are redrawn in site/screens/. Run after changing the screen, then commit the images:
//   node site/shots.mjs            (needs playwright, like tests/page.test.mjs)
import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';
import { fileURLToPath, pathToFileURL } from 'url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'site/www/assets/img');
const { share: PLAN } = JSON.parse(fs.readFileSync(path.join(ROOT, 'tests/dinner-fixture.json'), 'utf8'));
const enc = s => Buffer.from(JSON.stringify(s)).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const url = (rel, q = '') => pathToFileURL(path.join(ROOT, rel)).href + q;

// Muscat, 21 Oct 2026 (Asr→Maghrib, morning, afternoon). Times in UTC.
const SAVER = [
  ['saver-chart', { bg: 'chart', cards: ['dinner'], dinner: PLAN }, '2026-10-21T13:12:00Z'],
  ['saver-photo', { bg: 'photos', cards: ['prayers'] }, '2026-10-21T04:05:00Z'],
  ['saver-tasks', { bg: 'photos', cards: ['dinnerTasks'], dinner: PLAN }, '2026-10-21T12:40:00Z'],
];

const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
const shots = [];
async function page(w = 1920, h = 1080, scale = 1) {
  const p = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: scale });
  p.errors = []; p.on('pageerror', e => p.errors.push(e.message));
  await p.route(/^https?:\/\//, r => r.abort());   // no network: everything shown is local and fixed
  // the stand-alone screen takes its photos from GitHub; answer those from the repo's own photos/
  await p.route(/raw\.githubusercontent\.com\/.*\/photos\/([\w.-]+)$/, r =>
    r.fulfill({ path: path.join(ROOT, 'photos', path.basename(new URL(r.request().url()).pathname)) }));
  return p;
}
async function save(p, name, opts = {}) {
  if (p.errors.length) throw new Error(`${name}: ${p.errors.join('; ')}`);
  const file = path.join(OUT, name);
  await p.screenshot({ path: file, type: name.endsWith('.png') ? 'png' : 'jpeg', ...(name.endsWith('.png') ? {} : { quality: 82 }), ...opts });
  shots.push(`${name} ${(fs.statSync(file).size / 1024).toFixed(0)} KB`);
}

fs.mkdirSync(OUT, { recursive: true });
for (const lang of ['ar', 'en']) {
  const sfx = lang === 'en' ? '-en' : '';
  for (const [name, s, when] of SAVER) {
    const p = await page();
    await p.clock.install({ time: new Date(when) });
    await p.goto(url('dist/luban.html', '#s=' + enc({ city: 'muscat', lang, ...s })));
    await p.clock.runFor(4000);
    await p.waitForTimeout(1500);   // photo decode and fade
    await save(p, `${name}${sfx}.jpg`);
    if (name === 'saver-chart') {   // the link preview: the same screen at 1200×630
      const o = await page(1920, 1008, 0.625);
      await o.clock.install({ time: new Date(when) });
      await o.goto(url('dist/luban.html', '#s=' + enc({ city: 'muscat', lang, ...s })));
      await o.clock.runFor(4000); await o.waitForTimeout(800);
      await save(o, lang === 'en' ? 'og-en.jpg' : 'og.jpg'); await o.close();
    }
    await p.close();
  }
  const h = await page();
  await h.goto(url('site/screens/home.html', lang === 'en' ? '?lang=en' : ''));
  await h.waitForTimeout(1200);
  await save(h, `home${sfx}.jpg`); await h.close();
}
const q = await page();
await q.goto(url('site/screens/qr.html'));
await q.waitForTimeout(1200);
await save(q, 'qr.jpg');
// the small code for install step 5: the same pattern, 5 px a module, crisp
const mini = await q.evaluate(() => {
  const src = document.getElementById('q'), c = document.createElement('canvas'), k = 5;
  c.width = c.height = src.width * k;
  const x = c.getContext('2d'); x.imageSmoothingEnabled = false; x.fillStyle = '#fff'; x.fillRect(0, 0, c.width, c.height);
  x.drawImage(src, 0, 0, c.width, c.height);
  return c.toDataURL('image/png').split(',')[1];
});
fs.writeFileSync(path.join(OUT, 'qr-mini.png'), Buffer.from(mini, 'base64'));
shots.push('qr-mini.png');
await q.close();
await browser.close();
console.log(shots.join('\n'));
