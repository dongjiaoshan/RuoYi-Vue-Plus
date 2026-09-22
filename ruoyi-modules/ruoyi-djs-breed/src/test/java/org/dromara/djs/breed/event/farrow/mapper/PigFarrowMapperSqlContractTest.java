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
 * 但线上表现是「列表里有 N 窝、红点却恒为 0」——两边对不上。这里把现行口径钉死。</p>
 *
 * <p>现行口径 = D-0112（甲方 2026-09-22 选②按仔猪算）+ D-0110（零档案老窝要留着触发补建）：
 * 窝里还有没断奶的仔猪就算待办，整窝断完才消失；头数按未断奶头数算，零档案老窝退回 live_born。
 * 旧的「本窝没有断奶记录」口径已作废——断第一头整窝就消失，剩下还在哺乳的仔猪出生重再也改不了。</p>
 *
 * <p>另有一条本类顺带守的坑：Java 文本块会吃掉行尾空格，{@code "… CASE WHEN """ + 常量} 会拼出
 * {@code CASE WHENNOT EXISTS} —— 编译过、单测过、一调接口 JSqlParser 直接炸（2026-09-22 踩过）。
 * 故拼接处一律用显式 {@code " "} 胶水，并在 {@link #hasNoGluedKeywords} 里钉死。</p>
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
    @DisplayName("徽标窝数 = 「窝里还有没断奶的仔猪」逐头判（D-0112），不是「本窝没有断奶记录」")
    void countUsesPerPigletSemantics() {
        String sql = selectSql("countPendingFarrows");
        assertThat(sql)
            .contains("FROM t_farm_pig_farrow f")
            .as("必须逐头判——按窝判会让断第一头就整窝消失")
            .contains("EXISTS (SELECT 1 FROM t_farm_pig_pigletno pl")
            .contains("pl.farrow_id = f.id")
            .contains("cub.current_status != 'END'");
        assertThat(sql)
            .as("零档案老窝这一支不能丢，否则 D-0110 的点开即补建永远触发不了")
            .contains("NOT EXISTS (SELECT 1 FROM t_farm_pig_pigletno pn")
            .contains("NOT EXISTS (SELECT 1 FROM t_farm_pig_weaning w");
    }

    @Test
    @DisplayName("徽标头数 = 未断奶头数；零档案老窝才退回 live_born（D-0112 + D-0110）")
    void sumUsesPerPigletSemantics() {
        String sql = selectSql("sumPendingPiglets");
        assertThat(sql)
            .as("已断奶那几头不能再计数——它们的出生重已经改不了了")
            .contains("(SELECT COUNT(*) FROM t_farm_pig_pigletno pl");
        assertThat(sql)
            .as("零档案老窝没有逐头行可数，只能退回窝级 live_born")
            .contains("CASE WHEN NOT EXISTS (SELECT 1 FROM t_farm_pig_pigletno pn")
            .contains("THEN COALESCE(f.live_born, 0)");
        assertThat(sql)
            .as("旧口径：所有未断奶窝一律按 live_born 全窝计数")
            .doesNotContain("SUM(COALESCE(f.live_born, 0))");
    }

    @Test
    @DisplayName("徽标两条与选窝列表共用同一串判据 —— 不许各自抄一份")
    void badgeAndListShareOnePredicate() {
        for (String name : new String[]{"countPendingFarrows", "sumPendingPiglets"}) {
            assertThat(selectSql(name)).as(name)
                .contains(PigFarrowMapper.PENDING_BIRTH_WEIGHT.replaceAll("\\s+", " "));
        }
    }

    @Test
    @DisplayName("文本块吃行尾空格 → 关键字被粘成 WHENNOT / ANDEXISTS，编译过但一调接口就炸")
    void hasNoGluedKeywords() {
        for (String name : new String[]{"countPendingFarrows", "sumPendingPiglets"}) {
            String sql = selectSql(name);
            for (String kw : new String[]{"WHENNOT", "ANDNOT", "ANDEXISTS", "ELSE(", "THENCOALESCE", "NOTEXISTS"}) {
                assertThat(sql).as(name + " 里粘住了 " + kw).doesNotContain(kw);
            }
        }
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
