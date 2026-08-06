package org.dromara.djs.warehouse.burn.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 燎毛间产品重量调整列表查询条件（V6-R43）。
 *
 * @author djs
 * @since V6-R43
 */
@Data
public class BurnInhouseAdjustQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 入库日期起（{@code yyyy-MM-dd}，闭区间，比对 {@code product_inhouse.produce_time} 的日期部分）。
     *
     * <p>「默认近五天」由 admin 页面填初值传上来，后端不做隐式默认 —— 后端兜底默认会让
     * 「页面上的筛选条件」与「实际生效的筛选条件」不一致，导出/对账时对不上。</p>
     */
    private String inboundDateFrom;

    /**
     * 入库日期止（{@code yyyy-MM-dd}，闭区间）。
     */
    private String inboundDateTo;

    /**
     * 产品名称（模糊匹配）。
     */
    private String productName;

    /**
     * 是否调整（字典 {@code djs_yes_no}：1=是 / 0=否；空 = 不限）。
     */
    private Integer isAdjusted;

}
