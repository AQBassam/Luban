#!/usr/bin/env bash
# Checks the deployed site from any machine with curl (the server, a Mac, Linux, Git Bash on Windows):
#   bash deploy/verify.sh                                          # https://lubantv.bassamailab.xyz
#   bash deploy/verify.sh http://127.0.0.1 lubantv.bassamailab.xyz # on the server, before DNS or TLS
set -uo pipefail
BASE="${1:-https://lubantv.bassamailab.xyz}"
HOSTH="${2:-}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPECT="$(sed -n 's/.*"contentHash": *"\([^"]*\)".*/\1/p' "$HERE/site/version.json" 2>/dev/null)"
CSP="default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; manifest-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
pass=0; failn=0
H=(); [ -n "$HOSTH" ] && H=(-H "Host: $HOSTH")
ok()  { printf '  \033[32mناجح\033[0m  %s\n' "$1"; pass=$((pass+1)); }
bad() { printf '  \033[31mفاشل\033[0m  %s — %s\n' "$1" "$2"; failn=$((failn+1)); }
hdr() { curl -sS -o /dev/null -D - --max-time 15 "${H[@]}" "$BASE$1" | tr -d '\r'; }
has() { grep -qiF -- "$2" <<<"$1"; }
code() { head -1 <<<"$1" | grep -qE "^HTTP/[0-9.]+ $2"; }

echo "فحص $BASE"
h=$(hdr /)
code "$h" 200 && ok "الصفحة العربية 200" || bad "الصفحة العربية" "$(head -1 <<<"$h")"
has "$h" 'content-type: text/html' && ok "نوع المحتوى HTML" || bad "نوع المحتوى" "$(grep -i content-type <<<"$h")"
has "$h" "content-security-policy: $CSP" && ok "سياسة CSP كاملة: لا شيء من خارج الموقع" || bad "CSP" "$(grep -i content-security-policy <<<"$h")"
has "$h" 'x-content-type-options: nosniff' && ok "nosniff" || bad "nosniff" "مفقود"
has "$h" 'cache-control: no-cache' && ok "الصفحة لا تُخزَّن قديمة" || bad "Cache-Control للصفحة" "$(grep -i cache-control <<<"$h")"
has "$h" 'set-cookie' && bad "ملفات تعريف الارتباط" "الصفحة ترسل Set-Cookie" || ok "بلا ملفات تعريف ارتباط"

e=$(hdr /en/); code "$e" 200 && has "$e" 'content-type: text/html' && ok "الصفحة الإنجليزية /en/" || bad "الصفحة الإنجليزية" "$(head -1 <<<"$e")"
c=$(hdr /assets/site.css); code "$c" 200 && has "$c" 'content-type: text/css' && ok "ملف الأنماط بنوع صحيح" || bad "site.css" "$(head -1 <<<"$c") $(grep -i content-type <<<"$c")"
j=$(hdr /assets/site.js); code "$j" 200 && grep -qiE 'content-type: (application|text)/javascript' <<<"$j" && ok "ملف الشيفرة بنوع صحيح" || bad "site.js" "$(grep -i content-type <<<"$j")"
f=$(hdr /assets/fonts/el-messiri-arabic-400-normal.woff2); code "$f" 200 && has "$f" 'content-type: font/woff2' && ok "الخط woff2 بنوع صحيح" || bad "الخط" "$(head -1 <<<"$f") $(grep -i content-type <<<"$f")"
i=$(hdr /assets/img/saver-chart.jpg); code "$i" 200 && has "$i" 'content-type: image/jpeg' && has "$i" 'max-age=604800' && ok "اللقطات مخزنة أسبوعًا" || bad "اللقطات" "$(head -1 <<<"$i") $(grep -iE 'content-type|cache-control' <<<"$i" | tr '\n' ' ')"
s=$(hdr /assets/brand/wordmark-resin.svg); code "$s" 200 && has "$s" 'content-type: image/svg+xml' && ok "الشعار SVG بنوع صحيح" || bad "الشعار" "$(grep -i content-type <<<"$s")"
has "$i" 'content-security-policy' && ok "ترويسات الأمان على الملفات أيضًا" || bad "ترويسات الأمان على /assets/" "مفقودة"
d=$(hdr /.previous); code "$d" '40[34]' && ok "الملفات المخفية محجوبة" || bad "الملفات المخفية" "$(head -1 <<<"$d")"
n=$(hdr /nothing-here); code "$n" 404 && ok "الصفحات غير الموجودة 404" || bad "صفحة غير موجودة" "$(head -1 <<<"$n")"

v=$(curl -sS --max-time 15 "${H[@]}" "$BASE/version.json")
got=$(sed -n 's/.*"contentHash": *"\([^"]*\)".*/\1/p' <<<"$v")
ver=$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' <<<"$v")
if [ -n "$EXPECT" ]; then [ "$got" = "$EXPECT" ] && ok "الإصدار المنشور ${ver:-؟} يطابق الحزمة ($got)" || bad "مطابقة الإصدار" "المنشور: ${got:-لا شيء} / المتوقع: $EXPECT"
else [ -n "$got" ] && ok "version.json: ${ver:-؟} ($got)" || bad "version.json" "غير موجود"; fi

if [[ "$BASE" == https://* ]]; then
  r=$(curl -sS -o /dev/null -w '%{http_code} %{redirect_url}' --max-time 15 "http://${BASE#https://}/")
  [[ "$r" == 301\ https://* || "$r" == 308\ https://* ]] && ok "HTTP يحوّل إلى HTTPS" || bad "التحويل إلى HTTPS" "$r"
  host="${BASE#https://}"; host="${host%%/*}"
  exp=$(echo | openssl s_client -servername "$host" -connect "$host:443" 2>/dev/null | openssl x509 -noout -enddate -issuer 2>/dev/null | tr '\n' ' ')
  [ -n "$exp" ] && ok "شهادة TLS: $exp" || bad "شهادة TLS" "تعذرت قراءتها"
fi
echo; echo "النتيجة: $pass ناجح، $failn فاشل"
[ "$failn" -eq 0 ]
