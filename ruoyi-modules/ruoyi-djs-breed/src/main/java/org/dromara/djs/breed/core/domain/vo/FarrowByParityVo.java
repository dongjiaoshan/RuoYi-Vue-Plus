package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 母猪详情「产仔性能」按胎次堆叠柱单段出参（BRD-FIX-MP-DETAIL-SPLIT-001 / DJS-FIX-6-14，原型 93 / 母猪详情）。
 *
 * <p>mp 端母猪详情产仔性能堆叠柱图：x 轴 = 胎次（"{parity}T"），每胎一条本 VO，6 个分类列堆叠
 * （健仔 / 弱仔(处死) / 弱仔(饲养) / 压死 / 畸形 / 木乃伊）。同一 pig + parity 多行分娩记录按胎次汇总（SUM）。</p>
 *
 * <p>分类口径（落地 t_farm_pig_farrow 实有列）：</p>
 * <ul>
 *   <li>{@link #healthy} 健仔 = Σ(healthy_male + healthy_female)；两列均空时退化 Σ live_born；</li>
 *   <li>{@link #weakCulled} 弱仔(处死) = Σ weak_culled；</li>
 *   <li>{@link #weakRaised} 弱仔(饲养) = Σ(weak_raised_male + weak_raised_female)；两类弱仔全空时退化 Σ weak_born 归入留养；</li>
 *   <li>{@link #crushed} 压死 = Σ crushed_born（DJS-FIX-6-14 新增列；录入未采集前恒 0）；</li>
 *   <li>{@link #deformed} 畸形 = Σ deformed_born；</li>
 *   <li>{@link #mummy} 木乃伊 = Σ mummy_born。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-FIX-MP-DETAIL-SPLIT-001
 */
@Data
public class FarrowByParityVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 胎次（x 轴分类，如 1/2/3）。 */
    private Integer parity;

    /** 健仔数（该胎汇总）。 */
    private Integer healthy;

    /** 弱仔(处死)数（该胎汇总）。 */
    private Integer weakCulled;

    /** 弱仔(饲养)数（该胎汇总）。 */
    private Integer weakRaised;

    /** 压死数（该胎汇总；DJS-FIX-6-14 新增，录入未采集前为 0）。 */
    private Integer crushed;

    /** 畸形数（该胎汇总）。 */
    private Integer deformed;

    /** 木乃伊数（该胎汇总）。 */
    private Integer mummy;
}
