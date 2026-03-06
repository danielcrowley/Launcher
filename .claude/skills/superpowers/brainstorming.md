---
name: superpowers:brainstorming
description: Socratic brainstorming skill. Use before implementing a new feature or making architectural decisions. Refines requirements and design through structured questioning before any code is written.
---

# Superpowers: Brainstorming

A Socratic session that refines requirements and design decisions before any implementation begins. No code is written during this phase.

## Workflow

Make a todo list for all the tasks in this workflow and work on them one after another.

### 1. State the Problem

Clearly articulate:
- What problem are we solving?
- Who benefits from the solution?
- What does success look like (definition of done)?

### 2. Question Assumptions

Ask probing questions to surface hidden constraints:
- What are we assuming that might not be true?
- What edge cases could break a naive implementation?
- Are there existing solutions in the codebase we could leverage?
- What are the performance and memory implications?
- Does this need to work offline? With multiple users?

For this Android Launcher project, also consider:
- Android version compatibility (minSdk 26)
- Battery/performance impact on a home screen app
- Interaction with the launcher lifecycle (rotation, multi-window)
- Permission requirements and user-facing permission flows
- Does this touch Launcher3 base code (risky) or Lawnchair layer (safer)?

### 3. Explore Alternatives

Generate at least 3 different approaches:
- Simplest possible approach (minimal code, minimal risk)
- Most robust approach (handles all edge cases)
- Creative/unconventional approach

For each approach, evaluate:
- Implementation complexity
- Testability
- Reversibility (can we undo this later?)
- Impact on existing features

### 4. Select and Refine

Choose the best approach and refine it:
- Define the data model / API surface
- Identify which files will change
- Identify what new files are needed
- Identify risks and mitigations

### 5. Document the Design

Write a concise design summary:
- Chosen approach and rationale
- Data structures / interfaces
- File-by-file change plan
- Open questions that need resolution before implementation

```
STOP: Do not write any implementation code until this design is approved.
```

## Rules

- **No code during brainstorming** (pseudocode is OK)
- **Challenge every assumption**
- **Consider the simplest solution first**
- **Document trade-offs, not just the winner**

## Wrap Up

In your final message, provide:
- The selected design in a clear, structured format
- The implementation plan (ordered list of steps)
- Any open questions that need answers before starting
- Ask: "Shall I proceed with implementation using /superpowers:tdd or /superpowers:execute-plan?"
