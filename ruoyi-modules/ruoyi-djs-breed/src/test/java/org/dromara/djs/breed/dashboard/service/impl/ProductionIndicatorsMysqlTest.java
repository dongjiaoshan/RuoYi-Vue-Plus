package org.dromara.djs.breed.dashboard.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.djs.breed.dashboard.domain.AnnualIndicator;
import org.dromara.djs.breed.dashboard.domain.FarmIndicatorRecord;
import org.dromara.djs.breed.dashboard.domain.FarmIndicatorRecordToFarmIndicatorRecordVoMapperImpl;
import org.dromara.djs.breed.dashboard.domain.MonthlyProduction;
import org.dromara.djs.breed.dashboard.mapper.AggregateQueryMapper;
import org.dromara.djs.breed.dashboard.mapper.AnnualIndicatorMapper;
import org.dromara.djs.breed.dashboard.mapper.FarmIndicatorRecordMapper;
import org.dromara.djs.breed.dashboard.mapper.FarrowingRateMapper;
import org.dromara.djs.breed.dashboard.mapper.MonthlyProductionMapper;
import org.dromara.djs.breed.dashboard.mapper.ProductionIndicatorMapper;
import org.dromara.djs.breed.dashboard.mapper.SowRecordMapper;
import org.dromara.djs.breed.dashboard.mapper.WeaningAggregateSyncMapper;
import org.dromara.djs.breed.production.mapper.SowPerformanceMapper;
import org.dromara.djs.breed.production.service.IFattenAgeStageService;
import org.dromara.djs.breed.production.service.IProductionCycleConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/** 本机 MySQL 上的真实 Mapper SQL、看板聚合写入和 VO 读取。 */
@Tag("local")
@Tag("dev")
@EnabledIfEnvironmentVariable(named = "DJS_STAT_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:(3306|3316)/.*")
class ProductionIndicatorsMysqlTest {

    private static final String TENANT = "1001";
    private static final LocalDate JAN31 = LocalDate.of(2025, 1, 31);
    private static final LocalDate FEB1 = JAN31.plusDays(1);
    private static final YearMonth JAN = YearMonth.from(JAN31);
    private static final YearMonth FEB = YearMonth.from(FEB1);
    private static final short YEAR = 2025;

    // 快照重放与个体断奶查询会多次引用同一张表，测试库使用独立 schema 中的普通表。
    private static final String SNAPSHOT_REPLAY = "snapshot-replay";
    private static final Set<String> REPLAY_QUERIES = Set.of("deletePigSnapshotOnDate", "rebuildPigSnapshotForDate");
    // 与日表取数无关的台账、性能和出栏背膘 SQL 使用测试替身。
    private static final Set<String> AGGREGATES = Set.of(
        "countDistinctEventInDay", "countDistinctAbnormalSowInRange", "countStatusEventInRange",
        "aggregateWeanDetailForDay", "sumWeanedInRange", "sumIntroducedSowInRange", "snapshotByTypeStatusOnDate",
        "sumEventInDayDate", "sumLiveBornInRange", "countReserve230OnSnapshot",
        "countIndicatorDays", "sumIndicatorRange", "selectSowDaysInRange", "sumMonthlyProductionRange",
        "findLateEntriesBeforeWindow",
        "countDistinctBreedingSowInRange", "sumWeanedWeightInRange", "sumWeanMateIntervalRange"
    );

    private Connection connection;
    private String testSchema;
    private SqlSession session;
    private DashboardServiceImpl service;
    private FarmIndicatorRecordMapper days;
    private MonthlyProductionMapper months;
    private AnnualIndicatorMapper years;
    private long nextStatusId;

    @BeforeEach
    void openTables(TestInfo test) throws Exception {
        connection = DriverManager.getConnection(System.getenv("DJS_STAT_MYSQL_URL"),
            System.getenv("DJS_STAT_MYSQL_USER"), System.getenv("DJS_STAT_MYSQL_PASSWORD"));
        String sourceCatalog = connection.getCatalog();
        String schema = "b1r1_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
        testSchema = schema;
        connection.setCatalog(testSchema);
        connection.setAutoCommit(false);
        table("t_farm_pig_info", "id BIGINT PRIMARY KEY, pig_type VARCHAR(16), birth_date DATE, wean_date DATE, wean_weight DECIMAL(12,3),"
            + " ear_no VARCHAR(32), introduce_type VARCHAR(16), introduce_date DATE, mother_ear VARCHAR(32)");
        table("t_farm_pig_introduce", "id BIGINT PRIMARY KEY, introduce_type VARCHAR(16), introduce_date DATE,"
            + " pig_count INT, pig_sex CHAR(1), pig_id BIGINT, start_ear_no VARCHAR(32)");
        table("t_farm_status_record", "id BIGINT PRIMARY KEY, pig_id BIGINT, event_type VARCHAR(16),"
            + " old_status VARCHAR(16), new_status VARCHAR(16), related_event_id BIGINT, change_time DATETIME,"
            + " old_pig_type VARCHAR(16), new_pig_type VARCHAR(16), create_time DATETIME, ear_no VARCHAR(32)");
        table("t_farm_pig_snapshot", "snap_date DATE, pig_id BIGINT, pig_type VARCHAR(16), current_status VARCHAR(16), birth_date DATE");
        table("t_farm_pig_farrow", "id BIGINT PRIMARY KEY, pig_id BIGINT, farrow_date DATETIME, parity INT,"
            + " dead_born INT, mummy_born INT, deformed_born INT, weak_culled INT, weak_born INT, create_time DATETIME,"
            + " breeding_id BIGINT, total_born INT, live_born INT, total_weight DECIMAL(12,3), healthy_male INT, healthy_female INT,"
            + " weak_raised_male INT, weak_raised_female INT");
        table("t_farm_pig_breeding", "id BIGINT PRIMARY KEY, pig_id BIGINT, breeding_date DATETIME, parity INT");
        table("t_farm_pig_abnormal", "id BIGINT PRIMARY KEY, pig_id BIGINT, abnormal_date DATETIME,"
            + " abnormal_type CHAR(1), create_time DATETIME, related_breeding_id BIGINT");
        table("t_farm_pig_death", "id BIGINT PRIMARY KEY, pig_id BIGINT, death_date DATETIME,"
            + " death_pig_type VARCHAR(16), create_time DATETIME, update_time DATETIME");
        table("t_farm_pig_culling", "id BIGINT PRIMARY KEY, pig_id BIGINT, culling_date DATETIME,"
            + " create_time DATETIME, update_time DATETIME");
        table("t_warehouse_feed_log", "id BIGINT PRIMARY KEY, feed_date DATETIME, feed_type VARCHAR(20),"
            + " feed_animal VARCHAR(20), product_id BIGINT, crop_id BIGINT, location_id BIGINT, feed_weight DECIMAL(12,3)");
        table("t_warehouse_product_info", "id BIGINT PRIMARY KEY, product_unit VARCHAR(16),"
            + " belong_type VARCHAR(32), buy_class VARCHAR(32)");
        table("t_farm_pig_marketing", "id BIGINT PRIMARY KEY, pig_id BIGINT, marketing_date DATETIME,"
            + " out_weight DECIMAL(12,2), create_time DATETIME");
        table("t_farm_pig_weaning", "id BIGINT PRIMARY KEY, pig_id BIGINT, farrow_id BIGINT NOT NULL,"
            + " weaning_date DATETIME, weaned_count INT, weaned_weight DECIMAL(12,2), lactation_death_count INT");
        table("t_farm_pig_weaning_detail", "id BIGINT PRIMARY KEY, weaning_id BIGINT, ear_no VARCHAR(32),"
            + " birth_farrow_id BIGINT, weight DECIMAL(12,3)");
        table("t_farm_pig_pigletno", "id BIGINT PRIMARY KEY, farrow_id BIGINT, piglet_ear_no VARCHAR(32),"
            + " pig_id BIGINT, del_unique BIGINT DEFAULT 0, tag_date DATETIME");
        table("t_farm_pig_transfer", "id BIGINT PRIMARY KEY, pig_id BIGINT, transfer_date DATETIME, transfer_reason VARCHAR(16)");
        table("t_breed_medicine_record", "id BIGINT PRIMARY KEY, pig_id BIGINT, use_date DATE, drug_type INT, medicine_type VARCHAR(16)");
        table("t_farm_farrowing_rate", "expected_farrow_date DATE, farrow_date DATE");
        table("t_farm_pig_growth", "id BIGINT PRIMARY KEY, pig_id BIGINT, measure_date DATE");
        for (String table : List.of("t_farm_indicator_record", "t_farm_monthly_production", "t_farm_year_production")) {
            copyTableDefinition(sourceCatalog, table);
        }
        for (String migration : List.of("V202609300900__BRD-STAT-V11-production-indicators.sql",
            "V202609301400__BRD-STAT-V12-daily-indicators.sql",
            "V202609301500__BRD-STAT-V12-monthly-indicators.sql",
            "V202609301600__BRD-STAT-V12-annual-indicators.sql")) {
            Path sql = Path.of("../../ruoyi-admin/src/main/resources/db/migration", migration);
            for (String statement : Files.readString(sql).split(";")) {
                if (!statement.isBlank()) {
                    execute(statement);
                }
            }
        }

        var configuration = new MybatisConfiguration(new Environment("local-mysql",
            new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMappers(AggregateQueryMapper.class.getPackageName());
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(connection);
        var source = session.getMapper(AggregateQueryMapper.class);
        boolean replaySnapshots = test.getTags().contains(SNAPSHOT_REPLAY);
        AggregateQueryMapper other = mock(AggregateQueryMapper.class, invocation -> {
            String method = invocation.getMethod().getName();
            if (!AGGREGATES.contains(method) && !(replaySnapshots && REPLAY_QUERIES.contains(method))) {
                return RETURNS_DEFAULTS.answer(invocation);
            }
            try {
                return invocation.getMethod().invoke(source, invocation.getArguments());
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        });
        days = session.getMapper(FarmIndicatorRecordMapper.class);
        months = session.getMapper(MonthlyProductionMapper.class);
        years = session.getMapper(AnnualIndicatorMapper.class);
        var realFarrowingRate = session.getMapper(FarrowingRateMapper.class);
        var farrowingRate = mock(FarrowingRateMapper.class, invocation -> {
            if ("selectFarrowRate".equals(invocation.getMethod().getName())) {
                return realFarrowingRate.selectFarrowRate(invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2), invocation.getArgument(3));
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        service = new DashboardServiceImpl(mock(SowRecordMapper.class), months, years, other, days,
            farrowingRate, mock(WeaningAggregateSyncMapper.class), mock(SowPerformanceMapper.class),
            mock(IProductionCycleConfigService.class), mock(IFattenAgeStageService.class),
            new ProductionIndicatorCalculator(session.getMapper(ProductionIndicatorMapper.class)));
    }

    @AfterEach
    void closeTables() throws Exception {
        try {
            if (testSchema != null) {
                execute("DROP DATABASE " + testSchema);
            }
        } finally {
            try {
                if (session != null) {
                    session.close();
                }
            } finally {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            }
        }
    }

    @Test
    void headDaysIncludeIntradayMembershipOnce() throws Exception {
        for (int i = 1; i <= 12; i++) {
            pig(i, "sow", null, null);
            pig(i + 20, i == 9 ? "boar" : "fattening", null, null);
            if (i != 9 && i != 10) {
                snapshot(i, "sow", "PZ");
                snapshot(i + 20, "fattening", "");
            }
        }
        pig(33, "fattening", null, null);
        snapshot(33, "fattening", "YF");
        change(33, "TO_FATTEN", "DN", "YF", null, "2025-01-31 09:00:00", "sow", "fattening");
        change(9, "ELIMINATE", "DN", "END", null, "2025-01-31 12:00:00", null, null);
        change(10, "DIE", "PZ", "END", null, "2025-01-31 12:00:00", null, null);
        snapshot(29, "boar", "");
        change(29, "INTRO", null, "", null, "2025-01-31 12:00:00", "fattening", "boar");
        change(30, "ELIMINATE", null, "END", null, "2025-01-31 12:00:00", null, null);
        change(11, "BREED", "HB", "PZ", null, "2025-01-31 10:00:00", null, null);
        change(12, "BREED", "HB", "PZ", null, "2025-01-31 10:00:00", null, null);
        change(12, "TRANSFER", "PZ", "PZ", null, "2025-01-31 11:00:00", null, null);
        change(31, "TRANSFER", null, "", null, "2025-01-31 10:00:00", "piglet", "fattening");
        change(32, "TRANSFER", null, "", null, "2025-01-31 10:00:00", "piglet", "fattening");
        FarmIndicatorRecord day = day(JAN31);
        assertThat(day.getProductionSowHeadDays()).as("养殖 3 含当天淘汰、死亡和转育肥的母猪").isEqualTo(13);
        assertThat(day.getEndProductionSowCount()).as("养殖 4").isEqualTo(10);
        assertThat(day.getPregnantSowCount()).as("养殖 8 已配种待分娩").isEqualTo(10);
        assertThat(day.getEndBoarCount()).as("养殖 10 公猪").isEqualTo(1);
        assertThat(day.getFatteningHeadDays()).as("养殖 12 含当天转出和淘汰的育肥猪").isEqualTo(13);
        assertThat(day.getEndFatteningCount()).as("养殖 13").isEqualTo(11);
    }

    @Test
    void fatteningHeadDaysIncludeUnstatedDeathsAndSlaughter() throws Exception {
        for (int i = 1; i <= 10; i++) {
            pig(i, "fattening", null, null);
            if (i <= 8) {
                snapshot(i, "fattening", "");
            }
        }
        change(9, "DIE", null, "END", null, "2025-01-31 10:00:00", null, null);
        change(10, "SLAUGHTER", null, "END", null, "2025-01-31 11:00:00", null, null);

        FarmIndicatorRecord day = day(JAN31);
        assertThat(day.getEndFatteningCount()).as("养殖 13 期末存栏").isEqualTo(8);
        assertThat(day.getFatteningHeadDays()).as("养殖 12 当日死亡和出栏仍计头天").isEqualTo(10);
    }

    @Test
    void headDaysReplayIndividualWeaningAndEarlierFatteningTransfer() throws Exception {
        // 同窝第一批已断奶的日末快照可能提前把第二批翻为育肥，个体断奶日能修正。
        pig(1, "fattening", "2025-01-10", "2025-02-01");
        snapshot(1, "fattening", "");
        // 已有确切转育肥日志的仔猪在断奶前也可进育肥群。
        pig(2, "fattening", "2025-01-10", "2025-02-01");
        snapshot(2, "piglet", "");
        change(2, "TRANSFER", null, "", null, "2025-01-30 10:00:00", "piglet", "fattening");
        // 断奶导致的批量翻类型没有日志，个体已断奶的猪在当天也计头天。
        pig(3, "fattening", "2025-01-10", "2025-01-31");
        snapshot(3, "piglet", "");
        FarmIndicatorRecord day = day(JAN31);
        assertThat(day.getFatteningHeadDays()).as("养殖 12 个体断奶快照与显式转育肥").isEqualTo(2);
        assertThat(day.getEndFatteningCount()).as("养殖 13 期末与头天共用个体断奶修正").isEqualTo(2);
    }

    @Test
    void reserveStockRequiresAnIntroductionAndNoBreedingAsOfDayEnd() throws Exception {
        for (int i = 1; i <= 3; i++) {
            pig(i, "sow", "2024-04-01", null);
            snapshot(i, "sow", "HB");
        }
        execute("UPDATE t_farm_pig_snapshot SET birth_date='2024-04-01'");
        execute("UPDATE t_farm_pig_info SET introduce_type='external', introduce_date='2025-01-01', "
            + "ear_no=CONCAT('1-01-240401-00', id)");
        execute("INSERT INTO t_farm_pig_introduce (id,introduce_type,introduce_date,pig_count,pig_sex,start_ear_no) "
            + "VALUES (1,'external','2025-01-01',2,'F','1-01-240401-001')");
        execute("INSERT INTO t_farm_pig_breeding (id,pig_id,breeding_date) VALUES "
            + "(1,2,'2025-01-29'),(2,1,'2025-02-01')");
        FarmIndicatorRecord row = day(JAN31);
        assertThat(row.getEndReserveCount()).as("养殖 9 外引两头中仅一头未配，普通建档不算").isEqualTo(1);
        assertThat(row.getEndReserve230Count()).as("既有 230 日龄分母独立").isEqualTo(3);

        execute("INSERT INTO t_farm_pig_introduce (id,pig_id,introduce_type,introduce_date,pig_count,pig_sex) "
            + "VALUES (2,3,'internal','2025-01-30',1,'F')");
        assertThat(day(JAN31).getEndReserveCount()).as("养殖 9 场内留种按引种单关联").isEqualTo(2);
        execute("UPDATE t_farm_pig_introduce SET del_flag='1' WHERE id=1");
        assertThat(day(JAN31).getEndReserveCount()).as("养殖 9 撤销外引").isEqualTo(1);

        execute("UPDATE t_farm_pig_info SET ear_no='1-01-240401-1000' WHERE id=1");
        change(1, "INTRO", null, "HB", null, "2025-01-01 00:00:00", null, null);
        execute("UPDATE t_farm_status_record SET ear_no='1-01-240401-1000' WHERE event_type='INTRO'");
        execute("UPDATE t_farm_pig_info SET ear_no='1-01-240401-150' WHERE id=1");
        execute("INSERT INTO t_farm_pig_introduce (id,introduce_type,introduce_date,pig_count,pig_sex,start_ear_no) "
            + "VALUES (3,'external','2025-01-01',2,'F','1-01-240401-999')");
        assertThat(day(JAN31).getEndReserveCount()).as("养殖 9 连号跨三位、改耳号后仍可追溯").isEqualTo(2);
    }

    @Test
    void legacyIntroductionOrdersWithoutEarNumbersIncludeAllIntroducedReserves() throws Exception {
        for (int i = 1; i <= 4; i++) {
            pig(i, "sow", "2024-04-01", null);
            snapshot(i, "sow", "HB");
        }
        execute("UPDATE t_farm_pig_info SET introduce_type='external', "
            + "introduce_date=IF(id=3,'2025-01-02','2025-01-01')");
        change(1, "INTRO", null, "HB", null, "2025-01-01 08:00:00", null, null);
        change(2, "INTRO", null, "HB", null, "2025-01-01 08:00:00", null, null);
        change(3, "INTRO", null, "HB", null, "2025-01-02 08:00:00", null, null);
        execute("INSERT INTO t_farm_pig_introduce (id,introduce_type,introduce_date,pig_count,pig_sex) "
            + "VALUES (1,'external','2025-01-01',2,NULL),(2,'external','2025-01-02',1,'F')");

        assertThat(day(JAN31).getEndReserveCount()).as("养殖 9 旧批量两头加旧单头，排除无流水的档案").isEqualTo(3);
        execute("UPDATE t_farm_pig_introduce SET del_flag='1' WHERE id=2");
        assertThat(day(JAN31).getEndReserveCount()).as("养殖 9 撤销的旧引种单排除").isEqualTo(2);
    }

    @Test
    void legacyIntroductionCountsAreCappedBySameDayOrderTotal() throws Exception {
        for (int i = 1; i <= 3; i++) {
            pig(i, "sow", "2024-04-01", null);
            snapshot(i, "sow", "HB");
            change(i, "INTRO", null, "HB", null, "2025-01-01 08:00:00", null, null);
        }
        execute("UPDATE t_farm_pig_info SET introduce_type='external', introduce_date='2025-01-01'");
        execute("INSERT INTO t_farm_pig_introduce (id,tenant_id,introduce_type,introduce_date,pig_count,pig_sex) VALUES "
            + "(1,'1001','external','2025-01-01',1,'F'),"
            + "(2,'1001','external','2025-01-02',9,'F'),(3,'2002','external','2025-01-01',9,'F')");
        assertThat(day(JAN31).getEndReserveCount()).as("养殖 9 三头有流水，同租户同日旧单只有一头").isEqualTo(1);

        execute("INSERT INTO t_farm_pig_introduce (id,introduce_type,introduce_date,pig_count,pig_sex) "
            + "VALUES (4,'external','2025-01-01',2,NULL)");
        assertThat(day(JAN31).getEndReserveCount()).as("养殖 9 同日两张旧单合计三头").isEqualTo(3);
        execute("UPDATE t_farm_pig_introduce SET del_flag='1' WHERE id=1");
        assertThat(day(JAN31).getEndReserveCount()).as("养殖 9 撤销一头的旧单后只剩两头").isEqualTo(2);

        execute("INSERT INTO t_farm_pig_introduce (id,pig_id,introduce_type,introduce_date,pig_count,pig_sex) "
            + "VALUES (5,1,'external','2025-01-01',1,'F')");
        assertThat(day(JAN31).getEndReserveCount()).as("养殖 9 显式关联的一头与旧单两头均计入").isEqualTo(3);
        execute("UPDATE t_farm_pig_introduce SET pig_count=3 WHERE id=4");
        assertThat(day(JAN31).getEndReserveCount()).as("养殖 9 显式关联过的个体不能再次占用旧单").isEqualTo(3);
    }

    @Test
    void legacyIntroductionRequiresMatchingTypeAndFemaleReserve() throws Exception {
        pig(1, "sow", "2024-04-01", null);
        pig(2, "sow", "2024-04-01", null);
        pig(3, "boar", "2024-04-01", null);
        snapshot(1, "sow", "HB");
        snapshot(2, "sow", "HB");
        snapshot(3, "boar", "HB"); // 旧流水有 HB 时，仍须按日末猪型排除公猪。
        execute("UPDATE t_farm_pig_info SET introduce_type=IF(id=2,'internal','external'), introduce_date='2025-01-01'");
        for (int i = 1; i <= 3; i++) {
            change(i, "INTRO", null, "HB", null, "2025-01-01 08:00:00", null, null);
        }
        execute("INSERT INTO t_farm_pig_introduce (id,introduce_type,introduce_date,pig_count,pig_sex) "
            + "VALUES (1,'external','2025-01-01',3,'F')");
        assertThat(day(JAN31).getEndReserveCount()).as("养殖 9 同日外引单只匹配外引后备母猪").isEqualTo(1);

        execute("UPDATE t_farm_pig_introduce SET pig_sex='M' WHERE id=1");
        assertThat(day(JAN31).getEndReserveCount()).as("养殖 9 旧单只引入公猪时没有母猪名额").isZero();
    }

    @Test
    @Tag(SNAPSHOT_REPLAY)
    void replayedSnapshotCountsLegacyWholeLitterWeaningAsFatteningEntries() throws Exception {
        for (int i = 1; i <= 3; i++) {
            pig(i, "fattening", "2025-01-10", null);
        }
        execute("UPDATE t_farm_pig_info SET mother_ear='SOW-10'");
        execute("INSERT INTO t_farm_pig_pigletno (id,farrow_id,piglet_ear_no,pig_id) VALUES "
            + "(1,10,'A',1),(2,10,'B',2),(3,10,'C',3)");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) "
            + "VALUES (1,11,10,'2025-01-31',3)");

        FarmIndicatorRecord previous = day(JAN31.minusDays(1));
        assertThat(previous.getEndPigletCount()).as("养殖 11 断奶前无档案断奶日的仔猪仍在哺乳").isEqualTo(3);
        assertThat(previous.getEndFatteningCount()).as("养殖 13 断奶前期末育肥").isZero();
        FarmIndicatorRecord entry = day(JAN31);
        assertThat(entry.getBeginFatteningCount()).as("养殖 14 当日期初育肥").isZero();
        assertThat(entry.getEndFatteningCount()).as("养殖 13 整窝断奶后期末育肥").isEqualTo(3);
        assertThat(entry.getNewFatteningCount()).as("养殖 15 无逐头明细或转群日志的整窝断奶").isEqualTo(3);
        assertThat(entry.getWeanedPigletCount()).as("养殖 39 整窝断奶头数").isEqualTo(3);
        assertThat(entry.getBeginFatteningCount() + entry.getNewFatteningCount())
            .as("当日无转出或死淘，期初加新进等于期末").isEqualTo(entry.getEndFatteningCount());
        assertThat(day(JAN31)).isEqualTo(entry);
        assertThat(day(FEB1).getNewFatteningCount()).as("次日不重复计入").isZero();
    }

    @Test
    @Tag(SNAPSHOT_REPLAY)
    void replayedSnapshotCountsWholeLitterWithAnonymousWeaningDetails() throws Exception {
        for (int i = 1; i <= 3; i++) {
            pig(i, "fattening", "2025-01-10", null);
        }
        execute("UPDATE t_farm_pig_info SET mother_ear='SOW-10'");
        execute("INSERT INTO t_farm_pig_pigletno (id,farrow_id,piglet_ear_no,pig_id) VALUES "
            + "(1,10,'A',1),(2,10,'B',2),(3,10,'C',3)");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) "
            + "VALUES (1,11,10,'2025-01-31',3)");
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,ear_no,birth_farrow_id,weight) VALUES "
            + "(1,1,NULL,10,7),(2,1,NULL,10,7),(3,1,NULL,10,7)");

        assertThat(day(JAN31.minusDays(1)).getEndPigletCount()).as("养殖 11 断奶前仍有三头哺乳仔猪").isEqualTo(3);
        FarmIndicatorRecord entry = day(JAN31);
        assertThat(entry.getBeginFatteningCount()).as("养殖 14 期初育肥").isZero();
        assertThat(entry.getNewFatteningCount()).as("养殖 15 三条匿名明细按整窝日进育肥").isEqualTo(3);
        assertThat(entry.getEndFatteningCount()).as("养殖 13 期末育肥").isEqualTo(3);
        assertThat(entry.getBeginFatteningCount() + entry.getNewFatteningCount()).isEqualTo(entry.getEndFatteningCount());
        assertThat(day(FEB1).getNewFatteningCount()).as("养殖 15 次日不重复计入").isZero();
    }

    @Test
    @Tag(SNAPSHOT_REPLAY)
    void replayedSnapshotPrefersIndividualWeaningDateOverMixedLitterSummary() throws Exception {
        for (int i = 1; i <= 3; i++) {
            pig(i, "fattening", "2025-01-10", null);
        }
        execute("UPDATE t_farm_pig_info SET mother_ear='SOW-10'");
        execute("INSERT INTO t_farm_pig_pigletno (id,farrow_id,piglet_ear_no,pig_id) VALUES "
            + "(1,10,'A',1),(2,10,'B',2),(3,10,'C',3)");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) VALUES "
            + "(1,11,10,'2025-01-31',2),(2,11,10,'2025-02-02',1)");
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,ear_no,birth_farrow_id) VALUES "
            + "(1,1,'A',10),(2,1,'',10),(3,2,'C',10)");

        FarmIndicatorRecord first = day(JAN31);
        assertThat(first.getNewFatteningCount()).as("养殖 15 首日逐头 A 加匿名 B，排除尚未断奶的 C").isEqualTo(2);
        assertThat(first.getEndFatteningCount()).as("养殖 13 首日育肥两头").isEqualTo(2);
        assertThat(first.getEndPigletCount()).as("养殖 11 C 仍在哺乳").isEqualTo(1);
        assertThat(day(FEB1).getNewFatteningCount()).as("养殖 15 次日不重复计入").isZero();
        FarmIndicatorRecord second = day(FEB1.plusDays(1));
        assertThat(second.getBeginFatteningCount()).as("养殖 14 第二批前两头").isEqualTo(2);
        assertThat(second.getNewFatteningCount()).as("养殖 15 C 按逐头日转入").isEqualTo(1);
        assertThat(second.getEndFatteningCount()).as("养殖 13 第二批后共三头").isEqualTo(3);
    }

    @Test
    @Tag(SNAPSHOT_REPLAY)
    void replayedSnapshotIgnoresDeletedIndividualOrderWhenActiveSummaryExists() throws Exception {
        wholeLitterPiglets(null);
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count,del_flag) VALUES "
            + "(1,11,10,'2025-01-31',1,'1'),(2,11,10,'2025-02-02',3,'0')");
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,ear_no,birth_farrow_id) VALUES "
            + "(1,1,'E1',10),(2,2,NULL,10),(3,2,NULL,10),(4,2,NULL,10)");

        FarmIndicatorRecord before = day(FEB1);
        assertThat(before.getEndPigletCount()).isEqualTo(3);
        assertThat(before.getEndFatteningCount()).isZero();
        FarmIndicatorRecord entry = day(FEB1.plusDays(1));
        assertThat(entry.getNewFatteningCount()).as("养殖 15 删除的逐头单不影响有效整窝断奶").isEqualTo(3);
        assertThat(entry.getEndPigletCount()).isZero();
        assertThat(entry.getEndFatteningCount()).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({"ELIMINATE, true", "ELIMINATE, false", "DIE, true", "DIE, false"})
    @Tag(SNAPSHOT_REPLAY)
    void replayedSnapshotExcludesExitedPigletsFromWholeLitterWeaning(String eventType, boolean anonymousDetails)
        throws Exception {
        wholeLitterPiglets(null);
        execute("UPDATE t_farm_pig_info SET pig_type='piglet' WHERE id=3");
        change(3, eventType, "", "END", 1L, "2025-01-20 09:00:00", null, null);
        if ("DIE".equals(eventType)) {
            execute("INSERT INTO t_farm_pig_death (id,pig_id,death_date,death_pig_type) "
                + "VALUES (1,3,'2025-01-20 09:00:00','piglet')");
            execute("UPDATE t_farm_pig_pigletno SET del_flag='1', del_unique=id WHERE pig_id=3");
        } else {
            execute("INSERT INTO t_farm_pig_culling (id,pig_id,culling_date) VALUES (1,3,'2025-01-20 09:00:00')");
        }
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) "
            + "VALUES (1,11,10,'2025-01-31',2)");
        if (anonymousDetails) {
            execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,ear_no,birth_farrow_id) VALUES "
                + "(1,1,NULL,10),(2,1,NULL,10)");
        }

        assertThat(day(JAN31.minusDays(1)).getEndPigletCount()).isEqualTo(2);
        FarmIndicatorRecord entry = day(JAN31);
        assertThat(entry.getBeginFatteningCount()).isZero();
        assertThat(entry.getEndFatteningCount()).isEqualTo(2);
        assertThat(entry.getNewFatteningCount()).as("养殖 15 已离群仔猪不参加整窝断奶").isEqualTo(2);
    }

    @Test
    @Tag(SNAPSHOT_REPLAY)
    void replayedSnapshotKeepsWholeLitterWeaningOnRecalculationAfterLaterCulling() throws Exception {
        wholeLitterPiglets(null);
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) "
            + "VALUES (1,11,10,'2025-01-31',3)");

        FarmIndicatorRecord original = day(JAN31);
        recordLitterCulling(3, "2025-02-05 09:00:00", "2025-02-05 09:00:00");

        assertThat(original.getNewFatteningCount()).isEqualTo(3);
        assertThat(original.getEndFatteningCount()).isEqualTo(3);
        assertThat(original.getFatteningHeadDays()).isEqualTo(3);
        assertThat(day(JAN31)).isEqualTo(original);
    }

    @ParameterizedTest
    @CsvSource({
        "00:00:00, true",
        "00:00:00, false",
        "10:00:00, false"
    })
    @Tag(SNAPSHOT_REPLAY)
    void replayedSnapshotPrefersPigletsStillInGroupAfterSameDayCulling(String weaningTime, boolean anonymousDetails)
        throws Exception {
        wholeLitterPiglets(null);
        execute("UPDATE t_farm_pig_info SET pig_type='piglet' WHERE id=3");
        recordLitterCulling(3, "2025-01-31 09:00:00", "2025-01-31 09:00:00");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) VALUES (1,11,10,?,2)",
            "2025-01-31 " + weaningTime);
        if (anonymousDetails) {
            execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,ear_no,birth_farrow_id) VALUES "
                + "(1,1,NULL,10),(2,1,NULL,10)");
        }

        assertThat(day(JAN31.minusDays(1)).getEndFatteningCount()).isZero();
        FarmIndicatorRecord entry = day(JAN31);
        assertThat(entry.getBeginFatteningCount()).isZero();
        assertThat(entry.getNewFatteningCount()).isEqualTo(2);
        assertThat(entry.getEndFatteningCount()).isEqualTo(2);
        assertThat(entry.getFatteningHeadDays()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
        "00:00:00, 00:00:00, 2025-02-01 10:00:00",
        "09:00:00, 10:00:00, 2025-01-31 10:00:00"
    })
    @Tag(SNAPSHOT_REPLAY)
    void replayedSnapshotCountsWholeLitterWeaningBeforeSameDayCulling(
        String weaningTime, String cullingTime, String cullingRecordedAt
    ) throws Exception {
        wholeLitterPiglets(null);
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) VALUES (1,11,10,?,3)",
            "2025-01-31 " + weaningTime);
        recordLitterCulling(3, "2025-01-31 " + cullingTime, cullingRecordedAt);

        assertThat(day(JAN31.minusDays(1)).getEndFatteningCount()).isZero();
        FarmIndicatorRecord entry = day(JAN31);
        assertThat(entry.getBeginFatteningCount()).isZero();
        assertThat(entry.getNewFatteningCount()).isEqualTo(3);
        assertThat(entry.getEndFatteningCount()).isEqualTo(2);
        assertThat(entry.getFatteningHeadDays()).isEqualTo(3);
        assertThat(entry.getWeanedPigletCount()).isEqualTo(3);
    }

    @Test
    @Tag(SNAPSHOT_REPLAY)
    void replayedSnapshotCapsWholeLitterFallbackAfterMatchingEarTags() throws Exception {
        wholeLitterPiglets(null);
        execute("UPDATE t_farm_pig_info SET pig_type='piglet' WHERE id=1");
        pig(4, "fattening", "2025-01-10", null);
        execute("UPDATE t_farm_pig_info SET mother_ear='SOW-10' WHERE id=4");
        execute("INSERT INTO t_farm_pig_pigletno (id,farrow_id,piglet_ear_no,pig_id) VALUES (4,10,'E4',4)");
        recordLitterCulling(1, "2025-01-31 09:00:00", "2025-01-31 09:00:00");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) "
            + "VALUES (1,11,10,'2025-01-31',2)");
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,ear_no,birth_farrow_id) VALUES "
            + "(1,1,'E2',10),(2,1,NULL,10)");

        FarmIndicatorRecord entry = day(JAN31);
        assertThat(entry.getNewFatteningCount()).isEqualTo(2);
        assertThat(entry.getEndFatteningCount()).isEqualTo(2);
        assertThat(entry.getFatteningHeadDays()).isEqualTo(2);
        assertThat(entry.getEndPigletCount()).isEqualTo(1);
        var weanings = session.getMapper(ProductionIndicatorMapper.class).individualWeanings(TENANT, JAN31);
        assertThat(weanings).filteredOn(weaning -> JAN31.equals(weaning.weanDate()))
            .extracting(weaning -> weaning.pigId()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @Tag(SNAPSHOT_REPLAY)
    void replayedSnapshotPrefersPigRecordWeaningDateOverLitterSummary() throws Exception {
        wholeLitterPiglets("2025-02-02");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) "
            + "VALUES (1,11,10,'2025-01-31',2)");

        FarmIndicatorRecord summaryDay = day(JAN31);
        assertThat(summaryDay.getNewFatteningCount()).as("养殖 15 E1 的猪档断奶日优先").isEqualTo(2);
        assertThat(summaryDay.getEndPigletCount()).isEqualTo(1);
        assertThat(summaryDay.getEndFatteningCount()).isEqualTo(2);
        assertThat(day(FEB1).getNewFatteningCount()).isZero();
        FarmIndicatorRecord savedDay = day(FEB1.plusDays(1));
        assertThat(savedDay.getBeginFatteningCount()).isEqualTo(2);
        assertThat(savedDay.getNewFatteningCount()).isEqualTo(1);
        assertThat(savedDay.getEndPigletCount()).isZero();
        assertThat(savedDay.getEndFatteningCount()).isEqualTo(3);
    }

    @Test
    @Tag(SNAPSHOT_REPLAY)
    void replayedSnapshotKeepsUnweanedPigletAndIgnoresDeletedEarTag() throws Exception {
        pig(1, "fattening", "2025-01-10", "2025-01-31");
        pig(2, "fattening", "2025-01-10", "2025-02-02");
        pig(3, "piglet", "2025-01-10", null);
        execute("UPDATE t_farm_pig_info SET mother_ear='SOW-10'");
        execute("INSERT INTO t_farm_pig_pigletno (id,farrow_id,piglet_ear_no,pig_id,del_flag,del_unique) VALUES "
            + "(1,10,'A',1,'0',0),(2,10,'B',2,'0',0),(3,10,'A',3,'1',3),(4,10,'C',3,'0',0)");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) VALUES "
            + "(1,11,10,'2025-01-31',1),(2,11,10,'2025-02-02',1)");
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,ear_no,birth_farrow_id) VALUES "
            + "(1,1,'A',10),(2,2,'B',10)");

        FarmIndicatorRecord first = day(JAN31);
        assertThat(first.getEndPigletCount()).as("养殖 11 第二批和未断奶仔猪保留，删除耳号不配对").isEqualTo(2);
        assertThat(first.getEndFatteningCount()).as("养殖 13 首批只进一头").isEqualTo(1);
        assertThat(first.getNewFatteningCount()).as("养殖 15 首批只进一头").isEqualTo(1);
        FarmIndicatorRecord second = day(FEB1.plusDays(1));
        assertThat(second.getEndPigletCount()).as("养殖 11 无断奶日、无断奶明细的哺乳仔猪仍在栏").isEqualTo(1);
        assertThat(second.getEndFatteningCount()).as("养殖 13 第二批后两头").isEqualTo(2);
        assertThat(second.getNewFatteningCount()).as("养殖 15 第二批当天只进一头").isEqualTo(1);
    }

    @Test
    void staggeredWeaningKeepsPigletsUntilTheirIndividualDay() throws Exception {
        LocalDate feb2 = FEB1.plusDays(1);
        pig(1, "fattening", "2025-01-10", "2025-01-31");
        pig(2, "fattening", "2025-01-10", "2025-02-02");
        for (int i = 1; i <= 2; i++) {
            snapshot(i, "fattening", "");
            snapshot(feb2, i, "fattening", "");
        }
        FarmIndicatorRecord jan31 = day(JAN31);
        assertThat(jan31.getEndFatteningCount()).as("养殖 13 首批期末仅 1 头").isEqualTo(1);
        assertThat(jan31.getEndPigletCount()).as("养殖 11 第二批仍在哺乳").isEqualTo(1);
        assertThat(day(feb2).getEndFatteningCount()).as("养殖 13 第二批后 2 头").isEqualTo(2);
        assertThat(day(feb2).getBeginFatteningCount()).as("养殖 14 缺 2-01 日报，不能跳日").isNull();
    }

    @Test
    void tenPigletsCountOnlyActualEntriesAndIncludeOtherFatteningTransfers() throws Exception {
        for (int i = 1; i <= 10; i++) {
            pig(i, "fattening", "2025-01-10", i <= 4 ? "2025-01-31" : "2025-02-02");
            snapshot(i, "fattening", "");
        }
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) "
            + "VALUES (1,11,1,'2025-01-31',4)");
        FarmIndicatorRecord first = day(JAN31);
        assertThat(first.getEndPigletCount()).as("养殖 11 十头同窝仅四头断奶").isEqualTo(6);
        assertThat(first.getNewFatteningCount()).as("养殖 15 四头实际入群").isEqualTo(4);
        assertThat(first.getWeanedPigletCount()).as("养殖 29、39 主单无明细").isEqualTo(4);

        execute("UPDATE t_farm_pig_info SET wean_date='2025-02-02' WHERE id=4");
        execute("UPDATE t_farm_pig_weaning SET weaned_count=3 WHERE id=1");
        change(1, "TRANSFER", "", "", null, "2025-01-31 10:00:00", "piglet", "fattening");
        pig(11, "sow", null, null);
        snapshot(11, "sow", "DN");
        change(11, "TRANSFER", "DN", "DN", null, "2025-01-31 10:00:00", null, null);
        FarmIndicatorRecord corrected = day(JAN31);
        assertThat(corrected.getNewFatteningCount()).as("养殖 15 断奶三头，重复转群及母猪均不增加").isEqualTo(3);
        assertThat(corrected.getEndNonprodSowCount()).as("养殖 7 待配母猪").isEqualTo(1);
        pig(12, "fattening", null, null);
        snapshot(12, "fattening", "");
        change(12, "TRANSFER", "", "", null, "2025-01-31 10:00:00", "piglet", "fattening");
        change(12, "TRANSFER", "", "", null, "2025-01-31 11:00:00", "fattening", "fattening");
        FarmIndicatorRecord transferred = day(JAN31);
        assertThat(transferred.getNewFatteningCount()).as("养殖 15 另有一头非断奶转育肥").isEqualTo(4);
        assertThat(transferred.getWeanedPigletCount()).as("养殖 39 仍只有断奶三头").isEqualTo(3);
        assertThat(day(JAN31)).isEqualTo(transferred);
    }

    @Test
    void correctedWeaningBusinessDateOverridesStalePigSnapshot() throws Exception {
        LocalDate feb2 = FEB1.plusDays(1);
        pig(1, "fattening", "2025-01-10", "2025-01-31");
        snapshot(1, "fattening", "");
        snapshot(feb2, 1, "fattening", "");
        execute("INSERT INTO t_farm_pig_pigletno (id,farrow_id,piglet_ear_no,pig_id) VALUES (1,10,'A',1)");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) "
            + "VALUES (1,11,10,'2025-01-31',1)");
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,ear_no,birth_farrow_id) VALUES (1,1,'A',10)");
        assertThat(day(JAN31).getEndFatteningCount()).isEqualTo(1);
        assertThat(day(JAN31).getNewFatteningCount()).isEqualTo(1);

        execute("UPDATE t_farm_pig_weaning SET weaning_date='2025-02-02' WHERE id=1");
        FarmIndicatorRecord corrected = day(JAN31);
        assertThat(corrected.getEndPigletCount()).as("养殖 11 断奶单改期后仍哺乳").isEqualTo(1);
        assertThat(corrected.getEndFatteningCount()).as("养殖 13 不读过期档案日期").isZero();
        assertThat(corrected.getNewFatteningCount()).as("养殖 15 从旧业务日移出").isZero();
        assertThat(day(feb2).getNewFatteningCount()).as("养殖 15 改按新业务日入群").isEqualTo(1);

        execute("UPDATE t_farm_pig_weaning SET del_flag='1' WHERE id=1");
        assertThat(day(feb2).getNewFatteningCount()).as("养殖 15 撤销断奶").isZero();
        assertThat(day(feb2).getEndPigletCount()).as("养殖 11 撤销后个体仍按哺乳计").isEqualTo(1);
    }

    @Test
    void openingStockRequiresPreviousCalendarDayAndFollowsItsCorrection() throws Exception {
        LocalDate nov1 = LocalDate.of(2025, 11, 1);
        LocalDate nov23 = nov1.withDayOfMonth(23);
        LocalDate nov24 = nov23.plusDays(1);
        assertThat(day(nov1).getBeginProductionSowCount()).as("养殖 5 缺 10-31 日报").isNull();
        assertThat(day(nov1).getBeginFatteningCount()).as("养殖 14 缺 10-31 日报").isNull();
        for (int i = 1; i <= 91; i++) {
            snapshot(nov23, i, "sow", "PZ");
        }
        snapshot(nov23, 100, "fattening", "");
        assertThat(day(nov24).getBeginProductionSowCount()).isNull();
        day(nov23);
        FarmIndicatorRecord next = readDay(nov24);
        assertThat(next.getBeginProductionSowCount()).as("养殖 5 前一日报期末 91").isEqualTo(91);
        assertThat(next.getBeginFatteningCount()).as("养殖 14 前一日报期末 1").isEqualTo(1);
        assertThat(day(nov24)).isEqualTo(next);
        execute("DELETE FROM t_farm_pig_snapshot WHERE snap_date = ? AND pig_id = 100", nov23);
        day(nov23);
        assertThat(readDay(nov24).getBeginFatteningCount()).as("养殖 14 前日更正为实点 0").isZero();
        execute("UPDATE t_farm_indicator_record SET del_flag='1' WHERE stat_date = ?", nov23);
        FarmIndicatorRecord missing = day(nov24);
        assertThat(missing.getBeginProductionSowCount()).isNull();
        assertThat(missing.getBeginFatteningCount()).isNull();
    }

    @Test
    void firstReserveBreedingUsesEventAndDeduplicatesLaterBreeding() throws Exception {
        execute("INSERT INTO t_farm_pig_breeding (id,pig_id,breeding_date) VALUES "
            + "(10,1,'2025-01-31'),(11,1,'2025-02-01'),(12,2,'2025-01-31'),(13,3,'2025-01-30'),(14,3,'2025-01-31')");
        change(1, "BREED", "HB", "PZ", 10L, "2025-01-31 08:00:00", null, null);
        change(1, "BREED", "FQ", "PZ", 11L, "2025-02-01 08:00:00", null, null);
        change(2, "BREED", "DN", "PZ", 12L, "2025-01-31 08:00:00", null, null);
        change(3, "BREED", "HB", "PZ", 13L, "2025-01-30 08:00:00", null, null);
        change(3, "BREED", "HB", "PZ", 14L, "2025-01-31 08:00:00", null, null);
        FarmIndicatorRecord jan31 = day(JAN31);
        assertThat(jan31.getNewProductionSowCount()).as("养殖 6").isEqualTo(1);
        execute("INSERT INTO t_farm_pig_breeding (id,pig_id,breeding_date) VALUES (15,1,'2025-01-31 10:00:00')");
        assertThat(day(JAN31).getBreedingSowCount()).as("养殖 16 同头同日重复配种只计一次").isEqualTo(3);
        assertThat(day(FEB1).getNewProductionSowCount()).isZero();
        rollups(JAN, FEB);
        assertThat(month(JAN).getReserveFirstBreedCount()).as("养殖 47").isEqualTo(2);
        assertThat(month(FEB).getReserveFirstBreedCount()).isZero();
    }

    @Test
    void abnormalitiesAndFarrowingUseBusinessDateAndPreserveRecordedZero() throws Exception {
        execute("INSERT INTO t_farm_pig_abnormal (id,pig_id,abnormal_date,abnormal_type,create_time) VALUES "
            + "(1,1,'2025-01-31','R','2025-01-31'),(2,1,'2025-01-31','N','2025-01-31'),"
            + "(3,2,'2025-01-31','A','2025-02-01'),(4,3,'2025-02-01','A','2025-02-01')");
        execute("INSERT INTO t_farm_pig_abnormal (id,pig_id,abnormal_date,abnormal_type) VALUES "
            + "(5,1,'2025-01-31 10:00:00','R'),(6,2,'2025-01-31','R'),(7,1,'2025-01-31','N'),"
            + "(8,2,'2025-01-31','A'),(9,3,'2025-01-31','A')");
        execute("UPDATE t_farm_pig_abnormal SET del_flag='1' WHERE id=9");
        change(1, "BREED", "KH", "PZ", null, "2025-01-31 14:00:00", null, null);
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date,parity,dead_born,mummy_born,"
            + "deformed_born,weak_culled,weak_born,create_time) VALUES "
            + "(1,1,'2025-01-31',1,2,2,0,1,3,'2025-02-01'),"
            + "(2,2,'2025-01-31',NULL,1,NULL,NULL,NULL,5,'2025-01-31'),"
            + "(3,1,'2025-01-31',1,NULL,NULL,NULL,NULL,NULL,'2025-01-31'),"
            + "(4,3,'2025-02-01',2,NULL,NULL,NULL,NULL,NULL,'2025-02-01')");
        execute("UPDATE t_farm_pig_farrow SET total_born=12, live_born=10 WHERE id=1");
        execute("UPDATE t_farm_pig_farrow SET total_born=14, live_born=11 WHERE id=2");
        FarmIndicatorRecord day = day(JAN31);
        assertThat(day.getReturnHeatCount()).as("养殖 17 三条有效记录属于两头").isEqualTo(2);
        assertThat(day.getEmptySowCount()).as("养殖 18 同头同日两条只计一头").isEqualTo(1);
        assertThat(day.getAbortionCount()).as("养殖 19 同头重复、撤销的另一头均不重复计入").isEqualTo(1);
        assertThat(day.getTotalBornCount()).as("养殖 20 直接按总仔列").isEqualTo(26);
        assertThat(day.getLiveBornCount()).as("养殖 21").isEqualTo(21);
        assertThat(day.getFarrowSowCount()).as("养殖 26 同母同日三单只两头").isEqualTo(2);
        assertThat(day.getAvgLiveBornPerLitter()).as("养殖 27 两窝活仔 10、11").isEqualByComparingTo("10.500");
        assertThat(day(JAN31.minusDays(1)).getAvgLiveBornPerLitter()).as("养殖 27 分母零").isZero();
        assertThat(day.getAbnormalSowCount()).isEqualTo(2);
        assertThat(day.getFarrowDeadBornCount()).as("养殖 22").isEqualTo(3);
        assertThat(day.getFarrowMummyBornCount()).as("养殖 23").isEqualTo(2);
        assertThat(day.getFarrowDeformedBornCount()).as("养殖 24 实录零").isZero();
        assertThat(day.getFarrowWeakCulledCount()).as("养殖 25").isEqualTo(1);
        assertThat(day(FEB1).getFarrowDeformedBornCount()).as("养殖 24 历史缺值").isNull();
        rollups(JAN, FEB);
        assertThat(month(JAN).getFarrowSowCount()).as("养殖 56").isEqualTo(2);
        assertThat(month(JAN).getAbortionCount()).as("养殖 95 同母同日两条有效记录按日去重").isEqualTo(1);
        assertThat(month(FEB).getAbortionCount()).isEqualTo(1);
        assertThat(month(JAN).getFirstParityFarrowSowCount()).as("养殖 105").isEqualTo(1);
        assertThat(month(FEB).getFirstParityFarrowSowCount()).isZero();
        execute("UPDATE t_farm_pig_farrow SET deformed_born=NULL WHERE id=1");
        assertThat(day(JAN31).getFarrowDeformedBornCount()).isNull();
    }

    @Test
    void pigletAveragesUseValidIndividualsAndActualBatchMonth() throws Exception {
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date) VALUES (1,11,'2025-01-06')");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count,weaned_weight) VALUES "
            + "(1,11,1,'2025-01-31',20,200),(2,11,1,'2025-02-01',1,8)");
        execute("INSERT INTO t_farm_pig_introduce (id,introduce_type,introduce_date,pig_count,pig_sex)"
            + " VALUES (1,'external','2025-01-31',2,'F')");
        pig(1, "fattening", "2025-01-10", null);
        pig(2, "fattening", "2025-01-06", null);
        pig(3, "fattening", "2025-01-10", null);
        execute("INSERT INTO t_farm_pig_pigletno (id,farrow_id,piglet_ear_no,pig_id) VALUES "
            + "(1,1,'A',1),(2,1,'B',2),(3,1,'C',3)");
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,ear_no,birth_farrow_id,weight) VALUES "
            + "(1,1,'A',1,5),(2,1,'B',1,6),(3,1,'UNKNOWN',NULL,7),(4,1,NULL,NULL,NULL),(5,2,'C',1,8)");
        FarmIndicatorRecord day = day(JAN31);
        assertThat(day.getAvgWeanAgeDays()).as("养殖 30").isEqualByComparingTo("23");
        assertThat(day.getIntroduceSowCount()).as("同日外购引种两头").isEqualTo(2);
        assertThat(day.getWeanedPigletCount()).as("养殖 39 含无称重头，排除两头外购").isEqualTo(4);
        assertThat(day.getWeanTotalWeight()).as("养殖 40 明细/主单择一").isEqualByComparingTo("18");
        assertThat(day.getAvgEntryWeight()).as("养殖 41").isEqualByComparingTo("6");
        assertThat(day.getWeanWeightValidCount()).isEqualTo(3);
        assertThat(day.getAvgWeanAgeDays()).as("养殖 42 进栏与断奶用同一日龄列").isEqualByComparingTo("23");
        assertThat(day.getWeanAgeDaysSum()).isEqualTo(46);
        assertThat(day.getWeanAgeValidCount()).isEqualTo(2);
        assertThat(day(JAN31.minusDays(1)).getAvgWeanAgeDays()).as("养殖 30、42 无日龄样本").isZero();
        assertThat(day(FEB1).getAvgWeanAgeDays()).isEqualByComparingTo("22");
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date) VALUES (2,22,'2025-01-14')");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) "
            + "VALUES (4,22,2,'2025-01-29',1)");
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,birth_farrow_id,weight) VALUES (6,4,2,0)");
        assertThat(day(JAN31.minusDays(2)).getAvgEntryWeight()).as("养殖 41 无有效称重").isZero();
        var vo = new FarmIndicatorRecordToFarmIndicatorRecordVoMapperImpl().convert(day);
        assertThat(vo.getAvgWeanAgeDays()).isEqualByComparingTo("23");
        assertThat(vo.getAvgEntryWeight()).isEqualByComparingTo("6");
        rollups(JAN, FEB);
        assertThat(month(JAN).getAvgWeanAgeDays()).as("养殖 71 一月按有效头数加权 (21+25+15)/3").isEqualByComparingTo("20.3");
        assertThat(month(FEB).getAvgWeanAgeDays()).as("养殖 71 跨月第二批").isEqualByComparingTo("22");
    }

    @Test
    void dailyWeaningAgeRoundsToOneDecimalAndExcludesUnknownDates() throws Exception {
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date) VALUES "
            + "(1,11,'2025-01-10'),(2,12,'2025-01-09')");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) VALUES "
            + "(1,11,1,'2025-01-31',4),(2,11,1,'2025-02-01',1)");
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,birth_farrow_id,weight) VALUES "
            + "(1,1,1,5),(2,1,1,5),(3,1,2,6),(4,1,NULL,NULL),(5,2,NULL,NULL)");

        FarmIndicatorRecord row = day(JAN31);
        assertThat(row.getAvgWeanAgeDays()).as("养殖 30、42：21、21、22，缺出生日的排除").isEqualByComparingTo("21.3");
        assertThat(row.getWeanAgeDaysSum()).isEqualTo(64);
        assertThat(row.getWeanAgeValidCount()).isEqualTo(3);
        assertThat(row.getAvgEntryWeight()).as("养殖 41：16 kg 除以已称三头").isEqualByComparingTo("5.333");
        assertThat(row.getWeanWeightValidCount()).isEqualTo(3);
        FarmIndicatorRecord missing = day(FEB1);
        assertThat(missing.getWeanedPigletCount()).isEqualTo(1);
        assertThat(missing.getAvgWeanAgeDays()).as("养殖 30、42 有断奶但全部缺出生日").isZero();
        assertThat(missing.getAvgEntryWeight()).as("养殖 41 有断奶但没有称重样本").isZero();
    }

    @Test
    void maternalWeanCohortUsesOwnEventBirthLitterAndActualCycle() throws Exception {
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date) VALUES "
            + "(1,11,'2025-01-10'),(2,12,'2025-01-09'),(3,13,'2025-01-12'),"
            + "(4,14,'2025-01-15'),(5,14,'2024-10-15')");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) VALUES "
            + "(1,11,1,'2025-01-31',2),(2,11,1,'2025-02-01',2),"
            + "(3,12,2,'2025-01-31',2),(4,13,3,'2025-01-31',0)");
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,birth_farrow_id) VALUES "
            + "(1,1,1),(2,1,1),(3,2,1),(4,2,1),(5,3,2),(6,3,4)");
        change(11, "WEAN", "FM", "DN", 1L, "2025-01-31 11:00:00", null, null);
        change(11, "WEAN", "FM", "DN", 2L, "2025-02-01 12:00:00", null, null);
        change(12, "WEAN", "FM", "DN", 3L, "2025-01-31 11:00:00", null, null);
        change(13, "WEAN", "FM", "DN", 4L, "2025-01-31 11:00:00", null, null);
        change(14, "WEAN", "FM", "DN", null, "2025-01-31 11:01:00", null, null);
        change(14, "WEAN", "FM", "DN", null, "2024-11-01 11:01:00", null, null);
        assertThat(day(JAN31).getWeaningSowCount()).as("养殖 28 母猪事件，含无关联单寄养生母").isEqualTo(4);
        assertThat(day(FEB1).getWeaningSowCount()).as("养殖 28 同窝第二批").isZero();
        rollups(JAN, FEB);
        MonthlyProduction jan = month(JAN);
        MonthlyProduction feb = month(FEB);
        assertThat(jan.getWeaningSowCount()).as("养殖 92").isEqualTo(4);
        assertThat(feb.getWeaningSowCount()).as("养殖 92 跨月重复日志").isZero();
        assertThat(jan.getAvgWeanedPerLitter()).as("养殖 68 4头÷4窝").isEqualByComparingTo("1");
        assertThat(feb.getAvgWeanedPerLitter()).as("养殖 68 2头÷0窝").isZero();
        assertThat(jan.getNormalWeaningSowCount()).as("养殖 99 剔除寄养双方和零仔").isEqualTo(1);
        assertThat(jan.getLactationDaysSum()).as("养殖 100 21+22+19+16").isEqualTo(78);
        assertThat(jan.getLactationValidLitterCount()).as("养殖 100").isEqualTo(4);
        assertThat(jan.getAvgLactationDaysWeaned()).as("养殖 100 断奶月 78天÷4窝").isEqualByComparingTo("19.5");
        assertThat(service.getMonthlyProductionStats(JAN).getCurrentIndicators().avgLactationDaysWeaned())
            .isEqualByComparingTo("19.5");
        assertThat(year().getTotalWeanedSow()).isEqualTo(4);
    }

    @Test
    void lactationAverageByFarrowMonthIncludesSowsNotYetWeanedInDenominator() throws Exception {
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date) VALUES "
            + "(1,11,'2025-01-20'),(2,12,'2025-01-21')");
        rollups(JAN);
        assertThat(month(JAN).getFarrowSowCount()).isEqualTo(2);
        assertThat(month(JAN).getLactationDaysSumByFarrow()).isZero();

        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) VALUES "
            + "(1,11,1,'2025-02-10',7),(2,11,1,'2025-02-12',2)");
        change(11, "WEAN", "FM", "DN", 1L, "2025-02-10 09:00:00", null, null);
        change(11, "WEAN", "FM", "DN", 2L, "2025-02-12 09:00:00", null, null);
        rollups(JAN, FEB);

        MonthlyProduction jan = month(JAN);
        MonthlyProduction feb = month(FEB);
        assertThat(jan.getLactationDaysSumByFarrow()).as("养殖 100 2月断奶的21天归1月分娩").isEqualTo(21);
        assertThat(jan.getAvgLactationDaysFarrowed()).as("养殖 100 分娩月 21天÷2头").isEqualByComparingTo("10.5");
        assertThat(jan.getAvgLactationDaysWeaned()).isZero();
        assertThat(feb.getAvgLactationDaysWeaned()).isEqualByComparingTo("21");
        assertThat(feb.getLactationDaysSumByFarrow()).isZero();
        assertThat(feb.getAvgLactationDaysFarrowed()).isZero();
        var indicators = service.getMonthlyProductionStats(JAN).getCurrentIndicators();
        assertThat(indicators.lactationDaysSumByFarrow()).isEqualTo(21);
        assertThat(indicators.avgLactationDaysFarrowed()).isEqualByComparingTo("10.5");
    }

    @Test
    void biologicalLitterAverageIncludesLaterBatchesByBirthKey() throws Exception {
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date) VALUES "
            + "(1,11,'2025-01-10'),(2,12,'2025-01-09'),(3,13,'2025-01-12')");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) VALUES "
            + "(1,11,1,'2025-01-31',3),(2,11,1,'2025-02-01',1),(3,11,1,'2025-01-31',1)");
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,birth_farrow_id) VALUES "
            + "(1,1,1),(2,1,2),(3,3,3),(4,1,NULL),(5,2,1)");
        change(11, "WEAN", "FM", "DN", 1L, "2025-01-31 11:00:00", null, null);
        change(12, "WEAN", "FM", "DN", null, "2025-01-31 11:01:00", null, null);
        rollups(JAN, FEB);
        assertThat(month(JAN).getAvgWeanedPerLitter()).as("养殖 68 4头÷2窝").isEqualByComparingTo("2");
        assertThat(month(JAN).getBirthLitterWeanedPigletCount()).as("养殖 102 生母本月归属4头，含跨月追加1头").isEqualTo(4);
        assertThat(month(JAN).getAvgBirthLitterWeanedPiglets()).as("养殖 102 4头÷2窝").isEqualByComparingTo("2");
        assertThat(month(FEB).getAvgBirthLitterWeanedPiglets()).isZero();
        assertThat(service.getMonthlyProductionStats(JAN).getCurrentIndicators().avgBirthLitterWeanedPiglets())
            .isEqualByComparingTo("2");
    }

    @Test
    void biologicalLitterAverageUsesMainCountWithoutDetails() throws Exception {
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date) VALUES (1,11,'2025-01-10')");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count)"
            + " VALUES (1,11,1,'2025-01-31',10)");
        change(11, "WEAN", "FM", "DN", 1L, "2025-01-31 11:00:00", null, null);

        rollups(JAN);

        MonthlyProduction jan = month(JAN);
        assertThat(jan.getAvgWeanedPerLitter()).as("养殖 68 主单10头÷1窝").isEqualByComparingTo("10");
        assertThat(jan.getBirthLitterWeanedPigletCount()).as("养殖 102 主单10头归本窝").isEqualTo(10);
        assertThat(jan.getAvgBirthLitterWeanedPiglets()).as("养殖 102 本窝10头÷1窝").isEqualByComparingTo("10");
    }

    @Test
    void marketingSharesPointInTimeFatteningCohortAndUsesWeightedPeriodAverages() throws Exception {
        for (int i = 1; i <= 9; i++) {
            pig(i, i == 1 ? "sow" : "fattening", null, null);
        }
        marketing(1, "2025-01-31", 100, "SLAUGHTER");
        marketing(2, "2025-02-01", 110, "SLAUGHTER");
        marketing(3, "2025-02-01", 90, "SLAUGHTER");
        marketing(4, "2025-02-02", 160, "SLAUGHTER");
        marketing(5, "2025-02-01", 300, "SLAUGHTER");
        marketing(6, "2025-02-01", 400, "ELIMINATE");
        marketing(7, "2025-02-01", 500, "SLAUGHTER");
        marketing(8, "2024-12-31", 600, "SLAUGHTER");
        marketing(9, "2025-02-01", 700, null);
        change(1, "INTRO", "YF", "HB", null, "2025-02-02 09:00:00", "fattening", "sow");
        change(5, "TO_FATTEN", "HB", "YF", null, "2025-02-01 09:00:00", "sow", "fattening");
        execute("UPDATE t_farm_pig_marketing SET del_flag='1' WHERE id=7");
        FarmIndicatorRecord feb1 = day(FEB1);
        assertThat(feb1.getMarketingPigCount()).as("养殖 31 混合类型、死淘和撤销").isEqualTo(2);
        assertThat(feb1.getMarketingWeight()).as("养殖 32").isEqualByComparingTo("200");
        assertThat(feb1.getAvgMarketingWeight()).as("养殖 33 (110+90)/2").isEqualByComparingTo("100");
        assertThat(day(JAN31).getMarketingPigCount()).isEqualTo(1);
        day(FEB1.plusDays(1));
        rollups(JAN, FEB);
        assertThat(month(JAN).getMarketingCount()).as("养殖 73 月底出栏，类型变更后来补录").isEqualTo(1);
        assertThat(month(FEB).getMarketingCount()).isEqualTo(3);
        assertThat(month(FEB).getAvgMarketingWeight()).as("养殖 74 360/3，区分日均值平均").isEqualByComparingTo("120");
        assertThat(year().getMarketingCount()).as("养殖 114 跨年补录").isEqualTo(4);
        assertThat(year().getMarketingWeight()).as("养殖 115").isEqualByComparingTo("460");
        assertThat(year().getAvgMarketingWeight()).as("养殖 116 同一名单 460/4").isEqualByComparingTo("115");
        assertThat(service.getAnnualIndicator((int) YEAR).getAvgMarketingWeight()).isEqualByComparingTo("115");
    }

    @Test
    void annualAverageUsesScopeExampleAndIgnoresOldMonthlyTotals() throws Exception {
        for (int i = 1; i <= 4; i++) {
            pig(i, "fattening", null, null);
        }
        marketing(1, "2025-01-31", 100, "SLAUGHTER");
        marketing(2, "2025-02-01", 110, "SLAUGHTER");
        marketing(3, "2025-02-01", 130, "SLAUGHTER");
        marketing(4, "2025-02-01", 600, "ELIMINATE");
        day(JAN31);
        day(FEB1);
        rollups(JAN, FEB);
        assertThat(year().getAvgMarketingWeight()).as("养殖 116 340/3").isEqualByComparingTo("113.333");
        execute("UPDATE t_farm_monthly_production SET marketing_count=99, marketing_weight=10000");
        rollups(FEB);
        assertThat(year().getMarketingCount()).isEqualTo(3);
        assertThat(year().getMarketingWeight()).isEqualByComparingTo("340");
    }

    @Test
    void deathAndCullingUseBusinessDateAndRebuildIsIdempotent() throws Exception {
        change(1, "DIE", "YF", "END", null, "2025-01-31 13:00:00", null, null);
        change(2, "ELIMINATE", "DN", "END", null, "2025-01-31 14:00:00", null, null);
        change(3, "SLAUGHTER", "YF", "END", null, "2025-01-31 15:00:00", null, null);
        execute("UPDATE t_farm_status_record SET create_time='2025-02-01'");
        FarmIndicatorRecord first = day(JAN31);
        assertThat(first.getDeathPigCount()).as("养殖 34").isEqualTo(1);
        assertThat(first.getCullingPigCount()).as("养殖 35").isEqualTo(1);
        assertThat(day(FEB1).getDeathPigCount()).isZero();
        rollups(JAN, FEB);
        assertThat(month(JAN).getDeathCount()).as("养殖 89").isEqualTo(1);
        assertThat(month(JAN).getCullingCount()).as("养殖 90").isEqualTo(1);
        assertThat(month(FEB).getDeathCount()).isZero();
        assertThat(month(FEB).getCullingCount()).isZero();
        MonthlyProduction firstMonth = month(JAN);
        AnnualIndicator firstYear = year();
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getAopProxy(service)).thenReturn(service);
            assertThat(service.triggerAggregateRange(JAN31, FEB1)).contains("ok=2", "failed=[]");
        }
        assertThat(days.selectById(first.getId())).isEqualTo(first);
        assertThat(month(JAN)).isEqualTo(firstMonth);
        assertThat(year()).isEqualTo(firstYear);
    }

    @Test
    void deathCullRebuildMovesTheCountToTheLastValidOperation() throws Exception {
        LocalDate nov10 = LocalDate.of(2025, 11, 10);
        LocalDate nov12 = nov10.plusDays(2);
        execute("INSERT INTO t_farm_pig_culling (id,pig_id,culling_date,create_time) "
            + "VALUES (1,1,'2025-11-10','2025-11-11')");
        assertThat(day(nov10).getDeathCullCount()).as("养殖 36 首次 11-10 淘汰").isEqualTo(1);

        execute("INSERT INTO t_farm_pig_death (id,pig_id,death_date,death_pig_type,create_time) "
            + "VALUES (2,1,'2025-11-12','fattening','2025-11-14')");
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getAopProxy(service)).thenReturn(service);
            assertThat(service.triggerAggregateRange(nov10, nov12)).contains("ok=3", "failed=[]");
        }
        assertThat(readDay(nov10).getDeathCullCount()).as("养殖 36 回刷后旧日期为 0").isZero();
        assertThat(readDay(nov12).getDeathCullCount()).as("养殖 36 最终归业务日 11-12").isEqualTo(1);
        assertThat(day(nov12.plusDays(2)).getDeathCullCount()).isZero();
        execute("UPDATE t_farm_pig_culling SET update_time='2025-11-20' WHERE id=1");
        assertThat(day(nov10).getDeathCullCount()).as("养殖 36 修改早录的淘汰单，旧业务日仍为 0").isZero();
        assertThat(day(nov12).getDeathCullCount()).as("养殖 36 编辑不改变计数归属").isEqualTo(1);
        execute("UPDATE t_farm_pig_death SET del_flag='1' WHERE id=2");
        assertThat(day(nov10).getDeathCullCount()).as("养殖 36 后续操作撤销后回到有效淘汰").isEqualTo(1);
        assertThat(day(nov12).getDeathCullCount()).isZero();
    }

    @Test
    void deathCullUsesRecordingOrderBeforeBusinessDateAndBreaksTiesById() throws Exception {
        LocalDate nov10 = LocalDate.of(2025, 11, 10);
        LocalDate nov12 = nov10.plusDays(2);
        execute("INSERT INTO t_farm_pig_death (id,pig_id,death_date,death_pig_type,create_time) "
            + "VALUES (1,1,'2025-11-12','fattening','2025-11-14')");
        execute("INSERT INTO t_farm_pig_culling (id,pig_id,culling_date,create_time) "
            + "VALUES (2,1,'2025-11-10','2025-11-15')");

        assertThat(day(nov10).getDeathCullCount()).as("养殖 36 后补录的淘汰计在较早业务日").isEqualTo(1);
        assertThat(day(nov12).getDeathCullCount()).as("养殖 36 先录入的死亡不重复计数").isZero();
        execute("UPDATE t_farm_pig_culling SET create_time='2025-11-14' WHERE id=2");
        assertThat(day(nov10).getDeathCullCount()).as("养殖 36 同录入时间取较大 ID").isEqualTo(1);
        assertThat(day(nov12).getDeathCullCount()).isZero();
    }

    @Test
    void pigFeedConvertsMaterialUnitsAndCountsUnconvertibleRecords() throws Exception {
        execute("INSERT INTO t_warehouse_product_info (id,product_unit,belong_type) VALUES "
            + "(1,'吨','feed'),(2,'斤','feed'),(3,'克','feed'),(4,'公斤','feed'),"
            + "(5,' KG ','feed'),(6,'袋','feed'),(7,'包','feed')");
        execute("INSERT INTO t_warehouse_feed_log (id,feed_date,feed_type,feed_animal,product_id,feed_weight) VALUES "
            + "(1,'2025-01-31','warehouse','pig',1,0.02),"
            + "(2,'2025-02-01','warehouse','pig',2,4),(3,'2025-02-01','warehouse','pig',3,500),"
            + "(4,'2025-02-01','warehouse','pig',4,3),(5,'2025-02-01','warehouse','pig',5,4),"
            + "(6,'2025-02-01','warehouse','pig',6,2),(7,'2025-02-01','warehouse','pig',7,3)");

        FarmIndicatorRecord tonnes = day(JAN31);
        assertThat(tonnes.getFeedWeightKg()).as("养殖 37 教槽料 0.02 吨").isEqualByComparingTo("20");
        assertThat(tonnes.getNonKgFeedRecordCount()).isZero();
        FarmIndicatorRecord other = day(FEB1);
        assertThat(other.getFeedWeightKg()).as("养殖 37 斤、克、公斤和 kg").isEqualByComparingTo("9.500");
        assertThat(other.getNonKgFeedRecordCount()).as("养殖 37 袋、包各记一条").isEqualTo(2);
        assertThat(other.getOrganicFeedWeightKg()).as("养殖 38 饲料物资不进有机果蔬重量").isZero();
    }

    @Test
    void pigFeedConvertsSpacedUnitAliasesAndCountsMissingUnits() throws Exception {
        execute("INSERT INTO t_warehouse_product_info (id,product_unit,belong_type) VALUES "
            + "(1,'　G　','feed'),(2,'　t ','feed'),(3,'　k　g　','feed'),"
            + "(4,NULL,'feed'),(5,'','feed'),(6,'　 ','feed')");
        execute("INSERT INTO t_warehouse_feed_log (id,feed_date,feed_type,feed_animal,product_id,feed_weight) VALUES "
            + "(1,'2025-01-31','warehouse','pig',1,500),(2,'2025-02-01','warehouse','pig',2,0.02),"
            + "(3,'2025-02-02','warehouse','pig',3,3),(4,'2025-01-31','warehouse','pig',4,10),"
            + "(5,'2025-02-01','warehouse','pig',5,11),(6,'2025-02-02','warehouse','pig',6,12)");

        FarmIndicatorRecord grams = day(JAN31);
        assertThat(grams.getFeedWeightKg()).as("养殖 37 500 g 折 0.5 kg").isEqualByComparingTo("0.500");
        assertThat(grams.getNonKgFeedRecordCount()).as("养殖 37 NULL 单位记一条").isEqualTo(1);
        FarmIndicatorRecord tonnes = day(FEB1);
        assertThat(tonnes.getFeedWeightKg()).as("养殖 37 0.02 t 折 20 kg").isEqualByComparingTo("20");
        assertThat(tonnes.getNonKgFeedRecordCount()).as("养殖 37 空字符串单位记一条").isEqualTo(1);
        FarmIndicatorRecord kilograms = day(FEB1.plusDays(1));
        assertThat(kilograms.getFeedWeightKg()).as("养殖 37 去掉单位中的全角空格").isEqualByComparingTo("3");
        assertThat(kilograms.getNonKgFeedRecordCount()).as("养殖 37 纯空格单位记一条").isEqualTo(1);
    }

    @Test
    void pigFeedUsesOneLedgerAndSeparatesOrganicAndNonKgMaterials() throws Exception {
        execute("INSERT INTO t_warehouse_product_info (id,product_unit,belong_type,buy_class) VALUES "
            + "(1,'kg',NULL,'feed'),(2,'袋',NULL,'feed'),(3,'千克','vegetable',NULL)");
        execute("INSERT INTO t_warehouse_feed_log (id,feed_date,feed_type,feed_animal,product_id,feed_weight) VALUES "
            + "(1,'2025-01-31 08:00:00','veg_handle','pig',NULL,100),"
            + "(2,'2025-01-31 09:00:00','warehouse','pig',1,20),"
            + "(3,'2025-01-31','veg_handle','poultry',NULL,30),"
            + "(4,'2025-01-31','warehouse','pig',2,2),"
            + "(5,'2025-02-01','warehouse','pig',3,15),"
            + "(6,'2025-01-31','warehouse','pig',999,999),"
            + "(7,'2025-01-31','warehouse','pig',1,40),"
            + "(8,'2025-01-31','warehouse','pig',1,50),"
            + "(9,'2025-02-01',NULL,'pig',NULL,10)");
        execute("UPDATE t_warehouse_feed_log SET tenant_id='2002' WHERE id=7");
        execute("UPDATE t_warehouse_feed_log SET del_flag='1' WHERE id=8");
        FarmIndicatorRecord day = day(JAN31);
        assertThat(day.getFeedWeightKg()).as("养殖 37：红薯100 kg + 教槽料20 kg").isEqualByComparingTo("120.000");
        assertThat(day.getOrganicFeedWeightKg()).as("养殖 38：家禽、饲料物资排除").isEqualByComparingTo("100.000");
        assertThat(day.getNonKgFeedRecordCount()).as("养殖 37：两袋是一条非kg记录").isEqualTo(1);
        assertThat(day(FEB1).getOrganicFeedWeightKg()).as("养殖 38 仓库果蔬 15 + 旧毛菜台账 10").isEqualByComparingTo("25");
        var vo = new FarmIndicatorRecordToFarmIndicatorRecordVoMapperImpl().convert(day);
        assertThat(vo.getFeedWeightKg()).isEqualByComparingTo(day.getFeedWeightKg());
        assertThat(vo.getOrganicFeedWeightKg()).isEqualByComparingTo(day.getOrganicFeedWeightKg());
        assertThat(vo.getNonKgFeedRecordCount()).isEqualTo(1);
    }

    @Test
    void pregnancyLossIsFirstEventPerBreedingAndOneSowPerDay() throws Exception {
        LocalDate nov5 = LocalDate.of(2025, 11, 5);
        LocalDate nov8 = nov5.plusDays(3);
        execute("INSERT INTO t_farm_pig_breeding (id,pig_id,breeding_date) VALUES "
            + "(1,11,'2025-10-15'),(2,12,'2025-10-16'),(3,13,'2025-07-10'),(4,14,'2025-10-20'),"
            + "(5,11,'2025-11-06'),(6,15,'2025-10-15'),(7,16,'2025-10-15')");
        execute("INSERT INTO t_farm_pig_abnormal (id,pig_id,abnormal_date,abnormal_type,related_breeding_id) VALUES "
            + "(1,11,'2025-11-05','R',1),(2,11,'2025-11-05 10:00:00','A',1),"
            + "(3,11,'2025-11-08','N',1),(4,13,'2025-11-08','A',3),(5,99,'2025-11-08','R',NULL)");
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,breeding_id,farrow_date) VALUES (1,13,3,'2025-11-03')");
        execute("INSERT INTO t_farm_pig_death (id,pig_id,death_date,death_pig_type) VALUES "
            + "(1,12,'2025-11-08','sow'),(2,15,'2025-11-05','fattening')");
        execute("INSERT INTO t_farm_pig_culling (id,pig_id,culling_date) VALUES (1,14,'2025-11-09')");
        change(14, "ELIMINATE", "PZ", "END", 1L, "2025-11-09 00:00:00", null, null);
        execute("INSERT INTO t_farm_pig_culling (id,pig_id,culling_date) VALUES (2,16,'2025-11-09')");
        change(16, "ELIMINATE", "DN", "END", 2L, "2025-11-09 00:00:00", null, null);
        assertThat(day(nov5).getPregnancyLossCount()).as("养殖 43 同一配种同日多原因仅 1").isEqualTo(1);
        assertThat(day(nov8).getPregnancyLossCount()).as("养殖 43 A 空怀不重计，仅 B 妊娠中死亡").isEqualTo(1);
        assertThat(day(nov8.plusDays(1)).getPregnancyLossCount()).as("养殖 43 只计妊娠中淘汰").isEqualTo(1);
        execute("INSERT INTO t_farm_pig_abnormal (id,pig_id,abnormal_date,abnormal_type) "
            + "VALUES (6,11,'2025-11-10','R')");
        assertThat(day(nov8.plusDays(2)).getPregnancyLossCount()).as("养殖 43 重新配种后是另一胎").isEqualTo(1);
        execute("UPDATE t_farm_pig_abnormal SET del_flag='1' WHERE id IN (1,2)");
        assertThat(day(nov5).getPregnancyLossCount()).isZero();
        assertThat(day(nov8).getPregnancyLossCount()).as("养殖 43 撤销首损失后，旧胎转计 11-08").isEqualTo(2);
    }

    @Test
    void monthlyBreedingCountsSowsAndOnlyRepeatsAfterUnresolvedAbnormality() throws Exception {
        execute("INSERT INTO t_farm_pig_breeding (id,pig_id,breeding_date) VALUES "
            + "(1,11,'2025-10-22'),(2,11,'2025-11-10'),(3,11,'2025-11-20'),(4,12,'2025-11-12'),"
            + "(5,13,'2025-11-01'),(6,14,'2025-11-01')");
        execute("UPDATE t_farm_pig_breeding SET tenant_id='2002' WHERE id=5");
        execute("UPDATE t_farm_pig_breeding SET del_flag='1' WHERE id=6");
        execute("INSERT INTO t_farm_pig_abnormal (id,pig_id,abnormal_date,abnormal_type) VALUES "
            + "(1,11,'2025-11-03','R'),(2,12,'2025-10-01','A')");
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date) VALUES (1,12,'2025-10-20')");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) VALUES "
            + "(1,12,1,'2025-11-07',1)");
        change(12, "WEAN", "FM", "DN", 1L, "2025-11-07 10:00:00", null, null);

        YearMonth november = YearMonth.of(2025, 11);
        rollups(november);
        MonthlyProduction row = month(november);
        assertThat(row.getBreedingSowCount()).as("养殖 44 三张当月有效配种单涉及两头").isEqualTo(2);
        assertThat(row.getRepeatBreedingSowCount()).as("养殖 45 A 返情再配，另一头正常断奶再配").isEqualTo(1);
        assertThat(row.getRepeatBreedingRate()).as("养殖 46 1/2").isEqualByComparingTo("50.00");
        assertThat(row.getWeanBreedInterval()).as("养殖 50 正常断奶 11-07 到再配 11-12").isEqualByComparingTo("5");
        assertThat(row.getWeanBreedDaysSum()).isEqualTo(5);
        assertThat(row.getWeanBreedValidCount()).isEqualTo(1);
        var indicators = service.getMonthlyProductionStats(november).getCurrentIndicators();
        assertThat(indicators.repeatBreedingRate()).isEqualByComparingTo("50.00");
    }

    @Test
    void monthlyFirstBreedingUsesIntroductionOrRetentionDateAndRealBirth() throws Exception {
        pig(11, "sow", "2025-03-01", null);
        pig(12, "sow", "2025-04-01", null);
        pig(13, "sow", null, null);
        execute("UPDATE t_farm_pig_info SET introduce_type='external', introduce_date='2025-09-01' WHERE id=11");
        execute("UPDATE t_farm_pig_info SET introduce_type='internal', introduce_date='2025-08-01' WHERE id=12");
        change(12, "INTRO", "YF", "HB", null, "2025-10-01 09:00:00", "fattening", "sow");
        execute("INSERT INTO t_farm_pig_breeding (id,pig_id,breeding_date) VALUES "
            + "(1,11,'2025-11-10'),(2,12,'2025-11-01'),(3,13,'2025-11-05'),(4,11,'2025-11-20'),"
            + "(5,14,'2025-10-09'),(6,14,'2025-11-01')");
        change(11, "BREED", "HB", "PZ", 1L, "2025-11-10 10:00:00", null, null);
        change(12, "BREED", "HB", "PZ", 2L, "2025-11-01 10:00:00", null, null);
        change(13, "BREED", "HB", "PZ", 3L, "2025-11-05 10:00:00", null, null);
        change(14, "BREED", "HB", "PZ", 6L, "2025-11-01 10:00:00", null, null);
        YearMonth november = YearMonth.of(2025, 11);
        rollups(november);

        MonthlyProduction row = month(november);
        assertThat(row.getReserveFirstBreedCount()).as("养殖 47 排除上月已经首配的猪").isEqualTo(3);
        assertThat(row.getEntryFirstBreedDaysSum()).isEqualTo(101);
        assertThat(row.getEntryFirstBreedValidCount()).isEqualTo(2);
        assertThat(row.getAvgEntryFirstBreedDays()).as("养殖 48 (外引70天+留种31天)/2").isEqualByComparingTo("50.5");
        assertThat(row.getReserveFirstBreedAgeDaysSum()).isEqualTo(468);
        assertThat(row.getReserveFirstBreedAgeValidCount()).isEqualTo(2);
        assertThat(row.getAvgReserveFirstBreedAgeDays()).as("养殖 49 (254+214)/2，缺生日排除").isEqualByComparingTo("234.0");
    }

    @Test
    void monthlyFarrowWeightsAndRatesUseTheirOwnValidDenominators() throws Exception {
        execute("INSERT INTO t_farm_pig_breeding (id,pig_id,breeding_date) VALUES "
            + "(1,11,'2025-07-10'),(2,12,'2025-07-18'),(3,11,'2025-11-05')");
        execute("""
            INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date,breeding_id,parity,total_born,live_born,
                dead_born,mummy_born,total_weight,healthy_male,healthy_female,weak_raised_male,weak_raised_female,weak_culled)
            VALUES (1,11,'2025-11-10',1,1,8,6,2,0,7.50,2,2,1,0,0),
                   (2,12,'2025-11-11',2,2,8,7,0,1,10.75,3,3,0,1,1),
                   (3,13,'2025-11-12',NULL,NULL,9,8,0,1,NULL,NULL,NULL,0,1,0),
                   (4,14,'2025-11-13',NULL,4,1,0,1,0,0,0,0,0,0,0)
            """);
        execute("INSERT INTO t_farm_farrowing_rate (expected_farrow_date,farrow_date) VALUES "
            + "('2025-11-10','2025-11-10'),('2025-11-10','2025-11-11'),('2025-12-01','2025-11-30')");
        YearMonth november = YearMonth.of(2025, 11);
        for (int date = 10; date <= 13; date++) {
            day(november.atDay(date));
        }
        rollups(november);
        MonthlyProduction row = month(november);

        assertThat(row.getAvgBornPerLitter()).as("养殖 52 26/4").isEqualByComparingTo("6.500");
        assertThat(row.getAvgLiveBornPerLitter()).as("养殖 53 21/4").isEqualByComparingTo("5.250");
        assertThat(row.getTotalBornCount()).as("养殖 54").isEqualTo(26);
        assertThat(row.getBornCount()).as("养殖 55").isEqualTo(21);
        assertThat(row.getFarrowSowCount()).as("养殖 56").isEqualTo(4);
        assertThat(row.getAvgFarrowParity()).as("养殖 57 7/3，排除空胎次").isEqualByComparingTo("2.33");
        assertThat(row.getFarrowParitySum()).isEqualTo(7);
        assertThat(row.getFarrowParityValidCount()).isEqualTo(3);
        assertThat(row.getSmallLiveBornLitterCount()).isEqualTo(3);
        assertThat(row.getSmallLiveBornLitterRate()).as("养殖 58 6、7、8、0 → 3/4").isEqualByComparingTo("75.00");
        assertThat(row.getHealthyBornCount()).as("养殖 59 4+6+实录0").isEqualTo(10);
        assertThat(row.getHealthyBornRecordCount()).isEqualTo(3);
        assertThat(row.getAvgHealthyBornPerLitter()).as("养殖 60 10/4").isEqualByComparingTo("2.500");
        assertThat(row.getDeadBornCount()).isEqualTo(3);
        assertThat(row.getMummyBornCount()).isEqualTo(2);
        assertThat(row.getFarrowBornCount()).isEqualTo(26);
        assertThat(row.getDeadBornRate()).as("养殖 61 3/26").isEqualByComparingTo("11.54");
        assertThat(row.getAvgDeadBornPerLitter()).as("养殖 62 3/4").isEqualByComparingTo("0.750");
        assertThat(row.getMummyBornRate()).as("养殖 63 2/26").isEqualByComparingTo("7.69");
        assertThat(row.getAvgMummyBornPerLitter()).as("养殖 64 2/4").isEqualByComparingTo("0.500");
        assertThat(row.getGestationDaysSum()).isEqualTo(239);
        assertThat(row.getGestationValidCount()).isEqualTo(2);
        assertThat(row.getAvgGestationDays()).as("养殖 65 (123+116)/2，纳入晚产及排除缺配对").isEqualByComparingTo("119.5");
        assertThat(row.getBirthWeightSum()).isEqualByComparingTo("18.25");
        assertThat(row.getBirthWeightPigletCount()).isEqualTo(13);
        assertThat(row.getAvgBirthWeight()).as("养殖 66 18.25kg/13头已称窝活仔").isEqualByComparingTo("1.404");
        assertThat(row.getFarrowRate()).as("养殖 67 到期两窝，只计按期1窝").isEqualByComparingTo("50");
        assertThat(row.getMateLitterCount()).isEqualTo(2);
        assertThat(row.getCohortFarrowCount()).isEqualTo(1);
        assertThat(row.getFirstParityFarrowSowCount()).as("养殖 105").isEqualTo(1);
        assertThat(row.getHealthyBornTotalCount()).isEqualTo(17);
        assertThat(row.getHealthyBornRate()).as("养殖 106 10/17").isEqualByComparingTo("58.82");
        assertThat(row.getWeakBornCount()).isEqualTo(4);
        assertThat(row.getAvgWeakBornPerLitter()).as("养殖 107 4/4").isEqualByComparingTo("1.000");
        assertThat(row.getWeakBornRate()).as("养殖 108 4/26").isEqualByComparingTo("15.38");

        execute("UPDATE t_farm_pig_farrow SET healthy_male=NULL, healthy_female=NULL");
        rollups(november);
        assertThat(month(november).getHealthyBornCount()).as("养殖 59 全未填须清掉旧值").isNull();
        assertThat(month(november).getHealthyBornRate()).as("养殖 106 零分母").isZero();
    }

    @Test
    void monthlyWeaningSeparatesAgeWeightAndTransferCoverage() throws Exception {
        pig(1, "fattening", "2025-01-10", "2025-01-31");
        pig(2, "fattening", "2025-01-10", "2025-01-31");
        pig(3, "fattening", "2025-01-09", "2025-01-31");
        pig(4, "fattening", null, null);
        pig(13, "sow", "2024-02-01", "2024-03-01");
        execute("UPDATE t_farm_pig_info SET wean_weight=9 WHERE id=13");
        execute("UPDATE t_farm_pig_info SET wean_weight=7 WHERE id=2");
        execute("UPDATE t_farm_pig_info SET wean_weight=99 WHERE id=1");
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date) VALUES (1,11,'2025-01-10'),(2,12,'2025-01-09')");
        execute("INSERT INTO t_farm_pig_pigletno (id,farrow_id,piglet_ear_no,pig_id) VALUES "
            + "(1,1,'W1',1),(2,1,'W2',2),(3,2,'W3',3),(4,3,'S13',13)");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count,weaned_weight) VALUES "
            + "(1,11,1,'2025-01-31',99,999),(2,12,2,'2025-01-31',7,99),"
            + "(3,13,9,'2025-02-01',4,20),(4,14,1,'2025-01-31',2,7)");
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,birth_farrow_id,ear_no,weight) VALUES "
            + "(1,1,1,'W1',5),(2,1,1,'W2',NULL),(3,2,2,'W3',NULL)");
        execute("INSERT INTO t_farm_pig_transfer (id,pig_id,transfer_date,transfer_reason) VALUES "
            + "(1,1,'2025-02-01','weaning'),(2,2,'2025-02-01','weaning'),(3,3,'2025-02-02','weaning'),"
            + "(4,4,'2025-02-02','weaning'),(5,1,'2025-02-04','other'),(6,1,'2025-02-05','weaning'),"
            + "(7,13,'2025-02-01','weaning')");
        execute("UPDATE t_farm_pig_transfer SET del_flag='1' WHERE id=6");
        rollups(JAN, FEB);
        MonthlyProduction jan = month(JAN);
        MonthlyProduction feb = month(FEB);
        assertThat(jan.getWeanedCount()).as("养殖 70 逐头明细3加历史主单2").isEqualTo(5);
        assertThat(jan.getWeanedWeightSum()).isEqualByComparingTo("12");
        assertThat(jan.getAvgWeanedWeight()).as("养殖 69 / 养殖 101 (5+7)kg/5头").isEqualByComparingTo("2.400");
        assertThat(jan.getWeanAgeDaysSum()).isEqualTo(64);
        assertThat(jan.getWeanAgeValidCount()).isEqualTo(3);
        assertThat(jan.getAvgWeanAgeDays()).as("养殖 71 21、21、22 → 21.3，旧值21.333").isEqualByComparingTo("21.3");
        assertThat(feb.getAvgWeanAgeDays()).as("养殖 71 有断奶、无有效日龄 → 0，旧值NULL").isZero();
        assertThat(service.getMonthlyProductionStats(JAN).getCurrentIndicators().avgWeanAgeDays())
            .isEqualTo(new java.math.BigDecimal("21.3"));
        assertThat(feb.getWeaningTransferAgeDaysSum()).isEqualTo(68);
        assertThat(feb.getWeaningTransferAgeValidCount()).isEqualTo(3);
        assertThat(feb.getAvgWeaningTransferAgeDays()).as("养殖 103 排除同天转移母猪，22、22、24 → 22.7").isEqualByComparingTo("22.7");
        assertThat(feb.getWeaningTransferWeightSum()).isEqualByComparingTo("12");
        assertThat(feb.getWeaningTransferWeightValidCount()).isEqualTo(2);
        assertThat(feb.getAvgWeaningTransferWeight()).as("养殖 104 有效断奶明细5、猪档7 → 6.000").isEqualByComparingTo("6.000");
    }

    @Test
    void laterWeaningBatchRebuildsTheMotherWeanMonthThroughExistingEntryPoint() throws Exception {
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date) VALUES (1,11,'2025-01-10')");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) VALUES "
            + "(1,11,1,'2025-01-31',2)");
        change(11, "WEAN", "FM", "DN", 1L, "2025-01-31 11:00:00", null, null);
        rollups(JAN);
        assertThat(month(JAN).getAvgBirthLitterWeanedPiglets()).isEqualByComparingTo("2");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) VALUES "
            + "(2,11,1,'2025-02-02',3)");
        service.triggerAggregate(FEB1.plusDays(1));

        MonthlyProduction jan = month(JAN);
        assertThat(jan.getBirthLitterWeanedPigletCount()).as("养殖 102 1月断2+2月断3，归1月").isEqualTo(5);
        assertThat(jan.getWeaningSowCount()).isEqualTo(1);
        assertThat(jan.getAvgBirthLitterWeanedPiglets()).as("养殖 102 1月 5/1").isEqualByComparingTo("5.000");
        assertThat(jan.getAvgWeanedPerLitter()).as("养殖 68 1月仍为2/1").isEqualByComparingTo("2.000");
        assertThat(month(FEB).getBirthLitterWeanedPigletCount()).as("养殖 102 2月该窝不计").isZero();
        assertThat(month(FEB).getWeanedCount()).isEqualTo(3);
        service.triggerAggregate(FEB1.plusDays(1));
        assertThat(month(JAN)).usingRecursiveComparison().ignoringFields("updateTime").isEqualTo(jan);

        execute("UPDATE t_farm_pig_weaning SET del_flag='1' WHERE id=2");
        service.triggerAggregate(FEB1.plusDays(1));
        assertThat(month(JAN).getAvgBirthLitterWeanedPiglets()).as("养殖 102 撤销第二批后回到2").isEqualByComparingTo("2");
    }

    @Test
    void monthlyFarrowLossUsesRegisteredTerminalsAndDeduplicatesBirthLitter() throws Exception {
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date,live_born) VALUES (1,11,'2025-01-08',10)");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count,lactation_death_count) VALUES "
            + "(1,11,1,'2025-01-29',4,3),(2,11,1,'2025-01-31',5,0)");
        change(11, "WEAN", "FM", "DN", 1L, "2025-01-29 08:00:00", null, null);
        pig(1, "fattening", "2025-01-08", null);
        pig(2, "piglet", null, null);
        pig(3, "piglet", "2025-01-08", null);
        execute("INSERT INTO t_farm_pig_pigletno (id,farrow_id,piglet_ear_no,pig_id,del_flag) VALUES "
            + "(1,1,'P1',1,'1'),(3,1,'P3',3,'1')");
        execute("INSERT INTO t_farm_pig_death (id,pig_id,death_date,death_pig_type) VALUES "
            + "(1,1,'2025-01-22','piglet'),(2,2,'2025-01-25','piglet'),(3,3,'2025-02-01','piglet')");
        change(1, "INTRO", null, "YF", null, "2025-01-31 09:00:00", "piglet", "fattening");
        rollups(JAN);
        MonthlyProduction row = month(JAN);
        assertThat(row.getFarrowLossCount()).as("养殖 72 按已关联的死亡登记计1").isEqualTo(1);
        assertThat(row.getFarrowLossLiveBornCount()).as("养殖 72 两批断奶只计10头活仔").isEqualTo(10);
        assertThat(row.getFarrowLossRate()).as("养殖 72 1/10，旧填报3或重复分母会产生不同结果").isEqualByComparingTo("10.00");
        assertThat(row.getUnlinkedPigletDeathCullCount()).as("养殖 72 无法回查出生窝单列").isEqualTo(1);
        execute("UPDATE t_farm_pig_death SET del_flag='1' WHERE id=1");
        rollups(JAN);
        assertThat(month(JAN).getFarrowLossRate()).as("养殖 72 断奶头数仍为9，撤销实际死亡后应为0").isZero();
        change(1, "ELIMINATE", null, "END", 4L, "2025-01-23 10:00:00", "piglet", null);
        execute("INSERT INTO t_farm_pig_culling (id,pig_id,culling_date) VALUES (4,1,'2025-01-23')");
        rollups(JAN);
        assertThat(month(JAN).getFarrowLossRate()).as("养殖 72 登记淘汰也计入哺乳期死淘").isEqualByComparingTo("10.00");
    }

    @Test
    void monthlyMarketingUsesIdenticalFatteningCohortAndWeightedGrowth() throws Exception {
        pig(1, "sow", "2024-08-05", "2024-10-25");
        pig(2, "fattening", null, "2024-11-01");
        pig(3, "fattening", "2024-10-01", "2024-12-01");
        execute("UPDATE t_farm_pig_info SET wean_weight=20 WHERE id IN (1,3)");
        change(1, "INTRO", "YF", "HB", null, "2025-02-10 09:00:00", "fattening", "sow");
        change(3, "INTRO", "HB", "YF", null, "2025-02-09 09:00:00", "sow", "fattening");
        marketing(1, "2025-02-01", 80, "SLAUGHTER");
        marketing(2, "2025-02-02", 150, "SLAUGHTER");
        marketing(3, "2025-02-03", 300, "SLAUGHTER");
        rollups(FEB);
        MonthlyProduction row = month(FEB);

        assertThat(row.getMarketingCount()).as("养殖 73").isEqualTo(2);
        assertThat(row.getAvgMarketingWeight()).as("养殖 74").isEqualByComparingTo("115");
        assertThat(row.getMarketingWeight()).as("养殖 88").isEqualByComparingTo("230");
        assertThat(row.getMarketingAgeDaysSum()).isEqualTo(180);
        assertThat(row.getMarketingAgeValidCount()).isEqualTo(1);
        assertThat(row.getMarketingMissingBirthCount()).isEqualTo(1);
        assertThat(row.getAvgMarketingAgeDays()).as("养殖 75 育肥出栏日龄180，另1头缺出生").isEqualByComparingTo("180.0");
        assertThat(row.getMarketingNetGainKg()).isEqualByComparingTo("60");
        assertThat(row.getMarketingGainDays()).isEqualTo(100);
        assertThat(row.getAvgDailyGainGrams()).as("养殖 76 60kg/100天").isEqualByComparingTo("600.0");

        execute("UPDATE t_farm_pig_info SET wean_date='2024-12-15', wean_weight=130 WHERE id=2");
        rollups(FEB);
        assertThat(month(FEB).getMarketingGainDays()).isEqualTo(150);
        assertThat(month(FEB).getAvgDailyGainGrams()).as("养殖 76 (60+20)kg/(100+50)天").isEqualByComparingTo("533.3");
    }

    @Test
    void monthlyTerminalRatesReplayGroupsAndIncludeZeroStockDays() throws Exception {
        pig(1, "fattening", "2025-01-12", null);
        pig(2, "sow", null, null);
        pig(3, "fattening", null, null);
        pig(4, "sow", null, null);
        pig(5, "fattening", null, null);
        pig(6, "sow", null, null);
        pig(7, "sow", null, null);
        pig(8, "sow", null, null);
        change(1, "INTRO", null, "YF", null, "2025-03-01 10:00:00", "piglet", "fattening");
        change(3, "TO_FATTEN", "DN", "YF", null, "2025-03-01 10:00:00", "sow", "fattening");
        change(6, "INTRO", "YF", "HB", null, "2025-03-01 10:00:00", "fattening", "sow");
        execute("INSERT INTO t_farm_pig_death (id,pig_id,death_date,death_pig_type,create_time) VALUES "
            + "(1,1,'2025-02-01','fattening','2025-02-09'),(2,2,'2025-02-02','piglet','2025-02-09'),"
            + "(3,3,'2025-02-04','fattening','2025-02-09'),(4,4,'2025-02-04','sow','2025-02-09'),"
            + "(5,5,'2025-02-05','fattening','2025-02-10')");
        change(1, "DIE", null, "END", 1L, "2025-02-01 10:00:00", null, null);
        change(2, "DIE", null, "END", 2L, "2025-02-02 10:00:00", "piglet", null);
        change(3, "DIE", "PZ", "END", 3L, "2025-02-04 10:00:00", null, null);
        change(4, "DIE", "HB", "END", 4L, "2025-02-04 10:00:00", null, null);
        change(5, "DIE", "YF", "END", 5L, "2025-02-05 10:00:00", null, null);
        execute("INSERT INTO t_farm_pig_culling (id,pig_id,culling_date) VALUES "
            + "(1,6,'2025-02-06'),(2,7,'2025-02-06'),(3,8,'2025-02-06')");
        change(6, "ELIMINATE", "YF", "END", 1L, "2025-02-06 10:00:00", null, null);
        change(7, "ELIMINATE", "DN", "END", 2L, "2025-02-06 10:00:00", null, null);
        change(8, "ELIMINATE", "PZ", "END", 3L, "2025-02-06 10:00:00", null, null);
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date,parity,live_born) VALUES "
            + "(1,7,'2025-01-10',4,6),(2,7,'2025-01-20',5,5),(3,7,'2025-03-01',6,4),(4,99,'2025-02-02',1,8)");
        day(JAN31);
        day(FEB1);
        day(FEB1.plusDays(1));
        day(FEB1.plusDays(5));
        execute("UPDATE t_farm_indicator_record SET end_piglet_count=2 WHERE stat_date='2025-01-31'");
        execute("UPDATE t_farm_indicator_record SET end_production_sow_count=4, end_fattening_count=8,"
            + " death_pig_count=3 WHERE stat_date='2025-02-01'");
        execute("UPDATE t_farm_indicator_record SET death_pig_count=2, end_nonprod_sow_count=3 WHERE stat_date='2025-02-02'");
        execute("UPDATE t_farm_indicator_record SET end_production_sow_count=5, end_fattening_count=4,"
            + " culling_pig_count=3 WHERE stat_date='2025-02-06'");
        rollups(FEB);
        MonthlyProduction row = month(FEB);

        assertThat(row.getNpdDays()).as("养殖 51 3头天/3头").isEqualByComparingTo("1");
        assertThat(row.getPigletDeathCount()).as("养殖 77 实际死时为仔猪的2头").isEqualTo(2);
        assertThat(row.getProductionSowCullCount()).isEqualTo(2);
        assertThat(row.getProductionSowCullRate()).as("养殖 78 2/3").isEqualByComparingTo("66.67");
        assertThat(row.getProductionSowDeathCount()).isEqualTo(1);
        assertThat(row.getProductionSowDeathRate()).as("养殖 79 排除后备，1/3").isEqualByComparingTo("33.33");
        assertThat(row.getOpeningPigletStock()).isEqualTo(2);
        assertThat(row.getFarrowingHousePigletCount()).isEqualTo(10);
        assertThat(row.getFarrowingHouseDeathRate()).as("养殖 80 2/(8+2)").isEqualByComparingTo("20.00");
        assertThat(row.getFatteningStockSum()).isEqualTo(12);
        assertThat(row.getAvgFatteningStock()).isEqualByComparingTo("4");
        assertThat(row.getFatteningDeathRate()).as("养殖 81 1/4").isEqualByComparingTo("25.00");
        assertThat(row.getFatteningCullRate()).as("养殖 82 1/4").isEqualByComparingTo("25.00");
        assertThat(row.getDeathCount()).as("养殖 89").isEqualTo(5);
        assertThat(row.getCullingCount()).as("养殖 90").isEqualTo(3);
        assertThat(row.getDeathCullCount()).as("养殖 91 5+3").isEqualTo(8);
        assertThat(row.getEndProductionSowStock()).as("养殖 96 最后一份日报").isEqualTo(5);
        assertThat(row.getEndProductionSowStockDate()).as("养殖 96 月末缺报").isEqualTo(FEB1.plusDays(5));
        assertThat(row.getIndicatorDayCount()).as("养殖 97 当月3份日报，含实点0头1天").isEqualTo(3);
        assertThat(row.getProductionSowStockSum()).isEqualTo(9);
        assertThat(row.getAvgProdSowStock()).as("养殖 97 (4+0+5)/3，缺日不补0").isEqualByComparingTo("3");
        assertThat(row.getPigletDeathAgeDaysSum()).isEqualTo(20);
        assertThat(row.getPigletDeathAgeValidCount()).isEqualTo(1);
        assertThat(row.getPigletDeathMissingBirthCount()).isEqualTo(1);
        assertThat(row.getAvgPigletDeathAgeDays()).as("养殖 98 20天，另1头无出生日").isEqualByComparingTo("20.0");
        assertThat(row.getCulledSowParitySum()).isEqualTo(5);
        assertThat(row.getCulledSowParityValidCount()).isEqualTo(1);
        assertThat(row.getAvgCulledSowParity()).as("养殖 110 最近胎次5，排除淘汰后和缺失胎次").isEqualByComparingTo("5.00");

        execute("DELETE FROM t_farm_indicator_record WHERE stat_date >= '2025-02-01'");
        rollups(FEB);
        assertThat(month(FEB).getIndicatorDayCount()).isZero();
        assertThat(month(FEB).getEndProductionSowStock()).isNull();
        assertThat(month(FEB).getEndProductionSowStockDate()).isNull();
        assertThat(month(FEB).getProductionSowCullRate()).isZero();
    }

    @Test
    void monthlyAverageCulledSowParityExcludesZeroParity() throws Exception {
        pig(1, "sow", null, null);
        pig(2, "sow", null, null);
        pig(3, "sow", null, null);
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date,parity) VALUES (1,1,'2025-01-20',3)");
        execute("INSERT INTO t_farm_pig_breeding (id,pig_id,breeding_date,parity) VALUES "
            + "(1,2,'2025-01-20',2),(2,3,'2025-01-20',0)");
        execute("INSERT INTO t_farm_pig_culling (id,pig_id,culling_date) VALUES "
            + "(1,1,'2025-02-06'),(2,2,'2025-02-06'),(3,3,'2025-02-06')");
        change(1, "ELIMINATE", "DN", "END", 1L, "2025-02-06 10:00:00", "sow", null);
        change(2, "ELIMINATE", "PZ", "END", 2L, "2025-02-06 10:00:00", "sow", null);
        change(3, "ELIMINATE", "PZ", "END", 3L, "2025-02-06 10:00:00", "sow", null);

        rollups(FEB);
        MonthlyProduction row = month(FEB);
        assertThat(row.getAvgCulledSowParity()).as("养殖 110 D-0228，胎次 3、2、0 的平均值")
            .isEqualByComparingTo("2.50");
        assertThat(row.getCulledSowParitySum()).isEqualTo(5);
        assertThat(row.getCulledSowParityValidCount()).isEqualTo(2);
        assertThat(row.getProductionSowCullCount()).isEqualTo(3);
    }

    @Test
    void monthlyAverageCulledSowParityIsZeroWithOnlyZeroParity() throws Exception {
        pig(1, "sow", null, null);
        execute("INSERT INTO t_farm_pig_breeding (id,pig_id,breeding_date,parity) VALUES (1,1,'2025-01-20',0)");
        execute("INSERT INTO t_farm_pig_culling (id,pig_id,culling_date) VALUES (1,1,'2025-02-06')");
        change(1, "ELIMINATE", "PZ", "END", 1L, "2025-02-06 10:00:00", "sow", null);

        rollups(FEB);
        MonthlyProduction row = month(FEB);
        assertThat(row.getCulledSowParityValidCount()).as("养殖 110 D-0228，仅有 0 胎时无样本").isZero();
        assertThat(row.getCulledSowParitySum()).isZero();
        assertThat(row.getAvgCulledSowParity()).isZero();
        assertThat(row.getProductionSowCullCount()).isEqualTo(1);
    }

    @Test
    void rebuildingMonthEndUpdatesTheNextMonthsOpeningPiglets() throws Exception {
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date,live_born) VALUES (1,11,'2025-02-02',8)");
        execute("INSERT INTO t_farm_pig_death (id,pig_id,death_date,death_pig_type) "
            + "VALUES (1,3,'2025-02-10','piglet')");
        rollups(FEB);
        assertThat(month(FEB).getOpeningPigletStock()).as("养殖 80 缺上月最后日报").isNull();
        assertThat(month(FEB).getFarrowingHouseDeathRate()).isEqualByComparingTo("12.50");

        pig(1, "piglet", "2025-01-08", null);
        pig(2, "piglet", "2025-01-08", null);
        snapshot(1, "piglet", "");
        snapshot(2, "piglet", "");
        service.triggerAggregate(JAN31);
        assertThat(month(JAN).getEndProductionSowStockDate()).as("养殖 96 月末当日优先").isEqualTo(JAN31);
        assertThat(month(FEB).getOpeningPigletStock()).as("养殖 80 上月最后日报新增2头").isEqualTo(2);
        assertThat(month(FEB).getFarrowingHouseDeathRate()).isEqualByComparingTo("10.00");
    }

    @Test
    void monthlyDailyTotalsAndPregnancyLossDeduplicationUseB1Sources() throws Exception {
        YearMonth november = YearMonth.of(2025, 11);
        execute("INSERT INTO t_farm_pig_breeding (id,pig_id,breeding_date) VALUES "
            + "(1,11,'2025-10-20'),(2,11,'2025-11-04'),(3,12,'2025-10-21'),(4,13,'2025-11-03')");
        change(13, "BREED", "HB", "PZ", 4L, "2025-11-03 10:00:00", null, null);
        execute("INSERT INTO t_farm_pig_abnormal (id,pig_id,abnormal_date,abnormal_type,related_breeding_id) VALUES "
            + "(1,11,'2025-11-03','A',1),(2,11,'2025-11-03 11:00','A',1),"
            + "(3,12,'2025-11-10','A',3),(4,11,'2025-11-05','R',2),(5,11,'2025-11-07','R',2),"
            + "(6,11,'2025-11-07 11:00','R',2),(7,11,'2025-11-10','N',2),(8,11,'2025-11-10 11:00','N',2)");
        execute("INSERT INTO t_warehouse_product_info (id,product_unit,belong_type) VALUES (1,'kg','feed')");
        execute("INSERT INTO t_warehouse_feed_log (id,feed_date,feed_type,feed_animal,product_id,feed_weight) VALUES "
            + "(1,'2025-11-03','veg_handle','pig',NULL,30),(2,'2025-11-05','warehouse','pig',1,20),"
            + "(3,'2025-11-10','veg_handle','pig',NULL,5),(4,'2025-11-10','veg_handle','poultry',NULL,120)");
        for (int date : List.of(3, 5, 7, 10)) {
            day(november.atDay(date));
        }
        execute("UPDATE t_farm_indicator_record SET end_production_sow_count=4");
        assertThat(readDay(november.atDay(3)).getPregnancyLossCount()).isEqualTo(1);
        assertThat(readDay(november.atDay(5)).getPregnancyLossCount()).isEqualTo(1);
        assertThat(readDay(november.atDay(10)).getPregnancyLossCount()).isEqualTo(1);
        rollups(november);
        MonthlyProduction row = month(november);

        assertThat(row.getFeedWeightKg()).as("养殖 83 30+20+5").isEqualByComparingTo("55");
        assertThat(row.getOrganicFeedWeightKg()).as("养殖 84 30+5").isEqualByComparingTo("35");
        assertThat(row.getReturnHeatCount()).as("养殖 93 同头两日计2，同日重复排除").isEqualTo(2);
        assertThat(row.getEmptySowCount()).as("养殖 94 同头同日计1").isEqualTo(1);
        assertThat(row.getAbortionCount()).as("养殖 95 11-03两条同母、11-10另一母，旧值3").isEqualTo(2);
        assertThat(row.getNewProductionSowCount()).isEqualTo(1);
        assertThat(row.getProductionSowReplacementRate()).as("养殖 109 1/4").isEqualByComparingTo("25.00");
        assertThat(row.getPregnancyLossSowCount()).as("养殖 111 日损失合计3，按月两头").isEqualTo(2);
        assertThat(row.getPregnancyLossRate()).as("养殖 111 2/4").isEqualByComparingTo("50.00");
    }

    @Test
    void monthlyMedicineCountsIndividualsAndBatchLinesByUseDate() throws Exception {
        execute("""
            INSERT INTO t_breed_medicine_record (id,pig_id,use_date,drug_type,medicine_type)
            VALUES (1,11,'2025-11-02',1,'vaccine'),(2,11,'2025-11-20',1,'vaccine'),
                   (3,NULL,'2025-11-12',2,'vaccine'),(4,11,'2025-11-12',3,'vaccine'),
                   (5,12,'2025-11-12',3,'vaccine'),(6,13,'2025-11-12',3,'vaccine'),
                   (7,NULL,'2025-11-12',2,'health'),(8,11,'2025-11-12',3,'health'),
                   (9,11,'2025-11-20',1,'health'),(10,11,'2025-11-03',1,'treatment'),
                   (11,11,'2025-12-01',1,'treatment'),(12,11,'2025-11-30',1,'treatment'),
                   (13,11,'2025-11-30',1,'vaccine')
            """);
        execute("UPDATE t_breed_medicine_record SET del_flag='1' WHERE id=12");
        execute("UPDATE t_breed_medicine_record SET tenant_id='other' WHERE id=13");
        YearMonth november = YearMonth.of(2025, 11);
        rollups(november);
        MonthlyProduction row = month(november);
        assertThat(row.getVaccinePigTimes()).as("养殖 85 单只两次+批量三头").isEqualTo(5);
        assertThat(row.getHealthPigTimes()).as("养殖 86 单只一次+批量一头").isEqualTo(2);
        assertThat(row.getTreatmentPigTimes()).as("养殖 87 排除下月和撤销").isEqualTo(1);
    }

    @Test
    void annualTerminalRatesUseEventGroupsAndCoveredDays() throws Exception {
        execute("INSERT INTO t_farm_indicator_record (stat_date,end_production_sow_count,end_fattening_count,death_pig_count) VALUES "
            + "('2025-01-31',80,40,2),('2025-02-01',0,0,0),('2025-02-03',80,20,1),('2025-02-04',40,20,1)");
        execute("INSERT INTO t_farm_pig_death (id,pig_id,death_date,death_pig_type,create_time) VALUES "
            + "(1,1,'2025-01-31','sow','2025-03-01'),(2,2,'2025-02-03','sow','2025-03-01'),"
            + "(3,3,'2025-01-31','fattening','2025-03-01'),(4,4,'2025-02-04','sow','2025-03-01'),"
            + "(5,5,'2025-02-02','sow','2025-03-01')");
        change(1, "DIE", "DN", "END", 1L, "2025-01-31 09:00:00", "sow", null);
        change(2, "DIE", "PZ", "END", 2L, "2025-02-03 09:00:00", "sow", null);
        change(3, "DIE", "YF", "END", 3L, "2025-01-31 10:00:00", "fattening", null);
        change(4, "DIE", "HB", "END", 4L, "2025-02-04 09:00:00", "sow", null);
        change(5, "DIE", "PZ", "END", 5L, "2025-02-02 09:00:00", "sow", null);

        execute("INSERT INTO t_farm_pig_culling (id,pig_id,culling_date,create_time) VALUES "
            + "(6,6,'2025-02-01','2025-02-10'),(7,7,'2025-02-04','2025-02-10'),"
            + "(8,8,'2025-02-04','2025-02-10'),(9,9,'2025-02-02','2025-02-10')");
        change(6, "ELIMINATE", "PZ", "END", 6L, "2025-02-01 09:00:00", "sow", null);
        change(7, "ELIMINATE", "YF", "END", 7L, "2025-02-04 09:00:00", null, null);
        change(7, "INTRO", "YF", "HB", null, "2025-03-01 09:00:00", "fattening", "sow");
        change(8, "ELIMINATE", "HB", "END", 8L, "2025-02-04 09:00:00", "sow", null);
        change(9, "ELIMINATE", "YF", "END", 9L, "2025-02-02 09:00:00", "fattening", null);
        pig(7, "sow", null, null);

        rollups(JAN, FEB);

        AnnualIndicator annual = year();
        assertThat(annual.getAvgProdSowStock()).isEqualByComparingTo("50.000");
        assertThat(annual.getAvgFatteningStock()).isEqualByComparingTo("20.000");
        assertThat(annual.getProductionSowCullRate()).as("养殖 117 只算淘汰，1÷50×100")
            .isEqualByComparingTo("2.00");
        assertThat(annual.getMortalityRate()).as("养殖 118 2÷50×100，排除育肥、后备和缺日报的死猪")
            .isEqualByComparingTo("4.00");
        // 同一数据在旧实现中取全群 4 头，旧存活母猪查询用默认测试替身 0，库值为 1.00。
        assertThat(annual.getDeathCount()).isEqualTo(4);
        assertThat(annual.getFatteningCullRate()).as("养殖 119 事件时是育肥的1头÷20×100")
            .isEqualByComparingTo("5.00");
        assertThat(annual.getFatteningDeathRate()).as("养殖 120 1÷20×100").isEqualByComparingTo("5.00");
        assertThat(service.getAnnualIndicator((int) YEAR).getMortalityRate()).as("养殖 118 后台接口仍返回比例")
            .isEqualByComparingTo("0.0400");

        execute("UPDATE t_farm_indicator_record SET end_production_sow_count=0,end_fattening_count=0");
        rollups(JAN, FEB);
        AnnualIndicator zeroStock = year();
        assertThat(zeroStock.getProductionSowCullRate()).isZero();
        assertThat(zeroStock.getMortalityRate()).isZero();
        assertThat(zeroStock.getFatteningCullRate()).isZero();
        assertThat(zeroStock.getFatteningDeathRate()).isZero();
    }

    @Test
    void annualProductionUsesCoveredDaysAndWeightedSamples() throws Exception {
        execute("""
            INSERT INTO t_farm_pig_breeding (id,pig_id,breeding_date) VALUES
              (1,11,DATE_SUB('2025-01-31', INTERVAL 110 DAY)),
              (2,12,DATE_SUB('2025-02-01', INTERVAL 124 DAY)),
              (3,13,DATE_SUB('2025-02-01', INTERVAL 118 DAY)),
              (4,99,DATE_SUB('2025-02-03', INTERVAL 113 DAY)),
              (5,15,DATE_SUB('2025-02-02', INTERVAL 140 DAY))
            """);
        execute("""
            INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date,breeding_id,total_born,live_born) VALUES
              (1,11,'2025-01-31',1,8,7),(2,12,'2025-02-01',2,7,6),(3,13,'2025-02-01',3,9,9),
              (4,14,'2025-02-03',4,6,6),(5,15,'2025-02-02',5,99,99),
              (6,16,'2025-01-10',NULL,10,10),(7,17,'2025-01-13',NULL,10,10)
            """);
        execute("""
            INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) VALUES
              (1,16,6,'2025-01-31',30),(2,17,7,'2025-02-03',5),(3,16,6,'2025-02-02',40)
            """);
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id) VALUES (1,1),(2,1),(3,1)");
        change(16, "WEAN", "FM", "DN", 1L, "2025-01-31 08:00:00", null, null);
        change(17, "WEAN", "FM", "DN", 2L, "2025-02-03 08:00:00", null, null);
        pig(21, "sow", JAN31.minusDays(180).toString(), null);
        change(21, "INTRO", "YF", "HB", null, "2025-03-01 09:00:00", "fattening", "sow");
        pig(22, "fattening", null, null);
        pig(23, "fattening", FEB1.plusDays(2).minusDays(200).toString(), null);
        pig(24, "fattening", FEB1.plusDays(2).minusDays(100).toString(), null);
        pig(25, "fattening", FEB1.plusDays(1).minusDays(300).toString(), null);
        pig(26, "sow", "2024-01-01", null);
        marketing(21, "2025-01-31", 100, "SLAUGHTER");
        marketing(22, "2025-02-01", 80, "SLAUGHTER");
        marketing(23, "2025-02-03", 90, "SLAUGHTER");
        marketing(24, "2025-02-03", 150, "SLAUGHTER");
        marketing(25, "2025-02-02", 300, "SLAUGHTER");
        marketing(26, "2025-02-03", 160, "SLAUGHTER");
        execute("INSERT INTO t_warehouse_product_info (id,product_unit,buy_class) VALUES (1,'kg','feed')");
        execute("""
            INSERT INTO t_warehouse_feed_log (id,feed_date,feed_type,feed_animal,product_id,feed_weight) VALUES
              (1,'2025-01-31','veg_handle','pig',NULL,6.125),(2,'2025-02-01','warehouse','pig',1,5.125),
              (3,'2025-02-03','veg_handle','pig',NULL,4.250),(4,'2025-02-02','veg_handle','pig',NULL,90)
            """);
        execute("""
            INSERT INTO t_breed_medicine_record (id,pig_id,use_date,drug_type,medicine_type) VALUES
              (1,21,'2025-01-31',1,'vaccine'),(2,21,'2025-02-01',1,'vaccine'),
              (3,21,'2025-02-03',3,'vaccine'),(4,22,'2025-02-03',3,'vaccine'),(5,23,'2025-02-03',3,'vaccine'),
              (6,NULL,'2025-02-03',2,'vaccine'),(7,21,'2025-02-01',1,'health'),
              (8,22,'2025-02-03',3,'health'),(9,22,'2025-02-03',1,'treatment'),
              (10,23,'2025-02-02',1,'treatment'),(11,23,'2025-02-03',1,'treatment'),
              (12,23,'2025-02-03',1,'vaccine')
            """);
        execute("UPDATE t_breed_medicine_record SET del_flag='1' WHERE id=11");
        execute("UPDATE t_breed_medicine_record SET tenant_id='other' WHERE id=12");

        day(JAN31);
        day(FEB1);
        day(FEB1.plusDays(2));
        execute("""
            UPDATE t_farm_indicator_record SET end_production_sow_count=8, pregnant_sow_count=4,
                   end_nonprod_sow_count=2, preg_loss_days=1 WHERE stat_date='2025-01-31'
            """);
        execute("""
            UPDATE t_farm_indicator_record SET end_production_sow_count=4, pregnant_sow_count=3,
                   end_nonprod_sow_count=3 WHERE stat_date='2025-02-03'
            """);
        rollups(JAN, FEB);

        AnnualIndicator annual = year();
        assertThat(annual.getTotalBornCount()).as("养殖 112 8+7+9+6").isEqualTo(30);
        assertThat(annual.getTotalWeanedPiglet()).as("养殖 113 明细3+历史主单5").isEqualTo(8);
        assertThat(annual.getWeanedCount()).as("养殖 113 后台同源").isEqualTo(8);
        assertThat(annual.getMarketingCount()).as("养殖 114 同批育肥猪").isEqualTo(4);
        assertThat(annual.getMarketingWeight()).as("养殖 115 100+80+90+150").isEqualByComparingTo("420");
        assertThat(annual.getAvgMarketingWeight()).as("养殖 116 420÷4").isEqualByComparingTo("105.000");
        assertThat(annual.getPsy()).as("养殖 121 (7-1)÷12×365÷115×(8÷2)").isEqualByComparingTo("6.35");
        assertThat(annual.getPsyStatDays()).as("养殖 121 首末4天跨度内仅3份日报").isEqualTo(3);
        assertThat(annual.getPsyStatFrom()).as("养殖 121 实际起日").isEqualTo(JAN31);
        assertThat(annual.getPsyStatTo()).as("养殖 121 实际止日").isEqualTo(FEB1.plusDays(2));
        assertThat(annual.getFarrowingsPerSow()).as("养殖 122 4窝÷平均4头").isEqualByComparingTo("1.00");
        assertThat(annual.getFeedWeightKg()).as("养殖 123 6.125+5.125+4.250").isEqualByComparingTo("15.500");
        assertThat(annual.getOrganicFeedWeightKg()).as("养殖 124 6.125+4.250").isEqualByComparingTo("10.375");
        assertThat(annual.getVaccinePigTimes()).as("养殖 125 单只2次+批量3头").isEqualTo(5);
        assertThat(annual.getHealthPigTimes()).as("养殖 126 单只1次+批量1头").isEqualTo(2);
        assertThat(annual.getTreatmentPigTimes()).as("养殖 127 唯一有效覆盖日记录").isEqualTo(1);
        assertThat(annual.getAvgMarketingAgeDays()).as("养殖 128 (180+200+100)÷3").isEqualByComparingTo("160.0");
        assertThat(annual.getMarketingMissingBirthCount()).as("养殖 128 缺生日另记").isEqualTo(1);
        assertThat(annual.getAvgNpdDays()).as("养殖 129 (2+3+1)÷4，不外推").isEqualByComparingTo("1.500");
        assertThat(annual.getAvgGestationDays()).as("养殖 130 (110+124+118)÷3，错猪配种不算")
            .isEqualByComparingTo("117.3");

        var json = new ObjectMapper();
        var annualJson = json.valueToTree(service.getAnnualIndicator((int) YEAR));
        var breedingJson = json.valueToTree(service.getBreedingAnnual((int) YEAR));
        for (var response : List.of(annualJson, breedingJson)) {
            assertThat(response.path("psyStatFrom").asText()).isEqualTo(JAN31.toString());
            assertThat(response.path("psyStatTo").asText()).isEqualTo("2025-02-03");
            assertThat(response.path("psyStatDays").asInt()).isEqualTo(3);
            assertThat(response.path("organicFeedWeightKg").decimalValue()).isEqualByComparingTo("10.375");
            assertThat(response.path("marketingMissingBirthCount").asInt()).isEqualTo(1);
        }
        rollups(JAN, FEB);
        assertThat(year()).usingRecursiveComparison().ignoringFields("updateTime").isEqualTo(annual);
    }

    @Test
    void annualRebuildKeepsLegacyRatioUntilRefreshedAndClearsCoverage() throws Exception {
        execute("INSERT INTO t_farm_year_production (stat_year,mortality_rate,psy_stat_from,psy_stat_days) "
            + "VALUES (2025,0.12,'2025-01-31',2)");
        var historical = service.getAnnualIndicator((int) YEAR);
        assertThat(historical.getMortalityRate()).as("养殖 118 旧年表比例原样返回").isEqualByComparingTo("0.12");
        assertThat(historical.getPsyStatTo()).isNull();
        assertThat(historical.getAvgGestationDays()).isNull();

        day(JAN31);
        rollups(JAN);
        assertThat(year().getPsyStatTo()).isEqualTo(JAN31);
        execute("UPDATE t_farm_indicator_record SET del_flag='1' WHERE stat_date='2025-01-31'");
        rollups(JAN);

        AnnualIndicator annual = year();
        assertThat(annual.getPsyStatFrom()).as("养殖 121 失去覆盖后清除起日").isNull();
        assertThat(annual.getPsyStatTo()).as("养殖 121 失去覆盖后清除止日").isNull();
        assertThat(annual.getPsyStatDays()).isZero();
        assertThat(annual.getPsy()).isZero();
        assertThat(annual.getMortalityRate()).isZero();
        assertThat(annual.getFarrowingsPerSow()).isZero();
        assertThat(annual.getAvgMarketingAgeDays()).isZero();
        assertThat(annual.getAvgGestationDays()).isZero();
    }

    private FarmIndicatorRecord day(LocalDate date) {
        service.aggregateDay(TENANT, date);
        return readDay(date);
    }

    private FarmIndicatorRecord readDay(LocalDate date) {
        return days.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FarmIndicatorRecord>()
            .eq("stat_date", date));
    }

    private void rollups(YearMonth... periods) {
        service.aggregateRollups(TENANT, FEB1, List.of(periods), List.of(YEAR));
    }

    private MonthlyProduction month(YearMonth month) {
        return months.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MonthlyProduction>()
            .eq("stat_month", month.toString()));
    }

    private AnnualIndicator year() {
        return years.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AnnualIndicator>()
            .eq("stat_year", YEAR));
    }

    private void pig(long id, String type, String birthDate, String weanDate) throws Exception {
        execute("INSERT INTO t_farm_pig_info (id,pig_type,birth_date,wean_date) VALUES (?,?,?,?)",
            id, type, birthDate, weanDate);
    }

    private void wholeLitterPiglets(String firstPigWeanDate) throws Exception {
        pig(1, "fattening", "2025-01-10", firstPigWeanDate);
        pig(2, "fattening", "2025-01-10", null);
        pig(3, "fattening", "2025-01-10", null);
        execute("UPDATE t_farm_pig_info SET mother_ear='SOW-10'");
        execute("INSERT INTO t_farm_pig_pigletno (id,farrow_id,piglet_ear_no,pig_id) VALUES "
            + "(1,10,'E1',1),(2,10,'E2',2),(3,10,'E3',3)");
    }

    private void change(long pig, String event, String oldStatus, String newStatus, Long related, String at,
                        String oldType, String newType) throws Exception {
        execute("INSERT INTO t_farm_status_record (id,pig_id,event_type,old_status,new_status,"
                + "related_event_id,change_time,old_pig_type,new_pig_type) VALUES (?,?,?,?,?,?,?,?,?)",
            ++nextStatusId, pig, event, oldStatus, newStatus, related, at, oldType, newType);
    }

    private void recordLitterCulling(long pig, String date, String recordedAt) throws Exception {
        change(pig, "ELIMINATE", null, "END", 1L, date, null, null);
        execute("INSERT INTO t_farm_pig_culling (id,pig_id,culling_date,create_time) VALUES (1,?,?,?)",
            pig, date, recordedAt);
    }

    private void snapshot(long pig, String type, String status) throws Exception {
        snapshot(JAN31, pig, type, status);
    }

    private void snapshot(LocalDate day, long pig, String type, String status) throws Exception {
        execute("INSERT INTO t_farm_pig_snapshot (snap_date,pig_id,pig_type,current_status) VALUES (?,?,?,?)",
            day, pig, type, status);
    }

    private void marketing(long id, String date, int weight, String event) throws Exception {
        execute("INSERT INTO t_farm_pig_marketing (id,pig_id,marketing_date,out_weight,create_time)"
            + " VALUES (?,?,?,?, '2025-02-03')", id, id, date, weight);
        if (event != null) {
            change(id, event, "YF", "END", id, date + " 09:00:00", null, null);
        }
    }

    private void copyTableDefinition(String sourceCatalog, String table) throws Exception {
        String ddl;
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SHOW CREATE TABLE `" + sourceCatalog.replace("`", "``") + "`." + table)) {
            assertThat(result.next()).isTrue();
            ddl = result.getString(2);
        }
        execute(ddl);
    }

    private void table(String name, String columns) throws Exception {
        execute("CREATE TABLE " + name + " (" + columns
            + ", tenant_id VARCHAR(20) DEFAULT '1001', del_flag CHAR(1) DEFAULT '0')");
    }

    private void execute(String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.execute();
        }
        if (session != null) {
            session.clearCache();
        }
    }
}
