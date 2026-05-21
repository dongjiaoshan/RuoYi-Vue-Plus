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
