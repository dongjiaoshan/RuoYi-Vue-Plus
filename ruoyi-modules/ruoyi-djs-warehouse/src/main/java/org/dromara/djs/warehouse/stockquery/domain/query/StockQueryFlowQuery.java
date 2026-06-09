package org.dromara.djs.warehouse.stockquery.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * mp 出入库流水查询入参（FIX-WMS-MP-STOCKQUERY-001 独占）。
 *
 * <p>独立于 admin {@code org.dromara.djs.warehouse.flow.domain.query.StockFlowQuery}，仅暴露 mp
 * 「出入库统计」tab 需要的 3 个筛选维度（原型图55）：</p>
 * <ul>
 *   <li>{@code inoutType} 出入方向（IN 入库记录 tab / OT 出库记录 tab）—— 必填，由 controller 锁定</li>
 *   <li>{@code productName} 产品名称模糊（service 反查 product_id 集合后下推，参 StockFlowServiceImpl.buildWrapper matType 模式）</li>
 *   <li>{@code dateFrom} / {@code dateTo} 业务日期区间</li>
 * </ul>
 *
 * @author djs
 * @since FIX-WMS-MP-STOCKQUERY-001
 */
@Data
public class StockQueryFlowQuery {

    /**
     * 出入方向：IN=入库 / OT=出库（CHAR(3)，由 controller 端点锁定，前端传入被覆盖）。
     */
    private String inoutType;

    /**
     * 产品名称模糊（mp 端筛选下拉文案；service 反查 product_id 集合）。
     */
    private String productName;

    /**
     * 业务时间起。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dateFrom;

    /**
     * 业务时间止。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dateTo;

}
