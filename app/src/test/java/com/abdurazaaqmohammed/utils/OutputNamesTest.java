package com.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Naming the output file. A split container may carry any of a handful of extensions and the result
 * is always an {@code .apk}, so getting this wrong silently produces files the platform refuses.
 */
public class OutputNamesTest {

    private static final String SUFFIX = "_antisplit";

    @Test
    public void replacesEveryContainerExtension() {
        // ".apm" is not a container extension the app has ever supported, so it is not matched.
        for (String extension : new String[]{"zip", "xapk", "aspk", "apkm", "apks"}) {
            assertEquals("app." + extension,
                    "app" + SUFFIX + ".apk", OutputNames.merged("app." + extension, SUFFIX));
        }
    }

    @Test
    public void aBareApkIsLeftAlone() {
        // A loose split is already .apk, so the suffix must not be applied twice.
        assertEquals("base.apk", OutputNames.merged("base.apk", SUFFIX));
    }

    @Test
    public void keepsTheNameAroundTheExtension() {
        assertEquals("com.example.game_antisplit.apk",
                OutputNames.merged("com.example.game.xapk", SUFFIX));
        assertEquals("a.b.c_antisplit.apk", OutputNames.merged("a.b.c.apks", SUFFIX));
        assertEquals("app.antisplit.apk", OutputNames.merged("app.zip", ".antisplit"));
    }

    @Test
    public void leavesAnUnknownExtensionAlone() {
        assertEquals("app.tar", OutputNames.merged("app.tar", SUFFIX));
        assertEquals("app", OutputNames.merged("app", SUFFIX));
        assertEquals("app.apm", OutputNames.merged("app.apm", SUFFIX));
        // The trailing extension is the one that counts, whatever precedes it.
        assertEquals("app.xapk_antisplit.apk", OutputNames.merged("app.xapk.zip", SUFFIX));
    }

    @Test
    public void worksWithAnEmptySuffix() {
        assertEquals("app.apk", OutputNames.merged("app.xapk", ""));
    }

    @Test
    public void onlyReplacesATrailingExtension() {
        // "app.xapk" must not become "app_antisplit.apk.apk"
        assertEquals("app_antisplit.apk", OutputNames.merged("app.xapk", SUFFIX));
    }

    @Test
    public void replacesTheExtensionInsideAFullPath() {
        assertEquals("/storage/emulated/0/Download/app_antisplit.apk",
                OutputNames.mergedInPath("/storage/emulated/0/Download/app.xapk", SUFFIX));
        assertEquals("/storage/emulated/0/Download/app_antisplit.apk",
                OutputNames.mergedInPath("/storage/emulated/0/Download/app.apks", SUFFIX));
    }

    @Test
    public void renamesAnAlreadyExtractedApk() {
        assertEquals("base_antisplit.apk", OutputNames.mergedFromApk("base.apk", SUFFIX));
        assertEquals("com.example.game_antisplit.apk",
                OutputNames.mergedFromApk("com.example.game.apk", SUFFIX));
    }

    @Test
    public void stripsOnlyATrailingApk() {
        assertEquals("com.example.game", OutputNames.withoutExtension("com.example.game.apk"));
        assertEquals("app.zip", OutputNames.withoutExtension("app.zip"));
        assertEquals("app", OutputNames.withoutExtension("app"));
    }

    @Test
    public void namesTheFallbackOutput() {
        assertEquals("unknown_antisplit.apk", OutputNames.unknown(SUFFIX));
        assertEquals("unknown.apk", OutputNames.unknown(""));
    }

    @Test
    public void treatsTheSuffixAsLiteralText() {
        // The suffix comes from a user-editable field; $1 and \d must not be read as a pattern.
        assertEquals("app$1.apk", OutputNames.merged("app.zip", "$1"));
        assertEquals("app\\d.apk", OutputNames.merged("app.zip", "\\d"));
        assertEquals("app[x].apk", OutputNames.merged("app.zip", "[x]"));
        assertEquals("app$1.apk", OutputNames.mergedFromApk("app.apk", "$1"));
        assertEquals("/tmp/app$1.apk", OutputNames.mergedInPath("/tmp/app.zip", "$1"));
    }

    @Test
    public void aSuffixThatLooksLikeAnExtensionIsNotReinterpreted() {
        // "app.zip" + suffix "x.apk" must not have the ".apk" inside the suffix replaced again.
        assertEquals("appx.apk.apk", OutputNames.merged("app.zip", "x.apk"));
    }
}
