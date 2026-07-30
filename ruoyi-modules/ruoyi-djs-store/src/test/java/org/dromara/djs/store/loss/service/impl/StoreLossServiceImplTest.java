package org.dromara.djs.store.loss.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.service.DictService;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.store.ledger.domain.StoreDailyLedger;
import org.dromara.djs.store.ledger.mapper.StoreDailyLedgerMapper;
import org.dromara.djs.store.loss.domain.StoreLossRecord;
import org.dromara.djs.store.loss.mapper.StoreLossRecordMapper;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.shipment.domain.Shipment;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreLossServiceImpl} 单测（DENGBO-R15）。
 *
 * <p>覆盖 happy path：门店日损耗 2 行（含负值原样搬）+ 白条分割损耗 1 行
 * （loss = max(0, 到店 − 白条分割产品总重)，分割产品总重 = max(0, 当日各白条部位入库量之和
 * − 材料外售同原材料成品当日到店重)）→ 断言总插 3 行、白条分割损耗行 loss_qty / arrive / split 正确；
 * 另覆盖材料外售剔除项与两处钳 0（loss 钳 0 / 分割产品总重钳 0）。</p>
 *
 * @author djs
 * @since DENGBO-R15
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreLossServiceImpl 单元测试")
class StoreLossServiceImplTest {

    @Mock private StoreLossRecordMapper baseMapper;
    @Mock private StoreDailyLedgerMapper storeDailyLedgerMapper;
    @Mock private ShipmentMapper shipmentMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private IProductProductionService productProductionService;
    @Mock private DictService dictService;

    private StoreLossServiceImpl service;
    private MockedStatic<TenantHelper> tenantHelperMock;

    private static final String TENANT_ID = "1001";
    private static final Long STORE_ID = 5001L;
    private static final Long PRODUCT_A = 8001L;
    private static final Long PRODUCT_B = 8002L;
    private static final Long WHITE_BAR_PRODUCT = 8100L;
    private static final String WHITE_BAR_CODE = "P-WB-001";
    /** 材料外售成品（原材料 = 白条部位产品）：其当日到店重从白条分割产品总重中剔除。 */
    private static final Long MATERIAL_SOLD_PRODUCT = 8200L;
    private static final String MATERIAL_SOLD_CODE = "P-MS-001";
    private static final LocalDate DATE = LocalDate.of(2026, 8, 15);

    /** MyBatis-Plus 单测 entity cache 预热（LambdaQueryWrapper 解析列名需要）。 */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, StoreLossRecord.class);
        TableInfoHelper.initTableInfo(assistant, StoreDailyLedger.class);
        TableInfoHelper.initTableInfo(assistant, Shipment.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
    }

    @BeforeEach
    void setup() {
        service = new StoreLossServiceImpl(baseMapper, storeDailyLedgerMapper, shipmentMapper,
            productInfoMapper, productProductionService, dictService);
        tenantHelperMock = Mockito.mockStatic(TenantHelper.class);
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(TENANT_ID);
        when(baseMapper.insert(any(StoreLossRecord.class))).thenReturn(1);
        when(baseMapper.physicalDeleteByDate(eq(TENANT_ID), any())).thenReturn(0);
    }

    @AfterEach
    void tearDown() {
        tenantHelperMock.close();
    }

    @Test
    @DisplayName("aggregate happy：门店日损耗2行(含负值原样搬)+白条分割损耗1行(到店−分割产品总重钳0) → 总插3行")
    void testAggregate_Happy() {
        // 门店日损耗：产品 A loss=2.5、产品 B loss=-1.0（盘盈原样搬）。
        StoreDailyLedger la = ledger(PRODUCT_A, new BigDecimal("2.5"));
        StoreDailyLedger lb = ledger(PRODUCT_B, new BigDecimal("-1.0"));
        // 白条部位当日入库（门店分割产出）：WHITE_BAR_PRODUCT inbound=30 → 白条分割产品总重 30.000。
        StoreDailyLedger wbInbound = inboundLedger(WHITE_BAR_PRODUCT, new BigDecimal("30.000"));
        // selectList 被调 2 次：第 1 次（门店日损耗）返 [la, lb]；第 2 次（白条部位入库汇总）返 [wbInbound]。
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(la, lb), List.of(wbInbound));
        // 产品单位补充
        when(productInfoMapper.selectList(any())).thenReturn(List.of(
            product(PRODUCT_A, null, "斤"),
            product(PRODUCT_B, null, "个"),
            product(WHITE_BAR_PRODUCT, WHITE_BAR_CODE, "kg")));

        // 白条到店重 100.000，白条分割产品总重 30.000 → loss = 70.000。
        Shipment ship = new Shipment();
        ship.setStoreId(STORE_ID);
        ship.setShipQuantity(new BigDecimal("100.000"));
        when(shipmentMapper.selectList(any())).thenReturn(List.of(ship));

        when(dictService.getAllDictByDictType("djs_white_bar_return_product"))
            .thenReturn(Map.of(WHITE_BAR_CODE, "白条产品"));

        service.aggregate(DATE);

        // 幂等先物理删该日旧行
        verify(baseMapper, times(1)).physicalDeleteByDate(TENANT_ID, DATE);

        ArgumentCaptor<StoreLossRecord> cap = ArgumentCaptor.forClass(StoreLossRecord.class);
        verify(baseMapper, times(3)).insert(cap.capture());
        List<StoreLossRecord> inserted = cap.getAllValues();

        // 门店日损耗 A：原样 2.5，单位斤
        StoreLossRecord recA = inserted.stream()
            .filter(r -> "store_daily_loss".equals(r.getLossType()) && PRODUCT_A.equals(r.getProductId()))
            .findFirst().orElseThrow();
        assertThat(recA.getLossQty()).isEqualByComparingTo("2.5");
        assertThat(recA.getProductUnit()).isEqualTo("斤");
        assertThat(recA.getLossDate()).isEqualTo(DATE);

        // 门店日损耗 B：负值原样搬 -1.0
        StoreLossRecord recB = inserted.stream()
            .filter(r -> "store_daily_loss".equals(r.getLossType()) && PRODUCT_B.equals(r.getProductId()))
            .findFirst().orElseThrow();
        assertThat(recB.getLossQty()).isEqualByComparingTo("-1.0");

        // 白条分割损耗行：product_id=0（哨兵），loss=到店−分割产品总重=70，arrive=100 split=30
        StoreLossRecord recWb = inserted.stream()
            .filter(r -> "white_bar_split_loss".equals(r.getLossType()))
            .findFirst().orElseThrow();
        assertThat(recWb.getProductId()).isEqualTo(0L);
        assertThat(recWb.getStoreId()).isEqualTo(STORE_ID);
        assertThat(recWb.getLossQty()).isEqualByComparingTo("70.000");
        assertThat(recWb.getWhiteBarArriveWeight()).isEqualByComparingTo("100.000");
        assertThat(recWb.getWhiteBarSplitWeight()).isEqualByComparingTo("30.000");
    }

    @Test
    @DisplayName("aggregate 白条分割产品总重>到店 → loss 钳 0")
    void testAggregate_WhiteBarLossClampZero() {
        // 白条部位入库(分割产品总重) 50 > 到店 20 → loss=max(0,20−50)=0。
        StoreDailyLedger wbInbound = inboundLedger(WHITE_BAR_PRODUCT, new BigDecimal("50.000"));
        // 第 1 次（门店日损耗）空；第 2 次（白条部位入库汇总）返 [wbInbound]。
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(), List.of(wbInbound));
        Shipment ship = new Shipment();
        ship.setStoreId(STORE_ID);
        ship.setShipQuantity(new BigDecimal("20.000"));
        when(shipmentMapper.selectList(any())).thenReturn(List.of(ship));
        when(dictService.getAllDictByDictType("djs_white_bar_return_product"))
            .thenReturn(Map.of(WHITE_BAR_CODE, "白条产品"));
        when(productInfoMapper.selectList(any())).thenReturn(List.of(product(WHITE_BAR_PRODUCT, WHITE_BAR_CODE, "kg")));

        service.aggregate(DATE);

        ArgumentCaptor<StoreLossRecord> cap = ArgumentCaptor.forClass(StoreLossRecord.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        StoreLossRecord recWb = cap.getValue();
        assertThat(recWb.getLossType()).isEqualTo("white_bar_split_loss");
        assertThat(recWb.getLossQty()).isEqualByComparingTo("0");   // 钳 0
        assertThat(recWb.getWhiteBarArriveWeight()).isEqualByComparingTo("20.000");
        assertThat(recWb.getWhiteBarSplitWeight()).isEqualByComparingTo("50.000");
    }

    @Test
    @DisplayName("aggregate 分割产品总重剔除材料外售到店重：入库42.000−外售1.001=40.999，到店44.000 → loss 3.001")
    void testAggregate_MaterialSoldExcludedFromSplitWeight() {
        // 白条部位当日入库 42.000；材料外售成品（原材料 ∈ 白条部位字典）当日到店实称 1.001
        // → 白条分割产品总重 = 42.000 − 1.001 = 40.999；loss = 44.000 − 40.999 = 3.001。
        StoreDailyLedger wbInbound = inboundLedger(WHITE_BAR_PRODUCT, new BigDecimal("42.000"));
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(), List.of(wbInbound));
        Shipment ship = new Shipment();
        ship.setStoreId(STORE_ID);
        ship.setShipQuantity(new BigDecimal("44.000"));
        when(shipmentMapper.selectList(any())).thenReturn(List.of(ship));
        when(dictService.getAllDictByDictType("djs_white_bar_return_product"))
            .thenReturn(Map.of(WHITE_BAR_CODE, "白条产品"));
        // productInfoMapper 两次调用：① 白条部位字典 resolve ② 材料外售成品（product_material ∈ 白条部位）
        when(productInfoMapper.selectList(any())).thenReturn(
            List.of(product(WHITE_BAR_PRODUCT, WHITE_BAR_CODE, "kg")),
            List.of(product(MATERIAL_SOLD_PRODUCT, MATERIAL_SOLD_CODE, "份")));
        when(productProductionService.sumDeliveredWeightToStore(STORE_ID, MATERIAL_SOLD_PRODUCT, DATE))
            .thenReturn(new BigDecimal("1.001"));

        service.aggregate(DATE);

        ArgumentCaptor<StoreLossRecord> cap = ArgumentCaptor.forClass(StoreLossRecord.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        StoreLossRecord recWb = cap.getValue();
        assertThat(recWb.getLossType()).isEqualTo("white_bar_split_loss");
        assertThat(recWb.getWhiteBarArriveWeight()).isEqualByComparingTo("44.000");
        assertThat(recWb.getWhiteBarSplitWeight()).isEqualByComparingTo("40.999");
        assertThat(recWb.getLossQty()).isEqualByComparingTo("3.001");
    }

    @Test
    @DisplayName("aggregate 材料外售到店重 > 各部位入库量之和 → 白条分割产品总重钳 0")
    void testAggregate_SplitWeightClampZero() {
        // 入库 10.000 − 材料外售到店 15.000 = -5 → 分割产品总重钳 0；loss = 30.000 − 0 = 30.000。
        StoreDailyLedger wbInbound = inboundLedger(WHITE_BAR_PRODUCT, new BigDecimal("10.000"));
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(), List.of(wbInbound));
        Shipment ship = new Shipment();
        ship.setStoreId(STORE_ID);
        ship.setShipQuantity(new BigDecimal("30.000"));
        when(shipmentMapper.selectList(any())).thenReturn(List.of(ship));
        when(dictService.getAllDictByDictType("djs_white_bar_return_product"))
            .thenReturn(Map.of(WHITE_BAR_CODE, "白条产品"));
        when(productInfoMapper.selectList(any())).thenReturn(
            List.of(product(WHITE_BAR_PRODUCT, WHITE_BAR_CODE, "kg")),
            List.of(product(MATERIAL_SOLD_PRODUCT, MATERIAL_SOLD_CODE, "份")));
        when(productProductionService.sumDeliveredWeightToStore(STORE_ID, MATERIAL_SOLD_PRODUCT, DATE))
            .thenReturn(new BigDecimal("15.000"));

        service.aggregate(DATE);

        ArgumentCaptor<StoreLossRecord> cap = ArgumentCaptor.forClass(StoreLossRecord.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        StoreLossRecord recWb = cap.getValue();
        assertThat(recWb.getWhiteBarSplitWeight()).isEqualByComparingTo("0");   // 钳 0
        assertThat(recWb.getLossQty()).isEqualByComparingTo("30.000");
    }

    @Test
    @DisplayName("aggregate 当天无白条到店 → 不记录白条分割损耗行")
    void testAggregate_NoWhiteBarArrival() {
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of());
        when(shipmentMapper.selectList(any())).thenReturn(List.of());   // 无白条发货

        service.aggregate(DATE);

        verify(baseMapper, times(0)).insert(any(StoreLossRecord.class));
    }

    private StoreDailyLedger ledger(Long productId, BigDecimal loss) {
        StoreDailyLedger l = new StoreDailyLedger();
        l.setStoreId(STORE_ID);
        l.setProductId(productId);
        l.setLedgerDate(DATE);
        l.setLossQty(loss);
        return l;
    }

    /** 白条部位当日入库行（门店分割产出）：白条分割产品总重 = 各部位 inbound_qty 之和。 */
    private StoreDailyLedger inboundLedger(Long productId, BigDecimal inbound) {
        StoreDailyLedger l = new StoreDailyLedger();
        l.setStoreId(STORE_ID);
        l.setProductId(productId);
        l.setLedgerDate(DATE);
        l.setInboundQty(inbound);
        return l;
    }

    private ProductInfo product(Long id, String bizCode, String unit) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductId(bizCode);
        p.setProductUnit(unit);
        return p;
    }
}
