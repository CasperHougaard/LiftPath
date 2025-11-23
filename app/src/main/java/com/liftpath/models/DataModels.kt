package com.liftpath.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
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

// --- DATA CLASSES ---

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
    val manualMechanics: Mechanics? = null
) : Parcelable {
    val mechanics: Mechanics
        get() {
            if (manualMechanics != null) return manualMechanics
            if (secondaryTargets.isNotEmpty()) return Mechanics.COMPOUND
            if (primaryTargets.size > 1) return Mechanics.COMPOUND
            return Mechanics.ISOLATION
        }
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
    val completed: Boolean? = null
) : Parcelable

@Parcelize
data class TrainingSession(
    val id: String = UUID.randomUUID().toString(),
    val trainingNumber: Int,
    val date: String,
    val exercises: MutableList<ExerciseEntry>,
    val defaultWorkoutType: String? = null,
    val planId: String? = null,
    val planName: String? = null
) : Parcelable

@Parcelize
data class WorkoutPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val exerciseIds: MutableList<Int>,
    val workoutType: String,
    val notes: String? = null,
    val createdDate: String
) : Parcelable

@Parcelize
data class TrainingData(
    val exerciseLibrary: MutableList<ExerciseLibraryItem> = mutableListOf(),
    val trainings: MutableList<TrainingSession> = mutableListOf(),
    val workoutPlans: MutableList<WorkoutPlan> = mutableListOf(),
    var userLevel: UserLevel = UserLevel.NOVICE
) : Parcelable

// Helper Classes
data class ActiveWorkoutDraft(
    val workoutType: String,
    val date: String,
    val appliedPlanId: String?,
    val appliedPlanName: String?,
    val entries: List<ExerciseEntry>
)

data class GroupedExercise(val exerciseId: Int, val exerciseName: String, val sets: List<ExerciseEntry>)

data class ExerciseSet(
    val date: String,
    val setNumber: Int,
    val kg: Float,
    val reps: Int,
    val rpe: Float? = null
)