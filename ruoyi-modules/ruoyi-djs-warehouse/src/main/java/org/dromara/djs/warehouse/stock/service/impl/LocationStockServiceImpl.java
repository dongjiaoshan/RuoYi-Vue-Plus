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
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.bo.LocationStockBo;
import org.dromara.djs.warehouse.stock.domain.bo.StockOutBo;
import org.dromara.djs.warehouse.stock.domain.query.LocationStockQuery;
import org.dromara.djs.warehouse.stock.domain.vo.LocationStockVo;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.stock.service.ILocationStockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
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

    /**
     * 产品出库流水类型：{@code other}（非 pick_out → 列表派生「后台出库」backend_out，
     * 与商品详情业务流水口径一致，参 {@link ProductInfoMapper#selectFlowRecords}）。
     */
    private static final String FLOW_OTHER = "other";

    /**
     * 出库方向（DDL CHAR(3)）。
     */
    private static final String INOUT_OUT = "OT";

    private final LocationInfoMapper locationInfoMapper;
    private final PlotInfoMapper plotInfoMapper;
    private final ProductInfoMapper productInfoMapper;
    private final StockFlowMapper stockFlowMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IStockCheckService stockCheckService;

    public LocationStockServiceImpl(LocationStockMapper baseMapper,
                                    LocationInfoMapper locationInfoMapper,
                                    PlotInfoMapper plotInfoMapper,
                                    ProductInfoMapper productInfoMapper,
                                    StockFlowMapper stockFlowMapper,
                                    IBizCodeGenerator bizCodeGenerator,
                                    IStockCheckService stockCheckService) {
        super(baseMapper);
        this.locationInfoMapper = locationInfoMapper;
        this.plotInfoMapper = plotInfoMapper;
        this.productInfoMapper = productInfoMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.stockCheckService = stockCheckService;
    }

    @Override
    public TableDataInfo<LocationStockVo> queryPageList(LocationStockQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<LocationStock> wrapper = buildQueryWrapper(query);
        Page<LocationStockVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillLocationNames(page.getRecords());
        fillBlockNos(page.getRecords());
        fillProductCodes(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<LocationStockVo> queryList(LocationStockQuery query) {
        List<LocationStockVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillLocationNames(list);
        fillBlockNos(list);
        fillProductCodes(list);
        return list;
    }

    @Override
    public LocationStockVo queryById(Long id) {
        LocationStockVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillLocationNames(List.of(vo));
            fillBlockNos(List.of(vo));
            fillProductCodes(List.of(vo));
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long productOut(StockOutBo bo) {
        // 1. 取库存行，解析 locationId + productId（按行出库，避免前端透传可篡改的 location/product）
        LocationStock stock = baseMapper.selectById(bo.getId());
        if (stock == null) {
            throw new ServiceException("库存记录不存在或已删除：" + bo.getId());
        }
        Long productId = stock.getProductId();
        Long locationId = stock.getLocationId();
        if (productId == null || locationId == null) {
            throw new ServiceException("该库存行非产品库存（缺产品 / 库位），不支持产品出库");
        }
        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null) {
            throw new ServiceException("产品不存在或已删除：" + productId);
        }
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库（后端双保险）
        stockCheckService.assertLocationUnlocked(locationId);
        Long userId = LoginHelper.getUserId();

        // 2. INSERT 出库流水（flow_type=other → 列表派生「后台出库」）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_OUT));
        flow.setFlowDate(bo.getOutDate() != null ? bo.getOutDate() : new Date());
        flow.setProductId(productId);
        flow.setWarehouseId(locationId);
        flow.setInoutType(INOUT_OUT);
        flow.setFlowType(FLOW_OTHER);
        flow.setStockOutDest(bo.getStockOutDest());
        flow.setChangeNum(bo.getQuantity().negate());
        flow.setChangeQuantity(bo.getQuantity());
        flow.setOperatorId(userId);
        flow.setRemark(bo.getRemark());
        stockFlowMapper.insert(flow);

        // 3. UPDATE location_stock 原子扣减（product_stock >= quantity 行锁 + 数量校验）
        int affected = locationStockMapper().deductByProductLocation(locationId, productId, bo.getQuantity(), userId);
        if (affected == 0) {
            // 抛异常 → @Transactional 回滚 step 2
            throw new ServiceException(
                "库存不足，无法出库（product=" + product.getProductName()
                    + " / 当前库存=" + stock.getProductStock() + product.getProductUnit()
                    + " / 申请=" + bo.getQuantity() + product.getProductUnit() + "）");
        }
        return flow.getId();
    }

    /**
     * 取库存 Mapper（基类 {@code baseMapper} 即 {@link LocationStockMapper}，封装一层便于读）。
     */
    private LocationStockMapper locationStockMapper() {
        return baseMapper;
    }

    /**
     * 生成流水号（复用 SYS-INFRA-004 BizCodeService，{@code F+yyyyMMdd+ioCode2+seq4}）。
     */
    private String generateFlowNo(String ioCode) {
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", ioCode);
        return bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx);
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
     * 批量回填 {@code blockNo}（地块编号 = {@code t_plant_plot_info.plot_code}）。
     *
     * <p>库存表只存 {@code plotId}，地块编号在地块主数据表。单次 IN 查地块表回填，避免 N+1。
     * 库存行 {@code plotId} 为空（按产品 / 耳号入库的行）→ blockNo 保持 null。</p>
     */
    private void fillBlockNos(List<LocationStockVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> plotIds = records.stream()
            .map(LocationStockVo::getPlotId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (plotIds.isEmpty()) {
            return;
        }
        List<PlotInfo> plots = plotInfoMapper.selectList(
            new LambdaQueryWrapper<PlotInfo>().in(PlotInfo::getId, plotIds));
        Map<Long, String> codeMap = plots.stream()
            .filter(p -> p.getPlotCode() != null)
            .collect(Collectors.toMap(PlotInfo::getId, PlotInfo::getPlotCode, (a, b) -> a));
        for (LocationStockVo vo : records) {
            if (vo.getPlotId() != null) {
                vo.setBlockNo(codeMap.get(vo.getPlotId()));
            }
        }
    }

    /**
     * 批量回填 {@code productCode}（产品代码 = {@code ProductInfo.productId} 业务码，如 P10002）。
     *
     * <p>库存表只存 {@code product_id} FK（Long），产品代码在产品主数据表的业务码列。
     * 单次 IN 查产品表回填，避免 N+1。库存行 {@code productId} 为空（按耳号 / 地块入库的行）→ productCode 保持 null。</p>
     */
    private void fillProductCodes(List<LocationStockVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> productIds = records.stream()
            .map(LocationStockVo::getProductId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (productIds.isEmpty()) {
            return;
        }
        Map<Long, String> codeMap = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds))
            .stream()
            .filter(p -> p.getProductId() != null)
            .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getProductId, (a, b) -> a));
        for (LocationStockVo vo : records) {
            if (vo.getProductId() != null) {
                vo.setProductCode(codeMap.get(vo.getProductId()));
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
        return plots.stream().map(PlotInfo::getId).toList();
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
            .like(StringUtils.isNotBlank(query.getEarNo()), LocationStock::getEarNo, query.getEarNo())
            .eq(query.getPlotId() != null, LocationStock::getPlotId, query.getPlotId())
            .eq(query.getIsEnd() != null, LocationStock::getIsEnd, query.getIsEnd())
            .orderByDesc(LocationStock::getId);
        // 地块编号过滤：先解析匹配的 plotId 集合再 IN 过滤；无匹配则用不存在的 id 让结果恒空
        if (StringUtils.isNotBlank(query.getBlockNo())) {
            List<Long> plotIds = resolvePlotIdsByBlockNo(query.getBlockNo());
            if (plotIds.isEmpty()) {
                wrapper.eq(LocationStock::getId, -1L);
            } else {
                wrapper.in(LocationStock::getPlotId, plotIds);
            }
        }
        return wrapper;
    }

}
