# Prayer times · مواقيت الصلاة

Luban computes the times on the TV itself (`web/js/prayer.js`, and its Java twin `PrayerTimes.java` for the home
screen; `tests/prayer_parity.sh` checks they agree on 900 cases). No network is needed.

**Sun position:** the low-precision formulas of the U.S. Naval Observatory, refined in two passes; about a minute of
accuracy, which is what published calendars round to anyway.

## Methods

| id | Fajr | Isha | Used by default for |
|---|---|---|---|
| `oman` | 18° | 17° (+5 min on Dhuhr, Asr, Maghrib, Isha) | Oman |
| `makkah` | 18.5° | 90 min after Maghrib (120 in Ramadan) | Saudi Arabia |
| `gulf` | 19.5° | 90 min | Bahrain |
| `qatar` | 18° | 90 min | Qatar |
| `kuwait` | 18° | 17.5° | Kuwait |
| `dubai` | 18.2° | 18.2° | UAE |
| `egypt` | 19.5° | 17.5° | Egypt |
| `mwl` | 18° | 17° | Jordan, Tanzania, Kenya, UK, Germany, Australia, Japan, custom places |
| `karachi` | 18° | 18° | Pakistan, India |
| `isna` | 15° | 15° | USA, Canada |
| `singapore` | 20° | 18° | Malaysia, Indonesia |
| `turkey` | 18° | 17° | Türkiye |
| `france` | 12° | 12° | France |
| `tehran` | 17.7° | 14° (Maghrib at 4.5°) | — |

**Oman (`oman`):** 18°/17°, plus the 5-minute precaution (احتياط) that the Ministry of Awqaf and Religious Affairs
adds to Dhuhr, Asr, Maghrib and Isha (Fajr and Sunrise are not shifted). Checked against the ministry's own calendar
app (تقويم عُمان) for Muscat, Salalah, Khasab, Sur, Ibri and Sohar on 21 October 2026: every time within one minute,
most to the minute; the remaining minute comes from the reference point the ministry uses for each wilayat, which it
does not publish. `tests/page.test.mjs` keeps checking all 30 times. The phone settings page still has a ± minutes
field per prayer for anyone who follows a local mosque.

**High latitudes** (above ~48° in summer the sun may not reach the Fajr/Isha angle): the "angle ÷ 60 of the night"
rule, as most calculators use.

**Asr:** standard (shadow = object + noon shadow) or Hanafi (twice the object).

**Iqama:** minutes after the adhan, per prayer, default Fajr 25, Dhuhr 20, Asr 20, Maghrib 10, Isha 20. Friday's
Dhuhr is shown as الجمعة, without an iqama countdown.

**Hijri date:** Umm al-Qura (the browser's / Android's own ICU calendar), with a −2…+2 day adjustment for countries
that follow a local sighting, Oman among them.

## Sources

- [Aladhan: calculation methods](https://aladhan.com/calculation-methods)
- [ITL / Arabeyes method notes](https://github.com/arabeyes-org/ITL/blob/master/prayertime/doc/method-info.md)
- [Ministry of Awqaf and Religious Affairs](https://www.mara.gov.om) and its calendar app تقويم عُمان (the Oman check above)
- [PrayTimes.org: calculation](https://praytimes.org/docs/calculation)
