package com.liftpath.adapters

import android.content.Context
import android.os.CountDownTimer
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.liftpath.R
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.ProgressionHelper
import com.liftpath.helpers.ProgressionSettingsManager
import com.liftpath.helpers.RestTimerHelper
import com.liftpath.helpers.CircuitStore
import com.liftpath.helpers.SetFormatter
import com.liftpath.helpers.WorkoutGenerator
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.DraftExerciseRow
import com.liftpath.models.GroupedExercise
import com.liftpath.models.PlanSlotType
import com.liftpath.models.SetIntent
import com.google.android.material.chip.ChipGroup
import java.util.Locale
import com.liftpath.helpers.lpColor

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
    private val onSpecialTimerReset: (exerciseId: Int) -> Unit = {},
    private val onStartCircuitClicked: (position: Int) -> Unit = {},
    private val onDeleteCircuitClicked: (exerciseId: Int) -> Unit = {},
    private val onCircuitSettingsClicked: (position: Int) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val VIEW_TYPE_EXERCISE    = 0
        private const val VIEW_TYPE_ADD_BUTTONS = 1
        private const val VIEW_TYPE_SPECIAL     = 2
        private const val VIEW_TYPE_CIRCUIT     = 3
    }

    private val cardTimers = mutableMapOf<Int, CountDownTimer?>()

    /** Always use '.' as decimal separator (locale-independent) for workout numbers. */
    private fun formatOneDecimal(value: Float): String = String.format(Locale.US, "%.1f", value)

    /**
     * Exercises the user has ticked off with the check button.
     *
     * Deliberately separate from [expandedExerciseId]: this is a *session fact* — "I'm done
     * with this one, whatever the target says" — and it drives the card outline and the
     * auto-advance. Expansion is pure view state and carries no meaning.
     */
    private val manuallyCompleted = mutableSetOf<Int>()

    /**
     * The one exercise showing its full controls; null means everything is collapsed.
     *
     * One-at-a-time is the whole point of the card: the always-expanded version fitted about
     * one and a half exercises on screen, so you scrolled to find your place instead of
     * seeing the session.
     */
    private var expandedExerciseId: Int? = null
    private var hasAutoExpanded = false

    /** Held only so expansion can scroll the newly-opened card into view. */
    private var attachedRecyclerView: RecyclerView? = null

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

    // ------------------------------------------------------------------ completion state

    /** Working sets actually logged: warmups excluded, and a set counts once it has load or is ticked. */
    private fun workingSetsLogged(exercise: GroupedExercise): Int =
        exercise.sets.count { !it.isEffectivelyWarmup() && (it.kg > 0f || it.completed == true) }

    /**
     * How many working sets this exercise is aiming for. A superset's shared target wins over
     * the generator's recommendation, which in turn wins over "however many you did last time".
     * Zero means there is no target, so the card can never read as complete on its own.
     */
    private fun targetSetsFor(exercise: GroupedExercise): Int {
        val supersetTarget = getSupersetTargetSets(exercise.supersetGroupId)
        val recommended = exerciseRecommendations[exercise.exerciseId]?.recommendedSets
        val lastWorkingSets = exerciseIntents[exercise.exerciseId]
            ?.let { lastWorkoutData[exercise.exerciseId]?.get(it) }
            ?.count { !it.isEffectivelyWarmup() }
            ?: 0
        return supersetTarget ?: recommended ?: lastWorkingSets
    }

    /** Target met, or the user said so by tapping the check. */
    private fun isExerciseDone(exercise: GroupedExercise): Boolean {
        if (exercise.exerciseId in manuallyCompleted) return true
        val target = targetSetsFor(exercise)
        return target > 0 && workingSetsLogged(exercise) >= target
    }

    // ------------------------------------------------------------------ expansion state

    /**
     * Opens the first exercise that still has work left.
     *
     * Called at the top of [onBindViewHolder] rather than from a lifecycle callback because the
     * exercise list is populated before the first bind but not necessarily before attach. It
     * settles on the very first bind of a pass, so every holder in that pass reads a final
     * value and no notify is needed.
     */
    private fun ensureAutoExpanded() {
        if (hasAutoExpanded || groupedExercises.isEmpty()) return
        hasAutoExpanded = true
        val realExercises = groupedExercises.filter { !it.isSpecialElement && !it.isCircuit }
        expandedExerciseId = (realExercises.firstOrNull { !isExerciseDone(it) } ?: realExercises.firstOrNull())
            ?.exerciseId
    }

    private fun indexOfExercise(exerciseId: Int): Int =
        groupedExercises.indexOfFirst { it.exerciseId == exerciseId }

    /** Moves the single expansion to [exerciseId], or closes everything when null. */
    private fun setExpanded(exerciseId: Int?) {
        val previous = expandedExerciseId
        if (previous == exerciseId) return
        expandedExerciseId = exerciseId
        hasAutoExpanded = true

        previous?.let { id -> indexOfExercise(id).takeIf { it >= 0 }?.let(::notifyItemChanged) }
        val newPosition = exerciseId?.let { indexOfExercise(it) } ?: -1
        if (newPosition >= 0) {
            notifyItemChanged(newPosition)
            // The card that just grew is often the one that just left the viewport.
            attachedRecyclerView?.post { attachedRecyclerView?.smoothScrollToPosition(newPosition) }
        }
    }

    /**
     * Hands the expansion to the next exercise with work left after ticking one off.
     *
     * Scans forward first so an ordinary top-to-bottom session simply walks down the list, then
     * wraps to the start so an exercise skipped earlier isn't stranded closed.
     */
    private fun advanceExpansionPast(position: Int) {
        val order = (position + 1 until groupedExercises.size) + (0 until position)
        val next = order
            .map { groupedExercises[it] }
            .firstOrNull { !it.isSpecialElement && !it.isCircuit && !isExerciseDone(it) }
        setExpanded(next?.exerciseId)
    }

    class GroupedExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardExercise: MaterialCardView = view.findViewById(R.id.card_exercise)

        // --- header: always visible, tapping it toggles expansion ---
        val header: View = view.findViewById(R.id.layout_header)
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val intentBadge: TextView = view.findViewById(R.id.text_intent_badge)
        val swapExerciseButton: android.widget.ImageButton = view.findViewById(R.id.button_swap_exercise)
        val noteTooltipButton: android.widget.ImageButton = view.findViewById(R.id.button_note_tooltip)
        val collapsedSummary: TextView = view.findViewById(R.id.text_collapsed_summary)
        val completionCheck: ImageView = view.findViewById(R.id.image_completion_check)
        val setsCount: TextView = view.findViewById(R.id.text_sets_count)
        val chevron: ImageView = view.findViewById(R.id.image_expand_chevron)

        // --- body: shown for the single expanded exercise ---
        val body: View = view.findViewById(R.id.layout_body)
        val chipGroupIntent: ChipGroup = view.findViewById(R.id.chip_group_intent)
        val chipStrength: com.google.android.material.chip.Chip = view.findViewById(R.id.chip_strength)
        val chipBuild: com.google.android.material.chip.Chip = view.findViewById(R.id.chip_build)
        val chipFlush: com.google.android.material.chip.Chip = view.findViewById(R.id.chip_flush)
        val setComparison: View = view.findViewById(R.id.layout_set_comparison)
        val loggedSets: TextView = view.findViewById(R.id.text_logged_sets)
        val lastWorkoutSets: TextView = view.findViewById(R.id.text_last_workout_sets)
        val addSetButton: MaterialCardView = view.findViewById(R.id.button_add_set)
        val duplicateSetButton: MaterialCardView = view.findViewById(R.id.button_duplicate_set)
        val editActivityButton: MaterialCardView = view.findViewById(R.id.button_edit_activity)
        val completeExerciseButton: MaterialCardView = view.findViewById(R.id.button_complete_exercise)
        val iconCompleteExercise: ImageView = view.findViewById(R.id.icon_complete_exercise)
        val deleteExerciseButton: MaterialCardView = view.findViewById(R.id.button_delete_exercise)

        val supersetLinkTop: android.widget.ImageButton = view.findViewById(R.id.button_superset_link_top)
        val supersetLinkBottom: android.widget.ImageButton = view.findViewById(R.id.button_superset_link_bottom)
    }
    
    class AddButtonsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val regularPlusButton: MaterialButton = view.findViewById(R.id.button_add_exercise_regular)
        val bonusPlusButton: MaterialButton = view.findViewById(R.id.button_add_exercise_bonus)
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

    class CircuitViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.text_circuit_name)
        val summary: TextView = view.findViewById(R.id.text_circuit_summary)
        val roundsBadge: TextView = view.findViewById(R.id.text_rounds_badge)
        val stations: TextView = view.findViewById(R.id.text_circuit_stations)
        val pending: TextView = view.findViewById(R.id.text_circuit_pending)
        val deleteButton: android.widget.ImageButton = view.findViewById(R.id.button_delete_circuit)
        val runCard: MaterialCardView = view.findViewById(R.id.card_run_circuit)
        val runText: TextView = view.findViewById(R.id.text_run_circuit)
    }

    override fun getItemViewType(position: Int): Int = when {
        position == groupedExercises.size -> VIEW_TYPE_ADD_BUTTONS
        groupedExercises[position].isCircuit -> VIEW_TYPE_CIRCUIT
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
            VIEW_TYPE_CIRCUIT -> CircuitViewHolder(
                inflater.inflate(R.layout.list_item_active_circuit, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        ensureAutoExpanded()
        when (holder) {
            is SpecialElementViewHolder -> bindSpecialViewHolder(holder, position)
            is GroupedExerciseViewHolder -> bindExerciseViewHolder(holder, position)
            is AddButtonsViewHolder -> bindAddButtonsViewHolder(holder)
            is CircuitViewHolder -> bindCircuitViewHolder(holder, position)
        }
    }

    private fun bindCircuitViewHolder(holder: CircuitViewHolder, position: Int) {
        val group = groupedExercises[position]
        val circuit = group.circuit ?: return
        val ctx = holder.itemView.context

        holder.name.text = group.exerciseName
        holder.summary.text = CircuitStore.formatSummary(circuit.suggestedRounds, circuit.restBetweenRoundsSeconds)
        holder.summary.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) onCircuitSettingsClicked(pos)
        }

        if (circuit.completedRounds > 0) {
            holder.roundsBadge.text = ctx.getString(R.string.circuit_rounds_done, circuit.completedRounds)
            holder.roundsBadge.visibility = View.VISIBLE
        } else {
            holder.roundsBadge.visibility = View.GONE
        }

        val library = jsonHelper.readTrainingData().exerciseLibrary
        val nameById = library.associate { it.id to it.name }
        val stationNames = circuit.items.mapNotNull { nameById[it.exerciseId] }
        holder.stations.text = if (stationNames.isEmpty()) {
            ctx.getString(R.string.circuit_no_stations)
        } else {
            stationNames.joinToString(" · ")
        }

        val pendingRounds = circuit.pendingRounds
        if (pendingRounds.isNotEmpty()) {
            holder.pending.text = ctx.getString(R.string.circuit_round_not_logged, pendingRounds.first())
            holder.pending.visibility = View.VISIBLE
        } else {
            holder.pending.visibility = View.GONE
        }

        holder.deleteButton.setOnClickListener { onDeleteCircuitClicked(group.exerciseId) }
        holder.runText.setText(
            if (circuit.completedRounds > 0) R.string.btn_continue_circuit else R.string.btn_start_circuit
        )
        holder.runCard.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) onStartCircuitClicked(pos)
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

        val primaryColor = ctx.lpColor(R.attr.lpAccent)
        val greenColor = ctx.lpColor(R.attr.lpPositive)
        val grayColor = ctx.lpColor(R.attr.lpInkSecondary)
        // A running slot gets the same 2dp emphasis ring the exercise cards use; when it is not
        // running the card falls back to the hairline rather than losing its edge entirely.
        val emphasisedStrokePx = (2 * ctx.resources.displayMetrics.density).toInt()
        val hairlineStrokePx = ctx.resources.getDimensionPixelSize(R.dimen.lp_hairline_width)
        val hairlineColor = ctx.lpColor(R.attr.lpHairline)

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
                holder.cardSpecial.strokeWidth = emphasisedStrokePx
                holder.cardSpecial.strokeColor = greenColor
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

                holder.cardSpecial.strokeWidth = emphasisedStrokePx
                holder.cardSpecial.strokeColor = greenColor

                holder.progressTimer.visibility = View.VISIBLE

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
                holder.cardSpecial.strokeWidth = hairlineStrokePx
                holder.cardSpecial.strokeColor = hairlineColor
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
        val ctx = holder.itemView.context
        val isExpanded = expandedExerciseId == groupedExercise.exerciseId

        holder.exerciseName.text = groupedExercise.exerciseName
        holder.body.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.chevron.rotation = if (isExpanded) 180f else 0f

        // Swapping and the note/illustration are only offered on the open card — in the
        // collapsed row they would be four icons of chrome per exercise.
        val canSwap = isExpanded && groupedExercise.isFamilySlot && onChangeExerciseClicked != null
        holder.swapExerciseButton.visibility = if (canSwap) View.VISIBLE else View.GONE
        holder.swapExerciseButton.setOnClickListener(
            if (canSwap) {
                { _: View ->
                    val pos = holder.bindingAdapterPosition
                    if (pos >= 0) onChangeExerciseClicked!!.invoke(pos)
                }
            } else null
        )

        holder.header.setOnClickListener {
            setExpanded(if (expandedExerciseId == groupedExercise.exerciseId) null else groupedExercise.exerciseId)
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
        val currentWorkingSets = workingSetsLogged(groupedExercise)
        val targetSets = targetSetsFor(groupedExercise)
        val isComplete = targetSets > 0 && currentWorkingSets >= targetSets
        // Any logged set counts (warmup or working): kg > 0 or explicitly completed
        val hasAnyRegisteredSet = completedSets.isNotEmpty()
        val canMarkDone = currentWorkingSets > 0
        // "Done" can't survive the last set being deleted.
        if (groupedExercise.exerciseId in manuallyCompleted && !canMarkDone) {
            manuallyCompleted.remove(groupedExercise.exerciseId)
        }
        val isMarkedDone = groupedExercise.exerciseId in manuallyCompleted
        // Card outline: target met, or the user said so by tapping the check.
        val outlineComplete = isComplete || isMarkedDone

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

        // The card's whole state vocabulary is its outline: hairline when idle, a 2dp accent
        // ring once work has started, 2dp positive when done. Colours are carried as @AttrRes
        // and resolved with lpColor so they follow the selected palette.
        val restingOutlineAttr = when {
            outlineComplete -> R.attr.lpPositive
            hasAnyRegisteredSet -> R.attr.lpAccent
            else -> R.attr.lpHairline
        }
        val emphasisedStrokePx = (2 * holder.itemView.resources.displayMetrics.density).toInt()
        val hairlineStrokePx = holder.itemView.resources.getDimensionPixelSize(R.dimen.lp_hairline_width)
        val (outlineAttr, cardAlpha) = when {
            isSupersetComplete -> R.attr.lpPositive to 1f
            isSelectedForSuperset -> R.attr.lpAccent to 1f
            // Queued behind a superset partner: dimmed, but never left with no edge at all.
            isInSuperset && !canAddSet && !hasSupersetReachedTarget && !isActive && !isWaitingForTimer ->
                R.attr.lpHairline to 0.5f
            isWaitingForTimer && !outlineComplete && hasAnyRegisteredSet -> R.attr.lpIntentBuild to 1f
            else -> restingOutlineAttr to 1f
        }
        holder.cardExercise.strokeColor = ctx.lpColor(outlineAttr)
        holder.cardExercise.strokeWidth =
            if (outlineAttr == R.attr.lpHairline) hairlineStrokePx else emphasisedStrokePx
        holder.cardExercise.alpha = cardAlpha

        holder.addSetButton.isEnabled = canAddSet
        holder.addSetButton.alpha = if (canAddSet) 1f else 0.4f
        holder.duplicateSetButton.isEnabled = canAddSet
        holder.duplicateSetButton.alpha = if (canAddSet) 1f else 0.4f
        // The header owns tap; the card itself only listens for the superset long-press, so it
        // must not also ripple as though the whole surface were a button.
        holder.cardExercise.isClickable = false
        holder.cardExercise.isFocusable = false

        holder.itemView.setOnLongClickListener {
            onExerciseLongPress(position)
            true
        }

        // Get exercise note and illustration from library
        val trainingData = jsonHelper.readTrainingData()
        val exerciseLibraryItem = trainingData.exerciseLibrary.find { it.id == groupedExercise.exerciseId }
        val exerciseNoteText = exerciseLibraryItem?.note?.takeIf { it.isNotEmpty() } ?: ""
        val illustrationRes = exerciseLibraryItem?.illustrationRes

        // The stick figure (and note, if any) is offered on the open card — which is the one
        // you're about to perform, so it is the only card where it is worth the space.
        holder.noteTooltipButton.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.noteTooltipButton.setOnClickListener {
            showNoteDialog(ctx, groupedExercise.exerciseId, groupedExercise.exerciseName, exerciseNoteText, illustrationRes, position)
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
            val grey = ctx.lpColor(R.attr.lpInkTertiary)
            // A state list, not a flat colour: Widget.LP.Chip.Choice fills the CHECKED chip with
            // ink, so a flat lpInk label would be ink-on-ink and vanish. The greyed chips are
            // safe as a flat colour because a locked non-matching chip is never the checked one.
            val normal = androidx.core.content.ContextCompat.getColorStateList(ctx, R.color.lp_chip_text)

            fun applyChipState(
                chip: com.google.android.material.chip.Chip,
                @androidx.annotation.StringRes labelRes: Int,
                intent: SetIntent,
            ) {
                val baseLabel = ctx.getString(labelRes)
                chip.text = if (lastIntent == intent) {
                    ctx.getString(R.string.intent_chip_last_suffix, baseLabel)
                } else {
                    baseLabel
                }

                val shouldGrey = isLocked && lockedIntent != intent
                chip.alpha = if (shouldGrey) 0.45f else 1.0f
                if (shouldGrey) chip.setTextColor(grey) else chip.setTextColor(normal)
            }

            applyChipState(holder.chipStrength, R.string.intent_strength_chip, SetIntent.STRENGTH)
            applyChipState(holder.chipBuild, R.string.intent_build_chip, SetIntent.BUILD)
            applyChipState(holder.chipFlush, R.string.intent_flush_chip, SetIntent.FLUSH)
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

        // "2/3" rather than "(2 of 3 sets)": in the collapsed header this sits at the end of a
        // row that also carries the name, and mono keeps the column from twitching as it counts up.
        holder.setsCount.text = when {
            targetSets > 0 -> "$currentWorkingSets/$targetSets"
            currentWorkingSets > 0 -> "$currentWorkingSets"
            else -> ""
        }
        holder.setsCount.setTextColor(
            ctx.lpColor(if (outlineComplete) R.attr.lpPositive else R.attr.lpInkTertiary)
        )

        // --- collapsed row content: intent shorthand + this session's working sets ---

        val intentEmoji = when (exerciseIntents[groupedExercise.exerciseId]) {
            SetIntent.STRENGTH -> "💥"
            SetIntent.BUILD -> "🛡️"
            SetIntent.FLUSH -> "🩸"
            else -> null
        }
        holder.intentBadge.text = intentEmoji ?: ""
        holder.intentBadge.visibility =
            if (!isExpanded && intentEmoji != null) View.VISIBLE else View.GONE

        val summarySets = completedSets
            .filter { !it.isEffectivelyWarmup() }
            .sortedBy { it.setNumber }
        if (!isExpanded && summarySets.isNotEmpty()) {
            val compact = SpannableStringBuilder()
            summarySets.forEachIndexed { i, set ->
                if (i > 0) compact.append("  ·  ")
                compact.append(SetFormatter.compact(ctx, set))
            }
            holder.collapsedSummary.text = compact
            holder.collapsedSummary.visibility = View.VISIBLE
        } else {
            holder.collapsedSummary.visibility = View.GONE
        }

        // The logged-vs-last comparison is the expensive part of this bind — several set lines,
        // a progression lookup and a spannable build — so it is skipped entirely for the cards
        // that are collapsed, which is all but one of them.
        if (isExpanded) {
            bindSetComparison(holder, groupedExercise, completedSets, lastWorkoutSets, currentIntent, hasSets)
        }

        // Visibility logic
        holder.duplicateSetButton.visibility = if (hasSets) View.VISIBLE else View.GONE
        holder.editActivityButton.visibility = if (hasSets) View.VISIBLE else View.GONE
        holder.deleteExerciseButton.visibility = View.VISIBLE // Always show delete button

        // --- CLICK LISTENERS ---

        // Add Set — only when canAddSet (active or waiting in a superset)
        holder.addSetButton.setOnClickListener {
            if (canAddSet) onAddSetClicked(groupedExercise.exerciseId, groupedExercise.exerciseName)
        }

        // Duplicate Last Set — same gate
        holder.duplicateSetButton.setOnClickListener {
            if (canAddSet) onDuplicateSetClicked(groupedExercise.exerciseId)
        }

        holder.editActivityButton.setOnClickListener { onEditActivityClicked(groupedExercise) }

        holder.deleteExerciseButton.setOnClickListener {
            onDeleteExerciseClicked(groupedExercise.exerciseId)
        }

        // Tapping the set list is the shortcut into the editor — the header owns expansion, so
        // the old "tap anywhere on the card" gesture would now be ambiguous.
        holder.setComparison.setOnClickListener {
            if (hasSets) {
                onEditActivityClicked(groupedExercise)
            } else if (canAddSet) {
                onAddSetClicked(groupedExercise.exerciseId, groupedExercise.exerciseName)
            }
        }

        // --- The check: mark done, and hand the expansion to whatever is next ---

        holder.completeExerciseButton.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos < 0) return@setOnClickListener
            val id = groupedExercise.exerciseId
            if (id in manuallyCompleted) {
                manuallyCompleted.remove(id)
                notifyItemChanged(pos)
                setExpanded(id)
            } else if (canMarkDone) {
                manuallyCompleted.add(id)
                notifyItemChanged(pos)
                advanceExpansionPast(pos)
            }
        }

        holder.completeExerciseButton.isEnabled = isMarkedDone || canMarkDone
        holder.completeExerciseButton.alpha = if (holder.completeExerciseButton.isEnabled) 1f else 0.4f
        if (isMarkedDone) {
            holder.completeExerciseButton.setCardBackgroundColor(ctx.lpColor(R.attr.lpPositive))
            holder.iconCompleteExercise.setColorFilter(ctx.lpColor(R.attr.lpInkInverse))
        } else {
            holder.completeExerciseButton.setCardBackgroundColor(ctx.lpColor(R.attr.lpSurface))
            holder.iconCompleteExercise.setColorFilter(ctx.lpColor(R.attr.lpPositive))
        }
    }

    /**
     * Fills the expanded card's logged-sets column and the matching "last time" column beside it.
     *
     * The two lists are padded against each other line for line — warmups first, then working
     * sets — so set 2 always sits opposite last session's set 2 even when the counts differ.
     * The progression (or plan) suggestion is spliced in directly under the last set logged,
     * which is where you look next.
     */
    private fun bindSetComparison(
        holder: GroupedExerciseViewHolder,
        groupedExercise: GroupedExercise,
        completedSets: List<com.liftpath.models.ExerciseEntry>,
        lastWorkoutSets: List<com.liftpath.models.ExerciseEntry>,
        currentIntent: SetIntent?,
        hasSets: Boolean
    ) {
        val ctx = holder.itemView.context

        if (hasSets && completedSets.isNotEmpty()) {
            val sortedCurrentSets = completedSets.sortedBy { it.setNumber }
            
            // Separate into warmup and working sets (legacy: RPE 6 = warmup; new data: use isWarmup only)
            val currentWarmupSets = sortedCurrentSets.filter { it.isEffectivelyWarmup() }
            val currentWorkingSets = sortedCurrentSets.filter { !it.isEffectivelyWarmup() }
            val lastWarmupSets = lastWorkoutSets.filter { it.isEffectivelyWarmup() }
            val lastWorkingSets = lastWorkoutSets.filter { !it.isEffectivelyWarmup() }
            
            // Build display text with matching
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
                val suggestionInfo = getSuggestionInfo(ctx, groupedExercise.exerciseId, currentIntent)
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
                ?: getSuggestionForIntent(ctx, groupedExercise.exerciseId, currentIntent)

            if (effectiveSuggestionText != null) {
                // Show suggestion in the logged sets area with colored text
                val displayText = buildSpannableWithSuggestion(ctx, emptyList(), effectiveSuggestionText)
                holder.loggedSets.text = displayText
                holder.loggedSets.visibility = View.VISIBLE
            } else {
                holder.loggedSets.visibility = View.GONE
            }

            if (lastWorkoutSets.isNotEmpty()) {
                // Show all last workout sets (legacy: RPE 6 = warmup; new: use isWarmup only)
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

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        attachedRecyclerView = null
        cardTimers.values.forEach { it?.cancel() }
        cardTimers.clear()
    }

    private fun showNoteDialog(
        context: Context,
        exerciseId: Int,
        exerciseName: String,
        noteText: String,
        illustrationRes: Int?,
        position: Int
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_exercise_note_with_media, null)
        dialogView.findViewById<TextView>(R.id.text_dialog_title).text = exerciseName

        // Show the exercise stick-figure illustration (falls back to a generic icon).
        val mediaImageView = dialogView.findViewById<ImageView>(R.id.image_exercise_media)
        dialogView.findViewById<TextView>(R.id.text_media_attribution).visibility = View.GONE
        mediaImageView.visibility = View.VISIBLE
        mediaImageView.setImageResource(illustrationRes ?: R.drawable.ic_dumbbell)

        val noteTextView = dialogView.findViewById<TextView>(R.id.text_dialog_note)
        if (noteText.isNotEmpty()) {
            noteTextView.visibility = View.VISIBLE
            noteTextView.text = noteText
        } else {
            noteTextView.visibility = View.GONE
        }

        DialogHelper.createBuilder(context)
            .setView(dialogView)
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
    
    private fun trimNum(v: Float): String = SetFormatter.trimNum(v)

    /**
     * A full set line. All load/metric rendering (weighted, bodyweight, timed hold, bodyweight hold)
     * lives in [SetFormatter] so this list and the history detail screen can't drift apart.
     */
    private fun buildSetLine(
        context: Context,
        prefix: String,
        set: com.liftpath.models.ExerciseEntry,
        spaceBeforeKg: Boolean,
        suffix: String
    ): CharSequence = SetFormatter.setLine(
        context = context,
        e = set,
        prefix = prefix,
        suffix = suffix,
        spaceBeforeUnit = spaceBeforeKg
    )

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
        val suggestionColor = context.lpColor(R.attr.lpAccent)

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