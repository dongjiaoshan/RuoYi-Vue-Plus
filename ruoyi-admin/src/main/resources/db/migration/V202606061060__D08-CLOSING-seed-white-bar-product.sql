-- ============================================================
-- D08-CLOSING 白条产品 seed
--
-- 屠宰燎毛工序（WMS-PIG-001）出库时 stock_flow.product_id NOT NULL，
-- 但白条原本无独立产品主数据 → 旧实现写 0 占位制造追溯链路 dead-end。
-- 本 seed 灌入唯一一行白条产品主数据，PigBurnRecordServiceImpl 启动时按
-- product_id='PROD-WHITE-BAR-01' 查得 id 后回填到 stock_flow。
--
-- product_type=1 自产 / belong_type='white_bar' / product_attr=1 生产产品
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO t_warehouse_product_info
    (id, tenant_id, product_id, product_name, product_type, product_unit,
     belong_type, product_attr, product_status, is_delivery, is_buy_out,
     del_flag, del_unique, create_time)
VALUES
    (100000000000000001, '1001', 'PROD-WHITE-BAR-01', '白条·猪肉', 1, 'kg',
     'white_bar', 1, 0, 0, 0,
     '0', 0, NOW())
ON DUPLICATE KEY UPDATE
    product_name = VALUES(product_name),
    product_type = VALUES(product_type),
    product_unit = VALUES(product_unit),
    belong_type  = VALUES(belong_type),
    product_attr = VALUES(product_attr),
    product_status = VALUES(product_status),
    is_delivery  = VALUES(is_delivery),
    is_buy_out   = VALUES(is_buy_out);
