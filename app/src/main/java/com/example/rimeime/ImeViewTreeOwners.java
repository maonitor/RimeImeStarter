package com.example.rimeime;

import android.view.View;

import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.lifecycle.ViewTreeViewModelStoreOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;

/**
 * 把三个 ViewTree owner 挂到 ComposeView 上。
 *
 * 为什么用 Java 写：lifecycle 2.9 起 ViewTreeLifecycleOwner / ViewTreeViewModelStoreOwner
 * 的 Kotlin 元数据是隐藏的，Kotlin 侧 import 会报 Unresolved reference；
 * 但 JVM 层仍是 public static set(View, Owner)，Java 调用完全合法。
 * 这也是绕过 AndroidX Kotlin 可见性变更最稳的做法，不依赖具体 lifecycle 小版本。
 */
final class ImeViewTreeOwners {

    private ImeViewTreeOwners() {
    }

    static void attach(View view, ImeLifecycleOwner owner) {
        ViewTreeLifecycleOwner.set(view, owner);
        ViewTreeViewModelStoreOwner.set(view, owner);
        ViewTreeSavedStateRegistryOwner.set(view, owner);
    }
}
