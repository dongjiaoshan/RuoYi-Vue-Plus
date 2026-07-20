package org.dromara.djs.breed.event.slaughter.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.slaughter.domain.bo.SlaughterBo;
import org.dromara.djs.breed.event.slaughter.domain.query.SlaughterQuery;
import org.dromara.djs.breed.event.slaughter.domain.vo.PigMarketingVo;
import org.dromara.djs.breed.event.slaughter.service.ISlaughterService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 出栏事件 Controller（BRD-EVENT-004 SLAUGHTER）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event/slaughter")
public class SlaughterController extends BaseController {

    private final ISlaughterService slaughterService;

    @SaCheckPermission("djs:breed:event:slaughter")
    @Log(title = "出栏录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<PigMarketingVo> record(@Validated @RequestBody SlaughterBo bo) {
        return R.ok(slaughterService.recordSlaughter(bo));
    }

    @SaCheckPermission("djs:breed:event:slaughter:list")
    @GetMapping("/list")
    public TableDataInfo<PigMarketingVo> list(SlaughterQuery query, PageQuery pageQuery) {
        return slaughterService.queryPage(query, pageQuery);
    }
}
