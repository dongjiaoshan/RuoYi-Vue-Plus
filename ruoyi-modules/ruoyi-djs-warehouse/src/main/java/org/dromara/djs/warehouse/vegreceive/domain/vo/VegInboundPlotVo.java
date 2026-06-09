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

}
