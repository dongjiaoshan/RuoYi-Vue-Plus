package org.dromara.djs.plant.plot.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.farm.mapper.FarmRecordsTeamMapper;
import org.dromara.djs.plant.plot.domain.vo.PlotPlantingStatVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotCardVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotFarmworkVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotPlantingVo;
import org.dromara.djs.plant.plot.mapper.AppletPlotQueryMapper;
import org.dromara.djs.plant.plot.service.IAppletPlotQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link IAppletPlotQueryService} 实现（V7-R31 / V7-R32）。
 *
 * <p>只读聚合：地块基础行 1 次 + 当前茬候选 1 次 + 统计 1 次（批量 IN，禁 N+1）。</p>
 *
 * @author djs
 */
@Service
@RequiredArgsConstructor
public class AppletPlotQueryServiceImpl implements IAppletPlotQueryService {

    /** plot_status = 1 空闲。 */
    private static final int PLOT_STATUS_IDLE = 1;

    /** 采摘完成状态（djs_pick_status）。 */
    private static final String HARVEST_COMPLETED = "completed";

    /** 农事类型字典。 */
    private static final String DICT_FARM_WORK_TYPE = "djs_farm_work_type";

    /** 作物默认图归属（IMG-LIB-001 作物统一走果蔬默认图）。 */
    private static final String CROP_BELONG_TYPE = "vegetable";

    private final AppletPlotQueryMapper plotQueryMapper;
    private final FarmRecordsTeamMapper farmRecordsTeamMapper;
    private final ImageUrlResolver imageUrlResolver;
    private final DictService dictService;

    @Override
    public List<AppletPlotCardVo> listPlots(String plotName, Integer plotStatus) {
        String keyword = StringUtils.isNotBlank(plotName) ? plotName.trim() : null;
        List<AppletPlotCardVo> plots = plotQueryMapper.selectPlots(keyword, plotStatus);
        enrich(plots);
        return plots;
    }

    @Override
    public AppletPlotCardVo getPlot(Long plotId) {
        AppletPlotCardVo plot = plotQueryMapper.selectPlotById(plotId);
        if (plot == null) {
            throw new ServiceException("地块不存在或已删除");
        }
        List<AppletPlotCardVo> one = new ArrayList<>();
        one.add(plot);
        enrich(one);
        return plot;
    }

    @Override
    public List<AppletPlotPlantingVo> listPlantings(Long plotId) {
        List<AppletPlotPlantingVo> rows = plotQueryMapper.selectPlantings(plotId);
        if (rows.isEmpty()) {
            return rows;
        }
        // 「最高」= 采摘完成且有实际产量、面积>0 的行里亩产（actual/area）最大者；同值取 id 大者。
        // 与地块查询卡「亩产最高作物」（selectStartedStats 子查询）同一排序规则，两处必指同一茬。
        AppletPlotPlantingVo best = null;
        BigDecimal bestPerMu = null;
        for (AppletPlotPlantingVo r : rows) {
            r.setTop(Boolean.FALSE);
            BigDecimal perMu = perMu(r);
            if (perMu == null) {
                continue;
            }
            int cmp = bestPerMu == null ? 1 : perMu.compareTo(bestPerMu);
            if (cmp > 0 || (cmp == 0 && r.getId() != null && best.getId() != null && r.getId() > best.getId())) {
                best = r;
                bestPerMu = perMu;
            }
        }
        if (best != null) {
            best.setTop(Boolean.TRUE);
        }
        List<String> urls = imageUrlResolver.resolveList(rows.stream()
            .map(r -> new ImageUrlResolver.Item(r.getCropImage(), CROP_BELONG_TYPE))
            .toList());
        if (urls.size() == rows.size()) {
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).setCropImage(urls.get(i));
            }
        }
        return rows;
    }

    @Override
    public List<AppletPlotFarmworkVo> listFarmworks(Long plotId) {
        List<AppletPlotFarmworkVo> rows = plotQueryMapper.selectFarmworks(plotId);
        if (rows.isEmpty()) {
            return rows;
        }
        List<Long> recordIds = rows.stream().map(AppletPlotFarmworkVo::getId).filter(Objects::nonNull).toList();
        Map<Long, String> teamMap = new LinkedHashMap<>();
        if (!recordIds.isEmpty()) {
            for (Map<String, Object> m : farmRecordsTeamMapper.selectTeamNamesByRecordIds(recordIds)) {
                Object rid = m.get("recordId");
                Object name = m.get("teamName");
                if (rid == null || name == null) {
                    continue;
                }
                Long key = ((Number) rid).longValue();
                teamMap.merge(key, name.toString(), (a, b) -> a + "," + b);
            }
        }
        for (AppletPlotFarmworkVo r : rows) {
            String teams = teamMap.get(r.getId());
            r.setTeamNames(StringUtils.isNotBlank(teams) ? teams : r.getFarmByName());
            // 空闲状态下作业的农事（翻耕 / 整地等）不属于任何一茬：作物置空，作物下拉也不会列出它
            if (Objects.equals(r.getPlotType(), PLOT_STATUS_IDLE)) {
                r.setCropId(null);
                r.setCropName(null);
            }
            if (StringUtils.isNotBlank(r.getFarmType())) {
                String label = dictService.getDictLabel(DICT_FARM_WORK_TYPE, r.getFarmType());
                r.setFarmTypeLabel(StringUtils.isNotBlank(label) ? label : r.getFarmType());
            }
        }
        return rows;
    }

    /** 回填当前茬（非空闲地块）+ 历史统计。 */
    private void enrich(List<AppletPlotCardVo> plots) {
        if (plots.isEmpty()) {
            return;
        }
        List<Long> plotIds = plots.stream().map(AppletPlotCardVo::getId).filter(Objects::nonNull).distinct().toList();
        if (plotIds.isEmpty()) {
            return;
        }
        // 候选按 plot_id、种植日期降序返回 → 每地块首行即当前茬
        Map<Long, AppletPlotCardVo> current = new LinkedHashMap<>();
        for (AppletPlotCardVo c : plotQueryMapper.selectCurrentCandidates(plotIds)) {
            current.putIfAbsent(c.getId(), c);
        }
        Map<Long, PlotPlantingStatVo> stats = plotQueryMapper.selectStartedStats(plotIds).stream()
            .collect(Collectors.toMap(PlotPlantingStatVo::getPlotId, Function.identity(), (a, b) -> a));
        for (AppletPlotCardVo p : plots) {
            if (!Objects.equals(p.getPlotStatus(), PLOT_STATUS_IDLE)) {
                AppletPlotCardVo c = current.get(p.getId());
                if (c != null) {
                    p.setCropName(c.getCropName());
                    p.setPlantDate(c.getPlantDate());
                    p.setBeginHarvestdate(c.getBeginHarvestdate());
                    p.setEndHarvestdate(c.getEndHarvestdate());
                    p.setExpectedYield(c.getExpectedYield());
                    p.setActualYield(c.getActualYield());
                }
            }
            PlotPlantingStatVo s = stats.get(p.getId());
            if (s != null) {
                p.setHistoryPlantCount(s.getHistoryPlantCount());
                p.setMaxYieldCropName(s.getMaxYieldCropName());
                p.setMaxYieldPerMu(s.getMaxYieldPerMu() == null ? null
                    : s.getMaxYieldPerMu().setScale(2, RoundingMode.HALF_UP));
            } else {
                p.setHistoryPlantCount(0L);
            }
        }
    }

    /** 本条亩产（kg/亩）；不参与评比返 null。 */
    private static BigDecimal perMu(AppletPlotPlantingVo r) {
        if (!HARVEST_COMPLETED.equals(r.getHarvestStatus()) || r.getActualYield() == null
            || r.getPlotArea() == null || r.getPlotArea().signum() <= 0) {
            return null;
        }
        return r.getActualYield().divide(r.getPlotArea(), 6, RoundingMode.HALF_UP);
    }
}
