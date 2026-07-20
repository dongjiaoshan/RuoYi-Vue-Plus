package org.dromara.djs.warehouse.flow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * mp 包材库"今日总览" KPI 卡片 VO（GET {@code /applet/warehouse/packing/home}）。
 *
 * <p>聚合 4 个 KPI（全租户口径，不按操作人；包材库管理是仓管视角看全场）：</p>
 * <ul>
 *   <li>{@code todayInQuantity} 今日入库件数（SUM change_quantity WHERE inout_type='IN'
 *       AND product.belong_type='package' AND DATE(flow_date)=CURDATE()）</li>
 *   <li>{@code todayOutQuantity} 今日领用 / 出库件数（同上 inout_type='OT'）</li>
 *   <li>{@code packTypeCount} 包材种类数（DISTINCT product where belong_type='package' 且未软删）</li>
 *   <li>{@code latestCheckTime} 最近盘点日期（MAX(latest_check_time) over 包材库存）</li>
 * </ul>
 *
 * @author djs
 * @since WMS-FLOW-001
 */
@Data
public class PackingHomeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 今日入库件数（包材，绝对值）。
     */
    private BigDecimal todayInQuantity;

    /**
     * 今日领用 / 出库件数（包材，绝对值）。
     */
    private BigDecimal todayOutQuantity;

    /**
     * 包材种类数（DISTINCT 未软删包材产品数）。
     */
    private Long packTypeCount;

    /**
     * 最近盘点日期（包材库存最新一次盘点；无盘点时为 null）。
     */
    private Date latestCheckTime;

    public static PackingHomeVo empty() {
        PackingHomeVo vo = new PackingHomeVo();
        vo.setTodayInQuantity(BigDecimal.ZERO);
        vo.setTodayOutQuantity(BigDecimal.ZERO);
        vo.setPackTypeCount(0L);
        vo.setLatestCheckTime(null);
        return vo;
    }

}
