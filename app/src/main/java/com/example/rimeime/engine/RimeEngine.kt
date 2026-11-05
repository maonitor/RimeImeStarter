package com.example.rimeime.engine

import android.os.Handler
import android.os.Looper
import com.example.rimeime.EngineOutput
import com.example.rimeime.KeyAction
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * 真实 librime 引擎。
 *
 * 线程模型：所有 native 调用串行跑在唯一的 "rime-engine" 线程（librime 不是线程安全的），
 * 结果通过主线程 Handler 回切后交给 Service 写 InputConnection。
 * generation 用于在切换输入框 / reset 之后作废旧结果，避免 composing 与候选错乱。
 */
class RimeEngine : InputEngine {

    private val session = RimeBridge.nativeCreateSession()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rime-engine").apply { priority = Thread.NORM_PRIORITY }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0)

    /** session 创建失败时不能切换过去，否则用户会打不出字。 */
    val isReady: Boolean get() = session != 0L

    override fun process(action: KeyAction, onResult: (EngineOutput) -> Unit) {
        if (session == 0L) {
            onResult(EngineOutput())
            return
        }
        val expected = generation.get()
        executor.execute {
            val output = try {
                processSync(action)
            } catch (t: Throwable) {
                EngineOutput()
            }
            mainHandler.post {
                if (generation.get() == expected) onResult(output)
            }
        }
    }

    override fun reset(onDone: (() -> Unit)?) {
        generation.incrementAndGet()
        if (session == 0L) {
            onDone?.invoke()
            return
        }
        executor.execute {
            RimeBridge.nativeClearComposition(session)
            mainHandler.post { onDone?.invoke() }
        }
    }

    override fun close() {
        if (session == 0L) {
            executor.shutdown()
            return
        }
        executor.execute { RimeBridge.nativeDestroySession(session) }
        executor.shutdown()
    }

    private fun processSync(action: KeyAction): EngineOutput {
        when (action) {
            // Rime 用 X11 keysym：可打印字符就是 ASCII 码。
            is KeyAction.Letter -> RimeBridge.nativeProcessKey(
                session, action.char.lowercaseChar().code, 0,
            )
            KeyAction.Backspace -> RimeBridge.nativeProcessKey(session, XK_BACK_SPACE, 0)
            KeyAction.Space -> RimeBridge.nativeProcessKey(session, 0x20, 0)
            KeyAction.Enter -> RimeBridge.nativeCommitComposition(session)
            is KeyAction.SelectCandidate -> RimeBridge.nativeSelectCandidate(session, action.index)
            else -> Unit
        }

        val commit = RimeBridge.nativeTakeCommit(session)?.takeIf { it.isNotEmpty() }
        if (commit != null) return EngineOutput(commit = commit)

        return EngineOutput(
            composing = RimeBridge.nativeGetComposing(session),
            candidates = RimeBridge.nativeGetCandidates(session).toList(),
        )
    }

    private companion object {
        const val XK_BACK_SPACE = 0xff08
    }
}
