-- ============================================================
-- DENGBO-R15 产品配置新增「产品别名」字段（非必填）
--
-- t_warehouse_product_info 加 product_alias VARCHAR(128) NULL，位于 product_spec 后。
-- admin 产品配置（编辑产品弹框）规格字段后展示，落库 + 可查。
-- ============================================================

ALTER TABLE t_warehouse_product_info
    ADD COLUMN product_alias VARCHAR(128) NULL COMMENT '产品别名' AFTER product_spec;
