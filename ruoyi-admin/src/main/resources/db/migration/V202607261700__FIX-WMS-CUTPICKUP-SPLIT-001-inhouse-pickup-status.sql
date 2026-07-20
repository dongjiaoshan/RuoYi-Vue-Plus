-- ============================================================
-- FIX-WMS-CUTPICKUP-SPLIT-001  分割白条领用按燎毛产出行拆条
-- ============================================================
-- 测试反馈（何涛，已提多次）：一头白条燎毛录入两个半只（如「半只」110kg +「半扇」90kg）时，
-- 白条领用页应展示两条记录、可分两次领用；现状只按整猪一条、一次领满。
--
-- 口径（Kevin 2026-06-26 拍板，选项 a「按半只/产出行拆」）：
--   · 领用「卡片」按燎毛产出行（t_warehouse_product_inhouse WHERE white_bar_id 非空）逐条展示；
--   · 每条可单独领用进分割车间，bar 仍 in_stock，剩余未领行继续显示（分两次领）；
--   · 一头白条所有产出行都领满 → 才推 bar in_stock→pending_cut + 建「一个整猪」cut_record
--     （pickup_weight = 各行领用过磅之和）→ 下游分割/损耗/追溯仍按整猪聚合，零改动。
--
-- 本迁移：给共表加两列，仅「白条领用」读写，对门店拆单 / 蔬菜处理等既有写入路径零影响（默认值兜底）。
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE t_warehouse_product_inhouse
    ADD COLUMN pickup_status TINYINT NOT NULL DEFAULT 0
        COMMENT '燎毛产出行白条领用状态 0未领/1已领（仅 white_bar_id 非空的燎毛产出行用；其余行恒 0 无意义）' AFTER white_bar_id,
    ADD COLUMN pickup_weight DECIMAL(10,3) NULL
        COMMENT '该产出行白条领用过磅重量 kg（领用进分割车间时现场过磅；整猪 cut_record.pickup_weight=各行之和）' AFTER pickup_status;

-- 历史行 / 非白条领用行默认 pickup_status=0：在库白条（bar.status=in_stock）的产出行均视为「未领」，
-- 领用列表 WHERE bar.status=in_stock AND pickup_status=0 据此出条，符合预期（已领满的 bar 早已离 in_stock 态）。
