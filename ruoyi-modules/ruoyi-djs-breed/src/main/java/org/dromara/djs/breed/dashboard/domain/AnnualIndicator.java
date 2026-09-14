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

    /** PSY = 年度断奶头数 / 当年平均母猪存栏（4 位小数，前端 % 显示）。 */
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
    /** 年分娩头数（到期批次中在判定节点内分娩的头数 = 年分娩率分子）。 */
    private Integer yearBatchFarrowCount;
    /** 年分娩率%（年分娩头数/到期批次数×100，配种批次口径）。 */
    private BigDecimal yearFarrowRate;
    /** 年分娩率分母：判定节点落在本年且已到期的配种批次数。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer cohortMaturedCount;
    /** PSY/非生产天数年化的统计区间起始日。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate psyStatFrom;
    /** PSY/非生产天数年化的统计区间天数（年化乘数 365/该值）。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer psyStatDays;
    /** 平均出栏重（Σ日出栏总重/Σ日出栏头数）。 */
    private BigDecimal avgMarketingWeight;
    /** 分娩舍损失率（当年死亡仔猪数/总活仔数）。 */
    private BigDecimal farrowLossRate;
    /** 肥猪死亡数（当年 Σ日 death_fattening_count）。 */
    private Integer totalFatteningDeath;

    @TableLogic
    private String delFlag;
    private Long delUnique;
}
