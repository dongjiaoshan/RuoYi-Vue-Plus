package org.dromara.djs.warehouse.shipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.common.util.I18nMessages;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.shipment.domain.Shipment;
import org.dromara.djs.warehouse.shipment.domain.bo.ShipmentCheckBo;
import org.dromara.djs.warehouse.shipment.domain.query.ShipmentQuery;
import org.dromara.djs.warehouse.shipment.domain.vo.AvailableProductionVo;
import org.dromara.djs.warehouse.shipment.domain.vo.ShipDemandVo;
import org.dromara.djs.warehouse.shipment.domain.vo.ShipStoreVo;
import org.dromara.djs.warehouse.shipment.domain.vo.ShipmentVo;
import org.dromara.djs.warehouse.shipment.event.ShipmentConfirmedEvent;
import org.dromara.djs.warehouse.shipment.mapper.ShipmentMapper;
import org.dromara.djs.warehouse.shipment.service.IShipmentService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 发货流水 Service 实现（WMS-SHIP-001）。
 *
 * <h3>3 表事务一致性</h3>
 * <p>{@link #confirmCheck}：UPDATE product_production + INSERT shipment + INSERT stock_flow + publishEvent；
 * 任一失败 → 整个 {@code @Transactional} 回滚。</p>
 *
 * <h3>事件发布时机</h3>
 * <p>{@link ApplicationEventPublisher#publishEvent} 在 {@code @Transactional} 内调用 — Spring 默认
 * **同步**派发监听器。要让 D14 listener 走 {@code AFTER_COMMIT} 阶段必须由 listener 端用
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 显式声明。本端发布即可，
 * 不需要额外 try/catch（异常会随事务回滚）。</p>
 *
 * <h3>并发安全</h3>
 * <ul>
 *   <li>{@code markDeliveryChecked} 走 {@code WHERE is_delivery_check=0} 乐观锁，
 *       affectedRows < ids.size() → 抛"并发清点冲突"</li>
 *   <li>UNIQUE(tenant_id, shipment_no, del_unique) 保证业务码幂等</li>
 * </ul>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Slf4j
@Service
public class ShipmentServiceImpl
    extends DjsBaseServiceImpl<ShipmentMapper, Shipment>
    implements IShipmentService {

    /**
     * stock_flow.flow_type — 发货出库（dict djs_flow_type 已 D9 seed）。
     */
    private static final String FLOW_TYPE_SHIP_OUT = "ship_out";

    /**
     * stock_flow.inout_type — OT=出库（CHAR(3)）。
     */
    private static final String INOUT_OUT = "OT";

    /**
     * shipment_status — pending/checking/shipped/delivered。本 ticket 工人清点即直接 shipped。
     */
    private static final String SHIPMENT_STATUS_SHIPPED = "shipped";

    /**
     * demand_status 允许发货的白名单：CONFIRMED / IN_PRODUCTION / PARTIAL_SHIPPED。
     */
    private static final Set<DemandStatus> SHIPPABLE_DEMAND_STATUSES = Set.of(
        DemandStatus.CONFIRMED,
        DemandStatus.IN_PRODUCTION,
        DemandStatus.PARTIAL_SHIPPED
    );

    /**
     * demand.product_type（dict {@code djs_demand_product_type}：white_bar/vegetable/gift_box/other）
     * → production 关联 {@code ProductInfo.belong_type}（dict {@code djs_belong_type}）集合的映射。
     *
     * <p>SHIP-DEMANDID-001：发货"按业态匹配可用库存"。production 的真业态取自所属产品的
     * {@code belong_type}（pack 生成 produce_no 前缀即据此），<b>不是</b> production.product_type
     * 的 numeric 码（1 自产/2 外购/3 礼盒，语义不同，不能直接比较）。</p>
     *
     * <p>{@code other} 业态不入表 → 走兜底放宽（不按业态收窄，仅 store_id + demand_id IS NULL）。</p>
     */
    private static final Map<String, Set<String>> DEMAND_TYPE_TO_BELONG_TYPES = Map.of(
        "white_bar", Set.of("white_bar", "pork"),
        "vegetable", Set.of("vegetable"),
        "gift_box", Set.of("gift_box")
    );

    private final ProductProductionMapper productProductionMapper;
    private final StockFlowMapper stockFlowMapper;
    private final DemandManageMapper demandMapper;
    private final ProductInfoMapper productInfoMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final StoreMapper storeMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public ShipmentServiceImpl(ShipmentMapper baseMapper,
                               ProductProductionMapper productProductionMapper,
                               StockFlowMapper stockFlowMapper,
                               DemandManageMapper demandMapper,
                               ProductInfoMapper productInfoMapper,
                               LocationInfoMapper locationInfoMapper,
                               StoreMapper storeMapper,
                               IBizCodeGenerator bizCodeGenerator,
                               ApplicationEventPublisher eventPublisher) {
        super(baseMapper);
        this.productProductionMapper = productProductionMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.demandMapper = demandMapper;
        this.productInfoMapper = productInfoMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.storeMapper = storeMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long confirmCheck(ShipmentCheckBo bo) {
        Long userId = LoginHelper.getUserId();

        // 1. 校验 demand 存在 + 状态可发货
        DemandManage demand = demandMapper.selectById(bo.getDemandId());
        if (demand == null) {
            throw new ServiceException(I18nMessages.t("shipment.demand.not_found", bo.getDemandId()), 404);
        }
        DemandStatus demandStatus = DemandStatus.fromCodeSafe(demand.getDemandStatus());
        if (demandStatus == null || !SHIPPABLE_DEMAND_STATUSES.contains(demandStatus)) {
            throw new ServiceException(
                I18nMessages.t("shipment.demand.status_invalid", demand.getDemandStatus()), 400);
        }

        // 2. 校验 product_production 行存在 + 未清点
        List<ProductProduction> productions = productProductionMapper.selectList(
            new LambdaQueryWrapper<ProductProduction>()
                .in(ProductProduction::getId, bo.getProductionIds()));
        if (productions.size() != bo.getProductionIds().size()) {
            throw new ServiceException(I18nMessages.t("shipment.production.partial_missing"), 400);
        }
        for (ProductProduction p : productions) {
            if (p.getIsDeliveryCheck() != null && p.getIsDeliveryCheck() == 1) {
                throw new ServiceException(
                    I18nMessages.t("shipment.production.already_checked", p.getProduceNo()), 400);
            }
            // 可用库存 demand_id 为 NULL（未分配，待本次绑定 — SHIP-DEMANDID-001）→ 放行；
            // 已绑同一 demand 视为幂等重复确认 → 放行；绑了别的 demand 才 mismatch。
            if (p.getDemandId() != null && !Objects.equals(p.getDemandId(), bo.getDemandId())) {
                throw new ServiceException(
                    I18nMessages.t("shipment.production.demand_mismatch", p.getProduceNo()), 400);
            }
        }

        // 3. UPDATE product_production：标清点 + 回写 demand_id（一次原子 UPDATE，乐观锁）
        Date now = new Date();
        int affected = productProductionMapper.markDeliveryChecked(
            bo.getProductionIds(), now, bo.getDemandId());
        if (affected != bo.getProductionIds().size()) {
            throw new ServiceException(I18nMessages.t("shipment.production.concurrent_conflict"), 409);
        }

        // 4. INSERT shipment 主表
        Shipment shipment = new Shipment();
        shipment.setShipmentNo(bizCodeGenerator.generate(BizCodeType.SHIP_NO, Map.of()));
        shipment.setDemandId(demand.getId());
        shipment.setProductType(demand.getProductType());
        shipment.setStoreId(demand.getStoreId());
        shipment.setShipDate(java.time.LocalDate.now());
        shipment.setShipQuantity(bo.getTotalQuantity());
        shipment.setShipUnit(bo.getShipUnit());
        shipment.setDeliverType(bo.getDeliverType());
        shipment.setReceiverName(bo.getReceiverName());
        shipment.setReceiverPhone(bo.getReceiverPhone());
        shipment.setReceiverAddress(bo.getReceiverAddress());
        shipment.setShipmentStatus(SHIPMENT_STATUS_SHIPPED);
        shipment.setCheckerId(userId);
        shipment.setCheckTime(java.time.LocalDateTime.now());
        shipment.setProofOssIds(bo.getProofOssIds());
        shipment.setRemark(bo.getRemark());
        baseMapper.insert(shipment);

        // 5. INSERT stock_flow（出库流水，按 production 行逐条记 — 保留细颗粒追溯）
        for (ProductProduction p : productions) {
            StockFlow flow = new StockFlow();
            Map<String, Object> flowCtx = new HashMap<>(2);
            flowCtx.put("ioCode", INOUT_OUT);
            flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, flowCtx));
            flow.setFlowDate(now);
            flow.setProductId(p.getProductId());
            flow.setWarehouseId(p.getProduceLocation());
            flow.setDemandId(bo.getDemandId());
            flow.setInoutType(INOUT_OUT);
            flow.setFlowType(FLOW_TYPE_SHIP_OUT);
            flow.setChangeNum(p.getProduceQuantity());
            flow.setChangeQuantity(p.getProduceQuantity());
            flow.setEarNo(p.getEarNo());
            flow.setOperatorId(userId);
            flow.setRemark("发货 → store_id=" + demand.getStoreId()
                + " shipment_no=" + shipment.getShipmentNo()
                + " produce_no=" + p.getProduceNo());
            stockFlowMapper.insert(flow);
        }

        // 6. publishEvent — D14 CROSS-FLOW-003 listener 消费触发 demand.shipped_count 累加 + transition
        //    带 userId（=checkerId 发货确认人）：AFTER_COMMIT 阶段 listener 无 sa-token 上下文，
        //    需显式传 operator 给 demand transition 做审计，否则 resolveOperator(null) 抛 demand.operator.required
        eventPublisher.publishEvent(new ShipmentConfirmedEvent(
            this, shipment.getId(), demand.getId(), bo.getTotalQuantity(), userId));

        log.info("[WMS-SHIP-001] confirmCheck shipmentId={} demandId={} productionCount={} qty={}",
            shipment.getId(), demand.getId(), productions.size(), bo.getTotalQuantity());

        return shipment.getId();
    }

    @Override
    public TableDataInfo<ShipmentVo> queryPageList(ShipmentQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Shipment> wrapper = buildQueryWrapper(query);
        Page<ShipmentVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillStoreNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<ShipmentVo> queryList(ShipmentQuery query) {
        List<ShipmentVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillStoreNames(list);
        return list;
    }

    @Override
    public ShipmentVo queryById(Long id) {
        ShipmentVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillStoreNames(List.of(vo));
        }
        return vo;
    }

    @Override
    public List<AvailableProductionVo> listAvailableProductions(Long demandId) {
        if (demandId == null) {
            throw new ServiceException(I18nMessages.t("shipment.demand.id_required"), 400);
        }
        DemandManage demand = demandMapper.selectById(demandId);
        if (demand == null) {
            throw new ServiceException(I18nMessages.t("shipment.demand.not_found", demandId), 404);
        }
        return toAvailableProductionVos(findAvailableProductionsForDemand(demand));
    }

    @Override
    public int countAvailableProductionsForDemand(Long demandId) {
        if (demandId == null) {
            return 0;
        }
        DemandManage demand = demandMapper.selectById(demandId);
        return demand == null ? 0 : findAvailableProductionsForDemand(demand).size();
    }

    @Override
    public List<ShipStoreVo> listPendingStores() {
        // 1. 扫所有 SHIPPABLE 状态的 demand（门店列表的数据驱动单位是需求单，非 production）。
        List<DemandManage> demands = loadShippableDemands(null);
        if (demands.isEmpty()) {
            return List.of();
        }
        // 2. group by store_id（过滤无门店的 demand —— 无 store 不进门店列表）。
        Map<Long, List<DemandManage>> byStore = demands.stream()
            .filter(d -> d.getStoreId() != null)
            .collect(Collectors.groupingBy(DemandManage::getStoreId));
        if (byStore.isEmpty()) {
            return List.of();
        }
        // 3. 批量填门店名（无 N+1）。
        Map<Long, String> storeNameMap = loadStoreNameMap(byStore.keySet());
        // 4. 每门店算待发需求数 + 待发产品种类数 + 总量。
        List<ShipStoreVo> list = new ArrayList<>(byStore.size());
        byStore.forEach((storeId, storeDemands) -> {
            ShipStoreVo vo = new ShipStoreVo();
            vo.setStoreId(storeId);
            vo.setStoreName(storeNameMap.get(storeId));
            vo.setPendingDemandCount(storeDemands.size());
            vo.setProductKindCount((int) storeDemands.stream()
                .map(DemandManage::getProductId).filter(Objects::nonNull).distinct().count());
            vo.setPendingQuantity(storeDemands.stream()
                .map(DemandManage::getDemandQuantity).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
            vo.setShipStatus("待发货");
            list.add(vo);
        });
        // 5. 稳定排序：待发需求多的门店在前，其次按门店名。
        list.sort(Comparator.comparingInt(ShipStoreVo::getPendingDemandCount).reversed()
            .thenComparing(v -> v.getStoreName() == null ? "" : v.getStoreName()));
        return list;
    }

    @Override
    public List<ShipDemandVo> listStorePendingDemands(Long storeId) {
        if (storeId == null) {
            throw new ServiceException(I18nMessages.t("shipment.store.id_required"), 400);
        }
        // 该门店所有 SHIPPABLE demand，每个 demand 内嵌按业态 + store_id 匹配出的可发产品清单。
        List<DemandManage> demands = loadShippableDemands(storeId);
        if (demands.isEmpty()) {
            return List.of();
        }
        return demands.stream().map(d -> {
            ShipDemandVo vo = new ShipDemandVo();
            vo.setDemandId(d.getId());
            vo.setDemandNo(d.getDemandNo());
            vo.setDemandDate(d.getDemandDate());
            vo.setProductType(d.getProductType());
            vo.setDemandStatus(d.getDemandStatus());
            vo.setDemandQuantity(d.getDemandQuantity());
            vo.setShippedCount(d.getShippedCount());
            vo.setAvailableProductions(toAvailableProductionVos(findAvailableProductionsForDemand(d)));
            return vo;
        }).toList();
    }

    // ---------- private helpers ----------

    /**
     * SHIP-DEMANDID-001 核心匹配：按 demand 的业态(belong_type) + store_id 筛出"可发的未分配库存"
     * （{@code demand_id IS NULL AND is_delivery_check=0}）。listAvailableProductions /
     * listStorePendingDemands 共用，避免逻辑复制。
     */
    private List<ProductProduction> findAvailableProductionsForDemand(DemandManage demand) {
        LambdaQueryWrapper<ProductProduction> wrapper = new LambdaQueryWrapper<ProductProduction>()
            .isNull(ProductProduction::getDemandId)
            .eq(ProductProduction::getIsDeliveryCheck, 0);

        // store_id 收窄：取同门店 + 未绑门店(store_id IS NULL)的库存（WMS-SHIP-STOREID-001）。
        // 打包时 store_id 可选，production.store_id 为 NULL 的库存不应被门店 demand 漏掉（§3.2「无待清点产品」根因之一）
        // → 放行待本次清点时绑定（confirmCheck 回写 demand_id + shipment.store_id 取 demand.store_id）。
        if (demand.getStoreId() != null) {
            wrapper.and(w -> w.eq(ProductProduction::getStoreId, demand.getStoreId())
                              .or().isNull(ProductProduction::getStoreId));
        }

        // 业态收窄：命中映射 → 仅取所属产品 belong_type ∈ 集合 的 production；
        // 未命中（other/空）→ 兜底放宽，不按业态过滤（仅 store_id + demand_id IS NULL）。
        Set<String> belongTypes = DEMAND_TYPE_TO_BELONG_TYPES.get(demand.getProductType());
        if (belongTypes != null) {
            List<Long> productIds = productInfoMapper.selectList(
                    new LambdaQueryWrapper<ProductInfo>()
                        .select(ProductInfo::getId)
                        .in(ProductInfo::getBelongType, belongTypes))
                .stream().map(ProductInfo::getId).toList();
            if (productIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(ProductProduction::getProductId, productIds);
        }

        return productProductionMapper.selectList(wrapper.orderByDesc(ProductProduction::getProduceDate));
    }

    /**
     * production 实体 → 轻量 VO（填 product_name + location_name，避免 mp 只看到 snowflake ID）。
     */
    private List<AvailableProductionVo> toAvailableProductionVos(List<ProductProduction> productions) {
        if (productions.isEmpty()) {
            return List.of();
        }
        Map<Long, String> productNameMap = loadProductNameMap(productions);
        Map<Long, String> locationNameMap = loadLocationNameMap(productions);
        return productions.stream().map(p -> {
            AvailableProductionVo vo = new AvailableProductionVo();
            vo.setId(p.getId());
            vo.setProduceNo(p.getProduceNo());
            vo.setProduceDate(p.getProduceDate());
            vo.setProductId(p.getProductId());
            vo.setProductName(productNameMap.get(p.getProductId()));
            vo.setProduceQuantity(p.getProduceQuantity());
            vo.setProduceLocation(p.getProduceLocation());
            vo.setProduceLocationName(p.getProduceLocation() == null
                ? null : locationNameMap.get(p.getProduceLocation()));
            vo.setEarNo(p.getEarNo());
            vo.setWhiteBarId(p.getWhiteBarId());
            vo.setDemandId(p.getDemandId());
            return vo;
        }).toList();
    }

    /**
     * 查 SHIPPABLE 状态的 demand。{@code storeId} 非空时收窄到单门店；为空时全门店（门店列表用）。
     */
    private List<DemandManage> loadShippableDemands(Long storeId) {
        List<String> shippableCodes = SHIPPABLE_DEMAND_STATUSES.stream()
            .map(DemandStatus::name).toList();
        return demandMapper.selectList(new LambdaQueryWrapper<DemandManage>()
            .in(DemandManage::getDemandStatus, shippableCodes)
            .eq(storeId != null, DemandManage::getStoreId, storeId)
            .orderByDesc(DemandManage::getDemandDate));
    }

    /**
     * 批量取门店名（StoreMapper 跨域 in 查，无 N+1；参 TraceCodeAdminServiceImpl 范式）。
     */
    private Map<Long, String> loadStoreNameMap(Set<Long> storeIds) {
        if (storeIds.isEmpty()) {
            return Map.of();
        }
        return storeMapper.selectList(new LambdaQueryWrapper<Store>()
                .select(Store::getId, Store::getStoreName)
                .in(Store::getId, storeIds))
            .stream().collect(Collectors.toMap(Store::getId, Store::getStoreName, (a, b) -> a,
                LinkedHashMap::new));
    }

    private LambdaQueryWrapper<Shipment> buildQueryWrapper(ShipmentQuery query) {
        LambdaQueryWrapper<Shipment> w = new LambdaQueryWrapper<>();
        if (query == null) {
            return w.orderByDesc(Shipment::getId);
        }
        w.like(StringUtils.isNotBlank(query.getShipmentNo()), Shipment::getShipmentNo, query.getShipmentNo())
            .eq(query.getDemandId() != null, Shipment::getDemandId, query.getDemandId())
            .eq(StringUtils.isNotBlank(query.getProductType()), Shipment::getProductType, query.getProductType())
            .eq(query.getStoreId() != null, Shipment::getStoreId, query.getStoreId())
            .eq(StringUtils.isNotBlank(query.getShipmentStatus()),
                Shipment::getShipmentStatus, query.getShipmentStatus())
            .ge(query.getShipDateFrom() != null, Shipment::getShipDate, query.getShipDateFrom())
            .le(query.getShipDateTo() != null, Shipment::getShipDate, query.getShipDateTo())
            .eq(query.getCheckerId() != null, Shipment::getCheckerId, query.getCheckerId())
            .orderByDesc(Shipment::getId);
        return w;
    }

    private void fillStoreNames(List<ShipmentVo> records) {
        // doc/06 admin 端门店名 ruoyi 自带 t_md_store 由 djs store 域管，本 ticket 不引依赖 — 留空字段，
        // admin 端字典 / picker 自行解析；如需 join，下游 D13 STR 域 listener 接管。
        // 这里仅占位避免 null 字符串。
        if (records == null || records.isEmpty()) {
            return;
        }
        // 无操作：storeName 由 admin 端列表渲染时 lookup picker，避免跨域 mapper 依赖。
    }

    private Map<Long, String> loadProductNameMap(List<ProductProduction> productions) {
        List<Long> productIds = productions.stream()
            .map(ProductProduction::getProductId).filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<ProductInfo> infos = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds));
        return infos.stream().collect(
            Collectors.toMap(ProductInfo::getId, ProductInfo::getProductName, (a, b) -> a));
    }

    private Map<Long, String> loadLocationNameMap(List<ProductProduction> productions) {
        List<Long> locationIds = productions.stream()
            .map(ProductProduction::getProduceLocation).filter(Objects::nonNull).distinct().toList();
        if (locationIds.isEmpty()) {
            return Map.of();
        }
        List<LocationInfo> locations = locationInfoMapper.selectList(
            new LambdaQueryWrapper<LocationInfo>().in(LocationInfo::getId, locationIds));
        return locations.stream().collect(
            Collectors.toMap(LocationInfo::getId, LocationInfo::getLocationName, (a, b) -> a));
    }
}
