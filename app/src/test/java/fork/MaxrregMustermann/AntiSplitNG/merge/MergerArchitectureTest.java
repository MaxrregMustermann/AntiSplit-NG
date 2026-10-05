package fork.MaxrregMustermann.AntiSplitNG.merge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Mapping the ABI token in a split's file name onto the ABI's real directory name.
 *
 * <p>This decides which {@code lib/} folder is inspected for Play Store protection, so a wrong
 * answer here means a protected app gets merged and signed anyway.
 */
public class MergerArchitectureTest {

    @Test
    public void mapsEveryAbiToken() {
        assertEquals("arm64-v8a", Merger.architectureOf("config.arm64_v8a.apk"));
        assertEquals("arm64-v8a", Merger.architectureOf("split_config.arm64_v8a.apk"));
        assertEquals("armeabi-v7a", Merger.architectureOf("config.armeabi-v7a.apk"));
        assertEquals("armeabi-v7a", Merger.architectureOf("config.armv7a.apk"));
        assertEquals("armeabi-v7a", Merger.architectureOf("config.arm7.apk"));
        assertEquals("x86", Merger.architectureOf("config.x86.apk"));
        assertEquals("x86_64", Merger.architectureOf("config.x86_64.apk"));
        assertEquals("x86_64", Merger.architectureOf("config.x86-64.apk"));
        assertEquals("x86_64", Merger.architectureOf("config.x64.apk"));
    }

    @Test
    public void ignoresNamesThatCarryNoAbi() {
        assertNull(Merger.architectureOf("base.apk"));
        assertNull(Merger.architectureOf("config.en.apk"));
        assertNull(Merger.architectureOf("config.xxhdpi.apk"));
        assertNull(Merger.architectureOf("config.mdpi.apk"));
    }

    @Test
    public void checksTheWiderAbiBeforeTheNarrowerOne() {
        // "x86" is a substring of "x86_64", so the order matters.
        assertEquals("x86_64", Merger.architectureOf("config.x86_64.apk"));
        assertEquals("x86", Merger.architectureOf("config.x86.apk"));
        // Likewise "arm" appears inside "arm64".
        assertEquals("arm64-v8a", Merger.architectureOf("config.arm64_v8a.apk"));
        assertEquals("armeabi-v7a", Merger.architectureOf("config.armeabi-v7a.apk"));
    }
}