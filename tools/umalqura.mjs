// Writes content/umalqura.json: the Gregorian day (days since 1970-01-01) on which each Umm al-Qura month begins,
// 1440–1480 AH, taken from ICU. The screen uses it only when the TV's WebView lacks the Umm al-Qura calendar.
//   node tools/umalqura.mjs
import fs from 'fs';
const f = new Intl.DateTimeFormat('en-US-u-ca-islamic-umalqura-nu-latn', { timeZone: 'UTC', day: 'numeric', month: 'numeric', year: 'numeric' });
const starts = [];
let prev = null;
for (let day = Date.UTC(2018, 7, 1) / 864e5; day < Date.UTC(2059, 0, 1) / 864e5; day++) {
  const p = f.formatToParts(new Date(day * 864e5)).reduce((o, x) => (o[x.type] = x.value, o), {});
  const ym = [parseInt(p.year, 10), +p.month];
  if (+p.day === 1 && (!prev || prev[0] !== ym[0] || prev[1] !== ym[1]) && ym[0] >= 1440 && ym[0] <= 1480) starts.push([ym[0], ym[1], day]);
  prev = ym;
}
fs.writeFileSync(new URL('../content/umalqura.json', import.meta.url), JSON.stringify(starts) + '\n');
console.log(`${starts.length} months, ${starts[0]} … ${starts[starts.length - 1]}`);
