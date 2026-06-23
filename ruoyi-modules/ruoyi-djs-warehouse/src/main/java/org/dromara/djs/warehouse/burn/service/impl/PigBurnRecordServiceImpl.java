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
import org.dromara.djs.warehouse.burn.domain.vo.BurnProductTypeVo;
import org.dromara.djs.warehouse.burn.domain.vo.PigBurnRecordVo;
import org.dromara.djs.warehouse.burn.mapper.PigBurnRecordMapper;
import org.dromara.djs.warehouse.burn.service.IPigBurnRecordService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.domain.vo.LocationPickerVo;
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
     * 白条归属类型（{@code t_warehouse_product_info.belong_type}，字典 djs_belong_type）。
     * 燎毛入库产品类型由 admin 产品配置驱动 = belong_type='white_bar' + 燎毛间车间 + 正常态 + 原材料属性。
     */
    private static final String WHITE_BAR_BELONG_TYPE = "white_bar";

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
     * 白条产品类别枚举（FIX-WMS-MP-BURN-001 录入约束 + 去前缀用），按 product_id 业务码后缀映射。
     */
    private static final String PRODUCT_TYPE_WHOLE = "whole";
    private static final String PRODUCT_TYPE_HALF = "half";
    private static final String PRODUCT_TYPE_HEAD = "head";
    private static final String PRODUCT_TYPE_TROTTER = "trotter";

    /**
     * 白条状态码（{@code t_warehouse_bar_info.status}）。
     */
    private static final String BAR_STATUS_PENDING_SINGE = "pending_singe";
    private static final String BAR_STATUS_SINGING = "singing";

    /**
     * 库位启用态（{@code t_warehouse_location_info.location_status}）。
     */
    private static final Integer LOCATION_STATUS_ENABLED = 1;

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
            throw new ServiceException("入库库位不存在：" + bo.getLocationId());
        }
        Map<Long, ProductInfo> typeMap = loadWhiteBarTypeMap();
        // 单品上限兜底（MP-BURN 决策 #4）：单个产品入库重量不能超过头皮肉重量（到场重 arrive_weight，防直连 API 绕过前端拦截）。
        // 累计校验之前先拦单品，给更早更明确的报错。arrive 为 null（未称重）时跳过本校验（向后兼容）。
        BigDecimal headSkinWeight = bar.getArriveWeight();
        BigDecimal inWeightTotal = BigDecimal.ZERO;
        for (PigBurnRecordBo.ProductTypeItem item : bo.getProductTypeItems()) {
            if (!typeMap.containsKey(item.getProductId())) {
                throw new ServiceException("无效的白条产品类型：" + item.getProductId());
            }
            if (headSkinWeight != null && item.getWeight() != null
                && item.getWeight().compareTo(headSkinWeight) > 0) {
                throw new ServiceException("单个产品重量不能超过头皮肉重量");
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

        // ---------- Step 4：UPDATE bar_info status → singing（燎毛中，中间态；FIX-WMS-MP-BURN-001） ----------
        // 产品逐项入库只推进到中间态 singing（不直推 in_stock），解决「多产品逐个入库第 2 个抛错」现状 bug；
        // bar 终态 singed 由「处理完成」按钮调 finishBurn 推进。乐观锁 WHERE status IN(pending_singe,singing) 幂等兜并发。
        int affected = barInfoMapper.updateStatusToSinging(
            bar.getId(), burnTime, bo.getOperatorId());
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
        List<BarInfo> bars = barInfoMapper.selectList(
            new LambdaQueryWrapper<BarInfo>()
                .in(BarInfo::getStatus, List.of(BAR_STATUS_PENDING_SINGE, BAR_STATUS_SINGING))
                .orderByDesc(BarInfo::getMarketingTime)
                .last("LIMIT 200"));
        // 已入库产品重量之和（剩余未入库重量计算用）：对 singing 白条批量聚合 product_inhouse，避免 N+1
        Map<Long, BigDecimal> inboundedMap = loadInboundedWeightMap(bars);
        List<BarPendingVo> list = new ArrayList<>(bars.size());
        for (BarInfo bar : bars) {
            BarPendingVo vo = new BarPendingVo();
            vo.setId(bar.getId());
            vo.setBarId(bar.getBarId());
            vo.setEarNo(bar.getEarNo());
            vo.setMarketingTime(bar.getMarketingTime());
            vo.setMarketingWeight(bar.getMarketingWeight());
            vo.setStatus(bar.getStatus());
            vo.setArriveWeight(bar.getArriveWeight());
            // 接收时间 = 录入头皮肉重量那一刻（weighBurn 写 in_time）；未称重 pending_singe 为 null
            vo.setReceiveTime(bar.getInTime());
            // pending_singe 尚未入库任何产品 → 0；singing 取聚合值（无则 0）
            vo.setInboundedWeight(inboundedMap.getOrDefault(bar.getId(), BigDecimal.ZERO));
            list.add(vo);
        }
        return list;
    }

    /**
     * 批量聚合 singing 白条已入库产品重量之和（product_inhouse 按 white_bar_id IN + groupBy，避免 N+1）。
     *
     * @return white_bar_id → 已入库产品重量之和（pending_singe 白条不参与聚合，调用方默认 0）
     */
    private Map<Long, BigDecimal> loadInboundedWeightMap(List<BarInfo> bars) {
        List<Long> singingBarIds = bars.stream()
            .filter(b -> BAR_STATUS_SINGING.equals(b.getStatus()))
            .map(BarInfo::getId)
            .toList();
        if (singingBarIds.isEmpty()) {
            return Map.of();
        }
        List<ProductInhouse> inhouses = productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .select(ProductInhouse::getWhiteBarId, ProductInhouse::getProductWeight)
                .in(ProductInhouse::getWhiteBarId, singingBarIds));
        Map<Long, BigDecimal> map = new HashMap<>();
        for (ProductInhouse ih : inhouses) {
            if (ih.getWhiteBarId() == null) {
                continue;
            }
            BigDecimal w = ih.getProductWeight() == null ? BigDecimal.ZERO : ih.getProductWeight();
            map.merge(ih.getWhiteBarId(), w, BigDecimal::add);
        }
        return map;
    }

    @Override
    public List<BurnProductTypeVo> queryProductTypes() {
        List<ProductInfo> types = loadWhiteBarTypes();
        // IMG-LIB-001：批量解析产品图，禁 N+1。
        // L1 优先用户上传的缩略图 product_thumb（admin 产品表单唯一图片入口），退回自动匹配的 image_oss_id；
        // 再 L2 white_bar 默认图 → L3 全局兜底。
        List<ImageUrlResolver.Item> items = types.stream()
            .map(p -> new ImageUrlResolver.Item(resolveProductImageOssId(p), WHITE_BAR_BELONG_TYPE))
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
            vo.setProductType(resolveProductType(p.getProductId()));
            vo.setImageUrl(urlsAligned ? urls.get(i) : null);
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

        // ---------- Step 2：乐观锁推进 pending_singe/singing → singing（回填 in_time/in_method） ----------
        int affected = barInfoMapper.updateStatusToSinging(bar.getId(), new Date(), bo.getWeigherId());
        if (affected == 0) {
            throw new ServiceException("白条状态不符，无法称重");
        }

        // ---------- Step 3：回填到场重量 arrive_weight（updateStatusToSinging 不触此列）----------
        BarInfo patch = new BarInfo();
        patch.setId(bar.getId());
        patch.setArriveWeight(bo.getArriveWeight());
        barInfoMapper.updateById(patch);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishBurn(Long barInfoId, Long operatorId) {
        if (barInfoId == null) {
            throw new ServiceException("白条 ID 不能为空");
        }
        // ---------- Step 1：校验 bar 在燎毛中态 ----------
        BarInfo bar = barInfoMapper.selectById(barInfoId);
        if (bar == null) {
            throw new ServiceException("白条不存在：" + barInfoId);
        }
        if (!BAR_STATUS_SINGING.equals(bar.getStatus())) {
            throw new ServiceException("白条状态不符（当前：" + bar.getStatus() + "，需燎毛中），请先录入产品入库");
        }

        // ---------- Step 2：聚合该白条已入库产品 → 校验总重 + 整只/半只互斥（后端兜底前端约束）----------
        List<ProductInhouse> inhouses = productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .eq(ProductInhouse::getWhiteBarId, bar.getId()));
        if (inhouses.isEmpty()) {
            throw new ServiceException("尚未录入任何产品入库，无法处理完成");
        }

        Map<Long, ProductInfo> typeMap = loadWhiteBarTypeMap();
        BigDecimal inWeightTotal = BigDecimal.ZERO;
        boolean hasWhole = false;
        boolean hasHalf = false;
        int headCount = 0;
        for (ProductInhouse ih : inhouses) {
            BigDecimal w = ih.getProductWeight();
            if (w != null) {
                inWeightTotal = inWeightTotal.add(w);
            }
            ProductInfo type = typeMap.get(ih.getProductId());
            String pt = type == null ? null : resolveProductType(type.getProductId());
            if (PRODUCT_TYPE_WHOLE.equals(pt)) {
                hasWhole = true;
            } else if (PRODUCT_TYPE_HALF.equals(pt)) {
                hasHalf = true;
            } else if (PRODUCT_TYPE_HEAD.equals(pt)) {
                headCount++;
            }
        }
        // 整只 / 半只互斥（一头白条不能既整只又半只）
        if (hasWhole && hasHalf) {
            throw new ServiceException("整只与半只不能同时入库");
        }
        // 猪头限 1 次
        if (headCount > 1) {
            throw new ServiceException("猪头最多入库 1 次");
        }
        // 累计入库总重 ≤ 头皮肉重量（到场重 arrive_weight）。arrive 为 null（未称重）时跳过本校验（向后兼容）。
        BigDecimal headSkinWeight = bar.getArriveWeight();
        if (headSkinWeight != null && inWeightTotal.compareTo(headSkinWeight) > 0) {
            throw new ServiceException("已录入产品总重不能超过头皮肉重量");
        }

        // ---------- Step 3：UPDATE bar status singing → in_stock（燎毛处理完成=已入库，乐观锁）----------
        // 下游分割 availableBars / 库存自检均认 in_stock，故燎毛终态直接落 in_stock，
        // 不另立 singed 中间终态（否则白条进不了分割、不计在库，断链）。singing 中间态已解决多产品逐项入库 bug。
        int affected = barInfoMapper.updateStatusToInStock(bar.getId(), inWeightTotal, new Date(), operatorId);
        if (affected == 0) {
            throw new ServiceException("白条状态不符（已处理完成或不在燎毛中态），请刷新列表");
        }
    }

    /**
     * 按 product_id 业务码后缀映射结构化产品类别（FIX-WMS-MP-BURN-001）。
     *
     * <p>PROD-WHITE-BAR-01=整只 / -02=猪头 / -03=猪蹄 / -04=半只（与 white-bar seed 对齐）。
     * 未识别码返回 {@code null}（前端 graceful 不拦）。</p>
     */
    private String resolveProductType(String productCode) {
        if (productCode == null) {
            return null;
        }
        return switch (productCode) {
            case "PROD-WHITE-BAR-01" -> PRODUCT_TYPE_WHOLE;
            case "PROD-WHITE-BAR-02" -> PRODUCT_TYPE_HEAD;
            case "PROD-WHITE-BAR-03" -> PRODUCT_TYPE_TROTTER;
            case "PROD-WHITE-BAR-04" -> PRODUCT_TYPE_HALF;
            default -> null;
        };
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
    private static final Integer PRODUCT_WORKSHOP_BURN = 1;

    /**
     * 燎毛入库产品类型列表（admin 产品配置驱动）：product_workshop=1 燎毛间 + product_status=0 正常
     * （排除停用）+ product_attr=2 原材料，按业务码升序。
     *
     * <p>口径（Kevin 2026-06-23 拍板）：燎毛间入库 = admin 产品配置里「生产车间=燎毛间 + 产品属性=原材料
     * + 状态=正常」的所有产品，<b>不再额外限 belong_type='white_bar'</b>——否则会漏掉配在燎毛间的非白条
     * 原材料（如 GF0002 五花肉 belong_type='pork'，是燎毛间原材料但被 white_bar 过滤误挡）。</p>
     *
     * <p>只取 {@code product_attr=2}（原材料）；{@code product_attr=1} 生产产品 = 对外打包后的成品，
     * 不在燎毛入库。标准白条（整只/半只，业务码前缀 PROD-WHITE-BAR-）+ 其它配在燎毛间的原材料都进；
     * {@link #resolveProductType} 对非标准码返 null，前端回落按名称判类别。</p>
     */
    private List<ProductInfo> loadWhiteBarTypes() {
        return productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getProductWorkshop, PRODUCT_WORKSHOP_BURN)
                .eq(ProductInfo::getProductStatus, PRODUCT_STATUS_NORMAL)
                .eq(ProductInfo::getProductAttr, PRODUCT_ATTR_RAW_MATERIAL)
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
