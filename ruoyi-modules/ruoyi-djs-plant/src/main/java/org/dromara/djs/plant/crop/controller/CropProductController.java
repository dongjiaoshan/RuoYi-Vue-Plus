package org.dromara.djs.plant.crop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.crop.domain.bo.CropProductBo;
import org.dromara.djs.plant.crop.domain.vo.CropProductVo;
import org.dromara.djs.plant.crop.service.ICropProductService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 作物关联产品配置 Controller（V6 row16「产品配置」页签）。
 *
 * <p>权限沿用作物主数据的 {@code djs:plant:crop:*} —— 产品配置是作物编辑弹窗内的一个页签，
 * 不是独立菜单，单独开一套权限只会让运营多授一遍。</p>
 *
 * @author djs
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/cropProduct")
public class CropProductController extends BaseController {

    private final ICropProductService service;

    /** 某作物的产品配置列表。 */
    @SaCheckPermission("djs:plant:crop:list")
    @GetMapping("/list")
    public R<List<CropProductVo>> list(@RequestParam Long cropId) {
        return R.ok(service.listByCrop(cropId));
    }

    @SaCheckPermission("djs:plant:crop:edit")
    @Log(title = "种植-作物产品配置", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Void> add(@Valid @RequestBody CropProductBo bo) {
        return toAjax(service.insertByBo(bo));
    }

    @SaCheckPermission("djs:plant:crop:edit")
    @Log(title = "种植-作物产品配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public R<Void> edit(@Valid @RequestBody CropProductBo bo) {
        return toAjax(service.updateByBo(bo));
    }

    @SaCheckPermission("djs:plant:crop:edit")
    @Log(title = "种植-作物产品配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(service.deleteByIds(Arrays.asList(ids)));
    }
}
