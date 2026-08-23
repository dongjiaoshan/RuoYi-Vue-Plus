package org.dromara.djs.warehouse.veg.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 毛菜处理某菜品地块明细 VO（WMS-VEG-001 mp /crops/{cropId}/plots 列表）。
 *
 * <p>某菜品下每个种植记录（地块批次）一行。weighStatus / processStatus 驱动 mp 行内操作链接的
 * 橙(待办) / 灰(已完成) 切换。</p>
 *
 * <p>字段名严格对齐 mp 契约 {@code miniapp/src/api/warehouse/vegHandle.ts} 的 {@code VegPlotDetailVo}。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
public class VegPlotDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 种植记录 ID（= t_warehouse_planting_record.id，mp 提交录入时回传）。
     */
    private Long plantingRecordId;

    /**
     * 地块 ID（= t_plant_plot_info.id）。
     */
    private Long plotId;

    /**
     * 地块编号（= t_plant_plot_info.plot_code，如 A-D-001）。
     */
    private String plotCode;

    /**
     * 地块面积(亩)（= t_plant_plot_info.plot_area）。
     */
    private BigDecimal plotArea;

    /**
     * 采摘录入重量(kg)（= vegetable_handle.picked_weight，多次采摘录入之和）。
     */
    private BigDecimal harvestWeight;

    /**
     * 果蔬处理重量(kg)（= vegetable_handle.handled_weight，V6 row102 口径 = 带该地块标识的产品
     * <b>从毛菜间出库的总重量</b> = 果蔬月台 + 有机饲喂）。
     *
     * <p>⚠️ 饲喂量已含在本字段里，与 {@link #feedWeight} <b>不是互斥的两个桶</b>：
     * feedWeight 是其中「去向=有机饲喂」的那一部分，展示时不要把两者相加。</p>
     */
    private BigDecimal handledWeight;

    /**
     * 饲料重量(kg)（= vegetable_handle.feed_weight，去向③有机饲喂累计；已包含在 handledWeight 内）。
     */
    private BigDecimal feedWeight;

    /**
     * 剩余重量(kg)（V6 row102 口径 = 带该地块标识的产品<b>在毛菜间（L0006）的实时库存</b>，
     * 只统计本作物名下的产品）。
     */
    private BigDecimal remainWeight;

    /**
     * 预计产量(kg)（= 地块面积 plot_area × 作物预计亩产 crop.predicted_per − 该地块灾害损失 disasterLoss，
     * 不小于 0）。与作物级 {@code VegCropVo.expectedYield} 同口径（都已扣灾害损失）。
     */
    private BigDecimal expectYield;

    /**
     * 该地块灾害损失(kg)（= 该地块该作物 {@code t_plant_farm_records(farm_type='disaster')} 的
     * {@code SUM(loss_yield)}；已从 expectYield 中扣除，单列透出供 mp 展示扣减明细）。
     */
    private BigDecimal disasterLoss;

    /**
     * 该作物是否已配置「关联产品」：1=已配 / 0=未配（= t_plant_crop_info.related_product 是否非空）。
     * 0 时 mp 录入软提示「该作物未配置关联产品，建议先在后台作物录入页填写」，不阻断（客户 2026-06-20）。
     */
    private Integer hasRelatedProduct;

    /**
     * 该作物配置的产品清单 + 各自剩余重量（V6 row17/row18，service enrich）。
     *
     * <p>作物没配任何产品时为空 list —— mp 侧此时不显示产品行、也不带 productId 提交，
     * 与改造前行为一致（{@link #hasRelatedProduct} 的软提示仍在）。</p>
     */
    private java.util.List<VegPlotProductVo> products;

    /**
     * 称重(采摘录入)状态：pending=待办 / done=已完成（由 vegetable_handle.is_weighed 派生）。
     */
    private String weighStatus;

    /**
     * 处理(果蔬处理录入)状态：pending=待办 / done=已完成（由 vegetable_handle.is_finish 派生）。
     */
    private String processStatus;

    /**
     * 关联汇总 ID（= vegetable_handle.id，如已部分录入则非空；未录采摘时为 null，mp 据此置灰「果蔬处理录入」）。
     */
    private Long handleId;

}
