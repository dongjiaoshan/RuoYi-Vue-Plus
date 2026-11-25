package org.dromara.djs.warehouse.stat.mapper;

import org.apache.ibatis.mapping.Environment;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.job.DjsJobRegistry;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.warehouse.boardstat.mapper.WarehouseBoardStatMapper;
import org.dromara.djs.warehouse.dashboard.domain.vo.WarehousePorkEfficiencyVo;
import org.dromara.djs.warehouse.dashboard.mapper.WarehouseDashboardMapper;
import org.dromara.djs.warehouse.dashboard.mapper.WarehouseProductionDashboardMapper;
import org.dromara.djs.warehouse.dashboard.service.impl.WarehouseDashboardServiceImpl;
import org.dromara.djs.warehouse.flow.constant.FlowDisplayScope;
import org.dromara.djs.warehouse.loss.service.IProductionLossService;
import org.dromara.djs.warehouse.stat.domain.WarehouseIndicatorRecord;
import org.dromara.djs.warehouse.stat.domain.WarehouseMonthlyRecord;
import org.dromara.djs.warehouse.stat.job.WarehouseStatJob;
import org.dromara.djs.warehouse.stat.service.impl.WarehouseStatServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.MockedStatic;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@Tag("local")
@Tag("dev")
@EnabledIfEnvironmentVariable(named = "DJS_STAT_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:(3306|3316)/.*")
class WarehouseProductionIndicatorsMysqlTest {

    private static final LocalDate REPORT_DAY = LocalDate.of(2026, 8, 15);
    private static final Path MIGRATIONS = Path.of("../../ruoyi-admin/src/main/resources/db/migration");
    private static final String TENANT = "1001";
    private static final List<String> VEGETABLE = List.of("vegetable");
    private LocalDate today;
    private LocalDate monthStart;
    private Connection connection;
    private SqlSession session;
    private WarehouseStatAggregateMapper stats;
    private WarehouseDashboardMapper dashboard;
    private WarehouseBoardStatMapper board;
    private WarehouseProductionDashboardMapper production;
    private WarehouseStatServiceImpl service;
    private MockedStatic<TenantHelper> tenantHelper;

    @BeforeEach
    void openTemporaryTables() throws SQLException, IOException {
        connection = DriverManager.getConnection(System.getenv("DJS_STAT_MYSQL_URL"),
            System.getenv("DJS_STAT_MYSQL_USER"), System.getenv("DJS_STAT_MYSQL_PASSWORD"));
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement(); var date = statement.executeQuery("SELECT CURDATE()")) {
            date.next();
            today = date.getObject(1, LocalDate.class);
        }
        monthStart = today.withDayOfMonth(1);
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_pig_cut_record (
                id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), del_flag CHAR(1), white_bar_id BIGINT,
                is_half INT, out_type VARCHAR(16), pickup_weight DECIMAL(12,3),
                pickup_time DATETIME, cut_done_time DATETIME, cut_id VARCHAR(32),
                white_bar_no VARCHAR(32), create_time DATETIME, update_time DATETIME
            )
            """);
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_stock_flow (
                tenant_id VARCHAR(20), del_flag CHAR(1), product_id BIGINT, inout_type VARCHAR(3),
                flow_type VARCHAR(32), change_quantity DECIMAL(16,3), flow_date DATETIME,
                ear_no VARCHAR(32), white_bar_no VARCHAR(32), remark VARCHAR(500),
                create_time DATETIME, update_time DATETIME
            )
            """);
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_product_info (
                id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), del_flag CHAR(1), product_attr INT,
                belong_type VARCHAR(32), product_name VARCHAR(64), product_unit VARCHAR(16), product_spec VARCHAR(32)
            )
            """);
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_product_production (
                id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id VARCHAR(20), del_flag CHAR(1), product_id BIGINT,
                produce_date DATE, produce_no VARCHAR(32), product_weight DECIMAL(12,3), plot_id BIGINT,
                product_unit VARCHAR(16), product_spec VARCHAR(32), pack_status VARCHAR(16), material_consume DECIMAL(12,3)
            )
            """);
        execute("""
            INSERT INTO t_warehouse_product_info VALUES
                (1,'1001','0',2,'vegetable','毛菜','kg',NULL),
                (2,'1001','0',1,'vegetable','果蔬成品','kg',NULL),
                (3,'1001','0',1,'pork','猪肉成品','kg',NULL),
                (4,'1001','0',2,'white_bar','半扇','kg',NULL)
            """);
        createStatTables();
        createSourceTables();

        MybatisConfiguration config = new MybatisConfiguration();
        config.setEnvironment(new Environment("warehouse-production-fixtures",
            new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(WarehouseStatAggregateMapper.class);
        config.addMapper(WarehouseDashboardMapper.class);
        config.addMapper(WarehouseBoardStatMapper.class);
        config.addMapper(WarehouseProductionDashboardMapper.class);
        config.addMapper(WarehouseIndicatorRecordMapper.class);
        config.addMapper(WarehouseMonthlyRecordMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(connection);
        stats = session.getMapper(WarehouseStatAggregateMapper.class);
        dashboard = session.getMapper(WarehouseDashboardMapper.class);
        board = session.getMapper(WarehouseBoardStatMapper.class);
        production = session.getMapper(WarehouseProductionDashboardMapper.class);

        // The crop query references the same table three times, which MySQL temporary tables cannot do.
        // Crop and production-loss writes are outside this report's fixtures. All report source and write SQL runs.
        WarehouseStatAggregateMapper aggregates = mock(WarehouseStatAggregateMapper.class, delegatesTo(stats));
        doReturn(List.of()).when(aggregates).selectActiveCropIds(anyString(), anyString());
        tenantHelper = mockStatic(TenantHelper.class);
        tenantHelper.when(TenantHelper::getTenantId).thenReturn(TENANT);
        service = new WarehouseStatServiceImpl(aggregates, session.getMapper(WarehouseIndicatorRecordMapper.class),
            mock(WarehouseCroppRecordMapper.class), session.getMapper(WarehouseMonthlyRecordMapper.class),
            mock(IProductionLossService.class), production);
    }

    @AfterEach
    void closeTemporaryTables() throws SQLException {
        if (tenantHelper != null) tenantHelper.close();
        if (session != null) session.close();
        if (connection != null && !connection.isClosed()) connection.close();
    }

    @Test
    @DisplayName("仓库 10：一只加一扇为 3 扇")
    void row10CutCountsSides() throws SQLException {
        cut(1, 2, "cut", 100, today, null);
        cut(2, 1, "cut", 50, today, null);
        cut(3, 2, "ship", 100, today, null);
        assertThat(stats.countCutBar(TENANT, today.toString())).isEqualByComparingTo("3");
        assertThat(dashboard.countTodayCutBars(TENANT)).isEqualByComparingTo("3");
        assertThat(recompute(today).getCutBarCount()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("仓库 20：按份，排除作废与重复生产号")
    void row20DailyProductionCountsValidPortions() throws SQLException {
        packed("B01", today, "0", "packed");
        packed("B02", today, "0", "shipped_out");
        packed("B01", today, "0", "packed");
        packed("VOID", today, "1", "packed");
        packed("PENDING", today, "0", "pending");
        assertThat(dashboard.sumTodayVegProductWeight(TENANT)).isEqualByComparingTo("2");
        assertThat(recompute(today).getVegProducedPortionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("仓库 48：跨月按入库日，只算毛菜")
    void row48BoardInboundOnlyActualRawVegetables() throws SQLException {
        flow(1, "veg_stock_in", "IN", 30, monthStart);
        flow(1, "veg_stock_in", "IN", 20, monthStart.minusDays(1));
        flow(1, "store_back_in", "IN", 10, monthStart);
        flow(1, "backstage_in", "IN", 40, monthStart);
        assertThat(board.selectInboundByCategoryUnit(TENANT, VEGETABLE, FlowDisplayScope.IN_EXCLUDED,
            monthStart, monthStart.plusMonths(1))).singleElement().satisfies(row -> {
                assertThat(row.getQty()).isEqualByComparingTo("30");
                assertThat(row.getProductUnit()).isEqualTo("kg");
            });
        assertThat(board.selectInboundDetailByProduct(TENANT, VEGETABLE, FlowDisplayScope.IN_EXCLUDED,
            monthStart, monthStart.plusMonths(1))).singleElement()
            .satisfies(row -> assertThat(row.getQty()).isEqualByComparingTo("30"));
        recompute(monthStart.minusDays(1));
        recompute(monthStart);
        assertThat(monthly(monthStart).getVegRawInboundWeight()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("仓库 51：两批各 10 份，重打 3 份仍只计 20")
    void row51BoardProductionCountsTwentyPortions() throws SQLException {
        for (int i = 0; i < 10; i++) {
            packed("A" + i, monthStart, "0", "packed");
            packed("B" + i, monthStart.plusDays(1), "0", "shipped_out");
        }
        for (int i = 0; i < 3; i++) packed("A" + i, monthStart, "0", "packed");
        assertThat(board.selectProduceByCategoryUnit(TENANT, VEGETABLE, monthStart, monthStart.plusMonths(1)))
            .singleElement().satisfies(row -> {
                assertThat(row.getQty()).isEqualByComparingTo("20");
                assertThat(row.getProductUnit()).isEqualTo("份");
            });
        assertThat(board.selectProduceDetailByProduct(TENANT, VEGETABLE, monthStart, monthStart.plusMonths(1)))
            .singleElement().satisfies(row -> assertThat(row.getQty()).isEqualByComparingTo("20"));
        recompute(monthStart);
        recompute(monthStart.plusDays(1));
        assertThat(monthly(monthStart).getVegProducedPortionCount()).isEqualTo(20);
    }

    @Test
    @DisplayName("仓库 3：同一首次接收批次的自养 100 kg 和外购 140 kg")
    void row03AverageMarketingWeightUsesReceivedCohort() throws SQLException {
        pig("HOME", 100, REPORT_DAY.minusDays(1), REPORT_DAY, null, null);
        pig("BUY", null, null, REPORT_DAY, null, null);
        execute("UPDATE t_warehouse_bar_info SET buy_date = ? WHERE bar_id = 'BUY'", REPORT_DAY.minusDays(2));
        execute("INSERT INTO t_warehouse_outsource_pig (bar_id,tenant_id,del_flag,pig_weight,slaughter_date) "
            + "VALUES ('BUY','1001','0',140,?)", REPORT_DAY.minusDays(1));
        assertThat(recompute(REPORT_DAY).getAvgSlaughterWeight()).isEqualByComparingTo("120");
        assertThat(recompute(REPORT_DAY.minusDays(1)).getAvgSlaughterWeight()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("仓库 4：只计实际毛菜入库 30 kg")
    void row04RawInboundExcludesUnstockedPurchaseAndReturn() throws SQLException {
        weighed(60, today);
        flow(1, "veg_stock_in", "IN", 30, today);
        flow(1, "purchase_in", "IN", 20, today);
        flow(1, "store_back_in", "IN", 10, today);
        flow(1, "veg_stock_in", "IN", -7, today);
        assertThat(dashboard.sumTodayVegHandleWeight(TENANT)).isEqualByComparingTo("30");
        assertThat(recompute(today).getVegRawInboundWeight()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("仓库 5：当日出栏待接收不算屠宰，首次接收优先于 in_time")
    void row05OnlyFirstValidReceiptCounts() throws SQLException {
        pig("PENDING", 100, REPORT_DAY, null, REPORT_DAY, null);
        pig("RECEIVED", 120, REPORT_DAY.minusDays(1), REPORT_DAY, REPORT_DAY.minusDays(2), null);
        assertThat(recompute(REPORT_DAY).getSlaughterCount()).isEqualTo(1);
        assertThat(recompute(REPORT_DAY.minusDays(2)).getSlaughterCount()).isZero();
    }

    @Test
    @DisplayName("仓库 6：11-23 接收 5 完成 3，次日接收 2 完成 1，日末待完成 3")
    void row06PendingSlaughterCarriesEarlierReceipts() throws SQLException {
        LocalDate day = LocalDate.of(2025, 11, 24);
        for (int i = 0; i < 5; i++) {
            pig("P" + i, 100, day.minusDays(2), day.minusDays(1), null,
                i < 3 ? day.minusDays(1) : null);
        }
        pig("P5", 100, day, day, null, day);
        pig("P6", 100, day, day, null, day.plusDays(1));
        pig("FUTURE", 100, day, day.plusDays(1), null, null);
        pig("NOT_RECEIVED", 100, day, null, day, null);
        assertThat(recompute(day.minusDays(1)).getSlaughterPendingCount()).isEqualTo(2);
        assertThat(recompute(day).getSlaughterPendingCount()).isEqualTo(3);

        // 补录其中一头在当天已完成，已有日表应从 3 回刷为 2。
        execute("UPDATE t_warehouse_bar_info SET finish_time = ? WHERE bar_id = 'P3'", day);
        service.refreshProductionIndicators(day, day);
        assertThat(daily(day).getSlaughterPendingCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("仓库 7：本年接收 100 头，截止日报完成 90 头，去年 3 头不计")
    void row07CompletionUsesYearToDateReceivedCohort() throws SQLException {
        LocalDate yearStart = REPORT_DAY.withDayOfYear(1);
        for (int i = 0; i < 100; i++) {
            // 历史行没有 arrive_time 时，沿用 in_time。十头未完成含空值及报表日之后完成。
            pig("Y" + i, 100, yearStart, yearStart, yearStart, i < 90 ? REPORT_DAY : i < 95 ? null : REPORT_DAY.plusDays(1));
        }
        execute("UPDATE t_warehouse_bar_info SET arrive_time = NULL WHERE bar_id = 'Y0'");
        for (int i = 0; i < 3; i++) {
            pig("PREV" + i, 100, yearStart.minusDays(1), yearStart.minusDays(1), null, REPORT_DAY);
        }
        pig("FUTURE", 120, REPORT_DAY, REPORT_DAY.plusDays(1), null, null);
        WarehouseIndicatorRecord day = recompute(REPORT_DAY);
        assertThat(day.getSlaughterYtdReceivedCount()).isEqualTo(100);
        assertThat(day.getSlaughterYtdCompletedCount()).isEqualTo(90);
        assertThat(day.getSlaughterCompletionRate()).isEqualByComparingTo("90.00");
        assertThat(day.getFinishedCount()).isEqualTo(93);
    }

    @Test
    @DisplayName("仓库 8：两扇合计 80 kg，整猪分母为 1")
    void row08WhiteBarAverageCountsWholePigs() throws SQLException {
        pig("P1", 100, REPORT_DAY, REPORT_DAY, null, null);
        whiteBar("P1", 39, REPORT_DAY);
        execute("INSERT INTO t_warehouse_product_inhouse "
            + "SELECT 'P1-RIGHT','1001',id FROM t_warehouse_bar_info WHERE bar_id = 'P1'");
        execute("INSERT INTO t_warehouse_stock_flow VALUES ('1001','0',4,'IN','slaughter_burn',41,?,NULL,'P1-RIGHT',NULL,NULL,NULL)",
            REPORT_DAY.plusDays(1));
        WarehouseIndicatorRecord day = recompute(REPORT_DAY);
        assertThat(day.getBarPigCount()).isEqualTo(1);
        assertThat(day.getAvgBarWeight()).isEqualByComparingTo("80.000");
        assertThat(recompute(REPORT_DAY.minusDays(1)).getAvgBarWeight()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("仓库 9：1÷3 为 33.33%，缺源出栏重为空，实录零出品率仍为零")
    void row09WhiteBarYieldUsesSameCohortBases() throws SQLException {
        pig("P1", 3, REPORT_DAY, REPORT_DAY, null, null);
        whiteBar("P1", 1, REPORT_DAY.plusDays(1));
        assertThat(recompute(REPORT_DAY).getBarYieldRate()).isEqualByComparingTo("33.33");

        LocalDate missingDay = REPORT_DAY.plusDays(1);
        pig("MISSING", null, REPORT_DAY, missingDay, null, null);
        whiteBar("MISSING", 1, missingDay);
        assertThat(recompute(missingDay).getBarYieldRate()).isNull();
        pig("MIXED", 10, REPORT_DAY, missingDay, null, null);
        assertThat(recompute(missingDay).getBarYieldRate()).isNull();

        LocalDate zeroDay = missingDay.plusDays(1);
        pig("ZERO", 10, REPORT_DAY, zeroDay, null, null);
        assertThat(recompute(zeroDay).getBarYieldRate()).isEqualByComparingTo("0.00");
        assertThat(recompute(zeroDay.plusDays(1)).getBarYieldRate()).isEqualByComparingTo("0.00");
        var view = pork(REPORT_DAY);
        assertThat(matrixDaily(view, "白条出品率", "08-15")).isEqualTo("33.33");
        assertThat(matrixDaily(view, "白条出品率", "08-16")).isEmpty();
        assertThat(matrixDaily(view, "白条出品率", "08-17")).isEqualTo("0.00");

        execute("UPDATE t_warehouse_bar_info SET marketing_weight = 0 WHERE bar_id = 'P1'");
        service.refreshProductionIndicators(REPORT_DAY, REPORT_DAY);
        assertThat(daily(REPORT_DAY).getBarYieldRate()).isNull();
    }

    @Test
    @DisplayName("仓库 11：分割毛领用 100 kg，退回及其他出库不混入")
    void row11CutWeightIncludesOnlyCutPickup() throws SQLException {
        cut(1, 2, "cut", 100, REPORT_DAY, null);
        cut(2, 1, "ship", 50, REPORT_DAY, null);
        flow(4, "prod_return_in", "IN", 10, REPORT_DAY);
        assertThat(recompute(REPORT_DAY).getCutBarWeight()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("仓库 12、14：11-23 领 100 kg，11-24 产 96 kg，原领用日为 96 kg / 96%")
    void rows12And14CutOutputAndRateFollowExactPickup() throws SQLException {
        LocalDate pickup = LocalDate.of(2025, 11, 23);
        LocalDate output = pickup.plusDays(1);
        cut(1, 1, "cut", 100, pickup, null);
        cut(2, 1, "cut", 50, output.plusDays(1), null);
        execute("UPDATE t_warehouse_pig_cut_record SET white_bar_id = 1 WHERE id = 2");
        cutOutput(1, 96, output, output);
        execute("UPDATE t_warehouse_stock_flow SET remark = '[HISTORY-PORK-20261120] cut=CUT1' WHERE white_bar_no = 'HALF1'");
        cutOutput(2, 40, output.plusDays(1), output.plusDays(1));
        cutOutput(998, 10, output, output);
        cutOutput(999, 300, output, output);
        execute("UPDATE t_warehouse_stock_flow SET del_flag = '1' WHERE white_bar_no = 'HALF999'");
        cut(998, 1, "cut", 10, output, null);
        execute("UPDATE t_warehouse_pig_cut_record SET tenant_id = '2002' WHERE id = 998");
        cut(997, 1, "cut", 10, output, null);
        execute("UPDATE t_warehouse_pig_cut_record SET del_flag = '1' WHERE id = 997");
        cutOutput(997, 10, output, output);
        flow(3, "cut_out_in", "OT", 9, output);

        var row = recompute(pickup);
        assertThat(row.getCutProductWeight()).isEqualByComparingTo("96.000");
        assertThat(row.getCutRate()).isEqualByComparingTo("96.00");
        assertThat(row.getCutOutputUnmatchedCount()).isZero();
        var outputDay = recompute(output);
        assertThat(outputDay.getCutProductWeight()).isEqualByComparingTo("0.000");
        assertThat(outputDay.getCutRate()).isEqualByComparingTo("0.00");
        assertThat(outputDay.getCutOutputUnmatchedCount()).isEqualTo(2);

        execute("UPDATE t_warehouse_stock_flow SET del_flag = '1' WHERE white_bar_no = 'HALF998'");
        service.refreshProductionIndicators(output, output);
        assertThat(daily(output).getCutOutputUnmatchedCount()).isEqualTo(1);
        assertThat(recompute(output.plusDays(1)).getCutRate()).isEqualByComparingTo("80.00");

        cut(3, 1, "cut", 3, output.plusDays(2), null);
        cutOutput(3, 1, output.plusDays(3), output.plusDays(3));
        assertThat(recompute(output.plusDays(2)).getCutRate()).isEqualByComparingTo("33.33");
    }

    @Test
    @DisplayName("仓库 12：首页实时分割产品重也按领用日")
    void row12DashboardUsesPickupDay() throws SQLException {
        cut(1, 1, "cut", 100, today.minusDays(1), null);
        cut(2, 1, "cut", 100, today, null);
        cutOutput(1, 96, today, today);
        cutOutput(2, 30, today, today);
        execute("UPDATE t_warehouse_stock_flow SET remark = '[HISTORY-PORK-20261120] cut=CUT2' WHERE white_bar_no = 'HALF2'");
        assertThat(dashboard.sumTodayCutProductWeight(TENANT)).isEqualByComparingTo("30");
        assertThat(recompute(today).getCutProductWeight()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("仓库 13：昨天领用 100 kg，今日产 96 kg，损耗归昨天")
    void row13LossFollowsPickupAndExcludesPrecoolAndOrphans() throws SQLException {
        LocalDate done = REPORT_DAY.plusDays(1);
        addCutLoss(REPORT_DAY, done);
        loss(10, done, "precool_loss", "cut", 1);
        loss(20, done, "cut_loss", "cut", 999);
        loss(30, done, "cut_loss", "other", 1);
        WarehouseIndicatorRecord pickup = recompute(REPORT_DAY);
        WarehouseIndicatorRecord finished = recompute(done);
        assertThat(pickup.getCutLoss()).isEqualByComparingTo("4");
        assertThat(finished.getCutLoss()).isEqualByComparingTo("0");
        assertThat(finished.getPrecoolLoss()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("仓库 15：前一天领 3 扇，已完成 2 扇，当日日末余 1 扇")
    void row15PendingSidesUseHistoricalEndOfDay() throws SQLException {
        cut(1, 2, "cut", 100, REPORT_DAY.minusDays(1), REPORT_DAY.minusDays(1));
        cut(2, 1, "cut", 50, REPORT_DAY.minusDays(1), REPORT_DAY.plusDays(1));
        cut(3, 2, "cut", 100, REPORT_DAY.plusDays(1), null);
        cut(4, 2, "ship", 100, REPORT_DAY, null);
        assertThat(recompute(REPORT_DAY).getCutPendingHalfCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("仓库 16：领用 4 扇，当日日末完成 3 扇，完成率 75%")
    void row16CutCompletionUsesSamePickupAndEndOfDay() throws SQLException {
        cut(1, 2, "cut", 100, REPORT_DAY, REPORT_DAY);
        cut(2, 1, "cut", 50, REPORT_DAY, REPORT_DAY);
        cut(3, 1, "cut", 50, REPORT_DAY, REPORT_DAY.plusDays(1));
        cut(4, 2, "cut", 100, REPORT_DAY.minusDays(1), REPORT_DAY);
        cut(5, 2, "ship", 100, REPORT_DAY, REPORT_DAY);
        assertThat(recompute(REPORT_DAY).getCutCompletionRate()).isEqualByComparingTo("75.00");
        assertThat(recompute(REPORT_DAY.plusDays(1)).getCutCompletionRate()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("仓库 12、16：日批反查窗口外领用日，回刷补录产出和日末完成率，重跑幂等")
    void scheduledJobRepairsOlderPickupDates() throws SQLException {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate pickup = yesterday.minusDays(45);
        LocalDate anotherPickup = pickup.plusDays(1);
        cut(1, 2, "cut", 100, pickup, null);
        cut(2, 1, "cut", 40, anotherPickup, null);
        recompute(pickup);
        recompute(anotherPickup);
        assertThat(daily(pickup).getCutProductWeight()).isZero();
        assertThat(daily(anotherPickup).getCutCompletionRate()).isZero();

        // 产出业务日期也在窗口外，依赖录入日期定位。第二张单没有产出流，验证完成变更可独立定位。
        cutOutput(1, 96, pickup.plusDays(2), yesterday);
        execute("UPDATE t_warehouse_stock_flow SET remark = '[HISTORY-PORK-20261120] cut=CUT1' WHERE white_bar_no = 'HALF1'");
        execute("UPDATE t_warehouse_pig_cut_record SET cut_done_time = ?, update_time = ? WHERE id = 2",
            anotherPickup, yesterday);
        WarehouseStatJob job = new WarehouseStatJob(service, mock(DjsJobRegistry.class));
        try (MockedStatic<DjsJobRunner> runner = mockStatic(DjsJobRunner.class)) {
            runner.when(() -> DjsJobRunner.run(eq(WarehouseStatJob.JOB_NAME), any(Runnable.class)))
                .thenAnswer(call -> {
                    call.getArgument(1, Runnable.class).run();
                    return null;
                });
            for (int attempt = 0; attempt < 2; attempt++) {
                job.aggregate();
                assertThat(daily(pickup).getCutProductWeight()).isEqualByComparingTo("96.000");
                assertThat(daily(anotherPickup).getCutCompletionRate()).isEqualByComparingTo("100.00");
                assertThat(monthly(pickup).getCutProductWeight()).isEqualByComparingTo("96.000");
            }

            execute("UPDATE t_warehouse_stock_flow SET del_flag = '1', update_time = ? WHERE flow_type = 'cut_out_in'",
                yesterday);
            job.aggregate();
            assertThat(daily(pickup).getCutProductWeight()).isZero();
            assertThat(monthly(pickup).getCutProductWeight()).isZero();
        }
    }

    @Test
    @DisplayName("仓库 23：配送 30 kg，排除移库、领用及已删除产品的配送")
    void row23DeliveryUsesOnlyActualVegetableShipment() throws SQLException {
        deliveryAndNonShipments(REPORT_DAY);
        execute("""
            INSERT INTO t_warehouse_product_info VALUES (5,'1001','1',1,'vegetable','已删除果蔬','kg',NULL)
            """);
        flow(5, "ship_out", "OT", 25, REPORT_DAY);
        assertThat(recompute(REPORT_DAY).getVegDeliveryOutWeight()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("仓库 25：4/100 = 4%，补一张零损耗 100 kg 后为 2%")
    void row25LossRateIncludesZeroLossPickup() throws SQLException {
        addCutLoss(REPORT_DAY, REPORT_DAY.plusDays(1));
        WarehouseIndicatorRecord row = recompute(REPORT_DAY);
        assertThat(row.getCutLossRate()).isEqualByComparingTo("4.00");
        cut(2, 2, "cut", 100, REPORT_DAY, REPORT_DAY);
        assertThat(recompute(REPORT_DAY).getCutLossRate()).isEqualByComparingTo("2.00");
        assertThat(recompute(REPORT_DAY.plusDays(1)).getCutLossRate()).isNull();
    }

    @Test
    @DisplayName("仓库 28：八月送宰、九月首次接收，屠宰归九月")
    void row28MonthlySlaughterFollowsFirstReceipt() throws SQLException {
        LocalDate endAugust = LocalDate.of(2026, 8, 31);
        pig("P1", 105, endAugust, endAugust.plusDays(1), null, null);
        recompute(endAugust);
        recompute(endAugust.plusDays(1));
        assertThat(monthly(endAugust).getSlaughterCount()).isZero();
        assertThat(monthly(endAugust.plusDays(1)).getSlaughterCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("仓库 29：八月源出栏 100 + 140 kg，跨月收猪，八月出栏均重 120 kg")
    void row29MonthlyMarketAverageUsesSourceDates() throws SQLException {
        LocalDate endAugust = LocalDate.of(2026, 8, 31);
        sourceMarketings(endAugust);
        recompute(endAugust);
        recompute(endAugust.plusDays(1));
        var month = monthly(endAugust);
        assertThat(month.getSourceMarketingWeight()).isEqualByComparingTo("240");
        assertThat(month.getSourceMarketingAvgWeight()).isEqualByComparingTo("120");
        assertThat(monthly(endAugust.plusDays(1)).getSourceMarketingAvgWeight()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("仓库 30：八月出栏后九月收猪，自养与外购共 2 头，外购镜像不重计")
    void row30MonthlyMarketingHeadsUseSourceDates() throws SQLException {
        LocalDate endAugust = LocalDate.of(2026, 8, 31);
        sourceMarketings(endAugust);
        recompute(endAugust);
        recompute(endAugust.plusDays(1));
        assertThat(monthly(endAugust).getSourceMarketingCount()).isEqualTo(2);
        assertThat(monthly(endAugust.plusDays(1)).getSourceMarketingCount()).isZero();
    }

    @Test
    @DisplayName("仓库 31：两天共 3 头产 100 kg，月表及矩阵累计均为 33.333 kg")
    void row31MonthlyWhiteBarAverageUsesWeightedHeads() throws SQLException {
        pig("P1", 100, REPORT_DAY, REPORT_DAY, null, null);
        pig("P2", 150, REPORT_DAY, REPORT_DAY.plusDays(1), null, null);
        pig("P3", 150, REPORT_DAY, REPORT_DAY.plusDays(1), null, null);
        whiteBar("P1", 80, REPORT_DAY);
        whiteBar("P2", 9, REPORT_DAY);
        whiteBar("P3", 11, REPORT_DAY);
        recompute(REPORT_DAY);
        recompute(REPORT_DAY.plusDays(1));
        recompute(REPORT_DAY.minusMonths(1));
        var month = monthly(REPORT_DAY);
        assertThat(month.getReceivedBarWeight()).isEqualByComparingTo("100");
        assertThat(month.getReceivedBarPigCount()).isEqualTo(3);
        assertThat(month.getReceivedBarAvgWeight()).isEqualByComparingTo("33.333");
        assertThat(matrixTotal("白条均重")).isEqualTo("33.333");
        assertThat(monthly(REPORT_DAY.minusMonths(1)).getReceivedBarAvgWeight()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("仓库 32：日率 80%、20%、0%，月表与矩阵均为 50%，区别于原重量加权 30%")
    void row32MonthlyWhiteBarYieldKeepsMatrixFormula() throws SQLException {
        pig("P1", 100, REPORT_DAY, REPORT_DAY, null, null);
        pig("P2", 200, REPORT_DAY, REPORT_DAY.plusDays(1), null, null);
        pig("P3", 100, REPORT_DAY, REPORT_DAY.plusDays(2), null, null);
        whiteBar("P1", 80, REPORT_DAY);
        whiteBar("P2", 40, REPORT_DAY);
        recompute(REPORT_DAY);
        recompute(REPORT_DAY.plusDays(1));
        recompute(REPORT_DAY.plusDays(2));
        var month = monthly(REPORT_DAY);
        assertThat(month.getSumDailyBarYieldRate()).isEqualByComparingTo("100");
        assertThat(month.getPositiveBarYieldDays()).isEqualTo(2);
        assertThat(month.getMatrixBarYieldRate()).isEqualByComparingTo("50");
        assertThat(month.getBarYieldRate()).isEqualByComparingTo("50.00");
        assertThat(month.getMatrixBarYieldRate()).isEqualByComparingTo(matrixTotal("白条出品率"));
    }

    @Test
    @DisplayName("仓库 33：同月分两天领整只和单侧，合计 3 扇")
    void row33MonthlyCutBarsSumSides() throws SQLException {
        cut(1, 2, "cut", 100, REPORT_DAY, null);
        cut(2, 1, "cut", 50, REPORT_DAY.plusDays(1), null);
        cut(3, 2, "cut", 100, REPORT_DAY.plusMonths(1), null);
        recompute(REPORT_DAY);
        recompute(REPORT_DAY.plusDays(1));
        recompute(REPORT_DAY.plusMonths(1));
        assertThat(monthly(REPORT_DAY).getCutBarCount()).isEqualByComparingTo("3")
            .isEqualByComparingTo(matrixTotal("分割扇数"));
        assertThat(monthly(REPORT_DAY.plusMonths(1)).getCutBarCount()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("月表与小程序矩阵累计包含 31 天回刷窗口外的分割日行")
    void monthlyCutTotalsIncludeHistoricalDailyRowsAndMatchMiniappMatrix() throws SQLException {
        LocalDate historicalDay = today.minusDays(WarehouseStatServiceImpl.INDICATOR_REFRESH_DAYS + 2L)
            .withDayOfMonth(1);
        LocalDate nextDay = historicalDay.plusDays(1);
        addCutLoss(historicalDay, nextDay);
        cut(2, 1, "cut", 50, nextDay, nextDay);
        loss(3, nextDay, "cut_loss", "cut", 2);

        recompute(historicalDay);
        execute("UPDATE t_warehouse_indicator_record SET cut_pending_half_count = NULL WHERE stat_date = ?",
            historicalDay);
        recompute(nextDay);

        var daily = production.selectIndicatorRecordsInRange(TENANT, historicalDay, nextDay);
        assertThat(daily).hasSize(2);
        var first = daily.getFirst();
        var second = daily.getLast();
        assertThat(first.getCutPendingHalfCount()).isNull();
        assertThat(second.getCutPendingHalfCount()).isZero();

        var month = monthly(historicalDay);
        var dashboardService = new WarehouseDashboardServiceImpl(dashboard, production, service);
        var pork = dashboardService.getPorkEfficiency(historicalDay.getYear(), YearMonth.from(historicalDay).toString());
        assertThat(month.getCutBarCount()).isEqualByComparingTo("3")
            .isEqualByComparingTo(first.getCutBarCount().add(second.getCutBarCount()))
            .isEqualByComparingTo(matrixTotal(pork, "分割扇数"))
            .isEqualByComparingTo(pork.getCutBarCount());
        assertThat(month.getCutLoss()).isEqualByComparingTo("7")
            .isEqualByComparingTo(first.getCutLoss().add(second.getCutLoss()))
            .isEqualByComparingTo(matrixTotal(pork, "分割损耗"));
        assertThat(month.getCutLossPickupWeight()).isEqualByComparingTo("150")
            .isEqualByComparingTo(first.getCutBarWeight().add(second.getCutBarWeight()))
            .isEqualByComparingTo(matrixTotal(pork, "分割总重"));
        assertThat(month.getCutLossRate()).isEqualByComparingTo("4.67");
        assertThat(pork.getCutBarWeightTon()).isEqualByComparingTo("0.150");
        assertThat(service.getAnnual(historicalDay.getYear()).cutLossRate()).isEqualByComparingTo("4.67");
    }

    @Test
    @DisplayName("仓库 34、35、36：两天有效领用 100+50 kg，跨月产出 90+20 kg，月率 73.33%")
    void rows34To36MonthlyCutUsesNetPickupAndSameCohortOutput() throws SQLException {
        LocalDate pickup = LocalDate.of(2025, 11, 29);
        LocalDate secondPickup = pickup.plusDays(1);
        LocalDate output = pickup.plusDays(2);
        cut(1, 2, "cut", 110, pickup, null);
        cut(2, 1, "cut", 50, secondPickup, null);
        cut(3, 1, "cut", 35, secondPickup, null);
        cut(4, 2, "ship", 90, pickup, null);
        recompute(pickup);
        recompute(secondPickup);
        // 以有效领用单的更正净重作基数。软删模拟撤销领用整单。
        execute("UPDATE t_warehouse_pig_cut_record SET pickup_weight = 100 WHERE id = 1");
        execute("UPDATE t_warehouse_pig_cut_record SET del_flag = '1' WHERE id = 3");
        cutOutput(1, 90, output, output);
        cutOutput(2, 20, output, output);
        cutOutput(3, 30, output, output);
        var first = recompute(pickup);
        var second = recompute(secondPickup);
        recompute(output);

        var month = monthly(pickup);
        assertThat(month.getCutBarWeight()).isEqualByComparingTo("150")
            .isEqualByComparingTo(first.getCutBarWeight().add(second.getCutBarWeight()));
        assertThat(month.getCutProductWeight()).isEqualByComparingTo("110")
            .isEqualByComparingTo(first.getCutProductWeight().add(second.getCutProductWeight()));
        assertThat(month.getCutYieldRate()).isEqualByComparingTo("73.33");
        assertThat(monthly(output).getCutProductWeight()).isZero();
        assertThat(monthly(output).getCutYieldRate()).isZero();
        var view = pork(pickup);
        int monthIndex = view.getTrendMonths().indexOf("2025-11");
        assertThat(view.getTrendCutYieldRate().get(monthIndex)).isEqualByComparingTo("73.33");
        assertThat(matrixDaily(view, "分割率", "11-29")).isEqualTo("90.00");
        assertThat(matrixDaily(view, "分割率", "11-30")).isEqualTo("40.00");
        assertThat(matrixTotal(view, "分割率")).isEqualTo("65.00");
    }

    @Test
    @DisplayName("仓库 37：生产领 100 kg、退 10 kg，月毛领用仍为 100 kg")
    void row37MonthlyProductionPickIsGross() throws SQLException {
        flow(1, "prod_pick_out", "OT", 100, REPORT_DAY);
        flow(1, "prod_return_in", "IN", 10, REPORT_DAY);
        recompute(REPORT_DAY);
        assertThat(monthly(REPORT_DAY).getVegProductionPickWeight()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("仓库 44：100 kg 跨月完成损耗 4 kg，领用月 4%，再加零损耗 300 kg 为 1%")
    void row44MonthlyCutLossUsesWeightedPickupMonth() throws SQLException {
        LocalDate endAugust = LocalDate.of(2026, 8, 31);
        addCutLoss(endAugust, endAugust.plusDays(1));
        recompute(endAugust);
        recompute(endAugust.plusDays(1));
        assertThat(monthly(endAugust).getCutLossRate()).isEqualByComparingTo("4.00");
        assertThat(monthly(endAugust.plusDays(1)).getCutLossRate()).isNull();
        cut(2, 2, "cut", 300, endAugust.minusDays(1), endAugust);
        recompute(endAugust.minusDays(1));
        var month = monthly(endAugust);
        assertThat(month.getCutLoss()).isEqualByComparingTo("4");
        assertThat(month.getCutLossPickupWeight()).isEqualByComparingTo("400");
        assertThat(month.getCutLossRate()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("仓库 49：配送 30 + 5 kg，门店退回 10 kg，月合计 35 kg")
    void row49MonthlyDeliveryMatchesDailyTotals() throws SQLException {
        deliveryAndNonShipments(REPORT_DAY);
        flow(2, "ship_out", "OT", 5, REPORT_DAY.plusDays(1));
        flow(2, "store_back_in", "IN", 10, REPORT_DAY.plusDays(1));
        var first = recompute(REPORT_DAY);
        var second = recompute(REPORT_DAY.plusDays(1));
        assertThat(monthly(REPORT_DAY).getVegDeliveryOutWeight()).isEqualByComparingTo("35")
            .isEqualByComparingTo(first.getVegDeliveryOutWeight().add(second.getVegDeliveryOutWeight()));
    }

    @Test
    @DisplayName("仓库 53：12-31 送宰，1-01 首次接收，年度头数计入次年")
    void row53AnnualSlaughterFollowsFirstReceipt() throws SQLException {
        LocalDate sent = LocalDate.of(2025, 12, 31);
        LocalDate arrival = sent.plusDays(1);
        pig("CROSS", 100, sent, arrival, sent, null);
        recompute(sent);
        recompute(arrival);
        assertThat(service.getAnnual(2025).slaughterCount()).isZero();
        assertThat(service.getAnnual(2026).slaughterCount()).isEqualTo(1);
        assertThat(pork(sent).getSlaughterCount()).isEqualTo(1);
        assertThat(pork(sent).getAvgSlaughterWeight()).isEqualByComparingTo("100.00");
        assertThat(pork(arrival).getSlaughterCount()).isZero();
    }

    @Test
    @DisplayName("仓库 54：跨月 80%、20%、实录 0% 按三个可比日平均，缺源重量日排除")
    void row54AnnualWhiteBarYieldIncludesRecordedZeroDays() throws SQLException {
        LocalDate august = LocalDate.of(2025, 8, 31);
        LocalDate september = august.plusDays(1);
        pig("P80", 100, august, august, null, null);
        pig("P20", 100, september, september, null, null);
        pig("ZERO", 100, september, september.plusDays(1), null, null);
        pig("MISSING", null, september, september.plusDays(2), null, null);
        whiteBar("P80", 80, august);
        whiteBar("P20", 20, september);
        whiteBar("MISSING", 30, september.plusDays(2));
        recompute(august);
        for (int i = 0; i <= 3; i++) recompute(september.plusDays(i));
        assertThat(service.getAnnual(2025).barYieldRate()).isEqualByComparingTo("33.33");
        assertThat(pork(september).getBarYieldRate()).isEqualByComparingTo("33.33");
        assertThat(service.getAnnual(2024).barYieldRate()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("仓库 55：12-31 领用 100 kg，1-01 产出 96 kg，领用年为 96%")
    void row55AnnualCutRateFollowsPickupYear() throws SQLException {
        LocalDate pickup = LocalDate.of(2025, 12, 31);
        LocalDate output = pickup.plusDays(1);
        cut(1, 2, "cut", 100, pickup, output);
        cutOutput(1, 96, output, output);
        recompute(pickup);
        recompute(output);
        assertThat(service.getAnnual(2025).cutRate()).isEqualByComparingTo("96.00");
        assertThat(service.getAnnual(2026).cutRate()).isEqualByComparingTo("0.00");
        assertThat(pork(pickup).getCutRate()).isEqualByComparingTo("96.00");
        assertThat(pork(output).getCutRate()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("仓库 56：年度按两月基数重算，损耗 4 kg 除以领用 400 kg 得 1%")
    void row56AnnualLossFromWeightedMonthlyBases() throws SQLException {
        LocalDate endAugust = LocalDate.of(2026, 8, 31);
        addCutLoss(endAugust, endAugust.plusDays(1));
        recompute(endAugust);
        assertThat(service.getAnnual(2026).cutLossRate()).isEqualByComparingTo("4.00");
        cut(2, 2, "cut", 300, endAugust.plusDays(1), endAugust.plusDays(1));
        recompute(endAugust.plusDays(1));
        var year = service.getAnnual(2026);
        assertThat(year.cutLoss()).isEqualByComparingTo("4");
        assertThat(year.cutLossPickupWeight()).isEqualByComparingTo("400");
        assertThat(year.cutLossRate()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("仓库 57：年末称重、次年入库 25 kg，归次年")
    void row57AnnualRawInboundFollowsActualStockDate() throws SQLException {
        LocalDate yearEnd = LocalDate.of(2025, 12, 31);
        weighed(25, yearEnd);
        flow(1, "veg_stock_in", "IN", 25, yearEnd.plusDays(1));
        recompute(yearEnd);
        recompute(yearEnd.plusDays(1));
        assertThat(service.getAnnual(2025).vegRawInboundWeight()).isEqualByComparingTo("0");
        assertThat(service.getAnnual(2026).vegRawInboundWeight()).isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("仓库 58：按实际离库日归年，全年 30 + 50 = 80 kg")
    void row58AnnualDeliveryMatchesMonthlyTotals() throws SQLException {
        flow(2, "ship_out", "OT", 30, REPORT_DAY);
        flow(2, "ship_out", "OT", 50, REPORT_DAY.plusMonths(1));
        flow(2, "ship_out", "OT", 99, REPORT_DAY.plusYears(1));
        recompute(REPORT_DAY);
        recompute(REPORT_DAY.plusMonths(1));
        recompute(REPORT_DAY.plusYears(1));
        assertThat(service.getAnnual(2026).vegDeliveryOutWeight()).isEqualByComparingTo("80")
            .isEqualByComparingTo(monthly(REPORT_DAY).getVegDeliveryOutWeight()
                .add(monthly(REPORT_DAY.plusMonths(1)).getVegDeliveryOutWeight()));
    }

    @Test
    void rollingRefreshRepairsExistingRowsWithoutDuplicates() throws SQLException {
        cut(1, 2, "cut", 100, REPORT_DAY, null);
        recompute(REPORT_DAY);
        execute("UPDATE t_warehouse_indicator_record SET cut_bar_count = 1, cut_pending_half_count = NULL, precool_loss = 7");
        loss(4, REPORT_DAY.plusDays(1), "cut_loss", "cut", 1);
        flow(2, "ship_out", "OT", 30, REPORT_DAY.plusDays(1));

        for (int attempt = 0; attempt < 2; attempt++) {
            service.refreshProductionIndicators(REPORT_DAY, REPORT_DAY.plusDays(1));
        }

        assertThat(production.selectIndicatorRecordsInRange(TENANT, REPORT_DAY, REPORT_DAY.plusDays(1)))
            .hasSize(2).satisfies(rows -> {
                assertThat(rows.getFirst().getCutBarCount()).isEqualByComparingTo("2");
                assertThat(rows.getFirst().getCutLossRate()).isEqualByComparingTo("4");
                assertThat(rows.getFirst().getPrecoolLoss()).isEqualByComparingTo("7");
                assertThat(rows.getLast().getVegDeliveryOutWeight()).isEqualByComparingTo("30");
            });
        assertThat(production.selectMonthlyRecordsByYear(TENANT, "2026-%")).singleElement()
            .satisfies(row -> assertThat(row.getCutLossRate()).isEqualByComparingTo("4"));
    }

    private String matrixTotal(String metric) {
        return matrixTotal(pork(REPORT_DAY), metric);
    }

    private String matrixTotal(WarehousePorkEfficiencyVo pork, String metric) {
        return pork.getMatrixRows().stream()
            .filter(row -> metric.equals(row.getMetric())).findFirst().orElseThrow().getTotal();
    }

    private WarehousePorkEfficiencyVo pork(LocalDate day) {
        return new WarehouseDashboardServiceImpl(dashboard, production, service)
            .getPorkEfficiency(day.getYear(), YearMonth.from(day).toString());
    }

    private String matrixDaily(WarehousePorkEfficiencyVo pork, String metric, String day) {
        return pork.getMatrixRows().stream().filter(row -> metric.equals(row.getMetric()))
            .findFirst().orElseThrow().getDailyValues().get(pork.getMatrixDays().indexOf(day));
    }

    private WarehouseIndicatorRecord recompute(LocalDate day) {
        service.aggregate(day);
        return daily(day);
    }

    private WarehouseIndicatorRecord daily(LocalDate day) {
        var rows = production.selectIndicatorRecordsInRange(TENANT, day, day);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private WarehouseMonthlyRecord monthly(LocalDate date) {
        return production.selectMonthlyRecordsByYear(TENANT, date.getYear() + "-%").stream()
            .filter(row -> YearMonth.from(date).toString().equals(row.getStatMonth())).findFirst().orElseThrow();
    }

    private void addCutLoss(LocalDate pickup, LocalDate done) throws SQLException {
        cut(1, 2, "cut", 100, pickup, done);
        cutOutput(1, 96, done, done);
        loss(4, done, "cut_loss", "cut", 1);
    }

    private void loss(int weight, LocalDate date, String type, String source, long sourceId) throws SQLException {
        execute("INSERT INTO t_warehouse_loss_flow VALUES ('1001','0',?,?,?,?,?,'pork')", type, weight, date, source, sourceId);
    }

    private void deliveryAndNonShipments(LocalDate day) throws SQLException {
        flow(2, "ship_out", "OT", 30, day);
        flow(2, "location_transfer", "OT", 20, day);
        flow(1, "prod_pick_out", "OT", 10, day);
        flow(2, "manual_loss", "OT", 15, day);
        flow(3, "ship_out", "OT", 40, day);
    }

    private void sourceMarketings(LocalDate date) throws SQLException {
        pig("HOME", 100, date, date.plusDays(1), null, null);
        pig("BUY", 140, date, date.plusDays(1), null, null);
        execute("UPDATE t_warehouse_bar_info SET buy_date = ? WHERE bar_id = 'BUY'", date);
        execute("INSERT INTO t_warehouse_outsource_pig (bar_id,tenant_id,del_flag,pig_weight,slaughter_date) "
            + "VALUES ('BUY','1001','0',140,?)", date);
    }

    private void pig(String code, Integer sourceWeight, LocalDate marketed, LocalDate arrived, LocalDate inTime,
                     LocalDate finished) throws SQLException {
        execute("""
            INSERT INTO t_warehouse_bar_info
            (bar_id,tenant_id,del_flag,in_method,ear_no,marketing_time,marketing_weight,arrive_weight,arrive_time,in_time,finish_time)
            VALUES (?,'1001','0',1,?,?,?,?,?,?,?)
            """, code, code, marketed, sourceWeight, arrived == null ? null : 80, arrived, inTime, finished);
    }

    private void whiteBar(String ear, int weight, LocalDate day) throws SQLException {
        execute("INSERT INTO t_warehouse_stock_flow VALUES ('1001','0',4,'IN','slaughter_burn',?,?,?,NULL,NULL,NULL,NULL)", weight, day, ear);
    }

    private void weighed(int weight, LocalDate day) throws SQLException {
        execute("INSERT INTO t_warehouse_vegetable_handle VALUES ('1001','0',1,?,?)", day, weight);
    }

    private void createStatTables() throws IOException, SQLException {
        String original = Files.readString(MIGRATIONS.resolve(
            "V202607262400__WMS-STAT-001-create-warehouse-stat-tables.sql"));
        for (String table : List.of("t_warehouse_indicator_record", "t_warehouse_monthly_record")) {
            int from = original.indexOf("CREATE TABLE " + table + " (");
            int to = original.indexOf(';', from);
            execute(original.substring(from, to).replace("CREATE TABLE", "CREATE TEMPORARY TABLE"));
        }
        // Existing cohort columns from older migrations, before applying the actual V1.1 migration.
        execute("""
            ALTER TABLE t_warehouse_indicator_record
                ADD COLUMN arrive_pig_count INT,
                ADD COLUMN finished_count INT,
                ADD COLUMN finished_arrive_weight DECIMAL(12,3),
                ADD COLUMN slaughter_rate_arrive_weight DECIMAL(12,3),
                ADD COLUMN slaughter_rate_base_weight DECIMAL(12,3),
                ADD COLUMN bar_yield_numer_weight DECIMAL(12,3),
                ADD COLUMN bar_yield_base_weight DECIMAL(12,3),
                ADD COLUMN bar_pig_count INT,
                ADD COLUMN avg_arrive_weight DECIMAL(12,3)
            """);
        for (String name : List.of("V202609301000__WMS-STAT-V11-production-indicators.sql",
            "V202609301900__WMS-STAT-V12-pork-indicators.sql")) {
            for (String statement : Files.readString(MIGRATIONS.resolve(name)).split(";")) {
                if (!statement.isBlank()) execute(statement);
            }
        }
    }

    private void createSourceTables() throws SQLException {
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_bar_info (
                id BIGINT PRIMARY KEY AUTO_INCREMENT, bar_id VARCHAR(32), tenant_id VARCHAR(20), del_flag CHAR(1),
                in_method INT, ear_no VARCHAR(32), buy_date DATE, marketing_time DATETIME, marketing_weight DECIMAL(12,3),
                arrive_weight DECIMAL(12,3), arrive_time DATETIME, in_time DATETIME, finish_time DATETIME
            )
            """);
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_outsource_pig (
                id BIGINT PRIMARY KEY AUTO_INCREMENT, bar_id VARCHAR(32), tenant_id VARCHAR(20),
                del_flag CHAR(1), pig_weight DECIMAL(12,3), slaughter_date DATE
            )
            """);
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_product_inhouse (
                white_bar_no VARCHAR(32), tenant_id VARCHAR(20), white_bar_id BIGINT
            )
            """);
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_loss_flow (
                tenant_id VARCHAR(20), del_flag CHAR(1), loss_type VARCHAR(32), loss_weight DECIMAL(12,3),
                loss_date DATETIME, source_biz_type VARCHAR(32), source_biz_id BIGINT, belong_type VARCHAR(32)
            )
            """);
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_vegetable_handle (
                tenant_id VARCHAR(20), del_flag CHAR(1), is_finish INT, pick_end_time DATETIME, picked_weight DECIMAL(12,3)
            )
            """);
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_handle_record (
                tenant_id VARCHAR(20), del_flag CHAR(1), handle_time DATETIME, handle_target INT, record_weight DECIMAL(12,3)
            )
            """);
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_veg_receive (
                tenant_id VARCHAR(20), del_flag CHAR(1), receive_time DATETIME, receive_type INT, weight DECIMAL(12,3)
            )
            """);
    }

    private void cut(long id, int half, String type, int weight, LocalDate pickup, LocalDate done) throws SQLException {
        execute("""
            INSERT INTO t_warehouse_pig_cut_record
            (id,tenant_id,del_flag,white_bar_id,is_half,out_type,pickup_weight,pickup_time,cut_done_time,cut_id,white_bar_no)
            VALUES (?,'1001','0',?,?,?,?,?,?,?,?)
            """, id, id, half, type, weight, pickup, done, "CUT" + id, "HALF" + id);
    }

    private void cutOutput(long cutId, int weight, LocalDate date, LocalDate recorded) throws SQLException {
        execute("""
            INSERT INTO t_warehouse_stock_flow
            (tenant_id,del_flag,product_id,inout_type,flow_type,change_quantity,flow_date,white_bar_no,remark,create_time)
            VALUES ('1001','0',3,'IN','cut_out_in',?,?,?,?,?)
            """, weight, date, "HALF" + cutId, "分割产出入冻品库 cut_id=CUT" + cutId + " part=loin", recorded);
    }

    private void flow(int product, String type, String direction, int weight, LocalDate date) throws SQLException {
        execute("INSERT INTO t_warehouse_stock_flow VALUES ('1001','0',?,?,?,?,?,NULL,NULL,NULL,NULL,NULL)",
            product, direction, type, weight, date);
    }

    private void packed(String code, LocalDate date, String deleted, String status) throws SQLException {
        execute("""
            INSERT INTO t_warehouse_product_production
            (tenant_id,del_flag,product_id,produce_date,produce_no,product_weight,plot_id,product_unit,pack_status)
            VALUES ('1001',?,2,?,?,0.4,1,'kg',?)
            """, deleted, date, code, status);
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.execute();
        }
        if (session != null) session.clearCache();
    }
}
