package org.dromara.djs.store.returns.domain.vo;

import lombok.Data;
import org.dromara.djs.warehouse.location.domain.vo.LocationPickerVo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 「新增单位退回」弹框候选行 VO（STR-RETURN-OPS-001，{@code GET /djs/store/return/operation/unit-candidates}）。
 *
 * <p>候选来源 = 字典「退回产品清单」{@code djs_return_product_list}（与 mp 退回操作三个 tab 同一本，
 * R214 建的），按产品业务码 resolve 成产品数据；礼盒剔除（拆不回单一原材料，提交必拒）。</p>
 *
 * <p>与 {@link StoreReturnOpsItemVo} 的关键差别：这里没有「退回量」（由用户在弹框里手填），
 * 其余判据（单位 / 计量规则 / 清单内 / 能否入库 / 库位候选）完全同源。</p>
 *
 * @author djs
 * @since STR-RETURN-OPS-001
 */
@Data
public class StoreReturnUnitCandidateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 ID（提交时作 productId）。 */
    private Long productId;

    /** 产品业务编码 product_info.product_id（字典里配的就是它）。 */
    private String productCode;

    /** 产品名称。 */
    private String productName;

    /** 产品规格。 */
    private String productSpec;

    /** 产品归属类型 djs_belong_type（前端展示品类标签用；null 归「其他产品」）。 */
    private String belongType;

    /** 产品单位（= 退回单位，退回量按它录入）。 */
    private String productUnit;

    /** 产品原材料单位（缺省回落产品单位）。 */
    private String materialUnit;

    /** 计量规则（一件折算多少原材料，缺配回落 1）。 */
    private BigDecimal materialNum;

    /** 是否配在字典「退回产品清单」里（恒 true，随行下发让前端与抽屉行同一套精度规则）。 */
    private Boolean inReturnList;

    /** 能否退回入库（成品没配原材料 → 只能丢弃）。 */
    private Boolean canInbound;

    /** 能否做单位换算（false → 前端锁行 + 锁死丢弃）。 */
    private Boolean canConvert;

    /** 入库库位下拉默认值。 */
    private Long defaultLocationId;

    /** 入库库位下拉可选项（猪肉固定鲜品库 / 冻品库；其余按产品存储库位，配空回落全部启用库位）。 */
    private List<LocationPickerVo> locationOptions;
}
