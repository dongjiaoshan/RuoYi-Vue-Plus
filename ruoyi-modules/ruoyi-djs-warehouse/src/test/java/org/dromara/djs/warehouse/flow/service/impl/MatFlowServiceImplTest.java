package org.dromara.djs.warehouse.flow.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.domain.bo.MatLossBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatPickBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatReturnBo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.junit.jupiter.api.AfterEach;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MatFlowServiceImpl} 单测（WMS-MAT-001）。
 *
 * <p>覆盖跨表事务一致性的 6 个核心场景（pick / return / loss × happy / 异常）：</p>
 * <ol>
 *   <li>pick happy：location_stock 充足 → stock_flow INSERT(pick_out / OT / change_num=-q) + 扣减成功</li>
 *   <li>pick 库存不足：deductByProductLocation 返 0 → 抛"库存不足" → 流水回滚（@Transactional 由集成测试覆盖；
 *       单测验证流水 INSERT 已被调过但 service 抛出）</li>
 *   <li>return happy：今日已领 ≥ 已退 + 已损 + 当次 → stock_flow INSERT(return_in / IN / +q) + 加库存</li>
 *   <li>return 超额：已领 20 - 已退 10 - 已损 0 = 剩 10；申请 15 → 抛"今日额度不足" + 无 INSERT</li>
 *   <li>loss happy：同 return 校验但扣库存；INSERT stock_flow(loss / OT / -q)</li>
 *   <li>requireProduct 不存在：productInfoMapper 返 null → 抛"产品不存在"</li>
 * </ol>
 *
 * <p>Mockito {@code MockedStatic(LoginHelper)} stub 当前 userId，避开 Sa-Token 上下文。</p>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MatFlowServiceImpl 单元测试")
class MatFlowServiceImplTest {

    @Mock private StockFlowMapper stockFlowMapper;
    @Mock private LocationStockMapper locationStockMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private IStockCheckService stockCheckService;

    private MatFlowServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    private static final Long USER_ID = 9001L;
    private static final Long PRODUCT_ID = 8001L;
    private static final Long LOCATION_ID = 7001L;

    @BeforeEach
    void setup() {
        service = new MatFlowServiceImpl(stockFlowMapper, locationStockMapper, productInfoMapper, bizCodeGenerator, stockCheckService);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);

        // 默认 product stub
        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductId("PROD-PACK-BAG-01");
        product.setProductName("塑料袋");
        product.setProductUnit("个");
        product.setBelongType("package");
        when(productInfoMapper.selectOne(any())).thenReturn(product);

        // 默认 flow_no stub
        when(bizCodeGenerator.generate(any(), any())).thenReturn("FAKE_FLOW_NO");
        when(stockFlowMapper.insert(any(StockFlow.class))).thenAnswer(inv -> {
            StockFlow f = inv.getArgument(0);
            f.setId(50000L + (long) (Math.random() * 1000));
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private MatPickBo pickBo(BigDecimal qty) {
        MatPickBo bo = new MatPickBo();
        bo.setProductId(PRODUCT_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setQuantity(qty);
        bo.setStockOutDest("内部消耗");
        bo.setRemark("ut");
        return bo;
    }

    private MatReturnBo returnBo(BigDecimal qty) {
        MatReturnBo bo = new MatReturnBo();
        bo.setProductId(PRODUCT_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setQuantity(qty);
        bo.setRemark("ut");
        return bo;
    }

    private MatLossBo lossBo(BigDecimal qty) {
        MatLossBo bo = new MatLossBo();
        bo.setProductId(PRODUCT_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setQuantity(qty);
        bo.setRemark("ut");
        return bo;
    }

    @Test
    @DisplayName("pick happy：扣库存成功 → 流水 INSERT(pick_out / OT / change_num=-20 / change_quantity=20)")
    void testPick_Happy() {
        when(locationStockMapper.deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        service.pick(pickBo(new BigDecimal("20")));

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getFlowType()).isEqualTo("pick_out");
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-20");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("20");
        assertThat(f.getStockOutDest()).isEqualTo("内部消耗");
        assertThat(f.getOperatorId()).isEqualTo(USER_ID);
        verify(locationStockMapper, times(1))
            .deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID));
    }

    @Test
    @DisplayName("pick 库存不足：deductByProductLocation 返 0 → 抛 ServiceException 库存不足")
    void testPick_StockInsufficient() {
        when(locationStockMapper.deductByProductLocation(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.pick(pickBo(new BigDecimal("999"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("库存不足");

        // INSERT 已发生（Spring @Transactional 集成测试覆盖回滚行为；单测只验异常抛出 + service 中断）
        verify(stockFlowMapper, times(1)).insert(any(StockFlow.class));
    }

    @Test
    @DisplayName("return happy：今日已领 20 / 已退 5 / 已损 3 → 退 8 通过；INSERT(return_in / IN / +8)")
    void testReturn_Happy() {
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "pick_out")).thenReturn(new BigDecimal("20"));
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "return_in")).thenReturn(new BigDecimal("5"));
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "loss")).thenReturn(new BigDecimal("3"));
        when(locationStockMapper.addByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(), eq(USER_ID))).thenReturn(1);

        service.returnBack(returnBo(new BigDecimal("8")));

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getFlowType()).isEqualTo("return_in");
        assertThat(f.getInoutType()).isEqualTo("IN");
        assertThat(f.getChangeNum()).isEqualByComparingTo("8");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("return 超额：今日已领 20 / 已退 10 / 已损 0 → 剩 10，申请 15 → 抛今日额度不足 + 无 INSERT")
    void testReturn_OverQuota() {
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "pick_out")).thenReturn(new BigDecimal("20"));
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "return_in")).thenReturn(new BigDecimal("10"));
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "loss")).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.returnBack(returnBo(new BigDecimal("15"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("今日额度不足");

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never()).addByProductLocation(any(), any(), any(), any());
    }

    @Test
    @DisplayName("loss happy：额度内损 3 → INSERT(loss / OT / -3) + 扣库存（影响行 0 不抛）")
    void testLoss_Happy() {
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "pick_out")).thenReturn(new BigDecimal("20"));
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "return_in")).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "loss")).thenReturn(BigDecimal.ZERO);
        when(locationStockMapper.deductByProductLocation(any(), any(), any(), any())).thenReturn(0);

        Long id = service.loss(lossBo(new BigDecimal("3")));
        assertThat(id).isNotNull();

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getFlowType()).isEqualTo("loss");
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-3");
        // loss 即使 affected==0 也不抛（service 内部 log.warn 兜底，本测不验证 log）
    }

    @Test
    @DisplayName("requireProduct 找不到 → 抛产品不存在 + 任何 mapper 不调")
    void testPick_ProductNotFound() {
        when(productInfoMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.pick(pickBo(new BigDecimal("1"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("产品不存在");

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never()).deductByProductLocation(any(), any(), any(), any());
    }

}
