package org.dromara.djs.warehouse.burn.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.burn.domain.PigBurnRecord;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnRecordBo;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnWeighBo;
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
import org.junit.jupiter.api.BeforeAll;
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
 *   <li>无效产品类型：productId 不在燎毛间可入库产品集 → 抛 "无效的白条产品类型"</li>
 *   <li>到场重量小于入库合计 → 抛 "入库重量合计不能大于到场重量"</li>
 *   <li>finishBurn 半只约束：白条本体（belong_type=white_bar）须集齐 2 扇；只录猪头等非白条原材料不受约束</li>
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

    @Mock private org.dromara.common.core.service.DictService dictService;
    private TestablePigBurnRecordServiceImpl service;

    private static final Long BAR_ID = 5001L;
    private static final Long LOCATION_ID = 90001L;
    private static final Long OPERATOR_ID = 9001L;
    /** 白条本体「半扇」（belong_type=white_bar → productType=half，须集齐 2 扇）。 */
    private static final Long TYPE_HALF = 100000000000000001L;
    private static final Long TYPE_RIGHT = 100000000000000002L;
    private static final Long TYPE_LEGACY = 100000000000000003L;
    /** 燎毛间非白条原材料「猪头」（belong_type=pork → productType=null，不限次）。 */
    private static final Long TYPE_HEAD = 2059526196453937154L;

    @BeforeAll
    static void initMpEntityCache() {
        // MyBatis-Plus 单测 entity cache 预热（coder-mp-entity-cache-test）：
        // 累计入库总重校验用 LambdaQueryWrapper<ProductInhouse>，无 Spring 上下文时需预热 lambda 列名解析
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
            new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, ProductInhouse.class);
    }

    static class TestablePigBurnRecordServiceImpl extends PigBurnRecordServiceImpl {
        TestablePigBurnRecordServiceImpl(PigBurnRecordMapper b, StockFlowMapper f, BarInfoMapper bi,
                                         ProductInhouseMapper ih,
                                         org.dromara.djs.warehouse.stock.mapper.LocationStockMapper ls,
                                         LocationInfoMapper l, ProductInfoMapper pm,
                                         IBizCodeGenerator g, IStockCheckService cs,
                                         org.dromara.djs.warehouse.trace.service.ITraceService ts,
                                         org.dromara.djs.common.image.service.ImageUrlResolver ir,
                                         org.dromara.djs.warehouse.loss.service.ILossFlowService lfs,
                                         org.dromara.djs.warehouse.inout.service.WeightCompletionPolicy policy) {
            super(b, f, bi, ih, ls, l, pm, g, cs, ts, ir, lfs, policy);
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
            lossFlowService, new org.dromara.djs.warehouse.inout.service.WeightCompletionPolicy(dictService));
        var threshold = new org.dromara.common.core.domain.dto.DictDataDTO();
        threshold.setDictValue("50"); threshold.setIsDefault("Y");
        when(dictService.getDictData(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of(threshold));
    }

    private BarInfo sampleBar(String status) {
        BarInfo bar = new BarInfo();
        bar.setId(BAR_ID);
        bar.setBarId("BAR2606030001");
        bar.setEarNo("010126050101");
        bar.setStatus(status);
        return bar;
    }

    /**
     * 燎毛入库可选产品（= 燎毛间 + 原材料 + 正常态）：甲方主数据里白条产品只有「半扇」一条，
     * 猪头 / 猪脚归猪肉产品（belong_type=pork），同样配在燎毛间。
     */
    private List<ProductInfo> sampleTypes() {
        List<ProductInfo> list = new ArrayList<>();
        ProductInfo half = new ProductInfo();
        half.setId(TYPE_HALF);
        half.setProductId("Y00142");
        half.setProductName("左半扇");
        half.setProductType(1);
        half.setProductUnit("kg");
        half.setBelongType("white_bar");
        list.add(half);
        ProductInfo right = new ProductInfo(); right.setId(TYPE_RIGHT); right.setProductName("右半扇"); right.setBelongType("white_bar"); list.add(right);
        ProductInfo legacy = new ProductInfo(); legacy.setId(TYPE_LEGACY); legacy.setProductName("半扇"); legacy.setBelongType("white_bar"); list.add(legacy);
        ProductInfo head = new ProductInfo();
        head.setId(TYPE_HEAD);
        head.setProductId("Y00116");
        head.setProductName("猪头");
        head.setProductType(1);
        head.setProductUnit("kg");
        head.setBelongType("pork");
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
        i1.setProductId(TYPE_HALF);
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
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBar("pending_singe"));
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
        when(barInfoMapper.updateBurnProgress(eq(BAR_ID), any(BigDecimal.class), any(Date.class), eq(OPERATOR_ID)))
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
        // r134：burn_record 是产出行粒度，不再存损耗（整只损耗 = bar_info.arrive_weight − in_weight 派生）
        assertThat(saved.getLossWeight()).isNull();

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
            .updateBurnProgress(eq(BAR_ID), any(BigDecimal.class), any(Date.class), eq(OPERATOR_ID));
        verify(barInfoMapper, never())
            .updateStatusToInStock(eq(BAR_ID), any(BigDecimal.class), any(Date.class), eq(OPERATOR_ID));
    }

    @Test
    @DisplayName("submitBurnRecord: bar 状态不符(in_stock) → 抛 + 不写入")
    void testSubmit_BarStatusInvalid() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBar("in_stock"));

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
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.submitBurnRecord(sampleBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("白条不存在");

        verify(burnMapper, never()).insert(any(PigBurnRecord.class));
    }

    @Test
    @DisplayName("submitBurnRecord: 无效产品类型 → 抛 + 燎毛记录不写")
    void testSubmit_InvalidProductType() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBar("pending_singe"));
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
    @DisplayName("submitBurnRecord: 已入库+本次合计 > 头皮肉重量(bar.arrive_weight) → 抛 + 不写入")
    void testSubmit_ArriveLessThanInbound() {
        // r194/FIX-WMS-CUTPICKUP-SPLIT-001：重量上限口径 = bar_info.arrive_weight（头皮肉重量），
        // 非 bo.arriveWeight。单品 ≤ 头皮肉重量、累计（DB 已入库 + 本次）≤ 头皮肉重量。
        BarInfo bar = sampleBar("pending_singe");
        bar.setMarketingWeight(new BigDecimal("84.000")); // 单品 80.3 / 5.2 均 ≤ 84，累计 85.5 > 84
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(bar);
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(new LocationInfo());
        stubTypes();
        // productInhouseMapper.selectList 未 stub → 空列表（该白条尚无已入库产出行）

        assertThatThrownBy(() -> service.submitBurnRecord(sampleBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能超过出栏重量");

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

    private org.dromara.djs.warehouse.burn.domain.vo.BurnInboundVo inhouse(Long productId, String weight) {
        var ih = new org.dromara.djs.warehouse.burn.domain.vo.BurnInboundVo();
        ih.setProductId(productId);
        ih.setWeight(new BigDecimal(weight));
        ih.setBarInfoId(BAR_ID);
        return ih;
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("finishBurn: happy(半扇 2 扇合计 80.3 ≤ 头皮肉重 110.5) → bar singing→in_stock 回填 in_weight 合计")
    void testFinish_Happy() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "110.500"));
        when(flowMapper.selectBurnInbounds(any()))
            .thenReturn(List.of(inhouse(TYPE_HALF, "40.150"), inhouse(TYPE_RIGHT, "40.150")));
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
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "100.000"));
        when(flowMapper.selectBurnInbounds(any()))
            .thenReturn(List.of(inhouse(TYPE_HALF, "60.000"), inhouse(TYPE_RIGHT, "60.000")));
        when(productInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sampleTypes());

        assertThatThrownBy(() -> service.finishBurn(BAR_ID, OPERATOR_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能超过出栏重量");

        verify(barInfoMapper, never()).updateStatusToInStock(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("finishBurn: 未称重(接收重量为空)时，白条重仍不得超出栏重 —— 出品率分子不能大于分母")
    void testFinish_InWeightCappedByMarketingWhenNotWeighed() {
        // 外购猪 / 没走称重就直接处理完成的白条，arrive_weight 恰恰是 NULL，
        // 「累计总重 ≤ 头皮肉重量」那道闸整段跳过；而分母 marketing_weight 非空（建 bar 时就写死），
        // 于是单头录错就能把当日出品率顶过 100%。这条锁的就是这个洞。
        BarInfo bar = sampleBarWithMarketWeight("singing", "100.000");
        bar.setArriveWeight(null);
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(bar);
        when(flowMapper.selectBurnInbounds(any()))
            .thenReturn(List.of(inhouse(TYPE_HALF, "60.000"), inhouse(TYPE_RIGHT, "60.000")));
        when(productInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sampleTypes());

        assertThatThrownBy(() -> service.finishBurn(BAR_ID, OPERATOR_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("白条重量不能超过出栏重量");

        verify(barInfoMapper, never()).updateStatusToInStock(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("finishBurn: 未称重 + 白条重 ≤ 出栏重 → 正常放行（新闸不得比改动前更严）")
    void testFinish_NotWeighedWithinMarketingStillPasses() {
        BarInfo bar = sampleBarWithMarketWeight("singing", "200.000");
        bar.setArriveWeight(null);
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(bar);
        when(flowMapper.selectBurnInbounds(any()))
            .thenReturn(List.of(inhouse(TYPE_HALF, "60.000"), inhouse(TYPE_RIGHT, "60.000")));
        when(productInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sampleTypes());
        when(barInfoMapper.updateStatusToInStock(any(), any(), any(), any())).thenReturn(1);

        service.finishBurn(BAR_ID, OPERATOR_ID);

        verify(barInfoMapper).updateStatusToInStock(eq(BAR_ID), eq(new BigDecimal("120.000")), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("finishBurn: 半扇只录 1 扇 → 抛 半只需录入 2 个")
    void testFinish_HalfNotPaired() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "200.000"));
        when(productInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sampleTypes());
        when(flowMapper.selectBurnInbounds(any()))
            .thenReturn(List.of(inhouse(TYPE_HALF, "50.000"), inhouse(TYPE_HEAD, "5.000")));

        assertThatThrownBy(() -> service.finishBurn(BAR_ID, OPERATOR_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("左半扇和右半扇须各录入 1 个");

        verify(barInfoMapper, never()).updateStatusToInStock(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("finishBurn: 只录猪头，0半扇 → 拒绝完成")
    void testFinish_NonWhiteBarOnly() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "110.500"));
        when(productInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sampleTypes());
        when(flowMapper.selectBurnInbounds(any()))
            .thenReturn(List.of(inhouse(TYPE_HEAD, "5.200")));
        when(barInfoMapper.updateStatusToInStock(eq(BAR_ID), any(BigDecimal.class), any(Date.class), eq(OPERATOR_ID)))
            .thenReturn(1);

        assertThatThrownBy(() -> service.finishBurn(BAR_ID, OPERATOR_ID))
            .isInstanceOf(ServiceException.class).hasMessageContaining("当前已录 0/2");
        verify(barInfoMapper, never()).updateStatusToInStock(any(), any(), any(), any());
    }

    @Test
    @DisplayName("finishBurn: bar 不在 singing 态 → 抛 白条状态不符")
    void testFinish_BarNotSinging() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("pending_singe", "110.500"));

        assertThatThrownBy(() -> service.finishBurn(BAR_ID, OPERATOR_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("白条状态不符");

        verify(barInfoMapper, never()).updateStatusToInStock(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("finishBurn: 无任何产品入库 → 抛 尚未录入任何产品入库")
    void testFinish_NoInhouse() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "110.500"));
        when(flowMapper.selectBurnInbounds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.finishBurn(BAR_ID, OPERATOR_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("尚未录入任何产品入库");

        verify(barInfoMapper, never()).updateStatusToInStock(any(), any(), any(), any());
    }

    @Test void oldHalfIsReadonlyHistoryAndCannotBeSubmittedOrCountAsEitherSide() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing","200"));
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(new LocationInfo()); stubTypes();
        when(imageUrlResolver.resolveList(any())).thenReturn(List.of());
        assertThat(service.queryProductTypes(BAR_ID)).extracting(org.dromara.djs.warehouse.burn.domain.vo.BurnProductTypeVo::getProductId).doesNotContain(TYPE_LEGACY);
        when(flowMapper.selectBurnInbounds(any())).thenReturn(List.of(inhouse(TYPE_LEGACY,"40")));
        var legacy=service.queryProductTypes(BAR_ID).stream().filter(v -> v.getProductId().equals(TYPE_LEGACY)).findFirst().orElseThrow();
        assertThat(legacy.getCanRecord()).isFalse(); assertThat(legacy.getRecordedCount()).isEqualTo(1);
        var bo=sampleBo(); bo.getProductTypeItems().getFirst().setProductId(TYPE_LEGACY);
        assertThatThrownBy(() -> service.submitBurnRecord(bo)).hasMessageContaining("仅保留历史");
        assertThatThrownBy(() -> service.finishBurn(BAR_ID,OPERATOR_ID,true)).hasMessageContaining("核对历史");
        verify(burnMapper,never()).insert(any(PigBurnRecord.class));
        verify(barInfoMapper,never()).updateStatusToInStock(any(),any(),any(),any());
    }
    @Test void differentProductIdCannotRecordSameSideAndEverySameSideCardIsDisabled() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing","200"));
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(new LocationInfo());
        Long aliasId=100000000000000004L;
        var alias=new ProductInfo(); alias.setId(aliasId); alias.setProductName("左半扇"); alias.setBelongType("white_bar");
        var types=sampleTypes(); types.add(alias);
        when(productInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(types);
        when(imageUrlResolver.resolveList(any())).thenReturn(List.of());
        when(flowMapper.selectBurnInbounds(any())).thenReturn(List.of(inhouse(TYPE_HALF,"40")));
        var bo=sampleBo(); bo.getProductTypeItems().getFirst().setProductId(aliasId);
        assertThatThrownBy(() -> service.submitBurnRecord(bo)).hasMessageContaining("左半扇只允许录入 1 次");
        verify(burnMapper,never()).insert(any(PigBurnRecord.class));
        var cards=service.queryProductTypes(BAR_ID);
        var recorded=cards.stream().filter(c -> c.getProductId().equals(TYPE_HALF)).findFirst().orElseThrow();
        var sameSide=cards.stream().filter(c -> c.getProductId().equals(aliasId)).findFirst().orElseThrow();
        var otherSide=cards.stream().filter(c -> c.getProductId().equals(TYPE_RIGHT)).findFirst().orElseThrow();
        assertThat(recorded.getCanRecord()).isFalse(); assertThat(recorded.getRecordedCount()).isEqualTo(1);
        assertThat(sameSide.getCanRecord()).isFalse(); assertThat(sameSide.getRecordedCount()).isZero();
        assertThat(otherSide.getCanRecord()).isTrue(); assertThat(otherSide.getRecordedCount()).isZero();
    }

    @Test void twoIdenticalSidesCannotFinishEvenWhenWeightIsConfirmed() {
        readyToFinish("40");
        when(flowMapper.selectBurnInbounds(any())).thenReturn(List.of(inhouse(TYPE_HALF,"40"),inhouse(TYPE_HALF,"40")));
        assertThatThrownBy(() -> service.finishBurn(BAR_ID,OPERATOR_ID,true)).hasMessageContaining("左半扇和右半扇须各录入 1 个");
    }
    @Test void legacyReceiptsStillCountTowardTwoWhiteBarMaximum() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing","200"));
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(new LocationInfo()); stubTypes();
        when(flowMapper.selectBurnInbounds(any())).thenReturn(List.of(inhouse(TYPE_LEGACY,"40"),inhouse(TYPE_RIGHT,"40")));
        assertThatThrownBy(() -> service.submitBurnRecord(sampleBo())).hasMessageContaining("合计最多录入 2 次");
        verify(burnMapper,never()).insert(any(PigBurnRecord.class));
    }

    @Test
    void sameWhiteBarProductCannotBeRecordedTwice() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "200"));
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(new LocationInfo());
        stubTypes();
        when(flowMapper.selectBurnInbounds(any())).thenReturn(List.of(inhouse(TYPE_HALF,"40")));
        assertThatThrownBy(() -> service.submitBurnRecord(sampleBo())).hasMessageContaining("只允许录入 1 次");
        verify(burnMapper,never()).insert(any(PigBurnRecord.class));
    }

    @Test
    void legacyDuplicateHalfReceiptsStillPreventFurtherEntry() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "200"));
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(new LocationInfo());
        stubTypes();
        when(flowMapper.selectBurnInbounds(any())).thenReturn(List.of(inhouse(TYPE_HALF,"40"), inhouse(TYPE_HALF,"40")));
        assertThatThrownBy(() -> service.submitBurnRecord(sampleBo())).hasMessageContaining("只允许录入 1 次");
        verify(burnMapper,never()).insert(any(PigBurnRecord.class));
        verify(productInhouseMapper,never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void consumedOtherProductCannotBeRecordedAgain() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "200"));
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(new LocationInfo());
        stubTypes();
        when(flowMapper.selectBurnInbounds(any())).thenReturn(List.of(inhouse(TYPE_HEAD,"5")));
        assertThatThrownBy(() -> service.submitBurnRecord(sampleBo())).hasMessageContaining("只允许录入 1 次");
        verify(burnMapper,never()).insert(any(PigBurnRecord.class));
    }

    @Test
    void subsequentProductKeepsFirstReceiptTimeAndAddsWeight() {
        BarInfo bar=sampleBarWithMarketWeight("singing", "200"); bar.setArriveWeight(new BigDecimal("40"));
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(bar);
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(new LocationInfo()); stubTypes();
        Date first=new Date(1000L);
        var prior=inhouse(TYPE_HALF,"40"); prior.setFlowTime(first);
        when(flowMapper.selectBurnInbounds(any())).thenReturn(List.of(prior));
        when(barInfoMapper.updateBurnProgress(any(),any(),any(),any())).thenReturn(1);
        var bo=sampleBo(); bo.setProductTypeItems(List.of(bo.getProductTypeItems().get(1)));
        bo.getProductTypeItems().getFirst().setWeight(new BigDecimal("40"));
        service.submitBurnRecord(bo);
        verify(barInfoMapper).updateBurnProgress(eq(BAR_ID),eq(new BigDecimal("80")),eq(first),eq(OPERATOR_ID));
    }

    @Test
    void allDirectShippedHalvesFinishWithoutInventingInventory() {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "100"));
        stubTypes();
        when(flowMapper.selectBurnInbounds(any())).thenReturn(List.of(inhouse(TYPE_HALF,"40"),inhouse(TYPE_RIGHT,"40")));
        when(barInfoMapper.updateStatusToInStock(any(),any(),any(),any())).thenReturn(1);
        when(barInfoMapper.fullyDirectShippedWeight(BAR_ID)).thenReturn(new BigDecimal("80"));
        service.finishBurn(BAR_ID,OPERATOR_ID);
        verify(barInfoMapper).updateStatusToShipOut(eq(BAR_ID),any(),eq(new BigDecimal("80")),eq(OPERATOR_ID));
        verify(productInhouseMapper,never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void legacyStandaloneWeighDoesNotInventAReceiveTimeOrWeight() {
        BarInfo bar=sampleBar("singing"); bar.setInTime(new Date(999L)); bar.setArriveWeight(new BigDecimal("100"));
        when(barInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(bar));
        var result=service.queryPendingBars().getFirst();
        assertThat(result.getReceiveTime()).isNull();
        assertThat(result.getArriveWeight()).isNull();
        assertThat(result.getInboundedWeight()).isEqualByComparingTo("0");
    }

    @Test
    void pendingCardUsesEarliestProductReceiptEvenIfStoredTimeWasOverwritten() {
        BarInfo bar=sampleBar("singing"); bar.setInTime(new Date(9000L));
        when(barInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(bar));
        var first=inhouse(TYPE_HALF,"40"); first.setFlowTime(new Date(1000L));
        var second=inhouse(TYPE_HALF,"41"); second.setFlowTime(new Date(2000L));
        when(flowMapper.selectBurnInbounds(any())).thenReturn(List.of(second,first));
        var result=service.queryPendingBars().getFirst();
        assertThat(result.getReceiveTime()).isEqualTo(new Date(1000L));
        assertThat(result.getArriveWeight()).isEqualByComparingTo("81");
        assertThat(result.getInboundedWeight()).isEqualByComparingTo("81");
    }

    private void readyToFinish(String halfWeight) {
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBarWithMarketWeight("singing", "100"));
        stubTypes();
        when(flowMapper.selectBurnInbounds(any())).thenReturn(List.of(inhouse(TYPE_HALF,halfWeight),inhouse(TYPE_RIGHT,halfWeight)));
        when(barInfoMapper.updateStatusToInStock(any(),any(),any(),any())).thenReturn(1);
    }

    @Test void lowBurnPreflightDoesNotWriteAndUnconfirmedFinishRefuses() {
        readyToFinish("20");
        var check=service.finishCheck(BAR_ID);
        assertThat(check.confirmationRequired()).isTrue();
        assertThat(check.message()).isEqualTo("当前白条重量有误，请联系管理员处理。");
        assertThatThrownBy(() -> service.finishBurn(BAR_ID,OPERATOR_ID,false)).hasMessage(check.message());
        verify(barInfoMapper,never()).updateStatusToInStock(any(),any(),any(),any());
    }
    @Test void confirmedLowBurnCannotFinishAndExactBoundaryStillPasses() {
        readyToFinish("20");
        assertThatThrownBy(() -> service.finishBurn(BAR_ID,OPERATOR_ID,true))
            .hasMessage("当前白条重量有误，请联系管理员处理。");
        verify(barInfoMapper,never()).updateStatusToInStock(any(),any(),any(),any());
        readyToFinish("25"); assertThat(service.finishCheck(BAR_ID).confirmationRequired()).isFalse();
        service.finishBurn(BAR_ID,OPERATOR_ID,false);
        verify(barInfoMapper).updateStatusToInStock(eq(BAR_ID),eq(new BigDecimal("50")),any(),eq(OPERATOR_ID));
    }
    @Test void finishRechecksChangedDictionaryAndConfirmationCannotBypassBadConfiguration() {
        readyToFinish("25"); assertThat(service.finishCheck(BAR_ID).confirmationRequired()).isFalse();
        var threshold=new org.dromara.common.core.domain.dto.DictDataDTO(); threshold.setIsDefault("Y"); threshold.setDictValue("60");
        when(dictService.getDictData(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of(threshold));
        assertThatThrownBy(() -> service.finishBurn(BAR_ID,OPERATOR_ID,false)).hasMessage("当前白条重量有误，请联系管理员处理。");
        when(dictService.getDictData(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        assertThatThrownBy(() -> service.finishBurn(BAR_ID,OPERATOR_ID,true)).hasMessageContaining("默认百分比");
        verify(barInfoMapper,never()).updateStatusToInStock(any(),any(),any(),any());
    }
    @Test void confirmationNeverBypassesHalfCountStatusOrMarketingCap() {
        readyToFinish("25");
        when(flowMapper.selectBurnInbounds(any())).thenReturn(List.of(inhouse(TYPE_HALF,"25")));
        assertThatThrownBy(() -> service.finishBurn(BAR_ID,OPERATOR_ID,true)).hasMessageContaining("当前已录 1/2");
        readyToFinish("60");
        assertThatThrownBy(() -> service.finishBurn(BAR_ID,OPERATOR_ID,true)).hasMessageContaining("不能超过出栏重量");
        when(barInfoMapper.selectForUpdate(BAR_ID)).thenReturn(sampleBar("in_stock"));
        assertThatThrownBy(() -> service.finishBurn(BAR_ID,OPERATOR_ID,true)).hasMessageContaining("状态不符");
        verify(barInfoMapper,never()).updateStatusToInStock(any(),any(),any(),any());
    }

    private PigBurnWeighBo weighBo(String arriveWeight) {
        PigBurnWeighBo bo = new PigBurnWeighBo();
        bo.setBarInfoId(BAR_ID);
        bo.setArriveWeight(new BigDecimal(arriveWeight));
        bo.setWeigherId(OPERATOR_ID);
        return bo;
    }

    @Test
    @DisplayName("weighBurn: 入库重量 > 出栏重量×70% → 落库（150 出栏，录 110）")
    void testWeigh_AboveMinRatio() {
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(sampleBarWithMarketWeight("pending_singe", "150.000"));
        when(barInfoMapper.updateStatusToSinging(eq(BAR_ID), any(), eq(OPERATOR_ID))).thenReturn(1);

        assertThat(service.weighBurn(weighBo("110.000"))).isTrue();

        ArgumentCaptor<BarInfo> patch = ArgumentCaptor.forClass(BarInfo.class);
        verify(barInfoMapper).updateById(patch.capture());
        assertThat(patch.getValue().getArriveWeight()).isEqualByComparingTo("110.000");
        // V6 row113：到场时间必须和到场重量同一次落库（这一列此前建表起从没被写过，卡片恒显「—」）
        assertThat(patch.getValue().getArriveTime()).isNotNull();
    }

    @Test
    @DisplayName("weighBurn: 已有到场时间的白条重复称重 → 只改重量，到场时间锚定第一次不被推后")
    void testWeigh_doesNotOverwriteExistingArriveTime() {
        BarInfo bar = sampleBarWithMarketWeight("singing", "150.000");
        Date firstArrive = new Date(1_755_000_000_000L);
        bar.setArriveTime(firstArrive);
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(bar);
        when(barInfoMapper.updateStatusToSinging(eq(BAR_ID), any(), eq(OPERATOR_ID))).thenReturn(1);

        assertThat(service.weighBurn(weighBo("120.000"))).isTrue();

        ArgumentCaptor<BarInfo> patch = ArgumentCaptor.forClass(BarInfo.class);
        verify(barInfoMapper).updateById(patch.capture());
        assertThat(patch.getValue().getArriveWeight()).isEqualByComparingTo("120.000");
        // 已有值不覆盖：重复称重不该把到场时间推到已录产出行的入库时间之后（clean-QA 复现过这条倒挂）
        assertThat(patch.getValue().getArriveTime()).isNull();
    }

    @Test
    @DisplayName("weighBurn: 入库重量 < 出栏重量×50% → 抛 请录入正确的入库重量，状态不推进")
    void testWeigh_BelowMinRatio() {
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(sampleBarWithMarketWeight("pending_singe", "150.000"));

        assertThatThrownBy(() -> service.weighBurn(weighBo("70.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("请录入正确的入库重量");

        verify(barInfoMapper, never()).updateStatusToSinging(any(), any(), any());
        verify(barInfoMapper, never()).updateById(any(BarInfo.class));
    }

    @Test
    @DisplayName("weighBurn: 入库重量 == 出栏重量×50% 边界 → 拒（甲方口径是「必须大于」）")
    void testWeigh_EqualMinRatioRejected() {
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(sampleBarWithMarketWeight("pending_singe", "150.000"));

        assertThatThrownBy(() -> service.weighBurn(weighBo("75.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("请录入正确的入库重量");

        verify(barInfoMapper, never()).updateStatusToSinging(any(), any(), any());
    }

}
