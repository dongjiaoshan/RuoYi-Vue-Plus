package org.dromara.djs.breed.farm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.farm.domain.bo.FarmContactBo;
import org.dromara.djs.breed.farm.domain.vo.FarmInfoVo;
import org.dromara.djs.breed.farm.service.IFarmInfoService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 农场主数据 Controller（BRD-MD-002）。
 *
 * <p>V1 单农场（ADR-0001）—— admin 不暴露 list / add / remove，
 * 仅提供 2 个端点：</p>
 * <ul>
 *   <li>{@code GET /current} → {@code djs:breed:farm-info:query}：查 id=1001 详情</li>
 *   <li>{@code PUT /contact} → {@code djs:breed:farm-info:edit}：改 contact 三字段</li>
 * </ul>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/farm-info")
public class FarmInfoController extends BaseController {

    private final IFarmInfoService farmInfoService;

    /**
     * 查询当前农场详情（V1 固定 id=1001）。
     */
    @SaCheckPermission("djs:breed:farm-info:query")
    @GetMapping("/current")
    public R<FarmInfoVo> current() {
        return R.ok(farmInfoService.queryCurrent());
    }

    /**
     * 修改联系信息（仅 contact_name / contact_phone / address）。
     */
    @SaCheckPermission("djs:breed:farm-info:edit")
    @Log(title = "农场联系信息", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/contact")
    public R<Void> updateContact(@Validated @RequestBody FarmContactBo bo) {
        return toAjax(farmInfoService.updateContact(bo));
    }

}
