package org.dromara.djs.breed.event.weaning.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PigWeaningMapper#ALREADY_WEANED} 的口径契约（D-0113 寄养）。
 *
 * <p><b>为什么单独一支</b>：这一串是纯常量拼接的注解 SQL，service 侧全是 mock，把「绑窝」那个条件
 * 加回去整仓单测照样全绿（独立验收实测：加回 {@code AND w.farrow_id = pl.farrow_id} → 488 全绿）。
 * 而它一旦被加回去，寄养走的仔猪在生母窝里就永远显示「还没断奶」、可以被反复断，
 * 幽灵头数吃满 {@code live_born} 把整窝永久卡死。这种「改回去零成本」的口径必须按源码钉死。</p>
 *
 * <p>这一串被四处共用：断奶仔猪选择页的待断奶列表、提交去重守卫、出生重订正页选窝列表 + mp 首页徽标
 * （经 {@code PigFarrowMapper.PENDING_BIRTH_WEIGHT}）、断奶录入页铺逐头行。</p>
 *
 * @author djs
 */
@Tag("dev")
class PigWeaningMapperSqlContractTest {

    private static String flat(String sql) {
        return sql.replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("D-0113：判据不许绑窝 —— 寄养的仔猪跟着养母断，明细挂在养母那一窝，按本窝找必然漏判")
    void alreadyWeanedIsNotScopedToOwnFarrow() {
        assertThat(flat(PigWeaningMapper.ALREADY_WEANED))
            .as("加回这个条件 = 寄养的仔猪在生母窝里永远显示没断奶，可以被反复断")
            .doesNotContain("w.farrow_id = pl.farrow_id")
            .doesNotContain("w.farrow_id=pl.farrow_id");
    }

    @Test
    @DisplayName("三个信号缺一不可：逐头明细 / 猪只断奶日 / 已不是仔猪")
    void alreadyWeanedKeepsAllThreeSignals() {
        String sql = flat(PigWeaningMapper.ALREADY_WEANED);
        assertThat(sql)
            .as("信号①逐头断奶明细 —— 行238 逐头断奶留下的痕迹")
            .contains("FROM t_farm_pig_weaning_detail wd")
            .contains("wd.ear_no = pl.piglet_ear_no");
        assertThat(sql)
            .as("信号②个体断奶快照 —— 寄养头靠它兜底（明细挂在别窝）")
            .contains("cub.wean_date IS NOT NULL");
        assertThat(sql)
            .as("信号③已不是仔猪 —— 没有逐头明细的历史断奶记录只翻了 pig_type，前两个信号抓不到")
            .contains("cub.pig_type != 'piglet'");
    }

    @Test
    @DisplayName("明细与主记录都要走逻辑删过滤，软删的断奶记录不能继续把仔猪判成已断")
    void alreadyWeanedRespectsSoftDelete() {
        String sql = flat(PigWeaningMapper.ALREADY_WEANED);
        assertThat(sql).contains("wd.del_flag = '0'").contains("w.del_flag = '0'");
    }

    @Test
    @DisplayName("不等于一律写 != ：<> 在 <script> 里会被当 XML 标签，启动期崩容器（2026-09-20 踩过）")
    void neverUsesAngleBracketNotEquals() {
        assertThat(PigWeaningMapper.ALREADY_WEANED).doesNotContain("<>");
        assertThat(PigWeaningMapper.UNWEANED_PIGLET_BODY).doesNotContain("<>");
    }

    @Test
    @DisplayName("文本块吃行尾空格 → 关键字被粘成 ANDNOT / NOTEXISTS，编译过但一调接口就炸")
    void hasNoGluedKeywords() {
        for (String s : new String[]{
            flat(PigWeaningMapper.ALREADY_WEANED),
            flat(PigWeaningMapper.UNWEANED_PIGLET_EXISTS),
            flat(PigWeaningMapper.UNWEANED_PIGLET_COUNT)}) {
            for (String kw : new String[]{"ANDNOT", "ANDEXISTS", "NOTEXISTS", "ORNOT", "ANDcub.", "ANDpl."}) {
                assertThat(s).as("粘住了 " + kw).doesNotContain(kw);
            }
        }
    }

    @Test
    @DisplayName("未断奶仔猪那一串按别名 f 锚定分娩记录、按 pl 锚定逐头行 —— 别名契约变了四处调用方全崩")
    void unweanedPredicateKeepsAliasContract() {
        String sql = flat(PigWeaningMapper.UNWEANED_PIGLET_BODY);
        assertThat(sql)
            .contains("FROM t_farm_pig_pigletno pl")
            .contains("pl.farrow_id = f.id")
            .contains("pl.tenant_id = f.tenant_id")
            .contains("cub.current_status != 'END'");
    }
}
