package com.example.rimeime.engine

/**
 * librime 的 JNI 契约（占位，最小工程不加载 .so，也不调用这些方法）。
 *
 * 启用步骤：
 * 1. NDK + CMake 交叉编译 librime（依赖 boost / marisa-trie / yaml-cpp / glog / opencc），
 *    产出 librime_jni.so，建议只出 arm64-v8a。
 * 2. 打开 app/build.gradle.kts 里的 externalNativeBuild，并新增 src/main/cpp/CMakeLists.txt。
 * 3. native 侧按 com_example_rimeime_engine_RimeBridge 实现下列函数，
 *    或用 RegisterNatives 动态注册。
 * 4. 新建 RimeEngine : InputEngine，把 process() 转成 processKey + 读取 RimeContext，
 *    在后台线程调用，再把结果映射为 EngineOutput。
 * 5. schema 与词库（雾凇拼音 / rime-ice）放 assets 或首次启动下载，首次 deploy 需要进度提示。
 *
 * 注意：release 混淆必须 keep 本类，否则 JNI 静态注册会失效（见 proguard-rules.pro）。
 */
object RimeBridge {

    external fun nativeInit(schemaDir: String, userDir: String): Boolean
    external fun nativeDeploy(): Boolean
    external fun nativeProcessKey(keycode: Int, mask: Int): Boolean
    external fun nativeGetComposing(): String
    external fun nativeGetCandidates(): Array<String>
    external fun nativeSelect(index: Int): Boolean
    external fun nativeReset(): Unit

    /** 未编译 .so 时返回 false，避免 UnsatisfiedLinkError 让输入法崩溃。 */
    val isAvailable: Boolean by lazy {
        try {
            System.loadLibrary("rime_jni")
            true
        } catch (t: Throwable) {
            false
        }
    }
}
