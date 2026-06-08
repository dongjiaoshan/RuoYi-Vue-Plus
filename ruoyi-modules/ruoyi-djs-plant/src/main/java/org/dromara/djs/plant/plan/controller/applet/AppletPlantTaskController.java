package org.dromara.djs.plant.plan.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.plant.plan.domain.vo.PlantMonthTaskVo;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * mp 端「当月播种任务」Controller（FIX-PLANT-SEED-001，覆盖 #62a）。
 *
 * <p>专给 mp 播种首页「种植任务」tab 用，复用 {@link PlantDetailsMapper#selectMonthTasks}
 * 直接查当月种植明细 JOIN 地块 / 片区 / 作物 / 计划，按片区分组喂原型 59 任务卡，
 * 不动 admin {@code IPlantPlanService}。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/plant/plan/monthTasks?month=}
 *       返回指定月份（默认当前月）的播种任务卡列表，含完成率所需 expected/actual 两值。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckLogin}（worker 角色可用，不加 @SaCheckPermission）。</p>
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

}
