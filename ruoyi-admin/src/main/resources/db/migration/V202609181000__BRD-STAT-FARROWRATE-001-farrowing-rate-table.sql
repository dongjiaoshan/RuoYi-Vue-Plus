-- ============================================================================
-- BRD-STAT-FARROWRATE-001  同期配种分娩记录表 t_farm_farrowing_rate（甲方 row229）
--
-- 一条配种记录一行，把「这一窝后来怎么样了」摊平成四个日期列，供甲方直接查表对账，
-- 并作为 mp 月/年分娩率的取数来源（row230 / row231）；admin 配种批次对账页不走本表（D-0072）。
--
-- 口径（甲方 2026-09-18 拍板）：
--   D-0090 分母 = 预估分娩日**已到**且落在该月/该年的记录数（未到期的不进分母）
--   D-0091 分子 = 分娩日期非空 **且 分娩日期 ≤ 预估分娩日** 的记录数（晚产窝不算）
--   甲方原话：「对于晚产窝不算分子，分娩率的分子数为：分娩日期不为空且分娩日期小于等于预估分娩日的记录数」
--
-- ⚠️ 这两条合起来与现行 cohort 口径**逐字等价**（分娩日 ≤ 配种日+judgeDays ⟺ DATEDIFF ≤ judgeDays），
--    故本次改造只换数据源、不改数字：生产 2026 实测 43/44=97.73%，改造前后应一致。
--
-- 预估分娩日 = 配种日 + judgeDays，judgeDays 读 sow_farrow_judge_deadline_days 配置（缺省 119），
--   与 DashboardServiceImpl#farrowJudgeDeadlineDays() 同源，不另造阈值。
--   ⚠️ 本列是**落盘快照**：配置日后若改，历史行不会自动跟着变（每日任务只刷近一个月）。
--      聚合时检测到配置漂移会 log.warn 提示重跑全量初始化，不静默纠偏。
-- ============================================================================
SET NAMES utf8mb4;

SET @judge := (SELECT COALESCE(custom_value, default_value)
                 FROM t_farm_production_cycle_config
                WHERE config_key = 'sow_farrow_judge_deadline_days' AND del_flag = '0' LIMIT 1);
SET @judge := IF(@judge IS NULL OR @judge <= 0, 119, @judge);

-- ---------------------------------------------------------------------------
-- §1 建表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_farm_farrowing_rate (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001'  COMMENT '租户编号',
    pig_id              BIGINT       NOT NULL                 COMMENT '猪只ID（t_farm_pig_info.id）',
    ear_tag             VARCHAR(32)  NULL                     COMMENT '母猪耳号（冗余自 t_farm_pig_info.ear_tag，便于甲方直接查表对账）',
    breeding_id         BIGINT       NOT NULL                 COMMENT '配种ID（t_farm_pig_breeding.id），一条配种记录一行',
    breeding_date       DATE         NOT NULL                 COMMENT '配种日期（取自配种记录表，只取日期部分）',
    expected_farrow_date DATE        NOT NULL                 COMMENT '预估分娩日 = 配种日 + 判定节点天数（sow_farrow_judge_deadline_days，缺省119）。落盘快照，配置改了历史行不自动变',
    farrow_date         DATE         NULL                     COMMENT '分娩日期（按配种ID查分娩记录表，同一配种有多条取最早；查不到留空）',
    abnormal_date       DATE         NULL                     COMMENT '返空流日期（按配种ID查返空流记录表，含返情R/空怀N/流产A，多条取最早；查不到留空）',
    cull_date           DATE         NULL                     COMMENT '死淘日期（按猪只ID查状态记录表 DIE/ELIMINATE，多条取最早；查不到留空）',
    create_dept         BIGINT       NULL                     COMMENT '创建部门',
    create_by           BIGINT       NULL                     COMMENT '创建者',
    create_time         DATETIME     NULL                     COMMENT '创建时间',
    update_by           BIGINT       NULL                     COMMENT '更新者',
    update_time         DATETIME     NULL                     COMMENT '更新时间',
    del_flag            CHAR(1)      DEFAULT '0'              COMMENT '软删 0=正常 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_breeding (tenant_id, breeding_id),
    KEY idx_tenant_expected (tenant_id, expected_farrow_date),
    KEY idx_tenant_pig (tenant_id, pig_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同期配种分娩记录表（BRD-STAT-FARROWRATE-001，mp 月/年分娩率取数来源）';

-- ---------------------------------------------------------------------------
-- §2 历史数据全量初始化（甲方原文：「历史数据初始化：数据需从头完整写入一次」）
--    四步与每日任务同序：配种 → 分娩 → 返空流 → 死淘。
--    幂等：uk_tenant_breeding + INSERT ... ON DUPLICATE KEY UPDATE，重跑只刷新不重复。
-- ---------------------------------------------------------------------------

-- 第一步：以配种记录表铺底（每条配种一行）
INSERT INTO t_farm_farrowing_rate
    (tenant_id, pig_id, ear_tag, breeding_id, breeding_date, expected_farrow_date,
     create_by, create_time, del_flag)
SELECT b.tenant_id,
       b.pig_id,
       p.ear_tag,
       b.id,
       DATE(b.breeding_date),
       DATE(b.breeding_date) + INTERVAL @judge DAY,
       1, NOW(), '0'
  FROM t_farm_pig_breeding b
  LEFT JOIN t_farm_pig_info p ON p.id = b.pig_id AND p.del_flag = '0'
 WHERE b.del_flag = '0'
ON DUPLICATE KEY UPDATE
    pig_id               = VALUES(pig_id),
    ear_tag              = VALUES(ear_tag),
    breeding_date        = VALUES(breeding_date),
    expected_farrow_date = VALUES(expected_farrow_date),
    -- 复活：配种记录曾被软删（本表行随之 del_flag='1'）后又恢复，必须把标志翻回来。
    -- 不翻的话 uk_tenant_breeding 会让它永远插不进来，分母从此少一条且无任何信号。
    del_flag             = '0',
    update_time          = NOW();

-- 配种记录被软删 → 本表对应行跟着软删，否则分母里永久留着一条已撤销的配种。
-- 本表是纯派生表，行在不在只由源表决定；用软删而非物理删，配合上面的 del_flag='0' 支持复活。
UPDATE t_farm_farrowing_rate r
   LEFT JOIN t_farm_pig_breeding b
          ON b.id = r.breeding_id AND b.tenant_id = r.tenant_id AND b.del_flag = '0'
   SET r.del_flag = '1', r.update_time = NOW()
 WHERE r.del_flag = '0' AND b.id IS NULL;

-- 第二步：按配种ID回填分娩日期（同一配种有多条分娩记录取最早，与 cohort 的 MIN 口径一致）
--   用 LEFT JOIN 而非内连接：源记录被撤销时要把已写入的日期**置空**，内连接只能填不能清，
--   重跑就不会收敛到源表状态、幂等性名不副实。第三、四步同理。
UPDATE t_farm_farrowing_rate r
  LEFT JOIN (SELECT tenant_id, breeding_id, MIN(DATE(farrow_date)) AS d
          FROM t_farm_pig_farrow
         WHERE del_flag = '0' AND breeding_id IS NOT NULL
         GROUP BY tenant_id, breeding_id) f
    ON f.tenant_id = r.tenant_id AND f.breeding_id = r.breeding_id
   SET r.farrow_date = f.d,
       r.update_time = NOW()
 WHERE r.del_flag = '0';

-- 第三步：按配种ID回填返空流日期（返情R / 空怀N / 流产A 三类合称返空流，多条取最早）
UPDATE t_farm_farrowing_rate r
  LEFT JOIN (SELECT tenant_id, related_breeding_id AS bid, MIN(DATE(abnormal_date)) AS d
          FROM t_farm_pig_abnormal
         WHERE del_flag = '0' AND related_breeding_id IS NOT NULL
         GROUP BY tenant_id, related_breeding_id) a
    ON a.tenant_id = r.tenant_id AND a.bid = r.breeding_id
   SET r.abnormal_date = a.d,
       r.update_time   = NOW()
 WHERE r.del_flag = '0';

-- 第四步：按猪只ID回填死淘日期（t_farm_status_record 是追加型事件日志，无 del_flag）
--   甲方原文就是「查询猪只ID」：一头猪只死一次，她名下每条配种记录都会写上同一个死淘日期。
UPDATE t_farm_farrowing_rate r
  LEFT JOIN (SELECT tenant_id, pig_id, MIN(DATE(change_time)) AS d
          FROM t_farm_status_record
         WHERE event_type IN ('DIE', 'ELIMINATE')
         GROUP BY tenant_id, pig_id) s
    ON s.tenant_id = r.tenant_id AND s.pig_id = r.pig_id
   SET r.cull_date   = s.d,
       r.update_time = NOW()
 WHERE r.del_flag = '0';

-- ---------------------------------------------------------------------------
-- §3 校准年表/月表的列注释（甲方 row229 的本意就是直接查表对账，列注释是他看到的第一样东西）
--    V202609161000 写入的注释描述的是 D-0087 口径，已被 D-0090/D-0091/D-0092 取代；
--    其中 cohort_matured_count 的旧注释写「不是分娩率分母」，而它现在正是分母，属反向误导。
--    只改 COMMENT，不动类型/可空/默认值。
-- ---------------------------------------------------------------------------
ALTER TABLE t_farm_year_production
    MODIFY COLUMN year_batch_farrow_count INT NULL DEFAULT 0
        COMMENT '年分娩率分子（t_farm_farrowing_rate 全年「分娩日期非空且≤预估分娩日」的行数；晚产窝不计）',
    MODIFY COLUMN year_farrow_rate DECIMAL(12,3) NULL DEFAULT 0.000
        COMMENT '年分娩率%（分子/分母×100，两者同取 t_farm_farrowing_rate 一次查询）',
    MODIFY COLUMN cohort_matured_count INT NOT NULL DEFAULT 0
        COMMENT '年分娩率分母（t_farm_farrowing_rate 全年「预估分娩日已到、即≤收口日T-1」的行数）';

ALTER TABLE t_farm_monthly_production
    MODIFY COLUMN mate_litter_count INT NULL DEFAULT 0
        COMMENT '当月到期批次数 = 月分娩率分母（预估分娩日落在本月且已到）；年分娩率不再Σ本列',
    MODIFY COLUMN cohort_farrow_count INT NOT NULL DEFAULT 0
        COMMENT '当月按期分娩数 = 月分娩率分子（分娩日期非空且≤预估分娩日）';
