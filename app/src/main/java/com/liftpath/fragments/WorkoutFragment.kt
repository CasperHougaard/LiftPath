package com.liftpath.fragments

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.liftpath.R
import com.liftpath.activities.ActiveTrainingActivity
import com.liftpath.activities.MainActivity
import com.liftpath.activities.SettingsActivity
import com.liftpath.activities.StretchCooldownActivity
import com.liftpath.activities.TrainingDetailActivity
import com.liftpath.adapters.ProgressPagerAdapter
import com.liftpath.components.SelectWorkoutModeBottomSheet
import com.liftpath.databinding.FragmentWorkoutBinding
import com.liftpath.databinding.ItemUpNextSlotBinding
import com.liftpath.helpers.ActiveWorkoutDraftManager
import com.liftpath.helpers.BackupScheduler
import com.liftpath.helpers.BodyWeightDialogs
import com.liftpath.helpers.BodyWeightHelper
import com.liftpath.helpers.CatalogMergeHelper
import com.liftpath.helpers.CircuitStore
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.DurationHelper
import com.liftpath.helpers.HealthConnectHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.Motion
import com.liftpath.helpers.MuscleMapColorResolver
import com.liftpath.helpers.MuscleMapRenderer
import com.liftpath.helpers.PlanRotationHelper
import com.liftpath.helpers.ProgressAnalysisHelper
import com.liftpath.helpers.SetMetrics
import com.liftpath.helpers.TriPathConnection
import com.liftpath.helpers.TriPathSyncHelper
import com.liftpath.helpers.WithingsHealthConnectHelper
import com.liftpath.helpers.lpColor
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.ActiveWorkoutDraft
import com.liftpath.models.PlanSet
import com.liftpath.models.SetIntent
import com.liftpath.models.TargetMuscle
import com.liftpath.models.TrainingData
import com.liftpath.models.TrainingSession
import com.liftpath.models.WorkoutPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * The WORKOUT tab: start or resume a session, and the little context you need to decide how hard
 * to go at it.
 *
 * Was TodayFragment, and before that MainActivity itself. The rename is not cosmetic — the screen
 * used to double as a launcher for four destinations that now live in the bottom bar, and as a
 * miniature analytics dashboard duplicating Progress. Both jobs are gone: the trend charts and the
 * build/strength RPE card moved to [ProgressOverviewFragment], and the body-scan card was always a
 * smaller copy of Progress > Body Scan.
 *
 * What is left is one decision (the hero) plus the four facts that inform it: recovery, what the
 * next planned session contains, when the last one was, and whether the last three weeks add up.
 *
 * This fragment also owns the app's launch-time chores — periodic-backup registration, the catalog
 * merge offer, Health Connect / Withings auto-sync, the body-weight prompt — because it is the tab
 * the app opens on. They are unchanged from TodayFragment.
 */
class WorkoutFragment : Fragment() {

    private var _binding: FragmentWorkoutBinding? = null
    private val binding get() = _binding!!
    private lateinit var jsonHelper: JsonHelper
    private lateinit var draftManager: ActiveWorkoutDraftManager

    /** What the hero will do when tapped. Rebuilt on every [refresh]. */
    private var heroAction: (() -> Unit)? = null

    companion object {
        /** Days in the momentum strip. One dot per day, so it has to fit the gutter. */
        private const val MOMENTUM_DAYS = 21

        /** 21 dots at 8dp with 4dp gaps = 248dp, which fits a 320dp screen's card. */
        private const val DOT_SIZE_DP = 8
        private const val DOT_GAP_DP = 4

        /** Up-next rows shown before collapsing the rest into "+N more". */
        private const val UP_NEXT_MAX_ROWS = 4

        /** Body status figure lookback for per-muscle effort intensity. */
        private const val BODY_STATUS_DAYS = 14

        /** Muscles named in the body status card's summary line. */
        private const val BODY_STATUS_SUMMARY_MUSCLES = 3

        private const val SESSION_DATE_PATTERN = "yyyy/MM/dd"

        /** Between detail fragments on a card, e.g. "Pull A · 6 exercises · 8,240 kg". */
        private const val DETAIL_SEPARATOR = " · "
    }

    /** The bottom-nav host, or null if this fragment is detached mid-callback. */
    private fun host(): MainActivity? = activity as? MainActivity

    private val startWorkoutForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Called when ActiveTrainingActivity finishes; everything below the hero is now stale.
        jsonHelper.invalidateTrainingDataCache()
        refresh()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentWorkoutBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Edge-to-edge and window insets are the host's job (MainActivity), so this fragment
        // never touches either. It just fills the container it is given.

        jsonHelper = JsonHelper(requireContext())
        draftManager = ActiveWorkoutDraftManager(requireContext())
        // Daily safety-net backup. Idempotent, so re-registering on every launch is fine and
        // also repairs the schedule after a reinstall or a force-stop.
        BackupScheduler.ensurePeriodicBackup(requireContext())

        setupClickListeners()
        runEntranceAnimations()
        refresh()

        binding.root.post {
            // Guard: post() can outlive the view if the user switches tabs immediately.
            if (!isAdded) return@post
            CatalogMergeHelper.checkAndOfferIfNeeded(requireActivity(), jsonHelper, parentFragmentManager)
        }

        autoSyncHealthConnect()
        autoSyncTriPath()
    }

    override fun onResume() {
        super.onResume()
        // Palette changes are handled by the host activity, which recreates itself and
        // therefore this fragment too.
        jsonHelper.invalidateTrainingDataCache()
        refresh()
        autoSyncHealthConnect()
        autoSyncTriPath()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Staggered spring entrance. Timings and the spring itself live in [Motion] so every
     * screen shares one motion vocabulary; this method only declares the order.
     */
    private fun runEntranceAnimations() {
        Motion.springInWaves(
            listOf(
                listOf(binding.textWorkoutDate, binding.textWorkoutTitle, binding.cardSettings),
                listOf(binding.cardStartWorkout),
                listOf(binding.layoutModes),
                listOf(binding.cardUpNext),
                listOf(binding.cardLastSession),
                listOf(binding.cardMomentum)
            )
        )
    }

    private fun setupClickListeners() {
        // The hero gets CONFIRM (a firmer, two-stage tick) because it commits the user to a
        // workout; navigation gets the lighter CONTEXT_CLICK. This is a one-handed, sweaty-hands
        // app — haptics here are function, not garnish.
        binding.cardStartWorkout.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            // Always non-null: refresh() assigns it before the view can be touched.
            heroAction?.invoke()
        }

        // Tiles name the mode, so they skip the sheet's first screen rather than repeating it.
        binding.tileManual.setOnClickListener {
            startManualWorkout()
        }
        binding.tilePlan.setOnClickListener {
            showWorkoutModeBottomSheet(SelectWorkoutModeBottomSheet.Section.PLAN)
        }
        binding.tileStretch.setOnClickListener {
            showWorkoutModeBottomSheet(SelectWorkoutModeBottomSheet.Section.STRETCH)
        }

        binding.cardSettings.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        binding.textSeePlan.setOnClickListener {
            host()?.openPlan()
        }

        // The momentum card is the way to the trend charts, which moved to Progress > Overview.
        binding.cardMomentum.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            host()?.openProgress(ProgressPagerAdapter.TAB_OVERVIEW)
        }

        // Scale-down-on-touch for everything tappable. Registered after the click listeners
        // above; the touch listener never consumes the event, so both fire.
        Motion.applyPressResponse(
            binding.cardStartWorkout,
            binding.tileManual,
            binding.tilePlan,
            binding.tileStretch,
            binding.cardLastSession,
            binding.cardMomentum,
            binding.cardSettings
        )
    }

    // ---------------------------------------------------------------- content

    private fun refresh() {
        val trainingData = jsonHelper.readTrainingData()

        binding.textWorkoutDate.text = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
            .format(Calendar.getInstance().time)

        updateHero(trainingData)
        updateLastSession(trainingData)
        updateMomentum(trainingData)
        updateBodyStatus(trainingData)
    }

    /**
     * Hero + up-next, which are two views of one answer: "what is the next session?"
     *
     * Priority is resume → rotation → nothing, because a half-finished workout is the only thing
     * more urgent than the plan you were going to do.
     */
    private fun updateHero(trainingData: TrainingData) {
        val storedDraft = draftManager.loadDraft()
        val draft = storedDraft?.takeIf { it.entries.isNotEmpty() || !it.exerciseOrder.isNullOrEmpty() }
        // An empty draft file is left behind when a workout is opened and abandoned before the
        // first set. Clearing it here keeps it from suppressing the rotation hero forever.
        if (storedDraft != null && draft == null) draftManager.clearDraft()

        if (draft != null) {
            showResumeHero(draft)
            val draftPlan = draft.appliedPlanId?.let { id -> trainingData.workoutPlans.find { it.id == id } }
            showUpNext(trainingData, draftPlan, rotationName = null)
            return
        }

        when (val routine = PlanRotationHelper.resolveActiveRoutine(trainingData)) {
            is PlanRotationHelper.ActiveRoutine.Rotation -> {
                showRotationHero(trainingData, routine.planSet, routine.nextPlan)
                showUpNext(trainingData, routine.nextPlan, rotationName = routine.planSet.name)
                return
            }
            is PlanRotationHelper.ActiveRoutine.SinglePlan -> {
                showSinglePlanHero(trainingData, routine.plan)
                showUpNext(trainingData, routine.plan, rotationName = null)
                return
            }
            null -> Unit
        }

        showGenericHero()
        showUpNext(trainingData, plan = null, rotationName = null)
    }

    private fun showResumeHero(draft: ActiveWorkoutDraft) {
        // exerciseOrder is the authoritative row list (it includes exercises with no sets yet), but
        // warm-up and cool-down rows are not exercises. Legacy drafts have no order list, so fall
        // back to counting distinct exercises across the logged sets.
        val exerciseCount = draft.exerciseOrder?.count { !it.isSpecialElement }
            ?: draft.entries.map { it.exerciseId }.distinct().size
        val setCount = draft.entries.size
        // Two plurals joined rather than one two-argument plural: "1 exercise · 4 sets" needs the
        // singular for one half and the plural for the other, which a single quantity cannot do.
        val summary = joinDetails(
            resources.getQuantityString(R.plurals.workout_plan_exercises, exerciseCount, exerciseCount),
            resources.getQuantityString(R.plurals.workout_draft_sets, setCount, setCount)
        )

        binding.textHeroEyebrow.visibility = View.VISIBLE
        binding.textHeroEyebrow.text = getString(R.string.workout_hero_in_progress)
        binding.textHeroTitle.text = draft.appliedPlanName ?: draft.workoutType.replaceFirstChar {
            it.titlecase(Locale.getDefault())
        }
        binding.textHeroSub.text = elapsedMinutes(draft.startTimeMillis)
            ?.let { getString(R.string.workout_hero_resume_elapsed, summary, it) }
            ?: summary

        heroAction = { launchActiveWorkout(draft.workoutType, resumeDraft = true) }
    }

    private fun showRotationHero(trainingData: TrainingData, planSet: PlanSet, plan: WorkoutPlan) {
        val exerciseCount = PlanRotationHelper.exerciseCount(plan, trainingData.exerciseLibrary)

        binding.textHeroEyebrow.visibility = View.VISIBLE
        binding.textHeroEyebrow.text = getString(R.string.workout_hero_next_in, planSet.name)
        binding.textHeroTitle.text = plan.name
        binding.textHeroSub.text = resources.getQuantityString(
            R.plurals.workout_plan_exercises,
            exerciseCount,
            exerciseCount
        )

        // Passing the plan set through is what lets ActiveTrainingActivity advance the rotation
        // when the session is saved; starting the same plan without it would leave the rotation
        // stuck on this entry.
        heroAction = {
            launchActiveWorkout(
                workoutType = plan.workoutType,
                resumeDraft = false,
                planId = plan.id,
                planSetId = planSet.id
            )
        }
    }

    private fun showSinglePlanHero(trainingData: TrainingData, plan: WorkoutPlan) {
        val exerciseCount = PlanRotationHelper.exerciseCount(plan, trainingData.exerciseLibrary)

        binding.textHeroEyebrow.visibility = View.VISIBLE
        binding.textHeroEyebrow.text = getString(R.string.workout_hero_your_plan)
        binding.textHeroTitle.text = plan.name
        binding.textHeroSub.text = resources.getQuantityString(
            R.plurals.workout_plan_exercises,
            exerciseCount,
            exerciseCount
        )

        // No planSetId: there's no rotation progress to advance for a single-plan routine.
        heroAction = {
            launchActiveWorkout(
                workoutType = plan.workoutType,
                resumeDraft = false,
                planId = plan.id
            )
        }
    }

    private fun showGenericHero() {
        binding.textHeroEyebrow.visibility = View.GONE
        binding.textHeroTitle.text = getString(R.string.home_hero_start)
        binding.textHeroSub.text = getString(R.string.home_hero_modes)
        heroAction = { showWorkoutModeBottomSheet() }
    }

    /**
     * Joins card detail fragments with a middle dot, dropping the ones that don't apply (a session
     * with no recorded duration, say). Each fragment is already localised and pluralised; the
     * separator is punctuation, not prose.
     */
    private fun joinDetails(vararg parts: String?): String =
        parts.filterNotNull().filter { it.isNotBlank() }.joinToString(DETAIL_SEPARATOR)

    /** Whole minutes since [startTimeMillis], or null when the draft predates that field. */
    private fun elapsedMinutes(startTimeMillis: Long?): Int? {
        if (startTimeMillis == null || startTimeMillis <= 0L) return null
        val minutes = ((System.currentTimeMillis() - startTimeMillis) / 60_000L).toInt()
        return minutes.takeIf { it > 0 }
    }

    /**
     * The plan preview. Rows come from [PlanRotationHelper.previewSlots], which resolves family
     * slots and warmup/cooldown exactly the way ActiveTrainingActivity does — so what you read
     * here is what you get when you tap the hero.
     */
    private fun showUpNext(trainingData: TrainingData, plan: WorkoutPlan?, rotationName: String?) {
        if (plan == null) {
            binding.cardUpNext.visibility = View.GONE
            return
        }

        val slots = PlanRotationHelper.previewSlots(
            requireContext(), plan, trainingData.exerciseLibrary, CircuitStore.circuits(trainingData)
        )
        if (slots.isEmpty()) {
            binding.cardUpNext.visibility = View.GONE
            return
        }

        binding.textUpNextEyebrow.text = rotationName
            ?.let { getString(R.string.workout_up_next_rotation, it) }
            ?: getString(R.string.workout_up_next)
        binding.textUpNextPlan.text = plan.name

        binding.layoutUpNextRows.removeAllViews()
        slots.take(UP_NEXT_MAX_ROWS).forEach { slot ->
            val row = ItemUpNextSlotBinding.inflate(layoutInflater, binding.layoutUpNextRows, false)
            row.textSlotName.text = slot.label
            row.textSlotTarget.text = slot.target ?: ""
            // Warmup and cooldown are scaffolding around the working sets, so they recede.
            if (slot.isSpecial) {
                row.textSlotName.setTextColor(requireContext().lpColor(R.attr.lpInkSecondary))
            }
            binding.layoutUpNextRows.addView(row.root)
        }

        val hidden = slots.size - UP_NEXT_MAX_ROWS
        binding.textUpNextMore.visibility = if (hidden > 0) View.VISIBLE else View.GONE
        if (hidden > 0) {
            binding.textUpNextMore.text = getString(R.string.workout_up_next_more, hidden)
        }

        binding.cardUpNext.visibility = View.VISIBLE
    }

    private fun updateLastSession(trainingData: TrainingData) {
        val lastSession = trainingData.trainings.maxByOrNull { it.date }
        if (lastSession == null) {
            binding.textLastSessionWhen.text = getString(R.string.home_last_workout_never)
            binding.textLastSessionDetail.text = getString(R.string.home_last_workout_never_sub)
            binding.textLastSessionWhen.setTextColor(requireContext().lpColor(R.attr.lpInk))
            binding.textLastSessionPrs.visibility = View.GONE
            binding.cardLastSession.setOnClickListener(null)
            binding.cardLastSession.isClickable = false
            return
        }

        val daysBetween = calendarDaysBetweenSessionDateAndToday(lastSession.date)

        binding.textLastSessionWhen.text = when (daysBetween) {
            null -> "—"
            0    -> getString(R.string.home_last_workout_today)
            1    -> getString(R.string.home_last_workout_yesterday)
            else -> getString(R.string.home_last_workout_days_ago, daysBetween)
        }

        // Recency reads as ink → accent → negative. Nothing is "green for good" here: training
        // two days ago is simply normal, so it gets plain ink rather than a reassuring colour it
        // does not need.
        val whenColorAttr = when (daysBetween) {
            null, in 0..2 -> R.attr.lpInk
            3             -> R.attr.lpInkSecondary
            4             -> R.attr.lpAccent
            else          -> R.attr.lpNegative
        }
        binding.textLastSessionWhen.setTextColor(requireContext().lpColor(whenColorAttr))

        val workingSets = lastSession.exercises.filterNot { it.isWarmup }
        val exerciseCount = lastSession.exercises.map { it.exerciseId }.distinct().size
        val volume = SetMetrics.totalVolumeKg(workingSets).toInt()
        val label = lastSession.planName ?: lastSession.defaultWorkoutType?.replaceFirstChar {
            it.titlecase(Locale.getDefault())
        } ?: getString(R.string.workout_chip_manual)
        val duration = lastSession.durationSeconds?.takeIf { it > 0 }

        binding.textLastSessionDetail.text = joinDetails(
            label,
            resources.getQuantityString(R.plurals.workout_plan_exercises, exerciseCount, exerciseCount),
            getString(R.string.workout_volume_kg, volume),
            duration?.let { DurationHelper.formatDuration(it) }
        )

        val prCount = ProgressAnalysisHelper
            .getPRsForSession(trainingData.trainings, lastSession.id)
            .size
        binding.textLastSessionPrs.visibility = if (prCount > 0) View.VISIBLE else View.GONE
        if (prCount > 0) {
            binding.textLastSessionPrs.text =
                resources.getQuantityString(R.plurals.workout_last_session_prs, prCount, prCount)
        }

        binding.cardLastSession.isClickable = true
        binding.cardLastSession.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            openSessionDetail(lastSession)
        }
    }

    private fun openSessionDetail(session: TrainingSession) {
        startActivity(
            Intent(requireContext(), TrainingDetailActivity::class.java).apply {
                putExtra(TrainingDetailActivity.EXTRA_TRAINING_SESSION, session)
            }
        )
    }

    /**
     * Three weeks of training as 21 dots plus a one-line summary.
     *
     * A dot strip rather than a bar chart on purpose: the question this card answers is "have I
     * been showing up", which is a pattern of gaps, not a magnitude.
     */
    private fun updateMomentum(trainingData: TrainingData) {
        val summary = ProgressAnalysisHelper.getRollingDaysSummary(
            trainingData.trainings,
            dayCount = MOMENTUM_DAYS
        )

        if (summary.sessionCount == 0) {
            binding.textMomentumSummary.text = getString(R.string.home_week_no_sessions)
            binding.textMomentumInsight.text = getString(R.string.home_week_insight_empty)
        } else {
            binding.textMomentumSummary.text = resources.getQuantityString(
                R.plurals.home_week_sessions_volume,
                summary.sessionCount,
                summary.sessionCount,
                summary.totalVolume.toInt()
            )
            binding.textMomentumInsight.text = when (summary.dominantIntent) {
                SetIntent.STRENGTH -> getString(R.string.home_week_style_strength)
                SetIntent.FLUSH    -> getString(R.string.home_week_style_flush)
                else               -> getString(R.string.home_week_style_build)
            }
        }

        renderMomentumDots(trainingData)
    }

    private fun renderMomentumDots(trainingData: TrainingData) {
        val fmt = sessionDateFormat()
        val trainedDays = trainingData.trainings.map { it.date }.toSet()
        val trainedColor = requireContext().lpColor(R.attr.lpInk)
        val restColor = requireContext().lpColor(R.attr.lpHairlineStrong)
        val density = resources.displayMetrics.density
        val dotPx = (DOT_SIZE_DP * density).toInt()
        val gapPx = (DOT_GAP_DP * density).toInt()

        binding.layoutMomentumDots.removeAllViews()
        val now = Calendar.getInstance().time
        val calendar = Calendar.getInstance()
        // Oldest first, so the strip reads left-to-right as time passing and ends on today.
        for (offset in (MOMENTUM_DAYS - 1) downTo 0) {
            calendar.time = now
            calendar.add(Calendar.DAY_OF_YEAR, -offset)
            val trained = fmt.format(calendar.time) in trainedDays

            val dot = View(requireContext()).apply {
                setBackgroundResource(R.drawable.lp_dot)
                backgroundTintList = ColorStateList.valueOf(
                    if (trained) trainedColor else restColor
                )
                // Fixed size plus a fixed gap, NOT layout_weight: weight would add the slack to
                // each dot's width and draw 21 horizontal ovals. 21×8 + 20×4 = 248dp, which fits
                // inside the card padding even on a 320dp-wide screen.
                layoutParams = LinearLayout.LayoutParams(dotPx, dotPx).apply {
                    if (offset > 0) marginEnd = gapPx
                }
            }
            binding.layoutMomentumDots.addView(dot)
        }
    }

    /**
     * Formatter for `TrainingSession.date`.
     *
     * Default locale, not US: session dates are *written* with the default locale
     * (ActiveTrainingActivity.sessionDateFormat), so comparing against a US-formatted string
     * would silently miss every day under a non-Gregorian calendar.
     */
    private fun sessionDateFormat() = SimpleDateFormat(SESSION_DATE_PATTERN, Locale.getDefault())

    /**
     * The body status figure: the illustrated muscle map, tinted per muscle by effort (working
     * volume) over the last [BODY_STATUS_DAYS] days. Hidden with no recent training, unlike the
     * readiness card it replaced — that card stayed visible on a fresh install as the only route
     * to ReadinessDashboardActivity; that entry point now lives in Settings, so this one can just
     * hide instead of showing an empty silhouette.
     */
    private fun updateBodyStatus(trainingData: TrainingData) {
        val effort = ProgressAnalysisHelper.getRecentMuscleEffort(
            trainingData.trainings,
            trainingData.exerciseLibrary,
            daysBack = BODY_STATUS_DAYS
        )
        if (effort.isEmpty()) {
            binding.cardBodyStatus.visibility = View.GONE
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext()
            val intensities = MuscleMapColorResolver.resolveEffortIntensity(effort)
            val maskIntensities = MuscleMapColorResolver.flattenToMaskCategories(
                intensities, rank = MuscleMapColorResolver::effortRank
            )
            val maskColors = maskIntensities.map { (maskResId, intensity) ->
                maskResId to MuscleMapColorResolver.colorForEffort(context, intensity)
            }
            val bitmap = withContext(Dispatchers.Default) {
                MuscleMapRenderer.render(context, maskColors)
            }
            if (_binding == null) return@launch

            binding.imageBodyStatus.setImageBitmap(bitmap)
            val topMuscles = effort.entries
                .sortedByDescending { it.value }
                .take(BODY_STATUS_SUMMARY_MUSCLES)
                .joinToString(", ") { it.key.displayName }
            binding.textBodyStatusSummary.text =
                getString(R.string.workout_body_status_summary, topMuscles)

            if (binding.cardBodyStatus.visibility != View.VISIBLE) {
                binding.cardBodyStatus.visibility = View.VISIBLE
                // Same spring as the entrance waves, so a card that arrives later arrives the
                // same way as the ones that were there on launch.
                Motion.springIn(binding.cardBodyStatus)
            }
        }
    }

    /** Whole calendar days from session local date to today (0 = same calendar day). */
    private fun calendarDaysBetweenSessionDateAndToday(sessionDateStr: String): Int? {
        return try {
            val fmt = SimpleDateFormat(SESSION_DATE_PATTERN, Locale.US)
            val sessionDay = Calendar.getInstance().apply {
                time = fmt.parse(sessionDateStr) ?: return null
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diff = ((today.timeInMillis - sessionDay.timeInMillis) / 86_400_000L).toInt()
            diff.coerceAtLeast(0)
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- starting a workout

    private fun showWorkoutModeBottomSheet(
        section: SelectWorkoutModeBottomSheet.Section = SelectWorkoutModeBottomSheet.Section.NONE
    ) {
        val bottomSheet = SelectWorkoutModeBottomSheet.newInstance(
            onCustomSelected = {
                startManualWorkout()
            },
            onPlanSelected = { plan, planSet ->
                launchActiveWorkout(
                    workoutType = plan.workoutType,
                    resumeDraft = false,
                    planId = plan.id,
                    planSetId = planSet?.id
                )
            },
            onStretchSelected = { muscles ->
                launchStandaloneStretch(muscles)
            },
            initialSection = section
        )
        bottomSheet.show(parentFragmentManager, "SelectWorkoutModeBottomSheet")
    }

    /**
     * The Manual chip. Asks about an open draft first, because "Manual" from this screen means
     * "a new empty workout" and starting one silently on top of a half-logged session would lose
     * it. The plan and stretch routes do not need this: ActiveTrainingActivity raises the same
     * question itself when it is launched without `resumeDraft`, and a stretch session never
     * touches the draft at all.
     */
    private fun startManualWorkout() {
        val draft = draftManager.loadDraft()?.takeIf { it.entries.isNotEmpty() }
        if (draft == null) {
            launchActiveWorkout("custom", resumeDraft = false)
            return
        }

        DialogHelper.createBuilder(requireContext())
            .setTitle(getString(R.string.dialog_title_resume_workout))
            .setMessage(getString(R.string.dialog_message_resume_workout, draft.workoutType, draft.date))
            .setPositiveButton(getString(R.string.button_resume)) { _, _ ->
                launchActiveWorkout(draft.workoutType, resumeDraft = true)
            }
            .setNegativeButton(getString(R.string.button_discard)) { _, _ ->
                draftManager.clearDraft()
                launchActiveWorkout("custom", resumeDraft = false)
            }
            .setNeutralButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    /** Standalone stretch sessions are not recorded, so no result launcher / stats refresh. */
    private fun launchStandaloneStretch(muscles: Set<TargetMuscle>) {
        val intent = Intent(requireContext(), StretchCooldownActivity::class.java).apply {
            putExtra(StretchCooldownActivity.EXTRA_STANDALONE, true)
            putStringArrayListExtra(
                StretchCooldownActivity.EXTRA_WORKED_MUSCLES,
                ArrayList(muscles.map { it.name })
            )
        }
        startActivity(intent)
    }

    private fun launchActiveWorkout(
        workoutType: String,
        resumeDraft: Boolean,
        planId: String? = null,
        planSetId: String? = null
    ) {
        val intent = Intent(requireContext(), ActiveTrainingActivity::class.java).apply {
            putExtra(ActiveTrainingActivity.EXTRA_WORKOUT_TYPE, workoutType)
            putExtra(ActiveTrainingActivity.EXTRA_RESUME_DRAFT, resumeDraft)
            // Auto-generation has no entry point on this screen; the extra is passed explicitly
            // so ActiveTrainingActivity never reads a stale value from a recycled intent.
            putExtra(ActiveTrainingActivity.EXTRA_AUTO_GENERATE, false)
            if (planId != null) putExtra(ActiveTrainingActivity.EXTRA_PLAN_ID, planId)
            if (planSetId != null) putExtra(ActiveTrainingActivity.EXTRA_PLAN_SET_ID, planSetId)
        }
        startWorkoutForResult.launch(intent)
    }

    // ---------------------------------------------------------------- background sync

    private fun autoSyncHealthConnect() {
        val healthConnectPrefs = requireContext().getSharedPreferences("health_connect_settings", Context.MODE_PRIVATE)
        val isEnabled = healthConnectPrefs.getBoolean("use_health_connect_data", false)

        if (!isEnabled || !HealthConnectHelper.isAvailable(requireContext())) {
            // No Withings sync will run; check the body-weight prompt against current data.
            maybePromptBodyWeight()
            return
        }

        // Perform sync in background (silently, no UI feedback)
        viewLifecycleOwner.lifecycleScope.launch {
            HealthConnectHelper.autoSyncActivities(requireContext().applicationContext).fold(
                onSuccess = { _ -> },
                onFailure = { }  // logged in helper
            )
        }

        // Sync Withings body-scan data silently in the background, then evaluate the body-weight
        // prompt so it reflects the freshest Withings reading (the sync is otherwise
        // fire-and-forget). lifecycleScope resumes on the main thread after the suspend call, so
        // UI access is safe.
        viewLifecycleOwner.lifecycleScope.launch {
            WithingsHealthConnectHelper.autoSync(requireContext().applicationContext)
            maybePromptBodyWeight()
        }
    }

    /**
     * Refreshes TriPath's cardio load and recovery data. Fire-and-forget: readiness and the Fuel
     * page read the cached file, so a failed pull just leaves them on yesterday's numbers.
     */
    private fun autoSyncTriPath() {
        if (!TriPathConnection.isEnabled(requireContext())) return

        viewLifecycleOwner.lifecycleScope.launch {
            TriPathSyncHelper.autoSync(requireContext().applicationContext)
        }
    }

    private var bodyWeightDialogVisible = false

    /** Show a body-weight prompt if due. No-op for fresh-Withings / first-time cases. */
    private fun maybePromptBodyWeight() {
        if (!isAdded || bodyWeightDialogVisible) return
        when (BodyWeightHelper.evaluateBodyWeightPrompt(requireContext())) {
            BodyWeightHelper.BodyWeightPromptType.MANUAL_RECURRING -> {
                val current = BodyWeightHelper.getCurrentBodyweightKg(requireContext()) ?: return
                bodyWeightDialogVisible = true
                BodyWeightDialogs.showRecurringManualPrompt(requireContext(), current) {
                    bodyWeightDialogVisible = false
                }
            }
            BodyWeightHelper.BodyWeightPromptType.WITHINGS_STALE -> {
                val latest = BodyWeightHelper.latestWithingsWeight(requireContext()) ?: return
                bodyWeightDialogVisible = true
                BodyWeightDialogs.showWithingsStalePrompt(requireContext(), latest.first, latest.second) {
                    bodyWeightDialogVisible = false
                }
            }
            else -> { /* NONE / NEEDS_INITIAL: nothing on app open */ }
        }
    }
}
