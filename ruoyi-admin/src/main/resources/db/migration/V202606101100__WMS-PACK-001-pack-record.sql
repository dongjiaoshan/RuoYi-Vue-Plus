-- ============================================================
-- WMS-PACK-001 蔬菜 / 礼盒 / 干货 / 芹菜打包工序（D10）
--
-- 1. t_warehouse_product_production 发货产品生产信息表（doc/11 §2.6 R3-R35）
--    本 ticket 写入入口；admin 只读，mp 4 业态写入
--    D2 baseline 占位字段偏离 → DROP+CREATE 重建（同 WMS-PIG-002 处理 bar_info 模式）
-- 2. 字典 3 新建：djs_pack_status / djs_produce_location / djs_deliver_type
--    复用：djs_product_type（D8）/ djs_yes_no（baseline 1=是 / 0=否）/ djs_belong_type（D8）
-- 3. sys_menu seed 9220-9235（仓库 9000-9999 段内，本日 WMS-SHIP-001 占 9200-9215）
-- 4. role_menu 白名单绑定（role_id NOT IN (1, 101) AND del_flag='0'）
--
-- 礼盒组合：复用 D8 落的 t_warehouse_gift_box（box_product_id + component_product_id 关联表，
--   Kevin override 方案 b 已生效）；本 ticket service 礼盒打包按 box_product_id 查
--   gift_box 拿组件清单，扣减来源 product_inhouse / location_stock，写主 production 记录。
--
-- 跨表事务：service 层 @Transactional 控制 4 业态 submit：
--   1) 校验来源 product_inhouse 存在 + 数量足够
--   2) UPDATE product_inhouse 扣减来源（或标记 consumed）
--   3) INSERT product_production（pack_status='packed' / produce_no 业务码 / 凭证图）
--   4) INSERT stock_flow (flow_type='pack_in', inout_type='IN', 入冻品/鲜品库)
--   5) UPDATE location_stock += quantity
--   6) hook 预留 trace_code（D11 TRC-CORE-001 回填）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_warehouse_product_production 发货产品生产信息表（重建，对齐 doc/11 §2.6）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_product_production;
CREATE TABLE t_warehouse_product_production (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id             VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    produce_date          DATE         NOT NULL COMMENT '生产日期（doc/11 §2.6 R6）',
    produce_no            VARCHAR(32)  NOT NULL COMMENT '业务码 yyMMdd+前缀+4位（Z=猪肉/G=果蔬/B=白条/H=干货/D=鸡蛋/L=礼盒，doc/11 §2.6 R7）',
    product_id            BIGINT       NOT NULL COMMENT 'FK → t_warehouse_product_info.id（doc/11 §2.6 R8）',
    product_name          VARCHAR(128) NOT NULL COMMENT '产品名称冗余（doc/11 §2.6 R9）',
    product_type          TINYINT      NOT NULL COMMENT 'djs_product_type 1=自产 / 2=外购 / 3=礼盒（doc/11 §2.6 R10）',
    product_unit          VARCHAR(16)  NOT NULL COMMENT '计量单位（doc/11 §2.6 R11）',
    product_spec          VARCHAR(64)  NULL COMMENT '规格（doc/11 §2.6 R12）',
    plot_id               BIGINT       NULL COMMENT '蔬菜来源地块 ID（doc/11 §2.6 R13）',
    ear_no                VARCHAR(32)  NULL COMMENT '猪肉来源耳号（doc/11 §2.6 R14）',
    product_sort          INT          NOT NULL DEFAULT 1 COMMENT '产品序号 1 开始自增（doc/11 §2.6 R15）',
    product_weight        DECIMAL(12,3) NOT NULL COMMENT '产品重量 kg（doc/11 §2.6 R16）',
    store_id              BIGINT       NULL COMMENT '需求门店 FK → t_md_store.id（doc/11 §2.6 R17）',
    produce_time          DATETIME     NOT NULL COMMENT '生产时间到时分秒（doc/11 §2.6 R18）',
    is_delivery_check     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否发货清点 djs_yes_no 1=是/0=否（doc/11 §2.6 R19，WMS-SHIP-001 推进）',
    delivery_check_time   DATETIME     NULL COMMENT '发货清点时间（doc/11 §2.6 R20）',
    is_arrival_confirm    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否到货确认 djs_yes_no 1=是/0=否（doc/11 §2.6 R21）',
    arrival_confirm_time  DATETIME     NULL COMMENT '到货确认时间（doc/11 §2.6 R22）',
    white_bar_id          BIGINT       NULL COMMENT '内部分割时关联白条 FK → t_warehouse_bar_info.id（doc/11 §2.6 R23）',
    material_id           BIGINT       NULL COMMENT '原材料 ID（doc/11 §2.6 R24）',
    material_consume      DECIMAL(12,3) NULL COMMENT '原材料耗用 kg（doc/11 §2.6 R25）',
    supplier_id           BIGINT       NULL COMMENT '外购供应商 ID（doc/11 §2.6 R26）',
    produce_location      TINYINT      NOT NULL DEFAULT 1 COMMENT '生产位置 djs_produce_location 1=仓库/2=门店（doc/11 §2.6 R27）',
    deliver_type          TINYINT      NULL COMMENT '发货方式 djs_deliver_type 1=发货/2=邮寄/3=销售（doc/11 §2.6 R28）',
    pack_status           VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'djs_pack_status pending/packed/shipped_out（本 ticket 扩展字段）',
    proof_oss_ids         VARCHAR(500) NULL COMMENT '凭证图 OSS IDs CSV（biz_type=warehouse_pack_proof，本 ticket 扩展字段）',
    trace_code            VARCHAR(64)  NULL COMMENT '追溯码（D11 TRC-CORE-001 回填，hook 字段）',
    create_dept           BIGINT       NULL COMMENT '创建部门',
    create_by             BIGINT       NULL COMMENT '创建者',
    create_time           DATETIME     NULL COMMENT '创建时间',
    update_by             BIGINT       NULL COMMENT '更新者',
    update_time           DATETIME     NULL COMMENT '更新时间',
    remark                VARCHAR(500) NULL COMMENT '备注',
    del_flag              CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique            BIGINT       NOT NULL DEFAULT 0 COMMENT '软删唯一性辅助列',
    PRIMARY KEY (id),
    UNIQUE KEY uk_produce_no (tenant_id, produce_no, del_unique),
    KEY idx_product (tenant_id, product_id, produce_date),
    KEY idx_store (tenant_id, store_id, produce_date),
    KEY idx_white_bar (tenant_id, white_bar_id),
    KEY idx_pack_status (tenant_id, pack_status, del_flag),
    KEY idx_produce_time (tenant_id, produce_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货产品生产信息表（doc/11 §2.6，WMS-PACK-001）';

-- ------------------------------------------------------------
-- 2. 字典 seed
--    dict_id：102240 / 102250 / 102260（102200/102210/102220/102230 已被 WMS-PIG-002 占）
--    dict_code：1022400-1022499 / 1022500-1022599 / 1022600-1022699
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark) VALUES
    (102240, '1001', '打包状态',     'djs_pack_status',      1, NOW(), 'WMS-PACK-001'),
    (102250, '1001', '生产位置',     'djs_produce_location', 1, NOW(), 'WMS-PACK-001 doc/11 §2.6 R27'),
    (102260, '1001', '发货方式',     'djs_deliver_type',     1, NOW(), 'WMS-PACK-001 doc/11 §2.6 R28');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    -- djs_pack_status 3 态
    (1022400, '1001', 0, '待打包',     'pending',     'djs_pack_status', '', 'info',    'Y', 1, NOW()),
    (1022401, '1001', 1, '已打包',     'packed',      'djs_pack_status', '', 'success', 'N', 1, NOW()),
    (1022402, '1001', 2, '已出库待发货', 'shipped_out', 'djs_pack_status', '', 'warning', 'N', 1, NOW()),
    -- djs_produce_location
    (1022500, '1001', 0, '仓库', '1', 'djs_produce_location', '', 'primary', 'Y', 1, NOW()),
    (1022501, '1001', 1, '门店', '2', 'djs_produce_location', '', 'success', 'N', 1, NOW()),
    -- djs_deliver_type
    (1022600, '1001', 0, '发货', '1', 'djs_deliver_type', '', 'primary', 'Y', 1, NOW()),
    (1022601, '1001', 1, '邮寄', '2', 'djs_deliver_type', '', 'warning', 'N', 1, NOW()),
    (1022602, '1001', 2, '销售', '3', 'djs_deliver_type', '', 'success', 'N', 1, NOW());

-- ------------------------------------------------------------
-- 3. sys_menu seed 9220-9235
--    M2 父 9220 / 4 mp 子（9221-9224）/ C 父 9230 admin 列表 / 2 admin 子（9231/9235）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- M2 父菜单（仅占位，mp 子菜单挂于此）
    (9220, '打包工序', 9000, 22, '#', '', '',
     1, 0, 'M', '0', '0', '', 'pack', 1, NOW(), 'WMS-PACK-001 mp'),

    -- mp 4 业态权限
    (9221, 'mp 蔬菜打包', 9220, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:pack:veg', '#', 1, NOW(), 'WMS-PACK-001 mp'),
    (9222, 'mp 礼盒打包', 9220, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:pack:gift', '#', 1, NOW(), 'WMS-PACK-001 mp'),
    (9223, 'mp 干货打包', 9220, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:pack:dry', '#', 1, NOW(), 'WMS-PACK-001 mp'),
    (9224, 'mp 芹菜打包', 9220, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:pack:celery', '#', 1, NOW(), 'WMS-PACK-001 mp'),

    -- admin 只读列表 C 父
    (9230, '生产记录', 9000, 23, 'production', 'djs-warehouse/production/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:production:list', 'documentation', 1, NOW(), 'WMS-PACK-001 admin 只读'),

    -- admin 2 子权限
    (9231, '生产记录查询', 9230, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:production:list', '#', 1, NOW(), 'WMS-PACK-001 admin'),
    (9235, '生产记录导出', 9230, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:production:export', '#', 1, NOW(), 'WMS-PACK-001 admin');

-- ------------------------------------------------------------
-- 4. role_menu 白名单绑定（同 D9X-MP-002 / V202606091000 范式）
--    role_id NOT IN (1, 101) — 保留 superadmin/system_admin（perms 通配，无需 sys_role_menu 行）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id IN (9220, 9221, 9222, 9223, 9224, 9230, 9231, 9235);
