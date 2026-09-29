package org.dromara.djs.warehouse.inout.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnFinishBo;
import org.dromara.djs.warehouse.burn.domain.vo.BarPendingVo;
import org.dromara.djs.warehouse.burn.domain.vo.BurnProductTypeVo;
import org.dromara.djs.warehouse.burn.service.IPigBurnRecordService;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutDoneBo;
import org.dromara.djs.warehouse.cut.domain.vo.CutProductTypeVo;
import org.dromara.djs.warehouse.cut.service.IPigCutRecordService;
import org.dromara.djs.warehouse.inout.domain.bo.*;
import org.dromara.djs.warehouse.inout.domain.vo.*;
import org.dromara.djs.warehouse.inout.service.InoutWorkbenchService;
import org.dromara.djs.warehouse.pack.domain.vo.StoreDemandCopiesVo;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 出入库管理：查询、单产品操作与完成分别授权；重复请求由持久回执处理。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/inout")
public class InoutWorkbenchController {
    private final InoutWorkbenchService service;
    private final IPigBurnRecordService burnService;
    private final IPigCutRecordService cutService;

    @SaCheckPermission("djs:warehouse:inout:query")
    @GetMapping("/burn/pigs")
    public R<List<BarPendingVo>> burnPigs() { return R.ok(service.burnPigs()); }

    @SaCheckPermission("djs:warehouse:inout:query")
    @GetMapping("/burn/products")
    public R<List<BurnProductTypeVo>> burnProducts(@RequestParam Long barInfoId) { return R.ok(service.burnProducts(barInfoId)); }

    @SaCheckPermission("djs:warehouse:inout:query")
    @GetMapping("/cut/bars")
    public R<List<CutWorkbenchBarVo>> cutBars() { return R.ok(service.cutBars()); }

    @SaCheckPermission("djs:warehouse:inout:query")
    @GetMapping("/cut/products")
    public R<List<CutProductTypeVo>> cutProducts() { return R.ok(service.cutProducts()); }

    @SaCheckPermission("djs:warehouse:inout:query")
    @GetMapping("/cut/store-demands")
    public R<List<CutStoreDemandVo>> cutStoreDemands(@RequestParam Long materialProductId) {
        return R.ok(service.cutStoreDemands(materialProductId));
    }

    @SaCheckPermission("djs:warehouse:inout:query")
    @GetMapping("/shipStores")
    public R<List<StoreDemandCopiesVo>> shipStores(@RequestParam Long productId) { return R.ok(service.shipStores(productId)); }

    @SaCheckPermission("djs:warehouse:inout:query")
    @GetMapping("/recentOutDests")
    public R<List<RecentOutDestVo>> recentOutDests() { return R.ok(service.recentOutDests()); }

    @SaCheckPermission("djs:warehouse:inout:submit")
    @PostMapping("/burn/submit")
    public R<WorkbenchSubmitVo> submitBurn(@Valid @RequestBody BurnWorkbenchSubmitBo bo) { return R.ok(service.submitBurn(bo)); }

    @SaCheckPermission("djs:warehouse:inout:submit")
    @PostMapping("/cut/submit")
    public R<WorkbenchSubmitVo> submitCut(@Valid @RequestBody CutWorkbenchSubmitBo bo) { return R.ok(service.submitCut(bo)); }

    @SaCheckPermission("djs:warehouse:inout:finish")
    @GetMapping("/burn/finish-check")
    public R<CompletionCheckVo> checkBurn(@RequestParam Long barInfoId) {
        return R.ok(burnService.finishCheck(barInfoId));
    }

    @SaCheckPermission("djs:warehouse:inout:finish")
    @GetMapping("/cut/finish-check")
    public R<CompletionCheckVo> checkCut(@RequestParam Long cutRecordId) {
        return R.ok(service.cutFinishCheck(cutRecordId));
    }

    @SaCheckPermission("djs:warehouse:inout:finish")
    @PostMapping("/burn/finish")
    public R<Void> finishBurn(@Valid @RequestBody PigBurnFinishBo bo) {
        burnService.finishBurn(bo.getBarInfoId(), LoginHelper.getUserId(), bo.getConfirmAbnormalWeight());
        return R.ok();
    }

    @SaCheckPermission("djs:warehouse:inout:finish")
    @PostMapping("/cut/finish")
    public R<Void> finishCut(@Valid @RequestBody PigCutDoneBo bo) {
        service.finishCut(bo);
        return R.ok();
    }
}
