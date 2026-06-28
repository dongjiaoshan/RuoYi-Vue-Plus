package org.dromara.djs.warehouse.stat.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseCroppRecordVo;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseIndicatorRecordVo;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseMonthlyRecordVo;
import org.dromara.djs.warehouse.stat.service.IWarehouseStatService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 仓库统计指标 Controller（WMS-STAT-001，邓博 admin row16/17/18）。
 *
 * <p>3 个只读列表（仓库日报表 / 作物日报表 / 仓库月报表）+ 1 个手动补算端点。
 * 全部走只读权限串 {@code djs:warehouse:stat:list}。落盘数据由 WarehouseStatJob 每晚跑批。</p>
 *
 * @author djs
 * @since WMS-STAT-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/stat")
public class WarehouseStatController extends BaseController {

    private final IWarehouseStatService warehouseStatService;

    /**
     * 仓库日报表（row16）：按日期范围倒序返回全量（前端客户端分页）。
     *
     * @param dateFrom 起始日期（含，可空，yyyy-MM-dd）
     * @param dateTo   截止日期（含，可空，yyyy-MM-dd）
     */
    @SaCheckPermission("djs:warehouse:stat:list")
    @GetMapping("/daily")
    public R<List<WarehouseIndicatorRecordVo>> daily(
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo) {
        return R.ok(warehouseStatService.listDaily(dateFrom, dateTo));
    }

    /**
     * 作物日报表（row17）：按日期范围 + JOIN 作物信息，日期倒序 + crop_id 升序（前端客户端分页）。
     *
     * @param dateFrom 起始日期（含，可空，yyyy-MM-dd）
     * @param dateTo   截止日期（含，可空，yyyy-MM-dd）
     */
    @SaCheckPermission("djs:warehouse:stat:list")
    @GetMapping("/crop")
    public R<List<WarehouseCroppRecordVo>> crop(
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo) {
        return R.ok(warehouseStatService.listCrop(dateFrom, dateTo));
    }

    /**
     * 仓库月报表（row18）：按月份范围倒序返回全量（前端客户端分页）。
     *
     * @param monthFrom 起始月份 yyyy-MM（含，可空）
     * @param monthTo   截止月份 yyyy-MM（含，可空）
     */
    @SaCheckPermission("djs:warehouse:stat:list")
    @GetMapping("/monthly")
    public R<List<WarehouseMonthlyRecordVo>> monthly(
        @RequestParam(required = false) String monthFrom,
        @RequestParam(required = false) String monthTo) {
        return R.ok(warehouseStatService.listMonthly(monthFrom, monthTo));
    }

    /**
     * 手动触发聚合落盘（补算 / 验证用）。不传 date = T-1 昨天。
     *
     * <p>会落盘指定日的日表 + 作物日表，并刷新该日所在月的月表汇总（与定时 job 同源）。</p>
     *
     * @param date 目标统计日（可空，yyyy-MM-dd；空 = T-1）
     */
    @SaCheckPermission("djs:warehouse:stat:list")
    @PostMapping("/trigger-aggregate")
    public R<String> triggerAggregate(
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return R.ok(warehouseStatService.aggregate(date));
    }
}
