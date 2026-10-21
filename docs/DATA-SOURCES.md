# Data sources

Luban is distributed **free, without ads or subscriptions, from GitHub**. Each source's terms were read against that
model (21 October 2026). If the model changes (an app store, a paid version, ads), this page is the checklist to redo.

| Source | What | Called by | Key | Limits | Terms that apply | Cache | When it is down |
|---|---|---|---|---|---|---|---|
| [Open-Meteo](https://open-meteo.com/en/terms) | weather, 6-day forecast, dust and PM10 | each TV, directly | none | under 10,000 calls a day (a TV makes ~100) | free API for non-commercial use: "private or non-profit websites or apps that do not have subscriptions or advertising" qualify; data under CC BY 4.0, so it is credited on screen | 30 min on the TV | the last value for up to 12 h, then no weather |
| [Yahoo Finance](https://finance.yahoo.com) chart API | gold (COMEX futures `GC=F`), Brent (`BZ=F`), S&P 500 | the data server; the app's allowlisted proxy without one | none | not published | no public API terms: an unofficial endpoint that may change or stop. Credited on screen | 15 min (server) | the last value, marked "as of HH:MM" after 2 h |
| [CoinGecko](https://www.coingecko.com) | Bitcoin | the data server, or each TV | none (public API) | not guaranteed on the keyless API | keyless/Demo use grants no commercial rights; attribution required when shown publicly. Credited on screen | 15 min (server) | as above |
| [football-data.org](https://www.football-data.org) | fixtures and results: Premier League, La Liga, Serie A, Bundesliga, Ligue 1, Eredivisie, Primeira Liga, Championship, Brasileirão, Champions League, World Cup, Euros | the data server only (the token stays there) | free token (`FOOTBALL_DATA_KEY`) | 10 calls a minute; scores delayed | free forever plan; no attribution clause published, credited anyway | 10 min for matches, 1 day for the team list | the last good data for up to a day |
| [TVmaze](https://www.tvmaze.com/api) | new episodes this week | the data server, or each TV | none | 20 calls / 10 s per IP | data under CC BY-SA: credited on screen and linked from the phone page | 6 h | the last list |
| [Wikimedia Commons](https://commons.wikimedia.org) | photos of Oman (optional background) | `tools/photos.py` in CI, once | none | — | each photo's own free licence (CC0, PD, CC BY, CC BY-SA): author and licence on screen and in [PHOTO-CREDITS.md](PHOTO-CREDITS.md) | shipped in the APK | — |
| King Fahd Complex (via `tools/content.py`) | Quran text, Uthmani script | built in | — | — | verse and surah shown with every verse | built in | — |
| Forty Hadith of an-Nawawi ([fawazahmed0/hadith-api](https://github.com/fawazahmed0/hadith-api), Unlicense) | hadith | built in | — | — | number and collection shown with every hadith; 32 of 42 kept, the ones under 60 words that fit the card | built in | — |

Prayer times, the Hijri date and the clock need no source: they are computed on the TV.

## Notes

- **Gold** is a global reference, not a shop price in Oman: COMEX gold futures in USD per troy ounce, ÷ 31.1034768 ×
  0.3845 (the rial's peg) = OMR per gram of pure gold. Shop prices add the karat, making charges and the dealer's
  margin. The screen labels it "global price".
- **Dust**: Open-Meteo's current hourly `dust` and `pm10`, in µg/m³. The screen shows "dust" when dust > 150 or
  PM10 > 250: thresholds picked to mark a dust event, not a health assessment.
- **Removed:** TheSportsDB (its free key finds only one team and one home match per team), Unsplash (its download link
  needs a key; Commons replaced it).
- **Not covered: the Saudi Pro League** (and other Gulf and Asian club competitions). Checked 21 October 2026:
  football-data.org has no Saudi or Asian club competition in any plan; API-Football's free plan stops at the 2024
  season ("Free plans do not have access to this season, try from 2022 to 2024"); TheSportsDB's free key works for no
  league. ESPN's site API carries it but is unofficial, with no published terms, so it is left out on purpose. To be
  added when a free source with clear terms appears.
- **Data server status:** `GET /ready` gives each source's state (fresh, stale, failing, idle, off) and the age of its
  data, with no error text; `GET /health` only says the process answers.
