package org.dromara.djs.warehouse.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.flow.domain.query.StockFlowQuery;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;
import org.dromara.djs.warehouse.flow.service.IStockFlowService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 包材领用 admin 端 Controller（WMS-MAT-001 9 类领用标杆）。
 *
 * <p>本 Controller 隐式过滤 {@code matType='package'}：admin 包材页只展示包材相关的 stock_flow，
 * 其他 8 个物资类（白条 / 蔬菜 / 鸡蛋 / 干货 / 果蔬 / 采食 / 药品 / 种子）V1.5 复用同模板，
 * 各自加 Controller 即可（matType 改成对应字典 value）。</p>
 *
 * <p>列表 / 导出共用 {@link IStockFlowService}（无独立 service）。</p>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/mat/pack")
public class MatPackController extends BaseController {

    /** 包材物资 matType / belong_type 字典 value。 */
    private static final String MAT_TYPE_PACKAGE = "package";

    private final IStockFlowService service;

    @SaCheckPermission("djs:warehouse:mat:pack:list")
    @GetMapping("/list")
    public TableDataInfo<StockFlowVo> list(StockFlowQuery query, PageQuery pageQuery) {
        forcePackageMatType(query);
        return service.queryPageList(query, pageQuery);
    }

    @SaCheckPermission("djs:warehouse:mat:pack:export")
    @PostMapping("/export")
    public void export(StockFlowQuery query, HttpServletResponse response) {
        forcePackageMatType(query);
        List<StockFlowVo> list = service.queryList(query);
        ExcelUtil.exportExcel(list, "包材流水", StockFlowVo.class, response);
    }

    /**
     * 强制把 matType 设为 package（即便前端传别的也覆盖）。
     */
    private void forcePackageMatType(StockFlowQuery query) {
        if (query != null) {
            query.setMatType(MAT_TYPE_PACKAGE);
        }
    }

}
