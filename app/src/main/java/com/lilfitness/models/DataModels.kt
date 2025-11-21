package com.lilfitness.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

// --- NEW ENUMS (The "Vocabulary" for Smart Logic) ---

enum class UserLevel {
    NOVICE,       // Linear Progression (Default)
    INTERMEDIATE  // Periodized (Heavy/Light rotation)
}

enum class Tier {
    TIER_1, // Main Lifts (Squat, Bench, Deadlift)
    TIER_2, // Assistance (Leg Press, Incline Bench)
    TIER_3  // Accessory (Curls, Abs)
}

enum class MovementPattern {
    SQUAT,
    HINGE,
    LUNGE,
    PUSH_HORIZONTAL,
    PUSH_VERTICAL,
    PULL_HORIZONTAL,
    PULL_VERTICAL,
    CARRY,
    CORE,
    ISOLATION_ARMS,
    OTHER
}

enum class Mechanics {
    COMPOUND,
    ISOLATION
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