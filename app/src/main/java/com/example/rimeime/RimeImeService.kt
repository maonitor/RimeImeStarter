package com.example.rimeime

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.example.rimeime.engine.InputEngine
import com.example.rimeime.engine.MockPinyinEngine
import com.example.rimeime.engine.RimeBridge
import com.example.rimeime.engine.RimeDeployer
import com.example.rimeime.engine.RimeEngine
import com.example.rimeime.ui.ImeKeyboard
import com.example.rimeime.ui.ImeKeyboardTheme

/**
 * 输入法 Service。
 *
 * 架构约定：状态与 InputConnection 都留在 Service 侧，Compose 只负责渲染和派发意图。
 * 引擎通过 InputEngine 接口隔离，且是回调式（librime 的 native 调用在后台线程）。
 *
 * 启动策略：先用 MockPinyinEngine 保证立刻能打字，同时在后台部署 librime；
 * 部署成功后热切换到 RimeEngine，失败则一直用 mock（无 librime.so 时必然如此）。
 */
class RimeImeService : InputMethodService() {

    private var engine: InputEngine = MockPinyinEngine()
    private var uiState by mutableStateOf(ImeUiState())
    private var imeOwner: ImeLifecycleOwner? = null
    private var editorAction = EditorInfo.IME_ACTION_UNSPECIFIED
    private var passwordField = false
    private var destroyed = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        imeOwner = ImeLifecycleOwner().apply {
            onCreate()
            onResume()
        }
        prepareRimeEngine()
    }

    override fun onDestroy() {
        destroyed = true
        engine.close()
        imeOwner?.onDestroy()
        imeOwner = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val owner = checkNotNull(imeOwner)
        return ComposeView(this).apply {
            // InputMethodService 不是 Activity，三个 owner 必须手动挂上，缺一即崩。
            // 交给 Java 辅助类：新版 lifecycle 把这些 ViewTree*Owner 对 Kotlin 隐藏了。
            ImeViewTreeOwners.attach(this, owner)
            setContent {
                ImeKeyboardTheme {
                    ImeKeyboard(state = uiState, onAction = ::handleAction)
                }
            }
        }
    }

    /**
     * 首次 deploy 会编译词典，可能耗时数秒。放在后台线程，
     * 成功后再切主线程换引擎，避免阻塞键盘冷启动。
     */
    private fun prepareRimeEngine() {
        Thread({
            val ready = try {
                RimeDeployer(this@RimeImeService).prepare()
            } catch (t: Throwable) {
                Log.w(TAG, "librime 部署失败，继续使用 mock 引擎", t)
                false
            }
            if (!ready || destroyed) return@Thread
            mainHandler.post {
                if (destroyed) return@post
                val rime = try {
                    RimeEngine()
                } catch (t: Throwable) {
                    Log.w(TAG, "RimeEngine 创建失败", t)
                    null
                }
                if (rime == null || !rime.isReady) {
                    rime?.close()
                    return@post
                }
                engine.close()
                engine = rime
                currentInputConnection?.finishComposingText()
                uiState = uiState.copy(
                    composing = "",
                    candidates = emptyList(),
                    engineLabel = "rime ${RimeBridge.version}",
                )
            }
        }, "rime-prepare").start()
    }

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        editorAction = info.imeOptions and EditorInfo.IME_MASK_ACTION
        passwordField = isPasswordEditor(info)
        uiState = uiState.copy(
            // 密码框强制英文：既不把用户输入送进拼音引擎，也不写入用户词库。
            chinese = if (passwordField) false else uiState.chinese,
            languageLocked = passwordField,
            enterLabel = enterLabel(editorAction),
        )
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        engine.reset()
        uiState = uiState.copy(composing = "", candidates = emptyList())
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        engine.reset()
        uiState = uiState.copy(composing = "", candidates = emptyList())
        super.onFinishInputView(finishingInput)
    }

    /**
     * 用户点了输入框别处、或目标 App 自己改写了文本，composing span 会消失。
     * 若不清空内部 buffer，下一次按键会接在旧拼音串后面，出现「删不掉的拼音」。
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd,
        )
        if (uiState.composing.isNotEmpty() && (candidatesStart < 0 || candidatesEnd < 0)) {
            engine.reset()
            uiState = uiState.copy(composing = "", candidates = emptyList())
        }
    }

    /** 横屏全屏模式会盖住输入框并让候选栏失效。 */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        outInsets.contentTopInsets = outInsets.visibleTopInsets
    }

    private fun handleAction(action: KeyAction) {
        val chineseWithBuffer = uiState.chinese && uiState.composing.isNotEmpty()
        when (action) {
            is KeyAction.Letter -> {
                if (uiState.chinese) {
                    engine.process(KeyAction.Letter(action.char.lowercaseChar())) { applyOutput(it) }
                } else {
                    val c = if (uiState.shifted) {
                        action.char.uppercaseChar()
                    } else {
                        action.char
                    }
                    applyOutput(EngineOutput(commit = c.toString()))
                    if (uiState.shifted) uiState = uiState.copy(shifted = false)
                }
            }

            is KeyAction.Symbol -> applyOutput(EngineOutput(commit = action.char.toString()))

            is KeyAction.SelectCandidate -> engine.process(action) { applyOutput(it) }

            KeyAction.Backspace -> {
                if (chineseWithBuffer) {
                    engine.process(action) { applyOutput(it) }
                } else {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
            }

            KeyAction.Space -> {
                if (chineseWithBuffer) {
                    engine.process(action) { applyOutput(it) }
                } else {
                    applyOutput(EngineOutput(commit = " "))
                }
            }

            KeyAction.Enter -> {
                if (chineseWithBuffer) {
                    engine.process(action) { applyOutput(it) }
                } else if (
                    editorAction != EditorInfo.IME_ACTION_UNSPECIFIED &&
                    editorAction != EditorInfo.IME_ACTION_NONE
                ) {
                    currentInputConnection?.performEditorAction(editorAction)
                } else {
                    applyOutput(EngineOutput(commit = "\n"))
                }
            }

            KeyAction.ToggleLanguage -> {
                if (!uiState.languageLocked) {
                    engine.reset()
                    currentInputConnection?.finishComposingText()
                    uiState = uiState.copy(
                        chinese = !uiState.chinese,
                        composing = "",
                        candidates = emptyList(),
                    )
                }
            }

            KeyAction.Shift -> uiState = uiState.copy(shifted = !uiState.shifted)
            KeyAction.ToggleSymbols -> uiState = uiState.copy(symbols = !uiState.symbols)
            KeyAction.Hide -> requestHideSelf(0)
        }
    }

    private fun applyOutput(output: EngineOutput) {
        // 每次都重新取，绝不缓存：输入框切换、WebView 重载都会让旧连接失效。
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        try {
            output.commit?.let { text ->
                ic.finishComposingText()
                ic.commitText(text, 1)
            }
            if (output.composing.isNotEmpty()) {
                ic.setComposingText(output.composing, 1)
            } else {
                ic.finishComposingText()
            }
        } finally {
            ic.endBatchEdit()
        }
        if (output.commit != null) engine.reset()
        uiState = uiState.copy(
            composing = output.composing,
            candidates = output.candidates,
        )
    }

    private fun isPasswordEditor(info: EditorInfo): Boolean {
        val klass = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return klass == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
    }

    private fun enterLabel(action: Int): String = when (action) {
        EditorInfo.IME_ACTION_SEARCH -> "搜索"
        EditorInfo.IME_ACTION_SEND -> "发送"
        EditorInfo.IME_ACTION_GO -> "前往"
        EditorInfo.IME_ACTION_DONE -> "完成"
        EditorInfo.IME_ACTION_NEXT -> "下一个"
        else -> "回车"
    }

    private companion object {
        const val TAG = "RimeImeService"
    }
}
