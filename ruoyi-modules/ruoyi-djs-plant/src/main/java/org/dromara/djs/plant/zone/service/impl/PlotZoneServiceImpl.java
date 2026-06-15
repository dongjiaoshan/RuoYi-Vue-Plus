package org.dromara.djs.plant.zone.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.zone.domain.PlotZone;
import org.dromara.djs.plant.zone.domain.bo.PlotZoneBo;
import org.dromara.djs.plant.zone.domain.query.PlotZoneQuery;
import org.dromara.djs.plant.zone.domain.vo.PlotZoneVo;
import org.dromara.djs.plant.zone.mapper.PlotZoneMapper;
import org.dromara.djs.plant.zone.service.IPlotZoneService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 片区 Service 实现（PLT-MD-001）。
 *
 * <p>软删走基类 {@link DjsBaseServiceImpl#softDelete}；删除前校验：若片区下仍有
 * 未删除地块（{@code t_plant_plot_info.zone_id IN (ids) AND del_flag='0'}），
 * 抛 {@code ServiceException("plant.zone.has_plot")}。</p>
 *
 * @author djs
 * @since PLT-MD-001
 */
@Slf4j
@Service
public class PlotZoneServiceImpl extends DjsBaseServiceImpl<PlotZoneMapper, PlotZone> implements IPlotZoneService {

    private final PlotInfoMapper plotInfoMapper;

    public PlotZoneServiceImpl(PlotZoneMapper baseMapper, PlotInfoMapper plotInfoMapper) {
        super(baseMapper);
        this.plotInfoMapper = plotInfoMapper;
    }

    @Override
    public TableDataInfo<PlotZoneVo> queryPageList(PlotZoneQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PlotZone> wrapper = buildQueryWrapper(query);
        Page<PlotZoneVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichPlotCount(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PlotZoneVo> queryList(PlotZoneQuery query) {
        List<PlotZoneVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        enrichPlotCount(list);
        return list;
    }

    @Override
    public PlotZoneVo queryById(Long id) {
        PlotZoneVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            enrichPlotCount(List.of(vo));
        }
        return vo;
    }

    @Override
    public int insertByBo(PlotZoneBo bo) {
        PlotZone entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("片区入参转换失败");
        }
        // 未传状态默认启用：字典 sys_normal_disable 0=正常/启用，1=停用（与 admin 列表 toggle 同口径）。
        if (entity.getZoneStatus() == null) {
            entity.setZoneStatus(0);
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(PlotZoneBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("片区 ID 不能为空");
        }
        PlotZone exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("片区不存在或已删除：" + bo.getId());
        }
        PlotZone entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("片区入参转换失败");
        }
        // zoneCode 不允许修改（与 LocationInfo 一致）
        entity.setZoneCode(exists.getZoneCode());
        return baseMapper.updateById(entity);
    }

    @Override
    public int updateStatus(Long id, Integer status) {
        if (id == null) {
            throw new ServiceException("片区 ID 不能为空");
        }
        if (status == null) {
            throw new ServiceException("片区状态不能为空");
        }
        // 仅更新 zone_status 单字段（updateById 默认非 null 才更新，updateBy/updateTime 由 MetaObjectHandler 自动填）
        PlotZone entity = new PlotZone();
        entity.setId(id);
        entity.setZoneStatus(status);
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 删除前校验：每个片区下都不能有未删除地块
        for (Long id : ids) {
            long activePlots = plotInfoMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<org.dromara.djs.plant.plot.domain.PlotInfo>()
                    .eq(org.dromara.djs.plant.plot.domain.PlotInfo::getZoneId, id)
                    .eq(org.dromara.djs.plant.plot.domain.PlotInfo::getDelFlag, "0")
            );
            if (activePlots > 0) {
                PlotZone zone = baseMapper.selectById(id);
                String name = zone != null ? zone.getZoneName() : String.valueOf(id);
                throw new ServiceException("片区 [" + name + "] 仍有关联地块，不允许删除");
            }
        }
        return softDelete(ids);
    }

    /**
     * BO → Entity 转换钩子（MapStruct-Plus）。
     */
    protected PlotZone toEntity(PlotZoneBo bo) {
        return MapstructUtils.convert(bo, PlotZone.class);
    }

    /**
     * 批量回填「管理地块数量」：一次 GROUP BY 取全片区地块数，按 zoneId 回填 VO，
     * 无地块片区兜底 0（避免 N+1，对齐 {@code PlotInfoServiceImpl.enrichZoneNames}）。
     */
    private void enrichPlotCount(List<PlotZoneVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Map<String, Object>> rows = baseMapper.selectPlotCountByZone();
        Map<Long, Long> countMap = new HashMap<>();
        if (rows == null) {
            rows = List.of();
        }
        for (Map<String, Object> row : rows) {
            Object zoneIdObj = row.get("zoneId");
            Object countObj = row.get("plotCount");
            if (zoneIdObj != null && countObj != null) {
                countMap.put(((Number) zoneIdObj).longValue(), ((Number) countObj).longValue());
            }
        }
        for (PlotZoneVo vo : list) {
            if (vo.getId() != null) {
                vo.setPlotCount(countMap.getOrDefault(vo.getId(), 0L));
            }
        }
    }

    private LambdaQueryWrapper<PlotZone> buildQueryWrapper(PlotZoneQuery query) {
        LambdaQueryWrapper<PlotZone> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PlotZone::getId);
        }
        wrapper.eq(StringUtils.isNotBlank(query.getZoneCode()), PlotZone::getZoneCode, query.getZoneCode())
            .like(StringUtils.isNotBlank(query.getZoneName()), PlotZone::getZoneName, query.getZoneName())
            .eq(query.getZoneStatus() != null, PlotZone::getZoneStatus, query.getZoneStatus())
            .eq(query.getUpdateBy() != null, PlotZone::getUpdateBy, query.getUpdateBy())
            .ge(StringUtils.isNotBlank(query.getUpdateTimeStart()), PlotZone::getUpdateTime, query.getUpdateTimeStart())
            .le(StringUtils.isNotBlank(query.getUpdateTimeEnd()), PlotZone::getUpdateTime, query.getUpdateTimeEnd())
            .orderByDesc(PlotZone::getId);
        return wrapper;
    }
}
