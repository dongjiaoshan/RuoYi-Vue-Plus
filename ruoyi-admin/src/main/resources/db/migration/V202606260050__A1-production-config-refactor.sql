-- ============================================================
-- A1 admin 生产配置重构（3 tab：母猪生产配置 / 育肥生产配置 / 出栏配置）
--
-- 非破坏性：
--   - 旧 6 周期 key（gestation/lactation/nursery/fattening/oestrus_cycle/weaning_to_breeding）
--     被 PigCoreServiceImpl.computeDueDateMap（mp 选猪预产期 / 到断奶期提示）消费 —— 保留不删
--   - 旧表 t_farm_boar_config / t_farm_med_schedule_config 及数据保留不删（仅前端去 tab）
--
-- 本文件内容：
--   1. 建表 t_farm_fatten_age_stage（育肥日龄阶段，Tab2）
--   2. seed 母猪生产配置 6 个新 key（sow_*，与旧 key 独立，新增不替换）
--   3. seed 出栏配置 key（slaughter_age_days）
--   4. seed 育肥日龄阶段示例数据（5 段连续日龄）
--   5. 菜单：生产配置主菜单 7050 下新增「育肥日龄阶段」按钮权限（7056-7059）
--
-- 权限串：djs:breed:fatten-stage:{list,edit}
-- 母猪 / 出栏配置复用现有 djs:breed:production-cycle:{list,edit}（不新增权限）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_farm_fatten_age_stage  育肥日龄阶段配置（Tab2）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_farm_fatten_age_stage` (
  `id`             BIGINT       NOT NULL                       COMMENT '主键（雪花）',
  `tenant_id`      VARCHAR(20)  NOT NULL DEFAULT '1001'        COMMENT '农场ID（多租户，V1 全 1001 / ADR-0001）',
  `start_age`      INT          NOT NULL                       COMMENT '起始日龄（天，含）',
  `end_age`        INT          NOT NULL                       COMMENT '截止日龄（天，含）',
  `record_growth`  TINYINT      NOT NULL DEFAULT 0             COMMENT '是否记录生长记录（1 记录 / 0 不记录）',
  `remark`         VARCHAR(500) NULL                           COMMENT '备注',
  `create_dept`    BIGINT       NULL                           COMMENT '创建部门',
  `create_by`      BIGINT       NULL                           COMMENT '创建者',
  `create_time`    DATETIME     NULL                           COMMENT '创建时间',
  `update_by`      BIGINT       NULL                           COMMENT '更新者',
  `update_time`    DATETIME     NULL                           COMMENT '更新时间',
  `del_flag`       CHAR(1)      NULL DEFAULT '0'               COMMENT '软删（0 未删 / 1 已删）',
  `del_unique`     BIGINT       NOT NULL DEFAULT 0             COMMENT '软删唯一性辅助（未删=0，已删=id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_start_age` (`tenant_id`, `start_age`, `del_unique`),
  KEY         `idx_tenant`  (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='育肥日龄阶段配置（A1 Tab2）';

-- ============================================================
-- 2. seed 母猪生产配置 6 个新 key（sow_*）
--    与旧 6 周期 key 独立（不替换、不删旧 key）；admin 表单改 custom_value
--    seed 阶段 ruoyi 未起，handler 不生效 → 显式写 tenant_id
-- ============================================================
INSERT IGNORE INTO t_farm_production_cycle_config (
    id, tenant_id, config_key, default_value, custom_value, unit, description, create_by, create_time, del_flag, del_unique)
VALUES
  (101, '1001', 'sow_wean_to_breed_days',     6, NULL, '天', '断奶到配种天数',   1, NOW(), '0', 0),
  (102, '1001', 'sow_return_to_breed_days',   5, NULL, '天', '返情到配种天数',   1, NOW(), '0', 0),
  (103, '1001', 'sow_empty_to_breed_days',    5, NULL, '天', '空怀到配种天数',   1, NOW(), '0', 0),
  (104, '1001', 'sow_abort_to_breed_days',    5, NULL, '天', '流产到配种天数',   1, NOW(), '0', 0),
  (105, '1001', 'sow_breed_to_farrow_days', 141, NULL, '天', '配种到分娩天数',   1, NOW(), '0', 0),
  (106, '1001', 'sow_farrow_to_wean_days',   25, NULL, '天', '分娩到断奶天数',   1, NOW(), '0', 0);

-- ============================================================
-- 3. seed 出栏配置 key（slaughter_age_days，默认 175）
-- ============================================================
INSERT IGNORE INTO t_farm_production_cycle_config (
    id, tenant_id, config_key, default_value, custom_value, unit, description, create_by, create_time, del_flag, del_unique)
VALUES
  (107, '1001', 'slaughter_age_days', 175, NULL, '天', '出栏日龄（育肥猪达到出栏的标准日龄）', 1, NOW(), '0', 0);

-- ============================================================
-- 4. seed 育肥日龄阶段示例数据（5 段连续日龄）
--    record_growth：默认全 1（每段记录生长记录），可在 admin 调整
-- ============================================================
INSERT IGNORE INTO t_farm_fatten_age_stage (
    id, tenant_id, start_age, end_age, record_growth, create_by, create_time, del_flag, del_unique)
VALUES
  (201, '1001',  26,  42, 1, 1, NOW(), '0', 0),
  (202, '1001',  43,  70, 1, 1, NOW(), '0', 0),
  (203, '1001',  71, 140, 1, 1, NOW(), '0', 0),
  (204, '1001', 141, 200, 1, 1, NOW(), '0', 0),
  (205, '1001', 201, 250, 1, 1, NOW(), '0', 0);

-- ============================================================
-- 5. 菜单：生产配置主菜单 7050 下新增「育肥日龄阶段」按钮权限
--    7056 查询 / 7057 保存（编辑）—— 复用 djs:breed:fatten-stage:{list,edit}
-- ============================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7056, '育肥阶段查询', 7050, 16, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:fatten-stage:list', '#', 1, NOW(), 'A1 育肥生产配置 Tab2'),
  (7057, '育肥阶段保存', 7050, 17, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:fatten-stage:edit', '#', 1, NOW(), 'A1 育肥生产配置 Tab2');

-- 角色 → 菜单：boss(102) / manager(103) / breed_admin(104) 可见新按钮
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 102, menu_id FROM sys_menu WHERE menu_id IN (7056, 7057);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 103, menu_id FROM sys_menu WHERE menu_id IN (7056, 7057);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 104, menu_id FROM sys_menu WHERE menu_id IN (7056, 7057);
