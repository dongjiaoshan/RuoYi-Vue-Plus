package org.dromara.djs.breed.dashboard.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 年生产指标实体（BRD-DASH-001，表 {@code t_farm_year_production}）。
 *
 * <p>每个 tenant_id + stat_year 一行；由聚合 job 每天 00:30 重算当年。
 * 用于 admin dashboard 顶部"年度指标"区域。</p>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_year_production")
public class AnnualIndicator extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 统计年份。 */
    private Short statYear;

    /** 年度引种母猪数（Σ当年T-1前月表 introduce_count）。 */
    private Integer introduceCount;

    /** 年度引种公猪数（Σ当年T-1前月表 introduce_boar_count）。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer introduceBoarCount;

    private Integer bornCount;
    private Integer weanedCount;
    private Integer deathCount;
    private Integer cullingCount;
    private Integer marketingCount;
    private BigDecimal marketingWeight;

    /** PSY =（Σ日妊娠天数 / 母猪头日）×（365/115）× 窝均断奶数，单位 头/母猪·年（列 decimal(8,2)，不是百分比）。 */
    private BigDecimal psy;
    /** 死亡率（DEATH / (DEATH + ALIVE_END_OF_YEAR)，4 位小数）。 */
    private BigDecimal mortalityRate;

    // ---- row14 高级指标（BRD-STAT-001 扩列） ----
    /** 年均生产母猪存栏数（Σ日期末生产母猪头数/已历天数）。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(value = "avg_prod_sow_stock", updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgProdSowStock;
    /** 年配种头数（当年总配种次数）。 */
    private Integer breedingCount;
    /** 总产仔数（当年 Σ日总产仔）。 */
    private Integer totalBornCount;
    /** 年分娩次数（当年 Σ日分娩头数）。 */
    private Integer farrowCount;
    /** 总活仔数（当年 Σ日总活仔）。 */
    private Integer totalLiveBorn;
    /** 窝均活仔数（总活仔/年分娩次数）。 */
    private BigDecimal avgLiveBornPerLitter;
    /** 总断奶仔猪数（当年 Σ日断奶仔猪头数）。 */
    private Integer totalWeanedPiglet;
    /** 总断奶母猪头数（当年 Σ日断奶母猪头数）。 */
    private Integer totalWeanedSow;
    /** 窝均断奶数（总断奶仔猪/总断奶母猪）。 */
    private BigDecimal avgWeanedPerLitter;
    /** 当年断配间隔总天数。 */
    private Integer weanBreedTotalDays;
    /** 当年断配间隔总记录数。 */
    private Integer weanBreedTotalCount;
    /** 断配间隔（总天数/总记录数）。 */
    private BigDecimal weanBreedInterval;
    /** 全年总NPD天数（Σ日非生产母猪）。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer totalNpdDays;
    /** 年均NPD天数（总NPD/年均生产母猪存栏）。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgNpdDays;
    /**
     * 年分娩率分子 = {@code t_farm_farrowing_rate} 全年「分娩日期非空 <b>且 ≤ 预估分娩日</b>」的行数
     * （D-0091，甲方 2026-09-18；晚产窝不算）。定时重算，ALWAYS 覆盖旧值。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer yearBatchFarrowCount;
    /**
     * 年分娩率% = {@link #yearBatchFarrowCount} ÷ {@link #cohortMaturedCount} × 100。
     *
     * <p>两列出自对 {@code t_farm_farrowing_rate} 的<b>同一次</b>查询（D-0090 分母 / D-0091 分子），
     * 分子的行集是分母行集的子集，所以本列恒 ≤ 100（分母为 0 时 {@code ratio} 返回 0）。
     * 曾经有一个 &gt;100% 的告警分支，在换源之后已不可达，已删。</p>
     *
     * <p>⚠️ 这<b>不等于</b>「覆盖面问题消失了」：台账同样靠滚动窗刷新，窗外补录的配种/分娩进不来，
     * 分子分母会一起少、年值静默偏移。聚合时另有一条 live 底表 vs 台账 的交叉校验专门报这个。</p>
     */
    private BigDecimal yearFarrowRate;
    /**
     * 年分娩率<b>分母</b> = {@code t_farm_farrowing_rate} 全年「预估分娩日落在本年<b>且已到</b>
     * （≤ 收口日 T-1）」的行数（D-0090，甲方 2026-09-18「只算已到期的」）。
     *
     * <p>定时重算，ALWAYS 覆盖旧值。列名沿用 cohort 时代的叫法，但数据源已换成台账表。</p>
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer cohortMaturedCount;
    /** 统计区间起始日 = 当年日表实际覆盖的第一天（日表没铺满全年时，PSY/非生产天数只能按覆盖段算）。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate psyStatFrom;
    /**
     * 统计区间天数 = 该区间已落盘日表行数。定时重算，ALWAYS 覆盖旧值。
     *
     * <p>{@link #avgNpdDays} 的平均存栏按它取均值（区间值，不年化，D-0120）；{@link #psy} 式里的 365/115
     * 自带年化，区间长短只影响分子分母的采样量，不进乘数。两格共用本字段作「数据取自哪一段」的说明。</p>
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer psyStatDays;
    /** 平均出栏重（Σ日出栏总重/Σ日出栏头数）。 */
    private BigDecimal avgMarketingWeight;
    /** 产房损失率%（Σ本窝哺乳期死淘数 / Σ本窝活仔数 × 100，只统计已断奶的窝）。 */
    private BigDecimal farrowLossRate;
    /** 肥猪死亡数（当年 Σ日 death_fattening_count）。 */
    private Integer totalFatteningDeath;

    @TableLogic
    private String delFlag;
    private Long delUnique;
}
