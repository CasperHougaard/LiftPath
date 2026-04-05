# PR (Personal Record) Logic Documentation

## Product Rules (source of truth)

| Rule | Decision |
|------|----------|
| What is a PR? | A true **all-time personal record** — the first time a canonical metric is beaten across all sessions |
| PR counting | Every PR **event** is counted individually (weight + volume + 1RM on one exercise = up to 3 events) |
| Intent scope | PRs are tracked **across all intents** together — Bench is Bench regardless of Strength/Build/Flush |
| Reps PRs | **Excluded** from the canonical PR system — not emitted, not counted, not displayed as PRs |
| First occurrence | Seeds the baseline **without emitting a PR**. A PR requires a previous best to beat |
| "Better than last session" | Kept as a separately named **improvement/trend signal** in the workout report — never called a PR |

---

## PR Types

Three canonical types:

1. **WEIGHT** — Heaviest single working-set weight for an exercise (all intents)
2. **VOLUME** — Highest total session volume (kg × reps, all working sets) for an exercise (all intents)
3. **ONE_RM** — Highest estimated 1-rep max (all intents; sets excluded when `effectiveReps > 15` or `RPE < 6.5`)

`PRType.REPS` remains in the enum for backward compatibility but is never emitted by the canonical engine.

---

## PR Detection Algorithm

### Location: `ProgressAnalysisHelper.processSessionsForPRs()` (private)

Single chronological pass over all sessions:

1. Sessions sorted by date, oldest first.
2. For each session, accumulate per-exercise session bests (weight, 1RM, volume).
3. After processing all sets for a session, compare session bests against all-time `ExerciseBests`.
4. **Baseline rule**: if the all-time best for this type is `null` (first occurrence), update the best but **do not emit a PR**.
5. **PR rule**: if the session best strictly exceeds the all-time best, update the best and emit a `PRRecord`.
6. At most **one PR per type per exercise per session** (session bests are buffered).

### `PRRecord` data class
```kotlin
data class PRRecord(
    val exerciseId: Int,
    val exerciseName: String,
    val intent: SetIntent,     // Context intent for display (not used for PR scoping)
    val prType: PRType,
    val value: Float,
    val previousValue: Float?, // null would indicate baseline (but baseline never emits)
    val date: String           // "yyyy/MM/dd" session date
)
```

### Per-type date tracking

`processSessionsForPRs` maintains `lastPrDateByType: Map<String, Long>` with keys:
- `"${exerciseId}_WEIGHT"`
- `"${exerciseId}_VOLUME"`
- `"${exerciseId}_1RM"`

This feeds the per-type recency colors in all PR displays.

---

## Public API

| Function | Purpose |
|----------|---------|
| `getRecentPRs(sessions, library, dayWindow)` | PR events within last N days; used by overview strip and home card |
| `getPRsForSession(sessions, sessionId)` | Canonical PR events for a specific session; used by workout report header count |
| `getExerciseStatsSummaries(sessions, library)` | Per-exercise all-time bests + per-type PR dates; used by PR tab and report trend cards |
| `getWeeklySummary(sessions, weekOffset)` | Session count, total volume, canonical `prCount` for the week |
| `getIntentDistribution(sessions, dayWindow)` | Intent breakdown percentages |
| `getMuscleTrends(sessions, library, weeksBack)` | Muscle trend percentages for overview muscle map |

### `ExerciseStatsSummary`
```kotlin
data class ExerciseStatsSummary(
    val exerciseId: Int,
    val exerciseName: String,
    val best1RM: Float?,
    val bestWeight: Float?,
    val bestVolume: Float?,
    val lastWeightPrDate: Long,   // ms timestamp; 0 = no weight PR ever
    val lastVolumePrDate: Long,
    val last1RMPrDate: Long
) {
    val lastPrDate: Long get() = maxOf(lastWeightPrDate, lastVolumePrDate, last1RMPrDate)
}
```

---

## Workout Report Trend Cards

Trend cards (`ExerciseTrendData`) are **separate from PR logic**. They answer "how did I do this session compared to recent same-intent history?" not "did I set a record?"

### Trend window (WorkoutComparisonHelper)

- Looks back up to **6 prior sessions** containing the same exercise and same intent.
- Direct comparison uses the **most recent** matching session's metrics.
- `intentSessionCount` = number of prior same-intent sessions found.
  - `0`: First time with this intent → shows "First time with this intent" note, no comparison.
  - `≥ 1`: Shows comparison values and % change.

### All-time PRs on trend cards

Each trend card also shows the all-time bests (weight, volume, 1RM) sourced from `ExerciseStatsSummary`. The all-time PR star badge (`image_pr_badge`) appears only when a **canonical all-time PR** was set in this session (`hasNewAllTimePR = true`), determined by `getPRsForSession`.

---

## UI Surfaces

| Surface | Source | Shows |
|---------|--------|-------|
| Overview "Weekly PRs" | `getWeeklySummary().prCount` | Canonical PR events in current week |
| Overview recent PR strip | `getRecentPRs(..., 30)` | Last 30 days PR events (weight, volume, 1RM) |
| Home momentum "PRs in 30 days" | `getRecentPRs(..., 30).size` | Total canonical PR events |
| Workout report header PR count | `getPRsForSession(...)` | Canonical PRs for that session |
| Workout report trend cards | `WorkoutComparisonHelper.calculateExerciseTrends` | Per-intent trend + all-time PR section |
| PR tab list | `getExerciseStatsSummaries` | All-time bests per exercise |
| PR tab filter chips | Weight, Volume, 1RM (no Reps) | Filter by canonical PR type |

### PR recency coloring

| Age | Color token | Meaning |
|-----|-------------|---------|
| ≤ 7 days | `pr_fresh` | Recent PR |
| 8–30 days | `pr_improved` | Within a month |
| > 30 days | `pr_older` | Older record |

---

## What Changed (vs pre-cleanup state)

| Area | Before | After |
|------|--------|-------|
| First session | Emitted a PR for every exercise | Seeds baseline only — no PR emitted |
| Reps PRs | Emitted as `PRType.REPS`, shown in PR tab | Excluded from canonical engine; `PRType.REPS` kept in enum only |
| PR date tracking | One `lastPrDate` per exercise | Separate `lastWeightPrDate`, `lastVolumePrDate`, `last1RMPrDate` per exercise |
| Weekly PR count | Hardcoded `0` | Derived from canonical engine |
| Report PR count | `WorkoutComparisonHelper.detectPRs` (intent-scoped, "better than last") | `ProgressAnalysisHelper.getPRsForSession` (all-time canonical) |
| Report trend "isPR" badge | "Better than immediately previous session" | "Set a canonical all-time PR this session" |
| Report trend comparison | Single previous session (any intent) | Up to 6 previous sessions with same exercise + same intent |
| PR filter chips | "Strength / Volume / 1RM / Reps" | "Weight / Volume / 1RM" (Reps chip removed) |
| PR card value format | Always `%.1f kg` (broke volume and reps display) | Type-aware: volume as `%,d kg`, others as `%.1f kg` |

---

## Related Files

- `ProgressAnalysisHelper.kt` — canonical PR engine (single source of truth)
- `WorkoutComparisonHelper.kt` — session summary + per-intent trend window
- `OneRMEstimationHelper.kt` — 1RM calculation (gating conditions)
- `ExercisePRStatsAdapter.kt` — PR tab card list
- `ExerciseTrendAdapter.kt` — workout report exercise trend cards
- `ProgressPRsFragment.kt` — PR tab
- `ProgressOverviewFragment.kt` — overview strip + weekly stats
- `MainActivity.kt` — home momentum PR count
- `WorkoutReportActivity.kt` — post-session report
- `DataModels.kt` — `ExerciseTrendData`, `WorkoutSummary`
