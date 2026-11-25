package org.dromara.djs.plant.indicator;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.djs.plant.dashboard.applet.mapper.AppletPlantManageDashboardMapper;
import org.dromara.djs.plant.dashboard.mapper.PlantDashboardMapper;
import org.dromara.djs.plant.indicator.mapper.PlantProductionIndicatorMapper;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper;
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
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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
                actual_yield DECIMAL(12,3), rotated_at DATETIME)
            """);
        sql("""
            CREATE TEMPORARY TABLE t_plant_plant_plan (id BIGINT, plan_year INT,
                tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_plant_plot_info (id BIGINT PRIMARY KEY, plot_area DECIMAL(10,2),
                plot_status INT DEFAULT 1,
                tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_plant_farm_records (
                id BIGINT, plot_id BIGINT, farm_type VARCHAR(32), farm_date DATE, farm_by BIGINT,
                create_time DATETIME, tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')
            """);
        sql("""
            CREATE TEMPORARY TABLE t_warehouse_stock_flow (
                id BIGINT, plot_id BIGINT, flow_date DATETIME, flow_type VARCHAR(32),
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
                activity_date DATE, pick_dest VARCHAR(32), daily_weight DECIMAL(12,3),
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

        Configuration config = new Configuration(new Environment("local", new JdbcTransactionFactory(),
            new SingleConnectionDataSource(connection, true)));
        MybatisPlusInterceptor tenant = new MybatisPlusInterceptor();
        tenant.addInnerInterceptor(new TenantLineInnerInterceptor(() -> new StringValue(TENANT)));
        config.addInterceptor(tenant);
        config.addMapper(ProductionIndicatorSourceMapper.class);
        config.addMapper(PlantProductionIndicatorMapper.class);
        config.addMapper(PlotIdleIntervalMapper.class);
        config.addMapper(PlantDashboardMapper.class);
        config.addMapper(PlantDetailsMapper.class);
        config.addMapper(PlantWorkPerformanceMapper.class);
        config.addMapper(AppletPlantManageDashboardMapper.class);
        session = new SqlSessionFactoryBuilder().build(config).openSession();
        service = new ProductionIndicatorService(session.getMapper(ProductionIndicatorSourceMapper.class),
            session.getMapper(PlantProductionIndicatorMapper.class));
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

    private void sql(String statement, Object... values) throws Exception {
        try (var command = connection.prepareStatement(statement)) {
            for (int i = 0; i < values.length; i++) {
                command.setObject(i + 1, values[i]);
            }
            command.execute();
        }
    }
}
