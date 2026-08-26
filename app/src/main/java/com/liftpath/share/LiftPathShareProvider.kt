package com.liftpath.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.content.pm.ApplicationInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.liftpath.helpers.JsonHelper
import com.liftpath.models.ExerciseEntry
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.TrainingData
import com.liftpath.models.TrainingSession
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Read-only bridge that lets TriPath see LiftPath's set-level lifting detail.
 *
 * TriPath models training load for every discipline, but strength has always reached it as a bare
 * Health Connect session — a duration and nothing else. It cannot tell a heavy squat triple from a
 * set of lateral raises, which is exactly the difference that decides whether legs are wrecked
 * tomorrow. Everything needed is already here in RPE, tier and target muscles; this exposes those
 * and nothing else. No routes, no bodyweight history, no writes.
 *
 * **Why a package check rather than a `signature` permission:** the two apps are signed with
 * different keys (TriPath has no release signing config; LiftPath signs with its own keystore), so
 * a signature permission would simply always be denied. [assertCallerAllowed] uses
 * `callingPackage`, which the framework derives from the binder calling UID and an app therefore
 * cannot forge. This mirrors `TriPathShareProvider` in the other direction.
 */
class LiftPathShareProvider : ContentProvider() {

    private companion object {
        const val TAG = "LiftPathShareProvider"

        const val CODE_HANDSHAKE = 1
        const val CODE_SESSIONS = 2
        const val CODE_SETS = 3
        const val CODE_EXERCISES = 4

        /** Range served when the caller does not ask for one. */
        const val DEFAULT_RANGE_DAYS = 28L

        /** Hard ceiling so a malformed range cannot walk the whole history day by day. */
        const val MAX_RANGE_DAYS = 400L
    }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(LiftPathShareContract.AUTHORITY, LiftPathShareContract.PATH_HANDSHAKE, CODE_HANDSHAKE)
        addURI(LiftPathShareContract.AUTHORITY, LiftPathShareContract.PATH_SESSIONS, CODE_SESSIONS)
        addURI(LiftPathShareContract.AUTHORITY, LiftPathShareContract.PATH_SETS, CODE_SETS)
        addURI(LiftPathShareContract.AUTHORITY, LiftPathShareContract.PATH_EXERCISES, CODE_EXERCISES)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        assertCallerAllowed()
        val ctx = context ?: return null

        return try {
            val data = JsonHelper(ctx.applicationContext).readTrainingData()
            when (uriMatcher.match(uri)) {
                CODE_HANDSHAKE -> handshakeCursor(data)
                CODE_SESSIONS -> sessionsCursor(data, uri)
                CODE_SETS -> setsCursor(data, uri)
                CODE_EXERCISES -> exercisesCursor(data)
                else -> throw IllegalArgumentException("Unknown URI: $uri")
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            // A failed read must not take TriPath's sync down with it; a null cursor reads to the
            // caller as "nothing to sync" and the next sync tries again.
            Log.e(TAG, "Query failed for $uri", e)
            null
        }
    }

    override fun getType(uri: Uri): String? = when (uriMatcher.match(uri)) {
        CODE_HANDSHAKE, CODE_SESSIONS, CODE_SETS, CODE_EXERCISES ->
            "vnd.android.cursor.dir/vnd.${LiftPathShareContract.AUTHORITY}"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("LiftPath data is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("LiftPath data is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("LiftPath data is read-only")

    // ---- Cursors -----------------------------------------------------------------------------

    private fun handshakeCursor(data: TrainingData): Cursor {
        val sessions = data.trainings
        return MatrixCursor(LiftPathShareContract.Handshake.COLUMNS).apply {
            addRow(
                arrayOf(
                    LiftPathShareContract.CONTRACT_VERSION,
                    LiftPathShareContract.schemaHash(),
                    LiftPathShareContract.CAPABILITIES.joinToString(","),
                    appVersionName(),
                    sessions.size,
                    sessions.mapNotNull { it.isoDate() }.maxOrNull()
                )
            )
        }
    }

    private fun sessionsCursor(data: TrainingData, uri: Uri): Cursor {
        val (from, to) = requestedRange(uri)
        return MatrixCursor(LiftPathShareContract.Sessions.COLUMNS).apply {
            data.trainings.inRange(from, to).forEach { session ->
                addRow(
                    arrayOf(
                        session.id,
                        session.isoDate(),
                        // TrainingSession records a date and a duration but never a wall-clock
                        // start, so this is always null today. The column exists because TriPath's
                        // strain timeline is hour-resolution and will use it the moment LiftPath
                        // starts persisting the draft's start time.
                        null,
                        session.durationSeconds,
                        session.planName,
                        session.dominantIntentOrNull(),
                        session.countableSets().size
                    )
                )
            }
        }
    }

    private fun setsCursor(data: TrainingData, uri: Uri): Cursor {
        val (from, to) = requestedRange(uri)
        return MatrixCursor(LiftPathShareContract.Sets.COLUMNS).apply {
            data.trainings.inRange(from, to).forEach { session ->
                session.countableSets().forEach { entry ->
                    addRow(
                        arrayOf(
                            session.id,
                            entry.exerciseId,
                            entry.setNumber,
                            entry.kg,
                            entry.reps,
                            entry.rpe,
                            // Resolved here, not by the consumer. `isEffectivelyWarmup` carries the
                            // legacy "RPE 6 meant warm-up before explicit intent existed" rule, and
                            // that history has no business being re-implemented in another app.
                            if (entry.isEffectivelyWarmup()) 1 else 0,
                            runCatching { entry.getEffectiveIntent(session.defaultWorkoutType).name }
                                .getOrNull(),
                            entry.durationSeconds,
                            entry.bodyweightKg
                        )
                    )
                }
            }
        }
    }

    private fun exercisesCursor(data: TrainingData): Cursor =
        MatrixCursor(LiftPathShareContract.Exercises.COLUMNS).apply {
            data.exerciseLibrary.forEach { exercise ->
                addRow(
                    arrayOf(
                        exercise.id,
                        exercise.name,
                        exercise.region?.name,
                        exercise.tier?.name,
                        exercise.pattern?.name,
                        exercise.mechanics.name,
                        exercise.primaryTargets.joinToString(",") { it.name },
                        exercise.secondaryTargets.joinToString(",") { it.name }
                    )
                )
            }
        }

    // ---- Helpers -----------------------------------------------------------------------------

    /**
     * Rejects every caller but TriPath. `com.android.shell` is allowed in debug builds only, so
     * `adb shell content query` can smoke-test the provider without weakening release builds.
     */
    private fun assertCallerAllowed() {
        val caller = callingPackage
        val allowed = caller == LiftPathShareContract.CONSUMER_PACKAGE ||
            (isDebuggable() && caller == "com.android.shell")
        if (!allowed) {
            throw SecurityException("Package $caller may not read LiftPath data")
        }
    }

    /**
     * Read from the manifest rather than `BuildConfig`, which this module does not generate —
     * [com.liftpath.helpers.BackupManager] resolves the app version the same way.
     */
    private fun appVersionName(): String? = context?.let { ctx ->
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
    }

    /** The `BuildConfig.DEBUG` equivalent, for the same reason as [appVersionName]. */
    private fun isDebuggable(): Boolean {
        val ctx: Context = context ?: return false
        return (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /** Inclusive `from`/`to` from the query string, clamped and defaulted to a sane window. */
    private fun requestedRange(uri: Uri): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        val to = uri.parseDate(LiftPathShareContract.QUERY_TO) ?: today
        val from = uri.parseDate(LiftPathShareContract.QUERY_FROM) ?: to.minusDays(DEFAULT_RANGE_DAYS)
        if (from.isAfter(to)) return to to to
        val span = ChronoUnit.DAYS.between(from, to)
        return if (span > MAX_RANGE_DAYS) to.minusDays(MAX_RANGE_DAYS) to to else from to to
    }

    private fun Uri.parseDate(key: String): LocalDate? {
        val raw = getQueryParameter(key) ?: return null
        return try {
            LocalDate.parse(raw)
        } catch (e: DateTimeParseException) {
            Log.w(TAG, "Ignoring unparseable $key=$raw")
            null
        }
    }

    /** LiftPath stores session dates as `yyyy/MM/dd`; the contract speaks ISO-8601. */
    private fun TrainingSession.isoDate(): String? {
        val iso = date.replace('/', '-')
        return if (runCatching { LocalDate.parse(iso) }.isSuccess) iso else null
    }

    private fun List<TrainingSession>.inRange(from: LocalDate, to: LocalDate): List<TrainingSession> =
        mapNotNull { session ->
            val iso = session.isoDate() ?: return@mapNotNull null
            val date = LocalDate.parse(iso)
            if (date.isBefore(from) || date.isAfter(to)) null else session
        }.sortedBy { it.date }

    /**
     * Real logged sets only. The warm-up/cool-down "special element" entries use sentinel exercise
     * ids and carry no load, so shipping them would put phantom exercises in TriPath's catalog join.
     */
    private fun TrainingSession.countableSets(): List<ExerciseEntry> =
        exercises.filterNot { it.isSpecialSlotEntry() }

    /** Null rather than a guess when a session has no sets to infer an intent from. */
    private fun TrainingSession.dominantIntentOrNull(): String? =
        if (countableSets().isEmpty()) null else runCatching { getDominantIntent().name }.getOrNull()
}
