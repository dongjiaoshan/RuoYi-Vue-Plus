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
import org.dromara.djs.plant.plot.domain.vo.PlotInfoVo;
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
 * V1 数据量小（&lt; 100 地块）够用，V2 改 LEFT JOIN。</p>
 *
 * <p>删除前校验：D8 阶段 stub。t_plant_plant_plan / t_plant_farm_records /
 * t_plant_plant_details 在 D9+ PLT-PLAN-001 / PLT-WORK-001 / PLT-PICK-001 落地，
 * 届时 service 加 count + throw plant.plot.has_business_data。</p>
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
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // D8 阶段 stub：t_plant_plant_plan / t_plant_farm_records / t_plant_plant_details 0 行，
        // D9+ PLT-PLAN-001 / PLT-WORK-001 / PLT-PICK-001 落地后启用：
        //   long active = planMapper.selectCount(... plot_id IN(ids) AND del_flag=0)
        //                + recordMapper.selectCount(...)
        //                + detailMapper.selectCount(...);
        //   if (active > 0) throw new ServiceException("plant.plot.has_business_data");
        return softDelete(ids);
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
        Map<Long, String> zoneNameMap = new HashMap<>();
        for (PlotZone z : zones) {
            zoneNameMap.put(z.getId(), z.getZoneName());
        }
        for (PlotInfoVo vo : list) {
            if (vo.getZoneId() != null) {
                vo.setZoneName(zoneNameMap.get(vo.getZoneId()));
            }
        }
    }

    protected PlotInfo toEntity(PlotInfoBo bo) {
        return MapstructUtils.convert(bo, PlotInfo.class);
    }

    private LambdaQueryWrapper<PlotInfo> buildQueryWrapper(PlotInfoQuery query) {
        LambdaQueryWrapper<PlotInfo> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PlotInfo::getId);
        }
        wrapper.eq(query.getZoneId() != null, PlotInfo::getZoneId, query.getZoneId())
            .eq(StringUtils.isNotBlank(query.getPlotCode()), PlotInfo::getPlotCode, query.getPlotCode())
            .like(StringUtils.isNotBlank(query.getPlotName()), PlotInfo::getPlotName, query.getPlotName())
            .eq(StringUtils.isNotBlank(query.getPlotType()), PlotInfo::getPlotType, query.getPlotType())
            .eq(query.getPlotStatus() != null, PlotInfo::getPlotStatus, query.getPlotStatus())
            .eq(query.getIsLease() != null, PlotInfo::getIsLease, query.getIsLease())
            .orderByDesc(PlotInfo::getId);
        return wrapper;
    }
}
