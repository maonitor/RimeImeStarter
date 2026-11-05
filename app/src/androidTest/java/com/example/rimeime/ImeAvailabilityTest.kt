package com.example.rimeime

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机侧的最小冒烟测试：确认 IME 服务声明正确、且已在系统里启用。
 * 运行前先执行 scripts/ime-compat-adb.sh（或手工在系统设置里启用）。
 *
 * 注意：系统级 InputConnection 行为无法靠 instrumentation 直接驱动，
 * 各 App 的差异必须用 compatibility/manual-regression.txt 人工回归。
 */
@RunWith(AndroidJUnit4::class)
class ImeAvailabilityTest {

    @Test
    fun imeServiceIsDeclaredInManifest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val services = context.packageManager.queryIntentServices(Intent("android.view.InputMethod"), 0)
        val found = services.any { it.serviceInfo.packageName == context.packageName }
        assertTrue("Manifest 缺少 InputMethodService 声明", found)
    }

    @Test
    fun imeIsEnabledInSystemSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any {
            it.serviceInfo.packageName == context.packageName
        }
        assertTrue("请先在系统设置里启用本输入法", enabled)
    }
}
