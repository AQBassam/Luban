#!/usr/bin/env bash
# tools/sign.py (Python) signs, Verifier.java (the apps' code) checks: the two must agree byte for byte.
set -euo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
python3 - "$T" <<'EOF'
import base64, sys, zipfile, io
from pathlib import Path
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
sys.path.insert(0, "tools")
import sign
t = Path(sys.argv[1])
pem = lambda k: k.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
der = lambda k: base64.b64encode(k.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo))
key, other = ec.generate_private_key(ec.SECP256R1()), ec.generate_private_key(ec.SECP256R1())
buf = io.BytesIO()
with zipfile.ZipFile(buf, "w") as z: z.writestr("index.html", "<html>test</html>")
bundle = buf.getvalue()
m = sign.manifest_bytes(7, bundle)
(t / "bundle.zip").write_bytes(bundle); (t / "manifest.json").write_bytes(m); (t / "manifest.sig").write_bytes(sign.sign(pem(key), m))
(t / "pub.b64").write_bytes(der(key)); (t / "other.b64").write_bytes(der(other))
EOF
javac -d "$T/cls" android/app/src/main/java/om/luban/screen/Verifier.java tests/VerifierTest.java
java -cp "$T/cls" VerifierTest "$T"
echo "-- again with the TV set to Arabic (Oman): hex digits must stay 0-9"
java -Duser.language=ar -Duser.country=OM -cp "$T/cls" VerifierTest "$T"
