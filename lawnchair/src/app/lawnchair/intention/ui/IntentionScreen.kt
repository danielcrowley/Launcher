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

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lawnchair.intention.model.AppSuggestion
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * The intention input screen, shown after the 20 s lock screen countdown ends.
 * The user types why they want to use their phone; Gemini Nano maps this to a curated app list.
 *
 * States:
 * - [IntentionUiState.Input] — text field, waiting for submission.
 * - [IntentionUiState.Loading] — AI is processing the intention.
 * - [IntentionUiState.AppGrid] — AI has returned app suggestions; user can tap to launch.
 * - [IntentionUiState.Error] — AI failed; shows fallback message.
 */
@Composable
fun IntentionScreen(
    uiState: IntentionUiState,
    intentionText: String,
    onIntentionChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onEditIntention: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "intention_state",
        ) { state ->
            when (state) {
                is IntentionUiState.Input -> InputContent(
                    intentionText = intentionText,
                    onIntentionChanged = onIntentionChanged,
                    onSubmit = {
                        keyboard?.hide()
                        onSubmit()
                    },
                )

                is IntentionUiState.Loading -> LoadingContent()

                is IntentionUiState.AppGrid -> AppGridContent(
                    intention = state.intention,
                    apps = state.apps,
                    onLaunchApp = onLaunchApp,
                    onEditIntention = onEditIntention,
                )

                is IntentionUiState.Error -> ErrorContent(
                    message = state.message,
                    onRetry = onEditIntention,
                )
            }
        }
    }
}

// ── State ────────────────────────────────────────────────────────────────────

sealed interface IntentionUiState {
    data object Input : IntentionUiState
    data object Loading : IntentionUiState
    data class AppGrid(val intention: String, val apps: List<AppSuggestion>) : IntentionUiState
    data class Error(val message: String) : IntentionUiState
}

// ── Sub-screens ───────────────────────────────────────────────────────────────

@Composable
private fun InputContent(
    intentionText: String,
    onIntentionChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Why do you need the phone?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Be specific — the more detail you give, the better the app suggestions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = intentionText,
            onValueChange = onIntentionChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. Check if my order has shipped") },
            shape = RoundedCornerShape(16.dp),
            singleLine = false,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (intentionText.isNotBlank()) onSubmit() }),
            trailingIcon = {
                IconButton(
                    onClick = { if (intentionText.isNotBlank()) onSubmit() },
                    enabled = intentionText.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Submit intention")
                }
            },
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onSubmit,
            enabled = intentionText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Show me what I need")
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Working out what you need…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppGridContent(
    intention: String,
    apps: List<AppSuggestion>,
    onLaunchApp: (String) -> Unit,
    onEditIntention: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "Apps for: "$intention"",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap an app to open it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No specific apps found for that intention.\nTry being more specific.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppIcon(app = app, onClick = { onLaunchApp(app.packageName) })
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onEditIntention,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Change intention")
        }
    }
}

@Composable
private fun AppIcon(app: AppSuggestion, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon: Drawable? = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName)
        }.getOrNull()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        if (icon != null) {
            Image(
                painter = rememberDrawablePainter(drawable = icon),
                contentDescription = app.appLabel,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {}
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.appLabel,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}
