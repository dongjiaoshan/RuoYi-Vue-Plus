package org.dromara.djs.warehouse.demand.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.demand.domain.query.DemandPrintQuery;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintDetailVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintItemVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintSheetVo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.mapper.DemandPrintMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link DemandPrintServiceImpl} happy path + SQL 口径契约（V7-R8 需求单打印）。
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@DisplayName("需求单打印（V7-R8）")
class DemandPrintServiceImplTest {

    @Mock
    DemandPrintMapper demandPrintMapper;

    DemandPrintServiceImpl service;

    @BeforeEach
    void setup() {
        service = new DemandPrintServiceImpl(demandPrintMapper);
    }

    @Test
    @DisplayName("列表：透传日期区间 + 门店，分页结果原样返回")
    void sheetPagePassesFilters() {
        LocalDate today = LocalDate.of(2026, 10, 27);
        DemandPrintQuery q = new DemandPrintQuery();
        q.setBeginDate(today);
        q.setEndDate(today.plusDays(1));
        q.setStoreIds(List.of(1L, 2L));

        DemandPrintSheetVo row = new DemandPrintSheetVo();
        row.setDemandDate(today);
        row.setStoreId(1L);
        row.setStoreName("二七店");
        row.setCategoryCount(21);
        Page<DemandPrintSheetVo> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(demandPrintMapper.selectSheetPage(any(), eq(today), eq(today.plusDays(1)), eq(List.of(1L, 2L))))
            .thenReturn(page);

        PageQuery pq = new PageQuery(10, 1);
        TableDataInfo<DemandPrintSheetVo> rsp = service.querySheetPage(q, pq);

        assertThat(rsp.getTotal()).isEqualTo(1);
        assertThat(rsp.getRows()).singleElement()
            .satisfies(r -> {
                assertThat(r.getStoreName()).isEqualTo("二七店");
                assertThat(r.getCategoryCount()).isEqualTo(21);
            });
    }

    @Test
    @DisplayName("明细：抬头门店名 + 品类总数 = 合并后明细行数")
    void detailCategoryCountEqualsItemRows() {
        LocalDate day = LocalDate.of(2026, 10, 25);
        DemandPrintItemVo a = new DemandPrintItemVo();
        a.setProductName("白条");
        a.setDemandQuantity(new BigDecimal("0.5"));
        DemandPrintItemVo b = new DemandPrintItemVo();
        b.setProductName("刺黄瓜");
        b.setDemandQuantity(BigDecimal.ONE);
        when(demandPrintMapper.selectSheetItems(day, 7L)).thenReturn(List.of(a, b));
        when(demandPrintMapper.selectStoreName(7L)).thenReturn("二七店");

        DemandPrintDetailVo vo = service.getSheetDetail(day, 7L);

        assertThat(vo.getDemandDate()).isEqualTo(day);
        assertThat(vo.getStoreName()).isEqualTo("二七店");
        assertThat(vo.getCategoryCount()).isEqualTo(2);
        assertThat(vo.getItems()).extracting(DemandPrintItemVo::getProductName).containsExactly("白条", "刺黄瓜");
    }

    // ---------------- SQL 口径契约（注解 SQL，mock 打不到，拿原文钉死） ----------------

    @Test
    @DisplayName("长中文备注完整保留，按需求行顺序去重，备注内分号不拆开，产品间不串备注")
    void detailKeepsCompleteRemarks() {
        LocalDate day = LocalDate.of(2026, 10, 27);
        DemandPrintItemVo a = remarkRow(1L, null);
        DemandPrintItemVo b = remarkRow(2L, null);
        DemandPrintItemVo c = remarkRow(3L, null);
        String longRemark = "备货要求".repeat(120) + "尾部标记";
        when(demandPrintMapper.selectSheetItems(day, 7L)).thenReturn(List.of(a, b, c));
        when(demandPrintMapper.selectSheetRemarks(day, 7L)).thenReturn(List.of(
            remarkRow(1L, longRemark), remarkRow(1L, " 少盐；分装 "),
            remarkRow(1L, longRemark), remarkRow(1L, "少盐"),
            remarkRow(2L, "单独装箱"), remarkRow(3L, " "), remarkRow(3L, null)));

        DemandPrintDetailVo vo = service.getSheetDetail(day, 7L);

        assertThat(vo.getItems().get(0).getDemandRemark()).isEqualTo(longRemark + "；少盐；分装；少盐");
        assertThat(vo.getItems().get(1).getDemandRemark()).isEqualTo("单独装箱");
        assertThat(vo.getItems().get(2).getDemandRemark()).isNull();
        assertThat(vo.getCategoryCount()).isEqualTo(3);
    }

    private static DemandPrintItemVo remarkRow(Long productId, String remark) {
        DemandPrintItemVo row = new DemandPrintItemVo();
        row.setProductId(productId);
        row.setDemandRemark(remark);
        return row;
    }

    private static String sqlOf(Class<?> mapper, String name, Class<?>... params) throws Exception {
        Method m = mapper.getMethod(name, params);
        Select select = m.getAnnotation(Select.class);
        assertThat(select).as(name + " 应带 @Select").isNotNull();
        return String.join(" ", select.value())
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("打印列表、明细与备注仅含已确认需求，普通需求列表不受影响")
    void listAndDetailShareConfirmedPredicate() throws Exception {
        String list = sqlOf(DemandPrintMapper.class, "selectSheetPage",
            com.baomidou.mybatisplus.core.metadata.IPage.class, LocalDate.class, LocalDate.class, List.class);
        String detail = sqlOf(DemandPrintMapper.class, "selectSheetItems", LocalDate.class, Long.class);
        String remarks = sqlOf(DemandPrintMapper.class, "selectSheetRemarks", LocalDate.class, Long.class);
        String predicate = DemandPrintMapper.EFFECTIVE_DEMAND_WHERE.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);

        assertThat(list).contains(predicate);
        assertThat(detail).contains(predicate);
        assertThat(remarks).contains(predicate).contains("order by dm.id asc").doesNotContain("group_concat");
        assertThat(predicate).contains("dm.demand_status = 'confirmed'", "dm.del_flag = '0'", "dm.tenant_id = '1001'");
        assertThat(list).contains("order by dm.demand_date desc");

        // 需求管理汇总列表也是这四个条件：store 非空 + product 非空 + 排除 CANCELLED / DELETED
        String groupList = sqlOf(DemandManageMapper.class, "selectDemandGroupList",
            String.class, String.class, List.class, Long.class, List.class, LocalDate.class, LocalDate.class);
        assertThat(groupList)
            .contains("dm.product_id is not null")
            .contains("dm.store_id is not null")
            .contains("dm.demand_status <> 'cancelled'")
            .contains("dm.demand_status <> 'deleted'")
            .doesNotContain("dm.demand_status = 'confirmed'");
    }

    @Test
    @DisplayName("列表按（需求日期 × 门店）聚合，品类数 = 去重产品；明细按产品合并")
    void groupingKeys() throws Exception {
        String list = sqlOf(DemandPrintMapper.class, "selectSheetPage",
            com.baomidou.mybatisplus.core.metadata.IPage.class, LocalDate.class, LocalDate.class, List.class);
        String detail = sqlOf(DemandPrintMapper.class, "selectSheetItems", LocalDate.class, Long.class);

        assertThat(list)
            .contains("group by dm.demand_date, dm.store_id")
            .contains("count(distinct dm.product_id) as categorycount");
        assertThat(detail)
            .contains("group by dm.product_id")
            .contains("sum(dm.demand_quantity) as demandquantity");
    }
}
