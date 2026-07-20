-- D04 closing — D02/D03 历史问题 runtime patch
-- 由 D4 BRD-MD-002 raise（_open-issues "sys_farm seed 1001 farm_status=1=停用"）触发
-- 源 DDL `V202605200900__SYS-INIT-001-create-business-tables-common.sql` 已同步修：
--   - sys_farm.farm_status DEFAULT 1 → DEFAULT 0
--   - comment '1=启用 0=停用' → '0=启用 1=停用'（与字典 djs_farm_status 对齐）
--   - seed 1001 行 farm_status 1 → 0
-- 本文件是运行库 patch（已运行的 dev DB 需要 UPDATE backfill）

-- ----------------------------------------------------------------------
-- 1) sys_farm 1001 主场状态 1=停用 → 0=启用（与 djs_farm_status 字典对齐）
-- ----------------------------------------------------------------------
UPDATE sys_farm
SET farm_status = 0
WHERE id = 1001 AND farm_status = 1;

-- ----------------------------------------------------------------------
-- 2) sys_farm 表 DEFAULT 1 → DEFAULT 0（运行库 ALTER）
-- ----------------------------------------------------------------------
ALTER TABLE sys_farm
  MODIFY COLUMN farm_status TINYINT NOT NULL DEFAULT 0
  COMMENT '农场状态（字典 djs_farm_status：0=启用 1=停用）';

-- 验证
-- SELECT id, farm_code, farm_name, farm_status FROM sys_farm WHERE id = 1001;
-- 期望：farm_status = 0
