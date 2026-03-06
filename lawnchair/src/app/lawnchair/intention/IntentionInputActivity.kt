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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.intention.ai.GeminiNanoService
import app.lawnchair.intention.model.AppSuggestion
import app.lawnchair.intention.ui.IntentionScreen
import app.lawnchair.intention.ui.IntentionUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Shown after the 20 s lock screen countdown finishes. The user types their intention;
 * Gemini Nano maps it to a curated list of apps. Tapping an app launches it directly.
 *
 * Once the user submits a valid intention, [IntentionSessionManager.startSession] is called,
 * giving them a 5-minute session window where re-unlocking skips the lock screen.
 *
 * ## Back behaviour
 * Back returns to [IntentionLockActivity]. Since [LawnchairLauncher] will re-gate on resume,
 * the user cannot circumvent the intention step.
 */
class IntentionInputActivity : ComponentActivity() {

    companion object {
        private const val TAG = "IntentionInputActivity"

        fun newIntent(context: Context) =
            Intent(context, IntentionInputActivity::class.java)
    }

    private val viewModel: IntentionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.init(this)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            var intentionText by remember { mutableStateOf("") }

            IntentionScreen(
                uiState = uiState,
                intentionText = intentionText,
                onIntentionChanged = { intentionText = it },
                onSubmit = { viewModel.submitIntention(intentionText) },
                onLaunchApp = { packageName -> launchApp(packageName) },
                onEditIntention = {
                    intentionText = ""
                    viewModel.resetToInput()
                },
            )
        }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Log.w(TAG, "No launch intent for $packageName")
        }
    }
}

// ── ViewModel ────────────────────────────────────────────────────────────────

class IntentionViewModel : ViewModel() {

    private lateinit var geminiService: GeminiNanoService

    private val _uiState = MutableStateFlow<IntentionUiState>(IntentionUiState.Input)
    val uiState: StateFlow<IntentionUiState> = _uiState

    fun init(context: Context) {
        if (!::geminiService.isInitialized) {
            geminiService = GeminiNanoService(context.applicationContext)
        }
    }

    fun submitIntention(intention: String) {
        if (intention.isBlank()) return
        _uiState.value = IntentionUiState.Loading

        viewModelScope.launch {
            runCatching {
                val apps = geminiService.mapIntentionToApps(intention.trim())
                // Register the session so future unlocks skip the gate for 5 min.
                IntentionSessionManager.startSession(intention.trim(), apps)
                _uiState.value = IntentionUiState.AppGrid(
                    intention = intention.trim(),
                    apps = apps,
                )
            }.onFailure { e ->
                Log.e("IntentionViewModel", "AI mapping failed", e)
                _uiState.value = IntentionUiState.Error(
                    message = "Couldn't map your intention to apps. ${e.localizedMessage ?: "Unknown error."}",
                )
            }
        }
    }

    fun resetToInput() {
        _uiState.value = IntentionUiState.Input
    }
}
