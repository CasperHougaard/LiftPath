package com.liftpath.helpers

import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.liftpath.components.ExerciseLibraryUpdateBottomSheet
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.TrainingData

object CatalogMergeHelper {

    const val DECISION_KEEP_LOCAL = "KEEP_LOCAL"
    const val DECISION_USE_CATALOG = "USE_CATALOG"
    const val DECISION_ADD = "ADD"
    const val DECISION_SKIP = "SKIP"

    const val FRAGMENT_RESULT_KEY = "catalog_merge_result"
    const val BUNDLE_APPLIED_JSON = "applied_candidates_json"

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

    fun applyMerge(
        trainingData: TrainingData,
        candidates: List<MergeCandidate>,
        prefs: CatalogMergePrefs
    ): CatalogMergePrefs {
        val lib = trainingData.exerciseLibrary
        val newDecisions = prefs.conflictDecisions.toMutableMap()

        var nextId = lib.maxOfOrNull { it.id } ?: 0

        for (c in candidates) {
            when (c.kind) {
                MergeKind.NEW -> {
                    if (c.userDecision == DECISION_ADD) {
                        nextId += 1
                        lib.add(
                            c.catalogItem.copy(
                                id = nextId,
                                isFavorite = false
                            )
                        )
                    }
                }
                MergeKind.CONFLICT -> {
                    val userItem = c.existingUserItem ?: continue
                    val key = normalizeExerciseName(c.catalogItem.name)
                    newDecisions[key] = c.userDecision
                    if (c.userDecision == DECISION_USE_CATALOG) {
                        val idx = lib.indexOfFirst { it.id == userItem.id }
                        if (idx >= 0) {
                            val old = lib[idx]
                            lib[idx] = old.copy(
                                region = c.catalogItem.region,
                                pattern = c.catalogItem.pattern,
                                tier = c.catalogItem.tier,
                                primaryTargets = c.catalogItem.primaryTargets,
                                secondaryTargets = c.catalogItem.secondaryTargets,
                                manualMechanics = c.catalogItem.manualMechanics
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
        appliedJson: String?
    ) {
        if (appliedJson.isNullOrBlank()) return
        val gson = Gson()
        val type = object : TypeToken<List<MergeCandidate>>() {}.type
        val list: List<MergeCandidate> = try {
            gson.fromJson(appliedJson, type) ?: return
        } catch (_: Exception) {
            return
        }
        val prefsManager = CatalogMergePrefsManager(activity)
        val prefs = prefsManager.getPrefs()
        val data = jsonHelper.readTrainingData()
        val updatedPrefs = applyMerge(data, list, prefs)
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
