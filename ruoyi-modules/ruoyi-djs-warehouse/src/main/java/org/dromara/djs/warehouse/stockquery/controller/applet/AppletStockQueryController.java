package org.dromara.djs.warehouse.stockquery.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.check.domain.query.StockCheckQuery;
import org.dromara.djs.warehouse.check.domain.vo.StockCheckHeaderVo;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.stockquery.domain.query.StockQueryFlowQuery;
import org.dromara.djs.warehouse.stockquery.domain.query.StockQueryStockQuery;
import org.dromara.djs.warehouse.stockquery.domain.vo.StockQueryFlowVo;
import org.dromara.djs.warehouse.stockquery.domain.vo.StockQueryItemVo;
import org.dromara.djs.warehouse.stockquery.domain.vo.StockQueryStatVo;
import org.dromara.djs.warehouse.stockquery.service.IStockQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * mp 库存查询 + 5 统计 hub Controller（FIX-WMS-MP-STOCKQUERY-001，原型图27/53/55/56）。
 *
 * <p>mp「仓库管理」库存查询页的 5 tab 数据源（path 前缀 {@code /applet/warehouse/stockquery}，
 * 前端 http 不拼 /djs，直传完整 path）：</p>
 * <ul>
 *   <li>{@code GET /stock/list}    库存查询（库位 + 产品名筛选）</li>
 *   <li>{@code GET /flow/list}     出入库流水（in/out 由 inoutType 区分，日期 / 产品名筛选）</li>
 *   <li>{@code GET /check/list}    盘点记录（已完成盘点单，复用共享 IStockCheckService.queryHeaderPage 只读）</li>
 *   <li>{@code GET /return/stat}   退货统计（flow_type=return_in，按产品聚合）</li>
 *   <li>{@code GET /loss/stat}     损耗统计（flow_type=loss，按产品聚合）</li>
 * </ul>
 *
 * <p>全 {@code @SaCheckLogin}（mp V1 mock 登录走真 sa-token，ADR-0003）。本 controller 仅查询，
 * 复用既有 mapper / service，无写入入口；不编辑任何共享 controller。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-STOCKQUERY-001
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/stockquery")
public class AppletStockQueryController extends BaseController {

    private final IStockQueryService stockQueryService;
    private final IStockCheckService stockCheckService;

    /**
     * 库存查询分页（原型图27「库存查询」tab）。
     */
    @SaCheckLogin
    @GetMapping("/stock/list")
    public TableDataInfo<StockQueryItemVo> stockList(StockQueryStockQuery query, PageQuery pageQuery) {
        return stockQueryService.queryStockPage(query, pageQuery);
    }

    /**
     * 出入库流水分页（原型图55「出入库统计」/「进出库记录」）。
     *
     * <p>{@code inoutType=IN} 为入库记录 tab，{@code OT} 为出库记录 tab；前端 tab 切换时传入。</p>
     */
    @SaCheckLogin
    @GetMapping("/flow/list")
    public TableDataInfo<StockQueryFlowVo> flowList(StockQueryFlowQuery query, PageQuery pageQuery) {
        return stockQueryService.queryFlowPage(query, pageQuery);
    }

    /**
     * 盘点记录分页（原型图56「盘点记录」tab）：已完成盘点单（header 聚合 + 盈亏计）。
     *
     * <p>复用共享 {@link IStockCheckService#queryHeaderPage}（只读），固定 {@code checkStatus='completed'}
     * 与 mp 进行中盘点（overview）区隔。</p>
     */
    @SaCheckLogin
    @GetMapping("/check/list")
    public TableDataInfo<StockCheckHeaderVo> checkList(StockCheckQuery query, PageQuery pageQuery) {
        if (query == null) {
            query = new StockCheckQuery();
        }
        query.setCheckStatus("completed");
        return stockCheckService.queryHeaderPage(query, pageQuery);
    }

    /**
     * 退货统计（原型图27「退货统计」tab）：按产品聚合 flow_type=return_in。
     */
    @SaCheckLogin
    @GetMapping("/return/stat")
    public R<List<StockQueryStatVo>> returnStat() {
        return R.ok(stockQueryService.queryReturnStat());
    }

    /**
     * 损耗统计（原型图27「损耗统计」tab）：按产品聚合 flow_type=loss。
     */
    @SaCheckLogin
    @GetMapping("/loss/stat")
    public R<List<StockQueryStatVo>> lossStat() {
        return R.ok(stockQueryService.queryLossStat());
    }

}
