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
import org.dromara.djs.store.returns.domain.bo.StoreReturnBo;
import org.dromara.djs.store.returns.mapper.StoreReturnMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreReturnServiceImpl} 单测（STR-RETURN-001）。
 *
 * <p>覆盖核心场景：</p>
 * <ol>
 *   <li>insertByBo happy（customer_to_store）：returnNo=RET 开头 + operatorId 注入 + returnDate 缺省 now + member/trace 存值</li>
 *   <li>insertByBo 三方向各 1：customer_to_store / store_to_warehouse / warehouse_to_supplier 方向透传</li>
 *   <li>insertByBo 产品不存在 → 抛 ServiceException + 不 INSERT</li>
 *   <li>insertByBo 门店非空但不存在 → 抛 ServiceException</li>
 *   <li>updateByBo 不改 returnNo / operatorId（entity 两字段为 null）</li>
 *   <li>deleteByIds → softDelete（baseMapper.update wrapper-only 被调）</li>
 * </ol>
 *
 * @author djs
 * @since STR-RETURN-001
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
    @Mock private IBizCodeGenerator bizCodeGenerator;

    private TestableStoreReturnServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    private static final Long USER_ID = 9001L;
    private static final Long STORE_ID = 5001L;
    private static final Long PRODUCT_ID = 8001L;
    private static final Long MEMBER_ID = 7001L;
    private static final String RETURN_NO = "RET2026060200001";

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
    }

    /**
     * 子类化 stub generateReturnNo 固定值（避开真实 BizCodeGenerator / Redisson 锁）。
     */
    static class TestableStoreReturnServiceImpl extends StoreReturnServiceImpl {
        TestableStoreReturnServiceImpl(StoreReturnMapper b, StoreMapper sm,
                                       ProductInfoMapper pm, IBizCodeGenerator g) {
            super(b, sm, pm, g);
        }

        @Override
        protected String generateReturnNo() {
            return RETURN_NO;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableStoreReturnServiceImpl(baseMapper, storeMapper, productInfoMapper, bizCodeGenerator);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);
        when(baseMapper.insert(any(StoreReturn.class))).thenAnswer(inv -> {
            StoreReturn r = inv.getArgument(0);
            r.setId(60000L + (long) (Math.random() * 1000));
            return 1;
        });
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
        bo.setReturnQuantity(new BigDecimal("3.5"));
        bo.setReturnReason("客户改主意");
        bo.setTraceCode("TRC20260602ABCD");
        bo.setMemberId(MEMBER_ID);
        return bo;
    }

    @Test
    @DisplayName("insertByBo happy(customer_to_store)：returnNo=RET 开头 + operatorId 注入 + returnDate 缺省 now + member/trace 存值")
    void testInsert_Happy() {
        Long id = service.insertByBo(bo("customer_to_store", STORE_ID));
        assertThat(id).isNotNull();

        ArgumentCaptor<StoreReturn> cap = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        StoreReturn e = cap.getValue();
        assertThat(e.getReturnNo()).startsWith("RET");
        assertThat(e.getReturnNo()).isEqualTo(RETURN_NO);
        assertThat(e.getReturnDirection()).isEqualTo("customer_to_store");
        assertThat(e.getStoreId()).isEqualTo(STORE_ID);
        assertThat(e.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(e.getReturnQuantity()).isEqualByComparingTo("3.5");
        assertThat(e.getOperatorId()).isEqualTo(USER_ID);
        assertThat(e.getReturnDate()).isNotNull(); // 缺省 now
        assertThat(e.getTraceCode()).isEqualTo("TRC20260602ABCD");
        assertThat(e.getMemberId()).isEqualTo(MEMBER_ID);
    }

    @Test
    @DisplayName("insertByBo 三方向各 1：方向值透传 INSERT")
    void testInsert_ThreeDirections() {
        service.insertByBo(bo("customer_to_store", STORE_ID));
        service.insertByBo(bo("store_to_warehouse", STORE_ID));
        service.insertByBo(bo("warehouse_to_supplier", null)); // 供应商方向门店可空

        ArgumentCaptor<StoreReturn> cap = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper, times(3)).insert(cap.capture());
        List<StoreReturn> all = cap.getAllValues();
        assertThat(all).extracting(StoreReturn::getReturnDirection)
            .containsExactly("customer_to_store", "store_to_warehouse", "warehouse_to_supplier");
        // 供应商方向 storeId 可空，不校验门店存在
        assertThat(all.get(2).getStoreId()).isNull();
    }

    @Test
    @DisplayName("insertByBo 方向留空 → 默认 customer_to_store（门店主场景）")
    void testInsert_DefaultDirection() {
        service.insertByBo(bo(null, STORE_ID));
        ArgumentCaptor<StoreReturn> cap = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getReturnDirection()).isEqualTo("customer_to_store");
    }

    @Test
    @DisplayName("insertByBo 产品不存在 → 抛 ServiceException + 不 INSERT")
    void testInsert_ProductNotFound() {
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.insertByBo(bo("customer_to_store", STORE_ID)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("产品不存在");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("insertByBo 门店非空但不存在 → 抛 ServiceException + 不 INSERT")
    void testInsert_StoreNotFound() {
        when(storeMapper.selectById(STORE_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.insertByBo(bo("customer_to_store", STORE_ID)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("门店不存在");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
    }

    @Test
    @DisplayName("updateByBo：不改 returnNo / operatorId（entity 两字段为 null，只更新可编辑字段）")
    void testUpdate_NoReturnNoChange() {
        StoreReturn existing = new StoreReturn();
        existing.setId(60001L);
        existing.setReturnNo(RETURN_NO);
        existing.setOperatorId(USER_ID);
        when(baseMapper.selectById(60001L)).thenReturn(existing);
        when(baseMapper.updateById(any(StoreReturn.class))).thenReturn(1);

        StoreReturnBo upd = bo("store_to_warehouse", STORE_ID);
        upd.setId(60001L);
        upd.setReturnQuantity(new BigDecimal("9.9"));

        int n = service.updateByBo(upd);
        assertThat(n).isEqualTo(1);

        ArgumentCaptor<StoreReturn> cap = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper, times(1)).updateById(cap.capture());
        StoreReturn e = cap.getValue();
        assertThat(e.getReturnNo()).isNull();       // 不改单号
        assertThat(e.getOperatorId()).isNull();     // 不改经手人
        assertThat(e.getReturnQuantity()).isEqualByComparingTo("9.9");
        assertThat(e.getReturnDirection()).isEqualTo("store_to_warehouse");
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
    @DisplayName("deleteByIds → softDelete（DjsBaseServiceImpl wrapper-only update 被调）")
    void testDelete_SoftDelete() {
        when(baseMapper.update(any(), any())).thenReturn(1);
        int n = service.deleteByIds(List.of(60001L, 60002L));
        // softDelete 逐 id 调 baseMapper.update(null, wrapper)
        assertThat(n).isEqualTo(2);
        verify(baseMapper, times(2)).update(any(), any());
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
}
