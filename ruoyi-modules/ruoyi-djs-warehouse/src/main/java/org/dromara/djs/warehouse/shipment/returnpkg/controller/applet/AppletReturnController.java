package org.dromara.djs.warehouse.shipment.returnpkg.controller.applet;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnProductBo;
import org.dromara.djs.warehouse.shipment.returnpkg.service.IReturnProductService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退货录入 mp Controller（WMS-SHIP-001）。
 *
 * <p>mp 端仅录入（status='pending'），等 admin 端 confirm 才联动 stock_flow。</p>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/return")
public class AppletReturnController extends BaseController {

    private final IReturnProductService service;

    /**
     * mp 端录入退货（默认方向 store_to_warehouse，状态 pending；
     * 必须等 admin 在 ReturnProductController#confirm 复核 + 入库）。
     */
    @SaCheckPermission("djs:applet:warehouse:return:add")
    @PostMapping("/add")
    public R<Long> add(@Valid @RequestBody ReturnProductBo bo) {
        return R.ok(service.insertByBo(bo));
    }
}
