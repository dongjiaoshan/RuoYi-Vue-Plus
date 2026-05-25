package org.dromara.djs.breed.event.breeding.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.breeding.domain.bo.BreedingBo;
import org.dromara.djs.breed.event.breeding.domain.query.BreedingQuery;
import org.dromara.djs.breed.event.breeding.domain.vo.PigBreedingVo;
import org.dromara.djs.breed.event.breeding.service.IBreedingService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配种事件 Controller（BRD-EVENT-002 BREED）。
 *
 * <ul>
 *   <li>{@code POST /djs/breed/event/breeding}      mp 端配种录入</li>
 *   <li>{@code GET  /djs/breed/event/breeding/list} admin 端只读列表</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event/breeding")
public class BreedingController extends BaseController {

    private final IBreedingService breedingService;

    @SaCheckPermission("djs:breed:event:breeding")
    @Log(title = "配种录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<PigBreedingVo> record(@Validated @RequestBody BreedingBo bo) {
        return R.ok(breedingService.recordBreeding(bo));
    }

    @SaCheckPermission("djs:breed:event:breeding:list")
    @GetMapping("/list")
    public TableDataInfo<PigBreedingVo> list(BreedingQuery query, PageQuery pageQuery) {
        return breedingService.queryPage(query, pageQuery);
    }
}
