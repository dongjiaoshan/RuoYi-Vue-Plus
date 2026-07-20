package org.dromara.djs.warehouse.shipment.returnpkg.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.purchase.service.IWarehousePurchaseInService;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.ReturnProduct;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnConfirmBo;
import org.dromara.djs.warehouse.shipment.returnpkg.mapper.ReturnProductMapper;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ReturnProductServiceImpl#confirmReturn} 重复确认并发守卫单测（F1FIX TXN-3）。
 *
 * <p>守卫语义：确认落库走「WHERE 未确认谓词（{@code is_confirm IS NULL OR is_confirm <> 1}）」
 * 的守卫式 UPDATE（对齐发货清点 markDeliveryChecked 范式）；affected==0 = 已被并发确认 →
 * 幂等返回，不再执行 {@code replenishStockOnReturn} 回补库存 / 写流水。本方法收口 admin +
 * mp（AppletReturnController）两确认入口。</p>
 *
 * @author djs
 * @since F1FIX-TXN3
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReturnProductServiceImpl.confirmReturn 并发守卫")
class ReturnConfirmGuardTest {

    @Mock private ReturnProductMapper baseMapper;
    @Mock private StockFlowMapper stockFlowMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private StoreMapper storeMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private LocationInfoMapper locationInfoMapper;
    @Mock private LocationStockMapper locationStockMapper;
    @Mock private IWarehousePurchaseInService purchaseInService;

    private ReturnProductServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    private static final Long RETURN_ID = 71001L;
    private static final Long USER_ID = 9001L;

    /**
     * MyBatis-Plus 单测 entity cache 预热（skill coder-mp-entity-cache-test）：
     * confirmReturn 的守卫式 UPDATE 用 LambdaUpdateWrapper&lt;ReturnProduct&gt;，
     * replenishStockOnReturn 用 LambdaQueryWrapper&lt;ProductInfo&gt;。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, ReturnProduct.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
    }

    @BeforeEach
    void setup() {
        service = new ReturnProductServiceImpl(baseMapper, stockFlowMapper, bizCodeGenerator,
            storeMapper, productInfoMapper, locationInfoMapper, locationStockMapper, purchaseInService);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private ReturnProduct pendingEntity(String direction) {
        ReturnProduct entity = new ReturnProduct();
        entity.setId(RETURN_ID);
        entity.setIsConfirm(0);
        entity.setReturnStatus("pending");
        entity.setReturnNo("RTN2026070500001");
        entity.setReturnDirection(direction);
        return entity;
    }

    private ReturnConfirmBo confirmBo() {
        ReturnConfirmBo bo = new ReturnConfirmBo();
        bo.setConfirmWeight(new BigDecimal("3.5"));
        return bo;
    }

    @Test
    @DisplayName("并发二次确认（affected=0）→ 幂等返回，不回补库存不写流水")
    void confirmGuardMiss_idempotentSkipReplenish() {
        // 双击 race：读快照仍是未确认，但落库时守卫谓词已不命中
        when(baseMapper.selectById(RETURN_ID)).thenReturn(pendingEntity("store_to_warehouse"));
        when(baseMapper.update(any(ReturnProduct.class), any())).thenReturn(0);

        assertThatCode(() -> service.confirmReturn(RETURN_ID, confirmBo()))
            .doesNotThrowAnyException();

        verifyNoInteractions(purchaseInService, stockFlowMapper, locationStockMapper, productInfoMapper);
    }

    @Test
    @DisplayName("守卫命中（affected=1）→ 正常确认，UPDATE 带 is_confirm 未确认谓词")
    void confirmGuardHit_updateHasPredicate() {
        // 非 store_to_warehouse 方向走占位分支（不联动库存），聚焦断言守卫谓词本身
        when(baseMapper.selectById(RETURN_ID)).thenReturn(pendingEntity("warehouse_to_supplier"));
        when(baseMapper.update(any(ReturnProduct.class), any())).thenReturn(1);

        assertThatCode(() -> service.confirmReturn(RETURN_ID, confirmBo()))
            .doesNotThrowAnyException();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Wrapper<ReturnProduct>> wrapperCaptor =
            ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(baseMapper).update(any(ReturnProduct.class), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("is_confirm IS NULL").contains("is_confirm <>");
        verify(purchaseInService, never()).inbound(any(), any(), any(), anyString(), any());
    }
}
