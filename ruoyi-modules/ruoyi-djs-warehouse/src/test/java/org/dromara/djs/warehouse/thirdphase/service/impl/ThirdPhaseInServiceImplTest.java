package org.dromara.djs.warehouse.thirdphase.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.UserService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.domain.CropProduct;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.crop.mapper.CropProductMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.thirdphase.domain.ThirdPhaseIn;
import org.dromara.djs.warehouse.thirdphase.domain.bo.ThirdPhaseInBo;
import org.dromara.djs.warehouse.thirdphase.mapper.ThirdPhaseInMapper;
import org.junit.jupiter.api.AfterEach;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ThirdPhaseInServiceImpl} 单测（V6 row88 三期作物入库）。
 *
 * <ol>
 *   <li>happy path：果蔬原料 + L0006 + 作物命中 + 1 块在种地 →
 *       stock_flow(third_phase_in/IN/带 plot) + 按 plot 建篮 + 地块打标 + 落记录行</li>
 *   <li>闸 ①：产品不是果蔬原材料 → 抛，且一行台账都不落</li>
 *   <li>闸 ②：库位不是毛菜保鲜室 L0006 → 抛，且一行台账都不落</li>
 *   <li>闸 ③：产品没关联任何作物 → 抛，且一行台账都不落</li>
 *   <li>闸 ④：入库量 4 位小数 → 抛（不静默四舍五入）</li>
 *   <li>无在种地块：不阻断入库，plotNames 落空串、不调地块 update</li>
 * </ol>
 *
 * @author djs
 * @since V6-R88
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ThirdPhaseInServiceImpl 单元测试（V6 row88 三期作物入库）")
class ThirdPhaseInServiceImplTest {

    private static final Long PRODUCT_ID = 88001L;
    private static final Long LOCATION_ID = 9301000000000006L;
    private static final Long CROP_ID = 12001L;
    /** 期望写进地块的【三期】标识值 —— 与 ThirdPhaseInServiceImpl.THIRD_PHASE_YES 对齐。 */
    private static final Integer THIRD_PHASE_YES_EXPECTED = 1;

    private static final Long PLOT_ID = 11001L;
    private static final Long USER_ID = 9001L;

    @Mock
    private ThirdPhaseInMapper thirdPhaseInMapper;
    @Mock
    private ProductInfoMapper productInfoMapper;
    @Mock
    private LocationInfoMapper locationInfoMapper;
    @Mock
    private StockFlowMapper stockFlowMapper;
    @Mock
    private LocationStockMapper locationStockMapper;
    @Mock
    private CropInfoMapper cropInfoMapper;
    @Mock
    private CropProductMapper cropProductMapper;
    @Mock
    private PlantDetailsMapper plantDetailsMapper;
    @Mock
    private PlotInfoMapper plotInfoMapper;
    @Mock
    private IBizCodeGenerator bizCodeGenerator;
    @Mock
    private UserService userService;

    private ThirdPhaseInServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    /**
     * 预热 MP 的 lambda 列缓存。
     *
     * <p>纯 mock 单测里没有 Spring / MyBatis 上下文，`LambdaQueryWrapper.getTargetSql()` 会去查
     * 这份缓存，查不到直接抛 `can not find lambda cache for this entity`。断言 wrapper 内容
     * （在种口径的两个状态条件）必须先过这一步。同 skill `coder-mp-entity-cache-test`。</p>
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, PlantDetails.class);
    }

    @BeforeEach
    void setup() {
        service = new ThirdPhaseInServiceImpl(thirdPhaseInMapper, productInfoMapper, locationInfoMapper,
            stockFlowMapper, locationStockMapper, cropInfoMapper, cropProductMapper,
            plantDetailsMapper, plotInfoMapper, bizCodeGenerator, userService);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap())).thenReturn("F2608301IN0001");
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    // ------------------------------------------------------------------ stub

    /** 果蔬原材料产品（product_attr=2 + belong_type=vegetable）。 */
    private void stubVegMaterialProduct() {
        ProductInfo p = new ProductInfo();
        p.setId(PRODUCT_ID);
        p.setProductName("小白菜");
        p.setProductUnit("kg");
        p.setProductAttr(2);
        p.setBelongType("vegetable");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(p);
    }

    /** 毛菜鲜品库 L0006。 */
    private void stubFreshVegLocation() {
        LocationInfo loc = new LocationInfo();
        loc.setId(LOCATION_ID);
        loc.setLocationCode("L0006");
        loc.setLocationName("毛菜鲜品库");
        when(locationInfoMapper.selectOne(any(Wrapper.class))).thenReturn(loc);
    }

    /** 产品 → 作物：配置表命中一行。 */
    private void stubCropByProduct() {
        CropProduct cp = new CropProduct();
        cp.setCropId(CROP_ID);
        cp.setProductId(PRODUCT_ID);
        when(cropProductMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cp));
        when(cropInfoMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        CropInfo crop = new CropInfo();
        crop.setId(CROP_ID);
        crop.setCropName("小白菜");
        when(cropInfoMapper.selectByIds(any())).thenReturn(List.of(crop));
    }

    /** 在种明细：1 块地。 */
    private void stubOnePlantingPlot() {
        PlantDetails d = new PlantDetails();
        d.setPlotId(PLOT_ID);
        d.setCropId(CROP_ID);
        when(plantDetailsMapper.selectList(any(Wrapper.class))).thenReturn(List.of(d));
        PlotInfo plot = new PlotInfo();
        plot.setId(PLOT_ID);
        plot.setPlotCode("P001");
        plot.setPlotName("地头1");
        when(plotInfoMapper.selectByIds(any())).thenReturn(List.of(plot));
    }

    private ThirdPhaseInBo sampleBo() {
        ThirdPhaseInBo bo = new ThirdPhaseInBo();
        bo.setProductId(PRODUCT_ID);
        bo.setStockNum(new BigDecimal("12.345"));
        bo.setLocationId(LOCATION_ID);
        return bo;
    }

    // ------------------------------------------------------------ happy path

    @Test
    @DisplayName("happy：入库写 stock_flow(third_phase_in/IN/带 plot) + 按 plot 建篮 + 地块打【三期】+ 落记录行")
    void testSubmit_Happy() {
        stubVegMaterialProduct();
        stubFreshVegLocation();
        stubCropByProduct();
        stubOnePlantingPlot();

        service.submit(sampleBo());

        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        StockFlow flow = flowCap.getValue();
        assertThat(flow.getFlowType()).as("入库方式 = 三期作物入库").isEqualTo("third_phase_in");
        assertThat(flow.getInoutType()).isEqualTo("IN");
        assertThat(flow.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(flow.getWarehouseId()).as("落毛菜鲜品库").isEqualTo(LOCATION_ID);
        assertThat(flow.getChangeNum()).isEqualByComparingTo("12.345");
        assertThat(flow.getPlotId()).as("单地块时流水带 plot").isEqualTo(PLOT_ID);

        ArgumentCaptor<LocationStock> basketCap = ArgumentCaptor.forClass(LocationStock.class);
        verify(locationStockMapper, times(1)).insert(basketCap.capture());
        LocationStock basket = basketCap.getValue();
        assertThat(basket.getPlotId()).as("按 plot 建篮，口径同 insertPickStockIn").isEqualTo(PLOT_ID);
        assertThat(basket.getLocationId()).isEqualTo(LOCATION_ID);
        assertThat(basket.getProductStock()).isEqualByComparingTo("12.345");
        // 有 plot 走建篮分支，不应再走 product 级累加
        verify(locationStockMapper, never()).addByProductLocation(any(), any(), any(), any());

        // 【三期】标识是本功能唯一的区分性动作，必须断到「写了什么值、写到哪几个 id」——
        // 只 verify 调用过 update()，把 third_phase=1 改成 =0 也照样绿（变异测试实证存活）。
        ArgumentCaptor<Wrapper> markCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(plotInfoMapper, times(1)).update(eq(null), markCap.capture());
        UpdateWrapper<PlotInfo> markW = (UpdateWrapper<PlotInfo>) markCap.getValue();
        // MP 会把 set 的字面量参数化成 #{ew.paramNameValuePairs.MPGENVALn}，所以列名和值要分开断：
        // 只断 sqlSet 含 "third_phase" 挡不住把值写成 0，必须把参数值本身拎出来比。
        String markSqlSet = markW.getSqlSet();
        assertThat(markSqlSet).as("SET 子句必须写 third_phase 列").contains("third_phase=");
        String thirdPhaseParam = markSqlSet.substring(markSqlSet.indexOf("MPGENVAL"),
            markSqlSet.indexOf(",", markSqlSet.indexOf("MPGENVAL"))).replace("}", "");
        // getParamNameValuePairs() 在 getTargetSql() 跑过之前是空的（MP 惰性构建），顺序不能倒
        assertThat(markW.getTargetSql()).as("只打命中的在种地块，且不碰软删行")
            .contains("id IN").contains("del_flag =");
        assertThat(markW.getParamNameValuePairs())
            .as("third_phase 必须置 1（写成 0 就是没打上标识）")
            .containsEntry(thirdPhaseParam, THIRD_PHASE_YES_EXPECTED);
        assertThat(markW.getParamNameValuePairs().values())
            .as("打标识的 id 集合 = 反查到的在种地块").contains(PLOT_ID);

        // 在种口径的两个过滤条件（plant_status=completed / harvest_status<>completed）也要断，
        // 否则删掉它们 6 条用例照样全绿（变异测试实证存活）——那会让标识打到已采完的地块上。
        ArgumentCaptor<LambdaQueryWrapper> detailCap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(plantDetailsMapper, atLeastOnce()).selectList(detailCap.capture());
        LambdaQueryWrapper<PlantDetails> detailW = detailCap.getValue();
        assertThat(detailW.getTargetSql()).as("在种口径必须同时带上两个状态条件")
            .contains("plant_status =").contains("harvest_status <>");
        assertThat(detailW.getParamNameValuePairs().values()).contains("completed");

        ArgumentCaptor<ThirdPhaseIn> recCap = ArgumentCaptor.forClass(ThirdPhaseIn.class);
        verify(thirdPhaseInMapper, times(1)).insert(recCap.capture());
        ThirdPhaseIn rec = recCap.getValue();
        assertThat(rec.getCropId()).isEqualTo(CROP_ID);
        assertThat(rec.getCropName()).isEqualTo("小白菜");
        assertThat(rec.getProductName()).isEqualTo("小白菜");
        assertThat(rec.getStockNum()).isEqualByComparingTo("12.345");
        assertThat(rec.getLocationName()).isEqualTo("毛菜鲜品库");
        assertThat(rec.getPlotNames()).isEqualTo("地头1");
        assertThat(rec.getPlotIds()).isEqualTo(String.valueOf(PLOT_ID));
        assertThat(rec.getOperatorId()).isEqualTo(USER_ID);
        assertThat(rec.getInTime()).isNotNull();
    }

    // ------------------------------------------------------------------ 三条闸

    @Test
    @DisplayName("闸①：产品不是果蔬原材料（belong_type=pork）→ 抛，且不落任何台账")
    void testSubmit_NotVegMaterial() {
        ProductInfo p = new ProductInfo();
        p.setId(PRODUCT_ID);
        p.setProductName("五花肉");
        p.setProductAttr(2);
        p.setBelongType("pork");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(p);
        stubFreshVegLocation();

        assertThatThrownBy(() -> service.submit(sampleBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不是果蔬类原材料");

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never()).insert(any(LocationStock.class));
        verify(thirdPhaseInMapper, never()).insert(any(ThirdPhaseIn.class));
        verify(plotInfoMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("闸②：库位不是毛菜保鲜室 L0006 → 抛，且不落任何台账")
    void testSubmit_WrongLocation() {
        stubVegMaterialProduct();
        stubFreshVegLocation();
        ThirdPhaseInBo bo = sampleBo();
        bo.setLocationId(90003L); // 蔬菜保鲜库，不是 L0006

        assertThatThrownBy(() -> service.submit(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("只能入毛菜保鲜室");

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never()).insert(any(LocationStock.class));
        verify(thirdPhaseInMapper, never()).insert(any(ThirdPhaseIn.class));
    }

    @Test
    @DisplayName("闸③：产品没关联任何作物 → 抛并指向作物管理·产品配置，且不落任何台账")
    void testSubmit_NoCropRelated() {
        stubVegMaterialProduct();
        stubFreshVegLocation();
        when(cropProductMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(cropInfoMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(sampleBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("产品配置");

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(thirdPhaseInMapper, never()).insert(any(ThirdPhaseIn.class));
    }

    @Test
    @DisplayName("闸④：入库量 4 位小数 → 抛（不静默四舍五入成 3 位）")
    void testSubmit_TooManyDecimals() {
        stubVegMaterialProduct();
        stubFreshVegLocation();
        ThirdPhaseInBo bo = sampleBo();
        bo.setStockNum(new BigDecimal("1.2345"));

        assertThatThrownBy(() -> service.submit(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("最多 3 位小数");

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
    }

    // --------------------------------------------------------- 无在种地块降级

    @Test
    @DisplayName("无在种地块：入库照常（不阻断），plotNames 落空串、不调地块 update、库存退化成产品级行")
    void testSubmit_NoPlantingPlot() {
        stubVegMaterialProduct();
        stubFreshVegLocation();
        stubCropByProduct();
        when(plantDetailsMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(locationStockMapper.addByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(), eq(USER_ID)))
            .thenReturn(1);

        service.submit(sampleBo());

        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        assertThat(flowCap.getValue().getPlotId()).as("无地块 → 流水不带 plot").isNull();

        verify(locationStockMapper, times(1))
            .addByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(), eq(USER_ID));
        verify(locationStockMapper, never()).insert(any(LocationStock.class));
        verify(plotInfoMapper, never()).update(any(), any(Wrapper.class));

        ArgumentCaptor<ThirdPhaseIn> recCap = ArgumentCaptor.forClass(ThirdPhaseIn.class);
        verify(thirdPhaseInMapper, times(1)).insert(recCap.capture());
        assertThat(recCap.getValue().getPlotNames())
            .as("一块地都没打上 → 列表里看得出来，不是静默跳过").isEmpty();
    }
}
