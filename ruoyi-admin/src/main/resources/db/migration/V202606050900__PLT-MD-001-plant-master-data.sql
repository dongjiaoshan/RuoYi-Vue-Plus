-- ============================================================================
-- PLT-MD-001 种植主数据：片区 / 地块 / 作物（首落地，D8）
--
-- 1. t_plant_plot_zone 片区
-- 2. t_plant_plot_info 地块（zone_id 一对多，方案 A）
-- 3. t_plant_crop_info 作物
-- 4. 字典补漏 seed：djs_plot_type (新增) / djs_plot_status (新增) / djs_planting_season (新增)
--    djs_soil_type / djs_soil_fertility / djs_terrain_condition /
--    djs_light_condition / djs_drain_condition / djs_crop_family 已 D1 seed，本 ticket 不重复
-- 5. sys_menu seed：8010-8017 片区+地块 + 8020-8024 作物（一级菜单 8000 由 SYS-AUTH-001 seed）
--
-- 字段权威：doc/06 §PLT-MD-001 + doc/11 §1.1/§1.2/§1.3
-- 决策 A 固化（D7 closing）：不建 t_plant_zone_plotno，仅用 plot.zone_id 一对多
-- ============================================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_plant_plot_zone 片区表
--    D1 SYS-INIT-001 骨架字段不足，按 doc/11 §1.1 重建
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_plot_zone;
CREATE TABLE t_plant_plot_zone (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    zone_code       VARCHAR(32)  NOT NULL                COMMENT '片区业务码（用户手填，例 Z001）',
    zone_name       VARCHAR(64)  NOT NULL                COMMENT '片区名称（例 A 区 / 东部温室区）',
    zone_desc       VARCHAR(255) NULL                    COMMENT '片区说明',
    zone_belong     VARCHAR(64)  NULL                    COMMENT '所属大区（例 东部）',
    zone_status     TINYINT      NOT NULL DEFAULT 1      COMMENT '字典 sys_normal_disable：1=正常 / 2=停用',
    create_dept     BIGINT       NULL                    COMMENT '创建部门',
    create_by       BIGINT       NULL                    COMMENT '创建人',
    create_time     DATETIME     NULL                    COMMENT '创建时间',
    update_by       BIGINT       NULL                    COMMENT '更新人',
    update_time     DATETIME     NULL                    COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0'             COMMENT '删除标志',
    remark          VARCHAR(500) NULL                    COMMENT '备注',
    del_unique      BIGINT       NOT NULL DEFAULT 0      COMMENT "软删 token（update del_flag='1' 时同步 SET del_unique=id）",
    PRIMARY KEY (id),
    UNIQUE KEY uk_zone_code (tenant_id, zone_code, del_unique),
    KEY idx_zone_status (tenant_id, zone_status),
    KEY idx_zone_name (tenant_id, zone_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植 - 片区表（PLT-MD-001）';


-- ------------------------------------------------------------
-- 2. t_plant_plot_info 地块表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_plot_info;
CREATE TABLE t_plant_plot_info (
    id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id           VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    plot_code           VARCHAR(32)   NOT NULL                COMMENT '地块业务码（用户手填，例 P001）',
    plot_image_preview  VARCHAR(512)  NULL                    COMMENT '缩略图 OSS objectName（单张）',
    plot_image_url      VARCHAR(2048) NULL                    COMMENT '原图 OSS objectNames 逗号分隔（多张）',
    zone_id             BIGINT        NOT NULL                COMMENT 'FK → t_plant_plot_zone.id（一对多）',
    plot_type           VARCHAR(32)   NULL                    COMMENT '字典 djs_plot_type（露天/大棚/温室）',
    plot_name           VARCHAR(64)   NOT NULL                COMMENT '地块名称',
    plot_status         TINYINT       NOT NULL DEFAULT 1      COMMENT '字典 djs_plot_status：1=空闲 / 2=种植 / 3=采摘',
    is_lease            TINYINT       NOT NULL DEFAULT 0      COMMENT '字典 djs_yes_no：是否租赁 1=是 / 0=否',
    plot_remark         VARCHAR(500)  NULL                    COMMENT '地块业务备注（区别于系统 remark）',
    plot_area           DECIMAL(10,2) NOT NULL                COMMENT '面积（亩）',
    plot_location_desc  VARCHAR(500)  NULL                    COMMENT '位置文字描述',
    plot_location_x     DECIMAL(10,7) NULL                    COMMENT '经度',
    plot_location_y     DECIMAL(10,7) NULL                    COMMENT '纬度',
    soil_type           VARCHAR(32)   NULL                    COMMENT '字典 djs_soil_type',
    soil_fertility      VARCHAR(32)   NULL                    COMMENT '字典 djs_soil_fertility',
    soil_ph             DECIMAL(4,2)  NULL                    COMMENT '土壤 PH 值（0.00-14.00）',
    terrain_condition   VARCHAR(32)   NULL                    COMMENT '字典 djs_terrain_condition',
    light_condition     VARCHAR(32)   NULL                    COMMENT '字典 djs_light_condition',
    drain_condition     VARCHAR(32)   NULL                    COMMENT '字典 djs_drain_condition',
    create_dept         BIGINT        NULL                    COMMENT '创建部门',
    create_by           BIGINT        NULL                    COMMENT '创建人',
    create_time         DATETIME      NULL                    COMMENT '创建时间',
    update_by           BIGINT        NULL                    COMMENT '更新人',
    update_time         DATETIME      NULL                    COMMENT '更新时间',
    del_flag            CHAR(1)       DEFAULT '0'             COMMENT '删除标志',
    remark              VARCHAR(500)  NULL                    COMMENT '备注',
    del_unique          BIGINT        NOT NULL DEFAULT 0      COMMENT "软删 token（update del_flag='1' 时同步 SET del_unique=id）",
    PRIMARY KEY (id),
    UNIQUE KEY uk_plot_code (tenant_id, plot_code, del_unique),
    KEY idx_plot_zone (tenant_id, zone_id),
    KEY idx_plot_status (tenant_id, plot_status),
    KEY idx_plot_name (tenant_id, plot_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植 - 地块信息表（PLT-MD-001）';


-- ------------------------------------------------------------
-- 3. t_plant_crop_info 作物表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_crop_info;
CREATE TABLE t_plant_crop_info (
    id                       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id                VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    crop_code                VARCHAR(32)   NOT NULL                COMMENT '作物业务码（用户手填，例 C001）',
    crop_name                VARCHAR(64)   NOT NULL                COMMENT '作物名称（例 白菜）',
    crop_image_preview       VARCHAR(512)  NULL                    COMMENT '缩略图 OSS objectName',
    crop_image_url           VARCHAR(2048) NULL                    COMMENT '原图 OSS objectNames 逗号分隔',
    variety_name             VARCHAR(64)   NULL                    COMMENT '品种名（例 京白菜 4 号）',
    variety_origin           VARCHAR(128)  NULL                    COMMENT '品种来源/供应商（V1 自由文本，V2 FK supplier_id）',
    crop_family              VARCHAR(32)   NULL                    COMMENT '字典 djs_crop_family（科属）',
    related_product          BIGINT        NULL                    COMMENT '关联产品 FK → t_warehouse_product_info.id（D8 WMS-MD-002 同日，逻辑约束不写 FK）',
    planting_season          VARCHAR(64)   NULL                    COMMENT '字典 djs_planting_season 多选逗号分隔',
    sowing_period            VARCHAR(64)   NULL                    COMMENT '适宜播种期（手输自由文本，例 "3 月上旬-4 月下旬"）',
    max_cycle                INT           NULL                    COMMENT '生长最大周期（天）',
    min_cycle                INT           NULL                    COMMENT '生长最小周期（天）',
    fertilization_interval   INT           NULL                    COMMENT '施肥间隔（天）',
    irrigation_interval      INT           NULL                    COMMENT '浇灌间隔（天）',
    predicted_per            DECIMAL(12,3) NULL                    COMMENT '预计亩产（kg/亩）',
    quality_desc             VARCHAR(500)  NULL                    COMMENT '品质描述',
    pick_unit_price          DECIMAL(10,2) NULL                    COMMENT '采摘单价（元/斤），P1#14 单价位置 V1 暂放此',
    create_dept              BIGINT        NULL                    COMMENT '创建部门',
    create_by                BIGINT        NULL                    COMMENT '创建人',
    create_time              DATETIME      NULL                    COMMENT '创建时间',
    update_by                BIGINT        NULL                    COMMENT '更新人',
    update_time              DATETIME      NULL                    COMMENT '更新时间',
    del_flag                 CHAR(1)       DEFAULT '0'             COMMENT '删除标志',
    remark                   VARCHAR(500)  NULL                    COMMENT '备注',
    del_unique               BIGINT        NOT NULL DEFAULT 0      COMMENT "软删 token",
    PRIMARY KEY (id),
    UNIQUE KEY uk_crop_code (tenant_id, crop_code, del_unique),
    KEY idx_crop_name (tenant_id, crop_name),
    KEY idx_crop_family (tenant_id, crop_family)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植 - 作物信息表（PLT-MD-001）';


-- ------------------------------------------------------------
-- 4. 字典补漏 seed
--    D1 SYS-INIT-002 已 seed：djs_soil_type / djs_soil_fertility / djs_terrain_condition /
--    djs_light_condition / djs_drain_condition / djs_crop_family / djs_yes_no / sys_normal_disable
--    本 ticket 补 3 个：djs_plot_type / djs_plot_status / djs_planting_season
--
--    dict_id 起始 103000（避开 102xxx WMS-MD-001 占用）
--    dict_code 起始 1030000
-- ------------------------------------------------------------

-- 4.1 djs_plot_type 地块类型（业内默认 3 类，客户回复 Q1 后可调）
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (103000, '1001', '地块类型', 'djs_plot_type', 1, NOW(), '种植：地块类型（PLT-MD-001 业内默认 3 类；xlsx 未给，客户 Q1 待回复）');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1030000, '1001', 0, '露天', 'open',       'djs_plot_type', '', 'default', 'Y', 1, NOW()),
    (1030001, '1001', 1, '大棚', 'shed',       'djs_plot_type', '', 'success', 'N', 1, NOW()),
    (1030002, '1001', 2, '温室', 'greenhouse', 'djs_plot_type', '', 'warning', 'N', 1, NOW());

-- 4.2 djs_plot_status 地块状态（1 空闲 / 2 种植 / 3 采摘）
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (103001, '1001', '地块状态', 'djs_plot_status', 1, NOW(), '种植：地块状态 1=空闲 / 2=种植 / 3=采摘（PLT-MD-001）');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1030010, '1001', 0, '空闲', '1', 'djs_plot_status', '', 'info',    'Y', 1, NOW()),
    (1030011, '1001', 1, '种植', '2', 'djs_plot_status', '', 'success', 'N', 1, NOW()),
    (1030012, '1001', 2, '采摘', '3', 'djs_plot_status', '', 'warning', 'N', 1, NOW());

-- 4.3 djs_planting_season 种植季节（春/夏/秋/冬，作物多选逗号分隔）
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (103002, '1001', '种植季节', 'djs_planting_season', 1, NOW(), '种植：种植季节 春/夏/秋/冬（PLT-MD-001，作物多选）');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1030020, '1001', 0, '春季', 'spring', 'djs_planting_season', '', 'success', 'Y', 1, NOW()),
    (1030021, '1001', 1, '夏季', 'summer', 'djs_planting_season', '', 'danger',  'N', 1, NOW()),
    (1030022, '1001', 2, '秋季', 'autumn', 'djs_planting_season', '', 'warning', 'N', 1, NOW()),
    (1030023, '1001', 3, '冬季', 'winter', 'djs_planting_season', '', 'info',    'N', 1, NOW());


-- ------------------------------------------------------------
-- 5. sys_menu seed
--    8000 种植（一级菜单）由 SYS-AUTH-001 seed，本 ticket 不动
--    8010 片区/地块单页（C 类，树形双栏）+ 8011-8017 7 按钮权限
--    8020 作物管理（C 类，独立 list）+ 8021-8024 4 按钮权限
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 二级菜单：片区与地块（树形双栏单页）
    (8010, '片区与地块', 8000, 1, 'plot', 'djs-plant/plot/index', '',
     1, 0, 'C', '0', '0',
     'djs:plant:plot:list', 'tree', 1, NOW(), 'PLT-MD-001'),

    -- 片区 / 地块 7 按钮权限
    (8011, '片区新增', 8010, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:zone:add',    '#', 1, NOW(), 'PLT-MD-001'),
    (8012, '片区修改', 8010, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:zone:edit',   '#', 1, NOW(), 'PLT-MD-001'),
    (8013, '片区删除', 8010, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:zone:remove', '#', 1, NOW(), 'PLT-MD-001'),
    (8014, '地块新增', 8010, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:plot:add',    '#', 1, NOW(), 'PLT-MD-001'),
    (8015, '地块修改', 8010, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:plot:edit',   '#', 1, NOW(), 'PLT-MD-001'),
    (8016, '地块删除', 8010, 6, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:plot:remove', '#', 1, NOW(), 'PLT-MD-001'),
    (8017, '地块导出', 8010, 7, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:plot:export', '#', 1, NOW(), 'PLT-MD-001'),

    -- 二级菜单：作物管理
    (8020, '作物管理', 8000, 2, 'crop', 'djs-plant/crop/index', '',
     1, 0, 'C', '0', '0',
     'djs:plant:crop:list', 'sprout', 1, NOW(), 'PLT-MD-001'),

    -- 作物 4 按钮权限
    (8021, '作物新增', 8020, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:crop:add',    '#', 1, NOW(), 'PLT-MD-001'),
    (8022, '作物修改', 8020, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:crop:edit',   '#', 1, NOW(), 'PLT-MD-001'),
    (8023, '作物删除', 8020, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:crop:remove', '#', 1, NOW(), 'PLT-MD-001'),
    (8024, '作物导出', 8020, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:crop:export', '#', 1, NOW(), 'PLT-MD-001');

-- 分配菜单到超管角色（role_id=1）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 8010), (1, 8011), (1, 8012), (1, 8013),
    (1, 8014), (1, 8015), (1, 8016), (1, 8017),
    (1, 8020), (1, 8021), (1, 8022), (1, 8023), (1, 8024);
