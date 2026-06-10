package org.dromara.djs.plant.plot.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.domain.vo.IdlePlotVo;
import org.dromara.djs.plant.plot.domain.vo.IdleZoneCountVo;
import org.dromara.djs.plant.plot.domain.vo.PlotPickerVo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 小程序地块 picker Controller（MP-PICKERS-001）。
 *
 * <p>专给 mp {@code PlotPicker} 用，复用 {@link PlotInfoMapper#selectList} 直接查 entity 转轻量 VO，
 * 不动 admin {@code IPlotInfoService}。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/plant/plot/listAll?keyword=&zoneId=}
 *       返回全部地块，按 keyword LIKE plotName/plotCode 过滤、zoneId 非空时按片区过滤，
 *       按 plotCode 升序最多 200 条。</li>
 *   <li>{@code GET /djs/applet/plant/plot/idlePlots?keyword=&zoneId=}（FIX-PLT-MP-TILL-001 P6/P8）
 *       翻耕专用——只列 {@code plot_status=1} 空地，带「空地日期」（最近退茬日·派生）。</li>
 *   <li>{@code GET /djs/applet/plant/plot/idleZoneCounts}（FIX-PLT-MP-TILL-001 P6）
 *       翻耕片区计数胶囊：每个启用片区的空地总数（LEFT JOIN 保 0 也出胶囊）。</li>
 * </ul>
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
@RequestMapping("/djs/applet/plant/plot")
public class AppletPlotPickerController {

    private final PlotInfoMapper plotInfoMapper;

    /**
     * 地块 picker 列表。
     *
     * @param keyword 关键字（同时 LIKE plotName / plotCode），可空
     * @param zoneId  片区 ID（非空时按片区过滤地块），可空
     */
    @SaCheckLogin
    @GetMapping("/listAll")
    public R<List<PlotPickerVo>> listAll(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long zoneId) {
        LambdaQueryWrapper<PlotInfo> wrapper = new LambdaQueryWrapper<PlotInfo>()
            .eq(zoneId != null, PlotInfo::getZoneId, zoneId)
            .and(StringUtils.isNotBlank(keyword), w -> w
                .like(PlotInfo::getPlotName, keyword)
                .or()
                .like(PlotInfo::getPlotCode, keyword))
            .orderByAsc(PlotInfo::getPlotCode)
            .last("LIMIT 200");
        List<PlotInfo> rows = plotInfoMapper.selectList(wrapper);
        List<PlotPickerVo> vos = rows.stream().map(p -> {
            PlotPickerVo vo = new PlotPickerVo();
            vo.setId(p.getId());
            vo.setPlotCode(p.getPlotCode());
            vo.setPlotName(p.getPlotName());
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }

    /**
     * 翻耕目标卡列表——只列 {@code plot_status=1} 空地，带空地日期（FIX-PLT-MP-TILL-001 P6/P8）。
     *
     * <p>空地日期 = 该地块最近一条 rotation farm_date（派生，{@link PlotInfoMapper#selectLatestRotationDate}）；
     * 无退茬记录留空。</p>
     *
     * @param keyword 关键字（同时 LIKE plotName / plotCode），可空
     * @param zoneId  片区 ID（非空时按片区过滤），可空
     */
    @SaCheckLogin
    @GetMapping("/idlePlots")
    public R<List<IdlePlotVo>> idlePlots(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long zoneId) {
        LambdaQueryWrapper<PlotInfo> wrapper = new LambdaQueryWrapper<PlotInfo>()
            .eq(PlotInfo::getPlotStatus, 1)
            .eq(zoneId != null, PlotInfo::getZoneId, zoneId)
            .and(StringUtils.isNotBlank(keyword), w -> w
                .like(PlotInfo::getPlotName, keyword)
                .or()
                .like(PlotInfo::getPlotCode, keyword))
            .orderByAsc(PlotInfo::getPlotCode)
            .last("LIMIT 200");
        List<PlotInfo> rows = plotInfoMapper.selectList(wrapper);
        List<IdlePlotVo> vos = rows.stream().map(p -> {
            IdlePlotVo vo = new IdlePlotVo();
            vo.setId(p.getId());
            vo.setPlotCode(p.getPlotCode());
            vo.setPlotName(p.getPlotName());
            vo.setPlotStatusLabel("空闲");
            // 空地日期 = 最近退茬日（派生）；无退茬记录留空（不动 DDL）
            LocalDate idleDate = plotInfoMapper.selectLatestRotationDate(p.getId());
            vo.setIdleDate(idleDate);
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }

    /**
     * 翻耕片区计数胶囊（FIX-PLT-MP-TILL-001 P6）。
     *
     * <p>每个启用片区的空地（plot_status=1）总数，LEFT JOIN 保证 0 空地的启用片区也出 {@code X区(0)} 胶囊。</p>
     */
    @SaCheckLogin
    @GetMapping("/idleZoneCounts")
    public R<List<IdleZoneCountVo>> idleZoneCounts() {
        List<Map<String, Object>> rows = plotInfoMapper.selectIdleZoneCounts();
        List<IdleZoneCountVo> vos = rows.stream().map(m -> {
            IdleZoneCountVo vo = new IdleZoneCountVo();
            Object zoneId = m.get("zoneId");
            vo.setZoneId(zoneId == null ? null : ((Number) zoneId).longValue());
            vo.setZoneName((String) m.get("zoneName"));
            Object idleCount = m.get("idleCount");
            vo.setIdleCount(idleCount == null ? 0 : ((Number) idleCount).intValue());
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }

}
