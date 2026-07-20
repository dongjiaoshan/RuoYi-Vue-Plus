package org.dromara.djs.plant.plan.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.domain.PlantPlan;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanPickerVo;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plan.mapper.PlantPlanMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 小程序种植计划 picker Controller（MP-PICKERS-001）。
 *
 * <p>专给 mp {@code PlantPlanPicker} 用，复用 {@link PlantPlanMapper#selectList} 直接查 entity 转轻量 VO，
 * 不动 admin {@code IPlantPlanService}。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/plant/plan/listActive?plotId=}
 *       返回"进行中"（plant_status IN pending/ongoing）的计划；可选 plotId 过滤
 *       （含该地块的计划，经明细表 {@code t_plant_plant_details} 反查）。</li>
 * </ul>
 *
 * <h2>cropName 来源</h2>
 * <p>plan 主表只存 crop_id；本 controller 批量 JOIN {@code t_plant_crop_info.crop_name} 填充 cropName。</p>
 *
 * <h2>plotId 过滤实现</h2>
 * <p>plan 主表无 plot_id（地块挂在明细表，1 计划 N 地块）。传 plotId 时先查
 * {@code t_plant_plant_details} 拿含该地块的 plan_id 集合，再过滤主表。</p>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckLogin}（worker 角色可用，不加 @SaCheckPermission）。</p>
 *
 * @author djs
 * @since MP-PICKERS-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/plant/plan")
public class AppletPlantPlanPickerController {

    private final PlantPlanMapper plantPlanMapper;

    private final PlantDetailsMapper plantDetailsMapper;

    private final CropInfoMapper cropInfoMapper;

    /**
     * 进行中种植计划 picker 列表。
     *
     * @param plotId 地块 FK，可空；非空时仅返回含该地块的计划
     */
    @SaCheckLogin
    @GetMapping("/listActive")
    public R<List<PlantPlanPickerVo>> listActive(@RequestParam(required = false) Long plotId) {
        LambdaQueryWrapper<PlantPlan> wrapper = new LambdaQueryWrapper<PlantPlan>()
            .in(PlantPlan::getPlantStatus, List.of("pending", "ongoing"))
            .orderByDesc(PlantPlan::getId)
            .last("LIMIT 200");
        // plotId 过滤：先查明细表拿含该地块的 plan_id，再约束主表
        if (plotId != null) {
            List<Long> planIds = plantDetailsMapper.selectList(
                    new LambdaQueryWrapper<PlantDetails>()
                        .select(PlantDetails::getPlantId)
                        .eq(PlantDetails::getPlotId, plotId))
                .stream()
                .map(PlantDetails::getPlantId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
            if (planIds.isEmpty()) {
                return R.ok(Collections.emptyList());
            }
            wrapper.in(PlantPlan::getId, planIds);
        }
        List<PlantPlan> rows = plantPlanMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        // 批量取 crop_name，避免 N+1
        List<Long> cropIds = rows.stream()
            .map(PlantPlan::getCropId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, String> cropNameMap = cropIds.isEmpty()
            ? Collections.emptyMap()
            : cropInfoMapper.selectList(new LambdaQueryWrapper<CropInfo>().in(CropInfo::getId, cropIds))
                .stream()
                .collect(Collectors.toMap(CropInfo::getId, CropInfo::getCropName, (a, b) -> a));
        List<PlantPlanPickerVo> vos = rows.stream().map(p -> {
            PlantPlanPickerVo vo = new PlantPlanPickerVo();
            vo.setId(p.getId());
            vo.setPlanNo(p.getPlanNo());
            vo.setCropName(p.getCropId() == null ? null : cropNameMap.get(p.getCropId()));
            vo.setPlantStatus(p.getPlantStatus());
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }

}
