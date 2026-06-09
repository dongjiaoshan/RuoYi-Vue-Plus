-- ============================================================
-- FIX-WMS-MP-VEGDOCK-001 外购果蔬月台收货 + 果蔬间入库（原型图 42/43）
--
-- 1. t_warehouse_veg_purchase 外购果蔬到货（收货 → 待入库 → 入库）
-- 2. 字典新建：djs_veg_purchase_status（pending/processing/done）
-- 3. 字典扩：djs_flow_type 新增 veg_purchase_in（外购果蔬入库）
-- 4. sys_menu seed 9099（蔬菜处理段 9090-9099 ADR-0006；mp F-node）
--
-- 口径（D-FIX-7 #4.2）：外购果蔬 = 采购到货一类；月台登记到货重量；自产链路 mp 复用 vegHandle/pending。
-- 独立于自产 t_warehouse_vegetable_handle，stock_flow 用专属 flow_type=veg_purchase_in，不混算。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_warehouse_veg_purchase 外购果蔬到货
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_veg_purchase;
CREATE TABLE t_warehouse_veg_purchase (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id       VARCHAR(20)   NOT NULL DEFAULT '1001',
    source          VARCHAR(200)  NULL                    COMMENT '到货来源说明（自由文本）',
    supplier_code   VARCHAR(64)   NULL                    COMMENT '供应商业务编码（SupplierPicker 选中值，可空）',
    supplier_name   VARCHAR(128)  NULL                    COMMENT '供应商名称（冗余回显）',
    crop_id         BIGINT        NOT NULL                COMMENT '作物 ID FK → t_plant_crop_info.id（外购果蔬品种）',
    crop_name       VARCHAR(64)   NULL                    COMMENT '作物名称（冗余）',
    product_id      BIGINT        NULL                    COMMENT '关联产品 ID FK → t_warehouse_product_info.id（belong_type=vegetable，入库写库存用）',
    location_id     BIGINT        NULL                    COMMENT '入库库位 ID FK → t_warehouse_location_info.id（入库确认时写入）',
    arrival_weight  DECIMAL(12,3) NOT NULL                COMMENT '到货重量(kg)',
    pending_weight  DECIMAL(12,3) NOT NULL DEFAULT 0      COMMENT '待入库量(kg) = 到货 - 实际入库',
    actual_weight   DECIMAL(12,3) NOT NULL DEFAULT 0      COMMENT '实际入库量(kg)',
    arrival_time    DATETIME      NOT NULL                COMMENT '到货时间',
    status          VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'djs_veg_purchase_status：pending/processing/done',
    proof_oss_ids   VARCHAR(500)  NULL                    COMMENT '凭证图 OSS IDs CSV（biz_type=warehouse_veg_purchase）',
    create_dept     BIGINT        NULL,
    create_by       BIGINT        NULL,
    create_time     DATETIME      NULL,
    update_by       BIGINT        NULL,
    update_time     DATETIME      NULL,
    remark          VARCHAR(500)  NULL,
    del_flag        CHAR(1)       NULL DEFAULT '0',
    del_unique      BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_veg_purchase (tenant_id, id, del_unique),
    KEY idx_crop_status (tenant_id, crop_id, status),
    KEY idx_arrival_time (tenant_id, arrival_time),
    KEY idx_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外购果蔬到货（FIX-WMS-MP-VEGDOCK-001 原型图 42/43；收货 → 待入库 → 果蔬间入库）';

-- ------------------------------------------------------------
-- 2. 字典新建 djs_veg_purchase_status（ADR-0004 djs_ 命名）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (102320, '1001', '外购果蔬入库状态', 'djs_veg_purchase_status', 1, NOW(), 'FIX-WMS-MP-VEGDOCK-001');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1023200, '1001', 0, '待入库', 'pending',    'djs_veg_purchase_status', '', 'info',    'Y', 1, NOW()),
    (1023201, '1001', 1, '入库中', 'processing', 'djs_veg_purchase_status', '', 'warning', 'N', 1, NOW()),
    (1023202, '1001', 2, '已入库', 'done',       'djs_veg_purchase_status', '', 'success', 'N', 1, NOW());

-- ------------------------------------------------------------
-- 3. 字典扩 djs_flow_type 新增 veg_purchase_in（外购果蔬入库）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (102450016, '1001', 16, '外购果蔬入库', 'veg_purchase_in', 'djs_flow_type', '', 'success', 'N', 1, NOW(), 'FIX-WMS-MP-VEGDOCK-001');

-- ------------------------------------------------------------
-- 4. sys_menu seed（仓库 9000-9999 / 蔬菜处理段 9090-9099 ADR-0006；mp 端 @SaCheckLogin 不 gate perm，
--    本 F-node 仅为 admin 角色绑定完整性，挂 9090 毛菜处理父下）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9099, '果蔬月台收货', 9090, 9, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:vegPurchase:receive', '#', 1, NOW(), 'FIX-WMS-MP-VEGDOCK-001 mp 外购果蔬收货/入库');
