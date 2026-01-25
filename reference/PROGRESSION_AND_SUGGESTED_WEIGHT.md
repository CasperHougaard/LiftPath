# Progression Settings and Suggested Weight — Current Implementation

This document describes how **progression settings** and **suggested weight for exercises** work in the LiftPath app as of the current codebase. Use it as a reference before redesigning either system.

---

## 1. Overview

- **Progression settings** are user-configurable values (user level, step sizes, rest timers, deload thresholds, etc.) stored via `ProgressionSettingsManager` and edited in `ProgressionSettingsActivity`.
- **Suggested weight** (and reps) comes from `ProgressionHelper.getSuggestion()`. It uses the exercise’s **tier**, the **last logged session** for that exercise + workout type, and the current **progression settings** to propose a weight (and optionally reps) for the next set.

The suggestion logic is **tier-based**:

- **Tier 1 (main lifts)**: **Linear RPE** — adjust weight based on last session’s RPE and optional time decay / failure; reps stay fixed.
- **Tier 2/3 (assistance / accessories)**: **Double progression** — increase reps until a target range is hit, then increase weight and reset reps.

---

## 2. Architecture

| Component | Role |
|-----------|------|
| `ProgressionHelper` | Pure logic: `getSuggestion()`, `suggestRpe()`, and all progression algorithms. |
| `ProgressionSettingsManager` | Load/save `ProgressionSettings` from `SharedPreferences` (`progression_settings`), including migration. |
| `ProgressionSettingsActivity` | UI for editing a **subset** of progression settings (core, rest timer, deload). |
| `ProgressionHelper.ProgressionSettings` | Data class holding all settings (used by helper and manager). |
| `ProgressionHelper.ProgressionSuggestion` | Result of `getSuggestion()`: proposed weight/reps, reasoning, badges, etc. |

---

## 3. ProgressionSettings — Full Data Model

`ProgressionSettings` is defined in `ProgressionHelper.kt`. Below, **“Used in suggestion?”** means whether the **weight/rep suggestion** logic uses it. **“In UI?”** means whether `ProgressionSettingsActivity` exposes it.

### 3.1 Core / progression logic

| Field | Default | Used in suggestion? | In UI? | Notes |
|-------|---------|---------------------|--------|-------|
| `userLevel` | `NOVICE` | **No** (only for RPE hints) | Yes (spinner) | Novice / Intermediate. Affects `suggestRpe()` only, not weight. |
| `lookbackCount` | `3` | **No** | Yes | “How many recent sessions to analyze.” **Never used**; history uses all matching entries, then takes last. |
| `roundTo` | `1.25f` | Yes | **No** | Weight rounding increment (kg). Linear and double progression round via `roundToIncrement(_, settings.roundTo)`. |
| `increaseStep` | `2.5f` | Yes | Yes | Add this when RPE ≤ 7.0 (linear) or when applying failure penalty (−increaseStep). |
| `smallStep` | `1.25f` | Yes | Yes | Add when 7.0 < RPE ≤ 8.5 (linear), or for double-progression level-up. |

### 3.2 Volume defaults (rep ranges)

| Field | Default | Used in suggestion? | In UI? | Notes |
|-------|---------|---------------------|--------|-------|
| `heavySets` | `3` | No | No | Not used by `ProgressionHelper`. |
| `heavyReps` | `5` | Yes | **No** | Double progression **heavy**: target range `(heavyReps - 2)`–`(heavyReps + 2)` → default 3–7. |
| `lightSets` | `4` | No | No | Not used by `ProgressionHelper`. |
| `lightReps` | `10` | Yes | **No** | Double progression **light**: target range `lightReps`–`(lightReps + 5)` → default 10–15. |

### 3.3 Time decay (linear RPE only)

| Field | Default | Used in suggestion? | In UI? | Notes |
|-------|---------|---------------------|--------|-------|
| `timeDecayThresholds` | `[14, 30, 60]` days | Yes | **No** | Days since last session. |
| `timeDecayMultipliers` | `[0.95, 0.90, 0.85]` | Yes | **No** | Multiply last weight by these when `daysSince >=` corresponding threshold (see §7.2). |

### 3.4 Deload detection

| Field | Default | Used in suggestion? | In UI? | Notes |
|-------|---------|---------------------|--------|-------|
| `deloadThreshold` | `3` | **No** | Yes | “Consecutive hard sessions” — **not used** by `ProgressionHelper` or rest timer. |
| `deloadRPEThreshold` | `9.0f` | **No** | Yes | “What counts as hard” — **not used** by suggestion logic. |

Deload fields are stored and shown in “Deload Detection” but no code currently uses them for behavior. OneRMEstimationHelper mentions “deload” in insight text only.

### 3.5 Rest timer and notifications

| Field | Default | Used in suggestion? | In UI? | Notes |
|-------|---------|---------------------|--------|-------|
| `restTimerEnabled` | `true` | No | Yes | Rest timer on/off. |
| `heavyRestSeconds` | `150` | No | Yes | Rest after heavy sets. |
| `lightRestSeconds` | `60` | No | Yes | Rest after light sets. |
| `customRestSeconds` | `120` | No | Yes | Rest for custom workouts. |
| `rpeAdjustmentEnabled` | `true` | No | Yes | RPE-based rest adjustments. |
| `rpeHighThreshold`, `rpeHighBonusSeconds` | `9.0`, `60` | No | Yes | Extra rest for high-RPE sets. |
| `rpeDeviationThreshold`, `rpePositiveAdjustmentSeconds`, `rpeNegativeAdjustmentSeconds` | `1.0`, `30`, `15` | No | Yes | Rest ± based on logged vs suggested RPE. |
| `notificationLiveCountdown` | `false` | No | Yes | Live countdown in notification. |
| `notificationAutoDismissEnabled` | `false` | No | Yes | Auto-dismiss notification. |
| `notificationAutoDismissSeconds` | `10` | No | Yes | Seconds before dismiss. |

These affect only the rest timer and notifications, not weight suggestions.

---

## 4. Storage and Save Behavior

- **Storage**: `ProgressionSettingsManager` saves a JSON-serialized `ProgressionSettings` in `SharedPreferences` under key `"settings"` in `"progression_settings"`.
- **Migration**: Old defaults `heavyRestSeconds = 180`, `lightRestSeconds = 90` are migrated to `150` and `60` on load.
- **Reset**: “Reset to defaults” clears the stored JSON; next `getSettings()` returns `ProgressionSettings()` defaults.

**Important**: `ProgressionSettingsActivity.saveSettings()` builds a **new** `ProgressionSettings` from **UI fields only**. Any field not bound to the UI keeps its **default** value in that new object. So on every save:

- `roundTo`, `heavyReps`, `lightReps`, `heavySets`, `lightSets`, `timeDecayThresholds`, `timeDecayMultipliers` are **always** reset to their defaults, even if they were previously overridden (e.g. via JSON import or future UI). The **suggestion logic** still uses these defaults.

---

## 5. Tier-Based Scheme Selection

The **progression scheme** is chosen only from the exercise **tier** (from `ExerciseLibraryItem.tier`):

```
TIER_1  → Linear RPE
TIER_2  → Double progression
TIER_3  → Double progression
null/other → Linear RPE (fallback)
```

- Tier is set per exercise in the library (e.g. `DefaultExercisesHelper`, `EditExerciseActivity`) and used by `WorkoutGenerator` and `ProgressionHelper`.
- **User level** (Novice / Intermediate) does **not** change the scheme; it only affects **suggested RPE** (`suggestRpe()`).

---

## 6. History Extraction and “Last Session”

### 6.1 Source of history

- **Method**: `ProgressionHelper` uses a private `extractHistory()` over `TrainingData`.
- **Input**: `exerciseId`, `requestedType` (`"heavy"`, `"light"`, or `"custom"`), and `TrainingData`.

**Logic**:

1. Iterate all `TrainingSession`s and their `ExerciseEntry`s.
2. Keep entries where `exerciseId` matches and `workoutType == requestedType` **or** `workoutType == null`.
3. Map each such entry to a `SessionData` (date, weight, reps, RPE, `hadFailure`).
4. Sort by `date`.
5. Take `history.last()` as the “last session.”

So we use the **chronologically last** matching **entry** (i.e. **last set** of the last session that contains that exercise + type). Example: last time you did Bench “heavy” you logged 3 sets; we use the **third set’s** weight, reps, RPE, and `completed` flag.

### 6.2 RPE and failure

- **RPE**: `entry.rpe ?: 8.0f` (default 8.0 if missing).
- **Failure**: `hadFailure = (entry.completed == false)`. So “incomplete” sets are treated as failed.

### 6.3 Workout type and `null`

- Entries with `workoutType == null` match **any** `requestedType` filter. So heavy/light/custom optionally share history with untagged entries.
- For **custom** workouts, `LogSetActivity` and `ActiveExercisesAdapter` **skip** progression suggestions and use “last logged” only. `SelectExerciseActivity` still calls `getSuggestion()` with `"custom"` when picking an exercise for a custom session; history is then filtered by `"custom"` or `null`.

### 6.4 Lookback

- **`lookbackCount` is never used.** History is **not** limited to the last N sessions; we use **all** matching entries, then take the last one. The “Lookback Sessions” UI is misleading for the current implementation.

---

## 7. Linear RPE Progression (Tier 1)

Used for **main lifts**. Reps are unchanged from last session; only weight is adjusted.

### 7.1 Priority order

1. **Time decay** (if applicable)  
2. **Failure** (if last set was failed)  
3. **Standard RPE** (otherwise)

### 7.2 Time decay

- `daysSince` = days from `lastSession.date` to today.
- Loop over `timeDecayThresholds` **descending** (60 → 30 → 14). If `daysSince >= threshold`, use the matching `timeDecayMultipliers` value.
- Defaults:
  - ≥ 60 days → ×0.85  
  - ≥ 30 days → ×0.90  
  - ≥ 14 days → ×0.95  
  - &lt; 14 days → no decay (×1.0).

If decay &lt; 1.0:

- `decayed = lastWeight * multiplier`
- `adjustment = decayed - lastWeight` (negative).
- Badge: `"TIME DECAY"`, reasoning includes days off and % reset.

### 7.3 Failure

- If `last.hadFailure` **and** no time decay was applied:
  - `adjustment = -increaseStep` (default −2.5 kg).
  - Badge: `"FAILED REPS"`.

### 7.4 Standard RPE bands

Based on `last.rpe`:

| RPE | Adjustment | Badge |
|-----|------------|-------|
| ≤ 7.0 | `+increaseStep` (default +2.5 kg) | — |
| 7.0 &lt; RPE ≤ 8.5 | `+smallStep` (default +1.25 kg) | — |
| 8.5 &lt; RPE &lt; 9.5 | `0` (maintain) | — |
| ≥ 9.5 | `-smallStep` (default −1.25 kg) | — |

Reasoning includes “Last RPE X.X”.

### 7.5 Final weight

- `finalWeight = roundToIncrement(lastWeight + adjustment, roundTo)`.
- `roundToIncrement`: `(value / inc).roundToInt() * inc` (round to nearest multiple of `roundTo`).
- `ProgressionSuggestion`: `proposedWeight = finalWeight`, `proposedReps = last.reps` (unchanged).

---

## 8. Double Progression (Tier 2 / 3)

Used for **assistance / accessories**. Rep ranges come from `heavyReps` / `lightReps`; weight changes only on “level up.”

### 8.1 Target rep ranges

- **Heavy**: `minReps = heavyReps - 2`, `maxReps = heavyReps + 2` (default 3–7).
- **Light**: `minReps = lightReps`, `maxReps = lightReps + 5` (default 10–15).

### 8.2 Rules

1. **Failure** (`last.hadFailure`):
   - Same weight, same reps.
   - Badge: `"RETRY"`, reasoning e.g. “Missed reps last time. Retry same weight.”

2. **Level up** (`last.reps >= maxReps`):
   - `newWeight = roundToIncrement(lastWeight + smallStep, roundTo)`.
   - `newReps = minReps`.
   - Badge: `"LEVEL UP"`, reasoning e.g. “Hit X reps! Increasing weight, resetting to Y reps.”

3. **Otherwise** (in range):
   - `newWeight = lastWeight`.
   - `newReps = lastReps + 1`.
   - Badge: `"ADD REP"`, reasoning e.g. “Build volume. Aim for X reps today.”

### 8.3 Output

- `ProgressionSuggestion`: `proposedWeight = newWeight`, `proposedReps = newReps`.

---

## 9. First-Time and Maintenance

### 9.1 First time (no history)

- `extractHistory` returns empty → `createFirstTimeSuggestion()`.
- **Linear RPE**: `proposedWeight = null`, `proposedReps = 5`, “Start light. Aim for 5 clean reps.”
- **Double progression**: `proposedWeight = null`, `proposedReps = 12`, “Start light. Aim for 12 controlled reps.”
- `isFirstTime = true`.

### 9.2 Maintenance

- Scheme `MAINTENANCE` exists but is **never** selected (tier mapping only uses LINEAR_RPE or DOUBLE_PROGRESSION). If it were used, it would suggest same weight and reps, “Just get the work done.”

---

## 10. RPE Suggestions (`suggestRpe`)

`ProgressionHelper.suggestRpe(userLevel, type)` returns default RPE for the **RPE slider / hints** only (not for weight math):

| Workout type | Novice | Intermediate |
|--------------|--------|--------------|
| heavy | 8.0 | 8.5 |
| light | 7.0 | 7.5 |

Used in `LogSetActivity` (prefill RPE, hint) and when displaying “suggested RPE” in progression / rest-timer explanations.

---

## 11. Where Suggestions Are Used

### 11.1 SelectExerciseActivity

- When user selects an exercise (and optionally workout type), we call `getSuggestion(exerciseId, requestedType, trainingData, settings)`.
- **First time**: dialog “First time / [type]” + add vs change type vs cancel.
- **Has history**: dialog with badge, last RPE, days since (if ≥ 14), `humanExplanation`, and **“Suggested weight: X kg”** (or custom message if no weight). Then add / change type / cancel.

### 11.2 LogSetActivity

- **Custom workout**: No progression suggestion; only prefill from previous set / last logged. No suggestion hint.
- **Heavy / light**: `showWeightSuggestion()` calls `getSuggestion()`. If not first time and `proposedWeight != null`:
  - Prefill kg (if empty) with suggested weight.
  - Prefill reps (if empty) with `proposedReps` (default 5).
  - Prefill RPE with `suggestRpe(userLevel, workoutType)`.
  - Show hint like “Suggested: X kg (Y reps) @ RPE Z” and optional badge.

### 11.3 ActiveExercisesAdapter

- **Custom**: Uses last logged kg/reps only; no `ProgressionHelper`.
- **Plan-driven**: Uses blueprint recommendations when present; still gets weight/rep suggestion from `ProgressionHelper` for “Recommended: N sets” and “X kg × Y reps” (+ optional badge).
- **No recommendation**: Falls back to `ProgressionHelper.getSuggestion()` for the same “X kg × Y reps” display.

In all cases, suggestions are **display only** or **prefill**; the user can change values before saving.

---

## 12. ProgressionSuggestion Structure

Returned by `getSuggestion()`:

- `exerciseId`, `exerciseName`, `requestedType`
- `proposedWeight`, `proposedReps` (nullable for first-time)
- `reasoning`, `humanExplanation`
- `isFirstTime`, `badge`
- `lastWeight`, `lastRpe`, `daysSinceLastWorkout`

Legacy-style getters: `proposedHeavyWeight` / `proposedLightWeight` (based on `requestedType`), `lastHeavyRpe` (= `lastRpe`). Used by `SelectExerciseActivity` and similar.

---

## 13. Gaps and Inconsistencies

1. **`lookbackCount`**  
   - Shown and saved as “How many recent sessions to analyze.”  
   - **Never used.** History is full, then we take the last entry only.

2. **Deload settings**  
   - `deloadThreshold`, `deloadRPEThreshold` are in UI and stored.  
   - **Not used** by `ProgressionHelper` or rest timer. No “deload detection” behavior.

3. **`roundTo`, `heavyReps`, `lightReps`, `timeDecay*`**  
   - Used by suggestion logic.  
   - **Not in UI.** Not saved by `ProgressionSettingsActivity`; every save resets them to defaults.

4. **`heavySets` / `lightSets`**  
   - In `ProgressionSettings` but **unused** by `ProgressionHelper`.

5. **User level vs weight**  
   - UI says “Novice: Linear progression. Intermediate: Periodized logic.”  
   - **Weight suggestion** is the same for both; only **suggested RPE** changes. “Periodized” elsewhere (e.g. heavy/light alternation) is separate.

6. **History = last set only**  
   - We use the **last set** of the last matching session, not an aggregate (e.g. top set or average). Multi-set sessions are effectively represented by that single set.

7. **`workoutType == null`**  
   - Matches any requested type. Heavy/light/custom can therefore share history with untagged entries, which may or may not be desired.

8. **Custom workouts**  
   - Suggestion logic doesn’t treat “custom” specially; but LogSet and adapter **hide** suggestions for custom and use “last logged” instead. SelectExercise still requests suggestions for custom.

---

## 14. Files to Touch When Redoing Progression / Suggestions

- **Logic**: `app/.../helpers/ProgressionHelper.kt`  
- **Storage**: `app/.../helpers/ProgressionSettingsManager.kt`  
- **UI**: `app/.../activities/ProgressionSettingsActivity.kt`, `res/layout/activity_progression_settings.xml`  
- **Consumers**:  
  - `SelectExerciseActivity.kt` (dialog)  
  - `LogSetActivity.kt` (prefill + hint)  
  - `adapters/ActiveExercisesAdapter.kt` (recommendation text)  
- **Models**: `ProgressionHelper.ProgressionSettings`, `ProgressionHelper.ProgressionSuggestion`  
- **Data**: `TrainingData`, `ExerciseEntry` (workoutType, completed, rpe), `ExerciseLibraryItem` (tier)

---

## 15. Summary

- **Progression settings** control step sizes, time decay, rep ranges, rest timers, and deload UI; only a subset affects **suggested weight**, and several fields (lookback, deload, roundTo, heavy/light reps, time decay) are either unused or not configurable via UI.
- **Suggested weight** is computed by `ProgressionHelper.getSuggestion()` from exercise tier, last matching set, and those settings. Tier 1 uses **linear RPE** (time decay → failure → RPE bands); Tier 2/3 use **double progression** (retry / level up / add rep). RPE defaults come from `suggestRpe()` by user level and workout type only.
- Before redoing progression or suggested weight, consider: whether to use lookback, whether to implement deload behavior, which settings to expose, and how to handle “last session” (single set vs aggregate) and `workoutType == null`.
