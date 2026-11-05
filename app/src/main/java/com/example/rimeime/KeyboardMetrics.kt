package com.example.rimeime

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * 键盘尺寸与安全区。放在 Service 侧计算，而不是直接用 Compose 的
 * navigationBarsPadding()：IME 窗口不是普通 Activity 窗口，部分 ROM 上报的
 * WindowInsets 是 0，手势导航条会压住最底排按键。
 */
data class KeyboardMetrics(
    val keyHeightDp: Int = 52,
    val bottomInsetDp: Int = 0,
    val sideInsetDp: Int = 0,
)

object KeyboardMetricsCalculator {

    fun compute(context: Context, view: View?): KeyboardMetrics {
        val res = context.resources
        val config = res.configuration
        val density = res.displayMetrics.density.coerceAtLeast(1f)
        val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val wideScreen = config.smallestScreenWidthDp >= 600  // 平板 / 折叠屏展开

        val keyHeight = when {
            landscape -> 42       // 横屏留给编辑区更多空间
            wideScreen -> 62      // 大屏按键可以更大
            else -> 52
        }
        val bottomDp = (navigationBarBottomPx(view, res) / density)
            .toInt()
            .coerceIn(0, 48)
        // 横屏凹口 / 折叠屏铰链：给一个保守的水平安全边距。
        val sideDp = when {
            landscape -> 20
            wideScreen -> 24
            else -> 0
        }
        return KeyboardMetrics(keyHeight, bottomDp, sideDp)
    }

    private fun navigationBarBottomPx(view: View?, res: Resources): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && view != null) {
            val insets = view.rootWindowInsets
            if (insets != null) {
                val nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                val gesture = insets.getInsets(WindowInsets.Type.systemGestures()).bottom
                if (nav > 0) return nav
                if (gesture > 0) return gesture
            }
        }
        // 兜底：IME 窗口拿不到 insets 时读系统 dimen。
        val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else 0
    }
}
