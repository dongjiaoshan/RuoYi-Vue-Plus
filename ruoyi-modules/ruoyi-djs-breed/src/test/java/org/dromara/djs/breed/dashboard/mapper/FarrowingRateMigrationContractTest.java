package org.dromara.djs.breed.dashboard.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 迁移契约测试：{@code V202609181000__BRD-STAT-FARROWRATE-001} 的建表与全量初始化
 * （BRD-STAT-FARROWRATE-001，甲方 row229）。
 *
 * <p><b>为什么必须有</b>：这份迁移里的四步初始化与 {@link FarrowingRateMapper} 的每日刷新
 * <b>语义必须一致</b>，但它是纯 SQL 文件、不被任何单测执行。2026-09-19 对抗验收实测：对它施加
 * 8 个语义破坏（删唯一键、初始化 LEFT JOIN 改内连接、写死 114、只认 DIE、删孤儿软删……）后
 * 跑<b>全仓 1730 个用例，全绿</b>。其中「删 UNIQUE KEY」会让 ON DUPLICATE KEY UPDATE 静默退化成
 * 裸 INSERT —— 重跑一次全表翻倍，分母翻倍、分娩率腰斩。</p>
 *
 * <p>沿用仓库既有做法（{@code VegHandleRow102SqlContractTest#migrationSql}）：读迁移原文做结构断言。</p>
 *
 * @author djs
 * @since BRD-STAT-FARROWRATE-001
 */
@Tag("local")
@Tag("dev")
@DisplayName("同期配种分娩台账 迁移契约（建表 + 全量初始化）")
class FarrowingRateMigrationContractTest {

    private static final String FILE = "V202609181000__BRD-STAT-FARROWRATE-001-farrowing-rate-table.sql";

    private static String migrationSql() throws Exception {
        Path p = Path.of("..", "..", "ruoyi-admin", "src", "main", "resources", "db", "migration", FILE);
        assertThat(Files.exists(p)).as("迁移脚本应存在于 %s", p.toAbsolutePath()).isTrue();
        return Files.readString(p).replaceAll("\\s+", " ").toLowerCase();
    }

    @Test
    @DisplayName("建表：唯一键是幂等的全部依据，删了它重跑会让全表翻倍")
    void tableGuardsIdempotency() throws Exception {
        String sql = migrationSql();
        assertThat(sql)
            .as("uk_tenant_breeding 是 ON DUPLICATE KEY UPDATE 的判重依据；"
                + "删掉它 upsert 静默退化成裸 INSERT，重跑一次分母翻倍、分娩率腰斩")
            .contains("unique key uk_tenant_breeding (tenant_id, breeding_id)");
        assertThat(sql)
            .as("建表必须幂等（迁移可能被重跑）")
            .contains("create table if not exists t_farm_farrowing_rate");
        assertThat(sql)
            .as("按预估分娩日取数是最热的查询路径（D-0090 分母）")
            .contains("key idx_tenant_expected (tenant_id, expected_farrow_date)");
    }

    @Test
    @DisplayName("判定节点读配置且有兜底；不得写死天数")
    void judgeDaysFromConfigWithFallback() throws Exception {
        String sql = migrationSql();
        assertThat(sql)
            .as("必须读 sow_farrow_judge_deadline_days，与 DashboardServiceImpl#farrowJudgeDeadlineDays 同源")
            .contains("config_key = 'sow_farrow_judge_deadline_days'");
        assertThat(sql)
            .as("配置缺失/非正时必须回退 119，否则 expected_farrow_date 全 NULL 撞 NOT NULL 列")
            .contains("if(@judge is null or @judge <= 0, 119, @judge)");
        assertThat(sql)
            .as("预估分娩日必须由 @judge 算出，写死天数会与每日刷新的 judgeDays 脱钩")
            .contains("interval @judge day");
        assertThat(sql)
            .as("不得出现写死的 114/119 天区间")
            .doesNotContain("interval 114 day")
            .doesNotContain("interval 119 day");
    }

    @Test
    @DisplayName("四步初始化与每日刷新同语义：全部 LEFT JOIN（只填不清就不收敛）")
    void fourStepsUseLeftJoinLikeIncremental() throws Exception {
        String sql = migrationSql();
        // 第一步：猪只主表缺行不该让整条配种记录消失（与 mapper step1 同口径）
        assertThat(sql).contains("left join t_farm_pig_info p on p.id = b.pig_id");
        // 二三四步：源记录被撤销时必须把已写入的日期置空，内连接只能填不能清
        assertThat(sql)
            .as("分娩回填必须 LEFT JOIN，否则撤销的分娩清不掉、这一窝永远算在分子里")
            .contains("left join (select tenant_id, breeding_id, min(date(farrow_date))");
        assertThat(sql)
            .as("返空流回填同理")
            .contains("left join (select tenant_id, related_breeding_id as bid, min(date(abnormal_date))");
        assertThat(sql)
            .as("死淘回填同理")
            .contains("left join (select tenant_id, pig_id, min(date(change_time))");
        assertThat(sql)
            .as("死淘必须同时认 DIE 与 ELIMINATE")
            .contains("event_type in ('die', 'eliminate')");
    }

    @Test
    @DisplayName("复活与孤儿软删两条链路都在（缺任一都会让分母长期错）")
    void reviveAndOrphanSweepPresent() throws Exception {
        String sql = migrationSql();
        assertThat(sql)
            .as("配种记录软删后恢复，台账行必须能翻回 del_flag='0'，否则唯一键把它永久挡在外面")
            .contains("del_flag = '0', update_time = now()");
        assertThat(sql)
            .as("源配种记录已软删 → 台账行跟着软删，否则分母里永久留着一条已撤销的配种")
            .contains("set r.del_flag = '1'")
            .contains("where r.del_flag = '0' and b.id is null");
    }
}
