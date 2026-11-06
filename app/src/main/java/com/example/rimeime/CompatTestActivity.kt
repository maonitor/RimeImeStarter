package com.example.rimeime

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 兼容性回归专用宿主页面，adb 可直接拉起：
 *   adb shell am start -n com.example.rimeime/.CompatTestActivity
 *
 * 固定提供 4 类输入框（普通 / 密码 / 搜索 / 多行），每个带 contentDescription
 * 便于 uiautomator 精确定位；底部 mirror TextView 汇总所有框的文本，
 * 让脚本不必依赖截图 OCR 就能断言 composing/commit 的结果。
 *
 * 浏览器首页会随推荐内容变化，固定坐标不可靠，所以回归一律用这个页面。
 */
class CompatTestActivity : Activity() {

    private val fields = mutableListOf<Pair<String, EditText>>()
    private lateinit var mirror: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "Rime IME 兼容性自测页"
            textSize = 20f
        })

        addField(
            root, "normal", "普通输入框", InputType.TYPE_CLASS_TEXT,
            EditorInfo.IME_ACTION_NONE, singleLine = true,
        )
        addField(
            root, "password", "密码框",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            EditorInfo.IME_ACTION_DONE, singleLine = true,
        )
        addField(
            root, "search", "搜索框", InputType.TYPE_CLASS_TEXT,
            EditorInfo.IME_ACTION_SEARCH, singleLine = true,
        )
        addField(
            root, "multiline", "多行输入框",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            EditorInfo.IME_ACTION_NONE, singleLine = false,
        ).apply { minLines = 3 }

        mirror = TextView(this).apply {
            contentDescription = "mirror"
            textSize = 13f
            setPadding(0, 32, 0, 0)
        }
        root.addView(mirror)
        updateMirror()
        setContentView(root)
    }

    private fun addField(
        root: LinearLayout,
        tag: String,
        hint: String,
        inputType: Int,
        imeAction: Int,
        singleLine: Boolean,
    ): EditText {
        val edit = EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            imeOptions = imeAction
            isSingleLine = singleLine
            contentDescription = "$tag-input"
            textSize = 16f
        }
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateMirror()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        fields += tag to edit
        root.addView(edit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        return edit
    }

    private fun updateMirror() {
        if (!::mirror.isInitialized) return
        mirror.text = fields.joinToString(" | ") { (tag, edit) -> "$tag=${edit.text}" }
    }
}
