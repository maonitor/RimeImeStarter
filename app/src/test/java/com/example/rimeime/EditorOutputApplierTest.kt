package com.example.rimeime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 记录所有写入调用，用来验证 composing / commit 的顺序契约。 */
private class RecordingSink(
    private val throwOnCommit: Boolean = false,
) : EditorSink {
    val calls = mutableListOf<String>()

    override fun beginBatch() { calls += "begin" }
    override fun endBatch() { calls += "end" }
    override fun finishComposing() { calls += "finish" }
    override fun commit(text: String) {
        calls += "commit:$text"
        if (throwOnCommit) throw IllegalStateException("target app rejected commit")
    }
    override fun setComposing(text: String) { calls += "composing:$text" }
    override fun deleteBefore(count: Int) { calls += "delete:$count" }
    override fun performAction(action: Int): Boolean {
        calls += "action:$action"
        return true
    }
}

class EditorOutputApplierTest {

    @Test
    fun composingOnly_writesComposingRegionWithoutCommit() {
        val sink = RecordingSink()
        EditorOutputApplier.apply(sink, EngineOutput(composing = "nihao", candidates = listOf("你好")))
        assertEquals(listOf("begin", "composing:nihao", "end"), sink.calls)
    }

    /** 回归 C02：真机上曾出现 "nihao你好"，因为 commit 前先 finish 了 composing。 */
    @Test
    fun commit_replacesComposingWithoutFinishingFirst() {
        val sink = RecordingSink()
        EditorOutputApplier.apply(sink, EngineOutput(commit = "你好"))
        assertEquals(
            listOf("begin", "commit:你好", "finish", "end"),
            sink.calls,
        )
    }

    @Test
    fun emptyOutput_onlyFinishesComposing() {
        val sink = RecordingSink()
        EditorOutputApplier.apply(sink, EngineOutput())
        assertEquals(listOf("begin", "finish", "end"), sink.calls)
    }

    /** 目标 App 抛异常时也必须关闭 batch，否则后续输入全部丢失。 */
    @Test
    fun batchIsClosedEvenWhenTargetAppThrows() {
        val sink = RecordingSink(throwOnCommit = true)
        try {
            EditorOutputApplier.apply(sink, EngineOutput(commit = "你好"))
        } catch (ignored: IllegalStateException) {
            // 预期：模拟 WebView 拒绝 commit
        }
        assertEquals("begin", sink.calls.first())
        assertEquals("end", sink.calls.last())
        assertTrue(sink.calls.count { it == "end" } == 1)
    }

    @Test
    fun deleteAndAction_useUnifiedSink() {
        val sink = RecordingSink()
        sink.deleteBefore(1)
        sink.performAction(6)
        assertEquals(listOf("delete:1", "action:6"), sink.calls)
    }
}
