package org.dromara.djs.store.demand.controller.applet;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.store.demand.domain.vo.StorePendingArrivalVo;
import org.dromara.djs.store.demand.service.IStoreDemandService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仓库发货月台的到店确认入口。
 *
 * <p>按仓库发货权限跨门店处理，落在 store 模块以复用门店收货服务，避免 warehouse 反向依赖 store。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/warehouse/arrival")
public class AppletWarehouseArrivalController {

    private final IStoreDemandService storeDemandService;

    /** 按门店和需求日期列出所有已发货、尚未确认到店的产品，不限需求日期。 */
    @SaCheckPermission("djs:applet:warehouse:ship:check")
    @GetMapping("/store-list")
    public R<List<StorePendingArrivalVo>> listStores() {
        return R.ok(storeDemandService.listPendingArrivalStores());
    }

    /** 一次确认指定门店同一需求日期全部待到店产品，与门店需求下单复用同一收货逻辑。 */
    @SaCheckPermission("djs:applet:warehouse:ship:check")
    @PostMapping("/receive")
    public R<Integer> receive(@RequestParam Long storeId,
        @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate demandDate) {
        return R.ok(storeDemandService.receiveStoreArrivals(storeId, demandDate));
    }
}
