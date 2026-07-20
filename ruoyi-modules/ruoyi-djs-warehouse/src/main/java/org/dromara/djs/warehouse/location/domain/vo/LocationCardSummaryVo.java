package org.dromara.djs.warehouse.location.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 库位卡片汇总视图对象。
 *
 * <p>按单个库位逐行聚合，库位总览页卡片网格数据源（每库位一张卡）。
 * service 以全部未软删库位为 base 逐库位组装，即使某库位无库存 / 无流水也返 1 行（量为 0）。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-008
 */
@Data
public class LocationCardSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库位主键 id（卡片唯一 key）。
     */
    private Long locationId;

    /**
     * 库位编码。
     */
    private String locationCode;

    /**
     * 库位名称（卡片标题展示）。
     */
    private String locationName;

    /**
     * 库位类型字典 value（djs_location_type，卡片副标签展示）。
     */
    private String locationType;

    /**
     * 库位类型中文 label（service 按字典回填）。
     */
    private String locationTypeLabel;

    /**
     * 该库位在库产品数（COUNT DISTINCT product_id from location_stock）。
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
