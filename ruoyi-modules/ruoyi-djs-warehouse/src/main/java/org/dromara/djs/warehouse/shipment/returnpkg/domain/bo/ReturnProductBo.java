package org.dromara.djs.warehouse.shipment.returnpkg.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.ReturnProduct;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退货新增 / 编辑 BO（admin + mp 共用）。
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Data
@AutoMapper(target = ReturnProduct.class)
public class ReturnProductBo {

    /** 主键（编辑时必填）。 */
    private Long id;

    /** 退货门店（store_to_warehouse / customer_to_store 必填；warehouse_to_supplier 可空）。 */
    private Long storeId;

    /** 退货申请时间（不填时 service 用 now）。 */
    private LocalDateTime applyTime;

    @NotNull(message = "产品不能为空")
    private Long productId;

    @Size(max = 128, message = "产品名长度不能超过 128")
    private String productName;

    @NotNull(message = "退货重量不能为空")
    @Positive(message = "退货重量必须大于 0")
    private BigDecimal returnWeight;

    @Size(max = 255, message = "退货原因长度不能超过 255")
    private String returnReason;

    /** 退货方向字典 djs_return_direction（不填时默认 store_to_warehouse）。 */
    private String returnDirection;

    /** 凭证图 OSS IDs CSV。 */
    private String proofOssIds;

    /** 备注。 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
