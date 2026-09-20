-- ============================================================
-- BRD-STAT-NPD-001  妊娠损失天数进 NPD / 出 PSY；断奶统计改以明细表为源
--   甲方 V6 需求和问题（测试环境） 行232 / 行233 / 行234 / 行239（2026-09-18~19）
--
-- 口径（doc/decisions.yaml）
--   D-0096  妊娠损失天数 = 当日由配种（PZ）转出为 返情 FQ / 空怀 KH / 流产 LC / 死亡 DIE / 淘汰 ELIMINATE
--           的母猪，其在 PZ 状态的停留天数之和。取 t_farm_status_record.duration_days，与既有
--           单头母猪 NPD（sumSowNpdDurationDays）同一张流水表、同一个字段，只是 old_status 换成 PZ。
--   D-0099  覆盖 D-0064：月/年 NPD 分子由「Σ日非生产母猪头数」改为「Σ日非生产母猪头数 + Σ日妊娠损失天数」。
--   D-0100  覆盖 D-0086：PSY 分子由「Σ日在怀母猪头数」改为「Σ日在怀母猪头数 − Σ日妊娠损失天数」。
--   D-0097（待甲方）分母仍按「÷平均存栏」写，等价于甲方写的 Σ 再乘区间天数；照字面写会让页面上的
--           7.35 天变成 0.02，量纲不成立。
--   D-0101（待甲方）wean_total_weight 让位给「Σ当日断奶明细重」，原语义（当日出栏猪断奶时总重）
--           搬到新列 marketing_wean_weight，净增重链条继续可复算。
--
-- 甲方行232 列的三项里只有「妊娠损失天数」是新的：
--   非生产状态天数 = 已有 npd_days（= end_nonprod_sow_count，同值同定义）
--   妊娠猪只头数   = 已有 pregnant_sow_count
-- 不新建重复列 —— 同一个量两处落盘就会分叉。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 日表加「妊娠损失天数」
-- ------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_indicator_record'
               AND COLUMN_NAME = 'preg_loss_days');
SET @s1 := IF(@c1 > 0, 'SELECT 1',
  "ALTER TABLE t_farm_indicator_record
     ADD COLUMN preg_loss_days INT NULL DEFAULT 0
     COMMENT '妊娠损失天数（当日由配种 PZ 转出为 FQ/KH/LC/DIE/ELIMINATE 的母猪，Σ其在 PZ 的停留天数）；进 NPD 分子、出 PSY 分子'");
PREPARE st1 FROM @s1;
EXECUTE st1;
DEALLOCATE PREPARE st1;

-- ------------------------------------------------------------
-- 2. 日表加「当日出栏猪断奶时总重」—— 承接 wean_total_weight 的原语义
--
--    净增重 = Σ(出栏重 − 该批猪断奶重)，被减数就是这一项。行239 把 wean_total_weight 征用为
--    「当日断奶仔猪总重」之后，被减数若不另立一列就只剩在内存里，日后无法从表里复算净增重。
-- ------------------------------------------------------------
SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_indicator_record'
               AND COLUMN_NAME = 'marketing_wean_weight');
SET @s2 := IF(@c2 > 0, 'SELECT 1',
  "ALTER TABLE t_farm_indicator_record
     ADD COLUMN marketing_wean_weight DECIMAL(12,3) NULL DEFAULT 0
     COMMENT '当日出栏猪只在断奶时的总重 kg（净增重被减数：净增重 = 同集合出栏总重 − 本列）'");
PREPARE st2 FROM @s2;
EXECUTE st2;
DEALLOCATE PREPARE st2;

-- 3. 回填 marketing_wean_weight —— 先搬后改，顺序不能倒
--
--    此刻 wean_total_weight 里装的还是旧语义，原样搬进新列即可；第 5 步才把它改写成新语义。
--
--    只在「本次刚建出这一列」时搬（@c2 = 0）。列已存在说明搬运早跑过，此时 wean_total_weight
--    装的已经是新语义，再搬一次就是拿断奶仔猪总重覆盖掉出栏猪断奶重 —— 这一步天然不幂等，
--    手工重跑整个文件会毁数据，所以用建列那一刻的探测结果把它锁死。
--
--    紧挨第 2 步放，是为了把会话变量的跨度压到最短：Flyway 万一把脚本拆到不同连接执行，
--    @c2 会变 NULL、这一步静默跳过、marketing_wean_weight 全 0 还不报错。部署后必查一条：
--      SELECT COUNT(*) FROM t_farm_indicator_record WHERE marketing_wean_weight <> 0;
-- ------------------------------------------------------------
SET @s4 := IF(@c2 > 0, 'SELECT 1',
  "UPDATE t_farm_indicator_record
      SET marketing_wean_weight = COALESCE(wean_total_weight, 0)
    WHERE del_flag = '0'");
PREPARE st4 FROM @s4;
EXECUTE st4;
DEALLOCATE PREPARE st4;

-- ------------------------------------------------------------
-- 4. 月表加「当月NPD天数」
-- ------------------------------------------------------------
SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_farm_monthly_production'
               AND COLUMN_NAME = 'month_npd_days');
SET @s3 := IF(@c3 > 0, 'SELECT 1',
  "ALTER TABLE t_farm_monthly_production
     ADD COLUMN month_npd_days INT NULL DEFAULT 0
     COMMENT '当月NPD天数 = Σ当月日非生产母猪头数 + Σ当月妊娠损失天数（NPD 分子，D-0099）'");
PREPARE st3 FROM @s3;
EXECUTE st3;
DEALLOCATE PREPARE st3;

-- ------------------------------------------------------------
-- ------------------------------------------------------------
-- 5. 回填 preg_loss_days / weaned_piglet_count / wean_total_weight
--
--    定时任务 DashboardAggregateJob 是 60 天滚动窗，够不着更早的日表行，历史必须在这里一次性补。
--    三项都用 LEFT JOIN 子查询 + COALESCE 0，重复执行收敛到同一结果。
--
--    weaned_piglet_count / wean_total_weight 改以 t_farm_pig_weaning_detail 为源（甲方行239 第 3 点）：
--    明细是逐头一行，断奶从「整窝一起断」改成「按所选仔猪断」（行238）之后，只有明细数得准。
--    回落规则与 AggregateQueryMapper.WEAN_COUNT_EXPR/WEAN_WEIGHT_EXPR 逐字一致（D-0103）：整条断奶记录
--    一行明细都没有时用它自己的汇总值。明细表 V202606111520 才上线，照字面只数明细行会把此前的历史窝
--    全算成 0 头，而断奶母猪数仍按母猪去重照算 —— 窝均断奶数与 PSY 会在上线当天集体跳水。
--    这里和夜跑必须同一套规则，否则历史行与滚动窗重算出来的是两个数。
-- ------------------------------------------------------------
UPDATE t_farm_indicator_record r
   SET r.preg_loss_days = COALESCE((
        SELECT SUM(sr.duration_days)
          FROM t_farm_status_record sr
         WHERE sr.tenant_id = r.tenant_id
           AND DATE(sr.change_time) = r.stat_date
           AND sr.old_status = 'PZ'
           AND (sr.new_status IN ('FQ', 'KH', 'LC') OR sr.event_type IN ('DIE', 'ELIMINATE'))
           AND sr.duration_days > 0), 0)
 WHERE r.del_flag = '0';

UPDATE t_farm_indicator_record r
   SET r.weaned_piglet_count = COALESCE((
        SELECT SUM(CASE WHEN COALESCE(dc.cnt, 0) > 0 THEN dc.cnt ELSE COALESCE(w.weaned_count, 0) END)
          FROM t_farm_pig_weaning w
          LEFT JOIN (SELECT d.weaning_id, COUNT(*) AS cnt, COALESCE(SUM(d.weight), 0) AS wsum
                       FROM t_farm_pig_weaning_detail d
                      WHERE d.del_flag = '0'
                      GROUP BY d.weaning_id) dc ON dc.weaning_id = w.id
         WHERE w.tenant_id = r.tenant_id
           AND w.del_flag = '0'
           AND DATE(w.weaning_date) = r.stat_date), 0),
       r.wean_total_weight = COALESCE((
        SELECT SUM(CASE WHEN COALESCE(dc.cnt, 0) > 0 THEN dc.wsum ELSE COALESCE(w.weaned_weight, 0) END)
          FROM t_farm_pig_weaning w
          LEFT JOIN (SELECT d.weaning_id, COUNT(*) AS cnt, COALESCE(SUM(d.weight), 0) AS wsum
                       FROM t_farm_pig_weaning_detail d
                      WHERE d.del_flag = '0'
                      GROUP BY d.weaning_id) dc ON dc.weaning_id = w.id
         WHERE w.tenant_id = r.tenant_id
           AND w.del_flag = '0'
           AND DATE(w.weaning_date) = r.stat_date), 0)
 WHERE r.del_flag = '0';

-- ------------------------------------------------------------
-- 6. 注释校准 —— 被本次改动改掉语义的既有列
--
--    改列语义不改注释，下一个读表的人只能靠猜。年表两列的旧注释描述的正是 D-0064 那版
--    「Σ日非生产母猪」，现在分子多了妊娠损失项。
-- ------------------------------------------------------------
ALTER TABLE t_farm_indicator_record
    MODIFY COLUMN wean_total_weight DECIMAL(12,3) NULL DEFAULT 0
        COMMENT '当日断奶仔猪总重 kg（Σ t_farm_pig_weaning_detail.weight，按断奶日期归日）',
    MODIFY COLUMN weaned_piglet_count INT NULL DEFAULT 0
        COMMENT '当日断奶仔猪数（t_farm_pig_weaning_detail 逐头明细行数，按断奶日期归日）';

ALTER TABLE t_farm_year_production
    MODIFY COLUMN total_npd_days INT NULL DEFAULT 0
        COMMENT '全年总NPD天数 = Σ日非生产母猪头数 + Σ日妊娠损失天数（D-0099）',
    MODIFY COLUMN avg_npd_days DECIMAL(12,3) NULL DEFAULT 0
        COMMENT '年头均NPD天数（天/母猪·年）= 全年总NPD天数 ÷ 年均生产母猪存栏，再年化';

ALTER TABLE t_farm_monthly_production
    MODIFY COLUMN npd_days DECIMAL(12,3) NULL DEFAULT 0
        COMMENT '月头均NPD天数（天/母猪·月）= 当月NPD天数 ÷ 月均生产母猪存栏';
