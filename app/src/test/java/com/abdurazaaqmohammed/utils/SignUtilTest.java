package com.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.abdurazaaqmohammed.testing.SplitApkFixture;
import com.android.apksig.ApkVerifier;
import com.android.apksig.ApkVerifier.Result;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.Security;

/**
 * A merged APK has to be signed or the platform refuses to install it, so this covers the whole
 * path: reading the bundled keystore and producing a signature the platform accepts.
 */
@RunWith(RobolectricTestRunner.class)
public class SignUtilTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void addBouncyCastle() {
        // The bundled keystore is BKS v1, which recent BouncyCastle refuses unless told otherwise.
        System.setProperty("org.bouncycastle.bks.enable_v1", "true");
        // Android ships the BouncyCastle provider; the JVM does not, and a BKS keystore needs it.
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    private File unsignedApk;

    @Before
    public void createUnsignedApk() throws IOException {
        unsignedApk = SplitApkFixture.writeSplitApk(temporaryFolder.getRoot(), "base.apk", false).file;
    }

    private File sign(boolean v1, boolean v2, boolean v3) throws Exception {
        File signed = new File(temporaryFolder.getRoot(), "signed-" + v1 + v2 + v3 + ".apk");
        SignUtil.signDebugKey(ApplicationProvider.getApplicationContext(), unsignedApk, signed,
                v1, v2, v3);
        return signed;
    }

    private static Result verify(File apk) throws Exception {
        return new ApkVerifier.Builder(apk).build().verify();
    }

    private static void assertVerified(Result result) {
        assertTrue("verification failed: " + result.getErrors(), result.isVerified());
        assertTrue("unexpected warnings: " + result.getWarnings(), result.getWarnings().isEmpty());
        assertNotNull("no signer certificate was reported", result.getSignerCertificates());
    }

    @Test
    public void producesASignedApkThatVerifies() throws Exception {
        File signed = sign(true, true, true);

        assertTrue("the output must exist", signed.isFile());
        assertTrue("the output must not be empty", signed.length() > 0);
        assertVerified(verify(signed));
    }

    @Test
    public void usesEverySchemeItWasAskedFor() throws Exception {
        assertVerified(verify(sign(true, true, true)));
        assertTrue("v1 must verify", verify(sign(true, false, false)).isVerifiedUsingV1Scheme());
        assertTrue("v2 must verify", verify(sign(false, true, false)).isVerifiedUsingV2Scheme());
        assertTrue("v3 must verify", verify(sign(false, false, true)).isVerifiedUsingV3Scheme());
    }

    @Test
    public void doesNotAddSchemesTheCallerDisabled() throws Exception {
        Result v2Only = verify(sign(false, true, false));

        assertFalse("v1 must be absent", v2Only.isVerifiedUsingV1Scheme());
        assertFalse("v3 must be absent", v2Only.isVerifiedUsingV3Scheme());
    }

    @Test
    public void signingWorksForEverySupportedApiLevel() throws Exception {
        // v1 is what API < 24 reads and v2 what API 24+ reads; a merge must produce both.
        File signed = sign(true, true, true);
        for (int minSdk : new int[]{21, 24, 28, 33}) {
            Result result = new ApkVerifier.Builder(signed)
                    .setMinCheckedPlatformVersion(minSdk).build().verify();

            assertTrue("minSdk " + minSdk + " failed: " + result.getErrors(), result.isVerified());
        }
    }

    @Test
    public void refusesToSignSomethingThatIsNotAnApk() {
        File notAnApk = new File(temporaryFolder.getRoot(), "not-an-apk.apk");
        try {
            Files.write(notAnApk.toPath(), "definitely not an apk".getBytes());
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        try {
            SignUtil.signDebugKey(ApplicationProvider.getApplicationContext(), notAnApk,
                    new File(temporaryFolder.getRoot(), "out.apk"));
            fail("signing a non-APK must fail rather than leave a broken output behind");
        } catch (Exception expected) {
            assertTrue("expected an IOException, got " + expected,
                    expected instanceof IOException);
        }
    }

    @Test
    public void reportsAMissingInput() {
        try {
            SignUtil.signDebugKey(ApplicationProvider.getApplicationContext(),
                    new File(temporaryFolder.getRoot(), "gone.apk"),
                    new File(temporaryFolder.getRoot(), "out.apk"));
            fail("a missing input must be reported");
        } catch (IOException expected) {
            assertNotNull(expected);
        }
    }

    @Test
    public void leavesTheUnsignedInputUntouched() throws Exception {
        long before = unsignedApk.length();

        sign(true, true, true);

        assertEquals("the input APK must not be modified", before, unsignedApk.length());
    }

    @Test
    public void canSignRepeatedly() throws Exception {
        assertVerified(verify(sign(true, true, true)));
        assertVerified(verify(sign(true, true, true)));
    }
}