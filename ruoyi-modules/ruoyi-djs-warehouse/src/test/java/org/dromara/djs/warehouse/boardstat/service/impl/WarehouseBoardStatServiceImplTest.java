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
 *   <li>happy：三指标各有数 → 有数的卡按固定序输出、猪肉卡合并 pork + white_bar、多单位多行且行序稳定；</li>
 *   <li>本月无数据的卡整张不返回、本月三指标全 0 的单位行也不下发（甲方 2026-09-08，卡级 + 行级同规则）；</li>
 *   <li>单位合并键小写归一：{@code Kg} 与 {@code kg} 归到同一行；</li>
 *   <li>「其他产品」卡按 belong_type='other' 出，与其余卡同一套口径（甲方 2026-09-08），排在干货之后；</li>
 *   <li>环比：上月有数算百分比，上月无数据 / 为 0 → ratio 为 null（前端据此显黑色 0.00%）；</li>
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
    @DisplayName("happy：有数的卡按固定序输出，猪肉卡合并 pork + white_bar，多单位分行且行序稳定")
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
        // 果蔬 / 干货 / 其他本月一点数都没有 → 整张卡不返回（甲方 2026-09-08）
        assertThat(vo.getCategories()).extracting(CategoryStatVo::getCategoryKey)
            .containsExactly("pork", "egg");
        assertThat(vo.getCategories()).extracting(CategoryStatVo::getCategoryName)
            .containsExactly("猪肉产品", "蛋类产品");

        CategoryStatVo porkCard = vo.getCategories().get(0);
        // 单位名升序：'k' < '份'（CJK 码位在 ASCII 之后）
        assertThat(porkCard.getRows()).extracting(CategoryUnitStatVo::getUnit).containsExactly("kg", "份");
        // pork 800 + white_bar 200 合到猪肉卡 kg 行
        assertThat(porkCard.getRows().get(0).getInboundQty()).isEqualByComparingTo("1000.000");
        assertThat(porkCard.getRows().get(0).getProduceQty()).isEqualByComparingTo("32.300");
        assertThat(porkCard.getRows().get(1).getInboundQty()).isEqualByComparingTo("0");
        assertThat(porkCard.getRows().get(1).getProduceQty()).isEqualByComparingTo("39");

        CategoryStatVo eggCard = vo.getCategories().get(1);
        assertThat(eggCard.getRows()).hasSize(1);
        assertThat(eggCard.getRows().get(0).getUnit()).isEqualTo("枚");
        assertThat(eggCard.getRows().get(0).getInboundQty()).isEqualByComparingTo("7560");
    }

    /**
     * 甲方 2026-09-08 row202：「产品没有数据时，不显示对应的内容，即，没有时，就不显示任何内容」。
     * 判据只看本月 —— 上月 500、本月归零的卡过去会剩一行 0 与 −100.00%，那正是甲方指的「还在显示」。
     */
    @Test
    @DisplayName("本月无数据的卡整张不返回：上月有数也不能让它活下来（row202）")
    void getCategoryStat_dropsCardsWithoutCurrentMonthData() {
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("vegetable", "kg", "401.000")));
        // 蛋类：上月 300 枚、本月 0 → 旧行为会出一张「0 枚 / −100.00%」的卡，新口径整张不出
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(PRE_FROM), eq(PRE_TO)))
            .thenReturn(List.of(row("egg", "枚", "300")));
        // 干货：本月 SQL 出了行但量是 0 → 同样算「没有数据」
        when(boardStatMapper.selectProduceByCategoryUnit(eq("1001"), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("dry_good", "kg", "0.000")));

        WarehouseBoardStatVo vo = service.getCategoryStat("2026-09");

        assertThat(vo.getCategories()).extracting(CategoryStatVo::getCategoryKey)
            .containsExactly("vegetable");
    }

    /**
     * D-0045 <b>行级</b>：某单位当月三指标全 0 → 该单位行不下发，上月有数也不救它。
     *
     * <p>只做卡级不做行级，就会出现甲方 row202 截图里那个形态：卡还在（因为别的单位有数），
     * 卡里却挂着一行「0 + −100.00%」。QA 实测未修前 2026-09 后端下发 7 条这样的全零行、
     * 屏幕上渲染出 6 个「0 −100.00%」格。</p>
     */
    @Test
    @DisplayName("行级：本月三指标全 0 的单位行不下发，上月有数也不救（row202 行级）")
    void getCategoryStat_dropsAllZeroUnitRows() {
        // 蛋类：本月「枚」有数、「份」没有；上月「份」有数 300 —— 旧行为会给「份」留一行 0 与 −100%
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("egg", "枚", "300.000")));
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(PRE_FROM), eq(PRE_TO)))
            .thenReturn(List.of(row("egg", "枚", "932.000"), row("egg", "份", "12")));

        WarehouseBoardStatVo vo = service.getCategoryStat("2026-09");

        CategoryStatVo eggCard = vo.getCategories().get(0);
        assertThat(eggCard.getCategoryKey()).isEqualTo("egg");
        // 只剩「枚」一行；「份」那一行连同它的 −100.00% 一起不下发
        assertThat(eggCard.getRows()).extracting(CategoryUnitStatVo::getUnit).containsExactly("枚");
        assertThat(eggCard.getRows().get(0).getInboundQty()).isEqualByComparingTo("300.000");
        // 留下的行照常算环比：(300-932)/932 = -67.81%
        assertThat(eggCard.getRows().get(0).getInboundRatio()).isEqualByComparingTo("-67.81");
    }

    /**
     * 单位合并键小写归一：产品档案里同一个单位大小写混录（{@code other} 品类实测有 {@code Kg}），
     * 不归一会把同一张卡裂成两行、两行的量还各只有一半。与门店侧
     * {@code StoreManageServiceImpl#sumByUnit} 同一把尺子。
     */
    @Test
    @DisplayName("单位归一：Kg 与 kg 合并成一行且量相加，展示确定性取全小写那个")
    void getCategoryStat_mergesUnitCaseInsensitively() {
        // 大写在前、小写在后：取字面不能是「先到先得」（那依赖 SQL 行序、卡片与弹窗会显示不同字面），
        // 规则是「有全小写就取全小写」，与门店侧 StoreManageServiceImpl#putLabel 同一条
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("other", "Kg", "10.000"), row("other", "kg", "5.000")));

        WarehouseBoardStatVo vo = service.getCategoryStat("2026-09");

        CategoryStatVo otherCard = vo.getCategories().get(0);
        assertThat(otherCard.getCategoryKey()).isEqualTo("other");
        // 一行不是两行，量 10 + 5 = 15（裂成两行的话会是 10 和 5 各一行）
        assertThat(otherCard.getRows()).hasSize(1);
        assertThat(otherCard.getRows().get(0).getUnit()).isEqualTo("kg");
        assertThat(otherCard.getRows().get(0).getInboundQty()).isEqualByComparingTo("15.000");
    }

    /**
     * 重量单位恒显示小写 `kg`（doc/12 §0），且卡片与明细弹窗字面一致。
     *
     * <p>`Kg` 只有 other 品类的两个产品在用，「优先取全小写」在这张卡里挑不出小写来，
     * 原样透传就会让同屏的猪肉卡显示 `kg`、其他产品卡显示 `Kg`。</p>
     */
    @Test
    @DisplayName("重量单位恒小写 kg：other 卡只有大写 Kg 也显示 kg，且入库明细弹窗字面一致")
    void getCategoryStat_kgLabelAlwaysLowercase() {
        // pork 用小写 kg 且排在前面；other 自己只有大写 Kg
        List<CategoryUnitQtyRow> inbound = List.of(
            row("pork", "kg", "100.000"), row("other", "Kg", "23.800"));
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(inbound);

        WarehouseBoardStatVo vo = service.getCategoryStat("2026-09");

        CategoryStatVo otherCard = vo.getCategories().stream()
            .filter(c -> "other".equals(c.getCategoryKey())).findFirst().orElseThrow();
        assertThat(otherCard.getRows()).hasSize(1);
        assertThat(otherCard.getRows().get(0).getUnit()).isEqualTo("kg");

        // 明细弹窗合计走同一条聚合（品类白名单收窄到本卡）→ 字面必须与卡片一致
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), eq(List.of("other")), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("other", "Kg", "23.800")));
        BoardStatDetailVo detail = service.getInboundDetail("2026-09", "other");
        assertThat(detail.getTotals()).hasSize(1);
        assertThat(detail.getTotals().get(0).getUnit()).isEqualTo("kg");
    }

    /**
     * 非重量单位的展示原文仍<b>按品类隔离</b>：kg 走恒小写那条捷径，其余单位还是靠
     * 「每张卡一份 label map + 优先取全小写」定字面。共享一份 map 会让后到的品类显示先到者的字面。
     */
    @Test
    @DisplayName("非 kg 单位字面按品类隔离：other 卡显示自己的 Box，不被干货的 box 串味")
    void getCategoryStat_nonKgUnitLabelIsolatedPerCategory() {
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("dry_good", "box", "3.000"), row("other", "Box", "7.000")));

        WarehouseBoardStatVo vo = service.getCategoryStat("2026-09");

        CategoryStatVo dryCard = vo.getCategories().stream()
            .filter(c -> "dry_good".equals(c.getCategoryKey())).findFirst().orElseThrow();
        CategoryStatVo otherCard = vo.getCategories().stream()
            .filter(c -> "other".equals(c.getCategoryKey())).findFirst().orElseThrow();
        assertThat(dryCard.getRows().get(0).getUnit()).isEqualTo("box");
        assertThat(otherCard.getRows().get(0).getUnit()).isEqualTo("Box");
    }

    /**
     * 甲方 2026-09-08 row207：「增加【其他产品】的信息版块，统计逻辑和展示保持一致」。
     * 位置在干货之后，三个指标走同一套 SQL（品类白名单里必须带上 other，否则 SQL 一行都查不到）。
     */
    @Test
    @DisplayName("其他产品卡：belong_type='other'，排在干货之后，三指标同一套口径（row207）")
    void getCategoryStat_otherCard() {
        when(boardStatMapper.selectInboundByCategoryUnit(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("dry_good", "kg", "10.000"), row("other", "罐", "6")));
        when(boardStatMapper.selectProduceByCategoryUnit(eq("1001"), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("other", "罐", "2")));
        when(boardStatMapper.selectMaterialConsumeByCategoryUnit(
            eq("1001"), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(row("other", "罐", "2")));

        WarehouseBoardStatVo vo = service.getCategoryStat("2026-09");

        assertThat(vo.getCategories()).extracting(CategoryStatVo::getCategoryKey)
            .containsExactly("dry_good", "other");
        CategoryStatVo otherCard = vo.getCategories().get(1);
        assertThat(otherCard.getCategoryName()).isEqualTo("其他产品");
        assertThat(otherCard.getRows()).hasSize(1);
        assertThat(otherCard.getRows().get(0).getUnit()).isEqualTo("罐");
        assertThat(otherCard.getRows().get(0).getInboundQty()).isEqualByComparingTo("6");
        assertThat(otherCard.getRows().get(0).getProduceQty()).isEqualByComparingTo("2");
        assertThat(otherCard.getRows().get(0).getMaterialQty()).isEqualByComparingTo("2");

        // 三条 SQL 的品类白名单必须含 other，否则「其他产品」卡永远查不到数
        ArgumentCaptor<List<String>> belongCaptor = ArgumentCaptor.forClass(List.class);
        verify(boardStatMapper).selectProduceByCategoryUnit(
            eq("1001"), belongCaptor.capture(), eq(CUR_FROM), eq(CUR_TO));
        assertThat(belongCaptor.getValue())
            .containsExactly("pork", "white_bar", "vegetable", "egg", "dry_good", "other");
    }

    @Test
    @DisplayName("其他产品卡可下钻：belongType='other' 走白名单，明细正常返回")
    void getInboundDetail_otherCategory() {
        when(boardStatMapper.selectInboundDetailByProduct(
            eq("1001"), anyList(), anyList(), eq(CUR_FROM), eq(CUR_TO)))
            .thenReturn(List.of(productRow("301", "菜籽油（浓香）", "5L/桶", "6", "桶")));

        BoardStatDetailVo vo = service.getInboundDetail("2026-09", "other");

        assertThat(vo.getCategoryName()).isEqualTo("其他产品");
        assertThat(vo.getRows()).extracting(BoardStatProductRowVo::getProductName)
            .containsExactly("菜籽油（浓香）");
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

        // 有数的只剩果蔬 + 蛋类两张卡
        assertThat(vo.getCategories()).extracting(CategoryStatVo::getCategoryKey)
            .containsExactly("vegetable", "egg");

        CategoryUnitStatVo vegRow = vo.getCategories().get(0).getRows().get(0);
        assertThat(vegRow.getInboundRatio()).isEqualByComparingTo("30.00");
        // 本月生产量 0、上月也 0 → 无从算环比
        assertThat(vegRow.getProduceRatio()).isNull();

        // 上月该单位为 0 == 没有上个月数据
        CategoryUnitStatVo eggRow = vo.getCategories().get(1).getRows().get(0);
        assertThat(eggRow.getInboundRatio()).isNull();
    }

    @Test
    @DisplayName("全空兜底：mapper 全返空 → 一张卡都不出（mp 渲染整页空态），不抛 NPE")
    void getCategoryStat_empty() {
        WarehouseBoardStatVo vo = service.getCategoryStat(null);

        assertThat(vo.getMonth()).isNotBlank();
        assertThat(vo.getPrevMonth()).isNotBlank();
        assertThat(vo.getCategories()).isEmpty();
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

        // 礼盒没有自己的卡，礼盒自身的量（无论哪个指标）都不会挂到任何一张卡上
        assertThat(vo.getCategories()).extracting(CategoryStatVo::getCategoryKey)
            .containsExactly("pork");
        assertThat(vo.getCategories()).allSatisfy(card ->
            assertThat(card.getRows()).extracting(CategoryUnitStatVo::getUnit).doesNotContain("盒"));

        // 三条 SQL 拿到的是同一份品类白名单：白名单里没有 gift_box，
        // 所以「礼盒当原材料被消耗」也不会另开一张卡
        ArgumentCaptor<List<String>> belongCaptor = ArgumentCaptor.forClass(List.class);
        verify(boardStatMapper).selectMaterialConsumeByCategoryUnit(
            eq("1001"), belongCaptor.capture(), eq(CUR_FROM), eq(CUR_TO));
        assertThat(belongCaptor.getValue())
            .containsExactly("pork", "white_bar", "vegetable", "egg", "dry_good", "other")
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

        assertThat(vo.getCategories()).extracting(CategoryStatVo::getCategoryKey).containsExactly("pork");
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

        assertThat(vo.getCategories()).isNotNull();
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
    @DisplayName("belongType 白名单：不在卡片定义里的值 / 空 → 400，不静默返空")
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
