package org.dromara.djs.warehouse.burn.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.warehouse.burn.domain.bo.BurnInhouseAdjustBo;
import org.dromara.djs.warehouse.burn.mapper.BurnInhouseAdjustMapper;
import org.dromara.djs.warehouse.burn.domain.PigBurnRecord;
import org.dromara.djs.warehouse.burn.mapper.PigBurnRecordMapper;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.stat.service.IWarehouseStatService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link BurnInhouseAdjustServiceImpl} 单测（V6-R43 燎毛间产品重量调整）。
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>happy path：一次调整把「产出行 / 白条库存 / 燎毛入库流水 / 燎毛记录 / 白条」五处全部推到正确的值，
 *       且区分「覆盖」与「按差额」两种口径</li>
 *   <li>已点「处理完成」的猪只拒绝调整（后端拦，不靠前端隐藏按钮），且任何下游写入都不发生</li>
 *   <li>并发：写完前四张表后白条窗口锁失效（有人抢先点了处理完成）→ 抛异常触发整体回滚</li>
 *   <li>上限：调整后本行重量 + 其它产出行 &gt; 猪只接收重量 → 拒绝</li>
 *   <li>重量没变 → 幂等 no-op，不打「已调整」标记、不动任何台账</li>
 *   <li>非燎毛间入库行（领用 WIP / 门店拆单）→ 拒绝</li>
 * </ol>
 *
 * @author djs
 * @since V6-R43
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BurnInhouseAdjustServiceImpl 单元测试")
class BurnInhouseAdjustServiceImplTest {

    @Mock
    private BurnInhouseAdjustMapper adjustMapper;
    @Mock
    private ProductInhouseMapper productInhouseMapper;
    @Mock
    private BarInfoMapper barInfoMapper;
    @Mock
    private LocationStockMapper locationStockMapper;
    @Mock
    private StockFlowMapper stockFlowMapper;
    @Mock
    private PigBurnRecordMapper pigBurnRecordMapper;
    @Mock
    private IStockCheckService stockCheckService;
    @Mock
    private IWarehouseStatService warehouseStatService;

    private BurnInhouseAdjustServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    private static final Long INHOUSE_ID = 70001L;
    private static final Long BAR_ID = 5001L;
    private static final Long PRODUCT_ID = 100000000000000001L;
    private static final Long LOCATION_ID = 90001L;
    private static final Long BURN_RECORD_ID = 60001L;
    private static final Long USER_ID = 9001L;
    private static final String WHITE_BAR_NO = "BAR26060400012";

    /** 旧入库重量 80.300kg，新入库重量 78.500kg → delta = −1.800kg。 */
    private static final BigDecimal OLD_WEIGHT = new BigDecimal("80.300");
    private static final BigDecimal NEW_WEIGHT = new BigDecimal("78.500");
    private static final BigDecimal DELTA = new BigDecimal("-1.800");

    @BeforeAll
    static void initMpEntityCache() {
        // MyBatis-Plus 单测 entity cache 预热：上限校验用 LambdaQueryWrapper<ProductInhouse>，
        // 无 Spring 上下文时需先注册 TableInfo 才能解析 lambda 列名。
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, ProductInhouse.class);
    }

    @BeforeEach
    void setup() {
        service = new BurnInhouseAdjustServiceImpl(adjustMapper, productInhouseMapper, barInfoMapper,
            locationStockMapper, stockFlowMapper, pigBurnRecordMapper, stockCheckService, warehouseStatService);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    /** 燎毛间入库产出行：white_bar_id 非空 + material_id 为空 + source=warehouse。 */
    private ProductInhouse burnRow() {
        ProductInhouse row = new ProductInhouse();
        row.setId(INHOUSE_ID);
        row.setProductId(PRODUCT_ID);
        row.setProductName("半扇");
        row.setProductWeight(OLD_WEIGHT);
        row.setEarNo("010126050101");
        row.setWhiteBarId(BAR_ID);
        row.setWhiteBarNo(WHITE_BAR_NO);
        row.setBurnRecordId(BURN_RECORD_ID);
        row.setLocationId(LOCATION_ID);
        row.setProduceTime(new Date());
        row.setSource("warehouse");
        row.setIsAdjusted(0);
        return row;
    }

    private BarInfo bar(String status, BigDecimal arriveWeight) {
        BarInfo b = new BarInfo();
        b.setId(BAR_ID);
        b.setBarId("BAR2606030001");
        b.setEarNo("010126050101");
        b.setStatus(status);
        b.setArriveWeight(arriveWeight);
        return b;
    }

    /** 同白条另一条产出行（另半扇 30.000kg），用于上限校验。 */
    @SuppressWarnings("unchecked")
    private void stubOtherRows(String otherWeight) {
        List<ProductInhouse> others = new ArrayList<>();
        ProductInhouse other = new ProductInhouse();
        other.setId(INHOUSE_ID + 1);
        other.setProductWeight(new BigDecimal(otherWeight));
        others.add(other);
        when(productInhouseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(others);
    }

    private BurnInhouseAdjustBo bo(BigDecimal weight) {
        BurnInhouseAdjustBo b = new BurnInhouseAdjustBo();
        b.setId(INHOUSE_ID);
        b.setProductWeight(weight);
        return b;
    }

    /** happy path 的全套 stub：五个下游写入全部返回 1（成功）。 */
    private void stubAllWritesOk() {
        when(adjustMapper.applyAdjust(eq(INHOUSE_ID), any(), any(), eq(USER_ID))).thenReturn(1);
        when(locationStockMapper.adjustStockByWhiteBarNo(eq(WHITE_BAR_NO), eq(PRODUCT_ID), any(), eq(USER_ID)))
            .thenReturn(1);
        when(stockFlowMapper.updateBurnInFlowWeight(eq(WHITE_BAR_NO), eq(PRODUCT_ID), any(), eq(USER_ID)))
            .thenReturn(1);
        when(pigBurnRecordMapper.adjustBurnWeight(eq(BURN_RECORD_ID), any(), eq(USER_ID))).thenReturn(1);
        when(barInfoMapper.adjustInWeightIfBurning(eq(BAR_ID), any(), eq(USER_ID))).thenReturn(1);
    }

    @Test
    @DisplayName("adjustWeight: happy → 产出行/入库流水按新值覆盖，白条库存/燎毛记录/白条按差额同步")
    void testAdjust_Happy_AllDownstreamValues() {
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(burnRow());
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(bar("singing", new BigDecimal("110.500")));
        stubOtherRows("30.000");
        stubAllWritesOk();

        service.adjustWeight(bo(NEW_WEIGHT));

        // 1) 产出行：覆盖为新重量（带旧值乐观锁）+ 打调整留痕
        verify(adjustMapper).applyAdjust(INHOUSE_ID, OLD_WEIGHT, NEW_WEIGHT, USER_ID);
        // 2) 白条库存行：按差额 −1.800（不是覆盖成 78.500）
        verify(locationStockMapper).adjustStockByWhiteBarNo(WHITE_BAR_NO, PRODUCT_ID, DELTA, USER_ID);
        // 3) 燎毛入库流水：覆盖为新重量（这条流水就是这次入库事件本身）
        verify(stockFlowMapper).updateBurnInFlowWeight(WHITE_BAR_NO, PRODUCT_ID, NEW_WEIGHT, USER_ID);
        // 4) 燎毛记录合计：按差额（覆盖会抹掉同批其它产品）
        verify(pigBurnRecordMapper).adjustBurnWeight(BURN_RECORD_ID, DELTA, USER_ID);
        // 5) 白条：窗口乐观锁 + in_weight 差额
        verify(barInfoMapper).adjustInWeightIfBurning(BAR_ID, DELTA, USER_ID);
        // 库位盘点锁同样要过（调整会动库存）
        verify(stockCheckService).assertLocationUnlocked(LOCATION_ID);
    }

    @Test
    @DisplayName("adjustWeight: 燎毛日在今天之前 → 重算那一天的统计快照（白条总重/出成率是跑批落盘的，不重算会永久偏差）")
    void testAdjust_RecalcsStatSnapshotForPastBurnDate() {
        LocalDate burnDate = LocalDate.now().minusDays(1);
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(burnRow());
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(bar("singing", new BigDecimal("110.500")));
        when(pigBurnRecordMapper.selectById(BURN_RECORD_ID)).thenReturn(burnRecordOn(burnDate));
        stubOtherRows("30.000");
        stubAllWritesOk();

        service.adjustWeight(bo(NEW_WEIGHT));

        verify(warehouseStatService).aggregate(burnDate);
    }

    @Test
    @DisplayName("adjustWeight: 燎毛日就是今天 → 不重算（今晚跑批本来就会算这一天，白跑一次没意义）")
    void testAdjust_SkipsStatRecalcForTodayBurnDate() {
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(burnRow());
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(bar("singing", new BigDecimal("110.500")));
        when(pigBurnRecordMapper.selectById(BURN_RECORD_ID)).thenReturn(burnRecordOn(LocalDate.now()));
        stubOtherRows("30.000");
        stubAllWritesOk();

        service.adjustWeight(bo(NEW_WEIGHT));

        verify(warehouseStatService, never()).aggregate(any());
    }

    /** 指定燎毛业务日的燎毛记录（统计跑批按 DATE(burn_time) 分桶）。 */
    private static PigBurnRecord burnRecordOn(LocalDate day) {
        PigBurnRecord record = new PigBurnRecord();
        record.setId(BURN_RECORD_ID);
        record.setBurnTime(Date.from(day.atTime(10, 30).atZone(ZoneId.systemDefault()).toInstant()));
        return record;
    }

    @Test
    @DisplayName("adjustWeight: 已点「处理完成」(bar=in_stock) → 拒绝调整，任何下游写入都不发生")
    void testAdjust_RejectWhenBurnFinished() {
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(burnRow());
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(bar("in_stock", new BigDecimal("110.500")));

        assertThatThrownBy(() -> service.adjustWeight(bo(NEW_WEIGHT)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已处理完成");

        verifyNoInteractions(adjustMapper);
        verifyNoInteractions(locationStockMapper);
        verifyNoInteractions(stockFlowMapper);
        verify(pigBurnRecordMapper, never()).adjustBurnWeight(any(), any(), any());
        verify(barInfoMapper, never()).adjustInWeightIfBurning(any(), any(), any());
    }

    @Test
    @DisplayName("adjustWeight: 白条已被并发推进(窗口锁 affected=0) → 抛异常触发整体回滚")
    void testAdjust_RejectWhenBarLockLost() {
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(burnRow());
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(bar("singing", new BigDecimal("110.500")));
        stubOtherRows("30.000");
        stubAllWritesOk();
        // 前四张表都写成功，最后一步白条窗口锁没拿到（有人抢先点了「处理完成」）
        when(barInfoMapper.adjustInWeightIfBurning(eq(BAR_ID), any(), eq(USER_ID))).thenReturn(0);

        assertThatThrownBy(() -> service.adjustWeight(bo(NEW_WEIGHT)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已处理完成");
    }

    @Test
    @DisplayName("adjustWeight: 本行新重量 + 其它产出行 > 猪只接收重量 → 拒绝")
    void testAdjust_RejectOverArriveWeight() {
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(burnRow());
        // 接收重量 110.500，其它产出行已占 30.000 → 本行上限 80.500
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(bar("singing", new BigDecimal("110.500")));
        stubOtherRows("30.000");

        assertThatThrownBy(() -> service.adjustWeight(bo(new BigDecimal("80.501"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("80.5");

        verifyNoInteractions(adjustMapper);
        verifyNoInteractions(locationStockMapper);
    }

    @Test
    @DisplayName("adjustWeight: 重量没变 → 幂等 no-op，不打「已调整」标记也不动任何台账")
    void testAdjust_NoOpWhenUnchanged() {
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(burnRow());
        // 窗口校验排在「重量没变就早退」之前，所以可调整态的白条仍要能查到
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(bar("singing", new BigDecimal("110.500")));

        service.adjustWeight(bo(new BigDecimal("80.3000")));

        verifyNoInteractions(adjustMapper);
        verifyNoInteractions(locationStockMapper);
        verifyNoInteractions(stockFlowMapper);
        verify(barInfoMapper, never()).adjustInWeightIfBurning(any(), any(), any());
        verify(warehouseStatService, never()).aggregate(any());
    }

    @Test
    @DisplayName("adjustWeight: 已处理完成的行提交「同一个重量」→ 仍然拒绝（窗口校验在 no-op 早退之前，不能把该拒的请求答成 ok）")
    void testAdjust_RejectUnchangedWeightWhenBurnFinished() {
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(burnRow());
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(bar("in_stock", new BigDecimal("110.500")));

        assertThatThrownBy(() -> service.adjustWeight(bo(new BigDecimal("80.3000"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已处理完成");
        verifyNoInteractions(adjustMapper);
    }

    @Test
    @DisplayName("adjustWeight: 非燎毛间入库行（领用 WIP，material_id 非空）→ 拒绝")
    void testAdjust_RejectNonBurnRow() {
        ProductInhouse wip = burnRow();
        wip.setMaterialId(PRODUCT_ID);
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(wip);

        assertThatThrownBy(() -> service.adjustWeight(bo(NEW_WEIGHT)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不是燎毛间入库记录");
    }

    @Test
    @DisplayName("adjustWeight: 缺白条流水号（无法联动库存/流水）→ 拒绝，不做半截调整")
    void testAdjust_RejectMissingWhiteBarNo() {
        ProductInhouse row = burnRow();
        row.setWhiteBarNo(null);
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(row);

        assertThatThrownBy(() -> service.adjustWeight(bo(NEW_WEIGHT)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("白条流水号");

        verifyNoInteractions(adjustMapper);
    }

    @Test
    @DisplayName("adjustWeight: 历史行 burn_record_id 为空 → 按(耳号,燎毛时间)兜底反查燎毛记录")
    void testAdjust_FallbackResolveBurnRecord() {
        ProductInhouse legacy = burnRow();
        legacy.setBurnRecordId(null);
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(legacy);
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(bar("singing", new BigDecimal("110.500")));
        stubOtherRows("30.000");
        stubAllWritesOk();
        when(pigBurnRecordMapper.selectIdByEarNoAndBurnTime(eq("010126050101"), any(Date.class)))
            .thenReturn(BURN_RECORD_ID);

        service.adjustWeight(bo(NEW_WEIGHT));

        verify(pigBurnRecordMapper).adjustBurnWeight(BURN_RECORD_ID, DELTA, USER_ID);
    }

    @Test
    @DisplayName("adjustWeight: 未称重(接收重量为空)时跳过上限校验，仍可调整")
    void testAdjust_SkipCapWhenNotWeighed() {
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(burnRow());
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(bar("pending_singe", null));
        stubAllWritesOk();

        service.adjustWeight(bo(NEW_WEIGHT));

        verify(adjustMapper).applyAdjust(INHOUSE_ID, OLD_WEIGHT, NEW_WEIGHT, USER_ID);
        // 未称重时不需要读其它产出行做上限校验
        verify(productInhouseMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("adjustWeight: 白条库存行不存在 / 调整后为负 → 抛异常（不静默跳过）")
    void testAdjust_RejectWhenStockRowMissing() {
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(burnRow());
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(bar("singing", new BigDecimal("110.500")));
        stubOtherRows("30.000");
        stubAllWritesOk();
        when(locationStockMapper.adjustStockByWhiteBarNo(eq(WHITE_BAR_NO), eq(PRODUCT_ID), any(), eq(USER_ID)))
            .thenReturn(0);

        assertThatThrownBy(() -> service.adjustWeight(bo(NEW_WEIGHT)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("白条库存行");

        // 后续台账不再被推进（事务回滚前就中断）
        verify(stockFlowMapper, never()).updateBurnInFlowWeight(any(), any(), any(), any());
        verify(barInfoMapper, never()).adjustInWeightIfBurning(any(), any(), any());
    }

    @Test
    @DisplayName("adjustWeight: 调大重量 → 差额为正，库存/燎毛记录同向加")
    void testAdjust_IncreaseWeight() {
        when(productInhouseMapper.selectById(INHOUSE_ID)).thenReturn(burnRow());
        when(barInfoMapper.selectById(BAR_ID)).thenReturn(bar("singing", new BigDecimal("110.500")));
        stubOtherRows("20.000");
        stubAllWritesOk();

        service.adjustWeight(bo(new BigDecimal("85.000")));

        BigDecimal expectedDelta = new BigDecimal("85.000").subtract(OLD_WEIGHT);
        assertThat(expectedDelta.signum()).isPositive();
        verify(locationStockMapper).adjustStockByWhiteBarNo(WHITE_BAR_NO, PRODUCT_ID, expectedDelta, USER_ID);
        verify(pigBurnRecordMapper).adjustBurnWeight(BURN_RECORD_ID, expectedDelta, USER_ID);
        verify(stockFlowMapper).updateBurnInFlowWeight(WHITE_BAR_NO, PRODUCT_ID, new BigDecimal("85.000"), USER_ID);
    }

}
