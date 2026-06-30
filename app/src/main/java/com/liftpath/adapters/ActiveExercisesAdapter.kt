package com.liftpath.adapters

import android.content.Context
import android.graphics.Typeface
import android.os.CountDownTimer
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
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
import com.liftpath.helpers.RestTimerHelper
import com.liftpath.helpers.WorkoutGenerator
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.DraftExerciseRow
import com.liftpath.models.GroupedExercise
import com.liftpath.models.PlanSlotType
import com.liftpath.models.SetIntent
import com.google.android.material.chip.ChipGroup
import java.util.Locale

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
    private val planSnapshots: Map<Int, DraftExerciseRow> = emptyMap(),
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
    private val onExerciseLongPress: (position: Int) -> Unit,
    private val getSupersetTargetSets: (groupId: String?) -> Int?,
    private val getCompletedSupersetGroupIds: () -> Set<String>,
    private val onSpecialCompleted: (exerciseId: Int, isCompleted: Boolean) -> Unit = { _, _ -> },
    private val onDeleteSpecialClicked: (exerciseId: Int) -> Unit = {},
    private val onStartTimerClicked: (exerciseId: Int) -> Unit = {},
    private val onEditDurationClicked: (exerciseId: Int) -> Unit = {},
    private val onChangeExerciseClicked: ((position: Int) -> Unit)? = null,
    private val getTimerEndTimeMillis: (exerciseId: Int) -> Long? = { null },
    private val onSpecialTimerReset: (exerciseId: Int) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val VIEW_TYPE_EXERCISE    = 0
        private const val VIEW_TYPE_ADD_BUTTONS = 1
        private const val VIEW_TYPE_SPECIAL     = 2
    }

    private val cardTimers = mutableMapOf<Int, CountDownTimer?>()

    /** Always use '.' as decimal separator (locale-independent) for workout numbers. */
    private fun formatOneDecimal(value: Float): String = String.format(Locale.US, "%.1f", value)

    private val collapsedExercises = mutableSetOf<Int>()

    /** Rebuilt when exercise list layout / superset membership changes (not when only sets change). */
    private var cachedSupersetLayoutKey: Int = Int.MIN_VALUE
    private var supersetPositionsByGroupId: Map<String, List<Int>> = emptyMap()

    private var progressionSettingsCache: ProgressionHelper.ProgressionSettings? = null

    private fun ensureSupersetPositionCache() {
        var key = groupedExercises.size
        for (g in groupedExercises) {
            key = key * 31 + g.exerciseId
            key = key * 31 + (g.supersetGroupId?.hashCode() ?: 0)
        }
        if (key == cachedSupersetLayoutKey) return
        cachedSupersetLayoutKey = key
        supersetPositionsByGroupId = groupedExercises
            .mapIndexedNotNull { i, g -> g.supersetGroupId?.let { id -> id to i } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, indices) -> indices.sorted() }
    }

    private fun progressionSettings(context: Context): ProgressionHelper.ProgressionSettings {
        progressionSettingsCache?.let { return it }
        val s = try {
            ProgressionSettingsManager(context.applicationContext).getSettings()
        } catch (e: Exception) {
            ProgressionHelper.ProgressionSettings()
        }
        progressionSettingsCache = s
        return s
    }

    /** Call when progression settings may have changed (e.g. activity resumed). */
    fun invalidateProgressionSettingsCache() {
        progressionSettingsCache = null
    }

    class GroupedExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val swapExerciseButton: android.widget.ImageButton = view.findViewById(R.id.button_swap_exercise)
        val intentBadge: TextView = view.findViewById(R.id.text_intent_badge)
        val recommendedInfo: TextView = view.findViewById(R.id.text_recommended_info)
        val setsCount: TextView = view.findViewById(R.id.text_sets_count)
        val loggedSets: TextView = view.findViewById(R.id.text_logged_sets)
        val lastWorkoutSets: TextView = view.findViewById(R.id.text_last_workout_sets)
        val completionCheck: ImageView = view.findViewById(R.id.image_completion_check)
        val layoutActionButtons: View = view.findViewById(R.id.layout_action_buttons)
        val addSetButton: CardView = view.findViewById(R.id.button_add_set)
        val duplicateSetButton: CardView = view.findViewById(R.id.button_duplicate_set)
        val editActivityButton: CardView = view.findViewById(R.id.button_edit_activity)
        val completeExerciseButton: CardView = view.findViewById(R.id.button_complete_exercise)
        val iconCompleteExercise: ImageView = view.findViewById(R.id.icon_complete_exercise)
        val deleteExerciseButton: CardView = view.findViewById(R.id.button_delete_exercise)
        val chipGroupIntent: ChipGroup = view.findViewById(R.id.chip_group_intent)
        val chipStrength: com.google.android.material.chip.Chip = view.findViewById(R.id.chip_strength)
        val chipBuild: com.google.android.material.chip.Chip = view.findViewById(R.id.chip_build)
        val chipFlush: com.google.android.material.chip.Chip = view.findViewById(R.id.chip_flush)
        val noteTooltipButton: android.widget.ImageButton = view.findViewById(R.id.button_note_tooltip)
        val supersetLinkTop: android.widget.ImageButton = view.findViewById(R.id.button_superset_link_top)
        val supersetLinkBottom: android.widget.ImageButton = view.findViewById(R.id.button_superset_link_bottom)
        val cardExercise: MaterialCardView = view.findViewById(R.id.card_exercise)
    }
    
    class AddButtonsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val regularPlusButton: CardView = view.findViewById(R.id.button_add_exercise_regular)
        val bonusPlusButton: CardView = view.findViewById(R.id.button_add_exercise_bonus)
    }

    class SpecialElementViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.text_special_icon)
        val label: TextView = view.findViewById(R.id.text_special_label)
        val subtitle: TextView = view.findViewById(R.id.text_special_subtitle)
        val notesText: TextView = view.findViewById(R.id.text_special_notes)
        val durationBadge: TextView = view.findViewById(R.id.text_duration_badge)
        val cardMarkComplete: CardView = view.findViewById(R.id.card_mark_complete)
        val textMarkComplete: TextView = view.findViewById(R.id.text_mark_complete)
        val cardStartTimer: CardView = view.findViewById(R.id.card_start_timer)
        val btnDelete: android.widget.ImageButton = view.findViewById(R.id.button_delete_special)
        val cardSpecial: MaterialCardView = view as MaterialCardView
        val layoutTimerIdle: View = view.findViewById(R.id.layout_timer_idle)
        val layoutTimerRunning: View = view.findViewById(R.id.layout_timer_running)
        val textCountdown: TextView = view.findViewById(R.id.text_timer_countdown)
        val progressTimer: ProgressBar = view.findViewById(R.id.progress_timer)
        var boundExerciseId: Int = -1
    }

    override fun getItemViewType(position: Int): Int = when {
        position == groupedExercises.size -> VIEW_TYPE_ADD_BUTTONS
        groupedExercises[position].isSpecialElement -> VIEW_TYPE_SPECIAL
        else -> VIEW_TYPE_EXERCISE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_EXERCISE -> GroupedExerciseViewHolder(
                inflater.inflate(R.layout.list_item_active_exercise, parent, false)
            )
            VIEW_TYPE_ADD_BUTTONS -> AddButtonsViewHolder(
                inflater.inflate(R.layout.item_add_buttons_row, parent, false)
            )
            VIEW_TYPE_SPECIAL -> SpecialElementViewHolder(
                inflater.inflate(R.layout.list_item_active_special, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SpecialElementViewHolder -> bindSpecialViewHolder(holder, position)
            is GroupedExerciseViewHolder -> bindExerciseViewHolder(holder, position)
            is AddButtonsViewHolder -> bindAddButtonsViewHolder(holder)
        }
    }

    private fun formatCountdown(secs: Int): String {
        val m = secs / 60
        val s = secs % 60
        return String.format(Locale.getDefault(), "%d:%02d", m, s)
    }

    private fun bindSpecialViewHolder(holder: SpecialElementViewHolder, position: Int) {
        val element = groupedExercises[position]
        val isWarmup = element.slotType == PlanSlotType.WARMUP
        val isCompleted = element.isSpecialCompleted
        val ctx = holder.itemView.context

        holder.boundExerciseId = element.exerciseId

        holder.icon.text = if (isWarmup) "🔥" else "🧊"
        holder.label.setText(if (isWarmup) R.string.label_warmup_element else R.string.label_cooldown_element)
        holder.subtitle.setText(if (isWarmup) R.string.warmup_element_subtitle else R.string.cooldown_element_subtitle)

        // Duration badge — shows "X min", tapping opens edit dialog
        val durationMins = element.durationSeconds / 60
        holder.durationBadge.text = "${durationMins} min"
        holder.durationBadge.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) onEditDurationClicked(element.exerciseId)
        }

        // Show notes if any
        val notes = element.sets.firstOrNull()?.note
        if (!notes.isNullOrEmpty()) {
            holder.notesText.text = notes
            holder.notesText.visibility = View.VISIBLE
        } else {
            holder.notesText.visibility = View.GONE
        }

        // Cancel any running card timer for this element before rebinding
        cardTimers[element.exerciseId]?.cancel()
        cardTimers[element.exerciseId] = null

        val primaryColor = ContextCompat.getColor(ctx, R.color.fitness_primary)
        val greenColor = ContextCompat.getColor(ctx, R.color.superset_complete_green)
        val grayColor = ContextCompat.getColor(ctx, R.color.fitness_text_secondary)
        val strokePx = (3 * ctx.resources.displayMetrics.density).toInt()

        val endTimeMillis = getTimerEndTimeMillis(element.exerciseId)
        val nowMs = System.currentTimeMillis()
        val isTimerActive = !isCompleted && endTimeMillis != null && endTimeMillis > nowMs

        val markCompleteParams = holder.cardMarkComplete.layoutParams as android.widget.LinearLayout.LayoutParams

        when {
            isCompleted -> {
                // Completed: hide start timer, expand mark-complete to full width
                holder.cardStartTimer.visibility = View.GONE
                markCompleteParams.weight = 1f
                holder.cardMarkComplete.layoutParams = markCompleteParams

                holder.textMarkComplete.setText(R.string.btn_mark_incomplete)
                holder.cardMarkComplete.setCardBackgroundColor(greenColor)
                holder.cardMarkComplete.isClickable = true
                holder.cardMarkComplete.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos >= 0) onSpecialCompleted(element.exerciseId, false)
                }

                holder.progressTimer.visibility = View.GONE
                holder.cardSpecial.strokeWidth = 0
            }

            isTimerActive -> {
                // Running: show countdown in start-timer area, show Reset button
                holder.cardStartTimer.visibility = View.VISIBLE
                markCompleteParams.weight = 0.3f
                holder.cardMarkComplete.layoutParams = markCompleteParams

                holder.layoutTimerIdle.visibility = View.GONE
                holder.layoutTimerRunning.visibility = View.VISIBLE
                holder.cardStartTimer.setCardBackgroundColor(greenColor)
                holder.cardStartTimer.isClickable = false

                holder.textMarkComplete.setText(R.string.btn_reset_timer)
                holder.cardMarkComplete.setCardBackgroundColor(grayColor)
                holder.cardMarkComplete.isClickable = true
                holder.cardMarkComplete.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos >= 0) onSpecialTimerReset(element.exerciseId)
                }

                holder.cardSpecial.strokeWidth = strokePx
                holder.cardSpecial.strokeColor = greenColor

                holder.progressTimer.visibility = View.VISIBLE
                holder.progressTimer.alpha = 1f

                val totalMs = element.durationSeconds * 1000L
                val remainingMs = endTimeMillis!! - nowMs
                val initialSecs = (remainingMs / 1000).toInt()
                holder.textCountdown.text = formatCountdown(initialSecs)
                holder.progressTimer.progress = ((remainingMs.toFloat() / totalMs) * 100).toInt().coerceIn(0, 100)

                val timer = object : CountDownTimer(remainingMs, 1000L) {
                    override fun onTick(millisUntilFinished: Long) {
                        val secs = (millisUntilFinished / 1000).toInt()
                        holder.textCountdown.text = formatCountdown(secs)
                        holder.progressTimer.progress = ((millisUntilFinished.toFloat() / totalMs) * 100).toInt().coerceIn(0, 100)
                    }
                    override fun onFinish() {
                        holder.textCountdown.text = formatCountdown(0)
                        holder.progressTimer.progress = 0
                        val pos = holder.bindingAdapterPosition
                        if (pos >= 0) onSpecialCompleted(element.exerciseId, true)
                    }
                }.start()
                cardTimers[element.exerciseId] = timer
            }

            else -> {
                // Idle: standard start + done buttons
                holder.cardStartTimer.visibility = View.VISIBLE
                markCompleteParams.weight = 0.3f
                holder.cardMarkComplete.layoutParams = markCompleteParams

                holder.layoutTimerIdle.visibility = View.VISIBLE
                holder.layoutTimerRunning.visibility = View.GONE
                holder.cardStartTimer.setCardBackgroundColor(primaryColor)
                holder.cardStartTimer.isClickable = true
                holder.cardStartTimer.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos >= 0) onStartTimerClicked(element.exerciseId)
                }

                holder.textMarkComplete.setText(R.string.btn_mark_complete)
                holder.cardMarkComplete.setCardBackgroundColor(primaryColor)
                holder.cardMarkComplete.isClickable = true
                holder.cardMarkComplete.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos >= 0) onSpecialCompleted(element.exerciseId, true)
                }

                holder.progressTimer.visibility = View.GONE
                holder.cardSpecial.strokeWidth = 0
            }
        }

        // Delete button
        holder.btnDelete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) onDeleteSpecialClicked(element.exerciseId)
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

        if (groupedExercise.isFamilySlot && onChangeExerciseClicked != null) {
            holder.swapExerciseButton.visibility = View.VISIBLE
            holder.swapExerciseButton.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos >= 0) onChangeExerciseClicked.invoke(pos)
            }
        } else {
            holder.swapExerciseButton.visibility = View.GONE
            holder.swapExerciseButton.setOnClickListener(null)
        }

        ensureSupersetPositionCache()
        val groupId = groupedExercise.supersetGroupId
        val groupIndices = groupId?.let { supersetPositionsByGroupId[it] } ?: emptyList()
        val positionInGroup = groupIndices.indexOf(position)
        val isInSuperset = groupId != null && groupIndices.isNotEmpty()
        val hasSupersetAbove = isInSuperset && position > 0 && groupedExercises[position - 1].supersetGroupId == groupId
        val hasSupersetBelow = isInSuperset && position < groupedExercises.size - 1 && groupedExercises[position + 1].supersetGroupId == groupId

        holder.supersetLinkTop.visibility = if (hasSupersetAbove) View.VISIBLE else View.GONE
        holder.supersetLinkBottom.visibility = if (hasSupersetBelow) View.VISIBLE else View.GONE
        if (isInSuperset && groupId != null) {
            val unlinkAction: (android.view.View) -> Unit = { _ -> showRemoveSupersetDialog(holder.itemView.context, groupId) }
            holder.supersetLinkTop.setOnClickListener(if (hasSupersetAbove) unlinkAction else null)
            holder.supersetLinkBottom.setOnClickListener(if (hasSupersetBelow) unlinkAction else null)
        } else {
            holder.supersetLinkTop.setOnClickListener(null)
            holder.supersetLinkBottom.setOnClickListener(null)
        }

        val workingSetCount = { g: GroupedExercise ->
            g.sets.count { !it.isWarmup }
        }
        val supersetTarget = getSupersetTargetSets(groupId)

        val currentIntent = exerciseIntents[groupedExercise.exerciseId]
        val lastWorkoutSets = if (currentIntent != null) {
            lastWorkoutData[groupedExercise.exerciseId]?.get(currentIntent) ?: emptyList()
        } else {
            emptyList()
        }
        val completedSets = groupedExercise.sets.filter { set ->
            set.kg > 0f || set.completed == true
        }
        val hasSets = groupedExercise.sets.isNotEmpty()
        val loggedSetsCount = completedSets.size
        val recommendation = exerciseRecommendations[groupedExercise.exerciseId]
        val recommendedSetsCount = recommendation?.recommendedSets
        val currentWorkingSets = completedSets.count { !it.isEffectivelyWarmup() && (it.kg > 0f || it.completed == true) }
        val lastWorkingSetsCount = lastWorkoutSets.count { !it.isEffectivelyWarmup() }
        val targetSets = supersetTarget ?: recommendedSetsCount ?: lastWorkingSetsCount
        val isComplete = targetSets > 0 && currentWorkingSets >= targetSets
        // Card outline: target met (header check) or user tapped check to collapse ("done" for this session)
        val outlineComplete = isComplete || groupedExercise.exerciseId in collapsedExercises
        // Any logged set counts (warmup or working): kg > 0 or explicitly completed
        val hasAnyRegisteredSet = completedSets.isNotEmpty()
        val canCollapseExercise = currentWorkingSets > 0
        if (groupedExercise.exerciseId in collapsedExercises && !canCollapseExercise) {
            collapsedExercises.remove(groupedExercise.exerciseId)
        }

        val hasSupersetReachedTarget = groupId != null && supersetTarget != null &&
            groupIndices.all { workingSetCount(groupedExercises[it]) >= supersetTarget }
        val timerRunning = isRestTimerRunning()
        val setCounts = groupIndices.map { workingSetCount(groupedExercises[it]) }
        val minCount = setCounts.minOrNull() ?: 0
        val activePositionInGroup = if (groupIndices.isNotEmpty()) setCounts.indexOfFirst { it == minCount } else -1
        val nextPositionInGroup = if (activePositionInGroup >= 0 && groupIndices.size > 1) (activePositionInGroup + 1) % groupIndices.size else -1
        val isActive = isInSuperset && !hasSupersetReachedTarget && positionInGroup >= 0 && positionInGroup == activePositionInGroup
        val isWaitingForTimer = isInSuperset && !hasSupersetReachedTarget && timerRunning && nextPositionInGroup >= 0 && positionInGroup == nextPositionInGroup
        val canAddSet = !isInSuperset || hasSupersetReachedTarget || isActive || isWaitingForTimer

        val isSelectedForSuperset = position in selectedForSupersetPositions()
        val isSupersetComplete = groupId != null && groupId in getCompletedSupersetGroupIds()
        val strokeWidthPx = (2 * holder.itemView.resources.displayMetrics.density).toInt()
        val incompleteOutlineColorRes = when {
            hasAnyRegisteredSet -> R.color.superset_active_border
            else -> R.color.active_exercise_outline_idle
        }
        when {
            isSupersetComplete -> {
                holder.cardExercise.strokeWidth = strokeWidthPx
                holder.cardExercise.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.superset_complete_green)
                holder.cardExercise.alpha = 1f
            }
            isSelectedForSuperset -> {
                holder.cardExercise.strokeWidth = strokeWidthPx
                holder.cardExercise.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.fitness_accent)
                holder.cardExercise.alpha = 1f
            }
            hasSupersetReachedTarget -> {
                holder.cardExercise.strokeWidth = strokeWidthPx
                holder.cardExercise.strokeColor = ContextCompat.getColor(
                    holder.itemView.context,
                    if (outlineComplete) R.color.superset_complete_green else incompleteOutlineColorRes
                )
                holder.cardExercise.alpha = 1f
            }
            isActive -> {
                holder.cardExercise.strokeWidth = strokeWidthPx
                holder.cardExercise.strokeColor = ContextCompat.getColor(
                    holder.itemView.context,
                    if (outlineComplete) R.color.superset_complete_green else incompleteOutlineColorRes
                )
                holder.cardExercise.alpha = 1f
            }
            isWaitingForTimer -> {
                holder.cardExercise.strokeWidth = strokeWidthPx
                holder.cardExercise.strokeColor = ContextCompat.getColor(
                    holder.itemView.context,
                    when {
                        outlineComplete -> R.color.superset_complete_green
                        hasAnyRegisteredSet -> R.color.superset_waiting_border
                        else -> R.color.active_exercise_outline_idle
                    }
                )
                holder.cardExercise.alpha = 1f
            }
            isInSuperset && !canAddSet -> {
                holder.cardExercise.strokeWidth = 0
                holder.cardExercise.alpha = 0.5f
            }
            else -> {
                holder.cardExercise.strokeWidth = strokeWidthPx
                holder.cardExercise.strokeColor = ContextCompat.getColor(
                    holder.itemView.context,
                    if (outlineComplete) R.color.superset_complete_green else incompleteOutlineColorRes
                )
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
                showNoteDialog(holder.itemView.context, groupedExercise.exerciseId, groupedExercise.exerciseName, exerciseNoteText, position)
            }
        } else {
            holder.noteTooltipButton.visibility = View.GONE
        }

        // Setup Intent Selection ChipGroup
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

        holder.completionCheck.visibility = if (isComplete) View.VISIBLE else View.GONE

        // Show sets count: "(N of X sets)" where both count only working sets; for supersets use user-defined target
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
            val ctx = holder.itemView.context
            val currentSetsText = mutableListOf<CharSequence>()
            val lastSetsText = mutableListOf<CharSequence>()
            
            // Handle warmup section alignment
            val maxWarmupSets = maxOf(currentWarmupSets.size, lastWarmupSets.size)
            if (maxWarmupSets > 0) {
                for (i in 0 until maxWarmupSets) {
                    // Current side
                    if (i < currentWarmupSets.size) {
                        currentSetsText.add(buildSetLine(ctx, "Warmup: ", currentWarmupSets[i], spaceBeforeKg = false, suffix = ""))
                    } else {
                        currentSetsText.add("(No warmup)")
                    }

                    // Last side
                    if (i < lastWarmupSets.size) {
                        val lastSet = lastWarmupSets[i]
                        val lastSuffix = when {
                            lastSet.rpe != null -> " (${formatOneDecimal(lastSet.rpe)})"
                            else -> ""
                        }
                        lastSetsText.add(buildSetLine(ctx, "Last: ", lastSet, spaceBeforeKg = true, suffix = lastSuffix))
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
                    val suffix = when {
                        currentSet.rpe != null -> " (${formatOneDecimal(currentSet.rpe)})"
                        else -> ""
                    }
                    // Use sequential working set number (1, 2, 3...) excluding warmups
                    val workingSetNumber = i + 1
                    currentSetsText.add(buildSetLine(ctx, "Set $workingSetNumber: ", currentSet, spaceBeforeKg = false, suffix = suffix))
                } else {
                    currentSetsText.add("")
                }

                // Last side
                if (i < lastWorkingSets.size) {
                    val lastSet = lastWorkingSets[i]
                    val lastSuffix = when {
                        lastSet.rpe != null -> " (${formatOneDecimal(lastSet.rpe)})"
                        else -> ""
                    }
                    lastSetsText.add(buildSetLine(ctx, "Last: ", lastSet, spaceBeforeKg = true, suffix = lastSuffix))
                } else {
                    lastSetsText.add("")
                }
            }
            
            val currentWorkingSetsCount = currentWorkingSets.size
            val lastWorkoutWorkingSetsCount = lastWorkingSets.size

            // Plan targets take priority over progression suggestion
            val planSnapshot = planSnapshots[groupedExercise.exerciseId]
            val planIndicatorText = planSnapshot?.let { buildPlanIndicatorText(it) }

            val effectiveIndicatorText: String?
            val shouldShowSuggestion: Boolean

            if (planIndicatorText != null) {
                val planComplete = planSnapshot!!.plannedSetsTarget != null &&
                    currentWorkingSetsCount >= planSnapshot.plannedSetsTarget!!
                shouldShowSuggestion = !planComplete
                effectiveIndicatorText = planIndicatorText
            } else {
                val suggestionInfo = getSuggestionInfo(holder.itemView.context, groupedExercise.exerciseId, currentIntent)
                val suggestedSetsCount = suggestionInfo?.suggestedSets ?: 3
                shouldShowSuggestion = suggestionInfo != null &&
                    currentWorkingSetsCount < suggestedSetsCount &&
                    (lastWorkoutWorkingSetsCount == 0 || currentWorkingSetsCount < lastWorkoutWorkingSetsCount)
                effectiveIndicatorText = suggestionInfo?.text
            }

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
                    ctx,
                    currentSetsText,
                    effectiveIndicatorText!!,
                    insertAfterLine = lastLoggedWorkingSetIndex  // Insert after last logged working set
                )
            } else if (shouldShowSuggestion) {
                // No sets logged yet - append at end
                buildSpannableWithSuggestion(
                    ctx,
                    currentSetsText,
                    effectiveIndicatorText!!,
                    insertAfterLine = -1  // Append at end
                )
            } else {
                joinLines(currentSetsText)
            }

            holder.loggedSets.text = displayText
            holder.loggedSets.visibility = View.VISIBLE

            // Always show last workout data when available
            if (lastWorkoutSets.isNotEmpty()) {
                holder.lastWorkoutSets.text = joinLines(lastSetsText)
                holder.lastWorkoutSets.visibility = View.VISIBLE
            } else {
                holder.lastWorkoutSets.visibility = View.GONE
            }
        } else {
            // No sets logged yet - plan targets take priority over progression suggestion
            val planSnapshot = planSnapshots[groupedExercise.exerciseId]
            val effectiveSuggestionText = planSnapshot?.let { buildPlanIndicatorText(it) }
                ?: getSuggestionForIntent(holder.itemView.context, groupedExercise.exerciseId, currentIntent)

            if (effectiveSuggestionText != null) {
                // Show suggestion in the logged sets area with colored text
                val displayText = buildSpannableWithSuggestion(holder.itemView.context, emptyList(), effectiveSuggestionText)
                holder.loggedSets.text = displayText
                holder.loggedSets.visibility = View.VISIBLE
            } else {
                holder.loggedSets.visibility = View.GONE
            }

            if (lastWorkoutSets.isNotEmpty()) {
                // Show all last workout sets (legacy: RPE 6 = warmup; new: use isWarmup only)
                val ctx = holder.itemView.context
                val lastWarmupSets = lastWorkoutSets.filter { it.isEffectivelyWarmup() }
                val lastWorkingSets = lastWorkoutSets.filter { !it.isEffectivelyWarmup() }
                val lastSetsText = mutableListOf<CharSequence>()

                // Format warmup sets, then working sets
                (lastWarmupSets + lastWorkingSets).forEach { lastSet ->
                    val lastSuffix = when {
                        lastSet.rpe != null -> " (${formatOneDecimal(lastSet.rpe)})"
                        else -> ""
                    }
                    lastSetsText.add(buildSetLine(ctx, "Last: ", lastSet, spaceBeforeKg = true, suffix = lastSuffix))
                }

                holder.lastWorkoutSets.text = joinLines(lastSetsText)
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

        // --- Collapse / Expand toggle ---
        val isCollapsed = groupedExercise.exerciseId in collapsedExercises

        // Check button: toggle collapsed state (collapse only allowed with ≥1 working set logged)
        holder.completeExerciseButton.setOnClickListener {
            if (groupedExercise.exerciseId in collapsedExercises) {
                collapsedExercises.remove(groupedExercise.exerciseId)
            } else if (canCollapseExercise) {
                collapsedExercises.add(groupedExercise.exerciseId)
            }
            notifyItemChanged(position)
        }

        if (isCollapsed) {
            // Collapsed: hide intent chips, action buttons row, note icon, previous sets column
            holder.chipGroupIntent.visibility = View.GONE
            holder.layoutActionButtons.visibility = View.GONE
            holder.noteTooltipButton.visibility = View.GONE
            holder.lastWorkoutSets.visibility = View.GONE

            // Intent emoji badge next to exercise name
            val intentEmoji = when (exerciseIntents[groupedExercise.exerciseId]) {
                SetIntent.STRENGTH -> "💥"
                SetIntent.BUILD -> "🛡️"
                SetIntent.FLUSH -> "🩸"
                else -> null
            }
            holder.intentBadge.text = intentEmoji ?: ""
            holder.intentBadge.visibility = if (intentEmoji != null) View.VISIBLE else View.GONE

            // Compact inline working sets only (no warmups, no previous)
            val workingSets = completedSets
                .filter { !it.isEffectivelyWarmup() }
                .sortedBy { it.setNumber }
            if (workingSets.isNotEmpty()) {
                val ctx = holder.itemView.context
                val compact = SpannableStringBuilder()
                workingSets.forEachIndexed { i, set ->
                    if (i > 0) compact.append("  ·  ")
                    if (set.isTimedEntry()) {
                        compact.append(RestTimerHelper.formatDuration(set.durationSeconds ?: 0))
                        if (set.kg > 0f) compact.append(" +${trimNum(set.kg)}")
                    } else {
                        compact.append(weightPortion(ctx, set))
                        compact.append("×${set.reps}")
                    }
                }
                holder.loggedSets.text = compact
                holder.loggedSets.visibility = View.VISIBLE
            } else {
                holder.loggedSets.visibility = View.GONE
            }

            // Filled green check button = "done" (only reachable with ≥1 working set; reset state for recycled holders)
            holder.completeExerciseButton.isEnabled = true
            holder.completeExerciseButton.alpha = 1f
            holder.completeExerciseButton.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.superset_complete_green)
            )
            holder.iconCompleteExercise.setColorFilter(
                ContextCompat.getColor(holder.itemView.context, android.R.color.white)
            )

            // Clicking anywhere on the card expands it again
            holder.itemView.setOnClickListener {
                collapsedExercises.remove(groupedExercise.exerciseId)
                notifyItemChanged(position)
            }
        } else {
            // Expanded: ensure all sections visible (normal state)
            holder.chipGroupIntent.visibility = View.VISIBLE
            holder.layoutActionButtons.visibility = View.VISIBLE
            holder.intentBadge.visibility = View.GONE

            // Reset check button to outline style
            holder.completeExerciseButton.isEnabled = canCollapseExercise
            holder.completeExerciseButton.alpha = if (canCollapseExercise) 1f else 0.5f
            holder.completeExerciseButton.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.fitness_card_background)
            )
            holder.iconCompleteExercise.setColorFilter(
                android.graphics.Color.parseColor("#4CAF50")
            )
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

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is SpecialElementViewHolder && holder.boundExerciseId >= 0) {
            cardTimers[holder.boundExerciseId]?.cancel()
            cardTimers.remove(holder.boundExerciseId)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        cardTimers.values.forEach { it?.cancel() }
        cardTimers.clear()
    }

    private fun showNoteDialog(context: Context, exerciseId: Int, exerciseName: String, noteText: String, position: Int) {
        DialogHelper.createBuilder(context)
            .setTitle(exerciseName)
            .setMessage(noteText)
            .setPositiveButton(context.getString(R.string.button_ok), null)
            .setNeutralButton(context.getString(R.string.button_edit_note)) { _, _ ->
                showEditNoteDialog(context, exerciseId, exerciseName, noteText, position)
            }
            .showWithTransparentWindow()
    }

    private fun showEditNoteDialog(context: Context, exerciseId: Int, exerciseName: String, currentNote: String, position: Int) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_note, null)
        val editText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_text_note)
        editText.setText(currentNote)
        editText.setSelection(currentNote.length)

        DialogHelper.createBuilder(context)
            .setTitle(exerciseName)
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.button_save)) { _, _ ->
                val newNote = editText.text.toString().trim()
                saveExerciseNote(exerciseId, newNote, position)
            }
            .setNegativeButton(context.getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun saveExerciseNote(exerciseId: Int, newNote: String, position: Int) {
        val trainingData = jsonHelper.readTrainingData()
        val index = trainingData.exerciseLibrary.indexOfFirst { it.id == exerciseId }
        if (index != -1) {
            val item = trainingData.exerciseLibrary[index]
            trainingData.exerciseLibrary[index] = item.copy(note = newNote.takeIf { it.isNotEmpty() })
            jsonHelper.writeTrainingData(trainingData)
        }
        notifyItemChanged(position)
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
    
    private fun buildPlanIndicatorText(snapshot: DraftExerciseRow): String? {
        if (!snapshot.fromPlan) return null
        val sets = snapshot.plannedSetsTarget
        val rpe = snapshot.plannedRpeTarget
        // Timed slots render the target as a duration instead of reps.
        val durationSeconds = snapshot.plannedDurationSeconds?.takeIf { it > 0 }
        val reps = if (durationSeconds != null) null
                   else snapshot.plannedRepsTarget?.takeIf { it.isNotBlank() }
        if (sets == null && reps == null && rpe == null && durationSeconds == null) return null

        val sb = StringBuilder("Plan: ")
        val target = durationSeconds?.let { RestTimerHelper.formatDuration(it) } ?: reps
        when {
            sets != null && target != null -> sb.append("${sets}×${target}")
            sets != null -> sb.append("$sets sets")
            target != null -> sb.append(if (durationSeconds != null) target else "$target reps")
        }
        if (rpe != null) {
            if (sets != null || target != null) sb.append(" ")
            sb.append("@ RPE ${formatOneDecimal(rpe)}")
        }
        return sb.toString()
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
        val settings = progressionSettings(context)
        
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
                "Target: ${suggestion.suggestedSets ?: 3}×$minReps @ RPE ${formatOneDecimal(targetRpe)}"
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
    
    private fun trimNum(v: Float): String =
        if (v % 1 == 0f) v.toInt().toString() else v.toString()

    /**
     * The weight portion of a set line. Plain text for weighted sets. For bodyweight sets the body
     * weight is shown muted (1 decimal) and the signed added/assisted weight in a contrasting color
     * (green +, red −) so progression is readable even when body weight differs between workouts.
     */
    private fun weightPortion(context: Context, set: com.liftpath.models.ExerciseEntry): CharSequence {
        if (!set.isBodyweightEntry()) {
            return trimNum(set.kg)
        }
        val bw = set.bodyweightKg ?: 0f
        val added = set.addedKg ?: 0f
        val b = SpannableStringBuilder()
        b.append(String.format(Locale.US, "%.1f", bw))
        b.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.fitness_text_secondary)),
            0, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        if (added != 0f) {
            val sign = if (added > 0f) "+" else "−"
            val mag = if (added < 0f) -added else added
            val start = b.length
            b.append(" $sign${trimNum(mag)}")
            val colorRes = if (added > 0f) R.color.fitness_highlight_border else R.color.fitness_error_border
            b.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, colorRes)), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            b.setSpan(StyleSpan(Typeface.BOLD), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return b
    }

    /** A full set line: "<prefix><weight portion>kg × <reps><suffix>". */
    private fun buildSetLine(
        context: Context,
        prefix: String,
        set: com.liftpath.models.ExerciseEntry,
        spaceBeforeKg: Boolean,
        suffix: String
    ): CharSequence {
        val b = SpannableStringBuilder()
        b.append(prefix)
        // Timed holds render the duration, with weight appended only when present.
        if (set.isTimedEntry()) {
            b.append(RestTimerHelper.formatDuration(set.durationSeconds ?: 0))
            if (set.kg > 0f) b.append(" + ${trimNum(set.kg)} kg")
            b.append(suffix)
            return b
        }
        b.append(weightPortion(context, set))
        b.append(if (spaceBeforeKg) " kg × ${set.reps}$suffix" else "kg × ${set.reps}$suffix")
        return b
    }

    private fun joinLines(lines: List<CharSequence>): CharSequence {
        val b = SpannableStringBuilder()
        lines.forEachIndexed { i, line ->
            if (i > 0) b.append("\n")
            b.append(line)
        }
        return b
    }

    /**
     * Build the logged-sets text (preserving any per-line color spans) and optionally insert a
     * colored suggestion line.
     * @param insertAfterLine If >= 0, inserts suggestion after that line index. Otherwise appends at end.
     */
    private fun buildSpannableWithSuggestion(
        context: Context,
        lines: List<CharSequence>,
        suggestionText: String?,
        insertAfterLine: Int = -1
    ): CharSequence {
        if (suggestionText == null) {
            return joinLines(lines)
        }

        val builder = SpannableStringBuilder()
        val suggestionColor = ContextCompat.getColor(context, R.color.fitness_suggestion)

        if (lines.isEmpty()) {
            val start = builder.length
            builder.append(suggestionText)
            builder.setSpan(ForegroundColorSpan(suggestionColor), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return builder
        }

        val insertIndex = if (insertAfterLine in 0 until lines.size) insertAfterLine + 1 else lines.size

        for (i in lines.indices) {
            if (i > 0) builder.append("\n")
            builder.append(lines[i])

            if (i == insertIndex - 1) {
                builder.append("\n")
                val start = builder.length
                builder.append(suggestionText)
                builder.setSpan(ForegroundColorSpan(suggestionColor), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        return builder
    }
}