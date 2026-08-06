-- ============================================================================
-- V6 需求和问题（测试环境）row36：已种植地块的后台修改 —— 种植记录「变更类型」
--
-- 甲方口径：后台可对「已种植」的地块明细做三类调整，每类在种植记录上留痕：
--   · 改回待种植            → change_type = 后台调整      （admin）
--   · 改种植日期(可含班组)  → change_type = 后台调整      （admin）
--   · 仅改种植班组          → change_type = 后台班组调整  （admin_team）
--   · 小程序录入（默认）    → change_type = 小程序操作    （mp）
--
-- 落在哪张表：t_plant_plant_details（种植计划明细）。
--   本系统「种植记录」= 该表 begin_actualdate IS NOT NULL 的行 —— mp 端
--   /djs/applet/plant/dashboard/planRecords、admin 地块详情·种植信息、作物详情·
--   种植记录 三处列表全部以此为数据源（见 AppletPlantManageDashboardMapper §端点6
--   注释「种植记录分页（实际已开始种植的明细）」）。
--   t_warehouse_planting_record 是采摘侧的仓库快照（plant_date/harvest_date/
--   harvest_weight，由采摘事件写入），与「种植开工」无关，且属 warehouse 模块，
--   plant 模块不能反向依赖，故不选它。
--
-- 存 key 不存中文：同表 plant_status / harvest_status / plant_period 全部存字典 code
-- （pending / completed / 05 …），本列跟随，走字典 djs_plant_change_type。
--
-- 存量行：ADD COLUMN NOT NULL DEFAULT 'mp'，MySQL 自动把历史行填成 'mp'（小程序操作），
--        与甲方「字段默认为：小程序操作」一致，无需额外 UPDATE。
--
-- 另：earliest_harvestdate / last_harvestdate 由 NOT NULL 放开为 NULL —— 甲方要求改回待种植时
--    「计划采摘日期调整为空」，NOT NULL 下会 1048 直接报错（详见本文件第 2 段）。
--
-- 幂等：MySQL 无 ADD COLUMN IF NOT EXISTS，按项目既有先例用 information_schema
--      存在性判断后再 ADD（见 V202608241300__PLT-CROP-GENUS-001）。
--
-- ⚠️ 跑完刷 Redis 字典缓存（新增字典不刷则 admin 下拉/标签空白）：
--      本地    bash script/sql/djs/_post-init.sh
--      staging bash ops/redis-flush-dict.sh staging --yes
-- ============================================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_plant_plant_details 补 change_type
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_plant_plant_details'
      AND COLUMN_NAME = 'change_type'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_plant_plant_details ADD COLUMN change_type VARCHAR(16) NOT NULL DEFAULT ''mp'' COMMENT ''变更类型（字典 djs_plant_change_type：mp=小程序操作 / admin=后台调整 / admin_team=后台班组调整）'' AFTER transplant_adjusted',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 计划采摘日期两列放开 NULL
--
--    甲方 row36 第 2 点：改回待种植时「计划采摘日期调整为空的状态」。
--    建表时 earliest_harvestdate / last_harvestdate 是 NOT NULL（当时只有「按计划旬别推算」
--    一条写入路径，必然有值），现在多了「回退成待种植 → 没有种植日期就没有采摘窗口」这条路径，
--    NOT NULL 下 UPDATE 会直接 1048 报错，必须放开。
--
--    读取侧全部已 NULL-safe，无需改代码：
--      · PlantPlanMapper.recalcAggregates 用 MIN/MAX（忽略 NULL）
--      · validateHarvestOverlap 显式 isNotNull 过滤
--      · AppletPlantManageDashboardMapper 用 YEAR(...)=? / 区间比较（NULL 不命中）
--      · AppletPickServiceImpl 聚合前 filter(Objects::nonNull)
--    写入侧仍强制有值：PlantPlanServiceImpl.buildDetail 在作物 min/max_cycle 缺失时抛异常，
--    不会因为放开 NULL 就静默插入空采摘窗口。
-- ------------------------------------------------------------
SET @nullable := (
    SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_plant_plant_details'
      AND COLUMN_NAME = 'earliest_harvestdate'
);
SET @ddl := IF(@nullable = 'NO',
    CONCAT('ALTER TABLE t_plant_plant_details ',
           'MODIFY COLUMN earliest_harvestdate DATE NULL COMMENT ''计划最早采摘日期（种植日期 + crop.min_cycle；回退待种植时置空）'', ',
           'MODIFY COLUMN last_harvestdate DATE NULL COMMENT ''计划最晚采摘日期（种植日期 + crop.max_cycle；回退待种植时置空）'''),
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3. 字典 djs_plant_change_type（ADR-0004 §2.1 命名 djs_<业务域>_<维度>）
-- ------------------------------------------------------------
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
SELECT 102540, '1001', '种植记录变更类型', 'djs_plant_change_type', 1, NOW(),
       '种植记录是谁改的（小程序操作/后台调整/后台班组调整），计划详情地块明细里看'
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT 1 FROM sys_dict_type
                   WHERE dict_type = 'djs_plant_change_type' LIMIT 1) x);

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
SELECT 1025400, '1001', 0, '小程序操作', 'mp', 'djs_plant_change_type', '', 'info', 'Y', 1, NOW(), 'V6-R36'
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT 1 FROM sys_dict_data
                   WHERE dict_type = 'djs_plant_change_type' AND tenant_id = '1001'
                     AND dict_value = 'mp' LIMIT 1) x);

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
SELECT 1025401, '1001', 1, '后台调整', 'admin', 'djs_plant_change_type', '', 'warning', 'N', 1, NOW(), 'V6-R36'
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT 1 FROM sys_dict_data
                   WHERE dict_type = 'djs_plant_change_type' AND tenant_id = '1001'
                     AND dict_value = 'admin' LIMIT 1) x);

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
SELECT 1025402, '1001', 2, '后台班组调整', 'admin_team', 'djs_plant_change_type', '', 'primary', 'N', 1, NOW(), 'V6-R36'
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT 1 FROM sys_dict_data
                   WHERE dict_type = 'djs_plant_change_type' AND tenant_id = '1001'
                     AND dict_value = 'admin_team' LIMIT 1) x);
