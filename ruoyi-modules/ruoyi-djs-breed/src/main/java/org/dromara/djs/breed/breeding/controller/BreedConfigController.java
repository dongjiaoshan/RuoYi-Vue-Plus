package org.dromara.djs.breed.breeding.controller;

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
import org.dromara.djs.breed.breeding.domain.bo.BreedConfigBo;
import org.dromara.djs.breed.breeding.domain.query.BreedConfigQuery;
import org.dromara.djs.breed.breeding.domain.vo.BreedConfigVo;
import org.dromara.djs.breed.breeding.service.IBreedConfigService;
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
 * 育种配置（配种关系）Controller（BRD-MD-001）。
 *
 * <p>权限串与 {@link BreedInfoController} 共用 {@code djs:breed:breeding:*}。</p>
 *
 * @author djs
 * @since BRD-MD-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/breeding/config")
public class BreedConfigController extends BaseController {

    private final IBreedConfigService breedConfigService;

    /**
     * 分页查询配种关系列表（admin 4 tab 按 breedStrain 过滤）。
     */
    @SaCheckPermission("djs:breed:breeding:list")
    @GetMapping("/list")
    public TableDataInfo<BreedConfigVo> list(BreedConfigQuery query, PageQuery pageQuery) {
        return breedConfigService.queryPageList(query, pageQuery);
    }

    /**
     * 导出。
     */
    @SaCheckPermission("djs:breed:breeding:export")
    @Log(title = "育种配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BreedConfigQuery query, HttpServletResponse response) {
        List<BreedConfigVo> list = breedConfigService.queryList(query);
        ExcelUtil.exportExcel(list, "育种配置", BreedConfigVo.class, response);
    }

    /**
     * 详情。
     */
    @SaCheckPermission("djs:breed:breeding:list")
    @GetMapping("/getInfo/{id}")
    public R<BreedConfigVo> getInfo(@PathVariable Long id) {
        return R.ok(breedConfigService.queryById(id));
    }

    /**
     * 新增。
     */
    @SaCheckPermission("djs:breed:breeding:add")
    @Log(title = "育种配置", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody BreedConfigBo bo) {
        return toAjax(breedConfigService.insertByBo(bo));
    }

    /**
     * 编辑。
     */
    @SaCheckPermission("djs:breed:breeding:edit")
    @Log(title = "育种配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody BreedConfigBo bo) {
        return toAjax(breedConfigService.updateByBo(bo));
    }

    /**
     * 软删（支持批量逗号）。
     */
    @SaCheckPermission("djs:breed:breeding:remove")
    @Log(title = "育种配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(breedConfigService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
