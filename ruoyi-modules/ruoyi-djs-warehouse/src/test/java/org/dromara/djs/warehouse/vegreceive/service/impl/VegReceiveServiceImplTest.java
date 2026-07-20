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

    private VegReceiveServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeEach
    void setup() {
        service = new VegReceiveServiceImpl(
            vegReceiveMapper, locationStockMapper, locationInfoMapper, stockFlowMapper,
            productInfoMapper, supplierMapper, bizCodeGenerator, imageUrlResolver, cropInfoMapper, lossFlowService);
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
        when(vegReceiveMapper.selectRemainInboundWeight(eq(12001L), eq(11001L)))
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
        when(vegReceiveMapper.addStockByPlotLocation(eq(90001L), eq(11001L), any(), eq(9001L)))
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
        when(vegReceiveMapper.addStockByPlotLocation(eq(90001L), eq(11001L), any(), eq(9001L)))
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
        when(vegReceiveMapper.addStockByPlotLocation(eq(90001L), eq(11001L), any(), eq(9001L)))
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
    @DisplayName("月台中转入库·related_product 误配成品(attr=1) → 守门返 null，流水 product_id 兜底空（脏值防御）")
    void testInbound_RelatedProductNotRawMaterial_DegradesToNull() {
        stubCommonInbound();
        when(vegReceiveMapper.addStockByPlotLocation(eq(90001L), eq(11001L), any(), eq(9001L)))
            .thenReturn(1);
        // 作物 related_product=88001 指向一个成品(attr=1) —— 误配 / 脏值，应被守门拦下
        CropInfo crop = new CropInfo();
        crop.setId(12001L);
        crop.setRelatedProduct(88001L);
        when(cropInfoMapper.selectById(12001L)).thenReturn(crop);
        ProductInfo finished = new ProductInfo();
        finished.setId(88001L);
        finished.setProductAttr(1);
        finished.setBelongType("vegetable");
        when(productInfoMapper.selectById(88001L)).thenReturn(finished);

        service.inbound(sampleBo());

        // 流水照常落库，但 product_id 兜底 null（不把成品 id 漏到 veg_receive_in 流水）
        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        assertThat(flowCap.getValue().getProductId()).isNull();
        assertThat(flowCap.getValue().getPlotId()).isEqualTo(11001L);
    }
}
