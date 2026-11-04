package com.andyer.n2lu.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionPolicyTest {

    @Test
    fun progressAtMaxIsComplete() {
        assertTrue(CompletionPolicy.isComplete(100, 100, false, "下载中", "文件.zip"))
    }

    @Test
    fun progressAboveMaxIsComplete() {
        assertTrue(CompletionPolicy.isComplete(120, 100, false, "下载", ""))
    }

    @Test
    fun runningProgressIsNotComplete() {
        assertFalse(CompletionPolicy.isComplete(30, 100, false, "下载中", "1.2 MB/s"))
    }

    @Test
    fun indeterminateProgressIsNotComplete() {
        assertFalse(CompletionPolicy.isComplete(0, 0, true, "正在处理", "请稍候"))
    }

    @Test
    fun chineseDoneKeywordIsComplete() {
        assertTrue(CompletionPolicy.isComplete(null, 0, false, "外卖", "订单已完成"))
        assertTrue(CompletionPolicy.isComplete(null, 0, false, "下载完成", "report.pdf"))
    }

    @Test
    fun englishDoneKeywordIsComplete() {
        assertTrue(CompletionPolicy.isComplete(null, 0, false, "Download", "Installation complete"))
        assertTrue(CompletionPolicy.isComplete(null, 0, false, "Order delivered", ""))
    }

    @Test
    fun negatedDoneKeywordIsNotComplete() {
        assertFalse(CompletionPolicy.isComplete(40, 100, false, "下载未完成", "剩余 3 分钟"))
        assertFalse(CompletionPolicy.isComplete(null, 0, false, "Install", "not complete yet"))
    }

    @Test
    fun unrelatedTextIsNotComplete() {
        assertFalse(CompletionPolicy.isComplete(null, 0, false, "骑手正在路上", "还有 3 分钟"))
        assertFalse(CompletionPolicy.isComplete(null, 0, false, "", ""))
    }
}
