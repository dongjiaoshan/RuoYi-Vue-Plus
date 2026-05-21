-- ============================================================
-- SYS-INIT-001 业务表 DDL — 跨域 common
-- 生成时间: 2026-05-20
-- 表数: 4 张（sys_farm / t_md_store / t_md_supplier / mp_subscribe_record）
-- 强制规范: tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' / UNIQUE 含 del_unique 生成列 / 审计字段对齐 ruoyi
-- 引用: doc/05-架构文档-ruoyi.md §6, doc/06-实现描述.md 第 1 章 (SYS-MD-002/003 + SYS-INFRA-006/007), doc/_db-changes.md
-- 注: mp_subscribe_record 为全局共享表（SYS-INFRA-006 / SYS-INFRA-007 IGNORE 名单），不含 tenant_id
--     sys_farm 为农场主数据表（SYS-INFRA-007），全局共享，不含 tenant_id
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1. sys_farm（农场主数据，SYS-INFRA-007 多农场底座）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS sys_farm;
CREATE TABLE sys_farm (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '农场 ID',
  farm_code       VARCHAR(32)  NOT NULL COMMENT '农场编码（如 DJS-MAIN）',
  farm_name       VARCHAR(64)  NOT NULL COMMENT '农场名称',
  farm_status     TINYINT      NOT NULL DEFAULT 1 COMMENT '农场状态 1=启用 0=停用',
  contact_name    VARCHAR(32)  NULL COMMENT '联系人',
  contact_phone   VARCHAR(20)  NULL COMMENT '联系电话',
  address         VARCHAR(255) NULL COMMENT '地址',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志 0=未删 1=已删',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_farm_code (farm_code, del_unique),
  KEY idx_status (farm_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农场主数据（V1 仅 1 条 1001=东角山主场）';

-- 种子数据：V1 主场（建表后立即可用）
INSERT INTO sys_farm (id, farm_code, farm_name, farm_status, create_by, create_time)
VALUES (1001, 'DJS-MAIN', '东角山主场', 1, 1, NOW())
ON DUPLICATE KEY UPDATE farm_name = VALUES(farm_name);

-- ------------------------------------------------------------
-- 2. t_md_store（门店主数据，SYS-MD-002）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_md_store;
CREATE TABLE t_md_store (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '门店 ID',
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID（多租户）',
  store_code      VARCHAR(32)  NOT NULL COMMENT '门店编码（业务自定义，如 BJ001）',
  store_name      VARCHAR(64)  NOT NULL COMMENT '门店名称',
  store_type      VARCHAR(16)  NOT NULL DEFAULT 'direct' COMMENT '门店类型 direct=直营 / franchise=加盟（V1 自由文本，待客户上线前确认是否上字典）',
  business_status TINYINT      NOT NULL DEFAULT 1 COMMENT '经营状态 1=合作中 0=已终止',
  address         VARCHAR(255) NULL COMMENT '门店地址',
  contact_name    VARCHAR(32)  NULL COMMENT '联系人',
  contact_phone   VARCHAR(20)  NULL COMMENT '联系电话',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志 0=未删 1=已删',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_code (tenant_id, store_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_status (business_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店主数据（SYS-MD-002）';

-- ------------------------------------------------------------
-- 3. t_md_supplier（供应商主数据，SYS-MD-003）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_md_supplier;
CREATE TABLE t_md_supplier (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '供应商 ID',
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  supplier_code   VARCHAR(32)  NOT NULL COMMENT '供应商编码（SYS-INFRA-004 生成 G0001 风格）',
  supplier_name   VARCHAR(128) NOT NULL COMMENT '供应商名称',
  supplier_type   VARCHAR(32)  NOT NULL COMMENT '供应商类型 字典 djs_supplier_type: feed/breed/med/seed/pack/other',
  contact_name    VARCHAR(32)  NULL COMMENT '联系人',
  contact_phone   VARCHAR(20)  NULL COMMENT '联系电话',
  address         VARCHAR(255) NULL COMMENT '地址',
  business_status TINYINT      NOT NULL DEFAULT 1 COMMENT '合作状态 1=合作中 0=已终止',
  settle_type     VARCHAR(16)  NULL COMMENT '结算方式 cash/monthly/quarterly（V1 自由文本，待客户上线前确认是否上字典）',
  bank_account    VARCHAR(64)  NULL COMMENT '银行账号',
  bank_name       VARCHAR(64)  NULL COMMENT '开户行',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_supplier_code (tenant_id, supplier_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_supplier_type (supplier_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商主数据（SYS-MD-003）';

-- ------------------------------------------------------------
-- 4. mp_subscribe_record（微信订阅消息记录，SYS-INFRA-006）
-- 注: 全局共享表，不带 tenant_id（参 SYS-INFRA-007 IGNORE 名单）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS mp_subscribe_record;
CREATE TABLE mp_subscribe_record (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id         BIGINT       NOT NULL COMMENT '用户 ID（sys_user.user_id）',
  openid          VARCHAR(64)  NOT NULL COMMENT '微信 openid',
  template_id     VARCHAR(64)  NOT NULL COMMENT '微信模板 ID',
  subscribed_at   DATETIME     NOT NULL COMMENT '授权时间',
  expired_at      DATETIME     NULL COMMENT '过期时间（订阅消息有效期 7 天）',
  used            TINYINT      NOT NULL DEFAULT 0 COMMENT '0=未使用 1=已使用',
  used_at         DATETIME     NULL COMMENT '使用时间',
  always_keep     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否勾选"总是保持以上选择" 1=是 0=否',
  create_time     DATETIME     NOT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_user_template (user_id, template_id, used, expired_at),
  KEY idx_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信订阅消息授权记录（SYS-INFRA-006，全局共享）';

SET FOREIGN_KEY_CHECKS = 1;
