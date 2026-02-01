# PR (Personal Record) Logic Documentation

## Overview

The PR system tracks personal records across multiple dimensions for the "Hybrid Athlete" training style (Strength, Build, Flush). PRs are detected automatically as users log their workouts. The PR page shows a **Player Stats Card** list of exercises (one card per exercise with best 1RM, weight, volume, reps), sorted by most recent PR first.

## PR Types

The system tracks four types of PRs:

1. **WEIGHT PR** - Maximum weight lifted for a specific exercise (all intents)
2. **VOLUME PR** - Maximum total session volume (weight × reps) for a specific exercise (all intents)
3. **ONE_RM PR** - Maximum estimated 1-rep max (all intents; sets with effectiveReps > 15 excluded)
4. **REPS PR** - Maximum reps at a specific weight (±1 kg bucket); display uses **actual weight** (e.g. "22 reps @ 52.5kg")

## PR Detection Algorithm

### Core: `processSessionsForPRs()` (single chronological pass)

**Location:** `ProgressAnalysisHelper.kt`

**Process:**

1. **Chronological Processing**
   - Sessions are sorted by date (oldest first)
   - Per-exercise state: `ExerciseBests` (maxWeight, max1RM, maxVolume, maxRepsAtWeight, sessionVolumes), and `lastPrDate` (Long, timestamp ms) per exercise

2. **Session Deduplication**
   - At most **one PR per type per exercise per session**
   - Session bests are buffered; at end of each session, one PRRecord per (exercise, type) that improved is emitted

3. **Four Rules**
   - **Volume PR (unlocked):** Session volume = sum(weight × reps) for all working sets of that exercise in the session. Eligible for **all intents** (Strength, Build, Flush).
   - **1RM PR (gated by reps):** `OneRMEstimationHelper.calculateOneRM(entry.kg, entry.reps, entry.rpe)` is called for **all intents**. The helper returns `null` when effectiveReps > 15 or RPE < 6.5, so high-rep sets are excluded.
   - **Weight PR:** Heaviest single set weight; all intents.
   - **Reps PR:** Max reps at a given weight. Weight is bucketed with ±1 kg tolerance (bucket = round(weight)); internally stored as `Map<bucket, (maxReps, actualWeightKg)>`. Display string **always uses actual weight** (e.g. "20 reps @ 52.5kg"), not the bucket.

4. **Set Filtering**
   - Warmup sets are excluded (`it.isWarmup == false`)

### API

- **`getRecentPRs(sessions, exerciseLibrary, dayWindow)`**  
  Returns `List<PRRecord>` filtered by `dayWindow` (PRs whose date is within the last `dayWindow` days). Used by Progress Overview "Recent PRs" and by PR page summary stats.

- **`getExerciseStatsSummaries(sessions, exerciseLibrary)`**  
  Returns `List<ExerciseStatsSummary>` for the PR page. One summary per exercise that has at least one PR.  
  **ExerciseStatsSummary:** `exerciseId`, `exerciseName`, `best1RM`, `bestWeight`, `bestVolume`, `bestRepsRecord` (e.g. "22 reps @ 52.5kg"), **`lastPrDate: Long`** (timestamp ms; 0 if no PRs).  
  Caller sorts by `lastPrDate` DESC so the exercise with the most recent PR is at the top.

## PR Record Data Structure

```kotlin
data class PRRecord(
    val exerciseName: String,
    val intent: SetIntent,
    val prType: PRType,
    val value: Float,
    val previousValue: Float? = null,
    val date: String
)
```

## ExerciseStatsSummary (Player Stats Card)

```kotlin
data class ExerciseStatsSummary(
    val exerciseId: Int,
    val exerciseName: String,
    val best1RM: Float?,
    val bestWeight: Float?,
    val bestVolume: Float?,
    val bestRepsRecord: String?,  // e.g. "22 reps @ 52.5kg" (actual weight)
    val lastPrDate: Long          // timestamp ms; 0 if no PRs
)
```

- **lastPrDate** is stored as **Long** (timestamp) so the adapter can format flexibly: e.g. "2 days ago" for recent, "Oct 24, 2025" for older. Sorting by Long is fast and unambiguous.

## Time Window Filtering

- `getRecentPRs(..., dayWindow)` returns only PRs whose date falls within the last `dayWindow` days.
- `getExerciseStatsSummaries()` returns **all** exercises that have at least one PR (no day window); the list is then sorted by `lastPrDate` DESC.

## UI

### PR Page (ProgressPRsFragment)

- **List:** One card per exercise (`item_exercise_pr_card.xml`), bound by **ExercisePRStatsAdapter**.
- **Sorting:** By `lastPrDate` DESC (exercise with most recent PR at top).
- **Card header:** Exercise name + "Last PR: [formatted date]" (adapter formats `lastPrDate` Long: e.g. "Today", "Yesterday", "3 days ago", "Oct 24, 2025").
- **Card body:** Four stats in a row: 1RM | Weight | Volume | Reps (value or "—" if null).
- **Filter chips:** All, Weight, Volume, 1RM, Reps (filter exercises that have that type of PR).
- **Summary card:** Total PRs, This Month, Week Streak (computed from `getRecentPRs(..., large window)`).

### Progress Overview

- "Recent PRs" tiles still use `getRecentPRs(sessions, exerciseLibrary, 30)` and **PRTimelineAdapter** with `item_pr_timeline.xml` (list of PR events, not exercise summaries).

## Related Files

- `ProgressAnalysisHelper.kt` - PR detection, ExerciseStatsSummary, getExerciseStatsSummaries
- `OneRMEstimationHelper.kt` - 1RM calculation (effectiveReps ≤ 15, RPE ≥ 6.5)
- `ExercisePRStatsAdapter.kt` - Binds ExerciseStatsSummary to item_exercise_pr_card; formats lastPrDate (Long)
- `ProgressPRsFragment.kt` - PR page; uses getExerciseStatsSummaries and ExercisePRStatsAdapter
- `PRTimelineAdapter.kt` - Used by Overview for "Recent PRs" tiles (PRRecord list)
- `item_exercise_pr_card.xml` - Player Stats Card layout
- `item_pr_timeline.xml` - Timeline PR item layout (Overview)
