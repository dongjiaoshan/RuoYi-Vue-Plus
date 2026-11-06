package org.dromara.djs.warehouse.trace.pub.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.service.OssService;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.trace.domain.TraceCode;
import org.dromara.djs.warehouse.trace.mapper.TraceCodeMapper;
import org.dromara.djs.warehouse.trace.pub.domain.vo.PublicTraceVo;
import org.dromara.djs.warehouse.trace.pub.service.ITracePublicService;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreTracePublicServiceImpl} 单测（V7 row40 门店猪只追溯码）。
 *
 * <p>覆盖选猪规则：一头白条 / 多头白条取最重（半只合计）/ 同重确定性 / 无白条取最重猪肉产品 /
 * 什么都没有 / 不串他店 / 缺码回落；以及门店入口的父母系「缺一方整块隐藏」与门店块恒为本店。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreTracePublicServiceImpl 单元测试")
class StoreTracePublicServiceImplTest {

    private static final Long STORE_ID = 9311051000000001L;
    private static final String STORE_CODE = "ST9311";
    private static final Long WHITE_BAR_PRODUCT = 9311051000000101L;
    private static final Long PORK_PRODUCT = 9311051000000102L;
    private static final Long VEG_PRODUCT = 9311051000000103L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Mock private StoreMapper storeMapper;
    @Mock private DemandManageMapper demandManageMapper;
    @Mock private ProductProductionMapper productProductionMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private TraceCodeMapper traceCodeMapper;
    @Mock private ITracePublicService tracePublicService;
    @Mock private OssService ossService;

    private StoreTracePublicServiceImpl service;
    private MockedStatic<TenantHelper> tenantHelperMock;
    private final Map<String, String> cache = new java.util.HashMap<>();

    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, Store.class);
        TableInfoHelper.initTableInfo(assistant, DemandManage.class);
        TableInfoHelper.initTableInfo(assistant, ProductProduction.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, TraceCode.class);
    }

    @BeforeEach
    void setup() {
        service = spy(new StoreTracePublicServiceImpl(storeMapper, demandManageMapper, productProductionMapper,
            productInfoMapper, traceCodeMapper, tracePublicService, ossService));
        doReturn(TODAY).when(service).today();
        cache.clear();
        doAnswer(inv -> cache.get(inv.<String>getArgument(0))).when(service).readCache(anyString());
        doAnswer(inv -> cache.put(inv.getArgument(0), inv.getArgument(1))).when(service).writeCache(anyString(), anyString());
        tenantHelperMock = Mockito.mockStatic(TenantHelper.class);
        tenantHelperMock.when(() -> TenantHelper.ignore(any(Supplier.class)))
            .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());

        Store store = new Store();
        store.setId(STORE_ID);
        store.setStoreCode(STORE_CODE);
        store.setStoreName("东角山测试门店");
        store.setAddress("测试路 1 号");
        store.setManagerPhone("13800000000");
        when(storeMapper.selectOne(any(Wrapper.class))).thenReturn(store);

        DemandManage d = new DemandManage();
        d.setId(9311051000000201L);
        when(demandManageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(d));

        when(productInfoMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            product(WHITE_BAR_PRODUCT, "white_bar"), product(PORK_PRODUCT, "pork"), product(VEG_PRODUCT, "vegetable")));
        when(tracePublicService.getByProduceCode(anyString())).thenAnswer(inv -> porkVo(inv.getArgument(0), true, true));
    }

    @AfterEach
    void tearDown() {
        tenantHelperMock.close();
    }

    // ============================ 选猪规则 ============================

    @Test
    @DisplayName("只有一头白条：取这头白条的耳号")
    void singleWhiteBar() {
        givenRows(row(1L, WHITE_BAR_PRODUCT, "EAR-A", "45.000", "PG-A"));

        PublicTraceVo vo = service.getByStoreKey(STORE_CODE);

        assertThat(vo.getProduct().getProduceCode()).isEqualTo("PG-A");
        assertThat(vo.getPig().getEarNo()).isEqualTo("EAR-OF-PG-A");
    }

    @Test
    @DisplayName("多头白条：按一头猪（两个半只合计）比重量，取最重那头；猪肉产品再重也不看")
    void heaviestWhiteBarWins() {
        givenRows(
            row(1L, WHITE_BAR_PRODUCT, "EAR-A", "60.000", "PG-A1"),
            // B 两个半只 40 + 35 = 75 > A 的 60
            row(2L, WHITE_BAR_PRODUCT, "EAR-B", "40.000", "PG-B1"),
            row(3L, WHITE_BAR_PRODUCT, "EAR-B", "35.000", "PG-B2"),
            // 有白条时猪肉产品不参与比较
            row(4L, PORK_PRODUCT, "EAR-C", "99.000", "PG-C"));

        PublicTraceVo vo = service.getByStoreKey(STORE_CODE);

        // B 的码取这头猪里最重那个半只的码
        assertThat(vo.getProduct().getProduceCode()).isEqualTo("PG-B1");
    }

    @Test
    @DisplayName("同重：按耳号字典序取小者，结果确定（与入参顺序无关）")
    void tieBreakIsDeterministic() {
        givenRows(
            row(1L, WHITE_BAR_PRODUCT, "EAR-Z", "50.000", "PG-Z"),
            row(2L, WHITE_BAR_PRODUCT, "EAR-M", "50.000", "PG-M"));
        assertThat(service.getByStoreKey(STORE_CODE).getProduct().getProduceCode()).isEqualTo("PG-M");

        cache.clear();
        givenRows(
            row(2L, WHITE_BAR_PRODUCT, "EAR-M", "50.000", "PG-M"),
            row(1L, WHITE_BAR_PRODUCT, "EAR-Z", "50.000", "PG-Z"));
        assertThat(service.getByStoreKey(STORE_CODE).getProduct().getProduceCode()).isEqualTo("PG-M");
    }

    @Test
    @DisplayName("没有白条只有猪肉产品：取单件最重的猪肉产品的耳号（果蔬等其他业态不算）")
    void noWhiteBarUsesHeaviestPorkProduct() {
        givenRows(
            row(1L, PORK_PRODUCT, "EAR-A", "1.200", "PG-A1"),
            row(2L, PORK_PRODUCT, "EAR-A", "1.300", "PG-A2"),
            row(3L, PORK_PRODUCT, "EAR-B", "2.000", "PG-B"),
            row(4L, VEG_PRODUCT, "EAR-V", "30.000", "VG-1"));

        // 单件比：B 的 2.0kg > A 任一件（A 合计 2.5 也不按合计算）
        assertThat(service.getByStoreKey(STORE_CODE).getProduct().getProduceCode()).isEqualTo("PG-B");
    }

    @Test
    @DisplayName("白条·猪头 / 猪蹄虽属 white_bar，但按猪肉产品比重量：6kg 猪头压不过 99kg 猪肉产品，半扇仍优先")
    void headAndTrotterAreComparedAsPork() {
        Long headProduct = 9311051000000104L;
        ProductInfo head = product(headProduct, "white_bar");
        head.setProductName("白条·猪头");
        ProductInfo halfBar = product(WHITE_BAR_PRODUCT, "white_bar");
        halfBar.setProductName("左半扇");
        when(productInfoMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            halfBar, head, product(PORK_PRODUCT, "pork")));

        // 只有猪头 + 更重的猪肉产品：猪头不算白条，按重量比 → 猪肉产品 99kg 胜出
        givenRows(
            row(1L, headProduct, "EAR-H", "6.000", "PG-H"),
            row(2L, PORK_PRODUCT, "EAR-P", "99.000", "PG-P"));
        assertThat(service.getByStoreKey(STORE_CODE).getProduct().getProduceCode()).isEqualTo("PG-P");

        // 有真正的半扇：半扇（白条）优先，猪头、猪肉产品都不参与
        cache.clear();
        givenRows(
            row(1L, headProduct, "EAR-H", "6.000", "PG-H"),
            row(2L, PORK_PRODUCT, "EAR-P", "99.000", "PG-P"),
            row(3L, WHITE_BAR_PRODUCT, "EAR-B", "40.000", "PG-B"));
        assertThat(service.getByStoreKey(STORE_CODE).getProduct().getProduceCode()).isEqualTo("PG-B");
    }

    @Test
    @DisplayName("当天什么都没有：返 pig=null 的空态，门店块照常带上，且结果被缓存")
    void nothingToday() {
        when(demandManageMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        PublicTraceVo vo = service.getByStoreKey(STORE_CODE);

        assertThat(vo).isNotNull();
        assertThat(vo.getCodeType()).isEqualTo("pork");
        assertThat(vo.getPig()).isNull();
        assertThat(vo.getPedigree()).isNull();
        assertThat(vo.getTimeline()).isEmpty();
        assertThat(vo.getStore().getName()).isEqualTo("东角山测试门店");
        assertThat(cache).containsEntry("trace:public-store:" + STORE_ID + ":20261105", StoreTracePublicServiceImpl.NONE);
        verify(tracePublicService, never()).getByProduceCode(anyString());
    }

    @Test
    @DisplayName("门店不存在：返 null（H5 显示未查到门店）")
    void unknownStore() {
        when(storeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThat(service.getByStoreKey("NOPE")).isNull();
        assertThat(service.getByStoreKey(" ")).isNull();
    }

    @Test
    @DisplayName("不串他店：需求单只按本店 + 今天确认收货筛，产出行只按这些需求单取")
    @SuppressWarnings("unchecked")
    void scopedToThisStoreAndToday() {
        givenRows(row(1L, WHITE_BAR_PRODUCT, "EAR-A", "45.000", "PG-A"));

        service.getByStoreKey(STORE_CODE);

        ArgumentCaptor<LambdaQueryWrapper<DemandManage>> dq = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(demandManageMapper).selectList(dq.capture());
        String demandSql = dq.getValue().getTargetSql();
        assertThat(demandSql).contains("store_id =").contains("received_time >=").contains("received_time <");
        assertThat(dq.getValue().getParamNameValuePairs().values())
            .contains(STORE_ID, TODAY.atStartOfDay(), TODAY.plusDays(1).atStartOfDay());

        ArgumentCaptor<LambdaQueryWrapper<ProductProduction>> pq = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productProductionMapper).selectList(pq.capture());
        String prodSql = pq.getValue().getTargetSql();
        assertThat(prodSql).contains("demand_id IN").contains("is_delivery_check =").contains("is_damaged =");
        assertThat(pq.getValue().getParamNameValuePairs().values()).contains(9311051000000201L);
    }

    @Test
    @DisplayName("当天货品缺追溯码：回落该耳号最新猪肉码；白条都取不到码时顺延到猪肉产品")
    void missingCodeFallbacks() {
        givenRows(row(1L, WHITE_BAR_PRODUCT, "EAR-A", "70.000", null));
        TraceCode latest = new TraceCode();
        latest.setProduceCode("PG-A-LATEST");
        when(traceCodeMapper.selectOne(any(Wrapper.class))).thenReturn(latest);
        assertThat(service.getByStoreKey(STORE_CODE).getProduct().getProduceCode()).isEqualTo("PG-A-LATEST");

        cache.clear();
        when(traceCodeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        givenRows(
            row(1L, WHITE_BAR_PRODUCT, "EAR-A", "70.000", null),
            row(2L, PORK_PRODUCT, "EAR-B", "1.000", "PG-B"));
        assertThat(service.getByStoreKey(STORE_CODE).getProduct().getProduceCode()).isEqualTo("PG-B");
    }

    @Test
    @DisplayName("缓存命中：直接用缓存里的追溯码，不再查库选猪")
    void cacheHitSkipsSelection() {
        cache.put("trace:public-store:" + STORE_ID + ":20261105", "PG-CACHED");

        PublicTraceVo vo = service.getByStoreKey(STORE_CODE);

        assertThat(vo.getProduct().getProduceCode()).isEqualTo("PG-CACHED");
        verify(demandManageMapper, never()).selectList(any(Wrapper.class));
        verify(tracePublicService).getByProduceCode(eq("PG-CACHED"));
    }

    // ============================ 父母系 / 门店块 ============================

    @Test
    @DisplayName("父系母系都可追踪：照常显示")
    void pedigreeShownWhenBothKnown() {
        givenRows(row(1L, WHITE_BAR_PRODUCT, "EAR-A", "45.000", "PG-A"));
        PublicTraceVo.PedigreeBlock p = service.getByStoreKey(STORE_CODE).getPedigree();
        assertThat(p).isNotNull();
        assertThat(p.getSireEarNo()).isEqualTo("SIRE-1");
        assertThat(p.getDamEarNo()).isEqualTo("DAM-1");
    }

    @Test
    @DisplayName("父系或母系任一方无法追踪：整个父母系块置 null")
    void pedigreeHiddenWhenOneSideMissing() {
        givenRows(row(1L, WHITE_BAR_PRODUCT, "EAR-A", "45.000", "PG-A"));
        when(tracePublicService.getByProduceCode(anyString())).thenAnswer(inv -> porkVo(inv.getArgument(0), true, false));
        assertThat(service.getByStoreKey(STORE_CODE).getPedigree()).isNull();

        cache.clear();
        when(tracePublicService.getByProduceCode(anyString())).thenAnswer(inv -> porkVo(inv.getArgument(0), false, true));
        assertThat(service.getByStoreKey(STORE_CODE).getPedigree()).isNull();
    }

    @Test
    @DisplayName("门店块恒为二维码所属门店；产品块只留追溯码")
    void storeBlockIsThisStore() {
        givenRows(row(1L, WHITE_BAR_PRODUCT, "EAR-A", "45.000", "PG-A"));
        PublicTraceVo vo = service.getByStoreKey(STORE_CODE);
        assertThat(vo.getStore().getName()).isEqualTo("东角山测试门店");
        assertThat(vo.getStore().getManagerPhone()).isEqualTo("13800000000");
        assertThat(vo.getProduct().getName()).isNull();
        assertThat(vo.getProduct().getWeight()).isNull();
    }

    // ============================ helpers ============================

    private void givenRows(ProductProduction... rows) {
        when(productProductionMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>(List.of(rows)));
    }

    private static ProductProduction row(Long id, Long productId, String earNo, String weight, String code) {
        ProductProduction p = new ProductProduction();
        p.setId(id);
        p.setProductId(productId);
        p.setEarNo(earNo);
        p.setProductWeight(new BigDecimal(weight));
        p.setTraceCode(code);
        return p;
    }

    private static ProductInfo product(Long id, String belongType) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setBelongType(belongType);
        return p;
    }

    /** 模拟按码追溯返回的猪肉 VO（门店块故意给成别的门店，验证会被覆盖）。 */
    private static PublicTraceVo porkVo(String code, boolean hasSire, boolean hasDam) {
        PublicTraceVo vo = new PublicTraceVo();
        vo.setCodeType("pork");
        PublicTraceVo.ProductBlock product = new PublicTraceVo.ProductBlock();
        product.setProduceCode(code);
        product.setName("五花肉");
        product.setWeight("500g");
        vo.setProduct(product);
        PublicTraceVo.PigBlock pig = new PublicTraceVo.PigBlock();
        pig.setEarNo("EAR-OF-" + code);
        vo.setPig(pig);
        PublicTraceVo.PedigreeBlock ped = new PublicTraceVo.PedigreeBlock();
        ped.setSireEarNo(hasSire ? "SIRE-1" : null);
        ped.setDamEarNo(hasDam ? "DAM-1" : null);
        vo.setPedigree(ped);
        PublicTraceVo.StoreBlock other = new PublicTraceVo.StoreBlock();
        other.setName("别的门店");
        vo.setStore(other);
        vo.setTimeline(new ArrayList<>());
        return vo;
    }
}
