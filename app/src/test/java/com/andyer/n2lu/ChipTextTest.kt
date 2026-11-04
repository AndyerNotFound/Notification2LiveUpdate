package com.andyer.n2lu

import com.andyer.n2lu.data.ChipStyle
import com.andyer.n2lu.service.ChipText
import com.andyer.n2lu.service.SourceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChipTextTest {

    @Test
    fun percentUsesProgressAndMax() {
        val info = source(progress = 42, progressMax = 100)
        assertEquals("42%", ChipText.percent(info))
        assertEquals("42%", ChipText.build(info, ChipStyle.AUTO))
    }

    @Test
    fun percentIsNullWithoutProgress() {
        val info = source(progress = null, progressMax = 0)
        assertNull(ChipText.percent(info))
        assertEquals("外卖", ChipText.build(info, ChipStyle.APP_NAME))
    }

    @Test
    fun clampKeepsChipShort() {
        assertEquals("1234567", ChipText.clamp("1234567"))
        assertEquals("123456…", ChipText.clamp("123456789"))
        assertEquals("Live", ChipText.clamp("   "))
    }

    @Test
    fun titleStyleUsesTitle() {
        val info = source(progress = null, progressMax = 0, title = "骑手正在路上")
        assertEquals("骑手正在路上", ChipText.build(info, ChipStyle.TITLE))
    }

    private fun source(
        progress: Int?,
        progressMax: Int,
        title: String = "外卖还有 3 分钟",
    ): SourceInfo = SourceInfo(
        key = "0|com.example|1|null|1000",
        packageName = "com.example",
        id = 1,
        tag = null,
        title = title,
        text = "骑手正在路上",
        appLabel = "外卖",
        smallIcon = null,
        progress = progress,
        progressMax = progressMax,
        indeterminate = false,
        chronometer = false,
        chronometerCountDown = false,
        whenMillis = 0L,
        showWhen = false,
        category = null,
        isOngoing = true,
        isPromoted = false,
        isGroupSummary = false,
        hasMediaSession = false,
        channelImportance = 4,
        channelId = null,
        postTime = 0L,
    )
}
