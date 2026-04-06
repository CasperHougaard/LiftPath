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
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.liftpath.R
import com.liftpath.adapters.ExerciseMergeAdapter
import com.liftpath.helpers.CatalogMergeHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.MergeCandidate

/**
 * Bottom sheet for merging APK exercise catalog updates into the user's library.
 */
class ExerciseLibraryUpdateBottomSheet : DialogFragment() {

    private lateinit var adapter: ExerciseMergeAdapter
    private val gson = Gson()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.ThemeOverlay_Fitness_BottomSheetDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
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
    ): View = inflater.inflate(R.layout.bottom_sheet_exercise_library_update, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val json = requireArguments().getString(ARG_CANDIDATES_JSON) ?: return dismissAllowingStateLoss()
        val type = object : TypeToken<MutableList<MergeCandidate>>() {}.type
        val list: MutableList<MergeCandidate> = try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
        if (list.isEmpty()) {
            dismissAllowingStateLoss()
            return
        }

        val recycler = view.findViewById<RecyclerView>(R.id.recycler_merge_candidates)
        adapter = ExerciseMergeAdapter(list)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        view.findViewById<MaterialButton>(R.id.button_skip).setOnClickListener {
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.button_apply_updates).setOnClickListener {
            val rows = CatalogMergeHelper.mergeCandidatesToAppliedChoices(adapter.getCandidatesSnapshot())
            CatalogMergeHelper.handleMergeResult(
                requireActivity(),
                JsonHelper(requireActivity()),
                rows
            )
            dismiss()
        }
    }

    companion object {
        private const val ARG_CANDIDATES_JSON = "candidates_json"

        fun newInstance(candidatesJson: String): ExerciseLibraryUpdateBottomSheet {
            return ExerciseLibraryUpdateBottomSheet().apply {
                arguments = bundleOf(ARG_CANDIDATES_JSON to candidatesJson)
            }
        }
    }
}
