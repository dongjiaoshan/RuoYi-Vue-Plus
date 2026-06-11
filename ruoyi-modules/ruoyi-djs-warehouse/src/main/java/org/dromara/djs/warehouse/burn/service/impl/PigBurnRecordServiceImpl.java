package org.dromara.djs.warehouse.burn.service.impl;

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
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.burn.domain.PigBurnRecord;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnRecordBo;
import org.dromara.djs.warehouse.burn.domain.query.PigBurnRecordQuery;
import org.dromara.djs.warehouse.burn.domain.vo.BarPendingVo;
import org.dromara.djs.warehouse.burn.domain.vo.BurnProductTypeVo;
import org.dromara.djs.warehouse.burn.domain.vo.PigBurnRecordVo;
import org.dromara.djs.warehouse.burn.mapper.PigBurnRecordMapper;
import org.dromara.djs.warehouse.burn.service.IPigBurnRecordService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.trace.domain.TraceContentConst;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 燎毛入库 Service 实现（D12X-MP-BURN-IA-001：燎毛间 IA 重做）。
 *
 * <h3>IA 重做要点</h3>
 * <p>原实现是单表单（PigPicker 选猪 + 录总重 + 走 location_stock 扣减出库）。本 ticket 重做为
 * list→detail：mp 列表页展示「已出栏待燎毛入库白条」（bar_info status IN
 * ('pending_singe','singing')）→ 点猪进入库子页 → 白条按产品类型（整只 / 半只 / 猪头 / 猪蹄）
 * 分别入库。语义由「扣减出库」纠正为「入库」。</p>
 *
 * <h3>跨表事务一致性（核心风险）</h3>
 * <ul>
 *   <li>{@link #submitBurnRecord} 单 {@code @Transactional}：
 *       校验 bar 待燎毛 → INSERT burn_record → for each 类型 INSERT product_inhouse + IN 流水
 *       → UPDATE bar_info status→in_stock（乐观锁回填 in_weight/in_time/in_method=1）。
 *       任一 RuntimeException / ServiceException 触发整体回滚。</li>
 *   <li>幂等键：{@code burn_id} UNIQUE (tenant_id, burn_id, del_unique)，BizCodeGenerator Redisson 锁兜底。</li>
 *   <li>bar 推进用乐观锁 {@code WHERE status IN ('pending_singe','singing')}；并发提交（同 bar
 *       两次燎毛）只有一次 affectedRows>0，另一次抛"白条状态不符"回滚。</li>
 * </ul>
 *
 * @author djs
 * @since D12X-MP-BURN-IA-001
 */
@Slf4j
@Service
public class PigBurnRecordServiceImpl
    extends DjsBaseServiceImpl<PigBurnRecordMapper, PigBurnRecord>
    implements IPigBurnRecordService {

    /**
     * 字典 djs_burn_status 值：已完成（已入库）。
     */
    private static final String STATUS_DONE = "done";

    /**
     * 入库子类型：屠宰燎毛（{@code t_warehouse_stock_flow.flow_type}）。
     */
    private static final String FLOW_TYPE_SLAUGHTER_BURN = "slaughter_burn";

    /**
     * 出入库方向：入库（{@code t_warehouse_stock_flow.inout_type} CHAR(3)）。
     */
    private static final String INOUT_IN = "IN";

    /**
     * 白条标准产品类型业务码前缀（{@code t_warehouse_product_info.product_id}）。
     * 入库产品类型 list = 该前缀 + belong_type='white_bar'，排除测试数据。
     */
    private static final String WHITE_BAR_BELONG_TYPE = "white_bar";
    private static final String WHITE_BAR_CODE_PREFIX = "PROD-WHITE-BAR-";

    /**
     * 白条状态码（{@code t_warehouse_bar_info.status}）。
     */
    private static final String BAR_STATUS_PENDING_SINGE = "pending_singe";
    private static final String BAR_STATUS_SINGING = "singing";
    private static final String BAR_STATUS_IN_STOCK = "in_stock";

    private final StockFlowMapper stockFlowMapper;
    private final BarInfoMapper barInfoMapper;
    private final ProductInhouseMapper productInhouseMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final ProductInfoMapper productInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IStockCheckService stockCheckService;
    private final ITraceService traceService;
    private final ImageUrlResolver imageUrlResolver;

    public PigBurnRecordServiceImpl(PigBurnRecordMapper baseMapper,
                                    StockFlowMapper stockFlowMapper,
                                    BarInfoMapper barInfoMapper,
                                    ProductInhouseMapper productInhouseMapper,
                                    LocationInfoMapper locationInfoMapper,
                                    ProductInfoMapper productInfoMapper,
                                    IBizCodeGenerator bizCodeGenerator,
                                    IStockCheckService stockCheckService,
                                    ITraceService traceService,
                                    ImageUrlResolver imageUrlResolver) {
        super(baseMapper);
        this.stockFlowMapper = stockFlowMapper;
        this.barInfoMapper = barInfoMapper;
        this.productInhouseMapper = productInhouseMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.productInfoMapper = productInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.stockCheckService = stockCheckService;
        this.traceService = traceService;
        this.imageUrlResolver = imageUrlResolver;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitBurnRecord(PigBurnRecordBo bo) {
        // ---------- Step 1：校验待燎毛白条 ----------
        BarInfo bar = barInfoMapper.selectById(bo.getBarInfoId());
        if (bar == null) {
            throw new ServiceException("白条不存在：" + bo.getBarInfoId());
        }
        if (!BAR_STATUS_PENDING_SINGE.equals(bar.getStatus())
            && !BAR_STATUS_SINGING.equals(bar.getStatus())) {
            throw new ServiceException("白条状态不符（当前：" + bar.getStatus() + "，需待燎毛/燎毛中）");
        }
        String earNo = bar.getEarNo();

        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库（后端双保险）
        stockCheckService.assertLocationUnlocked(bo.getLocationId());

        // 校验入库库位存在 + 各产品类型为标准白条类型，并计算入库重量合计
        LocationInfo location = locationInfoMapper.selectById(bo.getLocationId());
        if (location == null) {
            throw new ServiceException("入冻品库位不存在：" + bo.getLocationId());
        }
        Map<Long, ProductInfo> typeMap = loadWhiteBarTypeMap();
        BigDecimal inWeightTotal = BigDecimal.ZERO;
        for (PigBurnRecordBo.ProductTypeItem item : bo.getProductTypeItems()) {
            if (!typeMap.containsKey(item.getProductId())) {
                throw new ServiceException("无效的白条产品类型：" + item.getProductId());
            }
            inWeightTotal = inWeightTotal.add(item.getWeight());
        }

        // ---------- Step 2：生成 burn_id + 计算 loss + INSERT 燎毛记录 ----------
        BigDecimal arriveWeight = bo.getArriveWeight();
        BigDecimal lossWeight = null;
        if (arriveWeight != null) {
            lossWeight = arriveWeight.subtract(inWeightTotal);
            if (lossWeight.compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("入库重量合计不能大于到场重量");
            }
        }

        PigBurnRecord record = toEntity(bo);
        if (record == null) {
            throw new ServiceException("燎毛记录入参转换失败");
        }
        record.setEarNo(earNo);
        record.setBurnId(generateBurnId());
        record.setBurnWeight(inWeightTotal);
        record.setLossWeight(lossWeight);
        record.setBurnStatus(STATUS_DONE);
        // 入库人由 EmployeePicker 指定（可与登录态不同），非 LoginHelper
        record.setOperatorId(bo.getOperatorId());
        baseMapper.insert(record);

        // ---------- Step 3：for each 产品类型 → INSERT product_inhouse + INSERT 入库流水 ----------
        Date burnTime = bo.getBurnTime();
        LocalDate today = LocalDate.now();
        for (PigBurnRecordBo.ProductTypeItem item : bo.getProductTypeItems()) {
            ProductInfo type = typeMap.get(item.getProductId());

            ProductInhouse inhouse = new ProductInhouse();
            inhouse.setProduceDate(java.sql.Date.valueOf(today));
            inhouse.setProductId(type.getId());
            inhouse.setProductName(type.getProductName());
            inhouse.setProductType(type.getProductType() == null ? 1 : type.getProductType());
            inhouse.setProductUnit(StringUtils.isNotBlank(type.getProductUnit()) ? type.getProductUnit() : "kg");
            inhouse.setEarNo(earNo);
            inhouse.setProductWeight(item.getWeight());
            inhouse.setProduceTime(burnTime);
            inhouse.setWhiteBarId(bar.getId());
            inhouse.setLocationId(bo.getLocationId());
            productInhouseMapper.insert(inhouse);

            StockFlow flowIn = new StockFlow();
            Map<String, Object> flowCtx = new HashMap<>(2);
            flowCtx.put("ioCode", INOUT_IN);
            flowIn.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, flowCtx));
            flowIn.setFlowDate(burnTime);
            flowIn.setProductId(type.getId());
            flowIn.setWarehouseId(bo.getLocationId());
            flowIn.setInoutType(INOUT_IN);
            flowIn.setFlowType(FLOW_TYPE_SLAUGHTER_BURN);
            flowIn.setChangeNum(item.getWeight());
            flowIn.setChangeQuantity(item.getWeight());
            flowIn.setEarNo(earNo);
            flowIn.setOperatorId(bo.getOperatorId());
            flowIn.setRemark("燎毛入库 burn_id=" + record.getBurnId() + " type=" + type.getProductId());
            stockFlowMapper.insert(flowIn);
        }

        // ---------- Step 4：UPDATE bar_info status → in_stock（乐观锁） ----------
        int affected = barInfoMapper.updateStatusToInStock(
            bar.getId(), inWeightTotal, burnTime, bo.getOperatorId());
        if (affected == 0) {
            throw new ServiceException("白条状态不符（已入库或不在待燎毛态），请刷新列表");
        }

        // TRC-CORE-001：燎毛追溯事件（按耳号反查 trace_code；猪肉链当前无生成入口 → warn 跳过，不拖垮燎毛事务）
        traceService.recordEventByEarNo(earNo, TraceContentConst.SINGE);

        return record.getId();
    }

    @Override
    public List<BarPendingVo> queryPendingBars() {
        List<BarInfo> bars = barInfoMapper.selectList(
            new LambdaQueryWrapper<BarInfo>()
                .in(BarInfo::getStatus, List.of(BAR_STATUS_PENDING_SINGE, BAR_STATUS_SINGING))
                .orderByDesc(BarInfo::getMarketingTime)
                .last("LIMIT 200"));
        List<BarPendingVo> list = new ArrayList<>(bars.size());
        for (BarInfo bar : bars) {
            BarPendingVo vo = new BarPendingVo();
            vo.setId(bar.getId());
            vo.setBarId(bar.getBarId());
            vo.setEarNo(bar.getEarNo());
            vo.setMarketingTime(bar.getMarketingTime());
            vo.setMarketingWeight(bar.getMarketingWeight());
            vo.setStatus(bar.getStatus());
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<BurnProductTypeVo> queryProductTypes() {
        List<ProductInfo> types = loadWhiteBarTypes();
        // IMG-LIB-001：批量解析产品图（L1 image_oss_id → L2 white_bar 默认图 → L3 全局），禁 N+1
        List<ImageUrlResolver.Item> items = types.stream()
            .map(p -> new ImageUrlResolver.Item(p.getImageOssId(), WHITE_BAR_BELONG_TYPE))
            .toList();
        List<String> urls = imageUrlResolver.resolveList(items);
        boolean urlsAligned = urls.size() == types.size();
        List<BurnProductTypeVo> result = new ArrayList<>(types.size());
        for (int i = 0; i < types.size(); i++) {
            ProductInfo p = types.get(i);
            BurnProductTypeVo vo = new BurnProductTypeVo();
            vo.setProductId(p.getId());
            vo.setProductCode(p.getProductId());
            vo.setProductName(p.getProductName());
            vo.setImageUrl(urlsAligned ? urls.get(i) : null);
            result.add(vo);
        }
        return result;
    }

    /**
     * 标准白条产品类型列表（belong_type='white_bar' + product_id 前缀 PROD-WHITE-BAR-，排除测试数据）。
     */
    private List<ProductInfo> loadWhiteBarTypes() {
        return productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getBelongType, WHITE_BAR_BELONG_TYPE)
                .likeRight(ProductInfo::getProductId, WHITE_BAR_CODE_PREFIX)
                .orderByAsc(ProductInfo::getProductId));
    }

    private Map<Long, ProductInfo> loadWhiteBarTypeMap() {
        return loadWhiteBarTypes().stream()
            .collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
    }

    @Override
    public TableDataInfo<PigBurnRecordVo> queryPageList(PigBurnRecordQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigBurnRecord> wrapper = buildQueryWrapper(query);
        Page<PigBurnRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillLocationNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PigBurnRecordVo> queryList(PigBurnRecordQuery query) {
        List<PigBurnRecordVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillLocationNames(list);
        return list;
    }

    @Override
    public PigBurnRecordVo queryById(Long id) {
        PigBurnRecordVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillLocationNames(List.of(vo));
        }
        return vo;
    }

    /**
     * BO → Entity 转换钩子（MapStruct-Plus）。
     *
     * <p>protected 方便单测覆盖。</p>
     */
    protected PigBurnRecord toEntity(PigBurnRecordBo bo) {
        return MapstructUtils.convert(bo, PigBurnRecord.class);
    }

    /**
     * 生成 burn_id：{@code BURN+yyMMdd+4 位}。
     *
     * <p>D9 closing Group B 迁入 {@link IBizCodeGenerator}（BizCodeType.BURN_NO，
     * seed 在 V202606071600）—— Redisson 分布式锁 + 序号表 UNIQUE 双保护，
     * 取代原 SELECT MAX inline 实现。</p>
     *
     * <p>protected 方便单测 stub 固定返回值。</p>
     */
    protected String generateBurnId() {
        return bizCodeGenerator.generate(BizCodeType.BURN_NO, Map.of());
    }

    /**
     * 批量回填库位名（避免 N+1）。
     */
    private void fillLocationNames(List<PigBurnRecordVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> locationIds = records.stream()
            .map(PigBurnRecordVo::getLocationId)
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
        for (PigBurnRecordVo vo : records) {
            if (vo.getLocationId() != null) {
                vo.setLocationName(nameMap.get(vo.getLocationId()));
            }
        }
    }

    private LambdaQueryWrapper<PigBurnRecord> buildQueryWrapper(PigBurnRecordQuery query) {
        LambdaQueryWrapper<PigBurnRecord> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PigBurnRecord::getId);
        }
        wrapper.eq(StringUtils.isNotBlank(query.getEarNo()), PigBurnRecord::getEarNo, query.getEarNo())
            .eq(StringUtils.isNotBlank(query.getBurnId()), PigBurnRecord::getBurnId, query.getBurnId())
            .eq(StringUtils.isNotBlank(query.getBurnStatus()), PigBurnRecord::getBurnStatus, query.getBurnStatus())
            .eq(query.getOperatorId() != null, PigBurnRecord::getOperatorId, query.getOperatorId())
            .ge(query.getBurnTimeFrom() != null, PigBurnRecord::getBurnTime, query.getBurnTimeFrom())
            .le(query.getBurnTimeTo() != null, PigBurnRecord::getBurnTime, query.getBurnTimeTo())
            .orderByDesc(PigBurnRecord::getId);
        return wrapper;
    }

}
