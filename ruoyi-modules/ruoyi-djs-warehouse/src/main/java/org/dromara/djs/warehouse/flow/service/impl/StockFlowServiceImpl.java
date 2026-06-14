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
import org.dromara.djs.warehouse.flow.domain.vo.PackingHomeVo;
import org.dromara.djs.warehouse.flow.domain.vo.PackingItemVo;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.flow.service.IStockFlowService;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    /**
     * 出入库方向：IN=入库 / OT=出库（DDL CHAR(3)）。
     */
    private static final String INOUT_IN = "IN";
    private static final String INOUT_OUT = "OT";

    /**
     * 包材归属（djs_belong_type，D9 WMS-MAT-001 已 seed，本 ticket 复用）。
     */
    private static final String BELONG_TYPE_PACKAGE = "package";

    private final LocationInfoMapper locationInfoMapper;
    private final ProductInfoMapper productInfoMapper;
    private final LocationStockMapper locationStockMapper;
    private final PlotInfoMapper plotInfoMapper;

    public StockFlowServiceImpl(StockFlowMapper baseMapper,
                                LocationInfoMapper locationInfoMapper,
                                ProductInfoMapper productInfoMapper,
                                LocationStockMapper locationStockMapper,
                                PlotInfoMapper plotInfoMapper) {
        super(baseMapper);
        this.locationInfoMapper = locationInfoMapper;
        this.productInfoMapper = productInfoMapper;
        this.locationStockMapper = locationStockMapper;
        this.plotInfoMapper = plotInfoMapper;
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

    @Override
    public TableDataInfo<StockFlowVo> queryInList(StockFlowQuery query, PageQuery pageQuery) {
        return queryPageList(lockInout(query, INOUT_IN), pageQuery);
    }

    @Override
    public TableDataInfo<StockFlowVo> queryOutList(StockFlowQuery query, PageQuery pageQuery) {
        return queryPageList(lockInout(query, INOUT_OUT), pageQuery);
    }

    @Override
    public List<StockFlowVo> queryInExport(StockFlowQuery query) {
        return queryList(lockInout(query, INOUT_IN));
    }

    @Override
    public List<StockFlowVo> queryOutExport(StockFlowQuery query) {
        return queryList(lockInout(query, INOUT_OUT));
    }

    @Override
    public PackingHomeVo queryPackingHome() {
        PackingHomeVo vo = new PackingHomeVo();
        vo.setTodayInQuantity(safe(baseMapper.sumTodayByInoutBelongType(INOUT_IN, BELONG_TYPE_PACKAGE)));
        vo.setTodayOutQuantity(safe(baseMapper.sumTodayByInoutBelongType(INOUT_OUT, BELONG_TYPE_PACKAGE)));
        Long typeCount = locationStockMapper.countProductsByBelongType(BELONG_TYPE_PACKAGE);
        vo.setPackTypeCount(typeCount == null ? 0L : typeCount);
        vo.setLatestCheckTime(locationStockMapper.selectLatestCheckTimeByBelongType(BELONG_TYPE_PACKAGE));
        return vo;
    }

    @Override
    public List<PackingItemVo> queryPackingList(String sortBy) {
        // belong_type='package' 在 mapper 层强制 eq（契约 14：不在前端 filter）
        return locationStockMapper.selectPackingItems(BELONG_TYPE_PACKAGE, sortBy);
    }

    @Override
    public TableDataInfo<StockFlowVo> queryPackingDetail(Long productId, PageQuery pageQuery) {
        StockFlowQuery query = new StockFlowQuery();
        query.setProductId(productId);
        return queryPageList(query, pageQuery);
    }

    /**
     * 锁定出入方向（admin 入库 / 出库两页强制 inout_type，防越界查到反方向流水）。
     *
     * <p>不可变副作用最小化：直接 set 到入参 query（admin 端 query 即用即弃）。</p>
     */
    private StockFlowQuery lockInout(StockFlowQuery query, String inoutType) {
        StockFlowQuery q = query == null ? new StockFlowQuery() : query;
        q.setInoutType(inoutType);
        return q;
    }

    private static BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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
        // matType / productName → 反查 product.id 集合（两者取交集下推 productId IN）
        if (StringUtils.isNotBlank(query.getMatType()) || StringUtils.isNotBlank(query.getProductName())) {
            LambdaQueryWrapper<ProductInfo> pw = new LambdaQueryWrapper<>();
            pw.eq(StringUtils.isNotBlank(query.getMatType()), ProductInfo::getBelongType, query.getMatType())
                .like(StringUtils.isNotBlank(query.getProductName()), ProductInfo::getProductName, query.getProductName());
            List<ProductInfo> products = productInfoMapper.selectList(pw);
            if (products.isEmpty()) {
                // 无任何符合条件的产品 → 兜底空集 → 流水必空
                return w.eq(StockFlow::getId, -1L);
            }
            List<Long> productIds = products.stream().map(ProductInfo::getId).distinct().toList();
            w.in(StockFlow::getProductId, productIds);
        }
        // blockNo → 反查 plot.id 集合下推 plotId IN
        if (StringUtils.isNotBlank(query.getBlockNo())) {
            List<Long> plotIds = resolvePlotIdsByBlockNo(query.getBlockNo());
            if (plotIds.isEmpty()) {
                return w.eq(StockFlow::getId, -1L);
            }
            w.in(StockFlow::getPlotId, plotIds);
        }
        // operatorName → 反查 sys_user.user_id 集合下推 operatorId IN
        if (StringUtils.isNotBlank(query.getOperatorName())) {
            List<Long> userIds = baseMapper.selectUserIdsByNickName(query.getOperatorName());
            if (userIds.isEmpty()) {
                return w.eq(StockFlow::getId, -1L);
            }
            w.in(StockFlow::getOperatorId, userIds);
        }
        w.eq(StringUtils.isNotBlank(query.getFlowNo()),      StockFlow::getFlowNo, query.getFlowNo())
            .eq(StringUtils.isNotBlank(query.getFlowType()),  StockFlow::getFlowType, query.getFlowType())
            .eq(StringUtils.isNotBlank(query.getInoutType()), StockFlow::getInoutType, query.getInoutType())
            .eq(StringUtils.isNotBlank(query.getStockOutDest()), StockFlow::getStockOutDest, query.getStockOutDest())
            .eq(query.getProductId() != null,    StockFlow::getProductId,   query.getProductId())
            .eq(query.getOperatorId() != null,   StockFlow::getOperatorId,  query.getOperatorId())
            .eq(query.getWarehouseId() != null,  StockFlow::getWarehouseId, query.getWarehouseId())
            .like(StringUtils.isNotBlank(query.getEarNo()), StockFlow::getEarNo, query.getEarNo())
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
        // 3. plots（地块编号 = t_plant_plot_info.plot_code；plotId 为空的行 blockNo 保持 null）
        List<Long> plotIds = rows.stream()
            .map(StockFlowVo::getPlotId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, String> plm = plotIds.isEmpty() ? Map.of() :
            plotInfoMapper.selectList(new LambdaQueryWrapper<PlotInfo>().in(PlotInfo::getId, plotIds))
                .stream()
                .filter(p -> p.getPlotCode() != null)
                .collect(Collectors.toMap(PlotInfo::getId, PlotInfo::getPlotCode, (a, b) -> a));
        for (StockFlowVo vo : rows) {
            if (vo.getPlotId() != null) {
                vo.setBlockNo(plm.get(vo.getPlotId()));
            }
        }
    }

    /**
     * 按地块编号（模糊）解析匹配的 plotId 集合；无匹配返空 list（调用方据此让查询恒空）。
     */
    private List<Long> resolvePlotIdsByBlockNo(String blockNo) {
        List<PlotInfo> plots = plotInfoMapper.selectList(
            new LambdaQueryWrapper<PlotInfo>()
                .like(PlotInfo::getPlotCode, blockNo)
                .select(PlotInfo::getId));
        return new ArrayList<>(plots.stream().map(PlotInfo::getId).toList());
    }

}
