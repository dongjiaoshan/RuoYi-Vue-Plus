package org.dromara.djs.warehouse.shipment.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * mp 端清点确认 BO（{@code POST /applet/warehouse/ship/check}）。
 *
 * <p>跨层契约：所有 ID 字段在前端 JSON 是 string（snowflake），通过 Jackson 反序列化为 Long。</p>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Data
public class ShipmentCheckBo {

    /** 关联需求 ID（前端 string，反序列化 Long）。 */
    @NotNull(message = "需求 ID 不能为空")
    private Long demandId;

    /** 本次清点的产品 production id 列表（前端 string array → Long list）。 */
    @NotEmpty(message = "请至少选择 1 个待清点产品")
    private List<Long> productionIds;

    /** 本次发货总量（kg / 头 / 盒）。 */
    @NotNull(message = "本次发货数量不能为空")
    @Positive(message = "本次发货数量必须大于 0")
    private BigDecimal totalQuantity;

    /** 单位（kg / 头 / 盒）。 */
    @NotNull(message = "单位不能为空")
    private String shipUnit;

    /** 发货方式字典 djs_deliver_type 1=发货 / 2=邮寄 / 3=销售。 */
    @NotNull(message = "发货方式不能为空")
    private Integer deliverType;

    /** 礼盒邮寄场景填收件信息。 */
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;

    /** 清点凭证图 OSS IDs CSV。 */
    private String proofOssIds;

    /** 备注。 */
    private String remark;
}
