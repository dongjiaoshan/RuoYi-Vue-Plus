package org.dromara.djs.store.returns.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门店退回新增 / 编辑 BO（admin only，STR-RETURN-001）。
 *
 * <p>{@code returnNo} 服务端生成不接收；{@code operatorId} 由 LoginHelper 注入；
 * {@code traceCode} / {@code memberId} 可空，V1 仅存值不做 FK 校验。service 手工映射到 entity。</p>
 *
 * @author djs
 * @since STR-RETURN-001
 */
@Data
public class StoreReturnBo {

    /** 主键（编辑时必填）。 */
    private Long id;

    /** 退回方向字典 djs_return_direction（默认 customer_to_store，门店主场景）。 */
    @NotBlank(message = "退回方向不能为空")
    @Size(max = 32, message = "退回方向长度不能超过 32")
    private String returnDirection;

    /** 退回门店。STR-RETURN-REBUILD-001 §8.2：简化后只 customer_to_store 一态，门店必填。 */
    @NotNull(message = "退回门店不能为空")
    private Long storeId;

    @NotNull(message = "产品不能为空")
    private Long productId;

    /** 退回入库库位（STR-RETURN-REBUILD-001 K4：退回联动外购入库必填，新增时校验；编辑不可改）。 */
    @NotNull(message = "退回入库库位不能为空")
    private Long locationId;

    @NotNull(message = "退回数量不能为空")
    @Positive(message = "退回数量必须大于 0")
    private BigDecimal returnQuantity;

    /** 退回原因（自由文本）。 */
    @Size(max = 255, message = "退回原因长度不能超过 255")
    private String returnReason;

    /** 已贴追溯码（如有），V1 仅存值无校验。 */
    @Size(max = 64, message = "追溯码长度不能超过 64")
    private String traceCode;

    /** 退回日期（不填时 service 用 now）。 */
    private LocalDateTime returnDate;

    /** 会员退回时的会员 ID（可空），V1 仅存值无校验。 */
    private Long memberId;

    /** 备注。 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
