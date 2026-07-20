package org.dromara.djs.warehouse.veg.domain.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 采摘去向仓库入账 BO（DENGBO-R4 决策 A）。
 *
 * <p>采摘活动录入选「非销售去向」（毛菜保鲜室/果蔬月台/损耗/饲料饲喂）时，由采摘去向入账编排
 * 调用 {@link org.dromara.djs.warehouse.veg.service.IVegetableHandleService#recordPickDestination} 把该批量
 * 直接写仓库台账（复用毛菜处理入库/月台/损耗/饲喂写入机制），不再走毛菜处理 mp 页 → 防双计。</p>
 *
 * <p>销售去向不写仓库（只进种植端产量分摊），故销售去向不调本入账方法。</p>
 *
 * <p>本 BO 不携带 {@code planting_record_id} / {@code vegetable_handle} 上下文（采摘活动是 plant 端事件，
 * 不经仓库毛菜处理状态机），仅靠 crop+plot+weight 直写各去向台账。</p>
 *
 * @author djs
 * @since DENGBO-R4
 */
@Data
public class PickDestSubmitBo {

    /** 作物 id（{@code t_plant_crop_info.id}）。 */
    private Long cropId;

    /** 作物名称（冗余快照，写台账 remark / feed_log.crop_name 用，可空）。 */
    private String cropName;

    /** 地块 id（{@code t_plant_plot_info.id}，非销售去向必填）。 */
    private Long plotId;

    /** 果蔬成品 product_id（= crop.related_product 解析，可空；缺失则台账 product 维度跳过）。 */
    private Long productId;

    /** 采摘去向（{@code djs_pick_dest}：veg_fresh/platform/loss/feed；sale 不调本方法）。 */
    private String pickDest;

    /** 本次重量（kg，> 0）。 */
    private BigDecimal weight;

    /** 记录人 userId（操作人，写台账 operator/handle_user 用）。 */
    private Long recorderId;
}
