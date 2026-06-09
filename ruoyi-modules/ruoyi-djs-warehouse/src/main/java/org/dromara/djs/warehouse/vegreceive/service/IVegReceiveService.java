package org.dromara.djs.warehouse.vegreceive.service;

import org.dromara.djs.warehouse.vegreceive.domain.bo.VegInboundBo;
import org.dromara.djs.warehouse.vegreceive.domain.bo.VegPurchaseBo;
import org.dromara.djs.warehouse.vegreceive.domain.vo.VegInboundPlotVo;
import org.dromara.djs.warehouse.vegreceive.domain.vo.VegReceiveItemVo;

import java.util.List;

/**
 * 果蔬月台收货 Service（FIX-WMS-VEGRECEIVE-001）。
 *
 * <p>对接 mp {@code miniapp/src/api/warehouse/vegReceive.ts} 5 端点：自产 / 外购待收货列表 +
 * 果蔬间按地块入库行 + 自产入库提交 + 外购收货入库提交。</p>
 *
 * <h3>两类入库的库存维度差异</h3>
 * <ul>
 *   <li>自产（{@code receiveType=1}）：果蔬来自上游毛菜处理"发往月台"量，按 {@code plotId} 维度入
 *       {@code location_stock}（库存三维互斥之一），入库前校验剩余可入量防超量。</li>
 *   <li>外购（{@code receiveType=2}）：外购果蔬产品（{@code product_type=2}），按 {@code productId} 维度入库，
 *       复用 {@code LocationStockMapper.addByProductLocation}（与包材入库同范式）。</li>
 * </ul>
 *
 * @author djs
 * @since FIX-WMS-VEGRECEIVE-001
 */
public interface IVegReceiveService {

    /**
     * 自产果蔬待收货列表（按作物聚合）。
     *
     * <p>待入库 = 上游月台量 − 已入库 self 量，仅保留待入库 &gt; 0 的作物。</p>
     *
     * @return 待收货项（{@code cropId} 为作物 ID，{@code pendingWeight} 为待入库总量）
     */
    List<VegReceiveItemVo> listSelf();

    /**
     * 外购果蔬待收货列表（{@code product_type=2} 外购产品，可按产品名 / 类型筛选）。
     *
     * @param productName 产品名模糊关键字（可空）
     * @param productType 产品类型文案占位（V1 不参与过滤，预留）
     * @return 待收货项（{@code cropId} 为产品 ID，{@code pendingWeight} 恒 0）
     */
    List<VegReceiveItemVo> listPurchased(String productName, String productType);

    /**
     * 某作物的果蔬间入库行（按地块：待入库 / 实际入库 / 状态）。
     *
     * @param cropId 作物 ID
     * @return 按地块行；无月台量返空 list
     */
    List<VegInboundPlotVo> listInboundPlots(Long cropId);

    /**
     * 自产果蔬入库提交（同事务：校验剩余可入量 → INSERT 收货记录 → UPSERT plot 维度库存 → INSERT 入库流水）。
     *
     * <p>超量（本次 weight &gt; 该 (crop, plot) 剩余可入量）抛 {@link org.dromara.common.core.exception.ServiceException}，
     * 不允许凭空入库。</p>
     *
     * @param bo 自产入库入参
     * @return 新建收货记录 ID
     */
    Long inbound(VegInboundBo bo);

    /**
     * 外购果蔬收货入库提交（同事务：resolve supplier → INSERT 收货记录 → UPSERT product 维度库存 → INSERT 入库流水）。
     *
     * @param bo 外购入库入参（{@code cropId} 承载产品 ID，{@code supplier} 承载供应商业务短码）
     * @return 新建收货记录 ID
     */
    Long purchase(VegPurchaseBo bo);

}
