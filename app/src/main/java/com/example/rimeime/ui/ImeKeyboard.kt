package com.example.rimeime.ui

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rimeime.ImeUiState
import com.example.rimeime.KeyAction
import com.example.rimeime.KeyboardMetrics
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val Row1 = listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p')
private val Row2 = listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l')
private val Row3 = listOf('z', 'x', 'c', 'v', 'b', 'n', 'm')

private val Row1Alt = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
private val Row2Alt = listOf('@', '#', '$', '%', '&', '*', '-', '+', '=')
private val Row3Alt = listOf('(', ')', '!', '?', '~', '\\', '/')

private val SymbolRows = listOf(
    listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0'),
    listOf('，', '。', '！', '？', '、', '：', '；', '（', '）', '@'),
    listOf('#', '/', '-', '_', '.', '+', '=', '%', '&', '*'),
)

private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 55L
private const val SWIPE_THRESHOLD_PX = 48f

@Composable
fun ImeKeyboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF185FA5),
            background = Color(0xFFF2F4F7),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF20242B),
        ),
        content = content,
    )
}

/** 按键音效 + 震动：系统音量/触觉开关会自动生效，无需自己管权限。 */
@Composable
private fun rememberKeyFeedback(): () -> Unit {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view, context) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audio?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }
}

@Composable
fun ImeKeyboard(
    state: ImeUiState,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onFeedback = rememberKeyFeedback()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            // 不用 navigationBarsPadding()：IME 窗口不是普通 Activity 窗口，
            // 部分 ROM 上报的 insets 为 0。统一用 Service 侧算好的安全区。
            .padding(
                start = state.metrics.sideInsetDp.dp,
                end = state.metrics.sideInsetDp.dp,
                bottom = (state.metrics.bottomInsetDp + 4).dp,
            ),
    ) {
        CandidateBar(state, onAction)
        if (state.symbols) {
            SymbolKeyboard(state, onAction, onFeedback)
        } else {
            QwertyKeyboard(state, onAction, onFeedback)
        }
    }
}

@Composable
private fun CandidateBar(state: ImeUiState, onAction: (KeyAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.composing.isNotEmpty()) {
            Text(
                text = state.composing,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.candidates.isEmpty()) {
                Text(
                    text = when {
                        state.languageLocked -> "密码框：不记录输入"
                        state.chinese -> "输入拼音，候选会出现在这里"
                        else -> "英文输入"
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF8A9099),
                )
            } else {
                state.candidates.forEachIndexed { index, word ->
                    Text(
                        text = word,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onAction(KeyAction.SelectCandidate(index)) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Text(
            text = state.engineLabel,
            fontSize = 10.sp,
            color = Color(0xFF8A9099),
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text = "收起",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { onAction(KeyAction.Hide) }
                .padding(start = 8.dp),
        )
    }
}

@Composable
private fun QwertyKeyboard(
    state: ImeUiState,
    onAction: (KeyAction) -> Unit,
    onFeedback: () -> Unit,
) {
    val upper = state.shifted && !state.chinese
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Row1.forEachIndexed { index, c ->
                KeyButton(
                    label = if (upper) c.uppercaseChar().toString() else c.toString(),
                    action = KeyAction.Letter(c),
                    altLabel = Row1Alt[index].toString(),
                    altAction = KeyAction.Symbol(Row1Alt[index]),
                    metrics = state.metrics,
                    onAction = onAction,
                    onFeedback = onFeedback,
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(0.5f))
            Row2.forEachIndexed { index, c ->
                KeyButton(
                    label = if (upper) c.uppercaseChar().toString() else c.toString(),
                    action = KeyAction.Letter(c),
                    altLabel = Row2Alt[index].toString(),
                    altAction = KeyAction.Symbol(Row2Alt[index]),
                    metrics = state.metrics,
                    onAction = onAction,
                    onFeedback = onFeedback,
                )
            }
            Spacer(Modifier.weight(0.5f))
        }
        Row(Modifier.fillMaxWidth()) {
            KeyButton(
                label = "⇧",
                action = KeyAction.Shift,
                weight = 1.5f,
                metrics = state.metrics,
                onAction = onAction,
                onFeedback = onFeedback,
            )
            Row3.forEachIndexed { index, c ->
                KeyButton(
                    label = if (upper) c.uppercaseChar().toString() else c.toString(),
                    action = KeyAction.Letter(c),
                    altLabel = Row3Alt[index].toString(),
                    altAction = KeyAction.Symbol(Row3Alt[index]),
                    metrics = state.metrics,
                    onAction = onAction,
                    onFeedback = onFeedback,
                )
            }
            KeyButton(
                label = "⌫",
                action = KeyAction.Backspace,
                weight = 1.5f,
                repeatable = true,
                metrics = state.metrics,
                onAction = onAction,
                onFeedback = onFeedback,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            KeyButton(
                label = "?123",
                action = KeyAction.ToggleSymbols,
                weight = 1.5f,
                metrics = state.metrics,
                onAction = onAction,
                onFeedback = onFeedback,
            )
            KeyButton(
                label = if (state.chinese) "中" else "En",
                action = KeyAction.ToggleLanguage,
                metrics = state.metrics,
                onAction = onAction,
                onFeedback = onFeedback,
            )
            KeyButton(
                label = "空格",
                action = KeyAction.Space,
                weight = 4f,
                metrics = state.metrics,
                onAction = onAction,
                onFeedback = onFeedback,
            )
            KeyButton(
                label = "。",
                action = KeyAction.Symbol('。'),
                metrics = state.metrics,
                onAction = onAction,
                onFeedback = onFeedback,
            )
            KeyButton(
                label = state.enterLabel,
                action = KeyAction.Enter,
                weight = 1.5f,
                accent = true,
                metrics = state.metrics,
                onAction = onAction,
                onFeedback = onFeedback,
            )
        }
    }
}

@Composable
private fun SymbolKeyboard(
    state: ImeUiState,
    onAction: (KeyAction) -> Unit,
    onFeedback: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        SymbolRows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { c ->
                    KeyButton(
                        label = c.toString(),
                        action = KeyAction.Symbol(c),
                        metrics = state.metrics,
                        onAction = onAction,
                        onFeedback = onFeedback,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            KeyButton(
                label = "abc",
                action = KeyAction.ToggleSymbols,
                weight = 1.5f,
                metrics = state.metrics,
                onAction = onAction,
                onFeedback = onFeedback,
            )
            KeyButton(
                label = "空格",
                action = KeyAction.Space,
                weight = 4f,
                metrics = state.metrics,
                onAction = onAction,
                onFeedback = onFeedback,
            )
            KeyButton(
                label = "⌫",
                action = KeyAction.Backspace,
                weight = 1.5f,
                repeatable = true,
                metrics = state.metrics,
                onAction = onAction,
                onFeedback = onFeedback,
            )
            KeyButton(
                label = state.enterLabel,
                action = KeyAction.Enter,
                weight = 2f,
                accent = true,
                metrics = state.metrics,
                onAction = onAction,
                onFeedback = onFeedback,
            )
        }
    }
}

/**
 * 按键手感的关键：动作在 ACTION_DOWN 就触发（不是等抬手），按下立刻变色+震动+音效，
 * 退格键长按连删，字母键上滑输入右上角的小符号。
 */
@Composable
private fun RowScope.KeyButton(
    label: String,
    action: KeyAction,
    metrics: KeyboardMetrics,
    onAction: (KeyAction) -> Unit,
    onFeedback: () -> Unit,
    weight: Float = 1f,
    altLabel: String = "",
    altAction: KeyAction? = null,
    repeatable: Boolean = false,
    accent: Boolean = false,
) {
    var pressed by remember { mutableStateOf(false) }
    val background = when {
        accent && pressed -> Color(0xFF0C447C)
        accent -> MaterialTheme.colorScheme.primary
        pressed -> Color(0xFFD6E7F7)
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .weight(weight)
            .height(metrics.keyHeightDp.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .pointerInput(action, altAction, repeatable, onAction, onFeedback) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        onFeedback()
                        onAction(action)

                        var repeatJob: Job? = null
                        if (repeatable) {
                            repeatJob = launch {
                                delay(REPEAT_DELAY_MS)
                                while (isActive) {
                                    onAction(action)
                                    delay(REPEAT_INTERVAL_MS)
                                }
                            }
                        }

                        val up = waitForUpOrCancellation()
                        repeatJob?.cancel()
                        if (up != null && altAction != null &&
                            down.position.y - up.position.y > SWIPE_THRESHOLD_PX
                        ) {
                            onAction(altAction)
                        }
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = if (label.length > 2) 12.sp else 17.sp,
            color = if (accent) Color.White else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (altLabel.isNotEmpty()) {
            Text(
                text = altLabel,
                fontSize = 10.sp,
                color = Color(0xFF8A9099),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp),
            )
        }
    }
}
