package org.dromara.djs.breed.dashboard.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.dashboard.domain.FarmIndicatorRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 养殖农场日数据记录出参（BRD-STAT-001，{@code t_farm_indicator_record} 的镜像）。
 *
 * <p>按 stat_date 范围查询的只读端点出参；mp/admin 看历史日表用。字段逐字对齐 row10。</p>
 *
 * @author djs
 * @since BRD-STAT-001
 */
@Data
@AutoMapper(target = FarmIndicatorRecord.class)
public class FarmIndicatorRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。 */
    private Long id;

    /** 统计日期。 */
    private LocalDate statDate;

    // ---- 当日事件类（头数） ----
    /** 分娩母猪数。 */
    private Integer farrowSowCount;
    /** 配种母猪数。 */
    private Integer breedingSowCount;
    /** 断奶母猪数。 */
    private Integer weaningSowCount;
    /** 返空流母猪数。 */
    private Integer abnormalSowCount;
    /** 引种母猪数。 */
    private Integer introduceSowCount;
    /** 查情不配种数。 */
    private Integer heatNoBreedCount;
    /** 死亡猪只数。 */
    private Integer deathPigCount;
    /** 淘汰猪只数。 */
    private Integer cullingPigCount;
    /** 产仔数。 */
    private Integer totalBornCount;
    /** 活仔数。 */
    private Integer liveBornCount;
    /** 仔猪打标数。 */
    private Integer pigletTagCount;
    /** 断奶仔猪数。 */
    private Integer weanedPigletCount;
    /** 生长记录数。 */
    private Integer growthRecordCount;
    /** 阉割猪只数。 */
    private Integer castratePigCount;
    /** 用药猪只数。 */
    private Integer medicatedPigCount;
    /** 出栏猪只数量。 */
    private Integer marketingPigCount;
    /** 死亡肥猪数。 */
    private Integer deathFatteningCount;
    /** 死亡种母猪数。 */
    private Integer deathSowCount;
    /** 死亡仔猪数。 */
    private Integer deathPigletCount;

    // ---- 当日出栏/生长重量类 ----
    /** 平均出栏重。 */
    private BigDecimal avgMarketingWeight;
    /** 出栏总重。 */
    private BigDecimal marketingWeight;
    /** 猪只断奶总重。 */
    private BigDecimal weanTotalWeight;
    /** 生长总天数。 */
    private Integer growthTotalDays;
    /** 净增重。 */
    private BigDecimal netGainWeight;
    /** 日增重。 */
    private BigDecimal dailyGainWeight;
    /** 平均背膘厚。 */
    private BigDecimal avgBackfatThickness;

    // ---- 期末存栏快照类 ----
    /** 期末生产母猪头数。 */
    private Integer endProductionSowCount;
    /** 期末种公猪数。 */
    private Integer endBoarCount;
    /** 期末肥猪头数。 */
    private Integer endFatteningCount;
    /** 期末仔猪头数。 */
    private Integer endPigletCount;
    /** 期末后备猪头数。 */
    private Integer endReserveCount;
    /** 期末 230 日龄以上后备猪头数。 */
    private Integer endReserve230Count;
    /** 期末非生产状态母猪头数。 */
    private Integer endNonprodSowCount;

    // ---- 配种批次回溯 ----
    /** 当年配种批次分娩头数。 */
    private Integer yearBatchFarrowCount;
}
