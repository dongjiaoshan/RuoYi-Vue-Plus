#!/usr/bin/env bash
#
# djs 模块回归测试脚本
#
#   ./regression-test.sh             全跑 5 个 djs 模块（common/breed/plant/warehouse/store）
#   ./regression-test.sh warehouse   只跑某个 djs 模块（参数 = common/breed/plant/warehouse/store）
#
# 关键：
#   - 必须 -Plocal —— 根 pom surefire <groups>${profiles.active}</groups>，local profile 下
#     profiles.active=local，只跑 @Tag("local") 的测试。不加 -Plocal（默认 profile=dev，groups=dev）
#     也能跑（djs 测试类同时标 @Tag("local") + @Tag("dev")），但本脚本统一用 local 口径。
#   - 必须 -DskipTests=false —— 根 pom <skipTests>true</skipTests> 默认跳过单测。
#   - 防假绿：每个模块解析 surefire 汇总 "Tests run: N"，断言 N>0；任一模块 N=0 或 BUILD FAILURE
#     视为失败，脚本以非 0 退出（避免 "Tests run: 0" 假绿骗过 CI）。
#
set -uo pipefail

# 脚本所在目录的上一级 = RuoYi-Vue-Plus 工程根（script/ 在工程根下）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

# 模块短名 → maven module 路径
declare -a ALL_MODULES=(common breed plant warehouse store)
module_path() {
  echo "ruoyi-modules/ruoyi-djs-$1"
}

# 解析单模块 maven test 输出，断言至少跑了 1 个测试。
# 入参：$1=模块短名 $2=该模块 mvn 输出日志文件
# 返回：0=通过（编译+测试均 OK 且 Tests run>0）；非 0=失败
assert_tests_ran() {
  local mod="$1" log="$2"

  if grep -q "BUILD FAILURE" "${log}"; then
    echo "  [FAIL] ${mod}: BUILD FAILURE（编译或测试失败）"
    grep -E "ERROR|Tests run:|FAILURE" "${log}" | tail -20
    return 1
  fi

  # surefire 汇总行形如：Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
  # 取所有汇总行里 Tests run 的最大值（reactor 多模块时每模块一行 + 总计一行）
  local summary
  summary="$(grep -E "Tests run: [0-9]+, Failures:" "${log}" || true)"
  if [[ -z "${summary}" ]]; then
    echo "  [FAIL] ${mod}: 未找到 surefire 'Tests run' 汇总行 —— 测试根本没执行（疑似假绿，检查 -Plocal / @Tag）"
    return 1
  fi

  local total_run
  total_run="$(echo "${summary}" | sed -E 's/.*Tests run: ([0-9]+),.*/\1/' | sort -n | tail -1)"
  local failures errors
  failures="$(echo "${summary}" | sed -E 's/.*Failures: ([0-9]+),.*/\1/' | sort -n | tail -1)"
  errors="$(echo "${summary}" | sed -E 's/.*Errors: ([0-9]+),.*/\1/' | sort -n | tail -1)"

  if [[ "${total_run}" -eq 0 ]]; then
    echo "  [FAIL] ${mod}: Tests run = 0（假绿！该模块未跑到任何 @Tag(\"local\") 测试）"
    return 1
  fi
  if [[ "${failures:-0}" -gt 0 || "${errors:-0}" -gt 0 ]]; then
    echo "  [FAIL] ${mod}: Tests run=${total_run} Failures=${failures} Errors=${errors}"
    grep -E "FAIL|ERROR" "${log}" | grep -iE "Test|expected|assert" | tail -20
    return 1
  fi

  echo "  [PASS] ${mod}: Tests run=${total_run}, Failures=0, Errors=0"
  return 0
}

run_module() {
  local mod="$1"
  local mpath
  mpath="$(module_path "${mod}")"
  if [[ ! -d "${PROJECT_ROOT}/${mpath}" ]]; then
    echo "  [SKIP] ${mod}: 模块目录不存在 ${mpath}"
    return 2
  fi

  local log
  log="$(mktemp -t djs-regr-"${mod}".XXXXXX)"
  echo "==> 测试模块 ${mod}（${mpath}）…"
  # -am: 连带编译上游依赖模块（ruoyi-djs-common 等），但 surefire 只在 -pl 目标模块跑。
  # 不加 -q：surefire "Tests run: N" 汇总行在 quiet 模式会被抑制，防假绿断言依赖该行，必须保留。
  # 过滤下载进度噪音（不影响 Tests run / BUILD 行）。
  mvn -Plocal -DskipTests=false test -pl "${mpath}" -am 2>&1 \
    | grep -vaE "Download(ing|ed) from|Progress \(|^\[INFO\] Download" \
    | tee "${log}"

  assert_tests_ran "${mod}" "${log}"
  local rc=$?
  rm -f "${log}"
  return ${rc}
}

main() {
  local -a targets
  if [[ $# -ge 1 ]]; then
    targets=("$1")
  else
    targets=("${ALL_MODULES[@]}")
  fi

  echo "================ djs regression-test ================"
  echo "工程根: ${PROJECT_ROOT}"
  echo "模块  : ${targets[*]}"
  echo "命令  : mvn -Plocal -DskipTests=false test -pl <module> -am"
  echo "====================================================="

  local -a passed=() failed=()
  for mod in "${targets[@]}"; do
    if run_module "${mod}"; then
      passed+=("${mod}")
    else
      failed+=("${mod}")
    fi
  done

  echo ""
  echo "================ 回归结果汇总 ================"
  echo "PASS (${#passed[@]}): ${passed[*]:-无}"
  echo "FAIL (${#failed[@]}): ${failed[*]:-无}"
  echo "============================================="

  if [[ ${#failed[@]} -gt 0 ]]; then
    echo "回归未通过 —— 见上方 [FAIL] 明细"
    exit 1
  fi
  echo "回归全部通过"
  exit 0
}

main "$@"
