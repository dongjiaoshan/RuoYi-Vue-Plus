package org.dromara.djs.warehouse.vegreceive.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.vegreceive.domain.bo.VegInboundBo;
import org.dromara.djs.warehouse.vegreceive.domain.bo.VegPurchaseBo;
import org.dromara.djs.warehouse.vegreceive.domain.vo.VegInboundPlotVo;
import org.dromara.djs.warehouse.vegreceive.domain.vo.VegReceiveItemVo;
import org.dromara.djs.warehouse.vegreceive.service.IVegReceiveService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 果蔬月台收货 mp 端 Controller（FIX-WMS-VEGRECEIVE-001，蔬菜处理 → 果蔬月台 → 保鲜室入库）。
 *
 * <p>对接 mp {@code miniapp/src/api/warehouse/vegReceive.ts} 5 端点：</p>
 * <ul>
 *   <li>{@code GET  /applet/warehouse/vegReceive/self}            自产果蔬待收货列表（row55 起按<b>产品</b>聚合待入库量）</li>
 *   <li>{@code GET  /applet/warehouse/vegReceive/purchased}       外购果蔬待收货列表（自产食材原料 SKU，可按名/类型筛选）</li>
 *   <li>{@code GET  /applet/warehouse/vegReceive/{cropId}/plots}  果蔬间入库（按地块的 待入库 / 实际入库 / 状态，?productId 收窄）</li>
 *   <li>{@code POST /applet/warehouse/vegReceive/inbound}         自产果蔬入库提交（product + plot 双键库存）</li>
 *   <li>{@code POST /applet/warehouse/vegReceive/purchase}        外购果蔬收货入库提交（product 维度库存 + supplier）</li>
 * </ul>
 *
 * <p>写端点同事务跨表：收货记录 + location_stock + stock_flow（详
 * {@link org.dromara.djs.warehouse.vegreceive.service.impl.VegReceiveServiceImpl}）。
 * {@code @SaCheckLogin} 走 sa-token 真路（mp V1 mock 登录已发真 token，ADR-0003）；
 * 跨层契约：{@code cropId / locationId / plotId} 全 string（snowflake，Jackson 全局 Long→String）。</p>
 *
 * @author djs
 * @since FIX-WMS-VEGRECEIVE-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/vegReceive")
public class AppletVegReceiveController extends BaseController {

    private final IVegReceiveService vegReceiveService;

    /**
     * 自产果蔬待收货列表（dock「自产产品收货」分段）。
     *
     * <p>row55 起<b>按产品聚合</b>：待入库 = 上游毛菜处理「发往月台」量 − 已入库 self 量 − 已结算损耗，
     * 仅返待入库 &gt; 0 的 (作物, 产品) 组合 —— 同一作物的多个产品各自一张卡。</p>
     */
    @SaCheckLogin
    @GetMapping("/self")
    public R<List<VegReceiveItemVo>> self() {
        return R.ok(vegReceiveService.listSelf());
    }

    /**
     * 外购果蔬待收货列表（dock「外购产品收货」分段）。
     *
     * <p>返<b>自产食材原料 SKU</b>（{@code product_type=1 且 product_attr=2 且 is_buy_out=1}，口径见
     * {@link org.dromara.djs.warehouse.vegreceive.mapper.VegReceiveMapper#selectPurchasedPending}；
     * 不是 {@code product_type=2}——那类是饲料/药品等生产资料，走 admin 采购入库）。
     * {@code pendingWeight} 恒 0（外购无预设待收量，工人现录实收重量）。</p>
     *
     * @param productName 产品名称模糊关键字（可空）
     * @param productType 产品类型文案占位（V1 不参与过滤，预留）
     */
    @SaCheckLogin
    @GetMapping("/purchased")
    public R<List<VegReceiveItemVo>> purchased(@RequestParam(required = false) String productName,
                                               @RequestParam(required = false) String productType) {
        return R.ok(vegReceiveService.listPurchased(productName, productType));
    }

    /**
     * 果蔬间入库行（按地块）：某作物 + 产品下各地块的 待入库 / 实际入库 / 入库状态。
     *
     * @param cropId    作物 ID（snowflake）
     * @param productId 产品 ID（row55，snowflake）。不传 = <b>不按产品过滤</b>，返回该作物全部地块
     */
    @SaCheckLogin
    @GetMapping("/{cropId}/plots")
    public R<List<VegInboundPlotVo>> plots(@NotNull @PathVariable Long cropId,
                                           @RequestParam(required = false) Long productId) {
        return R.ok(vegReceiveService.listInboundPlots(cropId, productId));
    }

    /**
     * 自产果蔬入库提交（同事务：校验剩余可入量 → 收货记录 + product+plot 双键库存 + 入库流水）。
     *
     * <p>row55：本次收的产品要么由 {@code productId} 指定，要么由作物的唯一产品自动补全；
     * 说不清是哪个产品（多产品作物未传）、产品不属于该作物配置、或产品不是果蔬原材料时返 400，不落库。</p>
     *
     * @return 新建收货记录 ID（snowflake）
     */
    @SaCheckLogin
    @PostMapping("/inbound")
    public R<Long> inbound(@Valid @RequestBody VegInboundBo bo) {
        return R.ok(vegReceiveService.inbound(bo));
    }

    /**
     * 外购果蔬收货入库提交（同事务：resolve supplier → 收货记录 + product 维度库存 + 入库流水）。
     *
     * @return 新建收货记录 ID（snowflake）
     */
    @SaCheckLogin
    @PostMapping("/purchase")
    public R<Long> purchase(@Valid @RequestBody VegPurchaseBo bo) {
        return R.ok(vegReceiveService.purchase(bo));
    }

}
