-- ============================================================
-- STORE-SPLIT-STOREID-001 (STORE-PERM-001)  拆单共表加门店列 + 隔离
-- ============================================================
-- t_warehouse_product_inhouse 是跨域共表：warehouse 域分割(source='warehouse')与
-- 门店域拆单(source='store')共用。门店拆单需按门店隔离 → 加 store_id 列纳入白名单。
--
-- 回填口径（已与 Kevin 确认，历史脏数据知情）：
--   现有 source='store' 行无任何可推断门店的线索 ——
--     · white_bar_id 多为 NULL（且白条不带门店维度）
--     · create_by 全为 admin(1)、create_dept=100（非门店维度）
--     · 无 demand / order 外键带 store_id
--   故无法回填，统一留 NULL。source='warehouse' 行本就不属任何门店，亦留 NULL。
--   留 NULL 的影响：门店拆单列表 WHERE store_id=? 会排除这些历史行（符合预期 ——
--   旧拆单数据无门店归属，新拆单从此带 store_id）。
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE t_warehouse_product_inhouse
    ADD COLUMN store_id BIGINT NULL COMMENT '门店 ID（仅 source=store 的门店拆单行有值；warehouse 行 / 历史行 NULL）' AFTER source;

-- 历史 source='store' 行无门店关联线索，统一留 NULL（不回填）。无 UPDATE。

ALTER TABLE t_warehouse_product_inhouse
    ADD KEY idx_store_id (store_id);
