package org.dromara.djs.plant.plot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.domain.bo.PlotInfoBo;
import org.dromara.djs.plant.plot.domain.query.PlotInfoQuery;
import org.dromara.djs.plant.plot.domain.vo.PlotFarmworkRecordVo;
import org.dromara.djs.plant.plot.domain.vo.PlotInfoVo;
import org.dromara.djs.plant.plot.domain.vo.PlotOrganicRefVo;
import org.dromara.djs.plant.plot.domain.vo.PlotPlantingRecordVo;
import org.dromara.djs.plant.plot.domain.vo.PlotPlantingStatVo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.plot.service.IPlotInfoService;
import org.dromara.djs.plant.zone.domain.PlotZone;
import org.dromara.djs.plant.zone.mapper.PlotZoneMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 地块 Service 实现（PLT-MD-001）。
 *
 * <p>列表查询时 service 层批量取 zone 名字 enrich 到 VO（一次额外 query，避免 N+1）；
 * V1 数据量小（&lt; 100 地块）够用，V2 改 LEFT JOIN。分页列表额外批量回填派生统计
 * （历史种植次数 / 最高亩产作物 / 最高亩作物产量，按 plot_id 聚合 t_plant_plant_details）。</p>
 *
 * <p>删除前校验：仅空闲态地块可删，种植/采摘态拦截。</p>
 *
 * @author djs
 * @since PLT-MD-001
 */
@Slf4j
@Service
public class PlotInfoServiceImpl extends DjsBaseServiceImpl<PlotInfoMapper, PlotInfo> implements IPlotInfoService {

    private final PlotZoneMapper plotZoneMapper;

    public PlotInfoServiceImpl(PlotInfoMapper baseMapper, PlotZoneMapper plotZoneMapper) {
        super(baseMapper);
        this.plotZoneMapper = plotZoneMapper;
    }

    @Override
    public TableDataInfo<PlotInfoVo> queryPageList(PlotInfoQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PlotInfo> wrapper = buildQueryWrapper(query);
        Page<PlotInfoVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichZoneNames(page.getRecords());
        fillPlantingStats(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PlotInfoVo> queryList(PlotInfoQuery query) {
        List<PlotInfoVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        enrichZoneNames(list);
        return list;
    }

    @Override
    public PlotInfoVo queryById(Long id) {
        PlotInfoVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getZoneId() != null) {
            PlotZone zone = plotZoneMapper.selectById(vo.getZoneId());
            if (zone != null) {
                vo.setZoneName(zone.getZoneName());
                vo.setZoneBelong(zone.getZoneBelong());
            }
        }
        // 当前作物（D8 阶段 t_plant_plant_details 0 行，先返回 null）
        // TODO PLT-PLAN-001 落地后启用：JOIN t_plant_plant_details WHERE plot_id=? AND end_actualdate IS NULL
        return vo;
    }

    @Override
    public int insertByBo(PlotInfoBo bo) {
        PlotInfo entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("地块入参转换失败");
        }
        // 校验所属片区存在
        PlotZone zone = plotZoneMapper.selectById(entity.getZoneId());
        if (zone == null) {
            throw new ServiceException("所属片区不存在：" + entity.getZoneId());
        }
        if (entity.getPlotStatus() == null) {
            entity.setPlotStatus(1);
        }
        if (entity.getIsLease() == null) {
            entity.setIsLease(0);
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(PlotInfoBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("地块 ID 不能为空");
        }
        PlotInfo exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("地块不存在或已删除：" + bo.getId());
        }
        // 校验所属片区存在（修改时如果改了 zone_id）
        if (bo.getZoneId() != null && !bo.getZoneId().equals(exists.getZoneId())) {
            PlotZone zone = plotZoneMapper.selectById(bo.getZoneId());
            if (zone == null) {
                throw new ServiceException("所属片区不存在：" + bo.getZoneId());
            }
        }
        PlotInfo entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("地块入参转换失败");
        }
        // plot_code 不允许修改
        entity.setPlotCode(exists.getPlotCode());
        // plot_status 编辑接口只读：状态只允许业务动作（种植/采摘/退茬）流转，置 null 让 updateById 跳过该列
        entity.setPlotStatus(null);
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<PlotInfo> plots = baseMapper.selectByIds(ids);
        for (PlotInfo plot : plots) {
            Integer status = plot.getPlotStatus();
            if (status != null && (status.equals(2) || status.equals(3))) {
                String name = StringUtils.isNotBlank(plot.getPlotName()) ? plot.getPlotName() : plot.getPlotCode();
                throw new ServiceException("地块 [" + name + "] 处于种植/采摘状态，不允许删除");
            }
        }
        return softDelete(ids);
    }

    @Override
    public List<PlotPlantingRecordVo> listPlantingByPlot(Long plotId) {
        if (plotId == null) {
            return List.of();
        }
        return baseMapper.selectPlantingByPlot(plotId);
    }

    @Override
    public List<PlotFarmworkRecordVo> listFarmworkByPlot(Long plotId) {
        if (plotId == null) {
            return List.of();
        }
        return baseMapper.selectFarmworkByPlot(plotId);
    }

    @Override
    public List<PlotOrganicRefVo> listOrganicByPlot(Long plotId) {
        if (plotId == null) {
            return List.of();
        }
        return baseMapper.selectOrganicByPlot(plotId);
    }

    /** 批量取 zone 名字 enrich 到 VO。 */
    private void enrichZoneNames(List<PlotInfoVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Long> zoneIds = list.stream()
            .map(PlotInfoVo::getZoneId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        if (zoneIds.isEmpty()) {
            return;
        }
        List<PlotZone> zones = plotZoneMapper.selectByIds(zoneIds);
        Map<Long, PlotZone> zoneMap = new HashMap<>();
        for (PlotZone z : zones) {
            zoneMap.put(z.getId(), z);
        }
        for (PlotInfoVo vo : list) {
            PlotZone zone = vo.getZoneId() != null ? zoneMap.get(vo.getZoneId()) : null;
            if (zone != null) {
                vo.setZoneName(zone.getZoneName());
                vo.setZoneBelong(zone.getZoneBelong());
            }
        }
    }

    /**
     * 批量回填地块派生统计（历史种植次数 / 最高亩产作物 / 最高亩作物产量，按 plot_id 聚合
     * {@code t_plant_plant_details}）。一次性 IN 查询所有 plotId 的聚合行（禁 N+1）；
     * 无种植记录的地块兜底 historyPlantCount=0、其余 null。
     */
    private void fillPlantingStats(List<PlotInfoVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> plotIds = records.stream()
            .map(PlotInfoVo::getId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (plotIds.isEmpty()) {
            return;
        }
        Map<Long, PlotPlantingStatVo> statMap = baseMapper.selectPlantingStats(plotIds).stream()
            .collect(Collectors.toMap(PlotPlantingStatVo::getPlotId, java.util.function.Function.identity(), (a, b) -> a));
        for (PlotInfoVo vo : records) {
            PlotPlantingStatVo stat = statMap.get(vo.getId());
            if (stat != null) {
                vo.setHistoryPlantCount(stat.getHistoryPlantCount());
                vo.setMaxYieldPerMu(stat.getMaxYieldPerMu());
                vo.setMaxYieldCropName(stat.getMaxYieldCropName());
            } else {
                vo.setHistoryPlantCount(0L);
            }
        }
    }

    protected PlotInfo toEntity(PlotInfoBo bo) {
        return MapstructUtils.convert(bo, PlotInfo.class);
    }

    private LambdaQueryWrapper<PlotInfo> buildQueryWrapper(PlotInfoQuery query) {
        LambdaQueryWrapper<PlotInfo> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PlotInfo::getUpdateTime).orderByDesc(PlotInfo::getId);
        }
        wrapper.eq(query.getZoneId() != null, PlotInfo::getZoneId, query.getZoneId())
            .like(StringUtils.isNotBlank(query.getPlotCode()), PlotInfo::getPlotCode, query.getPlotCode())
            .like(StringUtils.isNotBlank(query.getPlotName()), PlotInfo::getPlotName, query.getPlotName())
            .eq(StringUtils.isNotBlank(query.getPlotType()), PlotInfo::getPlotType, query.getPlotType())
            .eq(query.getPlotStatus() != null, PlotInfo::getPlotStatus, query.getPlotStatus())
            .eq(query.getIsLease() != null, PlotInfo::getIsLease, query.getIsLease())
            .orderByDesc(PlotInfo::getUpdateTime).orderByDesc(PlotInfo::getId);
        return wrapper;
    }
}
