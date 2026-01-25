# Heavy / Light Usage — Where They Appear and What They Control

This document lists **every place** in the LiftPath app where **Heavy** and **Light** (as workout types) are used, and what each usage **controls** or **connects to**. Use it when changing how heavy/light work or when tracking down behavior.

---

## 1. Overview

- **Heavy** and **Light** are two of three workout types; the third is **Custom**.
- They are stored as lowercase strings: `"heavy"`, `"light"`.
- **Heavy** ≈ strength / low-rep focus; **Light** ≈ volume / hypertrophy / higher reps.
- They affect: workout flow, rest timers, progression suggestions, 1RM/progress filtering, UI labels, and colors.

---

## 2. Data Model — Where Heavy/Light Are Stored

| Location | Field | Role |
|----------|--------|------|
| `TrainingSession` | `defaultWorkoutType: String?` | Workout type for the whole session. Default `"heavy"` when null in many code paths. |
| `ExerciseEntry` | `workoutType: String?` | Type for that **set**. Can override session default. Null often treated as “use session default.” |
| `WorkoutPlan` | `workoutType: String` | Plan is either heavy or light (or custom when creating plans). |
| `ActiveWorkoutDraft` | `workoutType: String` | Type of the in-progress workout. |

---

## 3. Constants and Formatting

### 3.1 `WorkoutTypeFormatter` (`utils/WorkoutTypeFormatter.kt`)

| Item | Role |
|------|------|
| `HEAVY = "heavy"`, `LIGHT = "light"`, `CUSTOM = "custom"` | Canonical string constants. |
| `normalize(type)` | Maps invalid/missing to `"heavy"`. Valid types unchanged. |
| `label(type)` | `"heavy"` → `"Heavy"`, `"light"` → `"Light"`, else `"Custom"`. |
| `fromIntensity(SessionIntensity)` | `HEAVY` → `"heavy"`, `LIGHT` → `"light"`. |
| `toIntensity(type)` | `"light"` → `LIGHT`, else → `HEAVY`. |

Used across the app for normalization and display.

### 3.2 `SessionIntensity` (`models/DataModels.kt`)

- `HEAVY`, `LIGHT` — enum used by `WorkoutGenerator` and intensity selection. Mapped to `"heavy"` / `"light"` via `WorkoutTypeFormatter`.

### 3.3 `formatTypeLabel` (SelectExerciseActivity, LogSetActivity)

- Simple titlecase: `"heavy"` → `"Heavy"`, etc. Used in dialogs and Log Set title (e.g. `"Bench Press (Heavy)"`).

---

## 4. Main Activity (Home) — Workout Start and Stats

### 4.1 Starting a workout

| Location | What it does |
|----------|----------------|
| **Workout mode bottom sheet** | User picks: **Custom**, **Plan** (heavy/light from plan), or **Auto**. |
| **Auto** | `detectNextWorkoutType()` alternates heavy ↔ light from last **non-custom** session. No history → `"heavy"`. |
| **Plan** | `launchActiveWorkout(plan.workoutType)` — uses plan’s `workoutType` (`"heavy"` or `"light"`). |
| **Custom** | `launchActiveWorkout("custom", ...)`. |
| **Resume draft** | Uses `draft.workoutType` (heavy/light/custom). |

### 4.2 “Days since” stats

| UI | Logic |
|----|--------|
| **Days since heavy** | `calculateDaysSinceLastWorkout(trainingData, "heavy")` — last session with `defaultWorkoutType == "heavy"` or any exercise `workoutType == "heavy"`. |
| **Days since light** | Same, for `"light"`. |

### 4.3 Left/right exercise 1RM and trend

- **1RM** and **trend** use **heavy only**:  
  `entry.workoutType == "heavy"` or `(workoutType == null && session.defaultWorkoutType == "heavy")`.  
- Light sessions are ignored for these stats.

**Files:** `MainActivity.kt`, `activity_main.xml` (e.g. `text_days_heavy`, `text_days_light`).

---

## 5. Active Training — In-Workout Flow

### 5.1 Workout type source

| Source | How heavy/light is set |
|--------|-------------------------|
| **Intent** | `EXTRA_WORKOUT_TYPE`; default `"heavy"`. |
| **Plan applied** | `workoutType = plan.workoutType` (unless current is custom). |
| **Exercise from plan** | `exerciseWorkoutTypes[exerciseId] = plan.workoutType` (heavy/light). |
| **Type override dialog** | User chooses “Heavy (Strength)” or “Light (Volume/Hypertrophy)” → `SessionIntensity` → `"heavy"` or `"light"`. |
| **Resume draft** | `workoutType = draft.workoutType`. |

### 5.2 What workout type controls

- **Title**: e.g. “Heavy” / “Light” in toolbar.
- **Exercise type when adding**: Passed to `SelectExerciseActivity` and used for suggestions.
- **Log Set intent**: `EXTRA_WORKOUT_TYPE` = per-exercise type or session `workoutType`.
- **Rest timer base**: When starting timer after logging a set, base rest uses `workoutType` (see §8).
- **Saving session**: `defaultWorkoutType = workoutType` on the saved `TrainingSession`.

**Files:** `ActiveTrainingActivity.kt`.

---

## 6. Select Exercise — Picker and Type Override

### 6.1 Session type

- `sessionWorkoutType` from intent; default `"heavy"`.
- Used when calling `ProgressionHelper.getSuggestion(..., requestedType = sessionWorkoutType)` and when returning the selected exercise.

### 6.2 “Change type” dialog

- Options: **Heavy**, **Light**, **Custom**.
- Maps to `"heavy"`, `"light"`, `"custom"` and returns that as `EXTRA_SELECTED_WORKOUT_TYPE`.

### 6.3 First-time / suggestion dialogs

- `formatTypeLabel(workoutType)` in messages (e.g. “First time … Heavy”).
- Suggestion text can show “last heavy RPE” (`lastHeavyRpe`) when type is heavy.

**Files:** `SelectExerciseActivity.kt`.

---

## 7. Log Set — Weights, Reps, RPE, Rest Timer

### 7.1 Workout type

- From intent `EXTRA_WORKOUT_TYPE`; default `"heavy"`.
- Shown in title: `"$exerciseName (${formatTypeLabel(workoutType)})"`.

### 7.2 What it controls

| Feature | Heavy/Light effect |
|---------|--------------------|
| **Progression suggestions** | Disabled for **custom**; for heavy/light, `getSuggestion(..., requestedType = workoutType)` and `suggestRpe(userLevel, workoutType)` (different default RPE). |
| **Rest timer base** | See §8. |

**Files:** `LogSetActivity.kt`.

---

## 8. Rest Timer — Base Duration

- **Heavy** → `heavyRestSeconds` (default 150).
- **Light** → `lightRestSeconds` (default 60).
- **Custom** (or unknown) → `customRestSeconds` (default 120).

RPE-based adjustments apply on top. Rest timer is started from `LogSetActivity` and `ActiveTrainingActivity` (duration chosen in each using `ProgressionSettingsManager` + workout type).

**Settings UI:** Progression Settings → Rest Timer: “Heavy Rest”, “Light Rest”, “Custom Rest” (labels reference heavy/strength vs light/volume).

**Files:** `LogSetActivity.kt`, `ActiveTrainingActivity.kt`, `ProgressionSettingsActivity.kt`, `activity_progression_settings.xml`, `ProgressionHelper` / `ProgressionSettings`.

---

## 9. Progression and Suggested Weight

### 9.1 `ProgressionHelper.getSuggestion`

- `requestedType` is `"heavy"`, `"light"`, or `"custom"`.
- **History**: Filters sets by `workoutType == requestedType` or `workoutType == null` (null matches any).
- **Double progression** (Tier 2/3):
  - **Heavy**: rep range `(heavyReps - 2)`–`(heavyReps + 2)` (default 3–7).
  - **Light**: `lightReps`–`(lightReps + 5)` (default 10–15).

### 9.2 `ProgressionHelper.suggestRpe`

- **Heavy**: Novice 8.0, Intermediate 8.5.
- **Light**: Novice 7.0, Intermediate 7.5.

### 9.3 Compatibility getters

- `proposedHeavyWeight` / `proposedLightWeight`: non-null only when `requestedType` matches.

**Files:** `ProgressionHelper.kt`, `ProgressionSettings` (e.g. `heavyReps`, `lightReps`). See `PROGRESSION_AND_SUGGESTED_WEIGHT.md`.

---

## 10. Progress (View Progress) — 1RM, Charts, Filtering

### 10.1 Session type storage

- `currentSessionWorkoutTypes: Map<date, workoutType>` from `TrainingSession.defaultWorkoutType`; default `"heavy"`.

### 10.2 Strength view (1RM) and “Heavy only” toggle

| Toggle | `showAllSessions` | Behavior |
|--------|-------------------|----------|
| **Heavy only** (default) | `false` | 1RM and strength chart use **heavy** sessions only. |
| **Heavy + Light** | `true` | Both heavy and light included in 1RM calculation. |

- **Heavy**: always included.
- **Light**: included only if the session has **RPE** for that exercise (Rule B).
- **Custom**: same as light (RPE required).

### 10.3 Volume view

- **All** session types (heavy, light, custom) included; no heavy-only filter.

### 10.4 UI strings

- “Heavy sessions only” / “Heavy + Light” in chart mode indicator and filter status.
- “Heavy only” / “Heavy + Light” in filter chip.

**Files:** `ProgressActivity.kt`, `activity_progress.xml`, `OneRMEstimationHelper.kt` (`includeLightSessions`).

---

## 11. OneRMEstimationHelper — 1RM and Volume

### 11.1 Strength / 1RM

- `calculateOneRMPerSession(..., includeLightSessions)`:
  - **Heavy**: always include.
  - **Light**: only if `includeLightSessions` (Progress “Heavy + Light” mode).
- RPE &lt; 6.5 “too light” filtering is about **effort**, not workout type.

### 11.2 Volume

- `calculateVolumePerSession`: **all** session types (heavy, light, custom).

**Files:** `OneRMEstimationHelper.kt`.

---

## 12. Workout Plans — Create and Apply

### 12.1 Edit plan

- Radio: **Heavy** / **Light** (and Custom when applicable).
- Stored as `WorkoutPlan.workoutType` (`"heavy"` or `"light"`).

### 12.2 Plan list and selection

- **PlanSelectionAdapter**, **WorkoutPlansAdapter**: badge color by `workoutType`:
  - **Heavy** → blue (`#2196F3`).
  - **Light** → orange (`#FF9800`).
- Label: titlecase of `workoutType`.

**Files:** `EditWorkoutPlanActivity.kt`, `activity_edit_workout_plan.xml`, `PlanSelectionAdapter.kt`, `WorkoutPlansAdapter.kt`.

---

## 13. WorkoutGenerator — Auto-Generated Workouts

- `SessionIntensity.HEAVY` / `.LIGHT` → `"heavy"` / `"light"`.
- **Blueprint** and **volume** (sets × reps) depend on intensity:
  - **Heavy**: e.g. 3×5 for Tier 1, different structure for Tier 2/3.
  - **Light**: higher-rep, higher-volume structure.

**Files:** `WorkoutGenerator.kt`.

---

## 14. Training Detail (History) — View / Edit Past Session

### 14.1 Session type

- **Spinner**: “Heavy”, “Light”, “Custom” → `workoutTypeKeys`: `["heavy","light","custom"]`.
- Changing spinner updates `defaultWorkoutType` for the whole session.

### 14.2 Per-exercise type

- Each exercise can override with **Heavy** / **Light** / **Custom**.
- Stored in `ExerciseEntry.workoutType`.
- When logging a new set from detail: `launchLogSetActivity(..., workoutType)` = exercise type or session default; default `"heavy"`.

### 14.3 Add exercise

- `SelectExerciseActivity` launched with `EXTRA_WORKOUT_TYPE` = session default (`"heavy"` if null).

**Files:** `TrainingDetailActivity.kt`.

---

## 15. History List (Session Cards)

- **Badge**: `training.defaultWorkoutType ?: "heavy"` → “HEAVY” / “LIGHT” / etc.
- **Colors**:
  - **Light** → `fitness_light_blue`.
  - **Heavy** (and default) → `fitness_dark_blue`.

**Files:** `HistoryAdapter.kt`, `colors.xml` (`fitness_light_blue`, `fitness_dark_blue`).

---

## 16. Charts (Carousel) — Fatigue and Colors

- **Fatigue chart**: bars colored by workout type:
  - **Heavy** → red (`#EF4444`).
  - **Light** → accent color.
  - **Custom** → secondary text color.
  - **Null** → gray (theme-dependent).

**Files:** `ChartCarouselAdapter.kt`.

---

## 17. Grouped Exercise Row (“Type: Heavy”)

- `TrainingDetailAdapter`: `WorkoutTypeFormatter.label(exerciseType)` → “Type: Heavy” / “Type: Light” / “Type: Custom” per exercise.

**Files:** `TrainingDetailAdapter.kt`, `item_grouped_exercise.xml`.

---

## 18. Strings and Validation

### 18.1 `strings.xml`

- RPE help: “For strength (heavy): RPE 7.5–8.5”; “For volume (light): RPE 7–8”.
- Validation: `validation_heavy_rest`, `validation_light_rest`, `validation_heavy_sets`, etc. (Progression Settings).

### 18.2 Progression Settings layout

- “Rest time for heavy/strength workouts” (Heavy Rest).
- “Rest time for light/volume workouts” (Light Rest).

**Files:** `strings.xml`, `activity_progression_settings.xml`.

---

## 19. ReadinessHelper (Mock Data)

- Mock sessions use `defaultWorkoutType = "heavy"` or `"light"` for demos.

**Files:** `ReadinessHelper.kt`.

---

## 20. Quick Reference Table

| Area | Heavy | Light | Notes |
|------|--------|--------|--------|
| **Workout start** | Default when no history; alternate from last light | Alternate from last heavy | Auto mode |
| **Rest timer** | `heavyRestSeconds` (e.g. 150s) | `lightRestSeconds` (e.g. 60s) | Progression Settings |
| **Suggested RPE** | 8.0 / 8.5 (Novice / Interm.) | 7.0 / 7.5 | ProgressionHelper |
| **Double-prog rep range** | 3–7 (default) | 10–15 (default) | heavyReps / lightReps |
| **Main 1RM/trend** | Used | Ignored | Home stats |
| **Progress 1RM** | Always included | Only if “Heavy + Light” + RPE | Progress screen |
| **Progress volume** | Included | Included | All sessions |
| **Plan badge** | Blue | Orange | Plan list |
| **History badge** | Dark blue | Light blue | History list |
| **Fatigue chart** | Red | Accent | Bar colors |

---

## 21. Files to Touch When Changing Heavy/Light

- **Models:** `DataModels.kt` (`SessionIntensity`, `ExerciseEntry.workoutType`, `TrainingSession.defaultWorkoutType`, `WorkoutPlan.workoutType`).
- **Utils:** `WorkoutTypeFormatter.kt`.
- **Activities:** `MainActivity`, `ActiveTrainingActivity`, `SelectExerciseActivity`, `LogSetActivity`, `TrainingDetailActivity`, `ProgressActivity`, `ProgressionSettingsActivity`, `EditWorkoutPlanActivity`.
- **Helpers:** `ProgressionHelper`, `ProgressionSettingsManager`, `WorkoutGenerator`, `OneRMEstimationHelper`, `ReadinessHelper`.
- **Adapters:** `ActiveExercisesAdapter`, `HistoryAdapter`, `ChartCarouselAdapter`, `PlanSelectionAdapter`, `WorkoutPlansAdapter`, `TrainingDetailAdapter`.
- **Layouts:** `activity_main`, `activity_progress`, `activity_progression_settings`, `activity_edit_workout_plan`, `item_grouped_exercise`.
- **Resources:** `strings.xml`, `colors.xml`.
