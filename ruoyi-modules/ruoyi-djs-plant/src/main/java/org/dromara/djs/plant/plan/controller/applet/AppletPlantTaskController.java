package org.dromara.djs.plant.plan.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.plan.domain.bo.PlantFinishBo;
import org.dromara.djs.plant.plan.domain.bo.PlantStartBo;
import org.dromara.djs.plant.plan.domain.vo.PlantCropTaskVo;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * mp 端「播种任务」Controller（FIX-PLANT-SEED-001 + FIX-PLT-MP-SEED-001）。
 *
 * <p>专给 mp 播种首页 / 作物种植详情页用。{@code @SaCheckLogin}（worker 角色可用，不加 @SaCheckPermission）。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET  /djs/applet/plant/plan/cropTasks?month=}   当月播种作物聚合卡（喂首页卡 + 「全部(N)」=作物数 + 完成率，D2 拆接口聚合侧）</li>
 *   <li>{@code GET  /djs/applet/plant/plan/monthTasks?month=}  当月播种任务明细行（含明细 id + beginActualdate，喂详情页逐块开工，D2 拆接口明细侧）</li>
 *   <li>{@code GET  /djs/applet/plant/plan/seedSummary}        播种首页 3 KPI（种植口径，完成率 = 当月已开工明细/当月明细总数）</li>
 *   <li>{@code POST /djs/applet/plant/plan/startPlant}         开始种植开工（批量标记计划地块实际开工）</li>
 *   <li>{@code POST /djs/applet/plant/plan/finishPlant}        种植完成收工（批量标记计划地块种植完成）</li>
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
    private final ImageUrlResolver imageUrlResolver;

    /** 作物 L2 默认图统一走果蔬（IMG-LIB-001）。 */
    private static final String CROP_BELONG_TYPE = "vegetable";

    /**
     * 当月播种作物聚合卡列表（D2 拆接口聚合侧）：喂首页作物卡 + 「全部(N)」计数（= 作物数 = 行数）
     * + 完成率（D1 开工进度 = startedPlotCount/plotCount）。片区 chips 仍由 mp 端按明细维度自算。
     *
     * @param month 月份 1-12，不传默认当前系统月
     */
    @SaCheckLogin
    @GetMapping("/cropTasks")
    public R<List<PlantCropTaskVo>> cropTasks(@RequestParam(required = false) Integer month) {
        int m = (month == null || month < 1 || month > 12) ? LocalDate.now().getMonthValue() : month;
        List<PlantCropTaskVo> rows = plantDetailsMapper.selectCropTasks(m);
        // cropImg = 有效 ossId（优先用户上传 crop_image_url 首图，其次自动匹配 image_oss_id）。
        // 仅把真实 ossId 解析成 URL（batchUrl 不走 L2/L3 默认图兜底）；无图保持 null，前端落「暂无图」占位。
        if (rows != null && !rows.isEmpty()) {
            Set<String> ossIds = rows.stream()
                .map(PlantCropTaskVo::getCropImg)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toSet());
            Map<String, String> urlMap = imageUrlResolver.batchUrl(ossIds);
            for (PlantCropTaskVo v : rows) {
                v.setCropImg(v.getCropImg() == null ? null : urlMap.get(v.getCropImg()));
            }
        }
        return R.ok(rows);
    }

    /**
     * 当月播种任务明细行列表（D2 拆接口明细侧）：含明细 id + beginActualdate，喂作物种植详情页
     * 逐块开工。mp 端详情页按 cropId 本地过滤拿该作物的计划地块明细。
     *
     * @param month 月份 1-12，不传默认当前系统月
     */
    @SaCheckLogin
    @GetMapping("/monthTasks")
    public R<List<PlantMonthTaskVo>> monthTasks(@RequestParam(required = false) Integer month) {
        int m = (month == null || month < 1 || month > 12) ? LocalDate.now().getMonthValue() : month;
        List<PlantMonthTaskVo> rows = plantDetailsMapper.selectMonthTasks(m);
        // IMG-LIB-001：cropImg 走 4 层 resolver（L1 image_oss_id → L2 vegetable → L3 全局），批量禁 N+1
        if (rows != null && !rows.isEmpty()) {
            List<ImageUrlResolver.Item> items = rows.stream()
                .map(v -> new ImageUrlResolver.Item(v.getCropImg(), CROP_BELONG_TYPE))
                .toList();
            List<String> urls = imageUrlResolver.resolveList(items);
            if (urls.size() == rows.size()) {
                for (int i = 0; i < rows.size(); i++) {
                    rows.get(i).setCropImg(urls.get(i));
                }
            }
        }
        return R.ok(rows);
    }

    /**
     * 播种首页 3 KPI（FIX-PLT-MP-SEED-001 #6.5，种植口径）：当月完成率 / 当月种植品种数 / 当月完成种植地块数。
     *
     * <p>三项 KPI（完成率 / 品种数 / 完成地块数）统一按 {@code plant_month=month} 计（r17）：
     * 与首页作物卡列表「全部(N)」及完成率同源，KPI 与列表一致。</p>
     *
     * @param month 月份 1-12，不传默认当前系统月
     */
    @SaCheckLogin
    @GetMapping("/seedSummary")
    public R<SeedSummaryVo> seedSummary(@RequestParam(required = false) Integer month) {
        int m = (month == null || month < 1 || month > 12) ? LocalDate.now().getMonthValue() : month;
        return R.ok(plantDetailsMapper.selectSeedSummary(m));
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

    /**
     * 种植完成收工：批量标记所选计划地块种植完成。
     *
     * @return 实际完成的明细行数（非进行中 / 已完成的被跳过）
     */
    @SaCheckLogin
    @PostMapping("/finishPlant")
    public R<Integer> finishPlant(@Valid @RequestBody PlantFinishBo bo) {
        return R.ok(plantPlanService.finishPlant(bo));
    }
}
