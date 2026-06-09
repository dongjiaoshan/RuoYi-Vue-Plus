package org.dromara.djs.plant.farm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.farm.domain.FarmRecords;
import org.dromara.djs.plant.farm.domain.bo.DisasterBatchBo;
import org.dromara.djs.plant.farm.domain.bo.DisasterRecordBo;
import org.dromara.djs.plant.farm.domain.bo.EmptyRecordBo;
import org.dromara.djs.plant.farm.domain.bo.GrowBatchBo;
import org.dromara.djs.plant.farm.domain.bo.HarvestWeightBo;
import org.dromara.djs.plant.farm.domain.bo.RotationRecordBo;
import org.dromara.djs.plant.farm.domain.bo.TransplantRecordBo;
import org.dromara.djs.plant.farm.domain.vo.DispatchSummaryVo;
import org.dromara.djs.plant.farm.mapper.FarmRecordsMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FarmRecordsServiceImpl} 单测（PLT-WORK-001）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>happy path：空地翻耕（无特殊字段）INSERT</li>
 *   <li>整地强校验：tillageType / tillageMethod 缺失抛 ServiceException</li>
 *   <li>灾害副作用：plant_details.loss_yield 累加（mapper.update 被 invoke）</li>
 *   <li>退茬副作用：plot_info.plot_status=1 + plant_details completed</li>
 *   <li>移栽校验：transplantPercent > 60 抛 ServiceException（前端 @Max 兜底服务端）</li>
 *   <li>中央分发台：12 类 key 全在 + count 正确</li>
 * </ul>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FarmRecordsServiceImpl 单元测试")
class FarmRecordsServiceImplTest {

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

    /**
     * MyBatis-Plus 单测 entity cache 预热：service 内 LambdaQueryWrapper / LambdaUpdateWrapper
     * 在 mock 路径下也会触发 TableInfoHelper.getTableInfo() 解析 lambda 列名，必须先注册 entity。
     */
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
        // mocking selectMaxRecordNoByPrefix 返 null（当日尚无序号）→ next=1
        when(baseMapper.selectMaxRecordNoByPrefix(any(), any())).thenReturn(null);
        // mocking plot / crop 落库快照（plot_type / crop_name 冗余）
        PlotInfo plot = new PlotInfo();
        plot.setId(1L);
        plot.setPlotStatus(1);
        plot.setPlotName("一号地块");
        plot.setPlotCode("PL001");
        when(plotInfoMapper.selectById(1L)).thenReturn(plot);
        CropInfo crop = new CropInfo();
        crop.setId(2L);
        crop.setCropName("白菜");
        when(cropInfoMapper.selectById(2L)).thenReturn(crop);
    }

    @Test
    @DisplayName("空地翻耕 happy: INSERT farm_records 1 行，无特殊字段")
    void submitEmpty_break_happy() {
        EmptyRecordBo bo = new EmptyRecordBo();
        bo.setFarmType("tillage_break");
        bo.setPlotId(1L);
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());
        bo.setRemark("test break");

        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            FarmRecords r = inv.getArgument(0);
            r.setId(99L);
            return 1;
        });

        Long id = service.submitEmpty(bo);
        assertThat(id).isEqualTo(99L);

        ArgumentCaptor<FarmRecords> cap = ArgumentCaptor.forClass(FarmRecords.class);
        verify(baseMapper).insert(cap.capture());
        FarmRecords saved = cap.getValue();
        assertThat(saved.getFarmType()).isEqualTo("tillage_break");
        assertThat(saved.getRecordNo()).startsWith("FR").hasSize(14);   // FR + 8 + 4
        assertThat(saved.getPlotType()).isEqualTo(1);                   // 冗余写入
        assertThat(saved.getTillageType()).isNull();
        assertThat(saved.getTillageMethod()).isNull();
        assertThat(saved.getIsWarning()).isEqualTo(2);
    }

    @Test
    @DisplayName("整地子类型缺 tillage_type 抛 ServiceException")
    void submitEmpty_tillage_prepare_missing_fields() {
        EmptyRecordBo bo = new EmptyRecordBo();
        bo.setFarmType("tillage_prepare");
        bo.setPlotId(1L);
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());
        // 未设 tillageType

        assertThatThrownBy(() -> service.submitEmpty(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("整地类型");
        verify(baseMapper, never()).insert(any(FarmRecords.class));
    }

    @Test
    @DisplayName("非法 farmType 提交空地接口抛错")
    void submitEmpty_invalid_type() {
        EmptyRecordBo bo = new EmptyRecordBo();
        bo.setFarmType("weed");   // 生长阶段不属于空地路径
        bo.setPlotId(1L);
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());

        assertThatThrownBy(() -> service.submitEmpty(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("空地农事");
    }

    @Test
    @DisplayName("灾害提交触发 plant_details.loss_yield 累加 + isWarning=1 当 lossRate≥30")
    void submitDisaster_accumulate_loss_yield_and_warning() {
        DisasterRecordBo bo = new DisasterRecordBo();
        bo.setPlantId(7L);
        bo.setPlotId(1L);
        bo.setCropId(2L);
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());
        bo.setDisasterType("flood");
        bo.setLossRate(new BigDecimal("45.0"));
        bo.setLossYield(new BigDecimal("120.5"));

        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            FarmRecords r = inv.getArgument(0);
            r.setId(101L);
            return 1;
        });

        PlantDetails details = new PlantDetails();
        details.setId(55L);
        details.setLossYield(new BigDecimal("10.0"));
        when(plantDetailsMapper.selectOne(any())).thenReturn(details);
        when(plantDetailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        Long id = service.submitDisaster(bo);
        assertThat(id).isEqualTo(101L);

        ArgumentCaptor<FarmRecords> cap = ArgumentCaptor.forClass(FarmRecords.class);
        verify(baseMapper).insert(cap.capture());
        FarmRecords saved = cap.getValue();
        assertThat(saved.getFarmType()).isEqualTo("disaster");
        assertThat(saved.getDisasterType()).isEqualTo("flood");
        assertThat(saved.getIsWarning()).isEqualTo(1);   // 45 ≥ 30

        verify(plantDetailsMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("退茬触发 plot_info.plot_status=1 + plant_details completed")
    void submitRotation_resets_plot_and_completes_details() {
        RotationRecordBo bo = new RotationRecordBo();
        bo.setPlantId(7L);
        bo.setPlotId(1L);
        bo.setCropId(2L);
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());

        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            FarmRecords r = inv.getArgument(0);
            r.setId(202L);
            return 1;
        });
        when(plotInfoMapper.updateById(any(PlotInfo.class))).thenReturn(1);
        when(plantDetailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        Long id = service.submitRotation(bo);
        assertThat(id).isEqualTo(202L);

        ArgumentCaptor<PlotInfo> plotCap = ArgumentCaptor.forClass(PlotInfo.class);
        verify(plotInfoMapper).updateById(plotCap.capture());
        assertThat(plotCap.getValue().getPlotStatus()).isEqualTo(1);

        verify(plantDetailsMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("移栽 transplantPercent > 60 抛 ServiceException 兜底前端 @Max")
    void submitTransplant_percent_exceeds_60() {
        TransplantRecordBo bo = new TransplantRecordBo();
        bo.setPlantId(7L);
        bo.setPlotId(1L);
        bo.setCropId(2L);
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());
        bo.setTransplantPlot(3L);
        bo.setTransplantPercent(65);

        assertThatThrownBy(() -> service.submitTransplant(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("60%");
        verify(baseMapper, never()).insert(any(FarmRecords.class));
    }

    @Test
    @DisplayName("submitGrowBatch happy: water_fertilize 2 地块 → INSERT 2 行，返回 2，无退茬副作用")
    void submitGrowBatch_happy() {
        GrowBatchBo bo = new GrowBatchBo();
        bo.setFarmType("water_fertilize");
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());
        bo.setRemark("批量水肥");
        GrowBatchBo.PlotTarget t1 = new GrowBatchBo.PlotTarget();
        t1.setPlantId(7L);
        t1.setPlotId(1L);
        t1.setCropId(2L);
        GrowBatchBo.PlotTarget t2 = new GrowBatchBo.PlotTarget();
        t2.setPlantId(7L);
        t2.setPlotId(1L);
        t2.setCropId(2L);
        bo.setTargets(List.of(t1, t2));

        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            ((FarmRecords) inv.getArgument(0)).setId(300L);
            return 1;
        });

        int count = service.submitGrowBatch(bo);
        assertThat(count).isEqualTo(2);

        verify(baseMapper, times(2)).insert(any(FarmRecords.class));
        // 非退茬：不触发地块状态回归 / 明细完结
        verify(plotInfoMapper, never()).updateById(any(PlotInfo.class));
        verify(plantDetailsMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("submitGrowBatch: 非法 farmType 抛 ServiceException，不 INSERT")
    void submitGrowBatch_invalid_type() {
        GrowBatchBo bo = new GrowBatchBo();
        bo.setFarmType("tillage_break");   // 空地类型不属于批量生长路径
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());
        GrowBatchBo.PlotTarget t1 = new GrowBatchBo.PlotTarget();
        t1.setPlantId(7L);
        t1.setPlotId(1L);
        t1.setCropId(2L);
        bo.setTargets(List.of(t1));

        assertThatThrownBy(() -> service.submitGrowBatch(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("批量农事类型");
        verify(baseMapper, never()).insert(any(FarmRecords.class));
    }

    @Test
    @DisplayName("中央分发台 dispatchSummary 返 12 类 key 全在 + count 正确")
    void dispatchSummary_12_keys_present() {
        FarmRecords r1 = new FarmRecords();
        r1.setFarmType("tillage_break");
        FarmRecords r2 = new FarmRecords();
        r2.setFarmType("disaster");
        FarmRecords r3 = new FarmRecords();
        r3.setFarmType("disaster");

        when(baseMapper.selectList(any())).thenReturn(List.of(r1, r2, r3));

        DispatchSummaryVo vo = service.dispatchSummary();
        assertThat(vo.getCounts()).hasSize(12);
        assertThat(vo.getCounts().get("tillage_break")).isEqualTo(1);
        assertThat(vo.getCounts().get("disaster")).isEqualTo(2);
        assertThat(vo.getCounts().get("fertilize")).isEqualTo(0);
        assertThat(vo.getCounts().keySet())
            .containsExactly("tillage_break", "tillage_prepare", "fertilize",
                "transplant", "water_fertilize", "irrigation", "weed", "pest_control", "pruning", "rotation",
                "disaster", "harvest_activity");
    }

    @Test
    @DisplayName("submitDisasterBatch happy: 2 地块 → INSERT 2 行 disaster + 各累加 loss_yield + lossRate≥30 置预警")
    void submitDisasterBatch_happy() {
        DisasterBatchBo bo = new DisasterBatchBo();
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());
        bo.setDisasterType("flood");
        bo.setLossRate(new BigDecimal("45.0"));
        DisasterBatchBo.PlotTarget t1 = new DisasterBatchBo.PlotTarget();
        t1.setPlantId(7L);
        t1.setPlotId(1L);
        t1.setCropId(2L);
        t1.setLossYield(new BigDecimal("23.12"));
        DisasterBatchBo.PlotTarget t2 = new DisasterBatchBo.PlotTarget();
        t2.setPlantId(7L);
        t2.setPlotId(1L);
        t2.setCropId(2L);
        t2.setLossYield(new BigDecimal("15.21"));
        bo.setTargets(List.of(t1, t2));

        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            ((FarmRecords) inv.getArgument(0)).setId(400L);
            return 1;
        });
        PlantDetails d = new PlantDetails();
        d.setId(55L);
        when(plantDetailsMapper.selectOne(any())).thenReturn(d);
        when(plantDetailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        int count = service.submitDisasterBatch(bo);
        assertThat(count).isEqualTo(2);

        ArgumentCaptor<FarmRecords> cap = ArgumentCaptor.forClass(FarmRecords.class);
        verify(baseMapper, times(2)).insert(cap.capture());
        assertThat(cap.getAllValues()).allSatisfy(r -> {
            assertThat(r.getFarmType()).isEqualTo("disaster");
            assertThat(r.getDisasterType()).isEqualTo("flood");
            assertThat(r.getIsWarning()).isEqualTo(1);   // 45 ≥ 30
        });
        // 各地块累加 loss_yield
        verify(plantDetailsMapper, times(2)).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("submitHarvestWeight happy: INSERT harvest_activity 携带 harvest_weight + 累加 actual_yield")
    void submitHarvestWeight_happy() {
        HarvestWeightBo bo = new HarvestWeightBo();
        bo.setPlantId(7L);
        bo.setPlotId(1L);
        bo.setCropId(2L);
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());
        bo.setHarvestWeight(new BigDecimal("52.21"));

        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            ((FarmRecords) inv.getArgument(0)).setId(500L);
            return 1;
        });
        PlantDetails d = new PlantDetails();
        d.setId(66L);
        d.setActualYield(new BigDecimal("10.00"));
        when(plantDetailsMapper.selectOne(any())).thenReturn(d);
        when(plantDetailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        Long id = service.submitHarvestWeight(bo);
        assertThat(id).isEqualTo(500L);

        ArgumentCaptor<FarmRecords> cap = ArgumentCaptor.forClass(FarmRecords.class);
        verify(baseMapper).insert(cap.capture());
        FarmRecords saved = cap.getValue();
        assertThat(saved.getFarmType()).isEqualTo("harvest_activity");
        assertThat(saved.getHarvestWeight()).isEqualByComparingTo("52.21");
        // 累加 actual_yield
        verify(plantDetailsMapper).update(isNull(), any(Wrapper.class));
    }
}
