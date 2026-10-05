package fork.MaxrregMustermann.AntiSplitNG.merge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import fork.MaxrregMustermann.AntiSplitNG.testing.RecordingLogger;
import fork.MaxrregMustermann.AntiSplitNG.testing.SplitApkFixture;
import fork.MaxrregMustermann.AntiSplitNG.testing.SplitApkFixture.ManifestExtras;
import com.reandroid.apk.ApkBundle;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Merging a set of splits into one installable APK.
 *
 * <p>Runs against real {@link ApkBundle}s built by ARSCLib, so this covers the whole path: loading
 * the modules, merging them, sanitizing the manifest and writing the archive back out.
 */
@RunWith(RobolectricTestRunner.class)
public class ApkMergerTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File parts;
    private File outputDirectory;
    private RecordingLogger logger;

    @Before
    public void setUp() throws IOException {
        parts = temporaryFolder.newFolder("parts");
        outputDirectory = temporaryFolder.newFolder("out");
        logger = new RecordingLogger();
    }

    private ApkBundle bundleOf(String... names) throws IOException {
        ApkBundle bundle = new ApkBundle();
        for (String name : names) {
            boolean base = name.equals("base.apk");
            SplitApkFixture.writeSplitApk(parts, name, base,
                    base ? ManifestExtras.everySplitDeclaration() : ManifestExtras.none());
        }
        bundle.loadApkDirectory(parts);
        return bundle;
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

    private static List<String> entryNames(ApkModule module) {
        List<String> names = new ArrayList<>();
        for (InputSource source : module.getZipEntryMap()) {
            names.add(source.getName());
        }
        java.util.Collections.sort(names);
        return names;
    }

    @Test
    public void mergesASplitSetIntoOneApk() throws Exception {
        try (ApkBundle bundle = bundleOf("base.apk", "config.arm64_v8a.apk", "config.en.apk")) {
            File merged = ApkMerger.merge(bundle, outputDirectory, false, logger);

            assertTrue(merged.isFile());
            assertTrue(merged.getName(), merged.getName().startsWith("merged_"));
            try (ApkModule reloaded = ApkModule.loadApkFile(merged)) {
                assertTrue(reloaded.hasAndroidManifest());
                assertNotNull(reloaded.getZipEntryMap().getInputSource("classes.dex"));
                assertEquals(SplitApkFixture.PACKAGE_NAME,
                        reloaded.getAndroidManifest().getPackageName());
            }
        }
    }

    @Test
    public void theMergedApkCarriesNoSplitMetadata() throws Exception {
        try (ApkBundle bundle = bundleOf("base.apk", "config.en.apk")) {
            File merged = ApkMerger.merge(bundle, outputDirectory, false, logger);

            try (ApkModule reloaded = ApkModule.loadApkFile(merged)) {
                AndroidManifestBlock manifest = reloaded.getAndroidManifest();
                assertNull("splitTypes must be gone",
                        manifest.getManifestElement()
                                .searchAttributeByResourceId(AndroidManifest.ID_splitTypes));
                assertNull("requiredSplitTypes must be gone",
                        manifest.getManifestElement()
                                .searchAttributeByResourceId(AndroidManifest.ID_requiredSplitTypes));
                assertNull("extractNativeLibs must be gone",
                        manifest.getManifestElement()
                                .searchAttributeByResourceId(AndroidManifest.ID_extractNativeLibs));
                assertNull("isSplitRequired must be gone",
                        manifest.getManifestElement()
                                .searchAttributeByResourceId(AndroidManifest.ID_isSplitRequired));
                assertNull(metaDataNamed(manifest, SplitApkFixture.FUSED_MODULES_MARKER));
            }
        }
    }

    @Test
    public void theMergedApkIsNoLongerSplitSpecific() throws Exception {
        try (ApkBundle bundle = bundleOf("base.apk", "config.xxhdpi.apk")) {
            File merged = ApkMerger.merge(bundle, outputDirectory, false, logger);

            try (ApkModule reloaded = ApkModule.loadApkFile(merged)) {
                // A merged APK must not carry any config split's identity.
                assertNull("no split name may survive",
                        reloaded.getAndroidManifest().getManifestElement()
                                .searchAttributeByResourceId(SplitApkFixture.ID_SPLIT));
            }
        }
    }

    @Test
    public void findsEverySplitItWasGiven() throws Exception {
        try (ApkBundle bundle = bundleOf("base.apk", "config.en.apk", "config.de.apk")) {
            assertEquals(3, bundle.getApkModuleList().size());

            ApkMerger.merge(bundle, outputDirectory, false, logger);
        }

        assertTrue("the sanitizer must have reported on the manifest: " + logger.messages(),
                logger.contains("Sanitizing manifest"));
    }

    @Test
    public void refusesAnEmptyBundle() throws IOException {
        try (ApkBundle bundle = new ApkBundle()) {
            assertThrows(FileNotFoundException.class,
                    () -> ApkMerger.merge(bundle, outputDirectory, false, logger));
        }
    }

    @Test
    public void forceOnlyChangesHowCollisionsAreReported() throws Exception {
        // Both modes must produce a readable APK; force is what decides whether a resource that
        // exists in two splits is an error.
        for (boolean force : new boolean[]{false, true}) {
            File target = temporaryFolder.newFolder("out-" + force);
            try (ApkBundle bundle = bundleOf("base.apk", "config.en.apk")) {
                File merged = ApkMerger.merge(bundle, target, force, logger);

                try (ApkModule reloaded = ApkModule.loadApkFile(merged)) {
                    assertTrue("force=" + force, reloaded.hasAndroidManifest());
                }
            }
        }
    }

    @Test
    public void theOutputIsNotSigned() throws Exception {
        try (ApkBundle bundle = bundleOf("base.apk", "config.en.apk")) {
            File merged = ApkMerger.merge(bundle, outputDirectory, false, logger);

            try (ApkModule reloaded = ApkModule.loadApkFile(merged)) {
                assertFalse("merging alone must not leave signature entries behind",
                        entryNames(reloaded).stream()
                                .anyMatch(name -> name.startsWith("META-INF/")));
            }
        }
    }

    @Test
    public void everySplitSurvivesIntoTheMergedArchive() throws Exception {
        try (ApkBundle bundle = bundleOf("base.apk", "config.arm64_v8a.apk", "config.x86.apk",
                "config.en.apk", "config.de.apk", "config.xxhdpi.apk")) {
            File merged = ApkMerger.merge(bundle, outputDirectory, false, logger);

            try (ApkModule reloaded = ApkModule.loadApkFile(merged)) {
                List<String> names = entryNames(reloaded);
                assertTrue(names.toString(), names.contains("AndroidManifest.xml"));
                assertTrue(names.toString(), names.contains("classes.dex"));
            }
        }
    }
}