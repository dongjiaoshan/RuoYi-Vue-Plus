package org.dromara.djs.warehouse.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
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

    @SaCheckPermission("djs:warehouse:stockFlow:export")
    @PostMapping("/export")
    public void export(StockFlowQuery query, HttpServletResponse response) {
        List<StockFlowVo> list = service.queryList(query);
        ExcelUtil.exportExcel(list, "出入库流水", StockFlowVo.class, response);
    }

}
