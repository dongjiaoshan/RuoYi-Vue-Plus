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

/**
 * 养殖月指标统计实体（BRD-DASH-001，表 {@code t_farm_monthly_production}）。
 *
 * <p>每个 tenant_id + stat_month (CHAR(7) YYYY-MM) 一行；
 * 由 {@link org.dromara.djs.breed.dashboard.job.DashboardAggregateJob} 每天 00:30 重算当月（INSERT ON DUPLICATE KEY UPDATE）。
 * 月度对比（当月 vs 上月）的数据源。</p>
 *
 * <p><b>颜色规则（注意国内畜牧惯例反直觉）</b>：
 * 当月数值"更好" → 红色 ("better")；
 * 当月数值"更差" → 绿色 ("worse")；
 * 持平 → 黑色 ("flat")。
 * 颜色映射在 VO 层做，不持久化。</p>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_monthly_production")
public class MonthlyProduction extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 统计月份（CHAR(7) YYYY-MM）。 */
    private String statMonth;

    /** 当月引种母猪数（Σ当月T-1前日表 introduce_sow_count）。 */
    private Integer introduceCount;
    /** 当月引种公猪数（Σ当月T-1前日表 introduce_boar_count）。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer introduceBoarCount;
    /** 当月总活仔数（Σ当月T-1前日表 live_born_count）。 */
    private Integer bornCount;
    /** 月度断奶头数（t_farm_pig_weaning SUM(weaned_count)）。 */
    private Integer weanedCount;
    /** 月度死亡头数（status_record event_type='DIE'）。 */
    private Integer deathCount;
    /** 月度淘汰头数（status_record event_type='ELIMINATE'）。 */
    private Integer cullingCount;
    /** 月度出栏头数（status_record event_type='SLAUGHTER' or pig_marketing COUNT）。 */
    private Integer marketingCount;
    /** 月度出栏总重（kg）。 */
    private BigDecimal marketingWeight;

    // ---- row13 高级指标（BRD-STAT-001 扩列） ----
    /** 当月累计匹配配种窝数（每日 当天−114 配种母猪数累加）。 */
    private Integer mateLitterCount;
    /** 分娩率%（当月分娩头数/累计匹配配种窝数×100）。 */
    private BigDecimal farrowRate;
    /** 配种率%（当月配种母猪数/((Σ日生产母猪+Σ日230后备)/当月天数)×100）。 */
    private BigDecimal breedRate;
    /** 断配间隔（当月断到配天数之和/完成断到配母猪头数）。 */
    private BigDecimal weanBreedInterval;
    /** 返空流头数（当月返空流母猪头数）。 */
    private Integer abnormalCount;
    /** 月均生产母猪存栏数（Σ日期末生产母猪头数/当月已历天数）。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgProdSowStock;
    /** 月均NPD天数（Σ日非生产母猪 / 月均生产母猪存栏）。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal npdDays;
    /** 总产仔数（当月 SUM total_born）。 */
    private Integer totalBornCount;
    /** 窝均总产仔数（总产仔/分娩母猪头数）。 */
    private BigDecimal avgBornPerLitter;
    /** 窝均活仔（总活仔/分娩母猪头数）。 */
    private BigDecimal avgLiveBornPerLitter;
    /** 窝均断奶（断奶仔猪数/断奶母头数）。 */
    private BigDecimal avgWeanedPerLitter;
    /** 分娩舍损失率（Σ当月T-1前日死亡仔猪数/当月总活仔数 born_count）。 */
    private BigDecimal farrowLossRate;

    @TableLogic
    private String delFlag;
    private Long delUnique;
}
