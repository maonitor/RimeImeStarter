#!/usr/bin/env bash
# 跑兼容性用例集里可自动化的部分：JVM 单元测试 + 真机 instrumentation 测试。
# 用法：./scripts/run-compat-tests.sh [unit|device]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-unit}"

case "$MODE" in
  unit)
    "$ROOT/gradlew" -p "$ROOT" :app:testDebugUnitTest --console=plain
    echo "单元测试报告：$ROOT/app/build/reports/tests/testDebugUnitTest/index.html"
    ;;
  device)
    "$ROOT/gradlew" -p "$ROOT" :app:connectedDebugAndroidTest --console=plain
    ;;
  *)
    echo "用法：$0 [unit|device]" >&2
    exit 1
    ;;
esac
