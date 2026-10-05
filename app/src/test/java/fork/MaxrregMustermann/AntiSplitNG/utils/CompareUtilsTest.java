package fork.MaxrregMustermann.AntiSplitNG.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import fork.MaxrregMustermann.AntiSplitNG.main.AppInfo;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Sorting the installed-apps list. The order decides which app a user taps when several share a
 * name, so case handling matters as much as the comparison itself.
 */
public class CompareUtilsTest {

    @Test
    public void sortsAppNamesCaseInsensitively() {
        List<AppInfo> apps = new ArrayList<>(Arrays.asList(
                app("Zebra"), app("apple"), app("Banana"), app("cherry")));

        Collections.sort(apps, CompareUtils::compareAppInfoByName);

        assertEquals(Arrays.asList("apple", "Banana", "cherry", "Zebra"), names(apps));
    }

    @Test
    public void sortsSplitNamesCaseInsensitively() {
        List<String> splits = new ArrayList<>(
                Arrays.asList("config.xxhdpi.apk", "config.EN.apk", "config.arm64_v8a.apk"));

        Collections.sort(splits, CompareUtils::compareByName);

        assertEquals(Arrays.asList("config.arm64_v8a.apk", "config.EN.apk", "config.xxhdpi.apk"),
                splits);
    }

    @Test
    public void sortingIsStableForEqualNames() {
        AppInfo first = app("Same");
        AppInfo second = app("same");
        List<AppInfo> apps = new ArrayList<>(Arrays.asList(first, second));

        Collections.sort(apps, CompareUtils::compareAppInfoByName);

        assertEquals("equal names must keep their original order", first, apps.get(0));
    }

    @Test
    public void comparesAgainstItself() {
        assertEquals(0, CompareUtils.compareByName("config.en.apk", "config.en.apk"));
        assertEquals(0, CompareUtils.compareAppInfoByName(app("Same"), app("Same")));
    }

    @Test
    public void picksTheRequestedTimestampField() {
        AppInfo app = app("Name");
        app.lastUpdated = 200L;
        app.firstInstall = 100L;

        assertEquals("sort mode 1 means last updated", 200L, app.lastUpdated);
        assertEquals("sort mode 0 means first install", 100L, app.firstInstall);
        assertTrue(new CompareUtils().getSortField(app, 1) == 200L);
        assertTrue(new CompareUtils().getSortField(app, 0) == 100L);
    }

    private static AppInfo app(String name) {
        return new AppInfo(name, null, "com.example." + name, 0L, 0L);
    }

    private static List<String> names(List<AppInfo> apps) {
        List<String> names = new ArrayList<>();
        for (AppInfo app : apps) {
            names.add(app.name);
        }
        return names;
    }
}