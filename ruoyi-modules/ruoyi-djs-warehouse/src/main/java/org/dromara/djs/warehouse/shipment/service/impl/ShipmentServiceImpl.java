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
import org.dromara.djs.warehouse.shipment.domain.vo.ShipmentVo;
import org.dromara.djs.warehouse.shipment.event.ShipmentConfirmedEvent;
import org.dromara.djs.warehouse.shipment.mapper.ShipmentMapper;
import org.dromara.djs.warehouse.shipment.service.IShipmentService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
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

    private final ProductProductionMapper productProductionMapper;
    private final StockFlowMapper stockFlowMapper;
    private final DemandManageMapper demandMapper;
    private final ProductInfoMapper productInfoMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public ShipmentServiceImpl(ShipmentMapper baseMapper,
                               ProductProductionMapper productProductionMapper,
                               StockFlowMapper stockFlowMapper,
                               DemandManageMapper demandMapper,
                               ProductInfoMapper productInfoMapper,
                               LocationInfoMapper locationInfoMapper,
                               IBizCodeGenerator bizCodeGenerator,
                               ApplicationEventPublisher eventPublisher) {
        super(baseMapper);
        this.productProductionMapper = productProductionMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.demandMapper = demandMapper;
        this.productInfoMapper = productInfoMapper;
        this.locationInfoMapper = locationInfoMapper;
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
            if (!Objects.equals(p.getDemandId(), bo.getDemandId())) {
                throw new ServiceException(
                    I18nMessages.t("shipment.production.demand_mismatch", p.getProduceNo()), 400);
            }
        }

        // 3. UPDATE product_production SET is_delivery_check=1（乐观锁）
        Date now = new Date();
        int affected = productProductionMapper.markDeliveryChecked(bo.getProductionIds(), now);
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
        eventPublisher.publishEvent(new ShipmentConfirmedEvent(
            this, shipment.getId(), demand.getId(), bo.getTotalQuantity()));

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
        List<ProductProduction> productions = productProductionMapper.selectList(
            new LambdaQueryWrapper<ProductProduction>()
                .eq(ProductProduction::getDemandId, demandId)
                .eq(ProductProduction::getIsDeliveryCheck, 0)
                .orderByDesc(ProductProduction::getProduceDate));
        if (productions.isEmpty()) {
            return List.of();
        }
        // 填 product_name + location_name（避免 mp 列表只看到 snowflake ID）
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

    // ---------- private helpers ----------

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
