package fork.MaxrregMustermann.AntiSplitNG.merge;

import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;

import java.io.File;
import java.io.IOException;

/**
 * Merges a loaded bundle of split APKs into one unsigned APK.
 *
 * <p>{@link ManifestSanitizer} runs on the merged module so the result no longer advertises itself
 * as a split. Signing happens after this, in {@link Merger}, because the signing key comes from the
 * user.
 *
 * <p>Deliberately free of Android types so the merge itself can be tested on its own.
 */
public final class ApkMerger {

    private ApkMerger() {
    }

    /**
     * Merges {@code bundle} and writes the result into {@code outputDirectory}.
     *
     * @param force keep going when a resource collides instead of failing
     * @return the merged, still unsigned APK
     */
    public static File merge(ApkBundle bundle, File outputDirectory, boolean force, APKLogger logger)
            throws IOException {
        try (ApkModule merged = bundle.mergeModules(!force)) {
            ManifestSanitizer.sanitize(merged, logger);
            File output = new File(outputDirectory, "merged_" + System.currentTimeMillis() + ".apk");
            merged.writeApk(output);
            return output;
        }
    }
}