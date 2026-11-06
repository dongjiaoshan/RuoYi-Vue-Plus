package org.dromara.djs.warehouse.flow.mapper;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.djs.warehouse.flow.constant.FlowDisplayScope;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatDetailQuery;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatOutVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运行真实 MyBatis 查询；临时表仅存在于本机 MySQL 的当前连接，不改业务表。
 * 运行时显式提供 DJS_STAT_MYSQL_URL / USER / PASSWORD；URL 仅允许本机 3306。
 */
@Tag("local")
@Tag("dev")
@EnabledIfEnvironmentVariable(named = "DJS_STAT_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:3306/.*")
class InoutStatShipmentMysqlTest {

    private static final List<String> EXCLUDED = FlowDisplayScope.OUT_EXCLUDED.stream()
        .filter(type -> !"ship_out".equals(type)).toList();

    private Connection connection;
    private SqlSession session;
    private InoutStatMapper mapper;
    private long nextId;

    @BeforeEach
    void prepareProductionCounterexample() throws SQLException {
        connection = DriverManager.getConnection(System.getenv("DJS_STAT_MYSQL_URL"),
            System.getenv("DJS_STAT_MYSQL_USER"), System.getenv("DJS_STAT_MYSQL_PASSWORD"));
        connection.setAutoCommit(false);
        execute("CREATE TEMPORARY TABLE t_warehouse_product_info (id BIGINT, tenant_id VARCHAR(20), "
            + "del_flag CHAR(1), product_id VARCHAR(32), product_name VARCHAR(64), product_type INT, "
            + "product_spec VARCHAR(32), product_unit VARCHAR(16))");
        execute("CREATE TEMPORARY TABLE t_warehouse_stock_flow (id BIGINT, tenant_id VARCHAR(20), "
            + "del_flag CHAR(1), product_id BIGINT, inout_type VARCHAR(3), flow_type VARCHAR(32), "
            + "stock_out_dest VARCHAR(32), change_quantity DECIMAL(16,3), demand_id BIGINT, "
            + "white_bar_no VARCHAR(32), flow_date DATETIME, operator_id BIGINT, create_time DATETIME)");
        execute("CREATE TEMPORARY TABLE t_warehouse_product_production (id BIGINT, tenant_id VARCHAR(20), "
            + "del_flag CHAR(1), product_id BIGINT, white_bar_no VARCHAR(32), store_id BIGINT, "
            + "deliver_dest VARCHAR(32))");
        execute("CREATE TEMPORARY TABLE t_warehouse_demand_manage (id BIGINT, tenant_id VARCHAR(20), store_id BIGINT)");
        execute("CREATE TEMPORARY TABLE t_md_store (id BIGINT, tenant_id VARCHAR(20), del_flag CHAR(1), store_name VARCHAR(64))");
        execute("CREATE TEMPORARY TABLE sys_user (user_id BIGINT, del_flag CHAR(1), nick_name VARCHAR(64))");
        execute("INSERT INTO t_warehouse_product_info VALUES "
            + "(1,'1001','0','Y171','右半扇',1,NULL,'kg'),"
            + "(2,'1001','0','Y170','左半扇',1,NULL,'kg'),"
            + "(3,'1001','0','OTHER','其他发货',1,NULL,'kg')");
        execute("INSERT INTO t_md_store VALUES (11,'1001','0','二七店'),(12,'1001','0','锦绣门店')");
        execute("INSERT INTO t_warehouse_demand_manage VALUES (21,'1001',11),(22,'1001',12)");
        production(1, "BAR2611050001", 11L, null, "1001", "0");
        production(2, "BAR2611050002", 12L, null, "1001", "0");
        flow(1, "cut_out", "ship_dock", "61.750", null, "BAR2611050001", "2026-11-05");
        flow(1, "ship_out", "ship_dock", "61.750", 21L, null, "2026-11-05");
        flow(2, "cut_out", "ship_dock", "61.000", null, "BAR2611050002", "2026-11-05");
        flow(2, "ship_out", "ship_dock", "61.000", 22L, null, "2026-11-05");
        flow(3, "ship_out", "ship_dock", "13.500", 21L, null, "2026-11-05");

        Configuration configuration = new Configuration(new Environment("local-temporary-tables",
            new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(InoutStatMapper.class);
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection);
        mapper = session.getMapper(InoutStatMapper.class);
    }

    @AfterEach
    void closeTemporaryTables() throws SQLException {
        if (session != null) session.close();
        if (connection != null && !connection.isClosed()) connection.close();
    }

    @Test
    void sameHalfIsCountedOnceInListTotalsAndExportDetails() {
        InoutStatQuery query = query("2026-11-05", "2026-11-05");
        List<InoutStatOutVo> rows = mapper.selectOutStatList("1001", query, EXCLUDED);
        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row -> assertThat(row.getStoreId()).isNotNull());
        assertThat(mapper.selectOutSummary("1001", query, EXCLUDED).getWeightQty())
            .isEqualByComparingTo("136.250");
        for (InoutStatOutVo row : rows) {
            InoutStatDetailQuery detail = new InoutStatDetailQuery();
            detail.setDateFrom(query.getDateFrom());
            detail.setDateTo(query.getDateTo());
            detail.setProductCode(row.getProductCode());
            detail.setOutDest(row.getStockOutDest());
            detail.setStoreId(row.getStoreId());
            assertThat(mapper.selectOutDetailList("1001", detail, EXCLUDED))
                .hasSize(1).allSatisfy(item -> assertThat(item.getOutboundQty())
                    .isEqualByComparingTo(row.getOutboundQty()));
        }
    }

    @Test
    void pendingTransferIsNotAnActualShipment() throws SQLException {
        production(1, "PENDING", 11L, null, "1001", "0");
        flow(1, "cut_out", "ship_dock", "7", null, "PENDING", "2026-11-05");
        assertWeight("2026-11-05", "2026-11-05", "136.250");
    }

    @Test
    void crossDayShipmentCountsOnShipmentDateOnly() throws SQLException {
        production(1, "CROSSDAY", 11L, null, "1001", "0");
        flow(1, "cut_out", "ship_dock", "5", null, "CROSSDAY", "2026-11-05");
        flow(1, "ship_out", "ship_dock", "5", 21L, null, "2026-11-06");
        assertWeight("2026-11-05", "2026-11-05", "136.250");
        assertWeight("2026-11-06", "2026-11-06", "5.000");
        assertWeight("2026-11-05", "2026-11-06", "141.250");
    }

    @Test
    void untraceableHistoricalTransfersStayVisible() throws SQLException {
        flow(1, "cut_out", "ship_dock", "3", null, null, "2026-11-05");
        flow(1, "cut_out", "ship_dock", "4", null, "", "2026-11-05");
        flow(1, "cut_out", "ship_dock", "5", null, "NO-PRODUCTION", "2026-11-05");
        production(1, "NO-STORE", null, null, "1001", "0");
        flow(1, "cut_out", "ship_dock", "6", null, "NO-STORE", "2026-11-05");
        assertWeight("2026-11-05", "2026-11-05", "154.250");
        assertThat(mapper.selectOutStatList("1001", query("2026-11-05", "2026-11-05"), EXCLUDED))
            .anySatisfy(row -> {
                assertThat(row.getStoreId()).isNull();
                assertThat(row.getOutboundQty()).isEqualByComparingTo("18.000");
            });
    }

    @Test
    void directWarehouseGiftAndOtherDestinationsAreNotLost() throws SQLException {
        production(1, "DIRECT", 11L, "warehouse_out", "1001", "0");
        flow(1, "cut_out", "ship_dock", "9", null, "DIRECT", "2026-11-05");
        production(1, "GIFT", 11L, "gift", "1001", "0");
        flow(1, "cut_out", "ship_dock", "11", null, "GIFT", "2026-11-05");
        flow(1, "cut_out", "bar_cut", "8", null, "BAR2611050001", "2026-11-05");
        flow(1, "cut_out", null, "2", null, "BAR2611050001", "2026-11-05");
        flow(1, "backstage_out", "ship_dock", "3", null, "BAR2611050001", "2026-11-05");
        assertWeight("2026-11-05", "2026-11-05", "169.250");
        InoutStatQuery dockOnly = query("2026-11-05", "2026-11-05");
        dockOnly.setStockOutDests(List.of("ship_dock"));
        assertThat(mapper.selectOutSummary("1001", dockOnly, EXCLUDED).getWeightQty())
            .isEqualByComparingTo("159.250");
    }

    @Test
    void productionIdentityRequiresSameTenantProductAndHalf() throws SQLException {
        production(1, "OTHER-TENANT", 11L, null, "2001", "0");
        flow(1, "cut_out", "ship_dock", "6", null, "OTHER-TENANT", "2026-11-05");
        production(2, "OTHER-PRODUCT", 11L, null, "1001", "0");
        flow(1, "cut_out", "ship_dock", "7", null, "OTHER-PRODUCT", "2026-11-05");
        production(1, "ANOTHER-HALF", 11L, null, "1001", "0");
        flow(1, "cut_out", "ship_dock", "8", null, "UNMATCHED-HALF", "2026-11-05");
        assertWeight("2026-11-05", "2026-11-05", "157.250");
    }

    @Test
    void archivedOrMultipleProductionsDoNotReclassifyOrMultiplyHistoricalFlow() throws SQLException {
        production(1, "ARCHIVED", 11L, null, "1001", "1");
        flow(1, "cut_out", "ship_dock", "4", null, "ARCHIVED", "2026-11-05");
        production(1, "BAR2611050001", 11L, null, "1001", "0");
        assertWeight("2026-11-05", "2026-11-05", "136.250");
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*), SUM(change_quantity) FROM t_warehouse_stock_flow")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(6);
            assertThat(result.getBigDecimal(2)).isEqualByComparingTo("263.000");
        }
    }

    private void assertWeight(String from, String to, String expected) {
        assertThat(mapper.selectOutSummary("1001", query(from, to), EXCLUDED).getWeightQty())
            .isEqualByComparingTo(expected);
    }

    private InoutStatQuery query(String from, String to) {
        InoutStatQuery query = new InoutStatQuery();
        query.setDateFrom(Date.valueOf(from));
        query.setDateTo(Date.valueOf(to));
        return query;
    }

    private void flow(long product, String type, String dest, String quantity, Long demand,
                      String barNo, String day) throws SQLException {
        execute("INSERT INTO t_warehouse_stock_flow VALUES (?, '1001','0',?,'OT',?,?,?,?,?,?,NULL,?)",
            ++nextId, product, type, dest, new BigDecimal(quantity), demand, barNo,
            day + " 12:00:00", day + " 12:00:00");
    }

    private void production(long product, String barNo, Long store, String dest, String tenant,
                            String deleted) throws SQLException {
        execute("INSERT INTO t_warehouse_product_production VALUES (?,?,?,?,?,?,?)",
            ++nextId, tenant, deleted, product, barNo, store, dest);
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.execute();
        }
    }
}
