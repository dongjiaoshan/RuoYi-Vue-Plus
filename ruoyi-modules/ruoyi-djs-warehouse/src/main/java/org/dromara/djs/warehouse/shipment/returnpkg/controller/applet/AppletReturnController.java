package org.dromara.djs.warehouse.shipment.returnpkg.controller.applet;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnConfirmBo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnProductBo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.vo.ReturnProductVo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.vo.ReturnStoreGroupVo;
import org.dromara.djs.warehouse.shipment.returnpkg.service.IReturnProductService;
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
 * 退货管理 + 退货录入 mp Controller（WMS-SHIP-001 + FIX-WMS-MP-RETURN-001）。
 *
 * <p>两条链：</p>
 * <ul>
 *   <li><b>退货录入</b>（{@code /add}）：仓库主动录入门店退货（status='pending'，补充入口）。</li>
 *   <li><b>退货管理</b>（{@code /pending} + {@code /{id}/confirm}，FIX-WMS-MP-RETURN-001 原型图 57/58）：
 *       按门店分组的待确认 / 已确认列表 → 进详情逐产品填确认重量 → 确认提交。
 *       确认复用 admin {@code confirmReturn}（同一 store_to_warehouse 联动 stock_flow(return_in)）。</li>
 * </ul>
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
     * 走退货管理逐项确认重量，或 admin 在 ReturnProductController#confirm 复核 + 入库）。
     */
    @SaCheckPermission("djs:applet:warehouse:return:add")
    @PostMapping("/add")
    public R<Long> add(@Valid @RequestBody ReturnProductBo bo) {
        return R.ok(service.insertByBo(bo));
    }

    /**
     * 退货管理列表（原型图 57）：按门店分组的待确认 / 已确认卡片
     * （门店名 + 状态 + 退货产品品种数 + 退货时间）。
     */
    @SaCheckPermission("djs:applet:warehouse:return:add")
    @GetMapping("/pending")
    public R<List<ReturnStoreGroupVo>> listPendingGroups() {
        return R.ok(service.listPendingGroups());
    }

    /**
     * 退货确认详情（原型图 58）：某门店某状态下的退货清单（逐产品 product_name / return_weight / confirm_weight）。
     */
    @SaCheckPermission("djs:applet:warehouse:return:add")
    @GetMapping("/store-items")
    public R<List<ReturnProductVo>> listStoreItems(@RequestParam Long storeId,
                                                   @RequestParam String returnStatus) {
        return R.ok(service.listByStoreAndStatus(storeId, returnStatus));
    }

    /**
     * 退货确认（原型图 58 提交，逐行调用）：mp 工人逐产品填确认重量后确认。
     *
     * <p>复用 admin {@link IReturnProductService#confirmReturn}：
     * UPDATE is_confirm=1 / return_status='confirmed' / confirm_weight + 联动 stock_flow(return_in)。</p>
     */
    @SaCheckPermission("djs:applet:warehouse:return:add")
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id, @Valid @RequestBody ReturnConfirmBo bo) {
        service.confirmReturn(id, bo);
        return R.ok();
    }
}
