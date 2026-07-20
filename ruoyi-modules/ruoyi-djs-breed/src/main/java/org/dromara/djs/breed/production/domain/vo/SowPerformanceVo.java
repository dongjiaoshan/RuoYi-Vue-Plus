package org.dromara.djs.breed.production.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.production.domain.SowPerformance;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 母猪生产指标视图（BRD-LIST-001 详情 tab2）。
 *
 * @author djs
 * @since BRD-LIST-001
 */
@Data
@AutoMapper(target = SowPerformance.class)
public class SowPerformanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;
    private Integer parity;
    private Integer totalBorn;
    private Integer totalLiveBorn;
    private Integer totalWeaned;
    private BigDecimal avgBornWeight;
    private BigDecimal avgWeanedWeight;
    private LocalDate lastUpdateDate;

    // ---- row11 指标算法列（BRD-STAT-001） ----
    /** 平均怀孕天数。 */
    private BigDecimal avgGestationDays;
    /** 断奶-配种天数。 */
    private BigDecimal weanBreedDays;
    /** 返空流总次数。 */
    private Integer abnormalTotal;
    /** 窝均产仔数。 */
    private BigDecimal avgBornPerLitter;
    /** 窝均活仔数。 */
    private BigDecimal avgLiveBornPerLitter;
    /** 窝均断奶数。 */
    private BigDecimal avgWeanedPerLitter;
    /** NPD。 */
    private BigDecimal npd;
}
