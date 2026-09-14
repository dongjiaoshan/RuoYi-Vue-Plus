package org.dromara.djs.store.returns.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.djs.warehouse.location.domain.vo.LocationPickerVo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 门店退回操作抽屉行 VO（STR-RETURN-OPS-001，{@code GET /djs/store/return/operation/items}）。
 *
 * <p>一行 = 一张退回单里的一个产品，供 admin 「退回处理」/「查看详情」右侧抽屉逐行渲染。
 * 全部换算所需的判据一次性下发，前端不再自己查字典 / 产品档案：</p>
 * <ul>
 *   <li>{@link #productUnit}（退回单位）→ 决定第三列的口径与小数位（D-0017 / D-0054）：<br>
 *       kg → 三位小数；非 kg → {@link #inReturnList} 为真两位小数、否则整数</li>
 *   <li>{@link #materialNum}（计量规则）→ 提交前把「退回单位量」换算回原材料量
 *       （与 mp {@code toConfirmWeight} 同一套算法，否则同一张单 admin 与 mp 会写出两个数）</li>
 *   <li>{@link #canConvert} → 单位为「份 → kg」但没配计量规则时锁行（瞎猜会把 3 只记成 3 kg 进库存）</li>
 *   <li>{@link #canInbound} → 成品缺原材料时锁死「产品丢弃」</li>
 *   <li>{@link #defaultLocationId} / {@link #locationOptions} → 入库库位下拉（猪肉只给鲜品库 / 冻品库）</li>
 * </ul>
 *
 * @author djs
 * @since STR-RETURN-OPS-001
 */
@Data
public class StoreReturnOpsItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 退回行主键（confirm 入参）。 */
    private Long id;

    /** 退回单号。 */
    private String returnNo;

    /** 退回类型 djs_store_return_type：store / unit。 */
    private String returnType;

    /** 退回单位名（仅 return_type=unit）。 */
    private String returnUnit;

    /** 门店 ID（单位退回为 null）。 */
    private Long storeId;

    /** 「退回门店」展示值：门店名 或 退回单位名。 */
    private String storeName;

    /** 产品 ID。 */
    private Long productId;

    /** 产品名称。 */
    private String productName;

    /** 产品规格。 */
    private String productSpec;

    /** 产品单位（= 退回单位，第三列标题 / 后缀 / 精度判据）。 */
    private String productUnit;

    /** 产品原材料单位（缺省回落产品单位）。 */
    private String materialUnit;

    /** 计量规则（一件折算多少原材料，缺配回落 1）。 */
    private BigDecimal materialNum;

    /** 是否配在字典「退回产品清单」里（非 kg 行两位小数的判据）。 */
    private Boolean inReturnList;

    /** 能否退回入库（成品没配原材料 → 只能丢弃）。 */
    private Boolean canInbound;

    /** 能否做单位换算（false → 前端锁行，后端也拒）。 */
    private Boolean canConvert;

    /** 退回量（按 productUnit）。 */
    private BigDecimal returnQuantity;

    /** 门店申报重量(kg)（← goods_weight）。 */
    private BigDecimal returnWeight;

    /** 仓库确认量（← received_weight，已处理行回显）。 */
    private BigDecimal receivedWeight;

    /** 仓库实收量（← received_qty，与 received_weight 同源写入）。 */
    private BigDecimal receivedQty;

    /** 已确认入库的库位 ID（← location_id）。 */
    private Long locationId;

    /** 已确认入库的库位名。 */
    private String locationName;

    /** 入库库位下拉的默认值（未选库位时前端预填；按入库产品的存储库位 → 库存最多库位兜底）。 */
    private Long defaultLocationId;

    /**
     * 入库库位下拉的可选项。
     *
     * <p>猪肉产品（{@code belong_type='pork'}）固定给「猪肉鲜品库 + 冻品库」两库；
     * 其余产品给自身 {@code store_location_id} 配的启用库位，配空时回落全部启用库位（保证下拉不为空）。</p>
     */
    private List<LocationPickerVo> locationOptions;

    /** 处置方式：0=产品入库（默认） / 1=产品丢弃。 */
    private Integer isDiscard;

    /** 退回状态 djs_store_return_status：pending=待处理 / received=已处理。 */
    private String returnStatus;

    /** 退回日期（← return_date）。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime returnDate;

    /** 退回操作人 ID。 */
    private Long operatorId;

    /** 退回操作人姓名。 */
    private String operatorName;

    /** 退回处理人 ID。 */
    private Long confirmUserId;

    /** 退回处理人姓名。 */
    private String confirmUserName;

    /** 退回处理时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmTime;
}
