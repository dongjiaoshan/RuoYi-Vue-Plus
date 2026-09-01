package org.dromara.djs.plant.market.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.market.domain.query.MarketPlanQuery;
import org.dromara.djs.plant.market.domain.vo.MarketPlanVo;
import org.dromara.djs.plant.market.mapper.MarketPlanMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyCollection;
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
}
