package com.example.rimeime.engine

import android.content.Context
import java.io.File

/**
 * 把 assets/rime 的 schema 与词典释放到可写目录，并完成 librime 首次部署。
 *
 * Rime 要求：
 *  - shared_data_dir：schema 与 dict.yaml，部署生成的 table.bin 也写这里；
 *  - user_data_dir：用户词典、user.yaml、installation.yaml。
 *
 * deploy() 会编译词典，首次可达数秒，必须在后台线程调用。
 */
class RimeDeployer(private val context: Context) {

    private val sharedDir = File(context.filesDir, "rime")
    private val userDir = File(context.filesDir, "rime-user")
    private val stamp = File(sharedDir, DATA_STAMP)

    fun prepare(): Boolean {
        if (!RimeBridge.isAvailable) return false
        if (!stamp.exists()) {
            copyAssets(DATA_ASSET_DIR, sharedDir)
            stamp.writeText("rime data v1")
        }
        userDir.mkdirs()
        val initialized = RimeBridge.nativeInit(
            sharedDir.absolutePath,
            userDir.absolutePath,
            "com.example.rimeime",
        )
        return initialized && RimeBridge.nativeDeploy()
    }

    /** assets 里的目录用 list() 递归展开，文件用 open() 复制。 */
    private fun copyAssets(assetPath: String, target: File) {
        val assets = context.assets
        target.mkdirs()
        for (name in assets.list(assetPath).orEmpty()) {
            val childPath = "$assetPath/$name"
            val children = assets.list(childPath).orEmpty()
            val destination = File(target, name)
            if (children.isNotEmpty()) {
                copyAssets(childPath, destination)
            } else {
                assets.open(childPath).use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private companion object {
        const val DATA_ASSET_DIR = "rime"
        // schema 变更时必须升级，否则旧数据不会重新复制/重新部署。
        const val DATA_STAMP = ".rime-data-v2"
    }
}
