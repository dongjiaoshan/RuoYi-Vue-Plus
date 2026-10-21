package org.dromara.djs.store.trace.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.store.context.StoreContext;
import org.dromara.djs.common.store.service.IStoreService;
import org.dromara.djs.store.ledger.mapper.StoreDailyLedgerMapper;
import org.dromara.djs.store.trace.domain.vo.TraceablePigVo;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code StoreTraceServiceImpl#listTraceablePigs} 单测：白条 chip 的「剩余 = 到货 − 已打包」两侧统计窗口必须一致。
 *
 * <p>夹具取 staging 2026-10-21 实查形态：耳号 {@code 01-01-1-251202-004} 前一天到店 2.5kg、打掉 3kg，
 * 当天又到店 3kg 通排。到货只算本店当天收货，已打包若不收窄到本店当天，就会把前一天的 3kg 扣到今天，
 * chip 误显「已用完」而产品卡还有原材料剩余。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreTraceTraceablePigTest {

    private static final Long STORE_ID = 9315000000000001L;
    private static final Long PRODUCT_TONGPAI = 2087538561113473026L;
    private static final String EAR_NO = "01-01-1-251202-004";

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
        TableInfoHelper.initTableInfo(assistant, DemandManage.class);
        TableInfoHelper.initTableInfo(assistant, ProductProduction.class);
    }

    private StoreTraceServiceImpl service() {
        return new StoreTraceServiceImpl(pigQueryService, traceService, traceCodeAdminService, barInfoMapper,
            productInfoMapper, demandManageMapper, productProductionMapper, traceCodeMapper, storeService,
            storeDailyLedgerMapper, redissonClient);
    }

    @Test
    @DisplayName("已打包只统计本店当天 —— 同耳号前一天打过的重量不扣今天新到的货")
    @SuppressWarnings("unchecked")
    void usedWeightIsScopedToStoreAndToday() {
        DemandManage demand = new DemandManage();
        demand.setId(1L);
        when(demandManageMapper.selectList(any())).thenReturn(List.of(demand));
        ProductInfo tongpai = new ProductInfo();
        tongpai.setId(PRODUCT_TONGPAI);
        when(productInfoMapper.selectList(any())).thenReturn(List.of(tongpai));
        ProductProduction arrivedToday = new ProductProduction();
        arrivedToday.setEarNo(EAR_NO);
        arrivedToday.setWhiteBarNo("BAR2610090006");
        arrivedToday.setProductId(PRODUCT_TONGPAI);
        arrivedToday.setProduceQuantity(new BigDecimal("3.000"));
        when(productProductionMapper.selectList(any())).thenReturn(List.of(arrivedToday));
        // 本店当天还没打过包（前一天那笔 3kg 应被查询条件排除）
        when(traceCodeMapper.selectList(any())).thenReturn(List.of());
        when(pigQueryService.listPigInfoByEarNos(any())).thenReturn(List.of());

        TableDataInfo<TraceablePigVo> page;
        try (MockedStatic<StoreContext> ctx = mockStatic(StoreContext.class)) {
            ctx.when(StoreContext::getStoreId).thenReturn(String.valueOf(STORE_ID));
            page = service().listTraceablePigs(new PageQuery(200, 1));
        }

        assertThat(page.getRows()).hasSize(1);
        assertThat(page.getRows().get(0).getRemainingWeight()).isEqualByComparingTo("3.000");

        ArgumentCaptor<Wrapper<TraceCode>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(traceCodeMapper).selectList(captor.capture());
        LambdaQueryWrapper<TraceCode> w = (LambdaQueryWrapper<TraceCode>) captor.getValue();
        String sql = w.getSqlSegment();
        assertThat(sql).as("必须按门店收窄").contains("store_id =");
        assertThat(sql).as("必须按当天收窄").contains("create_time >=").contains("create_time <");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        assertThat(w.getParamNameValuePairs().values())
            .contains(STORE_ID, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }
}
