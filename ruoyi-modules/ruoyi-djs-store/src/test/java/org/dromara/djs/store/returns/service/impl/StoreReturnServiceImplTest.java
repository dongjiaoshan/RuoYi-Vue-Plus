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
import org.dromara.djs.store.returns.domain.bo.StoreReturnUnitBo;
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

    // ==========================================================================
    // STR-RETURN-OPS-001 · admin「门店退回操作」（退回类型 / 三类品类数 / 单位退回新增）
    // ==========================================================================
    //
    // 三条最容易做反的：
    //   ① 单位退回**没有门店** —— store_id 必须恒 NULL（塞假门店 id 会污染门店盘点候选 /
    //      退回记录按门店分组 / store-daily 汇总）；退回单位落新列 return_unit。
    //   ② 「退回处理」的仓库确认量按**退回单位**录，提交前乘 material_num 换算回**原材料量** ——
    //      与 mp metric.ts#toConfirmWeight 同一套，否则同一张单两端口径不同。
    //   ③ 三个品类数必须**后端算**（前端 filter 当前页在分页/导出时必错），分流复用 returnTabOf。

    /** 退回单位配置字典（值取自出库去向）。 */
    private static final String DICT_RETURN_UNIT = "djs_return_unit";

    /**
     * 退回单位字典**值**（= 前端提交的 returnUnit）。
     *
     * <p>`getAllDictByDictType` 返 dictValue → dictLabel，成员资格判的是 key —— 桩里 key 必须是这个值，
     * 否则用例会把「拿 label 比 value」的错口径固化下来（E2E 实测就是这么漏过去的）。</p>
     */
    private static final String UNIT_VALUE = "mine";

    /** 30 枚礼盒的原材料（按枚计）。 */
    private static final Long M_EGG_2 = 9201L;

    @Test
    @DisplayName("OPS-001：toConfirmWeight —— 非 kg 行乘计量规则换算回原材料量（1 份 × 30 = 30 枚）")
    void testToConfirmWeight_ratioAppliedForNonKg() {
        ProductInfo gift = product(P_EGG_GIFT, "30枚散养绿壳土鸡蛋礼盒装", "份", M_EGG_2);
        gift.setMaterialNum(new BigDecimal("30"));

        assertThat(StoreReturnServiceImpl.toConfirmWeight(gift, new BigDecimal("1")))
            .as("1 份 30 枚礼盒 → 原材料 30 枚（与 mp metric.ts 同源）").isEqualByComparingTo("30");
        assertThat(StoreReturnServiceImpl.toConfirmWeight(gift, new BigDecimal("2")))
            .isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("OPS-001：toConfirmWeight —— kg 行原样透传（退回单位就是原材料单位，material_num 恒 1）")
    void testToConfirmWeight_kgPassthrough() {
        ProductInfo loin = product(P_LOIN, "里脊肉", "kg", null);
        assertThat(StoreReturnServiceImpl.toConfirmWeight(loin, new BigDecimal("2.350")))
            .isEqualByComparingTo("2.350");
    }

    @Test
    @DisplayName("OPS-001：toConfirmWeight —— 非 kg 行按 material_num 折算 (0.25)，结果裁到 3 位（DECIMAL(12,3)）")
    void testToConfirmWeight_quarterRatioRoundedTo3() {
        ProductInfo veg = product(P_VEG_350G, "有机苕尖250g", "份", M_KG);
        veg.setMaterialNum(new BigDecimal("0.25"));
        assertThat(StoreReturnServiceImpl.toConfirmWeight(veg, new BigDecimal("3")))
            .isEqualByComparingTo("0.750");
    }

    @Test
    @DisplayName("OPS-001：canConvert —— 单位不同又没配计量规则 → false（瞎猜会把「3 只」记成「3 kg」进库存）")
    void testCanConvert_missingRatioIsRejected() {
        ProductInfo eggGift = product(P_EGG_GIFT, "蛋礼盒", "份", M_EGG_2);
        assertThat(StoreReturnServiceImpl.canConvert(eggGift, "枚")).isFalse();

        eggGift.setMaterialNum(new BigDecimal("30"));
        assertThat(StoreReturnServiceImpl.canConvert(eggGift, "枚")).isTrue();
    }

    @Test
    @DisplayName("OPS-001：canConvert —— kg 行 / 单位相同 恒 true（不需要换算，ratio 缺省无害）")
    void testCanConvert_sameUnitAlwaysTrue() {
        assertThat(StoreReturnServiceImpl.canConvert(product(P_LOIN, "里脊肉", "kg", null), "kg")).isTrue();
        assertThat(StoreReturnServiceImpl.canConvert(product(P_EGG, "鸡蛋", "枚", null), "枚")).isTrue();
    }

    private StoreReturnUnitBo unitBo(String qty, Integer isDiscard) {
        StoreReturnUnitBo.Item item = new StoreReturnUnitBo.Item();
        item.setProductId(PRODUCT_ID);
        item.setReturnQuantity(new BigDecimal(qty));
        item.setLocationId(LOCATION_ID);
        item.setIsDiscard(isDiscard);
        StoreReturnUnitBo bo = new StoreReturnUnitBo();
        bo.setReturnDate(java.time.LocalDate.of(2026, 9, 20));
        bo.setReturnUnit(UNIT_VALUE);
        bo.setItems(List.of(item));
        return bo;
    }

    @Test
    @DisplayName("OPS-001：单位退回新增 —— store_id 恒 NULL（绝不复用门店列）、return_type=unit、建单即 received、未丢弃行写入库")
    void testCreateUnitReturns_happy() {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00109", "扇子骨", "pork"));
        when(dictService.getAllDictByDictType(DICT_RETURN_UNIT)).thenReturn(Map.of(UNIT_VALUE, "矿山"));

        int created = service.createUnitReturns(unitBo("3.500", 0));

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<StoreReturn> cap = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        StoreReturn e = cap.getValue();
        // 🔴 反作弊核心：单位退回没有门店
        assertThat(e.getStoreId()).as("单位退回塞了门店 id 会污染门店维度所有统计").isNull();
        assertThat(e.getReturnType()).isEqualTo("unit");
        assertThat(e.getReturnUnit()).isEqualTo(UNIT_VALUE);
        assertThat(e.getReturnDirection()).isEqualTo("store_to_warehouse");
        // 甲方「退回状态默认为已处理」+ 四个人员时间列 = 当前操作人与当前时刻
        assertThat(e.getReturnStatus()).isEqualTo("received");
        assertThat(e.getOperatorId()).isEqualTo(USER_ID);
        assertThat(e.getConfirmUserId()).isEqualTo(USER_ID);
        assertThat(e.getConfirmTime()).isNotNull();
        assertThat(e.getIsDiscard()).isZero();
        // 退回日期 = 甲方填的日期（只到天）
        assertThat(e.getReturnDate()).isEqualTo(LocalDateTime.of(2026, 9, 20, 0, 0));
        // kg 产品：报退货物重量取退回量本身；确认量同为 3.5
        assertThat(e.getGoodsWeight()).isEqualByComparingTo("3.500");
        assertThat(e.getReceivedQty()).isEqualByComparingTo("3.500");
        assertThat(e.getReceivedWeight()).isEqualByComparingTo("3.500");
        // 未丢弃 → 写入库（入库方式「门店退回」）
        ArgumentCaptor<String> remark = ArgumentCaptor.forClass(String.class);
        verify(purchaseInService, times(1)).inboundReturnBasket(
            eq(PRODUCT_ID), eq(LOCATION_ID), eq(new BigDecimal("3.500")), eq(FLOW_RETURN_IN), remark.capture());
        assertThat(remark.getValue()).contains("单位退回入库").contains(RETURN_NO);
    }

    @Test
    @DisplayName("OPS-001：单位退回新增 —— 非 kg 行入库量按计量规则换算（1 份 × 30 = 30 枚）")
    void testCreateUnitReturns_convertsToMaterialQty() {
        ProductInfo gift = listedProduct(PRODUCT_ID, "Y00500", "30枚散养绿壳土鸡蛋礼盒装", "other");
        gift.setProductUnit("份");
        gift.setProductMaterial(M_EGG_2);
        gift.setMaterialNum(new BigDecimal("30"));
        gift.setProductAttr(2); // 原材料本身存在 → canInbound 走 material != null 分支
        ProductInfo material = product(M_EGG_2, "鸡蛋", "枚", null);
        stubDictListedButQueryable(List.of(gift), List.of(gift, material));
        when(dictService.getAllDictByDictType(DICT_RETURN_UNIT)).thenReturn(Map.of(UNIT_VALUE, "矿山"));

        service.createUnitReturns(unitBo("1", 0));

        ArgumentCaptor<StoreReturn> cap = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        // 界面录 1 份，落库必须是原材料 30 枚
        assertThat(cap.getValue().getReceivedWeight()).isEqualByComparingTo("30");
        assertThat(cap.getValue().getReceivedQty()).isEqualByComparingTo("30");
        verify(purchaseInService, times(1)).inboundReturnBasket(
            any(), any(), eq(new BigDecimal("30.000")), eq(FLOW_RETURN_IN), any());
    }

    @Test
    @DisplayName("OPS-001：单位退回新增 —— 丢弃行**不写任何库存流水**（但照常建 received 行）")
    void testCreateUnitReturns_discardSkipsInbound() {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00109", "扇子骨", "pork"));
        when(dictService.getAllDictByDictType(DICT_RETURN_UNIT)).thenReturn(Map.of(UNIT_VALUE, "矿山"));

        assertThat(service.createUnitReturns(unitBo("2.000", 1))).isEqualTo(1);

        ArgumentCaptor<StoreReturn> cap = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getIsDiscard()).isEqualTo(1);
        assertThat(cap.getValue().getReturnStatus()).isEqualTo("received");
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("OPS-001：单位退回新增 —— 清单外产品一律拒（与门店退回同一道闸，防凭空造仓库库存）")
    void testCreateUnitReturns_rejectsProductNotInReturnList() {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00109", "扇子骨", "pork"));
        when(productInfoMapper.selectById(OTHER_PRODUCT_ID))
            .thenReturn(listedProduct(OTHER_PRODUCT_ID, "Y00999", "没配进清单的生菜", "vegetable"));
        when(dictService.getAllDictByDictType(DICT_RETURN_UNIT)).thenReturn(Map.of(UNIT_VALUE, "矿山"));

        StoreReturnUnitBo bo = unitBo("1.000", 0);
        bo.getItems().get(0).setProductId(OTHER_PRODUCT_ID);

        assertThatThrownBy(() -> service.createUnitReturns(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不在「退回产品清单」里");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("OPS-001：单位退回新增 —— 成品没配原材料又不能丢弃时拒（仓库只存原材料，无从记账）")
    void testCreateUnitReturns_rejectsInboundWithoutMaterial() {
        ProductInfo finishedNoMaterial = listedProduct(PRODUCT_ID, "Y00201", "有机苕尖350g", "vegetable");
        finishedNoMaterial.setProductUnit("份");
        finishedNoMaterial.setProductAttr(1); // 成品
        stubReturnProductList(finishedNoMaterial);
        when(dictService.getAllDictByDictType(DICT_RETURN_UNIT)).thenReturn(Map.of(UNIT_VALUE, "矿山"));

        assertThatThrownBy(() -> service.createUnitReturns(unitBo("1.000", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("未配置原材料");
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("OPS-001：单位退回新增 —— 退回单位不在「退回单位配置」字典里 → 400")
    void testCreateUnitReturns_rejectsUnknownUnit() {
        stubReturnProductList(listedProduct(PRODUCT_ID, "Y00109", "扇子骨", "pork"));
        when(dictService.getAllDictByDictType(DICT_RETURN_UNIT)).thenReturn(Map.of("kitchen", "厨房"));

        assertThatThrownBy(() -> service.createUnitReturns(unitBo("1.000", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不在「退回单位配置」字典里");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("OPS-001：store-daily 按「类型 + 门店/退回单位」分组 —— 单位退回不与门店退回折成一组，且三类品类数后端算")
    void testStoreDaily_splitsUnitReturnAndCountsKinds() {
        StoreReturn storeRow = new StoreReturn();
        storeRow.setId(1L);
        storeRow.setStoreId(STORE_ID);
        storeRow.setReturnType("store");
        storeRow.setProductId(P_LOIN);
        storeRow.setReturnDate(LocalDateTime.of(2026, 9, 20, 9, 0));
        storeRow.setReturnStatus("pending");
        storeRow.setOperatorId(USER_ID);

        StoreReturn unitPork = new StoreReturn();
        unitPork.setId(2L);
        unitPork.setReturnUnit(UNIT_VALUE);
        unitPork.setReturnType("unit");
        unitPork.setProductId(P_EGG);
        unitPork.setReturnDate(LocalDateTime.of(2026, 9, 20, 1, 0));
        unitPork.setReturnStatus("received");
        unitPork.setOperatorId(USER_ID);
        unitPork.setConfirmUserId(USER_ID);

        StoreReturn unitVeg = new StoreReturn();
        unitVeg.setId(3L);
        unitVeg.setReturnUnit(UNIT_VALUE);
        unitVeg.setReturnType("unit");
        unitVeg.setProductId(P_VEG_350G);
        unitVeg.setReturnDate(LocalDateTime.of(2026, 9, 20, 1, 0));
        unitVeg.setReturnStatus("received");
        unitVeg.setOperatorId(USER_ID);

        when(baseMapper.selectList(any())).thenReturn(List.of(storeRow, unitPork, unitVeg));
        when(productInfoMapper.selectList(any())).thenReturn(List.of(
            productWithBelong(P_LOIN, "里脊肉", "kg", null, "pork"),
            productWithBelong(P_EGG, "鸡蛋", "枚", null, "egg"),
            productWithBelong(P_VEG_350G, "有机苕尖350g", "份", null, "vegetable")));
        when(dictService.getAllDictByDictType(DICT_RETURN_UNIT)).thenReturn(Map.of(UNIT_VALUE, "矿山"));

        List<StoreReturnStoreDailyVo> daily = service.queryStoreDailyList(new StoreReturnQuery());

        assertThat(daily).as("门店退回 1 组 + 单位退回 1 组").hasSize(2);
        StoreReturnStoreDailyVo unit = daily.stream()
            .filter(v -> "unit".equals(v.getReturnType())).findFirst().orElseThrow();
        assertThat(unit.getStoreId()).isNull();
        // 甲方 row213 第 5 条：「退回门店为选择的退回单位**名称**」。库里 return_unit 存的是字典 **value**
        // （出库去向那套值是拼音，如 yejiazhuang_cun），直接下发会让整列显示拼音码。
        assertThat(unit.getStoreName()).as("「退回门店」列必须换成字典 label").isEqualTo("矿山");
        assertThat(unit.getReturnUnit()).as("returnUnit 仍是 value —— 抽屉要拿它当查询键定位这张单")
            .isEqualTo(UNIT_VALUE);
        assertThat(unit.getReturnStatus()).isEqualTo("received");
        assertThat(unit.getTotalCount()).isEqualTo(2);
        assertThat(unit.getPorkKindCount()).isZero();
        assertThat(unit.getVegKindCount()).isEqualTo(1);
        assertThat(unit.getOtherKindCount()).as("鸡蛋（belong_type=egg）落「其他产品」").isEqualTo(1);
        assertThat(unit.getProductKindCount()).isEqualTo(2);

        StoreReturnStoreDailyVo store = daily.stream()
            .filter(v -> "store".equals(v.getReturnType())).findFirst().orElseThrow();
        assertThat(store.getStoreId()).isEqualTo(STORE_ID);
        assertThat(store.getReturnStatus()).as("还有 pending 行 → 整单待处理").isEqualTo("pending");
        assertThat(store.getPorkKindCount()).isEqualTo(1);
        assertThat(store.getVegKindCount()).isZero();
        assertThat(store.getOtherKindCount()).isZero();
    }

    @Test
    @DisplayName("OPS-001：「退回状态」是**组级**结论 —— 按已处理筛，组内还有待处理行的那张单不能被判成已处理")
    void testStoreDaily_statusFilterAppliesAtGroupLevel() {
        // 同一张单（同门店同日）三行：2 行已确认、1 行还没确认 —— mp 逐行确认，这种混合态是常态
        StoreReturn done1 = new StoreReturn();
        done1.setId(1L);
        done1.setStoreId(STORE_ID);
        done1.setReturnType("store");
        done1.setProductId(P_LOIN);
        done1.setReturnDate(LocalDateTime.of(2026, 9, 20, 9, 0));
        done1.setReturnStatus("received");
        done1.setOperatorId(USER_ID);
        done1.setConfirmUserId(USER_ID);

        StoreReturn done2 = new StoreReturn();
        done2.setId(2L);
        done2.setStoreId(STORE_ID);
        done2.setReturnType("store");
        done2.setProductId(P_EGG);
        done2.setReturnDate(LocalDateTime.of(2026, 9, 20, 9, 0));
        done2.setReturnStatus("received");
        done2.setOperatorId(USER_ID);
        done2.setConfirmUserId(USER_ID);

        StoreReturn pending = new StoreReturn();
        pending.setId(3L);
        pending.setStoreId(STORE_ID);
        pending.setReturnType("store");
        pending.setProductId(P_VEG_350G);
        pending.setReturnDate(LocalDateTime.of(2026, 9, 20, 9, 0));
        pending.setReturnStatus("pending");
        pending.setOperatorId(USER_ID);

        when(baseMapper.selectList(any())).thenReturn(List.of(done1, done2, pending));
        when(productInfoMapper.selectList(any())).thenReturn(List.of(
            productWithBelong(P_LOIN, "里脊肉", "kg", null, "pork"),
            productWithBelong(P_EGG, "鸡蛋", "枚", null, "egg"),
            productWithBelong(P_VEG_350G, "有机苕尖350g", "份", null, "vegetable")));

        StoreReturnQuery received = new StoreReturnQuery();
        received.setReturnStatus("received");
        // 状态若被下推到行级，SQL 只回那 2 行已确认的 → 组内全 received → 整组被判「已处理」，
        // 操作列变「查看详情」，剩下那行待处理的货再也点不开；品类数也只数到 2 类。
        assertThat(service.queryStoreDailyList(received))
            .as("组内还有待处理行 → 不该出现在「已处理」结果里").isEmpty();

        StoreReturnQuery pendingQuery = new StoreReturnQuery();
        pendingQuery.setReturnStatus("pending");
        List<StoreReturnStoreDailyVo> daily = service.queryStoreDailyList(pendingQuery);
        assertThat(daily).hasSize(1);
        assertThat(daily.get(0).getReturnStatus()).isEqualTo("pending");
        assertThat(daily.get(0).getTotalCount()).as("品类数/条数按整组算，不是按筛剩的行算").isEqualTo(3);
        assertThat(daily.get(0).getPorkKindCount()).isEqualTo(1);
        assertThat(daily.get(0).getVegKindCount()).isEqualTo(1);
        assertThat(daily.get(0).getOtherKindCount()).isEqualTo(1);
    }

    private ProductInfo productWithBelong(Long id, String name, String unit, Long material, String belongType) {
        ProductInfo p = product(id, name, unit, material);
        p.setBelongType(belongType);
        return p;
    }

    @Test
    @DisplayName("OPS-001：抽屉明细 —— 单位退回缺 returnUnit 直接 400（没有门店，只靠日期会串单）")
    void testOperationItems_unitTypeRequiresReturnUnit() {
        StoreReturnQuery q = new StoreReturnQuery();
        q.setReturnType("unit");

        assertThatThrownBy(() -> service.listOperationItems(q))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("必须指定退回单位");
    }

    @Test
    @DisplayName("OPS-001：抽屉明细 —— 随行下发判据（计量规则 / 清单内 / 能否入库 / 能否换算），非 kg 清单内才有两位小数")
    void testOperationItems_carriesMetricJudgements() {
        ProductInfo eggGift = listedProduct(PRODUCT_ID, "Y00500", "30枚散养绿壳土鸡蛋礼盒装", "other");
        eggGift.setProductUnit("份");
        eggGift.setProductMaterial(M_EGG_2);
        eggGift.setMaterialNum(new BigDecimal("30"));
        eggGift.setProductAttr(2);
        stubDictListedButQueryable(List.of(eggGift), List.of(eggGift, product(M_EGG_2, "鸡蛋", "枚", null)));

        StoreReturn row = new StoreReturn();
        row.setId(11L);
        row.setStoreId(STORE_ID);
        row.setReturnType("store");
        row.setProductId(PRODUCT_ID);
        row.setReturnQuantity(new BigDecimal("2"));
        row.setReturnDate(LocalDateTime.of(2026, 9, 20, 9, 0));
        row.setReturnStatus("pending");
        when(baseMapper.selectList(any())).thenReturn(List.of(row));

        List<org.dromara.djs.store.returns.domain.vo.StoreReturnOpsItemVo> items =
            service.listOperationItems(new StoreReturnQuery());

        assertThat(items).hasSize(1);
        org.dromara.djs.store.returns.domain.vo.StoreReturnOpsItemVo vo = items.get(0);
        assertThat(vo.getMaterialNum()).isEqualByComparingTo("30");
        assertThat(vo.getInReturnList()).isTrue();
        assertThat(vo.getCanInbound()).isTrue();
        assertThat(vo.getCanConvert()).isTrue();
        assertThat(vo.getReturnQuantity()).isEqualByComparingTo("2");
        assertThat(vo.getReturnStatus()).isEqualTo("pending");
    }

    // ── V6 row221 / D-0069：候选 = 退回清单 ∪ 当日到店的生产产品 ─────────────────

    /** 当日到店的生产产品（product_attr=1），不在退回清单里。 */
    private ProductInfo arrivedProduct(Long id, String code, String name, String belongType, String unit) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductId(code);
        p.setProductName(name);
        p.setProductUnit(unit);
        p.setBelongType(belongType);
        p.setProductAttr(1);
        return p;
    }

    /** 把「当日到店」这条链桩起来：到店 id 集 + 按 id 查得到产品 + 到店量。 */
    private void stubArrivedToday(ProductInfo arrived, String arrivedQty) {
        when(productProductionMapper.selectDeliveredProductIdsToStore(eq(STORE_ID), any()))
            .thenReturn(List.of(arrived.getId()));
        when(productInfoMapper.selectById(arrived.getId())).thenReturn(arrived);
        when(productProductionService.sumDeliveredWeightToStore(eq(STORE_ID), eq(arrived.getId()), any()))
            .thenReturn(new BigDecimal(arrivedQty));
        when(productProductionMapper.sumDeliveredQuantityToStore(eq(STORE_ID), eq(arrived.getId()), any()))
            .thenReturn(new BigDecimal(arrivedQty));
    }

    @Test
    @DisplayName("row221：猪肉 tab 候选 = 清单产品 + 当日到店的生产产品，两半各带各的标识")
    void testPorkCandidates_unionOfListAndArrivedProduction() {
        ProductInfo listed = listedProduct(PRODUCT_ID, "Y00200", "2斤装猪肉", "pork");
        ProductInfo arrived = arrivedProduct(7002L, "Y00109", "扇子骨", "pork", "kg");
        stubReturnProductList(listed);
        // stubReturnProductList 的 selectList 桩会把到店那条也一起返回，这里覆盖成按 wrapper 分流
        when(productInfoMapper.selectList(any())).thenAnswer(inv -> {
            Object w = inv.getArgument(0);
            String seg = String.valueOf(
                ((com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) w).getSqlSegment());
            return seg.contains("product_attr") ? List.of(arrived) : List.of(listed);
        });
        stubArrivedToday(arrived, "12.000");

        var rows = service.listPorkCandidates(STORE_ID);

        assertThat(rows).extracting(v -> v.getProductName()).containsExactly("2斤装猪肉", "扇子骨");
        // 清单产品：不封顶（arrivedQuantity 恒 null）+ inReturnList=true
        assertThat(rows.get(0).getInReturnList()).isTrue();
        assertThat(rows.get(0).getArrivedQuantity()).isNull();
        // 当日到店生产产品：带到店量封顶 + inReturnList=false
        assertThat(rows.get(1).getInReturnList()).isFalse();
        assertThat(rows.get(1).getArrivedQuantity()).isEqualByComparingTo("12.000");
    }

    @Test
    @DisplayName("row221：同一产品既配在清单里又当日到店 → 按清单那一份，不封顶（不被到店量拖回去）")
    void testPorkCandidates_listWinsWhenAlsoArrivedToday() {
        ProductInfo both = listedProduct(PRODUCT_ID, "Y00109", "扇子骨", "pork");
        both.setProductAttr(1);
        stubReturnProductList(both);
        stubArrivedToday(both, "3.000");

        var rows = service.listPorkCandidates(STORE_ID);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getInReturnList()).isTrue();
        assertThat(rows.get(0).getArrivedQuantity()).as("清单产品不封顶").isNull();
    }

    @Test
    @DisplayName("row221/D-0069：当日到店的生产产品可以退，但退回量不得超过当日到店量")
    void testBatchCreate_arrivedProductionCappedByArrivedQuantity() {
        ProductInfo arrived = arrivedProduct(PRODUCT_ID, "Y00109", "扇子骨", "pork", "kg");
        // 清单为空 —— 这条产品完全靠「当日到店」进候选与过闸
        when(dictService.getAllDictByDictType(DICT_RETURN_PRODUCT_LIST)).thenReturn(Map.of());
        when(productInfoMapper.selectList(any())).thenReturn(List.of(arrived));
        when(baseMapper.selectList(any())).thenReturn(List.of());
        stubArrivedToday(arrived, "10.000");

        assertThat(service.batchCreate(batchOf("10"))).as("不超到店量 → 放行").isEqualTo(1);

        assertThatThrownBy(() -> service.batchCreate(batchOf("10.001")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能超过当日到店量减今日已退");
    }

    @Test
    @DisplayName("row221：既不在清单里、当日也没到店的产品仍然一律拒绝（防凭空造仓库库存）")
    void testBatchCreate_rejectsProductNeitherListedNorArrived() {
        ProductInfo stranger = arrivedProduct(PRODUCT_ID, "Y00001", "上海青", "vegetable", "kg");
        when(dictService.getAllDictByDictType(DICT_RETURN_PRODUCT_LIST)).thenReturn(Map.of());
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(stranger);
        when(productInfoMapper.selectList(any())).thenReturn(List.of());
        when(productProductionMapper.selectDeliveredProductIdsToStore(eq(STORE_ID), any())).thenReturn(List.of());
        when(baseMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.batchCreate(batchOf("1")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("当日也没有到店记录");
    }

    // ── V6 row222：退回门店筛选项取自列表实际取值 ────────────────────────────

    private StoreReturn ownerRow(String type, Long storeId, String unit) {
        StoreReturn r = new StoreReturn();
        r.setReturnType(type);
        r.setStoreId(storeId);
        r.setReturnUnit(unit);
        return r;
    }

    @Test
    @DisplayName("row222：筛选项 = 记录里出现过的门店 ∪ 退回单位，去重；单位取字典 label，门店在前单位在后")
    void testOwnerOptions_dedupedFromExistingRecords() {
        Store s1 = new Store();
        s1.setId(9001L);
        s1.setStoreName("门店AC");
        Store s2 = new Store();
        s2.setId(9002L);
        s2.setStoreName("A门店");
        when(storeMapper.selectList(any())).thenReturn(List.of(s1, s2));
        when(dictService.getAllDictByDictType("djs_return_unit"))
            .thenReturn(Map.of("hs_qinglong_dian", "黄石青龙店"));
        when(baseMapper.selectList(any())).thenReturn(List.of(
            ownerRow("store", 9001L, null),
            ownerRow("store", 9001L, null),          // 同门店多条 → 去重
            ownerRow("store", 9002L, null),
            ownerRow("unit", null, "hs_qinglong_dian"),
            ownerRow("unit", null, "hs_qinglong_dian")));  // 同单位多条 → 去重

        var opts = service.listStoreDailyOwnerOptions();

        assertThat(opts).extracting(v -> v.getReturnType() + "|" + v.getLabel())
            .containsExactly("store|A门店", "store|门店AC", "unit|黄石青龙店");
        assertThat(opts.get(2).getReturnUnit()).isEqualTo("hs_qinglong_dian");
    }

    @Test
    @DisplayName("row222：字典里没配的退回单位退回裸 value —— 不能让这一项从下拉里消失（那些记录会再也筛不到）")
    void testOwnerOptions_keepsUnconfiguredUnitAsRawValue() {
        when(storeMapper.selectList(any())).thenReturn(List.of());
        when(dictService.getAllDictByDictType("djs_return_unit")).thenReturn(Map.of());
        when(baseMapper.selectList(any())).thenReturn(List.of(ownerRow("unit", null, "mine_only")));

        var opts = service.listStoreDailyOwnerOptions();

        assertThat(opts).hasSize(1);
        assertThat(opts.get(0).getLabel()).isEqualTo("mine_only");
    }

    @Test
    @DisplayName("row221：单条新增路（直接 received + 立刻真写库存）也必须过到店量封顶 —— 两道闸成对出现")
    void testInsertByBo_arrivedProductionAlsoCapped() {
        ProductInfo arrived = arrivedProduct(PRODUCT_ID, "Y00109", "扇子骨", "pork", "kg");
        when(dictService.getAllDictByDictType(DICT_RETURN_PRODUCT_LIST)).thenReturn(Map.of());
        when(productInfoMapper.selectList(any())).thenReturn(List.of(arrived));
        when(baseMapper.selectList(any())).thenReturn(List.of());
        stubArrivedToday(arrived, "2.000");

        StoreReturnBo over = bo("store_to_warehouse", STORE_ID);
        over.setReturnQuantity(new BigDecimal("99999"));

        assertThatThrownBy(() -> service.insertByBo(over))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能超过当日到店量减今日已退");
        // 闸必须拦在写库之前：既不 INSERT 也不联动入库
        verify(baseMapper, never()).insert(any(StoreReturn.class));
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("row221：猪肉的「材料外售」产品不该把它的原材料放进允许集 —— 折叠只发生在果蔬 tab")
    void testAllowedIds_doesNotFoldPorkMaterialSold() {
        // 通排：pork + attr=1 + is_material_sold=1 + 配了原材料（staging 实测就是这么配的）
        ProductInfo porkSold = arrivedProduct(PRODUCT_ID, "Y0322", "通排", "pork", "kg");
        porkSold.setIsMaterialSold(1);
        porkSold.setProductMaterial(7777L);
        ProductInfo material = arrivedProduct(7777L, "Y00107", "通排(原材料)", "pork", "kg");
        material.setProductAttr(2);

        when(dictService.getAllDictByDictType(DICT_RETURN_PRODUCT_LIST)).thenReturn(Map.of());
        when(productInfoMapper.selectList(any())).thenReturn(List.of(porkSold));
        when(productInfoMapper.selectById(7777L)).thenReturn(material);
        when(baseMapper.selectList(any())).thenReturn(List.of());
        stubArrivedToday(porkSold, "2.000");

        // 那个原材料从不出现在任何候选里，提交它必须被成员资格闸拒（而不是只靠封顶兜住）
        assertThatThrownBy(() -> service.batchCreate(batchOf(7777L, "0.001")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("既不在「退回产品清单」里");
    }
}
