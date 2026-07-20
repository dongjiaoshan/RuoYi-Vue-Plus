package org.dromara.djs.breed.event.eliminate.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.eliminate.domain.bo.EliminateBo;
import org.dromara.djs.breed.event.eliminate.domain.query.EliminateQuery;
import org.dromara.djs.breed.event.eliminate.domain.vo.PigCullingVo;
import org.dromara.djs.breed.event.eliminate.service.IEliminateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 淘汰事件 Controller（BRD-EVENT-004 ELIMINATE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event/eliminate")
public class EliminateController extends BaseController {

    private final IEliminateService eliminateService;

    @SaCheckPermission("djs:breed:event:eliminate")
    @Log(title = "淘汰录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<PigCullingVo> record(@Validated @RequestBody EliminateBo bo) {
        return R.ok(eliminateService.recordEliminate(bo));
    }

    @SaCheckPermission("djs:breed:event:eliminate:list")
    @GetMapping("/list")
    public TableDataInfo<PigCullingVo> list(EliminateQuery query, PageQuery pageQuery) {
        return eliminateService.queryPage(query, pageQuery);
    }
}
