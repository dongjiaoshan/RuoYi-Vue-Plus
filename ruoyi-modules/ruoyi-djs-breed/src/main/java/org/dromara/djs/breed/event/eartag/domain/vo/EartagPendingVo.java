package org.dromara.djs.breed.event.eartag.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 端 breed home"仔猪耳标"卡片徽标用 VO（DJS-FIX-MP-W22-003）。
 *
 * <p>给红色徽标提供两个口径（V6 行243 起 = <b>未断奶</b>，与选窝列表同源）：</p>
 * <ul>
 *   <li>{@link #pendingFarrowCount} — 未断奶窝数</li>
 *   <li>{@link #pendingPigletCount} — 未断奶窝的仔猪总头数 = SUM(live_born)</li>
 * </ul>
 *
 * <p>口径来源：扫 {@code t_farm_pig_farrow}，排除已有 {@code t_farm_pig_weaning} 记录的窝。
 * {@code t_farm_pig_farrow} 表无 {@code farrow_status} 列；本接口动态聚合，不引入新字段
 * （SQL 见 {@code PigFarrowMapper}）。</p>
 *
 * @author djs
 * @since DJS-FIX-MP-W22-003
 */
@Data
public class EartagPendingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 未断奶窝数。 */
    private Integer pendingFarrowCount;

    /** 未断奶窝的仔猪总头数（SUM(live_born)）。 */
    private Integer pendingPigletCount;
}
