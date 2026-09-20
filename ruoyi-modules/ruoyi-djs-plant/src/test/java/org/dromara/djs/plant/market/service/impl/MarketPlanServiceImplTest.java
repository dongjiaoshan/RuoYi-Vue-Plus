package org.dromara.djs.plant.market.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.common.domain.vo.DateWindowStatusStatVo;
import org.dromara.djs.plant.market.domain.query.MarketPlanQuery;
import org.dromara.djs.plant.market.domain.vo.MarketPlanVo;
import org.dromara.djs.plant.market.mapper.MarketPlanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MarketPlanServiceImpl} 单测（V6-R151 建，V6-R157/R158 补日期与状态）。
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MarketPlanServiceImpl 单元测试")
class MarketPlanServiceImplTest {

    @Mock
    private MarketPlanMapper marketPlanMapper;

    @Mock
    private ImageUrlResolver imageUrlResolver;

    private MarketPlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketPlanServiceImpl(marketPlanMapper, imageUrlResolver);
    }

    @Test
    @DisplayName("happy path：聚合行透传 + 作物图 ossId 批量解析成 URL + 现算上市状态")
    void queryPageListResolvesCropImageUrl() {
        // 用相对今天的偏移造数据，断言不会随日历日漂移
        LocalDate today = LocalDate.now();
        MarketPlanVo row = new MarketPlanVo();
        row.setPlanId(9316000000001010L);
        row.setPlanNo("PLAN-2026-001");
        row.setPlanYear(2026);
        row.setCropId(9306000000000084L);
        row.setCropName("糯玉米");
        row.setCropImage("9318000000000001");
        row.setExpectedYield(new BigDecimal("1600.000"));
        row.setActualYield(new BigDecimal("320.500"));
        row.setMarketBeginDate(today.minusDays(3).toString());
        row.setMarketEndDate(today.plusDays(40).toString());

        List<MarketPlanVo> records = new ArrayList<>();
        records.add(row);
        Page<MarketPlanVo> page = new Page<>(1, 10, 1);
        page.setRecords(records);
        when(marketPlanMapper.selectMarketPlanPage(any(), anyString(), any()))
            .thenReturn(page);
        when(imageUrlResolver.batchUrl(anyCollection()))
            .thenReturn(Map.of("9318000000000001", "http://oss.example.com/corn.jpg"));

        TableDataInfo<MarketPlanVo> result = service.queryPageList(new MarketPlanQuery(), new PageQuery(10, 1));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        MarketPlanVo vo = result.getRows().get(0);
        assertThat(vo.getCropName()).isEqualTo("糯玉米");
        assertThat(vo.getCropImageUrl()).isEqualTo("http://oss.example.com/corn.jpg");
        assertThat(vo.getMarketBeginDate()).isEqualTo(today.minusDays(3).toString());
        assertThat(vo.getMarketEndDate()).isEqualTo(today.plusDays(40).toString());
        // 已过上市日期 + 离下架还有 40 天 → 上市中
        assertThat(vo.getMarketStatus()).isEqualTo("on_sale");
        assertThat(vo.getMarketStatusName()).isEqualTo("上市中");
        assertThat(vo.getExpectedYield()).isEqualByComparingTo("1600.000");
        assertThat(vo.getActualYield()).isEqualByComparingTo("320.500");
    }

    @Test
    @DisplayName("没有采摘明细的计划：上市/下架日期为空、无图、状态留空，该行仍保留在结果里")
    void queryPageListKeepsRowWithoutHarvestDetails() {
        MarketPlanVo row = new MarketPlanVo();
        row.setPlanId(1L);
        row.setCropName("空计划作物");
        row.setCropImage(null);
        row.setExpectedYield(BigDecimal.ZERO);
        row.setActualYield(BigDecimal.ZERO);
        row.setMarketBeginDate(null);
        row.setMarketEndDate(null);

        List<MarketPlanVo> records = new ArrayList<>();
        records.add(row);
        Page<MarketPlanVo> page = new Page<>(1, 10, 1);
        page.setRecords(records);
        when(marketPlanMapper.selectMarketPlanPage(any(), anyString(), any()))
            .thenReturn(page);

        TableDataInfo<MarketPlanVo> result = service.queryPageList(null, null);

        assertThat(result.getRows()).hasSize(1);
        MarketPlanVo vo = result.getRows().get(0);
        assertThat(vo.getCropImageUrl()).isNull();
        assertThat(vo.getMarketBeginDate()).isNull();
        assertThat(vo.getMarketEndDate()).isNull();
        assertThat(vo.getMarketStatus()).isNull();
        assertThat(vo.getMarketStatusName()).isNull();
    }

    @Test
    @DisplayName("导出行同样带状态：已过下架日期 → 已下架")
    void queryListFillsStatus() {
        LocalDate today = LocalDate.now();
        MarketPlanVo row = new MarketPlanVo();
        row.setPlanId(3L);
        row.setCropName("过季作物");
        row.setMarketBeginDate(today.minusDays(90).toString());
        row.setMarketEndDate(today.minusDays(1).toString());
        List<MarketPlanVo> rows = new ArrayList<>();
        rows.add(row);
        when(marketPlanMapper.selectMarketPlanList(anyString(), any())).thenReturn(rows);

        List<MarketPlanVo> list = service.queryList(new MarketPlanQuery());

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getMarketStatus()).isEqualTo("off_shelf");
        assertThat(list.get(0).getMarketStatusName()).isEqualTo("已下架");
    }

    @Test
    @DisplayName("导出全量：mapper 返 null → 返回空 list，不抛")
    void queryListHandlesNull() {
        when(marketPlanMapper.selectMarketPlanList(anyString(), any())).thenReturn(null);
        assertThat(service.queryList(new MarketPlanQuery())).isEmpty();

        MarketPlanVo row = new MarketPlanVo();
        row.setPlanId(2L);
        row.setCropImage("9318000000000002");
        List<MarketPlanVo> rows = new ArrayList<>();
        rows.add(row);
        when(marketPlanMapper.selectMarketPlanList(anyString(), any())).thenReturn(rows);
        when(imageUrlResolver.batchUrl(anyCollection()))
            .thenReturn(Map.of("9318000000000002", "http://oss.example.com/x.jpg"));

        List<MarketPlanVo> list = service.queryList(new MarketPlanQuery());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getCropImageUrl()).isEqualTo("http://oss.example.com/x.jpg");
    }

    @Test
    @DisplayName("状态筛选跨页生效：走全量 SQL 后按状态过滤再内存切页，total 是过滤后的总数而非当前页")
    void queryPageListFiltersByStatusAcrossPages() {
        LocalDate today = LocalDate.now();
        List<MarketPlanVo> all = new ArrayList<>();
        // 12 行「上市中」+ 3 行「已下架」：一页 10 条，第 2 页必须还能看到剩下的 2 行上市中
        for (int i = 0; i < 12; i++) {
            all.add(rowWithWindow(100L + i, today.minusDays(3), today.plusDays(40)));
        }
        for (int i = 0; i < 3; i++) {
            all.add(rowWithWindow(200L + i, today.minusDays(90), today.minusDays(1)));
        }
        when(marketPlanMapper.selectMarketPlanList(anyString(), any())).thenReturn(all);

        MarketPlanQuery query = new MarketPlanQuery();
        query.setMarketStatus("on_sale");

        TableDataInfo<MarketPlanVo> page1 = service.queryPageList(query, new PageQuery(10, 1));
        assertThat(page1.getTotal()).isEqualTo(12);
        assertThat(page1.getRows()).hasSize(10);
        assertThat(page1.getRows()).allMatch(vo -> "on_sale".equals(vo.getMarketStatus()));

        TableDataInfo<MarketPlanVo> page2 = service.queryPageList(query, new PageQuery(10, 2));
        assertThat(page2.getTotal()).isEqualTo(12);
        assertThat(page2.getRows()).hasSize(2);
        assertThat(page2.getRows()).allMatch(vo -> "on_sale".equals(vo.getMarketStatus()));

        // 越界页返回空行但 total 不变（前端切到不存在的页时不炸）
        TableDataInfo<MarketPlanVo> page9 = service.queryPageList(query, new PageQuery(10, 9));
        assertThat(page9.getTotal()).isEqualTo(12);
        assertThat(page9.getRows()).isEmpty();
    }

    @Test
    @DisplayName("状态筛选同样作用于导出：只导出选中那一档")
    void queryListFiltersByStatus() {
        LocalDate today = LocalDate.now();
        List<MarketPlanVo> all = new ArrayList<>();
        all.add(rowWithWindow(1L, today.minusDays(3), today.plusDays(40)));
        all.add(rowWithWindow(2L, today.minusDays(90), today.minusDays(1)));
        when(marketPlanMapper.selectMarketPlanList(anyString(), any())).thenReturn(all);

        MarketPlanQuery query = new MarketPlanQuery();
        query.setMarketStatus("off_shelf");

        List<MarketPlanVo> list = service.queryList(query);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getPlanId()).isEqualTo(2L);
        assertThat(list.get(0).getMarketStatusName()).isEqualTo("已下架");
    }

    @Test
    @DisplayName("统计版块：五档全量计数；状态条件本身被忽略，状态为空的行不计入任何一档")
    void statusStatCountsAllFiveBuckets() {
        LocalDate today = LocalDate.now();
        List<MarketPlanVo> all = new ArrayList<>();
        all.add(rowWithWindow(1L, today.plusDays(60), today.plusDays(200)));   // pending
        all.add(rowWithWindow(2L, today.plusDays(10), today.plusDays(200)));   // upcoming
        all.add(rowWithWindow(3L, today.plusDays(20), today.plusDays(200)));   // upcoming
        all.add(rowWithWindow(4L, today.minusDays(3), today.plusDays(40)));    // on_sale
        all.add(rowWithWindow(5L, today.minusDays(3), today.plusDays(5)));     // ending
        all.add(rowWithWindow(6L, today.minusDays(90), today.minusDays(1)));   // off_shelf
        all.add(rowWithWindow(7L, null, null));                                // 没排明细 → 不计入
        when(marketPlanMapper.selectMarketPlanList(anyString(), any())).thenReturn(all);

        MarketPlanQuery query = new MarketPlanQuery();
        query.setMarketStatus("on_sale");

        DateWindowStatusStatVo stat = service.statusStat(query);

        assertThat(stat.getPending()).isEqualTo(1);
        assertThat(stat.getUpcoming()).isEqualTo(2);
        assertThat(stat.getOnSale()).isEqualTo(1);
        assertThat(stat.getEnding()).isEqualTo(1);
        assertThat(stat.getOffShelf()).isEqualTo(1);

        // 统计口径：状态条件不下推，其余条件下推 —— 用 captor 确认传给 mapper 的 query 已剥掉状态
        ArgumentCaptor<MarketPlanQuery> captor = ArgumentCaptor.forClass(MarketPlanQuery.class);
        verify(marketPlanMapper).selectMarketPlanList(anyString(), captor.capture());
        assertThat(captor.getValue().getMarketStatus()).isNull();
    }

    @Test
    @DisplayName("统计版块：一行都没有 → 五档全 0，不返 null")
    void statusStatOnEmptyResultGivesZeros() {
        when(marketPlanMapper.selectMarketPlanList(anyString(), any())).thenReturn(null);

        DateWindowStatusStatVo stat = service.statusStat(null);

        assertThat(stat.getPending()).isZero();
        assertThat(stat.getUpcoming()).isZero();
        assertThat(stat.getOnSale()).isZero();
        assertThat(stat.getEnding()).isZero();
        assertThat(stat.getOffShelf()).isZero();
    }

    /** 造一行只关心上市 / 下架日期窗口的聚合行（状态由 service 现算）。 */
    private MarketPlanVo rowWithWindow(Long planId, LocalDate begin, LocalDate end) {
        MarketPlanVo row = new MarketPlanVo();
        row.setPlanId(planId);
        row.setCropName("作物" + planId);
        row.setMarketBeginDate(begin == null ? null : begin.toString());
        row.setMarketEndDate(end == null ? null : end.toString());
        return row;
    }
}
