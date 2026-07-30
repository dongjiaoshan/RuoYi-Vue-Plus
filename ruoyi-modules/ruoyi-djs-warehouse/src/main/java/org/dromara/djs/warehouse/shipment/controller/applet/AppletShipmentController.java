package org.dromara.djs.warehouse.shipment.controller.applet;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.shipment.domain.bo.ShipmentCheckBo;
import org.dromara.djs.warehouse.shipment.domain.vo.AvailableProductionVo;
import org.dromara.djs.warehouse.shipment.domain.vo.ShipDemandVo;
import org.dromara.djs.warehouse.shipment.domain.vo.ShipStoreVo;
import org.dromara.djs.warehouse.shipment.service.IShipmentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 发货流水 mp Controller（WMS-SHIP-001）。
 *
 * <p>端点（{@code djs:applet:warehouse:ship:*}）：</p>
 * <ul>
 *   <li>{@code POST /applet/warehouse/ship/check}         清点确认（核心）</li>
 *   <li>{@code GET  /applet/warehouse/ship/productions}   按 demand_id 拉待清点产品</li>
 *   <li>{@code GET  /applet/warehouse/ship/store-list}    门店维度待发货聚合（发货月台 IA 进页）</li>
 *   <li>{@code GET  /applet/warehouse/ship/store-demands} 某门店待发需求单 + 各自可发清单（发货子页）</li>
 *   <li>{@code GET  /applet/warehouse/ship/store-summary} 某门店当天聚合（种类数 + 需求满足率，发货子页头）</li>
 * </ul>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/ship")
public class AppletShipmentController extends BaseController {

    private final IShipmentService service;

    /** 清点确认（3 表事务 + publishEvent，业态 white_bar/vegetable/gift_box/other 共此端点）。 */
    @SaCheckPermission("djs:applet:warehouse:ship:check")
    @PostMapping("/check")
    public R<Long> confirmCheck(@Valid @RequestBody ShipmentCheckBo bo) {
        return R.ok(service.confirmCheck(bo));
    }

    /** 待清点产品列表（mp 工人按需求 ID 拉，按 produce_date 倒序）。 */
    @SaCheckPermission("djs:applet:warehouse:ship:check")
    @GetMapping("/productions")
    public R<List<AvailableProductionVo>> listAvailableProductions(@RequestParam Long demandId) {
        return R.ok(service.listAvailableProductions(demandId));
    }

    /** 门店维度待发货聚合（发货月台 IA 进页 = 门店列表：门店名 + 待发需求数 + 待发产品种类数 + 状态）。 */
    @SaCheckPermission("djs:applet:warehouse:ship:check")
    @GetMapping("/store-list")
    public R<List<ShipStoreVo>> listPendingStores() {
        return R.ok(service.listPendingStores());
    }

    /** 某门店待发需求单 + 各需求可发产品清单（发货子页，按需求单分组 + 各自出车发货）。 */
    @SaCheckPermission("djs:applet:warehouse:ship:check")
    @GetMapping("/store-demands")
    public R<List<ShipDemandVo>> listStorePendingDemands(@RequestParam Long storeId) {
        return R.ok(service.listStorePendingDemands(storeId));
    }

    /** 某门店当天发货聚合（门店货物页头：需求产品种类 + 需求满足率，与门店列表卡同一算法）。 */
    @SaCheckPermission("djs:applet:warehouse:ship:check")
    @GetMapping("/store-summary")
    public R<ShipStoreVo> getStoreSummary(@RequestParam Long storeId) {
        return R.ok(service.queryStoreSummary(storeId));
    }
}
