package org.dromara.djs.store.returns.controller.applet;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.store.returns.domain.bo.StoreReturnAppletAddBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnAppletConfirmBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBatchBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnConfirmBo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnAppletItemVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnGroupVo;
import org.dromara.djs.store.returns.service.IStoreReturnService;
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
 * 门店退回（门店→仓库）mp 仓库确认 Controller（STORE-RETURN-UNIFY-001）。
 *
 * <p>Kevin 规范流程：门店「退回操作」发起（pending）→ <b>仓库工人在小程序接受后入库</b> → admin 退货记录可见。
 * 本控制器即「仓库工人在小程序接受」一端：读 {@code t_store_return}（store_to_warehouse）分组 → 进详情逐行确认。</p>
 *
 * <p><b>权限零迁移</b>：端点虽在门店模块，{@code @SaCheckPermission} 挂仓库命名空间
 * {@code djs:applet:warehouse:return:add}（仓库角色按域规则现成命中），无需补授门店权限。
 * 确认动作复用 {@link IStoreReturnService#confirm}（猪肉成品ID/果蔬原材料ID入库 + 库位兜底）。</p>
 *
 * <p>路径与字段对齐 mp 原 {@code /applet/warehouse/return/*}，mp 仅换 api baseURL，页面零改。
 * 跨模块依赖：store 已依赖 warehouse，反向无依赖（不成环），故确认入库逻辑落门店模块。</p>
 *
 * @author djs
 * @since STORE-RETURN-UNIFY-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/store/return")
public class AppletStoreReturnController extends BaseController {

    private final IStoreReturnService service;

    /**
     * 退货录入：仓库工人在小程序主动登记一条门店退货（pending，不入库），统一落 t_store_return。
     * 复用 batchCreate（单条包装），与门店「退回操作」同源，避免双表割裂。
     */
    @SaCheckPermission("djs:applet:warehouse:return:add")
    @PostMapping("/add")
    public R<Integer> add(@Valid @RequestBody StoreReturnAppletAddBo bo) {
        StoreReturnBatchBo batch = new StoreReturnBatchBo();
        batch.setStoreId(bo.getStoreId());
        StoreReturnBatchBo.Item item = new StoreReturnBatchBo.Item();
        item.setProductId(bo.getProductId());
        item.setReturnWeight(bo.getReturnWeight());
        item.setReturnQuantity(bo.getReturnQuantity());
        item.setTraceCode(bo.getTraceCode());
        batch.setItems(List.of(item));
        return R.ok(service.batchCreate(batch));
    }

    /**
     * 退货管理列表：当天门店→仓库退回按门店分组卡（门店名 + 派生状态 pending/confirmed + 品种数 + 退回时间）。
     */
    @SaCheckPermission("djs:applet:warehouse:return:add")
    @GetMapping("/pending")
    public R<List<StoreReturnGroupVo>> listPendingGroups() {
        return R.ok(service.listPendingGroups());
    }

    /**
     * 退货确认详情：某门店某状态（mp 词表 pending/confirmed）下的退回清单（逐产品，字段对齐 mp ReturnItem）。
     */
    @SaCheckPermission("djs:applet:warehouse:return:add")
    @GetMapping("/store-items")
    public R<List<StoreReturnAppletItemVo>> listStoreItems(@RequestParam Long storeId,
                                                           @RequestParam String returnStatus) {
        return R.ok(service.listAppletItemsByStoreAndStatus(storeId, returnStatus));
    }

    /**
     * 退货确认（逐行）：mp 工人填确认重量后接受 → pending→received + 联动入库
     * （locationId 留空由 service 按入库产品预设库位/库存最多库位兜底）。
     */
    @SaCheckPermission("djs:applet:warehouse:return:add")
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id, @Valid @RequestBody StoreReturnAppletConfirmBo bo) {
        StoreReturnConfirmBo confirmBo = new StoreReturnConfirmBo();
        confirmBo.setId(id);
        confirmBo.setLocationId(null);
        confirmBo.setReceivedQty(bo.getConfirmWeight());
        confirmBo.setReceivedWeight(bo.getConfirmWeight());
        // row145.3：猪肉退货指定鲜/冻库（mp 仅对 pork 传，service 内再按产品 belong_type 二次守卫）
        confirmBo.setTargetLocationType(bo.getTargetLocationType());
        service.confirm(confirmBo);
        return R.ok();
    }
}
