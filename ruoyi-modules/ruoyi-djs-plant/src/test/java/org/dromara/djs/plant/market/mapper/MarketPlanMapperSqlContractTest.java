package org.dromara.djs.plant.market.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 果蔬上市计划聚合 SQL 契约（V6-R157）。
 *
 * <p>service 单测把 mapper 整个 mock 掉了，{@code MARKET_PLAN_SQL} 里的日期口径没有任何自动化覆盖——
 * 把那两行 COALESCE 改回去、或把 {@code %Y-%m-%d} 退回 {@code %Y-%m}，一条测试都不会红。
 * 这份契约测试专门守这几行文本。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@DisplayName("果蔬上市计划聚合 SQL 契约")
class MarketPlanMapperSqlContractTest {

    private static final String SQL = MarketPlanMapper.MARKET_PLAN_SQL;

    @Test
    @DisplayName("上市/下架日期：逐条明细「实际优先、计划兜底」后再 MIN/MAX")
    void effectiveDatesCoalesceInsideAggregate() {
        assertThat(SQL).contains("MIN(COALESCE(d.begin_harvestdate, d.earliest_harvestdate)) AS market_begin");
        assertThat(SQL).contains("MAX(COALESCE(d.end_harvestdate, d.last_harvestdate))       AS market_end");
        // COALESCE 必须在聚合函数「里面」：写成 COALESCE(MIN(...), MIN(...)) 就变成整条计划一起替换，
        // 「部分地块摘完、部分没开摘」时会算错
        assertThat(SQL).doesNotContain("COALESCE(MIN(");
        assertThat(SQL).doesNotContain("COALESCE(MAX(");
    }

    @Test
    @DisplayName("展示精确到天，不再截到月")
    void displayIsDayGranularity() {
        assertThat(SQL).contains("DATE_FORMAT(agg.market_begin, '%Y-%m-%d')        AS marketBeginDate");
        assertThat(SQL).contains("DATE_FORMAT(agg.market_end, '%Y-%m-%d')          AS marketEndDate");
        assertThat(SQL).doesNotContain("AS marketBeginMonth");
        assertThat(SQL).doesNotContain("AS marketEndMonth");
    }

    @Test
    @DisplayName("筛选仍按月：搜索框给的是月份选择器，比对时才截到月")
    void filtersStayMonthGranularity() {
        assertThat(SQL).contains("DATE_FORMAT(agg.market_begin, '%Y-%m') = #{q.marketBeginMonth}");
        assertThat(SQL).contains("DATE_FORMAT(agg.market_end, '%Y-%m') = #{q.marketEndMonth}");
    }

    @Test
    @DisplayName("没排采摘明细的计划仍在列表里，且被压到最后")
    void plansWithoutDetailsSurviveAndSortLast() {
        assertThat(SQL).contains("LEFT JOIN (");
        assertThat(SQL).contains("ORDER BY (agg.market_begin IS NULL) ASC, agg.market_begin DESC, p.id DESC");
    }
}
