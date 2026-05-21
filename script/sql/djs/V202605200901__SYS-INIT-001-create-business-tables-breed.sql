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
  current_status      VARCHAR(16)  NOT NULL DEFAULT 'HB' COMMENT '当前状态（9 枚举）：HB/PZ/PH/FM/DN/LC/KH/FQ/END；公猪固定 BOAR_ACTIVE',
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
  event_type      VARCHAR(16)  NOT NULL COMMENT '触发事件（10 枚举）：MATING/CONFIRM_PREG/FARROW/WEANING/ABORT/RETURN/IDLE/DEAD/CULL/MARKET',
  related_event_id BIGINT      NULL COMMENT '关联业务事件 ID（如 breeding_id/farrow_id）',
  duration_days   INT          NULL COMMENT '在原状态停留天数（业务层计算）',
  change_time     DATETIME     NOT NULL COMMENT '状态变更时间',
  create_dept       BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '操作人',
  create_time     DATETIME     NULL COMMENT '记录创建时间',
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
  is_pregnant_confirmed TINYINT(1) NULL DEFAULT 0 COMMENT '是否确认妊娠（勾选触发 CONFIRM_PREG 状态机事件）',
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
