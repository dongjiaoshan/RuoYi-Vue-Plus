package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 母猪详情「母猪性能」6 KPI 出参（BRD-FIX-MP-DETAIL-SPLIT-001 / DJS-FIX-6-14，原型 母猪详情）。
 *
 * <p>mp 端母猪详情页顶部 6 格 KPI 网格用。**从零按本母猪分娩 / 断奶 / 配种记录实时聚合**
 * （Kevin 2026-05-30 D2 拍板：本期 BE 从零建聚合，非读统计预聚合表）。</p>
 *
 * <p>每个指标都可空——数据源缺失时该字段返 {@code null}，mp 端对应格子显 {@code —}（不渲染 NaN）。不抛异常。</p>
 *
 * <p>口径（V1 粗算，客户验收 sense check 用；对齐原型 6 指标）：</p>
 * <ul>
 *   <li>{@link #avgGestationDays} 平均怀孕天数 = avg(分娩日期 − 关联配种日期)；无配种-分娩配对 → null；</li>
 *   <li>{@link #weanToBreedDays} 断奶-配种天数 = avg(配种日期 − 上一次断奶日期)；无配对 → null；</li>
 *   <li>{@link #npdTotalDays} NPD 总天数 = 累计非生产天数（今天 − 最近配种/分娩较晚者，V1 粗口径）；缺基准 → null；</li>
 *   <li>{@link #nullReturnTotal} 返空流总数 = 该母猪返情 + 空怀 + 流产事件累计次数（t_farm_pig_abnormal）；</li>
 *   <li>{@link #avgBornPerLitter} 窝均产仔数 = Σ total_born / 分娩窝数；无分娩 → null；</li>
 *   <li>{@link #avgWeanedPerLitter} 窝均断奶数 = Σ weaning.weaned_count / 断奶批数；无断奶 → null。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-FIX-MP-DETAIL-SPLIT-001
 */
@Data
public class SowPerformanceMpVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 平均怀孕天数（avg 分娩−配种）；无配对 → null。 */
    private BigDecimal avgGestationDays;

    /** 断奶-配种天数（avg 配种−上次断奶）；无配对 → null。 */
    private BigDecimal weanToBreedDays;

    /** NPD 总天数（累计非生产天数，V1 粗口径）；缺基准 → null。 */
    private Integer npdTotalDays;

    /** 返空流总数（返情 + 空怀 + 流产累计次数）；无异常事件 → 0。 */
    private Integer nullReturnTotal;

    /** 窝均产仔数（Σ total_born / 分娩窝数）；无分娩 → null。 */
    private BigDecimal avgBornPerLitter;

    /** 窝均断奶数（Σ weaned_count / 断奶批数）；无断奶 → null。 */
    private BigDecimal avgWeanedPerLitter;
}
