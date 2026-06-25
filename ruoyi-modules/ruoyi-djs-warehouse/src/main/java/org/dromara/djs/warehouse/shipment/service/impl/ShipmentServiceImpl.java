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
import org.dromara.djs.warehouse.check.service.IStockCheckService;
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
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
     * 「今日」算法时区（D-FIX-24 决策 #6a 发货月台当天过滤）：不依赖 DB CURDATE() 时区，
     * 避免部署到非 UTC+8 实例时「今日」偏移埋雷。
     */
    private static final ZoneId SHIP_TODAY_ZONE = ZoneId.of("Asia/Shanghai");

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
    private final LocationStockMapper locationStockMapper;
    private final StoreMapper storeMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final IStockCheckService stockCheckService;

    public ShipmentServiceImpl(ShipmentMapper baseMapper,
                               ProductProductionMapper productProductionMapper,
                               StockFlowMapper stockFlowMapper,
                               DemandManageMapper demandMapper,
                               ProductInfoMapper productInfoMapper,
                               LocationInfoMapper locationInfoMapper,
                               LocationStockMapper locationStockMapper,
                               StoreMapper storeMapper,
                               IBizCodeGenerator bizCodeGenerator,
                               ApplicationEventPublisher eventPublisher,
                               IStockCheckService stockCheckService) {
        super(baseMapper);
        this.productProductionMapper = productProductionMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.demandMapper = demandMapper;
        this.productInfoMapper = productInfoMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.locationStockMapper = locationStockMapper;
        this.storeMapper = storeMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.eventPublisher = eventPublisher;
        this.stockCheckService = stockCheckService;
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
        //    发货重量 serverTotal = Σ 本次清点 production 的实际成品量(kg)，仅用于发货记录 ship_quantity +
        //    下面逐 production 扣成品 location_stock（库存口径=kg）。bo.totalQuantity 是前端自报值，仅作
        //    展示/单位校验，不作记账依据。
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

        // 5. INSERT stock_flow（出库流水，按 production 行逐条记 — 保留细颗粒追溯）
        //    + 逐 production 扣成品冷库 location_stock（G6：发货是成品冷库账的唯一出口，
        //    否则成品 location_stock 只增不减 → 账面虚高）。
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
            // 发货出库去向固定为发货月台（FIX-WMS-FLOWDICT-001，前端只读不可改）
            flow.setStockOutDest(STOCK_OUT_DEST_SHIP_DOCK);
            flow.setChangeNum(p.getProduceQuantity());
            flow.setChangeQuantity(p.getProduceQuantity());
            flow.setEarNo(p.getEarNo());
            flow.setOperatorId(userId);
            flow.setRemark("发货 → store_id=" + demand.getStoreId()
                + " shipment_no=" + shipment.getShipmentNo()
                + " produce_no=" + p.getProduceNo());
            stockFlowMapper.insert(flow);

            deductProductionStock(p, userId);
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
            // 发货日期 = 该门店当天 demand 的业务日期（列表已过滤 demand_date=今天，取 max 兜底，客户主诉求字段 #201）。
            vo.setShipDate(storeDemands.stream()
                .map(DemandManage::getDemandDate).filter(Objects::nonNull)
                .max(LocalDate::compareTo).orElse(null));
            // 门店当天发货状态三态（允许多次发货 #50）：全 COMPLETED → 已发货；
            // 有 demand 已 COMPLETED/PARTIAL_SHIPPED 但未全发 → 部分发货；都未发 → 待发货。
            boolean allShipped = storeDemands.stream()
                .allMatch(d -> DemandStatus.COMPLETED.name().equals(d.getDemandStatus()));
            boolean anyShipped = storeDemands.stream()
                .anyMatch(d -> DemandStatus.COMPLETED.name().equals(d.getDemandStatus())
                    || DemandStatus.PARTIAL_SHIPPED.name().equals(d.getDemandStatus()));
            vo.setShipStatus(allShipped ? "已发货" : (anyShipped ? "部分发货" : "待发货"));
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
     * G6 发货扣成品冷库 location_stock（三链共有）：成品入冷库（pack 产 product_production 时入账）
     * 后，发货月台清点确认即从冷库剥离成品余量，否则成品 location_stock 只增不减 → 账面虚高。
     *
     * <p>库位解析：优先用 production.produce_location（打包落位）；为空则按 product_id 解析该产品库存
     * 最多的默认库位兜底（{@link LocationStockMapper#selectDefaultLocationByProduct}）。
     * 两者都为空（产品无任何 location_stock 行）→ 无可扣库位，log.warn 不阻断。</p>
     *
     * <p>扣减口径与 loss 同范式：affectedRows==0（账面已不足 / 库位无该产品行）时 log.warn 留痕、
     * <b>不抛异常</b>——账实倒挂是历史脏数据 / 跨日补登的常态，发货动作不应被库存账面阻断
     * （shipped_count 回写 + 状态机由 publishEvent listener 保证，与扣减解耦）。</p>
     */
    private void deductProductionStock(ProductProduction p, Long userId) {
        if (p.getProductId() == null) {
            log.warn("[WMS-SHIP-001] 发货扣库存跳过：production 无 product_id — produceNo={}", p.getProduceNo());
            return;
        }
        Long location = p.getProduceLocation();
        if (location == null) {
            location = locationStockMapper.selectDefaultLocationByProduct(p.getProductId());
        }
        if (location == null) {
            log.warn("[WMS-SHIP-001] 发货扣库存跳过：product 无库存库位可扣 — produceNo={} productId={}",
                p.getProduceNo(), p.getProductId());
            return;
        }
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的成品冷库禁出库（后端双保险，扣 location_stock 前置）
        stockCheckService.assertLocationUnlocked(location);
        int affected = locationStockMapper.deductByProductLocation(
            location, p.getProductId(), p.getProduceQuantity(), userId);
        if (affected == 0) {
            log.warn("[WMS-SHIP-001] 发货出库流水已记，但成品 location_stock 扣减失败（账面已不足）："
                    + "produceNo={} productId={} location={} qty={}",
                p.getProduceNo(), p.getProductId(), location, p.getProduceQuantity());
        }
    }

    /**
     * SHIP-DEMANDID-001 核心匹配：按 demand 的业态(belong_type) + store_id 筛出"可发的未分配库存"
     * （{@code demand_id IS NULL AND is_delivery_check=0}）。listAvailableProductions /
     * listStorePendingDemands 共用，避免逻辑复制。
     */
    private List<ProductProduction> findAvailableProductionsForDemand(DemandManage demand) {
        LambdaQueryWrapper<ProductProduction> wrapper = new LambdaQueryWrapper<ProductProduction>()
            .isNull(ProductProduction::getDemandId)
            .eq(ProductProduction::getIsDeliveryCheck, 0)
            // 发送位置=礼盒的成品是礼盒组件（预留给礼盒打包消耗），不出现在发货月台（礼盒澄清 2026-06-25）。
            // deliver_dest 为 NULL（默认发货月台）或非 'gift' 才可直接发货。
            .and(w -> w.isNull(ProductProduction::getDeliverDest)
                       .or().ne(ProductProduction::getDeliverDest, "gift"));

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
        Map<Long, String> belongTypeMap = loadProductBelongTypeMap(productions);
        Map<Long, String> locationNameMap = loadLocationNameMap(productions);
        return productions.stream().map(p -> {
            AvailableProductionVo vo = new AvailableProductionVo();
            vo.setId(p.getId());
            vo.setProduceNo(p.getProduceNo());
            vo.setProduceDate(p.getProduceDate());
            vo.setProductId(p.getProductId());
            vo.setProductName(productNameMap.get(p.getProductId()));
            vo.setBelongType(belongTypeMap.get(p.getProductId()));
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
        // mp 发货月台只看「当天有需求」的门店：按业务日期 demand_date = 今天过滤（非 create_time），
        // 今日按 Asia/Shanghai 算，避免部署到非 UTC+8 实例时跨日凌晨归错天（D-FIX-24 决策 #6a）。
        LocalDate today = LocalDate.now(SHIP_TODAY_ZONE);
        return demandMapper.selectList(new LambdaQueryWrapper<DemandManage>()
            .in(DemandManage::getDemandStatus, shippableCodes)
            .eq(DemandManage::getDemandDate, today)
            .eq(storeId != null, DemandManage::getStoreId, storeId)
            .orderByDesc(DemandManage::getDemandDate));
    }

    /**
     * 门店列表展示用：当天 + {@link #STORE_LIST_DEMAND_STATUSES}（含 COMPLETED 已发货）。仅 listPendingStores 用。
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

    /** 产品 id → belong_type 业态（mp 发货清单据此定数量单位 头/份）。 */
    private Map<Long, String> loadProductBelongTypeMap(List<ProductProduction> productions) {
        List<Long> productIds = productions.stream()
            .map(ProductProduction::getProductId).filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<ProductInfo> infos = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds)
                .select(ProductInfo::getId, ProductInfo::getBelongType));
        return infos.stream()
            .filter(i -> i.getBelongType() != null)
            .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getBelongType, (a, b) -> a));
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
