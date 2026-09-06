package org.dromara.djs.warehouse.flow.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.service.DictService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.flow.constant.FlowDisplayScope;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatInVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatOutVo;
import org.dromara.djs.warehouse.flow.mapper.InoutStatMapper;
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
 * {@link InoutStatServiceImpl} 单测（V6-R167 出入库统计）。
 *
 * <ol>
 *   <li>入库统计分页 happy：字典 label 回填 + 空供应商兜「无供应商」+ 空规格兜 "-"</li>
 *   <li>入库统计导出：与列表走同一套加工，逐列一致（甲方拿导出核对页面）</li>
 *   <li>出库统计：空出库去向兜「未指定」；排除清单与出库记录页同口径</li>
 *   <li>query 为 null 不 NPE（mapper 的 &lt;if&gt; 撞 null 会 OGNL 异常）</li>
 * </ol>
 *
 * @author djs
 * @since V6-R167
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InoutStatServiceImpl 单元测试（V6-R167）")
class InoutStatServiceImplTest {

    @Mock
    private InoutStatMapper inoutStatMapper;

    @Mock
    private DictService dictService;

    @InjectMocks
    private InoutStatServiceImpl service;

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

    /** mapper 出参样板：无供应商 + 无规格的那一桶（分组键 COALESCE 出来是空串，不是 null）。 */
    private static InoutStatInVo inRow() {
        InoutStatInVo row = new InoutStatInVo();
        row.setProductName("上海青");
        row.setProductType(2);
        row.setProductSpec("");
        row.setProductUnit("kg");
        row.setFlowType("purchase_in");
        row.setSupplierName("");
        row.setInboundQty(new BigDecimal("12.500"));
        return row;
    }

    private static PageQuery pageQuery() {
        return new PageQuery(10, 1);
    }

    @Test
    @DisplayName("queryInPage: 字典回填 + 空供应商兜「无供应商」+ 空规格兜 -；排除清单与入库记录页同口径")
    void testQueryInPage_HappyPathAndFallbacks() {
        Page<InoutStatInVo> page = new Page<>(1, 10);
        page.setRecords(List.of(inRow()));
        page.setTotal(1);

        when(inoutStatMapper.selectInStatPage(any(), eq("1001"), any(InoutStatQuery.class), any()))
            .thenReturn(page);
        when(dictService.getDictLabel("djs_product_type", "2")).thenReturn("外购");
        when(dictService.getDictLabel("djs_flow_type", "purchase_in")).thenReturn("采购入库");

        TableDataInfo<InoutStatInVo> result = service.queryInPage(new InoutStatQuery(), pageQuery());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        InoutStatInVo vo = result.getRows().get(0);
        assertThat(vo.getProductTypeName()).isEqualTo("外购");
        assertThat(vo.getInModeName()).isEqualTo("采购入库");
        assertThat(vo.getSupplierName()).as("甲方「供应商为空的统计到一起」那一行显示成「无供应商」，不是空白")
            .isEqualTo("无供应商");
        assertThat(vo.getProductSpec()).isEqualTo("-");
        assertThat(vo.getProductUnit()).as("单位是分组键的一部分，必须原样保留").isEqualTo("kg");
        assertThat(vo.getInboundQty()).isEqualByComparingTo("12.500");

        // 入库统计口径必须与「入库记录」页一致（都排 pack_in），否则甲方拿统计对明细会重报
        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(inoutStatMapper).selectInStatPage(any(IPage.class), eq("1001"), any(InoutStatQuery.class), cap.capture());
        assertThat(cap.getValue()).isEqualTo(FlowDisplayScope.IN_EXCLUDED);
    }

    @Test
    @DisplayName("queryInList（导出）: 与列表同一套字典翻译与兜底，逐列一致")
    void testQueryInList_DecoratedLikeThePage() {
        when(inoutStatMapper.selectInStatList(eq("1001"), any(InoutStatQuery.class), any()))
            .thenReturn(List.of(inRow()));
        when(dictService.getDictLabel("djs_product_type", "2")).thenReturn("外购");
        when(dictService.getDictLabel("djs_flow_type", "purchase_in")).thenReturn("采购入库");

        List<InoutStatInVo> list = service.queryInList(new InoutStatQuery());

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getProductTypeName()).isEqualTo("外购");
        assertThat(list.get(0).getInModeName()).isEqualTo("采购入库");
        assertThat(list.get(0).getSupplierName()).isEqualTo("无供应商");
        assertThat(list.get(0).getProductSpec()).isEqualTo("-");
    }

    @Test
    @DisplayName("queryOutPage: 空出库去向兜「未指定」+ 排除清单与出库记录页同口径")
    void testQueryOutPage_EmptyDestFallback() {
        InoutStatOutVo row = new InoutStatOutVo();
        row.setProductName("五花肉");
        row.setProductType(1);
        row.setProductSpec("500g/份");
        row.setProductUnit("kg");
        row.setStockOutDest("");
        row.setOutboundQty(new BigDecimal("3.000"));

        Page<InoutStatOutVo> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);

        when(inoutStatMapper.selectOutStatPage(any(), eq("1001"), any(InoutStatQuery.class), any()))
            .thenReturn(page);
        when(dictService.getDictLabel("djs_product_type", "1")).thenReturn("自产");

        TableDataInfo<InoutStatOutVo> result = service.queryOutPage(new InoutStatQuery(), pageQuery());

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getOutDestName()).isEqualTo("未指定");
        assertThat(result.getRows().get(0).getProductTypeName()).isEqualTo("自产");
        assertThat(result.getRows().get(0).getProductSpec()).isEqualTo("500g/份");

        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(inoutStatMapper).selectOutStatPage(any(IPage.class), eq("1001"), any(InoutStatQuery.class), cap.capture());
        assertThat(cap.getValue()).isEqualTo(FlowDisplayScope.OUT_EXCLUDED);
    }

    @Test
    @DisplayName("query 为 null 时兜空对象下传，mapper 的 <if> 不会撞 null")
    void testNullQueryIsReplacedWithEmptyOne() {
        when(inoutStatMapper.selectOutStatList(eq("1001"), any(InoutStatQuery.class), any()))
            .thenReturn(List.of());

        assertThat(service.queryOutList(null)).isEmpty();

        ArgumentCaptor<InoutStatQuery> cap = ArgumentCaptor.forClass(InoutStatQuery.class);
        verify(inoutStatMapper).selectOutStatList(eq("1001"), cap.capture(), any());
        assertThat(cap.getValue()).isNotNull();
        assertThat(cap.getValue().getDateFrom()).isNull();
        assertThat(cap.getValue().getDateTo()).isNull();
    }
}
