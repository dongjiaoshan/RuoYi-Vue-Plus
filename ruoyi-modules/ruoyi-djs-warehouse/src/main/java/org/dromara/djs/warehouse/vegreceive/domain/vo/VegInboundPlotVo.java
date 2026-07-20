package org.dromara.djs.warehouse.vegreceive.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 果蔬间入库 —— 按地块行 VO（FIX-WMS-VEGRECEIVE-001，mp {@code listInboundPlots}）。
 *
 * <p>对齐 mp 契约 {@code miniapp/src/api/warehouse/vegReceive.ts#VegInboundPlotVo}：
 * {@code plotId / plotCode / inboundStatus / pendingWeight / actualWeight}。</p>
 *
 * <p>数据来源：某作物下、毛菜处理"发往月台"（{@code vegetable_handle.send_platform_weight}）按地块聚合，
 * 左联本表已入库 self 量：</p>
 * <ul>
 *   <li>{@code pendingWeight} = 该地块月台量 − 已入库量（待入果蔬间）</li>
 *   <li>{@code actualWeight} = 该地块已入库量（{@code SUM(veg_receive.weight)} where receiveType=1）</li>
 *   <li>{@code inboundStatus}：actual=0 → pending；0&lt;actual&lt;月台量 → processing；actual≥月台量 → done</li>
 * </ul>
 *
 * @author djs
 * @since FIX-WMS-VEGRECEIVE-001
 */
@Data
public class VegInboundPlotVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 地块 ID（FK → t_plant_plot_info.id）。
     */
    private Long plotId;

    /**
     * 地块编号（如 A-D-001）。
     */
    private String plotCode;

    /**
     * 入库状态：pending 待入库 / processing 入库中 / done 已入库。
     */
    private String inboundStatus;

    /**
     * 待入库量(kg)。
     */
    private BigDecimal pendingWeight;

    /**
     * 实际入库量(kg)。
     */
    private BigDecimal actualWeight;

    /**
     * 损耗量(kg)：该地块已标记入库完成行的 {@code loss_weight} 合计（r72 头卡汇总损耗量 = Σ各地块 lossWeight；
     * r73 ① 损耗 = 确认完成时「待入库量 − 实际入库量」，入库提交时已结算写入 is_finish 行）。未完成地块恒 0。
     */
    private BigDecimal lossWeight;

    /**
     * 默认入库库位 ID（row3 方案B）：该作物关联产品（{@code crop.related_product} → 果蔬原料）配置的存储库位
     * （{@code t_warehouse_product_info.store_location_id} 逗号分隔取第一个）解析所得。mp 打开自产入库弹层时预填、
     * 仍可改。产品/库位查不到 → 留空（前端不预填，退回手选）。同一作物的所有地块行此值一致（按作物解析）。
     */
    private Long defaultLocationId;

    /**
     * 默认入库库位名称（row3 方案B）：{@link #defaultLocationId} 经 {@code t_warehouse_location_info.location_name}
     * 回填，mp 预填弹层库位文案。无默认库位时留空。
     */
    private String defaultLocationName;

}
