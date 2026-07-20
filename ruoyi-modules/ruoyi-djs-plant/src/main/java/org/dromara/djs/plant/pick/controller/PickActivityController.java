package org.dromara.djs.plant.pick.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.pick.domain.query.PickActivityQuery;
import org.dromara.djs.plant.pick.domain.vo.PickActivityVo;
import org.dromara.djs.plant.pick.service.IPickActivityService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采摘活动只读聚合报表 Controller（admin）。
 *
 * <p>每日采摘统计：按「作物 + 活动日期」聚合 {@code t_plant_plant_details}，仅查询 + 导出。</p>
 *
 * @author djs
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
}
