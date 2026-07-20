package org.dromara.djs.breed.event.die.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.die.domain.bo.DieBo;
import org.dromara.djs.breed.event.die.domain.query.DieQuery;
import org.dromara.djs.breed.event.die.domain.vo.PigDeathVo;
import org.dromara.djs.breed.event.die.service.IDieService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 死亡事件 Controller（BRD-EVENT-004 DIE）。
 *
 * <ul>
 *   <li>{@code POST /djs/breed/event/die}      mp 端死亡录入</li>
 *   <li>{@code GET  /djs/breed/event/die/list} admin 端只读列表</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event/die")
public class DieController extends BaseController {

    private final IDieService dieService;

    @SaCheckPermission("djs:breed:event:die")
    @Log(title = "死亡录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<PigDeathVo> record(@Validated @RequestBody DieBo bo) {
        return R.ok(dieService.recordDie(bo));
    }

    @SaCheckPermission("djs:breed:event:die:list")
    @GetMapping("/list")
    public TableDataInfo<PigDeathVo> list(DieQuery query, PageQuery pageQuery) {
        return dieService.queryPage(query, pageQuery);
    }
}
