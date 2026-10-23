/* Luban — the one settings shape, shared by the screen, the phone settings page and the apps.
 * sanitize() is the single gate: anything stored or received passes through it, unknown keys are dropped,
 * and every value is checked against what it may be. */
(function (root) {
  'use strict';
  const PANELS = ['verse', 'prayers', 'forecast', 'matches', 'shows', 'tasks', 'dinner', 'none'];
  const MARKETS = ['gold', 'brent', 'btc', 'spx'];
  const PRAYERS5 = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'];

  const DEFAULTS = {
    v: 1,
    lang: 'ar',                 // ar | en
    digits: 'latn',             // latn (0-9) | arab (٠-٩)
    clock24: false,
    city: 'muscat',             // an id from content/cities.json, or 'custom' with place{}
    place: null,                // {ar, en, lat, lon, tz} when city === 'custom'
    method: '',                 // '' = the country's default
    asr: 'standard',            // standard | hanafi
    offsets: { fajr: 0, sunrise: 0, dhuhr: 0, asr: 0, maghrib: 0, isha: 0 },
    iqama: { fajr: 25, dhuhr: 20, asr: 20, maghrib: 10, isha: 20 },
    hijriOffset: 0,             // -2..2 days, for countries that follow a local moon sighting
    panel: 'verse',
    homeCard: false,            // the launcher's card
    teams: [],                  // [{id, en, ar}] football-data.org team ids, priority order
    markets: ['gold', 'brent', 'btc', 'spx'],
    tasks: [],                  // [{t, who, when, done}] household tasks, written on the phone page, kept on this TV only
    dinner: null,               // the dinner plan from SmartDinner (one cycle, which repeats): see sanitizeDinner(). Pasted on the phone page
    shows: { genres: [], langs: [], minRating: 7 },
    omanDays: true,             // National Day countdown, Khareef season
    night: true,                // dim after Isha until before Fajr
    bg: 'chart',                // chart (the sea chart) | photos (Omani photos under a veil)
    api: 'https://luban.bassamailab.xyz',   // the shared data server (server/); '' = every TV asks the sources itself
    channel: 'stable',          // stable | canary: which signed screen updates this TV takes
  };

  const isObj = x => x && typeof x === 'object' && !Array.isArray(x);
  const str = (x, max) => typeof x === 'string' ? x.slice(0, max) : null;
  const num = (x, lo, hi) => (typeof x === 'number' || (typeof x === 'string' && x.trim() !== '')) && isFinite(+x)
    ? Math.min(hi, Math.max(lo, +x)) : null;
  const pick = (x, list, d) => list.includes(x) ? x : d;
  const validTz = tz => { try { new Intl.DateTimeFormat('en', { timeZone: tz }); return true; } catch (e) { return false; } };

  // The dinner plan SmartDinner shares as a code (the phone page unpacks it): one cycle of nights, repeated by the screen.
  // Day k has r (tonight: title, emoji, time to start), m (tomorrow), and the tasks s/a/e (shopping day, after lunch,
  // evening) as [text, minutes]; p is the day before the first dinner. Texts are shown as text, never as markup.
  const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;
  const isoDate = x => typeof x === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(x) && !isNaN(Date.parse(x + 'T00:00:00Z'));
  const dinnerTasks = a => Array.isArray(a)
    ? a.filter(x => Array.isArray(x) && typeof x[0] === 'string' && x[0].trim()).slice(0, 12)
        .map(x => [x[0].trim().slice(0, 400), Math.round(num(x[1], 0, 240) || 0)]) : [];
  function dinnerDay(x) {
    const o = {};
    if (!isObj(x)) return o;
    if (Array.isArray(x.r) && typeof x.r[0] === 'string' && x.r[0].trim() && HHMM.test(x.r[2])) o.r = [x.r[0].trim().slice(0, 80), str(x.r[1], 8) || '', x.r[2]];
    if (Array.isArray(x.m) && typeof x.m[0] === 'string' && x.m[0].trim()) o.m = [x.m[0].trim().slice(0, 80), str(x.m[1], 8) || ''];
    for (const k of ['s', 'a', 'e']) { const t = dinnerTasks(x[k]); if (t.length) o[k] = t; }
    const b = num(x.b, 1, 10); if (b !== null) o.b = Math.round(b);
    return o;
  }
  function sanitizeDinner(x) {
    if (!isObj(x) || x.v !== 1) return null;
    const n = num(x.n, 1, 21);
    if (n === null || n !== Math.round(n) || !isoDate(x.st) || !Array.isArray(x.d) || x.d.length !== n) return null;
    if (![x.dn, x.ask, x.prep, x.sh].every(t => typeof t === 'string' && HHMM.test(t))) return null;
    return { v: 1, n, st: x.st, dn: x.dn, ask: x.ask, prep: x.prep, sh: x.sh, d: x.d.map(dinnerDay), p: dinnerDay(x.p) };
  }

  function sanitize(input, methods) {
    const s = JSON.parse(JSON.stringify(DEFAULTS));
    if (!isObj(input)) return s;
    s.lang = pick(input.lang, ['ar', 'en'], s.lang);
    s.digits = pick(input.digits, ['latn', 'arab'], s.digits);
    s.clock24 = input.clock24 === true;
    if (typeof input.city === 'string' && /^[a-z0-9_-]{1,40}$/.test(input.city)) s.city = input.city;
    if (s.city === 'custom') {
      const p = isObj(input.place) ? input.place : {};
      const lat = num(p.lat, -66, 66), lon = num(p.lon, -180, 180);   // beyond ±66° prayer times stop making sense
      if (lat !== null && lon !== null && typeof p.tz === 'string' && validTz(p.tz)) {
        s.place = { ar: str(p.ar, 40) || str(p.en, 40) || '', en: str(p.en, 40) || str(p.ar, 40) || '', lat, lon, tz: p.tz };
      } else {
        s.city = DEFAULTS.city;
      }
    }
    if (typeof input.method === 'string' && (input.method === '' || !methods || methods[input.method])) s.method = input.method;
    s.asr = pick(input.asr, ['standard', 'hanafi'], s.asr);
    if (isObj(input.offsets)) for (const k of Object.keys(s.offsets)) { const n = num(input.offsets[k], -30, 30); if (n !== null) s.offsets[k] = Math.round(n); }
    if (isObj(input.iqama)) for (const k of PRAYERS5) { const n = num(input.iqama[k], 0, 60); if (n !== null) s.iqama[k] = Math.round(n); }
    const h = num(input.hijriOffset, -2, 2); if (h !== null) s.hijriOffset = Math.round(h);
    s.panel = pick(input.panel, PANELS, s.panel);
    s.homeCard = input.homeCard === true;
    if (Array.isArray(input.teams)) {
      s.teams = input.teams.filter(t => isObj(t) && /^\d{1,9}$/.test(String(t.id))).slice(0, 10)
        .map(t => ({ id: String(t.id), en: str(t.en, 60) || '', ar: str(t.ar, 60) || '' }));
    }
    if (Array.isArray(input.markets)) s.markets = [...new Set(input.markets.filter(m => MARKETS.includes(m)))];
    if (Array.isArray(input.tasks)) {   // up to 20, each a short line of text; who and when are free words ("أحمد", "الخميس")
      s.tasks = input.tasks.filter(x => isObj(x) && typeof x.t === 'string' && x.t.trim()).slice(0, 20)
        .map(x => ({ t: x.t.trim().slice(0, 80), who: (str(x.who, 24) || '').trim(), when: (str(x.when, 24) || '').trim(), done: x.done === true }));
    }
    s.dinner = sanitizeDinner(input.dinner);
    if (isObj(input.shows)) {
      const sh = input.shows;
      if (Array.isArray(sh.genres)) s.shows.genres = sh.genres.filter(g => typeof g === 'string' && /^[A-Za-z-]{2,20}$/.test(g)).slice(0, 16);
      if (Array.isArray(sh.langs)) s.shows.langs = sh.langs.filter(g => typeof g === 'string' && /^[A-Za-z]{2,20}$/.test(g)).slice(0, 12);
      const r = num(sh.minRating, 0, 10); if (r !== null) s.shows.minRating = r;
    }
    s.omanDays = input.omanDays !== false;
    s.night = input.night !== false;
    s.bg = pick(input.bg, ['chart', 'photos'], s.bg);
    if (input.api === '') s.api = '';   // the owner turned the shared server off
    else if (typeof input.api === 'string' && /^https:\/\/[A-Za-z0-9.-]+(:\d{2,5})?(\/[A-Za-z0-9._~\/-]*)?$/.test(input.api)) s.api = input.api.replace(/\/+$/, '');
    s.channel = pick(input.channel, ['stable', 'canary'], s.channel);
    return s;
  }

  /** The place (name, coordinates, zone) and the effective method for these settings. */
  function resolvePlace(s, cities) {
    if (s.city === 'custom' && s.place) {
      return { id: 'custom', ar: s.place.ar, en: s.place.en, lat: s.place.lat, lon: s.place.lon, tz: s.place.tz,
               country: '', gov: '', method: s.method || 'mwl' };
    }
    const c = cities.cities.find(x => x.id === s.city) || cities.cities.find(x => x.id === DEFAULTS.city);
    const country = cities.countries[c.c] || {};
    return { id: c.id, ar: c.ar, en: c.en, lat: c.lat, lon: c.lon, tz: c.tz || country.tz, country: c.c, gov: c.gov || '',
             method: s.method || country.method || 'mwl' };
  }

  root.LubanSettings = { DEFAULTS, PANELS, MARKETS, sanitize, sanitizeDinner, resolvePlace };
  if (typeof module !== 'undefined' && module.exports) module.exports = root.LubanSettings;
})(typeof window !== 'undefined' ? window : globalThis);
