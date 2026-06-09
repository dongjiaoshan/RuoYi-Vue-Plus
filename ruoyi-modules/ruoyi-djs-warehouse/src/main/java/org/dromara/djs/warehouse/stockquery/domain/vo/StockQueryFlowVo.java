package org.dromara.djs.warehouse.stockquery.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * mp 出入库流水行 VO（FIX-WMS-MP-STOCKQUERY-001，原型图55）。
 *
 * <p>行展示：业务时间 / 产品 / 数量 / 单位 / 经手人 / 供应商 + 类型标签（购买入库 / 打包退回 /
 * 收货入库 / 销售退回 等，前端按 {@code flowType} 走 djs_flow_type 字典文案）。</p>
 *
 * <p>独立于 admin {@code StockFlowVo}：admin VO 无 {@code supplierName}（仅 supplierId）。本 VO 由
 * service 层批量 JOIN 回填 {@code productName / productUnit / supplierName}（避免 N+1）；
 * {@code operatorName} 走 ruoyi {@code USER_ID_TO_NAME} 注解翻译。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-STOCKQUERY-001
 */
@Data
public class StockQueryFlowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流水行主键。
     */
    private Long id;

    /**
     * 流水单号。
     */
    private String flowNo;

    /**
     * 业务时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date flowDate;

    /**
     * 产品 ID。
     */
    private Long productId;

    /**
     * 产品名称（service JOIN 回填）。
     */
    private String productName;

    /**
     * 单位（service JOIN 回填）。
     */
    private String productUnit;

    /**
     * 出入方向：IN=入库 / OT=出库。
     */
    private String inoutType;

    /**
     * 流水类型 djs_flow_type 字典 value（前端按字典出中文标签）。
     */
    private String flowType;

    /**
     * 变动绝对值（件 / 数量）。
     */
    private BigDecimal changeQuantity;

    /**
     * 供应商 ID。
     */
    private Long supplierId;

    /**
     * 供应商名称（service JOIN 回填；外购入库流水才有，V1 多数为空）。
     */
    private String supplierName;

    /**
     * 经手人 ID。
     */
    private Long operatorId;

    /**
     * 经手人姓名（ruoyi USER_ID_TO_NAME 注解翻译）。
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "operatorId")
    private String operatorName;

    /**
     * 备注。
     */
    private String remark;

}
