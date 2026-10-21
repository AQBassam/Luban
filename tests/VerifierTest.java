import om.luban.screen.Verifier;

import java.nio.file.Files;
import java.nio.file.Paths;

/** Run by tests/run.sh after tools/sign.py signed a sample bundle with a throwaway key. */
public class VerifierTest {
    static int fails = 0;

    static void expect(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) fails++;
    }

    public static void main(String[] a) throws Exception {
        String dir = a[0];
        byte[] man = Files.readAllBytes(Paths.get(dir, "manifest.json"));
        byte[] sig = Files.readAllBytes(Paths.get(dir, "manifest.sig"));
        byte[] zip = Files.readAllBytes(Paths.get(dir, "bundle.zip"));
        String pub = new String(Files.readAllBytes(Paths.get(dir, "pub.b64")), "US-ASCII").trim();
        String other = new String(Files.readAllBytes(Paths.get(dir, "other.b64")), "US-ASCII").trim();

        Verifier.Manifest m = Verifier.check(man, sig, zip, pub, 5);
        expect("signed bundle accepted, version read", m.version == 7);
        expect("older or same version refused (no rollback)", fails(() -> Verifier.check(man, sig, zip, pub, 7)));
        byte[] zip2 = zip.clone(); zip2[zip2.length / 2] ^= 1;
        expect("changed bundle byte refused", fails(() -> Verifier.check(man, sig, zip2, pub, 5)));
        byte[] man2 = new String(man, "UTF-8").replace("\"version\":7", "\"version\":9").getBytes("UTF-8");
        expect("edited manifest refused", fails(() -> Verifier.check(man2, sig, zip, pub, 5)));
        expect("signature by another key refused", fails(() -> Verifier.check(man, sig, zip, other, 5)));
        expect("garbage signature refused", fails(() -> Verifier.check(man, "AAAA".getBytes(), zip, pub, 5)));
        if (fails > 0) System.exit(1);
        System.out.println("all verifier checks passed");
    }

    interface Check { void run() throws Exception; }

    static boolean fails(Check c) {
        try { c.run(); return false; } catch (Exception e) { return true; }
    }
}
