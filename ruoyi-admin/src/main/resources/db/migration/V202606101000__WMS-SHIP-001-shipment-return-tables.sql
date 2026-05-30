-- ============================================================
-- WMS-SHIP-001 发货月台 + 退货管理 — 建表
--
-- 表 1：t_warehouse_shipment 发货流水主表（D2 baseline 无；本 ticket 新建）
-- 表 2：t_warehouse_return_product 退货管理表（D2 baseline 有占位，字段偏离 doc/11 §2.11 → DROP 重建）
--
-- 关联：
--   - shipment.demand_id  → t_warehouse_demand_manage.id
--   - shipment.store_id   → t_md_store.id
--   - return.store_id     → t_md_store.id
--   - return.product_id   → t_warehouse_product_info.id
--   - return.confirm_user → sys_user.user_id
--
-- 跨层契约（skill: coder-djs-cross-layer-contract §契约 3）：
--   - tenant_id VARCHAR(20) DEFAULT '1001'
--   - 6 审计字段（create_by/create_time/update_by/update_time/create_dept/del_flag）
--   - del_unique BIGINT NOT NULL DEFAULT 0
--   - UNIQUE 必含 tenant_id + del_unique
--
-- 退货方向（Kevin 决策 a）：
--   V1 仅完整实现 "门店→仓库"（store_to_warehouse），联动 stock_flow；
--   其他 2 方向（customer_to_store / warehouse_to_supplier）仅录入，**不联动** stock_flow。
-- ============================================================

SET NAMES utf8mb4;

-- ============== 1. 发货流水主表（新建）==============
CREATE TABLE t_warehouse_shipment (
    id                BIGINT       NOT NULL                  COMMENT '主键（snowflake）',
    tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001'   COMMENT '租户/农场 ID',
    shipment_no       VARCHAR(32)  NOT NULL                  COMMENT '发货单号（SHIP_NO 业务码 S+yyyyMMdd+seq4）',
    demand_id         BIGINT       NOT NULL                  COMMENT 'FK → t_warehouse_demand_manage.id',
    product_type      VARCHAR(32)  NOT NULL                  COMMENT '业态字典 djs_demand_product_type：white_bar/vegetable/gift_box/other',
    store_id          BIGINT       NOT NULL                  COMMENT '目的门店 FK → t_md_store.id',
    ship_date         DATE         NOT NULL                  COMMENT '发货日期',
    ship_quantity     DECIMAL(12,3) NOT NULL                 COMMENT '本次发货数量（kg / 头 / 盒）',
    ship_unit         VARCHAR(16)  NOT NULL                  COMMENT '单位',
    deliver_type      TINYINT      NOT NULL                  COMMENT '发货方式字典 djs_deliver_type 1=发货/2=邮寄/3=销售（D8 seed）',
    -- 礼盒发货专用
    receiver_name     VARCHAR(64)  DEFAULT NULL              COMMENT '收件人姓名（礼盒邮寄场景）',
    receiver_phone    VARCHAR(32)  DEFAULT NULL              COMMENT '收件人电话',
    receiver_address  VARCHAR(255) DEFAULT NULL              COMMENT '收件地址',
    -- 状态
    shipment_status   VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT '状态字典 djs_shipment_status：pending/checking/shipped/delivered',
    checker_id        BIGINT       DEFAULT NULL              COMMENT '清点员 user_id（→ sys_user.user_id）',
    check_time        DATETIME     DEFAULT NULL              COMMENT '清点完成时间',
    proof_oss_ids     VARCHAR(500) DEFAULT NULL              COMMENT '清点凭证图 OSS IDs CSV（bizType=warehouse_shipment_proof）',
    -- 审计 + 软删
    create_dept       BIGINT       DEFAULT NULL              COMMENT '创建部门',
    create_by         BIGINT       DEFAULT NULL              COMMENT '创建者',
    create_time       DATETIME     DEFAULT NULL              COMMENT '创建时间',
    update_by         BIGINT       DEFAULT NULL              COMMENT '更新者',
    update_time       DATETIME     DEFAULT NULL              COMMENT '更新时间',
    remark            VARCHAR(500) DEFAULT NULL              COMMENT '备注',
    del_flag          CHAR(1)      NOT NULL DEFAULT '0'      COMMENT '删除标志 0=未删 1=已删',
    del_unique        BIGINT       NOT NULL DEFAULT 0        COMMENT '软删唯一辅助列（DjsBaseServiceImpl#softDelete 同步 id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_no (tenant_id, shipment_no, del_unique),
    KEY idx_demand       (tenant_id, demand_id, del_flag),
    KEY idx_store_date   (tenant_id, store_id, ship_date),
    KEY idx_status       (tenant_id, shipment_status, del_flag),
    KEY idx_checker_time (tenant_id, checker_id, check_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发货流水主表（WMS-SHIP-001 D10）';

-- ============== 2. 退货管理表（DROP 重建，对齐 doc/11 §2.11 + 决策 a 加 return_direction）==============
-- 原表（D2 baseline）字段缺失 + 类型偏差：
--   缺 apply_time（doc/11 R7） / product_name（R9）/ confirm_time（R13）/ proof_oss_ids
--   类型差 DECIMAL(12,2) vs doc/11 DECIMAL(12,3)
-- 全量 DROP + CREATE（D2 baseline 表数据为 0，安全）。
DROP TABLE IF EXISTS t_warehouse_return_product;
CREATE TABLE t_warehouse_return_product (
    id                BIGINT       NOT NULL                  COMMENT '主键（snowflake）',
    tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001'   COMMENT '租户/农场 ID',
    return_no         VARCHAR(32)  NOT NULL                  COMMENT '退货单号（业务码 RET+yyyyMMdd+4，inline）',
    store_id          BIGINT       DEFAULT NULL              COMMENT '退货门店 FK → t_md_store.id（其他方向时可空）',
    apply_time        DATETIME     NOT NULL                  COMMENT '退货申请时间',
    product_id        BIGINT       NOT NULL                  COMMENT '产品 FK → t_warehouse_product_info.id',
    product_name      VARCHAR(128) NOT NULL                  COMMENT '产品名称（冗余，便于列表展示）',
    return_weight     DECIMAL(12,3) NOT NULL                 COMMENT '退货重量',
    confirm_weight    DECIMAL(12,3) DEFAULT NULL             COMMENT '确认重量（admin 端复核后）',
    confirm_user      BIGINT       DEFAULT NULL              COMMENT '确认人 user_id → sys_user.user_id',
    confirm_time      DATETIME     DEFAULT NULL              COMMENT '确认时间',
    is_confirm        TINYINT      NOT NULL DEFAULT 0        COMMENT '是否已确认 1=已确认 0=待确认',
    return_reason     VARCHAR(255) DEFAULT NULL              COMMENT '退货原因',
    return_direction  VARCHAR(32)  NOT NULL DEFAULT 'store_to_warehouse'
                                                             COMMENT '退货方向字典 djs_return_direction（V1 仅 store_to_warehouse 联动 stock_flow，其他 2 方向占位）',
    return_status     VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT '退货状态字典 djs_return_status：pending/confirmed/rejected',
    proof_oss_ids     VARCHAR(500) DEFAULT NULL              COMMENT '退货凭证图 OSS IDs CSV（bizType=warehouse_return_proof）',
    -- 审计 + 软删
    create_dept       BIGINT       DEFAULT NULL              COMMENT '创建部门',
    create_by         BIGINT       DEFAULT NULL              COMMENT '创建者（mp 工人 / admin 录入）',
    create_time       DATETIME     DEFAULT NULL              COMMENT '创建时间',
    update_by         BIGINT       DEFAULT NULL              COMMENT '更新者',
    update_time       DATETIME     DEFAULT NULL              COMMENT '更新时间',
    remark            VARCHAR(500) DEFAULT NULL              COMMENT '备注',
    del_flag          CHAR(1)      NOT NULL DEFAULT '0'      COMMENT '删除标志',
    del_unique        BIGINT       NOT NULL DEFAULT 0        COMMENT '软删唯一辅助列',
    PRIMARY KEY (id),
    UNIQUE KEY uk_return_no (tenant_id, return_no, del_unique),
    KEY idx_store_apply  (tenant_id, store_id, apply_time),
    KEY idx_product      (tenant_id, product_id, del_flag),
    KEY idx_status       (tenant_id, return_status, del_flag),
    KEY idx_direction    (tenant_id, return_direction, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退货管理表（WMS-SHIP-001 D10，3 方向 V1 仅 store_to_warehouse 联动 stock_flow）';

-- ============== 3. product_production 扩列：delivery_check_time / arrival_confirm_time ==============
-- doc/06 §F-WMS-03 行 274 + doc/11 §2.6 R19-R20：发货确认 / 到货确认 时间戳字段
-- baseline SYS-INIT-001 漏建（仅有 is_delivery_check / is_arrival_confirm tinyint），本 ticket 补上
ALTER TABLE t_warehouse_product_production
    ADD COLUMN delivery_check_time DATETIME NULL COMMENT '发货清点时间（WMS-SHIP-001 ShipmentService 写入）'
        AFTER is_delivery_check,
    ADD COLUMN arrival_confirm_time DATETIME NULL COMMENT '门店到货确认时间（D14 STR-ARRIVE-001 写入）'
        AFTER is_arrival_confirm;

