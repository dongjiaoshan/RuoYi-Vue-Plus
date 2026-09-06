package org.dromara.djs.store.manage.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.store.domain.vo.StorePickerVo;
import org.dromara.djs.common.store.service.IStoreUserRelationService;
import org.dromara.djs.store.manage.domain.vo.StoreManageCategoryVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageMonthlyVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageProductCountRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageQtyRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageUnitRowVo;
import org.dromara.djs.store.manage.mapper.StoreManageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
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
 *   <li>空库兜底：mapper 全返空 → 4 张卡都在、品类数全 0、不抛 NPE</li>
 *   <li>月份非法 → 400，不静默回退当月</li>
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

        // 固定 4 张卡，顺序：猪肉 / 果蔬 / 蛋类 / 干货
        assertThat(vo.getCategories()).extracting(StoreManageCategoryVo::getCategoryKey)
            .containsExactly("pork", "vegetable", "egg", "dry_good");
        assertThat(vo.getCategories()).extracting(StoreManageCategoryVo::getCategoryName)
            .containsExactly("猪肉产品", "果蔬产品", "蛋类产品", "干货产品");

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

    @Test
    @DisplayName("单位大小写归一：kg / Kg 合成一行，展示取先见到的原文")
    void getMonthly_unitCaseMerged() {
        when(storeManageMapper.countArrivedProducts(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumDemandQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("dry_good", "kg", "10")));
        when(storeManageMapper.sumSaleQty(eq("1001"), eq(null), eq(CUR_START), eq(CUR_END), any()))
            .thenReturn(List.of(qty("dry_good", "Kg", "4")));
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
    @DisplayName("空库兜底：mapper 全返空 → 4 卡都在、品类数全 0、行全空，不抛 NPE")
    void getMonthly_emptyDb() {
        when(storeManageMapper.countArrivedProducts(eq("1001"), any(), any(), any(), any())).thenReturn(List.of());
        when(storeManageMapper.sumDemandQty(eq("1001"), any(), any(), any(), any())).thenReturn(null);
        when(storeManageMapper.sumSaleQty(eq("1001"), any(), any(), any(), any())).thenReturn(null);
        when(storeManageMapper.sumReturnQty(eq("1001"), any(), any(), any(), any())).thenReturn(null);

        StoreManageMonthlyVo vo = service.getMonthly(null, MONTH);

        assertThat(vo.getPorkProductCount()).isZero();
        assertThat(vo.getVegProductCount()).isZero();
        assertThat(vo.getOtherProductCount()).isZero();
        assertThat(vo.getCategories()).hasSize(4);
        assertThat(vo.getCategories()).allSatisfy(c -> assertThat(c.getRows()).isEmpty());
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
    @DisplayName("月份格式非法 → 400，不静默回退当月")
    void getMonthly_badMonthRejected() {
        assertThatThrownBy(() -> service.getMonthly(null, "2026/09"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("yyyy-MM");
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
