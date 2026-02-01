package com.liftpath.components

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import com.liftpath.R

/**
 * Bottom sheet dialog for adding special workout items: Warmup, Cooldown, etc.
 * Slides up from the bottom. Extensible for future options like Stretch, Hyrox, etc.
 */
class AddSpecialBottomSheet : DialogFragment() {

    private var onWarmupSelected: (() -> Unit)? = null
    private var onCooldownSelected: (() -> Unit)? = null
    private var onSuperSetSelected: (() -> Unit)? = null

    companion object {
        /**
         * Creates a new instance of AddSpecialBottomSheet.
         */
        fun newInstance(
            onWarmupSelected: () -> Unit,
            onCooldownSelected: () -> Unit,
            onSuperSetSelected: () -> Unit
        ): AddSpecialBottomSheet {
            return AddSpecialBottomSheet().apply {
                this.onWarmupSelected = onWarmupSelected
                this.onCooldownSelected = onCooldownSelected
                this.onSuperSetSelected = onSuperSetSelected
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.ThemeOverlay_Fitness_BottomSheetDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)
        
        // Enable edge-to-edge to handle system insets properly
        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        
        return dialog
    }
    
    override fun onStart() {
        super.onStart()
        
        // Configure window to slide from bottom and be full width
        dialog?.window?.let { window ->
            val params = window.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.BOTTOM
            params.dimAmount = 0.5f
            window.attributes = params
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_add_special, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply window insets to respect system UI (status bar, etc.)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Add bottom padding to account for navigation bar
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setupTiles(view)
    }

    private fun setupTiles(view: View) {
        // Warmup Tile
        val warmupTile = view.findViewById<View>(R.id.tile_warmup)
        val warmupIcon = warmupTile.findViewById<ImageView>(R.id.icon_tile)
        val warmupTitle = warmupTile.findViewById<TextView>(R.id.text_tile_title)
        warmupIcon.setImageResource(R.drawable.ic_play)
        warmupTitle.text = getString(R.string.tile_warmup)
        warmupTile.setOnClickListener {
            dismiss()
            onWarmupSelected?.invoke()
        }

        // Cooldown Tile
        val cooldownTile = view.findViewById<View>(R.id.tile_cooldown)
        val cooldownIcon = cooldownTile.findViewById<ImageView>(R.id.icon_tile)
        val cooldownTitle = cooldownTile.findViewById<TextView>(R.id.text_tile_title)
        cooldownIcon.setImageResource(R.drawable.ic_refresh)
        cooldownTitle.text = getString(R.string.tile_cooldown)
        cooldownTile.setOnClickListener {
            dismiss()
            onCooldownSelected?.invoke()
        }

        // SuperSet Tile
        val supersetTile = view.findViewById<View>(R.id.tile_superset)
        val supersetIcon = supersetTile.findViewById<ImageView>(R.id.icon_tile)
        val supersetTitle = supersetTile.findViewById<TextView>(R.id.text_tile_title)
        supersetIcon.setImageResource(R.drawable.ic_fitness_center_24)
        supersetTitle.text = getString(R.string.tile_superset)
        supersetTile.setOnClickListener {
            dismiss()
            onSuperSetSelected?.invoke()
        }
    }
}
