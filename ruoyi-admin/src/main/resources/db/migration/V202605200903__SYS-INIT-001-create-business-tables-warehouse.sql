-- ============================================================
-- SYS-INIT-001 业务表 DDL — 仓库域 WMS
-- 生成时间: 2026-05-20
-- 表数: 16 张（_db-changes 列出 16 张，无追溯码因移到 store 文件? 不，追溯码仍属仓库域）
-- 强制规范: tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' / UNIQUE 含 del_unique 生成列 / 审计字段对齐 ruoyi
-- typo 修复: 前缀空格 t_ warehouse_ -> t_warehouse_ / product_status x2 -> 合并 /
--           pig_code -> trace_code / pig_time -> trace_event (跨业态命名修正) /
--           farm_name 冗余 -> farm_id (改 JOIN)
-- 引用: doc/05-架构文档-ruoyi.md §6, doc/06-实现描述.md 第 4 章 (WMS-*) + 第 6 章 (TRC-*), doc/_db-changes.md
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 库位 + 库存（3 张）
-- ============================================================

-- ------------------------------------------------------------
-- 1. t_warehouse_location_info（库位信息表，WMS-MD-001）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_location_info;
CREATE TABLE t_warehouse_location_info (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  location_code   VARCHAR(32)  NOT NULL COMMENT '库位编码',
  location_name   VARCHAR(64)  NOT NULL COMMENT '库位名称',
  location_type   VARCHAR(16)  NULL COMMENT '库位类型 字典 location_type：冷藏/常温/干燥/冻品库/...',
  capacity        DECIMAL(12,2) NULL COMMENT '设计容量',
  capacity_unit   VARCHAR(16)  NULL COMMENT '容量单位（kg/件）',
  current_load    DECIMAL(12,2) NULL DEFAULT 0 COMMENT '当前占用',
  location_status TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=启用 0=停用',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_location_code (tenant_id, location_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_location_type (location_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库位信息表（WMS-MD-001，typo 修复前缀空格）';

-- ------------------------------------------------------------
-- 2. t_warehouse_location_stock（库存明细表，WMS-MD-001）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_location_stock;
CREATE TABLE t_warehouse_location_stock (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  warehouse_id    BIGINT       NOT NULL COMMENT '库位 ID',
  product_id      BIGINT       NOT NULL COMMENT '产品 ID',
  ear_no          VARCHAR(32)  NULL COMMENT '关联猪只耳号（白条等）',
  plot_id         BIGINT       NULL COMMENT '关联地块 ID（果蔬）',
  is_end          TINYINT(1)   NULL DEFAULT 0 COMMENT '是否已出清 1=是 0=否',
  product_stock   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '库存数量',
  stock_unit      VARCHAR(16)  NULL COMMENT '单位',
  last_in_time    DATETIME     NULL COMMENT '最近一次入库时间',
  last_out_time   DATETIME     NULL COMMENT '最近一次出库时间',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  version         INT          DEFAULT 0 COMMENT '乐观锁',
  remark          VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_warehouse_product (warehouse_id, product_id),
  KEY idx_product (product_id),
  KEY idx_ear (ear_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存明细表（WMS-MD-001）';

-- ------------------------------------------------------------
-- 3. t_warehouse_stock_flow（出入库记录表 ★ 大表，WMS-FLOW-001）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_stock_flow;
CREATE TABLE t_warehouse_stock_flow (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  flow_no         VARCHAR(32)  NOT NULL COMMENT '流水号（20260312XXXXX 日期+5位自增 SYS-INFRA-004）',
  flow_date       DATETIME     NOT NULL COMMENT '业务日期',
  product_id      BIGINT       NOT NULL COMMENT '产品 ID',
  warehouse_id    BIGINT       NULL COMMENT '库位 ID',
  inout_type      CHAR(3)      NOT NULL COMMENT '出入库类型 in=入库 out=出库',
  flow_type       VARCHAR(16)  NOT NULL COMMENT '业务类型 字典：pick=领用/return=退回/loss=损耗/produce=生产/check=盘点 等',
  stock_in_type   VARCHAR(16)  NULL COMMENT '入库类型 字典 stock_in_type：采摘/月台/交易/净菜/退货',
  stock_out_type  VARCHAR(16)  NULL COMMENT '出库类型 字典 stock_out_type',
  stock_out_dest  VARCHAR(32)  NULL COMMENT '出库去向 字典 stock_out_dest',
  change_num      DECIMAL(12,2) NOT NULL COMMENT '变更数量（正负，正=入 负=出）',
  change_quantity DECIMAL(12,2) NULL COMMENT '变更数量绝对值（前端展示用，后端自动算）',
  supplier_id     BIGINT       NULL COMMENT '供应商 ID（外购时）',
  ear_no          VARCHAR(32)  NULL COMMENT '关联猪只耳号',
  plot_id         BIGINT       NULL COMMENT '关联地块 ID',
  operator_id     BIGINT       NULL COMMENT '操作人 user_id',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '录入人',
  create_time     DATETIME     NULL COMMENT '录入时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_no (tenant_id, flow_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_flow_date (flow_date),
  KEY idx_product (product_id),
  KEY idx_warehouse (warehouse_id),
  KEY idx_inout_type (inout_type, flow_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出入库记录表（WMS-FLOW-001 大表）';

-- ============================================================
-- 产品 + 生产（4 张）
-- ============================================================

-- ------------------------------------------------------------
-- 4. t_warehouse_product_info（产品信息表，WMS-MD-002）
-- typo 修复: product_status x2 -> 合并为 1 个
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_product_info;
CREATE TABLE t_warehouse_product_info (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  product_code        VARCHAR(32)  NOT NULL COMMENT '产品编码',
  product_name        VARCHAR(128) NOT NULL COMMENT '产品名称',
  product_type        TINYINT      NOT NULL COMMENT '产品类型 1=自产 2=外购 3=礼盒',
  belong_type         VARCHAR(32)  NULL COMMENT '自生产产品类（自产时用，字典 product_type）',
  buy_class           VARCHAR(32)  NULL COMMENT '外购产品类（外购时用，字典 purchase_product_type）',
  product_attr        VARCHAR(32)  NULL COMMENT '产品属性',
  product_workshop    VARCHAR(32)  NULL COMMENT '生产车间：燎毛/分割/肉品打包/蔬菜打包/肉品净菜间',
  product_material    VARCHAR(64)  NULL COMMENT '产品原料/材质',
  is_buy_out          TINYINT(1)   NULL DEFAULT 0 COMMENT '是否买断',
  product_status      TINYINT      NOT NULL DEFAULT 1 COMMENT '产品状态 1=上架 0=下架',
  unit                VARCHAR(16)  NULL COMMENT '单位',
  reference_price     DECIMAL(10,2) NULL COMMENT '参考价 元',
  gift_components     JSON         NULL COMMENT '礼盒组件清单 JSON [{product_id,count,unit}]（type=3 时用）',
  oss_ids             VARCHAR(1024) NULL COMMENT '产品图片 OSS IDs',
  create_dept           BIGINT       NULL COMMENT '创建部门',
  create_by           BIGINT       NULL COMMENT '创建人',
  create_time         DATETIME     NULL COMMENT '创建时间',
  update_by           BIGINT       NULL COMMENT '更新人',
  update_time         DATETIME     NULL COMMENT '更新时间',
  del_flag            CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark              VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_code (tenant_id, product_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_product_type (product_type),
  KEY idx_status (product_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品信息表（共表 自产/外购/礼盒，WMS-MD-002）';

-- ------------------------------------------------------------
-- 5. t_warehouse_product_production（产品生产信息表/发货，WMS-DEMAND-001 etc）
-- 发货产品不入库
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_product_production;
CREATE TABLE t_warehouse_product_production (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  produce_no          VARCHAR(32)  NOT NULL COMMENT '生产编号（260312Z0001 日期+产品类型字母 Z/G/B/H/D/L+序号）',
  produce_date        DATETIME     NOT NULL COMMENT '生产日期',
  product_id          BIGINT       NOT NULL COMMENT '产品 ID',
  produce_quantity    DECIMAL(12,2) NOT NULL COMMENT '生产数量',
  plot_id             BIGINT       NULL COMMENT '关联地块 ID',
  ear_no              VARCHAR(32)  NULL COMMENT '关联猪只耳号',
  white_bar_id        BIGINT       NULL COMMENT '关联白条 ID',
  material_id         BIGINT       NULL COMMENT '原料 ID',
  produce_location    BIGINT       NULL COMMENT '生产库位 ID',
  demand_id           BIGINT       NULL COMMENT '关联需求 ID',
  is_delivery_check   TINYINT(1)   NULL DEFAULT 0 COMMENT '是否发货确认',
  is_arrival_confirm  TINYINT(1)   NULL DEFAULT 0 COMMENT '是否到货确认',
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
  UNIQUE KEY uk_produce_no (tenant_id, produce_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_produce_date (produce_date),
  KEY idx_product (product_id),
  KEY idx_demand (demand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品生产信息表（发货不入库）';

-- ------------------------------------------------------------
-- 6. t_warehouse_product_produce（非发货产品生产信息表，WMS-PIG-002 etc）
-- 过程产品入库
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_product_produce;
CREATE TABLE t_warehouse_product_produce (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  produce_no          VARCHAR(32)  NOT NULL COMMENT '生产编号',
  produce_date        DATETIME     NOT NULL COMMENT '生产日期',
  product_id          BIGINT       NOT NULL COMMENT '产品 ID',
  produce_quantity    DECIMAL(12,2) NOT NULL COMMENT '生产数量',
  in_stock_quantity   DECIMAL(12,2) NULL COMMENT '实际入库数量',
  loss_quantity       DECIMAL(12,2) NULL COMMENT '损耗数量',
  plot_id             BIGINT       NULL COMMENT '关联地块 ID',
  ear_no              VARCHAR(32)  NULL COMMENT '关联猪只耳号',
  white_bar_id        BIGINT       NULL COMMENT '关联白条 ID',
  material_id         BIGINT       NULL COMMENT '原料 ID',
  location_id         BIGINT       NULL COMMENT '入库库位 ID',
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
  UNIQUE KEY uk_produce_no (tenant_id, produce_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_product (product_id),
  KEY idx_white_bar (white_bar_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非发货产品生产信息表（入库）';

-- ------------------------------------------------------------
-- 7. t_warehouse_bar_info（白条信息表，WMS-PIG-001）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_bar_info;
CREATE TABLE t_warehouse_bar_info (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  bar_code            VARCHAR(32)  NOT NULL COMMENT '白条编码',
  ear_no              VARCHAR(32)  NULL COMMENT '关联猪只耳号（自产时填）',
  bar_status          VARCHAR(16)  NOT NULL DEFAULT 'pending_singe' COMMENT '状态：pending_singe=待燎毛/singeing=燎毛中/pending_in=待入库/in_stock=已入库/cutting=分割中/done=分割完成',
  out_time            DATETIME     NULL COMMENT '出栏时间',
  out_weight          DECIMAL(12,2) NULL COMMENT '出栏重量 kg',
  in_weight           DECIMAL(12,2) NULL COMMENT '燎毛后入库重量 kg',
  in_time             DATETIME     NULL COMMENT '入库时间',
  in_type             VARCHAR(16)  NULL COMMENT '入库类型：自产/外购',
  back_fat            DECIMAL(5,2) NULL COMMENT '背膘厚 mm',
  acid_remove_time    DATETIME     NULL COMMENT '排酸完成时间',
  acid_remove_loss    DECIMAL(12,2) NULL COMMENT '排酸损失 kg',
  buy_date            DATE         NULL COMMENT '采购日期（外购时填）',
  buy_weight          DECIMAL(12,2) NULL COMMENT '采购重量 kg（外购时填）',
  supplier_id         BIGINT       NULL COMMENT '供应商 ID（外购时填）',
  location_id         BIGINT       NULL COMMENT '入库库位 ID',
  operator_id         BIGINT       NULL COMMENT '操作人',
  create_dept           BIGINT       NULL COMMENT '创建部门',
  create_by           BIGINT       NULL COMMENT '录入人',
  create_time         DATETIME     NULL COMMENT '录入时间',
  update_by           BIGINT       NULL COMMENT '更新人',
  update_time         DATETIME     NULL COMMENT '更新时间',
  del_flag            CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  version             INT          DEFAULT 0 COMMENT '乐观锁',
  remark              VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_bar_code (tenant_id, bar_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_ear_no (ear_no),
  KEY idx_bar_status (bar_status),
  KEY idx_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='白条信息表（WMS-PIG-001）';

-- ============================================================
-- 业务流（5 张）
-- ============================================================

-- ------------------------------------------------------------
-- 8. t_warehouse_check_record（盘点记录表，WMS-STOCK-001）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_check_record;
CREATE TABLE t_warehouse_check_record (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  check_no        VARCHAR(32)  NOT NULL COMMENT '盘点单号',
  check_date      DATE         NOT NULL COMMENT '盘点日期',
  check_status    VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT '状态 字典 djs_check_status：draft/in_progress/completed',
  product_id      BIGINT       NULL COMMENT '产品 ID（按产品盘点时）',
  warehouse_id    BIGINT       NULL COMMENT '库位 ID',
  sys_stock       DECIMAL(12,2) NULL COMMENT '系统库存',
  check_stock     DECIMAL(12,2) NULL COMMENT '实盘数',
  diff_stock      DECIMAL(12,2) NULL COMMENT '差异（实盘-系统）',
  scope_desc      VARCHAR(255) NULL COMMENT '盘点范围说明',
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
  KEY idx_check_date (check_date),
  KEY idx_product (product_id),
  KEY idx_warehouse (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点记录表（WMS-STOCK-001）';

-- ------------------------------------------------------------
-- 9. t_warehouse_return_product（退货管理表，WMS-SHIP-001）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_return_product;
CREATE TABLE t_warehouse_return_product (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  return_no           VARCHAR(32)  NOT NULL COMMENT '退货单号',
  store_id            BIGINT       NULL COMMENT '退货门店 ID',
  product_id          BIGINT       NOT NULL COMMENT '产品 ID',
  return_weight       DECIMAL(12,2) NOT NULL COMMENT '退货重量',
  confirm_weight      DECIMAL(12,2) NULL COMMENT '确认重量',
  confirm_user        BIGINT       NULL COMMENT '确认人 user_id',
  is_confirm          TINYINT(1)   NULL DEFAULT 0 COMMENT '是否已确认 1=是 0=否',
  return_reason       VARCHAR(255) NULL COMMENT '退货原因',
  return_direction    VARCHAR(32)  NULL DEFAULT 'store_to_warehouse' COMMENT '退货方向：store_to_warehouse/warehouse_to_supplier',
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
  KEY idx_store (store_id),
  KEY idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货管理表（WMS-SHIP-001）';

-- ------------------------------------------------------------
-- 10. t_warehouse_demand_manage（需求管理表，WMS-DEMAND-001）
-- 新增字段: shipped_count / confirmed_count（_db-changes 建议补，状态机推进依赖累计）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_demand_manage;
CREATE TABLE t_warehouse_demand_manage (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  demand_no           VARCHAR(32)  NOT NULL COMMENT '需求单号',
  demand_date         DATE         NOT NULL COMMENT '需求日期',
  store_id            BIGINT       NULL COMMENT '门店 ID',
  product_id          BIGINT       NOT NULL COMMENT '产品 ID',
  product_type        TINYINT      NOT NULL COMMENT '产品类型 1=猪肉 2=蔬菜 3=礼盒 4=其他',
  demand_quantity     DECIMAL(12,2) NOT NULL COMMENT '需求数量',
  shipped_count       DECIMAL(12,2) NULL DEFAULT 0 COMMENT '累计已发货量（建议补）',
  confirmed_count     DECIMAL(12,2) NULL DEFAULT 0 COMMENT '累计已确认量（建议补）',
  demand_status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 字典 djs_demand_status：DRAFT/SUBMITTED/CONFIRMED/IN_PRODUCTION/PARTIAL_SHIPPED/COMPLETED/CANCELLED',
  audit_history       JSON         NULL COMMENT '状态流转历史 JSON（v1.1 手写状态机，便于 V2 迁 Flowable）',
  demand_confirmer    BIGINT       NULL COMMENT '确认人 user_id',
  create_dept           BIGINT       NULL COMMENT '创建部门',
  create_by           BIGINT       NULL COMMENT '录入人',
  create_time         DATETIME     NULL COMMENT '录入时间',
  update_by           BIGINT       NULL COMMENT '更新人',
  update_time         DATETIME     NULL COMMENT '更新时间',
  del_flag            CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  version             INT          DEFAULT 0 COMMENT '乐观锁',
  remark              VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_demand_no (tenant_id, demand_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_store (store_id),
  KEY idx_demand_status (demand_status),
  KEY idx_demand_date (demand_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求管理表（WMS-DEMAND-001 / STR-DEMAND-001 同表双视角）';

-- ------------------------------------------------------------
-- 11. t_warehouse_planting_record（地块种植记录表，WMS 视角快照）
-- 与 t_plant_plant_details 字段高度重叠，保留作仓库视角快照
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_planting_record;
CREATE TABLE t_warehouse_planting_record (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  plot_id             BIGINT       NOT NULL COMMENT '地块 ID',
  crop_id             BIGINT       NOT NULL COMMENT '作物 ID',
  plant_date          DATE         NULL COMMENT '种植日期',
  harvest_date        DATE         NULL COMMENT '采收日期',
  harvest_weight      DECIMAL(12,2) NULL COMMENT '采收重量 kg',
  disaster_record     VARCHAR(500) NULL COMMENT '灾害记录摘要',
  team_id             BIGINT       NULL COMMENT '关联班组 ID',
  create_dept           BIGINT       NULL COMMENT '创建部门',
  create_by           BIGINT       NULL COMMENT '录入人',
  create_time         DATETIME     NULL COMMENT '录入时间',
  update_by           BIGINT       NULL COMMENT '更新人',
  update_time         DATETIME     NULL COMMENT '更新时间',
  del_flag            CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark              VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_plot (plot_id),
  KEY idx_crop (crop_id),
  KEY idx_harvest_date (harvest_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地块种植记录表（仓库视角快照，与 t_plant_plant_details 协同）';

-- ------------------------------------------------------------
-- 12. t_warehouse_vegetable_handle（毛菜处理间，WMS-VEG-001）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_vegetable_handle;
CREATE TABLE t_warehouse_vegetable_handle (
  id                      BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id               VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  plot_id                 BIGINT       NULL COMMENT '关联地块 ID',
  crop_id                 BIGINT       NULL COMMENT '关联作物 ID',
  handle_date             DATETIME     NOT NULL COMMENT '处理日期',
  picked_weight           DECIMAL(12,2) NULL COMMENT '采摘重量 kg',
  handled_weight          DECIMAL(12,2) NULL COMMENT '处理后重量 kg',
  feed_weight             DECIMAL(12,2) NULL COMMENT '饲喂重量 kg',
  send_platform_weight    DECIMAL(12,2) NULL COMMENT '送月台重量 kg',
  stock_in_weight         DECIMAL(12,2) NULL COMMENT '入库重量 kg',
  loss_weight             DECIMAL(12,2) NULL COMMENT '损耗重量 kg',
  operator_id             BIGINT       NULL COMMENT '操作人',
  create_dept               BIGINT       NULL COMMENT '创建部门',
  create_by               BIGINT       NULL COMMENT '录入人',
  create_time             DATETIME     NULL COMMENT '录入时间',
  update_by               BIGINT       NULL COMMENT '更新人',
  update_time             DATETIME     NULL COMMENT '更新时间',
  del_flag                CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark                  VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_plot (plot_id),
  KEY idx_handle_date (handle_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='毛菜处理间（WMS-VEG-001）';

-- ------------------------------------------------------------
-- 13. t_warehouse_handle_record（毛菜处理记录，WMS-VEG-001）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_handle_record;
CREATE TABLE t_warehouse_handle_record (
  id                      BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id               VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  plot_id                 BIGINT       NULL COMMENT '关联地块 ID',
  crop_id                 BIGINT       NULL COMMENT '关联作物 ID',
  record_type             VARCHAR(16)  NOT NULL COMMENT '记录类型：stock_in=入库/platform=送月台/feed=饲喂/loss=损耗',
  handle_target           VARCHAR(32)  NULL COMMENT '处理目标：保鲜室/蔬菜月台/饲料库 等',
  location_id             BIGINT       NULL COMMENT '目标库位 ID',
  weight                  DECIMAL(12,2) NULL COMMENT '处理重量 kg',
  handle_date             DATETIME     NULL COMMENT '处理日期',
  operator_id             BIGINT       NULL COMMENT '操作人',
  create_dept               BIGINT       NULL COMMENT '创建部门',
  create_by               BIGINT       NULL COMMENT '录入人',
  create_time             DATETIME     NULL COMMENT '录入时间',
  update_by               BIGINT       NULL COMMENT '更新人',
  update_time             DATETIME     NULL COMMENT '更新时间',
  del_flag                CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark                  VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_plot (plot_id),
  KEY idx_record_type (record_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='毛菜处理记录（WMS-VEG-001）';

-- ------------------------------------------------------------
-- 14. t_warehouse_supplier_record（供应商交易信息）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_supplier_record;
CREATE TABLE t_warehouse_supplier_record (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  supplier_id     BIGINT       NOT NULL COMMENT '供应商 ID',
  product_id      BIGINT       NOT NULL COMMENT '产品 ID',
  buy_num         DECIMAL(12,2) NOT NULL COMMENT '采购数量',
  buy_price       DECIMAL(10,2) NULL COMMENT '采购单价（推断字段）',
  buy_amount      DECIMAL(15,2) NULL COMMENT '采购总额（推断字段）',
  buy_date        DATE         NULL COMMENT '采购日期',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '录入人',
  create_time     DATETIME     NULL COMMENT '录入时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_supplier (supplier_id),
  KEY idx_product (product_id),
  KEY idx_buy_date (buy_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商交易信息表';

-- ============================================================
-- 追溯（2 张，TRC-CORE-001）
-- 表名改造: pig_code -> trace_code / pig_time -> trace_event（含猪肉+果蔬+礼盒 3 种）
-- ============================================================

-- ------------------------------------------------------------
-- 15. t_warehouse_trace_code（追溯码，TRC-CORE-001）
-- _db-changes: 原 t_warehouse_pig_code，因含 3 业态改名 trace_code
-- 改造: farm_name 冗余 -> farm_id (JOIN sys_farm)
-- 毫秒级 datetime(3)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_trace_code;
CREATE TABLE t_warehouse_trace_code (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  produce_code        VARCHAR(64)  NOT NULL COMMENT '追溯码（UNIQUE）',
  code_type           VARCHAR(16)  NOT NULL COMMENT '追溯码类型：pork=猪肉 / veg=果蔬 / gift=礼盒',
  product_id          BIGINT       NULL COMMENT '产品 ID',
  pig_ear_no          VARCHAR(32)  NULL COMMENT '关联猪只耳号',
  plot_id             BIGINT       NULL COMMENT '关联地块 ID',
  plant_days          INT          NULL COMMENT '种植天数（果蔬时填）',
  havest_date         DATE         NULL COMMENT '采收日期',
  crop_cert_id        BIGINT       NULL COMMENT '作物有机证书 ID',
  plot_cert_id        BIGINT       NULL COMMENT '地块有机证书 ID',
  store_id            BIGINT       NULL COMMENT '门店 ID',
  farm_id             BIGINT       NULL COMMENT '农场 ID（替代原 farm_name 冗余，JOIN sys_farm 取名）',
  gift_components     JSON         NULL COMMENT '礼盒子追溯码 JSON（礼盒时用）',
  qr_oss_id           BIGINT       NULL COMMENT 'QR 码图片 OSS ID',
  create_dept           BIGINT       NULL COMMENT '创建部门',
  create_by           BIGINT       NULL COMMENT '生成人',
  create_time         DATETIME(3)  NULL COMMENT '生成时间（毫秒级）',
  update_by           BIGINT       NULL COMMENT '更新人',
  update_time         DATETIME(3)  NULL COMMENT '更新时间',
  del_flag            CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark              VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_produce_code (tenant_id, produce_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_code_type (code_type),
  KEY idx_product (product_id),
  KEY idx_pig_ear (pig_ear_no),
  KEY idx_plot (plot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追溯码（TRC-CORE-001，原 pig_code 改名）';

-- ------------------------------------------------------------
-- 16. t_warehouse_trace_event（时间追溯/事件流水，TRC-CORE-001）
-- _db-changes: 原 t_warehouse_pig_time，改名 trace_event
-- 流水表无 UNIQUE、无 del_flag/update_by（immutable）
-- 毫秒级 datetime(3)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_trace_event;
CREATE TABLE t_warehouse_trace_event (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  produce_code    VARCHAR(64)  NOT NULL COMMENT '追溯码（关联 t_warehouse_trace_code）',
  trace_content   VARCHAR(32)  NOT NULL COMMENT '事件类型（7 种）：marketing=出栏/singe=燎毛/slaughter=屠宰/acid=排酸/in_stock=入库/ship=发货/arrival=到店',
  trace_time      DATETIME(3)  NOT NULL COMMENT '事件时间（毫秒级）',
  event_data      JSON         NULL COMMENT '事件附加数据 JSON',
  operator_id     BIGINT       NULL COMMENT '操作人',
  create_time     DATETIME(3)  NULL COMMENT '记录创建时间',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_code_time (produce_code, trace_time),
  KEY idx_trace_content (trace_content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时间追溯/事件流水（TRC-CORE-001，原 pig_time 改名）';

SET FOREIGN_KEY_CHECKS = 1;
