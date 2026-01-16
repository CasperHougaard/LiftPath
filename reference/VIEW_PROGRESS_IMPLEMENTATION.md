# View Progress - Current Implementation

## Overview

The View Progress feature (`ProgressActivity`) provides users with comprehensive visualization and analysis of their training progress for individual exercises. It displays historical data through interactive charts, calculates key statistics, and provides 1RM (One Rep Max) progression estimates with projections.

## Architecture

### Main Components

1. **ProgressActivity** (`app/src/main/java/com/liftpath/activities/ProgressActivity.kt`)
   - Main activity handling UI and user interactions
   - Manages chart display, statistics calculation, and estimation display

2. **OneRMEstimationHelper** (`app/src/main/java/com/liftpath/helpers/OneRMEstimationHelper.kt`)
   - Handles 1RM calculation using hybrid formulas
   - Performs weighted linear regression for progression estimation
   - Implements RPE normalization and data quality checks

3. **ProgressSettingsManager** (`app/src/main/java/com/liftpath/helpers/ProgressSettingsManager.kt`)
   - Manages user preferences for progress tracking
   - Stores settings like estimation period, data quality thresholds, etc.

4. **Layout** (`app/src/main/res/layout/activity_progress.xml`)
   - Defines the UI structure with cards, charts, and controls

## Features

### 1. Exercise Selection

- **Spinner-based selection**: Users select an exercise from a dropdown populated with all exercises that have training history
- **Auto-population**: Exercise list is dynamically generated from `TrainingData.trainings`
- **Immediate update**: Selecting an exercise triggers recalculation of all stats and charts

### 2. Chart Visualization

The activity supports **5 different chart types** via tab navigation:

#### Chart Types

1. **Weight** (Blue)
   - Displays maximum weight per session
   - Shows progression of strength over time

2. **Volume** (Green)
   - Shows total volume (weight × reps) per session
   - Useful for tracking training load

3. **1RM** (Orange)
   - Estimated 1RM per session using hybrid formula
   - Includes projection line when estimation is available
   - Uses RPE normalization when available

4. **Avg Weight** (Purple)
   - Average weight per session (volume / total reps)
   - Shows intensity trends

5. **Avg RPE** (Amber)
   - Average RPE per session
   - Only displays sessions with RPE data
   - Shows effort/intensity trends

#### Chart Features

- **Interactive**: Pinch-to-zoom, drag, double-tap zoom
- **Smooth curves**: Cubic bezier interpolation for historical data
- **Projection line**: Dashed gray line for 1RM projections
- **Smart Y-axis**: Automatically calculates "nice" maximum values with 15% padding
- **Date formatting**: X-axis shows dates in MM/dd format, rotated -45°
- **Empty states**: Shows appropriate messages when no data is available

### 3. Statistics Summary

Displays key metrics in card layout:

- **Max Weight**: Highest weight lifted for the exercise
- **Max Volume**: Highest session volume (kg)
- **Avg Weight**: Average weight across all sets (volume-weighted)
- **Avg RPE**: Average RPE (only if RPE data exists)
- **Total Reps**: Sum of all repetitions performed

### 4. 1RM Progression Estimation

#### Estimation Card Features

- **Current 1RM**: Most recent calculated 1RM
- **Expected 1RM**: Projected 1RM at target date
- **Projected Improvement**: Shows both absolute (kg) and percentage improvement
- **Projection Date**: Target date for the projection
- **Projection Period Selector**: Spinner to choose 1, 2, 3, or 6 months
- **Warning System**: Dismissible warnings for data quality issues
- **Info Button**: Explains estimation logic
- **Extended Projection Button**: Opens dialog with 6-month detailed projection

#### Estimation Logic - Detailed Implementation

The 1RM progression estimation is a sophisticated multi-step process that combines RPE normalization, hybrid 1RM formulas, weighted linear regression, and damping factors to provide accurate and realistic strength projections.

##### Step 1: Data Collection and Preprocessing

**Input Data:**
- `sets: List<ExerciseSet>` - All sets for the selected exercise
- `sessionWorkoutTypes: Map<String, String>` - Workout type per session date
- `projectionMonths: Int` - Target projection period (1, 2, 3, or 6 months)
- `minDataPoints: Int` - Minimum sessions required (default: 4)
- `recentDataWindowDays: Int` - Maximum days since last session (default: 30)

**Processing:**
1. Group sets by session date (`yyyy/MM/dd` format)
2. For each session:
   - Parse date string to `Date` object
   - Lookup workout type from `sessionWorkoutTypes` map
   - Normalize workout type using `WorkoutTypeFormatter.normalize()`
   - Calculate 1RM for each set in the session (see Step 2)
   - Take the maximum 1RM value from all sets in that session
3. Filter out sessions with no valid 1RM calculations
4. Sort sessions chronologically by date

**Output:** `List<Pair<Date, Float>>` - (date, max1RM) pairs for each session

##### Step 2: Individual Set 1RM Calculation (`calculateOneRM()`)

This is the core function that converts a single set (weight, reps, RPE) into an estimated 1RM.

**Input Parameters:**
- `weight: Float` - Weight lifted in kg
- `actualReps: Int` - Number of repetitions actually performed
- `rpe: Float?` - Optional RPE value (1-10 scale, where 10 = absolute failure)

**Step 2.1: RPE Intensity Filter (Rule A)**
```
IF rpe != null AND rpe < 6.5:
    RETURN null  // Set too light to be predictive
```
**Rationale:** Sets with RPE < 6.5 are considered too light to accurately predict maximum strength. This threshold ensures only meaningful training data is used.

**Step 2.2: RPE Normalization - Effective Reps Calculation**

If RPE is provided, the system calculates "effective reps" to account for submaximal effort:

```
RIR (Reps In Reserve) = 10.0 - RPE
effectiveReps = actualReps + RIR
```

**Example:**
- Actual: 100kg × 5 reps @ RPE 8.0
- RIR = 10 - 8 = 2 reps
- Effective reps = 5 + 2 = 7 reps
- System treats this as if the lifter could have done 7 reps at failure

**Rationale:** RPE normalization allows the system to accurately estimate 1RM from submaximal sets, which is common in periodized training programs. Without this, sets with RPE 8-9 would underestimate true strength.

**If RPE is NOT provided:**
- `effectiveReps = actualReps`
- Assumes the set was performed near failure (standard 1RM formula behavior)

**Step 2.3: Rep Range Filter (Rule C)**
```
IF effectiveReps > 15:
    RETURN null  // Statistically unreliable for 1RM estimation
```
**Rationale:** High rep sets (>15 reps) are primarily endurance-based and don't accurately reflect maximum strength. The 1RM formulas become increasingly unreliable beyond this range.

**Step 2.4: Edge Cases**
```
IF effectiveReps <= 0:
    RETURN weight  // Single rep or invalid, assume it's the 1RM

IF effectiveReps == 1:
    RETURN weight  // Already at 1RM
```

**Step 2.5: Hybrid 1RM Formula Selection**

The system uses different formulas based on rep range for optimal accuracy:

**For effectiveReps ≤ 8: Epley's Formula**
```
1RM = weight × (1 + effectiveReps / 30)
```

**Mathematical Properties:**
- Linear relationship between reps and 1RM
- More aggressive (higher estimates) for lower rep ranges
- Well-validated for strength-focused training (1-8 reps)
- Formula derivation: Based on empirical data showing ~3.33% strength loss per rep

**Example:**
- 100kg × 5 reps (effective) → 1RM = 100 × (1 + 5/30) = 100 × 1.167 = 116.7kg
- 100kg × 8 reps (effective) → 1RM = 100 × (1 + 8/30) = 100 × 1.267 = 126.7kg

**For effectiveReps 9-15: Brzycki's Formula**
```
1RM = weight × (36 / (37 - effectiveReps))
```

**Mathematical Properties:**
- Non-linear (hyperbolic) relationship
- More conservative (lower estimates) for higher rep ranges
- Prevents overestimation from burnout/endurance sets
- Formula derivation: Based on percentage of 1RM tables

**Example:**
- 100kg × 10 reps (effective) → 1RM = 100 × (36 / (37 - 10)) = 100 × 1.333 = 133.3kg
- 100kg × 15 reps (effective) → 1RM = 100 × (36 / (37 - 15)) = 100 × 1.636 = 163.6kg

**Safety Check:**
```
IF effectiveReps >= 37:
    RETURN null  // Invalid for Brzycki formula (division by zero risk)
```

**Why Hybrid Approach?**
- Epley's is more accurate for strength ranges (1-8 reps) where most 1RM testing occurs
- Brzycki's is more conservative for higher reps, preventing unrealistic projections
- The crossover at 8 reps provides smooth transition between formulas

**Return Value:** `Float?` - Estimated 1RM in kg, or `null` if invalid/unreliable

##### Step 3: Session-Level 1RM Aggregation

For each training session:
1. Calculate 1RM for every set in the session using `calculateOneRM()`
2. Filter out `null` results (invalid sets)
3. Take the **maximum** 1RM value as the session's representative 1RM

**Rationale:** The maximum 1RM from a session best represents the lifter's true strength capacity on that day, accounting for warm-up sets and fatigue.

**Output:** One 1RM value per session date

##### Step 4: Data Quality Assessment

Before performing regression, the system evaluates data quality:

**4.1: Minimum Data Points Check**
```
IF uniqueSessions < minDataPoints:
    WARNING: "Limited data: Estimation based on only X session(s)"
```
**Default:** 4 sessions minimum
**Rationale:** Regression requires sufficient data points for statistical validity. Fewer than 4 sessions may produce unreliable trends.

**4.2: Recent Data Check**
```
daysSinceLastSession = (today - lastSessionDate) / (1000 × 60 × 60 × 24)
IF daysSinceLastSession > recentDataWindowDays:
    WARNING: "No recent data: Last session was X days ago"
```
**Default:** 30 days maximum
**Rationale:** Strength can decay with inactivity. Very old data may not reflect current fitness level.

**4.3: Data Consistency Check (Coefficient of Variation)**
```
mean = average(all 1RM values)
variance = average((1RM_i - mean)²)
stdDev = √variance
coefficientOfVariation = stdDev / mean

IF coefficientOfVariation > 0.15:
    WARNING: "Inconsistent progression: Results may vary"
```
**Rationale:** High variation (>15%) indicates inconsistent training, potential measurement errors, or program changes. The estimation may be less reliable.

**4.4: Insufficient Data Fallback**
```
IF oneRMPerSession.size < 2:
    RETURN OneRMEstimationResult(
        current1RM = current1RM,
        expected1RM = current1RM,  // No projection possible
        improvementKg = 0,
        improvementPercent = 0,
        isQualified = false,
        warnings = warnings + "Insufficient data for estimation"
    )
```
**Rationale:** Linear regression requires at least 2 data points. With only 1 session, no trend can be calculated.

##### Step 5: Weighted Linear Regression with Exponential Decay

The system performs **weighted linear regression** to find the best-fit line through the 1RM progression data, with recent data weighted more heavily.

**5.1: Data Preparation**

Convert dates to numeric X-axis values:
```
firstDate = earliest session date
xValues[i] = (sessionDate[i] - firstDate) / (1000 × 60 × 60 × 24)  // Days since first session
yValues[i] = 1RM[i]  // 1RM values
```

**5.2: Exponential Decay Weight Calculation**

Each data point is assigned a weight based on how recent it is:

```
lambda = 0.02  // Decay constant
daysAgo[i] = (today - sessionDate[i]) / (1000 × 60 × 60 × 24)
weight[i] = e^(-lambda × daysAgo[i])
```

**Mathematical Properties:**
- Recent data (daysAgo ≈ 0): weight ≈ e^0 = 1.0 (100% influence)
- 35 days ago: weight = e^(-0.02 × 35) ≈ 0.497 (50% influence) - **half-life**
- 70 days ago: weight = e^(-0.02 × 70) ≈ 0.247 (25% influence)
- 105 days ago: weight = e^(-0.02 × 105) ≈ 0.123 (12% influence)

**Rationale:** 
- Recent training reflects current fitness better than old data
- Exponential decay provides smooth, mathematically elegant weighting
- ~35 day half-life means data from a month ago has 50% influence
- Prevents old data from skewing projections while still using historical context

**5.3: Weighted Mean Calculation**

Calculate weighted means for X and Y:
```
sumWeights = Σ(weight[i])
meanX_weighted = Σ(weight[i] × xValues[i]) / sumWeights
meanY_weighted = Σ(weight[i] × yValues[i]) / sumWeights
```

**5.4: Weighted Slope Calculation**

The slope represents the rate of strength gain per day:

```
numerator = Σ(weight[i] × (xValues[i] - meanX_weighted) × (yValues[i] - meanY_weighted))
denominator = Σ(weight[i] × (xValues[i] - meanX_weighted)²)

slope = numerator / denominator  (if denominator ≠ 0, else 0)
```

**Interpretation:**
- Positive slope: Strength increasing over time
- Negative slope: Strength decreasing (deload, injury, etc.)
- Larger slope: Faster progression rate
- Units: kg per day

**5.5: Weighted Intercept Calculation**

The intercept represents the estimated 1RM at the first session (X=0):

```
intercept = meanY_weighted - slope × meanX_weighted
```

**5.6: Standard Error Calculation (for confidence intervals)**

Calculate weighted standard error to assess prediction reliability:

```
// Calculate residuals (actual - predicted)
FOR each data point i:
    predicted[i] = slope × xValues[i] + intercept
    residual[i] = yValues[i] - predicted[i]

// Calculate effective sample size
sumWeightSquared = Σ(weight[i]²)
effectiveN = (sumWeights²) / sumWeightSquared

// Calculate standard error
sumWeightedSquaredResiduals = Σ(weight[i] × residual[i]²)
standardError = √(sumWeightedSquaredResiduals / (effectiveN - 2) / sumWeights)
```

**Output:** `RegressionResult(slope, intercept, standardError, meanX, sumSquaredDeviations)`

##### Step 6: Projection Calculation with Damping

**6.1: Projection Date Calculation**

```
calendar.time = currentDate  // Last session date
calendar.add(Calendar.MONTH, projectionMonths)
projectionDate = calendar.time
```

**6.2: Days to Project**

```
daysToProject = (projectionDate - currentDate) / (1000 × 60 × 60 × 24)
```

**6.3: Undamped Projection**

Calculate what the regression line predicts at the projection date:

```
undampedProjection = slope × daysToProject + intercept
```

**Note:** This is a linear extrapolation that assumes current rate of improvement continues indefinitely.

**6.4: Damping Factor Application (Law of Diminishing Returns)**

The system applies a damping factor to account for the reality that strength gains slow down over time:

```
dampingFactor = getDampingFactor(projectionMonths)

WHERE:
  projectionMonths ≤ 1  → dampingFactor = 1.0   (100% - no damping)
  projectionMonths ≤ 2  → dampingFactor = 0.9   (90% - 10% reduction)
  projectionMonths ≤ 3  → dampingFactor = 0.8   (80% - 20% reduction)
  projectionMonths ≤ 6  → dampingFactor = 0.5   (50% - 50% reduction)
  projectionMonths > 6  → dampingFactor = 0.3   (30% - 70% reduction)
```

**Rationale:**
- **Short-term (1 month):** No damping - linear progression is realistic
- **Medium-term (2-3 months):** Moderate damping - some slowdown expected
- **Long-term (6+ months):** Heavy damping - accounts for:
  - Diminishing returns (harder to gain strength as you get stronger)
  - Potential plateaus
  - Life factors (injuries, deloads, program changes)
  - Non-linear nature of strength progression

**6.5: Final Projection Calculation**

```
projectedGain = (undampedProjection - current1RM) × dampingFactor
expected1RM = current1RM + projectedGain
```

**Example:**
- Current 1RM: 100kg
- Undamped projection (3 months): 115kg
- Damping factor (3 months): 0.8
- Projected gain: (115 - 100) × 0.8 = 12kg
- Expected 1RM: 100 + 12 = 112kg

**6.6: Improvement Metrics**

```
improvementKg = expected1RM - current1RM
improvementPercent = (improvementKg / current1RM) × 100
```

##### Step 7: Qualification Status

The estimation is marked as "qualified" if it meets all quality thresholds:

```
isQualified = (uniqueSessions >= minDataPoints) AND
              (daysSinceLastSession <= recentDataWindowDays) AND
              (coefficientOfVariation <= 0.15)
```

**Qualified estimations:**
- Meet minimum data requirements
- Use recent, consistent data
- More reliable for decision-making

**Unqualified estimations:**
- Still displayed but with warnings
- May be less accurate
- User should interpret with caution

##### Step 8: Result Assembly

```
OneRMEstimationResult(
    current1RM = oneRMPerSession.last().second,  // Most recent 1RM
    expected1RM = expected1RM,                   // Projected 1RM
    projectionDate = projectionDate,              // Target date
    improvementKg = improvementKg,               // Absolute improvement
    improvementPercent = improvementPercent,      // Percentage improvement
    isQualified = isQualified,                    // Quality flag
    warnings = warnings                            // List of data quality warnings
)
```

##### Complete Algorithm Flow Summary

```
1. Group sets by session date
2. FOR each session:
   a. FOR each set in session:
      - Apply RPE intensity filter (Rule A)
      - Calculate effective reps (RPE normalization)
      - Apply rep range filter (Rule C)
      - Calculate 1RM using hybrid formula
   b. Take maximum 1RM from session
3. Assess data quality (points, recency, consistency)
4. IF insufficient data (< 2 points):
   RETURN fallback result
5. Perform weighted linear regression:
   a. Calculate exponential decay weights
   b. Calculate weighted means
   c. Calculate weighted slope and intercept
6. Calculate projection:
   a. Determine projection date
   b. Calculate undamped projection
   c. Apply damping factor
   d. Calculate final expected 1RM
7. Calculate improvement metrics
8. Determine qualification status
9. RETURN OneRMEstimationResult
```

##### Mathematical Formulas Reference

**RPE Normalization:**
- RIR = 10 - RPE
- Effective Reps = Actual Reps + RIR

**1RM Formulas:**
- Epley (≤8 reps): `1RM = w × (1 + r/30)`
- Brzycki (9-15 reps): `1RM = w × (36 / (37 - r))`

**Weighted Regression:**
- Weight: `w_i = e^(-0.02 × days_ago_i)`
- Weighted Slope: `m = Σw_i(x_i - x̄_w)(y_i - ȳ_w) / Σw_i(x_i - x̄_w)²`
- Weighted Intercept: `b = ȳ_w - m × x̄_w`

**Projection:**
- Undamped: `y = m × days + b`
- Damped: `expected1RM = current1RM + (undamped - current1RM) × dampingFactor`

##### Edge Cases and Error Handling

1. **Empty sets list:** Returns `null` immediately
2. **Invalid dates:** Sessions with unparseable dates are skipped
3. **All sets filtered out:** Session returns `null`, excluded from regression
4. **Single session:** Returns result with `expected1RM = current1RM`, `isQualified = false`
5. **Zero slope:** Handled gracefully, projection equals current 1RM
6. **Negative slope:** Allowed (represents strength loss), damping still applied
7. **Very old data:** Weighted regression naturally reduces influence via exponential decay
8. **Division by zero:** Protected in Brzycki formula (reps ≥ 37 check) and regression denominator

### 5. Data Filtering Rules

The implementation applies sophisticated filtering to ensure accurate progress tracking:

#### Rule A: RPE Intensity Filter
- Sets with RPE < 6.5 are excluded from 1RM calculations
- Too light to be predictive of maximum strength

#### Rule B: Workout Type Filter
- **Heavy workouts**: Always included (can infer from weight/reps if no RPE)
- **Light/Deload/Warmup workouts**: Only included if RPE data is available
- This prevents light training sessions from skewing progress metrics

#### Rule C: Rep Range Filter
- Sets with effective reps > 15 are excluded
- High rep sets are unreliable for 1RM estimation

### 6. Settings Integration

Users can access progress settings via the settings button:
- Opens `ProgressSettingsActivity`
- Allows configuration of:
  - Default estimation period
  - Minimum data points threshold
  - Recent data window
  - Warning display preferences

### 7. Extended Projection Dialog

- Shows 6-month detailed projection
- Displays monthly projection points
- Separate chart with historical vs projected data
- Useful for long-term planning

## Data Flow

```
User selects exercise
    ↓
updateStatsForExercise()
    ↓
Read TrainingData from JsonHelper
    ↓
Filter sets by exercise name
    ↓
Apply workout type filtering (Rule B)
    ↓
Sort sets by date
    ↓
calculateAndDisplayStats() → Update stat cards
    ↓
setupChart() → Update chart visualization
    ↓
calculateAndDisplayEstimation() → Update estimation card
    ↓
OneRMEstimationHelper.estimate1RMProgression()
    ↓
Calculate 1RM per session (with RPE normalization)
    ↓
Perform weighted linear regression
    ↓
Apply damping factor for projection
    ↓
Return OneRMEstimationResult
    ↓
Display in UI
```

## UI Components

### Layout Structure

1. **Header Container**
   - Back button
   - Settings button
   - Title and subtitle

2. **Exercise Selector Card**
   - Exercise spinner
   - Helper text

3. **Chart Card**
   - Chart type tabs (5 tabs)
   - LineChart component
   - Empty state message

4. **Estimation Card**
   - Title with info button
   - Projection period spinner
   - Warning card (dismissible)
   - Current/Expected 1RM display
   - Improvement metrics
   - Projection date
   - Extended projection button

5. **Statistics Summary**
   - Grid of stat cards (2x2 + 1 full-width)
   - Icons and labels for each metric

### Visual Design

- **Background**: Animated background drawable (`avd_background_flow`)
- **Cards**: Elevated card views with rounded corners
- **Colors**: Material Design color scheme
- **Typography**: Clear hierarchy with bold titles and secondary text
- **Spacing**: Consistent padding and margins (20-24dp)

## Key Classes and Methods

### ProgressActivity

- `onCreate()`: Initializes UI and sets up listeners
- `setupTabs()`: Configures chart type tabs
- `setupSpinner()`: Populates exercise dropdown
- `updateStatsForExercise()`: Main data processing method
- `calculateAndDisplayStats()`: Computes and displays statistics
- `setupChart()`: Configures and displays chart
- `calculateAndDisplayEstimation()`: Handles 1RM estimation display
- `calculateNiceMaximum()`: Smart Y-axis scaling
- `showExtendedProjectionDialog()`: Opens 6-month projection dialog

### OneRMEstimationHelper

- `calculateOneRM()`: Hybrid 1RM calculation with RPE normalization
- `estimate1RMProgression()`: Main estimation method with regression
- `performWeightedLinearRegression()`: Weighted regression implementation
- `getDampingFactor()`: Returns damping factor based on projection period

### ProgressSettingsManager

- `getSettings()`: Retrieves user preferences
- `saveSettings()`: Persists settings
- `resetToDefaults()`: Restores default values

## Data Models

### ExerciseSet
```kotlin
data class ExerciseSet(
    val date: String,      // Format: "yyyy/MM/dd"
    val setNumber: Int,
    val kg: Float,
    val reps: Int,
    val rpe: Float?        // Optional RPE (1-10 scale)
)
```

### OneRMEstimationResult
```kotlin
data class OneRMEstimationResult(
    val current1RM: Float,
    val expected1RM: Float,
    val projectionDate: Date,
    val improvementKg: Float,
    val improvementPercent: Float,
    val isQualified: Boolean,  // Whether estimation meets quality thresholds
    val warnings: List<String>
)
```

### ProgressSettings
```kotlin
data class ProgressSettings(
    val defaultEstimationPeriodMonths: Int = 3,
    val minimumDataPoints: Int = 4,
    val recentDataWindowDays: Int = 30,
    val defaultChartType: String = "weight",
    val estimationMethod: String = "linear_regression",
    val showWarnings: Boolean = true
)
```

## Chart Library

Uses **MPAndroidChart** library:
- `LineChart` for visualization
- `LineDataSet` for data series
- `LineData` for chart data
- Custom `ValueFormatter` for date/axis formatting

## Navigation

- **Entry Point**: MainActivity → "View Progress" card
- **Settings**: ProgressActivity → Settings button → ProgressSettingsActivity
- **Back Navigation**: Standard Android back button behavior

## Error Handling

- **Empty data**: Shows appropriate empty state messages
- **Invalid dates**: Gracefully handles date parsing errors
- **Missing RPE**: Falls back to standard 1RM calculation
- **Insufficient data**: Shows warnings and disables estimation if needed
- **Chart errors**: Clears chart and shows empty state

## Performance Considerations

- **Lazy loading**: Chart only updates when exercise changes
- **Efficient filtering**: Single pass through training data
- **Caching**: Current exercise sets cached in `currentExerciseSets`
- **Chart animation**: 800ms animation for smooth transitions
- **Background processing**: Data processing happens on main thread (acceptable for current data volumes)

## Future Enhancement Opportunities

1. **Export functionality**: Save charts as images or export data to CSV
2. **Comparison mode**: Compare multiple exercises side-by-side
3. **Custom date ranges**: Filter data by date range
4. **Volume progression**: Add volume-based progression tracking
5. **PR tracking**: Highlight personal records
6. **Goal setting**: Set and track strength goals
7. **Advanced statistics**: Standard deviation, trends, etc.
8. **Offline caching**: Cache calculations for faster loading

## Dependencies

- **MPAndroidChart**: Chart visualization
- **Material Components**: UI components (Tabs, Cards, etc.)
- **Gson**: Settings serialization
- **AndroidX**: Core Android libraries

## Testing Considerations

- Test with various data scenarios (empty, sparse, dense)
- Verify RPE normalization accuracy
- Test filtering rules with different workout types
- Validate regression calculations
- Test edge cases (single session, very old data, etc.)
- Verify chart rendering with different data distributions

