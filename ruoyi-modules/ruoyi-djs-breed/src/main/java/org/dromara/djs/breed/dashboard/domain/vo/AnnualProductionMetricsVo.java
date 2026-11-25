package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/** 两个年表接口共用的统计覆盖和年指标字段。 */
@Data
public abstract class AnnualProductionMetricsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 实际首份、末份日报日期（含），格式 yyyy-MM-dd。 */
    private String psyStatFrom;
    private String psyStatTo;
    /** 实际有日报的天数，允许区间内缺日报。 */
    private Integer psyStatDays = 0;

    private BigDecimal avgProdSowStock;
    private BigDecimal avgFatteningStock;
    /** 年生产母猪淘汰率（百分比）。 */
    private BigDecimal productionSowCullRate;
    /** 年育肥猪淘汰率、死亡率（百分比）。 */
    private BigDecimal fatteningCullRate;
    private BigDecimal fatteningDeathRate;
    /** 统计区间内每头生产母猪实际分娩窝数。 */
    private BigDecimal farrowingsPerSow;
    private BigDecimal feedWeightKg;
    private BigDecimal organicFeedWeightKg;
    private Integer vaccinePigTimes;
    private Integer healthPigTimes;
    private Integer treatmentPigTimes;
    private BigDecimal avgMarketingAgeDays;
    private Integer marketingMissingBirthCount;
    private BigDecimal avgGestationDays;
}
