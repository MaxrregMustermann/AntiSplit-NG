package com.abdurazaaqmohammed.AntiSplit.merge;

import android.annotation.SuppressLint;
import com.reandroid.apk.APKLogger;
import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.InputSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Copies the APKs out of a split container into a working directory.
 *
 * <p>Every split except the ones the caller listed is extracted. Entry names come straight out of
 * the zip, so each output path is resolved against the working directory first and anything landing
 * outside it is refused rather than written.
 *
 * <p>Deliberately free of Android types so the extraction rules can be tested on their own.
 */
public final class SplitExtractor {

    private final File workingDirectory;
    private final APKLogger logger;
    /** Resolves localised log prefixes; supplied by the caller so this class stays Android-free. */
    private final IntFunction<String> strings;
    private final int skippingString;
    private final int notApkString;
    private final int unselectedString;

    public SplitExtractor(File workingDirectory, APKLogger logger, IntFunction<String> strings,
            int skippingString, int notApkString, int unselectedString) {
        this.workingDirectory = workingDirectory;
        this.logger = logger;
        this.strings = strings;
        this.skippingString = skippingString;
        this.notApkString = notApkString;
        this.unselectedString = unselectedString;
    }

    /**
     * Extracts the container's APKs into the working directory.
     *
     * @param leaveOut splits the caller excluded; {@code null} or empty extracts everything
     * @return the names of the extracted APKs, in container order
     */
        // core library desugaring supplies java.util.function below API 24; lint cannot see
        // that, and the class ships in desugar_jdk_libs_minimal.
        @SuppressLint("NewApi")
    public List<String> extract(ArchiveFile container, List<String> leaveOut) throws IOException {
        List<String> extracted = new ArrayList<>();
        boolean checkLeaveOut = leaveOut != null && !leaveOut.isEmpty();
        for (InputSource entry : container.getInputSources()) {
            String name = entry.getName();
            if (!name.endsWith(".apk")) {
                log(text(skippingString) + name + text(notApkString));
                continue;
            }
            if (checkLeaveOut && leaveOut.contains(name)) {
                log(text(skippingString) + name + text(unselectedString));
                continue;
            }
            if (extractApk(entry)) {
                extracted.add(name);
            }
        }
        return extracted;
    }

    private boolean extractApk(InputSource entry) throws IOException {
        File outputFile = new File(workingDirectory, entry.getName());
        if (!staysInside(workingDirectory, entry.getName())) {
            // A zip entry name may point anywhere on the filesystem, so refuse it outright.
            log("Skipped invalid path: " + entry.getName());
            return false;
        }
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            log("Could not create folder " + parent + " for " + entry.getName());
            return false;
        }
        try (InputStream is = entry.openStream();
                OutputStream out = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        return true;
    }

    /**
     * Whether a zip entry name resolves to somewhere inside the working directory.
     *
     * <p>ARSCLib strips {@code ..} segments before handing names over, so this is the second lock on
     * the same door: nothing may be written outside the directory the merge set up.
     */
    static boolean staysInside(File workingDirectory, String entryName) throws IOException {
        String workingPath = workingDirectory.getCanonicalPath() + File.separator;
        return new File(workingDirectory, entryName).getCanonicalPath().startsWith(workingPath);
    }

    // core library desugaring supplies java.util.function below API 24; lint cannot see that, and
    // the class ships in desugar_jdk_libs_minimal.
    @SuppressLint("NewApi")
    private String text(int stringId) {
        return strings.apply(stringId);
    }

    private void log(String message) {
        if (logger != null) {
            logger.logMessage(message);
        }
    }
}