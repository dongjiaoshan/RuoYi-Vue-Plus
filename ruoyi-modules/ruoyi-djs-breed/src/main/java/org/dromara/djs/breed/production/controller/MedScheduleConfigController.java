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
import org.dromara.djs.breed.production.domain.bo.MedScheduleConfigBo;
import org.dromara.djs.breed.production.domain.query.MedScheduleConfigQuery;
import org.dromara.djs.breed.production.domain.vo.MedScheduleConfigVo;
import org.dromara.djs.breed.production.service.IMedScheduleConfigService;
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
 * 药品 / 疫苗周期配置 Controller（BRD-MD-003 Tab3）。
 *
 * <p>权限串 {@code djs:breed:production-med:*}，菜单 {@code 7090-7099}。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/production/med")
public class MedScheduleConfigController extends BaseController {

    private final IMedScheduleConfigService medScheduleConfigService;

    @SaCheckPermission("djs:breed:production-med:list")
    @GetMapping("/list")
    public TableDataInfo<MedScheduleConfigVo> list(MedScheduleConfigQuery query, PageQuery pageQuery) {
        return medScheduleConfigService.queryPageList(query, pageQuery);
    }

    @SaCheckPermission("djs:breed:production-med:export")
    @Log(title = "药品周期配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(MedScheduleConfigQuery query, HttpServletResponse response) {
        List<MedScheduleConfigVo> list = medScheduleConfigService.queryList(query);
        ExcelUtil.exportExcel(list, "药品周期配置", MedScheduleConfigVo.class, response);
    }

    @SaCheckPermission("djs:breed:production-med:list")
    @GetMapping("/getInfo/{id}")
    public R<MedScheduleConfigVo> getInfo(@PathVariable Long id) {
        return R.ok(medScheduleConfigService.queryById(id));
    }

    @SaCheckPermission("djs:breed:production-med:add")
    @Log(title = "药品周期配置", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody MedScheduleConfigBo bo) {
        return toAjax(medScheduleConfigService.insertByBo(bo));
    }

    @SaCheckPermission("djs:breed:production-med:edit")
    @Log(title = "药品周期配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody MedScheduleConfigBo bo) {
        return toAjax(medScheduleConfigService.updateByBo(bo));
    }

    @SaCheckPermission("djs:breed:production-med:remove")
    @Log(title = "药品周期配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(medScheduleConfigService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
