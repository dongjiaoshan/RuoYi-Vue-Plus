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
import org.dromara.djs.breed.production.domain.bo.BoarConfigBo;
import org.dromara.djs.breed.production.domain.query.BoarConfigQuery;
import org.dromara.djs.breed.production.domain.vo.BoarConfigVo;
import org.dromara.djs.breed.production.service.IBoarConfigService;
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
 * 精液 / 公猪配置 Controller（BRD-MD-003 Tab2）。
 *
 * <p>权限串 {@code djs:breed:production-boar:*}，菜单 {@code 7080-7089}。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/production/boar")
public class BoarConfigController extends BaseController {

    private final IBoarConfigService boarConfigService;

    @SaCheckPermission("djs:breed:production-boar:list")
    @GetMapping("/list")
    public TableDataInfo<BoarConfigVo> list(BoarConfigQuery query, PageQuery pageQuery) {
        return boarConfigService.queryPageList(query, pageQuery);
    }

    @SaCheckPermission("djs:breed:production-boar:export")
    @Log(title = "公猪配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BoarConfigQuery query, HttpServletResponse response) {
        List<BoarConfigVo> list = boarConfigService.queryList(query);
        ExcelUtil.exportExcel(list, "公猪配置", BoarConfigVo.class, response);
    }

    @SaCheckPermission("djs:breed:production-boar:list")
    @GetMapping("/getInfo/{id}")
    public R<BoarConfigVo> getInfo(@PathVariable Long id) {
        return R.ok(boarConfigService.queryById(id));
    }

    @SaCheckPermission("djs:breed:production-boar:add")
    @Log(title = "公猪配置", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody BoarConfigBo bo) {
        return toAjax(boarConfigService.insertByBo(bo));
    }

    @SaCheckPermission("djs:breed:production-boar:edit")
    @Log(title = "公猪配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody BoarConfigBo bo) {
        return toAjax(boarConfigService.updateByBo(bo));
    }

    @SaCheckPermission("djs:breed:production-boar:remove")
    @Log(title = "公猪配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(boarConfigService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
