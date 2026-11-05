#!/usr/bin/env bash
# 用 Android NDK 交叉编译 librime.so，产出到 app/src/main/jniLibs/<ABI>/。
#
# 用法：
#   ./scripts/build-librime-android.sh <ANDROID_NDK_HOME> [ABI] [OUTPUT_DIR]
# 例：
#   ./scripts/build-librime-android.sh /d/Android/Sdk/ndk/27.0.12077973 arm64-v8a
#
# 依赖：git / curl / tar / cmake / ninja 或 make。
# 建议在 Linux、macOS 或 WSL 下运行；Windows 需 Git Bash 且 NDK 路径用 Unix 风格。
#
# 说明：librime 官方没有 Android 预编译包，deps/ 是 6 个 git submodule，
# 需要逐个用同一套 NDK toolchain 编成静态库，再让 librime 的 find_package 找到。
# 各依赖的 CMake 选项随版本变化，若某个依赖报错，按它的 CMakeLists.txt 调整下面的
# build_dep 参数即可；fcitx5-android / Trime 仓库的 CMake 配置也是很好的参考。
set -euo pipefail

NDK="${1:-${ANDROID_NDK_HOME:-}}"
ABI="${2:-arm64-v8a}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${3:-$ROOT/app/src/main/jniLibs/$ABI}"
LIBRIME_VERSION="${LIBRIME_VERSION:-1.12.0}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-26}"
WORK="${WORK:-$ROOT/.librime-build}"
PREFIX="$WORK/prefix/$ABI"
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"

if [ -z "$NDK" ] || [ ! -f "$TOOLCHAIN" ]; then
  echo "错误：找不到 NDK toolchain，请传入 ANDROID_NDK_HOME。" >&2
  echo "用法：$0 <ANDROID_NDK_HOME> [ABI] [OUTPUT_DIR]" >&2
  exit 1
fi

mkdir -p "$WORK" "$PREFIX" "$OUT"

# $1=本地目录名 $2=GitHub repo $3=ref $4=tags|heads
fetch_repo() {
  local name="$1" repo="$2" ref="$3" kind="${4:-tags}"
  if [ -d "$WORK/$name" ]; then return; fi
  echo "==> 下载 $name ($ref)"
  curl -fsSL "https://codeload.github.com/$repo/tar.gz/refs/$kind/$ref" -o "$WORK/$name.tar.gz"
  mkdir -p "$WORK/$name"
  tar -xzf "$WORK/$name.tar.gz" -C "$WORK/$name" --strip-components=1
}

build_dep() {
  local name="$1"; shift
  echo "==> 编译依赖 $name"
  cmake -S "$WORK/$name" -B "$WORK/build-$name-$ABI" \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="android-$ANDROID_PLATFORM" \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$PREFIX" \
    -DCMAKE_FIND_ROOT_PATH="$PREFIX" \
    -DBUILD_SHARED_LIBS=OFF \
    "$@"
  cmake --build "$WORK/build-$name-$ABI" --parallel
  cmake --install "$WORK/build-$name-$ABI"
}

fetch_repo librime "rime/librime" "$LIBRIME_VERSION"
fetch_repo yaml-cpp "jbeder/yaml-cpp" "0.8.0"
fetch_repo glog "google/glog" "v0.7.1"
fetch_repo leveldb "google/leveldb" "1.23"
fetch_repo marisa-trie "rime/marisa-trie" "master" "heads"
fetch_repo opencc "BYVoid/OpenCC" "master" "heads"

build_dep yaml-cpp \
  -DYAML_CPP_BUILD_TESTS=OFF -DYAML_CPP_BUILD_TOOLS=OFF -DYAML_CPP_BUILD_CONTRIB=OFF
build_dep glog \
  -DWITH_GFLAGS=OFF -DWITH_GTEST=OFF -DWITH_UNWIND=OFF -DBUILD_TESTING=OFF
build_dep leveldb \
  -DLEVELDB_BUILD_TESTS=OFF -DLEVELDB_BUILD_BENCHMARKS=OFF
build_dep marisa-trie
build_dep opencc \
  -DBUILD_TESTING=OFF -DENABLE_GTEST=OFF -DENABLE_DARTS=OFF

echo "==> 编译 librime $LIBRIME_VERSION"
cmake -S "$WORK/librime" -B "$WORK/build-rime-$ABI" \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
  -DANDROID_ABI="$ABI" \
  -DANDROID_PLATFORM="android-$ANDROID_PLATFORM" \
  -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="$PREFIX" \
  -DCMAKE_FIND_ROOT_PATH="$PREFIX" \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" \
  -DBUILD_TEST=OFF -DBUILD_SAMPLE=OFF -DBUILD_DATA=OFF -DBUILD_STATIC=ON \
  -DENABLE_LOGGING=OFF -DENABLE_EXTERNAL_PLUGINS=OFF \
  -DBUILD_SHARED_LIBS=ON
cmake --build "$WORK/build-rime-$ABI" --target rime --parallel

SO="$(find "$WORK/build-rime-$ABI" -name 'librime.so' -print -quit)"
if [ -z "$SO" ]; then
  echo "错误：没有生成 librime.so，请检查上面的编译日志。" >&2
  exit 1
fi
cp "$SO" "$OUT/librime.so"
echo "==> 完成：$OUT/librime.so"
echo "    重新 assembleDebug 后，输入法会自动从 mock 切换到 rime 引擎。"
echo "    可用 scripts/verify-rime-so.sh 检查导出符号。"
