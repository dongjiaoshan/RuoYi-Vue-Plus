package org.dromara.djs.warehouse.demand.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.demand.domain.query.DemandPrintQuery;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintDetailVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintItemVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintSheetVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintSummaryItemVo;
import org.dromara.common.core.exception.ServiceException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("列表：透传单日 + 门店，分页结果原样返回")
    void sheetPagePassesFilters() {
        LocalDate today = LocalDate.of(2026, 10, 27);
        DemandPrintQuery q = new DemandPrintQuery();
        q.setDemandDate(today);
        q.setStoreIds(List.of(1L, 2L));

        DemandPrintSheetVo row = new DemandPrintSheetVo();
        row.setDemandDate(today);
        row.setStoreId(1L);
        row.setStoreName("二七店");
        row.setCategoryCount(21);
        Page<DemandPrintSheetVo> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(demandPrintMapper.selectSheetPage(any(), eq(today), eq(List.of(1L, 2L))))
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
        a.setProductId(1L);
        a.setProductName("白条");
        a.setDemandQuantity(new BigDecimal("0.5"));
        DemandPrintItemVo b = new DemandPrintItemVo();
        b.setProductId(2L);
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
    @DisplayName("备注拆行保留完整文字，品类总数仍按去重产品统计")
    void detailKeepsCompleteRemarks() {
        LocalDate day = LocalDate.of(2026, 10, 27);
        String longRemark = "备货要求".repeat(120) + "尾部标记";
        var rows = List.of(remarkRow(1L, longRemark), remarkRow(1L, "少盐；分装"),
            remarkRow(2L, "单独装箱"), remarkRow(3L, ""));
        when(demandPrintMapper.selectSheetItems(day, 7L)).thenReturn(rows);
        var vo = service.getSheetDetail(day, 7L);
        assertThat(vo.getItems()).extracting(DemandPrintItemVo::getDemandRemark)
            .containsExactly(longRemark, "少盐；分装", "单独装箱", "");
        assertThat(vo.getCategoryCount()).isEqualTo(3);
    }

    private static DemandPrintItemVo remarkRow(Long productId, String remark) {
        DemandPrintItemVo row = new DemandPrintItemVo();
        row.setProductId(productId);
        row.setDemandRemark(remark);
        return row;
    }

    @Test
    @DisplayName("普通订货单先完整展示品类较多的备注组，组内保留类别顺序且不跨备注合量")
    void detailGroupsRemarksBeforeProductCategories() {
        LocalDate day = LocalDate.of(2026, 11, 11);
        var first = remarkRow(1L, "二七门店订单");
        var headPork = remarkRow(2L, "二七总部订单");
        var storePork = remarkRow(2L, "二七门店订单");
        var headVeg = remarkRow(3L, "二七总部订单");
        var storeVeg = remarkRow(4L, "二七门店订单");
        headPork.setDemandQuantity(new BigDecimal("1000"));
        storePork.setDemandQuantity(new BigDecimal("2"));
        var rows = List.of(first, headPork, storePork, headVeg, storeVeg);
        when(demandPrintMapper.selectSheetItems(day, 7L)).thenReturn(rows);

        var result = service.getSheetDetail(day, 7L);

        assertThat(result.getItems()).containsExactly(first, storePork, storeVeg, headPork, headVeg);
        assertThat(result.getCategoryCount()).isEqualTo(4);
        assertThat(headPork.getDemandQuantity()).isEqualByComparingTo("1000");
        assertThat(storePork.getDemandQuantity()).isEqualByComparingTo("2");
        assertThat(rows).containsExactly(first, headPork, storePork, headVeg, storeVeg);
    }

    @Test
    @DisplayName("备注组品类按去重产品计数，不按行数或需求量；同数保持原先稳定次序")
    void detailRemarkPriorityUsesDistinctProducts() {
        LocalDate day = LocalDate.of(2026, 11, 11);
        var a = remarkRow(1L, "A");
        var duplicate = remarkRow(1L, "A");
        var b = remarkRow(2L, "B");
        var b2 = remarkRow(3L, "B");
        var c = remarkRow(4L, "C");
        var c2 = remarkRow(5L, "C");
        when(demandPrintMapper.selectSheetItems(day, 7L)).thenReturn(List.of(a, b, c, duplicate, c2, b2));

        assertThat(service.getSheetDetail(day, 7L).getItems()).containsExactly(b, b2, c, c2, a, duplicate);
    }

    @Test
    @DisplayName("空备注归同组，大小写与完整长备注仍为不同组")
    void detailPreservesExactRemarkKeysAndEmptyGroup() {
        LocalDate day = LocalDate.of(2026, 11, 11);
        var blank = remarkRow(1L, null);
        var upper = remarkRow(2L, "ABC");
        var longText = remarkRow(3L, "备货".repeat(250) + "尾部");
        var empty = remarkRow(4L, "");
        var lower = remarkRow(5L, "abc");
        var longText2 = remarkRow(6L, longText.getDemandRemark());
        when(demandPrintMapper.selectSheetItems(day, 7L)).thenReturn(List.of(blank, upper, longText, empty, lower, longText2));

        assertThat(service.getSheetDetail(day, 7L).getItems()).containsExactly(blank, empty, longText, longText2, upper, lower);
        assertThat(longText.getDemandRemark()).endsWith("尾部").hasSize(502);
    }

    @Test
    @DisplayName("无有效需求时普通订货单仍返回空明细与零品类")
    void detailEmptyRemainsEmpty() {
        LocalDate day = LocalDate.of(2026, 11, 11);
        when(demandPrintMapper.selectSheetItems(day, 7L)).thenReturn(List.of());
        var result = service.getSheetDetail(day, 7L);
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getCategoryCount()).isZero();
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
    @DisplayName("列表包含已提交需求，打印仅含已确认及继续履约状态，普通列表不受影响")
    void listIncludesSubmittedAndPrintKeepsConfirmedOnly() throws Exception {
        String list = sqlOf(DemandPrintMapper.class, "selectSheetPage",
            com.baomidou.mybatisplus.core.metadata.IPage.class, LocalDate.class, List.class);
        String detail = sqlOf(DemandPrintMapper.class, "selectSheetItems", LocalDate.class, Long.class);
        String predicate = DemandPrintMapper.EFFECTIVE_DEMAND_WHERE.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);

        assertThat(list).contains(DemandPrintMapper.SUBMITTED_DEMAND_WHERE.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT));
        assertThat(list).contains("dm.demand_date = #{demanddate}").doesNotContain("#{begindate}", "#{enddate}");
        assertThat(detail).contains(predicate);
        assertThat(detail).doesNotContain("group_concat").contains("collate utf8mb4_bin");
        assertThat(predicate).contains("dm.demand_status in ('confirmed','in_production','partial_shipped','completed')", "dm.del_flag = '0'", "dm.tenant_id = '1001'").doesNotContain("'submitted'");
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
            com.baomidou.mybatisplus.core.metadata.IPage.class, LocalDate.class, List.class);
        String detail = sqlOf(DemandPrintMapper.class, "selectSheetItems", LocalDate.class, Long.class);

        assertThat(list)
            .contains("group by dm.demand_date, dm.store_id")
            .contains("count(distinct dm.product_id) as categorycount");
        assertThat(detail)
            .contains("group by dm.product_id")
            .contains("sum(dm.demand_quantity) as demandquantity");
    }
    @Test
    @DisplayName("按名称跨产品ID/门店合并，KG别名归一，规格及原料去重并列")
    void summaryMergesByNameAndNormalizesUnits() {
        LocalDate day = LocalDate.of(2026, 11, 10);
        when(demandPrintMapper.selectSummaryItems(day, List.of(7L, 8L))).thenReturn(List.of(
            summaryRow("同名品", "KG", "1.250", "甲规格", "甲原料", "pork"),
            summaryRow("同名品", "公斤", "2.750", "乙规格", "乙原料", "pork"),
            summaryRow("同名品", "kg", "3.125", "甲规格", "甲原料", "pork"),
            summaryRow("半扇", "kg", "0.5", "", "", "white_bar")));
        var result = service.getSummary(day, List.of(7L, 8L));
        assertThat(result.getDemandDate()).isEqualTo(day);
        assertThat(result.getItems()).hasSize(2);
        var sameName = result.getItems().get(0);
        assertThat(sameName.getDemandQuantity()).isEqualByComparingTo("7.125");
        assertThat(sameName.getProductUnit()).isEqualTo("kg");
        assertThat(sameName.getProductSpec()).contains("甲规格", "乙规格");
        assertThat(sameName.getRawMaterialName()).contains("甲原料", "乙原料");
        assertThat(result.getItems().get(1).getProductUnit()).isEqualTo("头");
        assertThat(result.getItems().get(1).getDemandQuantity()).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("同名异单位不静默相加，空已确认结果不制造数据")
    void summaryRejectsIncompatibleUnitsAndKeepsEmpty() {
        LocalDate day = LocalDate.of(2026, 11, 10);
        when(demandPrintMapper.selectSummaryItems(day, null)).thenReturn(List.of(
            summaryRow("同名品", "kg", "1", "", "", "pork"),
            summaryRow("同名品", "份", "2", "", "", "pork")));
        assertThatThrownBy(() -> service.getSummary(day, null)).isInstanceOf(ServiceException.class)
            .hasMessageContaining("不同单位");
        when(demandPrintMapper.selectSummaryItems(day, null)).thenReturn(List.of());
        assertThat(service.getSummary(day, null).getItems()).isEmpty();
        assertThatThrownBy(() -> service.querySheetPage(new DemandPrintQuery(), new PageQuery(10, 1)))
            .isInstanceOf(ServiceException.class).hasMessageContaining("需求日期");
    }

    @Test
    @DisplayName("汇总只取确认四态并带日期、门店、租户及软删条件，原料为关联原材料")
    void summarySqlScopeAndPermission() throws Exception {
        String summary = sqlOf(DemandPrintMapper.class, "selectSummaryItems", LocalDate.class, List.class);
        String predicate = DemandPrintMapper.EFFECTIVE_DEMAND_WHERE.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        assertThat(summary).contains(predicate, "dm.demand_date = #{demanddate}", "dm.store_id in", "pm.id = pi.product_material")
            .doesNotContain("limit", "group_concat", "'submitted'");
        var controller = org.dromara.djs.warehouse.demand.controller.DemandPrintController.class;
        var permission = controller.getMethod("summary", DemandPrintQuery.class)
            .getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class);
        assertThat(permission.value()).containsExactly("djs:warehouse:demand:print");
    }

    private static DemandPrintSummaryItemVo summaryRow(String name, String unit, String quantity,
                                                       String spec, String material, String belong) {
        var row = new DemandPrintSummaryItemVo();
        row.setProductName(name); row.setProductUnit(unit); row.setDemandQuantity(new BigDecimal(quantity));
        row.setProductSpec(spec); row.setRawMaterialName(material); row.setBelongType(belong);
        return row;
    }

}
