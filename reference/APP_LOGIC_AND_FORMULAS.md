# Lift Path - Complete Application Logic and Background Formulae

## Table of Contents

1. [Application Overview](#application-overview)
2. [Core Architecture](#core-architecture)
3. [Data Models](#data-models)
4. [Feature Implementation](#feature-implementation)
5. [Mathematical Formulae and Calculations](#mathematical-formulae-and-calculations)
6. [Progression System](#progression-system)
7. [Workout Generation Logic](#workout-generation-logic)
8. [Fatigue and Recovery System](#fatigue-and-recovery-system)
9. [Data Flow and Persistence](#data-flow-and-persistence)
10. [User Settings and Configuration](#user-settings-and-configuration)
11. [XML Layouts and UI Pages](#xml-layouts-and-ui-pages)
12. [Kotlin Activities and XML Interaction](#kotlin-activities-and-xml-interaction)

---

## Application Overview

**Lift Path** is a comprehensive offline-first Android fitness tracking application designed for serious lifters who want complete control over their training data. The app operates entirely offline, storing all data locally on the device in JSON format, ensuring complete privacy and data ownership.

### Core Value Propositions

- **Fast Workout Logging**: Streamlined interface for logging sets during active training sessions
- **Intelligent Progression**: RPE-based and double-progression algorithms for weight recommendations
- **Scientific 1RM Estimation**: Hybrid formulas with RPE normalization for accurate strength predictions
- **Smart Workout Generation**: Balanced workout creation based on focus (Upper/Lower/Full) and intensity (Heavy/Light)
- **Fatigue Tracking**: Three-score system (lower, upper, systemic) with exponential decay modeling
- **Rest Timer**: RPE-based automatic rest timer with foreground service support
- **Muscle Activation Visualization**: Interactive SVG muscle map showing primary and secondary targets
- **Complete History**: Full workout history with editing capabilities and statistical analysis
- **Data Portability**: Export/import JSON backups for data safety

### Technical Stack

- **Platform**: Android (API 34+)
- **Language**: Kotlin
- **UI Framework**: XML layouts with ViewBinding (no Compose)
- **Persistence**: JSON via Gson library
- **Charting**: MPAndroidChart
- **Storage**: Internal app storage (filesDir/cacheDir)
- **Settings**: SharedPreferences

---

## Core Architecture

### Module Structure

#### 1. UI Layer (Activities + ViewBinding)

- **MainActivity**: Dashboard with workout statistics, customizable 1RM cards, volume chart, days since last workout
- **ActiveTrainingActivity**: Live workout session builder with exercise management, rest timer controls, muscle overview
- **LogSetActivity**: Set logging interface with weight, reps, RPE (6.0-10.0), notes, completion status
- **ExercisesActivity** / **EditExerciseActivity**: Exercise library management with muscle target selection
- **SelectExerciseActivity**: Exercise picker with progression suggestions and muscle activation overview
- **HistoryActivity** / **TrainingDetailActivity**: Workout history viewing and navigation
- **EditSetActivity** / **EditActivityActivity**: Individual set and bulk exercise editing
- **ProgressActivity**: Statistical charts (Weight, Volume, 1RM, Avg Weight, Avg RPE) with tabbed interface
- **SettingsActivity**: Data management (reset, export, import) and settings access
- **ProgressionSettingsActivity**: Comprehensive progression configuration
- **WorkoutPlansActivity** / **EditWorkoutPlanActivity**: Workout plan template management
- **RestTimerDialogActivity**: Full-screen rest timer with controls
- **ReadinessDashboardActivity**: Fatigue monitoring and activity readiness assessment

#### 2. Data Layer

- **JsonHelper**: Wraps all JSON persistence using Gson, handles file corruption with automatic backups
- **TrainingData**: Root data container (exercise library, training sessions, workout plans, user level)
- **ActiveWorkoutDraftManager**: Manages draft workout persistence for recovery after app close
- **ProgressionSettingsManager**: Manages progression settings via SharedPreferences
- **ReadinessSettingsManager**: Manages readiness/fatigue settings via SharedPreferences

#### 3. Logic Layer

- **ProgressionHelper**: Intelligent weight recommendations based on tier-based progression schemes
- **WorkoutGenerator**: Balanced workout creation from blueprints
- **DefaultExercisesHelper**: Curated library of 40+ popular exercises with metadata
- **OneRMEstimationHelper**: Scientific 1RM calculations with RPE normalization and weighted regression
- **MuscleActivationHelper**: Calculates primary/secondary muscle activation for visualization
- **ReadinessHelper**: Fatigue calculation and recovery modeling with three-score system

#### 4. Services

- **RestTimerService**: Foreground service for rest timer countdown with notifications and broadcasts

#### 5. Components

- **MuscleMapDialog**: Bottom sheet dialog with interactive SVG muscle map visualization

---

## Data Models

### Core Enums

```kotlin
enum class UserLevel {
    NOVICE,        // Linear progression
    INTERMEDIATE   // Periodized progression
}

enum class Tier {
    TIER_1,  // Main Lift / Heavy (Linear RPE progression)
    TIER_2,  // Assistance / Volume (Double progression)
    TIER_3   // Accessory / Isolation (Double progression)
}

enum class BodyRegion {
    UPPER,   // Upper body exercises
    LOWER,   // Lower body exercises
    CORE,    // Core exercises
    FULL     // Full body exercises
}

enum class SessionFocus {
    UPPER,   // Upper body focus
    LOWER,   // Lower body focus
    FULL     // Full body
}

enum class SessionIntensity {
    HEAVY,   // Strength-focused (low reps, high weight)
    LIGHT    // Volume/hypertrophy-focused (high reps, moderate weight)
}
```

### Core Data Classes

```kotlin
data class ExerciseLibraryItem(
    val id: Int,
    val name: String,
    val region: BodyRegion?,
    val pattern: MovementPattern?,
    val tier: Tier?,
    val primaryTargets: List<TargetMuscle>,
    val secondaryTargets: List<TargetMuscle>,
    val manualMechanics: Mechanics?
)

data class ExerciseEntry(
    val exerciseId: Int,
    var exerciseName: String,
    val setNumber: Int,
    val kg: Float,
    val reps: Int,
    val note: String?,
    val rating: Int?,           // Legacy 1-5 rating
    val workoutType: String?,   // "heavy", "light", or "custom"
    val rpe: Float?,            // Rate of Perceived Exertion (6.0-10.0)
    val completed: Boolean?     // Whether set was completed
)

data class TrainingSession(
    val id: String,                    // UUID
    val trainingNumber: Int,           // Auto-incrementing
    val date: String,                  // yyyy/MM/dd
    val exercises: MutableList<ExerciseEntry>,
    val defaultWorkoutType: String?,   // "heavy", "light", or "custom"
    val planId: String?,               // Applied workout plan ID
    val planName: String?,             // Applied workout plan name
    val durationSeconds: Long?         // Workout duration
)

data class TrainingData(
    val exerciseLibrary: MutableList<ExerciseLibraryItem>,
    val trainings: MutableList<TrainingSession>,
    val workoutPlans: MutableList<WorkoutPlan>,
    var userLevel: UserLevel
)
```

---

## Feature Implementation

### 1. Workout Logging

#### Active Training Session Flow

1. User selects workout type (Heavy/Light/Custom) or uses smart workout generation
2. Exercises are added via `SelectExerciseActivity` or from saved workout plans
3. Sets are logged via `LogSetActivity` with:
   - Pre-filled weight from last session
   - Suggested weight from `ProgressionHelper.getSuggestion()`
   - Suggested RPE based on user level and workout type
   - Optional note field
   - Completion checkbox
4. After saving a set, rest timer automatically starts (if enabled) with RPE-based adjustments
5. Sets are grouped per exercise in-memory as `GroupedExercise`
6. Sets can be duplicated, edited (bulk via `EditActivityActivity`), or removed before saving
7. Workout date can be changed via `DatePickerDialog`
8. Draft is saved to `active_workout_draft.json` for recovery after app close

#### Set Logging Logic

**Weight Pre-fill Priority:**
1. Last logged weight for that exercise (same workout type)
2. Suggested weight from `ProgressionHelper.getSuggestion()`

**RPE Suggestion Formula:**

```
IF workoutType == "heavy":
    IF userLevel == NOVICE:
        suggestedRPE = 8.0
    ELSE:  // INTERMEDIATE
        suggestedRPE = 8.5
ELSE:  // "light"
    IF userLevel == NOVICE:
        suggestedRPE = 7.0
    ELSE:  // INTERMEDIATE
        suggestedRPE = 7.5
```

### 2. Exercise Library Management

- Exercises are stored in `TrainingData.exerciseLibrary`
- Each exercise has:
  - Unique ID (auto-incrementing)
  - Name, body region, movement pattern, tier
  - Primary and secondary target muscles
  - Mechanics (Compound/Isolation - auto-computed if not manual)
- Exercise rename cascades to all historical `ExerciseEntry` records
- Exercise deletion removes matching sets from all historical sessions
- Interactive muscle map visualization during editing

### 3. Workout Plans

- Templates containing exercise IDs, workout type, optional notes, creation date
- Plans can be applied when starting a workout to auto-populate exercises
- Applied plan ID and name are stored in the resulting `TrainingSession`
- Plans can be created, edited, and deleted independently

### 4. Progress Tracking

**ProgressActivity Features:**
- Exercise selector (spinner) showing all exercises with logged sets
- Tabbed charts:
  - **Weight**: Maximum weight per session over time
  - **Volume**: Total volume (weight × reps) per session
  - **1RM**: Estimated one-rep max per session
  - **Avg Weight**: Average weight across all sets per session
  - **Avg RPE**: Average RPE across all sets per session
- Statistics display:
  - Max Volume (per session)
  - Max Weight
  - Average Weight
  - Average RPE
  - Total Reps

### 5. Rest Timer

**Automatic Start Logic:**
- After logging a set (if rest timer enabled in settings)
- Base duration from workout type:
  - Heavy: `settings.heavyRestSeconds` (default: 150s / 2.5 min)
  - Light: `settings.lightRestSeconds` (default: 60s / 1 min)
  - Custom: `settings.customRestSeconds` (default: 120s / 2 min)

**RPE-Based Adjustments:**

IF `settings.rpeAdjustmentEnabled == true`:
    
    baseTime = getBaseTimeForWorkoutType(workoutType)
    
    // High RPE bonus
    IF loggedRPE >= settings.rpeHighThreshold (default: 9.0):
        adjustedTime = baseTime + settings.rpeHighBonusSeconds (default: +60s)
    
    // Deviation from suggested RPE
    suggestedRPE = getSuggestedRPE(userLevel, workoutType)
    deviation = loggedRPE - suggestedRPE
    
    IF deviation >= settings.rpeDeviationThreshold (default: 1.0):
        adjustedTime += settings.rpePositiveAdjustmentSeconds (default: +30s)
    
    IF deviation <= -settings.rpeDeviationThreshold:
        adjustedTime = max(0, adjustedTime - settings.rpeNegativeAdjustmentSeconds (default: -15s))
    
    finalRestSeconds = adjustedTime

**Rest Timer Features:**
- Foreground service with persistent notification
- Interactive dialog with controls: start/pause, reset/stop, +/-15s, set custom time
- Notification actions: +15s, -15s, Stop
- Vibration pattern on completion
- Auto-dismissing completion notification (10s)
- Broadcast updates for UI synchronization

---

## Mathematical Formulae and Calculations

### 1. Volume Calculation

**Volume per Set:**
```
volume = weight × reps
```

**Volume per Session:**
```
sessionVolume = Σ(set.weight × set.reps) for all sets in session
```

**Average Weight:**
```
avgWeight = sessionVolume / totalReps
```

**Total Reps:**
```
totalReps = Σ(set.reps) for all sets
```

### 2. One-Rep Maximum (1RM) Estimation

#### A. RPE Normalization (Reps In Reserve)

When RPE is provided, the system calculates effective reps to account for reps in reserve:

```
repsInReserve (RIR) = 10 - RPE

effectiveReps = actualReps + RIR
```

**Example:**
- Actual reps: 5
- RPE: 8.0
- RIR = 10 - 8.0 = 2.0
- Effective reps = 5 + 2 = 7

This normalizes submaximal sets to failure-equivalent reps for accurate 1RM estimation.

#### B. Hybrid 1RM Formula Selection

The system uses different formulas based on effective rep count:

**Rule A: Intensity Filtering**
- Sets with RPE < 6.5 are discarded (too light to be predictive)
- Sets with effective reps > 15 are discarded (statistically unreliable)

**Rule B: Formula Selection**

```
IF effectiveReps <= 8:
    // Epley's Formula (better for lower rep ranges)
    1RM = weight × (1 + effectiveReps / 30)
    
ELSE IF effectiveReps <= 15:
    // Brzycki's Formula (more conservative for higher reps)
    1RM = weight × (36 / (37 - effectiveReps))
    
    // Safety check: Brzycki invalid if reps >= 37
    IF effectiveReps >= 37:
        return null  // Invalid
```

**Mathematical Rationale:**

- **Epley's Formula**: Linear relationship, works well for 1-8 reps
  - Formula: `1RM = w × (1 + r/30)`
  - Example: 100kg × 5 reps = 100 × (1 + 5/30) = 100 × 1.167 = 116.7kg

- **Brzycki's Formula**: Non-linear relationship, more conservative for 9-15 reps
  - Formula: `1RM = w × (36 / (37 - r))`
  - Example: 80kg × 12 reps = 80 × (36 / (37 - 12)) = 80 × (36/25) = 80 × 1.44 = 115.2kg

**Maximum 1RM per Session:**
```
sessionMax1RM = max(calculateOneRM(set.weight, set.reps, set.rpe)) 
                for all sets in session with valid RPE
```

### 3. 1RM Progression Estimation

The system uses **weighted linear regression with exponential decay** to project future 1RM values.

#### A. Data Preparation

1. Filter sets by workout type (prefer heavy sessions)
2. Calculate 1RM for each session using hybrid formula with RPE normalization
3. Group by date and take maximum 1RM per session
4. Sort chronologically

#### B. Weighted Linear Regression

**Weight Calculation (Exponential Decay):**

```
λ = 0.02  // Decay constant (~35 day half-life)

weight_i = e^(-λ × daysAgo_i)
```

This means:
- Recent data (today) has weight ≈ 1.0
- Data from ~35 days ago has weight ≈ 0.5
- Data from ~70 days ago has weight ≈ 0.25

**Weighted Means:**

```
meanX_weighted = (Σ(w_i × x_i)) / Σ(w_i)
meanY_weighted = (Σ(w_i × y_i)) / Σ(w_i)
```

Where:
- `x_i` = days since first session
- `y_i` = 1RM value for session i

**Weighted Slope (m):**

```
m = Σ(w_i × (x_i - meanX_weighted) × (y_i - meanY_weighted)) / 
    Σ(w_i × (x_i - meanX_weighted)²)
```

**Weighted Intercept (b):**

```
b = meanY_weighted - m × meanX_weighted
```

#### C. Projection with Damping

**Undamped Projection:**

```
daysToProject = (projectionDate - currentDate) in days
undampedProjection = m × daysToProject + b
```

**Damping Factor (Law of Diminishing Returns):**

```
dampingFactor = {
    1 month:   1.0   (no damping)
    2 months:  0.9   (10% reduction)
    3 months:  0.8   (20% reduction)
    6 months:  0.5   (50% reduction)
    >6 months: 0.3   (70% reduction)
}
```

**Final Projected 1RM:**

```
projectedGain = (undampedProjection - current1RM) × dampingFactor
expected1RM = current1RM + projectedGain
```

**Improvement Metrics:**

```
improvementKg = expected1RM - current1RM
improvementPercent = (improvementKg / current1RM) × 100
```

#### D. Data Quality Checks

**Minimum Data Points:**
- Requires at least 2 sessions for regression
- Recommended: 5+ sessions for reliable projections

**Recent Data Window:**
- Default: 30 days
- If last session > 30 days ago: warning flag

**Consistency Check (Coefficient of Variation):**

```
mean = average(1RM_values)
variance = average((1RM_i - mean)²)
stdDev = √variance
coefficientOfVariation = stdDev / mean

IF coefficientOfVariation > 0.15 (15%):
    Warning: "Inconsistent progression"
```

**Qualification Flag:**

```
isQualified = (
    uniqueSessions >= minDataPoints AND
    daysSinceLastSession <= recentDataWindowDays AND
    coefficientOfVariation <= 0.15
)
```

### 4. Chart Axis Scaling

The system calculates "nice" maximum values for Y-axis scaling:

**Nice Maximum Formula:**

```
IF maxValue <= 0:
    return 100  // Default minimum

paddedValue = maxValue × 1.15  // Add 15% padding

// Round up based on magnitude
IF paddedValue < 10:
    niceMax = ceil(paddedValue / 2) × 2  // Round to nearest 2
ELSE IF paddedValue < 50:
    niceMax = ceil(paddedValue / 5) × 5  // Round to nearest 5
ELSE IF paddedValue < 100:
    niceMax = ceil(paddedValue / 10) × 10  // Round to nearest 10
ELSE IF paddedValue < 500:
    niceMax = ceil(paddedValue / 25) × 25  // Round to nearest 25
ELSE IF paddedValue < 1000:
    niceMax = ceil(paddedValue / 50) × 50  // Round to nearest 50
ELSE:
    niceMax = ceil(paddedValue / 100) × 100  // Round to nearest 100
```

This ensures readable chart scaling with sensible tick intervals.

---

## Progression System

The progression system uses **tier-based progression schemes** to provide intelligent weight recommendations.

### Tier-Based Progression Schemes

1. **TIER_1 (Main Lifts)**: Linear RPE Progression
   - Adjusts weight based on RPE of last session
   - Keeps reps constant (typically 5)

2. **TIER_2/3 (Assistance/Accessories)**: Double Progression
   - Increases reps until target range hit, then increases weight
   - Resets reps to minimum when weight increases

### Linear RPE Progression (Tier 1)

**Priority-Based Adjustment Logic:**

#### Priority 1: Time Decay

```
daysSince = days between last workout and today

IF daysSince >= 60:
    decayMultiplier = 0.85  // -15%
    badge = "🕐 TIME DECAY"
ELSE IF daysSince >= 30:
    decayMultiplier = 0.90  // -10%
    badge = "🕐 TIME DECAY"
ELSE IF daysSince >= 14:
    decayMultiplier = 0.95  // -5%
    badge = "🕐 TIME DECAY"
ELSE:
    decayMultiplier = 1.0   // No decay

IF decayMultiplier < 1.0:
    decayedWeight = lastWeight × decayMultiplier
    adjustment = decayedWeight - lastWeight
```

**Example:**
- Last weight: 100kg
- Days since: 45
- Decay multiplier: 0.90
- Decayed weight: 90kg
- Adjustment: -10kg

#### Priority 2: Failure Detection

```
IF lastSession.hadFailure (completed == false):
    adjustment = -settings.increaseStep  // Default: -2.5kg
    badge = "⚠️ FAILED REPS"
```

#### Priority 3: Standard RPE Logic

```
rpe = lastSession.rpe

IF rpe <= 7.0:
    adjustment = +settings.increaseStep  // Default: +2.5kg
ELSE IF rpe <= 8.5:
    adjustment = +settings.smallStep     // Default: +1.25kg
ELSE IF rpe < 9.5:
    adjustment = 0                       // Maintain
ELSE:  // rpe >= 9.5
    adjustment = -settings.smallStep     // Default: -1.25kg
```

**Final Weight Calculation:**

```
proposedWeight = roundToIncrement(
    lastWeight + adjustment,
    settings.roundTo  // Default: 1.25kg
)

// Round to increment function
roundToIncrement(weight, increment):
    return floor(weight / increment + 0.5) × increment
```

**Example:**
- Last weight: 100kg
- Adjustment: +2.5kg
- Round to: 1.25kg
- Proposed: 102.5kg (exact match, no rounding needed)

### Double Progression (Tier 2/3)

**Target Rep Ranges:**

```
IF workoutType == "heavy":
    minReps = settings.heavyReps - 2  // Default: 5 - 2 = 3
    maxReps = settings.heavyReps + 2  // Default: 5 + 2 = 7
ELSE:  // "light"
    minReps = settings.lightReps      // Default: 10
    maxReps = settings.lightReps + 5  // Default: 15
```

**Progression Logic:**

```
IF lastSession.hadFailure:
    newWeight = lastWeight  // Retry same weight
    newReps = lastReps
    badge = "🔁 RETRY"

ELSE IF lastReps >= maxReps:
    // Hit top range - increase weight, reset reps
    newWeight = roundToIncrement(
        lastWeight + settings.smallStep,
        settings.roundTo
    )
    newReps = minReps
    badge = "🚀 LEVEL UP"

ELSE:
    // Build volume - add one rep
    newWeight = lastWeight
    newReps = lastReps + 1
    badge = "➕ ADD REP"
```

**Example Progression:**
- Start: 50kg × 10 reps (light day, target range 10-15)
- Session 1: 50kg × 11 reps
- Session 2: 50kg × 12 reps
- ...
- Session 6: 50kg × 15 reps (hit max)
- Session 7: 51.25kg × 10 reps (level up)

### RPE Suggestion

The system suggests appropriate RPE based on user level and workout type:

```
IF workoutType == "heavy":
    IF userLevel == NOVICE:
        suggestedRPE = 8.0
    ELSE:  // INTERMEDIATE
        suggestedRPE = 8.5
ELSE:  // "light"
    IF userLevel == NOVICE:
        suggestedRPE = 7.0
    ELSE:  // INTERMEDIATE
        suggestedRPE = 7.5
```

---

## Workout Generation Logic

The `WorkoutGenerator` creates balanced workouts based on focus and intensity using predefined blueprints.

### Blueprint Structure

#### Upper Body Heavy

```
1. Main Horizontal Push (Tier 1) - 1 exercise
   - Pattern: PUSH_HORIZONTAL
   - Example: Bench Press
   - Volume: 3 sets × 5 reps

2. Main Vertical Pull (Tier 1) - 1 exercise
   - Pattern: PULL_VERTICAL or PULL_HORIZONTAL
   - Example: Weighted Pullup / Heavy Row
   - Volume: 3 sets × 5 reps

3. Secondary Vertical Push (Tier 1/2) - 1 exercise
   - Pattern: PUSH_VERTICAL
   - Example: Overhead Press
   - Volume: 3 sets × 5 reps (Tier 1) or 3 sets × 8 reps (Tier 2)

4. Secondary Pull (Tier 2) - 1 exercise
   - Pattern: PULL_HORIZONTAL or PULL_VERTICAL
   - Example: Barbell Row
   - Volume: 3 sets × 8 reps

5. Arms/Shoulders Isolation (Tier 3) - 2 exercises
   - Patterns: ISOLATION_ELBOW_FLEXION, ISOLATION_ELBOW_EXTENSION, 
               ISOLATION_SHOULDER_ABDUCTION
   - Example: Bicep Curl, Tricep Extension, Lateral Raise
   - Volume: 3 sets × 12 reps each
```

#### Upper Body Light (Hypertrophy)

```
1. Chest Variation (Tier 2/3) - 1 exercise
   - Volume: 3 sets × 12 reps

2. Back Volume (Tier 2) - 2 exercises
   - Volume: 3 sets × 12 reps each

3. Shoulder Volume (Tier 2/3) - 2 exercises
   - Volume: 3 sets × 12 reps each

4. Arm Pump (Tier 3) - 2 exercises
   - Volume: 3 sets × 15 reps each
```

#### Lower Body Heavy

```
1. Main Squat (Tier 1) - 1 exercise
   - Pattern: SQUAT
   - Example: Back Squat
   - Volume: 3 sets × 5 reps

2. Main Hinge (Tier 1) - 1 exercise
   - Pattern: HINGE
   - Example: Deadlift
   - Volume: 3 sets × 5 reps

3. Unilateral/Lunge (Tier 2) - 1 exercise
   - Pattern: LUNGE
   - Example: Bulgarian Split Squat
   - Volume: 3 sets × 8 reps

4. Calves/Isolation (Tier 3) - 1 exercise
   - Patterns: ISOLATION_PLANTAR_FLEXION, ISOLATION_KNEE_EXTENSION
   - Volume: 3 sets × 12 reps
```

#### Lower Body Light (Hypertrophy)

```
1. Quad Focus (Tier 2) - 2 exercises
   - Patterns: SQUAT, LUNGE
   - Volume: 3 sets × 12 reps each

2. Hamstring/Glute Focus (Tier 2/3) - 2 exercises
   - Patterns: HINGE, ISOLATION_KNEE_FLEXION
   - Volume: 3 sets × 12 reps each

3. Isolation (Tier 3) - 2 exercises
   - Patterns: ISOLATION_KNEE_EXTENSION, ISOLATION_PLANTAR_FLEXION
   - Volume: 3 sets × 15 reps each
```

#### Full Body

```
1. Squat Pattern (Tier 1/2) - 1 exercise
   - Volume: 3 sets × 5 reps (Tier 1) or 3 sets × 8 reps (Tier 2)

2. Hinge Pattern (Tier 1/2) - 1 exercise
   - Volume: 3 sets × 5 reps (Tier 1) or 3 sets × 8 reps (Tier 2)

3. Push Pattern (Tier 1/2) - 1 exercise
   - Volume: 3 sets × 5 reps (Tier 1) or 3 sets × 8 reps (Tier 2)

4. Pull Pattern (Tier 1/2) - 1 exercise
   - Volume: 3 sets × 5 reps (Tier 1) or 3 sets × 8 reps (Tier 2)

5. Core/Carry/Arms (Tier 3) - 1 exercise
   - Volume: 3 sets × 12 reps
```

### Volume Recommendations

**Heavy Day:**
- Tier 1: 3 sets × 5 reps
- Tier 2: 3 sets × 8 reps
- Tier 3: 3 sets × 12 reps

**Light Day:**
- Tier 1: 3 sets × 10 reps
- Tier 2: 3 sets × 12 reps
- Tier 3: 3 sets × 15 reps

### Exercise Selection Algorithm

```
FOR each slot in blueprint:
    1. Filter library by:
       - Matching movement pattern
       - Matching tier preference
       - Not already selected
    
    2. Sort candidates by tier priority (preferred tiers first)
    
    3. Pick top N exercises (where N = slot.count)
    
    4. Add to workout with recommended sets/reps
```

---

## Fatigue and Recovery System

The app implements a sophisticated **three-score fatigue system** (lower, upper, systemic) with exponential decay modeling.

### Fatigue Calculation

#### Raw Fatigue from Workout

**Per Exercise Set Load:**

```
setLoad = Σ(RPE × tierMultiplier) for all sets of exercise

WHERE:
    RPE = set.rpe OR config.defaultRPE (default: 7.0)
    tierMultiplier = {
        TIER_1: 1.5
        TIER_2: 1.2
        TIER_3: 0.8
        null:   1.0
    }
```

**Regional Distribution:**

```
IF exercise.region == LOWER:
    lowerFatigue += setLoad

ELSE IF exercise.region == UPPER:
    upperFatigue += setLoad

ELSE IF exercise.region == FULL:
    lowerFatigue += setLoad × 0.7
    upperFatigue += setLoad × 0.5

ELSE IF exercise.region == CORE:
    // Minimal contribution (optional)
```

**Systemic Fatigue:**

```
systemicFatigue = lowerFatigue + upperFatigue
```

**Example Calculation:**

Workout:
- Squat (Tier 1, Lower): 3 sets × 5 reps @ 9.0 RPE
- Deadlift (Tier 1, Lower): 2 sets × 5 reps @ 8.5 RPE

```
Squat load = 3 × (9.0 × 1.5) = 3 × 13.5 = 40.5
Deadlift load = 2 × (8.5 × 1.5) = 2 × 12.75 = 25.5

lowerFatigue = 40.5 + 25.5 = 66.0
upperFatigue = 0
systemicFatigue = 66.0
```

### Recovery Time Calculation

**Base Recovery Hours (Diminishing Returns Logic):**

```
IF fatigue > config.thresholds.high (default: 50):
    excess = fatigue - 50
    extraHours = excess × 0.15  // Damped impact
    baseHours = min(48 + extraHours, 96)  // Max 4 days

ELSE IF fatigue >= config.thresholds.moderate (default: 30):
    baseHours = 24

ELSE:  // fatigue < 30
    baseHours = 12
```

**Speed Multiplier Application:**

```
adjustedHours = baseHours / config.recoverySpeedMultiplier

WHERE:
    recoverySpeedMultiplier = {
        Normal: 1.0
        Fast:   1.2 (20% faster recovery)
        Slow:   0.8 (25% slower recovery)
    }
```

**Recovery Time in Milliseconds:**

```
recoveryTimeMs = adjustedHours × 3600 × 1000
```

**Example:**
- Fatigue: 66.0
- Excess: 66.0 - 50 = 16.0
- Extra hours: 16.0 × 0.15 = 2.4
- Base hours: min(48 + 2.4, 96) = 50.4 hours
- With normal recovery (multiplier 1.0): 50.4 hours ≈ 2.1 days

### Fatigue Decay Model

**Exponential Decay Function (Half-Life):**

```
decayedScore = originalScore × (0.5 ^ (hoursElapsed / 48.0))
```

**Mathematical Properties:**
- Based on biological CNS recovery patterns (Half-Life)
- Half-life is set to **48 hours**
- Recovery is rapid initially, then slows as it approaches baseline
- Formula in code: `val decayFactor = 0.5.pow(hoursElapsed / 48.0)`

**Example:**
- Original fatigue: 60.0
- Time elapsed: 24 hours (half of 48-hour half-life)

```
decayFactor = 0.5 ^ (24 / 48.0) = 0.5 ^ 0.5 = 0.707
decayedScore = 60.0 × 0.707 = 42.4
```

**After 48 hours (one half-life):**
```
decayFactor = 0.5 ^ (48 / 48.0) = 0.5 ^ 1.0 = 0.5
decayedScore = 60.0 × 0.5 = 30.0
```

### Continuous Fatigue Timeline

The system simulates fatigue hour-by-hour using a bucket method:

**Hour-by-Hour Simulation:**

```
FOR each hour from startTime to endTime:
    1. ACCUMULATE: Add fatigue from workouts ending in this hour
    2. DECAY: Apply hourly decay to all three stacks independently
    3. STORE: Save current fatigue values as graph point
    4. SNAPSHOT: Store end-of-day values for calendar view
```

**Hourly Decay Calculation (Exponential):**

```
// Calculate hourly decay factor for 48-hour half-life
// This results in a 50% drop over 48 hours when applied each hour
hourlyDecayFactor = 0.5 ^ (1.0 / 48.0)

IF currentFatigue > 0:
    newFatigue = currentFatigue × hourlyDecayFactor
```

**Mathematical Explanation:**
- Applying the factor 48 times: `(0.5 ^ (1/48)) ^ 48 = 0.5 ^ 1 = 0.5`
- This means exactly 50% reduction after 48 hours (one half-life)

**Accumulation Logic:**

```
currentLowerStack += newLowerFatigue
currentUpperStack += newUpperFatigue
currentSystemicStack += newSystemicFatigue

// Then apply decay to each stack independently
```

### Activity Readiness Status

**Lower Body Lifting:**

```
IF systemicFatigue > config.thresholds.cnsMax (default: 80):
    status = RED
    message = "CNS burnout - Full rest required"

ELSE IF lowerFatigue > config.thresholds.high (default: 50):
    status = RED
    message = "Blocked - Rest required"

ELSE IF lowerFatigue >= config.thresholds.moderate (default: 30):
    status = YELLOW
    message = "Caution - Light work only"

ELSE:
    status = GREEN
    message = "Ready to go"
```

**Upper Body Lifting:**

Same logic, but checks `upperFatigue` instead of `lowerFatigue`.

**Running/Cycling:**

```
IF lowerFatigue > config.thresholds.high:
    IF config.allowRunningOnTiredLegs == false:
        status = RED
        message = "Blocked - Rest required"
    ELSE:
        status = YELLOW
        message = "Caution - Easy Zone 2 only"

ELSE IF lowerFatigue >= config.thresholds.moderate:
    status = YELLOW
    message = "Caution - Easy Zone 2 only"

ELSE:
    status = GREEN
    message = "Ready to go"
```

**Swimming:**

Same as upper body lifting, but checks `upperFatigue`.

**Weekend Override:**

```
IF config.ignoreWeekends == true AND isWeekend():
    status = GREEN
    message = "Weekend mode - Warnings disabled"
```

---

## Data Flow and Persistence

### Storage Structure

**Training Data File:**
- Path: `context.filesDir/training_data.json`
- Format: Entire `TrainingData` object serialized as JSON via Gson
- Encoding: UTF-8

**Draft File:**
- Path: `context.cacheDir/active_workout_draft.json`
- Format: `ActiveWorkoutDraft` object as JSON
- Lifecycle: Cleared on successful workout save or cancellation

**Settings:**
- Storage: SharedPreferences
- Key: `progression_settings` and `readiness_settings`
- Format: Key-value pairs

### JSON Structure Example

```json
{
  "exerciseLibrary": [
    {
      "id": 1,
      "name": "Deadlift (Barbell)",
      "region": "LOWER",
      "pattern": "HINGE",
      "tier": "TIER_1",
      "primaryTargets": ["HAMSTRINGS", "GLUTES", "LOWER_BACK"],
      "secondaryTargets": ["TRAPS_UPPER", "FOREARMS", "QUADS"],
      "manualMechanics": "COMPOUND"
    }
  ],
  "trainings": [
    {
      "id": "b122a9de-47fd-4c93-8177-54038a5f4a7c",
      "trainingNumber": 5,
      "date": "2025/11/13",
      "defaultWorkoutType": "heavy",
      "planId": null,
      "planName": null,
      "exercises": [
        {
          "exerciseId": 3,
          "exerciseName": "Bench Press",
          "setNumber": 1,
          "kg": 82.5,
          "reps": 5,
          "workoutType": "heavy",
          "rpe": 8.5,
          "completed": true,
          "note": "Good energy"
        }
      ]
    }
  ],
  "workoutPlans": [],
  "userLevel": "NOVICE"
}
```

### Data Flow

#### App Launch (MainActivity)

```
1. JsonHelper.readTrainingData() loads or initializes TrainingData
2. IF exerciseLibrary.isEmpty():
     Seed default exercises from DefaultExercisesHelper
3. Update dashboard stats:
     - Total workout count
     - Customizable 1RM cards (default: Bench Press, Squat)
     - Days since last heavy/light workout
     - Volume chart
4. Check for draft workouts and prompt to resume
```

#### Active Workout Session

```
1. User selects workout type OR uses smart workout generation
2. Exercises added via SelectExerciseActivity
3. Sets logged via LogSetActivity:
     - Pre-filled from last session
     - Suggested weight from ProgressionHelper
     - Suggested RPE based on user level
4. Rest timer auto-starts (if enabled) with RPE adjustments
5. Draft saved to active_workout_draft.json periodically
6. User finishes workout:
     - TrainingSession created with GUID and next trainingNumber
     - Appended to trainingData.trainings
     - Written to disk via JsonHelper.writeTrainingData()
     - Draft file cleared
```

#### History Editing

```
1. HistoryActivity shows workouts (newest first)
2. Tapping workout opens TrainingDetailActivity
3. Sets grouped visually per exercise
4. EditSetActivity: Edit individual set
5. EditActivityActivity: Bulk edit all sets for exercise
6. Delete sets or entire session (with confirmation)
7. Changes immediately written to disk
```

#### Data Safety

**Corruption Handling:**
```
IF JSON parse fails:
    1. Rename corrupted file to training_data.json.bak.<timestamp>
    2. Initialize fresh TrainingData
    3. Log error
```

**Export/Import:**
```
EXPORT:
    1. Read training_data.json
    2. Create timestamped backup file
    3. Save via Storage Access Framework

IMPORT:
    1. Validate JSON structure
    2. Create backup of current data
    3. Overwrite training_data.json
    4. Reload data
```

**Cascading Updates:**
- Exercise rename: Updates `exerciseName` in all historical `ExerciseEntry` records
- Exercise delete: Removes matching sets from all historical sessions
- These operations mutate historical data to maintain consistency

---

## User Settings and Configuration

### Progression Settings

**User Level:**
- `NOVICE`: Linear progression, lower RPE suggestions
- `INTERMEDIATE`: Periodized progression, higher RPE suggestions

**Progression Steps:**
- `increaseStep`: Default 2.5kg (for RPE ≤ 7.0)
- `smallStep`: Default 1.25kg (for RPE 7.1-8.5 or double progression)
- `roundTo`: Default 1.25kg (weight rounding increment)

**Volume Defaults:**
- `heavySets`: Default 3
- `heavyReps`: Default 5
- `lightSets`: Default 4
- `lightReps`: Default 10

**Time Decay:**
- Thresholds: [14, 30, 60] days
- Multipliers: [0.95, 0.90, 0.85] (5%, 10%, 15% reduction)

**Deload Settings:**
- `deloadThreshold`: Default 3 consecutive sessions
- `deloadRPEThreshold`: Default 9.0

**Rest Timer Settings:**
- `restTimerEnabled`: Default true
- `heavyRestSeconds`: Default 150s (2.5 min)
- `lightRestSeconds`: Default 60s (1 min)
- `customRestSeconds`: Default 120s (2 min)

**RPE Timer Adjustments:**
- `rpeAdjustmentEnabled`: Default true
- `rpeHighThreshold`: Default 9.0
- `rpeHighBonusSeconds`: Default 60s
- `rpeDeviationThreshold`: Default 1.0
- `rpePositiveAdjustmentSeconds`: Default 30s
- `rpeNegativeAdjustmentSeconds`: Default 15s

### Readiness Settings

**Recovery Configuration:**
- `recoverySpeedMultiplier`: Default 1.0 (Normal)
  - Options: 0.8 (Slow), 1.0 (Normal), 1.2 (Fast)
- `defaultRPE`: Default 7.0 (for sets without RPE)

**Thresholds:**
- `highThreshold`: Default 50 (configurable)
- `moderateThreshold`: Fixed 30
- `cnsMax`: Fixed 80

**Activity Preferences:**
- `allowRunningOnTiredLegs`: Default false
  - If true: Allows easy runs even with high lower fatigue
  - If false: Strict blocking for running with high fatigue
- `ignoreWeekends`: Default false
  - If true: Disables warnings on weekends

### Data Management

**Reset Options:**
- Reset all data (keeps default exercise library)
- Export JSON backup
- Import JSON backup

**Backup Strategy:**
- Automatic backup before import
- Timestamped export files
- Corruption recovery with backup restoration

---

## XML Layouts and UI Pages

The application uses **XML-based layouts** with **ViewBinding** for type-safe view access. All layouts follow Material Design 3 principles with a consistent design system.

### Layout Architecture

#### Common Patterns

**1. Background Flow Animation:**
All major activity layouts include an animated background:
```xml
<ImageView
    android:id="@+id/image_bg_animation"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:alpha="1.0"
    android:scaleType="centerCrop"
    app:srcCompat="@drawable/avd_background_flow" />
```

**2. Scrollable Content:**
Most activities use `NestedScrollView` for scrollable content:
```xml
<androidx.core.widget.NestedScrollView
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">
    <!-- Content -->
</androidx.core.widget.NestedScrollView>
```

**3. Card-Based UI:**
Primary UI elements use `CardView` with consistent styling:
- Corner radius: `20dp` (standard cards), `24dp` (hero cards)
- Elevation: `2dp` (standard), `4dp` (elevated), `6dp` (action buttons), `8dp` (hero buttons)
- Background: `@color/fitness_card_background`

**4. ConstraintLayout:**
Primary layout container for complex layouts, providing flexible positioning and responsive design.

### Activity Layouts

#### 1. MainActivity (`activity_main.xml`)

**Structure:**
- Background flow animation (full screen)
- Scrollable content container
- Header section (welcome title/subtitle)
- Hero card: "Start Workout" button (100dp height, primary color)
- Grid actions (2×2): Progress, Exercises, History, Plans
- Readiness card
- Settings button (top-right, 48dp circular)
- Stats section:
  - Two customizable 1RM cards (Bench Press, Squat by default)
  - Days since last workout card
  - Volume trends chart (MPAndroidChart LineChart)

**Key Components:**
- `card_start_workout`: Hero action button
- `card_view_progress`, `card_exercises`, `card_view_history`, `card_plans`: Grid navigation
- `card_readiness`: Readiness dashboard entry
- `card_bench_press`, `card_squat`: Customizable 1RM displays
- `card_days_since`: Consistency tracker
- `card_volume_chart`: Volume trends visualization

#### 2. ActiveTrainingActivity (`activity_active_training.xml`)

**Structure:**
- Background flow animation
- Toolbar (transparent, custom title)
- Date header with workout plan button and muscle overview button
- Workout timer display
- RecyclerView for exercises (with bottom padding for fixed buttons)
- Fixed timer card (permanent, with controls)
- Fixed action buttons (bottom):
  - "Add Exercise" (left, card background)
  - "Finish" (right, primary color)

**Key Components:**
- `layout_date_header`: Date picker, plan selector, workout timer, muscle overview
- `recycler_view_active_workout`: Exercise list
- `card_timer_container`: Rest timer controls (start/pause, ±15s, reset)
- `button_add_exercise`, `button_finish_workout`: Fixed bottom actions

**Timer Card Features:**
- Start/pause button
- ±15s adjustment buttons
- Large timer display (32sp, bold, clickable for custom time)
- Reset/stop button

#### 3. LogSetActivity (`activity_log_set.xml`)

**Structure:**
- Background flow animation
- Scrollable content
- Header with back button and exercise title
- Suggestion hint card (conditional, accent color)
- Set details card:
  - Weight and Reps input fields (side-by-side)
  - Completion checkbox
  - RPE section (with help button)
  - Note field
- Fixed "Save Set" button (bottom, primary color)

**Key Components:**
- `tvSuggestionHint`: Progression suggestion display
- `card_set_details`: Main input form
- `text_input_layout_kg`, `text_input_layout_reps`: Material text fields
- `cbCompleted`: Completion checkbox
- `text_input_layout_rpe`: RPE input (6.0-10.0)
- `button_save_set`: Save action

#### 4. ReadinessDashboardActivity (`activity_readiness_dashboard.xml`)

**Structure:**
- Background flow animation
- Scrollable content
- Header with back button, title, and settings button
- Last workout summary card (fatigue breakdown)
- Activity readiness matrix (2×2 grid):
  - Run/Cycle card
  - Swim card
  - Lower Body Lift card
  - Upper Body Lift card
- 7-day fatigue calendar card (expandable)
- Health Connect sync button
- Empty state message

**Key Components:**
- `card_last_workout`: Fatigue summary display
- `card_run_cycle`, `card_swim`, `card_lower_lift`, `card_upper_lift`: Readiness status cards
- `card_calendar`: 7-day fatigue visualization
- `layout_calendar_days`: Dynamic day views
- `chart_fatigue`: MPAndroidChart LineChart for fatigue timeline
- `switch_use_health_connect`: Health Connect integration toggle

**Readiness Cards:**
Each card displays:
- Activity emoji and name
- Status text (GREEN/YELLOW/RED)
- Status message
- Optional countdown timer

#### 5. ExercisesActivity (`activity_exercises.xml`)

**Structure:**
- Background flow animation
- Scrollable content
- Header with back button
- Search functionality
- RecyclerView for exercise library
- Fixed "Add Exercise" button (bottom)

**Key Components:**
- `recycler_view_exercises`: Exercise library list
- `button_add_exercise`: Create new exercise

#### 6. ProgressActivity (`activity_progress.xml`)

**Structure:**
- Background flow animation
- Scrollable content
- Header section
- Exercise selector (spinner)
- TabLayout for chart types (Weight, Volume, 1RM, Avg Weight, Avg RPE)
- Chart container (MPAndroidChart)
- Statistics cards grid

**Key Components:**
- Exercise spinner: Filter by exercise
- TabLayout: Switch between chart types
- Chart view: MPAndroidChart visualization
- Stats cards: Max volume, max weight, avg weight, avg RPE, total reps

#### 7. HistoryActivity (`activity_history.xml`)

**Structure:**
- Background flow animation
- Scrollable content
- Header with back button
- RecyclerView for workout history (newest first)

**Key Components:**
- `recycler_view_history`: Workout list

#### 8. TrainingDetailActivity (`activity_training_detail.xml`)

**Structure:**
- Background flow animation
- Scrollable content
- Header with workout date and delete button
- RecyclerView for grouped exercises
- Workout summary section

**Key Components:**
- `recycler_view_exercises`: Grouped exercise display
- Summary stats: Total volume, duration, exercise count

#### 9. SettingsActivity (`activity_settings.xml`)

**Structure:**
- Background flow animation
- Scrollable content
- Header with back button
- Settings sections:
  - Data management (reset, export, import)
  - Progression settings link
  - Readiness settings link

**Key Components:**
- Data management cards
- Navigation to sub-settings

#### 10. SelectExerciseActivity (`activity_select_exercise.xml`)

**Structure:**
- Background flow animation
- Scrollable content
- Header with back button
- Search bar
- Filter chips (region, tier, pattern)
- RecyclerView for exercise selection
- Muscle activation preview

**Key Components:**
- Search functionality
- Filter chips
- Exercise list with badges (suggestions, progression info)
- Muscle map integration

### Dialog Layouts

#### 1. RestTimerDialogActivity (`dialog_rest_timer.xml`)

**Structure:**
- Full-screen dialog
- Exercise name display
- Large timer display (96sp, monospace, accent color)
- Control buttons: -15s, +15s
- Action buttons: Skip, Dismiss

**Key Components:**
- `tvTimerDisplay`: Large countdown display
- `btnRemove15s`, `btnAdd15s`: Time adjustment
- `btnSkipRest`, `btnDismiss`: Actions

#### 2. MuscleMapDialog (`dialog_muscle_map.xml`)

**Structure:**
- Bottom sheet dialog
- SVG muscle map visualization
- Interactive muscle selection
- Primary/secondary target display

**Key Components:**
- SVG WebView for muscle map
- Muscle selection interface

#### 3. Extended Projection Dialog (`dialog_extended_projection.xml`)

**Structure:**
- Dialog showing 1RM projection details
- Projection timeline
- Statistics and warnings

**Key Components:**
- Projection chart
- Qualification flags
- Improvement metrics

### List Item Layouts

#### 1. Grouped Exercise (`item_grouped_exercise.xml`)

**Structure:**
- CardView container
- Exercise name and type
- Action buttons (Edit, Change Type)
- Dynamic sets container

**Key Components:**
- `text_exercise_name`: Exercise title
- `text_exercise_type`: Workout type badge
- `button_edit_activity`: Bulk edit action
- `button_change_type`: Override workout type
- `sets_container`: Dynamic set list

#### 2. Active Exercise (`list_item_active_exercise.xml`)

**Structure:**
- CardView for active workout exercises
- Exercise name
- Set count display
- Quick actions (log set, duplicate, delete)

**Key Components:**
- Exercise info
- Set management buttons

#### 3. History Item (`item_history.xml`)

**Structure:**
- CardView for workout history entry
- Date display
- Workout type badge
- Exercise count
- Volume summary

**Key Components:**
- Date and type display
- Summary statistics

#### 4. Exercise Library Item (`list_item_exercise_library.xml`)

**Structure:**
- CardView for exercise library entry
- Exercise name
- Region and tier badges
- Muscle targets preview

**Key Components:**
- Exercise metadata
- Badge displays

#### 5. Set Detail (`item_set_detail.xml`)

**Structure:**
- Individual set display
- Weight, reps, RPE
- Completion status
- Note preview
- Edit/delete actions

**Key Components:**
- Set data display
- Action buttons

### Resource Files

#### Colors (`values/colors.xml`)

**Primary Palette:**
- `fitness_primary`: #2563EB (Royal Blue)
- `fitness_primary_dark`: #1E40AF
- `fitness_accent`: #F59E0B (Amber)
- `fitness_background`: #F3F4F6 (Cool Gray 100)
- `fitness_card_background`: #FFFFFF
- `fitness_text_primary`: #111827 (Cool Gray 900)
- `fitness_text_secondary`: #6B7280 (Cool Gray 500)

**Semantic Colors:**
- `fitness_info_banner_background`: #E0E7FF
- `fitness_warning_background`: #FEF3C7
- `fitness_highlight_background`: #D1FAE5
- `fitness_error_background`: #FEE2E2
- `fitness_light_blue`: #60A5FA (light workouts)
- `fitness_dark_blue`: #1E40AF (heavy workouts)

#### Strings (`values/strings.xml`)

**Categories:**
- App name and welcome messages
- Navigation labels
- Button labels
- Toast messages
- Validation messages
- Dialog titles and messages
- Notification strings
- Progress chart labels

**Key Strings:**
- `app_name`: "LiftPath"
- `welcome_title`: "Welcome to LiftPath"
- `start_workout`: "Start Workout"
- `view_progress`, `view_history`, `exercises`: Navigation
- Toast messages for user feedback
- Validation messages for input errors

#### Dimensions (`values/dimens.xml`)

**Standard Dimensions:**
- `screen_padding`: 16dp
- `section_spacing`: 24dp
- `component_spacing`: 16dp
- `card_corner_radius`: 12dp
- `card_padding`: 16dp
- `list_item_spacing`: 12dp
- `dialog_corner_radius`: 24dp

**Usage:**
- Consistent spacing throughout app
- Card styling standardization
- Dialog appearance

#### Themes (`values/themes.xml`)

**Base Theme:**
- `Theme.Fitness`: Material Components DayNight theme
- Primary: Royal Blue
- Accent: Amber
- Light status bar
- Custom dialog theme overlay

**Dialog Theme:**
- `ThemeOverlay.Fitness.MaterialAlertDialog`:
  - 24dp corner radius
  - Custom text styles (bold titles)
  - Primary color buttons
  - Transparent window background

**Bottom Sheet Theme:**
- `ThemeOverlay.Fitness.BottomSheetDialog`:
  - Consistent with app theme
  - Card background color

### Drawable Resources

#### Icons (`drawable/`)

**Navigation Icons:**
- `ic_arrow_back_24`, `ic_arrow_forward_24`: Navigation
- `ic_arrow_drop_down_24`: Dropdowns
- `ic_settings`: Settings
- `ic_calendar_today_24`: Date picker

**Action Icons:**
- `ic_play`, `ic_pause`: Timer controls
- `ic_plus`, `ic_minus`: Add/remove
- `ic_check`, `ic_checkmark`: Success
- `ic_delete`: Delete
- `ic_refresh`: Reset

**Feature Icons:**
- `ic_dumbbell`: Exercises
- `ic_graph`: Progress
- `ic_history`: History
- `ic_plans`: Workout plans
- `ic_fitness_center_24`: Readiness/muscles
- `ic_search_24`: Search

**Background Drawables:**
- `avd_background_flow`: Animated vector drawable for background
- `bg_circle_decoration`: Decorative circle
- `bg_dialog_rounded`: Dialog background with rounded corners
- `badge_rounded_background`: Badge styling
- `tile_selected`, `tile_unselected`: Selection states

### ViewBinding Integration

**Pattern:**
All activities use ViewBinding for type-safe view access:

```kotlin
private lateinit var binding: ActivityMainBinding

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    
    // Access views via binding
    binding.cardStartWorkout.setOnClickListener { ... }
    binding.textBenchPress1rm.text = "100"
}
```

**Benefits:**
- Type safety (no `findViewById` casting)
- Null safety (views guaranteed to exist)
- Performance (single inflation pass)
- Code clarity (explicit view references)

### Common UI Patterns

#### 1. Card-Based Design

All major content uses CardView with:
- Consistent corner radius (20dp standard, 24dp hero)
- Elevation hierarchy (2dp → 4dp → 6dp → 8dp)
- Card background color for contrast
- Padding: 16dp standard, 20dp for larger cards

#### 2. Fixed Action Buttons

Bottom action buttons are fixed with:
- ConstraintLayout positioning
- Horizontal weight distribution
- Primary color for primary actions
- Card background for secondary actions
- 56dp height standard
- 16dp margins

#### 3. Input Forms

Material Design text fields with:
- Outlined box style
- Primary color accents
- Consistent spacing (12dp between fields)
- Validation feedback
- Helper text support

#### 4. Status Indicators

Color-coded status displays:
- Green: Ready/positive
- Yellow: Caution/warning
- Red: Blocked/error
- Blue: Information

#### 5. Badge System

Small badges for:
- Progression suggestions
- Workout types (Heavy/Light)
- Exercise tiers
- Time decay warnings
- Failure indicators

### Responsive Design

**Layout Strategies:**
- ConstraintLayout for flexible positioning
- Weight-based distribution for equal-width elements
- NestedScrollView for overflow content
- RecyclerView for dynamic lists
- View visibility toggles for conditional UI

**Screen Adaptation:**
- Consistent padding (24dp standard)
- Scalable text sizes (sp units)
- Flexible card layouts
- Responsive grid systems

### Accessibility

**Features:**
- Content descriptions for icons
- Semantic button labels
- Touch target sizes (minimum 48dp)
- Color contrast compliance
- Text size scalability

---

## Kotlin Activities and XML Interaction

The application follows a **classic Android architecture** with XML layouts and Kotlin activities using **ViewBinding** for type-safe view access. All activities extend `AppCompatActivity` and follow consistent patterns for lifecycle management, data binding, and inter-activity communication.

### Activity Structure Pattern

#### Standard Activity Template

Every activity follows this structure:

```kotlin
class ExampleActivity : AppCompatActivity() {
    
    // 1. ViewBinding instance
    private lateinit var binding: ActivityExampleBinding
    
    // 2. Helper instances
    private lateinit var jsonHelper: JsonHelper
    private lateinit var settingsManager: SomeSettingsManager
    
    // 3. Data state
    private val dataList = mutableListOf<DataModel>()
    
    // 4. Activity result launchers
    private val someActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Handle result
    }
    
    // 5. Lifecycle methods
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inflate binding
        binding = ActivityExampleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize helpers
        jsonHelper = JsonHelper(this)
        
        // Setup UI
        setupBackgroundAnimation()
        setupClickListeners()
        loadData()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh data when returning
        loadData()
    }
    
    // 6. Setup methods
    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }
    
    private fun setupClickListeners() {
        binding.buttonAction.setOnClickListener {
            // Handle click
        }
    }
    
    private fun loadData() {
        // Load and display data
    }
}
```

### ViewBinding Integration

#### Binding Initialization

**Pattern:**
```kotlin
private lateinit var binding: ActivityMainBinding

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
}
```

**Benefits:**
- Type-safe view access (no `findViewById` casting)
- Null safety (views guaranteed to exist after inflation)
- Performance (single inflation pass)
- Code clarity (explicit view references)

#### View Access Pattern

**Direct Access:**
```kotlin
binding.textWelcomeTitle.text = "Welcome"
binding.cardStartWorkout.setOnClickListener { ... }
binding.recyclerViewExercises.adapter = adapter
```

**Conditional Visibility:**
```kotlin
binding.tvSuggestionHint.visibility = View.VISIBLE
binding.textEmptyState.visibility = View.GONE
```

**Text Input:**
```kotlin
val weight = binding.editTextKg.text.toString().toFloatOrNull()
binding.editTextRpe.setText("8.5")
```

### Activity Lifecycle Management

#### Common Lifecycle Hooks

**onCreate:**
- Initialize ViewBinding
- Setup helpers (JsonHelper, SettingsManager)
- Setup UI components
- Load initial data
- Register listeners

**onResume:**
- Refresh data (e.g., after returning from other activities)
- Restart animations
- Update UI state
- Resume background operations

**onPause:**
- Stop timers/updates
- Save draft state
- Pause animations

**onDestroy:**
- Cleanup resources
- Unregister receivers
- Cancel coroutines

#### Background Animation Pattern

**Standard Implementation:**
```kotlin
private fun setupBackgroundAnimation() {
    val drawable = binding.imageBgAnimation.drawable
    if (drawable is Animatable) {
        drawable.start()
    }
}
```

**Usage:**
- Called in `onCreate()` for all major activities
- Uses `avd_background_flow` animated vector drawable
- Provides consistent visual background

### Inter-Activity Communication

#### Activity Result Contracts

**Modern Pattern (ActivityResultContracts):**

```kotlin
// 1. Define launcher
private val logSetLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        val data = result.data
        val loggedSet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data?.getParcelableExtra(LogSetActivity.EXTRA_LOGGED_SET, ExerciseEntry::class.java)
        } else {
            @Suppress("DEPRECATION")
            data?.getParcelableExtra(LogSetActivity.EXTRA_LOGGED_SET)
        }
        
        if (loggedSet != null) {
            updateExercises(loggedSet)
        }
    }
}

// 2. Launch activity
private fun launchLogSetActivity(exerciseId: Int, exerciseName: String) {
    val intent = Intent(this, LogSetActivity::class.java).apply {
        putExtra(LogSetActivity.EXTRA_EXERCISE_ID, exerciseId)
        putExtra(LogSetActivity.EXTRA_EXERCISE_NAME, exerciseName)
        putExtra(LogSetActivity.EXTRA_WORKOUT_TYPE, workoutType)
    }
    logSetLauncher.launch(intent)
}
```

**Benefits:**
- Type-safe result handling
- No deprecated `startActivityForResult()`
- Clear separation of launch and result handling

#### Intent Extras Pattern

**Defining Extras (Companion Object):**

```kotlin
companion object {
    const val EXTRA_EXERCISE_ID = "extra_exercise_id"
    const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
    const val EXTRA_LOGGED_SET = "extra_logged_set"
    const val EXTRA_WORKOUT_TYPE = "extra_workout_type"
}
```

**Sending Data:**
```kotlin
val intent = Intent(this, TargetActivity::class.java).apply {
    putExtra(TargetActivity.EXTRA_EXERCISE_ID, exerciseId)
    putExtra(TargetActivity.EXTRA_EXERCISE_NAME, exerciseName)
    putStringArrayListExtra(TargetActivity.EXTRA_LIST, arrayList)
}
startActivity(intent)
```

**Receiving Data:**
```kotlin
val exerciseId = intent.getIntExtra(EXTRA_EXERCISE_ID, -1)
val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: ""
val loggedSet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    intent.getParcelableExtra(EXTRA_LOGGED_SET, ExerciseEntry::class.java)
} else {
    @Suppress("DEPRECATION")
    intent.getParcelableExtra(EXTRA_LOGGED_SET)
}
```

**Returning Results:**
```kotlin
val resultIntent = Intent().apply {
    putExtra(EXTRA_LOGGED_SET, loggedSet)
}
setResult(Activity.RESULT_OK, resultIntent)
finish()
```

### Data Flow Patterns

#### Loading Data from JSON

**Pattern:**
```kotlin
private fun loadData() {
    val trainingData = jsonHelper.readTrainingData()
    val exercises = trainingData.exerciseLibrary
    adapter.updateExercises(exercises)
}
```

**Common Helpers:**
- `JsonHelper`: Read/write training data
- `ProgressionSettingsManager`: Progression settings
- `ReadinessSettingsManager`: Readiness/fatigue settings
- `ActiveWorkoutDraftManager`: Draft workout persistence

#### Saving Data to JSON

**Pattern:**
```kotlin
private fun saveData() {
    val trainingData = jsonHelper.readTrainingData()
    trainingData.exerciseLibrary.add(newExercise)
    jsonHelper.writeTrainingData(trainingData)
}
```

**Error Handling:**
```kotlin
try {
    jsonHelper.writeTrainingData(trainingData)
    Toast.makeText(this, "Saved successfully", Toast.LENGTH_SHORT).show()
} catch (e: Exception) {
    Log.e(TAG, "Error saving data", e)
    Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
}
```

### RecyclerView Integration

#### Adapter Pattern

**Standard Adapter Structure:**
```kotlin
class ExerciseLibraryAdapter(
    private var exercises: List<ExerciseLibraryItem>,
    private val onEditClicked: (ExerciseLibraryItem) -> Unit
) : RecyclerView.Adapter<ExerciseLibraryAdapter.ExerciseViewHolder>() {

    class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val editButton: CardView = view.findViewById(R.id.button_edit_exercise)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_exercise_library, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exercises[position]
        holder.exerciseName.text = exercise.name
        holder.editButton.setOnClickListener {
            onEditClicked(exercise)
        }
    }

    override fun getItemCount() = exercises.size

    fun updateExercises(newExercises: List<ExerciseLibraryItem>) {
        this.exercises = newExercises
        notifyDataSetChanged()
    }
}
```

**ViewBinding in Adapters:**
```kotlin
class TrainingDetailAdapter(...) : RecyclerView.Adapter<ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGroupedExerciseBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val exercise = groupedExercises[position]
        holder.binding.textExerciseName.text = exercise.exerciseName
        holder.binding.buttonEditActivity.setOnClickListener {
            onEditActivityClicked(exercise)
        }
    }
    
    class ViewHolder(val binding: ItemGroupedExerciseBinding) : 
        RecyclerView.ViewHolder(binding.root)
}
```

**Activity Setup:**
```kotlin
private lateinit var adapter: ActiveExercisesAdapter

override fun onCreate(savedInstanceState: Bundle?) {
    // ...
    adapter = ActiveExercisesAdapter(
        groupedExercises = groupedExercises,
        onLogSetClicked = { exerciseId, exerciseName ->
            launchLogSetActivity(exerciseId, exerciseName)
        },
        onEditActivityClicked = { groupedExercise ->
            launchEditActivityActivity(groupedExercise)
        },
        onDuplicateClicked = { groupedExercise ->
            duplicateExercise(groupedExercise)
        },
        onDeleteClicked = { groupedExercise ->
            deleteExercise(groupedExercise)
        }
    )
    
    binding.recyclerViewActiveWorkout.layoutManager = LinearLayoutManager(this)
    binding.recyclerViewActiveWorkout.adapter = adapter
}
```

### Activity-Specific Patterns

#### MainActivity

**Key Features:**
- Dashboard with statistics
- Customizable 1RM cards (stored in SharedPreferences)
- Volume chart (MPAndroidChart)
- Days since last workout tracking
- Entrance animations

**Data Flow:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    // ...
    updateStats()  // Load and display stats
}

override fun onResume() {
    super.onResume()
    updateStats()  // Refresh when returning
}

private fun updateStats() {
    val trainingData = jsonHelper.readTrainingData()
    // Calculate and display stats
    update1RMCards(trainingData)
    updateVolumeChart(trainingData)
    updateDaysSince(trainingData)
}
```

#### ActiveTrainingActivity

**Key Features:**
- Live workout session management
- Draft persistence (ActiveWorkoutDraftManager)
- Rest timer integration (RestTimerService)
- Workout timer (duration tracking)
- Exercise grouping and management

**Draft Management:**
```kotlin
override fun onPause() {
    super.onPause()
    saveDraft()  // Save draft when leaving
}

private fun saveDraft() {
    val draft = ActiveWorkoutDraft(
        workoutType = workoutType,
        exercises = currentExerciseEntries,
        selectedDate = selectedDate.timeInMillis
    )
    draftManager.saveDraft(draft)
}

override fun onCreate(savedInstanceState: Bundle?) {
    // ...
    restoreDraft()  // Restore on create
}

private fun restoreDraft() {
    val draft = draftManager.loadDraft()
    if (draft != null) {
        // Restore workout state
        workoutType = draft.workoutType
        currentExerciseEntries.addAll(draft.exercises)
        // ...
    }
}
```

**Timer Integration:**
```kotlin
private fun startRestTimer(rpe: Float?, workoutType: String, exerciseName: String) {
    val intent = Intent(this, RestTimerService::class.java).apply {
        putExtra(RestTimerService.EXTRA_RPE, rpe)
        putExtra(RestTimerService.EXTRA_WORKOUT_TYPE, workoutType)
        putExtra(RestTimerService.EXTRA_EXERCISE_NAME, exerciseName)
    }
    ContextCompat.startForegroundService(this, intent)
}

private val timerReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val timeRemaining = intent?.getLongExtra(RestTimerService.EXTRA_TIME_REMAINING, 0) ?: 0
        updateTimerDisplay(timeRemaining)
    }
}
```

#### LogSetActivity

**Key Features:**
- Set logging with pre-filled suggestions
- Progression suggestions (ProgressionHelper)
- RPE input with validation
- Automatic rest timer start
- Completion status tracking

**Progression Suggestion:**
```kotlin
private fun showWeightSuggestion() {
    val trainingData = jsonHelper.readTrainingData()
    val settingsManager = ProgressionSettingsManager(this)
    val userSettings = settingsManager.getSettings()

    val suggestion = ProgressionHelper.getSuggestion(
        exerciseId = exerciseId,
        requestedType = workoutType,
        trainingData = trainingData,
        settings = userSettings
    )

    if (!suggestion.isFirstTime && suggestion.proposedWeight != null) {
        binding.editTextKg.setText(suggestion.proposedWeight.toString())
        binding.editTextReps.setText(suggestion.proposedReps.toString())
        binding.editTextRpe.setText(suggestion.suggestedRpe.toString())
        binding.tvSuggestionHint.visibility = View.VISIBLE
        binding.textSuggestionContent.text = "Suggested: ${suggestion.proposedWeight}kg"
    }
}
```

**Data Validation:**
```kotlin
private fun saveSet() {
    val weight = binding.editTextKg.text.toString().toFloatOrNull()
    val reps = binding.editTextReps.text.toString().toIntOrNull()
    val rpe = binding.editTextRpe.text.toString().toFloatOrNull()
    val completed = binding.cbCompleted.isChecked

    if (weight == null || weight <= 0) {
        Toast.makeText(this, "Please enter valid weight", Toast.LENGTH_SHORT).show()
        return
    }

    if (reps == null || reps <= 0) {
        Toast.makeText(this, "Please enter valid reps", Toast.LENGTH_SHORT).show()
        return
    }

    if (rpe != null && (rpe < 6.0f || rpe > 10.0f)) {
        Toast.makeText(this, "RPE must be between 6.0 and 10.0", Toast.LENGTH_SHORT).show()
        return
    }

    val exerciseEntry = ExerciseEntry(
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        setNumber = setNumber,
        kg = weight,
        reps = reps,
        rpe = rpe,
        completed = completed,
        note = binding.editTextNote.text.toString().takeIf { it.isNotBlank() }
    )

    val resultIntent = Intent().apply {
        putExtra(EXTRA_LOGGED_SET, exerciseEntry)
    }
    setResult(Activity.RESULT_OK, resultIntent)
    finish()
}
```

#### ReadinessDashboardActivity

**Key Features:**
- Fatigue calculation and display
- Activity readiness assessment
- 7-day fatigue calendar
- Health Connect integration
- Continuous fatigue timeline

**Fatigue Calculation:**
```kotlin
private fun loadReadinessData() {
    lifecycleScope.launch(Dispatchers.IO) {
        val trainingData = jsonHelper.readTrainingData()
        val settings = settingsManager.getSettings()
        val config = ReadinessConfig.fromSettings(settings)
        
        val useHealthConnect = healthConnectPrefs.getBoolean(HEALTH_CONNECT_ENABLED_KEY, false)
        val externalActivities = if (useHealthConnect) {
            HealthConnectHelper.getStoredActivities(applicationContext)
        } else {
            emptyList()
        }
        
        val timeline = ReadinessHelper.calculateContinuousFatigueTimeline(
            trainingData,
            config,
            externalActivities
        )
        
        withContext(Dispatchers.Main) {
            fatigueTimeline = timeline
            val currentFatigue = ReadinessHelper.getCurrentFatigueFromTimeline(timeline, config)
            updateActivityReadiness(currentFatigue, config)
            updateCalendarWithTimeline(timeline)
        }
    }
}
```

### Common Helper Patterns

#### DialogHelper

**Usage:**
```kotlin
DialogHelper.createBuilder(this)
    .setTitle("Title")
    .setMessage("Message")
    .setPositiveButton("OK") { _, _ ->
        // Handle positive action
    }
    .setNegativeButton("Cancel", null)
    .showWithTransparentWindow()
```

#### ProgressionHelper

**Weight Suggestions:**
```kotlin
val suggestion = ProgressionHelper.getSuggestion(
    exerciseId = exerciseId,
    requestedType = workoutType,
    trainingData = trainingData,
    settings = userSettings
)

// suggestion.proposedWeight
// suggestion.proposedReps
// suggestion.suggestedRpe
// suggestion.badge (e.g., "🕐 TIME DECAY", "⚠️ FAILED REPS")
```

#### WorkoutGenerator

**Smart Workout Creation:**
```kotlin
val blueprint = WorkoutGenerator.getBlueprint(focus, intensity)
val recommendedExercises = WorkoutGenerator.generateWorkout(
    blueprint = blueprint,
    exerciseLibrary = trainingData.exerciseLibrary,
    excludeIds = alreadySelectedIds
)
```

### Error Handling Patterns

#### Try-Catch Blocks

**Data Operations:**
```kotlin
try {
    val trainingData = jsonHelper.readTrainingData()
    // Process data
} catch (e: Exception) {
    Log.e(TAG, "Error reading training data", e)
    Toast.makeText(this, "Error loading data", Toast.LENGTH_LONG).show()
}
```

#### Validation

**Input Validation:**
```kotlin
private fun validateInput(): Boolean {
    val weight = binding.editTextKg.text.toString().toFloatOrNull()
    if (weight == null || weight <= 0) {
        binding.editTextKg.error = "Invalid weight"
        return false
    }
    return true
}
```

### Coroutines Integration

#### Background Operations

**Pattern:**
```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    // Heavy operation
    val data = loadDataFromDatabase()
    
    withContext(Dispatchers.Main) {
        // Update UI
        binding.recyclerView.adapter = Adapter(data)
    }
}
```

**Usage in Activities:**
- ReadinessDashboardActivity: Fatigue timeline calculation
- MainActivity: Health Connect auto-sync
- ActiveTrainingActivity: Draft loading/saving

### Activity Navigation Flow

#### Main Navigation Paths

**1. Start Workout Flow:**
```
MainActivity
  → (Select Workout Type Dialog)
  → ActiveTrainingActivity
    → SelectExerciseActivity
      → LogSetActivity
        → (Returns ExerciseEntry)
    → (Finish Workout)
  → (Returns to MainActivity, updates stats)
```

**2. View History Flow:**
```
MainActivity
  → HistoryActivity
    → TrainingDetailActivity
      → EditSetActivity / EditActivityActivity
        → (Returns updated data)
```

**3. Exercise Management Flow:**
```
MainActivity
  → ExercisesActivity
    → EditExerciseActivity
      → (Muscle Map Dialog)
```

**4. Progress Tracking Flow:**
```
MainActivity
  → ProgressActivity
    → (Exercise selector)
    → (Chart tabs: Weight, Volume, 1RM, Avg Weight, Avg RPE)
```

**5. Readiness Flow:**
```
MainActivity
  → ReadinessDashboardActivity
    → ReadinessCalibrationActivity
    → HealthConnectActivity
```

### State Management

#### Activity State

**In-Memory State:**
- Current exercise entries
- Grouped exercises
- Workout type
- Selected date
- Timer state

**Persisted State:**
- Draft workouts (ActiveWorkoutDraftManager)
- Settings (SharedPreferences)
- Training data (JSON file)

#### State Restoration

**Draft Restoration:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    // ...
    restoreDraft()
}

private fun restoreDraft() {
    val draft = draftManager.loadDraft()
    if (draft != null) {
        // Show dialog to resume
        showResumeDialog(draft)
    }
}
```

**Settings Restoration:**
```kotlin
private fun loadSettings() {
    val settings = settingsManager.getSettings()
    binding.switchOption.isChecked = settings.optionEnabled
}
```

### Testing Considerations

#### Activity Testing Patterns

**ViewBinding Access:**
- All views accessible via binding
- No findViewById calls needed
- Type-safe view references

**Mock Data:**
- JsonHelper can be mocked
- SettingsManager can be mocked
- Test data can be injected

**Activity Result Testing:**
- ActivityResultContracts can be tested
- Intent extras can be verified
- Result handling can be tested

---

## Summary

Lift Path implements a comprehensive, scientifically-backed fitness tracking system with:

1. **Intelligent Progression**: Tier-based algorithms (Linear RPE for main lifts, Double Progression for accessories) with time decay, failure detection, and RPE-based adjustments

2. **Scientific 1RM Estimation**: Hybrid formulas (Epley for ≤8 reps, Brzycki for 9-15 reps) with RPE normalization, weighted regression, and realistic projections accounting for diminishing returns

3. **Smart Workout Generation**: Balanced blueprints covering all movement patterns and muscle groups based on focus and intensity

4. **Fatigue Tracking**: Three-score system (lower, upper, systemic) with exponential decay modeling, recovery time calculations, and activity readiness assessment

5. **Complete Data Control**: Offline-first architecture with JSON persistence, export/import capabilities, and comprehensive data safety measures

6. **Modern Android Architecture**: XML-based layouts with ViewBinding for type-safe view access, ActivityResultContracts for inter-activity communication, and consistent lifecycle management patterns

7. **Comprehensive UI System**: Material Design 3 components with card-based layouts, consistent theming, responsive design, and accessibility features

All calculations are transparent, mathematically sound, and based on exercise science principles. The system provides intelligent recommendations while maintaining complete user control and data privacy. The codebase follows Android best practices with clear separation of concerns, type-safe view binding, and robust error handling.

---

*Document Version: 1.0*  
*Last Updated: 2025*  
*Application: Lift Path - Personal Training Tracker*

