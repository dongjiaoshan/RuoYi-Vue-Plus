package org.dromara.djs.plant.plan.controller;

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
import org.dromara.djs.plant.plan.domain.bo.PlantPlanCreateBo;
import org.dromara.djs.plant.plan.domain.bo.PlantPlanUpdateBo;
import org.dromara.djs.plant.plan.domain.query.PlantPlanQuery;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanDetailVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanGanttVo;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanVo;
import org.dromara.djs.plant.plan.domain.vo.PlotByZoneVo;
import org.dromara.djs.plant.plan.service.IPlantPlanService;
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
 * 种植计划 Controller（PLT-PLAN-001）。
 *
 * <p>端点：</p>
 * <ul>
 *   <li>GET  /list                列表分页</li>
 *   <li>GET  /getInfo/{id}        详情（主表 + 明细 list）</li>
 *   <li>POST /add                 向导 3 步合一提交（{@link PlantPlanCreateBo}）</li>
 *   <li>PUT  /edit                编辑（{@link PlantPlanUpdateBo}）</li>
 *   <li>DELETE /remove/{ids}      批量软删</li>
 *   <li>POST /export              导出</li>
 *   <li>GET  /{id}/gantt          甘特图数据（简化双甘特图）</li>
 *   <li>GET  /availablePlots      向导 step3 按片区分组的地块清单</li>
 * </ul>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/plan")
public class PlantPlanController extends BaseController {

    private final IPlantPlanService plantPlanService;

    @SaCheckPermission("djs:plant:plan:list")
    @GetMapping("/list")
    public TableDataInfo<PlantPlanVo> list(PlantPlanQuery query, PageQuery pageQuery) {
        return plantPlanService.queryPageList(query, pageQuery);
    }

    @SaCheckPermission("djs:plant:plan:export")
    @Log(title = "种植-种植计划", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PlantPlanQuery query, HttpServletResponse response) {
        List<PlantPlanVo> list = plantPlanService.queryList(query);
        ExcelUtil.exportExcel(list, "种植计划", PlantPlanVo.class, response);
    }

    @SaCheckPermission("djs:plant:plan:list")
    @GetMapping("/getInfo/{id}")
    public R<PlantPlanDetailVo> getInfo(@PathVariable Long id) {
        return R.ok(plantPlanService.queryDetailById(id));
    }

    @SaCheckPermission("djs:plant:plan:add")
    @Log(title = "种植-种植计划", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Long> add(@Validated @RequestBody PlantPlanCreateBo bo) {
        return R.ok(plantPlanService.createByBo(bo));
    }

    @SaCheckPermission("djs:plant:plan:edit")
    @Log(title = "种植-种植计划", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody PlantPlanUpdateBo bo) {
        plantPlanService.updateByBo(bo);
        return R.ok();
    }

    @SaCheckPermission("djs:plant:plan:remove")
    @Log(title = "种植-种植计划", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        plantPlanService.deleteWithValidByIds(Arrays.asList(ids));
        return R.ok();
    }

    @SaCheckPermission("djs:plant:plan:ganttView")
    @GetMapping("/{id}/gantt")
    public R<PlantPlanGanttVo> gantt(@PathVariable Long id) {
        return R.ok(plantPlanService.getGantt(id));
    }

    @SaCheckPermission("djs:plant:plan:wizard")
    @GetMapping("/availablePlots")
    public R<List<PlotByZoneVo>> availablePlots() {
        return R.ok(plantPlanService.listAvailablePlots());
    }
}
