package org.dromara.djs.warehouse.trace.pub.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.service.OssService;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.PedigreeVo;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.ISowDetailService;
import org.dromara.djs.breed.event.growth.domain.PigGrowth;
import org.dromara.djs.breed.event.growth.mapper.PigGrowthMapper;
import org.dromara.djs.breed.med.domain.Medicine;
import org.dromara.djs.breed.med.mapper.MedicineMapper;
import org.dromara.djs.breed.med.record.domain.MedRecord;
import org.dromara.djs.breed.med.record.mapper.MedRecordMapper;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.farm.domain.FarmRecords;
import org.dromara.djs.plant.farm.mapper.FarmRecordsMapper;
import org.dromara.djs.plant.organic.domain.CropOrganic;
import org.dromara.djs.plant.organic.mapper.CropOrganicMapper;
import org.dromara.djs.plant.organic.mapper.PlotOrganicMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.zone.domain.PlotZone;
import org.dromara.djs.plant.zone.mapper.PlotZoneMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.trace.domain.TraceCode;
import org.dromara.djs.warehouse.trace.domain.TraceEvent;
import org.dromara.djs.warehouse.trace.mapper.TraceCodeMapper;
import org.dromara.djs.warehouse.trace.mapper.TraceEventMapper;
import org.dromara.djs.warehouse.trace.mapper.TraceFarmNameMapper;
import org.dromara.djs.warehouse.trace.pub.domain.vo.PublicTraceVo;
import org.dromara.djs.warehouse.trace.pub.mapper.TraceUserNameMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * {@link TracePublicServiceImpl} happy path 单测（TRC-PUB-API-001）。
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>pork 链路：codeType=pork，填 pig / growthRecords / medications / store；
 *       veg 专属（crop/plot/organicCerts）为 null；timeline 按 trace_time 升序；
 *       operatorId 翻译为姓名。</li>
 *   <li>veg 链路：codeType=veg，填 crop / plot / plotRecords / organicCerts；
 *       pork 专属（pig/growthRecords）为 null。</li>
 *   <li>追溯码不存在：返 null（controller 转友好响应）。</li>
 * </ol>
 *
 * <p>@SaIgnore 端无登录态：service 走 {@link TenantHelper#ignore}（mock 直执行 supplier）
 * + {@link RedisUtils} 缓存（mock 返 null 模拟未命中）。只读，无写方法。</p>
 *
 * @author djs
 * @since TRC-PUB-API-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TracePublicServiceImpl 单元测试")
class TracePublicServiceImplTest {

    @Mock private TraceCodeMapper traceCodeMapper;
    @Mock private TraceEventMapper traceEventMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private StoreMapper storeMapper;
    @Mock private TraceFarmNameMapper traceFarmNameMapper;
    @Mock private TraceUserNameMapper traceUserNameMapper;
    @Mock private OssService ossService;
    @Mock private PigMapper pigMapper;
    @Mock private PigGrowthMapper pigGrowthMapper;
    @Mock private MedRecordMapper medRecordMapper;
    @Mock private MedicineMapper medicineMapper;
    @Mock private ISowDetailService sowDetailService;
    @Mock private PlotInfoMapper plotInfoMapper;
    @Mock private PlotZoneMapper plotZoneMapper;
    @Mock private FarmRecordsMapper farmRecordsMapper;
    @Mock private CropOrganicMapper cropOrganicMapper;
    @Mock private PlotOrganicMapper plotOrganicMapper;
    @Mock private PlantDetailsMapper plantDetailsMapper;
    @Mock private CropInfoMapper cropInfoMapper;

    private TracePublicServiceImpl service;
    private MockedStatic<TenantHelper> tenantHelperMock;

    private static final String PORK_CODE = "T20260604PG000005";
    private static final String VEG_CODE = "T20260604VG000001";
    private static final Long PRODUCT_ID = 100000000000000105L;
    private static final Long PIG_ID = 2059482914806059010L;
    private static final Long PLOT_ID = 6001L;
    private static final Long ZONE_ID = 6101L;
    private static final Long STORE_ID = 5001L;
    private static final Long FARM_ID = 100L;
    private static final Long CROP_CERT_ID = 7001L;
    private static final String EAR_NO = "010126060004";

    /**
     * MyBatis-Plus 单测 entity cache 预热（skill coder-mp-entity-cache-test）：service 内
     * LambdaQueryWrapper method-reference 在 mock 路径下触发 TableInfoHelper.getTableInfo()
     * 解析 lambda 列名，必须先注册所有被 wrapper 引用的 entity（否则 can not find lambda cache）。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, TraceCode.class);
        TableInfoHelper.initTableInfo(assistant, TraceEvent.class);
        TableInfoHelper.initTableInfo(assistant, Pig.class);
        TableInfoHelper.initTableInfo(assistant, PigGrowth.class);
        TableInfoHelper.initTableInfo(assistant, MedRecord.class);
        TableInfoHelper.initTableInfo(assistant, FarmRecords.class);
        TableInfoHelper.initTableInfo(assistant, PlantDetails.class);
    }

    @BeforeEach
    void setup() {
        // spy 真实 service，覆盖 readCache/writeCache（RedisUtils 静态字段在无 Spring 环境 clinit 失败，不可 mockStatic）
        service = spy(new TracePublicServiceImpl(
            traceCodeMapper, traceEventMapper, productInfoMapper, storeMapper,
            traceFarmNameMapper, traceUserNameMapper, ossService,
            pigMapper, pigGrowthMapper, medRecordMapper, medicineMapper, sowDetailService,
            plotInfoMapper, plotZoneMapper, farmRecordsMapper, cropOrganicMapper, plotOrganicMapper,
            plantDetailsMapper, cropInfoMapper));
        doReturn(null).when(service).readCache(anyString());       // 缓存恒未命中 → 每次走聚合
        doNothing().when(service).writeCache(anyString(), any());  // 写缓存 no-op
        // TenantHelper.ignore(Supplier) → 直接执行 supplier
        tenantHelperMock = Mockito.mockStatic(TenantHelper.class);
        tenantHelperMock.when(() -> TenantHelper.ignore(any(Supplier.class)))
            .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
    }

    @AfterEach
    void tearDown() {
        tenantHelperMock.close();
    }

    // ============================ pork 链路 ============================

    @Test
    @DisplayName("pork 码：填 pig/growth/medications/store，veg 专属为 null，timeline 升序，operator 翻译")
    void getByProduceCode_pork_happy() {
        TraceCode code = new TraceCode();
        code.setProduceCode(PORK_CODE);
        code.setCodeType("pork");
        code.setProductId(PRODUCT_ID);
        code.setPigEarNo(EAR_NO);
        code.setStoreId(STORE_ID);
        code.setFarmId(FARM_ID);
        when(traceCodeMapper.selectOne(any(Wrapper.class))).thenReturn(code);

        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductName("猪肉·里脊");
        product.setProductSpec("500g/份");
        product.setProductImg("9001");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product);
        when(ossService.selectUrlByIds("9001")).thenReturn("http://oss/img-9001.jpg");

        // 两条事件，乱序插入，断言返回升序
        TraceEvent e1 = event("ship", LocalDateTime.of(2026, 6, 3, 10, 0), 9101L);
        TraceEvent e2 = event("in_stock", LocalDateTime.of(2026, 6, 1, 8, 0), 9102L);
        when(traceEventMapper.selectList(any(Wrapper.class))).thenReturn(List.of(e2, e1));
        when(traceUserNameMapper.selectUserNames(anyList())).thenReturn(List.of(
            Map.of("userId", 9101L, "nickName", "张三"),
            Map.of("userId", 9102L, "nickName", "李四")));

        Pig pig = new Pig();
        pig.setId(PIG_ID);
        pig.setEarNo(EAR_NO);
        pig.setPigBreedCode("black_pig");
        pig.setBirthDate(LocalDate.of(2025, 12, 1));
        when(pigMapper.selectOne(any(Wrapper.class))).thenReturn(pig);

        PigGrowth g = new PigGrowth();
        g.setMeasureDate(LocalDate.of(2026, 3, 1));
        g.setWeight(new BigDecimal("88.50"));
        g.setBackfatThickness(new BigDecimal("12.0"));
        g.setBarnName("1号栋舍");
        when(pigGrowthMapper.selectList(any(Wrapper.class))).thenReturn(List.of(g));

        MedRecord med = new MedRecord();
        med.setUseDate(LocalDateTime.of(2026, 2, 1, 9, 0));
        med.setMedicineName("猪瘟疫苗");
        med.setMedicineType("vaccine");
        med.setMedicineReason("常规免疫");
        when(medRecordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(med));

        Store store = new Store();
        store.setId(STORE_ID);
        store.setStoreName("东角山旗舰店");
        store.setAddress("上海市XX路1号");
        when(storeMapper.selectById(STORE_ID)).thenReturn(store);

        when(traceFarmNameMapper.selectFarmNames(anyList(), any()))
            .thenReturn(List.of(Map.of("id", FARM_ID, "farmName", "东角山农场")));

        // 谱系：复用 breed queryPedigree，返父母系
        PedigreeVo pedigree = new PedigreeVo();
        pedigree.setSireEarNo("S250001");
        pedigree.setSireBreed("杜洛克");
        pedigree.setSireAgeDays(600);
        pedigree.setDamEarNo("D250002");
        pedigree.setDamBreed("长白");
        pedigree.setDamAgeDays(720);
        pedigree.setDamParity(4);
        when(sowDetailService.queryPedigree(PIG_ID)).thenReturn(pedigree);

        PublicTraceVo vo = service.getByProduceCode(PORK_CODE);

        assertThat(vo).isNotNull();
        assertThat(vo.getCodeType()).isEqualTo("pork");
        // 通用产品块
        assertThat(vo.getProduct().getName()).isEqualTo("猪肉·里脊");
        assertThat(vo.getProduct().getImageUrl()).isEqualTo("http://oss/img-9001.jpg");
        assertThat(vo.getProduct().getProduceCode()).isEqualTo(PORK_CODE);
        // timeline 升序 + 翻译
        assertThat(vo.getTimeline()).hasSize(2);
        assertThat(vo.getTimeline().get(0).getTraceContent()).isEqualTo("in_stock");
        assertThat(vo.getTimeline().get(0).getTraceTime()).isBefore(vo.getTimeline().get(1).getTraceTime());
        assertThat(vo.getTimeline().get(0).getOperatorName()).isEqualTo("李四");
        // pork 专属填充
        assertThat(vo.getPig()).isNotNull();
        assertThat(vo.getPig().getEarNo()).isEqualTo(EAR_NO);
        assertThat(vo.getPig().getBreed()).isEqualTo("black_pig");
        assertThat(vo.getPig().getBarnName()).isEqualTo("1号栋舍");
        assertThat(vo.getPig().getFarmName()).isEqualTo("东角山农场");
        assertThat(vo.getGrowthRecords()).hasSize(1);
        assertThat(vo.getGrowthRecords().get(0).getWeight()).isEqualTo("88.5");
        assertThat(vo.getMedications()).hasSize(1);
        assertThat(vo.getMedications().get(0).getName()).isEqualTo("猪瘟疫苗");
        assertThat(vo.getStore().getName()).isEqualTo("东角山旗舰店");
        // 谱系块（复用 breed queryPedigree）
        assertThat(vo.getPedigree()).isNotNull();
        assertThat(vo.getPedigree().getSireEarNo()).isEqualTo("S250001");
        assertThat(vo.getPedigree().getSireBreed()).isEqualTo("杜洛克");
        assertThat(vo.getPedigree().getSireAgeDays()).isEqualTo(600);
        assertThat(vo.getPedigree().getDamEarNo()).isEqualTo("D250002");
        assertThat(vo.getPedigree().getDamParity()).isEqualTo(4);
        // veg 专属为 null
        assertThat(vo.getCrop()).isNull();
        assertThat(vo.getPlotCropHistory()).isNull();
        assertThat(vo.getPlot()).isNull();
        assertThat(vo.getOrganicCerts()).isNull();
        // 检疫 V1 缺口
        assertThat(vo.getQuarantine()).isNull();
    }

    // ============================ veg 链路 ============================

    @Test
    @DisplayName("veg 码：填 crop/plot/plotRecords/organicCerts，pork 专属为 null")
    void getByProduceCode_veg_happy() {
        TraceCode code = new TraceCode();
        code.setProduceCode(VEG_CODE);
        code.setCodeType("veg");
        code.setProductId(PRODUCT_ID);
        code.setPlotId(PLOT_ID);
        code.setCropCertId(CROP_CERT_ID);
        when(traceCodeMapper.selectOne(any(Wrapper.class))).thenReturn(code);

        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductName("有机番茄");
        product.setProductSpec("1kg/盒");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product);

        TraceEvent e1 = event("in_stock", LocalDateTime.of(2026, 6, 2, 8, 0), 9103L);
        when(traceEventMapper.selectList(any(Wrapper.class))).thenReturn(List.of(e1));
        when(traceUserNameMapper.selectUserNames(anyList())).thenReturn(List.of(
            Map.of("userId", 9103L, "nickName", "王五")));

        PlotInfo plot = new PlotInfo();
        plot.setId(PLOT_ID);
        plot.setPlotName("东区3号地块");
        plot.setPlotArea(new BigDecimal("2.50"));
        plot.setZoneId(ZONE_ID);
        when(plotInfoMapper.selectById(PLOT_ID)).thenReturn(plot);

        PlotZone zone = new PlotZone();
        zone.setId(ZONE_ID);
        zone.setZoneName("东片区");
        when(plotZoneMapper.selectById(ZONE_ID)).thenReturn(zone);

        FarmRecords fr = new FarmRecords();
        fr.setFarmDate(LocalDate.of(2026, 4, 1));
        fr.setFarmType("transplant");
        fr.setRemark("移栽番茄苗");
        when(farmRecordsMapper.selectList(any(Wrapper.class))).thenReturn(List.of(fr));

        CropOrganic cert = new CropOrganic();
        cert.setId(CROP_CERT_ID);
        cert.setCropCertNo("ORG-2026-001");
        cert.setCropCertCompany("中绿华夏有机食品认证中心");
        cert.setCropCertValid(LocalDate.of(2027, 1, 1));
        cert.setCropImagePreview("8001");
        when(cropOrganicMapper.selectById(CROP_CERT_ID)).thenReturn(cert);
        when(ossService.selectUrlByIds("8001")).thenReturn("http://oss/cert-8001.jpg");

        // 该地块历史种植：两条明细（一条有实际采摘日 → 算生长天数 / 一条仅计划采摘）
        PlantDetails d1 = new PlantDetails();
        d1.setId(70001L);
        d1.setPlotId(PLOT_ID);
        d1.setCropId(60001L);
        d1.setBeginActualdate(LocalDate.of(2026, 4, 1));
        d1.setBeginHarvestdate(LocalDate.of(2026, 6, 1));   // 实际采摘 → growthDays=61
        PlantDetails d2 = new PlantDetails();
        d2.setId(70002L);
        d2.setPlotId(PLOT_ID);
        d2.setCropId(60002L);
        d2.setBeginActualdate(LocalDate.of(2025, 9, 1));
        d2.setEarliestHarvestdate(LocalDate.of(2025, 11, 1)); // 无实际采摘 → 用计划
        when(plantDetailsMapper.selectList(any(Wrapper.class))).thenReturn(List.of(d1, d2));

        CropInfo c1 = new CropInfo();
        c1.setId(60001L);
        c1.setCropName("有机番茄");
        c1.setCropImagePreview("8101");
        CropInfo c2 = new CropInfo();
        c2.setId(60002L);
        c2.setCropName("有机黄瓜");
        when(cropInfoMapper.selectByIds(anyList())).thenReturn(List.of(c1, c2));
        when(ossService.selectUrlByIds("8101")).thenReturn("http://oss/crop-8101.jpg");

        PublicTraceVo vo = service.getByProduceCode(VEG_CODE);

        assertThat(vo).isNotNull();
        assertThat(vo.getCodeType()).isEqualTo("veg");
        // veg 专属填充
        assertThat(vo.getCrop()).isNotNull();
        assertThat(vo.getCrop().getName()).isEqualTo("有机番茄");
        assertThat(vo.getPlot().getPlotName()).isEqualTo("东区3号地块");
        assertThat(vo.getPlot().getZoneName()).isEqualTo("东片区");
        assertThat(vo.getPlot().getArea()).isEqualTo("2.5");
        assertThat(vo.getPlotRecords()).hasSize(1);
        assertThat(vo.getPlotRecords().get(0).getWorkType()).isEqualTo("transplant");
        assertThat(vo.getOrganicCerts()).hasSize(1);
        assertThat(vo.getOrganicCerts().get(0).getImageUrl()).isEqualTo("http://oss/cert-8001.jpg");
        assertThat(vo.getOrganicCerts().get(0).getCertNo()).isEqualTo("ORG-2026-001");
        // 该地块历史种植作物（与农事记录并存）
        assertThat(vo.getPlotCropHistory()).hasSize(2);
        assertThat(vo.getPlotCropHistory().get(0).getCropName()).isEqualTo("有机番茄");
        assertThat(vo.getPlotCropHistory().get(0).getGrowthDays()).isEqualTo(61); // 4/1 → 6/1
        assertThat(vo.getPlotCropHistory().get(0).getImageUrl()).isEqualTo("http://oss/crop-8101.jpg");
        assertThat(vo.getPlotCropHistory().get(1).getCropName()).isEqualTo("有机黄瓜");
        assertThat(vo.getPlotCropHistory().get(1).getEndDate()).isEqualTo(LocalDate.of(2025, 11, 1)); // 计划采摘兜底
        assertThat(vo.getPlotCropHistory().get(1).getImageUrl()).isNull();
        // pork 专属为 null
        assertThat(vo.getPig()).isNull();
        assertThat(vo.getGrowthRecords()).isNull();
        assertThat(vo.getPedigree()).isNull();
        assertThat(vo.getStore()).isNull();
    }

    // ============================ 不存在 ============================

    @Test
    @DisplayName("追溯码不存在：返 null（controller 转友好响应）")
    void getByProduceCode_notFound_returnsNull() {
        when(traceCodeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        PublicTraceVo vo = service.getByProduceCode("T-bogus-code");
        assertThat(vo).isNull();
    }

    // ============================ 辅助 ============================

    private TraceEvent event(String content, LocalDateTime time, Long operatorId) {
        TraceEvent e = new TraceEvent();
        e.setProduceCode(PORK_CODE);
        e.setTraceContent(content);
        e.setTraceTime(time);
        e.setOperatorId(operatorId);
        return e;
    }
}
