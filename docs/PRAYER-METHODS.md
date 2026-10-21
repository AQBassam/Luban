# Prayer times · مواقيت الصلاة

Luban computes the times on the TV itself (`web/js/prayer.js`, and its Java twin `PrayerTimes.java` for the home
screen; `tests/prayer_parity.sh` checks they agree on 900 cases). No network is needed.

**Sun position:** the low-precision formulas of the U.S. Naval Observatory, refined in two passes; about a minute of
accuracy, which is what published calendars round to anyway.

## Methods

| id | Fajr | Isha | Used by default for |
|---|---|---|---|
| `oman` | 18° | 17° | Oman |
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

**Oman (`oman`):** 18°/17° reproduces the published Muscat table to the minute on 19 October 2026 (Fajr 04:50,
Sunrise 06:05, Dhuhr 11:52, Asr 15:09, Maghrib 17:37, Isha 18:48; `tests/page.test.mjs` keeps checking it).
The Ministry of Awqaf and Religious Affairs publishes its own calendar with small local adjustments; the phone
settings page has a ± minutes field per prayer for matching it exactly.

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
- [Muscat prayer times, khaleejcalculators.com](https://khaleejcalculators.com/prayer-times/muscat) (the Oman check above)
- [PrayTimes.org: calculation](https://praytimes.org/docs/calculation)
