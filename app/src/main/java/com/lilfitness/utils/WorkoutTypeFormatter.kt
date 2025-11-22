package com.lilfitness.utils

import com.lilfitness.models.SessionIntensity
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
            else -> "Custom"
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
}