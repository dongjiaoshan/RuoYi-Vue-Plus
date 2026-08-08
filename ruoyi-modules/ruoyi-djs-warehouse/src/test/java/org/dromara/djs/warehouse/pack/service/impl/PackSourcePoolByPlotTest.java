package org.dromara.djs.warehouse.pack.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.domain.bo.CeleryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.DryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.VegPackBo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V6 row60：打包来源池按「原材料 + 地块 / 耳号」聚合，<b>不带库位维度</b>。
 *
 * <p>复现的线上现象（admin 果蔬打包，2026-08-07 截图）：卡片「领用剩余重量 2117 g」，
 * 输入 309 g 提交后弹「打包失败：来源待打包库存不足：当前 0.117，本次打包 0.309」——
 * 前端按「原材料 + 地块」跨行求和显示，后端只认前端挑中的那<b>一条</b> inhouse 行余量。
 * 同一原材料同一地块之所以有多行，是因为从不同库位分多次领用（每次领用只扣一个库位的篮）。</p>
 *
 * <p>本类只钉这一条口径，与 {@code ProductProductionServiceImplTest} 的既有断言互不覆盖。</p>
 *
 * @author djs
 * @since V6-row60
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("V6 row60：打包来源池按 原材料+地块 聚合（不分库位）")
class PackSourcePoolByPlotTest {

    private static final Long MATERIAL_ID = 60001L;
    private static final Long PLOT_ID = 50001L;
    private static final Long PRODUCT_ID = 60010L;

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
        // MyBatis-Plus 单测 entity cache 预热（coder-mp-entity-cache-test）
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
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap())).thenReturn("F2608070001");
        when(bizCodeGenerator.generate(eq(BizCodeType.PRODUCE_NO), anyMap())).thenReturn("P2608070001");
        when(inhouseMapper.deductWeightById(anyLong(), any())).thenReturn(1);
        when(locationInfoMapper.selectById(anyLong())).thenReturn(location());
        // resolveSourcePool 按**来源行所属原材料**的 belong_type 选池；缺省给果蔬原料，
        // 肉品/其他产品用例再各自覆盖成 pork / egg。
        when(productInfoMapper.selectById(MATERIAL_ID)).thenReturn(materialProduct("vegetable"));
    }

    /** 来源行所属的原材料 product（attr=2），业态决定池口径。 */
    private ProductInfo materialProduct(String belongType) {
        ProductInfo p = new ProductInfo();
        p.setId(MATERIAL_ID);
        p.setProductName("QA原材料-" + belongType);
        p.setProductType(1);
        p.setProductUnit("kg");
        p.setProductAttr(2);
        p.setBelongType(belongType);
        return p;
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    // ===================== fixtures =====================

    /** 一条领用产的待打包 inhouse 行；locationId 是它的来源库位——正是本 ticket 要求**不**参与分池的维度。 */
    private ProductInhouse inhouse(long id, Long plotId, Long locationId, String weight) {
        ProductInhouse i = new ProductInhouse();
        i.setId(id);
        i.setProductId(MATERIAL_ID);
        i.setProductName("有机空心菜(毛菜)");
        i.setProductType(1);
        i.setProductUnit("kg");
        i.setProductWeight(new BigDecimal(weight));
        i.setPlotId(plotId);
        i.setLocationId(locationId);
        i.setMaterialId(MATERIAL_ID);
        i.setSource("warehouse");
        return i;
    }

    private ProductInfo vegProduct() {
        ProductInfo p = new ProductInfo();
        p.setId(PRODUCT_ID);
        p.setProductId("PROD-VEG-KXC-300");
        p.setProductName("有机空心菜300g");
        p.setProductType(1);
        p.setProductUnit("份");
        p.setBelongType("vegetable");
        p.setIsDelivery(1);
        p.setStoreLocationId("90001");
        return p;
    }

    private LocationInfo location() {
        LocationInfo l = new LocationInfo();
        l.setId(90001L);
        l.setLocationName("保鲜库");
        return l;
    }

    private VegPackBo vegBo(String weight) {
        VegPackBo bo = new VegPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(PRODUCT_ID);
        bo.setProductWeight(new BigDecimal(weight));
        // 需求 C：发货月台打包须选门店（打包即扣需求）；无未完成需求时 warn 跳过扣减
        bo.setStoreId(7L);
        return bo;
    }

    /** 抓来源池查询的 wrapper（只有来源池查询会走 inhouseMapper.selectList）。 */
    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<ProductInhouse> capturePoolWrapper() {
        ArgumentCaptor<LambdaQueryWrapper<ProductInhouse>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inhouseMapper).selectList(cap.capture());
        return cap.getValue();
    }

    private String capturePoolSql() {
        return capturePoolWrapper().getTargetSql();
    }

    /**
     * 池谓词**实际绑定的值**（`getTargetSql()` 里是 `?` 占位符，光断言 SQL 里有列名杀不掉「绑错值」的变异）。
     */
    private java.util.Collection<Object> capturePoolBoundValues() {
        return capturePoolWrapper().getParamNameValuePairs().values();
    }

    // ===================== 果蔬打包 =====================

    @Test
    @DisplayName("submitVegPack: 同原材料同地块跨库位两行(0.117+2.000) → 打 0.309 放行，FIFO 先扣完 0.117 行再扣第二行 0.192")
    void vegPack_poolsAcrossLocations() {
        // 复现截图：工人挑中的是 0.117 那条（前端 onPlotChange 取首个匹配行），另一条 2.000 在别的库位
        ProductInhouse picked = inhouse(70001L, PLOT_ID, 90001L, "0.117");
        ProductInhouse other = inhouse(70002L, PLOT_ID, 90002L, "2.000");
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(vegProduct());
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked, other));

        service.submitVegPack(vegBo("0.309"));

        // 修前：requireInhouseEnough(picked=0.117) 抛「来源待打包库存不足：当前 0.117」；修后按池总重 2.117 放行
        ArgumentCaptor<ProductProduction> pCap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper).insert(pCap.capture());
        assertThat(pCap.getValue().getProductWeight()).isEqualByComparingTo("0.309");
        // FIFO：先把 0.117 那行用尽（整行软删），剩 0.192 从第二行部分扣
        verify(inhouseMapper).deleteById(70001L);
        verify(inhouseMapper).deductWeightById(eq(70002L), eq(new BigDecimal("0.192")));
        // **被部分扣的那行绝不能连行软删** —— 只扣掉 0.192 却把整行删了 = 剩下的 1.808 凭空消失。
        // 这条断言钉的是 tryConsumeInhouse 里「用尽才删」的 == 判定：改成 >= 就每次消耗都删行。
        verify(inhouseMapper, never()).deleteById(70002L);
    }

    @Test
    @DisplayName("单行部分扣：只扣本次用量、**不软删该行**（改成「扣了就删」会把剩余量整行吞掉）")
    void partialConsume_neverSoftDeletesTheRow() {
        ProductInhouse only = inhouse(70001L, PLOT_ID, 90001L, "2.117");
        when(inhouseMapper.selectById(70001L)).thenReturn(only);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(vegProduct());
        when(inhouseMapper.selectList(any())).thenReturn(List.of(only));

        service.submitVegPack(vegBo("0.250"));

        verify(inhouseMapper).deductWeightById(eq(70001L), eq(new BigDecimal("0.250")));
        verify(inhouseMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("submitVegPack: 来源池查询按 product_id + plot_id，且 SQL 里不出现 location_id（甲方：不要带上库位的标准）")
    void vegPack_poolQueryHasPlotButNoLocation() {
        ProductInhouse picked = inhouse(70001L, PLOT_ID, 90001L, "5.000");
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(vegProduct());
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked));

        service.submitVegPack(vegBo("1.000"));

        String sql = capturePoolSql();
        assertThat(sql).contains("product_id").contains("plot_id")
            .contains("CURDATE()").contains("product_weight");
        assertThat(sql).doesNotContain("location_id");
        // source='warehouse'：门店现场分割产的 inhouse（source='store'）绝不能混进仓库打包池（跨域库存隔离）
        assertThat(sql).contains("source");
        assertThat(capturePoolBoundValues()).contains("warehouse");
        // FIFO：先吃最早那行。改成 DESC 就是 LIFO，甲方口径是先进先出
        assertThat(sql).contains("ORDER BY").doesNotContain("DESC");
        assertThat(sql).contains("produce_date ASC").contains("id ASC");
    }

    @Test
    @DisplayName("submitVegPack: 来源无地块(外购) → 池用 plot_id IS NULL，不是恒假的 plot_id = null")
    void vegPack_nullPlotUsesIsNull() {
        ProductInhouse picked = inhouse(70001L, null, 90001L, "5.000");
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(vegProduct());
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked));

        service.submitVegPack(vegBo("1.000"));

        assertThat(capturePoolSql()).contains("plot_id IS NULL");
    }

    @Test
    @DisplayName("submitVegPack: 池总重仍不够(0.117+0.100 < 0.309) → 抛，报的是池总重 0.217 而非单行 0.117，且不 INSERT")
    void vegPack_poolStillInsufficient_reportsPoolTotal() {
        ProductInhouse picked = inhouse(70001L, PLOT_ID, 90001L, "0.117");
        ProductInhouse other = inhouse(70002L, PLOT_ID, 90002L, "0.100");
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(vegProduct());
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked, other));

        assertThatThrownBy(() -> service.submitVegPack(vegBo("0.309")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("来源待打包库存不足")
            .hasMessageContaining("0.217");

        verify(productionMapper, never()).insert(any(ProductProduction.class));
        verify(inhouseMapper, never()).deleteById(anyLong());
        verify(inhouseMapper, never()).deductWeightById(anyLong(), any());
    }

    @Test
    @DisplayName("submitVegPack: 池谓词绑的是**来源行自己的** product_id / plot_id —— 断到绑定值，光断 SQL 里有列名杀不掉「绑错值」")
    void vegPack_poolBindsOwnProductAndPlotValues() {
        ProductInhouse picked = inhouse(70001L, PLOT_ID, 90001L, "0.117");
        ProductInhouse samePlot = inhouse(70002L, PLOT_ID, 90002L, "0.100");
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(vegProduct());
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked, samePlot));

        assertThatThrownBy(() -> service.submitVegPack(vegBo("0.309")))
            .isInstanceOf(ServiceException.class);

        assertThat(capturePoolSql()).contains("plot_id");
        // getTargetSql() 里 plot 值是 `?`，必须查绑定值本身：绑成别的地块 id 时这条会红
        assertThat(capturePoolBoundValues()).contains(PLOT_ID).contains(MATERIAL_ID);
    }

    // ===================== 芹菜打包（果蔬镜像口径）=====================

    @Test
    @DisplayName("submitCeleryPack: 同样按 原材料+地块 池放行(0.117+2.000 打 0.309)，不再只认单行")
    void celeryPack_poolsAcrossLocations() {
        ProductInhouse picked = inhouse(70001L, PLOT_ID, 90001L, "0.117");
        ProductInhouse other = inhouse(70002L, PLOT_ID, 90002L, "2.000");
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        ProductInfo celery = vegProduct();
        celery.setProductUnit("kg");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(celery);
        when(productInfoMapper.selectById(MATERIAL_ID)).thenReturn(materialProduct("vegetable"));
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked, other));

        CeleryPackBo bo = new CeleryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(PRODUCT_ID);
        bo.setProductWeight(new BigDecimal("0.309"));
        bo.setLocationId(90001L);
        bo.setStoreId(7L);

        service.submitCeleryPack(bo);

        verify(productionMapper).insert(any(ProductProduction.class));
        verify(inhouseMapper).deleteById(70001L);
        verify(inhouseMapper).deductWeightById(eq(70002L), eq(new BigDecimal("0.192")));
    }

    // ===================== 其他产品打包（无耳号 → 按原材料池）=====================

    @Test
    @DisplayName("submitDryPack 无耳号: 分多次领用两行(3.000+5.000) → 打 6.000 放行（旧口径只认 resolveAutoSource 挑的那一条）")
    void dryPack_noEar_poolsByMaterial() {
        ProductInhouse picked = inhouse(70001L, null, 90001L, "3.000");
        ProductInhouse other = inhouse(70002L, null, 90002L, "5.000");
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        ProductInfo dry = vegProduct();
        dry.setBelongType("dry_good");
        dry.setProductUnit("kg");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(dry);
        when(productInfoMapper.selectById(MATERIAL_ID)).thenReturn(materialProduct("dry_good"));
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked, other));

        DryPackBo bo = new DryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(PRODUCT_ID);
        bo.setProductWeight(new BigDecimal("6.000"));
        bo.setStoreId(7L);

        service.submitDryPack(bo);

        verify(productionMapper).insert(any(ProductProduction.class));
        verify(inhouseMapper).deleteById(70001L);
        verify(inhouseMapper).deductWeightById(eq(70002L), eq(new BigDecimal("3.000")));
    }

    @Test
    @DisplayName("submitDryPack 无耳号: 池查询按 product_id 聚合，不带 plot_id 也不带 location_id（该页无地块/耳号选择器，与卡片求和同口径）")
    void dryPack_noEar_poolQueryHasNoPlotNorLocation() {
        ProductInhouse picked = inhouse(70001L, null, 90001L, "8.000");
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        ProductInfo dry = vegProduct();
        dry.setBelongType("dry_good");
        dry.setProductUnit("kg");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(dry);
        when(productInfoMapper.selectById(MATERIAL_ID)).thenReturn(materialProduct("dry_good"));
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked));

        DryPackBo bo = new DryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(PRODUCT_ID);
        bo.setProductWeight(new BigDecimal("1.000"));
        bo.setStoreId(7L);

        service.submitDryPack(bo);

        String sql = capturePoolSql();
        assertThat(sql).contains("product_id").contains("CURDATE()").contains("product_weight");
        assertThat(sql).doesNotContain("plot_id");
        assertThat(sql).doesNotContain("location_id");
    }

    @Test
    @DisplayName("submitDryPack 有耳号: 仍走 原材料+耳号 池（row32 口径不动），SQL 带 ear_no")
    void dryPack_withEar_keepsEarPool() {
        ProductInhouse picked = inhouse(70001L, null, 90001L, "3.000");
        picked.setEarNo("010126050101");
        ProductInhouse other = inhouse(70002L, null, 90002L, "5.000");
        other.setEarNo("010126050101");
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        ProductInfo pork = vegProduct();
        pork.setBelongType("pork");
        pork.setProductUnit("kg");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(pork);
        when(productInfoMapper.selectById(MATERIAL_ID)).thenReturn(materialProduct("pork"));
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked, other));

        DryPackBo bo = new DryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(PRODUCT_ID);
        bo.setProductWeight(new BigDecimal("6.000"));
        bo.setStoreId(7L);

        service.submitDryPack(bo);

        String sql = capturePoolSql();
        assertThat(sql).contains("ear_no").contains("material_id");
        assertThat(sql).doesNotContain("location_id");
        // 「今天领用」过滤（doc/14 §5）：删掉它昨天的同耳号行会进池
        assertThat(sql).contains("CURDATE()");
        assertThat(capturePoolBoundValues()).contains("010126050101");
    }

    // ===================== 肉品打包「无耳号」哨兵（P0：不能退化成同原材料一锅端）=====================

    @Test
    @DisplayName("肉品打包选「无耳号」: 池必须带 ear_no IS NULL —— 否则跨耳号吃别的猪的肉（前端 NO_EAR 哨兵语义）")
    void meatPack_noEarSentinel_poolScopedToNullEar() {
        ProductInhouse picked = inhouse(70001L, null, 90001L, "5.000");   // 工人选的：无耳号（外购/无耳标）
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        ProductInfo pork = vegProduct();
        pork.setBelongType("pork");
        pork.setProductUnit("kg");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(pork);
        when(productInfoMapper.selectById(MATERIAL_ID)).thenReturn(materialProduct("pork"));
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked));

        DryPackBo bo = new DryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(PRODUCT_ID);
        bo.setProductWeight(new BigDecimal("1.000"));
        bo.setStoreId(7L);

        service.submitDryPack(bo);

        String sql = capturePoolSql();
        assertThat(sql).contains("ear_no IS NULL");
    }

    @Test
    @DisplayName("肉品打包选「无耳号」: 池必须带 material_id 非空 —— 否则吃掉未领用的燎毛整只白条（material_id 空）")
    void meatPack_noEarSentinel_poolExcludesBurnWhiteBar() {
        ProductInhouse picked = inhouse(70001L, null, 90001L, "5.000");
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        ProductInfo pork = vegProduct();
        pork.setBelongType("pork");
        pork.setProductUnit("kg");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(pork);
        when(productInfoMapper.selectById(MATERIAL_ID)).thenReturn(materialProduct("pork"));
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked));

        DryPackBo bo = new DryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(PRODUCT_ID);
        bo.setProductWeight(new BigDecimal("1.000"));
        bo.setStoreId(7L);

        service.submitDryPack(bo);

        assertThat(capturePoolSql()).contains("material_id IS NOT NULL");
    }

    @Test
    @DisplayName("其他产品打包(egg/dry_good/other) 才走同原材料池：SQL 不带 ear_no —— 与肉品无耳号分岔，别串了")
    void dryPack_otherBelongType_poolHasNoEarPredicate() {
        ProductInhouse picked = inhouse(70001L, null, 90001L, "8.000");
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        ProductInfo egg = vegProduct();
        egg.setBelongType("egg");
        egg.setProductUnit("枚");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(egg);
        when(productInfoMapper.selectById(MATERIAL_ID)).thenReturn(materialProduct("egg"));
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked));

        DryPackBo bo = new DryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(PRODUCT_ID);
        bo.setProductWeight(new BigDecimal("1.000"));
        bo.setStoreId(7L);

        service.submitDryPack(bo);

        assertThat(capturePoolSql()).doesNotContain("ear_no");
    }

    // ===================== 并发：满额消耗必须走带条件的行锁扣减 =====================

    @Test
    @DisplayName("整行用尽也要先过 deductWeightById 行锁闸（affected=0=已被别人抢走 → 抛，不再无条件 deleteById 造成两人同时打成功）")
    void fullConsume_takesRowLockBeforeSoftDelete() {
        ProductInhouse picked = inhouse(70001L, PLOT_ID, 90001L, "1.000");
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(vegProduct());
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked));
        // 并发抢占：别的工人已经把这行吃掉 → 带条件的 UPDATE 影响 0 行
        when(inhouseMapper.deductWeightById(eq(70001L), any())).thenReturn(0);

        assertThatThrownBy(() -> service.submitVegPack(vegBo("1.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已被占用");

        // 抢占时绝不能把行软删掉（那会让别人的事务读到不一致状态）
        verify(inhouseMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("FIFO 首行被别人抢走 → **顺延到池里下一行**继续扣，不整笔抛（池里还有货就不该报打包失败）")
    void poolFifo_skipsRowTakenByOthersAndContinues() {
        ProductInhouse a = inhouse(70001L, PLOT_ID, 90001L, "0.300");
        ProductInhouse b = inhouse(70002L, PLOT_ID, 90002L, "0.300");
        when(inhouseMapper.selectById(70001L)).thenReturn(a);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(vegProduct());
        when(inhouseMapper.selectList(any())).thenReturn(List.of(a, b));
        // 另一个工人已经把首行扣到不足 0.250 → 带条件的 UPDATE 影响 0 行
        when(inhouseMapper.deductWeightById(eq(70001L), any())).thenReturn(0);
        when(inhouseMapper.deductWeightById(eq(70002L), any())).thenReturn(1);

        service.submitVegPack(vegBo("0.250"));

        verify(productionMapper).insert(any(ProductProduction.class));
        verify(inhouseMapper).deductWeightById(eq(70002L), eq(new BigDecimal("0.250")));
        // 抢不到的那行绝不能被软删
        verify(inhouseMapper, never()).deleteById(70001L);
    }

    @Test
    @DisplayName("池里每一行都被抢光、凑不够总量 → consumePoolFifo 尾部闸抛，且不 INSERT 产出（防产出 > 实际消耗）")
    void poolFifo_tailGuardThrowsWhenWholePoolDrained() {
        ProductInhouse a = inhouse(70001L, PLOT_ID, 90001L, "0.200");
        ProductInhouse b = inhouse(70002L, PLOT_ID, 90002L, "0.200");
        when(inhouseMapper.selectById(70001L)).thenReturn(a);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(vegProduct());
        when(inhouseMapper.selectList(any())).thenReturn(List.of(a, b));
        // 快照说池有 0.400 够打 0.300；真扣时两行都已被别人吃光
        when(inhouseMapper.deductWeightById(anyLong(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.submitVegPack(vegBo("0.300")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已被占用");

        verify(inhouseMapper, never()).deleteById(anyLong());
    }

    // ===================== 来源池按「来源行业态」分岔，不是按目标成品业态 =====================

    @Test
    @DisplayName("来源是猪肉/白条但目标成品填成 other → 仍走肉品池（带 material_id 非空），"
        + "不能因为目标成品业态就把未领用的燎毛白条纳入池")
    void poolFamily_decidedBySourceNotTarget() {
        ProductInhouse burnBar = inhouse(70001L, null, 90001L, "40.000");
        burnBar.setEarNo("QA-PIG-1");
        burnBar.setMaterialId(null);           // 燎毛产整只白条：未领用，material_id 空
        when(inhouseMapper.selectById(70001L)).thenReturn(burnBar);
        ProductInfo srcProduct = vegProduct();
        srcProduct.setId(MATERIAL_ID);
        srcProduct.setBelongType("pork");      // 来源行所属原材料是猪肉
        when(productInfoMapper.selectById(MATERIAL_ID)).thenReturn(srcProduct);
        ProductInfo target = vegProduct();
        target.setBelongType("other");         // 目标成品却是「其他产品」
        target.setProductUnit("kg");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(target);
        when(inhouseMapper.selectList(any())).thenReturn(List.of(burnBar));

        DryPackBo bo = new DryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(PRODUCT_ID);
        bo.setProductWeight(new BigDecimal("1.000"));
        bo.setStoreId(7L);

        service.submitDryPack(bo);

        String sql = capturePoolSql();
        assertThat(sql).contains("material_id IS NOT NULL").contains("ear_no");
    }

    @Test
    @DisplayName("来源业态不是 pork/vegetable/egg/dry_good/other（如白条整只）→ 不放宽，退回单行语义（不查池）")
    void poolFamily_unknownSourceFamilyStaysSingleRow() {
        ProductInhouse bar = inhouse(70001L, null, 90001L, "40.000");
        when(inhouseMapper.selectById(70001L)).thenReturn(bar);
        ProductInfo srcProduct = vegProduct();
        srcProduct.setId(MATERIAL_ID);
        srcProduct.setBelongType("white_bar");
        when(productInfoMapper.selectById(MATERIAL_ID)).thenReturn(srcProduct);
        ProductInfo target = vegProduct();
        target.setBelongType("other");
        target.setProductUnit("kg");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(target);

        DryPackBo bo = new DryPackBo();
        bo.setSourceInhouseId(70001L);
        bo.setProductId(PRODUCT_ID);
        bo.setProductWeight(new BigDecimal("1.000"));
        bo.setStoreId(7L);

        service.submitDryPack(bo);

        // 单行语义 = 压根不查池
        verify(inhouseMapper, never()).selectList(any());
    }

    // ===================== picker 返回集必须覆盖得住扣减池 =====================

    @Test
    @DisplayName("四个来源 picker 的 LIMIT 要够大（500）—— picker 返回集就是前端算「领用剩余重量」的数据源，"
        + "它按 id 降序截断、池按 id 升序 FIFO 吃，截少了就「打包了但剩余重量不降」，row60 换形态复发")
    void sourcePickers_limitCoversPool() {
        ProductInfo material = vegProduct();
        material.setId(MATERIAL_ID);
        material.setProductAttr(2);
        when(productInfoMapper.selectList(any())).thenReturn(List.of(material));
        when(inhouseMapper.selectList(any())).thenReturn(List.of(inhouse(70001L, PLOT_ID, 90001L, "1.000")));

        for (Runnable picker : List.<Runnable>of(
            () -> service.listSourceForVeg(), () -> service.listSourceForDry(),
            () -> service.listSourceForMeat(), () -> service.listSourceForCelery())) {
            Mockito.clearInvocations(inhouseMapper);
            picker.run();
            assertThat(capturePoolSql()).contains("LIMIT 500");
        }
    }

    @Test
    @DisplayName("正常整行用尽：deductWeightById 扣到 0 拿到闸之后才 deleteById 软删（顺序不能反）")
    void fullConsume_deductThenSoftDelete() {
        ProductInhouse picked = inhouse(70001L, PLOT_ID, 90001L, "1.000");
        when(inhouseMapper.selectById(70001L)).thenReturn(picked);
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(vegProduct());
        when(inhouseMapper.selectList(any())).thenReturn(List.of(picked));

        service.submitVegPack(vegBo("1.000"));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(inhouseMapper);
        order.verify(inhouseMapper).deductWeightById(eq(70001L), eq(new BigDecimal("1.000")));
        order.verify(inhouseMapper).deleteById(70001L);
    }
}
