package org.dromara.djs.warehouse.location.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.domain.bo.LocationInfoBo;
import org.dromara.djs.warehouse.location.domain.query.LocationInfoQuery;
import org.dromara.djs.warehouse.location.domain.vo.LocationInfoVo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.location.service.ILocationInfoService;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 库位 Service 实现（WMS-MD-001）。
 *
 * <p>软删走基类 {@link DjsBaseServiceImpl#softDelete}；删除前校验：若库位下仍有
 * {@code product_stock > 0} 的库存记录，抛 {@code ServiceException("location.has_stock")}。
 * 校验通过单次 {@link LocationStockMapper#countActiveStockByLocation} count 完成，避免拉全行。</p>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Slf4j
@Service
public class LocationInfoServiceImpl extends DjsBaseServiceImpl<LocationInfoMapper, LocationInfo> implements ILocationInfoService {

    private final LocationStockMapper stockMapper;

    public LocationInfoServiceImpl(LocationInfoMapper baseMapper, LocationStockMapper stockMapper) {
        super(baseMapper);
        this.stockMapper = stockMapper;
    }

    @Override
    public TableDataInfo<LocationInfoVo> queryPageList(LocationInfoQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<LocationInfo> wrapper = buildQueryWrapper(query);
        Page<LocationInfoVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
    }

    @Override
    public List<LocationInfoVo> queryList(LocationInfoQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public LocationInfoVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public int insertByBo(LocationInfoBo bo) {
        LocationInfo entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("库位入参转换失败");
        }
        if (entity.getLocationStatus() == null) {
            entity.setLocationStatus(1);
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(LocationInfoBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("库位 ID 不能为空");
        }
        LocationInfo exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("库位不存在或已删除：" + bo.getId());
        }
        LocationInfo entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("库位入参转换失败");
        }
        // locationCode 不允许通过编辑端点修改（DB UNIQUE 兜底，应用层显式锁住语义）
        entity.setLocationCode(exists.getLocationCode());
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 删除前校验：每个库位都不能有 product_stock > 0 的库存记录
        for (Long id : ids) {
            long active = stockMapper.countActiveStockByLocation(id);
            if (active > 0) {
                LocationInfo loc = baseMapper.selectById(id);
                String name = loc != null ? loc.getLocationName() : String.valueOf(id);
                throw new ServiceException("库位 [" + name + "] 仍有库存，不允许删除");
            }
        }
        return softDelete(ids);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus。
     *
     * <p>protected 方便 Mockito 单测覆盖。</p>
     */
    protected LocationInfo toEntity(LocationInfoBo bo) {
        return MapstructUtils.convert(bo, LocationInfo.class);
    }

    private LambdaQueryWrapper<LocationInfo> buildQueryWrapper(LocationInfoQuery query) {
        LambdaQueryWrapper<LocationInfo> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(LocationInfo::getId);
        }
        wrapper.eq(StringUtils.isNotBlank(query.getLocationCode()), LocationInfo::getLocationCode, query.getLocationCode())
            .like(StringUtils.isNotBlank(query.getLocationName()), LocationInfo::getLocationName, query.getLocationName())
            .eq(StringUtils.isNotBlank(query.getLocationType()), LocationInfo::getLocationType, query.getLocationType())
            .eq(query.getLocationStatus() != null, LocationInfo::getLocationStatus, query.getLocationStatus())
            .orderByDesc(LocationInfo::getId);
        return wrapper;
    }

}
