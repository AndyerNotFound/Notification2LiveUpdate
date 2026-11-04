package com.andyer.n2lu.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap

object IconLoader {

    private val cache = LruCache<String, Bitmap>(48)

    fun appIcon(context: Context, packageName: String, sizePx: Int = 96): Bitmap? {
        cache.get(packageName)?.let { return it }
        val bitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
        }.getOrNull() ?: return null
        cache.put(packageName, bitmap)
        return bitmap
    }

    fun smallIcon(context: Context, source: Icon?, fallback: Int): Icon =
        source ?: Icon.createWithResource(context, fallback)
}
