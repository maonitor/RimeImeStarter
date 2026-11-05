package com.example.rimeime.engine

import android.util.Log

/**
 * librime 的 JNI 契约。
 *
 * 两段式加载：
 *  - libimebridge.so：本工程 CMake 编译，一定存在，负责 dlopen/dlsym。
 *  - librime.so：由 scripts/build-librime-android.sh 产出并放到
 *    app/src/main/jniLibs/<abi>/，运行时动态加载。
 *
 * 因此没有 librime.so 时工程照样编译安装，输入法自动降级为 MockPinyinEngine。
 * 调用任何 native 方法前必须先检查 [isAvailable]。
 *
 * release 混淆必须 keep 本类，否则 JNI 静态注册会失效（见 proguard-rules.pro）。
 */
object RimeBridge {

    private const val TAG = "RimeBridge"

    private var bridgeLoaded = false

    init {
        try {
            System.loadLibrary("imebridge")
            bridgeLoaded = true
        } catch (t: Throwable) {
            Log.w(TAG, "libimebridge.so 未打包，使用 mock 引擎", t)
        }
    }

    /** true 表示 librime.so 已加载且 rime_get_api 函数表完整。 */
    val isAvailable: Boolean
        get() = bridgeLoaded && try {
            nativeIsEnginePresent()
        } catch (t: Throwable) {
            false
        }

    val version: String
        get() = if (bridgeLoaded) {
            try {
                nativeVersion()
            } catch (t: Throwable) {
                "unavailable"
            }
        } else {
            "unavailable"
        }

    external fun nativeIsEnginePresent(): Boolean
    external fun nativeInit(sharedDataDir: String, userDataDir: String, appName: String): Boolean
    external fun nativeDeploy(): Boolean
    external fun nativeCreateSession(): Long
    external fun nativeDestroySession(session: Long)
    external fun nativeProcessKey(session: Long, keycode: Int, mask: Int)
    external fun nativeTakeCommit(session: Long): String?
    external fun nativeGetComposing(session: Long): String
    external fun nativeGetCandidates(session: Long): Array<String>
    external fun nativeSelectCandidate(session: Long, index: Int)
    external fun nativeCommitComposition(session: Long)
    external fun nativeClearComposition(session: Long)
    external fun nativeVersion(): String
}
