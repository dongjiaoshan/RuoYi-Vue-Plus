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

/**
 * 物资领用 / 退回 / 损耗 mp 端 Controller（WMS-MAT-001）。
 *
 * <p>5 端点：</p>
 * <ul>
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
