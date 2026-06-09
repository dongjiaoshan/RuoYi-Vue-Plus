package org.dromara.djs.plant.plan.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.plant.plan.domain.bo.PlantStartBo;
import org.dromara.djs.plant.plan.domain.vo.PlantMonthTaskVo;
import org.dromara.djs.plant.plan.domain.vo.SeedSummaryVo;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plan.service.IPlantPlanService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * mp 端「播种任务」Controller（FIX-PLANT-SEED-001 + FIX-PLT-MP-SEED-001）。
 *
 * <p>专给 mp 播种首页 / 作物种植详情页用。{@code @SaCheckLogin}（worker 角色可用，不加 @SaCheckPermission）。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET  /djs/applet/plant/plan/monthTasks?month=}  当月播种任务卡列表（含 beginActualdate）</li>
 *   <li>{@code GET  /djs/applet/plant/plan/seedSummary}        播种首页 3 KPI（种植口径）</li>
 *   <li>{@code POST /djs/applet/plant/plan/startPlant}         开始种植开工（批量标记计划地块实际开工）</li>
 * </ul>
 *
 * @author djs
 * @since FIX-PLANT-SEED-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/plant/plan")
public class AppletPlantTaskController {

    private final PlantDetailsMapper plantDetailsMapper;
    private final IPlantPlanService plantPlanService;

    /**
     * 当月播种任务列表（按片区分组前的扁平行，mp 端本地按 zoneName 分组）。
     *
     * @param month 月份 1-12，不传默认当前系统月
     */
    @SaCheckLogin
    @GetMapping("/monthTasks")
    public R<List<PlantMonthTaskVo>> monthTasks(@RequestParam(required = false) Integer month) {
        int m = (month == null || month < 1 || month > 12) ? LocalDate.now().getMonthValue() : month;
        return R.ok(plantDetailsMapper.selectMonthTasks(m));
    }

    /**
     * 播种首页 3 KPI（FIX-PLT-MP-SEED-001 #6.5，种植口径）：当月完成率 / 当日种植品种数 / 当日种植地块数。
     *
     * @param month 月份 1-12，不传默认当前系统月（用于当月完成率）
     */
    @SaCheckLogin
    @GetMapping("/seedSummary")
    public R<SeedSummaryVo> seedSummary(@RequestParam(required = false) Integer month) {
        LocalDate today = LocalDate.now();
        int m = (month == null || month < 1 || month > 12) ? today.getMonthValue() : month;
        return R.ok(plantDetailsMapper.selectSeedSummary(m, today));
    }

    /**
     * 开始种植开工（FIX-PLT-MP-SEED-001 #5）：批量标记所选计划地块实际开工。
     *
     * @return 实际开工的明细行数（已开工的被跳过）
     */
    @SaCheckLogin
    @PostMapping("/startPlant")
    public R<Integer> startPlant(@Valid @RequestBody PlantStartBo bo) {
        return R.ok(plantPlanService.startPlant(bo));
    }
}
