package com.abdurazaaqmohammed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The translation files, checked the way the build checks them.
 *
 * <p>Translations arrive from Crowdin and are then edited by hand, so the failure modes are a file
 * that no longer parses, a duplicate key, or a {@code %1$s} dropped from a translation that the app
 * then formats at runtime. Each of those is tedious to diagnose from a stack trace, so they are
 * checked here instead.
 */
public class TranslationsTest {

    /** The unit test working directory is the module directory, which is where the sources live. */
    private static final File RES = new File("src/main/res");
    private static final File DEFAULT_STRINGS = new File(RES, "values/strings.xml");

    /** A locale folder is a language alone ("values-de") or with a region ("values-zh-rCN"). */
    private static final Pattern LOCALE_FOLDER = Pattern.compile("^values-([a-z]{2,3})(-r[A-Z]{2})?$");
    private static final Pattern STRING_NAME = Pattern.compile("<string\\s+name=\"([^\"]+)\"");
    /** Android's Pattern does not expose RegexOption, so the DOTALL flag is set by hand. */
    private static final int DOTALL = 32;
    private static final Pattern STRING_VALUE =
            Pattern.compile("<string\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string>", DOTALL);
    private static final Pattern FORMAT_SPECIFIER = Pattern.compile("%\\d*\\$?[sd]");

    private static List<File> translationFiles() {
        File[] folders = RES.listFiles();
        List<File> files = new ArrayList<>();
        if (folders == null) {
            return files;
        }
        for (File folder : folders) {
            if (folder.isDirectory() && LOCALE_FOLDER.matcher(folder.getName()).matches()
                    && new File(folder, "strings.xml").isFile()) {
                files.add(new File(folder, "strings.xml"));
            }
        }
        return files;
    }

    private static String read(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Could not read " + file, e);
        }
    }

    private static List<String> stringNames(String xml) {
        List<String> names = new ArrayList<>();
        Matcher matcher = STRING_NAME.matcher(xml);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static Map<String, String> stringValues(String xml) {
        Map<String, String> values = new HashMap<>();
        Matcher matcher = STRING_VALUE.matcher(xml);
        while (matcher.find()) {
            values.put(matcher.group(1), matcher.group(2));
        }
        return values;
    }

    /** The format specifiers in a value, sorted so argument order does not matter. */
    private static List<String> specifiers(String value) {
        if (value == null) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        Matcher matcher = FORMAT_SPECIFIER.matcher(value);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        Collections.sort(found);
        return found;
    }

    @Test
    public void theDefaultLocaleIsWhereTheTestExpectsIt() {
        // Without this the other tests would quietly pass on an empty set of files.
        assertTrue("the default locale was not found under " + new File(".").getAbsolutePath(),
                DEFAULT_STRINGS.isFile());
    }

    @Test
    public void thereAreTranslationsToCheck() {
        // Without this the rest of the class would pass on an empty set of files.
        assertFalse("no translation files found under " + RES.getAbsolutePath(),
                translationFiles().isEmpty());
    }

    @Test
    public void everyTranslationFileIsWellFormed() {
        for (File file : translationFiles()) {
            try {
                javax.xml.parsers.DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder().parse(file);
            } catch (Exception e) {
                throw new AssertionError(file + " is not valid XML: " + e.getMessage(), e);
            }
        }
    }

    @Test
    public void everyTranslationIsValidUtf8() {
        // A file saved in the wrong encoding breaks the build on a resource that looks fine in a diff.
        for (File file : translationFiles()) {
            assertFalse(file + " contains a replacement character, so it is not valid UTF-8",
                    read(file).contains("�"));
        }
    }

    @Test
    public void theDefaultLocaleDefinesTheAppStrings() {
        assertFalse("the default locale must define the app's strings",
                stringNames(read(DEFAULT_STRINGS)).isEmpty());
    }

    @Test
    public void noStringIsDefinedTwiceInTheSameFile() {
        // A duplicate silently drops one of the two values, which is hard to notice in a diff.
        for (File file : translationFiles()) {
            List<String> seen = new ArrayList<>();
            for (String name : stringNames(read(file))) {
                assertFalse(file + " defines \"" + name + "\" more than once", seen.contains(name));
                seen.add(name);
            }
        }
    }

    @Test
    public void theAppNameIsNotTranslated() {
        // "AntiSplit M" is a brand name, so a translation of it is a Crowdin mistake to drop.
        assertTrue("app_name must be marked untranslatable",
                read(DEFAULT_STRINGS).contains(
                        "<string name=\"app_name\" translatable=\"false\""));
        for (File file : translationFiles()) {
            assertFalse(file + " translates app_name, which is a brand name",
                    read(file).contains("name=\"app_name\""));
        }
    }

    @Test
    public void formatSpecifiersMatchTheDefaultLocale() {
        // A %1$s dropped from a translation crashes the app when that string is shown.
        Map<String, String> defaults = stringValues(read(DEFAULT_STRINGS));
        for (File file : translationFiles()) {
            Map<String, String> translated = stringValues(read(file));
            for (Map.Entry<String, String> entry : translated.entrySet()) {
                String defaultValue = defaults.get(entry.getKey());
                if (defaultValue == null) {
                    continue; // a library string override, which the default locale does not define
                }
                assertEquals(file + " formats \"" + entry.getKey() + "\" differently from the "
                                + "default locale",
                        specifiers(defaultValue), specifiers(entry.getValue()));
            }
        }
    }

    @Test
    public void quotesAreBalancedInEveryTranslation() {
        // A stray quote makes Android's aapt2 fail with an unhelpful parse error.
        for (File file : translationFiles()) {
            for (Map.Entry<String, String> entry : stringValues(read(file)).entrySet()) {
                String value = entry.getValue();
                // A wrapped value has exactly two quote characters: one opening, one closing.
                long quotes = value.chars().filter(character -> character == '"').count();
                assertTrue(file + " has unbalanced quotes in \"" + entry.getKey() + "\": " + value,
                        quotes % 2 == 0);
            }
        }
    }
}