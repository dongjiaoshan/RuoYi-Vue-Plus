package org.dromara.djs.warehouse.thirdphase.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 三期作物入库列表查询入参（V6 row88）。
 *
 * <p>甲方只要两个筛选条件：日期区间（入库时间）+ 作物名称模糊。</p>
 *
 * @author djs
 * @since V6-R88
 */
@Data
public class ThirdPhaseInQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 入库日期 ≥（{@code yyyy-MM-dd}）。
     *
     * <p>故意收成 String 而不是 Date：{@code in_time} 是 DATETIME，前端传的是纯日期，
     * 绑成 Date 会得到 {@code 00:00:00}，配上 {@code endTime} 同样的 0 点就会把「结束当天」
     * 整天漏掉。service 里把 {@code endTime} 补成当天 {@code 23:59:59} 再比。</p>
     */
    private String beginTime;

    /** 入库日期 ≤（{@code yyyy-MM-dd}，含当天全天）。 */
    private String endTime;

    /** 作物名称（模糊；关键字进 LIKE 前过 {@code LikeEscape.escape}）。 */
    private String cropName;
}
