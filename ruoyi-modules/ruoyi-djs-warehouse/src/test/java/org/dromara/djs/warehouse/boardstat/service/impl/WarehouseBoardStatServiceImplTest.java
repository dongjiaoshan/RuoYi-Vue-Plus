package org.dromara.djs.warehouse.boardstat.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.boardstat.domain.vo.BoardStatDetailVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.BoardStatProductRowVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.BoardStatUnitTotalVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.CategoryStatVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.CategoryUnitQtyRow;
import org.dromara.djs.warehouse.boardstat.domain.vo.CategoryUnitStatVo;
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
 * {@link WarehouseBoardStatServiceImpl} 单测（V6-R178 / R193）。
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>happy：三指标各有数 → 4 张卡固定输出、猪肉卡合并 pork + white_bar、多单位多行且行序稳定；</li>
 *   <li>环比：上月有数算百分比，上月无数据 / 为 0 → ratio 为 null（前端据此显黑色 0.00%）；</li>
 *   <li>全空兜底：mapper 全返空 → 仍出 4 张卡、rows 为空、不抛 NPE；</li>
 *   <li>明细弹窗（R193）：按产品聚合、逐产品环比、只列本月有量的产品、totals 与卡片同源；</li>
 *   <li>原材料消耗不含礼盒产线（甲方 2026-09-07），且该单位只有原材料消耗归零时行仍在（R194 兜底）。</li>
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

    private WarehouseBoardStatServiceImpl service;

    private MockedStatic<TenantHelper> tenantHelperMock;

    @BeforeEach
    void setUp() {
        service = new WarehouseBoardStatServiceImpl(boardStatMapper);
        tenantHelperMock = Mockito.mockStatic(TenantHelper.class);
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn("1001");
        // 缺省全空：各用例只 stub 自己关心的那几次调用
        when(boardStatMapper.selectInboundByCategoryUnit(anyString(), anyList(), anyList(), any(), any()))
            .thenReturn(List.of());
        when(boardStatMapper.selectProduceByCategoryUnit(anyString(), anyList(), any(), any()))
            .thenReturn(List.of());
        when(boardStatMapper.selectMaterialConsumeByCategoryUnit(anyString(), anyList(), any(), any()))
            .thenReturn(List.of());
        when(boardStatMapper.selectInboundDetailByProduct(anyString(), anyList(), anyList(), any(), any()))
            .thenReturn(List.of());
        when(boardStatMapper.selectProduceDetailByProduct(anyString(), anyList(), any(), any()))
            .thenReturn(List.of());
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
    @DisplayName("原材料消耗：礼盒生产消耗的猪肉原料不进猪肉卡（甲方 2026-09-07：礼盒生产的不进行统计）")
    void getCategoryStat_materialConsumeExcludesGiftProduce() {
        // 本月两条猪肉原料消耗：普通猪肉产品生产耗 8kg + 礼盒生产耗 5kg。
        // 排除动作发生在 SQL 里（WarehouseBoardStatMapper.EXCLUDE_GIFT_PRODUCE，口径由 SQL 契约测试锁住），
        // 故 mapper 只吐得出普通那 8kg；service 不得再对这个数做任何加工。
        when(boardStatMapper.selectMaterialConsumeByCategoryUnit(
            eq("1001"), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("pork", "kg", "8.000")));
        // 生产量不受这条排除影响：礼盒自己产的 3 盒仍按礼盒品类正常统计（四张卡里没有礼盒卡，故不显示），
        // 猪肉产品自己的生产量照常出数
        when(boardStatMapper.selectProduceByCategoryUnit(eq("1001"), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("pork", "kg", "6.000"), row("gift_box", "盒", "3")));
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("pork", "kg", "20.000")));

        WarehouseBoardStatVo vo = service.getCategoryStat("2026-09");

        CategoryStatVo porkCard = vo.getCategories().get(0);
        assertThat(porkCard.getRows()).hasSize(1);
        CategoryUnitStatVo kgRow = porkCard.getRows().get(0);
        assertThat(kgRow.getUnit()).isEqualTo("kg");
        // 只算普通产线那 8kg —— 礼盒那 5kg 在 SQL 侧就被剔掉了，不是 8 + 5 = 13
        assertThat(kgRow.getMaterialQty()).isEqualByComparingTo("8.000");
        assertThat(kgRow.getInboundQty()).isEqualByComparingTo("20.000");
        assertThat(kgRow.getProduceQty()).isEqualByComparingTo("6.000");

        // 礼盒不是四张卡之一，礼盒自身的量（无论哪个指标）都不会挂到任何一张卡上
        assertThat(vo.getCategories()).extracting(CategoryStatVo::getCategoryKey)
            .containsExactly("pork", "vegetable", "egg", "dry_good");
        assertThat(vo.getCategories()).allSatisfy(card ->
            assertThat(card.getRows()).extracting(CategoryUnitStatVo::getUnit).doesNotContain("盒"));

        // 三条 SQL 拿到的是同一份品类白名单：白名单里没有 gift_box，
        // 所以「礼盒当原材料被消耗」也不会另开一张卡
        ArgumentCaptor<List<String>> belongCaptor = ArgumentCaptor.forClass(List.class);
        verify(boardStatMapper).selectMaterialConsumeByCategoryUnit(
            eq("1001"), belongCaptor.capture(), eq(CUR_FROM), eq(CUR_TO));
        assertThat(belongCaptor.getValue())
            .containsExactly("pork", "white_bar", "vegetable", "egg", "dry_good")
            .doesNotContain("gift_box");
    }

    @Test
    @DisplayName("某单位只有原材料消耗归零：该行仍在且原材料列显 0，不整行消失（R194 只显示有数单位的兜底）")
    void getCategoryStat_rowKeptWhenOnlyMaterialIsZero() {
        // 礼盒排除后，猪肉 kg 行的原材料消耗有可能被清成 0；只要入库 / 生产还有数，这一行就得留着
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("pork", "kg", "20.000")));
        when(boardStatMapper.selectProduceByCategoryUnit(eq("1001"), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("pork", "kg", "6.000")));
        when(boardStatMapper.selectMaterialConsumeByCategoryUnit(
            eq("1001"), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of());
        // 上月这个单位是有原材料消耗的 → 环比按 0 对 5 算得出 -100%
        when(boardStatMapper.selectMaterialConsumeByCategoryUnit(
            eq("1001"), anyList(), eq(PRE_FROM), eq(PRE_TO)))
            .thenReturn(List.of(row("pork", "kg", "5.000")));

        WarehouseBoardStatVo vo = service.getCategoryStat("2026-09");

        CategoryUnitStatVo kgRow = vo.getCategories().get(0).getRows().get(0);
        assertThat(kgRow.getUnit()).isEqualTo("kg");
        assertThat(kgRow.getMaterialQty()).isEqualByComparingTo("0");
        assertThat(kgRow.getMaterialRatio()).isEqualByComparingTo("-100.00");
        assertThat(kgRow.getInboundQty()).isEqualByComparingTo("20.000");
    }

    @Test
    @DisplayName("月份入参非法 → 回落当月，不抛异常")
    void getCategoryStat_badMonth() {
        WarehouseBoardStatVo vo = service.getCategoryStat("2026/13");

        assertThat(vo.getCategories()).hasSize(4);
        assertThat(vo.getMonth()).matches("\\d{4}-\\d{2}");
    }

    // ============ 明细弹窗（V6-R193：按产品聚合 + 逐产品环比）============

    @Test
    @DisplayName("入库明细：按产品出行，逐产品对上月算环比；totals 取卡片聚合（pork + white_bar 按单位合并）")
    void getInboundDetail_rowsAndRatio() {
        when(boardStatMapper.selectInboundDetailByProduct(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(
                productRow("101", "五花肉", "500g/份", "2400", "份"),
                productRow("102", "里脊肉", "1kg/份", "1100", "份"),
                productRow("103", "排骨", "", "420", "份")));
        when(boardStatMapper.selectInboundDetailByProduct(
            eq("1001"), anyList(), anyList(), eq(PRE_FROM), eq(PRE_TO)))
            .thenReturn(List.of(
                productRow("101", "五花肉", "500g/份", "2000", "份"),
                // 102 上月为 0 → 算不出环比；103 上月压根没这个产品 → 同样 null
                productRow("102", "里脊肉", "1kg/份", "0", "份")));
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(
                row("pork", "kg", "800.000"),
                row("white_bar", "kg", "200.000"),
                row("pork", "份", "3920")));

        BoardStatDetailVo vo = service.getInboundDetail("2026-09", "pork");

        assertThat(vo.getMonth()).isEqualTo("2026-09");
        assertThat(vo.getPrevMonth()).isEqualTo("2026-08");
        assertThat(vo.getBelongType()).isEqualTo("pork");
        assertThat(vo.getCategoryName()).isEqualTo("猪肉产品");

        assertThat(vo.getRows()).extracting(BoardStatProductRowVo::getProductName)
            .containsExactly("五花肉", "里脊肉", "排骨");
        // (2400 - 2000) / 2000 = +20.00%
        assertThat(vo.getRows().get(0).getPrevQty()).isEqualByComparingTo("2000");
        assertThat(vo.getRows().get(0).getRatio()).isEqualByComparingTo("20.00");
        // 上月为 0 / 上月无此产品 → ratio 为 null（前端显黑色 0.00%），prevQty 恒 0 不为 null
        assertThat(vo.getRows().get(1).getRatio()).isNull();
        assertThat(vo.getRows().get(1).getPrevQty()).isEqualByComparingTo("0");
        assertThat(vo.getRows().get(2).getRatio()).isNull();
        assertThat(vo.getRows().get(2).getPrevQty()).isEqualByComparingTo("0");

        // 合计与卡片同源：同单位跨 belong_type 合并，单位名升序
        assertThat(vo.getTotals()).extracting(BoardStatUnitTotalVo::getUnit).containsExactly("kg", "份");
        assertThat(vo.getTotals().get(0).getQty()).isEqualByComparingTo("1000.000");
        assertThat(vo.getTotals().get(1).getQty()).isEqualByComparingTo("3920");

        // 猪肉卡把 pork + white_bar 一起传给 SQL（明细与卡片同集合的前提）
        ArgumentCaptor<List<String>> belongCaptor = ArgumentCaptor.forClass(List.class);
        verify(boardStatMapper).selectInboundDetailByProduct(
            eq("1001"), belongCaptor.capture(), anyList(), eq(CUR_FROM), eq(CUR_TO));
        assertThat(belongCaptor.getValue()).containsExactly("pork", "white_bar");
    }

    @Test
    @DisplayName("入库明细：上月有、本月没有的产品不补空行（甲方要的是「统计月的产品」）")
    void getInboundDetail_onlyCurrentMonthProducts() {
        when(boardStatMapper.selectInboundDetailByProduct(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(productRow("101", "五花肉", "500g/份", "10", "份")));
        when(boardStatMapper.selectInboundDetailByProduct(
            eq("1001"), anyList(), anyList(), eq(PRE_FROM), eq(PRE_TO)))
            .thenReturn(List.of(
                productRow("101", "五花肉", "500g/份", "8", "份"),
                productRow("999", "上月才有的肉", "", "50", "份")));

        BoardStatDetailVo vo = service.getInboundDetail("2026-09", "pork");

        assertThat(vo.getRows()).extracting(BoardStatProductRowVo::getProductId).containsExactly("101");
        assertThat(vo.getRows().get(0).getRatio()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("生产明细：单位缺失归一成「未标单位」并按此对齐上月；规格空保持空串（前端不渲染规格标签）")
    void getProductionDetail_normalizesUnitAndSpec() {
        when(boardStatMapper.selectProduceDetailByProduct(eq("1001"), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(
                productRow("201", "有机厚肉丝瓜", "500g/份", "79", "份"),
                productRow("202", "礼盒", null, "3", null)));
        when(boardStatMapper.selectProduceDetailByProduct(eq("1001"), anyList(), eq(PRE_FROM), eq(PRE_TO)))
            .thenReturn(List.of(productRow("202", "礼盒", null, "2", "")));
        when(boardStatMapper.selectProduceByCategoryUnit(eq("1001"), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("vegetable", "份", "82")));

        BoardStatDetailVo vo = service.getProductionDetail("2026-09", "vegetable");

        assertThat(vo.getCategoryName()).isEqualTo("果蔬产品");
        BoardStatProductRowVo boxed = vo.getRows().get(1);
        assertThat(boxed.getUnit()).isEqualTo("未标单位");
        assertThat(boxed.getProductSpec()).isEmpty();
        // 上月同产品单位同样空 → 归一后同键对上，(3 - 2) / 2 = +50.00%
        assertThat(boxed.getRatio()).isEqualByComparingTo("50.00");

        assertThat(vo.getTotals()).hasSize(1);
        assertThat(vo.getTotals().get(0).getUnit()).isEqualTo("份");
        assertThat(vo.getTotals().get(0).getQty()).isEqualByComparingTo("82");
    }

    @Test
    @DisplayName("belongType 白名单：非四张卡的值 / 空 → 400，不静默返空")
    void detail_rejectsUnknownBelongType() {
        assertThatThrownBy(() -> service.getInboundDetail("2026-09", "gift_box"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("品类不合法");
        assertThatThrownBy(() -> service.getInboundDetail("2026-09", "white_bar"))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.getProductionDetail("2026-09", null))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("明细月份格式非法 → 400（不像看板那样静默回落，否则拿去对卡片会对错月）")
    void detail_rejectsBadMonth() {
        assertThatThrownBy(() -> service.getInboundDetail("2026/09", "pork"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("月份格式不合法");
    }

    @Test
    @DisplayName("明细全空：mapper 返空 → rows / totals 皆空，不抛 NPE")
    void detail_empty() {
        BoardStatDetailVo vo = service.getInboundDetail("2026-09", "egg");

        assertThat(vo.getRows()).isEmpty();
        assertThat(vo.getTotals()).isEmpty();
        assertThat(vo.getCategoryName()).isEqualTo("蛋类产品");
    }

    private static BoardStatProductRowVo productRow(String productId, String productName, String spec,
                                                    String qty, String unit) {
        BoardStatProductRowVo r = new BoardStatProductRowVo();
        r.setProductId(productId);
        r.setProductName(productName);
        r.setProductSpec(spec);
        r.setQty(new BigDecimal(qty));
        r.setUnit(unit);
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
