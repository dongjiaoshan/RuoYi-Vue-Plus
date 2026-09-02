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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
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
     * 「今日」算法时区（D-FIX-24 决策 #6a 发货月台当天过滤）：不依赖 DB CURDATE() 时区，
     * 避免部署到非 UTC+8 实例时「今日」偏移埋雷。
     */
    private static final ZoneId SHIP_TODAY_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 可发成品的打包日回看窗口（天）—— V6 row27 配套。
     *
     * <p>「今天可以对明天的需求打包」意味着成品的 {@code produce_date} 会早于它要履约的那条需求的
     * {@code demand_date}。可发清单若仍只认「当天打包」，那批提前打的货第二天就凭空消失。
     * 取 1 天（= 昨天和今天）刚好覆盖「提前一天打包」这个甲方明说的场景；再往前的陈货不放进来，
     * 免得未绑 demand 的旧成品被今天的需求匹配走、造成「列表有货却发不出车」。</p>
     */
    private static final long SHIPPABLE_PRODUCE_LOOKBACK_DAYS = 1L;

    /**
     * stock_flow.flow_type — 发货出库（dict djs_flow_type 已 D9 seed）。
     */
    private static final String FLOW_TYPE_SHIP_OUT = "ship_out";
    /** 出库去向：发货月台（FIX-WMS-FLOWDICT-001，发货出库固定去向）。 */
    private static final String STOCK_OUT_DEST_SHIP_DOCK = "ship_dock";

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

    /** SHIPPABLE 状态的 demand_status 字符串码（demand.demandStatus 存的是枚举名）—— 门店列表种类口径对齐详情用。 */
    private static final Set<String> SHIPPABLE_STATUS_CODES = SHIPPABLE_DEMAND_STATUSES.stream()
        .map(DemandStatus::name).collect(Collectors.toUnmodifiableSet());

    /**
     * 发货月台门店列表展示状态集：在可发货白名单基础上 + COMPLETED（当天已发货完成），
     * 让「当天有需求且已发货」的门店也进列表（状态显「已发货」）。仅用于 listPendingStores 展示，
     * 不影响发货动作校验（动作仍只认 SHIPPABLE_DEMAND_STATUSES）。
     */
    private static final Set<DemandStatus> STORE_LIST_DEMAND_STATUSES = Set.of(
        DemandStatus.CONFIRMED,
        DemandStatus.IN_PRODUCTION,
        DemandStatus.PARTIAL_SHIPPED,
        DemandStatus.COMPLETED
    );

    /** 礼盒组件（deliver_dest='gift'）：预留给礼盒打包消耗，不出现在发货月台。 */
    private static final String DELIVER_DEST_GIFT = "gift";

    /** 后台出库（deliver_dest='warehouse_out'）：矿山/厨房等直接来仓库拿走，拿走即终态，不出现在发货月台。 */
    private static final String DELIVER_DEST_WAREHOUSE_OUT = "warehouse_out";

    /**
     * 「按重量计量」的产品单位集合（小写）——{@link #producedCopies} 据此决定生产量取
     * {@code produce_quantity} 公斤数还是按件计 1。
     *
     * <p>成员与 {@code ProductProductionServiceImpl.isKgUnit} 一致：打包扣需求（KG 扣重量 / 其余恒扣 1 份）
     * 与本处生产量必须同一把尺，否则满足率的分子分母量纲会错位。只收 kg 量纲——{@code produce_quantity}
     * 全链路以 kg 落库，吨 / 斤等其他重量单位直接取原值会量纲错位，不纳入。</p>
     */
    private static final Set<String> WEIGHT_PRODUCT_UNITS = Set.of("kg", "公斤");

    /** 需求满足率中间比值精度（最终结果 scale=2）。 */
    private static final int RATE_CALC_SCALE = 6;
    private static final BigDecimal RATE_PERCENT = BigDecimal.valueOf(100);
    /** 无可算种类时的满足率（0.00）。 */
    private static final BigDecimal RATE_ZERO = BigDecimal.ZERO.setScale(2);

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

        // 1b. 出车发货「全有或全无」：需求未全部满足（已打包 shipped_count < demand_quantity）禁止出车，
        //     杜绝部分发货（Kevin 2026-06-25）。需求扣减在打包时完成，shipped_count = 已备货量。
        BigDecimal shipped = demand.getShippedCount() == null ? BigDecimal.ZERO : demand.getShippedCount();
        BigDecimal need = demand.getDemandQuantity() == null ? BigDecimal.ZERO : demand.getDemandQuantity();
        if (need.signum() > 0 && shipped.compareTo(need) < 0) {
            throw new ServiceException(I18nMessages.t("shipment.demand.not_fully_satisfied",
                demand.getDemandNo(),
                shipped.stripTrailingZeros().toPlainString(),
                need.stripTrailingZeros().toPlainString()), 400);
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
        //    发货重量 serverTotal = Σ 本次清点 production 的实际成品量(kg)，仅用于发货记录 ship_quantity。
        //    bo.totalQuantity 是前端自报值，仅作展示/单位校验，不作记账依据。
        BigDecimal serverTotal = productions.stream()
            .map(p -> p.getProduceQuantity() == null ? BigDecimal.ZERO : p.getProduceQuantity())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (bo.getTotalQuantity() != null && bo.getTotalQuantity().compareTo(serverTotal) != 0) {
            log.warn("[WMS-SHIP-001] confirmCheck 前端自报量 {} 与服务端清点合计 {} 不一致，按服务端量记账",
                bo.getTotalQuantity().toPlainString(), serverTotal.toPlainString());
        }
        // 需求履约份数 = 本次清点的成品「件数」（每件打包=1 份，对齐门店按份下单）。demand.shipped_count
        // 累加这个份数（整数），与 demand_quantity 同量纲——绝不能用 serverTotal(kg)：份与 kg 串味会让
        // 需求量(=demand_quantity-shipped_count) 算出碎值（如 3.292），且 kg≥份 时状态机误判「完成」。
        BigDecimal shippedCopies = BigDecimal.valueOf(productions.size());
        Shipment shipment = new Shipment();
        shipment.setShipmentNo(bizCodeGenerator.generate(BizCodeType.SHIP_NO, Map.of()));
        shipment.setDemandId(demand.getId());
        shipment.setProductType(demand.getProductType());
        shipment.setStoreId(demand.getStoreId());
        shipment.setShipDate(java.time.LocalDate.now());
        shipment.setShipQuantity(serverTotal);
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

        // 5. INSERT stock_flow（出库流水，按 production 行逐条记 — 保留细颗粒追溯）。
        //    row42：生产产品不入库 → 发货不扣 location_stock（成品账走 product_production），
        //    库存总览回放对 ship_out 单列「已发货」、不计期初/出库/期末。
        for (ProductProduction p : productions) {
            BigDecimal qty = p.getProduceQuantity() == null ? BigDecimal.ZERO : p.getProduceQuantity();
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
            // 发货出库去向固定为发货月台（FIX-WMS-FLOWDICT-001，前端只读不可改）
            flow.setStockOutDest(STOCK_OUT_DEST_SHIP_DOCK);
            // change_num 带符号（出库为负，doc/11 §2.3 R9）；change_quantity 恒正绝对值
            flow.setChangeNum(qty.negate());
            flow.setChangeQuantity(qty);
            flow.setEarNo(p.getEarNo());
            flow.setOperatorId(userId);
            flow.setRemark("发货 → store_id=" + demand.getStoreId()
                + " shipment_no=" + shipment.getShipmentNo()
                + " produce_no=" + p.getProduceNo());
            stockFlowMapper.insert(flow);
        }

        // 6. publishEvent — D14 CROSS-FLOW-003 listener 消费触发 demand.shipped_count 累加 + transition
        //    传 shippedCopies（份数，非 kg）：demand 按份履约，shipped_count 与 demand_quantity 同量纲。
        //    带 userId（=checkerId 发货确认人）：AFTER_COMMIT 阶段 listener 无 sa-token 上下文，
        //    需显式传 operator 给 demand transition 做审计，否则 resolveOperator(null) 抛 demand.operator.required
        eventPublisher.publishEvent(new ShipmentConfirmedEvent(
            this, shipment.getId(), demand.getId(), shippedCopies, userId));

        log.info("[WMS-SHIP-001] confirmCheck shipmentId={} demandId={} productionCount={} qty={}",
            shipment.getId(), demand.getId(), productions.size(), serverTotal.toPlainString());

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
        // 1. 扫当天展示状态的 demand（SHIPPABLE + COMPLETED，门店列表数据驱动单位是需求单，非 production）。
        List<DemandManage> demands = loadStoreListDemands(null);
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
        // 3. 批量填门店名 + 批量取各店可发成品份数（两处均无 N+1：门店数 × 需求数不再各查一次）。
        Map<Long, String> storeNameMap = loadStoreNameMap(byStore.keySet());
        Map<Long, Map<Long, BigDecimal>> producedByStore =
            loadProducedCopies(byStore.keySet(), collectShippableProductIds(demands));
        // 4. 每门店算待发需求数 + 待发产品种类数 + 总量 + 需求满足率。
        List<ShipStoreVo> list = new ArrayList<>(byStore.size());
        byStore.forEach((storeId, storeDemands) -> list.add(buildStoreVo(
            storeId, storeNameMap.get(storeId), storeDemands,
            producedByStore.getOrDefault(storeId, Map.of()))));
        // 5. 发货月台只展示「当天还有货要发」的门店：产品种类数=0（当天需求已全部发完 COMPLETED，
        //    或无 SHIPPABLE 待发需求）的门店不进列表，避免「0 种·已发货」的空壳卡占屏（流程性问题 row187）。
        list.removeIf(vo -> vo.getProductKindCount() <= 0);
        // 6. 稳定排序：待发需求多的门店在前，其次按门店名。
        list.sort(Comparator.comparingInt(ShipStoreVo::getPendingDemandCount).reversed()
            .thenComparing(v -> v.getStoreName() == null ? "" : v.getStoreName()));
        return list;
    }

    @Override
    public ShipStoreVo queryStoreSummary(Long storeId) {
        if (storeId == null) {
            throw new ServiceException(I18nMessages.t("shipment.store.id_required"), 400);
        }
        String storeName = loadStoreNameMap(Set.of(storeId)).get(storeId);
        List<DemandManage> storeDemands = loadStoreListDemands(storeId);
        if (storeDemands.isEmpty()) {
            ShipStoreVo vo = new ShipStoreVo();
            vo.setStoreId(storeId);
            vo.setStoreName(storeName);
            vo.setPendingQuantity(BigDecimal.ZERO);
            vo.setShipStatus("待发货");
            vo.setSatisfyRate(RATE_ZERO);
            return vo;
        }
        Map<Long, Map<Long, BigDecimal>> producedByStore =
            loadProducedCopies(Set.of(storeId), collectShippableProductIds(storeDemands));
        return buildStoreVo(storeId, storeName, storeDemands,
            producedByStore.getOrDefault(storeId, Map.of()));
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
            // 需求单自身产品身份：让「已确认需求但未生产（无可发 production）」的产品在门店货物里也成卡展示
            // （流程性问题 row6：当日所有已确认需求的产品均要显示产品及需求量）。
            vo.setProductId(d.getProductId());
            vo.setProductName(d.getProductName());
            vo.setProductSpec(d.getProductSpec());
            vo.setProductUnit(d.getProductUnit());
            vo.setAvailableProductions(toAvailableProductionVos(findAvailableProductionsForDemand(d)));
            return vo;
        }).toList();
    }

    // ---------- private helpers ----------

    /**
     * SHIP-DEMANDID-001 核心匹配：按 demand 的 product_id + store_id 筛出"可发的未分配库存"
     * （{@code demand_id IS NULL AND is_delivery_check=0}）。listAvailableProductions /
     * listStorePendingDemands 共用，条件明细见 {@link #availableProductionWrapper}。
     */
    private List<ProductProduction> findAvailableProductionsForDemand(DemandManage demand) {
        return productProductionMapper.selectList(availableProductionWrapper(
            demand.getStoreId() == null ? List.of() : List.of(demand.getStoreId()),
            demand.getProductId() == null ? List.of() : List.of(demand.getProductId())));
    }

    /**
     * 「可发成品」匹配条件的单一真相源（单 demand 查询与门店批量聚合共用，杜绝两处口径漂移）。
     *
     * <p><b>V6 row27 起改为「今天及最近 N 天打包」</b>（{@link #SHIPPABLE_PRODUCE_LOOKBACK_DAYS}）。
     * 原来钉死 {@code produce_date = 今日}，与当时「打包只认当天需求」同口径。row27 放开成「今天可以对明天的
     * 需求打包」之后，那条钉死会造成：8/6 为 8/7 的需求打的包，8/6 发不掉（月台按 demand_date=today 列门店）、
     * 到了 8/7 又因 {@code produce_date=8/6 ≠ today} 被过滤掉 —— 需求 shipped_count 已经扣满，
     * 可发清单却是空的，「备齐/满足率」判定直接错乱。</p>
     *
     * <p>回看窗口取有限天数而不是完全不限：完全不限会把陈年未发、未绑 demand 的成品翻出来匹配今天的需求，
     * 正是原注释担心的「列表有货却发不出车」。窗口取 {@code SHIPPABLE_PRODUCE_LOOKBACK_DAYS} 天，
     * 覆盖「提前一天打包」这个甲方明确要的场景，同时把更早的陈货挡在外面。</p>
     *
     * @param storeIds   门店收窄：取同门店 + 未绑门店(store_id IS NULL)的库存（WMS-SHIP-STOREID-001）。
     *                   打包时 store_id 可选，production.store_id 为 NULL 的库存不应被门店 demand 漏掉
     *                   （§3.2「无待清点产品」根因之一）→ 放行待清点时绑定（confirmCheck 回写 demand_id
     *                   + shipment.store_id 取 demand.store_id）。空集合 = 不按门店收窄。
     * @param productIds 产品收窄：demand 只由「同款产品」产出履约（Kevin 2026-07-07）—— 精确匹配 product_id，
     *                   不按 belong_type 族放大。白条需求只由同款白条产出履约、不再吃分割猪肉；果蔬等同族
     *                   不同产品不串味。这样「可发清单(生产量)」与「shipped_count 扣减(备齐/出车判定)」同口径
     *                   （扣减走 deductDemandOnPack 精确 product_id），杜绝生产量按族虚高、shipped_count
     *                   按精确扣不到 → 备齐永不满足「无法出车」的错位（row3/row5）。空集合（产品已删/未绑）
     *                   = 兜底放宽，不按产品收窄。
     */
    private LambdaQueryWrapper<ProductProduction> availableProductionWrapper(Collection<Long> storeIds,
                                                                            Collection<Long> productIds) {
        LocalDate today = LocalDate.now(SHIP_TODAY_ZONE);
        LocalDate produceFrom = today.minusDays(SHIPPABLE_PRODUCE_LOOKBACK_DAYS);
        LambdaQueryWrapper<ProductProduction> wrapper = new LambdaQueryWrapper<ProductProduction>()
            .isNull(ProductProduction::getDemandId)
            .eq(ProductProduction::getIsDeliveryCheck, 0)
            .apply("DATE(produce_date) >= {0}", produceFrom.toString())
            .apply("DATE(produce_date) <= {0}", today.toString())
            // 发送位置=礼盒的成品是礼盒组件（预留给礼盒打包消耗），不出现在发货月台（礼盒澄清 2026-06-25）。
            // 发送位置=后台出库的成品是矿山/厨房/劲牌等直接从仓库拿走的货，拿走即终态，同样不进发货月台
            // （V6 row115：它 store_id 为空，被下面那条「同门店 OR 未绑门店」当通用件捞进门店可发清单 →
            //  门店卡上「生产量」凭空多一件、需求满足率虚高，甚至能被出车发货发给门店）。
            // deliver_dest 为 NULL（默认发货月台）或非这两个值才可直接发货。
            .and(w -> w.isNull(ProductProduction::getDeliverDest)
                       .or().notIn(ProductProduction::getDeliverDest,
                                   DELIVER_DEST_GIFT, DELIVER_DEST_WAREHOUSE_OUT));
        if (storeIds != null && !storeIds.isEmpty()) {
            wrapper.and(w -> w.in(ProductProduction::getStoreId, storeIds)
                              .or().isNull(ProductProduction::getStoreId));
        }
        if (productIds != null && !productIds.isEmpty()) {
            wrapper.in(ProductProduction::getProductId, productIds);
        }
        return wrapper.orderByDesc(ProductProduction::getProduceDate);
    }

    /**
     * 单门店聚合 VO（门店列表卡 + 门店货物页头共用，两端读同一个后端算法，杜绝口径漂移）。
     *
     * @param producedByProduct 该门店可发成品的生产量（product_id → 生产量），口径同 mp 门店货物卡「生产量」
     *                          （kg 产品按公斤、按件产品每条计 1，见 {@link #producedCopies}）
     */
    private ShipStoreVo buildStoreVo(Long storeId, String storeName, List<DemandManage> storeDemands,
                                     Map<Long, BigDecimal> producedByProduct) {
        ShipStoreVo vo = new ShipStoreVo();
        vo.setStoreId(storeId);
        vo.setStoreName(storeName);
        vo.setPendingDemandCount(storeDemands.size());
        // 产品种类数 = 当日所有 SHIPPABLE 需求的产品种类（按 demand.product_id 去重），
        // 含「已确认但未生产」的产品（流程性问题 row6：详情页 displayGoods 现也按需求全量展示，两处同口径）。
        // COMPLETED 已发货 demand 不计入（与详情 loadShippableDemands 一致 → 已发完门店显 0 种）。
        Map<Long, BigDecimal> needByProduct = sumNeedByProduct(storeDemands);
        vo.setProductKindCount(needByProduct.size());
        vo.setSatisfyRate(calcSatisfyRate(needByProduct, producedByProduct));
        vo.setPendingQuantity(storeDemands.stream()
            .map(DemandManage::getDemandQuantity).filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        // 发货日期 = 该门店当天 demand 的业务日期（列表已过滤 demand_date=今天，取 max 兜底，客户主诉求字段 #201）。
        vo.setShipDate(storeDemands.stream()
            .map(DemandManage::getDemandDate).filter(Objects::nonNull)
            .max(LocalDate::compareTo).orElse(null));
        // 门店当天发货状态二态：出车发货「全有或全无」，无部分发货中间态。
        // 全部 demand COMPLETED → 已发货；否则 → 待发货。
        boolean allShipped = storeDemands.stream()
            .allMatch(d -> DemandStatus.COMPLETED.name().equals(d.getDemandStatus()));
        vo.setShipStatus(allShipped ? "已发货" : "待发货");
        return vo;
    }

    /**
     * 该门店当天各产品种类的需求量（product_id → Σ demand_quantity）。
     *
     * <p>只取 SHIPPABLE 状态 + 有 product_id 的 demand（与产品种类数同口径）。<b>先按 product_id 归并</b>
     * 是满足率算对的关键：同店同日同产品会有多行 demand，按 demand 行平均会把该产品的权重放大。</p>
     */
    private Map<Long, BigDecimal> sumNeedByProduct(List<DemandManage> storeDemands) {
        Map<Long, BigDecimal> needByProduct = new LinkedHashMap<>();
        for (DemandManage d : storeDemands) {
            if (!SHIPPABLE_STATUS_CODES.contains(d.getDemandStatus()) || d.getProductId() == null) {
                continue;
            }
            needByProduct.merge(d.getProductId(),
                d.getDemandQuantity() == null ? BigDecimal.ZERO : d.getDemandQuantity(),
                BigDecimal::add);
        }
        return needByProduct;
    }

    /**
     * 需求满足率 = 各产品种类 {@code min(1, 生产量 ÷ 需求量)} 的平均 × 100（scale=2 HALF_UP）。
     *
     * <p>需求量 ≤ 0 的种类分子分母都不计；无可算种类 → 0.00。</p>
     */
    private BigDecimal calcSatisfyRate(Map<Long, BigDecimal> needByProduct,
                                       Map<Long, BigDecimal> producedByProduct) {
        BigDecimal ratioSum = BigDecimal.ZERO;
        int kinds = 0;
        for (Map.Entry<Long, BigDecimal> e : needByProduct.entrySet()) {
            BigDecimal need = e.getValue();
            if (need == null || need.signum() <= 0) {
                continue;
            }
            kinds++;
            BigDecimal made = producedByProduct.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal ratio = made.divide(need, RATE_CALC_SCALE, RoundingMode.HALF_UP);
            // 单项满足率钳在 [0, 1]：上限 100%（需求已定），下限 0（生产量为负的冲销脏数据不产出负百分比）
            if (ratio.compareTo(BigDecimal.ONE) > 0) {
                ratio = BigDecimal.ONE;
            } else if (ratio.signum() < 0) {
                ratio = BigDecimal.ZERO;
            }
            ratioSum = ratioSum.add(ratio);
        }
        if (kinds == 0) {
            return RATE_ZERO;
        }
        return ratioSum.divide(BigDecimal.valueOf(kinds), RATE_CALC_SCALE, RoundingMode.HALF_UP)
            .multiply(RATE_PERCENT).setScale(2, RoundingMode.HALF_UP);
    }

    /** SHIPPABLE 需求涉及的产品 id 集（满足率生产量批量查询的收窄条件）。 */
    private Set<Long> collectShippableProductIds(List<DemandManage> demands) {
        return demands.stream()
            .filter(d -> SHIPPABLE_STATUS_CODES.contains(d.getDemandStatus()))
            .map(DemandManage::getProductId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    /**
     * 批量取各门店可发成品的「生产量」（store_id → product_id → 生产量），无 N+1：
     * 一次查出所有相关门店的可发成品，再在内存按门店归属分摊。
     *
     * <p>未绑门店（store_id IS NULL）的通用成品对每个门店都可发，故各门店都计入
     * ——与 {@link #findAvailableProductionsForDemand} 逐 demand 查的结果一致。</p>
     */
    private Map<Long, Map<Long, BigDecimal>> loadProducedCopies(Set<Long> storeIds, Set<Long> productIds) {
        Map<Long, Map<Long, BigDecimal>> result = new HashMap<>();
        if (storeIds.isEmpty() || productIds.isEmpty()) {
            return result;
        }
        List<ProductProduction> productions = productProductionMapper.selectList(
            availableProductionWrapper(storeIds, productIds));
        if (productions.isEmpty()) {
            return result;
        }
        Map<Long, ProductInfo> productMap = loadProductInfoMap(productions);
        for (ProductProduction p : productions) {
            if (p.getProductId() == null) {
                continue;
            }
            BigDecimal copies = producedCopies(p, productMap.get(p.getProductId()));
            if (p.getStoreId() == null) {
                storeIds.forEach(sid -> result.computeIfAbsent(sid, k -> new HashMap<>())
                    .merge(p.getProductId(), copies, BigDecimal::add));
            } else {
                result.computeIfAbsent(p.getStoreId(), k -> new HashMap<>())
                    .merge(p.getProductId(), copies, BigDecimal::add);
            }
        }
        return result;
    }

    /**
     * 单条成品的「生产量」——按<b>产品自身计量单位</b>取值，与 mp 门店货物卡「生产量」同口径
     * （Kevin 2026-07-29）：
     * <ul>
     *   <li>按重量计量的产品（{@code product_unit} ∈ {@link #WEIGHT_PRODUCT_UNITS}）→ 生产量就是
     *       {@code produce_quantity} 本身的公斤数（需求量同为 kg，两者同量纲，满足率才算得对）。</li>
     *   <li>按件计量的产品（份 / 件 / 盒 / 枚 / 袋 …）→ <b>一条打包记录恒计 1</b>：打包台一次提交 = 产出
     *       一件成品，与录入重量、{@code material_num} 单份规格都无关（与打包扣需求
     *       {@code resolveDemandDeductQty} 恒扣 1 份同源）。</li>
     * </ul>
     *
     * <p>产品主数据缺失（{@code info == null}）时无从判定单位，按件计 1（不拿重量当件数用）。</p>
     *
     * <p>与 admin 产品生产概览的 {@code ProductProductionMapper.selectGroupList} 生产量 SQL
     * （{@code CASE WHEN unit IN ('kg','公斤') THEN SUM(product_weight) ELSE COUNT(*) END}）同一口径 —
     * 两处必须一起改，否则 admin 与发货月台的满足率会对不上。</p>
     */
    private BigDecimal producedCopies(ProductProduction p, ProductInfo info) {
        if (info != null && isWeightUnit(info.getProductUnit())) {
            return p.getProduceQuantity() == null ? BigDecimal.ZERO : p.getProduceQuantity();
        }
        return BigDecimal.ONE;
    }

    /**
     * 产品计量单位是否「按重量（kg）」——生产量取 produce_quantity 原值而非计 1 件的判定依据。
     * 大小写与前后空白不敏感；空单位视为按件。
     */
    private static boolean isWeightUnit(String productUnit) {
        return productUnit != null && WEIGHT_PRODUCT_UNITS.contains(productUnit.trim().toLowerCase());
    }

    /**
     * production 实体 → 轻量 VO（填 product_name + location_name，避免 mp 只看到 snowflake ID）。
     */
    private List<AvailableProductionVo> toAvailableProductionVos(List<ProductProduction> productions) {
        if (productions.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductInfo> productMap = loadProductInfoMap(productions);
        // 原材料单位：按 product_info.product_material 自引用 FK 批量 IN 查关联原材料的 product_unit（无 N+1）。
        // 用于 mp 发货清单「产品总重」是否展示的判定——如干羊肚菌70g product_unit='盒'、原材料=干货 unit='kg'。
        Set<Long> materialIds = productMap.values().stream()
            .map(ProductInfo::getProductMaterial).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProductInfo> materialMap = loadProductInfoByIds(materialIds);
        Map<Long, String> locationNameMap = loadLocationNameMap(productions);
        return productions.stream().map(p -> {
            ProductInfo info = p.getProductId() == null ? null : productMap.get(p.getProductId());
            AvailableProductionVo vo = new AvailableProductionVo();
            vo.setId(p.getId());
            vo.setProduceNo(p.getProduceNo());
            vo.setProduceDate(p.getProduceDate());
            vo.setProductId(p.getProductId());
            vo.setProductName(info == null ? null : info.getProductName());
            vo.setBelongType(info == null ? null : info.getBelongType());
            vo.setProductUnit(info == null ? null : info.getProductUnit());
            vo.setMaterialUnit(resolveMaterialUnit(info, materialMap));
            vo.setMaterialNum(info == null ? null : info.getMaterialNum());
            vo.setProduceQuantity(p.getProduceQuantity());
            vo.setProductSpec(p.getProductSpec());
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
        // 一趟车装的就是「今天该发给这门店的货」：按业务日期 demand_date **精确等于**今天（非 create_time），
        // 今日按 Asia/Shanghai 算，避免部署到非 UTC+8 实例时跨日凌晨归错天（D-FIX-24 决策 #6a）。
        //
        // 上界必须钳死：出车是「全店备齐才发」（Kevin 2026-06-25，故意的门槛，配不齐不许漏着发）。
        // 门店次日需求实测在前一天 18:10-20:57 确认，而那批货当晚才打包 —— 一旦放进闸门，
        // 次日需求一确认整店就再也出不了车，一直锁到午夜。打包端的 >= today 不动：甲方 r27 要的是
        // 「今天可以对明天的需求**打包**」（提前备货），不是「今天可以把明天的货**发掉**」，两件事。
        //
        // 下界同样钳死：过期未发的单不回月台。车每天都在发（甲方 2026-09-02 确认），积压不是常态；
        // 偶发配不齐的单由 admin 需求管理人工取消关掉，不靠月台兜。
        LocalDate today = LocalDate.now(SHIP_TODAY_ZONE);
        return demandMapper.selectList(new LambdaQueryWrapper<DemandManage>()
            .in(DemandManage::getDemandStatus, shippableCodes)
            .eq(DemandManage::getDemandDate, today)
            .eq(storeId != null, DemandManage::getStoreId, storeId)
            .orderByDesc(DemandManage::getDemandDate));
    }

    /**
     * 门店列表 + 页头聚合用：日期口径同 {@link #loadShippableDemands}（严格当天）
     * + {@link #STORE_LIST_DEMAND_STATUSES}（含 COMPLETED，让当天已发完的门店也进列表显「已发货」）。
     * 供 {@code listPendingStores} 与 {@code queryStoreSummary} 用。
     *
     * <p><b>日期口径必须与 {@link #loadShippableDemands} 完全一致</b>：门店列表 / 页头满足率 / 发货清单
     * 是同一屏上的三个视图，页头若算「今天+明天」而清单只列今天，就会出现「满足率和清单对不上」的读数矛盾
     * ——发货月台已经因为同类不同源问题出过事故（页头 100% 而出车被拦，且页面上看不出是哪条卡住）。</p>
     */
    private List<DemandManage> loadStoreListDemands(Long storeId) {
        List<String> codes = STORE_LIST_DEMAND_STATUSES.stream()
            .map(DemandStatus::name).toList();
        LocalDate today = LocalDate.now(SHIP_TODAY_ZONE);
        return demandMapper.selectList(new LambdaQueryWrapper<DemandManage>()
            .in(DemandManage::getDemandStatus, codes)
            .eq(DemandManage::getDemandDate, today)
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
        boolean hasProductTypes = query.getProductTypes() != null && !query.getProductTypes().isEmpty();
        boolean hasShipmentStatuses = query.getShipmentStatuses() != null && !query.getShipmentStatuses().isEmpty();
        w.like(StringUtils.isNotBlank(query.getShipmentNo()), Shipment::getShipmentNo, query.getShipmentNo())
            .eq(query.getDemandId() != null, Shipment::getDemandId, query.getDemandId())
            .in(hasProductTypes, Shipment::getProductType, query.getProductTypes())
            .eq(!hasProductTypes && StringUtils.isNotBlank(query.getProductType()), Shipment::getProductType, query.getProductType())
            .eq(query.getStoreId() != null, Shipment::getStoreId, query.getStoreId())
            .in(hasShipmentStatuses, Shipment::getShipmentStatus, query.getShipmentStatuses())
            .eq(!hasShipmentStatuses && StringUtils.isNotBlank(query.getShipmentStatus()),
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

    /**
     * 批量取产品主数据（一次查 product_info，VO 填 productName / belongType / productUnit / materialNum）。
     * mp 发货清单据此定数量单位（kg/头/份/枚）+ 判定生产量取 produceQuantity 公斤数还是按件计 1。
     */
    private Map<Long, ProductInfo> loadProductInfoMap(List<ProductProduction> productions) {
        List<Long> productIds = productions.stream()
            .map(ProductProduction::getProductId).filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<ProductInfo> infos = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds));
        return infos.stream().collect(
            Collectors.toMap(ProductInfo::getId, i -> i, (a, b) -> a));
    }

    /**
     * 批量取产品主数据（按 id 集合，仅 select id + product_unit，供原材料单位回填）。
     */
    private Map<Long, ProductInfo> loadProductInfoByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .select(ProductInfo::getId, ProductInfo::getProductUnit)
                .in(ProductInfo::getId, ids))
            .stream().collect(Collectors.toMap(ProductInfo::getId, i -> i, (a, b) -> a));
    }

    /**
     * 解析产品的原材料计量单位：product_material 自引用 FK 非空且关联原材料存在 → 取原材料 product_unit；
     * 否则（无关联 / 原材料已删）回落自身 product_unit。
     */
    private String resolveMaterialUnit(ProductInfo info, Map<Long, ProductInfo> materialMap) {
        if (info == null) {
            return null;
        }
        Long materialId = info.getProductMaterial();
        if (materialId != null) {
            ProductInfo material = materialMap.get(materialId);
            if (material != null && material.getProductUnit() != null) {
                return material.getProductUnit();
            }
        }
        return info.getProductUnit();
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
