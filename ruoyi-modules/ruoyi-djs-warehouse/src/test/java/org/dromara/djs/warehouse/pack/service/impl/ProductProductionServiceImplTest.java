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
import org.dromara.djs.warehouse.pack.domain.bo.WhiteBarOutBo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.GiftBox;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.GiftBoxMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
    @Mock private org.dromara.djs.common.store.mapper.StoreMapper storeMapper;
    @Mock private org.dromara.djs.plant.plot.mapper.PlotInfoMapper plotInfoMapper;
    @Mock private org.dromara.djs.warehouse.demand.mapper.DemandManageMapper demandManageMapper;
    @Mock private org.dromara.djs.warehouse.cross.mapper.BarInfoMapper barInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private org.dromara.djs.warehouse.trace.service.ITraceService traceService;
    @Mock private org.dromara.djs.warehouse.check.service.IStockCheckService stockCheckService;

    private ProductProductionServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeAll
    static void initMpEntityCache() {
        // MyBatis-Plus 单测 entity cache 预热（coder-mp-entity-cache-test）：
        // checkVegMaterialIfConfigured/deductPorkMaterialIfConfigured → sumProductStock 用
        // LambdaQueryWrapper<LocationStock>；listSourceForVeg/Dry 用 LambdaQueryWrapper<ProductInfo>/<ProductInhouse>。
        // 无 Spring 上下文时 TableInfoHelper 解析不到 lambda 列名 → 预热三类实体。
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, LocationStock.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, ProductInhouse.class);
    }

    @BeforeEach
    void setup() {
        service = new ProductProductionServiceImpl(
            productionMapper, inhouseMapper, productInfoMapper, giftBoxMapper,
            locationInfoMapper, locationStockMapper, stockFlowMapper, storeMapper, plotInfoMapper,
            demandManageMapper, barInfoMapper, bizCodeGenerator, traceService, stockCheckService);

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

        // consumeInhouse 部分扣减（来源余量 > 打包实重 → deductWeightById 行锁扣减，非整行软删）默认成功
        when(inhouseMapper.deductWeightById(anyLong(), any())).thenReturn(1);
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
        // 验证 inhouse 按实重部分扣减（来源 50 > 打包 30.5，非整行软删）
        verify(inhouseMapper, times(1)).deductWeightById(eq(70001L), any());
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

    @Test
    @DisplayName("submitVegPack: location_stock 无既有行（add 返 0）→ upsert 兜底 INSERT 新行（WMS-PACK-UPSERT-001）")
    void testVegPack_UpsertInsertsWhenNoStockRow() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        when(productInfoMapper.selectById(60010L)).thenReturn(sampleVegProduct());
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ProductProduction p = inv.getArgument(0);
            p.setId(80001L);
            return 1;
        });
        // 关键：该 product+location 无既有库存行 → UPDATE affected=0
        when(locationStockMapper.addByProductLocation(eq(90001L), eq(60010L), any(), eq(9001L))).thenReturn(0);
        when(inhouseMapper.deleteById(eq(70001L))).thenReturn(1);

        service.submitVegPack(sampleVegBo());

        // upsert 兜底 INSERT 新 LocationStock（字段取自 production）
        ArgumentCaptor<LocationStock> cap = ArgumentCaptor.forClass(LocationStock.class);
        verify(locationStockMapper, times(1)).insert(cap.capture());
        LocationStock fresh = cap.getValue();
        assertThat(fresh.getLocationId()).isEqualTo(90001L);
        assertThat(fresh.getProductId()).isEqualTo(60010L);
        assertThat(fresh.getProductName()).isEqualTo("番茄·小盒装");
        assertThat(fresh.getProductStock()).isEqualByComparingTo("30.500");
        assertThat(fresh.getIsEnd()).isEqualTo(0);
        assertThat(fresh.getOperatorId()).isEqualTo(9001L);
    }

    @Test
    @DisplayName("submitVegPack: location_stock 有既有行（add 返 1）→ 仅 UPDATE 增量，不 INSERT")
    void testVegPack_UpsertSkipsInsertWhenRowExists() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        when(productInfoMapper.selectById(60010L)).thenReturn(sampleVegProduct());
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ProductProduction p = inv.getArgument(0);
            p.setId(80002L);
            return 1;
        });
        when(locationStockMapper.addByProductLocation(eq(90001L), eq(60010L), any(), eq(9001L))).thenReturn(1);
        when(inhouseMapper.deleteById(eq(70001L))).thenReturn(1);

        service.submitVegPack(sampleVegBo());

        verify(locationStockMapper, never()).insert(any(LocationStock.class));
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

    /** 一条「发送礼盒」生产产品（deliver_dest='gift'），作礼盒组件可用池。 */
    private ProductProduction giftBoundProduction(Long id, Long productId, String qty) {
        ProductProduction p = new ProductProduction();
        p.setId(id);
        p.setProductId(productId);
        p.setDeliverDest("gift");
        p.setProduceQuantity(new BigDecimal(qty));
        p.setProductWeight(new BigDecimal(qty));
        return p;
    }

    @Test
    @DisplayName("submitGiftPack: happy 2 组件 → 扣「发送礼盒生产产品」(FIFO 软删/部分扣) + stock_flow 3 行 + production INSERT；不扣 location_stock")
    void testGiftPack_Happy() {
        when(productInfoMapper.selectById(60100L)).thenReturn(sampleGiftProduct());
        when(locationInfoMapper.selectById(90100L)).thenReturn(sampleLocation());
        when(giftBoxMapper.selectList(any())).thenReturn(List.of(
            sampleComponent(60001L, new BigDecimal("2.000"), "kg"),
            sampleComponent(60002L, new BigDecimal("1.000"), "包")
        ));
        // 组件 60001 需 2×5=10：gift-bound 生产产品池正好 10（整条用尽 → 软删）；
        // 组件 60002 需 1×5=5：池 8（部分扣 → updateById，剩 3）。selectList 按组件顺序消费。
        when(productionMapper.selectList(any())).thenReturn(
            List.of(giftBoundProduction(80001L, 60001L, "10")),
            List.of(giftBoundProduction(80002L, 60002L, "8"))
        );
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ProductProduction p = inv.getArgument(0);
            p.setId(80100L);
            return 1;
        });
        when(locationStockMapper.addByProductLocation(eq(90100L), eq(60100L), any(), eq(9001L))).thenReturn(1);

        Long id = service.submitGiftPack(sampleGiftBo());

        assertThat(id).isEqualTo(80100L);
        // 不再扣原料/成品 location_stock（按对应生产产品判，Kevin 2026-06-25）
        verify(locationStockMapper, never()).deductByProductLocation(anyLong(), anyLong(), any(), anyLong());
        // 60001 整条用尽 → 软删（证明消耗的是「发送礼盒生产产品」而非 location_stock）
        verify(productionMapper, times(1)).deleteById(80001L);
        // 60002 部分扣 → updateById 把 80002 剩量改为 3（updateById 还含 fillTraceCode 对礼盒主记录回填，故 captor 校验任一命中）
        ArgumentCaptor<ProductProduction> upd = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper, atLeastOnce()).updateById(upd.capture());
        assertThat(upd.getAllValues()).anySatisfy(pp -> {
            assertThat(pp.getId()).isEqualTo(80002L);
            assertThat(pp.getProduceQuantity()).isEqualByComparingTo("3");
        });
        verify(stockFlowMapper, times(3)).insert(any(StockFlow.class)); // 2 consume + 1 in
        verify(locationStockMapper, times(1)).addByProductLocation(
            eq(90100L), eq(60100L), any(), eq(9001L));
        ArgumentCaptor<ProductProduction> cap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper).insert(cap.capture());
        ProductProduction saved = cap.getValue();
        assertThat(saved.getProductId()).isEqualTo(60100L);
        assertThat(saved.getProductType()).isEqualTo(3);
        assertThat(saved.getProductWeight()).isEqualByComparingTo("5"); // packBoxCount
        assertThat(saved.getProduceNo()).isNotBlank(); // 生产序号非空（PRODUCE_NO 编码不再按业态前缀分桶）
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
    @DisplayName("submitGiftPack: 组件「发送礼盒生产产品」不足 → 抛 + 事务回滚（production 不 insert）")
    void testGiftPack_ComponentStockInsufficient() {
        when(productInfoMapper.selectById(60100L)).thenReturn(sampleGiftProduct());
        when(locationInfoMapper.selectById(90100L)).thenReturn(sampleLocation());
        when(giftBoxMapper.selectList(any())).thenReturn(List.of(
            sampleComponent(60001L, new BigDecimal("2.000"), "kg")
        ));
        // 该组件没有「发送礼盒」生产产品（池空）→ 可用 0 < 需 10
        when(productionMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitGiftPack(sampleGiftBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("组件不足");

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

    // ============================================================
    // white_bar / pork out (WMS-WHITEBAR-SHIP-001)
    // ============================================================

    @Test
    @DisplayName("submitWhiteBarOut: happy 白条 inhouse → production B 前缀 + 不校验 is_delivery（白条 SKU is_delivery=0 仍出库）+ consumeInhouse")
    void testWhiteBarOut_Happy() {
        ProductInhouse src = sampleVegSource(); // id=70001, productId=60001
        src.setEarNo("010126050101");
        src.setLocationId(90001L);
        when(inhouseMapper.selectById(70001L)).thenReturn(src);
        ProductInfo wb = sampleVegProduct();
        wb.setId(60001L);
        wb.setBelongType("white_bar");
        wb.setProductName("白条·整只");
        wb.setIsDelivery(0); // 关键：白条整只 SKU is_delivery=0，出库发货不校验
        when(productInfoMapper.selectById(60001L)).thenReturn(wb);
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ((ProductProduction) inv.getArgument(0)).setId(80500L);
            return 1;
        });
        when(locationStockMapper.addByProductLocation(any(), any(), any(), any())).thenReturn(1);
        when(inhouseMapper.deleteById(any())).thenReturn(1);

        WhiteBarOutBo bo = new WhiteBarOutBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductWeight(new BigDecimal("12.000"));
        bo.setStoreId(9L);
        when(storeMapper.selectById(9L)).thenReturn(new org.dromara.djs.common.store.domain.Store());

        Long id = service.submitWhiteBarOut(bo);

        assertThat(id).isEqualTo(80500L);
        ArgumentCaptor<ProductProduction> cap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper).insert(cap.capture());
        ProductProduction saved = cap.getValue();
        assertThat(saved.getProduceNo()).contains("B"); // 白条前缀 B（非 requireDeliveryProduct）
        assertThat(saved.getProductId()).isEqualTo(60001L); // 直接用来源 inhouse 的 product_id
        assertThat(saved.getStoreId()).isEqualTo(9L);
        assertThat(saved.getEarNo()).isEqualTo("010126050101");
        assertThat(saved.getProductWeight()).isEqualByComparingTo("12.000");
        verify(inhouseMapper, times(1)).deductWeightById(eq(70001L), any()); // consumeInhouse 部分扣减
    }

    // ============================================================
    // veg pack — product_material 库存校验（V4 果疏全流程 Part II）
    // ============================================================

    @Test
    @DisplayName("submitVegPack: 来源 inhouse 余量 < 打包实重 → 抛「来源待打包库存不足」+ production 不 INSERT（fail-fast，doc/14 §1）")
    void testVegPack_SourceInhouseInsufficient() {
        // 领用已把原材料从冷库转入 inhouse；打包的真正闸 = 来源 inhouse 余量（非冷库 location_stock）
        ProductInhouse src = sampleVegSource();
        src.setProductWeight(new BigDecimal("10.000")); // 来源余量 10 < 打包 30.5
        when(inhouseMapper.selectById(70001L)).thenReturn(src);
        when(productInfoMapper.selectById(60010L)).thenReturn(sampleVegProduct());

        assertThatThrownBy(() -> service.submitVegPack(sampleVegBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("来源待打包库存不足");

        verify(productionMapper, never()).insert(any(ProductProduction.class));
    }

    @Test
    @DisplayName("submitVegPack: 配 product_material 且原材料库存 >= 打包重量 → 通过（不扣减原材料，沿用 consumeInhouse）")
    void testVegPack_MaterialStockSufficient_NoExtraDeduct() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        ProductInfo p = sampleVegProduct();
        p.setProductMaterial(60001L);
        when(productInfoMapper.selectById(60010L)).thenReturn(p);
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        // 原材料库存 100kg >= 打包 30.5kg
        when(locationStockMapper.selectList(any())).thenReturn(List.of(stockRow(new BigDecimal("100.000"))));
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ((ProductProduction) inv.getArgument(0)).setId(80011L);
            return 1;
        });
        when(locationStockMapper.addByProductLocation(any(), any(), any(), any())).thenReturn(1);
        when(inhouseMapper.deleteById(any())).thenReturn(1);

        Long id = service.submitVegPack(sampleVegBo());

        assertThat(id).isEqualTo(80011L);
        // 果蔬不扣原材料 location_stock：来源消耗走 consumeInhouse（按实重部分扣减），不调 deductByProductLocation
        verify(inhouseMapper, times(1)).deductWeightById(eq(70001L), any());
        verify(locationStockMapper, never()).deductByProductLocation(any(), any(), any(), any());
    }

    @Test
    @DisplayName("submitVegPack: 未配 product_material → 降级跳过校验，正常打包（向后兼容现有数据全 NULL）")
    void testVegPack_NoMaterialConfigured_Degrades() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        when(productInfoMapper.selectById(60010L)).thenReturn(sampleVegProduct()); // productMaterial=null
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ((ProductProduction) inv.getArgument(0)).setId(80012L);
            return 1;
        });
        when(locationStockMapper.addByProductLocation(any(), any(), any(), any())).thenReturn(1);
        when(inhouseMapper.deleteById(any())).thenReturn(1);

        Long id = service.submitVegPack(sampleVegBo());

        assertThat(id).isEqualTo(80012L);
        // 未配料 → 不查原材料库存（不调 selectList 做 sumProductStock）
        verify(locationStockMapper, never()).selectList(any());
    }

    // ============================================================
    // veg daily loss (V4 compute-on-read)
    // ============================================================

    @Test
    @DisplayName("queryVegDailyLoss: 公式 = 领用(pick_out) − 打包(pack_in) − 退回(return_in) − 饲喂(=0)")
    void testVegDailyLoss_Formula() {
        // 领用 = pick_out（物资领用出库）
        when(stockFlowMapper.sumVegFlowByTypeAndDate(eq("pick_out"), any()))
            .thenReturn(new BigDecimal("100.000"));
        // 打包 = pack_in（果蔬打包入库，submitVegPack 写）
        when(stockFlowMapper.sumVegFlowByTypeAndDate(eq("pack_in"), any()))
            .thenReturn(new BigDecimal("70.000"));
        // 退回 = return_in
        when(stockFlowMapper.sumVegFlowByTypeAndDate(eq("pick_return_in"), any()))
            .thenReturn(new BigDecimal("5.000"));
        // 饲喂：物资领用 V1 无果蔬饲喂操作 → service 端置 0，不查 mapper（故不 mock，校验 never 调用）

        org.dromara.djs.warehouse.pack.domain.vo.VegDailyLossVo vo = service.queryVegDailyLoss(null);

        assertThat(vo.getPickedWeight()).isEqualByComparingTo("100.000");
        assertThat(vo.getPackedWeight()).isEqualByComparingTo("70.000");
        assertThat(vo.getReturnedWeight()).isEqualByComparingTo("5.000");
        assertThat(vo.getFeedWeight()).isEqualByComparingTo("0");
        // 100 - 70 - 5 - 0 = 25
        assertThat(vo.getLossWeight()).isEqualByComparingTo("25.000");
        assertThat(vo.getStatDate()).isNotBlank();
        // 饲喂不查任何 flow_type（旧 veg_stock_in / pack_consume / loss 口径已弃用）
        verify(stockFlowMapper, never()).sumVegFlowByTypeAndDate(eq("loss"), any());
        verify(stockFlowMapper, never()).sumVegFlowByTypeAndDate(eq("veg_stock_in"), any());
        verify(stockFlowMapper, never()).sumVegFlowByTypeAndDate(eq("pack_consume"), any());
    }

    @Test
    @DisplayName("queryVegDailyLoss: 负损耗（打包 > 领用，录入未配齐）→ 归零")
    void testVegDailyLoss_NegativeClampedToZero() {
        when(stockFlowMapper.sumVegFlowByTypeAndDate(eq("pick_out"), any()))
            .thenReturn(new BigDecimal("10.000"));
        when(stockFlowMapper.sumVegFlowByTypeAndDate(eq("pack_in"), any()))
            .thenReturn(new BigDecimal("50.000"));
        when(stockFlowMapper.sumVegFlowByTypeAndDate(eq("pick_return_in"), any())).thenReturn(BigDecimal.ZERO);

        org.dromara.djs.warehouse.pack.domain.vo.VegDailyLossVo vo = service.queryVegDailyLoss(null);

        assertThat(vo.getLossWeight()).isEqualByComparingTo("0");
    }

    private LocationStock stockRow(BigDecimal stock) {
        LocationStock s = new LocationStock();
        s.setProductStock(stock);
        return s;
    }

    // ============================================================
    // listSourceForVeg (G8: belong_type='vegetable' + product_attr=2)
    // ============================================================

    @Test
    @DisplayName("listSourceForVeg: 解析 belong_type='vegetable' + product_attr=2 的 product_id 集 → inhouse WHERE product_id IN + 今天 + weight>0")
    void testListSourceForVeg_FiltersByVegAndAttrMaterial() {
        ProductInfo material = sampleVegProduct();
        material.setId(60001L);
        material.setProductAttr(2);
        when(productInfoMapper.selectList(any())).thenReturn(List.of(material));
        when(inhouseMapper.selectList(any())).thenReturn(List.of(sampleVegSource()));

        List<ProductInhouse> rows = service.listSourceForVeg();

        assertThat(rows).hasSize(1);
        // product_info 查询带 belong_type='vegetable' + product_attr=2 双条件（G8 守门）
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductInfo>> piCap =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(productInfoMapper).selectList(piCap.capture());
        String piSql = piCap.getValue().getTargetSql();
        assertThat(piSql).contains("belong_type").contains("product_attr");
        // inhouse 查询带「今天」过滤（DATE(produce_date)=CURDATE()）+ product_weight
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductInhouse>> ihCap =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(inhouseMapper).selectList(ihCap.capture());
        assertThat(ihCap.getValue().getTargetSql()).contains("CURDATE()").contains("product_weight");
    }

    @Test
    @DisplayName("listSourceForVeg: 无 vegetable 原料 product → 直接返空，不查 inhouse")
    void testListSourceForVeg_NoVegMaterialProduct_ReturnsEmpty() {
        when(productInfoMapper.selectList(any())).thenReturn(List.of());

        List<ProductInhouse> rows = service.listSourceForVeg();

        assertThat(rows).isEmpty();
        verify(inhouseMapper, never()).selectList(any());
    }

    // ============================================================
    // listSourceForDry (G5: belong_type IN(egg,dry_good,other) + product_attr=2 + 今天，去裸捞)
    // ============================================================

    @Test
    @DisplayName("listSourceForDry: 解析 belong_type∈{egg,dry_good,other} + product_attr=2 的 product_id 集 → inhouse WHERE product_id IN + 今天 + weight>0（不再裸捞全表）")
    void testListSourceForDry_FiltersByOtherBelongTypesAndAttrMaterial() {
        ProductInfo eggMaterial = sampleVegProduct();
        eggMaterial.setId(60050L);
        eggMaterial.setBelongType("egg");
        eggMaterial.setProductAttr(2);
        when(productInfoMapper.selectList(any())).thenReturn(List.of(eggMaterial));
        ProductInhouse src = sampleVegSource();
        src.setProductId(60050L);
        when(inhouseMapper.selectList(any())).thenReturn(List.of(src));

        List<ProductInhouse> rows = service.listSourceForDry();

        assertThat(rows).hasSize(1);
        // product_info 查询带业态白名单 IN + product_attr=2（G5：去裸捞，按白名单过滤）
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductInfo>> piCap =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(productInfoMapper).selectList(piCap.capture());
        String piSql = piCap.getValue().getTargetSql();
        assertThat(piSql).contains("belong_type").contains("IN").contains("product_attr");
        // inhouse 查询按 product_id IN + 今天 + weight>0（不再裸捞）
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductInhouse>> ihCap =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(inhouseMapper).selectList(ihCap.capture());
        String ihSql = ihCap.getValue().getTargetSql();
        assertThat(ihSql).contains("product_id").contains("CURDATE()").contains("product_weight");
    }

    @Test
    @DisplayName("listSourceForDry: 无 egg/dry_good/other 原料 product → 直接返空，不查 inhouse（去裸捞验证）")
    void testListSourceForDry_NoOtherMaterialProduct_ReturnsEmpty() {
        when(productInfoMapper.selectList(any())).thenReturn(List.of());

        List<ProductInhouse> rows = service.listSourceForDry();

        assertThat(rows).isEmpty();
        verify(inhouseMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("submitWhiteBarOut: 来源业态非 white_bar/pork → 抛 + production 不 INSERT")
    void testWhiteBarOut_NotWhiteBarOrPork() {
        ProductInhouse src = sampleVegSource();
        when(inhouseMapper.selectById(70001L)).thenReturn(src);
        ProductInfo veg = sampleVegProduct();
        veg.setId(60001L);
        veg.setBelongType("vegetable"); // 非白条/猪肉
        when(productInfoMapper.selectById(60001L)).thenReturn(veg);

        WhiteBarOutBo bo = new WhiteBarOutBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductWeight(new BigDecimal("12.000"));

        assertThatThrownBy(() -> service.submitWhiteBarOut(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("出库发货仅限白条/猪肉");
        verify(productionMapper, never()).insert(any(ProductProduction.class));
    }

}
