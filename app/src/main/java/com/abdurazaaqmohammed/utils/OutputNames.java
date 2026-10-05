package com.abdurazaaqmohammed.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the file name a merged APK should be saved under.
 *
 * <p>A split APK container arrives as {@code .zip}, {@code .xapk}, {@code .apkm}, {@code .apks} or
 * {@code .apm}, and a bare split as {@code .apk}. The merged result is always an {@code .apk}, so
 * whatever extension the input carried has to give way to it.
 */
public final class OutputNames {

    /** Extensions a split APK container can carry, anchored so only a trailing one is replaced. */
    private static final Pattern CONTAINER_EXTENSION =
            Pattern.compile("\\.(zip|xapk|aspk|apk[sm])$");
    /** The same, unanchored, for a path that may carry more after the extension. */
    private static final Pattern CONTAINER_EXTENSION_IN_PATH = Pattern.compile("\\.(zip|xapk|aspk|apk[sm])");
    private static final Pattern APK_EXTENSION = Pattern.compile("\\.apk$");

    private OutputNames() {
    }

    /**
     * Names the merged APK after the container it came from.
     *
     * @param original the name of the file that was opened
     * @param suffix the user's configured file name suffix
     */
    public static String merged(String original, String suffix) {
        return CONTAINER_EXTENSION.matcher(original)
                .replaceFirst(replacement(suffix) + ".apk");
    }

    /**
     * The same, for a full path, where the extension is followed by more of the path.
     *
     * @param path the path of the container that was opened
     */
    public static String mergedInPath(String path, String suffix) {
        return CONTAINER_EXTENSION_IN_PATH.matcher(path)
                .replaceFirst(replacement(suffix) + ".apk");
    }

    /**
     * Names the merged APK after an already extracted {@code .apk}, which is what a set of loose
     * splits looks like once it has been copied out of its container.
     */
    public static String mergedFromApk(String name, String suffix) {
        return APK_EXTENSION.matcher(name).replaceFirst(replacement(suffix) + ".apk");
    }

    /** Strips a trailing {@code .apk}, leaving the bare app name. */
    public static String withoutExtension(String name) {
        return APK_EXTENSION.matcher(name).replaceFirst("");
    }

    /** The name used when nothing in the selected files identifies the app. */
    public static String unknown(String suffix) {
        return "unknown" + suffix + ".apk";
    }

    /**
     * The suffix comes from a user-editable field and may contain {@code $} or {@code \}, which
     * {@code replaceFirst} would otherwise read as a group reference or an escape.
     */
    private static String replacement(String suffix) {
        return Matcher.quoteReplacement(suffix);
    }
}