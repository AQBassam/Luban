# موقع LubanTV.bassamailab.xyz

صفحة تعريف بلُبان، بالعربية (`/`) والإنجليزية (`/en/`). ملفات ثابتة بلا خادم ولا قاعدة بيانات ولا ملفات تعريف ارتباط
ولا إحصاءات، ولا تطلب شيئًا من خارج الموقع: سياسة CSP تبدأ بـ `default-src 'none'`.

وهو منفصل عن خادم البيانات `luban.bassamailab.xyz`: لا يجمعهما إلا الخادم نفسه.

## الملفات

| | |
|---|---|
| `www/` | الصفحتان، والأنماط `assets/site.css`، وشيفرة أزرار النسخ `assets/site.js`، واللقطات `assets/img/` |
| `screens/` | الواجهة الرئيسية وشاشة رمز الإعدادات مرسومتين للّقطات (هما في التلفاز تطبيق أندرويد أصلي): بلاطات محايدة، ورمز لا يفتح شيئًا، وعنوان مختلَق |
| `shots.mjs` | يرسم اللقطات كلها: شاشة التوقف الحقيقية `dist/luban.html` بإعدادات مسقط وساعة ثابتة، و`screens/` |
| `build.py` | يجمع الإصدار: `www/` مع الخطوط من `assets/fonts` والشعار من `docs/brand/v4`، و`version.json`، و`SHA256SUMS`، و`deploy/` |
| `deploy/` | `install.sh` و`verify.sh` و`rollback.sh`، وملف موقع nginx وترويسات الأمان |

**لا تُلتقط صور من تلفاز حقيقي للموقع:** لقطة التلفاز تكشف تطبيقاته، وعنوانه في الشبكة، ورابط إعداداته الخاص.
بعد تعديل الشاشة: `node site/shots.mjs`، ثم راجع الصور والتزم بها.

`tests/site.test.mjs` يبني الإصدار ويقدّمه بترويسات الأمان نفسها، ثم يفحص الصفحتين على عرض 1440 و390.

## مسار الإصدار

كل دفع إلى `main` يُرفق بإصدار `v1.N` الأرشيف `lubantv-site-1.N.tar.gz` وبصمته. التحديث على الخادم يدوي، ومتى شئت.

## التثبيت على الخادم (كل إصدار)

```bash
V=1.N   # رقم الإصدار من https://github.com/AQBassam/Luban/releases
cd /root
curl -fLO https://github.com/AQBassam/Luban/releases/download/v$V/lubantv-site-$V.tar.gz
curl -fLO https://github.com/AQBassam/Luban/releases/download/v$V/lubantv-site-$V.tar.gz.sha256
sha256sum -c lubantv-site-$V.tar.gz.sha256
tar -xzf lubantv-site-$V.tar.gz
cd lubantv-site-$V
bash deploy/install.sh
bash deploy/verify.sh
```

`install.sh` يتحقق من بصمة كل ملف، وينسخ الإصدار إلى `/var/www/lubantv/releases/`، ويبدّل الرابط `current`
دون انقطاع، ويحدّث ترويسات الأمان (ويعيد القديمة إن رفضها `nginx -t`). للرجوع: `bash deploy/rollback.sh`.

## الإعداد لأول مرة

1. **DNS:** سجل `A` باسم `lubantv` يشير إلى `161.97.99.78` (دون `AAAA`، كبقية المواقع على الخادم).
   تحقّق: `dig +short lubantv.bassamailab.xyz`.
2. **nginx** (من داخل مجلد الإصدار بعد فكّه):
   ```bash
   cp deploy/nginx/lubantv-security-headers.conf /etc/nginx/snippets/
   cp deploy/nginx/lubantv.bassamailab.xyz.conf /etc/nginx/sites-available/lubantv.bassamailab.xyz
   ln -s /etc/nginx/sites-available/lubantv.bassamailab.xyz /etc/nginx/sites-enabled/
   mkdir -p /var/www/lubantv
   bash deploy/install.sh
   bash deploy/verify.sh http://127.0.0.1 lubantv.bassamailab.xyz
   ```
3. **الشهادة:** `certbot --nginx -d lubantv.bassamailab.xyz --redirect`، ثم `bash deploy/verify.sh`.
4. **بعد يوم من عمل HTTPS:** احذف `#` من سطر `Strict-Transport-Security` في
   `/etc/nginx/snippets/lubantv-security-headers.conf` وفي `site/deploy/nginx/` معًا، ثم `nginx -t && systemctl reload nginx`.

**بعد certbot لا يُنسخ ملف الموقع من جديد:** certbot كتب أسطر الشهادة في الملف الحي، والنسخ فوقه يمحوها.
أي تعديل على ملف الموقع يُنقل إليه يدويًا.
