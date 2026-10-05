package fork.MaxrregMustermann.AntiSplitNG.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import fork.MaxrregMustermann.AntiSplitNG.utils.CompareUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Filtering and sorting the installed-apps list.
 *
 * <p>The lowercasing matters more than it looks: with a Turkish device locale the default locale
 * lowercases "I" to a dotless one, which would make "Instagram" unfindable by typing "instagram".
 */
@RunWith(RobolectricTestRunner.class)
public class AppListArrayAdapterTest {

    private Locale originalLocale;

    @Before
    public void rememberLocale() {
        originalLocale = Locale.getDefault();
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    private static List<AppInfo> apps(String... names) {
        List<AppInfo> apps = new ArrayList<>();
        for (String name : names) {
            apps.add(new AppInfo(name, null, "com.example." + name, 0L, 0L));
        }
        return apps;
    }

    private AppListArrayAdapter adapterFor(List<AppInfo> apps) {
        Context context = ApplicationProvider.getApplicationContext();
        return new AppListArrayAdapter(context, context.getResources(), apps, true);
    }

    private List<String> filteredNames(AppListArrayAdapter adapter, String query) {
        adapter.getFilter().filter(query);
        List<String> names = new ArrayList<>();
        for (AppInfo app : adapter.filteredAppInfoList) {
            names.add(app.name);
        }
        return names;
    }

    @Test
    public void showsEverythingWhenTheSearchIsEmpty() {
        AppListArrayAdapter adapter = adapterFor(apps("Alpha", "Beta", "Gamma"));

        assertEquals(3, adapter.filteredAppInfoList.size());
        assertEquals(List.of("Alpha", "Beta", "Gamma"), filteredNames(adapter, ""));
        assertEquals(List.of("Alpha", "Beta", "Gamma"), filteredNames(adapter, null));
    }

    @Test
    public void matchesOnAppName() {
        AppListArrayAdapter adapter = adapterFor(apps("Alpha", "Beta", "Gamma"));

        assertEquals(List.of("Beta"), filteredNames(adapter, "Bet"));
        assertEquals(List.of("Alpha"), filteredNames(adapter, "alpha"));
    }

    @Test
    public void matchesOnPackageName() {
        AppListArrayAdapter adapter = adapterFor(apps("Alpha", "Beta"));

        assertEquals(List.of("Alpha"), filteredNames(adapter, "com.example.Alph"));
    }

    @Test
    public void ignoresSurroundingWhitespace() {
        AppListArrayAdapter adapter = adapterFor(apps("Alpha", "Beta"));

        assertEquals(List.of("Alpha"), filteredNames(adapter, "  alp  "));
    }

    @Test
    public void matchingIsIndependentOfTheDeviceLocale() {
        Locale.setDefault(new Locale("tr", "TR"));
        AppListArrayAdapter adapter = adapterFor(apps("Instagram", "InShot"));

        // With the Turkish locale a default toLowerCase would map "I" to "ı" and miss both.
        assertEquals(List.of("Instagram"), filteredNames(adapter, "instagram"));
        assertEquals(List.of("InShot"), filteredNames(adapter, "inshot"));
    }

    @Test
    public void sortingIsAlsoLocaleIndependent() {
        Locale.setDefault(new Locale("tr", "TR"));
        List<AppInfo> apps = apps("istanbul", "Izmir", "Istanbul");

        apps.sort(CompareUtils::compareAppInfoByName);

        assertTrue("the list must be sorted the same way everywhere",
                apps.get(0).name.equals("istanbul"));
    }

    @Test
    public void aQueryThatMatchesNothingClearsTheList() {
        AppListArrayAdapter adapter = adapterFor(apps("Alpha", "Beta"));

        assertTrue(filteredNames(adapter, "zzzz").isEmpty());
        assertEquals("the list recovers when the query is cleared",
                List.of("Alpha", "Beta"), filteredNames(adapter, ""));
    }
}