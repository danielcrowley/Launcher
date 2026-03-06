---
name: superpowers:tdd
description: Test-Driven Development skill. Use when the user wants to implement a feature using TDD (red-green-refactor cycle). Enforces writing failing tests before implementation code.
---

# Superpowers: Test-Driven Development (TDD)

Enforce strict red-green-refactor TDD cycles. Tests must fail before any implementation is written.

## Workflow

Make a todo list for all the tasks in this workflow and work on them one after another.

### 1. Understand the Requirement

Before writing any code:
- Clarify exactly what behavior needs to be implemented
- Identify the smallest testable unit of behavior
- Confirm the test framework being used (JUnit, Espresso, etc.)
- Identify where test files should live

### 2. RED — Write a Failing Test

Write a test that:
- Tests exactly one behavior
- Has a descriptive name that explains what it tests
- Will fail because the implementation doesn't exist yet
- Does NOT yet have the implementation code

**Run the test and confirm it FAILS.** If it passes, the test is wrong or the behavior already exists.

```
VERIFY: Test must fail at this stage. Do not proceed until confirmed failing.
```

### 3. GREEN — Write Minimal Implementation

Write the **minimum** code needed to make the test pass:
- Do not add extra functionality
- Do not refactor yet
- Just make the test green

**Run the test and confirm it PASSES.**

```
VERIFY: Test must pass at this stage. Fix implementation if still failing.
```

### 4. REFACTOR — Improve Without Breaking

Now improve the code:
- Remove duplication
- Improve naming and readability
- Apply design patterns if needed
- Keep all tests passing throughout

**Run all tests and confirm they still PASS.**

### 5. Repeat

Go back to step 2 for the next behavior to implement.

## Rules

- **Never write implementation before a failing test exists**
- **Never skip the failing test verification step**
- **Commit after each green-refactor cycle**
- **Keep tests fast and isolated**

## Android/Kotlin Specific

For this project (Lawnchair Android Launcher):

- Unit tests: `lawnchair/src/test/` with JUnit 4/5
- Instrumented tests: `lawnchair/src/androidTest/` with Espresso/TAPL
- Run unit tests: `./gradlew :lawnchair:test`
- Run instrumented tests: `./gradlew :lawnchair:connectedAndroidTest`
- Mock with MockK or Mockito-Kotlin

## Wrap Up

In your final message, provide:
- Summary of behaviors implemented
- Test names and their status
- Any design decisions made during refactor
