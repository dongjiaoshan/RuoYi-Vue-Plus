package org.dromara.djs.warehouse.flow.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.flow.domain.query.StockFlowQuery;
import org.dromara.djs.warehouse.flow.domain.vo.FeedStockVo;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;
import org.dromara.djs.warehouse.flow.service.IStockFlowService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 采食原料（饲料）领用 mp 端 Controller（DJS-FIX-MP-W22-002）。
 *
 * <p>背景：原 breed 首页"采食原料领用"卡片错路由到药品领用页
 * （{@code pages/breed/med/usage}，单位"盒/瓶"，关联 {@code t_breed_medicine_*}），
 * 工人录入饲料数据会污染药品库存。本 Controller 提供饲料专属的库存查询 + 领用流水
 * 查询，配合新 mp 页 {@code pages/breed/feed/index.vue}。</p>
 *
 * <h2>2 端点（仅读，无写入）</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/breed/feed/stock}：聚合饲料类（{@code belong_type='feed'}）
 *       产品的全场总库存，按 product 聚合 SUM(product_stock)。</li>
 *   <li>{@code GET /djs/applet/breed/feed/issue-log}：当前登录人的饲料领用流水
 *       （强制 {@code matType='feed' + flow_type='pick_out' + operatorId=current}）。</li>
 * </ul>
 *
 * <p>写入端（领用录入）走 D10 WMS-MAT-001-FEED-EXT 复用现有
 * {@link org.dromara.djs.warehouse.flow.controller.applet.AppletMatFlowController#pick}
 * + {@code matType=feed}；本 ticket 仅切断错路由 + 占位列表。</p>
 *
 * @author djs
 * @since DJS-FIX-MP-W22-002
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/breed/feed")
public class AppletFeedController extends BaseController {

    /**
     * 饲料 belong_type / matType 字典 value（{@code djs_belong_type} + {@code djs_mat_type} 已含）。
     */
    private static final String MAT_TYPE_FEED = "feed";

    /**
     * 领用流水类型（同 MatFlowServiceImpl#pick 写入 {@code flow_type='pick_out'}）。
     */
    private static final String FLOW_TYPE_PICK_OUT = "pick_out";

    private final ProductInfoMapper productInfoMapper;
    private final LocationStockMapper locationStockMapper;
    private final IStockFlowService stockFlowService;

    /**
     * 饲料原料库存聚合列表。
     *
     * <p>实现：</p>
     * <ol>
     *   <li>查所有 {@code belong_type='feed'} + 启用（{@code product_status=0}）的产品</li>
     *   <li>查这些产品 ID 对应的所有 location_stock（未软删），按 productId 聚合 SUM</li>
     *   <li>无库存（SUM=0 或无 stock 行）的产品仍返回（totalStock=0），便于 mp 端区分"未启用"和"已用尽"</li>
     * </ol>
     *
     * <p>性能：V1 饲料 SKU ≤ 20 个，全表扫足够；后续若上规模可走 mapper 自定义 SQL
     * 的 {@code SELECT product_id, SUM(product_stock) ... GROUP BY product_id}。</p>
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:breed:feed:list")
    @GetMapping("/stock")
    public R<List<FeedStockVo>> stock() {
        // 1. 拉所有启用饲料产品（基础信息固定，作为输出骨架）
        List<ProductInfo> products = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getBelongType, MAT_TYPE_FEED)
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

        // 4. 拼 VO（无库存的产品 totalStock=0）
        List<FeedStockVo> vos = products.stream().map(p -> {
            FeedStockVo vo = new FeedStockVo();
            vo.setProductId(p.getId());
            vo.setProductCode(p.getProductId());
            vo.setProductName(p.getProductName());
            vo.setProductUnit(p.getProductUnit());
            vo.setTotalStock(sumByProduct.getOrDefault(p.getId(), BigDecimal.ZERO));
            return vo;
        }).sorted(Comparator.comparing(FeedStockVo::getProductCode, Comparator.nullsLast(String::compareTo))).toList();

        return R.ok(vos);
    }

    /**
     * 当前登录人的饲料领用流水（分页）。
     *
     * <p>强制 3 个过滤：</p>
     * <ul>
     *   <li>{@code matType='feed'}（StockFlowServiceImpl 内部 JOIN product.belong_type 二次过滤）</li>
     *   <li>{@code flowType='pick_out'}（仅"领用出库"，不混退回 / 损耗）</li>
     *   <li>{@code operatorId=LoginHelper.getUserId()}（仅"我的"流水）</li>
     * </ul>
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:breed:feed:list")
    @GetMapping("/issue-log")
    public TableDataInfo<StockFlowVo> issueLog(StockFlowQuery query, PageQuery pageQuery) {
        if (query == null) {
            query = new StockFlowQuery();
        }
        query.setMatType(MAT_TYPE_FEED);
        query.setFlowType(FLOW_TYPE_PICK_OUT);
        query.setOperatorId(LoginHelper.getUserId());
        return stockFlowService.queryPageList(query, pageQuery);
    }

}
