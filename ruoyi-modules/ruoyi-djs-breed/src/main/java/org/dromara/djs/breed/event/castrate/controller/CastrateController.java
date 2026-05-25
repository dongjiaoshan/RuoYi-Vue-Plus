package org.dromara.djs.breed.event.castrate.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.castrate.domain.bo.CastrateBo;
import org.dromara.djs.breed.event.castrate.domain.query.CastrateQuery;
import org.dromara.djs.breed.event.castrate.domain.vo.CastrateRecordVo;
import org.dromara.djs.breed.event.castrate.service.ICastrateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阉割事件 Controller（BRD-EVENT-004 CASTRATE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event/castrate")
public class CastrateController extends BaseController {

    private final ICastrateService castrateService;

    @SaCheckPermission("djs:breed:event:castrate")
    @Log(title = "阉割录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<CastrateRecordVo> record(@Validated @RequestBody CastrateBo bo) {
        return R.ok(castrateService.recordCastrate(bo));
    }

    @SaCheckPermission("djs:breed:event:castrate:list")
    @GetMapping("/list")
    public TableDataInfo<CastrateRecordVo> list(CastrateQuery query, PageQuery pageQuery) {
        return castrateService.queryPage(query, pageQuery);
    }
}
