package com.abdurazaaqmohammed.AntiSplit.merge;

import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.abdurazaaqmohammed.AntiSplit.R;
import com.abdurazaaqmohammed.AntiSplit.main.MainActivity;
import com.abdurazaaqmohammed.AntiSplit.main.MyAPKLogger;
import com.abdurazaaqmohammed.utils.DeviceSpecsUtil;
import com.abdurazaaqmohammed.utils.FileUtils;
import com.abdurazaaqmohammed.utils.SignUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ArchiveFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Drives a merge from the user's input through to a signed APK.
 *
 * <p>The work itself lives in {@link SplitExtractor} and {@link ApkMerger}; this class is the part
 * that needs an Android {@code Context}: reading the container the user picked, asking about
 * Play-Store protected splits, and signing.
 */
public class Merger {

    private static final String FILE_PROVIDER_AUTHORITY =
            "com.abdurazaaqmohammed.AntiSplit.fileprovider";
    private static final String PAIR_IP_LIBRARY = "libpairipcore.so";
    private static final String CACHED_CONTAINER = "split_container.zip";

    private final MainActivity context;
    private final MyAPKLogger logger;
    private final Resources rss;
    private final DeviceSpecsUtil deviceSpecs;
    /** Where the extracted APKs live, and where the merged and signed APKs are written. */
    private File workingDirectory;

    /** Content URI of the signed APK, set once {@link #run} produced one. */
    public Uri signedApk;

    public Merger(File workingDirectory, MainActivity context, DeviceSpecsUtil deviceSpecs) {
        this.workingDirectory = workingDirectory;
        this.rss = (this.context = context).getRss();
        this.logger = context.getLogger();
        this.deviceSpecs = deviceSpecs;
    }

    public void setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public File run(ApkBundle bundle, boolean signApk, boolean force) throws Exception {
        logger.logMessage("Found modules: " + bundle.getApkModuleList().size());
        return finish(ApkMerger.merge(bundle, workingDirectory, force, logger),
                signApk && !hasPairIpProtection());
    }

    public File run(Uri splitApkUri, List<String> splitsToNotInclude, boolean signApk, boolean force)
            throws Exception {
        logger.logMessage((R.string.searching));
        try (ApkBundle bundle = new ApkBundle()) {
            if (splitApkUri == null) {
                // Several splits, already copied into the working directory by the caller.
                try {
                    bundle.loadApkDirectory(workingDirectory);
                } catch (FileNotFoundException e) {
                    throw new IOException("no APK found in " + splitsToNotInclude + ' '
                            + e.getMessage(), e);
                }
            } else {
                logger.logMessage("MIME Type " + context.getContentResolver().getType(splitApkUri));
                extractAndLoad(bundle, splitApkUri, splitsToNotInclude);
            }
            return run(bundle, signApk, force);
        }
    }

    /** Signs the merged APK, unless signing was declined or turned out to be pointless. */
    private File finish(File mergedApk, boolean sign) throws Exception {
        if (!sign) {
            return mergedApk;
        }
        logger.logMessage((R.string.signing));
        File signed = new File(workingDirectory, System.currentTimeMillis() + "signed.apk");
        SignUtil.signDebugKey(context, mergedApk, signed);
        signedApk = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, signed);
        return signed;
    }

    /**
     * A split shipping {@code libpairipcore.so} is Play-Store protected, so the merged APK cannot
     * be signed with the app's own key. Ask before merging unsigned, which will not install.
     *
     * @return whether the user chose to merge without signing
     */
    private boolean hasPairIpProtection() {
        File[] splits = workingDirectory.listFiles();
        if (splits == null) {
            return false;
        }
        for (File split : splits) {
            String architecture = architectureOf(split.getName());
            if (architecture == null) {
                continue;
            }
            try (ApkModule module = ApkModule.loadApkFile(split, split.getName())) {
                if (module.containsFile("lib/" + architecture + '/' + PAIR_IP_LIBRARY)) {
                    logger.logMessage((R.string.warning) + " " + split.getName());
                    return askWhetherToSkipSigning();
                }
            } catch (IOException | InterruptedException e) {
                logger.logMessage("Could not inspect " + split.getName() + ": " + e.getMessage());
            }
        }
        return false;
    }

    /** Maps an ABI token in a split's file name onto the ABI's real directory name. */
    static String architectureOf(String splitName) {
        if (splitName.contains("x86_64") || splitName.contains("x86-64")
                || splitName.contains("x64")) {
            return "x86_64";
        }
        if (splitName.contains("x86")) {
            return "x86";
        }
        if (splitName.contains("arm64")) {
            return "arm64-v8a";
        }
        if (splitName.contains("v7a") || splitName.contains("arm7")) {
            return "armeabi-v7a";
        }
        return null;
    }

    private boolean askWhetherToSkipSigning() throws InterruptedException {
        CountDownLatch answered = new CountDownLatch(1);
        boolean[] skipSigning = {false};
        context.getHandler().post(() -> context.runOnUiThread(
                new MaterialAlertDialogBuilder(context)
                        .setTitle(rss.getString(R.string.warning))
                        .setMessage(R.string.pairip_warning)
                        .setPositiveButton("OK", (dialog, which) -> {
                            skipSigning[0] = true;
                            answered.countDown();
                        })
                        .setNegativeButton(rss.getString(R.string.cancel), (dialog, which) -> {
                            context.startActivity(new Intent(context, MainActivity.class));
                            context.finishAffinity();
                            answered.countDown();
                        })
                        .create()::show));
        answered.await();
        return skipSigning[0];
    }

    /**
     * Extracts the container into the working directory and hands the directory to the bundle.
     *
     * <p>The container may already be open from split discovery, in which case it is reused instead
     * of copying a potentially large archive a second time.
     */
    private void extractAndLoad(ApkBundle bundle, Uri splitApkUri, List<String> splitsToNotInclude)
            throws IOException {
        ArchiveFile container = deviceSpecs.takeContainer();
        File temporaryCopy = null;
        try {
            if (container == null) {
                String path = deviceSpecs.readablePathOf(splitApkUri);
                File containerFile;
                if (path == null) {
                    containerFile = new File(context.getCacheDir(), CACHED_CONTAINER);
                    temporaryCopy = containerFile;
                    try (InputStream is = context.getContentResolver().openInputStream(splitApkUri)) {
                        FileUtils.copyFile(is, containerFile);
                    }
                } else {
                    containerFile = new File(path);
                }
                container = new ArchiveFile(containerFile);
            }
            new SplitExtractor(workingDirectory, logger, rss::getString,
                    R.string.skipping, R.string.not_apk, R.string.unselected)
                    .extract(container, splitsToNotInclude);
        } finally {
            if (container != null) {
                container.close();
            }
            if (temporaryCopy != null && !temporaryCopy.delete()) {
                temporaryCopy.deleteOnExit();
            }
        }
        try {
            bundle.loadApkDirectory(workingDirectory);
        } catch (FileNotFoundException e) {
            throw new IOException(describeContainer(splitApkUri) + ' ' + e.getMessage(), e);
        }
    }

    private String describeContainer(Uri splitApkUri) {
        String path = deviceSpecs.readablePathOf(splitApkUri);
        if (path == null || path.isEmpty()) {
            path = context.getOriginalFileName(splitApkUri);
        }
        return "uri " + splitApkUri + " file " + path;
    }
}