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
import org.dromara.djs.warehouse.pack.domain.bo.MarkDamageBo;
import org.dromara.djs.warehouse.pack.domain.bo.VegPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.WhiteBarOutBo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
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
 *   <li>giftPack happy：礼盒为独立成品 → 只产出 N 盒 production + 扣门店礼盒需求，不查/不消耗组件</li>
 *   <li>giftPack 产品非礼盒类型 → 抛</li>
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
    @Mock private org.dromara.djs.warehouse.loss.service.ILossFlowService lossFlowService;
    @Mock private org.dromara.djs.warehouse.cut.service.IPigCutRecordService pigCutService;

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
            productionMapper, inhouseMapper, productInfoMapper,
            locationInfoMapper, locationStockMapper, stockFlowMapper, storeMapper, plotInfoMapper,
            demandManageMapper, barInfoMapper, bizCodeGenerator, traceService, stockCheckService);

        // lossFlowService / pigCutService 走 @Autowired @Lazy 字段注入（非构造器），单测用反射塞 mock
        try {
            java.lang.reflect.Field f = ProductProductionServiceImpl.class.getDeclaredField("lossFlowService");
            f.setAccessible(true);
            f.set(service, lossFlowService);
            java.lang.reflect.Field f2 = ProductProductionServiceImpl.class.getDeclaredField("pigCutService");
            f2.setAccessible(true);
            f2.set(service, pigCutService);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 @Lazy 字段 mock 失败", e);
        }

        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(9001L);

        // BizCodeType.STOCK_FLOW_NO 默认 stub
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap()))
            .thenReturn("F2606280001");

        // BizCodeType.PRODUCE_NO 默认 stub：全部打包/出库生码共用一个每日计数器
        // （daily_reset=1，前缀由 code rule 决定、ctx 不再传业态 prefix）
        when(bizCodeGenerator.generate(eq(BizCodeType.PRODUCE_NO), anyMap()))
            .thenReturn("P2606280001");

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
        // P1-2 跨域隔离：仓库打包/出库只认 source='warehouse' 的分割产
        i.setSource("warehouse");
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
        // 需求 C：发货月台打包须选门店（打包即扣需求）；无未完成需求时 warn 跳过扣减
        bo.setStoreId(7L);
        return bo;
    }

    @Test
    @DisplayName("submitVegPack: happy → INSERT production + 消耗来源 inhouse；row42 不写入库流水/不动 location_stock")
    void testVegPack_Happy() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        when(productInfoMapper.selectById(60010L)).thenReturn(sampleVegProduct());
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ProductProduction p = inv.getArgument(0);
            p.setId(80001L);
            return 1;
        });

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
        assertThat(saved.getProduceNo()).isEqualTo("P2606280001"); // PRODUCE_NO 共用每日计数器
        assertThat(saved.getIsDeliveryCheck()).isEqualTo(0);
        // row42：生产产品不入库 → 不写入库流水、不动 location_stock
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never()).addByProductLocation(any(), any(), any(), any());
        // 验证 inhouse 按实重部分扣减（来源 50 > 打包 30.5，非整行软删）
        verify(inhouseMapper, times(1)).deductWeightById(eq(70001L), any());
    }

    @Test
    @DisplayName("submitVegPack: 无论实称重量 / material_num 为何，门店需求恒恰好扣 1 份")
    void testVegPack_DeductExactlyOneCopy_RegardlessOfWeightOrMaterialNum() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        ProductInfo p = sampleVegProduct();
        p.setMaterialNum(new BigDecimal("0.500"));
        when(productInfoMapper.selectById(60010L)).thenReturn(p);
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ((ProductProduction) inv.getArgument(0)).setId(80013L);
            return 1;
        });
        // 门店有未完成需求 → 打包即扣
        org.dromara.djs.warehouse.demand.domain.DemandManage demand =
            new org.dromara.djs.warehouse.demand.domain.DemandManage();
        demand.setId(50002L);
        demand.setDemandQuantity(new BigDecimal("3"));
        demand.setShippedCount(BigDecimal.ZERO);
        when(demandManageMapper.selectOldestUncompletedDemand(eq(60010L), eq(7L))).thenReturn(demand);
        when(demandManageMapper.incrementShipped(eq(50002L), any(), any())).thenReturn(1);

        // 打包 0.55kg（=550g）
        VegPackBo bo = sampleVegBo();
        bo.setProductWeight(new BigDecimal("0.550"));
        bo.setAllowOverMeasure(true);
        service.submitVegPack(bo);

        // 铁令：扣减量恒 1 份（整数），不是 0.55
        ArgumentCaptor<BigDecimal> qtyCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(demandManageMapper, times(1)).incrementShipped(eq(50002L), any(), qtyCap.capture());
        assertThat(qtyCap.getValue()).isEqualByComparingTo("1");
    }

    /**
     * 打包称重用例的公共 stub：规则 0.500kg（放行区间 [0.500, 0.515]，低于 0.500 硬拒）+ 允许 INSERT。
     *
     * @param productionId INSERT 时回填的自增 id
     */
    private void stubVegPackWithMeasureRule(long productionId) {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        ProductInfo p = sampleVegProduct();
        p.setMaterialNum(new BigDecimal("0.500"));
        when(productInfoMapper.selectById(60010L)).thenReturn(p);
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ((ProductProduction) inv.getArgument(0)).setId(productionId);
            return 1;
        });
    }

    /** 只 stub 到校验发生前（校验不通过时后续 mapper 都不该被碰）。规则 0.500kg。 */
    private void stubVegPackMeasureRuleOnly() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        ProductInfo p = sampleVegProduct();
        p.setMaterialNum(new BigDecimal("0.500"));
        when(productInfoMapper.selectById(60010L)).thenReturn(p);
    }

    @Test
    @DisplayName("submitVegPack: 实称恰等于规则重量（0.500/规则 0.500）→ 通过，production 照常 INSERT")
    void testVegPack_ExactlyRuleWeightAllowed() {
        stubVegPackWithMeasureRule(80014L);
        VegPackBo bo = sampleVegBo();
        bo.setProductWeight(new BigDecimal("0.500"));

        Long id = service.submitVegPack(bo);

        assertThat(id).isEqualTo(80014L);
        ArgumentCaptor<ProductProduction> cap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getProductWeight()).isEqualByComparingTo("0.500");
    }

    @Test
    @DisplayName("submitVegPack: 实称恰为允许上界 rule×1.03（0.515）→ 边界含入，直接通过")
    void testVegPack_ExactlyUpperToleranceBoundAllowed() {
        stubVegPackWithMeasureRule(80016L);
        VegPackBo bo = sampleVegBo();
        bo.setProductWeight(new BigDecimal("0.515"));

        assertThat(service.submitVegPack(bo)).isEqualTo(80016L);
        verify(productionMapper, times(1)).insert(any(ProductProduction.class));
    }

    @Test
    @DisplayName("submitVegPack: 实称低于规则重量（0.499 < 0.500）→ 硬拒，文案不带二次确认标识，不 INSERT")
    void testVegPack_BelowRuleRejected() {
        stubVegPackMeasureRuleOnly();
        VegPackBo bo = sampleVegBo();
        bo.setProductWeight(new BigDecimal("0.499"));

        assertThatThrownBy(() -> service.submitVegPack(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("低于打包规则")
            .hasMessageContaining("不能少于规则重量")
            .hasMessageContaining("0.499")
            .hasMessageContaining("0.5")
            // 关键：不带标识串 -> 前端不会弹「继续打包」，硬拒生效
            .hasMessageNotContaining("超出3%");
        verify(productionMapper, never()).insert(any(ProductProduction.class));
    }

    @Test
    @DisplayName("submitVegPack: 实称低于规则重量 + allowOverMeasure=true → 仍硬拒（确认绕不过下限），不 INSERT")
    void testVegPack_BelowRuleNotBypassableByConfirmation() {
        stubVegPackMeasureRuleOnly();
        VegPackBo bo = sampleVegBo();
        bo.setProductWeight(new BigDecimal("0.300"));
        bo.setAllowOverMeasure(true);

        assertThatThrownBy(() -> service.submitVegPack(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能少于规则重量");
        verify(productionMapper, never()).insert(any(ProductProduction.class));
    }

    @Test
    @DisplayName("submitVegPack: 实称超出允许上界（0.516 > 0.515）且未确认 → 抛带标识的提示，不 INSERT")
    void testVegPack_OverToleranceRequiresConfirmation() {
        stubVegPackMeasureRuleOnly();
        VegPackBo bo = sampleVegBo();
        bo.setProductWeight(new BigDecimal("0.516"));

        assertThatThrownBy(() -> service.submitVegPack(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("超出3%");
        verify(productionMapper, never()).insert(any(ProductProduction.class));
    }

    @Test
    @DisplayName("submitVegPack: 超出上界但带 allowOverMeasure=true（二次确认）→ 放行 INSERT")
    void testVegPack_OverToleranceAllowedAfterConfirmation() {
        stubVegPackWithMeasureRule(80017L);
        VegPackBo bo = sampleVegBo();
        bo.setProductWeight(new BigDecimal("0.600"));
        bo.setAllowOverMeasure(true);

        assertThat(service.submitVegPack(bo)).isEqualTo(80017L);
        ArgumentCaptor<ProductProduction> cap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getProductWeight()).isEqualByComparingTo("0.600");
    }

    @Test
    @DisplayName("submitVegPack: 未配打包规则（material_num 空）→ 任意实称都不校验")
    void testVegPack_NoMeasureRuleSkipsValidation() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        when(productInfoMapper.selectById(60010L)).thenReturn(sampleVegProduct());
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ((ProductProduction) inv.getArgument(0)).setId(80018L);
            return 1;
        });
        VegPackBo bo = sampleVegBo();
        bo.setProductWeight(new BigDecimal("0.010"));

        assertThat(service.submitVegPack(bo)).isEqualTo(80018L);
        verify(productionMapper, times(1)).insert(any(ProductProduction.class));
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
    @DisplayName("submitVegPack: row42 生产产品不入库 → 全程不写 location_stock（无 UPDATE 增量、无兜底 INSERT）")
    void testVegPack_UpsertInsertsWhenNoStockRow() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        when(productInfoMapper.selectById(60010L)).thenReturn(sampleVegProduct());
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ProductProduction p = inv.getArgument(0);
            p.setId(80001L);
            return 1;
        });

        service.submitVegPack(sampleVegBo());

        // row42：打包成品直送发货月台，不进 location_stock（原 WMS-PACK-UPSERT-001 upsert 链路已随之移除）
        verify(locationStockMapper, never()).addByProductLocation(any(), any(), any(), any());
        verify(locationStockMapper, never()).insert(any(LocationStock.class));
    }

    // ============================================================
    // gift pack
    // ============================================================

    private ProductInfo sampleGiftProduct() {
        // 礼盒 = 自产（productType=1）+ belong_type=gift_box（djs_product_type 已废弃 3）
        ProductInfo p = new ProductInfo();
        p.setId(60100L);
        p.setProductId("PROD-GIFT-A-01");
        p.setProductName("精品礼盒 A");
        p.setProductType(1);
        p.setProductUnit("盒");
        p.setBelongType("gift_box");
        p.setIsDelivery(1);
        return p;
    }

    private GiftPackBo sampleGiftBo() {
        GiftPackBo bo = new GiftPackBo();
        bo.setGiftBoxProductId(60100L);
        bo.setPackBoxCount(5);
        bo.setLocationId(90100L);
        bo.setStoreId(7L);
        return bo;
    }

    @Test
    @DisplayName("submitGiftPack: 礼盒为独立成品 → 只产出 N 盒 production + 扣门店礼盒需求；不查/不消耗任何组件")
    void testGiftPack_Happy() {
        when(productInfoMapper.selectById(60100L)).thenReturn(sampleGiftProduct());
        when(locationInfoMapper.selectById(90100L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ProductProduction p = inv.getArgument(0);
            p.setId(80100L);
            return 1;
        });
        // 门店有未完成礼盒需求 → 打包即扣 shipped_count
        org.dromara.djs.warehouse.demand.domain.DemandManage demand =
            new org.dromara.djs.warehouse.demand.domain.DemandManage();
        demand.setId(50001L);
        // row53-BE 硬拦：剩余份数（demand_quantity − shipped_count）须 ≥ 本次打包 5 盒
        demand.setDemandQuantity(new BigDecimal("10"));
        demand.setShippedCount(BigDecimal.ZERO);
        when(demandManageMapper.selectOldestUncompletedDemand(eq(60100L), eq(7L))).thenReturn(demand);
        // incrementShipped 带上界守卫：affected=1 = 守卫命中正常扣减（0 会被 service 判并发超打抛错）
        when(demandManageMapper.incrementShipped(eq(50001L), any(), any())).thenReturn(1);

        Long id = service.submitGiftPack(sampleGiftBo());

        assertThat(id).isEqualTo(80100L);
        // 不查/不消耗任何组件：无组件 production 软删、无组件库存扣减、无组件消耗流水
        verify(productionMapper, never()).deleteById(anyLong());
        verify(locationStockMapper, never()).deductByProductLocation(anyLong(), anyLong(), any(), anyLong());
        // 只产出 N 盒礼盒成品：1 行 production insert；row42 不写入库流水/不进 location_stock
        ArgumentCaptor<ProductProduction> cap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper, times(1)).insert(cap.capture());
        ProductProduction saved = cap.getValue();
        assertThat(saved.getProductId()).isEqualTo(60100L);
        assertThat(saved.getProductType()).isEqualTo(1); // 产出沿用礼盒源产品 type=1（djs_product_type 已废弃 3）
        assertThat(saved.getProductWeight()).isEqualByComparingTo("5"); // packBoxCount=5 盒
        assertThat(saved.getProduceQuantity()).isEqualByComparingTo("5");
        assertThat(saved.getProductUnit()).isEqualTo("盒");
        assertThat(saved.getPackStatus()).isEqualTo("packed");
        assertThat(saved.getProduceNo()).isNotBlank();
        // row42：生产产品不入库 → 无入库流水、不动 location_stock
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never()).addByProductLocation(any(), any(), any(), any());
        // 扣门店礼盒需求（deductDemandOnPack）：按盒数累加 shipped_count
        verify(demandManageMapper, times(1)).incrementShipped(eq(50001L), any(), any());
    }

    @Test
    @DisplayName("submitGiftPack: 产品 belong_type != gift_box → 抛")
    void testGiftPack_NotGiftType() {
        ProductInfo p = sampleGiftProduct();
        p.setBelongType("pork"); // 非礼盒（belong_type != gift_box）
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
        p.setProductUnit("个");
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
        bo.setStoreId(7L);

        Long id = service.submitDryPack(bo);

        assertThat(id).isEqualTo(80200L);
        ArgumentCaptor<ProductProduction> cap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper).insert(cap.capture());
        ProductProduction saved = cap.getValue();
        assertThat(saved.getProductUnit()).isEqualTo("个");
        // produce_no 全业态共用 PRODUCE_NO 每日计数器（前缀由 code rule 决定，非业态分桶）
        assertThat(saved.getProduceNo()).isEqualTo("P2606280001");
    }

    /** 肉品打包称重用例公共 stub：规则 0.500kg（放行区间 [0.500, 0.515]，低于 0.500 硬拒）。 */
    private DryPackBo stubPorkDryPackWithMeasureRule(String actualWeight) {
        ProductInhouse src = sampleVegSource();
        src.setPlotId(null);
        when(inhouseMapper.selectById(70001L)).thenReturn(src);
        ProductInfo p = sampleVegProduct();
        p.setBelongType("pork");
        p.setMaterialNum(new BigDecimal("0.500"));
        when(productInfoMapper.selectById(60010L)).thenReturn(p);
        DryPackBo bo = new DryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(60010L);
        bo.setProductWeight(new BigDecimal(actualWeight));
        bo.setProductUnit("kg");
        bo.setLocationId(90001L);
        return bo;
    }

    @Test
    @DisplayName("submitDryPack: 肉品实称超允许上界（0.560 > 0.515）且未确认 → fail-fast，文案带二次确认标识")
    void testDryPack_PorkOverToleranceRequiresConfirmation() {
        DryPackBo bo = stubPorkDryPackWithMeasureRule("0.560");

        assertThatThrownBy(() -> service.submitDryPack(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("超出3%");
        verify(productionMapper, never()).insert(any(ProductProduction.class));
    }

    @Test
    @DisplayName("submitDryPack: 肉品实称低于规则重量（0.400 < 0.500）→ 硬拒，文案不带二次确认标识")
    void testDryPack_PorkBelowRuleRejected() {
        DryPackBo bo = stubPorkDryPackWithMeasureRule("0.400");

        assertThatThrownBy(() -> service.submitDryPack(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能少于规则重量")
            .hasMessageNotContaining("超出3%");
        verify(productionMapper, never()).insert(any(ProductProduction.class));
    }

    @Test
    @DisplayName("submitDryPack: 肉品实称在 [规则, 规则×1.03] 内（0.510）→ 直接通过")
    void testDryPack_PorkWithinToleranceAllowed() {
        DryPackBo bo = stubPorkDryPackWithMeasureRule("0.510");
        // KG 肉品打包必选门店（fulfillKgDemandOnPack）；无未完成需求时 warn 跳过扣减，不阻塞主链路
        bo.setStoreId(7L);
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ((ProductProduction) inv.getArgument(0)).setId(80210L);
            return 1;
        });
        when(locationStockMapper.addByProductLocation(any(), any(), any(), any())).thenReturn(1);
        when(inhouseMapper.deleteById(any())).thenReturn(1);

        assertThat(service.submitDryPack(bo)).isEqualTo(80210L);
        verify(productionMapper, times(1)).insert(any(ProductProduction.class));
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
        bo.setStoreId(7L);

        Long id = service.submitCeleryPack(bo);

        assertThat(id).isEqualTo(80300L);
        ArgumentCaptor<ProductProduction> cap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper).insert(cap.capture());
        ProductProduction saved = cap.getValue();
        assertThat(saved.getProductSpec()).isEqualTo("按重量");
        // produce_no 全业态共用 PRODUCE_NO 每日计数器（前缀由 code rule 决定，非业态分桶）
        assertThat(saved.getProduceNo()).isEqualTo("P2606280001");
    }

    @Test
    @DisplayName("submitCeleryPack: 目标产品配了 material_num 且实称低于规则 → 硬拒（芹菜页与蔬菜页同一 SKU 集合，不能成为绕过口）")
    void testCeleryPack_BelowRuleRejected() {
        when(inhouseMapper.selectById(70001L)).thenReturn(sampleVegSource());
        ProductInfo p = sampleVegProduct();
        p.setMaterialNum(new BigDecimal("0.500"));
        when(productInfoMapper.selectById(60010L)).thenReturn(p);

        CeleryPackBo bo = new CeleryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(60010L);
        bo.setProductWeight(new BigDecimal("0.400"));
        bo.setLocationId(90001L);
        bo.setStoreId(7L);

        assertThatThrownBy(() -> service.submitCeleryPack(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能少于规则重量")
            .hasMessageNotContaining("超出3%");
        verify(productionMapper, never()).insert(any(ProductProduction.class));
    }

    @Test
    @DisplayName("submitDryPack: 非肉品（干货）即便配了 material_num 也不套用重量规则 —— 它按份数×规格提交，套上必误判")
    void testDryPack_NonPorkSkipsMeasureRule() {
        ProductInhouse src = sampleVegSource();
        src.setPlotId(null);
        when(inhouseMapper.selectById(70001L)).thenReturn(src);
        ProductInfo p = sampleVegProduct();
        p.setBelongType("dry_good");
        // 干货「30 枚/份」：份数模式提交的 productWeight 是份数，远小于 materialNum，
        // 若误套重量规则会被下限硬拒 → 该页彻底不能提交
        p.setMaterialNum(new BigDecimal("30.000"));
        when(productInfoMapper.selectById(60010L)).thenReturn(p);
        when(locationInfoMapper.selectById(90001L)).thenReturn(sampleLocation());
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ((ProductProduction) inv.getArgument(0)).setId(80211L);
            return 1;
        });
        when(locationStockMapper.addByProductLocation(any(), any(), any(), any())).thenReturn(1);
        when(inhouseMapper.deleteById(any())).thenReturn(1);

        DryPackBo bo = new DryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(60010L);
        bo.setProductWeight(new BigDecimal("2.000"));
        bo.setProductUnit("份");
        bo.setLocationId(90001L);
        // 其他产品打包同样必选门店（fulfillKgDemandOnPack 前置校验）；无未完成需求时 warn 跳过扣减
        bo.setStoreId(7L);

        assertThat(service.submitDryPack(bo)).isEqualTo(80211L);
        verify(productionMapper, times(1)).insert(any(ProductProduction.class));
    }

    // ============================================================
    // produceNo 生成委托 IBizCodeGenerator（PRODUCE_NO + 业态前缀）
    // ============================================================

    @Test
    @DisplayName("produceNo: 委托 IBizCodeGenerator PRODUCE_NO（全业态共用每日计数器，ctx 不传业态前缀）")
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
        // produceNo 由 generator 按 PRODUCE_NO 规则生成（daily_reset 共用计数器，前缀在 code rule 内）
        assertThat(cap.getValue().getProduceNo()).isEqualTo("P2606280001");
        // 校验确实走 PRODUCE_NO 规则（ctx 不再携带业态 prefix）
        verify(bizCodeGenerator).generate(eq(BizCodeType.PRODUCE_NO), eq(Map.of()));
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
        assertThat(saved.getProduceNo()).isEqualTo("P2606280001"); // PRODUCE_NO 共用计数器（非业态前缀分桶）
        assertThat(saved.getProductId()).isEqualTo(60001L); // 直接用来源 inhouse 的 product_id
        assertThat(saved.getStoreId()).isEqualTo(9L);
        assertThat(saved.getEarNo()).isEqualTo("010126050101");
        assertThat(saved.getProductWeight()).isEqualByComparingTo("12.000");
        // DENGBO row28：发货月台出库按「整条产出行」消耗软删（产出重 50 全额 → deleteById），
        // 差额(50−12)=预冷损耗由 lossFlowService 记，不留零头残行
        verify(inhouseMapper, times(1)).deleteById(70001L);
        verify(inhouseMapper, never()).deductWeightById(anyLong(), any());
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

    // ============================================================
    // damage marking guard (admin row99)
    // ============================================================

    @Test
    @DisplayName("markDamage: KG 生产产品禁止标损，且不更新损坏信息")
    void markDamageRejectsKgProduct() {
        ProductProduction production = new ProductProduction();
        production.setId(81001L);
        production.setProductName("小白菜");
        production.setProductUnit("KG");
        when(productionMapper.selectById(81001L)).thenReturn(production);

        MarkDamageBo bo = new MarkDamageBo();
        bo.setId(81001L);
        bo.setRemark("不应落库");

        assertThatThrownBy(() -> service.markDamage(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KG")
            .hasMessageContaining("损坏");
        verify(productionMapper, never()).updateDamage(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("markDamage: 非 KG 生产产品允许标损")
    void markDamageAllowsNonKgProduct() {
        ProductProduction production = new ProductProduction();
        production.setId(81002L);
        production.setProductName("鸡蛋礼盒");
        production.setProductUnit("盒");
        when(productionMapper.selectById(81002L)).thenReturn(production);
        when(productionMapper.updateDamage(eq(81002L), any(), eq("破损"), any())).thenReturn(1);

        MarkDamageBo bo = new MarkDamageBo();
        bo.setId(81002L);
        bo.setRemark("破损");

        service.markDamage(bo);

        verify(productionMapper).updateDamage(eq(81002L), any(), eq("破损"), any());
    }

}
