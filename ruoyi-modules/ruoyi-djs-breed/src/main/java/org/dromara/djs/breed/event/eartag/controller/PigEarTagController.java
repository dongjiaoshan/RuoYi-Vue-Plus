package org.dromara.djs.breed.event.eartag.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBatchEarTagBo;
import org.dromara.djs.breed.event.eartag.domain.vo.FarrowEarTagStatVo;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo;
import org.dromara.djs.breed.event.eartag.service.IPigEarTagService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仔猪批量耳标 Controller（BRD-EVENT-003）。
 *
 * <p>2 端点：</p>
 * <ul>
 *   <li>{@code GET  /djs/breed/event/eartag/farrow/{farrowId}} — 统计某次分娩耳标进度</li>
 *   <li>{@code POST /djs/breed/event/eartag/batch}             — 批量贴耳标</li>
 * </ul>
 *
 * <p>权限：</p>
 * <ul>
 *   <li>{@code djs:breed:event:eartag:query} 查询 stat</li>
 *   <li>{@code djs:breed:event:eartag}       批量贴（写）</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event/eartag")
public class PigEarTagController extends BaseController {

    private final IPigEarTagService eartagService;

    /** 统计某次分娩耳标进度。 */
    @SaCheckPermission("djs:breed:event:eartag:query")
    @GetMapping("/farrow/{farrowId}")
    public R<FarrowEarTagStatVo> stat(@PathVariable Long farrowId) {
        return R.ok(eartagService.statByFarrow(farrowId));
    }

    /** 批量贴耳标（一次 transaction）。 */
    @SaCheckPermission("djs:breed:event:eartag")
    @Log(title = "仔猪批量贴耳标", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/batch")
    public R<List<PigletEarTagVo>> batchTag(@Validated @RequestBody PigletBatchEarTagBo bo) {
        return R.ok(eartagService.batchTag(bo));
    }
}
