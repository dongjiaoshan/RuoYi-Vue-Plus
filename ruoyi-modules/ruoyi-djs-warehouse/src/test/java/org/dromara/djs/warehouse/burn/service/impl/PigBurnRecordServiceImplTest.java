package org.dromara.djs.warehouse.burn.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.burn.domain.PigBurnRecord;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnRecordBo;
import org.dromara.djs.warehouse.burn.mapper.PigBurnRecordMapper;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PigBurnRecordServiceImpl} 单测（D12X-MP-BURN-IA-001 燎毛入库重做）。
 *
 * <p>覆盖入库语义的核心场景：</p>
 * <ol>
 *   <li>happy path：bar pending_singe + 2 类型 → 燎毛记录 INSERT + 2 行 product_inhouse + 2 行 IN 流水
 *       + bar 推进 in_stock（乐观锁回填 in_weight）</li>
 *   <li>白条状态不符：bar status=in_stock → 抛 "白条状态不符" + 任何写入不发生</li>
 *   <li>白条不存在：selectById 返 null → 抛 "白条不存在"</li>
 *   <li>无效产品类型：productId 不在标准白条类型集 → 抛 "无效的白条产品类型"</li>
 *   <li>到场重量小于入库合计 → 抛 "入库重量合计不能大于到场重量"</li>
 * </ol>
 *
 * <p>子类化避开 MapStruct convert（无 Spring 上下文）+ stub generateBurnId 固定值。</p>
 *
 * @author djs
 * @since D12X-MP-BURN-IA-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PigBurnRecordServiceImpl 单元测试")
class PigBurnRecordServiceImplTest {

    @Mock
    private PigBurnRecordMapper burnMapper;
    @Mock
    private StockFlowMapper flowMapper;
    @Mock
    private BarInfoMapper barInfoMapper;
    @Mock
    private ProductInhouseMapper productInhouseMapper;
    @Mock
    private org.dromara.djs.warehouse.stock.mapper.LocationStockMapper locationStockMapper;
    @Mock
    private LocationInfoMapper locationInfoMapper;
    @Mock
    private ProductInfoMapper productInfoMapper;
    @Mock
    private IBizCodeGenerator bizCodeGenerator;
    @Mock
    private IStockCheckService stockCheckService;
    @Mock
    private org.dromara.djs.warehouse.trace.service.ITraceService traceService;
    @Mock
    private org.dromara.djs.common.image.service.ImageUrlResolver imageUrlResolver;
    @Mock
    private org.dromara.djs.warehouse.loss.service.ILossFlowService lossFlowService;

    private TestablePigBurnRecordServiceImpl service;

    private static final Long BAR_ID = 5001L;
    private static final Long LOCATION_ID = 90001L;
    private static final Long OPERATOR_ID = 9001L;
    private static final Long TYPE_WHOLE = 100000000000000001L;
    private static final Long TYPE_HEAD = 2059526196453937154L;

    static class TestablePigBurnRecordServiceImpl extends PigBurnRecordServiceImpl {
        TestablePigBurnRecordServiceImpl(PigBurnRecordMapper b, StockFlowMapper f, BarInfoMapper bi,
                                         ProductInhouseMapper ih,
                                         org.dromara.djs.warehouse.stock.mapper.LocationStockMapper ls,
                                         LocationInfoMapper l, ProductInfoMapper pm,
                                         IBizCodeGenerator g, IStockCheckService cs,
                                         org.dromara.djs.warehouse.trace.service.ITraceService ts,
                                         org.dromara.djs.common.image.service.ImageUrlResolver ir,
                                         org.dromara.djs.warehouse.loss.service.ILossFlowService lfs) {
            super(b, f, bi, ih, ls, l, pm, g, cs, ts, ir, lfs);
        }

        @Override
        protected PigBurnRecord toEntity(PigBurnRecordBo bo) {
            if (bo == null) {
                return null;
            }
            PigBurnRecord e = new PigBurnRecord();
            e.setBurnTime(bo.getBurnTime());
            e.setArriveWeight(bo.getArriveWeight());
            e.setLocationId(bo.getLocationId());
            e.setRemark(bo.getRemark());
            return e;
        }

        @Override
        protected String generateBurnId() {
            return "BURN2606040001";
        }
    }

    @BeforeEach
    void setup() {
        service = new TestablePigBurnRecordServiceImpl(
            burnMapper, flowMapper, barInfoMapper, productInhouseMapper, locationStockMapper,
            locationInfoMapper, productInfoMapper, bizCodeGenerator, stockCheckService, traceService, imageUrlResolver,
            lossFlowService);
    }

    private BarInfo sampleBar(String status) {
        BarInfo bar = new BarInfo();
        bar.setId(BAR_ID);
        bar.setBarId("BAR2606030001");
        bar.setEarNo("010126050101");
        bar.setStatus(status);
        return bar;
    }

    private List<ProductInfo> sampleTypes() {
        List<ProductInfo> list = new ArrayList<>();
        ProductInfo whole = new ProductInfo();
        whole.setId(TYPE_WHOLE);
        whole.setProductId("PROD-WHITE-BAR-01");
        whole.setProductName("白条·整只");
        whole.setProductType(1);
        whole.setProductUnit("kg");
        whole.setBelongType("white_bar");
        list.add(whole);
        ProductInfo head = new ProductInfo();
        head.setId(TYPE_HEAD);
        head.setProductId("PROD-WHITE-BAR-02");
        head.setProductName("白条·猪头");
        head.setProductType(1);
        head.setProductUnit("kg");
        head.setBelongType("white_bar");
        list.add(head);
        return list;
    }

    private PigBurnRecordBo sampleBo() {
        PigBurnRecordBo bo = new PigBurnRecordBo();
        bo.setBarInfoId(BAR_ID);
        bo.setBurnTime(new Date());
        bo.setArriveWeight(new BigDecimal("110.500"));
        bo.setLocationId(LOCATION_ID);
        bo.setOperatorId(OPERATOR_ID);
        PigBurnRecordBo.ProductTypeItem i1 = new PigBurnRecordBo.ProductTypeItem();
        i1.setProductId(TYPE_WHOLE);
        i1.setWeight(new BigDecimal("80.300"));
        PigBurnRecordBo.ProductTypeItem i2 = new PigBurnRecordBo.ProductTypeItem();
        i2.setProductId(TYPE_HEAD);
        i2.setWeight(new BigDecimal("5.200"));
        bo.setProductTypeItems(List.of(i1, i2));
        return bo;
    }

    @SuppressWarnings("unchecked")
    private void stubTypes() {
        when(productInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sampleTypes());
    }

    @Test
    @DisplayName("submitBurnRecord: happy → 2 product_inhouse + 2 IN 流水 + bar 推进 in_stock(乐观锁 in_weight 合计)")
    void testSubmit_Happy() {
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(sampleBar("pending_singe"));
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(new LocationInfo());
        stubTypes();
        when(burnMapper.insert(any(PigBurnRecord.class))).thenAnswer(inv -> {
            PigBurnRecord e = inv.getArgument(0);
            e.setId(60001L);
            return 1;
        });
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap())).thenReturn("F2606040IN0001");
        when(productInhouseMapper.insert(any(ProductInhouse.class))).thenReturn(1);
        when(flowMapper.insert(any(StockFlow.class))).thenReturn(1);
        when(barInfoMapper.updateStatusToSinging(eq(BAR_ID), any(Date.class), eq(OPERATOR_ID)))
            .thenReturn(1);

        Long id = service.submitBurnRecord(sampleBo());

        assertThat(id).isEqualTo(60001L);

        // 燎毛记录：burnWeight = 入库合计 85.5；earNo 从 bar 反查；operatorId = 入库人
        ArgumentCaptor<PigBurnRecord> burnCaptor = ArgumentCaptor.forClass(PigBurnRecord.class);
        verify(burnMapper, times(1)).insert(burnCaptor.capture());
        PigBurnRecord saved = burnCaptor.getValue();
        assertThat(saved.getBurnId()).isEqualTo("BURN2606040001");
        assertThat(saved.getBurnStatus()).isEqualTo("done");
        assertThat(saved.getEarNo()).isEqualTo("010126050101");
        assertThat(saved.getOperatorId()).isEqualTo(OPERATOR_ID);
        assertThat(saved.getBurnWeight()).isEqualByComparingTo("85.500");
        assertThat(saved.getLossWeight()).isEqualByComparingTo("25.000");

        // 2 行 product_inhouse + 2 行 IN 流水
        verify(productInhouseMapper, times(2)).insert(any(ProductInhouse.class));
        ArgumentCaptor<StockFlow> flowCaptor = ArgumentCaptor.forClass(StockFlow.class);
        verify(flowMapper, times(2)).insert(flowCaptor.capture());
        for (StockFlow flow : flowCaptor.getAllValues()) {
            assertThat(flow.getInoutType()).isEqualTo("IN");
            assertThat(flow.getFlowType()).isEqualTo("slaughter_burn");
            assertThat(flow.getEarNo()).isEqualTo("010126050101");
            assertThat(flow.getOperatorId()).isEqualTo(OPERATOR_ID);
        }

        // bar 推进 singing（燎毛中间态；FIX-WMS-MP-BURN-001 不再直推 in_stock）
        verify(barInfoMapper, times(1))
            .updateStatusToSinging(eq(BAR_ID), any(Date.class), eq(OPERATOR_ID));
        verify(barInfoMapper, never())
            .updateStatusToInStock(eq(BAR_ID), any(BigDecimal.class), any(Date.class), eq(OPERATOR_ID));
    }

    @Test
    @DisplayName("submitBurnRecord: bar 状态不符(in_stock) → 抛 + 不写入")
    void testSubmit_BarStatusInvalid() {
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(sampleBar("in_stock"));

        assertThatThrownBy(() -> service.submitBurnRecord(sampleBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("白条状态不符");

        verify(burnMapper, never()).insert(any(PigBurnRecord.class));
        verify(productInhouseMapper, never()).insert(any(ProductInhouse.class));
        verify(flowMapper, never()).insert(any(StockFlow.class));
    }

    @Test
    @DisplayName("submitBurnRecord: bar 不存在 → 抛 白条不存在")
    void testSubmit_BarNotFound() {
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.submitBurnRecord(sampleBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("白条不存在");

        verify(burnMapper, never()).insert(any(PigBurnRecord.class));
    }

    @Test
    @DisplayName("submitBurnRecord: 无效产品类型 → 抛 + 燎毛记录不写")
    void testSubmit_InvalidProductType() {
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(sampleBar("pending_singe"));
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(new LocationInfo());
        stubTypes();

        PigBurnRecordBo bo = sampleBo();
        PigBurnRecordBo.ProductTypeItem bad = new PigBurnRecordBo.ProductTypeItem();
        bad.setProductId(999999L);
        bad.setWeight(new BigDecimal("1.0"));
        bo.setProductTypeItems(List.of(bad));

        assertThatThrownBy(() -> service.submitBurnRecord(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("无效的白条产品类型");

        verify(burnMapper, never()).insert(any(PigBurnRecord.class));
    }

    @Test
    @DisplayName("submitBurnRecord: 到场重量 < 入库合计 → 抛 不能大于到场重量")
    void testSubmit_ArriveLessThanInbound() {
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(sampleBar("pending_singe"));
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(new LocationInfo());
        stubTypes();

        PigBurnRecordBo bo = sampleBo();
        bo.setArriveWeight(new BigDecimal("50.000")); // < 85.5 合计

        assertThatThrownBy(() -> service.submitBurnRecord(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能大于到场重量");

        verify(burnMapper, never()).insert(any(PigBurnRecord.class));
    }

    // ============================ finishBurn（处理完成终态 + 录入约束）============================

    private BarInfo sampleBarWithMarketWeight(String status, String marketWeight) {
        BarInfo bar = sampleBar(status);
        bar.setMarketingWeight(new BigDecimal(marketWeight));
        // finishBurn 累计校验现以「头皮肉重量」= 称重落的到场重 arrive_weight 为上限（非出栏重）。
        // happy / 互斥用例给足量上限避免误拦；超量用例单独覆写更小的 arrive_weight。
        bar.setArriveWeight(new BigDecimal(marketWeight));
        return bar;
    }

    private ProductInhouse inhouse(Long productId, String weight) {
        ProductInhouse ih = new ProductInhouse();
        ih.setProductId(productId);
        ih.setProductWeight(new BigDecimal(weight));
        return ih;
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("finishBurn: happy(整只 80.3 ≤ 出栏重 110.5) → bar singing→in_stock 回填 in_weight 合计")
    void testFinish_Happy() {
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "110.500"));
        when(productInhouseMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(inhouse(TYPE_WHOLE, "80.300")));
        when(productInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sampleTypes());
        when(barInfoMapper.updateStatusToInStock(eq(BAR_ID), any(BigDecimal.class), any(Date.class), eq(OPERATOR_ID)))
            .thenReturn(1);

        service.finishBurn(BAR_ID, OPERATOR_ID);

        verify(barInfoMapper, times(1))
            .updateStatusToInStock(eq(BAR_ID), eq(new BigDecimal("80.300")), any(Date.class), eq(OPERATOR_ID));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("finishBurn: 累计总重 > 头皮肉重量 → 抛 不能超过头皮肉重量 + 不推进")
    void testFinish_TotalExceedsMarketing() {
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "100.000"));
        when(productInhouseMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(inhouse(TYPE_WHOLE, "120.000")));
        when(productInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sampleTypes());

        assertThatThrownBy(() -> service.finishBurn(BAR_ID, OPERATOR_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能超过头皮肉重量");

        verify(barInfoMapper, never()).updateStatusToInStock(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("finishBurn: 整只 + 半只同时入库 → 抛 整只与半只不能同时入库")
    void testFinish_WholeHalfMutex() {
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "200.000"));
        // sampleTypes 缺半只类型 → 补 PROD-WHITE-BAR-04
        List<ProductInfo> types = sampleTypes();
        ProductInfo half = new ProductInfo();
        Long typeHalf = 100000000000000004L;
        half.setId(typeHalf);
        half.setProductId("PROD-WHITE-BAR-04");
        half.setProductName("白条·半只");
        half.setBelongType("white_bar");
        types.add(half);
        when(productInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(types);
        when(productInhouseMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(inhouse(TYPE_WHOLE, "50.000"), inhouse(typeHalf, "25.000")));

        assertThatThrownBy(() -> service.finishBurn(BAR_ID, OPERATOR_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("整只与半只不能同时入库");

        verify(barInfoMapper, never()).updateStatusToInStock(any(), any(), any(), any());
    }

    @Test
    @DisplayName("finishBurn: bar 不在 singing 态 → 抛 白条状态不符")
    void testFinish_BarNotSinging() {
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(sampleBarWithMarketWeight("pending_singe", "110.500"));

        assertThatThrownBy(() -> service.finishBurn(BAR_ID, OPERATOR_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("白条状态不符");

        verify(barInfoMapper, never()).updateStatusToInStock(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("finishBurn: 无任何产品入库 → 抛 尚未录入任何产品入库")
    void testFinish_NoInhouse() {
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "110.500"));
        when(productInhouseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.finishBurn(BAR_ID, OPERATOR_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("尚未录入任何产品入库");

        verify(barInfoMapper, never()).updateStatusToInStock(any(), any(), any(), any());
    }

}
