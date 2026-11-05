package com.example.rimeime.engine

import com.example.rimeime.EngineOutput
import com.example.rimeime.KeyAction

/**
 * 引擎的唯一抽象。MockPinyinEngine 与未来的 RimeEngine 实现同一个接口，
 * 因此换引擎时 Service / Compose 层零改动。
 *
 * 注意：真实 librime 查询是 C++ 调用，必须放到后台线程，
 * 结果再回主线程更新 Compose 状态，否则会掉帧甚至 ANR。
 */
interface InputEngine {
    fun process(action: KeyAction): EngineOutput
    fun reset()
}
