package org.dromara.djs.warehouse.shipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发货流水主表实体（{@code t_warehouse_shipment}，WMS-SHIP-001）。
 *
 * <p>4 业态共表：</p>
 * <ul>
 *   <li>{@code productType=white_bar} 白条 — 关联 demand_pig + product_production</li>
 *   <li>{@code productType=vegetable} 蔬菜 — 仅 product_production</li>
 *   <li>{@code productType=gift_box} 礼盒 — 增加 receiverName/phone/address 收件信息（邮寄场景）</li>
 *   <li>{@code productType=other} 其他</li>
 * </ul>
 *
 * <p>状态 4 态由 {@code djs_shipment_status}：pending / checking / shipped / delivered。
 * V1 直接 pending → shipped（mp 工人确认即发车）；CROSS-FLOW-003（D14）touched 后到 delivered。</p>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_shipment")
public class Shipment extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 发货单号（业务码 {@code SHIP_NO}：{@code S+yyyyMMdd+seq4}；UNIQUE(tenant_id, shipment_no, del_unique)）。
     */
    private String shipmentNo;

    /** FK → {@code t_warehouse_demand_manage.id}。 */
    private Long demandId;

    /** 业态字典 {@code djs_demand_product_type}：white_bar / vegetable / gift_box / other。 */
    private String productType;

    /** 目的门店 FK → {@code t_md_store.id}。 */
    private Long storeId;

    /** 发货日期。 */
    private LocalDate shipDate;

    /** 本次发货数量（kg / 头 / 盒）。 */
    private BigDecimal shipQuantity;

    /** 单位（kg / 头 / 盒）。 */
    private String shipUnit;

    /** 发货方式字典 {@code djs_deliver_type}：1=发货 / 2=邮寄 / 3=销售。 */
    private Integer deliverType;

    /** 收件人姓名（礼盒邮寄场景）。 */
    private String receiverName;

    /** 收件人电话。 */
    private String receiverPhone;

    /** 收件地址。 */
    private String receiverAddress;

    /** 状态字典 {@code djs_shipment_status}：pending / checking / shipped / delivered。 */
    private String shipmentStatus;

    /** 清点员 user_id（FK → sys_user.user_id）。 */
    private Long checkerId;

    /** 清点完成时间。 */
    private LocalDateTime checkTime;

    /** 清点凭证图 OSS IDs CSV（bizType={@code warehouse_shipment_proof}）。 */
    private String proofOssIds;

    /** 备注。 */
    private String remark;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 软删唯一辅助列。 */
    private Long delUnique;
}
