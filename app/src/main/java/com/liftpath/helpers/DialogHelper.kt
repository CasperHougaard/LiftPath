package com.liftpath.helpers

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.liftpath.R

/**
 * Helper object for creating styled MaterialAlertDialog dialogs
 * that strictly follow the Fitness App Design Guidelines.
 * 
 * Features:
 * - 24dp corner radius (Featured/Hero card style)
 * - Card background color
 * - Primary text colors
 * - Primary blue action buttons
 * - Bold titles
 */
object DialogHelper {
    
    /**
     * Creates a MaterialAlertDialogBuilder with the custom Fitness theme explicitly applied.
     * This ensures the dialog uses the Fitness design system styling regardless of implicit theming.
     * 
     * @param context The context to create the dialog in
     * @return MaterialAlertDialogBuilder with Fitness design system styling
     */
    fun createBuilder(context: Context): MaterialAlertDialogBuilder {
        // Explicitly apply the Fitness dialog theme to ensure styling is applied
        return MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Fitness_MaterialAlertDialog)
    }
    
    /**
     * Legacy method name - delegates to createBuilder for backwards compatibility
     * @deprecated Use createBuilder() instead
     */
    @Deprecated("Use createBuilder() instead", ReplaceWith("createBuilder(context)"))
    fun createStyledDialog(context: Context): MaterialAlertDialogBuilder {
        return createBuilder(context)
    }
}

/**
 * Extension function to show a MaterialAlertDialog with transparent window background.
 * This prevents square corners from showing outside the rounded dialog content.
 * 
 * Usage:
 * ```
 * DialogHelper.createBuilder(context)
 *     .setTitle("Title")
 *     .setMessage("Message")
 *     .showWithTransparentWindow()
 * ```
 */
fun MaterialAlertDialogBuilder.showWithTransparentWindow(): AlertDialog {
    val dialog = this.show()
    // Set window background to transparent to prevent square corners showing
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    return dialog
}
