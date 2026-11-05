# JNI 桥通过静态注册查找 com.example.rimeime.engine.RimeBridge 的 native 方法。
# 混淆类名会破坏 Java_com_example_rimeime_engine_RimeBridge_* 符号匹配。
-keep class com.example.rimeime.engine.RimeBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
