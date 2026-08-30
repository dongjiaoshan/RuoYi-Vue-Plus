package org.dromara.djs.warehouse.cross.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「今日白条出库耳号」候选口径（V6 row132 / row144）。
 *
 * <p>甲方原话：<em>「可选择项为当天出白条库的白条耳号数据，耳号重复时，去重显示即可」</em>。
 * 这两条语义（当天 + 去重）整个链路上只有 {@link BarInfoMapper#selectTodayOutEarNos()} 这一句
 * 注解 SQL 承担；service 层只是原样透传（那条单测把 mapper mock 掉了，SQL 改坏不会有任何测试变红）。
 * 所以这里直接把 SQL 的关键子句钉住 —— 有人顺手改成 {@code DISTINCT} 之外的写法、
 * 或把 {@code out_time} 换成 {@code in_time}、或去掉上界，都会在这里当场红。</p>
 *
 * <p>钉子句而不是打真库，是因为本模块单测全是无容器的纯单元测试；真库行为已在本地
 * 用 10 行边界夹具（重复耳号 / 前天出库 / 空耳号 / 软删 / 明天 / 只入库未出库）实测过一轮。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@DisplayName("今日白条出库耳号候选：当天 + 去重（V6 row132/row144）")
class BarInfoTodayEarNoSqlTest {

    private static String sql() throws NoSuchMethodException {
        Select select = BarInfoMapper.class.getMethod("selectTodayOutEarNos").getAnnotation(Select.class);
        assertThat(select).as("selectTodayOutEarNos 必须是注解 SQL").isNotNull();
        return select.value()[0].replaceAll("\\s+", " ").trim();
    }

    @Test
    @DisplayName("去重：按 ear_no 收敛，同一头猪出库两次只出现一次")
    void deduplicatesByEarNo() throws Exception {
        assertThat(sql()).containsIgnoringCase("GROUP BY ear_no");
    }

    @Test
    @DisplayName("只取当天：下界 CURDATE()、上界次日零点，且比的是出库时间不是入库时间")
    void limitsToToday() throws Exception {
        String s = sql();
        assertThat(s).containsIgnoringCase("out_time >= CURDATE()");
        assertThat(s).containsIgnoringCase("out_time < CURDATE() + INTERVAL 1 DAY");
        assertThat(s).doesNotContainIgnoringCase("in_time");
    }

    @Test
    @DisplayName("空耳号与软删行不进候选")
    void excludesBlankAndDeleted() throws Exception {
        String s = sql();
        assertThat(s).containsIgnoringCase("ear_no IS NOT NULL");
        assertThat(s).containsIgnoringCase("ear_no <> ''");
        assertThat(s).containsIgnoringCase("del_flag = '0'");
    }

    @Test
    @DisplayName("最近出库的排前面 —— 刚出库那头猪就是用户要选的那头")
    void latestOutFirst() throws Exception {
        assertThat(sql()).containsIgnoringCase("ORDER BY MAX(out_time) DESC");
    }
}
