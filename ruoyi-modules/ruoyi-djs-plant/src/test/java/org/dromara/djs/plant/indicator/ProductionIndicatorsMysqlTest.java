package org.dromara.djs.plant.indicator;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.job.DjsJobRegistry;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.common.job.service.IDjsJobLogService;
import org.dromara.djs.plant.dashboard.applet.mapper.AppletPlantManageDashboardMapper;
import org.dromara.djs.plant.dashboard.mapper.PlantDashboardMapper;
import org.dromara.djs.plant.indicator.job.PlantPlotSnapshotJob;
import org.dromara.djs.plant.indicator.mapper.PlantPlotSnapshotMapper;
import org.dromara.djs.plant.indicator.mapper.PlantProductionIndicatorMapper;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper;
import org.dromara.djs.plant.indicator.service.PlotSnapshotService;
import org.dromara.djs.plant.indicator.service.ProductionIndicatorService;
import org.dromara.djs.plant.perf.mapper.PlantWorkPerformanceMapper;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.mapper.PlotIdleIntervalMapper;
import org.dromara.djs.plant.plot.service.PlotIdleIntervalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("local")
@Tag("dev")
@EnabledIfEnvironmentVariable(named = "DJS_STAT_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:(3306|3316)/.*")
class ProductionIndicatorsMysqlTest {

    private static final LocalDate OCT = LocalDate.of(2025, 10, 1);
    private static final LocalDate NOV = OCT.plusMonths(1);
    private static final LocalDate DAY = NOV.plusDays(9);
    private static final String TENANT = "1001";
    private Connection connection;
    private SqlSession session;
    private ProductionIndicatorService service;
    private Consumer<LocalDate> captureSnapshot;
    private LocalDate businessDate;
    private PlotIdleIntervalService idleIntervals;
    private String initializeIdleHistory;
    private String backfillIdleHistory;

    @BeforeEach
    void setup() throws Exception {
        connection = DriverManager.getConnection(System.getenv("DJS_STAT_MYSQL_URL"),
            System.getenv("DJS_STAT_MYSQL_USER"), System.getenv("DJS_STAT_MYSQL_PASSWORD"));
        sql("""
            CREATE TEMPORARY TABLE t_plant_plant_details (
                id BIGINT, tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0',
                plant_id BIGINT, plot_id BIGINT, crop_id BIGINT, plant_month INT, plot_area DECIMAL(10,2),
                begin_actualdate DATE, end_actualdate DATE, begin_harvestdate DATE, end_harvestdate DATE,
                plant_status VARCHAR(16) DEFAULT 'completed', harvest_status VARCHAR(16) DEFAULT 'completed',
                actual_yield DECIMAL(12,3), expected_yield DECIMAL(12,3), rotated_at DATETIME, last_harvestdate DATE,
                transplant_adjusted TINYINT DEFAULT 0)
            """);
        sql("""
            CREATE TEMPORARY TABLE t_plant_plant_plan (id BIGINT, plan_year INT,
                tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_plant_plot_info (id BIGINT PRIMARY KEY, plot_area DECIMAL(10,2),
                plot_status INT DEFAULT 1, zone_id BIGINT,
                tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_plant_plot_zone (
                id BIGINT PRIMARY KEY, zone_status INT, tenant_id VARCHAR(20) DEFAULT '1001',
                del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_plant_farm_records (
                id BIGINT, plot_id BIGINT, crop_id BIGINT, plant_id BIGINT, farm_type VARCHAR(32), farm_date DATE, farm_by BIGINT,
                loss_rate DECIMAL(10,2), loss_yield DECIMAL(12,3), transplant_plot BIGINT,
                create_time DATETIME, tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_warehouse_stock_flow (
                id BIGINT, plot_id BIGINT, flow_no VARCHAR(32), product_id BIGINT, flow_date DATETIME, flow_type VARCHAR(32),
                inout_type VARCHAR(3), change_quantity DECIMAL(16,3), change_num DECIMAL(16,3),
                tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_warehouse_planting_record (
                id BIGINT PRIMARY KEY, crop_id BIGINT, tenant_id VARCHAR(20) DEFAULT '1001')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_plant_work_performance (
                stat_month CHAR(7), performance_amount DECIMAL(12,2), create_time DATETIME,
                tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_plant_plant_activity (
                id BIGINT, plot_id BIGINT, crop_id BIGINT, product_id BIGINT,
                activity_date DATE, pick_dest VARCHAR(32), daily_weight DECIMAL(12,3),
                tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);

        sql("""
            CREATE TEMPORARY TABLE t_warehouse_veg_receive (
                id BIGINT, receive_no VARCHAR(32), receive_type INT, plot_id BIGINT, crop_id BIGINT, product_id BIGINT,
                is_finish INT, receive_time DATETIME, weight DECIMAL(12,3), loss_weight DECIMAL(12,3), tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_warehouse_vegetable_handle (
                id BIGINT, planting_record_id BIGINT, send_platform_weight DECIMAL(12,3), picked_weight DECIMAL(12,3),
                is_weighed INT, tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_warehouse_handle_record (
                id BIGINT, handle_id BIGINT, plot_id BIGINT, crop_id BIGINT, product_id BIGINT, team_id BIGINT,
                record_type INT, handle_target INT, record_weight DECIMAL(12,3), perf_percent INT DEFAULT 100,
                handle_time DATETIME, remark VARCHAR(500),
                tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_warehouse_handle_record_team (
                record_id BIGINT, team_id BIGINT, tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_plant_crop_product (
                id BIGINT, crop_id BIGINT, product_id BIGINT, perf_price DECIMAL(10,2),
                tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_plant_crop_info (
                id BIGINT, tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_warehouse_product_info (
                id BIGINT, tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_plant_work_people (
                team_id BIGINT, user_id BIGINT, join_date DATE, create_time DATETIME, update_time DATETIME,
                tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);

        sql("""
            CREATE TEMPORARY TABLE t_plant_activity_team (
                activity_id BIGINT, team_id BIGINT, tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_plant_details_team (
                detail_id BIGINT, team_id BIGINT, role VARCHAR(16),
                tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);

        Path repo = Path.of("").toAbsolutePath();
        while (repo != null && !Files.isDirectory(repo.resolve("ruoyi-admin"))) {
            repo = repo.getParent();
        }
        assertThat(repo).as("后端仓库根目录").isNotNull();
        Path migrations = repo.resolve("ruoyi-admin/src/main/resources/db/migration");
        applyMigration(migrations.resolve("V202609301100__PLT-STAT-V11-production-indicators.sql"));
        applyMigration(migrations.resolve("V202609301130__PLT-STAT-V11-plot-idle-interval.sql"));
        applyMigration(migrations.resolve("V202609301700__PLT-STAT-V12-event-indicators.sql"));
        applyMigration(migrations.resolve("V202609301800__PLT-STAT-V12-plot-daily-snapshot.sql"));

        MybatisConfiguration config = new MybatisConfiguration(new Environment("local", new JdbcTransactionFactory(),
            new SingleConnectionDataSource(connection, true)));
        MybatisPlusInterceptor tenant = new MybatisPlusInterceptor();
        tenant.addInnerInterceptor(new TenantLineInnerInterceptor(() -> new StringValue(TENANT)));
        config.addInterceptor(tenant);
        config.addMapper(ProductionIndicatorSourceMapper.class);
        config.addMapper(PlantProductionIndicatorMapper.class);
        config.addMapper(PlantPlotSnapshotMapper.class);
        config.addMapper(PlotIdleIntervalMapper.class);
        config.addMapper(PlantDashboardMapper.class);
        config.addMapper(PlantDetailsMapper.class);
        config.addMapper(PlantWorkPerformanceMapper.class);
        config.addMapper(AppletPlantManageDashboardMapper.class);
        session = new SqlSessionFactoryBuilder().build(config).openSession();
        ZoneId shanghai = ZoneId.of(ProductionIndicatorService.BUSINESS_ZONE);
        businessDate = LocalDate.now(shanghai);
        InstantSource time = () -> businessDate.atTime(23, 50).atZone(shanghai).toInstant();
        var snapshots = new PlotSnapshotService(session.getMapper(PlantPlotSnapshotMapper.class), time);
        var registry = new DjsJobRegistry();
        new PlantPlotSnapshotJob(snapshots, registry).register();
        captureSnapshot = registry.get("plant-plot-snapshot");
        service = new ProductionIndicatorService(session.getMapper(ProductionIndicatorSourceMapper.class),
            session.getMapper(PlantProductionIndicatorMapper.class), snapshots);
        idleIntervals = new PlotIdleIntervalService(session.getMapper(PlotIdleIntervalMapper.class));
    }

    private void applyMigration(Path file) throws Exception {
        String migration = Files.readString(file).replace("CREATE TABLE ", "CREATE TEMPORARY TABLE ");
        for (String statement : migration.split(";")) {
            if (!statement.isBlank()) {
                sql(statement);
                if (statement.contains("INSERT INTO t_plant_plot_idle_interval")) {
                    initializeIdleHistory = statement;
                } else if (statement.contains("SET p.idle_open_interval_id = i.id")) {
                    backfillIdleHistory = statement;
                }
            }
        }
    }

    @AfterEach
    void close() throws Exception {
        if (session != null) {
            session.close();
        }
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void closingSnapshotCountsScopedPlotsAndCropsAtMonthEnd() throws Exception {
        LocalDate close = LocalDate.of(2025, 11, 30);
        businessDate = close;
        sql("INSERT INTO t_plant_plot_zone (id, zone_status, del_flag) VALUES (1,1,'0'),(2,0,'0'),(3,1,'1')");
        sql("""
            INSERT INTO t_plant_plot_info (id, zone_id, plot_status, plot_area, del_flag) VALUES
                (10,1,1,2.50,'0'),(20,1,2,5,'0'),(30,1,3,3,'0'),
                (40,1,3,1,'0'),(41,1,3,1,'0'),(42,1,3,1,'0'),(43,1,3,1,'0'),(44,1,3,1,'0'),
                (45,1,3,1,'0'),(46,1,3,1,'0'),(47,1,3,1,'0'),(48,1,3,1,'0'),(49,1,3,1,'0'),
                (60,2,1,100,'0'),(61,1,1,100,'1'),(62,3,1,100,'0')
            """);
        sql("INSERT INTO t_plant_plot_info (id, zone_id, plot_status, plot_area, tenant_id) VALUES (63,1,1,100,'2002')");
        sql("INSERT INTO t_plant_plot_zone (id, zone_status, tenant_id) VALUES (4,1,'2002')");
        sql("INSERT INTO t_plant_plot_info (id, zone_id, plot_status, plot_area) VALUES (64,4,1,100)");
        sql("""
            INSERT INTO t_plant_plant_details (id, plot_id, crop_id, harvest_status, last_harvestdate, tenant_id)
            VALUES (100,30,999,'pending','2025-11-25','2002')
            """);
        sql("""
            INSERT INTO t_plant_plant_details
                (id, plot_id, crop_id, plant_status, harvest_status, last_harvestdate, rotated_at, del_flag) VALUES
                (1,20,111,'completed','pending','2025-11-29',NULL,'0'),
                (2,20,222,'ongoing','pending','2025-11-30',NULL,'0'),
                (3,20,111,'completed','picking','2025-11-30',NULL,'0'),
                (4,30,111,'completed','picking','2025-11-30',NULL,'0'),
                (5,20,333,'pending','pending','2025-11-23',NULL,'0'),
                (6,30,444,'completed','completed','2025-11-20','2025-11-25 18:00:00','0'),
                (7,20,555,'completed','pending','2025-11-20',NULL,'1'),
                (8,10,666,'completed','completed','2025-11-20',NULL,'0'),
                (9,30,777,'completed','pending','2025-11-20','2025-11-25 18:00:00','0')
            """);
        sql("""
            INSERT INTO t_plant_plant_details (id, plot_id, crop_id, harvest_status, end_harvestdate, last_harvestdate)
            VALUES (40,40,900,'completed','2025-11-29','2025-11-20'),
                (41,41,900,'completed','2025-11-29','2025-11-20'),(42,42,900,'completed','2025-11-29','2025-11-20'),
                (43,43,900,'completed','2025-11-29','2025-11-20'),(44,44,900,'completed','2025-11-29','2025-11-20'),
                (45,45,900,'completed','2025-11-29','2025-11-20'),(46,46,900,'completed','2025-11-29','2025-11-20'),
                (47,47,900,'completed','2025-11-30','2025-11-20'),(48,48,900,'completed','2025-11-30','2025-11-20'),
                (49,49,900,'completed','2025-11-30','2025-11-20')
            """);
        captureSnapshot.accept(close);
        assertThat(queryLong("SELECT id FROM t_plant_plot_snapshot_day")).isGreaterThan(9_007_199_254_740_991L);
        assertThat(queryLong("SELECT MIN(id) FROM t_plant_plot_status_daily")).isGreaterThan(9_007_199_254_740_991L);
        try (var query = connection.prepareStatement("""
            SELECT plot_id FROM t_plant_plot_status_daily WHERE snap_date = ? ORDER BY plot_id
            """)) {
            query.setObject(1, close);
            try (var rows = query.executeQuery()) {
                List<Long> captured = new ArrayList<>();
                while (rows.next()) {
                    captured.add(rows.getLong(1));
                }
                assertThat(captured).containsExactly(10L, 20L, 30L, 40L, 41L, 42L, 43L, 44L, 45L, 46L, 47L, 48L, 49L);
            }
        }
        businessDate = close.plusDays(1);
        service.recalculate(close, close);
        session.clearCache();
        var report = service.query(close, close);
        var day = report.daily().getFirst();
        var month = report.monthly().getFirst();
        assertAll(
            () -> assertThat(day.idlePlotCount()).as("种植 3").isEqualTo(1),
            () -> assertThat(day.idleArea()).as("种植 4").isEqualByComparingTo("2.50"),
            () -> assertThat(day.inFieldArea()).as("种植 8").isEqualByComparingTo("8"),
            () -> assertThat(day.inFieldCropCount()).as("种植 9，同一地块两种，跨地同种去重").isEqualTo(2),
            () -> assertThat(day.inFieldPlotCount()).as("种植 10").isEqualTo(2),
            () -> assertThat(day.totalPlotCount()).as("种植 11，排除停用片区、软删及跨租户地块").isEqualTo(13),
            () -> assertThat(day.overdueHarvestPlotCount()).as("种植 18，未开采也计，最晚日当天不计").isEqualTo(1),
            () -> assertThat(day.pendingRotationPlotCount()).as("种植 20，7 块加当日完采 3 块").isEqualTo(10),
            () -> assertThat(month.idlePlotCount()).as("种植 26").isEqualTo(1),
            () -> assertThat(month.plotUtilizationPercent()).as("种植 30，待退茬计为利用").isEqualByComparingTo("92.31"),
            () -> assertThat(month.overdueHarvestPlotCount()).as("种植 39").isEqualTo(1),
            () -> assertThat(month.inFieldArea()).as("种植 45").isEqualByComparingTo("8"),
            () -> assertThat(month.inFieldPlotCount()).as("种植 46").isEqualTo(2),
            () -> assertThat(month.inFieldCropCount()).as("种植 47").isEqualTo(2),
            () -> assertThat(month.idleArea()).as("种植 48").isEqualByComparingTo("2.50"),
            () -> assertThat(month.pendingRotationPlotCount()).as("种植 50").isEqualTo(10)
        );
    }

    @Test
    void sameDayRecaptureOverwritesAndHistoricalRerunDoesNotCapture() throws Exception {
        LocalDate close = DAY;
        businessDate = close;
        sql("INSERT INTO t_plant_plot_zone (id, zone_status) VALUES (1,1),(2,1)");
        sql("INSERT INTO t_plant_plot_info (id, zone_id, plot_status, plot_area) VALUES (10,1,1,2),(20,2,1,3)");
        captureSnapshot.accept(close);
        long snapshotId = queryLong("SELECT id FROM t_plant_plot_snapshot_day");
        sql("UPDATE t_plant_plot_zone SET zone_status=0 WHERE id=2");
        sql("UPDATE t_plant_plot_info SET plot_area=4 WHERE id=10");
        captureSnapshot.accept(close);

        businessDate = close.plusDays(2);
        sql("UPDATE t_plant_plot_info SET plot_area=99 WHERE id=10");
        var audit = mock(IDjsJobLogService.class);
        String job = PlantPlotSnapshotJob.JOB_NAME;
        String manual = DjsJobRunner.TRIGGER_MANUAL;
        List<LocalDate> pastDates = List.of(close.minusDays(1), close, close.plusDays(1));
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(IDjsJobLogService.class)).thenReturn(audit);
            for (LocalDate pastDate : pastDates) {
                long logId = pastDate.toEpochDay();
                when(audit.recordStart(job, pastDate, manual)).thenReturn(logId);
                DjsJobRunner.runForDate(job, TENANT, pastDate, manual, captureSnapshot);
                verify(audit).recordFail(eq(logId), anyLong(), eq("过去日期不补拍快照，已跳过"));
            }
        }
        verify(audit, never()).recordDone(anyLong(), anyLong());
        captureSnapshot.accept(businessDate.plusDays(1));
        assertThat(queryLong("SELECT COUNT(*) FROM t_plant_plot_snapshot_day")).isEqualTo(1);
        assertThat(queryLong("SELECT id FROM t_plant_plot_snapshot_day")).isEqualTo(snapshotId);
        assertThat(queryLong("SELECT COUNT(*) FROM t_plant_plot_status_daily")).isEqualTo(1);

        service.recalculate(close.minusDays(1), close.plusDays(1));
        var report = service.query(close.minusDays(1), close.plusDays(1));
        var day = report.daily().get(1);
        assertThat(day.totalPlotCount()).as("种植 11，同一天重拍去掉停用片区").isEqualTo(1);
        assertThat(day.idleArea()).as("种植 4，保留当天第二次拍摄的 4 亩").isEqualByComparingTo("4");
        for (int index : List.of(0, 2)) {
            var missing = report.daily().get(index);
            assertThat(Arrays.asList(missing.idlePlotCount(), missing.idleArea(), missing.inFieldArea(),
                missing.inFieldCropCount(), missing.inFieldPlotCount(), missing.totalPlotCount(),
                missing.overdueHarvestPlotCount(), missing.pendingRotationPlotCount()))
                .as("种植 3、4、8–11、18、20，无快照日报").containsOnlyNulls();
        }
        assertThat(report.monthly().getFirst().idleArea()).as("种植 48，当前月回取最新已有快照").isEqualByComparingTo("4");
        assertThat(report.annual().getFirst().averagePlantingsPerPlot()).as("种植 54，当年有快照、零次种植")
            .isEqualByComparingTo("0.00");

        businessDate = close.plusMonths(1);
        service.recalculate(close, close);
        var month = service.query(close, close).monthly().getFirst();
        assertThat(Arrays.asList(month.idlePlotCount(), month.plotUtilizationPercent(),
            month.overdueHarvestPlotCount(), month.inFieldArea(), month.inFieldPlotCount(), month.inFieldCropCount(),
            month.idleArea(), month.pendingRotationPlotCount()))
            .as("种植 26、30、39、45–48、50，已结束月缺月底快照").containsOnlyNulls();
    }

    @Test
    void openMonthWithoutItsOwnSnapshotKeepsStockMetricsNull() throws Exception {
        LocalDate priorMonthEnd = NOV.minusDays(1);
        businessDate = priorMonthEnd;
        sql("INSERT INTO t_plant_plot_zone (id, zone_status) VALUES (1,1)");
        sql("INSERT INTO t_plant_plot_info (id, zone_id, plot_area) VALUES (10,1,2)");
        captureSnapshot.accept(priorMonthEnd);

        businessDate = NOV.plusDays(1);
        service.recalculate(priorMonthEnd, NOV);
        var months = service.query(priorMonthEnd, NOV).monthly();
        assertThat(months.getFirst().idleArea()).isEqualByComparingTo("2");
        var openMonth = months.getLast();
        assertThat(Arrays.asList(openMonth.idlePlotCount(), openMonth.plotUtilizationPercent(),
            openMonth.overdueHarvestPlotCount(), openMonth.inFieldArea(), openMonth.inFieldPlotCount(),
            openMonth.inFieldCropCount(), openMonth.idleArea(), openMonth.pendingRotationPlotCount()))
            .as("种植 26、30、39、45–48、50，当前月没有本月快照").containsOnlyNulls();
    }

    @Test
    void crossingBusinessMidnightRollsBackSnapshotDay() throws Exception {
        LocalDate close = DAY;
        sql("INSERT INTO t_plant_plot_zone (id, zone_status) VALUES (1,1)");
        sql("INSERT INTO t_plant_plot_info (id, zone_id, plot_area) VALUES (10,1,2)");
        var dataSource = new SingleConnectionDataSource(connection, true);
        var config = new MybatisConfiguration(new Environment("snapshot-tx", new SpringManagedTransactionFactory(), dataSource));
        config.addMapper(PlantPlotSnapshotMapper.class);
        var template = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        var time = mock(InstantSource.class);
        Instant beforeMidnight = close.atTime(23, 59).atZone(ZoneId.of(ProductionIndicatorService.BUSINESS_ZONE)).toInstant();
        when(time.instant()).thenReturn(beforeMidnight, beforeMidnight).thenAnswer(invocation -> {
            assertThat(queryLong("SELECT COUNT(*) FROM t_plant_plot_snapshot_day")).isEqualTo(1);
            return beforeMidnight.plusSeconds(60);
        });
        var target = new PlotSnapshotService(template.getMapper(PlantPlotSnapshotMapper.class), time);
        var transaction = new TransactionInterceptor();
        transaction.setTransactionManager(new DataSourceTransactionManager(dataSource));
        transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(transaction);
        var snapshots = (PlotSnapshotService) proxy.getProxy();

        assertThatThrownBy(() -> snapshots.capture(close)).isInstanceOf(ServiceException.class)
            .hasMessage("已跨业务日，取消收工快照");
        assertThat(queryLong("SELECT COUNT(*) FROM t_plant_plot_snapshot_day")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM t_plant_plot_status_daily")).isZero();
    }

    @Test
    void annualPlantingRoundsUseLatestOrExactYearEndDenominator() throws Exception {
        LocalDate december = LocalDate.of(2025, 12, 31);
        businessDate = december.minusDays(2);
        sql("INSERT INTO t_plant_plot_zone (id, zone_status) VALUES (1,1)");
        sql("INSERT INTO t_plant_plot_info (id, zone_id, plot_area) VALUES (10,1,2),(20,1,3)");
        sql("INSERT INTO t_plant_plant_plan (id, plan_year) VALUES (1,2025)");
        detail(1, 1, 10, 111, 2, 2, "2025-02-04", "2025-02-05", null, null);
        detail(2, 1, 10, 111, 3, 2, "2025-03-04", "2025-03-05", null, null);
        detail(3, 1, 20, 111, 12, 2, "2025-12-04", null, null, null);
        captureSnapshot.accept(businessDate);

        LocalDate rerun = december.minusDays(1);
        businessDate = december;
        service.recalculate(rerun, rerun);
        assertThat(service.query(rerun, rerun).annual().getFirst().averagePlantingsPerPlot())
            .as("种植 54，当年用最近快照，同一块种两次除以 2").isEqualByComparingTo("1.00");

        businessDate = december.plusDays(1);
        service.recalculate(december, december);
        assertThat(service.query(december, december).annual().getFirst().averagePlantingsPerPlot())
            .as("种植 54，已有 12-29 但缺 12-31，上一年仍为空").isNull();
    }

    @Test
    void yearEndSnapshotUsesSameDayCompletionsAndStaysFixedAfterYearCloses() throws Exception {
        LocalDate december = LocalDate.of(2025, 12, 31);
        businessDate = december.minusDays(2);
        sql("INSERT INTO t_plant_plot_zone (id, zone_status) VALUES (1,1)");
        sql("INSERT INTO t_plant_plot_info (id, zone_id, plot_area) VALUES (10,1,2),(20,1,3)");
        captureSnapshot.accept(businessDate);
        businessDate = december;
        sql("INSERT INTO t_plant_plot_info (id, zone_id, plot_area) VALUES (30,1,5)");
        sql("INSERT INTO t_plant_plant_plan (id, plan_year) VALUES (1,2025),(2,2024)");
        detail(1, 1, 10, 111, 2, 2, "2025-02-04", "2025-02-05", null, null);
        detail(2, 2, 10, 111, 12, 2, "2025-12-31", "2025-12-31", null, null);
        LocalDate priorDay = december.minusDays(1);
        service.recalculate(priorDay, priorDay);
        assertThat(service.query(priorDay, priorDay).annual().getFirst().averagePlantingsPerPlot())
            .as("种植 54，最新快照早于今天，1 次完工 ÷ 2 块地").isEqualByComparingTo("0.50");

        captureSnapshot.accept(december);
        service.recalculate(priorDay, priorDay);
        var openYear = service.query(priorDay, priorDay);
        assertThat(openYear.annual().getFirst().averagePlantingsPerPlot()).as("种植 54，最新快照当天完工也计，2 ÷ 3")
            .isEqualByComparingTo("0.67");
        assertThat(openYear.monthly().getFirst().idleArea()).as("种植 48，当月已有当天快照取当天").isEqualByComparingTo("10");
        assertThat(openYear.monthly().getFirst().plantedPlotCount()).as("种植 29，P1 事件仍统计到昨天").isZero();

        businessDate = december.plusDays(2);
        sql("UPDATE t_plant_plot_zone SET zone_status=0 WHERE id=1");
        captureSnapshot.accept(businessDate);
        service.recalculate(december, december);
        assertThat(service.query(december, december).annual().getFirst().averagePlantingsPerPlot())
            .as("种植 54，上一年只取 12-31 三块地，后续片区停用不改变分母").isEqualByComparingTo("0.67");
    }

    @Test
    void capturedZeroPlotsDiffersFromMissingSnapshot() throws Exception {
        LocalDate december = LocalDate.of(2025, 12, 31);
        businessDate = december;
        sql("INSERT INTO t_plant_plot_zone (id, zone_status) VALUES (1,1)");
        sql("INSERT INTO t_plant_plot_info (id, zone_id, plot_area) VALUES (10,1,2)");
        captureSnapshot.accept(december);
        sql("UPDATE t_plant_plot_zone SET zone_status=0 WHERE id=1");
        captureSnapshot.accept(december);
        businessDate = december.plusDays(1);

        service.recalculate(december, december);
        var report = service.query(december, december);
        assertThat(report.daily().getFirst().totalPlotCount()).as("种植 11，采集成功且有效地块为 0").isZero();
        assertThat(report.daily().getFirst().inFieldCropCount()).as("种植 9，零地块零作物").isZero();
        assertThat(report.monthly().getFirst().plotUtilizationPercent()).as("种植 30，零分母").isEqualByComparingTo("0.00");
        assertThat(report.annual().getFirst().averagePlantingsPerPlot()).as("种植 54，年末已有快照，零分母")
            .isEqualByComparingTo("0.00");
    }

    @Test
    void plantingAndHarvestCompletionUseActualDatesAndRoundGrain() throws Exception {
        sql("INSERT INTO t_plant_plant_plan (id, plan_year) VALUES (1,2025),(2,2024)");
        detail(1, 1, 10, 111, 11, 5, "2025-10-29", "2025-11-10", "2025-11-02", "2025-11-10");
        detail(2, 1, 10, 111, 11, 5, "2025-11-01", "2025-11-10", "2025-11-03", "2025-11-10");
        detail(3, 1, 20, 111, 10, 2, "2025-11-01", "2025-11-10", "2025-11-10", null);
        detail(4, 1, 30, 222, 11, 9, "2025-11-10", "2025-12-02", null, null);
        detail(5, 2, 40, 333, 11, 8, null, null, null, null);
        detail(6, 2, 50, 444, 12, 3, "2025-12-01", "2025-12-02", null, null);
        detail(7, 1, 60, 666, 12, 3, null, null, null, null);
        detail(8, 1, 80, 888, 11, 99, "2025-11-01", "2025-11-10", null, null);
        sql("UPDATE t_plant_plant_details SET del_flag='1' WHERE id=8");
        sql("UPDATE t_plant_plant_details SET plant_status='pending' WHERE id IN (5,7)");
        sql("UPDATE t_plant_plant_details SET harvest_status='picking' WHERE id=3");
        service.recalculate(DAY, DAY);
        var report = service.query(DAY, DAY);
        var day = report.daily().getFirst();
        var month = report.monthly().getFirst();
        var year = report.annual().getFirst();
        assertThat(day.plantedArea()).as("种植 5 同地两茬仍计 5 亩，软删明细排除").isEqualByComparingTo("7");
        assertThat(day.plantedCropCount()).isEqualTo(1); // 种植 6
        assertThat(day.plantedPlotCount()).isEqualTo(2); // 种植 7
        assertThat(day.harvestFinishedPlotCount()).isEqualTo(1); // 种植 16：续采记录不计
        assertThat(month.plantedArea()).isEqualByComparingTo("12"); // 种植 27：同地两茬 10 亩次 + 2
        assertThat(month.plantedCropCount()).isEqualTo(1); // 种植 28
        assertThat(month.plantedPlotCount()).isEqualTo(3); // 种植 29
        assertThat(month.plantPlanCompletedCount()).isEqualTo(2); // 种植 31：上月计划补种不进分子
        assertThat(month.plantPlanCount()).isEqualTo(3); // 种植 31：跨月完成仍保留在分母
        assertThat(month.harvestFinishedPlotCount()).isEqualTo(2); // 种植 35
        assertThat(year.plantedArea()).isEqualByComparingTo("24"); // 种植 53：包括上年计划本年补种
        assertThat(year.plantPlanCompletedCount()).isEqualTo(4); // 种植 56
        assertThat(year.plantPlanCount()).isEqualTo(5); // 种植 56：4/5，上年补种不增加分子
        assertThat(year.getPlantPlanCompletionPercent()).isEqualByComparingTo("80");
        assertThat(year.getIdleAverageDays()).isEqualByComparingTo("0");
        var realtime = session.getMapper(PlantDetailsMapper.class)
            .selectSeedSummary(TENANT, 2025, 11, NOV, NOV.plusMonths(1));
        assertThat(realtime.getTodayCropKindCount()).isEqualTo(1); // 种植 28：剔除晚完和其他计划年
        assertThat(realtime.getTodayPlotCount()).isEqualTo(3); // 种植 29：计两茬且纳入上月补种
        assertThat(realtime.getMonthCompletionRate()).isEqualTo(67); // 种植 31：2/3，旧口径会纳入晚完
    }

    @Test
    void farmRotationAndDisasterUseBusinessDayAndPairOnce() throws Exception {
        sql("INSERT INTO t_plant_plot_info (id, plot_area) VALUES (10,5),(20,5),(30,1),(40,1),(50,1)");
        detail(10, 1, 10, 111, 8, 5, "2025-08-01", "2025-08-02", "2025-11-01", "2025-11-04");
        detail(11, 1, 20, 112, 8, 5, "2025-08-01", "2025-08-02", "2025-11-01", "2025-11-08");
        detail(12, 1, 10, 113, 8, 5, "2025-08-01", "2025-08-02", "2025-11-01", "2025-11-11");
        detail(13, 1, 30, 114, 8, 1, "2025-08-01", "2025-08-02", "2025-11-01", "2025-11-07");
        detail(14, 1, 30, 115, 8, 1, "2025-08-01", "2025-08-02", "2025-11-01", "2025-11-08");
        sql("UPDATE t_plant_plant_details SET rotated_at = '2025-12-01'");
        farm(1, 10, "rotation", DAY, 10L);
        farm(2, 20, "rotation", DAY, null);
        farm(3, 10, "rotation", DAY, 10L); // 重复关联
        farm(4, 10, "rotation", DAY.plusDays(5), 12L); // 同地另一茬
        farm(5, 40, "rotation", DAY.plusDays(1), null); // 无配对
        farm(6, 30, "rotation", DAY.plusDays(1), null); // 最近完采者优先
        farm(7, 30, "rotation", DAY.plusDays(2), null); // 不能重复占用上条配对
        farm(8, 10, "tillage_break", DAY.minusDays(1), null);
        farm(9, 10, "tillage_prepare", DAY.minusDays(1), null);
        farm(10, 10, "harvest_activity", DAY.minusDays(1), null);
        farm(11, 10, "pest_control", DAY.minusDays(1), null);
        farm(12, 10, "disaster", DAY, null);
        farm(13, 10, "disaster", DAY, null);
        farm(14, 30, "disaster", OCT, null);
        farm(15, 40, "disaster", OCT, null);
        farm(16, 50, "disaster", OCT, null);
        farm(17, 10, "harvest", DAY.minusDays(1), null);
        farm(18, 10, "pruning", DAY.minusDays(1), null);
        sql("UPDATE t_plant_farm_records SET del_flag='1' WHERE id=18");
        service.recalculate(DAY.minusDays(1), DAY.plusDays(5));
        var report = service.query(DAY.minusDays(1), DAY.plusDays(5));
        var day = report.daily().get(1);
        var month = report.monthly().getFirst();
        var year = report.annual().getFirst();
        assertAll(
            () -> assertThat(report.daily().getFirst().farmWorkArea()).as("种植 12 农事作业面积")
                .isEqualByComparingTo("15"),
            () -> assertThat(report.daily().getFirst().farmWorkCount()).as("种植 13 农事完成数")
                .isEqualTo(3),
            () -> assertThat(month.farmWorkCount()).as("种植 32 月农事总数").isEqualTo(10),
            () -> assertThat(session.getMapper(PlantWorkPerformanceMapper.class)
                .countFarmByTeamMonths(List.of(77L), List.of("2025-11")))
                .singleElement().satisfies(row -> assertThat(row.getCnt()).as("种植 32 班组农事总数").isEqualTo(10)));
        assertThat(day.rotationPlotCount()).isEqualTo(2); // 种植 19：次日补录仍归业务日
        assertThat(day.rotationArea()).isEqualByComparingTo("10"); // 种植 21：同日重复单不加面积
        assertThat(day.rotationWaitDaysSum()).isEqualTo(8); // 种植 22：6 + 2
        assertThat(day.rotationWaitValidCount()).isEqualTo(2); // 种植 23：8 / 2 = 4
        assertThat(day.getRotationWaitAverageDays()).isEqualByComparingTo("4");
        assertThat(day.disasterPlotCount()).isEqualTo(1); // 种植 24
        assertThat(month.farmWorkArea()).isEqualByComparingTo("38"); // 种植 33
        assertThat(month.rotationPlotCount()).isEqualTo(6); // 种植 40：同地跨茬分别计
        assertThat(month.rotationArea()).isEqualByComparingTo("18"); // 种植 49
        assertThat(year.disasterRecordCount()).isEqualTo(5); // 种植 59：单灾三地三笔 + 另两笔
        assertThat(year.rotationPlotCount()).isEqualTo(6); // 种植 60
        assertThat(report.daily().get(2).rotationUnmatchedCount()).isEqualTo(1);
        assertThat(report.daily().get(2).rotationWaitDaysSum()).isEqualTo(3);
        assertThat(report.daily().get(3).rotationWaitDaysSum()).isEqualTo(5);
        assertThat(month.rotationUnmatchedCount()).isEqualTo(1);
    }

    @Test
    void inboundAndPerformanceFollowTheirOwnBusinessPeriods() throws Exception {
        detail(20, 1, 10, 111, 9, 5, "2025-09-01", "2025-09-02", "2025-10-01", "2026-01-10");
        detail(21, 1, 20, 222, 9, 5, "2025-09-01", "2025-09-02", null, null);
        detail(22, 1, 20, 333, 9, 5, "2025-09-01", "2025-09-02", null, null);
        flow(1, 10, "2025-10-31 23:59:59", "veg_stock_in", "5", null);
        flow(2, 10, "2025-11-10 00:00:00", "veg_stock_in", "8", 111L);
        flow(3, 10, "2025-11-10 23:59:59", "veg_stock_in", "12", null);
        flow(4, 10, "2025-11-11 00:00:00", "veg_stock_in", "9", 111L);
        flow(5, 20, "2025-11-11 08:00:00", "veg_stock_in", "3", null);
        flow(6, 99, "2025-11-10 12:00:00", "visitor_sale", "77", 999L);
        flow(7, 99, "2025-11-10 12:00:00", "veg_stock_in", "0", 999L);
        flow(8, 10, "2026-01-01 00:00:00", "veg_stock_in", "27", 111L);
        flow(9, 99, "2025-11-11 08:00:00", "veg_stock_in", "98", 999L);
        flow(10, 10, "2025-11-10 12:00:00", "veg_stock_in", "101", 111L);
        sql("UPDATE t_warehouse_stock_flow SET del_flag='1' WHERE id=10");
        sql("UPDATE t_warehouse_stock_flow SET tenant_id='2001' WHERE id=9");
        sql("UPDATE t_plant_plant_details SET actual_yield=900");
        sql("""
            INSERT INTO t_plant_work_performance (stat_month, performance_amount, create_time)
            VALUES ('2025-10',120,'2025-11-05'),('2025-11',80,'2025-12-05'),('2026-01',7,'2026-02-05')
            """);
        service.recalculate(OCT.plusDays(30), LocalDate.of(2026, 1, 1));
        var beforeCorrection = service.query(DAY, DAY);
        assertThat(beforeCorrection.daily().getFirst().harvestInboundCropCount()).isEqualTo(1); // 种植 15
        assertThat(beforeCorrection.monthly().getFirst().harvestInboundCropCount()).isEqualTo(1); // 种植 36
        sql("UPDATE t_warehouse_stock_flow SET crop_id=112 WHERE id IN (2,4)");
        service.recalculate(DAY, DAY); // 更正作物触发回刷，日行不重复，月/年刷新完整期
        var report = service.query(OCT.plusDays(30), LocalDate.of(2026, 1, 1));
        var day = report.daily().stream().filter(row -> row.statDate().equals(DAY)).findFirst().orElseThrow();
        var october = report.monthly().get(0);
        var november = report.monthly().get(1);
        var year = report.annual().getFirst();
        assertAll(
            () -> assertThat(day.harvestInboundWeight()).as("种植 14 日入库 kg").isEqualByComparingTo("20"),
            () -> assertThat(november.harvestInboundWeight()).as("种植 34 月入库 kg").isEqualByComparingTo("32"),
            () -> assertThat(year.harvestInboundWeight()).as("种植 57 年入库 kg").isEqualByComparingTo("37"));
        assertThat(day.harvestInboundCropCount()).isEqualTo(2); // 种植 15：直接键更正，另一条历史推断为 111
        assertThat(day.harvestInboundPlotCount()).isEqualTo(1); // 种植 17
        assertThat(october.harvestInboundWeight()).isEqualByComparingTo("5"); // 种植 34
        assertThat(november.harvestInboundCropCount()).isEqualTo(2); // 种植 36：112 连续两天仍只计一次
        assertThat(october.harvestInboundPlotCount()).isEqualTo(1); // 种植 37
        assertThat(november.harvestInboundPlotCount()).isEqualTo(2); // 种植 37
        assertThat(october.harvestPerformanceAmount()).isEqualByComparingTo("120"); // 种植 43
        assertThat(report.annual().get(1).harvestInboundWeight()).isEqualByComparingTo("27"); // 种植 57 跨年
        assertThat(year.harvestPerformanceAmount()).isEqualByComparingTo("200"); // 种植 61
        assertThat(year.performanceThroughMonth()).isEqualTo("2025-11"); // 种植 61
        assertThat(day.harvestCropUnmatchedCount()).isZero();
        assertThat(report.daily().stream().filter(row -> row.statDate().equals(DAY.plusDays(1))).findFirst()
            .orElseThrow().harvestCropUnmatchedCount()).isEqualTo(1);
        assertThat(november.harvestCropUnmatchedCount()).isEqualTo(1);
        assertThat(report.daily()).hasSize(63);
        assertThat(session.getMapper(AppletPlantManageDashboardMapper.class).sumHarvestedYieldByYear(TENANT, 2025))
            .isEqualByComparingTo("37"); // 种植 57：明细旧重量为 900 kg
    }

    @Test
    void associationKeysWinAndAmbiguousHistoryStaysUnmatched() throws Exception {
        detail(1, 1, 10, 111, 9, 5, "2025-09-01", "2025-09-02", "2025-11-01", "2025-11-08");
        detail(2, 1, 10, 222, 9, 5, "2025-09-01", "2025-09-02", "2025-11-01", "2025-11-08");
        detail(3, 1, 20, 333, 9, 5, "2025-09-01", "2025-09-02", "2025-11-01", "2025-11-07");
        farm(1, 10, "rotation", DAY, null); // 最近完采有两条同日候选
        farm(2, 20, "rotation", DAY, null); // 唯一候选已被后续精确键占用
        farm(3, 20, "rotation", DAY.plusDays(2), 3L);
        sql("INSERT INTO t_warehouse_planting_record (id,crop_id) VALUES (71,444)");
        flow(1, 10, "2025-11-10 12:00:00", "veg_stock_in", "9", null);
        sql("UPDATE t_warehouse_stock_flow SET harvest_batch_id=71, change_num=9, change_quantity=NULL WHERE id=1");

        service.recalculate(DAY, DAY.plusDays(2));
        var report = service.query(DAY, DAY.plusDays(2));
        var day = report.daily().getFirst();
        assertThat(day.rotationWaitValidCount()).isZero();
        assertThat(day.rotationUnmatchedCount()).isEqualTo(2);
        assertThat(report.daily().getLast().rotationWaitDaysSum()).isEqualTo(5);
        assertThat(day.harvestInboundCropCount()).isEqualTo(1);
        assertThat(day.harvestCropUnmatchedCount()).isZero();
        assertThat(day.harvestInboundWeight()).isEqualByComparingTo("9");
    }

    @Test
    void realtimeDailyCardsUseCompletionAndActualInbound() throws Exception {
        LocalDate today;
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT CURDATE()")) {
            result.next();
            today = result.getDate(1).toLocalDate();
        }
        String yesterday = today.minusDays(1).toString();
        detail(1, 1, 10, 111, 11, 5, yesterday, today.toString(), yesterday, today.toString());
        detail(2, 1, 10, 111, 11, 5, yesterday, today.toString(), yesterday, today.toString());
        detail(3, 1, 20, 111, 11, 5, today.toString(), null, today.toString(), null);
        detail(4, 1, 30, 111, 11, 5, today.toString(), null, today.toString(), null);
        flow(1, 10, today + " 15:00:00", "veg_stock_in", "12", 111L);
        flow(2, 10, yesterday + " 23:59:59", "veg_stock_in", "34", 111L);
        sql("INSERT INTO t_plant_plant_activity (activity_date,pick_dest,daily_weight) VALUES (?, 'sale', 99)",
            today);
        PlantDashboardMapper board = session.getMapper(PlantDashboardMapper.class);
        assertThat(board.countTodayPlanting(TENANT)).isEqualTo(1); // 种植 7：旧值 2
        assertThat(board.countTodayHarvest(TENANT)).isEqualTo(1); // 种植 16：旧值 2
        assertThat(board.selectTodayPickActivityWeight(TENANT)).isEqualByComparingTo("12"); // 种植 14：旧值 99
    }

    @Test
    void annualIdleAverageCountsBothThreeDayIntervalsOnSamePlot() throws Exception {
        sql("""
            INSERT INTO t_plant_plot_idle_interval (id, plot_id, idle_started_on, idle_ended_on, start_source)
            VALUES (1,10,'2025-08-01','2025-08-04','test'), (2,10,'2025-10-01','2025-10-04','test'),
                   (3,20,'2025-08-01','2025-08-01','test')
            """);
        service.recalculate(DAY, DAY);
        var year = service.query(DAY, DAY).annual().getFirst();
        assertThat(year.idleDaysSum()).isEqualTo(6);
        assertThat(year.idleIntervalCount()).isEqualTo(2);
        assertThat(year.getIdleAverageDays()).isEqualByComparingTo("3");
    }

    @Test
    void annualIdleClipsCrossYearAndOpenIntervals() throws Exception {
        sql("""
            INSERT INTO t_plant_plot_idle_interval (id, plot_id, idle_started_on, idle_ended_on, start_source)
            VALUES (1,10,'2024-12-28','2025-01-03','test'), (2,20,'2025-12-30',NULL,'test'),
                   (3,30,'2024-12-30','2025-01-01','test')
            """);
        service.recalculate(DAY, DAY);
        var year = service.query(DAY, DAY).annual().getFirst();
        assertThat(year.idleDaysSum()).isEqualTo(4);
        assertThat(year.idleIntervalCount()).isEqualTo(2);
    }

    @Test
    void annualIdleIncludesTheRequestedReportDayForCurrentYear() throws Exception {
        LocalDate yearStart = LocalDate.now(ZoneId.of(ProductionIndicatorService.BUSINESS_ZONE)).withDayOfYear(1);
        sql("INSERT INTO t_plant_production_indicator_annual (id, stat_year) VALUES (1,?)", yearStart.getYear());
        sql("""
            INSERT INTO t_plant_plot_idle_interval (id, plot_id, idle_started_on, start_source)
            VALUES (1,10,?,'test')
            """, yearStart);
        var year = service.query(yearStart, yearStart).annual().getFirst();
        assertThat(year.idleDaysSum()).isEqualTo(1);
        assertThat(year.idleIntervalCount()).isEqualTo(1);
    }

    @Test
    void historySeedUsesLatestRotationWithFloorAndCanBeRepeated() throws Exception {
        sql("""
            INSERT INTO t_plant_plot_info (id, plot_status, tenant_id, del_flag)
            VALUES (10,1,'1001','0'),(20,1,'1001','0'),(30,1,'1001','0'),
                   (40,2,'1001','0'),(50,1,'1001','1'),(60,1,'2001','0')
            """);
        farm(1, 10, "rotation", LocalDate.of(2026, 7, 30), null);
        farm(2, 20, "rotation", LocalDate.of(2026, 8, 3), null);
        farm(3, 20, "rotation", LocalDate.of(2026, 8, 4), null);
        farm(4, 20, "rotation", LocalDate.of(2026, 8, 5), null);
        sql("UPDATE t_plant_farm_records SET del_flag='1' WHERE id=4");
        farm(5, 60, "rotation", LocalDate.of(2026, 8, 5), null);
        sql("UPDATE t_plant_farm_records SET tenant_id='2001' WHERE id=5");
        sql(initializeIdleHistory);
        sql(backfillIdleHistory);
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
            SELECT idle_open_interval_id FROM t_plant_plot_info ORDER BY id
            """)) {
            List<Long> pointers = new ArrayList<>();
            while (rows.next()) {
                pointers.add(rows.getObject(1, Long.class));
            }
            assertThat(pointers).containsExactly(10L, 20L, 30L, null, null, 60L);
        }

        sql("UPDATE t_plant_plot_info SET plot_status=2 WHERE id=10");
        idleIntervals.recordAfterPlotWrite(10L, LocalDate.of(2026, 8, 7), "plant_start");
        sql("UPDATE t_plant_plot_info SET plot_status=1 WHERE id=10");
        sql(initializeIdleHistory);
        sql(backfillIdleHistory);
        assertThat(idleIntervalsInDatabase()).containsExactly(
            "10:1001:2026-08-01:2026-08-07:history_init:plant_start",
            "20:1001:2026-08-04:null:history_init:null",
            "30:1001:2026-08-01:null:history_init:null",
            "60:2001:2026-08-05:null:history_init:null");
    }

    @Test
    void statusRecordingReusesAnOpenIntervalAndClosesOnlyOnLeavingIdle() throws Exception {
        sql("INSERT INTO t_plant_plot_info (id, plot_status) VALUES (10,1),(20,2)");
        LocalDate day = LocalDate.of(2026, 8, 4);
        idleIntervals.recordAfterPlotWrite(List.of(10L, 20L), day, "plot_create");
        idleIntervals.recordAfterPlotWrite(10L, day.plusDays(1), "rotation");
        assertThat(idleIntervalsInDatabase()).containsExactly("10:1001:2026-08-04:null:plot_create:null");

        sql("UPDATE t_plant_plot_info SET plot_status=2 WHERE id=10");
        session.clearCache();
        idleIntervals.recordAfterPlotWrite(10L, day.plusDays(3), "plant_start");
        sql("UPDATE t_plant_plot_info SET plot_status=3 WHERE id=10");
        session.clearCache();
        idleIntervals.recordAfterPlotWrite(10L, day.plusDays(4), "pick_submit");
        sql("UPDATE t_plant_plot_info SET plot_status=1 WHERE id=10");
        session.clearCache();
        idleIntervals.recordAfterPlotWrite(10L, day.plusDays(2), "rotation"); // 补录早于上段恢复日
        assertThat(idleIntervalsInDatabase()).containsExactly(
            "10:1001:2026-08-04:2026-08-07:plot_create:plant_start",
            "10:1001:2026-08-07:null:rotation:null");

        sql("UPDATE t_plant_plot_info SET del_flag='1' WHERE id=10");
        session.clearCache();
        idleIntervals.recordAfterPlotWrite(10L, day.plusDays(2), "plot_delete");
        assertThat(idleIntervalsInDatabase()).containsExactly(
            "10:1001:2026-08-04:2026-08-07:plot_create:plant_start",
            "10:1001:2026-08-07:2026-08-07:rotation:plot_delete");
        assertThat(session.getMapper(PlotIdleIntervalMapper.class).lockPlots(List.of(10L)))
            .singleElement().satisfies(plot -> {
                assertThat(plot.idleOpenIntervalId()).isNull();
                assertThat(plot.idleLastEndedOn()).isEqualTo(day.plusDays(3));
            });
    }

    @Test
    void plotStatusAndIdleHistoryRollBackTogether() throws Exception {
        session.getConnection(); // 将 JDBC fixture 与 mapper 置于同一事务
        sql("INSERT INTO t_plant_plot_info (id, plot_status) VALUES (10,3)");
        session.commit(true);

        sql("UPDATE t_plant_plot_info SET plot_status=1 WHERE id=10");
        idleIntervals.recordAfterPlotWrite(10L, LocalDate.of(2026, 9, 1), "rotation");
        assertThat(idleIntervalsInDatabase()).hasSize(1);

        session.rollback(true);
        assertThat(idleIntervalsInDatabase()).isEmpty();
        assertThat(session.getMapper(PlotIdleIntervalMapper.class).lockPlots(List.of(10L)))
            .singleElement().satisfies(plot -> {
                assertThat(plot.status()).isEqualTo(3);
                assertThat(plot.idleOpenIntervalId()).isNull();
                assertThat(plot.idleLastEndedOn()).isNull();
            });
    }

    @Test
    void firstInboundCountsActualDirectReceiptsAndNotTransfers() throws Exception {
        flow(1, 20, "2025-11-10 07:00:00", "veg_stock_in", "10", 222L);
        dispatch(1, 10, 111, 101, "2025-11-09 10:00:00", "8", true);
        dispatch(2, 20, 222, 102, "2025-11-10 09:00:00", "10", false);
        receipt(11, 10, 111, 101, "2025-11-10 11:00:00", "7", "1");
        receipt(12, 20, 222, 102, "2025-11-10 11:00:00", "10", "0");
        service.recalculate(DAY, DAY);
        var report = service.query(DAY, DAY);
        assertAll(
            () -> assertThat(report.daily().getFirst().harvestInboundWeight()).as("种植 14").isEqualByComparingTo("17"),
            () -> assertThat(report.daily().getFirst().harvestDockUnmatchedCount()).as("种植 14 转库已配对").isZero(),
            () -> assertThat(report.daily().getFirst().harvestInboundCropCount()).as("种植 15").isEqualTo(2),
            () -> assertThat(report.daily().getFirst().harvestInboundPlotCount()).as("种植 17").isEqualTo(2),
            () -> assertThat(report.monthly().getFirst().harvestInboundWeight()).as("种植 34").isEqualByComparingTo("17"),
            () -> assertThat(report.monthly().getFirst().harvestInboundCropCount()).as("种植 36").isEqualTo(2),
            () -> assertThat(report.monthly().getFirst().harvestInboundPlotCount()).as("种植 37").isEqualTo(2),
            () -> assertThat(report.annual().getFirst().harvestInboundWeight()).as("种植 57").isEqualByComparingTo("17"));
        service.recalculate(DAY, DAY);
        assertThat(service.query(DAY, DAY)).isEqualTo(report);
    }

    @Test
    void zeroWeightReceiptAndTransferWithoutSameDayStockInDoNotAddCropsOrPlots() throws Exception {
        flow(1, 20, "2025-11-09 07:00:00", "veg_stock_in", "12", 222L);
        dispatch(1, 20, 222, 102, "2025-11-09 09:00:00", "12", false);
        receipt(11, 20, 222, 102, "2025-11-10 10:00:00", "12", "0");
        dispatch(2, 30, 333, 103, "2025-11-10 09:00:00", "5", true);
        receipt(12, 30, 333, 103, "2025-11-10 11:00:00", "0", "5");

        service.recalculate(DAY.minusDays(1), DAY);
        var receiptDay = service.query(DAY, DAY).daily().getFirst();
        assertAll(
            () -> assertThat(receiptDay.harvestInboundWeight()).as("种植 14").isZero(),
            () -> assertThat(receiptDay.harvestDockUnmatchedCount()).as("种植 14 转库已配对").isZero(),
            () -> assertThat(receiptDay.harvestInboundCropCount()).as("种植 15").isZero(),
            () -> assertThat(receiptDay.harvestInboundPlotCount()).as("种植 17").isZero());
    }

    @Test
    void sameDayMixedReceiptsStillProveDailyTotal() throws Exception {
        flow(1, 10, "2025-11-10 07:00:00", "veg_stock_in", "10", 111L);
        dispatch(1, 10, 111, 101, "2025-11-10 08:00:00", "7", true);
        dispatch(2, 10, 111, 101, "2025-11-10 09:00:00", "10", false);
        receipt(11, 10, 111, 101, "2025-11-10 10:00:00", "7", null);
        receipt(12, 10, 111, 101, "2025-11-10 23:59:59", "10", "0");
        sql("UPDATE t_warehouse_stock_flow SET flow_date='2025-11-11 00:00:00' WHERE id=12");

        service.recalculate(DAY, DAY.plusDays(1));
        var report = service.query(DAY, DAY.plusDays(1));
        assertThat(report.daily().getFirst().harvestInboundWeight()).as("种植 14").isEqualByComparingTo("17");
        assertThat(report.daily().getLast().harvestInboundWeight()).as("种植 14 实收日优先").isZero();
        assertThat(report.daily().getFirst().harvestDockUnmatchedCount()).isEqualTo(2);
    }

    @Test
    void mixedAndOrphanDockReceiptsAreFlaggedWithoutGuessing() throws Exception {
        dispatch(1, 10, 111, 101, "2024-12-31 10:00:00", "5", true);
        dispatch(2, 10, 111, 101, "2025-01-01 09:00:00", "4", false);
        receipt(11, 10, 111, 101, "2025-01-01 12:00:00", "6", null);
        receipt(12, 10, 111, 101, "2025-01-02 12:00:00", "3", "0");
        flow(13, 30, "2025-01-02 13:00:00", "veg_receive_in", "77", null);
        LocalDate first = LocalDate.of(2025, 1, 1);
        service.recalculate(first, first.plusDays(1));
        var report = service.query(first, first.plusDays(1));
        assertThat(report.daily().getFirst().harvestInboundWeight()).isEqualByComparingTo("2"); // 种植 14
        assertThat(report.annual().getFirst().harvestInboundWeight()).isEqualByComparingTo("2"); // 种植 57：逐日仅加已证实量
        assertThat(report.daily().getLast().harvestDockUnmatchedCount()).isEqualTo(2);
        assertThat(report.monthly().getFirst().harvestDockUnmatchedCount()).isEqualTo(3);
    }

    @Test
    void transplantClosingIsNotHarvestOrAnOriginalPlantingPlan() throws Exception {
        sql("INSERT INTO t_plant_plant_plan (id,plan_year) VALUES (1,2025)");
        detail(1, 1, 10, 111, 11, 5, "2025-11-01", "2025-11-02", "2025-11-09", DAY.toString());
        detail(2, 1, 20, 222, 11, 5, "2025-11-01", "2025-11-02", DAY.toString(), DAY.toString());
        detail(3, 1, 30, 222, 11, 5, "2025-11-01", null, null, null);
        sql("UPDATE t_plant_plant_details SET transplant_adjusted=1 WHERE id=3");
        farm(1, 20, "transplant", DAY.minusDays(2), null); // 人工结束移栽可晚于最后一笔移栽
        sql("UPDATE t_plant_farm_records SET plant_id=1,crop_id=222 WHERE id=1");
        farm(2, 20, "rotation", DAY.plusDays(2), 2L);
        farm(3, 40, "rotation", DAY.plusDays(2), null);

        service.recalculate(DAY, DAY.plusDays(2));
        var report = service.query(DAY, DAY.plusDays(2));
        var month = report.monthly().getFirst();
        assertThat(report.daily().getFirst().harvestFinishedPlotCount()).as("种植 16").isEqualTo(1);
        assertThat(month.harvestFinishedPlotCount()).as("种植 35").isEqualTo(1);
        assertThat(month.getPlantPlanCompletionPercent()).as("种植 31").isEqualByComparingTo("100.00");
        assertThat(report.annual().getFirst().getPlantPlanCompletionPercent()).as("种植 56").isEqualByComparingTo("100.00");
        var rotation = report.daily().getLast();
        assertThat(rotation.rotationWaitDaysSum()).as("种植 22").isEqualTo(2);
        assertThat(rotation.getRotationWaitAverageDays()).as("种植 23").isEqualTo(new BigDecimal("1.0"));
    }

    @Test
    void backdatedTransplantClosureDoesNotCountAsHarvestCompletion() throws Exception {
        detail(1, 1, 10, 111, 10, 1, "2025-10-01", "2025-10-01", DAY.toString(), DAY.toString());
        farm(1, 10, "transplant", DAY.plusDays(1), null);
        sql("UPDATE t_plant_farm_records SET plant_id=1,crop_id=111 WHERE id=1");

        service.recalculate(DAY, DAY);
        var report = service.query(DAY, DAY);
        assertAll(
            () -> assertThat(report.daily().getFirst().harvestFinishedPlotCount()).as("种植 16").isZero(),
            () -> assertThat(report.monthly().getFirst().harvestFinishedPlotCount()).as("种植 35").isZero());
    }

    @Test
    void transplantTargetInTheSamePlanAndCropStillCountsAsHarvestCompletion() throws Exception {
        detail(1, 1, 10, 111, 10, 1, "2025-10-01", "2025-10-01", DAY.toString(), DAY.toString());
        detail(2, 1, 50, 111, 10, 1, "2025-10-01", null, "2025-11-01", DAY.toString());
        sql("UPDATE t_plant_plant_details SET transplant_adjusted=1 WHERE id=2");
        farm(1, 10, "transplant", DAY.minusDays(20), null);
        sql("UPDATE t_plant_farm_records SET plant_id=1,crop_id=111,transplant_plot=50 WHERE id=1");

        service.recalculate(DAY, DAY);
        var report = service.query(DAY, DAY);
        assertAll(
            () -> assertThat(report.daily().getFirst().harvestFinishedPlotCount()).as("种植 16").isEqualTo(1),
            () -> assertThat(report.monthly().getFirst().harvestFinishedPlotCount()).as("种植 35").isEqualTo(1));
    }

    @Test
    void monthlyYieldDisastersRotationAndPerformanceUseTheirOwnSources() throws Exception {
        detail(1, 1, 10, 111, 10, 5, "2025-10-15", "2025-10-16", "2025-11-01", "2025-11-08");
        detail(2, 1, 20, 222, 11, 3, "2025-11-10", "2025-11-11", "2025-12-01", "2025-12-02");
        detail(3, 2, 10, 111, 12, 5, "2025-12-04", "2025-12-05", "2025-12-06", "2025-12-07");
        detail(4, 1, 30, 333, 11, 5, "2025-11-01", "2025-11-02", "2025-11-03", "2025-11-04");
        sql("UPDATE t_plant_plant_details SET actual_yield=72, expected_yield=100 WHERE id=1");
        sql("UPDATE t_plant_plant_details SET actual_yield=5, expected_yield=0 WHERE id=2");
        sql("UPDATE t_plant_plant_details SET expected_yield=70 WHERE id=3");
        sql("INSERT INTO t_plant_plot_info (id,plot_area) VALUES (10,5),(20,3)");
        farm(1, 10, "disaster", NOV.plusDays(1), null);
        farm(2, 10, "disaster", NOV.plusDays(2), null);
        farm(3, 10, "disaster", NOV.plusDays(3), null);
        farm(4, 10, "disaster", NOV.plusMonths(1).plusDays(5), null);
        sql("UPDATE t_plant_farm_records SET crop_id=111, plant_id=1, loss_rate=20, loss_yield=20 WHERE id=1");
        sql("UPDATE t_plant_farm_records SET crop_id=111, plant_id=1, loss_rate=0, loss_yield=0 WHERE id=2");
        sql("UPDATE t_plant_farm_records SET crop_id=111, plant_id=1, loss_rate=90, loss_yield=90, del_flag='1' WHERE id=3");
        sql("UPDATE t_plant_farm_records SET crop_id=111, plant_id=2, loss_rate=70, loss_yield=70 WHERE id=4");
        farm(5, 10, "rotation", DAY, 1L); // 2 天
        farm(6, 20, "rotation", NOV.plusMonths(1).plusDays(11), 2L); // 10 天
        farm(7, 10, "rotation", NOV.plusMonths(1).plusDays(16), 3L); // 10 天
        detail(5, 1, 30, 333, 12, 5, "2025-12-01", "2025-12-03", "2025-12-06", "2025-12-08");
        farm(8, 30, "rotation", NOV.plusMonths(1).plusDays(17), 5L); // 10 天

        sql("INSERT INTO t_warehouse_product_info (id) VALUES (101),(102)");
        sql("INSERT INTO t_plant_crop_product (id,crop_id,product_id,perf_price) VALUES (1,111,101,2),(2,111,102,6)");
        sql("""
            INSERT INTO t_warehouse_handle_record
                (id,crop_id,product_id,team_id,record_type,record_weight,handle_time)
            VALUES (21,111,101,77,1,10,'2025-11-10 08:00:00'),
                   (22,111,102,78,1,10,'2025-11-10 09:00:00'),
                   (23,111,102,78,2,40,'2025-11-10 09:00:00')
            """);
        sql("INSERT INTO t_warehouse_handle_record_team (record_id,team_id) VALUES (21,77),(21,78)");
        sql("""
            INSERT INTO t_plant_work_people (team_id,user_id,join_date,del_flag,update_time)
            VALUES (77,1,'2025-10-01','0',NULL),(77,2,'2025-10-01','1','2025-11-20 12:00:00'),
                   (78,3,'2025-11-10','0',NULL),(78,4,'2025-11-11','0',NULL),
                   (78,5,'2025-12-01','0',NULL)
            """);
        sql("""
            INSERT INTO t_plant_work_performance (stat_month,performance_amount,create_time)
            VALUES ('2025-11',120,'2025-12-07'),('2025-12',80,'2026-01-07')
            """);

        LocalDate end = NOV.plusMonths(1).plusDays(20);
        service.recalculate(DAY, end);
        var report = service.query(DAY, end);
        var november = report.monthly().getFirst();
        var december = report.monthly().getLast();
        var year = report.annual().getFirst();
        assertThat(report.daily().getFirst().harvestPerformancePerPersonEstimate()).isEqualTo(new BigDecimal("26.67")); // 种植 25
        assertThat(november.yieldCompletionPercent()).isEqualTo(new BigDecimal("90.00")); // 种植 38
        assertThat(december.yieldCompletionPercent()).isNull(); // 种植 38：无可用分母
        assertThat(november.rotationAverageDays()).isEqualTo(new BigDecimal("2.0")); // 种植 41
        assertThat(december.rotationAverageDays()).isEqualTo(new BigDecimal("10.0")); // 种植 41
        assertThat(year.rotationAverageDays()).isEqualTo(new BigDecimal("8.0")); // 种植 58
        assertThat(november.disasterPlotCount()).isEqualTo(1); // 种植 42
        assertThat(november.harvestPerformanceAmount()).isEqualByComparingTo("120"); // 种植 43
        assertThat(november.harvestPerformancePerPerson()).isEqualTo(new BigDecimal("40.00")); // 种植 44
        assertThat(november.disasterLossArea()).isEqualTo(new BigDecimal("1.000")); // 种植 51
        assertThat(november.disasterLossYield()).isEqualTo(new BigDecimal("20.000")); // 种植 52
        assertThat(year.harvestPerformanceAmount()).isEqualByComparingTo("200"); // 种植 61
        assertThat(year.harvestPerformancePerPerson()).isEqualTo(new BigDecimal("50.00")); // 种植 62
        service.recalculate(DAY, end);
        assertThat(service.query(DAY, end)).isEqualTo(report);
    }

    @Test
    void monthlyAndAnnualRotationAveragesIncludeUnmatchedRounds() throws Exception {
        detail(1, 1, 10, 111, 10, 1, "2025-10-01", "2025-10-01", "2025-11-01", "2025-11-07");
        farm(1, 10, "rotation", DAY, 1L); // 3 天
        farm(2, 20, "rotation", DAY, null); // 未配对
        detail(2, 1, 30, 111, 11, 1, "2025-11-01", "2025-11-02", "2025-12-01", "2025-12-05");
        LocalDate december = LocalDate.of(2025, 12, 10);
        farm(3, 30, "rotation", december, 2L); // 5 天

        service.recalculate(DAY, december);
        var report = service.query(DAY, december);
        assertAll(
            () -> assertThat(report.monthly().getFirst().rotationAverageDays()).as("种植 41").isEqualTo(new BigDecimal("1.5")),
            () -> assertThat(report.monthly().getLast().rotationAverageDays()).as("种植 41").isEqualTo(new BigDecimal("5.0")),
            () -> assertThat(report.annual().getFirst().rotationAverageDays()).as("种植 58").isEqualTo(new BigDecimal("2.7")));
    }

    @Test
    void monthlyYieldCompletionUsesOnlyDetailsWithActualYield() throws Exception {
        detail(1, 1, 10, 111, 10, 1, "2025-10-01", "2025-10-01", "2025-11-01", DAY.toString());
        detail(2, 1, 20, 222, 10, 1, "2025-10-01", "2025-10-01", "2025-11-01", DAY.toString());
        sql("UPDATE t_plant_plant_details SET expected_yield=200, actual_yield=120 WHERE id=1");
        sql("UPDATE t_plant_plant_details SET expected_yield=100 WHERE id=2");
        farm(1, 10, "disaster", OCT.plusDays(19), 1L);
        sql("UPDATE t_plant_farm_records SET crop_id=111, plant_id=1, loss_yield=50 WHERE id=1");

        service.recalculate(DAY, DAY);
        assertThat(service.query(DAY, DAY).monthly().getFirst().yieldCompletionPercent())
            .as("种植 38").isEqualTo(new BigDecimal("80.00"));
    }

    @Test
    void monthlyYieldMatchesDisasterByPlanEvenBeforePlantingStarts() throws Exception {
        LocalDate august = LocalDate.of(2026, 8, 31);
        detail(1, 826049, 68, 34, 8, 1, "2026-08-02", "2026-08-03", "2026-08-10", "2026-08-20");
        detail(2, 826048, 68, 34, 7, 1, "2026-07-01", "2026-07-02", "2026-07-10", "2026-07-20");
        detail(3, 826049, 69, 34, 8, 1, "2026-08-03", "2026-08-04", "2026-08-11", "2026-08-21");
        sql("UPDATE t_plant_plant_details SET expected_yield=2472, actual_yield=1319 WHERE id=1");
        sql("UPDATE t_plant_plant_details SET expected_yield=4359, actual_yield=1701 WHERE id=3");
        farm(1, 68, "disaster", august.withDayOfMonth(1), null);
        sql("UPDATE t_plant_farm_records SET crop_id=34,plant_id=826049,loss_yield=988.8 WHERE id=1");

        service.recalculate(august, august);
        assertThat(service.query(august, august).monthly().getFirst().yieldCompletionPercent())
            .as("种植 38：3020 / (6831 - 988.8)").isEqualByComparingTo("51.69");
    }

    @Test
    void monthlyYieldFallsBackToDatesOnlyWithoutDisasterPlan() throws Exception {
        detail(1, 1, 10, 111, 10, 1, "2025-10-01", "2025-10-01", "2025-11-01", DAY.toString());
        sql("UPDATE t_plant_plant_details SET expected_yield=100,actual_yield=72 WHERE id=1");
        farm(1, 10, "disaster", NOV, null);
        farm(2, 10, "disaster", NOV.plusDays(1), null);
        farm(3, 10, "disaster", NOV.plusDays(2), null);
        sql("UPDATE t_plant_farm_records SET crop_id=111,loss_yield=20 WHERE id=1");
        sql("UPDATE t_plant_farm_records SET crop_id=111,plant_id=2,loss_yield=10 WHERE id=2");
        sql("UPDATE t_plant_farm_records SET crop_id=222,plant_id=1,loss_yield=10 WHERE id=3");

        service.recalculate(DAY, DAY);
        assertThat(service.query(DAY, DAY).monthly().getFirst().yieldCompletionPercent())
            .as("种植 38：只有缺计划的旧灾害按日期匹配").isEqualByComparingTo("90.00");
    }

    @Test
    void dailyPerformanceAppliesEachWeighingPercentageWithSettlementPrices() throws Exception {
        sql("INSERT INTO t_plant_crop_info (id) VALUES (111)");
        sql("INSERT INTO t_warehouse_product_info (id) VALUES (101),(102),(103)");
        sql("INSERT INTO t_plant_crop_product (id,crop_id,product_id,perf_price) VALUES (1,111,101,3),(2,111,102,6)");
        sql("""
            INSERT INTO t_warehouse_handle_record
                (id,crop_id,product_id,team_id,record_type,record_weight,perf_percent,handle_time)
            VALUES (21,111,102,77,1,10,50,'2025-11-10 08:00:00'),
                   (22,111,102,77,1,10,0,'2025-11-10 08:30:00'),
                   (23,111,103,77,1,10,20,'2025-11-10 09:00:00'),
                   (24,111,101,77,1,10,NULL,'2025-11-10 09:30:00')
            """);
        sql("INSERT INTO t_plant_work_people (team_id,user_id,join_date) VALUES (77,1,?),(77,2,?)",
            DAY.minusDays(1), DAY.minusDays(1));

        service.recalculate(DAY, DAY);
        assertThat(service.query(DAY, DAY).daily().getFirst().harvestPerformancePerPersonEstimate())
            .as("种植 25：(30 + 0 + 6 + 30) / 2").isEqualTo(new BigDecimal("33.00"));
    }

    @Test
    void disasterYieldUsesRegisteredLossAndMissingActualStaysNull() throws Exception {
        detail(1, 1, 10, 111, 10, 5, "2025-10-01", "2025-10-02", "2025-10-03", "2025-10-30");
        detail(2, 1, 20, 222, 11, 5, "2025-11-01", "2025-11-02", "2025-11-03", "2025-11-04");
        sql("UPDATE t_plant_plant_details SET expected_yield=100");
        sql("UPDATE t_plant_plant_details SET actual_yield=40 WHERE id=1");
        farm(1, 10, "disaster", OCT.plusDays(14), null);
        sql("UPDATE t_plant_farm_records SET crop_id=111, plant_id=1, loss_rate=20, loss_yield=20");
        service.recalculate(OCT, DAY);
        var report = service.query(OCT, DAY);
        assertThat(report.monthly().getFirst().disasterLossYield()).as("种植 52").isEqualByComparingTo("20");
        assertThat(report.monthly().getFirst().yieldCompletionPercent()).as("种植 38").isEqualByComparingTo("50.00");
        assertThat(report.monthly().getLast().yieldCompletionPercent()).as("种植 38 无实际产量").isNull();
    }

    @Test
    void activityWeighingContributesOnlyOnItsBusinessDate() throws Exception {
        sql("INSERT INTO t_warehouse_product_info (id) VALUES (101)");
        sql("INSERT INTO t_plant_crop_product (id,crop_id,product_id,perf_price) VALUES (1,111,101,3)");
        sql("""
            INSERT INTO t_plant_plant_activity (id,crop_id,product_id,activity_date,pick_dest,daily_weight)
            VALUES (1,111,101,?,'platform',5),(2,111,101,?,NULL,100)
            """, DAY, DAY);
        sql("INSERT INTO t_plant_activity_team (activity_id,team_id) VALUES (1,77),(2,77)");
        sql("""
            INSERT INTO t_plant_work_people (team_id,user_id,join_date)
            VALUES (77,1,?),(78,2,?)
            """, DAY.minusDays(1), DAY.minusDays(1));
        service.recalculate(DAY.minusDays(1), DAY);
        var report = service.query(DAY.minusDays(1), DAY);
        assertThat(report.daily().getFirst().harvestPerformancePerPersonEstimate()).as("种植 25").isEqualByComparingTo("0");
        assertThat(report.daily().getLast().harvestPerformancePerPersonEstimate()).as("种植 25").isEqualByComparingTo("15");
    }

    @Test
    void activityWithoutDirectTeamsSharesWeightAcrossPlotCropHarvestTeams() throws Exception {
        sql("INSERT INTO t_warehouse_product_info (id) VALUES (101)");
        sql("INSERT INTO t_plant_crop_product (id,crop_id,product_id,perf_price) VALUES (1,111,101,3)");
        detail(1, 1, 10, 111, 11, 1, DAY.toString(), null, null, null);
        detail(2, 2, 10, 111, 11, 1, DAY.toString(), null, null, null);
        detail(3, 3, 10, 222, 11, 1, DAY.toString(), null, null, null);
        sql("""
            INSERT INTO t_plant_details_team (detail_id,team_id,role)
            VALUES (1,77,'harvest'),(2,77,'harvest'),(2,78,'harvest'),(2,80,'plant'),(3,81,'harvest')
            """);
        sql("""
            INSERT INTO t_plant_plant_activity (id,plot_id,crop_id,product_id,activity_date,pick_dest,daily_weight)
            VALUES (1,10,111,101,?,'raw',10),(2,10,111,101,?,'raw',10)
            """, DAY, DAY.plusDays(1));
        sql("INSERT INTO t_plant_activity_team (activity_id,team_id) VALUES (2,79)");
        sql("""
            INSERT INTO t_plant_work_people (team_id,user_id,join_date)
            VALUES (77,1,?),(78,2,?),(79,3,?),(80,4,?),(81,5,?)
            """, DAY, DAY, DAY, DAY, DAY);

        service.recalculate(DAY, DAY.plusDays(1));
        var daily = service.query(DAY, DAY.plusDays(1)).daily();
        assertAll(
            () -> assertThat(daily.getFirst().harvestPerformancePerPersonEstimate())
                .as("种植 25：10 kg 由 2 个采收班组平摊").isEqualByComparingTo("15.00"),
            () -> assertThat(daily.getLast().harvestPerformancePerPersonEstimate())
                .as("种植 25：有直接班组时全部计入直接班组").isEqualByComparingTo("30.00"));
        var shares = session.getMapper(ProductionIndicatorSourceMapper.class).selectDailyPerformance(TENANT, DAY, DAY.plusDays(1));
        assertThat(shares).extracting(ProductionIndicatorSourceMapper.DailyPerformance::teamId)
            .containsExactlyInAnyOrder(77L, 78L);
        assertThat(shares).allSatisfy(share -> assertThat(share.amount()).isEqualByComparingTo("15.00"));
    }

    @Test
    void annualIdleRestoresKnownPreAugustStartAndClosedHistory() throws Exception {
        LocalDate july = LocalDate.of(2026, 7, 10);
        LocalDate august = LocalDate.of(2026, 8, 20);
        farm(1, 10, "rotation", july, null);
        farm(3, 10, "rotation", july.plusDays(1), null); // 同一空闲段，重复退茬单不会再加一天
        detail(1, 1, 10, 111, 8, 5, "2026-08-16", august.toString(), null, null);
        farm(2, 20, "rotation", july, null);
        sql("""
            INSERT INTO t_plant_plot_idle_interval (id,plot_id,idle_started_on,idle_ended_on,start_source)
            VALUES (20,20,'2026-08-01','2026-08-20','history_init')
            """);
        service.recalculate(august, august);
        var year = service.query(august, august).annual().getFirst();
        assertThat(year.idleDaysSum()).as("种植 55：回补至开工 37 天，已记录 41 天").isEqualTo(78);
        assertThat(year.idleIntervalCount()).as("种植 55 两段").isEqualTo(2);
        sql("DELETE FROM t_plant_plot_idle_interval");
        sql("DELETE FROM t_plant_farm_records");
        sql("""
            INSERT INTO t_plant_plot_idle_interval (id,plot_id,idle_started_on,idle_ended_on,start_source)
            VALUES (1,10,'2026-06-01','2026-06-02','rotation'),
                   (2,10,'2026-06-04','2026-06-05','rotation'),
                   (3,20,'2026-06-01','2026-06-03','rotation')
            """);
        service.recalculate(august, august);
        assertThat(service.query(august, august).annual().getFirst().getIdleAverageDays())
            .as("种植 55").isEqualTo(new BigDecimal("1.3"));
    }

    @Test
    void sameDayRotationAndPlantStartWithRecordedZeroLengthAddsNoIdle() throws Exception {
        LocalDate rotation = LocalDate.of(2026, 9, 1);
        farm(1, 10, "rotation", rotation, null);
        detail(1, 1, 10, 111, 9, 1, "2026-09-01", "2026-09-05", null, null);
        sql("""
            INSERT INTO t_plant_plot_idle_interval (id,plot_id,idle_started_on,idle_ended_on,start_source,end_source)
            VALUES (1,10,'2026-09-01','2026-09-01','rotation','plant_start')
            """);
        LocalDate reportDay = rotation.plusDays(9);
        service.recalculate(reportDay, reportDay);
        var year = service.query(reportDay, reportDay).annual().getFirst();
        assertAll(
            () -> assertThat(year.idleDaysSum()).as("种植 55").isZero(),
            () -> assertThat(year.idleIntervalCount()).as("种植 55").isZero());
    }

    @Test
    void idleBackfillRespectsExistingIntervalsAndFallsBackToCompletion() throws Exception {
        LocalDate rotation = LocalDate.of(2026, 9, 1);
        farm(1, 10, "rotation", rotation, null);
        farm(2, 20, "rotation", rotation, null);
        farm(3, 30, "rotation", rotation, null);
        farm(4, 40, "rotation", rotation, null);
        detail(1, 1, 10, 111, 9, 1, "2026-09-05", "2026-09-07", null, null);
        detail(2, 1, 20, 111, 9, 1, "2026-09-05", "2026-09-07", null, null);
        detail(3, 1, 30, 111, 9, 1, null, "2026-09-04", null, null); // 3 天
        detail(4, 1, 40, 111, 9, 1, "2026-09-03", null, null, null); // 开工未完工，2 天
        sql("UPDATE t_plant_plant_details SET plant_status='ongoing' WHERE id=4");
        sql("""
            INSERT INTO t_plant_plot_idle_interval (id,plot_id,idle_started_on,idle_ended_on,start_source,end_source)
            VALUES (1,10,'2026-09-01','2026-09-01','rotation','plant_start'),
                   (2,20,'2026-09-02','2026-09-02','rotation','plant_start')
            """);
        LocalDate reportDay = rotation.plusDays(9);
        service.recalculate(reportDay, reportDay);
        var year = service.query(reportDay, reportDay).annual().getFirst();
        assertAll(
            () -> assertThat(year.idleDaysSum()).as("种植 55").isEqualTo(5),
            () -> assertThat(year.idleIntervalCount()).as("种植 55").isEqualTo(2));
    }

    @Test
    void historicalIdleEndsWhenTheTargetPlotReceivesATransplant() throws Exception {
        LocalDate september = LocalDate.of(2026, 9, 1);
        farm(1, 50, "rotation", september, null);
        detail(1, 1, 50, 111, 8, 1, "2026-08-01", "2026-08-05", "2026-09-20", "2026-10-10");
        sql("UPDATE t_plant_plant_details SET transplant_adjusted=1 WHERE id=1");
        farm(2, 10, "transplant", september.plusDays(9), null);
        sql("UPDATE t_plant_farm_records SET plant_id=1,crop_id=111,transplant_plot=50 WHERE id=2");
        farm(3, 50, "rotation", LocalDate.of(2026, 10, 15), null);
        detail(2, 2, 50, 222, 10, 1, "2026-10-20", "2026-10-22", null, null);

        LocalDate reportDay = LocalDate.of(2026, 11, 1);
        service.recalculate(reportDay, reportDay);
        var year = service.query(reportDay, reportDay).annual().getFirst();
        assertAll(
            () -> assertThat(year.idleDaysSum()).as("种植 55：移入前 9 天，再种前 5 天").isEqualTo(14),
            () -> assertThat(year.idleIntervalCount()).as("种植 55").isEqualTo(2),
            () -> assertThat(year.getIdleAverageDays()).as("种植 55").isEqualByComparingTo("7.0"));

        sql("UPDATE t_plant_plant_details SET begin_actualdate='2026-09-02' WHERE id=1");
        service.recalculate(reportDay, reportDay);
        assertThat(service.query(reportDay, reportDay).annual().getFirst().idleDaysSum())
            .as("种植 55：目标明细沿用的育苗开工日也可能晚于退茬").isEqualTo(14);
    }

    private void dispatch(int id, int plot, int crop, int product, String stamp, String weight, boolean direct)
        throws Exception {
        sql("""
            INSERT INTO t_warehouse_vegetable_handle (id, planting_record_id, send_platform_weight, picked_weight, is_weighed)
            VALUES (?,?,?,?,?)
            """, id, direct ? null : id, weight, direct ? 0 : weight, direct ? 2 : 1);
        sql("""
            INSERT INTO t_warehouse_handle_record
                (id,handle_id,plot_id,crop_id,product_id,record_type,handle_target,record_weight,handle_time)
            VALUES (?,?,?,?,?,2,2,?,?)
            """, id, id, plot, crop, product, weight, stamp);
    }

    private void receipt(int id, int plot, int crop, int product, String stamp, String weight, String loss) throws Exception {
        flow(id, plot, stamp, "veg_receive_in", weight, null);
        sql("UPDATE t_warehouse_stock_flow SET flow_no=?,product_id=? WHERE id=?", "dock-" + id, product, id);
        sql("""
            INSERT INTO t_warehouse_veg_receive
                (id,receive_no,receive_type,plot_id,crop_id,product_id,is_finish,receive_time,weight,loss_weight)
            VALUES (?,?,1,?,?,?,?,?,?,?)
            """, id, "dock-" + id, plot, crop, product, loss == null ? 2 : 1, stamp, weight, loss);
    }

    private List<String> idleIntervalsInDatabase() throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
            SELECT CONCAT_WS(':',plot_id,tenant_id,idle_started_on,COALESCE(idle_ended_on,'null'),
                start_source,COALESCE(end_source,'null'))
              FROM t_plant_plot_idle_interval ORDER BY plot_id, idle_started_on
            """)) {
            var intervals = new ArrayList<String>();
            while (rows.next()) {
                intervals.add(rows.getString(1));
            }
            return intervals;
        }
    }

    private void detail(int id, int plan, int plot, int crop, int month, int area,
                        String begin, String end, String harvestBegin, String harvestEnd) throws Exception {
        sql("""
            INSERT INTO t_plant_plant_details (id,plant_id,plot_id,crop_id,plant_month,plot_area,
                begin_actualdate,end_actualdate,begin_harvestdate,end_harvestdate)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """, id, plan, plot, crop, month, area, begin, end, harvestBegin, harvestEnd);
    }

    private void farm(int id, int plot, String type, LocalDate date, Long detail) throws Exception {
        sql("""
            INSERT INTO t_plant_farm_records (id,plot_id,farm_type,farm_date,farm_by,create_time,plant_detail_id)
            VALUES (?,?,?,?,77,?,?)
            """, id, plot, type, date, date.plusDays(1), detail);
    }

    private void flow(int id, int plot, String stamp, String type, String weight, Long crop) throws Exception {
        sql("""
            INSERT INTO t_warehouse_stock_flow (id,plot_id,flow_date,flow_type,inout_type,change_quantity,crop_id)
            VALUES (?,?,?,?,'IN',?,?)
            """, id, plot, stamp, type, weight, crop);
    }

    private long queryLong(String statement) throws Exception {
        try (var query = connection.createStatement(); var rows = query.executeQuery(statement)) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }

    private void sql(String statement, Object... values) throws Exception {
        try (var command = connection.prepareStatement(statement)) {
            for (int i = 0; i < values.length; i++) {
                command.setObject(i + 1, values[i]);
            }
            command.execute();
        }
    }
}
