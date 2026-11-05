#!/usr/bin/env bash
# 主流 App 兼容性回归的 adb 辅助脚本。
#
# 用法：
#   ./scripts/ime-compat-adb.sh setup    [serial]   安装 APK、启用并设为当前输入法
#   ./scripts/ime-compat-adb.sh apps     [serial]   列出设备上已装的目标 App
#   ./scripts/ime-compat-adb.sh ui       [serial]   打开系统输入法设置
#   ./scripts/ime-compat-adb.sh capture  [serial]   抓取本轮 logcat 到 compatibility/report/
#   ./scripts/ime-compat-adb.sh clear    [serial]   清空 logcat（开始一轮前执行）
#
# 人工用例见 compatibility/manual-regression.txt。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ACTION="${1:-setup}"
SERIAL="${2:-}"
PKG="com.example.rimeime"
COMPONENT="$PKG/.RimeImeService"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
REPORT="$ROOT/compatibility/report"

ADB=(adb)
if [ -n "$SERIAL" ]; then ADB=(adb -s "$SERIAL"); fi

case "$ACTION" in
  setup)
    if [ ! -f "$APK" ]; then
      echo "未找到 $APK，请先 assembleDebug。" >&2
      exit 1
    fi
    "${ADB[@]}" install -r "$APK"
    "${ADB[@]}" shell ime enable "$COMPONENT"
    "${ADB[@]}" shell ime set "$COMPONENT"
    echo "已启用并设为当前输入法：$COMPONENT"
    echo "接下来按 compatibility/manual-regression.txt 逐项操作。"
    ;;

  ui)
    "${ADB[@]}" shell am start -a android.settings.INPUT_METHOD_SETTINGS
    ;;

  apps)
    echo "== 设备已安装的目标 App =="
    for spec in \
      "微信:com.tencent.mm" \
      "QQ:com.tencent.mobileqq" \
      "支付宝:com.eg.android.AlipayGphone" \
      "淘宝:com.taobao.taobao" \
      "抖音:com.ss.android.ugc.aweme" \
      "小红书:com.xingin.xhs" \
      "Chrome:com.android.chrome" \
      "企业微信:com.tencent.wework" \
      "钉钉:com.alibaba.android.rimet"; do
      name="${spec%%:*}"; pkg="${spec##*:}"
      if "${ADB[@]}" shell pm list packages "$pkg" 2>/dev/null | grep -q "^package:$pkg$"; then
        echo "  [已装] $name ($pkg)"
      else
        echo "  [未装] $name ($pkg)"
      fi
    done
    echo "== 当前输入法 =="
    "${ADB[@]}" shell settings get secure default_input_method
    ;;

  clear)
    "${ADB[@]}" logcat -c
    echo "logcat 已清空，可以开始一轮人工回归。"
    ;;

  capture)
    mkdir -p "$REPORT"
    STAMP="$(date +%Y%m%d-%H%M%S)"
    OUT="$REPORT/logcat-$STAMP.txt"
    "${ADB[@]}" logcat -d -v time \
      -s RimeImeService:V RimeBridge:V AndroidRuntime:E InputMethodService:W > "$OUT"
    echo "已保存：$OUT"
    echo "请把它和 compatibility/manual-regression.txt 的填写结果一并归档。"
    ;;

  *)
    echo "未知动作：$ACTION" >&2
    echo "可用：setup / apps / ui / clear / capture" >&2
    exit 1
    ;;
esac
