package org.dromara.djs.store.returns.domain.bo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
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

    /**
     * 确认重量 / 确认数量。<b>丢弃行豁免，其余必填 &gt; 0</b>（校验见 {@link #isConfirmWeightValid()}）。
     *
     * <p>计量口径随产品原材料单位走（row13）：原材料单位 = kg → 重量(kg)；≠ kg → 件数（枚 / 份，整数）。</p>
     */
    private BigDecimal confirmWeight;

    /**
     * 猪肉退货入库库位类型（row145.3）：{@code fresh}=猪肉鲜品库 / {@code frozen}=冻品库。
     * 整单一次选、mp 仅对 pork 产品传；非 pork 传 null（由 service 走默认库位兜底）。
     */
    private String targetLocationType;

    /**
     * 处置方式：{@code 0}/null=退回入库（默认） / {@code 1}=产品丢弃。
     *
     * <p>mp 确认页每个产品卡上的「退回入库 ⇄ 产品丢弃」切换（小程序 行269）。</p>
     */
    private Integer isDiscard;

    /** 备注（V1 mp 不持久化，留待 V2）。 */
    private String remark;

    /**
     * row12：<b>产品丢弃行不必填仓库称重</b>，其余行仍必须 &gt; 0。
     *
     * <p>丢弃行的称重值在 {@code confirm()} 里没有任何下游用途（丢弃分支只推状态到 received，
     * 不写 location_stock / stock_flow），却因为字段级 {@code @NotNull + @Positive} 把整单卡住
     * ——mp 一单里只要有一行标了丢弃且没数，底部「退回确认」就永远是灰的。</p>
     *
     * <p><b>不能简单删掉 @Positive</b>：那会让非丢弃行也不校验，0 / 负数实收会直接落库并联动入库。
     * 故按 {@code isDiscard} 分支校验：丢弃 → 允许空 / 0（负数仍拒）；非丢弃 → 必须非空且 &gt; 0。</p>
     */
    @JsonIgnore
    @AssertTrue(message = "确认重量必须大于 0")
    public boolean isConfirmWeightValid() {
        if (Integer.valueOf(1).equals(isDiscard)) {
            return confirmWeight == null || confirmWeight.signum() >= 0;
        }
        return confirmWeight != null && confirmWeight.signum() > 0;
    }
}
