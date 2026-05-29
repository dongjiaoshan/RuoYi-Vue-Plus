-- ============================================================
-- PLT-WORK-001 农事记录（mp 12 类录入 + admin 只读列表）
--
-- 1. t_plant_farm_records 主表（doc/11 §1.9 完整 18 字段 + 特殊字段）
--    baseline 占位 schema 偏离权威（缺 record_no/plot_type/crop_name/transplant_plot/del_unique 等）
--    且 0 行业务数据 → DROP + CREATE 重建
-- 2. 字典 4 新建：djs_farm_work_type 12 / djs_tillage_type 4 / djs_tillage_way 3 / djs_disaster_type 5
--    baseline 已有 12+4+4+5 行旧 seed（key 与 doc/06 不一致：sow/till/... vs tillage_break/tillage_prepare/...）
--    → DELETE 旧 seed + INSERT 对齐 doc/06 §PLT-WORK-001 / doc/10 §F-PLT-06
-- 3. 菜单 8090-8096 + role 白名单（role_id NOT IN (1, 101) AND del_flag='0'）
-- 4. OSS bizType plant_farm_proof 通过 OssStsServiceImpl.ALLOWED_BIZ_TYPES Java 同步（本 SQL 不涉及）
--
-- 跑完必须刷 Redis 字典缓存（详 .claude/CLAUDE.md §5）：
--   bash code/main/RuoYi-Vue-Plus/script/sql/djs/_post-init.sh
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_plant_farm_records（doc/11 §1.9 / doc/10 §F-PLT-06）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_farm_records;
CREATE TABLE t_plant_farm_records (
    id                  BIGINT       NOT NULL                 COMMENT '主键 snowflake',
    tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001'  COMMENT '租户编号',
    record_no           VARCHAR(32)  NOT NULL                 COMMENT '业务码 FR+yyyyMMdd+4 位序号（inline mapper.selectMaxByDate）',
    plant_id            BIGINT       NULL                     COMMENT 'FK → t_plant_plant_plan.id；空地处理时为空',
    plot_id             BIGINT       NOT NULL                 COMMENT 'FK → t_plant_plot_info.id',
    plot_type           TINYINT      NULL                     COMMENT 'djs_plot_status 1=空闲 / 2=种植 / 3=采摘（冗余下单时地块状态）',
    crop_id             BIGINT       NULL                     COMMENT 'FK → t_plant_crop_info.id；空地时为空',
    crop_name           VARCHAR(64)  NULL                     COMMENT '冗余作物名',
    farm_type           VARCHAR(32)  NOT NULL                 COMMENT 'djs_farm_work_type 12 类',
    farm_date           DATE         NOT NULL                 COMMENT '农事日期',
    farm_by             BIGINT       NOT NULL                 COMMENT 'FK → t_plant_work_team.id 处理班组',
    -- 整地专用
    tillage_type        VARCHAR(32)  NULL                     COMMENT 'djs_tillage_type 仅 farm_type=tillage_prepare',
    tillage_method      VARCHAR(32)  NULL                     COMMENT 'djs_tillage_way 仅 farm_type=tillage_prepare（doc/11 §1.9 字段名 tillage_method）',
    -- 移栽专用
    transplant_plot     BIGINT       NULL                     COMMENT '转移地块 FK → t_plant_plot_info.id 仅 farm_type=transplant',
    transplant_percent  TINYINT      NULL                     COMMENT '移栽百分比 0-100，业务规则≤60，仅 farm_type=transplant',
    -- 灾害专用
    disaster_type       VARCHAR(32)  NULL                     COMMENT 'djs_disaster_type 仅 farm_type=disaster',
    loss_rate           DECIMAL(5,2) NULL                     COMMENT '损失率 0-100，仅 farm_type=disaster',
    loss_yield          DECIMAL(12,3) NULL                    COMMENT '损失产量；触发更新 plant_details.loss_yield 累加',
    is_warning          TINYINT      NULL DEFAULT 2           COMMENT '预警标记 djs_yes_no 1=是 / 2=否（loss_rate≥30 触发 1）',
    -- 通用
    proof_oss_ids       VARCHAR(500) NULL                     COMMENT 'OSS bizType=plant_farm_proof，逗号分隔 ossIds',
    create_dept         BIGINT       NULL                     COMMENT '创建部门',
    create_by           BIGINT       NULL                     COMMENT '创建者',
    create_time         DATETIME     NULL                     COMMENT '创建时间',
    update_by           BIGINT       NULL                     COMMENT '更新者',
    update_time         DATETIME     NULL                     COMMENT '更新时间',
    remark              VARCHAR(500) NULL                     COMMENT '备注',
    del_flag            CHAR(1)      NOT NULL DEFAULT '0'     COMMENT '软删 0=正常 / 1=已删',
    del_unique          BIGINT       NOT NULL DEFAULT 0       COMMENT '软删唯一辅助列（活动行=0；软删行=id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_no (tenant_id, record_no, del_unique),
    KEY idx_plot_date (tenant_id, plot_id, farm_date),
    KEY idx_farm_type_date (tenant_id, farm_type, farm_date),
    KEY idx_farm_by (tenant_id, farm_by, farm_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农事记录单表（一表覆盖 12 类，farm_type 字段区分；doc/11 §1.9）';

-- ------------------------------------------------------------
-- 2. 字典 seed —— 4 个全 cleanup + 重 seed
-- ------------------------------------------------------------

-- 2.1 djs_farm_work_type 12 类（按 doc/06 §PLT-WORK-001 / doc/10 §F-PLT-06）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_farm_work_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_farm_work_type';

INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102600, '1001', '农事类型', 'djs_farm_work_type', 1, NOW(), 'PLT-WORK-001 12 类（空地 3 + 种植 7 + 其他 2）；doc/06 + doc/10 §F-PLT-06');

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    -- 空地管理 3 类
    (1026000, '1001',  0, '翻耕',       'tillage_break',    'djs_farm_work_type', '', 'info',    'Y', 1, NOW(), '空地阶段'),
    (1026001, '1001',  1, '整地',       'tillage_prepare',  'djs_farm_work_type', '', 'info',    'N', 1, NOW(), '空地阶段，特殊字段 tillage_type/method'),
    (1026002, '1001',  2, '施肥',       'fertilize',        'djs_farm_work_type', '', 'info',    'N', 1, NOW(), '空地阶段'),
    -- 种植管理 7 类
    (1026003, '1001',  3, '移栽',       'transplant',       'djs_farm_work_type', '', 'primary', 'N', 1, NOW(), '生长阶段独立画布，特殊字段 transplant_plot/percent'),
    (1026004, '1001',  4, '水肥',       'water_fertilize',  'djs_farm_work_type', '', 'primary', 'N', 1, NOW(), '生长阶段'),
    (1026005, '1001',  5, '浇灌',       'irrigation',       'djs_farm_work_type', '', 'primary', 'N', 1, NOW(), '生长阶段'),
    (1026006, '1001',  6, '除草',       'weed',             'djs_farm_work_type', '', 'primary', 'N', 1, NOW(), '生长阶段'),
    (1026007, '1001',  7, '病虫防治',   'pest_control',     'djs_farm_work_type', '', 'primary', 'N', 1, NOW(), '生长阶段'),
    (1026008, '1001',  8, '整枝绑蔓',   'pruning',          'djs_farm_work_type', '', 'primary', 'N', 1, NOW(), '生长阶段'),
    (1026009, '1001',  9, '退茬',       'rotation',         'djs_farm_work_type', '', 'primary', 'N', 1, NOW(), '生长阶段；触发 plot_info.plot_status=1 + plant_details.plant_status=completed'),
    -- 其他管理 2 类
    (1026010, '1001', 10, '灾害损失',   'disaster',         'djs_farm_work_type', '', 'danger',  'N', 1, NOW(), '独立画布；触发 plant_details.loss_yield 累加'),
    (1026011, '1001', 11, '采摘活动',   'harvest_activity', 'djs_farm_work_type', '', 'success', 'N', 1, NOW(), '生长阶段游客采摘活动登记');

-- 2.2 djs_tillage_type 4 默认（推断；客户后续可调）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_tillage_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_tillage_type';

INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102601, '1001', '整地类型', 'djs_tillage_type', 1, NOW(), 'PLT-WORK-001 farm_type=tillage_prepare 用；🔴 待客户最终确认');

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1026100, '1001', 0, '深耕', 'deep',    'djs_tillage_type', '', 'primary', 'Y', 1, NOW()),
    (1026101, '1001', 1, '浅耕', 'shallow', 'djs_tillage_type', '', 'info',    'N', 1, NOW()),
    (1026102, '1001', 2, '旋耕', 'rotary',  'djs_tillage_type', '', 'success', 'N', 1, NOW()),
    (1026103, '1001', 3, '深松', 'spike',   'djs_tillage_type', '', 'warning', 'N', 1, NOW());

-- 2.3 djs_tillage_way 3 默认
DELETE FROM sys_dict_data WHERE dict_type = 'djs_tillage_way';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_tillage_way';

INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102602, '1001', '整地方式', 'djs_tillage_way', 1, NOW(), 'PLT-WORK-001 farm_type=tillage_prepare 用；🔴 待客户最终确认');

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1026200, '1001', 0, '人工', 'manual',  'djs_tillage_way', '', 'info',    'Y', 1, NOW()),
    (1026201, '1001', 1, '机械', 'machine', 'djs_tillage_way', '', 'primary', 'N', 1, NOW()),
    (1026202, '1001', 2, '混合', 'mixed',   'djs_tillage_way', '', 'success', 'N', 1, NOW());

-- 2.4 djs_disaster_type 5 默认（doc/06 §PLT-WORK-003 推断）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_disaster_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_disaster_type';

INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102603, '1001', '灾害类型', 'djs_disaster_type', 1, NOW(), 'PLT-WORK-001 farm_type=disaster 用；🔴 待客户最终确认');

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1026300, '1001', 0, '虫害', 'insect',  'djs_disaster_type', '', 'warning', 'Y', 1, NOW()),
    (1026301, '1001', 1, '风灾', 'wind',    'djs_disaster_type', '', 'warning', 'N', 1, NOW()),
    (1026302, '1001', 2, '洪涝', 'flood',   'djs_disaster_type', '', 'danger',  'N', 1, NOW()),
    (1026303, '1001', 3, '干旱', 'drought', 'djs_disaster_type', '', 'danger',  'N', 1, NOW()),
    (1026304, '1001', 4, '病害', 'disease', 'djs_disaster_type', '', 'danger',  'N', 1, NOW());

-- ------------------------------------------------------------
-- 3. sys_menu 8090-8096（菜单 + 角色白名单）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    -- 8090 农事录入父菜单（M2 类型；M2 父挂在种植根 8000 下）
    (8090, '农事录入',       8000, 9,  'work',  '',           '', 1, 0, 'M', '0', '0', '',                                  'edit',     1, NOW(), 'PLT-WORK-001 mp 12 类 + admin 只读列表父'),
    -- mp 端 5 个端点权限（F 类型 visible=1 隐藏；mp 鉴权用）
    (8091, 'mp 中央分发台',  8090, 1, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:plant:work:dispatch',    '#', 1, NOW(), 'PLT-WORK-001 GET dispatchSummary'),
    (8092, 'mp 空地农事',    8090, 2, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:plant:work:empty',       '#', 1, NOW(), 'PLT-WORK-001 POST submit/empty'),
    (8093, 'mp 生长农事',    8090, 3, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:plant:work:grow',        '#', 1, NOW(), 'PLT-WORK-001 POST submit/grow'),
    (8094, 'mp 灾害录入',    8090, 4, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:plant:work:disaster',    '#', 1, NOW(), 'PLT-WORK-001 POST submit/disaster + plant_details.loss_yield 累加'),
    (8095, 'mp 移栽录入',    8090, 5, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:plant:work:transplant',  '#', 1, NOW(), 'PLT-WORK-001 POST submit/transplant'),
    (8096, 'mp 我的记录',    8090, 6, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:plant:work:myList',      '#', 1, NOW(), 'PLT-WORK-001 GET myRecords'),
    -- 8097 mp 班组下拉（配套 TeamPicker）
    (8097, 'mp 班组下拉',    8090, 7, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:plant:team:list',        '#', 1, NOW(), 'PLT-WORK-001 配套 TeamPicker GET listAll');

-- 角色白名单：role_id NOT IN (1, 101) AND del_flag='0' —— 全 mp 业务角色
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id BETWEEN 8090 AND 8097;
