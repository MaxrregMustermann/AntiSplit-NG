package com.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The file helpers that need a real {@code Context}. */
@RunWith(RobolectricTestRunner.class)
public class FileUtilsAndroidTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void copiesAnAssetIntoInternalStorage() throws IOException {
        // This is how the signing keystore reaches the signer.
        File copied = FileUtils.copyFileFromAssetsAndGetFile("debug23.keystore", context);

        assertTrue(copied.isFile());
        assertTrue("the keystore must not be empty", copied.length() > 0);
        assertTrue("it must land in internal storage", copied.getPath()
                .startsWith(context.getFilesDir().getPath()));
    }

    @Test
    public void doesNotCopyAnAssetItAlreadyHas() throws IOException {
        File first = FileUtils.copyFileFromAssetsAndGetFile("debug23.keystore", context);
        long length = first.length();

        File second = FileUtils.copyFileFromAssetsAndGetFile("debug23.keystore", context);

        assertTrue("a second call must reuse the copy", first.equals(second));
        assertTrue(length == second.length());
    }

    @Test
    public void reportsAnAssetThatIsNotThere() {
        assertThrows(IOException.class,
                () -> FileUtils.copyFileFromAssetsAndGetFile("not-an-asset", context));
    }

    @Test
    public void readsAndWritesThroughChannelsWhenAvailable() throws IOException {
        // FileUtils uses NIO channels from API 26 and plain streams below that.
        File source = new File(context.getCacheDir(), "source.bin");
        Files.write(source.toPath(), "payload".getBytes(StandardCharsets.UTF_8));
        File destination = new File(context.getCacheDir(), "destination.bin");

        FileUtils.copyFile(source, destination);

        assertTrue(destination.isFile());
        assertTrue("payload".equals(
                new String(Files.readAllBytes(destination.toPath()), StandardCharsets.UTF_8)));
    }

    @Test
    public void truncatesAnExistingDestination() throws IOException {
        File source = new File(context.getCacheDir(), "source.bin");
        Files.write(source.toPath(), "new".getBytes(StandardCharsets.UTF_8));
        File destination = new File(context.getCacheDir(), "destination.bin");
        Files.write(destination.toPath(),
                "a much longer previous value".getBytes(StandardCharsets.UTF_8));

        FileUtils.copyFile(source, destination);

        assertTrue("new".equals(new String(Files.readAllBytes(destination.toPath()),
                StandardCharsets.UTF_8)));
    }
}
