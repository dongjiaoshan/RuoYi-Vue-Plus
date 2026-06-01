package org.dromara.djs.warehouse.location.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.domain.bo.LocationInfoBo;
import org.dromara.djs.warehouse.location.domain.query.LocationInfoQuery;
import org.dromara.djs.warehouse.location.domain.vo.LocationCardSummaryVo;
import org.dromara.djs.warehouse.location.domain.vo.LocationInfoVo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.location.service.ILocationInfoService;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 库位类型字典 type（卡片网格 8 类 base）。
     */
    private static final String DICT_LOCATION_TYPE = "djs_location_type";

    private final LocationStockMapper stockMapper;
    private final DictService dictService;

    public LocationInfoServiceImpl(LocationInfoMapper baseMapper, LocationStockMapper stockMapper, DictService dictService) {
        super(baseMapper);
        this.stockMapper = stockMapper;
        this.dictService = dictService;
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

    @Override
    public List<LocationCardSummaryVo> getCardSummary() {
        String tenantId = TenantHelper.getTenantId();
        // 8 类字典 value→label（LinkedHashMap 按 dict_sort 有序），作为固定 base
        Map<String, String> typeLabels = dictService.getAllDictByDictType(DICT_LOCATION_TYPE);

        // 3 路聚合（每路 WHERE 显式带 tenant_id，§0.5）→ 按 locationType 索引
        Map<String, LocationCardSummaryVo> stockMap = indexByType(baseMapper.selectStockSummaryByType(tenantId));
        Map<String, LocationCardSummaryVo> flowMap = indexByType(baseMapper.selectTodayFlowByType(tenantId));
        Map<String, LocationCardSummaryVo> checkMap = indexByType(baseMapper.selectLastCheckByType(tenantId));

        List<LocationCardSummaryVo> cards = new ArrayList<>(typeLabels.size());
        for (Map.Entry<String, String> e : typeLabels.entrySet()) {
            String type = e.getKey();
            LocationCardSummaryVo vo = new LocationCardSummaryVo();
            vo.setLocationType(type);
            vo.setLocationTypeLabel(e.getValue());

            LocationCardSummaryVo stock = stockMap.get(type);
            vo.setLocationCount(stock != null && stock.getLocationCount() != null ? stock.getLocationCount() : 0);
            vo.setProductCount(stock != null && stock.getProductCount() != null ? stock.getProductCount() : 0);
            vo.setCurrentStock(stock != null && stock.getCurrentStock() != null ? stock.getCurrentStock() : BigDecimal.ZERO);

            LocationCardSummaryVo flow = flowMap.get(type);
            vo.setTodayInQty(flow != null && flow.getTodayInQty() != null ? flow.getTodayInQty() : BigDecimal.ZERO);
            vo.setTodayOutQty(flow != null && flow.getTodayOutQty() != null ? flow.getTodayOutQty() : BigDecimal.ZERO);

            LocationCardSummaryVo check = checkMap.get(type);
            if (check != null) {
                vo.setLastCheckDate(check.getLastCheckDate());
                vo.setLastCheckResult(check.getLastCheckResult());
            }
            cards.add(vo);
        }
        return cards;
    }

    /**
     * 部分聚合结果按 locationType 建索引（locationType 在每类唯一）。
     */
    private Map<String, LocationCardSummaryVo> indexByType(List<LocationCardSummaryVo> rows) {
        Map<String, LocationCardSummaryVo> map = new LinkedHashMap<>();
        if (rows == null) {
            return map;
        }
        for (LocationCardSummaryVo row : rows) {
            if (row.getLocationType() != null) {
                map.put(row.getLocationType(), row);
            }
        }
        return map;
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
