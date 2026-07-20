package org.dromara.djs.warehouse.pack.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.pack.domain.bo.CeleryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.DryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.GiftPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.VegPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.WhiteBarOutBo;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 打包工序 mp 端 Controller（WMS-PACK-001）。
 *
 * <p>4 业态 submit + 3 业态来源查询：</p>
 * <ul>
 *   <li>{@code POST /applet/warehouse/pack/veg}     蔬菜打包</li>
 *   <li>{@code POST /applet/warehouse/pack/gift}    礼盒打包（按 D8 gift_box 组件清单）</li>
 *   <li>{@code POST /applet/warehouse/pack/dry}     干货打包</li>
 *   <li>{@code POST /applet/warehouse/pack/celery}  芹菜按重量打包</li>
 *   <li>{@code GET  /applet/warehouse/pack/sourceVeg}     蔬菜可打包来源（plot_id 非空）</li>
 *   <li>{@code GET  /applet/warehouse/pack/sourceDry}     干货可打包来源</li>
 *   <li>{@code GET  /applet/warehouse/pack/sourceCelery}  芹菜可打包来源</li>
 * </ul>
 *
 * <p>{@code @SaCheckLogin + @SaCheckPermission} 走 sa-token 真路（mp V1 mock 登录已发真 token，
 * 参 ADR-0003）。权限串与 V202606101100 menu seed 一一对齐。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/pack")
public class AppletPackController extends BaseController {

    private final IProductProductionService service;

    /**
     * 蔬菜打包提交。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:pack:veg")
    @PostMapping("/veg")
    public R<Long> packVeg(@Valid @RequestBody VegPackBo bo) {
        return R.ok(service.submitVegPack(bo));
    }

    /**
     * 礼盒打包提交（按 D8 gift_box 组件清单）。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:pack:gift")
    @PostMapping("/gift")
    public R<Long> packGift(@Valid @RequestBody GiftPackBo bo) {
        return R.ok(service.submitGiftPack(bo));
    }

    /**
     * 干货打包提交。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:pack:dry")
    @PostMapping("/dry")
    public R<Long> packDry(@Valid @RequestBody DryPackBo bo) {
        return R.ok(service.submitDryPack(bo));
    }

    /**
     * 芹菜按重量打包提交。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:pack:celery")
    @PostMapping("/celery")
    public R<Long> packCelery(@Valid @RequestBody CeleryPackBo bo) {
        return R.ok(service.submitCeleryPack(bo));
    }

    /**
     * 蔬菜可打包来源列表（最近 50 条 plot_id 非空 inhouse）。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:pack:veg")
    @GetMapping("/sourceVeg")
    public R<List<ProductInhouse>> sourceVeg() {
        return R.ok(service.listSourceForVeg());
    }

    /**
     * 干货可打包来源列表（最近 50 条活动 inhouse）。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:pack:dry")
    @GetMapping("/sourceDry")
    public R<List<ProductInhouse>> sourceDry() {
        return R.ok(service.listSourceForDry());
    }

    /**
     * 芹菜可打包来源列表（最近 50 条蔬菜 inhouse）。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:pack:celery")
    @GetMapping("/sourceCelery")
    public R<List<ProductInhouse>> sourceCelery() {
        return R.ok(service.listSourceForCelery());
    }

    /**
     * 白条/猪肉出库(发货领用)提交：inhouse → product_production（前缀 B/Z，绑门店）。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:pack:whiteBarOut")
    @PostMapping("/whiteBarOut")
    public R<Long> packWhiteBarOut(@Valid @RequestBody WhiteBarOutBo bo) {
        return R.ok(service.submitWhiteBarOut(bo));
    }

    /**
     * 白条/猪肉出库可选来源列表（最近 50 条 belong_type ∈ white_bar/pork 的活动 inhouse）。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:pack:whiteBarOut")
    @GetMapping("/sourceWhiteBar")
    public R<List<ProductInhouse>> sourceWhiteBar() {
        return R.ok(service.listSourceForWhiteBar());
    }

}
