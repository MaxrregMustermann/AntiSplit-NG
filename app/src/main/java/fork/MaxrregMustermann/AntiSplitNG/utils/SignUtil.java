package fork.MaxrregMustermann.AntiSplitNG.utils;

import android.content.Context;

import com.android.apksig.ApkSigner;
import com.android.apksig.apk.ApkFormatException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/**
 * Signs merged APKs with the bundled debug keystore.
 *
 * <p>A merged APK has to be signed before the platform will install it. The key here is the public
 * Android debug key, so the result is only good for sideloading and for apps that do not verify
 * their own signature, which is what merging is for.
 */
public final class SignUtil {

    private static final String KEYSTORE_PASSWORD = "android";
    private static final String SIGNER_NAME = "CERT";

    private SignUtil() {
    }

    public static void signDebugKey(Context context, File inputApk, File output) throws IOException {
        signDebugKey(context, inputApk, output, true, true, true);
    }

    public static void signDebugKey(Context context, File inputApk, File output, boolean v1,
            boolean v2, boolean v3) throws IOException {
        try (InputStream keystoreStream = context.getAssets().open("debug23.keystore")) {
            sign(keystoreStream, inputApk, output, v1, v2, v3);
        } catch (GeneralSecurityException | ApkFormatException e) {
            throw new IOException("Could not sign the merged APK: " + e.getMessage(), e);
        }
    }

    static void sign(InputStream keystoreStream, File inputApk, File output, boolean v1, boolean v2,
            boolean v3) throws IOException, GeneralSecurityException, ApkFormatException {
        char[] password = KEYSTORE_PASSWORD.toCharArray();
        KeyStore keystore = KeyStore.getInstance("BKS");
        keystore.load(keystoreStream, password);

        String alias = keystore.aliases().nextElement();
        KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) keystore.getEntry(alias,
                new KeyStore.PasswordProtection(password));
        PrivateKey privateKey = entry.getPrivateKey();

        new ApkSigner.Builder(Collections.singletonList(
                new ApkSigner.SignerConfig.Builder(SIGNER_NAME, privateKey,
                        Collections.singletonList((X509Certificate) keystore.getCertificate(alias)))
                        .build()))
                .setInputApk(inputApk)
                .setOutputApk(output)
                .setCreatedBy("AntiSplit M")
                .setV1SigningEnabled(v1)
                .setV2SigningEnabled(v2)
                .setV3SigningEnabled(v3)
                .build()
                .sign();
    }
}