package org.dromara.djs.warehouse.flow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.domain.query.StockFlowQuery;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.flow.service.IStockFlowService;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 出入库流水查询 Service 实现（WMS-MAT-001）。
 *
 * <p>VO 通过 service 层批量 JOIN 回填 {@code productName / productCode / belongType / productUnit / locationName}
 * （避免 N+1，沿 LocationStockServiceImpl.fillLocationNames 模式）。</p>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Slf4j
@Service
public class StockFlowServiceImpl
    extends DjsBaseServiceImpl<StockFlowMapper, StockFlow>
    implements IStockFlowService {

    private final LocationInfoMapper locationInfoMapper;
    private final ProductInfoMapper productInfoMapper;

    public StockFlowServiceImpl(StockFlowMapper baseMapper,
                                LocationInfoMapper locationInfoMapper,
                                ProductInfoMapper productInfoMapper) {
        super(baseMapper);
        this.locationInfoMapper = locationInfoMapper;
        this.productInfoMapper = productInfoMapper;
    }

    @Override
    public TableDataInfo<StockFlowVo> queryPageList(StockFlowQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<StockFlow> wrapper = buildWrapper(query);
        Page<StockFlowVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillJoinNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<StockFlowVo> queryList(StockFlowQuery query) {
        List<StockFlowVo> list = baseMapper.selectVoList(buildWrapper(query));
        fillJoinNames(list);
        return list;
    }

    @Override
    public StockFlowVo queryById(Long id) {
        StockFlowVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillJoinNames(List.of(vo));
        }
        return vo;
    }

    /**
     * 构造查询条件。
     *
     * <p>matType 维度走 product_info.belong_type 二次过滤：先按其他维度查 stock_flow，
     * 在内存层过滤 matType 不在 V1 规模问题（D11 WMS-FLOW-001 大表分页改 inner-JOIN）。
     * 当前为简化路径——若调用方传 matType，先按 belongType 查出符合 product 的 id 集合后
     * 作为 productId IN 条件下推。</p>
     */
    private LambdaQueryWrapper<StockFlow> buildWrapper(StockFlowQuery query) {
        LambdaQueryWrapper<StockFlow> w = new LambdaQueryWrapper<>();
        if (query == null) {
            return w.orderByDesc(StockFlow::getId);
        }
        // matType → 反查 product.id 集合
        if (StringUtils.isNotBlank(query.getMatType())) {
            List<ProductInfo> products = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>().eq(ProductInfo::getBelongType, query.getMatType()));
            if (products.isEmpty()) {
                // 无任何符合 belong_type 的产品 → 兜底空集 → 流水必空
                return w.eq(StockFlow::getId, -1L);
            }
            List<Long> productIds = products.stream().map(ProductInfo::getId).distinct().toList();
            w.in(StockFlow::getProductId, productIds);
        }
        w.eq(StringUtils.isNotBlank(query.getFlowNo()),      StockFlow::getFlowNo, query.getFlowNo())
            .eq(StringUtils.isNotBlank(query.getFlowType()),  StockFlow::getFlowType, query.getFlowType())
            .eq(StringUtils.isNotBlank(query.getInoutType()), StockFlow::getInoutType, query.getInoutType())
            .eq(StringUtils.isNotBlank(query.getStockOutDest()), StockFlow::getStockOutDest, query.getStockOutDest())
            .eq(query.getProductId() != null,    StockFlow::getProductId,   query.getProductId())
            .eq(query.getOperatorId() != null,   StockFlow::getOperatorId,  query.getOperatorId())
            .eq(query.getWarehouseId() != null,  StockFlow::getWarehouseId, query.getWarehouseId())
            .eq(StringUtils.isNotBlank(query.getEarNo()), StockFlow::getEarNo, query.getEarNo())
            .eq(query.getPlotId() != null, StockFlow::getPlotId, query.getPlotId())
            .ge(query.getDateFrom() != null, StockFlow::getFlowDate, query.getDateFrom())
            .le(query.getDateTo()   != null, StockFlow::getFlowDate, query.getDateTo())
            .orderByDesc(StockFlow::getFlowDate)
            .orderByDesc(StockFlow::getId);
        return w;
    }

    /**
     * 批量回填 productName / productCode / belongType / productUnit / locationName。
     *
     * <p>两次 IN 查询（products + locations），避免 N+1。</p>
     */
    private void fillJoinNames(List<StockFlowVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // 1. products
        List<Long> productIds = rows.stream()
            .map(StockFlowVo::getProductId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, ProductInfo> pm = productIds.isEmpty() ? Map.of() :
            productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds))
                .stream()
                .collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
        for (StockFlowVo vo : rows) {
            ProductInfo p = pm.get(vo.getProductId());
            if (p != null) {
                vo.setProductName(p.getProductName());
                vo.setProductCode(p.getProductId());  // 业务码
                vo.setBelongType(p.getBelongType());
                vo.setProductUnit(p.getProductUnit());
            }
        }
        // 2. locations
        List<Long> locationIds = rows.stream()
            .map(StockFlowVo::getWarehouseId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, String> lm = locationIds.isEmpty() ? Map.of() :
            locationInfoMapper.selectList(new LambdaQueryWrapper<LocationInfo>().in(LocationInfo::getId, locationIds))
                .stream()
                .collect(Collectors.toMap(LocationInfo::getId, LocationInfo::getLocationName, (a, b) -> a));
        for (StockFlowVo vo : rows) {
            if (vo.getWarehouseId() != null) {
                vo.setLocationName(lm.get(vo.getWarehouseId()));
            }
        }
    }

}
