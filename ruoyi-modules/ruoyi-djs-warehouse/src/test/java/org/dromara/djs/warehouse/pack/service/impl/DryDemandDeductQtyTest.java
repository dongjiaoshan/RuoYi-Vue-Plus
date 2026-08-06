package org.dromara.djs.warehouse.pack.service.impl;

import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ProductProductionServiceImpl#resolveDryDemandDeductQty} 单测（V6 row34）。
 *
 * <p>甲方 2026-08-06：「其他产品打包」非 KG 成品，需求扣减量 = 前端回传的**打包量**，
 * 而非原口径的恒 1 份（现场表现：原需求 4 枚，输入 4，需求只减 1 枚）。</p>
 *
 * <p>回落分支同样要钉死：重量模式 / 肉品页 / 老客户端不回传 packQuantity，
 * 必须仍扣 1 份 —— 若误用 productWeight（500 g 的 500）会把需求瞬间清零。</p>
 *
 * @author djs
 * @since V6-R34
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("其他产品打包：需求扣减量 = 打包量（V6 row34）")
class DryDemandDeductQtyTest {

    @Mock private ProductProductionMapper productionMapper;
    @Mock private ProductInhouseMapper inhouseMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private LocationInfoMapper locationInfoMapper;
    @Mock private LocationStockMapper locationStockMapper;
    @Mock private StockFlowMapper stockFlowMapper;
    @Mock private org.dromara.djs.common.store.mapper.StoreMapper storeMapper;
    @Mock private org.dromara.djs.plant.plot.mapper.PlotInfoMapper plotInfoMapper;
    @Mock private DemandManageMapper demandManageMapper;
    @Mock private org.dromara.djs.warehouse.cross.mapper.BarInfoMapper barInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private org.dromara.djs.warehouse.trace.service.ITraceService traceService;
    @Mock private org.dromara.djs.warehouse.check.service.IStockCheckService stockCheckService;

    private ProductProductionServiceImpl service;

    private static ProductInfo egg() {
        ProductInfo p = new ProductInfo();
        p.setId(8001L);
        p.setProductName("鸡蛋");
        p.setProductUnit("枚");
        return p;
    }

    @BeforeEach
    void setup() {
        service = new ProductProductionServiceImpl(
            productionMapper, inhouseMapper, productInfoMapper,
            locationInfoMapper, locationStockMapper, stockFlowMapper, storeMapper, plotInfoMapper,
            demandManageMapper, barInfoMapper, bizCodeGenerator, traceService, stockCheckService);
    }

    @Test
    @DisplayName("回传打包量 4 → 扣 4（甲方原场景：需求 4 枚，输入 4，应减到 0 而非 3）")
    void packQuantityPresent_deductsThatAmount() {
        assertThat(service.resolveDryDemandDeductQty(egg(), new BigDecimal("4")))
            .isEqualByComparingTo(new BigDecimal("4"));
    }

    @Test
    @DisplayName("份数模式回传的是份数（2 份礼盒）→ 扣 2 份，不是 60 枚原材料")
    void packQuantityIsCopiesNotMaterial_deductsCopies() {
        assertThat(service.resolveDryDemandDeductQty(egg(), new BigDecimal("2")))
            .isEqualByComparingTo(new BigDecimal("2"));
    }

    @Test
    @DisplayName("未回传打包量（重量模式 / 肉品页 / 老客户端）→ 回落扣 1 份")
    void packQuantityNull_fallsBackToOne() {
        assertThat(service.resolveDryDemandDeductQty(egg(), null))
            .isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("打包量为 0 / 负数（脏数据）→ 回落扣 1 份，不产生 0 扣减或负扣减")
    void packQuantityNonPositive_fallsBackToOne() {
        assertThat(service.resolveDryDemandDeductQty(egg(), BigDecimal.ZERO))
            .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(service.resolveDryDemandDeductQty(egg(), new BigDecimal("-3")))
            .isEqualByComparingTo(BigDecimal.ONE);
    }
}
