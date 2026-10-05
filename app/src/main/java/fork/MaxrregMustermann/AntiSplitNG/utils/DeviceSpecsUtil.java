package fork.MaxrregMustermann.AntiSplitNG.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import com.reandroid.apk.APKLogger;
import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.InputSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Works out which split APKs a device can actually use, and can hand the container they came from
 * to the merger so the (possibly large) archive is only read once.
 */
public class DeviceSpecsUtil {

    /** Where the container gets copied when its real location cannot be read directly. */
    private static final String CACHED_CONTAINER = "split_container.zip";

    private final Context context;
    private final APKLogger logger;
    public final String lang;
    private final String densityType;

    private ArchiveFile container;

    public DeviceSpecsUtil(Context context, APKLogger logger) {
        this.context = context;
        this.logger = logger;
        this.lang = Locale.getDefault().getLanguage();
        this.densityType = getDeviceDpi();
    }

    /**
     * The splits to leave out of the merge so the result only carries what this device needs.
     *
     * <p>A container with no configuration splits needs no trimming at all, and one with only a
     * couple of them is cheaper to merge whole.
     */
    public List<String> getSplitsForDevice(Uri uri) throws IOException {
        List<String> splits = getListOfSplits(uri);
        if (splits.size() <= 2) {
            splits.clear();
            return splits;
        }

        List<String> usable = new ArrayList<>();
        for (String split : splits) {
            if (shouldIncludeSplit(split)) {
                usable.add(split);
            }
        }

        // Falling back to "every architecture" beats producing an APK the device cannot run.
        if (!keepsAtLeastOne(DeviceSpecsUtil::isArch, splits, usable)) {
            log("Could not find device architecture, selecting all architectures");
            for (String split : splits) {
                if (isArch(split)) {
                    usable.add(split);
                }
            }
        }
        // Same for screen density: any density at all beats none. The match is a substring one, so
        // a device with no exact match still ends up with the nearest denser bucket.
        if (!keepsAtLeastOne(split -> split.contains("dpi"), splits, usable)) {
            for (String split : splits) {
                if (split.contains("hdpi")) {
                    usable.add(split);
                }
            }
        }

        splits.removeAll(usable);
        return splits;
    }

        // core library desugaring supplies java.util.function below API 24; lint cannot see
        // that, and the class ships in desugar_jdk_libs_minimal.
        @SuppressLint("NewApi")
    private static boolean keepsAtLeastOne(Predicate<String> filter, List<String> candidates,
            List<String> chosen) {
        for (String split : candidates) {
            if (filter.test(split) && chosen.contains(split)) {
                return true;
            }
        }
        return false;
    }

    /** Every {@code .apk} entry in the container, whether or not it is needed by this device. */
    public List<String> getListOfSplits(Uri splitApkUri) throws IOException {
        String path = readablePathOf(splitApkUri);
        if (path != null) {
            return listApksIn(new File(path));
        }
        File copy = new File(context.getCacheDir(), CACHED_CONTAINER);
        try (InputStream is = context.getContentResolver().openInputStream(splitApkUri)) {
            FileUtils.copyFile(is, copy);
        }
        return listApksIn(copy);
    }

    private List<String> listApksIn(File containerFile) throws IOException {
        container = new ArchiveFile(containerFile);
        List<String> splits = new ArrayList<>();
        for (InputSource inputSource : container.getInputSources()) {
            String name = inputSource.getName();
            if (name.endsWith(".apk")) {
                splits.add(name);
            }
        }
        return splits;
    }

    /**
     * The container left open by {@link #getListOfSplits}, or {@code null} if it has not been read
     * yet or has already been handed over. The merger closes whatever it takes.
     */
    public ArchiveFile takeContainer() {
        ArchiveFile taken = container;
        container = null;
        return taken;
    }

    /** The container's real path, or {@code null} if it is not directly readable. */
    public String readablePathOf(Uri uri) {
        try {
            String path = com.starry.FileUtils.getPath(uri, context);
            if (!TextUtils.isEmpty(path) && new File(path).canRead()) {
                return path;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Releases the container if it was opened but never handed to the merger. */
    public void closeContainer() {
        ArchiveFile open = container;
        container = null;
        if (open == null) {
            return;
        }
        try {
            open.close();
        } catch (IOException e) {
            log("Could not close the split container: " + e.getMessage());
        }
    }

    public static boolean isArch(String splitName) {
        return splitName.contains("armeabi") || splitName.contains("arm64")
                || splitName.contains("x86") || splitName.contains("mips");
    }

    public static boolean isBaseApk(String name) {
        return name.equals("base.apk") || !name.startsWith("config") && !name.startsWith("split");
    }

    public boolean shouldIncludeSplit(String name) {
        return isBaseApk(name) || shouldIncludeLang(name) || shouldIncludeArch(name)
                || shouldIncludeDpi(name);
    }

    public boolean shouldIncludeLang(String name) {
        return name.contains(lang);
    }

    public boolean shouldIncludeArch(String name) {
        return name.contains(Build.CPU_ABI)
                || name.replace('-', '_').contains(Build.CPU_ABI.replace('-', '_'));
    }

    /** Matches this device's density bucket, but never a denser bucket that merely shares a prefix. */
    public boolean shouldIncludeDpi(String name) {
        return name.endsWith(densityType) && !name.replace(densityType, "").endsWith("x");
    }

    public String getDeviceDpi() {
        String stored = context.getSharedPreferences("set", Context.MODE_PRIVATE)
                .getString("deviceDpi", "");
        if (!TextUtils.isEmpty(stored)) {
            return stored;
        }
        String densityType = densityTypeOf(context.getResources().getDisplayMetrics().densityDpi);
        densityType += ".apk";
        context.getSharedPreferences("set", Context.MODE_PRIVATE).edit()
                .putString("deviceDpi", densityType).apply();
        return densityType;
    }

    static String densityTypeOf(int densityDpi) {
        switch (densityDpi) {
            case DisplayMetrics.DENSITY_LOW:
                return "ldpi";
            case DisplayMetrics.DENSITY_MEDIUM:
            case DisplayMetrics.DENSITY_140:
                return "mdpi";
            case DisplayMetrics.DENSITY_XHIGH:
            case DisplayMetrics.DENSITY_260:
            case DisplayMetrics.DENSITY_280:
            case DisplayMetrics.DENSITY_300:
                return "xhdpi";
            case DisplayMetrics.DENSITY_340:
            case DisplayMetrics.DENSITY_360:
            case DisplayMetrics.DENSITY_390:
            case DisplayMetrics.DENSITY_400:
            case DisplayMetrics.DENSITY_420:
            case DisplayMetrics.DENSITY_440:
            case DisplayMetrics.DENSITY_450:
            case DisplayMetrics.DENSITY_XXHIGH:
                return "xxhdpi";
            case DisplayMetrics.DENSITY_520:
            case DisplayMetrics.DENSITY_560:
            case DisplayMetrics.DENSITY_600:
            case DisplayMetrics.DENSITY_XXXHIGH:
                return "xxxhdpi";
            case DisplayMetrics.DENSITY_TV:
                return "tvdpi";
            case DisplayMetrics.DENSITY_HIGH:
            case DisplayMetrics.DENSITY_180:
            case DisplayMetrics.DENSITY_200:
            case DisplayMetrics.DENSITY_220:
            default:
                return "hdpi";
        }
    }

    private void log(String message) {
        if (logger != null) {
            logger.logMessage(message);
        }
    }
}