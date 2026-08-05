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
import org.dromara.djs.store.returns.domain.query.StoreReturnQuery;
import org.dromara.djs.store.returns.domain.vo.StoreReturnStoreDailyVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVegCandidateVo;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.Set;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
    @Mock private org.dromara.common.core.service.UserService userService;

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
                                       StoreDailyLedgerMapper sdlm,
                                       org.dromara.common.core.service.UserService us) {
            super(b, sm, pm, lm, g, pis, dm, ds, pps, ppm, iss, sdlm, us);
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
            productProductionService, productProductionMapper, storeService, storeDailyLedgerMapper, userService);
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
    @DisplayName("row178：礼盒顾客退门店同样拦（insertByBo 对所有方向都真写 location_stock）→ 不 INSERT + 不联动入库")
    void testInsert_GiftBoxRejectedForCustomerReturn() {
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(giftBoxProduct());
        assertThatThrownBy(() -> service.insertByBo(bo("customer_to_store", STORE_ID)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("礼盒");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
        // 关键断言：闸必须挡在 inbound 之前，否则错账已经写进仓库库存了
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("row178：方向留空（默认 customer_to_store）也拦礼盒 → 不 INSERT + 不联动入库")
    void testInsert_GiftBoxRejectedForBlankDirection() {
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(giftBoxProduct());
        assertThatThrownBy(() -> service.insertByBo(bo(null, STORE_ID)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("礼盒");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("row178：改方向也过礼盒闸（先按别的方向建、再 PUT 改成门店退仓库绕不过去）")
    void testUpdate_GiftBoxRejectedOnDirectionChange() {
        StoreReturn existing = new StoreReturn();
        existing.setId(999L);
        existing.setProductId(PRODUCT_ID);
        when(baseMapper.selectById(999L)).thenReturn(existing);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(giftBoxProduct());

        StoreReturnBo bo = bo("store_to_warehouse", STORE_ID);
        bo.setId(999L);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("礼盒");
        verify(baseMapper, never()).updateById(any(StoreReturn.class));
    }

    @Test
    @DisplayName("row178：退回入库流水备注带退回成品名（入库记录按成品名搜靠它精确定位，不串同原材料其他规格）")
    void testInsert_RemarkCarriesReturnedProductName() {
        ProductInfo finished = new ProductInfo();
        finished.setId(PRODUCT_ID);
        finished.setProductName("黑毛猪猪脚1000g/份");
        finished.setBelongType("pork");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(finished);

        service.insertByBo(bo("customer_to_store", STORE_ID));

        ArgumentCaptor<String> remarkCap = ArgumentCaptor.forClass(String.class);
        verify(purchaseInService, times(1)).inboundReturnBasket(any(), any(), any(), eq(FLOW_RETURN_IN), remarkCap.capture());
        assertThat(remarkCap.getValue()).contains(RETURN_NO).contains("黑毛猪猪脚1000g/份");
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
    @DisplayName("row119：字典原材料退回额度=门店当日盘点台账 期初+入库−销售−赠送，超额抛异常")
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
    @Test
    @DisplayName("row205：候选可退量**不减损坏** —— 损坏的货本身就是要退回仓库的（销售/赠送为空时按 期初+入库 算）")
    void testVegCandidates_doesNotSubtractLoss() {
        StoreDailyLedger ledger = new StoreDailyLedger();
        ledger.setProductId(PRODUCT_ID);
        ledger.setOpeningQty(new BigDecimal("2.000"));
        ledger.setInboundQty(new BigDecimal("10.000"));
        // 全损：旧口径（期初+入库−损坏）会算出 0 → 这批货再也退不回仓库，正是要防的
        ledger.setLossQty(new BigDecimal("12.000"));
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(ledger));
        ProductInfo veg = new ProductInfo();
        veg.setId(PRODUCT_ID);
        veg.setProductName("上海青");
        veg.setProductUnit("kg");
        veg.setBelongType("vegetable");
        when(productInfoMapper.selectBatchIds(any())).thenReturn(List.of(veg));
        when(productInfoMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.selectList(any())).thenReturn(List.of());

        List<StoreReturnVegCandidateVo> candidates = service.listVegCandidates(STORE_ID);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getArrivedQuantity()).isEqualByComparingTo("12.000");
    }

    @Test
    @DisplayName("row205：候选可退量 = 期初+入库−销售−赠送（卖掉/送掉的不再算进可退额度）")
    void testVegCandidates_subtractsSaleAndGift() {
        StoreDailyLedger ledger = new StoreDailyLedger();
        ledger.setProductId(PRODUCT_ID);
        ledger.setOpeningQty(new BigDecimal("2.000"));
        ledger.setInboundQty(new BigDecimal("10.000"));
        ledger.setSaleQty(new BigDecimal("3.000"));
        ledger.setGiftQty(new BigDecimal("1.000"));
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(ledger));
        ProductInfo veg = new ProductInfo();
        veg.setId(PRODUCT_ID);
        veg.setProductName("上海青");
        veg.setProductUnit("kg");
        veg.setBelongType("vegetable");
        when(productInfoMapper.selectBatchIds(any())).thenReturn(List.of(veg));
        when(productInfoMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.selectList(any())).thenReturn(List.of());

        List<StoreReturnVegCandidateVo> candidates = service.listVegCandidates(STORE_ID);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getArrivedQuantity()).isEqualByComparingTo("8.000");
    }

    @Test
    @DisplayName("row205：销售+赠送吃光账面 → 该产品不再出现在候选里（可退量 ≤ 0 不列）")
    void testVegCandidates_soldOutDropsFromCandidates() {
        StoreDailyLedger ledger = new StoreDailyLedger();
        ledger.setProductId(PRODUCT_ID);
        ledger.setOpeningQty(new BigDecimal("2.000"));
        ledger.setInboundQty(new BigDecimal("10.000"));
        ledger.setSaleQty(new BigDecimal("12.000"));
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(ledger));
        ProductInfo veg = new ProductInfo();
        veg.setId(PRODUCT_ID);
        veg.setProductName("上海青");
        veg.setProductUnit("kg");
        veg.setBelongType("vegetable");
        when(productInfoMapper.selectBatchIds(any())).thenReturn(List.of(veg));
        when(productInfoMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.listVegCandidates(STORE_ID)).isEmpty();
    }

    @Test
    @DisplayName("row202：折叠成原材料后**已退量必须跟着搬**，否则 used 恒 0 → 同一产品可无限次退")
    void testVegCandidates_foldKeepsReturnedQuantity() {
        StoreDailyLedger ledger = new StoreDailyLedger();
        ledger.setProductId(PRODUCT_ID);
        ledger.setInboundQty(new BigDecimal("10.000"));
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(ledger));
        ProductInfo veg = new ProductInfo();
        veg.setId(PRODUCT_ID);
        veg.setProductName("上海青");
        veg.setProductUnit("kg");
        veg.setBelongType("vegetable");
        when(productInfoMapper.selectBatchIds(any())).thenReturn(List.of(veg));
        when(productInfoMapper.selectList(any())).thenReturn(List.of());
        // 今日已退 4.000
        StoreReturn returned = new StoreReturn();
        returned.setProductId(PRODUCT_ID);
        returned.setReturnQuantity(new BigDecimal("4.000"));
        when(baseMapper.selectList(any())).thenReturn(List.of(returned));

        List<StoreReturnVegCandidateVo> candidates = service.listVegCandidates(STORE_ID);

        assertThat(candidates).hasSize(1);
        // 折叠链路（foldVegMaterialSold）重建 VO 时若漏搬 returnedQuantity，这里会是 null
        assertThat(candidates.get(0).getReturnedQuantity()).isEqualByComparingTo("4.000");
    }

    /**
     * 构造一个「台账里有该产品」的场景：给定业态 + 期初/入库，其余 mock 走空集。
     * 用于验证提交闸对**每个 tab** 都真的生效（此前只有猪肉 tab 被测到）。
     */
    private void prepareLedgerScenario(String belongType, String opening, String inbound) {
        prepareLedgerScenario(belongType, opening, inbound, "0", "0");
    }

    private void prepareLedgerScenario(String belongType, String opening, String inbound, String sale, String gift) {
        StoreDailyLedger ledger = new StoreDailyLedger();
        ledger.setProductId(PRODUCT_ID);
        ledger.setOpeningQty(new BigDecimal(opening));
        ledger.setInboundQty(new BigDecimal(inbound));
        ledger.setSaleQty(new BigDecimal(sale));
        ledger.setGiftQty(new BigDecimal(gift));
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(ledger));
        ProductInfo p = new ProductInfo();
        p.setId(PRODUCT_ID);
        p.setProductName("测试产品");
        p.setProductUnit("kg");
        p.setBelongType(belongType);
        when(productInfoMapper.selectBatchIds(any())).thenReturn(List.of(p));
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(p);
        when(productInfoMapper.selectList(any())).thenReturn(List.of(p));
        when(baseMapper.selectList(any())).thenReturn(List.of());
        when(productProductionMapper.selectDeliveredProductIdsToStore(any(), any())).thenReturn(List.of());
        when(dictService.getAllDictByDictType(any())).thenReturn(Map.of());
    }

    /** 台账里没有的另一个产品（验「没盘过」分支）。 */
    private static final Long OTHER_PRODUCT_ID = 8009L;

    private ProductInfo otherProduct() {
        ProductInfo p = new ProductInfo();
        p.setId(OTHER_PRODUCT_ID);
        p.setProductName("没盘过的菜");
        p.setProductUnit("kg");
        p.setBelongType("vegetable");
        return p;
    }

    private StoreReturnBatchBo batchOf(String qty) {
        StoreReturnBatchBo.Item item = new StoreReturnBatchBo.Item();
        item.setProductId(PRODUCT_ID);
        item.setReturnQuantity(new BigDecimal(qty));
        item.setReturnWeight(new BigDecimal(qty));
        StoreReturnBatchBo batch = new StoreReturnBatchBo();
        batch.setStoreId(STORE_ID);
        batch.setItems(List.of(item));
        return batch;
    }

    @Test
    @DisplayName("row202：**果蔬 tab** 的提交闸真的生效（删掉 listVegCandidates 那圈 for 必须变红）")
    void testBatchCreate_vegTabGateEnforced() {
        prepareLedgerScenario("vegetable", "2.000", "4.000");
        assertThatThrownBy(() -> service.batchCreate(batchOf("6.001")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("6.000");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("row205：**insertByBo** 单条路径同样套台账闸（storeId 真被传进去，退超额必拒）")
    void testInsertByBo_storeToWarehouseGateEnforced() {
        prepareLedgerScenario("vegetable", "0", "3.000");

        StoreReturnBo b = bo("store_to_warehouse", STORE_ID);
        b.setReturnQuantity(new BigDecimal("3.001"));

        assertThatThrownBy(() -> service.insertByBo(b))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("3.000");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("row205：**insertByBo** 对没盘过的产品报「请先去盘点」，且回查台账时 storeId 真被带上（捕获 wrapper 断言）")
    void testInsertByBo_notInLedgerSaysGoCount() {
        prepareLedgerScenario("vegetable", "0", "3.000");
        when(productInfoMapper.selectById(OTHER_PRODUCT_ID)).thenReturn(otherProduct());
        when(storeDailyLedgerMapper.exists(any())).thenReturn(false);

        StoreReturnBo b = bo("store_to_warehouse", STORE_ID);
        b.setProductId(OTHER_PRODUCT_ID);

        assertThatThrownBy(() -> service.insertByBo(b))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("请先在门店盘点里录入");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
        // ⚠️ 光 stub exists(any()) 证明不了 storeId 传对了（传 null 也一样绿）——捕获 wrapper 看真实绑定值。
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StoreDailyLedger>> wrapperCap =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(storeDailyLedgerMapper, atLeastOnce()).exists(wrapperCap.capture());
        assertThat(wrapperCap.getAllValues()).anySatisfy(w -> {
            // MP 的 paramNameValuePairs 是懒生成的，先取一次 SQL 才会绑值
            assertThat(w.getTargetSql()).contains("store_id").contains("product_id");
            assertThat(w.getParamNameValuePairs().values()).contains(STORE_ID, OTHER_PRODUCT_ID);
        });
    }

    @Test
    @DisplayName("row205：品类不可退（包材/饲料等非白名单业态）→ 报「品类不支持退回」，不能说「被销售赠送抵完」")
    void testBatchCreate_nonReturnableBelongTypeSaysCategory() {
        prepareLedgerScenario("vegetable", "0", "10.000");
        ProductInfo pkg = new ProductInfo();
        pkg.setId(OTHER_PRODUCT_ID);
        pkg.setProductName("礼品纸箱");
        pkg.setProductUnit("个");
        pkg.setBelongType("package");
        when(productInfoMapper.selectById(OTHER_PRODUCT_ID)).thenReturn(pkg);
        when(storeDailyLedgerMapper.exists(any())).thenReturn(true);

        StoreReturnBatchBo.Item item = new StoreReturnBatchBo.Item();
        item.setProductId(OTHER_PRODUCT_ID);
        item.setReturnQuantity(new BigDecimal("1.000"));
        item.setReturnWeight(new BigDecimal("1.000"));
        StoreReturnBatchBo batch = new StoreReturnBatchBo();
        batch.setStoreId(STORE_ID);
        batch.setItems(List.of(item));

        assertThatThrownBy(() -> service.batchCreate(batch))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("品类不支持退回")
            .hasMessageNotContaining("已被销售");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    /**
     * 可退业态白名单必须恰好 = 三个 tab 白名单之和。
     * <p>少一个 → 那个业态的产品卖光时会被告知「品类不支持退回」（说瞎话）；多一个 → 不该出现在任何
     * 候选里的品类会被放进「账面已无可退量」分支。两种都只在特定数据下才现形，靠功能用例抓不住，
     * 所以这里做结构断言。</p>
     */
    @Test
    @DisplayName("row205：可退业态白名单 = 猪肉tab ∪ 其他tab ∪ {vegetable}，一个不多一个不少")
    void testReturnableBelongTypesIsExactUnionOfTabs() throws Exception {
        assertThat(readStaticSet("RETURNABLE_BELONG_TYPES"))
            .containsExactlyInAnyOrder("pork", "white_bar", "vegetable", "dry_good", "egg", "other")
            .isEqualTo(union(readStaticSet("PORK_TAB_BELONG_TYPES"),
                readStaticSet("OTHER_TAB_BELONG_TYPES"), Set.of("vegetable")));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> readStaticSet(String field) throws Exception {
        java.lang.reflect.Field f = StoreReturnServiceImpl.class.getDeclaredField(field);
        f.setAccessible(true);
        return (Set<String>) f.get(null);
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        Set<String> all = new java.util.LinkedHashSet<>();
        for (Set<String> one : sets) {
            all.addAll(one);
        }
        return all;
    }

    @Test
    @DisplayName("row205：**提交闸**对 belong_type=NULL 的产品不得 NPE，要报「品类不支持退回」")
    void testBatchCreate_nullBelongTypeDoesNotThrowNpe() {
        prepareLedgerScenario("vegetable", "0", "10.000");
        ProductInfo nullType = new ProductInfo();
        nullType.setId(OTHER_PRODUCT_ID);
        nullType.setProductName("外购未分类品");
        nullType.setProductUnit("kg");
        nullType.setBelongType(null);
        when(productInfoMapper.selectById(OTHER_PRODUCT_ID)).thenReturn(nullType);
        when(storeDailyLedgerMapper.exists(any())).thenReturn(true);

        StoreReturnBatchBo.Item item = new StoreReturnBatchBo.Item();
        item.setProductId(OTHER_PRODUCT_ID);
        item.setReturnQuantity(new BigDecimal("1.000"));
        item.setReturnWeight(new BigDecimal("1.000"));
        StoreReturnBatchBo batch = new StoreReturnBatchBo();
        batch.setStoreId(STORE_ID);
        batch.setItems(List.of(item));

        assertThatThrownBy(() -> service.batchCreate(batch))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("品类不支持退回");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("row205：品类不可退 + 今天没盘过 → 报「品类不支持」而不是「请先去盘点」（别指死路）")
    void testBatchCreate_nonReturnableAndNotCountedSaysCategory() {
        prepareLedgerScenario("vegetable", "0", "10.000");
        ProductInfo seed = new ProductInfo();
        seed.setId(OTHER_PRODUCT_ID);
        seed.setProductName("上海青种子");
        seed.setProductUnit("包");
        seed.setBelongType("seed");
        when(productInfoMapper.selectById(OTHER_PRODUCT_ID)).thenReturn(seed);
        when(storeDailyLedgerMapper.exists(any())).thenReturn(false);

        StoreReturnBatchBo.Item item = new StoreReturnBatchBo.Item();
        item.setProductId(OTHER_PRODUCT_ID);
        item.setReturnQuantity(new BigDecimal("1.000"));
        item.setReturnWeight(new BigDecimal("1.000"));
        StoreReturnBatchBo batch = new StoreReturnBatchBo();
        batch.setStoreId(STORE_ID);
        batch.setItems(List.of(item));

        assertThatThrownBy(() -> service.batchCreate(batch))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("品类不支持退回")
            .hasMessageNotContaining("请先在门店盘点里录入");
    }

    @Test
    @DisplayName("row205：台账里有 belong_type=NULL 的行 → 候选接口正常返回，不得 NPE 500")
    void testCandidates_nullBelongTypeDoesNotThrow() {
        StoreDailyLedger normal = new StoreDailyLedger();
        normal.setProductId(PRODUCT_ID);
        normal.setOpeningQty(new BigDecimal("5.000"));
        normal.setInboundQty(BigDecimal.ZERO);
        StoreDailyLedger weird = new StoreDailyLedger();
        weird.setProductId(OTHER_PRODUCT_ID);
        weird.setOpeningQty(new BigDecimal("9.000"));
        weird.setInboundQty(BigDecimal.ZERO);
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(normal, weird));

        ProductInfo veg = new ProductInfo();
        veg.setId(PRODUCT_ID);
        veg.setProductName("上海青");
        veg.setProductUnit("kg");
        veg.setBelongType("vegetable");
        ProductInfo nullType = new ProductInfo();
        nullType.setId(OTHER_PRODUCT_ID);
        nullType.setProductName("外购未分类品");
        nullType.setProductUnit("kg");
        nullType.setBelongType(null);
        when(productInfoMapper.selectBatchIds(any())).thenReturn(List.of(veg, nullType));
        when(productInfoMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.selectList(any())).thenReturn(List.of());

        List<StoreReturnVegCandidateVo> candidates = service.listVegCandidates(STORE_ID);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getProductName()).isEqualTo("上海青");
    }

    @Test
    @DisplayName("row205：盘过但被销售赠送抵完 → 报「账面已无可退量」，不能再喊「请先去盘点」（他刚盘完）")
    void testBatchCreate_soldOutSaysNoQuotaNotGoCount() {
        prepareLedgerScenario("vegetable", "2.000", "10.000", "12.000", "0");
        // 台账里有这个产品（盘过了），只是 2+10−12=0 被候选剔除 → 落到 limit==null 分支
        when(storeDailyLedgerMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> service.batchCreate(batchOf("1.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("账面已无可退量")
            .hasMessageNotContaining("请先在门店盘点里录入");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("row205：今天压根没盘过这个产品 → 仍报「不在当日盘点台账中，请先盘点」")
    void testBatchCreate_notInLedgerSaysGoCount() {
        prepareLedgerScenario("vegetable", "2.000", "10.000");
        when(productInfoMapper.selectById(OTHER_PRODUCT_ID)).thenReturn(otherProduct());
        when(storeDailyLedgerMapper.exists(any())).thenReturn(false);

        StoreReturnBatchBo.Item item = new StoreReturnBatchBo.Item();
        item.setProductId(OTHER_PRODUCT_ID);
        item.setReturnQuantity(new BigDecimal("1.000"));
        item.setReturnWeight(new BigDecimal("1.000"));
        StoreReturnBatchBo batch = new StoreReturnBatchBo();
        batch.setStoreId(STORE_ID);
        batch.setItems(List.of(item));

        assertThatThrownBy(() -> service.batchCreate(batch))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("请先在门店盘点里录入");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("row205：提交闸按 期初+入库−销售−赠送 封顶（10+0−4−1=5，退 5.001 必拒）")
    void testBatchCreate_gateUsesSaleAndGiftDeducted() {
        prepareLedgerScenario("vegetable", "0", "10.000", "4.000", "1.000");
        assertThatThrownBy(() -> service.batchCreate(batchOf("5.001")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("5.000");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("row202：**其他产品 tab**（干货/蛋类/其他）的提交闸真的生效 —— 本轮唯一功能性新增，此前零覆盖")
    void testBatchCreate_otherTabGateEnforced() {
        prepareLedgerScenario("dry_good", "5.000", "20.000");
        assertThatThrownBy(() -> service.batchCreate(batchOf("25.001")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("25.000");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("row202：不在台账候选里的产品**必须拒绝** —— 曾经放行，可从没收过货的门店凭空退成仓库库存")
    void testBatchCreate_rejectsProductNotInLedger() {
        // 台账为空 → 任何产品都不在候选 map 里
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of());
        ProductInfo p = new ProductInfo();
        p.setId(PRODUCT_ID);
        p.setProductName("从没到过店的菜");
        p.setProductUnit("kg");
        p.setBelongType("vegetable");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(p);
        when(productInfoMapper.selectList(any())).thenReturn(List.of());
        when(productInfoMapper.selectBatchIds(any())).thenReturn(List.of());
        when(baseMapper.selectList(any())).thenReturn(List.of());
        when(productProductionMapper.selectDeliveredProductIdsToStore(any(), any())).thenReturn(List.of());
        when(dictService.getAllDictByDictType(any())).thenReturn(Map.of());

        assertThatThrownBy(() -> service.batchCreate(batchOf("99999")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不在该门店当日盘点台账中");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    /**
     * 三个 tab 的候选**都必须**带上「今日已退量」。
     *
     * <p>回填是在各 tab 入口分别做的（listLedgerCandidates 本身不填 —— 它要留给果蔬折叠改写 productId
     * 之后再按最终 id 查）。这个分散结构很容易漏掉某个 tab，漏掉的那个 tab 已退量恒 null →
     * mergeRemain 把 used 当 0 → 该 tab 可无限次退。**曾经真漏过猪肉 tab。**</p>
     */
    @ParameterizedTest(name = "{0} tab 候选必须回填已退量")
    @CsvSource({"pork,pork", "vegetable,vegetable", "dry_good,other"})
    void testCandidates_allTabsFillReturnedQuantity(String belongType, String tab) {
        prepareLedgerScenario(belongType, "0", "10.000");
        StoreReturn returned = new StoreReturn();
        returned.setProductId(PRODUCT_ID);
        returned.setReturnQuantity(new BigDecimal("4.000"));
        when(baseMapper.selectList(any())).thenReturn(List.of(returned));

        BigDecimal actual = switch (tab) {
            case "pork" -> service.listPorkCandidates(STORE_ID).get(0).getReturnedQuantity();
            case "vegetable" -> service.listVegCandidates(STORE_ID).get(0).getReturnedQuantity();
            default -> service.listOtherCandidates(STORE_ID).get(0).getReturnedQuantity();
        };
        assertThat(actual).as("%s tab 漏回填已退量 → 该 tab 闸门失效", tab).isEqualByComparingTo("4.000");
    }

    // ---------- row15：「非重量产品退回重量」统计口径 ----------
    //
    // 甲方 2026-08-04 口径：该列只统计「退回产品中**单位为非 KG** 的产品里、**原材料单位为 KG** 的产品重量」；
    // 原材料单位非 KG 的（鸡蛋按枚、蛋礼盒按份）**不累加** —— 它们的 received_weight 落的是件数，
    // 累进 kg 合计等于把 30 枚当 30 公斤。旧口径（Σ 全部非 kg 行）在甲方那批数据上算出 31.430kg，正确值 0.430kg。

    private static final Long P_LOIN = 9001L;        // 里脊肉：产品单位 kg、无原材料
    private static final Long P_EGG = 9002L;         // 鸡蛋：产品单位 枚、原材料单位 枚
    private static final Long P_EGG_GIFT = 9003L;    // 30枚散养绿壳土鸡蛋礼盒装：产品单位 份、原材料单位 枚
    private static final Long P_MOREL_GIFT = 9004L;  // 干羊肚菌礼盒装(净重量80g)：产品单位 份、原材料单位 kg
    private static final Long P_VEG_350G = 9005L;    // 有机苕尖350g：产品单位 份、原材料单位 kg
    private static final Long M_EGG = 9101L;         // 原材料：鸡蛋（单位 枚）
    private static final Long M_KG = 9102L;          // 原材料：按 kg 计的原料

    private ProductInfo product(Long id, String name, String unit, Long material) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductName(name);
        p.setProductUnit(unit);
        p.setProductMaterial(material);
        return p;
    }

    private StoreReturn dailyRow(Long productId, String goodsWeight, String receivedWeight) {
        StoreReturn r = new StoreReturn();
        r.setStoreId(STORE_ID);
        r.setProductId(productId);
        r.setReturnDate(LocalDateTime.of(2026, 8, 4, 10, 0));
        r.setGoodsWeight(new BigDecimal(goodsWeight));
        r.setReceivedWeight(new BigDecimal(receivedWeight));
        r.setReturnStatus("received");
        return r;
    }

    /**
     * 铺 buildStoreDailyList 的 mock：退回行 + 行产品（第 1 次 IN 查）+ 原材料产品（第 2 次 IN 查）。
     * 行产品**必须带 productMaterial**，否则原材料单位解析不出来，全部行会退化成「按产品单位」判定。
     */
    private void prepareStoreDaily(List<StoreReturn> rows, List<ProductInfo> rowProducts, List<ProductInfo> materials) {
        when(baseMapper.selectList(any())).thenReturn(rows);
        when(productInfoMapper.selectList(any())).thenReturn(rowProducts, materials);
    }

    @Test
    @DisplayName("row15：非重量产品退回重量只累加「产品单位≠kg **且原材料单位=kg**」的行（甲方那批 31.430 → 0.430）")
    void testStoreDaily_nonWeightTotalOnlyCountsKgMaterialRows() {
        prepareStoreDaily(
            List.of(dailyRow(P_LOIN, "2.000", "2.000"),
                dailyRow(P_EGG, "0", "1.000"),
                dailyRow(P_EGG_GIFT, "0", "30.000"),
                dailyRow(P_MOREL_GIFT, "0", "0.080"),
                dailyRow(P_VEG_350G, "0", "0.350")),
            List.of(product(P_LOIN, "里脊肉", "kg", null),
                product(P_EGG, "鸡蛋", "枚", M_EGG),
                product(P_EGG_GIFT, "30枚散养绿壳土鸡蛋礼盒装", "份", M_EGG),
                product(P_MOREL_GIFT, "干羊肚菌礼盒装（净重量80g）", "份", M_KG),
                product(P_VEG_350G, "有机苕尖350g", "份", M_KG)),
            List.of(product(M_EGG, "鸡蛋", "枚", null),
                product(M_KG, "羊肚菌原料", "kg", null)));

        List<StoreReturnStoreDailyVo> daily = service.queryStoreDailyList(new StoreReturnQuery());

        assertThat(daily).hasSize(1);
        // 0.080 + 0.350 = 0.430；鸡蛋 1 枚 + 蛋礼盒 30 份是**件数**，累进去就是旧口径那个 31.430
        assertThat(daily.get(0).getNonWeightReturnWeightTotal())
            .as("非重量产品退回重量必须剔掉件数口径行").isEqualByComparingTo("0.430");
        // 产品单位 = kg 的里脊肉只进「退货重量 / 确认重量」两列
        assertThat(daily.get(0).getReturnWeightTotal()).isEqualByComparingTo("2.000");
        assertThat(daily.get(0).getConfirmWeightTotal()).isEqualByComparingTo("2.000");
    }

    @Test
    @DisplayName("row15：产品单位非 kg + **原材料单位 kg**（80g 干货礼盒）→ 累加进非重量产品退回重量")
    void testStoreDaily_nonKgProductWithKgMaterialAccumulates() {
        prepareStoreDaily(
            List.of(dailyRow(P_MOREL_GIFT, "0", "0.080")),
            List.of(product(P_MOREL_GIFT, "干羊肚菌礼盒装（净重量80g）", "份", M_KG)),
            List.of(product(M_KG, "羊肚菌原料", "kg", null)));

        List<StoreReturnStoreDailyVo> daily = service.queryStoreDailyList(new StoreReturnQuery());

        assertThat(daily).hasSize(1);
        assertThat(daily.get(0).getNonWeightReturnWeightTotal()).isEqualByComparingTo("0.080");
        assertThat(daily.get(0).getConfirmWeightTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("row15：产品单位非 kg + **原材料单位也非 kg**（30 枚蛋礼盒按份）→ 不累加")
    void testStoreDaily_nonKgProductWithNonKgMaterialNotAccumulated() {
        prepareStoreDaily(
            List.of(dailyRow(P_EGG_GIFT, "0", "30.000")),
            List.of(product(P_EGG_GIFT, "30枚散养绿壳土鸡蛋礼盒装", "份", M_EGG)),
            List.of(product(M_EGG, "鸡蛋", "枚", null)));

        List<StoreReturnStoreDailyVo> daily = service.queryStoreDailyList(new StoreReturnQuery());

        assertThat(daily).hasSize(1);
        assertThat(daily.get(0).getNonWeightReturnWeightTotal())
            .as("30 份是件数，不能当 30 公斤累加").isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("row15：产品单位 = kg → 只进退货/确认重量两列，**不进**非重量产品退回重量")
    void testStoreDaily_kgProductNotInNonWeightColumn() {
        prepareStoreDaily(
            List.of(dailyRow(P_LOIN, "2.000", "1.900")),
            List.of(product(P_LOIN, "里脊肉", "kg", null)),
            List.of());

        List<StoreReturnStoreDailyVo> daily = service.queryStoreDailyList(new StoreReturnQuery());

        assertThat(daily).hasSize(1);
        assertThat(daily.get(0).getNonWeightReturnWeightTotal()).isEqualByComparingTo("0");
        assertThat(daily.get(0).getReturnWeightTotal()).isEqualByComparingTo("2.000");
        assertThat(daily.get(0).getConfirmWeightTotal()).isEqualByComparingTo("1.900");
        assertThat(daily.get(0).getWeightDiffTotal()).isEqualByComparingTo("0.100");
    }

    @Test
    @DisplayName("row15：原材料**无单位**时回落产品自身单位（枚）→ 仍按件数，不累加")
    void testStoreDaily_materialWithoutUnitFallsBackToProductUnit() {
        prepareStoreDaily(
            List.of(dailyRow(P_EGG, "0", "1.000")),
            List.of(product(P_EGG, "鸡蛋", "枚", M_EGG)),
            // 原材料存在但没配单位 → resolveMaterialUnits 不给值 → 回落产品单位「枚」
            List.of(product(M_EGG, "鸡蛋", null, null)));

        List<StoreReturnStoreDailyVo> daily = service.queryStoreDailyList(new StoreReturnQuery());

        assertThat(daily).hasSize(1);
        assertThat(daily.get(0).getNonWeightReturnWeightTotal()).isEqualByComparingTo("0");
    }

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
        // 上限来源 = **门店当日盘点台账**（期初 + 入库 − 销售 − 赠送，row205）。
        // 台账行必须带 productId，listLedgerCandidates 才认得出这个产品；不带的话候选为空 → 无上限
        // → 超额也放行（正是本用例要防的）。
        //
        // ⚠️ 这里**故意把 loss_qty 也填成 3.000**：口径是「期初 + 入库 − 销售 − 赠送」，**不减损坏**
        //（Kevin 2026-08-04：损坏的货本身就是要退回仓库的，减掉等于把它挡在退回之外）。
        // 所以上限仍是 3.000 —— 一旦有人把「− loss_qty」改回去，上限会塌成 0，
        // 本用例与 testBatchCreate_AllowsUpToArrivedQuantity 会立刻双双变红。
        StoreDailyLedger ledger = new StoreDailyLedger();
        ledger.setProductId(PRODUCT_ID);
        ledger.setInboundQty(new BigDecimal("3.000"));
        ledger.setLossQty(new BigDecimal("3.000"));
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(ledger));
        // 候选按 belong_type 分 tab，扇子骨属猪肉 tab
        dictProduct.setBelongType("pork");
        when(productInfoMapper.selectBatchIds(any())).thenReturn(List.of(dictProduct));
        when(baseMapper.selectList(any())).thenReturn(List.of());
    }
}
