// The promo site (site/) built as it ships, served with the production security headers, in a real browser.
import { chromium } from 'playwright';
import { execFileSync } from 'child_process';
import http from 'http';
import fs from 'fs';
import os from 'os';
import path from 'path';
import { fileURLToPath } from 'url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
let fails = 0;
const check = (name, ok, extra) => { console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${ok || extra === undefined ? '' : ' — ' + JSON.stringify(extra)}`); if (!ok) fails++; };

// ---------- build exactly what the release carries
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'lubantv-'));
const py = process.platform === 'win32' ? 'py' : 'python3';
execFileSync(py, [path.join(ROOT, 'site/build.py'), TMP, '--version', 'test', '--tar'], { stdio: 'pipe' });
const REL = path.join(TMP, 'lubantv-site-test'), WWW = path.join(REL, 'site');
{
  const again = fs.mkdtempSync(path.join(os.tmpdir(), 'lubantv-'));
  execFileSync(py, [path.join(ROOT, 'site/build.py'), again, '--version', 'test', '--tar'], { stdio: 'pipe' });
  const a = fs.readFileSync(path.join(TMP, 'lubantv-site-test.tar.gz')), b = fs.readFileSync(path.join(again, 'lubantv-site-test.tar.gz'));
  check('build: the release archive is reproducible (byte-identical twice)', a.equals(b));
  fs.rmSync(again, { recursive: true, force: true });
  const sums = fs.readFileSync(path.join(REL, 'SHA256SUMS'), 'utf8').trim().split('\n');
  const listed = new Set(sums.map(l => l.split('  ./')[1]));
  const walk = d => fs.readdirSync(d, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(path.join(d, e.name)) : [path.relative(WWW, path.join(d, e.name)).split(path.sep).join('/')]);
  const all = walk(WWW);
  check('build: SHA256SUMS covers every served file', all.length === listed.size && all.every(f => listed.has(f)), all.filter(f => !listed.has(f)));
  check('build: the release carries the deploy scripts', ['install.sh', 'verify.sh', 'rollback.sh', 'nginx/lubantv-security-headers.conf'].every(f => fs.existsSync(path.join(REL, 'deploy', f))));
  const v = JSON.parse(fs.readFileSync(path.join(WWW, 'version.json'), 'utf8'));
  check('build: version.json names the version and a content hash', v.version === 'test' && /^[0-9a-f]{16}$/.test(v.contentHash));
}

// ---------- serve it like nginx: same CSP and headers (read from the snippet), same types
const SNIP = fs.readFileSync(path.join(ROOT, 'site/deploy/nginx/lubantv-security-headers.conf'), 'utf8');
const HEADERS = {};
for (const m of SNIP.matchAll(/^add_header ([\w-]+) "([^"]*)" always;/gm)) HEADERS[m[1]] = m[2];
check('headers: the CSP allows nothing outside the site', /^default-src 'none';/.test(HEADERS['Content-Security-Policy'] || '') &&
  !/https?:|\*|'unsafe-/.test(HEADERS['Content-Security-Policy']));
const TYPES = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'application/javascript', '.json': 'application/json',
  '.svg': 'image/svg+xml', '.jpg': 'image/jpeg', '.png': 'image/png', '.woff2': 'font/woff2' };
const server = http.createServer((req, res) => {
  let p = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (p.endsWith('/')) p += 'index.html';
  const f = path.join(WWW, p);
  if (!f.startsWith(WWW) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404, HEADERS); return res.end('not found'); }
  res.writeHead(200, { ...HEADERS, 'Content-Type': TYPES[path.extname(f)] || 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const BASE = `http://127.0.0.1:${server.address().port}`;

const browser = await chromium.launch();
for (const [lang, url] of [['ar', '/'], ['en', '/en/']]) {
  for (const [vw, vh] of [[1440, 900], [820, 1180], [390, 844]]) {
    const ctx = await browser.newContext({ viewport: { width: vw, height: vh } });
    const page = await ctx.newPage();
    const problems = [], outside = [];
    page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') problems.push(m.text()); });
    page.on('pageerror', e => problems.push(e.message));
    page.on('requestfailed', r => problems.push('failed ' + r.url()));
    page.on('response', r => { if (r.status() >= 400) problems.push(`${r.status()} ${r.url()}`); });
    page.on('request', r => { if (!r.url().startsWith(BASE) && !r.url().startsWith('data:')) outside.push(r.url()); });
    await page.goto(BASE + url, { waitUntil: 'networkidle' });
    // lazy images: scroll through the page so every one loads
    await page.evaluate(async () => { for (let y = 0; y < document.body.scrollHeight; y += 600) { window.scrollTo(0, y); await new Promise(r => setTimeout(r, 40)); } window.scrollTo(0, 0); });
    await page.waitForLoadState('networkidle');
    const tag = `${lang} ${vw}px`;
    check(`${tag}: no errors, CSP violations or missing files`, problems.length === 0, problems);
    check(`${tag}: nothing requested from outside the site`, outside.length === 0, outside);
    const r = await page.evaluate(() => ({
      lang: document.documentElement.lang, dir: document.documentElement.dir,
      wide: document.documentElement.scrollWidth - innerWidth,
      broken: [...document.images].filter(i => !i.complete || !i.naturalWidth).map(i => i.src),
      noAlt: [...document.images].filter(i => !i.hasAttribute('alt')).map(i => i.src),
      deadAnchors: [...document.querySelectorAll('a[href^="#"]')].map(a => a.getAttribute('href')).filter(h => h.length > 1 && !document.querySelector(h)),
      inlineStyle: document.querySelectorAll('[style]').length + document.querySelectorAll('style').length,
      fonts: [...document.fonts].filter(f => f.status === 'loaded').map(f => f.family + ' ' + f.weight),
      h1: document.querySelectorAll('h1').length,
      wordmarks: document.querySelectorAll('img.lb[alt="لُبان"]').length,
      plainName: (document.querySelector('main').innerText.match(/لُبان/g) || []).length,
    }));
    check(`${tag}: lang and direction`, lang === 'ar' ? r.lang === 'ar' && r.dir === 'rtl' : r.lang === 'en' && r.dir === 'ltr', r);
    check(`${tag}: no sideways scroll`, r.wide <= 0, r.wide);
    check(`${tag}: every image loads and has alt text`, r.broken.length === 0 && r.noAlt.length === 0, { broken: r.broken, noAlt: r.noAlt });
    check(`${tag}: every in-page link has a target`, r.deadAnchors.length === 0, r.deadAnchors);
    check(`${tag}: no inline styles (the CSP would block them)`, r.inlineStyle === 0, r.inlineStyle);
    check(`${tag}: El Messiri loaded`, r.fonts.some(f => f.startsWith('Messiri')), r.fonts);
    check(`${tag}: one h1`, r.h1 === 1);
    if (lang === 'ar' && vw === 1440) {
      // the name is drawn as the wordmark everywhere except inside the request that gets copied as text
      check('ar: «لُبان» is the wordmark in running text (only the copied request spells it)', r.wordmarks >= 9 && r.plainName === 1, r);
    }
    if (vw === 820) {
      // tablet: the feature cards in two columns, the dinner tag inside the page
      const t = await page.evaluate(() => ({
        cols: getComputedStyle(document.querySelector('.grid4')).gridTemplateColumns.split(' ').length,
        tag: (b => Math.min(b.left, innerWidth - b.right))(document.querySelector('.tv .tag').getBoundingClientRect()),
        menu: getComputedStyle(document.querySelector('nav .menu')).display,
      }));
      check(`${tag}: two-column cards, the dinner tag inside the page, no menu button`, t.cols === 2 && t.tag >= 16 && t.menu === 'none', t);
    }
    if (vw === 390) {
      // phone: the menu button opens the section links, and a link closes them again
      const menu = page.locator('nav button.menu');
      const shut = await page.locator('nav .links').isVisible();
      await menu.click();
      const open = await page.locator('nav .links a[href="#install"]').isVisible() && await menu.getAttribute('aria-expanded') === 'true';
      // focus moves into the links, which come before the button in the page
      check(`${tag}: opening the menu puts focus on its first link`, await page.evaluate(() => document.activeElement.matches('nav .links a:first-child')));
      await page.keyboard.press('Escape');
      const esc = await menu.getAttribute('aria-expanded') === 'false' && await page.evaluate(() => document.activeElement.matches('button.menu'));
      await menu.click();
      await page.locator('nav .links a[href="#install"]').click();
      const closed = !(await page.locator('nav .links').isVisible()) && await menu.getAttribute('aria-expanded') === 'false';
      check(`${tag}: the menu opens, Escape and a link close it`, !shut && open && esc && closed, { shut, open, esc, closed });
    }
    if (vw === 1440) {
      // the copy buttons put the exact text on the clipboard
      await ctx.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: BASE });
      const btn = page.locator('.prompt button.copy');
      const want = await btn.getAttribute('data-copy');
      await btn.click();
      const got = await page.evaluate(() => navigator.clipboard.readText());
      check(`${tag}: «copy» puts the assistant request on the clipboard`, got === want && /https:\/\/github\.com\/AQBassam\/Luban/.test(got), got);
      check(`${tag}: the button says it copied`, await btn.evaluate(b => b.classList.contains('done')));
      await page.locator('details.manual summary').click();
      const cmd = page.locator('.cmd button.copy').last();
      await cmd.click();
      check(`${tag}: the terminal steps open and each command copies`, await page.evaluate(() => navigator.clipboard.readText()) === 'python tools/luban.py setup');
      // the live region is filled 50 ms after the copy, so screen readers hear a repeat as new
      const said = await page.waitForFunction(() => document.querySelector('.sr[role=status][aria-live=polite]').textContent, null, { timeout: 2000 }).then(h => h.jsonValue(), () => '');
      check(`${tag}: the result of a copy is announced`, said === (lang === 'ar' ? 'نُسخ' : 'Copied'), said);
      check(`${tag}: the hero image loads first`, await page.getAttribute('.tv img', 'fetchpriority') === 'high');
      // a browser that refuses the clipboard: the button says so, and the text is selected for copying by hand
      const ctx2 = await browser.newContext({ viewport: { width: vw, height: vh } });
      const p2 = await ctx2.newPage();
      await p2.addInitScript(() => {
        Object.defineProperty(navigator, 'clipboard', { value: { writeText: () => Promise.reject(new Error('refused')) } });
        document.execCommand = () => false;
      });
      await p2.goto(BASE + url, { waitUntil: 'networkidle' });
      const pb = p2.locator('.prompt button.copy');
      await pb.click();
      await p2.waitForTimeout(150);
      const refused = await p2.evaluate(() => ({ fail: document.querySelector('.prompt button.copy').classList.contains('fail'),
        sel: String(getSelection()), want: document.querySelector('.prompt button.copy').dataset.copy, said: document.querySelector('.sr[role=status]').textContent }));
      check(`${tag}: a refused copy selects the request and says to copy it by hand`, refused.fail && refused.said !== '' &&
        refused.sel.length > 40 && !/[«»“”]/.test(refused.sel) && refused.want.includes('AQBassam/Luban') && refused.sel.includes('AQBassam/Luban'), refused);
      await ctx2.close();
    }
    await ctx.close();
  }
}
// the screenshots say nothing about a real TV: no home network address, no settings token
{
  const qr = fs.readFileSync(path.join(ROOT, 'site/screens/qr.html'), 'utf8');
  check('screens: the settings-code demo shows a made-up address and no token', /192\.168\.1\.20:8737\/…/.test(qr) && !/192\.168\.100\./.test(qr) && !/[0-9a-f]{32}/.test(qr));
}
await browser.close();
server.close();
fs.rmSync(TMP, { recursive: true, force: true });
console.log(fails ? `\n${fails} FAILED` : '\nall site checks passed');
process.exit(fails ? 1 : 0);
