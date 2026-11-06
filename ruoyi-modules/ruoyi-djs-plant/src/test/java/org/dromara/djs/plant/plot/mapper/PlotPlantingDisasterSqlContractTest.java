package org.dromara.djs.plant.plot.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-0154：地块种植记录预计产量只扣本茬期间的灾害损失——admin 地块详情与 mp 地块查询/详情三处 SQL 同一片段。
 *
 * <p>数值回归（2025 生菜预计 750 不被 2026 生菜灾害 100 扣减）走本地库真实接口验证，见本批验收记录。</p>
 */
@Tag("local")
@Tag("dev")
@DisplayName("地块种植记录预计产量扣灾害 SQL 契约（D-0154）")
class PlotPlantingDisasterSqlContractTest {

    private static String sql(Class<?> mapper, String name, Class<?>... types) throws Exception {
        Method m = mapper.getMethod(name, types);
        return String.join("\n", Arrays.asList(m.getAnnotation(Select.class).value()));
    }

    @Test
    @DisplayName("片段按本茬开工日（含）～同地块下一茬开工日（不含）归属灾害，且不出现 < 以兼容 <script>")
    void fragmentScopesDisasterToCurrentCrop() {
        String f = PlotPlantingSql.CURRENT_CROP_DISASTER_LOSS;
        assertThat(f).contains("fr.farm_type = 'disaster'");
        assertThat(f).contains("fr.plot_id = d.plot_id AND fr.crop_id = d.crop_id");
        assertThat(f).contains("fr.farm_date >= d.begin_actualdate");
        assertThat(f).contains("nx.begin_actualdate > d.begin_actualdate");
        assertThat(f).contains("fr.farm_date >= nx.begin_actualdate");
        // 同日同作物多条明细按 id 定序：只归 id 最大的那条，同一笔灾害不重复扣
        assertThat(f).containsPattern("nx\\.begin_actualdate = d\\.begin_actualdate\\s+AND nx\\.crop_id = d\\.crop_id AND nx\\.id > d\\.id");
        assertThat(f).doesNotContain("<");
        assertThat(f).doesNotContain("loss_yield =");
        assertThat(PlotPlantingSql.NET_EXPECTED_YIELD).startsWith("GREATEST(COALESCE(d.expected_yield, 0) - ");
    }

    @Test
    @DisplayName("admin selectPlantingByPlot 与 mp selectPlantings / selectCurrentCandidates 都用同一片段，不再按地块+作物全量 SUM")
    void allPlotPlantingQueriesUseSameFragment() throws Exception {
        String admin = sql(PlotInfoMapper.class, "selectPlantingByPlot", Long.class);
        String mpRecords = sql(AppletPlotQueryMapper.class, "selectPlantings", Long.class);
        String mpCurrent = sql(AppletPlotQueryMapper.class, "selectCurrentCandidates", Collection.class);
        for (String s : new String[]{admin, mpRecords, mpCurrent}) {
            assertThat(s).contains(PlotPlantingSql.NET_EXPECTED_YIELD);
            assertThat(s).doesNotContain("disaster_loss");
            assertThat(s).doesNotContain("GROUP BY fr.plot_id, fr.crop_id");
        }
    }
}
