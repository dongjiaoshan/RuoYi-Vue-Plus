package org.dromara.djs.breed.event.farrow.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PigFarrowMapper} 首页徽标两条聚合 SQL 的契约（V6 行243）。
 *
 * <p><b>为什么单独一支</b>：这两条是注解 SQL，service 侧全是 mock，口径写错单测照样全绿，
 * 但线上表现是「列表里有 N 窝、红点却恒为 0」——两边对不上。行242 起整窝在分娩提交时就建好档，
 * 旧的「live_born 大于已打标数」口径必然恒 0，这里把新口径钉死。</p>
 *
 * @author djs
 */
@Tag("dev")
class PigFarrowMapperSqlContractTest {

    private static String selectSql(String methodName) {
        for (Method m : PigFarrowMapper.class.getDeclaredMethods()) {
            Select sel = m.getAnnotation(Select.class);
            if (sel != null && methodName.equals(m.getName())) {
                return String.join(" ", sel.value()).replaceAll("\\s+", " ");
            }
        }
        throw new AssertionError(methodName + " 上没有 @Select —— 徽标口径被挪走了，本类的保护随之失效");
    }

    @Test
    @DisplayName("徽标窝数 = 未断奶窝（NOT EXISTS t_farm_pig_weaning），不是「未打标」")
    void countUsesUnweanedSemantics() {
        String sql = selectSql("countPendingFarrows");
        assertThat(sql)
            .contains("FROM t_farm_pig_farrow f")
            .contains("NOT EXISTS ( SELECT 1 FROM t_farm_pig_weaning w")
            .contains("WHERE w.farrow_id = f.id AND w.del_flag = '0' AND w.tenant_id = f.tenant_id");
        assertThat(sql)
            .as("还在按已打标头数算 = 行242 自动建档后恒 0，红点永远不亮")
            .doesNotContain("t_farm_pig_pigletno");
    }

    @Test
    @DisplayName("徽标头数 = 未断奶窝的 live_born 合计，同一套 NOT EXISTS 口径")
    void sumUsesUnweanedSemantics() {
        String sql = selectSql("sumPendingPiglets");
        assertThat(sql)
            .contains("SUM(COALESCE(f.live_born, 0))")
            .contains("NOT EXISTS ( SELECT 1 FROM t_farm_pig_weaning w")
            .contains("WHERE w.farrow_id = f.id AND w.del_flag = '0' AND w.tenant_id = f.tenant_id");
        assertThat(sql).doesNotContain("t_farm_pig_pigletno");
    }

    @Test
    @DisplayName("两条都只算未软删、本租户的窝")
    void respectsSoftDeleteAndTenant() {
        for (String name : new String[]{"countPendingFarrows", "sumPendingPiglets"}) {
            String sql = selectSql(name);
            assertThat(sql).as(name).contains("f.del_flag = '0'").contains("f.tenant_id = '1001'");
        }
    }

    @Test
    @DisplayName("不等于一律写 !=：<> 在 <script> 里会被当 XML 标签，启动期崩容器（2026-09-20 踩过）")
    void neverUsesAngleBracketNotEquals() {
        for (String name : new String[]{"countPendingFarrows", "sumPendingPiglets"}) {
            assertThat(selectSql(name)).as(name).doesNotContain("<>");
        }
    }
}
