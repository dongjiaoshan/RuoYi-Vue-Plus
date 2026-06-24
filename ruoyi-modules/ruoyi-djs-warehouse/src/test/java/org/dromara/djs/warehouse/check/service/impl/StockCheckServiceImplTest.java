package org.dromara.djs.warehouse.check.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.check.domain.StockCheckRecord;
import org.dromara.djs.warehouse.check.domain.bo.StockCheckCreateBo;
import org.dromara.djs.warehouse.check.domain.bo.StockCheckLineBo;
import org.dromara.djs.warehouse.check.mapper.StockCheckRecordMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
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
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StockCheckServiceImpl} 单测（WMS-STOCK-001）。
 *
 * <p>覆盖 4 个核心场景：</p>
 * <ol>
 *   <li>createCheck 锁库位 happy：库位存在 + 无进行中盘点 → INSERT header(status=in_progress)</li>
 *   <li>createCheck 互斥：库位已有进行中盘点 → 抛 ServiceException + 不 INSERT</li>
 *   <li>submitLine line：自动算 sysStock + diffStock + checkResultType；INSERT line</li>
 *   <li>completeCheck：diff&gt;0 写 check_in/IN，diff&lt;0 写 check_out/OT；回写 location_stock + header completed</li>
 *   <li>assertLocationUnlocked：被锁 → 抛 ServiceException（业务锁后端双保险）</li>
 * </ol>
 *
 * @author djs
 * @since WMS-STOCK-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StockCheckServiceImpl 单元测试")
class StockCheckServiceImplTest {

    @Mock private StockCheckRecordMapper baseMapper;
    @Mock private LocationStockMapper locationStockMapper;
    @Mock private StockFlowMapper stockFlowMapper;
    @Mock private LocationInfoMapper locationInfoMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private ILossFlowService lossFlowService;

    private TestableStockCheckServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    private static final Long USER_ID = 9001L;
    private static final Long LOCATION_ID = 7001L;
    private static final Long PRODUCT_ID = 8001L;
    private static final String CHECK_ID = "C2026061300001";

    /**
     * MyBatis-Plus 单测 entity cache 预热（skill coder-mp-entity-cache-test）：service 内
     * LambdaQueryWrapper 在 mock 路径下也可能触发 TableInfoHelper.getTableInfo() 解析 lambda 列名。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, StockCheckRecord.class);
        TableInfoHelper.initTableInfo(assistant, LocationStock.class);
        TableInfoHelper.initTableInfo(assistant, LocationInfo.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
    }

    /**
     * 子类化 stub generateCheckId / currentStock 固定值（避开真实 mapper / Redisson 锁）。
     */
    static class TestableStockCheckServiceImpl extends StockCheckServiceImpl {
        BigDecimal stubSysStock = BigDecimal.ZERO;

        TestableStockCheckServiceImpl(StockCheckRecordMapper b, LocationStockMapper ls, StockFlowMapper sf,
                                      LocationInfoMapper li, ProductInfoMapper pm, IBizCodeGenerator g,
                                      ILossFlowService lf) {
            super(b, ls, sf, li, pm, g, lf);
        }

        @Override
        protected String generateCheckId() {
            return CHECK_ID;
        }

        @Override
        protected BigDecimal currentStock(Long locationId, Long productId) {
            return stubSysStock;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableStockCheckServiceImpl(
            baseMapper, locationStockMapper, stockFlowMapper, locationInfoMapper, productInfoMapper, bizCodeGenerator, lossFlowService);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);
        when(bizCodeGenerator.generate(any(), any())).thenReturn("FAKE_FLOW_NO");
        when(baseMapper.insert(any(StockCheckRecord.class))).thenAnswer(inv -> {
            StockCheckRecord r = inv.getArgument(0);
            r.setId(60000L + (long) (Math.random() * 1000));
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private StockCheckCreateBo createBo() {
        StockCheckCreateBo bo = new StockCheckCreateBo();
        bo.setLocationId(LOCATION_ID);
        bo.setCheckDate(new Date());
        bo.setRemark("ut");
        return bo;
    }

    @Test
    @DisplayName("createCheck happy：库位存在 + 无进行中 → INSERT header(status=in_progress / isHeader=1 / 锁库位)")
    void testCreateCheck_Happy() {
        LocationInfo loc = new LocationInfo();
        loc.setId(LOCATION_ID);
        loc.setLocationName("白条冻品库");
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(loc);
        when(baseMapper.countActiveByLocation(LOCATION_ID)).thenReturn(0L);

        Long id = service.createCheck(createBo());
        assertThat(id).isNotNull();

        ArgumentCaptor<StockCheckRecord> cap = ArgumentCaptor.forClass(StockCheckRecord.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        StockCheckRecord header = cap.getValue();
        assertThat(header.getCheckStatus()).isEqualTo("in_progress");
        assertThat(header.getIsHeader()).isEqualTo(1);
        assertThat(header.getCheckId()).isEqualTo(CHECK_ID);
        assertThat(header.getLocationId()).isEqualTo(LOCATION_ID);
    }

    @Test
    @DisplayName("createCheck 互斥：库位已有进行中盘点 → 抛 ServiceException + 不 INSERT")
    void testCreateCheck_LocationBusy() {
        LocationInfo loc = new LocationInfo();
        loc.setId(LOCATION_ID);
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(loc);
        when(baseMapper.countActiveByLocation(LOCATION_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.createCheck(createBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("进行中");

        verify(baseMapper, never()).insert(any(StockCheckRecord.class));
    }

    @Test
    @DisplayName("submitLine：系统量 10 / 实盘 12 → diff=+2 / resultType=异常(2) / INSERT line")
    void testSubmitLine_AutoCalc() {
        // header in_progress stub
        StockCheckRecord header = new StockCheckRecord();
        header.setId(1L);
        header.setCheckId(CHECK_ID);
        header.setLocationId(LOCATION_ID);
        header.setCheckStatus("in_progress");
        header.setIsHeader(1);
        header.setCheckDate(new Date());
        // selectOne 第 1 次返 header（找 header），第 2 次返 null（无既有 line）
        when(baseMapper.selectOne(any())).thenReturn(header, (StockCheckRecord) null);

        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductName("猪肉");
        product.setProductUnit("kg");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product);

        service.stubSysStock = new BigDecimal("10");

        StockCheckLineBo bo = new StockCheckLineBo();
        bo.setCheckId(CHECK_ID);
        bo.setProductId(PRODUCT_ID);
        bo.setCheckStock(new BigDecimal("12"));

        service.submitLine(bo);

        ArgumentCaptor<StockCheckRecord> cap = ArgumentCaptor.forClass(StockCheckRecord.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        StockCheckRecord line = cap.getValue();
        assertThat(line.getIsHeader()).isEqualTo(0);
        assertThat(line.getSysStock()).isEqualByComparingTo("10");
        assertThat(line.getCheckStock()).isEqualByComparingTo("12");
        assertThat(line.getDiffStock()).isEqualByComparingTo("2");
        assertThat(line.getCheckResultType()).isEqualTo(2); // 有差异 → 异常
        assertThat(line.getProductName()).isEqualTo("猪肉");
    }

    @Test
    @DisplayName("completeCheck：line diff=+3 盘盈 → 写 check_in/IN；回写 location_stock；header completed")
    void testCompleteCheck_Surplus() {
        StockCheckRecord header = new StockCheckRecord();
        header.setId(1L);
        header.setCheckId(CHECK_ID);
        header.setLocationId(LOCATION_ID);
        header.setCheckStatus("in_progress");
        header.setIsHeader(1);
        when(baseMapper.selectById(1L)).thenReturn(header);

        StockCheckRecord line = new StockCheckRecord();
        line.setCheckId(CHECK_ID);
        line.setLocationId(LOCATION_ID);
        line.setProductId(PRODUCT_ID);
        line.setSysStock(new BigDecimal("10"));
        line.setCheckStock(new BigDecimal("13"));
        line.setDiffStock(new BigDecimal("3"));
        line.setCheckResultType(2);
        line.setIsHeader(0);
        when(baseMapper.selectList(any())).thenReturn(List.of(line));
        when(locationStockMapper.setStockAfterCheck(eq(LOCATION_ID), eq(PRODUCT_ID), any(), any(), eq(USER_ID)))
            .thenReturn(1);

        service.completeCheck(1L);

        // 1. 写盘盈流水 check_in / IN / change_num=+3
        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        StockFlow flow = flowCap.getValue();
        assertThat(flow.getFlowType()).isEqualTo("check_in");
        assertThat(flow.getInoutType()).isEqualTo("IN");
        assertThat(flow.getChangeNum()).isEqualByComparingTo("3");
        assertThat(flow.getChangeQuantity()).isEqualByComparingTo("3");

        // 2. 回写库存
        verify(locationStockMapper, times(1))
            .setStockAfterCheck(eq(LOCATION_ID), eq(PRODUCT_ID), eq(new BigDecimal("13")), eq(2), eq(USER_ID));

        // 3. header completed（updateById 被调）
        verify(baseMapper, times(1)).updateById(any(StockCheckRecord.class));
        assertThat(header.getCheckStatus()).isEqualTo("completed");
    }

    @Test
    @DisplayName("completeCheck：line diff=-5 盘亏 → 写 check_out/OT")
    void testCompleteCheck_Deficit() {
        StockCheckRecord header = new StockCheckRecord();
        header.setId(2L);
        header.setCheckId(CHECK_ID);
        header.setLocationId(LOCATION_ID);
        header.setCheckStatus("in_progress");
        header.setIsHeader(1);
        when(baseMapper.selectById(2L)).thenReturn(header);

        StockCheckRecord line = new StockCheckRecord();
        line.setCheckId(CHECK_ID);
        line.setLocationId(LOCATION_ID);
        line.setProductId(PRODUCT_ID);
        line.setCheckStock(new BigDecimal("5"));
        line.setDiffStock(new BigDecimal("-5"));
        line.setCheckResultType(3);
        line.setIsHeader(0);
        when(baseMapper.selectList(any())).thenReturn(List.of(line));
        when(locationStockMapper.setStockAfterCheck(any(), any(), any(), any(), any())).thenReturn(1);

        service.completeCheck(2L);

        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        StockFlow flow = flowCap.getValue();
        assertThat(flow.getFlowType()).isEqualTo("check_out");
        assertThat(flow.getInoutType()).isEqualTo("OT");
        assertThat(flow.getChangeNum()).isEqualByComparingTo("-5");
        assertThat(flow.getChangeQuantity()).isEqualByComparingTo("5");
        // FIX-WMS-FLOWDICT-001：盘亏出库去向回填盘点计损
        assertThat(flow.getStockOutDest()).isEqualTo("check_loss");
    }

    @Test
    @DisplayName("completeCheck：line diff=-5 盘亏且 result=异常(2) → 写 check_abnormal_out/OT + dest=check_loss")
    void testCompleteCheck_DeficitAbnormal() {
        StockCheckRecord header = new StockCheckRecord();
        header.setId(2L);
        header.setCheckId(CHECK_ID);
        header.setLocationId(LOCATION_ID);
        header.setCheckStatus("in_progress");
        header.setIsHeader(1);
        when(baseMapper.selectById(2L)).thenReturn(header);

        StockCheckRecord line = new StockCheckRecord();
        line.setCheckId(CHECK_ID);
        line.setLocationId(LOCATION_ID);
        line.setProductId(PRODUCT_ID);
        line.setCheckStock(new BigDecimal("5"));
        line.setDiffStock(new BigDecimal("-5"));
        line.setCheckResultType(2); // 异常
        line.setIsHeader(0);
        when(baseMapper.selectList(any())).thenReturn(List.of(line));
        when(locationStockMapper.setStockAfterCheck(any(), any(), any(), any(), any())).thenReturn(1);

        service.completeCheck(2L);

        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        StockFlow flow = flowCap.getValue();
        assertThat(flow.getFlowType()).isEqualTo("check_abnormal_out");
        assertThat(flow.getInoutType()).isEqualTo("OT");
        assertThat(flow.getStockOutDest()).isEqualTo("check_loss");
    }

    @Test
    @DisplayName("assertLocationUnlocked：库位被锁（有进行中盘点）→ 抛 ServiceException")
    void testAssertLocationUnlocked_Locked() {
        when(baseMapper.countActiveByLocation(LOCATION_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.assertLocationUnlocked(LOCATION_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("盘点中");
    }

    @Test
    @DisplayName("assertLocationUnlocked：库位未锁 → 不抛")
    void testAssertLocationUnlocked_Free() {
        when(baseMapper.countActiveByLocation(LOCATION_ID)).thenReturn(0L);
        service.assertLocationUnlocked(LOCATION_ID); // 无异常即通过
        assertThat(service.isLocationLocked(LOCATION_ID)).isFalse();
    }

}
