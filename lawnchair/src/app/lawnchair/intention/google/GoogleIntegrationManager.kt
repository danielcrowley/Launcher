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

package app.lawnchair.intention.google

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import app.lawnchair.intention.model.CalendarEvent
import app.lawnchair.intention.model.TaskItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Handles Google Sign-In and data fetching from Google Tasks and Google Calendar.
 *
 * ## Sign-in flow
 * 1. Call [buildSignInClient] and launch its [GoogleSignInClient.signInIntent] from an Activity.
 * 2. In `onActivityResult`, pass the result to [handleSignInResult].
 * 3. After sign-in, [fetchTodayData] can be called to load tasks and events.
 *
 * ## OAuth scopes required
 * - `TasksScopes.TASKS`       — read/write Google Tasks
 * - `CalendarScopes.CALENDAR_READONLY` — read Google Calendar events
 *
 * These must also be added to the OAuth consent screen in Google Cloud Console, and the
 * SHA-1 fingerprint of the debug/release keystore must be registered for the OAuth client ID.
 */
class GoogleIntegrationManager(private val context: Context) {

    companion object {
        private const val TAG = "GoogleIntegration"
        const val RC_SIGN_IN = 9001

        private val SCOPES = listOf(TasksScopes.TASKS, CalendarScopes.CALENDAR_READONLY)

        // Prefs key — stores the account email for re-use across sessions
        private const val PREFS_NAME = "intention_google_prefs"
        private const val PREF_ACCOUNT_EMAIL = "account_email"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Sign-In ─────────────────────────────────────────────────────────────

    fun buildSignInClient(activity: Activity): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                com.google.android.gms.common.api.Scope(TasksScopes.TASKS),
                com.google.android.gms.common.api.Scope(CalendarScopes.CALENDAR_READONLY),
            )
            .build()
        return GoogleSignIn.getClient(activity, gso)
    }

    fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            prefs.edit().putString(PREF_ACCOUNT_EMAIL, account.email).apply()
            Log.d(TAG, "Signed in as ${account.email}")
        }
    }

    fun isSignedIn(): Boolean =
        GoogleSignIn.getLastSignedInAccount(context) != null

    fun getSignedInEmail(): String? =
        prefs.getString(PREF_ACCOUNT_EMAIL, null)
            ?: GoogleSignIn.getLastSignedInAccount(context)?.email

    fun signOut(activity: Activity) {
        buildSignInClient(activity).signOut()
        prefs.edit().remove(PREF_ACCOUNT_EMAIL).apply()
    }

    // ── Google API credential ────────────────────────────────────────────────

    private fun buildCredential(accountEmail: String): GoogleAccountCredential =
        GoogleAccountCredential.usingOAuth2(context, SCOPES).apply {
            selectedAccount = Account(accountEmail, "com.google")
        }

    // ── Data fetching ────────────────────────────────────────────────────────

    /**
     * Fetches today's tasks (from ALL task lists) and calendar events (from ALL calendars).
     * Returns a [Pair] of ([List<TaskItem>], [List<CalendarEvent>]).
     *
     * Must be called from a coroutine; does network I/O on [Dispatchers.IO].
     */
    suspend fun fetchTodayData(accountEmail: String): Pair<List<TaskItem>, List<CalendarEvent>> =
        withContext(Dispatchers.IO) {
            val credential = buildCredential(accountEmail)
            val transport = NetHttpTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()

            val tasks = fetchTasks(credential, transport, jsonFactory)
            val events = fetchCalendarEvents(credential, transport, jsonFactory)
            Pair(tasks, events)
        }

    // ── Tasks ────────────────────────────────────────────────────────────────

    private fun fetchTasks(
        credential: GoogleAccountCredential,
        transport: NetHttpTransport,
        jsonFactory: GsonFactory,
    ): List<TaskItem> {
        val service = Tasks.Builder(transport, jsonFactory, credential)
            .setApplicationName("IntentionLauncher")
            .build()

        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

        val taskLists = service.tasklists().list().setMaxResults(20).execute().items
            ?: return emptyList()

        return taskLists.flatMap { list ->
            val items = service.tasks().list(list.id)
                .setShowCompleted(false)
                .setDueMin(startOfDay.toString()) // RFC 3339
                .setDueMax(endOfDay.toString())
                .setMaxResults(50)
                .execute()
                .items ?: emptyList()

            items.map { task ->
                TaskItem(
                    id = task.id,
                    title = task.title ?: "(no title)",
                    notes = task.notes,
                    completed = "completed" == task.status,
                    listId = list.id,
                    listTitle = list.title ?: "Tasks",
                )
            }
        }
    }

    // ── Calendar ─────────────────────────────────────────────────────────────

    private fun fetchCalendarEvents(
        credential: GoogleAccountCredential,
        transport: NetHttpTransport,
        jsonFactory: GsonFactory,
    ): List<CalendarEvent> {
        val service = Calendar.Builder(transport, jsonFactory, credential)
            .setApplicationName("IntentionLauncher")
            .build()

        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val startOfDay = today.atStartOfDay(zone)
        val endOfDay = today.plusDays(1).atStartOfDay(zone)

        val rfc3339Formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

        val calendarList = service.calendarList().list().execute().items ?: return emptyList()

        return calendarList.flatMap { cal ->
            val events = service.events().list(cal.id)
                .setTimeMin(com.google.api.client.util.DateTime(startOfDay.toInstant().toEpochMilli()))
                .setTimeMax(com.google.api.client.util.DateTime(endOfDay.toInstant().toEpochMilli()))
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .setMaxResults(20)
                .execute()
                .items ?: emptyList()

            events.mapNotNull { event ->
                val start = event.start ?: return@mapNotNull null
                val end = event.end ?: return@mapNotNull null

                val isAllDay = start.date != null
                val startZdt = if (isAllDay) {
                    today.atStartOfDay(zone)
                } else {
                    ZonedDateTime.parse(
                        start.dateTime?.toStringRfc3339() ?: return@mapNotNull null,
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                    )
                }
                val endZdt = if (isAllDay) {
                    today.plusDays(1).atStartOfDay(zone)
                } else {
                    ZonedDateTime.parse(
                        end.dateTime?.toStringRfc3339() ?: return@mapNotNull null,
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                    )
                }

                CalendarEvent(
                    id = event.id ?: return@mapNotNull null,
                    title = event.summary ?: "(no title)",
                    startTime = startZdt,
                    endTime = endZdt,
                    location = event.location,
                    calendarName = cal.summary ?: "Calendar",
                    isAllDay = isAllDay,
                )
            }
        }.sortedBy { it.startTime }
    }

    // ── Task completion ──────────────────────────────────────────────────────

    /**
     * Marks a Google Task as complete. Runs on [Dispatchers.IO].
     */
    suspend fun completeTask(accountEmail: String, listId: String, taskId: String) =
        withContext(Dispatchers.IO) {
            val credential = buildCredential(accountEmail)
            val service = Tasks.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("IntentionLauncher")
                .build()
            val task = service.tasks().get(listId, taskId).execute()
            task.status = "completed"
            service.tasks().update(listId, taskId, task).execute()
        }
}
