package com.liftpath.helpers

import android.content.Context
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/**
 * Kotlin-side resolution of the `lp*` design-token attributes.
 *
 * The token layer is theme attributes rather than colour resources so the four palettes
 * can be swapped at runtime. That means code cannot use
 * `ContextCompat.getColor(this, R.color.lp_ink)` — there is no such resource. Use
 * `lpColor(R.attr.lpInk)` instead and the value follows whichever palette is active.
 *
 * Implemented with [Context.obtainStyledAttributes] rather than
 * `Theme.resolveAttribute`: the TypedArray path transparently handles both plain colours
 * and colour state lists, so the same call works for `lpInk` and for a CSL-backed
 * attribute if one is ever added.
 */
@ColorInt
fun Context.lpColor(@AttrRes attr: Int): Int {
    val array = obtainStyledAttributes(intArrayOf(attr))
    try {
        // A missing binding returns the fallback. Magenta is deliberate: an unbound token
        // should be impossible to miss on screen rather than quietly resolving to black.
        return array.getColor(0, UNBOUND_TOKEN_COLOR)
    } finally {
        array.recycle()
    }
}

/** Convenience for adapters and view holders, which have a view but not the activity. */
@ColorInt
fun View.lpColor(@AttrRes attr: Int): Int = context.lpColor(attr)

/**
 * Shown when an attribute has no binding in the active theme — i.e. a token was added to
 * `lp_attrs.xml` but not to all four `Theme.LiftPath.*` variants.
 */
private const val UNBOUND_TOKEN_COLOR = 0xFFFF00FF.toInt()
