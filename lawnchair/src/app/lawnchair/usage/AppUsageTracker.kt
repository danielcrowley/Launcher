package app.lawnchair.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Queries Android's UsageStatsManager to return a map of packageName → total foreground
 * time (ms) over the past 30 days. This is used as a proxy for launch frequency so that
 * frequently-used apps rank higher in app drawer search results.
 *
 * Requires the PACKAGE_USAGE_STATS permission (granted by the user in Settings →
 * Special app access → Usage access). If the permission is missing or no data is
 * available, an empty map is returned so existing search ranking is unaffected.
 */
object AppUsageTracker {

    private const val WINDOW_DAYS = 30L

    fun getLaunchCounts(context: Context): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.DAYS.toMillis(WINDOW_DAYS)

        return try {
            usm.queryAndAggregateUsageStats(startTime, endTime)
                .mapValues { it.value.totalTimeInForeground }
        } catch (_: SecurityException) {
            emptyMap()
        }
    }
}
