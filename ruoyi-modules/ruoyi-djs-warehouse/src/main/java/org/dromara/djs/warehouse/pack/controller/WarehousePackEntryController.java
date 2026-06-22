package org.dromara.djs.warehouse.pack.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutDoneBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutOutBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutPickupBo;
import org.dromara.djs.warehouse.cut.domain.query.PigCutRecordQuery;
import org.dromara.djs.warehouse.cut.domain.vo.BarInfoVo;
import org.dromara.djs.warehouse.cut.domain.vo.PigCutRecordVo;
import org.dromara.djs.warehouse.cut.service.IPigCutRecordService;
import org.dromara.djs.warehouse.pack.domain.bo.DryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.GiftPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.VegPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.WhiteBarOutBo;
import org.dromara.djs.warehouse.pack.domain.vo.PackSubmitResultVo;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionVo;
import org.dromara.djs.warehouse.pack.domain.vo.StoreDemandCopiesVo;
import org.dromara.djs.warehouse.pack.domain.vo.VegDailyLossVo;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.domain.query.ProductInfoQuery;
import org.dromara.djs.warehouse.product.domain.vo.ProductInfoVo;
import org.dromara.djs.warehouse.product.service.IProductInfoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 仓库生产管理 - 打包/分割录入 admin 端 Controller（A5）。
 *
 * <p>Kevin 口径：<b>仓库生产管理 admin 可写入打包录入</b>（admin / mp 都能写；只有养殖在 admin 只读）。
 * 本 controller 给 admin「生产管理」下 5 个录入页提供 {@code @SaCheckPermission} 写入端点，
 * <b>全部复用 mp 端已落的 ServiceImpl 业务方法</b>，不重写业务逻辑、不动状态机核心：</p>
 *
 * <ul>
 *   <li>肉品打包 / 其他产品打包    → {@link IProductProductionService#submitDryPack}（按目标 SKU 打包，含猪肉/干货/小白菜等）</li>
 *   <li>其他产品打包 - 礼盒        → {@link IProductProductionService#submitGiftPack}（按 D8 gift_box 组件清单）</li>
 *   <li>果蔬打包                    → {@link IProductProductionService#submitVegPack}（地块来源蔬菜）</li>
 *   <li>白条分割管理（分割间）      → {@link IPigCutRecordService#submitCutOut} + {@link IPigCutRecordService#submitCutDone}</li>
 *   <li>分割白条领用（白条领用）    → {@link IPigCutRecordService#submitPickup}（出库到分割车间）
 *       / {@link IProductProductionService#submitWhiteBarOut}（出库到发货月台）</li>
 * </ul>
 *
 * <p><b>operator / createBy 解析</b>：复用的 ServiceImpl 内部用 {@code LoginHelper.getUserId()} 取当前登录人，
 * admin 登录的就是 {@code sys_user}（B 端员工），语义与 mp 一致，<b>无需 admin 适配、不动共享核心</b>。</p>
 *
 * <p><b>来源/白条列表</b>：admin 录入页选「来源过程产品」「待领用白条」需要列表数据，mp 端列表挂在
 * {@code @SaCheckPermission("djs:applet:warehouse:...")} 上（仅 mp 角色），admin 无该权限，
 * 故这里另开 admin perm 的 GET 端点，<b>仍复用同一 service 查询方法</b>。</p>
 *
 * @author djs
 * @since A5
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/packEntry")
public class WarehousePackEntryController extends BaseController {

    private final IProductProductionService productionService;
    private final IPigCutRecordService pigCutService;
    private final IProductInfoService productInfoService;

    /** 果蔬业态（按领用原料反查命中成品时叠加，避免混入猪肉/干货成品）。 */
    private static final String BELONG_TYPE_VEGETABLE = "vegetable";
    /** 自产产品类型（djs_product_type：1=自产成品）。 */
    private static final Integer PRODUCT_TYPE_SELF = 1;

    // ==================== 肉品打包 / 其他产品打包（干货/标准 SKU 口）====================

    /**
     * 肉品/其他产品打包提交（按目标 SKU 打包，复用 dry 打包业务）。
     *
     * <p>原型「肉品打包管理」「其他产品打包管理」共用此口：选来源过程产品 + 目标产品 SKU +
     * 重量/数量 + 入库库位 + 需求门店 + 发送位置。返回 {@code {id, traceCode}}
     * 供「确认并打印追溯码」展示。</p>
     */
    @SaCheckPermission("djs:warehouse:packEntry:dry")
    @PostMapping("/dry")
    public R<PackSubmitResultVo> packDry(@Valid @RequestBody DryPackBo bo) {
        return R.ok(toResult(productionService.submitDryPack(bo)));
    }

    /**
     * 礼盒打包提交（按 D8 gift_box 组件清单自动查，扣减组件 + 入成品礼盒库）。
     *
     * <p>原型「其他产品打包管理」中的礼盒卡（猪肉礼盒 / 果蔬礼盒）走此口。</p>
     */
    @SaCheckPermission("djs:warehouse:packEntry:gift")
    @PostMapping("/gift")
    public R<PackSubmitResultVo> packGift(@Valid @RequestBody GiftPackBo bo) {
        return R.ok(toResult(productionService.submitGiftPack(bo)));
    }

    /**
     * 果蔬打包提交（地块来源蔬菜 → 目标蔬菜 SKU）。
     *
     * <p>原型「果蔬打包管理」：选地块 + 目标产品 + 重量 + 入库库位 + 发送位置。</p>
     */
    @SaCheckPermission("djs:warehouse:packEntry:veg")
    @PostMapping("/veg")
    public R<PackSubmitResultVo> packVeg(@Valid @RequestBody VegPackBo bo) {
        return R.ok(toResult(productionService.submitVegPack(bo)));
    }

    /**
     * 某产品各门店未发货需求份数（打包录入页底部「门店(N份)」标签条）。
     *
     * <p>原型「肉品 / 果蔬 / 其他产品打包管理」底部需求门店标签：按所选目标产品在需求管理表里
     * 各门店未发货需求量（{@code demand_quantity - shipped_count > 0}）聚合。纯展示/选择参考——
     * 打包提交仍单店语义（带单 storeId），不做多店拆分。</p>
     */
    @SaCheckPermission("djs:warehouse:packEntry:dry")
    @GetMapping("/storeDemand")
    public R<List<StoreDemandCopiesVo>> storeDemand(@RequestParam Long productId) {
        return R.ok(productionService.listStoreDemandCopies(productId));
    }

    /**
     * 批量查目标成品的「原材料实时库存」（打包录入卡片库存口径统一，取数逻辑 doc#13）。
     *
     * <p>卡片展示库存改用此口径：对每个成品取其 {@code product_material} 指向的原材料 {@code location_stock} 合计，
     * <b>与打包校验/扣减用的库存完全一致</b>，修复「操作员看到的库存 ≠ 系统校验/扣减的库存」。
     * 返回 {@code Map<成品雪花id字符串, 库存合计>}；未配 product_material 的成品不在 Map 中（前端展示 '—'）。</p>
     *
     * @param productIds 目标成品 id 列表（逗号分隔；{@code t_warehouse_product_info.id}）
     */
    @SaCheckPermission("djs:warehouse:packEntry:dry")
    @GetMapping("/materialStock")
    public R<Map<String, BigDecimal>> materialStock(@RequestParam List<Long> productIds) {
        return R.ok(productionService.listMaterialStock(productIds));
    }

    /**
     * 批量查目标成品「今天已打包份数」（每条 {@code product_production} = 一份）。
     *
     * <p>打包录入卡片用：某成品今天已打包份数 ≥ 门店需求份数 → 卡片标「打包完成」、禁选，避免超量打包。</p>
     *
     * @param productIds 目标成品 id 列表（逗号分隔；{@code t_warehouse_product_info.id}）
     */
    @SaCheckPermission("djs:warehouse:packEntry:dry")
    @GetMapping("/packedCount")
    public R<Map<String, Integer>> packedCount(@RequestParam List<Long> productIds) {
        return R.ok(productionService.listPackedCount(productIds));
    }

    /**
     * 批量查目标成品「已打包累计总重量(kg)」（admin 果蔬打包页按此算剩余可打包量）。
     *
     * <p>数据源同 {@link #packedCount}（{@code t_warehouse_product_production} 打包记录），
     * 但聚合 {@code SUM(product_weight)} 而非 COUNT。返回 {@code Map<成品雪花id字符串, 重量kg字符串>}
     * （key/value 均字符串：id 防 JS Number 精度丢失、重量保留 BigDecimal 原文精度）。
     * 无生产记录的成品不在 Map（前端按 0 处理）。</p>
     *
     * @param productIds 目标成品 id 列表（逗号分隔；{@code t_warehouse_product_info.id}）
     */
    @SaCheckPermission("djs:warehouse:packEntry:veg")
    @GetMapping("/packedWeight")
    public R<Map<String, String>> packedWeight(@RequestParam List<Long> productIds) {
        return R.ok(productionService.listPackedWeight(productIds));
    }

    /**
     * 果蔬日损耗（V4，果疏产品全流程处理.docx）：按自然日聚合果蔬流水计算
     * {@code 日损耗 = 领用入库 − 打包消耗 − 退回 − 饲喂}。compute-on-read 只读，不建汇总表。
     *
     * <p>果蔬打包录入页展示「当日损耗」。{@code statDate} 缺省取当天。</p>
     *
     * @param statDate 统计自然日（yyyy-MM-dd，可空 → 当天）
     */
    @SaCheckPermission("djs:warehouse:packEntry:veg")
    @GetMapping("/vegDailyLoss")
    public R<VegDailyLossVo> vegDailyLoss(
        @RequestParam(required = false)
        @DateTimeFormat(pattern = "yyyy-MM-dd") Date statDate) {
        return R.ok(productionService.queryVegDailyLoss(statDate));
    }

    /**
     * 按「本次领用原料产品 ID」反查命中成品（findings 步12，果蔬打包页成品下拉收敛）。
     *
     * <p>原型「果蔬打包管理」加载规则：读 {@code t_warehouse_product_info}，筛选
     * {@code product_material} 字段命中本次领用产品 ID 的果蔬自产成品。本端点用
     * {@link ProductInfoQuery#getProductMaterials() productMaterials IN} + belong_type='vegetable'
     * + product_type=1（自产）过滤，复用 {@link IProductInfoService#queryList} 不重写业务。</p>
     *
     * <p><b>向后兼容</b>：{@code materialIds} 为空 → 不按原料过滤（退回「全部果蔬自产成品」，
     * 不破坏 dry/gift/meat 入口现状）。{@code materialIds} 来源 = admin 打包页前端「本次领用的原料产品 ID」。</p>
     *
     * @param materialIds 本次领用原料产品 id 列表（逗号分隔；{@code t_warehouse_product_info.id}，可空）
     * @return 命中成品列表（{@link ProductInfoVo}）
     */
    @SaCheckPermission("djs:warehouse:packEntry:veg")
    @GetMapping("/vegProductsByMaterial")
    public R<List<ProductInfoVo>> vegProductsByMaterial(
        @RequestParam(required = false) List<Long> materialIds) {
        ProductInfoQuery query = new ProductInfoQuery();
        query.setBelongType(BELONG_TYPE_VEGETABLE);
        query.setProductType(PRODUCT_TYPE_SELF);
        if (materialIds != null && !materialIds.isEmpty()) {
            query.setProductMaterials(materialIds);
        }
        return R.ok(productInfoService.queryList(query));
    }

    /**
     * 提交结果包装：按新建 production id 反查 traceCode，组装 {@code {id, traceCode}}。
     */
    private PackSubmitResultVo toResult(Long id) {
        ProductProductionVo vo = id == null ? null : productionService.queryById(id);
        return new PackSubmitResultVo(id, vo == null ? null : vo.getTraceCode());
    }

    /**
     * 打包来源过程产品列表（admin 录入页选「来源」，复用 dry 来源查询）。
     */
    @SaCheckPermission("djs:warehouse:packEntry:dry")
    @GetMapping("/sourceDry")
    public R<List<ProductInhouse>> sourceDry() {
        return R.ok(productionService.listSourceForDry());
    }

    /**
     * 肉品打包可选来源（belong_type='pork' 的活动 inhouse，ear_no = 来源猪只耳号，admin 肉品打包页「耳号去重选择条」）。
     */
    @SaCheckPermission("djs:warehouse:packEntry:dry")
    @GetMapping("/sourceMeat")
    public R<List<ProductInhouse>> sourceMeat() {
        return R.ok(productionService.listSourceForMeat());
    }

    /**
     * 蔬菜可打包来源列表（plot_id 非空，admin 果蔬打包页选「来源/地块」）。
     */
    @SaCheckPermission("djs:warehouse:packEntry:veg")
    @GetMapping("/sourceVeg")
    public R<List<ProductInhouse>> sourceVeg() {
        return R.ok(productionService.listSourceForVeg());
    }

    // ==================== 白条分割管理（分割间）====================

    /**
     * 分割出库称重（多部位一次提交，复用 mp cutOut）。
     *
     * <p>原型「分割间管理」：选白条耳号 + 部位卡 + 各部位重量 + 入冻品库位 → 确认入库。
     * service 首次提交把 cut_record picked→cutting、bar pending_cut→cutting，并按部位入冻品库写流水。</p>
     */
    @SaCheckPermission("djs:warehouse:packEntry:cut")
    @PostMapping("/cutOut")
    public R<Void> cutOut(@Valid @RequestBody PigCutOutBo bo) {
        pigCutService.submitCutOut(bo);
        return R.ok();
    }

    /**
     * 待称重/称重中分割记录列表（cut_status ∈ picked/cutting，admin 分割页选「分割单」）。
     *
     * <p>复用只读 {@link IPigCutRecordService#queryList}，按 cutStatus 各查一次合并返回（V1 量小）。</p>
     */
    @SaCheckPermission("djs:warehouse:packEntry:cut")
    @GetMapping("/cuttableList")
    public R<List<PigCutRecordVo>> cuttableList() {
        PigCutRecordQuery pickedQ = new PigCutRecordQuery();
        pickedQ.setCutStatus("picked");
        List<PigCutRecordVo> result = new java.util.ArrayList<>(pigCutService.queryList(pickedQ));
        PigCutRecordQuery cuttingQ = new PigCutRecordQuery();
        cuttingQ.setCutStatus("cutting");
        result.addAll(pigCutService.queryList(cuttingQ));
        return R.ok(result);
    }

    /**
     * 白条完成分割（复用 mp cutDone）。
     *
     * <p>原型「分割间管理」的「白条完成分割」按钮：cut_record cutting→done、bar cutting→cut_done，
     * 记滴水损失 + 排酸时长 + 屠宰/排酸追溯事件。</p>
     */
    @SaCheckPermission("djs:warehouse:packEntry:cut")
    @PostMapping("/cutDone")
    public R<Void> cutDone(@Valid @RequestBody PigCutDoneBo bo) {
        pigCutService.submitCutDone(bo);
        return R.ok();
    }

    // ==================== 分割白条领用（白条领用）====================

    /**
     * 白条领用到分割车间（复用 mp pickup）。
     *
     * <p>原型「白条领用」出库位置=分割车间：bar in_stock→pending_cut + 新建 cut_record(picked)。</p>
     */
    @SaCheckPermission("djs:warehouse:packEntry:pickup")
    @PostMapping("/pickup")
    public R<Long> pickup(@Valid @RequestBody PigCutPickupBo bo) {
        return R.ok(pigCutService.submitPickup(bo));
    }

    /**
     * 白条/猪肉发货领用（出库到发货月台，复用 whiteBarOut）。
     *
     * <p>原型「白条领用」出库位置=发货月台：inhouse → product_production（前缀 B/Z、绑门店），
     * 解锁白条/猪肉发货链。</p>
     */
    @SaCheckPermission("djs:warehouse:packEntry:pickup")
    @PostMapping("/whiteBarOut")
    public R<Long> whiteBarOut(@Valid @RequestBody WhiteBarOutBo bo) {
        return R.ok(productionService.submitWhiteBarOut(bo));
    }

    /**
     * 白条/猪肉出库可选来源 inhouse（belong_type ∈ white_bar/pork，admin 白条领用页选「来源」）。
     */
    @SaCheckPermission("djs:warehouse:packEntry:pickup")
    @GetMapping("/sourceWhiteBar")
    public R<List<ProductInhouse>> sourceWhiteBar() {
        return R.ok(productionService.listSourceForWhiteBar());
    }

    /**
     * 待领用白条列表（status='in_stock'，admin 白条领用/分割页选白条）。
     */
    @SaCheckPermission("djs:warehouse:packEntry:pickup")
    @GetMapping("/availableBars")
    public R<List<BarInfoVo>> availableBars() {
        return R.ok(pigCutService.queryAvailableBars());
    }

}
