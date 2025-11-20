package com.lilfitness.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class ExerciseLibraryItem(
    val id: Int,
    val name: String,
    val category: String? = null
) : Parcelable

@Parcelize
data class ExerciseEntry(
    val exerciseId: Int,
    var exerciseName: String,
    val setNumber: Int,
    val kg: Float,
    val reps: Int,
    val note: String? = null,
    val rating: Int? = null,        // KEEP - legacy field, ignore going forward
    val workoutType: String? = null,
    val rpe: Float? = null,         // NEW - Rate of Perceived Exertion (6-10)
    val completed: Boolean? = null  // NEW - Did user complete all prescribed sets?
) : Parcelable

@Parcelize
data class TrainingSession(
    val id: String = UUID.randomUUID().toString(), // GUID
    val trainingNumber: Int,
    val date: String, // yyyy/mm/dd
    val exercises: MutableList<ExerciseEntry>,
    val defaultWorkoutType: String? = null,
    val planId: String? = null,  // NEW - ID of workout plan used (if any)
    val planName: String? = null // NEW - Name of workout plan used (if any)
) : Parcelable

@Parcelize
data class WorkoutPlan(
    val id: String = UUID.randomUUID().toString(), // GUID
    val name: String,
    val exerciseIds: MutableList<Int>, // List of exercise IDs in this plan
    val workoutType: String, // "heavy", "light", or "custom"
    val notes: String? = null,
    val createdDate: String // yyyy/mm/dd
) : Parcelable

@Parcelize
data class TrainingData(
    val exerciseLibrary: MutableList<ExerciseLibraryItem> = mutableListOf(),
    val trainings: MutableList<TrainingSession> = mutableListOf(),
    val workoutPlans: MutableList<WorkoutPlan> = mutableListOf() // NEW - List of workout plans
) : Parcelable

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

