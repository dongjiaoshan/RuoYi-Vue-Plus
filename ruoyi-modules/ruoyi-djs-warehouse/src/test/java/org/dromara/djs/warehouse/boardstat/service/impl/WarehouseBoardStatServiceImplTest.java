package org.dromara.djs.warehouse.boardstat.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.boardstat.domain.vo.BoardStatDetailVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.BoardStatUnitTotalVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.CategoryStatVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.CategoryUnitQtyRow;
import org.dromara.djs.warehouse.boardstat.domain.vo.CategoryUnitStatVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.InboundDetailRowVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.ProductionDetailRowVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.WarehouseBoardStatVo;
import org.dromara.djs.warehouse.boardstat.mapper.WarehouseBoardStatMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WarehouseBoardStatServiceImpl} 单测（V6-R178）。
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>happy：三指标各有数 → 4 张卡固定输出、猪肉卡合并 pork + white_bar、多单位多行且行序稳定；</li>
 *   <li>环比：上月有数算百分比，上月无数据 / 为 0 → ratio 为 null（前端据此显黑色 0.00%）；</li>
 *   <li>全空兜底：mapper 全返空 → 仍出 4 张卡、rows 为空、不抛 NPE。</li>
 * </ol>
 *
 * <p>service 不用 LambdaWrapper（纯 Mapper 注解 SQL），无需 entity cache 预热。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WarehouseBoardStatServiceImpl 单元测试")
class WarehouseBoardStatServiceImplTest {

    private static final LocalDate CUR_FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate CUR_TO = LocalDate.of(2026, 10, 1);
    private static final LocalDate PRE_FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate PRE_TO = LocalDate.of(2026, 9, 1);

    @Mock
    private WarehouseBoardStatMapper boardStatMapper;

    @Mock
    private DictService dictService;

    private WarehouseBoardStatServiceImpl service;

    private MockedStatic<TenantHelper> tenantHelperMock;

    @BeforeEach
    void setUp() {
        service = new WarehouseBoardStatServiceImpl(boardStatMapper, dictService);
        tenantHelperMock = Mockito.mockStatic(TenantHelper.class);
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn("1001");
        // 缺省全空：各用例只 stub 自己关心的那几次调用
        when(boardStatMapper.selectInboundByCategoryUnit(anyString(), anyList(), anyList(), any(), any()))
            .thenReturn(List.of());
        when(boardStatMapper.selectProduceByCategoryUnit(anyString(), anyList(), any(), any()))
            .thenReturn(List.of());
        when(boardStatMapper.selectMaterialConsumeByCategoryUnit(anyString(), anyList(), any(), any()))
            .thenReturn(List.of());
        when(dictService.getDictLabel(eq("djs_flow_type"), anyString())).thenReturn("采购入库");
    }

    @AfterEach
    void tearDown() {
        if (tenantHelperMock != null) {
            tenantHelperMock.close();
        }
    }

    @Test
    @DisplayName("happy：4 张卡固定输出，猪肉卡合并 pork + white_bar，多单位分行且行序稳定")
    void getCategoryStat_happy() {
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(
                row("pork", "kg", "800.000"),
                row("white_bar", "kg", "200.000"),
                row("egg", "枚", "7560")));
        when(boardStatMapper.selectProduceByCategoryUnit(eq("1001"), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(
                row("pork", "份", "39"),
                row("pork", "kg", "32.300")));

        WarehouseBoardStatVo vo = service.getCategoryStat("2026-09");

        assertThat(vo.getMonth()).isEqualTo("2026-09");
        assertThat(vo.getPrevMonth()).isEqualTo("2026-08");
        assertThat(vo.getCategories()).extracting(CategoryStatVo::getCategoryKey)
            .containsExactly("pork", "vegetable", "egg", "dry_good");
        assertThat(vo.getCategories()).extracting(CategoryStatVo::getCategoryName)
            .containsExactly("猪肉产品", "果蔬产品", "蛋类产品", "干货产品");

        CategoryStatVo porkCard = vo.getCategories().get(0);
        // 单位名升序：'k' < '份'（CJK 码位在 ASCII 之后）
        assertThat(porkCard.getRows()).extracting(CategoryUnitStatVo::getUnit).containsExactly("kg", "份");
        // pork 800 + white_bar 200 合到猪肉卡 kg 行
        assertThat(porkCard.getRows().get(0).getInboundQty()).isEqualByComparingTo("1000.000");
        assertThat(porkCard.getRows().get(0).getProduceQty()).isEqualByComparingTo("32.300");
        assertThat(porkCard.getRows().get(1).getInboundQty()).isEqualByComparingTo("0");
        assertThat(porkCard.getRows().get(1).getProduceQty()).isEqualByComparingTo("39");

        CategoryStatVo eggCard = vo.getCategories().get(2);
        assertThat(eggCard.getRows()).hasSize(1);
        assertThat(eggCard.getRows().get(0).getUnit()).isEqualTo("枚");
        assertThat(eggCard.getRows().get(0).getInboundQty()).isEqualByComparingTo("7560");

        // 果蔬 / 干货本月无数据 → 空行集，卡片仍在
        assertThat(vo.getCategories().get(1).getRows()).isEmpty();
        assertThat(vo.getCategories().get(3).getRows()).isEmpty();
    }

    @Test
    @DisplayName("环比：上月有数算百分比；上月无数据 / 为 0 → null（前端显黑色 0.00%）")
    void getCategoryStat_ratio() {
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("vegetable", "kg", "130"), row("egg", "枚", "50")));
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(PRE_FROM), eq(PRE_TO)))
            .thenReturn(List.of(row("vegetable", "kg", "100"), row("egg", "枚", "0")));

        WarehouseBoardStatVo vo = service.getCategoryStat("2026-09");

        CategoryUnitStatVo vegRow = vo.getCategories().get(1).getRows().get(0);
        assertThat(vegRow.getInboundRatio()).isEqualByComparingTo("30.00");
        // 本月生产量 0、上月也 0 → 无从算环比
        assertThat(vegRow.getProduceRatio()).isNull();

        // 上月该单位为 0 == 没有上个月数据
        CategoryUnitStatVo eggRow = vo.getCategories().get(2).getRows().get(0);
        assertThat(eggRow.getInboundRatio()).isNull();
    }

    @Test
    @DisplayName("全空兜底：mapper 全返空 → 仍出 4 张卡、rows 为空、不抛 NPE")
    void getCategoryStat_empty() {
        WarehouseBoardStatVo vo = service.getCategoryStat(null);

        assertThat(vo.getMonth()).isNotBlank();
        assertThat(vo.getPrevMonth()).isNotBlank();
        assertThat(vo.getCategories()).hasSize(4);
        assertThat(vo.getCategories()).allSatisfy(c -> assertThat(c.getRows()).isEmpty());
    }

    @Test
    @DisplayName("月份入参非法 → 回落当月，不抛异常")
    void getCategoryStat_badMonth() {
        WarehouseBoardStatVo vo = service.getCategoryStat("2026/13");

        assertThat(vo.getCategories()).hasSize(4);
        assertThat(vo.getMonth()).matches("\\d{4}-\\d{2}");
    }

    // ==================== 下钻明细（入库明细 / 生产明细）====================

    @Test
    @DisplayName("入库明细：totals 与卡片同源（按单位合并 pork + white_bar），分页参数原样透传")
    void getInboundDetail_totalsAndPaging() {
        // 明细行：一页两条
        when(boardStatMapper.selectInboundDetailPage(any(), eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(pageOf(List.of(
                inboundRow("2026-09-12T08:30:00", "白条", "kg", "120.500", "purchase_in"),
                inboundRow("2026-09-03T09:00:00", "鲜鸡蛋", null, "60", "supplier_in")), 2L));
        // 合计源 = 卡片那条聚合 SQL：pork 800 + white_bar 200 应合成 kg 1000
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(
                row("pork", "kg", "800.000"),
                row("white_bar", "kg", "200.000"),
                row("pork", "份", "12")));

        BoardStatDetailVo<InboundDetailRowVo> vo =
            service.getInboundDetail("2026-09", "pork", new PageQuery(10, 2));

        assertThat(vo.getMonth()).isEqualTo("2026-09");
        assertThat(vo.getBelongType()).isEqualTo("pork");
        assertThat(vo.getCategoryName()).isEqualTo("猪肉产品");
        assertThat(vo.getTotal()).isEqualTo(2L);
        assertThat(vo.getRows()).hasSize(2);
        // 单位名升序，且同单位跨 belong_type 合并
        assertThat(vo.getTotals()).extracting(BoardStatUnitTotalVo::getUnit).containsExactly("kg", "份");
        assertThat(vo.getTotals().get(0).getQty()).isEqualByComparingTo("1000.000");
        assertThat(vo.getTotals().get(1).getQty()).isEqualByComparingTo("12");

        // 猪肉卡把 pork + white_bar 一起传给 SQL（明细与卡片同集合的前提）
        ArgumentCaptor<List<String>> belongCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<IPage<InboundDetailRowVo>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        verify(boardStatMapper).selectInboundDetailPage(
            pageCaptor.capture(), eq("1001"), belongCaptor.capture(), anyList(), eq(CUR_FROM), eq(CUR_TO));
        assertThat(belongCaptor.getValue()).containsExactly("pork", "white_bar");
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10L);

        // 空值兜底：规格 / 供应商 / 库位空 → 占位符；入库方式翻成字典 label
        InboundDetailRowVo second = vo.getRows().get(1);
        assertThat(second.getProductSpec()).isEqualTo("—");
        assertThat(second.getSupplierName()).isEqualTo("—");
        assertThat(second.getLocationName()).isEqualTo("—");
        assertThat(second.getInModeName()).isEqualTo("采购入库");
    }

    @Test
    @DisplayName("生产明细：totals 取卡片生产量聚合，单位缺失归一成「未标单位」，原材料名空兜占位")
    void getProductionDetail_totals() {
        when(boardStatMapper.selectProduceDetailPage(any(), eq("1001"), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(pageOf(List.of(
                produceRow("2026-09-08", "五花肉", "kg", "3.250", "2.000", "白条"),
                produceRow("2026-09-01", "礼盒", null, "1", null, null)), 2L));
        when(boardStatMapper.selectProduceByCategoryUnit(eq("1001"), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("vegetable", "kg", "3350.000")));

        BoardStatDetailVo<ProductionDetailRowVo> vo =
            service.getProductionDetail("2026-09", "vegetable", new PageQuery(20, 1));

        assertThat(vo.getCategoryName()).isEqualTo("果蔬产品");
        assertThat(vo.getTotals()).hasSize(1);
        assertThat(vo.getTotals().get(0).getUnit()).isEqualTo("kg");
        assertThat(vo.getTotals().get(0).getQty()).isEqualByComparingTo("3350.000");

        ProductionDetailRowVo second = vo.getRows().get(1);
        assertThat(second.getUnit()).isEqualTo("未标单位");
        assertThat(second.getMaterialName()).isEqualTo("—");
        assertThat(second.getMaterialConsume()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("belongType 白名单：非四张卡的值 / 空 → 400，不静默返空")
    void detail_rejectsUnknownBelongType() {
        PageQuery pq = new PageQuery(10, 1);

        assertThatThrownBy(() -> service.getInboundDetail("2026-09", "gift_box", pq))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("品类不合法");
        assertThatThrownBy(() -> service.getInboundDetail("2026-09", "white_bar", pq))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.getProductionDetail("2026-09", null, pq))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("明细月份格式非法 → 400（不像看板那样静默回落，否则拿去对卡片会对错月）")
    void detail_rejectsBadMonth() {
        assertThatThrownBy(() -> service.getInboundDetail("2026/09", "pork", new PageQuery(10, 1)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("月份格式不合法");
    }

    @Test
    @DisplayName("分页参数缺省：pageQuery 为 null → 第 1 页 20 条，不退化成 PageQuery 的「查全部」")
    void detail_defaultsPaging() {
        when(boardStatMapper.selectInboundDetailPage(any(), anyString(), anyList(), anyList(), any(), any()))
            .thenReturn(pageOf(List.of(), 0L));

        BoardStatDetailVo<InboundDetailRowVo> vo = service.getInboundDetail("2026-09", "egg", null);

        assertThat(vo.getRows()).isEmpty();
        assertThat(vo.getTotals()).isEmpty();
        ArgumentCaptor<IPage<InboundDetailRowVo>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        verify(boardStatMapper).selectInboundDetailPage(
            pageCaptor.capture(), anyString(), anyList(), anyList(), any(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
    }

    private static <T> IPage<T> pageOf(List<T> records, long total) {
        Page<T> page = new Page<>(1, 10, total);
        page.setRecords(records);
        return page;
    }

    private static InboundDetailRowVo inboundRow(String flowDate, String productName, String unit,
                                                 String qty, String flowType) {
        InboundDetailRowVo r = new InboundDetailRowVo();
        r.setFlowDate(LocalDateTime.parse(flowDate));
        r.setProductName(productName);
        r.setProductSpec("");
        r.setUnit(unit);
        r.setQty(new BigDecimal(qty));
        r.setFlowType(flowType);
        r.setSupplierName("");
        r.setLocationName("");
        return r;
    }

    private static ProductionDetailRowVo produceRow(String produceDate, String productName, String unit,
                                                    String qty, String materialConsume, String materialName) {
        ProductionDetailRowVo r = new ProductionDetailRowVo();
        r.setProduceDate(LocalDate.parse(produceDate));
        r.setProductName(productName);
        r.setProductSpec("");
        r.setUnit(unit);
        r.setQty(new BigDecimal(qty));
        r.setMaterialConsume(materialConsume == null ? null : new BigDecimal(materialConsume));
        r.setMaterialName(materialName);
        return r;
    }

    private static CategoryUnitQtyRow row(String belongType, String unit, String qty) {
        CategoryUnitQtyRow r = new CategoryUnitQtyRow();
        r.setBelongType(belongType);
        r.setProductUnit(unit);
        r.setQty(new BigDecimal(qty));
        return r;
    }
}
