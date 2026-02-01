package com.liftpath.helpers

object DurationHelper {
    /**
     * Formats seconds as "HH:mm:ss" string
     * @param seconds Total seconds to format
     * @return Formatted string like "01:23:45" or "00:05:30"
     */
    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    /**
     * Parses a formatted duration string (HH:mm:ss or mm:ss) back to seconds
     * @param durationString Formatted duration string
     * @return Total seconds, or null if parsing fails
     */
    fun parseDurationToSeconds(durationString: String): Long? {
        return try {
            val parts = durationString.trim().split(":")
            when (parts.size) {
                2 -> {
                    // mm:ss format
                    val minutes = parts[0].toLong()
                    val seconds = parts[1].toLong()
                    minutes * 60 + seconds
                }
                3 -> {
                    // HH:mm:ss format
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val seconds = parts[2].toLong()
                    hours * 3600 + minutes * 60 + seconds
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

