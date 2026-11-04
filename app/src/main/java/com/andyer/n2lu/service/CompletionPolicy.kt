package com.andyer.n2lu.service

object CompletionPolicy {

    private val englishDone = Regex("\\b(completed|complete|finished|done|delivered)\\b")

    private val chineseDone = listOf("完成", "已结束", "已送达", "已到达")

    private val negations = listOf("未完成", "未结束", "尚未", "还没", "not complete", "incomplete", "unfinished")

    fun isComplete(
        progress: Int?,
        progressMax: Int,
        indeterminate: Boolean,
        title: String,
        text: String,
    ): Boolean {
        val content = (title + " " + text).lowercase()
        if (negations.any { content.contains(it) }) return false
        if (!indeterminate && progress != null && progressMax > 0 && progress >= progressMax) return true
        if (content.isBlank()) return false
        if (chineseDone.any { content.contains(it) }) return true
        return englishDone.containsMatchIn(content)
    }
}
