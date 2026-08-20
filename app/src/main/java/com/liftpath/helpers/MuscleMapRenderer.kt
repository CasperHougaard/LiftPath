package com.liftpath.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.LruCache
import androidx.annotation.DrawableRes
import com.liftpath.R

/**
 * Composites the illustrated muscle map (front+back body, drawable-nodpi/muscle_base +
 * muscle_mask_* overlays sourced from github.com/MertenD/musclegroup-image-generator,
 * personal/non-commercial use) into a single tinted Bitmap.
 *
 * Each mask is a transparent PNG whose non-transparent pixels get painted with a
 * PorterDuffColorFilter(color, SRC_IN), the standard Android silhouette-tinting technique.
 */
object MuscleMapRenderer {

    private const val MASK_CACHE_MAX_ENTRIES = 16
    private const val COMPOSITE_CACHE_MAX_BYTES = 6 * 1024 * 1024

    private var baseBitmap: Bitmap? = null

    private val maskBitmapCache = LruCache<Int, Bitmap>(MASK_CACHE_MAX_ENTRIES)

    private val compositeBitmapCache = object : LruCache<String, Bitmap>(COMPOSITE_CACHE_MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun getBaseBitmap(context: Context): Bitmap {
        return baseBitmap ?: BitmapFactory.decodeResource(context.resources, R.drawable.muscle_base)
            .also { baseBitmap = it }
    }

    private fun getMaskBitmap(context: Context, @DrawableRes maskResId: Int): Bitmap {
        maskBitmapCache.get(maskResId)?.let { return it }
        val bitmap = BitmapFactory.decodeResource(context.resources, maskResId)
        maskBitmapCache.put(maskResId, bitmap)
        return bitmap
    }

    /**
     * Draws [maskColors] (drawable res id to ARGB color) over the base body image, in order
     * (later entries paint over earlier ones on overlap), and returns the composited result.
     * Identical calls (same masks/colors, any order) are served from cache.
     */
    fun render(context: Context, maskColors: List<Pair<Int, Int>>): Bitmap {
        val cacheKey = maskColors
            .sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString(separator = "|") { (maskResId, color) -> "$maskResId:$color" }
        compositeBitmapCache.get(cacheKey)?.let { return it }

        val base = getBaseBitmap(context)
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        maskColors.forEach { (maskResId, color) ->
            val mask = getMaskBitmap(context, maskResId)
            paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(mask, 0f, 0f, paint)
        }

        compositeBitmapCache.put(cacheKey, result)
        return result
    }

    /** Releases all cached bitmaps, e.g. on a low-memory callback. */
    fun clearCaches() {
        baseBitmap = null
        maskBitmapCache.evictAll()
        compositeBitmapCache.evictAll()
    }
}
