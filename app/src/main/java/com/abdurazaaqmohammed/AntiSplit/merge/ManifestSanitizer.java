package com.abdurazaaqmohammed.AntiSplit.merge;

import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.apk.ApkUtil;
import com.reandroid.app.AndroidManifest;
import com.reandroid.archive.ZipEntryMap;
import com.reandroid.arsc.array.ResValueMapArray;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.container.SpecTypePair;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResValue;
import com.reandroid.arsc.value.ResValueMap;
import com.reandroid.arsc.value.ValueType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Strips the split-APK bookkeeping out of a merged module's manifest and resource table.
 *
 * <p>A merged APK that still advertises itself as a split - or that still points at the Play
 * Store's "which splits do I need" metadata - fails to install on some devices. Everything this
 * class removes here is only meaningful while the APK is a split, so it goes once the splits are
 * merged in.
 */
public final class ManifestSanitizer {

    private ManifestSanitizer() {
    }

    /**
     * Removes split declarations from {@code module} in place.
     *
     * @param module the merged module to sanitize
     * @param logger progress sink, may be {@code null}
     * @return {@code true} when a manifest was found and sanitized
     */
    public static boolean sanitize(ApkModule module, APKLogger logger) {
        if (!module.hasAndroidManifest()) {
            return false;
        }
        AndroidManifestBlock manifest = module.getAndroidManifest();
        log(logger, "Sanitizing manifest");

        // Attributes only a split APK needs; harmless on a merged one.
        removeById(manifest, AndroidManifest.ID_requiredSplitTypes, logger);
        removeById(manifest, AndroidManifest.ID_splitTypes, logger);
        removeByName(manifest, AndroidManifest.NAME_splitTypes, logger);
        removeByName(manifest, AndroidManifest.NAME_requiredSplitTypes, logger);
        // The merged APK no longer loads native code from split directories, and a merged
        // APK is never itself required by a parent split.
        removeFromManifestAndApplication(manifest, AndroidManifest.ID_extractNativeLibs, logger,
                AndroidManifest.NAME_extractNativeLibs);
        removeFromManifestAndApplication(manifest, AndroidManifest.ID_isSplitRequired, logger,
                AndroidManifest.NAME_isSplitRequired);

        ResXmlElement application = manifest.getApplicationElement();
        for (ResXmlElement metaData : listSplitMetadata(application)) {
            log(logger, "Removed element: <" + metaData.getName() + "> name=\""
                    + AndroidManifestBlock.getAndroidNameValue(metaData) + "\"");
            if (referencesPlayStoreSplitList(metaData)) {
                removePlayStoreTableEntry(module, metaData, logger);
            }
            application.remove(metaData);
        }

        manifest.refresh();
        return true;
    }

    /**
     * The {@code <meta-data>} elements a split APK uses to point at its required splits: the
     * Play Store's dynamic-module markers and the vendor stamp markers.
     */
    private static List<ResXmlElement> listSplitMetadata(ResXmlElement application) {
        List<ResXmlElement> result = new ArrayList<>();
        if (application == null) {
            return result;
        }
        for (Iterator<ResXmlElement> elements = application.getElements(); elements.hasNext(); ) {
            ResXmlElement element = elements.next();
            if (!element.equalsName(AndroidManifest.TAG_meta_data)) {
                continue;
            }
            ResXmlAttribute name = element.searchAttributeByResourceId(AndroidManifest.ID_name);
            if (name == null || name.getValueType() != ValueType.STRING) {
                continue;
            }
            String value = name.getValueAsString();
            if (value == null) {
                continue;
            }
            if (isFusedModuleMarker(element, value)
                    || value.startsWith("com.android.vending.")
                    || value.startsWith("com.android.stamp.")) {
                result.add(element);
            }
        }
        return result;
    }

    private static boolean isFusedModuleMarker(ResXmlElement element, String name) {
        if (!"com.android.dynamic.apk.fused.modules".equals(name)) {
            return false;
        }
        ResXmlAttribute value = element.searchAttributeByResourceId(AndroidManifest.ID_value);
        return value != null && ApkUtil.DEF_MODULE_NAME.equals(value.getValueAsString());
    }

    /** Whether this element is the Play Store's "these are my splits" pointer. */
    private static boolean referencesPlayStoreSplitList(ResXmlElement metaData) {
        ResXmlAttribute name = metaData.searchAttributeByResourceId(AndroidManifest.ID_name);
        if (name == null || !"com.android.vending.splits".equals(name.getValueAsString())) {
            return false;
        }
        ResXmlAttribute value = findSplitListReference(metaData);
        return value != null && value.getValueType() == ValueType.REFERENCE;
    }

    private static ResXmlAttribute findSplitListReference(ResXmlElement metaData) {
        ResXmlAttribute value = metaData.searchAttributeByResourceId(AndroidManifest.ID_value);
        return value != null ? value
                : metaData.searchAttributeByResourceId(AndroidManifest.ID_resource);
    }

    /**
     * Drops the file list the Play Store's split metadata points at, both from the archive and
     * from the resource table. The table entry is nulled rather than destroyed because its id may
     * still be referenced from dex code.
     */
    private static void removePlayStoreTableEntry(ApkModule module, ResXmlElement metaData,
            APKLogger logger) {
        if (!module.hasTableBlock()) {
            return;
        }
        ResXmlAttribute reference = findSplitListReference(metaData);
        ResourceEntry resourceEntry = module.getTableBlock().getResource(reference.getData());
        if (resourceEntry == null) {
            return;
        }
        ZipEntryMap zipEntryMap = module.getZipEntryMap();
        for (Entry entry : resourceEntry) {
            if (entry == null) {
                continue;
            }
            for (String path : splitFilesOf(entry)) {
                log(logger, "Removed table entry: " + path);
                zipEntryMap.remove(path);
            }
            entry.setNull(true);
            SpecTypePair specTypePair = entry.getTypeBlock().getParentSpecTypePair();
            specTypePair.removeNullEntries(entry.getId());
        }
    }

    /**
     * The paths one entry of the split list points at. Bundletool writes the list as a string
     * array, but a hand-built APK may hold a single string instead.
     */
    private static List<String> splitFilesOf(Entry entry) {
        List<String> paths = new ArrayList<>();
        ResValue value = entry.getResValue();
        if (value != null && value.getValueAsString() != null) {
            paths.add(value.getValueAsString());
        }
        ResValueMapArray items = entry.getResValueMapArray();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                ResValueMap item = items.get(i);
                String path = item == null ? null : item.getValueAsString();
                if (path != null) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    private static void removeById(AndroidManifestBlock manifest, int resourceId, APKLogger logger) {
        ResXmlElement manifestElement = manifest.getManifestElement();
        if (manifestElement == null) {
            log(logger, "WARN: AndroidManifest has no <manifest> element");
            return;
        }
        if (manifestElement.removeAttributesWithId(resourceId)) {
            log(logger, "Removed attribute id=" + hex(resourceId));
        }
    }

    private static void removeByName(AndroidManifestBlock manifest, String name, APKLogger logger) {
        ResXmlElement manifestElement = manifest.getManifestElement();
        if (manifestElement == null) {
            log(logger, "WARN: AndroidManifest has no <manifest> element");
            return;
        }
        if (manifestElement.removeAttributesWithName(name)) {
            log(logger, "Removed attribute name=" + name);
        }
    }

    private static void removeFromManifestAndApplication(AndroidManifestBlock manifest,
            int resourceId, APKLogger logger, String nameForLogging) {
        if (resourceId == 0) {
            return;
        }
        ResXmlElement manifestElement = manifest.getManifestElement();
        if (manifestElement == null) {
            log(logger, "WARN: AndroidManifest has no <manifest> element");
            return;
        }
        if (manifestElement.removeAttributesWithId(resourceId)) {
            log(logger, "Removed from <manifest> id=" + hex(resourceId) + " (" + nameForLogging + ")");
        }
        ResXmlElement application = manifestElement.getElement(AndroidManifest.TAG_application);
        if (application != null && application.removeAttributesWithId(resourceId)) {
            log(logger, "Removed from <application> id=" + hex(resourceId) + " (" + nameForLogging + ")");
        }
    }

    /** Resource ids read better zero-padded, as they are written in the platform sources. */
    private static String hex(int resourceId) {
        return String.format("0x%08x", resourceId);
    }

    private static void log(APKLogger logger, String message) {
        if (logger != null) {
            logger.logMessage(message);
        }
    }
}