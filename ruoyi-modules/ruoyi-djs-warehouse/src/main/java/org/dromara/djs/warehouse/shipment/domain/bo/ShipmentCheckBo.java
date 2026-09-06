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

    /**
     * 缺量发车放行标志（V6-row160）。
     *
     * <p>默认 {@code false} —— 已打包量不足需求量时拒发，保持「杜绝部分发货」的默认姿态
     * （Kevin 2026-06-25 拍板），任何直调接口都不会悄悄发出半车货。
     * mp 在弹过「当前生产产品暂未满足门店需求，是否确定发车？」并拿到确认后才传 {@code true}，
     * 于是「这是一次有意的缺量发车」在请求里是显式信号，日志可追。</p>
     */
    private Boolean force;

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
