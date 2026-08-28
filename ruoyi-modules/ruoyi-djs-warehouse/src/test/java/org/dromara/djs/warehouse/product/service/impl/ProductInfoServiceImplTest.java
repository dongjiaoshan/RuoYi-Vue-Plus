package org.dromara.djs.warehouse.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.bo.ProductInfoBo;
import org.dromara.djs.warehouse.product.domain.query.ProductInfoQuery;
import org.dromara.djs.warehouse.product.domain.vo.ProductInfoVo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProductInfoServiceImpl} 单元测试（WMS-MD-002）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>新增自产 happy / 缺归属类型 → error</li>
 *   <li>新增外购 happy / 缺供应商 → error</li>
 *   <li>新增礼盒 happy（独立成品，自动 set belong_type=gift_box）</li>
 *   <li>重复编码 DuplicateKeyException → product.code_duplicate</li>
 *   <li>删除：有库存 → error / 被原材料引用 → error / happy</li>
 *   <li>分页 happy</li>
 * </ul>
 *
 * @author djs
 * @since WMS-MD-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductInfoServiceImpl 单元测试")
class ProductInfoServiceImplTest {

    @Mock
    private ProductInfoMapper productInfoMapper;

    @Mock
    private org.dromara.djs.common.image.service.ImageUrlResolver imageUrlResolver;

    @Mock
    private org.dromara.djs.warehouse.location.mapper.LocationInfoMapper locationInfoMapper;

    @Mock
    private org.dromara.djs.warehouse.flow.mapper.StockFlowMapper stockFlowMapper;

    @Mock
    private org.dromara.djs.warehouse.stock.mapper.LocationStockMapper locationStockMapper;

    @Mock
    private org.dromara.djs.common.encoder.IBizCodeGenerator bizCodeGenerator;

    @Mock
    private org.dromara.djs.warehouse.check.service.IStockCheckService stockCheckService;

    @Mock
    private org.dromara.djs.common.supplier.mapper.SupplierMapper supplierMapper;

    @Mock
    private org.dromara.djs.warehouse.product.service.IProductDisplayNameResolver displayNameResolver;

    private TestableProductInfoServiceImpl service;

    /**
     * 子类覆盖 toEntity 钩子，避开 SpringUtils.getBean(Converter.class)（MapStruct-Plus 容器）。
     */
    static class TestableProductInfoServiceImpl extends ProductInfoServiceImpl {
        TestableProductInfoServiceImpl(ProductInfoMapper baseMapper,
                                       org.dromara.djs.common.image.service.ImageUrlResolver imageUrlResolver,
                                       org.dromara.djs.warehouse.location.mapper.LocationInfoMapper locationInfoMapper,
                                       org.dromara.djs.warehouse.flow.mapper.StockFlowMapper stockFlowMapper,
                                       org.dromara.djs.warehouse.stock.mapper.LocationStockMapper locationStockMapper,
                                       org.dromara.djs.common.encoder.IBizCodeGenerator bizCodeGenerator,
                                       org.dromara.djs.warehouse.check.service.IStockCheckService stockCheckService,
                                       org.dromara.djs.common.supplier.mapper.SupplierMapper supplierMapper,
                                       org.dromara.djs.warehouse.product.service.IProductDisplayNameResolver displayNameResolver) {
            super(baseMapper, imageUrlResolver,
                locationInfoMapper, stockFlowMapper, locationStockMapper, bizCodeGenerator, stockCheckService, supplierMapper, displayNameResolver);
        }

        @Override
        protected ProductInfo toEntity(ProductInfoBo bo) {
            if (bo == null) {
                return null;
            }
            ProductInfo e = new ProductInfo();
            e.setId(bo.getId());
            e.setProductId(bo.getProductId());
            e.setProductName(bo.getProductName());
            e.setProductType(bo.getProductType());
            e.setProductUnit(bo.getProductUnit());
            e.setProductSpec(bo.getProductSpec());
            e.setBelongType(bo.getBelongType());
            e.setBuyClass(bo.getBuyClass());
            e.setProductThumb(bo.getProductThumb());
            e.setProductImg(bo.getProductImg());
            e.setProductAttr(bo.getProductAttr());
            e.setProductWorkshop(bo.getProductWorkshop());
            e.setStoreLocationId(bo.getStoreLocationId());
            e.setProductStatus(bo.getProductStatus());
            e.setProductMaterial(bo.getProductMaterial());
            e.setProductDesc(bo.getProductDesc());
            e.setMaterialNum(bo.getMaterialNum());
            e.setIsDelivery(bo.getIsDelivery());
            e.setSupplierId(bo.getSupplierId());
            e.setIsBuyOut(bo.getIsBuyOut());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableProductInfoServiceImpl(productInfoMapper, imageUrlResolver,
            locationInfoMapper, stockFlowMapper, locationStockMapper, bizCodeGenerator, stockCheckService, supplierMapper, displayNameResolver);
    }

    private ProductInfoBo selfBo() {
        ProductInfoBo bo = new ProductInfoBo();
        bo.setProductId("P0001");
        bo.setProductName("五花肉");
        bo.setProductType(1);
        bo.setProductUnit("kg");
        bo.setBelongType("pork");
        return bo;
    }

    private ProductInfoBo purchaseBo() {
        ProductInfoBo bo = new ProductInfoBo();
        bo.setProductId("P0002");
        bo.setProductName("包材");
        bo.setProductType(2);
        bo.setProductUnit("个");
        bo.setSupplierId(123L);
        return bo;
    }

    private ProductInfoBo giftBoxBo() {
        // 礼盒 = 自产（productType=1）+ 产品类别 belongType=gift_box（djs_product_type 已废弃 3）
        ProductInfoBo bo = new ProductInfoBo();
        bo.setProductId("GIFT001");
        bo.setProductName("中秋礼盒");
        bo.setProductType(1);
        bo.setBelongType("gift_box");
        bo.setProductUnit("盒");
        return bo;
    }

    // ---------- 新增 happy paths ----------

    @Test
    @DisplayName("新增自产 happy → productType=1 + belongType=pork → insert 1 次，不走 gift_box")
    void testInsertSelfProduce_HappyPath() {
        when(productInfoMapper.insert(any(ProductInfo.class))).thenAnswer(inv -> {
            ProductInfo e = inv.getArgument(0);
            e.setId(20001L);
            return 1;
        });

        int rows = service.insertByBo(selfBo());

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<ProductInfo> captor = ArgumentCaptor.forClass(ProductInfo.class);
        verify(productInfoMapper, times(1)).insert(captor.capture());
        ProductInfo saved = captor.getValue();
        assertThat(saved.getProductType()).isEqualTo(1);
        assertThat(saved.getBelongType()).isEqualTo("pork");
        assertThat(saved.getProductStatus()).as("默认 productStatus=0").isEqualTo(0);
        assertThat(saved.getIsDelivery()).as("默认 isDelivery=1").isEqualTo(1);
    }

    @Test
    @DisplayName("新增：生产车间多归属 '3,5' 原样入库（WMS-PRODUCT-WORKSHOP-MULTI-001 一品多车间）")
    void testInsertWorkshop_MultiValueKept() {
        when(productInfoMapper.insert(any(ProductInfo.class))).thenReturn(1);
        ProductInfoBo bo = selfBo();
        bo.setProductWorkshop("3,5");

        service.insertByBo(bo);

        ArgumentCaptor<ProductInfo> captor = ArgumentCaptor.forClass(ProductInfo.class);
        verify(productInfoMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getProductWorkshop())
            .as("多车间归属应原样保留，供 FIND_IN_SET 匹配")
            .isEqualTo("3,5");
    }

    @Test
    @DisplayName("新增：脏 CSV '3, 5,,3' 归一化成 '3,5'——带空格的段会让 FIND_IN_SET 静默不命中")
    void testInsertWorkshop_NormalizesDirtyCsv() {
        when(productInfoMapper.insert(any(ProductInfo.class))).thenReturn(1);
        ProductInfoBo bo = selfBo();
        bo.setProductWorkshop("3, 5,,3");

        service.insertByBo(bo);

        ArgumentCaptor<ProductInfo> captor = ArgumentCaptor.forClass(ProductInfo.class);
        verify(productInfoMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getProductWorkshop()).isEqualTo("3,5");
    }

    @Test
    @DisplayName("新增：空串车间归一化成 null（列语义：空归属存 NULL 不存空串）")
    void testInsertWorkshop_BlankToNull() {
        when(productInfoMapper.insert(any(ProductInfo.class))).thenReturn(1);
        ProductInfoBo bo = selfBo();
        bo.setProductWorkshop("");

        service.insertByBo(bo);

        ArgumentCaptor<ProductInfo> captor = ArgumentCaptor.forClass(ProductInfo.class);
        verify(productInfoMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getProductWorkshop()).isNull();
    }

    @Test
    @DisplayName("新增外购 happy → productType=2 + supplierId → insert 1 次")
    void testInsertPurchase_HappyPath() {
        when(productInfoMapper.insert(any(ProductInfo.class))).thenReturn(1);

        int rows = service.insertByBo(purchaseBo());

        assertThat(rows).isEqualTo(1);
        verify(productInfoMapper).insert(any(ProductInfo.class));
    }

    @Test
    @DisplayName("新增礼盒 happy → 自产 + belong_type=gift_box（用户选产品类别），insert 1 次")
    void testInsertGiftBox_HappyPath() {
        when(productInfoMapper.insert(any(ProductInfo.class))).thenAnswer(inv -> {
            ProductInfo e = inv.getArgument(0);
            e.setId(30001L);
            return 1;
        });

        int rows = service.insertByBo(giftBoxBo());

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<ProductInfo> captor = ArgumentCaptor.forClass(ProductInfo.class);
        verify(productInfoMapper).insert(captor.capture());
        ProductInfo saved = captor.getValue();
        assertThat(saved.getProductType()).as("礼盒走自产 productType=1").isEqualTo(1);
        assertThat(saved.getBelongType()).as("礼盒 belong_type=gift_box").isEqualTo("gift_box");
    }

    // ---------- 校验失败 ----------

    @Test
    @DisplayName("新增自产缺归属 → 抛 product.belong_type.required，不打 DB")
    void testInsertSelfProduce_MissingBelongType() {
        ProductInfoBo bo = selfBo();
        bo.setBelongType(null);

        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class);
        verify(productInfoMapper, never()).insert(any(ProductInfo.class));
    }

    @Test
    @DisplayName("新增生产产品(attr=1) 缺规格 → 抛 product.spec.required，不打 DB（doc/14 §4）")
    void testInsertProduction_MissingSpec() {
        ProductInfoBo bo = selfBo();
        bo.setProductAttr(1);   // 生产产品
        bo.setProductSpec(null);

        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class);
        verify(productInfoMapper, never()).insert(any(ProductInfo.class));
    }

    @Test
    @DisplayName("新增原材料(attr=2) 不强制规格 → 通过校验（spec 可空）")
    void testInsertRawMaterial_SpecOptional() {
        ProductInfoBo bo = selfBo();
        bo.setProductAttr(2);   // 原材料
        bo.setProductSpec(null);
        when(productInfoMapper.insert(any(ProductInfo.class))).thenReturn(1);

        service.insertByBo(bo);

        verify(productInfoMapper, times(1)).insert(any(ProductInfo.class));
    }

    @Test
    @DisplayName("新增外购缺 supplierId → 抛 product.supplier.required")
    void testInsertPurchase_MissingSupplier() {
        ProductInfoBo bo = purchaseBo();
        bo.setSupplierId(null);

        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class);
        verify(productInfoMapper, never()).insert(any(ProductInfo.class));
    }

    @Test
    @DisplayName("重复编码 DuplicateKeyException → 转译 product.code_duplicate")
    void testInsert_DuplicateCode() {
        when(productInfoMapper.insert(any(ProductInfo.class)))
            .thenThrow(new DuplicateKeyException("uk_product_id"));

        assertThatThrownBy(() -> service.insertByBo(selfBo()))
            .isInstanceOf(ServiceException.class);
    }

    // ---------- 删除校验 ----------

    @Test
    @DisplayName("删除：有库存 → 抛 product.has_stock，不软删")
    void testDelete_HasStock() {
        when(productInfoMapper.countActiveStockByProduct(20001L)).thenReturn(5L);
        ProductInfo p = new ProductInfo();
        p.setId(20001L);
        p.setProductName("五花肉");
        when(productInfoMapper.selectById(20001L)).thenReturn(p);

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(20001L)))
            .isInstanceOf(ServiceException.class);
        verify(productInfoMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("删除：被原材料引用 → 抛 product.referenced_as_material")
    void testDelete_ReferencedAsMaterial() {
        when(productInfoMapper.countActiveStockByProduct(20001L)).thenReturn(0L);
        when(productInfoMapper.countReferencedAsMaterial(20001L)).thenReturn(2L);
        ProductInfo p = new ProductInfo();
        p.setId(20001L);
        p.setProductName("白条肉");
        when(productInfoMapper.selectById(20001L)).thenReturn(p);

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(20001L)))
            .isInstanceOf(ServiceException.class);
        verify(productInfoMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("删除 happy → 校验通过 + 软删")
    void testDelete_Happy() {
        when(productInfoMapper.countActiveStockByProduct(30001L)).thenReturn(0L);
        when(productInfoMapper.countReferencedAsMaterial(30001L)).thenReturn(0L);
        when(productInfoMapper.update(any(), any())).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(30001L));

        assertThat(rows).isEqualTo(1);
    }

    // ---------- 查询 ----------

    @Test
    @DisplayName("分页查询 happy → mapper.selectVoPage 返 TableDataInfo")
    void testQueryPageList() {
        ProductInfoQuery query = new ProductInfoQuery();
        query.setProductType(1);
        PageQuery pageQuery = new PageQuery(1, 10);

        ProductInfoVo vo = new ProductInfoVo();
        vo.setId(20001L);
        vo.setProductId("P0001");
        Page<ProductInfoVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(productInfoMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        TableDataInfo<ProductInfoVo> result = service.queryPageList(query, pageQuery);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getProductId()).isEqualTo("P0001");
    }

    @Test
    @DisplayName("产品入库 happy → 写入库流水（IN/purchase_in）+ addByProductLocation 命中既有库存行")
    void testInbound_Happy() {
        ProductInfo product = new ProductInfo();
        product.setId(20001L);
        product.setProductName("支原体保健药");
        product.setProductUnit("盒");
        when(productInfoMapper.selectById(eq(20001L))).thenReturn(product);
        when(bizCodeGenerator.generate(any(), any())).thenReturn("F20260612IN0001");
        // 既有库存行命中 → 返 1，不走兜底 INSERT
        when(locationStockMapper.addByProductLocation(eq(30001L), eq(20001L), any(BigDecimal.class), any()))
            .thenReturn(1);

        org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo bo =
            new org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo();
        bo.setProductId(20001L);
        bo.setLocationId(30001L);
        bo.setQuantity(new BigDecimal("22"));

        service.inbound(bo);

        ArgumentCaptor<org.dromara.djs.warehouse.flow.domain.StockFlow> flowCaptor =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.flow.domain.StockFlow.class);
        verify(stockFlowMapper).insert(flowCaptor.capture());
        org.dromara.djs.warehouse.flow.domain.StockFlow flow = flowCaptor.getValue();
        assertThat(flow.getInoutType()).isEqualTo("IN");
        assertThat(flow.getFlowType()).isEqualTo("purchase_in");
        assertThat(flow.getProductId()).isEqualTo(20001L);
        assertThat(flow.getWarehouseId()).isEqualTo(30001L);
        assertThat(flow.getChangeQuantity()).isEqualByComparingTo("22");
        // 命中既有库存行（addByProductLocation 返 1）→ 不兜底 INSERT；盘点锁已校验
        verify(locationStockMapper).addByProductLocation(eq(30001L), eq(20001L), any(BigDecimal.class), any());
        verify(stockCheckService).assertLocationUnlocked(eq(30001L));
    }

    @Test
    @DisplayName("采购入库 autoConfig=true + 商品未配库位 → 回写 store_location_id=入库库位")
    void testInbound_AutoConfigLocation_WritesBack() {
        ProductInfo product = new ProductInfo();
        product.setId(20002L);
        product.setProductName("外购包材");
        product.setProductUnit("个");
        product.setStoreLocationId(null);   // 未配存储库位
        when(productInfoMapper.selectById(eq(20002L))).thenReturn(product);
        when(bizCodeGenerator.generate(any(), any())).thenReturn("F20260622IN0001");
        when(locationStockMapper.addByProductLocation(eq(40001L), eq(20002L), any(BigDecimal.class), any()))
            .thenReturn(1);

        org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo bo =
            new org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo();
        bo.setProductId(20002L);
        bo.setLocationId(40001L);
        bo.setQuantity(new BigDecimal("10"));
        bo.setAutoConfigLocation(true);

        service.inbound(bo);

        ArgumentCaptor<ProductInfo> captor = ArgumentCaptor.forClass(ProductInfo.class);
        verify(productInfoMapper).updateById(captor.capture());
        ProductInfo updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(20002L);
        assertThat(updated.getStoreLocationId()).as("回写本次入库库位").isEqualTo("40001");
    }

    @Test
    @DisplayName("采购入库 autoConfig=null（商品配置入库）→ 不回写，行为零变化")
    void testInbound_AutoConfigNull_NoWriteBack() {
        ProductInfo product = new ProductInfo();
        product.setId(20003L);
        product.setProductName("外购包材");
        product.setProductUnit("个");
        product.setStoreLocationId(null);
        when(productInfoMapper.selectById(eq(20003L))).thenReturn(product);
        when(bizCodeGenerator.generate(any(), any())).thenReturn("F20260622IN0002");
        when(locationStockMapper.addByProductLocation(eq(40001L), eq(20003L), any(BigDecimal.class), any()))
            .thenReturn(1);

        org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo bo =
            new org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo();
        bo.setProductId(20003L);
        bo.setLocationId(40001L);
        bo.setQuantity(new BigDecimal("10"));
        // autoConfigLocation 不设 → null

        service.inbound(bo);

        verify(productInfoMapper, never()).updateById(any(ProductInfo.class));
    }

    @Test
    @DisplayName("采购入库 autoConfig=true 但商品已配库位 → 不覆盖，不回写")
    void testInbound_AutoConfigTrue_AlreadyConfigured_NoWriteBack() {
        ProductInfo product = new ProductInfo();
        product.setId(20004L);
        product.setProductName("外购包材");
        product.setProductUnit("个");
        product.setStoreLocationId("50001");   // 已配存储库位（命中校验放行）
        when(productInfoMapper.selectById(eq(20004L))).thenReturn(product);
        when(bizCodeGenerator.generate(any(), any())).thenReturn("F20260622IN0003");
        when(locationStockMapper.addByProductLocation(eq(50001L), eq(20004L), any(BigDecimal.class), any()))
            .thenReturn(1);

        org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo bo =
            new org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo();
        bo.setProductId(20004L);
        bo.setLocationId(50001L);
        bo.setQuantity(new BigDecimal("10"));
        bo.setAutoConfigLocation(true);

        service.inbound(bo);

        verify(productInfoMapper, never()).updateById(any(ProductInfo.class));
    }

    @Test
    @DisplayName("产品入库：产品不存在 → ServiceException")
    void testInbound_ProductNotFound() {
        when(productInfoMapper.selectById(eq(99999L))).thenReturn(null);
        org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo bo =
            new org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo();
        bo.setProductId(99999L);
        bo.setLocationId(30001L);
        bo.setQuantity(new BigDecimal("5"));
        assertThatThrownBy(() -> service.inbound(bo)).isInstanceOf(ServiceException.class);
        verify(stockFlowMapper, never()).insert(ArgumentMatchers.<org.dromara.djs.warehouse.flow.domain.StockFlow>any());
    }


    // ---------------- V6-R141 采购入库：入库量小数位闸 + 供应商按次选择 ----------------

    /** 造一个可入库的商品（无配置库位、不锁库位），单位由入参决定。 */
    private ProductInfo r141Product(String unit, Integer productType, Long configuredSupplier) {
        ProductInfo product = new ProductInfo();
        product.setId(20101L);
        product.setProductName("R141 测试商品");
        product.setProductUnit(unit);
        product.setProductType(productType);
        product.setSupplierId(configuredSupplier);
        when(productInfoMapper.selectById(eq(20101L))).thenReturn(product);
        when(bizCodeGenerator.generate(any(), any())).thenReturn("F20260828IN0001");
        when(locationStockMapper.addByProductLocation(any(), any(), any(BigDecimal.class), any())).thenReturn(1);
        return product;
    }

    private org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo r141Bo(String qty, Long supplierId) {
        org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo bo =
            new org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo();
        bo.setProductId(20101L);
        bo.setLocationId(30001L);
        bo.setQuantity(new BigDecimal(qty));
        bo.setSupplierId(supplierId);
        return bo;
    }

    @Test
    @DisplayName("R141 小数位闸：计数类单位（瓶）填小数 → 拒；填整数 / 3.000（scale 归一后为 0）→ 放行")
    void testInboundScale_CountingUnitIntegerOnly() {
        r141Product("瓶", 2, null);
        assertThatThrownBy(() -> service.inbound(r141Bo("1.6", null))).isInstanceOf(ServiceException.class);
        verify(stockFlowMapper, never()).insert(any(org.dromara.djs.warehouse.flow.domain.StockFlow.class));
        // 3.000 是 JSON 里常见写法，stripTrailingZeros 后 scale=0，不能误拒
        service.inbound(r141Bo("3.000", null));
        service.inbound(r141Bo("3", null));
        verify(stockFlowMapper, times(2)).insert(any(org.dromara.djs.warehouse.flow.domain.StockFlow.class));
    }

    @Test
    @DisplayName("R141 小数位闸：计量类单位最多三位小数 —— kg/公斤/KG 1.234 放行、1.2345 拒")
    void testInboundScale_MeasureUnitThreeDecimals() {
        r141Product("kg", 2, null);
        service.inbound(r141Bo("1.234", null));
        assertThatThrownBy(() -> service.inbound(r141Bo("1.2345", null))).isInstanceOf(ServiceException.class);

        r141Product("公斤", 2, null);
        service.inbound(r141Bo("2.500", null));
        r141Product("KG", 2, null);
        service.inbound(r141Bo("0.125", null));
        verify(stockFlowMapper, times(3)).insert(any(org.dromara.djs.warehouse.flow.domain.StockFlow.class));
    }

    @Test
    @DisplayName("R141 计量类单位不被误锁成整数（Kevin 2026-08-28 定：只有计数类才强制整数）")
    void testInboundScale_MeasureUnitsAllowDecimals() {
        // 甲方字面说的是「非 KG 就整数」，但 2.5 吨是合法业务值（本类 formatFlowQtyByUnit 注释亦承认），
        // 这几个单位库里真实在用：吨 36 / 升 4 / 斤 3 / 米 1 / 平方米 1 / 亩 1
        for (String unit : new String[]{"吨", "升", "斤", "米", "平方米", "亩"}) {
            r141Product(unit, 2, null);
            service.inbound(r141Bo("2.5", null));
        }
        verify(stockFlowMapper, times(6)).insert(any(org.dromara.djs.warehouse.flow.domain.StockFlow.class));
    }

    @Test
    @DisplayName("R141 未知 / 空单位按「可小数」处理 —— 拦错会挡住干活，放过只是多个小数位")
    void testInboundScale_UnknownUnitFallsBackToDecimals() {
        r141Product("盒 / 片", 2, null);   // 库里真实存在的一个畸形单位
        service.inbound(r141Bo("1.5", null));
        r141Product(null, 2, null);
        service.inbound(r141Bo("1.5", null));
        verify(stockFlowMapper, times(2)).insert(any(org.dromara.djs.warehouse.flow.domain.StockFlow.class));
    }

    @Test
    @DisplayName("R141 供应商：本次选了就落本次选的，且不回写商品配置")
    void testInboundSupplier_PickedWins() {
        r141Product("瓶", 2, 9001L);
        org.dromara.djs.common.supplier.domain.Supplier picked = new org.dromara.djs.common.supplier.domain.Supplier();
        picked.setId(9002L);
        when(supplierMapper.selectById(eq(9002L))).thenReturn(picked);

        service.inbound(r141Bo("2", 9002L));

        ArgumentCaptor<org.dromara.djs.warehouse.flow.domain.StockFlow> cap =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.flow.domain.StockFlow.class);
        verify(stockFlowMapper).insert(cap.capture());
        assertThat(cap.getValue().getSupplierId()).isEqualTo(9002L);
        // 商品主数据没被改写（本次选择只作用于这笔流水）
        verify(productInfoMapper, never()).updateById(any(ProductInfo.class));
    }

    @Test
    @DisplayName("R141 供应商：没选 → 回落商品配置快照（商品配置入口老行为不变）")
    void testInboundSupplier_FallsBackToConfigured() {
        r141Product("瓶", 2, 9001L);
        service.inbound(r141Bo("2", null));
        ArgumentCaptor<org.dromara.djs.warehouse.flow.domain.StockFlow> cap =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.flow.domain.StockFlow.class);
        verify(stockFlowMapper).insert(cap.capture());
        assertThat(cap.getValue().getSupplierId()).isEqualTo(9001L);
        verify(supplierMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("R141 供应商：选了个不存在 / 已软删的 → 拒，且不写流水")
    void testInboundSupplier_NotFoundRejected() {
        r141Product("瓶", 2, 9001L);
        when(supplierMapper.selectById(eq(8888L))).thenReturn(null);   // @TableLogic 软删也走这条
        assertThatThrownBy(() -> service.inbound(r141Bo("2", 8888L))).isInstanceOf(ServiceException.class);
        verify(stockFlowMapper, never()).insert(any(org.dromara.djs.warehouse.flow.domain.StockFlow.class));
    }

    @Test
    @DisplayName("R141 供应商：自产商品（productType=1）不落供应商，传了也忽略")
    void testInboundSupplier_SelfProducedIgnoresSupplier() {
        r141Product("瓶", 1, 9001L);
        service.inbound(r141Bo("2", 9002L));
        ArgumentCaptor<org.dromara.djs.warehouse.flow.domain.StockFlow> cap =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.flow.domain.StockFlow.class);
        verify(stockFlowMapper).insert(cap.capture());
        assertThat(cap.getValue().getSupplierId()).isNull();
    }
}
