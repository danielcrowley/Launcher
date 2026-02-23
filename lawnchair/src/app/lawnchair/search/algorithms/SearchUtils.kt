package app.lawnchair.search.algorithms

import android.content.Context
import android.content.pm.ShortcutInfo
import app.lawnchair.launcher
import app.lawnchair.ui.preferences.components.HiddenAppsInSearch
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.popup.PopupPopulator
import com.android.launcher3.search.StringMatcherUtility
import com.android.launcher3.shortcuts.ShortcutRequest
import java.util.Locale
import me.xdrop.fuzzywuzzy.FuzzySearch
import me.xdrop.fuzzywuzzy.algorithms.WeightedRatio

object SearchUtils {
    /**
     * @param usageCounts  packageName → total foreground time (ms) from AppUsageTracker.
     *                     Passing an empty map falls back to the original unweighted behaviour.
     */
    fun normalSearch(
        apps: List<AppInfo>,
        query: String,
        maxResultsCount: Int,
        hiddenApps: Set<String>,
        hiddenAppsInSearch: String,
        usageCounts: Map<String, Long> = emptyMap(),
    ): List<AppInfo> {
        // Filter to apps whose titles contain all words in the query, then rank
        // the matches by usage frequency so the most-used apps surface first.
        val queryTextLower = query.lowercase(Locale.getDefault())
        val matcher = StringMatcherUtility.StringMatcher.getInstance()
        return apps.asSequence()
            .filter { StringMatcherUtility.matches(queryTextLower, it.title.toString(), matcher) }
            .filterHiddenApps(queryTextLower, hiddenApps, hiddenAppsInSearch)
            .sortedByDescending { usageCounts[it.componentName?.packageName] ?: 0L }
            .take(maxResultsCount)
            .toList()
    }

    /**
     * @param usageCounts  packageName → total foreground time (ms) from AppUsageTracker.
     *                     When provided, the final rank blends 70 % fuzzy score + 30 % usage
     *                     score (normalised 0–100 relative to the top result in the set).
     *                     Passing an empty map falls back to pure fuzzy ranking.
     */
    fun fuzzySearch(
        apps: List<AppInfo>,
        query: String,
        maxResultsCount: Int,
        hiddenApps: Set<String>,
        hiddenAppsInSearch: String,
        usageCounts: Map<String, Long> = emptyMap(),
    ): List<AppInfo> {
        val queryTextLower = query.lowercase(Locale.getDefault())
        val filteredApps = apps.asSequence()
            .filterHiddenApps(queryTextLower, hiddenApps, hiddenAppsInSearch)
            .toList()
        val matches = FuzzySearch.extractSorted(
            queryTextLower,
            filteredApps,
            { it.sectionName + it.title },
            WeightedRatio(),
            65,
        )

        if (usageCounts.isEmpty()) {
            return matches.take(maxResultsCount).map { it.referent }
        }

        // Normalise usage counts relative to the highest-scored candidate so the
        // usage contribution stays in the same 0–100 range as the fuzzy score.
        val maxUsage = matches
            .mapNotNull { usageCounts[it.referent.componentName?.packageName] }
            .maxOrNull()
            ?.takeIf { it > 0L } ?: 1L

        return matches
            .map { result ->
                val usageScore = (usageCounts[result.referent.componentName?.packageName] ?: 0L)
                    .toDouble() / maxUsage * 100.0
                val blended = 0.7 * result.score + 0.3 * usageScore
                Pair(result.referent, blended)
            }
            .sortedByDescending { it.second }
            .take(maxResultsCount)
            .map { it.first }
    }

    fun getShortcuts(app: AppInfo, context: Context): List<ShortcutInfo> {
        val shortcuts = ShortcutRequest(context.launcher, app.user)
            .withContainer(app.targetComponent)
            .query(ShortcutRequest.PUBLISHED)
        return PopupPopulator.sortAndFilterShortcuts(shortcuts)
    }
}

fun Sequence<AppInfo>.filterHiddenApps(
    query: String,
    hiddenApps: Set<String>,
    hiddenAppsInSearch: String,
): Sequence<AppInfo> {
    return when (hiddenAppsInSearch) {
        HiddenAppsInSearch.ALWAYS -> {
            this
        }

        HiddenAppsInSearch.IF_NAME_TYPED -> {
            filter {
                it.toComponentKey().toString() !in hiddenApps ||
                    it.title.toString().lowercase(Locale.getDefault()) == query
            }
        }

        else -> {
            filter { it.toComponentKey().toString() !in hiddenApps }
        }
    }
}
