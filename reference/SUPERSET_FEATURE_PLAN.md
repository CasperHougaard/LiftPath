# SuperSet Feature Plan

## Summary

- **Entry point**: Add a "SuperSet" tile in [AddSpecialBottomSheet](app/src/main/java/com/liftpath/components/AddSpecialBottomSheet.kt) (the "Add Special" plus button flow).
- **Flow**: User taps SuperSet → regular add-exercise (SelectExerciseActivity) opens → on selection, the new exercise is added and **linked** to the **last exercise** in the list (the one immediately above the add-buttons row). If there are no exercises, SuperSet can be disabled or show a toast.
- **Data**: Link two consecutive exercises with a shared `supersetGroupId`. Persist superset pairs in the draft; persist `groupId` and `groupType` on each set (ExerciseEntry) for analytics.
- **UI**: Show a highlighted line/connector between the two linked exercises in the active workout list.
- **Rest timer**: When logging a set for the **first** exercise of the pair → start a **short break** of Y seconds. When logging a set for the **second** → start the **normal** rest timer (intent-based). Y will be a constant (or setting) you provide later.

---

## 1. Data model (future-proofed)

### 1a. ExerciseEntry (persisted set record)

**File:** [app/src/main/java/com/liftpath/models/DataModels.kt](app/src/main/java/com/liftpath/models/DataModels.kt)

**Add fields to `ExerciseEntry`:**

- **`groupId: String? = null`** (UUID)
  - Logic: Any exercises (sets) sharing the same `groupId` in a session are treated as a cluster.
- **`groupType: String? = null`** (e.g. enum or string: `"SUPERSET"`, `"CIRCUIT"`, etc.)
  - Default for this feature: `"SUPERSET"`.

**Reasoning:** Persisting on each set is mandatory for analytics. We need to know later whether performance dipped because the set was part of a superset (or circuit). TrainingSession stores `exercises: List<ExerciseEntry>`, so every saved set will carry its `groupId` and `groupType`.

**Optional:** Add a small enum or constants for group types, e.g. `object GroupType { const val SUPERSET = "SUPERSET"; const val CIRCUIT = "CIRCUIT" }`, and use `GroupType.SUPERSET` when creating entries.

### 1b. GroupedExercise (in-memory only)

**Add:** `supersetGroupId: String? = null` (and optionally `groupType: String? = null`) on **GroupedExercise** for:

- UI: showing the line between the two exercises and styling.
- Rest timer: knowing first vs second in superset when launching LogSetActivity.
- Draft: when the user has added a superset partner but not yet logged any sets, there are no ExerciseEntry records yet; the link lives only on GroupedExercise and in draft.

**Flow:** When a set is logged and we create/append an `ExerciseEntry` in `updateExercises()`, set:

- `entry.groupId = group.supersetGroupId`
- `entry.groupType = group.groupType ?: "SUPERSET"`

so that the saved session (TrainingSession) has the correct cluster info on every set.

### 1c. ActiveWorkoutDraft

**Add:** `supersetPairs: List<Pair<Int, Int>>? = null` (or equivalent) to persist which exercise IDs are linked when they have **no sets yet**. Once sets exist, `ExerciseEntry.groupId` and `ExerciseEntry.groupType` are the source of truth for persisted data; draft still needs to remember links for exercises with zero sets so that on restore we can reapply `supersetGroupId` (and groupType) to the rebuilt GroupedExercises.

### 1d. Summary

| Layer              | Where                        | Purpose |
|--------------------|------------------------------|--------|
| **Persisted**      | `ExerciseEntry`              | `groupId`, `groupType` – analytics, "was this set in a superset?" |
| **In-memory**      | `GroupedExercise`            | `supersetGroupId` (and optional `groupType`) – UI, rest timer, and feeding groupId/groupType into new entries |
| **Draft (no sets)**| `ActiveWorkoutDraft.supersetPairs` | Remember linked exercises before any set is logged |

When saving a completed workout, `TrainingSession.exercises` is a list of `ExerciseEntry`; each entry's `groupId` and `groupType` are already set, so no extra "session-level" table is needed. Analytics can filter or group by `groupType` and `groupId` per set.

---

## 2. Draft save/restore

**ActiveTrainingActivity** ([ActiveTrainingActivity.kt](app/src/main/java/com/liftpath/activities/ActiveTrainingActivity.kt))

- **persistDraft()**: Derive pairs from `groupedExercises` (consecutive items with same non-null `supersetGroupId`) and pass to `ActiveWorkoutDraft`.
- **applyDraft()** / **rebuildGroupedExercisesFromEntries()**: After rebuilding groups from `entries`, apply `draft.supersetPairs`: for each pair `(id1, id2)`, assign the same `supersetGroupId` (e.g. UUID) to the two `GroupedExercise` items. When restoring from entries that already have `groupId`/`groupType`, set each group's `supersetGroupId`/`groupType` from the first set in that group (if any).
- **deleteExercise()**: When removing an exercise, if it had a `supersetGroupId`, clear that ID from the partner group (the adjacent group that shared it) so the remaining exercise is no longer shown as superset-linked.

---

## 3. Add SuperSet to "Add Special" bottom sheet

**AddSpecialBottomSheet**

- Add callback `onSuperSetSelected: (() -> Unit)?` and pass it from the activity.
- In [bottom_sheet_add_special.xml](app/src/main/res/layout/bottom_sheet_add_special.xml), add a third tile (e.g. "SuperSet") that calls `onSuperSetSelected`.
- **Activity**: When SuperSet is chosen, require at least one exercise in `groupedExercises`. If none, show a toast (e.g. "Add an exercise first") and return. Otherwise launch SelectExerciseActivity with a flag, e.g. `EXTRA_ADD_AS_SUPERSET_PARTNER = true` and optionally `EXTRA_SUPERSET_FIRST_EXERCISE_ID` = last exercise's ID.

**SelectExerciseActivity result handling**

- In ActiveTrainingActivity, when the result is from an "add as superset partner" request: add the new exercise as today (same as normal add), then set a new `supersetGroupId` (e.g. `UUID.randomUUID().toString()`) and `groupType = "SUPERSET"` on both the **last** GroupedExercise and the **newly added** GroupedExercise. Notify the adapter for the last item and the new item (or notifyItemRangeChanged).

---

## 4. UI: line between superset pair

**Adapter** ([ActiveExercisesAdapter.kt](app/src/main/java/com/liftpath/adapters/ActiveExercisesAdapter.kt))

- In `bindExerciseViewHolder`, determine:
  - `isFirstInSuperset`: current item has non-null `supersetGroupId` and the previous item (if any) does not share it.
  - `isSecondInSuperset`: current item has non-null `supersetGroupId` and the previous item shares the same `supersetGroupId`.
- **Layout** ([list_item_active_exercise.xml](app/src/main/res/layout/list_item_active_exercise.xml)): Add an optional top "connector" view (e.g. a thin horizontal bar or left-border accent) that is visible only when `isSecondInSuperset`. Optionally style the card for both items in the pair so the two cards plus the line read as one superset block.
- Adapter already receives `groupedExercises` and position, so it can compute first/second in superset.

---

## 5. Rest timer: short break vs full rest

**LogSetActivity**

- Add extras, e.g. `EXTRA_SUPERSET_REST_MODE` with values like `"short_break"` or `"full_rest"`, or a single `EXTRA_REST_SECONDS_OVERRIDE: Int?` to force rest duration.
- In `startRestTimerAfterPermissionCheck()`: if `EXTRA_REST_SECONDS_OVERRIDE` is present, use that for `restSeconds` instead of intent-based calculation; otherwise keep current logic.

**ActiveTrainingActivity**

- In `launchLogSetActivity(exerciseId, exerciseName)`:
  - Find the index of the exercise in `groupedExercises` and whether it shares a `supersetGroupId` with the previous item.
  - If **second in superset** (previous item has same `supersetGroupId`): do **not** pass override; full rest timer as today.
  - If **first in superset** (next item has same `supersetGroupId`): pass `EXTRA_REST_SECONDS_OVERRIDE = Y` (short break).
- Define **Y** as a constant (e.g. in a small constants object or ProgressionSettings) so you can replace it with a setting later.

---

## 6. Strings and accessibility

- Add string for the new tile, e.g. `tile_superset` / "SuperSet", and any toasts ("Add an exercise first", etc.).
- Ensure the new connector view has a minimal or decorative content description for TalkBack.

---

## 7. Edge cases

- **Delete first (or second) of superset**: Clear `supersetGroupId` from the remaining exercise so the connector and short-break logic no longer apply.
- **Draft restore**: Rebuild groups then reapply `supersetPairs` (and from entries with `groupId`/`groupType` where applicable) so links and order are correct.
- **No exercises**: SuperSet in the sheet either disabled (greyed + toast on tap) or always enabled with toast "Add an exercise first" when tapped.

---

## Files to touch (summary)

| Area        | Files |
|------------|--------|
| Models     | [DataModels.kt](app/src/main/java/com/liftpath/models/DataModels.kt) (`ExerciseEntry.groupId`/`groupType`, `GroupedExercise.supersetGroupId`/`groupType`, `ActiveWorkoutDraft.supersetPairs`) |
| Draft      | [ActiveTrainingActivity.kt](app/src/main/java/com/liftpath/activities/ActiveTrainingActivity.kt) (persist/restore pairs, add-as-superset flow, launchLogSetActivity with override, set groupId/groupType on new entries) |
| Special add| [AddSpecialBottomSheet.kt](app/src/main/java/com/liftpath/components/AddSpecialBottomSheet.kt), [bottom_sheet_add_special.xml](app/src/main/res/layout/bottom_sheet_add_special.xml) |
| List UI    | [ActiveExercisesAdapter.kt](app/src/main/java/com/liftpath/adapters/ActiveExercisesAdapter.kt), [list_item_active_exercise.xml](app/src/main/res/layout/list_item_active_exercise.xml) |
| Rest timer | [LogSetActivity.kt](app/src/main/java/com/liftpath/activities/LogSetActivity.kt) (read override, use for rest seconds) |
| Strings    | [strings.xml](app/src/main/res/values/strings.xml) |

No changes to TrainingSession structure beyond ExerciseEntry; superset is a workout-time UX and rest-timer behavior, with analytics-ready data on each set.
