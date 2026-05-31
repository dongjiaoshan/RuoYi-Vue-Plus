package org.dromara.djs.breed.core.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.breed.core.domain.vo.FarrowByParityVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 母猪详情聚合 mapper（BRD-FIX-MP-DETAIL-SPLIT-001，read-only 实时聚合，无 DDL）。
 *
 * <p>专给 mp 母猪详情 6 KPI + 产仔堆叠柱用，从 {@code t_farm_pig_farrow} /
 * {@code t_farm_pig_weaning} 按 pig_id 现算。不复用 PigFarrowMapper 是为隔离聚合 SQL，
 * 不污染分娩 CRUD mapper。多租户由 MP 拦截器在 final SQL 注入 tenant_id（V1 单租户 1001）。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-DETAIL-SPLIT-001
 */
@Mapper
public interface SowDetailAggMapper {

    /**
     * 累计总产仔数 = Σ total_born（缺 total_born 时退化 Σ(健仔+弱仔+死胎+木乃伊)）。
     * 无分娩记录返 0。
     */
    @Select("""
        SELECT COALESCE(SUM(
            COALESCE(total_born,
                COALESCE(healthy_male,0) + COALESCE(healthy_female,0)
              + COALESCE(weak_raised_male,0) + COALESCE(weak_raised_female,0)
              + COALESCE(weak_culled,0) + COALESCE(weak_born,0)
              + COALESCE(dead_born,0) + COALESCE(mummy_born,0))
        ), 0)
        FROM t_farm_pig_farrow
        WHERE pig_id = #{pigId} AND del_flag = '0'
        """)
    Integer sumTotalBorn(@Param("pigId") Long pigId);

    /**
     * 累计健仔数 = Σ(healthy_male + healthy_female)；两列全空（旧数据）时退化 Σ live_born。
     */
    @Select("""
        SELECT COALESCE(SUM(
            CASE WHEN healthy_male IS NULL AND healthy_female IS NULL
                 THEN COALESCE(live_born, 0)
                 ELSE COALESCE(healthy_male,0) + COALESCE(healthy_female,0)
            END
        ), 0)
        FROM t_farm_pig_farrow
        WHERE pig_id = #{pigId} AND del_flag = '0'
        """)
    Integer sumHealthy(@Param("pigId") Long pigId);

    /**
     * 累计活产数 = Σ live_born（断奶成活率分母）。无分娩返 0。
     */
    @Select("""
        SELECT COALESCE(SUM(COALESCE(live_born, 0)), 0)
        FROM t_farm_pig_farrow
        WHERE pig_id = #{pigId} AND del_flag = '0'
        """)
    Integer sumLiveBorn(@Param("pigId") Long pigId);

    /**
     * 平均窝重 kg = avg(total_weight)，仅统计 total_weight 非空的窝；全空返 null（mp 端降级）。
     */
    @Select("""
        SELECT AVG(total_weight)
        FROM t_farm_pig_farrow
        WHERE pig_id = #{pigId} AND del_flag = '0' AND total_weight IS NOT NULL
        """)
    BigDecimal avgLitterWeight(@Param("pigId") Long pigId);

    /**
     * 最大胎次 = max(parity)；无分娩返 null。
     */
    @Select("""
        SELECT MAX(parity)
        FROM t_farm_pig_farrow
        WHERE pig_id = #{pigId} AND del_flag = '0'
        """)
    Integer maxParity(@Param("pigId") Long pigId);

    /**
     * 累计断奶头数 = Σ weaning.weaned_count（断奶成活率分子）。无断奶返 0。
     */
    @Select("""
        SELECT COALESCE(SUM(COALESCE(weaned_count, 0)), 0)
        FROM t_farm_pig_weaning
        WHERE pig_id = #{pigId} AND del_flag = '0'
        """)
    Integer sumWeaned(@Param("pigId") Long pigId);

    /**
     * 最近一次分娩日期（NPD 基准之一）；无返 null。返 'YYYY-MM-DD' 字符串避免 LocalDateTime 序列化复杂。
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

    /**
     * 产仔性能按胎次聚合（堆叠柱 4 系列）：同 pig + parity 多行 SUM 汇总，按胎次升序。
     *
     * <p>健仔 = Σ(healthy_male+healthy_female)，两列全空退化 live_born；
     * 弱仔 = Σ(weak_raised_male+weak_raised_female+weak_culled+deformed_born)，全空退化 weak_born；
     * 死胎 = Σ dead_born；木乃伊 = Σ mummy_born。parity 为 null 的窝归一组（COALESCE 0）。</p>
     */
    @Select("""
        SELECT
            COALESCE(parity, 0) AS parity,
            SUM(CASE WHEN healthy_male IS NULL AND healthy_female IS NULL
                     THEN COALESCE(live_born, 0)
                     ELSE COALESCE(healthy_male,0) + COALESCE(healthy_female,0) END) AS healthy,
            SUM(CASE WHEN weak_raised_male IS NULL AND weak_raised_female IS NULL
                          AND weak_culled IS NULL AND deformed_born IS NULL
                     THEN COALESCE(weak_born, 0)
                     ELSE COALESCE(weak_raised_male,0) + COALESCE(weak_raised_female,0)
                        + COALESCE(weak_culled,0) + COALESCE(deformed_born,0) END) AS weak,
            SUM(COALESCE(dead_born, 0)) AS dead,
            SUM(COALESCE(mummy_born, 0)) AS mummy
        FROM t_farm_pig_farrow
        WHERE pig_id = #{pigId} AND del_flag = '0'
        GROUP BY COALESCE(parity, 0)
        ORDER BY COALESCE(parity, 0) ASC
        """)
    List<FarrowByParityVo> farrowByParity(@Param("pigId") Long pigId);
}
