-- WMS-STAT-001 — 仓库统计指标 3 张落盘表（邓博 admin 测试表 row16/17/18）。
--
-- 用途：每晚定时任务（WarehouseStatJob，错峰 0:45）经 DjsJobRunner 落盘 T-1 仓库统计指标，
--   月表月内每日刷新当月、历史月固定。admin 3 个只读列表页读本表（日期/月份范围查询）。
--
-- 三张表（维度递进）：
--   t_warehouse_indicator_record  仓库日表（~25 指标，stat_date 维度）
--   t_warehouse_cropp_record      作物日表（stat_date + crop_id 维度，一作物一行）
--   t_warehouse_monthly_record    仓库月表（stat_month 维度，汇总当月日表）
--
-- 字段全部对齐 row16/17/18 邓博逐字指标 + 7 个已拍口径。
--   重量/率/天 DECIMAL(12,3)，头数 INT。率类分母≤0 时落 NULL（无意义不造假，可空列走
--   @TableField(updateStrategy=ALWAYS) 让重算的 NULL 真覆盖旧值，防脏值残留）。
-- 业务表契约：tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' + 6 公共字段（含 create_dept）
--   + del_flag/del_unique；id AUTO_INCREMENT（与 t_farm_indicator_record 同款）。
-- V202607262400 > 当前 flyway max V202607262300。

SET NAMES utf8mb4;

-- ============================================================
-- 1. 仓库日表 t_warehouse_indicator_record（row16）
-- ============================================================
DROP TABLE IF EXISTS t_warehouse_indicator_record;
CREATE TABLE t_warehouse_indicator_record (
  id                          BIGINT        NOT NULL AUTO_INCREMENT,
  tenant_id                   VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  stat_date                   DATE          NOT NULL COMMENT '统计日期（T-1）',

  -- 屠宰 / 送宰段
  slaughter_count             INT           NULL DEFAULT 0     COMMENT '屠宰头数（当日燎毛间接收的猪只头数 = 当日 burn 记录数）',
  slaughter_weight            DECIMAL(12,3) NULL DEFAULT 0     COMMENT '送宰总重（当日出栏送宰猪总重 bar.marketing_weight + 当日外购猪总重 outsource.pig_weight）',
  avg_slaughter_weight        DECIMAL(12,3) NULL              COMMENT '送宰均重（送宰总重/屠宰头数；分母 0 → NULL）',
  arrive_weight               DECIMAL(12,3) NULL DEFAULT 0     COMMENT '接收重量（当日燎毛间称重总重 = Σ burn.arrive_weight）',
  slaughter_rate              DECIMAL(12,3) NULL              COMMENT '屠宰率%（接收重量/送宰总重×100；分母 0 → NULL）',

  -- 白条段
  bar_total_weight            DECIMAL(12,3) NULL DEFAULT 0     COMMENT '白条总重（当日白条库入库白条产品总重 = Σ burn.burn_weight）',
  avg_bar_weight              DECIMAL(12,3) NULL              COMMENT '白条均重（白条总重/屠宰头数；分母 0 → NULL）',
  bar_yield_rate              DECIMAL(12,3) NULL              COMMENT '白条出品率%（白条总重/送宰总重×100；row16 写÷屠宰头数为笔误，统一÷送宰总重对齐 row18；分母 0 → NULL）',

  -- 分割段
  cut_bar_count               INT           NULL DEFAULT 0     COMMENT '分割白条数（当日转入分割车间的白条数量 = 当日领用 cut_record 数）',
  precool_loss                DECIMAL(12,3) NULL DEFAULT 0     COMMENT '预冷损耗（当日 loss_flow loss_type=precool_loss 之和）',
  cut_product_weight          DECIMAL(12,3) NULL DEFAULT 0     COMMENT '分割产品总重（当日分割车间产出产品重之和 = Σ product_inhouse.product_weight 猪肉）',
  cut_bar_weight              DECIMAL(12,3) NULL DEFAULT 0     COMMENT '分割白条总重（当日白条出库总重 = Σ cut_record.pickup_weight）',
  cut_rate                    DECIMAL(12,3) NULL              COMMENT '分割率%（分割产品总重/分割白条总重×100；分母 0 → NULL）',
  cut_loss                    DECIMAL(12,3) NULL DEFAULT 0     COMMENT '分割间损耗重（当日 loss_flow loss_type=cut_loss 之和）',

  -- 毛菜段
  veg_weigh_weight            DECIMAL(12,3) NULL DEFAULT 0     COMMENT '毛菜称量总重（当日毛菜处理间采摘称重之和 = Σ vegetable_handle.picked_weight）',
  veg_loss                    DECIMAL(12,3) NULL DEFAULT 0     COMMENT '毛菜损耗重（当日 loss_flow loss_type=veg_handle_loss 之和）',
  veg_loss_rate               DECIMAL(12,3) NULL              COMMENT '毛菜损耗率%（毛菜损耗重/毛菜称量总重×100；分母 0 → NULL）',

  -- 果蔬月台段
  send_platform_weight        DECIMAL(12,3) NULL DEFAULT 0     COMMENT '发往月台果蔬总重（当日 Σ vegetable_handle.send_platform_weight）',
  receive_platform_weight     DECIMAL(12,3) NULL DEFAULT 0     COMMENT '月台接收果蔬总重（当日 Σ veg_receive.weight 自产 receive_type=1）',
  transport_loss_rate         DECIMAL(12,3) NULL              COMMENT '路损率%（(发往月台量−月台接收量)/发往月台量×100，运输损耗标准口径；分母 0 → NULL）',

  -- 净菜生产段
  prod_pick_weight            DECIMAL(12,3) NULL DEFAULT 0     COMMENT '果蔬生产领用总重（当日 stock_flow flow_type=prod_pick_out 之和）',
  prod_loss_weight            DECIMAL(12,3) NULL DEFAULT 0     COMMENT '果蔬产品生产损耗总重（当日 loss_flow loss_type=production_loss 之和）',
  net_veg_loss_rate           DECIMAL(12,3) NULL              COMMENT '净菜损耗率%（(生产损耗+录入损耗)/(生产领用−生产退回)×100；分母≤0 → NULL）',

  create_dept                 BIGINT        NULL COMMENT '创建部门',
  create_by                   BIGINT        NULL COMMENT '创建者',
  create_time                 DATETIME      NULL COMMENT '创建时间',
  update_by                   BIGINT        NULL COMMENT '更新者',
  update_time                 DATETIME      NULL COMMENT '更新时间',
  remark                      VARCHAR(500)  NULL COMMENT '备注',
  del_flag                    CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志',
  del_unique                  BIGINT        NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
  PRIMARY KEY (id),
  UNIQUE KEY uk_wh_indicator_tenant_date (tenant_id, stat_date, del_unique),
  KEY idx_wh_indicator_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库日数据记录（WMS-STAT-001，邓博 row16，定时落盘 T-1）';

-- ============================================================
-- 2. 作物日表 t_warehouse_cropp_record（row17）
-- ============================================================
DROP TABLE IF EXISTS t_warehouse_cropp_record;
CREATE TABLE t_warehouse_cropp_record (
  id                          BIGINT        NOT NULL AUTO_INCREMENT,
  tenant_id                   VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  stat_date                   DATE          NOT NULL COMMENT '统计日期（T-1）',
  crop_id                     BIGINT        NOT NULL COMMENT '作物 ID（FK → t_plant_crop_info.id；展示走 JOIN 取 crop_code/crop_name/image）',

  pick_weight                 DECIMAL(12,3) NULL DEFAULT 0     COMMENT '采摘量（当日该作物毛菜处理间称重总重 = Σ handle_record record_type=1 record_weight）',
  feed_weight                 DECIMAL(12,3) NULL DEFAULT 0     COMMENT '饲喂量（当日该作物饲料饲喂总重 = Σ handle_record handle_target=3 record_weight）',
  veg_handle_rate             DECIMAL(12,3) NULL              COMMENT '毛菜处理率%（(采摘总量−毛菜损耗量)/采摘总量×100；分母 0 → NULL）',
  receive_weight              DECIMAL(12,3) NULL DEFAULT 0     COMMENT '接收量（当日该作物果蔬月台接收总重 = Σ veg_receive.weight 自产）',
  send_platform_weight        DECIMAL(12,3) NULL DEFAULT 0     COMMENT '发往月台量（当日该作物从毛菜处理间发往月台重 = Σ handle_record handle_target=2 record_weight）',
  transport_loss_rate         DECIMAL(12,3) NULL              COMMENT '路损率%（(发往月台量−接收量)/接收量×100，按 row17 写法以接收量为分母；分母 0 → NULL）',
  out_weight                  DECIMAL(12,3) NULL DEFAULT 0     COMMENT '出库量（当日该作物生产领用出库量−生产退回量 = Σ stock_flow prod_pick_out − prod_return_in）',
  net_veg_loss_rate           DECIMAL(12,3) NULL              COMMENT '净菜损耗率%（(生产损耗+录入损耗)/出库量×100，按 row17 以出库量为分母；分母 0 → NULL）',

  create_dept                 BIGINT        NULL COMMENT '创建部门',
  create_by                   BIGINT        NULL COMMENT '创建者',
  create_time                 DATETIME      NULL COMMENT '创建时间',
  update_by                   BIGINT        NULL COMMENT '更新者',
  update_time                 DATETIME      NULL COMMENT '更新时间',
  remark                      VARCHAR(500)  NULL COMMENT '备注',
  del_flag                    CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志',
  del_unique                  BIGINT        NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
  PRIMARY KEY (id),
  UNIQUE KEY uk_wh_cropp_tenant_date_crop (tenant_id, stat_date, crop_id, del_unique),
  KEY idx_wh_cropp_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作物日数据记录（WMS-STAT-001，邓博 row17，定时落盘 T-1，一作物一行）';

-- ============================================================
-- 3. 仓库月表 t_warehouse_monthly_record（row18）
-- ============================================================
DROP TABLE IF EXISTS t_warehouse_monthly_record;
CREATE TABLE t_warehouse_monthly_record (
  id                          BIGINT        NOT NULL AUTO_INCREMENT,
  tenant_id                   VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  stat_month                  VARCHAR(7)    NOT NULL COMMENT '统计月份 yyyy-MM（当月每日刷新，历史月固定）',

  slaughter_count             INT           NULL DEFAULT 0     COMMENT '屠宰头数（当月日屠宰头数之和）',
  slaughter_rate              DECIMAL(12,3) NULL              COMMENT '屠宰率%（当月日接收重量之和/当月日送宰总重之和×100；分母 0 → NULL）',
  bar_yield_rate              DECIMAL(12,3) NULL              COMMENT '白条出品率%（当月日白条总重之和/当月日送宰总重之和×100；分母 0 → NULL）',
  cut_yield_rate              DECIMAL(12,3) NULL              COMMENT '分割出品率%（当月日分割产品总重之和/当月日分割白条总重之和×100；分母 0 → NULL）',

  create_dept                 BIGINT        NULL COMMENT '创建部门',
  create_by                   BIGINT        NULL COMMENT '创建者',
  create_time                 DATETIME      NULL COMMENT '创建时间',
  update_by                   BIGINT        NULL COMMENT '更新者',
  update_time                 DATETIME      NULL COMMENT '更新时间',
  remark                      VARCHAR(500)  NULL COMMENT '备注',
  del_flag                    CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志',
  del_unique                  BIGINT        NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
  PRIMARY KEY (id),
  UNIQUE KEY uk_wh_monthly_tenant_month (tenant_id, stat_month, del_unique),
  KEY idx_wh_monthly_stat_month (stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库月数据记录（WMS-STAT-001，邓博 row18，汇总当月日表）';
