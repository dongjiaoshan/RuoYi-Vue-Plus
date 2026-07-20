package org.dromara.djs.breed.event.farrow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.farrow.domain.bo.FarrowBo;
import org.dromara.djs.breed.event.farrow.domain.query.FarrowQuery;
import org.dromara.djs.breed.event.farrow.domain.vo.PigFarrowVo;
import org.dromara.djs.breed.event.farrow.service.IFarrowService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分娩事件 Controller（BRD-EVENT-002 FARROW）。
 *
 * <ul>
 *   <li>{@code POST /djs/breed/event/farrow}         mp 端分娩录入</li>
 *   <li>{@code GET  /djs/breed/event/farrow/list}    admin 端只读列表</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event/farrow")
public class FarrowController extends BaseController {

    private final IFarrowService farrowService;

    /** mp 端分娩录入。 */
    @SaCheckPermission("djs:breed:event:farrow")
    @Log(title = "分娩录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<PigFarrowVo> record(@Validated @RequestBody FarrowBo bo) {
        return R.ok(farrowService.recordFarrow(bo));
    }

    /** admin 列表分页。 */
    @SaCheckPermission("djs:breed:event:farrow:list")
    @GetMapping("/list")
    public TableDataInfo<PigFarrowVo> list(FarrowQuery query, PageQuery pageQuery) {
        return farrowService.queryPage(query, pageQuery);
    }
}
