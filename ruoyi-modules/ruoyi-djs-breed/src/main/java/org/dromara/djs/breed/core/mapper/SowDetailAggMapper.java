package org.dromara.djs.breed.core.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.breed.core.domain.vo.FarrowByParityVo;
import org.dromara.djs.breed.core.domain.vo.FarrowRecordMpVo;
import org.dromara.djs.breed.core.domain.vo.PigMedRecordMpVo;
import org.dromara.djs.breed.core.domain.vo.PigTransferMpVo;
import org.dromara.djs.breed.core.domain.vo.SowPerformanceMpVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 母猪 / 猪只详情聚合 mapper（BRD-FIX-MP-DETAIL-SPLIT-001 + DJS-FIX-6-14，read-only 实时聚合）。
 *
 * <p>专给 mp 猪只详情 6 KPI + 产仔堆叠柱 + 分娩/用药/转移明细 list 用，从 {@code t_farm_pig_farrow} /
 * {@code t_farm_pig_weaning} / {@code t_farm_pig_breeding} / {@code t_farm_pig_abnormal} /
 * {@code t_breed_medicine_record} / {@code t_farm_pig_transfer} 按 pig_id 现算。
 * 不复用各域 CRUD mapper 是为隔离聚合 SQL。多租户由 MP 拦截器在 final SQL 注入 tenant_id。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-DETAIL-SPLIT-001
 */
@Mapper
public interface SowDetailAggMapper {

    // ── 6 KPI 聚合 ──────────────────────────────────────────────────────────

    /** 已落库分娩记录数；admin 生产指标实时 fallback 仅在至少一次分娩后启用。 */
    @Select("""
        SELECT COUNT(*)
        FROM t_farm_pig_farrow
        WHERE pig_id = #{pigId} AND del_flag = '0'
        """)
    int countFarrowRecords(@Param("pigId") Long pigId);

    /**
     * 分娩累计与重量实时快照。公式与夜间汇总一致：
     * 累计值取 SUM；平均出生重取 Σ每窝平均重 / 分娩窝数。
     */
    @Select("""
        SELECT
            SUM(total_born) AS totalBorn,
            SUM(live_born) AS totalLiveBorn,
            SUM(live_born) / NULLIF(COUNT(*), 0) AS avgLiveBornPerLitter,
            SUM(avg_weight) / NULLIF(COUNT(*), 0) AS avgBornWeight
        FROM t_farm_pig_farrow
        WHERE pig_id = #{pigId} AND del_flag = '0'
        """)
    SowPerformanceMpVo farrowProductionTotals(@Param("pigId") Long pigId);

    /**
     * 断奶累计与重量实时快照。公式与夜间汇总一致：
     * 累计值取 SUM；平均断奶重取 Σ每批平均重 / 断奶批数。
     */
    @Select("""
        SELECT
            COALESCE(SUM(weaned_count), 0) AS totalWeaned,
            SUM(avg_weaned_weight) / NULLIF(COUNT(*), 0) AS avgWeanedWeight
        FROM t_farm_pig_weaning
        WHERE pig_id = #{pigId} AND del_flag = '0'
        """)
    SowPerformanceMpVo weanProductionTotals(@Param("pigId") Long pigId);

    /**
     * 平均怀孕天数（row94 口径）= AVG(duration_days)，取状态变更记录表
     * 配种(old_status='PZ') → 分娩(new_status='FM') 的流转记录；无记录 → null。
     */
    @Select("""
        SELECT AVG(duration_days)
        FROM t_farm_status_record
        WHERE pig_id = #{pigId} AND old_status = 'PZ' AND new_status = 'FM'
        """)
    BigDecimal avgGestationDays(@Param("pigId") Long pigId);

    /**
     * 断奶-配种天数（row97/183 口径）= AVG(duration_days)，取状态变更记录表
     * 断奶(old_status='DN') → 配种(new_status='PZ') 的流转记录；无记录 → null。
     */
    @Select("""
        SELECT AVG(duration_days)
        FROM t_farm_status_record
        WHERE pig_id = #{pigId} AND old_status = 'DN' AND new_status = 'PZ'
        """)
    BigDecimal avgWeanToBreedDays(@Param("pigId") Long pigId);

    /**
     * 返空流总数 = 返情(R) + 流产(A) + 空怀(N) 事件累计次数；无异常事件返 0。
     */
    @Select("""
        SELECT COUNT(*)
        FROM t_farm_pig_abnormal
        WHERE pig_id = #{pigId} AND del_flag = '0'
        """)
    Integer countNullReturn(@Param("pigId") Long pigId);

    /**
     * 窝均产仔数 = avg(total_born)；total_born 缺时退化健仔+各类弱仔+死胎+木乃伊；无分娩 → null。
     */
    @Select("""
        SELECT AVG(
            COALESCE(total_born,
                COALESCE(healthy_male,0) + COALESCE(healthy_female,0)
              + COALESCE(weak_raised_male,0) + COALESCE(weak_raised_female,0)
              + COALESCE(weak_culled,0) + COALESCE(deformed_born,0) + COALESCE(crushed_born,0)
              + COALESCE(weak_born,0) + COALESCE(dead_born,0) + COALESCE(mummy_born,0))
        )
        FROM t_farm_pig_farrow
        WHERE pig_id = #{pigId} AND del_flag = '0'
        """)
    BigDecimal avgBornPerLitter(@Param("pigId") Long pigId);

    /**
     * 窝均断奶数 = avg(weaning.weaned_count)；无断奶 → null。
     */
    @Select("""
        SELECT AVG(weaned_count)
        FROM t_farm_pig_weaning
        WHERE pig_id = #{pigId} AND del_flag = '0' AND weaned_count IS NOT NULL
        """)
    BigDecimal avgWeanedPerLitter(@Param("pigId") Long pigId);

    /**
     * 最近一次分娩日期（NPD 基准之一）；无返 null。返 'YYYY-MM-DD' 字符串。
     */
    @Select("""
        SELECT DATE_FORMAT(MAX(farrow_date), '%Y-%m-%d')
        FROM t_farm_pig_farrow
        WHERE pig_id = #{pigId} AND del_flag = '0'
        """)
    String lastFarrowDate(@Param("pigId") Long pigId);

    /**
     * 最近一次配种日期（NPD 基准之一）；无返 null。
     */
    @Select("""
        SELECT DATE_FORMAT(MAX(breeding_date), '%Y-%m-%d')
        FROM t_farm_pig_breeding
        WHERE pig_id = #{pigId} AND del_flag = '0'
        """)
    String lastBreedingDate(@Param("pigId") Long pigId);

    // ── 产仔性能堆叠柱（6 系列） ───────────────────────────────────────────────

    /**
     * 产仔性能按胎次聚合（堆叠柱 6 系列）：同 pig + parity 多行 SUM 汇总，按胎次升序。
     *
     * <p>健仔 = Σ(healthy_male+healthy_female)，两列全空退化 live_born；
     * 弱仔(处死) = Σ weak_culled；弱仔(饲养) = Σ(weak_raised_male+weak_raised_female)，
     * 多类弱仔全空退化 weak_born 归入留养；压死 = Σ crushed_born；畸形 = Σ deformed_born；
     * 死胎 = Σ dead_born；木乃伊 = Σ mummy_born。parity 为 null 的窝归一组（COALESCE 0）。</p>
     */
    @Select("""
        SELECT
            COALESCE(parity, 0) AS parity,
            SUM(CASE WHEN healthy_male IS NULL AND healthy_female IS NULL
                     THEN COALESCE(live_born, 0)
                     ELSE COALESCE(healthy_male,0) + COALESCE(healthy_female,0) END) AS healthy,
            SUM(COALESCE(weak_culled, 0)) AS weakCulled,
            SUM(CASE WHEN weak_raised_male IS NULL AND weak_raised_female IS NULL
                          AND weak_culled IS NULL AND deformed_born IS NULL AND crushed_born IS NULL
                     THEN COALESCE(weak_born, 0)
                     ELSE COALESCE(weak_raised_male,0) + COALESCE(weak_raised_female,0) END) AS weakRaised,
            SUM(COALESCE(crushed_born, 0)) AS crushed,
            SUM(COALESCE(deformed_born, 0)) AS deformed,
            SUM(COALESCE(mummy_born, 0)) AS mummy
        FROM t_farm_pig_farrow
        WHERE pig_id = #{pigId} AND del_flag = '0'
        GROUP BY COALESCE(parity, 0)
        ORDER BY COALESCE(parity, 0) ASC
        """)
    List<FarrowByParityVo> farrowByParity(@Param("pigId") Long pigId);

    // ── 分娩记录明细 list（row212） ────────────────────────────────────────────

    /**
     * 分娩记录逐条明细（倒序 by farrow_date）：原型「分娩记录」tab 明细卡。
     *
     * <p>关联 t_farm_pig_breeding 取配种日期 + 配种公猪耳号；母猪数 = 健仔母 + 弱仔留养母。</p>
     */
    @Select("""
        SELECT
            DATE_FORMAT(b.breeding_date, '%Y-%m-%d') AS breedingDate,
            DATE_FORMAT(f.farrow_date, '%Y-%m-%d')   AS farrowDate,
            f.parity        AS parity,
            f.total_born    AS totalBorn,
            b.boar_ear_no   AS boarEarNo,
            COALESCE(f.live_born, 0) AS liveBorn,
            f.avg_weight    AS avgWeight,
            (COALESCE(f.healthy_male,0)   + COALESCE(f.weak_raised_male,0))   AS maleCount,
            (COALESCE(f.healthy_female,0) + COALESCE(f.weak_raised_female,0)) AS femaleCount,
            (CASE WHEN f.healthy_male IS NULL AND f.healthy_female IS NULL
                  THEN COALESCE(f.live_born, 0)
                  ELSE COALESCE(f.healthy_male,0) + COALESCE(f.healthy_female,0) END) AS healthy,
            COALESCE(f.weak_culled, 0)  AS weakCulled,
            (COALESCE(f.weak_raised_male,0) + COALESCE(f.weak_raised_female,0)) AS weakRaised,
            COALESCE(f.crushed_born, 0) AS crushed,
            COALESCE(f.deformed_born,0) AS deformed,
            COALESCE(f.dead_born, 0)    AS dead,
            COALESCE(f.mummy_born, 0)   AS mummy
        FROM t_farm_pig_farrow f
            LEFT JOIN t_farm_pig_breeding b ON b.id = f.breeding_id AND b.del_flag = '0'
        WHERE f.pig_id = #{pigId} AND f.del_flag = '0'
        ORDER BY f.farrow_date DESC, f.id DESC
        """)
    List<FarrowRecordMpVo> farrowRecords(@Param("pigId") Long pigId);

    // ── 用药记录 list（row212 母猪 / row213 育肥仔猪） ──────────────────────────

    /**
     * 本猪用药记录（倒序 by use_date）：原型「用药记录」tab 表。
     *
     * <p>来源 t_breed_medicine_record 按 pig_id（drug_type 1 单只 + 3 批量 detail 都含本猪）。
     * 原因 / 方式留 code，service 层翻字典 label。</p>
     */
    @Select("""
        SELECT
            DATE_FORMAT(use_date, '%Y-%m-%d') AS useDate,
            medicine_reason  AS reason,
            medicine_name    AS medicineName,
            medicine_dosage  AS dosage,
            dosage_unit      AS dosageUnit,
            medicine_way     AS way,
            operator_name    AS operatorName
        FROM t_breed_medicine_record
        WHERE pig_id = #{pigId} AND del_flag = '0'
        ORDER BY use_date DESC, id DESC
        """)
    List<PigMedRecordMpVo> medRecords(@Param("pigId") Long pigId);

    // ── 转移记录 list（row213 育肥仔猪） ───────────────────────────────────────

    /**
     * 本猪转移记录（倒序 by 日期）：原型「转移记录」tab 表（含出栏行）。
     *
     * <p>UNION 两类记录：</p>
     * <ul>
     *   <li>普通转移 t_farm_pig_transfer：targetBarn = CONCAT(new_barn_name, new_pen_name)，slaughter=0；</li>
     *   <li>出栏 t_farm_pig_marketing（出栏 new_barn_id 恒非空，故另存此表）：targetBarn 空、slaughter=1。</li>
     * </ul>
     *
     * <p>开始/结束时间（旧栏位驻留区间）由 service 用相邻日期补算（SQL 仅给 transferDate）。</p>
     */
    @Select("""
        SELECT transferDate, sourceBarn, targetBarn, operatorId, slaughter FROM (
            SELECT
                transfer_date AS rawDate,
                DATE_FORMAT(transfer_date, '%Y-%m-%d') AS transferDate,
                CONCAT(COALESCE(old_barn_name, ''), COALESCE(old_pen_name, '')) AS sourceBarn,
                CONCAT(COALESCE(new_barn_name, ''), COALESCE(new_pen_name, '')) AS targetBarn,
                operator_id AS operatorId,
                0 AS slaughter,
                id AS rawId
            FROM t_farm_pig_transfer
            WHERE pig_id = #{pigId} AND del_flag = '0'
            UNION ALL
            SELECT
                marketing_date AS rawDate,
                DATE_FORMAT(marketing_date, '%Y-%m-%d') AS transferDate,
                '' AS sourceBarn,
                '' AS targetBarn,
                operator_id AS operatorId,
                1 AS slaughter,
                id AS rawId
            FROM t_farm_pig_marketing
            WHERE pig_id = #{pigId} AND del_flag = '0'
        ) u
        ORDER BY rawDate DESC, rawId DESC
        """)
    List<PigTransferMpVo> transferRecords(@Param("pigId") Long pigId);
}
