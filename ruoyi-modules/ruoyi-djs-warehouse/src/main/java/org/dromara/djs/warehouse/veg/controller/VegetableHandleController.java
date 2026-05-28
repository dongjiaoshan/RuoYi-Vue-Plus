package org.dromara.djs.warehouse.veg.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.veg.domain.query.VegHandleQuery;
import org.dromara.djs.warehouse.veg.domain.vo.HandleRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegetableHandleVo;
import org.dromara.djs.warehouse.veg.service.IVegetableHandleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 毛菜处理 admin 端 Controller（WMS-VEG-001）。
 *
 * <p>admin 端只读：list / 详情 / 详情下钻 records / export。
 * 写入由 mp 端 {@link org.dromara.djs.warehouse.veg.controller.applet.AppletVegHandleController}。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/vegHandle")
public class VegetableHandleController extends BaseController {

    private final IVegetableHandleService service;

    @SaCheckPermission("djs:warehouse:vegHandle:list")
    @GetMapping("/list")
    public TableDataInfo<VegetableHandleVo> list(VegHandleQuery query, PageQuery pageQuery) {
        return service.queryPageList(query, pageQuery);
    }

    @SaCheckPermission("djs:warehouse:vegHandle:list")
    @GetMapping("/{id}")
    public R<VegetableHandleVo> getInfo(@NotNull @PathVariable Long id) {
        return R.ok(service.queryById(id));
    }

    /**
     * 详情下钻：本汇总下的所有处理流水。
     */
    @SaCheckPermission("djs:warehouse:vegHandle:list")
    @GetMapping("/{id}/records")
    public R<List<HandleRecordVo>> records(@NotNull @PathVariable Long id) {
        return R.ok(service.listRecords(id));
    }

    @SaCheckPermission("djs:warehouse:vegHandle:export")
    @PostMapping("/export")
    public void export(VegHandleQuery query, HttpServletResponse response) {
        List<VegetableHandleVo> list = service.queryList(query);
        ExcelUtil.exportExcel(list, "毛菜处理汇总", VegetableHandleVo.class, response);
    }

}
