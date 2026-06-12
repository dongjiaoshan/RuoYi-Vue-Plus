package org.dromara.djs.warehouse.outsource.controller;

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
import org.dromara.djs.warehouse.outsource.domain.bo.OutsourcePigBo;
import org.dromara.djs.warehouse.outsource.domain.query.OutsourcePigQuery;
import org.dromara.djs.warehouse.outsource.domain.vo.OutsourcePigVo;
import org.dromara.djs.warehouse.outsource.service.IOutsourcePigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 外购猪只 Controller（DJS-FIX-WMS-RALN，原型「产品生产管理/外购猪只」）。
 *
 * <p>原型仅 新增 + 删除（无编辑），故端点为 list / getInfo / add / remove + export。
 * 权限串 {@code djs:warehouse:outsourcePig:{list,add,remove,export}}（菜单 seed 在
 * {@code V202606280900__DJS-FIX-WMS-RALN-menu.sql}，C=9028 / F=9290-9293）。</p>
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/outsourcePig")
public class OutsourcePigController extends BaseController {

    private final IOutsourcePigService outsourcePigService;

    /**
     * 分页查询外购猪只列表。
     */
    @SaCheckPermission("djs:warehouse:outsourcePig:list")
    @GetMapping("/list")
    public TableDataInfo<OutsourcePigVo> list(OutsourcePigQuery query, PageQuery pageQuery) {
        return outsourcePigService.queryPageList(query, pageQuery);
    }

    /**
     * 导出外购猪只列表（不分页）。
     */
    @SaCheckPermission("djs:warehouse:outsourcePig:export")
    @Log(title = "外购猪只", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(OutsourcePigQuery query, HttpServletResponse response) {
        List<OutsourcePigVo> list = outsourcePigService.queryList(query);
        ExcelUtil.exportExcel(list, "外购猪只数据", OutsourcePigVo.class, response);
    }

    /**
     * 根据 ID 查询外购猪只详情。
     */
    @SaCheckPermission("djs:warehouse:outsourcePig:list")
    @GetMapping("/getInfo/{id}")
    public R<OutsourcePigVo> getInfo(@PathVariable Long id) {
        return R.ok(outsourcePigService.queryById(id));
    }

    /**
     * 新增外购猪只（必填：购买日期 / 送宰日期 / 毛猪重量 / 供应商）。
     */
    @SaCheckPermission("djs:warehouse:outsourcePig:add")
    @Log(title = "外购猪只", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody OutsourcePigBo bo) {
        return toAjax(outsourcePigService.insertByBo(bo));
    }

    /**
     * 删除外购猪只（支持批量）。
     */
    @SaCheckPermission("djs:warehouse:outsourcePig:remove")
    @Log(title = "外购猪只", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(outsourcePigService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
