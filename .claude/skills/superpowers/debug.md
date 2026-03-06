---
name: superpowers:debug
description: Systematic debugging skill. Use when the user has a bug to fix. Enforces root cause investigation before writing any fix. Never guess at solutions.
---

# Superpowers: Systematic Debugging

A four-phase methodology that requires understanding the root cause before writing any fix.

## Workflow

Make a todo list for all the tasks in this workflow and work on them one after another.

### Phase 1 — REPRODUCE

Before touching any code:
- Confirm you can reproduce the bug consistently
- Identify the exact conditions that trigger it
- Write a failing test that captures the bug (if feasible)
- Document: inputs, expected behavior, actual behavior

```
STOP: Do not hypothesize causes yet. Just reproduce and document.
```

### Phase 2 — INVESTIGATE

Systematically narrow down the cause:
- Read the relevant code paths end-to-end
- Add logging/debugging output to trace the flow
- Form hypotheses about what could cause the observed behavior
- Eliminate hypotheses one at a time with evidence
- Identify the EXACT line/condition that is wrong

**Do not write a fix until you can state:**
> "The bug is caused by [specific thing] at [specific location] because [specific reason]."

Tools for Android debugging:
- `adb logcat` filtered by tag
- Android Studio debugger with breakpoints
- `Log.d()` / `Timber.d()` instrumentation
- `./gradlew :lawnchair:test --info` for unit test details

### Phase 3 — FIX

Now write the minimal fix:
- Change only what is necessary to fix the root cause
- Do not refactor surrounding code (separate concern)
- Verify the failing test from Phase 1 now passes
- Run the full test suite to check for regressions

```
VERIFY: The bug reproduction test must pass. No new failures allowed.
```

### Phase 4 — PREVENT

Make the bug harder to reintroduce:
- Add or improve the test coverage around this area
- Add a comment explaining why the fix is correct
- Consider if similar bugs exist elsewhere (and fix them)
- Commit with a message that explains the root cause

## Rules

- **Never write a fix before identifying the root cause**
- **Never delete a failing test to make the build pass**
- **Never apply a workaround that masks the real problem**
- **Always run the full test suite after the fix**

## Wrap Up

In your final message, provide:
- Root cause statement (one sentence)
- What was changed and why
- Test results before and after
- Any follow-up items to consider
