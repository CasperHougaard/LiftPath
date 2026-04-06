package com.liftpath.helpers

import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.gson.Gson
import com.liftpath.components.ExerciseLibraryUpdateBottomSheet
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.TrainingData

/**
 * Gson-safe apply payload (strings only). [MergeCandidate] must not be round-tripped through Gson —
 * nested [ExerciseLibraryItem] and enums often deserialize as wrong types, so "use catalog" never runs.
 */
data class AppliedMergeChoice(
    val nameKey: String = "",
    val kind: String = "",
    val decision: String = ""
)

object CatalogMergeHelper {

    const val DECISION_KEEP_LOCAL = "KEEP_LOCAL"
    const val DECISION_USE_CATALOG = "USE_CATALOG"
    const val DECISION_ADD = "ADD"
    const val DECISION_SKIP = "SKIP"

    fun normalizeExerciseName(name: String): String =
        name.trim().lowercase()

    private fun metadataMatches(catalog: ExerciseLibraryItem, user: ExerciseLibraryItem): Boolean {
        return catalog.region == user.region &&
            catalog.pattern == user.pattern &&
            catalog.tier == user.tier &&
            catalog.primaryTargets == user.primaryTargets &&
            catalog.secondaryTargets == user.secondaryTargets &&
            catalog.manualMechanics == user.manualMechanics
    }

    fun computeDiff(
        userLibrary: List<ExerciseLibraryItem>,
        catalog: List<ExerciseLibraryItem>,
        prefs: CatalogMergePrefs
    ): List<MergeCandidate> {
        val out = mutableListOf<MergeCandidate>()
        val userByNormName = userLibrary.groupBy { normalizeExerciseName(it.name) }

        for (cat in catalog) {
            val key = normalizeExerciseName(cat.name)
            val matches = userByNormName[key].orEmpty()
            if (matches.isEmpty()) {
                out.add(
                    MergeCandidate(
                        kind = MergeKind.NEW,
                        catalogItem = cat,
                        existingUserItem = null,
                        storedDecision = null,
                        userDecision = DECISION_ADD
                    )
                )
            } else {
                val user = matches.first()
                if (metadataMatches(cat, user)) {
                    continue
                }
                val stored = prefs.conflictDecisions[key]
                val initial = when (stored) {
                    DECISION_USE_CATALOG -> DECISION_USE_CATALOG
                    else -> DECISION_KEEP_LOCAL
                }
                out.add(
                    MergeCandidate(
                        kind = MergeKind.CONFLICT,
                        catalogItem = cat,
                        existingUserItem = user,
                        storedDecision = stored,
                        userDecision = initial
                    )
                )
            }
        }
        return out
    }

    fun mergeCandidatesToAppliedChoices(candidates: List<MergeCandidate>): List<AppliedMergeChoice> =
        candidates.map { c ->
            AppliedMergeChoice(
                nameKey = normalizeExerciseName(c.catalogItem.name),
                kind = c.kind.name,
                decision = c.userDecision
            )
        }

    fun applyMerge(
        trainingData: TrainingData,
        appliedRows: List<AppliedMergeChoice>,
        prefs: CatalogMergePrefs,
        bundledCatalog: List<ExerciseLibraryItem>
    ): CatalogMergePrefs {
        val lib = trainingData.exerciseLibrary
        val newDecisions = prefs.conflictDecisions.toMutableMap()
        val catalogByNormName = bundledCatalog.associateBy { normalizeExerciseName(it.name) }

        var nextId = lib.maxOfOrNull { it.id } ?: 0

        for (row in appliedRows) {
            val key = normalizeExerciseName(row.nameKey)
            if (key.isEmpty()) continue
            when (row.kind) {
                MergeKind.NEW.name -> {
                    if (row.decision == DECISION_ADD) {
                        val catRow = catalogByNormName[key] ?: continue
                        nextId += 1
                        lib.add(
                            catRow.copy(
                                id = nextId,
                                isFavorite = false
                            )
                        )
                    }
                }
                MergeKind.CONFLICT.name -> {
                    newDecisions[key] = row.decision
                    if (row.decision == DECISION_USE_CATALOG) {
                        val catRow = catalogByNormName[key] ?: continue
                        val idx = lib.indexOfFirst { normalizeExerciseName(it.name) == key }
                        if (idx >= 0) {
                            val old = lib[idx]
                            lib[idx] = old.copy(
                                region = catRow.region,
                                pattern = catRow.pattern,
                                tier = catRow.tier,
                                primaryTargets = catRow.primaryTargets,
                                secondaryTargets = catRow.secondaryTargets,
                                manualMechanics = catRow.manualMechanics
                            )
                        }
                    }
                }
            }
        }

        return prefs.copy(
            conflictDecisions = newDecisions,
            lastOfferedCatalogVersion = DefaultExercisesHelper.CATALOG_VERSION
        )
    }

    fun checkAndOfferIfNeeded(
        activity: FragmentActivity,
        jsonHelper: JsonHelper,
        fragmentManager: FragmentManager
    ) {
        val prefsManager = CatalogMergePrefsManager(activity)
        val prefs = prefsManager.getPrefs()
        if (prefs.lastOfferedCatalogVersion >= DefaultExercisesHelper.CATALOG_VERSION) {
            return
        }

        val trainingData = jsonHelper.readTrainingData()
        val catalog = DefaultExercisesHelper.getPopularDefaults()
        val candidates = computeDiff(trainingData.exerciseLibrary, catalog, prefs)

        if (candidates.isEmpty()) {
            prefsManager.savePrefs(
                prefs.copy(lastOfferedCatalogVersion = DefaultExercisesHelper.CATALOG_VERSION)
            )
            return
        }

        val gson = Gson()
        val json = gson.toJson(candidates)
        ExerciseLibraryUpdateBottomSheet.newInstance(json)
            .show(fragmentManager, "ExerciseLibraryUpdateBottomSheet")
    }

    fun handleMergeResult(
        activity: FragmentActivity,
        jsonHelper: JsonHelper,
        appliedRows: List<AppliedMergeChoice>
    ) {
        if (appliedRows.isEmpty()) return
        val prefsManager = CatalogMergePrefsManager(activity)
        val prefs = prefsManager.getPrefs()
        val data = jsonHelper.readTrainingData()
        val bundledCatalog = DefaultExercisesHelper.getPopularDefaults()
        val updatedPrefs = applyMerge(data, appliedRows, prefs, bundledCatalog)
        jsonHelper.writeTrainingData(data)
        prefsManager.savePrefs(updatedPrefs)
    }
}

enum class MergeKind {
    NEW,
    CONFLICT
}

data class MergeCandidate(
    val kind: MergeKind,
    val catalogItem: ExerciseLibraryItem,
    val existingUserItem: ExerciseLibraryItem?,
    val storedDecision: String?,
    var userDecision: String
)
