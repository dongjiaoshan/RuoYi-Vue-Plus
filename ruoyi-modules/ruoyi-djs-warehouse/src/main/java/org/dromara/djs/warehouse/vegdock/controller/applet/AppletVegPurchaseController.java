package org.dromara.djs.warehouse.vegdock.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.vegdock.domain.bo.VegPurchaseInboundBo;
import org.dromara.djs.warehouse.vegdock.domain.bo.VegPurchaseReceiveBo;
import org.dromara.djs.warehouse.vegdock.domain.vo.VegPurchaseCropGroupVo;
import org.dromara.djs.warehouse.vegdock.domain.vo.VegPurchaseVo;
import org.dromara.djs.warehouse.vegdock.service.IVegPurchaseService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 外购果蔬月台收货 + 果蔬间入库 mp 端 Controller（FIX-WMS-MP-VEGDOCK-001，原型图 42/43）。
 *
 * <p>4 个端点（外购链路；自产链路 mp 直接复用 {@code /applet/warehouse/vegHandle/pending} 只读）：</p>
 * <ul>
 *   <li>{@code POST /applet/warehouse/vegPurchase/receive}      外购收货登记（图 42 外购产品收货）</li>
 *   <li>{@code GET  /applet/warehouse/vegPurchase/cropGroups}   外购收货 tab 按品种聚合待入库（图 42）</li>
 *   <li>{@code GET  /applet/warehouse/vegPurchase/byCrop}       某品种下未入完到货明细（图 43 下钻）</li>
 *   <li>{@code POST /applet/warehouse/vegPurchase/inbound}      果蔬间入库确认实际入库量（图 43）</li>
 * </ul>
 *
 * <p>{@code @SaCheckLogin} 走 sa-token 真路径（ADR-0003 mock 登录已发真 token）。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-VEGDOCK-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/vegPurchase")
public class AppletVegPurchaseController extends BaseController {

    private final IVegPurchaseService service;

    /**
     * 外购收货登记（图 42 外购产品收货）。
     */
    @SaCheckLogin
    @PostMapping("/receive")
    public R<Long> receive(@Valid @RequestBody VegPurchaseReceiveBo bo) {
        return R.ok(service.receive(bo));
    }

    /**
     * 外购收货 tab：按品种聚合的待入库列表（图 42 卡片）。
     */
    @SaCheckLogin
    @GetMapping("/cropGroups")
    public R<List<VegPurchaseCropGroupVo>> cropGroups() {
        return R.ok(service.listCropGroups());
    }

    /**
     * 果蔬间入库：某品种下未入完到货明细行（图 43 下钻）。
     */
    @SaCheckLogin
    @GetMapping("/byCrop")
    public R<List<VegPurchaseVo>> byCrop(@NotNull(message = "缺少 cropId 参数") @RequestParam Long cropId) {
        return R.ok(service.listByCrop(cropId));
    }

    /**
     * 果蔬间入库确认（图 43 实际入库量）。
     */
    @SaCheckLogin
    @PostMapping("/inbound")
    public R<Long> inbound(@Valid @RequestBody VegPurchaseInboundBo bo) {
        return R.ok(service.inbound(bo));
    }

}
