-- ============================================================
-- DENGBO-R4 采摘活动「采摘去向」（决策 A：采摘即入口、直写）
--
-- 邓博 6/29 流程性需求 + Kevin 2026-06-30 拍板方案 A：
-- 采摘活动录入时选去向（5 选 1），直接写仓库台账（复用毛菜处理入库/月台/损耗/饲喂写入），
-- 同一批量不再走毛菜处理 mp 页（毛菜处理侧对采摘去向已处理量去重，防双计）。
--
-- 改动 1：t_plant_plant_activity 由「crop+date+班组累计一行」改为「per-event 一行」，加 5 业务列。
--   - 加列：pick_weight / pick_dest / product_id / plot_id / recorder_id
--   - DROP 原 UNIQUE(uk_crop_date_team)（per-event 不再幂等累加）
--   - 存量行 pick_dest 留 NULL（视作历史销售），daily_weight 语义不变（本次采摘重量）
--     —— 报表 SUM(daily_weight) GROUP BY crop_id,activity_date 口径不受影响。
-- 改动 2：dict djs_pick_dest 5 值（采摘去向）。
--
-- ⚠️ 跑完需 flush redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================

SET NAMES utf8mb4;

-- ---- 改动 1：t_plant_plant_activity 加 5 业务列 ----
ALTER TABLE t_plant_plant_activity
    ADD COLUMN pick_weight  DECIMAL(12,3) NULL COMMENT '本次采摘重量（kg，per-event；与 daily_weight 同值，新列语义更明确）' AFTER daily_weight,
    ADD COLUMN pick_dest    VARCHAR(32)   NULL COMMENT '采摘去向（字典 djs_pick_dest：sale/veg_fresh/platform/loss/feed；NULL=历史销售）' AFTER pick_weight,
    ADD COLUMN product_id   BIGINT        NULL COMMENT '果蔬成品 product_id（= crop.related_product 解析，可空）' AFTER pick_dest,
    ADD COLUMN plot_id      BIGINT        NULL COMMENT '地块 id（非销售去向必填；销售去向为空，结算时平均分摊到活动地块）' AFTER product_id,
    ADD COLUMN recorder_id  BIGINT        NULL COMMENT '记录人 userId（仓库相关人员；替代旧记录班组 activity_by）' AFTER plot_id;

-- ---- 改动 1b：DROP 原 UNIQUE（per-event 不再按 crop+date+班组 幂等累加）----
-- 原约束 uk_crop_date_team (tenant_id, crop_id, activity_date, activity_by, del_unique)
-- 改 per-event 后允许同 crop+date+班组多行（不同去向/地块），故移除该唯一约束。
ALTER TABLE t_plant_plant_activity DROP INDEX uk_crop_date_team;

-- 补：去向 + 地块过滤的二级索引（行5 五列聚合 + 销售分摊扫描）
ALTER TABLE t_plant_plant_activity
    ADD KEY idx_crop_date_dest (tenant_id, crop_id, activity_date, pick_dest);

-- ---- 改动 2：dict djs_pick_dest（采摘去向，ADR-0004）----
DELETE FROM sys_dict_data WHERE dict_type = 'djs_pick_dest';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_pick_dest';

INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (1089000, '1001', '采摘去向', 'djs_pick_dest', 1, NOW(), 'DENGBO-R4 采摘活动采摘去向 5 值');

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1089001, '1001', 0, '销售',     'sale',     'djs_pick_dest', '', 'primary', 'Y', 1, NOW(), '不写仓库库存，只进产量分摊'),
    (1089002, '1001', 1, '毛菜保鲜室', 'veg_fresh', 'djs_pick_dest', '', 'success', 'N', 1, NOW(), '复用毛菜处理入库（L0006 + location_stock）'),
    (1089003, '1001', 2, '果蔬月台',   'platform',  'djs_pick_dest', '', 'success', 'N', 1, NOW(), '复用毛菜处理月台（send_platform）'),
    (1089004, '1001', 3, '损耗',      'loss',     'djs_pick_dest', '', 'warning', 'N', 1, NOW(), '写 loss_flow'),
    (1089005, '1001', 4, '饲料饲喂',  'feed',     'djs_pick_dest', '', 'info',    'N', 1, NOW(), '复用毛菜处理饲料台账');
