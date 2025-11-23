package com.liftpath.components

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.liftpath.R
import com.liftpath.databinding.DialogMuscleMapBinding
import com.liftpath.helpers.MuscleActivationHelper
import com.liftpath.models.TargetMuscle

/**
 * Reusable bottom sheet dialog for displaying muscle activation map.
 * Shows a WebView with the muscle map SVG and highlights activated muscles.
 */
class MuscleMapDialog : BottomSheetDialogFragment() {

    private var _binding: DialogMuscleMapBinding? = null
    private val binding get() = _binding!!

    private var primaryMuscles: Set<TargetMuscle> = emptySet()
    private var secondaryMuscles: Set<TargetMuscle> = emptySet()
    private var isWebViewReady = false

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

        // Setup UI
        setupSummary()
        setupWebView()
        setupCloseButton()

        // Show empty state if no muscles activated
        if (primaryMuscles.isEmpty() && secondaryMuscles.isEmpty()) {
            showEmptyState()
        } else {
            hideEmptyState()
        }
    }

    private fun setupSummary() {
        val activatedCount = (primaryMuscles + secondaryMuscles).size
        val totalCount = TargetMuscle.values().size
        binding.textMuscleSummary.text = "$activatedCount/$totalCount"
    }

    private fun setupWebView() {
        binding.webviewMuscleMap.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
        }
        binding.webviewMuscleMap.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        // Add WebChromeClient to capture JavaScript console messages
        binding.webviewMuscleMap.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    android.util.Log.d("MuscleMapDialog", 
                        "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                }
                return true
            }
        }

        binding.webviewMuscleMap.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isWebViewReady = true
                binding.progressLoading.visibility = View.GONE
                updateMuscleMap()
            }
        }

        binding.webviewMuscleMap.loadUrl("file:///android_asset/muscle_map.html")
    }

    private fun updateMuscleMap() {
        if (!isWebViewReady) {
            android.util.Log.d("MuscleMapDialog", "WebView not ready yet")
            return
        }

        if (primaryMuscles.isEmpty() && secondaryMuscles.isEmpty()) {
            return // Empty state is already shown
        }

        // Convert muscle sets to JavaScript arrays
        val primaryArray = primaryMuscles.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ", "
        ) { "'${it.name}'" }

        val secondaryArray = secondaryMuscles.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ", "
        ) { "'${it.name}'" }

        // Call JavaScript setHighlights function
        val jsCode = """
            (function() {
                try {
                    if (typeof setHighlights === 'function') {
                        setHighlights($primaryArray, $secondaryArray);
                        return 'setHighlights called';
                    } else if (typeof window.setHighlights === 'function') {
                        window.setHighlights($primaryArray, $secondaryArray);
                        return 'window.setHighlights called';
                    } else {
                        console.error('setHighlights function not found!');
                        return 'ERROR: setHighlights not found';
                    }
                } catch (e) {
                    console.error('Error calling setHighlights:', e);
                    return 'ERROR: ' + e.message;
                }
            })();
        """.trimIndent()

        binding.webviewMuscleMap.evaluateJavascript(jsCode) { result ->
            android.util.Log.d("MuscleMapDialog", "JavaScript result: $result")
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

