package com.example.rimeime

import android.view.inputmethod.InputConnection

/**
 * 把 InputConnection 的写入抽象出来。
 *
 * 目的：输入法最脆的部分是 composing / commit 的调用顺序，而真实
 * InputConnection 只能在真机上拿到。抽象之后，这套顺序可以用 JVM 单元测试
 * 覆盖（见 app/src/test），真机只负责验证各 App 的实现差异（见 compatibility/）。
 */
interface EditorSink {
    fun beginBatch()
    fun endBatch()
    fun finishComposing()
    fun commit(text: String)
    fun setComposing(text: String)
    fun deleteBefore(count: Int)
    fun performAction(action: Int): Boolean
}

class AndroidEditorSink(private val connection: InputConnection) : EditorSink {
    override fun beginBatch() { connection.beginBatchEdit() }
    override fun endBatch() { connection.endBatchEdit() }
    override fun finishComposing() { connection.finishComposingText() }
    override fun commit(text: String) { connection.commitText(text, 1) }
    override fun setComposing(text: String) { connection.setComposingText(text, 1) }
    override fun deleteBefore(count: Int) { connection.deleteSurroundingText(count, 0) }
    override fun performAction(action: Int): Boolean = connection.performEditorAction(action)
}

/** 引擎输出 → InputConnection 调用序列。所有写入都包在 batch edit 里。 */
object EditorOutputApplier {
    fun apply(sink: EditorSink, output: EngineOutput) {
        sink.beginBatch()
        try {
            output.commit?.let { text ->
                // 先结束 composing，再 commit，避免部分 App 把两者叠加成重复文本。
                sink.finishComposing()
                sink.commit(text)
            }
            if (output.composing.isNotEmpty()) {
                sink.setComposing(output.composing)
            } else {
                sink.finishComposing()
            }
        } finally {
            sink.endBatch()
        }
    }
}
