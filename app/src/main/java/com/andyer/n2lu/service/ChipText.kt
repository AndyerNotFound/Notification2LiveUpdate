package com.andyer.n2lu.service

import com.andyer.n2lu.data.ChipStyle
import kotlin.math.roundToInt

object ChipText {

    private const val MAX_LENGTH = 7

    fun build(info: SourceInfo, style: ChipStyle): String {
        val percent = percent(info)
        val app = clamp(info.appLabel.ifBlank { info.packageName })
        val title = clamp(info.title.ifBlank { info.appLabel })
        return when (style) {
            ChipStyle.PERCENT -> percent ?: app
            ChipStyle.APP_NAME -> app
            ChipStyle.TITLE -> title
            ChipStyle.AUTO -> percent ?: app
        }
    }

    fun percent(info: SourceInfo): String? {
        val progress = info.progress ?: return null
        val max = info.progressMax
        if (max <= 0) return null
        val pct = (progress.toFloat() / max * 100f).roundToInt().coerceIn(0, 100)
        return "$pct%"
    }

    fun clamp(text: String): String {
        val clean = text.trim().replace('\n', ' ')
        if (clean.isEmpty()) return "Live"
        return if (clean.length <= MAX_LENGTH) clean else clean.take(MAX_LENGTH - 1) + "…"
    }
}
