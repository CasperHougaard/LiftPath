package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.databinding.ItemChartPageBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ChartType {
    VOLUME,
    AVG_RPE,
    TIME_CONSUMPTION,
    FATIGUE
}

data class ChartData(
    val type: ChartType,
    val entries: List<Entry>,
    val title: String,
    val color: Int,
    val yAxisLabel: String,
    val workoutTypes: List<String?>? = null // For fatigue chart: workout type per entry (heavy/light/custom/null)
)

class ChartCarouselAdapter(
    private val charts: List<ChartData>
) : RecyclerView.Adapter<ChartCarouselAdapter.ChartViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartViewHolder {
        val binding = ItemChartPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChartViewHolder, position: Int) {
        holder.bind(charts[position])
    }

    override fun getItemCount() = charts.size

    inner class ChartViewHolder(
        private val binding: ItemChartPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private fun getThemeAwareColor(colorResId: Int): Int {
            return ContextCompat.getColor(binding.root.context, colorResId)
        }

        private fun getThemeAwareGridColor(): Int {
            val context = binding.root.context
            return try {
                ContextCompat.getColor(context, R.color.fitness_chart_grid)
            } catch (e: Exception) {
                // Fallback if color resource doesn't exist
                val isDarkMode = (context.resources.configuration.uiMode and 
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                if (isDarkMode) {
                    Color.parseColor("#374151") // Gray-700 for dark mode
                } else {
                    Color.parseColor("#E0E0E0") // Gray-200 for light mode
                }
            }
        }

        fun bind(chartData: ChartData) {
            binding.textChartTitle.text = chartData.title

            if (chartData.entries.isEmpty()) {
                binding.chartLine.visibility = android.view.View.GONE
                binding.chartBar.visibility = android.view.View.GONE
                return
            }

            // Calculate maximum value for Y-axis
            val maxEntryValue = chartData.entries.maxOfOrNull { it.y } ?: 0f
            val niceMaximum = calculateNiceMaximum(maxEntryValue, chartData.type)

            // Use BarChart for fatigue, LineChart for others
            if (chartData.type == ChartType.FATIGUE) {
                binding.chartLine.visibility = android.view.View.GONE
                binding.chartBar.visibility = android.view.View.VISIBLE
                setupBarChart(binding.chartBar, chartData, niceMaximum)
            } else {
                binding.chartLine.visibility = android.view.View.VISIBLE
                binding.chartBar.visibility = android.view.View.GONE
                setupLineChart(binding.chartLine, chartData, niceMaximum)
            }
        }

        private fun setupLineChart(chart: LineChart, chartData: ChartData, niceMaximum: Float) {
            val dataSet = LineDataSet(chartData.entries, chartData.yAxisLabel)
            dataSet.color = chartData.color
            dataSet.valueTextColor = Color.DKGRAY
            dataSet.setCircleColor(chartData.color)
            dataSet.circleRadius = 3f
            dataSet.lineWidth = 2f
            dataSet.setDrawValues(false)
            dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            dataSet.cubicIntensity = 0.2f
            dataSet.setDrawFilled(true)
            dataSet.fillColor = chartData.color
            dataSet.fillAlpha = 30

            val lineData = LineData(dataSet)
            chart.data = lineData

            configureLineChartAxes(chart, chartData, niceMaximum)
        }

        private fun setupBarChart(chart: BarChart, chartData: ChartData, niceMaximum: Float) {
            // Convert Entry to BarEntry
            val barEntries = chartData.entries.mapIndexed { index, entry ->
                BarEntry(index.toFloat(), entry.y)
            }

            val dataSet = BarDataSet(barEntries, chartData.yAxisLabel)
            
            // Color code bars based on workout type for fatigue chart
            if (chartData.type == ChartType.FATIGUE && chartData.workoutTypes != null) {
                val context = binding.root.context
                val colors = chartData.workoutTypes.map { workoutType ->
                    when (workoutType?.lowercase(Locale.getDefault())) {
                        "heavy" -> {
                            // Red that works in both light and dark mode
                            // Using a vibrant red that's visible on both backgrounds
                            Color.parseColor("#EF4444") // Red-500 - good contrast in both modes
                        }
                        "light" -> {
                            // Orange/Amber that works in both modes
                            ContextCompat.getColor(context, R.color.fitness_accent) // Uses theme-aware accent color
                        }
                        "custom" -> {
                            // Gray that's visible in both modes
                            ContextCompat.getColor(context, R.color.fitness_text_secondary) // Uses theme-aware secondary text
                        }
                        else -> {
                            // Subtle color for no workout - visible but not prominent
                            // Use a color that contrasts with card background in both modes
                            val isDarkMode = (context.resources.configuration.uiMode and 
                                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                                android.content.res.Configuration.UI_MODE_NIGHT_YES
                            if (isDarkMode) {
                                Color.parseColor("#4B5563") // Gray-600 for dark mode
                            } else {
                                Color.parseColor("#D1D5DB") // Gray-300 for light mode
                            }
                        }
                    }
                }
                dataSet.colors = colors
            } else {
                dataSet.color = chartData.color
            }
            
            dataSet.valueTextColor = Color.DKGRAY
            dataSet.setDrawValues(false)

            val barData = BarData(dataSet)
            barData.barWidth = 0.6f
            chart.data = barData

            configureBarChartAxes(chart, chartData, niceMaximum)
        }

        private fun configureLineChartAxes(chart: LineChart, chartData: ChartData, niceMaximum: Float) {
            // Configure X-axis
            val xAxis = chart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textSize = 10f
            xAxis.textColor = getThemeAwareColor(R.color.fitness_text_secondary)
            xAxis.setLabelCount(minOf(chartData.entries.size, 6), true)
            
            if (chartData.type == ChartType.FATIGUE) {
                // For bar chart, use index-based formatter
                xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        if (index >= 0 && index < chartData.entries.size) {
                            return try {
                                displayDateFormat.format(Date(chartData.entries[index].x.toLong()))
                            } catch (e: Exception) {
                                ""
                            }
                        }
                        return ""
                    }
                }
            } else {
                xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return try {
                            displayDateFormat.format(Date(value.toLong()))
                        } catch (e: Exception) {
                            ""
                        }
                    }
                }
            }
            
            xAxis.labelRotationAngle = -45f
            xAxis.setDrawGridLines(true)
            xAxis.gridColor = getThemeAwareGridColor()
            xAxis.gridLineWidth = 1f
            xAxis.enableGridDashedLine(0f, 0f, 0f)
            xAxis.setDrawAxisLine(true)
            xAxis.axisLineColor = getThemeAwareColor(R.color.fitness_text_secondary)
            xAxis.axisLineWidth = 1f

            // Configure Y-axis
            val leftAxis = chart.axisLeft
            leftAxis.axisMinimum = 0f
            leftAxis.axisMaximum = niceMaximum
            leftAxis.textSize = 10f
            leftAxis.textColor = getThemeAwareColor(R.color.fitness_text_secondary)
            leftAxis.setDrawGridLines(true)
            leftAxis.gridColor = getThemeAwareGridColor()
            leftAxis.gridLineWidth = 1f
            leftAxis.enableGridDashedLine(0f, 0f, 0f)
            leftAxis.setDrawZeroLine(true)
            leftAxis.zeroLineColor = getThemeAwareColor(R.color.fitness_text_secondary)
            leftAxis.zeroLineWidth = 1f
            leftAxis.setLabelCount(5, true)
            leftAxis.setDrawAxisLine(true)
            leftAxis.axisLineColor = getThemeAwareColor(R.color.fitness_text_secondary)
            leftAxis.axisLineWidth = 1f
            leftAxis.spaceTop = 5f
            leftAxis.spaceBottom = 0f

            leftAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return when (chartData.type) {
                        ChartType.AVG_RPE -> String.format(Locale.US, "%.1f", value)
                        ChartType.TIME_CONSUMPTION -> {
                            val minutes = value.toInt()
                            if (minutes >= 60) {
                                val hours = minutes / 60
                                val mins = minutes % 60
                                "${hours}h ${mins}m"
                            } else {
                                "${minutes}m"
                            }
                        }
                        else -> String.format(Locale.US, "%.0f", value)
                    }
                }
            }

            chart.axisRight.isEnabled = false

            // Configure chart appearance
            chart.description.isEnabled = false
            chart.setBackgroundColor(Color.TRANSPARENT)
            chart.setDrawGridBackground(false)

            val legend = chart.legend
            legend.isEnabled = false

            chart.setTouchEnabled(false)
            chart.setDragEnabled(false)
            chart.setScaleEnabled(false)
            chart.setPinchZoom(false)
            chart.setDoubleTapToZoomEnabled(false)

            chart.invalidate()
        }

        private fun configureBarChartAxes(chart: BarChart, chartData: ChartData, niceMaximum: Float) {
            // Configure X-axis
            val xAxis = chart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textSize = 10f
            xAxis.textColor = getThemeAwareColor(R.color.fitness_text_secondary)
            // For fatigue chart with 28 days, show more labels
            val labelCount = if (chartData.type == ChartType.FATIGUE) {
                minOf(chartData.entries.size, 14) // Show up to 14 labels for 28 days
            } else {
                minOf(chartData.entries.size, 6)
            }
            xAxis.setLabelCount(labelCount, true)
            
            // For bar chart, use index-based formatter
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    if (index >= 0 && index < chartData.entries.size) {
                        return try {
                            displayDateFormat.format(Date(chartData.entries[index].x.toLong()))
                        } catch (e: Exception) {
                            ""
                        }
                    }
                    return ""
                }
            }
            
            xAxis.labelRotationAngle = -45f
            xAxis.setDrawGridLines(true)
            xAxis.gridColor = getThemeAwareGridColor()
            xAxis.gridLineWidth = 1f
            xAxis.enableGridDashedLine(0f, 0f, 0f)
            xAxis.setDrawAxisLine(true)
            xAxis.axisLineColor = getThemeAwareColor(R.color.fitness_text_secondary)
            xAxis.axisLineWidth = 1f

            // Configure Y-axis
            val leftAxis = chart.axisLeft
            leftAxis.axisMinimum = 0f
            leftAxis.axisMaximum = niceMaximum
            leftAxis.textSize = 10f
            leftAxis.textColor = getThemeAwareColor(R.color.fitness_text_secondary)
            leftAxis.setDrawGridLines(true)
            leftAxis.gridColor = getThemeAwareGridColor()
            leftAxis.gridLineWidth = 1f
            leftAxis.enableGridDashedLine(0f, 0f, 0f)
            leftAxis.setDrawZeroLine(true)
            leftAxis.zeroLineColor = getThemeAwareColor(R.color.fitness_text_secondary)
            leftAxis.zeroLineWidth = 1f
            leftAxis.setLabelCount(5, true)
            leftAxis.setDrawAxisLine(true)
            leftAxis.axisLineColor = getThemeAwareColor(R.color.fitness_text_secondary)
            leftAxis.axisLineWidth = 1f
            leftAxis.spaceTop = 5f
            leftAxis.spaceBottom = 0f

            leftAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format(Locale.US, "%.0f", value)
                }
            }

            chart.axisRight.isEnabled = false

            // Configure chart appearance
            chart.description.isEnabled = false
            chart.setBackgroundColor(Color.TRANSPARENT)
            chart.setDrawGridBackground(false)

            val legend = chart.legend
            legend.isEnabled = false

            chart.setTouchEnabled(false)
            chart.setDragEnabled(false)
            chart.setScaleEnabled(false)
            chart.setPinchZoom(false)
            chart.setDoubleTapToZoomEnabled(false)

            chart.invalidate()
        }

        private fun calculateNiceMaximum(maxValue: Float, type: ChartType): Float {
            if (maxValue <= 0) {
                return when (type) {
                    ChartType.AVG_RPE -> 10f
                    ChartType.TIME_CONSUMPTION -> 120f // 2 hours default
                    ChartType.FATIGUE -> 100f // Default fatigue max
                    else -> 1000f
                }
            }

            val paddedValue = maxValue * 1.15f

            return when (type) {
                ChartType.AVG_RPE -> {
                    // RPE is 0-10, round to nearest 0.5
                    ((paddedValue / 0.5f).toInt() * 0.5f + 0.5f).coerceAtMost(10f).coerceAtLeast(8f)
                }
                ChartType.TIME_CONSUMPTION -> {
                    // Round to nearest 15 minutes
                    ((paddedValue / 15f).toInt() * 15 + 15).toFloat().coerceAtLeast(30f)
                }
                ChartType.FATIGUE -> {
                    // Round to nearest 10 for fatigue
                    ((paddedValue / 10f).toInt() * 10 + 10).toFloat().coerceAtLeast(50f)
                }
                else -> {
                    // Volume chart logic
                    when {
                        paddedValue < 100 -> {
                            ((paddedValue / 10).toInt() * 10 + 10).toFloat().coerceAtLeast(50f)
                        }
                        paddedValue < 500 -> {
                            ((paddedValue / 25).toInt() * 25 + 25).toFloat().coerceAtLeast(100f)
                        }
                        paddedValue < 1000 -> {
                            ((paddedValue / 50).toInt() * 50 + 50).toFloat().coerceAtLeast(500f)
                        }
                        paddedValue < 5000 -> {
                            ((paddedValue / 250).toInt() * 250 + 250).toFloat().coerceAtLeast(1000f)
                        }
                        else -> {
                            ((paddedValue / 500).toInt() * 500 + 500).toFloat().coerceAtLeast(5000f)
                        }
                    }
                }
            }
        }
    }
}