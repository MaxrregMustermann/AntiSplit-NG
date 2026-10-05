package com.abdurazaaqmohammed.testing;

import com.reandroid.apk.ApkModule;
import com.reandroid.app.AndroidManifest;
import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.ByteInputSource;
import com.reandroid.archive.InputSource;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResTableMapEntry;
import com.reandroid.arsc.value.ResValueMap;
import com.reandroid.arsc.value.ValueType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the split APKs and containers the merger tests run against.
 *
 * <p>The splits are real {@link ApkModule}s written by ARSCLib, so the tests exercise the same
 * parsing, merging and rewriting code the app runs on a device.
 */
public final class SplitApkFixture {

    public static final String PACKAGE_NAME = "com.example.splitted";
    /** Marks an APK as the fused result of a dynamic-app split delivery. */
    public static final String FUSED_MODULES_MARKER = "com.android.dynamic.apk.fused.modules";
    /** The Play Store's "these are my splits" pointer. */
    public static final String SPLITS_MARKER = "com.android.vending.splits";
    /** {@code android:split}; ARSCLib's AndroidManifest has no constant for it. */
    public static final int ID_SPLIT = 0x01010472;
    /** Files the Play Store split marker in the base split points at. */
    public static final List<String> SPLIT_FILES =
            List.of("assets/splits/base.apk", "assets/splits/config.arm64_v8a.apk");

    private SplitApkFixture() {
    }

    /** One split APK on disk. */
    public static final class Split {
        public final String name;
        public final boolean base;
        public final File file;

        Split(String name, boolean base, File file) {
            this.name = name;
            this.base = base;
            this.file = file;
        }

        /** The value the manifest advertises in {@code android:split}, or {@code null} for the base. */
        public String manifestSplitName() {
            return base ? null : name.substring("config.".length(), name.length() - ".apk".length());
        }
    }

    /** Extra manifest attributes a test wants on the base split. */
    public static final class ManifestExtras {
        public boolean splitTypesById;
        public boolean splitTypesByName;
        public boolean requiredSplitTypesById;
        public boolean extractNativeLibsOnManifest;
        public boolean extractNativeLibsOnApplication;
        public boolean isSplitRequiredOnManifest;
        public boolean fusedModulesMarker;
        /** The module the fused-modules marker names; only "base" marks this APK as assembled. */
        public String fusedModulesModuleName = "base";
        public boolean vendingStampMarker;
        public boolean unrelatedMetaData;

        public static ManifestExtras none() {
            return new ManifestExtras();
        }

        public static ManifestExtras everySplitDeclaration() {
            return new ManifestExtras()
                    .withSplitTypesById()
                    .withSplitTypesByName()
                    .withRequiredSplitTypesById()
                    .withExtractNativeLibs(true, true)
                    .withIsSplitRequired()
                    .withFusedModulesMarker()
                    .withVendingStampMarker()
                    .withUnrelatedMetaData();
        }

        public ManifestExtras withSplitTypesById() {
            splitTypesById = true;
            return this;
        }

        public ManifestExtras withSplitTypesByName() {
            splitTypesByName = true;
            return this;
        }

        public ManifestExtras withRequiredSplitTypesById() {
            requiredSplitTypesById = true;
            return this;
        }

        public ManifestExtras withExtractNativeLibs(boolean onManifest, boolean onApplication) {
            extractNativeLibsOnManifest = onManifest;
            extractNativeLibsOnApplication = onApplication;
            return this;
        }

        public ManifestExtras withIsSplitRequired() {
            isSplitRequiredOnManifest = true;
            return this;
        }

        public ManifestExtras withFusedModulesMarker() {
            return withFusedModulesMarker("base");
        }

        public ManifestExtras withFusedModulesMarker(String moduleName) {
            fusedModulesMarker = true;
            fusedModulesModuleName = moduleName;
            return this;
        }

        public ManifestExtras withVendingStampMarker() {
            vendingStampMarker = true;
            return this;
        }

        public ManifestExtras withUnrelatedMetaData() {
            unrelatedMetaData = true;
            return this;
        }
    }

    /**
     * Writes one split APK into {@code directory}.
     *
     * @param name the split's file name, for example {@code config.arm64_v8a.apk}
     * @param splitListMarker add the {@code com.android.vending.splits} metadata plus the resource
     *     table and files it points at, the way a Play Store container does
     */
    public static Split writeSplitApk(File directory, String name, boolean splitListMarker)
            throws IOException {
        return writeSplitApk(directory, name, splitListMarker, ManifestExtras.none());
    }

    public static Split writeSplitApk(File directory, String name, boolean splitListMarker,
            ManifestExtras extras) throws IOException {
        File file = new File(directory, name);
        boolean base = name.equals("base.apk");

        ApkModule module = new ApkModule();
        AndroidManifestBlock manifest = new AndroidManifestBlock();
        // Also creates the <manifest> element.
        manifest.setPackageName(PACKAGE_NAME);
        manifest.setVersionCode(1);
        manifest.setVersionName("1.0");
        manifest.setMinSdkVersion(21);
        manifest.setTargetSdkVersion(34);
        module.setManifest(manifest);

        ResXmlElement manifestElement = manifest.getManifestElement();
        if (!base) {
            manifestElement.getOrCreateAndroidAttribute(AndroidManifest.NAME_split, ID_SPLIT)
                    .setValueAsString(
                            name.substring("config.".length(), name.length() - ".apk".length()));
        }
        if (extras.splitTypesById) {
            manifestElement.getOrCreateAndroidAttribute("splitTypes", AndroidManifest.ID_splitTypes)
                    .setValueAsString("density,abi");
        }
        if (extras.splitTypesByName) {
            // A manifest built by hand often names the attribute without giving it an id.
            manifestElement.getOrCreateAttribute("splitTypes", 0).setValueAsString("density,abi");
        }
        if (extras.requiredSplitTypesById) {
            manifestElement.getOrCreateAndroidAttribute("requiredSplitTypes",
                    AndroidManifest.ID_requiredSplitTypes).setValueAsString("abi");
        }
        if (extras.extractNativeLibsOnManifest) {
            manifestElement.getOrCreateAndroidAttribute("extractNativeLibs",
                    AndroidManifest.ID_extractNativeLibs).setValueAsBoolean(true);
        }
        if (extras.isSplitRequiredOnManifest) {
            manifestElement.getOrCreateAndroidAttribute("isSplitRequired",
                    AndroidManifest.ID_isSplitRequired).setValueAsBoolean(true);
        }

        ResXmlElement application = manifest.getOrCreateApplicationElement();
        if (extras.extractNativeLibsOnApplication) {
            application.getOrCreateAndroidAttribute("extractNativeLibs",
                    AndroidManifest.ID_extractNativeLibs).setValueAsBoolean(true);
        }
        if (extras.fusedModulesMarker) {
            ResXmlElement metaData = application.newElement(AndroidManifest.TAG_meta_data);
            metaData.getOrCreateAndroidAttribute("name", AndroidManifest.ID_name)
                    .setValueAsString(FUSED_MODULES_MARKER);
            metaData.getOrCreateAndroidAttribute("value", AndroidManifest.ID_value)
                    .setValueAsString(extras.fusedModulesModuleName);
        }
        if (extras.vendingStampMarker) {
            ResXmlElement metaData = application.newElement(AndroidManifest.TAG_meta_data);
            metaData.getOrCreateAndroidAttribute("name", AndroidManifest.ID_name)
                    .setValueAsString("com.android.stamp.v2");
        }
        if (extras.unrelatedMetaData) {
            ResXmlElement metaData = application.newElement(AndroidManifest.TAG_meta_data);
            metaData.getOrCreateAndroidAttribute("name", AndroidManifest.ID_name)
                    .setValueAsString("com.example.KEEP_ME");
            metaData.getOrCreateAndroidAttribute("value", AndroidManifest.ID_value)
                    .setValueAsString("keep-me-too");
        }
        if (splitListMarker) {
            ResXmlElement metaData = application.newElement(AndroidManifest.TAG_meta_data);
            metaData.getOrCreateAndroidAttribute("name", AndroidManifest.ID_name)
                    .setValueAsString(SPLITS_MARKER);
            ResXmlAttribute resource =
                    metaData.getOrCreateAndroidAttribute("resource", AndroidManifest.ID_resource);
            resource.setTypeAndData(ValueType.REFERENCE, splitListResourceId());
        }
        manifest.refresh();

        if (splitListMarker) {
            SplitList splitList = splitList();
            module.setTableBlock(splitList.table);
            for (String splitFile : SPLIT_FILES) {
                module.add(new ByteInputSource(new byte[]{1, 2, 3}, splitFile));
            }
        }
        module.add(new ByteInputSource(dexBytes(), "classes.dex"));
        module.writeApk(file);
        return new Split(name, base, file);
    }

    /** The resource table whose single entry lists the split files. */
    private static final class SplitList {
        final TableBlock table;
        final Entry entry;

        SplitList(TableBlock table, Entry entry) {
            this.table = table;
            this.entry = entry;
        }
    }

    /** The id of the resource table entry listing the split files. */
    public static int splitListResourceId() {
        return splitList().entry.getResourceId();
    }

    /**
     * A resource table whose only entry lists the split files, matching what a base split carrying a
     * {@code com.android.vending.splits} marker looks like.
     */
    private static SplitList splitList() {
        TableBlock table = new TableBlock();
        PackageBlock packageBlock = table.newPackage(0x7f, PACKAGE_NAME);
        // The split list is not the only array in a real table, and it must not end up as entry 0,
        // which ARSCLib treats as "no resource".
        packageBlock.getOrCreate("", "array", "install_time").setValueAsBoolean(true);
        Entry entry = packageBlock.getOrCreate("", "array", "split_files");
        entry.ensureComplex(true);
        ResTableMapEntry mapEntry = entry.getResTableMapEntry();
        mapEntry.setValuesCount(SPLIT_FILES.size());
        for (int i = 0; i < SPLIT_FILES.size(); i++) {
            ResValueMap item = mapEntry.getValue().get(i);
            item.setArrayIndex(i + 1);
            item.setValueAsString(SPLIT_FILES.get(i));
        }
        mapEntry.refresh();
        table.refreshFull();
        if (entry.getId() == 0) {
            throw new IllegalStateException("split_files entry must not be entry 0");
        }
        return new SplitList(table, entry);
    }

    /** A minimal DEX header; nothing on the merge path parses it. */
    private static byte[] dexBytes() {
        byte[] dex = new byte[112];
        byte[] magic = {'d', 'e', 'x', '\n', '0', '3', '5', 0};
        System.arraycopy(magic, 0, dex, 0, magic.length);
        writeInt(dex, 32, dex.length);
        writeInt(dex, 36, 112);
        writeInt(dex, 40, 0x12345678);
        writeInt(dex, 96, dex.length);
        writeInt(dex, 100, 112);
        return dex;
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >> 8);
        target[offset + 2] = (byte) (value >> 16);
        target[offset + 3] = (byte) (value >> 24);
    }

    /** Packs splits into a zip container, adding any extra entries alongside them. */
    public static File writeContainer(File directory, String containerName, List<Split> splits,
            Map<String, String> extraEntries) throws IOException {
        File container = new File(directory, containerName);
        try (OutputStream out = new FileOutputStream(container);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Split split : splits) {
                zip.putNextEntry(new ZipEntry(split.name));
                zip.write(Files.readAllBytes(split.file.toPath()));
                zip.closeEntry();
            }
            for (Map.Entry<String, String> extra : extraEntries.entrySet()) {
                zip.putNextEntry(new ZipEntry(extra.getKey()));
                zip.write(extra.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return container;
    }

    /** {@link #writeContainer} with no extra entries. */
    public static File writeContainer(File directory, String containerName, List<Split> splits)
            throws IOException {
        return writeContainer(directory, containerName, splits, new LinkedHashMap<>());
    }

    /** Every entry name inside a container, in the order they appear. */
    public static List<String> entryNamesIn(File container) throws IOException {
        List<String> names = new ArrayList<>();
        try (ArchiveFile archive = new ArchiveFile(container)) {
            for (InputSource source : archive.getInputSources()) {
                names.add(source.getName());
            }
        }
        return names;
    }
}