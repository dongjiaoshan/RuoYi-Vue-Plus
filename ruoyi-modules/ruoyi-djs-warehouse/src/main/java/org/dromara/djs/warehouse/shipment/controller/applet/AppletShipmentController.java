package org.dromara.djs.warehouse.shipment.controller.applet;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.shipment.domain.bo.ShipmentCheckBo;
import org.dromara.djs.warehouse.shipment.domain.vo.AvailableProductionVo;
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
 *   <li>{@code POST /applet/warehouse/ship/check}        清点确认（核心）</li>
 *   <li>{@code GET  /applet/warehouse/ship/productions}  按 demand_id 拉待清点产品</li>
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
}
