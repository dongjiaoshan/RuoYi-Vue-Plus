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
import org.dromara.djs.store.returns.domain.StoreReturn;
import org.dromara.djs.store.returns.mapper.StoreReturnMapper;
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
 * <p>覆盖 happy path：门店日损耗 2 行（含负值原样搬）+ 白条分割损耗 1 行（到店>退回，loss=到店−退回 钳 0）
 * → 断言总插 3 行、白条分割损耗行 loss_qty / arrive / split 正确。</p>
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
    @Mock private StoreReturnMapper storeReturnMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private org.dromara.djs.warehouse.pack.service.IProductProductionService productProductionService;
    @Mock private DictService dictService;

    private StoreLossServiceImpl service;
    private MockedStatic<TenantHelper> tenantHelperMock;

    private static final String TENANT_ID = "1001";
    private static final Long STORE_ID = 5001L;
    private static final Long PRODUCT_A = 8001L;
    private static final Long PRODUCT_B = 8002L;
    private static final Long WHITE_BAR_PRODUCT = 8100L;
    private static final String WHITE_BAR_CODE = "P-WB-001";
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
        TableInfoHelper.initTableInfo(assistant, StoreReturn.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
    }

    @BeforeEach
    void setup() {
        service = new StoreLossServiceImpl(baseMapper, storeDailyLedgerMapper, shipmentMapper,
            storeReturnMapper, productInfoMapper, productProductionService, dictService);
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
    @DisplayName("aggregate happy：门店日损耗2行(含负值原样搬)+白条分割损耗1行(到店−退回钳0) → 总插3行")
    void testAggregate_Happy() {
        // 门店日损耗：产品 A loss=2.5、产品 B loss=-1.0（盘盈原样搬）。
        StoreDailyLedger la = ledger(PRODUCT_A, new BigDecimal("2.5"));
        StoreDailyLedger lb = ledger(PRODUCT_B, new BigDecimal("-1.0"));
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(la, lb));
        // 产品单位补充
        when(productInfoMapper.selectList(any())).thenReturn(List.of(
            product(PRODUCT_A, null, "斤"),
            product(PRODUCT_B, null, "个"),
            product(WHITE_BAR_PRODUCT, WHITE_BAR_CODE, "kg")));

        // 白条到店重 100.000，退回入库重 30.000 → loss = 70.000。
        Shipment ship = new Shipment();
        ship.setStoreId(STORE_ID);
        ship.setShipQuantity(new BigDecimal("100.000"));
        when(shipmentMapper.selectList(any())).thenReturn(List.of(ship));

        when(dictService.getAllDictByDictType("djs_white_bar_return_product"))
            .thenReturn(Map.of(WHITE_BAR_CODE, "白条产品"));

        StoreReturn ret = new StoreReturn();
        ret.setStoreId(STORE_ID);
        ret.setReceivedWeight(new BigDecimal("30.000"));
        when(storeReturnMapper.selectList(any())).thenReturn(List.of(ret));

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

        // 白条分割损耗行：product_id=0（哨兵），loss=到店−退回=70，arrive=100 split=30
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
    @DisplayName("aggregate 白条退回>到店 → loss 钳 0")
    void testAggregate_WhiteBarLossClampZero() {
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of());
        Shipment ship = new Shipment();
        ship.setStoreId(STORE_ID);
        ship.setShipQuantity(new BigDecimal("20.000"));
        when(shipmentMapper.selectList(any())).thenReturn(List.of(ship));
        when(dictService.getAllDictByDictType("djs_white_bar_return_product"))
            .thenReturn(Map.of(WHITE_BAR_CODE, "白条产品"));
        when(productInfoMapper.selectList(any())).thenReturn(List.of(product(WHITE_BAR_PRODUCT, WHITE_BAR_CODE, "kg")));
        StoreReturn ret = new StoreReturn();
        ret.setStoreId(STORE_ID);
        ret.setReceivedWeight(new BigDecimal("50.000"));   // 退回 > 到店
        when(storeReturnMapper.selectList(any())).thenReturn(List.of(ret));

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

    private ProductInfo product(Long id, String bizCode, String unit) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductId(bizCode);
        p.setProductUnit(unit);
        return p;
    }
}
