package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 母猪详情「母猪性能」KPI 出参（BRD-FIX-MP-DETAIL-SPLIT-001 / 邓博 row11，原型 母猪详情）。
 *
 * <p>mp 端母猪详情页「母猪性能」KPI 网格用。字段对齐 {@code t_farm_sow_performance}（定时任务填的
 * row11 12 项指标）。{@link org.dromara.djs.breed.core.service.impl.SowDetailServiceImpl#querySowPerformance}
 * **优先读** {@code t_farm_sow_performance} 最新一行映射；该母猪还没被定时任务覆盖到（行不存在）时
 * fallback 到当场实时聚合，保证不空白。</p>
 *
 * <p>每个指标都可空——数据源缺失时该字段返 {@code null}，mp 端对应格子显 {@code —}（不渲染 NaN）。不抛异常。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-DETAIL-SPLIT-001
 */
@Data
public class SowPerformanceMpVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 平均怀孕天数（{@code avg_gestation_days}）；无 → null。 */
    private BigDecimal avgGestationDays;

    /** 断奶-配种天数（{@code wean_breed_days}）；无 → null。 */
    private BigDecimal weanBreedDays;

    /** NPD（{@code npd}，列类型 DECIMAL）；无 → null。 */
    private BigDecimal npd;

    /** 返空流总次数（{@code abnormal_total}）；无 → 0。 */
    private Integer abnormalTotal;

    /** 窝均产仔数（{@code avg_born_per_litter}）；无 → null。 */
    private BigDecimal avgBornPerLitter;

    /** 窝均活仔数（{@code avg_live_born_per_litter}）；无 → null。 */
    private BigDecimal avgLiveBornPerLitter;

    /** 窝均断奶数（{@code avg_weaned_per_litter}）；无 → null。 */
    private BigDecimal avgWeanedPerLitter;

    /** 累计产仔数（{@code total_born}）；无 → null。 */
    private Integer totalBorn;

    /** 累计活仔数（{@code total_live_born}）；无 → null。 */
    private Integer totalLiveBorn;

    /** 累计断奶数（{@code total_weaned}）；无 → null。 */
    private Integer totalWeaned;

    /** 平均出生重 kg（{@code avg_born_weight}）；无 → null。 */
    private BigDecimal avgBornWeight;

    /** 平均断奶重 kg（{@code avg_weaned_weight}）；无 → null。 */
    private BigDecimal avgWeanedWeight;
}
