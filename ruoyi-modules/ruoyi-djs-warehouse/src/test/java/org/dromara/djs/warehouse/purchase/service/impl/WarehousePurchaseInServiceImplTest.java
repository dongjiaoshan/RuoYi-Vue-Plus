package org.dromara.djs.warehouse.purchase.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.purchase.domain.query.PurchaseInProductQuery;
import org.dromara.djs.warehouse.purchase.domain.vo.PurchaseInProductVo;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WarehousePurchaseInServiceImpl} 单测（WMS-OUTSOURCE-001 商品维度聚合查询）。
 *
 * <p>覆盖 {@code queryProductPageList} happy path：mapper 返聚合行 → service 透传 monthInTotal /
 * lastPurchaserId + imageUrl 经 resolver 回填。storeLocationId 用 null 避开 LambdaWrapper（不需 TableInfo 预热）。</p>
 *
 * @author djs
 * @since WMS-OUTSOURCE-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WarehousePurchaseInServiceImpl 单元测试")
class WarehousePurchaseInServiceImplTest {

    @Mock private StockFlowMapper stockFlowMapper;
    @Mock private LocationStockMapper locationStockMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private LocationInfoMapper locationInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private ImageUrlResolver imageUrlResolver;

    private WarehousePurchaseInServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WarehousePurchaseInServiceImpl(
            stockFlowMapper, locationStockMapper, productInfoMapper, locationInfoMapper,
            bizCodeGenerator, imageUrlResolver);
    }

    @Test
    @DisplayName("queryProductPageList happy：聚合行 monthInTotal/lastPurchaserId 透传 + imageUrl 经 resolver 回填")
    void testQueryProductPageList_Happy() {
        PurchaseInProductVo row = new PurchaseInProductVo();
        row.setProductId(70001L);
        row.setProductName("外购原料A");
        row.setBuyClass("raw");
        row.setImageOssId("oss-raw-id");
        // storeLocationId 留 null → 跳过 LambdaWrapper 库位名回填分支（无需 TableInfo 预热）
        row.setStoreLocationId(null);
        row.setCurrentStock(new BigDecimal("88.00"));
        row.setLastPurchaserId(9001L);
        row.setMonthInTotal(new BigDecimal("150.00"));

        // mapper 返回填好 records 的 page；service 直接复用
        when(productInfoMapper.selectPurchaseInProductPage(any(), any()))
            .thenAnswer(inv -> {
                Page<PurchaseInProductVo> p = inv.getArgument(0);
                p.setRecords(new java.util.ArrayList<>(List.of(row)));
                p.setTotal(1);
                return p;
            });
        when(imageUrlResolver.resolveList(any())).thenReturn(List.of("https://oss/url-raw.png"));

        TableDataInfo<PurchaseInProductVo> result =
            service.queryProductPageList(new PurchaseInProductQuery(), new PageQuery(10, 1));

        assertThat(result.getRows()).hasSize(1);
        PurchaseInProductVo vo = result.getRows().get(0);
        // monthInTotal / lastPurchaserId 聚合透传
        assertThat(vo.getMonthInTotal()).isEqualByComparingTo("150.00");
        assertThat(vo.getLastPurchaserId()).isEqualTo(9001L);
        // imageUrl 经 resolver 回填
        assertThat(vo.getImageUrl()).isEqualTo("https://oss/url-raw.png");
        verify(imageUrlResolver, times(1)).resolveList(any());
    }

    @Test
    @DisplayName("queryProductPageList 空结果：mapper 返空 records → 不调 resolver + 返空 rows")
    void testQueryProductPageList_Empty() {
        when(productInfoMapper.selectPurchaseInProductPage(any(), any()))
            .thenAnswer(inv -> {
                Page<PurchaseInProductVo> p = inv.getArgument(0);
                p.setRecords(new java.util.ArrayList<>());
                p.setTotal(0);
                return p;
            });

        TableDataInfo<PurchaseInProductVo> result =
            service.queryProductPageList(new PurchaseInProductQuery(), new PageQuery(10, 1));

        assertThat(result.getRows()).isEmpty();
        verify(imageUrlResolver, times(0)).resolveList(any());
    }

}
