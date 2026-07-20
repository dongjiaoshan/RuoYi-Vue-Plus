-- BRD-STAT-FIX-001 — 育肥猪断奶溯源快照（修 row10 生长链 4 项 blocker）。
--
-- 育肥猪出栏时算「净增重 / 日增重」需要个体断奶重 + 断奶日，但断奶事件只关联母猪 id，
-- 与育肥猪（仔猪贴标后翻成的 pig_info 行）是不同实体。原 aggregateMarketingWeanForDay
-- 按 weaning.pig_id = marketing.pig_id 关联恒空（母猪 vs 育肥猪不同体）。
--
-- 解法：在 t_farm_pig_info 加两列快照（wean_date / wean_weight），溯源链：
--   pig_info.id → pigletno.pig_id → pigletno.piglet_ear_no
--   → 个体断奶重 + 断奶日（断奶逐头明细 t_farm_pig_weaning_detail.ear_no/weight + 断奶主表 weaning_date）
-- 出栏统计直接读育肥猪自己的 pig_info.wean_weight/wean_date。
--
-- 注：实际个体断奶重写 t_farm_pig_weaning_detail（ear_no=仔猪耳号 / weight / weaning_id）；
--     t_farm_wean_weight 表 V1 未被任何写入点填充（WeaningServiceImpl OQ-11 仅写明细表），故回填走明细表。
--
-- V202607280901 > baseline；裸 ALTER ADD COLUMN（业务表，首次执行无冲突）。

SET NAMES utf8mb4;

-- 1. 加两列快照
ALTER TABLE t_farm_pig_info
  ADD COLUMN wean_date   DATE         NULL COMMENT '断奶日期(育肥猪溯源快照)',
  ADD COLUMN wean_weight DECIMAL(8,3) NULL COMMENT '个体断奶重kg(溯源快照)';

-- 2. 一次性回填存量育肥猪的断奶快照。
--    溯源：pig_info(育肥猪) → pigletno(pig_id=该育肥猪) → 同窝断奶逐头明细(ear_no=仔猪耳号)
--    → 个体断奶重 wd.weight + 断奶主表 weaning_date。
--    一头仔猪可能多条明细（理论唯一），取任一匹配行；断奶日按断奶主表。
UPDATE t_farm_pig_info p
  JOIN t_farm_pig_pigletno pl
    ON pl.pig_id = p.id AND pl.del_flag = '0' AND pl.tenant_id = p.tenant_id
  JOIN t_farm_pig_weaning_detail wd
    ON wd.ear_no = pl.piglet_ear_no AND wd.del_flag = '0' AND wd.tenant_id = p.tenant_id
  JOIN t_farm_pig_weaning w
    ON w.id = wd.weaning_id AND w.del_flag = '0' AND w.tenant_id = p.tenant_id
   SET p.wean_weight = wd.weight,
       p.wean_date   = DATE(w.weaning_date)
 WHERE p.del_flag = '0';
