package com.lilfitness.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

// --- NEW ENUMS (The "Vocabulary" for Smart Logic) ---

// 1. User Level
enum class UserLevel(val displayName: String) {
    NOVICE("Novice (Linear Progression)"),
    INTERMEDIATE("Intermediate (Periodized)")
}

// 2. The Tier
enum class Tier(val displayName: String) {
    TIER_1("Tier 1 (Main Lift / Heavy)"),
    TIER_2("Tier 2 (Assistance / Volume)"),
    TIER_3("Tier 3 (Accessory / Isolation)")
}

// 3. Movement Patterns
enum class MovementPattern(val displayName: String) {
    SQUAT("Squat (Knee Dominant)"),
    HINGE("Hinge (Hip Dominant)"),
    LUNGE("Lunge (Single Leg)"),
    PUSH_HORIZONTAL("Horizontal Push (Chest)"),
    PUSH_VERTICAL("Vertical Push (Shoulders)"),
    PULL_HORIZONTAL("Horizontal Pull (Rows)"),
    PULL_VERTICAL("Vertical Pull (Lats)"),
    CARRY("Carry (Farmer's Walk)"),
    CORE("Core (Abs/Obliques)"),
    ISOLATION_ARMS("Arms (Bicep/Tricep)"),
    OTHER("Other")
}

// 4. Mechanics
enum class Mechanics(val displayName: String) {
    COMPOUND("Compound (Multi-joint)"),
    ISOLATION("Isolation (Single-joint)")
}

// --- UPDATED DATA MODELS ---

@Parcelize
data class ExerciseLibraryItem(
    val id: Int,
    val name: String,
    val category: String? = null,
    
    // NEW: Smart Attributes (Nullable to support old JSON data)
    val pattern: MovementPattern? = null,
    val mechanics: Mechanics? = null,
    val tier: Tier? = null
) : Parcelable

@Parcelize
data class ExerciseEntry(
    val exerciseId: Int,
    var exerciseName: String,
    val setNumber: Int,
    val kg: Float,
    val reps: Int,
    val note: String? = null,
    val rating: Int? = null,        // KEEP - legacy field
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
    
    // NEW: User Level Setting (Defaults to Novice)
    var userLevel: UserLevel = UserLevel.NOVICE 
) : Parcelable

// --- Helper Data Classes (Not persisted to JSON) ---

data class ActiveWorkoutDraft(
    val workoutType: String,
    val date: String,
    val appliedPlanId: String?,
    val appliedPlanName: String?,
    val entries: List<ExerciseEntry>
)

data class GroupedExercise(
    val exerciseId: Int,
    val exerciseName: String,
    val sets: List<ExerciseEntry>
)

data class ExerciseSet(
    val date: String,
    val setNumber: Int,
    val kg: Float,
    val reps: Int,
    val rpe: Float? = null
)