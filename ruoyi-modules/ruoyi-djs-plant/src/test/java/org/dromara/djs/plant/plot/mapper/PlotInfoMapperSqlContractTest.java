package org.dromara.djs.plant.plot.mapper;

import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.plot.domain.vo.PlotPlantingRecordVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("local")
@Tag("dev")
@DisplayName("地块种植详情 SQL 契约")
class PlotInfoMapperSqlContractTest {

    @Test
    @DisplayName("实际产量取总产量，实际亩产按总产量除以本条种植面积")
    void plantingDetailUsesProductionAndPerMuYield() throws Exception {
        Method method = PlotInfoMapper.class.getMethod("selectPlantingByPlot", Long.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join("\n", Arrays.asList(select.value()));

        assertThat(sql).contains("d.actual_yield        AS actualProduction");
        assertThat(sql).contains("ROUND(d.actual_yield / NULLIF(pl.plot_area, 0), 3) AS actualYield");
        assertThat(sql).contains("LEFT JOIN t_plant_plot_info pl");
        assertThat(sql).doesNotContain("AS earliestHarvestdate");
        assertThat(sql).doesNotContain("AS lastHarvestdate");

        PlotPlantingRecordVo vo = new PlotPlantingRecordVo();
        vo.setActualProduction(new BigDecimal("12.345"));
        vo.setActualYield(new BigDecimal("2.469"));
        assertThat(vo.getActualProduction()).isEqualByComparingTo("12.345");
        assertThat(vo.getActualYield()).isEqualByComparingTo("2.469");
    }
}
