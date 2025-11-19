package com.lilfitness.utils

import java.util.Locale

object WorkoutTypeFormatter {

    private val validTypes = setOf("heavy", "light", "custom")

    fun normalize(type: String?): String {
        val lower = type?.lowercase(Locale.getDefault()) ?: "heavy"
        return if (lower in validTypes) lower else "heavy"
    }

    fun label(type: String?): String {
        return when (normalize(type)) {
            "heavy" -> "Heavy"
            "light" -> "Light"
            else -> "Custom"
        }
    }
}

