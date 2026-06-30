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
     * 产品出库流水类型：{@code backstage_out}（后台手工出库）。
     *
     * <p>出库记录页 {@code flow/out} 直接按 {@code djs_flow_type} 字典渲染 flow_type，
     * 字典值 {@code backstage_out} = 「后台出库」（seed 102450019）。与商品详情业务流水
     * {@link ProductInfoMapper#selectFlowRecords} 把非 pick_out 的出库派生为 {@code backend_out}
     * 文案口径一致（均为「后台出库」语义）。</p>
     */
    private static final String FLOW_BACKSTAGE_OUT = "backstage_out";

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
        validateDimensionExclusive(bo);
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
        // row186-BE：出库量不得超过当前库存（前端软拦 + 此处后端 fail-fast 硬拦，写流水前拦截防绕过/并发超扣；
        // 与 step3 的 deductByProductLocation 行锁原子校验互为内外两道闸）。
        BigDecimal currentStock = stock.getProductStock() == null ? BigDecimal.ZERO : stock.getProductStock();
        if (bo.getQuantity().compareTo(currentStock) > 0) {
            throw new ServiceException(
                "出库量超过当前库存（product=" + product.getProductName()
                    + " / 当前库存=" + currentStock.stripTrailingZeros().toPlainString() + product.getProductUnit()
                    + " / 申请=" + bo.getQuantity().stripTrailingZeros().toPlainString() + product.getProductUnit() + "）");
        }
        Long userId = LoginHelper.getUserId();

        // 2. INSERT 出库流水（flow_type=backstage_out → 出库记录显示「后台出库」）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_OUT));
        // flow_date 记录实际操作时刻（含时分秒）；bo.outDate 仅是用户选的业务日期、为纯日期会落 00:00:00
        flow.setFlowDate(new Date());
        flow.setProductId(productId);
        flow.setWarehouseId(locationId);
        flow.setInoutType(INOUT_OUT);
        flow.setFlowType(FLOW_BACKSTAGE_OUT);
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

    @Override
    public List<String> listStockEarNos(Long locationId) {
        List<LocationStock> rows = baseMapper.selectList(
            new LambdaQueryWrapper<LocationStock>()
                .select(LocationStock::getEarNo)
                .eq(locationId != null, LocationStock::getLocationId, locationId)
                .isNotNull(LocationStock::getEarNo)
                .ne(LocationStock::getEarNo, ""));
        return rows.stream()
            .map(LocationStock::getEarNo)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .sorted()
            .toList();
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
     * 校验 productId / earNo / plotId / medicineId 四选一（非空字段恰好一个）。
     */
    private void validateDimensionExclusive(LocationStockBo bo) {
        int filled = 0;
        if (bo.getProductId() != null) filled++;
        if (StringUtils.isNotBlank(bo.getEarNo())) filled++;
        if (bo.getPlotId() != null) filled++;
        if (bo.getMedicineId() != null) filled++;
        if (filled != 1) {
            throw new ServiceException("stock.dimension.exclusive");
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
     * 批量回填 {@code productCode} + {@code belongType} + {@code productAttr}
     * （均取自产品主数据 {@code t_warehouse_product_info}）。
     *
     * <p>库存表只存 {@code product_id} FK（Long），产品代码/业态/属性在产品主数据表。
     * 单次 IN 查产品表回填，避免 N+1。库存行 {@code productId} 为空（按耳号 / 地块入库的行）→ 三者保持 null。
     * {@code belongType + productAttr} 供库存详情「饲料饲喂记录」tab 判定（果蔬原材料才显示）。</p>
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
        Map<Long, ProductInfo> productMap = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds))
            .stream()
            .collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
        for (LocationStockVo vo : records) {
            if (vo.getProductId() != null) {
                ProductInfo product = productMap.get(vo.getProductId());
                if (product != null) {
                    vo.setProductCode(product.getProductId());
                    vo.setBelongType(product.getBelongType());
                    vo.setProductAttr(product.getProductAttr());
                }
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
     * 按归属类型（{@code djs_belong_type} 字典值，精确）解析匹配的 productId 集合；无匹配返空 list
     * （调用方据此让查询恒空）。
     *
     * <p>{@code belong_type} 不在 {@code t_warehouse_location_stock} 表，仅存于产品主数据
     * {@code t_warehouse_product_info}，故先按 belong_type 查出 productId 再 IN 过滤库存
     * （参 {@link #fillProductCodes} 的 productInfoMapper 用法）。</p>
     */
    private List<Long> resolveProductIdsByBelongType(String belongType) {
        List<ProductInfo> products = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getBelongType, belongType)
                .select(ProductInfo::getId));
        return products.stream().map(ProductInfo::getId).toList();
    }

    /**
     * 按归属类型多选（{@code djs_belong_type} 字典值集合，IN）解析匹配的 productId 集合；无匹配返空 list
     * （R70 产品品类多选，调用方据此让查询恒空）。
     */
    private List<Long> resolveProductIdsByBelongTypes(List<String> belongTypes) {
        List<ProductInfo> products = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>()
                .in(ProductInfo::getBelongType, belongTypes)
                .select(ProductInfo::getId));
        return products.stream().map(ProductInfo::getId).toList();
    }

    /**
     * 构造查询条件。
     */
    private LambdaQueryWrapper<LocationStock> buildQueryWrapper(LocationStockQuery query) {
        LambdaQueryWrapper<LocationStock> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(LocationStock::getId);
        }
        // 库位多选（R70）：locationIds 非空 → IN；否则 fallback 单值 locationId .eq（mp / 其他单值调用方）
        boolean hasLocationIds = query.getLocationIds() != null && !query.getLocationIds().isEmpty();
        wrapper.in(hasLocationIds, LocationStock::getLocationId, query.getLocationIds())
            .eq(!hasLocationIds && query.getLocationId() != null, LocationStock::getLocationId, query.getLocationId())
            .eq(query.getProductId() != null, LocationStock::getProductId, query.getProductId())
            .like(StringUtils.isNotBlank(query.getProductName()), LocationStock::getProductName, query.getProductName())
            .like(StringUtils.isNotBlank(query.getEarNo()), LocationStock::getEarNo, query.getEarNo())
            .eq(query.getPlotId() != null, LocationStock::getPlotId, query.getPlotId())
            .eq(query.getMedicineId() != null, LocationStock::getMedicineId, query.getMedicineId())
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
        // 归属类型过滤：belong_type 不在库存表，先按产品主数据 belong_type 解析 productId 集合再 IN 过滤；无匹配则恒空。
        // R70 多选：belongTypes 非空 → 按集合 IN 反查；否则 fallback 单值 belongType（mp / 其他单值调用方）。
        boolean hasBelongTypes = query.getBelongTypes() != null && !query.getBelongTypes().isEmpty();
        if (hasBelongTypes) {
            List<Long> productIds = resolveProductIdsByBelongTypes(query.getBelongTypes());
            if (productIds.isEmpty()) {
                wrapper.eq(LocationStock::getId, -1L);
            } else {
                wrapper.in(LocationStock::getProductId, productIds);
            }
        } else if (StringUtils.isNotBlank(query.getBelongType())) {
            List<Long> productIds = resolveProductIdsByBelongType(query.getBelongType());
            if (productIds.isEmpty()) {
                wrapper.eq(LocationStock::getId, -1L);
            } else {
                wrapper.in(LocationStock::getProductId, productIds);
            }
        }
        return wrapper;
    }

}
