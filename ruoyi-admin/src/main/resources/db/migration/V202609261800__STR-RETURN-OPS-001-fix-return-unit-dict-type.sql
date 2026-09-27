-- ============================================================================
-- STR-RETURN-OPS-001 补漏：`djs_return_unit`（退回单位配置）缺 sys_dict_type 头行
--
-- 问题（独立 QA 2026-09-26 实测打穿，day-one 缺陷）：
--   V202609151010__STR-RETURN-OPS-001-return-ops.sql 只写了 `djs_return_unit` 的 46 条
--   sys_dict_data（值拷自「出库去向」），**没有写 sys_dict_type 头行**（该迁移第 30 行的注释
--   自己声明了 `dict_id 102609150（djs_return_unit）`，但全文只有 1 条 sys_dict_type INSERT，
--   且只建了 102609140 djs_store_return_type）。
--
--   后果是「半通」：
--     · `GET /system/dict/data/type/djs_return_unit` → 46 行正常（前端 useDict 走这个，下拉能出来）；
--     · `GET /system/dict/type/list?dictType=djs_return_unit` → total=0（admin「字典管理 → 字典类型」
--       列表页查的正是这个）→ **「退回单位配置」在界面上根本打不开**。
--   而迁移自己的注释（「由客户在 admin『字典管理 → 退回单位配置』自行增删改」）与后端
--   `StoreReturnServiceImpl#createUnitReturns` 的报错文案（「请先在 admin 字典管理 → 退回单位配置 里配置」）
--   都指向这个打不开的页面 —— 甲方无法自行维护退回单位，只能改库。
--
-- 为什么**不改** V202609151010：
--   它已在 staging/prod 的 flyway_schema_history 里 applied = 1，改文件会让校验和不匹配、
--   部署时 Flyway validate 直接失败（属运维动作）。故另出本条补建，幂等可重跑。
--
-- 取号：flyway_schema_history 当前 max（success=1）= 202609221930，本文件取 202609261800
--   （ADR-0008 §2.5：> max + 1h buffer，且不按「今天」大跨度跳未来）。
--   dict_id 102609150 取自 V202609151010 第 30 行注释里已声明的号，实查零占用。
--
-- 跑完刷 Redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================================
SET NAMES utf8mb4;

INSERT INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (102609150, '1001', '退回单位配置', 'djs_return_unit', NULL, NOW(),
   '门店退回操作「单位退回」的退回单位来源；值取自「出库去向」djs_stock_out_dest（V202609151010 已灌 46 条示例），之后由客户在本页自行增删改')
ON DUPLICATE KEY UPDATE
  dict_name = VALUES(dict_name),
  remark    = VALUES(remark);

-- 验收 query（迁移后）
--   SELECT dict_id, dict_name, dict_type FROM sys_dict_type WHERE dict_type = 'djs_return_unit';   -- 必须 1 行
--   GET /system/dict/type/list?dictType=djs_return_unit                                            -- total 必须 = 1
--   SELECT COUNT(*) FROM sys_dict_data WHERE dict_type = 'djs_return_unit';                        -- 46（数据本来就对）
