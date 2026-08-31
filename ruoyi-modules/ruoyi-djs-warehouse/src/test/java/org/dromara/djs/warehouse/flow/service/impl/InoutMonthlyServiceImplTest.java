package org.dromara.djs.warehouse.flow.service.impl;

import org.dromara.common.core.service.DictService;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.flow.constant.FlowDisplayScope;
import org.dromara.djs.warehouse.flow.domain.query.InoutSummaryQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutMonthVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutSummaryInVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutSummaryOutVo;
import org.dromara.djs.warehouse.flow.mapper.InoutMonthlyMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InoutMonthlyServiceImpl} 单测（V6-R154 / R155 / R156 出入库月汇总）。
 *
 * <ol>
 *   <li>入库汇总 happy：字典 label 回填 + 空供应商兜「无供应商」+ 空规格兜 "-"</li>
 *   <li>入库汇总：mapper 分组键 COALESCE 出来的空串（不是 null）也要走同一套兜底</li>
 *   <li>出库汇总：空出库去向兜「未指定」</li>
 *   <li>月份列表：透传的排除清单必须是 FlowDisplayScope 那两份（与入/出库记录页同口径）；mapper 返 null → 空 List</li>
 * </ol>
 *
 * @author djs
 * @since V6-R154
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InoutMonthlyServiceImpl 单元测试（V6-R154/R155/R156）")
class InoutMonthlyServiceImplTest {

    @Mock
    private InoutMonthlyMapper inoutMonthlyMapper;

    @Mock
    private DictService dictService;

    @InjectMocks
    private InoutMonthlyServiceImpl service;

    /** TenantHelper 是静态工具且依赖 Spring 上下文，单测里固定成 V1 租户 '1001'。 */
    private MockedStatic<TenantHelper> tenantHelper;

    @BeforeEach
    void setUp() {
        tenantHelper = Mockito.mockStatic(TenantHelper.class);
        tenantHelper.when(TenantHelper::getTenantId).thenReturn("1001");
    }

    @AfterEach
    void tearDown() {
        tenantHelper.close();
    }

    @Test
    @DisplayName("queryInSummary: 字典回填 + 空供应商兜「无供应商」+ 空规格兜 -")
    void testQueryInSummary_HappyPathAndFallbacks() {
        InoutSummaryInVo row = new InoutSummaryInVo();
        row.setProductName("上海青");
        row.setProductType(2);
        row.setProductSpec(null);
        row.setProductUnit("kg");
        row.setFlowType("purchase_in");
        row.setSupplierName(null);
        row.setInboundQty(new BigDecimal("12.500"));

        when(inoutMonthlyMapper.selectInSummary(eq("1001"), any(InoutSummaryQuery.class), any()))
            .thenReturn(List.of(row));
        when(dictService.getDictLabel("djs_product_type", "2")).thenReturn("外购");
        when(dictService.getDictLabel("djs_flow_type", "purchase_in")).thenReturn("采购入库");

        InoutSummaryQuery query = new InoutSummaryQuery();
        query.setStatMonth("2026-08");
        List<InoutSummaryInVo> result = service.queryInSummary(query);

        assertThat(result).hasSize(1);
        InoutSummaryInVo vo = result.get(0);
        assertThat(vo.getProductTypeName()).isEqualTo("外购");
        assertThat(vo.getInModeName()).isEqualTo("采购入库");
        assertThat(vo.getSupplierName()).as("甲方「供应商为空的统计到一起」那一行显示成「无供应商」，不是空白")
            .isEqualTo("无供应商");
        assertThat(vo.getProductSpec()).isEqualTo("-");
        assertThat(vo.getInboundQty()).isEqualByComparingTo("12.500");

        // 入库汇总口径必须与「入库记录」页一致（都排 pack_in），否则甲方拿汇总对明细会重报
        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(inoutMonthlyMapper).selectInSummary(eq("1001"), any(InoutSummaryQuery.class), cap.capture());
        assertThat(cap.getValue()).isEqualTo(FlowDisplayScope.IN_EXCLUDED);
    }

    @Test
    @DisplayName("queryInSummary: 分组键 COALESCE 出来的空串同样兜底（'' → 无供应商 / -）")
    void testQueryInSummary_BlankFromCoalesceStillFallsBack() {
        // mapper 的分组键是 COALESCE(pi.product_spec,'') / COALESCE(sp.supplier_name,'')，
        // 无供应商与无规格的桶回来是空串而不是 null，兜底必须照样触发
        InoutSummaryInVo row = new InoutSummaryInVo();
        row.setProductName("大米10斤");
        row.setProductType(1);
        row.setProductSpec("");
        row.setProductUnit("袋");
        row.setFlowType("other");
        row.setSupplierName("");
        row.setInboundQty(new BigDecimal("30.000"));

        when(inoutMonthlyMapper.selectInSummary(eq("1001"), any(InoutSummaryQuery.class), any()))
            .thenReturn(List.of(row));
        when(dictService.getDictLabel("djs_product_type", "1")).thenReturn("自产");
        when(dictService.getDictLabel("djs_flow_type", "other")).thenReturn("其他");

        InoutSummaryQuery query = new InoutSummaryQuery();
        query.setStatMonth("2099-01");
        List<InoutSummaryInVo> result = service.queryInSummary(query);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSupplierName()).isEqualTo("无供应商");
        assertThat(result.get(0).getProductSpec()).isEqualTo("-");
        assertThat(result.get(0).getProductUnit()).as("单位是分组键的一部分，必须原样保留").isEqualTo("袋");
    }

    @Test
    @DisplayName("queryOutSummary: 空出库去向兜「未指定」+ 排除清单与出库记录页同口径")
    void testQueryOutSummary_EmptyDestFallback() {
        InoutSummaryOutVo row = new InoutSummaryOutVo();
        row.setProductName("五花肉");
        row.setProductType(1);
        row.setProductSpec("500g/份");
        row.setProductUnit("kg");
        row.setStockOutDest("");
        row.setOutboundQty(new BigDecimal("3.000"));

        when(inoutMonthlyMapper.selectOutSummary(eq("1001"), any(InoutSummaryQuery.class), any()))
            .thenReturn(List.of(row));
        when(dictService.getDictLabel("djs_product_type", "1")).thenReturn("自产");

        InoutSummaryQuery query = new InoutSummaryQuery();
        query.setStatMonth("2026-08");
        List<InoutSummaryOutVo> result = service.queryOutSummary(query);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOutDestName()).isEqualTo("未指定");
        assertThat(result.get(0).getProductTypeName()).isEqualTo("自产");
        assertThat(result.get(0).getProductSpec()).isEqualTo("500g/份");

        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(inoutMonthlyMapper).selectOutSummary(eq("1001"), any(InoutSummaryQuery.class), cap.capture());
        assertThat(cap.getValue()).isEqualTo(FlowDisplayScope.OUT_EXCLUDED);
    }

    @Test
    @DisplayName("queryMonths: 透传两份排除清单；mapper 返 null → 空 List 不 NPE")
    void testQueryMonths_ScopePassThroughAndNullSafe() {
        InoutMonthVo m = new InoutMonthVo();
        m.setStatMonth("2026-08");
        when(inoutMonthlyMapper.selectMonths(eq("1001"), any(), any(), any())).thenReturn(List.of(m));

        assertThat(service.queryMonths("2026-08")).extracting(InoutMonthVo::getStatMonth).containsExactly("2026-08");

        ArgumentCaptor<List<String>> inCap = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> outCap = ArgumentCaptor.forClass(List.class);
        verify(inoutMonthlyMapper).selectMonths(eq("1001"), eq("2026-08"), inCap.capture(), outCap.capture());
        assertThat(inCap.getValue()).isEqualTo(FlowDisplayScope.IN_EXCLUDED);
        assertThat(outCap.getValue()).isEqualTo(FlowDisplayScope.OUT_EXCLUDED);

        when(inoutMonthlyMapper.selectMonths(eq("1001"), any(), any(), any())).thenReturn(null);
        assertThat(service.queryMonths(null)).isEmpty();
    }
}
