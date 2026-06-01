package org.dromara.djs.warehouse.flow.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.domain.query.StockFlowQuery;
import org.dromara.djs.warehouse.flow.domain.vo.PackingHomeVo;
import org.dromara.djs.warehouse.flow.domain.vo.PackingItemVo;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StockFlowServiceImpl} 单测（D11 WMS-FLOW-001）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>queryInList happy：强制 inout_type=IN 下推 wrapper（即便入参传 OT 也覆盖为 IN）+ JOIN 回填 productName</li>
 *   <li>queryPackingList：belong_type 强制 eq 'package'（不透传前端值，契约 14）</li>
 *   <li>queryPackingHome：4 KPI 聚合（今日入 / 今日出 / 种类数 / 最近盘点）+ null 兜底</li>
 * </ul>
 *
 * <p>MyBatis-Plus 单测 entity cache 预热（skill coder-mp-entity-cache-test）：buildWrapper /
 * fillJoinNames 内部 LambdaQueryWrapper 在 mock 路径下也会触发 TableInfoHelper 解析列名，
 * 必须先注册 StockFlow / ProductInfo / LocationInfo / LocationStock entity。</p>
 *
 * @author djs
 * @since WMS-FLOW-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StockFlowServiceImpl 单元测试")
class StockFlowServiceImplTest {

    @Mock
    private StockFlowMapper stockFlowMapper;

    @Mock
    private LocationInfoMapper locationInfoMapper;

    @Mock
    private ProductInfoMapper productInfoMapper;

    @Mock
    private LocationStockMapper locationStockMapper;

    private StockFlowServiceImpl service;

    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, StockFlow.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, LocationInfo.class);
        TableInfoHelper.initTableInfo(assistant, LocationStock.class);
    }

    @BeforeEach
    void setUp() {
        service = new StockFlowServiceImpl(stockFlowMapper, locationInfoMapper, productInfoMapper, locationStockMapper);
    }

    @Test
    @DisplayName("queryInList: happy → 强制 inout_type=IN 下推 + JOIN 回填 productName/locationName")
    void testQueryInList_ForceInAndFillNames() {
        StockFlowQuery query = new StockFlowQuery();
        query.setInoutType("OT"); // 故意传 OT，验证被覆盖为 IN
        PageQuery pageQuery = new PageQuery(1, 10);

        StockFlowVo vo = new StockFlowVo();
        vo.setId(70001L);
        vo.setInoutType("IN");
        vo.setProductId(50001L);
        vo.setWarehouseId(90001L);
        Page<StockFlowVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(stockFlowMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        ProductInfo p = new ProductInfo();
        p.setId(50001L);
        p.setProductName("塑料袋");
        p.setProductId("PROD-PKG-001");
        p.setBelongType("package");
        p.setProductUnit("个");
        when(productInfoMapper.selectList(any())).thenReturn(List.of(p));

        LocationInfo loc = new LocationInfo();
        loc.setId(90001L);
        loc.setLocationName("包材库A");
        when(locationInfoMapper.selectList(any())).thenReturn(List.of(loc));

        // 验证 inout_type 被锁为 IN（lockInout 副作用直接 set 入参 query）
        TableDataInfo<StockFlowVo> result = service.queryInList(query, pageQuery);

        assertThat(query.getInoutType()).as("入库页强制锁 inout_type=IN").isEqualTo("IN");
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getProductName()).as("回填产品名").isEqualTo("塑料袋");
        assertThat(result.getRows().get(0).getLocationName()).as("回填库位名").isEqualTo("包材库A");
    }

    @Test
    @DisplayName("queryPackingList: belong_type 强制 eq 'package'（不透传前端值，契约 14）")
    void testQueryPackingList_ForcePackageBelongType() {
        PackingItemVo item = new PackingItemVo();
        item.setProductId(50001L);
        item.setProductName("纸箱");
        item.setCurrentStock(new BigDecimal("120"));
        when(locationStockMapper.selectPackingItems(any(), any())).thenReturn(List.of(item));

        List<PackingItemVo> result = service.queryPackingList("stock");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductName()).isEqualTo("纸箱");
        // 关键断言：mapper 第一参恒为 'package'，sortBy 透传 'stock'
        ArgumentCaptor<String> belongCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        verify(locationStockMapper, times(1)).selectPackingItems(belongCaptor.capture(), sortCaptor.capture());
        assertThat(belongCaptor.getValue()).as("belong_type 后端强制 package").isEqualTo("package");
        assertThat(sortCaptor.getValue()).as("sortBy 透传").isEqualTo("stock");
    }

    @Test
    @DisplayName("queryPackingHome: 4 KPI 聚合 + null 兜底")
    void testQueryPackingHome_AggregateKpis() {
        when(stockFlowMapper.sumTodayByInoutBelongType(eq("IN"), eq("package"))).thenReturn(new BigDecimal("30"));
        when(stockFlowMapper.sumTodayByInoutBelongType(eq("OT"), eq("package"))).thenReturn(null); // 验证兜底 0
        when(locationStockMapper.countProductsByBelongType(eq("package"))).thenReturn(5L);
        Date checkTime = new Date();
        when(locationStockMapper.selectLatestCheckTimeByBelongType(eq("package"))).thenReturn(checkTime);

        PackingHomeVo vo = service.queryPackingHome();

        assertThat(vo.getTodayInQuantity()).isEqualByComparingTo("30");
        assertThat(vo.getTodayOutQuantity()).as("null SUM 兜底 0").isEqualByComparingTo("0");
        assertThat(vo.getPackTypeCount()).isEqualTo(5L);
        assertThat(vo.getLatestCheckTime()).isEqualTo(checkTime);
    }

}
