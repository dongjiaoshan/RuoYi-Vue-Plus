package org.dromara.djs.plant.farm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * mp 中央分发台聚合 VO（PLT-WORK-001 GET dispatchSummary）。
 *
 * <p>两套数都按 farm_type 为 key（djs_farm_work_type 12 类），mp 12 张卡片同时展示
 * 「已处理 / 待处理」（如「翻耕 1/5」）：</p>
 * <ul>
 *   <li>{@code processedCount}：当日已处理地块数（{@code COUNT(DISTINCT plot_id) WHERE farm_date=今日}，同地块多次算 1）</li>
 *   <li>{@code pendingCount}：待处理空地数（{@code plot_status=1} 空地池，12 工种共享同一空地池）</li>
 * </ul>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Data
public class DispatchSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** farmType → 当日已处理地块数（按地块去重，同地块多次算 1）。 */
    private Map<String, Integer> processedCount;

    /** farmType → 待处理空地数（plot_status=1 空地池，12 工种共享）。 */
    private Map<String, Integer> pendingCount;
}
