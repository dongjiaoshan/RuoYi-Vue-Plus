-- ============================================================
-- STR-SPLIT-001  门店白条分割  inhouse 表加 source 列
-- ============================================================
-- 跨域共表：t_warehouse_product_inhouse 是 warehouse 域过程产品表（WMS-PIG-002 建），
-- 门店再分（本 ticket）让 store 模块也往里写行，用 source 列区分：
--   warehouse = 仓库分割（WMS-PIG-002 / WMS-VEG-001 产出，历史行）
--   store     = 门店再分（STR-SPLIT-001 录入）
-- 只 ALTER ADD COLUMN，不 DROP / 重建（有历史数据 + 仓库分割链路在用）
-- ============================================================
SET NAMES utf8mb4;

-- 1. 加 source 列（NOT NULL DEFAULT 'warehouse'，放 cut_part 后与实体字段顺序对齐）
ALTER TABLE t_warehouse_product_inhouse
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'warehouse'
        COMMENT '来源 warehouse=仓库分割/store=门店再分' AFTER cut_part;

-- 2. 历史行回填（DEFAULT 已兜底，显式 UPDATE 一遍防 NULL/空串）
UPDATE t_warehouse_product_inhouse SET source = 'warehouse' WHERE source IS NULL OR source = '';

-- 3. admin 列表按 source 过滤的索引（含 tenant_id 对齐多租户）
ALTER TABLE t_warehouse_product_inhouse
    ADD KEY idx_source (tenant_id, source);
