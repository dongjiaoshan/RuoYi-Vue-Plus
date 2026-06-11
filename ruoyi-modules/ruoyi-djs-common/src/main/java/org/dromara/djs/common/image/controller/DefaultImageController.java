package org.dromara.djs.common.image.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.common.image.domain.bo.DefaultImageBo;
import org.dromara.djs.common.image.domain.vo.DefaultImageVo;
import org.dromara.djs.common.image.service.IDefaultImageService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类默认图 Controller（IMG-LIB-001）。
 *
 * <p>权限串 {@code djs:common:defaultImage:{list,edit}}。固定 7 行（6 belong_type + global），只编辑 ossId。</p>
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/common/defaultImage")
public class DefaultImageController extends BaseController {

    private final IDefaultImageService defaultImageService;

    /**
     * 查全部分类默认图（带 imageUrl 预览）。
     */
    @SaCheckPermission("djs:common:defaultImage:list")
    @GetMapping("/list")
    public R<List<DefaultImageVo>> list() {
        return R.ok(defaultImageService.queryAll());
    }

    /**
     * 配置某分类默认图的 ossId。
     */
    @SaCheckPermission("djs:common:defaultImage:edit")
    @Log(title = "分类默认图", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody DefaultImageBo bo) {
        return toAjax(defaultImageService.updateByBo(bo));
    }

}
