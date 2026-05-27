-- ============================================================
-- WMS-MD-002 产品 / 商品 / 礼盒主数据
--
-- 1. t_warehouse_product_info 产品/商品共表（product_type=1 自产 / 2 外购 / 3 礼盒）
-- 2. t_warehouse_gift_box     礼盒组件清单（product_type=3 时多对多组件关系）
-- 3. 字典 seed：djs_product_type / djs_belong_type / djs_buy_class（空）/
--               djs_product_attr / djs_product_workshop
-- 4. sys_menu seed：9030 商品管理 + 9031-9035 5 按钮权限
--
-- 字段权威：doc/06 §WMS-MD-002 + doc/11 §2.5
--
-- 说明：baseline (V202605200903) 曾建过 t_warehouse_product_info 占位结构
-- （字段名 product_code / gift_components JSON 等已废），本 ticket DROP 后用
-- doc/11 §2.5 字段权威重建；占位表为空，无数据迁移负担。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_warehouse_product_info 产品 / 商品共表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_product_info;
CREATE TABLE t_warehouse_product_info (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id         VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    product_id        VARCHAR(32)   NOT NULL COMMENT '业务码（用户手填，例 P0001 / SP-PORK-001）',
    product_name      VARCHAR(128)  NOT NULL COMMENT '产品 / 商品名称',
    product_type      TINYINT       NOT NULL COMMENT '字典 djs_product_type：1=自产 / 2=外购 / 3=礼盒',
    product_unit      VARCHAR(16)   NOT NULL COMMENT '单位（kg / 个 / 盒 等）',
    product_spec      VARCHAR(64)   NULL     COMMENT '规格（如 500g/包）',
    belong_type       VARCHAR(32)   NULL     COMMENT '字典 djs_belong_type：自产归属类型（pork/vegetable/white_bar/dry_good/egg/gift_box）',
    buy_class         VARCHAR(32)   NULL     COMMENT '字典 djs_buy_class：外购产品类（V1 客户后填）',
    product_thumb     VARCHAR(512)  NULL     COMMENT '缩略图 OSS ID',
    product_img       VARCHAR(2048) NULL     COMMENT '原图 OSS IDs 逗号分隔',
    product_attr      TINYINT       NULL     COMMENT '字典 djs_product_attr：1=生产产品 / 2=原材料',
    product_workshop  TINYINT       NULL     COMMENT '字典 djs_product_workshop：1=燎毛间 / 2=分割间 / 3=肉品打包 / 4=蔬菜打包',
    store_location_id VARCHAR(255)  NULL     COMMENT '存储库位 ID 列表（逗号分隔；V2 改关联表）',
    product_status    TINYINT       NOT NULL DEFAULT 0 COMMENT '字典 sys_normal_disable：0=正常 / 1=停用',
    product_material  BIGINT        NULL     COMMENT 'FK → t_warehouse_product_info.id（生产产品关联原材料）',
    product_desc      VARCHAR(500)  NULL     COMMENT '产品描述',
    material_num      DECIMAL(12,3) NULL     COMMENT '原材料计算量（主要鸡蛋）',
    is_delivery       TINYINT       NOT NULL DEFAULT 1 COMMENT '字典 djs_yes_no：是否发货产品 1=是 / 0=否',
    supplier_id       BIGINT        NULL     COMMENT 'FK → t_md_supplier.id（外购产品填）',
    is_buy_out        TINYINT       NOT NULL DEFAULT 0 COMMENT '字典 djs_yes_no：是否可外购 1=是 / 0=否',
    create_dept       BIGINT        NULL     COMMENT '创建部门',
    create_by         BIGINT        NULL     COMMENT '创建人',
    create_time       DATETIME      NULL     COMMENT '创建时间',
    update_by         BIGINT        NULL     COMMENT '更新人',
    update_time       DATETIME      NULL     COMMENT '更新时间',
    del_flag          CHAR(1)       DEFAULT '0' COMMENT '删除标志',
    remark            VARCHAR(500)  NULL     COMMENT '备注',
    del_unique        BIGINT        NOT NULL DEFAULT 0 COMMENT '软删 token（update del_flag=''1'' 时同步 SET del_unique=id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_id (tenant_id, product_id, del_unique),
    KEY idx_product_type (tenant_id, product_type),
    KEY idx_belong_type  (tenant_id, belong_type),
    KEY idx_buy_class    (tenant_id, buy_class),
    KEY idx_status       (tenant_id, product_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品/商品/礼盒共表（WMS-MD-002）';

-- ------------------------------------------------------------
-- 2. t_warehouse_gift_box 礼盒组件清单
--    与 t_warehouse_product_info 1:N（一个礼盒 N 个组件 SKU）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_gift_box;
CREATE TABLE t_warehouse_gift_box (
    id                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id             VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    box_product_id        BIGINT        NOT NULL COMMENT 'FK → t_warehouse_product_info.id（product_type=3 礼盒）',
    component_product_id  BIGINT        NOT NULL COMMENT 'FK → t_warehouse_product_info.id（组件 SKU）',
    component_count       DECIMAL(12,3) NOT NULL COMMENT '组件数量',
    component_unit        VARCHAR(16)   NOT NULL COMMENT '组件单位（冗余，便于展示）',
    component_sort        INT           NOT NULL DEFAULT 0 COMMENT '排序',
    create_dept           BIGINT        NULL     COMMENT '创建部门',
    create_by             BIGINT        NULL     COMMENT '创建人',
    create_time           DATETIME      NULL     COMMENT '创建时间',
    update_by             BIGINT        NULL     COMMENT '更新人',
    update_time           DATETIME      NULL     COMMENT '更新时间',
    del_flag              CHAR(1)       DEFAULT '0' COMMENT '删除标志',
    remark                VARCHAR(500)  NULL     COMMENT '备注',
    del_unique            BIGINT        NOT NULL DEFAULT 0 COMMENT '软删 token',
    PRIMARY KEY (id),
    KEY idx_box       (tenant_id, box_product_id),
    KEY idx_component (tenant_id, component_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼盒组件清单（WMS-MD-002）';

-- ============================================================
-- 3. 字典 seed（5 个）
--    dict_id 起始 102010（WMS-MD-001 用到 102000-102002）
--    dict_code 起始 1020030（WMS-MD-001 用到 1020000-1020022）
-- ============================================================

-- 3.1 djs_product_type 产品类型（3 项）
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102010, '1001', '产品类型', 'djs_product_type', 1, NOW(), '仓库：产品类型 1=自产 / 2=外购 / 3=礼盒（WMS-MD-002）');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1020030, '1001', 0, '自产', '1', 'djs_product_type', '', 'success', 'Y', 1, NOW()),
    (1020031, '1001', 1, '外购', '2', 'djs_product_type', '', 'warning', 'N', 1, NOW()),
    (1020032, '1001', 2, '礼盒', '3', 'djs_product_type', '', 'primary', 'N', 1, NOW());

-- 3.2 djs_belong_type 自产产品归属类型（6 项，dict_value 字符串 enum）
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102011, '1001', '自产产品归属类型', 'djs_belong_type', 1, NOW(), '仓库：自产产品归属类型（WMS-MD-002，product_type=1 时使用）');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1020040, '1001', 0, '猪肉产品', 'pork',       'djs_belong_type', '', 'danger',  'N', 1, NOW()),
    (1020041, '1001', 1, '果蔬产品', 'vegetable',  'djs_belong_type', '', 'success', 'N', 1, NOW()),
    (1020042, '1001', 2, '白条产品', 'white_bar',  'djs_belong_type', '', 'warning', 'N', 1, NOW()),
    (1020043, '1001', 3, '干货产品', 'dry_good',   'djs_belong_type', '', 'info',    'N', 1, NOW()),
    (1020044, '1001', 4, '鸡蛋产品', 'egg',        'djs_belong_type', '', '',        'N', 1, NOW()),
    (1020045, '1001', 5, '礼盒产品', 'gift_box',   'djs_belong_type', '', 'primary', 'N', 1, NOW());

-- 3.3 djs_buy_class 外购产品类（字典 type 建好，dict_data 留空 — 客户 Q3 未明示，admin 字典管理页让客户自加）
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102012, '1001', '外购产品类', 'djs_buy_class', 1, NOW(), '仓库：外购产品类（WMS-MD-002，🔴 Q3 客户未明示具体类目；admin 字典管理页可自加）');

-- 3.4 djs_product_attr 产品属性（2 项）
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102013, '1001', '产品属性', 'djs_product_attr', 1, NOW(), '仓库：产品属性 1=生产产品 / 2=原材料（WMS-MD-002）');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1020050, '1001', 0, '生产产品', '1', 'djs_product_attr', '', 'primary', 'Y', 1, NOW()),
    (1020051, '1001', 1, '原材料',   '2', 'djs_product_attr', '', 'info',    'N', 1, NOW());

-- 3.5 djs_product_workshop 生产车间（4 项）
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102014, '1001', '生产车间', 'djs_product_workshop', 1, NOW(), '仓库：生产车间 1=燎毛间 / 2=分割间 / 3=肉品打包 / 4=蔬菜打包（WMS-MD-002）');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1020060, '1001', 0, '燎毛间',     '1', 'djs_product_workshop', '', 'danger',  'N', 1, NOW()),
    (1020061, '1001', 1, '分割间',     '2', 'djs_product_workshop', '', 'warning', 'N', 1, NOW()),
    (1020062, '1001', 2, '肉品打包间', '3', 'djs_product_workshop', '', 'primary', 'N', 1, NOW()),
    (1020063, '1001', 3, '蔬菜打包间', '4', 'djs_product_workshop', '', 'success', 'N', 1, NOW());

-- ============================================================
-- 4. sys_menu seed：9030 商品管理 + 5 按钮权限
-- ============================================================
INSERT IGNORE INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
VALUES
    (9030, '商品管理', 9000, 3, 'product', 'djs-warehouse/product/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:product:list', 'goods',
     1, NOW(), 'WMS-MD-002'),

    -- 5 按钮权限
    (9031, '商品查询', 9030, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:product:list',   '#', 1, NOW(), 'WMS-MD-002'),
    (9032, '商品新增', 9030, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:product:add',    '#', 1, NOW(), 'WMS-MD-002'),
    (9033, '商品修改', 9030, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:product:edit',   '#', 1, NOW(), 'WMS-MD-002'),
    (9034, '商品删除', 9030, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:product:remove', '#', 1, NOW(), 'WMS-MD-002'),
    (9035, '商品导出', 9030, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:product:export', '#', 1, NOW(), 'WMS-MD-002');
