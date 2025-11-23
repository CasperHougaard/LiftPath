package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.liftpath.R
import com.liftpath.databinding.ActivityEditExerciseBinding
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.showWithTransparentWindow
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.liftpath.models.BodyRegion
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.Mechanics
import com.liftpath.models.MovementPattern
import com.liftpath.models.TargetMuscle
import com.liftpath.models.Tier

class EditExerciseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditExerciseBinding
    private lateinit var jsonHelper: JsonHelper
    private var exerciseId: Int = -1
    // Flag to ensure muscle map update only happens after WebView is ready
    private var isWebViewReady = false

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
        const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditExerciseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)
        exerciseId = intent.getIntExtra(EXTRA_EXERCISE_ID, -1)

        setupBackgroundAnimation()
        setupDropdowns()
        setupWebView()
        loadExerciseData()
        setupClickListeners()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun setupDropdowns() {
        // 1. Body Region
        val regions = BodyRegion.values().map { it.displayName }
        val regionAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, regions)
        binding.dropdownRegion.setAdapter(regionAdapter)

        // 2. Movement Pattern (Show Human Name)
        val patterns = MovementPattern.values().map { it.displayName }
        val patternAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, patterns)
        binding.dropdownPattern.setAdapter(patternAdapter)

        // 3. Tier
        val tiers = Tier.values().map { it.displayName }
        val tierAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, tiers)
        binding.dropdownTier.setAdapter(tierAdapter)

        // 4. Mechanics
        val manualMechanics = Mechanics.values().map { it.displayName }
        val mechanicsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, manualMechanics)
        binding.dropdownMechanics.setAdapter(mechanicsAdapter)

        // 5. Setup Target Muscle Chips
        setupTargetMuscleChips()
    }

    private fun setupTargetMuscleChips() {
        // Create chips for all TargetMuscle values
        val allMuscles = TargetMuscle.values()

        val onChipChecked: (View, Boolean) -> Unit = { _, _ -> updateMuscleMap() }

        // Primary Targets ChipGroup
        binding.chipGroupPrimaryTargets.removeAllViews()
        allMuscles.forEach { muscle ->
            val chip = Chip(this)
            chip.text = muscle.displayName
            chip.isCheckable = true
            chip.tag = muscle
            chip.chipStrokeWidth = 1f
            chip.chipStrokeColor = getColorStateList(R.color.fitness_primary)
            chip.setTextColor(getColorStateList(R.color.fitness_text_primary))
            chip.setOnCheckedChangeListener(onChipChecked)
            binding.chipGroupPrimaryTargets.addView(chip)
        }

        // Secondary Targets ChipGroup
        binding.chipGroupSecondaryTargets.removeAllViews()
        allMuscles.forEach { muscle ->
            val chip = Chip(this)
            chip.text = muscle.displayName
            chip.isCheckable = true
            chip.tag = muscle
            chip.chipStrokeWidth = 1f
            chip.chipStrokeColor = getColorStateList(R.color.fitness_primary)
            chip.setTextColor(getColorStateList(R.color.fitness_text_primary))
            chip.setOnCheckedChangeListener(onChipChecked)
            binding.chipGroupSecondaryTargets.addView(chip)
        }
    }

    private fun setupWebView() {
        // Essential setup for asset loading and interaction
        binding.webViewMuscleMap.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // CRITICAL SETTINGS FOR LOCAL FILES
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true // Allows JS to load other local files
            allowUniversalAccessFromFileURLs = true // Allows JS to access content from any origin
        }
        binding.webViewMuscleMap.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        // Add WebChromeClient to capture JavaScript console messages
        binding.webViewMuscleMap.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    android.util.Log.d("WebViewConsole", 
                        "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                }
                return true
            }
        }
        
        binding.webViewMuscleMap.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Set flag and then update muscle map
                isWebViewReady = true
                updateMuscleMap()
            }
        }
        binding.webViewMuscleMap.loadUrl("file:///android_asset/muscle_map.html")
    }

    private fun updateMuscleMap() {
        // Prevent JavaScript call until WebView is confirmed ready
        if (!isWebViewReady) {
            android.util.Log.d("MuscleMap", "WebView not ready yet")
            return
        }

        val primaryTargets = getSelectedTargetMuscles(binding.chipGroupPrimaryTargets)
        val secondaryTargets = getSelectedTargetMuscles(binding.chipGroupSecondaryTargets)
        
        // Combine primary and secondary targets
        val allTargets = (primaryTargets + secondaryTargets).distinct()
        
        android.util.Log.d("MuscleMap", "Updating muscle map with ${allTargets.size} targets: ${allTargets.map { it.name }}")
        
        // Convert enum list to JavaScript array format: ['BICEPS', 'TRICEPS_LONG']
        val jsArray = allTargets.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ", "
        ) { "'${it.name}'" }
        
        // Call the JavaScript function setHighlights()
        // Try both window.setHighlights and setHighlights for compatibility
        val jsCode = """
            (function() {
                try {
                    if (typeof setHighlights === 'function') {
                        setHighlights($jsArray);
                        return 'setHighlights called';
                    } else if (typeof window.setHighlights === 'function') {
                        window.setHighlights($jsArray);
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
        android.util.Log.d("MuscleMap", "Calling JavaScript: setHighlights($jsArray)")
        binding.webViewMuscleMap.evaluateJavascript(jsCode) { result ->
            android.util.Log.d("MuscleMap", "JavaScript result: $result")
        }
    }

    private fun loadExerciseData() {
        if (exerciseId != -1) {
            binding.textEditExerciseTitle.text = "Edit Exercise"
            binding.cardDelete.visibility = View.VISIBLE

            val trainingData = jsonHelper.readTrainingData()
            val exercise = trainingData.exerciseLibrary.find { it.id == exerciseId }

            if (exercise != null) {
                binding.editTextExerciseName.setText(exercise.name)

                // Set Dropdown Values (Using displayName)
                exercise.region?.let { binding.dropdownRegion.setText(it.displayName, false) }
                exercise.pattern?.let { binding.dropdownPattern.setText(it.displayName, false) }
                exercise.tier?.let { binding.dropdownTier.setText(it.displayName, false) }
                // Use manualMechanics if set, otherwise use computed mechanics
                val mechanicsToDisplay = exercise.manualMechanics ?: exercise.mechanics
                binding.dropdownMechanics.setText(mechanicsToDisplay.displayName, false)

                // Set Target Muscle Chips
                setSelectedTargetMuscles(exercise.primaryTargets, exercise.secondaryTargets)
                // updateMuscleMap() is called automatically via onPageFinished or chip listener
            }
        } else {
            binding.textEditExerciseTitle.text = "Create New Exercise"
            binding.cardDelete.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.buttonSaveExercise.setOnClickListener { saveExercise() }
        binding.cardDelete.setOnClickListener { showDeleteConfirmationDialog() }
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonCancel.setOnClickListener { finish() }
        
        // TEST: Button to test JavaScript connection
        binding.buttonTestSvg.setOnClickListener {
            if (isWebViewReady) {
                // Test: Change LATS element color in SVG
                val testCode = """
                    (function() {
                        console.log('Test button clicked - looking for LATS element');
                        
                        // Check main document first (fallback method loads SVG here)
                        var doc = document;
                        var latsInMain = document.getElementById('LATS');
                        
                        if (latsInMain) {
                            console.log('Found LATS in main document');
                            doc = document;
                        } else {
                            // Try to get SVG document from object tag
                            var svgObject = document.getElementById('svg-object');
                            var svgDoc = null;
                            
                            if (svgObject) {
                                try {
                                    svgDoc = svgObject.contentDocument;
                                    if (svgDoc && svgDoc.getElementById('LATS')) {
                                        doc = svgDoc;
                                        console.log('Using svgDoc from object');
                                    } else {
                                        console.log('svgDoc accessible but LATS not found there');
                                    }
                                } catch (e) {
                                    console.log('Cannot access svgDoc:', e.message);
                                }
                            }
                        }
                        
                        console.log('Using document:', doc === document ? 'main document' : 'svgDoc');
                        
                        // Look for LATS element (it's a rect element)
                        var latsElement = doc.getElementById('LATS');
                        if (latsElement) {
                            console.log('Found LATS element:', latsElement.tagName, latsElement.id);
                            
                            // Get current fill - check attribute, style, or computed
                            var currentFill = latsElement.getAttribute('fill');
                            if (!currentFill) {
                                currentFill = window.getComputedStyle ? window.getComputedStyle(latsElement).fill : null;
                            }
                            console.log('Current fill:', currentFill);
                            
                            // Toggle between red and default gray
                            var isRed = currentFill === '#FF3B30' || currentFill === 'rgb(255, 59, 48)' || currentFill === '#ff3b30';
                            
                            if (isRed) {
                                // Change to gray
                                latsElement.removeAttribute('style');
                                latsElement.setAttribute('fill', '#444444');
                                latsElement.setAttribute('fill-opacity', '1');
                                latsElement.setAttribute('stroke', '#ffffff');
                                latsElement.setAttribute('stroke-width', '2px');
                                console.log('Changed LATS to default gray');
                                return 'LATS changed to gray';
                            } else {
                                // Change to red
                                latsElement.removeAttribute('style');
                                latsElement.setAttribute('fill', '#FF3B30');
                                latsElement.setAttribute('fill-opacity', '1');
                                latsElement.setAttribute('stroke', '#ffffff');
                                latsElement.setAttribute('stroke-width', '2px');
                                console.log('Changed LATS to red');
                                return 'LATS changed to red';
                            }
                        } else {
                            console.error('LATS element not found!');
                            
                            // Debug: Check what's in the document
                            var svgCount = doc.querySelectorAll('svg').length;
                            var rectCount = doc.querySelectorAll('rect').length;
                            var pathCount = doc.querySelectorAll('path').length;
                            var gCount = doc.querySelectorAll('g').length;
                            console.log('Document stats - SVG:', svgCount, 'Rect:', rectCount, 'Path:', pathCount, 'G:', gCount);
                            
                            // List all available IDs
                            var allIds = [];
                            doc.querySelectorAll('[id]').forEach(function(el) {
                                allIds.push(el.id);
                            });
                            console.log('Available IDs:', allIds);
                            
                            // Try to find LATS in main document too
                            var mainDocLats = document.getElementById('LATS');
                            if (mainDocLats) {
                                console.log('Found LATS in main document!');
                                return 'LATS found in main document, not svgDoc';
                            }
                            
                            return 'LATS not found. Available IDs: ' + allIds.join(', ') + ' | SVG count: ' + svgCount;
                        }
                    })();
                """.trimIndent()
                binding.webViewMuscleMap.evaluateJavascript(testCode) { result ->
                    android.util.Log.d("WebViewTest", "JavaScript result: $result")
                }
            } else {
                android.util.Log.d("WebViewTest", "WebView not ready yet")
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_delete_exercise))
            .setMessage(getString(R.string.dialog_message_delete_exercise))
            .setPositiveButton(getString(R.string.button_delete)) { _, _ -> deleteExercise() }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun deleteExercise() {
        val trainingData = jsonHelper.readTrainingData()
        trainingData.exerciseLibrary.removeAll { it.id == exerciseId }
        trainingData.trainings.forEach { session ->
            session.exercises.removeAll { it.exerciseId == exerciseId }
        }
        jsonHelper.writeTrainingData(trainingData)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun setSelectedTargetMuscles(primaryTargets: List<TargetMuscle>, secondaryTargets: List<TargetMuscle>) {
        // Set primary targets
        for (i in 0 until binding.chipGroupPrimaryTargets.childCount) {
            val chip = binding.chipGroupPrimaryTargets.getChildAt(i) as Chip
            val muscle = chip.tag as? TargetMuscle
            chip.isChecked = muscle != null && primaryTargets.contains(muscle)
        }

        // Set secondary targets
        for (i in 0 until binding.chipGroupSecondaryTargets.childCount) {
            val chip = binding.chipGroupSecondaryTargets.getChildAt(i) as Chip
            val muscle = chip.tag as? TargetMuscle
            chip.isChecked = muscle != null && secondaryTargets.contains(muscle)
        }
        
        // Explicitly update muscle map after setting chips
        // (Programmatically setting isChecked may not trigger listeners in some cases)
        updateMuscleMap()
    }

    private fun getSelectedTargetMuscles(chipGroup: ChipGroup): List<TargetMuscle> {
        val selectedMuscles = mutableListOf<TargetMuscle>()
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            if (chip.isChecked) {
                val muscle = chip.tag as? TargetMuscle
                muscle?.let { selectedMuscles.add(it) }
            }
        }
        return selectedMuscles
    }

    private fun saveExercise() {
        val newName = binding.editTextExerciseName.text.toString().trim()
        if (newName.isEmpty()) {
            binding.editTextExerciseName.error = "Exercise name cannot be empty"
            return
        }

        // Get Display Strings
        val regionStr = binding.dropdownRegion.text.toString()
        val patternStr = binding.dropdownPattern.text.toString()
        val tierStr = binding.dropdownTier.text.toString()
        val mechanicsStr = binding.dropdownMechanics.text.toString()

        // Reverse Lookup: Find Enum by displayName
        val selectedRegion = BodyRegion.values().find { it.displayName == regionStr }
        val selectedPattern = MovementPattern.values().find { it.displayName == patternStr }
        val selectedTier = Tier.values().find { it.displayName == tierStr }
        val selectedMechanics = Mechanics.values().find { it.displayName == mechanicsStr }

        // Get selected target muscles
        val selectedPrimaryTargets = getSelectedTargetMuscles(binding.chipGroupPrimaryTargets)
        val selectedSecondaryTargets = getSelectedTargetMuscles(binding.chipGroupSecondaryTargets)

        val trainingData = jsonHelper.readTrainingData()

        if (exerciseId != -1) {
            val existingExercise = trainingData.exerciseLibrary.find { it.id == exerciseId }
            if (existingExercise != null) {
                val index = trainingData.exerciseLibrary.indexOf(existingExercise)
                if (index != -1) {
                    trainingData.exerciseLibrary[index] = existingExercise.copy(
                        name = newName,
                        region = selectedRegion,
                        pattern = selectedPattern,
                        tier = selectedTier,
                        manualMechanics = selectedMechanics,
                        primaryTargets = selectedPrimaryTargets,
                        secondaryTargets = selectedSecondaryTargets
                    )
                }
                // Legacy name update
                trainingData.trainings.forEach { session ->
                    session.exercises.forEach { entry ->
                        if (entry.exerciseId == exerciseId) entry.exerciseName = newName
                    }
                }
            }
        } else {
            val nextId = (trainingData.exerciseLibrary.maxOfOrNull { it.id } ?: 0) + 1
            val newExercise = ExerciseLibraryItem(
                id = nextId,
                name = newName,
                region = selectedRegion,
                pattern = selectedPattern,
                tier = selectedTier,
                manualMechanics = selectedMechanics,
                primaryTargets = selectedPrimaryTargets,
                secondaryTargets = selectedSecondaryTargets
            )
            trainingData.exerciseLibrary.add(newExercise)
            exerciseId = nextId
        }

        jsonHelper.writeTrainingData(trainingData)

        val resultIntent = Intent().apply {
            putExtra(EXTRA_EXERCISE_ID, exerciseId)
            putExtra(EXTRA_EXERCISE_NAME, newName)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}