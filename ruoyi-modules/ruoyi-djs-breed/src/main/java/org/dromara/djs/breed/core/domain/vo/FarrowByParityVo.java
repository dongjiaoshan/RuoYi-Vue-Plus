package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 母猪详情「产仔性能」按胎次堆叠柱单段出参（BRD-FIX-MP-DETAIL-SPLIT-001，原型 09 SOWDET-02）。
 *
 * <p>mp 端母猪详情产仔性能堆叠柱图：x 轴 = 胎次，每胎一条本 VO，4 个分类列堆叠
 * （健仔 / 弱仔 / 死胎 / 木乃伊）。同一 pig + parity 多行分娩记录按胎次汇总（SUM）。</p>
 *
 * <p>分类口径（落地 t_farm_pig_farrow 实有列）：</p>
 * <ul>
 *   <li>{@link #healthy} 健仔 = Σ(healthy_male + healthy_female)；两列均空时退化 Σ live_born；</li>
 *   <li>{@link #weak} 弱仔 = Σ(weak_raised_male + weak_raised_female + weak_culled)；缺时退化 Σ weak_born；</li>
 *   <li>{@link #dead} 死胎 = Σ dead_born；</li>
 *   <li>{@link #mummy} 木乃伊 = Σ mummy_born。</li>
 * </ul>
 *
 * <p>注：原型柱图列含「压死」「畸形」，t_farm_pig_farrow 无独立"压死"列，
 * 「畸形」走 deformed_born 但 V1 不单列展示——并入弱仔口径（mp 端 4 系列展示，
 * reports 标注与原型 6 系列的差异）。</p>
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

    /** 弱仔数（该胎汇总，含留养 + 处死 + 畸形）。 */
    private Integer weak;

    /** 死胎数（该胎汇总）。 */
    private Integer dead;

    /** 木乃伊数（该胎汇总）。 */
    private Integer mummy;
}
