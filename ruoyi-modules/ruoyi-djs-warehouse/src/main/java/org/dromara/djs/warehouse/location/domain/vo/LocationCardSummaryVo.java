package org.dromara.djs.warehouse.location.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 库位类型卡片汇总视图对象（DJS-FIX-ADMIN-W22-008）。
 *
 * <p>按 {@code djs_location_type} 8 类聚合，库位一览页顶部 2×4 卡片网格数据源。
 * service 以 8 个字典 value 作为 base，即使某类无库位 / 无库存也返 1 行（量为 0），
 * 保证前端固定 8 格布局。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-008
 */
@Data
public class LocationCardSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库位类型字典 value（djs_location_type 8 类）。
     */
    private String locationType;

    /**
     * 库位类型中文 label（service 按字典回填）。
     */
    private String locationTypeLabel;

    /**
     * 该类库位数（COUNT t_warehouse_location_info）。
     */
    private Integer locationCount;

    /**
     * 该类在库产品数（COUNT DISTINCT product_id from location_stock）。
     */
    private Integer productCount;

    /**
     * 当前库存总量（SUM location_stock.product_stock）。
     */
    private BigDecimal currentStock;

    /**
     * 今日入库总量（SUM stock_flow.change_quantity WHERE inout_type='IN' AND today）。
     */
    private BigDecimal todayInQty;

    /**
     * 今日出库总量（SUM stock_flow.change_quantity WHERE inout_type='OT' AND today）。
     */
    private BigDecimal todayOutQty;

    /**
     * 最近盘点日（MAX location_stock.latest_check_time）。
     */
    private Date lastCheckDate;

    /**
     * 最近盘点结果（djs_check_result 1 正常 / 2 异常 / 3 计损，取最近一条对应值）。
     */
    private Integer lastCheckResult;

}
