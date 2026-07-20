package org.dromara.djs.warehouse.flow.controller.applet;

import org.dromara.common.core.domain.R;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.warehouse.flow.domain.vo.FeedStockVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatTodaySummaryVo;
import org.dromara.djs.warehouse.flow.service.IMatFlowService;
import org.dromara.djs.warehouse.flow.service.IStockFlowService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link AppletFeedController#stock()} 单测（D12X-MP-FEED-LIST-001）。
 *
 * <p>验证饲料库存列表卡 enrich 「今日已领 / 今日已退」：</p>
 * <ol>
 *   <li>每个 FeedStockVo 的 todayPicked / todayReturned 取自 matFlowService.todaySummary("feed", pid)
 *       的 pickedQuantity / returnedQuantity（不为 null）</li>
 *   <li>多产品各自按 productId 单独 enrich（不串值）</li>
 *   <li>无流水产品 today 字段为 0（empty VO 兜底）</li>
 * </ol>
 *
 * <p>仅 mock 4 个依赖，不起 Spring 上下文。</p>
 *
 * @author djs
 * @since D12X-MP-FEED-LIST-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppletFeedController.stock() 今日已领/已退 enrich 单元测试")
class AppletFeedControllerTest {

    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private LocationStockMapper locationStockMapper;
    @Mock private IStockFlowService stockFlowService;
    @Mock private IMatFlowService matFlowService;
    @Mock private ImageUrlResolver imageUrlResolver;

    private AppletFeedController controller;

    private static final Long P1 = 8001L;
    private static final Long P2 = 8002L;

    @BeforeEach
    void setup() {
        controller = new AppletFeedController(
            productInfoMapper, locationStockMapper, stockFlowService, matFlowService, imageUrlResolver);
    }

    private static ProductInfo product(Long id, String code, String name) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductId(code);
        p.setProductName(name);
        p.setProductUnit("kg");
        return p;
    }

    private static LocationStock stock(Long productId, String num) {
        LocationStock s = new LocationStock();
        s.setProductId(productId);
        s.setProductStock(new BigDecimal(num));
        return s;
    }

    private static MatTodaySummaryVo summary(String picked, String returned) {
        MatTodaySummaryVo vo = new MatTodaySummaryVo();
        vo.setPickedQuantity(new BigDecimal(picked));
        vo.setReturnedQuantity(new BigDecimal(returned));
        vo.setLossQuantity(BigDecimal.ZERO);
        return vo;
    }

    @Test
    @DisplayName("每个产品 enrich todayPicked/todayReturned，多产品各取各值不串")
    void stockEnrichTodayPerProduct() {
        when(productInfoMapper.selectList(any())).thenReturn(List.of(
            product(P1, "PROD-FEED-CORN-01", "玉米"),
            product(P2, "PROD-FEED-SOY-02", "大豆")));
        when(locationStockMapper.selectList(any())).thenReturn(List.of(
            stock(P1, "1000"), stock(P2, "500")));
        // P1 今日领 30 退 5；P2 今日领 0 退 0（无流水 → empty 兜底）
        when(matFlowService.todaySummary(eq("feed"), eq(String.valueOf(P1)))).thenReturn(summary("30", "5"));
        when(matFlowService.todaySummary(eq("feed"), eq(String.valueOf(P2)))).thenReturn(MatTodaySummaryVo.empty());

        R<List<FeedStockVo>> r = controller.stock();
        assertThat(r.getCode()).isEqualTo(200);
        List<FeedStockVo> vos = r.getData();
        assertThat(vos).hasSize(2);

        FeedStockVo corn = vos.stream().filter(v -> P1.equals(v.getProductId())).findFirst().orElseThrow();
        assertThat(corn.getTotalStock()).isEqualByComparingTo("1000");
        assertThat(corn.getTodayPicked()).isEqualByComparingTo("30");
        assertThat(corn.getTodayReturned()).isEqualByComparingTo("5");

        FeedStockVo soy = vos.stream().filter(v -> P2.equals(v.getProductId())).findFirst().orElseThrow();
        // 无流水产品 today 字段为 0（非 null）
        assertThat(soy.getTodayPicked()).isEqualByComparingTo("0");
        assertThat(soy.getTodayReturned()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("无饲料产品时返回空列表，不调 todaySummary")
    void stockEmptyWhenNoProduct() {
        when(productInfoMapper.selectList(any())).thenReturn(List.of());
        R<List<FeedStockVo>> r = controller.stock();
        assertThat(r.getData()).isEmpty();
    }
}
