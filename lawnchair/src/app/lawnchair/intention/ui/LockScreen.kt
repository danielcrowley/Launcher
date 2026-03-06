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

package app.lawnchair.intention.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.intention.IntentionSessionManager
import app.lawnchair.intention.model.CalendarEvent
import app.lawnchair.intention.model.TaskItem
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The 20-second mandatory pause screen shown after every phone unlock (when no valid session
 * exists). The user can view and tick off today's tasks and meetings but cannot proceed until the
 * countdown finishes.
 *
 * @param tasks Today's Google Tasks.
 * @param events Today's Google Calendar events.
 * @param onTaskCompleted Called when the user ticks off a task (passes listId + taskId).
 * @param onCountdownFinished Called when the 20 s countdown ends — navigates to intention input.
 */
@Composable
fun LockScreen(
    tasks: List<TaskItem>,
    events: List<CalendarEvent>,
    isLoading: Boolean,
    onTaskCompleted: (listId: String, taskId: String) -> Unit,
    onCountdownFinished: () -> Unit,
) {
    // ── Countdown state ─────────────────────────────────────────────────────
    var remainingMs by remember {
        mutableLongStateOf(IntentionSessionManager.countdownRemainingMs())
    }
    val totalMs = IntentionSessionManager.COUNTDOWN_DURATION_MS.toFloat()
    val progress by animateFloatAsState(
        targetValue = remainingMs / totalMs,
        animationSpec = tween(durationMillis = 200),
        label = "countdown_progress",
    )

    LaunchedEffect(Unit) {
        IntentionSessionManager.startCountdownIfNotStarted()
        while (remainingMs > 0L) {
            delay(100L)
            remainingMs = IntentionSessionManager.countdownRemainingMs()
        }
        IntentionSessionManager.onCountdownFinished()
        onCountdownFinished()
    }

    val countdownFinished = remainingMs == 0L

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            // ── Header: date + countdown ring ───────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = java.time.LocalDate.now()
                            .format(DateTimeFormatter.ofPattern("EEEE")),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = java.time.LocalDate.now()
                            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Countdown ring
                Box(contentAlignment = Alignment.Center) {
                    val ringColor by animateColorAsState(
                        targetValue = if (countdownFinished) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary,
                        label = "ring_color",
                    )
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(72.dp),
                        color = ringColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 6.dp,
                    )
                    Text(
                        text = if (countdownFinished) "✓"
                        else "${(remainingMs / 1000L) + 1}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = if (countdownFinished) "Take a breath. Ready to continue?" else "Take a moment…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            // ── Content: tasks + events ─────────────────────────────────────
            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (events.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Today's meetings")
                        }
                        items(events, key = { it.id }) { event ->
                            CalendarEventCard(event = event)
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    if (tasks.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Today's tasks")
                        }
                        items(tasks, key = { it.id }) { task ->
                            TaskItemRow(task = task, onCompleted = {
                                onTaskCompleted(task.listId, task.id)
                            })
                        }
                    }

                    if (events.isEmpty() && tasks.isEmpty()) {
                        item {
                            EmptyDayMessage()
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── CTA: proceed to intention input ─────────────────────────────
            Button(
                onClick = onCountdownFinished,
                enabled = countdownFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    text = if (countdownFinished) "What do you need the phone for?" else "Please wait…",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun CalendarEventCard(event: CalendarEvent) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val timeText = if (event.isAllDay) "All day" else
                    "${event.startTime.format(timeFormatter)} – ${event.endTime.format(timeFormatter)}"
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (!event.location.isNullOrBlank()) {
                    Text(
                        text = event.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskItemRow(task: TaskItem, onCompleted: () -> Unit) {
    var locallyCompleted by remember(task.id) { mutableStateOf(task.completed) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                if (!locallyCompleted) {
                    locallyCompleted = true
                    onCompleted()
                }
            },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (locallyCompleted) Icons.Filled.CheckCircle
                else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (locallyCompleted) "Completed" else "Mark complete",
                tint = if (locallyCompleted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (locallyCompleted) TextDecoration.LineThrough else null,
                color = if (locallyCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!task.notes.isNullOrBlank()) {
                Text(
                    text = task.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyDayMessage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Nothing scheduled for today",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Use this time to set your intention for the day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
