# LiftPath — Claude Code Rules

## Workout Plan Import/Export Contract

`WorkoutPlanMarkdownHelper.kt` encodes the markdown schema used for AI-generated plan
import/export. When any of the following change, verify the helper still works end-to-end:

- `WorkoutPlan` fields (`DataModels.kt`)
- `PlanExerciseSlot` fields (`DataModels.kt`)
- `ExerciseLibraryItem` fields or IDs (`DataModels.kt` / `DefaultExercisesHelper.kt`)
- `SetIntent` enum values (`DataModels.kt`)

**Checklist after data-model changes:**

1. `buildSpecMarkdown` still emits correct column order and enum value strings.
2. `parsePlansFromMarkdown` column indices still match (column 0 = name, 1 = id, 2 = sets,
   3 = reps, 4 = intent, 5 = rpe, 6 = rest, 7 = notes).
3. The field reference table in the export doc is still accurate.
4. Round-trip test: export spec → add a `## Plan:` section with real exercise IDs → import →
   confirm all fields (intent, setsTarget, rpeTarget, restTimeSeconds) are populated correctly.

**Key files:**

| File | Role |
|---|---|
| `app/src/main/java/com/liftpath/helpers/WorkoutPlanMarkdownHelper.kt` | Build spec + parse import |
| `app/src/main/java/com/liftpath/helpers/JsonHelper.kt` | `exportWorkoutPlanSpec` / `importWorkoutPlans` |
| `app/src/main/java/com/liftpath/activities/WorkoutPlansActivity.kt` | UI entry points (⋮ menu) |
| `app/src/main/res/menu/menu_workout_plans.xml` | Popup menu items |
