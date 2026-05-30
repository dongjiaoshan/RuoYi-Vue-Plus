package org.dromara.djs.plant.farm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * mp 中央分发台聚合 VO（PLT-WORK-001 GET dispatchSummary）。
 *
 * <p>{@code counts} 以 farm_type 为 key（djs_farm_work_type 12 类），value 为今日（{@code farm_date=今日}）
 * 记录条数。mp 中央分发台 12 张卡片右上角徽章展示。</p>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Data
public class DispatchSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** farmType → 今日记录条数（不含已软删）。 */
    private Map<String, Integer> counts;
}
