/* Luban — the screen. Runs in the Android apps (served from https://appassets.androidplatform.net, network through
 * the app's allowlisted proxy) and in any browser (direct requests; sources without CORS are skipped).
 * DOM is built with textContent only: no data from the network is ever parsed as HTML. */
(function () {
  'use strict';
  const { compute, KEYS, METHODS } = window.LubanPrayer;
  const { sanitize, resolvePlace } = window.LubanSettings;
  const DATA = window.LUBAN_DATA;                 // cities, verses, hadith, photos, teamsAr (inlined by build.py)
  const QS = new URLSearchParams(location.search);
  const IN_APP = QS.has('app');
  const OMR_PER_USD = 0.3845;                     // the rial's fixed peg to the dollar
  const TROY_OZ_G = 31.1034768;

  let S, P, T, CAL;                               // settings, place, strings, formatters
  const state = { weather: null, forecast: [], markets: {}, matches: {}, shows: [], page: 0, prayerDay: '', times: null, tomorrow: null };

  // ---------- small helpers
  const $ = id => document.getElementById(id);
  function h(tag, cls, text) { const e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; }
  function clear(el) { while (el.firstChild) el.removeChild(el.firstChild); return el; }
  const store = {
    get(k, d) { try { const v = localStorage.getItem('luban.' + k); return v ? JSON.parse(v) : d; } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem('luban.' + k, JSON.stringify(v)); } catch (e) { /* storage may be off */ } },
  };
  const AR_DIGITS = '٠١٢٣٤٥٦٧٨٩';
  const dg = s => S.digits === 'arab' ? String(s).replace(/[0-9]/g, d => AR_DIGITS[d]).replace(/\./g, '٫') : String(s);
  const pad = n => String(n).padStart(2, '0');

  function net(url) { return IN_APP ? '/net?u=' + encodeURIComponent(url) : url; }
  async function getJSON(url, ms) {
    const ctl = typeof AbortController === 'function' ? new AbortController() : null;   // older TV WebViews lack it
    const t = setTimeout(() => ctl && ctl.abort(), ms || 12000);
    try {
      const r = await fetch(url, ctl ? { signal: ctl.signal, cache: 'no-store' } : { cache: 'no-store' });
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return await r.json();
    } finally { clearTimeout(t); }
  }

  /** Wall-clock parts in the place's zone. */
  function local(d) {
    const p = CAL.parts.formatToParts(d || new Date()).reduce((o, x) => (o[x.type] = x.value, o), {});
    return { y: +p.year, m: +p.month, d: +p.day, h: (+p.hour) % 24, mi: +p.minute, s: +p.second, wd: p.weekday };
  }
  const minutesNow = () => { const l = local(); return l.h * 60 + l.mi + l.s / 60; };
  const dayKey = l => `${l.y}-${pad(l.m)}-${pad(l.d)}`;

  /** bare: no AM/PM, for the dial's rim where the position on the day already says it */
  function fmtTime(min, bare) {
    const r = Math.round(min) % 1440, hh = Math.floor(r / 60), mm = r % 60;
    if (S.clock24) return dg(pad(hh) + ':' + pad(mm));
    const h12 = hh % 12 === 0 ? 12 : hh % 12;
    return dg(h12 + ':' + pad(mm)) + (bare ? '' : ' ' + (hh < 12 ? T.am : T.pm));
  }
  /** "بعد ساعتين و47 دقيقة" / "in 2 h 47 min": the dial's centre has room for the full words */
  function leftLong(mins) {
    mins = Math.max(0, Math.ceil(mins));
    const hh = Math.floor(mins / 60), mm = mins % 60;
    if (S.lang !== 'ar') return `${T.in} ${hh ? hh + ' h ' : ''}${hh && !mm ? '' : mm + ' min'}`.trim();
    const hs = hh === 1 ? 'ساعة' : hh === 2 ? 'ساعتين' : hh <= 10 ? `${dg(hh)} ساعات` : `${dg(hh)} ساعة`;
    const ms = mm === 1 ? 'دقيقة' : mm === 2 ? 'دقيقتين' : mm <= 10 ? `${dg(mm)} دقائق` : `${dg(mm)} دقيقة`;
    return `${T.in} ${hh && mm ? hs + ' و' + ms : hh ? hs : ms}`;
  }
  function fmtLeft(mins) {
    mins = Math.max(0, Math.ceil(mins));
    const hh = Math.floor(mins / 60), mm = mins % 60;
    if (hh === 0) return `${dg(mm)} ${T.min(mm)}`;
    return S.lang === 'ar' ? `${dg(hh)} س ${dg(mm)} د` : `${hh}h ${pad(mm)}m`;
  }

  // ---------- settings
  async function loadSettings() {
    let raw = null;
    if (IN_APP) { try { raw = await getJSON('/settings.json', 4000); } catch (e) { /* the app always answers; fall through */ } }
    if (!raw) {
      const m = location.hash.match(/[#&]s=([A-Za-z0-9_-]+)/);
      if (m) { try { raw = JSON.parse(decodeURIComponent(escape(atob(m[1].replace(/-/g, '+').replace(/_/g, '/'))))); } catch (e) { raw = null; } }
    }
    if (!raw) raw = store.get('settings', null);
    return sanitize(raw, METHODS);
  }

  function applySettings() {
    P = resolvePlace(S, DATA.cities);
    T = window.LubanI18n.T[S.lang];
    document.documentElement.lang = S.lang;
    document.documentElement.dir = S.lang === 'ar' ? 'rtl' : 'ltr';
    document.body.classList.toggle('ltr', S.lang !== 'ar');
    document.body.classList.toggle('photos', S.bg === 'photos');
    const loc = (S.lang === 'ar' ? 'ar-OM' : 'en-GB') + '-u-nu-' + S.digits;
    CAL = {
      parts: new Intl.DateTimeFormat('en-US', { timeZone: P.tz, hour12: false, year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: 'numeric', second: 'numeric', weekday: 'short' }),
      greg: new Intl.DateTimeFormat(loc + '-ca-gregory', { timeZone: P.tz, weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }),
      hijri: new Intl.DateTimeFormat(loc + '-ca-islamic-umalqura', { timeZone: P.tz, day: 'numeric', month: 'long', year: 'numeric' }),
      hijriNum: new Intl.DateTimeFormat('en-US-u-ca-islamic-umalqura-nu-latn', { timeZone: P.tz, day: 'numeric', month: 'numeric', year: 'numeric' }),
      wd: new Intl.DateTimeFormat(loc, { timeZone: P.tz, weekday: 'short' }),
      // an old WebView may silently fall back to the Gregorian calendar: then use the arithmetic one
      umalqura: (() => { try { return new Intl.DateTimeFormat('en-u-ca-islamic-umalqura').resolvedOptions().calendar === 'islamic-umalqura'; } catch (e) { return false; } })(),
      day: new Intl.DateTimeFormat(loc, { timeZone: P.tz, weekday: 'short', day: 'numeric', month: 'short' }),
    };
    $('city').textContent = S.lang === 'ar' ? P.ar : P.en;
  }

  // ---------- clock and dates
  function tickClock() {
    const l = local();
    const hh = S.clock24 ? l.h : (l.h % 12 === 0 ? 12 : l.h % 12);
    $('hh').textContent = dg(S.clock24 ? pad(hh) : hh);
    $('mm').textContent = dg(pad(l.mi));
    $('ampm').textContent = S.clock24 ? '' : (l.h < 12 ? T.am : T.pm);
  }

  /** Umm al-Qura from the table in content/umalqura.json (month starts, 1440–1480 AH), for WebViews whose ICU lacks it. */
  function tableHijri(epochDay) {
    const t = DATA.umalqura;
    let lo = 0, hi = t.length - 1;
    if (!t.length || epochDay < t[0][2]) return null;
    while (lo < hi) { const mid = (lo + hi + 1) >> 1; if (t[mid][2] <= epochDay) lo = mid; else hi = mid - 1; }
    return { y: t[lo][0], m: t[lo][1], d: epochDay - t[lo][2] + 1 };
  }
  const HIJRI_MONTHS = {
    ar: ['محرم', 'صفر', 'ربيع الأول', 'ربيع الآخر', 'جمادى الأولى', 'جمادى الآخرة', 'رجب', 'شعبان', 'رمضان', 'شوال', 'ذو القعدة', 'ذو الحجة'],
    en: ['Muharram', 'Safar', "Rabi' I", "Rabi' II", 'Jumada I', 'Jumada II', 'Rajab', "Sha'ban", 'Ramadan', 'Shawwal', "Dhu al-Qi'dah", 'Dhu al-Hijjah'],
  };
  function hijriToday() {
    const d = new Date(Date.now() + S.hijriOffset * 86400000);
    if (CAL.umalqura) {
      const p = CAL.hijriNum.formatToParts(d).reduce((o, x) => (o[x.type] = x.value, o), {});
      return { y: parseInt(p.year, 10), m: +p.month, d: +p.day, text: CAL.hijri.format(d) };
    }
    const l = local(d), t = tableHijri(Date.UTC(l.y, l.m - 1, l.d) / 864e5) || { y: 0, m: 1, d: 1 };
    return { y: t.y, m: t.m, d: t.d, text: `${dg(t.d)} ${HIJRI_MONTHS[S.lang][t.m - 1]} ${dg(t.y)}${S.lang === 'ar' ? ' هـ' : ' AH'}` };
  }

  function tickDates() {
    $('greg').textContent = CAL.greg.format(new Date());
    $('hijri').textContent = hijriToday().text;
  }

  // ---------- prayers
  function computeDay(offsetDays) {
    const l = local(new Date(Date.now() + offsetDays * 86400000));
    return compute({ y: l.y, m: l.m, d: l.d }, P, { method: P.method, asr: S.asr, offsets: S.offsets, ramadan: hijriToday().m === 9 });
  }

  function ensureTimes() {
    const key = dayKey(local()) + P.id + P.method;
    if (state.prayerDay !== key) {
      state.prayerDay = key; state.times = computeDay(0); state.tomorrow = computeDay(1);
      // far north in summer Maghrib or Isha can fall after midnight: keep them after Dhuhr for every comparison
      state.order = {};
      for (const k of KEYS) state.order[k] = state.times[k] + ((k === 'maghrib' || k === 'isha') && state.times[k] < state.times.dhuhr ? 1440 : 0);
    }
  }

  /** The next prayer (sunrise included), and the one whose iqama is still ahead. */
  function nextPrayer() {
    ensureTimes();
    const now = minutesNow(), t = state.order, friday = local().wd === 'Fri';
    for (const k of KEYS) if (t[k] > now) return { key: k, at: t[k], left: t[k] - now, friday };
    return { key: 'fajr', at: state.tomorrow.fajr, left: state.tomorrow.fajr + 1440 - now, friday: false, tomorrow: true };
  }
  function iqamaPending() {
    const now = minutesNow(), t = state.order, friday = local().wd === 'Fri';
    for (const k of ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha']) {
      const gap = (k === 'dhuhr' && friday) ? 0 : S.iqama[k];
      if (gap > 0 && now >= t[k] && now < t[k] + gap) return { key: k, left: t[k] + gap - now };
    }
    return null;
  }
  const prayerName = (k, friday) => (k === 'dhuhr' && friday) ? T.jumuah : T[k];

  // ---------- the dial: the navigators' star compass as a 24-hour clock
  const SVGNS = 'http://www.w3.org/2000/svg', R = 300;
  function svg(tag, attrs, parent) {
    const e = document.createElementNS(SVGNS, tag);
    for (const k in attrs) e.setAttribute(k, attrs[k]);
    if (parent) parent.appendChild(e);
    return e;
  }
  /** Noon at the top, midnight at the bottom. Time runs the way the language reads: morning on the right in Arabic. */
  function at(min, r) {
    const a = (min - 720) / 1440 * 2 * Math.PI, side = S.lang === 'ar' ? -1 : 1;
    return [side * r * Math.sin(a), -r * Math.cos(a)];
  }
  function arc(t0, t1, r) {
    const p = at(t0, r), q = at(t1, r), sweep = S.lang === 'ar' ? 0 : 1;
    return `M${p[0].toFixed(1)} ${p[1].toFixed(1)} A${r} ${r} 0 ${t1 - t0 > 720 ? 1 : 0} ${sweep} ${q[0].toFixed(1)} ${q[1].toFixed(1)}`;
  }
  let dialDyn = null;
  function drawDialFrame() {
    const root = clear($('dial-svg'));
    const defs = svg('defs', {}, root), g = svg('radialGradient', { id: 'glow' }, defs);
    svg('stop', { offset: '0', 'stop-color': '#FFB547', 'stop-opacity': '.95' }, g);
    svg('stop', { offset: '.35', 'stop-color': '#E2762A', 'stop-opacity': '.45' }, g);
    svg('stop', { offset: '1', 'stop-color': '#E2762A', 'stop-opacity': '0' }, g);
    svg('circle', { r: 372, fill: '#06192A', 'fill-opacity': '.55' }, root);
    svg('circle', { r: R, fill: 'none', stroke: '#E2A94F', 'stroke-opacity': '.5', 'stroke-width': 1.5 }, root);
    svg('circle', { r: R - 26, fill: 'none', stroke: '#E2A94F', 'stroke-opacity': '.25' }, root);
    for (let q = 0; q < 96; q++) {   // quarter hours, the hours longer
      const hour = q % 4 === 0, p = at(q * 15, R), i = at(q * 15, R - (hour ? 22 : 9));
      svg('line', { x1: p[0], y1: p[1], x2: i[0], y2: i[1], stroke: '#E2A94F', 'stroke-opacity': hour ? '.8' : '.4', 'stroke-width': hour ? 2 : 1 }, root);
    }
    for (let k = 0; k < 8; k++) {   // the eight principal rhumbs, kept clear of the words in the middle
      const a = k / 8 * 2 * Math.PI, s0 = Math.sin(a), c0 = Math.cos(a);
      svg('line', { x1: 200 * s0, y1: -200 * c0, x2: 262 * s0, y2: -262 * c0, stroke: '#E2A94F', 'stroke-opacity': '.25' }, root);
    }
    const word = (txt, y, op) => { const t = svg('text', { x: 0, y, 'text-anchor': 'middle', 'font-family': 'Messiri', 'font-size': 24, fill: '#E2A94F', 'fill-opacity': op }, root); t.textContent = txt; };
    word(T.noon, -R + 62, '.8');
    word(T.midnight, R - 44, '.6');
    dialDyn = svg('g', {}, root);
  }

  /** The smoke: fine threads that curl and fade as they rise, drawn once per minute; CSS breathes the two layers. */
  function drawSmoke(id, seed) {
    const cv = $(id), c = cv.getContext && cv.getContext('2d');
    if (!c) return;
    let s = seed;
    const rnd = () => (s = (s * 16807) % 2147483647) / 2147483647;
    c.clearRect(0, 0, cv.width, cv.height);
    const x0 = cv.width / 2, y0 = cv.height - 12, lean = S.lang === 'ar' ? -1 : 1;
    const layers = [[18, 34, .06, 4], [6, 10, .08, 7], [1.2, 2, .3, 10]];
    for (const [blur, w, al, n] of layers) {
      c.save();
      c.filter = `blur(${blur}px)`;
      c.lineCap = 'round'; c.lineWidth = w;
      const g = c.createLinearGradient(0, y0, 0, 0);
      g.addColorStop(0, 'rgba(246,236,218,0)'); g.addColorStop(.06, `rgba(246,236,218,${al})`); g.addColorStop(1, 'rgba(246,236,218,0)');
      c.strokeStyle = g;
      for (let k = 0; k < n; k++) {
        let y = y0, dx = 0;
        const t = rnd() * 6.28, sp = (rnd() - .5) * 1.4;
        c.beginPath(); c.moveTo(x0, y);
        for (let j = 0; j < 86; j++) {
          const q = j / 86;
          dx += Math.sin(j * .11 + t) * (.6 + q * 3) + Math.sin(j * .27 + t * 2) * (.3 + q * .8) + sp * q * 2.4;
          y -= 4.6; c.lineTo(x0 + dx + lean * q * q * 70, y);
        }
        c.stroke();
      }
      c.restore();
    }
  }

  let smokeAt = -1;
  function renderPrayers() {
    ensureTimes();
    if (!dialDyn) drawDialFrame();
    const box = clear($('prayers')), dyn = clear(dialDyn), nx = nextPrayer(), iq = iqamaPending(), friday = local().wd === 'Fri';
    const t = state.order, now = minutesNow();
    // daylight, and the part of today already lived (from Fajr), in resin
    svg('path', { d: arc(t.sunrise, t.maghrib, R - 13), fill: 'none', stroke: '#E2A94F', 'stroke-width': 26, 'stroke-opacity': '.2' }, dyn);
    if (now > t.fajr) svg('path', { d: arc(t.fajr, Math.min(now, t.fajr + 1439), R - 13), fill: 'none', stroke: '#E2A94F', 'stroke-width': 26, 'stroke-opacity': '.55' }, dyn);
    const placed = [];
    for (const k of KEYS) {
      // after Isha the Fajr mark points at tomorrow's Fajr
      const tm = nx.tomorrow && k === 'fajr' ? state.tomorrow.fajr : state.times[k];
      const isNext = iq ? iq.key === k : nx.key === k;
      const o = at(t[k], R + 6), i = at(t[k], R - 40);
      svg('line', { x1: o[0], y1: o[1], x2: i[0], y2: i[1], stroke: isNext ? '#8FE0D6' : '#F1EBDD', 'stroke-width': isNext ? 5 : 3 }, dyn);
      // labels outside the rim; one too close to the last steps further out
      let p = at(t[k], 392);
      if (placed.some(q => Math.abs(q[0] - p[0]) < 150 && Math.abs(q[1] - p[1]) < 80)) p = at(t[k], 470);
      placed.push(p);
      const el = h('div', 'pr ' + k + (isNext ? ' next' : ''));
      el.style.left = ((p[0] + 400) / 8) + '%'; el.style.top = ((p[1] + 400) / 8) + '%';
      el.append(h('div', 't', fmtTime(tm, true)), h('div', 'n', prayerName(k, friday)));
      box.append(el);
    }
    // now: a lit grain of frankincense on the rim
    const e = at(now, R - 13), pin = at(now, 214);
    svg('line', { x1: pin[0], y1: pin[1], x2: e[0], y2: e[1], stroke: '#F6D9A0', 'stroke-width': 2 }, dyn);
    svg('circle', { cx: e[0], cy: e[1], r: 70, fill: 'url(#glow)' }, dyn);
    const [x, y] = e;
    svg('path', { fill: '#F7E7C4', d: `M${x - 15} ${y + 4}C${x - 16} ${y - 12} ${x - 4} ${y - 17} ${x + 6} ${y - 14}C${x + 17} ${y - 10} ${x + 18} ${y + 6} ${x + 8} ${y + 13}C${x - 2} ${y + 18} ${x - 14} ${y + 15} ${x - 15} ${y + 4}Z` }, dyn);
    svg('path', { d: `M${x - 6} ${y - 6}q6 -5 12 -1`, stroke: '#fff', 'stroke-opacity': '.8', 'stroke-width': 3, fill: 'none', 'stroke-linecap': 'round' }, dyn);
    // the smoke rises from the grain
    for (const id of ['smoke-a', 'smoke-b']) {
      const cv = $(id);
      cv.style.left = ((x + 400) / 8 - 30 / 76 * 50) + '%';
      cv.style.top = ((y + 400) / 8 - 42 / 76 * 100 + 1) + '%';
    }
    const minute = Math.floor(now);
    if (minute !== smokeAt) { smokeAt = minute; drawSmoke('smoke-a', 11); drawSmoke('smoke-b', 29); }
    // the centre: what comes next
    if (iq) {
      $('next-name').textContent = prayerName(iq.key, friday);
      $('next-time').textContent = T.iqama;
      $('next-left').textContent = leftLong(iq.left);
    } else {
      $('next-name').textContent = prayerName(nx.key, nx.friday);
      $('next-time').textContent = fmtTime(nx.at);
      $('next-left').textContent = leftLong(nx.left);
    }
  }

  /** The chart's rhumb lines, running out from the dial's centre across the whole screen. */
  function drawRhumbs() {
    const root = clear($('rhumbs')), r = $('dial').getBoundingClientRect();
    const cx = r.left + r.width / 2, cy = r.top + r.height / 2, W = window.innerWidth, H = window.innerHeight, L = W + H;
    root.setAttribute('viewBox', `0 0 ${W} ${H}`);
    for (let i = 0; i < 32; i++) {
      const a = i / 32 * 2 * Math.PI;
      svg('line', { x1: cx, y1: cy, x2: cx + L * Math.sin(a), y2: cy - L * Math.cos(a), stroke: '#E2A94F', 'stroke-opacity': i % 4 ? '.08' : '.17', 'stroke-width': i % 8 ? 1 : 1.5 }, root);
    }
    // a second, fainter rose, as old charts had several
    const x2 = S.lang === 'ar' ? W * .8 : W * .2, y2 = H * 1.15;
    for (let i = 0; i < 16; i++) {
      const a = i / 16 * 2 * Math.PI;
      svg('line', { x1: x2, y1: y2, x2: x2 + L * Math.sin(a), y2: y2 - L * Math.cos(a), stroke: '#E2A94F', 'stroke-opacity': '.04' }, root);
    }
  }

  // ---------- chips: National Day, Khareef, Ramadan and Eid, Friday
  function daysBetween(a, b) { return Math.round((Date.UTC(b.y, b.m - 1, b.d) - Date.UTC(a.y, a.m - 1, a.d)) / 86400000); }
  function renderChips() {
    const box = clear($('chips')), l = local(), hj = hijriToday();
    const chip = (text, cls) => {
      const c = h('span', 'chip ' + (cls || ''));
      c.append(h('span', '', text));
      box.append(c);
    };
    if (S.omanDays) {
      // Royal Decree 15/2025: National Day on 20 and 21 November
      const nd = { y: l.y, m: 11, d: 20 };
      const until = daysBetween(l, nd);
      if (l.m === 11 && (l.d === 20 || l.d === 21)) chip(T.nationalDay, 'nat');
      else if (until > 0 && until <= 45) chip(T.nationalDayIn(`${dg(until)} ${T.day(until)}`), 'nat');
      // Dhofar's monsoon season, 21 June to 21 September
      const md = l.m * 100 + l.d;
      if (P.gov === 'dhofar' && md >= 621 && md <= 921) chip(T.khareef, 'luban');
    }
    if (hj.m === 9) chip(T.ramadanDay(dg(hj.d)), 'luban');
    else if (hj.m === 8 && hj.d >= 15) { const n = 30 - hj.d; if (n > 0) chip(T.ramadanIn(`${dg(n)} ${T.day(n)}`), 'luban'); }
    if (hj.m === 10 && hj.d <= 3) chip(T.eidFitr, 'luban');
    if (hj.m === 12 && hj.d === 9) chip(T.arafah, 'luban');
    if (hj.m === 12 && hj.d >= 10 && hj.d <= 13) chip(T.eidAdha, 'luban');
    if (l.wd === 'Fri' && box.childElementCount === 0) chip(T.friday);
  }

  // ---------- weather (Open-Meteo, no key, CORS-friendly)
  const ICONS = {
    sun: 'M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10zM12 1v3M12 20v3M1 12h3M20 12h3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1',
    moon: 'M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z',
    cloud: 'M7 18h10a4 4 0 0 0 .5-8 6 6 0 0 0-11.5 1.5A3.5 3.5 0 0 0 7 18z',
    rain: 'M7 15h10a4 4 0 0 0 .5-8 6 6 0 0 0-11.5 1.5A3.5 3.5 0 0 0 7 15zM8 18l-1 3M12 18l-1 3M16 18l-1 3',
    storm: 'M7 14h10a4 4 0 0 0 .5-8 6 6 0 0 0-11.5 1.5A3.5 3.5 0 0 0 7 14zM12 15l-2 4h3l-2 4',
    fog: 'M4 9h16M3 13h18M5 17h14',
    snow: 'M7 14h10a4 4 0 0 0 .5-8 6 6 0 0 0-11.5 1.5A3.5 3.5 0 0 0 7 14zM9 18h.01M12 20h.01M15 18h.01',
    dust: 'M3 8h11a3 3 0 1 0-3-3M3 12h16a3 3 0 1 1-3 3M3 16h8',
  };
  function icon(code, isDay, dusty) {
    let k = dusty ? 'dust' : code === 0 || code === 1 ? (isDay ? 'sun' : 'moon') : code <= 3 ? 'cloud' : code <= 48 ? 'fog'
      : code >= 95 ? 'storm' : (code >= 71 && code <= 77) ? 'snow' : 'rain';
    const ns = 'http://www.w3.org/2000/svg', svg = document.createElementNS(ns, 'svg'), path = document.createElementNS(ns, 'path');
    svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('fill', 'none'); svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', '1.6'); svg.setAttribute('stroke-linecap', 'round'); svg.setAttribute('stroke-linejoin', 'round');
    path.setAttribute('d', ICONS[k]); svg.append(path); return svg;
  }
  async function loadWeather() {
    const q = `latitude=${P.lat}&longitude=${P.lon}&timezone=${encodeURIComponent(P.tz)}`;
    try {
      const j = await getJSON(`https://api.open-meteo.com/v1/forecast?${q}&current=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=6`);
      state.weather = { t: Math.round(j.current.temperature_2m), code: j.current.weather_code, day: j.current.is_day === 1, dust: false };
      state.forecast = j.daily.time.map((d, i) => ({ d, code: j.daily.weather_code[i], hi: Math.round(j.daily.temperature_2m_max[i]), lo: Math.round(j.daily.temperature_2m_min[i]) }));
      try {   // dust matters in the Gulf: show it when PM10 is high
        const a = await getJSON(`https://air-quality-api.open-meteo.com/v1/air-quality?${q}&current=pm10,dust`, 8000);
        state.weather.dust = (a.current.dust || 0) > 150 || (a.current.pm10 || 0) > 250;
      } catch (e) { /* optional */ }
      store.set('weather:' + P.id, { w: state.weather, f: state.forecast, at: Date.now() });
    } catch (e) {
      const c = store.get('weather:' + P.id, null);
      if (c && Date.now() - c.at < 12 * 3600e3) { state.weather = c.w; state.forecast = c.f; }
    }
    renderWeather();
    if (S.panel === 'forecast') renderCard();
  }
  function renderWeather() {
    const box = clear($('wx')), w = state.weather;
    if (!w) return;
    box.append(icon(w.code, w.day, w.dust), h('span', 't', dg(w.t) + '°'), h('span', '', w.dust ? T.dust : (T.weather[w.code] || '')));
    renderCredit();
  }

  // ---------- markets: Yahoo through the app's proxy or the server; CoinGecko directly
  const MARKETS = {
    gold: { yahoo: 'GC=F', label: () => T.gold, value: p => p / TROY_OZ_G * OMR_PER_USD, dp: 3 },
    brent: { yahoo: 'BZ=F', label: () => T.brent, value: p => p, dp: 2 },
    spx: { yahoo: '^GSPC', label: () => T.spx, value: p => p, dp: 0 },
    btc: { cg: 'bitcoin', label: () => T.btc, value: p => p, dp: 0 },
  };
  async function loadMarkets() {
    if (!S.markets.length) { renderMarkets(); return; }
    let got = {};
    if (S.api) { try { got = await getJSON(`${S.api}/v1/markets?ids=${S.markets.join(',')}`, 10000); } catch (e) { got = {}; } }
    await Promise.all(S.markets.filter(id => !got[id]).map(async id => {
      const m = MARKETS[id];
      try {
        if (m.cg) {
          const j = await getJSON(`https://api.coingecko.com/api/v3/simple/price?ids=${m.cg}&vs_currencies=usd&include_24hr_change=true`);
          const c = j[m.cg]; got[id] = { price: c.usd, change: c.usd_24h_change };
        } else if (IN_APP) {
          const j = await getJSON(net(`https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(m.yahoo)}?range=5d&interval=1d`));
          const meta = j.chart.result[0].meta, prev = meta.chartPreviousClose || meta.previousClose;
          got[id] = { price: meta.regularMarketPrice, change: prev ? (meta.regularMarketPrice / prev - 1) * 100 : null };
        }
      } catch (e) { /* keep the last value */ }
    }));
    for (const k of Object.keys(got)) if (got[k] && isFinite(got[k].price)) state.markets[k] = { price: got[k].price, change: got[k].change, at: Date.now() };
    store.set('markets', state.markets);
    renderMarkets();
  }
  function renderMarkets() {
    const box = clear($('markets'));
    for (const id of S.markets) {
      const q = state.markets[id], m = MARKETS[id];
      if (!q || !m) continue;
      const v = m.value(q.price);
      const el = h('div', 'mk');
      el.append(h('span', 'n', m.label()), h('span', 'v', dg(v.toLocaleString('en-US', { minimumFractionDigits: m.dp, maximumFractionDigits: m.dp }))));
      if (typeof q.change === 'number') {
        const up = q.change >= 0;
        el.append(h('span', 'd ' + (up ? 'up' : 'down'), (up ? '▲ ' : '▼ ') + dg(Math.abs(q.change).toFixed(2)) + '%'));
      }
      // older than two hours (the source or the network is down): say when it is from, rather than pass it off as now
      if (!q.at || Date.now() - q.at > 2 * 3600e3) {
        el.classList.add('stale');
        if (q.at) { const l = local(new Date(q.at)); el.append(h('span', 'd', `${T.asOf} ${fmtTime(l.h * 60 + l.mi)}`)); }
      }
      box.append(el);
    }
    renderCredit();
    fitCard();   // the footer grew or shrank: the card's room changed
  }

  // ---------- matches (football-data.org, through the data server: the free token stays on the server)
  const norm = s => String(s || '').toLowerCase().replace(/\b(al|fc|sc|club|saudi club)\b/g, '').replace(/[^a-z0-9]/g, '');
  const AR_NAMES = Object.keys(DATA.teamsAr).reduce((o, k) => { if (k[0] !== '_') o[norm(k)] = DATA.teamsAr[k]; return o; }, {});
  const teamAr = (id, en) => { const t = S.teams.find(x => x.id === String(id)); return (t && t.ar) || AR_NAMES[norm(en)] || en || ''; };
  const teamName = (id, en) => S.lang === 'ar' ? teamAr(id, en) : (en || '');
  function eventTime(ev) {
    const ts = ev.strTimestamp ? ev.strTimestamp.replace(/\+00:00$/, '') : (ev.dateEvent && ev.strTime ? `${ev.dateEvent}T${ev.strTime}` : null);
    return ts ? new Date(/Z$/.test(ts) ? ts : ts + 'Z') : null;
  }
  async function loadMatches() {
    if (!S.teams.length) { state.matches = {}; renderMatch(); return; }
    let data = null;
    if (S.api) { try { data = await getJSON(`${S.api}/v1/matches?teams=${S.teams.map(t => t.id).join(',')}`, 12000); } catch (e) { data = null; } }
    if (data && Object.keys(data).length) { state.matches = data; store.set('matches', data); }
    renderMatch();
    if (S.panel === 'matches') renderCard();
  }
  /** Events of the followed teams: results from the last 20 hours, then upcoming ones, soonest first. */
  function matchList() {
    const seen = new Set(), now = Date.now(), out = [];
    for (const t of S.teams) {
      const d = state.matches[t.id]; if (!d) continue;
      for (const ev of [...(d.last || []), ...(d.next || [])]) {
        if (!ev || seen.has(ev.idEvent)) continue;
        const at = eventTime(ev); if (!at) continue;
        const done = ev.intHomeScore != null && ev.intHomeScore !== '' && /FT|Finished|AET|PEN/i.test(ev.strStatus || 'FT');
        const live = !done && ev.intHomeScore != null && ev.intHomeScore !== '' && now >= at.getTime();
        if (done && now - at.getTime() > 20 * 3600e3) continue;
        if (!done && !live && at.getTime() < now - 3 * 3600e3) continue;
        seen.add(ev.idEvent);
        out.push({ ev, at, done, live });
      }
    }
    return out.sort((a, b) => (b.live - a.live) || (b.done - a.done) || a.at - b.at);
  }
  function whenText(at) {
    const l = local(), e = local(at), diff = daysBetween(l, e);
    const tm = fmtTime(e.h * 60 + e.mi);
    if (diff === 0) return `${T.today} ${tm}`;
    if (diff === 1) return `${T.tomorrow} ${tm}`;
    return `${CAL.day.format(at)} ${tm}`;
  }
  function renderMatch() {
    const box = clear($('match')), list = matchList();
    if (!list.length) return;
    const { ev, at, done, live } = list[0];
    box.append(h('span', '', teamName(ev.idHomeTeam, ev.strHomeTeam)));
    if (done || live) box.append(h('span', 'vs', dg(`${ev.intHomeScore} - ${ev.intAwayScore}`)));
    else box.append(h('span', 'vs', '×'));
    box.append(h('span', '', teamName(ev.idAwayTeam, ev.strAwayTeam)));
    box.append(h('span', live ? 'live' : '', live ? T.live : done ? T.ft : whenText(at)));
    renderCredit();
    fitCard();
  }

  // ---------- new episodes (TVMaze, CORS-friendly)
  async function loadShows() {
    const f = S.shows, days = [];
    for (let i = 0; i < 7; i++) { const l = local(new Date(Date.now() + i * 86400000)); days.push(dayKey(l)); }
    let list = null;
    if (S.api) {
      try { list = await getJSON(`${S.api}/v1/shows?genres=${f.genres.join(',')}&langs=${f.langs.join(',')}&min=${f.minRating}`, 15000); } catch (e) { list = null; }
    }
    if (!list) {
      const seen = new Set(); list = [];
      const all = await Promise.all(days.map(d => getJSON(`https://api.tvmaze.com/schedule/web?date=${d}`).catch(() => [])));
      for (const e of [].concat.apply([], all)) {
        const s = e._embedded && e._embedded.show; if (!s || seen.has(s.id)) continue;
        if (['News', 'Talk Show', 'Sports', 'Game Show'].includes(s.type)) continue;
        const rating = (s.rating && s.rating.average) || 0;
        if (rating < f.minRating) continue;
        if (f.genres.length && !(s.genres || []).some(g => f.genres.includes(g))) continue;
        if (f.langs.length && !f.langs.includes(s.language)) continue;
        seen.add(s.id);
        list.push({ name: s.name, season: e.season, number: e.number, date: e.airdate, rating });
      }
      list.sort((a, b) => b.rating - a.rating).splice(30);
    }
    if (list.length) { state.shows = list; store.set('shows', list); }
    if (S.panel === 'shows') renderCard();
  }

  // ---------- the card beside the clock
  function quoteOfDay() {
    const l = local(), n = Math.floor(Date.UTC(l.y, l.m - 1, l.d) / 86400000);
    if (n % 2 === 0 && DATA.verses.length) {
      const v = DATA.verses[Math.floor(n / 2) % DATA.verses.length];
      return { title: T.verse, quote: v.text, cls: 'quote', src: `${v.surah} · ${dg(v.ayah)}` };
    }
    const x = DATA.hadith[Math.floor(n / 2) % DATA.hadith.length];
    return { title: T.hadith, quote: x.text.replace(/صلى الله عليه و ?سلم/g, 'ﷺ'), cls: 'quote hadith', src: x.source };
  }
  function rowsList(rows, nowIndex) {
    const ul = h('ul', 'rows');
    rows.forEach((r, i) => { const li = h('li', i === nowIndex ? 'now' : ''); li.append(h('span', 'a', r[0]), h('span', 'b', r[1])); ul.append(li); });
    return ul;
  }
  /** Long texts step the font down until the card holds them (never below 70%). */
  function fitCard() {
    const card = $('card'), q = card.querySelector('.quote');
    if (!q) return;
    q.style.fontSize = '';
    const base = parseFloat(getComputedStyle(q).fontSize);
    for (let f = 1; f >= 0.7 && card.scrollHeight > card.clientHeight + 1; f -= 0.05) q.style.fontSize = (base * f) + 'px';
  }

  function renderCard(animate) {
    const card = $('card'), body = $('card-body'), title = $('card-title');
    if (S.panel === 'none') { card.hidden = true; return; }
    card.hidden = false; clear(body);
    if (animate) { body.classList.remove('fade'); void body.offsetWidth; body.classList.add('fade'); }
    if (S.panel === 'verse') {
      const q = quoteOfDay(); title.textContent = q.title;
      body.append(h('div', q.cls, S.digits === 'arab' ? q.quote.replace(/\d+/g, dg) : q.quote), h('div', 'src', q.src));
      fitCard();
    } else if (S.panel === 'prayers') {
      ensureTimes(); title.textContent = T.prayersToday;
      const nx = nextPrayer(), friday = local().wd === 'Fri';
      const rows = ['fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha'].map(k => {
        const iq = k === 'sunrise' || (k === 'dhuhr' && friday) ? '' : `  ·  ${T.iqama} ${fmtTime(state.times[k] + S.iqama[k])}`;
        return [prayerName(k, friday), fmtTime(state.times[k]) + iq];
      });
      body.append(rowsList(rows, nx.tomorrow ? -1 : KEYS.indexOf(nx.key)));
    } else if (S.panel === 'forecast') {
      title.textContent = T.forecast;
      const rows = state.forecast.slice(1, 6).map(f => [CAL.day.format(new Date(f.d + 'T12:00:00Z')), `${T.weather[f.code] || ''}  ${dg(f.hi)}° / ${dg(f.lo)}°`]);
      body.append(rows.length ? rowsList(rows, -1) : h('div', 'empty', T.noData));
    } else if (S.panel === 'matches') {
      title.textContent = T.matches;
      // one line of text: a Latin name next to the score would pull the score into its own direction, so a mark of the
      // reading direction holds each part apart (home first, its score beside it, like the strip under the clock)
      const m = S.lang === 'ar' ? '‏' : '‎';
      const list = matchList().slice(0, 5).map(({ ev, at, done, live }) => [
        [teamName(ev.idHomeTeam, ev.strHomeTeam), done || live ? dg(ev.intHomeScore + ' - ' + ev.intAwayScore) : '×',
          teamName(ev.idAwayTeam, ev.strAwayTeam)].join(m + ' ' + m),
        live ? T.live : done ? T.ft : whenText(at)]);
      body.append(list.length ? rowsList(list, -1) : h('div', 'empty', T.noMatches), h('div', 'src', T.matchesSrc));
    } else if (S.panel === 'shows') {
      title.textContent = T.shows;
      const per = 5, pages = Math.max(1, Math.ceil(state.shows.length / per)), p = state.page % pages;
      const rows = state.shows.slice(p * per, p * per + per).map(s => [s.name, `S${dg(s.season || 1)}·E${dg(s.number || 1)}  ★${dg((s.rating || 0).toFixed(1))}`]);
      body.append(rows.length ? rowsList(rows, -1) : h('div', 'empty', T.noShows));
    }
  }

  /** Every source on screen, named (their terms ask for it): only the ones whose data is showing now. */
  function renderCredit() {
    const box = clear($('credit')), parts = [];
    if (state.weather) parts.push([T.srcWeather, 'Open-Meteo']);
    const shown = S.markets.filter(id => state.markets[id]);
    if (shown.length) parts.push([T.srcMarkets, [shown.some(id => MARKETS[id].yahoo) ? 'Yahoo Finance' : '', shown.indexOf('btc') >= 0 ? 'CoinGecko' : ''].filter(Boolean).join(', ')]);
    if (S.panel === 'shows' && state.shows.length) parts.push([T.srcShows, 'TVmaze']);
    if (S.teams.length && $('match').firstChild) parts.push([T.srcMatches, 'football-data.org']);
    if (state.photoCredit && S.bg === 'photos') parts.push([T.photo, state.photoCredit]);
    // each name in its own <bdi>, so Latin names keep their place inside Arabic text
    parts.forEach(([label, name], i) => { const sp = h('span', 'cr', label + ': '); sp.append(h('bdi', '', name)); box.append(sp); });
  }

  // ---------- photos: four a day out of the set, a new one every 20 minutes
  let photoSlot = -1, layer = 0;
  function photoUrl(f) { return IN_APP ? '/photos/' + f : (window.LUBAN_PHOTO_BASE || 'photos/') + f; }
  function applyPhoto(force) {
    const list = DATA.photos; if (!list.length || S.bg !== 'photos') return;
    const l = local(), day = Math.floor(Date.UTC(l.y, l.m - 1, l.d) / 86400000);
    const slot = Math.floor((l.h * 60 + l.mi) / 20);
    if (!force && slot === photoSlot) return;
    photoSlot = slot;
    const idx = (day * 4 + (slot % 4)) % list.length, ph = list[idx];
    const img = new Image();
    img.onload = () => {
      const a = $(layer ? 'ph-a' : 'ph-b'), b = $(layer ? 'ph-b' : 'ph-a');
      a.style.backgroundImage = `url("${img.src}")`; a.classList.add('on'); b.classList.remove('on'); layer ^= 1;
      state.photoCredit = `${S.lang === 'ar' ? ph.place_ar : ph.place_en}, ${ph.by}${ph.licence ? ', ' + ph.licence : ''}`;
      renderCredit();
    };
    img.src = photoUrl(ph.f);
  }

  // ---------- night dimming, burn-in shift
  function applyNight() {
    ensureTimes();
    const now = minutesNow(), t = state.order;
    const night = S.night && (now >= t.isha + 120 || now < t.fajr - 30);
    document.body.classList.toggle('night', night);
  }
  let shiftStep = 0;
  function shift() {
    const steps = [[0, 0], [6, 4], [-5, 6], [4, -5], [-6, -3]], s = steps[++shiftStep % steps.length];
    document.documentElement.style.setProperty('--shift-x', s[0] + 'px');
    document.documentElement.style.setProperty('--shift-y', s[1] + 'px');
  }
  // ---------- start
  async function start() {
    S = await loadSettings();
    applySettings();
    state.markets = store.get('markets', {});
    state.matches = store.get('matches', {});
    state.shows = store.get('shows', []);
    const wc = store.get('weather:' + P.id, null);
    if (wc) { state.weather = wc.w; state.forecast = wc.f; }
    tickClock(); tickDates(); renderPrayers(); renderChips(); renderWeather(); renderMarkets(); renderMatch(); renderCard(); applyPhoto(true); applyNight();
    drawRhumbs();
    window.addEventListener('resize', () => { drawRhumbs(); renderPrayers(); });
    document.body.classList.add('ready');

    setInterval(tickClock, 1000);
    setInterval(() => { tickDates(); renderPrayers(); renderChips(); applyNight(); }, 20000);
    setInterval(() => { state.page++; if (['shows', 'prayers', 'matches'].includes(S.panel)) renderCard(S.panel === 'shows'); }, 20000);
    setInterval(applyPhoto, 60000);
    setInterval(shift, 180000);
    loadWeather(); setInterval(loadWeather, 30 * 60000);
    loadMarkets(); setInterval(loadMarkets, 15 * 60000);
    loadMatches(); setInterval(loadMatches, 10 * 60000);
    if (S.panel === 'shows') { loadShows(); setInterval(loadShows, 6 * 3600e3); }
    window.addEventListener('hashchange', () => location.reload());
  }

  window.LubanScreen = { start, fitCard, tableHijri, _state: state, _render: { markets: renderMarkets, weather: renderWeather } };
  start();
})();
