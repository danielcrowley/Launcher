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

package app.lawnchair.intention

import app.lawnchair.intention.model.AppSuggestion
import app.lawnchair.intention.model.IntentionSession

/**
 * Singleton that manages the current [IntentionSession].
 *
 * Session lifecycle:
 * - Created when the user completes the intention input after the 20 s lock screen.
 * - Valid for [IntentionSession.SESSION_TTL_MS] (5 min) from [lastScreenOffAtMs].
 * - Invalidated when the screen turns off for longer than the TTL, or at the start of a new day.
 *
 * The 20 s countdown state is also tracked here so that pressing Home during the countdown does
 * not reset the timer. [LawnchairLauncher] checks this on every [onResume] and re-routes to
 * [IntentionLockActivity] if the session is invalid.
 */
object IntentionSessionManager {

    // ── Active session ──────────────────────────────────────────────────────

    @Volatile private var activeSession: IntentionSession? = null

    /** True if the user has a valid intention session and may use the full phone. */
    fun hasValidSession(): Boolean {
        val session = activeSession ?: return false
        val elapsed = System.currentTimeMillis() - lastScreenOffAtMs
        return elapsed < IntentionSession.SESSION_TTL_MS
    }

    fun getActiveSession(): IntentionSession? = if (hasValidSession()) activeSession else null

    fun startSession(intention: String, apps: List<AppSuggestion>) {
        activeSession = IntentionSession(
            statedIntention = intention,
            suggestedApps = apps,
            startedAtMs = System.currentTimeMillis(),
        )
        countdownFinished = true
    }

    fun clearSession() {
        activeSession = null
        countdownFinished = false
    }

    // ── Screen-off timestamp (for TTL calculation) ──────────────────────────

    /**
     * Updated by [ScreenStateReceiver] when ACTION_SCREEN_OFF fires.
     * If the screen is off for longer than SESSION_TTL_MS, the session is invalid on next wake.
     */
    @Volatile var lastScreenOffAtMs: Long = System.currentTimeMillis()
        private set

    fun onScreenOff() {
        lastScreenOffAtMs = System.currentTimeMillis()
    }

    // ── 20 s countdown state ────────────────────────────────────────────────

    /**
     * When the countdown started (epoch ms). Persists across Home presses because the
     * launcher redirects back to [IntentionLockActivity] if the session is invalid, and the
     * activity reads [countdownStartedAtMs] to resume from where it left off.
     */
    @Volatile var countdownStartedAtMs: Long = 0L
        private set

    /**
     * Whether the 20 s countdown has finished. Set to true once the timer expires, allowing
     * the user to proceed to intention input.
     */
    @Volatile var countdownFinished: Boolean = false
        private set

    /** Called once when [IntentionLockActivity] first appears for this wake cycle. */
    fun startCountdownIfNotStarted() {
        if (countdownStartedAtMs == 0L || countdownFinished) {
            countdownStartedAtMs = System.currentTimeMillis()
            countdownFinished = false
        }
    }

    fun onCountdownFinished() {
        countdownFinished = true
    }

    /** Remaining countdown milliseconds (0 if already done). */
    fun countdownRemainingMs(): Long {
        if (countdownFinished) return 0L
        val elapsed = System.currentTimeMillis() - countdownStartedAtMs
        return maxOf(0L, COUNTDOWN_DURATION_MS - elapsed)
    }

    /** Called by [ScreenStateReceiver] on ACTION_USER_PRESENT. Resets countdown for this wake. */
    fun onScreenUnlocked() {
        // Only reset the countdown if the session has expired; otherwise keep the valid session.
        if (!hasValidSession()) {
            countdownStartedAtMs = 0L
            countdownFinished = false
        }
    }

    companion object {
        const val COUNTDOWN_DURATION_MS = 20_000L
    }
}
