package com.liftpath.helpers

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.liftpath.R

/** Canvas/ink/accent swatch colours for one mode, used by the light/dark palette pickers
 *  in Settings. Flat, qualifier-free resources (see `lp_palette_previews.xml`) so both a
 *  palette's light and dark look can be shown at once regardless of which mode is active. */
data class SwatchPreview(
    @ColorRes val canvas: Int,
    @ColorRes val ink: Int,
    @ColorRes val accent: Int
)

/**
 * The eight palettes the user can choose between — independently for light mode and dark
 * mode (see [AppearanceManager.lightTheme] / [darkTheme]).
 *
 * Each entry maps to one `ThemeOverlay.LiftPath.*`, which binds the ~27 `lp*` token
 * attributes to that palette. Adding a ninth means: one `lp_palette_*.xml` pair, one
 * overlay block in `themes.xml`, one preview row in `lp_palette_previews.xml`, one entry
 * here. No layout should need touching.
 */
enum class LiftPathTheme(
    /** Stable string written to prefs. Never renumber or reuse — ordinals are not used
     *  precisely so that reordering this enum cannot silently change a user's choice. */
    val prefValue: String,
    /**
     * A theme *overlay*, not a full theme. `Activity.setTheme` merges rather than replaces,
     * so applying a full app theme over an activity that declares its own — as
     * RestTimerDialogActivity does — would clobber its dialog windowing. An overlay carries
     * only colour, so it is safe on every activity. See the header of `themes.xml`.
     */
    @StyleRes val overlayRes: Int,
    @StringRes val labelRes: Int,
    @StringRes val subtitleRes: Int,
    val previewLight: SwatchPreview,
    val previewDark: SwatchPreview
) {
    PAPER(
        prefValue = "paper",
        overlayRes = R.style.ThemeOverlay_LiftPath_Paper,
        labelRes = R.string.theme_paper_label,
        subtitleRes = R.string.theme_paper_subtitle,
        previewLight = SwatchPreview(R.color.paper_preview_light_canvas, R.color.paper_preview_light_ink, R.color.paper_preview_light_accent),
        previewDark = SwatchPreview(R.color.paper_preview_dark_canvas, R.color.paper_preview_dark_ink, R.color.paper_preview_dark_accent)
    ),
    CHALK(
        prefValue = "chalk",
        overlayRes = R.style.ThemeOverlay_LiftPath_Chalk,
        labelRes = R.string.theme_chalk_label,
        subtitleRes = R.string.theme_chalk_subtitle,
        previewLight = SwatchPreview(R.color.chalk_preview_light_canvas, R.color.chalk_preview_light_ink, R.color.chalk_preview_light_accent),
        previewDark = SwatchPreview(R.color.chalk_preview_dark_canvas, R.color.chalk_preview_dark_ink, R.color.chalk_preview_dark_accent)
    ),
    BONE(
        prefValue = "bone",
        overlayRes = R.style.ThemeOverlay_LiftPath_Bone,
        labelRes = R.string.theme_bone_label,
        subtitleRes = R.string.theme_bone_subtitle,
        previewLight = SwatchPreview(R.color.bone_preview_light_canvas, R.color.bone_preview_light_ink, R.color.bone_preview_light_accent),
        previewDark = SwatchPreview(R.color.bone_preview_dark_canvas, R.color.bone_preview_dark_ink, R.color.bone_preview_dark_accent)
    ),
    STEEL(
        prefValue = "steel",
        overlayRes = R.style.ThemeOverlay_LiftPath_Steel,
        labelRes = R.string.theme_steel_label,
        subtitleRes = R.string.theme_steel_subtitle,
        previewLight = SwatchPreview(R.color.steel_preview_light_canvas, R.color.steel_preview_light_ink, R.color.steel_preview_light_accent),
        previewDark = SwatchPreview(R.color.steel_preview_dark_canvas, R.color.steel_preview_dark_ink, R.color.steel_preview_dark_accent)
    ),
    CLOUD(
        prefValue = "cloud",
        overlayRes = R.style.ThemeOverlay_LiftPath_Cloud,
        labelRes = R.string.theme_cloud_label,
        subtitleRes = R.string.theme_cloud_subtitle,
        previewLight = SwatchPreview(R.color.cloud_preview_light_canvas, R.color.cloud_preview_light_ink, R.color.cloud_preview_light_accent),
        previewDark = SwatchPreview(R.color.cloud_preview_dark_canvas, R.color.cloud_preview_dark_ink, R.color.cloud_preview_dark_accent)
    ),
    ASH(
        prefValue = "ash",
        overlayRes = R.style.ThemeOverlay_LiftPath_Ash,
        labelRes = R.string.theme_ash_label,
        subtitleRes = R.string.theme_ash_subtitle,
        previewLight = SwatchPreview(R.color.ash_preview_light_canvas, R.color.ash_preview_light_ink, R.color.ash_preview_light_accent),
        previewDark = SwatchPreview(R.color.ash_preview_dark_canvas, R.color.ash_preview_dark_ink, R.color.ash_preview_dark_accent)
    ),
    SAND(
        prefValue = "sand",
        overlayRes = R.style.ThemeOverlay_LiftPath_Sand,
        labelRes = R.string.theme_sand_label,
        subtitleRes = R.string.theme_sand_subtitle,
        previewLight = SwatchPreview(R.color.sand_preview_light_canvas, R.color.sand_preview_light_ink, R.color.sand_preview_light_accent),
        previewDark = SwatchPreview(R.color.sand_preview_dark_canvas, R.color.sand_preview_dark_ink, R.color.sand_preview_dark_accent)
    ),
    FOG(
        prefValue = "fog",
        overlayRes = R.style.ThemeOverlay_LiftPath_Fog,
        labelRes = R.string.theme_fog_label,
        subtitleRes = R.string.theme_fog_subtitle,
        previewLight = SwatchPreview(R.color.fog_preview_light_canvas, R.color.fog_preview_light_ink, R.color.fog_preview_light_accent),
        previewDark = SwatchPreview(R.color.fog_preview_dark_canvas, R.color.fog_preview_dark_ink, R.color.fog_preview_dark_accent)
    );

    companion object {
        val DEFAULT = PAPER

        /** Unknown or absent values fall back to the default rather than throwing, so a
         *  hand-edited or restored-from-older-build pref can never brick app start. */
        fun fromPrefValue(value: String?): LiftPathTheme =
            values().firstOrNull { it.prefValue == value } ?: DEFAULT
    }
}

/** Whether the app follows the system's light/dark setting or forces one. Applied via
 *  `AppCompatDelegate.setDefaultNightMode`, which is what makes `values-night/` resources
 *  (including every palette's dark colours) resolve consistently app-wide. */
enum class AppearanceMode(val prefValue: String, @StringRes val labelRes: Int) {
    SYSTEM("system", R.string.appearance_mode_system),
    LIGHT("light", R.string.appearance_mode_light),
    DARK("dark", R.string.appearance_mode_dark);

    fun toNightMode(): Int = when (this) {
        SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }

    companion object {
        val DEFAULT = SYSTEM

        fun fromPrefValue(value: String?): AppearanceMode =
            values().firstOrNull { it.prefValue == value } ?: DEFAULT
    }
}

/**
 * Reads and writes the selected appearance: the System/Light/Dark mode, and one palette
 * each for light mode and dark mode (independent — they need not match).
 *
 * Applied by [com.liftpath.LiftPathApplication], which themes every activity from a
 * single lifecycle hook rather than requiring `setTheme` in 26 `onCreate` methods.
 *
 * NOTE for the backup contract (see CLAUDE.md): [PREFS_NAME] is registered in
 * `BackupManager.BACKED_UP_PREFS`. If that entry is removed, the user's appearance
 * choices are silently lost on a phone swap.
 */
object AppearanceManager {

    /** Must stay in sync with `BackupManager.BACKED_UP_PREFS`. */
    const val PREFS_NAME = "appearance_settings"

    /** Pre-8.0 single-palette key. No longer written, but still read as a fallback so
     *  users upgrading keep their existing choice in both slots instead of resetting to
     *  Paper. */
    private const val KEY_THEME_LEGACY = "theme"

    private const val KEY_LIGHT_THEME = "light_theme"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_MODE = "night_mode"

    fun lightTheme(context: Context): LiftPathTheme =
        LiftPathTheme.fromPrefValue(prefs(context).getString(KEY_LIGHT_THEME, null) ?: legacyThemeValue(context))

    fun darkTheme(context: Context): LiftPathTheme =
        LiftPathTheme.fromPrefValue(prefs(context).getString(KEY_DARK_THEME, null) ?: legacyThemeValue(context))

    fun setLightTheme(context: Context, theme: LiftPathTheme) {
        prefs(context).edit().putString(KEY_LIGHT_THEME, theme.prefValue).apply()
    }

    fun setDarkTheme(context: Context, theme: LiftPathTheme) {
        prefs(context).edit().putString(KEY_DARK_THEME, theme.prefValue).apply()
    }

    fun mode(context: Context): AppearanceMode =
        AppearanceMode.fromPrefValue(prefs(context).getString(KEY_MODE, null))

    fun setMode(context: Context, mode: AppearanceMode) {
        prefs(context).edit().putString(KEY_MODE, mode.prefValue).apply()
    }

    /** The palette actually in effect right now: [darkTheme] if night is currently active
     *  (system or forced), [lightTheme] otherwise. */
    fun resolvedTheme(context: Context): LiftPathTheme =
        if (isNightActive(context)) darkTheme(context) else lightTheme(context)

    /** The overlay style id for the resolved choice, applied in onActivityPreCreated. */
    @StyleRes
    fun overlayRes(context: Context): Int = resolvedTheme(context).overlayRes

    /** Reflects the current *resolved* night state — system, or forced by [AppearanceMode]
     *  via `setDefaultNightMode`, whichever was applied last. */
    fun isNightActive(context: Context): Boolean {
        val nightFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightFlags == Configuration.UI_MODE_NIGHT_YES
    }

    private fun legacyThemeValue(context: Context): String? =
        prefs(context).getString(KEY_THEME_LEGACY, null)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
