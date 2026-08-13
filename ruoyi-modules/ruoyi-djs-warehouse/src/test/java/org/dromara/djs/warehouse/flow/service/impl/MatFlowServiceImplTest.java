package org.dromara.djs.warehouse.flow.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.domain.bo.MatFeedBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatLossBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatPickBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatReturnBo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueItemVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueLocationVo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.veg.domain.FeedLog;
import org.dromara.djs.warehouse.veg.mapper.FeedLogMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MatFlowServiceImpl} 单测（WMS-MAT-001）。
 *
 * <p>覆盖跨表事务一致性的 6 个核心场景（pick / return / loss × happy / 异常）：</p>
 * <ol>
 *   <li>pick happy：location_stock 充足 → stock_flow INSERT(pick_out / OT / change_num=-q) + 扣减成功</li>
 *   <li>pick 库存不足：deductByProductLocation 返 0 → 抛"库存不足" → 流水回滚（@Transactional 由集成测试覆盖；
 *       单测验证流水 INSERT 已被调过但 service 抛出）</li>
 *   <li>return happy：今日已领 ≥ 已退 + 已损 + 当次 → stock_flow INSERT(return_in / IN / +q) + 加库存</li>
 *   <li>return 超额：已领 20 - 已退 10 - 已损 0 = 剩 10；申请 15 → 抛"今日额度不足" + 无 INSERT</li>
 *   <li>loss happy：同 return 校验但扣库存；INSERT stock_flow(loss / OT / -q)</li>
 *   <li>requireProduct 不存在：productInfoMapper 返 null → 抛"产品不存在"</li>
 * </ol>
 *
 * <p>Mockito {@code MockedStatic(LoginHelper)} stub 当前 userId，避开 Sa-Token 上下文。</p>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MatFlowServiceImpl 单元测试")
class MatFlowServiceImplTest {

    @Mock private StockFlowMapper stockFlowMapper;
    @Mock private LocationStockMapper locationStockMapper;
    @Mock private org.dromara.djs.warehouse.location.mapper.LocationInfoMapper locationInfoMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper productInhouseMapper;
    @Mock private CropInfoMapper cropInfoMapper;
    @Mock private BarInfoMapper barInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private IStockCheckService stockCheckService;
    @Mock private ImageUrlResolver imageUrlResolver;
    @Mock private ILossFlowService lossFlowService;
    @Mock private FeedLogMapper feedLogMapper;

    private MatFlowServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    private static final Long USER_ID = 9001L;
    private static final Long PRODUCT_ID = 8001L;
    private static final Long LOCATION_ID = 7001L;
    private static final Long PLOT_ID = 6001L;
    private static final Long CROP_ID = 5001L;
    private static final Long RELATED_PRODUCT_ID = 8002L;

    /**
     * MyBatis-Plus 单测 entity cache 预热（skill coder-mp-entity-cache-test）：
     * pick/loss/feed 的篮子 FIFO / WIP 剥离用 LambdaQueryWrapper&lt;ProductInhouse&gt;/&lt;LocationStock&gt; 等。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, ProductInhouse.class);
        TableInfoHelper.initTableInfo(assistant, LocationStock.class);
        TableInfoHelper.initTableInfo(assistant, CropInfo.class);
    }

    @BeforeEach
    void setup() {
        service = new MatFlowServiceImpl(stockFlowMapper, locationStockMapper, locationInfoMapper, productInfoMapper, productInhouseMapper, cropInfoMapper, barInfoMapper, bizCodeGenerator, stockCheckService, imageUrlResolver, lossFlowService, feedLogMapper);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);

        // 默认 product stub
        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductId("PROD-PACK-BAG-01");
        product.setProductName("塑料袋");
        product.setProductUnit("个");
        product.setBelongType("package");
        when(productInfoMapper.selectOne(any())).thenReturn(product);

        // 默认 flow_no stub
        when(bizCodeGenerator.generate(any(), any())).thenReturn("FAKE_FLOW_NO");
        when(stockFlowMapper.insert(any(StockFlow.class))).thenAnswer(inv -> {
            StockFlow f = inv.getArgument(0);
            f.setId(50000L + (long) (Math.random() * 1000));
            return 1;
        });
        // 默认：本行今日领用剩余充足（ensureTodayRowRemaining 守卫，row123/row16 硬校验）——退回/损耗/饲喂
        // happy 用例默认「今日已领用过」，不被「未领用即操作」硬拦；专测该守卫的用例可另行覆盖返 0。
        when(stockFlowMapper.sumTodayNetPickedByBasket(any(), any(), any(), any(), any()))
            .thenReturn(new BigDecimal("999999"));
        // 猪肉 null-ear 组篮（admin 聚合行 / mp 退货卡）的组级净额同理默认充足；专测该守卫的用例另行覆盖
        when(stockFlowMapper.sumTodayNetPickedByNullEarGroup(any()))
            .thenReturn(new BigDecimal("999999"));
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private MatPickBo pickBo(BigDecimal qty) {
        MatPickBo bo = new MatPickBo();
        bo.setProductId(PRODUCT_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setQuantity(qty);
        bo.setStockOutDest("内部消耗");
        bo.setRemark("ut");
        return bo;
    }

    private MatReturnBo returnBo(BigDecimal qty) {
        MatReturnBo bo = new MatReturnBo();
        bo.setProductId(PRODUCT_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setQuantity(qty);
        bo.setRemark("ut");
        return bo;
    }

    private MatLossBo lossBo(BigDecimal qty) {
        MatLossBo bo = new MatLossBo();
        bo.setProductId(PRODUCT_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setQuantity(qty);
        bo.setRemark("ut");
        return bo;
    }

    @Test
    @DisplayName("pick happy：扣库存成功 → 流水 INSERT(dept_pick_out / OT / change_num=-20 / change_quantity=20)；无 sourceScene 兜底部门领用 + dest 强制 dept_pick（FIX-WMS-FLOWDICT-003）")
    void testPick_Happy() {
        when(locationStockMapper.deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        service.pick(pickBo(new BigDecimal("20")));

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        // 无 sourceScene + dest 非 kitchen → 兜底部门来源：flow_type=dept_pick_out、dest 强制覆盖 dept_pick
        assertThat(f.getFlowType()).isEqualTo("dept_pick_out");
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-20");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("20");
        assertThat(f.getStockOutDest()).isEqualTo("dept_pick");
        assertThat(f.getOperatorId()).isEqualTo(USER_ID);
        verify(locationStockMapper, times(1))
            .deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID));
    }

    @Test
    @DisplayName("pick 猪肉原材料 happy：按篮子(ear_no) FIFO 领用 50 → 跨 2 头猪(41.4+8.6) → 2 行 inhouse(带 ear_no) + 2 次 deductStockById；不走 product 维度")
    void testPick_PorkBasketFifo() {
        ProductInfo pork = new ProductInfo();
        pork.setId(PRODUCT_ID);
        pork.setProductName("猪肉·精瘦肉");
        pork.setProductUnit("kg");
        pork.setProductType(1);
        pork.setBelongType("pork");
        when(productInfoMapper.selectOne(any())).thenReturn(pork);

        LocationStock b1 = new LocationStock();
        b1.setId(9001L);
        b1.setProductId(PRODUCT_ID);
        b1.setEarNo("EAR-A");
        b1.setProductStock(new BigDecimal("41.4"));
        LocationStock b2 = new LocationStock();
        b2.setId(9002L);
        b2.setProductId(PRODUCT_ID);
        b2.setEarNo("EAR-B");
        b2.setProductStock(new BigDecimal("20"));
        when(locationStockMapper.selectList(any())).thenReturn(List.of(b1, b2));
        when(locationStockMapper.deductStockById(anyLong(), any(BigDecimal.class), eq(USER_ID))).thenReturn(1);

        service.pick(pickBo(new BigDecimal("50")));

        // 1 行 pick_out 流水
        verify(stockFlowMapper, times(1)).insert(any(StockFlow.class));
        // FIFO：篮1(EAR-A) 扣 41.4（全），篮2(EAR-B) 扣 8.6（凑够 50）；不走 product 维度扣减
        verify(locationStockMapper, times(1)).deductStockById(eq(9001L), eq(new BigDecimal("41.4")), eq(USER_ID));
        verify(locationStockMapper, times(1)).deductStockById(eq(9002L), eq(new BigDecimal("8.6")), eq(USER_ID));
        verify(locationStockMapper, never()).deductByProductLocation(any(), any(), any(), any());
        // 2 行 product_inhouse（带 ear_no = 篮子标签 → 打包追溯键）
        ArgumentCaptor<ProductInhouse> ihCap = ArgumentCaptor.forClass(ProductInhouse.class);
        verify(productInhouseMapper, times(2)).insert(ihCap.capture());
        List<ProductInhouse> ihs = ihCap.getAllValues();
        assertThat(ihs).extracting(ProductInhouse::getEarNo).containsExactly("EAR-A", "EAR-B");
        assertThat(ihs.get(0).getProductWeight()).isEqualByComparingTo("41.4");
        assertThat(ihs.get(1).getProductWeight()).isEqualByComparingTo("8.6");
        assertThat(ihs).extracting(ProductInhouse::getProductId).allMatch(v -> v.equals(PRODUCT_ID));
    }

    @Test
    @DisplayName("pick 自产果蔬原料 happy：按地块篮子(plot_id) FIFO 领用 50 → 跨 2 地块(40+10) → 2 行 inhouse(带 plot_id) + 2 次 deductStockById；不走 product 维度")
    void testPick_VegBasketFifo() {
        ProductInfo veg = new ProductInfo();
        veg.setId(PRODUCT_ID);
        veg.setProductName("黄秋葵");
        veg.setProductUnit("kg");
        veg.setProductType(1);
        veg.setProductAttr(2);
        veg.setBelongType("vegetable");
        when(productInfoMapper.selectOne(any())).thenReturn(veg);

        LocationStock b1 = new LocationStock();
        b1.setId(8001L);
        b1.setProductId(PRODUCT_ID);
        b1.setPlotId(7001L);
        b1.setProductStock(new BigDecimal("40"));
        LocationStock b2 = new LocationStock();
        b2.setId(8002L);
        b2.setProductId(PRODUCT_ID);
        b2.setPlotId(7002L);
        b2.setProductStock(new BigDecimal("30"));
        when(locationStockMapper.selectList(any())).thenReturn(List.of(b1, b2));
        when(locationStockMapper.deductStockById(anyLong(), any(BigDecimal.class), eq(USER_ID))).thenReturn(1);

        service.pick(pickBo(new BigDecimal("50")));

        // 1 行 pick_out 流水
        verify(stockFlowMapper, times(1)).insert(any(StockFlow.class));
        // FIFO：地块篮1(plot 7001) 扣 40（全），地块篮2(plot 7002) 扣 10（凑够 50）；不走 product 维度扣减
        verify(locationStockMapper, times(1)).deductStockById(eq(8001L), eq(new BigDecimal("40")), eq(USER_ID));
        verify(locationStockMapper, times(1)).deductStockById(eq(8002L), eq(new BigDecimal("10")), eq(USER_ID));
        verify(locationStockMapper, never()).deductByProductLocation(any(), any(), any(), any());
        // 2 行 product_inhouse（带 plot_id = 地块篮标签 → 果蔬打包右台显地块）
        ArgumentCaptor<ProductInhouse> ihCap = ArgumentCaptor.forClass(ProductInhouse.class);
        verify(productInhouseMapper, times(2)).insert(ihCap.capture());
        List<ProductInhouse> ihs = ihCap.getAllValues();
        assertThat(ihs).extracting(ProductInhouse::getPlotId).containsExactly(7001L, 7002L);
        assertThat(ihs.get(0).getProductWeight()).isEqualByComparingTo("40");
        assertThat(ihs.get(1).getProductWeight()).isEqualByComparingTo("10");
        assertThat(ihs).extracting(ProductInhouse::getProductId).allMatch(v -> v.equals(PRODUCT_ID));
    }

    @Test
    @DisplayName("pick 库存不足：deductByProductLocation 返 0 → 抛 ServiceException 库存不足")
    void testPick_StockInsufficient() {
        when(locationStockMapper.deductByProductLocation(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.pick(pickBo(new BigDecimal("999"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("库存不足");

        // INSERT 已发生（Spring @Transactional 集成测试覆盖回滚行为；单测只验异常抛出 + service 中断）
        verify(stockFlowMapper, times(1)).insert(any(StockFlow.class));
    }

    @Test
    @DisplayName("pick 其他业态原料(egg, attr=2) happy：product 维度扣减成功 → 桥接 1 行 product_inhouse(productId=materialId=自身, plotId/earNo=null)")
    void testPick_OtherMaterialBridgesInhouse() {
        ProductInfo egg = new ProductInfo();
        egg.setId(PRODUCT_ID);
        egg.setProductName("土鸡蛋·原料");
        egg.setProductUnit("kg");
        egg.setProductType(1);
        egg.setProductAttr(2);            // 原料
        egg.setBelongType("egg");
        // requireProduct 走 selectOne；bridge 走 selectById —— 两路都返这只 egg
        when(productInfoMapper.selectOne(any())).thenReturn(egg);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(egg);
        when(locationStockMapper.deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        service.pick(pickBo(new BigDecimal("12")));

        // 走 product 维度扣减（非篮子路径）
        verify(locationStockMapper, times(1))
            .deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID));
        // 桥接 1 行 inhouse：productId=materialId=自身原料，weight=领用量，plotId/earNo 为 null（product 维度，无篮标签）
        ArgumentCaptor<ProductInhouse> ihCap = ArgumentCaptor.forClass(ProductInhouse.class);
        verify(productInhouseMapper, times(1)).insert(ihCap.capture());
        ProductInhouse ih = ihCap.getValue();
        assertThat(ih.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(ih.getMaterialId()).isEqualTo(PRODUCT_ID);
        assertThat(ih.getProductWeight()).isEqualByComparingTo("12");
        assertThat(ih.getPlotId()).isNull();
        assertThat(ih.getEarNo()).isNull();
        assertThat(ih.getLocationId()).isEqualTo(LOCATION_ID);
    }

    @Test
    @DisplayName("pick 干货原料(dry_good, attr=2) happy：桥接 1 行 product_inhouse(其他业态食品原料同构)")
    void testPick_DryGoodMaterialBridgesInhouse() {
        ProductInfo dry = new ProductInfo();
        dry.setId(PRODUCT_ID);
        dry.setProductName("笋干·原料");
        dry.setProductUnit("kg");
        dry.setProductType(1);
        dry.setProductAttr(2);
        dry.setBelongType("dry_good");
        when(productInfoMapper.selectOne(any())).thenReturn(dry);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(dry);
        when(locationStockMapper.deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        service.pick(pickBo(new BigDecimal("7.5")));

        ArgumentCaptor<ProductInhouse> ihCap = ArgumentCaptor.forClass(ProductInhouse.class);
        verify(productInhouseMapper, times(1)).insert(ihCap.capture());
        assertThat(ihCap.getValue().getProductWeight()).isEqualByComparingTo("7.5");
        assertThat(ihCap.getValue().getProductId()).isEqualTo(PRODUCT_ID);
    }

    @Test
    @DisplayName("pick 包材(package, attr=2) happy：product 维度扣减成功，但桥接门槛不满足 → 不产 product_inhouse")
    void testPick_PackageMaterialNoBridge() {
        // 默认 setup() 的 product = belongType=package（非可打包食品业态）；显式置 attr=2 也不产 inhouse
        ProductInfo pkg = new ProductInfo();
        pkg.setId(PRODUCT_ID);
        pkg.setProductName("塑料袋");
        pkg.setProductUnit("个");
        pkg.setProductType(1);
        pkg.setProductAttr(2);            // 即使标原料，包材也不是可打包食品 → 不产 inhouse
        pkg.setBelongType("package");
        when(productInfoMapper.selectOne(any())).thenReturn(pkg);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(pkg);
        when(locationStockMapper.deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        service.pick(pickBo(new BigDecimal("100")));

        verify(locationStockMapper, times(1))
            .deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID));
        // 包材不在 PACKABLE_FOOD_BELONG_TYPES → bridgeMaterialInhouse 跳过，无 inhouse
        verify(productInhouseMapper, never()).insert(any(ProductInhouse.class));
    }

    @Test
    @DisplayName("pick 其他业态成品(other, attr=1) happy：attr 非原料 → 桥接门槛不满足 → 不产 product_inhouse（防成品当来源循环）")
    void testPick_OtherFinishedNoBridge() {
        ProductInfo finished = new ProductInfo();
        finished.setId(PRODUCT_ID);
        finished.setProductName("综合礼包·成品");
        finished.setProductUnit("盒");
        finished.setProductType(1);
        finished.setProductAttr(1);       // 成品（非原料）
        finished.setBelongType("other");
        when(productInfoMapper.selectOne(any())).thenReturn(finished);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(finished);
        when(locationStockMapper.deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        service.pick(pickBo(new BigDecimal("3")));

        verify(productInhouseMapper, never()).insert(any(ProductInhouse.class));
    }

    private MatPickBo plotPickBo(BigDecimal qty) {
        MatPickBo bo = new MatPickBo();
        bo.setPlotId(PLOT_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setQuantity(qty);
        bo.setStockOutDest("内部消耗");
        bo.setRemark("ut-veg");
        return bo;
    }

    @Test
    @DisplayName("自产果蔬 pick happy：plotId 非空 → 走 plot 维度扣减 + 流水带 plot_id + product_id=related_product")
    void testPickSelfVeg_Happy() {
        when(locationStockMapper.selectCropIdByPlot(eq(PLOT_ID))).thenReturn(CROP_ID);
        CropInfo crop = new CropInfo();
        crop.setId(CROP_ID);
        crop.setRelatedProduct(RELATED_PRODUCT_ID);
        when(cropInfoMapper.selectById(eq(CROP_ID))).thenReturn(crop);
        when(locationStockMapper.deductByPlotLocation(eq(LOCATION_ID), eq(PLOT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        service.pick(plotPickBo(new BigDecimal("12")));

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        // 无 sourceScene + dest 非 kitchen → 兜底部门领用 dept_pick_out（FIX-WMS-FLOWDICT-003）
        assertThat(f.getFlowType()).isEqualTo("dept_pick_out");
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getPlotId()).isEqualTo(PLOT_ID);
        assertThat(f.getProductId()).isEqualTo(RELATED_PRODUCT_ID);
        assertThat(f.getChangeNum()).isEqualByComparingTo("-12");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("12");
        // plot 维度领用不应走 product 维度扣减
        verify(locationStockMapper, never()).deductByProductLocation(any(), any(), any(), any());
        verify(locationStockMapper, times(1))
            .deductByPlotLocation(eq(LOCATION_ID), eq(PLOT_ID), any(BigDecimal.class), eq(USER_ID));
    }

    @Test
    @DisplayName("自产果蔬 pick：作物未配 related_product → product_id 兜底 0，不阻塞领用")
    void testPickSelfVeg_FallbackProductZero() {
        when(locationStockMapper.selectCropIdByPlot(eq(PLOT_ID))).thenReturn(CROP_ID);
        CropInfo crop = new CropInfo();
        crop.setId(CROP_ID);
        crop.setRelatedProduct(null);
        when(cropInfoMapper.selectById(eq(CROP_ID))).thenReturn(crop);
        when(locationStockMapper.deductByPlotLocation(eq(LOCATION_ID), eq(PLOT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        service.pick(plotPickBo(new BigDecimal("5")));

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getProductId()).isEqualTo(0L);
        assertThat(cap.getValue().getPlotId()).isEqualTo(PLOT_ID);
    }

    @Test
    @DisplayName("自产果蔬 pick 库存不足：deductByPlotLocation 返 0 → 抛 ServiceException")
    void testPickSelfVeg_StockInsufficient() {
        when(locationStockMapper.selectCropIdByPlot(eq(PLOT_ID))).thenReturn(null);
        when(locationStockMapper.deductByPlotLocation(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.pick(plotPickBo(new BigDecimal("999"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("自产果蔬库存不足");
    }

    @Test
    @DisplayName("pick 产品 + 地块均空：抛 ServiceException 产品 ID 不能为空")
    void testPick_NoProductNoPlot() {
        MatPickBo bo = new MatPickBo();
        bo.setQuantity(new BigDecimal("1"));
        bo.setStockOutDest("内部消耗");

        assertThatThrownBy(() -> service.pick(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("产品 ID 不能为空");
    }

    @Test
    @DisplayName("return happy：今日已领 20 / 已退 5 / 已损 3 → 退 8 通过；INSERT(pick_return_in / IN / +8)")
    void testReturn_Happy() {
        // 额度统计按来源拆分后走 IN-list 版（领用两键 + 历史 pick_out / 退回两键），loss 仍单键
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("dept_pick_out")))).thenReturn(new BigDecimal("20"));
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("pick_return_in")))).thenReturn(new BigDecimal("5"));
        when(stockFlowMapper.sumTodayByProductType(PRODUCT_ID, "loss")).thenReturn(new BigDecimal("3"));
        when(locationStockMapper.addByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(), eq(USER_ID))).thenReturn(1);

        service.returnBack(returnBo(new BigDecimal("8")));

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getFlowType()).isEqualTo("pick_return_in");
        assertThat(f.getInoutType()).isEqualTo("IN");
        assertThat(f.getChangeNum()).isEqualByComparingTo("8");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("return 超额：今日已领 20 / 已退 10 / 已损 0 → 剩 10，申请 15 → 抛今日额度不足 + 无 INSERT")
    void testReturn_OverQuota() {
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("dept_pick_out")))).thenReturn(new BigDecimal("20"));
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("pick_return_in")))).thenReturn(new BigDecimal("10"));
        when(stockFlowMapper.sumTodayByProductType(PRODUCT_ID, "loss")).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.returnBack(returnBo(new BigDecimal("15"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("今日额度不足");

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never()).addByProductLocation(any(), any(), any(), any());
    }

    @Test
    @DisplayName("loss happy（非可打包物资 package）：额度内损 3 → INSERT(loss / OT / -3)；领用时已扣货架，损耗不再二次扣 location_stock（DENGBO-R18/R19）")
    void testLoss_Happy() {
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("dept_pick_out")))).thenReturn(new BigDecimal("20"));
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("pick_return_in")))).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayByProductType(PRODUCT_ID, "loss")).thenReturn(BigDecimal.ZERO);

        Long id = service.loss(lossBo(new BigDecimal("3")));
        assertThat(id).isNotNull();

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getFlowType()).isEqualTo("loss");
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-3");
        // DENGBO-R18/R19 核心：非可打包物资（饲料/种子/包材/药品）损耗只在 stock_flow 留痕，不二次扣货架
        verify(locationStockMapper, never()).deductByProductLocation(any(), any(), any(), any());
    }

    @Test
    @DisplayName("lossByBatch 白条·猪头 null-ear 篮损耗(row70)：pork 走 reduceTodayInhouse 剥「今日领用剩余」，不再 deductStockById 扣货架")
    void testLossByBatch_PorkNullEar_DeductsInhouseNotShelf() {
        Long batchId = 90050L;
        Long locId = 7003L;
        // 白条·猪头 null-ear 篮：ear_no / plot_id 全 null（分发既不中 isVegPlotBasket 也不中 isPorkEarBasket → 落尾分支）
        LocationStock basket = new LocationStock();
        basket.setId(batchId);
        basket.setLocationId(locId);
        basket.setProductId(PRODUCT_ID);
        basket.setEarNo(null);
        basket.setWhiteBarNo("BAR-001");
        basket.setPlotId(null);
        basket.setProductName("白条·猪头");
        basket.setProductUnit("kg");
        basket.setProductStock(new BigDecimal("30"));
        basket.setDelFlag("0");
        when(locationStockMapper.selectById(batchId)).thenReturn(basket);

        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductName("白条·猪头");
        product.setProductUnit("kg");
        product.setBelongType("pork");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product);

        // 额度校验（pork 非可打包 → 走 flow SUM 口径）：已领 20 / 无退无损无饲 → 余 20，损 3 通过
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("dept_pick_out")))).thenReturn(new BigDecimal("20"));
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("pick_return_in")))).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayByProductType(PRODUCT_ID, "loss")).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayByProductType(PRODUCT_ID, "feed_out")).thenReturn(BigDecimal.ZERO);

        // reduceTodayInhouseForBasket(productId, null, null, ...)：earNo=null → 退化成 productId 维匹配今日 null-ear inhouse
        ProductInhouse wip = new ProductInhouse();
        wip.setId(60001L);
        wip.setProductId(PRODUCT_ID);
        wip.setEarNo(null);
        wip.setPlotId(null);
        wip.setProductWeight(new BigDecimal("10"));
        when(productInhouseMapper.selectList(any())).thenReturn(java.util.List.of(wip));
        when(productInhouseMapper.deductWeightById(eq(60001L), any(BigDecimal.class))).thenReturn(1);

        MatLossBo bo = new MatLossBo();
        bo.setBatchId(batchId);
        bo.setProductId(PRODUCT_ID);
        bo.setLocationId(locId);
        bo.setQuantity(new BigDecimal("3"));
        bo.setRemark("ut-row70");
        service.loss(bo);

        // 1 行 loss 流水（带篮 product_id / white_bar_no 源标签、plot_id 为 null）
        ArgumentCaptor<StockFlow> fCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(fCap.capture());
        StockFlow f = fCap.getValue();
        assertThat(f.getFlowType()).isEqualTo("loss");
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("3");
        assertThat(f.getWhiteBarNo()).isEqualTo("BAR-001");

        // row70 核心：pork 走剥离今日待打包 inhouse（deductWeightById），不再 deductStockById 扣货架
        verify(productInhouseMapper, times(1)).deductWeightById(eq(60001L), eq(new BigDecimal("3")));
        verify(locationStockMapper, never()).deductStockById(anyLong(), any(BigDecimal.class), any());
    }

    @Test
    @DisplayName("requireProduct 找不到 → 抛产品不存在 + 任何 mapper 不调")
    void testPick_ProductNotFound() {
        when(productInfoMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.pick(pickBo(new BigDecimal("1"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("产品不存在");

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never()).deductByProductLocation(any(), any(), any(), any());
    }

    @Test
    @DisplayName("pick locationId 为空（饲料子页）：按 productId 解析默认库位 → 用解析后 locId 扣减成功（D12X-MP-FEED-SUBPAGE-001）")
    void testPick_NullLocation_ResolveDefault() {
        Long defaultLoc = 7099L; // 与 bo 不传的 LOCATION_ID 区分，验证确实走解析
        when(locationStockMapper.selectDefaultLocationByProduct(PRODUCT_ID)).thenReturn(defaultLoc);
        when(locationStockMapper.deductByProductLocation(eq(defaultLoc), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        MatPickBo bo = pickBo(new BigDecimal("5"));
        bo.setLocationId(null); // 饲料子页不传库位

        service.pick(bo);

        // 默认库位被解析并用于扣减 + 写入 stock_flow.warehouse_id
        verify(locationStockMapper, times(1)).selectDefaultLocationByProduct(PRODUCT_ID);
        verify(locationStockMapper, times(1))
            .deductByProductLocation(eq(defaultLoc), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID));
        verify(stockCheckService, times(1)).assertLocationUnlocked(defaultLoc);
        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getWarehouseId()).isEqualTo(defaultLoc);
    }

    @Test
    @DisplayName("pick locationId 为空且产品无库位行：selectDefaultLocationByProduct 返 null → 抛'该产品暂无库位' + 无 INSERT")
    void testPick_NullLocation_NoStockRow() {
        when(locationStockMapper.selectDefaultLocationByProduct(PRODUCT_ID)).thenReturn(null);

        MatPickBo bo = pickBo(new BigDecimal("5"));
        bo.setLocationId(null);

        assertThatThrownBy(() -> service.pick(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("该产品暂无库位");

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never()).deductByProductLocation(any(), any(), any(), any());
    }

    @Test
    @DisplayName("return locationId 为空（饲料子页）：解析默认库位 → 用解析后 locId 加库存（D12X-MP-FEED-SUBPAGE-001）")
    void testReturn_NullLocation_ResolveDefault() {
        Long defaultLoc = 7099L;
        when(locationStockMapper.selectDefaultLocationByProduct(PRODUCT_ID)).thenReturn(defaultLoc);
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("dept_pick_out")))).thenReturn(new BigDecimal("20"));
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("pick_return_in")))).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayByProductType(PRODUCT_ID, "loss")).thenReturn(BigDecimal.ZERO);
        when(locationStockMapper.addByProductLocation(eq(defaultLoc), eq(PRODUCT_ID), any(), eq(USER_ID))).thenReturn(1);

        MatReturnBo bo = returnBo(new BigDecimal("3"));
        bo.setLocationId(null);

        service.returnBack(bo);

        verify(locationStockMapper, times(1)).selectDefaultLocationByProduct(PRODUCT_ID);
        verify(locationStockMapper, times(1)).addByProductLocation(eq(defaultLoc), eq(PRODUCT_ID), any(), eq(USER_ID));
        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getWarehouseId()).isEqualTo(defaultLoc);
    }

    // ===== FIX-WMS-MATISSUE-001：物资领用扩 5 业态 查询端点 =====

    @Test
    @DisplayName("issueLocations happy：业态非空 → 透传 mapper 返 chip 列表")
    void testIssueLocations_Happy() {
        MatIssueLocationVo loc = new MatIssueLocationVo();
        loc.setLocationId(LOCATION_ID);
        loc.setLocationCode("LOC-WB-01");
        loc.setLocationName("白条库");
        when(locationStockMapper.selectMatIssueLocations(List.of("pork"))).thenReturn(List.of(loc));

        List<MatIssueLocationVo> result = service.issueLocations("pork");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLocationName()).isEqualTo("白条库");
        verify(locationStockMapper, times(1)).selectMatIssueLocations(List.of("pork"));
    }

    @Test
    @DisplayName("issueLocations 猪肉业态多值：'pork,white_bar' → split 成 [pork, white_bar] 传 mapper")
    void testIssueLocations_MultiBelongType() {
        when(locationStockMapper.selectMatIssueLocations(List.of("pork", "white_bar")))
            .thenReturn(Collections.emptyList());

        service.issueLocations("pork,white_bar");

        verify(locationStockMapper, times(1)).selectMatIssueLocations(List.of("pork", "white_bar"));
    }

    @Test
    @DisplayName("issueLocations 业态为空 → 抛 ServiceException + 不调 mapper")
    void testIssueLocations_BlankBelongType() {
        assertThatThrownBy(() -> service.issueLocations(" "))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("业态类型不能为空");

        verify(locationStockMapper, never()).selectMatIssueLocations(any());
    }

    @Test
    @DisplayName("issueItems happy：locationId 为空 → 透传 null locId + 当前 userId 查列表")
    void testIssueItems_NullLocation() {
        MatIssueItemVo item = new MatIssueItemVo();
        item.setProductId(PRODUCT_ID);
        item.setProductName("番茄");
        item.setCurrentStock(new BigDecimal("221.23"));
        item.setTodayPicked(new BigDecimal("25.22"));
        when(locationStockMapper.selectMatIssueItems(List.of("vegetable"), null, USER_ID)).thenReturn(List.of(item));

        List<MatIssueItemVo> result = service.issueItems("vegetable", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductName()).isEqualTo("番茄");
        verify(locationStockMapper, times(1)).selectMatIssueItems(List.of("vegetable"), null, USER_ID);
    }

    @Test
    @DisplayName("issueItems：locationId 字符串（snowflake string）→ parse 成 Long 传 mapper（chip 选中态）")
    void testIssueItems_WithLocation() {
        String locStr = "2058525064717926401"; // 19 位 snowflake，验证 parse 不截断
        when(locationStockMapper.selectMatIssueItems(eq(List.of("pork", "white_bar")), eq(Long.valueOf(locStr)), eq(USER_ID)))
            .thenReturn(Collections.emptyList());

        service.issueItems("pork,white_bar", locStr);

        verify(locationStockMapper, times(1))
            .selectMatIssueItems(List.of("pork", "white_bar"), Long.valueOf(locStr), USER_ID);
    }

    @Test
    @DisplayName("issueItems：locationId 非法字符串 → 抛 ServiceException 库位 ID 非法")
    void testIssueItems_IllegalLocation() {
        assertThatThrownBy(() -> service.issueItems("pork", "abc"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("库位 ID 非法");

        verify(locationStockMapper, never()).selectMatIssueItems(any(), any(), any());
    }

    // ===== WMS-OUTSOURCE-001：物资领用 by-locationType 端点 =====

    @Test
    @DisplayName("issueLocationsByType happy：locationType 透传 LocationInfoMapper 返 chip 列表")
    void testIssueLocationsByType_Happy() {
        MatIssueLocationVo loc = new MatIssueLocationVo();
        loc.setLocationId(LOCATION_ID);
        loc.setLocationName("种植主库");
        when(locationInfoMapper.selectMatIssueLocationsByType("crop_loc")).thenReturn(List.of(loc));

        List<MatIssueLocationVo> result = service.issueLocationsByType("crop_loc");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLocationName()).isEqualTo("种植主库");
        verify(locationInfoMapper, times(1)).selectMatIssueLocationsByType("crop_loc");
    }

    @Test
    @DisplayName("issueLocationsByType 空 locationType → 抛 ServiceException + 不调 mapper")
    void testIssueLocationsByType_Blank() {
        assertThatThrownBy(() -> service.issueLocationsByType("  "))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("库位类型不能为空");
        verify(locationInfoMapper, never()).selectMatIssueLocationsByType(any());
    }

    @Test
    @DisplayName("issueItemsByType happy：buyClass 透传 + locationId snowflake string parse 不截断 + resolver 回填 thumb")
    void testIssueItemsByType_Happy() {
        String locStr = "2058525064717926401"; // 19 位 snowflake，验证 parse 不截断
        MatIssueItemVo item = new MatIssueItemVo();
        item.setProductId(PRODUCT_ID);
        item.setProductName("外购饲料A");
        item.setBuyClass("feed");
        item.setBelongType(null);
        item.setProductThumb("oss-raw-id");
        item.setCurrentStock(new BigDecimal("120.50"));
        when(locationStockMapper.selectMatIssueItemsByType(eq("farm_loc"), eq(Long.valueOf(locStr)), eq(USER_ID)))
            .thenReturn(new java.util.ArrayList<>(List.of(item)));
        when(imageUrlResolver.resolveList(any())).thenReturn(List.of("https://oss/url-feed.png"));

        List<MatIssueItemVo> result = service.issueItemsByType("farm_loc", locStr);

        assertThat(result).hasSize(1);
        // buyClass 透传（mp 去重成分类 chip 的数据源）
        assertThat(result.get(0).getBuyClass()).isEqualTo("feed");
        // thumb 经 resolver 回填成 public url
        assertThat(result.get(0).getProductThumb()).isEqualTo("https://oss/url-feed.png");
        verify(locationStockMapper, times(1))
            .selectMatIssueItemsByType("farm_loc", Long.valueOf(locStr), USER_ID);
        verify(imageUrlResolver, times(1)).resolveList(any());
    }

    @Test
    @DisplayName("issueItemsByType 空 locationType → 抛 ServiceException + 不调 mapper")
    void testIssueItemsByType_Blank() {
        assertThatThrownBy(() -> service.issueItemsByType("", "100"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("库位类型不能为空");
        verify(locationStockMapper, never()).selectMatIssueItemsByType(any(), any(), any());
    }

    @Test
    @DisplayName("pickByBatch happy(猪肉耳号卡 r164)：batchId 非空 → 按 (product,ear,库位) FIFO 扣该组篮(此处单篮 deductStockById) + 1 行 dept_pick_out 流水(带 product_id/ear_no 源标签) + 1 行 inhouse(带 ear_no 源标签)")
    void testPickByBatch_Happy() {
        Long batchId = 90011L;
        Long locId = 7003L;
        LocationStock basket = new LocationStock();
        basket.setId(batchId);
        basket.setLocationId(locId);
        basket.setProductId(PRODUCT_ID);
        basket.setEarNo("EAR-X");
        basket.setProductName("猪肉·精瘦肉");
        basket.setProductUnit("kg");
        basket.setProductStock(new BigDecimal("41.4"));
        basket.setDelFlag("0");
        when(locationStockMapper.selectById(batchId)).thenReturn(basket);
        // r164：猪肉耳号卡走 consumePorkEarBaskets 的 (product,ear,库位) FIFO 查询
        when(locationStockMapper.selectList(any())).thenReturn(java.util.List.of(basket));

        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductName("猪肉·精瘦肉");
        product.setProductUnit("kg");
        product.setProductType(1);
        product.setBelongType("pork");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product);

        when(locationStockMapper.deductStockById(eq(batchId), any(BigDecimal.class), eq(USER_ID))).thenReturn(1);

        MatPickBo bo = new MatPickBo();
        bo.setBatchId(batchId);
        bo.setProductId(PRODUCT_ID);
        bo.setQuantity(new BigDecimal("10"));
        bo.setStockOutDest("门店发货");
        bo.setRemark("ut-batch");
        service.pick(bo);

        // 1 行 dept_pick_out 流水：带篮的 product_id / ear_no / plot_id 源标签（无 sourceScene 兜底部门领用）
        ArgumentCaptor<StockFlow> fCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(fCap.capture());
        StockFlow f = fCap.getValue();
        assertThat(f.getFlowType()).isEqualTo("dept_pick_out");
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(f.getEarNo()).isEqualTo("EAR-X");
        assertThat(f.getPlotId()).isNull();
        assertThat(f.getWarehouseId()).isEqualTo(locId);
        assertThat(f.getChangeNum()).isEqualByComparingTo("-10");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("10");

        // 扣选中篮（by id），不走 product / plot 维度兜底
        verify(locationStockMapper, times(1)).deductStockById(eq(batchId), eq(new BigDecimal("10")), eq(USER_ID));
        verify(locationStockMapper, never()).deductByProductLocation(any(), any(), any(), any());
        verify(locationStockMapper, never()).deductByPlotLocation(any(), any(), any(), any());

        // 1 行 product_inhouse：带篮的 ear_no + plot_id 源标签 → 打包链追溯
        ArgumentCaptor<ProductInhouse> ihCap = ArgumentCaptor.forClass(ProductInhouse.class);
        verify(productInhouseMapper, times(1)).insert(ihCap.capture());
        ProductInhouse ih = ihCap.getValue();
        assertThat(ih.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(ih.getEarNo()).isEqualTo("EAR-X");
        assertThat(ih.getPlotId()).isNull();
        assertThat(ih.getProductWeight()).isEqualByComparingTo("10");
        assertThat(ih.getMaterialId()).isEqualTo(PRODUCT_ID);
        assertThat(ih.getMaterialConsume()).isEqualByComparingTo("10");
        assertThat(ih.getLocationId()).isEqualTo(locId);
    }

    @Test
    @DisplayName("pickByBatch 猪肉耳号卡跨篮 FIFO(r164)：同耳号同库位 2 篮(41.4+38) 领用 50 → FIFO 41.4+8.6 → 2 次 deductStockById + 2 行 inhouse")
    void testPickByBatch_PorkEar_MultiBasket_FIFO() {
        Long batchId = 90011L;
        Long locId = 7003L;
        LocationStock b1 = new LocationStock();
        b1.setId(batchId);
        b1.setLocationId(locId);
        b1.setProductId(PRODUCT_ID);
        b1.setEarNo("EAR-X");
        b1.setProductName("猪肉·精瘦肉");
        b1.setProductUnit("kg");
        b1.setProductStock(new BigDecimal("41.4"));
        b1.setDelFlag("0");
        LocationStock b2 = new LocationStock();
        b2.setId(90012L);
        b2.setLocationId(locId);
        b2.setProductId(PRODUCT_ID);
        b2.setEarNo("EAR-X");
        b2.setProductName("猪肉·精瘦肉");
        b2.setProductUnit("kg");
        b2.setProductStock(new BigDecimal("38"));
        b2.setDelFlag("0");
        when(locationStockMapper.selectById(batchId)).thenReturn(b1);
        when(locationStockMapper.selectList(any())).thenReturn(java.util.List.of(b1, b2));
        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductName("猪肉·精瘦肉");
        product.setProductUnit("kg");
        product.setProductType(1);
        product.setBelongType("pork");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product);
        when(locationStockMapper.deductStockById(anyLong(), any(BigDecimal.class), eq(USER_ID))).thenReturn(1);

        MatPickBo bo = new MatPickBo();
        bo.setBatchId(batchId);
        bo.setProductId(PRODUCT_ID);
        bo.setQuantity(new BigDecimal("50"));
        bo.setStockOutDest("门店发货");
        service.pick(bo);

        // FIFO：首篮扣满 41.4，次篮扣 8.6（跨篮，不只扣首篮）
        verify(locationStockMapper, times(1)).deductStockById(eq(90011L), eq(new BigDecimal("41.4")), eq(USER_ID));
        verify(locationStockMapper, times(1)).deductStockById(eq(90012L), eq(new BigDecimal("8.6")), eq(USER_ID));
        // 2 行 inhouse（每扣一篮产一行，带 ear_no 源标签）
        verify(productInhouseMapper, times(2)).insert(any(ProductInhouse.class));
    }

    /** 猪肉 null-ear 组篮 stub：ear_no / plot_id 全空 + product belong_type=pork（admin 聚合行 / mp 退货卡语义）。 */
    private LocationStock nullEarPorkBasket(Long id, Long locId, String whiteBarNo, String stock) {
        LocationStock b = new LocationStock();
        b.setId(id);
        b.setLocationId(locId);
        b.setProductId(PRODUCT_ID);
        b.setEarNo(null);
        b.setWhiteBarNo(whiteBarNo);
        b.setPlotId(null);
        b.setProductName("筒子骨");
        b.setProductUnit("kg");
        b.setProductStock(new BigDecimal(stock));
        b.setDelFlag("0");
        return b;
    }

    private ProductInfo porkProduct(String name) {
        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductName(name);
        product.setProductUnit("kg");
        product.setProductType(1);
        product.setBelongType("pork");
        return product;
    }

    @Test
    @DisplayName("pickByBatch 猪肉 null-ear 组篮跨库位 FIFO：2 篮(0.2@库位A + 4.0@库位B) 领 4.2 → 2 次 deductStockById + 2 行 inhouse(白条号/库位逐篮带上)，首篮小库存不再锁死整组")
    void testPickByBatch_PorkNullEarGroup_CrossLocationFifo() {
        Long batchId = 90021L;
        Long locA = 7001L;
        Long locB = 7002L;
        LocationStock b1 = nullEarPorkBasket(batchId, locA, "BAR-A", "0.2");
        LocationStock b2 = nullEarPorkBasket(90022L, locB, "BAR-B", "4.0");
        when(locationStockMapper.selectById(batchId)).thenReturn(b1);
        when(locationStockMapper.selectList(any())).thenReturn(java.util.List.of(b1, b2));
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(porkProduct("筒子骨"));
        when(locationStockMapper.deductStockById(anyLong(), any(BigDecimal.class), eq(USER_ID))).thenReturn(1);

        MatPickBo bo = new MatPickBo();
        bo.setBatchId(batchId);
        bo.setProductId(PRODUCT_ID);
        bo.setQuantity(new BigDecimal("4.2"));
        bo.setStockOutDest("门店发货");
        service.pick(bo);

        // 1 行 pick_out 流水：组标签 ear_no 空、white_bar / warehouse 取首篮、量=总申请量
        ArgumentCaptor<StockFlow> fCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(fCap.capture());
        StockFlow f = fCap.getValue();
        assertThat(f.getEarNo()).isNull();
        assertThat(f.getWhiteBarNo()).isEqualTo("BAR-A");
        assertThat(f.getWarehouseId()).isEqualTo(locA);
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("4.2");

        // 跨库位 FIFO：首篮扣满 0.2、次篮（另一库位）扣 4.0——不再「显示 4.2 只能领 0.2」
        verify(locationStockMapper, times(1)).deductStockById(eq(batchId), eq(new BigDecimal("0.2")), eq(USER_ID));
        verify(locationStockMapper, times(1)).deductStockById(eq(90022L), eq(new BigDecimal("4.0")), eq(USER_ID));
        // 组内第二库位也过盘点锁校验（首篮库位在入口已校验）
        verify(stockCheckService, times(1)).assertLocationUnlocked(locA);
        verify(stockCheckService, times(1)).assertLocationUnlocked(locB);
        // 2 行 inhouse：白条号 / 库位源标签逐篮带上（打包 / 追溯链不断）
        ArgumentCaptor<ProductInhouse> ihCap = ArgumentCaptor.forClass(ProductInhouse.class);
        verify(productInhouseMapper, times(2)).insert(ihCap.capture());
        List<ProductInhouse> rows = ihCap.getAllValues();
        assertThat(rows.get(0).getWhiteBarNo()).isEqualTo("BAR-A");
        assertThat(rows.get(0).getLocationId()).isEqualTo(locA);
        assertThat(rows.get(0).getProductWeight()).isEqualByComparingTo("0.2");
        assertThat(rows.get(1).getWhiteBarNo()).isEqualTo("BAR-B");
        assertThat(rows.get(1).getLocationId()).isEqualTo(locB);
        assertThat(rows.get(1).getProductWeight()).isEqualByComparingTo("4.0");
        assertThat(rows.get(0).getEarNo()).isNull();
    }

    @Test
    @DisplayName("lossByBatch 猪肉 null-ear 组篮净额守卫：今日组净领 2 < 损 3 → 抛「超过该篮子今日领用剩余」+ 零写入（row38 兄弟路径按组口径）")
    void testLossByBatch_PorkNullEarGroup_OverNet_Rejected() {
        Long batchId = 90031L;
        LocationStock basket = nullEarPorkBasket(batchId, 7001L, "BAR-A", "10");
        when(locationStockMapper.selectById(batchId)).thenReturn(basket);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(porkProduct("筒子骨"));
        // 产品级额度充足（已领 20），但该 null-ear 组今日净领只有 2 → 组级守卫拦截
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("dept_pick_out")))).thenReturn(new BigDecimal("20"));
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("pick_return_in")))).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayByProductType(PRODUCT_ID, "loss")).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayByProductType(PRODUCT_ID, "feed_out")).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayNetPickedByNullEarGroup(PRODUCT_ID)).thenReturn(new BigDecimal("2"));

        MatLossBo bo = new MatLossBo();
        bo.setBatchId(batchId);
        bo.setProductId(PRODUCT_ID);
        bo.setQuantity(new BigDecimal("3"));

        assertThatThrownBy(() -> service.loss(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("超过该篮子今日领用剩余");

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(productInhouseMapper, never()).deductWeightById(anyLong(), any(BigDecimal.class));
        verify(locationStockMapper, never()).deductStockById(anyLong(), any(BigDecimal.class), any());
    }

    @Test
    @DisplayName("feedByBatch 猪肉 null-ear 组篮：猪肉无饲喂业务 → 抛「猪肉不支持饲料饲喂」+ 零写入（与耳号卡同口径后端防御）")
    void testFeedByBatch_PorkNullEarGroup_Rejected() {
        Long batchId = 90041L;
        LocationStock basket = nullEarPorkBasket(batchId, 7001L, "BAR-A", "10");
        when(locationStockMapper.selectById(batchId)).thenReturn(basket);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(porkProduct("筒子骨"));

        MatFeedBo bo = new MatFeedBo();
        bo.setBatchId(batchId);
        bo.setProductId(PRODUCT_ID);
        bo.setQuantity(new BigDecimal("1"));

        assertThatThrownBy(() -> service.feed(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("猪肉不支持饲料饲喂");

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(feedLogMapper, never()).insert(any(FeedLog.class));
        verify(locationStockMapper, never()).deductStockById(anyLong(), any(BigDecimal.class), any());
    }

    // -------- feed（F0-8 验收门 · TEST-GAP-4：同文件 pick/return/loss 已覆盖，feed 此前裸奔） --------

    private MatFeedBo feedBo(BigDecimal qty) {
        MatFeedBo bo = new MatFeedBo();
        bo.setProductId(PRODUCT_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setQuantity(qty);
        bo.setRemark("ut-feed");
        return bo;
    }

    /** feed 用非可打包物资（饲料）product stub：belong_type=feed → 走 location_stock 扣减分支。 */
    private void stubFeedProduct() {
        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductName("育肥饲料");
        product.setProductUnit("kg");
        product.setBelongType("feed");
        when(productInfoMapper.selectOne(any())).thenReturn(product);
    }

    @Test
    @DisplayName("feed happy（饲料，非可打包）：额度内喂 20 → 两写：流水 feed_out/OT/-20 + feed_log(warehouse, weight=20)；领用时已扣货架，饲喂不再二次扣 location_stock（DENGBO-R18/R19 同口径）")
    void testFeed_Happy_ThreeWrites() {
        stubFeedProduct();
        // 今日额度：已领 50 − 已退 0 − 已损 0 − 已饲喂 10 = 剩 40 ≥ 20
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("dept_pick_out")))).thenReturn(new BigDecimal("50"));
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("pick_return_in")))).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayByProductType(PRODUCT_ID, "loss")).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayByProductType(PRODUCT_ID, "feed_out")).thenReturn(new BigDecimal("10"));

        Long flowId = service.feed(feedBo(new BigDecimal("20")));
        assertThat(flowId).isNotNull();

        // 写 1：流水 feed_out / OT / change_num=-20
        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        StockFlow f = flowCap.getValue();
        assertThat(f.getFlowType()).isEqualTo("feed_out");
        assertThat(f.getStockOutDest()).isEqualTo("feed");
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-20");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("20");
        assertThat(f.getOperatorId()).isEqualTo(USER_ID);
        // DENGBO-R18/R19 同口径：非可打包物资领用时已扣货架，饲喂只留流水，不二次扣 location_stock
        verify(locationStockMapper, never()).deductByProductLocation(any(), any(), any(), any());
        // 写 2：饲喂台账 feed_log（feed_type=warehouse，行64 来源②；作物维度留空）
        ArgumentCaptor<FeedLog> logCap = ArgumentCaptor.forClass(FeedLog.class);
        verify(feedLogMapper, times(1)).insert(logCap.capture());
        FeedLog log = logCap.getValue();
        assertThat(log.getFeedType()).isEqualTo("warehouse");
        assertThat(log.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(log.getLocationId()).isEqualTo(LOCATION_ID);
        assertThat(log.getFeedWeight()).isEqualByComparingTo("20");
        assertThat(log.getOperatorId()).isEqualTo(USER_ID);
        assertThat(log.getCropId()).isNull();
    }

    @Test
    @DisplayName("feed 超量：今日剩余额度 5 < 申请 20 → 抛今日额度不足 + 三写全无（流水/货架/feed_log 零写入）")
    void testFeed_OverQuota_NoWrites() {
        stubFeedProduct();
        // 已领 10 − 已退 0 − 已损 0 − 已饲喂 5 = 剩 5 < 20
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("dept_pick_out")))).thenReturn(new BigDecimal("10"));
        when(stockFlowMapper.sumTodayByProductTypes(eq(PRODUCT_ID), argThat(l -> l != null && l.contains("pick_return_in")))).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayByProductType(PRODUCT_ID, "loss")).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayByProductType(PRODUCT_ID, "feed_out")).thenReturn(new BigDecimal("5"));

        assertThatThrownBy(() -> service.feed(feedBo(new BigDecimal("20"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("今日额度不足");

        // 三写全无：额度闸在任何写入之前
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never()).deductByProductLocation(any(), any(), any(), any());
        verify(feedLogMapper, never()).insert(any(FeedLog.class));
    }

    @Test
    @DisplayName("feed 可打包果蔬原料：剥离今日待打包 inhouse WIP（deductWeightById），不二次扣 location_stock；feed_log 照写")
    void testFeed_PackableFood_ReducesInhouseNotShelf() {
        // 可打包食品原料（vegetable）：领用时已离货架进「待打包」product_inhouse
        ProductInfo veg = new ProductInfo();
        veg.setId(PRODUCT_ID);
        veg.setProductName("鸡毛菜");
        veg.setProductUnit("kg");
        veg.setBelongType("vegetable");
        when(productInfoMapper.selectOne(any())).thenReturn(veg);
        // 额度走「今日待打包余额」口径
        when(productInhouseMapper.sumTodayRemaining(PRODUCT_ID)).thenReturn(new BigDecimal("100"));
        ProductInhouse wip = new ProductInhouse();
        wip.setId(301L);
        wip.setProductId(PRODUCT_ID);
        wip.setProductWeight(new BigDecimal("50"));
        when(productInhouseMapper.selectList(any())).thenReturn(List.of(wip));
        when(productInhouseMapper.deductWeightById(eq(301L), any())).thenReturn(1);

        service.feed(feedBo(new BigDecimal("20")));

        // 剥离 WIP（只减 inhouse），不二次扣货架（否则货架双扣）
        verify(productInhouseMapper, times(1)).deductWeightById(eq(301L), eq(new BigDecimal("20")));
        verify(locationStockMapper, never()).deductByProductLocation(any(), any(), any(), any());
        // 流水 + feed_log 照写
        verify(stockFlowMapper, times(1)).insert(any(StockFlow.class));
        verify(feedLogMapper, times(1)).insert(any(FeedLog.class));
    }

    // -------- 【三期】分篮 / 标识继承（V6 row92） --------

    /**
     * {@code pickByProduct} 的三个分支必须同一口径：<b>product 维度的领用一律只动普通货</b>。
     * 猪肉分支靠 {@code ear_no IS NOT NULL} 天然排除三期篮、其余分支靠
     * {@code deductByProductLocation} 的 {@code third_phase = 0}；果蔬分支的 FIFO 是纯 Java 侧
     * wrapper，漏了就只有它一条会把三期篮扣掉，而流水上只有一条按总量记的 pick_out ——
     * 三期与普通货混在同一笔里拆不开，「三期总出库」直接漏计。
     */
    @Test
    @DisplayName("pick 自产果蔬 product 维度 FIFO：wrapper 带 third_phase 过滤 → 不扣三期篮（与另外两个分支同口径）")
    void testPick_VegFifoExcludesThirdPhaseBaskets() {
        ProductInfo veg = new ProductInfo();
        veg.setId(PRODUCT_ID);
        veg.setProductName("黄秋葵");
        veg.setProductUnit("kg");
        veg.setProductType(1);
        veg.setProductAttr(2);
        veg.setBelongType("vegetable");
        when(productInfoMapper.selectOne(any())).thenReturn(veg);

        LocationStock normal = new LocationStock();
        normal.setId(8001L);
        normal.setProductId(PRODUCT_ID);
        normal.setPlotId(7001L);
        normal.setThirdPhase(0);
        normal.setProductStock(new BigDecimal("40"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LocationStock>> wrapCap =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        when(locationStockMapper.selectList(any())).thenReturn(List.of(normal));
        when(locationStockMapper.deductStockById(anyLong(), any(BigDecimal.class), eq(USER_ID))).thenReturn(1);

        service.pick(pickBo(new BigDecimal("40")));

        verify(locationStockMapper).selectList(wrapCap.capture());
        String sql = wrapCap.getValue().getTargetSql().toLowerCase(java.util.Locale.ROOT);
        assertThat(sql).contains("third_phase");
    }

    /**
     * 「按源手选」领用：用户明确点了哪一篮，标识就必须随那一篮走 —— 这是三期货唯一还能被 mp 领出去的路径
     * （product 维度 FIFO 已把三期篮排除在外）。
     */
    @Test
    @DisplayName("pickByBatch：选中的是三期篮 → pick_out 流水继承 third_phase=1")
    void testPickByBatch_InheritsThirdPhaseFromSelectedBasket() {
        ProductInfo pack = new ProductInfo();
        pack.setId(PRODUCT_ID);
        pack.setProductName("塑料袋");
        pack.setProductUnit("个");
        pack.setBelongType("package");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(pack);

        LocationStock thirdPhaseBasket = new LocationStock();
        thirdPhaseBasket.setId(8801L);
        thirdPhaseBasket.setProductId(PRODUCT_ID);
        thirdPhaseBasket.setLocationId(LOCATION_ID);
        thirdPhaseBasket.setProductStock(new BigDecimal("30"));
        thirdPhaseBasket.setThirdPhase(1);
        thirdPhaseBasket.setDelFlag("0");
        when(locationStockMapper.selectById(8801L)).thenReturn(thirdPhaseBasket);
        when(locationStockMapper.deductStockById(eq(8801L), any(BigDecimal.class), eq(USER_ID))).thenReturn(1);

        MatPickBo bo = pickBo(new BigDecimal("10"));
        bo.setBatchId(8801L);
        service.pick(bo);

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getThirdPhase()).isEqualTo(1);
    }

    @Test
    @DisplayName("pickByBatch：选中普通篮（third_phase=null 存量行）→ 流水归一成 0，不误标、不写空 NOT NULL 列")
    void testPickByBatch_LegacyNullThirdPhaseNormalized() {
        ProductInfo pack = new ProductInfo();
        pack.setId(PRODUCT_ID);
        pack.setProductName("塑料袋");
        pack.setProductUnit("个");
        pack.setBelongType("package");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(pack);

        LocationStock legacyBasket = new LocationStock();
        legacyBasket.setId(8802L);
        legacyBasket.setProductId(PRODUCT_ID);
        legacyBasket.setLocationId(LOCATION_ID);
        legacyBasket.setProductStock(new BigDecimal("30"));
        legacyBasket.setThirdPhase(null);
        legacyBasket.setDelFlag("0");
        when(locationStockMapper.selectById(8802L)).thenReturn(legacyBasket);
        when(locationStockMapper.deductStockById(eq(8802L), any(BigDecimal.class), eq(USER_ID))).thenReturn(1);

        MatPickBo bo = pickBo(new BigDecimal("10"));
        bo.setBatchId(8802L);
        service.pick(bo);

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getThirdPhase()).isEqualTo(0);
    }

    /**
     * product 维度退回的回补篮查找带 {@code LIMIT 1} —— 三期篮的 plot/ear/white_bar 也全 NULL，
     * 不加 third_phase 过滤完全可能选中它，把退回的普通货加进三期账。配套流水是 {@code third_phase=0}，
     * 篮子却是三期的，两边当场就对不上。
     */
    @Test
    @DisplayName("returnBack（product 维度）：回补篮查找带 third_phase 过滤 → 不会把普通货退进三期篮")
    void testReturn_BasketLookupExcludesThirdPhase() {
        ProductInfo veg = new ProductInfo();
        veg.setId(PRODUCT_ID);
        veg.setProductName("黄秋葵");
        veg.setProductUnit("kg");
        veg.setProductType(1);
        veg.setProductAttr(2);
        veg.setBelongType("vegetable");
        when(productInfoMapper.selectOne(any())).thenReturn(veg);
        // 可打包食品原料的今日额度走「今日待打包余额」口径（不是 pick/return/loss 流水差额）
        when(productInhouseMapper.sumTodayRemaining(PRODUCT_ID)).thenReturn(new BigDecimal("100"));

        ProductInhouse wip = new ProductInhouse();
        wip.setId(401L);
        wip.setProductId(PRODUCT_ID);
        wip.setProductWeight(new BigDecimal("30"));
        wip.setLocationId(LOCATION_ID);
        wip.setPlotId(null);                       // 历史无地块 inhouse → 走 isNull(plotId) 那一支
        when(productInhouseMapper.selectList(any())).thenReturn(List.of(wip));
        when(productInhouseMapper.deductWeightById(eq(401L), any())).thenReturn(1);
        when(locationStockMapper.selectOne(any())).thenReturn(null);

        service.returnBack(returnBo(new BigDecimal("8")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LocationStock>> bwCap =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(locationStockMapper).selectOne(bwCap.capture());
        assertThat(bwCap.getValue().getTargetSql().toLowerCase(java.util.Locale.ROOT)).contains("third_phase");
    }

}
