package org.dromara.djs.warehouse.shipment.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.shipment.domain.Shipment;
import org.dromara.djs.warehouse.shipment.domain.bo.ShipmentCheckBo;
import org.dromara.djs.warehouse.shipment.domain.vo.AvailableProductionVo;
import org.dromara.djs.warehouse.shipment.domain.vo.ShipDemandVo;
import org.dromara.djs.warehouse.shipment.domain.vo.ShipStoreVo;
import org.dromara.djs.warehouse.shipment.event.ShipmentConfirmedEvent;
import org.dromara.djs.warehouse.shipment.mapper.ShipmentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ShipmentServiceImpl} 单元测试（WMS-SHIP-001）。
 *
 * <p>覆盖跨表事务一致性的 3 个核心场景：</p>
 * <ol>
 *   <li>happy path：demand 状态 CONFIRMED + 2 个未清点产品 → mark + insert shipment + insert 2 flow + publishEvent</li>
 *   <li>demand 状态非法（DRAFT）→ 抛 ServiceException + 任何 INSERT 都不发生</li>
 *   <li>并发清点冲突（affected < ids.size()）→ 抛 + 不写 shipment / stock_flow / publishEvent</li>
 * </ol>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ShipmentServiceImpl 单元测试")
class ShipmentServiceImplTest {

    @Mock
    private ShipmentMapper shipmentMapper;
    @Mock
    private ProductProductionMapper productProductionMapper;
    @Mock
    private StockFlowMapper stockFlowMapper;
    @Mock
    private DemandManageMapper demandMapper;
    @Mock
    private ProductInfoMapper productInfoMapper;
    @Mock
    private LocationInfoMapper locationInfoMapper;
    @Mock
    private StoreMapper storeMapper;
    @Mock
    private IBizCodeGenerator bizCodeGenerator;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private org.dromara.djs.warehouse.demand.service.IDemandStatusService demandStatusService;

    private ShipmentServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeAll
    static void initMpEntityCache() {
        // mock 路径下 service 内部的 LambdaQueryWrapper 仍会触发 TableInfoHelper 解析列名，
        // 纯单测无 Spring/MyBatis 上下文 → 预热 entity 的 lambda cache，否则报 "can not find lambda cache"。
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, ProductProduction.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, Shipment.class);
        TableInfoHelper.initTableInfo(assistant, StockFlow.class);
        TableInfoHelper.initTableInfo(assistant, DemandManage.class);
        TableInfoHelper.initTableInfo(assistant, Store.class);
    }

    @BeforeEach
    void setup() {
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(1L);

        service = new ShipmentServiceImpl(shipmentMapper, productProductionMapper, stockFlowMapper,
            demandMapper, productInfoMapper, locationInfoMapper, storeMapper,
            bizCodeGenerator, eventPublisher, demandStatusService);

        // 业务码 stub
        when(bizCodeGenerator.generate(eq(BizCodeType.SHIP_NO), anyMap())).thenReturn("S260610TEST0001");
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap()))
            .thenReturn("F260610OT0001", "F260610OT0002");
    }

    @AfterEach
    void cleanup() {
        if (loginHelperMock != null) {
            loginHelperMock.close();
        }
    }

    // ============== happy path ==============

    @Test
    @DisplayName("happy: demand CONFIRMED + 2 production 未清点 → mark + insert shipment + 2 flow + publishEvent")
    void confirmCheck_happyPath() {
        Long demandId = 100L;
        Long storeId = 9L;
        DemandManage demand = newDemand(demandId, storeId, DemandStatus.CONFIRMED);
        when(demandMapper.selectById(demandId)).thenReturn(demand);

        List<Long> productionIds = List.of(11L, 12L);
        // 两行成品各带 product_id + produce_location（ship_out 流水按 production 行逐条记）
        ProductProduction p1 = newProduction(11L, demandId, "PROD-001", new BigDecimal("3.000"));
        p1.setProductId(501L);
        p1.setProduceLocation(7001L);
        ProductProduction p2 = newProduction(12L, demandId, "PROD-002", new BigDecimal("4.500"));
        p2.setProductId(502L);
        p2.setProduceLocation(7002L);
        List<ProductProduction> productions = List.of(p1, p2);
        when(productProductionMapper.selectList(any())).thenReturn(productions);
        when(productProductionMapper.markDeliveryChecked(any(), any(), any())).thenReturn(2);

        ShipmentCheckBo bo = new ShipmentCheckBo();
        bo.setDemandId(demandId);
        bo.setProductionIds(productionIds);
        bo.setTotalQuantity(new BigDecimal("7.500"));
        bo.setShipUnit("kg");
        bo.setDeliverType(1);

        Long shipmentId = service.confirmCheck(bo);

        // 1. UPDATE markDeliveryChecked 调过 1 次（批量），且回写本次 demandId
        verify(productProductionMapper, times(1)).markDeliveryChecked(eq(productionIds), any(), eq(demandId));
        // 2. INSERT shipment 1 次
        ArgumentCaptor<Shipment> shipmentCap = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentMapper, times(1)).insert(shipmentCap.capture());
        Shipment captured = shipmentCap.getValue();
        assertThat(captured.getShipmentNo()).isEqualTo("S260610TEST0001");
        assertThat(captured.getStoreId()).isEqualTo(storeId);
        assertThat(captured.getShipmentStatus()).isEqualTo("shipped");
        assertThat(captured.getCheckerId()).isEqualTo(1L);
        // 3. INSERT stock_flow 2 次（按 production 逐条），change_num 带符号为负 / change_quantity 恒正
        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(2)).insert(flowCap.capture());
        StockFlow firstFlow = flowCap.getAllValues().get(0);
        assertThat(firstFlow.getInoutType()).isEqualTo("OT");
        assertThat(firstFlow.getFlowType()).isEqualTo("ship_out");
        assertThat(firstFlow.getChangeNum()).isEqualByComparingTo("-3.000");
        assertThat(firstFlow.getChangeQuantity()).isEqualByComparingTo("3.000");
        // 4. publishEvent 1 次
        ArgumentCaptor<ShipmentConfirmedEvent> eventCap =
            ArgumentCaptor.forClass(ShipmentConfirmedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCap.capture());
        assertThat(eventCap.getValue().getDemandId()).isEqualTo(demandId);
        // 事件携带履约「份数」(= 本次清点 production 件数 2)，非 kg —— 需求按份履约，与 demand_quantity 同量纲
        assertThat(eventCap.getValue().getShippedQuantity()).isEqualByComparingTo("2");
    }

    // ============== row42：生产产品不入库，发货不动货架账 ==============

    @Test
    @DisplayName("row42: produce_location 为空 → 流水 warehouse_id 留空；发货照常完成")
    void confirmCheck_noProduceLocation_doesNotBlock() {
        Long demandId = 100L;
        DemandManage demand = newDemand(demandId, 9L, DemandStatus.CONFIRMED);
        when(demandMapper.selectById(demandId)).thenReturn(demand);

        // production 无 produce_location（打包未落位）—— row42 生产产品不入库，发货本就不动货架账
        ProductProduction p = newProduction(11L, demandId, "PROD-001", new BigDecimal("2.500"));
        p.setProductId(501L);
        p.setProduceLocation(null);
        when(productProductionMapper.selectList(any())).thenReturn(List.of(p));
        when(productProductionMapper.markDeliveryChecked(any(), any(), any())).thenReturn(1);

        ShipmentCheckBo bo = new ShipmentCheckBo();
        bo.setDemandId(demandId);
        bo.setProductionIds(List.of(11L));
        bo.setTotalQuantity(new BigDecimal("2.500"));
        bo.setShipUnit("kg");
        bo.setDeliverType(1);

        Long shipmentId = service.confirmCheck(bo);

        // 发货照常：shipment 仍写、事件仍发（row42：不动 location_stock，货架账与发货解耦）
        verify(shipmentMapper, times(1)).insert(any(Shipment.class));
        verify(stockFlowMapper, times(1)).insert(any(StockFlow.class));
        verify(eventPublisher, times(1)).publishEvent(any(ShipmentConfirmedEvent.class));
    }

    @Test
    @DisplayName("edge: production 无 product_id（理论脏数据）→ 不抛；发货照常完成")
    void confirmCheck_noProductId_doesNotBlock() {
        Long demandId = 100L;
        DemandManage demand = newDemand(demandId, 9L, DemandStatus.CONFIRMED);
        when(demandMapper.selectById(demandId)).thenReturn(demand);

        // product_id 为空（理论脏数据）→ 流水 product_id 留空，不阻断发货
        ProductProduction p = newProduction(11L, demandId, "PROD-001", new BigDecimal("1.000"));
        p.setProductId(null);
        p.setProduceLocation(null);
        when(productProductionMapper.selectList(any())).thenReturn(List.of(p));
        when(productProductionMapper.markDeliveryChecked(any(), any(), any())).thenReturn(1);

        ShipmentCheckBo bo = new ShipmentCheckBo();
        bo.setDemandId(demandId);
        bo.setProductionIds(List.of(11L));
        bo.setTotalQuantity(new BigDecimal("1.000"));
        bo.setShipUnit("kg");
        bo.setDeliverType(1);

        service.confirmCheck(bo);

        verify(shipmentMapper, times(1)).insert(any(Shipment.class));
        verify(stockFlowMapper, times(1)).insert(any(StockFlow.class));
    }

    // ============== demand 状态非法 ==============

    @Test
    @DisplayName("error: demand DRAFT → 抛 + 不写任何表")
    void confirmCheck_demandDraft_rejected() {
        DemandManage demand = newDemand(100L, 9L, DemandStatus.DRAFT);
        when(demandMapper.selectById(100L)).thenReturn(demand);

        ShipmentCheckBo bo = new ShipmentCheckBo();
        bo.setDemandId(100L);
        bo.setProductionIds(List.of(11L));
        bo.setTotalQuantity(new BigDecimal("3.0"));
        bo.setShipUnit("kg");
        bo.setDeliverType(1);

        assertThatThrownBy(() -> service.confirmCheck(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("DRAFT");

        verify(productProductionMapper, never()).markDeliveryChecked(anyList(), any(), any());
        verify(shipmentMapper, never()).insert(any(Shipment.class));
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(eventPublisher, never()).publishEvent(any(ShipmentConfirmedEvent.class));
    }

    // ============== 出车发货全有或全无：需求未满足拒绝 ==============

    @Test
    @DisplayName("error: 需求未全部满足（shipped_count < demand_quantity）→ 抛 not_fully_satisfied + 不写任何表")
    void confirmCheck_demandNotFullySatisfied_rejected() {
        Long demandId = 100L;
        DemandManage demand = newDemand(demandId, 9L, DemandStatus.CONFIRMED);
        demand.setDemandNo("XQ260625001");
        demand.setDemandQuantity(new BigDecimal("10"));
        demand.setShippedCount(new BigDecimal("3"));  // 仅备 3 / 需求 10 → 未满足
        when(demandMapper.selectById(demandId)).thenReturn(demand);

        ShipmentCheckBo bo = new ShipmentCheckBo();
        bo.setDemandId(demandId);
        bo.setProductionIds(List.of(11L));
        bo.setTotalQuantity(new BigDecimal("3.0"));
        bo.setShipUnit("份");
        bo.setDeliverType(1);

        assertThatThrownBy(() -> service.confirmCheck(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("not_fully_satisfied");

        // 拒绝出车：production 不查不标、shipment / flow / event 全不写
        verify(productProductionMapper, never()).markDeliveryChecked(anyList(), any(), any());
        verify(shipmentMapper, never()).insert(any(Shipment.class));
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(eventPublisher, never()).publishEvent(any(ShipmentConfirmedEvent.class));
    }

    @Test
    @DisplayName("V6-R160: 未满足但 force=true → 放行缺量发车（不再抛 not_fully_satisfied）")
    void confirmCheck_notFullySatisfiedButForced_proceeds() {
        Long demandId = 100L;
        DemandManage demand = newDemand(demandId, 9L, DemandStatus.CONFIRMED);
        demand.setDemandNo("XQ260625001");
        demand.setDemandQuantity(new BigDecimal("10"));
        demand.setShippedCount(new BigDecimal("3"));   // 仅备 3 / 需求 10
        when(demandMapper.selectById(demandId)).thenReturn(demand);

        ShipmentCheckBo bo = new ShipmentCheckBo();
        bo.setDemandId(demandId);
        bo.setProductionIds(List.of(11L));
        bo.setTotalQuantity(new BigDecimal("3.0"));
        bo.setShipUnit("份");
        bo.setDeliverType(1);
        bo.setForce(true);                             // ← 工人已在 mp 上确认「仍要发车」

        // 未满足这道闸不再拦；后续流程照常（本用例只断言没被 1b 挡下）
        assertThatThrownBy(() -> service.confirmCheck(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageNotContaining("not_fully_satisfied");
    }

    @Test
    @DisplayName("V6-R160: forceClose 只关没发满的活需求，发满的 / 已终态的 / 别家门店的一律不动")
    void forceCloseUnmetDemands_closesOnlyUnmetLiveOnes() {
        DemandManage unmet = newDemand(201L, 9L, DemandStatus.PARTIAL_SHIPPED);
        unmet.setStoreId(7L);
        unmet.setDemandQuantity(new BigDecimal("10"));
        unmet.setShippedCount(new BigDecimal("4"));
        DemandManage full = newDemand(202L, 9L, DemandStatus.PARTIAL_SHIPPED);
        full.setStoreId(7L);
        full.setDemandQuantity(new BigDecimal("5"));
        full.setShippedCount(new BigDecimal("5"));     // 已发满 → 不该被强制关
        DemandManage done = newDemand(203L, 9L, DemandStatus.COMPLETED);
        done.setStoreId(7L);
        done.setDemandQuantity(new BigDecimal("8"));
        done.setShippedCount(new BigDecimal("2"));     // 已是终态 → 幂等，不重复关
        DemandManage other = newDemand(204L, 9L, DemandStatus.CONFIRMED);
        other.setStoreId(99L);                          // 别家门店 → 不该被误伤
        other.setDemandQuantity(new BigDecimal("6"));
        other.setShippedCount(BigDecimal.ZERO);
        when(demandMapper.selectById(201L)).thenReturn(unmet);
        when(demandMapper.selectById(202L)).thenReturn(full);
        when(demandMapper.selectById(203L)).thenReturn(done);
        when(demandMapper.selectById(204L)).thenReturn(other);

        int closed = service.forceCloseUnmetDemands(7L, List.of(201L, 202L, 203L, 204L));

        assertThat(closed).isEqualTo(1);
        verify(demandStatusService).transition(eq(201L), eq(DemandEvent.COMPLETE), eq(1L), anyString());
        verify(demandStatusService, never()).transition(eq(202L), any(), any(), anyString());
        verify(demandStatusService, never()).transition(eq(203L), any(), any(), anyString());
        verify(demandStatusService, never()).transition(eq(204L), any(), any(), anyString());
    }

    @Test
    @DisplayName("happy: 需求全部满足（shipped_count >= demand_quantity）→ 放行出车")
    void confirmCheck_demandFullySatisfied_proceeds() {
        Long demandId = 100L;
        DemandManage demand = newDemand(demandId, 9L, DemandStatus.CONFIRMED);
        demand.setDemandQuantity(new BigDecimal("3"));
        demand.setShippedCount(new BigDecimal("3"));  // 已备齐
        when(demandMapper.selectById(demandId)).thenReturn(demand);

        ProductProduction p = newProduction(11L, demandId, "PROD-001", new BigDecimal("3.0"));
        when(productProductionMapper.selectList(any())).thenReturn(List.of(p));
        when(productProductionMapper.markDeliveryChecked(any(), any(), any())).thenReturn(1);

        ShipmentCheckBo bo = new ShipmentCheckBo();
        bo.setDemandId(demandId);
        bo.setProductionIds(List.of(11L));
        bo.setTotalQuantity(new BigDecimal("3.0"));
        bo.setShipUnit("份");
        bo.setDeliverType(1);

        service.confirmCheck(bo);

        verify(shipmentMapper, times(1)).insert(any(Shipment.class));
        verify(eventPublisher, times(1)).publishEvent(any(ShipmentConfirmedEvent.class));
    }

    // ============== 并发清点冲突 ==============

    @Test
    @DisplayName("error: markDeliveryChecked affected < ids.size → 抛并发冲突 + 不写 shipment / flow / event")
    void confirmCheck_concurrentConflict() {
        Long demandId = 100L;
        DemandManage demand = newDemand(demandId, 9L, DemandStatus.IN_PRODUCTION);
        when(demandMapper.selectById(demandId)).thenReturn(demand);

        List<Long> productionIds = List.of(11L, 12L);
        when(productProductionMapper.selectList(any())).thenReturn(List.of(
            newProduction(11L, demandId, "PROD-001", new BigDecimal("3.0")),
            newProduction(12L, demandId, "PROD-002", new BigDecimal("4.0"))
        ));
        // 模拟并发：只有 1 行被乐观锁更新成功
        when(productProductionMapper.markDeliveryChecked(any(), any(), any())).thenReturn(1);

        ShipmentCheckBo bo = new ShipmentCheckBo();
        bo.setDemandId(demandId);
        bo.setProductionIds(productionIds);
        bo.setTotalQuantity(new BigDecimal("7.0"));
        bo.setShipUnit("kg");
        bo.setDeliverType(1);

        assertThatThrownBy(() -> service.confirmCheck(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("concurrent_conflict");

        verify(shipmentMapper, never()).insert(any(Shipment.class));
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(eventPublisher, never()).publishEvent(any(ShipmentConfirmedEvent.class));
    }

    // ============== SHIP-DEMANDID-001：可用库存匹配 + 确认回写 ==============

    @Test
    @DisplayName("happy: listAvailableProductions 按业态(vegetable→belong_type) + demand_id IS NULL 返非空")
    void listAvailableProductions_byBelongType_returnsUnassignedStock() {
        Long demandId = 100L;
        Long storeId = 9L;
        DemandManage demand = newDemand(demandId, storeId, DemandStatus.CONFIRMED);
        demand.setProductType("vegetable");
        when(demandMapper.selectById(demandId)).thenReturn(demand);

        // belong_type=vegetable 的产品 → id 集（product_name 业务必填，loadProductInfoMap toMap 不容 null key）
        ProductInfo veg = new ProductInfo();
        veg.setId(501L);
        veg.setBelongType("vegetable");
        veg.setProductName("有机青菜");
        when(productInfoMapper.selectList(any())).thenReturn(List.of(veg));

        // 可用库存：demand_id=NULL（未分配）、未清点、属于上面的产品
        ProductProduction avail = newProduction(11L, null, "260610V0001", new BigDecimal("3.000"));
        avail.setProductId(501L);
        avail.setStoreId(storeId);
        when(productProductionMapper.selectList(any())).thenReturn(List.of(avail));

        List<AvailableProductionVo> result = service.listAvailableProductions(demandId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProduceNo()).isEqualTo("260610V0001");
        assertThat(result.get(0).getDemandId()).isNull();
    }

    @Test
    @DisplayName("V6 row115: 可发清单 WHERE 必须排掉 deliver_dest=warehouse_out（后台出库货不进门店发货月台）")
    void listAvailableProductions_excludesWarehouseOutProductions() {
        Long demandId = 101L;
        Long storeId = 9L;
        DemandManage demand = newDemand(demandId, storeId, DemandStatus.CONFIRMED);
        demand.setProductType("white_bar");
        demand.setProductId(601L);
        when(demandMapper.selectById(demandId)).thenReturn(demand);
        ProductInfo bar = new ProductInfo();
        bar.setId(601L);
        bar.setBelongType("white_bar");
        bar.setProductName("半扇");
        when(productInfoMapper.selectList(any())).thenReturn(List.of(bar));
        when(productProductionMapper.selectList(any())).thenReturn(List.of());

        service.listAvailableProductions(demandId);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductProduction>> captor =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(productProductionMapper).selectList(captor.capture());
        String sql = captor.getValue().getTargetSql();
        // 后台出库（矿山/厨房直接拿走）与礼盒组件一样，都不是门店可发的货
        assertThat(sql).contains("deliver_dest");
        assertThat(captor.getValue().getParamNameValuePairs().values())
            .contains("warehouse_out", "gift");
    }

    @Test
    @DisplayName("happy: confirmCheck 对 demand_id=NULL 的可用库存放行（不抛 mismatch）+ 回写 demandId")
    void confirmCheck_unassignedStock_bindsDemandId() {
        Long demandId = 100L;
        DemandManage demand = newDemand(demandId, 9L, DemandStatus.CONFIRMED);
        when(demandMapper.selectById(demandId)).thenReturn(demand);

        List<Long> productionIds = List.of(11L);
        // demand_id=NULL 的可用库存（关键：旧逻辑会判 NULL≠demandId 抛 mismatch）
        ProductProduction avail = newProduction(11L, null, "260610V0001", new BigDecimal("3.0"));
        when(productProductionMapper.selectList(any())).thenReturn(List.of(avail));
        when(productProductionMapper.markDeliveryChecked(any(), any(), any())).thenReturn(1);

        ShipmentCheckBo bo = new ShipmentCheckBo();
        bo.setDemandId(demandId);
        bo.setProductionIds(productionIds);
        bo.setTotalQuantity(new BigDecimal("3.0"));
        bo.setShipUnit("kg");
        bo.setDeliverType(1);

        service.confirmCheck(bo);

        // 回写：markDeliveryChecked 以本次 demandId 调用
        verify(productProductionMapper, times(1)).markDeliveryChecked(eq(productionIds), any(), eq(demandId));
        verify(shipmentMapper, times(1)).insert(any(Shipment.class));
    }

    @Test
    @DisplayName("edge: production 已绑别的 demand → confirmCheck 仍抛 demand_mismatch")
    void confirmCheck_boundToOtherDemand_throwsMismatch() {
        Long demandId = 100L;
        DemandManage demand = newDemand(demandId, 9L, DemandStatus.CONFIRMED);
        when(demandMapper.selectById(demandId)).thenReturn(demand);

        // 该 production 已绑 demand 200（≠ 本次 100）→ mismatch
        ProductProduction bound = newProduction(11L, 200L, "260610V0001", new BigDecimal("3.0"));
        when(productProductionMapper.selectList(any())).thenReturn(List.of(bound));

        ShipmentCheckBo bo = new ShipmentCheckBo();
        bo.setDemandId(demandId);
        bo.setProductionIds(List.of(11L));
        bo.setTotalQuantity(new BigDecimal("3.0"));
        bo.setShipUnit("kg");
        bo.setDeliverType(1);

        assertThatThrownBy(() -> service.confirmCheck(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("demand_mismatch");

        verify(productProductionMapper, never()).markDeliveryChecked(anyList(), any(), any());
        verify(shipmentMapper, never()).insert(any(Shipment.class));
    }

    // ============== D12X-MP-SHIPDOCK-IA-001：门店维度两级 IA 端点 ==============

    @Test
    @DisplayName("happy: listPendingStores 按 SHIPPABLE demand group store → 门店项含种类数 + 门店名")
    void listPendingStores_groupByStore_returnsAggregated() {
        // 2 门店：store 9 有 2 个 demand（productId 501/502 两种）、store 8 有 1 个 demand（productId 501）
        DemandManage d1 = newDemand(100L, 9L, DemandStatus.CONFIRMED);
        d1.setProductId(501L);
        d1.setDemandQuantity(new BigDecimal("10"));
        DemandManage d2 = newDemand(101L, 9L, DemandStatus.IN_PRODUCTION);
        d2.setProductId(502L);
        d2.setDemandQuantity(new BigDecimal("5"));
        DemandManage d3 = newDemand(102L, 8L, DemandStatus.CONFIRMED);
        d3.setProductId(501L);
        d3.setDemandQuantity(new BigDecimal("3"));
        when(demandMapper.selectList(any())).thenReturn(List.of(d1, d2, d3));

        Store s9 = newStore(9L, "城东店");
        Store s8 = newStore(8L, "城西店");
        when(storeMapper.selectList(any())).thenReturn(List.of(s9, s8));

        List<ShipStoreVo> result = service.listPendingStores();

        assertThat(result).hasSize(2);
        // store 9 待发 2 单在前（按 pendingDemandCount desc 排序）
        ShipStoreVo first = result.get(0);
        assertThat(first.getStoreId()).isEqualTo(9L);
        assertThat(first.getStoreName()).isEqualTo("城东店");
        assertThat(first.getPendingDemandCount()).isEqualTo(2);
        assertThat(first.getProductKindCount()).isEqualTo(2);   // distinct 501/502
        assertThat(first.getPendingQuantity()).isEqualByComparingTo("15");
        assertThat(first.getShipStatus()).isEqualTo("待发货");
        // store 8 一单
        ShipStoreVo second = result.get(1);
        assertThat(second.getStoreId()).isEqualTo(8L);
        assertThat(second.getPendingDemandCount()).isEqualTo(1);
        assertThat(second.getProductKindCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("happy: listStorePendingDemands 返该门店 demand 列表 + 各自内嵌可发产品清单")
    void listStorePendingDemands_returnsDemandsWithAvailableProductions() {
        Long storeId = 9L;
        DemandManage d1 = newDemand(100L, storeId, DemandStatus.CONFIRMED);
        d1.setProductType("vegetable");
        d1.setDemandNo("XQ260610001");
        d1.setDemandDate(LocalDate.of(2026, 6, 10));
        d1.setDemandQuantity(new BigDecimal("10"));
        when(demandMapper.selectList(any())).thenReturn(List.of(d1));

        // 业态 vegetable → belong_type=vegetable 产品
        ProductInfo veg = new ProductInfo();
        veg.setId(501L);
        veg.setBelongType("vegetable");
        veg.setProductName("有机青菜");
        when(productInfoMapper.selectList(any())).thenReturn(List.of(veg));

        // 可发库存：demand_id=NULL、未清点
        ProductProduction avail = newProduction(11L, null, "260610V0001", new BigDecimal("3.000"));
        avail.setProductId(501L);
        avail.setStoreId(storeId);
        when(productProductionMapper.selectList(any())).thenReturn(List.of(avail));

        List<ShipDemandVo> result = service.listStorePendingDemands(storeId);

        assertThat(result).hasSize(1);
        ShipDemandVo dv = result.get(0);
        assertThat(dv.getDemandId()).isEqualTo(100L);
        assertThat(dv.getDemandNo()).isEqualTo("XQ260610001");
        assertThat(dv.getProductType()).isEqualTo("vegetable");
        assertThat(dv.getAvailableProductions()).hasSize(1);
        assertThat(dv.getAvailableProductions().get(0).getProduceNo()).isEqualTo("260610V0001");
    }

    @Test
    @DisplayName("error: listStorePendingDemands storeId 为 null → 抛 store.id_required")
    void listStorePendingDemands_nullStoreId_throws() {
        assertThatThrownBy(() -> service.listStorePendingDemands(null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("store.id_required");
    }

    // ============== 生产量口径（Kevin 2026-07-29：按产品单位，kg 取公斤数 / 其余每条计 1）==============

    @Test
    @DisplayName("生产量: kg 单位产品 → 取 produce_quantity 公斤数（不再 ÷ material_num）")
    void queryStoreSummary_kgProduct_producedIsWeight() {
        DemandManage d = newDemand(100L, 9L, DemandStatus.CONFIRMED);
        d.setProductId(501L);
        d.setDemandQuantity(new BigDecimal("10"));   // 需求 10kg
        when(demandMapper.selectList(any())).thenReturn(List.of(d));
        when(storeMapper.selectList(any())).thenReturn(List.of(newStore(9L, "城东店")));

        ProductInfo kgProduct = newProduct(501L, "vegetable", "kg", new BigDecimal("0.25"));
        when(productInfoMapper.selectList(any())).thenReturn(List.of(kgProduct));
        ProductProduction made = newProduction(11L, null, "260610V0001", new BigDecimal("2.500"));
        made.setProductId(501L);
        made.setStoreId(9L);
        when(productProductionMapper.selectList(any())).thenReturn(List.of(made));

        ShipStoreVo vo = service.queryStoreSummary(9L);

        // 生产量 2.5kg / 需求 10kg = 25%（旧口径 2.5 ÷ material_num 0.25 = 10 份 → 会误算 100%）
        assertThat(vo.getSatisfyRate()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("生产量: 按件单位产品（份/枚）→ 每条打包记录恒计 1，与 produce_quantity / material_num 无关")
    void queryStoreSummary_pieceProduct_producedIsOnePerRecord() {
        DemandManage d = newDemand(100L, 9L, DemandStatus.CONFIRMED);
        d.setProductId(502L);
        d.setDemandQuantity(new BigDecimal("4"));    // 需求 4 份
        when(demandMapper.selectList(any())).thenReturn(List.of(d));
        when(storeMapper.selectList(any())).thenReturn(List.of(newStore(9L, "城东店")));

        // 鸡蛋：一份=30 枚，两条打包记录各录 60（旧口径会还原成 2+2=4 份 → 误算 100%）
        ProductInfo eggProduct = newProduct(502L, "egg", "份", new BigDecimal("30"));
        when(productInfoMapper.selectList(any())).thenReturn(List.of(eggProduct));
        ProductProduction m1 = newProduction(11L, null, "260610E0001", new BigDecimal("60"));
        m1.setProductId(502L);
        m1.setStoreId(9L);
        ProductProduction m2 = newProduction(12L, null, "260610E0002", new BigDecimal("60"));
        m2.setProductId(502L);
        m2.setStoreId(9L);
        when(productProductionMapper.selectList(any())).thenReturn(List.of(m1, m2));

        ShipStoreVo vo = service.queryStoreSummary(9L);

        // 2 条记录 = 2 份 / 需求 4 份 = 50%
        assertThat(vo.getSatisfyRate()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("生产量: 礼盒（单位=盒）同按件计 —— 1 条打包记录 = 1，不再取 produce_quantity 盒数")
    void queryStoreSummary_giftBox_producedIsOnePerRecord() {
        DemandManage d = newDemand(100L, 9L, DemandStatus.CONFIRMED);
        d.setProductId(503L);
        d.setDemandQuantity(new BigDecimal("6"));    // 需求 6 盒
        when(demandMapper.selectList(any())).thenReturn(List.of(d));
        when(storeMapper.selectList(any())).thenReturn(List.of(newStore(9L, "城东店")));

        ProductInfo giftProduct = newProduct(503L, "gift_box", "盒", null);
        when(productInfoMapper.selectList(any())).thenReturn(List.of(giftProduct));
        ProductProduction made = newProduction(11L, null, "260610G0001", new BigDecimal("6"));
        made.setProductId(503L);
        made.setStoreId(9L);
        when(productProductionMapper.selectList(any())).thenReturn(List.of(made));

        ShipStoreVo vo = service.queryStoreSummary(9L);

        // 1 条记录 = 1 / 需求 6 = 16.67%
        assertThat(vo.getSatisfyRate()).isEqualByComparingTo("16.67");
    }

    @Test
    @DisplayName("生产量: 产品主数据缺失（product_info 查不到）→ 按件计 1，不拿重量当件数")
    void queryStoreSummary_missingProductInfo_fallsBackToOne() {
        DemandManage d = newDemand(100L, 9L, DemandStatus.CONFIRMED);
        d.setProductId(504L);
        d.setDemandQuantity(new BigDecimal("2"));
        when(demandMapper.selectList(any())).thenReturn(List.of(d));
        when(storeMapper.selectList(any())).thenReturn(List.of(newStore(9L, "城东店")));
        when(productInfoMapper.selectList(any())).thenReturn(List.of());

        ProductProduction made = newProduction(11L, null, "260610X0001", new BigDecimal("99.000"));
        made.setProductId(504L);
        made.setStoreId(9L);
        when(productProductionMapper.selectList(any())).thenReturn(List.of(made));

        ShipStoreVo vo = service.queryStoreSummary(9L);

        assertThat(vo.getSatisfyRate()).isEqualByComparingTo("50.00");
    }

    // ---------- helpers ----------

    private ProductInfo newProduct(Long id, String belongType, String productUnit, BigDecimal materialNum) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setBelongType(belongType);
        p.setProductUnit(productUnit);
        p.setMaterialNum(materialNum);
        return p;
    }

    private DemandManage newDemand(Long id, Long storeId, DemandStatus status) {
        DemandManage d = new DemandManage();
        d.setId(id);
        d.setStoreId(storeId);
        d.setProductType("white_bar");
        d.setDemandStatus(status.name());
        return d;
    }

    @Test
    @DisplayName("闸范围: 发货清单与门店聚合取「今天及以后」的需求（demand_date >= today，过期单不回月台）")
    void loadDemands_datePredicateIsTodayOnwards() {
        when(demandMapper.selectList(any())).thenReturn(List.of());

        service.listStorePendingDemands(9L);
        service.listPendingStores();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<DemandManage>> cap =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(demandMapper, atLeast(2)).selectList(cap.capture());

        // 次日需求在前一天傍晚确认、当晚打包，车也在当晚装 —— 闸只认当天的话，晚上装车时整屏是空的
        // （当天的货中午已发完，次日的货又进不来）。下界仍钳死在今天：过期未发的单不回月台，
        // 偶发配不齐的由 admin 人工取消关掉。两个 loader 必须同口径，否则页头满足率与清单对不上。
        assertThat(cap.getAllValues()).isNotEmpty();
        assertThat(cap.getAllValues()).allSatisfy(w -> {
            String sql = w.getTargetSql();
            assertThat(sql).contains("demand_date >=");
        });
    }

    private Store newStore(Long id, String name) {
        Store s = new Store();
        s.setId(id);
        s.setStoreName(name);
        return s;
    }

    private ProductProduction newProduction(Long id, Long demandId, String produceNo, BigDecimal qty) {
        ProductProduction p = new ProductProduction();
        p.setId(id);
        p.setDemandId(demandId);
        p.setProduceNo(produceNo);
        p.setProduceQuantity(qty);
        p.setIsDeliveryCheck(0);
        return p;
    }
}
