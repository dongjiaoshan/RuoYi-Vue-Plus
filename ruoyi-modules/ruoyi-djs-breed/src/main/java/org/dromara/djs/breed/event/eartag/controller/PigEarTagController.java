package org.dromara.djs.breed.event.eartag.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBatchEarTagBo;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBirthWeightBo;
import org.dromara.djs.breed.event.eartag.domain.query.PigletEarTagQuery;
import org.dromara.djs.breed.event.eartag.domain.vo.EarNoPreviewVo;
import org.dromara.djs.breed.event.eartag.domain.vo.FarrowEarTagStatVo;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletnoVo;
import org.dromara.djs.breed.event.eartag.service.IPigEarTagService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仔猪批量耳标 Controller（BRD-EVENT-003）。
 *
 * <p>端点：</p>
 * <ul>
 *   <li>{@code GET  /djs/breed/event/eartag/farrow/{farrowId}} — 统计某次分娩耳标进度 + 本窝仔猪清单
 *       （mp 出生重订正页据此渲染「耳号 + 性别 + 当前出生重」逐行，不另开查询端点）</li>
 *   <li>{@code POST /djs/breed/event/eartag/batch}             — 批量贴耳标（V6 行242 后 mp 不再走，保留供补录）</li>
 *   <li>{@code POST /djs/breed/event/eartag/birth-weight}      — 按耳号订正出生重（V6 行243，可重复提交）</li>
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

    /** admin 只读列表 — 历史仔猪耳标分页查询（mp 不调用）。 */
    @SaCheckPermission("djs:breed:event:eartag:list")
    @GetMapping("/list")
    public TableDataInfo<PigletnoVo> list(PigletEarTagQuery query, PageQuery pageQuery) {
        return eartagService.queryPage(query, pageQuery);
    }

    /** 统计某次分娩耳标进度。 */
    @SaCheckPermission("djs:breed:event:eartag:query")
    @GetMapping("/farrow/{farrowId}")
    public R<FarrowEarTagStatVo> stat(@PathVariable Long farrowId) {
        return R.ok(eartagService.statByFarrow(farrowId));
    }

    /** 预览某次分娩下一批公/母连号耳号（K122，仅预览不占号）。 */
    @SaCheckPermission("djs:breed:event:eartag:query")
    @GetMapping("/preview")
    public R<EarNoPreviewVo> preview(@RequestParam Long farrowId,
                                     @RequestParam(defaultValue = "0") int maleCount,
                                     @RequestParam(defaultValue = "0") int femaleCount) {
        return R.ok(eartagService.previewEarNos(farrowId, maleCount, femaleCount));
    }

    /** 批量贴耳标（一次 transaction）。 */
    @SaCheckPermission("djs:breed:event:eartag")
    @Log(title = "仔猪批量贴耳标", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/batch")
    public R<List<PigletEarTagVo>> batchTag(@Validated @RequestBody PigletBatchEarTagBo bo) {
        return R.ok(eartagService.batchTag(bo));
    }

    /**
     * 按耳号订正本窝仔猪出生重（V6 行243）。
     *
     * <p>同改 {@code t_farm_pig_info.birth_weight} + {@code t_farm_pig_pigletno.birth_weight}，
     * 收尾回写窝级 total_weight / avg_weight。幂等，同一窝可反复提交，故<b>不加</b> {@code @RepeatSubmit}。
     * 权限复用写侧串 {@code djs:breed:event:eartag}（mp 原打标提交同串，无需新菜单）。</p>
     */
    @SaCheckPermission("djs:breed:event:eartag")
    @Log(title = "仔猪出生重订正", businessType = BusinessType.UPDATE)
    @PostMapping("/birth-weight")
    public R<List<PigletEarTagVo>> adjustBirthWeight(@Validated @RequestBody PigletBirthWeightBo bo) {
        return R.ok(eartagService.adjustBirthWeights(bo));
    }
}
