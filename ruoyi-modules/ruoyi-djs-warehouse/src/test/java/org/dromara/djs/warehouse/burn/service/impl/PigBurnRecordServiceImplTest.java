package org.dromara.djs.warehouse.burn.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.burn.domain.PigBurnRecord;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnRecordBo;
import org.dromara.djs.warehouse.burn.mapper.PigBurnRecordMapper;
import org.dromara.djs.warehouse.burn.mapper.PigInfoReadMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
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
import java.util.Date;

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
 * {@link PigBurnRecordServiceImpl} 单测（WMS-PIG-001）。
 *
 * <p>覆盖跨表事务一致性的 3 类核心场景：</p>
 * <ol>
 *   <li>happy path：ear_no 已出栏 + 库存充足 → 燎毛记录 INSERT + 库存扣减 + 流水 INSERT 全发生</li>
 *   <li>库存不足：affectedRows=0 → 抛 "白条库存不足..." + 流水 INSERT 不发生（Spring @Transactional
 *       回滚由集成测试覆盖；单测验证"抛异常前流水未触发"）</li>
 *   <li>耳号未出栏：current_status != 'END' → 抛 "猪只未出栏..." + 任何 mapper 都不调</li>
 * </ol>
 *
 * <p>Mock 全链路：用 {@link Mockito#mockStatic(Class)} stub {@link LoginHelper}，避开 Sa-Token 上下文。
 * 这样测试可以在不启 Spring 的情况下覆盖跨 mapper 调用次序 / 异常路径。</p>
 *
 * @author djs
 * @since WMS-PIG-001
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
    private LocationStockMapper stockMapper;
    @Mock
    private StockFlowMapper flowMapper;
    @Mock
    private PigInfoReadMapper pigInfoReadMapper;
    @Mock
    private LocationInfoMapper locationInfoMapper;
    @Mock
    private IBizCodeGenerator bizCodeGenerator;

    private TestablePigBurnRecordServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    /**
     * 子类化避开 MapStruct convert（无 Spring 上下文）+ stub generateBurnId 固定值。
     */
    static class TestablePigBurnRecordServiceImpl extends PigBurnRecordServiceImpl {
        TestablePigBurnRecordServiceImpl(PigBurnRecordMapper b, LocationStockMapper s, StockFlowMapper f,
                                         PigInfoReadMapper p, LocationInfoMapper l, IBizCodeGenerator g) {
            super(b, s, f, p, l, g);
        }

        @Override
        protected PigBurnRecord toEntity(PigBurnRecordBo bo) {
            if (bo == null) return null;
            PigBurnRecord e = new PigBurnRecord();
            e.setEarNo(bo.getEarNo());
            e.setBurnTime(bo.getBurnTime());
            e.setArriveWeight(bo.getArriveWeight());
            e.setBurnWeight(bo.getBurnWeight());
            e.setLocationId(bo.getLocationId());
            e.setProofOssIds(bo.getProofOssIds());
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
            burnMapper, stockMapper, flowMapper, pigInfoReadMapper, locationInfoMapper, bizCodeGenerator);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(9001L);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private PigBurnRecordBo sampleBo() {
        PigBurnRecordBo bo = new PigBurnRecordBo();
        bo.setEarNo("TEST-EAR-001");
        bo.setBurnTime(new Date());
        bo.setArriveWeight(new BigDecimal("80.500"));
        bo.setBurnWeight(new BigDecimal("75.300"));
        bo.setLocationId(90001L);
        bo.setProofOssIds("100,101");
        bo.setRemark("e2e");
        return bo;
    }

    @Test
    @DisplayName("submitBurnRecord: happy → 3 mapper 全调用 + lossWeight 计算正确 + burnStatus=done")
    void testSubmit_Happy() {
        when(pigInfoReadMapper.selectCurrentStatusByEarNo("TEST-EAR-001")).thenReturn("END");
        when(burnMapper.insert(any(PigBurnRecord.class))).thenAnswer(inv -> {
            PigBurnRecord e = inv.getArgument(0);
            e.setId(60001L);
            return 1;
        });
        when(stockMapper.deductByEarNo(eq(90001L), eq("TEST-EAR-001"), any(BigDecimal.class), eq(9001L)))
            .thenReturn(1);
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap())).thenReturn("F2606040OT0001");
        when(flowMapper.insert(any(StockFlow.class))).thenReturn(1);

        Long id = service.submitBurnRecord(sampleBo());

        assertThat(id).isEqualTo(60001L);

        ArgumentCaptor<PigBurnRecord> burnCaptor = ArgumentCaptor.forClass(PigBurnRecord.class);
        verify(burnMapper, times(1)).insert(burnCaptor.capture());
        PigBurnRecord saved = burnCaptor.getValue();
        assertThat(saved.getBurnId()).isEqualTo("BURN2606040001");
        assertThat(saved.getBurnStatus()).isEqualTo("done");
        assertThat(saved.getOperatorId()).isEqualTo(9001L);
        assertThat(saved.getLossWeight()).isEqualByComparingTo("5.200");

        ArgumentCaptor<StockFlow> flowCaptor = ArgumentCaptor.forClass(StockFlow.class);
        verify(flowMapper, times(1)).insert(flowCaptor.capture());
        StockFlow flow = flowCaptor.getValue();
        assertThat(flow.getFlowNo()).isEqualTo("F2606040OT0001");
        assertThat(flow.getFlowType()).isEqualTo("slaughter_burn");
        assertThat(flow.getInoutType()).isEqualTo("OT");
        assertThat(flow.getEarNo()).isEqualTo("TEST-EAR-001");
        assertThat(flow.getChangeNum()).isEqualByComparingTo("75.300");
        assertThat(flow.getChangeQuantity()).isEqualByComparingTo("75.300");
        assertThat(flow.getOperatorId()).isEqualTo(9001L);
    }

    @Test
    @DisplayName("submitBurnRecord: 库存不足 → affectedRows=0 抛 ServiceException + flow.insert 不调用")
    void testSubmit_StockInsufficient() {
        when(pigInfoReadMapper.selectCurrentStatusByEarNo("TEST-EAR-001")).thenReturn("END");
        when(burnMapper.insert(any(PigBurnRecord.class))).thenAnswer(inv -> {
            PigBurnRecord e = inv.getArgument(0);
            e.setId(60002L);
            return 1;
        });
        when(stockMapper.deductByEarNo(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.submitBurnRecord(sampleBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("白条库存不足");

        verify(flowMapper, never()).insert(any(StockFlow.class));
        // 注意：burnMapper.insert 已被调用（事务回滚由 Spring 在集成测试覆盖）；单测只验证"流水未触发"
        verify(burnMapper, times(1)).insert(any(PigBurnRecord.class));
    }

    @Test
    @DisplayName("submitBurnRecord: 耳号未出栏 → current_status='HB' 抛 ServiceException + 任何 mapper 不调")
    void testSubmit_PigNotEnd() {
        when(pigInfoReadMapper.selectCurrentStatusByEarNo("TEST-EAR-001")).thenReturn("HB");

        assertThatThrownBy(() -> service.submitBurnRecord(sampleBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("猪只未出栏");

        verify(burnMapper, never()).insert(any(PigBurnRecord.class));
        verify(stockMapper, never()).deductByEarNo(any(), any(), any(), any());
        verify(flowMapper, never()).insert(any(StockFlow.class));
    }

    @Test
    @DisplayName("submitBurnRecord: 耳号不存在 → mapper 返 null 抛 ServiceException")
    void testSubmit_PigNotFound() {
        when(pigInfoReadMapper.selectCurrentStatusByEarNo("TEST-EAR-001")).thenReturn(null);

        assertThatThrownBy(() -> service.submitBurnRecord(sampleBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("耳号未找到");

        verify(burnMapper, never()).insert(any(PigBurnRecord.class));
    }

    @Test
    @DisplayName("submitBurnRecord: 燎毛后重量 > 到场重量 → 抛 不能大于到场重量")
    void testSubmit_BurnHeavierThanArrive() {
        PigBurnRecordBo bo = sampleBo();
        bo.setArriveWeight(new BigDecimal("70.000"));
        bo.setBurnWeight(new BigDecimal("75.300"));
        when(pigInfoReadMapper.selectCurrentStatusByEarNo("TEST-EAR-001")).thenReturn("END");

        assertThatThrownBy(() -> service.submitBurnRecord(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能大于到场重量");

        verify(burnMapper, never()).insert(any(PigBurnRecord.class));
        verify(stockMapper, never()).deductByEarNo(any(), any(), any(), any());
    }

}
