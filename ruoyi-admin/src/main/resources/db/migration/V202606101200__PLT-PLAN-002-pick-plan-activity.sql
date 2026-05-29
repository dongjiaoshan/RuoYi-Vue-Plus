-- ============================================================
-- PLT-PLAN-002 采摘计划与采摘活动
--
-- 1. t_plant_pick_activity 新表（采摘活动管理 — 游客体验场景，xlsx 无对应 sheet，doc/06 推断）
-- 2. 字典 djs_pick_activity_status 3 态（upcoming/ongoing/ended）
-- 3. sys_menu seed 8081-8089 共 9 行（admin 计划列表/调整/活动 CRUD + mp 任务列表/详情）
--
-- 复用：t_plant_plant_details 4 时间字段 + is_pick flag（D9 PLT-PLAN-001 已落，不改 schema）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_plant_pick_activity 采摘活动管理
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_pick_activity;
CREATE TABLE t_plant_pick_activity (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 snowflake',
    tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001'  COMMENT '租户编号',
    activity_no     VARCHAR(32)  NOT NULL                 COMMENT '活动业务码 PA + yyyyMMdd + 3 位',
    activity_name   VARCHAR(128) NOT NULL                 COMMENT '活动名称',
    activity_date   DATE         NOT NULL                 COMMENT '活动日期',
    activity_status VARCHAR(16)  NOT NULL DEFAULT 'upcoming' COMMENT 'djs_pick_activity_status: upcoming/ongoing/ended',
    crop_id         BIGINT       NOT NULL                 COMMENT 'FK → t_plant_crop_info.id',
    crop_name       VARCHAR(64)  NOT NULL                 COMMENT '冗余 from crop_info.crop_name',
    total_plot      INT          NOT NULL DEFAULT 0       COMMENT '涉及地块数',
    total_yield     DECIMAL(12,3) NOT NULL DEFAULT 0      COMMENT '当日采摘汇总（结束后回填）',
    visitor_count   INT          NULL                     COMMENT '当日参与人数',
    create_dept     BIGINT       NULL                     COMMENT '创建部门',
    create_by       BIGINT       NULL                     COMMENT '创建者',
    create_time     DATETIME     NULL                     COMMENT '创建时间',
    update_by       BIGINT       NULL                     COMMENT '更新者',
    update_time     DATETIME     NULL                     COMMENT '更新时间',
    remark          VARCHAR(500) NULL                     COMMENT '备注',
    del_flag        CHAR(1)      DEFAULT '0'              COMMENT '软删 0=正常 1=已删',
    del_unique      BIGINT       NOT NULL DEFAULT 0       COMMENT '软删唯一辅助列（活动行=0；软删行=id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_no (tenant_id, activity_no, del_unique),
    KEY idx_date (tenant_id, activity_date),
    KEY idx_crop (tenant_id, crop_id, del_flag),
    KEY idx_status (tenant_id, activity_status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采摘活动管理（doc/06 PLT-PLAN-002 推断；游客体验场景）';

-- ------------------------------------------------------------
-- 2. 字典 djs_pick_activity_status 3 态
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (102530, '1001', '采摘活动状态', 'djs_pick_activity_status', 1, NOW(), 'PLT-PLAN-002 游客体验采摘活动状态');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1025300, '1001', 0, '即将开始', 'upcoming', 'djs_pick_activity_status', '', 'info',    'Y', 1, NOW()),
    (1025301, '1001', 1, '进行中',   'ongoing',  'djs_pick_activity_status', '', 'primary', 'N', 1, NOW()),
    (1025302, '1001', 2, '已结束',   'ended',    'djs_pick_activity_status', '', 'success', 'N', 1, NOW());

-- ------------------------------------------------------------
-- 3. sys_menu seed 8081-8089 共 9 行
--    8081 采摘计划父菜单 / 8082-8083 list+adjust / 8084-8087 activity CRUD / 8088-8089 mp 权限串
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark
)
VALUES
    -- 8081 采摘计划父菜单（admin 主菜单，下挂 list + adjust + activity 三页）
    (8081, '采摘计划',         8000, 9,  'pick',          '',                                 '', 1, 0, 'M', '0', '0', '',                                  'date',   1, NOW(), 'PLT-PLAN-002'),

    -- 8082 计划列表（按作物聚合）
    (8082, '采摘计划列表',     8081, 1,  'plan',          'djs-plant/pick/plan/index',        '', 1, 0, 'C', '0', '0', 'djs:plant:pick:list',               'list',   1, NOW(), 'PLT-PLAN-002 按作物聚合'),

    -- 8083 调整页（按地块调整 4 时间字段 + is_pick）
    (8083, '采摘计划调整',     8081, 2,  'adjust',        'djs-plant/pick/adjust/index',      '', 0, 0, 'C', '1', '0', 'djs:plant:pick:adjust',             'edit',   1, NOW(), 'PLT-PLAN-002 隐藏菜单，从列表跳入'),

    -- 8084-8087 采摘活动管理 CRUD
    (8084, '采摘活动',         8081, 3,  'activity',      'djs-plant/pick/activity/index',    '', 1, 0, 'C', '0', '0', 'djs:plant:pick:activity:list',      'tree',   1, NOW(), 'PLT-PLAN-002 游客采摘活动'),
    (8085, '活动新增',         8084, 1,  '',              '',                                 '', 1, 0, 'F', '0', '0', 'djs:plant:pick:activity:add',       '#',      1, NOW(), ''),
    (8086, '活动编辑',         8084, 2,  '',              '',                                 '', 1, 0, 'F', '0', '0', 'djs:plant:pick:activity:edit',      '#',      1, NOW(), ''),
    (8087, '活动删除',         8084, 3,  '',              '',                                 '', 1, 0, 'F', '0', '0', 'djs:plant:pick:activity:remove',    '#',      1, NOW(), ''),

    -- 8088-8089 mp 端权限串（无 admin route，纯权限挂点）
    (8088, 'mp 采摘任务列表',  8081, 4,  '',              '',                                 '', 1, 0, 'F', '0', '0', 'djs:applet:plant:pick:list',        '#',      1, NOW(), 'PLT-PLAN-002 mp 工人浏览'),
    (8089, 'mp 采摘任务详情',  8081, 5,  '',              '',                                 '', 1, 0, 'F', '0', '0', 'djs:applet:plant:pick:detail',      '#',      1, NOW(), 'PLT-PLAN-002 mp 详情查看');

-- 4. role_menu 白名单（不绑 super_admin=1 / common=101，业务岗位才绑）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id BETWEEN 8081 AND 8089;
