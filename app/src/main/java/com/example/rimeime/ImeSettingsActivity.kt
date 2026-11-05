package com.example.rimeime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rimeime.ui.ImeKeyboardTheme

/**
 * 输入法装完后默认是「未启用」状态，没有引导页用户根本用不起来。
 * 这个页面同时被 method.xml 的 settingsActivity 引用，系统输入法设置里点齿轮会进来。
 */
class ImeSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImeKeyboardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = "Rime IME Starter",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "路线 B 的最小骨架：Kotlin + Compose 键盘，中文闭环由 mock 拼音引擎提供，下一步替换为 librime。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = {
                                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            },
                        ) {
                            Text("1. 到系统设置里启用输入法")
                        }
                        Button(
                            onClick = {
                                val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
                                    as InputMethodManager
                                imm.showInputMethodPicker()
                            },
                        ) {
                            Text("2. 切换为当前输入法")
                        }
                        Text(
                            text = "启用后点任意输入框即可调出键盘。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
