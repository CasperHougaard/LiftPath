package com.liftpath.adapters

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.liftpath.R
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.ProgressionHelper
import com.liftpath.helpers.ProgressionSettingsManager
import com.liftpath.helpers.WorkoutGenerator
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.GroupedExercise
import com.liftpath.models.SetIntent
import com.google.android.material.chip.ChipGroup

class ActiveExercisesAdapter(
    private val groupedExercises: List<GroupedExercise>,
    private val exerciseRecommendations: Map<Int, WorkoutGenerator.RecommendedExercise>,
    private val jsonHelper: JsonHelper,
    private val workoutType: String,
    private val lastSetsCount: Map<Int, Int>,
    private val lastLoggedKg: Map<Int, Float>,
    private val lastLoggedReps: Map<Int, Int>,
    private val exerciseIntents: MutableMap<Int, SetIntent>,
    private val lockedIntents: MutableMap<Int, SetIntent>,
    private val lastWorkoutData: Map<Int, Map<SetIntent, List<com.liftpath.models.ExerciseEntry>>>,
    private val lastIntents: Map<Int, SetIntent>,
    private val onAddSetClicked: (exerciseId: Int, exerciseName: String) -> Unit,
    private val onEditActivityClicked: (GroupedExercise) -> Unit,
    private val onDuplicateSetClicked: (exerciseId: Int) -> Unit,
    private val onDeleteExerciseClicked: (exerciseId: Int) -> Unit,
    private val onIntentChanged: (exerciseId: Int, intent: SetIntent) -> Unit,
    private val onAddExerciseClicked: () -> Unit,
    private val onAddSpecialClicked: () -> Unit,
    private val isRestTimerRunning: () -> Boolean,
    private val onUnlinkSuperset: (supersetGroupId: String) -> Unit,
    private val selectedForSupersetPositions: () -> Set<Int>,
    private val onExerciseLongPress: (position: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val VIEW_TYPE_EXERCISE = 0
        private const val VIEW_TYPE_ADD_BUTTONS = 1
    }
    

    class GroupedExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val recommendedInfo: TextView = view.findViewById(R.id.text_recommended_info)
        val setsCount: TextView = view.findViewById(R.id.text_sets_count)
        val loggedSets: TextView = view.findViewById(R.id.text_logged_sets)
        val lastWorkoutSets: TextView = view.findViewById(R.id.text_last_workout_sets)
        val completionCheck: ImageView = view.findViewById(R.id.image_completion_check)
        val addSetButton: CardView = view.findViewById(R.id.button_add_set)
        val duplicateSetButton: CardView = view.findViewById(R.id.button_duplicate_set)
        val editActivityButton: CardView = view.findViewById(R.id.button_edit_activity)
        val deleteExerciseButton: CardView = view.findViewById(R.id.button_delete_exercise)
        val chipGroupIntent: ChipGroup = view.findViewById(R.id.chip_group_intent)
        val chipStrength: com.google.android.material.chip.Chip = view.findViewById(R.id.chip_strength)
        val chipBuild: com.google.android.material.chip.Chip = view.findViewById(R.id.chip_build)
        val chipFlush: com.google.android.material.chip.Chip = view.findViewById(R.id.chip_flush)
        val noteTooltipButton: android.widget.ImageButton = view.findViewById(R.id.button_note_tooltip)
        val supersetLinkButton: android.widget.ImageButton = view.findViewById(R.id.button_superset_link)
        val cardExercise: MaterialCardView = view.findViewById(R.id.card_exercise)
    }
    
    class AddButtonsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val regularPlusButton: CardView = view.findViewById(R.id.button_add_exercise_regular)
        val bonusPlusButton: CardView = view.findViewById(R.id.button_add_exercise_bonus)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == groupedExercises.size) {
            VIEW_TYPE_ADD_BUTTONS
        } else {
            VIEW_TYPE_EXERCISE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_EXERCISE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_active_exercise, parent, false)
                GroupedExerciseViewHolder(view)
            }
            VIEW_TYPE_ADD_BUTTONS -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_add_buttons_row, parent, false)
                AddButtonsViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is GroupedExerciseViewHolder -> {
                bindExerciseViewHolder(holder, position)
            }
            is AddButtonsViewHolder -> {
                bindAddButtonsViewHolder(holder)
            }
        }
    }
    
    private fun bindAddButtonsViewHolder(holder: AddButtonsViewHolder) {
        holder.regularPlusButton.setOnClickListener {
            onAddExerciseClicked()
        }
        holder.bonusPlusButton.setOnClickListener {
            onAddSpecialClicked()
        }
    }
    
    private fun bindExerciseViewHolder(holder: GroupedExerciseViewHolder, position: Int) {
        val groupedExercise = groupedExercises[position]
        holder.exerciseName.text = groupedExercise.exerciseName

        val groupId = groupedExercise.supersetGroupId
        val groupIndices = if (groupId != null) {
            groupedExercises.mapIndexed { i, g -> if (g.supersetGroupId == groupId) i else -1 }.filter { it >= 0 }
        } else emptyList()
        val positionInGroup = groupIndices.indexOf(position)
        val isInSuperset = groupId != null && groupIndices.isNotEmpty()
        val isFirstInSuperset = isInSuperset && positionInGroup == 0 && groupIndices.size > 1

        holder.supersetLinkButton.visibility = if (isFirstInSuperset) View.VISIBLE else View.GONE
        if (isFirstInSuperset && groupId != null) {
            holder.supersetLinkButton.setOnClickListener {
                showRemoveSupersetDialog(holder.itemView.context, groupId)
            }
        } else {
            holder.supersetLinkButton.setOnClickListener(null)
        }

        val workingSetCount = { g: GroupedExercise ->
            g.sets.count { !it.isWarmup }
        }
        val timerRunning = isRestTimerRunning()
        val setCounts = groupIndices.map { workingSetCount(groupedExercises[it]) }
        val minCount = setCounts.minOrNull() ?: 0
        val activePositionInGroup = if (groupIndices.isNotEmpty()) setCounts.indexOfFirst { it == minCount } else -1
        val nextPositionInGroup = if (activePositionInGroup >= 0 && groupIndices.size > 1) (activePositionInGroup + 1) % groupIndices.size else -1
        val isActive = isInSuperset && positionInGroup >= 0 && positionInGroup == activePositionInGroup
        val isWaitingForTimer = isInSuperset && timerRunning && nextPositionInGroup >= 0 && positionInGroup == nextPositionInGroup
        val canAddSet = !isInSuperset || isActive || isWaitingForTimer

        val isSelectedForSuperset = position in selectedForSupersetPositions()
        val strokeWidthPx = (2 * holder.itemView.resources.displayMetrics.density).toInt()
        when {
            isSelectedForSuperset -> {
                holder.cardExercise.strokeWidth = strokeWidthPx
                holder.cardExercise.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.fitness_accent)
                holder.cardExercise.alpha = 1f
            }
            isActive -> {
                holder.cardExercise.strokeWidth = strokeWidthPx
                holder.cardExercise.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.superset_active_border)
                holder.cardExercise.alpha = 1f
            }
            isWaitingForTimer -> {
                holder.cardExercise.strokeWidth = strokeWidthPx
                holder.cardExercise.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.superset_waiting_border)
                holder.cardExercise.alpha = 1f
            }
            isInSuperset && !canAddSet -> {
                holder.cardExercise.strokeWidth = 0
                holder.cardExercise.alpha = 0.5f
            }
            else -> {
                holder.cardExercise.strokeWidth = 0
                holder.cardExercise.alpha = 1f
            }
        }

        holder.addSetButton.isEnabled = canAddSet
        holder.addSetButton.alpha = if (canAddSet) 1f else 0.5f
        holder.duplicateSetButton.isEnabled = canAddSet
        holder.duplicateSetButton.alpha = if (canAddSet) 1f else 0.5f
        holder.itemView.isClickable = canAddSet
        holder.itemView.isFocusable = canAddSet

        holder.itemView.setOnLongClickListener {
            onExerciseLongPress(position)
            true
        }

        // Get exercise note from library
        val trainingData = jsonHelper.readTrainingData()
        val exerciseLibraryItem = trainingData.exerciseLibrary.find { it.id == groupedExercise.exerciseId }
        val exerciseNoteText = exerciseLibraryItem?.note?.takeIf { it.isNotEmpty() }
        
        // Show/hide note tooltip button based on whether note exists
        if (exerciseNoteText != null) {
            holder.noteTooltipButton.visibility = View.VISIBLE
            
            // Set up note tooltip popup dialog
            holder.noteTooltipButton.setOnClickListener {
                showNoteDialog(holder.itemView.context, groupedExercise.exerciseName, exerciseNoteText)
            }
        } else {
            holder.noteTooltipButton.visibility = View.GONE
        }

        // Setup Intent Selection ChipGroup
        val currentIntent = exerciseIntents[groupedExercise.exerciseId]
        val lastIntent = lastIntents[groupedExercise.exerciseId]
        
        // Check if intent is locked (first set has been logged)
        val lockedIntent = lockedIntents[groupedExercise.exerciseId]
        val isLocked = lockedIntent != null
        
        // Clear previous selection listeners to avoid RecyclerView bugs
        holder.chipGroupIntent.setOnCheckedChangeListener(null)
        
        // Set initial selection - only check if currentIntent is set (not null)
        if (currentIntent != null) {
            when (currentIntent) {
                SetIntent.STRENGTH -> holder.chipGroupIntent.check(R.id.chip_strength)
                SetIntent.BUILD -> holder.chipGroupIntent.check(R.id.chip_build)
                SetIntent.FLUSH -> holder.chipGroupIntent.check(R.id.chip_flush)
                else -> holder.chipGroupIntent.clearCheck()
            }
        } else {
            // No default selection - clear any checked state
            holder.chipGroupIntent.clearCheck()
        }
        
        // Emoji icons can't be reliably tinted, so grey the WHOLE chip for the two not chosen.
        run {
            val grey = ContextCompat.getColor(holder.itemView.context, R.color.fitness_text_secondary)
            val normal = ContextCompat.getColor(holder.itemView.context, R.color.fitness_text_primary)

            fun applyChipState(
                chip: com.google.android.material.chip.Chip,
                baseLabel: String,
                intent: SetIntent,
            ) {
                // Add "(Last)" if this is the last intent used
                val label = if (lastIntent == intent) {
                    "$baseLabel (Last)"
                } else {
                    baseLabel
                }
                chip.text = label

                val shouldGrey = isLocked && lockedIntent != intent
                chip.alpha = if (shouldGrey) 0.45f else 1.0f
                chip.setTextColor(if (shouldGrey) grey else normal)
            }

            applyChipState(holder.chipStrength, "Strength 💥", SetIntent.STRENGTH)
            applyChipState(holder.chipBuild, "Build 🛡️", SetIntent.BUILD)
            applyChipState(holder.chipFlush, "Flush 🩸", SetIntent.FLUSH)
        }
        
        // Handle selection changes
        holder.chipGroupIntent.setOnCheckedChangeListener { group, checkedId ->
            val selectedIntent = when (checkedId) {
                R.id.chip_strength -> SetIntent.STRENGTH
                R.id.chip_build -> SetIntent.BUILD
                R.id.chip_flush -> SetIntent.FLUSH
                else -> SetIntent.BUILD
            }
            
            // If intent is locked and user is trying to change to a different intent, show warning
            if (isLocked && selectedIntent != lockedIntent) {
                // Temporarily revert selection to prevent UI change
                group.setOnCheckedChangeListener(null)
                when (lockedIntent) {
                    SetIntent.STRENGTH -> group.check(R.id.chip_strength)
                    SetIntent.BUILD -> group.check(R.id.chip_build)
                    SetIntent.FLUSH -> group.check(R.id.chip_flush)
                    else -> group.check(R.id.chip_build)
                }
                // Show warning dialog - if confirmed, it will update the intent
                showIntentChangeWarning(holder.itemView.context, groupedExercise.exerciseId, selectedIntent, holder)
                // Re-attach listener after dialog is shown
                holder.chipGroupIntent.setOnCheckedChangeListener { g, cId ->
                    val selIntent = when (cId) {
                        R.id.chip_strength -> SetIntent.STRENGTH
                        R.id.chip_build -> SetIntent.BUILD
                        R.id.chip_flush -> SetIntent.FLUSH
                        else -> SetIntent.BUILD
                    }
                    val locked = lockedIntents[groupedExercise.exerciseId]
                    if (locked != null && selIntent != locked) {
                        g.setOnCheckedChangeListener(null)
                        when (locked) {
                            SetIntent.STRENGTH -> g.check(R.id.chip_strength)
                            SetIntent.BUILD -> g.check(R.id.chip_build)
                            SetIntent.FLUSH -> g.check(R.id.chip_flush)
                            else -> g.check(R.id.chip_build)
                        }
                        showIntentChangeWarning(holder.itemView.context, groupedExercise.exerciseId, selIntent, holder)
                    } else {
                        exerciseIntents[groupedExercise.exerciseId] = selIntent
                        onIntentChanged(groupedExercise.exerciseId, selIntent)
                    }
                }
            } else {
                exerciseIntents[groupedExercise.exerciseId] = selectedIntent
                onIntentChanged(groupedExercise.exerciseId, selectedIntent)
            }
        }

        // Get last workout data only if user has selected an intent
        // Don't show data for "Last" intent - it's just informational, not a selection
        val lastWorkoutSets = if (currentIntent != null) {
            lastWorkoutData[groupedExercise.exerciseId]?.get(currentIntent) ?: emptyList()
        } else {
            emptyList()
        }
        
        // Check if exercise has completed sets (non-zero weight or explicitly completed)
        val completedSets = groupedExercise.sets.filter { set ->
            set.kg > 0f || set.completed == true
        }
        val hasSets = groupedExercise.sets.isNotEmpty()
        val loggedSetsCount = completedSets.size
        
        // Get recommendation to check if sets are complete
        val recommendation = exerciseRecommendations[groupedExercise.exerciseId]
        val recommendedSetsCount = recommendation?.recommendedSets
        
        // Count only working sets (exclude warmup: isWarmup or legacy RPE 6)
        val currentWorkingSets = completedSets.count { !it.isEffectivelyWarmup() && (it.kg > 0f || it.completed == true) }
        val lastWorkingSets = lastWorkoutSets.count { !it.isEffectivelyWarmup() }
        
        // Show completion checkmark if user has logged the recommended number of sets
        // or if they've logged the last number of sets (when no recommendation exists)
        val targetSetsCount = recommendedSetsCount ?: lastWorkingSets
        val isComplete = targetSetsCount != null && targetSetsCount > 0 && currentWorkingSets >= targetSetsCount
        holder.completionCheck.visibility = if (isComplete) View.VISIBLE else View.GONE

        // Show sets count: "(N of X sets)" where both count only working sets
        val targetSets = recommendedSetsCount ?: lastWorkingSets
        if (targetSets > 0) {
            holder.setsCount.text = "($currentWorkingSets of $targetSets sets)"
        } else if (currentWorkingSets > 0) {
            holder.setsCount.text = "($currentWorkingSets sets)"
        } else {
            holder.setsCount.text = ""
        }

        // Hide recommended info (replaced by inline last workout data)
        holder.recommendedInfo.visibility = View.GONE

        // Show actual logged sets with last workout data
        if (hasSets && completedSets.isNotEmpty()) {
            val sortedCurrentSets = completedSets.sortedBy { it.setNumber }
            
            // Separate into warmup and working sets (legacy: RPE 6 = warmup; new data: use isWarmup only)
            val currentWarmupSets = sortedCurrentSets.filter { it.isEffectivelyWarmup() }
            val currentWorkingSets = sortedCurrentSets.filter { !it.isEffectivelyWarmup() }
            val lastWarmupSets = lastWorkoutSets.filter { it.isEffectivelyWarmup() }
            val lastWorkingSets = lastWorkoutSets.filter { !it.isEffectivelyWarmup() }
            
            // Build display text with matching
            val currentSetsText = mutableListOf<String>()
            val lastSetsText = mutableListOf<String>()
            
            // Handle warmup section alignment
            val maxWarmupSets = maxOf(currentWarmupSets.size, lastWarmupSets.size)
            if (maxWarmupSets > 0) {
                for (i in 0 until maxWarmupSets) {
                    // Current side
                    if (i < currentWarmupSets.size) {
                        val currentSet = currentWarmupSets[i]
                        val weightString = if (currentSet.kg % 1 == 0f) {
                            currentSet.kg.toInt().toString()
                        } else {
                            currentSet.kg.toString()
                        }
                        currentSetsText.add("Warmup: ${weightString}kg × ${currentSet.reps}")
                    } else {
                        currentSetsText.add("(No warmup)")
                    }
                    
                    // Last side
                    if (i < lastWarmupSets.size) {
                        val lastSet = lastWarmupSets[i]
                        val lastWeightString = if (lastSet.kg % 1 == 0f) {
                            lastSet.kg.toInt().toString()
                        } else {
                            lastSet.kg.toString()
                        }
                        val lastSuffix = when {
                            lastSet.rpe != null -> " (${"%.1f".format(lastSet.rpe)})"
                            else -> ""
                        }
                        lastSetsText.add("Last: $lastWeightString kg × ${lastSet.reps}$lastSuffix")
                    } else {
                        lastSetsText.add("(No warmup)")
                    }
                }
            }
            
            // Handle working sets alignment
            val maxWorkingSets = maxOf(currentWorkingSets.size, lastWorkingSets.size)
            for (i in 0 until maxWorkingSets) {
                // Current side
                if (i < currentWorkingSets.size) {
                    val currentSet = currentWorkingSets[i]
                    val weightString = if (currentSet.kg % 1 == 0f) {
                        currentSet.kg.toInt().toString()
                    } else {
                        currentSet.kg.toString()
                    }
                    val suffix = when {
                        currentSet.rpe != null -> " (${"%.1f".format(currentSet.rpe)})"
                        else -> ""
                    }
                    // Use sequential working set number (1, 2, 3...) excluding warmups
                    val workingSetNumber = i + 1
                    currentSetsText.add("Set $workingSetNumber: ${weightString}kg × ${currentSet.reps}$suffix")
                } else {
                    currentSetsText.add("")
                }
                
                // Last side
                if (i < lastWorkingSets.size) {
                    val lastSet = lastWorkingSets[i]
                    val lastWeightString = if (lastSet.kg % 1 == 0f) {
                        lastSet.kg.toInt().toString()
                    } else {
                        lastSet.kg.toString()
                    }
                    val lastSuffix = when {
                        lastSet.rpe != null -> " (${"%.1f".format(lastSet.rpe)})"
                        else -> ""
                    }
                    lastSetsText.add("Last: $lastWeightString kg × ${lastSet.reps}$lastSuffix")
                } else {
                    lastSetsText.add("")
                }
            }
            
            // Get progression suggestion if intent is selected (STRENGTH or BUILD only)
            val suggestionInfo = getSuggestionInfo(holder.itemView.context, groupedExercise.exerciseId, currentIntent)
            val currentWorkingSetsCount = currentWorkingSets.size
            val lastWorkoutWorkingSetsCount = lastWorkingSets.size
            
            // Only show suggestion if we haven't completed the suggested number of sets
            // Also hide if we've done as many sets as last workout
            val suggestedSetsCount = suggestionInfo?.suggestedSets ?: 3
            val shouldShowSuggestion = suggestionInfo != null && 
                currentWorkingSetsCount < suggestedSetsCount &&
                (lastWorkoutWorkingSetsCount == 0 || currentWorkingSetsCount < lastWorkoutWorkingSetsCount)
            
            // Find the line index of the last logged working set
            // Working sets are added after warmup sets, so we need to find the last non-empty working set line
            val lastLoggedWorkingSetIndex = if (currentWorkingSetsCount > 0) {
                // Find the index of the last working set in currentSetsText
                // Warmup sets come first, then working sets
                val warmupLinesCount = if (maxWarmupSets > 0) maxWarmupSets else 0
                // The last working set is at: warmupLinesCount + (index of last logged working set in the loop)
                // Since we iterate up to maxWorkingSets, but only log when i < currentWorkingSets.size,
                // the last logged set is at warmupLinesCount + currentWorkingSetsCount - 1
                warmupLinesCount + currentWorkingSetsCount - 1
            } else {
                // No working sets logged yet - insert after warmup if any, otherwise at start
                if (maxWarmupSets > 0) maxWarmupSets - 1 else -1
            }
            
            // Build spannable text with colored suggestion inserted after last logged working set
            val displayText = if (shouldShowSuggestion && lastLoggedWorkingSetIndex >= 0) {
                buildSpannableWithSuggestion(
                    holder.itemView.context,
                    currentSetsText.joinToString("\n"),
                    suggestionInfo.text,
                    insertAfterLine = lastLoggedWorkingSetIndex  // Insert after last logged working set
                )
            } else if (shouldShowSuggestion) {
                // No sets logged yet - append at end
                buildSpannableWithSuggestion(
                    holder.itemView.context,
                    currentSetsText.joinToString("\n"),
                    suggestionInfo.text,
                    insertAfterLine = -1  // Append at end
                )
            } else {
                currentSetsText.joinToString("\n")
            }
            
            holder.loggedSets.text = displayText
            holder.loggedSets.visibility = View.VISIBLE
            
            // Always show last workout data when available
            if (lastWorkoutSets.isNotEmpty()) {
                holder.lastWorkoutSets.text = lastSetsText.joinToString("\n")
                holder.lastWorkoutSets.visibility = View.VISIBLE
            } else {
                holder.lastWorkoutSets.visibility = View.GONE
            }
        } else {
            // No sets logged yet - show suggestion if intent is selected
            val suggestionText = getSuggestionForIntent(holder.itemView.context, groupedExercise.exerciseId, currentIntent)
            
            if (suggestionText != null) {
                // Show suggestion in the logged sets area with colored text
                val displayText = buildSpannableWithSuggestion(holder.itemView.context, "", suggestionText)
                holder.loggedSets.text = displayText
                holder.loggedSets.visibility = View.VISIBLE
            } else {
                holder.loggedSets.visibility = View.GONE
            }
            
            if (lastWorkoutSets.isNotEmpty()) {
                // Show all last workout sets (legacy: RPE 6 = warmup; new: use isWarmup only)
                val lastWarmupSets = lastWorkoutSets.filter { it.isEffectivelyWarmup() }
                val lastWorkingSets = lastWorkoutSets.filter { !it.isEffectivelyWarmup() }
                val lastSetsText = mutableListOf<String>()
                
                // Format warmup sets
                lastWarmupSets.forEach { lastSet ->
                    val lastWeightString = if (lastSet.kg % 1 == 0f) {
                        lastSet.kg.toInt().toString()
                    } else {
                        lastSet.kg.toString()
                    }
                    val lastSuffix = when {
                        lastSet.rpe != null -> " (${"%.1f".format(lastSet.rpe)})"
                        else -> ""
                    }
                    lastSetsText.add("Last: $lastWeightString kg × ${lastSet.reps}$lastSuffix")
                }
                
                // Format working sets
                lastWorkingSets.forEach { lastSet ->
                    val lastWeightString = if (lastSet.kg % 1 == 0f) {
                        lastSet.kg.toInt().toString()
                    } else {
                        lastSet.kg.toString()
                    }
                    val lastSuffix = when {
                        lastSet.rpe != null -> " (${"%.1f".format(lastSet.rpe)})"
                        else -> ""
                    }
                    lastSetsText.add("Last: $lastWeightString kg × ${lastSet.reps}$lastSuffix")
                }
                
                holder.lastWorkoutSets.text = lastSetsText.joinToString("\n")
                holder.lastWorkoutSets.visibility = View.VISIBLE
            } else {
                // Don't show anything if no intent selected or no data
                holder.lastWorkoutSets.visibility = View.GONE
            }
        }

        // Visibility logic
        holder.duplicateSetButton.visibility = if (hasSets) View.VISIBLE else View.GONE
        holder.editActivityButton.visibility = if (hasSets) View.VISIBLE else View.GONE
        holder.deleteExerciseButton.visibility = View.VISIBLE // Always show delete button

        // --- CLICK LISTENERS ---

        // 1. Add Set (Plus button) - only when canAddSet (active or waiting in superset)
        holder.addSetButton.setOnClickListener {
            if (canAddSet) onAddSetClicked(groupedExercise.exerciseId, groupedExercise.exerciseName)
        }

        // 2. Duplicate Last Set - only when canAddSet
        holder.duplicateSetButton.setOnClickListener {
            if (canAddSet) onDuplicateSetClicked(groupedExercise.exerciseId)
        }

        // 3. Edit (Pencil button)
        holder.editActivityButton.setOnClickListener {
            onEditActivityClicked(groupedExercise)
        }

        // 4. Delete Exercise (Trash button)
        holder.deleteExerciseButton.setOnClickListener {
            onDeleteExerciseClicked(groupedExercise.exerciseId)
        }
        
        // 5. Card Body Click -> Trigger Edit or Add (only when canAddSet for add)
        holder.itemView.setOnClickListener {
            if (!canAddSet) return@setOnClickListener
            if (hasSets) {
                onEditActivityClicked(groupedExercise)
            } else {
                onAddSetClicked(groupedExercise.exerciseId, groupedExercise.exerciseName)
            }
        }
    }

    private fun showRemoveSupersetDialog(context: Context, supersetGroupId: String) {
        DialogHelper.createBuilder(context)
            .setTitle(context.getString(R.string.dialog_title_remove_superset))
            .setMessage(context.getString(R.string.dialog_message_remove_superset))
            .setPositiveButton(context.getString(R.string.button_remove_superset)) { _, _ ->
                onUnlinkSuperset(supersetGroupId)
            }
            .setNegativeButton(context.getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    override fun getItemCount() = groupedExercises.size + 1
    
    private fun showNoteDialog(context: Context, exerciseName: String, noteText: String) {
        DialogHelper.createBuilder(context)
            .setTitle(exerciseName)
            .setMessage(noteText)
            .setPositiveButton(context.getString(R.string.button_ok), null)
            .showWithTransparentWindow()
    }
    
    private fun showIntentChangeWarning(context: Context, exerciseId: Int, newIntent: SetIntent, holder: GroupedExerciseViewHolder) {
        DialogHelper.createBuilder(context)
            .setTitle(context.getString(R.string.dialog_title_intent_change_warning))
            .setMessage(context.getString(R.string.dialog_message_intent_change_warning))
            .setPositiveButton(context.getString(R.string.button_change)) { _, _ ->
                // User confirmed - allow the change and update locked intent
                exerciseIntents[exerciseId] = newIntent
                lockedIntents[exerciseId] = newIntent
                onIntentChanged(exerciseId, newIntent)
                // Update chip selection to reflect the change
                holder.chipGroupIntent.setOnCheckedChangeListener(null)
                when (newIntent) {
                    SetIntent.STRENGTH -> holder.chipGroupIntent.check(R.id.chip_strength)
                    SetIntent.BUILD -> holder.chipGroupIntent.check(R.id.chip_build)
                    SetIntent.FLUSH -> holder.chipGroupIntent.check(R.id.chip_flush)
                    else -> holder.chipGroupIntent.check(R.id.chip_build)
                }
                // Notify adapter to update UI (to refresh greyed out state)
                val position = groupedExercises.indexOfFirst { it.exerciseId == exerciseId }
                if (position >= 0) {
                    notifyItemChanged(position)
                }
            }
            .setNegativeButton(context.getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }
    
    /**
     * Data class to hold suggestion info including suggested sets count
     */
    private data class SuggestionInfo(
        val text: String,
        val suggestedSets: Int?
    )
    
    /**
     * Get progression suggestion for an exercise based on selected intent.
     * Returns null for WARMUP or if no intent selected.
     */
    private fun getSuggestionForIntent(context: Context, exerciseId: Int, intent: SetIntent?): String? {
        return getSuggestionInfo(context, exerciseId, intent)?.text
    }
    
    /**
     * Get progression suggestion info including suggested sets count.
     * Returns null for WARMUP or if no intent selected.
     */
    private fun getSuggestionInfo(context: Context, exerciseId: Int, intent: SetIntent?): SuggestionInfo? {
        // No suggestion for WARMUP or if no intent selected
        if (intent == null || intent == SetIntent.WARMUP) {
            return null
        }
        
        val trainingData = jsonHelper.readTrainingData()
        val settings = try {
            ProgressionSettingsManager(context).getSettings()
        } catch (e: Exception) {
            ProgressionHelper.ProgressionSettings()
        }
        
        val suggestion = ProgressionHelper.getIntentSuggestion(
            exerciseId = exerciseId,
            intent = intent,
            trainingData = trainingData,
            settings = settings
        )
        
        // Don't show suggestion if no action needed
        if (suggestion.weightAction == ProgressionHelper.WeightAction.NONE) {
            return null
        }
        
        // Build display text based on suggestion
        val displayText = when {
            suggestion.isFirstTime -> {
                val (minReps, _) = ProgressionHelper.getRepRange(intent, settings)
                val targetRpe = ProgressionHelper.getTargetRpe(intent, settings)
                "Target: ${suggestion.suggestedSets ?: 3}×$minReps @ RPE ${"%.1f".format(targetRpe)}"
            }
            suggestion.displayText.isNotEmpty() -> {
                val badge = suggestion.badge?.let { "[$it] " } ?: ""
                "${badge}Next: ${suggestion.displayText}"
            }
            else -> return null
        }
        
        return SuggestionInfo(
            text = displayText,
            suggestedSets = suggestion.suggestedSets
        )
    }
    
    /**
     * Build a SpannableStringBuilder with the logged sets text and a colored suggestion line.
     * @param insertAfterLine If >= 0, inserts suggestion after that line number. Otherwise appends at end.
     */
    private fun buildSpannableWithSuggestion(
        context: Context,
        setsText: String,
        suggestionText: String?,
        insertAfterLine: Int = -1
    ): CharSequence {
        if (suggestionText == null) {
            return setsText
        }
        
        val builder = SpannableStringBuilder()
        
        if (setsText.isEmpty()) {
            // No sets logged yet - just show suggestion
            val suggestionStart = builder.length
            builder.append(suggestionText)
            val suggestionEnd = builder.length
            
            val suggestionColor = ContextCompat.getColor(context, R.color.fitness_suggestion)
            builder.setSpan(
                ForegroundColorSpan(suggestionColor),
                suggestionStart,
                suggestionEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            return builder
        }
        
        // Split sets text into lines
        val lines = setsText.split("\n")
        
        // Insert suggestion after specified line, or at end if insertAfterLine is invalid
        val insertIndex = if (insertAfterLine >= 0 && insertAfterLine < lines.size) {
            insertAfterLine + 1
        } else {
            lines.size
        }
        
        // Build text with suggestion inserted
        for (i in lines.indices) {
            if (i > 0) {
                builder.append("\n")
            }
            builder.append(lines[i])
            
            // Insert suggestion after this line if it's the insertion point
            if (i == insertIndex - 1) {
                builder.append("\n")
                val suggestionStart = builder.length
                builder.append(suggestionText)
                val suggestionEnd = builder.length
                
                val suggestionColor = ContextCompat.getColor(context, R.color.fitness_suggestion)
                builder.setSpan(
                    ForegroundColorSpan(suggestionColor),
                    suggestionStart,
                    suggestionEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        
        return builder
    }
}