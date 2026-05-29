package org.dromara.djs.warehouse.shipment.returnpkg.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnConfirmBo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnProductBo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.query.ReturnProductQuery;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.vo.ReturnProductVo;
import org.dromara.djs.warehouse.shipment.returnpkg.service.IReturnProductService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 退货管理 admin Controller（WMS-SHIP-001）。
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/return")
public class ReturnProductController extends BaseController {

    private final IReturnProductService service;

    /** 列表（分页）。 */
    @SaCheckPermission("djs:warehouse:return:list")
    @GetMapping("/list")
    public TableDataInfo<ReturnProductVo> list(ReturnProductQuery query, PageQuery pageQuery) {
        return service.queryPageList(query, pageQuery);
    }

    /** 详情。 */
    @SaCheckPermission("djs:warehouse:return:query")
    @GetMapping("/{id}")
    public R<ReturnProductVo> getInfo(@PathVariable Long id) {
        return R.ok(service.queryById(id));
    }

    /** 新增。 */
    @SaCheckPermission("djs:warehouse:return:add")
    @Log(title = "退货管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Valid @RequestBody ReturnProductBo bo) {
        return R.ok(service.insertByBo(bo));
    }

    /** 修改（仅 pending 可编辑）。 */
    @SaCheckPermission("djs:warehouse:return:edit")
    @Log(title = "退货管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public R<Void> edit(@Valid @RequestBody ReturnProductBo bo) {
        return toAjax(service.updateByBo(bo));
    }

    /** 软删（支持批量）。 */
    @SaCheckPermission("djs:warehouse:return:remove")
    @Log(title = "退货管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(service.deleteByIds(Arrays.asList(ids)));
    }

    /** 确认退货（核心入库联动）。 */
    @SaCheckPermission("djs:warehouse:return:confirm")
    @Log(title = "退货管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id, @Valid @RequestBody ReturnConfirmBo bo) {
        service.confirmReturn(id, bo);
        return R.ok();
    }

    /** 导出。 */
    @SaCheckPermission("djs:warehouse:return:export")
    @Log(title = "退货管理", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public void export(ReturnProductQuery query, HttpServletResponse response) {
        List<ReturnProductVo> list = service.queryList(query);
        ExcelUtil.exportExcel(list == null ? new ArrayList<>() : list,
            "退货管理", ReturnProductVo.class, response);
    }
}
