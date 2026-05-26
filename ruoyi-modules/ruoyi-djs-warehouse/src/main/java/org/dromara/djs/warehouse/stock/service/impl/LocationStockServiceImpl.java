package org.dromara.djs.warehouse.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.bo.LocationStockBo;
import org.dromara.djs.warehouse.stock.domain.query.LocationStockQuery;
import org.dromara.djs.warehouse.stock.domain.vo.LocationStockVo;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.stock.service.ILocationStockService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 库存明细 Service 实现（WMS-MD-001）。
 *
 * <p>查询时 service 层 JOIN {@code t_warehouse_location_info} 回填 {@code locationName}，
 * 避免 VO 走 ruoyi {@code @Translation} 注册位置类型（V1 简化路径）。</p>
 *
 * <p>{@code operatorId} 在 {@link #insertByBo(LocationStockBo)} 中走
 * {@link LoginHelper#getUserId()} 注入（ADR-0007 强制 — D6 #14 教训）。</p>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Slf4j
@Service
public class LocationStockServiceImpl extends DjsBaseServiceImpl<LocationStockMapper, LocationStock> implements ILocationStockService {

    private final LocationInfoMapper locationInfoMapper;

    public LocationStockServiceImpl(LocationStockMapper baseMapper, LocationInfoMapper locationInfoMapper) {
        super(baseMapper);
        this.locationInfoMapper = locationInfoMapper;
    }

    @Override
    public TableDataInfo<LocationStockVo> queryPageList(LocationStockQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<LocationStock> wrapper = buildQueryWrapper(query);
        Page<LocationStockVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillLocationNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<LocationStockVo> queryList(LocationStockQuery query) {
        List<LocationStockVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillLocationNames(list);
        return list;
    }

    @Override
    public LocationStockVo queryById(Long id) {
        LocationStockVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillLocationNames(List.of(vo));
        }
        return vo;
    }

    @Override
    public int insertByBo(LocationStockBo bo) {
        validateThreeWayExclusive(bo);
        LocationStock entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("库存入参转换失败");
        }
        if (entity.getIsEnd() == null) {
            entity.setIsEnd(0);
        }
        // ADR-0007：最后操作人显式注入（D6 #14 BRD-EVENT-002 教训：依赖 createBy 不够，需独立 operatorId 字段）
        entity.setOperatorId(LoginHelper.getUserId());
        return baseMapper.insert(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        return softDelete(ids);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus。
     *
     * <p>protected 方便 Mockito 单测覆盖（避免启 Spring 上下文）。</p>
     */
    protected LocationStock toEntity(LocationStockBo bo) {
        return MapstructUtils.convert(bo, LocationStock.class);
    }

    /**
     * 校验 productId / earNo / plotId 三选一（非空字段恰好一个）。
     */
    private void validateThreeWayExclusive(LocationStockBo bo) {
        int filled = 0;
        if (bo.getProductId() != null) filled++;
        if (StringUtils.isNotBlank(bo.getEarNo())) filled++;
        if (bo.getPlotId() != null) filled++;
        if (filled != 1) {
            throw new ServiceException("stock.three_way.exclusive");
        }
    }

    /**
     * 批量回填 {@code locationName}（避免 N+1，单次 IN 查 location 表）。
     */
    private void fillLocationNames(List<LocationStockVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> locationIds = records.stream()
            .map(LocationStockVo::getLocationId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (locationIds.isEmpty()) {
            return;
        }
        List<LocationInfo> locations = locationInfoMapper.selectList(
            new LambdaQueryWrapper<LocationInfo>().in(LocationInfo::getId, locationIds));
        Map<Long, String> nameMap = locations.stream()
            .collect(Collectors.toMap(LocationInfo::getId, LocationInfo::getLocationName, (a, b) -> a));
        for (LocationStockVo vo : records) {
            if (vo.getLocationId() != null) {
                vo.setLocationName(nameMap.get(vo.getLocationId()));
            }
        }
    }

    /**
     * 构造查询条件。
     */
    private LambdaQueryWrapper<LocationStock> buildQueryWrapper(LocationStockQuery query) {
        LambdaQueryWrapper<LocationStock> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(LocationStock::getId);
        }
        wrapper.eq(query.getLocationId() != null, LocationStock::getLocationId, query.getLocationId())
            .eq(query.getProductId() != null, LocationStock::getProductId, query.getProductId())
            .like(StringUtils.isNotBlank(query.getProductName()), LocationStock::getProductName, query.getProductName())
            .eq(StringUtils.isNotBlank(query.getEarNo()), LocationStock::getEarNo, query.getEarNo())
            .eq(query.getPlotId() != null, LocationStock::getPlotId, query.getPlotId())
            .eq(query.getIsEnd() != null, LocationStock::getIsEnd, query.getIsEnd())
            .orderByDesc(LocationStock::getId);
        return wrapper;
    }

}
