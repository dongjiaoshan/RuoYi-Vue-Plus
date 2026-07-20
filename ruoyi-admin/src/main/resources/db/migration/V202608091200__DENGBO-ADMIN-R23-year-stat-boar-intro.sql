-- DENGBO-ADMIN-R23 · 养殖数据统计逻辑-年度指标（李婷 7/8）
--
-- 承接 R14/R21 的日/月表引种公母拆分，推到年表 t_farm_year_production：
--   1) 加「年度引种公猪数」introduce_boar_count = Σ当年T-1前月表 introduce_boar_count
--   2) introduce_count 改语义为「年度引种母猪数」= Σ当年T-1前月表 introduce_count（母猪口径）

-- 年表：加年度引种公猪数
ALTER TABLE t_farm_year_production
  ADD COLUMN introduce_boar_count INT NULL DEFAULT 0 COMMENT '年度引种公猪数（Σ当年T-1前月表 introduce_boar_count）' AFTER introduce_count;

-- 年表：introduce_count 改名年度引种母猪数
ALTER TABLE t_farm_year_production
  MODIFY COLUMN introduce_count INT NULL DEFAULT 0 COMMENT '年度引种母猪数（Σ当年T-1前月表 introduce_count）';
