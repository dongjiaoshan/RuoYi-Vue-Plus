package org.dromara.djs.warehouse.flow.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.domain.bo.MatLossBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatPickBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatReturnBo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueItemVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueLocationVo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper productInhouseMapper;
    @Mock private CropInfoMapper cropInfoMapper;
    @Mock private BarInfoMapper barInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private IStockCheckService stockCheckService;
    @Mock private ImageUrlResolver imageUrlResolver;

    private MatFlowServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    private static final Long USER_ID = 9001L;
    private static final Long PRODUCT_ID = 8001L;
    private static final Long LOCATION_ID = 7001L;
    private static final Long PLOT_ID = 6001L;
    private static final Long CROP_ID = 5001L;
    private static final Long RELATED_PRODUCT_ID = 8002L;

    @BeforeEach
    void setup() {
        service = new MatFlowServiceImpl(stockFlowMapper, locationStockMapper, productInfoMapper, productInhouseMapper, cropInfoMapper, barInfoMapper, bizCodeGenerator, stockCheckService, imageUrlResolver);
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
    @DisplayName("pick happy：扣库存成功 → 流水 INSERT(pick_out / OT / change_num=-20 / change_quantity=20)")
    void testPick_Happy() {
        when(locationStockMapper.deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        service.pick(pickBo(new BigDecimal("20")));

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getFlowType()).isEqualTo("pick_out");
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-20");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("20");
        assertThat(f.getStockOutDest()).isEqualTo("内部消耗");
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
    @DisplayName("pick 库存不足：deductByProductLocation 返 0 → 抛 ServiceException 库存不足")
    void testPick_StockInsufficient() {
        when(locationStockMapper.deductByProductLocation(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.pick(pickBo(new BigDecimal("999"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("库存不足");

        // INSERT 已发生（Spring @Transactional 集成测试覆盖回滚行为；单测只验异常抛出 + service 中断）
        verify(stockFlowMapper, times(1)).insert(any(StockFlow.class));
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
        assertThat(f.getFlowType()).isEqualTo("pick_out");
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
    @DisplayName("return happy：今日已领 20 / 已退 5 / 已损 3 → 退 8 通过；INSERT(return_in / IN / +8)")
    void testReturn_Happy() {
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "pick_out")).thenReturn(new BigDecimal("20"));
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "return_in")).thenReturn(new BigDecimal("5"));
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "loss")).thenReturn(new BigDecimal("3"));
        when(locationStockMapper.addByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(), eq(USER_ID))).thenReturn(1);

        service.returnBack(returnBo(new BigDecimal("8")));

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getFlowType()).isEqualTo("return_in");
        assertThat(f.getInoutType()).isEqualTo("IN");
        assertThat(f.getChangeNum()).isEqualByComparingTo("8");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("return 超额：今日已领 20 / 已退 10 / 已损 0 → 剩 10，申请 15 → 抛今日额度不足 + 无 INSERT")
    void testReturn_OverQuota() {
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "pick_out")).thenReturn(new BigDecimal("20"));
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "return_in")).thenReturn(new BigDecimal("10"));
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "loss")).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.returnBack(returnBo(new BigDecimal("15"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("今日额度不足");

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never()).addByProductLocation(any(), any(), any(), any());
    }

    @Test
    @DisplayName("loss happy：额度内损 3 → INSERT(loss / OT / -3) + 扣库存（影响行 0 不抛）")
    void testLoss_Happy() {
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "pick_out")).thenReturn(new BigDecimal("20"));
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "return_in")).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "loss")).thenReturn(BigDecimal.ZERO);
        when(locationStockMapper.deductByProductLocation(any(), any(), any(), any())).thenReturn(0);

        Long id = service.loss(lossBo(new BigDecimal("3")));
        assertThat(id).isNotNull();

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getFlowType()).isEqualTo("loss");
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-3");
        // loss 即使 affected==0 也不抛（service 内部 log.warn 兜底，本测不验证 log）
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
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "pick_out")).thenReturn(new BigDecimal("20"));
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "return_in")).thenReturn(BigDecimal.ZERO);
        when(stockFlowMapper.sumTodayByUserProductType(USER_ID, PRODUCT_ID, "loss")).thenReturn(BigDecimal.ZERO);
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

}
