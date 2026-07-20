#!/usr/bin/env bash
#
# 猪肉追溯链 E2E 造数脚本（真实全流程：出栏 → 燎毛 → 分割 → 打包出库）
#
# 目的：造一条 ear_no 从出栏一路走到打包出库的真实数据，使 t_warehouse_bar_info
#       的 marketing_time / in_time / out_time 三个时间戳齐全；打包出库触发 pork
#       追溯码生成时，TraceServiceImpl.backfillEarNoEvents 按 ear_no 回填
#       marketing/singe/slaughter/acid 4 个上游事件 + in_stock 事件 = 5 事件。
#       （第 6 事件 ship 需再走门店发货确认，本脚本不含，见 runbook 末尾说明。）
#
# 前置（脚本不自动起服务，避免占端口）：
#   1. admin 后端已在 http://localhost:8080 运行（mvn spring-boot:run -pl ruoyi-admin）
#   2. dev-mysql / dev-redis docker 容器在跑
#   3. mp mock 登录已启用（djs.applet.auth-mock-enabled=true，dev 默认开）
#   4. 一头在养猪（t_farm_pig_info，current_status != END）的耳号 —— 见下方 EAR_NO，
#      跑前用 PIG_EAR_NO 环境变量覆盖成 dev 库真实在养猪耳号，或脚本自动挑一头。
#
# 用法：
#   ./seed-pork-trace-chain.sh                 # 用默认 BASE + 自动挑在养猪
#   BASE=http://localhost:8080 PIG_EAR_NO=010126050004 ./seed-pork-trace-chain.sh
#
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
ADMIN_CLIENT_ID="${ADMIN_CLIENT_ID:-e5cd7e4891bf95d1d19206ce24a7b32e}"   # sys_client: pc, grant=password
MP_CLIENT_ID="${MP_CLIENT_ID:-mp-applet-dongjiaoshan}"                    # sys_client: mp, grant=password
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin123}"     # ruoyi 默认；按 dev 库实际改
MP_USER="${MP_USER:-dev_warehouse_mgr}"  # mock 白名单内
MP_PASS="${MP_PASS:-admin123}"

# 主数据（按 dev 库实际改；下面是 2026-06-09 dev 库实查值）
FROZEN_LOCATION_ID="${FROZEN_LOCATION_ID:-100000000000930001}"           # 演示冷冻库 frozen
WHITE_BAR_PRODUCT_ID="${WHITE_BAR_PRODUCT_ID:-100000000000000001}"       # PROD-WHITE-BAR-01 白条·整只
PORK_PRODUCT_ID="${PORK_PRODUCT_ID:-100000000000000101}"                 # PROD-PIG-LEAN-01 猪肉·精瘦肉
OPERATOR_ID="${OPERATOR_ID:-9104}"                                       # dev_warehouse_mgr user_id
MARKET_DEST="${MARKET_DEST:-sale_out}"                                   # djs_market_dest（dev 库现有值）
NOW_TS="$(date '+%Y-%m-%d %H:%M:%S')"

# ----- 工具：jq 取字段；curl 带鉴权 -----
need() { command -v "$1" >/dev/null 2>&1 || { echo "缺少命令 $1，请先安装"; exit 1; }; }
need curl; need jq

post() { # post <token> <path> <json>
  curl -sS -X POST "${BASE}$2" \
    -H "Authorization: Bearer $1" \
    -H "Content-Type: application/json" \
    -H "clientid: ${MP_CLIENT_ID}" \
    -d "$3"
}
get() { curl -sS "${BASE}$2" -H "Authorization: Bearer $1" -H "clientid: ${MP_CLIENT_ID}"; }

assert_ok() { # assert_ok <resp> <step>
  local code; code="$(echo "$1" | jq -r '.code // empty')"
  if [[ "${code}" != "200" ]]; then
    echo "  ✗ [$2] 失败：$1"; exit 1
  fi
}

echo "================ 猪肉追溯链 E2E 造数 ================"
echo "BASE=${BASE}"

# ============ 0. 登录拿 token ============
echo "==> [0] 登录"
ADMIN_TOKEN="$(curl -sS -X POST "${BASE}/auth/login" -H "Content-Type: application/json" \
  -d "{\"clientId\":\"${ADMIN_CLIENT_ID}\",\"grantType\":\"password\",\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}" \
  | jq -r '.data.access_token // .data.token // empty')"
[[ -n "${ADMIN_TOKEN}" ]] || { echo "  ✗ admin 登录失败（检查 ADMIN_PASS / clientId / 后端是否起）"; exit 1; }
echo "  ✓ admin token 取到"

MP_TOKEN="$(curl -sS -X POST "${BASE}/applet/auth/login" -H "Content-Type: application/json" \
  -H "clientid: ${MP_CLIENT_ID}" \
  -d "{\"clientId\":\"${MP_CLIENT_ID}\",\"grantType\":\"password\",\"username\":\"${MP_USER}\",\"password\":\"${MP_PASS}\"}" \
  | jq -r '.data.access_token // .data.token // empty')"
[[ -n "${MP_TOKEN}" ]] || { echo "  ✗ mp mock 登录失败（检查 djs.applet.auth-mock-enabled / 白名单账号）"; exit 1; }
echo "  ✓ mp token 取到"

# 在养猪耳号（未指定则提示去 DB 挑）
if [[ -z "${PIG_EAR_NO:-}" ]]; then
  echo "  ⚠ 未指定 PIG_EAR_NO，请先在 dev-mysql 挑一头在养猪："
  echo "     docker exec dev-mysql mysql -uroot -proot ry-vue -e \"SELECT ear_no,current_status FROM t_farm_pig_info WHERE del_flag='0' AND current_status<>'END' LIMIT 5;\""
  echo "     然后 PIG_EAR_NO=<耳号> 重跑。"
  exit 1
fi
EAR_NO="${PIG_EAR_NO}"
echo "  使用在养猪耳号 EAR_NO=${EAR_NO}"

# ============ 1. 出栏（admin）→ 自动建白条（CROSS-FLOW-001 监听器）============
echo "==> [1] 出栏 slaughter（写 t_farm_pig_marketing + 监听器自动建白条 status=pending_singe，写 marketing_time）"
RESP="$(curl -sS -X POST "${BASE}/djs/breed/event/slaughter" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H "Content-Type: application/json" \
  -H "clientid: ${ADMIN_CLIENT_ID}" \
  -d "{\"earNo\":\"${EAR_NO}\",\"marketingDate\":\"${NOW_TS}\",\"outWeight\":110.5,\"outDest\":\"${MARKET_DEST}\",\"ossIds\":\"seed-mock-oss-1\"}")"
assert_ok "${RESP}" "出栏"
echo "  ✓ 出栏成功，等监听器(AFTER_COMMIT)建白条…"
sleep 1

# 取刚建的白条 id（按 ear_no + pending_singe，取最新）
BAR_ID="$(docker exec dev-mysql mysql -uroot -proot ry-vue -N -e \
  "SELECT id FROM t_warehouse_bar_info WHERE ear_no='${EAR_NO}' AND status='pending_singe' AND del_flag='0' ORDER BY id DESC LIMIT 1;" 2>/dev/null)"
[[ -n "${BAR_ID}" ]] || { echo "  ✗ 未找到自动创建的白条（监听器可能没触发，检查 admin 日志 [CROSS-FLOW-001]）"; exit 1; }
echo "  ✓ 白条已建 bar_info.id=${BAR_ID}"

# ============ 2. 燎毛（mp）→ 白条 in_stock，写 in_time + 建 product_inhouse ============
echo "==> [2] 燎毛 burn（白条 pending_singe→in_stock，写 in_time；建 white_bar product_inhouse）"
RESP="$(post "${MP_TOKEN}" "/applet/warehouse/pigBurn/submit" \
  "{\"barInfoId\":${BAR_ID},\"burnTime\":\"${NOW_TS}\",\"arriveWeight\":108.0,\"locationId\":${FROZEN_LOCATION_ID},\"operatorId\":${OPERATOR_ID},\"productTypeItems\":[{\"productId\":${WHITE_BAR_PRODUCT_ID},\"weight\":100.0}]}")"
assert_ok "${RESP}" "燎毛"
echo "  ✓ 燎毛入库成功"

# ============ 3. 分割 pickup（mp）→ 白条 in_stock→pending_cut ============
echo "==> [3] 分割 pickup（白条 in_stock→pending_cut，建 cut_record=picked）"
RESP="$(post "${MP_TOKEN}" "/applet/warehouse/pigCut/pickup" \
  "{\"barInfoId\":${BAR_ID},\"locationId\":${FROZEN_LOCATION_ID}}")"
assert_ok "${RESP}" "分割pickup"
CUT_RECORD_ID="$(echo "${RESP}" | jq -r '.data // empty')"
echo "  ✓ 领取分割任务 cut_record.id=${CUT_RECORD_ID}"

# ============ 4. 分割 cutOut（mp）→ 白条 pending_cut→cutting，建 pork 部位 inhouse ============
echo "==> [4] 分割 cutOut（白条→cutting，建 pork 部位 product_inhouse 带 ear_no）"
RESP="$(post "${MP_TOKEN}" "/applet/warehouse/pigCut/cutOut" \
  "{\"cutRecordId\":${CUT_RECORD_ID},\"locationId\":${FROZEN_LOCATION_ID},\"partItems\":[{\"cutPart\":\"lean\",\"productWeight\":40.0}]}")"
assert_ok "${RESP}" "分割cutOut"
echo "  ✓ 分割出肉成功（如 cutPart 字典值不符，按 djs_pig_cut_part 改）"

# ============ 5. 分割 cutDone（mp）→ 白条 cutting→cut_done，写 out_time + acid_remove_loss ============
echo "==> [5] 分割 cutDone（白条→cut_done，写 out_time + 排酸损耗；slaughter/acid 事件时间戳来源）"
RESP="$(post "${MP_TOKEN}" "/applet/warehouse/pigCut/cutDone" \
  "{\"cutRecordId\":${CUT_RECORD_ID},\"dripLoss\":2.0}")"
assert_ok "${RESP}" "分割cutDone"
echo "  ✓ 分割完成，白条三时间戳齐全（marketing/in/out）"

# ============ 6. 打包出库 whiteBarOut（mp）→ genCode 生成 pork 码 + backfill 4 上游 + in_stock ============
echo "==> [6] 找分割产的 pork inhouse 作出库来源（del_flag='0' = 未消费；消费后走软删 del_flag='1'）"
SRC_INHOUSE_ID="$(docker exec dev-mysql mysql -uroot -proot ry-vue -N -e \
  "SELECT id FROM t_warehouse_product_inhouse WHERE ear_no='${EAR_NO}' AND del_flag='0' AND cut_part IS NOT NULL ORDER BY id DESC LIMIT 1;" 2>/dev/null)"
[[ -n "${SRC_INHOUSE_ID}" ]] || { echo "  ✗ 未找到可出库的分割 inhouse（cut_part 非空且 del_flag='0'）"; exit 1; }
echo "  来源 inhouse.id=${SRC_INHOUSE_ID}"
echo "==> [6] 打包出库 whiteBarOut（触发 pork 追溯码 + 回填 6 事件中的前 5）"
RESP="$(post "${MP_TOKEN}" "/applet/warehouse/pack/whiteBarOut" \
  "{\"sourceInhouseId\":${SRC_INHOUSE_ID},\"productWeight\":38.0,\"storeId\":null}")"
assert_ok "${RESP}" "打包出库"
echo "  ✓ 打包出库成功，pork 追溯码已生成 + 回填"

# ============ 验证：扫码看事件 ============
echo "==> [验证] 该 ear_no 的 pork 追溯码事件"
docker exec dev-mysql mysql -uroot -proot ry-vue -e \
  "SELECT c.produce_code, GROUP_CONCAT(e.trace_content ORDER BY e.trace_time) AS events, COUNT(e.id) ev
     FROM t_warehouse_trace_code c LEFT JOIN t_warehouse_trace_event e ON e.produce_code=c.produce_code
    WHERE c.code_type='pork' AND c.pig_ear_no='${EAR_NO}' AND c.del_flag='0'
    GROUP BY c.produce_code;" 2>/dev/null

echo ""
echo "================ 完成 ================"
echo "期望 events 含 marketing,singe,slaughter,acid,in_stock（5 事件）。"
echo "第 6 事件 ship 需再走门店需求→发货单→confirmCheck（is_delivery_check=1），见 runbook §7。"
