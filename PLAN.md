# Custom Launcher — Implementation Plan

## Overview

Build a custom Android launcher on top of **Lawnchair 16-dev** with two features:
1. **Phase 1 (now):** App drawer search ranks results by most-frequently-used apps
2. **Phase 2 (future):** Delayed unlock screen that shows a todo list before allowing home screen access

---

## Phase 1: Project Bootstrap + Usage-Weighted Search

### Step 1 — Clone Lawnchair as the base

```
git clone --depth=1 --branch 16-dev \
  https://github.com/LawnchairLauncher/lawnchair.git .
```

- Bring all Lawnchair source into this repo directly (not a submodule)
- Re-point `origin` to our remote; add `upstream` pointing to LawnchairLauncher
- Make an initial "base: import Lawnchair 16-dev" commit

### Step 2 — Create `AppUsageTracker.kt`

**Location:** `lawnchair/src/app/lawnchair/usage/AppUsageTracker.kt`

Uses Android's `UsageStatsManager` API to return a `Map<String, Long>` of
`packageName → launchCount` for the past 30 days.

```kotlin
@Singleton
class AppUsageTracker @Inject constructor(@ApplicationContext ctx: Context) {
    fun getLaunchCounts(): Map<String, Long> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.DAYS.toMillis(30)
        return usm.queryAndAggregateUsageStats(start, end)
            .mapValues { it.value.totalTimeInForeground }  // use time as proxy for freq
    }
}
```

> Fallback: if `UsageStatsManager` returns empty (permission not granted), usage scores default to 0 so existing alphabetical/fuzzy logic is unaffected.

### Step 3 — Add permission to `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
```

A runtime settings-intent prompt will be added so users can grant it on first launch.

### Step 4 — Modify `SearchUtils.kt`

**Location:** `lawnchair/src/app/lawnchair/search/algorithms/SearchUtils.kt`

Add a new parameter `usageCounts: Map<String, Long>` to both `normalSearch` and `fuzzySearch`.

**`normalSearch` change:**
After filtering by `StringMatcherUtility.matches()`, sort results descending by `usageCounts[pkg] ?: 0` before returning.

**`fuzzySearch` change:**
FuzzySearch already returns a scored list. Apply a blended score:
```
finalScore = (0.7 * fuzzyScore) + (0.3 * normalizedUsageScore)
```
Where `normalizedUsageScore` is the usage count scaled 0–100 relative to the max count in the result set. Re-sort by `finalScore` descending.

### Step 5 — Wire up `AppUsageTracker` in `LawnchairAppSearchAlgorithm.kt`

Inject `AppUsageTracker` (Dagger/Hilt) and pass `getLaunchCounts()` into both `SearchUtils` calls.

### Step 6 — Build validation

```
./gradlew :lawnchair:assembleDebug
```

Fix any Kotlin/Java compilation issues introduced by our changes.

---

## Phase 2 (Future): Delayed Unlock Screen with Todo List

*This is documented here for architectural awareness — not implemented now.*

### Concept

When the user unlocks the device, instead of immediately showing the home screen, an overlay activity is presented that:
- Shows the user's todo list (sourced from a local Room database or a pluggable provider)
- Has a configurable delay (e.g. 10 seconds) with a countdown indicator
- Allows the user to tap "Done" early or wait for the timer

### Key components to create later

| File | Purpose |
|------|---------|
| `overlay/DelayedUnlockOverlay.kt` | Full-screen `Activity` with `FLAG_SHOW_WHEN_LOCKED` |
| `overlay/UnlockDelayPreference.kt` | Settings screen for configuring delay duration |
| `todo/TodoRepository.kt` | Room-based todo storage with CRUD |
| `todo/TodoItem.kt` | Room entity |
| `todo/TodoViewModel.kt` | ViewModel for the overlay |
| `todo/TodoListView.kt` | Composable list shown in the overlay |

### Manifest hook

```xml
<activity android:name=".overlay.DelayedUnlockOverlay"
    android:showWhenLocked="true"
    android:turnScreenOn="true"
    android:excludeFromRecents="true" />
```

The launcher listens to `ACTION_USER_PRESENT` broadcast to trigger the overlay after keyguard dismissal.

---

## Repository Layout After Phase 1

```
Launcher/
├── PLAN.md
├── settings.local.json
├── build.gradle.kts
├── settings.gradle.kts
├── lawnchair/
│   └── src/app/lawnchair/
│       ├── usage/
│       │   └── AppUsageTracker.kt          ← NEW
│       └── search/algorithms/
│           ├── SearchUtils.kt              ← MODIFIED
│           └── LawnchairAppSearchAlgorithm.kt ← MODIFIED
└── ...rest of Lawnchair source
```

---

## Git Branch Strategy

- `master` / `main` — stable releases
- `claude/app-drawer-search-filter-jFeEX` — current development branch (Phase 1)
- Future branches per Phase 2 feature

All Phase 1 work goes on `claude/app-drawer-search-filter-jFeEX`.
