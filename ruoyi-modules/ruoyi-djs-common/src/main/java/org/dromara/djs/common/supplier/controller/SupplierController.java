package org.dromara.djs.common.supplier.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.common.supplier.domain.bo.SupplierBo;
import org.dromara.djs.common.supplier.domain.query.SupplierQuery;
import org.dromara.djs.common.supplier.domain.vo.SupplierVo;
import org.dromara.djs.common.supplier.service.ISupplierService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 供应商主数据 Controller（SYS-MD-003）。
 *
 * <p>5 端点 list / getInfo / add / edit / remove + export；
 * 权限串 {@code djs:common:supplier:{list,add,edit,remove,export}}。</p>
 *
 * @author djs
 * @since SYS-MD-003
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/common/supplier")
public class SupplierController extends BaseController {

    private final ISupplierService supplierService;

    /**
     * 分页查询供应商列表。
     */
    @SaCheckPermission("djs:common:supplier:list")
    @GetMapping("/list")
    public TableDataInfo<SupplierVo> list(SupplierQuery query, PageQuery pageQuery) {
        return supplierService.queryPageList(query, pageQuery);
    }

    /**
     * 导出供应商列表（不分页，应用相同筛选条件）。
     */
    @SaCheckPermission("djs:common:supplier:export")
    @Log(title = "供应商管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SupplierQuery query, HttpServletResponse response) {
        List<SupplierVo> list = supplierService.queryList(query);
        ExcelUtil.exportExcel(list, "供应商数据", SupplierVo.class, response);
    }

    /**
     * 根据 ID 查询供应商详情。
     */
    @SaCheckPermission("djs:common:supplier:list")
    @GetMapping("/getInfo/{id}")
    public R<SupplierVo> getInfo(@PathVariable Long id) {
        return R.ok(supplierService.queryById(id));
    }

    /**
     * 新增供应商。
     */
    @SaCheckPermission("djs:common:supplier:add")
    @Log(title = "供应商管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody SupplierBo bo) {
        return toAjax(supplierService.insertByBo(bo));
    }

    /**
     * 编辑供应商。
     */
    @SaCheckPermission("djs:common:supplier:edit")
    @Log(title = "供应商管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody SupplierBo bo) {
        return toAjax(supplierService.updateByBo(bo));
    }

    /**
     * 删除供应商（支持批量，逗号分隔）。
     */
    @SaCheckPermission("djs:common:supplier:remove")
    @Log(title = "供应商管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(supplierService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
