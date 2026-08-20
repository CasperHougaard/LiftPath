package com.liftpath.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.liftpath.R
import com.liftpath.databinding.FragmentProgressFuelBinding
import com.liftpath.helpers.BodyWeightHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.LiftingBurnEstimator
import com.liftpath.helpers.TriPathDay
import com.liftpath.helpers.TriPathStorageHelper
import com.liftpath.helpers.lpColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Progress > Fuel: what TriPath says you ate against what it says you spent, and a cross-check of
 * LiftPath's own lifting-burn estimate against the same day's figures.
 *
 * Only exists while TriPath is connected — [com.liftpath.adapters.ProgressPagerAdapter] leaves the
 * page out entirely otherwise, so nothing here needs an "unavailable" state beyond the case where
 * a sync has happened but the days carry no energy data (no bodyweight or demographics in TriPath,
 * so it cannot compute a TDEE).
 */
class ProgressFuelFragment : Fragment() {

    private var _binding: FragmentProgressFuelBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentProgressFuelBinding.inflate(inflater, container, false)
        .also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadData() {
        val days = TriPathStorageHelper(requireContext()).read().days.sortedBy { it.date }
        // A day only counts once TriPath could compute a balance for it; without demographics or a
        // weight there is no expenditure figure and the whole page would be a row of dashes.
        val withEnergy = days.filter { it.intakeKcal != null || it.expenditureKcal != null }

        if (withEnergy.isEmpty()) {
            binding.textFuelEmpty.visibility = View.VISIBLE
            binding.cardFuelSummary.visibility = View.GONE
            binding.cardFuelChart.visibility = View.GONE
            binding.cardLiftBurn.visibility = View.GONE
            return
        }

        binding.textFuelEmpty.visibility = View.GONE
        binding.cardFuelSummary.visibility = View.VISIBLE
        binding.cardFuelChart.visibility = View.VISIBLE
        binding.cardLiftBurn.visibility = View.VISIBLE

        setupSummary(days, withEnergy)
        setupChart(withEnergy)
        setupLiftBurnRows(days)
    }

    private fun setupSummary(allDays: List<TriPathDay>, withEnergy: List<TriPathDay>) {
        binding.textFuelWindow.text = getString(R.string.fuel_window_label, allDays.size)

        val intake = withEnergy.mapNotNull { it.intakeKcal }
        val expenditure = withEnergy.mapNotNull { it.expenditureKcal }
        val balance = withEnergy.mapNotNull { it.balanceKcal }

        binding.textFuelIntake.text = intake.averageOrNull().asKcal()
        binding.textFuelExpenditure.text = expenditure.averageOrNull().asKcal()
        binding.textFuelBalance.text = balance.averageOrNull()?.let {
            String.format(Locale.US, "%+,.0f", it)
        } ?: getString(R.string.placeholder_dash)

        binding.textFuelBalance.setTextColor(
            when {
                balance.isEmpty() -> requireContext().lpColor(R.attr.lpInk)
                // Chronic under-fuelling is the failure mode worth flagging; a surplus is not.
                balance.average() < -UNDER_FUELED_KCAL -> requireContext().lpColor(R.attr.lpNegative)
                else -> requireContext().lpColor(R.attr.lpInk)
            }
        )

        val underFueled = balance.count { it < -UNDER_FUELED_KCAL }
        binding.textFuelUnderFueled.text = if (balance.isEmpty()) {
            getString(R.string.fuel_no_data)
        } else {
            getString(R.string.fuel_under_fueled, underFueled, balance.size)
        }
    }

    private fun setupChart(days: List<TriPathDay>) {
        val ctx = requireContext()
        val first = days.first().date

        val intakeEntries = days.mapNotNull { day ->
            day.intakeKcal?.let { Entry(dayOffset(first, day.date), it) }
        }
        val expenditureEntries = days.mapNotNull { day ->
            day.expenditureKcal?.let { Entry(dayOffset(first, day.date), it) }
        }

        if (intakeEntries.isEmpty() && expenditureEntries.isEmpty()) {
            binding.cardFuelChart.visibility = View.GONE
            return
        }

        // Typed as ILineDataSet up front — the two branches are otherwise a mixed-variance list
        // that LineData's List<ILineDataSet> constructor can't accept without a cast.
        val sets = mutableListOf<ILineDataSet>()
        if (intakeEntries.isNotEmpty()) {
            sets.add(
                LineDataSet(intakeEntries, getString(R.string.fuel_intake)).apply {
                    color = ctx.lpColor(R.attr.lpChartVolume)
                    setCircleColor(ctx.lpColor(R.attr.lpChartVolume))
                    lineWidth = 2.5f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
            )
        }
        if (expenditureEntries.isNotEmpty()) {
            sets.add(
                LineDataSet(expenditureEntries, getString(R.string.fuel_expenditure)).apply {
                    color = ctx.lpColor(R.attr.lpChartFatigue)
                    setCircleColor(ctx.lpColor(R.attr.lpChartFatigue))
                    lineWidth = 2.5f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
            )
        }

        binding.chartFuel.apply {
            data = LineData(sets)
            description.isEnabled = false
            legend.isEnabled = true
            legend.textColor = ctx.lpColor(R.attr.lpInkSecondary)
            setTouchEnabled(true)
            setPinchZoom(false)
            setBackgroundColor(Color.TRANSPARENT)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textColor = ctx.lpColor(R.attr.lpInkSecondary)
                textSize = 10f
                labelRotationAngle = -30f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        runCatching {
                            LocalDate.parse(first).plusDays(value.toLong()).format(DAY_LABEL)
                        }.getOrDefault("")
                }
            }

            axisLeft.apply {
                textColor = ctx.lpColor(R.attr.lpInkSecondary)
                setDrawGridLines(true)
                gridColor = ctx.lpColor(R.attr.lpChartGrid)
            }
            axisRight.isEnabled = false

            animateX(600)
            invalidate()
        }
    }

    /**
     * One row per day on which LiftPath logged a lift: its own estimate of the burn, next to what
     * TriPath recorded as that day's total training burn. The point is the gap, not either number.
     */
    private fun setupLiftBurnRows(days: List<TriPathDay>) {
        val container: LinearLayout = binding.layoutLiftBurnRows
        container.removeAllViews()

        val weight = BodyWeightHelper.getCurrentBodyweightKg(requireContext())
        val sessions = JsonHelper(requireContext()).readTrainingData().trainings
        val liftBurn = LiftingBurnEstimator.dailyKcalByIsoDate(sessions, weight)

        if (liftBurn.isEmpty()) {
            container.addView(bodyText(getString(R.string.fuel_no_data)))
            return
        }

        val daysByDate = days.associateBy { it.date }
        val rows = liftBurn.keys
            .filter { it in daysByDate }
            .sortedDescending()
            .take(LIFT_BURN_ROWS)

        if (rows.isEmpty()) {
            container.addView(bodyText(getString(R.string.fuel_no_data)))
            return
        }

        rows.forEach { date ->
            val day = daysByDate.getValue(date)
            val mine = liftBurn.getValue(date)
            // TriPath's expenditure includes a resting baseline; the comparable part is what it
            // attributes to training, which is expenditure minus that baseline. We don't have the
            // baseline broken out, so show the day's total and let the gap speak for itself.
            val theirs = day.expenditureKcal
            val label = runCatching { LocalDate.parse(date).format(DAY_LABEL) }.getOrDefault(date)

            container.addView(
                bodyText(
                    String.format(
                        Locale.US,
                        "%s — LiftPath %,.0f kcal · TriPath TDEE %s",
                        label,
                        mine,
                        theirs?.let { String.format(Locale.US, "%,.0f kcal", it) } ?: "—"
                    )
                )
            )
        }
    }

    private fun bodyText(text: String): TextView = TextView(requireContext()).apply {
        this.text = text
        setTextAppearance(R.style.TextAppearance_LP_Body)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.lp_space_1) }
    }

    private fun Float?.asKcal(): String =
        this?.let { String.format(Locale.US, "%,.0f", it) } ?: getString(R.string.placeholder_dash)

    private fun List<Float>.averageOrNull(): Float? =
        if (isEmpty()) null else (sum() / size)

    private fun dayOffset(fromIso: String, iso: String): Float = runCatching {
        (LocalDate.parse(iso).toEpochDay() - LocalDate.parse(fromIso).toEpochDay()).toFloat()
    }.getOrDefault(0f)

    companion object {
        /** Matches TriPath's own threshold for calling a day meaningfully under-fuelled. */
        private const val UNDER_FUELED_KCAL = 300f

        /** Recent lifting days shown in the cross-check. Enough to spot a pattern, not a log. */
        private const val LIFT_BURN_ROWS = 7

        /** Axis and row labels. Formats the LocalDate directly — going via java.util.Date
         *  would reinterpret UTC midnight in the local zone and shift labels a day. */
        private val DAY_LABEL: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    }
}
