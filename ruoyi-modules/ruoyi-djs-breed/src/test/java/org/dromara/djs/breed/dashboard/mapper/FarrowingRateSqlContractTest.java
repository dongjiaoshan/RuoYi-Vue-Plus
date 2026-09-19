package org.dromara.djs.breed.dashboard.mapper;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 契约测试：把 {@code t_farm_farrowing_rate} 的 8 条 SQL 钉住
 * （BRD-STAT-FARROWRATE-001；口径 D-0090 / D-0091 / D-0092）。
 *
 * <p><b>三层保护，缺一层都被实测打穿过</b>：</p>
 * <ol>
 *   <li><b>golden 全串</b> —— 逐字比对。挡住「只加不删」型改动（{@code contains} 是单调断言，
 *       实测 {@code ) OR 1=1}、分母加 {@code AND farrow_date IS NOT NULL}、
 *       分子加 {@code OR farrow_date IS NOT NULL} 全部免疫）。</li>
 *   <li><b>完整性闸</b> —— GOLDEN 的键必须等于 mapper 上所有带 {@code @Select}/{@code @Update}
 *       的方法。实测：加第 9 个 SQL 方法，只对 map 遍历的话照样全绿、新 SQL 完全没保护。</li>
 *   <li><b>语义不变量</b> —— golden 可以被「照失败提示复制成新值」而退化成永真
 *       （实测：施加真缺陷 + 同步改 golden → 全绿）。下面几条不变量描述的是<b>口径本身</b>，
 *       改坏 SQL 时改不动、复制不掉。</li>
 * </ol>
 *
 * <p>这几段 SQL 在 service 单测里一行都不会执行（mapper 是 mock），全仓也没有 DB 级测试，
 * 所以 SQL 语义错误在 CI 里结构上不可见 —— 这三层是它仅有的闸。</p>
 *
 * @author djs
 * @since BRD-STAT-FARROWRATE-001
 */
@Tag("local")
@Tag("dev")
@DisplayName("同期配种分娩台账 SQL 契约（golden + 完整性闸 + 语义不变量）")
class FarrowingRateSqlContractTest {

    // ===================== golden：改 SQL 必须同步改这里 =====================

    private static final String G_STEP1_BREEDING =
        "INSERT INTO t_farm_farrowing_rate (tenant_id, pig_id, ear_tag, breeding_id, "
            + "breeding_date, expected_farrow_date, create_by, create_time, del_flag) SELECT "
            + "b.tenant_id, b.pig_id, p.ear_tag, b.id, DATE(b.breeding_date), DATE(b.breeding_date) + "
            + "INTERVAL #{judgeDays} DAY, 1, NOW(), '0' FROM t_farm_pig_breeding b LEFT JOIN "
            + "t_farm_pig_info p ON p.id = b.pig_id AND p.del_flag = '0' WHERE b.tenant_id = "
            + "#{tenantId} AND b.del_flag = '0' AND b.breeding_date >= #{from} - INTERVAL "
            + "#{judgeDays} DAY AND b.breeding_date < #{to} ON DUPLICATE KEY UPDATE del_flag = '0', "
            + "update_time = IF(t_farm_farrowing_rate.del_flag = '0', "
            + "t_farm_farrowing_rate.update_time, NOW()) ";

    private static final String G_STEP1B_RESYNC =
        "UPDATE t_farm_farrowing_rate r JOIN t_farm_pig_breeding b ON b.id = r.breeding_id AND "
            + "b.tenant_id = r.tenant_id AND b.del_flag = '0' LEFT JOIN t_farm_pig_info p ON p.id = "
            + "b.pig_id AND p.del_flag = '0' SET r.pig_id = b.pig_id, r.ear_tag = p.ear_tag, "
            + "r.breeding_date = DATE(b.breeding_date), r.expected_farrow_date = "
            + "DATE(b.breeding_date) + INTERVAL #{judgeDays} DAY, r.update_time = NOW() WHERE "
            + "r.tenant_id = #{tenantId} AND r.del_flag = '0' AND r.breeding_date >= #{from} - "
            + "INTERVAL #{judgeDays} DAY AND r.breeding_date < #{to} AND NOT (r.pig_id <=> b.pig_id "
            + "AND r.ear_tag <=> p.ear_tag AND r.breeding_date <=> DATE(b.breeding_date) AND "
            + "r.expected_farrow_date <=> DATE(b.breeding_date) + INTERVAL #{judgeDays} DAY) ";

    private static final String G_STEP2_FARROW =
        "UPDATE t_farm_farrowing_rate r LEFT JOIN (SELECT tenant_id, breeding_id, "
            + "MIN(DATE(farrow_date)) AS d FROM t_farm_pig_farrow WHERE tenant_id = #{tenantId} AND "
            + "del_flag = '0' AND breeding_id IS NOT NULL GROUP BY tenant_id, breeding_id) f ON "
            + "f.tenant_id = r.tenant_id AND f.breeding_id = r.breeding_id SET r.farrow_date = f.d, "
            + "r.update_time = NOW() WHERE r.tenant_id = #{tenantId} AND r.del_flag = '0' AND NOT "
            + "(r.farrow_date <=> f.d) AND r.breeding_date >= #{from} - INTERVAL #{judgeDays} DAY AND "
            + "r.breeding_date < #{to} ";

    private static final String G_STEP3_ABNORMAL =
        "UPDATE t_farm_farrowing_rate r LEFT JOIN (SELECT tenant_id, related_breeding_id AS "
            + "bid, MIN(DATE(abnormal_date)) AS d FROM t_farm_pig_abnormal WHERE tenant_id = "
            + "#{tenantId} AND del_flag = '0' AND related_breeding_id IS NOT NULL GROUP BY tenant_id, "
            + "related_breeding_id) a ON a.tenant_id = r.tenant_id AND a.bid = r.breeding_id SET "
            + "r.abnormal_date = a.d, r.update_time = NOW() WHERE r.tenant_id = #{tenantId} AND "
            + "r.del_flag = '0' AND NOT (r.abnormal_date <=> a.d) AND r.breeding_date >= #{from} - "
            + "INTERVAL #{judgeDays} DAY AND r.breeding_date < #{to} ";

    private static final String G_STEP4_CULL =
        "UPDATE t_farm_farrowing_rate r LEFT JOIN (SELECT tenant_id, pig_id, "
            + "MIN(DATE(change_time)) AS d FROM t_farm_status_record WHERE tenant_id = #{tenantId} "
            + "AND event_type IN ('DIE','ELIMINATE') GROUP BY tenant_id, pig_id) s ON s.tenant_id = "
            + "r.tenant_id AND s.pig_id = r.pig_id SET r.cull_date = s.d, r.update_time = NOW() WHERE "
            + "r.tenant_id = #{tenantId} AND r.del_flag = '0' AND NOT (r.cull_date <=> s.d) AND "
            + "r.breeding_date >= #{from} - INTERVAL #{judgeDays} DAY AND r.breeding_date < #{to} ";

    private static final String G_SOFT_DELETE_ORPHANS =
        "UPDATE t_farm_farrowing_rate r LEFT JOIN t_farm_pig_breeding b ON b.id = r.breeding_id "
            + "AND b.tenant_id = r.tenant_id AND b.del_flag = '0' SET r.del_flag = '1', r.update_time "
            + "= NOW() WHERE r.tenant_id = #{tenantId} AND r.del_flag = '0' AND r.breeding_date >= "
            + "#{from} - INTERVAL #{judgeDays} DAY AND r.breeding_date < #{to} AND b.id IS NULL ";

    private static final String G_SELECT_FARROW_RATE =
        "SELECT COUNT(*) AS denom, COALESCE(SUM(farrow_date IS NOT NULL AND farrow_date <= "
            + "expected_farrow_date), 0) AS numer, COALESCE(SUM(farrow_date IS NOT NULL AND "
            + "farrow_date > expected_farrow_date), 0) AS farrowLate FROM t_farm_farrowing_rate WHERE "
            + "tenant_id = #{tenantId} AND del_flag = '0' AND expected_farrow_date >= #{from} AND "
            + "expected_farrow_date < #{to} AND expected_farrow_date <= #{asOf} ";

    private static final String G_COUNT_JUDGE_DAYS_DRIFT =
        "SELECT COUNT(*) FROM t_farm_farrowing_rate WHERE tenant_id = #{tenantId} AND del_flag "
            + "= '0' AND expected_farrow_date <> breeding_date + INTERVAL #{judgeDays} DAY ";

    private static final Map<String, String> GOLDEN = new LinkedHashMap<>();

    static {
        GOLDEN.put("refreshStep1Breeding", G_STEP1_BREEDING);
        GOLDEN.put("refreshStep1bResync", G_STEP1B_RESYNC);
        GOLDEN.put("refreshStep2Farrow", G_STEP2_FARROW);
        GOLDEN.put("refreshStep3Abnormal", G_STEP3_ABNORMAL);
        GOLDEN.put("refreshStep4Cull", G_STEP4_CULL);
        GOLDEN.put("softDeleteOrphans", G_SOFT_DELETE_ORPHANS);
        GOLDEN.put("selectFarrowRate", G_SELECT_FARROW_RATE);
        GOLDEN.put("countJudgeDaysDrift", G_COUNT_JUDGE_DAYS_DRIFT);
    }

    /** 取 mapper 方法上 @Select / @Update 里的真实 SQL，压平空白便于逐字比对。 */
    private static String sqlOf(String methodName) {
        for (Method m : FarrowingRateMapper.class.getDeclaredMethods()) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            Select sel = m.getAnnotation(Select.class);
            Update upd = m.getAnnotation(Update.class);
            String raw = sel != null ? String.join(" ", sel.value())
                : upd != null ? String.join(" ", upd.value()) : null;
            if (raw != null) {
                return raw.replaceAll("\\s+", " ").trim();
            }
        }
        throw new AssertionError("找不到方法或它没有 @Select/@Update：" + methodName);
    }

    // ===================== 第 1 层：golden 全串 =====================

    @Test
    @DisplayName("8 条 SQL 逐字与 golden 一致 —— 任何增删改都会红，包括「只加不删」")
    void testAllSqlMatchGolden() {
        GOLDEN.forEach((name, golden) -> assertThat(sqlOf(name))
            .as("%s 的 SQL 变了。若是有意的口径调整，请同步改 golden **并确认下面的语义不变量仍然成立**，"
                + "在 PR 里写明改的是哪条口径（D-0090 分母 / D-0091 分子 / D-0092 数据源）；"
                + "若不是有意的，那就是一次悄悄的口径改动，必须回退。", name)
            .isEqualTo(golden.trim()));
    }

    // ===================== 第 2 层：完整性闸 =====================

    @Test
    @DisplayName("mapper 上每个带 @Select/@Update 的方法都必须在 GOLDEN 里（新增 SQL 不许漏保护）")
    void testGoldenCoversEverySqlMethod() {
        Set<String> declared = Arrays.stream(FarrowingRateMapper.class.getDeclaredMethods())
            .filter(m -> m.getAnnotation(Select.class) != null || m.getAnnotation(Update.class) != null)
            .map(Method::getName)
            .collect(Collectors.toSet());
        assertThat(declared)
            .as("给 FarrowingRateMapper 加了新的 @Select/@Update 却没加 golden —— "
                + "那条 SQL 将完全没有保护。把它补进 GOLDEN。")
            .isEqualTo(GOLDEN.keySet());
    }

    // ===================== 第 3 层：语义不变量（复制 golden 也绕不过） =====================

    @Test
    @DisplayName("D-0091 分子：分娩日期非空 AND ≤ 预估分娩日，且该谓词不得上移进分母")
    void testNumeratorInvariant() {
        String sql = sqlOf("selectFarrowRate");
        String where = sql.substring(sql.indexOf(" WHERE "));
        assertThat(sql)
            .as("甲方原话：分子 = 分娩日期不为空且分娩日期小于等于预估分娩日的记录数")
            .contains("AND farrow_date <= expected_farrow_date), 0) AS " + FarrowingRateMapper.K_NUMER);
        assertThat(where)
            .as("分子谓词一旦出现在 WHERE 里，分母会被同样过滤掉 → 分娩率恒 ≈100%%")
            .doesNotContain("farrow_date IS NOT NULL")
            .doesNotContain("farrow_date <= expected_farrow_date");
    }

    @Test
    @DisplayName("D-0090 分母：只算预估分娩日已到的，且 WHERE 顶层不得出现 OR（整段被旁路）")
    void testDenominatorInvariant() {
        String sql = sqlOf("selectFarrowRate");
        String where = sql.substring(sql.indexOf(" WHERE "));
        assertThat(where)
            .as("没有 asOf 上界，未到期批次会进分母（实测年分娩率 97.73%% → 24.16%%）")
            .contains("expected_farrow_date <= #{asOf}");
        assertThat(where).contains("tenant_id = #{tenantId}").contains("del_flag = '0'");
        assertThat(where)
            .as("WHERE 里出现 OR 意味着整段过滤可被旁路（实测 `) OR 1 = 1` 让租户+软删+窗口+收口全废）")
            .doesNotContain(" OR ");
    }

    @Test
    @DisplayName("窗口口径只有一处定义：六条刷新语句逐字共用 WIN_SRC / WIN_LEDGER")
    void testWindowDefinedOnce() {
        assertThat(sqlOf("refreshStep1Breeding"))
            .as("源侧窗口必须来自 WIN_SRC")
            .contains(FarrowingRateMapper.WIN_SRC.replaceAll("\\s+", " ").trim());
        for (String m : new String[] {"refreshStep1bResync", "refreshStep2Farrow",
            "refreshStep3Abnormal", "refreshStep4Cull", "softDeleteOrphans"}) {
            assertThat(sqlOf(m))
                .as("%s 的窗口必须来自 WIN_LEDGER —— 窗口口径写第二遍就是连错三版的根因", m)
                .contains(FarrowingRateMapper.WIN_LEDGER.replaceAll("\\s+", " ").trim());
        }
        assertThat(FarrowingRateMapper.WIN_SRC.replace("b.", "").trim())
            .as("两个常量必须是同一个区间，只是换了表别名")
            .isEqualTo(FarrowingRateMapper.WIN_LEDGER.replace("r.", "").trim());
    }

    @Test
    @DisplayName("四步回填必须 LEFT JOIN（内连接只填不清，撤销的结局永远留在台账里）")
    void testBackfillUsesLeftJoinSoRevocationClears() {
        for (String m : new String[] {"refreshStep2Farrow", "refreshStep3Abnormal", "refreshStep4Cull"}) {
            assertThat(sqlOf(m)).as("%s 必须 LEFT JOIN", m).contains("LEFT JOIN (SELECT");
            assertThat(sqlOf(m)).as("%s 必须带空转闸，否则每晚重写全部行", m).contains("AND NOT (r.");
        }
        assertThat(sqlOf("softDeleteOrphans"))
            .as("孤儿判据丢了会把整张台账软删光")
            .contains("AND b.id IS NULL");
        assertThat(sqlOf("refreshStep1Breeding"))
            .as("猪只主表缺行不该让整条配种记录从台账消失")
            .contains("LEFT JOIN t_farm_pig_info");
    }

    @Test
    @DisplayName("列别名与 Java 读取用的 key 同源")
    void testAliasesBoundToJavaKeys() {
        String sql = sqlOf("selectFarrowRate");
        assertThat(sql).contains("AS " + FarrowingRateMapper.K_DENOM);
        assertThat(sql).contains("AS " + FarrowingRateMapper.K_NUMER);
        assertThat(sql).contains("AS " + FarrowingRateMapper.K_FARROW_LATE);
        assertThat(FarrowingRateMapper.K_DENOM).isEqualTo("denom");
        assertThat(FarrowingRateMapper.K_NUMER).isEqualTo("numer");
        assertThat(FarrowingRateMapper.K_FARROW_LATE).isEqualTo("farrowLate");
    }

    @Test
    @DisplayName("8 条 SQL 都能被 JSqlParser 解析（解析不了只在运行期炸）")
    void testAllStatementsAreParseable() {
        TenantLineInnerInterceptor interceptor = new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new StringValue("1001");
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // 本表 SQL 自己已显式写 tenant_id 条件；这里只验可解析性，不验改写结果。
                return true;
            }
        });
        GOLDEN.keySet().forEach(name -> {
            String probe = sqlOf(name)
                .replace("#{judgeDays}", "119")
                .replace("#{tenantId}", "'1001'")
                .replace("#{from}", "'2026-07-01'")
                .replace("#{to}", "'2026-10-01'")
                .replace("#{asOf}", "'2026-09-17'");
            assertThat(interceptor.parserSingle(probe, null))
                .as("%s 的 SQL 必须能被 JSqlParser 解析", name)
                .isNotBlank();
        });
    }
}
