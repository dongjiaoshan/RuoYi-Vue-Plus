package org.dromara.djs.warehouse.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.flow.domain.query.InoutSummaryQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutMonthVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutSummaryInVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutSummaryOutVo;
import org.dromara.djs.warehouse.flow.service.IInoutMonthlyService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 出入库月汇总 Controller（V6-R154 列表 / R155 入库汇总 / R156 出库汇总）。
 *
 * <p>compute-on-read 按月 GROUP BY 既有出入库流水，无汇总表、无跑批。
 * 三个页面都不分页（月份行是个位数，下钻行数 ≈ 产品数 × 方式数），一次返全量前端内滚。</p>
 *
 * <p>🔴 两个导出端点的参数是<b>命令对象</b> {@link InoutSummaryQuery}，不是 {@code @RequestParam}：
 * 前端 {@code proxy.download} 把数组序列化成 {@code flowTypes[0]=a} 索引形式，
 * 只有 WebDataBinder 命令对象绑定收得到，否则多选筛选在导出里静默失效。</p>
 *
 * @author djs
 * @since V6-R154
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/inoutMonthly")
public class InoutMonthlyController extends BaseController {

    private final IInoutMonthlyService inoutMonthlyService;

    /**
     * 月份列表（R154）：有出入库流水的月份，倒序。
     *
     * @param statMonth 月份精确筛选 yyyy-MM（可空 = 全部月份）
     */
    @SaCheckPermission("djs:warehouse:inoutMonthly:list")
    @GetMapping("/list")
    public R<List<InoutMonthVo>> list(@RequestParam(required = false) String statMonth) {
        return R.ok(inoutMonthlyService.queryMonths(statMonth));
    }

    /**
     * 当月入库汇总（R155）：产品 × 入库方式 × 供应商。
     */
    @SaCheckPermission("djs:warehouse:inoutMonthly:query")
    @GetMapping("/in/list")
    public R<List<InoutSummaryInVo>> inList(@Validated InoutSummaryQuery query) {
        return R.ok(inoutMonthlyService.queryInSummary(query));
    }

    /**
     * 入库汇总导出（R155 第 4 点：导出内容与列表一致）。
     */
    @SaCheckPermission("djs:warehouse:inoutMonthly:inExport")
    @Log(title = "入库汇总", businessType = BusinessType.EXPORT)
    @PostMapping("/in/export")
    public void inExport(@Validated InoutSummaryQuery query, HttpServletResponse response) {
        List<InoutSummaryInVo> list = inoutMonthlyService.queryInSummary(query);
        ExcelUtil.exportExcel(list, "入库汇总_" + query.getStatMonth(), InoutSummaryInVo.class, response);
    }

    /**
     * 当月出库汇总（R156）：产品 × 出库去向。
     */
    @SaCheckPermission("djs:warehouse:inoutMonthly:query")
    @GetMapping("/out/list")
    public R<List<InoutSummaryOutVo>> outList(@Validated InoutSummaryQuery query) {
        return R.ok(inoutMonthlyService.queryOutSummary(query));
    }

    /**
     * 出库汇总导出（R156 第 4 点：导出内容与列表一致）。
     */
    @SaCheckPermission("djs:warehouse:inoutMonthly:outExport")
    @Log(title = "出库汇总", businessType = BusinessType.EXPORT)
    @PostMapping("/out/export")
    public void outExport(@Validated InoutSummaryQuery query, HttpServletResponse response) {
        List<InoutSummaryOutVo> list = inoutMonthlyService.queryOutSummary(query);
        ExcelUtil.exportExcel(list, "出库汇总_" + query.getStatMonth(), InoutSummaryOutVo.class, response);
    }
}
