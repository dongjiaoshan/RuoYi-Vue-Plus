package org.dromara.djs.warehouse.inout.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.djs.warehouse.inout.domain.bo.VegInFinishBo;
import org.dromara.djs.warehouse.inout.domain.bo.VegInSubmitBo;
import org.dromara.djs.warehouse.inout.domain.bo.VegOutWorkbenchSubmitBo;
import org.dromara.djs.warehouse.inout.domain.vo.RecentOutDestVo;
import org.dromara.djs.warehouse.inout.domain.vo.VegInOptionsVo;
import org.dromara.djs.warehouse.inout.domain.vo.VegOutProductVo;
import org.dromara.djs.warehouse.inout.domain.vo.VegOutStockVo;
import org.dromara.djs.warehouse.inout.service.VegInoutWorkbenchService;
import org.dromara.djs.warehouse.veg.domain.vo.VegCropVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegPlotDetailVo;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 出入库管理 · 果蔬入库 / 果蔬出库工作台（V6 row282 / row283），权限与猪只 / 白条工作台同一组。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/inout/veg")
public class VegInoutWorkbenchController {

    private final VegInoutWorkbenchService service;

    /** 果蔬入库：作物列表（同小程序毛菜处理）。 */
    @SaCheckPermission("djs:warehouse:inout:query")
    @GetMapping("/in/crops")
    public R<List<VegCropVo>> crops() {
        return R.ok(service.crops());
    }

    /** 果蔬入库：作物的采摘地块。 */
    @SaCheckPermission("djs:warehouse:inout:query")
    @GetMapping("/in/plots")
    public R<List<VegPlotDetailVo>> plots(@RequestParam Long cropId) {
        return R.ok(service.plots(cropId));
    }

    /** 果蔬入库：去向库位名 + 采摘班组。 */
    @SaCheckPermission("djs:warehouse:inout:query")
    @GetMapping("/in/options")
    public R<VegInOptionsVo> inOptions() {
        return R.ok(service.inOptions());
    }

    /** 果蔬入库：确认入库（毛菜处理采摘录入）。 */
    @SaCheckPermission("djs:warehouse:inout:submit")
    @Log(title = "仓库-果蔬入库管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/in/submit")
    public R<Long> submitIn(@Valid @RequestBody VegInSubmitBo bo) {
        return R.ok(service.submitIn(bo));
    }

    /** 果蔬入库：处理完成（地块称重完成）。 */
    @SaCheckPermission("djs:warehouse:inout:finish")
    @Log(title = "仓库-果蔬入库管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/in/finish")
    public R<Long> finishIn(@Valid @RequestBody VegInFinishBo bo) {
        return R.ok(service.finishIn(bo));
    }

    /** 果蔬出库：库存里的果蔬产品。 */
    @SaCheckPermission("djs:warehouse:inout:query")
    @GetMapping("/out/products")
    public R<List<VegOutProductVo>> outProducts() {
        return R.ok(service.outProducts());
    }

    /** 果蔬出库：所选产品的地块库存卡。 */
    @SaCheckPermission("djs:warehouse:inout:query")
    @GetMapping("/out/stocks")
    public R<List<VegOutStockVo>> outStocks(@RequestParam Long productId) {
        return R.ok(service.outStocks(productId));
    }

    /** 果蔬出库：近 30 天常用出库去向。 */
    @SaCheckPermission("djs:warehouse:inout:query")
    @GetMapping("/out/recentOutDests")
    public R<List<RecentOutDestVo>> recentOutDests() {
        return R.ok(service.recentOutDests());
    }

    /** 果蔬出库：确认出库（仓库出库 / 猪养殖饲料）。 */
    @SaCheckPermission("djs:warehouse:inout:submit")
    @Log(title = "仓库-果蔬出库管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/out/submit")
    public R<Void> submitOut(@Valid @RequestBody VegOutWorkbenchSubmitBo bo) {
        service.submitOut(bo);
        return R.ok();
    }
}
