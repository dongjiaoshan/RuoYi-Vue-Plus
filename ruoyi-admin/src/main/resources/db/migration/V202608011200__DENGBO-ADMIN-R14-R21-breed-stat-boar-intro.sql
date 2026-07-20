-- DENGBO-ADMIN-R14/R21 · 养殖数据统计逻辑-月度指标（李婷 7/7）
--
-- row14：
--   1) 日表 t_farm_indicator_record 加「引种公猪数」= 当日外部引种公猪头数（external AND pig_sex='M'）
--   2) 月表 t_farm_monthly_production 加「当月引种公猪数」= Σ当月T-1前日表引种公猪数
--   3) 月表 introduce_count 改语义为「当月引种母猪数」= Σ当月T-1前日表 introduce_sow_count（原为业务表全部引种）
-- row21：
--   1) 月表 born_count 中文名改「当月总活仔数」（算法不变，本就 = Σ日表 live_born_count）
--   2) 月表 farrow_loss_rate 口径 = Σ日死亡仔猪数 / 当月总活仔数 born_count（现值已等价，分母显式指向 born_count）

-- 日表：加引种公猪数
ALTER TABLE t_farm_indicator_record
  ADD COLUMN introduce_boar_count INT NULL DEFAULT 0 COMMENT '引种公猪数（当日外部引种公猪头数）' AFTER introduce_sow_count;

-- 月表：加当月引种公猪数
ALTER TABLE t_farm_monthly_production
  ADD COLUMN introduce_boar_count INT NULL DEFAULT 0 COMMENT '当月引种公猪数' AFTER introduce_count;

-- 月表：introduce_count 改名当月引种母猪数、born_count 改名当月总活仔数
ALTER TABLE t_farm_monthly_production
  MODIFY COLUMN introduce_count INT NULL DEFAULT 0 COMMENT '当月引种母猪数（Σ当月T-1前日表 introduce_sow_count）';
ALTER TABLE t_farm_monthly_production
  MODIFY COLUMN born_count INT NULL DEFAULT 0 COMMENT '当月总活仔数（Σ当月T-1前日表 live_born_count）';
