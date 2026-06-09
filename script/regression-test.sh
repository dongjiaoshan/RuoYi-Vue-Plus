#!/usr/bin/env bash
#
# djs 后端回归测试 —— 一条命令跑 5 个业务模块的单元测试（JUnit5 + Mockito）。
#
# 背景：父 pom 全局 <skipTests>true</skipTests>，打包默认跳过测试。本脚本显式
# -DskipTests=false 覆盖，把已写的 639 个单测盘活成回归网。测试都是纯 Mockito
# 单测（不起 Spring / DB / Redis），秒级跑完，适合 fix-cycle 里高频回归。
#
# surefire 配了 <groups>${profiles.active}</groups>，默认 dev profile → 只跑
# @Tag("dev") 的测试（与打包/CI 行为一致）。
#
# 用法：
#   ./script/regression-test.sh              # 跑全部 5 个 djs 模块（默认）
#   ./script/regression-test.sh breed        # 只跑 breed 模块（fix 完该模块后回归）
#   ./script/regression-test.sh breed warehouse   # 跑指定多个模块
#
# 模块名取值：breed | common | plant | warehouse | store
#
# 退出码：测试有失败/错误 → 非 0（回归网逮到东西时 CI/本地能感知）。
#
set -euo pipefail

# 切到后端工程根（脚本在 <root>/script/ 下）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

ALL_MODULES=(breed common plant warehouse store)

# 解析参数：无参 = 全部模块；有参 = 指定模块（校验合法性）
if [ "$#" -eq 0 ]; then
  MODULES=("${ALL_MODULES[@]}")
else
  MODULES=()
  for m in "$@"; do
    found=0
    for valid in "${ALL_MODULES[@]}"; do
      [ "$m" = "$valid" ] && found=1 && break
    done
    if [ "$found" -eq 0 ]; then
      echo "ERROR: 未知模块 '$m'。合法取值：${ALL_MODULES[*]}" >&2
      exit 2
    fi
    MODULES+=("$m")
  done
fi

# 拼 -pl 多模块参数：ruoyi-modules/ruoyi-djs-<m>,...
PL_ARG=""
for m in "${MODULES[@]}"; do
  [ -n "${PL_ARG}" ] && PL_ARG="${PL_ARG},"
  PL_ARG="${PL_ARG}ruoyi-modules/ruoyi-djs-${m}"
done

echo "==> djs 回归测试：模块 [${MODULES[*]}]"
echo "==> mvn -pl ${PL_ARG} -am test -DskipTests=false"
echo ""

# -am：连带编译依赖模块（djs-common 等）。failure.ignore=false → 失败即红。
mvn -pl "${PL_ARG}" -am test \
  -DskipTests=false \
  -Dmaven.test.failure.ignore=false

echo ""
echo "==> 回归通过（绿）。"

# ───────────────────────────────────────────────────────────────────────────
# 已知缺口（交付后修，非阻塞）：
#   以下 2 个测试类漏标 @Tag("dev")，在默认 dev profile 下不会被 surefire 跑到，
#   游离在本回归网外。补一行 @Tag("dev") 即可纳入：
#     - ruoyi-djs-breed:     org.dromara.djs.breed.med.api.MedicineSupplierDealProviderTest
#     - ruoyi-djs-warehouse: org.dromara.djs.warehouse.flow.api.StockFlowSupplierDealProviderTest
#   临时单独验（绕过 tag 过滤，跑全部 tag）：
#     mvn -pl ruoyi-modules/ruoyi-djs-breed test -DskipTests=false \
#         -Dtest=MedicineSupplierDealProviderTest -Dgroups='dev,local' \
#         -Dsurefire.failIfNoSpecifiedTests=false
#
# CI 接入草案（不要改 deploy-staging.yml / deploy-prod.yml 的触发逻辑）：
#   建独立 workflow .github/workflows/test.yml，与部署解耦、不阻断部署：
#     name: Backend Unit Tests
#     on:
#       push:        # 任意分支 push 都跑，仅作信号，失败不挡部署
#       pull_request:
#     jobs:
#       test:
#         runs-on: ubuntu-latest
#         continue-on-error: true   # 关键：测试红也不卡 CI / 不阻断 deploy workflow
#         steps:
#           - uses: actions/checkout@v4
#           - uses: actions/setup-java@v4
#             with: { distribution: temurin, java-version: '21', cache: maven }
#           - run: cd code/main/RuoYi-Vue-Plus && bash script/regression-test.sh
#   想让测试成为硬门槛（红就挡）→ 去掉 continue-on-error，但那是交付后的决策，
#   现阶段保持"只跑不阻断"，避免影响 M5 staging 部署节奏。
# ───────────────────────────────────────────────────────────────────────────
