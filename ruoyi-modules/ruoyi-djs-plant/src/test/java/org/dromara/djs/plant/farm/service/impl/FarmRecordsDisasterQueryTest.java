package org.dromara.djs.plant.farm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.farm.domain.FarmRecords;
import org.dromara.djs.plant.farm.domain.vo.FarmRecordsVo;
import org.dromara.djs.plant.farm.mapper.FarmRecordsMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * {@link FarmRecordsServiceImpl} 灾害记录 admin 查询单测（PLT-WORK-003）。
 *
 * <p>admin 灾害子页走 controller 强制 {@code farmType='disaster'} → service.queryById / queryPageList
 * （复用 PLT-WORK-001 查询路径，不新增 service 方法）。本测覆盖灾害记录详情 / 列表的 enrich 回填：</p>
 *
 * <ul>
 *   <li>queryById：灾害专属字段（disasterType / lossRate / lossYield / isWarning）原样返回 + plotName/teamName enrich</li>
 *   <li>queryPageList：灾害记录批量 enrich plotName + teamName（避免 N+1）</li>
 * </ul>
 *
 * @author djs
 * @since PLT-WORK-003
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FarmRecordsServiceImpl 灾害 admin 查询单测")
class FarmRecordsDisasterQueryTest {

    @Mock
    private FarmRecordsMapper baseMapper;
    @Mock
    private PlotInfoMapper plotInfoMapper;
    @Mock
    private CropInfoMapper cropInfoMapper;
    @Mock
    private PlantDetailsMapper plantDetailsMapper;
    @Mock
    private PlantWorkTeamMapper teamMapper;

    private FarmRecordsServiceImpl service;

    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, FarmRecords.class);
        TableInfoHelper.initTableInfo(assistant, PlantDetails.class);
        TableInfoHelper.initTableInfo(assistant, PlotInfo.class);
    }

    @BeforeEach
    void setUp() {
        service = new FarmRecordsServiceImpl(baseMapper, plotInfoMapper, cropInfoMapper, plantDetailsMapper, teamMapper);
    }

    private FarmRecordsVo buildDisasterVo() {
        FarmRecordsVo vo = new FarmRecordsVo();
        vo.setId(501L);
        vo.setRecordNo("FR202606130001");
        vo.setFarmType("disaster");
        vo.setPlotId(1L);
        vo.setCropId(2L);
        vo.setCropName("白菜");
        vo.setFarmBy(10L);
        vo.setFarmDate(LocalDate.of(2026, 6, 13));
        vo.setDisasterType("flood");
        vo.setLossRate(new BigDecimal("45.0"));
        vo.setLossYield(new BigDecimal("120.5"));
        vo.setIsWarning(1);
        vo.setProofOssIds("1001,1002");
        return vo;
    }

    private void stubEnrich() {
        PlotInfo plot = new PlotInfo();
        plot.setId(1L);
        plot.setPlotName("一号地块");
        plot.setPlotCode("PL001");
        when(plotInfoMapper.selectByIds(anyCollection())).thenReturn(List.of(plot));
        PlantWorkTeam team = new PlantWorkTeam();
        team.setId(10L);
        team.setTeamName("一组");
        when(teamMapper.selectByIds(anyCollection())).thenReturn(List.of(team));
    }

    @Test
    @DisplayName("queryById 灾害记录：专属字段原样返回 + plotName/teamName enrich")
    void queryById_disaster_enriched() {
        when(baseMapper.selectVoById(501L)).thenReturn(buildDisasterVo());
        stubEnrich();

        FarmRecordsVo vo = service.queryById(501L);

        assertThat(vo).isNotNull();
        assertThat(vo.getFarmType()).isEqualTo("disaster");
        assertThat(vo.getDisasterType()).isEqualTo("flood");
        assertThat(vo.getLossRate()).isEqualByComparingTo("45.0");
        assertThat(vo.getLossYield()).isEqualByComparingTo("120.5");
        assertThat(vo.getIsWarning()).isEqualTo(1);
        assertThat(vo.getProofOssIds()).isEqualTo("1001,1002");
        // enrich 回填
        assertThat(vo.getPlotName()).isEqualTo("一号地块");
        assertThat(vo.getPlotCode()).isEqualTo("PL001");
        assertThat(vo.getTeamName()).isEqualTo("一组");
    }

    @Test
    @DisplayName("queryById 不存在 id 返 null（不触发 enrich NPE）")
    void queryById_not_found_returns_null() {
        when(baseMapper.selectVoById(999L)).thenReturn(null);
        assertThat(service.queryById(999L)).isNull();
    }

    @Test
    @DisplayName("queryPageList 灾害列表：批量 enrich plotName + teamName")
    void queryPageList_disaster_batch_enriched() {
        FarmRecordsVo vo = buildDisasterVo();
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FarmRecordsVo> page =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        page.setRecords(List.of(vo));
        page.setTotal(1);
        when(baseMapper.selectVoPage(any(), any(Wrapper.class))).thenReturn(page);
        stubEnrich();

        org.dromara.djs.plant.farm.domain.query.FarmRecordsQuery query =
            new org.dromara.djs.plant.farm.domain.query.FarmRecordsQuery();
        query.setFarmType("disaster");
        org.dromara.common.mybatis.core.page.PageQuery pageQuery =
            new org.dromara.common.mybatis.core.page.PageQuery(1, 10);

        org.dromara.common.mybatis.core.page.TableDataInfo<FarmRecordsVo> result =
            service.queryPageList(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        FarmRecordsVo r = result.getRows().get(0);
        assertThat(r.getDisasterType()).isEqualTo("flood");
        assertThat(r.getPlotName()).isEqualTo("一号地块");
        assertThat(r.getTeamName()).isEqualTo("一组");
    }
}
