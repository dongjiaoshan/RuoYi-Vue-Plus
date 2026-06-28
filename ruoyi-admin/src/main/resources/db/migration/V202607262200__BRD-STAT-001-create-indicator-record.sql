-- BRD-STAT-001 — 养殖农场日数据记录表 t_farm_indicator_record（邓博 admin 测试表 row10）。
--
-- 用途：每晚定时任务落盘 T-1 全量养殖日指标（30+ 列），mp「种猪场活动统计」读历史 + 落盘数据。
--   今日实时聚合仍走 DashboardServiceImpl.getDailyOverview 等实时端点（保持不动），两套并存：
--   今日看实时、历史看本表。每 tenant_id + stat_date 一行（UPSERT 幂等）。
--
-- 字段全部对齐 row10 邓博逐字指标。头数类 INT，重量/天数/率类 DECIMAL(12,3)。
-- 业务表契约：tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' + 6 公共字段 + del_flag/del_unique，
--   UNIQUE(tenant_id, stat_date, del_unique)。mp 读、无 admin 菜单（不占 menu_id）。
-- V202607262200 > 当前 flyway max V202607262100。

SET NAMES utf8mb4;

DROP TABLE IF EXISTS t_farm_indicator_record;
CREATE TABLE t_farm_indicator_record (
  id                       BIGINT        NOT NULL AUTO_INCREMENT,
  tenant_id                VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  stat_date                DATE          NOT NULL COMMENT '统计日期（T-1）',

  -- 当日事件类（头数）
  farrow_sow_count         INT           NULL DEFAULT 0 COMMENT '分娩母猪数（当日分娩记录的母猪数）',
  breeding_sow_count       INT           NULL DEFAULT 0 COMMENT '配种母猪数（当日配种记录的母猪数）',
  weaning_sow_count        INT           NULL DEFAULT 0 COMMENT '断奶母猪数（当日断奶记录的母猪数）',
  abnormal_sow_count       INT           NULL DEFAULT 0 COMMENT '返空流母猪数（当日返空流记录数）',
  introduce_sow_count      INT           NULL DEFAULT 0 COMMENT '引种母猪数（当日内外部引种头数）',
  heat_no_breed_count      INT           NULL DEFAULT 0 COMMENT '查情不配种数（当日查情但 3 日内未配种的母猪数）',
  death_pig_count          INT           NULL DEFAULT 0 COMMENT '死亡猪只数（当日 DIE 事件数）',
  culling_pig_count        INT           NULL DEFAULT 0 COMMENT '淘汰猪只数（当日 ELIMINATE 事件数）',
  total_born_count         INT           NULL DEFAULT 0 COMMENT '产仔数（当日总产仔数 SUM total_born）',
  live_born_count          INT           NULL DEFAULT 0 COMMENT '活仔数（当日总活仔数 SUM live_born）',
  piglet_tag_count         INT           NULL DEFAULT 0 COMMENT '仔猪打标数（当日打标记录数）',
  weaned_piglet_count      INT           NULL DEFAULT 0 COMMENT '断奶仔猪数（当日断奶头数 SUM weaned_count）',
  growth_record_count      INT           NULL DEFAULT 0 COMMENT '生长记录数（当日生长记录录入数）',
  castrate_pig_count       INT           NULL DEFAULT 0 COMMENT '阉割猪只数（当日 CASTRATE 事件数）',
  medicated_pig_count      INT           NULL DEFAULT 0 COMMENT '用药猪只数（当日有用药记录的猪只数 DISTINCT pig_id）',
  marketing_pig_count      INT           NULL DEFAULT 0 COMMENT '出栏猪只数量（当日出栏头数）',
  death_fattening_count    INT           NULL DEFAULT 0 COMMENT '死亡肥猪数（当日 DIE 且 pig_type=fattening）',
  death_sow_count          INT           NULL DEFAULT 0 COMMENT '死亡种母猪数（当日 DIE 且 pig_type=sow）',
  death_piglet_count       INT           NULL DEFAULT 0 COMMENT '死亡仔猪数（当日 DIE 且 pig_type=piglet）',

  -- 当日出栏/生长重量类
  avg_marketing_weight     DECIMAL(12,3) NULL DEFAULT 0 COMMENT '平均出栏重（出栏总重/出栏头数）',
  marketing_weight         DECIMAL(12,3) NULL DEFAULT 0 COMMENT '出栏总重（当日出栏猪只总重）',
  wean_total_weight        DECIMAL(12,3) NULL DEFAULT 0 COMMENT '猪只断奶总重（当日出栏猪只断奶时总重）',
  growth_total_days        INT           NULL DEFAULT 0 COMMENT '生长总天数（Σ 当日出栏猪 出栏日−断奶日）',
  net_gain_weight          DECIMAL(12,3) NULL DEFAULT 0 COMMENT '净增重（出栏总重−断奶总重）',
  daily_gain_weight        DECIMAL(12,3) NULL DEFAULT 0 COMMENT '日增重（净增重/生长总天数）',
  avg_backfat_thickness    DECIMAL(12,3) NULL DEFAULT 0 COMMENT '平均背膘厚（当日出栏猪背膘厚之和/有背膘的肥猪数）',

  -- 期末存栏快照类（T-1 当日 current_status 快照）
  end_production_sow_count INT           NULL DEFAULT 0 COMMENT '期末生产母猪头数（期末非后备非终止种母猪存栏）',
  end_boar_count           INT           NULL DEFAULT 0 COMMENT '期末种公猪数',
  end_fattening_count      INT           NULL DEFAULT 0 COMMENT '期末肥猪头数',
  end_piglet_count         INT           NULL DEFAULT 0 COMMENT '期末仔猪头数',
  end_reserve_count        INT           NULL DEFAULT 0 COMMENT '期末后备猪头数（种母猪 HB）',
  end_reserve_230_count    INT           NULL DEFAULT 0 COMMENT '期末 230 日龄以上后备猪头数',
  end_nonprod_sow_count    INT           NULL DEFAULT 0 COMMENT '期末非生产状态母猪头数（空怀/返情/流产/断奶）',

  -- 配种批次回溯
  year_batch_farrow_count  INT           NULL DEFAULT 0 COMMENT '当年配种批次分娩头数（分娩日−114 天落当年则累加）',

  create_dept              BIGINT        NULL COMMENT '创建部门',
  create_by                BIGINT        NULL COMMENT '创建者',
  create_time              DATETIME      NULL COMMENT '创建时间',
  update_by                BIGINT        NULL COMMENT '更新者',
  update_time              DATETIME      NULL COMMENT '更新时间',
  remark                   VARCHAR(500)  NULL COMMENT '备注',
  del_flag                 CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志',
  del_unique               BIGINT        NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
  PRIMARY KEY (id),
  UNIQUE KEY uk_indicator_tenant_date (tenant_id, stat_date, del_unique),
  KEY idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='养殖农场日数据记录（BRD-STAT-001，邓博 row10，定时落盘 T-1）';
