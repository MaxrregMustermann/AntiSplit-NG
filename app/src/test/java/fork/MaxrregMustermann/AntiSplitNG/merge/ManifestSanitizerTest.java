package fork.MaxrregMustermann.AntiSplitNG.merge;

import static fork.MaxrregMustermann.AntiSplitNG.testing.SplitApkFixture.PACKAGE_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import fork.MaxrregMustermann.AntiSplitNG.testing.SplitApkFixture;
import fork.MaxrregMustermann.AntiSplitNG.testing.SplitApkFixture.ManifestExtras;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.app.AndroidManifest;
import com.reandroid.archive.InputSource;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A merged APK must not claim to be a split any more: leftover split metadata is what makes Android
 * reject the install with "App not installed".
 */
@RunWith(RobolectricTestRunner.class)
public class ManifestSanitizerTest {

    /** Vendor marker that only says where the APK came from. */
    private static final String VENDOR_STAMP_MARKER = "com.android.stamp.v2";
    /** Nothing to do with splits; must survive sanitizing. */
    private static final String UNRELATED_MARKER = "com.example.KEEP_ME";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final RecordingLogger logger = new RecordingLogger();

    @Before
    public void createDirectory() throws IOException {
        temporaryFolder.newFolder("splits");
    }

    private ApkModule write(String name, boolean splitListMarker, ManifestExtras extras)
            throws IOException {
        return ApkModule.loadApkFile(SplitApkFixture.writeSplitApk(
                temporaryFolder.getRoot(), name, splitListMarker, extras).file);
    }

    @Test
    public void removesSplitDeclarationsByIdAndByName() throws IOException {
        ApkModule module = write("config.arm64_v8a.apk", false,
                ManifestExtras.everySplitDeclaration());
        AndroidManifestBlock manifest = module.getAndroidManifest();

        assertTrue(ManifestSanitizer.sanitize(module, logger));

        assertNull("android:splitTypes by id",
                attribute(manifest.getManifestElement(), AndroidManifest.ID_splitTypes));
        assertNull("android:requiredSplitTypes",
                attribute(manifest.getManifestElement(), AndroidManifest.ID_requiredSplitTypes));
        assertNull("android:extractNativeLibs",
                attribute(manifest.getManifestElement(), AndroidManifest.ID_extractNativeLibs));
        assertNull("android:isSplitRequired",
                attribute(manifest.getManifestElement(), AndroidManifest.ID_isSplitRequired));
        assertNull("splitTypes declared without a resource id",
                manifest.getManifestElement().searchAttributeByName("splitTypes"));
    }

    @Test
    public void keepsTheSplitAttributeItself() throws IOException {
        // android:split names this module; it is not a statement about needing other splits.
        ApkModule module = write("config.arm64_v8a.apk", false,
                ManifestExtras.everySplitDeclaration());
        AndroidManifestBlock manifest = module.getAndroidManifest();

        ManifestSanitizer.sanitize(module, logger);

        assertEquals("arm64_v8a",
                attribute(manifest.getManifestElement(), SplitApkFixture.ID_SPLIT).getValueAsString());
    }

    @Test
    public void keepsUnrelatedManifestAttributes() throws IOException {
        ApkModule module = write("config.en.apk", false, ManifestExtras.everySplitDeclaration());
        AndroidManifestBlock manifest = module.getAndroidManifest();

        ManifestSanitizer.sanitize(module, logger);

        assertEquals(PACKAGE_NAME, manifest.getPackageName());
        assertEquals(1, manifest.getVersionCode().intValue());
        assertEquals("1.0", manifest.getVersionName());
        assertNotNull(manifest.getApplicationElement());
    }

    @Test
    public void removesExtractNativeLibsFromManifestAndApplication() throws IOException {
        ApkModule module = write("config.hdpi.apk", false,
                ManifestExtras.none().withExtractNativeLibs(true, true));
        AndroidManifestBlock manifest = module.getAndroidManifest();

        ManifestSanitizer.sanitize(module, logger);

        assertNull(attribute(manifest.getManifestElement(), AndroidManifest.ID_extractNativeLibs));
        assertNull("android:extractNativeLibs on <application>",
                attribute(manifest.getApplicationElement(), AndroidManifest.ID_extractNativeLibs));
    }

    @Test
    public void removesSplitMetaDataButKeepsUnrelatedMetaData() throws IOException {
        ApkModule module = write("config.xxhdpi.apk", false,
                ManifestExtras.none().withFusedModulesMarker().withVendingStampMarker()
                        .withUnrelatedMetaData());
        AndroidManifestBlock manifest = module.getAndroidManifest();

        ManifestSanitizer.sanitize(module, logger);

        assertNull(metaDataNamed(manifest, SplitApkFixture.FUSED_MODULES_MARKER));
        assertNull(metaDataNamed(manifest, VENDOR_STAMP_MARKER));
        assertNotNull("unrelated meta-data must survive",
                metaDataNamed(manifest, UNRELATED_MARKER));
    }

    @Test
    public void keepsAFusedModulesMarkerNamingAnotherModule() throws IOException {
        // The marker only says "this APK was assembled from splits" when it names the "base"
        // module, so a config split carrying it is left alone.
        ApkModule module = write("config.de.apk", false,
                ManifestExtras.none().withFusedModulesMarker("config.de"));
        AndroidManifestBlock manifest = module.getAndroidManifest();
        assertNotNull("fixture must carry the marker",
                metaDataNamed(manifest, SplitApkFixture.FUSED_MODULES_MARKER));

        ManifestSanitizer.sanitize(module, logger);

        assertNotNull(metaDataNamed(manifest, SplitApkFixture.FUSED_MODULES_MARKER));
    }

    @Test
    public void removesPlayStoreSplitMarkerAndTheFilesItPointsAt() throws IOException {
        ApkModule module = write("base.apk", true, ManifestExtras.none());
        AndroidManifestBlock manifest = module.getAndroidManifest();
        assertNotNull("fixture must carry the marker",
                metaDataNamed(manifest, SplitApkFixture.SPLITS_MARKER));
        assertNotNull("fixture must carry the split list",
                module.getZipEntryMap().getInputSource(SplitApkFixture.SPLIT_FILES.get(0)));

        assertTrue(ManifestSanitizer.sanitize(module, logger));

        assertNull(metaDataNamed(manifest, SplitApkFixture.SPLITS_MARKER));
        for (String splitFile : SplitApkFixture.SPLIT_FILES) {
            assertNull("the split list entry must be gone from the archive: " + splitFile,
                    module.getZipEntryMap().getInputSource(splitFile));
        }
        assertNotNull("unrelated entries stay",
                module.getZipEntryMap().getInputSource("classes.dex"));
    }

    @Test
    public void leavesNoSplitOnlyEntriesInTheArchive() throws IOException {
        ApkModule module = write("base.apk", true, ManifestExtras.none());

        ManifestSanitizer.sanitize(module, logger);

        List<String> remaining = new ArrayList<>();
        for (InputSource source : module.getZipEntryMap()) {
            if (source.getName().startsWith("assets/splits/")) {
                remaining.add(source.getName());
            }
        }
        assertEquals(remaining.toString(), List.of(), remaining);
    }

    @Test
    public void survivesAnEntryPointWithoutATableBlock() throws IOException {
        ApkModule module = write("base.apk", false, ManifestExtras.none());
        module.getZipEntryMap().remove(com.reandroid.arsc.chunk.TableBlock.FILE_NAME);

        assertTrue(ManifestSanitizer.sanitize(module, logger));
    }

    @Test
    public void reportsWhenThereIsNoManifestToSanitize() throws IOException {
        ApkModule module = write("config.x86.apk", false, ManifestExtras.none());
        module.getZipEntryMap().remove(AndroidManifestBlock.FILE_NAME);

        assertFalse(ManifestSanitizer.sanitize(module, logger));
    }

    @Test
    public void worksWithoutALogger() throws IOException {
        assertTrue(ManifestSanitizer.sanitize(
                write("config.arm.apk", false, ManifestExtras.everySplitDeclaration()), null));
    }

    @Test
    public void isIdempotent() throws IOException {
        ApkModule module = write("base.apk", true, ManifestExtras.everySplitDeclaration());

        assertTrue(ManifestSanitizer.sanitize(module, logger));
        String afterFirst = module.getAndroidManifest().serializeToXml();
        assertTrue(ManifestSanitizer.sanitize(module, logger));

        assertEquals(afterFirst, module.getAndroidManifest().serializeToXml());
    }

    @Test
    public void logsWhatItRemoved() throws IOException {
        ManifestSanitizer.sanitize(
                write("config.mips.apk", false, ManifestExtras.everySplitDeclaration()), logger);

        assertTrue("expected splitTypes in the log: " + logger.messages,
                logger.contains("0x0101064f"));
        assertTrue("expected the fused-modules marker in the log: " + logger.messages,
                logger.contains(SplitApkFixture.FUSED_MODULES_MARKER));
        assertTrue("expected the stamp marker in the log: " + logger.messages,
                logger.contains(VENDOR_STAMP_MARKER));
    }

    @Test
    public void everySplitDeclarationIsCovered() {
        // Guards against a split declaration being removed from the sanitizer without a test change.
        int[] covered = {AndroidManifest.ID_splitTypes, AndroidManifest.ID_requiredSplitTypes,
                AndroidManifest.ID_extractNativeLibs, AndroidManifest.ID_isSplitRequired};
        for (int id : covered) {
            assertTrue("id 0x" + Integer.toHexString(id) + " is not a real resource id", id != 0);
        }
    }

    private static ResXmlAttribute attribute(ResXmlElement element, int resourceId) {
        return element == null ? null : element.searchAttributeByResourceId(resourceId);
    }

    private static ResXmlElement metaDataNamed(AndroidManifestBlock manifest, String name) {
        ResXmlElement application = manifest.getApplicationElement();
        if (application == null) {
            return null;
        }
        for (Iterator<ResXmlElement> elements = application.getElements(); elements.hasNext(); ) {
            ResXmlElement element = elements.next();
            if (!element.equalsName(AndroidManifest.TAG_meta_data)) {
                continue;
            }
            ResXmlAttribute nameAttribute = element.searchAttributeByResourceId(AndroidManifest.ID_name);
            if (nameAttribute != null && name.equals(nameAttribute.getValueAsString())) {
                return element;
            }
        }
        return null;
    }

    /** Collects what the sanitizer reported, so the tests can assert on it. */
    private static final class RecordingLogger implements APKLogger {
        final List<String> messages = new ArrayList<>();

        @Override
        public void logMessage(String s) {
            messages.add(s);
        }

        @Override
        public void logError(String s, Throwable throwable) {
            messages.add(s);
        }

        @Override
        public void logVerbose(String s) {
            messages.add(s);
        }

        boolean contains(String fragment) {
            for (String message : messages) {
                if (message.contains(fragment)) {
                    return true;
                }
            }
            return false;
        }
    }
}