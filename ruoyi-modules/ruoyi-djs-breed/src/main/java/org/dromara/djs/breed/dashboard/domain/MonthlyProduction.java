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
    /**
     * 当月累计匹配配种窝数 = 判定节点（配种日 + judgeDays）落在本月的配种批次数，也是分娩率分母。
     *
     * <p>与 {@link #cohortFarrowCount} 出自对 {@code t_farm_farrowing_rate} 的同一次查询，故分子 ⊆ 分母。</p>
     *
     * <p>⚠️ 年表的年分娩率<b>不再</b> Σ 这两列（D-0087 已被 D-0090/D-0091 取代）：它直接扫台账整年，
     * 免得月表缺行时年值静默少报。本列与 Σ月表的差异现在只用于对账告警。</p>
     */
    private Integer mateLitterCount;
    /** 分娩率%（本月到期行的按期分娩数/到期行数×100，取自 {@code t_farm_farrowing_rate}）。 */
    private BigDecimal farrowRate;
    /** 分娩率分子：本月到期的配种批次中，在判定节点内分娩的头数。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer cohortFarrowCount;
    /** 配种率%（当月配种母猪数/((Σ日生产母猪+Σ日230后备)/当月天数)×100）。 */
    private BigDecimal breedRate;
    /** 断配间隔（当月断到配天数之和/完成断到配母猪头数）。 */
    private BigDecimal weanBreedInterval;
    /** 返空流头数（当月返空流母猪头数）。 */
    private Integer abnormalCount;
    /** 月均生产母猪存栏数（Σ日期末生产母猪头数/当月已历天数）。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgProdSowStock;
    /** 当月NPD天数（Σ当月日非生产母猪头数 + Σ当月妊娠损失天数）= NPD 分子，D-0099。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer monthNpdDays;
    /** 月头均NPD天数（当月NPD天数 / 月均生产母猪存栏）。定时重算，ALWAYS 覆盖旧值。 */
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
    /** 当月已断奶生母窝的哺乳期实际仔猪死淘数 / 这些窝的分娩活仔数（%）。 */
    private BigDecimal farrowLossRate;

    /** 按日去重的月分娩总数。 */
    private Integer farrowSowCount;

    /** 当月后备首配头数。 */
    private Integer reserveFirstBreedCount;

    /** 当月头胎分娩母猪数。 */
    private Integer firstParityFarrowSowCount;

    /** 按母猪实际断奶日归月的断奶窝数。 */
    private Integer weaningSowCount;

    /** 正常断奶窝数（排除寄养并窝和零仔窝）。 */
    private Integer normalWeaningSowCount;

    /** 当月断奶窝哺乳天数合计。 */
    private Integer lactationDaysSum;

    /** 可配对有效分娩日的断奶窝数。 */
    private Integer lactationValidLitterCount;

    /** 按断奶月归属的断奶母猪平均哺乳天数。 */
    private BigDecimal avgLactationDaysWeaned;

    /** 按分娩月归属的已断奶窝哺乳天数合计。 */
    private Integer lactationDaysSumByFarrow;

    /** 按分娩月归属的分娩母猪平均哺乳天数。 */
    private BigDecimal avgLactationDaysFarrowed;

    /** 归属当月实际断奶生母窝的仔猪总数（含以后月份的分批断奶）。 */
    private Integer birthLitterWeanedPigletCount;

    /** 按生母窝计算的窝均断奶仔猪数。 */
    private BigDecimal avgBirthLitterWeanedPiglets;

    /** 各日按母猪去重的流产数之和。 */
    private Integer abortionCount;

    /** 有效断奶仔猪日龄合计（天）。 */
    private Integer weanAgeDaysSum;

    /** 有效断奶仔猪日龄头数。 */
    private Integer weanAgeValidCount;

    /** 加权平均断奶日龄（天）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgWeanAgeDays;

    /** 正常育肥猪出栏均重（kg/头）。 */
    private BigDecimal avgMarketingWeight;

    // 养殖月指标 V1.2。
    /** 养殖 44 当月去重配种母猪数。 */
    private Integer breedingSowCount;

    /** 养殖 45 异常后重复配种母猪数。 */
    private Integer repeatBreedingSowCount;

    /** 养殖 46 重复配种比例（%）。 */
    private BigDecimal repeatBreedingRate;

    /** 养殖 48 进场至首配天数合计。 */
    private Integer entryFirstBreedDaysSum;

    /** 养殖 48 进场至首配有效头数。 */
    private Integer entryFirstBreedValidCount;

    /** 养殖 48 平均进场至首配间隔（天）。 */
    private BigDecimal avgEntryFirstBreedDays;

    /** 养殖 49 后备初配日龄合计。 */
    private Integer reserveFirstBreedAgeDaysSum;

    /** 养殖 49 后备初配有效日龄头数。 */
    private Integer reserveFirstBreedAgeValidCount;

    /** 养殖 49 后备平均初配日龄（天）。 */
    private BigDecimal avgReserveFirstBreedAgeDays;

    /** 养殖 50 断配间隔有效天数合计。 */
    private Integer weanBreedDaysSum;

    /** 养殖 50 断配间隔有效配对数。 */
    private Integer weanBreedValidCount;

    /** 养殖 52 53 窝均总仔和活仔所用日表分娩窝数。 */
    private Integer dailyFarrowSowCount;

    /** 养殖 53 窝均活仔所用日表活仔数。 */
    private Integer dailyLiveBornCount;

    /** 养殖 57 有效分娩胎次合计。 */
    private Integer farrowParitySum;

    /** 养殖 57 有效分娩胎次记录数。 */
    private Integer farrowParityValidCount;

    /** 养殖 57 平均分娩胎次。 */
    private BigDecimal avgFarrowParity;

    /** 养殖 58 活仔不超过7头的分娩窝数。 */
    private Integer smallLiveBornLitterCount;

    /** 养殖 58 活仔不超过7头的分娩窝占比（%）。 */
    private BigDecimal smallLiveBornLitterRate;

    /** 养殖 59 60 106 健仔数，未记录为NULL。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer healthyBornCount;

    /** 养殖 59 有健仔登记的分娩单数（含实录0）。 */
    private Integer healthyBornRecordCount;

    /** 养殖 60 窝均健仔数。 */
    private BigDecimal avgHealthyBornPerLitter;

    /** 养殖 61 62 死胎头数。 */
    private Integer deadBornCount;

    /** 养殖 61 死胎率（%）。 */
    private BigDecimal deadBornRate;

    /** 养殖 62 窝均死胎数。 */
    private BigDecimal avgDeadBornPerLitter;

    /** 养殖 63 64 木乃伊头数。 */
    private Integer mummyBornCount;

    /** 养殖 63 木乃伊率（%）。 */
    private BigDecimal mummyBornRate;

    /** 养殖 64 窝均木乃伊数。 */
    private BigDecimal avgMummyBornPerLitter;

    /** 养殖 61 63 108 分娩单总产仔数。 */
    private Integer farrowBornCount;

    /** 养殖 65 有效分娩的怀孕天数合计。 */
    private Integer gestationDaysSum;

    /** 养殖 65 有有效本胎配种日的分娩记录数。 */
    private Integer gestationValidCount;

    /** 养殖 65 平均怀孕天数。 */
    private BigDecimal avgGestationDays;

    /** 养殖 66 已称窝的出生总重（kg）。 */
    private BigDecimal birthWeightSum;

    /** 养殖 66 已称窝活仔数。 */
    private Integer birthWeightPigletCount;

    /** 养殖 66 平均初生重（kg/头）。 */
    private BigDecimal avgBirthWeight;

    /** 养殖 69 101 当月断奶仔猪总重（kg）。 */
    private BigDecimal weanedWeightSum;

    /** 养殖 69 101 断奶均重（kg/头）。 */
    private BigDecimal avgWeanedWeight;

    /** 养殖 72 当月实际断奶窝已关联哺乳期仔猪死淘数。 */
    private Integer farrowLossCount;

    /** 养殖 72 当月实际断奶窝活仔数。 */
    private Integer farrowLossLiveBornCount;

    /** 养殖 72 当月登记在业务日但无法确认出生窝的仔猪死淘数。 */
    private Integer unlinkedPigletDeathCullCount;

    /** 养殖 75 出栏育肥猪有效日龄合计。 */
    private Integer marketingAgeDaysSum;

    /** 养殖 75 出栏育肥猪有效日龄头数。 */
    private Integer marketingAgeValidCount;

    /** 养殖 75 出栏育肥猪缺出生日头数。 */
    private Integer marketingMissingBirthCount;

    /** 养殖 75 平均出栏日龄（天）。 */
    private BigDecimal avgMarketingAgeDays;

    /** 养殖 76 有效出栏育肥猪断奶后增重合计（kg）。 */
    private BigDecimal marketingNetGainKg;

    /** 养殖 76 有效出栏育肥猪断奶后饲养天数合计。 */
    private Integer marketingGainDays;

    /** 养殖 76 出栏育肥猪日增重（g/天）。 */
    private BigDecimal avgDailyGainGrams;

    /** 养殖 77 80 98 当月按实际死亡日的仔猪死亡数。 */
    private Integer pigletDeathCount;

    /** 养殖 78 110 生产母猪淘汰头数。 */
    private Integer productionSowCullCount;

    /** 养殖 78 生产母猪淘汰比率（%）。 */
    private BigDecimal productionSowCullRate;

    /** 养殖 79 生产母猪死亡头数。 */
    private Integer productionSowDeathCount;

    /** 养殖 79 生产母猪死亡率（%）。 */
    private BigDecimal productionSowDeathRate;

    /** 养殖 80 上月最后自然日报的期末已建档仔猪数，缺报为NULL。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer openingPigletStock;

    /** 养殖 80 分娩舍死亡率分母（本月活仔加月初已建档仔猪）。 */
    private Integer farrowingHousePigletCount;

    /** 养殖 80 分娩舍死亡率（%）。 */
    private BigDecimal farrowingHouseDeathRate;

    /** 养殖 81 育肥猪死亡头数。 */
    private Integer fatteningDeathCount;

    /** 养殖 81 育肥猪死亡率（%）。 */
    private BigDecimal fatteningDeathRate;

    /** 养殖 82 育肥猪淘汰头数。 */
    private Integer fatteningCullCount;

    /** 养殖 82 育肥猪淘汰率（%）。 */
    private BigDecimal fatteningCullRate;

    /** 养殖 81 82 各日末育肥存栏之和。 */
    private Integer fatteningStockSum;

    /** 养殖 81 82 月平均育肥存栏。 */
    private BigDecimal avgFatteningStock;

    /** 养殖 83 当月喂猪饲料消耗量（kg）。 */
    private BigDecimal feedWeightKg;

    /** 养殖 84 当月喂猪有机饲料消耗量（kg）。 */
    private BigDecimal organicFeedWeightKg;

    /** 养殖 85 疫苗头次数。 */
    private Integer vaccinePigTimes;

    /** 养殖 86 保健头次数。 */
    private Integer healthPigTimes;

    /** 养殖 87 治疗头次数。 */
    private Integer treatmentPigTimes;

    /** 养殖 91 月死亡与淘汰数量之和。 */
    private Integer deathCullCount;

    /** 养殖 93 当月按日去重后返情头数之和。 */
    private Integer returnHeatCount;

    /** 养殖 94 当月按日去重后空怀头数之和。 */
    private Integer emptySowCount;

    /** 养殖 96 当月最后一份日报期末生产母猪存栏。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer endProductionSowStock;

    /** 养殖 96 月末生产母猪存栏的实际日报日期。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate endProductionSowStockDate;

    /** 养殖 97 各日末生产母猪存栏之和。 */
    private Integer productionSowStockSum;

    /** 养殖 97 当月有日报的自然日数。 */
    private Integer indicatorDayCount;

    /** 养殖 98 死亡仔猪有效日龄合计。 */
    private Integer pigletDeathAgeDaysSum;

    /** 养殖 98 死亡仔猪有效日龄头数。 */
    private Integer pigletDeathAgeValidCount;

    /** 养殖 98 死亡仔猪缺出生日头数。 */
    private Integer pigletDeathMissingBirthCount;

    /** 养殖 98 仔猪死亡平均日龄（天）。 */
    private BigDecimal avgPigletDeathAgeDays;

    /** 养殖 103 断奶转移仔猪有效日龄合计。 */
    private Integer weaningTransferAgeDaysSum;

    /** 养殖 103 断奶转移仔猪有效日龄头数。 */
    private Integer weaningTransferAgeValidCount;

    /** 养殖 103 平均断奶转移日龄（天）。 */
    private BigDecimal avgWeaningTransferAgeDays;

    /** 养殖 104 断奶转移个体有效重量合计（kg）。 */
    private BigDecimal weaningTransferWeightSum;

    /** 养殖 104 断奶转移个体有效称重头数。 */
    private Integer weaningTransferWeightValidCount;

    /** 养殖 104 断奶转移均重（kg/头）。 */
    private BigDecimal avgWeaningTransferWeight;

    /** 养殖 106 有健仔登记的同批总产仔数。 */
    private Integer healthyBornTotalCount;

    /** 养殖 106 健仔率（%）。 */
    private BigDecimal healthyBornRate;

    /** 养殖 107 108 留养弱仔和处死弱仔合计。 */
    private Integer weakBornCount;

    /** 养殖 107 窝均弱仔数。 */
    private BigDecimal avgWeakBornPerLitter;

    /** 养殖 108 弱仔率（%）。 */
    private BigDecimal weakBornRate;

    /** 养殖 109 当月日表新进生产母猪数之和。 */
    private Integer newProductionSowCount;

    /** 养殖 109 母猪更新率（%）。 */
    private BigDecimal productionSowReplacementRate;

    /** 养殖 110 淘汰生产母猪最后有效胎次合计。 */
    private Integer culledSowParitySum;

    /** 养殖 110 有胎次数据的淘汰生产母猪头数。 */
    private Integer culledSowParityValidCount;

    /** 养殖 110 淘汰生产母猪平均胎次。 */
    private BigDecimal avgCulledSowParity;

    /** 养殖 111 按母猪去重的月妊娠损失头数。 */
    private Integer pregnancyLossSowCount;

    /** 养殖 111 妊娠损失率（%）。 */
    private BigDecimal pregnancyLossRate;

    @TableLogic
    private String delFlag;
    private Long delUnique;
}
