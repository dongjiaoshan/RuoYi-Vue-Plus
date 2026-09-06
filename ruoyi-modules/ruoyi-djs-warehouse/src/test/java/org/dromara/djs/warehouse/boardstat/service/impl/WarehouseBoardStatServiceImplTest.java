package org.dromara.djs.warehouse.boardstat.service.impl;

import org.dromara.common.tenant.helper.TenantHelper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private static CategoryUnitQtyRow row(String belongType, String unit, String qty) {
        CategoryUnitQtyRow r = new CategoryUnitQtyRow();
        r.setBelongType(belongType);
        r.setProductUnit(unit);
        r.setQty(new BigDecimal(qty));
        return r;
    }
}
