-- ============================================================
-- WMS-MD-001 仓库主数据：库位 + 库存明细
--
-- 1. t_warehouse_location_info 库位信息
-- 2. t_warehouse_location_stock 库存明细（3 维互斥关联：product_id / ear_no / plot_id）
-- 3. 字典 seed：djs_location_type / djs_location_status / djs_check_result
--    （djs_yes_no 已在 V202605201000 SYS-INIT-002 seed，is_end 字段复用）
-- 4. sys_menu seed：9000 仓库父菜单 + 9010 库位管理 + 9020 库存查询 + 子按钮
--
-- 字段权威：doc/06 §WMS-MD-001 + doc/11 §2.1/§2.2
-- ⏳ djs_location_type 客户未明示，先 seed 业内典型 6 类（doc/11 §0.3）
--    客户回复后可在 admin 字典管理页自由增删
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_warehouse_location_info 库位信息表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_location_info;
CREATE TABLE t_warehouse_location_info (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    location_code   VARCHAR(32)  NOT NULL COMMENT '业务码（用户手填，例 L0001 / FROZEN-01）',
    location_name   VARCHAR(64)  NOT NULL COMMENT '库位名称（例 冻品库 / 蔬菜鲜品库）',
    location_type   VARCHAR(32)  NOT NULL COMMENT '字典 djs_location_type（frozen/fresh/dry/ambient/medicine/feed）',
    location_thumb  VARCHAR(512) NULL COMMENT '缩略图 OSS ID',
    location_img    VARCHAR(2048) NULL COMMENT '原图 OSS IDs 逗号分隔',
    location_status TINYINT      NOT NULL DEFAULT 1 COMMENT '字典 djs_location_status：1=启用 / 2=停用',
    capacity        DECIMAL(12,2) NULL COMMENT '容量（kg / m³，单位由 location_type 决定）',
    create_dept     BIGINT       NULL COMMENT '创建部门',
    create_by       BIGINT       NULL COMMENT '创建人',
    create_time     DATETIME     NULL COMMENT '创建时间',
    update_by       BIGINT       NULL COMMENT '更新人',
    update_time     DATETIME     NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
    remark          VARCHAR(500) NULL COMMENT '备注',
    del_unique      BIGINT       NOT NULL DEFAULT 0 COMMENT "软删 token（update del_flag='1' 时同步 SET del_unique=id）",
    PRIMARY KEY (id),
    UNIQUE KEY uk_location_code (tenant_id, location_code, del_unique),
    KEY idx_tenant_status (tenant_id, location_status),
    KEY idx_location_type (tenant_id, location_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库位信息表（WMS-MD-001）';

-- ------------------------------------------------------------
-- 2. t_warehouse_location_stock 库存明细表
--    3 维互斥关联（product_id / ear_no / plot_id 三选一），V1 应用层校验
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_location_stock;
CREATE TABLE t_warehouse_location_stock (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id         VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    location_id       BIGINT        NOT NULL COMMENT 'FK → t_warehouse_location_info.id',
    product_id        BIGINT        NULL COMMENT 'FK → t_warehouse_product_info（WMS-MD-002 D8 建）',
    ear_no            VARCHAR(32)   NULL COMMENT '白条入库按耳号关联（猪只库存追溯）',
    plot_id           BIGINT        NULL COMMENT '蔬菜入库按地块关联',
    product_name      VARCHAR(128)  NOT NULL COMMENT '产品名称（冗余字段，便于列表展示）',
    product_stock     DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '当前库存数量',
    product_unit      VARCHAR(16)   NOT NULL COMMENT '产品单位（kg / 头 / 箱 等）',
    is_end            TINYINT       NOT NULL DEFAULT 1 COMMENT '字典 djs_yes_no：是否完成；1=是（已用完，不显示）/ 0=否（进行中）',
    latest_check_time DATETIME      NULL COMMENT '最新盘点时间（由 WMS-STOCK-001 D11 更新）',
    check_result      TINYINT       NULL COMMENT '字典 djs_check_result：1=正常 / 2=异常 / 3=计损',
    operator_id       BIGINT        NULL COMMENT '最后操作人 FK → sys_user.user_id（ADR-0007）',
    create_dept       BIGINT        NULL COMMENT '创建部门',
    create_by         BIGINT        NULL COMMENT '创建人',
    create_time       DATETIME      NULL COMMENT '创建时间',
    update_by         BIGINT        NULL COMMENT '更新人',
    update_time       DATETIME      NULL COMMENT '更新时间',
    del_flag          CHAR(1)       DEFAULT '0' COMMENT '删除标志',
    remark            VARCHAR(500)  NULL COMMENT '备注',
    del_unique        BIGINT        NOT NULL DEFAULT 0 COMMENT "软删 token（update del_flag='1' 时同步 SET del_unique=id）",
    PRIMARY KEY (id),
    KEY idx_location (tenant_id, location_id),
    KEY idx_product (tenant_id, product_id),
    KEY idx_ear_no (tenant_id, ear_no),
    KEY idx_plot (tenant_id, plot_id),
    KEY idx_is_end (tenant_id, is_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存明细表（WMS-MD-001）';


-- ------------------------------------------------------------
-- 3. 字典 seed
--    dict_id 起始 102000（避开 100xxx 已占 + 101xxx 预留段；当前 max 100800）
--    dict_code 起始 1020000
-- ------------------------------------------------------------

-- 3.1 djs_location_type 库位类型（业内典型 6 类，客户回复后可在 admin 字典页自由增删）
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (102000, '1001', '库位类型', 'djs_location_type', 1, NOW(), '仓库：库位类型（WMS-MD-001 业内默认 6 类；客户未明示，可后调）');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1020000, '1001', 0, '冻品',     'frozen',   'djs_location_type', '', 'primary', 'N', 1, NOW()),
    (1020001, '1001', 1, '鲜品',     'fresh',    'djs_location_type', '', 'success', 'N', 1, NOW()),
    (1020002, '1001', 2, '干燥',     'dry',      'djs_location_type', '', 'warning', 'N', 1, NOW()),
    (1020003, '1001', 3, '常温',     'ambient',  'djs_location_type', '', 'info',    'N', 1, NOW()),
    (1020004, '1001', 4, '药品库',   'medicine', 'djs_location_type', '', 'danger',  'N', 1, NOW()),
    (1020005, '1001', 5, '饲料库',   'feed',     'djs_location_type', '', '',        'N', 1, NOW());

-- 3.2 djs_location_status 库位状态（1 启用 / 2 停用，与表字段 location_status TINYINT 对齐）
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (102001, '1001', '库位状态', 'djs_location_status', 1, NOW(), '仓库：库位状态 1=启用 / 2=停用（WMS-MD-001）');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1020010, '1001', 0, '启用', '1', 'djs_location_status', '', 'success', 'Y', 1, NOW()),
    (1020011, '1001', 1, '停用', '2', 'djs_location_status', '', 'info',    'N', 1, NOW());

-- 3.3 djs_check_result 盘点结果（1 正常 / 2 异常 / 3 计损）
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (102002, '1001', '盘点结果', 'djs_check_result', 1, NOW(), '仓库：盘点结果（WMS-MD-001 + WMS-STOCK-001 共用）');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1020020, '1001', 0, '正常', '1', 'djs_check_result', '', 'success', 'Y', 1, NOW()),
    (1020021, '1001', 1, '异常', '2', 'djs_check_result', '', 'danger',  'N', 1, NOW()),
    (1020022, '1001', 2, '计损', '3', 'djs_check_result', '', 'warning', 'N', 1, NOW());


-- ------------------------------------------------------------
-- 4. sys_menu seed
--    9000 仓库（一级菜单，order_num=4，与养殖 7000 / 种植 8000 形成业务一级序列）
--    9010 库位管理 + 9011-9015 5 按钮权限
--    9020 库存查询 + 9021-9025 5 按钮权限（V1 只读列表，无新增 / 编辑入口；
--                                          库存写入由 WMS-DEMAND-001 / WMS-STOCK-001 后续 ticket 负责）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 一级父菜单：仓库
    (9000, '仓库', 0, 4, 'djs-warehouse', '', '',
     1, 0, 'M', '0', '0',
     '', 'shopping', 1, NOW(), 'WMS-MD-001'),

    -- 二级菜单：库位管理（C 类，独立路由 + 组件）
    (9010, '库位管理', 9000, 1, 'location', 'djs-warehouse/location/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:location:list', 'tree', 1, NOW(), 'WMS-MD-001'),

    -- 库位 5 按钮权限
    (9011, '库位查询', 9010, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:location:list',   '#', 1, NOW(), 'WMS-MD-001'),
    (9012, '库位新增', 9010, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:location:add',    '#', 1, NOW(), 'WMS-MD-001'),
    (9013, '库位修改', 9010, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:location:edit',   '#', 1, NOW(), 'WMS-MD-001'),
    (9014, '库位删除', 9010, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:location:remove', '#', 1, NOW(), 'WMS-MD-001'),
    (9015, '库位导出', 9010, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:location:export', '#', 1, NOW(), 'WMS-MD-001'),

    -- 二级菜单：库存查询（C 类，只读）
    (9020, '库存查询', 9000, 2, 'stock', 'djs-warehouse/stock/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:stock:list', 'list', 1, NOW(), 'WMS-MD-001'),

    -- 库存只读：仅 seed list / export 两个 perm；add / edit / remove 不 seed，
    -- 避免 v-hasPermi 在 admin superadmin 视角下错误暴露写入按钮；
    -- 库存写入入口由 WMS-DEMAND-001 / WMS-STOCK-001 D8-D11 ticket 提供（走流水触发，不开 admin 直接编辑）
    (9021, '库存查询', 9020, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:stock:list',   '#', 1, NOW(), 'WMS-MD-001'),
    (9025, '库存导出', 9020, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:stock:export', '#', 1, NOW(), 'WMS-MD-001');
