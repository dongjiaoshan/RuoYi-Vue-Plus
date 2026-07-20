package org.dromara.djs.warehouse.pack.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 白条发货记录列表 VO（WS12 row133）。
 *
 * <p>统计每日白条到发货月台 / 仓库出库情况。每行 = 一次白条出库事件
 * （{@code t_warehouse_product_production} 中 {@code belong_type='white_bar'} 的记录）。</p>
 *
 * @author djs
 * @since WS12
 */
@Data
@ExcelIgnoreUnannotated
public class WhiteBarShipmentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 发货日期（到时分秒 = {@code produce_time}）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "发货日期")
    private Date produceTime;

    /**
     * 产品编码（= {@code product_info.product_id} 业务码，如 PROD-WHITE-BAR-04）。
     */
    @ExcelProperty(value = "产品编码")
    private String productCode;

    /**
     * 产品名称（= {@code product_production.product_name} 冗余列）。
     */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /**
     * 猪只耳号（= {@code product_production.ear_no}）。
     */
    @ExcelProperty(value = "猪只耳号")
    private String earNo;

    /**
     * 出库方式（字典 {@code djs_bar_out_method} 派生值：1=发货月台 / 3=仓库出库）。
     */
    @ExcelProperty(value = "出库方式")
    private String outMethod;

    /**
     * 出库去向（字典 {@code djs_stock_out_dest}：ship_dock=发货月台 / mine=矿山 / kitchen=厨房 等）。
     */
    @ExcelProperty(value = "出库去向")
    private String outDest;

    /**
     * 发货门店名（= {@code product_production.store_id} → {@code t_md_store.store_name}；
     * 发货月台发往门店时有值，后台出库/非到门店时为 {@code null} 显示空）。
     */
    @ExcelProperty(value = "门店")
    private String storeName;

    /**
     * 出库量（= {@code product_production.product_weight}）。
     */
    @ExcelProperty(value = "出库量")
    private BigDecimal productWeight;

    /**
     * 单位（= {@code product_production.product_unit}）。
     */
    @ExcelProperty(value = "单位")
    private String productUnit;

    private Long createBy;

    /**
     * 操作人（录入人昵称，{@code create_by} translation）。
     */
    @ExcelProperty(value = "操作人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String operatorName;

}
