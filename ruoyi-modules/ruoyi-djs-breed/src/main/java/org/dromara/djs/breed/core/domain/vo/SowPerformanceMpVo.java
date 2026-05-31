package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 母猪详情「母猪性能」6 KPI 出参（BRD-FIX-MP-DETAIL-SPLIT-001，原型 09）。
 *
 * <p>mp 端母猪详情页顶部 6 格 KPI 网格用。**从零按本母猪分娩 / 断奶 / 配种记录实时聚合**
 * （Kevin 2026-05-30 D2 拍板：本期 BE 从零建聚合，非读统计预聚合表）。</p>
 *
 * <p>每个指标都可空——数据源缺失（如某母猪还没断奶记录）时该字段返 {@code null}，
 * mp 端对应格子显 {@code —}（不渲染 NaN / undefined）。不抛异常。</p>
 *
 * <p>口径（V1 粗算，客户验收 sense check 用）：</p>
 * <ul>
 *   <li>{@link #totalBorn} 总产仔 = Σ farrow.total_born（缺 total_born 时退化 Σ(健仔+弱仔+死胎+木乃伊)）；</li>
 *   <li>{@link #healthyRate} 健仔率 = Σ(healthy_male+healthy_female) / Σ total_born；</li>
 *   <li>{@link #avgLitterWeight} 平均窝重 = avg(farrow.total_weight)；无 total_weight 列数据 → null（降级）；</li>
 *   <li>{@link #weanSurvivalRate} 断奶成活率 = Σ weaning.weaned_count / Σ farrow.live_born；</li>
 *   <li>{@link #currentParity} 当前胎次 = max(farrow.parity) 与 pig.parity 取大；</li>
 *   <li>{@link #npd} NPD 非生产天数 = 今天 − 最近一次配种 / 分娩日期（V1 粗口径，取较近者）。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-FIX-MP-DETAIL-SPLIT-001
 */
@Data
public class SowPerformanceMpVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总产仔数（Σ total_born）；无分娩记录 → null。 */
    private Integer totalBorn;

    /** 健仔率（0~1 小数，mp 端 ×100 显百分比）；Σ total_born=0 → null。 */
    private BigDecimal healthyRate;

    /** 平均窝重 kg（avg total_weight）；无 total_weight 数据 → null（降级）。 */
    private BigDecimal avgLitterWeight;

    /** 断奶成活率（0~1 小数）；无分娩活产基数 → null。 */
    private BigDecimal weanSurvivalRate;

    /** 当前胎次；max(farrow.parity, pig.parity)；均 0/缺 → null。 */
    private Integer currentParity;

    /** NPD 非生产天数（天）；无配种 / 分娩基准日期 → null。 */
    private Integer npd;
}
