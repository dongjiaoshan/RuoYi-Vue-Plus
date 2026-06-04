package org.dromara.djs.store.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店看板 - 通用"分组 : 数量"项（STR-DASH-001）。
 *
 * <p>承载三处分组聚合：</p>
 * <ul>
 *   <li>当日产品结构：{@code key}=业态（djs_demand_product_type）、{@code value}=需求量</li>
 *   <li>需求统计 Tab：{@code key}=需求状态（DemandStatus）、{@code value}=笔数</li>
 *   <li>发货统计 Tab：{@code key}=发货相关状态、{@code value}=笔数</li>
 * </ul>
 *
 * <p>{@code value} 用 BigDecimal 统一承载计数（笔数）与数量（kg）。</p>
 *
 * @author djs
 * @since STR-DASH-001
 */
@Data
public class StoreGroupCountVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分组键（业态 / 需求状态 / 发货状态，字典 value 或枚举字面量）。
     */
    private String key;

    /**
     * 该分组聚合值（笔数或需求量；无数据按 0）。
     */
    private BigDecimal value;

}
