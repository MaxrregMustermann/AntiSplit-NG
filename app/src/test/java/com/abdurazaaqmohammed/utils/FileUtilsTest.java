package com.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Never overwriting an earlier merge, and telling an extension from a dotted folder name. */
public class FileUtilsTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void returnsTheFileWhenNothingIsInTheWay() {
        File file = new File(temporaryFolder.getRoot(), "merged.apk");

        assertEquals(file, FileUtils.getUnusedFile(file));
    }

    @Test
    public void numbersUntilItFindsAFreeName() throws IOException {
        File target = new File(temporaryFolder.getRoot(), "merged.apk");
        Files.write(target.toPath(), "first".getBytes(StandardCharsets.UTF_8));

        File second = FileUtils.getUnusedFile(target);
        assertEquals("merged_1.apk", second.getName());

        Files.write(second.toPath(), "second".getBytes(StandardCharsets.UTF_8));
        File third = FileUtils.getUnusedFile(target);
        assertEquals("merged_2.apk", third.getName());
    }

    @Test
    public void keepsCountingPastExistingNumberedNames() throws IOException {
        File target = new File(temporaryFolder.getRoot(), "merged.apk");
        for (String name : new String[]{"merged.apk", "merged_1.apk", "merged_2.apk",
                "merged_3.apk"}) {
            Files.write(new File(temporaryFolder.getRoot(), name).toPath(), "x".getBytes());
        }

        assertEquals("merged_4.apk", FileUtils.getUnusedFile(target).getName());
    }

    @Test
    public void keepsTheFolderOfTheTargetFile() throws IOException {
        File subFolder = temporaryFolder.newFolder("sub");
        File target = new File(subFolder, "merged.apk");

        assertEquals(subFolder, FileUtils.getUnusedFile(target).getParentFile());
    }

    @Test
    public void readsTheExtension() {
        assertEquals("apk", FileUtils.extensionOf("merged.apk"));
        assertEquals("gz", FileUtils.extensionOf("merged.tar.gz"));
        assertEquals("apk", FileUtils.extensionOf("com.example.game.apk"));
    }

    @Test
    public void reportsNoExtensionWhenThereIsNone() {
        assertEquals("", FileUtils.extensionOf("merged"));
        assertEquals("", FileUtils.extensionOf(".hidden"));
        assertEquals("", FileUtils.extensionOf(""));
    }

    @Test
    public void doesNotMistakeAFolderForAnExtension() {
        assertEquals("", FileUtils.extensionOf("dir.apk/merged"));
        assertEquals("apk", FileUtils.extensionOf("dir.apk/merged.apk"));
        assertEquals("", FileUtils.extensionOf(".apk"));
        assertEquals("", FileUtils.extensionOf("C:\\folder.name\\merged"));
        assertEquals("apk", FileUtils.extensionOf("C:\\folder\\merged.apk"));
    }

    @Test
    public void copiesAFile() throws IOException {
        File source = new File(temporaryFolder.getRoot(), "source.bin");
        Files.write(source.toPath(), "hello world".getBytes(StandardCharsets.UTF_8));
        File destination = new File(temporaryFolder.getRoot(), "copy.bin");

        FileUtils.copyFile(source, destination);

        assertTrue(destination.isFile());
        assertEquals("hello world",
                new String(Files.readAllBytes(destination.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void copiesOverAnExistingFile() throws IOException {
        File source = new File(temporaryFolder.getRoot(), "source.bin");
        Files.write(source.toPath(), "new".getBytes(StandardCharsets.UTF_8));
        File destination = new File(temporaryFolder.getRoot(), "destination.bin");
        Files.write(destination.toPath(), "much longer old contents".getBytes(StandardCharsets.UTF_8));

        FileUtils.copyFile(source, destination);

        assertEquals("new", new String(Files.readAllBytes(destination.toPath()),
                StandardCharsets.UTF_8));
    }

    @Test
    public void reportsAMissingSource() {
        File missing = new File(temporaryFolder.getRoot(), "gone.bin");

        assertFalse(missing.exists());
        org.junit.Assert.assertThrows(IOException.class,
                () -> FileUtils.copyFile(missing, new File(temporaryFolder.getRoot(), "out.bin")));
    }

}