#!/usr/bin/env bash
# 检查 jniLibs 里的 librime.so 是否可被 JNI 桥加载（导出 rime_get_api）。
# 用法：./scripts/verify-rime-so.sh [ANDROID_NDK_HOME] [SO_PATH]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${1:-${ANDROID_NDK_HOME:-/d/Android/Sdk/ndk/27.0.12077973}}"
SO="${2:-}"
NM="$(find "$NDK" -name llvm-nm.exe -o -name llvm-nm 2>/dev/null | head -1)"
RE="$(find "$NDK" -name llvm-readelf.exe -o -name llvm-readelf 2>/dev/null | head -1)"

if [ -z "$SO" ]; then
  SO="$(find "$ROOT/app/src/main/jniLibs" -name 'librime.so' -print -quit 2>/dev/null || true)"
fi
if [ -z "$SO" ] || [ ! -f "$SO" ]; then
  echo "未找到 librime.so。当前输入法会运行在 mock 引擎。"
  echo "请先执行：scripts/build-librime-android.sh <NDK> arm64-v8a"
  exit 0
fi

echo "库：$SO ($(stat -c%s "$SO") bytes)"
echo "== 依赖 =="
"$RE" -d "$SO" | grep NEEDED || true
echo "== rime_get_api =="
if "$NM" -D --defined-only "$SO" | grep -q " rime_get_api"; then
  echo "OK：导出 rime_get_api，JNI 桥可以加载。"
  "$NM" -D --defined-only "$SO" | grep " rime_get_api"
else
  echo "失败：未导出 rime_get_api，这个 .so 不是可直接链接的 librime。"
  exit 1
fi
