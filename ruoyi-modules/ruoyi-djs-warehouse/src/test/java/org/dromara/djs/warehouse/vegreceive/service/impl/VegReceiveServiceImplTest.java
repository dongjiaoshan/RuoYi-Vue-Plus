package org.dromara.djs.warehouse.vegreceive.service.impl;

import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.vegreceive.domain.VegReceive;
import org.dromara.djs.warehouse.vegreceive.domain.bo.VegInboundBo;
import org.dromara.djs.warehouse.vegreceive.mapper.VegReceiveMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VegReceiveServiceImpl} 单测（G2 双键篮）。
 *
 * <p>聚焦自产月台中转再入库 {@code inbound} 的 {@code location_stock} 兜底 INSERT 分支（无库存行）：
 * 与直接入库一致，建 {@code product_id+plot_id} 双键篮——product_id 经
 * {@code crop.related_product} 解析（命中 → set；未配置 → 退化为 plot 单键，不抛、不阻塞）。</p>
 *
 * <ol>
 *   <li>related_product 命中 → 兜底篮 product_id = 映射值 + plot_id 双键</li>
 *   <li>related_product 为空 → 兜底篮 product_id=NULL 单键（不抛、入库照常）</li>
 *   <li>已有库存行（addStockByPlotLocation 返 &gt;0）→ 不走兜底 INSERT</li>
 * </ol>
 *
 * @author djs
 * @since G2
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VegReceiveServiceImpl 单元测试（G2 双键篮）")
class VegReceiveServiceImplTest {

    @Mock
    private VegReceiveMapper vegReceiveMapper;
    @Mock
    private LocationStockMapper locationStockMapper;
    @Mock
    private LocationInfoMapper locationInfoMapper;
    @Mock
    private StockFlowMapper stockFlowMapper;
    @Mock
    private ProductInfoMapper productInfoMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private IBizCodeGenerator bizCodeGenerator;
    @Mock
    private ImageUrlResolver imageUrlResolver;
    @Mock
    private CropInfoMapper cropInfoMapper;
    @Mock
    private ILossFlowService lossFlowService;
    @Mock
    private org.dromara.djs.plant.crop.service.ICropProductService cropProductService;
    @Mock
    private org.dromara.djs.warehouse.cross.mapper.BarInfoMapper barInfoMapper;

    private VegReceiveServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeEach
    void setup() {
        service = new VegReceiveServiceImpl(
            vegReceiveMapper, locationStockMapper, locationInfoMapper, stockFlowMapper,
            productInfoMapper, supplierMapper, bizCodeGenerator, imageUrlResolver, cropInfoMapper, lossFlowService,
            cropProductService, barInfoMapper);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(9001L);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    /**
     * 共用：合法入库库位（L0003 蔬菜保鲜库，命中白名单）+ 充足剩余可入量 + 作物名 + 流水号。
     */
    private void stubCommonInbound() {
        LocationInfo loc = new LocationInfo();
        loc.setId(90001L);
        loc.setLocationCode("L0003");
        loc.setLocationName("蔬菜保鲜库");
        when(locationInfoMapper.selectById(90001L)).thenReturn(loc);
        // row55：额度按 (作物, 地块, 产品) 查；bo 不带 productId 时透传 null = 不按产品过滤
        when(vegReceiveMapper.selectRemainInboundWeight(eq(12001L), eq(11001L), any()))
            .thenReturn(new BigDecimal("100.000"));
        when(vegReceiveMapper.selectCropName(12001L)).thenReturn("卷心菜");
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap()))
            .thenReturn("F2606200IN0001");
    }

    /**
     * 共用：stub related_product=#{id} 指向一个 attr=2 果蔬原料产品（通过 resolveProductIdByCrop 的守门）。
     */
    private void stubRawMaterialProduct(Long productId) {
        ProductInfo p = new ProductInfo();
        p.setId(productId);
        p.setProductAttr(2);
        p.setBelongType("vegetable");
        when(productInfoMapper.selectById(productId)).thenReturn(p);
    }

    private VegInboundBo sampleBo() {
        VegInboundBo bo = new VegInboundBo();
        bo.setCropId(12001L);
        bo.setPlotId(11001L);
        bo.setWeight(new BigDecimal("30.000"));
        bo.setLocationId(90001L);
        bo.setIsFinish(2);
        return bo;
    }

    @Test
    @DisplayName("月台中转入库·无库存行：related_product 命中 → 兜底篮 product_id+plot_id 双键")
    void testInbound_NoStockRow_RelatedProductHit_DoubleKeyBasket() {
        stubCommonInbound();
        // 无库存行：plot 维度增量返 0 → 走兜底 INSERT
        when(vegReceiveMapper.addStockByPlotLocation(eq(90001L), eq(11001L), any(), any(), eq(9001L)))
            .thenReturn(0);
        // 作物配置了 related_product=88001（→ t_warehouse_product_info.id，果蔬原料）
        CropInfo crop = new CropInfo();
        crop.setId(12001L);
        crop.setRelatedProduct(88001L);
        when(cropInfoMapper.selectById(12001L)).thenReturn(crop);
        stubRawMaterialProduct(88001L);

        service.inbound(sampleBo());

        // 兜底 INSERT 的 location_stock 行：product_id=88001 + plot_id=11001 双键篮
        ArgumentCaptor<LocationStock> stockCap = ArgumentCaptor.forClass(LocationStock.class);
        verify(locationStockMapper, times(1)).insert(stockCap.capture());
        LocationStock basket = stockCap.getValue();
        assertThat(basket.getProductId()).isEqualTo(88001L);
        assertThat(basket.getPlotId()).isEqualTo(11001L);
        assertThat(basket.getLocationId()).isEqualTo(90001L);
        assertThat(basket.getProductStock()).isEqualByComparingTo("30.000");
        assertThat(basket.getProductName()).isEqualTo("卷心菜");

        // 收货记录 + 流水仍正常落库
        verify(vegReceiveMapper, times(1)).insert(any(VegReceive.class));
        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        assertThat(flowCap.getValue().getFlowType()).isEqualTo("veg_receive_in");
        assertThat(flowCap.getValue().getInoutType()).isEqualTo("IN");
        assertThat(flowCap.getValue().getPlotId()).isEqualTo(11001L);
    }

    @Test
    @DisplayName("月台中转入库·无库存行：related_product 为空 → 兜底篮退化为 plot 单键（不抛、入库照常）")
    void testInbound_NoStockRow_RelatedProductNull_DegradesToPlotOnly() {
        stubCommonInbound();
        when(vegReceiveMapper.addStockByPlotLocation(eq(90001L), eq(11001L), any(), any(), eq(9001L)))
            .thenReturn(0);
        // 作物未配 related_product（现网多为 NULL）
        CropInfo crop = new CropInfo();
        crop.setId(12001L);
        crop.setRelatedProduct(null);
        when(cropInfoMapper.selectById(12001L)).thenReturn(crop);

        // 不抛：降级保持现状（product_id 退化为 null、仅 plot 单键篮）
        service.inbound(sampleBo());

        ArgumentCaptor<LocationStock> stockCap = ArgumentCaptor.forClass(LocationStock.class);
        verify(locationStockMapper, times(1)).insert(stockCap.capture());
        LocationStock basket = stockCap.getValue();
        assertThat(basket.getProductId()).isNull();
        assertThat(basket.getPlotId()).isEqualTo(11001L);
        assertThat(basket.getProductStock()).isEqualByComparingTo("30.000");

        // 收货记录仍正常落库
        verify(vegReceiveMapper, times(1)).insert(any(VegReceive.class));
    }

    @Test
    @DisplayName("月台中转入库·已有库存行（addStockByPlotLocation 返 1）→ 不走兜底 INSERT；流水仍带果蔬原料 product_id（#3 修复）")
    void testInbound_StockRowExists_NoFallbackInsert() {
        stubCommonInbound();
        // 已有库存行：plot 维度增量返 1 → 不走兜底 INSERT
        when(vegReceiveMapper.addStockByPlotLocation(eq(90001L), eq(11001L), any(), any(), eq(9001L)))
            .thenReturn(1);
        // 作物配了 related_product → 流水 product_id 解析为果蔬原料（让 admin 入库记录「产品」列有值）
        CropInfo crop = new CropInfo();
        crop.setId(12001L);
        crop.setRelatedProduct(88001L);
        when(cropInfoMapper.selectById(12001L)).thenReturn(crop);
        stubRawMaterialProduct(88001L);

        service.inbound(sampleBo());

        // 不 INSERT 新库存行（增量 UPDATE 已生效）
        verify(locationStockMapper, Mockito.never()).insert(any(LocationStock.class));
        // 收货记录 + 流水仍正常落库；流水带解析出的果蔬原料 product_id（#3：admin 入库记录「产品」列）
        verify(vegReceiveMapper, times(1)).insert(any(VegReceive.class));
        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        assertThat(flowCap.getValue().getProductId()).isEqualTo(88001L);
        assertThat(flowCap.getValue().getPlotId()).isEqualTo(11001L);
    }

    @Test
    @DisplayName("resolveProductIdByCrop：cropId 为空 → 返 null，不查作物")
    void testResolveProductIdByCrop_NullCropId() {
        assertThat(service.resolveProductIdByCrop(null)).isNull();
        verify(cropInfoMapper, Mockito.never()).selectById(anyLong());
    }

    @Test
    @DisplayName("row55·收的产品不是果蔬原材料(attr=1 成品) → 直接拒绝，一行都不落库")
    void testInbound_ProductNotVegMaterial_Rejected() {
        stubCommonInbound();
        // 作物只配了一个产品 88001，但它是成品(attr=1) —— 配置脏了
        org.dromara.djs.plant.crop.domain.vo.CropProductVo only =
            new org.dromara.djs.plant.crop.domain.vo.CropProductVo();
        only.setProductId(88001L);
        when(cropProductService.listByCrop(12001L)).thenReturn(java.util.List.of(only));
        ProductInfo finished = new ProductInfo();
        finished.setId(88001L);
        finished.setProductAttr(1);
        finished.setBelongType("vegetable");
        when(productInfoMapper.selectById(88001L)).thenReturn(finished);

        // 曾经的做法是「退化成 product_id=NULL 的无名篮」，实测更坏：两个都过不了守门的产品会因
        // `product_id <=> NULL` 并进同一张篮，而下游领用 eq(product_id, ?) 永远匹配不到 NULL，货领不出去。
        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> service.inbound(sampleBo()))
            .isInstanceOf(org.dromara.common.core.exception.ServiceException.class)
            .hasMessageContaining("不是果蔬原材料");

        verify(vegReceiveMapper, Mockito.never()).insert(any(VegReceive.class));
        verify(stockFlowMapper, Mockito.never()).insert(any(StockFlow.class));
        verify(locationStockMapper, Mockito.never()).insert(any(LocationStock.class));
    }

    // ───────────────── row55：按产品收窄（QA 指出这三点删掉也不会有测试变红）─────────────────

    @Test
    @DisplayName("row55·收货记录必须落 product_id —— 否则下次算已入库量时认不出这笔收的是哪个产品")
    void testInbound_PersistsSelectedProductIdOnReceive() {
        stubCommonInbound();
        when(vegReceiveMapper.addStockByPlotLocation(eq(90001L), eq(11001L), any(), any(), eq(9001L)))
            .thenReturn(1);
        stubRawMaterialProduct(77001L);   // 红薯杆这类「非作物默认」的产品
        VegInboundBo bo = sampleBo();
        bo.setProductId(77001L);

        service.inbound(bo);

        ArgumentCaptor<VegReceive> cap = ArgumentCaptor.forClass(VegReceive.class);
        verify(vegReceiveMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getProductId()).isEqualTo(77001L);
    }

    @Test
    @DisplayName("row55·额度按 (作物,地块,产品) 查 —— 同一块地的红薯不能吃掉红薯杆的额度")
    void testInbound_QuotaScopedToSelectedProduct() {
        stubCommonInbound();
        when(vegReceiveMapper.addStockByPlotLocation(eq(90001L), eq(11001L), any(), any(), eq(9001L)))
            .thenReturn(1);
        stubRawMaterialProduct(77001L);
        VegInboundBo bo = sampleBo();
        bo.setProductId(77001L);

        service.inbound(bo);

        verify(vegReceiveMapper, times(1))
            .selectRemainInboundWeight(eq(12001L), eq(11001L), eq(77001L));
    }

    @Test
    @DisplayName("row55·库存篮增量必须带 product_id —— 否则第二个产品会加到第一个产品的篮子上")
    void testInbound_StockIncrementScopedToProduct() {
        stubCommonInbound();
        when(vegReceiveMapper.addStockByPlotLocation(eq(90001L), eq(11001L), any(), any(), eq(9001L)))
            .thenReturn(1);
        stubRawMaterialProduct(77001L);
        ProductInfo p = new ProductInfo();
        p.setId(77001L);
        p.setProductAttr(2);
        p.setBelongType("vegetable");
        p.setProductName("红薯杆");
        when(productInfoMapper.selectById(77001L)).thenReturn(p);
        VegInboundBo bo = sampleBo();
        bo.setProductId(77001L);

        service.inbound(bo);

        verify(vegReceiveMapper, times(1))
            .addStockByPlotLocation(eq(90001L), eq(11001L), eq(77001L), any(), eq(9001L));
    }

    @Test
    @DisplayName("row55·新建篮子的名字跟产品走 —— 同地块两个篮子不能都叫作物名")
    void testInbound_NewBasketNamedAfterProduct() {
        stubCommonInbound();
        when(vegReceiveMapper.addStockByPlotLocation(eq(90001L), eq(11001L), any(), any(), eq(9001L)))
            .thenReturn(0);   // 无篮 → 兜底 INSERT
        ProductInfo p = new ProductInfo();
        p.setId(77001L);
        p.setProductAttr(2);
        p.setBelongType("vegetable");
        p.setProductName("红薯杆");
        when(productInfoMapper.selectById(77001L)).thenReturn(p);
        VegInboundBo bo = sampleBo();
        bo.setProductId(77001L);

        service.inbound(bo);

        ArgumentCaptor<LocationStock> cap = ArgumentCaptor.forClass(LocationStock.class);
        verify(locationStockMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getProductId()).isEqualTo(77001L);
        assertThat(cap.getValue().getProductName()).isEqualTo("红薯杆");   // 不是「卷心菜」
    }

    @Test
    @DisplayName("row55·mp 没传 productId + 作物只有一个产品 → 自动补全该产品，额度按它收窄")
    void testInbound_NoProductId_SingleProductCrop_AutoResolves() {
        stubCommonInbound();
        when(vegReceiveMapper.addStockByPlotLocation(eq(90001L), eq(11001L), any(), any(), eq(9001L)))
            .thenReturn(1);
        org.dromara.djs.plant.crop.domain.vo.CropProductVo only =
            new org.dromara.djs.plant.crop.domain.vo.CropProductVo();
        only.setProductId(88001L);
        when(cropProductService.listByCrop(12001L)).thenReturn(java.util.List.of(only));
        stubRawMaterialProduct(88001L);

        service.inbound(sampleBo());   // bo.productId 为 null

        verify(vegReceiveMapper, times(1))
            .selectRemainInboundWeight(eq(12001L), eq(11001L), eq(88001L));
        ArgumentCaptor<VegReceive> cap = ArgumentCaptor.forClass(VegReceive.class);
        verify(vegReceiveMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getProductId()).isEqualTo(88001L);
    }

    @Test
    @DisplayName("row55·mp 没传 productId + 作物是多产品 → 拒绝（不许记一笔说不清是什么的货）")
    void testInbound_NoProductId_MultiProductCrop_Rejected() {
        stubCommonInbound();
        org.dromara.djs.plant.crop.domain.vo.CropProductVo a =
            new org.dromara.djs.plant.crop.domain.vo.CropProductVo();
        a.setProductId(88001L);
        org.dromara.djs.plant.crop.domain.vo.CropProductVo b =
            new org.dromara.djs.plant.crop.domain.vo.CropProductVo();
        b.setProductId(77001L);
        when(cropProductService.listByCrop(12001L)).thenReturn(java.util.List.of(a, b));

        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> service.inbound(sampleBo()))
            .isInstanceOf(org.dromara.common.core.exception.ServiceException.class)
            .hasMessageContaining("更新小程序");

        // 一行都不许落库：额度没查、收货没写、库存没动
        verify(vegReceiveMapper, Mockito.never()).selectRemainInboundWeight(anyLong(), anyLong(), any());
        verify(vegReceiveMapper, Mockito.never()).insert(any(VegReceive.class));
        verify(locationStockMapper, Mockito.never()).insert(any(LocationStock.class));
    }

    @Test
    @DisplayName("row55·详情页地块列表：productId 原样透传给 mapper（传了收窄 / 没传不过滤）")
    void testListInboundPlots_PassesProductIdThrough() {
        service.listInboundPlots(12001L, 77001L);
        verify(vegReceiveMapper, times(1)).selectInboundPlots(eq(12001L), eq(77001L));

        service.listInboundPlots(12001L, null);
        verify(vegReceiveMapper, times(1)).selectInboundPlots(eq(12001L), eq((Long) null));
    }

    // ==================== V6 row132：外购猪肉产品带耳号 ====================

    /** row132 共用：一个合法的外购产品 + 供应商 + 流水号，让 purchase() 能跑到库存/流水那两步。 */
    private org.dromara.djs.warehouse.vegreceive.domain.bo.VegPurchaseBo stubPurchase(String earNo) {
        org.dromara.djs.warehouse.product.domain.ProductInfo p =
            new org.dromara.djs.warehouse.product.domain.ProductInfo();
        p.setId(77001L);
        p.setProductName("猪心");
        p.setProductUnit("kg");
        // requirePurchaseProduct 的三条硬闸：自产品类(1) + 原材料(2) + 已开「支持外购」
        p.setProductType(1);
        p.setProductAttr(2);
        p.setIsBuyOut(1);
        when(productInfoMapper.selectOne(any())).thenReturn(p);
        when(supplierMapper.selectOne(any())).thenReturn(null);
        when(bizCodeGenerator.generate(any(), anyMap())).thenReturn("F20260824IN0001");

        org.dromara.djs.warehouse.vegreceive.domain.bo.VegPurchaseBo bo =
            new org.dromara.djs.warehouse.vegreceive.domain.bo.VegPurchaseBo();
        bo.setCropId(77001L);
        bo.setWeight(new BigDecimal("3.500"));
        bo.setSupplier("G0004");
        bo.setLocationId(66001L);
        bo.setPigEarNo(earNo);
        return bo;
    }

    @Test
    @DisplayName("row132·外购填了耳号：进耳号篮（不碰通用篮），流水带耳号")
    void testPurchase_WithEarNo_GoesToEarBasket() {
        var bo = stubPurchase("01-01-1-251016-001");
        when(locationStockMapper.addByProductLocationEarNo(
            anyLong(), anyLong(), any(), any(), anyLong())).thenReturn(1);

        service.purchase(bo);

        // 只累加耳号篮，通用篮一次都不许碰（碰了就把带追溯归属的货混进无归属篮）
        verify(locationStockMapper, times(1)).addByProductLocationEarNo(
            eq(66001L), eq(77001L), eq("01-01-1-251016-001"), eq(new BigDecimal("3.500")), anyLong());
        verify(locationStockMapper, Mockito.never()).addByProductLocation(any(), any(), any(), any());
        // 命中已有篮子 → 不建新行
        verify(locationStockMapper, Mockito.never()).insert(any(LocationStock.class));

        ArgumentCaptor<StockFlow> flow = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper).insert(flow.capture());
        assertThat(flow.getValue().getEarNo()).isEqualTo("01-01-1-251016-001");
    }

    @Test
    @DisplayName("row132·耳号篮还不存在：兜底 INSERT 的新行 ear_no 要落上")
    void testPurchase_WithEarNo_InsertsEarBasketWhenMissing() {
        var bo = stubPurchase("01-01-1-251016-001");
        when(locationStockMapper.addByProductLocationEarNo(
            anyLong(), anyLong(), any(), any(), anyLong())).thenReturn(0);

        service.purchase(bo);

        ArgumentCaptor<LocationStock> row = ArgumentCaptor.forClass(LocationStock.class);
        verify(locationStockMapper).insert(row.capture());
        assertThat(row.getValue().getEarNo()).isEqualTo("01-01-1-251016-001");
        assertThat(row.getValue().getProductId()).isEqualTo(77001L);
    }

    @Test
    @DisplayName("row132·没填耳号（含空串）：维持原口径进通用篮，流水 ear_no 为空")
    void testPurchase_WithoutEarNo_KeepsGeneralBasket() {
        var bo = stubPurchase("   ");   // mp 未选时发空串，trim 后按没填处理
        when(locationStockMapper.addByProductLocation(anyLong(), anyLong(), any(), anyLong())).thenReturn(1);

        service.purchase(bo);

        verify(locationStockMapper, times(1)).addByProductLocation(
            eq(66001L), eq(77001L), eq(new BigDecimal("3.500")), anyLong());
        verify(locationStockMapper, Mockito.never()).addByProductLocationEarNo(
            any(), any(), any(), any(), any());

        ArgumentCaptor<StockFlow> flow = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper).insert(flow.capture());
        assertThat(flow.getValue().getEarNo()).isNull();
    }

    @Test
    @DisplayName("row132·耳号候选：原样透传 mapper 的今日白条出库耳号")
    void testListTodayOutBarEarNos_Delegates() {
        when(barInfoMapper.selectTodayOutEarNos())
            .thenReturn(java.util.List.of("01-01-1-251016-001", "01-01-2-251001-005"));

        assertThat(service.listTodayOutBarEarNos())
            .containsExactly("01-01-1-251016-001", "01-01-2-251001-005");
    }
}
