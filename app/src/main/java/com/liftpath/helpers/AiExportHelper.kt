package com.liftpath.helpers

import android.content.Context
import com.liftpath.models.ExerciseEntry
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.SetIntent
import com.liftpath.models.TrainingData
import com.liftpath.models.TrainingSession
import com.liftpath.models.WithingsScanEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a self-describing Markdown summary of the user's training for handing to an external AI
 * coach (Claude/ChatGPT). The document leads with a legend so the model needs no knowledge of the
 * app, then renders the latest session in full detail, the recent history, and computed
 * progression context (trends, all-time PRs, next-session suggestions).
 *
 * This is a pure builder: it reads from the passed [TrainingData] plus the body-weight/Withings
 * helpers and returns a String. All heavy lifting (1RM, PRs, trends, suggestions) is delegated to
 * the existing canonical helpers so the export matches what the app shows elsewhere.
 */
object AiExportHelper {

    /** Recent sessions rendered in full detail. Progression context always uses full history. */
    const val DEFAULT_SESSION_COUNT = 10

    /** All-time-best rows capped to keep the paste size LLM-friendly. */
    private const val MAX_ALL_TIME_ROWS = 15

    /** Suggested week-on-week increment for a timed hold, the analogue of a weight jump. */
    private const val HOLD_PROGRESSION_STEP_SECONDS = 5

    fun buildMarkdown(
        context: Context,
        trainingData: TrainingData,
        sessionCount: Int = DEFAULT_SESSION_COUNT
    ): String {
        // Each call gets its own formatter — SimpleDateFormat is not thread-safe.
        val dateMs = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        val library = trainingData.exerciseLibrary
        val sb = StringBuilder()

        appendHeader(sb, dateMs)
        appendLegend(sb)
        appendAthleteSnapshot(sb, context, trainingData, dateMs)

        val sessionsNewestFirst = trainingData.trainings.sortedByDescending { it.date }
        if (sessionsNewestFirst.isEmpty()) {
            sb.append("\n## Training\nNo training sessions logged yet.\n")
            return sb.toString()
        }

        val allSessions = trainingData.trainings
        val settings = ProgressionSettingsManager(context).getSettings()
        val incrementTable = WeightIncrementSettingsManager(context).getTable()

        // --- Latest training (full detail) ---
        val latest = sessionsNewestFirst.first()
        sb.append("\n## Latest Training\n")
        appendSessionDetail(
            sb, latest, allSessions, library, settings, incrementTable, trainingData,
            isLatest = true, dateMs = dateMs
        )

        // --- Recent history (sessions 2..N) ---
        val history = sessionsNewestFirst.drop(1).take(sessionCount - 1)
        if (history.isNotEmpty()) {
            sb.append("\n## Recent History (older sessions)\n")
            history.forEach { session ->
                appendSessionDetail(
                    sb, session, allSessions, library, settings, incrementTable, trainingData,
                    isLatest = false, dateMs = dateMs
                )
            }
        }

        // --- Progression context (full history) ---
        appendProgressionContext(sb, allSessions, library, dateMs)

        return sb.toString()
    }

    // ---------------------------------------------------------------------------------------------
    // Sections
    // ---------------------------------------------------------------------------------------------

    private fun appendHeader(sb: StringBuilder, dateMs: SimpleDateFormat) {
        sb.append("# LiftPath AI Training Export\n")
        sb.append("Exported: ").append(dateMs.format(Date(System.currentTimeMillis())))
        sb.append(" · Units: kg\n")
    }

    private fun appendLegend(sb: StringBuilder) {
        sb.append("\n## Legend (read me first)\n")
        sb.append("- All loads are in kilograms (kg).\n")
        sb.append("- Bodyweight sets show `BW<base>+<added>=<effective>` (a `-` means assistance). ")
        sb.append("The effective load is what all volume/1RM/PR math uses.\n")
        sb.append("- Set intent: STRENGTH (3–6 reps, ~8.5 RPE), BUILD (8–12 reps, ~8 RPE), ")
        sb.append("FLUSH (~20 reps, ~6.5 RPE), WARMUP. Sessions logged before intents existed ")
        sb.append("have their intent inferred from rep ranges.\n")
        sb.append("- RPE is Rate of Perceived Exertion (1–10); reps-in-reserve = 10 − RPE.\n")
        sb.append("- est.1RM is an estimated one-rep max: Epley (≤8 effective reps) or Brzycki ")
        sb.append("(9–15), RPE-normalized. Shown as `n/a` when RPE < 6.5 or effective reps > 15.\n")
        sb.append("- Warmup sets are excluded from all volume/1RM/PR calculations.\n")
        sb.append("- PRs are ALL-TIME records (Weight, Volume, est.1RM). The first time an exercise ")
        sb.append("is logged seeds a baseline and is not counted as a PR.\n")
    }

    private fun appendAthleteSnapshot(
        sb: StringBuilder,
        context: Context,
        trainingData: TrainingData,
        dateMs: SimpleDateFormat
    ) {
        sb.append("\n## Athlete Snapshot\n")

        val resolved = BodyWeightHelper.resolveBodyWeight(context)
        val sourceLabel = when (resolved.source) {
            BodyWeightHelper.BodyWeightSource.WITHINGS -> "Withings body scan"
            BodyWeightHelper.BodyWeightSource.MANUAL -> "manual entry"
            BodyWeightHelper.BodyWeightSource.NONE -> "unknown"
        }
        if (resolved.kg != null) {
            sb.append("- Current body weight: ").append(fmt(resolved.kg)).append(" kg (source: ")
                .append(sourceLabel).append(")\n")
        } else {
            sb.append("- Current body weight: unknown (not set)\n")
        }

        sb.append("- Training level: ").append(trainingData.userLevel.displayName).append("\n")

        val scans = WithingsStorageHelper(context).read().entries
            .filter { it.weightKg != null }
            .sortedByDescending { it.dateMs }
        val newest = scans.firstOrNull()
        if (newest == null) {
            sb.append("- Body composition: no body-scan data synced.\n")
        } else {
            sb.append("- Latest body scan (").append(dateMs.format(Date(newest.dateMs))).append("): ")
                .append(scanMetrics(newest)).append("\n")
            // Compare against the most recent scan that is at least 2 weeks older.
            val ref = scans.firstOrNull { newest.dateMs - it.dateMs >= BodyWeightHelper.TWO_WEEKS_MS }
            if (ref != null) {
                sb.append("- Change since ").append(dateMs.format(Date(ref.dateMs))).append(": ")
                    .append(scanDelta(ref, newest)).append("\n")
            }
        }
    }

    private fun appendSessionDetail(
        sb: StringBuilder,
        session: TrainingSession,
        allSessions: List<TrainingSession>,
        library: List<ExerciseLibraryItem>,
        settings: ProgressionHelper.ProgressionSettings,
        incrementTable: EquipmentIncrementTable,
        trainingData: TrainingData,
        isLatest: Boolean,
        dateMs: SimpleDateFormat
    ) {
        val summary = WorkoutComparisonHelper.calculateSessionSummary(session, allSessions)
        val typeLabel = session.defaultWorkoutType?.takeIf { it.isNotBlank() } ?: "—"
        val durationLabel = session.durationSeconds?.let { " · ${it / 60} min" } ?: ""

        sb.append("\n### Session #").append(session.trainingNumber)
            .append(" · ").append(session.date)
            .append(" · ").append(typeLabel)
            .append(durationLabel).append("\n")
        if (session.planName != null) sb.append("Plan: ").append(session.planName).append("\n")
        sb.append("Summary: ").append(summary.totalSets).append(" working sets · ")
            .append(summary.exerciseCount).append(" exercises · volume ")
            .append(fmtVol(summary.totalVolume)).append(" kg")
        // Timed holds contribute no rep-based volume, so report their work separately.
        if (summary.holdSetCount > 0) {
            sb.append(" · hold time ")
                .append(RestTimerHelper.formatHoldTotal(summary.totalHoldSeconds))
                .append(" over ").append(summary.holdSetCount).append(" holds")
        }
        sb.append(" · PRs this session: ").append(summary.prCount).append("\n")

        // Per-exercise trend rows (current vs prior same-intent session), keyed for lookup.
        val trendsById = WorkoutComparisonHelper
            .calculateExerciseTrends(session, allSessions, library)
            .associateBy { it.exerciseId }

        // Preserve the logged order of exercises.
        val byExercise = LinkedHashMap<Int, MutableList<ExerciseEntry>>()
        session.exercises.forEach { byExercise.getOrPut(it.exerciseId) { mutableListOf() }.add(it) }

        byExercise.forEach { (exerciseId, entries) ->
            val workingSets = entries.filterNot { it.isEffectivelyWarmup() }
            val warmupCount = entries.size - workingSets.size
            val libItem = library.find { it.id == exerciseId }
            val name = libItem?.name ?: entries.first().exerciseName
            val intent = resolveExerciseIntent(session, exerciseId, workingSets)

            sb.append("\n#### ").append(name)
            val meta = exerciseMeta(libItem)
            if (meta.isNotEmpty()) sb.append(" — ").append(meta)
            sb.append(" [").append(intent.displayName).append("]\n")

            if (workingSets.isEmpty()) {
                sb.append("_Warmup only (").append(warmupCount).append(" sets)._\n")
                return@forEach
            }

            sb.append("| set | load | reps | RPE | est.1RM | note |\n")
            sb.append("|---:|---:|---:|---:|---:|---|\n")
            workingSets.forEach { e ->
                // Timed holds report duration in the reps column and skip rep-based 1RM.
                val oneRm = if (e.isTimedEntry()) null
                            else OneRMEstimationHelper.calculateOneRM(e.kg, e.reps, e.rpe)
                val repsCell = if (e.isTimedEntry()) RestTimerHelper.formatDuration(e.durationSeconds ?: 0)
                               else e.reps.toString()
                sb.append("| ").append(e.setNumber)
                    .append(" | ").append(loadCell(e))
                    .append(" | ").append(repsCell)
                    .append(" | ").append(e.rpe?.let { fmt(it) } ?: "—")
                    .append(" | ").append(oneRm?.let { fmt(it) } ?: "n/a")
                    .append(" | ").append(noteCell(e.note))
                    .append(" |\n")
            }
            if (warmupCount > 0) sb.append("_(+").append(warmupCount).append(" warmup sets)_\n")

            // Trend vs prior same-intent session (rich line only on the latest session).
            if (isLatest) {
                trendsById[exerciseId]?.let { t ->
                    sb.append(trendLine(t)).append("\n")
                    sb.append(nextSessionLine(exerciseId, intent, t, libItem, trainingData, settings, incrementTable))
                }
            }
        }

        // PR callouts for this session.
        val prs = ProgressAnalysisHelper.getPRsForSession(allSessions, session.id)
        if (prs.isNotEmpty()) {
            sb.append("\nPRs set this session:\n")
            prs.forEach { pr ->
                sb.append("- ").append(pr.exerciseName).append(": ")
                    .append(prTypeLabel(pr.prType)).append(" ").append(prValue(pr))
                pr.previousValue?.let { sb.append(" (prev ").append(prValue(pr, it)).append(")") }
                sb.append("\n")
            }
        }
    }

    private fun appendProgressionContext(
        sb: StringBuilder,
        allSessions: List<TrainingSession>,
        library: List<ExerciseLibraryItem>,
        dateMs: SimpleDateFormat
    ) {
        sb.append("\n## Progression Context (all-time)\n")

        // All-time bests per exercise, most recent PR first.
        val summaries = ProgressAnalysisHelper
            .getExerciseStatsSummaries(allSessions, library)
            .sortedByDescending { it.lastPrDate }
        if (summaries.isNotEmpty()) {
            sb.append("\n### All-time bests (most recently improved first)\n")
            sb.append("| exercise | best weight | best est.1RM | best volume | longest hold | last PR |\n")
            sb.append("|---|---:|---:|---:|---:|---|\n")
            summaries.take(MAX_ALL_TIME_ROWS).forEach { s ->
                sb.append("| ").append(s.exerciseName)
                    .append(" | ").append(s.bestWeight?.let { fmt(it) } ?: "—")
                    .append(" | ").append(s.best1RM?.let { fmt(it) } ?: "—")
                    .append(" | ").append(s.bestVolume?.let { fmtVol(it) } ?: "—")
                    .append(" | ").append(s.bestHoldSeconds?.let { RestTimerHelper.formatDuration(it) } ?: "—")
                    .append(" | ").append(if (s.lastPrDate > 0L) dateMs.format(Date(s.lastPrDate)) else "—")
                    .append(" |\n")
            }
            if (summaries.size > MAX_ALL_TIME_ROWS) {
                sb.append("_… ").append(summaries.size - MAX_ALL_TIME_ROWS)
                    .append(" more exercises with PRs not shown._\n")
            }
        }

        // Recent PRs (last 30 days).
        val recentPrs = ProgressAnalysisHelper.getRecentPRs(allSessions, library, 30)
        if (recentPrs.isNotEmpty()) {
            sb.append("\n### Recent PRs (last 30 days)\n")
            recentPrs.forEach { pr ->
                sb.append("- ").append(pr.date).append(" — ").append(pr.exerciseName)
                    .append(": ").append(prTypeLabel(pr.prType)).append(" ").append(prValue(pr))
                pr.previousValue?.let {
                    sb.append(" (prev ").append(prValue(pr, it)).append(")")
                }
                sb.append("\n")
            }
        }

        // Volume & strength trend over the recent training block.
        val metrics = allSessions.sortedBy { it.date }.map { session ->
            // Timed holds carry no reps; exclude them from rep-based volume and 1RM.
            val working = session.exercises
                .filterNot { it.isEffectivelyWarmup() || it.isTimedEntry() }
            val volume = working.sumOf { (it.kg * it.reps).toDouble() }.toFloat()
            val oneRm = working.mapNotNull {
                OneRMEstimationHelper.calculateOneRM(it.kg, it.reps, it.rpe)
            }.maxOrNull()
            SessionMetrics(
                date = session.date,
                workoutType = session.defaultWorkoutType,
                oneRM = oneRm,
                volume = volume,
                efficiency = null
            )
        }
        val volumeTrend = OneRMEstimationHelper.calculateTrend(metrics, "volume")
        val strengthTrend = OneRMEstimationHelper.calculateTrend(metrics, "strength")

        sb.append("\n### Trends (last ").append(minOf(6, metrics.size)).append(" sessions)\n")
        sb.append("- Volume: ").append(trendText(volumeTrend)).append("\n")
        sb.append("- Strength (est.1RM): ").append(trendText(strengthTrend)).append("\n")
        if (volumeTrend != null || strengthTrend != null) {
            sb.append("- Coach summary: ")
                .append(OneRMEstimationHelper.generateCoachReport(
                    volumeTrend, strengthTrend, null, metrics.size
                ))
                .append("\n")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Small formatting helpers
    // ---------------------------------------------------------------------------------------------

    private fun resolveExerciseIntent(
        session: TrainingSession,
        exerciseId: Int,
        workingSets: List<ExerciseEntry>
    ): SetIntent {
        return if (session.isLegacySession()) {
            session.getLegacyExerciseIntent(exerciseId)
        } else {
            workingSets.firstOrNull()?.explicitIntent ?: SetIntent.BUILD
        }
    }

    private fun exerciseMeta(item: ExerciseLibraryItem?): String {
        if (item == null) return ""
        val parts = mutableListOf<String>()
        item.region?.let { parts.add(it.displayName) }
        item.pattern?.let { parts.add(it.displayName) }
        item.tier?.let { parts.add(it.displayName) }
        parts.add(item.mechanics.displayName)
        if (item.isBodyweight) parts.add("Bodyweight")
        val primary = item.primaryTargets.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.displayName }
        if (primary != null) parts.add("targets: $primary")
        return parts.joinToString(" · ")
    }

    private fun loadCell(e: ExerciseEntry): String = SetFormatter.loadCellPlain(e)

    private fun noteCell(note: String?): String {
        val n = note?.trim().orEmpty()
        if (n.isEmpty()) return ""
        // Keep the markdown table intact.
        return n.replace("|", "/").replace("\n", " ")
    }

    private fun scanMetrics(e: WithingsScanEntry): String {
        val parts = mutableListOf<String>()
        e.weightKg?.let { parts.add("weight ${fmtD(it)} kg") }
        e.bodyFatPct?.let { parts.add("body fat ${fmtD(it)}%") }
        e.leanBodyMassKg?.let { parts.add("lean mass ${fmtD(it)} kg") }
        e.boneMassKg?.let { parts.add("bone mass ${fmtD(it)} kg") }
        e.bodyWaterMassKg?.let { parts.add("body water ${fmtD(it)} kg") }
        e.bmrKcal?.let { parts.add("BMR ${it.toInt()} kcal") }
        return if (parts.isEmpty()) "no metrics" else parts.joinToString(", ")
    }

    private fun scanDelta(old: WithingsScanEntry, new: WithingsScanEntry): String {
        val parts = mutableListOf<String>()
        deltaD(old.weightKg, new.weightKg)?.let { parts.add("weight $it kg") }
        deltaD(old.bodyFatPct, new.bodyFatPct)?.let { parts.add("body fat $it%") }
        deltaD(old.leanBodyMassKg, new.leanBodyMassKg)?.let { parts.add("lean mass $it kg") }
        return if (parts.isEmpty()) "no comparable metrics" else parts.joinToString(", ")
    }

    /**
     * "Next session" guidance.
     *
     * [ProgressionHelper] is rep-and-absolute-kg based: for a bodyweight exercise its suggested
     * weight is total load (body weight + extra), and for a timed hold it has no meaningful input
     * at all (reps are 0). Emitting its raw output for those would feed a model advice like
     * "increase to 87.5 kg" on a pull-up, so both cases are handled explicitly here.
     */
    private fun nextSessionLine(
        exerciseId: Int,
        intent: SetIntent,
        t: com.liftpath.models.ExerciseTrendData,
        libItem: ExerciseLibraryItem?,
        trainingData: TrainingData,
        settings: ProgressionHelper.ProgressionSettings,
        incrementTable: EquipmentIncrementTable
    ): String {
        if (t.isTimedExercise || libItem?.isTimeBased == true) {
            val best = t.currentBestHoldSeconds ?: return ""
            val target = best + HOLD_PROGRESSION_STEP_SECONDS
            return "Next session: hold ${RestTimerHelper.formatDuration(target)} " +
                "(+${HOLD_PROGRESSION_STEP_SECONDS}s on ${RestTimerHelper.formatDuration(best)})\n"
        }

        val suggestion = ProgressionHelper.getIntentSuggestion(
            exerciseId, intent, trainingData, settings, incrementTable
        )
        if (suggestion.displayText.isBlank()) return ""
        val sb = StringBuilder("Next session: ").append(suggestion.displayText)
        // Only worth disambiguating when a figure is actually quoted. A bodyweight suggestion
        // carries reps and RPE but no kg, so there is no load to misread.
        if (libItem?.isBodyweight == true && suggestion.suggestedWeight != null) {
            sb.append(" (total load = body weight + added)")
        }
        suggestion.badge?.let { sb.append(" [").append(it).append("]") }
        return sb.append("\n").toString()
    }

    private fun trendLine(t: com.liftpath.models.ExerciseTrendData): String {
        // A timed exercise has no rep-based volume or 1RM; report hold time instead.
        if (t.isTimedExercise) {
            val sb = StringBuilder("Trend vs last ").append(t.intent.displayName)
                .append(" session (").append(t.intentSessionCount).append(" prior): ")
            val prev = t.previousBestHoldSeconds
            val cur = t.currentBestHoldSeconds ?: 0
            if (prev == null) {
                sb.append("building baseline (no prior same-intent session).")
            } else {
                sb.append("best hold ").append(RestTimerHelper.formatDuration(prev))
                    .append("→").append(RestTimerHelper.formatDuration(cur))
                    .append(pctText(prev.toFloat(), cur.toFloat()))
                    .append("; total ")
                    .append(RestTimerHelper.formatHoldTotal(t.currentTotalHoldSeconds))
                t.currentLoadSeconds?.let {
                    sb.append("; load-seconds ").append(fmtVol(it)).append(" kg·s")
                }
                sb.append(".")
            }
            if (t.hasNewAllTimePR) sb.append(" NEW ALL-TIME PR.")
            return sb.toString()
        }

        val sb = StringBuilder("Trend vs last ").append(t.intent.displayName)
            .append(" session (").append(t.intentSessionCount).append(" prior): ")
        if (t.previousVolume == null && t.previousEstimated1RM == null) {
            sb.append("building baseline (no prior same-intent session).")
        } else {
            val vol = "vol ${t.previousVolume?.let { fmtVol(it) } ?: "—"}→${fmtVol(t.currentVolume)}" +
                pctText(t.previousVolume, t.currentVolume)
            val rm = "1RM ${t.previousEstimated1RM?.let { fmt(it) } ?: "—"}→" +
                (t.currentEstimated1RM?.let { fmt(it) } ?: "n/a") +
                pctText(t.previousEstimated1RM, t.currentEstimated1RM)
            sb.append(vol).append("; ").append(rm).append(".")
        }
        if (t.hasNewAllTimePR) sb.append(" NEW ALL-TIME PR.")
        return sb.toString()
    }

    private fun trendText(t: TrendResult?): String {
        if (t == null) return "insufficient data for a trend (need 4+ qualifying sessions)"
        val dir = when (t.trendDirection) {
            TrendDirection.UP -> "↑ up"
            TrendDirection.DOWN -> "↓ down"
            TrendDirection.STABLE -> "→ stable"
        }
        val sign = if (t.percentageChange >= 0) "+" else ""
        return "$dir, $sign${String.format(Locale.US, "%.1f", t.percentageChange)}% over " +
            "${t.sessionCount} sessions (confidence ${String.format(Locale.US, "%.0f", t.confidence * 100)}%)"
    }

    /**
     * A PR's value with its unit. Hold PRs carry seconds, not kilograms, so they must not be
     * formatted with the shared numeric formatter.
     */
    private fun prValue(
        pr: ProgressAnalysisHelper.PRRecord,
        value: Float = pr.value
    ): String = if (pr.prType == ProgressAnalysisHelper.PRType.TIME_HOLD) {
        RestTimerHelper.formatDuration(value.toInt())
    } else {
        fmt(value)
    }

    private fun prTypeLabel(type: ProgressAnalysisHelper.PRType): String = when (type) {
        ProgressAnalysisHelper.PRType.WEIGHT -> "Weight PR"
        ProgressAnalysisHelper.PRType.VOLUME -> "Volume PR"
        ProgressAnalysisHelper.PRType.ONE_RM -> "Est. 1RM PR"
        ProgressAnalysisHelper.PRType.TIME_HOLD -> "Longest Hold PR"
        ProgressAnalysisHelper.PRType.REPS -> "Reps PR"
    }

    private fun pctText(old: Float?, new: Float?): String {
        if (old == null || new == null || old <= 0f) return ""
        val pct = (new - old) / old * 100f
        val sign = if (pct >= 0) "+" else ""
        return " ($sign${String.format(Locale.US, "%.1f", pct)}%)"
    }

    /** Signed delta of two optional doubles, formatted with a sign, or null if not comparable. */
    private fun deltaD(old: Double?, new: Double?): String? {
        if (old == null || new == null) return null
        val d = new - old
        val sign = if (d >= 0) "+" else ""
        return "$sign${fmtD(d)}"
    }

    private fun fmt(v: Float): String =
        if (v == v.toLong().toFloat()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

    private fun fmtD(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

    private fun fmtVol(v: Float): String = String.format(Locale.US, "%,d", v.toLong())
}
