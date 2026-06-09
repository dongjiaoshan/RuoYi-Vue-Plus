# 猪肉追溯链 E2E 造数 runbook

> 造一条真实全流程数据，让一头猪的 ear_no 从 **出栏 → 燎毛 → 分割 → 打包出库** 走完，
> 使 `t_warehouse_bar_info` 的 `marketing_time / in_time / out_time` 三时间戳齐全；
> 打包出库生成 pork 追溯码时自动回填 5 个事件（前端扫码即看到完整链路）。
> 这份 runbook 同时就是猪肉追溯链的 E2E 冒烟。
>
> 配套脚本：[`seed-pork-trace-chain.sh`](seed-pork-trace-chain.sh)（curl 全自动；需先起 admin + 改好账密/主数据 ID）。

---

## 为什么 demo 扫码只显 2 事件（问题根因）

pork 追溯码在**打包出库那一刻**才由 `TraceServiceImpl.genCode` 生成。生成时 `backfillEarNoEvents`
按 ear_no 查 `t_warehouse_bar_info`，用其 `marketing_time / in_time / out_time` 回填
`marketing / singe / slaughter / acid` 4 个上游事件——**时间戳为 NULL 的事件直接跳过**。

旧 demo 的 pork 码是直接 seed 的，对应 ear_no 在 bar_info 里要么没有行、要么三时间戳缺失，
所以 4 个上游事件全跳过，只剩打包出库当场写的 `in_stock` + 发货写的 `ship` = 最多 2 个。

修法不是改代码，是**造一条三时间戳齐全的真实数据**（本 runbook）。

---

## 事件 ← 时间戳来源映射（核实自 `TraceServiceImpl.backfillEarNoEvents`）

| 追溯事件 | 时间戳来源（bar_info 列） | 哪一步写入该列 |
|---|---|---|
| `marketing`（出栏） | `marketing_time` | **步骤 1 出栏** —— CROSS-FLOW-001 监听器建白条时写 |
| `singe`（燎毛） | `in_time` | **步骤 2 燎毛** —— 白条 →in_stock 时回填 |
| `slaughter`（屠宰/分割出库） | `out_time` | **步骤 5 分割 cutDone** |
| `acid`（排酸） | `out_time`（同 cutDone） | **步骤 5 分割 cutDone** |
| `in_stock`（入库） | 打包出库当场 `NOW()` | **步骤 6 打包出库** 实时写 |
| `ship`（发货） | 发货确认当场 `NOW()` | **步骤 7 发货确认**（可选，凑第 6 事件） |

> 结论：**要看到 marketing/singe/slaughter/acid 全部齐全，必须走完分割（cutDone 写 out_time）**，
> 然后出库**分割产的 pork 部位 inhouse**（不是燎毛的整只白条 inhouse）。只燎毛不分割就出库 → 缺 slaughter/acid。

---

## 造数前置检查清单（跑前确认 DB 里有）

| # | 需要 | dev 库现状（2026-06-09 实查） | 查/造 |
|---|---|---|---|
| 1 | 一头在养猪（`t_farm_pig_info` `current_status != 'END'`，带 ear_no） | HB 8 / PZ 3 / FM 2 等，充足 | `SELECT ear_no,current_status FROM t_farm_pig_info WHERE del_flag='0' AND current_status<>'END' LIMIT 5;` |
| 2 | 冻品库位（`t_warehouse_location_info` location_type='frozen'） | `100000000000930001` 演示冷冻库 / `2059244331633524737` 冷冻一号库 | 已有 |
| 3 | 白条产品类型（belong_type='white_bar'，product_id `PROD-WHITE-BAR-%`） | 01整只/02猪头/03猪蹄/04半只 | 已有 |
| 4 | 猪肉产品（belong_type='pork'，打包出库目标） | PROD-PIG-LEAN-01 等 5 个 | 已有 |
| 5 | 操作员 user_id（EmployeePicker） | dev_warehouse_mgr=9104 等 | 已有 |
| 6 | 出栏去向字典值（`djs_market_dest`） | dev 现有出栏记录用 `sale_out` / `slaughter` | 以字典/现有值为准 |
| 7 | 分割部位字典值（`djs_pig_cut_part`） | **字典 dict_data 未 seed**，cutPart 用现有数据值或先 seed | ⚠️ 见步骤 4 注意 |
| 8 | admin 后端起在 :8080 + mp mock 登录开（`djs.applet.auth-mock-enabled=true`） | dev 默认开 | 起服务（不要本脚本起，手动起） |

---

## 鉴权（两套 token）

- **admin token**（出栏步骤用，`/djs/breed/event/slaughter` 是 admin 端）：
  `POST /auth/login`，body `{clientId, grantType:"password", username, password}`，clientId 用
  `e5cd7e4891bf95d1d19206ce24a7b32e`（sys_client pc）。取 `data.access_token`。
- **mp token**（燎毛/分割/打包都是 `/applet/*` 端点）：
  `POST /applet/auth/login`，header `clientid: mp-applet-dongjiaoshan`，body 同上 username 用 mock 白名单内
  （`dev` / `admin` / `dev_boss` / `dev_warehouse_mgr`）。取 `data.access_token`。

---

## 步骤详解

### 步骤 1 · 出栏（admin token）
- **API**：`POST /djs/breed/event/slaughter`
- **入参**（SlaughterBo，必填 marketingDate/outWeight/outDest/ossIds；pigId 与 earNo 二选一）：
  ```json
  {"earNo":"<在养猪耳号>","marketingDate":"2026-06-09 10:00:00","outWeight":110.5,"outDest":"sale_out","ossIds":"seed-mock-oss-1"}
  ```
- **产物**：INSERT `t_farm_pig_marketing` + 猪只状态机 → END(end_reason=MARKET) + publish `PigMarketingEvent`。
  `PigMarketingEventListener`（AFTER_COMMIT）**自动 INSERT 白条**：`status='pending_singe'`，
  写 `ear_no` / `marketing_time`(出栏日期) / `marketing_weight`，`bar_id`=BAR{yyMMdd}{seq4}。
- **取白条 id**（监听器异步，sleep 1s 后）：
  ```sql
  SELECT id FROM t_warehouse_bar_info WHERE ear_no='<耳号>' AND status='pending_singe' AND del_flag='0' ORDER BY id DESC LIMIT 1;
  ```
  > 验证：admin 日志应有 `[CROSS-FLOW-001] bar_info 创建成功`。

### 步骤 2 · 燎毛（mp token）
- **API**：`POST /applet/warehouse/pigBurn/submit`
- **入参**（PigBurnRecordBo，必填 barInfoId/burnTime/locationId/operatorId/productTypeItems）：
  ```json
  {"barInfoId":<步骤1的id>,"burnTime":"2026-06-09 10:05:00","arriveWeight":108.0,"locationId":100000000000930001,"operatorId":9104,"productTypeItems":[{"productId":100000000000000001,"weight":100.0}]}
  ```
- **前置**：白条 status IN ('pending_singe','singing')。
- **产物**：白条 `status → in_stock`，回填 `in_weight` / `in_time` / `in_method=1`；
  for each productTypeItem INSERT `t_warehouse_product_inhouse`（带 ear_no）+ `t_warehouse_stock_flow`(IN, flow_type='slaughter_burn')。

### 步骤 3 · 分割 pickup（mp token）
- **API**：`POST /applet/warehouse/pigCut/pickup`
- **入参**（PigCutPickupBo，必填 barInfoId/locationId）：
  ```json
  {"barInfoId":<步骤1的id>,"locationId":100000000000930001}
  ```
- **前置**：白条 status='in_stock'。
- **产物**：白条 `status → pending_cut`，建 `cut_record`(cut_status='picked')。响应 `data` = cut_record.id。

### 步骤 4 · 分割 cutOut（mp token）
- **API**：`POST /applet/warehouse/pigCut/cutOut`
- **入参**（PigCutOutBo，必填 cutRecordId/locationId/partItems）：
  ```json
  {"cutRecordId":<步骤3返回>,"locationId":100000000000930001,"partItems":[{"cutPart":"lean","productWeight":40.0}]}
  ```
- **前置**：cut_record cut_status IN ('picked','cutting')。
- **产物**：cut_record `picked → cutting`，白条 `pending_cut → cutting`；
  for each part INSERT **pork 部位** `t_warehouse_product_inhouse`（带 ear_no + cut_part）+ stock_flow IN。
- ⚠️ **cutPart 字典**：`djs_pig_cut_part` 的 dict_data 当前未 seed。若 cutOut 校验该字典会报错——
  跑前先 `SELECT DISTINCT cut_part FROM t_warehouse_product_inhouse WHERE cut_part IS NOT NULL;`
  看现有合法值，或先 seed 字典。本 runbook 暂用 `lean`，按实际改。

### 步骤 5 · 分割 cutDone（mp token）
- **API**：`POST /applet/warehouse/pigCut/cutDone`
- **入参**（PigCutDoneBo，必填 cutRecordId/dripLoss）：
  ```json
  {"cutRecordId":<步骤3返回>,"dripLoss":2.0}
  ```
- **前置**：cut_record cut_status='cutting'。
- **产物**：cut_record `cutting → done`，白条 `cutting → cut_done`，**写 `out_time` + `out_weight` + `acid_remove_loss`**。
  至此 bar_info `marketing_time / in_time / out_time` 三时间戳齐全（backfill 前提满足）。

### 步骤 6 · 打包出库 whiteBarOut（mp token）
- **找来源 inhouse**（分割产的 pork 部位，未消费 = del_flag='0'）：
  ```sql
  SELECT id FROM t_warehouse_product_inhouse
   WHERE ear_no='<耳号>' AND del_flag='0' AND cut_part IS NOT NULL ORDER BY id DESC LIMIT 1;
  ```
- **API**：`POST /applet/warehouse/pack/whiteBarOut`
- **入参**（WhiteBarOutBo，必填 sourceInhouseId/productWeight；storeId 可空）：
  ```json
  {"sourceInhouseId":<上面查到>,"productWeight":38.0,"storeId":null}
  ```
- **前置**：inhouse 存在且未消费（del_flag='0'），其产品 belong_type ∈ {white_bar, pork}。
- **产物**：INSERT `t_warehouse_product_production`（trace_code 字段写入）+ pack_in stock_flow + upsert location_stock，
  软删来源 inhouse；调 `genCode` 生成 pork 追溯码 → **backfill marketing/singe/slaughter/acid（用三时间戳）+ 写 in_stock 事件**。
  → 此时该 pork 码已串起 **5 个事件**。

### 验证（扫码看链路）
```sql
SELECT c.produce_code, GROUP_CONCAT(e.trace_content ORDER BY e.trace_time) AS events, COUNT(e.id) ev
  FROM t_warehouse_trace_code c
  LEFT JOIN t_warehouse_trace_event e ON e.produce_code=c.produce_code
 WHERE c.code_type='pork' AND c.pig_ear_no='<耳号>' AND c.del_flag='0'
 GROUP BY c.produce_code;
-- 期望 events = marketing,singe,slaughter,acid,in_stock（ev=5）
```
admin 端追溯页输入该 produce_code，应看到 5 个事件的完整链路（含真实时间戳）。

---

## 步骤 7（可选）· 发货确认 → 第 6 事件 ship

第 6 个事件 `ship` 不由打包出库产生，需走门店发货流，链较长：

1. 门店建需求（白条需求）→ `t_warehouse_demand_manage`（type=white_bar）
2. 需求关联发货单 → `t_warehouse_shipment`
3. 把步骤 6 产的 `product_production` 挂到该 demand（`demand_id`）并清点 `is_delivery_check=1`
4. 发货确认 → `ShipmentServiceImpl.confirmCheck` 触发 `ShipmentConfirmedEvent`
5. `ShipTraceEventListener`（AFTER_COMMIT）对 `demand_id` 下 `is_delivery_check=1 且 trace_code 非空` 的产品写 `ship` 事件

> ship 触发条件核实自 `ShipTraceEventListener.onShipmentConfirmed`：要求产品 `is_delivery_check=1` + traceCode 非空。
> 单纯打包出库的产品 `is_delivery_check` 默认 0，所以必须走完发货清点才会出 ship。
> 这一段依赖门店需求/发货主数据，建议先用 admin UI 跑通一次确认入参，再脚本化。**6 事件 = 前 5 + ship**。

---

## 一键脚本

```bash
cd code/main/RuoYi-Vue-Plus
# 1. 确认 admin 已起在 :8080（手动起，不要让脚本起，避免占端口）
# 2. 挑一头在养猪耳号
docker exec dev-mysql mysql -uroot -proot ry-vue -e \
  "SELECT ear_no,current_status FROM t_farm_pig_info WHERE del_flag='0' AND current_status<>'END' LIMIT 5;"
# 3. 跑（按 dev 库改账密/主数据 ID；脚本顶部变量都可用环境变量覆盖）
PIG_EAR_NO=<耳号> ADMIN_PASS=<admin密码> MP_PASS=<密码> ./script/seed-pork-trace-chain.sh
```

脚本会跑步骤 0-6 并在末尾打印该 pork 码的事件列表。**步骤 7 ship 不在脚本内**（需门店发货主数据）。

> 脚本未实跑验证（造数会写库 + 需 admin 起服务占 8080，按 dongjiaoshan 端口纪律未起）。
> 首次跑若某步 4xx，多半是：cutPart 字典值、账号密码、或主数据 ID 与 dev 库不符——按报错对照本 runbook 入参调整。
