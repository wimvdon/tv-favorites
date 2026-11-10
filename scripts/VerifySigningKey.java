import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;

/**
 * Checks that the signing key can actually be opened with ANDROID_KEY_PASSWORD.
 *
 * keytool cannot check this for PKCS12 keystores (it ignores -keypass), but the release
 * build would fail on it later with a confusing error. Passwords are read from the
 * environment and never printed.
 *
 * Usage: java scripts/VerifySigningKey.java KEYSTORE_FILE
 * Needs: ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD
 */
public class VerifySigningKey {
    public static void main(String[] args) throws Exception {
        String storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD");
        String alias = System.getenv("ANDROID_KEY_ALIAS");
        String keyPassword = System.getenv("ANDROID_KEY_PASSWORD");

        KeyStore keyStore = KeyStore.getInstance(new java.io.File(args[0]), storePassword.toCharArray());
        if (!keyStore.isKeyEntry(alias)) {
            fail("ANDROID_KEY_ALIAS does not name a private key in the keystore.");
        }
        try {
            Key key = keyStore.getKey(alias, keyPassword.toCharArray());
            if (!(key instanceof PrivateKey)) {
                fail("ANDROID_KEY_ALIAS does not name a private key in the keystore.");
            }
        } catch (UnrecoverableKeyException e) {
            fail("The key does not open with ANDROID_KEY_PASSWORD. "
                + "For keystores made with keytool (PKCS12) it is the same as the keystore password.");
        }
        System.out.println("Signing key opens with ANDROID_KEY_PASSWORD (" + keyStore.getType() + " keystore).");
    }

    private static void fail(String message) {
        System.out.println("::error title=Signing key::" + message);
        System.exit(1);
    }
}
