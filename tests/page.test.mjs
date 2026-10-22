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
  check('settings: a plain-http data server is refused (the default stays)', s.api === 'https://luban.bassamailab.xyz');
  check('settings: the data server can be turned off', sanitize({ api: '' }, P.METHODS).api === '');
  check('settings: unknown keys dropped', !('extra' in s));
  check('settings: background is the sea chart unless photos are chosen', s.bg === 'chart' && sanitize({ bg: 'photos' }, P.METHODS).bg === 'photos' && sanitize({ bg: 'x' }, P.METHODS).bg === 'chart');
  const c = sanitize({ city: 'custom', place: { en: 'Reykjavik', lat: 64.1, lon: -21.9, tz: 'Not/AZone' } }, P.METHODS);
  check('settings: custom place with a bad time zone falls back', c.city === 'muscat' && c.place === null);
  const c2 = sanitize({ city: 'custom', place: { en: 'Zanzibar', lat: -6.16, lon: 39.19, tz: 'Africa/Dar_es_Salaam' } }, P.METHODS);
  check('settings: custom place kept when valid', c2.city === 'custom' && c2.place.tz === 'Africa/Dar_es_Salaam');
}

// ---------- prayer engine against published times
{
  // The Ministry of Awqaf's own calendar app (تقويم عُمان), shared as text on 21 Oct 2026: Fajr Dhuhr Asr Maghrib Isha
  const ministry = {
    muscat:  ['04:51', '11:57', '15:15', '17:42', '18:52'], salalah: ['05:05', '12:13', '15:34', '18:04', '19:11'],
    khasab:  ['05:00', '12:05', '15:21', '17:47', '19:00'], sur:     ['04:46', '11:52', '15:10', '17:38', '18:48'],
    ibri:    ['04:58', '12:04', '15:22', '17:49', '19:00'], sohar:   ['04:58', '12:03', '15:20', '17:47', '18:58'],
  };
  const cities = require('../content/cities.json').cities, five = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'];
  let worst = 0, exact = 0;
  for (const id in ministry) {
    const c = cities.find(x => x.id === id);
    const r = P.compute({ y: 2026, m: 10, d: 21 }, { lat: c.lat, lon: c.lon, tz: 'Asia/Muscat' }, { method: 'oman' });
    five.forEach((k, i) => { const d = Math.abs(Math.round(r[k]) - (+ministry[id][i].slice(0, 2) * 60 + +ministry[id][i].slice(3)));
      worst = Math.max(worst, d); if (!d) exact++; });
  }
  check('prayer: Oman method within a minute of the ministry calendar in 6 wilayats', worst <= 1, `worst ${worst} min, ${exact}/30 exact`);
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
  check('dial: the centre names the next prayer and counts down in words', (await text(page, '#next-name')) === 'الظهر' && (await text(page, '#next-left')).startsWith('بعد ساعتين و'), await text(page, '#next-left'));
  check('dial: a lit grain of frankincense marks now, its smoke drawn', await page.evaluate(() => !!document.querySelector('#dial-svg circle[fill="url(#glow)"]') && document.getElementById('smoke-a').toDataURL().length > 2000));
  check('dial: morning on the right in Arabic (Fajr right of centre, Maghrib left)', await page.evaluate(() =>
    parseFloat(document.querySelector('.pr.fajr').style.left) > 50 && parseFloat(document.querySelector('.pr.maghrib').style.left) < 50));
  check('chart: 48 rhumb lines run out from the dial', await page.evaluate(() => document.querySelectorAll('#rhumbs line').length === 48));
  check('chart: no photos unless chosen', await page.evaluate(() => !document.body.classList.contains('photos')));
  await page.close();
}
{
  const page = await open({ lang: 'en', digits: 'arab', city: 'london', panel: 'verse' });
  check('screen: no script errors (English, London)', page.errors.length === 0, page.errors);
  check('screen: English layout is left-to-right', (await page.evaluate(() => document.documentElement.dir)) === 'ltr');
  check('dial: morning on the left in English', await page.evaluate(() => parseFloat(document.querySelector('.pr.fajr').style.left) < 50));
  check('screen: London clock (06:10 BST)', (await text(page, '#hh')) === '٦');
  check('screen: Arabic-Indic digits when chosen', /[٠-٩]/.test(await text(page, '#mm')));
  await page.close();
}
{
  // 21:00 in Muscat, after Isha: the Fajr tile counts to tomorrow's Fajr and shows tomorrow's time
  const page = await open({ city: 'muscat', clock24: true }, '2026-10-21T17:00:00Z');
  const tom = P.compute({ y: 2026, m: 10, d: 22 }, { lat: 23.59, lon: 58.38, tz: 'Asia/Muscat' }, { method: 'oman' });
  const tile = await page.$eval('#prayers .pr.fajr', e => ({ t: e.querySelector('.t').textContent, next: e.classList.contains('next') }));
  check("prayer bar: after Isha, Fajr shows tomorrow's time", tile.next && tile.t === P.hhmm(tom.fajr), tile);
  await page.close();
}
{
  // Oslo, 21 June, 23:30: Isha (MWL) falls after midnight and is still ahead
  const page = await open({ city: 'custom', place: { en: 'Oslo', lat: 59.91, lon: 10.75, tz: 'Europe/Oslo' }, method: 'mwl', lang: 'en' }, '2026-06-21T21:30:00Z');
  const next = await page.$eval('#prayers .pr.next .n', e => e.textContent);
  check('prayer bar: an Isha after midnight is still the next prayer', next === 'Isha', next);
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
{
  // sources are named on screen, and a price that stopped updating says when it is from
  const page = await open({ markets: ['gold', 'btc'] });
  await page.evaluate(() => {
    const st = window.LubanScreen._state;
    st.weather = { t: 31, code: 0, day: true, dust: false };
    st.markets = { gold: { price: 4000, change: 0.5, at: Date.now() - 5 * 3600e3 }, btc: { price: 100000, change: -1, at: Date.now() } };
    window.LubanScreen._render.weather(); window.LubanScreen._render.markets();
  });
  const credit = await text(page, '#credit'), mk = await text(page, '#markets');
  check('credits: weather and prices name their sources', credit.includes('Open-Meteo') && credit.includes('Yahoo Finance') && credit.includes('CoinGecko'), credit);
  check('markets: gold is labelled a global price; a 5-hour-old price says "as of"', mk.includes('سعر عالمي') && mk.includes('حتى') &&
    (await page.$$('#markets .mk.stale')).length === 1, mk);
  await page.close();
}
{
  // a football-data.org result as the server passes it on: short names, Arabic from teams-ar.json, the source named
  const page = await open({ panel: 'matches', teams: [{ id: '86', en: 'Real Madrid', ar: 'ريال مدريد' }] });
  await page.evaluate(() => {
    const t = new Date(Date.now() - 3 * 3600e3).toISOString().replace(/\.\d+Z$/, 'Z');
    window.LubanScreen._state.matches = { 86: { last: [{ idEvent: '2', idHomeTeam: '81', strHomeTeam: 'Barça', idAwayTeam: '86', strAwayTeam: 'Real Madrid',
      intHomeScore: 1, intAwayScore: 2, strTimestamp: t, strStatus: 'FT', strLeague: 'Primera Division' }], next: [] } };
  });
  await page.clock.runFor(21000);
  const card = await text(page, '#card');
  check('matches: a football-data result reads in Arabic, with its source', card.includes('برشلونة') && card.includes('ريال مدريد') && card.includes('1 - 2') && card.includes('football-data.org'), card);
  await page.close();
}
for (const lang of ['ar', 'en']) {
  // a Latin home name (no Arabic for it) keeps its place: home, its score beside it, then the away team, in reading order
  const page = await open({ lang, panel: 'matches', teams: [{ id: '66', en: 'Manchester United', ar: 'مانشستر يونايتد' }] });
  await page.evaluate(() => {
    const t = new Date(Date.now() - 3 * 3600e3).toISOString().replace(/\.\d+Z$/, 'Z');
    window.LubanScreen._state.matches = { 66: { last: [{ idEvent: '5', idHomeTeam: '7001', strHomeTeam: 'Como 1907', idAwayTeam: '66', strAwayTeam: 'Manchester United',
      intHomeScore: 4, intAwayScore: 1, strTimestamp: t, strStatus: 'FT' }], next: [] } };
  });
  await page.clock.runFor(21000);
  const x = await page.evaluate(() => {
    const node = document.querySelector('#card .rows li .a').firstChild, s = node.data, sc = s.indexOf('4 - 1');
    const at = (i, n) => { const r = document.createRange(); r.setStart(node, i); r.setEnd(node, i + n); const b = r.getBoundingClientRect(); return b.left + b.width / 2; };
    const away = s.indexOf('مانشستر') >= 0 ? s.indexOf('مانشستر') : s.indexOf('Manchester');
    return [at(s.indexOf('Como'), 4), at(sc, 1), at(sc + 4, 1), at(away, 4)];
  });
  const order = lang === 'ar' ? x.every((v, i) => i === 0 || v < x[i - 1]) : x.every((v, i) => i === 0 || v > x[i - 1]);
  check(`matches (${lang}): home, its score, the other score, away — in reading order`, order, x);
  await page.close();
}
for (const vp of [{ width: 1920, height: 1080 }, { width: 1280, height: 720 }]) {
  const page = await open({ panel: 'verse', markets: ['gold', 'brent', 'btc', 'spx'] }, null, vp);
  const overflow = await page.evaluate(() => {
    // worst case: the markets row under the card, one of them stale (an extra line)
    const now = Date.now(), st = window.LubanScreen._state;
    st.markets = { gold: { price: 4000, change: 1, at: now - 5 * 3600e3 }, brent: { price: 66, change: 1, at: now }, btc: { price: 1e5, change: 1, at: now }, spx: { price: 6000, change: 1, at: now } };
    window.LubanScreen._render.markets();
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

{
  // the fallback for WebViews without Umm al-Qura must give the same date as ICU, every day for 30 years
  const page = await open({});
  const bad = await page.evaluate(() => {
    const f = new Intl.DateTimeFormat('en-US-u-ca-islamic-umalqura-nu-latn', { timeZone: 'UTC', day: 'numeric', month: 'numeric', year: 'numeric' });
    const out = [];
    for (let day = Date.UTC(2019, 0, 1) / 864e5; day < Date.UTC(2049, 0, 1) / 864e5; day++) {
      const p = f.formatToParts(new Date(day * 864e5)).reduce((o, x) => (o[x.type] = x.value, o), {});
      const t = window.LubanScreen.tableHijri(day);
      if (!t || t.y !== parseInt(p.year, 10) || t.m !== +p.month || t.d !== +p.day) out.push(day);
    }
    return out.length;
  });
  check('hijri: the fallback table matches Umm al-Qura every day 2019–2048', bad === 0, bad);
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
