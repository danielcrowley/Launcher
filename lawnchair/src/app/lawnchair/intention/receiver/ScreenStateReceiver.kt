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

package app.lawnchair.intention.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.lawnchair.intention.IntentionSessionManager

/**
 * Listens for screen state changes to manage [IntentionSessionManager] state.
 *
 * - [Intent.ACTION_SCREEN_OFF] — records when the screen turned off for TTL calculation.
 * - [Intent.ACTION_USER_PRESENT] — fires after the biometric/PIN lock screen is dismissed.
 *   This is the signal that the user has unlocked the device and our intention gate should run.
 *
 * Registered dynamically in [LawnchairApp] because ACTION_SCREEN_OFF/USER_PRESENT cannot
 * be received by statically declared receivers.
 */
class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenStateReceiver"

        val INTENT_FILTER = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen off — recording timestamp for TTL")
                IntentionSessionManager.onScreenOff()
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present (unlocked) — resetting countdown if session expired")
                IntentionSessionManager.onScreenUnlocked()
            }
        }
    }
}
