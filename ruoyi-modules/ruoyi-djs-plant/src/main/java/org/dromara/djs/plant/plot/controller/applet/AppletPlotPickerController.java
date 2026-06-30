package org.dromara.djs.plant.plot.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.plant.farm.domain.FarmRecords;
import org.dromara.djs.plant.farm.mapper.FarmRecordsMapper;
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
import java.util.ArrayList;
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

    /** 灾害类型字典（地块卡多行灾害展示，服务端译 label）。 */
    private static final String DICT_DISASTER_TYPE = "djs_disaster_type";

    /** 地块卡最多展示的灾害条数（r18：最近 3 条，按 farm_date 倒序）。 */
    private static final int MAX_DISASTER_PER_PLOT = 3;

    private final PlotInfoMapper plotInfoMapper;
    private final FarmRecordsMapper farmRecordsMapper;
    private final DictService dictService;

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
            // 防御上限 1000（V1 单农场地块主数据量级；与无 LIMIT 的 idleZoneCounts 全量计数对齐）。
            // 200 太小：空地总数超 200 时，plot_code 升序尾部的地块（含用户新建的）被截断不显示。
            .last("LIMIT 1000");
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
     * <p>{@code farmType}（翻耕/整地/施肥）非空时，额外回填「30 天内上次同工种日期」
     * （{@link FarmRecordsMapper#selectLastFarmDateByPlotType}，row14）——卡片在「空闲时间」旁展示
     * 「上次&lt;农事&gt;：YYYY-MM-DD」，近 30 天无该工种记录则留空。</p>
     *
     * @param keyword  关键字（同时 LIKE plotName / plotCode），可空
     * @param zoneId   片区 ID（非空时按片区过滤），可空
     * @param farmType 空地农事类型（tillage_break/tillage_prepare/fertilize），可空——非空时回填上次同工种日期
     */
    @SaCheckLogin
    @GetMapping("/idlePlots")
    public R<List<IdlePlotVo>> idlePlots(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long zoneId,
                                         @RequestParam(required = false) String farmType) {
        LambdaQueryWrapper<PlotInfo> wrapper = new LambdaQueryWrapper<PlotInfo>()
            .eq(PlotInfo::getPlotStatus, 1)
            .eq(zoneId != null, PlotInfo::getZoneId, zoneId)
            .and(StringUtils.isNotBlank(keyword), w -> w
                .like(PlotInfo::getPlotName, keyword)
                .or()
                .like(PlotInfo::getPlotCode, keyword))
            .orderByAsc(PlotInfo::getPlotCode)
            // 防御上限 1000（V1 单农场地块主数据量级；与无 LIMIT 的 idleZoneCounts 全量计数对齐）。
            // 200 太小：空地总数超 200 时，plot_code 升序尾部的地块（含用户新建的）被截断不显示。
            .last("LIMIT 1000");
        List<PlotInfo> rows = plotInfoMapper.selectList(wrapper);
        List<Long> plotIds = rows.stream().map(PlotInfo::getId).collect(Collectors.toList());
        // 空地日期 = 最近退茬日（派生）：一次 GROUP BY 批量取，避免按地块逐条单查（远程 RDS 下 N+1 会拖到数十秒）
        Map<Long, LocalDate> idleDateByPlot = rows.isEmpty()
            ? Map.of()
            : plotInfoMapper.selectLatestRotationDates(plotIds)
                .stream()
                .filter(m -> m.get("plotId") != null && m.get("idleDate") != null)
                .collect(Collectors.toMap(
                    m -> Long.valueOf(m.get("plotId").toString()),
                    m -> LocalDate.parse(m.get("idleDate").toString().substring(0, 10))));
        // 上次同工种日期（row14，30 天内）：farmType 非空时一次 GROUP BY 批量取，禁 N+1
        Map<Long, LocalDate> lastFarmDateByPlot = (rows.isEmpty() || StringUtils.isBlank(farmType))
            ? Map.of()
            : farmRecordsMapper.selectLastFarmDateByPlotType(plotIds, farmType)
                .stream()
                .filter(m -> m.get("plotId") != null && m.get("lastDate") != null)
                .collect(Collectors.toMap(
                    m -> Long.valueOf(m.get("plotId").toString()),
                    m -> LocalDate.parse(m.get("lastDate").toString().substring(0, 10))));
        // r18：地块灾害记录（farm_type='disaster'）一次性批量拉，farm_date 倒序，按 plot 取最近 3 条（Java 端裁剪，禁 N+1）
        Map<Long, List<IdlePlotVo.DisasterRecord>> disasterByPlot = buildDisasterRecords(plotIds);
        List<IdlePlotVo> vos = rows.stream().map(p -> {
            IdlePlotVo vo = new IdlePlotVo();
            vo.setId(p.getId());
            vo.setPlotCode(p.getPlotCode());
            vo.setPlotName(p.getPlotName());
            vo.setPlotStatusLabel("空闲");
            vo.setIdleDate(idleDateByPlot.get(p.getId()));
            vo.setLastFarmDate(lastFarmDateByPlot.get(p.getId()));
            vo.setDisasterRecords(disasterByPlot.getOrDefault(p.getId(), List.of()));
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }

    /**
     * 按地块批量构建「最近 3 条灾害记录」（r18，按 farm_date 倒序 + id 倒序）。
     *
     * <p>一次 {@code IN (plotIds)} 全量拉 {@code farm_type='disaster'} 记录（DB 端
     * {@code farm_date DESC} 排序），Java 端按 plotId 分组 + 取前 {@value #MAX_DISASTER_PER_PLOT} 条，
     * 避免按地块逐条单查（远程 RDS 下 N+1 拖慢）。{@code disasterType} 经
     * {@code djs_disaster_type} 字典翻译为 label（mp 卡无字典上下文）。</p>
     *
     * @param plotIds 地块 id 集合（可空/空时返空 Map）
     * @return {@code plotId -> 最近 3 条灾害记录（farm_date 倒序）}
     */
    private Map<Long, List<IdlePlotVo.DisasterRecord>> buildDisasterRecords(List<Long> plotIds) {
        if (plotIds == null || plotIds.isEmpty()) {
            return Map.of();
        }
        List<FarmRecords> disasterRows = farmRecordsMapper.selectList(
            new LambdaQueryWrapper<FarmRecords>()
                .eq(FarmRecords::getFarmType, "disaster")
                .in(FarmRecords::getPlotId, plotIds)
                .orderByDesc(FarmRecords::getFarmDate)
                .orderByDesc(FarmRecords::getId));
        Map<Long, List<IdlePlotVo.DisasterRecord>> byPlot = new java.util.HashMap<>();
        for (FarmRecords r : disasterRows) {
            Long plotId = r.getPlotId();
            if (plotId == null) {
                continue;
            }
            List<IdlePlotVo.DisasterRecord> list = byPlot.computeIfAbsent(plotId, k -> new ArrayList<>());
            // 已排序：DB 端 farm_date/id 倒序，按 plot 累计到上限即跳过余下（保留最近 3 条）
            if (list.size() >= MAX_DISASTER_PER_PLOT) {
                continue;
            }
            IdlePlotVo.DisasterRecord vo = new IdlePlotVo.DisasterRecord();
            vo.setDisasterType(r.getDisasterType());
            String label = StringUtils.isBlank(r.getDisasterType())
                ? null
                : dictService.getDictLabel(DICT_DISASTER_TYPE, r.getDisasterType());
            // 字典缺失/未翻译时回落原始值，避免卡片空白
            vo.setDisasterTypeLabel(StringUtils.isBlank(label) ? r.getDisasterType() : label);
            vo.setLossRate(r.getLossRate());
            vo.setFarmDate(r.getFarmDate());
            list.add(vo);
        }
        return byPlot;
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
