package org.dromara.djs.store.returns.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店退回（门店→仓库）mp 确认 BO（STORE-RETURN-UNIFY-001，对齐 mp ReturnConfirmBody，逐行提交）。
 *
 * <p>mp 确认页只填确认重量（不选库位）→ service 映射 {@code receivedQty/receivedWeight = confirmWeight}、
 * {@code locationId = null}（由 service 按产品预设/库存最多库位兜底）。</p>
 *
 * @author djs
 * @since STORE-RETURN-UNIFY-001
 */
@Data
public class StoreReturnAppletConfirmBo {

    /** 确认重量（必填，> 0）。 */
    @NotNull(message = "确认重量不能为空")
    @Positive(message = "确认重量必须大于 0")
    private BigDecimal confirmWeight;

    /**
     * 猪肉退货入库库位类型（row145.3）：{@code fresh}=猪肉鲜品库 / {@code frozen}=冻品库。
     * 整单一次选、mp 仅对 pork 产品传；非 pork 传 null（由 service 走默认库位兜底）。
     */
    private String targetLocationType;

    /** 备注（V1 mp 不持久化，留待 V2）。 */
    private String remark;
}
