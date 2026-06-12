package org.dromara.djs.plant.pick.service.impl;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.pick.domain.query.PickActivityQuery;
import org.dromara.djs.plant.pick.domain.vo.PickActivityVo;
import org.dromara.djs.plant.pick.mapper.PickActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * {@link PickActivityServiceImpl} 单测（只读每日采摘聚合报表）。
 *
 * <h3>覆盖 case（happy path）</h3>
 * <ol>
 *   <li>queryList：透传 cropId / activityDate 到 mapper 聚合查询</li>
 *   <li>queryPageList：应用层分页切片 + total 正确</li>
 * </ol>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PickActivityServiceImpl 只读报表单元测试")
class PickActivityServiceImplTest {

    @Mock
    private PickActivityMapper activityMapper;

    private PickActivityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PickActivityServiceImpl(activityMapper);
    }

    private PickActivityVo row(Long cropId, LocalDate date, double today, double cumulative) {
        PickActivityVo vo = new PickActivityVo();
        vo.setCropId(cropId);
        vo.setActivityDate(date);
        vo.setCropName("南瓜");
        vo.setPlotCount(2);
        vo.setTodayPickWeight(new BigDecimal(String.valueOf(today)));
        vo.setExpectedYield(new BigDecimal("100"));
        vo.setCumulativePickWeight(new BigDecimal(String.valueOf(cumulative)));
        return vo;
    }

    @Test
    @DisplayName("queryList：透传 cropId/activityDate；返回 mapper 聚合结果")
    void queryList_passesFilters() {
        PickActivityQuery q = new PickActivityQuery();
        q.setCropId(701L);
        q.setActivityDate(LocalDate.of(2026, 6, 20));
        when(activityMapper.selectDailyReport(any(), eq(701L), eq(LocalDate.of(2026, 6, 20))))
            .thenReturn(List.of(row(701L, LocalDate.of(2026, 6, 20), 38.5, 120.0)));

        List<PickActivityVo> list = service.queryList(q);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTodayPickWeight()).isEqualByComparingTo("38.5");
        assertThat(list.get(0).getCumulativePickWeight()).isEqualByComparingTo("120.0");
    }

    @Test
    @DisplayName("queryList：null query 兜底（cropId/activityDate 传 null）")
    void queryList_nullQuery() {
        when(activityMapper.selectDailyReport(any(), isNull(), isNull())).thenReturn(List.of());
        assertThat(service.queryList(null)).isEmpty();
    }

    @Test
    @DisplayName("queryPageList：应用层分页切片 + total")
    void queryPageList_slices() {
        when(activityMapper.selectDailyReport(any(), isNull(), isNull())).thenReturn(List.of(
            row(701L, LocalDate.of(2026, 6, 22), 10, 10),
            row(701L, LocalDate.of(2026, 6, 21), 20, 30),
            row(702L, LocalDate.of(2026, 6, 20), 30, 30)
        ));

        PageQuery pq = new PageQuery(2, 1);
        TableDataInfo<PickActivityVo> page = service.queryPageList(new PickActivityQuery(), pq);

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getRows()).hasSize(2);

        pq.setPageNum(2);
        TableDataInfo<PickActivityVo> page2 = service.queryPageList(new PickActivityQuery(), pq);
        assertThat(page2.getRows()).hasSize(1);
    }
}
