package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 母猪窝产聚合 VO（母猪列表「活仔数 / 断奶仔猪数」批量聚合，避免 N+1）。
 *
 * <p>仅内部使用：{@code PigMapper.sumLiveBornByPigIds} / {@code PigMapper.sumWeanedByPigIds}
 * 各按 {@code GROUP BY pig_id} 一次性回填母猪列表行。不直接出参给前端。</p>
 *
 * @author djs
 */
@Data
public class PigLitterAggVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 母猪 id（t_farm_pig_info.id）。 */
    private Long pigId;

    /** 聚合值（活仔 SUM(live_born) 或断奶 SUM(weaned_count)）。 */
    private Integer total;
}
