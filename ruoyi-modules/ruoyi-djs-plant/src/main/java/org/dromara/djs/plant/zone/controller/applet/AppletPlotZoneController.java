package org.dromara.djs.plant.zone.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.plant.zone.domain.PlotZone;
import org.dromara.djs.plant.zone.domain.vo.ZonePickerVo;
import org.dromara.djs.plant.zone.mapper.PlotZoneMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
 *       返回启用片区，按 keyword LIKE zoneName/zoneCode 过滤，按 zoneCode 升序最多 200 条。</li>
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
        return R.ok(vos);
    }

}
