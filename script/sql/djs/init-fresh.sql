-- ============================================================================
-- init-fresh.sql — 东角山 djs 业务库「全新环境」一次性初始化快照
-- ============================================================================
-- 用途：
--   全新空库从源码初始化时，用本文件一把建好所有 djs 业务表 + 字典 + 菜单
--   + 编码规则 + ruoyi 默认数据 cleanup。本文件 = 把所有版本号 ≤ Flyway
--   baseline（V202605281100）的迁移按版本升序拼接而成（被 baseline-on-migrate
--   跳过、新库不会自动跑的那批）。原始 SQL 原样拼接，未改写。
--
-- 全新环境初始化顺序（三步）：
--   (1) 导入 ruoyi 自带 script/sql/ry_vue_5.X.sql 建 sys_* 系统表（含 ry_job /
--       ry_workflow 按 quickstart 需要时一并导）。
--   (2) 跑本 init-fresh.sql：建 djs 业务表 + 字典 + 菜单 + 编码规则 + cleanup
--       ruoyi 默认 demo/sample 数据。
--       例：mysql -uroot -p ry-vue < script/sql/djs/init-fresh.sql
--   (3) 启动 ruoyi-admin。首次启动 Flyway baseline-on-migrate=true 会：
--         - 建 flyway_schema_history 表并插入 baseline 行（version=202605281100）；
--         - 把版本 ≤ baseline 的迁移视为已应用、跳过（即本文件已覆盖的那批）；
--         - 把版本 > baseline 的迁移作为增量按序应用。
--
-- 维护约定：
--   新增 DDL 一律写成版本号 > baseline（V202607220910 之后）的 Flyway 增量迁移文件，
--   放 ruoyi-admin/src/main/resources/db/migration/，Flyway 启动时自动应用。
--   无需再手动维护本快照 —— baseline 不动，本文件就一直等价于「baseline 之前的全集」。
--   仅当 Kevin 升 baseline-version 时才需要把新跨过 baseline 的迁移重新合进本文件。
--
-- 注意：本文件含 INSERT sys_dict_data / sys_menu，导入后需跑
--   bash script/sql/djs/_post-init.sh 清 Redis 字典缓存（若此前 admin 已起过）。
--
-- 合并自 40 个迁移文件（版本 ≤ V202605281100），按版本升序：
--   V202605200900__SYS-INIT-001-create-business-tables-common.sql
--   V202605200901__SYS-INIT-001-create-business-tables-breed.sql
--   V202605200902__SYS-INIT-001-create-business-tables-plant.sql
--   V202605200903__SYS-INIT-001-create-business-tables-warehouse.sql
--   V202605200904__SYS-INIT-001-create-business-tables-store.sql
--   V202605200905__SYS-INIT-001-extend-ruoyi-tables.sql
--   V202605200906__SYS-INIT-001-cleanup-ruoyi-menus.sql
--   V202605201000__SYS-INIT-002-init-dict.sql
--   V202605201100__SYS-AUTH-001-roles-and-menus.sql
--   V202605201200__SYS-INFRA-002-oss-config-and-menus.sql
--   V202605201500__SYS-CLEANUP-single-tenant.sql
--   V202605201600__SYS-CLEANUP-ruoyi-demo-menus.sql
--   V202605201700__SYS-CLEANUP-ruoyi-sample-data.sql
--   V202605210800__D02-PATCH-D01-missing-tables.sql
--   V202605210900__SYS-INFRA-004-biz-code-rules.sql
--   V202605211200__SYS-MD-001-menu.sql
--   V202605211300__SYS-MD-002-menu.sql
--   V202605211400__D02-PATCH-D01-D02-fixes.sql
--   V202605211800__D02-PATCH-fix-audit-cols.sql
--   V202605211900__D02-PATCH-65-tables-audit-cols.sql
--   V202605220900__SYS-MD-003-menu.sql
--   V202605221000__BRD-MD-001-breeding-menu-and-dict.sql
--   V202605221100__BRD-MD-002-farm-barn-pen-menu.sql
--   V202605221101__BRD-MED-001-medicine-batch.sql
--   V202605221200__BRD-MD-003-production-configs.sql
--   V202605221201__SYS-FIX-001-biz-dict-supplement.sql
--   V202605221300__D04-CLOSING-D02-D03-leftover-fixes.sql
--   V202605221400__D04-CLOSING-seed-dev-users-and-depts.sql
--   V202605222100__D04-TH06-seed-mock-dev-user.sql
--   V202605231400__SYS-MD-FIX-002-store-supplier.sql
--   V202605242200__BRD-CORE-001-add-status-record-update-cols.sql
--   V202605242300__BRD-EVENT-001-003-admin-readonly-list-menu.sql
--   V202605260900__BRD-CORE-001-menu.sql
--   V202605260901__BRD-CORE-001-realign-status-record-comment.sql
--   V202605270900__BRD-EVENT-001-intro-no-rule.sql
--   V202605270901__BRD-EVENT-001-menu.sql
--   V202605270902__BRD-EVENT-003-menu.sql
--   V202605280800__D05-CLOSING-fixes.sql
--   V202605281000__SYS-FIX-002-drop-person-postId.sql
--   V202605281100__D05-HOTFIX-breeding-split-4-menus.sql
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 来源文件：V202605200900__SYS-INIT-001-create-business-tables-common.sql
-- ----------------------------------------------------------------------------
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
  farm_status     TINYINT      NOT NULL DEFAULT 0 COMMENT '农场状态（字典 djs_farm_status：0=启用 1=停用）',
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
VALUES (1001, 'DJS-MAIN', '东角山主场', 0, 1, NOW())
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


-- ----------------------------------------------------------------------------
-- 来源文件：V202605200901__SYS-INIT-001-create-business-tables-breed.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- SYS-INIT-001 业务表 DDL — 养殖域 BRD
-- 生成时间: 2026-05-20
-- 表数: 29 张（含 _db-changes 列出的 25 张 + pig_batch + t_breed_production_config + t_breed_medicine_info + t_breed_medicine_use）
-- 强制规范: tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' / UNIQUE 含 del_unique 生成列 / 审计字段对齐 ruoyi
-- 引用: doc/05-架构文档-ruoyi.md §6, doc/06-实现描述.md 第 2 章 (BRD-*) + SYS-INFRA-004 (pig_batch), doc/_db-changes.md
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 主数据（5 张）
-- ============================================================

-- ------------------------------------------------------------
-- 1. t_farm_breed_info（育种信息表，品种+品系合表，CR-20260519-06）
-- breed_strain 字段区分 1=品种 / 2=品系
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_breed_info;
CREATE TABLE t_farm_breed_info (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  breed_strain      TINYINT      NOT NULL COMMENT '类型 1=品种 / 2=品系',
  breed_strain_code VARCHAR(32)  NOT NULL COMMENT '品种/品系编码（业务码，字符串引用）',
  breed_strain_name VARCHAR(64)  NOT NULL COMMENT '品种/品系名称',
  parent_code       VARCHAR(32)  NULL COMMENT '父级编码（品系归属品种时填）',
  description       VARCHAR(255) NULL COMMENT '描述',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '创建人',
  create_time       DATETIME     NULL COMMENT '创建时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_breed_strain_code (tenant_id, breed_strain, breed_strain_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='育种信息表（品种+品系合表，BRD-MD-001）';

-- ------------------------------------------------------------
-- 2. t_farm_breed_config（育种配置表，配种关系合表，CR-20260519-06）
-- typo 修复: confing -> config / monther_code -> mother_code
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_breed_config;
CREATE TABLE t_farm_breed_config (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  breed_strain      TINYINT      NOT NULL COMMENT '类型 1=品种配种 / 2=品系配种',
  mother_code       VARCHAR(32)  NOT NULL COMMENT '母本品种/品系编码（引用 t_farm_breed_info.breed_strain_code）',
  father_code       VARCHAR(32)  NOT NULL COMMENT '父本品种/品系编码',
  cub_code          VARCHAR(32)  NOT NULL COMMENT '仔代品种/品系编码（必须先在 t_farm_breed_info 建好）',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '创建人',
  create_time       DATETIME     NULL COMMENT '创建时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_breed_config (tenant_id, breed_strain, mother_code, father_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_cub (tenant_id, cub_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='育种配置表（配种关系合表，BRD-MD-001）';

-- ------------------------------------------------------------
-- 3. t_farm_barn_info（栋舍信息表，BRD-MD-002）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_barn_info;
CREATE TABLE t_farm_barn_info (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  barn_code       VARCHAR(32)  NOT NULL COMMENT '栋舍编码',
  barn_name       VARCHAR(64)  NOT NULL COMMENT '栋舍名称',
  barn_type       VARCHAR(16)  NOT NULL COMMENT '栋舍类型 字典 barn_type：母猪舍/育成舍/公猪舍/产床舍/育肥舍/隔离舍',
  capacity        INT          NULL COMMENT '设计容量（头数，推断字段）',
  current_count   INT          NULL DEFAULT 0 COMMENT '当前头数（推断字段，可定时算）',
  barn_status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=启用 0=停用',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_barn_code (tenant_id, barn_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_barn_type (barn_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栋舍信息表（BRD-MD-002）';

-- ------------------------------------------------------------
-- 4. t_farm_barn_pen（栏位信息表，BRD-MD-002）
-- typo 修复: bran_pen -> barn_pen
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_barn_pen;
CREATE TABLE t_farm_barn_pen (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  barn_id         BIGINT       NOT NULL COMMENT '栋舍 ID',
  pen_code        VARCHAR(32)  NOT NULL COMMENT '栏位编码',
  pen_name        VARCHAR(64)  NOT NULL COMMENT '栏位名称',
  pen_type        VARCHAR(16)  NOT NULL COMMENT '栏位类型：大栏/限位栏/产床/隔离栏',
  capacity        INT          NULL COMMENT '设计容量',
  current_count   INT          NULL DEFAULT 0 COMMENT '当前头数',
  pen_status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=启用 0=停用',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_pen_code (tenant_id, barn_id, pen_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_barn (barn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='栏位信息表（BRD-MD-002）';

-- ------------------------------------------------------------
-- 5. t_breed_production_config（生产配置表，BRD-MD-003）
-- type 字段区分 sow / fattening / marketing
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_breed_production_config;
CREATE TABLE t_breed_production_config (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  config_type     VARCHAR(16)  NOT NULL COMMENT '配置类型 sow=母猪 / fattening=育肥 / marketing=出栏',
  config_key      VARCHAR(64)  NOT NULL COMMENT '配置键（如 breed_wean_to_mate_days）',
  value_days      INT          NULL COMMENT '天数值（type=sow 时用）',
  start_age       INT          NULL COMMENT '起始日龄（type=fattening 时用）',
  end_age         INT          NULL COMMENT '结束日龄（type=fattening 时用）',
  record_grow     TINYINT      NULL DEFAULT 0 COMMENT '是否需要记录生长 1=是 0=否',
  display_order   INT          DEFAULT 0 COMMENT '展示顺序',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_key (tenant_id, config_type, config_key, del_unique),
  KEY idx_tenant_create (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产配置表（BRD-MD-003，3 type 多行）';

-- ============================================================
-- 核心实体 + 状态机（3 张）
-- ============================================================

-- ------------------------------------------------------------
-- 6. t_farm_pig_info（猪只信息表 ★ 中心实体，BRD-CORE-001）
-- v1.2 改造：pig_status 废弃 / 新增 current_status + status_started_at + lifecycle_id + recyclable
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_pig_info;
CREATE TABLE t_farm_pig_info (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  ear_tag             VARCHAR(32)  NULL COMMENT '耳号全版 {breed}-{strain}-{sex}-{yyMMdd}-{seq:03d}',
  ear_no              VARCHAR(32)  NOT NULL COMMENT '耳号简版 {yyMMdd}-{seq:03d}',
  lifecycle_id        INT          NOT NULL DEFAULT 1 COMMENT '生命周期 id（耳号复用次数+1，SYS-INFRA-004）',
  recyclable          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否可回收复用 1=是 0=否',
  pig_sex             CHAR(1)      NOT NULL COMMENT '性别 F=母 M=公',
  pig_type            VARCHAR(16)  NOT NULL COMMENT '猪只类型 字典 pig_type：sow/boar/piglet/fattening',
  pig_breed_code      VARCHAR(32)  NULL COMMENT '品种编码（引用 t_farm_breed_info）',
  pig_strain_code     VARCHAR(32)  NULL COMMENT '品系编码（引用 t_farm_breed_info）',
  current_status      VARCHAR(16)  NOT NULL DEFAULT 'HB' COMMENT '当前状态（10 枚举）：HB/PZ/PH/FM/DN/LC/KH/FQ/END/BOAR_ACTIVE；字典 djs_pig_lifecycle',
  status_started_at   DATETIME     NOT NULL COMMENT '进入当前状态的时间（小程序"按 X 天提醒"基准）',
  end_reason          VARCHAR(16)  NULL COMMENT '终止原因 END 状态时填：DEAD/CULL/MARKET',
  father_ear          VARCHAR(32)  NULL COMMENT '父猪耳号（仔猪用）',
  mother_ear          VARCHAR(32)  NULL COMMENT '母猪耳号（仔猪用）',
  birth_date          DATE         NULL COMMENT '出生日期',
  introduce_date      DATE         NULL COMMENT '引种日期',
  introduce_type      VARCHAR(16)  NULL COMMENT '引种方式 字典 introduce_from：internal/external',
  supplier_id         BIGINT       NULL COMMENT '供应商 ID（外部引种用）',
  parity              INT          NULL DEFAULT 0 COMMENT '胎次（母猪用）',
  barn_id             BIGINT       NULL COMMENT '当前栋舍 ID',
  pen_id              BIGINT       NULL COMMENT '当前栏位 ID',
  mating_id           BIGINT       NULL COMMENT '最近一次配种记录 ID',
  is_appointed        TINYINT(1)   NULL DEFAULT 0 COMMENT '是否被预约出栏 1=是 0=否',
  store_id            BIGINT       NULL COMMENT '预约门店 ID',
  create_dept           BIGINT       NULL COMMENT '创建部门',
  create_by           BIGINT       NULL COMMENT '创建人',
  create_time         DATETIME     NULL COMMENT '创建时间',
  update_by           BIGINT       NULL COMMENT '更新人',
  update_time         DATETIME     NULL COMMENT '更新时间',
  del_flag            CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  version             INT          DEFAULT 0 COMMENT '乐观锁版本',
  remark              VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_ear_no_farm (tenant_id, ear_no, lifecycle_id, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_current_status (tenant_id, current_status),
  KEY idx_barn_pen (barn_id, pen_id),
  KEY idx_pig_type (tenant_id, pig_type),
  KEY idx_recyclable (tenant_id, recyclable)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猪只信息表（中心实体，BRD-CORE-001）';

-- ------------------------------------------------------------
-- 7. t_farm_status_record（状态变更记录表 ★ 状态机历史，BRD-CORE-001）
-- 流水表无 UNIQUE 故无需 del_unique，但 _db-changes 说统计表不带 del_flag/update_by
-- 这里不算预聚合统计，保留 del_flag/update_by
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_status_record;
CREATE TABLE t_farm_status_record (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id          BIGINT       NOT NULL COMMENT '猪只 ID',
  ear_no          VARCHAR(32)  NOT NULL COMMENT '耳号（冗余便于查询）',
  old_status      VARCHAR(16)  NULL COMMENT '原状态',
  new_status      VARCHAR(16)  NOT NULL COMMENT '新状态',
  event_type      VARCHAR(16)  NOT NULL COMMENT '触发事件（11 枚举）：INTRO/BREED/FARROW/WEAN/OESTRUS/NULL_RETURN/DIE/ELIMINATE/CASTRATE/TRANSFER/SLAUGHTER；字典 djs_pig_status_event',
  related_event_id BIGINT      NULL COMMENT '关联业务事件 ID（如 breeding_id/farrow_id）',
  duration_days   INT          NULL COMMENT '在原状态停留天数（业务层计算）',
  change_time     DATETIME     NOT NULL COMMENT '状态变更时间',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '操作人',
  create_time     DATETIME     NULL COMMENT '记录创建时间',
  update_by       BIGINT       NULL COMMENT '更新人（MP insertFill 占位，状态记录实际不 update）',
  update_time     DATETIME     NULL COMMENT '更新时间（MP insertFill 占位）',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_ear_change (ear_no, change_time),
  KEY idx_pig (tenant_id, pig_id, change_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='状态变更记录（状态机历史，BRD-CORE-001）';

-- ------------------------------------------------------------
-- 8. pig_batch（耳号批次回收表，SYS-INFRA-004 v1.2 新增）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS pig_batch;
CREATE TABLE pig_batch (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  batch_no        VARCHAR(32)  NOT NULL COMMENT '批次号（yymmdd_yymmdd 格式）',
  year            SMALLINT     NOT NULL COMMENT '所属年份',
  batch_status    CHAR(1)      NOT NULL DEFAULT 'A' COMMENT '状态 A=Active C=Closed',
  closed_at       DATETIME     NULL COMMENT '关闭时间',
  closed_by       BIGINT       NULL COMMENT '关闭操作人 user_id',
  recycled_count  INT          NULL DEFAULT 0 COMMENT '回收数量',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NOT NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_batch (tenant_id, batch_no, del_unique),
  KEY idx_tenant_status (tenant_id, batch_status),
  KEY idx_year (year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='耳号批次回收表（SYS-INFRA-004）';

-- ============================================================
-- 事件流水（13 张）
-- ============================================================

-- ------------------------------------------------------------
-- 9. t_farm_pig_introduce（猪种引种记录，BRD-EVENT-001）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_pig_introduce;
CREATE TABLE t_farm_pig_introduce (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  introduce_no    VARCHAR(32)  NOT NULL COMMENT '引种单号',
  introduce_type  VARCHAR(16)  NOT NULL COMMENT '引种方式 字典 introduce_from：external/internal',
  introduce_date  DATE         NOT NULL COMMENT '引种日期',
  supplier_id     BIGINT       NULL COMMENT '供应商 ID（外部引种时必填）',
  pig_count       INT          NOT NULL COMMENT '引入头数',
  start_ear_no    VARCHAR(32)  NULL COMMENT '起始耳号',
  pig_breed_code  VARCHAR(32)  NULL COMMENT '品种编码',
  pig_strain_code VARCHAR(32)  NULL COMMENT '品系编码',
  pig_sex         CHAR(1)      NULL COMMENT '性别（统一时填，混批时 NULL）',
  proof_oss_ids   VARCHAR(1024) NULL COMMENT '凭证图片 OSS IDs 逗号分隔（外部引种强制）',
  barn_id         BIGINT       NULL COMMENT '目标栋舍 ID',
  pen_id          BIGINT       NULL COMMENT '目标栏位 ID',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '录入人',
  create_time     DATETIME     NULL COMMENT '录入时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_introduce_no (tenant_id, introduce_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_introduce_date (introduce_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猪种引种记录（BRD-EVENT-001）';

-- ------------------------------------------------------------
-- 10. t_farm_pig_breeding（母猪配种记录，BRD-EVENT-002）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_pig_breeding;
CREATE TABLE t_farm_pig_breeding (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id          BIGINT       NOT NULL COMMENT '母猪 ID',
  ear_no          VARCHAR(32)  NOT NULL COMMENT '母猪耳号（冗余）',
  breeding_date   DATETIME     NOT NULL COMMENT '配种日期',
  breeding_type   VARCHAR(16)  NOT NULL COMMENT '配种方式 字典 breeding_type：own_boar/semen',
  boar_ear_no     VARCHAR(32)  NULL COMMENT '公猪耳号（本场公猪时填）',
  semen_supplier  VARCHAR(64)  NULL COMMENT '精液供应商（精液产品时填）',
  semen_batch_no  VARCHAR(32)  NULL COMMENT '精液批号',
  parity          INT          NULL COMMENT '本次胎次',
  operator_id     BIGINT       NULL COMMENT '操作人 user_id',
  barn_name       VARCHAR(64)  NULL COMMENT '栋舍名称冗余',
  pen_name        VARCHAR(64)  NULL COMMENT '栏位名称冗余',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '录入人',
  create_time     DATETIME     NULL COMMENT '录入时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_pig (tenant_id, pig_id),
  KEY idx_breeding_date (breeding_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='母猪配种记录（BRD-EVENT-002）';

-- ------------------------------------------------------------
-- 11. t_farm_pig_farrow（母猪分娩记录，BRD-EVENT-002）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_pig_farrow;
CREATE TABLE t_farm_pig_farrow (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id            BIGINT       NOT NULL COMMENT '母猪 ID',
  ear_no            VARCHAR(32)  NOT NULL COMMENT '母猪耳号',
  breeding_id       BIGINT       NOT NULL COMMENT '关联配种记录 ID',
  farrow_date       DATETIME     NOT NULL COMMENT '分娩日期',
  total_born        INT          NOT NULL COMMENT '总产仔数',
  live_born         INT          NOT NULL COMMENT '活产数',
  dead_born         INT          NULL DEFAULT 0 COMMENT '死胎数',
  mummy_born        INT          NULL DEFAULT 0 COMMENT '木乃伊数',
  weak_born         INT          NULL DEFAULT 0 COMMENT '弱仔数',
  male_count        INT          NULL COMMENT '公仔数',
  female_count      INT          NULL COMMENT '母仔数',
  total_weight      DECIMAL(12,2) NULL COMMENT '产仔总重 kg',
  avg_weight        DECIMAL(8,3)  NULL COMMENT '平均出生重 kg',
  parity            INT          NULL COMMENT '本次胎次',
  operator_id       BIGINT       NULL COMMENT '操作人 user_id',
  barn_name         VARCHAR(64)  NULL COMMENT '栋舍名称冗余',
  pen_name          VARCHAR(64)  NULL COMMENT '栏位名称冗余',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_pig (tenant_id, pig_id),
  KEY idx_breeding (breeding_id),
  KEY idx_farrow_date (farrow_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='母猪分娩记录（BRD-EVENT-002）';

-- ------------------------------------------------------------
-- 12. t_farm_pig_weaning（母猪断奶记录，BRD-EVENT-002）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_pig_weaning;
CREATE TABLE t_farm_pig_weaning (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id            BIGINT       NOT NULL COMMENT '母猪 ID',
  ear_no            VARCHAR(32)  NOT NULL COMMENT '母猪耳号',
  farrow_id         BIGINT       NOT NULL COMMENT '关联分娩记录 ID',
  breeding_id       BIGINT       NULL COMMENT '关联配种记录 ID',
  weaning_date      DATETIME     NOT NULL COMMENT '断奶日期',
  weaned_count      INT          NOT NULL COMMENT '断奶头数',
  weaned_weight     DECIMAL(12,2) NULL COMMENT '断奶总重 kg',
  avg_weaned_weight DECIMAL(8,3)  NULL COMMENT '平均断奶重 kg',
  operator_id       BIGINT       NULL COMMENT '操作人',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_pig (tenant_id, pig_id),
  KEY idx_farrow (farrow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='母猪断奶记录（BRD-EVENT-002）';

-- ------------------------------------------------------------
-- 13. t_farm_pig_heat（母猪查情记录，BRD-EVENT-002）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_pig_heat;
CREATE TABLE t_farm_pig_heat (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id            BIGINT       NOT NULL COMMENT '母猪 ID',
  ear_no            VARCHAR(32)  NOT NULL COMMENT '母猪耳号',
  heat_date         DATETIME     NOT NULL COMMENT '查情日期',
  heat_result       VARCHAR(16)  NOT NULL COMMENT '查情结果：normal=正常/no_heat=未发情/pregnant=已妊娠/abnormal=异常',
  is_pregnant_confirmed TINYINT(1) NULL DEFAULT 0 COMMENT '是否确认妊娠：1=已妊娠（OESTRUS 事件 payload 决定 PZ→PH 状态跳转）',
  operator_id       BIGINT       NULL COMMENT '操作人 user_id',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_pig (tenant_id, pig_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='母猪查情记录（BRD-EVENT-002）';

-- ------------------------------------------------------------
-- 14. t_farm_pig_abnormal（母猪返空流记录，BRD-EVENT-002）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_pig_abnormal;
CREATE TABLE t_farm_pig_abnormal (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id            BIGINT       NOT NULL COMMENT '母猪 ID',
  ear_no            VARCHAR(32)  NOT NULL COMMENT '母猪耳号',
  abnormal_date     DATETIME     NOT NULL COMMENT '异常日期',
  abnormal_type     VARCHAR(16)  NOT NULL COMMENT '异常类型：abort=流产/return=返情/idle=空怀',
  related_breeding_id BIGINT     NULL COMMENT '关联配种记录 ID',
  abnormal_reason   VARCHAR(32)  NULL COMMENT '异常原因',
  operator_id       BIGINT       NULL COMMENT '操作人',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_pig (tenant_id, pig_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='母猪返空流记录（BRD-EVENT-002）';

-- ------------------------------------------------------------
-- 15. t_farm_pig_pigletno（仔猪耳号打标记录，BRD-EVENT-003）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_pig_pigletno;
CREATE TABLE t_farm_pig_pigletno (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  piglet_ear_no     VARCHAR(32)  NOT NULL COMMENT '仔猪耳号',
  mother_ear_no     VARCHAR(32)  NOT NULL COMMENT '母猪耳号',
  father_ear_no     VARCHAR(32)  NULL COMMENT '父猪耳号',
  farrow_id         BIGINT       NOT NULL COMMENT '关联分娩记录 ID',
  tag_date          DATETIME     NOT NULL COMMENT '打标日期',
  piglet_sex        CHAR(1)      NOT NULL COMMENT '性别 F=母 M=公',
  birth_weight      DECIMAL(8,3) NULL COMMENT '出生重 kg',
  pig_id            BIGINT       NULL COMMENT '生成的 t_farm_pig_info.id',
  operator_id       BIGINT       NULL COMMENT '操作人',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_piglet_ear (tenant_id, piglet_ear_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_mother (mother_ear_no),
  KEY idx_farrow (farrow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仔猪耳号打标记录（BRD-EVENT-003）';

-- ------------------------------------------------------------
-- 16. t_farm_wean_weight（断奶仔猪重记录，BRD-EVENT-003）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_wean_weight;
CREATE TABLE t_farm_wean_weight (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  piglet_ear_no     VARCHAR(32)  NOT NULL COMMENT '仔猪耳号',
  weaning_id        BIGINT       NULL COMMENT '关联断奶记录 ID',
  weigh_date        DATETIME     NOT NULL COMMENT '称重日期',
  weight            DECIMAL(8,3) NOT NULL COMMENT '断奶重 kg',
  operator_id       BIGINT       NULL COMMENT '操作人',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_piglet (piglet_ear_no),
  KEY idx_weaning (weaning_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='断奶仔猪重记录（BRD-EVENT-003）';

-- ------------------------------------------------------------
-- 17. t_farm_pig_death（猪只死亡记录，BRD-EVENT-004）
-- typo 修复: death_type x2 -> death_pig_type + death_kind
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_pig_death;
CREATE TABLE t_farm_pig_death (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id            BIGINT       NOT NULL COMMENT '猪只 ID',
  ear_no            VARCHAR(32)  NOT NULL COMMENT '猪只耳号',
  death_date        DATETIME     NOT NULL COMMENT '死亡日期',
  death_pig_type    VARCHAR(16)  NOT NULL COMMENT '死亡猪只类型 字典 pig_type：sow/boar/piglet/fattening',
  death_kind        VARCHAR(16)  NOT NULL COMMENT '死亡分类 字典 death_type：normal/abnormal',
  death_reason      VARCHAR(32)  NULL COMMENT '死亡原因 字典 death_reason',
  death_dest        VARCHAR(32)  NULL COMMENT '死亡去向 字典 death_dest',
  death_weight      DECIMAL(12,2) NULL COMMENT '死亡重量 kg',
  oss_ids           VARCHAR(1024) NULL COMMENT '照片 OSS IDs 多图逗号分隔',
  operator_id       BIGINT       NULL COMMENT '操作人',
  barn_name         VARCHAR(64)  NULL COMMENT '栋舍名称冗余',
  pen_name          VARCHAR(64)  NULL COMMENT '栏位名称冗余',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_pig (tenant_id, pig_id),
  KEY idx_death_date (death_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猪只死亡记录（BRD-EVENT-004）';

-- ------------------------------------------------------------
-- 18. t_farm_pig_culling（猪只淘汰记录，BRD-EVENT-004）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_pig_culling;
CREATE TABLE t_farm_pig_culling (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id            BIGINT       NOT NULL COMMENT '猪只 ID',
  ear_no            VARCHAR(32)  NOT NULL COMMENT '猪只耳号',
  culling_date      DATETIME     NOT NULL COMMENT '淘汰日期',
  culling_reason    VARCHAR(32)  NOT NULL COMMENT '淘汰原因 字典 eliminate_reason',
  culling_dest      VARCHAR(32)  NULL COMMENT '淘汰去向 字典 eliminate_dest',
  culling_weight    DECIMAL(12,2) NULL COMMENT '淘汰重量 kg',
  oss_ids           VARCHAR(1024) NULL COMMENT '照片 OSS IDs',
  operator_id       BIGINT       NULL COMMENT '操作人',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_pig (tenant_id, pig_id),
  KEY idx_culling_date (culling_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猪只淘汰记录（BRD-EVENT-004）';

-- ------------------------------------------------------------
-- 19. t_farm_castrate_record（猪只阉割记录，BRD-EVENT-004）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_castrate_record;
CREATE TABLE t_farm_castrate_record (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id            BIGINT       NOT NULL COMMENT '猪只 ID',
  ear_no            VARCHAR(32)  NOT NULL COMMENT '猪只耳号',
  castrate_date     DATETIME     NOT NULL COMMENT '阉割日期',
  operator_id       BIGINT       NULL COMMENT '操作人',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_pig (tenant_id, pig_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猪只阉割记录（BRD-EVENT-004）';

-- ------------------------------------------------------------
-- 20. t_farm_pig_transfer（猪只转移记录，BRD-EVENT-004）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_pig_transfer;
CREATE TABLE t_farm_pig_transfer (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id            BIGINT       NOT NULL COMMENT '猪只 ID',
  ear_no            VARCHAR(32)  NOT NULL COMMENT '猪只耳号',
  transfer_date     DATETIME     NOT NULL COMMENT '转移日期',
  old_barn_id       BIGINT       NULL COMMENT '原栋舍 ID',
  old_pen_id        BIGINT       NULL COMMENT '原栏位 ID',
  old_barn_name     VARCHAR(64)  NULL COMMENT '原栋舍名称冗余',
  old_pen_name      VARCHAR(64)  NULL COMMENT '原栏位名称冗余',
  new_barn_id       BIGINT       NOT NULL COMMENT '新栋舍 ID',
  new_pen_id        BIGINT       NULL COMMENT '新栏位 ID',
  new_barn_name     VARCHAR(64)  NULL COMMENT '新栋舍名称冗余',
  new_pen_name      VARCHAR(64)  NULL COMMENT '新栏位名称冗余',
  transfer_reason   VARCHAR(64)  NULL COMMENT '转移原因',
  operator_id       BIGINT       NULL COMMENT '操作人',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_pig (tenant_id, pig_id),
  KEY idx_transfer_date (transfer_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猪只转移记录（BRD-EVENT-004）';

-- ------------------------------------------------------------
-- 21. t_farm_pig_marketing（猪只出栏记录，BRD-EVENT-004）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_pig_marketing;
CREATE TABLE t_farm_pig_marketing (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id            BIGINT       NOT NULL COMMENT '猪只 ID',
  ear_no            VARCHAR(32)  NOT NULL COMMENT '猪只耳号',
  marketing_date    DATETIME     NOT NULL COMMENT '出栏日期',
  out_weight        DECIMAL(12,2) NOT NULL COMMENT '出栏重量 kg',
  out_dest          VARCHAR(32)  NOT NULL COMMENT '出栏去向 字典 out_house_dest：送宰/外销/...',
  store_id          BIGINT       NULL COMMENT '目标门店 ID（如适用）',
  is_room           TINYINT(1)   NULL DEFAULT 0 COMMENT '燎毛间是否接收 1=已接收 0=未接收',
  oss_ids           VARCHAR(1024) NULL COMMENT '照片 OSS IDs',
  operator_id       BIGINT       NULL COMMENT '操作人',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_pig (tenant_id, pig_id),
  KEY idx_marketing_date (marketing_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猪只出栏记录（BRD-EVENT-004）';

-- ------------------------------------------------------------
-- 22. t_farm_grow_record（猪只生长记录/背膘，BRD-EVENT-005）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_grow_record;
CREATE TABLE t_farm_grow_record (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id            BIGINT       NOT NULL COMMENT '猪只 ID',
  ear_no            VARCHAR(32)  NOT NULL COMMENT '猪只耳号',
  measure_date      DATETIME     NOT NULL COMMENT '测量日期',
  back_fat          DECIMAL(5,2) NULL COMMENT '背膘厚 mm',
  weight            DECIMAL(12,2) NULL COMMENT '体重 kg',
  age_days          INT          NULL COMMENT '日龄',
  oss_ids           VARCHAR(1024) NULL COMMENT '照片 OSS IDs（可选）',
  operator_id       BIGINT       NULL COMMENT '测量人',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_pig (tenant_id, pig_id),
  KEY idx_measure_date (measure_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猪只生长记录（BRD-EVENT-005）';

-- ============================================================
-- 用药管理（3 张）
-- ============================================================

-- ------------------------------------------------------------
-- 23. t_breed_medicine_info（药品库主数据，BRD-MED-001）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_breed_medicine_info;
CREATE TABLE t_breed_medicine_info (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  medicine_code     VARCHAR(32)  NOT NULL COMMENT '药品编码',
  medicine_name     VARCHAR(128) NOT NULL COMMENT '药品名称',
  medicine_type     VARCHAR(16)  NOT NULL COMMENT '药品类型 字典 drug_type：vaccine/health/treat',
  supplier_id       BIGINT       NULL COMMENT '供应商 ID',
  approval_no       VARCHAR(64)  NULL COMMENT '批准文号',
  batch_no          VARCHAR(64)  NULL COMMENT '批号',
  expire_date       DATE         NULL COMMENT '过期日期',
  withdraw_days     INT          NULL COMMENT '休药期（天）',
  unit              VARCHAR(16)  NULL COMMENT '单位（瓶/盒/克 等）',
  current_stock     DECIMAL(12,2) NULL DEFAULT 0 COMMENT '当前库存',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '创建人',
  create_time       DATETIME     NULL COMMENT '创建时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_medicine_code (tenant_id, medicine_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_medicine_type (medicine_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品库主数据（BRD-MED-001）';

-- ------------------------------------------------------------
-- 24. t_breed_medicine_use（药品领用/退回/损耗，BRD-MED-002）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_breed_medicine_use;
CREATE TABLE t_breed_medicine_use (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  medicine_id       BIGINT       NOT NULL COMMENT '药品 ID',
  use_date          DATE         NOT NULL COMMENT '领用日期（业务日期，BRD-MED-003 用此过滤"3 天内已领"）',
  inout_type        VARCHAR(16)  NOT NULL COMMENT '类型 字典 medicine_use_type：pick=领用/return=退回/loss=损耗',
  use_location      VARCHAR(32)  NULL COMMENT '领用位置 字典 medicine_use_location',
  use_count         DECIMAL(12,2) NOT NULL COMMENT '数量',
  operator_id       BIGINT       NULL COMMENT '操作人',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_medicine_date (medicine_id, use_date),
  KEY idx_use_date (use_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品领用/退回/损耗（BRD-MED-002）';

-- ------------------------------------------------------------
-- 25. t_farm_medicine_record（用药耗用流水/治疗记录，BRD-MED-003）
-- 表名按 _db-changes：t_farm_annual_indicator (用药) -> t_farm_medicine_record
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_medicine_record;
CREATE TABLE t_farm_medicine_record (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id            BIGINT       NULL COMMENT '猪只 ID（批量用药时为 NULL，明细在 detail）',
  ear_no            VARCHAR(32)  NULL COMMENT '耳号（单只用药时填）',
  batch_pig_ids     VARCHAR(2048) NULL COMMENT '批量猪只 ID 逗号分隔（批量用药时）',
  treat_date        DATETIME     NOT NULL COMMENT '用药日期',
  drug_type         VARCHAR(16)  NOT NULL COMMENT '用药类型 字典 drug_type：vaccine/health/treat',
  medicine_reason   VARCHAR(32)  NULL COMMENT '用药原因 字典 medicine_reason',
  medicine_id       BIGINT       NOT NULL COMMENT '药品 ID',
  dose              DECIMAL(12,2) NOT NULL COMMENT '用药量',
  medicine_way      VARCHAR(16)  NULL COMMENT '用药方式 字典 medicine_way',
  operator_id       BIGINT       NULL COMMENT '用药人',
  create_dept         BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '录入人',
  create_time       DATETIME     NULL COMMENT '录入时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_pig (tenant_id, pig_id),
  KEY idx_treat_date (treat_date),
  KEY idx_medicine (medicine_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用药耗用/治疗流水（BRD-MED-003，原 xlsx 误命名 t_farm_annual_indicator）';

-- ============================================================
-- 统计预聚合（4 张）— 不带 del_flag/update_by（_db-changes 全局规则）
-- ============================================================

-- ------------------------------------------------------------
-- 26. t_farm_sow_record（母猪日数据汇总，定时任务）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_sow_record;
CREATE TABLE t_farm_sow_record (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  stat_date         DATE         NOT NULL COMMENT '统计日期',
  sow_total         INT          NULL DEFAULT 0 COMMENT '母猪总数',
  sow_pregnant      INT          NULL DEFAULT 0 COMMENT '配怀母猪数',
  sow_farrow        INT          NULL DEFAULT 0 COMMENT '分娩母猪数',
  sow_weaning       INT          NULL DEFAULT 0 COMMENT '断奶母猪数',
  sow_idle          INT          NULL DEFAULT 0 COMMENT '空怀母猪数',
  sow_culling_count INT          NULL DEFAULT 0 COMMENT '当日淘汰母猪数',
  sow_death_count   INT          NULL DEFAULT 0 COMMENT '当日死亡母猪数',
  piglet_total      INT          NULL DEFAULT 0 COMMENT '仔猪总数',
  create_time       DATETIME     NULL COMMENT '记录创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_date (tenant_id, stat_date),
  KEY idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='母猪日数据汇总（统计预聚合，定时任务重算）';

-- ------------------------------------------------------------
-- 27. t_farm_sow_performance（母猪性能表，每日 update 每头母猪一行）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_sow_performance;
CREATE TABLE t_farm_sow_performance (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  pig_id            BIGINT       NOT NULL COMMENT '母猪 ID',
  ear_no            VARCHAR(32)  NOT NULL COMMENT '母猪耳号',
  parity            INT          NULL DEFAULT 0 COMMENT '累计胎次',
  total_born        INT          NULL DEFAULT 0 COMMENT '累计产仔数',
  total_live_born   INT          NULL DEFAULT 0 COMMENT '累计活产数',
  total_weaned      INT          NULL DEFAULT 0 COMMENT '累计断奶数',
  avg_born_weight   DECIMAL(8,3) NULL COMMENT '平均出生重 kg',
  avg_weaned_weight DECIMAL(8,3) NULL COMMENT '平均断奶重 kg',
  last_update_date  DATE         NULL COMMENT '最近统计日期',
  create_time       DATETIME     NULL COMMENT '记录创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_pig (tenant_id, pig_id),
  KEY idx_ear (ear_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='母猪性能表（每头母猪一行，定时刷新）';

-- ------------------------------------------------------------
-- 28. t_farm_monthly_production（养殖月指标统计，定时 update 当月）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_monthly_production;
CREATE TABLE t_farm_monthly_production (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  stat_month        CHAR(7)      NOT NULL COMMENT '统计月份 yyyy-MM',
  introduce_count   INT          NULL DEFAULT 0 COMMENT '当月引种数',
  born_count        INT          NULL DEFAULT 0 COMMENT '当月产仔数',
  weaned_count      INT          NULL DEFAULT 0 COMMENT '当月断奶数',
  death_count       INT          NULL DEFAULT 0 COMMENT '当月死亡数',
  culling_count     INT          NULL DEFAULT 0 COMMENT '当月淘汰数',
  marketing_count   INT          NULL DEFAULT 0 COMMENT '当月出栏数',
  marketing_weight  DECIMAL(15,2) NULL DEFAULT 0 COMMENT '当月出栏总重 kg',
  create_time       DATETIME     NULL COMMENT '记录创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_month (tenant_id, stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='养殖月指标统计（定时任务每日 update 当月）';

-- ------------------------------------------------------------
-- 29. t_farm_annual_indicator（年度指标，定时 update 当年）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_farm_annual_indicator;
CREATE TABLE t_farm_annual_indicator (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  stat_year         SMALLINT     NOT NULL COMMENT '统计年份',
  introduce_count   INT          NULL DEFAULT 0 COMMENT '年度引种数',
  born_count        INT          NULL DEFAULT 0 COMMENT '年度产仔数',
  weaned_count      INT          NULL DEFAULT 0 COMMENT '年度断奶数',
  death_count       INT          NULL DEFAULT 0 COMMENT '年度死亡数',
  culling_count     INT          NULL DEFAULT 0 COMMENT '年度淘汰数',
  marketing_count   INT          NULL DEFAULT 0 COMMENT '年度出栏数',
  marketing_weight  DECIMAL(15,2) NULL DEFAULT 0 COMMENT '年度出栏总重 kg',
  psy               DECIMAL(8,2) NULL COMMENT 'PSY（每头母猪年产断奶仔猪数）',
  mortality_rate    DECIMAL(5,2) NULL COMMENT '死亡率 %',
  create_time       DATETIME     NULL COMMENT '记录创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_year (tenant_id, stat_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年度指标（定时任务每日 update 当年）';

SET FOREIGN_KEY_CHECKS = 1;


-- ----------------------------------------------------------------------------
-- 来源文件：V202605200902__SYS-INIT-001-create-business-tables-plant.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- SYS-INIT-001 业务表 DDL — 种植域 PLT
-- 生成时间: 2026-05-20
-- 表数: 14 张（_db-changes 列出 13 张 + PLT-PLAN-002 新增 t_plant_pick_activity）
-- 强制规范: tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' / UNIQUE 含 del_unique 生成列 / 审计字段对齐 ruoyi
-- typo 修复: belong zone -> belong_zone / draiage_condition -> drainage_condition /
--           lrrigation_interval -> irrigation_interval / t_plant_crop_crop -> t_plant_crop_organic /
--           tillage_method -> tillage_way (与字典对齐)
-- 引用: doc/05-架构文档-ruoyi.md §6, doc/06-实现描述.md 第 3 章 (PLT-*), doc/_db-changes.md
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 主数据 + 关联（7 张）
-- ============================================================

-- ------------------------------------------------------------
-- 1. t_plant_plot_zone（地块片区表，PLT-MD-001）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_plot_zone;
CREATE TABLE t_plant_plot_zone (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  zone_code       VARCHAR(32)  NOT NULL COMMENT '片区编码',
  zone_name       VARCHAR(64)  NOT NULL COMMENT '片区名称',
  zone_belong     VARCHAR(64)  NULL COMMENT '归属部门/区域',
  zone_status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=启用 0=停用',
  total_area      DECIMAL(10,2) NULL COMMENT '片区总面积 亩（推断字段）',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_zone_code (tenant_id, zone_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地块片区表（PLT-MD-001）';

-- ------------------------------------------------------------
-- 2. t_plant_plot_info（地块信息表，PLT-MD-001）
-- typo 修复: belong zone / draiage_condition
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_plot_info;
CREATE TABLE t_plant_plot_info (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  plot_code           VARCHAR(32)  NOT NULL COMMENT '地块编码',
  plot_name           VARCHAR(64)  NOT NULL COMMENT '地块名称',
  belong_zone         VARCHAR(32)  NULL COMMENT '归属片区编码（typo 修复 belong zone -> belong_zone）',
  plot_type           VARCHAR(16)  NULL COMMENT '地块类型 字典',
  plot_status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=启用 0=停用',
  area                DECIMAL(10,2) NULL COMMENT '面积 亩',
  is_rented           TINYINT(1)   NULL DEFAULT 0 COMMENT '是否租赁 1=租赁 0=自有',
  soil_type           VARCHAR(32)  NULL COMMENT '土壤类型 字典 soil_type',
  soil_fertility      VARCHAR(32)  NULL COMMENT '土壤肥力 字典 soil_fertility',
  terrain_condition   VARCHAR(32)  NULL COMMENT '地势情况 字典 terrain_condition',
  light_condition     VARCHAR(32)  NULL COMMENT '光照条件 字典 light_condition',
  drainage_condition  VARCHAR(32)  NULL COMMENT '排水条件 字典 drain_condition（typo 修复 draiage -> drainage）',
  plot_location_x     DECIMAL(10,7) NULL COMMENT '经度',
  plot_location_y     DECIMAL(10,7) NULL COMMENT '纬度',
  oss_ids             VARCHAR(1024) NULL COMMENT '图片 OSS IDs 多图逗号分隔',
  create_dept           BIGINT       NULL COMMENT '创建部门',
  create_by           BIGINT       NULL COMMENT '创建人',
  create_time         DATETIME     NULL COMMENT '创建时间',
  update_by           BIGINT       NULL COMMENT '更新人',
  update_time         DATETIME     NULL COMMENT '更新时间',
  del_flag            CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark              VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_plot_code (tenant_id, plot_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_zone (belong_zone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地块信息表（PLT-MD-001）';

-- ------------------------------------------------------------
-- 3. t_plant_crop_info（作物信息，PLT-MD-001）
-- typo 修复: lrrigation_interval -> irrigation_interval
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_crop_info;
CREATE TABLE t_plant_crop_info (
  id                    BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id             VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  crop_code             VARCHAR(32)  NOT NULL COMMENT '作物编码',
  variety_name          VARCHAR(64)  NOT NULL COMMENT '品种名称',
  crop_family           VARCHAR(32)  NULL COMMENT '作物科属 字典 crop_family',
  planting_season       VARCHAR(16)  NULL COMMENT '种植季节',
  max_cycle             INT          NULL COMMENT '最大生长周期（天）',
  min_cycle             INT          NULL COMMENT '最小生长周期（天）',
  predicted_per         DECIMAL(10,2) NULL COMMENT '预计亩产 kg',
  irrigation_interval   INT          NULL COMMENT '灌溉间隔（天，typo 修复 lrrigation -> irrigation）',
  pick_unit_price       DECIMAL(10,2) NULL COMMENT '采摘单价（PLT-PERF-001 用，元/kg）',
  oss_ids               VARCHAR(1024) NULL COMMENT '图片 OSS IDs',
  create_dept             BIGINT       NULL COMMENT '创建部门',
  create_by             BIGINT       NULL COMMENT '创建人',
  create_time           DATETIME     NULL COMMENT '创建时间',
  update_by             BIGINT       NULL COMMENT '更新人',
  update_time           DATETIME     NULL COMMENT '更新时间',
  del_flag              CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark                VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_crop_code (tenant_id, crop_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作物信息（PLT-MD-001）';

-- ------------------------------------------------------------
-- 4. t_plant_zone_plotno（片区地块关联表，M:N）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_zone_plotno;
CREATE TABLE t_plant_zone_plotno (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  zone_id         BIGINT       NOT NULL COMMENT '片区 ID',
  plot_id         BIGINT       NOT NULL COMMENT '地块 ID',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_zone_plot (tenant_id, zone_id, plot_id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_plot (plot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='片区地块关联表（M:N，硬删）';

-- ------------------------------------------------------------
-- 5. t_plant_plot_organic（地块有机证书信息表，PLT-MD-003）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_plot_organic;
CREATE TABLE t_plant_plot_organic (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  organic_no      VARCHAR(64)  NOT NULL COMMENT '证书编号',
  organic_company VARCHAR(128) NULL COMMENT '认证机构',
  organic_valid   DATE         NULL COMMENT '证书有效期（typo 修复 vaild -> valid）',
  is_warning      TINYINT(1)   NULL DEFAULT 0 COMMENT '是否预警 1=是 0=否',
  oss_ids         VARCHAR(1024) NULL COMMENT '证书图片 OSS IDs',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_organic_no (tenant_id, organic_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_warning (is_warning)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地块有机证书信息表（PLT-MD-003）';

-- ------------------------------------------------------------
-- 6. t_plant_organic_plotno（有机证书与地块关联表，M:N）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_organic_plotno;
CREATE TABLE t_plant_organic_plotno (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  organic_id      BIGINT       NOT NULL COMMENT '证书 ID',
  plot_id         BIGINT       NOT NULL COMMENT '地块 ID',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_organic_plot (tenant_id, organic_id, plot_id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_plot (plot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='有机证书与地块关联表（M:N，硬删）';

-- ------------------------------------------------------------
-- 7. t_plant_crop_organic（作物有机证书信息表，PLT-MD-003）
-- typo 修复: 原 t_plant_crop_crop -> t_plant_crop_organic
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_crop_organic;
CREATE TABLE t_plant_crop_organic (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  crop_no         VARCHAR(64)  NOT NULL COMMENT '证书编号',
  crop_company    VARCHAR(128) NULL COMMENT '认证机构',
  crop_valid      DATE         NULL COMMENT '证书有效期',
  crop_id         BIGINT       NULL COMMENT '关联作物 ID',
  is_warning      TINYINT(1)   NULL DEFAULT 0 COMMENT '是否预警 1=是 0=否',
  oss_ids         VARCHAR(1024) NULL COMMENT '证书图片 OSS IDs',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_crop_no (tenant_id, crop_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_crop (crop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作物有机证书信息表（PLT-MD-003，typo 修复 crop_crop -> crop_organic）';

-- ============================================================
-- 计划 + 农事（4 张）
-- ============================================================

-- ------------------------------------------------------------
-- 8. t_plant_plant_plan（种植采摘计划表，PLT-PLAN-001）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_plant_plan;
CREATE TABLE t_plant_plant_plan (
  id                    BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id             VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  plan_no               VARCHAR(32)  NOT NULL COMMENT '计划编号',
  plan_year             SMALLINT     NOT NULL COMMENT '计划年份',
  plan_crop             BIGINT       NOT NULL COMMENT '计划作物 ID',
  planting_season       VARCHAR(16)  NULL COMMENT '种植季节',
  plant_date            DATE         NULL COMMENT '计划种植日期',
  earliest_harvestdate  DATE         NULL COMMENT '最早采收日期',
  total_area            DECIMAL(10,2) NULL COMMENT '总面积 亩',
  total_predicted_yield DECIMAL(15,2) NULL COMMENT '计划预计总产量 kg（推断字段，便于双甘特图）',
  plant_status          TINYINT      NOT NULL DEFAULT 1 COMMENT '计划状态 1=草稿 2=待开始 3=正常执行 4=已完成 5=延期',
  create_dept             BIGINT       NULL COMMENT '创建部门',
  create_by             BIGINT       NULL COMMENT '创建人',
  create_time           DATETIME     NULL COMMENT '创建时间',
  update_by             BIGINT       NULL COMMENT '更新人',
  update_time           DATETIME     NULL COMMENT '更新时间',
  del_flag              CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark                VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_plan_no (tenant_id, plan_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_plan_year (plan_year),
  KEY idx_plan_crop (plan_crop)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植采摘计划表（PLT-PLAN-001）';

-- ------------------------------------------------------------
-- 9. t_plant_plant_details（地块种植列表/明细，PLT-PLAN-001）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_plant_details;
CREATE TABLE t_plant_plant_details (
  id                    BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id             VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  plant_id              BIGINT       NOT NULL COMMENT '种植计划 ID',
  plot_id               BIGINT       NOT NULL COMMENT '地块 ID',
  crop_id               BIGINT       NOT NULL COMMENT '作物 ID',
  plant_money           DECIMAL(12,2) NULL COMMENT '种植成本预算 元',
  plant_date            DATE         NULL COMMENT '计划种植日期（按月+上中下旬）',
  begin_plantdate       DATE         NULL COMMENT '计划开始种植日期',
  end_plantdate         DATE         NULL COMMENT '计划结束种植日期',
  begin_pickdate        DATE         NULL COMMENT '计划开始采摘日期',
  end_pickdate          DATE         NULL COMMENT '计划结束采摘日期',
  start_actualdate      DATE         NULL COMMENT '实际开始种植日期',
  end_actualdate        DATE         NULL COMMENT '实际结束采摘日期',
  predicted_yield       DECIMAL(12,2) NULL COMMENT '预计产量 kg',
  actual_yield          DECIMAL(12,2) NULL DEFAULT 0 COMMENT '实际采摘产量 kg（采摘录入累加）',
  loss_yield            DECIMAL(12,2) NULL DEFAULT 0 COMMENT '损失产量 kg（灾害记录累加）',
  area                  DECIMAL(10,2) NULL COMMENT '面积 亩',
  is_pick               TINYINT(1)   NULL DEFAULT 0 COMMENT '是否采摘完成 1=完成 0=未完成',
  is_activity           TINYINT(1)   NULL DEFAULT 0 COMMENT '是否采摘活动',
  team_id               BIGINT       NULL COMMENT '关联班组 ID',
  create_dept             BIGINT       NULL COMMENT '创建部门',
  create_by             BIGINT       NULL COMMENT '创建人',
  create_time           DATETIME     NULL COMMENT '创建时间',
  update_by             BIGINT       NULL COMMENT '更新人',
  update_time           DATETIME     NULL COMMENT '更新时间',
  del_flag              CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark                VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_plan_plot (plant_id, plot_id),
  KEY idx_dates (begin_plantdate, end_plantdate),
  KEY idx_plot (plot_id),
  KEY idx_crop (crop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地块种植列表（PLT-PLAN-001）';

-- ------------------------------------------------------------
-- 10. t_plant_farm_records（农事记录，PLT-WORK-001）
-- typo 修复: tillage_method -> tillage_way（与字典对齐）
-- 新增字段: material_type / material_id（看板查询"今天用了什么肥"，建议补）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_farm_records;
CREATE TABLE t_plant_farm_records (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  plant_id            BIGINT       NULL COMMENT '种植计划 ID',
  plot_id             BIGINT       NOT NULL COMMENT '地块 ID',
  crop_id             BIGINT       NULL COMMENT '作物 ID',
  team_id             BIGINT       NULL COMMENT '班组 ID',
  farm_type           VARCHAR(16)  NOT NULL COMMENT '农事类型 字典 farm_work_type：12 类（翻耕/整地/施肥/浇灌/除草/...）',
  farm_date           DATE         NOT NULL COMMENT '作业日期',
  farm_by             BIGINT       NULL COMMENT '作业人 user_id',
  tillage_type        VARCHAR(32)  NULL COMMENT '整地类型 字典 tillage_type',
  tillage_way         VARCHAR(32)  NULL COMMENT '整地方式 字典 tillage_way（typo 修复 tillage_method -> tillage_way）',
  material_type       VARCHAR(32)  NULL COMMENT '用药/施肥种类（推断字段，建议补，含农药/化肥/有机肥等）',
  material_id         BIGINT       NULL COMMENT '材料 ID（关联仓库药品/化肥）',
  material_dose       DECIMAL(12,2) NULL COMMENT '使用量',
  disaster_type       VARCHAR(32)  NULL COMMENT '灾害类型（farm_type=disaster 时填）',
  loss_yield          DECIMAL(12,2) NULL COMMENT '损失产量 kg（disaster 时填）',
  loss_rate           DECIMAL(5,2) NULL COMMENT '损失率 %',
  transplant_rate     DECIMAL(5,2) NULL COMMENT '移栽百分比 %（farm_type=transplant 时填）',
  oss_ids             VARCHAR(1024) NULL COMMENT '现场照片 OSS IDs',
  is_warning          TINYINT(1)   NULL DEFAULT 0 COMMENT '是否预警 1=是 0=否',
  create_dept           BIGINT       NULL COMMENT '创建部门',
  create_by           BIGINT       NULL COMMENT '录入人',
  create_time         DATETIME     NULL COMMENT '录入时间',
  update_by           BIGINT       NULL COMMENT '更新人',
  update_time         DATETIME     NULL COMMENT '更新时间',
  del_flag            CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark              VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_plot_date (plot_id, farm_date),
  KEY idx_farm_type (farm_type),
  KEY idx_plant (plant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农事记录（PLT-WORK-001，含 12 类）';

-- ------------------------------------------------------------
-- 11. t_plant_pick_activity（采摘活动表，PLT-PLAN-002 新增）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_pick_activity;
CREATE TABLE t_plant_pick_activity (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  activity_no     VARCHAR(32)  NOT NULL COMMENT '活动编号',
  activity_date   DATE         NOT NULL COMMENT '活动日期',
  crop_id         BIGINT       NOT NULL COMMENT '作物 ID',
  activity_name   VARCHAR(128) NULL COMMENT '活动名称',
  daily_pick_weight DECIMAL(12,2) NULL DEFAULT 0 COMMENT '当日采摘总重 kg',
  participant_count INT        NULL COMMENT '参与人数',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_no (tenant_id, activity_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_activity_date (activity_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采摘活动表（PLT-PLAN-002）';

-- ============================================================
-- 班组 + 绩效（3 张）
-- ============================================================

-- ------------------------------------------------------------
-- 12. t_plant_work_team（班组表，PLT-MD-002）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_work_team;
CREATE TABLE t_plant_work_team (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  team_code       VARCHAR(32)  NOT NULL COMMENT '班组编码',
  team_name       VARCHAR(64)  NOT NULL COMMENT '班组名称',
  leader_id       BIGINT       NULL COMMENT '班组长 user_id',
  team_status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=启用 0=停用',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_code (tenant_id, team_code, del_unique),
  KEY idx_tenant_create (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班组表（PLT-MD-002）';

-- ------------------------------------------------------------
-- 13. t_plant_work_people（班组人员表，M:N，关联 sys_user）
-- 业务约束: 一人只能在一个班组（service 层保证）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_work_people;
CREATE TABLE t_plant_work_people (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  work_id         BIGINT       NOT NULL COMMENT '班组 ID',
  people_id       BIGINT       NOT NULL COMMENT '人员 user_id（关联 sys_user.user_id）',
  is_leader       TINYINT(1)   NULL DEFAULT 0 COMMENT '是否班组长 1=是 0=否',
  join_date       DATE         NULL COMMENT '加入日期',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '创建人',
  create_time     DATETIME     NULL COMMENT '创建时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(500) NULL COMMENT '备注',
  del_unique          BIGINT       NOT NULL DEFAULT 0 COMMENT "软删除生成 token（应用层 update del_flag='1' 时同步 SET del_unique=id；§6.3.0）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_people (tenant_id, work_id, people_id, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_people (people_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班组人员表（PLT-MD-002，关联 sys_user）';

-- ------------------------------------------------------------
-- 14. t_plant_work_performance（绩效表，PLT-PERF-001，月度结算）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_work_performance;
CREATE TABLE t_plant_work_performance (
  id                    BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id             VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  stat_month            CHAR(7)      NOT NULL COMMENT '统计月份 yyyy-MM',
  team_id               BIGINT       NULL COMMENT '班组 ID',
  people_id             BIGINT       NULL COMMENT '人员 user_id',
  crop_id               BIGINT       NULL COMMENT '作物 ID',
  pick_weight           DECIMAL(12,2) NULL DEFAULT 0 COMMENT '采摘重量 kg',
  unit_price_snapshot   DECIMAL(10,2) NULL COMMENT '单价快照（不受历史影响）',
  performance_amount    DECIMAL(12,2) NULL DEFAULT 0 COMMENT '应付绩效金额 元',
  performance_rule      VARCHAR(255) NULL COMMENT '绩效规则说明',
  create_dept             BIGINT       NULL COMMENT '创建部门',
  create_by             BIGINT       NULL COMMENT '创建人',
  create_time           DATETIME     NULL COMMENT '创建时间',
  update_by             BIGINT       NULL COMMENT '更新人',
  update_time           DATETIME     NULL COMMENT '更新时间',
  del_flag              CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark                VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_month_team (stat_month, team_id),
  KEY idx_people (people_id),
  KEY idx_crop (crop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绩效表（PLT-PERF-001）';

SET FOREIGN_KEY_CHECKS = 1;


-- ----------------------------------------------------------------------------
-- 来源文件：V202605200903__SYS-INIT-001-create-business-tables-warehouse.sql
-- ----------------------------------------------------------------------------
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


-- ----------------------------------------------------------------------------
-- 来源文件：V202605200904__SYS-INIT-001-create-business-tables-store.sql
-- ----------------------------------------------------------------------------
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


-- ----------------------------------------------------------------------------
-- 来源文件：V202605200905__SYS-INIT-001-extend-ruoyi-tables.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- SYS-INIT-001 — 步骤 3：扩展 ruoyi 自带 sys_* 表
-- 生成时间: 2026-05-20
-- 内容：sys_user / sys_oss_config / sys_client / sys_post 扩字段
-- 引用: doc/06-实现描述.md SYS-AUTH-001 / SYS-INFRA-002 / SYS-INFRA-006
-- ============================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------
-- 1. sys_user 加多农场 + 微信字段
--    farm_id 类型对齐 sys_user.tenant_id（VARCHAR(20)）
-- ----------------------------------------------------------------
ALTER TABLE sys_user
  ADD COLUMN farm_id              VARCHAR(20)  DEFAULT NULL COMMENT '当前默认农场 id（V1 默认 "1001"）',
  ADD COLUMN current_farm_id      VARCHAR(20)  DEFAULT NULL COMMENT '当前激活的农场 id（用户切换后更新）',
  ADD COLUMN accessible_farm_ids  VARCHAR(500) DEFAULT NULL COMMENT '可访问的农场 id 列表（逗号分隔，多农场用户用）',
  ADD COLUMN wx_openid            VARCHAR(64)  DEFAULT NULL COMMENT '微信 openid',
  ADD COLUMN wx_unionid           VARCHAR(64)  DEFAULT NULL COMMENT '微信 unionid',
  ADD INDEX idx_wx_openid (wx_openid);

-- ----------------------------------------------------------------
-- 2. sys_oss_config 加 STS 字段
-- ----------------------------------------------------------------
ALTER TABLE sys_oss_config
  ADD COLUMN sts_role_arn         VARCHAR(200) DEFAULT NULL COMMENT 'STS 角色 ARN（阿里云）',
  ADD COLUMN sts_session_duration INT          DEFAULT 3600 COMMENT 'STS 会话有效期（秒）';

-- ----------------------------------------------------------------
-- 3. sys_client 新增小程序客户端
--    id 显式赋值（ruoyi sys_client 无 AUTO_INCREMENT，dev seed 用低位 id）
--    client_secret 是 dev 占位，prod 部署时由运维替换
-- ----------------------------------------------------------------
INSERT INTO sys_client
  (id, client_id, client_key, client_secret, grant_type, device_type, active_timeout, timeout, status, del_flag, create_time, update_time)
VALUES
  (3, 'mp-applet-dongjiaoshan', 'mp_dongjiaoshan', 'djs_mp_dev_placeholder_replace_in_prod', 'wechat,password', 'mp', 1800, 604800, '0', '0', NOW(), NOW());

-- ----------------------------------------------------------------
-- 4. sys_post 加微信角色码
-- ----------------------------------------------------------------
ALTER TABLE sys_post
  ADD COLUMN wx_role_code VARCHAR(64) DEFAULT NULL COMMENT '小程序角色码（pig_keeper / planter / warehouse_keeper / store_clerk 等）';


-- ----------------------------------------------------------------------------
-- 来源文件：V202605200906__SYS-INIT-001-cleanup-ruoyi-menus.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- SYS-INIT-001 — 步骤 4：清理 ruoyi 自带不用的菜单
-- 生成时间: 2026-05-20
-- 原则：隐藏（visible='1'）而非物理删，便于回滚；demo 物理删（不会再用）
-- ruoyi 约定：visible '0'=显示 '1'=隐藏
-- ============================================================

SET NAMES utf8mb4;

-- 服务器监控（不用）— 服务监控 / 缓存监控 / 在线用户
UPDATE sys_menu SET visible='1' WHERE menu_name IN ('服务监控', '缓存监控', '在线用户');

-- 系统工具 — 表单构建 / 通知公告 不用
UPDATE sys_menu SET visible='1' WHERE menu_name IN ('表单构建', '通知公告');

-- demo 模块菜单物理删（确认不再用）
DELETE FROM sys_menu WHERE perms LIKE 'demo:%';


-- ----------------------------------------------------------------------------
-- 来源文件：V202605201000__SYS-INIT-002-init-dict.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- SYS-INIT-002 字典数据初始化
-- 生成时间: 2026-05-20
-- 范围: 38 类 dict_type + 约 224 条 dict_data
--   A 系统通用 6 类 (dict_id 100000-100099, dict_code 1000000-1000999)
--   B 养殖域   8 类 (dict_id 100100-100199, dict_code 1001000-1001999)
--   C 种植域   5 类 (dict_id 100200-100249, dict_code 1002000-1002499)
--   D 种植空白 8 类 (dict_id 100250-100299, dict_code 1002500-1002999)  -- doc/02 v1.1 + doc/06 要求
--   E 仓库域   6 类 (dict_id 100300-100399, dict_code 1003000-1003999)
--   F 门店域   3 类 (dict_id 100400-100499, dict_code 1004000-1004999)
--   G 跨域/追溯 2 类 (dict_id 100500-100599, dict_code 1005000-1005999)
-- 约束:
--   1. 全部 INSERT IGNORE 幂等（PK / UNIQUE(tenant_id, dict_type) 撞了即跳过）
--   2. tenant_id 全 '1001'（与 SYS-INIT-001 CR-20260520-01 一致，VARCHAR(20)）
--   3. dict_type 前缀 'djs_'
--   4. dict_label 中文 / dict_value 英文蛇形或大写枚举
--   5. create_by / create_dept 为 bigint，置 NULL（ruoyi 5.x 设计：业务字典无具体创建人）
-- 引用: doc/02-需求拆解-v1.2.md §SYS-INIT-002, doc/06-实现描述.md §SYS-INIT-002
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- A. 系统通用（6 类）
-- ============================================================

-- A1 djs_user_status 用户状态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100001, '1001', '用户状态', 'djs_user_status', NULL, NOW(), '系统通用：员工档案状态');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1000010, '1001', 0, '在职', '0', 'djs_user_status', '', 'primary', 'Y', NULL, NOW()),
  (1000011, '1001', 1, '离职', '1', 'djs_user_status', '', 'danger',  'N', NULL, NOW()),
  (1000012, '1001', 2, '试用', '2', 'djs_user_status', '', 'warning', 'N', NULL, NOW());

-- A2 djs_role_code 系统角色（13 个，v1.2 含 boss + manager）
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100002, '1001', '系统角色', 'djs_role_code', NULL, NOW(), '系统通用：13 种业务角色 code');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1000020, '1001',  0, '老板',           'boss',             'djs_role_code', '', 'primary', 'N', NULL, NOW()),
  (1000021, '1001',  1, '管理人员',       'manager',          'djs_role_code', '', 'primary', 'N', NULL, NOW()),
  (1000022, '1001',  2, '系统管理员',     'admin',            'djs_role_code', '', 'danger',  'N', NULL, NOW()),
  (1000023, '1001',  3, '养猪员',         'pig_keeper',       'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000024, '1001',  4, '种植员',         'planter',          'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000025, '1001',  5, '仓管员',         'warehouse_keeper', 'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000026, '1001',  6, '门店员工',       'store_clerk',      'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000027, '1001',  7, '店长',           'store_manager',    'djs_role_code', '', 'success', 'N', NULL, NOW()),
  (1000028, '1001',  8, '调度员',         'dispatcher',       'djs_role_code', '', 'warning', 'N', NULL, NOW()),
  (1000029, '1001',  9, '屠宰员',         'butcher',          'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000030, '1001', 10, '打包员',         'packer',           'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000031, '1001', 11, '司机',           'driver',           'djs_role_code', '', 'info',    'N', NULL, NOW()),
  (1000032, '1001', 12, '顾客',           'customer',         'djs_role_code', '', '',        'N', NULL, NOW());

-- A3 djs_farm_status 农场状态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100003, '1001', '农场状态', 'djs_farm_status', NULL, NOW(), '系统通用：sys_farm.farm_status');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1000040, '1001', 0, '启用', '0', 'djs_farm_status', '', 'primary', 'Y', NULL, NOW()),
  (1000041, '1001', 1, '停用', '1', 'djs_farm_status', '', 'danger',  'N', NULL, NOW());

-- A4 djs_store_status 门店状态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100004, '1001', '门店状态', 'djs_store_status', NULL, NOW(), '系统通用：t_md_store.status');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1000050, '1001', 0, '启用',   '0', 'djs_store_status', '', 'primary', 'Y', NULL, NOW()),
  (1000051, '1001', 1, '停用',   '1', 'djs_store_status', '', 'danger',  'N', NULL, NOW()),
  (1000052, '1001', 2, '装修中', '2', 'djs_store_status', '', 'warning', 'N', NULL, NOW());

-- A5 djs_supplier_type 供应商类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100005, '1001', '供应商类型', 'djs_supplier_type', NULL, NOW(), '系统通用：t_md_supplier.supplier_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1000060, '1001', 0, '饲料',     'feed',  'djs_supplier_type', '', 'primary', 'N', NULL, NOW()),
  (1000061, '1001', 1, '种猪',     'breed', 'djs_supplier_type', '', 'primary', 'N', NULL, NOW()),
  (1000062, '1001', 2, '兽药',     'med',   'djs_supplier_type', '', 'warning', 'N', NULL, NOW()),
  (1000063, '1001', 3, '蔬菜种子', 'seed',  'djs_supplier_type', '', 'success', 'N', NULL, NOW()),
  (1000064, '1001', 4, '包材',     'pack',  'djs_supplier_type', '', 'info',    'N', NULL, NOW()),
  (1000065, '1001', 5, '其他',     'other', 'djs_supplier_type', '', '',        'N', NULL, NOW());

-- A6 djs_yes_no 通用是否
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100006, '1001', '通用是否', 'djs_yes_no', NULL, NOW(), '系统通用：1=是 / 0=否');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1000070, '1001', 0, '是', '1', 'djs_yes_no', '', 'primary', 'N', NULL, NOW()),
  (1000071, '1001', 1, '否', '0', 'djs_yes_no', '', 'info',    'Y', NULL, NOW());

-- ============================================================
-- B. 养殖域（10 类）
-- ============================================================

-- B1 djs_pig_gender 猪只性别
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100101, '1001', '猪只性别', 'djs_pig_gender', NULL, NOW(), '养殖：公/母');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001010, '1001', 0, '公', 'M', 'djs_pig_gender', '', 'primary', 'N', NULL, NOW()),
  (1001011, '1001', 1, '母', 'F', 'djs_pig_gender', '', 'success', 'N', NULL, NOW());

-- B2 djs_pig_breed 品种
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100102, '1001', '猪只品种', 'djs_pig_breed', NULL, NOW(), '养殖：杜洛克/长白/大白 等');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001020, '1001', 0, '杜洛克', 'duroc',     'djs_pig_breed', '', 'primary', 'N', NULL, NOW()),
  (1001021, '1001', 1, '长白',   'landrace',  'djs_pig_breed', '', 'primary', 'N', NULL, NOW()),
  (1001022, '1001', 2, '大白',   'yorkshire', 'djs_pig_breed', '', 'primary', 'N', NULL, NOW()),
  (1001023, '1001', 3, 'PIC',    'pic',       'djs_pig_breed', '', 'success', 'N', NULL, NOW()),
  (1001024, '1001', 4, '二元',   'binary',    'djs_pig_breed', '', 'info',    'N', NULL, NOW()),
  (1001025, '1001', 5, '三元',   'ternary',   'djs_pig_breed', '', 'info',    'N', NULL, NOW()),
  (1001026, '1001', 6, '其他',   'other',     'djs_pig_breed', '', '',        'N', NULL, NOW());

-- B3 djs_pig_lifecycle 猪只生命周期阶段（状态机 10 状态 — 与 BRD-CORE-001 enum 必须严格一致）
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100103, '1001', '猪只生命周期', 'djs_pig_lifecycle', NULL, NOW(), '养殖：状态机 10 状态，BRD-CORE-001 enum 严格对齐');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001030, '1001', 0, '后备',    'HB',                  'djs_pig_lifecycle', '', 'info',    'Y', NULL, NOW()),
  (1001031, '1001', 1, '配种',    'PZ',                  'djs_pig_lifecycle', '', 'primary', 'N', NULL, NOW()),
  (1001032, '1001', 2, '配怀',    'PH',                  'djs_pig_lifecycle', '', 'primary', 'N', NULL, NOW()),
  (1001033, '1001', 3, '分娩',    'FM',                  'djs_pig_lifecycle', '', 'success', 'N', NULL, NOW()),
  (1001034, '1001', 4, '断奶',    'DN',                  'djs_pig_lifecycle', '', 'success', 'N', NULL, NOW()),
  (1001035, '1001', 5, '流产',    'LC',                  'djs_pig_lifecycle', '', 'warning', 'N', NULL, NOW()),
  (1001036, '1001', 6, '空怀',    'KH',                  'djs_pig_lifecycle', '', 'warning', 'N', NULL, NOW()),
  (1001037, '1001', 7, '返情',    'FQ',                  'djs_pig_lifecycle', '', 'warning', 'N', NULL, NOW()),
  (1001038, '1001', 8, '终止',    'END',                 'djs_pig_lifecycle', '', 'danger',  'N', NULL, NOW()),
  (1001039, '1001', 9, '公猪在产', 'BOAR_ACTIVE',        'djs_pig_lifecycle', '', 'info',    'N', NULL, NOW());

-- B4 djs_pig_status_event 猪只状态机事件（11 个，BRD-CORE-001 严格对齐）
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100104, '1001', '状态机事件', 'djs_pig_status_event', NULL, NOW(), '养殖：状态机 11 事件，BRD-CORE-001 enum 严格对齐');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001040, '1001',  0, '引种',     'INTRO',       'djs_pig_status_event', '', 'primary', 'N', NULL, NOW()),
  (1001041, '1001',  1, '配种',     'BREED',       'djs_pig_status_event', '', 'primary', 'N', NULL, NOW()),
  (1001042, '1001',  2, '分娩',     'FARROW',      'djs_pig_status_event', '', 'success', 'N', NULL, NOW()),
  (1001043, '1001',  3, '断奶',     'WEAN',        'djs_pig_status_event', '', 'success', 'N', NULL, NOW()),
  (1001044, '1001',  4, '查情',     'OESTRUS',     'djs_pig_status_event', '', 'info',    'N', NULL, NOW()),
  (1001045, '1001',  5, '返空',     'NULL_RETURN', 'djs_pig_status_event', '', 'warning', 'N', NULL, NOW()),
  (1001046, '1001',  6, '死亡',     'DIE',         'djs_pig_status_event', '', 'danger',  'N', NULL, NOW()),
  (1001047, '1001',  7, '淘汰',     'ELIMINATE',   'djs_pig_status_event', '', 'danger',  'N', NULL, NOW()),
  (1001048, '1001',  8, '阉割',     'CASTRATE',    'djs_pig_status_event', '', 'info',    'N', NULL, NOW()),
  (1001049, '1001',  9, '转移',     'TRANSFER',    'djs_pig_status_event', '', 'info',    'N', NULL, NOW()),
  (1001050, '1001', 10, '出栏',     'SLAUGHTER',   'djs_pig_status_event', '', 'warning', 'N', NULL, NOW());

-- B5 djs_barn_type 栋舍类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100105, '1001', '栋舍类型', 'djs_barn_type', NULL, NOW(), '养殖：t_farm_barn.barn_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001060, '1001', 0, '配种舍', 'breeding',  'djs_barn_type', '', 'primary', 'N', NULL, NOW()),
  (1001061, '1001', 1, '妊娠舍', 'pregnant',  'djs_barn_type', '', 'primary', 'N', NULL, NOW()),
  (1001062, '1001', 2, '产房',   'farrow',    'djs_barn_type', '', 'success', 'N', NULL, NOW()),
  (1001063, '1001', 3, '保育舍', 'nursery',   'djs_barn_type', '', 'success', 'N', NULL, NOW()),
  (1001064, '1001', 4, '育肥舍', 'fattening', 'djs_barn_type', '', 'warning', 'N', NULL, NOW()),
  (1001065, '1001', 5, '隔离舍', 'isolation', 'djs_barn_type', '', 'danger',  'N', NULL, NOW());

-- B6 djs_pen_type 栏位类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100106, '1001', '栏位类型', 'djs_pen_type', NULL, NOW(), '养殖：t_farm_pen.pen_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001070, '1001', 0, '公栏',   'male',   'djs_pen_type', '', 'primary', 'N', NULL, NOW()),
  (1001071, '1001', 1, '母栏',   'female', 'djs_pen_type', '', 'success', 'N', NULL, NOW()),
  (1001072, '1001', 2, '限位栏', 'stall',  'djs_pen_type', '', 'warning', 'N', NULL, NOW()),
  (1001073, '1001', 3, '群栏',   'group',  'djs_pen_type', '', 'info',    'N', NULL, NOW());

-- B7 djs_med_type 药品类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100107, '1001', '药品类型', 'djs_med_type', NULL, NOW(), '养殖：t_farm_med.med_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001080, '1001', 0, '抗生素',   'antibiotic',   'djs_med_type', '', 'danger',  'N', NULL, NOW()),
  (1001081, '1001', 1, '疫苗',     'vaccine',      'djs_med_type', '', 'primary', 'N', NULL, NOW()),
  (1001082, '1001', 2, '营养剂',   'nutrition',    'djs_med_type', '', 'success', 'N', NULL, NOW()),
  (1001083, '1001', 3, '消毒剂',   'disinfectant', 'djs_med_type', '', 'warning', 'N', NULL, NOW()),
  (1001084, '1001', 4, '其他',     'other',        'djs_med_type', '', '',        'N', NULL, NOW());

-- B8 djs_elimination_reason 淘汰原因
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100108, '1001', '淘汰原因', 'djs_elimination_reason', NULL, NOW(), '养殖：淘汰事件原因分类');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001090, '1001', 0, '年龄', 'age',         'djs_elimination_reason', '', 'info',    'N', NULL, NOW()),
  (1001091, '1001', 1, '疾病', 'disease',     'djs_elimination_reason', '', 'danger',  'N', NULL, NOW()),
  (1001092, '1001', 2, '性能', 'performance', 'djs_elimination_reason', '', 'warning', 'N', NULL, NOW()),
  (1001093, '1001', 3, '其他', 'other',       'djs_elimination_reason', '', '',        'N', NULL, NOW());

-- B9 djs_pig_type 猪只类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100109, '1001', '猪只类型', 'djs_pig_type', NULL, NOW(), '养殖：t_farm_pig_info.pig_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001100, '1001', 0, '母猪',   'sow',       'djs_pig_type', '', 'success', 'Y', NULL, NOW()),
  (1001101, '1001', 1, '公猪',   'boar',      'djs_pig_type', '', 'primary', 'N', NULL, NOW()),
  (1001102, '1001', 2, '仔猪',   'piglet',    'djs_pig_type', '', 'info',    'N', NULL, NOW()),
  (1001103, '1001', 3, '育肥猪', 'fattening', 'djs_pig_type', '', 'warning', 'N', NULL, NOW());

-- B10 djs_pig_end_reason 猪只终止原因
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100110, '1001', '终止原因', 'djs_pig_end_reason', NULL, NOW(), '养殖：t_farm_pig_info.end_reason（DIE→DEAD / ELIMINATE→CULL / SLAUGHTER→MARKET）');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001110, '1001', 0, '死亡', 'DEAD',   'djs_pig_end_reason', '', 'danger',  'N', NULL, NOW()),
  (1001111, '1001', 1, '淘汰', 'CULL',   'djs_pig_end_reason', '', 'warning', 'N', NULL, NOW()),
  (1001112, '1001', 2, '出栏', 'MARKET', 'djs_pig_end_reason', '', 'info',    'N', NULL, NOW());

-- ============================================================
-- C. 种植域（5 类 — 已有清晰值）
-- ============================================================

-- C1 djs_plot_type 地块类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100201, '1001', '地块类型', 'djs_plot_type', NULL, NOW(), '种植：t_plant_plot.plot_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002010, '1001', 0, '大棚', 'greenhouse', 'djs_plot_type', '', 'primary', 'N', NULL, NOW()),
  (1002011, '1001', 1, '露天', 'open',       'djs_plot_type', '', 'success', 'N', NULL, NOW()),
  (1002012, '1001', 2, '水田', 'paddy',      'djs_plot_type', '', 'info',    'N', NULL, NOW());

-- C2 djs_crop_type 作物类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100202, '1001', '作物类型', 'djs_crop_type', NULL, NOW(), '种植：t_plant_crop.crop_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002020, '1001', 0, '叶菜',   'leaf',       'djs_crop_type', '', 'success', 'N', NULL, NOW()),
  (1002021, '1001', 1, '根茎',   'root',       'djs_crop_type', '', 'warning', 'N', NULL, NOW()),
  (1002022, '1001', 2, '茄果',   'fruit_veg',  'djs_crop_type', '', 'primary', 'N', NULL, NOW()),
  (1002023, '1001', 3, '水果',   'fruit',      'djs_crop_type', '', 'danger',  'N', NULL, NOW()),
  (1002024, '1001', 4, '其他',   'other',      'djs_crop_type', '', '',        'N', NULL, NOW());

-- C3 djs_organic_cert_status 有机认证状态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100203, '1001', '有机认证状态', 'djs_organic_cert_status', NULL, NOW(), '种植：t_plant_plot.organic_cert_status');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002030, '1001', 0, '未认证',   'none',       'djs_organic_cert_status', '', 'info',    'Y', NULL, NOW()),
  (1002031, '1001', 1, '转换期',   'transition', 'djs_organic_cert_status', '', 'warning', 'N', NULL, NOW()),
  (1002032, '1001', 2, '已认证',   'certified',  'djs_organic_cert_status', '', 'success', 'N', NULL, NOW()),
  (1002033, '1001', 3, '已过期',   'expired',    'djs_organic_cert_status', '', 'danger',  'N', NULL, NOW());

-- C4 djs_farm_work_type 农事类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100204, '1001', '农事类型', 'djs_farm_work_type', NULL, NOW(), '种植：t_plant_work.work_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002040, '1001',  0, '播种',   'sow',        'djs_farm_work_type', '', 'primary', 'N', NULL, NOW()),
  (1002041, '1001',  1, '移栽',   'transplant', 'djs_farm_work_type', '', 'primary', 'N', NULL, NOW()),
  (1002042, '1001',  2, '施肥',   'fertilize',  'djs_farm_work_type', '', 'success', 'N', NULL, NOW()),
  (1002043, '1001',  3, '灌溉',   'irrigate',   'djs_farm_work_type', '', 'info',    'N', NULL, NOW()),
  (1002044, '1001',  4, '喷药',   'spray',      'djs_farm_work_type', '', 'warning', 'N', NULL, NOW()),
  (1002045, '1001',  5, '除草',   'weed',       'djs_farm_work_type', '', 'warning', 'N', NULL, NOW()),
  (1002046, '1001',  6, '整地',   'till',       'djs_farm_work_type', '', 'info',    'N', NULL, NOW()),
  (1002047, '1001',  7, '修剪',   'prune',      'djs_farm_work_type', '', 'info',    'N', NULL, NOW()),
  (1002048, '1001',  8, '嫁接',   'graft',      'djs_farm_work_type', '', 'info',    'N', NULL, NOW()),
  (1002049, '1001',  9, '套袋',   'bag',        'djs_farm_work_type', '', 'info',    'N', NULL, NOW()),
  (1002050, '1001', 10, '疏果',   'thin_fruit', 'djs_farm_work_type', '', 'info',    'N', NULL, NOW()),
  (1002051, '1001', 11, '其他',   'other',      'djs_farm_work_type', '', '',        'N', NULL, NOW());

-- C5 djs_disaster_type 灾害类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100205, '1001', '灾害类型', 'djs_disaster_type', NULL, NOW(), '种植：t_plant_disaster.disaster_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002060, '1001', 0, '旱',       'drought', 'djs_disaster_type', '', 'warning', 'N', NULL, NOW()),
  (1002061, '1001', 1, '涝',       'flood',   'djs_disaster_type', '', 'primary', 'N', NULL, NOW()),
  (1002062, '1001', 2, '风',       'wind',    'djs_disaster_type', '', 'info',    'N', NULL, NOW()),
  (1002063, '1001', 3, '冻',       'frost',   'djs_disaster_type', '', 'info',    'N', NULL, NOW()),
  (1002064, '1001', 4, '病虫害',   'pest',    'djs_disaster_type', '', 'danger',  'N', NULL, NOW());

-- ============================================================
-- D. 种植空白补全（8 类 — 业内通用默认值，doc/02 v1.1 + doc/06 要求）
-- 注: 客户上线前必须过一遍（命名/术语可能有客户偏好）
-- 命名沿用 doc/02 / doc/06 既有约定，不加 djs_ 前缀冲突的话补 djs_ 统一
-- ============================================================

-- D1 djs_soil_type 土壤类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100251, '1001', '土壤类型', 'djs_soil_type', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002510, '1001', 0, '壤土',     'loam',        'djs_soil_type', '', 'primary', 'N', NULL, NOW()),
  (1002511, '1001', 1, '砂土',     'sand',        'djs_soil_type', '', 'warning', 'N', NULL, NOW()),
  (1002512, '1001', 2, '黏土',     'clay',        'djs_soil_type', '', 'info',    'N', NULL, NOW()),
  (1002513, '1001', 3, '壤砂土',   'loam_sand',   'djs_soil_type', '', 'primary', 'N', NULL, NOW()),
  (1002514, '1001', 4, '砂壤土',   'sand_loam',   'djs_soil_type', '', 'primary', 'N', NULL, NOW()),
  (1002515, '1001', 5, '黑土',     'black_soil',  'djs_soil_type', '', 'success', 'N', NULL, NOW());

-- D2 djs_soil_fertility 土壤肥力
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100252, '1001', '土壤肥力', 'djs_soil_fertility', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002520, '1001', 0, '高',     'high',   'djs_soil_fertility', '', 'success', 'N', NULL, NOW()),
  (1002521, '1001', 1, '中',     'medium', 'djs_soil_fertility', '', 'primary', 'N', NULL, NOW()),
  (1002522, '1001', 2, '低',     'low',    'djs_soil_fertility', '', 'warning', 'N', NULL, NOW()),
  (1002523, '1001', 3, '贫瘠',   'barren', 'djs_soil_fertility', '', 'danger',  'N', NULL, NOW());

-- D3 djs_terrain_condition 地势情况
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100253, '1001', '地势情况', 'djs_terrain_condition', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002530, '1001', 0, '平地',   'flat',         'djs_terrain_condition', '', 'success', 'N', NULL, NOW()),
  (1002531, '1001', 1, '缓坡',   'gentle_slope', 'djs_terrain_condition', '', 'primary', 'N', NULL, NOW()),
  (1002532, '1001', 2, '陡坡',   'steep_slope',  'djs_terrain_condition', '', 'warning', 'N', NULL, NOW()),
  (1002533, '1001', 3, '梯田',   'terrace',      'djs_terrain_condition', '', 'info',    'N', NULL, NOW());

-- D4 djs_light_condition 光照条件
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100254, '1001', '光照条件', 'djs_light_condition', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002540, '1001', 0, '充足',     'sufficient', 'djs_light_condition', '', 'success', 'N', NULL, NOW()),
  (1002541, '1001', 1, '一般',     'normal',     'djs_light_condition', '', 'primary', 'N', NULL, NOW()),
  (1002542, '1001', 2, '半阴',     'half_shade', 'djs_light_condition', '', 'warning', 'N', NULL, NOW()),
  (1002543, '1001', 3, '阴',       'shade',      'djs_light_condition', '', 'info',    'N', NULL, NOW());

-- D5 djs_drain_condition 排水条件
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100255, '1001', '排水条件', 'djs_drain_condition', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002550, '1001', 0, '良好', 'good',   'djs_drain_condition', '', 'success', 'N', NULL, NOW()),
  (1002551, '1001', 1, '一般', 'normal', 'djs_drain_condition', '', 'primary', 'N', NULL, NOW()),
  (1002552, '1001', 2, '较差', 'poor',   'djs_drain_condition', '', 'warning', 'N', NULL, NOW());

-- D6 djs_crop_family 作物科属
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100256, '1001', '作物科属', 'djs_crop_family', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002560, '1001', 0, '茄科',     'solanaceae',    'djs_crop_family', '', 'primary', 'N', NULL, NOW()),
  (1002561, '1001', 1, '葫芦科',   'cucurbitaceae', 'djs_crop_family', '', 'primary', 'N', NULL, NOW()),
  (1002562, '1001', 2, '十字花科', 'cruciferae',    'djs_crop_family', '', 'success', 'N', NULL, NOW()),
  (1002563, '1001', 3, '豆科',     'leguminosae',   'djs_crop_family', '', 'success', 'N', NULL, NOW()),
  (1002564, '1001', 4, '禾本科',   'gramineae',     'djs_crop_family', '', 'warning', 'N', NULL, NOW()),
  (1002565, '1001', 5, '菊科',     'compositae',    'djs_crop_family', '', 'info',    'N', NULL, NOW()),
  (1002566, '1001', 6, '百合科',   'liliaceae',     'djs_crop_family', '', 'info',    'N', NULL, NOW()),
  (1002567, '1001', 7, '旋花科',   'convolvulaceae','djs_crop_family', '', 'info',    'N', NULL, NOW());

-- D7 djs_tillage_type 整地类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100257, '1001', '整地类型', 'djs_tillage_type', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002570, '1001', 0, '深耕', 'deep',       'djs_tillage_type', '', 'primary', 'N', NULL, NOW()),
  (1002571, '1001', 1, '浅耕', 'shallow',    'djs_tillage_type', '', 'primary', 'N', NULL, NOW()),
  (1002572, '1001', 2, '旋耕', 'rotary',     'djs_tillage_type', '', 'success', 'N', NULL, NOW()),
  (1002573, '1001', 3, '免耕', 'no_till',    'djs_tillage_type', '', 'info',    'N', NULL, NOW());

-- D8 djs_tillage_way 整地方式
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100258, '1001', '整地方式', 'djs_tillage_way', NULL, NOW(), '种植：v1.1 业内默认值占位，客户上线前确认');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002580, '1001', 0, '机械翻耕', 'mechanical',  'djs_tillage_way', '', 'primary', 'N', NULL, NOW()),
  (1002581, '1001', 1, '人工翻耕', 'manual',      'djs_tillage_way', '', 'warning', 'N', NULL, NOW()),
  (1002582, '1001', 2, '起垄',     'ridging',     'djs_tillage_way', '', 'success', 'N', NULL, NOW()),
  (1002583, '1001', 3, '平整',     'leveling',    'djs_tillage_way', '', 'info',    'N', NULL, NOW());

-- ============================================================
-- E. 仓库域（6 类）
-- ============================================================

-- E1 djs_warehouse_type 仓库类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100301, '1001', '仓库类型', 'djs_warehouse_type', NULL, NOW(), '仓库：t_warehouse_house.warehouse_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1003010, '1001', 0, '鲜肉仓',   'fresh_meat', 'djs_warehouse_type', '', 'danger',  'N', NULL, NOW()),
  (1003011, '1001', 1, '蔬菜仓',   'veg',        'djs_warehouse_type', '', 'success', 'N', NULL, NOW()),
  (1003012, '1001', 2, '物资仓',   'material',   'djs_warehouse_type', '', 'info',    'N', NULL, NOW()),
  (1003013, '1001', 3, '包材仓',   'pack',       'djs_warehouse_type', '', 'warning', 'N', NULL, NOW());

-- E2 djs_product_status 产品状态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100302, '1001', '产品状态', 'djs_product_status', NULL, NOW(), '仓库：产品在库/已发/已售/已退/报损');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1003020, '1001', 0, '在库', 'in_stock',  'djs_product_status', '', 'primary', 'Y', NULL, NOW()),
  (1003021, '1001', 1, '已发', 'shipped',   'djs_product_status', '', 'warning', 'N', NULL, NOW()),
  (1003022, '1001', 2, '已售', 'sold',      'djs_product_status', '', 'success', 'N', NULL, NOW()),
  (1003023, '1001', 3, '已退', 'returned',  'djs_product_status', '', 'info',    'N', NULL, NOW()),
  (1003024, '1001', 4, '报损', 'damaged',   'djs_product_status', '', 'danger',  'N', NULL, NOW());

-- E3 djs_demand_business 需求业态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100303, '1001', '需求业态', 'djs_demand_business', NULL, NOW(), '仓库：t_warehouse_demand.business_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1003030, '1001', 0, '门店', 'store',      'djs_demand_business', '', 'primary', 'N', NULL, NOW()),
  (1003031, '1001', 1, '经销', 'distrib',    'djs_demand_business', '', 'success', 'N', NULL, NOW()),
  (1003032, '1001', 2, '团购', 'group',      'djs_demand_business', '', 'warning', 'N', NULL, NOW()),
  (1003033, '1001', 3, '加工', 'processing', 'djs_demand_business', '', 'info',    'N', NULL, NOW());

-- E4 djs_demand_status 需求状态
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100304, '1001', '需求状态', 'djs_demand_status', NULL, NOW(), '仓库：t_warehouse_demand.status 7 状态');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1003040, '1001', 0, '草稿',     'DRAFT',      'djs_demand_status', '', 'info',    'N', NULL, NOW()),
  (1003041, '1001', 1, '已提交',   'SUBMITTED',  'djs_demand_status', '', 'primary', 'N', NULL, NOW()),
  (1003042, '1001', 2, '已确认',   'CONFIRMED',  'djs_demand_status', '', 'primary', 'N', NULL, NOW()),
  (1003043, '1001', 3, '排产中',   'IN_PRODUCTION',   'djs_demand_status', '', 'warning', 'N', NULL, NOW()),
  (1003044, '1001', 4, '部分发货', 'PARTIAL_SHIPPED', 'djs_demand_status', '', 'warning', 'N', NULL, NOW()),
  (1003045, '1001', 5, '已完成',   'COMPLETED',  'djs_demand_status', '', 'success', 'N', NULL, NOW()),
  (1003046, '1001', 6, '已取消',   'CANCELLED',  'djs_demand_status', '', 'danger',  'N', NULL, NOW());

-- E5 djs_stock_flow_type 出入库类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100305, '1001', '出入库类型', 'djs_stock_flow_type', NULL, NOW(), '仓库：t_warehouse_stock_flow.flow_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1003050, '1001', 0, '入库',   'IN',       'djs_stock_flow_type', '', 'success', 'N', NULL, NOW()),
  (1003051, '1001', 1, '出库',   'OUT',      'djs_stock_flow_type', '', 'warning', 'N', NULL, NOW()),
  (1003052, '1001', 2, '调拨',   'TRANSFER', 'djs_stock_flow_type', '', 'primary', 'N', NULL, NOW()),
  (1003053, '1001', 3, '盘盈',   'GAIN',     'djs_stock_flow_type', '', 'info',    'N', NULL, NOW()),
  (1003054, '1001', 4, '盘亏',   'LOSS',     'djs_stock_flow_type', '', 'danger',  'N', NULL, NOW());

-- E6 djs_pack_type 包装类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100306, '1001', '包装类型', 'djs_pack_type', NULL, NOW(), '仓库：t_warehouse_product.pack_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1003060, '1001', 0, '散装',     'bulk',      'djs_pack_type', '', 'info',    'N', NULL, NOW()),
  (1003061, '1001', 1, '标准盒',   'std_box',   'djs_pack_type', '', 'primary', 'N', NULL, NOW()),
  (1003062, '1001', 2, '礼盒',     'gift_box',  'djs_pack_type', '', 'success', 'N', NULL, NOW()),
  (1003063, '1001', 3, '真空袋',   'vacuum',    'djs_pack_type', '', 'warning', 'N', NULL, NOW());

-- ============================================================
-- F. 门店域（3 类）
-- ============================================================

-- F1 djs_member_level 会员等级
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100401, '1001', '会员等级', 'djs_member_level', NULL, NOW(), '门店：t_store_member.member_level');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1004010, '1001', 0, '普通',           'normal', 'djs_member_level', '', 'info',    'Y', NULL, NOW()),
  (1004011, '1001', 1, '重要价值客户',   'vip',    'djs_member_level', '', 'danger',  'N', NULL, NOW()),
  (1004012, '1001', 2, '重要保持客户',   'keep',   'djs_member_level', '', 'warning', 'N', NULL, NOW());

-- F2 djs_return_reason 退货原因
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100402, '1001', '退货原因', 'djs_return_reason', NULL, NOW(), '门店：t_store_return.reason');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1004020, '1001', 0, '质量问题',         'quality',      'djs_return_reason', '', 'danger',  'N', NULL, NOW()),
  (1004021, '1001', 1, '客户改主意',       'mind_change',  'djs_return_reason', '', 'warning', 'N', NULL, NOW()),
  (1004022, '1001', 2, '配送问题',         'delivery',     'djs_return_reason', '', 'info',    'N', NULL, NOW()),
  (1004023, '1001', 3, '其他',             'other',        'djs_return_reason', '', '',        'N', NULL, NOW());

-- F3 djs_dispatch_priority 调度优先级
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100403, '1001', '调度优先级', 'djs_dispatch_priority', NULL, NOW(), '门店：t_store_dispatch.priority');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1004030, '1001', 0, '紧急', 'urgent', 'djs_dispatch_priority', '', 'danger',  'N', NULL, NOW()),
  (1004031, '1001', 1, '普通', 'normal', 'djs_dispatch_priority', '', 'primary', 'Y', NULL, NOW()),
  (1004032, '1001', 2, '低',   'low',    'djs_dispatch_priority', '', 'info',    'N', NULL, NOW());

-- ============================================================
-- G. 跨域 / 追溯（2 类）
-- ============================================================

-- G1 djs_trace_event_type 追溯事件类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100501, '1001', '追溯事件类型', 'djs_trace_event_type', NULL, NOW(), '追溯：t_store_trace.event_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1005010, '1001', 0, '引种',     'intro',     'djs_trace_event_type', '', 'primary', 'N', NULL, NOW()),
  (1005011, '1001', 1, '出生',     'birth',     'djs_trace_event_type', '', 'primary', 'N', NULL, NOW()),
  (1005012, '1001', 2, '配种',     'breed',     'djs_trace_event_type', '', 'primary', 'N', NULL, NOW()),
  (1005013, '1001', 3, '分娩',     'farrow',    'djs_trace_event_type', '', 'success', 'N', NULL, NOW()),
  (1005014, '1001', 4, '用药',     'medicate',  'djs_trace_event_type', '', 'warning', 'N', NULL, NOW()),
  (1005015, '1001', 5, '出栏',     'slaughter', 'djs_trace_event_type', '', 'danger',  'N', NULL, NOW()),
  (1005016, '1001', 6, '燎毛',     'singe',     'djs_trace_event_type', '', 'info',    'N', NULL, NOW()),
  (1005017, '1001', 7, '分割',     'split',     'djs_trace_event_type', '', 'info',    'N', NULL, NOW()),
  (1005018, '1001', 8, '发货',     'ship',      'djs_trace_event_type', '', 'warning', 'N', NULL, NOW()),
  (1005019, '1001', 9, '售卖',     'sell',      'djs_trace_event_type', '', 'success', 'N', NULL, NOW());

-- G2 djs_subscribe_message_type 订阅消息类型
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100502, '1001', '订阅消息类型', 'djs_subscribe_message_type', NULL, NOW(), '跨域：mp_subscribe_record.message_type');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1005020, '1001', 0, '出栏通知',     'slaughter_notice',  'djs_subscribe_message_type', '', 'danger',  'N', NULL, NOW()),
  (1005021, '1001', 1, '库存告警',     'stock_alert',       'djs_subscribe_message_type', '', 'warning', 'N', NULL, NOW()),
  (1005022, '1001', 2, '销售汇总',     'sales_summary',     'djs_subscribe_message_type', '', 'success', 'N', NULL, NOW()),
  (1005023, '1001', 3, '内测反馈',     'internal_feedback', 'djs_subscribe_message_type', '', 'info',    'N', NULL, NOW());

-- H1 djs_check_status 盘点状态（跨域复用：仓库 + 门店 check_record）
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100601, '1001', '盘点状态', 'djs_check_status', NULL, NOW(), '跨域：t_warehouse_check_record / t_store_check_record.check_status');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006010, '1001', 0, '草稿',     'draft',       'djs_check_status', '', 'info',    'Y', NULL, NOW()),
  (1006011, '1001', 1, '进行中',   'in_progress', 'djs_check_status', '', 'warning', 'N', NULL, NOW()),
  (1006012, '1001', 2, '已完成',   'completed',   'djs_check_status', '', 'success', 'N', NULL, NOW());

-- ============================================================
-- 验收: 期望 dict_type=39, dict_data≈228
-- SELECT COUNT(*) FROM sys_dict_type  WHERE dict_type LIKE 'djs_%';   -- 39
-- SELECT COUNT(*) FROM sys_dict_data  WHERE dict_type LIKE 'djs_%';   -- 228
-- ============================================================


-- ----------------------------------------------------------------------------
-- 来源文件：V202605201100__SYS-AUTH-001-roles-and-menus.sql
-- ----------------------------------------------------------------------------
-- =============================================================================
-- SYS-AUTH-001  用户 / 角色 / 权限三件套
-- =============================================================================
--   1. 13 个角色种子数据（ruoyi 自带 superadmin role_id=1 + 12 个新增 djs 角色 role_id=101-112）
--   2. 业务一级目录菜单 + 二级通用主数据菜单 seed（menu_id 段 5000-5099 / 7000 / 8000 / 9000 / 10000）
--   3. 角色 → 菜单 / 角色 → 用户 关联种子（仅 boss / manager 全菜单；其他业务角色由各业务 ticket 自己 INSERT sys_role_menu）
--   4. admin (user_id=1) 关联 superadmin 角色（IGNORE 防重）；租户清理由 V202605201500 处理
--
-- 角色清单（按 doc/05 §4.4.4 表 + doc/06 SYS-AUTH-001 §5 + doc/02 v1.2 §SYS-AUTH-001）：
--   role_id=1   superadmin       超级管理员   （ruoyi 自带，不变）
--   role_id=101 system_admin     系统管理员
--   role_id=102 boss             老板         （v1.2 新增）
--   role_id=103 manager          管理人员     （v1.2 新增）
--   role_id=104 breed_admin      养殖管理员
--   role_id=105 plant_admin      种植管理员
--   role_id=106 warehouse_admin  仓库管理员
--   role_id=107 store_admin      门店管理员
--   role_id=108 breed_worker     养殖工人
--   role_id=109 vet              兽医
--   role_id=110 warehouse_worker 仓库工人（含分割师 / 库管员）
--   role_id=111 plant_worker     种植工人
--   role_id=112 store_clerk      门店店员
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 12 个新角色 INSERT（ruoyi superadmin role_id=1 是自带，不重复插）
-- -----------------------------------------------------------------------------
INSERT INTO sys_role
  (role_id, tenant_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
   status, del_flag, create_by, create_time, remark)
VALUES
  (101, '1001', '系统管理员',     'system_admin',     2,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 用户/角色/字典维护'),
  (102, '1001', '老板',           'boss',             3,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 v1.2 DSH驾驶舱可见 + 跨域数据浏览'),
  (103, '1001', '管理人员',       'manager',          4,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 v1.2 DSH驾驶舱可见 + 业务管理'),
  (104, '1001', '养殖管理员',     'breed_admin',      5,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 养殖 admin 模块'),
  (105, '1001', '种植管理员',     'plant_admin',      6,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 种植 admin 模块'),
  (106, '1001', '仓库管理员',     'warehouse_admin',  7,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 仓库 admin 模块'),
  (107, '1001', '门店管理员',     'store_admin',      8,  '1', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 门店 admin 模块'),
  (108, '1001', '养殖工人',       'breed_worker',     9,  '4', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 养殖小程序'),
  (109, '1001', '兽医',           'vet',              10, '4', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 养殖小程序（含药品）'),
  (110, '1001', '仓库工人',       'warehouse_worker', 11, '4', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 仓库小程序（含分割师/库管员）'),
  (111, '1001', '种植工人',       'plant_worker',     12, '4', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 种植小程序'),
  (112, '1001', '门店店员',       'store_clerk',      13, '4', 1, 1, '0', '0', 1, NOW(), 'SYS-AUTH-001 门店小程序');

-- -----------------------------------------------------------------------------
-- 2. 业务一级 / 二级目录菜单 seed
--    menu_id 段：通用主数据 5000-5099 / 养殖 7000 / 种植 8000 / 仓库 9000 / 门店 10000
--    一级目录是各业务 ticket 实现 list/form 时的父节点占位；具体 list 菜单由对应 ticket INSERT
-- -----------------------------------------------------------------------------
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
   menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  -- 一级目录：通用主数据（人员 / 门店 / 供应商 / 农场切换）
  (5000, '通用主数据',   0,    50,  'djs-common',    NULL,                            '', 1, 0, 'M', '0', '0', '',                          'tab',       1, NOW(), 'SYS-AUTH-001 通用主数据目录'),
  -- 二级（人员 / 门店 / 供应商三个 list 由 SYS-MD-001/002/003 自己 INSERT；本 ticket 不预占）

  -- 一级目录：养殖（BRD-*）
  (7000, '养殖',         0,    70,  'djs-breed',     NULL,                            '', 1, 0, 'M', '0', '0', '',                          'tree',      1, NOW(), 'SYS-AUTH-001 BRD-* 全域'),
  -- 一级目录：种植（PLT-*）
  (8000, '种植',         0,    80,  'djs-plant',     NULL,                            '', 1, 0, 'M', '0', '0', '',                          'tree-table',1, NOW(), 'SYS-AUTH-001 PLT-* 全域'),
  -- 一级目录：仓库（WMS-*）
  (9000, '仓库',         0,    90,  'djs-warehouse', NULL,                            '', 1, 0, 'M', '0', '0', '',                          'list',      1, NOW(), 'SYS-AUTH-001 WMS-* 全域'),
  -- 一级目录：门店销售（STR-* + TRC + DSH）
  (10000,'门店销售',     0,    100, 'djs-store',     NULL,                            '', 1, 0, 'M', '0', '0', '',                          'star',      1, NOW(), 'SYS-AUTH-001 STR-* + TRC-* + DSH-*');

-- -----------------------------------------------------------------------------
-- 3. 农场切换 perm seed（菜单不挂，仅做权限串占位，UserFarmController 端点用）
--    放在通用主数据下作"按钮型"权限（visible='1' 隐藏）
-- -----------------------------------------------------------------------------
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
   menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (5050, '查询可访问农场', 5000, 1, '', NULL, '', 1, 0, 'F', '1', '0', 'djs:user:farm:query',  '#', 1, NOW(), 'SYS-AUTH-001 农场切换器 GET /djs/user/farm/accessible'),
  (5051, '切换当前农场',   5000, 2, '', NULL, '', 1, 0, 'F', '1', '0', 'djs:user:farm:switch', '#', 1, NOW(), 'SYS-AUTH-001 农场切换器 POST /djs/user/farm/switch');

-- -----------------------------------------------------------------------------
-- 4. 角色 → 菜单 映射：boss / manager / system_admin / 4 业务 admin 给一级目录可见
--    （具体 list 菜单与按钮权限由对应业务 ticket INSERT sys_role_menu）
-- -----------------------------------------------------------------------------

-- boss / manager 全菜单可见（5000-10999 + 5050/5051 农场切换）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 102, menu_id FROM sys_menu WHERE menu_id BETWEEN 5000 AND 10999;
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 103, menu_id FROM sys_menu WHERE menu_id BETWEEN 5000 AND 10999;

-- system_admin 仅通用主数据目录可见
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (101, 5000), (101, 5050), (101, 5051);

-- 4 个业务 admin 各自仅可见对应一级目录 + 通用主数据（数据来源）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (104, 7000), (104, 5000), (104, 5050), -- breed_admin: 养殖 + 通用主数据 + 农场查询
  (105, 8000), (105, 5000), (105, 5050), -- plant_admin
  (106, 9000), (106, 5000), (106, 5050), -- warehouse_admin
  (107, 10000),(107, 5000), (107, 5050); -- store_admin

-- 5 个 worker 角色（breed_worker / vet / warehouse_worker / plant_worker / store_clerk）
-- V1 主要走小程序，admin 后台仅给农场查询权限（小程序登录后查可见农场）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (108, 5050), -- breed_worker
  (109, 5050), -- vet
  (110, 5050), -- warehouse_worker
  (111, 5050), -- plant_worker
  (112, 5050); -- store_clerk

-- -----------------------------------------------------------------------------
-- 5. 给 admin 用户分配 superadmin 角色（ruoyi 自带，sys_user_role 应已存在，IGNORE 防重）
--    （admin tenant_id 与 sys_tenant 重命名由 V202605201500__SYS-CLEANUP-single-tenant 统一处理）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- =============================================================================
-- 验收 query（subagent 自测用）
--   SELECT role_id, role_key, role_name FROM sys_role WHERE role_id IN (1,101,102,103,104,105,106,107,108,109,110,111,112);
--   SELECT menu_id, menu_name, parent_id FROM sys_menu WHERE menu_id BETWEEN 5000 AND 10999;
--   SELECT role_id, COUNT(menu_id) FROM sys_role_menu WHERE role_id BETWEEN 101 AND 112 GROUP BY role_id;
--   SELECT user_id, user_name, tenant_id FROM sys_user WHERE user_id = 1;
-- =============================================================================


-- ----------------------------------------------------------------------------
-- 来源文件：V202605201200__SYS-INFRA-002-oss-config-and-menus.sql
-- ----------------------------------------------------------------------------
-- =============================================================================
-- SYS-INFRA-002: OSS STS 直传 — 配置 + 菜单权限 seed
--
-- 1. 把 ruoyi 自带 minio config (id=1) 调整为 djs dev 用桶 djs-dev（V1 dev 环境）
-- 2. 占位 aliyun-djs 配置（V1 不启用，prod 上线时 Kevin 改 access_key / sts_role_arn）
-- 3. 菜单权限 seed：
--    - admin 端 djs:common:oss:sts → 挂在通用主数据 5000 下，按钮型 (visible='1' 隐藏)
--    - 小程序端 djs:applet:oss:sts → 同样按钮型，仅做权限串占位（不展示菜单）
--
-- 上游依赖：SYS-INIT-001（sys_oss_config 已扩 sts_role_arn / sts_session_duration），
--           SYS-AUTH-001（5000 通用主数据父菜单已建）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. minio 配置：dev 用桶名 djs-dev（与本机 docker dev-minio 容器联调）
--    sys_oss_config 由 V202605201500 cleanup 统一搬到 tenant_id='1001'
-- -----------------------------------------------------------------------------
-- dev-minio 容器实际 root user/password 是 ruoyi / ruoyi123（见 docker inspect dev-minio）
UPDATE sys_oss_config
SET bucket_name          = 'djs-dev',
    access_key           = 'ruoyi',
    secret_key           = 'ruoyi123',
    endpoint             = '127.0.0.1:9000',
    domain               = '',
    is_https             = 'N',
    region               = '',
    access_policy        = '1',       -- public（dev 用，浏览器直拉 URL）
    status               = '0',       -- 启用为默认
    sts_role_arn         = '',
    sts_session_duration = 600
WHERE config_key = 'minio';

-- aliyun 配置：占位为 djs 业务桶（V1 不启用，status=1；上线时 Kevin 改 ak/sk/role_arn 后 status=0）
UPDATE sys_oss_config
SET bucket_name          = 'dongjiaoshan-prod',
    domain               = '',
    is_https             = 'Y',
    region               = 'cn-hangzhou',
    sts_role_arn         = '',                                  -- prod 启用时填 acs:ram::<account-id>:role/<sts-role>
    sts_session_duration = 600,
    status               = '1',                                 -- 停用，等真实 key
    remark               = '东角山 prod 阿里云 OSS（V1 占位，待 Kevin 填客户 RAM 子账号）'
WHERE config_key = 'aliyun';

-- -----------------------------------------------------------------------------
-- 2. 菜单权限：admin 端 + 小程序端 OSS STS 权限串
--    挂在通用主数据 5000 下作"按钮型"权限（visible='1' 隐藏，仅做 perms 串占位）
--    分配菜单 id: 5052（admin） / 5053（applet）
-- -----------------------------------------------------------------------------
INSERT INTO sys_menu
  (menu_id, menu_name,           parent_id, order_num, path, component, query_param,
   is_frame, is_cache, menu_type, visible, status, perms,
   icon, create_by, create_time, remark)
VALUES
  (5052, '申请 OSS 上传凭证(admin)', 5000, 11, '', NULL, '',
   1, 0, 'F', '1', '0', 'djs:common:oss:sts',
   '#', 1, NOW(), 'SYS-INFRA-002 admin 直传 OSS GET/POST /djs/oss/sts/*'),
  (5053, '申请 OSS 上传凭证(小程序)', 5000, 12, '', NULL, '',
   1, 0, 'F', '1', '0', 'djs:applet:oss:sts',
   '#', 1, NOW(), 'SYS-INFRA-002 小程序直传 OSS GET/POST /djs/applet/oss/sts/*');

-- -----------------------------------------------------------------------------
-- 3. 角色绑权
--    - boss(102) / manager(103) 已通过 SYS-AUTH-001 的范围 SELECT 拿到 5000-10999，
--      新增的 5052/5053 自动包含？—— 不！那个 INSERT 是一次性快照（SELECT 时刻），新增菜单要补回写
--    - system_admin(101) admin 上传必备
--    - 4 个业务 admin（104/105/106/107）admin 上传必备
--    - 小程序登录用户角色（如 mp_user）后续 ticket 接入时绑 5053
-- -----------------------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (101, 5052), -- system_admin
  (102, 5052), (102, 5053), -- boss
  (103, 5052), (103, 5053), -- manager
  (104, 5052), -- breed_admin
  (105, 5052), -- plant_admin
  (106, 5052), -- warehouse_admin
  (107, 5052); -- store_admin


-- ----------------------------------------------------------------------------
-- 来源文件：V202605201500__SYS-CLEANUP-single-tenant.sql
-- ----------------------------------------------------------------------------
-- =============================================================================
-- SYS-CLEANUP-single-tenant  V1 单租户规整
-- =============================================================================
-- V1 只服务一个农场（东角山有机生态农场）。
-- 本脚本在 ruoyi 基础 seed + 所有 djs DDL 之后执行：
--   1) 把 ruoyi 默认 '000000' 租户 rename 为业务租户 '1001'
--   2) 把所有 ruoyi 自带 sys_* 表的 tenant_id='000000' 行 rebind 到 '1001'
-- 跑完后：整库不存在 tenant_id='000000'，sys_tenant 唯一一行 = 1001 东角山农场。
--
-- 多租户拦截器 `tenant.enable=true` 保留：单租户场景无副作用，V2 多农场启用即生效。
-- =============================================================================

-- 1. 重命名 ruoyi 默认租户 → 东角山农场
UPDATE sys_tenant SET
  tenant_id         = '1001',
  company_name      = '东角山有机生态农场',
  contact_user_name = '王奎',
  contact_phone     = '13800000000',
  address           = '广州市从化区',
  intro             = '东角山有机生态农场 V1 默认租户'
WHERE tenant_id = '000000';

-- 2. ruoyi 自带 sys_* 表数据 rebind 到 '1001'
UPDATE sys_user        SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_role        SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_dept        SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_post        SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_dict_type   SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_dict_data   SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_config      SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_notice      SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_oss_config  SET tenant_id = '1001' WHERE tenant_id = '000000';

-- =============================================================================
-- 验收 query
--   SELECT (
--     (SELECT COUNT(*) FROM sys_tenant    WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_user       WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_role       WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_dept       WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_post       WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_dict_type  WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_dict_data  WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_config     WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_notice     WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_oss_config WHERE tenant_id <> '1001')
--   ) AS not_1001_rows;
--   -- 期望 = 0
-- =============================================================================


-- ----------------------------------------------------------------------------
-- 来源文件：V202605201600__SYS-CLEANUP-ruoyi-demo-menus.sql
-- ----------------------------------------------------------------------------
-- =============================================================================
-- SYS-CLEANUP-ruoyi-demo-menus  V1 不用的 ruoyi 自带菜单清理
-- =============================================================================
-- ruoyi 默认 seed 包含 V1 用不到的功能菜单（工作流 / 租户管理 / 客户端管理 / demo）。
-- 直接 DELETE 删菜单 + role-menu 关联；对于开发期还要用的（代码生成 / 系统监控）改为 visible='1' 隐藏。
--
-- 删除范围（67 项，含子菜单 + 按钮权限）：
--   - PLUS官网(4)                     纯营销跳转
--   - 测试菜单(5) + 请假申请 demo     ruoyi 工作流示例
--   - 租户管理(6) / 租户套餐(122)     V1 单租户 1001，不需要管理界面
--   - 客户端管理(123)                 sys_client 已 SQL 配死，不需要 UI
--   - 工作流(11616) 全树              V1 不上 Flowable
--   - 我的任务(11618) 全树            工作流配套
--
-- 隐藏范围（visible='0'→'1'，可在菜单管理里改回）：
--   - 系统监控(2) + Admin监控(117) + 任务调度中心(120)   运维上线后再露出
--   - 系统工具(3) + 代码生成(115)                       开发期内部用
-- =============================================================================

-- 1. 解除 role-menu 关联（ruoyi 无 CASCADE）
DELETE FROM sys_role_menu WHERE menu_id IN (
  4, 5, 6, 121, 122, 123,
  1061, 1062, 1063, 1064, 1065,
  1606, 1607, 1608, 1609, 1610,
  1611, 1612, 1613, 1614, 1615,
  11616, 11618, 11619, 11620, 11621, 11622,
  11623, 11624, 11625, 11626, 11627,
  11629, 11630, 11631, 11632, 11633,
  11638, 11639, 11640, 11641, 11642, 11643,
  11644, 11645, 11646, 11647, 11648, 11649, 11650, 11651, 11652,
  11653, 11654, 11655, 11656, 11657, 11658, 11659,
  11700, 11701,
  11801, 11802, 11803, 11804, 11805, 11806
);

-- 2. 删菜单本身
DELETE FROM sys_menu WHERE menu_id IN (
  4, 5, 6, 121, 122, 123,
  1061, 1062, 1063, 1064, 1065,
  1606, 1607, 1608, 1609, 1610,
  1611, 1612, 1613, 1614, 1615,
  11616, 11618, 11619, 11620, 11621, 11622,
  11623, 11624, 11625, 11626, 11627,
  11629, 11630, 11631, 11632, 11633,
  11638, 11639, 11640, 11641, 11642, 11643,
  11644, 11645, 11646, 11647, 11648, 11649, 11650, 11651, 11652,
  11653, 11654, 11655, 11656, 11657, 11658, 11659,
  11700, 11701,
  11801, 11802, 11803, 11804, 11805, 11806
);

-- 3. 隐藏开发期 dev-tool 菜单（visible='1' 在导航不显示，菜单管理里仍可见可改回）
UPDATE sys_menu SET visible = '1'
WHERE menu_id IN (2, 117, 120, 3, 115);

-- =============================================================================
-- 验收 query
--   SELECT menu_id, menu_name FROM sys_menu WHERE menu_id IN (4,5,6,123,11616,11618);
--   -- 期望 0 rows
--   SELECT menu_id, menu_name, visible FROM sys_menu WHERE menu_id IN (2,3,115,117,120);
--   -- 期望 visible='1'
--   SELECT COUNT(*) AS visible_top_dirs FROM sys_menu WHERE parent_id=0 AND visible='0';
--   -- 期望 6（系统管理 + 5 个业务一级目录）
-- =============================================================================


-- ----------------------------------------------------------------------------
-- 来源文件：V202605201700__SYS-CLEANUP-ruoyi-sample-data.sql
-- ----------------------------------------------------------------------------
-- =============================================================================
-- SYS-CLEANUP-ruoyi-sample-data  清理 ruoyi 自带 sample 数据
-- =============================================================================
-- ruoyi base seed (ry_vue_5.X.sql) 自带的演示用 sample 数据，V1 业务不用。
-- 单租户合并（V202605201500）后 admin 视角能看到，造成 UI noise。本脚本清理它们。
--
-- 删除范围：
--   - 2 个 sample 角色: role_id=3 (test1) / role_id=4 (test2)
--   - 2 个 sample 用户: user_id=3 (test) / user_id=4 (test1)
--   - 3 类工作流字典 (wf_business_status / wf_form_type / wf_task_status，菜单已在 V202605201600 删除)
--   - sys_notice 全部 sample 数据 (ruoyi 自带 2 条演示通知)
--
-- 保留（V1 必需）:
--   - 10 个 sys_* 系统字典 (sys_user_sex / sys_show_hide / sys_normal_disable 等，ruoyi UI 组件依赖)
--   - sys_dept 部门树 / sys_post 岗位（admin.dept_id 引用，留待 SYS-MD-001 业务人员管理落地时 rename）
-- =============================================================================

-- 1. 解 FK 关联（ruoyi 无 CASCADE）
DELETE FROM sys_user_role WHERE user_id IN (3, 4) OR role_id IN (3, 4);
DELETE FROM sys_role_menu WHERE role_id IN (3, 4);
DELETE FROM sys_role_dept WHERE role_id IN (3, 4);
DELETE FROM sys_user_post WHERE user_id IN (3, 4);

-- 2. 删 sample 角色 + 用户
DELETE FROM sys_role WHERE role_id IN (3, 4);
DELETE FROM sys_user WHERE user_id IN (3, 4);

-- 3. 删工作流字典（菜单已在 V202605201600 删除）
DELETE FROM sys_dict_data WHERE dict_type IN ('wf_business_status', 'wf_form_type', 'wf_task_status');
DELETE FROM sys_dict_type WHERE dict_type IN ('wf_business_status', 'wf_form_type', 'wf_task_status');

-- 4. 删 sample 通知
DELETE FROM sys_notice;

-- =============================================================================
-- 验收 query
--   SELECT role_id, role_key FROM sys_role ORDER BY role_id;
--   -- 期望: 1 superadmin + 12 djs (101-112) = 13 rows
--   SELECT user_id, user_name FROM sys_user;
--   -- 期望: 1 row (admin)
--   SELECT COUNT(*) FROM sys_dict_type WHERE dict_type LIKE 'wf_%';
--   -- 期望: 0
--   SELECT COUNT(*) FROM sys_notice;
--   -- 期望: 0
-- =============================================================================


-- ----------------------------------------------------------------------------
-- 来源文件：V202605210800__D02-PATCH-D01-missing-tables.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- D02 开工前置补丁 — 补建 D01 SYS-INIT-001 漏建的 3 张表 + 1 个菜单占位
--   修补目标：
--     1. t_md_biz_code_rule       —— SYS-INFRA-004 编码规则配置
--     2. t_md_biz_code_sequence   —— SYS-INFRA-004 编码序号（按日/按月/按年）
--     3. t_md_person              —— SYS-MD-001 人员主数据
--     4. sys_menu menu_id=5001    —— SYS-AUTH-001 人员管理二级菜单占位（5000 通用主数据下）
--   字段风格对齐 D01 SYS-INIT-001 common.sql 既有表（tenant_id VARCHAR(20)/del_unique BIGINT 应用层 fill）
-- ============================================================

-- ------------------------------------------------------------
-- 1. t_md_biz_code_rule（业务编码规则配置）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_md_biz_code_rule;
CREATE TABLE t_md_biz_code_rule (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id    VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  code_type    VARCHAR(32)  NOT NULL COMMENT '编码类型 EAR_NO/TRACE_CODE/DEMAND_NO/SHIP_NO/PACK_NO/STOCK_FLOW_NO ...',
  pattern      VARCHAR(255) NOT NULL COMMENT '编码格式串，支持占位符 {farmCode2}{barnCode2}{yyMM}{yyyyMMdd}{dailySeq4}{seq4}{seq6} 等',
  daily_reset  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否每日重置序号 1=是 0=否',
  prefix       VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '固定前缀（如 T/D/S/P/F）',
  seq_length   INT          NOT NULL DEFAULT 4 COMMENT '序号位数（4/6）',
  status       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态 0=启用 1=停用',
  create_dept  BIGINT       NULL COMMENT '创建部门',
  create_by    BIGINT       NULL COMMENT '创建人',
  create_time  DATETIME     NULL COMMENT '创建时间',
  update_by    BIGINT       NULL COMMENT '更新人',
  update_time  DATETIME     NULL COMMENT '更新时间',
  del_flag     CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark       VARCHAR(500) NULL COMMENT '备注',
  del_unique   BIGINT       NOT NULL DEFAULT 0 COMMENT '软删除生成 token（应用层 fill）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code_type (tenant_id, code_type, del_unique),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务编码规则配置（SYS-INFRA-004）';

-- ------------------------------------------------------------
-- 2. t_md_biz_code_sequence（业务编码序号表）
--   按 (tenant_id, code_type, seq_date) 维度计数；daily_reset=0 时 seq_date 填空串
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_md_biz_code_sequence;
CREATE TABLE t_md_biz_code_sequence (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id    VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  code_type    VARCHAR(32)  NOT NULL COMMENT '编码类型',
  seq_date     VARCHAR(8)   NOT NULL DEFAULT '' COMMENT '序号统计周期：yyyyMMdd / yyyyMM / yyyy / 空串（终生）',
  current_seq  BIGINT       NOT NULL DEFAULT 0 COMMENT '当前已用最大序号',
  create_dept  BIGINT       NULL COMMENT '创建部门',
  create_by    BIGINT       NULL COMMENT '创建人',
  create_time  DATETIME     NULL COMMENT '创建时间',
  update_by    BIGINT       NULL COMMENT '更新人',
  update_time  DATETIME     NULL COMMENT '更新时间',
  del_flag     CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark       VARCHAR(500) NULL COMMENT '备注',
  del_unique   BIGINT       NOT NULL DEFAULT 0 COMMENT '软删除生成 token（应用层 fill）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code_seq (tenant_id, code_type, seq_date, del_unique)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务编码序号表（SYS-INFRA-004 并发安全计数）';

-- ------------------------------------------------------------
-- 3. t_md_person（人员主数据）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_md_person;
CREATE TABLE t_md_person (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id    VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  person_code  VARCHAR(32)  NOT NULL COMMENT '人员编号（SYS-INFRA-004 MEMBER_NO 生成）',
  name         VARCHAR(64)  NOT NULL COMMENT '姓名',
  gender       CHAR(1)      NULL COMMENT '性别 0=男 1=女 (字典 sys_user_sex)',
  phone        VARCHAR(20)  NULL COMMENT '联系电话',
  id_card      VARCHAR(20)  NULL COMMENT '身份证号',
  position     VARCHAR(64)  NULL COMMENT '岗位描述',
  post_id      BIGINT       NULL COMMENT '岗位（sys_post.post_id 外键，区分 admin 角色 vs 微信端角色）',
  hire_date    DATE         NULL COMMENT '入职日期',
  status       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态 0=在职 1=离职 2=试用 (字典 djs_person_status)',
  avatar_url   VARCHAR(500) NULL COMMENT '头像（OSS key，SYS-INFRA-002）',
  create_dept  BIGINT       NULL COMMENT '创建部门',
  create_by    BIGINT       NULL COMMENT '创建人',
  create_time  DATETIME     NULL COMMENT '创建时间',
  update_by    BIGINT       NULL COMMENT '更新人',
  update_time  DATETIME     NULL COMMENT '更新时间',
  del_flag     CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark       VARCHAR(500) NULL COMMENT '备注',
  del_unique   BIGINT       NOT NULL DEFAULT 0 COMMENT '软删除生成 token（应用层 fill）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_person_code (tenant_id, person_code, del_unique),
  KEY idx_post (post_id),
  KEY idx_status (status),
  KEY idx_tenant_create (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员主数据（SYS-MD-001）';

-- ------------------------------------------------------------
-- 4. menu_id 5001 = 人员管理（5000 通用主数据下的二级菜单占位）
--   按钮权限菜单 5010-5014 由 SYS-MD-001 自己 seed
--   role_id 101 (boss) / 102 (manager) 自动挂载（D01 SYS-AUTH-001 已用 BETWEEN 5000 AND 10999 兜底）
--     —— D01 把 role 101-103 全挂 5000-10999 区间，5001 INSERT 后无需补 sys_role_menu
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
   menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (5001, '人员管理', 5000, 1, 'person', 'djs-common/person/index', '', 1, 0,
   'C', '0', '0', 'djs:common:person:list', 'user', 1, NOW(), 'SYS-MD-001 占位');


-- ----------------------------------------------------------------------------
-- 来源文件：V202605210900__SYS-INFRA-004-biz-code-rules.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- SYS-INFRA-004 业务编码规则种子数据
--   9 类编码：
--     业务单据（每日重置 5 + 终生 1）：EAR_NO / DEMAND_NO / SHIP_NO / PACK_NO / STOCK_FLOW_NO / TRACE_CODE
--     主数据（终生递增 3）：MEMBER_NO / STORE_CODE / SUPPLIER_CODE
--   tenant_id 不显式赋值（DEFAULT '1001'，对齐 D01 SYS-INIT-001 规约）
-- ============================================================

INSERT IGNORE INTO t_md_biz_code_rule
  (code_type,       pattern,                                   daily_reset, prefix, seq_length, status, create_by, create_time)
VALUES
  ('EAR_NO',        '{farmCode2}{barnCode2}{yyMM}{dailySeq4}', 1,           '',     4,          '0',    1,         NOW()),
  ('TRACE_CODE',    'T{yyyyMMdd}{productCode2}{seq6}',         0,           'T',    6,          '0',    1,         NOW()),
  ('DEMAND_NO',     'D{yyyyMMdd}{bizCode2}{seq4}',             1,           'D',    4,          '0',    1,         NOW()),
  ('SHIP_NO',       'S{yyyyMMdd}{seq4}',                       1,           'S',    4,          '0',    1,         NOW()),
  ('PACK_NO',       'P{yyyyMMdd}{seq4}',                       1,           'P',    4,          '0',    1,         NOW()),
  ('STOCK_FLOW_NO', 'F{yyyyMMdd}{ioCode2}{seq4}',              1,           'F',    4,          '0',    1,         NOW()),
  ('MEMBER_NO',     'M{seq4}',                                 0,           'M',    4,          '0',    1,         NOW()),
  ('STORE_CODE',    'ST{seq4}',                                0,           'ST',   4,          '0',    1,         NOW()),
  ('SUPPLIER_CODE', 'G{seq4}',                                 0,           'G',    4,          '0',    1,         NOW());


-- ----------------------------------------------------------------------------
-- 来源文件：V202605211200__SYS-MD-001-menu.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- SYS-MD-001 人员管理菜单按钮权限
--   父菜单 5001 (人员管理) 已在 SYS-AUTH-001 占位 (path='person')
--   状态字典走 D1 SYS-INIT-002 已 seed 的 djs_user_status（0 在职/1 离职/2 试用）
--   role 101/102/103 通过 SYS-AUTH-001 BETWEEN 5000-10999 兜底，本文件不写 sys_role_menu
-- ============================================================

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (5010, '人员查询', 5001, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:person:list',    '#', 1, NOW(), 'SYS-MD-001'),
  (5011, '人员新增', 5001, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:person:add',     '#', 1, NOW(), 'SYS-MD-001'),
  (5012, '人员修改', 5001, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:person:edit',    '#', 1, NOW(), 'SYS-MD-001'),
  (5013, '人员删除', 5001, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:person:remove',  '#', 1, NOW(), 'SYS-MD-001'),
  (5014, '人员导出', 5001, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:person:export',  '#', 1, NOW(), 'SYS-MD-001');


-- ----------------------------------------------------------------------------
-- 来源文件：V202605211300__SYS-MD-002-menu.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- SYS-MD-002 门店管理菜单 + 按钮权限
--   一级父菜单 5000 (通用主数据) 已在 SYS-AUTH-001 占位
--   本 ticket 新增二级父菜单 5002 (门店管理) 及 5 个按钮权限 5020-5024
--   role 101/102/103 通过 SYS-AUTH-001 BETWEEN 5000-10999 兜底，本文件不写 sys_role_menu
-- ============================================================

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 二级目录：门店管理（挂在通用主数据 5000 下）
  (5002, '门店管理', 5000, 2, 'store', 'djs-common/store/index', '',
   1, 0, 'C', '0', '0',
   'djs:common:store:list', 'shop', 1, NOW(), 'SYS-MD-002'),

  -- 三级按钮权限
  (5020, '门店查询', 5002, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:store:list',    '#', 1, NOW(), 'SYS-MD-002'),
  (5021, '门店新增', 5002, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:store:add',     '#', 1, NOW(), 'SYS-MD-002'),
  (5022, '门店修改', 5002, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:store:edit',    '#', 1, NOW(), 'SYS-MD-002'),
  (5023, '门店删除', 5002, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:store:remove',  '#', 1, NOW(), 'SYS-MD-002'),
  (5024, '门店导出', 5002, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:store:export',  '#', 1, NOW(), 'SYS-MD-002');


-- ----------------------------------------------------------------------------
-- 来源文件：V202605211400__D02-PATCH-D01-D02-fixes.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- D02 当日修订补丁（D01 推断字段 + D01/D02 字典与 DDL 不自洽 修订）
--   触发：D02 全栈 review 提出
--     1. t_md_store / t_farm_pig_info 4 个 AI 推断字段不符合实际业务
--     2. djs_pig_lifecycle / djs_demand_status dict_value 与 DDL enum 不一致
--     3. djs_check_status 字典缺失（DDL 已引用）
--   修补范围（与重新跑全量初始化 SQL 后结果等价）：
--     A. ALTER DROP 4 个推断字段
--     B. 重写 djs_pig_lifecycle 10 行 dict_data（HB/PZ/PH/FM/DN/LC/KH/FQ/END + BOAR_ACTIVE）
--     C. 修正 djs_demand_status 2 行 dict_value（SCHEDULING→IN_PRODUCTION / PARTIAL→PARTIAL_SHIPPED）
--     D. 新增 djs_check_status 字典 + 3 行 dict_data
--   幂等性：
--     - DROP COLUMN 用 information_schema 守卫，已删则跳过（MySQL 8 无 DROP IF EXISTS COLUMN）
--     - dict_data 用 DELETE + INSERT 重写
--     - dict_type 用 INSERT IGNORE（已存在则跳过）
--   重跑场景：
--     - 已 drop & rebuild dev DB（源 SQL 已修）→ 本 patch 全 no-op
--     - 沿用现 dev DB → 本 patch 完成迁移
-- ============================================================

-- ------------------------------------------------------------
-- A. ALTER DROP 4 个推断字段（D02 review 决策：删）
-- ------------------------------------------------------------

-- A1. t_md_store.warehouse_id：客户 V1 无门店专属仓库需求
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_md_store' AND COLUMN_NAME = 'warehouse_id');
SET @sql := IF(@col > 0, 'ALTER TABLE t_md_store DROP COLUMN warehouse_id', 'SELECT ''A1 skip: t_md_store.warehouse_id absent'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- A2. t_md_store.settle_type：客户 V1 无门店结算需求
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_md_store' AND COLUMN_NAME = 'settle_type');
SET @sql := IF(@col > 0, 'ALTER TABLE t_md_store DROP COLUMN settle_type', 'SELECT ''A2 skip: t_md_store.settle_type absent'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- A3. t_farm_pig_info.current_weight：无日常称重事件源，应改走 t_farm_pig_weight_record（V2 引入）
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_pig_info' AND COLUMN_NAME = 'current_weight');
SET @sql := IF(@col > 0, 'ALTER TABLE t_farm_pig_info DROP COLUMN current_weight', 'SELECT ''A3 skip: t_farm_pig_info.current_weight absent'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- A4. t_farm_pig_info.current_age_days：VO 层从 birth_date 实时算（DATEDIFF），落库冗余
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_pig_info' AND COLUMN_NAME = 'current_age_days');
SET @sql := IF(@col > 0, 'ALTER TABLE t_farm_pig_info DROP COLUMN current_age_days', 'SELECT ''A4 skip: t_farm_pig_info.current_age_days absent'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- B. 重写 djs_pig_lifecycle 字典 dict_value（与 BRD-CORE-001 PigState enum 严格对齐）
--    DDL t_farm_pig_info.current_status DEFAULT 'HB' / 9 枚举：HB/PZ/PH/FM/DN/LC/KH/FQ/END
--    + 公猪固定 BOAR_ACTIVE（不在 9 状态内，但需 enum 字段非空）
-- ------------------------------------------------------------
DELETE FROM sys_dict_data WHERE dict_type = 'djs_pig_lifecycle';
INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001030, '1001', 0, '后备',     'HB',          'djs_pig_lifecycle', '', 'info',    'Y', NULL, NOW()),
  (1001031, '1001', 1, '配种',     'PZ',          'djs_pig_lifecycle', '', 'primary', 'N', NULL, NOW()),
  (1001032, '1001', 2, '配怀',     'PH',          'djs_pig_lifecycle', '', 'primary', 'N', NULL, NOW()),
  (1001033, '1001', 3, '分娩',     'FM',          'djs_pig_lifecycle', '', 'success', 'N', NULL, NOW()),
  (1001034, '1001', 4, '断奶',     'DN',          'djs_pig_lifecycle', '', 'success', 'N', NULL, NOW()),
  (1001035, '1001', 5, '流产',     'LC',          'djs_pig_lifecycle', '', 'warning', 'N', NULL, NOW()),
  (1001036, '1001', 6, '空怀',     'KH',          'djs_pig_lifecycle', '', 'warning', 'N', NULL, NOW()),
  (1001037, '1001', 7, '返情',     'FQ',          'djs_pig_lifecycle', '', 'warning', 'N', NULL, NOW()),
  (1001038, '1001', 8, '终止',     'END',         'djs_pig_lifecycle', '', 'danger',  'N', NULL, NOW()),
  (1001039, '1001', 9, '公猪在产', 'BOAR_ACTIVE', 'djs_pig_lifecycle', '', 'info',    'N', NULL, NOW());

-- ------------------------------------------------------------
-- C. 修正 djs_demand_status 2 行 dict_value（与 DDL t_warehouse_demand_manage.demand_status enum 对齐）
-- ------------------------------------------------------------
UPDATE sys_dict_data SET dict_value = 'IN_PRODUCTION'
  WHERE dict_type = 'djs_demand_status' AND dict_value = 'SCHEDULING';
UPDATE sys_dict_data SET dict_value = 'PARTIAL_SHIPPED'
  WHERE dict_type = 'djs_demand_status' AND dict_value = 'PARTIAL';

-- ------------------------------------------------------------
-- D. 新增 djs_check_status 字典（盘点状态，跨域 warehouse + store）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100601, '1001', '盘点状态', 'djs_check_status', NULL, NOW(), '跨域：t_warehouse_check_record / t_store_check_record.check_status');
DELETE FROM sys_dict_data WHERE dict_type = 'djs_check_status';
INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006010, '1001', 0, '草稿',   'draft',       'djs_check_status', '', 'info',    'Y', NULL, NOW()),
  (1006011, '1001', 1, '进行中', 'in_progress', 'djs_check_status', '', 'warning', 'N', NULL, NOW()),
  (1006012, '1001', 2, '已完成', 'completed',   'djs_check_status', '', 'success', 'N', NULL, NOW());

-- ============================================================
-- 验收（dev MySQL 跑完后预期）：
--   SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
--     AND ((TABLE_NAME='t_md_store' AND COLUMN_NAME IN ('warehouse_id','settle_type'))
--          OR (TABLE_NAME='t_farm_pig_info' AND COLUMN_NAME IN ('current_weight','current_age_days')));
--   -- 0
--   SELECT COUNT(*) FROM sys_dict_type WHERE dict_type LIKE 'djs_%';    -- 39
--   SELECT COUNT(*) FROM sys_dict_data WHERE dict_type LIKE 'djs_%';    -- 228（旧 224 - 9 老 lifecycle + 10 新 + 3 check_status）
--   SELECT dict_value FROM sys_dict_data WHERE dict_type='djs_pig_lifecycle' ORDER BY dict_sort;
--   -- HB,PZ,PH,FM,DN,LC,KH,FQ,END,BOAR_ACTIVE
--   SELECT dict_value FROM sys_dict_data WHERE dict_type='djs_demand_status' ORDER BY dict_sort;
--   -- DRAFT,SUBMITTED,CONFIRMED,IN_PRODUCTION,PARTIAL_SHIPPED,COMPLETED,CANCELLED
-- ============================================================


-- ----------------------------------------------------------------------------
-- 来源文件：V202605211800__D02-PATCH-fix-audit-cols.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- D02 hot-patch — 把 D01/D02 创建的 3 张主数据/编码表的审计字段对齐 ruoyi BaseEntity 约定
--   ruoyi BaseEntity: createDept Long / createBy Long / updateBy Long （不是 varchar）
--   现状：t_md_biz_code_rule / t_md_biz_code_sequence / t_md_person 都用 varchar(64) + 缺 create_dept
--   后果：MyBatis-Plus auto-SELECT 含 create_dept → SQLSyntaxErrorException
--         seed 写入 'system' → 后续 ResultSet.getLong(create_by) NumberFormatException
--   修复步骤：先 UPDATE 把字符串值改成 NULL / 1，再 MODIFY 类型为 BIGINT，最后 ADD create_dept
-- ============================================================

-- ------------------------------------------------------------
-- t_md_biz_code_rule（9 行 seed，create_by='system' / update_by=''）
-- ------------------------------------------------------------
UPDATE t_md_biz_code_rule SET create_by = '1' WHERE create_by = 'system';
UPDATE t_md_biz_code_rule SET update_by = NULL WHERE update_by = '' OR update_by IS NULL OR update_by = '0';
ALTER TABLE t_md_biz_code_rule
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门' AFTER status;

-- ------------------------------------------------------------
-- t_md_biz_code_sequence（0 行，简单 ALTER）
-- ------------------------------------------------------------
UPDATE t_md_biz_code_sequence SET create_by = NULL WHERE create_by = '' OR create_by IS NULL;
UPDATE t_md_biz_code_sequence SET update_by = NULL WHERE update_by = '' OR update_by IS NULL;
ALTER TABLE t_md_biz_code_sequence
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门' AFTER current_seq;

-- ------------------------------------------------------------
-- t_md_person（0 行）
-- ------------------------------------------------------------
UPDATE t_md_person SET create_by = NULL WHERE create_by = '' OR create_by IS NULL;
UPDATE t_md_person SET update_by = NULL WHERE update_by = '' OR update_by IS NULL;
ALTER TABLE t_md_person
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门' AFTER avatar_url;

-- ------------------------------------------------------------
-- sys_menu 5001 占位的 create_by 也是 'system'，改成 1
--   （ruoyi 自带 sys_menu.create_by 已经是 bigint，存 'system' 等于隐式转 0；这里改成显式 1=admin）
-- ------------------------------------------------------------
UPDATE sys_menu SET create_by = 1 WHERE menu_id = 5001 AND (create_by = 0 OR create_by IS NULL);


-- ----------------------------------------------------------------------------
-- 来源文件：V202605211900__D02-PATCH-65-tables-audit-cols.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- D02 closing patch — 65 张 D01 SYS-INIT-001 业务表审计字段对齐 ruoyi BaseEntity
--   一次性 ALTER 62 张表（DB 实际存在 + 还是 varchar 的）：
--     MODIFY create_by  BIGINT NULL
--     MODIFY update_by  BIGINT NULL（若存在 varchar）
--     ADD    create_dept BIGINT NULL（若不存在）
--   源 DDL V202605200900~V202605200904 已同步用 BIGINT + create_dept；本 patch 让运行库追上。
--   先 UPDATE 字符串 audit 列为 NULL，避免 'system' 字符串 NumberFormatException
-- ============================================================

UPDATE sys_farm SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE sys_farm SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE sys_farm
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_breed_medicine_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_breed_medicine_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_breed_medicine_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_breed_medicine_use SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_breed_medicine_use SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_breed_medicine_use
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_breed_production_config SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_breed_production_config SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_breed_production_config
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_barn_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_barn_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_barn_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_barn_pen SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_barn_pen SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_barn_pen
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_breed_config SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_breed_config SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_breed_config
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_breed_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_breed_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_breed_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_castrate_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_castrate_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_castrate_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_grow_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_grow_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_grow_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_medicine_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_medicine_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_medicine_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_abnormal SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_abnormal SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_abnormal
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_breeding SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_breeding SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_breeding
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_culling SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_culling SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_culling
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_death SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_death SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_death
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_farrow SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_farrow SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_farrow
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_heat SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_heat SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_heat
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_introduce SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_introduce SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_introduce
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_marketing SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_marketing SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_marketing
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_pigletno SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_pigletno SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_pigletno
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_transfer SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_transfer SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_transfer
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_pig_weaning SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_pig_weaning SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_pig_weaning
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_status_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
ALTER TABLE t_farm_status_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_farm_wean_weight SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_farm_wean_weight SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_farm_wean_weight
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_md_store SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_md_store SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_md_store
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_md_supplier SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_md_supplier SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_md_supplier
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_crop_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_crop_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_crop_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_crop_organic SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_crop_organic SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_crop_organic
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_farm_records SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_farm_records SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_farm_records
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_organic_plotno SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
ALTER TABLE t_plant_organic_plotno
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_pick_activity SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_pick_activity SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_pick_activity
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_plant_details SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_plant_details SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_plant_details
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_plant_plan SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_plant_plan SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_plant_plan
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_plot_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_plot_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_plot_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_plot_organic SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_plot_organic SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_plot_organic
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_plot_zone SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_plot_zone SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_plot_zone
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_work_people SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_work_people SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_work_people
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_work_performance SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_work_performance SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_work_performance
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_work_team SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_plant_work_team SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_plant_work_team
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_plant_zone_plotno SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
ALTER TABLE t_plant_zone_plotno
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_store_check_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_store_check_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_store_check_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_store_member SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_store_member SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_store_member
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_store_member_consumption SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_store_member_consumption SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_store_member_consumption
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_store_product_relation SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_store_product_relation SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_store_product_relation
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_store_return SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_store_return SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_store_return
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_store_sale_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_store_sale_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_store_sale_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_bar_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_bar_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_bar_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_check_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_check_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_check_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_demand_manage SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_demand_manage SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_demand_manage
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_handle_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_handle_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_handle_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_location_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_location_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_location_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_location_stock SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_location_stock SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_location_stock
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_planting_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_planting_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_planting_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_product_info SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_product_info SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_product_info
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_product_produce SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_product_produce SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_product_produce
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_product_production SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_product_production SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_product_production
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_return_product SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_return_product SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_return_product
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_stock_flow SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_stock_flow SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_stock_flow
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_supplier_record SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_supplier_record SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_supplier_record
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_trace_code SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_trace_code SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_trace_code
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

UPDATE t_warehouse_vegetable_handle SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '' OR create_by IS NULL;
UPDATE t_warehouse_vegetable_handle SET update_by = NULL WHERE update_by NOT REGEXP '^[0-9]+$' OR update_by = '' OR update_by IS NULL;
ALTER TABLE t_warehouse_vegetable_handle
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门';

-- sys_farm seed: create_by 'system' → 1 (idempotent — UPDATE 仅当当前值是 'system' 才生效)
UPDATE sys_farm SET create_by = NULL WHERE create_by NOT REGEXP '^[0-9]+$' OR create_by = '';
UPDATE sys_farm SET create_by = 1 WHERE id = 1001 AND (create_by IS NULL OR create_by = 0);


-- ----------------------------------------------------------------------------
-- 来源文件：V202605220900__SYS-MD-003-menu.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- SYS-MD-003 供应商管理菜单 + 按钮权限
--   父菜单 5003 (供应商管理) 由本 ticket seed（SYS-AUTH-001 未占位）
--   字典 djs_supplier_type 6 类已在 D1 SYS-INIT-002 灌
--   role 101/102/103 通过 SYS-AUTH-001 BETWEEN 5000-10999 兜底，本文件不写 sys_role_menu
-- ============================================================

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (5003, '供应商管理', 5000, 3, 'supplier', 'djs-common/supplier/index', '', 1, 0, 'C', '0', '0',
   'djs:common:supplier:list',     'peoples', 1, NOW(), 'SYS-MD-003'),
  (5030, '供应商查询', 5003, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:supplier:list',     '#', 1, NOW(), 'SYS-MD-003'),
  (5031, '供应商新增', 5003, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:supplier:add',      '#', 1, NOW(), 'SYS-MD-003'),
  (5032, '供应商修改', 5003, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:supplier:edit',     '#', 1, NOW(), 'SYS-MD-003'),
  (5033, '供应商删除', 5003, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:supplier:remove',   '#', 1, NOW(), 'SYS-MD-003'),
  (5034, '供应商导出', 5003, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:common:supplier:export',   '#', 1, NOW(), 'SYS-MD-003');


-- ----------------------------------------------------------------------------
-- 来源文件：V202605221000__BRD-MD-001-breeding-menu-and-dict.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- BRD-MD-001 育种配置（品种 / 品系 / 配种关系）
--   表 t_farm_breed_info + t_farm_breed_config 已在 SYS-INIT-001 V202605200901 建好
--   本文件只 seed：
--     1. 字典 djs_breed_strain_type（1=品种 / 2=品系）
--     2. 父菜单 7000 (养殖) 已在 SYS-AUTH-001 V202605201100 占位
--     3. 二级目录 7010 (育种配置) + 4 个 C 菜单 7016-7019（品种/品系/品种配种/品系配种）
--     4. 三级按钮权限 7011-7015（list/add/edit/remove/export）
--   权限串 djs:breed:breeding:{list,add,edit,remove,export} 同时覆盖品种/品系/配种关系两个 Controller
--   （4 个 C 菜单共用一组按钮权限，BreedInfoController + BreedConfigController 共用）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 字典 djs_breed_strain_type（品种/品系类型，TINYINT 1/2 与表字段 breed_strain 对齐）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type (
    dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100151, '1001', '育种类型', 'djs_breed_strain_type', 1, NOW(), '养殖：1=品种 / 2=品系（BRD-MD-001 / t_farm_breed_info.breed_strain 字段对齐）');

INSERT IGNORE INTO sys_dict_data (
    dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001510, '1001', 0, '品种', '1', 'djs_breed_strain_type', '', 'primary', 'Y', 1, NOW()),
  (1001511, '1001', 1, '品系', '2', 'djs_breed_strain_type', '', 'success', 'N', 1, NOW());

-- ------------------------------------------------------------
-- 2. 菜单：育种配置目录 + 4 个 C 子菜单 + 5 个按钮权限
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 二级目录：育种配置（M 目录，下挂 4 个 C 菜单）
  (7010, '育种配置', 7000, 1, 'breeding-config', '', '',
   1, 0, 'M', '0', '0',
   '', 'tree', 1, NOW(), 'BRD-MD-001'),

  -- 4 个 C 子菜单（独立路由 + 独立组件）
  (7016, '品种管理', 7010, 1, 'breed-strain',      'djs-breed/breeding-config/strain',      '',
   1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'BRD-MD-001'),
  (7017, '品系管理', 7010, 2, 'breed-line',        'djs-breed/breeding-config/line',        '',
   1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'BRD-MD-001'),
  (7018, '品种配种', 7010, 3, 'breed-mate-strain', 'djs-breed/breeding-config/mate-strain', '',
   1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'BRD-MD-001'),
  (7019, '品系配种', 7010, 4, 'breed-mate-line',   'djs-breed/breeding-config/mate-line',   '',
   1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'BRD-MD-001'),

  -- 三级按钮权限（5 个，与后端 @SaCheckPermission 严格一致；4 个 C 菜单共用）
  (7011, '育种查询', 7010, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:breeding:list',   '#', 1, NOW(), 'BRD-MD-001'),
  (7012, '育种新增', 7010, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:breeding:add',    '#', 1, NOW(), 'BRD-MD-001'),
  (7013, '育种修改', 7010, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:breeding:edit',   '#', 1, NOW(), 'BRD-MD-001'),
  (7014, '育种删除', 7010, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:breeding:remove', '#', 1, NOW(), 'BRD-MD-001'),
  (7015, '育种导出', 7010, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:breeding:export', '#', 1, NOW(), 'BRD-MD-001');


-- ----------------------------------------------------------------------------
-- 来源文件：V202605221100__BRD-MD-002-farm-barn-pen-menu.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- BRD-MD-002 农场 + 栋舍 + 栏位 菜单 seed
--   表 sys_farm / t_farm_barn_info / t_farm_barn_pen 已在 SYS-INIT-001 cleanup 阶段建好
--   字典 djs_barn_type / djs_pen_type / djs_farm_status 已在 SYS-AUTH-001 seed
--   本文件只 seed 三块菜单（养殖目录 7000 下）：
--     7020-7022  农场信息（query / edit）        路由 djs-breed/farm/index  权限 djs:breed:farm-info:*
--     7030-7034  栋舍管理（list/add/edit/remove）              权限 djs:breed:barn:*
--     7040-7044  栏位管理（list/add/edit/remove）              权限 djs:breed:pen:*
--   注：栋舍 + 栏位 + 农场详情共用同一前端单页 djs-breed/farm/index.vue
--       （左侧 el-tree 栋舍→栏位，右侧 panel；农场只读 panel 在顶部）
--       菜单层"农场信息"挂目录，其下 5+5+2 = 12 个按钮权限串
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 二级目录：农场信息（4 tab/section 单页）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7020, '农场信息', 7000, 2, 'farm', 'djs-breed/farm/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:farm-info:query', 'building', 1, NOW(), 'BRD-MD-002');

-- ------------------------------------------------------------
-- 2. 农场信息按钮权限（2 个：query / edit）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7021, '农场查询', 7020, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:farm-info:query', '#', 1, NOW(), 'BRD-MD-002'),
  (7022, '农场联系信息编辑', 7020, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:farm-info:edit',  '#', 1, NOW(), 'BRD-MD-002');

-- ------------------------------------------------------------
-- 3. 栋舍按钮权限（4 个：list/add/edit/remove；与后端 @SaCheckPermission 严格一致）
--    挂在 7020 农场信息目录下（同一单页操作）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7030, '栋舍查询', 7020, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:barn:list',   '#', 1, NOW(), 'BRD-MD-002'),
  (7031, '栋舍新增', 7020, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:barn:add',    '#', 1, NOW(), 'BRD-MD-002'),
  (7032, '栋舍修改', 7020, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:barn:edit',   '#', 1, NOW(), 'BRD-MD-002'),
  (7033, '栋舍删除', 7020, 6, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:barn:remove', '#', 1, NOW(), 'BRD-MD-002');

-- ------------------------------------------------------------
-- 4. 栏位按钮权限（4 个）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7040, '栏位查询', 7020, 7, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pen:list',   '#', 1, NOW(), 'BRD-MD-002'),
  (7041, '栏位新增', 7020, 8, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pen:add',    '#', 1, NOW(), 'BRD-MD-002'),
  (7042, '栏位修改', 7020, 9, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pen:edit',   '#', 1, NOW(), 'BRD-MD-002'),
  (7043, '栏位删除', 7020, 10, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pen:remove', '#', 1, NOW(), 'BRD-MD-002');


-- ----------------------------------------------------------------------------
-- 来源文件：V202605221101__BRD-MED-001-medicine-batch.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- BRD-MED-001 药品库 + 药品批次
--
-- 现状：
--   表 t_breed_medicine_info 已在 SYS-INIT-001 V202605200901 建好（含
--     medicine_code/name/type/supplier_id/approval_no/batch_no/expire_date/
--     withdraw_days/unit/current_stock + 审计 + del_flag + del_unique）。
--   prompt 数据模型段叫 t_farm_medicine，与 DB / doc/06 / _db-changes 已落地的
--   t_breed_medicine_info 不一致；本 ticket 按 DB + doc/06 走（详 D04 _open-issues raise）。
--
-- 本文件做 3 件事：
--   1. 给 t_breed_medicine_info 加 4 列：spec / manufacturer / storage_condition / med_status
--   2. 建批次表 t_breed_medicine_batch（V1 admin 暴露批次能力；V2 BRD-MED-002 FIFO 扣减用）
--   3. seed 菜单：父 7000(养殖) 下二级 7040(药品库) + 7050(药品批次) + 子按钮权限
--      （djs_med_type 字典已在 SYS-INIT-002 V202605201000 seed，无需重做）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 给 t_breed_medicine_info 增列（spec / manufacturer / storage_condition / med_status）
--    用 INFORMATION_SCHEMA 探测列存在性，可重入；同 D02-PATCH 风格
-- ------------------------------------------------------------
SET @schema := DATABASE();
SET @tbl := 't_breed_medicine_info';

-- spec
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = @tbl AND COLUMN_NAME = 'spec');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_breed_medicine_info ADD COLUMN spec VARCHAR(128) NULL COMMENT ''规格（如 10ml × 100 支 / 盒）'' AFTER current_stock',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- manufacturer
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = @tbl AND COLUMN_NAME = 'manufacturer');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_breed_medicine_info ADD COLUMN manufacturer VARCHAR(128) NULL COMMENT ''生产厂家'' AFTER spec',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- storage_condition
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = @tbl AND COLUMN_NAME = 'storage_condition');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_breed_medicine_info ADD COLUMN storage_condition VARCHAR(200) NULL COMMENT ''储存条件（如 2-8℃ 冷藏 避光）'' AFTER manufacturer',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- med_status
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema AND TABLE_NAME = @tbl AND COLUMN_NAME = 'med_status');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_breed_medicine_info ADD COLUMN med_status TINYINT NOT NULL DEFAULT 1 COMMENT ''状态 1=启用 0=停用（对齐 sys_normal_disable）'' AFTER storage_condition',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ------------------------------------------------------------
-- 2. 建批次表 t_breed_medicine_batch（BRD-MED-001 批次能力，V2 FIFO 扣减用）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_breed_medicine_batch;
CREATE TABLE t_breed_medicine_batch (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  medicine_id       BIGINT       NOT NULL COMMENT '药品 ID（引用 t_breed_medicine_info.id）',
  batch_no          VARCHAR(64)  NOT NULL COMMENT '批次编码',
  production_date   DATE         NULL COMMENT '生产日期',
  expiry_date       DATE         NULL COMMENT '过期日期',
  quantity          DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '批次当前剩余库存',
  unit_price        DECIMAL(18,2) NULL COMMENT '进货单价',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by         BIGINT       NULL COMMENT '创建人',
  create_time       DATETIME     NULL COMMENT '创建时间',
  update_by         BIGINT       NULL COMMENT '更新人',
  update_time       DATETIME     NULL COMMENT '更新时间',
  del_flag          CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark            VARCHAR(500) NULL COMMENT '备注',
  del_unique        BIGINT       NOT NULL DEFAULT 0 COMMENT "软删 token（应用层 update del_flag='1' 时同步 SET del_unique=id）",
  PRIMARY KEY (id),
  UNIQUE KEY uk_med_batch (tenant_id, medicine_id, batch_no, del_unique),
  KEY idx_tenant_create (tenant_id, create_time),
  KEY idx_medicine (medicine_id),
  KEY idx_expiry (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品批次表（BRD-MED-001）';


-- ------------------------------------------------------------
-- 3. 菜单 seed（父 7000=养殖 已有；二级 7060 药品库 / 7070 药品批次）
--    分段说明：BRD-MD-001 占 7010-7015（育种）/ BRD-MD-002 占 7020-7043
--    （农场 7020-7022 + 栋舍 7030-7033 + 栏位 7040-7043）
--    本 ticket 跳过 7040-7059 段避免 BRD-MD-002 扩展冲突；用 7060-7079
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 二级目录：药品库
  (7060, '药品库', 7000, 4, 'med', 'djs-breed/med/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:med:list', 'medication', 1, NOW(), 'BRD-MED-001'),

  -- 子按钮权限（药品库 5 个，与后端 @SaCheckPermission 严格一致）
  (7061, '药品查询', 7060, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med:list',   '#', 1, NOW(), 'BRD-MED-001'),
  (7062, '药品新增', 7060, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med:add',    '#', 1, NOW(), 'BRD-MED-001'),
  (7063, '药品修改', 7060, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med:edit',   '#', 1, NOW(), 'BRD-MED-001'),
  (7064, '药品删除', 7060, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med:remove', '#', 1, NOW(), 'BRD-MED-001'),
  (7065, '药品导出', 7060, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med:export', '#', 1, NOW(), 'BRD-MED-001'),

  -- 二级目录：药品批次
  (7070, '药品批次', 7000, 5, 'med-batch', 'djs-breed/med/batch', '',
   1, 0, 'C', '0', '0',
   'djs:breed:med-batch:list', 'list', 1, NOW(), 'BRD-MED-001'),

  -- 子按钮权限（药品批次 5 个）
  (7071, '批次查询', 7070, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med-batch:list',   '#', 1, NOW(), 'BRD-MED-001'),
  (7072, '批次新增', 7070, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med-batch:add',    '#', 1, NOW(), 'BRD-MED-001'),
  (7073, '批次修改', 7070, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med-batch:edit',   '#', 1, NOW(), 'BRD-MED-001'),
  (7074, '批次删除', 7070, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med-batch:remove', '#', 1, NOW(), 'BRD-MED-001'),
  (7075, '批次导出', 7070, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:med-batch:export', '#', 1, NOW(), 'BRD-MED-001');


-- ----------------------------------------------------------------------------
-- 来源文件：V202605221200__BRD-MD-003-production-configs.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- BRD-MD-003 生产配置（3 tab）
--   tab1 生产周期 / tab2 精液公猪 / tab3 药品疫苗周期
--
-- 本文件内容：
--   1. 建表 t_farm_production_cycle_config / t_farm_boar_config / t_farm_med_schedule_config
--      （SYS-INIT-001 V202605200901 未建过这 3 张表，所以本文件首次创建）
--   2. seed 字典 djs_med_event_trigger（药品周期触发时机枚举）
--      （djs_med_type 已在 BRD-MED-001 灌过，本文件不重复）
--   3. seed 6 个生产周期业内默认值（gestation/lactation/nursery/fattening/oestrus_cycle/weaning_to_breeding）
--   4. 菜单：父 7000 (养殖) 下 3 个二级目录
--      - 7050-7059 production-cycle 生产周期
--      - 7080-7089 production-boar  精液公猪
--      - 7090-7099 production-med   药品疫苗周期
--
-- 权限串：
--   - djs:breed:production-cycle:{list,add,edit,remove,export}
--   - djs:breed:production-boar:{list,add,edit,remove,export}
--   - djs:breed:production-med:{list,add,edit,remove,export}
--
-- v1.2 关键约束：无定时任务 / 无自动流转 —— 本配置只决定"建议时间"，
-- 状态转换 / 任务生成全靠 BRD-EVENT-* / BRD-CORE-001 状态机事件触发。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1.1 t_farm_production_cycle_config  生产周期配置（Tab1）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_farm_production_cycle_config` (
  `id`             BIGINT       NOT NULL                       COMMENT '主键（雪花）',
  `tenant_id`      VARCHAR(20)  NOT NULL DEFAULT '1001'        COMMENT '农场ID（多租户，V1 全 1001 / ADR-0001）',
  `config_key`     VARCHAR(64)  NOT NULL                       COMMENT '业务键（如 gestation_days）',
  `default_value`  INT          NOT NULL                       COMMENT '业内默认值（天，seed 灌入，admin 不可改）',
  `custom_value`   INT          NULL                           COMMENT '客户自定义值（天，admin 可改，null = 沿用 default）',
  `unit`           VARCHAR(16)  NOT NULL DEFAULT '天'          COMMENT '单位',
  `description`    VARCHAR(255) NULL                           COMMENT '业务含义说明',
  `remark`         VARCHAR(500) NULL                           COMMENT '备注',
  `create_dept`    BIGINT       NULL                           COMMENT '创建部门',
  `create_by`      BIGINT       NULL                           COMMENT '创建者',
  `create_time`    DATETIME     NULL                           COMMENT '创建时间',
  `update_by`      BIGINT       NULL                           COMMENT '更新者',
  `update_time`    DATETIME     NULL                           COMMENT '更新时间',
  `del_flag`       CHAR(1)      NULL DEFAULT '0'               COMMENT '软删（0 未删 / 1 已删）',
  `del_unique`     BIGINT       NOT NULL DEFAULT 0             COMMENT '软删唯一性辅助（未删=0，已删=id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cycle_key`  (`tenant_id`, `config_key`, `del_unique`),
  KEY         `idx_tenant`    (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产周期配置（BRD-MD-003 Tab1）';

-- ------------------------------------------------------------
-- 1.2 t_farm_boar_config  精液 / 公猪配置（Tab2）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_farm_boar_config` (
  `id`                         BIGINT         NOT NULL                       COMMENT '主键（雪花）',
  `tenant_id`                  VARCHAR(20)    NOT NULL DEFAULT '1001'        COMMENT '农场ID（多租户，V1 全 1001）',
  `boar_id`                    BIGINT         NULL                           COMMENT '关联公猪 ID（V1 NULL = 通用配置 / V2 启用具体公猪覆盖）',
  `sperm_quality_threshold`    DECIMAL(8,2)   NOT NULL                       COMMENT '精液密度阈值（亿/mL）',
  `breeding_interval_days`     INT            NOT NULL                       COMMENT '同公猪两次采精最小间隔天数',
  `remark`                     VARCHAR(500)   NULL                           COMMENT '备注',
  `create_dept`                BIGINT         NULL                           COMMENT '创建部门',
  `create_by`                  BIGINT         NULL                           COMMENT '创建者',
  `create_time`                DATETIME       NULL                           COMMENT '创建时间',
  `update_by`                  BIGINT         NULL                           COMMENT '更新者',
  `update_time`                DATETIME       NULL                           COMMENT '更新时间',
  `del_flag`                   CHAR(1)        NULL DEFAULT '0'               COMMENT '软删',
  `del_unique`                 BIGINT         NOT NULL DEFAULT 0             COMMENT '软删唯一性辅助',
  PRIMARY KEY (`id`),
  -- boar_id NULL 时 MySQL UNIQUE 不会冲突（NULL != NULL），刚好满足 V1 "通用配置一条 + 后续可加针对具体公猪覆盖"
  UNIQUE KEY `uk_boar_id`     (`tenant_id`, `boar_id`, `del_unique`),
  KEY         `idx_tenant`    (`tenant_id`),
  KEY         `idx_boar_id`   (`boar_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='精液 / 公猪配置（BRD-MD-003 Tab2）';

-- ------------------------------------------------------------
-- 1.3 t_farm_med_schedule_config  药品 / 疫苗周期配置（Tab3）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_farm_med_schedule_config` (
  `id`              BIGINT       NOT NULL                       COMMENT '主键（雪花）',
  `tenant_id`       VARCHAR(20)  NOT NULL DEFAULT '1001'        COMMENT '农场ID',
  `med_type`        VARCHAR(32)  NOT NULL                       COMMENT '药品类型（字典 djs_med_type）',
  `event_trigger`   VARCHAR(64)  NOT NULL                       COMMENT '触发时机（字典 djs_med_event_trigger）',
  `days_offset`     INT          NOT NULL                       COMMENT '天数偏移（正 = 事件后 / 负 = 事件前）',
  `description`     VARCHAR(255) NULL                           COMMENT '业务含义说明',
  `remark`          VARCHAR(500) NULL                           COMMENT '备注',
  `create_dept`     BIGINT       NULL                           COMMENT '创建部门',
  `create_by`       BIGINT       NULL                           COMMENT '创建者',
  `create_time`     DATETIME     NULL                           COMMENT '创建时间',
  `update_by`       BIGINT       NULL                           COMMENT '更新者',
  `update_time`     DATETIME     NULL                           COMMENT '更新时间',
  `del_flag`        CHAR(1)      NULL DEFAULT '0'               COMMENT '软删',
  `del_unique`      BIGINT       NOT NULL DEFAULT 0             COMMENT '软删唯一性辅助',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_med_trigger` (`tenant_id`, `med_type`, `event_trigger`, `days_offset`, `del_unique`),
  KEY         `idx_tenant`    (`tenant_id`),
  KEY         `idx_trigger`   (`event_trigger`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品 / 疫苗周期配置（BRD-MD-003 Tab3）';

-- ============================================================
-- 2. 字典：djs_med_event_trigger（药品触发时机枚举，BRD-MED-002 自动建任务时查询用）
--    （djs_med_type 由 BRD-MED-001 V202605221100 灌过，本文件不重复）
-- ============================================================
INSERT IGNORE INTO sys_dict_type (
    dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100153, '1001', '药品触发时机', 'djs_med_event_trigger', 1, NOW(), '养殖：药品 / 疫苗周期触发的业务事件类型（BRD-MD-003 Tab3）');

INSERT IGNORE INTO sys_dict_data (
    dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001530, '1001', 0, '仔猪出生',  'birth',            'djs_med_event_trigger', '', 'primary', 'N', 1, NOW()),
  (1001531, '1001', 1, '断奶',      'weaning',          'djs_med_event_trigger', '', 'success', 'N', 1, NOW()),
  (1001532, '1001', 2, '转入育肥',  'fattening_start',  'djs_med_event_trigger', '', 'success', 'N', 1, NOW()),
  (1001533, '1001', 3, '配种',      'mating',           'djs_med_event_trigger', '', 'warning', 'N', 1, NOW()),
  (1001534, '1001', 4, '妊娠确认',  'pregnant',         'djs_med_event_trigger', '', 'warning', 'N', 1, NOW()),
  (1001535, '1001', 5, '分娩',      'farrow',           'djs_med_event_trigger', '', 'danger',  'N', 1, NOW()),
  (1001536, '1001', 6, '引种入栏',  'introduce',        'djs_med_event_trigger', '', 'info',    'N', 1, NOW());

-- ============================================================
-- 3. seed 6 个生产周期业内默认值（v1.1 / spawn prompt 明确数值）
--    INSERT 不显式赋 tenant_id —— 走 MetaObjectHandler.insertFill 自动填 '1001'
--    （但本 seed 在 ruoyi 启动前跑，handler 不生效 → 显式写 tenant_id）
-- ============================================================
INSERT IGNORE INTO t_farm_production_cycle_config (
    id, tenant_id, config_key, default_value, custom_value, unit, description, create_by, create_time, del_flag, del_unique)
VALUES
  (1, '1001', 'gestation_days',           114, NULL, '天', '妊娠天数（母猪配种到分娩的标准周期）',         1, NOW(), '0', 0),
  (2, '1001', 'lactation_days',            28, NULL, '天', '哺乳天数（仔猪从出生到断奶的标准时长）',       1, NOW(), '0', 0),
  (3, '1001', 'nursery_days',              35, NULL, '天', '保育天数（仔猪从断奶到转入育肥的标准时长）',   1, NOW(), '0', 0),
  (4, '1001', 'fattening_days',           120, NULL, '天', '育肥天数（保育结束到达出栏的标准时长）',       1, NOW(), '0', 0),
  (5, '1001', 'oestrus_cycle_days',        21, NULL, '天', '发情周期（母猪一个发情周期的标准时长）',       1, NOW(), '0', 0),
  (6, '1001', 'weaning_to_breeding_days',   7, NULL, '天', '断奶到配种（母猪断奶后到下次配种的建议时长）', 1, NOW(), '0', 0);

-- ============================================================
-- 4. 菜单：3 个二级目录（挂养殖目录 7000 下）+ 各自按钮权限
-- ============================================================

-- ----------------------------------------------------
-- 4.1 二级目录：生产周期 / 精液公猪 / 药品周期（各一个）
-- ----------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 生产周期（Tab1 入口，但物理上单页 3 tab 在 production-config/index.vue 内）
  (7050, '生产配置', 7000, 5, 'production-config', 'djs-breed/production-config/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:production-cycle:list', 'time', 1, NOW(), 'BRD-MD-003 生产配置主入口（3 tab 单页）');

-- ----------------------------------------------------
-- 4.2 生产周期 按钮权限（5 个，挂在生产配置主菜单 7050 下）
-- ----------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7051, '生产周期查询', 7050, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-cycle:list',   '#', 1, NOW(), 'BRD-MD-003 Tab1'),
  (7052, '生产周期新增', 7050, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-cycle:add',    '#', 1, NOW(), 'BRD-MD-003 Tab1'),
  (7053, '生产周期修改', 7050, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-cycle:edit',   '#', 1, NOW(), 'BRD-MD-003 Tab1'),
  (7054, '生产周期删除', 7050, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-cycle:remove', '#', 1, NOW(), 'BRD-MD-003 Tab1'),
  (7055, '生产周期导出', 7050, 5, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-cycle:export', '#', 1, NOW(), 'BRD-MD-003 Tab1');

-- ----------------------------------------------------
-- 4.3 精液 / 公猪配置 按钮权限（5 个，挂在 7050 下）
-- ----------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7080, '公猪配置查询', 7050, 6, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-boar:list',   '#', 1, NOW(), 'BRD-MD-003 Tab2'),
  (7081, '公猪配置新增', 7050, 7, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-boar:add',    '#', 1, NOW(), 'BRD-MD-003 Tab2'),
  (7082, '公猪配置修改', 7050, 8, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-boar:edit',   '#', 1, NOW(), 'BRD-MD-003 Tab2'),
  (7083, '公猪配置删除', 7050, 9, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-boar:remove', '#', 1, NOW(), 'BRD-MD-003 Tab2'),
  (7084, '公猪配置导出', 7050, 10, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-boar:export', '#', 1, NOW(), 'BRD-MD-003 Tab2');

-- ----------------------------------------------------
-- 4.4 药品 / 疫苗周期 按钮权限（5 个，挂在 7050 下）
-- ----------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7090, '药品周期查询', 7050, 11, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-med:list',   '#', 1, NOW(), 'BRD-MD-003 Tab3'),
  (7091, '药品周期新增', 7050, 12, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-med:add',    '#', 1, NOW(), 'BRD-MD-003 Tab3'),
  (7092, '药品周期修改', 7050, 13, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-med:edit',   '#', 1, NOW(), 'BRD-MD-003 Tab3'),
  (7093, '药品周期删除', 7050, 14, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-med:remove', '#', 1, NOW(), 'BRD-MD-003 Tab3'),
  (7094, '药品周期导出', 7050, 15, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:production-med:export', '#', 1, NOW(), 'BRD-MD-003 Tab3');

-- ============================================================
-- 5. 角色 → 菜单：boss(102) / manager(103) / breed_admin(104) 给本 ticket 菜单可见
--    （SYS-AUTH-001 的一次性 SELECT 已建过，但只在那次扫的范围里；新菜单要追加）
-- ============================================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 102, menu_id FROM sys_menu WHERE menu_id IN (7050,7051,7052,7053,7054,7055,7080,7081,7082,7083,7084,7090,7091,7092,7093,7094);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 103, menu_id FROM sys_menu WHERE menu_id IN (7050,7051,7052,7053,7054,7055,7080,7081,7082,7083,7084,7090,7091,7092,7093,7094);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 104, menu_id FROM sys_menu WHERE menu_id IN (7050,7051,7052,7053,7054,7055,7080,7081,7082,7083,7084,7090,7091,7092,7093,7094);


-- ----------------------------------------------------------------------------
-- 来源文件：V202605221201__SYS-FIX-001-biz-dict-supplement.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- SYS-FIX-001 biz 组件字典补 seed（D03 _open-issues #37）
-- 范围: 7 类 dict_type + ~37 条 dict_data
--   dict_id 100600-100699 / dict_code 1006000-1006099（H 跨域补充段）
-- 约束:
--   1. INSERT IGNORE 幂等
--   2. tenant_id 全 '1001'
--   3. 与 doc/06 BRD-CORE-001 母猪状态机 / BRD-MED-* / WMS-DEMAND-* 命名严格对齐
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- H1 djs_sow_status 母猪状态机（7 状态 — 与 BRD-CORE-001 SowStatus enum 严格一致）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100600, '1001', '母猪状态', 'djs_sow_status', NULL, NOW(), '养殖：母猪状态机 7 状态，BRD-CORE-001 enum 严格对齐');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006000, '1001', 0, '空怀',   'EMPTY',       'djs_sow_status', '', 'info',    'Y', NULL, NOW()),
  (1006001, '1001', 1, '配种',   'BREEDING',    'djs_sow_status', '', 'primary', 'N', NULL, NOW()),
  (1006002, '1001', 2, '妊娠',   'PREGNANT',    'djs_sow_status', '', 'primary', 'N', NULL, NOW()),
  (1006003, '1001', 3, '分娩',   'FARROWING',   'djs_sow_status', '', 'success', 'N', NULL, NOW()),
  (1006004, '1001', 4, '哺乳',   'LACTATING',   'djs_sow_status', '', 'success', 'N', NULL, NOW()),
  (1006005, '1001', 5, '返情',   'RETURN_HEAT', 'djs_sow_status', '', 'warning', 'N', NULL, NOW()),
  (1006006, '1001', 6, '淘汰',   'ELIMINATED',  'djs_sow_status', '', 'danger',  'N', NULL, NOW());

-- ------------------------------------------------------------
-- H2 djs_material_type 物资类型（仓库 biz 组件 MaterialCard / MaterialList）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100601, '1001', '物资类型', 'djs_material_type', NULL, NOW(), '仓库：饲料/兽药/疫苗/工器具/其他');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006015, '1001', 0, '饲料',   'feed',    'djs_material_type', '', 'primary', 'Y', NULL, NOW()),
  (1006016, '1001', 1, '兽药',   'med',     'djs_material_type', '', 'warning', 'N', NULL, NOW()),
  (1006017, '1001', 2, '疫苗',   'vaccine', 'djs_material_type', '', 'success', 'N', NULL, NOW()),
  (1006018, '1001', 3, '工器具', 'tool',    'djs_material_type', '', 'info',    'N', NULL, NOW()),
  (1006019, '1001', 4, '其他',   'other',   'djs_material_type', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- H3 djs_task_status 任务状态（TaskCard / 派工通用）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100602, '1001', '任务状态', 'djs_task_status', NULL, NOW(), '跨域：派工/作业 5 状态');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006020, '1001', 0, '待处理',  'pending',     'djs_task_status', '', 'info',    'Y', NULL, NOW()),
  (1006021, '1001', 1, '进行中',  'in_progress', 'djs_task_status', '', 'primary', 'N', NULL, NOW()),
  (1006022, '1001', 2, '已完成',  'completed',   'djs_task_status', '', 'success', 'N', NULL, NOW()),
  (1006023, '1001', 3, '已取消',  'cancelled',   'djs_task_status', '', '',        'N', NULL, NOW()),
  (1006024, '1001', 4, '已退回',  'returned',    'djs_task_status', '', 'warning', 'N', NULL, NOW());

-- ------------------------------------------------------------
-- H4 djs_task_biz 任务业务类型（TaskCard 顶部 bizType 标签）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100603, '1001', '任务业务类型', 'djs_task_biz', NULL, NOW(), '跨域：派工业务类型 6 类');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006030, '1001', 0, '养殖', 'breed',     'djs_task_biz', '', 'primary', 'Y', NULL, NOW()),
  (1006031, '1001', 1, '种植', 'plant',     'djs_task_biz', '', 'success', 'N', NULL, NOW()),
  (1006032, '1001', 2, '仓库', 'warehouse', 'djs_task_biz', '', 'info',    'N', NULL, NOW()),
  (1006033, '1001', 3, '门店', 'store',     'djs_task_biz', '', 'warning', 'N', NULL, NOW()),
  (1006034, '1001', 4, '用药', 'med',       'djs_task_biz', '', 'danger',  'N', NULL, NOW()),
  (1006035, '1001', 5, '通用', 'general',   'djs_task_biz', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- H5 djs_return_loss_reason 退还/损耗原因（ReturnLossForm）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100604, '1001', '退还/损耗原因', 'djs_return_loss_reason', NULL, NOW(), '仓库：退货/损耗常见原因');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006040, '1001', 0, '货品损坏',    'damaged',         'djs_return_loss_reason', '', 'danger',  'Y', NULL, NOW()),
  (1006041, '1001', 1, '过期/失效',   'expired',         'djs_return_loss_reason', '', 'danger',  'N', NULL, NOW()),
  (1006042, '1001', 2, '规格错',     'wrong_spec',      'djs_return_loss_reason', '', 'warning', 'N', NULL, NOW()),
  (1006043, '1001', 3, '客户退还',    'customer_return', 'djs_return_loss_reason', '', 'info',    'N', NULL, NOW()),
  (1006044, '1001', 4, '盘亏',       'inventory_loss',  'djs_return_loss_reason', '', 'warning', 'N', NULL, NOW()),
  (1006045, '1001', 5, '其他',       'other',           'djs_return_loss_reason', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- H6 djs_crop 作物（PlotPickList / 田块 biz 组件，与 djs_crop_type 大类区分）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100605, '1001', '作物', 'djs_crop', NULL, NOW(), '种植：常见作物明细，djs_crop_type 是大类');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006050, '1001', 0, '水稻',  'rice',      'djs_crop', '', 'success', 'Y', NULL, NOW()),
  (1006051, '1001', 1, '小麦',  'wheat',     'djs_crop', '', 'warning', 'N', NULL, NOW()),
  (1006052, '1001', 2, '玉米',  'corn',      'djs_crop', '', 'warning', 'N', NULL, NOW()),
  (1006053, '1001', 3, '大豆',  'soybean',   'djs_crop', '', 'info',    'N', NULL, NOW()),
  (1006054, '1001', 4, '蔬菜',  'vegetable', 'djs_crop', '', 'success', 'N', NULL, NOW()),
  (1006055, '1001', 5, '水果',  'fruit',     'djs_crop', '', 'danger',  'N', NULL, NOW()),
  (1006056, '1001', 6, '其他',  'other',     'djs_crop', '', '',        'N', NULL, NOW());

-- ------------------------------------------------------------
-- H7 djs_dispatch_stage 派工阶段（DispatchEntry / 仓库 biz 组件）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100606, '1001', '派工阶段', 'djs_dispatch_stage', NULL, NOW(), '仓库/跨域：派工生命周期 5 阶段');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1006060, '1001', 0, '计划中',  'planning',   'djs_dispatch_stage', '', 'info',    'Y', NULL, NOW()),
  (1006061, '1001', 1, '已派工',  'dispatched', 'djs_dispatch_stage', '', 'primary', 'N', NULL, NOW()),
  (1006062, '1001', 2, '执行中',  'in_field',   'djs_dispatch_stage', '', 'primary', 'N', NULL, NOW()),
  (1006063, '1001', 3, '已完成',  'completed',  'djs_dispatch_stage', '', 'success', 'N', NULL, NOW()),
  (1006064, '1001', 4, '已回执',  'reported',   'djs_dispatch_stage', '', 'success', 'N', NULL, NOW());


-- ----------------------------------------------------------------------------
-- 来源文件：V202605221300__D04-CLOSING-D02-D03-leftover-fixes.sql
-- ----------------------------------------------------------------------------
-- D04 closing — D02/D03 历史问题 runtime patch
-- 由 D4 BRD-MD-002 raise（_open-issues "sys_farm seed 1001 farm_status=1=停用"）触发
-- 源 DDL `V202605200900__SYS-INIT-001-create-business-tables-common.sql` 已同步修：
--   - sys_farm.farm_status DEFAULT 1 → DEFAULT 0
--   - comment '1=启用 0=停用' → '0=启用 1=停用'（与字典 djs_farm_status 对齐）
--   - seed 1001 行 farm_status 1 → 0
-- 本文件是运行库 patch（已运行的 dev DB 需要 UPDATE backfill）

-- ----------------------------------------------------------------------
-- 1) sys_farm 1001 主场状态 1=停用 → 0=启用（与 djs_farm_status 字典对齐）
-- ----------------------------------------------------------------------
UPDATE sys_farm
SET farm_status = 0
WHERE id = 1001 AND farm_status = 1;

-- ----------------------------------------------------------------------
-- 2) sys_farm 表 DEFAULT 1 → DEFAULT 0（运行库 ALTER）
-- ----------------------------------------------------------------------
ALTER TABLE sys_farm
  MODIFY COLUMN farm_status TINYINT NOT NULL DEFAULT 0
  COMMENT '农场状态（字典 djs_farm_status：0=启用 1=停用）';

-- 验证
-- SELECT id, farm_code, farm_name, farm_status FROM sys_farm WHERE id = 1001;
-- 期望：farm_status = 0


-- ----------------------------------------------------------------------------
-- 来源文件：V202605221400__D04-CLOSING-seed-dev-users-and-depts.sql
-- ----------------------------------------------------------------------------
-- D04 closing — dev / 联调用户 + 部门 seed
-- 触发：D04 MIN-INFRA-003 raise（通讯录端点 SELECT sys_user 仅 admin 1 行，dev 联调全空，UI 验证无数据）
-- 决策：Kevin closing 拍板 — D04 当晚加 SQL seed（5-8 用户覆盖 4 板块角色 + 2-3 部门）
--
-- 覆盖：
--   - 4 部门：200 养殖部 / 201 种植部 / 202 仓库部 / 203 门店部（挂 XXX科技 100 下）+ 204 综合管理部
--   - 8 用户：每板块至少 1 个管理员 + 工人，含 boss / vet 角色
--   - 全部 tenant_id=1001 + farm_id=1001 + current_farm_id=1001（V1 单农场）
--   - 密码统一 admin123（BCrypt 同 admin user）
--
-- 注意：user_id 9100-9107 业务角色用户；mock LoginUser 9001 (user_name=dev) 由后续 patch
-- V202605222100__D04-TH06-seed-mock-dev-user.sql 单独 seed，对齐 AppletAuthController#mockUserId

-- ----------------------------------------------------------------------
-- 1) 部门 seed
-- ----------------------------------------------------------------------

INSERT INTO sys_dept (dept_id, parent_id, ancestors, dept_name, order_num, leader, phone, email, status, del_flag, create_by, create_time, tenant_id)
VALUES
  (200, 100, '0,100',        '东角山-养殖部',     1, NULL, NULL, NULL, '0', '0', 1, NOW(), '1001'),
  (201, 100, '0,100',        '东角山-种植部',     2, NULL, NULL, NULL, '0', '0', 1, NOW(), '1001'),
  (202, 100, '0,100',        '东角山-仓库部',     3, NULL, NULL, NULL, '0', '0', 1, NOW(), '1001'),
  (203, 100, '0,100',        '东角山-门店部',     4, NULL, NULL, NULL, '0', '0', 1, NOW(), '1001'),
  (204, 100, '0,100',        '东角山-综合管理部', 5, NULL, NULL, NULL, '0', '0', 1, NOW(), '1001')
ON DUPLICATE KEY UPDATE dept_name = VALUES(dept_name);

-- ----------------------------------------------------------------------
-- 2) 用户 seed（密码统一 admin123，BCrypt hash 同 admin user）
-- ----------------------------------------------------------------------

INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, password, status, del_flag, farm_id, current_farm_id, create_by, create_time, remark)
VALUES
  (9100, '1001', 200, 'dev_breed_mgr',     '李养殖（养殖管理员）', 'sys_user', 'breed_mgr@dongjiaoshan.dev',    '13800009100', '0', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 养殖管理员'),
  (9101, '1001', 200, 'dev_breed_worker',  '张三（养殖工人）',     'sys_user', 'breed_worker@dongjiaoshan.dev', '13800009101', '1', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 养殖工人'),
  (9102, '1001', 200, 'dev_vet',           '王兽医（兽医）',       'sys_user', 'vet@dongjiaoshan.dev',          '13800009102', '0', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 兽医'),
  (9103, '1001', 201, 'dev_plant_mgr',     '陈种植（种植管理员）', 'sys_user', 'plant_mgr@dongjiaoshan.dev',    '13800009103', '0', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 种植管理员'),
  (9104, '1001', 202, 'dev_warehouse_mgr', '赵仓库（仓库管理员）', 'sys_user', 'warehouse_mgr@dongjiaoshan.dev','13800009104', '0', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 仓库管理员'),
  (9105, '1001', 203, 'dev_store_mgr',     '钱门店（门店管理员）', 'sys_user', 'store_mgr@dongjiaoshan.dev',    '13800009105', '0', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 门店管理员'),
  (9106, '1001', 203, 'dev_store_clerk',   '孙小妹（门店店员）',   'sys_user', 'store_clerk@dongjiaoshan.dev',  '13800009106', '1', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 门店店员'),
  (9107, '1001', 204, 'dev_boss',          '老板（dev 测试）',     'sys_user', 'boss@dongjiaoshan.dev',         '13800009107', '0', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 老板')
ON DUPLICATE KEY UPDATE nick_name = VALUES(nick_name), dept_id = VALUES(dept_id);

-- ----------------------------------------------------------------------
-- 3) 用户 → 角色绑定
-- ----------------------------------------------------------------------

INSERT INTO sys_user_role (user_id, role_id)
VALUES
  (9100, 104),  -- breed_admin
  (9101, 108),  -- breed_worker
  (9102, 109),  -- vet
  (9103, 105),  -- plant_admin
  (9104, 106),  -- warehouse_admin
  (9105, 107),  -- store_admin
  (9106, 112),  -- store_clerk
  (9107, 102)   -- boss
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- ----------------------------------------------------------------------
-- 验证
-- ----------------------------------------------------------------------
-- SELECT COUNT(*) FROM sys_user WHERE user_id BETWEEN 9100 AND 9107 AND del_flag='0';
-- 期望：8
-- SELECT COUNT(*) FROM sys_dept WHERE dept_id BETWEEN 200 AND 204 AND del_flag='0';
-- 期望：5
-- SELECT COUNT(*) FROM sys_user_role WHERE user_id BETWEEN 9100 AND 9107;
-- 期望：8


-- ----------------------------------------------------------------------------
-- 来源文件：V202605222100__D04-TH06-seed-mock-dev-user.sql
-- ----------------------------------------------------------------------------
-- D04 testing-human #6 — 补 user_id=9001 dev 用户（mock LoginUser 对齐）
-- 触发：mock dev/dev123 login 颁发 mock-token-9001（mockUserId=9001L，[AppletAuthController.java:112]），
--       但 D04 closing seed 当时为"避开 mock LoginUser 9001"用了 9100-9107 段，DB 无 user_id=9001 行。
--       小程序"我的"页 GET /applet/user/me 走 SELECT sys_user WHERE user_id=9001 → 空 → R.fail(404)
--       → 前端"加载失败"。
-- 决策：补一行 user_id=9001 user_name=dev，与 mock LoginUser 对齐；保留 9100-9107 业务角色 seed 不动。

INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, password, status, del_flag, farm_id, current_farm_id, create_by, create_time, remark)
VALUES
  (9001, '1001', 204, 'dev', 'dev 员工', 'sys_user', 'dev@dongjiaoshan.dev', '13800009001', '0',
   '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm',
   '0', '0', '1001', '1001', 1, NOW(), 'D04 TH#6 - mock LoginUser 对齐（dev/dev123）')
ON DUPLICATE KEY UPDATE nick_name = VALUES(nick_name), dept_id = VALUES(dept_id), status = '0', del_flag = '0';

INSERT INTO sys_user_role (user_id, role_id)
VALUES
  (9001, 101)  -- admin 角色（mock LoginUser.rolePermission 默认 *:*:*，DB 给个 admin 角色对齐）
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 验证：
-- SELECT user_id, user_name, nick_name, dept_id FROM sys_user WHERE user_id = 9001;
-- 期望：1 行 dev / dev 员工 / 204


-- ----------------------------------------------------------------------------
-- 来源文件：V202605231400__SYS-MD-FIX-002-store-supplier.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- SYS-MD-FIX-002 门店 + 供应商 V1 验收 gap（D03 testing-human #4 hetao 反馈）
--
-- 改动总览：
--   1. t_md_store：重命名 contact_* → manager_*；新增 short_name/open_date/pos_system_id
--      /image_oss_id/manager_user_id；business_status TINYINT → VARCHAR(16) 走字典
--      djs_store_status
--   2. t_md_supplier：重命名 contact_* → liaison_*；新增 license_no/license_image_oss_id
--      /business_license_no/cooperation_start_date/deal_count/purchase_qty；business_status
--      TINYINT → VARCHAR(16) 走字典 djs_supplier_status；settle_type 走字典 djs_settle_type
--   3. 字典调整：djs_store_status label 改 / djs_store_type 新建 / djs_supplier_type label 改
--      + 数据 backfill / djs_supplier_status 新建 / djs_settle_type 新建
--
-- 跑完后必须 flush redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================
SET NAMES utf8mb4;

-- ===========================
-- 1. t_md_store 改造
-- ===========================
-- 1.1 字段重命名 contact_* → manager_*
ALTER TABLE t_md_store CHANGE COLUMN contact_name manager_name VARCHAR(32) NULL COMMENT '店长姓名';
ALTER TABLE t_md_store CHANGE COLUMN contact_phone manager_phone VARCHAR(20) NULL COMMENT '店长电话';

-- 1.2 business_status TINYINT(1=合作中/0=已终止) → VARCHAR(16) 走 djs_store_status 字典
--     旧值 1 → '0'（合作中），旧值 0 → '1'（已终止）
ALTER TABLE t_md_store ADD COLUMN business_status_new VARCHAR(16) NOT NULL DEFAULT '0' AFTER store_type;
UPDATE t_md_store SET business_status_new = CASE WHEN business_status=1 THEN '0' WHEN business_status=0 THEN '1' ELSE '0' END;
ALTER TABLE t_md_store DROP COLUMN business_status;
ALTER TABLE t_md_store CHANGE COLUMN business_status_new business_status VARCHAR(16) NOT NULL DEFAULT '0' COMMENT '合作状态（字典 djs_store_status: 0=合作中/1=已终止/2=装修中）';

-- 1.3 新加字段
ALTER TABLE t_md_store ADD COLUMN short_name VARCHAR(64) NULL COMMENT '门店简称' AFTER store_name;
ALTER TABLE t_md_store ADD COLUMN open_date DATE NULL COMMENT '开业日期' AFTER short_name;
ALTER TABLE t_md_store ADD COLUMN pos_system_id VARCHAR(64) NULL COMMENT '收银系统 ID' AFTER manager_phone;
ALTER TABLE t_md_store ADD COLUMN image_oss_id BIGINT NULL COMMENT '门店图片（引用 sys_oss.oss_id）' AFTER pos_system_id;
ALTER TABLE t_md_store ADD COLUMN manager_user_id BIGINT NULL COMMENT '店长 sys_user.user_id（NULL=未设置）' AFTER manager_phone;
ALTER TABLE t_md_store ADD INDEX idx_store_manager (manager_user_id);

-- ===========================
-- 2. t_md_supplier 改造
-- ===========================
-- 2.1 字段重命名 contact_* → liaison_*
ALTER TABLE t_md_supplier CHANGE COLUMN contact_name liaison_name VARCHAR(32) NULL COMMENT '联系负责人';
ALTER TABLE t_md_supplier CHANGE COLUMN contact_phone liaison_phone VARCHAR(20) NULL COMMENT '负责人电话';

-- 2.2 业务数据 backfill：pack（包材）映射 → other（其他），再删 pack 字典
UPDATE t_md_supplier SET supplier_type='other' WHERE supplier_type='pack';

-- 2.3 business_status TINYINT → VARCHAR(16) 走 djs_supplier_status 字典
--     旧值 1 → '0'（合作中），旧值 0 → '1'（已终止）
ALTER TABLE t_md_supplier ADD COLUMN business_status_new VARCHAR(16) NOT NULL DEFAULT '0' AFTER address;
UPDATE t_md_supplier SET business_status_new = CASE WHEN business_status=1 THEN '0' WHEN business_status=0 THEN '1' ELSE '0' END;
ALTER TABLE t_md_supplier DROP COLUMN business_status;
ALTER TABLE t_md_supplier CHANGE COLUMN business_status_new business_status VARCHAR(16) NOT NULL DEFAULT '0' COMMENT '合作状态（字典 djs_supplier_status: 0=合作中/1=已终止）';

-- 2.4 settle_type 自由文本 → VARCHAR(16) 走 djs_settle_type 字典
--     旧数据统一 backfill 为 cash（V1 默认现款现货）；已是 cash 重复 UPDATE 也安全
UPDATE t_md_supplier SET settle_type = 'cash' WHERE settle_type IS NOT NULL AND settle_type NOT IN ('cash','monthly','quarterly');
UPDATE t_md_supplier SET settle_type = 'cash' WHERE settle_type IS NULL OR settle_type = '';
ALTER TABLE t_md_supplier MODIFY COLUMN settle_type VARCHAR(16) NULL DEFAULT 'cash' COMMENT '结算方式（字典 djs_settle_type: cash=现款现货/monthly=月结/quarterly=季结）';

-- 2.5 新加字段
ALTER TABLE t_md_supplier ADD COLUMN license_no VARCHAR(64) NULL COMMENT '营业执照编号' AFTER supplier_name;
ALTER TABLE t_md_supplier ADD COLUMN license_image_oss_id BIGINT NULL COMMENT '营业执照图片（sys_oss.oss_id）' AFTER license_no;
ALTER TABLE t_md_supplier ADD COLUMN business_license_no VARCHAR(64) NULL COMMENT '经营许可证编号' AFTER license_image_oss_id;
ALTER TABLE t_md_supplier ADD COLUMN cooperation_start_date DATE NULL COMMENT '合作开始日期' AFTER business_license_no;
ALTER TABLE t_md_supplier ADD COLUMN deal_count INT NOT NULL DEFAULT 0 COMMENT '交易次数（聚合冗余 / V1 stub=0，下游 BRD-MED / WMS-PURCHASE 落地后回填）';
ALTER TABLE t_md_supplier ADD COLUMN purchase_qty DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '累计购入商品数（V1 stub=0）';

-- ===========================
-- 3. 字典调整
-- ===========================
-- 3.1 djs_store_status label 改语义（0=启用→合作中 / 1=停用→已终止）；装修中 (value=2) 不变
UPDATE sys_dict_data SET dict_label='合作中', list_class='success' WHERE dict_type='djs_store_status' AND dict_value='0';
UPDATE sys_dict_data SET dict_label='已终止', list_class='danger'  WHERE dict_type='djs_store_status' AND dict_value='1';

-- 3.2 djs_store_type 新建（V1 直营 / 加盟）
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100700, '1001', '门店类型', 'djs_store_type', NULL, NOW(), '门店：直营 / 加盟');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1007000, '1001', 0, '直营', 'direct',    'djs_store_type', '', 'primary', 'Y', NULL, NOW()),
  (1007001, '1001', 1, '加盟', 'franchise', 'djs_store_type', '', 'success', 'N', NULL, NOW());

-- 3.3 djs_supplier_type label 改：饲料 → 饲料原材料 / 兽药 → 药品 / 蔬菜种子 → 种子
UPDATE sys_dict_data SET dict_label='药品'         WHERE dict_type='djs_supplier_type' AND dict_value='med';
UPDATE sys_dict_data SET dict_label='种猪'         WHERE dict_type='djs_supplier_type' AND dict_value='breed';
UPDATE sys_dict_data SET dict_label='饲料原材料'   WHERE dict_type='djs_supplier_type' AND dict_value='feed';
UPDATE sys_dict_data SET dict_label='种子'         WHERE dict_type='djs_supplier_type' AND dict_value='seed';
-- 删除 pack（V1 不要；t_md_supplier 中 pack 数据已在 §2.2 UPDATE 到 other）；保留 other 作兜底
DELETE FROM sys_dict_data WHERE dict_type='djs_supplier_type' AND dict_value='pack';

-- 3.4 djs_supplier_status 新建
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100701, '1001', '供应商合作状态', 'djs_supplier_status', NULL, NOW(), '供应商：合作中 / 已终止');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1007010, '1001', 0, '合作中', '0', 'djs_supplier_status', '', 'success', 'Y', NULL, NOW()),
  (1007011, '1001', 1, '已终止', '1', 'djs_supplier_status', '', 'danger',  'N', NULL, NOW());

-- 3.5 djs_settle_type 新建
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100702, '1001', '结算方式', 'djs_settle_type', NULL, NOW(), '供应商：现款现货 / 月结 / 季结');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1007020, '1001', 0, '现款现货', 'cash',      'djs_settle_type', '', 'primary', 'Y', NULL, NOW()),
  (1007021, '1001', 1, '月结',     'monthly',   'djs_settle_type', '', 'warning', 'N', NULL, NOW()),
  (1007022, '1001', 2, '季结',     'quarterly', 'djs_settle_type', '', 'info',    'N', NULL, NOW());

-- ===========================
-- 4. 菜单按钮权限（SYS-MD-FIX-002 新增"设置店长"按钮）
--    SYS-MD-002 已 seed menu_id 5002 (门店目录) + 5020-5024 (list/add/edit/remove/export)；
--    本 ticket 在 5002 下新增 menu_id 5025 设置店长
-- ===========================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (5025, '设置店长', 5002, 6, '', '', '',
   1, 0, 'F', '0', '0',
   'djs:common:store:setManager', '#', 1, NOW(), 'SYS-MD-FIX-002');


-- ----------------------------------------------------------------------------
-- 来源文件：V202605242200__BRD-CORE-001-add-status-record-update-cols.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- BRD-CORE-001 t_farm_status_record 补 update_by / update_time 列
--
-- 触发：testing-human B.1 mp 引种提交时 MP InjectionMetaObjectHandler
-- 全局 insertFill 注入 updateBy/updateTime，但本表 DDL 缺这两列 →
-- SQLSyntaxErrorException: Unknown column 'update_by' in 'field list'
--
-- 其他 12 张业务表都已有这两列；status_record 当初按"事件流不可编辑"
-- 语义省略，但 MP 框架行为是全局 insertFill 不分表，省不掉。
--
-- SYS-INIT-001 V202605200901 源文件同步更新，新环境从零跑也正确。
-- ============================================================
ALTER TABLE t_farm_status_record
  ADD COLUMN update_by   BIGINT   NULL COMMENT '更新人（MP insertFill 占位，状态记录实际不 update）' AFTER create_time,
  ADD COLUMN update_time DATETIME NULL COMMENT '更新时间（MP insertFill 占位）' AFTER update_by;


-- ----------------------------------------------------------------------------
-- 来源文件：V202605242300__BRD-EVENT-001-003-admin-readonly-list-menu.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- BRD-EVENT-001/003 admin 端补只读列表页菜单
--
-- testing-human B 阶段：admin 左侧「引种登记」/「仔猪耳标」点击空白页。
-- doc/02 scope = be+fe-app（只 mp 录），admin 端补只读历史查询页（不录入）。
--
-- 1) menu 7110「仔猪耳标」从 M（目录）改 C（页面）+ 加 path/component/perms
-- 2) breed_admin / breed_worker 加 7110 菜单访问
-- 3) intro/eartag :list 权限赋给 breed_admin / breed_worker（boss/manager 已有）
-- ============================================================

UPDATE sys_menu
SET menu_type = 'C',
    path = 'eartag',
    component = 'djs-breed/event/eartag/index',
    perms = 'djs:breed:event:eartag:list'
WHERE menu_id = 7110;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7110 FROM sys_role r
WHERE r.role_key IN ('breed_admin', 'breed_worker');


-- ----------------------------------------------------------------------------
-- 来源文件：V202605260900__BRD-CORE-001-menu.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- BRD-CORE-001 猪只主表 + 状态机引擎（菜单 + 按钮权限）
--   父菜单 7000 (养殖) 已在 SYS-AUTH-001 V202605201100 占位
--   本文件分配 7200-7299 段（参 .claude/CLAUDE.md §6 第 6 条段表）
--     7200 二级目录：猪只主表（admin 列表 + 详情）
--     7201-7204 三级按钮：查询 / 详情 / 历史 / 触发事件
--   权限串 djs:breed:pig:* 与后端 PigController @SaCheckPermission 严格一致
--   注：djs:breed:pig:event 是通用事件入口，仅赋系统级角色（boss / 调试），
--   不下放普通用户；BRD-EVENT-* 子 ticket 自己写业务端点 + 内部调 fireEvent
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 菜单：猪只主表（挂养殖目录 7000 下，order_num=50 排在育种 / 农场 / 药品 / 公猪 / 用药计划之后）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 二级目录：猪只主表（admin 列表 + 详情 + 历史）
  (7200, '猪只主表', 7000, 50, 'pig', 'djs-breed/pig/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:pig:list', 'monitor', 1, NOW(), 'BRD-CORE-001'),

  -- 三级按钮权限（4 个，与后端 PigController @SaCheckPermission 严格一致）
  (7201, '猪只查询', 7200, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pig:list',  '#', 1, NOW(), 'BRD-CORE-001'),
  (7202, '猪只详情', 7200, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pig:query', '#', 1, NOW(), 'BRD-CORE-001'),
  (7203, '触发事件', 7200, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pig:event', '#', 1, NOW(), 'BRD-CORE-001'),
  (7204, '猪只导出', 7200, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:pig:export', '#', 1, NOW(), 'BRD-CORE-001');

-- ------------------------------------------------------------
-- 角色 → 菜单映射
--   boss / manager / breed_admin / breed_worker 全部赋猪只主表 list + query
--   djs:breed:pig:event 仅赋 boss（系统级 / 调试），manager / 业务角色不下放
--   djs:breed:pig:export 赋 boss + manager
-- ------------------------------------------------------------
-- list (7201) + query (7202)：所有养殖相关角色
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 7200 AS menu_id UNION SELECT 7201 UNION SELECT 7202) m
WHERE r.role_key IN ('boss', 'manager', 'breed_admin', 'breed_worker');

-- event (7203)：仅 boss（系统级）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7203
FROM sys_role r
WHERE r.role_key IN ('boss');

-- export (7204)：boss + manager
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7204
FROM sys_role r
WHERE r.role_key IN ('boss', 'manager');


-- ----------------------------------------------------------------------------
-- 来源文件：V202605260901__BRD-CORE-001-realign-status-record-comment.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- BRD-CORE-001 修 t_farm_status_record.event_type 列注释词汇漂移（无破坏性）
--   SYS-INIT-001 V202605200901 建表时注释写的是 MATING/CONFIRM_PREG/.../DEAD/CULL/MARKET (10 个旧词汇)
--   SYS-INIT-002 V202605201000 灌字典 djs_pig_status_event 用的是 INTRO/BREED/FARROW/.../SLAUGHTER (11 个)
--   应用层（PigStatusEvent enum / PigStateMachine）以字典 11 个 value 为准
--   本 SQL 只改 COLUMN COMMENT 元数据，不动数据 / 不动类型 / 不动索引
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE t_farm_status_record MODIFY COLUMN event_type VARCHAR(16) NOT NULL
  COMMENT '触发事件（11 枚举，字典 djs_pig_status_event）：INTRO/BREED/FARROW/WEAN/OESTRUS/NULL_RETURN/DIE/ELIMINATE/CASTRATE/TRANSFER/SLAUGHTER';

-- 顺带修 t_farm_pig_info.current_status 注释（原写"9 枚举"，应为 10：含 BOAR_ACTIVE 公猪在产）
ALTER TABLE t_farm_pig_info MODIFY COLUMN current_status VARCHAR(16) NOT NULL DEFAULT 'HB'
  COMMENT '当前状态（10 枚举，字典 djs_pig_lifecycle）：HB/PZ/PH/FM/DN/LC/KH/FQ/END/BOAR_ACTIVE；BRD-CORE-001 状态机维护';


-- ----------------------------------------------------------------------------
-- 来源文件：V202605270900__BRD-EVENT-001-intro-no-rule.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- BRD-EVENT-001 引种单号编码规则（INTRO_NO）
--   pattern: INT{yyyyMMdd}{seq4}     例 INT202605260001（每日重置 4 位序号）
-- ============================================================

INSERT IGNORE INTO t_md_biz_code_rule
  (code_type, pattern,                  daily_reset, prefix, seq_length, status, create_by, create_time)
VALUES
  ('INTRO_NO', 'INT{yyyyMMdd}{seq4}',   1,           'INT',  4,          '0',    1,         NOW());


-- ----------------------------------------------------------------------------
-- 来源文件：V202605270901__BRD-EVENT-001-menu.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- BRD-EVENT-001 引种登记（菜单 + 按钮权限）
--   父菜单 7000 (养殖) 已在 SYS-AUTH-001 占位
--   本文件分配 7100-7109 段（CLAUDE.md §6 段表：7100-7199 = BRD-EVENT-001~005 业务事件）
--     7100 二级目录：引种登记（admin 查看历史 + 业务）
--     7101-7105 三级按钮
--   权限串 djs:breed:event:intro* 与后端 PigIntroController @SaCheckPermission 严格一致
--   注：引种主入口在 mp 端（CameraUpload 拍照 + 表单），admin 端只做列表查看 / 导出
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 二级目录：引种登记
  (7100, '引种登记', 7000, 10, 'intro', 'djs-breed/event/intro/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:event:intro:list', 'log', 1, NOW(), 'BRD-EVENT-001'),

  -- 三级按钮（与 controller @SaCheckPermission 一一对应）
  (7101, '引种查询', 7100, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:intro:list',   '#', 1, NOW(), 'BRD-EVENT-001'),
  (7102, '引种详情', 7100, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:intro:query',  '#', 1, NOW(), 'BRD-EVENT-001'),
  (7103, '引种新增', 7100, 3, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:intro',        '#', 1, NOW(), 'BRD-EVENT-001'),
  (7104, '引种导出', 7100, 4, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:intro:export', '#', 1, NOW(), 'BRD-EVENT-001');

-- ------------------------------------------------------------
-- 角色 → 菜单映射（对齐当前项目角色：boss / manager / breed_admin / breed_worker / vet）
--   list / query (7100, 7101, 7102): boss / manager / breed_admin / breed_worker（养殖员录入端会查自己引的猪）
--   intro 写权限 (7103): boss / manager / breed_admin / breed_worker
--   export (7104): boss / manager / breed_admin
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 7100 AS menu_id UNION SELECT 7101 UNION SELECT 7102 UNION SELECT 7103) m
WHERE r.role_key IN ('boss', 'manager', 'breed_admin', 'breed_worker');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7104
FROM sys_role r
WHERE r.role_key IN ('boss', 'manager', 'breed_admin');


-- ----------------------------------------------------------------------------
-- 来源文件：V202605270902__BRD-EVENT-003-menu.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- BRD-EVENT-003 仔猪批量耳标（菜单 + 按钮权限）
--   父菜单 7000 (养殖) 在 SYS-AUTH-001 占位；本文件分配 7110-7119 段
--   参 .claude/CLAUDE.md §6 第 6 条：7100-7199 留给 BRD-EVENT-001~005 业务事件
--     7110          二级目录：仔猪耳标（小程序入口 + 后续 admin 列表）
--     7111-7113     三级按钮：查询 / 批量贴标
--   权限串 djs:breed:event:eartag(:query) 与 PigEarTagController @SaCheckPermission 一致
--   注：本菜单主要服务小程序写入；admin 端 V1 不提供独立列表页（详情通过 BRD-LIST-001 接入）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 菜单：仔猪耳标（挂养殖目录 7000 下，order_num=60 排在猪只主表之后）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  -- 二级目录：仔猪耳标
  (7110, '仔猪耳标', 7000, 60, 'eartag', '', '',
   1, 0, 'M', '0', '0',
   '', 'edit', 1, NOW(), 'BRD-EVENT-003'),

  -- 三级按钮权限
  (7111, '耳标查询', 7110, 1, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:eartag:query', '#', 1, NOW(), 'BRD-EVENT-003'),
  (7112, '批量贴标', 7110, 2, '', '', '', 1, 0, 'F', '0', '0',
   'djs:breed:event:eartag', '#', 1, NOW(), 'BRD-EVENT-003');

-- ------------------------------------------------------------
-- 角色 → 菜单映射
--   query (7111)：所有养殖相关角色
--   eartag write (7112)：boss / manager / pig_keeper（执行人）；butcher 不需要
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 7110 AS menu_id UNION SELECT 7111) m
WHERE r.role_key IN ('boss', 'manager', 'pig_keeper', 'butcher');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7112
FROM sys_role r
WHERE r.role_key IN ('boss', 'manager', 'pig_keeper');


-- ----------------------------------------------------------------------------
-- 来源文件：V202605280800__D05-CLOSING-fixes.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- D05 Closing 修复补丁
-- 修复 D5 实施中发现的 5 个数据问题（_open-issues.md 待决项）
--
--   1. menu_id 7103 名称重复（"引种登记" → "引种新增"）
--   2. BRD-CORE-001 菜单 7200-7202 补赋 breed_admin / breed_worker
--   3. djs_pig_gender dict_value 对齐 DB（male/female → M/F）
--   4. 补种 djs_pig_type 字典（4 值）
--   5. 补种 djs_pig_end_reason 字典（3 值）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- Fix 1: menu_id 7103 名称去重
--   7100 = 引种登记（二级目录）
--   7103 = 引种新增（三级按钮，perms djs:breed:intro:add）
-- ------------------------------------------------------------
UPDATE sys_menu SET menu_name = '引种新增' WHERE menu_id = 7103;

-- ------------------------------------------------------------
-- Fix 2: BRD-CORE-001 猪只主表菜单补赋 breed_admin / breed_worker
--   V202605260900 源文件引用了不存在的 pig_keeper / butcher
--   运行库只赋了 boss + manager，breed_worker 看不到猪只主表菜单
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 7200 AS menu_id UNION SELECT 7201 UNION SELECT 7202) m
WHERE r.role_key IN ('breed_admin', 'breed_worker');

-- ------------------------------------------------------------
-- Fix 3: djs_pig_gender dict_value 对齐 DB
--   t_farm_pig_info.pig_sex CHAR(1) 存 'F'/'M'
--   原字典值 male/female 与 DB 不一致导致 dict-tag 显示为空
-- ------------------------------------------------------------
UPDATE sys_dict_data
SET dict_value = 'M'
WHERE dict_type = 'djs_pig_gender' AND dict_label = '公';

UPDATE sys_dict_data
SET dict_value = 'F'
WHERE dict_type = 'djs_pig_gender' AND dict_label = '母';

-- ------------------------------------------------------------
-- Fix 4: 补种 djs_pig_type 字典（猪只类型）
--   t_farm_pig_info.pig_type 引用此字典
--   dict_id = 100109，数据 ID 从 1001100 起
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100109, '1001', '猪只类型', 'djs_pig_type', NULL, NOW(), '养殖：t_farm_pig_info.pig_type');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001100, '1001', 0, '母猪',   'sow',       'djs_pig_type', '', 'success', 'Y', NULL, NOW()),
  (1001101, '1001', 1, '公猪',   'boar',      'djs_pig_type', '', 'primary', 'N', NULL, NOW()),
  (1001102, '1001', 2, '仔猪',   'piglet',    'djs_pig_type', '', 'info',    'N', NULL, NOW()),
  (1001103, '1001', 3, '育肥猪', 'fattening', 'djs_pig_type', '', 'warning', 'N', NULL, NOW());

-- ------------------------------------------------------------
-- Fix 5: 补种 djs_pig_end_reason 字典（终止原因）
--   t_farm_pig_info.end_reason 引用此字典
--   dict_id = 100110，数据 ID 从 1001110 起
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100110, '1001', '终止原因', 'djs_pig_end_reason', NULL, NOW(), '养殖：t_farm_pig_info.end_reason（DIE→DEAD / ELIMINATE→CULL / SLAUGHTER→MARKET）');

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1001110, '1001', 0, '死亡', 'DEAD',   'djs_pig_end_reason', '', 'danger',  'N', NULL, NOW()),
  (1001111, '1001', 1, '淘汰', 'CULL',   'djs_pig_end_reason', '', 'warning', 'N', NULL, NOW()),
  (1001112, '1001', 2, '出栏', 'MARKET', 'djs_pig_end_reason', '', 'info',    'N', NULL, NOW());


-- ----------------------------------------------------------------------------
-- 来源文件：V202605281000__SYS-FIX-002-drop-person-postId.sql
-- ----------------------------------------------------------------------------
SET NAMES utf8mb4;
-- Removes post_id (sys_post foreign key) from t_md_person.
-- post_id was removed from the Person domain; column should not exist in fresh installs.
-- For environments where it was added by an earlier migration, drop it here.
SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_md_person'
          AND COLUMN_NAME = 'post_id'
    ),
    'ALTER TABLE t_md_person DROP COLUMN post_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ----------------------------------------------------------------------------
-- 来源文件：V202605281100__D05-HOTFIX-breeding-split-4-menus.sql
-- ----------------------------------------------------------------------------
-- ============================================================
-- D05-HOTFIX BRD-MD-001 育种配置 — 顶层菜单按 4 个独立子菜单拆开
--
-- 拆分前：7010 是 C 菜单（单页 4 tab：品种 / 品种配种 / 品系 / 品系配种）
-- 拆分后：7010 改 M 目录，下挂 4 个 C 菜单：
--   7016 品种管理      breed-strain      djs-breed/breeding-config/strain
--   7017 品系管理      breed-line        djs-breed/breeding-config/line
--   7018 品种配种      breed-mate-strain djs-breed/breeding-config/mate-strain
--   7019 品系配种      breed-mate-line   djs-breed/breeding-config/mate-line
--
-- 7011-7015 按钮权限（list/add/edit/remove/export）保留 parent_id=7010 不动 —
-- v-hasPermi 看 perms 串 djs:breed:breeding:{action}，不依赖按钮归属哪个 C 菜单。
-- ============================================================

SET NAMES utf8mb4;

-- 1) 7010 由 C 菜单升级为 M 目录
UPDATE sys_menu
   SET menu_type = 'M',
       component = '',
       perms     = '',
       icon      = 'tree'
 WHERE menu_id = 7010;

-- 2) 新增 4 个 C 菜单（独立路由 + 独立组件，共用一组 perms）
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7016, '品种管理', 7010, 1, 'breed-strain',      'djs-breed/breeding-config/strain',      '',
   1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'D05-HOTFIX BRD-MD-001 split-4-menus'),

  (7017, '品系管理', 7010, 2, 'breed-line',        'djs-breed/breeding-config/line',        '',
   1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'D05-HOTFIX BRD-MD-001 split-4-menus'),

  (7018, '品种配种', 7010, 3, 'breed-mate-strain', 'djs-breed/breeding-config/mate-strain', '',
   1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'D05-HOTFIX BRD-MD-001 split-4-menus'),

  (7019, '品系配种', 7010, 4, 'breed-mate-line',   'djs-breed/breeding-config/mate-line',   '',
   1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'D05-HOTFIX BRD-MD-001 split-4-menus');

