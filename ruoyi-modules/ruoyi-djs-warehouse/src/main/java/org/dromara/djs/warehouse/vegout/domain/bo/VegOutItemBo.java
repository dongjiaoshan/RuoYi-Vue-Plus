package org.dromara.djs.warehouse.vegout.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 毛菜间出库单项（admin row185/row187）：候选列表上的<b>一行</b>出多少。
 *
 * <p>V6 row224 / D-0068 起一行不再等于一个库存篮：同 (产品, 库位, 耳号, 地块, 三期)
 * 的多个篮在候选里合并成一行，提交时把这一组篮 id 整组带上来，服务端按列表顺序跨篮先进先出扣减
 * （「出库先进先出自动，工人不再手选哪一篮」——D-0068 原话）。</p>
 *
 * @author djs
 */
@Data
public class VegOutItemBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 这一行背后的库存篮 id 组（{@code t_warehouse_location_stock.id}），先进先出序 ——
     * 原样回传候选给的 {@code stockIds}。
     *
     * <p>仍然只收篮 id、不收 location/product：那两个前端可篡改，而篮 id 一查就能反解出
     * 真实的库位与产品，校验才有意义。</p>
     */
    @NotEmpty(message = "库存行 id 不能为空")
    private List<Long> stockIds;

    /** 出库量（kg，> 0）。 */
    @NotNull(message = "出库量不能为空")
    @DecimalMin(value = "0.001", message = "出库量必须大于 0")
    private BigDecimal quantity;

    /**
     * 出库销售单价（row194）。前端默认带出产品 {@code sale_price}，用户可改；
     * 为空时 service 回落产品主数据价格。落库为流水行上的快照，改产品价格不影响历史单。
     *
     * <p>不允许负数：明细弹框判「组内单价是否一致」用的是 {@code COALESCE(out_unit_price, -1)} 去重，
     * 真有一行填 -1 会让「一行 -1 + 一行 NULL」被误判成一致、显示出一个与总价对不上的单价。
     * 前端输入框 {@code :min="0"} 已挡，这里是裸调接口时的第二道。</p>
     */
    @DecimalMin(value = "0", message = "出库单价不能为负数")
    private BigDecimal outUnitPrice;
}
