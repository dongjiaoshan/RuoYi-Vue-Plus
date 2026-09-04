package org.dromara.djs.plant.farmmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.plant.farmmap.domain.FarmMapRegion;
import org.dromara.djs.plant.farmmap.domain.bo.FarmMapBindBo;
import org.dromara.djs.plant.farmmap.domain.vo.FarmMapOverviewVo;
import org.dromara.djs.plant.farmmap.domain.vo.FarmMapRegionVo;
import org.dromara.djs.plant.farmmap.domain.vo.FarmMapUnboundPlotVo;
import org.dromara.djs.plant.farmmap.mapper.FarmMapRegionMapper;
import org.dromara.djs.plant.farmmap.service.IFarmMapService;
import org.dromara.djs.plant.plot.domain.query.PlotInfoQuery;
import org.dromara.djs.plant.plot.domain.vo.PlotInfoVo;
import org.dromara.djs.plant.plot.service.IPlotInfoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 农场地图 Service 实现（PLT-FARMMAP-001）。
 *
 * <p><b>为什么在内存里 join 而不写 LEFT JOIN</b>：地块富化（片区名）已经在
 * {@link IPlotInfoService#queryList} 里做过一遍，写一条新的 JOIN SQL 等于把那段富化逻辑
 * 复制第二份，将来片区改名规则一变就两处不同步。V1 地块 &lt; 200 行，全量拉回来
 * 用 Map 对一遍的开销可以忽略。数据量真涨上去（多农场 V2）再换 JOIN。</p>
 *
 * @author djs
 * @since PLT-FARMMAP-001
 */
@Slf4j
@Service
public class FarmMapServiceImpl extends DjsBaseServiceImpl<FarmMapRegionMapper, FarmMapRegion>
    implements IFarmMapService {

    private final IPlotInfoService plotInfoService;

    public FarmMapServiceImpl(FarmMapRegionMapper baseMapper, IPlotInfoService plotInfoService) {
        super(baseMapper);
        this.plotInfoService = plotInfoService;
    }

    @Override
    public FarmMapOverviewVo overview() {
        List<PlotInfoVo> plots = plotInfoService.queryList(new PlotInfoQuery());
        Map<Long, PlotInfoVo> plotById = plots.stream()
            .collect(Collectors.toMap(PlotInfoVo::getId, Function.identity(), (a, b) -> a));

        List<FarmMapRegion> bindings = baseMapper.selectList(new LambdaQueryWrapper<>());

        List<FarmMapRegionVo> regions = new ArrayList<>();
        Set<Long> boundPlotIds = new java.util.HashSet<>();
        for (FarmMapRegion binding : bindings) {
            PlotInfoVo plot = plotById.get(binding.getPlotId());
            if (plot == null) {
                // 地块被删了但绑定还在。不静默丢弃也不抛错：图上这个格子按未挂画，
                // 日志留痕给 closing 看——这是数据不一致，不是用户操作错误。
                log.warn("农场地图绑定指向了不存在的地块 regionKey={} plotId={}", binding.getRegionKey(), binding.getPlotId());
                continue;
            }
            boundPlotIds.add(plot.getId());
            regions.add(toRegionVo(binding.getRegionKey(), plot));
        }

        List<FarmMapUnboundPlotVo> unbound = plots.stream()
            .filter(p -> !boundPlotIds.contains(p.getId()))
            .map(this::toUnboundVo)
            .sorted(Comparator.comparing(FarmMapUnboundPlotVo::getZoneName, Comparator.nullsLast(String::compareTo))
                .thenComparing(FarmMapUnboundPlotVo::getPlotCode, Comparator.nullsLast(String::compareTo)))
            .toList();

        FarmMapOverviewVo vo = new FarmMapOverviewVo();
        vo.setRegions(regions);
        vo.setUnboundPlots(unbound);
        vo.setPlotTotal(plots.size());
        vo.setBoundCount(regions.size());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int bind(FarmMapBindBo bo) {
        FarmMapRegion byPlot = findByPlotId(bo.getPlotId());
        if (byPlot != null && !byPlot.getRegionKey().equals(bo.getRegionKey())) {
            // 1:1：一块地只能挂一个格子。这里主动拦是为了给出人话报错——
            // 不拦的话会撞 uk_fmr_plot 抛 DuplicateKeyException，前端只能看到一句 SQL 错误。
            throw new ServiceException("该地块已挂在格子 " + byPlot.getRegionKey() + " 上，请先解绑");
        }

        FarmMapRegion existing = findByRegionKey(bo.getRegionKey());
        if (existing != null) {
            if (existing.getPlotId().equals(bo.getPlotId())) {
                return 0;
            }
            // 格子改挂另一块地：直接改 plot_id，保留同一行，绑定历史不留痕（§11 不记录中间态）
            existing.setPlotId(bo.getPlotId());
            return baseMapper.updateById(existing);
        }

        FarmMapRegion entity = new FarmMapRegion();
        entity.setRegionKey(bo.getRegionKey());
        entity.setPlotId(bo.getPlotId());
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unbind(String regionKey) {
        FarmMapRegion existing = findByRegionKey(regionKey);
        if (existing == null) {
            return 0;
        }
        return softDelete(List.of(existing.getId()));
    }

    private FarmMapRegion findByRegionKey(String regionKey) {
        return baseMapper.selectOne(new LambdaQueryWrapper<FarmMapRegion>()
            .eq(FarmMapRegion::getRegionKey, regionKey));
    }

    private FarmMapRegion findByPlotId(Long plotId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<FarmMapRegion>()
            .eq(FarmMapRegion::getPlotId, plotId));
    }

    private FarmMapRegionVo toRegionVo(String regionKey, PlotInfoVo plot) {
        FarmMapRegionVo vo = new FarmMapRegionVo();
        vo.setRegionKey(regionKey);
        vo.setPlotId(plot.getId());
        vo.setPlotCode(plot.getPlotCode());
        vo.setPlotName(plot.getPlotName());
        vo.setZoneName(plot.getZoneName());
        vo.setZoneBelong(plot.getZoneBelong());
        vo.setPlotType(plot.getPlotType());
        vo.setPlotStatus(plot.getPlotStatus());
        vo.setPlotArea(plot.getPlotArea());
        return vo;
    }

    private FarmMapUnboundPlotVo toUnboundVo(PlotInfoVo plot) {
        FarmMapUnboundPlotVo vo = new FarmMapUnboundPlotVo();
        vo.setId(plot.getId());
        vo.setPlotCode(plot.getPlotCode());
        vo.setPlotName(plot.getPlotName());
        vo.setZoneName(plot.getZoneName());
        vo.setZoneBelong(plot.getZoneBelong());
        return vo;
    }

}
