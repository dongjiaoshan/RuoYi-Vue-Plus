package org.dromara.djs.breed.dashboard.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

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

    private Integer introduceCount;
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
    /** 年均能繁母猪存栏数（(Σ日生产母猪+Σ日230后备)/已历天数）。 */
    private BigDecimal avgBreedingSowStock;
    /** 年配种头数（当年总配种次数）。 */
    private Integer breedingCount;
    /** 年配种率（Σ日配种次数/年均能繁母猪存栏）。 */
    private BigDecimal breedRate;
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
    /** 全年总NPD天数（Σ日230后备+Σ日非生产母猪）。 */
    private Integer totalNpdDays;
    /** 年均NPD天数（总NPD/年均能繁存栏）。 */
    private BigDecimal avgNpdDays;
    /** 年分娩头数（Σ日当年配种批次分娩头数）。 */
    private Integer yearBatchFarrowCount;
    /** 年分娩率%（年分娩头数/年配种头数×100）。 */
    private BigDecimal yearFarrowRate;
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
