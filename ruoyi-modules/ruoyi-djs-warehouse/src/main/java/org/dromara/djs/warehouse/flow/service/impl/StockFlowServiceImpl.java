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
import org.dromara.djs.warehouse.stock.domain.PlotLabel;

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

    /**
     * 打包入库流水类型（djs_flow_type）。
     * 打包是「出库到发货月台」语义，不应出现在 admin 入库记录页/入库方式下拉里；
     * 仅在入库记录两页（queryInList / queryInExport）排除展示，pack_in 流水写入保留
     * （库存余额 / 日损耗 / 盘点依赖）。
     */
    private static final String FLOW_TYPE_PACK_IN = "pack_in";

    /**
     * 出库记录页 / 导出（queryOutList / queryOutExport）展示排除的流水类型（djs_flow_type）：
     * 出库记录页仅展示库位领用 / 后台 / 盘点类真出库，生产发货（ship_out / pack_consume）与
     * 领用后损耗（loss）不展示；流水本身仍写入（库存余额 / 损耗总览依赖）。
     * ⚠️ 勿复用 / 勿改 MatFlowServiceImpl 的权威键集——那是额度统计口径，与本展示口径无关。
     */
    private static final List<String> DISPLAY_EXCLUDED_OUT_FLOW_TYPES = List.of("loss", "ship_out", "pack_consume");

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
        // 入库记录页：排除打包入库（pack_in），其余入库流水照常展示
        LambdaQueryWrapper<StockFlow> wrapper = buildWrapper(lockInout(query, INOUT_IN))
            .ne(StockFlow::getFlowType, FLOW_TYPE_PACK_IN);
        Page<StockFlowVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillJoinNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public TableDataInfo<StockFlowVo> queryOutList(StockFlowQuery query, PageQuery pageQuery) {
        // 出库记录页：排除生产发货 / 领用后损耗类流水，只保留库位领用 / 后台 / 盘点类真出库口径
        LambdaQueryWrapper<StockFlow> wrapper = buildWrapper(lockInout(query, INOUT_OUT))
            .notIn(StockFlow::getFlowType, DISPLAY_EXCLUDED_OUT_FLOW_TYPES);
        Page<StockFlowVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillJoinNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<StockFlowVo> queryInExport(StockFlowQuery query) {
        // 入库记录导出：与列表口径一致，排除打包入库（pack_in）
        LambdaQueryWrapper<StockFlow> wrapper = buildWrapper(lockInout(query, INOUT_IN))
            .ne(StockFlow::getFlowType, FLOW_TYPE_PACK_IN);
        List<StockFlowVo> list = baseMapper.selectVoList(wrapper);
        fillJoinNames(list);
        return list;
    }

    @Override
    public List<StockFlowVo> queryOutExport(StockFlowQuery query) {
        // 出库记录导出：与列表口径一致，排除生产发货 / 领用后损耗类流水
        LambdaQueryWrapper<StockFlow> wrapper = buildWrapper(lockInout(query, INOUT_OUT))
            .notIn(StockFlow::getFlowType, DISPLAY_EXCLUDED_OUT_FLOW_TYPES);
        List<StockFlowVo> list = baseMapper.selectVoList(wrapper);
        fillJoinNames(list);
        return list;
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
     * row178：退回类入库流水（成品名 → 原材料台账扩展只放行这几种）。
     *
     * <p>退回入库落在原材料名下（{@code resolveInboundProductId} 把成品折成 {@code product_material}），
     * 所以用成品名搜必须能查到原材料上的那条退回流水。但原材料台账里还有采购 / 分割产出 / 打包领用等，
     * 与「我这笔退回入没入库」无关，一并并进来会把合计放大、行也认不出是谁的。</p>
     */
    private static final List<String> RETURN_FLOW_TYPES =
        List.of("store_return_in", "return_in", "return_goods_in", "prod_return_in", "pick_return_in");

    /**
     * 产品维度谓词（{@code productName} 之外的部分），列表查询与原材料反查共用同一组条件。
     *
     * <p>抽出来是为了让注入的原材料 id 也过一遍 {@code matType / buyClass / productType}——
     * 直接把 {@code product_material} 塞进 {@code productId IN} 会绕开这些筛选（成品与材料
     * 业态/类型不一致时就串量）。</p>
     */
    private LambdaQueryWrapper<ProductInfo> productPredicates(StockFlowQuery query, boolean hasMatTypes, boolean hasProductTypes) {
        LambdaQueryWrapper<ProductInfo> pw = new LambdaQueryWrapper<>();
        return pw.in(hasMatTypes, ProductInfo::getBelongType, query.getMatTypes())
            .eq(!hasMatTypes && StringUtils.isNotBlank(query.getMatType()), ProductInfo::getBelongType, query.getMatType())
            .eq(StringUtils.isNotBlank(query.getBuyClass()), ProductInfo::getBuyClass, query.getBuyClass())
            .in(hasProductTypes, ProductInfo::getProductType, query.getProductTypes())
            .eq(!hasProductTypes && query.getProductType() != null, ProductInfo::getProductType, query.getProductType());
    }

    /**
     * row178：成品名命中产品 → 需要一并看其退回流水的原材料 id 集。
     *
     * <p>仅在按产品名搜时扩展；材料 id 必须再过一遍同组谓词（见 {@link #productPredicates}）。</p>
     *
     * @return 原材料 id（无需扩展 / 材料被谓词滤掉 → 空集）
     */
    private List<Long> resolveReturnMaterialIds(StockFlowQuery query, List<ProductInfo> products,
                                                boolean hasMatTypes, boolean hasProductTypes) {
        if (StringUtils.isBlank(query.getProductName())) {
            return List.of();
        }
        List<Long> rawIds = products.stream().map(ProductInfo::getProductMaterial)
            .filter(Objects::nonNull).distinct().toList();
        if (rawIds.isEmpty()) {
            return List.of();
        }
        return productInfoMapper.selectList(
                productPredicates(query, hasMatTypes, hasProductTypes).in(ProductInfo::getId, rawIds))
            .stream().map(ProductInfo::getId).distinct().toList();
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
        boolean hasMatTypes = query.getMatTypes() != null && !query.getMatTypes().isEmpty();
        boolean hasProductTypes = query.getProductTypes() != null && !query.getProductTypes().isEmpty();
        // matType / productName / buyClass / productType → 反查 product.id 集合（取交集下推 productId IN）
        if (hasMatTypes || StringUtils.isNotBlank(query.getMatType())
            || StringUtils.isNotBlank(query.getProductName())
            || StringUtils.isNotBlank(query.getBuyClass())
            || hasProductTypes || query.getProductType() != null) {
            LambdaQueryWrapper<ProductInfo> pw = productPredicates(query, hasMatTypes, hasProductTypes)
                .like(StringUtils.isNotBlank(query.getProductName()), ProductInfo::getProductName, query.getProductName());
            List<ProductInfo> products = productInfoMapper.selectList(pw);
            if (products.isEmpty()) {
                // 无任何符合条件的产品 → 兜底空集 → 流水必空
                return w.eq(StockFlow::getId, -1L);
            }
            List<Long> ownIds = products.stream().map(ProductInfo::getId).distinct().toList();
            List<Long> materialIds = resolveReturnMaterialIds(query, products, hasMatTypes, hasProductTypes);
            if (materialIds.isEmpty()) {
                w.in(StockFlow::getProductId, ownIds);
            } else {
                // 成品名命中的产品自身流水，OR 其原材料上「这一笔退回」的流水（下面 appendReturnBranch 说明范围）
                w.and(outer -> outer.in(StockFlow::getProductId, ownIds)
                    .or(branch -> branch.in(StockFlow::getProductId, materialIds)
                        .in(StockFlow::getFlowType, RETURN_FLOW_TYPES)
                        .like(StockFlow::getRemark, query.getProductName())));
            }
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
        boolean hasFlowTypes = query.getFlowTypes() != null && !query.getFlowTypes().isEmpty();
        boolean hasStockOutDests = query.getStockOutDests() != null && !query.getStockOutDests().isEmpty();
        boolean hasWarehouseIds = query.getWarehouseIds() != null && !query.getWarehouseIds().isEmpty();
        w.eq(StringUtils.isNotBlank(query.getFlowNo()),      StockFlow::getFlowNo, query.getFlowNo())
            .in(hasFlowTypes, StockFlow::getFlowType, query.getFlowTypes())
            .eq(!hasFlowTypes && StringUtils.isNotBlank(query.getFlowType()),  StockFlow::getFlowType, query.getFlowType())
            .eq(StringUtils.isNotBlank(query.getInoutType()), StockFlow::getInoutType, query.getInoutType())
            .in(hasStockOutDests, StockFlow::getStockOutDest, query.getStockOutDests())
            .eq(!hasStockOutDests && StringUtils.isNotBlank(query.getStockOutDest()), StockFlow::getStockOutDest, query.getStockOutDest())
            .eq(query.getProductId() != null,    StockFlow::getProductId,   query.getProductId())
            .eq(query.getOperatorId() != null,   StockFlow::getOperatorId,  query.getOperatorId())
            .in(hasWarehouseIds, StockFlow::getWarehouseId, query.getWarehouseIds())
            .eq(!hasWarehouseIds && query.getWarehouseId() != null,  StockFlow::getWarehouseId, query.getWarehouseId())
            .like(StringUtils.isNotBlank(query.getEarNo()), StockFlow::getEarNo, query.getEarNo())
            .eq(query.getPlotId() != null, StockFlow::getPlotId, query.getPlotId())
            // 三期过滤（V6 row92）：传 1 = 只看三期；不传 = 全部
            .eq(query.getThirdPhase() != null, StockFlow::getThirdPhase, query.getThirdPhase())
            .ge(query.getDateFrom() != null, StockFlow::getFlowDate, query.getDateFrom())
            .le(query.getDateTo()   != null, StockFlow::getFlowDate, query.getDateTo())
            .orderByDesc(StockFlow::getFlowDate)
            .orderByDesc(StockFlow::getId);
        return w;
    }

    /**
     * 批量回填 productName / productCode / belongType / productUnit / buyClass / locationName /
     * blockNo / plotName。
     *
     * <p>三次 IN 查询（products + locations + plots），避免 N+1。</p>
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
                vo.setProductType(p.getProductType());  // 产品类型（自产/外购，与 belongType 不同维度；礼盒 = 自产 + belongType=gift_box）
                vo.setProductName(p.getProductName());
                vo.setProductCode(p.getProductId());  // 业务码
                vo.setBelongType(p.getBelongType());
                vo.setProductUnit(p.getProductUnit());
                vo.setBuyClass(p.getBuyClass());      // 商品分类（mp 领用记录卡展示 + 筛选）
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
        // 3. plots（地块编号 = plot_code / 地块名 = plot_name；plotId 为空的行两者都保持 null）
        //    三期货没有真实地块（thirdPhase=1 的行 plotId 恒 null），「地块」列由前端按
        //    thirdPhase → plotName → '-' 的顺序渲染，后端只负责把两个原料都给全。
        List<Long> plotIds = rows.stream()
            .map(StockFlowVo::getPlotId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, PlotInfo> plm = plotIds.isEmpty() ? Map.of() :
            plotInfoMapper.selectList(new LambdaQueryWrapper<PlotInfo>().in(PlotInfo::getId, plotIds))
                .stream()
                .collect(Collectors.toMap(PlotInfo::getId, p -> p, (a, b) -> a));
        for (StockFlowVo vo : rows) {
            if (vo.getPlotId() != null) {
                PlotInfo plot = plm.get(vo.getPlotId());
                if (plot != null) {
                    vo.setBlockNo(plot.getPlotCode());
                    vo.setPlotName(plot.getPlotName());
                }
            }
            // 导出件的「地块」列（V6 row92）：与页面 formatPlotLabel 同一套规则。
            // 必须在 plotName 落完之后算 —— 三期行直接返「三期」，不看 plotName。
            vo.setPlotLabel(PlotLabel.of(vo.getThirdPhase(), vo.getPlotName()));
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
