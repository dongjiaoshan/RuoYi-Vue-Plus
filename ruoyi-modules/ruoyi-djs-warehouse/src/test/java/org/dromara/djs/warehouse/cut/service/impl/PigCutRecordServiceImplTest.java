package org.dromara.djs.warehouse.cut.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.cut.domain.PigCutRecord;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutDoneBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutOutBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutPickupBo;
import org.dromara.djs.warehouse.cut.mapper.PigCutRecordMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PigCutRecordServiceImpl} 单测（WMS-PIG-002）。
 *
 * <p>覆盖 3 阶段事务一致性的核心场景：</p>
 * <ol>
 *   <li>pickup happy：bar_info status='in_stock' → cut_record INSERT + bar_info UPDATE pending_cut</li>
 *   <li>pickup 状态冲突：bar_info.status='cut_done' → 抛 + 不变更任何表</li>
 *   <li>cutOut happy 多部位：3 部位 → 3 location_stock 篮子 INSERT（入冷库）+ 4 stock_flow INSERT（3 入 + 1 出）+ status 推进</li>
 *   <li>cutOut 状态不符：cut_status='done' → 抛 + 任何 mapper 不调用</li>
 *   <li>cutDone happy：cutting → done + bar_info cut_done + acidRemoveMinutes 计算 + outWeight 正确</li>
 * </ol>
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PigCutRecordServiceImpl 单元测试")
class PigCutRecordServiceImplTest {

    @Mock
    private PigCutRecordMapper cutMapper;
    @Mock
    private BarInfoMapper barInfoMapper;
    @Mock
    private LocationStockMapper locationStockMapper;
    @Mock
    private StockFlowMapper flowMapper;
    @Mock
    private ProductInfoMapper productInfoMapper;
    @Mock
    private org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper productInhouseMapper;
    @Mock
    private LocationInfoMapper locationInfoMapper;
    @Mock
    private org.dromara.djs.common.supplier.mapper.SupplierMapper supplierMapper;
    @Mock
    private IBizCodeGenerator bizCodeGenerator;
    @Mock
    private org.dromara.djs.warehouse.trace.service.ITraceService traceService;
    @Mock
    private org.dromara.djs.common.image.service.ImageUrlResolver imageUrlResolver;
    @Mock
    private org.dromara.djs.warehouse.check.service.IStockCheckService stockCheckService;
    @Mock
    private org.dromara.djs.warehouse.loss.service.ILossFlowService lossFlowService;

    private TestablePigCutRecordServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    /**
     * 子类化：stub generateCutId / resolveProductIdByCutPart / resolveWhiteBarProductId 避开真实 product_info 查询。
     */
    static class TestablePigCutRecordServiceImpl extends PigCutRecordServiceImpl {
        TestablePigCutRecordServiceImpl(PigCutRecordMapper c, BarInfoMapper b,
                                        StockFlowMapper f, ProductInfoMapper p,
                                        org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper ph,
                                        LocationInfoMapper l,
                                        LocationStockMapper ls,
                                        org.dromara.djs.common.supplier.mapper.SupplierMapper s,
                                        IBizCodeGenerator g,
                                        org.dromara.djs.warehouse.trace.service.ITraceService ts,
                                        org.dromara.djs.common.image.service.ImageUrlResolver ir,
                                        org.dromara.djs.warehouse.check.service.IStockCheckService scs,
                                        org.dromara.djs.warehouse.loss.service.ILossFlowService lf) {
            super(c, b, f, p, ph, l, ls, s, g, ts, ir, scs, lf);
        }

        @Override
        protected String generateCutId() {
            return "CUT2606051001";
        }

        @Override
        protected Long resolveProductIdByCutPart(String cutPart) {
            return switch (cutPart) {
                case "lean" -> 100000000000000101L;
                case "part" -> 100000000000000102L;
                case "bone" -> 100000000000000103L;
                case "skin" -> 100000000000000104L;
                case "scrap" -> 100000000000000105L;
                default -> 0L;
            };
        }

        @Override
        protected Long resolveWhiteBarProductId() {
            return 100000000000000001L;
        }
    }

    @BeforeAll
    static void initMpEntityCache() {
        // MyBatis-Plus 单测 entity cache 预热（coder-mp-entity-cache-test）：submitPickup/submitCutOut/submitCutDone
        // 用 LambdaQueryWrapper<PigCutRecord>/<ProductInhouse>/<LocationStock> 等；无 Spring 上下文时 TableInfoHelper
        // 解析不到 lambda 列名 → 预热相关实体。r148 后 submitCutDone 能走到 pendingRows 的 ProductInhouse lambda。
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, PigCutRecord.class);
        TableInfoHelper.initTableInfo(assistant, ProductInhouse.class);
        TableInfoHelper.initTableInfo(assistant, LocationStock.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, BarInfo.class);
    }

    @BeforeEach
    void setup() {
        service = new TestablePigCutRecordServiceImpl(
            cutMapper, barInfoMapper, flowMapper, productInfoMapper, productInhouseMapper, locationInfoMapper, locationStockMapper, supplierMapper, bizCodeGenerator, traceService, imageUrlResolver, stockCheckService, lossFlowService);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(9001L);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private BarInfo sampleBar() {
        BarInfo bar = new BarInfo();
        bar.setId(70001L);
        bar.setBarId("BAR2606040001");
        bar.setEarNo("TEST-EAR-001");
        bar.setInWeight(new BigDecimal("80.000"));
        bar.setStatus("in_stock");
        return bar;
    }

    private PigCutPickupBo samplePickupBo() {
        PigCutPickupBo bo = new PigCutPickupBo();
        bo.setBarInfoId(70001L);
        bo.setLocationId(90002L);
        bo.setIsHalf(2);
        bo.setRemark("e2e pickup");
        return bo;
    }

    // -------- pickup --------

    @Test
    @DisplayName("submitPickup: happy → cut_record INSERT + bar_info pending_cut UPDATE + 字段冗余正确")
    void testPickup_Happy() {
        when(barInfoMapper.selectById(70001L)).thenReturn(sampleBar());
        when(barInfoMapper.updateStatusToPendingCut(eq(70001L), eq(9001L))).thenReturn(1);
        when(cutMapper.insert(any(PigCutRecord.class))).thenAnswer(inv -> {
            PigCutRecord r = inv.getArgument(0);
            r.setId(80001L);
            return 1;
        });

        Long id = service.submitPickup(samplePickupBo());

        assertThat(id).isEqualTo(80001L);
        ArgumentCaptor<PigCutRecord> cap = ArgumentCaptor.forClass(PigCutRecord.class);
        verify(cutMapper, times(1)).insert(cap.capture());
        PigCutRecord saved = cap.getValue();
        assertThat(saved.getCutId()).isEqualTo("CUT2606051001");
        assertThat(saved.getWhiteBarId()).isEqualTo(70001L);
        assertThat(saved.getBarId()).isEqualTo("BAR2606040001");
        assertThat(saved.getEarNo()).isEqualTo("TEST-EAR-001");
        assertThat(saved.getPickupWeight()).isEqualByComparingTo("80.000");
        assertThat(saved.getOperatorId()).isEqualTo(9001L);
        assertThat(saved.getLocationId()).isEqualTo(90002L);
        assertThat(saved.getIsHalf()).isEqualTo(2);
        assertThat(saved.getCutStatus()).isEqualTo("picked");
    }

    @Test
    @DisplayName("submitPickup: 白条状态不符 → 抛 + cut_record.insert 不调用")
    void testPickup_BarStatusInvalid() {
        BarInfo bar = sampleBar();
        bar.setStatus("cut_done");
        when(barInfoMapper.selectById(70001L)).thenReturn(bar);

        assertThatThrownBy(() -> service.submitPickup(samplePickupBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("白条状态不符");

        verify(barInfoMapper, never()).updateStatusToPendingCut(anyLong(), anyLong());
        verify(cutMapper, never()).insert(any(PigCutRecord.class));
    }

    @Test
    @DisplayName("submitPickup: 并发抢占 affectedRows=0 → 抛 已被并发领用")
    void testPickup_ConcurrentLost() {
        when(barInfoMapper.selectById(70001L)).thenReturn(sampleBar());
        when(barInfoMapper.updateStatusToPendingCut(eq(70001L), eq(9001L))).thenReturn(0);

        assertThatThrownBy(() -> service.submitPickup(samplePickupBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("并发领用");

        verify(cutMapper, never()).insert(any(PigCutRecord.class));
    }

    // -------- cutOut --------

    private PigCutRecord sampleRecord(String status) {
        PigCutRecord r = new PigCutRecord();
        r.setId(80001L);
        r.setCutId("CUT2606051001");
        r.setWhiteBarId(70001L);
        r.setBarId("BAR2606040001");
        r.setEarNo("TEST-EAR-001");
        r.setPickupTime(new Date(System.currentTimeMillis() - 60 * 60 * 1000L)); // 1h ago
        r.setPickupWeight(new BigDecimal("80.000"));
        // r148：记录从 DB 载入时 drip_loss 已在领用时按白条写好（= 入库重 − 领用重），非 null。
        // 单半只场景与整猪收口口径一致（81.5 − 80 = 1.5），submitCutDone 透传给 updateStatusToDone。
        r.setDripLoss(new BigDecimal("1.500"));
        r.setLocationId(90002L);
        r.setCutStatus(status);
        return r;
    }

    private PigCutOutBo sampleCutOutBo() {
        PigCutOutBo bo = new PigCutOutBo();
        bo.setCutRecordId(80001L);
        bo.setLocationId(90002L);

        PigCutOutBo.PartItem p1 = new PigCutOutBo.PartItem();
        p1.setCutPart("lean");
        p1.setProductWeight(new BigDecimal("30.000"));

        PigCutOutBo.PartItem p2 = new PigCutOutBo.PartItem();
        p2.setCutPart("bone");
        p2.setProductWeight(new BigDecimal("15.000"));

        PigCutOutBo.PartItem p3 = new PigCutOutBo.PartItem();
        p3.setCutPart("skin");
        p3.setProductWeight(new BigDecimal("5.000"));

        bo.setPartItems(List.of(p1, p2, p3));
        return bo;
    }

    @Test
    @DisplayName("submitCutOut: 3 部位 happy → 3 location_stock 篮子(入冷库) + 4 stock_flow（3 入冻品 + 1 白条出）+ status 推进")
    void testCutOut_Happy() {
        when(cutMapper.selectById(80001L)).thenReturn(sampleRecord("picked"));
        when(cutMapper.updateStatusToCutting(eq(80001L), any(Date.class), eq(9001L))).thenReturn(1);
        when(barInfoMapper.updateStatusToCutting(eq(70001L), eq(9001L))).thenReturn(1);
        LocationInfo loc = new LocationInfo();
        loc.setId(90002L);
        loc.setLocationName("冻品库 A");
        when(locationInfoMapper.selectById(90002L)).thenReturn(loc);
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap()))
            .thenReturn("F2606050IN0001", "F2606050IN0002", "F2606050IN0003", "F2606050OT0001");

        service.submitCutOut(sampleCutOutBo());

        // 3 行 location_stock 篮子（入冷库，doc/14 §1：分割→入库，ear_no 作篮子标签）
        ArgumentCaptor<LocationStock> basketCap = ArgumentCaptor.forClass(LocationStock.class);
        verify(locationStockMapper, times(3)).insert(basketCap.capture());
        List<LocationStock> baskets = basketCap.getAllValues();
        assertThat(baskets).extracting(LocationStock::getEarNo)
            .allMatch(v -> v.equals("TEST-EAR-001"));
        assertThat(baskets).extracting(LocationStock::getLocationId)
            .allMatch(v -> v.equals(90002L));
        assertThat(baskets.get(0).getProductId()).isEqualTo(100000000000000101L);
        assertThat(baskets.get(1).getProductId()).isEqualTo(100000000000000103L);
        assertThat(baskets.get(2).getProductId()).isEqualTo(100000000000000104L);
        assertThat(baskets.get(0).getProductStock()).isEqualByComparingTo("30.000");
        assertThat(baskets.get(1).getProductStock()).isEqualByComparingTo("15.000");
        assertThat(baskets.get(2).getProductStock()).isEqualByComparingTo("5.000");

        // 4 行 stock_flow（3 IN + 1 OT）
        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(flowMapper, times(4)).insert(flowCap.capture());
        List<StockFlow> flows = flowCap.getAllValues();
        assertThat(flows).extracting(StockFlow::getInoutType)
            .containsExactly("IN", "IN", "IN", "OT");
        assertThat(flows.get(3).getFlowType()).isEqualTo("cut_out");
        assertThat(flows.get(3).getChangeNum()).isEqualByComparingTo("50.000");
        assertThat(flows.get(3).getProductId()).isEqualTo(100000000000000001L);
        // 入冻品库的 productId 等于部位标准 SKU
        assertThat(flows.get(0).getProductId()).isEqualTo(100000000000000101L);
        assertThat(flows.get(0).getFlowType()).isEqualTo("cut_out_in");

        // status 推进调用次数
        verify(cutMapper, times(1)).updateStatusToCutting(eq(80001L), any(Date.class), eq(9001L));
        verify(barInfoMapper, times(1)).updateStatusToCutting(eq(70001L), eq(9001L));
    }

    @Test
    @DisplayName("submitCutOut: cut_status='done' → 抛 + 任何 mapper 写入不调用")
    void testCutOut_StatusInvalid() {
        when(cutMapper.selectById(80001L)).thenReturn(sampleRecord("done"));

        assertThatThrownBy(() -> service.submitCutOut(sampleCutOutBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("分割单状态不符");

        verify(locationStockMapper, never()).insert(any(LocationStock.class));
        verify(flowMapper, never()).insert(any(StockFlow.class));
        verify(cutMapper, never()).updateStatusToCutting(anyLong(), any(Date.class), anyLong());
    }

    // -------- cutDone --------

    @Test
    @DisplayName("submitCutDone: happy → done + bar cut_done + acidMinutes 合理 + dripLoss=in−pickup 自动算 + outWeight=pickup")
    void testCutDone_Happy() {
        when(cutMapper.selectById(80001L)).thenReturn(sampleRecord("cutting")); // pickupWeight 80.000
        // 入库重量 81.5 → 滴水损耗自动 = 81.5 − 80 = 1.5；出库重量 = pickupWeight = 80
        BarInfo bar = sampleBar();
        bar.setInWeight(new BigDecimal("81.500"));
        when(barInfoMapper.selectById(70001L)).thenReturn(bar);
        when(cutMapper.updateStatusToDone(eq(80001L), any(Date.class), any(BigDecimal.class),
            any(Integer.class), any(), any(), eq(9001L))).thenReturn(1);
        when(barInfoMapper.updateStatusToCutDone(eq(70001L), any(Date.class), any(BigDecimal.class),
            any(Integer.class), any(BigDecimal.class), eq(9001L))).thenReturn(1);
        // row150 后整猪收口 outWeight = Σ已领产出行 pickup_weight（sumPickedRowWeight）；mock 一条已领行 80.000
        // → outWeight=80、整猪 dripLoss = in(81.5) − 80 = 1.5。
        ProductInhouse pickedRow = new ProductInhouse();
        pickedRow.setPickupWeight(new BigDecimal("80.000"));
        when(productInhouseMapper.selectList(any())).thenReturn(java.util.List.of(pickedRow));

        PigCutDoneBo bo = new PigCutDoneBo();
        bo.setCutRecordId(80001L);
        bo.setRemark("e2e done");

        service.submitCutDone(bo);

        // bar_info update：outWeight = pickupWeight = 80；dripLoss = in − pickup = 1.5（系统自动）
        ArgumentCaptor<BigDecimal> outWeightCap = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> dripCap = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<Integer> minutesCap = ArgumentCaptor.forClass(Integer.class);
        verify(barInfoMapper).updateStatusToCutDone(eq(70001L), any(Date.class), outWeightCap.capture(),
            minutesCap.capture(), dripCap.capture(), eq(9001L));
        assertThat(outWeightCap.getValue()).isEqualByComparingTo("80.000");
        assertThat(dripCap.getValue()).isEqualByComparingTo("1.500");
        // cut_record done 也应写入同一自动算的 dripLoss
        ArgumentCaptor<BigDecimal> doneDripCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(cutMapper).updateStatusToDone(eq(80001L), any(Date.class), doneDripCap.capture(),
            any(Integer.class), any(), any(), eq(9001L));
        assertThat(doneDripCap.getValue()).isEqualByComparingTo("1.500");
        // 排酸时长 ≈ 60 分钟（sample record pickupTime 是 1h ago）
        assertThat(minutesCap.getValue()).isBetween(59, 61);
    }

    @Test
    @DisplayName("submitCutDone: cut_status='picked' → 抛 + 任何 mapper 不写入")
    void testCutDone_StatusInvalid() {
        when(cutMapper.selectById(80001L)).thenReturn(sampleRecord("picked"));

        PigCutDoneBo bo = new PigCutDoneBo();
        bo.setCutRecordId(80001L);

        assertThatThrownBy(() -> service.submitCutDone(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("分割单状态不符");

        verify(cutMapper, never()).updateStatusToDone(anyLong(), any(Date.class), any(BigDecimal.class),
            any(Integer.class), any(), any(), anyLong());
        verify(barInfoMapper, never()).updateStatusToCutDone(anyLong(), any(Date.class), any(BigDecimal.class),
            any(Integer.class), any(BigDecimal.class), anyLong());
    }

}
