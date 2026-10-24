#!/usr/bin/env python3
"""site/ -> the LubanTV.bassamailab.xyz release

  python3 site/build.py OUT [--version 1.N]          the site folder only (for tests and a local look)
  python3 site/build.py OUT --version 1.N --tar      also lubantv-site-1.N.tar.gz and its .sha256 in OUT

The release folder holds:
  site/            what nginx serves: site/www, plus the fonts and brand files taken from the repo, plus version.json
  deploy/          install.sh, verify.sh, rollback.sh and the nginx files (from site/deploy)
  SHA256SUMS       one line per file in site/, checked by install.sh before anything is copied

Deterministic: the same sources and version give byte-identical files and archive.
"""
import argparse
import gzip
import hashlib
import io
import json
import shutil
import sys
import tarfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SITE = ROOT / "site"
FONTS = [f"el-messiri-{s}-{w}-normal.woff2" for s in ("arabic", "latin") for w in (400, 500, 600)]
BRAND = ROOT / "docs" / "brand" / "v4"
BRAND_FILES = {  # name on the site: file in docs/brand/v4
    "symbol.svg": "luban_symbol_v4.svg",
    "wordmark-resin.svg": "luban_wordmark_resin_v4.svg",
    "wordmark-deep.svg": "luban_wordmark_deep_v4.svg",
}


def files_under(d):
    return sorted(p for p in d.rglob("*") if p.is_file())


def build(out: Path, version: str) -> Path:
    rel = out / f"lubantv-site-{version}"
    if rel.exists():
        shutil.rmtree(rel)
    www = rel / "site"
    shutil.copytree(SITE / "www", www)
    (www / "assets" / "fonts").mkdir(parents=True)
    for f in FONTS:
        shutil.copyfile(ROOT / "assets" / "fonts" / f, www / "assets" / "fonts" / f)
    (www / "assets" / "brand").mkdir(parents=True)
    for name, src in BRAND_FILES.items():
        shutil.copyfile(BRAND / src, www / "assets" / "brand" / name)
    # the large symbol on the page: the same drawing with room for the ember's glow, which the tight one clips
    sym = (BRAND / "luban_symbol_v4.svg").read_text(encoding="utf-8")
    glow = sym.replace('viewBox="-50 -50 100 100" width="100" height="100"', 'viewBox="-62 -62 124 124" width="124" height="124"')
    assert glow != sym, "the symbol's viewBox changed: update site/build.py"
    (www / "assets" / "brand" / "symbol-glow.svg").write_text(glow, encoding="utf-8", newline="\n")

    files = files_under(www)
    h = hashlib.sha256()
    for p in files:
        h.update(p.relative_to(www).as_posix().encode() + b"\0" + p.read_bytes())
    (www / "version.json").write_text(json.dumps({"version": version, "contentHash": h.hexdigest()[:16]}) + "\n", encoding="utf-8", newline="\n")

    sums = "".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  ./{p.relative_to(www).as_posix()}\n" for p in files_under(www))
    (rel / "SHA256SUMS").write_text(sums, encoding="utf-8", newline="\n")
    shutil.copytree(SITE / "deploy", rel / "deploy")
    return rel


def tar(rel: Path) -> Path:
    """A reproducible .tar.gz: sorted names, fixed times and owners, scripts executable."""
    arc = rel.parent / f"{rel.name}.tar.gz"
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w", format=tarfile.PAX_FORMAT) as t:
        for p in [rel, *sorted(rel.rglob("*"))]:
            info = t.gettarinfo(str(p), arcname=p.relative_to(rel.parent).as_posix())
            info.mtime, info.uid, info.gid, info.uname, info.gname = 0, 0, 0, "root", "root"
            info.mode = 0o755 if p.is_dir() or p.suffix == ".sh" else 0o644
            if p.is_file():
                with open(p, "rb") as f:
                    t.addfile(info, f)
            else:
                t.addfile(info)
    with open(arc, "wb") as f, gzip.GzipFile(fileobj=f, mode="wb", mtime=0, filename="") as z:
        z.write(buf.getvalue())
    digest = hashlib.sha256(arc.read_bytes()).hexdigest()
    (rel.parent / f"{arc.name}.sha256").write_text(f"{digest}  {arc.name}\n", encoding="utf-8", newline="\n")
    return arc


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--version", default="dev")
    ap.add_argument("--tar", action="store_true")
    a = ap.parse_args()
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    rel = build(out, a.version)
    n = sum(1 for _ in files_under(rel / "site"))
    print(f"{rel}  ({n} files)")
    if a.tar:
        arc = tar(rel)
        print(f"{arc}  ({arc.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    sys.exit(main())
