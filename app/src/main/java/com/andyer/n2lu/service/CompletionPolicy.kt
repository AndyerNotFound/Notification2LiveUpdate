package com.andyer.n2lu.service

object CompletionPolicy {

    private val englishDone = Regex("\\b(completed|complete|finished|done|delivered)\\b(?!\\s*\\d)")

    private val chineseDone = Regex("(已完成|完成|已结束|已送达|已到达)(?!率|度|情况|进度|中|\\s*\\d)")

    private val negations = listOf("未完成", "未结束", "尚未", "还没", "not complete", "incomplete", "unfinished")

    fun isComplete(
        progress: Int?,
        progressMax: Int,
        indeterminate: Boolean,
        countDown: Boolean,
        title: String,
        text: String,
    ): Boolean {
        val content = (title + " " + text).lowercase()
        if (negations.any { content.contains(it) }) return false
        if (!countDown && !indeterminate && progress != null && progressMax > 0 && progress >= progressMax) {
            return true
        }
        if (content.isBlank()) return false
        if (chineseDone.containsMatchIn(content)) return true
        return englishDone.containsMatchIn(content)
    }
}
