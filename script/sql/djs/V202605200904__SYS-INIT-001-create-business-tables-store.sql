-- ============================================================
-- SYS-INIT-001 业务表 DDL — 门店域 STR (含会员)
-- 生成时间: 2026-05-20
-- 表数: 6 张
--   t_store_product_relation / t_store_sale_record / t_store_check_record /
--   t_store_member / t_store_member_consumption / t_store_return
-- 注: 追溯 TRC-* 的 2 张表（t_warehouse_trace_code / t_warehouse_trace_event）已放在 warehouse 文件
--     数据驾驶舱 DSH-* 全部复用其他模块数据，无新表
-- 强制规范: tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' / UNIQUE 含 del_unique 生成列 / 审计字段对齐 ruoyi
-- 引用: doc/05-架构文档-ruoyi.md §6, doc/06-实现描述.md 第 5 章 (STR-*) + 第 6/7 章, doc/_db-changes.md
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1. t_store_product_relation（门店产品关联，STR-OP-001）
-- M:N 关联表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_store_product_relation;
CREATE TABLE t_store_product_relation (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  store_id        BIGINT       NOT NULL COMMENT '门店 ID',
  product_id      BIGINT       NOT NULL COMMENT '产品 ID',
  shelf_status    TINYINT      NULL DEFAULT 1 COMMENT '上架状态 1=上架 0=下架',
  shelf_price     DECIMAL(10,2) NULL COMMENT '上架价格 元',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_product (tenant_id, store_id, product_id, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店产品关联（STR-OP-001）';

-- ------------------------------------------------------------
-- 2. t_store_sale_record（门店销售流水，STR-OP-001 ★ 待 P1 #7 数据来源）
-- V1 手录，V2 接 POS/微信支付回调
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_store_sale_record;
CREATE TABLE t_store_sale_record (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  sale_no         VARCHAR(32)  NOT NULL COMMENT '销售单号',
  store_id        BIGINT       NOT NULL COMMENT '门店 ID',
  product_id      BIGINT       NOT NULL COMMENT '产品 ID',
  sale_date       DATETIME     NOT NULL COMMENT '销售日期',
  quantity        DECIMAL(12,2) NOT NULL COMMENT '销售数量',
  unit_price      DECIMAL(10,2) NULL COMMENT '单价 元',
  total_amount    DECIMAL(12,2) NULL COMMENT '总额 元',
  member_id       BIGINT       NULL COMMENT '会员 ID（可选）',
  data_source     VARCHAR(16)  NULL DEFAULT 'manual' COMMENT '数据来源：manual=手录/pos=POS/wxpay=微信支付（V1 仅 manual）',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '录入人',
  create_time     DATETIME     NULL COMMENT '录入时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_sale_no (tenant_id, sale_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_store_date (store_id, sale_date),
  KEY idx_product (product_id),
  KEY idx_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店销售流水（STR-OP-001 V1 手录）';

-- ------------------------------------------------------------
-- 3. t_store_check_record（门店盘点，STR-STOCK-001）
-- 与仓库盘点 t_warehouse_check_record 同结构，单独表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_store_check_record;
CREATE TABLE t_store_check_record (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  check_no        VARCHAR(32)  NOT NULL COMMENT '盘点单号',
  store_id        BIGINT       NOT NULL COMMENT '门店 ID',
  check_date      DATE         NOT NULL COMMENT '盘点日期',
  check_status    VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT '状态 字典 djs_check_status：draft/in_progress/completed',
  product_id      BIGINT       NULL COMMENT '产品 ID',
  sys_stock       DECIMAL(12,2) NULL COMMENT '系统库存',
  check_stock     DECIMAL(12,2) NULL COMMENT '实盘数',
  diff_stock      DECIMAL(12,2) NULL COMMENT '差异',
  operator_id     BIGINT       NULL COMMENT '盘点人',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '录入人',
  create_time     DATETIME     NULL COMMENT '录入时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_check_no (tenant_id, check_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_store_date (store_id, check_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店盘点（STR-STOCK-001）';

-- ------------------------------------------------------------
-- 4. t_store_member（会员档案，STR-MEMBER-001 v1.1 大幅裁剪）
-- 会员编号规则 10001 起，SYS-INFRA-004 生成
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_store_member;
CREATE TABLE t_store_member (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  member_no       VARCHAR(32)  NOT NULL COMMENT '会员编号（10001 风格 SYS-INFRA-004 生成）',
  member_name     VARCHAR(64)  NULL COMMENT '会员姓名',
  phone           VARCHAR(20)  NOT NULL COMMENT '手机号',
  member_level    VARCHAR(16)  NULL COMMENT '会员等级',
  join_date       DATE         NULL COMMENT '入会日期',
  store_id        BIGINT       NULL COMMENT '所属门店 ID',
  member_tags     VARCHAR(255) NULL COMMENT '会员标签（逗号分隔）',
  member_status   TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=正常 0=停用',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_no (tenant_id, member_no, del_unique),
  UNIQUE KEY uk_phone (tenant_id, phone, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员档案（STR-MEMBER-001 v1.1 裁剪后）';

-- ------------------------------------------------------------
-- 5. t_store_member_consumption（会员手动消费记录，STR-MEMBER-001）
-- v1.1 裁剪: 去掉 amount_auto / payment_method / wx_pay_transaction_id 等"交易自动化"字段
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_store_member_consumption;
CREATE TABLE t_store_member_consumption (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  member_id         BIGINT       NOT NULL COMMENT '会员 ID',
  consume_date      DATETIME     NOT NULL COMMENT '消费日期',
  store_id          BIGINT       NULL COMMENT '门店 ID',
  sku               VARCHAR(64)  NULL COMMENT '商品 SKU/产品编码',
  product_id        BIGINT       NULL COMMENT '产品 ID',
  quantity          DECIMAL(12,2) NULL COMMENT '数量',
  amount_manual     DECIMAL(12,2) NULL COMMENT '手填金额 元',
  notes             VARCHAR(500) NULL COMMENT '备注',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注（兼容字段）',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_member_date (member_id, consume_date),
  KEY idx_store (store_id),
  KEY idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员手动消费记录（STR-MEMBER-001 V1 手录）';

-- ------------------------------------------------------------
-- 6. t_store_return（门店退回管理，STR-RETURN-001）
-- 与 t_warehouse_return_product 区分：本表记录从顾客退回门店的流水
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_store_return;
CREATE TABLE t_store_return (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  return_no           VARCHAR(32)  NOT NULL COMMENT '退回单号',
  return_direction    VARCHAR(32)  NOT NULL COMMENT '退回方向：customer_to_store=顾客→门店 / store_to_warehouse=门店→仓库 / warehouse_to_supplier=仓库→供应商',
  store_id            BIGINT       NULL COMMENT '门店 ID',
  product_id          BIGINT       NOT NULL COMMENT '产品 ID',
  return_quantity     DECIMAL(12,2) NOT NULL COMMENT '退回数量',
  return_reason       VARCHAR(255) NULL COMMENT '退回原因',
  trace_code          VARCHAR(64)  NULL COMMENT '已贴追溯码（如有）',
  return_date         DATETIME     NOT NULL COMMENT '退回日期',
  member_id           BIGINT       NULL COMMENT '退回会员 ID（顾客退回时）',
  operator_id         BIGINT       NULL COMMENT '操作人',
  create_dept           BIGINT       NULL COMMENT '创建部门',
  create_by           BIGINT       NULL COMMENT '录入人',
  create_time         DATETIME     NULL COMMENT '录入时间',
  update_by           BIGINT       NULL COMMENT '更新人',
  update_time         DATETIME     NULL COMMENT '更新时间',
  del_flag            CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark              VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_return_no (tenant_id, return_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_store_date (store_id, return_date),
  KEY idx_product (product_id),
  KEY idx_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店退回管理（STR-RETURN-001 多方向退回）';

SET FOREIGN_KEY_CHECKS = 1;
