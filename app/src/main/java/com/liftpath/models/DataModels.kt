package com.liftpath.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.google.gson.annotations.SerializedName
import java.util.UUID

// --- ENUMS ---

enum class UserLevel(val displayName: String) {
    NOVICE("Novice (Linear Progression)"),
    INTERMEDIATE("Intermediate (Periodized)")
}

enum class Tier(val displayName: String) {
    TIER_1("Tier 1 (Main Lift / Heavy)"),
    TIER_2("Tier 2 (Assistance / Volume)"),
    TIER_3("Tier 3 (Accessory / Isolation)")
}

enum class BodyRegion(val displayName: String) {
    UPPER("Upper Body"),
    LOWER("Lower Body"),
    CORE("Core"),
    FULL("Full Body")
}

// MOVED FROM WORKOUT GENERATOR SO ALL ACTIVITIES CAN SEE THEM
enum class SessionFocus(val displayName: String) {
    UPPER("Upper Body Focus"),
    LOWER("Lower Body Focus"),
    FULL("Full Body")
}

enum class SessionIntensity { HEAVY, LIGHT }

enum class SetIntent(val value: String, val displayName: String) {
    @SerializedName("strength") STRENGTH("strength", "Strength"),
    @SerializedName("build") BUILD("build", "Build"),
    @SerializedName("flush") FLUSH("flush", "Flush"),
    @SerializedName("warmup") WARMUP("warmup", "Warmup"),
    @SerializedName("unknown") UNKNOWN("unknown", "Unknown")
}

enum class TargetMuscle(val displayName: String) {
    CHEST_UPPER("Upper Chest"), CHEST_MIDDLE("Middle Chest"), CHEST_LOWER("Lower Chest"),
    LATS("Lats"), TRAPS_MID("Mid-Back"), TRAPS_UPPER("Upper Traps"), LOWER_BACK("Lower Back"),
    DELT_FRONT("Front Delts"), DELT_SIDE("Side Delts"), DELT_REAR("Rear Delts"),
    BICEPS("Biceps"), TRICEPS_LONG("Triceps (Long)"), TRICEPS_LATERAL("Triceps (Lat)"), FOREARMS("Forearms"),
    QUADS("Quads"), HAMSTRINGS("Hamstrings"), GLUTES("Glutes"), CALVES("Calves"), TIBIALIS("Tibialis"), ADDUCTORS("Adductors"), ABDUCTORS("Abductors"), HIPFLEXORS("Hipflexors"),
    ABS("Abs"), OBLIQUES("Obliques")
}

enum class MovementPattern(val displayName: String) {
    SQUAT("Squat"), HINGE("Hinge"), LUNGE("Lunge"), 
    PUSH_HORIZONTAL("Horizontal Push"), PUSH_VERTICAL("Vertical Push"),
    PULL_HORIZONTAL("Horizontal Pull"), PULL_VERTICAL("Vertical Pull"),
    CARRY("Carry"),
    ISOLATION_ELBOW_FLEXION("Curl"), ISOLATION_ELBOW_EXTENSION("Extension"),
    ISOLATION_SHOULDER_ABDUCTION("Lat Raise"), ISOLATION_SHOULDER_FLEXION("Front Raise"), 
    ISOLATION_SHOULDER_EXTENSION("Rear Fly"),
    ISOLATION_KNEE_FLEXION("Leg Curl"), ISOLATION_KNEE_EXTENSION("Leg Ext"), 
    ISOLATION_PLANTAR_FLEXION("Calf Raise"),
    CORE_FLEXION("Crunch"), CORE_STABILITY("Plank"), OTHER("Other"),
    // LEGACY
    ISOLATION_ARMS("Arms"), CORE("Core")
}

enum class Mechanics(val displayName: String) {
    COMPOUND("Compound"), ISOLATION("Isolation")
}

enum class ExerciseType(val displayName: String) {
    @SerializedName("weighted")   WEIGHTED("Weighted"),    // default / legacy
    @SerializedName("bodyweight") BODYWEIGHT("Bodyweight")
}

/** Group types for linked exercises (e.g. superset, circuit). Used for analytics. */
object GroupType {
    const val SUPERSET = "SUPERSET"
    const val CIRCUIT = "CIRCUIT"
}

enum class WorkoutSourceType {
    @SerializedName("manual") MANUAL,
    @SerializedName("plan") PLAN,
    @SerializedName("plan_set") PLAN_SET
}

enum class PlanExerciseSelectionType {
    @SerializedName("specific_variant") SPECIFIC_VARIANT,
    @SerializedName("family_slot")      FAMILY_SLOT
}

enum class Equipment(val displayName: String) {
    @SerializedName("barbell")     BARBELL("Barbell"),
    @SerializedName("dumbbell")    DUMBBELL("Dumbbell"),
    @SerializedName("cable")       CABLE("Cable"),
    @SerializedName("machine")     MACHINE("Machine"),
    @SerializedName("bodyweight")  BODYWEIGHT("Bodyweight"),
    @SerializedName("kettlebell")  KETTLEBELL("Kettlebell"),
    @SerializedName("ez_bar")      EZ_BAR("EZ Bar"),
    @SerializedName("bands")       BANDS("Bands"),
    @SerializedName("smith")       SMITH_MACHINE("Smith Machine"),
    @SerializedName("other")       OTHER("Other")
}

enum class ExerciseAngle {
    @SerializedName("flat")     FLAT,
    @SerializedName("incline")  INCLINE,
    @SerializedName("decline")  DECLINE
}

enum class GripType {
    @SerializedName("overhand")   OVERHAND,
    @SerializedName("underhand")  UNDERHAND,
    @SerializedName("neutral")    NEUTRAL,
    @SerializedName("wide")       WIDE,
    @SerializedName("close")      CLOSE
}

enum class Laterality {
    @SerializedName("bilateral")   BILATERAL,
    @SerializedName("unilateral")  UNILATERAL
}

// --- DATA CLASSES ---

@Parcelize
data class ExerciseFamily(
    val id: String,
    val name: String,
    val movementPattern: MovementPattern? = null,
    val bodyRegion: BodyRegion? = null,
    val primaryTargets: List<TargetMuscle>? = null,
    val secondaryTargets: List<TargetMuscle>? = null,
    val aliases: List<String>? = null,
    val notes: String? = null
) : Parcelable

/**
 * One exercise slot in a WorkoutPlan. In V2 every slot is SPECIFIC_VARIANT with a non-null
 * exerciseId. The V3 family-slot fields (selectionType = FAMILY_SLOT, familyId, movementPattern)
 * are reserved and not exposed in the UI yet.
 *
 * Gson safety: all new fields are nullable so old JSON (which lacks them) deserialises cleanly.
 * Use [effectiveSelectionType] rather than [selectionType] directly to get the V2 default.
 */
@Parcelize
data class PlanExerciseSlot(
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: Int? = null,                        // non-null for SPECIFIC_VARIANT
    val defaultIntent: SetIntent? = null,
    val restTimeSeconds: Int? = null,
    val rpeTarget: Float? = null,
    val setsTarget: Int? = null,
    val repsTarget: String? = null,
    val notes: String? = null,
    // V3 reserved ─ not used in V2 UI
    val selectionType: PlanExerciseSelectionType? = null,
    val familyId: String? = null,
    val movementPattern: MovementPattern? = null
) : Parcelable {
    val effectiveSelectionType: PlanExerciseSelectionType
        get() = selectionType ?: PlanExerciseSelectionType.SPECIFIC_VARIANT
}

@Parcelize
data class WorkoutSource(
    val type: WorkoutSourceType,
    val planId: String? = null,
    val planName: String? = null,
    val planSetId: String? = null,
    val planSetName: String? = null
) : Parcelable

@Parcelize
data class PlanSet(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val planIds: MutableList<String> = mutableListOf(),
    val notes: String? = null,
    val createdDate: String = ""
) : Parcelable

@Parcelize
data class PlanSetProgress(
    val planSetId: String,
    val lastCompletedPlanId: String? = null,
    val lastCompletedAt: Long? = null
) : Parcelable

@Parcelize
data class ExerciseLibraryItem(
    val id: Int,
    val name: String,
    val category: String? = null, // Legacy
    
    val region: BodyRegion? = null,
    val pattern: MovementPattern? = null,
    val tier: Tier? = null,
    
    val primaryTargets: List<TargetMuscle> = emptyList(),
    val secondaryTargets: List<TargetMuscle> = emptyList(),
    
    // Renamed from 'mechanics' to 'manualMechanics' to avoid conflict with computed property
    val manualMechanics: Mechanics? = null,
    
    val isFavorite: Boolean = false,
    val note: String? = null,

    val exerciseType: ExerciseType? = null,  // null == WEIGHTED (legacy)

    var familyId: String? = null,
    var equipment: Equipment? = null,
    val angle: ExerciseAngle? = null,
    val grip: GripType? = null,
    val laterality: Laterality? = null,
    val aliases: List<String>? = null
) : Parcelable {
    val mechanics: Mechanics
        get() {
            if (manualMechanics != null) return manualMechanics
            if (secondaryTargets.isNotEmpty()) return Mechanics.COMPOUND
            if (primaryTargets.size > 1) return Mechanics.COMPOUND
            return Mechanics.ISOLATION
        }

    val effectiveType: ExerciseType get() = exerciseType ?: ExerciseType.WEIGHTED
    val isBodyweight: Boolean get() = effectiveType == ExerciseType.BODYWEIGHT
}

@Parcelize
data class ExerciseEntry(
    val exerciseId: Int,
    var exerciseName: String,
    val setNumber: Int,
    val kg: Float,
    val reps: Int,
    val note: String? = null,
    val rating: Int? = null,
    val workoutType: String? = null,
    val rpe: Float? = null,
    val completed: Boolean? = null,
    val isWarmup: Boolean = false,
    val explicitIntent: SetIntent? = null,
    val groupId: String? = null,
    val groupType: String? = null,

    // Bodyweight set support. Both null for a normal (weighted) set, in which case `kg`
    // holds the external load as before. For a bodyweight set, `bodyweightKg` is the body
    // weight snapshot at log time, `addedKg` is the signed extra (+added / -assistance),
    // and `kg` is written as (bodyweightKg + addedKg) so all existing readers stay coherent.
    val bodyweightKg: Float? = null,
    val addedKg: Float? = null,
    val familyIdSnapshot: String? = null
) : Parcelable {
    /** True if this set was logged as a bodyweight set (carries a body-weight snapshot). */
    fun isBodyweightEntry(): Boolean = bodyweightKg != null

    /**
     * True only for old/pre-migration data: RPE 6 was used to denote warmup before we had
     * explicit intent. For new data (explicitIntent set), RPE 6 is valid and NOT warmup.
     */
    fun isLegacyWarmup(): Boolean = explicitIntent == null && rpe == 6.0f

    /**
     * True if this set is warmup: either via isWarmup flag (new) or legacy RPE 6 convention (old).
     */
    fun isEffectivelyWarmup(): Boolean = isWarmup || isLegacyWarmup()

    fun getEffectiveIntent(parentSessionType: String?): SetIntent {
        // Priority 1: RPE 6.0 indicates warmup for legacy data only
        if (isLegacyWarmup()) {
            return SetIntent.WARMUP
        }
        
        // Priority 2: isWarmup flag
        if (isWarmup) return SetIntent.WARMUP
        
        // Priority 3: Explicit intent (modern data)
        if (explicitIntent != null) return explicitIntent
        
        // Priority 4: Legacy inference based on reps (fallback, overridden by session-level logic)
        return when (parentSessionType?.lowercase()) {
            "heavy" -> if (reps <= 7) SetIntent.STRENGTH else SetIntent.BUILD
            "light" -> if (reps >= 15) SetIntent.FLUSH else SetIntent.BUILD
            else -> when {
                reps <= 6 -> SetIntent.STRENGTH
                reps <= 15 -> SetIntent.BUILD
                else -> SetIntent.FLUSH
            }
        }
    }
}

@Parcelize
data class TrainingSession(
    val id: String = UUID.randomUUID().toString(),
    val trainingNumber: Int,
    val date: String,
    val exercises: MutableList<ExerciseEntry>,
    val defaultWorkoutType: String? = null,
    val planId: String? = null,
    val planName: String? = null,
    val durationSeconds: Long? = null
) : Parcelable {
    fun getDominantIntent(): SetIntent {
        val intentCounts = exercises
            .filterNot { it.isWarmup }
            .groupingBy { it.getEffectiveIntent(defaultWorkoutType) }
            .eachCount()
        return intentCounts.maxByOrNull { it.value }?.key ?: SetIntent.BUILD
    }
    
    fun isLegacySession(): Boolean {
        return exercises.all { it.explicitIntent == null }
    }
    
    /**
     * For legacy sessions, evaluates the intent of an exercise based on the rep patterns
     * of its non-warmup sets.
     */
    fun getLegacyExerciseIntent(exerciseId: Int): SetIntent {
        val exerciseSets = exercises
            .filter { it.exerciseId == exerciseId }
            .filterNot { it.isEffectivelyWarmup() } // Exclude warmups (isWarmup flag or legacy RPE 6)
        
        if (exerciseSets.isEmpty()) return SetIntent.BUILD
        
        // Count sets by rep ranges based on workout type
        val workoutTypeLower = defaultWorkoutType?.lowercase()
        
        return when (workoutTypeLower) {
            "heavy" -> {
                // Heavy: STRENGTH (≤7 reps) vs BUILD (8+ reps)
                val strengthCount = exerciseSets.count { it.reps <= 7 }
                val buildCount = exerciseSets.count { it.reps >= 8 }
                if (strengthCount >= buildCount) SetIntent.STRENGTH else SetIntent.BUILD
            }
            "light" -> {
                // Light: BUILD (8-15 reps) vs FLUSH (16+ reps)
                val buildCount = exerciseSets.count { it.reps in 8..15 }
                val flushCount = exerciseSets.count { it.reps >= 16 }
                if (flushCount > buildCount) SetIntent.FLUSH else SetIntent.BUILD
            }
            else -> {
                // Custom/unknown: Full spectrum
                val strengthCount = exerciseSets.count { it.reps <= 6 }
                val buildCount = exerciseSets.count { it.reps in 7..15 }
                val flushCount = exerciseSets.count { it.reps >= 16 }
                
                when {
                    strengthCount >= buildCount && strengthCount >= flushCount -> SetIntent.STRENGTH
                    flushCount > buildCount && flushCount > strengthCount -> SetIntent.FLUSH
                    else -> SetIntent.BUILD
                }
            }
        }
    }
}

@Parcelize
data class WorkoutPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val exerciseIds: MutableList<Int>,
    val workoutType: String,  // KEEP for legacy compatibility (hidden in UI)
    val exerciseConfigs: List<PlanExerciseSlot>? = null,
    val notes: String? = null,
    val createdDate: String,
    val planSchemaVersion: Int = 2
) : Parcelable

@Parcelize
data class TrainingData(
    val exerciseLibrary: MutableList<ExerciseLibraryItem> = mutableListOf(),
    val trainings: MutableList<TrainingSession> = mutableListOf(),
    val workoutPlans: MutableList<WorkoutPlan> = mutableListOf(),
    var userLevel: UserLevel = UserLevel.NOVICE,
    var planSets: MutableList<PlanSet> = mutableListOf(),
    var planSetProgress: MutableList<PlanSetProgress> = mutableListOf(),
    val schemaVersion: Int = 2,
    var exerciseFamilies: MutableList<ExerciseFamily>? = null
) : Parcelable

// Helper Classes (SupersetPair for Gson-safe draft serialization)
data class SupersetPair(val exerciseId1: Int, val exerciseId2: Int)

/** One row in the active workout list (including exercises with no logged sets yet). */
data class DraftExerciseRow(
    val exerciseId: Int,
    val exerciseName: String,
    val supersetGroupId: String? = null,
    val groupType: String? = null,
    val workoutType: String? = null,
    val explicitIntent: SetIntent? = null,
    // Plan snapshot fields — copied from PlanExerciseSlot at apply time so the
    // draft is stable even if the plan is later edited.
    val fromPlan: Boolean = false,
    val sourcePlanConfigId: String? = null,
    val plannedIntent: SetIntent? = null,
    val plannedRestTimeSeconds: Int? = null,
    val plannedRpeTarget: Float? = null,
    val plannedSetsTarget: Int? = null,
    val plannedRepsTarget: String? = null,
    val plannedNotes: String? = null
)

data class ActiveWorkoutDraft(
    val workoutType: String,
    val date: String,
    val appliedPlanId: String?,
    val appliedPlanName: String?,
    val entries: List<ExerciseEntry>,
    val startTimeMillis: Long? = null,
    val supersetPairs: List<SupersetPair>? = null,
    /** Target sets per exercise for each superset group; one entry per group (chains of consecutive pairs count as one group). */
    val supersetTargetSets: List<Int>? = null,
    /** Ordered workout rows (includes empty exercises); null in legacy drafts. */
    val exerciseOrder: List<DraftExerciseRow>? = null,
    /** Source of this workout (manual, single plan, or plan set rotation). */
    val workoutSource: WorkoutSource? = null
)

// --- WITHINGS BODY SCAN ---

/** One body-composition snapshot imported from Withings via Health Connect. */
data class WithingsScanEntry(
    val dateMs: Long,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val leanBodyMassKg: Double? = null,
    val boneMassKg: Double? = null,
    val bodyWaterMassKg: Double? = null,
    val bmrKcal: Double? = null
)

/** Root object persisted to withings_body_data.json */
data class WithingsStorage(
    var lastSyncTime: Long = 0L,
    val entries: MutableList<WithingsScanEntry> = mutableListOf(),
    /** Per-day keys (yyyy-MM-dd, local time) the user chose to ignore; excluded on every sync. */
    val ignoredDayKeys: MutableSet<String> = mutableSetOf()
)

data class GroupedExercise(
    val exerciseId: Int,
    val exerciseName: String,
    val sets: List<ExerciseEntry>,
    val supersetGroupId: String? = null,
    val groupType: String? = null
)

data class ExerciseSet(
    val date: String,
    val setNumber: Int,
    val kg: Float,
    val reps: Int,
    val rpe: Float? = null
    // TODO(bodyweight results model): carry bodyweightKg/addedKg here when bodyweight load
    //  needs to influence volume/1RM/PR/progression differently from effective load.
)

// Workout Report Data Classes
data class WorkoutSummary(
    val totalVolume: Float,
    val totalSets: Int,
    val totalReps: Int,
    val exerciseCount: Int,
    val durationSeconds: Long?,
    val prCount: Int
)

data class ExerciseTrendData(
    val exerciseId: Int,
    val exerciseName: String,
    val intent: SetIntent,
    val currentVolume: Float,
    val previousVolume: Float?,       // Most recent prior same-intent session volume
    val currentEstimated1RM: Float?,
    val previousEstimated1RM: Float?,
    val currentTopSet: Pair<Float, Int>?,   // kg, reps
    val previousTopSet: Pair<Float, Int>?,
    val hasNewAllTimePR: Boolean,           // True when a canonical all-time PR was set this session
    val intentSessionCount: Int,            // Number of prior same-intent sessions found (trend confidence)
    val prWeight: Float?,   // All-time best weight for this exercise
    val prWeightDate: Long, // Timestamp of weight PR
    val prVolume: Float?,   // All-time best volume for this exercise
    val prVolumeDate: Long, // Timestamp of volume PR
    val pr1RM: Float?,      // All-time best estimated 1RM for this exercise
    val pr1RMDate: Long     // Timestamp of 1RM PR
)

data class MuscleGroupTrend(
    val muscleGroup: String,  // "Chest", "Back", etc.
    val currentVolume: Float,
    val previousVolume: Float?,
    val changePercent: Float?
)

// For the muscle figure coloring (per individual TargetMuscle)
data class MuscleProgress(
    val muscle: TargetMuscle,
    val wasWorked: Boolean,
    val currentVolume: Float,
    val previousVolume: Float?,
    val changePercent: Float?  // null if first time working this muscle
)