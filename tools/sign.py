#!/usr/bin/env python3
"""Sign dist/bundle.zip for over-the-air screen updates.

    LUBAN_UPDATE_KEY="$(cat key.pem)" python3 tools/sign.py <version>   -> dist/manifest.json, dist/manifest.sig

manifest.json = {"format":1,"version":N,"sha256":"<hex of bundle.zip>","size":bytes}
manifest.sig  = base64 of the ECDSA P-256 / SHA-256 signature (DER) over the exact bytes of manifest.json.
The apps carry the public key (keys/screen-update.pub.pem) and refuse any bundle whose manifest is not signed by
it, whose hash does not match, or whose version is not newer than what they run (no rollback).
"""
import base64
import hashlib
import json
import os
import sys
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

DIST = Path(__file__).resolve().parents[1] / "dist"


def manifest_bytes(version, bundle):
    return json.dumps({"format": 1, "version": version, "sha256": hashlib.sha256(bundle).hexdigest(), "size": len(bundle)},
                      separators=(",", ":"), sort_keys=True).encode()


def sign(key_pem, data):
    key = serialization.load_pem_private_key(key_pem, password=None)
    if not isinstance(key, ec.EllipticCurvePrivateKey) or key.curve.name != "secp256r1":
        raise SystemExit("LUBAN_UPDATE_KEY must be an EC P-256 private key")
    return base64.b64encode(key.sign(data, ec.ECDSA(hashes.SHA256())))


def main():
    version = int(sys.argv[1])
    key = os.environ.get("LUBAN_UPDATE_KEY", "").encode()
    if not key.strip():
        raise SystemExit("LUBAN_UPDATE_KEY is not set")
    m = manifest_bytes(version, (DIST / "bundle.zip").read_bytes())
    (DIST / "manifest.json").write_bytes(m)
    (DIST / "manifest.sig").write_bytes(sign(key, m))
    print(m.decode())


if __name__ == "__main__":
    main()
