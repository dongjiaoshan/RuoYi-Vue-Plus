package org.dromara.djs.warehouse.pack.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutPickupBo;
import org.dromara.djs.warehouse.cut.domain.vo.BarPickupItemVo;
import org.dromara.djs.warehouse.cut.service.IPigCutRecordService;
import org.dromara.djs.warehouse.pack.domain.bo.WarehouseOutBo;
import org.dromara.djs.warehouse.pack.domain.bo.WhiteBarOutBo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 白条出库 mp 端 Controller（V6 row103，「分拣发货 / 白条出库」）。
 *
 * <p>mp 侧对齐 admin「分割白条领用」（{@code /djs/warehouse/packEntry/*}）的完整能力：同一份白条卡
 * 列表、同样的三条出库去向。<b>本 controller 只是权限外壳 + 薄委托，业务逻辑一行不重写</b> ——
 * 与 {@link org.dromara.djs.warehouse.pack.controller.WarehousePackEntryController} 打到的是同一批
 * ServiceImpl 方法，故白条状态机 / 白条库扣减 / 追溯 / 门店需求扣减 三端行为完全一致：</p>
 *
 * <ul>
 *   <li>{@code GET  /applet/warehouse/barOut/items}      在库白条卡（按燎毛产出行逐条，含到场/入库时间与重量）</li>
 *   <li>{@code GET  /applet/warehouse/barOut/shipStores} 「门店发货」可选门店（只列当天有已确认白条需求的店 + 需求数）</li>
 *   <li>{@code GET  /applet/warehouse/barOut/sources}    白条/猪肉可出库来源 inhouse（整只兜底卡按耳号回落匹配用）</li>
 *   <li>{@code POST /applet/warehouse/barOut/cut}        出库去向=分割车间 → {@link IPigCutRecordService#submitPickup}</li>
 *   <li>{@code POST /applet/warehouse/barOut/ship}       出库去向=门店发货 → {@link IProductProductionService#submitWhiteBarOut}</li>
 *   <li>{@code POST /applet/warehouse/barOut/warehouse}  出库去向=仓库出库 → {@link IProductProductionService#submitWarehouseOut}</li>
 * </ul>
 *
 * <p><b>为什么另开权限命名空间 {@code djs:applet:warehouse:barOut:*}</b>：白条出库挂在 mp「分拣发货」
 * tab 下（授权树 11042），而既有 {@code pack*}（打包·包材）与 {@code pigCut:*}（白条分割领用）都挂在
 * 「燎毛间」（11068）下。复用它们会让「分拣发货」的这张卡被燎毛间的勾选项间接开关，取消勾选压不住
 * （FIX-DJS-PERM-MENU-008 同款坑）。独立串 → 独立授权节点，勾谁显谁。</p>
 *
 * <p>{@code @SaCheckLogin + @SaCheckPermission} 走 sa-token 真路（mp V1 mock 登录已发真 token，参 ADR-0003）。
 * 权限串与 {@code V202608310300} menu seed 一一对齐。</p>
 *
 * @author djs
 * @since V6-R103
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/barOut")
public class AppletBarOutController extends BaseController {

    private final IPigCutRecordService pigCutService;
    private final IProductProductionService productionService;
    private final ProductProductionMapper productProductionMapper;

    /** 读端点权限串（白条卡 / 门店 / 来源）。 */
    private static final String PERM_LIST = "djs:applet:warehouse:barOut:list";
    /** 写端点权限串（三条出库去向共用）。 */
    private static final String PERM_SUBMIT = "djs:applet:warehouse:barOut:submit";

    /**
     * 白条出库的重复提交拦截窗口（毫秒）—— 与 {@code AppletPackController} 同值 1 秒。
     *
     * <p>取 1 秒而非框架默认 5 秒：判重 key = URL + md5(token + 全部入参)，白条出库请求体里没有随时间
     * 变化的字段；但白条按半只逐张卡出库，相邻两次的 {@code sourceInhouseId} 必然不同，5 秒窗口只会
     * 误伤「同一张卡被后端拒后立刻改参重试」这类场景。1 秒足以挡住双击与弱网重发。</p>
     */
    private static final int BAR_OUT_REPEAT_INTERVAL_MS = 1000;

    /** 与 admin / mp 打包口同一条场景文案（讲清「本次没扣、去确认上一次」）。 */
    private static final String REPEAT_SUBMIT_MESSAGE = "{pack.repeat.submit}";

    /**
     * 在库白条卡列表（取数与 admin「分割白条领用」完全同源）。
     *
     * <p>在库白条（{@code bar.status ∈ in_stock/pending_cut/cutting}）的每个未领燎毛白条产出行
     * （半只 / 半扇）= 一张卡；燎毛无产出行的旧数据白条回落「整只」兜底卡（{@code inhouseId=null}）。</p>
     */
    @SaCheckLogin
    @SaCheckPermission(PERM_LIST)
    @GetMapping("/items")
    public R<List<BarPickupItemVo>> items() {
        return R.ok(pigCutService.queryPickupItems());
    }

    /**
     * 「门店发货」可选门店（当天有已确认白条需求的门店 + 各店剩余未发份数）。
     *
     * <p>口径见 {@link ProductProductionMapper#selectWhiteBarShipStores()}：需求发满即从列表消失。
     * 返回空 List = 当前没有任何门店有白条需求，前端据此禁用「门店发货」这条去向。</p>
     */
    @SaCheckLogin
    @SaCheckPermission(PERM_LIST)
    @GetMapping("/shipStores")
    public R<List<Map<String, Object>>> shipStores() {
        return R.ok(productProductionMapper.selectWhiteBarShipStores());
    }

    /**
     * 白条/猪肉可出库来源 inhouse（{@code belong_type ∈ white_bar/pork} 的活动行）。
     *
     * <p>只给「整只兜底卡」（{@code inhouseId} 为空的旧数据白条）用：前端按耳号在本列表里回落匹配出
     * {@code sourceInhouseId} 再提交门店发货 / 仓库出库，与 admin 白条领用页
     * {@code resolveSourceInhouseId} 同一兜底路径。</p>
     */
    @SaCheckLogin
    @SaCheckPermission(PERM_LIST)
    @GetMapping("/sources")
    public R<List<ProductInhouse>> sources() {
        return R.ok(productionService.listSourceForWhiteBar());
    }

    /**
     * 出库去向 = 分割车间：白条领用进分割间（bar in_stock→pending_cut + 建 cut_record）。
     *
     * <p>{@code operatorId} = 页面所选出库人（默认登录人）。</p>
     *
     * @return 领满该白条 → 新 cut_record.id；未领满（还有其他半只待领）→ {@code null}
     */
    @SaCheckLogin
    @SaCheckPermission(PERM_SUBMIT)
    @RepeatSubmit(interval = BAR_OUT_REPEAT_INTERVAL_MS, message = REPEAT_SUBMIT_MESSAGE)
    @PostMapping("/cut")
    public R<Long> outToCut(@Valid @RequestBody PigCutPickupBo bo) {
        return R.ok(pigCutService.submitPickup(bo));
    }

    /**
     * 出库去向 = 门店发货：inhouse → product_production（前缀 B/Z、绑门店），并扣该门店白条需求。
     */
    @SaCheckLogin
    @SaCheckPermission(PERM_SUBMIT)
    @RepeatSubmit(interval = BAR_OUT_REPEAT_INTERVAL_MS, message = REPEAT_SUBMIT_MESSAGE)
    @PostMapping("/ship")
    public R<Long> outToShip(@Valid @RequestBody WhiteBarOutBo bo) {
        return R.ok(productionService.submitWhiteBarOut(bo));
    }

    /**
     * 出库去向 = 仓库出库：inhouse → product_production（前缀 B/Z），不发门店、不扣需求，
     * 记出库去向（字典 {@code djs_stock_out_dest}）+ 出库方式=后台出库。
     */
    @SaCheckLogin
    @SaCheckPermission(PERM_SUBMIT)
    @RepeatSubmit(interval = BAR_OUT_REPEAT_INTERVAL_MS, message = REPEAT_SUBMIT_MESSAGE)
    @PostMapping("/warehouse")
    public R<Long> outToWarehouse(@Valid @RequestBody WarehouseOutBo bo) {
        return R.ok(productionService.submitWarehouseOut(bo));
    }

}
