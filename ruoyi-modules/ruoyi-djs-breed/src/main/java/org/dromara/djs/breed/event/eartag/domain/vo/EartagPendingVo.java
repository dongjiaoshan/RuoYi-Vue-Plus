package org.dromara.djs.breed.event.eartag.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 端 breed home"仔猪耳标"卡片徽标用 VO（DJS-FIX-MP-W22-003）。
 *
 * <p>给"待贴 N 头"红色徽标提供两个口径：</p>
 * <ul>
 *   <li>{@link #pendingFarrowCount} — 待处理 farrow 批数（活产已落但未贴满标）</li>
 *   <li>{@link #pendingPigletCount} — 待贴耳标仔猪总头数 = SUM(live_born - tagged)</li>
 * </ul>
 *
 * <p>口径来源：扫 {@code t_farm_pig_farrow} 与 {@code t_farm_pig_pigletno}，按
 * {@code live_born} 减去本次分娩已 INSERT 的 pigletno 行数。{@code t_farm_pig_farrow}
 * 表无 {@code farrow_status} 列，也无冗余 {@code tagged_count} 列；本接口动态聚合，
 * 不引入新字段（SQL 描述见 service 实现）。</p>
 *
 * @author djs
 * @since DJS-FIX-MP-W22-003
 */
@Data
public class EartagPendingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待处理 farrow 批数（live_born &gt; 已贴）。 */
    private Integer pendingFarrowCount;

    /** 待贴仔猪总头数（SUM(live_born - tagged)）。 */
    private Integer pendingPigletCount;
}
