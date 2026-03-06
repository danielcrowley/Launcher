---
name: superpowers:execute-plan
description: Subagent-driven plan execution skill. Use when implementing a multi-step plan that requires checkpoints and code review between steps. Breaks implementation into verified phases with review gates.
---

# Superpowers: Execute Plan

Implements a multi-step plan using subagent-driven development with review checkpoints between phases. Ensures correctness is verified at each step before proceeding.

## Prerequisites

This skill assumes you have a design ready (from `/superpowers:brainstorming` or an explicit plan). If you don't have a plan yet, use `/superpowers:brainstorming` first.

## Workflow

Make a todo list for all the tasks in this workflow and work on them one after another.

### 1. Parse the Plan

Extract the ordered list of implementation steps from the plan. For each step, identify:
- What files change
- What the acceptance criterion is
- Whether it requires a build/test verification

### 2. Execute Step-by-Step with Checkpoints

For each step:

**a. Implement the step**
- Make only the changes described in that step
- Do not anticipate future steps

**b. Verify the step**
- Build the project (if code changed): `./gradlew :lawnchair:assembleDebug`
- Run relevant tests: `./gradlew :lawnchair:test`
- Confirm the acceptance criterion is met

**c. Review checkpoint**
- Summarize what was changed and why
- Note any deviations from the plan and why
- If anything unexpected was discovered, surface it before continuing

**d. Commit the step**
- Commit with a descriptive message referencing the plan step
- Do not batch multiple steps into one commit

```
GATE: Only proceed to next step after current step is verified and committed.
```

### 3. Integration Verification

After all steps are complete:
- Run the full test suite: `./gradlew :lawnchair:test`
- Build the full app: `./gradlew :lawnchair:assembleLawnWithQuickstepGithubDebug`
- Verify no regressions

### 4. Final Review

Before declaring done:
- Review all changes made across all steps
- Check for any leftover debug code (`Log.d`, `println`, `TODO`)
- Verify the implementation matches the original design intent
- Run `/simplify` if code quality improvements are warranted

## Rules

- **One step at a time** — never combine steps
- **Verify before proceeding** — a failing build stops progress
- **Surface surprises immediately** — don't hide unexpected findings
- **Commit each step** — granular history enables easy rollback

## For This Project (Android Launcher)

Build commands:
```bash
# Fast: compile check only
./gradlew :lawnchair:compileDebugKotlin

# Unit tests only
./gradlew :lawnchair:testDebugUnitTest

# Full debug build
./gradlew :lawnchair:assembleLawnWithQuickstepGithubDebug
```

## Wrap Up

In your final message, provide:
- List of all steps completed with their status
- Final build and test results
- Summary of any deviations from the original plan
- Links to relevant commits
