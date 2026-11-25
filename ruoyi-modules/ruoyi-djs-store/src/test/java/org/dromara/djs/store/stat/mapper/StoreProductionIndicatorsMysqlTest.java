package org.dromara.djs.store.stat.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.common.core.service.DictService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.store.service.IStoreUserRelationService;
import org.dromara.djs.store.demand.core.StoreDemandViewEnricher;
import org.dromara.djs.store.ledger.mapper.StoreDailyLedgerMapper;
import org.dromara.djs.store.loss.mapper.StoreLossRecordMapper;
import org.dromara.djs.store.loss.service.impl.StoreLossServiceImpl;
import org.dromara.djs.store.manage.domain.vo.StoreManageMonthlyVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageUnitRowVo;
import org.dromara.djs.store.manage.mapper.StoreManageMapper;
import org.dromara.djs.store.manage.service.impl.StoreManageServiceImpl;
import org.dromara.djs.store.stat.domain.StoreIndicatorPeriod;
import org.dromara.djs.store.stat.domain.StoreProductionIndicatorVo;
import org.dromara.djs.store.stat.service.StoreProductionIndicatorService;
import org.dromara.djs.warehouse.demand.core.DemandArrivedQuantityFiller;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.MockedStatic;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.dromara.djs.store.stat.domain.StoreIndicatorPeriod.DAY;
import static org.dromara.djs.store.stat.domain.StoreIndicatorPeriod.MONTH;
import static org.dromara.djs.store.stat.domain.StoreIndicatorPeriod.YEAR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("local")
@Tag("dev")
@EnabledIfEnvironmentVariable(named = "DJS_STAT_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:(3306|3316)/.*")
class StoreProductionIndicatorsMysqlTest {

    private static final String TENANT = "1001";
    private static final long STORE = 11L;
    private static final long OTHER_STORE = 12L;
    private static final LocalDate DEC_31 = LocalDate.of(2025, 12, 31);
    private static final LocalDate JAN_1 = DEC_31.plusDays(1);
    private static final LocalDate JAN_2 = JAN_1.plusDays(1);
    private static final LocalDate JAN_31 = JAN_1.plusDays(30);
    private static final LocalDate FEB_1 = JAN_31.plusDays(1);
    private static final Path MIGRATIONS = Path.of("../../ruoyi-admin/src/main/resources/db/migration");

    private Connection connection;
    private SqlSession session;
    private MockedStatic<TenantHelper> tenant;
    private StoreLossServiceImpl loss;
    private StoreProductionIndicatorService indicators;
    private StoreManageServiceImpl manage;
    private StoreDemandViewEnricher demands;
    private DemandArrivedQuantityFiller shippedQuantities;

    @BeforeEach
    void createTemporaryTablesAndFixture() throws Exception {
        connection = DriverManager.getConnection(System.getenv("DJS_STAT_MYSQL_URL"),
            System.getenv("DJS_STAT_MYSQL_USER"), System.getenv("DJS_STAT_MYSQL_PASSWORD"));
        connection.setAutoCommit(false);

        createTemporaryFromMigration("V202609301200__STR-STAT-V11-production-indicators.sql",
            "t_store_production_indicator_daily");
        createTemporaryFromMigration("V202608150900__DENGBO-R15-store-loss-record.sql", "t_store_loss_record");
        createTemporaryFromMigration("V202606270400__STORE-LEDGER-001.sql", "t_store_daily_ledger");
        execute("ALTER TABLE t_store_daily_ledger ADD COLUMN wh_return_qty DECIMAL(12,3) DEFAULT 0");
        execute("CREATE TEMPORARY TABLE t_md_store (id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), del_flag CHAR(1))");
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_demand_manage (
                id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), del_flag CHAR(1), store_id BIGINT,
                product_type VARCHAR(32), received_time DATETIME, product_id BIGINT, demand_date DATE,
                demand_status VARCHAR(32), demand_quantity DECIMAL(12,3)
            )
            """);
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_shipment (
                demand_id BIGINT, tenant_id VARCHAR(20), del_flag CHAR(1), store_id BIGINT,
                product_type VARCHAR(32), ship_date DATE, ship_quantity DECIMAL(12,3)
            )
            """);
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_product_production (
                demand_id BIGINT, tenant_id VARCHAR(20), del_flag CHAR(1), product_id BIGINT,
                is_delivery_check TINYINT, delivery_check_time DATETIME, product_weight DECIMAL(12,3),
                demand_deduct_qty DECIMAL(12,3), product_unit VARCHAR(16),
                deliver_dest VARCHAR(32), deliver_type TINYINT, is_damaged TINYINT DEFAULT 0
            )
            """);
        execute("""
            CREATE TEMPORARY TABLE t_warehouse_product_info (
                id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), del_flag CHAR(1), product_id VARCHAR(32),
                product_name VARCHAR(64), belong_type VARCHAR(32), product_unit VARCHAR(16),
                product_spec VARCHAR(64), is_material_sold TINYINT, product_material BIGINT
            )
            """);
        execute("""
            CREATE TEMPORARY TABLE t_store_return (
                tenant_id VARCHAR(20), del_flag CHAR(1), store_id BIGINT, product_id BIGINT,
                return_direction VARCHAR(32), return_date DATETIME, return_quantity DECIMAL(12,3)
            )
            """);
        seed();
        for (int index = 2; index <= 3; index++) {
            execute("CREATE TEMPORARY TABLE t_warehouse_product_info_fixture_" + index
                + " AS SELECT * FROM t_warehouse_product_info");
        }
        wireServices();
    }

    private void seed() throws SQLException {
        execute("INSERT INTO t_md_store VALUES (11,'1001','0'),(12,'1001','0'),(13,'2001','0')");
        execute("""
            INSERT INTO t_warehouse_product_info VALUES
            (1,'1001','0','BAR','白条','white_bar','kg',NULL,0,NULL),
            (2,'1001','0','CUT','原料','pork','kg',NULL,0,NULL),
            (3,'1001','0','SOLD','外售','pork','kg',NULL,1,2),
            (4,'1001','0','P2','两斤装猪肉','pork','份','2斤/份',0,NULL),
            (5,'1001','0','PKG','公斤计量猪肉','pork','公斤','2斤装',0,NULL),
            (6,'1001','0','P12','十二斤猪肉','pork','份','12斤/份',0,NULL),
            (7,'1001','0','VEG','菜份','vegetable','份','400g/份',0,NULL),
            (8,'1001','0','VEG-B','菜袋','vegetable','袋',NULL,0,NULL),
            (9,'1001','0','VEG-KG','菜历史公斤','vegetable','kg',NULL,0,NULL),
            (10,'1001','0','P05','半公斤装猪肉','pork','包','500g/包',0,NULL),
            (11,'1001','1','VEG-OLD','历史菜份','vegetable','份',NULL,0,NULL),
            (12,'1001','1','P-OLD','历史猪肉','pork','kg',NULL,0,NULL)
            """);
        execute("""
            INSERT INTO t_warehouse_demand_manage VALUES
            (101,'1001','0',11,'white_bar','2026-01-01 09:30:00',1,'2025-12-31','COMPLETED',2),
            (102,'1001','0',11,'white_bar',NULL,1,'2025-12-31','COMPLETED',1),
            (103,'1001','0',11,'white_bar','2026-02-01 11:00:00',1,'2026-02-01','COMPLETED',1),
            (104,'1001','0',12,'white_bar','2026-01-01 08:00:00',1,'2026-01-01','COMPLETED',1),
            (105,'2001','0',13,'white_bar','2026-01-01 08:00:00',1,'2026-01-01','COMPLETED',1),
            (201,'1001','0',11,'vegetable','2026-01-01 09:30:00',7,'2025-12-31','COMPLETED',5),
            (202,'1001','0',11,'vegetable',NULL,7,'2025-12-31','COMPLETED',9),
            (203,'1001','0',11,'vegetable','2026-02-01 10:00:00',7,'2026-01-31','COMPLETED',7),
            (204,'1001','0',12,'vegetable','2026-01-01 08:00:00',7,'2026-01-01','COMPLETED',4),
            (205,'1001','0',11,'vegetable','2026-01-01 09:30:00',9,'2025-12-31','COMPLETED',2.5),
            (206,'1001','0',11,'vegetable',NULL,9,'2025-12-31','COMPLETED',6.75),
            (207,'1001','0',11,'vegetable','2026-02-01 10:00:00',9,'2026-01-31','COMPLETED',1.75),
            (301,'1001','0',11,'other','2026-01-01 08:00:00',3,'2026-01-01','COMPLETED',10),
            (302,'1001','0',11,'other',NULL,4,'2026-01-01','COMPLETED',2)
            """);
        execute("""
            INSERT INTO t_warehouse_shipment VALUES
            (101,'1001','0',11,'white_bar','2025-12-31',100),
            (101,'1001','0',11,'white_bar','2026-01-02',50),
            (102,'1001','0',11,'white_bar','2025-12-31',90),
            (102,'1001','0',11,'white_bar','2026-01-01',70),
            (101,'1001','1',11,'white_bar','2025-12-31',999),
            (103,'1001','0',11,'white_bar','2026-02-01',60),
            (104,'1001','0',12,'white_bar','2026-01-01',20),
            (105,'2001','0',13,'white_bar','2026-01-01',700)
            """);
        execute("""
            INSERT INTO t_warehouse_product_production
                (demand_id,tenant_id,del_flag,product_id,is_delivery_check,delivery_check_time,
                 product_weight,demand_deduct_qty,product_unit) VALUES
            (201,'1001','0',7,1,'2025-12-31 20:00:00',8,4,'份'),
            (205,'1001','0',9,1,'2025-12-31 20:00:00',1.25,1.25,'kg'),
            (205,'1001','0',9,1,'2026-01-01 08:00:00',1.25,1.25,'kg'),
            (205,'1001','0',9,0,NULL,11,11,'kg'),
            (206,'1001','0',9,1,'2026-01-01 07:00:00',6.75,6.75,'kg'),
            (207,'1001','0',9,1,'2026-01-31 20:00:00',1.75,1.75,'kg'),
            (201,'1001','0',7,0,NULL,8,11,'份'),
            (201,'1001','1',7,1,'2025-12-31 20:00:00',8,12,'份'),
            (201,'1001','0',4,1,'2025-12-31 20:00:00',8,13,'份'),
            (202,'1001','0',7,1,'2026-01-01 07:00:00',8,9,'份'),
            (203,'1001','0',7,1,'2026-01-31 20:00:00',8,7,'份'),
            (204,'1001','0',7,1,'2026-01-01 07:00:00',8,4,'份'),
            (301,'1001','0',3,1,'2026-01-01 07:00:00',10,10,'kg')
            """);
        execute("""
            INSERT INTO t_store_daily_ledger (id,store_id,product_id,ledger_date,inbound_qty,loss_qty) VALUES
            (51,11,2,'2026-01-01',90,2.5), (52,11,2,'2026-01-02',25,0),
            (53,11,2,'2026-01-31',5,0), (54,11,2,'2026-02-01',30,0)
            """);
        execute("""
            INSERT INTO t_store_return VALUES
            ('1001','0',11,4,'store_to_warehouse','2026-01-01 13:08:00',3),
            ('1001','0',11,5,'store_to_warehouse','2026-01-01 23:59:59',2),
            ('1001','0',11,6,'store_to_warehouse','2026-01-01',40),
            ('1001','0',11,10,'store_to_warehouse','2026-01-01',50),
            ('1001','0',11,11,'store_to_warehouse','2026-01-01',1),
            ('1001','0',11,12,'store_to_warehouse','2026-01-01',20),
            ('1001','0',11,8,'store_to_warehouse','2026-01-01 23:59:59',2),
            ('1001','1',11,7,'store_to_warehouse','2026-01-01',70),
            ('1001','0',11,7,'customer_to_store','2026-01-01',80),
            ('1001','0',11,9,'store_to_warehouse','2026-01-01',90),
            ('1001','0',11,9,'store_to_warehouse','2026-02-01',45),
            ('1001','0',11,5,'store_to_warehouse','2026-02-01',1),
            ('1001','0',11,8,'store_to_warehouse','2026-02-01',2),
            ('1001','0',12,5,'store_to_warehouse','2026-01-01',9),
            ('1001','0',12,7,'store_to_warehouse','2026-01-01',8)
            """);
    }

    private void wireServices() {
        MybatisConfiguration configuration = new MybatisConfiguration(new Environment("local-temporary-tables",
            new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
        configuration.setMapUnderscoreToCamelCase(true);
        MybatisPlusInterceptor plus = new MybatisPlusInterceptor();
        plus.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new StringValue(TENANT);
            }
        }));
        configuration.addInterceptor(plus);
        configuration.addInterceptor(new TemporaryCatalogCopies());
        configuration.addMapper(StoreProductionIndicatorMapper.class);
        configuration.addMapper(StoreLossRecordMapper.class);
        configuration.addMapper(StoreDailyLedgerMapper.class);
        configuration.addMapper(StoreManageMapper.class);
        configuration.addMapper(ProductInfoMapper.class);
        configuration.addMapper(ProductProductionMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(connection);
        StoreProductionIndicatorMapper mapper = session.getMapper(StoreProductionIndicatorMapper.class);
        ProductInfoMapper products = session.getMapper(ProductInfoMapper.class);
        ProductProductionMapper production = session.getMapper(ProductProductionMapper.class);

        // 保留现有外售 Service 边界，实际重量由原仓库 Mapper SQL 在同一临时表连接计算。
        IProductProductionService productionService = mock(IProductProductionService.class);
        when(productionService.sumDeliveredWeightToStore(any(), any(), any())).thenAnswer(invocation ->
            production.sumDeliveredWeightToStore(invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(2)));
        DictService dict = mock(DictService.class);
        when(dict.getAllDictByDictType("djs_white_bar_return_product")).thenReturn(Map.of("CUT", "原料"));

        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
        loss = new StoreLossServiceImpl(session.getMapper(StoreLossRecordMapper.class),
            session.getMapper(StoreDailyLedgerMapper.class), mapper, products, productionService, dict);
        indicators = new StoreProductionIndicatorService(mapper, loss);
        manage = new StoreManageServiceImpl(session.getMapper(StoreManageMapper.class), mock(IStoreUserRelationService.class));
        shippedQuantities = new DemandArrivedQuantityFiller(production);
        demands = new StoreDemandViewEnricher(products, production, productionService, shippedQuantities, mapper);
    }

    @AfterEach
    void closeTemporaryTables() throws SQLException {
        if (tenant != null) {
            tenant.close();
        }
        if (session != null) {
            session.close();
        }
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void dailyIndicatorsAndLegacyLossUseConfirmedReceipt() throws SQLException {
        indicators.rebuildRange(DEC_31, JAN_2);
        indicators.rebuildRange(JAN_31, JAN_31);

        var day = period(JAN_1, DAY);
        assertThat(day.getVegReceivedPortionCount()).isEqualByComparingTo("6"); // 5：4 份 + 2 袋
        assertThat(day.getVegReceiveKgWeight()).isEqualByComparingTo("2.5");
        assertThat(day.getWhiteBarReceivedWeight()).isEqualByComparingTo("100"); // 6
        assertThat(day.getSplitProductWeight()).isEqualByComparingTo("80"); // 7：90 − 10
        assertThat(day.getSplitYieldRate()).isEqualByComparingTo("80.00"); // 8
        assertThat(day.getSplitLossWeight()).isEqualByComparingTo("20"); // 9
        assertThat(day.getSplitLossRate()).isEqualByComparingTo("20.00"); // 10
        assertThat(day.getPorkReturnWeightKg()).isEqualByComparingTo("5"); // 11：3 份 2斤装 + 2 kg
        assertThat(day.getVegReturnPortionCount()).isEqualByComparingTo("2"); // 12
        assertThat(day.getVegReturnKgWeight()).isEqualByComparingTo("90");

        var previous = period(DEC_31, DAY);
        assertThat(previous.getWhiteBarReceivedWeight()).isEqualByComparingTo("0");
        assertThat(previous.getVegReceivedPortionCount()).isEqualByComparingTo("0");
        assertThat(period(JAN_2, DAY).getWhiteBarReceivedWeight()).isEqualByComparingTo("50");
        assertThat(period(JAN_31, DAY).getSplitProductWeight()).isEqualByComparingTo("5");
        assertThat(period(JAN_31, DAY).getSplitYieldRate()).isEqualByComparingTo("0.00");
        assertThat(period(JAN_31, DAY).getSplitLossRate()).isNull();

        var live = loss.getWhiteBarSplitLoss(STORE, JAN_1);
        assertThat(live.getArriveWeight()).isEqualByComparingTo("100");
        assertThat(live.getSplitTotalWeight()).isEqualByComparingTo("80");
        assertThat(live.getSplitLoss()).isEqualByComparingTo("20");
        assertThat(loss.getWhiteBarSplitLoss(STORE, DEC_31).getArriveWeight()).isEqualByComparingTo("0");
        assertThat(lossRows()).containsExactly("store_daily_loss|2.500||", "white_bar_split_loss|20.000|100.000|80.000");
    }

    @Test
    void monthAndYearAggregateDailyBasesByStore() {
        rebuildFixtureDays();

        var jan = period(JAN_1, MONTH);
        assertThat(jan.getVegReceivedPortionCount()).isEqualByComparingTo("6"); // 16
        assertThat(jan.getVegReceiveKgWeight()).isEqualByComparingTo("2.5");
        assertThat(jan.getWhiteBarReceivedWeight()).isEqualByComparingTo("150"); // 17
        assertThat(jan.getSplitProductWeight()).isEqualByComparingTo("110"); // 18
        assertThat(jan.getSplitLossRate()).isEqualByComparingTo("30.00"); // 20：45 / 150
        assertThat(jan.getPorkReturnWeightKg()).isEqualByComparingTo("5"); // 21
        assertThat(jan.getVegReturnPortionCount()).isEqualByComparingTo("2"); // 22
        assertThat(jan.getVegReturnKgWeight()).isEqualByComparingTo("90");
        assertThat(jan.getSplitYieldRate()).isEqualByComparingTo("73.33"); // 27：110 / 150
        assertThat(period(DEC_31, MONTH).getWhiteBarReceivedWeight()).isEqualByComparingTo("0");
        assertThat(period(DEC_31, MONTH).getVegReturnPortionCount()).isEqualByComparingTo("0");
        assertThat(period(DEC_31, MONTH).getSplitYieldRate()).isNull();

        var year = period(JAN_1, YEAR);
        assertThat(year.getVegReceivedPortionCount()).isEqualByComparingTo("14"); // 29：1 月 6 + 2 月 8
        assertThat(year.getVegReceiveKgWeight()).isEqualByComparingTo("4.25");
        assertThat(year.getWhiteBarReceivedWeight()).isEqualByComparingTo("210"); // 31：150 + 60
        assertThat(year.getSplitProductWeight()).isEqualByComparingTo("140"); // 32：110 + 30
        assertThat(year.getSplitLossRate()).isEqualByComparingTo("35.71"); // 33：75 / 210
        assertThat(year.getPorkReturnWeightKg()).isEqualByComparingTo("6"); // 34：5 + 1
        assertThat(year.getVegReturnPortionCount()).isEqualByComparingTo("4"); // 36：2 + 2
        assertThat(year.getVegReturnKgWeight()).isEqualByComparingTo("135");
        assertThat(period(DEC_31, YEAR).getWhiteBarReceivedWeight()).isEqualByComparingTo("0");

        var other = indicators.query(OTHER_STORE, JAN_1, JAN_1, YEAR).getFirst();
        assertThat(other.getWhiteBarReceivedWeight()).isEqualByComparingTo("20");
        assertThat(other.getVegReceivedPortionCount()).isEqualByComparingTo("4");
        assertThat(other.getPorkReturnWeightKg()).isEqualByComparingTo("9");
        assertThat(other.getVegReturnPortionCount()).isEqualByComparingTo("8");
        assertThat(indicators.query(null, JAN_1, JAN_1, YEAR)).extracting(StoreProductionIndicatorVo::getStoreId)
            .containsExactly(STORE, OTHER_STORE);
    }

    @Test
    void confirmedVegetableDemandShowsDemandUnitAndUnconfirmedShowsZero() {
        DemandManageVo portions = vegetableDemand(201L, 7L, JAN_1);
        DemandManageVo kilograms = vegetableDemand(205L, 9L, JAN_1);
        DemandManageVo inTransit = vegetableDemand(206L, 9L, null);
        var rows = List.of(portions, kilograms, inTransit);
        shippedQuantities.fill(rows);
        assertThat(kilograms.getArrivedQuantity()).isEqualByComparingTo("2.5");
        assertThat(inTransit.getArrivedQuantity()).isEqualByComparingTo("6.75");
        demands.enrich(rows);
        assertThat(portions.getArrivedQuantity()).isEqualByComparingTo("4");
        assertThat(kilograms.getArrivedQuantity()).isEqualByComparingTo("2.5");
        assertThat(inTransit.getArrivedQuantity()).isEqualByComparingTo("0");
        assertThat(inTransit.getStoreDemandStatus()).isEqualTo("SHIPPED");
    }

    @Test
    void vegetableSupplementUsesCheckDayButDemandOnlyCountsConfirmedRows() throws SQLException {
        LocalDate receipt = LocalDate.of(2026, 3, 10);
        LocalDate supplement = receipt.plusDays(2);
        execute("""
            INSERT INTO t_warehouse_demand_manage VALUES
            (208,'1001','0',11,'vegetable','2026-03-10 16:00:00',9,'2026-03-10','COMPLETED',5)
            """);
        execute("""
            INSERT INTO t_warehouse_product_production
                (demand_id,tenant_id,del_flag,product_id,is_delivery_check,delivery_check_time,
                 product_weight,demand_deduct_qty,product_unit) VALUES
            (208,'1001','0',9,1,'2026-03-10 15:00:00',1,1,'kg'),
            (208,'1001','0',9,1,'2026-03-12 09:00:00',4,4,'kg')
            """);
        indicators.rebuildRange(receipt, supplement);

        assertThat(period(receipt, DAY).getVegReceivedPortionCount()).isEqualByComparingTo("1");
        assertThat(period(receipt.plusDays(1), DAY).getVegReceivedPortionCount()).isEqualByComparingTo("0");
        assertThat(period(supplement, DAY).getVegReceivedPortionCount()).isEqualByComparingTo("1");
        assertThat(period(receipt, DAY).getVegReceiveKgWeight()).isEqualByComparingTo("1");
        assertThat(period(supplement, DAY).getVegReceiveKgWeight()).isEqualByComparingTo("4");
        assertThat(period(receipt, MONTH).getVegReceivedPortionCount()).isEqualByComparingTo("2");
        assertThat(period(receipt, YEAR).getVegReceiveKgWeight()).isEqualByComparingTo("5");
        DemandManageVo demand = vegetableDemand(208L, 9L, receipt);
        demand.setReceivedTime(receipt.atTime(16, 0));
        demands.enrich(List.of(demand));
        assertThat(demand.getArrivedQuantity()).isEqualByComparingTo("1");
    }

    @Test
    void vegetableReceiptsExcludeOtherDestinationsAndIncludeDamagedRows() throws SQLException {
        execute("""
            INSERT INTO t_warehouse_product_production
                (demand_id,tenant_id,del_flag,product_id,is_delivery_check,delivery_check_time,
                 product_weight,demand_deduct_qty,product_unit,deliver_dest,deliver_type,is_damaged) VALUES
            (205,'1001','0',9,1,'2026-01-01 07:00:00',9,9,'kg','mail',NULL,0),
            (205,'1001','0',9,1,'2026-01-01 07:00:00',8,8,'kg','gift',NULL,0),
            (205,'1001','0',9,1,'2026-01-01 07:00:00',7,7,'kg','warehouse_out',NULL,0),
            (205,'1001','0',9,1,'2026-01-01 07:00:00',6,6,'kg',NULL,2,0),
            (205,'1001','0',9,1,'2026-01-01 07:00:00',0.75,0.75,'kg',NULL,NULL,1)
            """);
        indicators.rebuildRange(JAN_1, JAN_1);

        var day = period(JAN_1, DAY);
        assertThat(day.getVegReceivedPortionCount()).isEqualByComparingTo("7");
        assertThat(day.getVegReceiveKgWeight()).isEqualByComparingTo("3.25");
        var demand = vegetableDemand(205L, 9L, JAN_1);
        demands.enrich(List.of(demand));
        assertThat(demand.getArrivedQuantity()).isEqualByComparingTo("3.25");
    }

    @Test
    void managementKeepsKgReturnsInCardDetailTotalsAndMonthOverMonth() {
        var jan = manage.getMonthly(STORE, "2026-01");
        assertThat(cardReturn(jan, "pork", "kg").getReturned().getValue()).isEqualByComparingTo("5");
        assertThat(cardReturn(jan, "vegetable", "份").getReturned().getValue()).isEqualByComparingTo("2");
        assertThat(cardReturn(jan, "vegetable", "kg").getReturned().getValue()).isEqualByComparingTo("90");
        var febKg = cardReturn(manage.getMonthly(STORE, "2026-02"), "vegetable", "kg").getReturned();
        assertThat(febKg.getValue()).isEqualByComparingTo("45");
        assertThat(febKg.getMom()).isEqualByComparingTo("-50.00");
        assertThat(cardReturn(manage.getMonthly(OTHER_STORE, "2026-01"), "vegetable", "份")
            .getReturned().getValue()).isEqualByComparingTo("8");
        var pork = manage.getDetail(STORE, "2026-01", "pork", new PageQuery(20, 1));
        assertThat(pork.getTotals()).filteredOn(row -> "kg".equals(row.getUnit())).singleElement()
            .satisfies(row -> assertThat(row.getReturnQty()).isEqualByComparingTo("5"));
        assertThat(pork.getRows()).noneMatch(row -> row.getProductId() == 6L || row.getProductId() == 10L || row.getProductId() == 12L);
        assertThat(pork.getRows()).filteredOn(row -> row.getProductId() == 4L).singleElement().satisfies(row -> {
            assertThat(row.getDemandQty()).isEqualByComparingTo("2");
            assertThat(row.getReturnQty()).isEqualByComparingTo("3");
            assertThat(row.getReturnUnit()).isEqualTo("kg");
        });
        var veg = manage.getDetail(STORE, "2026-01", "vegetable", new PageQuery(20, 1));
        assertThat(veg.getTotals()).filteredOn(row -> "份".equals(row.getUnit())).singleElement()
            .satisfies(row -> assertThat(row.getReturnQty()).isEqualByComparingTo("2"));
        assertThat(veg.getTotals()).filteredOn(row -> "kg".equals(row.getUnit())).singleElement()
            .satisfies(row -> assertThat(row.getReturnQty()).isEqualByComparingTo("90"));
        assertThat(veg.getRows()).noneMatch(row -> row.getProductId() == 11L);
        var febVeg = manage.getDetail(STORE, "2026-02", "vegetable", new PageQuery(20, 1));
        assertThat(febVeg.getRows()).filteredOn(row -> row.getProductId() == 9L).singleElement().satisfies(row -> {
            assertThat(row.getReturnQty()).isEqualByComparingTo("45");
            assertThat(row.getReturnUnit()).isEqualTo("kg");
            assertThat(row.getReturnHasBase()).isTrue();
            assertThat(row.getReturnMom()).isEqualByComparingTo("-50.00");
        });
    }

    @Test
    void sameDayRebuildReplacesSnapshotsAndBothLossTypes() throws SQLException {
        indicators.rebuildRange(JAN_1, JAN_1);
        var first = period(JAN_1, DAY);
        var firstLoss = lossRows();
        session.commit(true);

        indicators.rebuildRange(JAN_1, JAN_1);
        assertThat(indicators.query(STORE, JAN_1, JAN_1, DAY)).containsExactly(first);
        assertThat(lossRows()).containsExactlyElementsOf(firstLoss);
        assertThat(indicators.query(null, JAN_1, JAN_1, DAY)).hasSize(2);
        session.commit(true);

        // 删除白条收货确认后重算：旧分割损耗行消失，当日盘点损耗及分割产出仍可追溯。
        execute("UPDATE t_warehouse_demand_manage SET received_time = NULL WHERE id = 101");
        session.clearCache();
        indicators.rebuildRange(JAN_1, JAN_1);
        assertThat(lossRows()).containsExactly("store_daily_loss|2.500||");
        assertThat(period(JAN_1, DAY).getWhiteBarReceivedWeight()).isEqualByComparingTo("0");
        assertThat(period(JAN_1, DAY).getSplitProductWeight()).isEqualByComparingTo("80");
    }

    private static DemandManageVo vegetableDemand(long id, long productId, LocalDate confirmation) {
        DemandManageVo row = new DemandManageVo();
        row.setId(id);
        row.setProductId(productId);
        row.setProductUnit(productId == 9L ? "kg" : "份");
        row.setProductType("vegetable");
        row.setStoreDemandStatus(confirmation == null ? "SHIPPED" : "ARRIVED");
        row.setReceivedTime(confirmation == null ? null : confirmation.atTime(9, 30));
        return row;
    }

    private static StoreManageUnitRowVo cardReturn(StoreManageMonthlyVo month, String category, String unit) {
        return month.getCategories().stream().filter(card -> category.equals(card.getCategoryKey()))
            .flatMap(card -> card.getRows().stream()).filter(row -> unit.equals(row.getUnit()))
            .findFirst().orElseThrow();
    }

    private void rebuildFixtureDays() {
        indicators.rebuildRange(DEC_31, JAN_2);
        indicators.rebuildRange(JAN_31, FEB_1);
    }

    private StoreProductionIndicatorVo period(LocalDate date, StoreIndicatorPeriod period) {
        return indicators.query(STORE, date, date, period).getFirst();
    }

    private List<String> lossRows() throws SQLException {
        try (var statement = connection.createStatement();
             var rs = statement.executeQuery("""
                 SELECT CONCAT_WS('|',loss_type,loss_qty,COALESCE(white_bar_arrive_weight,''),
                     COALESCE(white_bar_split_weight,'')) AS item
                 FROM t_store_loss_record WHERE store_id = 11 AND loss_date = '2026-01-01'
                 ORDER BY loss_type
                 """)) {
            List<String> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(rs.getString("item"));
            }
            return rows;
        }
    }

    /**
     * MySQL 同一语句不能重复打开同一临时表。重复引用商品档案时，按出现次序绑定到等值的临时副本。
     * 只换表名，保留真实 Mapper 的连接、UNION、过滤和聚合 SQL。
     */
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare",
        args = {Connection.class, Integer.class}))
    private static class TemporaryCatalogCopies implements Interceptor {

        private static final Pattern CATALOG = Pattern.compile("\\bt_warehouse_product_info\\b");

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            var bound = ((StatementHandler) invocation.getTarget()).getBoundSql();
            var references = CATALOG.matcher(bound.getSql());
            StringBuilder sql = new StringBuilder();
            int index = 0;
            while (references.find()) {
                index++;
                references.appendReplacement(sql, index == 1 ? references.group()
                    : references.group() + "_fixture_" + index);
            }
            if (index > 1) {
                SystemMetaObject.forObject(bound).setValue("sql", references.appendTail(sql).toString());
            }
            return invocation.proceed();
        }
    }

    private void createTemporaryFromMigration(String filename, String table) throws Exception {
        String ddl = Files.readString(MIGRATIONS.resolve(filename));
        String create = "CREATE TABLE " + table + " (";
        int start = ddl.indexOf(create);
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = ddl.indexOf("ENGINE=InnoDB", start);
        execute(ddl.substring(start, ddl.indexOf(';', end)).replace(create, "CREATE TEMPORARY TABLE " + table + " ("));
    }

    private void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
