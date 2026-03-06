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

package app.lawnchair.intention.model

/**
 * Represents a completed intention session. A session is valid for [SESSION_TTL_MS] after the
 * user stated their intention. If the screen is off for longer than [SESSION_TTL_MS], the user
 * must state their intention again on next unlock.
 */
data class IntentionSession(
    val statedIntention: String,
    val suggestedApps: List<AppSuggestion>,
    val startedAtMs: Long,
) {
    companion object {
        /** 5 minutes — re-prompt if screen has been off for longer than this. */
        const val SESSION_TTL_MS = 5 * 60 * 1000L
    }
}
