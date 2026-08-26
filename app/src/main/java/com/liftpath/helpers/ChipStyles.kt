package com.liftpath.helpers

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.liftpath.R

/**
 * Applies [R.style.Widget_LP_Chip_Choice] to a programmatically-created chip.
 *
 * XML-inflated chips get this via `style="@style/Widget.LP.Chip.Choice"`, but a style attribute
 * only applies at inflation time — a `Chip(context)` built in code ignores it. Chip groups whose
 * contents come from data (body areas, muscle lists) have to mirror the style here, so it lives
 * in one place rather than being re-typed per screen: a copy that drifts pins its chips to
 * whatever colours were current when it was written, which is exactly what the design-system
 * contract in CLAUDE.md forbids.
 */
fun Chip.applyChoiceChipStyle() {
    setTextAppearance(R.style.TextAppearance_LP_Label)
    chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.lp_chip_background)
    setTextColor(ContextCompat.getColorStateList(context, R.color.lp_chip_text))
    chipStrokeColor = ColorStateList.valueOf(context.lpColor(R.attr.lpHairline))
    chipStrokeWidth = resources.getDimension(R.dimen.lp_hairline_width)
    chipCornerRadius = resources.getDimension(R.dimen.lp_radius_sm)
    rippleColor = ColorStateList.valueOf(context.lpColor(R.attr.lpRipple))
}
