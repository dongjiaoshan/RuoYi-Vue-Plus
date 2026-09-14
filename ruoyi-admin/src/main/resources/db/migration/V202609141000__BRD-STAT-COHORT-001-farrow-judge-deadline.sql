-- ============================================================================
-- BRD-STAT-COHORT-001  分娩率改配种批次口径 + PSY 年化
--
-- 1) 新增配置键 sow_farrow_judge_deadline_days = 119
--    甲方（祝碧）口径：正常妊娠 114-116 天，119 天是**判定节点** —— 配种满 119 天仍未分娩，
--    该头必须定性为「未分娩」并计入损失，以此算分娩率。
--    这与 sow_breed_to_farrow_days 不是一回事，后者是「分娩板块可选门」（配种满 N 天才进
--    mp 分娩录入列表，PigCoreServiceImpl#computeDueDateMap）。把它改成 119 会让 114-118 天
--    分娩的母猪在列表里消失，只能搜耳号才录得进，故统计判定另起一个键。
--
-- 2) 年表落统计区间两列：PSY / 平均非生产天数按「年化」口径展示，必须同时说明年化自哪段区间，
--    否则 44 天的数乘 365/44 挂着「年度」标题无法自证。
--
-- 3) 补 t_farm_pig_abnormal(related_breeding_id) 索引：批次去向归集按该列关联。
--
-- 幂等：INSERT IGNORE 按 uk_cycle_key 去重；加列/加索引先查 information_schema。
-- ============================================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 分娩判定节点天数
--    custom_value 留 NULL = 未定制。注意 effectiveValue 把显式 0 当有效值，
--    而 0 天对「判定节点」无业务含义（会让所有批次瞬间到期），故读取侧 (<=0 → 回退默认) 另有防御。
-- ------------------------------------------------------------
INSERT IGNORE INTO t_farm_production_cycle_config (
    id, tenant_id, config_key, default_value, custom_value, unit, description,
    create_by, create_time, del_flag, del_unique)
VALUES
  (109, '1001', 'sow_farrow_judge_deadline_days', 119, NULL, '天',
   '分娩判定节点（配种满该天数仍未分娩即定性为未分娩、计入损失，用于分娩率）', 1, NOW(), '0', 0);

-- ------------------------------------------------------------
-- 2. 年表 PSY 统计区间
-- ------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_year_production'
               AND COLUMN_NAME = 'psy_stat_from');
SET @s1 := IF(@c1 > 0, 'SELECT 1',
  "ALTER TABLE t_farm_year_production
     ADD COLUMN psy_stat_from DATE NULL
     COMMENT 'PSY/非生产天数年化的统计区间起始日（= 年初与断奶记录最早业务日的较晚者）'");
PREPARE st1 FROM @s1;
EXECUTE st1;
DEALLOCATE PREPARE st1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_year_production'
               AND COLUMN_NAME = 'psy_stat_days');
SET @s2 := IF(@c2 > 0, 'SELECT 1',
  "ALTER TABLE t_farm_year_production
     ADD COLUMN psy_stat_days INT NOT NULL DEFAULT 0
     COMMENT 'PSY/非生产天数年化的统计区间天数（= 该区间内已落盘日表行数，年化乘数 365/该值）' AFTER psy_stat_from");
PREPARE st2 FROM @s2;
EXECUTE st2;
DEALLOCATE PREPARE st2;

-- ------------------------------------------------------------
-- 3. cohort 分子/分母落盘
--    分娩率改按配种批次口径后，分子 = 该批次中 ≤judge 天分娩的头数、分母 = 当期到期的批次数。
--    两者都落盘，率才能自证，月表 Σ 也才能对上年表（到期日唯一归属一个月）。
--    年表 breeding_count（全年配种次数）语义不变，仍是原始计数，不参与分娩率。
-- ------------------------------------------------------------
SET @c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_year_production'
               AND COLUMN_NAME = 'cohort_matured_count');
SET @s4 := IF(@c4 > 0, 'SELECT 1',
  "ALTER TABLE t_farm_year_production
     ADD COLUMN cohort_matured_count INT NOT NULL DEFAULT 0
     COMMENT '年分娩率分母：判定节点落在本年且已到期的配种批次数'");
PREPARE st4 FROM @s4;
EXECUTE st4;
DEALLOCATE PREPARE st4;

SET @c5 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_monthly_production'
               AND COLUMN_NAME = 'cohort_farrow_count');
SET @s5 := IF(@c5 > 0, 'SELECT 1',
  "ALTER TABLE t_farm_monthly_production
     ADD COLUMN cohort_farrow_count INT NOT NULL DEFAULT 0
     COMMENT '月分娩率分子：本月到期的配种批次中，在判定节点内分娩的头数（分母 = mate_litter_count）'");
PREPARE st5 FROM @s5;
EXECUTE st5;
DEALLOCATE PREPARE st5;

-- ------------------------------------------------------------
-- 4. 批次去向归集索引
-- ------------------------------------------------------------
SET @c3 := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_pig_abnormal'
               AND INDEX_NAME = 'idx_abnormal_related_breeding');
SET @s3 := IF(@c3 > 0, 'SELECT 1',
  'CREATE INDEX idx_abnormal_related_breeding ON t_farm_pig_abnormal (tenant_id, related_breeding_id)');
PREPARE st3 FROM @s3;
EXECUTE st3;
DEALLOCATE PREPARE st3;
