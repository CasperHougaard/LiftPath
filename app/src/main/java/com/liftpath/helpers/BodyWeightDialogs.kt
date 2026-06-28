package com.liftpath.helpers

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.liftpath.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Body-weight prompt dialogs. Each builds a numeric-input dialog using [DialogHelper] so styling
 * matches the rest of the app, validates the value, and persists through [BodyWeightSettingsManager].
 */
object BodyWeightDialogs {

    private const val MIN_KG = 20f
    private const val MAX_KG = 400f

    private fun formatKg(kg: Float): String = String.format(Locale.US, "%.1f", kg)

    private fun buildInput(context: Context, prefill: Float?): Pair<FrameLayout, EditText> {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = context.getString(R.string.bodyweight_field_hint)
            if (prefill != null) setText(formatKg(prefill))
        }
        val density = context.resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val container = FrameLayout(context).apply {
            setPadding(pad, (8 * density).toInt(), pad, 0)
            addView(input)
        }
        return container to input
    }

    private fun parseValid(input: EditText, context: Context): Float? {
        val kg = input.text.toString().trim().toFloatOrNull()
        if (kg == null || kg < MIN_KG || kg > MAX_KG) {
            Toast.makeText(context, R.string.bodyweight_invalid, Toast.LENGTH_SHORT).show()
            return null
        }
        return kg
    }

    /**
     * First-use prompt (no body weight known yet). Marks the one-time prompt done on any dismiss so
     * the user isn't re-nagged mid-workout.
     */
    fun showInitialBodyweightPrompt(context: Context, onSaved: (Float) -> Unit = {}) {
        val manager = BodyWeightSettingsManager(context)
        val (view, input) = buildInput(context, null)
        val dialog = DialogHelper.createBuilder(context)
            .setTitle(R.string.bodyweight_initial_title)
            .setMessage(R.string.bodyweight_initial_message)
            .setView(view)
            .setPositiveButton(R.string.button_save, null)
            .setNegativeButton(R.string.button_skip, null)
            .setOnDismissListener { manager.markFirstBodyweightPromptDone() }
            .showWithTransparentWindow()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val kg = parseValid(input, context) ?: return@setOnClickListener
            manager.setManualWeight(kg)
            Toast.makeText(context, R.string.toast_bodyweight_saved, Toast.LENGTH_SHORT).show()
            onSaved(kg)
            dialog.dismiss()
        }
    }

    /** Recurring (every 2 weeks) manual prompt when relying on a manual weight. */
    fun showRecurringManualPrompt(context: Context, currentKg: Float, onDismiss: (() -> Unit)? = null) {
        val manager = BodyWeightSettingsManager(context)
        val (view, input) = buildInput(context, currentKg)
        val dialog = DialogHelper.createBuilder(context)
            .setTitle(R.string.bodyweight_recurring_title)
            .setMessage(context.getString(R.string.bodyweight_recurring_message, formatKg(currentKg)))
            .setView(view)
            .setPositiveButton(R.string.button_save, null)
            .setNegativeButton(R.string.button_not_now, null)
            .setOnDismissListener {
                manager.markPrompted()
                onDismiss?.invoke()
            }
            .showWithTransparentWindow()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val kg = parseValid(input, context) ?: return@setOnClickListener
            manager.setManualWeight(kg)
            Toast.makeText(context, R.string.toast_bodyweight_saved, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    /**
     * Withings is connected but the latest weighing is older than 2 weeks. Offer to use a manual
     * value until the next automatic (Withings) weight arrives. "Not now" keeps using Withings.
     */
    fun showWithingsStalePrompt(
        context: Context,
        latestWithingsKg: Float,
        latestWithingsDateMs: Long,
        onDismiss: (() -> Unit)? = null
    ) {
        val manager = BodyWeightSettingsManager(context)
        val (view, input) = buildInput(context, latestWithingsKg)
        val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(latestWithingsDateMs))
        val dialog = DialogHelper.createBuilder(context)
            .setTitle(R.string.bodyweight_stale_title)
            .setMessage(context.getString(R.string.bodyweight_stale_message, dateStr, formatKg(latestWithingsKg)))
            .setView(view)
            .setPositiveButton(R.string.bodyweight_stale_positive, null)
            .setNegativeButton(R.string.button_not_now, null)
            .setOnDismissListener {
                manager.markPrompted()
                onDismiss?.invoke()
            }
            .showWithTransparentWindow()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val kg = parseValid(input, context) ?: return@setOnClickListener
            manager.setManualOverride(kg, latestWithingsDateMs)
            Toast.makeText(context, R.string.toast_bodyweight_saved, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }
}
