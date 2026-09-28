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
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnWeighBo;
import org.dromara.djs.warehouse.burn.domain.query.PigBurnRecordQuery;
import org.dromara.djs.warehouse.burn.domain.vo.BarPendingVo;
import org.dromara.djs.warehouse.burn.domain.vo.BurnInboundVo;
import org.dromara.djs.warehouse.burn.domain.vo.BurnProductTypeVo;
import org.dromara.djs.warehouse.burn.domain.vo.PigBurnRecordVo;
import org.dromara.djs.warehouse.burn.mapper.PigBurnRecordMapper;
import org.dromara.djs.warehouse.burn.service.IPigBurnRecordService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.inout.domain.vo.CompletionCheckVo;
import org.dromara.djs.warehouse.inout.service.WeightCompletionPolicy;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.loss.domain.LossFlow;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.domain.vo.LocationPickerVo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.product.util.WorkshopMatcher;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
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
     * 白条归属类型（{@code t_warehouse_product_info.belong_type}，字典 djs_belong_type）。
     * 燎毛入库产品类型由 admin 产品配置驱动 = belong_type='white_bar' + 燎毛间车间 + 正常态 + 原材料属性。
     */
    private static final String WHITE_BAR_BELONG_TYPE = "white_bar";
    /** 早期占位白条产品业务码前缀：解析白条产品时排在甲方自建产品之后，避免占位产品夺走损耗 / 出库归属。 */
    private static final String SEED_WHITE_BAR_CODE_PREFIX = "PROD-WHITE-BAR-";

    /**
     * 产品状态（{@code t_warehouse_product_info.product_status}，字典 sys_normal_disable）：0=正常 / 1=停用。
     * 入库类型只取正常态，停用产品不可入库。
     */
    private static final Integer PRODUCT_STATUS_NORMAL = 0;

    /**
     * 产品属性（{@code t_warehouse_product_info.product_attr}，字典 djs_product_attr）：1=生产产品 / 2=原材料。
     * 燎毛产出的白条（整只/半只/猪头/猪蹄）是下游分割/打包的原材料，故燎毛入库只取原材料属性；
     * 生产产品 = 对外打包后的成品（belong_type=pork/gift_box 等），不在燎毛这一步入库。
     */
    private static final Integer PRODUCT_ATTR_RAW_MATERIAL = 2;

    /**
     * 白条产品类别（FIX-WMS-MP-BURN-001 录入约束用）：half=半只（一头猪左右两扇）。
     */
    private static final String PRODUCT_TYPE_HALF = "half";

    /**
     * 白条状态码（{@code t_warehouse_bar_info.status}）。
     */
    private static final String BAR_STATUS_PENDING_SINGE = "pending_singe";
    private static final String BAR_STATUS_SINGING = "singing";

    /**
     * 入库称重下限系数：入库重量必须 &gt; 出栏重量 × 该系数，否则判定为录错数并拒收。
     */
    private static final BigDecimal MIN_ARRIVE_WEIGHT_RATIO = new BigDecimal("0.5");

    /**
     * 库位启用态（{@code t_warehouse_location_info.location_status}）。
     */
    private static final Integer LOCATION_STATUS_ENABLED = 1;

    /**
     * row170 猪肉类可选库位名（按库位名精确匹配，两库 location_type 均为 warehouse 无法靠字典类型过滤）。
     */
    private static final String PORK_FRESH_LOCATION_NAME = "猪肉鲜品库";
    private static final String FROZEN_LOCATION_NAME = "冻品库";

    /**
     * 损耗类型（字典 {@code djs_loss_type}）：燎毛损耗（DENGBO row27）。
     */
    private static final String LOSS_TYPE_BURN = "burn_loss";

    /**
     * 损耗来源业务类型（{@code t_warehouse_loss_flow.source_biz_type}）：燎毛处理完成。
     */
    private static final String LOSS_SOURCE_BIZ_BURN = "burn";

    private final StockFlowMapper stockFlowMapper;
    private final BarInfoMapper barInfoMapper;
    private final ProductInhouseMapper productInhouseMapper;
    private final LocationStockMapper locationStockMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final ProductInfoMapper productInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IStockCheckService stockCheckService;
    private final ITraceService traceService;
    private final ImageUrlResolver imageUrlResolver;
    private final ILossFlowService lossFlowService;
    private final WeightCompletionPolicy completionPolicy;

    public PigBurnRecordServiceImpl(PigBurnRecordMapper baseMapper,
                                    StockFlowMapper stockFlowMapper,
                                    BarInfoMapper barInfoMapper,
                                    ProductInhouseMapper productInhouseMapper,
                                    LocationStockMapper locationStockMapper,
                                    LocationInfoMapper locationInfoMapper,
                                    ProductInfoMapper productInfoMapper,
                                    IBizCodeGenerator bizCodeGenerator,
                                    IStockCheckService stockCheckService,
                                    ITraceService traceService,
                                    ImageUrlResolver imageUrlResolver,
                                    ILossFlowService lossFlowService,
                                    WeightCompletionPolicy completionPolicy) {
        super(baseMapper);
        this.stockFlowMapper = stockFlowMapper;
        this.barInfoMapper = barInfoMapper;
        this.productInhouseMapper = productInhouseMapper;
        this.locationStockMapper = locationStockMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.productInfoMapper = productInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.stockCheckService = stockCheckService;
        this.traceService = traceService;
        this.imageUrlResolver = imageUrlResolver;
        this.lossFlowService = lossFlowService;
        this.completionPolicy = completionPolicy;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitBurnRecord(PigBurnRecordBo bo) {
        // ---------- Step 1：校验待燎毛白条 ----------
        BarInfo bar = barInfoMapper.selectForUpdate(bo.getBarInfoId());
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
            throw new ServiceException("入库库位不存在：" + bo.getLocationId());
        }
        Map<Long, ProductInfo> typeMap = loadWhiteBarTypeMap();
        List<BurnInboundVo> prior = stockFlowMapper.selectBurnInbounds(List.of(bar.getId()));
        Map<Long, Long> counts = prior.stream().collect(Collectors.groupingBy(
            BurnInboundVo::getProductId, Collectors.counting()));
        long halfCount = prior.stream().filter(r -> typeMap.containsKey(r.getProductId())
            && PRODUCT_TYPE_HALF.equals(resolveProductType(typeMap.get(r.getProductId())))).count();
        BigDecimal existing = prior.stream().map(BurnInboundVo::getWeight)
            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal inWeightTotal = BigDecimal.ZERO;
        for (PigBurnRecordBo.ProductTypeItem item : bo.getProductTypeItems()) {
            ProductInfo type = typeMap.get(item.getProductId());
            if (type == null) {
                throw new ServiceException("无效的白条产品类型：" + item.getProductId());
            }
            if (item.getWeight() == null || item.getWeight().signum() <= 0 || item.getWeight().scale() > 3) {
                throw new ServiceException("产品重量须大于 0，最多三位小数");
            }
            boolean half = PRODUCT_TYPE_HALF.equals(resolveProductType(type));
            long count = counts.merge(item.getProductId(), 1L, Long::sum);
            if (count > (half ? 2 : 1) || (half && ++halfCount > 2)) {
                throw new ServiceException(half ? "半扇最多录入 2 次" : "该产品已处理，只允许录入 1 次");
            }
            inWeightTotal = inWeightTotal.add(item.getWeight());
        }
        BigDecimal received = existing.add(inWeightTotal);
        if (bar.getMarketingWeight() != null && received.compareTo(bar.getMarketingWeight()) > 0) {
            throw new ServiceException("已录入产品总重不能超过出栏重量");
        }

        // ---------- Step 2：生成 burn_id + INSERT 燎毛记录 ----------
        // 一次提交记录本次产品重量；接收累计量在 bar 及查询VO中按入库事实计算。
        PigBurnRecord record = toEntity(bo);
        if (record == null) {
            throw new ServiceException("燎毛记录入参转换失败");
        }
        record.setEarNo(earNo);
        record.setBurnId(generateBurnId());
        record.setBurnWeight(inWeightTotal);
        record.setLossWeight(null);
        record.setBurnStatus(STATUS_DONE);
        // 入库人由 EmployeePicker 指定（可与登录态不同），非 LoginHelper
        record.setOperatorId(bo.getOperatorId());
        baseMapper.insert(record);

        // ---------- Step 3：for each 产品类型 → INSERT product_inhouse + INSERT 入库流水 ----------
        Date burnTime = bo.getBurnTime();
        LocalDate today = LocalDate.now();
        for (PigBurnRecordBo.ProductTypeItem item : bo.getProductTypeItems()) {
            ProductInfo type = typeMap.get(item.getProductId());

            // 白条流水号：每个白条产出行（半只/整只/猪头/猪蹄）生成一个唯一号（方案 A，邓博/Kevin 定）。
            // 这是「区分同一耳号两个半只」的半只标识（burn_id 是燎毛批次、区分不了同批两半只，故不用）；
            // 贯穿库存 / 领用 / 分割 / 发货，结算/聚合仍按 white_bar_id(整猪)。
            String whiteBarNo = bizCodeGenerator.generate(BizCodeType.BAR_NO, Map.of());

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
            inhouse.setWhiteBarNo(whiteBarNo);
            // V6-R43：产出行回指本次燎毛记录。burn_record.burn_weight 是本次提交各产出行之和，
            // 「燎毛间产品重量调整」按此把差额同步回燎毛记录；列表「入库人」也取 burn_record.operator_id。
            inhouse.setBurnRecordId(record.getId());
            inhouse.setLocationId(bo.getLocationId());
            productInhouseMapper.insert(inhouse);

            // 白条进白条库：每白条产出行按 (product_id, ear_no, white_bar_no) 建一条独立库存行
            // （邓博 row13：半只白条在库存维度成一条、库位查询按半只展示；耳号空显 white_bar_no）。
            // location_stock 无唯一约束，per-half 各成一行；领用时按 white_bar_no 扣减 + 写出库流水（P3）。
            LocationStock barStock = new LocationStock();
            barStock.setLocationId(bo.getLocationId());
            barStock.setProductId(type.getId());
            barStock.setProductName(type.getProductName());
            barStock.setEarNo(earNo);
            barStock.setWhiteBarNo(whiteBarNo);
            barStock.setProductStock(item.getWeight());
            barStock.setProductUnit(StringUtils.isNotBlank(type.getProductUnit()) ? type.getProductUnit() : "kg");
            barStock.setOperatorId(bo.getOperatorId());
            locationStockMapper.insert(barStock);

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
            flowIn.setWhiteBarNo(whiteBarNo);
            flowIn.setWhiteBarId(bar.getId());
            flowIn.setOperatorId(bo.getOperatorId());
            flowIn.setRemark("燎毛入库 burn_id=" + record.getBurnId() + " whiteBarNo=" + whiteBarNo + " type=" + type.getProductId());
            stockFlowMapper.insert(flowIn);
        }

        // ---------- Step 4：UPDATE bar_info status → singing（燎毛中，中间态；FIX-WMS-MP-BURN-001） ----------
        // 产品逐项入库只推进到中间态 singing（不直推 in_stock），解决「多产品逐个入库第 2 个抛错」现状 bug；
        // bar 终态 singed 由「处理完成」按钮调 finishBurn 推进。乐观锁 WHERE status IN(pending_singe,singing) 幂等兜并发。
        Date firstTime = prior.stream().map(BurnInboundVo::getFlowTime).filter(Objects::nonNull)
            .min(Date::compareTo).orElse(burnTime);
        int affected = barInfoMapper.updateBurnProgress(
            bar.getId(), received, firstTime, bo.getOperatorId());
        if (affected == 0) {
            throw new ServiceException("白条状态不符（已处理完成或不在待燎毛态），请刷新列表");
        }

        // TRC-CORE-001：燎毛追溯事件（按耳号反查 trace_code；猪肉链当前无生成入口 → warn 跳过，不拖垮燎毛事务）
        // 追溯时间轴每节点重量：燎毛节点重量 = 本次燎毛入库总重 burnWeight
        traceService.recordEventByEarNo(earNo, TraceContentConst.SINGE, record.getBurnWeight());

        return record.getId();
    }

    @Override
    public List<BarPendingVo> queryPendingBars() {
        List<BarInfo> bars = barInfoMapper.selectList(new LambdaQueryWrapper<BarInfo>()
            .in(BarInfo::getStatus, List.of(BAR_STATUS_PENDING_SINGE, BAR_STATUS_SINGING)));
        if (bars.isEmpty()) {
            return List.of();
        }
        // 接收时间和重量都从同一批完整入库事实计算：兼容旧称重时刻、已直发和已消费产出。
        List<BurnInboundVo> inbounds = stockFlowMapper.selectBurnInbounds(bars.stream().map(BarInfo::getId).toList());
        Map<Long, BigDecimal> weights = new HashMap<>();
        Map<Long, Date> firstTimes = new HashMap<>();
        for (BurnInboundVo inbound : inbounds) {
            weights.merge(inbound.getBarInfoId(), inbound.getWeight() == null ? BigDecimal.ZERO : inbound.getWeight(), BigDecimal::add);
            if (inbound.getFlowTime() != null) {
                firstTimes.merge(inbound.getBarInfoId(), inbound.getFlowTime(), (x, y) -> x.before(y) ? x : y);
            }
        }
        List<BarPendingVo> list = new ArrayList<>(bars.size());
        for (BarInfo bar : bars) {
            BarPendingVo vo = new BarPendingVo();
            vo.setId(bar.getId()); vo.setBarId(bar.getBarId()); vo.setEarNo(bar.getEarNo());
            vo.setMarketingTime(bar.getMarketingTime()); vo.setMarketingWeight(bar.getMarketingWeight());
            vo.setStatus(bar.getStatus());
            vo.setArriveWeight(weights.get(bar.getId()));
            vo.setReceiveTime(firstTimes.get(bar.getId()));
            vo.setInboundedWeight(weights.getOrDefault(bar.getId(), BigDecimal.ZERO));
            list.add(vo);
        }
        list.sort(java.util.Comparator.comparing((BarPendingVo v) -> v.getReceiveTime() == null
            ? v.getMarketingTime() : v.getReceiveTime(), java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
            .thenComparing(BarPendingVo::getId, java.util.Comparator.reverseOrder()));
        return list;
    }

    @Override
    public List<BurnProductTypeVo> queryProductTypes() {
        return queryProductTypes(null);
    }

    @Override
    public List<BurnProductTypeVo> queryProductTypes(Long barInfoId) {
        List<ProductInfo> types = loadWhiteBarTypes();
        // IMG-LIB-001：批量解析产品图，禁 N+1。
        // L1 优先用户上传的缩略图 product_thumb（admin 产品表单唯一图片入口），退回自动匹配的 image_oss_id；
        // 再 L2 white_bar 默认图 → L3 全局兜底。
        List<ImageUrlResolver.Item> items = types.stream()
            .map(p -> new ImageUrlResolver.Item(resolveProductImageOssId(p), WHITE_BAR_BELONG_TYPE))
            .toList();
        List<String> urls = imageUrlResolver.resolveList(items);
        boolean urlsAligned = urls.size() == types.size();
        // 入库事实计数不会随直发、打包等后续消耗消失；每个产品同时返回累计实重。
        List<BurnInboundVo> recorded = barInfoId == null ? List.of()
            : stockFlowMapper.selectBurnInbounds(List.of(barInfoId));
        Map<Long, Integer> recordedCountMap = new HashMap<>();
        Map<Long, BigDecimal> recordedWeights = new HashMap<>();
        for (BurnInboundVo r : recorded) {
            recordedCountMap.merge(r.getProductId(), 1, Integer::sum);
            recordedWeights.merge(r.getProductId(), r.getWeight() == null ? BigDecimal.ZERO : r.getWeight(), BigDecimal::add);
        }
        List<BurnProductTypeVo> result = new ArrayList<>(types.size());
        for (int i = 0; i < types.size(); i++) {
            ProductInfo p = types.get(i);
            BurnProductTypeVo vo = new BurnProductTypeVo();
            vo.setProductId(p.getId());
            vo.setProductCode(p.getProductId());
            vo.setProductName(p.getProductName());
            vo.setProductType(resolveProductType(p));
            vo.setImageUrl(urlsAligned ? urls.get(i) : null);
            vo.setRecordedCount(recordedCountMap.getOrDefault(p.getId(), 0));
            vo.setRecordedWeight(recordedWeights.getOrDefault(p.getId(), BigDecimal.ZERO));
            boolean whiteBar = WHITE_BAR_BELONG_TYPE.equals(p.getBelongType());
            vo.setIsWhiteBar(whiteBar);
            vo.setMaxCount(whiteBar ? 2 : 1);
            List<LocationPickerVo> locations = queryProductInboundLocations(p.getId());
            if (!locations.isEmpty()) {
                vo.setDefaultLocationId(locations.getFirst().getId());
                vo.setDefaultLocationName(locations.getFirst().getLocationName());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<LocationPickerVo> queryPorkOptionLocations() {
        List<String> names = List.of(PORK_FRESH_LOCATION_NAME, FROZEN_LOCATION_NAME);
        List<LocationInfo> rows = locationInfoMapper.selectList(
            new LambdaQueryWrapper<LocationInfo>()
                .in(LocationInfo::getLocationName, names)
                .eq(LocationInfo::getLocationStatus, LOCATION_STATUS_ENABLED));
        Map<String, LocationInfo> byName = rows.stream()
            .collect(Collectors.toMap(LocationInfo::getLocationName, l -> l, (a, b) -> a));
        List<LocationPickerVo> result = new ArrayList<>(names.size());
        for (String name : names) {
            LocationInfo l = byName.get(name);
            if (l == null) {
                continue;
            }
            LocationPickerVo vo = new LocationPickerVo();
            vo.setId(l.getId());
            vo.setLocationCode(l.getLocationCode());
            vo.setLocationName(l.getLocationName());
            vo.setLocationType(l.getLocationType());
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<LocationPickerVo> queryProductInboundLocations(Long productId) {
        if (productId == null) {
            return List.of();
        }
        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null || StringUtils.isBlank(product.getStoreLocationId())) {
            // 该产品未配置存储仓库 → 返回空，前端回落自由选库位
            return List.of();
        }
        // store_location_id 逗号分隔库位 ID 列表（doc/11 §2.5；V2 改关联表）
        List<Long> locationIds = new ArrayList<>();
        for (String token : product.getStoreLocationId().split(",")) {
            String trimmed = token.trim();
            if (StringUtils.isNotBlank(trimmed)) {
                try {
                    locationIds.add(Long.valueOf(trimmed));
                } catch (NumberFormatException ignore) {
                    // 脏数据（非数字 ID）跳过，不拖垮整体取数
                }
            }
        }
        if (locationIds.isEmpty()) {
            return List.of();
        }
        // 仅返启用库位（不限库位类型 —— 入库库位由产品「存储仓库」配置驱动，白条库/冻品库等都可），保持配置顺序
        List<LocationInfo> rows = locationInfoMapper.selectList(
            new LambdaQueryWrapper<LocationInfo>()
                .in(LocationInfo::getId, locationIds)
                .eq(LocationInfo::getLocationStatus, LOCATION_STATUS_ENABLED));
        Map<Long, LocationInfo> rowMap = rows.stream()
            .collect(Collectors.toMap(LocationInfo::getId, l -> l, (a, b) -> a));
        List<LocationPickerVo> result = new ArrayList<>(locationIds.size());
        for (Long id : locationIds) {
            LocationInfo l = rowMap.get(id);
            if (l == null) {
                continue;
            }
            LocationPickerVo vo = new LocationPickerVo();
            vo.setId(l.getId());
            vo.setLocationCode(l.getLocationCode());
            vo.setLocationName(l.getLocationName());
            vo.setLocationType(l.getLocationType());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean weighBurn(PigBurnWeighBo bo) {
        // ---------- Step 1：校验 bar 在待燎毛 / 燎毛中态 + 到场重 ≤ 出栏重 ----------
        BarInfo bar = barInfoMapper.selectById(bo.getBarInfoId());
        if (bar == null) {
            throw new ServiceException("白条不存在：" + bo.getBarInfoId());
        }
        if (!BAR_STATUS_PENDING_SINGE.equals(bar.getStatus())
            && !BAR_STATUS_SINGING.equals(bar.getStatus())) {
            throw new ServiceException("白条状态不符（当前：" + bar.getStatus() + "，需待燎毛/燎毛中），无法称重");
        }
        BigDecimal marketingWeight = bar.getMarketingWeight();
        if (marketingWeight != null && bo.getArriveWeight().compareTo(marketingWeight) > 0) {
            throw new ServiceException("到场重量不能超过出栏重量");
        }
        // 入库重量下限：必须 > 出栏重量 × 50%（燎毛去毛去杂 + 去头的正常损耗上限，低于此判定为录错数）
        if (marketingWeight != null && marketingWeight.compareTo(BigDecimal.ZERO) > 0
            && bo.getArriveWeight().compareTo(marketingWeight.multiply(MIN_ARRIVE_WEIGHT_RATIO)) <= 0) {
            throw new ServiceException("请录入正确的入库重量");
        }

        // ---------- Step 2：乐观锁推进 pending_singe/singing → singing（回填 in_time/in_method） ----------
        Date weighTime = new Date();
        int affected = barInfoMapper.updateStatusToSinging(bar.getId(), weighTime, bo.getWeigherId());
        if (affected == 0) {
            throw new ServiceException("白条状态不符，无法称重");
        }

        // ---------- Step 3：回填到场重量 arrive_weight + 到场时间 arrive_time（updateStatusToSinging 不触这两列）----------
        // 到场时间 = **首次**燎毛间称重时刻（与 arrive_weight 同一次过磅），口径同外购猪只列表的「到场时间 =
        // 燎毛间称重完成时刻」。两条都别改：
        //   · 不能借 bar.in_time —— 它在后续每次产品逐项入库、以及处理完成时都会被覆盖成最后一次时刻，
        //     用它当到场时间会随入库进度往后漂；
        //   · 已有值不覆盖 —— 称重接口的状态守卫在「称重→逐项入库→处理完成」整个窗口期都放行，重复称重
        //     （mp 端有 weighDone 硬锁，裸调接口仍可达）会把到场时间推到已录产出行的入库时间之后，出现
        //     「到场晚于入库」的倒挂。重量允许改正，时间锚定第一次。
        BarInfo patch = new BarInfo();
        patch.setId(bar.getId());
        patch.setArriveWeight(bo.getArriveWeight());
        if (bar.getArriveTime() == null) {
            patch.setArriveTime(weighTime);
        }
        barInfoMapper.updateById(patch);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishBurn(Long barInfoId, Long operatorId) {
        finishBurn(barInfoId, operatorId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CompletionCheckVo finishCheck(Long barInfoId) {
        BurnCompletionState state = loadBurnCompletion(barInfoId);
        return completionPolicy.burn(state.receivedWeight(), state.bar().getMarketingWeight());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void finishBurn(Long barInfoId, Long operatorId, Boolean confirmAbnormalWeight) {
        BurnCompletionState state = loadBurnCompletion(barInfoId);
        BarInfo bar = state.bar();
        BigDecimal inWeightTotal = state.receivedWeight();
        WeightCompletionPolicy.requireConfirmation(
            completionPolicy.burn(inWeightTotal, bar.getMarketingWeight()), confirmAbnormalWeight);

        // ---------- Step 3：UPDATE bar status singing → in_stock（燎毛处理完成=已入库，乐观锁）----------
        // 下游分割 availableBars / 库存自检均认 in_stock，故燎毛终态直接落 in_stock，
        // 不另立 singed 中间终态（否则白条进不了分割、不计在库，断链）。singing 中间态已解决多产品逐项入库 bug。
        int affected = barInfoMapper.updateStatusToInStock(bar.getId(), inWeightTotal, new Date(), operatorId);
        if (affected == 0) {
            throw new ServiceException("白条状态不符（已处理完成或不在燎毛中态），请刷新列表");
        }

        // TRC 白条入库事件（邓博 row19 拆出独立事件）：燎毛处理完成、白条称重入库时刻按耳号写追溯流水。
        // 重量 = 本次入库总重 inWeightTotal。recordEventByEarNo 全程容错（无生码白条 warn 跳过，不拖垮燎毛事务）。
        traceService.recordEventByEarNo(bar.getEarNo(), TraceContentConst.WHITE_BAR_IN, inWeightTotal);

        BigDecimal shippedWeight = barInfoMapper.fullyDirectShippedWeight(bar.getId());
        if (shippedWeight != null) {
            barInfoMapper.updateStatusToShipOut(bar.getId(), new Date(), shippedWeight, operatorId);
        }


    }


    private record BurnCompletionState(BarInfo bar, BigDecimal receivedWeight) { }

    /** 预检和真正完成都执行相同硬约束；确认标志不能绕过它们。 */
    private BurnCompletionState loadBurnCompletion(Long barInfoId) {
        if (barInfoId == null) {
            throw new ServiceException("白条 ID 不能为空");
        }
        // ---------- Step 1：校验 bar 在燎毛中态 ----------
        BarInfo bar = barInfoMapper.selectForUpdate(barInfoId);
        if (bar == null) {
            throw new ServiceException("白条不存在：" + barInfoId);
        }
        if (!BAR_STATUS_SINGING.equals(bar.getStatus())) {
            throw new ServiceException("白条状态不符（当前：" + bar.getStatus() + "，需燎毛中），请先录入产品入库");
        }

        // 累计入库事实包含已经直发/消耗的产出；整猪锁与录入、调整互斥。
        List<BurnInboundVo> inbounds = stockFlowMapper.selectBurnInbounds(List.of(bar.getId()));
        if (inbounds.isEmpty()) {
            throw new ServiceException("尚未录入任何产品入库，无法处理完成");
        }
        Map<Long, ProductInfo> typeMap = loadWhiteBarTypeMap();
        BigDecimal inWeightTotal = BigDecimal.ZERO;
        int halfCount = 0;
        for (BurnInboundVo inbound : inbounds) {
            inWeightTotal = inWeightTotal.add(inbound.getWeight() == null ? BigDecimal.ZERO : inbound.getWeight());
            ProductInfo type = typeMap.get(inbound.getProductId());
            if (type != null && PRODUCT_TYPE_HALF.equals(resolveProductType(type))) {
                halfCount++;
            }
        }
        if (halfCount != 2) {
            throw new ServiceException("半只需录入 2 个才能处理完成，当前已录 " + halfCount + "/2");
        }
        // 完成时再次校验累计不超过出栏重；缺失基准重量由比例判定作为数据异常拒绝。
        BigDecimal marketingWeight = bar.getMarketingWeight();
        if (marketingWeight != null && inWeightTotal.compareTo(marketingWeight) > 0) {
            throw new ServiceException("白条重量不能超过出栏重量（出栏重 "
                + marketingWeight.stripTrailingZeros().toPlainString() + "kg，当前已录 "
                + inWeightTotal.stripTrailingZeros().toPlainString() + "kg）");
        }

        return new BurnCompletionState(bar, inWeightTotal);
    }

    /**
     * 按产品主数据判定结构化产品类别（FIX-WMS-MP-BURN-001 录入约束用）。
     *
     * <p>判据是 {@code belong_type}（产品类别）而非业务码 —— 白条产品由甲方在 admin 产品配置里维护，
     * 增删改都不该要求改代码：</p>
     * <ul>
     *   <li>{@code belong_type='white_bar'}（产品类别=白条产品）= 燎毛产出的白条本体，
     *       一头猪出左右两扇 → {@link #PRODUCT_TYPE_HALF}，限录 2 次、须集齐 2 扇才能处理完成；</li>
     *   <li>其余配在燎毛间的原材料（猪头 / 猪脚 等 {@code belong_type='pork'}）→ {@code null}，
     *       不参与半只约束，前端按产品名回落判类别。</li>
     * </ul>
     */
    private static String resolveProductType(ProductInfo product) {
        return WHITE_BAR_BELONG_TYPE.equals(product.getBelongType()) ? PRODUCT_TYPE_HALF : null;
    }

    /**
     * 产品卡展示用 ossId 优先级：用户在 admin 上传的缩略图 {@code product_thumb} 优先，
     * 退回 IMG-LIB-001 自动匹配的主图 {@code image_oss_id}（两者都空交 resolver 走 L2/L3 默认图兜底）。
     *
     * <p>admin 产品表单唯一图片入口写 {@code product_thumb}，{@code image_oss_id} 仅在创建时按产品名
     * 自动命中图库才有值；故展示必须优先取用户显式上传的缩略图，否则自建产品的图在 mp 全不显示。</p>
     */
    private static String resolveProductImageOssId(ProductInfo p) {
        return StringUtils.isNotBlank(p.getProductThumb()) ? p.getProductThumb() : p.getImageOssId();
    }

    /**
     * 燎毛间车间码（{@code t_warehouse_product_info.product_workshop}，字典 djs_product_workshop = 1）。
     */
    private static final String PRODUCT_WORKSHOP_BURN = "1";

    /**
     * 燎毛入库产品类型列表（admin 产品配置驱动）：product_workshop=1 燎毛间 + product_status=0 正常
     * （排除停用）+ product_attr=2 原材料，按业务码升序。
     *
     * <p>口径（Kevin 2026-06-23 拍板）：燎毛间入库 = admin 产品配置里「生产车间=燎毛间 + 产品属性=原材料
     * + 状态=正常」的所有产品，<b>不再额外限 belong_type='white_bar'</b>——否则会漏掉配在燎毛间的非白条
     * 原材料（如 GF0002 五花肉 belong_type='pork'，是燎毛间原材料但被 white_bar 过滤误挡）。</p>
     *
     * <p>只取 {@code product_attr=2}（原材料）；{@code product_attr=1} 生产产品 = 对外打包后的成品，
     * 不在燎毛入库。白条本体（{@code belong_type='white_bar'}，甲方主数据里是「半扇」）+ 其它配在燎毛间的
     * 原材料（猪头 / 猪脚 等 {@code belong_type='pork'}）都进；
     * {@link #resolveProductType} 只对白条本体返 half，其余返 null，前端回落按名称判类别。</p>
     */
    private List<ProductInfo> loadWhiteBarTypes() {
        return productInfoMapper.selectList(
            WorkshopMatcher.match(
                new LambdaQueryWrapper<ProductInfo>()
                    .eq(ProductInfo::getProductStatus, PRODUCT_STATUS_NORMAL)
                    .eq(ProductInfo::getProductAttr, PRODUCT_ATTR_RAW_MATERIAL),
                PRODUCT_WORKSHOP_BURN)
                .orderByAsc(ProductInfo::getProductId));
    }

    private Map<Long, ProductInfo> loadWhiteBarTypeMap() {
        return loadWhiteBarTypes().stream()
            .collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
    }

    /**
     * 解析白条产品 id：产品主数据里「产品类别=白条产品 + 状态正常」的产品，甲方自建产品优先、其次按业务码升序取第一个
     *（甲方主数据里当前是「半扇」）。
     *
     * <p>口径与 {@code PigCutRecordServiceImpl#resolveWhiteBarProductId} 一致，使燎毛损耗 / 分割损耗 /
     * 预冷损耗三种猪肉过程损耗在「损耗总览」里归到同一个产品行。不绑固定业务码，因为白条产品由甲方在
     * admin 产品配置里维护。</p>
     *
     * <p>与分割侧的唯一差别：查不到白条产品时返 {@code null} 而不抛异常 —— 损耗只是燎毛完成的副产账，
     * 不能因产品主数据缺失阻断燎毛主链；此时 loss_flow 退化为无产品损耗行。</p>
     */
    private Long resolveWhiteBarProductId() {
        ProductInfo whiteBar = productInfoMapper.selectOne(
            new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getBelongType, WHITE_BAR_BELONG_TYPE)
                .eq(ProductInfo::getProductStatus, PRODUCT_STATUS_NORMAL)
                .last("ORDER BY (product_id LIKE '" + SEED_WHITE_BAR_CODE_PREFIX + "%'), product_id ASC LIMIT 1"));
        if (whiteBar == null || whiteBar.getId() == null) {
            log.warn("燎毛损耗未挂产品：产品配置里没有「产品类别=白条产品」且状态正常的产品");
            return null;
        }
        return whiteBar.getId();
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
