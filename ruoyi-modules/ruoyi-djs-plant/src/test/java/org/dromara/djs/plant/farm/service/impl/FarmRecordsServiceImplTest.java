package org.dromara.djs.plant.farm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.activity.service.IPlantActivityService;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.farm.domain.vo.CropZoneCountVo;
import org.dromara.djs.plant.farm.domain.vo.FarmCropTargetCardVo;
import org.dromara.djs.plant.farm.domain.FarmRecords;
import org.dromara.djs.plant.farm.domain.bo.DisasterBatchBo;
import org.dromara.djs.plant.farm.domain.bo.DisasterRecordBo;
import org.dromara.djs.plant.farm.domain.bo.EmptyRecordBo;
import org.dromara.djs.plant.farm.domain.bo.GrowBatchBo;
import org.dromara.djs.plant.farm.domain.bo.GrowRecordBo;
import org.dromara.djs.plant.farm.domain.bo.HarvestWeightBo;
import org.dromara.djs.plant.farm.domain.bo.PlotPickStatusBo;
import org.dromara.djs.plant.farm.domain.bo.RotationRecordBo;
import org.dromara.djs.plant.farm.domain.bo.TransplantRecordBo;
import org.dromara.djs.plant.farm.domain.query.FarmRecordsQuery;
import org.dromara.djs.plant.farm.domain.vo.DispatchSummaryVo;
import org.dromara.djs.plant.farm.domain.vo.FarmRecordsVo;
import org.dromara.djs.plant.farm.mapper.FarmRecordsMapper;
import org.dromara.djs.plant.team.domain.PlantWorkPeople;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.mapper.PlantWorkPeopleMapper;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.dromara.djs.plant.zone.mapper.PlotZoneMapper;
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
import java.util.Map;

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
 *   <li>移栽校验：多次累计 > 100% 抛 ServiceException；累计达 100% 触发状态机（采摘完成 + 地块置空）</li>
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
    private PlotZoneMapper plotZoneMapper;
    @Mock
    private CropInfoMapper cropInfoMapper;
    @Mock
    private PlantDetailsMapper plantDetailsMapper;
    @Mock
    private PlantWorkTeamMapper teamMapper;
    @Mock
    private PlantWorkPeopleMapper peopleMapper;
    @Mock
    private ImageUrlResolver imageUrlResolver;
    @Mock
    private IPlantActivityService plantActivityService;
    @Mock
    private org.dromara.djs.plant.team.service.PlantTeamLinkService teamLinkService;

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
        TableInfoHelper.initTableInfo(assistant, PlantWorkPeople.class);
    }

    @BeforeEach
    void setUp() {
        service = new FarmRecordsServiceImpl(baseMapper, plotInfoMapper, plotZoneMapper, cropInfoMapper, plantDetailsMapper, teamMapper, peopleMapper, imageUrlResolver, plantActivityService, teamLinkService);
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
        // 前端不再传损失产量；系统按 expected_yield × lossRate/100 算出

        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            FarmRecords r = inv.getArgument(0);
            r.setId(101L);
            return 1;
        });

        PlantDetails details = new PlantDetails();
        details.setId(55L);
        details.setExpectedYield(new BigDecimal("200"));   // 200 × 45/100 = 90.00
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
        // 损失产量 = 预计产量 200 × 损失率 45/100 = 90.00（两位小数）
        assertThat(saved.getLossYield()).isEqualByComparingTo("90.00");

        verify(plantDetailsMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("退茬触发 plot_info.plot_status=1（plant_details completed 归种植完成 finishPlant，退茬不重复写）")
    void submitRotation_resets_plot_and_completes_details() {
        RotationRecordBo bo = new RotationRecordBo();
        bo.setPlantId(7L);
        bo.setPlotId(1L);
        bo.setCropId(2L);
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());

        // 退茬幂等 guard 要求地块处于采摘态(plot_status=3)；共享 fixture 是空地(1)，本测试局部覆盖为采摘态。
        PlotInfo activePlot = new PlotInfo();
        activePlot.setId(1L);
        activePlot.setPlotStatus(3);
        when(plotInfoMapper.selectById(1L)).thenReturn(activePlot);

        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            FarmRecords r = inv.getArgument(0);
            r.setId(202L);
            return 1;
        });
        when(plotInfoMapper.updateById(any(PlotInfo.class))).thenReturn(1);

        Long id = service.submitRotation(bo);
        assertThat(id).isEqualTo(202L);

        ArgumentCaptor<PlotInfo> plotCap = ArgumentCaptor.forClass(PlotInfo.class);
        verify(plotInfoMapper).updateById(plotCap.capture());
        assertThat(plotCap.getValue().getPlotStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("移栽：历史累计 + 本次 > 100% 抛 ServiceException，不 INSERT（row12 多次累计校验）")
    void submitTransplant_cumulative_exceeds_100() {
        TransplantRecordBo bo = new TransplantRecordBo();
        bo.setPlantId(7L);
        bo.setPlotId(1L);
        bo.setCropId(2L);
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());
        bo.setTransplantPlot(3L);
        bo.setTransplantPercent(50);
        // 历史已移 60%，本次 50% → 110 > 100 拒绝
        when(baseMapper.sumTransplantedPercent(2L, 1L)).thenReturn(60);

        assertThatThrownBy(() -> service.submitTransplant(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("剩余可移 40%");
        verify(baseMapper, never()).insert(any(FarmRecords.class));
    }

    @Test
    @DisplayName("移栽：累计未满 100% 正常 INSERT，不触发状态机（row12/row13）")
    void submitTransplant_under_100_inserts_no_sideEffect() {
        TransplantRecordBo bo = new TransplantRecordBo();
        bo.setPlantId(7L);
        bo.setPlotId(1L);
        bo.setCropId(2L);
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());
        bo.setTransplantPlot(3L);
        bo.setTransplantPercent(30);
        // 提交前累计 50（50+30=80 ≤ 100 放行），提交后重查仍 80（< 100 不触发状态机）
        when(baseMapper.sumTransplantedPercent(2L, 1L)).thenReturn(50, 80);
        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            ((FarmRecords) inv.getArgument(0)).setId(210L);
            return 1;
        });

        Long id = service.submitTransplant(bo);
        assertThat(id).isEqualTo(210L);
        verify(baseMapper).insert(any(FarmRecords.class));
        // 未跨 100% → 不置采摘完成、不置空地
        verify(plantDetailsMapper, never()).update(isNull(), any(Wrapper.class));
        verify(plotInfoMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("移栽：累计刚好跨过 100% → 自动状态机（采摘完成 + 地块置空，row13）")
    void submitTransplant_reaches_100_triggers_stateMachine() {
        TransplantRecordBo bo = new TransplantRecordBo();
        bo.setPlantId(7L);
        bo.setPlotId(1L);
        bo.setCropId(2L);
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());
        bo.setTransplantPlot(3L);
        bo.setTransplantPercent(40);
        // 提交前累计 60（60+40=100 ≤ 100 放行），提交后重查 100（跨过 → 触发状态机）
        when(baseMapper.sumTransplantedPercent(2L, 1L)).thenReturn(60, 100);
        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            ((FarmRecords) inv.getArgument(0)).setId(211L);
            return 1;
        });
        when(plantDetailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(plotInfoMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        Long id = service.submitTransplant(bo);
        assertThat(id).isEqualTo(211L);
        verify(baseMapper).insert(any(FarmRecords.class));
        // 副作用：plant_details 采摘完成 + plot_info 置空地各 1 次
        verify(plantDetailsMapper).update(isNull(), any(Wrapper.class));
        verify(plotInfoMapper).update(isNull(), any(Wrapper.class));
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
    @DisplayName("submitGrow: 普通采收 farmType='harvest' 被接受并 INSERT（FIX-PLT-AD-PICK-FARMTYPE-001 回归守门）")
    void submitGrow_harvest_accepted() {
        // 背景：AppletPickServiceImpl.submitPick 对普通采收(is_pick=2)写 farm_type='harvest' 经此入库。
        // 旧 isPlainGrowFarmType 漏列 'harvest' → mp「开始采摘」报「不支持的生长阶段农事类型: harvest」。
        GrowRecordBo bo = new GrowRecordBo();
        bo.setFarmType("harvest");
        bo.setPlantId(7L);
        bo.setPlotId(1L);
        bo.setCropId(2L);
        bo.setFarmBy(10L);
        bo.setFarmDate(LocalDate.now());

        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            ((FarmRecords) inv.getArgument(0)).setId(400L);
            return 1;
        });

        Long id = service.submitGrow(bo);
        assertThat(id).isEqualTo(400L);

        ArgumentCaptor<FarmRecords> cap = ArgumentCaptor.forClass(FarmRecords.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getFarmType()).isEqualTo("harvest");
    }

    @Test
    @DisplayName("adjustPlotPickStatus completed: begin_harvestdate NULL-safe 回填（COALESCE 不覆写已有值）+ end_harvestdate=调整日期")
    @SuppressWarnings("unchecked")
    void adjustPlotPickStatus_completed_backfills_begin_harvestdate() {
        PlotPickStatusBo bo = new PlotPickStatusBo();
        bo.setPlotId(1L);
        bo.setCropId(2L);
        bo.setPickStatus("completed");
        bo.setAdjustDate(LocalDate.of(2026, 7, 21));
        bo.setTeamId(10L);

        PlantDetails d = new PlantDetails();
        d.setId(55L);
        d.setPlantId(7L);
        d.setIsPick(1);
        d.setHarvestStatus("picking");   // 未完成行 → completed 方向的更新目标
        when(plantDetailsMapper.selectList(any(Wrapper.class))).thenReturn(List.of(d));
        when(plantDetailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            ((FarmRecords) inv.getArgument(0)).setId(600L);
            return 1;
        });

        int affected = service.adjustPlotPickStatus(bo);
        assertThat(affected).isEqualTo(1);

        ArgumentCaptor<Wrapper> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(plantDetailsMapper).update(isNull(), cap.capture());
        LambdaUpdateWrapper<PlantDetails> uw = (LambdaUpdateWrapper<PlantDetails>) cap.getValue();
        String sqlSet = uw.getSqlSet();
        // begin_harvestdate 走 COALESCE NULL-safe 回填（已有值不覆写）；end_harvestdate 同批落调整日期
        assertThat(sqlSet).contains("begin_harvestdate = COALESCE(begin_harvestdate,");
        assertThat(sqlSet).contains("end_harvestdate");
        // 调整日期走参数绑定（非字符串拼接），状态值也在同一 wrapper 参数里
        assertThat(uw.getParamNameValuePairs().values())
            .contains(LocalDate.of(2026, 7, 21), "completed");
        // 留痕农事记录仍写一条 harvest_activity
        verify(baseMapper).insert(any(FarmRecords.class));
    }

    @Test
    @DisplayName("adjustPlotPickStatus picking: 同样回填 begin_harvestdate + 班组写 harvest_by")
    @SuppressWarnings("unchecked")
    void adjustPlotPickStatus_picking_backfills_begin_harvestdate() {
        PlotPickStatusBo bo = new PlotPickStatusBo();
        bo.setPlotId(1L);
        bo.setCropId(2L);
        bo.setPickStatus("picking");
        bo.setAdjustDate(LocalDate.of(2026, 7, 20));
        bo.setTeamId(10L);

        PlantDetails d = new PlantDetails();
        d.setId(56L);
        d.setPlantId(7L);
        d.setIsPick(1);
        d.setHarvestStatus("pending");
        when(plantDetailsMapper.selectList(any(Wrapper.class))).thenReturn(List.of(d));
        when(plantDetailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            ((FarmRecords) inv.getArgument(0)).setId(601L);
            return 1;
        });

        int affected = service.adjustPlotPickStatus(bo);
        assertThat(affected).isEqualTo(1);

        ArgumentCaptor<Wrapper> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(plantDetailsMapper).update(isNull(), cap.capture());
        LambdaUpdateWrapper<PlantDetails> uw = (LambdaUpdateWrapper<PlantDetails>) cap.getValue();
        String sqlSet = uw.getSqlSet();
        assertThat(sqlSet).contains("begin_harvestdate = COALESCE(begin_harvestdate,");
        // picking 方向不写 end_harvestdate（完成日期归 completed 方向独占），班组落 harvest_by
        assertThat(sqlSet).doesNotContain("end_harvestdate");
        assertThat(sqlSet).contains("harvest_by");
        assertThat(uw.getParamNameValuePairs().values())
            .contains(LocalDate.of(2026, 7, 20), "picking", 10L);
    }

    @Test
    @DisplayName("中央分发台 dispatchSummary：已处理=当日去重地块数 + 待处理=空地池，12 类 key 全在")
    void dispatchSummary_processed_and_pending() {
        // 已处理：tillage_break 去重 1 块、disaster 去重 2 块（同地块多次仍记 1，由 SQL COUNT DISTINCT 保证）
        when(baseMapper.selectTodayProcessedPlotCount(any())).thenReturn(List.of(
            Map.of("farmType", "tillage_break", "plotCount", 1),
            Map.of("farmType", "disaster", "plotCount", 2)));
        // 待处理：空地池 plot_status=1 共 5 块（12 工种共享）
        when(plotInfoMapper.selectCount(any())).thenReturn(5L);

        DispatchSummaryVo vo = service.dispatchSummary();

        assertThat(vo.getProcessedCount()).hasSize(12);
        assertThat(vo.getProcessedCount().get("tillage_break")).isEqualTo(1);
        assertThat(vo.getProcessedCount().get("disaster")).isEqualTo(2);
        assertThat(vo.getProcessedCount().get("fertilize")).isEqualTo(0);
        assertThat(vo.getProcessedCount().keySet())
            .containsExactly("tillage_break", "tillage_prepare", "fertilize",
                "transplant", "water_fertilize", "irrigation", "weed", "pest_control", "pruning", "rotation",
                "disaster", "harvest_activity");

        assertThat(vo.getPendingCount()).hasSize(12);
        assertThat(vo.getPendingCount().get("tillage_break")).isEqualTo(5);
        assertThat(vo.getPendingCount().get("harvest_activity")).isEqualTo(5);
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
        DisasterBatchBo.PlotTarget t2 = new DisasterBatchBo.PlotTarget();
        t2.setPlantId(7L);
        t2.setPlotId(1L);
        t2.setCropId(2L);
        bo.setTargets(List.of(t1, t2));
        // 前端不再传各地块损失产量；系统按各地块 expected_yield × lossRate/100 算出

        when(baseMapper.insert(any(FarmRecords.class))).thenAnswer(inv -> {
            ((FarmRecords) inv.getArgument(0)).setId(400L);
            return 1;
        });
        PlantDetails d = new PlantDetails();
        d.setId(55L);
        d.setExpectedYield(new BigDecimal("200"));   // 200 × 45/100 = 90.00
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
            // 损失产量 = 预计产量 200 × 损失率 45/100 = 90.00（两位小数）
            assertThat(r.getLossYield()).isEqualByComparingTo("90.00");
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

    @Test
    @DisplayName("myRecords 必须同时按 farmType 与所属班组 farmBy 过滤（修 P4/P8 漏 farmType）")
    @SuppressWarnings("unchecked")
    void myRecords_filters_by_farmType_and_team() {
        // 登录用户经 work_people 反查所属班组 → teamIds [10,20]
        PlantWorkPeople m1 = new PlantWorkPeople();
        m1.setTeamId(10L);
        PlantWorkPeople m2 = new PlantWorkPeople();
        m2.setTeamId(20L);
        when(peopleMapper.selectList(any())).thenReturn(List.of(m1, m2));

        when(baseMapper.selectVoPage(any(IPage.class), any(Wrapper.class)))
            .thenReturn(new Page<FarmRecordsVo>());

        FarmRecordsQuery query = new FarmRecordsQuery();
        query.setFarmType("tillage_break");
        query.setOperatorId(99L);

        service.myRecords(query, new PageQuery(10, 1));

        ArgumentCaptor<Wrapper> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).selectVoPage(any(IPage.class), cap.capture());
        LambdaQueryWrapper<FarmRecords> w = (LambdaQueryWrapper<FarmRecords>) cap.getValue();
        // 同一 wrapper 内 farmType eq + farmBy in 都在（buildWrapper 已含 farm_type）
        String sql = w.getCustomSqlSegment();
        assertThat(sql).contains("farm_type");
        assertThat(sql).contains("farm_by");
        Map<String, Object> params = w.getParamNameValuePairs();
        assertThat(params.values()).contains("tillage_break", 10L, 20L);
    }

    @Test
    @DisplayName("listCropTargetCards: rotation 走 ForRotation @Select（harvest_status='completed'），透传 farmType/zoneId/plotCode")
    void listCropTargetCards_rotationUsesCompletedStatus() {
        when(baseMapper.selectCropTargetCardsForRotation(any(), any(), any())).thenReturn(List.of(
            Map.of("cropId", 2L, "cropName", "白菜", "cropCode", "C001", "plotCount", 3)));
        when(imageUrlResolver.resolveList(any())).thenReturn(List.of("http://img/c001.png"));

        List<FarmCropTargetCardVo> cards = service.listCropTargetCards("rotation", 9L, "PL001");

        // 路由到退茬聚合且参数原样透传
        verify(baseMapper).selectCropTargetCardsForRotation(eq("rotation"), eq(9L), eq("PL001"));
        verify(baseMapper, never()).selectCropTargetCardsForGrow(any(), any(), any());
        verify(baseMapper, never()).selectCropTargetCardsForTransplant(any(), any(), any());
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getId()).isEqualTo(2L);
        assertThat(cards.get(0).getPlotCount()).isEqualTo(3);
        // cropImg 回填（IMG-LIB-001 resolver）
        assertThat(cards.get(0).getCropImg()).isEqualTo("http://img/c001.png");
    }

    @Test
    @DisplayName("listCropTargetCards: 生长工种走 ForGrow @Select（plant_status='ongoing'）")
    void listCropTargetCards_growUsesOngoingStatus() {
        when(baseMapper.selectCropTargetCardsForGrow(any(), any(), any())).thenReturn(List.of(
            Map.of("cropId", 2L, "cropName", "番茄", "cropCode", "C002", "plotCount", 2)));
        when(imageUrlResolver.resolveList(any())).thenReturn(List.of("http://img/c002.png"));

        List<FarmCropTargetCardVo> cards = service.listCropTargetCards("water_fertilize", null, null);

        verify(baseMapper).selectCropTargetCardsForGrow(eq("water_fertilize"), isNull(), isNull());
        verify(baseMapper, never()).selectCropTargetCardsForRotation(any(), any(), any());
        verify(baseMapper, never()).selectCropTargetCardsForTransplant(any(), any(), any());
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getCropName()).isEqualTo("番茄");
        assertThat(cards.get(0).getPlotCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("listCropTargetCards: 含无图作物（resolveCropImageOssId 返 null）不抛 NPE（fillCropImg toMap null-value 修复）")
    void listCropTargetCards_cropWithoutImage_noNpe() {
        // 真机 500 复现：浇灌等生长工种的作物卡里含一个无图作物（西兰苔，image_oss_id/preview/url 全空）
        // → resolveCropImageOssId 返 null → 旧 Collectors.toMap 遇 null value 抛 NPE → cropTargetCards 500。
        when(baseMapper.selectCropTargetCardsForGrow(any(), any(), any())).thenReturn(List.of(
            Map.of("cropId", 2L, "cropName", "西兰苔", "cropCode", "C009", "plotCount", 1)));
        CropInfo noImg = new CropInfo();
        noImg.setId(2L);   // 全部图字段为 null → resolveCropImageOssId 返 null
        when(cropInfoMapper.selectByIds(any())).thenReturn(List.of(noImg));
        when(imageUrlResolver.resolveList(any())).thenReturn(List.of("http://img/default.png"));

        List<FarmCropTargetCardVo> cards = service.listCropTargetCards("irrigation", null, null);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getCropName()).isEqualTo("西兰苔");
    }

    @Test
    @DisplayName("listCropTargetCards: 移栽走 ForTransplant @Select（叠 plot_type='nursery' 保育地块）")
    void listCropTargetCards_transplantStatusRule() {
        when(baseMapper.selectCropTargetCardsForTransplant(any(), any(), any())).thenReturn(List.of(
            Map.of("cropId", 2L, "cropName", "辣椒", "cropCode", "C003", "plotCount", 1)));
        when(imageUrlResolver.resolveList(any())).thenReturn(List.of("http://img/c003.png"));

        List<FarmCropTargetCardVo> cards = service.listCropTargetCards("transplant", null, null);

        verify(baseMapper).selectCropTargetCardsForTransplant(eq("transplant"), isNull(), isNull());
        verify(baseMapper, never()).selectCropTargetCardsForGrow(any(), any(), any());
        verify(baseMapper, never()).selectCropTargetCardsForRotation(any(), any(), any());
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getCropName()).isEqualTo("辣椒");
    }

    @Test
    @DisplayName("listCropTargetCards: farmType 空返空列表，不触 mapper")
    void listCropTargetCards_blankFarmType() {
        assertThat(service.listCropTargetCards("", 1L, null)).isEmpty();
        verify(baseMapper, never()).selectCropTargetCardsForGrow(any(), any(), any());
        verify(baseMapper, never()).selectCropTargetCardsForRotation(any(), any(), any());
        verify(baseMapper, never()).selectCropTargetCardsForTransplant(any(), any(), any());
    }

    @Test
    @DisplayName("listCropZoneCounts: 工种 → 片区胶囊 @Select 分支（rotation/transplant/grow），含 0 片区透传")
    void listCropZoneCounts_routesByFarmType() {
        when(baseMapper.selectCropZoneCountsForRotation()).thenReturn(List.of(
            Map.of("zoneId", 9L, "zoneName", "A区", "plotCount", 3),
            Map.of("zoneId", 10L, "zoneName", "B区", "plotCount", 0)));
        List<CropZoneCountVo> rot = service.listCropZoneCounts("rotation");
        verify(baseMapper).selectCropZoneCountsForRotation();
        verify(baseMapper, never()).selectCropZoneCountsForGrow();
        verify(baseMapper, never()).selectCropZoneCountsForTransplant();
        assertThat(rot).hasSize(2);
        assertThat(rot.get(0).getZoneName()).isEqualTo("A区");
        assertThat(rot.get(0).getPlotCount()).isEqualTo(3);
        assertThat(rot.get(1).getPlotCount()).isZero();

        when(baseMapper.selectCropZoneCountsForTransplant()).thenReturn(List.of(
            Map.of("zoneId", 9L, "zoneName", "A区", "plotCount", 1)));
        service.listCropZoneCounts("transplant");
        verify(baseMapper).selectCropZoneCountsForTransplant();

        when(baseMapper.selectCropZoneCountsForGrow()).thenReturn(List.of(
            Map.of("zoneId", 9L, "zoneName", "A区", "plotCount", 2)));
        service.listCropZoneCounts("water_fertilize");
        verify(baseMapper).selectCropZoneCountsForGrow();
    }

    @Test
    @DisplayName("listCropZoneCounts: farmType 空返空列表，不触 mapper")
    void listCropZoneCounts_blankFarmType() {
        assertThat(service.listCropZoneCounts("")).isEmpty();
        verify(baseMapper, never()).selectCropZoneCountsForGrow();
        verify(baseMapper, never()).selectCropZoneCountsForRotation();
        verify(baseMapper, never()).selectCropZoneCountsForTransplant();
    }

    @Test
    @DisplayName("listCropPlots: rotation 改判 harvest_status='completed'（T3 与 listCropTargetCards 退茬口径一致）")
    @SuppressWarnings("unchecked")
    void listCropPlots_rotationUsesHarvestStatusCompleted() {
        // 退茬分支先查 plot_status=3 活跃地块；mock 一个非空，否则空集合提前 return、plantDetailsMapper.selectList 不被调用。
        PlotInfo activePlot = new PlotInfo();
        activePlot.setId(99L);
        activePlot.setPlotStatus(3);
        when(plotInfoMapper.selectList(any(Wrapper.class))).thenReturn(List.of(activePlot));
        when(plantDetailsMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        service.listCropPlots(2L, "rotation");

        ArgumentCaptor<Wrapper> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(plantDetailsMapper).selectList(cap.capture());
        LambdaQueryWrapper<PlantDetails> w = (LambdaQueryWrapper<PlantDetails>) cap.getValue();
        String sql = w.getCustomSqlSegment();
        // 退茬口径走 harvest_status 列、值 completed；不再用 plant_status
        assertThat(sql).contains("harvest_status");
        assertThat(sql).doesNotContain("plant_status");
        assertThat(w.getParamNameValuePairs().values()).contains("completed", 2L);
    }

    @Test
    @DisplayName("listCropPlots: 生长工种判 plant_status='completed' AND harvest_status<>'completed'（产出期口径）")
    @SuppressWarnings("unchecked")
    void listCropPlots_growUsesPlantStatusOngoing() {
        when(plantDetailsMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        service.listCropPlots(2L, "water_fertilize");

        ArgumentCaptor<Wrapper> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(plantDetailsMapper).selectList(cap.capture());
        LambdaQueryWrapper<PlantDetails> w = (LambdaQueryWrapper<PlantDetails>) cap.getValue();
        String sql = w.getCustomSqlSegment();
        // 产出期口径：plant_status='completed' AND harvest_status<>'completed'（种植完成但未采完）
        assertThat(sql).contains("plant_status");
        assertThat(sql).contains("harvest_status");
        assertThat(w.getParamNameValuePairs().values()).contains("completed", 2L);
    }
}
