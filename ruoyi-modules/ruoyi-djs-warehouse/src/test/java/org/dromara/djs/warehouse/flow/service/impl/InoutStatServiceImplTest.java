package org.dromara.djs.warehouse.flow.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.service.DictService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.flow.constant.FlowDisplayScope;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatDetailQuery;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatInDetailVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatInVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatOutDetailVo;
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
 *   <li>行内「查看详情」明细（V6-R186）：量按单位拼串（kg 三位小数 / 计件带单位）、
 *       空供应商与空出库去向套与汇总行<b>同一份</b>兜底文案、记录人查不到兜 "-"，
 *       且明细导出与弹窗走同一套加工</li>
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

    /** 入库明细 mapper 出参样板：无供应商 + 无规格 + 记录人档案已删（联不出名字）。 */
    private static InoutStatInDetailVo inDetailRow() {
        InoutStatInDetailVo row = new InoutStatInDetailVo();
        row.setProductCode("Y00001");
        row.setProductName("上海青");
        row.setProductSpec("");
        row.setProductUnit("kg");
        row.setInboundQty(new BigDecimal("12.5"));
        row.setSupplierName("");
        row.setOperatorName(null);
        return row;
    }

    @Test
    @DisplayName("queryInDetailPage: kg 量拼三位小数 + 兜「无供应商」/「-」；排除清单与汇总同一份")
    void testQueryInDetailPage_LabelsAndFallbacks() {
        Page<InoutStatInDetailVo> page = new Page<>(1, 10);
        page.setRecords(List.of(inDetailRow()));
        page.setTotal(1);

        when(inoutStatMapper.selectInDetailPage(any(), eq("1001"), any(InoutStatDetailQuery.class), any()))
            .thenReturn(page);

        InoutStatDetailQuery query = new InoutStatDetailQuery();
        query.setProductCode("Y00001");
        query.setFlowType("purchase_in");
        TableDataInfo<InoutStatInDetailVo> result = service.queryInDetailPage(query, pageQuery());

        assertThat(result.getTotal()).isEqualTo(1);
        InoutStatInDetailVo vo = result.getRows().get(0);
        assertThat(vo.getProductCode()).as("产品编码是明细的身份列，原样透出").isEqualTo("Y00001");
        assertThat(vo.getInQtyLabel()).as("kg 恒 3 位小数并带单位，页面与 xlsx 读同一个字段").isEqualTo("12.500kg");
        assertThat(vo.getSupplierName()).as("与所属汇总行的供应商列一字不差，否则甲方以为点错了行")
            .isEqualTo("无供应商");
        assertThat(vo.getProductSpec()).isEqualTo("-");
        assertThat(vo.getOperatorName()).as("用户档案已删时联不出名字，兜 - 而不是留空").isEqualTo("-");

        // 明细行集必须与汇总同集合，排除清单只能是同一份
        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(inoutStatMapper).selectInDetailPage(any(IPage.class), eq("1001"),
            any(InoutStatDetailQuery.class), cap.capture());
        assertThat(cap.getValue()).isEqualTo(FlowDisplayScope.IN_EXCLUDED);
    }

    @Test
    @DisplayName("queryInDetailList（明细导出）: 与弹窗同一套加工，逐列一致")
    void testQueryInDetailList_DecoratedLikeTheDialog() {
        when(inoutStatMapper.selectInDetailList(eq("1001"), any(InoutStatDetailQuery.class), any()))
            .thenReturn(List.of(inDetailRow()));

        List<InoutStatInDetailVo> list = service.queryInDetailList(new InoutStatDetailQuery());

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getInQtyLabel()).isEqualTo("12.500kg");
        assertThat(list.get(0).getSupplierName()).isEqualTo("无供应商");
        assertThat(list.get(0).getOperatorName()).isEqualTo("-");
    }

    @Test
    @DisplayName("queryOutDetailPage: 计件单位按「数值 空格 单位」拼；空去向兜「未指定」")
    void testQueryOutDetailPage_CountingUnitAndDestFallback() {
        InoutStatOutDetailVo row = new InoutStatOutDetailVo();
        row.setProductCode("S0339");
        row.setProductName("丝瓜");
        row.setProductSpec("10克/袋");
        row.setProductUnit("袋");
        row.setOutboundQty(new BigDecimal("10.000"));
        row.setStockOutDest("");
        row.setOperatorName("张三");

        Page<InoutStatOutDetailVo> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);

        when(inoutStatMapper.selectOutDetailPage(any(), eq("1001"), any(InoutStatDetailQuery.class), any()))
            .thenReturn(page);

        TableDataInfo<InoutStatOutDetailVo> result = service.queryOutDetailPage(new InoutStatDetailQuery(), pageQuery());

        InoutStatOutDetailVo vo = result.getRows().get(0);
        assertThat(vo.getOutQtyLabel()).as("计件单位不能按 kg 印成 10.000kg").isEqualTo("10 袋");
        assertThat(vo.getOutDestName()).isEqualTo("未指定");
        assertThat(vo.getOperatorName()).isEqualTo("张三");

        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(inoutStatMapper).selectOutDetailPage(any(IPage.class), eq("1001"),
            any(InoutStatDetailQuery.class), cap.capture());
        assertThat(cap.getValue()).isEqualTo(FlowDisplayScope.OUT_EXCLUDED);
    }

    @Test
    @DisplayName("queryOutDetailList（明细导出）: 出库去向翻字典，与弹窗同一份文案")
    void testQueryOutDetailList_TranslatesDest() {
        InoutStatOutDetailVo row = new InoutStatOutDetailVo();
        row.setProductCode("Y00101");
        row.setProductName("五花肉");
        row.setProductSpec("500g/份");
        row.setProductUnit("kg");
        row.setOutboundQty(new BigDecimal("7"));
        row.setStockOutDest("dept_pick");
        row.setOperatorName("李四");

        when(inoutStatMapper.selectOutDetailList(eq("1001"), any(InoutStatDetailQuery.class), any()))
            .thenReturn(List.of(row));
        when(dictService.getDictLabel("djs_stock_out_dest", "dept_pick")).thenReturn("部门领用");

        List<InoutStatOutDetailVo> list = service.queryOutDetailList(new InoutStatDetailQuery());

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getOutDestName()).isEqualTo("部门领用");
        assertThat(list.get(0).getOutQtyLabel()).isEqualTo("7.000kg");
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
