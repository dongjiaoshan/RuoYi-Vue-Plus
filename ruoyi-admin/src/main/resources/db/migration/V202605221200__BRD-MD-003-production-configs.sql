-- ============================================================
-- BRD-MD-003 生产配置（3 tab）
--   tab1 生产周期 / tab2 精液公猪 / tab3 药品疫苗周期
--
-- 本文件内容：
--   1. 建表 t_farm_production_cycle_config / t_farm_boar_config / t_farm_med_schedule_config
--      （SYS-INIT-001 V202605200901 未建过这 3 张表，所以本文件首次创建）
--   2. seed 字典 djs_med_event_trigger（药品周期触发时机枚举）
--      （djs_med_type 已在 BRD-MED-001 灌过，本文件不重复）
--   3. seed 6 个生产周期业内默认值（gestation/lactation/nursery/fattening/oestrus_cycle/weaning_to_breeding）
--   4. 菜单：父 7000 (养殖) 下 3 个二级目录
--      - 7050-7059 production-cycle 生产周期
--      - 7080-7089 production-boar  精液公猪
--      - 7090-7099 production-med   药品疫苗周期
--
-- 权限串：
--   - djs:breed:production-cycle:{list,add,edit,remove,export}
--   - djs:breed:production-boar:{list,add,edit,remove,export}
--   - djs:breed:production-med:{list,add,edit,remove,export}
--
-- v1.2 关键约束：无定时任务 / 无自动流转 —— 本配置只决定"建议时间"，
-- 状态转换 / 任务生成全靠 BRD-EVENT-* / BRD-CORE-001 状态机事件触发。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1.1 t_farm_production_cycle_config  生产周期配置（Tab1）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_farm_production_cycle_config` (
  `id`             BIGINT       NOT NULL                       COMMENT '主键（雪花）',
  `tenant_id`      VARCHAR(20)  NOT NULL DEFAULT '1001'        COMMENT '农场ID（多租户，V1 全 1001 / ADR-0001）',
  `config_key`     VARCHAR(64)  NOT NULL                       COMMENT '业务键（如 gestation_days）',
  `default_value`  INT          NOT NULL                       COMMENT '业内默认值（天，seed 灌入，admin 不可改）',
  `custom_value`   INT          NULL                           COMMENT '客户自定义值（天，admin 可改，null = 沿用 default）',
  `unit`           VARCHAR(16)  NOT NULL DEFAULT '天'          COMMENT '单位',
  `description`    VARCHAR(255) NULL                           COMMENT '业务含义说明',
  `remark`         VARCHAR(500) NULL                           COMMENT '备注',
  `create_dept`    BIGINT       NULL                           COMMENT '创建部门',
  `create_by`      BIGINT       NULL                           COMMENT '创建者',
  `create_time`    DATETIME     NULL                           COMMENT '创建时间',
  `update_by`      BIGINT       NULL                           COMMENT '更新者',
  `update_time`    DATETIME     NULL                           COMMENT '更新时间',
  `del_flag`       CHAR(1)      NULL DEFAULT '0'               COMMENT '软删（0 未删 / 1 已删）',
  `del_unique`     BIGINT       NOT NULL DEFAULT 0             COMMENT '软删唯一性辅助（未删=0，已删=id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cycle_key`  (`tenant_id`, `config_key`, `del_unique`),
  KEY         `idx_tenant`    (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产周期配置（BRD-MD-003 Tab1）';

-- ------------------------------------------------------------
-- 1.2 t_farm_boar_config  精液 / 公猪配置（Tab2）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_farm_boar_config` (
  `id`                         BIGINT         NOT NULL                       COMMENT '主键（雪花）',
  `tenant_id`                  VARCHAR(20)    NOT NULL DEFAULT '1001'        COMMENT '农场ID（多租户，V1 全 1001）',
  `boar_id`                    BIGINT         NULL                           COMMENT '关联公猪 ID（V1 NULL = 通用配置 / V2 启用具体公猪覆盖）',
  `sperm_quality_threshold`    DECIMAL(8,2)   NOT NULL                       COMMENT '精液密度阈值（亿/mL）',
  `breeding_interval_days`     INT            NOT NULL                       COMMENT '同公猪两次采精最小间隔天数',
  `remark`                     VARCHAR(500)   NULL                           COMMENT '备注',
  `create_dept`                BIGINT         NULL                           COMMENT '创建部门',
  `create_by`                  BIGINT         NULL                           COMMENT '创建者',
  `create_time`                DATETIME       NULL                           COMMENT '创建时间',
  `update_by`                  BIGINT         NULL                           COMMENT '更新者',
  `update_time`                DATETIME       NULL                           COMMENT '更新时间',
  `del_flag`                   CHAR(1)        NULL DEFAULT '0'               COMMENT '软删',
  `del_unique`                 BIGINT         NOT NULL DEFAULT 0             COMMENT '软删唯一性辅助',
  PRIMARY KEY (`id`),
  -- boar_id NULL 时 MySQL UNIQUE 不会冲突（NULL != NULL），刚好满足 V1 "通用配置一条 + 后续可加针对具体公猪覆盖"
  UNIQUE KEY `uk_boar_id`     (`tenant_id`, `boar_id`, `del_unique`),
  KEY         `idx_tenant`    (`tenant_id`),
  KEY         `idx_boar_id`   (`boar_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='精液 / 公猪配置（BRD-MD-003 Tab2）';

-- ------------------------------------------------------------
-- 1.3 t_farm_med_schedule_config  药品 / 疫苗周期配置（Tab3）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_farm_med_schedule_config` (
  `id`              BIGINT       NOT NULL                       COMMENT '主键（雪花）',
  `tenant_id`       VARCHAR(20)  NOT NULL DEFAULT '1001'        COMMENT '农场ID',
  `med_type`        VARCHAR(32)  NOT NULL                       COMMENT '药品类型（字典 djs_med_type）',
  `event_trigger`   VARCHAR(64)  NOT NULL                       COMMENT '触发时机（字典 djs_med_event_trigger）',
  `days_offset`     INT          NOT NULL                       COMMENT '天数偏移（正 = 事件后 / 负 = 事件前）',
  `description`     VARCHAR(255) NULL                           COMMENT '业务含义说明',
  `remark`          VARCHAR(500) NULL                           COMMENT '备注',
  `create_dept`     BIGINT       NULL                           COMMENT '创建部门',
  `create_by`       BIGINT       NULL                           COMMENT '创建者',
  `create_time`     DATETIME     NULL                           COMMENT '创建时间',
  `update_by`       BIGINT       NULL                           COMMENT '更新者',
  `update_time`     DATETIME     NULL                           COMMENT '更新时间',
  `del_flag`        CHAR(1)      NULL DEFAULT '0'               COMMENT '软删',
  `del_unique`      BIGINT       NOT NULL DEFAULT 0             COMMENT '软删唯一性辅助',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_med_trigger` (`tenant_id`, `med_type`, `event_trigger`, `days_offset`, `del_unique`),
  KEY         `idx_tenant`    (`tenant_id`),
  KEY         `idx_trigger`   (`event_trigger`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品 / 疫苗周期配置（BRD-MD-003 Tab3）';

-- ============================================================
-- 2. 字典：djs_med_event_trigger（药品触发时机枚举，BRD-MED-002 自动建任务时查询用）
--    （djs_med_type 由 BRD-MED-001 V202605221100 灌过，本文件不重复）
-- ============================================================
INSERT IGNORE INTO sys_dict_type (
    dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100153, '1001', '药品触发时机', 'djs_med_event_trigger', 1, NOW(), '养殖：药品 / 疫苗周期触发的业务事件类型（BRD-MD-003 Tab3）');

INSERT IGNORE INTO sys_dict_data (
    dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001530, '1001', 0, '仔猪出生',  'birth',            'djs_med_event_trigger', '', 'primary', 'N', 1, NOW()),
  (1001531, '1001', 1, '断奶',      'weaning',          'djs_med_event_trigger', '', 'success', 'N', 1, NOW()),
  (1001532, '1001', 2, '转入育肥',  'fattening_start',  'djs_med_event_trigger', '', 'success', 'N', 1, NOW()),
  (1001533, '1001', 3, '配种',      'mating',           'djs_med_event_trigger', '', 'warning', 'N', 1, NOW()),
  (1001534, '1001', 4, '妊娠确认',  'pregnant',         'djs_med_event_trigger', '', 'warning', 'N', 1, NOW()),
  (1001535, '1001', 5, '分娩',      'farrow',           'djs_med_event_trigger', '', 'danger',  'N', 1, NOW()),
  (1001536, '1001', 6, '引种入栏',  'introduce',        'djs_med_event_trigger', '', 'info',    'N', 1, NOW());

-- ============================================================
-- 3. seed 6 个生产周期业内默认值（v1.1 / spawn prompt 明确数值）
--    INSERT 不显式赋 tenant_id —— 走 MetaObjectHandler.insertFill 自动填 '1001'
--    （但本 seed 在 ruoyi 启动前跑，handler 不生效 → 显式写 tenant_id）
-- ============================================================
INSERT IGNORE INTO t_farm_production_cycle_config (
    id, tenant_id, config_key, default_value, custom_value, unit, description, create_by, create_time, del_flag, del_unique)
VALUES
  (1, '1001', 'gestation_days',           114, NULL, '天', '妊娠天数（母猪配种到分娩的标准周期）',         1, NOW(), '0', 0),
  (2, '1001', 'lactation_days',            28, NULL, '天', '哺乳天数（仔猪从出生到断奶的标准时长）',       1, NOW(), '0', 0),
  (3, '1001', 'nursery_days',              35, NULL, '天', '保育天数（仔猪从断奶到转入育肥的标准时长）',   1, NOW(), '0', 0),
  (4, '1001', 'fattening_days',           120, NULL, '天', '育肥天数（保育结束到达出栏的标准时长）',       1, NOW(), '0', 0),
  (5, '1001', 'oestrus_cycle_days',        21, NULL, '天', '发情周期（母猪一个发情周期的标准时长）',       1, NOW(), '0', 0),
  (6, '1001', 'weaning_to_breeding_days',   7, NULL, '天', '断奶到配种（母猪断奶后到下次配种的建议时长）', 1, NOW(), '0', 0);

-- ============================================================
-- 4. 菜单：3 个二级目录（挂养殖目录 7000 下）+ 各自按钮权限
-- ============================================================

-- ----------------------------------------------------
-- 4.1 二级目录：生产周期 / 精液公猪 / 药品周期（各一个）
-- ----------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 生产周期（Tab1 入口，但物理上单页 3 tab 在 production-config/index.vue 内）
  (7050, '生产配置', 7000, 5, 'production-config', 'djs-breed/production-config/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:production-cycle:list', 'time', 1, NOW(), 'BRD-MD-003 生产配置主入口（3 tab 单页）');

-- ----------------------------------------------------
-- 4.2 生产周期 按钮权限（5 个，挂在生产配置主菜单 7050 下）
-- ----------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7051, '生产周期查询', 7050, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-cycle:list',   '#', 1, NOW(), 'BRD-MD-003 Tab1'),
  (7052, '生产周期新增', 7050, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-cycle:add',    '#', 1, NOW(), 'BRD-MD-003 Tab1'),
  (7053, '生产周期修改', 7050, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-cycle:edit',   '#', 1, NOW(), 'BRD-MD-003 Tab1'),
  (7054, '生产周期删除', 7050, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-cycle:remove', '#', 1, NOW(), 'BRD-MD-003 Tab1'),
  (7055, '生产周期导出', 7050, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-cycle:export', '#', 1, NOW(), 'BRD-MD-003 Tab1');

-- ----------------------------------------------------
-- 4.3 精液 / 公猪配置 按钮权限（5 个，挂在 7050 下）
-- ----------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7080, '公猪配置查询', 7050, 6, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-boar:list',   '#', 1, NOW(), 'BRD-MD-003 Tab2'),
  (7081, '公猪配置新增', 7050, 7, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-boar:add',    '#', 1, NOW(), 'BRD-MD-003 Tab2'),
  (7082, '公猪配置修改', 7050, 8, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-boar:edit',   '#', 1, NOW(), 'BRD-MD-003 Tab2'),
  (7083, '公猪配置删除', 7050, 9, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-boar:remove', '#', 1, NOW(), 'BRD-MD-003 Tab2'),
  (7084, '公猪配置导出', 7050, 10, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-boar:export', '#', 1, NOW(), 'BRD-MD-003 Tab2');

-- ----------------------------------------------------
-- 4.4 药品 / 疫苗周期 按钮权限（5 个，挂在 7050 下）
-- ----------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7090, '药品周期查询', 7050, 11, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-med:list',   '#', 1, NOW(), 'BRD-MD-003 Tab3'),
  (7091, '药品周期新增', 7050, 12, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-med:add',    '#', 1, NOW(), 'BRD-MD-003 Tab3'),
  (7092, '药品周期修改', 7050, 13, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-med:edit',   '#', 1, NOW(), 'BRD-MD-003 Tab3'),
  (7093, '药品周期删除', 7050, 14, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-med:remove', '#', 1, NOW(), 'BRD-MD-003 Tab3'),
  (7094, '药品周期导出', 7050, 15, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-med:export', '#', 1, NOW(), 'BRD-MD-003 Tab3');

-- ============================================================
-- 5. 角色 → 菜单：boss(102) / manager(103) / breed_admin(104) 给本 ticket 菜单可见
--    （SYS-AUTH-001 的一次性 SELECT 已建过，但只在那次扫的范围里；新菜单要追加）
-- ============================================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 102, menu_id FROM sys_menu WHERE menu_id IN (7050,7051,7052,7053,7054,7055,7080,7081,7082,7083,7084,7090,7091,7092,7093,7094);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 103, menu_id FROM sys_menu WHERE menu_id IN (7050,7051,7052,7053,7054,7055,7080,7081,7082,7083,7084,7090,7091,7092,7093,7094);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 104, menu_id FROM sys_menu WHERE menu_id IN (7050,7051,7052,7053,7054,7055,7080,7081,7082,7083,7084,7090,7091,7092,7093,7094);
