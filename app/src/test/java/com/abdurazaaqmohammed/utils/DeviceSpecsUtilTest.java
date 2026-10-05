package com.abdurazaaqmohammed.utils;

import static com.abdurazaaqmohammed.testing.SplitApkFixture.entryNamesIn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.abdurazaaqmohammed.testing.RecordingLogger;
import com.abdurazaaqmohammed.testing.SplitApkFixture;
import com.reandroid.archive.ArchiveFile;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which splits a device can actually run, and how the container is read.
 */
@RunWith(RobolectricTestRunner.class)
public class DeviceSpecsUtilTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Context context;
    private RecordingLogger logger;
    /** Where the container is assembled. */
    private File directory;
    /** Where the individual splits are written before being packed. */
    private File parts;

    @Before
    public void setUp() throws IOException {
        context = ApplicationProvider.getApplicationContext();
        logger = new RecordingLogger();
        directory = temporaryFolder.newFolder("splits");
        parts = temporaryFolder.newFolder("parts");
    }

    private DeviceSpecsUtil deviceSpecs() {
        return new DeviceSpecsUtil(context, logger);
    }

    private List<SplitApkFixture.Split> writeSplits(String... names) throws IOException {
        List<SplitApkFixture.Split> splits = new ArrayList<>();
        for (String name : names) {
            splits.add(SplitApkFixture.writeSplitApk(parts, name, false));
        }
        return splits;
    }

    @Test
    public void classifiesBaseApk() {
        assertTrue(DeviceSpecsUtil.isBaseApk("base.apk"));
        assertTrue(DeviceSpecsUtil.isBaseApk("whatever.apk"));
        assertTrue("a split renamed to its package is still the base",
                DeviceSpecsUtil.isBaseApk("com.example.splitted.apk"));
        org.junit.Assert.assertFalse(DeviceSpecsUtil.isBaseApk("config.en.apk"));
        org.junit.Assert.assertFalse(DeviceSpecsUtil.isBaseApk("split_config.apk"));
    }

    @Test
    public void classifiesAbis() {
        for (String name : new String[]{"config.arm64_v8a.apk", "config.armeabi-v7a.apk",
                "config.x86.apk", "config.x86_64.apk", "config.mips.apk"}) {
            assertTrue(name, DeviceSpecsUtil.isArch(name));
        }
        org.junit.Assert.assertFalse(DeviceSpecsUtil.isArch("config.xxhdpi.apk"));
        org.junit.Assert.assertFalse(DeviceSpecsUtil.isArch("base.apk"));
    }

    @Test
    public void mapsEveryDensityBucket() {
        assertEquals("ldpi", DeviceSpecsUtil.densityTypeOf(120));
        assertEquals("mdpi", DeviceSpecsUtil.densityTypeOf(160));
        assertEquals("mdpi", DeviceSpecsUtil.densityTypeOf(140));
        assertEquals("hdpi", DeviceSpecsUtil.densityTypeOf(240));
        assertEquals("hdpi", DeviceSpecsUtil.densityTypeOf(180));
        assertEquals("hdpi", DeviceSpecsUtil.densityTypeOf(200));
        assertEquals("hdpi", DeviceSpecsUtil.densityTypeOf(220));
        assertEquals("xhdpi", DeviceSpecsUtil.densityTypeOf(320));
        assertEquals("xhdpi", DeviceSpecsUtil.densityTypeOf(260));
        assertEquals("xhdpi", DeviceSpecsUtil.densityTypeOf(280));
        assertEquals("xhdpi", DeviceSpecsUtil.densityTypeOf(300));
        assertEquals("xxhdpi", DeviceSpecsUtil.densityTypeOf(480));
        assertEquals("xxhdpi", DeviceSpecsUtil.densityTypeOf(340));
        assertEquals("xxhdpi", DeviceSpecsUtil.densityTypeOf(360));
        assertEquals("xxhdpi", DeviceSpecsUtil.densityTypeOf(390));
        assertEquals("xxhdpi", DeviceSpecsUtil.densityTypeOf(400));
        assertEquals("xxhdpi", DeviceSpecsUtil.densityTypeOf(420));
        assertEquals("xxhdpi", DeviceSpecsUtil.densityTypeOf(440));
        assertEquals("xxhdpi", DeviceSpecsUtil.densityTypeOf(450));
        assertEquals("xxxhdpi", DeviceSpecsUtil.densityTypeOf(640));
        assertEquals("xxxhdpi", DeviceSpecsUtil.densityTypeOf(520));
        assertEquals("xxxhdpi", DeviceSpecsUtil.densityTypeOf(560));
        assertEquals("xxxhdpi", DeviceSpecsUtil.densityTypeOf(600));
        assertEquals("tvdpi", DeviceSpecsUtil.densityTypeOf(213));
        assertEquals("an unknown density falls back to hdpi", "hdpi",
                DeviceSpecsUtil.densityTypeOf(1));
    }

    @Test
    public void densityNameIsRememberedBetweenRuns() {
        DeviceSpecsUtil specs = deviceSpecs();
        String first = specs.getDeviceDpi();

        assertTrue(first, first.endsWith(".apk"));
        assertEquals("the value is cached in shared preferences",
                first, deviceSpecs().getDeviceDpi());
    }

    @Test
    public void densityMatchIgnoresBucketsThatMerelyShareAPrefix() {
        context.getSharedPreferences("set", Context.MODE_PRIVATE).edit()
                .putString("deviceDpi", "xhdpi.apk").commit();
        DeviceSpecsUtil specs = deviceSpecs();

        assertTrue(specs.shouldIncludeDpi("config.xhdpi.apk"));
        org.junit.Assert.assertFalse("xxhdpi must not satisfy an xhdpi device",
                specs.shouldIncludeDpi("config.xxhdpi.apk"));
        org.junit.Assert.assertFalse("xxxhdpi must not satisfy an xhdpi device",
                specs.shouldIncludeDpi("config.xxxhdpi.apk"));
        org.junit.Assert.assertFalse(specs.shouldIncludeDpi("config.mdpi.apk"));
    }

    @Test
    public void listsOnlyApkEntriesOfAContainer() throws IOException {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("icon.png", "not-an-apk");
        extras.put("AndroidManifest.xml", "<manifest/>");
        File container = SplitApkFixture.writeContainer(directory, "app.xapk",
                writeSplits("base.apk", "config.en.apk"), extras);

        List<String> names = deviceSpecs().getListOfSplits(Uri.fromFile(container));

        assertEquals(List.of("base.apk", "config.en.apk"), names);
    }

    @Test
    public void doesNotTrimAContainerWithoutConfigSplits() throws IOException {
        // Merging two or fewer APKs is cheaper whole, and nothing is left over anyway.
        for (String[] names : new String[][]{{"base.apk"}, {"base.apk", "config.en.apk"}}) {
            File container = SplitApkFixture.writeContainer(directory, "app.zip",
                    writeSplits(names));
            DeviceSpecsUtil specs = deviceSpecs();

            List<String> toRemove = specs.getSplitsForDevice(Uri.fromFile(container));

            assertEquals(String.join("+", names) + " must not be trimmed",
                    List.of(), toRemove);
        }
    }

    @Test
    public void keepsOnlyWhatTheDeviceNeeds() throws IOException {
        File container = SplitApkFixture.writeContainer(directory, "app.xapk", writeSplits(
                "base.apk", "config.en.apk", "config.de.apk", "config.xxhdpi.apk",
                "config.mdpi.apk"));
        context.getSharedPreferences("set", Context.MODE_PRIVATE).edit()
                .putString("deviceDpi", "xxhdpi.apk").commit();

        List<String> toRemove = deviceSpecs().getSplitsForDevice(Uri.fromFile(container));

        // The returned list is what gets left out of the merge.
        assertEquals("only the splits this device cannot use are left out",
                List.of("config.de.apk", "config.mdpi.apk"), toRemove);
    }

    @Test
    public void fallsBackToTheDensestSplitWhenNoneMatchesTheDevice() throws IOException {
        // Dropping every density split would leave the app unable to draw at all.
        File container = SplitApkFixture.writeContainer(directory, "app.xapk", writeSplits(
                "base.apk", "config.en.apk", "config.hdpi.apk", "config.x86.apk"));
        context.getSharedPreferences("set", Context.MODE_PRIVATE).edit()
                .putString("deviceDpi", "tvdpi.apk").commit();

        List<String> toRemove = deviceSpecs().getSplitsForDevice(Uri.fromFile(container));

        org.junit.Assert.assertFalse("the fallback density must be kept: " + toRemove,
                toRemove.contains("config.hdpi.apk"));
    }

    @Test
    public void fallsBackToEveryAbiWhenTheDeviceHasNone() throws IOException {
        // Keeping only the language split would produce an APK the device cannot run.
        File container = SplitApkFixture.writeContainer(directory, "app.xapk", writeSplits(
                "base.apk", "config.en.apk", "config.arm64_v8a.apk", "config.x86.apk"));

        List<String> toRemove = deviceSpecs().getSplitsForDevice(Uri.fromFile(container));

        org.junit.Assert.assertFalse("no ABI split may be dropped: " + toRemove,
                toRemove.contains("config.arm64_v8a.apk"));
        org.junit.Assert.assertFalse(toRemove.contains("config.x86.apk"));
        assertTrue(logger.contains("Could not find device architecture"));
    }

    @Test
    public void keepsEveryDensitySplitWhenNoneIsExactlyRight() throws IOException {
        File container = SplitApkFixture.writeContainer(directory, "app.xapk", writeSplits(
                "base.apk", "config.en.apk", "config.xxxhdpi.apk", "config.xxhdpi.apk"));
        context.getSharedPreferences("set", Context.MODE_PRIVATE).edit()
                .putString("deviceDpi", "tvdpi.apk").commit();

        List<String> toRemove = deviceSpecs().getSplitsForDevice(Uri.fromFile(container));

        assertEquals("no density split may be dropped when none matches exactly",
                List.of(), toRemove);
    }

    @Test
    public void handsTheOpenContainerOverExactlyOnce() throws IOException {
        File container = SplitApkFixture.writeContainer(directory, "app.xapk",
                writeSplits("base.apk", "config.en.apk", "config.xxhdpi.apk"));
        DeviceSpecsUtil specs = deviceSpecs();

        specs.getListOfSplits(Uri.fromFile(container));

        ArchiveFile taken = specs.takeContainer();
        assertNotNull("the container should stay open for the merger to reuse", taken);
        assertNull("a second handover would re-read the container", specs.takeContainer());
        taken.close();
    }

    @Test
    public void closingAnUnusedContainerIsSafe() throws IOException {
        File container = SplitApkFixture.writeContainer(directory, "app.xapk",
                writeSplits("base.apk", "config.en.apk"));
        DeviceSpecsUtil specs = deviceSpecs();
        specs.getListOfSplits(Uri.fromFile(container));

        specs.closeContainer();
        specs.closeContainer();

        assertNull(specs.takeContainer());
    }

    @Test
    public void reportsACorruptContainer() throws IOException {
        File corrupt = new File(directory, "broken.xapk");
        try (java.io.OutputStream out = new java.io.FileOutputStream(corrupt)) {
            out.write("this is not a zip file at all, not even close".getBytes());
        }

        assertThrows(IOException.class,
                () -> deviceSpecs().getListOfSplits(Uri.fromFile(corrupt)));
    }

    @Test
    public void keepsTheContainerEntriesIntactForTheMerger() throws IOException {
        File container = SplitApkFixture.writeContainer(directory, "app.xapk",
                writeSplits("base.apk", "config.en.apk"));

        assertEquals(List.of("base.apk", "config.en.apk"), entryNamesIn(container));
    }

    @Test
    public void worksWithoutALogger() throws IOException {
        File container = SplitApkFixture.writeContainer(directory, "app.xapk", writeSplits(
                "base.apk", "config.en.apk", "config.arm64_v8a.apk", "config.x86.apk"));

        List<String> toRemove =
                new DeviceSpecsUtil(context, null).getSplitsForDevice(Uri.fromFile(container));

        org.junit.Assert.assertFalse(toRemove.contains("config.x86.apk"));
    }
}