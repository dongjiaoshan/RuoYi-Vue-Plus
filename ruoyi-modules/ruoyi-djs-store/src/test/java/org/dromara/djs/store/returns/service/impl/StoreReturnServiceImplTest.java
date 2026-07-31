package org.dromara.djs.store.returns.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.store.ledger.domain.StoreDailyLedger;
import org.dromara.djs.store.ledger.mapper.StoreDailyLedgerMapper;
import org.dromara.djs.store.returns.domain.StoreReturn;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBatchBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBo;
import org.dromara.djs.store.returns.mapper.StoreReturnMapper;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.purchase.service.IWarehousePurchaseInService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreReturnServiceImpl} 单测（STR-RETURN-REBUILD-001，K4 简化重做 + 联动外购入库）。
 *
 * <p>覆盖核心场景：</p>
 * <ol>
 *   <li>insertByBo happy：returnNo=RET 开头 + operatorId 注入 + returnDate 缺省 now + locationId 存值</li>
 *   <li>insertByBo 联动入库：调 {@code purchaseInService.inboundReturnBasket(productId, locationId, qty, "return_in", "门店退回入库:...")}（row31 退货专属篮）</li>
 *   <li>insertByBo 产品/门店不存在 → 抛 ServiceException + 不 INSERT + <b>不联动入库</b></li>
 *   <li>updateByBo 元数据 only：不回写 returnNo / operatorId / productId / locationId / returnQuantity + 不联动入库</li>
 *   <li>deleteByIds → softDelete（不冲销库存，V1）</li>
 * </ol>
 *
 * @author djs
 * @since STR-RETURN-REBUILD-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreReturnServiceImpl 单元测试")
class StoreReturnServiceImplTest {

    @Mock private StoreReturnMapper baseMapper;
    @Mock private StoreMapper storeMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private LocationInfoMapper locationInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private IWarehousePurchaseInService purchaseInService;
    @Mock private DemandManageMapper demandManageMapper;
    @Mock private org.dromara.common.core.service.DictService dictService;
    @Mock private org.dromara.djs.warehouse.pack.service.IProductProductionService productProductionService;
    @Mock private org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper productProductionMapper;
    @Mock private org.dromara.djs.common.store.service.IStoreService storeService;
    @Mock private StoreDailyLedgerMapper storeDailyLedgerMapper;

    private TestableStoreReturnServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    private static final Long USER_ID = 9001L;
    private static final Long STORE_ID = 5001L;
    private static final Long PRODUCT_ID = 8001L;
    private static final Long LOCATION_ID = 3001L;
    private static final Long MEMBER_ID = 7001L;
    private static final String RETURN_NO = "RET2026060200001";
    private static final String FLOW_RETURN_IN = "store_return_in";

    /**
     * MyBatis-Plus 单测 entity cache 预热（skill coder-mp-entity-cache-test）：
     * service 内 LambdaQueryWrapper 在 mock 路径下也可能触发 TableInfoHelper.getTableInfo() 解析 lambda 列名。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, StoreReturn.class);
        TableInfoHelper.initTableInfo(assistant, Store.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, StoreDailyLedger.class);
        TableInfoHelper.initTableInfo(assistant, DemandManage.class);
        TableInfoHelper.initTableInfo(assistant, ProductProduction.class);
    }

    /**
     * 子类化 stub generateReturnNo 固定值（避开真实 BizCodeGenerator / Redisson 锁）。
     */
    static class TestableStoreReturnServiceImpl extends StoreReturnServiceImpl {
        TestableStoreReturnServiceImpl(StoreReturnMapper b, StoreMapper sm,
                                       ProductInfoMapper pm, LocationInfoMapper lm,
                                       IBizCodeGenerator g, IWarehousePurchaseInService pis,
                                       DemandManageMapper dm, org.dromara.common.core.service.DictService ds,
                                       org.dromara.djs.warehouse.pack.service.IProductProductionService pps,
                                       org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper ppm,
                                       org.dromara.djs.common.store.service.IStoreService iss,
                                       StoreDailyLedgerMapper sdlm) {
            super(b, sm, pm, lm, g, pis, dm, ds, pps, ppm, iss, sdlm);
        }

        @Override
        protected String generateReturnNo() {
            return RETURN_NO;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableStoreReturnServiceImpl(baseMapper, storeMapper, productInfoMapper,
            locationInfoMapper, bizCodeGenerator, purchaseInService, demandManageMapper, dictService,
            productProductionService, productProductionMapper, storeService, storeDailyLedgerMapper);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);
        when(baseMapper.insert(any(StoreReturn.class))).thenAnswer(inv -> {
            StoreReturn r = inv.getArgument(0);
            r.setId(60000L + (long) (Math.random() * 1000));
            return 1;
        });
        when(purchaseInService.inboundReturnBasket(any(), any(), any(), anyString(), any())).thenReturn(77777L);
        // product / store 默认存在
        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductName("有机番茄");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product);
        Store store = new Store();
        store.setId(STORE_ID);
        store.setStoreName("东角山旗舰店");
        when(storeMapper.selectById(STORE_ID)).thenReturn(store);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private StoreReturnBo bo(String direction, Long storeId) {
        StoreReturnBo bo = new StoreReturnBo();
        bo.setReturnDirection(direction);
        bo.setStoreId(storeId);
        bo.setProductId(PRODUCT_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setReturnQuantity(new BigDecimal("3.5"));
        bo.setReturnReason("客户改主意");
        bo.setTraceCode("TRC20260602ABCD");
        bo.setMemberId(MEMBER_ID);
        return bo;
    }

    @Test
    @DisplayName("insertByBo happy：returnNo=RET 开头 + operatorId 注入 + returnDate 缺省 now + locationId 存值")
    void testInsert_Happy() {
        Long id = service.insertByBo(bo("customer_to_store", STORE_ID));
        assertThat(id).isNotNull();

        ArgumentCaptor<StoreReturn> cap = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        StoreReturn e = cap.getValue();
        assertThat(e.getReturnNo()).isEqualTo(RETURN_NO);
        assertThat(e.getReturnDirection()).isEqualTo("customer_to_store");
        assertThat(e.getStoreId()).isEqualTo(STORE_ID);
        assertThat(e.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(e.getLocationId()).isEqualTo(LOCATION_ID);
        assertThat(e.getReturnQuantity()).isEqualByComparingTo("3.5");
        assertThat(e.getOperatorId()).isEqualTo(USER_ID);
        assertThat(e.getReturnDate()).isNotNull(); // 缺省 now
        assertThat(e.getTraceCode()).isEqualTo("TRC20260602ABCD");
        assertThat(e.getMemberId()).isEqualTo(MEMBER_ID);
    }

    @Test
    @DisplayName("insertByBo 联动入库：row31 走退货专属篮 inboundReturnBasket(productId, locationId, qty, return_in, 门店退回入库:RET...)")
    void testInsert_InboundLinkage() {
        service.insertByBo(bo("customer_to_store", STORE_ID));

        ArgumentCaptor<String> remarkCap = ArgumentCaptor.forClass(String.class);
        verify(purchaseInService, times(1)).inboundReturnBasket(
            eq(PRODUCT_ID), eq(LOCATION_ID), eq(new BigDecimal("3.5")), eq(FLOW_RETURN_IN), remarkCap.capture());
        assertThat(remarkCap.getValue()).startsWith("门店退回入库").contains(RETURN_NO);
    }

    @Test
    @DisplayName("insertByBo 方向留空 → 默认 customer_to_store（门店主场景）+ 仍联动退货篮入库")
    void testInsert_DefaultDirection() {
        service.insertByBo(bo(null, STORE_ID));
        ArgumentCaptor<StoreReturn> cap = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getReturnDirection()).isEqualTo("customer_to_store");
        verify(purchaseInService, times(1)).inboundReturnBasket(any(), any(), any(), eq(FLOW_RETURN_IN), any());
    }

    @Test
    @DisplayName("insertByBo 产品不存在 → 抛 ServiceException + 不 INSERT + 不联动入库")
    void testInsert_ProductNotFound() {
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.insertByBo(bo("customer_to_store", STORE_ID)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("产品不存在");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("insertByBo 门店非空但不存在 → 抛 ServiceException + 不 INSERT + 不联动入库")
    void testInsert_StoreNotFound() {
        when(storeMapper.selectById(STORE_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.insertByBo(bo("customer_to_store", STORE_ID)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("门店不存在");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("row178：礼盒退回仓库 → 抛 ServiceException + 不 INSERT + 不联动入库")
    void testInsert_GiftBoxRejectedForWarehouseReturn() {
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(giftBoxProduct());
        assertThatThrownBy(() -> service.insertByBo(bo("store_to_warehouse", STORE_ID)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("礼盒");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("row178：礼盒顾客退门店不受影响（不回仓库库存）→ 正常入库")
    void testInsert_GiftBoxAllowedForCustomerReturn() {
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(giftBoxProduct());
        assertThat(service.insertByBo(bo("customer_to_store", STORE_ID))).isNotNull();
        verify(baseMapper, times(1)).insert(any(StoreReturn.class));
    }

    /** row178：礼盒 = belong_type gift_box + 无 product_material（多种原料组合，拆不回单一原材料）。 */
    private ProductInfo giftBoxProduct() {
        ProductInfo giftBox = new ProductInfo();
        giftBox.setId(PRODUCT_ID);
        giftBox.setProductName("有机蔬菜盲盒3.5斤");
        giftBox.setBelongType("gift_box");
        return giftBox;
    }

    @Test
    @DisplayName("updateByBo 元数据 only：不回写 returnNo/operatorId/productId/locationId/returnQuantity + 不联动入库")
    void testUpdate_MetadataOnly() {
        StoreReturn existing = new StoreReturn();
        existing.setId(60001L);
        existing.setReturnNo(RETURN_NO);
        existing.setOperatorId(USER_ID);
        when(baseMapper.selectById(60001L)).thenReturn(existing);
        when(baseMapper.updateById(any(StoreReturn.class))).thenReturn(1);

        StoreReturnBo upd = bo("customer_to_store", STORE_ID);
        upd.setId(60001L);
        upd.setReturnQuantity(new BigDecimal("9.9")); // 尝试改数量
        upd.setReturnReason("修正原因");

        int n = service.updateByBo(upd);
        assertThat(n).isEqualTo(1);

        ArgumentCaptor<StoreReturn> cap = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper, times(1)).updateById(cap.capture());
        StoreReturn e = cap.getValue();
        assertThat(e.getReturnNo()).isNull();         // 不改单号
        assertThat(e.getOperatorId()).isNull();       // 不改经手人
        assertThat(e.getProductId()).isNull();        // 入库驱动字段锁死
        assertThat(e.getLocationId()).isNull();       // 入库驱动字段锁死
        assertThat(e.getReturnQuantity()).isNull();   // 入库驱动字段锁死（即便 bo 传了 9.9）
        assertThat(e.getReturnReason()).isEqualTo("修正原因"); // 元数据可改
        // 编辑不再次联动入库
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("updateByBo 记录不存在 → 抛 ServiceException")
    void testUpdate_NotFound() {
        when(baseMapper.selectById(99999L)).thenReturn(null);
        StoreReturnBo upd = bo("customer_to_store", STORE_ID);
        upd.setId(99999L);
        assertThatThrownBy(() -> service.updateByBo(upd))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("退回记录不存在");
    }

    @Test
    @DisplayName("deleteByIds → softDelete（不冲销库存，V1）")
    void testDelete_SoftDelete() {
        when(baseMapper.update(any(), any())).thenReturn(1);
        int n = service.deleteByIds(List.of(60001L, 60002L));
        assertThat(n).isEqualTo(2);
        verify(baseMapper, times(2)).update(any(), any());
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("deleteByIds 空集合 → 返 0，不调 update")
    void testDelete_Empty() {
        int n = service.deleteByIds(List.of());
        assertThat(n).isZero();
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("returnDate 显式传入 → 透传不被 now 覆盖")
    void testInsert_ExplicitReturnDate() {
        LocalDateTime fixed = LocalDateTime.of(2026, 5, 1, 10, 0);
        StoreReturnBo b = bo("customer_to_store", STORE_ID);
        b.setReturnDate(fixed);
        service.insertByBo(b);
        ArgumentCaptor<StoreReturn> cap = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getReturnDate()).isEqualTo(fixed);
    }

    @Test
    @DisplayName("row119：字典原材料退回额度=max(当日盘点 期初+入库, 材料外售成品到店重)，超额抛异常")
    void testBatchCreate_RejectsOverArrivedQuantity() {
        prepareWhiteBarDictScenario();

        StoreReturnBatchBo.Item item = new StoreReturnBatchBo.Item();
        item.setProductId(PRODUCT_ID);
        item.setReturnQuantity(new BigDecimal("4.000"));
        item.setReturnWeight(new BigDecimal("4.000"));
        StoreReturnBatchBo batch = new StoreReturnBatchBo();
        batch.setStoreId(STORE_ID);
        batch.setItems(List.of(item));

        // 盘点当日入库 3.000、材料外售成品到店 1.001 → 上限取大 = 3.000（旧口径相加 4.001 会放过 4.000 → 盘点损耗变负）
        assertThatThrownBy(() -> service.batchCreate(batch))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("3.000");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("row119：退回量等于当日到店量上限 → 放行入库")
    void testBatchCreate_AllowsUpToArrivedQuantity() {
        prepareWhiteBarDictScenario();

        StoreReturnBatchBo.Item item = new StoreReturnBatchBo.Item();
        item.setProductId(PRODUCT_ID);
        item.setReturnQuantity(new BigDecimal("3.000"));
        item.setReturnWeight(new BigDecimal("3.000"));
        StoreReturnBatchBo batch = new StoreReturnBatchBo();
        batch.setStoreId(STORE_ID);
        batch.setItems(List.of(item));

        assertThat(service.batchCreate(batch)).isEqualTo(1);
        ArgumentCaptor<StoreReturn> captor = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper).insert(captor.capture());
        assertThat(captor.getValue().getGoodsWeight()).isEqualByComparingTo("3.000");
    }

    /**
     * 白条退回字典产品场景：该店当日有白条到店、盘点已录该产品入库 3.000、
     * 另有一个材料外售成品（原材料=该产品）当日到店 1.001。
     */
    private void prepareWhiteBarDictScenario() {
        long finishedId = 8100L;
        ProductInfo finished = new ProductInfo();
        finished.setId(finishedId);
        finished.setBelongType("pork");
        finished.setProductMaterial(PRODUCT_ID);
        ProductInfo dictProduct = new ProductInfo();
        dictProduct.setId(PRODUCT_ID);
        dictProduct.setProductId("MAT-CODE");
        dictProduct.setProductName("扇子骨");
        dictProduct.setProductUnit("kg");
        ProductInfo whiteBar = new ProductInfo();
        whiteBar.setId(8200L);
        whiteBar.setBelongType("white_bar");
        when(productInfoMapper.selectList(any())).thenReturn(
            List.of(finished),
            List.of(),
            List.of(dictProduct),
            List.of(whiteBar)
        );
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(dictProduct);
        when(productProductionMapper.selectDeliveredProductIdsToStore(any(), any()))
            .thenReturn(List.of(finishedId));
        when(productProductionService.sumDeliveredWeightToStore(any(), eq(finishedId), any()))
            .thenReturn(new BigDecimal("1.001"));
        when(dictService.getAllDictByDictType("djs_white_bar_return_product"))
            .thenReturn(Map.of("MAT-CODE", "扇子骨"));
        org.dromara.djs.warehouse.demand.domain.DemandManage demand =
            new org.dromara.djs.warehouse.demand.domain.DemandManage();
        demand.setId(8300L);
        when(demandManageMapper.selectList(any())).thenReturn(List.of(demand));
        when(productProductionMapper.selectCount(any())).thenReturn(1L);
        StoreDailyLedger ledger = new StoreDailyLedger();
        ledger.setInboundQty(new BigDecimal("3.000"));
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(ledger));
        when(baseMapper.selectList(any())).thenReturn(List.of());
    }
}
