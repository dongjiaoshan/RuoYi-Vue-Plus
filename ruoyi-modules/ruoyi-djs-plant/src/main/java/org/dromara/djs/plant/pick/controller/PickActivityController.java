package org.dromara.djs.plant.pick.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.pick.domain.bo.PickActivityBo;
import org.dromara.djs.plant.pick.domain.query.PickActivityQuery;
import org.dromara.djs.plant.pick.domain.vo.PickActivityVo;
import org.dromara.djs.plant.pick.service.IPickActivityService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 采摘活动 Controller（admin，PLT-PLAN-002 游客采摘）。
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/pick/activity")
public class PickActivityController extends BaseController {

    private final IPickActivityService activityService;

    @SaCheckPermission("djs:plant:pick:activity:list")
    @GetMapping("/list")
    public TableDataInfo<PickActivityVo> list(PickActivityQuery query, PageQuery pageQuery) {
        return activityService.queryPageList(query, pageQuery);
    }

    @SaCheckPermission("djs:plant:pick:activity:list")
    @Log(title = "种植-采摘活动", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PickActivityQuery query, HttpServletResponse response) {
        List<PickActivityVo> list = activityService.queryList(query);
        ExcelUtil.exportExcel(list, "采摘活动", PickActivityVo.class, response);
    }

    @SaCheckPermission("djs:plant:pick:activity:list")
    @GetMapping("/getInfo/{id}")
    public R<PickActivityVo> getInfo(@PathVariable Long id) {
        return R.ok(activityService.getInfo(id));
    }

    @SaCheckPermission("djs:plant:pick:activity:add")
    @Log(title = "种植-采摘活动", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Long> add(@Validated @RequestBody PickActivityBo bo) {
        return R.ok(activityService.createByBo(bo));
    }

    @SaCheckPermission("djs:plant:pick:activity:edit")
    @Log(title = "种植-采摘活动", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody PickActivityBo bo) {
        activityService.updateByBo(bo);
        return R.ok();
    }

    @SaCheckPermission("djs:plant:pick:activity:remove")
    @Log(title = "种植-采摘活动", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        activityService.deleteByIds(Arrays.asList(ids));
        return R.ok();
    }

    @SaCheckPermission("djs:plant:pick:activity:edit")
    @Log(title = "种植-采摘活动-汇总", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/summary")
    public R<PickActivityVo> summary(@PathVariable Long id) {
        return R.ok(activityService.summary(id));
    }
}
