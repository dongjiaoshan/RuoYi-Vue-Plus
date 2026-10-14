package org.dromara.djs.store.returns.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.store.returns.domain.StoreReturn;
import org.dromara.djs.store.returns.domain.bo.StoreReturnConfirmBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnUnitBo;
import org.dromara.djs.store.returns.domain.query.StoreReturnQuery;
import org.dromara.djs.store.returns.mapper.StoreReturnMapper;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.purchase.service.IWarehousePurchaseInService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** D-0152：其他分组门店退回按实际kg称重，单位新增与其他分组边界保持原规则。 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreReturnServiceImpl row305 其他产品实际称重")
class StoreReturnOtherWeightTest {

    private static final Long RETURN_ID = 63005L;
    private static final Long STORE_ID = 5001L;
    private static final Long PRODUCT_ID = 8305L;
    private static final Long MATERIAL_ID = 8306L;
    private static final Long LOCATION_ID = 3002L;

    @Mock private StoreReturnMapper baseMapper;
    @Mock private StoreMapper storeMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private LocationInfoMapper locationInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private IWarehousePurchaseInService purchaseInService;
    @Mock private DemandManageMapper demandManageMapper;
    @Mock private org.dromara.common.core.service.DictService dictService;
    @Mock private org.dromara.djs.warehouse.pack.service.IProductProductionService productProductionService;
    @Mock private org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper productProductionMapper;
    @Mock private org.dromara.djs.common.store.service.IStoreService storeService;
    @Mock private org.dromara.common.core.service.UserService userService;

    private StoreReturnServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;
    private StoreReturn existing;
    private ProductInfo product;
    private ProductInfo material;
    private final Map<Long, ProductInfo> products = new LinkedHashMap<>();

    @BeforeAll
    static void initEntityCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, StoreReturn.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, LocationInfo.class);
    }

    @BeforeEach
    void setup() {
        service = new StoreReturnServiceImpl(baseMapper, storeMapper, productInfoMapper,
            locationInfoMapper, bizCodeGenerator, purchaseInService, demandManageMapper, dictService,
            productProductionService, productProductionMapper, storeService, userService);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(9001L);

        existing = new StoreReturn();
        existing.setId(RETURN_ID);
        existing.setStoreId(STORE_ID);
        existing.setProductId(PRODUCT_ID);
        existing.setReturnType("store");
        existing.setReturnDirection("store_to_warehouse");
        existing.setReturnStatus("pending");
        existing.setReturnNo("RET2026101400305");
        existing.setReturnDate(LocalDateTime.of(2024, 10, 14, 16, 51));
        existing.setReturnQuantity(new BigDecimal("0.5"));
        existing.setGoodsWeight(BigDecimal.ZERO);

        product = product(PRODUCT_ID, "P0305", "鸡", "只", "other");
        product.setProductAttr(1);
        product.setProductMaterial(MATERIAL_ID);
        product.setMaterialNum(new BigDecimal("0.5"));
        material = product(MATERIAL_ID, "Y0305", "鸡原材料", "kg", "other");
        material.setProductAttr(2);
        material.setStoreLocationId(LOCATION_ID.toString());
        products.put(PRODUCT_ID, product);
        products.put(MATERIAL_ID, material);
        LocationInfo location = new LocationInfo();
        location.setId(LOCATION_ID);
        location.setLocationCode("L0002");
        location.setLocationName("产品预设库位");
        location.setLocationStatus(1);

        when(baseMapper.selectById(RETURN_ID)).thenReturn(existing);
        when(baseMapper.selectList(any())).thenReturn(List.of(existing));
        when(baseMapper.update(any(StoreReturn.class), any())).thenReturn(1);
        when(productInfoMapper.selectById(any())).thenAnswer(inv -> products.get(inv.getArgument(0)));
        when(productInfoMapper.selectList(any())).thenAnswer(inv -> selectProducts(inv.getArgument(0)));
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(location);
        when(locationInfoMapper.selectList(any())).thenReturn(List.of(location));
        when(dictService.getAllDictByDictType("djs_return_product_list"))
            .thenReturn(Map.of(product.getProductId(), product.getProductName()));
        when(purchaseInService.inboundReturnBasket(any(), any(), any(), anyString(), any()))
            .thenReturn(77777L);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    @ParameterizedTest
    @CsvSource(value = {"other,kg", "egg,KG", "dry_good,公斤", "NULL,kg", "new_category,kg"}, nullValues = "NULL")
    @DisplayName("其他是兜底分组：鸡蛋、干货、空分类和新增分类的KG都按实际重量直入原材料")
    void otherGroupsAcceptActualWeightWithoutReturnCap(String belongType, String unit) {
        product.setBelongType(belongType);
        material.setProductUnit(unit);
        // 原材料的分类不能把退回产品从other分组变成果蔬。
        material.setBelongType("vegetable");

        assertThat(service.confirm(confirmBo("2.345", "2.345"))).isEqualTo(1);

        verifyInbound("2.345");
        verifyNoInteractions(productProductionService, productProductionMapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.001"})
    @DisplayName("实际KG确认不要求固定计量比例，不再把输入重量乘比例")
    void actualWeightDoesNotRequireOrApplyMaterialRatio(String ratio) {
        product.setMaterialNum(decimal(ratio));

        assertThat(service.confirm(confirmBo("2.345", null))).isEqualTo(1);

        verifyInbound("2.345");
        ArgumentCaptor<StoreReturn> captor = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper).update(captor.capture(), any());
        assertThat(captor.getValue().getReceivedQty()).isEqualByComparingTo("2.345");
        assertThat(captor.getValue().getReceivedWeight()).isEqualByComparingTo("2.345");
    }

    @Test
    @DisplayName("没有正退回量和申报重量时仍可录其他产品实际称重，两个实收字段均不受旧cap约束")
    void missingBaseDoesNotBlockActualWeight() {
        existing.setReturnQuantity(null);
        existing.setGoodsWeight(BigDecimal.ZERO);

        assertThat(service.confirm(confirmBo("2.345", "9.876"))).isEqualTo(1);

        verifyInbound("2.345");
    }

    @Test
    @DisplayName("产品本身是KG原材料时同样不封顶，入产品自身库存")
    void directKgRawMaterialAcceptsActualWeight() {
        product.setProductAttr(2);
        product.setProductMaterial(null);
        product.setProductUnit("KG");
        product.setStoreLocationId(LOCATION_ID.toString());

        assertThat(service.confirm(confirmBo("2.345", "2.345"))).isEqualTo(1);

        verify(purchaseInService).inboundReturnBasket(eq(PRODUCT_ID), eq(LOCATION_ID),
            eq(new BigDecimal("2.345")), eq("store_return_in"), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pork", "white_bar", "vegetable"})
    @DisplayName("猪肉、白条、果蔬KG仍拦截超过120%的重量")
    void cappedGroupsStillRejectOvershoot(String belongType) {
        product.setBelongType(belongType);

        assertThatThrownBy(() -> service.confirm(confirmBo("0.301", "0.301")))
            .isInstanceOf(ServiceException.class).hasMessageContaining("120%");

        verifyNoWrite();
        var rows = service.listAppletItemsByStoreAndStatus(STORE_ID, "pending", null);
        assertThat(rows.get(0).getUnlimitedConfirmWeight()).isFalse();
        assertThat(rows.get(0).getMaxConfirmQty()).isEqualByComparingTo("0.300");
    }

    @Test
    @DisplayName("其他分组但原材料非KG时保留数量封顶，即使产品本身写KG")
    void nonKgMaterialKeepsQuantityCap() {
        product.setProductUnit("KG");
        material.setProductUnit("枚");
        product.setMaterialNum(new BigDecimal("30"));

        assertThatThrownBy(() -> service.confirm(confirmBo("15.001", "15.001")))
            .isInstanceOf(ServiceException.class).hasMessageContaining("可退上限");

        verifyNoWrite();
        var rows = service.listAppletItemsByStoreAndStatus(STORE_ID, "pending", null);
        assertThat(rows.get(0).getUnlimitedConfirmWeight()).isFalse();
        assertThat(rows.get(0).getMaxConfirmQty()).isEqualByComparingTo("15");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"store"})
    @DisplayName("小程序明确下发无上限标记和null cap，保留原默认值需要的退回量与比例")
    void appletFlagKeepsOriginalDefaultFields(String returnType) {
        existing.setReturnType(returnType);

        var rows = service.listAppletItemsByStoreAndStatus(STORE_ID, "pending", null);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getUnlimitedConfirmWeight()).isTrue();
        assertThat(rows.get(0).getMaxConfirmQty()).isNull();
        assertThat(rows.get(0).getReturnQuantity()).isEqualByComparingTo("0.5");
        assertThat(rows.get(0).getMaterialNum()).isEqualByComparingTo("0.5");
        assertThat(rows.get(0).getReturnWeight()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Admin实际称重不因无固定比例被锁行，canInbound继续独立判断")
    void operationsCanConfirmActualWeightWithoutRatio() {
        product.setMaterialNum(null);

        var rows = service.listOperationItems(new StoreReturnQuery());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getUnlimitedConfirmWeight()).isTrue();
        assertThat(rows.get(0).getCanConvert()).isTrue();
        assertThat(rows.get(0).getCanInbound()).isTrue();
        assertThat(rows.get(0).getMaterialNum()).isEqualByComparingTo("1");
        assertThat(rows.get(0).getDefaultLocationId()).isEqualTo(LOCATION_ID);
        // 确认专用放行不能改变单位新增共用的换算守卫。
        assertThat(StoreReturnServiceImpl.canConvert(product, "kg")).isFalse();
    }

    @Test
    @DisplayName("其他KG成品缺原材料仍只能丢弃，取消重量cap不能绕过canInbound")
    void finishedProductWithoutMaterialStillCannotInbound() {
        product.setProductMaterial(null);
        product.setProductUnit("kg");
        var rows = service.listOperationItems(new StoreReturnQuery());
        assertThat(rows.get(0).getUnlimitedConfirmWeight()).isTrue();
        assertThat(rows.get(0).getCanInbound()).isFalse();

        assertThatThrownBy(() -> service.confirm(confirmBo("2.345", "2.345")))
            .isInstanceOf(ServiceException.class).hasMessageContaining("未配置原材料");

        verifyNoWrite();
    }

    @Test
    @DisplayName("缺原材料的零量丢弃仍完成状态但不写库存")
    void zeroDiscardRemainsAvailable() {
        product.setProductMaterial(null);
        product.setProductUnit("kg");
        StoreReturnConfirmBo bo = confirmBo("0", null);
        bo.setIsDiscard(1);

        assertThat(service.confirm(bo)).isEqualTo(1);

        ArgumentCaptor<StoreReturn> captor = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper).update(captor.capture(), any());
        assertThat(captor.getValue().getIsDiscard()).isEqualTo(1);
        assertThat(captor.getValue().getReturnStatus()).isEqualTo("received");
        assertThat(captor.getValue().getLocationId()).isNull();
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("单位退回历史pending不解锁，单位新增候选和创建仍要求计量比例")
    void unitReturnKeepsConversionAndCap() {
        existing.setReturnType("unit");
        existing.setReturnUnit("mine");
        existing.setStoreId(null);
        product.setMaterialNum(null);
        StoreReturnQuery query = new StoreReturnQuery();
        query.setReturnType("unit");
        query.setReturnUnit("mine");

        var operations = service.listOperationItems(query);
        assertThat(operations.get(0).getUnlimitedConfirmWeight()).isFalse();
        assertThat(operations.get(0).getCanConvert()).isFalse();
        assertThat(service.listUnitCandidates().get(0).getCanConvert()).isFalse();
        assertThatThrownBy(() -> service.confirm(confirmBo("2.345", "2.345")))
            .isInstanceOf(ServiceException.class).hasMessageContaining("120%");

        StoreReturnUnitBo bo = new StoreReturnUnitBo();
        bo.setReturnUnit("mine");
        bo.setReturnDate(LocalDate.of(2026, 10, 14));
        StoreReturnUnitBo.Item item = new StoreReturnUnitBo.Item();
        item.setProductId(PRODUCT_ID);
        item.setReturnQuantity(new BigDecimal("0.5"));
        bo.setItems(List.of(item));
        assertThatThrownBy(() -> service.createUnitReturns(bo))
            .isInstanceOf(ServiceException.class).hasMessageContaining("计量规则");
        verify(baseMapper, never()).insert(any(StoreReturn.class));
        verifyNoWrite();
    }

    @Test
    @DisplayName("其他方向的记录不能套用门店实际KG确认的例外")
    void otherDirectionDoesNotGetUnlimitedWeight() {
        existing.setReturnDirection("warehouse_to_store");

        assertThatThrownBy(() -> service.confirm(confirmBo("2.345", "2.345")))
            .isInstanceOf(ServiceException.class).hasMessageContaining("120%");

        verifyNoWrite();
    }

    @Test
    @DisplayName("历史其他产品实收保留原值，重复确认仍拒绝且不写库存")
    void historicalEchoAndRepeatGuardRemain() {
        existing.setReturnStatus("received");
        existing.setReceivedQty(new BigDecimal("0.125"));
        existing.setReceivedWeight(new BigDecimal("0.125"));
        existing.setLocationId(LOCATION_ID);

        var applet = service.listAppletItemsByStoreAndStatus(STORE_ID, "confirmed", null);
        assertThat(applet.get(0).getConfirmWeight()).isEqualByComparingTo("0.125");
        var operations = service.listOperationItems(new StoreReturnQuery());
        assertThat(operations.get(0).getReceivedQty()).isEqualByComparingTo("0.125");
        assertThat(operations.get(0).getReceivedWeight()).isEqualByComparingTo("0.125");
        assertThatThrownBy(() -> service.confirm(confirmBo("2.345", "2.345")))
            .isInstanceOf(ServiceException.class).hasMessageContaining("重复确认");
        verifyNoWrite();
    }

    private StoreReturnConfirmBo confirmBo(String qty, String weight) {
        StoreReturnConfirmBo bo = new StoreReturnConfirmBo();
        bo.setId(RETURN_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setReceivedQty(decimal(qty));
        bo.setReceivedWeight(decimal(weight));
        return bo;
    }

    private void verifyInbound(String quantity) {
        ArgumentCaptor<BigDecimal> captor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(purchaseInService).inboundReturnBasket(eq(MATERIAL_ID), eq(LOCATION_ID), captor.capture(),
            eq("store_return_in"), anyString());
        assertThat(captor.getValue()).isEqualByComparingTo(quantity);
    }

    private void verifyNoWrite() {
        verify(baseMapper, never()).update(any(StoreReturn.class), any());
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    private List<ProductInfo> selectProducts(LambdaQueryWrapper<ProductInfo> wrapper) {
        String sql = wrapper.getSqlSegment();
        Collection<Object> values = wrapper.getParamNameValuePairs().values();
        return products.values().stream()
            .filter(p -> sql.contains("product_id") ? values.contains(p.getProductId()) : values.contains(p.getId()))
            .toList();
    }

    private static ProductInfo product(Long id, String code, String name, String unit, String belongType) {
        ProductInfo result = new ProductInfo();
        result.setId(id);
        result.setProductId(code);
        result.setProductName(name);
        result.setProductUnit(unit);
        result.setBelongType(belongType);
        return result;
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
