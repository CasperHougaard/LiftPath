package com.liftpath.helpers

import com.liftpath.models.ExerciseFamily
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.SetIntent
import com.liftpath.models.TrainingSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.round

/**
 * Single canonical source of truth for Personal Record detection.
 *
 * Rules (confirmed product decisions):
 *  - PRs are ALL-TIME records only. First occurrence of an exercise seeds the baseline and does NOT emit a PR.
 *  - PR types: WEIGHT, VOLUME, ONE_RM. Reps are intentionally excluded.
 *  - PRs are tracked across all intents together (not per-intent).
 *  - At most one PR per type per exercise per session.
 *  - 1RM is gated: OneRMEstimationHelper returns null when effectiveReps > 15 or RPE < 6.5.
 */
object ProgressAnalysisHelper {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    /**
     * Return canonical PR events within the last [dayWindow] days.
     * Used by home screen momentum card and overview recent-PR strip.
     */
    fun getRecentPRs(
        sessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>,
        dayWindow: Int = 30
    ): List<PRRecord> {
        val cutoffTime = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -dayWindow)
        }.timeInMillis

        val (prs, _) = processSessionsForPRs(sessions)
        return prs.filter { pr ->
            try {
                (dateFormat.parse(pr.date)?.time ?: 0L) >= cutoffTime
            } catch (e: Exception) {
                false
            }
        }.distinctBy { "${it.exerciseId}_${it.prType}_${it.date}" }
    }

    /**
     * Return canonical PR events that were achieved in a specific session.
     * Returns an empty list for the first-ever session per exercise (baseline only).
     * Used by WorkoutReportActivity to get the accurate PR count for a completed session.
     */
    fun getPRsForSession(
        sessions: List<TrainingSession>,
        sessionId: String
    ): List<PRRecord> {
        val session = sessions.find { it.id == sessionId } ?: return emptyList()
        val (prs, _) = processSessionsForPRs(sessions)
        return prs.filter { it.date == session.date }
    }

    /**
     * Per-exercise all-time bests for the PR page (Player Stats Card list).
     * One entry per exercise that has at least one canonical PR after its baseline session.
     * Sorted by [ExerciseStatsSummary.lastPrDate] DESC by the caller.
     */
    fun getExerciseStatsSummaries(
        sessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): List<ExerciseStatsSummary> {
        val (_, processed) = processSessionsForPRs(sessions)
        val bestByExercise = processed.bestByExercise
        val lastPrDateByType = processed.lastPrDateByType

        return bestByExercise.keys
            .filter { exerciseId ->
                val typeKey = { suffix: String -> lastPrDateByType["${exerciseId}_$suffix"] ?: 0L }
                typeKey("WEIGHT") > 0L || typeKey("VOLUME") > 0L || typeKey("1RM") > 0L ||
                    typeKey("TIME_HOLD") > 0L
            }
            .map { exerciseId ->
                val b = bestByExercise[exerciseId]!!
                val name = exerciseLibrary.find { it.id == exerciseId }?.name
                    ?: sessions.asSequence()
                        .flatMap { it.exercises.asSequence() }
                        .firstOrNull { it.exerciseId == exerciseId }?.exerciseName
                    ?: "Exercise $exerciseId"
                ExerciseStatsSummary(
                    exerciseId = exerciseId,
                    exerciseName = name,
                    best1RM = if ((lastPrDateByType["${exerciseId}_1RM"] ?: 0L) > 0L) b.max1RM else null,
                    bestWeight = if ((lastPrDateByType["${exerciseId}_WEIGHT"] ?: 0L) > 0L) b.maxWeight else null,
                    bestVolume = if ((lastPrDateByType["${exerciseId}_VOLUME"] ?: 0L) > 0L) b.maxVolume else null,
                    bestHoldSeconds = if ((lastPrDateByType["${exerciseId}_TIME_HOLD"] ?: 0L) > 0L) b.maxHoldSeconds else null,
                    lastWeightPrDate = lastPrDateByType["${exerciseId}_WEIGHT"] ?: 0L,
                    lastVolumePrDate = lastPrDateByType["${exerciseId}_VOLUME"] ?: 0L,
                    last1RMPrDate = lastPrDateByType["${exerciseId}_1RM"] ?: 0L,
                    lastHoldPrDate = lastPrDateByType["${exerciseId}_TIME_HOLD"] ?: 0L
                )
            }
    }

    /**
     * Core chronological pass over all sessions.
     * Builds the canonical PR event list and per-exercise all-time bests.
     *
     * Baseline rule: the first session that establishes a value for an exercise seeds the
     * all-time best without emitting a PR event.
     */
    private fun processSessionsForPRs(
        sessions: List<TrainingSession>
    ): Pair<List<PRRecord>, ProcessedBests> {
        val prs = mutableListOf<PRRecord>()
        val bestByExercise = mutableMapOf<Int, ExerciseBests>()
        // key: "${exerciseId}_WEIGHT" | "${exerciseId}_VOLUME" | "${exerciseId}_1RM"
        val lastPrDateByType = mutableMapOf<String, Long>()
        val sortedSessions = sessions.sortedBy { it.date }

        sortedSessions.forEach { session ->
            val sessionDate = try {
                dateFormat.parse(session.date)
            } catch (e: Exception) {
                null
            } ?: return@forEach
            val sessionTime = sessionDate.time

            val sessionBests = mutableMapOf<Int, SessionExerciseBests>()
            val sessionVolumeAccum = mutableMapOf<Int, Float>()

            session.exercises.filterNot { it.isWarmup || it.isSpecialSlotEntry() }.forEach { entry ->
                val exerciseId = entry.exerciseId
                bestByExercise.getOrPut(exerciseId) { ExerciseBests() }
                val sessionBest = sessionBests.getOrPut(exerciseId) { SessionExerciseBests() }

                // Timed holds use their own metric (longest hold) and are excluded from
                // weight-based weight/1RM/volume PRs (those formulas are rep-based).
                // Ranked by (seconds, load): at equal duration a heavier hold is the better set,
                // which is the only progression axis a weighted plank has.
                if (entry.isTimedEntry()) {
                    val secs = SetMetrics.holdSeconds(entry)
                    val best = sessionBest.bestHoldSeconds ?: 0
                    if (secs > best || (secs == best && entry.kg > (sessionBest.bestHoldLoadKg ?: 0f))) {
                        sessionBest.bestHoldSeconds = secs
                        sessionBest.bestHoldLoadKg = entry.kg
                    }
                    return@forEach
                }

                // Track session-best weight
                if (entry.kg > (sessionBest.bestWeight ?: 0f)) {
                    sessionBest.bestWeight = entry.kg
                }

                // Track session-best estimated 1RM (gated by OneRMEstimationHelper)
                val oneRM = OneRMEstimationHelper.calculateOneRM(entry.kg, entry.reps, entry.rpe)
                if (oneRM != null && oneRM > (sessionBest.best1RM ?: 0f)) {
                    sessionBest.best1RM = oneRM
                }

                // Accumulate volume per exercise for this session
                sessionVolumeAccum[exerciseId] =
                    (sessionVolumeAccum[exerciseId] ?: 0f) + SetMetrics.volumeKg(entry)
            }

            // --- Volume PRs: one per exercise per session ---
            sessionVolumeAccum.forEach { (exerciseId, sessionVolume) ->
                val current = bestByExercise.getOrPut(exerciseId) { ExerciseBests() }
                val sessionKey = "${exerciseId}_${session.date}"
                if (current.sessionVolumes.contains(sessionKey)) return@forEach
                current.sessionVolumes.add(sessionKey)

                val prevVolume = current.maxVolume
                if (prevVolume == null) {
                    current.maxVolume = sessionVolume  // Baseline — no PR emitted
                } else if (sessionVolume > prevVolume) {
                    current.maxVolume = sessionVolume
                    val name = session.exercises
                        .firstOrNull { it.exerciseId == exerciseId }?.exerciseName
                        ?: "Exercise $exerciseId"
                    val intent = session.exercises
                        .firstOrNull { it.exerciseId == exerciseId }
                        ?.getEffectiveIntent(session.defaultWorkoutType) ?: SetIntent.BUILD
                    prs.add(
                        PRRecord(
                            exerciseId = exerciseId,
                            exerciseName = name,
                            intent = intent,
                            prType = PRType.VOLUME,
                            value = sessionVolume,
                            previousValue = prevVolume,
                            date = session.date
                        )
                    )
                    lastPrDateByType["${exerciseId}_VOLUME"] = sessionTime
                }
            }

            // --- Weight and 1RM PRs: one per type per exercise per session ---
            sessionBests.forEach { (exerciseId, sessionBest) ->
                val current = bestByExercise.getOrPut(exerciseId) { ExerciseBests() }
                val exerciseName = session.exercises
                    .firstOrNull { it.exerciseId == exerciseId }?.exerciseName
                    ?: "Exercise $exerciseId"

                // Weight
                if (sessionBest.bestWeight != null) {
                    val prev = current.maxWeight
                    if (prev == null) {
                        current.maxWeight = sessionBest.bestWeight  // Baseline
                    } else if (sessionBest.bestWeight!! > prev) {
                        current.maxWeight = sessionBest.bestWeight
                        prs.add(
                            PRRecord(
                                exerciseId = exerciseId,
                                exerciseName = exerciseName,
                                intent = SetIntent.STRENGTH,
                                prType = PRType.WEIGHT,
                                value = sessionBest.bestWeight!!,
                                previousValue = prev,
                                date = session.date
                            )
                        )
                        lastPrDateByType["${exerciseId}_WEIGHT"] = sessionTime
                    }
                }

                // 1RM
                if (sessionBest.best1RM != null) {
                    val prev = current.max1RM
                    if (prev == null) {
                        current.max1RM = sessionBest.best1RM  // Baseline
                    } else if (sessionBest.best1RM!! > prev) {
                        current.max1RM = sessionBest.best1RM
                        prs.add(
                            PRRecord(
                                exerciseId = exerciseId,
                                exerciseName = exerciseName,
                                intent = SetIntent.STRENGTH,
                                prType = PRType.ONE_RM,
                                value = sessionBest.best1RM!!,
                                previousValue = prev,
                                date = session.date
                            )
                        )
                        lastPrDateByType["${exerciseId}_1RM"] = sessionTime
                    }
                }

                // Longest hold (timed exercises), ranked by (seconds, load)
                if (sessionBest.bestHoldSeconds != null) {
                    val prev = current.maxHoldSeconds
                    val secs = sessionBest.bestHoldSeconds!!
                    val load = sessionBest.bestHoldLoadKg ?: 0f
                    val beatsPrev = prev != null &&
                        (secs > prev || (secs == prev && load > (current.maxHoldLoadKg ?: 0f)))
                    if (prev == null) {
                        current.maxHoldSeconds = secs  // Baseline
                        current.maxHoldLoadKg = load
                    } else if (beatsPrev) {
                        current.maxHoldSeconds = secs
                        current.maxHoldLoadKg = load
                        val intent = session.exercises
                            .firstOrNull { it.exerciseId == exerciseId }
                            ?.getEffectiveIntent(session.defaultWorkoutType) ?: SetIntent.BUILD
                        prs.add(
                            PRRecord(
                                exerciseId = exerciseId,
                                exerciseName = exerciseName,
                                intent = intent,
                                prType = PRType.TIME_HOLD,
                                value = sessionBest.bestHoldSeconds!!.toFloat(),
                                previousValue = prev.toFloat(),
                                date = session.date
                            )
                        )
                        lastPrDateByType["${exerciseId}_TIME_HOLD"] = sessionTime
                    }
                }
            }
        }

        return Pair(prs, ProcessedBests(bestByExercise, lastPrDateByType))
    }

    /**
     * Weekly summary for a given week offset (0 = current week).
     * prCount is derived from the canonical PR engine.
     */
    fun getWeeklySummary(
        sessions: List<TrainingSession>,
        weekOffset: Int = 0
    ): WeeklySummary {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.WEEK_OF_YEAR, -weekOffset)
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val weekStart = calendar.time

        calendar.add(Calendar.DAY_OF_YEAR, 7)
        val weekEnd = calendar.time

        val weeklySessions = sessions.filter { session ->
            try {
                val date = dateFormat.parse(session.date)
                date != null && date.time >= weekStart.time && date.time < weekEnd.time
            } catch (e: Exception) {
                false
            }
        }

        val totalVolume = weeklySessions.sumOf { session ->
            SetMetrics.totalVolumeKg(session.exercises.filterNot { it.isWarmup }).toDouble()
        }.toFloat()
        val totalHoldSeconds = weeklySessions.sumOf { session ->
            SetMetrics.totalHoldSeconds(session.exercises.filterNot { it.isWarmup })
        }

        // Canonical PR count: events whose session date falls in this week
        val (allPrs, _) = processSessionsForPRs(sessions)
        val prCount = allPrs.count { pr ->
            try {
                val date = dateFormat.parse(pr.date)
                date != null && date.time >= weekStart.time && date.time < weekEnd.time
            } catch (e: Exception) {
                false
            }
        }

        val intentCounts = mutableMapOf<SetIntent, Int>()
        weeklySessions.forEach { session ->
            session.exercises.filterNot { it.isWarmup }.forEach { entry ->
                val intent = entry.getEffectiveIntent(session.defaultWorkoutType)
                intentCounts[intent] = (intentCounts[intent] ?: 0) + 1
            }
        }
        val dominantIntent = intentCounts.maxByOrNull { it.value }?.key ?: SetIntent.BUILD

        return WeeklySummary(
            totalVolume = totalVolume,
            totalHoldSeconds = totalHoldSeconds,
            sessionCount = weeklySessions.size,
            prCount = prCount,
            dominantIntent = dominantIntent
        )
    }

    /**
     * Rolling summary over the last [dayCount] calendar days, inclusive of today
     * (from start of the oldest day through start of tomorrow as the exclusive end).
     * [prCount] uses the canonical PR engine, counting PRs whose session date falls in the window.
     */
    private fun rollingCalendarDayWindow(dayCount: Int): Pair<Date, Date> {
        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val windowEnd = endCal.time

        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(dayCount - 1))
        }
        val windowStart = startCal.time
        return windowStart to windowEnd
    }

    /**
     * Mean RPE over working sets with logged RPE in the rolling [dayCount]-day window,
     * split by effective intent (build vs strength only).
     */
    fun getBuildStrengthRpeAverages(
        sessions: List<TrainingSession>,
        dayCount: Int = 21
    ): Pair<Float?, Float?> {
        val (windowStart, windowEnd) = rollingCalendarDayWindow(dayCount)
        val windowSessions = sessions.filter { session ->
            try {
                val date = dateFormat.parse(session.date)
                date != null && date.time >= windowStart.time && date.time < windowEnd.time
            } catch (e: Exception) {
                false
            }
        }

        val buildRpes = mutableListOf<Float>()
        val strengthRpes = mutableListOf<Float>()
        windowSessions.forEach { session ->
            session.exercises.forEach { entry ->
                if (entry.isEffectivelyWarmup()) return@forEach
                val rpe = entry.rpe ?: return@forEach
                when (entry.getEffectiveIntent(session.defaultWorkoutType)) {
                    SetIntent.BUILD -> buildRpes.add(rpe)
                    SetIntent.STRENGTH -> strengthRpes.add(rpe)
                    else -> Unit
                }
            }
        }

        val buildAvg = buildRpes.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val strengthAvg = strengthRpes.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        return buildAvg to strengthAvg
    }

    fun getRollingDaysSummary(
        sessions: List<TrainingSession>,
        dayCount: Int = 21
    ): WeeklySummary {
        val (windowStart, windowEnd) = rollingCalendarDayWindow(dayCount)

        val windowSessions = sessions.filter { session ->
            try {
                val date = dateFormat.parse(session.date)
                date != null && date.time >= windowStart.time && date.time < windowEnd.time
            } catch (e: Exception) {
                false
            }
        }

        val totalVolume = windowSessions.sumOf { session ->
            SetMetrics.totalVolumeKg(session.exercises.filterNot { it.isWarmup }).toDouble()
        }.toFloat()
        val totalHoldSeconds = windowSessions.sumOf { session ->
            SetMetrics.totalHoldSeconds(session.exercises.filterNot { it.isWarmup })
        }

        val (allPrs, _) = processSessionsForPRs(sessions)
        val prCount = allPrs.count { pr ->
            try {
                val date = dateFormat.parse(pr.date)
                date != null && date.time >= windowStart.time && date.time < windowEnd.time
            } catch (e: Exception) {
                false
            }
        }

        val intentCounts = mutableMapOf<SetIntent, Int>()
        windowSessions.forEach { session ->
            session.exercises.filterNot { it.isWarmup }.forEach { entry ->
                val intent = entry.getEffectiveIntent(session.defaultWorkoutType)
                intentCounts[intent] = (intentCounts[intent] ?: 0) + 1
            }
        }
        val dominantIntent = intentCounts.maxByOrNull { it.value }?.key ?: SetIntent.BUILD

        return WeeklySummary(
            totalVolume = totalVolume,
            totalHoldSeconds = totalHoldSeconds,
            sessionCount = windowSessions.size,
            prCount = prCount,
            dominantIntent = dominantIntent
        )
    }

    /**
     * Intent distribution as percentages over a time window.
     */
    fun getIntentDistribution(
        sessions: List<TrainingSession>,
        dayWindow: Int = 30
    ): Map<SetIntent, Float> {
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -dayWindow)
        }.time

        val intentCounts = mutableMapOf<SetIntent, Int>()
        var totalSets = 0

        sessions.forEach { session ->
            try {
                val date = dateFormat.parse(session.date) ?: return@forEach
                if (date.time < cutoffDate.time) return@forEach

                session.exercises.filterNot { it.isWarmup }.forEach { entry ->
                    val intent = entry.getEffectiveIntent(session.defaultWorkoutType)
                    if (intent != SetIntent.UNKNOWN && intent != SetIntent.WARMUP) {
                        intentCounts[intent] = (intentCounts[intent] ?: 0) + 1
                        totalSets++
                    }
                }
            } catch (e: Exception) {
                // Skip invalid dates
            }
        }

        if (totalSets == 0) return emptyMap()

        return intentCounts.mapValues { (_, count) ->
            (count.toFloat() / totalSets) * 100f
        }
    }

    /**
     * Aggregated stats per exercise family across all sessions.
     * Falls back to live library lookup when `familyIdSnapshot` is null on a session entry.
     */
    fun getFamilyStatsSummaries(
        sessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>,
        exerciseFamilies: List<ExerciseFamily>
    ): List<FamilyStatsSummary> {
        val libraryFamilyMap = exerciseLibrary.associate { it.id to it.familyId }
        val familyNameMap = exerciseFamilies.associate { it.id to it.name }

        data class Accum(
            var totalSets: Int = 0,
            var totalReps: Int = 0,
            var totalVolume: Float = 0f,
            var totalHoldSeconds: Int = 0,
            val sessionIds: MutableSet<String> = mutableSetOf()
        )

        val accum = mutableMapOf<String, Accum>()

        sessions.forEach { session ->
            session.exercises.filterNot { it.isWarmup }.forEach { entry ->
                val familyId = entry.familyIdSnapshot
                    ?: libraryFamilyMap[entry.exerciseId]
                    ?: return@forEach
                val a = accum.getOrPut(familyId) { Accum() }
                a.totalSets++
                a.totalReps += SetMetrics.repsForStats(entry)
                a.totalVolume += SetMetrics.volumeKg(entry)
                a.totalHoldSeconds += SetMetrics.holdSeconds(entry)
                a.sessionIds.add(session.id)
            }
        }

        return accum.entries
            .map { (familyId, a) ->
                FamilyStatsSummary(
                    familyId = familyId,
                    familyName = familyNameMap[familyId] ?: familyId,
                    totalSets = a.totalSets,
                    totalReps = a.totalReps,
                    totalVolume = a.totalVolume,
                    totalHoldSeconds = a.totalHoldSeconds,
                    sessionCount = a.sessionIds.size
                )
            }
            .sortedByDescending { it.totalVolume }
    }

    // ---- Data classes ----

    data class PRRecord(
        val exerciseId: Int,
        val exerciseName: String,
        val intent: SetIntent,
        val prType: PRType,
        val value: Float,
        val previousValue: Float? = null,
        val date: String
    )

    /**
     * Per-exercise stats for the PR page. All-time bests with per-type PR dates for
     * proper recency coloring in [ExercisePRStatsAdapter].
     */
    data class ExerciseStatsSummary(
        val exerciseId: Int,
        val exerciseName: String,
        val best1RM: Float?,
        val bestWeight: Float?,
        val bestVolume: Float?,
        /** Longest single hold in seconds; null unless this exercise has a timed-hold PR. */
        val bestHoldSeconds: Int? = null,
        val lastWeightPrDate: Long,   // ms timestamp; 0 = no weight PR ever
        val lastVolumePrDate: Long,   // ms timestamp; 0 = no volume PR ever
        val last1RMPrDate: Long,      // ms timestamp; 0 = no 1RM PR ever
        val lastHoldPrDate: Long = 0L // ms timestamp; 0 = no hold PR ever
    ) {
        /** Most recent PR date across all types. Used for card recency coloring and sorting. */
        val lastPrDate: Long
            get() = maxOf(lastWeightPrDate, lastVolumePrDate, last1RMPrDate, lastHoldPrDate)
    }

    data class WeeklySummary(
        val totalVolume: Float,
        /** Total time under tension from timed holds, which carry no rep-based volume. */
        val totalHoldSeconds: Int = 0,
        val sessionCount: Int,
        val prCount: Int,
        val dominantIntent: SetIntent
    )

    data class FamilyStatsSummary(
        val familyId: String,
        val familyName: String,
        val totalSets: Int,
        val totalReps: Int,
        val totalVolume: Float,
        /** Total time under tension from timed holds, which carry no rep-based volume. */
        val totalHoldSeconds: Int = 0,
        val sessionCount: Int
    )

    enum class PRType {
        WEIGHT,
        VOLUME,
        ONE_RM,
        TIME_HOLD,  // Longest single-set hold for timed/isometric exercises (value in seconds)
        REPS  // Retained in enum for legacy compatibility; not emitted by canonical engine
    }

    private data class ProcessedBests(
        val bestByExercise: Map<Int, ExerciseBests>,
        val lastPrDateByType: Map<String, Long>  // "${exerciseId}_WEIGHT|VOLUME|1RM"
    )

    private data class SessionExerciseBests(
        var bestWeight: Float? = null,
        var best1RM: Float? = null,
        var bestHoldSeconds: Int? = null,
        /** Load carried during [bestHoldSeconds]; tie-breaks equal-duration holds. */
        var bestHoldLoadKg: Float? = null
    )

    private data class ExerciseBests(
        var maxWeight: Float? = null,
        var max1RM: Float? = null,
        var maxVolume: Float? = null,
        var maxHoldSeconds: Int? = null,
        /** Load carried during [maxHoldSeconds]; tie-breaks equal-duration holds. */
        var maxHoldLoadKg: Float? = null,
        val sessionVolumes: MutableSet<String> = mutableSetOf()
    )

    /**
     * Muscle group trends over the last [weeksBack] weeks.
     * Returns a map of TargetMuscle → trend percentage (1RM > Volume > Strength priority).
     */
    fun getMuscleTrends(
        sessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>,
        weeksBack: Int = 4
    ): Map<com.liftpath.models.TargetMuscle, Float?> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.WEEK_OF_YEAR, -weeksBack)
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val cutoffDate = calendar.time

        val filteredSessions = sessions.filter { session ->
            try {
                val date = dateFormat.parse(session.date)
                date != null && date.time >= cutoffDate.time
            } catch (e: Exception) {
                false
            }
        }.sortedBy { it.date }

        if (filteredSessions.isEmpty()) return emptyMap()

        val halfwayPoint = filteredSessions.size / 2
        val firstPeriod = filteredSessions.take(halfwayPoint)
        val secondPeriod = filteredSessions.drop(halfwayPoint)

        val muscleTrends = mutableMapOf<com.liftpath.models.TargetMuscle, MusclePeriodData>()

        fun processPeriod(period: List<TrainingSession>, isFirst: Boolean) {
            period.forEach { session ->
                session.exercises.filterNot { it.isWarmup }.forEach { entry ->
                    val exercise = exerciseLibrary.find { it.id == entry.exerciseId }
                        ?: return@forEach
                    val intent = entry.getEffectiveIntent(session.defaultWorkoutType)

                    (exercise.primaryTargets + exercise.secondaryTargets).forEach { muscle ->
                        val data = muscleTrends.getOrPut(muscle) { MusclePeriodData() }

                        if (intent == SetIntent.STRENGTH) {
                            val oneRM =
                                OneRMEstimationHelper.calculateOneRM(entry.kg, entry.reps, entry.rpe)
                            if (oneRM != null) {
                                if (isFirst) data.first1RM = maxOf(data.first1RM ?: 0f, oneRM)
                                else data.second1RM = maxOf(data.second1RM ?: 0f, oneRM)
                            }
                        }

                        if (intent == SetIntent.BUILD) {
                            val volume = SetMetrics.volumeKg(entry)
                            if (isFirst) data.firstVolume = (data.firstVolume ?: 0f) + volume
                            else data.secondVolume = (data.secondVolume ?: 0f) + volume
                        }

                        if (intent == SetIntent.STRENGTH) {
                            if (isFirst) data.firstStrength = maxOf(data.firstStrength ?: 0f, entry.kg)
                            else data.secondStrength = maxOf(data.secondStrength ?: 0f, entry.kg)
                        }
                    }
                }
            }
        }

        processPeriod(firstPeriod, true)
        processPeriod(secondPeriod, false)

        return muscleTrends.mapValues { (_, data) ->
            when {
                data.first1RM != null && data.second1RM != null && data.first1RM!! > 0f ->
                    ((data.second1RM!! - data.first1RM!!) / data.first1RM!!) * 100f
                data.firstVolume != null && data.secondVolume != null && data.firstVolume!! > 0f ->
                    ((data.secondVolume!! - data.firstVolume!!) / data.firstVolume!!) * 100f
                data.firstStrength != null && data.secondStrength != null && data.firstStrength!! > 0f ->
                    ((data.secondStrength!! - data.firstStrength!!) / data.firstStrength!!) * 100f
                data.second1RM != null || data.secondVolume != null || data.secondStrength != null ->
                    null  // First time in window
                else -> null
            }
        }
    }

    /**
     * Per-muscle effort over the last [daysBack] days, as total volume (working sets only)
     * normalized 0f–1f against the hardest-worked muscle in the window — so the body figure
     * always has at least one muscle at full intensity when there has been any training, rather
     * than an absolute scale that would fade out for someone who trains lighter.
     *
     * Unlike [getMuscleTrends] this is a single window, not two halves compared — "how much",
     * not "more or less than before".
     */
    fun getRecentMuscleEffort(
        sessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>,
        daysBack: Int = 14
    ): Map<com.liftpath.models.TargetMuscle, Float> {
        val cutoffDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysBack) }.time

        val recentSessions = sessions.filter { session ->
            try {
                val date = dateFormat.parse(session.date)
                date != null && date.time >= cutoffDate.time
            } catch (e: Exception) {
                false
            }
        }

        val volumeByMuscle = mutableMapOf<com.liftpath.models.TargetMuscle, Float>()
        recentSessions.forEach { session ->
            session.exercises.filterNot { it.isWarmup }.forEach { entry ->
                val exercise = exerciseLibrary.find { it.id == entry.exerciseId } ?: return@forEach
                val volume = SetMetrics.volumeKg(entry)
                (exercise.primaryTargets + exercise.secondaryTargets).forEach { muscle ->
                    volumeByMuscle[muscle] = (volumeByMuscle[muscle] ?: 0f) + volume
                }
            }
        }

        val maxVolume = volumeByMuscle.values.maxOrNull() ?: return emptyMap()
        if (maxVolume <= 0f) return emptyMap()
        return volumeByMuscle.mapValues { (_, volume) -> volume / maxVolume }
    }

    private data class MusclePeriodData(
        var first1RM: Float? = null,
        var second1RM: Float? = null,
        var firstVolume: Float? = null,
        var secondVolume: Float? = null,
        var firstStrength: Float? = null,
        var secondStrength: Float? = null
    )
}
