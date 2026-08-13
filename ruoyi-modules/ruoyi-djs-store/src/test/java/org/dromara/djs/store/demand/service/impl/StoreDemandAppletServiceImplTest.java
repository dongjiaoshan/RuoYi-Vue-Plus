package org.dromara.djs.store.demand.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.common.store.service.IStoreService;
import org.dromara.djs.common.store.service.IStoreUserRelationService;
import org.dromara.djs.plant.cropstat.domain.vo.CropPlotStatVo;
import org.dromara.djs.plant.cropstat.service.ICropPlotStatService;
import org.dromara.djs.store.demand.domain.bo.StoreDemandBatchBo;
import org.dromara.djs.store.demand.domain.bo.StoreDemandQuantityBo;
import org.dromara.djs.store.demand.domain.vo.StoreDemandCatalogVo;
import org.dromara.djs.store.demand.domain.vo.StoreDemandDayVo;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.domain.vo.StoreDemandDayAggVo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
import org.dromara.djs.warehouse.demand.service.IDemandStatusService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.service.IProductDisplayNameResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreDemandAppletServiceImpl} 单测（V6 row66/68/70 门店小程序需求）。
 *
 * <p>重点覆盖三处最容易出错、且不看真库也能断言的口径：</p>
 * <ol>
 *   <li>{@code dayStatus} 单调阶梯（含零行 / 混合态边界）</li>
 *   <li>{@code confirmRate} 算式（与 admin 同口径，按需求单不按门店）</li>
 *   <li>门店态筛选<b>下推到 SQL</b>（不是查全量再内存筛），以及目录排序的确定性</li>
 * </ol>
 *
 * @author djs
 * @since STORE-MP-BOARD-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreDemandAppletServiceImpl 门店小程序需求单测")
class StoreDemandAppletServiceImplTest {

    @Mock
    private DemandManageMapper demandManageMapper;

    @Mock
    private IDemandManageService demandManageService;

    @Mock
    private IDemandStatusService demandStatusService;

    @Mock
    private ProductInfoMapper productInfoMapper;

    @Mock
    private IProductDisplayNameResolver displayNameResolver;

    @Mock
    private ImageUrlResolver imageUrlResolver;

    @Mock
    private ICropPlotStatService cropPlotStatService;

    @Mock
    private IStoreUserRelationService storeUserRelationService;

    @Mock
    private IStoreService storeService;

    @Mock
    private org.dromara.djs.store.demand.service.IStoreDemandService storeDemandService;

    @Mock
    private org.dromara.djs.store.demand.core.StoreDemandViewEnricher viewEnricher;

    private StoreDemandAppletServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    /** MP 单测 entity cache 预热：service 内 LambdaQueryWrapper 解析 lambda 列名需要先注册 entity。 */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, DemandManage.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
    }

    @BeforeEach
    void setUp() {
        service = new StoreDemandAppletServiceImpl(demandManageMapper, demandManageService, demandStatusService,
            productInfoMapper, displayNameResolver, imageUrlResolver, cropPlotStatService, storeUserRelationService,
            storeService, storeDemandService, viewEnricher);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(2001L);
        // 关墙（V1 默认）→ 恒放行；测「无权门店」的用例自己覆盖成 false
        when(storeUserRelationService.isStoreAccessible(any(), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    // ---------------- dayStatus 阶梯 ----------------

    @Test
    @DisplayName("dayStatus：全部已到店 → ARRIVED")
    void dayStatusAllArrived() {
        assertThat(StoreDemandAppletServiceImpl.dayStatus(3, 3, 0, 0)).isEqualTo("ARRIVED");
    }

    @Test
    @DisplayName("dayStatus：只剩 已发货+已到店 → SHIPPED")
    void dayStatusShippedOrArrived() {
        assertThat(StoreDemandAppletServiceImpl.dayStatus(3, 1, 2, 0)).isEqualTo("SHIPPED");
        assertThat(StoreDemandAppletServiceImpl.dayStatus(2, 0, 2, 0)).isEqualTo("SHIPPED");
    }

    @Test
    @DisplayName("dayStatus：全部确认（含已发货 / 已到店混合）→ IN_PRODUCTION")
    void dayStatusAllConfirmed() {
        assertThat(StoreDemandAppletServiceImpl.dayStatus(3, 0, 0, 3)).isEqualTo("IN_PRODUCTION");
        assertThat(StoreDemandAppletServiceImpl.dayStatus(5, 1, 1, 3)).isEqualTo("IN_PRODUCTION");
    }

    @Test
    @DisplayName("dayStatus：混合态里只要还有一条待确认（其余桶 > 0）→ CONFIRMING")
    void dayStatusAnySubmittedFallsBack() {
        // 4 条里 3 条已到店 + 1 条待确认（未计入三个桶）→ 仍是「需求确认中」
        assertThat(StoreDemandAppletServiceImpl.dayStatus(4, 3, 0, 0)).isEqualTo("CONFIRMING");
        assertThat(StoreDemandAppletServiceImpl.dayStatus(4, 1, 1, 1)).isEqualTo("CONFIRMING");
    }

    @Test
    @DisplayName("dayStatus：零行（理论不出现，SQL 已排除全 DELETED 的天）→ CONFIRMING，不炸")
    void dayStatusZeroRows() {
        assertThat(StoreDemandAppletServiceImpl.dayStatus(0, 0, 0, 0)).isEqualTo("CONFIRMING");
    }

    // ---------------- confirmRate 算式 ----------------

    @Test
    @DisplayName("confirmRate：已确认/总数，4 位小数 HALF_UP")
    void confirmRateComputation() {
        assertThat(StoreDemandAppletServiceImpl.confirmRate(3, 1)).isEqualByComparingTo(new BigDecimal("0.3333"));
        assertThat(StoreDemandAppletServiceImpl.confirmRate(3, 3)).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(StoreDemandAppletServiceImpl.confirmRate(4, 1)).isEqualByComparingTo(new BigDecimal("0.2500"));
    }

    @Test
    @DisplayName("confirmRate：一条都没确认 → 0；分母 0（零行边界）→ 0 不抛除零")
    void confirmRateEdgeCases() {
        assertThat(StoreDemandAppletServiceImpl.confirmRate(5, 0)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(StoreDemandAppletServiceImpl.confirmRate(0, 0)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------------- row66 day-list ----------------

    @Test
    @DisplayName("day-list：聚合行 → 卡片 VO（dayStatus + confirmRate 派生，total 透传）")
    void queryDayListHappyPath() {
        StoreDemandDayAggVo agg = new StoreDemandDayAggVo();
        agg.setDemandDate("2026-08-08");
        agg.setStoreId(9315000000000001L);
        agg.setOrdererName("张店员");
        agg.setCategoryCount(3);
        agg.setTotalCount(4);
        agg.setArrivedCount(1);
        agg.setShippedCount(1);
        agg.setConfirmedCount(1);
        agg.setDamagedCount(2);
        agg.setLastOrderTime("2026-08-08 18:30");
        Page<StoreDemandDayAggVo> page = new Page<>(1, 10);
        page.setRecords(List.of(agg));
        page.setTotal(7);
        when(demandManageMapper.selectStoreDemandDayPage(any(), any(), any(), any())).thenReturn(page);

        TableDataInfo<StoreDemandDayVo> rsp = service.queryDayList(9315000000000001L, null, null, new PageQuery(1, 10));

        assertThat(rsp.getTotal()).isEqualTo(7);
        StoreDemandDayVo vo = rsp.getRows().get(0);
        assertThat(vo.getDemandDate()).isEqualTo("2026-08-08");
        assertThat(vo.getOrdererName()).isEqualTo("张店员");
        assertThat(vo.getCategoryCount()).isEqualTo(3);
        assertThat(vo.getDamagedCount()).isEqualTo(2);
        assertThat(vo.getLastOrderTime()).isEqualTo("2026-08-08 18:30");
        // 4 条里 1 条待确认 → 需求确认中；已确认 3/4
        assertThat(vo.getDayStatus()).isEqualTo("CONFIRMING");
        assertThat(vo.getConfirmRate()).isEqualByComparingTo(new BigDecimal("0.7500"));
    }

    @Test
    @DisplayName("day-list：计数列为 null（聚合无值）时兜 0，不 NPE")
    void queryDayListNullCounts() {
        StoreDemandDayAggVo agg = new StoreDemandDayAggVo();
        agg.setDemandDate("2026-08-08");
        Page<StoreDemandDayAggVo> page = new Page<>(1, 10);
        page.setRecords(List.of(agg));
        page.setTotal(1);
        when(demandManageMapper.selectStoreDemandDayPage(any(), any(), any(), any())).thenReturn(page);

        StoreDemandDayVo vo = service.queryDayList(1L, null, null, null).getRows().get(0);
        assertThat(vo.getCategoryCount()).isZero();
        assertThat(vo.getDamagedCount()).isZero();
        assertThat(vo.getDayStatus()).isEqualTo("CONFIRMING");
        assertThat(vo.getConfirmRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------------- row70 day-detail：门店态筛选下推 SQL ----------------

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("day-detail：门店态多选落到 SQL（不是内存筛），并永远排除已删除行")
    void queryDayDetailPushesStoreStatusToSql() {
        when(demandManageMapper.selectVoList(any())).thenReturn(new ArrayList<>());

        service.queryDayDetail(5001L, LocalDate.of(2026, 8, 8), "白条", "SUBMITTED,CONFIRMED");

        ArgumentCaptor<LambdaQueryWrapper<DemandManage>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(demandManageMapper).selectVoList(captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertThat(sql)
            .contains("(demand_status = 'SUBMITTED')")
            .contains("(demand_status IN ('CONFIRMED','IN_PRODUCTION') AND received_time IS NULL)")
            .contains("OR")
            .contains("demand_status NOT IN")
            .contains("product_name LIKE");
    }

    @Test
    @DisplayName("day-detail：不传 storeStatuses → 不加状态条件，但仍排除已删除")
    void queryDayDetailWithoutStatusFilter() {
        when(demandManageMapper.selectVoList(any())).thenReturn(new ArrayList<>());

        service.queryDayDetail(5001L, LocalDate.of(2026, 8, 8), null, null);

        ArgumentCaptor<LambdaQueryWrapper<DemandManage>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(demandManageMapper).selectVoList(captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertThat(sql).doesNotContain("received_time");
        assertThat(sql).contains("demand_status NOT IN");
    }

    @Test
    @DisplayName("day-detail：回填门店态 + 产品品类 + 产品图")
    void queryDayDetailFillsDerivedFields() {
        DemandManageVo row = new DemandManageVo();
        row.setId(101L);
        row.setProductId(8001L);
        row.setDemandStatus("COMPLETED");
        row.setReceivedTime(LocalDateTime.of(2026, 8, 8, 9, 0));
        when(demandManageMapper.selectVoList(any())).thenReturn(new ArrayList<>(List.of(row)));

        ProductInfo p = new ProductInfo();
        p.setId(8001L);
        p.setBelongType("white_bar");
        p.setProductThumb("777");
        when(productInfoMapper.selectList(any())).thenReturn(List.of(p));
        when(imageUrlResolver.resolveList(any())).thenReturn(List.of("https://oss/whitebar.png"));

        List<DemandManageVo> rows = service.queryDayDetail(5001L, LocalDate.of(2026, 8, 8), null, null);

        assertThat(rows).hasSize(1);
        // COMPLETED + 已收货 → 门店态 ARRIVED
        assertThat(rows.get(0).getStoreDemandStatus()).isEqualTo("ARRIVED");
        assertThat(rows.get(0).getBelongType()).isEqualTo("white_bar");
        assertThat(rows.get(0).getImageUrl()).isEqualTo("https://oss/whitebar.png");
    }

    @Test
    @DisplayName("day-detail：入参 DELETED / 未知态直接报错，不静默返回空")
    void queryDayDetailRejectsDeletedFilter() {
        assertThatThrownBy(() -> service.queryDayDetail(5001L, LocalDate.of(2026, 8, 8), null, "DELETED"))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.queryDayDetail(5001L, LocalDate.of(2026, 8, 8), null, "WHATEVER"))
            .isInstanceOf(ServiceException.class);
        verify(demandManageMapper, never()).selectVoList(any());
    }

    @Test
    @DisplayName("day-detail：门店 / 日期缺失直接报错")
    void queryDayDetailRequiresStoreAndDate() {
        assertThatThrownBy(() -> service.queryDayDetail(null, LocalDate.now(), null, null))
            .isInstanceOf(ServiceException.class).hasMessageContaining("门店");
        assertThatThrownBy(() -> service.queryDayDetail(5001L, null, null, null))
            .isInstanceOf(ServiceException.class).hasMessageContaining("需求日期");
    }

    // ---------------- row68 catalog ----------------

    private ProductInfo product(long id, String name, String belongType, Integer attr) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductName(name);
        p.setBelongType(belongType);
        p.setProductType(1);
        p.setProductAttr(attr);
        p.setProductStatus(0);
        p.setProductUnit("份");
        return p;
    }

    /** 目录用桩：无图 / 无展示名覆盖 / 无作物统计 / 无历史下单，专测排序。 */
    private void stubCatalogNoExtras(List<ProductInfo> products) {
        when(productInfoMapper.selectList(any())).thenReturn(products);
        when(displayNameResolver.resolveDisplayNames(any())).thenReturn(Map.of());
        when(imageUrlResolver.resolveList(any())).thenReturn(products.stream().map(x -> (String) null).toList());
        when(cropPlotStatService.listPlotStat()).thenReturn(List.of());
        when(demandManageMapper.selectLastOrderTimeByStore(anyLong(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("catalog：两个排序键全为空时顺序仍确定（product_id 倒序收口），连查两次逐条相等")
    void catalogOrderIsDeterministicWhenKeysAllNull() {
        // 故意用「非 id 序」的入库顺序，且全部同品类、无历史下单、无采摘日
        List<ProductInfo> products = List.of(
            product(8003L, "干货C", "dry_good", 1),
            product(8001L, "干货A", "dry_good", 1),
            product(8002L, "干货B", "dry_good", 1));
        stubCatalogNoExtras(products);

        List<StoreDemandCatalogVo> first = service.queryCatalog(5001L, null);
        List<StoreDemandCatalogVo> second = service.queryCatalog(5001L, null);

        assertThat(first).extracting(StoreDemandCatalogVo::getProductId)
            .containsExactly(8003L, 8002L, 8001L);
        assertThat(second).extracting(StoreDemandCatalogVo::getProductId)
            .containsExactlyElementsOf(first.stream().map(StoreDemandCatalogVo::getProductId).toList());
    }

    @Test
    @DisplayName("catalog：果蔬按最早采摘日**距今距离**升序、无日期沉底；同组内再按 id 倒序")
    void catalogVegetableSortedByEarliestPickDate() {
        List<ProductInfo> products = List.of(
            product(8001L, "无采摘日菜", "vegetable", 1),
            product(8002L, "晚菜", "vegetable", 1),
            product(8003L, "早菜", "vegetable", 1),
            product(8004L, "也无采摘日", "vegetable", 1));
        stubCatalogNoExtras(products);
        when(cropPlotStatService.listPlotStat()).thenReturn(List.of(
            cropStat(8002L, LocalDate.now().plusDays(23).toString()),
            cropStat(8003L, LocalDate.now().plusDays(1).toString())));

        List<StoreDemandCatalogVo> list = service.queryCatalog(5001L, null);

        assertThat(list).extracting(StoreDemandCatalogVo::getProductId)
            .containsExactly(8003L, 8002L, 8004L, 8001L);
        assertThat(list.get(0).getEarliestPickDate()).isEqualTo(LocalDate.now().plusDays(1).toString());
        assertThat(list.get(2).getEarliestPickDate()).isNull();
    }

    @Test
    @DisplayName("catalog：同一产品被多条作物指向时取最早日期；加工果蔬回落原材料基础菜")
    void catalogVegetablePickDateTakesMinAndFallsBackToMaterial() {
        ProductInfo processed = product(8010L, "净菜甘蓝", "vegetable", 1);
        processed.setProductMaterial(9001L);   // 加工成品挂原材料基础菜
        List<ProductInfo> products = List.of(processed);
        stubCatalogNoExtras(products);
        when(cropPlotStatService.listPlotStat()).thenReturn(List.of(
            cropStat(9001L, "2026-09-20"),
            cropStat(9001L, "2026-08-15")));   // 同一基础产品两条作物 → 取 min

        List<StoreDemandCatalogVo> list = service.queryCatalog(5001L, null);
        assertThat(list.get(0).getEarliestPickDate()).isEqualTo("2026-08-15");
    }

    @Test
    @DisplayName("catalog：非果蔬按本店最近下单时间倒序、无记录沉底")
    void catalogNonVegetableSortedByLastOrderTime() {
        List<ProductInfo> products = List.of(
            product(8001L, "礼盒A", "gift_box", 1),
            product(8002L, "礼盒B", "gift_box", 1),
            product(8003L, "礼盒C", "gift_box", 1));
        stubCatalogNoExtras(products);
        when(demandManageMapper.selectLastOrderTimeByStore(anyLong(), any())).thenReturn(List.of(
            Map.of("productId", 8001L, "lastOrderTime", "2026-08-01 10:00"),
            Map.of("productId", 8003L, "lastOrderTime", "2026-08-07 20:15")));

        List<StoreDemandCatalogVo> list = service.queryCatalog(5001L, null);

        assertThat(list).extracting(StoreDemandCatalogVo::getProductId)
            .containsExactly(8003L, 8001L, 8002L);
        assertThat(list.get(0).getLastOrderTime()).isEqualTo("2026-08-07 20:15");
        assertThat(list.get(2).getLastOrderTime()).isNull();
    }

    @Test
    @DisplayName("catalog：品类分组序 = admin 购物车 7 tab 序（白条/猪肉/果蔬/干货/鸡蛋/礼盒/其他）")
    void catalogGroupedByBelongTypeOrder() {
        List<ProductInfo> products = List.of(
            product(8001L, "未知归属", null, 1),
            product(8002L, "礼盒", "gift_box", 1),
            product(8003L, "白条", "white_bar", 2),
            product(8004L, "青菜", "vegetable", 1));
        stubCatalogNoExtras(products);

        assertThat(service.queryCatalog(5001L, null)).extracting(StoreDemandCatalogVo::getBelongType)
            .containsExactly("white_bar", "vegetable", "gift_box", null);
    }

    @Test
    @DisplayName("catalog：落库业态 4 值映射（猪肉/干货/鸡蛋/未知 全部 → other）")
    void catalogDemandProductTypeMapping() {
        assertThat(StoreDemandAppletServiceImpl.toDemandProductType("white_bar")).isEqualTo("white_bar");
        assertThat(StoreDemandAppletServiceImpl.toDemandProductType("vegetable")).isEqualTo("vegetable");
        assertThat(StoreDemandAppletServiceImpl.toDemandProductType("gift_box")).isEqualTo("gift_box");
        assertThat(StoreDemandAppletServiceImpl.toDemandProductType("pork")).isEqualTo("other");
        assertThat(StoreDemandAppletServiceImpl.toDemandProductType("dry_good")).isEqualTo("other");
        assertThat(StoreDemandAppletServiceImpl.toDemandProductType("egg")).isEqualTo("other");
        assertThat(StoreDemandAppletServiceImpl.toDemandProductType(null)).isEqualTo("other");
    }

    @Test
    @DisplayName("catalog：展示名优先用 displayNameResolver 的结果，缺失回落产品名")
    void catalogUsesDisplayName() {
        List<ProductInfo> products = List.of(
            product(8001L, "有机小白菜", "vegetable", 1),
            product(8002L, "有机菠菜", "vegetable", 1));
        stubCatalogNoExtras(products);
        when(displayNameResolver.resolveDisplayNames(any())).thenReturn(Map.of(8001L, "小白菜(无证别名)"));

        // 两条都无采摘日 → 落到 product_id 倒序收口，8002 在前
        List<StoreDemandCatalogVo> list = service.queryCatalog(5001L, null);
        assertThat(list).extracting(StoreDemandCatalogVo::getProductId)
            .containsExactly(8002L, 8001L);
        assertThat(list).extracting(StoreDemandCatalogVo::getProductName)
            .containsExactly("有机菠菜", "小白菜(无证别名)");
    }

    @Test
    @DisplayName("catalog：门店必填；无候选产品返空 List 且不去查历史下单")
    void catalogGuards() {
        assertThatThrownBy(() -> service.queryCatalog(null, null))
            .isInstanceOf(ServiceException.class).hasMessageContaining("门店");

        when(productInfoMapper.selectList(any())).thenReturn(List.of());
        assertThat(service.queryCatalog(5001L, null)).isEmpty();
        verify(demandManageMapper, never()).selectLastOrderTimeByStore(anyLong(), any(Collection.class));
    }

    private CropPlotStatVo cropStat(Long relatedProduct, String earliestPickDate) {
        CropPlotStatVo vo = new CropPlotStatVo();
        vo.setRelatedProduct(relatedProduct);
        vo.setEarliestPickDate(earliestPickDate);
        return vo;
    }

    // ---------------- row70 改量 / 删行 ----------------

    private DemandManage demand(String status, LocalDateTime receivedTime) {
        DemandManage d = new DemandManage();
        d.setId(101L);
        d.setStoreId(5001L);
        d.setDemandNo("DEM-WB-20260808-001");
        d.setDemandStatus(status);
        d.setReceivedTime(receivedTime);
        return d;
    }

    private StoreDemandQuantityBo qtyBo(String qty) {
        StoreDemandQuantityBo bo = new StoreDemandQuantityBo();
        bo.setId(101L);
        bo.setDemandQuantity(new BigDecimal(qty));
        return bo;
    }

    @Test
    @DisplayName("改量：待确认行 + 数量 > 0 → 走 updateByBo，只 patch 数量，不触发删除")
    void updateQuantityHappyPath() {
        when(demandManageMapper.selectById(101L)).thenReturn(demand("SUBMITTED", null));

        service.updateQuantity(qtyBo("12.5"));

        ArgumentCaptor<DemandManageBo> captor = ArgumentCaptor.forClass(DemandManageBo.class);
        verify(demandManageService).updateByBo(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(101L);
        assertThat(captor.getValue().getDemandQuantity()).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(captor.getValue().getProductId()).isNull();
        verify(demandManageService, never()).deleteWithValidByIds(any());
    }

    @Test
    @DisplayName("删行：数量 = 0 → 走仓库既有删除路径（置 DELETED + 软删），不走 update")
    void updateQuantityZeroDeletes() {
        when(demandManageMapper.selectById(101L)).thenReturn(demand("SUBMITTED", null));

        service.updateQuantity(qtyBo("0"));

        verify(demandManageService).deleteWithValidByIds(eq(List.of(101L)));
        verify(demandManageService, never()).updateByBo(any());
    }

    @Test
    @DisplayName("非「待确认」行（已确认 / 已发货 / 已到店）一律拒绝改量与删除")
    void updateQuantityRejectsNonSubmitted() {
        when(demandManageMapper.selectById(101L)).thenReturn(demand("CONFIRMED", null));
        assertThatThrownBy(() -> service.updateQuantity(qtyBo("3")))
            .isInstanceOf(ServiceException.class).hasMessageContaining("待确认");

        when(demandManageMapper.selectById(101L)).thenReturn(demand("COMPLETED", null));
        assertThatThrownBy(() -> service.updateQuantity(qtyBo("3")))
            .isInstanceOf(ServiceException.class);

        when(demandManageMapper.selectById(101L)).thenReturn(demand("COMPLETED", LocalDateTime.now()));
        assertThatThrownBy(() -> service.updateQuantity(qtyBo("0")))
            .isInstanceOf(ServiceException.class);

        verify(demandManageService, never()).updateByBo(any());
        verify(demandManageService, never()).deleteWithValidByIds(any());
    }

    @Test
    @DisplayName("需求不存在 → 404；门店不在可见集合 → 403（开墙后自动收紧）")
    void updateQuantityGuards() {
        when(demandManageMapper.selectById(101L)).thenReturn(null);
        assertThatThrownBy(() -> service.updateQuantity(qtyBo("3")))
            .isInstanceOf(ServiceException.class).hasMessageContaining("不存在");

        when(demandManageMapper.selectById(101L)).thenReturn(demand("SUBMITTED", null));
        when(storeUserRelationService.isStoreAccessible(2001L, 5001L)).thenReturn(false);
        assertThatThrownBy(() -> service.updateQuantity(qtyBo("3")))
            .isInstanceOf(ServiceException.class).hasMessageContaining("无权");

        verify(demandManageService, never()).updateByBo(any());
    }

    // ---------------- row69 整单下单：mp 侧三道服务端闸 ----------------

    private StoreDemandBatchBo batchBo(LocalDate date, Long productId, String clientProductType) {
        StoreDemandBatchBo bo = new StoreDemandBatchBo();
        bo.setStoreId(9315000000000001L);
        bo.setDemandDate(date);
        StoreDemandBatchBo.Item item = new StoreDemandBatchBo.Item();
        item.setProductId(productId);
        item.setProductType(clientProductType);
        item.setDemandQuantity(new java.math.BigDecimal("2"));
        bo.setItems(new java.util.ArrayList<>(List.of(item)));
        return bo;
    }

    private ProductInfo product(Long id, String belongType, int attr) {
        // productType=1 自产：门店唯一可下单的类型（不设的话会被「外购不可下单」闸挡掉）
        return product(id, belongType, attr, 1);
    }

    private ProductInfo product(Long id, String belongType, int attr, Integer productType) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductName("测试产品" + id);
        p.setBelongType(belongType);
        p.setProductAttr(attr);
        p.setProductType(productType);
        return p;
    }

    @Test
    @DisplayName("row69 需求日期早于今天 → 拒（前端 min-date 基于设备本地时间，直连接口零阻力）")
    void batchCreate_rejectsPastDemandDate() {
        StoreDemandBatchBo bo = batchBo(LocalDate.now().minusDays(1), 9304000000000001L, "vegetable");
        ServiceException e = assertThrows(ServiceException.class, () -> service.batchCreate(bo));
        assertThat(e.getMessage()).contains("需求日期不能早于今天");
        verify(storeDemandService, never()).batchCreate(any());
    }

    @Test
    @DisplayName("row69 今天可下单（下界是「今天」不是「明天」）")
    void batchCreate_allowsToday() {
        when(productInfoMapper.selectList(any()))
            .thenReturn(List.of(product(9304000000000001L, "vegetable", 1)));
        when(storeDemandService.batchCreate(any())).thenReturn(1);
        assertThat(service.batchCreate(batchBo(LocalDate.now(), 9304000000000001L, "vegetable"))).isEqualTo(1);
    }

    @Test
    @DisplayName("row69 业态由服务端按 belong_type 推导，客户端传什么都被覆盖")
    void batchCreate_overridesClientProductType() {
        // 客户端把果蔬声明成 gift_box：不覆盖的话需求单号会拿到礼盒段 GB、下游发货也按礼盒筛
        when(productInfoMapper.selectList(any()))
            .thenReturn(List.of(product(9304000000000001L, "vegetable", 1)));
        when(storeDemandService.batchCreate(any())).thenReturn(1);
        StoreDemandBatchBo bo = batchBo(LocalDate.now(), 9304000000000001L, "gift_box");
        service.batchCreate(bo);
        ArgumentCaptor<StoreDemandBatchBo> captor = ArgumentCaptor.forClass(StoreDemandBatchBo.class);
        verify(storeDemandService).batchCreate(captor.capture());
        assertThat(captor.getValue().getItems().get(0).getProductType()).isEqualTo("vegetable");
    }

    @Test
    @DisplayName("row69 猪肉原材料被拒 —— 与目录候选谓词逐字相同（共用路径的黑名单漏了 pork）")
    void batchCreate_rejectsPorkRawMaterial() {
        when(productInfoMapper.selectList(any()))
            .thenReturn(List.of(product(9303000000000099L, "pork", 2)));
        StoreDemandBatchBo bo = batchBo(LocalDate.now(), 9303000000000099L, "other");
        ServiceException e = assertThrows(ServiceException.class, () -> service.batchCreate(bo));
        assertThat(e.getMessage()).contains("是原材料");
        verify(storeDemandService, never()).batchCreate(any());
    }

    @Test
    @DisplayName("row69 白条 / 礼盒 attr=2 豁免（门店现卖单位，与目录豁免一致）")
    void batchCreate_whiteBarAndGiftBoxExempt() {
        when(productInfoMapper.selectList(any()))
            .thenReturn(List.of(product(9301000000000001L, "white_bar", 2)));
        when(storeDemandService.batchCreate(any())).thenReturn(1);
        assertThat(service.batchCreate(batchBo(LocalDate.now(), 9301000000000001L, "white_bar"))).isEqualTo(1);
    }

    @Test
    @DisplayName("row69 产品不存在 → 404，且不落库")
    void batchCreate_rejectsUnknownProduct() {
        when(productInfoMapper.selectList(any())).thenReturn(List.of());
        StoreDemandBatchBo bo = batchBo(LocalDate.now(), 999999L, "other");
        ServiceException e = assertThrows(ServiceException.class, () -> service.batchCreate(bo));
        assertThat(e.getMessage()).contains("产品不存在");
        verify(storeDemandService, never()).batchCreate(any());
    }

    // ---------------- /add 与 /batch 必须同三道闸（独立验收：闸只装 batch 时 /add 一发全穿）----------------

    private DemandManageBo addBo(LocalDate date, Long productId, String clientProductType) {
        DemandManageBo bo = new DemandManageBo();
        bo.setStoreId(9315000000000001L);
        bo.setDemandDate(date);
        bo.setProductId(productId);
        bo.setProductName("测试产品");
        bo.setProductType(clientProductType);
        bo.setDemandQuantity(new java.math.BigDecimal("1"));
        return bo;
    }

    @Test
    @DisplayName("/add 昨天日期 → 拒（与 /batch 同闸）")
    void create_rejectsPastDemandDate() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.create(addBo(LocalDate.now().minusDays(1), 9304000000000001L, "vegetable")));
        assertThat(e.getMessage()).contains("需求日期不能早于今天");
        verify(storeDemandService, never()).createStoreDemand(any());
    }

    @Test
    @DisplayName("/add 猪肉原材料 → 拒（与 /batch 同闸）")
    void create_rejectsPorkRawMaterial() {
        when(productInfoMapper.selectById(9303000000000099L)).thenReturn(product(9303000000000099L, "pork", 2));
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.create(addBo(LocalDate.now(), 9303000000000099L, "other")));
        assertThat(e.getMessage()).contains("是原材料");
        verify(storeDemandService, never()).createStoreDemand(any());
    }

    @Test
    @DisplayName("/add 业态谎报被服务端改回（与 /batch 同闸）")
    void create_overridesClientProductType() {
        when(productInfoMapper.selectById(9304000000000001L)).thenReturn(product(9304000000000001L, "vegetable", 1));
        when(storeDemandService.createStoreDemand(any())).thenReturn(1L);
        DemandManageBo bo = addBo(LocalDate.now(), 9304000000000001L, "gift_box");
        service.create(bo);
        ArgumentCaptor<DemandManageBo> c = ArgumentCaptor.forClass(DemandManageBo.class);
        verify(storeDemandService).createStoreDemand(c.capture());
        assertThat(c.getValue().getProductType()).isEqualTo("vegetable");
    }

    @Test
    @DisplayName("/add 4 位小数 → 拒（不能让 MySQL 静默四舍五入）")
    void create_rejectsOverScaleQuantity() {
        when(productInfoMapper.selectById(9304000000000001L)).thenReturn(product(9304000000000001L, "vegetable", 1));
        DemandManageBo bo = addBo(LocalDate.now(), 9304000000000001L, "vegetable");
        bo.setDemandQuantity(new java.math.BigDecimal("1.2345"));
        ServiceException e = assertThrows(ServiceException.class, () -> service.create(bo));
        assertThat(e.getMessage()).contains("3 位小数");
        verify(storeDemandService, never()).createStoreDemand(any());
    }

    @Test
    @DisplayName("并单不跨需求类型：个人邮寄不得并进门店需求（否则 mailing 标记静默消失、发货地址会错）")
    void batchCreate_doesNotMergeAcrossDemandType() {
        when(productInfoMapper.selectList(any()))
            .thenReturn(List.of(product(9304000000000001L, "vegetable", 1)));
        when(storeDemandService.batchCreate(any())).thenReturn(1);
        StoreDemandBatchBo bo = batchBo(LocalDate.now(), 9304000000000001L, "vegetable");
        bo.getItems().get(0).setMailing(true);
        service.batchCreate(bo);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper> w =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(demandManageMapper).selectOne(w.capture());
        // 匹配条件里必须带 demand_type，且值是 mailing
        assertThat(w.getValue().getTargetSql()).contains("demand_type");
        assertThat(w.getValue().getParamNameValuePairs().values()).contains("mailing");
    }

    @Test
    @DisplayName("catalog：果蔬「最近采摘日」按**距今距离**，过去 60 天不得压过未来 3 天（纯日期升序会红）")
    void catalog_vegetableSortsByDistanceNotAscendingDate() {
        // 旧实现是日期升序 → 60 天前的排第一；新实现按 |日期−今天| → 未来 3 天更近，排第一。
        // staging 实测过这个形状：2026-06-04（距今 66 天）曾霸榜第 1，2026-08-06（距今 3 天）被压到第 7。
        String past60 = LocalDate.now().minusDays(60).toString();
        String future3 = LocalDate.now().plusDays(3).toString();
        List<ProductInfo> products = List.of(
            product(9001L, "远期已过菜", "vegetable", 1),
            product(9002L, "即将可采菜", "vegetable", 1));
        stubCatalogNoExtras(products);
        when(cropPlotStatService.listPlotStat()).thenReturn(List.of(
            cropStat(9001L, past60), cropStat(9002L, future3)));

        List<StoreDemandCatalogVo> list = service.queryCatalog(5001L, null);

        assertThat(list).extracting(StoreDemandCatalogVo::getProductId)
            .containsExactly(9002L, 9001L);
        assertThat(list.get(0).getEarliestPickDate()).isEqualTo(future3);
    }

    // ---------------- 独立验收补的闸（2026-08-11 clean-QA）----------------

    @Test
    @DisplayName("row70 已确认行不得走 /cancel 撤回 —— 改量端点拦住的行，换这个端点也必须拦住")
    void cancelSubmitted_rejectsConfirmedRow() {
        // 独立验收实测：同一 token，改量端点对已确认行返 400，紧接着打 /cancel 却 200，
        // 已确认行照样从详情页消失、日卡品数 -1、确认率归零。
        when(demandManageMapper.selectById(101L)).thenReturn(demand("CONFIRMED", null));
        assertThatThrownBy(() -> service.cancelSubmitted(101L, null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("仅「待确认」的需求可撤回")
            .hasMessageContaining("已确认");
        verify(demandStatusService, never()).transition(any(), any(), any(), any());
    }

    @Test
    @DisplayName("row70 待确认行可以撤回（闸不能把正常业务一起拦死）")
    void cancelSubmitted_allowsSubmittedRow() {
        when(demandManageMapper.selectById(101L)).thenReturn(demand("SUBMITTED", null));
        service.cancelSubmitted(101L, "不要了");
        verify(demandStatusService).transition(eq(101L), eq(DemandEvent.CANCEL), isNull(), eq("不要了"));
    }

    @Test
    @DisplayName("row70 无权门店的行不得撤回")
    void cancelSubmitted_rejectsForeignStore() {
        when(demandManageMapper.selectById(101L)).thenReturn(demand("SUBMITTED", null));
        when(storeUserRelationService.isStoreAccessible(any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.cancelSubmitted(101L, null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("无权操作该门店的需求");
        verify(demandStatusService, never()).transition(any(), any(), any(), any());
    }

    @Test
    @DisplayName("row70 产品名搜索转义 LIKE 元字符 —— 敲一个 % 不该等于「不筛」")
    void queryDayDetail_escapesLikeMetacharacters() {
        // 独立验收实测：store=…004 / 2026-08-06（当天 7 行），productName=`%` 或 `_` 都返 7 条 = 全返。
        when(demandManageMapper.selectVoList(any())).thenReturn(List.of());
        service.queryDayDetail(5001L, LocalDate.now(), "100%_纯", null);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper> w =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(demandManageMapper).selectVoList(w.capture());
        // 先触发 SQL 生成，参数才会 materialize 到 paramNameValuePairs
        assertThat(w.getValue().getTargetSql()).contains("product_name LIKE");
        assertThat(w.getValue().getParamNameValuePairs().values())
            .anyMatch(v -> String.valueOf(v).contains("100\\%\\_纯"));
    }

    @Test
    @DisplayName("row70 DRAFT 行不进门店详情 —— 否则「不筛看得到、四态全选反而筛不到」")
    void queryDayDetail_excludesDraftRows() {
        when(demandManageMapper.selectVoList(any())).thenReturn(List.of());
        service.queryDayDetail(5001L, LocalDate.now(), null, null);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper> w =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(demandManageMapper).selectVoList(w.capture());
        assertThat(w.getValue().getTargetSql()).contains("demand_status NOT IN");
        assertThat(w.getValue().getParamNameValuePairs().values()).contains("DRAFT", "DELETED", "CANCELLED");
    }

    @Test
    @DisplayName("row69 外购商品不可被门店下单 —— 这道闸原先只在单条落库路径上，整单下单没有")
    void batchCreate_rejectsOutsourcedProduct() {
        when(productInfoMapper.selectList(any()))
            .thenReturn(List.of(product(9304000000000001L, "vegetable", 1, 2)));
        assertThatThrownBy(() -> service.batchCreate(batchBo(LocalDate.now(), 9304000000000001L, "vegetable")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("外购商品，不可被门店下单");
        verify(storeDemandService, never()).batchCreate(any());
    }

    @Test
    @DisplayName("/add 同店同日同产品也并单（并单原先只装在 /batch，换个端点就能造出一天两条同名行）")
    void create_mergesSameDaySameProduct() {
        when(productInfoMapper.selectById(9304000000000001L))
            .thenReturn(product(9304000000000001L, "vegetable", 1));
        DemandManage exist = new DemandManage();
        exist.setId(777L);
        exist.setDemandNo("D-EXIST-001");
        exist.setDemandQuantity(new java.math.BigDecimal("3"));
        when(demandManageMapper.selectOne(any())).thenReturn(exist);

        DemandManageBo bo = new DemandManageBo();
        bo.setStoreId(5001L);
        bo.setDemandDate(LocalDate.now());
        bo.setProductId(9304000000000001L);
        bo.setDemandQuantity(new java.math.BigDecimal("2"));
        Long id = service.create(bo);

        assertThat(id).isEqualTo(777L);
        // 走并单 → 绝不能再新建一行
        verify(storeDemandService, never()).createStoreDemand(any());
        ArgumentCaptor<DemandManageBo> patch = ArgumentCaptor.forClass(DemandManageBo.class);
        verify(demandManageService).updateByBo(patch.capture());
        assertThat(patch.getValue().getDemandQuantity()).isEqualByComparingTo("5");
    }
}
