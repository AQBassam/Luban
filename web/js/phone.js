/* Luban — the phone settings page. Served by the TV at http://<tv>:<port>/<token>/ ; it talks only to that same
 * address: GET settings.json, POST settings.json, GET teams?q=. The token in the path is the pairing secret shown
 * in the QR code on the TV, so another device on the network cannot change the settings without seeing the screen. */
(function () {
  'use strict';
  const { METHODS, compute, hhmm } = window.LubanPrayer;
  const { sanitize, resolvePlace, DEFAULTS } = window.LubanSettings;
  const DATA = window.LUBAN_DATA;
  const $ = id => document.getElementById(id);
  const h = (tag, attrs, text) => { const e = document.createElement(tag); Object.assign(e, attrs || {}); if (text != null) e.textContent = text; return e; };
  const BASE = location.pathname.replace(/[^/]*$/, '');   // /<token>/
  const PR = ['fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha'];
  const GENRES = ['Drama', 'Comedy', 'Action', 'Crime', 'Thriller', 'Mystery', 'Science-Fiction', 'Fantasy', 'Adventure', 'Family', 'History', 'Documentary', 'Anime', 'Romance', 'Horror'];
  const EN = {
    title: 'Luban settings', place: 'Place and time', city: 'City', nameAr: 'Arabic name', nameEn: 'English name', lat: 'Latitude', lon: 'Longitude',
    tz: 'Time zone (IANA)', lang: 'Language', digits: 'Digits', clock24: '24-hour clock', prayer: 'Prayer', method: 'Calculation method', asr: 'Asr',
    asrStd: 'Standard (shadow ×1)', asrHan: 'Hanafi (shadow ×2)', offsets: 'Adjust in minutes (to match the local calendar)', iqama: 'Iqama, minutes after adhan',
    hijri: 'Hijri date adjustment', screen: 'Screen', panel: 'Card beside the clock', pVerse: 'Verse or hadith of the day', pPrayers: "Today's prayers and iqama",
    pForecast: 'Weather, next days', pMatches: 'Your teams', pShows: 'New episodes', pNone: 'No card', omanDays: 'Oman National Day and Khareef season',
    night: 'Dim at night', bg: 'Background', bgChart: 'The sea chart', bgPhotos: 'Photos of Oman', homeCard: 'Prayer card on the home screen', markets: 'Markets', teams: 'Your teams', search: 'Search',
    teamsHint: 'Up to 10 teams. Edit the Arabic name as you like it shown.', shows: 'TV shows', genres: 'Genres (empty = all)', minRating: 'Minimum rating',
    advanced: 'Advanced', api: 'Data server (optional)', channel: 'Update channel', stable: 'Stable', canary: 'Canary (gets updates first)', save: 'Save',
    saved: 'Saved. The screen updates now.', failed: 'Not saved: ', custom: 'Other place…', default: 'Country default', teamSearch: 'Search in English: Oman, Al Seeb…',
    gold: 'Gold (OMR/g)', brent: 'Brent oil', btc: 'Bitcoin', spx: 'S&P 500', noHits: 'No team found',
  };
  const AR = { custom: 'مكان آخر…', default: 'حسب البلد', saved: 'حُفظت. الشاشة تتحدث الآن.', failed: 'لم تُحفظ: ', gold: 'الذهب (ر.ع/غ)', brent: 'نفط برنت', btc: 'بيتكوين', spx: 'S&P 500', noHits: 'لا نتائج' };
  let S = sanitize(null, METHODS), lang = 'ar';
  const t = k => lang === 'en' ? (EN[k] || k) : (AR[k] || (document.querySelector(`[data-t="${k}"]`) || {}).dataset?.ar || k);

  function translate() {
    document.documentElement.lang = lang; document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
    document.querySelectorAll('[data-t]').forEach(e => { if (!e.dataset.ar) e.dataset.ar = e.textContent; e.textContent = lang === 'en' ? (EN[e.dataset.t] || e.dataset.ar) : e.dataset.ar; });
    document.querySelectorAll('[data-ph]').forEach(e => { if (!e.dataset.arph) e.dataset.arph = e.placeholder; e.placeholder = lang === 'en' ? EN[e.dataset.ph] : e.dataset.arph; });
  }

  function fillCities() {
    const sel = $('city'); sel.textContent = '';
    const byCountry = {};
    for (const c of DATA.cities.cities) (byCountry[c.c] = byCountry[c.c] || []).push(c);
    for (const code of Object.keys(DATA.cities.countries)) {
      if (!byCountry[code]) continue;
      const co = DATA.cities.countries[code];
      if (code === 'OM') {   // Oman by governorate
        const govs = {};
        for (const c of byCountry.OM) (govs[c.gov] = govs[c.gov] || []).push(c);
        for (const g of Object.keys(DATA.cities.governorates)) {
          if (!govs[g]) continue;
          const grp = h('optgroup', { label: `${co[lang]} — ${DATA.cities.governorates[g][lang]}` });
          govs[g].forEach(c => grp.append(h('option', { value: c.id }, c[lang])));
          sel.append(grp);
        }
      } else {
        const grp = h('optgroup', { label: co[lang] });
        byCountry[code].forEach(c => grp.append(h('option', { value: c.id }, c[lang])));
        sel.append(grp);
      }
    }
    sel.append(h('option', { value: 'custom' }, t('custom')));
  }
  function fillMethods() {
    const sel = $('method'); sel.textContent = '';
    sel.append(h('option', { value: '' }, t('default')));
    for (const [k, m] of Object.entries(METHODS)) sel.append(h('option', { value: k }, m.label[lang]));
  }
  function numberGrid(id, keys, values, lo, hi) {
    const box = $(id); box.textContent = '';
    for (const k of keys) {
      const lab = h('label'); lab.append(h('span', {}, (lang === 'ar' ? { fajr: 'الفجر', sunrise: 'الشروق', dhuhr: 'الظهر', asr: 'العصر', maghrib: 'المغرب', isha: 'العشاء' } : { fajr: 'Fajr', sunrise: 'Sunrise', dhuhr: 'Dhuhr', asr: 'Asr', maghrib: 'Maghrib', isha: 'Isha' })[k]));
      const inp = h('input', { type: 'number', min: lo, max: hi, value: values[k], dir: 'ltr' }); inp.dataset.k = k;
      inp.addEventListener('input', preview); lab.append(inp); box.append(lab);
    }
  }
  function checks(id, items, chosen, label) {
    const box = $(id); box.textContent = '';
    for (const v of items) {
      const lab = h('label', { className: 'check' }), inp = h('input', { type: 'checkbox', checked: chosen.includes(v) });
      inp.dataset.v = v; lab.append(inp, h('span', {}, label ? label(v) : v)); box.append(lab);
    }
  }
  const checked = id => [...$(id).querySelectorAll('input:checked')].map(i => i.dataset.v);
  const grid = id => Object.fromEntries([...$(id).querySelectorAll('input')].map(i => [i.dataset.k, +i.value || 0]));

  function renderTeams() {
    const box = $('teams'); box.textContent = '';
    S.teams.forEach((tm, i) => {
      const row = h('div', { className: 'team' });
      const ar = h('input', { type: 'text', value: tm.ar || tm.en, maxLength: 60 });
      ar.addEventListener('input', () => { S.teams[i].ar = ar.value; });
      const up = h('button', { type: 'button', title: '↑' }, '↑'); up.onclick = () => { if (i) { [S.teams[i - 1], S.teams[i]] = [S.teams[i], S.teams[i - 1]]; renderTeams(); } };
      const del = h('button', { type: 'button', title: '×' }, '×'); del.onclick = () => { S.teams.splice(i, 1); renderTeams(); };
      row.append(h('span', { dir: 'ltr' }, tm.en), ar, up, del); box.append(row);
    });
  }
  async function searchTeams() {
    const q = $('tq').value.trim(), box = $('hits'); box.textContent = '';
    if (q.length < 2) return;
    try {
      const r = await fetch(BASE + 'teams?q=' + encodeURIComponent(q)); const list = await r.json();
      if (!list.length) box.append(h('div', { className: 'hint' }, t('noHits')));
      list.slice(0, 8).forEach(tm => {
        const row = h('div', { className: 'hit' }); const add = h('button', { type: 'button' }, '+');
        add.onclick = () => { if (S.teams.length < 10 && !S.teams.some(x => x.id === tm.id)) { S.teams.push(tm); renderTeams(); } box.textContent = ''; $('tq').value = ''; };
        row.append(h('span', { dir: 'auto' }, `${tm.ar ? tm.ar + ' · ' : ''}${tm.en}${tm.league ? ' — ' + tm.league : ''}`), add); box.append(row);
      });
    } catch (e) { box.append(h('div', { className: 'hint' }, String(e))); }
  }

  function collect() {
    const custom = $('city').value === 'custom';
    return sanitize({
      ...S, lang: $('lang').value, digits: $('digits').value, clock24: $('clock24').checked, city: $('city').value,
      place: custom ? { ar: $('c-ar').value, en: $('c-en').value, lat: +$('c-lat').value, lon: +$('c-lon').value, tz: $('c-tz').value.trim() } : null,
      method: $('method').value, asr: $('asr').value, offsets: grid('offsets'), iqama: grid('iqama'), hijriOffset: +$('hijri').value,
      panel: $('panel').value, bg: $('bg').value, omanDays: $('omanDays').checked, night: $('night').checked, homeCard: $('homeCard').checked,
      markets: checked('markets'), shows: { genres: checked('genres'), langs: S.shows.langs, minRating: +$('minRating').value },
      api: $('api').value.trim(), channel: $('channel').value,
    }, METHODS);
  }
  function preview() {
    const s = collect(), p = resolvePlace(s, DATA.cities), now = new Date();
    const d = new Intl.DateTimeFormat('en-CA', { timeZone: p.tz }).format(now).split('-').map(Number);
    const tm = compute({ y: d[0], m: d[1], d: d[2] }, p, { method: p.method, asr: s.asr, offsets: s.offsets });
    const box = $('preview'); box.textContent = '';
    const names = lang === 'ar' ? ['الفجر', 'الشروق', 'الظهر', 'العصر', 'المغرب', 'العشاء'] : ['Fajr', 'Sunrise', 'Dhuhr', 'Asr', 'Maghrib', 'Isha'];
    PR.forEach((k, i) => { const c = h('div'); c.append(h('span', {}, names[i]), h('b', { dir: 'ltr' }, hhmm(tm[k]))); box.append(c); });
    $('sub').textContent = `${p[lang]} · ${p.tz} · ${METHODS[p.method].label[lang]}`;
    $('custom').hidden = $('city').value !== 'custom';
  }

  function fill() {
    lang = S.lang; translate(); fillCities(); fillMethods();
    $('city').value = S.city; $('lang').value = S.lang; $('digits').value = S.digits; $('clock24').checked = S.clock24;
    if (S.place) { $('c-ar').value = S.place.ar; $('c-en').value = S.place.en; $('c-lat').value = S.place.lat; $('c-lon').value = S.place.lon; $('c-tz').value = S.place.tz; }
    $('method').value = S.method; $('asr').value = S.asr; $('hijri').value = String(S.hijriOffset);
    numberGrid('offsets', PR, S.offsets, -30, 30);
    numberGrid('iqama', ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'], S.iqama, 0, 60);
    $('panel').value = S.panel; $('bg').value = S.bg; $('omanDays').checked = S.omanDays; $('night').checked = S.night; $('homeCard').checked = S.homeCard;
    checks('markets', ['gold', 'brent', 'btc', 'spx'], S.markets, t);
    checks('genres', GENRES, S.shows.genres);
    $('minRating').value = String(S.shows.minRating); $('api').value = S.api; $('channel').value = S.channel;
    renderTeams(); preview();
  }

  async function save() {
    const msg = $('msg'); msg.className = '';
    try {
      const body = JSON.stringify(collect());
      const r = await fetch(BASE + 'settings.json', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body });
      if (!r.ok) throw new Error(await r.text());
      S = sanitize(await r.json(), METHODS); msg.textContent = t('saved'); msg.className = 'ok';
      if (S.lang !== lang) fill();
    } catch (e) { msg.textContent = t('failed') + e.message; msg.className = 'err'; }
    setTimeout(() => { msg.className = ''; }, 4000);
  }

  async function start() {
    try { const r = await fetch(BASE + 'settings.json'); S = sanitize(await r.json(), METHODS); } catch (e) { S = sanitize(DEFAULTS, METHODS); }
    fill();
    ['city', 'method', 'asr', 'hijri', 'c-lat', 'c-lon', 'c-tz'].forEach(id => $(id).addEventListener('input', preview));
    $('lang').addEventListener('change', () => { S = collect(); fill(); });
    $('tgo').onclick = searchTeams; $('tq').addEventListener('keydown', e => { if (e.key === 'Enter') searchTeams(); });
    $('save').onclick = save;
  }
  start();
})();
