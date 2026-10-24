package org.dromara.djs.store.trace.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.djs.common.store.context.StoreContext;
import org.dromara.djs.common.store.service.IStoreService;
import org.dromara.djs.store.ledger.domain.StoreDailyLedger;
import org.dromara.djs.store.ledger.mapper.StoreDailyLedgerMapper;
import org.dromara.djs.store.trace.domain.vo.StorePackProductVo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.trace.domain.TraceCode;
import org.dromara.djs.warehouse.trace.mapper.TraceCodeMapper;
import org.dromara.djs.warehouse.trace.service.ITraceCodeAdminService;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code StoreTraceServiceImpl#listPackProducts} 单测：产品卡「原材料剩余」在门店当日已有盘点行时，
 * 盘点保存之后才到店的材料外售成品也要计入（row307），且不与盘点值重复相加。
 *
 * <p>夹具取 staging 2026-10-20 形态：徐汇旗舰店同日两次下通排，第一批 1kg 到店后先盘点（入库 1kg），
 * 第二批 2.5kg 盘点之后才到。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreTracePackMaterialStockTest {

    private static final Long STORE_ID = 9315000000000001L;
    private static final Long MATERIAL_TONGPAI = 9303000000000107L;
    private static final Long FINISHED_TONGPAI = 2087538561113473026L;
    private static final Long MATERIAL_WUHUA = 9303000000000101L;

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

    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, TraceCode.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, StoreDailyLedger.class);
    }

    private StoreTraceServiceImpl service() {
        return new StoreTraceServiceImpl(pigQueryService, traceService, traceCodeAdminService, barInfoMapper,
            productInfoMapper, demandManageMapper, productProductionMapper, traceCodeMapper, storeService,
            storeDailyLedgerMapper, redissonClient);
    }

    @Test
    @DisplayName("已盘点 1kg 后又到 2.5kg → 通排剩余 3.5kg；没有原材料的产品剩余 0")
    void ledgeredMaterialAddsArrivalsAfterSave() {
        Date savedAt = new Date(1_792_000_000_000L);
        stubPackProducts();
        StoreDailyLedger ledger = new StoreDailyLedger();
        ledger.setProductId(MATERIAL_TONGPAI);
        ledger.setInboundQty(new BigDecimal("1.000"));
        ledger.setUpdateTime(savedAt);
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of(ledger));
        when(productProductionMapper.sumDeliveredWeightToStoreCheckedAfter(STORE_ID, FINISHED_TONGPAI, today(), savedAt))
            .thenReturn(new BigDecimal("2.500"));

        Map<String, StorePackProductVo> byName = listByName();

        assertThat(byName.get("黑毛猪通排500g/份").getMaterialInboundQty())
            .as("盘点值 1kg + 盘点之后到店的 2.5kg").isEqualByComparingTo("3.500");
        assertThat(byName.get("黑毛猪通排500g/份").getMaterialRemainingQty()).isEqualByComparingTo("3.500");
        assertThat(byName.get("黑毛猪五花肉250g/份").getMaterialRemainingQty()).isEqualByComparingTo("0");
        verify(productProductionMapper, never()).sumDeliveredWeightToStore(any(), any(), any());
    }

    @Test
    @DisplayName("当日还没盘点 → 仍用全天到店重兜底，不走保存时间下界")
    void unledgeredMaterialFallsBackToWholeDayArrival() {
        stubPackProducts();
        when(storeDailyLedgerMapper.selectList(any())).thenReturn(List.of());
        when(productProductionMapper.sumDeliveredWeightToStore(STORE_ID, FINISHED_TONGPAI, today()))
            .thenReturn(new BigDecimal("3.500"));

        Map<String, StorePackProductVo> byName = listByName();

        assertThat(byName.get("黑毛猪通排1000g/份").getMaterialRemainingQty()).isEqualByComparingTo("3.500");
        verify(productProductionMapper, never()).sumDeliveredWeightToStoreCheckedAfter(any(), any(), any(), any());
    }

    /** selectList 依次：① 门店打包间产品；② 材料外售成品。selectByIds = 原材料名称。 */
    private void stubPackProducts() {
        when(productInfoMapper.selectList(any())).thenReturn(
            List.of(pack(1L, "黑毛猪通排500g/份", MATERIAL_TONGPAI), pack(2L, "黑毛猪通排1000g/份", MATERIAL_TONGPAI),
                pack(3L, "黑毛猪五花肉250g/份", MATERIAL_WUHUA)),
            List.of(pack(FINISHED_TONGPAI, "通排", MATERIAL_TONGPAI)));
        when(productInfoMapper.selectByIds(any())).thenReturn(
            List.of(pack(MATERIAL_TONGPAI, "通排", null), pack(MATERIAL_WUHUA, "五花肉", null)));
    }

    private Map<String, StorePackProductVo> listByName() {
        try (MockedStatic<StoreContext> ctx = mockStatic(StoreContext.class)) {
            ctx.when(StoreContext::getStoreId).thenReturn(String.valueOf(STORE_ID));
            return service().listPackProducts().stream()
                .collect(Collectors.toMap(StorePackProductVo::getProductName, Function.identity()));
        }
    }

    private static ProductInfo pack(Long id, String name, Long materialId) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductName(name);
        p.setProductMaterial(materialId);
        return p;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai"));
    }
}
