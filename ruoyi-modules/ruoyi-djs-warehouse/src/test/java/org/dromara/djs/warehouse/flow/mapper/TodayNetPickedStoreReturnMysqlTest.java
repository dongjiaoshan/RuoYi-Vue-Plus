package org.dromara.djs.warehouse.flow.mapper;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 今日领用剩余不扣门店退回入库（生产 2026-11-25 板蓝根青菜）。
 *
 * <p>门店退回的货入「退货专属篮」（plot / ear / white_bar 全空）并写 {@code store_return_in}。工人当天从这篮领了
 * 2.5kg，门店又退回 3.3kg：领用剩余若扣门店退回 = 2.5 − 3.3 &lt; 0，工人领的 2.5kg 一点都退不回去。
 * 领用剩余只扣工人自己的退回（prod_return_in / pick_return_in）。</p>
 *
 * <p>真跑 MyBatis 注解 SQL；临时表只在本机 MySQL 当前连接里，不碰业务表。
 * 需显式提供 DJS_STAT_MYSQL_URL / USER / PASSWORD，URL 只允许本机 3306 或隔离测试库 3316。</p>
 */
@Tag("local")
@Tag("dev")
@EnabledIfEnvironmentVariable(named = "DJS_STAT_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:(3306|3316)/.*")
class TodayNetPickedStoreReturnMysqlTest {

    private static final long PRODUCT = 25L;
    private static final long LOCATION = 6L;
    private static final long PLOT = 77L;

    private Connection connection;
    private SqlSession session;
    private StockFlowMapper mapper;
    private long nextId;

    @BeforeEach
    void prepare() throws SQLException {
        connection = DriverManager.getConnection(System.getenv("DJS_STAT_MYSQL_URL"),
            System.getenv("DJS_STAT_MYSQL_USER"), System.getenv("DJS_STAT_MYSQL_PASSWORD"));
        connection.setAutoCommit(false);
        execute("CREATE TEMPORARY TABLE t_warehouse_stock_flow (id BIGINT, tenant_id VARCHAR(20), del_flag CHAR(1), "
            + "product_id BIGINT, warehouse_id BIGINT, ear_no VARCHAR(32), white_bar_no VARCHAR(32), plot_id BIGINT, "
            + "flow_type VARCHAR(32), change_quantity DECIMAL(16,3), flow_date DATETIME)");

        Configuration configuration = new Configuration(new Environment("local-temporary-tables",
            new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(StockFlowMapper.class);
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection);
        mapper = session.getMapper(StockFlowMapper.class);
    }

    @AfterEach
    void close() throws SQLException {
        if (session != null) session.close();
        if (connection != null && !connection.isClosed()) connection.close();
    }

    @Test
    void storeReturnIntoReturnBasketDoesNotEatWorkerPick() throws SQLException {
        flow(null, "prod_pick_out", "2.500");
        flow(null, "store_return_in", "3.300");

        assertThat(mapper.sumTodayNetPickedByBasket(PRODUCT, LOCATION, null, null, null))
            .isEqualByComparingTo("2.500");
        assertThat(mapper.sumTodayNetPickedByNullEarGroup(PRODUCT)).isEqualByComparingTo("2.500");
    }

    @Test
    void workerReturnsStillReduceRemaining() throws SQLException {
        flow(PLOT, "prod_pick_out", "5.000");
        flow(PLOT, "prod_return_in", "0.902");
        flow(PLOT, "pick_return_in", "0.098");
        flow(PLOT, "pick_loss", "1.000");

        assertThat(mapper.sumTodayNetPickedByBasket(PRODUCT, LOCATION, null, null, PLOT))
            .isEqualByComparingTo("3.000");
    }

    private void flow(Long plotId, String flowType, String qty) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO t_warehouse_stock_flow "
            + "(id,tenant_id,del_flag,product_id,warehouse_id,ear_no,white_bar_no,plot_id,flow_type,change_quantity,flow_date) "
            + "VALUES (?,'1001','0',?,?,NULL,NULL,?,?,?,NOW())")) {
            ps.setLong(1, ++nextId);
            ps.setLong(2, PRODUCT);
            ps.setLong(3, LOCATION);
            ps.setObject(4, plotId);
            ps.setString(5, flowType);
            ps.setBigDecimal(6, new java.math.BigDecimal(qty));
            ps.executeUpdate();
        }
    }

    private void execute(String sql) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.execute();
        }
    }
}
