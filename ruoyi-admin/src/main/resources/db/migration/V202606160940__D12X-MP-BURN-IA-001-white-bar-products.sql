-- ============================================================
-- D12X-MP-BURN-IA-001 白条产品类型规范化（燎毛入库按 4 类型分类）
--
-- 客户 §2.3 燎毛间要求：白条入库需按产品类型「整只 / 半只 / 猪头 / 猪蹄」分别入库。
-- 燎毛入库子页拉 productTypes 端点（belong_type='white_bar' + product_id 前缀
-- 'PROD-WHITE-BAR-' 的标准 4 行），每类型分别录重量 → INSERT product_inhouse + IN 流水。
--
-- 现有 seed 现状：
--   PROD-WHITE-BAR-01 白条·猪肉（V202606061060）→ 本 ticket 改名「白条·整只」（语义=完整白条）
--   PROD-WHITE-BAR-02 白条·猪头 / -03 白条·猪蹄（V202606071300）→ 保留
--   PROD-WHITE-BAR-04 白条·半只 → 本 ticket 新增
-- product_type=1 自产 / belong_type='white_bar' / product_attr=1 生产产品 / product_unit='kg'
-- ON DUPLICATE KEY 保证幂等（重跑不报 UNIQUE）。
-- ============================================================

SET NAMES utf8mb4;

-- PROD-WHITE-BAR-01：白条·猪肉 → 白条·整只（复用既有固定大数 id，仅改名）
UPDATE t_warehouse_product_info
   SET product_name = '白条·整只'
 WHERE product_id = 'PROD-WHITE-BAR-01'
   AND tenant_id = '1001'
   AND del_flag = '0';

-- PROD-WHITE-BAR-04：白条·半只（新增）
INSERT INTO t_warehouse_product_info
    (id, tenant_id, product_id, product_name, product_type, product_unit,
     belong_type, product_attr, product_status, is_delivery, is_buy_out,
     del_flag, del_unique, create_time)
VALUES
    (100000000000000004, '1001', 'PROD-WHITE-BAR-04', '白条·半只', 1, 'kg',
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
