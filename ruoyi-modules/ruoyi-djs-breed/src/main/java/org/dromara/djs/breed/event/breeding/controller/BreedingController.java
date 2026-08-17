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
import org.dromara.djs.breed.event.breeding.domain.bo.BreedingBatchBo;
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

import java.util.List;

/**
 * 配种事件 Controller（BRD-EVENT-002 BREED）。
 *
 * <ul>
 *   <li>{@code POST /djs/breed/event/breeding}       mp 端配种录入</li>
 *   <li>{@code POST /djs/breed/event/breeding/batch} mp 端批量配种</li>
 *   <li>{@code GET  /djs/breed/event/breeding/list}  admin 端只读列表</li>
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

    /**
     * 批量配种（mp「批量配种」）。N 头母猪 + 同一套配种信息（公猪 / 日期 / 配种人员）。
     * 逐头复用单只 recordBreeding，配种记录落 N 条独立单头记录；整批同一事务，任一头失败全回滚。
     */
    @SaCheckPermission("djs:breed:event:breeding")
    @Log(title = "批量配种录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/batch")
    public R<List<PigBreedingVo>> recordBatch(@Validated @RequestBody BreedingBatchBo bo) {
        return R.ok(breedingService.recordBreedingBatch(bo));
    }

    @SaCheckPermission("djs:breed:event:breeding:list")
    @GetMapping("/list")
    public TableDataInfo<PigBreedingVo> list(BreedingQuery query, PageQuery pageQuery) {
        return breedingService.queryPage(query, pageQuery);
    }
}
