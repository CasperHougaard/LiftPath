package com.liftpath.helpers

import android.database.Cursor

/**
 * Column readers that tolerate contract drift.
 *
 * LiftPath and TriPath ship independently, so a column named here may be missing from an older
 * TriPath build. Reading by name and returning null for both "absent column" and "NULL value"
 * means a version skew degrades the integration rather than crashing it — the caller already has
 * to handle a null TSS or a night with no sleep score.
 */

internal fun Cursor.optString(name: String): String? {
    val index = getColumnIndex(name)
    return if (index < 0 || isNull(index)) null else getString(index)
}

internal fun Cursor.optInt(name: String): Int? {
    val index = getColumnIndex(name)
    return if (index < 0 || isNull(index)) null else getInt(index)
}

internal fun Cursor.optLong(name: String): Long? {
    val index = getColumnIndex(name)
    return if (index < 0 || isNull(index)) null else getLong(index)
}

internal fun Cursor.optFloat(name: String): Float? {
    val index = getColumnIndex(name)
    return if (index < 0 || isNull(index)) null else getFloat(index)
}
