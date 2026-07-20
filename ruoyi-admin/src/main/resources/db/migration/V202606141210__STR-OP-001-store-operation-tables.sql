-- ============================================================
-- STR-OP-001  门店产品关联 + 经营明细（销售流水手录 + Excel 导入）
-- ============================================================
-- baseline SYS-INIT-001 V202605200904 已建两表占位，字段陈旧（与 doc/11 §3.3/§3.4 权威全不一致）
-- + 0 行数据 → DROP + CREATE 重建（无数据迁移负担，参 WMS-MD-002 / WMS-STOCK-001 范式）
-- doc/11 §3.3 t_store_product_relation / §3.4 t_store_sale_record（瘦身版，V1 不做交易）
-- ADR-0004 字典 seed 规范 / ADR-0003 tenant_id VARCHAR(20) DEFAULT '1001'
-- ============================================================

-- ------------------------------------------------------------
-- 1. t_store_product_relation 门店产品关联（el-transfer 配「门店能卖哪些 SKU」）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_store_product_relation;
CREATE TABLE t_store_product_relation (
    id            BIGINT       NOT NULL COMMENT 'snowflake',
    store_id      BIGINT       NOT NULL COMMENT 'FK t_md_store.id 门店',
    product_id    BIGINT       NOT NULL COMMENT 'FK t_warehouse_product_info.id 产品',
    is_active     TINYINT      NOT NULL DEFAULT 1 COMMENT 'djs_active_status 1=启用/2=停用',
    tenant_id     VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    create_dept   BIGINT                              COMMENT '创建部门',
    create_by     BIGINT                              COMMENT '创建者',
    create_time   DATETIME                            COMMENT '创建时间',
    update_by     BIGINT                              COMMENT '更新者',
    update_time   DATETIME                            COMMENT '更新时间',
    remark        VARCHAR(500)                        COMMENT '备注',
    del_flag      CHAR(1)      NOT NULL DEFAULT '0'   COMMENT '删除标志（0 存在 / 1 删除）',
    del_unique    BIGINT       NOT NULL DEFAULT 0     COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_product (tenant_id, store_id, product_id, del_unique),
    KEY idx_store (store_id, is_active, del_flag),
    KEY idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店产品关联（STR-OP-001）';

-- ------------------------------------------------------------
-- 2. t_store_sale_record 门店销售流水（V1 手录 + Excel 导入，不做交易自动化）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_store_sale_record;
CREATE TABLE t_store_sale_record (
    id            BIGINT        NOT NULL COMMENT 'snowflake',
    store_id      BIGINT        NOT NULL COMMENT 'FK t_md_store.id',
    product_id    BIGINT        NOT NULL COMMENT 'FK t_warehouse_product_info.id',
    product_name  VARCHAR(128)  NOT NULL COMMENT '冗余产品名（导入/快照）',
    sale_date     DATE          NOT NULL COMMENT '销售日期（只到天）',
    sale_qty      DECIMAL(12,3) NOT NULL COMMENT '销售数量',
    sale_unit     VARCHAR(16)   NOT NULL COMMENT '单位（冗余自 product）',
    sale_amount   DECIMAL(10,2) NOT NULL COMMENT '销售总额 元',
    operator_id   BIGINT        NOT NULL COMMENT 'FK sys_user.user_id 手录店员',
    source        VARCHAR(16)   NOT NULL DEFAULT 'manual' COMMENT 'djs_sale_source manual=手录/excel_import',
    tenant_id     VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    create_dept   BIGINT                               COMMENT '创建部门',
    create_by     BIGINT                               COMMENT '创建者',
    create_time   DATETIME                             COMMENT '创建时间',
    update_by     BIGINT                               COMMENT '更新者',
    update_time   DATETIME                             COMMENT '更新时间',
    remark        VARCHAR(500)                         COMMENT '备注',
    del_flag      CHAR(1)       NOT NULL DEFAULT '0'   COMMENT '删除标志（0 存在 / 1 删除）',
    del_unique    BIGINT        NOT NULL DEFAULT 0     COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    KEY idx_store_date (store_id, sale_date),
    KEY idx_product (product_id),
    KEY idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店销售流水（STR-OP-001 V1 手录 + Excel 导入）';

-- ------------------------------------------------------------
-- 3. 字典 seed（ADR-0004 命名 djs_* / dict_value string）
-- ------------------------------------------------------------
-- djs_active_status：产品关联启用状态（给 t_store_product_relation.is_active）
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_dept, create_time, remark) VALUES
    (920701, '1001', '产品关联状态', 'djs_active_status', NULL, NOW(), 't_store_product_relation.is_active');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_time, remark) VALUES
    (102452101, '1001', 0, '启用', '1', 'djs_active_status', '', 'primary', 'Y', NULL, NOW(), 'STR-OP-001'),
    (102452102, '1001', 1, '停用', '2', 'djs_active_status', '', 'danger',  'N', NULL, NOW(), 'STR-OP-001');

-- djs_sale_source：销售流水数据来源（给 t_store_sale_record.source）
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_dept, create_time, remark) VALUES
    (920702, '1001', '销售来源', 'djs_sale_source', NULL, NOW(), 't_store_sale_record.source');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_time, remark) VALUES
    (102452103, '1001', 0, '手录',      'manual',       'djs_sale_source', '', 'primary', 'Y', NULL, NOW(), 'STR-OP-001'),
    (102452104, '1001', 1, 'Excel导入', 'excel_import', 'djs_sale_source', '', 'info',    'N', NULL, NOW(), 'STR-OP-001');
