package org.dromara.djs.warehouse.veg.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.veg.domain.FeedDailyConfirm;
import org.dromara.djs.warehouse.veg.domain.bo.FeedDailyConfirmBo;
import org.dromara.djs.warehouse.veg.domain.bo.FeedRecordQuery;
import org.dromara.djs.warehouse.veg.domain.vo.FeedDailyVo;
import org.dromara.djs.warehouse.veg.mapper.FeedDailyConfirmMapper;
import org.dromara.djs.warehouse.veg.mapper.FeedLogMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 有机饲喂记录按饲喂动物分开（V7 行17 / 行18）。
 *
 * <p>① 框数确认按 (日期, 饲喂动物) 各一条；不传饲喂动物 = 猪（mp 只管猪只饲料，老 mp 包也不带这个字段）。
 * ② 日汇总 SQL 按 (日期, feed_animal) 分组、按同一对键左联确认表，传 feedAnimal 只出该类型（mp 固定 pig）。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@DisplayName("FeedRecordServiceImpl 猪只饲料 / 家禽饲料分开统计与确认")
class FeedRecordServiceImplTest {

    @Mock
    private FeedLogMapper feedLogMapper;

    @Mock
    private FeedDailyConfirmMapper confirmMapper;

    @InjectMocks
    private FeedRecordServiceImpl service;

    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, FeedDailyConfirm.class);
    }

    private static FeedDailyConfirmBo confirmBo(String animal) {
        FeedDailyConfirmBo bo = new FeedDailyConfirmBo();
        bo.setFeedDate(LocalDate.of(2026, 10, 26));
        bo.setFeedAnimal(animal);
        bo.setBoxCount(new BigDecimal("3.5"));
        bo.setConfirmUserId(9L);
        return bo;
    }

    @Test
    @DisplayName("家禽饲料框数单独落一条 feed_animal=poultry 的确认行")
    void confirmPoultryInsertsPoultryRow() {
        service.saveDailyConfirm(confirmBo("poultry"));

        ArgumentCaptor<FeedDailyConfirm> row = ArgumentCaptor.forClass(FeedDailyConfirm.class);
        verify(confirmMapper).insert(row.capture());
        assertThat(row.getValue().getFeedAnimal()).isEqualTo("poultry");
        assertThat(row.getValue().getFeedDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(row.getValue().getBoxCount()).isEqualByComparingTo("3.5");
        assertThat(row.getValue().getDelUnique()).isZero();
    }

    @Test
    @DisplayName("不传饲喂动物 = 猪只饲料（mp 只录猪，老 mp 包不带字段）")
    void confirmWithoutAnimalDefaultsToPig() {
        service.saveDailyConfirm(confirmBo(null));

        ArgumentCaptor<FeedDailyConfirm> row = ArgumentCaptor.forClass(FeedDailyConfirm.class);
        verify(confirmMapper).insert(row.capture());
        assertThat(row.getValue().getFeedAnimal()).isEqualTo("pig");
    }

    @Test
    @DisplayName("同日同类型已确认 → 只更新那一条，不新建")
    void confirmExistingSameAnimalUpdates() {
        FeedDailyConfirm exist = new FeedDailyConfirm();
        exist.setId(1L);
        exist.setFeedDate(LocalDate.of(2026, 10, 26));
        exist.setFeedAnimal("poultry");
        when(confirmMapper.selectOne(any())).thenReturn(exist);

        service.saveDailyConfirm(confirmBo("poultry"));

        verify(confirmMapper, never()).insert(any(FeedDailyConfirm.class));
        verify(confirmMapper).updateById(exist);
        assertThat(exist.getBoxCount()).isEqualByComparingTo("3.5");
        assertThat(exist.getConfirmUserId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("日汇总把 feedAnimal 原样交给 SQL（mp 传 pig 只看猪只饲料）")
    void dailyPagePassesAnimalFilter() {
        FeedDailyVo pig = new FeedDailyVo();
        pig.setFeedAnimal("pig");
        Page<FeedDailyVo> page = new Page<>(1, 10);
        page.setRecords(List.of(pig));
        page.setTotal(1);
        FeedRecordQuery query = new FeedRecordQuery();
        query.setFeedAnimal("pig");
        try (MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("1001");
            when(feedLogMapper.selectDailyPage(any(), eq("1001"), eq(query))).thenReturn(page);

            List<FeedDailyVo> rows = service.queryDailyPage(query, new PageQuery(10, 1)).getRows();

            assertThat(rows).extracting(FeedDailyVo::getFeedAnimal).containsExactly("pig");
        }
    }

    private static String script(String name, Class<?>... types) throws Exception {
        Method method = FeedLogMapper.class.getMethod(name, types);
        return String.join(" ", method.getAnnotation(Select.class).value()).trim();
    }

    private static String render(String script, FeedRecordQuery query) {
        SqlSource src = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Object.class);
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", "1001");
        params.put("query", query);
        BoundSql bound = src.getBoundSql(params);
        return bound.getSql().replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("日汇总 SQL：按 (日期, feed_animal) 分组并按同一对键联确认表；传 pig 才加过滤")
    void dailySqlSplitsByAnimal() throws Exception {
        String script = script("selectDailyPage", IPage.class, String.class, FeedRecordQuery.class);

        String all = render(script, new FeedRecordQuery());
        assertThat(all).contains("GROUP BY DATE(fl.feed_date), fl.feed_animal");
        assertThat(all).contains("fc.feed_animal = d.feedAnimal");
        assertThat(all).doesNotContain("AND fl.feed_animal = ?");

        FeedRecordQuery pigOnly = new FeedRecordQuery();
        pigOnly.setFeedAnimal("pig");
        assertThat(render(script, pigOnly)).contains("AND fl.feed_animal = ?");
    }

    @Test
    @DisplayName("明细列表 / 导出 SQL：带出 feed_animal，传类型时按类型过滤（查看详情按行类型拉明细）")
    void recordSqlCarriesAnimal() throws Exception {
        FeedRecordQuery poultry = new FeedRecordQuery();
        poultry.setFeedAnimal("poultry");
        String page = render(script("selectRecordPage", IPage.class, String.class, FeedRecordQuery.class), poultry);
        String list = render(script("selectRecordList", String.class, FeedRecordQuery.class), poultry);
        for (String sql : List.of(page, list)) {
            assertThat(sql).contains("fl.feed_animal AS feedAnimal");
            assertThat(sql).contains("AND fl.feed_animal = ?");
        }
    }
}
