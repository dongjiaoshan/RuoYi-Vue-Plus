package org.dromara.djs.warehouse.stock.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.stock.domain.vo.StockOverviewDailyVo;
import org.dromara.djs.warehouse.stock.domain.vo.StockOverviewDetailVo;
import org.dromara.djs.warehouse.stock.service.IStockOverviewService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * 库存总览 Controller（WMS-STOCK-OVERVIEW-001，仓库-admin 行56）。
 *
 * <p>compute-on-read 回放 stock_flow，每日库存系统汇总（list）+ 按日下钻产品库存情况（detail）。
 * 权限串 {@code djs:warehouse:stockOverview:{list,query,export}}。</p>
 *
 * @author djs
 * @since WMS-STOCK-OVERVIEW-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/stockOverview")
public class StockOverviewController extends BaseController {

    private final IStockOverviewService stockOverviewService;

    /**
     * 每日库存系统汇总列表（行56 列表）。
     *
     * @param dateFrom 起始日期（含，可空，yyyy-MM-dd）
     * @param dateTo   截止日期（含，可空，yyyy-MM-dd）
     */
    @SaCheckPermission("djs:warehouse:stockOverview:list")
    @GetMapping("/list")
    public R<List<StockOverviewDailyVo>> list(
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(stockOverviewService.queryDaily(dateFrom, dateTo));
    }

    /**
     * 某自然日库存明细（行56 详情弹窗）：按 产品 × 库位 的库存情况。
     *
     * @param date        统计自然日（必传，yyyy-MM-dd）
     * @param productName 产品名称模糊（可空）
     * @param locationId  库位 id 精确（可空）
     */
    @SaCheckPermission("djs:warehouse:stockOverview:query")
    @GetMapping("/detail")
    public R<List<StockOverviewDetailVo>> detail(
        @RequestParam String date,
        @RequestParam(required = false) String productName,
        @RequestParam(required = false) Long locationId) {
        return R.ok(stockOverviewService.queryDetail(date, productName, locationId));
    }

    /**
     * 导出某自然日库存明细（行56 详情导出）。
     *
     * @param date        统计自然日（必传，yyyy-MM-dd）
     * @param productName 产品名称模糊（可空）
     * @param locationId  库位 id 精确（可空）
     */
    @SaCheckPermission("djs:warehouse:stockOverview:export")
    @Log(title = "库存总览", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(
        @RequestParam String date,
        @RequestParam(required = false) String productName,
        @RequestParam(required = false) Long locationId,
        HttpServletResponse response) {
        List<StockOverviewDetailVo> list = stockOverviewService.queryDetail(date, productName, locationId);
        ExcelUtil.exportExcel(list, "库存总览_" + date, StockOverviewDetailVo.class, response);
    }

}
