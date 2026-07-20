package org.dromara.djs.breed.event.heat.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.heat.domain.bo.HeatBo;
import org.dromara.djs.breed.event.heat.domain.query.HeatQuery;
import org.dromara.djs.breed.event.heat.domain.vo.PigHeatVo;
import org.dromara.djs.breed.event.heat.service.IHeatService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查情事件 Controller（BRD-EVENT-002 OESTRUS）。
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event/heat")
public class HeatController extends BaseController {

    private final IHeatService heatService;

    @SaCheckPermission("djs:breed:event:heat")
    @Log(title = "查情录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<PigHeatVo> record(@Validated @RequestBody HeatBo bo) {
        return R.ok(heatService.recordHeat(bo));
    }

    @SaCheckPermission("djs:breed:event:heat:list")
    @GetMapping("/list")
    public TableDataInfo<PigHeatVo> list(HeatQuery query, PageQuery pageQuery) {
        return heatService.queryPage(query, pageQuery);
    }
}
