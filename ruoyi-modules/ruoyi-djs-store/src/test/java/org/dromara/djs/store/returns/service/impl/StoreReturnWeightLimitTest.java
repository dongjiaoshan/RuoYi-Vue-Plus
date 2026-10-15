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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.when;

/** D-0151：仓库确认重量容差、实际入库数量与果蔬默认库位的公开接口回归。 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreReturnServiceImpl row304 确认重量与库位")
class StoreReturnWeightLimitTest {

    private static final Long RETURN_ID = 61001L;
    private static final Long STORE_ID = 5001L;
    private static final Long PRODUCT_ID = 8001L;
    private static final Long MATERIAL_ID = 8002L;
    private static final Long CONFIGURED_LOCATION_ID = 3002L;
    private static final Long VEGETABLE_LOCATION_ID = 3006L;
    private static final Long PORK_FRESH_LOCATION_ID = 3003L;
    private static final Long FROZEN_LOCATION_ID = 3004L;

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
    private final Map<Long, LocationInfo> locations = new LinkedHashMap<>();

    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
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
        existing.setReturnNo("RET2026101400001");
        existing.setReturnDate(LocalDateTime.of(2026, 10, 14, 8, 0));
        existing.setReturnQuantity(new BigDecimal("0.5"));
        existing.setGoodsWeight(BigDecimal.ZERO);

        product = product(PRODUCT_ID, "P0042", "有机紫线茄500g", "份", "vegetable");
        product.setProductAttr(1);
        product.setProductMaterial(MATERIAL_ID);
        product.setMaterialNum(new BigDecimal("0.5"));
        material = product(MATERIAL_ID, "Y00041", "紫线茄", "kg", "vegetable");
        material.setProductAttr(2);
        material.setStoreLocationId(CONFIGURED_LOCATION_ID.toString());
        products.put(PRODUCT_ID, product);
        products.put(MATERIAL_ID, material);

        locations.put(CONFIGURED_LOCATION_ID,
            location(CONFIGURED_LOCATION_ID, "L0002", "产品预设库位", 1));
        locations.put(VEGETABLE_LOCATION_ID,
            location(VEGETABLE_LOCATION_ID, "L0006", "毛菜保鲜室", 1));
        locations.put(PORK_FRESH_LOCATION_ID,
            location(PORK_FRESH_LOCATION_ID, "L0003", "猪肉鲜品库", 1));
        locations.put(FROZEN_LOCATION_ID,
            location(FROZEN_LOCATION_ID, "L0004", "冻品库", 1));

        when(baseMapper.selectById(RETURN_ID)).thenReturn(existing);
        when(baseMapper.selectList(any())).thenReturn(List.of(existing));
        when(baseMapper.update(any(StoreReturn.class), any())).thenReturn(1);
        when(productInfoMapper.selectById(any())).thenAnswer(inv -> products.get(inv.getArgument(0)));
        when(productInfoMapper.selectList(any())).thenAnswer(inv -> selectProducts(inv.getArgument(0)));
        when(locationInfoMapper.selectById(any())).thenAnswer(inv -> locations.get(inv.getArgument(0)));
        when(locationInfoMapper.selectList(any())).thenAnswer(inv -> selectLocations(inv.getArgument(0)));
        when(locationInfoMapper.selectOne(any())).thenAnswer(inv -> {
            List<LocationInfo> matches = selectLocations(inv.getArgument(0));
            return matches.isEmpty() ? null : matches.get(0);
        });
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
    @CsvSource({
        "份, kg, 0.5, 0.5, 0.300, true",
        "份, kg, 0.5, 0.5, 0.301, false",
        "KG, kg, 0.25, 7, 0.300, true",
        "KG, kg, 0.25, 7, 0.301, false",
        "份, 公斤, 0.5, 0.5, 0.300, true",
        "份, 公斤, 0.5, 0.5, 0.301, false"
    })
    @DisplayName("按原材料kg判定：正退回量折料的120%边界允许，多0.001拒绝")
    void kgLimitUsesReturnQuantity(String productUnit, String materialUnit, String returnQuantity,
                                  String ratio, String received, boolean accepted) {
        product.setProductUnit(productUnit);
        material.setProductUnit(materialUnit);
        product.setMaterialNum(new BigDecimal(ratio));
        existing.setReturnQuantity(new BigDecimal(returnQuantity));
        existing.setGoodsWeight(new BigDecimal("0.100"));
        // 旧的当天到店上限不应继续挡住本次退回折料后合法的120%重量。
        when(productProductionMapper.sumDeliveredWeightToStore(any(), any(), any()))
            .thenReturn(new BigDecimal("0.100"));

        assertConfirmation(received, received, accepted);
    }

    @ParameterizedTest
    @CsvSource({"0.003, true", "0.004, false"})
    @DisplayName("0.003kg的120%上限向下保留三位，不能四舍五入放到0.004kg")
    void microWeightLimitFloorsAtThreeDecimals(String received, boolean accepted) {
        product.setProductUnit("kg");
        existing.setReturnQuantity(new BigDecimal("0.003"));

        assertConfirmation(received, received, accepted);
    }

    @Test
    @DisplayName("份数折料先按既有三位HALF_UP算base，再计算120%上限")
    void materialConversionRetainsHalfUpBase() {
        existing.setReturnQuantity(new BigDecimal("0.001"));
        product.setMaterialNum(new BigDecimal("0.5"));

        assertConfirmation("0.001", "0.001", true);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL", "0", "-1"}, nullValues = "NULL")
    @DisplayName("无正退回量时使用正goodsWeight作为原材料重量base")
    void missingPositiveQuantityFallsBackToGoodsWeight(String returnQuantity) {
        existing.setReturnQuantity(decimal(returnQuantity));
        existing.setGoodsWeight(new BigDecimal("0.250"));
        product.setMaterialNum(new BigDecimal("9"));

        assertConfirmation("0.300", null, true);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,NULL", "0,0", "-1,-1"}, nullValues = "NULL")
    @DisplayName("kg无有效base时拒绝确认，不写记录和库存")
    void missingWeightBaseRejects(String returnQuantity, String goodsWeight) {
        existing.setReturnQuantity(decimal(returnQuantity));
        existing.setGoodsWeight(decimal(goodsWeight));

        assertConfirmation("0.001", "0.001", false);
    }

    @Test
    @DisplayName("正base计算出的三位cap为0时拒绝确认，不回落goodsWeight放行")
    void positiveBaseWithZeroCapRejects() {
        product.setProductUnit("kg");
        existing.setReturnQuantity(new BigDecimal("0.0004"));
        existing.setGoodsWeight(BigDecimal.ONE);

        assertConfirmation("0.001", "0.001", false);
    }

    @Test
    @DisplayName("无base的零量丢弃仍能收口，确认状态但不入库")
    void zeroDiscardWithoutWeightBaseKeepsStatusOnly() {
        existing.setReturnQuantity(null);
        existing.setGoodsWeight(BigDecimal.ZERO);
        locations.remove(VEGETABLE_LOCATION_ID);
        StoreReturnConfirmBo bo = confirmBo("0", null);
        bo.setIsDiscard(1);

        assertThat(service.confirm(bo)).isEqualTo(1);

        ArgumentCaptor<StoreReturn> captor = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper).update(captor.capture(), any());
        assertThat(captor.getValue().getReturnStatus()).isEqualTo("received");
        assertThat(captor.getValue().getIsDiscard()).isEqualTo(1);
        assertThat(captor.getValue().getLocationId()).isNull();
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    @ParameterizedTest
    @CsvSource({"0.301,0.250", "0.250,0.301"})
    @DisplayName("两个实收字段分别受kg上限约束，不能用较小字段绕过实际入库数校验")
    void bothReceivedFieldsRespectKgLimit(String qty, String weight) {
        assertConfirmation(qty, weight, false);
    }

    @ParameterizedTest
    @CsvSource({"60,60,true", "60.001,60,false", "60,60.001,false", "72,72,false"})
    @DisplayName("非kg原材料仍以退回量乘计量规则封顶，两个字段均不增加20%")
    void nonKgMaterialKeepsQuantityLimit(String qty, String weight, boolean accepted) {
        material.setProductUnit("枚");
        product.setMaterialNum(new BigDecimal("30"));
        existing.setReturnQuantity(new BigDecimal("2"));

        assertConfirmation(qty, weight, accepted);
    }

    @Test
    @DisplayName("原材料非kg时，产品自身kg单位不能触发20%重量容差")
    void materialUnitControlsNonKgBranch() {
        product.setProductUnit("kg");
        material.setProductUnit("枚");
        existing.setReturnQuantity(new BigDecimal("2"));
        product.setMaterialNum(new BigDecimal("30"));

        assertConfirmation("60.001", "60.001", false);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL", "0", "-2"}, nullValues = "NULL")
    @DisplayName("未配置有效计量规则时按1折料")
    void missingMaterialRatioDefaultsToOne(String ratio) {
        product.setMaterialNum(decimal(ratio));

        assertConfirmation("0.600", "0.600", true);
    }

    @Test
    @DisplayName("果蔬默认入库毛菜保鲜室，忽略其他产品预设和猪肉fresh参数")
    void vegetableDefaultLocationUsesEnabledL0006() {
        StoreReturnConfirmBo bo = confirmBo("0.300", "0.300");
        bo.setTargetLocationType("fresh");

        assertThat(service.confirm(bo)).isEqualTo(1);

        verifyInbound(VEGETABLE_LOCATION_ID, "0.300");
        ArgumentCaptor<StoreReturn> captor = ArgumentCaptor.forClass(StoreReturn.class);
        verify(baseMapper).update(captor.capture(), any());
        assertThat(captor.getValue().getLocationId()).isEqualTo(VEGETABLE_LOCATION_ID);
    }

    @Test
    @DisplayName("果蔬显式合法库位优先，L0006缺失仍可确认到显式库位")
    void vegetableExplicitLocationTakesPriority() {
        locations.remove(VEGETABLE_LOCATION_ID);
        StoreReturnConfirmBo bo = confirmBo("0.300", "0.300");
        bo.setLocationId(CONFIGURED_LOCATION_ID);

        assertThat(service.confirm(bo)).isEqualTo(1);

        verifyInbound(CONFIGURED_LOCATION_ID, "0.300");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("果蔬L0006缺失或停用时明确拒绝，不能回落其他预设库位")
    void vegetableMissingOrDisabledDefaultLocationRejects(boolean disabled) {
        if (disabled) {
            locations.get(VEGETABLE_LOCATION_ID).setLocationStatus(2);
        } else {
            locations.remove(VEGETABLE_LOCATION_ID);
        }

        assertThatThrownBy(() -> service.confirm(confirmBo("0.300", "0.300")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("L0006");

        verifyNoWrite();
    }

    @ParameterizedTest
    @CsvSource(value = {"store", "NULL"}, nullValues = "NULL")
    @DisplayName("admin待处理门店果蔬默认L0006，原材料仅配置L0002时候选仍含L0006")
    void pendingStoreOperationsOfferVegetableDefault(String returnType) {
        existing.setReturnType(returnType);

        var rows = service.listOperationItems(new StoreReturnQuery());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDefaultLocationId()).isEqualTo(VEGETABLE_LOCATION_ID);
        assertThat(rows.get(0).getLocationOptions()).extracting(v -> v.getId())
            .contains(VEGETABLE_LOCATION_ID);
    }

    @Test
    @DisplayName("小程序待确认项下发0.300kg上限，保留独立计算0.250kg默认值的退回量及计量规则")
    void appletItemsExposeWeightCapWithoutChangingBaseFields() {
        var rows = service.listAppletItemsByStoreAndStatus(STORE_ID, "pending", null);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getMaxConfirmQty()).isEqualByComparingTo("0.300");
        assertThat(rows.get(0).getReturnQuantity()).isEqualByComparingTo("0.5");
        assertThat(rows.get(0).getMaterialNum()).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("小程序无base时不下发虚构上限，服务拒绝正重量确认")
    void appletItemsWithoutBaseHaveNoCapAndCannotConfirmPositiveWeight() {
        existing.setReturnQuantity(null);
        existing.setGoodsWeight(BigDecimal.ZERO);

        var rows = service.listAppletItemsByStoreAndStatus(STORE_ID, "pending", null);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getMaxConfirmQty()).isNull();
        assertThatThrownBy(() -> service.confirm(confirmBo("0.001", "0.001")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("没有有效正值");
        verifyNoWrite();
    }

    @Test
    @DisplayName("admin已处理历史果蔬保留原库位默认与回显")
    void receivedOperationsKeepHistoricalLocation() {
        existing.setReturnStatus("received");
        existing.setLocationId(CONFIGURED_LOCATION_ID);

        var rows = service.listOperationItems(new StoreReturnQuery());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getLocationId()).isEqualTo(CONFIGURED_LOCATION_ID);
        assertThat(rows.get(0).getDefaultLocationId()).isEqualTo(CONFIGURED_LOCATION_ID);
    }

    @Test
    @DisplayName("admin单位退回明细不切到果蔬L0006默认")
    void unitOperationsKeepConfiguredLocation() {
        existing.setReturnType("unit");
        existing.setReturnUnit("mine");
        existing.setStoreId(null);
        StoreReturnQuery query = new StoreReturnQuery();
        query.setReturnType("unit");
        query.setReturnUnit("mine");

        var rows = service.listOperationItems(query);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDefaultLocationId()).isEqualTo(CONFIGURED_LOCATION_ID);
        assertThat(rows.get(0).getLocationOptions()).extracting(v -> v.getId())
            .containsExactly(CONFIGURED_LOCATION_ID);
    }

    @Test
    @DisplayName("单位退回新增候选保持原材料预设库位，不受门店果蔬新默认影响")
    void unitCandidatesKeepConfiguredLocation() {
        var rows = service.listUnitCandidates();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDefaultLocationId()).isEqualTo(CONFIGURED_LOCATION_ID);
        assertThat(rows.get(0).getLocationOptions()).extracting(v -> v.getId())
            .containsExactly(CONFIGURED_LOCATION_ID);
    }

    @ParameterizedTest
    @CsvSource({"fresh,3003", "frozen,3004"})
    @DisplayName("猪肉fresh/frozen继续按鲜品库和冻品库入库")
    void porkTargetLocationKeepsFreshFrozenRouting(String type, Long expectedLocation) {
        product.setBelongType("pork");
        material.setBelongType("pork");
        StoreReturnConfirmBo bo = confirmBo("0.300", "0.300");
        bo.setTargetLocationType(type);

        assertThat(service.confirm(bo)).isEqualTo(1);

        verifyInbound(expectedLocation, "0.300");
    }

    @Test
    @DisplayName("admin猪肉候选仍仅鲜品库和冻品库，不加入毛菜保鲜室")
    void porkOperationsKeepFreshFrozenOptions() {
        product.setBelongType("pork");
        material.setBelongType("pork");

        var rows = service.listOperationItems(new StoreReturnQuery());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDefaultLocationId()).isEqualTo(PORK_FRESH_LOCATION_ID);
        assertThat(rows.get(0).getLocationOptions()).extracting(v -> v.getId())
            .containsExactly(PORK_FRESH_LOCATION_ID, FROZEN_LOCATION_ID);
    }

    private void assertConfirmation(String qty, String weight, boolean accepted) {
        StoreReturnConfirmBo bo = confirmBo(qty, weight);
        if (accepted) {
            assertThat(service.confirm(bo)).isEqualTo(1);
            verifyInbound(VEGETABLE_LOCATION_ID, qty);
        } else {
            assertThatThrownBy(() -> service.confirm(bo)).isInstanceOf(ServiceException.class);
            verifyNoWrite();
        }
    }

    private StoreReturnConfirmBo confirmBo(String qty, String weight) {
        StoreReturnConfirmBo bo = new StoreReturnConfirmBo();
        bo.setId(RETURN_ID);
        bo.setReceivedQty(decimal(qty));
        bo.setReceivedWeight(decimal(weight));
        return bo;
    }

    private void verifyInbound(Long locationId, String qty) {
        ArgumentCaptor<BigDecimal> captor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(purchaseInService).inboundReturnBasket(eq(MATERIAL_ID), eq(locationId), captor.capture(),
            eq("store_return_in"), anyString());
        assertThat(captor.getValue()).isEqualByComparingTo(qty);
    }

    private void verifyNoWrite() {
        verify(baseMapper, never()).update(any(StoreReturn.class), any());
        verify(purchaseInService, never()).inboundReturnBasket(any(), any(), any(), anyString(), any());
    }

    /** 用查询的实际参数筛选 fixture，避免顺序桩把原材料误当成清单产品。 */
    private List<ProductInfo> selectProducts(LambdaQueryWrapper<ProductInfo> wrapper) {
        String sql = wrapper.getSqlSegment();
        Collection<Object> values = wrapper.getParamNameValuePairs().values();
        return products.values().stream()
            .filter(p -> sql.contains("product_id") ? values.contains(p.getProductId()) : values.contains(p.getId()))
            .toList();
    }

    private List<LocationInfo> selectLocations(LambdaQueryWrapper<LocationInfo> wrapper) {
        String sql = wrapper.getSqlSegment();
        Collection<Object> values = wrapper.getParamNameValuePairs().values();
        return locations.values().stream()
            .filter(l -> !sql.contains("id IN") || values.contains(l.getId()))
            .filter(l -> !sql.contains("location_code") || values.contains(l.getLocationCode()))
            .filter(l -> !sql.contains("location_name") || values.contains(l.getLocationName()))
            .filter(l -> !sql.contains("location_status") || Integer.valueOf(1).equals(l.getLocationStatus()))
            .toList();
    }

    private static ProductInfo product(Long id, String code, String name, String unit, String belongType) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductId(code);
        p.setProductName(name);
        p.setProductUnit(unit);
        p.setBelongType(belongType);
        return p;
    }

    private static LocationInfo location(Long id, String code, String name, int status) {
        LocationInfo l = new LocationInfo();
        l.setId(id);
        l.setLocationCode(code);
        l.setLocationName(name);
        l.setLocationStatus(status);
        l.setLocationSort(1);
        return l;
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
