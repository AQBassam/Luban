// The screen and the phone settings page in a real browser (Playwright + Chromium), network blocked, clock fixed.
import { chromium } from 'playwright';
import { createRequire } from 'module';
import path from 'path';
import { fileURLToPath } from 'url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(import.meta.url);
const P = require(path.join(ROOT, 'web/js/prayer.js'));
const { sanitize } = require(path.join(ROOT, 'web/js/settings.js'));
let fails = 0;
const check = (name, ok, extra) => { console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${ok || extra === undefined ? '' : ' — ' + JSON.stringify(extra)}`); if (!ok) fails++; };
const enc = s => Buffer.from(JSON.stringify(s)).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

// ---------- settings.js: the one gate for every value
{
  const s = sanitize({ lang: 'fr', city: '../../etc', method: 'nope', offsets: { fajr: 99, bad: 1 }, teams: [{ id: '12', en: 'X', ar: 'س' }, { id: 'drop table' }], api: 'http://plain.example.com', place: { lat: 1 }, extra: 1 }, P.METHODS);
  check('settings: unknown language → ar', s.lang === 'ar');
  check('settings: bad city id → default', s.city === 'muscat');
  check('settings: unknown method → country default', s.method === '');
  check('settings: offsets clamped to ±30, unknown keys dropped', s.offsets.fajr === 30 && !('bad' in s.offsets));
  check('settings: team ids must be numbers', s.teams.length === 1 && s.teams[0].id === '12');
  check('settings: data server must be https', s.api === '');
  check('settings: unknown keys dropped', !('extra' in s));
  const c = sanitize({ city: 'custom', place: { en: 'Reykjavik', lat: 64.1, lon: -21.9, tz: 'Not/AZone' } }, P.METHODS);
  check('settings: custom place with a bad time zone falls back', c.city === 'muscat' && c.place === null);
  const c2 = sanitize({ city: 'custom', place: { en: 'Zanzibar', lat: -6.16, lon: 39.19, tz: 'Africa/Dar_es_Salaam' } }, P.METHODS);
  check('settings: custom place kept when valid', c2.city === 'custom' && c2.place.tz === 'Africa/Dar_es_Salaam');
}

// ---------- prayer engine against published times
{
  const muscat = P.compute({ y: 2026, m: 10, d: 19 }, { lat: 23.588, lon: 58.3829, tz: 'Asia/Muscat' }, { method: 'oman' });
  const got = P.KEYS.map(k => P.hhmm(muscat[k])).join(' ');
  // khaleejcalculators.com/prayer-times/muscat, 19 Oct 2026 (MWL 18/17 as used for Oman): 04:50 06:05 11:52 15:09 17:37 18:48
  const want = ['04:50', '06:05', '11:52', '15:09', '17:37', '18:48'];
  const close = P.KEYS.every((k, i) => Math.abs(muscat[k] - (+want[i].slice(0, 2) * 60 + +want[i].slice(3))) <= 1.5);
  check('prayer: Muscat matches the published table within a minute', close, got);
  const oslo = P.compute({ y: 2026, m: 6, d: 21 }, { lat: 59.91, lon: 10.75, tz: 'Europe/Oslo' }, { method: 'mwl' });
  check('prayer: high latitude in midsummer still gives ordered times', oslo.fajr < oslo.sunrise && oslo.maghrib < oslo.isha + (oslo.isha < 600 ? 1440 : 0));
  const ram = P.compute({ y: 2027, m: 2, d: 15 }, { lat: 21.42, lon: 39.83, tz: 'Asia/Riyadh' }, { method: 'makkah', ramadan: true });
  check('prayer: Umm al-Qura Isha is 120 min after Maghrib in Ramadan', Math.round(ram.isha - ram.maghrib) === 120);
}

const browser = await chromium.launch();
async function open(settings, when, viewport) {
  const page = await browser.newPage({ viewport: viewport || { width: 1920, height: 1080 } });
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.route(/^https?:\/\//, r => r.abort());
  await page.clock.install({ time: new Date(when || '2026-10-21T05:10:00Z') });
  await page.goto('file://' + path.join(ROOT, 'dist/luban.html') + '#s=' + enc(settings || {}));
  await page.clock.runFor(3000);
  page.errors = errors;
  return page;
}
const text = (page, sel) => page.$eval(sel, e => e.textContent);

// ---------- the screen
{
  const page = await open({ panel: 'prayers' });
  check('screen: no script errors (Arabic, Muscat)', page.errors.length === 0, page.errors);
  check('screen: city shown', (await text(page, '#city')) === 'مسقط');
  check('screen: clock in Muscat time (09:10)', (await text(page, '#hh')) === '9' && (await text(page, '#mm')) === '10');
  const bar = await page.$$eval('#prayers .pr .t', els => els.map(e => e.textContent));
  check('screen: six prayer tiles', bar.length === 6, bar);
  check('screen: Dhuhr is next and highlighted', (await page.$eval('#prayers .pr.next .n', e => e.textContent)) === 'الظهر');
  check('screen: National Day countdown on 21 October', (await text(page, '#chips')).includes('العيد الوطني') && (await text(page, '#chips')).includes('30'));
  check('screen: Western digits by default', /\d/.test(await text(page, '#hh')));
  await page.close();
}
{
  const page = await open({ lang: 'en', digits: 'arab', city: 'london', panel: 'verse' });
  check('screen: no script errors (English, London)', page.errors.length === 0, page.errors);
  check('screen: English layout is left-to-right', (await page.evaluate(() => document.documentElement.dir)) === 'ltr');
  check('screen: London clock (06:10 BST)', (await text(page, '#hh')) === '٦');
  check('screen: Arabic-Indic digits when chosen', /[٠-٩]/.test(await text(page, '#mm')));
  await page.close();
}
{
  const page = await open({ city: 'muscat' }, '2026-11-20T08:00:00Z');
  check('screen: National Day greeting on 20 November', (await text(page, '#chips')).includes('كل عام وعُمان بخير'));
  await page.close();
}
{
  const s = await open({ city: 'salalah' }, '2026-07-15T08:00:00Z'), m = await open({ city: 'muscat' }, '2026-07-15T08:00:00Z');
  check('screen: Khareef season shown in Salalah in July', (await text(s, '#chips')).includes('خريف'));
  check('screen: … and not in Muscat', !(await text(m, '#chips')).includes('خريف'));
  await s.close(); await m.close();
}
{
  const page = await open({ city: 'muscat' }, '2026-10-21T21:30:00Z');   // 01:30 in Muscat
  check('screen: dimmed at night', await page.evaluate(() => document.body.classList.contains('night')));
  await page.close();
}
{
  // a hostile team name can never become markup
  const evil = '<img src=x onerror="window.pwned=1">';
  const page = await open({ panel: 'matches', teams: [{ id: '1', en: 'X', ar: evil }] });
  await page.evaluate(ev => {
    const now = Date.now();
    window.LubanScreen._state.matches = { 1: { next: [{ idEvent: '9', idHomeTeam: '1', strHomeTeam: 'X', idAwayTeam: '2', strAwayTeam: ev, strTimestamp: new Date(now + 3600e3).toISOString() }], last: [] } };
  }, evil);
  await page.clock.runFor(21000);
  check('screen: team names are text, never HTML', (await page.$$('#card img, #match img')).length === 0 && !(await page.evaluate(() => window.pwned)));
  await page.close();
}
for (const vp of [{ width: 1920, height: 1080 }, { width: 1280, height: 720 }]) {
  const page = await open({ panel: 'verse' }, null, vp);
  const overflow = await page.evaluate(() => {
    const D = window.LUBAN_DATA, card = document.getElementById('card'), body = document.getElementById('card-body'), bad = [];
    const items = D.verses.map(v => ({ cls: 'quote', t: v.text })).concat(D.hadith.map(h => ({ cls: 'quote hadith', t: h.text })));
    for (const it of items) {
      body.textContent = '';
      const q = document.createElement('div'); q.className = it.cls; q.textContent = it.t;
      const s = document.createElement('div'); s.className = 'src'; s.textContent = 'x';
      body.append(q, s);
      window.LubanScreen.fitCard();
      if (card.scrollHeight > card.clientHeight + 1) bad.push(it.t.slice(0, 40));
    }
    return { n: items.length, bad };
  });
  check(`screen: every verse and hadith fits the card (font steps down at most 30%) at ${vp.width}x${vp.height} (${overflow.n})`, overflow.bad.length === 0, overflow.bad.slice(0, 3));
  await page.close();
}

// ---------- the phone settings page
{
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  const errors = []; page.on('pageerror', e => errors.push(e.message));
  await page.route(/^https?:\/\//, r => r.abort());
  await page.goto('file://' + path.join(ROOT, 'dist/screen/settings.html'));
  await page.waitForTimeout(800);
  check('phone: no script errors', errors.length === 0, errors);
  const groups = await page.$$eval('#city optgroup', g => g.map(x => x.label));
  check('phone: all 11 Omani governorates listed first', groups.slice(0, 11).every(l => l.startsWith('سلطنة عُمان')) && groups.length > 11, groups.slice(0, 12));
  const times = await page.$$eval('#preview b', b => b.map(x => x.textContent));
  check('phone: live prayer preview', times.length === 6 && times.every(t => /^\d\d:\d\d$/.test(t)), times);
  const wide = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
  check('phone: no sideways scrolling at 390px', wide);
  await page.selectOption('#lang', 'en');
  check('phone: switches to English', (await page.textContent('#save')) === 'Save');
  await page.close();
}

await browser.close();
if (fails) { console.log(`\n${fails} page test(s) failed`); process.exit(1); }
console.log('\nall page tests passed');
