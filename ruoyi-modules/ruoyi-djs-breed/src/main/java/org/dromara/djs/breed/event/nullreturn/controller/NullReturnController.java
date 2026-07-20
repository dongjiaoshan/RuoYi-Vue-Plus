package org.dromara.djs.breed.event.nullreturn.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.nullreturn.domain.bo.NullReturnBo;
import org.dromara.djs.breed.event.nullreturn.domain.query.NullReturnQuery;
import org.dromara.djs.breed.event.nullreturn.domain.vo.PigAbnormalVo;
import org.dromara.djs.breed.event.nullreturn.service.INullReturnService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 返空事件 Controller（BRD-EVENT-002 NULL_RETURN）。
 *
 * <p>权限共用 {@code djs:breed:event:heat*}（菜单 7127 查情/返空/断奶合并）。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event/null-return")
public class NullReturnController extends BaseController {

    private final INullReturnService nullReturnService;

    @SaCheckPermission("djs:breed:event:heat")
    @Log(title = "返空录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<PigAbnormalVo> record(@Validated @RequestBody NullReturnBo bo) {
        return R.ok(nullReturnService.recordNullReturn(bo));
    }

    @SaCheckPermission("djs:breed:event:heat:list")
    @GetMapping("/list")
    public TableDataInfo<PigAbnormalVo> list(NullReturnQuery query, PageQuery pageQuery) {
        return nullReturnService.queryPage(query, pageQuery);
    }
}
