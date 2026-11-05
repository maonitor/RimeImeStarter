package com.example.rimeime

/** UI → Service 的按键意图，UI 层不直接接触 InputConnection。 */
sealed interface KeyAction {
    data class Letter(val char: Char) : KeyAction
    data class Symbol(val char: Char) : KeyAction
    data class SelectCandidate(val index: Int) : KeyAction
    data object Backspace : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    data object Shift : KeyAction
    data object ToggleLanguage : KeyAction
    data object ToggleSymbols : KeyAction
    data object Hide : KeyAction
}

/** Service 持有的渲染状态。Compose 只消费它，不反向修改。 */
data class ImeUiState(
    val composing: String = "",
    val candidates: List<String> = emptyList(),
    val chinese: Boolean = true,
    val shifted: Boolean = false,
    val symbols: Boolean = false,
    val enterLabel: String = "回车",
    /** 密码框：强制英文并锁住中英切换，避免用户输入进入拼音引擎和用户词库。 */
    val languageLocked: Boolean = false,
    /** 当前实际使用的引擎，显示在候选栏，便于验证 librime 是否接上。 */
    val engineLabel: String = "mock",
)

/**
 * 引擎 → Service 的输出。
 * composing 会写进输入框的「待确认区域」，commit 一旦非空就上屏并清空待确认区。
 * 换 librime 时只要也产出这个结构，UI 和 Service 都不用改。
 */
data class EngineOutput(
    val composing: String = "",
    val candidates: List<String> = emptyList(),
    val commit: String? = null,
)
