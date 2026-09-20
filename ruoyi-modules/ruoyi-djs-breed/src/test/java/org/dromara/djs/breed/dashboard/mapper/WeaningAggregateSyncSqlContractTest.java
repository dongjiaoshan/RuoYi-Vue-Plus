package org.dromara.djs.breed.dashboard.mapper;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WeaningAggregateSyncMapper} 的 SQL 契约。
 *
 * <p><b>为什么单独一支</b>：这个 mapper 里那条 UPDATE 是整批改动里<b>唯一会写业务表、且写错就会删数据</b>的语句
 * —— 没有 {@code EXISTS} 守卫时，它会把「只录了汇总、没录逐头明细」的历史断奶记录头数与重量一把清零。
 * 而 service 侧的单测是 mock 的、只断言了调用窗口参数，2026-09-20 的对抗验收把守卫整段删掉之后
 * 446 个单测<b>全绿</b>。本类就是把那一行钉死。</p>
 *
 * <p>钉的是语义不变量而不是全串 golden：SQL 还会因为格式/别名调整变动，但下面这几条一旦破就是事故。</p>
 *
 * @author djs
 */
@Tag("dev")
class WeaningAggregateSyncSqlContractTest {

    private static String resyncSql() {
        for (Method m : WeaningAggregateSyncMapper.class.getDeclaredMethods()) {
            Update u = m.getAnnotation(Update.class);
            if (u != null && "resyncFromDetail".equals(m.getName())) {
                return String.join(" ", u.value()).replaceAll("\\s+", " ");
            }
        }
        throw new AssertionError("resyncFromDetail 上没有 @Update —— 回算逻辑被挪走了，本类的保护随之失效");
    }

    @Test
    @DisplayName("EXISTS 守卫必须在：没有它，只录汇总没录明细的历史断奶记录会被一把清零（删数据）")
    void keepsExistsGuard() {
        String sql = resyncSql();
        assertThat(sql)
            .as("守卫被删 = 无明细的记录 weaned_count/weaned_weight/avg 全变 0，这是删数据不是同步")
            .contains("AND EXISTS (SELECT 1 FROM t_farm_pig_weaning_detail d")
            .contains("WHERE d.weaning_id = w.id AND d.del_flag = '0')");
    }

    @Test
    @DisplayName("只回算未软删的记录与未软删的明细")
    void respectsSoftDelete() {
        String sql = resyncSql();
        assertThat(sql).contains("w.del_flag = '0'");
        // 三个子查询 + EXISTS 守卫各带一次明细侧软删过滤
        assertThat(sql.split("d\\.del_flag = '0'", -1).length - 1)
            .as("任何一处漏掉，软删掉的明细就会被算回汇总里")
            .isEqualTo(4);
    }

    @Test
    @DisplayName("按断奶日期的半开区间 [from, to) 圈窗口，不是闭区间")
    void usesHalfOpenWindow() {
        String sql = resyncSql();
        assertThat(sql)
            .as("右边界写成 <= 会把次日 00:00:00 的记录算进来，与日表按日归档的口径对不上")
            .contains("w.weaning_date >= #{from} AND w.weaning_date < #{to}");
    }

    @Test
    @DisplayName("均重由本次算出的总重除头数得到，不读旧值")
    void recomputesAverageFromDetail() {
        String sql = resyncSql();
        assertThat(sql)
            .as("均重若沿用旧值，头数和总重更新了它却不动，三列会自相矛盾")
            .contains("w.avg_weaned_weight = ( SELECT COALESCE(SUM(d.weight),0) / COUNT(*)");
    }

    @Test
    @DisplayName("租户必须显式绑，不依赖拦截器改写裸 SQL")
    void bindsTenantExplicitly() {
        assertThat(resyncSql()).contains("w.tenant_id = #{tenantId}");
    }
}
