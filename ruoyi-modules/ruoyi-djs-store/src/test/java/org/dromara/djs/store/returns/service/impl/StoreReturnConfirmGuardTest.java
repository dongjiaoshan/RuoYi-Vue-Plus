package org.dromara.djs.store.returns.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.store.returns.domain.StoreReturn;
import org.dromara.djs.store.returns.domain.bo.StoreReturnConfirmBo;
import org.dromara.djs.store.returns.mapper.StoreReturnMapper;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreReturnServiceImpl#confirm} 重复确认并发守卫单测（F1FIX TXN-3）。
 *
 * <p>守卫语义：确认落库走「WHERE 状态谓词（{@code return_status <> 'received'}）」的守卫式
 * UPDATE（对齐发货清点 markDeliveryChecked 范式）；affected==0 = 已被并发确认 → 幂等返回，
 * 不再联动 {@code inboundReturnBasket}，杜绝双击 / 两人同点导致的双倍回补库存 + 双份流水。</p>
 *
 * @author djs
 * @since F1FIX-TXN3
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreReturnServiceImpl.confirm 并发守卫")
class StoreReturnConfirmGuardTest {

    @Mock private StoreReturnMapper baseMapper;
    @Mock private org.dromara.djs.common.store.mapper.StoreMapper storeMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private LocationInfoMapper locationInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private IWarehousePurchaseInService purchaseInService;
    @Mock private DemandManageMapper demandManageMapper;
    @Mock private org.dromara.common.core.service.DictService dictService;
    @Mock private org.dromara.djs.warehouse.pack.service.IProductProductionService productProductionService;
    @Mock private org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper productProductionMapper;

    private StoreReturnServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    private static final Long RETURN_ID = 61001L;
    private static final Long PRODUCT_ID = 8001L;
    private static final Long LOCATION_ID = 3001L;
    private static final Long USER_ID = 9001L;

    /**
     * MyBatis-Plus 单测 entity cache 预热（skill coder-mp-entity-cache-test）：
     * confirm 的守卫式 UPDATE 用 LambdaUpdateWrapper&lt;StoreReturn&gt; 解析 lambda 列名。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, StoreReturn.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
    }

    @BeforeEach
    void setup() {
        service = new StoreReturnServiceImpl(baseMapper, storeMapper, productInfoMapper,
            locationInfoMapper, bizCodeGenerator, purchaseInService, demandManageMapper, dictService,
            productProductionService, productProductionMapper);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);

        StoreReturn existing = new StoreReturn();
        existing.setId(RETURN_ID);
        existing.setProductId(PRODUCT_ID);
        existing.setReturnStatus("pending");
        existing.setReturnNo("RET2026070500001");
        when(baseMapper.selectById(RETURN_ID)).thenReturn(existing);

        // 猪肉产品：入库目标 = 成品本身（不走果蔬原材料换算分支）
        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setBelongType("pork");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product);

        when(purchaseInService.inboundReturnBasket(any(), any(), any(), anyString(), any())).thenReturn(77777L);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private StoreReturnConfirmBo confirmBo() {
        StoreReturnConfirmBo bo = new StoreReturnConfirmBo();
        bo.setId(RETURN_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setReceivedQty(new BigDecimal("2"));
        return bo;
    }

    @Test
    @DisplayName("守卫命中（affected=1）→ 联动入库一次，UPDATE 带 return_status 状态谓词")
    void confirmGuardHit_inboundOnce() {
        when(baseMapper.update(any(StoreReturn.class), any())).thenReturn(1);

        int rows = service.confirm(confirmBo());

        assertThat(rows).isEqualTo(1);
        verify(purchaseInService, times(1)).inboundReturnBasket(any(), any(), any(), anyString(), any());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Wrapper<StoreReturn>> wrapperCaptor =
            ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(baseMapper).update(any(StoreReturn.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("return_status <>");
    }

    @Test
    @DisplayName("并发二次确认（affected=0）→ 幂等返回 0，不再联动入库")
    void confirmGuardMiss_idempotentSkipInbound() {
        when(baseMapper.update(any(StoreReturn.class), any())).thenReturn(0);

        int rows = service.confirm(confirmBo());

        assertThat(rows).isZero();
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }
}
