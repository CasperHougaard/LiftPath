package com.liftpath.components

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.liftpath.R
import com.liftpath.databinding.DialogMuscleMapBinding
import com.liftpath.helpers.MuscleMapColorResolver
import com.liftpath.helpers.MuscleMapRenderer
import com.liftpath.models.TargetMuscle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reusable bottom sheet dialog showing the illustrated muscle map with activated muscles
 * highlighted.
 */
class MuscleMapDialog : BottomSheetDialogFragment() {

    private var _binding: DialogMuscleMapBinding? = null
    private val binding get() = _binding!!

    private var primaryMuscles: Set<TargetMuscle> = emptySet()
    private var secondaryMuscles: Set<TargetMuscle> = emptySet()

    companion object {
        private const val ARG_PRIMARY_MUSCLES = "primary_muscles"
        private const val ARG_SECONDARY_MUSCLES = "secondary_muscles"

        /**
         * Creates a new instance of MuscleMapDialog.
         *
         * @param primaryMuscles Set of primary target muscles to highlight
         * @param secondaryMuscles Set of secondary target muscles to highlight
         * @return New MuscleMapDialog instance
         */
        fun newInstance(
            primaryMuscles: Set<TargetMuscle>,
            secondaryMuscles: Set<TargetMuscle>
        ): MuscleMapDialog {
            return MuscleMapDialog().apply {
                arguments = Bundle().apply {
                    putStringArray(ARG_PRIMARY_MUSCLES, primaryMuscles.map { it.name }.toTypedArray())
                    putStringArray(ARG_SECONDARY_MUSCLES, secondaryMuscles.map { it.name }.toTypedArray())
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Use BottomSheetDialog for Material Design bottom sheet
        return BottomSheetDialog(requireContext(), R.style.ThemeOverlay_Fitness_BottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMuscleMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load muscle sets from arguments
        arguments?.let { args ->
            val primaryNames = args.getStringArray(ARG_PRIMARY_MUSCLES)?.toSet() ?: emptySet()
            val secondaryNames = args.getStringArray(ARG_SECONDARY_MUSCLES)?.toSet() ?: emptySet()

            primaryMuscles = primaryNames.mapNotNull { name ->
                TargetMuscle.values().find { it.name == name }
            }.toSet()

            secondaryMuscles = secondaryNames.mapNotNull { name ->
                TargetMuscle.values().find { it.name == name }
            }.toSet()
        }

        setupSummary()
        setupCloseButton()

        if (primaryMuscles.isEmpty() && secondaryMuscles.isEmpty()) {
            showEmptyState()
        } else {
            hideEmptyState()
            updateMuscleMap()
        }
    }

    private fun setupSummary() {
        val activatedCount = (primaryMuscles + secondaryMuscles).size
        val totalCount = TargetMuscle.values().size
        binding.textMuscleSummary.text = "$activatedCount/$totalCount"
    }

    private fun updateMuscleMap() {
        val context = context ?: return
        lifecycleScope.launch {
            val muscleRoles = MuscleMapColorResolver.resolveHighlightColors(primaryMuscles, secondaryMuscles)
            val maskRoles = MuscleMapColorResolver.flattenToMaskCategories(
                muscleRoles, rank = MuscleMapColorResolver::highlightRank
            )
            val maskColors = maskRoles.map { (maskResId, role) ->
                maskResId to MuscleMapColorResolver.colorFor(context, role)
            }
            val bitmap = withContext(Dispatchers.Default) {
                MuscleMapRenderer.render(context, maskColors)
            }
            if (_binding == null) return@launch
            binding.imageMuscleMap.setImageBitmap(bitmap)
            binding.progressLoading.visibility = View.GONE
        }
    }

    private fun setupCloseButton() {
        binding.buttonClose.setOnClickListener {
            dismiss()
        }
    }

    private fun showEmptyState() {
        binding.layoutEmptyState.visibility = View.VISIBLE
        binding.layoutWebviewContainer.visibility = View.GONE
        binding.progressLoading.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.layoutEmptyState.visibility = View.GONE
        binding.layoutWebviewContainer.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
