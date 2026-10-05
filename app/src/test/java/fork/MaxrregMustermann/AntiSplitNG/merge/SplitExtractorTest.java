package fork.MaxrregMustermann.AntiSplitNG.merge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import fork.MaxrregMustermann.AntiSplitNG.testing.RecordingLogger;
import fork.MaxrregMustermann.AntiSplitNG.testing.SplitApkFixture;
import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.InputSource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Getting the splits out of a container, and nothing else.
 */
public class SplitExtractorTest {

    private static final int SKIPPING = 1;
    private static final int NOT_APK = 2;
    private static final int UNSELECTED = 3;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File workingDirectory;
    private File containerDirectory;
    private RecordingLogger logger;

    @Before
    public void setUp() throws IOException {
        workingDirectory = temporaryFolder.newFolder("work");
        containerDirectory = temporaryFolder.newFolder("container");
        logger = new RecordingLogger();
    }

    private SplitExtractor extractor() {
        return new SplitExtractor(workingDirectory, logger, id -> "str" + id + ":",
                SKIPPING, NOT_APK, UNSELECTED);
    }

    private File containerOf(String name, List<SplitApkFixture.Split> splits,
            Map<String, String> extras) throws IOException {
        File container = new File(containerDirectory, name);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(container))) {
            for (SplitApkFixture.Split split : splits) {
                zip.putNextEntry(new ZipEntry(split.name));
                zip.write(java.nio.file.Files.readAllBytes(split.file.toPath()));
                zip.closeEntry();
            }
            for (Map.Entry<String, String> extra : extras.entrySet()) {
                zip.putNextEntry(new ZipEntry(extra.getKey()));
                zip.write(extra.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return container;
    }

    private List<SplitApkFixture.Split> splits(String... names) throws IOException {
        List<SplitApkFixture.Split> written = new ArrayList<>();
        for (String name : names) {
            written.add(SplitApkFixture.writeSplitApk(containerDirectory, name, false));
        }
        return written;
    }

    private List<String> workDirEntries() {
        String[] names = workingDirectory.list();
        List<String> entries = new ArrayList<>();
        if (names != null) {
            java.util.Collections.addAll(entries, names);
        }
        java.util.Collections.sort(entries);
        return entries;
    }

    @Test
    public void extractsEveryApk() throws IOException {
        File container = containerOf("app.xapk", splits("base.apk", "config.en.apk"),
                new LinkedHashMap<>());

        List<String> extracted;
        try (ArchiveFile archive = new ArchiveFile(container)) {
            extracted = extractor().extract(archive, null);
        }

        assertEquals(List.of("base.apk", "config.en.apk"), extracted);
        assertEquals(List.of("base.apk", "config.en.apk"), workDirEntries());
    }

    @Test
    public void skipsEntriesThatAreNotApks() throws IOException {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("icon.png", "png-bytes");
        extras.put("AndroidManifest.xml", "<manifest/>");
        extras.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0");
        File container = containerOf("app.xapk", splits("base.apk"), extras);

        try (ArchiveFile archive = new ArchiveFile(container)) {
            assertEquals(List.of("base.apk"), extractor().extract(archive, null));
        }

        assertEquals(List.of("base.apk"), workDirEntries());
        assertTrue("icon.png must be reported: " + logger.messages(),
                logger.contains("icon.png"));
        assertTrue("the not-an-APK reason must be reported: " + logger.messages(),
                logger.contains("str2:"));
    }

    @Test
    public void leavesOutTheSplitsItIsToldTo() throws IOException {
        File container = containerOf("app.xapk",
                splits("base.apk", "config.en.apk", "config.de.apk"), new LinkedHashMap<>());

        try (ArchiveFile archive = new ArchiveFile(container)) {
            assertEquals(List.of("base.apk", "config.en.apk"),
                    extractor().extract(archive, List.of("config.de.apk")));
        }

        assertEquals(List.of("base.apk", "config.en.apk"), workDirEntries());
        assertTrue("the skipped split must be reported: " + logger.messages(),
                logger.contains("config.de.apk"));
        assertTrue("the unselected reason must be reported: " + logger.messages(),
                logger.contains("str3:"));
    }

    @Test
    public void anEmptyLeaveOutListMeansExtractEverything() throws IOException {
        File container = containerOf("app.xapk", splits("base.apk", "config.en.apk"),
                new LinkedHashMap<>());

        try (ArchiveFile archive = new ArchiveFile(container)) {
            assertEquals(2, extractor().extract(archive, List.of()).size());
        }
    }

    @Test
    public void neutralisesAnEntryPointingOutsideTheWorkingDirectory() throws IOException {
        // ARSCLib strips ".." from entry names before handing them over, so a hostile container
        // cannot make the extractor write outside the working directory.
        File container = containerOf("evil.xapk", splits("base.apk"),
                Map.of("../escaped.apk", "payload"));

        try (ArchiveFile archive = new ArchiveFile(container)) {
            assertEquals(List.of("base.apk", "escaped.apk"),
                    extractor().extract(archive, null));
        }

        assertFalse("nothing may be written outside the working directory",
                new File(containerDirectory, "escaped.apk").exists());
        assertTrue("it landed inside instead", new File(workingDirectory, "escaped.apk").isFile());
    }

    @Test
    public void refusesAnyNameThatWouldEscapeTheWorkingDirectory() throws IOException {
        for (String name : new String[]{"../escaped.apk", "../../escaped.apk",
                "nested/../../escaped.apk", "..", "../", "nested/../.."}) {
            assertFalse(name + " must be refused",
                    SplitExtractor.staysInside(workingDirectory, name));
        }
        for (String name : new String[]{"base.apk", "nested/config.en.apk", "a/b/c/d.apk",
                ".hidden.apk", "..apk", "apk..", "a..b.apk", "nested/../kept.apk"}) {
            assertTrue(name + " must be allowed", SplitExtractor.staysInside(workingDirectory, name));
        }
        // A leading slash is not an absolute path here: File resolution keeps it under the parent.
        assertTrue("/etc/escaped.apk must stay inside",
                SplitExtractor.staysInside(workingDirectory, "/etc/escaped.apk"));
    }

    @Test
    public void createsMissingFoldersForNestedSplitNames() throws IOException {
        File container = containerOf("app.xapk", List.of(), Map.of("nested/config.en.apk", "apk"));

        try (ArchiveFile archive = new ArchiveFile(container)) {
            assertEquals(List.of("nested/config.en.apk"), extractor().extract(archive, null));
        }

        assertTrue(new File(workingDirectory, "nested/config.en.apk").isFile());
    }

    @Test
    public void overwritesAnExistingSplit() throws IOException {
        File container = containerOf("app.xapk", splits("base.apk"), new LinkedHashMap<>());
        File existing = new File(workingDirectory, "base.apk");
        try (FileOutputStream out = new FileOutputStream(existing)) {
            out.write("stale contents".getBytes(StandardCharsets.UTF_8));
        }

        try (ArchiveFile archive = new ArchiveFile(container)) {
            extractor().extract(archive, null);
        }

        assertTrue("the file must have been rewritten, not appended to",
                existing.length() < 100000);
        assertTrue(existing.isFile());
    }

    @Test
    public void worksWithoutALogger() throws IOException {
        File container = containerOf("app.xapk", splits("base.apk"), Map.of("icon.png", "png"));

        try (ArchiveFile archive = new ArchiveFile(container)) {
            SplitExtractor silent = new SplitExtractor(workingDirectory, null, id -> "",
                    SKIPPING, NOT_APK, UNSELECTED);
            assertEquals(List.of("base.apk"), silent.extract(archive, null));
        }
    }

    @Test
    public void reportsAContainerThatIsNotAZip() throws IOException {
        File broken = new File(containerDirectory, "broken.xapk");
        java.nio.file.Files.write(broken.toPath(), "not a zip".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> new ArchiveFile(broken));
    }

    @Test
    public void reportsAContainerThatIsMissing() {
        assertThrows(IOException.class,
                () -> new ArchiveFile(new File(containerDirectory, "gone.xapk")));
    }

    @Test
    public void extractsNothingFromAContainerWithoutApks() throws IOException {
        File container = containerOf("empty.xapk", List.of(), Map.of("readme.txt", "hi"));

        try (ArchiveFile archive = new ArchiveFile(container)) {
            assertEquals(List.of(), extractor().extract(archive, null));
        }

        assertEquals(List.of(), workDirEntries());
    }

    @Test
    public void everyEntryInTheContainerIsAccountedFor() throws IOException {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("a.apk", "first");
        extras.put("b.apk", "second");
        extras.put("c.txt", "third");
        File container = containerOf("app.xapk", splits("base.apk"), extras);

        List<String> extracted;
        try (ArchiveFile archive = new ArchiveFile(container)) {
            extracted = extractor().extract(archive, null);
        }

        List<String> all = new ArrayList<>();
        try (ArchiveFile archive = new ArchiveFile(container)) {
            for (InputSource source : archive.getInputSources()) {
                all.add(source.getName());
            }
        }
        assertEquals("nothing in the container may go unaccounted for", all.size(),
                extracted.size() + logger.messages().size());
        assertNull(new File(workingDirectory, "c.txt").exists() ? "c.txt was extracted" : null);
    }
}