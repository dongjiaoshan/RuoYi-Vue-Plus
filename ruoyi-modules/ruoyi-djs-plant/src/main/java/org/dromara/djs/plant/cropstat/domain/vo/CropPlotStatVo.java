package org.dromara.djs.plant.cropstat.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 作物「在田地块」聚合统计行（门店需求下单页果蔬列：剩余地块 / 预计产量 / 最早·最晚可采摘日期）。
 *
 * <p>口径（客户 2026-07 定义）：只统计<b>当前处于种植 / 采摘中 / 采摘完成三态</b>的地块——
 * 即地块尚未退茬（{@code t_plant_plot_info.plot_status IN (2,3)}）且本轮已实际播种
 * （明细 {@code plant_status != 'pending'}）。每个地块只取「当前轮」一条明细
 * （按实际种植日期 + id 倒序取最新），避免历史轮次重复计数。</p>
 *
 * <p><b>与产品的关联</b>：作物挂产品是<b>反向</b>的——{@code t_plant_crop_info.related_product}
 * 指向基础果蔬产品 {@code t_warehouse_product_info.id}。前端果蔬产品行匹配本行的规则为
 * {@code relatedProduct == (product.productMaterial ?? product.id)}（加工成品走 productMaterial
 * 回落到基础菜，基础果蔬自身即原材料），与 {@code VegDisplayNameMapper} 的展示名解析链完全一致。</p>
 *
 * @author djs
 */
@Data
public class CropPlotStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物 ID（{@code t_plant_crop_info.id}，雪花；JSON 序列化为字符串）。 */
    private Long cropId;

    /** 作物名称。 */
    private String cropName;

    /**
     * 作物关联的基础果蔬产品 ID（{@code t_plant_crop_info.related_product}）。
     * 前端按 {@code product.productMaterial ?? product.id} 与之匹配。
     */
    private Long relatedProduct;

    /** 剩余地块数（在田地块块数，去重后）。 */
    private Integer remainPlotCount;

    /** 预计产量合计（kg，SUM(明细 expected_yield)）。 */
    private BigDecimal expectYield;

    /** 最早可采摘日期 {@code yyyy-MM-dd}；无数据为 null。 */
    private String earliestPickDate;

    /** 最晚可采摘日期 {@code yyyy-MM-dd}；无数据为 null。 */
    private String latestPickDate;
}
