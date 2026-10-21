package om.luban.screen;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Checks a screen update before it is used. Pure Java (no Android), so tests/ runs it with plain javac.
 * The manifest is the exact bytes that were signed; its fields are read with a strict tiny parser, not a JSON library,
 * so the same bytes mean the same thing everywhere.
 */
public final class Verifier {
    private Verifier() {}

    public static final class Manifest {
        public final int version;
        public final String sha256;
        public final long size;
        Manifest(int version, String sha256, long size) { this.version = version; this.sha256 = sha256; this.size = size; }
    }

    /** @return the manifest when the signature is valid and the fields are well formed; otherwise throws. */
    public static Manifest verify(byte[] manifest, byte[] signatureB64, String publicKeyB64) throws Exception {
        PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64)));
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initVerify(key);
        sig.update(manifest);
        byte[] der = Base64.getDecoder().decode(new String(signatureB64, "US-ASCII").trim());
        if (!sig.verify(der)) throw new SecurityException("bad signature");
        String m = new String(manifest, "UTF-8");
        if (!m.matches("\\{\"format\":1,\"sha256\":\"[0-9a-f]{64}\",\"size\":\\d{1,9},\"version\":\\d{1,9}\\}"))
            throw new SecurityException("unexpected manifest");
        String sha = m.replaceAll(".*\"sha256\":\"([0-9a-f]{64})\".*", "$1");
        long size = Long.parseLong(m.replaceAll(".*\"size\":(\\d+).*", "$1"));
        int version = Integer.parseInt(m.replaceAll(".*\"version\":(\\d+).*", "$1"));
        return new Manifest(version, sha, size);
    }

    public static String sha256Hex(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format(java.util.Locale.ROOT, "%02x", x & 0xff));
        return b.toString();
    }

    /** Whole check: signed manifest, newer than what runs, and the bundle bytes are exactly the ones signed. */
    public static Manifest check(byte[] manifest, byte[] sig, byte[] bundle, String publicKeyB64, int runningVersion) throws Exception {
        Manifest m = verify(manifest, sig, publicKeyB64);
        if (m.version <= runningVersion) throw new SecurityException("not newer: " + m.version + " <= " + runningVersion);
        if (bundle.length != m.size || !sha256Hex(bundle).equals(m.sha256)) throw new SecurityException("bundle does not match manifest");
        return m;
    }
}
