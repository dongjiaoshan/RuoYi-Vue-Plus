package org.dromara.djs.store.manage.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.store.domain.vo.StorePickerVo;
import org.dromara.djs.common.store.service.IStoreUserRelationService;
import org.dromara.djs.store.manage.domain.vo.StoreManageCategoryVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageDetailRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageDetailTotalVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageDetailVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageMonthlyVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageProductCountRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageProductQtyRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageQtyRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageUnitRowVo;
import org.dromara.djs.store.manage.mapper.StoreManageMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreManageServiceImpl} 单测（MGMT-MP-STORE-MONTH-001）。
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>happy：品类数三桶归并（猪肉=pork+white_bar / 其他=egg+dry_good）+ 业态卡按单位分行 + 环比</li>
 *   <li>上月无基数 → 环比 0.00 且 hasBase=false（甲方口径：显示黑色 0.00%）</li>
 *   <li>上月有基数且相等 → 环比 0.00 但 hasBase=true（与上一条必须分得开）</li>
 *   <li>单位大小写归一：kg / Kg 合并成一行</li>
 *   <li>全 0 单位行不出行（台账 sale/gift 全 0 会制造这种行）</li>
 *   <li>D-0045 卡级：当月三指标全 0 的业态卡整卡不下发；全被剔掉时 categories 为空列表</li>
 *   <li>D-0045 行级：上月有数、当月三项全 0 的单位行不出（与卡级同规则，-100% 环比一起放弃）</li>
 *   <li>D-0046：belong_type=other 的「其他产品」卡，与顶部「其他品类数」(egg+dry_good) 互不影响</li>
 *   <li>空库兜底：mapper 全返空 → categories 空列表、品类数全 0、不抛 NPE</li>
 *   <li>月份非法 → 400，不静默回退当月</li>
 *   <li>「明细」下钻（V6-R180）：只在退回源出现的产品也出行（另两量 0）、totals 复用业态卡三条聚合、
 *       猪肉卡把 pork + white_bar 一起传给 SQL、belongType 白名单拒绝、分页参数透传</li>
 *   <li>明细逐产品环比（V6-R209）：上月同产品同口径为基数、上月无记录 → hasBase=false + 0.00、
 *       当前页为空时不发上月查询</li>
 * </ol>
 *
 * @author djs
 * @since MGMT-MP-STORE-MONTH-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreManageServiceImpl 单元测试")
class StoreManageServiceImplTest {

    private static final String MONTH = "2026-09";

    private static final LocalDate CUR_START = LocalDate.of(2026, 9, 1);

    private static final LocalDate CUR_END = LocalDate.of(2026, 10, 1);

    private static final LocalDate PREV_START = LocalDate.of(2026, 8, 1);

    @Mock
    private StoreManageMapper storeManageMapper;

    @Mock
    private IStoreUserRelationService storeUserRelationService;

    private StoreManageServiceImpl service;

    private MockedStatic<TenantHelper> tenantHelperMock;

    @BeforeEach
    void setUp() {
        service = new StoreManageServiceImpl(storeManageMapper, storeUserRelationService);
        tenantHelperMock = Mockito.mockStatic(TenantHelper.class);
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn("1001");
    }

    @AfterEach
    void tearDown() {
        if (tenantHelperMock != null) {
            tenantHelperMock.close();
        }
    }

    private static StoreManageQtyRowVo qty(String belongType, String unit, String value) {
        StoreManageQtyRowVo row = new StoreManageQtyRowVo();
        row.setBelongType(belongType);
        row.setUnit(unit);
        row.setQty(new BigDecimal(value));
        return row;
    }

    private static StoreManageProductCountRowVo count(String belongType, int productCount) {
        StoreManageProductCountRowVo row = new StoreManageProductCountRowVo();
        row.setBelongType(belongType);
        row.setProductCount(productCount);
        return row;
    }

    private static StoreManageCategoryVo categoryOf(StoreManageMonthlyVo vo, String key) {
        return vo.getCategories().stream()
            .filter(c -> key.equals(c.getCategoryKey()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("缺少业态卡 " + key));
    }

    @Test
    @DisplayName("happy：品类数三桶归并 + 猪肉卡按 kg/份 两行 + 环比计算")
    void getMonthly_happy() {
        when(storeManageMapper.countArrivedProducts(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(
                count("pork", 2), count("white_bar", 1),
                count("vegetable", 5),
                count("egg", 1), count("dry_good", 2)));

        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("pork", "kg", "240"), qty("white_bar", "kg", "3"), qty("pork", "份", "10")));
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("pork", "kg", "200"), qty("pork", "份", "4")));
        when(storeManageMapper.sumReturnQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("pork", "kg", "3")));

        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of(qty("pork", "kg", "200")));
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of(qty("pork", "kg", "250")));
        when(storeManageMapper.sumReturnQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of());

        StoreManageMonthlyVo vo = service.getMonthly(null, MONTH);

        assertThat(vo.getMonth()).isEqualTo("2026-09");
        assertThat(vo.getStoreId()).isNull();
        // 猪肉品类数 = pork(2) + white_bar(1)
        assertThat(vo.getPorkProductCount()).isEqualTo(3);
        assertThat(vo.getVegProductCount()).isEqualTo(5);
        // 其他品类数 = egg(1) + dry_good(2)
        assertThat(vo.getOtherProductCount()).isEqualTo(3);

        // D-0045：本月只有猪肉有数据，其余四张卡整卡不下发
        assertThat(vo.getCategories()).extracting(StoreManageCategoryVo::getCategoryKey)
            .containsExactly("pork");
        assertThat(vo.getCategories()).extracting(StoreManageCategoryVo::getCategoryName)
            .containsExactly("猪肉产品");

        StoreManageCategoryVo pork = categoryOf(vo, "pork");
        assertThat(pork.getRows()).hasSize(2);
        // 合计大的单位排前：kg 行 = 需求 240+3(white_bar 并进猪肉卡) / 销售 200 / 退回 3
        StoreManageUnitRowVo kg = pork.getRows().get(0);
        assertThat(kg.getUnit()).isEqualTo("kg");
        assertThat(kg.getDemand().getValue()).isEqualByComparingTo("243");
        assertThat(kg.getSale().getValue()).isEqualByComparingTo("200");
        assertThat(kg.getReturned().getValue()).isEqualByComparingTo("3");
        // 需求环比 = (243 - 200) / 200 = +21.50%
        assertThat(kg.getDemand().getMom()).isEqualByComparingTo("21.50");
        assertThat(kg.getDemand().getHasBase()).isTrue();
        // 销售环比 = (200 - 250) / 250 = -20.00%
        assertThat(kg.getSale().getMom()).isEqualByComparingTo("-20.00");
        // 退回上月无基数 → 0.00 + hasBase=false
        assertThat(kg.getReturned().getMom()).isEqualByComparingTo("0.00");
        assertThat(kg.getReturned().getHasBase()).isFalse();

        StoreManageUnitRowVo fen = pork.getRows().get(1);
        assertThat(fen.getUnit()).isEqualTo("份");
        assertThat(fen.getDemand().getValue()).isEqualByComparingTo("10");
        assertThat(fen.getSale().getValue()).isEqualByComparingTo("4");
        assertThat(fen.getReturned().getValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("上月有基数且持平 → 环比 0.00 但 hasBase=true（与「无基数」的 0.00 分得开）")
    void getMonthly_flatMomKeepsHasBase() {
        when(storeManageMapper.countArrivedProducts(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("vegetable", "kg", "100")));
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of(qty("vegetable", "kg", "100")));
        when(storeManageMapper.sumSaleQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumReturnQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());

        StoreManageUnitRowVo row = categoryOf(service.getMonthly(null, MONTH), "vegetable").getRows().get(0);

        assertThat(row.getDemand().getMom()).isEqualByComparingTo("0.00");
        assertThat(row.getDemand().getHasBase()).isTrue();
        assertThat(row.getSale().getHasBase()).isFalse();
    }

    /**
     * 单位大小写归一 + 取字面规则。
     *
     * <p>mock 故意让<b>大写先出现</b>（demand=Kg 在前、sale=kg 在后）：这样「先见到的原文」会给
     * {@code Kg}、而 {@link StoreManageServiceImpl#putLabel} 的「有全小写就取全小写」给 {@code kg}，
     * 两种规则结论不同，本用例才对规则有判别力 —— 反过来（小写在前）两种规则同结果，
     * 把实现退回 {@code putIfAbsent} 也照样绿，等于没测。</p>
     */
    @Test
    @DisplayName("单位大小写归一：kg / Kg 合成一行，展示确定性取全小写那个（大写先出现也不例外）")
    void getMonthly_unitCaseMerged() {
        when(storeManageMapper.countArrivedProducts(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("dry_good", "Kg", "10")));
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("dry_good", "kg", "4")));
        when(storeManageMapper.sumReturnQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of());
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of());

        List<StoreManageUnitRowVo> rows = categoryOf(service.getMonthly(null, MONTH), "dry_good").getRows();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getUnit()).isEqualTo("kg");
        assertThat(rows.get(0).getDemand().getValue()).isEqualByComparingTo("10");
        assertThat(rows.get(0).getSale().getValue()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("本月与上月都是 0 的单位行不出行（台账 sale/gift 全 0 的行不该占一行）")
    void getMonthly_dropsAllZeroUnitRow() {
        when(storeManageMapper.countArrivedProducts(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("egg", "枚", "0"), qty("egg", "份", "12")));
        when(storeManageMapper.sumDemandQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumReturnQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of());

        List<StoreManageUnitRowVo> rows = categoryOf(service.getMonthly(null, MONTH), "egg").getRows();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getUnit()).isEqualTo("份");
    }

    @Test
    @DisplayName("空库兜底：mapper 全返空 → categories 空列表、品类数全 0，不抛 NPE（前端出整页空态）")
    void getMonthly_emptyDb() {
        when(storeManageMapper.countArrivedProducts(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumDemandQty(eq("1001"), any(), any(), any(), any())).thenReturn(null);
        when(storeManageMapper.sumSaleQty(eq("1001"), any(), any(), any(), any())).thenReturn(null);
        when(storeManageMapper.sumReturnQty(eq("1001"), any(), any(), any(), any())).thenReturn(null);

        StoreManageMonthlyVo vo = service.getMonthly(null, MONTH);

        assertThat(vo.getPorkProductCount()).isZero();
        assertThat(vo.getVegProductCount()).isZero();
        assertThat(vo.getOtherProductCount()).isZero();
        assertThat(vo.getCategories()).isEmpty();
    }

    @Test
    @DisplayName("storeId 透传：单店查询把 storeId 带进每条聚合")
    void getMonthly_withStoreId() {
        Long storeId = 2057794757010124802L;
        when(storeManageMapper.countArrivedProducts(eq("1001"), eq(storeId), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(count("vegetable", 4)));
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(storeId), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(storeId), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumReturnQty(eq("1001"), eq(storeId), any(), any(), any())).thenReturn(List.of());

        StoreManageMonthlyVo vo = service.getMonthly(storeId, MONTH);

        assertThat(vo.getStoreId()).isEqualTo(storeId);
        assertThat(vo.getVegProductCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("D-0045：本月三指标全 0 的业态卡整卡不下发（上月有数据也不救它）")
    void getMonthly_dropsCardWithoutCurrentData() {
        when(storeManageMapper.countArrivedProducts(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        // 果蔬本月有量 → 留；蛋类本月全 0（上月还有 20）→ 剔
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("vegetable", "份", "29"), qty("egg", "枚", "0")));
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of());
        when(storeManageMapper.sumReturnQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of());
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of(qty("egg", "枚", "20")));
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of());
        when(storeManageMapper.sumReturnQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of());

        StoreManageMonthlyVo vo = service.getMonthly(null, MONTH);

        assertThat(vo.getCategories()).extracting(StoreManageCategoryVo::getCategoryKey)
            .containsExactly("vegetable");
    }

    @Test
    @DisplayName("D-0045 行级：上月有数、当月三项全 0 的单位行不再出现（与卡级同一把尺子）")
    void getMonthly_dropsUnitRowWithoutCurrentData() {
        when(storeManageMapper.countArrivedProducts(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        // 猪肉卡本月只有「份」有量 → 卡留下；「kg」上月 200、本月归零 → 该行必须消失（连同 -100% 环比）
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("pork", "份", "7")));
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("pork", "kg", "0")));
        when(storeManageMapper.sumReturnQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of());
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of(qty("pork", "kg", "200")));
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of(qty("pork", "kg", "150")));
        when(storeManageMapper.sumReturnQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of(qty("pork", "kg", "3")));

        List<StoreManageUnitRowVo> rows = categoryOf(service.getMonthly(null, MONTH), "pork").getRows();

        assertThat(rows).extracting(StoreManageUnitRowVo::getUnit).containsExactly("份");
        assertThat(rows.get(0).getDemand().getValue()).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("D-0046：belong_type=other 出「其他产品」卡（排在干货之后），顶部「其他品类数」仍只数 egg+dry_good")
    void getMonthly_otherCategoryCard() {
        when(storeManageMapper.countArrivedProducts(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(count("egg", 1), count("dry_good", 2), count("other", 18)));
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("dry_good", "份", "5"), qty("other", "份", "6")));
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("other", "份", "2")));
        when(storeManageMapper.sumReturnQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of(qty("other", "份", "4")));
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any()))
            .thenReturn(List.of());

        StoreManageMonthlyVo vo = service.getMonthly(null, MONTH);

        // 其他品类数 = egg(1) + dry_good(2)：不含 other 的 18 —— 两个「其他」口径互不串
        assertThat(vo.getOtherProductCount()).isEqualTo(3);
        assertThat(vo.getCategories()).extracting(StoreManageCategoryVo::getCategoryKey)
            .containsExactly("dry_good", "other");
        StoreManageCategoryVo other = categoryOf(vo, "other");
        assertThat(other.getCategoryName()).isEqualTo("其他产品");
        assertThat(other.getRows()).hasSize(1);
        assertThat(other.getRows().get(0).getDemand().getValue()).isEqualByComparingTo("6");
        // 环比与其余卡同一套算法：(6-4)/4 = +50.00%
        assertThat(other.getRows().get(0).getDemand().getMom()).isEqualByComparingTo("50.00");
        assertThat(other.getRows().get(0).getSale().getHasBase()).isFalse();
    }

    @Test
    @DisplayName("聚合 SQL 的 belong_type 白名单含 other（否则「其他产品」卡永远查不到数）")
    void getMonthly_belongTypeWhitelistIncludesOther() {
        when(storeManageMapper.countArrivedProducts(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumDemandQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumSaleQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumReturnQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());

        service.getMonthly(null, MONTH);

        ArgumentCaptor<List<String>> belongCaptor = ArgumentCaptor.forClass(List.class);
        verify(storeManageMapper).countArrivedProducts(
            anyString(), any(), eq(CUR_START), eq(CUR_END), belongCaptor.capture());
        assertThat(belongCaptor.getValue())
            .containsExactly("pork", "white_bar", "vegetable", "egg", "dry_good", "other");
    }

    @Test
    @DisplayName("月份格式非法 → 400，不静默回退当月")
    void getMonthly_badMonthRejected() {
        assertThatThrownBy(() -> service.getMonthly(null, "2026/09"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("yyyy-MM");
    }

    // ==================== 业态卡「明细」下钻（V6-R180）====================

    @Test
    @DisplayName("明细：只在退回源出现的产品也出行（需求/销售 0），totals 复用卡片三条聚合并按单位合并")
    void getDetail_mergesThreeSourcesAndReusesCardTotals() {
        // SQL 侧三源 UNION ALL 后的形态：第 2 行是「本月没下单没卖、只退了货」的产品
        when(storeManageMapper.selectProductDetailPage(
            any(), eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(pageOf(List.of(
                detailRow(2057794757010124801L, "带皮五花", "500g/份", "kg", "240", "200", "3"),
                detailRow(2057794757010124802L, "猪筒骨", null, null, "0", "0", "8")), 2L));
        // totals 源 = 卡片那三条聚合：pork + white_bar 同单位应合成一行
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("pork", "kg", "240"), qty("white_bar", "Kg", "3")));
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("pork", "kg", "200")));
        when(storeManageMapper.sumReturnQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("pork", "份", "8")));

        StoreManageDetailVo vo = service.getDetail(null, MONTH, "pork", new PageQuery(10, 1));

        assertThat(vo.getMonth()).isEqualTo(MONTH);
        assertThat(vo.getBelongType()).isEqualTo("pork");
        assertThat(vo.getCategoryName()).isEqualTo("猪肉产品");
        assertThat(vo.getStoreId()).isNull();
        assertThat(vo.getTotal()).isEqualTo(2L);

        // 只有退回量的产品照出行，另两个量补 0；规格 / 单位空值兜底
        StoreManageDetailRowVo onlyReturn = vo.getRows().get(1);
        assertThat(onlyReturn.getProductName()).isEqualTo("猪筒骨");
        assertThat(onlyReturn.getProductSpec()).isEqualTo("—");
        assertThat(onlyReturn.getUnit()).isEqualTo("未设单位");
        assertThat(onlyReturn.getDemandQty()).isEqualByComparingTo("0");
        assertThat(onlyReturn.getSaleQty()).isEqualByComparingTo("0");
        assertThat(onlyReturn.getReturnQty()).isEqualByComparingTo("8");

        // totals：kg / Kg 归一成一行（240 + 3），只有退回量的「份」单位也必须出行
        assertThat(vo.getTotals()).extracting(StoreManageDetailTotalVo::getUnit).containsExactly("kg", "份");
        assertThat(vo.getTotals().get(0).getDemandQty()).isEqualByComparingTo("243");
        assertThat(vo.getTotals().get(0).getSaleQty()).isEqualByComparingTo("200");
        assertThat(vo.getTotals().get(0).getReturnQty()).isEqualByComparingTo("0");
        assertThat(vo.getTotals().get(1).getDemandQty()).isEqualByComparingTo("0");
        assertThat(vo.getTotals().get(1).getReturnQty()).isEqualByComparingTo("8");

        // 猪肉卡把 pork + white_bar 一起传给 SQL（明细与卡片同集合的前提）
        ArgumentCaptor<List<String>> belongCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<IPage<StoreManageDetailRowVo>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        verify(storeManageMapper).selectProductDetailPage(
            pageCaptor.capture(), eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), belongCaptor.capture());
        assertThat(belongCaptor.getValue()).containsExactly("pork", "white_bar");
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10L);
    }

    @Test
    @DisplayName("明细：storeId 透传到分页与三条合计聚合")
    void getDetail_passesStoreId() {
        Long storeId = 2057794757010124802L;
        when(storeManageMapper.selectProductDetailPage(any(), eq("1001"), eq(storeId), any(), any(), any()))
            .thenReturn(pageOf(List.of(), 0L));
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(storeId), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(storeId), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumReturnQty(eq("1001"), eq(storeId), any(), any(), any())).thenReturn(List.of());

        StoreManageDetailVo vo = service.getDetail(storeId, MONTH, "vegetable", new PageQuery(20, 1));

        assertThat(vo.getStoreId()).isEqualTo(storeId);
        assertThat(vo.getCategoryName()).isEqualTo("果蔬产品");
        assertThat(vo.getRows()).isEmpty();
        assertThat(vo.getTotals()).isEmpty();
        verify(storeManageMapper).sumReturnQty(eq("1001"), eq(storeId), eq(CUR_START), eq(CUR_END),
            eq(List.of("vegetable")));
    }

    @Test
    @DisplayName("明细：belongType 白名单——不在业态卡里的值 / 空 / white_bar 一律 400，不静默返空")
    void getDetail_rejectsUnknownBelongType() {
        PageQuery pq = new PageQuery(10, 1);

        assertThatThrownBy(() -> service.getDetail(null, MONTH, "gift_box", pq))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("业态不合法");
        assertThatThrownBy(() -> service.getDetail(null, MONTH, "white_bar", pq))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.getDetail(null, MONTH, null, pq))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("明细：新卡 other 可下钻（D-0046），belongTypes 只传 other")
    void getDetail_acceptsOtherCategory() {
        when(storeManageMapper.selectProductDetailPage(any(), eq("1001"), any(), any(), any(), any()))
            .thenReturn(pageOf(List.of(), 0L));
        when(storeManageMapper.sumDemandQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumSaleQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumReturnQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());

        StoreManageDetailVo vo = service.getDetail(null, MONTH, "other", new PageQuery(20, 1));

        assertThat(vo.getBelongType()).isEqualTo("other");
        assertThat(vo.getCategoryName()).isEqualTo("其他产品");
        verify(storeManageMapper).selectProductDetailPage(
            any(), eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), eq(List.of("other")));
    }

    @Test
    @DisplayName("明细逐产品环比（R209）：上月同产品同口径为基数；上月无记录 → 0.00 + hasBase=false")
    void getDetail_perProductMom() {
        when(storeManageMapper.selectProductDetailPage(
            any(), eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(pageOf(List.of(
                detailRow(2057794757010124801L, "有机种植豇豆300g", "300g/份", "份", "7", "1", "1"),
                detailRow(2057794757010124802L, "有机茼蒿350g", "350g/份", "份", "2", "0", "0")), 2L));
        when(storeManageMapper.selectProductMonthSums(
            eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), any(), any()))
            .thenReturn(List.of(prevQty(2057794757010124801L, "4", "2", "0")));
        when(storeManageMapper.sumDemandQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumSaleQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumReturnQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());

        StoreManageDetailVo vo = service.getDetail(null, MONTH, "vegetable", new PageQuery(20, 1));

        StoreManageDetailRowVo first = vo.getRows().get(0);
        // 需求 (7-4)/4 = +75.00%；销售 (1-2)/2 = -50.00%；退回上月 0 → 0.00 + hasBase=false
        assertThat(first.getDemandMom()).isEqualByComparingTo("75.00");
        assertThat(first.getDemandHasBase()).isTrue();
        assertThat(first.getSaleMom()).isEqualByComparingTo("-50.00");
        assertThat(first.getSaleHasBase()).isTrue();
        assertThat(first.getReturnMom()).isEqualByComparingTo("0.00");
        assertThat(first.getReturnHasBase()).isFalse();

        // 上月完全没这个产品 → 三项都是黑色 0.00%
        StoreManageDetailRowVo second = vo.getRows().get(1);
        assertThat(second.getDemandMom()).isEqualByComparingTo("0.00");
        assertThat(second.getDemandHasBase()).isFalse();
        assertThat(second.getSaleHasBase()).isFalse();
        assertThat(second.getReturnHasBase()).isFalse();

        // 上月基数只查当前页那几个产品，且业态白名单与本月同一份
        ArgumentCaptor<List<Long>> idCaptor = ArgumentCaptor.forClass(List.class);
        verify(storeManageMapper).selectProductMonthSums(
            eq("1001"), eq(null), eq(PREV_START), eq(CUR_START), eq(List.of("vegetable")), idCaptor.capture());
        assertThat(idCaptor.getValue())
            .containsExactly(2057794757010124801L, 2057794757010124802L);
    }

    @Test
    @DisplayName("明细：当前页一行都没有时不发上月基数查询（空 IN () 会拼出非法 SQL）")
    void getDetail_skipsPrevQueryOnEmptyPage() {
        when(storeManageMapper.selectProductDetailPage(any(), eq("1001"), any(), any(), any(), any()))
            .thenReturn(pageOf(List.of(), 0L));
        when(storeManageMapper.sumDemandQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumSaleQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumReturnQty(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());

        service.getDetail(null, MONTH, "egg", new PageQuery(20, 1));

        verify(storeManageMapper, never()).selectProductMonthSums(
            anyString(), any(), any(), any(), anyList(), anyList());
    }

    @Test
    @DisplayName("明细：月份非法 → 400；分页参数缺省 → 第 1 页 20 条（不退化成查全部）")
    void getDetail_monthAndPagingDefaults() {
        assertThatThrownBy(() -> service.getDetail(null, "2026/09", "egg", new PageQuery(10, 1)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("yyyy-MM");

        when(storeManageMapper.selectProductDetailPage(any(), anyString(), any(), any(), any(), anyList()))
            .thenReturn(pageOf(List.of(), 0L));
        service.getDetail(null, MONTH, "egg", null);

        ArgumentCaptor<IPage<StoreManageDetailRowVo>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        verify(storeManageMapper).selectProductDetailPage(
            pageCaptor.capture(), anyString(), any(), any(), any(), anyList());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
    }

    private static <T> IPage<T> pageOf(List<T> records, long total) {
        Page<T> page = new Page<>(1, 10, total);
        page.setRecords(records);
        return page;
    }

    private static StoreManageProductQtyRowVo prevQty(Long productId, String demand, String sale, String returned) {
        StoreManageProductQtyRowVo row = new StoreManageProductQtyRowVo();
        row.setProductId(productId);
        row.setDemandQty(new BigDecimal(demand));
        row.setSaleQty(new BigDecimal(sale));
        row.setReturnQty(new BigDecimal(returned));
        return row;
    }

    private static StoreManageDetailRowVo detailRow(Long productId, String name, String spec, String unit,
                                                    String demand, String sale, String returned) {
        StoreManageDetailRowVo row = new StoreManageDetailRowVo();
        row.setProductId(productId);
        row.setProductName(name);
        row.setProductSpec(spec);
        row.setUnit(unit);
        row.setDemandQty(new BigDecimal(demand));
        row.setSaleQty(new BigDecimal(sale));
        row.setReturnQty(new BigDecimal(returned));
        return row;
    }

    @Test
    @DisplayName("门店下拉：service 返 null 时兜成空列表")
    void listSelectableStores_nullSafe() {
        when(storeUserRelationService.listMyStores(true)).thenReturn(null);
        assertThat(service.listSelectableStores()).isEmpty();

        StorePickerVo s = new StorePickerVo();
        s.setId(1L);
        s.setStoreName("二七滨江门店");
        when(storeUserRelationService.listMyStores(true)).thenReturn(List.of(s));
        assertThat(service.listSelectableStores()).hasSize(1);
    }

}
