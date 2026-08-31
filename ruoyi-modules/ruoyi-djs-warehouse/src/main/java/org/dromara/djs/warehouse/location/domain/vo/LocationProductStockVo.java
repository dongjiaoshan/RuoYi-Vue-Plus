package org.dromara.djs.warehouse.location.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 库位总览卡片下钻——单库位内的逐产品库存行（V6 row136）。
 *
 * <p>口径与库位卡片一致：实时库存量按 {@code t_warehouse_location_stock} 逐产品汇总
 * （同一产品的多个耳号 / 地块「篮子」行合并成一行）；今日入库 / 出库量按
 * {@code t_warehouse_stock_flow} 当天流水汇总，出库同样排除 {@code flow_type='loss'}
 * （录入损耗是库存缩减不是出库）。</p>
 *
 * <p>产品当天进完又全部出完、库存归零的情况仍会出现在列表里（毛菜类日清日结很常见），
 * 否则「今日入库 / 今日出库」两列会凭空少掉当天最活跃的那些产品。</p>
 *
 * @author djs
 * @since V6 row136
 */
@Data
@ExcelIgnoreUnannotated
public class LocationProductStockVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 ID（雪花，前端按 string 承接防精度截断）。 */
    private Long productId;

    /** 产品名称。 */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /** 产品规格。 */
    @ExcelProperty(value = "规格")
    private String productSpec;

    /** 实时库存量（该库位内该产品全部库存行之和）。 */
    @ExcelProperty(value = "实时库存量")
    private BigDecimal productStock;

    /** 单位。 */
    @ExcelProperty(value = "单位")
    private String productUnit;

    /** 今日入库量。 */
    @ExcelProperty(value = "今日入库量")
    private BigDecimal todayInQty;

    /** 今日出库量（不含录入损耗）。 */
    @ExcelProperty(value = "今日出库量")
    private BigDecimal todayOutQty;

}
