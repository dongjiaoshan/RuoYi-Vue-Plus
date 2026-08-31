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
 *   <li>自产（{@code receiveType=1}）：果蔬来自上游毛菜处理"发往月台"量，row55 起按
 *       <b>{@code productId + plotId} 双键</b>入 {@code location_stock}，入库前按 (作物, 产品, 地块)
 *       校验剩余可入量防超量。</li>
 *   <li>外购（{@code receiveType=2}）：自产食材原料 SKU（{@code product_type=1 且 product_attr=2}，
 *       <b>不是</b> {@code product_type=2}，口径见 {@code VegReceiveMapper.selectPurchasedPending}），
 *       按 {@code productId} 维度入库，复用 {@code LocationStockMapper.addByProductLocation}。</li>
 * </ul>
 *
 * @author djs
 * @since FIX-WMS-VEGRECEIVE-001
 */
public interface IVegReceiveService {

    /**
     * 自产果蔬待收货列表（row55 起<b>按产品聚合</b>）。
     *
     * <p>待入库 = 上游月台量 − 已入库 self 量 − 已结算损耗，仅保留待入库 &gt; 0 的 (作物, 产品) 组合。
     * 同一作物的多个产品（如红薯 / 红薯杆）各自一张卡、各自一份待入库量。</p>
     *
     * @return 待收货项（{@code cropId} 作物 ID + {@code productId} 产品 ID 共同定位一张卡）
     */
    List<VegReceiveItemVo> listSelf();

    /**
     * 外购果蔬待收货列表（<b>自产食材原料 SKU</b>：{@code product_type=1 且 product_attr=2 且 is_buy_out=1}，
     * 不是 {@code product_type=2}；口径见 {@code VegReceiveMapper.selectPurchasedPending}）。可按产品名 / 类型筛选。
     *
     * @param productName 产品名模糊关键字（可空）
     * @param productType 产品类型文案占位（V1 不参与过滤，预留）
     * @return 待收货项（{@code cropId} 为产品 ID，{@code pendingWeight} 恒 0）
     */
    List<VegReceiveItemVo> listPurchased(String productName, String productType);

    /**
     * 某作物 + 产品的果蔬间入库行（按地块：待入库 / 实际入库 / 状态）。
     *
     * @param cropId    作物 ID
     * @param productId 产品 ID（row55 起月台按产品聚合）。传 null = <b>不按产品过滤</b>，返回该作物全部地块
     *                  （读路径保持宽松，等价改动前）；写路径不接受这种含糊，见 {@link #inbound}
     * @return 按地块行；无月台量返空 list
     */
    List<VegInboundPlotVo> listInboundPlots(Long cropId, Long productId);

    /**
     * 自产果蔬入库提交（同事务：校验剩余可入量 → INSERT 收货记录 → UPSERT plot 维度库存 → INSERT 入库流水）。
     *
     * <p>超量（本次 weight &gt; 该 (crop, product, plot) 剩余可入量）抛
     * {@link org.dromara.common.core.exception.ServiceException}，不允许凭空入库。</p>
     *
     * <p>row55 起还有三种会被拒的情况（都返 400）：所选产品不属于该作物的产品配置；
     * 未指定产品而该作物配了多个产品（提示更新小程序）；所选产品不是果蔬原材料。</p>
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

    /**
     * 今日白条出库的猪只耳号候选（V6 row132，外购猪肉产品录入时选耳号用）。
     *
     * @return 去重耳号，按最近一次出库时间倒序；今日无出库则空列表
     */
    List<String> listTodayOutBarEarNos();

}
