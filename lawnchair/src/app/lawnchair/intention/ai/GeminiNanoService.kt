/*
 * Copyright 2024, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.intention.ai

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import app.lawnchair.intention.model.AppSuggestion
import com.google.ai.edge.aicore.GenerationConfig
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uses on-device Gemini Nano (via Android AICore) to map the user's stated intention to a
 * curated list of installed apps.
 *
 * ## Requirements
 * - Android 14 (API 34) or higher
 * - AICore service installed (standard on Pixel 6+ running Android 14+)
 * - The `com.google.ai.edge.aicore:aicore` dependency in build.gradle
 *
 * ## Fallback
 * If the on-device model is unavailable (older device, AICore not installed), [mapIntentionToApps]
 * falls back to a simple keyword-matching heuristic via [KeywordAppMatcher].
 */
class GeminiNanoService(private val context: Context) {

    companion object {
        private const val TAG = "GeminiNanoService"
        private const val MAX_OUTPUT_TOKENS = 512
    }

    private val generationConfig: GenerationConfig = generationConfig {
        temperature = 0.2f
        topK = 16
        maxOutputTokens = MAX_OUTPUT_TOKENS
    }

    /**
     * Maps the user's [intention] to a ranked list of [AppSuggestion]s.
     *
     * Tries Gemini Nano first, falls back to keyword matching if unavailable.
     */
    suspend fun mapIntentionToApps(intention: String): List<AppSuggestion> =
        withContext(Dispatchers.Default) {
            val installedApps = getInstalledUserApps()
            if (installedApps.isEmpty()) return@withContext emptyList()

            try {
                mapWithGeminiNano(intention, installedApps)
            } catch (e: Exception) {
                Log.w(TAG, "Gemini Nano unavailable, falling back to keyword matching: ${e.message}")
                KeywordAppMatcher.match(intention, installedApps)
            }
        }

    private suspend fun mapWithGeminiNano(
        intention: String,
        apps: List<AppInfo>,
    ): List<AppSuggestion> {
        val model = GenerativeModel(
            modelName = "gemini-nano",
            generationConfig = generationConfig,
        )

        val appListText = apps.joinToString("\n") { "- ${it.label} (${it.packageName})" }

        val prompt = """
            You are helping the user of an intentional phone launcher. The user has just unlocked
            their phone and stated why they want to use it.

            User's intention: "$intention"

            Installed apps:
            $appListText

            Instructions:
            - Choose ONLY the apps that are directly relevant to the user's intention.
            - Return a maximum of 6 apps.
            - For each app return exactly one line in this format:
              PACKAGE_NAME | Short reason (max 8 words)
            - Return only the list, no preamble or explanation.
            - If no apps are relevant, return exactly: NONE
        """.trimIndent()

        val response = model.generateContent(prompt)
        val text = response.text ?: return emptyList()

        if (text.trim() == "NONE") return emptyList()

        return text.lines()
            .mapNotNull { line ->
                val parts = line.split("|", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val pkg = parts[0].trim()
                val reason = parts[1].trim()
                val app = apps.firstOrNull { it.packageName == pkg } ?: return@mapNotNull null
                AppSuggestion(packageName = pkg, appLabel = app.label, reason = reason)
            }
    }

    private fun getInstalledUserApps(): List<AppInfo> {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { resolve ->
                AppInfo(
                    packageName = resolve.activityInfo.packageName,
                    label = resolve.loadLabel(pm).toString(),
                )
            }
            .sortedBy { it.label }
    }

    data class AppInfo(val packageName: String, val label: String)
}

/**
 * Simple keyword-based fallback when Gemini Nano is unavailable.
 * Maps common intention keywords to app categories.
 */
private object KeywordAppMatcher {

    private val CATEGORY_KEYWORDS = mapOf(
        listOf("message", "chat", "text", "whatsapp", "signal", "telegram", "sms") to
            listOf("com.whatsapp", "org.thoughtcrime.securesms", "org.telegram.messenger",
                "com.google.android.apps.messaging"),
        listOf("email", "mail", "inbox", "gmail") to
            listOf("com.google.android.gm", "com.microsoft.office.outlook"),
        listOf("map", "navigate", "directions", "drive", "route", "uber", "lyft") to
            listOf("com.google.android.apps.maps", "com.uber.client", "lyft.android"),
        listOf("music", "spotify", "podcast", "listen", "play") to
            listOf("com.spotify.music", "com.google.android.apps.youtube.music",
                "com.google.android.apps.podcasts"),
        listOf("news", "read", "article", "reddit", "twitter", "x.com") to
            listOf("com.twitter.android", "com.reddit.frontpage", "com.google.android.apps.magazines"),
        listOf("photo", "camera", "picture", "snap", "instagram") to
            listOf("com.google.android.GoogleCamera", "com.instagram.android",
                "com.snapchat.android"),
        listOf("pay", "bank", "money", "transfer", "finance") to
            listOf("com.google.android.apps.walletnfcrel", "com.revolut.revolut",
                "uk.co.hsbc.hsbcukmobilebanking"),
        listOf("work", "task", "todo", "calendar", "meet", "zoom", "slack") to
            listOf("com.google.android.apps.meetings", "us.zoom.videomeetings",
                "com.Slack", "com.google.android.calendar"),
        listOf("shop", "amazon", "order", "buy") to
            listOf("com.amazon.mShop.android.shopping"),
        listOf("youtube", "video", "watch") to
            listOf("com.google.android.youtube"),
    )

    fun match(intention: String, apps: List<GeminiNanoService.AppInfo>): List<AppSuggestion> {
        val lower = intention.lowercase()
        val installedPackages = apps.associateBy { it.packageName }

        val matched = CATEGORY_KEYWORDS.entries
            .filter { (keywords, _) -> keywords.any { lower.contains(it) } }
            .flatMap { (_, packages) -> packages }
            .distinct()
            .mapNotNull { pkg ->
                val app = installedPackages[pkg] ?: return@mapNotNull null
                AppSuggestion(
                    packageName = pkg,
                    appLabel = app.label,
                    reason = "Matches your intention",
                )
            }
            .take(6)

        return matched
    }
}
