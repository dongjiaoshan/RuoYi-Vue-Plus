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
import org.dromara.common.idempotent.annotation.RepeatSubmit;
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
     * 打包口的重复提交拦截窗口（毫秒）—— 与 admin 端 {@code WarehousePackEntryController} 同值，改动必须两侧同步。
     *
     * <p>mp 侧原本<b>一个打包口都没有</b>服务端防重闸：工人在弱网下双击，两次请求都会真正落库、
     * 真正扣两次原材料与需求。这正是甲方在 admin 端怀疑、而 admin 端其实并未发生的那个场景。</p>
     *
     * <p>取 1 秒而非框架默认 5 秒：判重 key = URL + md5(token + 全部入参)，而打包请求体里没有随时间变化的字段，
     * 5 秒窗口会把「连打多份」这种常规操作误判成重复提交。1 秒足以挡住双击与网络重发。
     * （Kevin 2026-08-06 拍板）</p>
     */
    private static final int PACK_REPEAT_INTERVAL_MS = 1000;

    /** 与 admin 端同一条打包场景专属文案（讲清「本次没扣、去确认上一次」）。 */
    private static final String REPEAT_SUBMIT_MESSAGE = "{pack.repeat.submit}";


    /**
     * 蔬菜打包提交。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:pack:veg")
    @RepeatSubmit(interval = PACK_REPEAT_INTERVAL_MS, message = REPEAT_SUBMIT_MESSAGE)
    @PostMapping("/veg")
    public R<Long> packVeg(@Valid @RequestBody VegPackBo bo) {
        return R.ok(service.submitVegPack(bo));
    }

    /**
     * 礼盒打包提交（按 D8 gift_box 组件清单）。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:pack:gift")
    @RepeatSubmit(interval = PACK_REPEAT_INTERVAL_MS, message = REPEAT_SUBMIT_MESSAGE)
    @PostMapping("/gift")
    public R<Long> packGift(@Valid @RequestBody GiftPackBo bo) {
        return R.ok(service.submitGiftPack(bo));
    }

    /**
     * 干货打包提交。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:pack:dry")
    @RepeatSubmit(interval = PACK_REPEAT_INTERVAL_MS, message = REPEAT_SUBMIT_MESSAGE)
    @PostMapping("/dry")
    public R<Long> packDry(@Valid @RequestBody DryPackBo bo) {
        return R.ok(service.submitDryPack(bo));
    }

    /**
     * 芹菜按重量打包提交。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:pack:celery")
    @RepeatSubmit(interval = PACK_REPEAT_INTERVAL_MS, message = REPEAT_SUBMIT_MESSAGE)
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
    @RepeatSubmit(interval = PACK_REPEAT_INTERVAL_MS, message = REPEAT_SUBMIT_MESSAGE)
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
