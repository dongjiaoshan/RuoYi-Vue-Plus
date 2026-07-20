-- ============================================================
-- STORE-LEDGER-REALIGN-001  门店盘点录入对齐最新原型：拆「退货量」与「退回量」两列
-- ============================================================
-- 最新原型「门店盘点>新增当日盘点」矩阵把退货（顾客退货到门店）与退回（门店退回仓库）分成两列：
--   退货量 return_qty       = 顾客退货到门店（customer_to_store）
--   退回量 wh_return_qty     = 门店退回仓库（store_to_warehouse），原型中为只读
-- 期末公式更新：closing_qty = opening + inbound − sale − gift − return − wh_return − loss。
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE t_store_daily_ledger
    ADD COLUMN wh_return_qty DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '退回量（门店退回仓库，可由 t_store_return store_to_warehouse 当日聚合预填）'
    AFTER return_qty;
