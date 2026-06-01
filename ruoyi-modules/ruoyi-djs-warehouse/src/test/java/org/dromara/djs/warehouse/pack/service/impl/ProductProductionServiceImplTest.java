package org.dromara.djs.warehouse.pack.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.domain.bo.CeleryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.DryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.GiftPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.VegPackBo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.GiftBox;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.GiftBoxMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProductProductionServiceImpl} 单测（WMS-PACK-001）。
 *
 * <p>覆盖 4 业态打包跨表事务核心场景：</p>
 * <ol>
 *   <li>vegPack happy：来源 inhouse 存在 + 目标 product 是发货品 → INSERT production + stock_flow + location_stock += + softDelete inhouse</li>
 *   <li>vegPack 来源不存在 → 抛 + 不动 production</li>
 *   <li>vegPack 目标产品非发货品 → 抛 + 不动 production</li>
 *   <li>giftPack happy：礼盒 SKU 存在 + gift_box 配置 2 组件 → N+1 stock_flow + location_stock 多次扣减</li>
 *   <li>giftPack 组件未配 → 抛友好提示</li>
 *   <li>giftPack 组件库存不足 → 抛 + 事务回滚</li>
 *   <li>dryPack happy：写 produce_no H 前缀 + 自定义单位</li>
 *   <li>celeryPack happy：自动写 productSpec='按重量'</li>
 *   <li>produceNo 委托 IBizCodeGenerator PRODUCE_NO（按 belong_type 传业态前缀）</li>
 * </ol>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductProductionServiceImpl 单元测试")
class ProductProductionServiceImplTest {

    @Mock private ProductProductionMapper productionMapper;
    @Mock private ProductInhouseMapper inhouseMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private GiftBoxMapper giftBoxMapper;
    @Mock private LocationInfoMapper locationInfoMapper;
    @Mock private LocationStockMapper locationStockMapper;
    @Mock private StockFlowMapper stockFlowMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;

    private ProductProductionServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeEach
    void setup() {
        service = new ProductProductionServiceImpl(
            productionMapper, inhouseMapper, productInfoMapper, giftBoxMapper,
            locationInfoMapper, locationStockMapper, stockFlowMapper, bizCodeGenerator);

        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(9001L);

        // BizCodeType.STOCK_FLOW_NO 默认 stub
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap()))
            .thenReturn("F2606280001");

        // BizCodeType.PRODUCE_NO 默认 stub：回显 context.prefix，模拟 {yyMMdd}{prefix}0001
        when(bizCodeGenerator.generate(eq(BizCodeType.PRODUCE_NO), anyMap())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> ctx = inv.getArgument(1);
            Object prefix = ctx.get("prefix");
            return "260628" + prefix + "0001";
        });
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    // ============================================================
    // veg pack
    // ============================================================

    private ProductInhouse sampleVegSource() {
        ProductInhouse i = new ProductInhouse();
        i.setId(70001L);
        i.setProductId(60001L);
        i.setProductName("毛菜·番茄");
        i.setProductType(1);
        i.setProductUnit("kg");
        i.setProductWeight(new BigDecimal("50.000"));
        i.setPlotId(50001L);
        return i;
    }

    private ProductInfo sampleVegProduct() {
        ProductInfo p = new ProductInfo();
        p.setId(60010L);
        p.setProductId("PROD-VEG-TOMATO-01");
        p.setProductName("番茄·小盒装");
        p.setProductType(1);
        p.setProductUnit("kg");
        p.setProductSpec("500g/盒");
        p.setBelongType("vegetable");
        p.setIsDelivery(1);
        return p;
    }

    private LocationInfo sampleLocation() {
        LocationInfo l = new LocationInfo();
        l.setId(90001L);
        l.setLocationName("蔬菜鲜品库");
        return l;
    }

    private VegPackBo sampleVegBo() {
        VegPackBo bo = new VegPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(60010L);
        bo.setProductWeight(new BigDecimal("30.500"));
        bo.setLocationId(90001L);
        bo.setMaterialConsume(new BigDecimal("0.5"));
        bo.setRemark("e2e veg pack");
        return bo;
    }

    @Test
    @DisplayName("submitVegPack: happy → INSERT production + stock_flow + location_stock += + softDelete inhouse")
    void testVegPack_Happy() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        when(productInfoMapper.selectById(60010L)).thenReturn(sampleVegProduct());
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ProductProduction p = inv.getArgument(0);
            p.setId(80001L);
            return 1;
        });
        when(locationStockMapper.addByProductLocation(eq(90001L), eq(60010L), any(), eq(9001L))).thenReturn(1);
        when(inhouseMapper.deleteById(eq(70001L))).thenReturn(1);

        Long id = service.submitVegPack(sampleVegBo());

        assertThat(id).isEqualTo(80001L);
        // 验证 production 字段
        ArgumentCaptor<ProductProduction> cap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper, times(1)).insert(cap.capture());
        ProductProduction saved = cap.getValue();
        assertThat(saved.getProductId()).isEqualTo(60010L);
        assertThat(saved.getProductName()).isEqualTo("番茄·小盒装");
        assertThat(saved.getProductWeight()).isEqualByComparingTo("30.500");
        assertThat(saved.getPlotId()).isEqualTo(50001L);
        assertThat(saved.getPackStatus()).isEqualTo("packed");
        assertThat(saved.getProduceNo()).startsWith(""); // yyMMdd + G + 0001
        assertThat(saved.getProduceNo()).endsWith("0001");
        assertThat(saved.getProduceNo()).contains("G");
        assertThat(saved.getIsDeliveryCheck()).isEqualTo(0);
        // 验证 stock_flow 调一次入库
        verify(stockFlowMapper, times(1)).insert(any(StockFlow.class));
        // 验证 location_stock += weight
        verify(locationStockMapper, times(1)).addByProductLocation(
            eq(90001L), eq(60010L), any(), eq(9001L));
        // 验证 inhouse 软删
        verify(inhouseMapper, times(1)).deleteById(eq(70001L));
    }

    @Test
    @DisplayName("submitVegPack: 来源不存在 → 抛 + production 不 INSERT")
    void testVegPack_SourceMissing() {
        when(inhouseMapper.selectById(70001L)).thenReturn(null);

        assertThatThrownBy(() -> service.submitVegPack(sampleVegBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("来源过程产品不存在");

        verify(productionMapper, never()).insert(any(ProductProduction.class));
    }

    @Test
    @DisplayName("submitVegPack: 目标产品 is_delivery!=1 → 抛")
    void testVegPack_NotDeliveryProduct() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        ProductInfo p = sampleVegProduct();
        p.setIsDelivery(0);
        when(productInfoMapper.selectById(60010L)).thenReturn(p);

        assertThatThrownBy(() -> service.submitVegPack(sampleVegBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不是发货品");

        verify(productionMapper, never()).insert(any(ProductProduction.class));
    }

    // ============================================================
    // gift pack
    // ============================================================

    private ProductInfo sampleGiftProduct() {
        ProductInfo p = new ProductInfo();
        p.setId(60100L);
        p.setProductId("PROD-GIFT-A-01");
        p.setProductName("精品礼盒 A");
        p.setProductType(3); // 礼盒
        p.setProductUnit("盒");
        p.setBelongType("gift_box");
        p.setIsDelivery(1);
        return p;
    }

    private GiftBox sampleComponent(Long componentProductId, BigDecimal count, String unit) {
        GiftBox c = new GiftBox();
        c.setBoxProductId(60100L);
        c.setComponentProductId(componentProductId);
        c.setComponentCount(count);
        c.setComponentUnit(unit);
        return c;
    }

    private GiftPackBo sampleGiftBo() {
        GiftPackBo bo = new GiftPackBo();
        bo.setGiftBoxProductId(60100L);
        bo.setPackBoxCount(5);
        bo.setLocationId(90100L);
        return bo;
    }

    @Test
    @DisplayName("submitGiftPack: happy 2 组件 → location_stock 扣 2 次 + stock_flow 写 3 行（2 出 + 1 入）+ production INSERT")
    void testGiftPack_Happy() {
        when(productInfoMapper.selectById(60100L)).thenReturn(sampleGiftProduct());
        when(locationInfoMapper.selectById(90100L)).thenReturn(sampleLocation());
        when(giftBoxMapper.selectList(any())).thenReturn(List.of(
            sampleComponent(60001L, new BigDecimal("2.000"), "kg"),
            sampleComponent(60002L, new BigDecimal("1.000"), "包")
        ));
        when(locationStockMapper.deductByProductLocation(eq(90100L), eq(60001L), any(), eq(9001L))).thenReturn(1);
        when(locationStockMapper.deductByProductLocation(eq(90100L), eq(60002L), any(), eq(9001L))).thenReturn(1);
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ProductProduction p = inv.getArgument(0);
            p.setId(80100L);
            return 1;
        });
        when(locationStockMapper.addByProductLocation(eq(90100L), eq(60100L), any(), eq(9001L))).thenReturn(1);

        Long id = service.submitGiftPack(sampleGiftBo());

        assertThat(id).isEqualTo(80100L);
        verify(locationStockMapper, times(2)).deductByProductLocation(
            anyLong(), anyLong(), any(), anyLong());
        verify(stockFlowMapper, times(3)).insert(any(StockFlow.class)); // 2 consume + 1 in
        verify(locationStockMapper, times(1)).addByProductLocation(
            eq(90100L), eq(60100L), any(), eq(9001L));
        ArgumentCaptor<ProductProduction> cap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper).insert(cap.capture());
        ProductProduction saved = cap.getValue();
        assertThat(saved.getProductId()).isEqualTo(60100L);
        assertThat(saved.getProductType()).isEqualTo(3);
        assertThat(saved.getProductWeight()).isEqualByComparingTo("5"); // packBoxCount
        assertThat(saved.getProduceNo()).contains("L"); // 礼盒前缀
    }

    @Test
    @DisplayName("submitGiftPack: gift_box 组件未配 → 抛友好提示")
    void testGiftPack_NoComponents() {
        when(productInfoMapper.selectById(60100L)).thenReturn(sampleGiftProduct());
        when(locationInfoMapper.selectById(90100L)).thenReturn(sampleLocation());
        when(giftBoxMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitGiftPack(sampleGiftBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("礼盒未配置组件清单");

        verify(productionMapper, never()).insert(any(ProductProduction.class));
    }

    @Test
    @DisplayName("submitGiftPack: 组件库存不足 → 抛 + 事务回滚（production 不 insert）")
    void testGiftPack_ComponentStockInsufficient() {
        when(productInfoMapper.selectById(60100L)).thenReturn(sampleGiftProduct());
        when(locationInfoMapper.selectById(90100L)).thenReturn(sampleLocation());
        when(giftBoxMapper.selectList(any())).thenReturn(List.of(
            sampleComponent(60001L, new BigDecimal("2.000"), "kg")
        ));
        when(locationStockMapper.deductByProductLocation(eq(90100L), eq(60001L), any(), eq(9001L))).thenReturn(0);

        assertThatThrownBy(() -> service.submitGiftPack(sampleGiftBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("组件库存不足");

        verify(productionMapper, never()).insert(any(ProductProduction.class));
    }

    @Test
    @DisplayName("submitGiftPack: 产品 product_type != 3 → 抛")
    void testGiftPack_NotGiftType() {
        ProductInfo p = sampleGiftProduct();
        p.setProductType(1); // 自产，不是礼盒
        when(productInfoMapper.selectById(60100L)).thenReturn(p);

        assertThatThrownBy(() -> service.submitGiftPack(sampleGiftBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("产品不是礼盒类型");
    }

    // ============================================================
    // dry pack
    // ============================================================

    @Test
    @DisplayName("submitDryPack: happy → produceNo H 前缀 + 自定义单位")
    void testDryPack_Happy() {
        ProductInhouse src = sampleVegSource();
        src.setPlotId(null);
        when(inhouseMapper.selectById(70001L)).thenReturn(src);
        ProductInfo p = sampleVegProduct();
        p.setBelongType("dry_good");
        p.setProductName("干货·腊肉");
        when(productInfoMapper.selectById(60010L)).thenReturn(p);
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ProductProduction pp = inv.getArgument(0);
            pp.setId(80200L);
            return 1;
        });
        when(locationStockMapper.addByProductLocation(any(), any(), any(), any())).thenReturn(1);
        when(inhouseMapper.deleteById(any())).thenReturn(1);

        DryPackBo bo = new DryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(60010L);
        bo.setProductWeight(new BigDecimal("12.000"));
        bo.setProductUnit("个");
        bo.setLocationId(90001L);

        Long id = service.submitDryPack(bo);

        assertThat(id).isEqualTo(80200L);
        ArgumentCaptor<ProductProduction> cap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper).insert(cap.capture());
        ProductProduction saved = cap.getValue();
        assertThat(saved.getProductUnit()).isEqualTo("个");
        assertThat(saved.getProduceNo()).contains("H");
    }

    // ============================================================
    // celery pack
    // ============================================================

    @Test
    @DisplayName("submitCeleryPack: happy → 自动写 productSpec='按重量' + G 前缀")
    void testCeleryPack_Happy() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        when(productInfoMapper.selectById(60010L)).thenReturn(sampleVegProduct());
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ProductProduction p = inv.getArgument(0);
            p.setId(80300L);
            return 1;
        });
        when(locationStockMapper.addByProductLocation(any(), any(), any(), any())).thenReturn(1);
        when(inhouseMapper.deleteById(any())).thenReturn(1);

        CeleryPackBo bo = new CeleryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(60010L);
        bo.setProductWeight(new BigDecimal("20.500"));
        bo.setLocationId(90001L);

        Long id = service.submitCeleryPack(bo);

        assertThat(id).isEqualTo(80300L);
        ArgumentCaptor<ProductProduction> cap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper).insert(cap.capture());
        ProductProduction saved = cap.getValue();
        assertThat(saved.getProductSpec()).isEqualTo("按重量");
        assertThat(saved.getProduceNo()).contains("G"); // 果蔬前缀
    }

    // ============================================================
    // produceNo 生成委托 IBizCodeGenerator（PRODUCE_NO + 业态前缀）
    // ============================================================

    @Test
    @DisplayName("produceNo: 委托 IBizCodeGenerator PRODUCE_NO，按 belong_type 传业态前缀")
    void testProduceNo_DelegatesToBizCodeGenerator() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        when(productInfoMapper.selectById(60010L)).thenReturn(sampleVegProduct());
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ProductProduction p = inv.getArgument(0);
            p.setId(80999L);
            return 1;
        });
        when(locationStockMapper.addByProductLocation(any(), any(), any(), any())).thenReturn(1);
        when(inhouseMapper.deleteById(any())).thenReturn(1);

        Long id = service.submitVegPack(sampleVegBo());

        assertThat(id).isEqualTo(80999L);
        ArgumentCaptor<ProductProduction> cap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper).insert(cap.capture());
        // produceNo 由 generator 生成（mock 回显 vegetable→G 前缀）
        assertThat(cap.getValue().getProduceNo()).contains("G").endsWith("0001");
        // 校验确实走 PRODUCE_NO 规则且传了正确业态前缀
        ArgumentCaptor<Map<String, Object>> ctxCap = ArgumentCaptor.forClass(Map.class);
        verify(bizCodeGenerator).generate(eq(BizCodeType.PRODUCE_NO), ctxCap.capture());
        assertThat(ctxCap.getValue()).containsEntry("prefix", "G");
    }

}
