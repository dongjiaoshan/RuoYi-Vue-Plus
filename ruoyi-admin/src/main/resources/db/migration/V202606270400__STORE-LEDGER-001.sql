-- ============================================================
-- STORE-LEDGER-001  门店经营流水盘点（原型 门店管理>门店盘点 重建为经营流水台账）
-- ============================================================
-- 客户口径：对仓库发货到门店的产品，进行期初 / 新入库 / 销售 / 赠送 / 退货 / 退回 / 损耗的录入与盘点。
--   新建当日盘点时把门店关联产品全 SKU 列出来逐行填，相当于门店自己的库存盘点。
-- 六列流水模型与旧 t_store_check_record（实盘 vs 系统差异两列）结构不符 → 新建独立台账表，旧表保留不动。
-- 公式（service 落库时算）：closing_qty = opening_qty + inbound_qty − sale_qty − gift_qty − return_qty − loss_qty。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_store_daily_ledger 门店经营流水盘点台账
--    一行 = 某门店某产品某盘点日的经营六列流水 + 期末库存。
--    UNIQUE(tenant_id, store_id, product_id, ledger_date, del_unique)：同门店同产品同日只一行，
--    软删后可重盘（del_unique 翻 id 解唯一冲突）。
-- ------------------------------------------------------------
CREATE TABLE t_store_daily_ledger (
    id              BIGINT        NOT NULL COMMENT '主键（雪花）',
    tenant_id       VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户号（V1 全 1001）',
    store_id        BIGINT        NOT NULL COMMENT '门店 FK → t_md_store.id',
    product_id      BIGINT        NOT NULL COMMENT '产品 FK → t_warehouse_product_info.id',
    ledger_date     DATE          NOT NULL COMMENT '盘点日期（到天）',
    opening_qty     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '期初库存（V1 手填，首次建账即期初）',
    inbound_qty     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '当日入库量（可用仓库发出量预填，口径=发出非实收）',
    sale_qty        DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '销售量（可由 t_store_sale_record 当日聚合预填）',
    gift_qty        DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '赠送量（手填）',
    return_qty      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '退货量（顾客退货，可由 t_store_return 当日聚合预填）',
    loss_qty        DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '损耗量（手填，未填按 0）',
    closing_qty     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '期末库存（service 算：期初+入库−销售−赠送−退货−损耗）',
    operator_id     BIGINT        NULL COMMENT '盘点人 user_id → sys_user.user_id（LoginHelper 注入）',
    remark          VARCHAR(500)  NULL COMMENT '备注',
    create_by       BIGINT        NULL COMMENT '创建者',
    create_time     DATETIME      NULL COMMENT '创建时间',
    update_by       BIGINT        NULL COMMENT '更新者',
    update_time     DATETIME      NULL COMMENT '更新时间',
    create_dept     BIGINT        NULL COMMENT '创建部门',
    del_flag        CHAR(1)       NOT NULL DEFAULT '0' COMMENT '软删标记（0 未删 / 1 已删）',
    del_unique      BIGINT        NOT NULL DEFAULT 0 COMMENT '软删唯一辅助列（未删=0，删除时写 id 解唯一冲突，DjsMetaObjectHandler 自动）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_product_date (tenant_id, store_id, product_id, ledger_date, del_unique),
    KEY idx_store_date (store_id, ledger_date),
    KEY idx_product_date (product_id, ledger_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店经营流水盘点台账（STORE-LEDGER-001）';
