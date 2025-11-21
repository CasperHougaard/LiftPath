package com.lilfitness.examples

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lilfitness.R
import com.lilfitness.helpers.DialogHelper

/**
 * Example file demonstrating how to use MaterialAlertDialogBuilder
 * with the custom Fitness App Design Guidelines styling.
 * 
 * The dialogs will automatically use:
 * - 24dp corner radius (Featured/Hero card style)
 * - @color/fitness_card_background for background
 * - @color/fitness_text_primary for text
 * - @color/fitness_primary for action buttons
 * - Bold titles
 */
object DialogUsageExample {
    
    /**
     * Example 1: Simple dialog with OK button
     * The theme is automatically applied via materialAlertDialogTheme in Theme.Fitness
     */
    fun showSimpleDialog(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Dialog Title")
            .setMessage("This is a dialog message that follows the Fitness App Design Guidelines.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Example 2: Dialog with explicit theme application
     * Use this if you want to be explicit about the theme
     */
    fun showExplicitThemedDialog(context: Context) {
        DialogHelper.createStyledDialog(context)
            .setTitle("Dialog Title")
            .setMessage("This dialog explicitly uses the Fitness theme.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Example 3: Confirmation dialog with actions
     */
    fun showConfirmationDialog(context: Context, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Confirm Action")
            .setMessage("Are you sure you want to proceed?")
            .setPositiveButton("Confirm") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Example 4: Dialog with multiple buttons
     */
    fun showMultiButtonDialog(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Choose Option")
            .setMessage("Please select an option:")
            .setPositiveButton("Option 1") { _, _ ->
                // Handle option 1
            }
            .setNeutralButton("Option 2") { _, _ ->
                // Handle option 2
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Example 5: Dialog with localized strings
     */
    fun showLocalizedDialog(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.dialog_title_reset_data))
            .setMessage(context.getString(R.string.dialog_message_reset_data))
            .setPositiveButton(context.getString(R.string.button_reset)) { _, _ ->
                // Handle reset
            }
            .setNegativeButton(context.getString(R.string.button_cancel), null)
            .show()
    }
    
    /**
     * Example 6: Dialog with custom view (if needed)
     * Note: Custom views should also follow design guidelines
     */
    fun showDialogWithCustomView(context: Context) {
        // Create your custom view following design guidelines
        // val customView = LayoutInflater.from(context).inflate(R.layout.custom_dialog_view, null)
        
        MaterialAlertDialogBuilder(context)
            .setTitle("Custom Dialog")
            .setMessage("This dialog can have a custom view if needed.")
            // .setView(customView) // Uncomment if using custom view
            .setPositiveButton("OK", null)
            .show()
    }
}

/**
 * Usage in an Activity or Fragment:
 * 
 * class MyActivity : AppCompatActivity() {
 *     private fun showMyDialog() {
 *         // Simple usage - theme is automatically applied
 *         MaterialAlertDialogBuilder(this)
 *             .setTitle("Title")
 *             .setMessage("Message")
 *             .setPositiveButton("OK", null)
 *             .show()
 *         
 *         // Or use the helper for explicit theme application
 *         DialogHelper.createStyledDialog(this)
 *             .setTitle("Title")
 *             .setMessage("Message")
 *             .setPositiveButton("OK", null)
 *             .show()
 *     }
 * }
 */

