package com.example.rimeime.engine

import com.example.rimeime.EngineOutput
import com.example.rimeime.KeyAction

/**
 * 最小中文输入闭环：按键 → 拼音串 → 候选 → 上屏。
 *
 * 这里刻意只做「整串查表 + 用户词频」，没做音节切分、模糊音和 N-gram 打分。
 * 换成 librime 时，把 process() 内部替换成对 RimeBridge 的调用即可。
 */
class MockPinyinEngine : InputEngine {

    private val buffer = StringBuilder()
    private val userFreq = mutableMapOf<String, Int>()

    override fun process(action: KeyAction): EngineOutput = when (action) {
        is KeyAction.Letter -> {
            buffer.append(action.char.lowercaseChar())
            snapshot()
        }

        KeyAction.Backspace -> {
            if (buffer.isNotEmpty()) buffer.deleteCharAt(buffer.lastIndex)
            snapshot()
        }

        KeyAction.Space -> commit(candidates().firstOrNull())

        is KeyAction.SelectCandidate -> commit(candidates().getOrNull(action.index))

        // 回车时把拼音原文上屏，避免用户输入丢失。
        KeyAction.Enter -> commit(buffer.toString())

        else -> snapshot()
    }

    override fun reset() {
        buffer.clear()
    }

    private fun commit(word: String?): EngineOutput {
        val text = word ?: buffer.toString()
        if (text.isNotEmpty() && word != null) {
            userFreq[word] = (userFreq[word] ?: 0) + 1
        }
        reset()
        return EngineOutput(commit = text.ifEmpty { null })
    }

    private fun snapshot() = EngineOutput(
        composing = buffer.toString(),
        candidates = candidates(),
    )

    /** 用户词频越高越靠前，这是真实输入法最基础的自学习机制。 */
    private fun candidates(): List<String> {
        val key = buffer.toString()
        if (key.isEmpty()) return emptyList()
        val base = DICTIONARY[key] ?: listOf(key)
        return base.sortedByDescending { userFreq[it] ?: 0 }
    }

    private companion object {
        val DICTIONARY: Map<String, List<String>> = mapOf(
            "a" to listOf("啊", "阿", "吖", "呵"),
            "ai" to listOf("爱", "哎", "矮", "碍", "哀"),
            "ba" to listOf("吧", "把", "八", "爸", "罢"),
            "bu" to listOf("不", "部", "步", "布", "补"),
            "de" to listOf("的", "得", "地", "德"),
            "hao" to listOf("好", "号", "毫", "豪", "耗"),
            "hen" to listOf("很", "狠", "恨", "痕"),
            "jie" to listOf("界", "接", "解", "街", "节"),
            "ma" to listOf("吗", "妈", "马", "码", "嘛"),
            "ni" to listOf("你", "尼", "泥", "拟", "逆"),
            "nihao" to listOf("你好", "拟好"),
            "shi" to listOf("是", "时", "事", "十", "使", "式"),
            "shijie" to listOf("世界", "视界", "诗节"),
            "wo" to listOf("我", "窝", "握", "卧", "沃"),
            "women" to listOf("我们", "我不能"),
            "xie" to listOf("谢", "写", "些", "鞋", "斜"),
            "xiexie" to listOf("谢谢", "写写"),
            "zhong" to listOf("中", "种", "重", "众", "终"),
            "zhongwen" to listOf("中文", "中温"),
            "wen" to listOf("文", "问", "闻", "稳", "吻"),
            "zi" to listOf("字", "自", "子", "资", "紫"),
            "shuru" to listOf("输入", "数入"),
            "shurufa" to listOf("输入法", "输入阀"),
            "ime" to listOf("输入法"),
        )
    }
}
