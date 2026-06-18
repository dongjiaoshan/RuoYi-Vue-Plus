package org.dromara.djs.warehouse.flow.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.flow.domain.bo.MatLossBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatPickBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatReturnBo;
import org.dromara.djs.warehouse.flow.domain.query.StockFlowQuery;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueItemVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueLocationVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatTodaySummaryVo;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;
import org.dromara.djs.warehouse.flow.service.IMatFlowService;
import org.dromara.djs.warehouse.flow.service.IStockFlowService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 物资领用 / 退回 / 损耗 mp 端 Controller（WMS-MAT-001）。
 *
 * <p>7 端点：</p>
 * <ul>
 *   <li>{@code GET  /applet/warehouse/mat/issueLocations} 业态「二级库」chip 列表（FIX-WMS-MATISSUE-001）</li>
 *   <li>{@code GET  /applet/warehouse/mat/issueItems} 业态待领产品卡列表（库存 + 今日已领/退/损）</li>
 *   <li>{@code POST /applet/warehouse/mat/pick}   领用（同事务扣库存 + 写流水）</li>
 *   <li>{@code POST /applet/warehouse/mat/return} 退回（同事务加库存 + 写流水 + 校验今日额度）</li>
 *   <li>{@code POST /applet/warehouse/mat/loss}   损耗（同事务扣库存 + 写流水 + 校验今日额度）</li>
 *   <li>{@code GET  /applet/warehouse/mat/myToday} 今日数据卡片（picked / returned / loss）</li>
 *   <li>{@code GET  /applet/warehouse/mat/myList} 我的流水（按 operatorId 自动过滤当前登录人）</li>
 * </ul>
 *
 * <p>{@code @SaCheckLogin} 走 sa-token 真路（mp V1 mock 登录已发真 token，参 ADR-0003）。</p>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/mat")
public class AppletMatFlowController extends BaseController {

    private final IMatFlowService matFlowService;
    private final IStockFlowService stockFlowService;

    /**
     * 业态「二级库」chip 列表（FIX-WMS-MATISSUE-001）。
     *
     * <p>原型「物资领用」页每个业态 tab 下橙底库位 chip（白条库 / 冻品库 / 蔬菜保鲜库 ...）。
     * 由「该业态产品实际分布的库位」数据驱动；包材业态常无库位 → 返空 list（前端不渲染 chip 段）。</p>
     *
     * @param belongType 字典 {@code djs_belong_type}（pork / vegetable / egg / dry_good ...）
     */
    @SaCheckLogin
    @GetMapping("/issueLocations")
    public R<List<MatIssueLocationVo>> issueLocations(@RequestParam String belongType) {
        return R.ok(matFlowService.issueLocations(belongType));
    }

    /**
     * 业态待领产品卡列表（FIX-WMS-MATISSUE-001）。
     *
     * <p>某业态产品 + 当前库存 + 当前登录人今日已领 / 退 / 损（驱动卡片字段与点入表单上限）。</p>
     *
     * @param belongType 字典 {@code djs_belong_type}
     * @param locationId 库位 ID（可空，chip 选中态过滤；snowflake string 防截断，service 内 parse）
     */
    @SaCheckLogin
    @GetMapping("/issueItems")
    public R<List<MatIssueItemVo>> issueItems(@RequestParam String belongType,
                                              @RequestParam(required = false) String locationId) {
        return R.ok(matFlowService.issueItems(belongType, locationId));
    }

    /**
     * 自产果蔬「按地块维度」可领用列表（步11 偏差修复 · 决策 a；蔬菜 tab 自产果蔬数据源）。
     *
     * <p>自产果蔬库存按 {@code (plot_id, location)} 建账（无 product_id），常规 {@link #issueItems}
     * 按 product_id join 列不到。本端点以 plot 维度列出，VO 回填 {@code plotId} / {@code plotCode}
     * （productId 为 null），前端点卡进领用表单时把 plotId 回填到 {@code MatPickBo.plotId}。</p>
     *
     * @param locationId 库位 ID（可空，chip 选中态过滤；snowflake string 防截断，service 内 parse）
     */
    @SaCheckLogin
    @GetMapping("/selfVegIssueItems")
    public R<List<MatIssueItemVo>> selfVegIssueItems(@RequestParam(required = false) String locationId) {
        return R.ok(matFlowService.selfVegIssueItems(locationId));
    }

    @SaCheckLogin
    @PostMapping("/pick")
    public R<Long> pick(@Valid @RequestBody MatPickBo bo) {
        return R.ok(matFlowService.pick(bo));
    }

    @SaCheckLogin
    @PostMapping("/return")
    public R<Long> returnBack(@Valid @RequestBody MatReturnBo bo) {
        return R.ok(matFlowService.returnBack(bo));
    }

    @SaCheckLogin
    @PostMapping("/loss")
    public R<Long> loss(@Valid @RequestBody MatLossBo bo) {
        return R.ok(matFlowService.loss(bo));
    }

    @SaCheckLogin
    @GetMapping("/myToday")
    public R<MatTodaySummaryVo> myToday(@RequestParam(required = false) String matType) {
        return R.ok(matFlowService.todaySummary(matType));
    }

    @SaCheckLogin
    @GetMapping("/myList")
    public TableDataInfo<StockFlowVo> myList(StockFlowQuery query, PageQuery pageQuery) {
        query.setOperatorId(LoginHelper.getUserId());
        return stockFlowService.queryPageList(query, pageQuery);
    }

}
