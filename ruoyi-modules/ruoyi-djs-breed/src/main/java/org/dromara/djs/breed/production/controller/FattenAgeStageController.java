package org.dromara.djs.breed.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.production.domain.bo.FattenAgeStageBo;
import org.dromara.djs.breed.production.domain.vo.FattenAgeStageVo;
import org.dromara.djs.breed.production.service.IFattenAgeStageService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 育肥日龄阶段配置 Controller（A1 育肥生产配置 Tab2）。
 *
 * <p>权限串 {@code djs:breed:fatten-stage:*}，菜单挂生产配置主菜单 7050 下（7056-7059）。</p>
 *
 * @author djs
 * @since A1
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/production/fatten-stage")
public class FattenAgeStageController extends BaseController {

    private final IFattenAgeStageService fattenAgeStageService;

    /**
     * 查询全部日龄阶段（按起始日龄升序，不分页）。
     */
    @SaCheckPermission("djs:breed:fatten-stage:list")
    @GetMapping("/list")
    public R<List<FattenAgeStageVo>> list() {
        return R.ok(fattenAgeStageService.queryList());
    }

    /**
     * 批量保存（整表覆盖：软删旧行 + 重灌入参全部行）。
     */
    @SaCheckPermission("djs:breed:fatten-stage:edit")
    @Log(title = "育肥日龄阶段", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/batchSave")
    public R<Void> batchSave(@Validated @RequestBody List<FattenAgeStageBo> stages) {
        fattenAgeStageService.batchSave(stages);
        return R.ok();
    }

    /**
     * 软删单行。
     */
    @SaCheckPermission("djs:breed:fatten-stage:edit")
    @Log(title = "育肥日龄阶段", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{id}")
    public R<Void> remove(@PathVariable Long id) {
        return toAjax(fattenAgeStageService.removeById(id));
    }

}
