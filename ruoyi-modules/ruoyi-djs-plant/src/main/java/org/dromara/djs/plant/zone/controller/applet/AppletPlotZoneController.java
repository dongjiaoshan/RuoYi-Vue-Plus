package org.dromara.djs.plant.zone.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.zone.domain.PlotZone;
import org.dromara.djs.plant.zone.domain.vo.ZonePickerVo;
import org.dromara.djs.plant.zone.mapper.PlotZoneMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 小程序片区 picker Controller（D12X-MP-PLANT-WORK-IA-001）。
 *
 * <p>专给 mp {@code ZonePicker} 用，复用 {@link PlotZoneMapper#selectList} 直接查 entity 转轻量 VO，
 * 不动 admin {@code IPlotZoneService}。农事录入按片区缩小地块范围（再传 zoneId 到 plot picker）。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/plant/zone/listAll?keyword=}
 *       返回启用片区，按 keyword LIKE zoneName/zoneCode 过滤，按 zoneCode 升序最多 200 条，
 *       并回填各片区当月采摘任务数 {@code pickTaskCount}（采收 chip 角标用）。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckLogin}（worker 角色可用，不加 @SaCheckPermission，与 {@code AppletPlotPickerController} 一致）。</p>
 *
 * @author djs
 * @since D12X-MP-PLANT-WORK-IA-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/plant/zone")
public class AppletPlotZoneController {

    private final PlotZoneMapper plotZoneMapper;
    private final PlantDetailsMapper plantDetailsMapper;
    private final PlotInfoMapper plotInfoMapper;

    /**
     * is_pick=2 表示非游客采摘（普通采收）；与 {@code AppletPickServiceImpl} 采收列表同口径。
     */
    private static final int IS_PICK_NORMAL = 2;

    /**
     * 片区 picker 列表。
     *
     * @param keyword 关键字（同时 LIKE zoneName / zoneCode），可空
     */
    @SaCheckLogin
    @GetMapping("/listAll")
    public R<List<ZonePickerVo>> listAll(@RequestParam(required = false) String keyword) {
        // zone_status 走字典 sys_normal_disable：0=正常（启用）/ 1=停用（与 admin 列表 toggle 同口径）。
        // mp picker 只列启用片区 → eq 0。
        LambdaQueryWrapper<PlotZone> wrapper = new LambdaQueryWrapper<PlotZone>()
            .eq(PlotZone::getZoneStatus, 0)
            .and(StringUtils.isNotBlank(keyword), w -> w
                .like(PlotZone::getZoneName, keyword)
                .or()
                .like(PlotZone::getZoneCode, keyword))
            .orderByAsc(PlotZone::getZoneCode)
            .last("LIMIT 200");
        List<PlotZone> rows = plotZoneMapper.selectList(wrapper);
        List<ZonePickerVo> vos = rows.stream().map(z -> {
            ZonePickerVo vo = new ZonePickerVo();
            vo.setId(z.getId());
            vo.setZoneCode(z.getZoneCode());
            vo.setZoneName(z.getZoneName());
            return vo;
        }).collect(Collectors.toList());

        // 各片区当月采摘任务数回填（口径同 AppletPickServiceImpl#listCropTasks，应用层聚合，V1 数据量小）。
        fillPickTaskCount(vos);
        return R.ok(vos);
    }

    /**
     * 回填各片区当月采摘任务数。
     *
     * <p>口径：is_pick=2（非游客）且采摘窗口 [earliest, last] 与当月 [firstDay, lastDay] 相交的明细，
     * 取明细所属地块的 zone_id 计数。无任务的片区保持 0。</p>
     */
    private void fillPickTaskCount(List<ZonePickerVo> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay = today.with(TemporalAdjusters.lastDayOfMonth());

        // 当月采摘明细：窗口相交（earliest ≤ lastDay AND last ≥ firstDay）。
        List<PlantDetails> monthly = plantDetailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getIsPick, IS_PICK_NORMAL)
                .le(PlantDetails::getEarliestHarvestdate, lastDay)
                .ge(PlantDetails::getLastHarvestdate, firstDay)
                .select(PlantDetails::getPlotId));
        if (monthly.isEmpty()) {
            return;
        }

        // 地块 → 片区映射。
        Set<Long> plotIds = monthly.stream().map(PlantDetails::getPlotId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (plotIds.isEmpty()) {
            return;
        }
        Map<Long, Long> plotToZone = plotInfoMapper.selectList(
                new LambdaQueryWrapper<PlotInfo>()
                    .in(PlotInfo::getId, plotIds)
                    .select(PlotInfo::getId, PlotInfo::getZoneId))
            .stream()
            .filter(p -> p.getZoneId() != null)
            .collect(Collectors.toMap(PlotInfo::getId, PlotInfo::getZoneId, (a, b) -> a));

        // 按 zoneId 统计当月明细数。
        Map<Long, Long> zoneCount = monthly.stream()
            .map(PlantDetails::getPlotId)
            .map(plotToZone::get)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(z -> z, Collectors.counting()));

        for (ZonePickerVo vo : vos) {
            Long cnt = zoneCount.getOrDefault(vo.getId(), 0L);
            vo.setPickTaskCount(Math.toIntExact(cnt));
        }
    }

}
