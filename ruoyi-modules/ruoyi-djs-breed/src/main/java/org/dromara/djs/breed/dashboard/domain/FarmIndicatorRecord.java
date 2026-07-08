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
 * 养殖农场日数据记录实体（BRD-STAT-001，表 {@code t_farm_indicator_record}）。
 *
 * <p>每个 tenant_id + stat_date 一行；由 {@link org.dromara.djs.breed.dashboard.job.DashboardAggregateJob}
 * 每晚跑批落盘 T-1 全量养殖日指标（UPSERT 幂等）。mp「种猪场活动统计」读历史 + 落盘数据。</p>
 *
 * <p><b>与实时端点的关系</b>：今日实时聚合仍走 {@code DashboardServiceImpl.getDailyOverview} 等
 * 实时方法（保持不动），本表供历史 + 定时落盘用，两套并存。</p>
 *
 * <p>字段逐字对齐邓博 admin 测试表 row10。</p>
 *
 * @author djs
 * @since BRD-STAT-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_indicator_record")
public class FarmIndicatorRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 统计日期（T-1）。 */
    private LocalDate statDate;

    // ---- 当日事件类（头数） ----
    /** 分娩母猪数（当日分娩记录的母猪数）。 */
    private Integer farrowSowCount;
    /** 配种母猪数（当日配种记录的母猪数）。 */
    private Integer breedingSowCount;
    /** 断奶母猪数（当日断奶记录的母猪数）。 */
    private Integer weaningSowCount;
    /** 返空流母猪数（当日返空流记录数）。 */
    private Integer abnormalSowCount;
    /** 引种母猪数（当日内外部引种头数）。 */
    private Integer introduceSowCount;
    /** 引种公猪数（当日外部引种公猪头数，external AND pig_sex='M'）。定时重算，ALWAYS 覆盖旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer introduceBoarCount;
    /** 查情不配种数（当日查情但 3 日内未配种的母猪数）。 */
    private Integer heatNoBreedCount;
    /** 死亡猪只数（当日 DIE 事件数）。 */
    private Integer deathPigCount;
    /** 淘汰猪只数（当日 ELIMINATE 事件数）。 */
    private Integer cullingPigCount;
    /** 产仔数（当日总产仔数 SUM total_born）。 */
    private Integer totalBornCount;
    /** 活仔数（当日总活仔数 SUM live_born）。 */
    private Integer liveBornCount;
    /** 仔猪打标数（当日打标记录数）。 */
    private Integer pigletTagCount;
    /** 断奶仔猪数（当日断奶头数 SUM weaned_count）。 */
    private Integer weanedPigletCount;
    /** 生长记录数（当日生长记录录入数）。 */
    private Integer growthRecordCount;
    /** 阉割猪只数（当日 CASTRATE 事件数）。 */
    private Integer castratePigCount;
    /** 用药猪只数（当日有用药记录的猪只数 DISTINCT pig_id）。 */
    private Integer medicatedPigCount;
    /** 出栏猪只数量（当日出栏头数）。 */
    private Integer marketingPigCount;
    /** 死亡肥猪数（当日 DIE 且 pig_type=fattening）。 */
    private Integer deathFatteningCount;
    /** 死亡种母猪数（当日 DIE 且 pig_type=sow）。 */
    private Integer deathSowCount;
    /** 死亡仔猪数（当日 DIE 且 pig_type=piglet）。 */
    private Integer deathPigletCount;

    // ---- 当日出栏/生长重量类 ----
    /** 平均出栏重（出栏总重/出栏头数）。 */
    private BigDecimal avgMarketingWeight;
    /** 出栏总重（当日出栏猪只总重）。 */
    private BigDecimal marketingWeight;
    /** 猪只断奶总重（当日出栏猪只断奶时总重）。 */
    private BigDecimal weanTotalWeight;
    /** 生长总天数（Σ 当日出栏猪 出栏日−出生日+1；出生日为空的外购猪跳过累加）。 */
    private Integer growthTotalDays;
    /** 饲养总天数（Σ 当日出栏猪 出栏日−断奶日+1）；日增重分母。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer feedTotalDays;
    /** 净增重（出栏总重−断奶总重）。 */
    private BigDecimal netGainWeight;
    /** 日增重（净增重/饲养总天数）。 */
    private BigDecimal dailyGainWeight;
    /** 平均背膘厚（当日出栏猪背膘厚之和/有背膘的肥猪数）。 */
    private BigDecimal avgBackfatThickness;

    // ---- 期末存栏快照类（T-1 当日 current_status 快照） ----
    /** 期末生产母猪头数（期末非后备非终止种母猪存栏）。 */
    private Integer endProductionSowCount;
    /** 期末种公猪数。 */
    private Integer endBoarCount;
    /** 期末肥猪头数。 */
    private Integer endFatteningCount;
    /** 期末仔猪头数。 */
    private Integer endPigletCount;
    /** 期末后备猪头数（种母猪 HB）。 */
    private Integer endReserveCount;
    /** 期末 230 日龄以上后备猪头数。 */
    @TableField("end_reserve_230_count")  // MP 默认会把 230 拼成 end_reserve230_count，与迁移列名不符，显式指定
    private Integer endReserve230Count;
    /** 期末非生产状态母猪头数（空怀/返情/流产/断奶）。 */
    private Integer endNonprodSowCount;

    // ---- 配种批次回溯 ----
    /** 当年配种批次分娩头数（分娩日−114 天落当年则累加）。 */
    private Integer yearBatchFarrowCount;

    // ---- NPD（row112） ----
    /** 日NPD天数（当日非生产状态母猪头数 = endNonprodSowCount 同值）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer npdDays;

    /** 软删标志（业务表必含）。 */
    @TableLogic
    private String delFlag;
    /** 软删唯一标识。 */
    private Long delUnique;
}
