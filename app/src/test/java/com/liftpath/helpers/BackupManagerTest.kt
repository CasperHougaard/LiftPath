package com.liftpath.helpers

import com.google.gson.Gson
import com.liftpath.models.BackupBundle
import com.liftpath.models.BackupPrefEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the parts of the backup path that don't need a Context: bundle serialization and the
 * validation gate every restore goes through. The gate is the important half — it is the only
 * thing standing between a user tapping "restore" on the wrong file and losing their history.
 */
class BackupManagerTest {

    private val gson = Gson()

    /**
     * Guards the backup coverage contract in CLAUDE.md, which BackupManager cannot enforce
     * itself: it enumerates state rather than discovering it, so a new prefs file that nobody
     * adds to the list is silently lost on a phone swap. Appearance is a user choice, not
     * device wiring, so it belongs in the backup.
     */
    @Test
    fun `appearance prefs are covered by the backup contract`() {
        assertTrue(
            "AppearanceManager.PREFS_NAME ('${AppearanceManager.PREFS_NAME}') is missing from " +
                "BackupManager.BACKED_UP_PREFS — the user's theme choice would not survive a " +
                "phone swap. See the Backup Coverage Contract in CLAUDE.md.",
            BackupManager.BACKED_UP_PREFS.contains(AppearanceManager.PREFS_NAME)
        )
    }

    private fun bundleWithTrainingData(
        formatVersion: Int = BackupBundle.CURRENT_FORMAT_VERSION
    ): BackupBundle = BackupBundle(
        formatVersion = formatVersion,
        createdAtMs = 1_700_000_000_000L,
        appVersionName = "3.00.002",
        appVersionCode = 13,
        deviceModel = "Test Device",
        files = mutableMapOf(
            "training_data.json" to """{"trainings":[],"exerciseLibrary":[],"workoutPlans":[]}"""
        )
    )

    @Test
    fun `round trips through json unchanged`() {
        val original = bundleWithTrainingData().apply {
            prefs["progression_settings"] = mutableMapOf(
                "restSeconds" to BackupPrefEntry(BackupPrefEntry.TYPE_INT, "90"),
                "useKg" to BackupPrefEntry(BackupPrefEntry.TYPE_BOOLEAN, "true"),
                "tags" to BackupPrefEntry(
                    type = BackupPrefEntry.TYPE_STRING_SET,
                    values = listOf("a", "b")
                )
            )
        }

        val restored = BackupManager.parse(BackupManager.serialize(original)).getOrThrow()

        assertEquals(original.createdAtMs, restored.createdAtMs)
        assertEquals(original.appVersionCode, restored.appVersionCode)
        assertEquals(original.files, restored.files)
        val prefs = restored.prefs["progression_settings"]
        assertNotNull(prefs)
        assertEquals("90", prefs!!["restSeconds"]?.value)
        assertEquals(listOf("a", "b"), prefs["tags"]?.values)
    }

    @Test
    fun `rejects a bundle from a newer format version`() {
        val json = gson.toJson(
            bundleWithTrainingData(formatVersion = BackupBundle.CURRENT_FORMAT_VERSION + 1)
        )

        val error = BackupManager.parse(json).exceptionOrNull()

        assertNotNull(error)
        assertTrue(
            "Message should tell the user to update: ${error?.message}",
            error!!.message!!.contains("newer version")
        )
    }

    @Test
    fun `rejects a bundle with no training data`() {
        val json = gson.toJson(
            BackupBundle(
                createdAtMs = 1L,
                files = mutableMapOf("withings_body_data.json" to "{}")
            )
        )

        val error = BackupManager.parse(json).exceptionOrNull()

        assertNotNull(error)
        assertTrue(error!!.message!!.contains("missing training data"))
    }

    @Test
    fun `rejects an empty bundle`() {
        val error = BackupManager.parse(gson.toJson(BackupBundle())).exceptionOrNull()

        assertNotNull(error)
        assertTrue(error!!.message!!.contains("no data"))
    }

    @Test
    fun `rejects a file that is not json`() {
        val error = BackupManager.parse("this is not a backup").exceptionOrNull()

        assertNotNull(error)
        assertTrue(error!!.message!!.contains("not a valid LiftPath backup"))
    }

    @Test
    fun `describe reports the session count`() {
        val bundle = BackupBundle(
            createdAtMs = 1_700_000_000_000L,
            files = mutableMapOf(
                "training_data.json" to """
                    {"trainings":[
                      {"trainingNumber":1,"date":"2026-01-01","exercises":[]},
                      {"trainingNumber":2,"date":"2026-01-03","exercises":[]}
                    ],"exerciseLibrary":[],"workoutPlans":[]}
                """.trimIndent()
            )
        )

        assertEquals(2, BackupManager.sessionCount(bundle))
        assertTrue(BackupManager.describe(bundle).contains("2 workouts"))
    }

    @Test
    fun `default file name is sortable and scoped to the app`() {
        val name = BackupManager.defaultFileName(1_700_000_000_000L)

        assertTrue(name.startsWith("liftpath_backup_"))
        assertTrue(name.endsWith(".json"))
        // Pruning relies on lexicographic order matching chronological order.
        val later = BackupManager.defaultFileName(1_700_000_060_000L)
        assertTrue("$name should sort before $later", name < later)
    }
}
