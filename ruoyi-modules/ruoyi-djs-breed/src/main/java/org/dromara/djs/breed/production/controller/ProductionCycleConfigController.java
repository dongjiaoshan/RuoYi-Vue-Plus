package org.dromara.djs.breed.production.controller;

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
import org.dromara.djs.breed.production.domain.bo.ProductionCycleConfigBo;
import org.dromara.djs.breed.production.domain.query.ProductionCycleConfigQuery;
import org.dromara.djs.breed.production.domain.vo.ProductionCycleConfigVo;
import org.dromara.djs.breed.production.service.IProductionCycleConfigService;
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
 * 生产周期配置 Controller（BRD-MD-003 Tab1）。
 *
 * <p>权限串 {@code djs:breed:production-cycle:*}，菜单 {@code 7050-7059}。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/production/cycle")
public class ProductionCycleConfigController extends BaseController {

    private final IProductionCycleConfigService cycleConfigService;

    /**
     * 分页查询。
     */
    @SaCheckPermission("djs:breed:production-cycle:list")
    @GetMapping("/list")
    public TableDataInfo<ProductionCycleConfigVo> list(ProductionCycleConfigQuery query, PageQuery pageQuery) {
        return cycleConfigService.queryPageList(query, pageQuery);
    }

    /**
     * 导出。
     */
    @SaCheckPermission("djs:breed:production-cycle:export")
    @Log(title = "生产周期配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ProductionCycleConfigQuery query, HttpServletResponse response) {
        List<ProductionCycleConfigVo> list = cycleConfigService.queryList(query);
        ExcelUtil.exportExcel(list, "生产周期配置", ProductionCycleConfigVo.class, response);
    }

    /**
     * 详情。
     */
    @SaCheckPermission("djs:breed:production-cycle:list")
    @GetMapping("/getInfo/{id}")
    public R<ProductionCycleConfigVo> getInfo(@PathVariable Long id) {
        return R.ok(cycleConfigService.queryById(id));
    }

    /**
     * 新增。
     */
    @SaCheckPermission("djs:breed:production-cycle:add")
    @Log(title = "生产周期配置", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody ProductionCycleConfigBo bo) {
        return toAjax(cycleConfigService.insertByBo(bo));
    }

    /**
     * 编辑（admin 实际只改 customValue）。
     */
    @SaCheckPermission("djs:breed:production-cycle:edit")
    @Log(title = "生产周期配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody ProductionCycleConfigBo bo) {
        return toAjax(cycleConfigService.updateByBo(bo));
    }

    /**
     * 软删（支持批量逗号）。
     */
    @SaCheckPermission("djs:breed:production-cycle:remove")
    @Log(title = "生产周期配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(cycleConfigService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
