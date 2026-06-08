package org.dromara.djs.warehouse.flow.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.warehouse.flow.domain.vo.MatTodaySummaryVo;
import org.dromara.djs.warehouse.flow.domain.vo.SeedStockVo;
import org.dromara.djs.warehouse.flow.service.IMatFlowService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 种子领用 mp 端 Controller（FIX-PLANT-SEED-001，覆盖 #62a，原型 61）。
 *
 * <p>种子领用复用 WMS 商品库（{@code belong_type='seed'}，_open-issues 决策 a：不新建种子表），
 * 与饲料同款 mat 领用链路。本 Controller 仿
 * {@link AppletFeedController} 提供种子专属的库存聚合 + 单产品今日汇总只读端点，
 * 配合 mp 播种首页「种子领用」tab + 种子领用子页（{@code pages/plant/seed/issue}）。</p>
 *
 * <h2>架构归属说明</h2>
 * <p>端点本应放 mp「播种」域（plant 模块），但库存聚合需读 {@code t_warehouse_product_info}
 * + {@code t_warehouse_location_stock}，而 {@code ruoyi-djs-warehouse} 已依赖
 * {@code ruoyi-djs-plant}（反向加依赖会成环），故与 {@code AppletFeedController} 同放 warehouse 模块。
 * mp 录入端（pick / return）直接复用 {@code /applet/warehouse/mat/pick|return} + matType=seed，无新增。</p>
 *
 * <h2>2 端点（仅读）</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/plant/seed/stock}：聚合种子类（{@code belong_type='seed'}）
 *       产品的全场总库存 + 当前登录人今日已领 / 已退。</li>
 *   <li>{@code GET /djs/applet/plant/seed/today-summary?productId=}：当前登录人 + 当日 +
 *       单产品的今日已领 / 退回 / 损耗（领用子页顶卡上下文）。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckLogin}（worker 角色可用，不加 @SaCheckPermission —— 与饲料 mp 录入一致，
 * 种子领用走通用物资权限 {@code djs:applet:warehouse:mat:*}，已在 WMS-MAT-001 挂给 mp 角色）。</p>
 *
 * @author djs
 * @since FIX-PLANT-SEED-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/plant/seed")
public class AppletSeedController {

    /** 种子 belong_type / matType 字典 value（{@code djs_belong_type} 1020048 + {@code djs_mat_type} 102452008 已含）。 */
    private static final String MAT_TYPE_SEED = "seed";

    private final ProductInfoMapper productInfoMapper;
    private final LocationStockMapper locationStockMapper;
    private final IMatFlowService matFlowService;

    /**
     * 种子库存聚合列表（仿 {@link AppletFeedController#stock}）。
     *
     * <ol>
     *   <li>查所有 {@code belong_type='seed'} + 启用（{@code product_status=0}）的产品</li>
     *   <li>查这些产品对应的所有 location_stock，按 productId 内存聚合 SUM(product_stock)</li>
     *   <li>无库存的产品仍返回（totalStock=0），并 enrich 当前登录人今日已领 / 已退</li>
     * </ol>
     *
     * <p>性能：V1 种子 SKU ≤ 10 个，全表扫 + N 次轻查可接受。</p>
     */
    @SaCheckLogin
    @GetMapping("/stock")
    public R<List<SeedStockVo>> stock() {
        // 1. 拉所有启用种子产品（作为输出骨架）
        List<ProductInfo> products = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getBelongType, MAT_TYPE_SEED)
                .eq(ProductInfo::getProductStatus, 0)
                .orderByAsc(ProductInfo::getId)
        );
        if (products.isEmpty()) {
            return R.ok(List.of());
        }
        List<Long> productIds = products.stream().map(ProductInfo::getId).filter(Objects::nonNull).distinct().toList();

        // 2. 拉这些 product 的所有 location_stock 行
        List<LocationStock> stocks = locationStockMapper.selectList(
            new LambdaQueryWrapper<LocationStock>()
                .in(LocationStock::getProductId, productIds)
        );

        // 3. 内存聚合 SUM(product_stock) by productId
        Map<Long, BigDecimal> sumByProduct = new HashMap<>();
        for (LocationStock s : stocks) {
            if (s.getProductId() == null) continue;
            BigDecimal stockNum = s.getProductStock() == null ? BigDecimal.ZERO : s.getProductStock();
            sumByProduct.merge(s.getProductId(), stockNum, BigDecimal::add);
        }

        // 4. 拼 VO（无库存的产品 totalStock=0）+ enrich 当前登录人今日已领 / 已退
        List<SeedStockVo> vos = products.stream().map(p -> {
            SeedStockVo vo = new SeedStockVo();
            vo.setProductId(p.getId());
            vo.setProductCode(p.getProductId());
            vo.setProductName(p.getProductName());
            vo.setProductUnit(p.getProductUnit());
            vo.setTotalStock(sumByProduct.getOrDefault(p.getId(), BigDecimal.ZERO));
            MatTodaySummaryVo today = matFlowService.todaySummary(MAT_TYPE_SEED, String.valueOf(p.getId()));
            vo.setTodayPicked(zeroIfNull(today.getPickedQuantity()));
            vo.setTodayReturned(zeroIfNull(today.getReturnedQuantity()));
            return vo;
        }).sorted(Comparator.comparing(SeedStockVo::getProductCode, Comparator.nullsLast(String::compareTo))).toList();

        return R.ok(vos);
    }

    /**
     * 当前登录人 + 当日 + 指定单产品的「今日已领 / 退回 / 损耗」汇总。
     *
     * <p>种子领用子页顶卡上下文用，单产品维度。</p>
     *
     * @param productId 产品 ID（snowflake String，前端 query 传字符串防截断；service 内转 Long）
     */
    @SaCheckLogin
    @GetMapping("/today-summary")
    public R<MatTodaySummaryVo> todaySummary(@RequestParam String productId) {
        return R.ok(matFlowService.todaySummary(MAT_TYPE_SEED, productId));
    }

    /** null 兜底为 0（确保 VO 字段非 null）。 */
    private static BigDecimal zeroIfNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

}
