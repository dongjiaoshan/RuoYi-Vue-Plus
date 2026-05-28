package org.dromara.djs.warehouse.flow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 端"今日数据"卡片汇总 VO（GET {@code /applet/warehouse/mat/myToday}）。
 *
 * <p>按当前登录人 + 当日 + 可选 matType 聚合 stock_flow：</p>
 * <ul>
 *   <li>{@code pickedQuantity} 今日已领（SUM change_quantity WHERE flow_type='pick_out'）</li>
 *   <li>{@code returnedQuantity} 今日已退（SUM change_quantity WHERE flow_type='return_in'）</li>
 *   <li>{@code lossQuantity} 今日已损（SUM change_quantity WHERE flow_type='loss'）</li>
 * </ul>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Data
public class MatTodaySummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 今日已领（数量绝对值）。
     */
    private BigDecimal pickedQuantity;

    /**
     * 今日已退。
     */
    private BigDecimal returnedQuantity;

    /**
     * 今日已损。
     */
    private BigDecimal lossQuantity;

    public static MatTodaySummaryVo empty() {
        MatTodaySummaryVo vo = new MatTodaySummaryVo();
        vo.setPickedQuantity(BigDecimal.ZERO);
        vo.setReturnedQuantity(BigDecimal.ZERO);
        vo.setLossQuantity(BigDecimal.ZERO);
        return vo;
    }

}
