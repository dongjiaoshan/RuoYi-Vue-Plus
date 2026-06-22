package org.dromara.djs.breed.event.growth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.growth.domain.bo.GrowthBo;
import org.dromara.djs.breed.event.growth.domain.query.GrowthQuery;
import org.dromara.djs.breed.event.growth.domain.vo.PigGrowthVo;
import org.dromara.djs.breed.event.growth.service.IPigGrowthService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生长记录 Controller（BRD-EVENT-005 GROWTH）。
 *
 * <ul>
 *   <li>{@code GET    /djs/breed/event/growth/list}     列表（分页 + 按 pigId / 耳号 / 日期过滤）</li>
 *   <li>{@code POST   /djs/breed/event/growth}          新增（mp + admin 共用，BO 字段决定来源）</li>
 *   <li>{@code DELETE /djs/breed/event/growth/{id}}     删除（hard delete；单 id 或逗号分隔批量，逐条物理删，{@code R<Void>}）</li>
 * </ul>
 *
 * <p><strong>不触发状态机</strong>。仅 INSERT t_farm_pig_growth。</p>
 *
 * @author djs
 * @since BRD-EVENT-005
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event/growth")
public class PigGrowthController extends BaseController {

    private final IPigGrowthService growthService;

    @SaCheckPermission("djs:breed:event:growth:list")
    @GetMapping("/list")
    public TableDataInfo<PigGrowthVo> list(GrowthQuery query, PageQuery pageQuery) {
        return growthService.queryPage(query, pageQuery);
    }

    @SaCheckPermission("djs:breed:event:growth:add")
    @Log(title = "生长记录录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<PigGrowthVo> add(@Validated @RequestBody GrowthBo bo) {
        return R.ok(growthService.addGrowthRecord(bo));
    }

    /**
     * 删除生长记录（fe-growth row54）。
     *
     * <p>hard delete：单 id（{@code /{id}}）或逗号分隔多 id（admin 批量），逐条物理删
     * {@code t_farm_pig_growth}（tenant_id 由 MP 拦截器自动注入）。返回 {@code R<Void>}。
     * mp 记录列表卡 / admin 列表行删除共用本端点。</p>
     */
    @SaCheckPermission("djs:breed:event:growth:remove")
    @Log(title = "生长记录删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        for (Long id : ids) {
            growthService.deleteById(id);
        }
        return R.ok();
    }
}
