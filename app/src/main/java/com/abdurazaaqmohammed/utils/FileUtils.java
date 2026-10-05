package com.abdurazaaqmohammed.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class FileUtils {

    public static File copyFileFromAssetsAndGetFile(String fileName, Context context) throws IOException {
        File destinationFile = new File(context.getFilesDir(), fileName);
        if (!destinationFile.exists())
            try (InputStream is = context.getAssets().open(fileName)) {
                copyFile(is, destinationFile);
            }
        return destinationFile;
    }

    /**
     * The same file with a {@code _1}, {@code _2}, ... suffix added, so a merge never silently
     * overwrites an earlier one.
     */
    public static File getUnusedFile(File file) {
        int i = 0;
        while (file.exists()) {
            i++;
            String fileName = file.getName();
            String extension = extensionOf(fileName);
            file = new File(file.getParentFile(),
                    fileName.replace('.' + extension, "").replaceFirst("_\\d+$", "") + '_' + i + '.' + extension);
        }
        return file;
    }

    /**
     * The text after the last dot, or an empty string when there is none.
     *
     * <p>A dot in a folder name is not an extension, and a leading dot marks a hidden file rather
     * than an extension.
     */
    static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        int separator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return dot > separator + 1 ? fileName.substring(dot + 1) : "";
    }

    public static File getUnusedFile(String file) {
        return getUnusedFile(new File(file));
    }

    public static OutputStream getOutputStream(String filepath) throws IOException {
        return getOutputStream(new File(filepath));
    }

    public static void copyFile(File sourceFile, File destinationFile) throws IOException {
        try (InputStream is = getInputStream(sourceFile);
                OutputStream os = getOutputStream(destinationFile)) {
            copyFile(is, os);
        }
    }

    public static void copyFile(InputStream is, OutputStream os) throws IOException {
        if (LegacyUtils.supportsWriteExternalStorage) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0)
                os.write(buffer, 0, length);
        } else
            android.os.FileUtils.copy(is, os);
    }

    public static InputStream getInputStream(File file) throws IOException {
        if (LegacyUtils.supportsFileChannel) {
            try {
                return Files.newInputStream(file.toPath(), StandardOpenOption.READ);
            } catch (Exception ignored) {
            }
        }
        return new FileInputStream(file);
    }

    public static InputStream getInputStream(String filePath) throws IOException {
        return getInputStream(new File(filePath));
    }

    public static void copyFile(InputStream is, File destinationFile) throws IOException {
        try (OutputStream os = getOutputStream(destinationFile)) {
            copyFile(is, os);
        }
    }

    public static void copyFile(File in, OutputStream os) throws IOException {
        try (InputStream is = getInputStream(in)) {
            copyFile(is, os);
        }
    }

    public static OutputStream getOutputStream(File file) throws IOException {
        if (LegacyUtils.supportsFileChannel) {
            try {
                return Files.newOutputStream(file.toPath(), java.nio.file.StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception ignored) {
            }
        }
        return new FileOutputStream(file);
    }

    public static boolean doesNotHaveStoragePerm(Context context) {
        return Build.VERSION.SDK_INT > 22 && (LegacyUtils.supportsWriteExternalStorage
                ? context.checkSelfPermission(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                : !Environment.isExternalStorageManager());
    }

    public static File getAntisplitMFolder() {
        final File antisplitMFolder = new File(Environment.getExternalStorageDirectory(), "AntiSplit-M");
        return antisplitMFolder.exists() || antisplitMFolder.mkdir() ? antisplitMFolder
                : new File(Environment.getExternalStorageDirectory(), "Download");
    }
}