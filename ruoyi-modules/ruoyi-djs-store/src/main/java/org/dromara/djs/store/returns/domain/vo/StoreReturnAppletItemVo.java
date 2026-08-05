package org.dromara.djs.store.returns.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门店退回（门店→仓库）mp 退货确认明细行 VO（{@code GET /applet/store/return/store-items}，STORE-RETURN-UNIFY-001）。
 *
 * <p>字段名对齐 mp {@code ReturnItem}（returnWeight / confirmWeight / applyTime / productCategory / isConfirm /
 * returnStatus 用 mp 词表 pending/confirmed），mp 确认页零改、api 仅换路径。映射来源（{@code t_store_return}）：</p>
 * <ul>
 *   <li>{@code returnWeight} ← goods_weight（申报重量）；{@code confirmWeight} ← received_weight（实收重量）</li>
 *   <li>{@code applyTime} ← return_date；{@code isConfirm} ← returnStatus==received ? 1 : 0</li>
 *   <li>{@code productCategory} ← 产品 belong_type（service 批量回填，原占位「—」改真值）</li>
 * </ul>
 *
 * @author djs
 * @since STORE-RETURN-UNIFY-001
 */
@Data
public class StoreReturnAppletItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 退回行 ID（snowflake string，confirm 入参）。 */
    private Long id;

    /** 退回单号。 */
    private String returnNo;

    /** 门店 ID。 */
    private Long storeId;

    /** 退回申请时间（← return_date）。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime applyTime;

    /** 产品 ID。 */
    private Long productId;

    /** 产品名称。 */
    private String productName;

    /** 产品品类（产品 belong_type，service 回填）。 */
    private String productCategory;

    /** 产品单位（← product_unit，service 回填；mp 退货确认页「退货单位」列显示，流程性问题 row16）。 */
    private String productUnit;

    /**
     * 产品<b>原材料单位</b>（← 原材料产品的 product_unit，service 批量回填；无原材料 / 原材料无单位时回落
     * 产品自身单位）。
     *
     * <p>mp 退回确认页据它决定「仓库称重重量」输入框的口径（甲方 row13）：
     * = kg → 小数录入、后缀 kg；≠ kg → <b>整数</b>录入、后缀 {@code productUnit}（枚 / 份 …）。</p>
     */
    private String materialUnit;

    /** 产品规格（← product_spec，service 回填；mp 退货确认页品名行右侧显示，row186）。 */
    private String productSpec;

    /** 退回数量（← return_quantity，退回的份数/把/盒；mp 确认页「退货量」列）。 */
    private BigDecimal returnQuantity;

    /** 退回重量（← goods_weight）。 */
    private BigDecimal returnWeight;

    /** 确认重量（← received_weight，未确认时空）。 */
    private BigDecimal confirmWeight;

    /** 是否已确认：1=是 / 0=否（returnStatus==received 派生）。 */
    private Integer isConfirm;

    /**
     * 处置方式：{@code 0}=退回入库（默认）/ {@code 1}=产品丢弃（小程序 row269）。
     *
     * <p>已确认行回显仓库当时的选择；未确认行为建表默认 0，mp 侧即"默认退回入库"的初始态。</p>
     */
    private Integer isDiscard;

    /** 退回原因。 */
    private String returnReason;

    /** 退回方向（恒 store_to_warehouse）。 */
    private String returnDirection;

    /** 退回状态（mp 词表 pending / confirmed）。 */
    private String returnStatus;

    /** 备注。 */
    private String remark;
}
