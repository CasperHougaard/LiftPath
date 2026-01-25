# PR (Personal Record) Logic Documentation

## Overview

The PR system tracks personal records across multiple dimensions to help users monitor their strength training progress. PRs are detected automatically as users log their workouts and are displayed in the Progress section of the app.

## PR Types

The system tracks four types of PRs:

1. **WEIGHT PR** - Maximum weight lifted for a specific exercise
2. **VOLUME PR** - Maximum total volume (weight × reps) for a specific exercise in a single session
3. **ONE_RM PR** - Maximum estimated 1-rep max for a specific exercise
4. **REPS PR** - Maximum reps performed (defined but not currently implemented)

## PR Detection Algorithm

### Core Function: `getRecentPRs()`

**Location:** `ProgressAnalysisHelper.kt`

**Parameters:**
- `sessions: List<TrainingSession>` - All training sessions
- `exerciseLibrary: List<ExerciseLibraryItem>` - Exercise definitions
- `dayWindow: Int` - Time window for PR detection (default: 30 days)

**Process:**

1. **Chronological Processing**
   - Sessions are sorted by date (oldest first)
   - PRs are tracked incrementally as the algorithm processes each session
   - This ensures historical accuracy - a PR is only recorded when it's actually achieved

2. **Exercise-Level Tracking**
   - Best values are tracked per exercise ID
   - Each exercise maintains its own PR history independently
   - Uses `ExerciseBests` data structure to track:
     - `maxWeight: Float?` - Highest weight lifted
     - `max1RM: Float?` - Highest estimated 1RM
     - `maxVolume: Float?` - Highest session volume
     - `maxReps: Int?` - Highest reps (for future use)

3. **Set Filtering**
   - Warmup sets are excluded (`it.isWarmup == false`)
   - Only working sets are considered for PR detection

4. **Intent-Aware Processing**
   - Uses `getEffectiveIntent()` to determine the intent of each set
   - Intent affects which PR types are eligible:
     - **STRENGTH intent**: Eligible for Weight PR and 1RM PR
     - **BUILD intent**: Eligible for Weight PR and Volume PR
     - **FLUSH intent**: Eligible for Weight PR only
     - **WARMUP intent**: Excluded from PR detection

## PR Type Details

### 1. Weight PR

**Detection Logic:**
```kotlin
if (entry.kg > (current.maxWeight ?: 0f)) {
    // Record PR if within day window
    if (sessionDate.time >= cutoffDate.time) {
        prs.add(PRRecord(
            exerciseName = exerciseName,
            intent = intent,
            prType = PRType.WEIGHT,
            value = entry.kg,
            previousValue = current.maxWeight,
            date = session.date
        ))
    }
    current.maxWeight = entry.kg
}
```

**Rules:**
- Applies to ALL intents (STRENGTH, BUILD, FLUSH)
- Compares current set weight against historical maximum
- Records previous value for comparison display
- Updates the exercise's max weight tracker

**Example:**
- Previous max: 100kg
- Current set: 102.5kg
- Result: Weight PR recorded (102.5kg)

### 2. 1RM PR

**Detection Logic:**
```kotlin
if (intent == SetIntent.STRENGTH) {
    val oneRM = OneRMEstimationHelper.calculateOneRM(entry.kg, entry.reps, entry.rpe)
    if (oneRM != null && oneRM > (current.max1RM ?: 0f)) {
        // Record PR if within day window
        if (sessionDate.time >= cutoffDate.time) {
            prs.add(PRRecord(...))
        }
        current.max1RM = oneRM
    }
}
```

**Rules:**
- **Only for STRENGTH intent sets**
- Uses `OneRMEstimationHelper.calculateOneRM()` for estimation
- 1RM calculation uses hybrid formula:
  - **Epley's formula** (≤8 reps): `1RM = weight × (1 + reps/30)`
  - **Brzycki's formula** (9-15 reps): `1RM = weight × (36 / (37 - reps))`
- RPE normalization: If RPE is provided, calculates effective reps
  - `effectiveReps = actualReps + (10 - RPE)` (reps in reserve)
- Filters out sets with RPE < 6.5 (too light to be predictive)
- Filters out sets with effective reps > 15 (unreliable for 1RM)

**Example:**
- Set: 100kg × 5 reps @ RPE 8.5
- Effective reps: 5 + (10 - 8.5) = 6.5 → 6 reps
- Estimated 1RM: 100 × (1 + 6/30) = 120kg
- If previous max 1RM was 115kg → 1RM PR recorded (120kg)

### 3. Volume PR

**Detection Logic:**
```kotlin
if (intent == SetIntent.BUILD) {
    val sessionVolumeKey = "${exerciseId}_${session.date}"
    if (!current.sessionVolumes.contains(sessionVolumeKey)) {
        current.sessionVolumes.add(sessionVolumeKey)
        val sessionVolume = session.exercises
            .filter { it.exerciseId == exerciseId && !it.isWarmup }
            .sumOf { (it.kg * it.reps).toDouble() }
            .toFloat()
        
        if (sessionVolume > (current.maxVolume ?: 0f)) {
            // Record PR if within day window
            if (sessionDate.time >= cutoffDate.time) {
                prs.add(PRRecord(...))
            }
            current.maxVolume = sessionVolume
        }
    }
}
```

**Rules:**
- **Only for BUILD intent sets**
- Calculated per session (sum of all sets for that exercise in the session)
- Formula: `sessionVolume = Σ(weight × reps)` for all non-warmup sets
- Uses session key to prevent duplicate calculations for the same session
- Only one Volume PR per exercise per session

**Example:**
- Session sets for "Bench Press":
  - Set 1: 80kg × 10 reps = 800kg
  - Set 2: 80kg × 10 reps = 800kg
  - Set 3: 80kg × 9 reps = 720kg
  - Set 4: 80kg × 8 reps = 640kg
- Total session volume: 2960kg
- If previous max volume was 2800kg → Volume PR recorded (2960kg)

### 4. Reps PR

**Status:** Defined in `PRType` enum but not currently implemented in `getRecentPRs()`

**Future Implementation:**
- Would track maximum reps performed at a given weight
- Could be intent-specific or weight-specific

## PR Record Data Structure

```kotlin
data class PRRecord(
    val exerciseName: String,      // Display name of the exercise
    val intent: SetIntent,          // Intent when PR was achieved
    val prType: PRType,             // Type of PR (WEIGHT, VOLUME, ONE_RM, REPS)
    val value: Float,               // The PR value
    val previousValue: Float? = null, // Previous best (for comparison)
    val date: String               // Date of PR (format: "yyyy/MM/dd")
)
```

## Time Window Filtering

PRs are only recorded if they occur within the specified `dayWindow`:

- Default window: 30 days
- Calculated from current date backwards
- PRs outside the window are still tracked internally but not returned in results
- This allows users to see "recent PRs" vs "all-time PRs"

**Example:**
- `dayWindow = 30`: Only PRs from the last 30 days
- `dayWindow = 365`: PRs from the last year
- `dayWindow = Int.MAX_VALUE`: All PRs ever

## Deduplication

The algorithm uses `distinctBy` to prevent duplicate PRs:

```kotlin
return prs.distinctBy { "${it.exerciseName}_${it.prType}_${it.date}" }
```

This ensures that if the same PR is detected multiple times (edge cases), only one record is kept.

## Weekly Summary Integration

The `getWeeklySummary()` function counts PRs for weekly statistics:

- Currently returns `prCount = 0` (simplified implementation)
- Full implementation would need to cross-reference with PR records
- Used in Progress Overview to show "PRs this week"

## Display and UI

### PR Timeline View
- Shows all PRs sorted by date (most recent first)
- Filterable by PR type (All, Weight, Volume, 1RM)
- Displays:
  - Exercise name
  - Intent badge (STRENGTH, BUILD, FLUSH)
  - PR type and value
  - Improvement vs previous value
  - Date formatted as "MMM dd, yyyy"

### Recent PRs Cards
- Shows top 5 most recent PRs
- Displayed in Progress Overview
- Shows exercise name, value, intent, and date

### PR Statistics
- Total PRs: Count of all PRs ever recorded
- This Month: PRs recorded in current month
- Week Streak: Consecutive weeks with at least one PR

## Edge Cases and Special Considerations

### Multiple PRs in Same Session
- If a user achieves multiple PR types in the same session, all are recorded
- Example: Weight PR (102.5kg) and 1RM PR (120kg) in same session → both recorded

### Same Value PRs
- If a user matches (but doesn't exceed) a previous PR, no new PR is recorded
- Only improvements are tracked

### Intent Changes
- If an exercise is performed with different intents over time, PRs are tracked separately per intent
- However, Weight PRs are universal (not intent-specific)

### Data Quality
- Invalid dates are skipped (try-catch around date parsing)
- Missing exercise data is handled gracefully
- Warmup sets are automatically excluded

### 1RM Estimation Reliability
- Sets with RPE < 6.5 are excluded (too light)
- Sets with effective reps > 15 are excluded (unreliable)
- RPE normalization improves accuracy for submaximal sets

## Future Enhancements

1. **Reps PR Implementation**
   - Track maximum reps at a given weight
   - Could be weight-specific (e.g., "PR for 100kg: 8 reps")

2. **Intent-Specific Weight PRs**
   - Separate Weight PRs by intent (STRENGTH weight PR vs BUILD weight PR)

3. **Relative PRs**
   - PRs adjusted for body weight
   - PRs as percentage of estimated 1RM

4. **PR Streaks**
   - Track consecutive sessions with PRs
   - Track PRs per week/month trends

5. **PR Goals**
   - Allow users to set PR goals
   - Track progress toward goals

## Related Files

- `ProgressAnalysisHelper.kt` - Main PR detection logic
- `OneRMEstimationHelper.kt` - 1RM calculation formulas
- `PRTimelineAdapter.kt` - UI adapter for PR timeline
- `ProgressPRsFragment.kt` - Fragment displaying PR timeline
- `ProgressOverviewFragment.kt` - Shows recent PRs cards
- `DataModels.kt` - PRType enum and SetIntent enum definitions
