package com.atharok.screentime.common.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.atharok.screentime.common.extensions.getApplicationIconSafety

/**
 * A single process-wide icon cache.
 *
 * Before this existed every grid cell called PackageManager.getApplicationIcon() (a binder call
 * returning a full-size Drawable) and then toBitmap() on every recomposition. With ~800 ranked
 * apps kept in the month/long-range entities that produced hundreds of full-resolution bitmaps,
 * which is what pushed PSS past 1 GB and got the process killed by LMK on ColorOS.
 *
 * Now the entity only keeps the package name, and icons are drawn once into a small bitmap
 * sized to the actual on-screen dimension, then reused from an LRU capped at a few MB.
 */
object AppIconCache {

    /** 8 MB of small bitmaps is plenty for several hundred visible icons. */
    private const val MAX_CACHE_KB = 8 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    /** Release transient icon bitmaps when Android reports that the UI is hidden or memory is tight. */
    @Synchronized
    fun trimMemory() {
        cache.evictAll()
    }

    /** Cache-only lookup: cheap and synchronous, safe to call during composition. */
    @Synchronized
    fun peek(packageName: String, sizePx: Int): ImageBitmap? =
        cache.get("$packageName@$sizePx")?.takeIf { !it.isRecycled }?.asImageBitmap()

    @Synchronized
    fun get(context: Context, packageName: String, sizePx: Int): ImageBitmap? {
        val key = "$packageName@$sizePx"
        cache.get(key)?.takeIf { !it.isRecycled }?.let { return it.asImageBitmap() }
        // getApplicationIcon() is a binder call. Callers must invoke this off the main thread
        // (see ApplicationIcon(packageName)), otherwise a cold list of 40 icons stalls frames.
        val drawable: Drawable =
            context.packageManager.getApplicationIconSafety(packageName) ?: return null
        val bitmap = runCatching { render(drawable, sizePx) }.getOrNull() ?: return null
        cache.put(key, bitmap)
        return bitmap.asImageBitmap()
    }

    private fun render(drawable: Drawable, sizePx: Int): Bitmap {
        val safe = sizePx.coerceIn(1, 512)
        val bitmap = Bitmap.createBitmap(safe, safe, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, safe, safe)
        drawable.draw(canvas)
        canvas.setBitmap(null)
        return bitmap
    }
}
