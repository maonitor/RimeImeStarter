package com.example.rimeime.engine

import com.example.rimeime.EngineOutput
import com.example.rimeime.KeyAction

/**
 * 引擎的唯一抽象。MockPinyinEngine 与 RimeEngine 实现同一个接口，
 * 因此换引擎时 Service / Compose 层零改动。
 *
 * 刻意设计成回调式：真实 librime 的 process_key / deploy 是 C++ 调用，
 * 必须放在单一后台线程，结果再回主线程更新 Compose 状态，否则会掉帧甚至 ANR。
 * MockPinyinEngine 同步回调即可，不引入线程开销。
 */
interface InputEngine {
    fun process(action: KeyAction, onResult: (EngineOutput) -> Unit)
    fun reset(onDone: (() -> Unit)? = null)
    fun close() = Unit
}
