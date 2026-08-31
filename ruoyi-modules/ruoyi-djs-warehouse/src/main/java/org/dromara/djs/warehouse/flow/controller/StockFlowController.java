package org.dromara.djs.warehouse.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.djs.common.excel.DjsExcelUtil;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.flow.domain.query.StockFlowQuery;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;
import org.dromara.djs.warehouse.flow.service.IStockFlowService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 出入库流水 admin 端 Controller（WMS-MAT-001 → 预热 D11 WMS-FLOW-001）。
 *
 * <p>仅查询：list / getInfo / export。写入由 mp 端 + burn / cut / mat 等 service。</p>
 *
 * <p>{@code adjust} 调账按钮（D11 WMS-FLOW-001 完整实现）当前不暴露端点；admin 页面按钮 placeholder。</p>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/stockFlow")
public class StockFlowController extends BaseController {

    private final IStockFlowService service;

    @SaCheckPermission("djs:warehouse:stockFlow:list")
    @GetMapping("/list")
    public TableDataInfo<StockFlowVo> list(StockFlowQuery query, PageQuery pageQuery) {
        return service.queryPageList(query, pageQuery);
    }

    @SaCheckPermission("djs:warehouse:stockFlow:list")
    @GetMapping("/{id}")
    public R<StockFlowVo> getInfo(@NotNull @PathVariable Long id) {
        return R.ok(service.queryById(id));
    }

    /**
     * 「供应商」列只出现在入库记录导出（甲方 V6-R139 只要了那一页）。
     *
     * <p>{@link StockFlowVo} 被三个导出端点共用，字段上挂了 {@code @ExcelProperty} 就三张表都会长出这列。
     * 而 {@code supplier_id} 的 4 条写入路径全是入库方向 —— 出库导出这列恒空，是纯垃圾列；
     * 出入库流水导出本轮甲方也没要求加。两处按字段名排除掉。</p>
     */
    private static final List<String> EXCLUDE_SUPPLIER = List.of("supplierName");

    @SaCheckPermission("djs:warehouse:stockFlow:export")
    @PostMapping("/export")
    public void export(StockFlowQuery query, HttpServletResponse response) {
        List<StockFlowVo> list = service.queryList(query);
        DjsExcelUtil.exportExcelExcluding(list, "出入库流水", StockFlowVo.class, EXCLUDE_SUPPLIER, response);
    }

    // ============ D11 WMS-FLOW-001：admin 入库 / 出库记录两页（强制 inout_type）============

    /**
     * 入库记录分页（强制 {@code inout_type=IN}）。
     */
    @SaCheckPermission("djs:warehouse:flowIn:list")
    @GetMapping("/in/list")
    public TableDataInfo<StockFlowVo> inList(StockFlowQuery query, PageQuery pageQuery) {
        return service.queryInList(query, pageQuery);
    }

    /**
     * 出库记录分页（强制 {@code inout_type=OT}）。
     */
    @SaCheckPermission("djs:warehouse:flowOut:list")
    @GetMapping("/out/list")
    public TableDataInfo<StockFlowVo> outList(StockFlowQuery query, PageQuery pageQuery) {
        return service.queryOutList(query, pageQuery);
    }

    /**
     * 入库记录导出（强制 {@code inout_type=IN}）。
     */
    @SaCheckPermission("djs:warehouse:flowIn:export")
    @PostMapping("/in/export")
    public void inExport(StockFlowQuery query, HttpServletResponse response) {
        List<StockFlowVo> list = service.queryInExport(query);
        ExcelUtil.exportExcel(list, "入库记录", StockFlowVo.class, response);
    }

    /**
     * 出库记录导出（强制 {@code inout_type=OT}）。
     */
    @SaCheckPermission("djs:warehouse:flowOut:export")
    @PostMapping("/out/export")
    public void outExport(StockFlowQuery query, HttpServletResponse response) {
        List<StockFlowVo> list = service.queryOutExport(query);
        DjsExcelUtil.exportExcelExcluding(list, "出库记录", StockFlowVo.class, EXCLUDE_SUPPLIER, response);
    }

}
