package com.example.rimeime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rimeime.ImeUiState
import com.example.rimeime.KeyAction

private val Row1 = listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p')
private val Row2 = listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l')
private val Row3 = listOf('z', 'x', 'c', 'v', 'b', 'n', 'm')

private val SymbolRows = listOf(
    listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0'),
    listOf('，', '。', '！', '？', '、', '：', '；', '（', '）', '@'),
    listOf('#', '/', '-', '_', '.', '+', '=', '%', '&', '*'),
)

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

@Composable
fun ImeKeyboard(
    state: ImeUiState,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            // IME 窗口不是普通 Activity 窗口，navigationBarsPadding() 在部分 ROM 上
            // 取不到手势导航条高度，真实产品仍需按 WindowInsets 单独兜底。
            .navigationBarsPadding()
            .padding(bottom = 4.dp),
    ) {
        CandidateBar(state, onAction)
        if (state.symbols) SymbolKeyboard(state, onAction) else QwertyKeyboard(state, onAction)
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
private fun QwertyKeyboard(state: ImeUiState, onAction: (KeyAction) -> Unit) {
    val upper = state.shifted && !state.chinese
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Row1.forEach { c ->
                KeyButton(if (upper) c.uppercaseChar().toString() else c.toString()) {
                    onAction(KeyAction.Letter(c))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(0.5f))
            Row2.forEach { c ->
                KeyButton(if (upper) c.uppercaseChar().toString() else c.toString()) {
                    onAction(KeyAction.Letter(c))
                }
            }
            Spacer(Modifier.weight(0.5f))
        }
        Row(Modifier.fillMaxWidth()) {
            KeyButton("⇧", weight = 1.5f) { onAction(KeyAction.Shift) }
            Row3.forEach { c ->
                KeyButton(if (upper) c.uppercaseChar().toString() else c.toString()) {
                    onAction(KeyAction.Letter(c))
                }
            }
            KeyButton("⌫", weight = 1.5f) { onAction(KeyAction.Backspace) }
        }
        Row(Modifier.fillMaxWidth()) {
            KeyButton("?123", weight = 1.5f) { onAction(KeyAction.ToggleSymbols) }
            KeyButton(if (state.chinese) "中" else "En", weight = 1f) {
                onAction(KeyAction.ToggleLanguage)
            }
            KeyButton("空格", weight = 4f) { onAction(KeyAction.Space) }
            KeyButton("。", weight = 1f) { onAction(KeyAction.Symbol('。')) }
            KeyButton(state.enterLabel, weight = 1.5f, accent = true) {
                onAction(KeyAction.Enter)
            }
        }
    }
}

@Composable
private fun SymbolKeyboard(state: ImeUiState, onAction: (KeyAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        SymbolRows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { c ->
                    KeyButton(c.toString()) { onAction(KeyAction.Symbol(c)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            KeyButton("abc", weight = 1.5f) { onAction(KeyAction.ToggleSymbols) }
            KeyButton("空格", weight = 4f) { onAction(KeyAction.Space) }
            KeyButton("⌫", weight = 1.5f) { onAction(KeyAction.Backspace) }
            KeyButton(state.enterLabel, weight = 2f, accent = true) {
                onAction(KeyAction.Enter)
            }
        }
    }
}

@Composable
private fun RowScope.KeyButton(
    label: String,
    weight: Float = 1f,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .height(50.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (accent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = if (label.length > 2) 12.sp else 17.sp,
            color = if (accent) Color.White else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
