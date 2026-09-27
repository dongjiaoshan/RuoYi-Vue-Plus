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
 * <p>数据来源：某作物（可选按产品收窄）下、<b>月台明细</b>
 * （{@code t_warehouse_handle_record} 的 {@code record_type=2 且 handle_target=2} 的 {@code record_weight}）按地块聚合，
 * 左联已入库 self 量（{@code receive_type=1}）与已结算损耗（{@code is_finish=1} 的 {@code loss_weight}）：</p>
 * <ul>
 *   <li>{@code pendingWeight} = 该地块月台量 − 已入库量 − 已结算全历史损耗（刚送到、当天未标记完成的地块损耗恒 0）</li>
 *   <li>{@code actualWeight} = 该地块已入库量（{@code SUM(veg_receive.weight)} where {@code receiveType=1}）</li>
 *   <li>{@code lossWeight} = 该地块<b>当天</b>入库完成结算的损耗（{@code DATE(receive_time)=CURDATE()}），与列表卡口径一致；
 *       参与 {@code pending} 判定的是<b>全历史</b>损耗（{@code loss_all}），两者刻意拆开</li>
 *   <li>{@code inboundStatus}：真实待入库量 ≤ 0 → done；&gt; 0 且 actual=0 → pending；其余 → processing</li>
 * </ul>
 *
 * <p>⚠️ <b>不是</b> {@code vegetable_handle.send_platform_weight} 汇总列 —— 那列按作物、多产品会互串（row55 的病根）。</p>
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
