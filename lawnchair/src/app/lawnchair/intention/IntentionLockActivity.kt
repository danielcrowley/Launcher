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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.intention.google.GoogleIntegrationManager
import app.lawnchair.intention.model.CalendarEvent
import app.lawnchair.intention.model.TaskItem
import app.lawnchair.intention.ui.LockScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Mandatory 20-second pause screen shown after every phone unlock when no valid
 * [IntentionSession] exists.
 *
 * ## Behaviour
 * - The user CANNOT dismiss this screen using Back (overridden).
 * - Pressing Home returns to [LawnchairLauncher], which immediately re-launches this Activity if
 *   the session is still invalid — so the 20 s timer cannot be circumvented.
 * - The timer state lives in [IntentionSessionManager], which persists across Activity restarts
 *   within the same wake cycle.
 * - Once the countdown finishes, a "proceed" button becomes active, launching
 *   [IntentionInputActivity].
 * - Google sign-in is triggered here if the user has not yet authenticated.
 */
class IntentionLockActivity : ComponentActivity() {

    companion object {
        private const val TAG = "IntentionLockActivity"

        fun newIntent(context: android.content.Context) =
            Intent(context, IntentionLockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
    }

    private val viewModel: LockViewModel by viewModels()

    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                    viewModel.onSignInSuccess(account)
                } catch (e: ApiException) {
                    Log.w(TAG, "Sign-in failed: ${e.message}")
                    viewModel.onSignInFailed()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the user already has a valid session, skip straight back to the launcher.
        if (IntentionSessionManager.hasValidSession()) {
            Log.d(TAG, "Valid session exists — skipping lock screen")
            finish()
            return
        }

        viewModel.init(this)

        // Trigger Google sign-in if not yet authenticated.
        if (!viewModel.googleManager.isSignedIn()) {
            val client = viewModel.googleManager.buildSignInClient(this)
            signInLauncher.launch(client.signInIntent)
        } else {
            viewModel.loadTodayData()
        }

        setContent {
            val tasks by viewModel.tasks.collectAsState()
            val events by viewModel.events.collectAsState()
            val isLoading by viewModel.isLoading.collectAsState()

            // Wraps in Lawnchair's theme automatically via the application theme.
            LockScreen(
                tasks = tasks,
                events = events,
                isLoading = isLoading,
                onTaskCompleted = { listId, taskId ->
                    viewModel.completeTask(listId, taskId)
                },
                onCountdownFinished = {
                    startActivity(IntentionInputActivity.newIntent(this@IntentionLockActivity))
                    // Don't finish — the launcher will handle routing on return.
                },
            )
        }
    }

    /** Prevent the user from dismissing the lock screen with Back. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intentionally suppressed — Back does nothing on the lock screen.
    }
}

// ── ViewModel ────────────────────────────────────────────────────────────────

class LockViewModel : ViewModel() {

    lateinit var googleManager: GoogleIntegrationManager
        private set

    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun init(context: android.content.Context) {
        if (!::googleManager.isInitialized) {
            googleManager = GoogleIntegrationManager(context.applicationContext)
        }
    }

    fun onSignInSuccess(account: GoogleSignInAccount) {
        googleManager.handleSignInResult(account)
        loadTodayData()
    }

    fun onSignInFailed() {
        // Sign-in failed — load screen without data.
        _isLoading.value = false
    }

    fun loadTodayData() {
        val email = googleManager.getSignedInEmail() ?: return
        _isLoading.value = true
        viewModelScope.launch {
            runCatching {
                val (tasks, events) = googleManager.fetchTodayData(email)
                _tasks.value = tasks
                _events.value = events
            }.onFailure { e ->
                Log.e("LockViewModel", "Failed to fetch today's data", e)
            }
            _isLoading.value = false
        }
    }

    fun completeTask(listId: String, taskId: String) {
        val email = googleManager.getSignedInEmail() ?: return
        // Optimistic: the UI already updates locally in the composable.
        viewModelScope.launch {
            runCatching {
                googleManager.completeTask(email, listId, taskId)
            }.onFailure { e ->
                Log.e("LockViewModel", "Failed to complete task $taskId", e)
            }
        }
    }
}
