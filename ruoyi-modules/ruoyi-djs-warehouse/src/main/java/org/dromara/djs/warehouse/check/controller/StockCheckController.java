package org.dromara.djs.warehouse.check.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.check.domain.bo.StockCheckCreateBo;
import org.dromara.djs.warehouse.check.domain.query.StockCheckQuery;
import org.dromara.djs.warehouse.check.domain.vo.StockCheckHeaderVo;
import org.dromara.djs.warehouse.check.domain.vo.StockCheckRecordVo;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存盘点 admin 端 Controller（WMS-STOCK-001）。
 *
 * <p>5 端点：盘点单列表（header 维度）/ 明细 line 列表 / 新建（锁库位）/ 完成（回写解锁）/ 取消 / 导出。
 * 权限串 {@code djs:warehouse:check:{list,query,add,complete,cancel,export}}（menu 9250-9255）。</p>
 *
 * @author djs
 * @since WMS-STOCK-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/check")
public class StockCheckController extends BaseController {

    private final IStockCheckService checkService;

    /**
     * 盘点记录列表（只读，盘点单维度）：统一读 stock_flow 盘点流水聚合，
     * 覆盖小程序端工人提交的盘点 + admin 完成盘点两路来源。
     */
    @SaCheckPermission("djs:warehouse:check:list")
    @GetMapping("/list")
    public TableDataInfo<StockCheckHeaderVo> list(StockCheckQuery query, PageQuery pageQuery) {
        return checkService.queryFlowCheckRecordPage(query, pageQuery);
    }

    /**
     * 盘点明细 line 列表（按 checkId / locationId 查某盘点单的逐项核对明细）。
     */
    @SaCheckPermission("djs:warehouse:check:query")
    @GetMapping("/lines")
    public R<List<StockCheckRecordVo>> lines(StockCheckQuery query) {
        return R.ok(checkService.queryLineList(query));
    }

    /**
     * 新建盘点单（INSERT header status='in_progress' → 锁库位）。
     */
    @SaCheckPermission("djs:warehouse:check:add")
    @Log(title = "库存盘点", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody StockCheckCreateBo bo) {
        return R.ok(checkService.createCheck(bo));
    }

    /**
     * 完成盘点（写差异流水 + 回写 location_stock + 解锁库位）。
     */
    @SaCheckPermission("djs:warehouse:check:complete")
    @Log(title = "库存盘点", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{id}")
    public R<Void> complete(@PathVariable Long id) {
        checkService.completeCheck(id);
        return R.ok();
    }

    /**
     * 取消盘点（解锁库位，不回写库存、不写流水）。
     */
    @SaCheckPermission("djs:warehouse:check:cancel")
    @Log(title = "库存盘点", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{id}")
    public R<Void> cancel(@PathVariable Long id) {
        checkService.cancelCheck(id);
        return R.ok();
    }

    /**
     * 导出盘点记录列表（不分页，盘点单维度，与列表同数据源 stock_flow 聚合）。
     */
    @SaCheckPermission("djs:warehouse:check:export")
    @Log(title = "库存盘点", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(StockCheckQuery query, HttpServletResponse response) {
        List<StockCheckHeaderVo> list = checkService.exportHeaderList(query);
        ExcelUtil.exportExcel(list, "盘点记录", StockCheckHeaderVo.class, response);
    }

}
