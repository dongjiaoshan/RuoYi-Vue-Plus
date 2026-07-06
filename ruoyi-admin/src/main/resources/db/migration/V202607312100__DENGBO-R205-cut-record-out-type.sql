-- ============================================================
-- DENGBO-R205 白条领用表：t_warehouse_pig_cut_record 加出库类型 out_type + 字典
-- ============================================================
-- 邓博 2026-07-05（admin 测试表 row205）：本表定位从「分割工序记录表」改为「白条领用表」。
--   白条领用页 3 个出库位置（分割车间 cut / 发货月台 ship / 仓库出库 warehouse）确认出库时都写一条记录，
--   统一记 drip_loss(滴水损失) + acid_remove_minutes(排酸时长=领用出库−入库) + out_type + (发货月台)门店/需求。
--   仅 out_type='cut' 后续进分割 + 计入分割白条数/总重/率与 trace 领用时点；ship/warehouse 领用即终态
--   (cut_status='done')、不计入分割口径（统计层已加 out_type='cut' 过滤）。
-- DEFAULT 'cut'：现有记录（全为分割领用）ADD COLUMN 时自动归 cut，无需单独回填 out_type。
-- 幂等 guard（INFORMATION_SCHEMA 守卫 + INSERT IGNORE）：可先手动 apply staging 做 live 验证，Flyway 部署重跑安全。
-- version 202607312100 > 当前 flyway max V202607312000。跑后须 bash script/sql/djs/_post-init.sh flush redis 字典缓存。
-- ============================================================
SET NAMES utf8mb4;
SET @db := DATABASE();

-- 1. cut_record.out_type 列（幂等）
SET @has_ot := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_warehouse_pig_cut_record' AND COLUMN_NAME='out_type');
SET @sql := IF(@has_ot=0,
  "ALTER TABLE t_warehouse_pig_cut_record ADD COLUMN out_type VARCHAR(16) NULL DEFAULT 'cut' COMMENT '出库类型 cut=分割车间/ship=发货月台/warehouse=仓库出库（白条领用表）' AFTER cut_status",
  "DO 0");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. 出库类型字典 djs_pig_cut_out_type（admin 白条领用记录列表展示用）
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (1007200, '1001', '白条出库类型', 'djs_pig_cut_out_type', 1, NOW(), 'DENGBO-R205 白条领用表出库位置');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1007200, '1001', 1, '分割车间', 'cut',       'djs_pig_cut_out_type', '', 'primary', 'Y', 1, NOW(), ''),
    (1007201, '1001', 2, '发货月台', 'ship',      'djs_pig_cut_out_type', '', 'success', 'N', 1, NOW(), ''),
    (1007202, '1001', 3, '仓库出库', 'warehouse', 'djs_pig_cut_out_type', '', 'info',    'N', 1, NOW(), '');
