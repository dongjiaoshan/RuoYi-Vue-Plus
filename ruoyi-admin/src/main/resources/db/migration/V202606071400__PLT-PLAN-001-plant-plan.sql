-- ============================================================
-- PLT-PLAN-001 种植计划 3 步向导
--
-- 1. t_plant_plant_plan 主表（doc/11 §1.7）
-- 2. t_plant_plant_details 明细表（doc/11 §1.8，30 字段含 4 时间字段）
-- 3. 字典 djs_plant_plan_status / djs_pick_status / djs_plant_period
-- 4. sys_menu seed 8070-8080
--
-- xlsx 笔误清理：
--   earlisest_harvestdate → earliest_harvestdate
--   plant_money → plant_month
--   plant_date(明细表 R10) → plant_period (CHAR(2) 05/15/25)
--
-- baseline 占位 schema 大量偏离 doc/11 权威，0 行业务数据，安全 DROP + CREATE
-- （参 D8 _open-issues #13 模式）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_plant_plant_plan 主表（doc/11 §1.7）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_plant_plan;
CREATE TABLE t_plant_plant_plan (
    id                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 snowflake',
    tenant_id               VARCHAR(20)  NOT NULL DEFAULT '1001'  COMMENT '租户编号',
    plan_no                 VARCHAR(32)  NOT NULL                 COMMENT '业务码 PLAN-yyyy-NNN',
    plan_year               INT          NOT NULL                 COMMENT '计划年份 例 2026',
    crop_id                 BIGINT       NOT NULL                 COMMENT 'FK → t_plant_crop_info.id',
    plant_date              VARCHAR(32)  NULL                     COMMENT '计划种植时间（自由文本 例"4月上旬"）',
    plan_season             VARCHAR(16)  NOT NULL                 COMMENT 'djs_planting_season: spring/summer/autumn/winter',
    earliest_harvestdate    DATE         NULL                     COMMENT '由明细表聚合 MIN(details.earliest)',
    last_harvestdate        DATE         NULL                     COMMENT '由明细表聚合 MAX(details.last)',
    total_area              DECIMAL(10,2) NULL                    COMMENT '亩，SUM(details.plot_area)',
    total_plot              INT          NULL                     COMMENT '地块数 COUNT(details)',
    plant_status            VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'djs_plant_plan_status',
    create_dept             BIGINT       NULL                     COMMENT '创建部门',
    create_by               BIGINT       NULL                     COMMENT '创建者',
    create_time             DATETIME     NULL                     COMMENT '创建时间',
    update_by               BIGINT       NULL                     COMMENT '更新者',
    update_time             DATETIME     NULL                     COMMENT '更新时间',
    remark                  VARCHAR(500) NULL                     COMMENT '备注',
    del_flag                CHAR(1)      DEFAULT '0'              COMMENT '软删 0=正常 1=已删',
    del_unique              BIGINT       NOT NULL DEFAULT 0       COMMENT '软删唯一辅助列（活动行=0；软删行=id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_no (tenant_id, plan_no, del_unique),
    KEY idx_year_season (tenant_id, plan_year, plan_season),
    KEY idx_crop (tenant_id, crop_id),
    KEY idx_status (tenant_id, plant_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植计划主表（doc/11 §1.7）';

-- ------------------------------------------------------------
-- 2. t_plant_plant_details 明细（doc/11 §1.8，30 字段）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_plant_details;
CREATE TABLE t_plant_plant_details (
    id                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 snowflake',
    tenant_id               VARCHAR(20)  NOT NULL DEFAULT '1001'  COMMENT '租户编号',
    plant_id                BIGINT       NOT NULL                 COMMENT 'FK → t_plant_plant_plan.id',
    plot_id                 BIGINT       NOT NULL                 COMMENT 'FK → t_plant_plot_info.id',
    crop_id                 BIGINT       NOT NULL                 COMMENT 'FK → t_plant_crop_info.id',
    plant_month             TINYINT      NOT NULL                 COMMENT '计划月份 1-12（xlsx plant_money typo 清理）',
    plant_period            CHAR(2)      NOT NULL                 COMMENT '计划阶段 djs_plant_period: 05=上旬 / 15=中旬 / 25=下旬',
    begin_actualdate        DATE         NULL                     COMMENT '实际开始种植日期（工人录入触发）',
    end_actualdate          DATE         NULL                     COMMENT '实际结束种植日期',
    begin_harvestdate       DATE         NULL                     COMMENT '实际开始采摘日期',
    end_harvestdate         DATE         NULL                     COMMENT '实际结束采摘日期',
    earliest_harvestdate    DATE         NOT NULL                 COMMENT '计划最早采摘日期（plant_start + crop.min_cycle）',
    last_harvestdate        DATE         NOT NULL                 COMMENT '计划最晚采摘日期（plant_start + crop.max_cycle）',
    plant_status            VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'djs_plant_plan_status',
    harvest_status          VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'djs_pick_status',
    plot_area               DECIMAL(10,2) NOT NULL                COMMENT '地块面积（冗余 from plot.plot_area）',
    expected_yield          DECIMAL(12,3) NULL                    COMMENT '预计产量 = area × crop.predicted_per',
    loss_yield              DECIMAL(12,3) NULL                    COMMENT '预计损失产量（灾害填写）',
    actual_yield            DECIMAL(12,3) NULL                    COMMENT '实际产量（采摘累计，CROSS-FLOW-002 触发）',
    average_yield           DECIMAL(12,3) NULL                    COMMENT '平均亩产 = actual / area',
    plant_by                BIGINT       NULL                     COMMENT '种植班组 FK → t_plant_work_team.id',
    harvest_by              BIGINT       NULL                     COMMENT '采摘班组 FK → t_plant_work_team.id',
    is_pick                 TINYINT      NOT NULL DEFAULT 2       COMMENT '是否游客采摘 djs_yes_no（1=是 / 2=否）',
    create_dept             BIGINT       NULL                     COMMENT '创建部门',
    create_by               BIGINT       NULL                     COMMENT '创建者',
    create_time             DATETIME     NULL                     COMMENT '创建时间',
    update_by               BIGINT       NULL                     COMMENT '更新者',
    update_time             DATETIME     NULL                     COMMENT '更新时间',
    remark                  VARCHAR(500) NULL                     COMMENT '备注',
    del_flag                CHAR(1)      DEFAULT '0'              COMMENT '软删 0=正常 1=已删',
    del_unique              BIGINT       NOT NULL DEFAULT 0       COMMENT '软删唯一辅助列',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_plot_month (tenant_id, plant_id, plot_id, plant_month, plant_period, del_unique),
    KEY idx_plan (tenant_id, plant_id),
    KEY idx_plot (tenant_id, plot_id),
    KEY idx_crop (tenant_id, crop_id),
    KEY idx_status (tenant_id, plant_status),
    KEY idx_harvest_status (tenant_id, harvest_status),
    KEY idx_is_pick (tenant_id, is_pick)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植计划明细（doc/11 §1.8 30 字段）';

-- ------------------------------------------------------------
-- 3. 字典 seed
--    djs_plant_plan_status 补 seed（PLT-MD-001 假设已 seed 但事实未 seed）
--    djs_pick_status 新建
--    djs_plant_period 新建（上中下旬）
-- ------------------------------------------------------------

INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (102500, '1001', '计划阶段（旬）', 'djs_plant_period',      1, NOW(), 'PLT-PLAN-001 doc/11 §1.8 R10'),
    (102510, '1001', '采摘状态',       'djs_pick_status',       1, NOW(), 'PLT-PLAN-001 doc/11 §1.8 R18'),
    (102520, '1001', '种植计划状态',   'djs_plant_plan_status', 1, NOW(), 'PLT-PLAN-001 doc/11 §1.7 R13');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    -- djs_plant_period（5/15/25 业务约定，类型 CHAR(2)）
    (1025000, '1001', 0, '上旬', '05', 'djs_plant_period', '', 'info',    'Y', 1, NOW()),
    (1025001, '1001', 1, '中旬', '15', 'djs_plant_period', '', 'primary', 'N', 1, NOW()),
    (1025002, '1001', 2, '下旬', '25', 'djs_plant_period', '', 'success', 'N', 1, NOW()),

    -- djs_pick_status
    (1025100, '1001', 0, '待开始', 'pending',   'djs_pick_status', '', 'info',    'Y', 1, NOW()),
    (1025101, '1001', 1, '采摘中', 'picking',   'djs_pick_status', '', 'warning', 'N', 1, NOW()),
    (1025102, '1001', 2, '已完成', 'completed', 'djs_pick_status', '', 'success', 'N', 1, NOW()),
    (1025103, '1001', 3, '延期',   'delayed',   'djs_pick_status', '', 'danger',  'N', 1, NOW()),

    -- djs_plant_plan_status
    (1025200, '1001', 0, '待开始', 'pending',   'djs_plant_plan_status', '', 'info',    'Y', 1, NOW()),
    (1025201, '1001', 1, '执行中', 'ongoing',   'djs_plant_plan_status', '', 'primary', 'N', 1, NOW()),
    (1025202, '1001', 2, '已完成', 'completed', 'djs_plant_plan_status', '', 'success', 'N', 1, NOW()),
    (1025203, '1001', 3, '延期',   'delayed',   'djs_plant_plan_status', '', 'danger',  'N', 1, NOW());

-- ------------------------------------------------------------
-- 4. sys_menu seed 8070-8080（种植计划段）
--    component 路径对齐 plus-ui/src/views/djs-plant/plan/{list,wizard,detail}.vue
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark
)
VALUES
    -- 8070 计划列表 + 5 按钮
    (8070, '种植计划',     8000, 6, 'plan',         'djs-plant/plan/index',  '', 1, 0, 'C', '0', '0', 'djs:plant:plan:list',    'date',  1, NOW(), 'PLT-PLAN-001'),
    (8071, '计划查询',     8070, 1, '',             '',                       '', 1, 0, 'F', '0', '0', 'djs:plant:plan:list',    '#',     1, NOW(), ''),
    (8072, '计划新增',     8070, 2, '',             '',                       '', 1, 0, 'F', '0', '0', 'djs:plant:plan:add',     '#',     1, NOW(), ''),
    (8073, '计划编辑',     8070, 3, '',             '',                       '', 1, 0, 'F', '0', '0', 'djs:plant:plan:edit',    '#',     1, NOW(), ''),
    (8074, '计划删除',     8070, 4, '',             '',                       '', 1, 0, 'F', '0', '0', 'djs:plant:plan:remove',  '#',     1, NOW(), ''),
    (8077, '计划导出',     8070, 5, '',             '',                       '', 1, 0, 'F', '0', '0', 'djs:plant:plan:export',  '#',     1, NOW(), ''),

    -- 8075 计划向导（独立入口，不依赖列表）
    (8075, '计划新建向导', 8000, 7, 'plan/wizard',  'djs-plant/plan/wizard', '', 1, 0, 'C', '0', '0', 'djs:plant:plan:wizard',  'guide', 1, NOW(), 'PLT-PLAN-001'),
    (8076, '向导操作',     8075, 1, '',             '',                       '', 1, 0, 'F', '0', '0', 'djs:plant:plan:wizard',  '#',     1, NOW(), ''),

    -- 8078 计划详情（隐藏菜单，路由从列表 / 向导跳转）
    (8078, '计划详情',     8000, 8, 'plan/detail',  'djs-plant/plan/detail', '', 0, 0, 'C', '1', '0', 'djs:plant:plan:detail',  'view',  1, NOW(), 'PLT-PLAN-001 隐藏菜单'),
    (8079, '详情查看',     8078, 1, '',             '',                       '', 1, 0, 'F', '0', '0', 'djs:plant:plan:detail',  '#',     1, NOW(), ''),
    (8080, '甘特图查看',   8078, 2, '',             '',                       '', 1, 0, 'F', '0', '0', 'djs:plant:plan:ganttView','#',    1, NOW(), '');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id BETWEEN 8070 AND 8080;
