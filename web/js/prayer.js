/* Luban — prayer times engine.
 * Sun position from the low-precision formulas published by the U.S. Naval Observatory
 * (accurate to about a minute for prayer purposes). Everything is computed on the device:
 * no network is needed for the times.
 */
(function (root) {
  'use strict';

  // Twilight angles in degrees below the horizon, or a fixed interval in minutes after Maghrib.
  // Sources for each authority are listed in docs/PRAYER-METHODS.md.
  const METHODS = {
    oman:     { fajr: 18,   isha: 17,  label: { ar: 'سلطنة عُمان (18°/17°)', en: 'Oman (18°/17°)' } },
    gulf:     { fajr: 19.5, ishaMin: 90, label: { ar: 'منطقة الخليج', en: 'Gulf Region' } },
    mwl:      { fajr: 18,   isha: 17,  label: { ar: 'رابطة العالم الإسلامي', en: 'Muslim World League' } },
    makkah:   { fajr: 18.5, ishaMin: 90, ishaMinRamadan: 120, label: { ar: 'أم القرى (مكة)', en: 'Umm al-Qura, Makkah' } },
    egypt:    { fajr: 19.5, isha: 17.5, label: { ar: 'الهيئة المصرية العامة للمساحة', en: 'Egyptian General Authority' } },
    karachi:  { fajr: 18,   isha: 18,  label: { ar: 'جامعة العلوم الإسلامية، كراتشي', en: 'University of Islamic Sciences, Karachi' } },
    isna:     { fajr: 15,   isha: 15,  label: { ar: 'الجمعية الإسلامية لأمريكا الشمالية', en: 'ISNA (North America)' } },
    kuwait:   { fajr: 18,   isha: 17.5, label: { ar: 'الكويت', en: 'Kuwait' } },
    qatar:    { fajr: 18,   ishaMin: 90, label: { ar: 'قطر', en: 'Qatar' } },
    dubai:    { fajr: 18.2, isha: 18.2, label: { ar: 'دبي (غير رسمي)', en: 'Dubai (unofficial)' } },
    singapore:{ fajr: 20,   isha: 18,  label: { ar: 'سنغافورة', en: 'Singapore (MUIS)' } },
    turkey:   { fajr: 18,   isha: 17,  label: { ar: 'تركيا (ديانت)', en: 'Turkey (Diyanet)' } },
    france:   { fajr: 12,   isha: 12,  label: { ar: 'فرنسا (12°)', en: 'France (UOIF 12°)' } },
    tehran:   { fajr: 17.7, isha: 14, maghribAngle: 4.5, label: { ar: 'طهران', en: 'Tehran' } },
  };

  const KEYS = ['fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha'];
  const D2R = Math.PI / 180, R2D = 180 / Math.PI;
  const sinD = d => Math.sin(d * D2R), cosD = d => Math.cos(d * D2R), tanD = d => Math.tan(d * D2R);
  const asinD = x => Math.asin(x) * R2D, acosD = x => Math.acos(x) * R2D;
  const wrap = (x, m) => ((x % m) + m) % m;

  /** Offset of an IANA zone from UTC, in hours, at a given instant. */
  function zoneOffsetHours(tz, instant) {
    const p = new Intl.DateTimeFormat('en-US', {
      timeZone: tz, hour12: false, year: 'numeric', month: 'numeric', day: 'numeric',
      hour: 'numeric', minute: 'numeric', second: 'numeric',
    }).formatToParts(instant).reduce((o, x) => (o[x.type] = +x.value, o), {});
    const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour % 24, p.minute, p.second);
    return Math.round((asUtc - instant.getTime()) / 60000) / 60;
  }

  /** Declination and equation of time for a Julian day number. */
  function sunAt(jd) {
    const d = jd - 2451545.0;
    const g = wrap(357.529 + 0.98560028 * d, 360);
    const q = wrap(280.459 + 0.98564736 * d, 360);
    const lambda = wrap(q + 1.915 * sinD(g) + 0.020 * sinD(2 * g), 360);
    const eps = 23.439 - 0.00000036 * d;
    const ra = wrap(Math.atan2(cosD(eps) * sinD(lambda), cosD(lambda)) * R2D / 15, 24);
    return { decl: asinD(sinD(eps) * sinD(lambda)), eqt: q / 15 - ra };
  }

  /**
   * Prayer times for one civil date in one place.
   * @param {{y:number,m:number,d:number}} date  the local calendar date
   * @param {{lat:number,lon:number,tz:string}} place
   * @param {object} opt  {method, asr:'standard'|'hanafi', offsets:{fajr:..}, ramadan:boolean}
   * @returns {{fajr:number,...}} minutes after local midnight (may be fractional)
   */
  function compute(date, place, opt) {
    opt = opt || {};
    const m = METHODS[opt.method] || METHODS.oman;
    const noonUtc = new Date(Date.UTC(date.y, date.m - 1, date.d, 12));
    const tz = zoneOffsetHours(place.tz, noonUtc);
    const lat = place.lat, lon = place.lon;
    // Julian day at local midnight, longitude-adjusted
    const jd0 = Date.UTC(date.y, date.m - 1, date.d) / 86400000 + 2440587.5 - lon / 360;

    const transit = h => wrap(12 - sunAt(jd0 + h / 24).eqt, 24);
    const hourAngle = (angle, h) => {
      const dec = sunAt(jd0 + h / 24).decl;
      const x = (-sinD(angle) - sinD(dec) * sinD(lat)) / (cosD(dec) * cosD(lat));
      return x < -1 || x > 1 ? NaN : acosD(x) / 15;
    };
    // Two passes: estimate, then refine at the estimated hour.
    const before = (angle, guess) => {
      let t = transit(guess) - hourAngle(angle, guess);
      return transit(t) - hourAngle(angle, t);
    };
    const after = (angle, guess) => {
      let t = transit(guess) + hourAngle(angle, guess);
      return transit(t) + hourAngle(angle, t);
    };
    const asrAngle = h => {
      const dec = sunAt(jd0 + h / 24).decl;
      const factor = opt.asr === 'hanafi' ? 2 : 1;
      return -Math.atan(1 / (factor + tanD(Math.abs(lat - dec)))) * R2D;
    };

    const SUN = 0.833;
    const t = {};
    t.dhuhr = transit(12);
    t.sunrise = before(SUN, 6);
    t.maghrib = m.maghribAngle ? after(m.maghribAngle, 18) : after(SUN, 18);
    const sunset = after(SUN, 18);
    t.fajr = before(m.fajr, 5);
    let a = asrAngle(13);
    t.asr = after(a, 13);
    a = asrAngle(t.asr);
    t.asr = after(a, t.asr);
    if (m.ishaMin) {
      t.isha = t.maghrib + ((opt.ramadan && m.ishaMinRamadan) || m.ishaMin) / 60;
    } else {
      t.isha = after(m.isha, 18);
    }

    // High latitudes: when the sun never reaches the angle, use the "angle/60 of the night" rule.
    const night = wrap(t.sunrise - sunset, 24);
    if (isNaN(t.fajr) || wrap(t.sunrise - t.fajr, 24) > night * m.fajr / 60) {
      t.fajr = t.sunrise - night * m.fajr / 60;
    }
    if (!m.ishaMin && (isNaN(t.isha) || wrap(t.isha - sunset, 24) > night * m.isha / 60)) {
      t.isha = sunset + night * m.isha / 60;
    }

    const off = opt.offsets || {};
    const out = {};
    for (const k of KEYS) {
      const local = t[k] + tz - lon / 15;   // hours, local clock
      out[k] = wrap(local * 60 + (+off[k] || 0), 1440);
    }
    return out;
  }

  /** "HH:MM" from minutes after midnight, rounded to the nearest minute. */
  function hhmm(min) {
    const r = Math.round(min) % 1440;
    return String(Math.floor(r / 60)).padStart(2, '0') + ':' + String(r % 60).padStart(2, '0');
  }

  const api = { METHODS, KEYS, compute, hhmm, zoneOffsetHours };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.LubanPrayer = api;
})(typeof window !== 'undefined' ? window : globalThis);
