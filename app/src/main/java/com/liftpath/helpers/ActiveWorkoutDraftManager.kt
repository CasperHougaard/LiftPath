package com.liftpath.helpers

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.liftpath.models.ActiveWorkoutDraft
import java.io.File

class ActiveWorkoutDraftManager(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()
    private val draftFile = File(appContext.cacheDir, "active_workout_draft.json")

    fun hasDraft(): Boolean = draftFile.exists()

    fun saveDraft(draft: ActiveWorkoutDraft) {
        if (draft.entries.isEmpty()) {
            clearDraft()
            return
        }
        runCatching {
            val json = gson.toJson(draft)
            draftFile.writeText(json)
        }.onFailure {
            Log.e(TAG, "Failed to save active workout draft", it)
        }
    }

    fun loadDraft(): ActiveWorkoutDraft? {
        if (!draftFile.exists()) return null
        return try {
            val json = draftFile.readText()
            gson.fromJson(json, ActiveWorkoutDraft::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load active workout draft", e)
            clearDraft()
            null
        }
    }

    fun clearDraft() {
        runCatching {
            if (draftFile.exists()) {
                draftFile.delete()
            }
        }.onFailure {
            Log.e(TAG, "Failed to clear active workout draft", it)
        }
    }

    companion object {
        private const val TAG = "ActiveWorkoutDraftMgr"
    }
}

