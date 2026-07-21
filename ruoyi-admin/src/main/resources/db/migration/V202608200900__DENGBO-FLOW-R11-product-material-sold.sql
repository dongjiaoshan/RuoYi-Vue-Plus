-- 流程性问题 row11：产品配置新增「是否原材料外售」字段（复用字典 djs_yes_no）。
-- 为「是」时该生产产品到店后，门店盘点/退回按其关联原材料 product_material 口径处理（row12/13 下游逻辑另行）。
ALTER TABLE t_warehouse_product_info
    ADD COLUMN is_material_sold TINYINT NOT NULL DEFAULT 0
        COMMENT '字典 djs_yes_no：是否原材料外售 1=是 / 0=否' AFTER is_buy_out;
