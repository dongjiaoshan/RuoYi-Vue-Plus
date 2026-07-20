-- STR-RETURN-REBUILD-001（D14X，K4 退回联动外购入库）
-- 门店退回新增时联动 location_stock，需记录入库目标库位。
-- 列可空（历史登记行无此值）；新增走 Bo @NotNull 强制，编辑不可改（service 锁死）。

ALTER TABLE t_store_return
    ADD COLUMN location_id BIGINT NULL COMMENT '退回入库库位 FK→t_warehouse_location_info.id（K4 联动外购入库）' AFTER product_id;
