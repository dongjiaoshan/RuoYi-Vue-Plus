package org.dromara.djs.store.operation.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 门店销售流水手录 BO（STR-OP-001）。
 *
 * <p>admin 手录单条销售：门店 + 产品（选后前端带出 product_name / sale_unit 冗余，
 * 后端再用 productId 反查兜底）+ 日期 + 数量 + 金额；service 写入 source='manual'。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Data
public class StoreSaleRecordBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 门店 ID。
     */
    @NotNull(message = "门店不能为空")
    private Long storeId;

    /**
     * 产品 ID。
     */
    @NotNull(message = "产品不能为空")
    private Long productId;

    /**
     * 销售日期（只到天）。
     */
    @NotNull(message = "销售日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date saleDate;

    /**
     * 销售数量。
     */
    @NotNull(message = "销售数量不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "销售数量须大于 0")
    private BigDecimal saleQty;

    /**
     * 销售总额（元）。
     */
    @NotNull(message = "销售总额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "销售总额须大于 0")
    private BigDecimal saleAmount;

    /**
     * 备注。
     */
    private String remark;

}
