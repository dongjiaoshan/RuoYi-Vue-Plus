package org.dromara.djs.warehouse.pack.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.pack.domain.query.ProductProductionQuery;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionVo;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 发货产品生产记录 admin 端 Controller（WMS-PACK-001）。
 *
 * <p>admin 端只读：list / 详情 / export。写入由 mp 端
 * {@link org.dromara.djs.warehouse.pack.controller.applet.AppletPackController}。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/production")
public class ProductProductionController extends BaseController {

    private final IProductProductionService service;

    /**
     * 列表（分页 + 多维筛选）。
     */
    @SaCheckPermission("djs:warehouse:production:list")
    @GetMapping("/list")
    public TableDataInfo<ProductProductionVo> list(ProductProductionQuery query, PageQuery pageQuery) {
        return service.queryPageList(query, pageQuery);
    }

    /**
     * 详情。
     */
    @SaCheckPermission("djs:warehouse:production:list")
    @GetMapping("/{id}")
    public R<ProductProductionVo> getInfo(@NotNull @PathVariable Long id) {
        return R.ok(service.queryById(id));
    }

    /**
     * 导出 Excel。
     */
    @SaCheckPermission("djs:warehouse:production:export")
    @PostMapping("/export")
    public void export(ProductProductionQuery query, HttpServletResponse response) {
        List<ProductProductionVo> list = service.queryList(query);
        ExcelUtil.exportExcel(list, "发货产品生产记录", ProductProductionVo.class, response);
    }

}
