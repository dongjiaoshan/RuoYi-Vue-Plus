package org.dromara.djs.breed.dashboard.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/** 单连接临时表上的真实 Mapper SQL、看板聚合写入和 VO 读取。 */
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

    // 同一连接不能重复打开 MySQL 临时表；现有快照重放在别处完成，本套件直接灌快照，
    // 与本任务无关的台账、性能和出栏背膘 SQL 不在本套件执行范围。
    private static final Set<String> AGGREGATES = Set.of(
        "countDistinctEventInDay", "countDistinctAbnormalSowInRange", "countStatusEventInRange",
        "aggregateWeanDetailForDay", "sumWeanedInRange", "sumIntroducedSowInRange", "snapshotByTypeStatusOnDate",
        "countIndicatorDays", "sumIndicatorRange", "sumMonthlyProductionRange", "findLateEntriesBeforeWindow"
    );

    private Connection connection;
    private SqlSession session;
    private DashboardServiceImpl service;
    private FarmIndicatorRecordMapper days;
    private MonthlyProductionMapper months;
    private AnnualIndicatorMapper years;
    private long nextStatusId;

    @BeforeEach
    void openTemporaryTables() throws Exception {
        connection = DriverManager.getConnection(System.getenv("DJS_STAT_MYSQL_URL"),
            System.getenv("DJS_STAT_MYSQL_USER"), System.getenv("DJS_STAT_MYSQL_PASSWORD"));
        connection.setAutoCommit(false);
        table("t_farm_pig_info", "id BIGINT PRIMARY KEY, pig_type VARCHAR(16), birth_date DATE, wean_date DATE");
        table("t_farm_pig_introduce", "id BIGINT PRIMARY KEY, introduce_type VARCHAR(16), introduce_date DATE,"
            + " pig_count INT, pig_sex CHAR(1)");
        table("t_farm_status_record", "id BIGINT PRIMARY KEY, pig_id BIGINT, event_type VARCHAR(16),"
            + " old_status VARCHAR(16), new_status VARCHAR(16), related_event_id BIGINT, change_time DATETIME,"
            + " old_pig_type VARCHAR(16), new_pig_type VARCHAR(16), create_time DATETIME");
        table("t_farm_pig_snapshot", "snap_date DATE, pig_id BIGINT, pig_type VARCHAR(16), current_status VARCHAR(16)");
        table("t_farm_pig_farrow", "id BIGINT PRIMARY KEY, pig_id BIGINT, farrow_date DATETIME, parity INT,"
            + " dead_born INT, mummy_born INT, deformed_born INT, weak_culled INT, weak_born INT, create_time DATETIME");
        table("t_farm_pig_breeding", "id BIGINT PRIMARY KEY, pig_id BIGINT, breeding_date DATETIME");
        table("t_farm_pig_abnormal", "id BIGINT PRIMARY KEY, pig_id BIGINT, abnormal_date DATETIME,"
            + " abnormal_type CHAR(1), create_time DATETIME");
        table("t_farm_pig_marketing", "id BIGINT PRIMARY KEY, pig_id BIGINT, marketing_date DATETIME,"
            + " out_weight DECIMAL(12,2), create_time DATETIME");
        table("t_farm_pig_weaning", "id BIGINT PRIMARY KEY, pig_id BIGINT, farrow_id BIGINT NOT NULL,"
            + " weaning_date DATETIME, weaned_count INT, weaned_weight DECIMAL(12,2)");
        table("t_farm_pig_weaning_detail", "id BIGINT PRIMARY KEY, weaning_id BIGINT, ear_no VARCHAR(32),"
            + " birth_farrow_id BIGINT, weight DECIMAL(12,3)");
        table("t_farm_pig_pigletno", "id BIGINT PRIMARY KEY, farrow_id BIGINT, piglet_ear_no VARCHAR(32),"
            + " pig_id BIGINT, del_unique BIGINT DEFAULT 0, tag_date DATETIME");
        table("t_farm_pig_growth", "id BIGINT PRIMARY KEY, pig_id BIGINT, measure_date DATE");
        for (String table : List.of("t_farm_indicator_record", "t_farm_monthly_production", "t_farm_year_production")) {
            copyIndicatorDefinition(table);
        }
        Path migration = Path.of("../../ruoyi-admin/src/main/resources/db/migration",
            "V202609300900__BRD-STAT-V11-production-indicators.sql");
        for (String statement : Files.readString(migration).split(";")) {
            if (!statement.isBlank()) {
                execute(statement);
            }
        }

        var configuration = new MybatisConfiguration(new Environment("local-temporary-tables",
            new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMappers(AggregateQueryMapper.class.getPackageName());
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(connection);
        var source = session.getMapper(AggregateQueryMapper.class);
        AggregateQueryMapper other = mock(AggregateQueryMapper.class, invocation -> {
            if (!AGGREGATES.contains(invocation.getMethod().getName())) {
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
        service = new DashboardServiceImpl(mock(SowRecordMapper.class), months, years, other, days,
            mock(FarrowingRateMapper.class), mock(WeaningAggregateSyncMapper.class), mock(SowPerformanceMapper.class),
            mock(IProductionCycleConfigService.class), mock(IFattenAgeStageService.class),
            new ProductionIndicatorCalculator(session.getMapper(ProductionIndicatorMapper.class)));
    }

    @AfterEach
    void closeTemporaryTables() throws Exception {
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
        assertThat(day.getEndFatteningCount()).as("养殖 13 原期末存栏快照").isEqualTo(1);
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
        assertThat(day(JAN31).getNewProductionSowCount()).as("养殖 6").isEqualTo(1);
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
        change(1, "BREED", "KH", "PZ", null, "2025-01-31 14:00:00", null, null);
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date,parity,dead_born,mummy_born,"
            + "deformed_born,weak_culled,weak_born,create_time) VALUES "
            + "(1,1,'2025-01-31',1,2,2,0,1,3,'2025-02-01'),"
            + "(2,2,'2025-01-31',NULL,1,NULL,NULL,NULL,5,'2025-01-31'),"
            + "(3,1,'2025-01-31',1,NULL,NULL,NULL,NULL,NULL,'2025-01-31'),"
            + "(4,3,'2025-02-01',2,NULL,NULL,NULL,NULL,NULL,'2025-02-01')");
        FarmIndicatorRecord day = day(JAN31);
        assertThat(day.getReturnHeatCount()).as("养殖 17").isEqualTo(1);
        assertThat(day.getEmptySowCount()).as("养殖 18").isEqualTo(1);
        assertThat(day.getAbortionCount()).as("养殖 19").isEqualTo(1);
        assertThat(day.getAbnormalSowCount()).isEqualTo(2);
        assertThat(day.getFarrowDeadBornCount()).as("养殖 22").isEqualTo(3);
        assertThat(day.getFarrowMummyBornCount()).as("养殖 23").isEqualTo(2);
        assertThat(day.getFarrowDeformedBornCount()).as("养殖 24 实录零").isZero();
        assertThat(day.getFarrowWeakCulledCount()).as("养殖 25").isEqualTo(1);
        assertThat(day(FEB1).getFarrowDeformedBornCount()).as("养殖 24 历史缺值").isNull();
        rollups(JAN, FEB);
        assertThat(month(JAN).getFarrowSowCount()).as("养殖 56").isEqualTo(2);
        assertThat(month(JAN).getAbortionCount()).as("养殖 95 跨月补录").isEqualTo(1);
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
        assertThat(day(JAN31.minusDays(1)).getAvgWeanAgeDays()).isNull();
        assertThat(day(FEB1).getAvgWeanAgeDays()).isEqualByComparingTo("22");
        execute("INSERT INTO t_farm_pig_farrow (id,pig_id,farrow_date) VALUES (2,22,'2025-01-14')");
        execute("INSERT INTO t_farm_pig_weaning (id,pig_id,farrow_id,weaning_date,weaned_count) "
            + "VALUES (4,22,2,'2025-01-29',1)");
        execute("INSERT INTO t_farm_pig_weaning_detail (id,weaning_id,birth_farrow_id,weight) VALUES (6,4,2,0)");
        assertThat(day(JAN31.minusDays(2)).getAvgEntryWeight()).isNull();
        var vo = new FarmIndicatorRecordToFarmIndicatorRecordVoMapperImpl().convert(day);
        assertThat(vo.getAvgWeanAgeDays()).isEqualByComparingTo("23");
        assertThat(vo.getAvgEntryWeight()).isEqualByComparingTo("6");
        rollups(JAN, FEB);
        assertThat(month(JAN).getAvgWeanAgeDays()).as("养殖 71 一月按有效头数加权 (21+25+15)/3").isEqualByComparingTo("20.333");
        assertThat(month(FEB).getAvgWeanAgeDays()).as("养殖 71 跨月第二批").isEqualByComparingTo("22");
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
    void biologicalLitterAverageUsesBirthKeyWithinWeanMonth() throws Exception {
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
        assertThat(month(JAN).getBirthLitterWeanedPigletCount()).as("养殖 102 生母本月已断奶的3头，含1头归本窝").isEqualTo(3);
        assertThat(month(JAN).getAvgBirthLitterWeanedPiglets()).as("养殖 102 3头÷2窝").isEqualByComparingTo("1.5");
        assertThat(month(FEB).getAvgBirthLitterWeanedPiglets()).isZero();
        assertThat(service.getMonthlyProductionStats(JAN).getCurrentIndicators().avgBirthLitterWeanedPiglets())
            .isEqualByComparingTo("1.5");
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

    private FarmIndicatorRecord day(LocalDate date) {
        service.aggregateDay(TENANT, date);
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

    private void change(long pig, String event, String oldStatus, String newStatus, Long related, String at,
                        String oldType, String newType) throws Exception {
        execute("INSERT INTO t_farm_status_record (id,pig_id,event_type,old_status,new_status,"
                + "related_event_id,change_time,old_pig_type,new_pig_type) VALUES (?,?,?,?,?,?,?,?,?)",
            ++nextStatusId, pig, event, oldStatus, newStatus, related, at, oldType, newType);
    }

    private void snapshot(long pig, String type, String status) throws Exception {
        execute("INSERT INTO t_farm_pig_snapshot (snap_date,pig_id,pig_type,current_status) VALUES (?,?,?,?)",
            JAN31, pig, type, status);
    }

    private void marketing(long id, String date, int weight, String event) throws Exception {
        execute("INSERT INTO t_farm_pig_marketing (id,pig_id,marketing_date,out_weight,create_time)"
            + " VALUES (?,?,?,?, '2025-02-03')", id, id, date, weight);
        if (event != null) {
            change(id, event, "YF", "END", id, date + " 09:00:00", null, null);
        }
    }

    private void copyIndicatorDefinition(String table) throws Exception {
        String ddl;
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SHOW CREATE TABLE " + table)) {
            assertThat(result.next()).isTrue();
            ddl = result.getString(2);
        }
        execute(ddl.replaceFirst("^CREATE TABLE ", "CREATE TEMPORARY TABLE "));
    }

    private void table(String name, String columns) throws Exception {
        execute("CREATE TEMPORARY TABLE " + name + " (" + columns
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
