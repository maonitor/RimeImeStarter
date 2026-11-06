#!/usr/bin/env bash
# 用 Android NDK 交叉编译真正的 librime.so，产出到 app/src/main/jniLibs/<ABI>/。
#
# 用法：
#   ./scripts/build-librime-android.sh <ANDROID_NDK_HOME> [ABI] [OUTPUT_DIR]
# 例：
#   ./scripts/build-librime-android.sh /d/Android/Sdk/ndk/27.0.12077973 arm64-v8a
#
# 已在 Windows 11 + Git Bash + NDK 27.0.12077973 + CMake 3.22.1 / Ninja + arm64-v8a
# 完整跑通（产出 3.6MB strip 后的 librime.so，导出 rime_get_api）。
#
# 依赖链（librime 1.12 的 4 个 REQUIRED 库 + Boost 头文件 + Boost.Regex）：
#   yaml-cpp / leveldb / marisa-trie(rime fork) / opencc / boost 1.84 headers / boost_regex
# glog 与 googletest 通过 -DENABLE_LOGGING=OFF -DBUILD_TEST=OFF 跳过。
# Boost 只需要头文件（signals2 等 header-only）与 regex 这一个编译库，
# regex 直接用 NDK clang++ 编 5 个 .cpp，不跑 b2。
set -euo pipefail

NDK="${1:-${ANDROID_NDK_HOME:-}}"
ABI="${2:-arm64-v8a}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${3:-$ROOT/app/src/main/jniLibs/$ABI}"
LIBRIME_VERSION="${LIBRIME_VERSION:-1.12.0}"
BOOST_VERSION="${BOOST_VERSION:-1.84.0}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-26}"
WORK="${WORK:-$ROOT/.librime-build}"
PREFIX="$WORK/prefix/$ABI"
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"

if [ -z "$NDK" ] || [ ! -f "$TOOLCHAIN" ]; then
  echo "错误：找不到 NDK toolchain，请传入 ANDROID_NDK_HOME。" >&2
  echo "用法：$0 <ANDROID_NDK_HOME> [ABI] [OUTPUT_DIR]" >&2
  exit 1
fi

IS_WIN=0
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) IS_WIN=1 ;; esac

# Windows 的 cmake.exe/clang++.exe 是原生程序，只认 D:/... 风格路径。
native() {
  if [ "$IS_WIN" -eq 1 ] && [[ "$1" =~ ^/([a-zA-Z])/(.*)$ ]]; then
    echo "${BASH_REMATCH[1]^}:/${BASH_REMATCH[2]}"
  else
    echo "$1"
  fi
}

find_tool() {
  local name="$1" exe="$1"
  [ "$IS_WIN" -eq 1 ] && exe="$name.exe"
  if command -v "$name" >/dev/null 2>&1; then command -v "$name"; return; fi
  if [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/cmake/3.22.1/bin/$exe" ]; then
    echo "$ANDROID_HOME/cmake/3.22.1/bin/$exe"; return
  fi
  echo "错误：找不到 $name，请把它加入 PATH。" >&2
  exit 1
}

CMAKE_BIN="$(find_tool cmake)"
NINJA_BIN="$(find_tool ninja)"
HOST_DIR="$(find "$NDK/toolchains/llvm/prebuilt" -mindepth 1 -maxdepth 1 -type d -print -quit)"
CLANGXX="$HOST_DIR/bin/clang++"; [ "$IS_WIN" -eq 1 ] && CLANGXX="$CLANGXX.exe"
LLVM_AR="$HOST_DIR/bin/llvm-ar"; [ "$IS_WIN" -eq 1 ] && LLVM_AR="$LLVM_AR.exe"
LLVM_STRIP="$HOST_DIR/bin/llvm-strip"; [ "$IS_WIN" -eq 1 ] && LLVM_STRIP="$LLVM_STRIP.exe"
NINJA_NATIVE="$(native "$NINJA_BIN")"

run_cmake() {
  if [ "$IS_WIN" -eq 1 ]; then env MSYS_NO_PATHCONV=1 "$CMAKE_BIN" "$@"; else "$CMAKE_BIN" "$@"; fi
}

mkdir -p "$WORK" "$PREFIX" "$OUT"

BASE=(-G Ninja
  -DCMAKE_MAKE_PROGRAM="$NINJA_NATIVE"
  -DCMAKE_TOOLCHAIN_FILE="$(native "$TOOLCHAIN")"
  -DANDROID_ABI="$ABI"
  -DANDROID_PLATFORM="android-$ANDROID_PLATFORM"
  -DANDROID_STL=c++_shared
  -DCMAKE_BUILD_TYPE=Release
  -DCMAKE_INSTALL_PREFIX="$(native "$PREFIX")"
  -DCMAKE_PREFIX_PATH="$(native "$PREFIX")"
  -DCMAKE_FIND_ROOT_PATH="$(native "$PREFIX")"
  -DBUILD_SHARED_LIBS=OFF)

# $1=目录名 $2=repo $3=ref $4=tags|heads
fetch_repo() {
  local name="$1" repo="$2" ref="$3" kind="${4:-tags}"
  [ -d "$WORK/$name" ] && return
  echo "==> 下载 $name ($ref)"
  curl -fsSL "https://codeload.github.com/$repo/tar.gz/refs/$kind/$ref" -o "$WORK/$name.tar.gz"
  mkdir -p "$WORK/$name"
  tar -xzf "$WORK/$name.tar.gz" -C "$WORK/$name" --strip-components=1
}

build_dep() {
  local name="$1"; shift
  echo "==> 编译依赖 $name"
  # 脏 CMakeCache 会让 marisa 卡在 "Detecting CXX compiler ABI info"，每次清干净。
  rm -rf "$WORK/build-$name-$ABI"
  run_cmake -S "$(native "$WORK/$name")" -B "$(native "$WORK/build-$name-$ABI")" "${BASE[@]}" "$@"
  run_cmake --build "$(native "$WORK/build-$name-$ABI")" --parallel
  run_cmake --install "$(native "$WORK/build-$name-$ABI")"
}

fetch_repo librime "rime/librime" "$LIBRIME_VERSION"
fetch_repo yaml-cpp "jbeder/yaml-cpp" "0.8.0"
fetch_repo leveldb "google/leveldb" "1.23"
fetch_repo marisa-trie "rime/marisa-trie" "master" "heads"
fetch_repo opencc "BYVoid/OpenCC" "master" "heads"

build_dep yaml-cpp \
  -DYAML_CPP_BUILD_TESTS=OFF -DYAML_CPP_BUILD_TOOLS=OFF -DYAML_CPP_BUILD_CONTRIB=OFF
build_dep leveldb \
  -DLEVELDB_BUILD_TESTS=OFF -DLEVELDB_BUILD_BENCHMARKS=OFF
build_dep marisa-trie -DBUILD_TOOLS=OFF
build_dep opencc \
  -DBUILD_TESTING=OFF -DENABLE_GTEST=OFF -DENABLE_DARTS=OFF

echo "==> 准备 Boost $BOOST_VERSION 头文件与 regex 库"
BOOST_TARBALL="$WORK/boost.tar.gz"
if [ ! -f "$BOOST_TARBALL" ]; then
  curl -fsSL --max-time 900 -o "$BOOST_TARBALL" \
    "https://archives.boost.io/release/$BOOST_VERSION/source/boost_${BOOST_VERSION//./_}.tar.gz"
fi
BOOST_DIR="boost_${BOOST_VERSION//./_}"
mkdir -p "$WORK/boost-stage" "$WORK/boost-regex-obj"
if [ ! -d "$WORK/boost-stage/boost" ]; then
  tar -xzf "$BOOST_TARBALL" -C "$WORK/boost-stage" --strip-components=1 \
    "$BOOST_DIR/boost" "$BOOST_DIR/libs/regex"
fi
mkdir -p "$PREFIX/include"
cp -r "$WORK/boost-stage/boost" "$PREFIX/include/"

REGEX_SRC="$(native "$WORK/boost-stage/libs/regex/src")"
INC="$(native "$PREFIX/include")"
OBJ="$(native "$WORK/boost-regex-obj")"
OBJS=()
for name in posix_api regex regex_debug static_mutex wide_posix_api; do
  "$CLANGXX" --target=aarch64-none-linux-android$ANDROID_PLATFORM \
    -I"$INC" -I"$REGEX_SRC" -O3 -fPIC -std=c++17 -DNDEBUG \
    -c "$REGEX_SRC/$name.cpp" -o "$OBJ/$name.o"
  OBJS+=("$OBJ/$name.o")
done
"$LLVM_AR" rcs "$(native "$PREFIX/lib/libboost_regex.a")" "${OBJS[@]}"

echo "==> 编译 librime $LIBRIME_VERSION"
rm -rf "$WORK/build-rime-$ABI"
run_cmake -S "$(native "$WORK/librime")" -B "$(native "$WORK/build-rime-$ABI")" \
  -DCMAKE_MAKE_PROGRAM="$NINJA_NATIVE" \
  -DCMAKE_TOOLCHAIN_FILE="$(native "$TOOLCHAIN")" \
  -DANDROID_ABI="$ABI" \
  -DANDROID_PLATFORM="android-$ANDROID_PLATFORM" \
  -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="$(native "$PREFIX")" \
  -DCMAKE_FIND_ROOT_PATH="$(native "$PREFIX")" \
  -DCMAKE_INSTALL_PREFIX="$(native "$PREFIX")" \
  -DBOOST_ROOT="$(native "$PREFIX")" -DBoost_NO_BOOST_CMAKE=ON -DBoost_USE_STATIC_LIBS=ON \
  -DBUILD_TEST=OFF -DBUILD_SAMPLE=OFF -DBUILD_DATA=OFF -DBUILD_STATIC=ON \
  -DENABLE_LOGGING=OFF -DENABLE_EXTERNAL_PLUGINS=OFF \
  -DBUILD_SHARED_LIBS=ON
run_cmake --build "$(native "$WORK/build-rime-$ABI")" --target rime --parallel

SO="$(find "$WORK/build-rime-$ABI" -name 'librime.so' -print -quit)"
if [ -z "$SO" ]; then
  echo "错误：没有生成 librime.so。" >&2
  exit 1
fi
cp "$SO" "$OUT/librime.so"
"$LLVM_STRIP" --strip-unneeded "$OUT/librime.so"

# librime.so 用 -DANDROID_STL=c++_shared 编译，必须同时打包 NDK 的 C++ 运行库；
# AGP 不会分析 jniLibs 里预编译 .so 的依赖，所以要显式复制。
case "$ABI" in
  arm64-v8a)   STL_TRIPLE="aarch64-linux-android" ;;
  x86_64)      STL_TRIPLE="x86_64-linux-android" ;;
  armeabi-v7a) STL_TRIPLE="arm-linux-androideabi" ;;
  x86)         STL_TRIPLE="i686-linux-android" ;;
  *)           STL_TRIPLE="" ;;
esac
if [ -n "$STL_TRIPLE" ]; then
  STL_SRC="$(find "$NDK/toolchains/llvm/prebuilt" -path "*$STL_TRIPLE*" -name 'libc++_shared.so' -print -quit)"
  if [ -n "$STL_SRC" ]; then
    cp "$STL_SRC" "$OUT/libc++_shared.so"
    echo "==> 附带 C++ 运行库：$OUT/libc++_shared.so"
  fi
fi

echo "==> 完成：$OUT/librime.so ($(du -h "$OUT/librime.so" | cut -f1))"
echo "    重新 assembleDebug 后输入法会自动从 mock 热切换到 rime 引擎。"
echo "    可用 scripts/verify-rime-so.sh 检查导出符号。"
