package org.dromara.djs.warehouse.loss.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.loss.domain.vo.LossOverviewDailyVo;
import org.dromara.djs.warehouse.loss.domain.vo.LossOverviewDetailVo;
import org.dromara.djs.warehouse.loss.service.ILossOverviewService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * 损耗总览 Controller（WMS-LOSS-OVERVIEW-001，仓库-admin 行63）。
 *
 * <p>compute-on-read over {@code t_warehouse_loss_flow}，admin 只读：list 每日汇总 + detail 当日明细。
 * 权限串 {@code djs:warehouse:loss:overview:{list,query}}。</p>
 *
 * @author djs
 * @since WMS-LOSS-OVERVIEW-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/lossOverview")
public class LossOverviewController extends BaseController {

    private final ILossOverviewService lossOverviewService;

    /**
     * 每日损耗汇总列表（行63）：日期 + 当日有损耗的产品/商品 distinct 数量。
     *
     * <p>compute-on-read，按日期倒序返回全量（按日汇总行数小，不分页）。</p>
     *
     * @param dateFrom 起始日期（含，可空；yyyy-MM-dd）
     * @param dateTo   截止日期（含，可空；yyyy-MM-dd）
     */
    @SaCheckPermission("djs:warehouse:loss:overview:list")
    @GetMapping("/list")
    public R<List<LossOverviewDailyVo>> list(
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(lossOverviewService.dailyOverview(dateFrom, dateTo));
    }

    /**
     * 当日损耗明细（行63 详情弹窗）：当日有损耗的产品明细。
     *
     * @param date        统计自然日（必传，yyyy-MM-dd）
     * @param productName 产品名称模糊（可空，支持输入）
     * @param lossType    损耗类型 djs_loss_type（可空，下拉）
     */
    @SaCheckPermission("djs:warehouse:loss:overview:query")
    @GetMapping("/detail")
    public R<List<LossOverviewDetailVo>> detail(
        @RequestParam String date,
        @RequestParam(required = false) String productName,
        @RequestParam(required = false) String lossType) {
        return R.ok(lossOverviewService.dailyDetail(date, productName, lossType));
    }
}
