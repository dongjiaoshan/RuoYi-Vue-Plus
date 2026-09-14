package org.dromara.djs.store.returns.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
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
 *   <li>V6-R214 退回候选与提交闸：候选唯一来源 = 字典 {@code djs_return_product_list}，按产品 belong_type
 *       分三个 tab（其他 tab 兜底），退回量不封顶，提交闸只剩「在不在清单里」一道成员资格</li>
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
                                       org.dromara.common.core.service.UserService us) {
            super(b, sm, pm, lm, g, pis, dm, ds, pps, ppm, iss, us);
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
            productProductionService, productProductionMapper, storeService, userService);
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

    // ---------- V6-R214：退回候选与提交闸（甲方 2026-09-13 row214 换口径）----------
    //
    // 新口径三句话：
    //   ① 候选唯一来源 = 字典 djs_return_product_list（Map 的 key = 产品业务码 product_id），
    //      不再由门店当日盘点台账推导 —— 退回操作因此不再依赖门店有没有盘点；
    //   ② tab 分流只看产品自身 belong_type：pork/white_bar → 猪肉、vegetable → 果蔬、
    //      **其余一律（含 belong_type=null）→ 其他**（甲方原话「其他的类型统一显示在其他产品里」，是兜底不是白名单）；
    //   ③ 退回量**不封顶**（候选 arrivedQuantity 恒 null），提交闸只剩「产品在不在清单里」这一道成员资格。
    //
    // 旧口径（账面可退量 期初+入库−销售−赠送 封顶 / 不在候选按三种成因分别报错 /
    // RETURNABLE_BELONG_TYPES 业态白名单）整条作废，对应用例已删，不做两边兼容。

    /** 清单外产品（验成员资格闸的拒绝路径）。 */
    private static final Long OTHER_PRODUCT_ID = 8009L;

    /** 果蔬「材料外售」成品折叠后的原材料产品。 */
    private static final Long VEG_MATERIAL_ID = 8102L;

    /** 退回产品清单字典（dict_value = 产品业务码，见 V202609081200__V6-R214-*.sql）。 */
    private static final String DICT_RETURN_PRODUCT_LIST = "djs_return_product_list";

    /** 清单里的一个产品：{@code id}=雪花主键、{@code code}=产品业务码（字典填的就是它）。 */
    private ProductInfo listedProduct(Long id, String code, String name, String belongType) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductId(code);
        p.setProductName(name);
        p.setProductUnit("kg");
        p.setBelongType(belongType);
        return p;
    }

    /**
     * 把这批产品配成「退回产品清单」。
     *
     * <p>取一次候选会打三次 {@code productInfoMapper.selectList}：按业务码 resolve 雪花 id、按 id 取详情、
     * 果蔬折叠时取原材料。三次一律返这同一批 —— mock 不执行 WHERE，多返的行由 service 自己按 tab /
     * 折叠门槛筛掉，比按调用次序摆 {@code thenReturn(a, b, c)} 稳得多（次序一变就静默错位）。</p>
     */
    /**
     * 比 {@link #stubReturnProductList} 精细一档的桩：**字典里配了谁**与**按 id 查得到谁**分开给。
     *
     * <p>`resolveReturnListProductIds` 是按业务码查（`product_id IN (...)`），其余链路按雪花主键查（`id IN (...)`），
     * 靠 wrapper 的 sqlSegment 区分 —— 一律返全部的 blanket 桩会让「只配成品」这个前提失效，
     * 原材料被当成也配进了字典，折叠那条路就测不到了。</p>
     */
    private void stubDictListedButQueryable(List<ProductInfo> listed, List<ProductInfo> queryable) {
        Map<String, String> dict = new LinkedHashMap<>();
        for (ProductInfo p : listed) {
            dict.put(p.getProductId(), p.getProductName());
        }
        for (ProductInfo p : queryable) {
            when(productInfoMapper.selectById(p.getId())).thenReturn(p);
        }
        when(dictService.getAllDictByDictType(DICT_RETURN_PRODUCT_LIST)).thenReturn(dict);
        when(productInfoMapper.selectList(any())).thenAnswer(inv -> {
            Object w = inv.getArgument(0);
            String seg = w == null ? "" : String.valueOf(
                ((com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) w).getSqlSegment());
            // 按业务码查 = 字典 resolve，只有配进字典的那些才算命中
            return seg.contains("product_id") ? listed : queryable;
        });
        when(baseMapper.selectList(any())).thenReturn(List.of());
    }

    private void stubReturnProductList(ProductInfo... listed) {
        List<ProductInfo> all = List.of(listed);
        Map<String, String> dict = new LinkedHashMap<>();
        for (ProductInfo p : all) {
            // getAllDictByDictType 返的是 dictValue → dictLabel，所以 key 才是产品业务码
            dict.put(p.getProductId(), p.getProductName());
            when(productInfoMapper.selectById(p.getId())).thenReturn(p);
        }
        when(dictService.getAllDictByDictType(DICT_RETURN_PRODUCT_LIST)).thenReturn(dict);
        when(productInfoMapper.selectList(any())).thenReturn(all);
        when(baseMapper.selectList(any())).thenReturn(List.of());
    }

    private StoreReturnBatchBo batchOf(String qty) {
        return batchOf(PRODUCT_ID, qty);
    }

    private StoreReturnBatchBo batchOf(Long productId, String qty) {
        StoreReturnBatchBo.Item item = new StoreReturnBatchBo.Item();
        item.setProductId(productId);
        item.setReturnQuantity(new BigDecimal(qty));
        item.setReturnWeight(new BigDecimal(qty));
        StoreReturnBatchBo batch = new StoreReturnBatchBo();
        batch.setStoreId(STORE_ID);
        batch.setItems(List.of(item));
        return batch;
    }

    @Test
    @DisplayName("row214：清单内产品退回量**不封顶** —— 退 999999 也放行（甲方「对于其退回量不做限制」）")
    void testBatchCreate_allowsHugeQuantityWhenInReturnList() {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00109", "扇子骨", "pork"));

        assertThat(service.batchCreate(batchOf("999999"))).isEqualTo(1);

        ArgumentCaptor<StoreReturn> cap = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getReturnQuantity()).isEqualByComparingTo("999999");
        assertThat(cap.getValue().getReturnDirection()).isEqualTo("store_to_warehouse");
        assertThat(cap.getValue().getReturnStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("row214：清单外产品提交 → 400，文案指向 admin 字典管理（不再说「请先去盘点」）")
    void testBatchCreate_rejectsProductNotInReturnList() {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00109", "扇子骨", "pork"));
        when(productInfoMapper.selectById(OTHER_PRODUCT_ID))
            .thenReturn(listedProduct(OTHER_PRODUCT_ID, "Y00999", "没配进清单的生菜", "vegetable"));

        Throwable thrown = catchThrowable(() -> service.batchCreate(batchOf(OTHER_PRODUCT_ID, "1.000")));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        assertThat(((ServiceException) thrown).getCode()).isEqualTo(400);
        assertThat(thrown.getMessage())
            .contains("没配进清单的生菜")
            .contains("不在「退回产品清单」里")
            .contains("字典管理")
            // 台账口径已作废：再让人去盘点就是指死路（盘完照样退不了）
            .doesNotContain("盘点");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("row214：字典未配（空清单）→ 三个 tab 全空，且任何产品提交一律被拒")
    void testEmptyDict_allTabsEmptyAndSubmitRejected() {
        when(dictService.getAllDictByDictType(anyString())).thenReturn(Map.of());
        when(productInfoMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.selectList(any())).thenReturn(List.of());
        when(productInfoMapper.selectById(PRODUCT_ID))
            .thenReturn(listedProduct(PRODUCT_ID, "Y00109", "扇子骨", "pork"));

        assertThat(service.listPorkCandidates(STORE_ID)).as("猪肉 tab").isEmpty();
        assertThat(service.listVegCandidates(STORE_ID)).as("果蔬 tab").isEmpty();
        assertThat(service.listOtherCandidates(STORE_ID)).as("其他 tab").isEmpty();

        assertThatThrownBy(() -> service.batchCreate(batchOf("1.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不在「退回产品清单」里");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("row214：belong_type=NULL 的外购产品落「其他」tab（兜底不是白名单）—— 不丢也不 NPE")
    void testCandidates_nullBelongTypeFallsToOtherTab() {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00901", "外购未分类品", null));

        List<StoreReturnVegCandidateVo> other = service.listOtherCandidates(STORE_ID);
        assertThat(other).hasSize(1);
        assertThat(other.get(0).getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(other.get(0).getProductName()).isEqualTo("外购未分类品");
        assertThat(other.get(0).getBelongType()).isNull();
        assertThat(service.listPorkCandidates(STORE_ID)).as("不该窜进猪肉 tab").isEmpty();
        assertThat(service.listVegCandidates(STORE_ID)).as("不该窜进果蔬 tab").isEmpty();
    }

    @Test
    @DisplayName("row214：**提交闸**对 belong_type=NULL 的产品不得 NPE —— 在清单里就照常放行")
    void testBatchCreate_nullBelongTypeInListPasses() {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00901", "外购未分类品", null));

        assertThat(service.batchCreate(batchOf("1.000"))).isEqualTo(1);
        verify(baseMapper, times(1)).insert(any(StoreReturn.class));
    }

    @ParameterizedTest(name = "belong_type={0} → {1} tab")
    @CsvSource({"pork,pork", "white_bar,pork", "vegetable,veg", "dry_good,other", "egg,other", "package,other"})
    @DisplayName("row214：tab 分流只看产品自身 belong_type，猪肉 / 果蔬之外一律落「其他」")
    void testCandidates_tabRoutingByBelongType(String belongType, String tab) {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00109", "测试产品", belongType));

        assertThat(service.listPorkCandidates(STORE_ID)).as("猪肉 tab").hasSize("pork".equals(tab) ? 1 : 0);
        assertThat(service.listVegCandidates(STORE_ID)).as("果蔬 tab").hasSize("veg".equals(tab) ? 1 : 0);
        assertThat(service.listOtherCandidates(STORE_ID)).as("其他 tab").hasSize("other".equals(tab) ? 1 : 0);
    }

    @ParameterizedTest(name = "belong_type={0} → subCategory={1}")
    @CsvSource({"pork,pork", "white_bar,white_bar"})
    @DisplayName("row214：白条仍留在猪肉 tab，只用 subCategory 区分展示（Kevin 口径，不单开 tab）")
    void testPorkCandidates_subCategoryByBelongType(String belongType, String subCategory) {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00109", "测试产品", belongType));

        assertThat(service.listPorkCandidates(STORE_ID)).hasSize(1);
        assertThat(service.listPorkCandidates(STORE_ID).get(0).getSubCategory()).isEqualTo(subCategory);
    }

    @Test
    @DisplayName("row214：猪肉 / 其他 tab 候选的 arrivedQuantity 恒为 null = 不封顶（前端据此把 :max 放开）")
    void testCandidates_arrivedQuantityIsNullMeaningNoCap() {
        stubReturnProductList(
            listedProduct(PRODUCT_ID, "Y00109", "扇子骨", "pork"),
            listedProduct(OTHER_PRODUCT_ID, "Y00301", "干羊肚菌", "dry_good"));

        assertThat(service.listPorkCandidates(STORE_ID).get(0).getArrivedQuantity())
            .as("猪肉 tab 一旦下发上限，前端 maxOf 就又开始封顶了").isNull();
        assertThat(service.listOtherCandidates(STORE_ID).get(0).getArrivedQuantity())
            .as("其他 tab 一旦下发上限，前端 maxOf 就又开始封顶了").isNull();
    }

    @Test
    @DisplayName("row214：**果蔬 tab** 候选的 arrivedQuantity 同样必须是 null —— 给 0 会被前端当「无可退量」把输入框禁掉")
    void testVegCandidates_arrivedQuantityIsNullNotZero() {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00201", "上海青", "vegetable"));

        List<StoreReturnVegCandidateVo> veg = service.listVegCandidates(STORE_ID);
        assertThat(veg).hasSize(1);
        // admin ReturnOperationPanel.maxOf()：null → Infinity（不封顶）；0 → max=0 → 输入框禁用 +「无可退量」
        assertThat(veg.get(0).getArrivedQuantity())
            .as("果蔬 tab 下发 0 = 整列输入框被禁，等于退回操作果蔬没法用").isNull();
    }

    @Test
    @DisplayName("row214：果蔬「材料外售」成品折叠成原材料（清单里配的是成品，退回入库要落到原材料头上）")
    void testVegCandidates_foldsMaterialSoldToMaterial() {
        ProductInfo finished = listedProduct(PRODUCT_ID, "Y00201", "有机苕尖350g", "vegetable");
        finished.setProductUnit("份");
        finished.setIsMaterialSold(1);
        finished.setProductMaterial(VEG_MATERIAL_ID);
        // 原材料自身 belong_type 留空 → 它自己落「其他」tab，不干扰果蔬 tab 的折叠断言
        ProductInfo material = listedProduct(VEG_MATERIAL_ID, "Y00202", "苕尖", null);
        stubReturnProductList(finished, material);
        StoreReturn returned = new StoreReturn();
        returned.setProductId(VEG_MATERIAL_ID);
        returned.setReturnQuantity(new BigDecimal("4.000"));
        when(baseMapper.selectList(any())).thenReturn(List.of(returned));

        List<StoreReturnVegCandidateVo> veg = service.listVegCandidates(STORE_ID);

        assertThat(veg).hasSize(1);
        assertThat(veg.get(0).getProductId()).as("折叠后要换成原材料 id").isEqualTo(VEG_MATERIAL_ID);
        assertThat(veg.get(0).getProductName()).isEqualTo("苕尖");
        assertThat(veg.get(0).getProductUnit()).isEqualTo("kg");
        // 已退量按**折叠后的最终 id** 回填 —— 折叠前填会落在成品头上，录入页显示恒 0
        assertThat(veg.get(0).getReturnedQuantity()).isEqualByComparingTo("4.000");
    }

    @Test
    @DisplayName("row214：只把**成品**配进清单，折叠成原材料后仍然能提交 —— 候选点得到就必须提交得了")
    void testBatchCreate_foldedMaterialPassesGateWhenOnlyFinishedIsListed() {
        // 客户在字典里配的是他在产品列表里看到的成品；候选会被 foldVegMaterialSold 换成原材料 id，
        // 闸若只比字典本身 → 候选里点得到、一提交必 400，正是换口径前那段注释警告过的坑。
        ProductInfo finished = listedProduct(PRODUCT_ID, "Y00201", "有机苕尖350g", "vegetable");
        finished.setProductUnit("份");
        finished.setIsMaterialSold(1);
        finished.setProductMaterial(VEG_MATERIAL_ID);
        ProductInfo material = listedProduct(VEG_MATERIAL_ID, "Y00202", "苕尖", "vegetable");
        // 字典里**只配成品**（现实里客户配的就是产品列表里看得见的那个），但按 id 查得到原材料
        stubDictListedButQueryable(List.of(finished), List.of(finished, material));
        when(productInfoMapper.selectById(VEG_MATERIAL_ID)).thenReturn(material);

        // 候选给出来的就是原材料 id
        List<StoreReturnVegCandidateVo> veg = service.listVegCandidates(STORE_ID);
        assertThat(veg).hasSize(1);
        assertThat(veg.get(0).getProductId()).isEqualTo(VEG_MATERIAL_ID);

        // 拿候选给的那个 id 提交，必须过闸
        StoreReturnBatchBo bo = new StoreReturnBatchBo();
        bo.setStoreId(STORE_ID);
        StoreReturnBatchBo.Item item = new StoreReturnBatchBo.Item();
        item.setProductId(VEG_MATERIAL_ID);
        item.setReturnQuantity(new BigDecimal("2.000"));
        bo.setItems(List.of(item));

        assertThatCode(() -> service.batchCreate(bo))
            .as("清单里配的成品折叠成原材料后被闸拒 = 候选点得到却提交不了")
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("row214：折叠放行只限清单产品自己的原材料，别的原材料照样拒")
    void testBatchCreate_unrelatedMaterialStillRejected() {
        ProductInfo finished = listedProduct(PRODUCT_ID, "Y00201", "有机苕尖350g", "vegetable");
        finished.setIsMaterialSold(1);
        finished.setProductMaterial(VEG_MATERIAL_ID);
        ProductInfo material = listedProduct(VEG_MATERIAL_ID, "Y00202", "苕尖", "vegetable");
        ProductInfo unrelated = listedProduct(9_999_001L, "Y00999", "毫不相干的原材料", "vegetable");
        stubDictListedButQueryable(List.of(finished), List.of(finished, material, unrelated));
        when(productInfoMapper.selectById(9_999_001L)).thenReturn(unrelated);

        StoreReturnBatchBo bo = new StoreReturnBatchBo();
        bo.setStoreId(STORE_ID);
        StoreReturnBatchBo.Item item = new StoreReturnBatchBo.Item();
        item.setProductId(9_999_001L);
        item.setReturnQuantity(new BigDecimal("1.000"));
        bo.setItems(List.of(item));

        assertThatThrownBy(() -> service.batchCreate(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不在「退回产品清单」里");
    }

    @Test
    @DisplayName("row214：礼盒配进清单也不出现在候选里 —— 列出来只会让工人白填一遍再吃 400")
    void testOtherCandidates_giftBoxNotListedEvenWhenInReturnList() {
        stubReturnProductList(
            listedProduct(PRODUCT_ID, "Y00500", "有机蔬菜盲盒3.5斤", "gift_box"),
            listedProduct(9_999_002L, "Y00501", "大米10斤", "other"));

        List<StoreReturnVegCandidateVo> other = service.listOtherCandidates(STORE_ID);

        assertThat(other).extracting(StoreReturnVegCandidateVo::getProductName)
            .containsExactly("大米10斤");
    }

    @ParameterizedTest(name = "belong_type={0}（{1} tab）候选必须回填已退量")
    @CsvSource({"pork,pork", "white_bar,pork", "vegetable,veg", "dry_good,other"})
    @DisplayName("row214：三个 tab 都要回填「今日已退量」—— 不再用于封顶，但录入页要显示今天已经退过多少")
    void testCandidates_allTabsFillReturnedQuantity(String belongType, String tab) {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00109", "测试产品", belongType));
        StoreReturn returned = new StoreReturn();
        returned.setProductId(PRODUCT_ID);
        returned.setReturnQuantity(new BigDecimal("4.000"));
        when(baseMapper.selectList(any())).thenReturn(List.of(returned));

        BigDecimal actual = switch (tab) {
            case "pork" -> service.listPorkCandidates(STORE_ID).get(0).getReturnedQuantity();
            case "veg" -> service.listVegCandidates(STORE_ID).get(0).getReturnedQuantity();
            default -> service.listOtherCandidates(STORE_ID).get(0).getReturnedQuantity();
        };
        assertThat(actual).as("%s tab 漏回填已退量 → 录入页看不到今天退过多少", tab).isEqualByComparingTo("4.000");
    }

    @Test
    @DisplayName("row178+row214：礼盒即便配进了清单也拒 —— 礼盒闸排在清单闸前面")
    void testBatchCreate_giftBoxRejectedEvenWhenInReturnList() {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00500", "有机蔬菜盲盒3.5斤", "gift_box"));

        assertThatThrownBy(() -> service.batchCreate(batchOf("1.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("礼盒");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("row214：**insertByBo** 门店退仓库方向同样只过清单闸 —— 清单外产品必拒且不写库存")
    void testInsertByBo_storeToWarehouseRejectsNotInReturnList() {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00109", "扇子骨", "pork"));
        when(productInfoMapper.selectById(OTHER_PRODUCT_ID))
            .thenReturn(listedProduct(OTHER_PRODUCT_ID, "Y00999", "没配进清单的生菜", "vegetable"));

        StoreReturnBo b = bo("store_to_warehouse", STORE_ID);
        b.setProductId(OTHER_PRODUCT_ID);

        assertThatThrownBy(() -> service.insertByBo(b))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不在「退回产品清单」里");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
        // 这条路是即时真写 location_stock 的，闸必须挡在 inbound 之前
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("row214：**insertByBo** 清单内产品退多少都放行（数量放开，产品范围没放开）")
    void testInsertByBo_storeToWarehouseAllowsHugeQuantityWhenInList() {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00109", "扇子骨", "pork"));

        StoreReturnBo b = bo("store_to_warehouse", STORE_ID);
        b.setReturnQuantity(new BigDecimal("999999"));

        assertThat(service.insertByBo(b)).isNotNull();
        verify(baseMapper, times(1)).insert(any(StoreReturn.class));
        verify(purchaseInService, times(1)).inboundReturnBasket(
            eq(PRODUCT_ID), eq(LOCATION_ID), eq(new BigDecimal("999999")), eq(FLOW_RETURN_IN), any());
    }

    @Test
    @DisplayName("row214：顾客退门店（customer_to_store）**不过**清单闸 —— 清单管的是门店退仓库那条路")
    void testInsertByBo_customerToStoreNotGatedByReturnList() {
        when(dictService.getAllDictByDictType(anyString())).thenReturn(Map.of());

        assertThat(service.insertByBo(bo("customer_to_store", STORE_ID))).isNotNull();
        verify(baseMapper, times(1)).insert(any(StoreReturn.class));
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
}
