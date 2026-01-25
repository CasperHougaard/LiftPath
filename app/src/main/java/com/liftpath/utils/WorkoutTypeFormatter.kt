package com.liftpath.utils

import com.liftpath.models.SessionIntensity
import com.liftpath.models.SetIntent
import java.util.Locale

object WorkoutTypeFormatter {

    const val HEAVY = "heavy"
    const val LIGHT = "light"
    const val CUSTOM = "custom"

    private val validTypes = setOf(HEAVY, LIGHT, CUSTOM)

    fun normalize(type: String?): String {
        val lower = type?.lowercase(Locale.getDefault()) ?: HEAVY
        return if (lower in validTypes) lower else HEAVY
    }

    fun label(type: String?): String {
        return when (normalize(type)) {
            HEAVY -> "Heavy"
            LIGHT -> "Light"
            else -> "Manual"
        }
    }

    fun fromIntensity(intensity: SessionIntensity): String {
        return when (intensity) {
            SessionIntensity.HEAVY -> HEAVY
            SessionIntensity.LIGHT -> LIGHT
        }
    }

    fun toIntensity(type: String?): SessionIntensity {
        return when (normalize(type)) {
            LIGHT -> SessionIntensity.LIGHT
            else -> SessionIntensity.HEAVY
        }
    }
    
    fun intentLabel(intent: SetIntent): String {
        return intent.displayName
    }
    
    fun inferIntentFromLegacy(workoutType: String?, reps: Int): SetIntent {
        return when (workoutType?.lowercase()) {
            "heavy" -> if (reps <= 7) SetIntent.STRENGTH else SetIntent.BUILD
            "light" -> if (reps >= 15) SetIntent.FLUSH else SetIntent.BUILD
            else -> when {
                reps <= 6 -> SetIntent.STRENGTH
                reps <= 15 -> SetIntent.BUILD
                else -> SetIntent.FLUSH
            }
        }
    }
}