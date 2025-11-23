package com.liftpath.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.ProgressionHelper
import com.liftpath.helpers.ProgressionSettingsManager
import com.liftpath.helpers.WorkoutGenerator
import com.liftpath.models.GroupedExercise

class ActiveExercisesAdapter(
    private val groupedExercises: List<GroupedExercise>,
    private val exerciseRecommendations: Map<Int, WorkoutGenerator.RecommendedExercise>,
    private val jsonHelper: JsonHelper,
    private val workoutType: String,
    private val onAddSetClicked: (exerciseId: Int, exerciseName: String) -> Unit,
    private val onEditActivityClicked: (GroupedExercise) -> Unit,
    private val onDuplicateSetClicked: (exerciseId: Int) -> Unit,
    private val onDeleteExerciseClicked: (exerciseId: Int) -> Unit
) : RecyclerView.Adapter<ActiveExercisesAdapter.GroupedExerciseViewHolder>() {

    class GroupedExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val recommendedInfo: TextView = view.findViewById(R.id.text_recommended_info)
        val setsCount: TextView = view.findViewById(R.id.text_sets_count)
        val loggedSets: TextView = view.findViewById(R.id.text_logged_sets)
        val completionCheck: ImageView = view.findViewById(R.id.image_completion_check)
        val addSetButton: CardView = view.findViewById(R.id.button_add_set)
        val duplicateSetButton: CardView = view.findViewById(R.id.button_duplicate_set)
        val editActivityButton: CardView = view.findViewById(R.id.button_edit_activity)
        val deleteExerciseButton: CardView = view.findViewById(R.id.button_delete_exercise)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupedExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_active_exercise, parent, false)
        return GroupedExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupedExerciseViewHolder, position: Int) {
        val groupedExercise = groupedExercises[position]
        holder.exerciseName.text = groupedExercise.exerciseName

        // Check if exercise has completed sets (non-zero weight or explicitly completed)
        val completedSets = groupedExercise.sets.filter { set ->
            set.kg > 0f || set.completed == true
        }
        val hasSets = groupedExercise.sets.isNotEmpty()
        val loggedSetsCount = completedSets.size
        
        // Get recommendation to check if sets are complete
        val recommendation = exerciseRecommendations[groupedExercise.exerciseId]
        val recommendedSetsCount = recommendation?.recommendedSets
        
        // Show completion checkmark if user has logged the recommended number of sets
        val isComplete = recommendedSetsCount != null && loggedSetsCount >= recommendedSetsCount
        holder.completionCheck.visibility = if (isComplete) View.VISIBLE else View.GONE

        // Show sets count: "(x of y sets)"
        if (recommendedSetsCount != null) {
            holder.setsCount.text = "($loggedSetsCount of $recommendedSetsCount sets)"
        } else {
            // If no recommendation, just show logged count
            if (loggedSetsCount > 0) {
                holder.setsCount.text = "($loggedSetsCount sets)"
            } else {
                holder.setsCount.text = ""
            }
        }

        // Show recommended kg × reps (only when no sets logged)
        val recommendedText = getRecommendedText(holder.itemView.context, groupedExercise.exerciseId)
        if (hasSets) {
            // Hide recommended info when sets are logged
            holder.recommendedInfo.visibility = View.GONE
        } else {
            // Show recommended info when no sets logged
            if (recommendedText.isNotEmpty()) {
                holder.recommendedInfo.text = recommendedText
                holder.recommendedInfo.visibility = View.VISIBLE
            } else {
                holder.recommendedInfo.visibility = View.GONE
            }
        }

        // Show actual logged sets
        if (hasSets && completedSets.isNotEmpty()) {
            val setsText = completedSets.sortedBy { it.setNumber }.joinToString("\n") { set ->
                // Clean formatting: remove decimal if whole number (e.g., 50.0 -> 50)
                val weightString = if (set.kg % 1 == 0f) {
                    set.kg.toInt().toString()
                } else {
                    set.kg.toString()
                }
                "Set ${set.setNumber}: ${weightString}kg × ${set.reps}"
            }
            holder.loggedSets.text = setsText
            holder.loggedSets.visibility = View.VISIBLE
        } else {
            holder.loggedSets.visibility = View.GONE
        }

        // Visibility logic
        holder.duplicateSetButton.visibility = if (hasSets) View.VISIBLE else View.GONE
        holder.editActivityButton.visibility = if (hasSets) View.VISIBLE else View.GONE
        holder.deleteExerciseButton.visibility = if (hasSets) View.VISIBLE else View.GONE

        // --- CLICK LISTENERS ---

        // 1. Add Set (Plus button)
        holder.addSetButton.setOnClickListener {
            onAddSetClicked(groupedExercise.exerciseId, groupedExercise.exerciseName)
        }

        // 2. Duplicate Last Set
        holder.duplicateSetButton.setOnClickListener {
            onDuplicateSetClicked(groupedExercise.exerciseId)
        }

        // 3. Edit (Pencil button)
        holder.editActivityButton.setOnClickListener {
            onEditActivityClicked(groupedExercise)
        }

        // 4. Delete Exercise (Trash button)
        holder.deleteExerciseButton.setOnClickListener {
            onDeleteExerciseClicked(groupedExercise.exerciseId)
        }
        
        // 5. Card Body Click -> Trigger Edit
        // This is a UX improvement: tapping the text allows editing the pre-filled targets
        holder.itemView.setOnClickListener {
            if (hasSets) {
                onEditActivityClicked(groupedExercise)
            } else {
                onAddSetClicked(groupedExercise.exerciseId, groupedExercise.exerciseName)
            }
        }
    }

    override fun getItemCount() = groupedExercises.size

    private fun getRecommendedText(context: Context, exerciseId: Int): String {
        val recommendation = exerciseRecommendations[exerciseId]
        
        if (recommendation == null) {
            // Try to get from ProgressionHelper
            val trainingData = jsonHelper.readTrainingData()
            val settings = try {
                ProgressionSettingsManager(context).getSettings()
            } catch (e: Exception) {
                ProgressionHelper.ProgressionSettings(userLevel = trainingData.userLevel)
            }
            
            val suggestion = ProgressionHelper.getSuggestion(
                exerciseId = exerciseId,
                requestedType = workoutType,
                trainingData = trainingData,
                settings = settings
            )
            
            val parts = mutableListOf<String>()
            
            // Weight suggestion
            if (suggestion.proposedWeight != null) {
                val weightString = if (suggestion.proposedWeight % 1 == 0f) {
                    suggestion.proposedWeight.toInt().toString()
                } else {
                    suggestion.proposedWeight.toString()
                }
                parts.add("${weightString}kg")
            }
            
            // Reps suggestion
            suggestion.proposedReps?.let {
                parts.add("× $it reps")
            }
            
            return if (parts.isNotEmpty()) parts.joinToString(" ") else ""
        }

        // Get weight suggestion from ProgressionHelper
        val trainingData = jsonHelper.readTrainingData()
        val settings = try {
            ProgressionSettingsManager(context).getSettings()
        } catch (e: Exception) {
            ProgressionHelper.ProgressionSettings(userLevel = trainingData.userLevel)
        }
        
        val suggestion = ProgressionHelper.getSuggestion(
            exerciseId = exerciseId,
            requestedType = recommendation.workoutType,
            trainingData = trainingData,
            settings = settings
        )

        val parts = mutableListOf<String>()
        
        // Weight suggestion
        if (suggestion.proposedWeight != null) {
            val weightString = if (suggestion.proposedWeight % 1 == 0f) {
                suggestion.proposedWeight.toInt().toString()
            } else {
                suggestion.proposedWeight.toString()
            }
            parts.add("${weightString}kg")
        } else {
            return "" // Don't show if no weight suggestion
        }
        
        // Reps suggestion
        val suggestedReps = suggestion.proposedReps ?: recommendation.recommendedReps
        parts.add("× $suggestedReps reps")
        
        return parts.joinToString(" ")
    }

    private fun getSuggestionText(context: Context, exerciseId: Int, exerciseName: String): String {
        val recommendation = exerciseRecommendations[exerciseId]
        
        if (recommendation == null) {
            // No recommendation from blueprint - try to get from ProgressionHelper
            return getProgressionSuggestionText(context, exerciseId, exerciseName)
        }

        // Get weight suggestion from ProgressionHelper
        val trainingData = jsonHelper.readTrainingData()
        val settings = try {
            ProgressionSettingsManager(context).getSettings()
        } catch (e: Exception) {
            ProgressionHelper.ProgressionSettings(userLevel = trainingData.userLevel) // Use defaults
        }
        
        val suggestion = ProgressionHelper.getSuggestion(
            exerciseId = exerciseId,
            requestedType = recommendation.workoutType,
            trainingData = trainingData,
            settings = settings
        )

        val nextSetNumber = 1 // First set
        
        val suggestionParts = mutableListOf<String>()
        
        // Show recommended sets count
        suggestionParts.add("Recommended: ${recommendation.recommendedSets} sets")
        suggestionParts.add("\nSet $nextSetNumber:")
        
        // Weight suggestion
        if (suggestion.proposedWeight != null) {
            val weightString = if (suggestion.proposedWeight % 1 == 0f) {
                suggestion.proposedWeight.toInt().toString()
            } else {
                suggestion.proposedWeight.toString()
            }
            suggestionParts.add("${weightString}kg")
        } else {
            suggestionParts.add("kg (not estimated)")
        }
        
        // Reps suggestion
        val suggestedReps = suggestion.proposedReps ?: recommendation.recommendedReps
        suggestionParts.add("× $suggestedReps reps")
        
        // Add badge if available
        suggestion.badge?.let {
            suggestionParts.add("($it)")
        }
        
        return suggestionParts.joinToString(" ")
    }

    private fun getProgressionSuggestionText(context: Context, exerciseId: Int, exerciseName: String): String {
        val trainingData = jsonHelper.readTrainingData()
        val settings = try {
            ProgressionSettingsManager(context).getSettings()
        } catch (e: Exception) {
            ProgressionHelper.ProgressionSettings(userLevel = trainingData.userLevel) // Use defaults
        }
        
        val suggestion = ProgressionHelper.getSuggestion(
            exerciseId = exerciseId,
            requestedType = workoutType,
            trainingData = trainingData,
            settings = settings
        )

        val nextSetNumber = 1
        
        val suggestionParts = mutableListOf<String>()
        suggestionParts.add("Set $nextSetNumber:")
        
        // Weight suggestion
        if (suggestion.proposedWeight != null) {
            val weightString = if (suggestion.proposedWeight % 1 == 0f) {
                suggestion.proposedWeight.toInt().toString()
            } else {
                suggestion.proposedWeight.toString()
            }
            suggestionParts.add("${weightString}kg")
        } else {
            suggestionParts.add("kg (not estimated)")
        }
        
        // Reps suggestion
        val suggestedReps = suggestion.proposedReps
        if (suggestedReps != null) {
            suggestionParts.add("× $suggestedReps reps")
        }
        
        // Add badge if available
        suggestion.badge?.let {
            suggestionParts.add("($it)")
        }
        
        return if (suggestionParts.size > 1) {
            suggestionParts.joinToString(" ")
        } else {
            "Tap to add set"
        }
    }
}