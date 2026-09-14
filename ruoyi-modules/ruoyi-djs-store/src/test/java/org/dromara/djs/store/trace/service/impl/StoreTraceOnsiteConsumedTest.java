package org.dromara.djs.store.trace.service.impl;

import org.dromara.djs.common.store.service.IStoreService;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.trace.domain.TraceCode;
import org.dromara.djs.warehouse.trace.mapper.TraceCodeMapper;
import org.dromara.djs.warehouse.trace.service.ITraceCodeAdminService;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.dromara.djs.store.ledger.mapper.StoreDailyLedgerMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@code StoreTraceServiceImpl#sumOnsiteConsumedWeightByMaterial} 单测（V6-R215 的取数源）。
 *
 * <p><b>为什么单独建这个文件</b>：这个方法是「门店盘点猪肉原材料行的销售量」唯一来源，
 * 而它最容易做错的地方是 <b>key 的语义</b> —— remark 里的「部位」写的是打包<b>成品名</b>
 * （{@code 黑毛猪通排1000g/份}，{@code product_attr=1}），调用方要的却是<b>原材料</b>（{@code 通排}，
 * {@code product_attr=2}）。第一版实现拿部位名直接当原材料名查，两者零交集，在任何真实数据上恒取 0，
 * 而调用方那侧的单测因为 mock 成了「部位名=原材料名」照样全绿（clean-QA 2026-09-14 揪出）。
 * 所以折叠这一层必须有自己的、按真实数据形态构造的用例。</p>
 *
 * <p>夹具用 staging 实查形态：两个规格的成品（500g / 1000g）共享同一个原材料 {@code 通排}。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreTraceOnsiteConsumedTest {

    private static final Long STORE_ID = 9315000000000001L;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 14);
    /** 原材料「通排」（product_attr=2） */
    private static final Long MATERIAL_TONGPAI = 9303000000000107L;
    /** 原材料「里脊肉」（product_attr=2） */
    private static final Long MATERIAL_LIJI = 9303000000000099L;

    @Mock private org.dromara.djs.breed.core.service.IPigQueryService pigQueryService;
    @Mock private ITraceService traceService;
    @Mock private ITraceCodeAdminService traceCodeAdminService;
    @Mock private org.dromara.djs.warehouse.cross.mapper.BarInfoMapper barInfoMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private DemandManageMapper demandManageMapper;
    @Mock private ProductProductionMapper productProductionMapper;
    @Mock private TraceCodeMapper traceCodeMapper;
    @Mock private IStoreService storeService;
    @Mock private StoreDailyLedgerMapper storeDailyLedgerMapper;
    @Mock private RedissonClient redissonClient;

    /** MP LambdaWrapper 需要 TableInfo 缓存，纯单测环境没跑过 MapperScan，必须手动预热（见 skill coder-mp-entity-cache-test）。 */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, TraceCode.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
    }

    private StoreTraceServiceImpl service() {
        return new StoreTraceServiceImpl(pigQueryService, traceService, traceCodeAdminService, barInfoMapper,
            productInfoMapper, demandManageMapper, productProductionMapper, traceCodeMapper, storeService,
            storeDailyLedgerMapper, redissonClient);
    }

    private TraceCode onsite(String cutLabel, String weightKg) {
        TraceCode c = new TraceCode();
        c.setRemark("现场生码 部位=" + cutLabel + " 重量=" + weightKg + "kg");
        return c;
    }

    private ProductInfo product(Long id, String name, int attr, Long materialId) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductName(name);
        p.setProductAttr(attr);
        p.setProductMaterial(materialId);
        return p;
    }

    @Test
    @DisplayName("部位名是**成品名** → 折叠成原材料 id；两个规格折到同一原材料上累加")
    void foldsFinishedProductNameToMaterialId() {
        when(traceCodeMapper.selectList(any())).thenReturn(List.of(
            onsite("黑毛猪通排1000g/份", "8.000"),
            onsite("黑毛猪通排500g/份", "2.000"),
            onsite("黑毛猪里脊肉500g/份", "1.001")));
        when(productInfoMapper.selectList(any())).thenReturn(List.of(
            product(9303000000000901L, "黑毛猪通排1000g/份", 1, MATERIAL_TONGPAI),
            product(9303000000000902L, "黑毛猪通排500g/份", 1, MATERIAL_TONGPAI),
            product(9303000000000903L, "黑毛猪里脊肉500g/份", 1, MATERIAL_LIJI)));

        Map<Long, BigDecimal> used = service().sumOnsiteConsumedWeightByMaterial(STORE_ID, DAY);

        // 拿部位名当原材料名查的旧写法在这组数据上会返回空 map —— 这就是它在线上恒取 0 的样子
        assertThat(used).containsOnlyKeys(MATERIAL_TONGPAI, MATERIAL_LIJI);
        assertThat(used.get(MATERIAL_TONGPAI)).as("两个规格必须折到同一原材料并累加").isEqualByComparingTo("10.000");
        assertThat(used.get(MATERIAL_LIJI)).isEqualByComparingTo("1.001");
    }

    @Test
    @DisplayName("部位名直接就是原材料名（老数据形态）→ 也能解析，不需要第二套口径")
    void resolvesRawMaterialNameDirectly() {
        when(traceCodeMapper.selectList(any())).thenReturn(List.of(onsite("通排", "3.500")));
        when(productInfoMapper.selectList(any())).thenReturn(List.of(
            product(MATERIAL_TONGPAI, "通排", 2, null)));

        Map<Long, BigDecimal> used = service().sumOnsiteConsumedWeightByMaterial(STORE_ID, DAY);

        assertThat(used).containsEntry(MATERIAL_TONGPAI, new BigDecimal("3.500"));
    }

    @Test
    @DisplayName("成品没配 product_material → 那条消耗不计入（并留 warn），不静默塞进别的原材料")
    void skipsFinishedProductWithoutMaterial() {
        when(traceCodeMapper.selectList(any())).thenReturn(List.of(
            onsite("黑毛猪未配料500g/份", "5.000"),
            onsite("黑毛猪通排500g/份", "2.000")));
        when(productInfoMapper.selectList(any())).thenReturn(List.of(
            product(9303000000000904L, "黑毛猪未配料500g/份", 1, null),
            product(9303000000000902L, "黑毛猪通排500g/份", 1, MATERIAL_TONGPAI)));

        Map<Long, BigDecimal> used = service().sumOnsiteConsumedWeightByMaterial(STORE_ID, DAY);

        assertThat(used).containsOnlyKeys(MATERIAL_TONGPAI);
        assertThat(used.get(MATERIAL_TONGPAI)).isEqualByComparingTo("2.000");
    }

    @Test
    @DisplayName("当日没有现场码 → 空 map，不打产品表")
    void emptyWhenNoOnsiteCode() {
        when(traceCodeMapper.selectList(any())).thenReturn(List.of());

        assertThat(service().sumOnsiteConsumedWeightByMaterial(STORE_ID, DAY)).isEmpty();
    }

    @Test
    @DisplayName("storeId 为空 → 空 map，不跨店统计")
    void emptyWhenNoStore() {
        assertThat(service().sumOnsiteConsumedWeightByMaterial(null, DAY)).isEmpty();
    }
}
