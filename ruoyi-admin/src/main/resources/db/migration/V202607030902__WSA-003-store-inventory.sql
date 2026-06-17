-- WSA-003 门店独立库存表 t_store_inventory（仓库×门店全流程对齐 阶段0 F0-3）
-- 用途：门店盘点期初读「当前结存」、盘点完成回写「期末结存」（期初 = 上一期期末，自然兼容跨日空档）。
-- 最小模型：一门店一产品一行结存；V1 不实时改库存（销售/退回靠盘点对账），与「期末实盘」语义一致。
CREATE TABLE IF NOT EXISTS t_store_inventory (
    id          BIGINT       NOT NULL COMMENT '主键（雪花）',
    tenant_id   VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    store_id    BIGINT       NOT NULL COMMENT '门店 ID',
    product_id  BIGINT       NOT NULL COMMENT '产品 FK → t_warehouse_product_info.id',
    stock_qty   DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '门店结存数量/重量（盘点期末回写、期初读取）',
    create_dept BIGINT       NULL COMMENT '创建部门',
    create_by   BIGINT       NULL COMMENT '创建者',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新者',
    update_time DATETIME     NULL COMMENT '更新时间',
    del_flag    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique  BIGINT       NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_product (tenant_id, store_id, product_id, del_unique),
    KEY idx_store (tenant_id, store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店独立库存（盘点期初读/期末写，WSA-003）';
