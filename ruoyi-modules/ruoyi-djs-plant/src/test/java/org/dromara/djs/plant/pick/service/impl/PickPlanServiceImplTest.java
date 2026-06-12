package org.dromara.djs.plant.pick.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.pick.domain.bo.PickAdjustBatchBo;
import org.dromara.djs.plant.pick.domain.bo.PickDetailAdjustBo;
import org.dromara.djs.plant.pick.mapper.PickPlanMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.dromara.djs.common.image.service.ImageUrlResolver;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * {@link PickPlanServiceImpl#adjustDetails} 单测（FIX-PLT-AD-PICK-001 / K182）。
 *
 * <h3>覆盖 case（happy path）</h3>
 * <ol>
 *   <li>用户改「计划最早采摘日期」→ 后端按创建时固化的窗口天数（原 last-earliest）派生重算「计划最晚」，
 *       不接收/不改实际采摘起止（begin/end_harvestdate）。</li>
 * </ol>
 *
 * @author djs
 * @since FIX-PLT-AD-PICK-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PickPlanServiceImpl.adjustDetails 单元测试")
class PickPlanServiceImplTest {

    @Mock
    private PickPlanMapper pickPlanMapper;
    @Mock
    private PlantDetailsMapper detailsMapper;
    @Mock
    private CropInfoMapper cropMapper;
    @Mock
    private PlotInfoMapper plotMapper;
    @Mock
    private PlantWorkTeamMapper teamMapper;
    @Mock
    private ImageUrlResolver imageUrlResolver;

    private PickPlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PickPlanServiceImpl(pickPlanMapper, detailsMapper, cropMapper, plotMapper, teamMapper, imageUrlResolver);
    }

    @Test
    @DisplayName("改计划最早 → 计划最晚按原窗口天数派生重算；实际采摘起止不入更新")
    void adjustDetails_recomputesPlanLatestFromWindow() {
        // 既有明细：earliest=2026-07-16, last=2026-07-20（窗口 4 天），是普通采收（is_pick=2）已指派班组
        PlantDetails existing = new PlantDetails();
        existing.setId(1001L);
        existing.setPlantId(900L);
        existing.setCropId(800L);
        existing.setEarliestHarvestdate(LocalDate.of(2026, 7, 16));
        existing.setLastHarvestdate(LocalDate.of(2026, 7, 20));
        existing.setIsPick(2);
        existing.setHarvestBy(5L);
        when(detailsMapper.selectList(any())).thenReturn(List.of(existing));
        when(detailsMapper.update(isNull(), any())).thenReturn(1);

        // 用户把计划最早改到 2026-07-25
        PickDetailAdjustBo row = new PickDetailAdjustBo();
        row.setId(1001L);
        row.setEarliestHarvestdate(LocalDate.of(2026, 7, 25));

        PickAdjustBatchBo bo = new PickAdjustBatchBo();
        bo.setPlantId(900L);
        bo.setCropId(800L);
        bo.setRows(List.of(row));

        int updated = service.adjustDetails(bo);

        assertThat(updated).isEqualTo(1);

        // 校验更新 wrapper：earliest=2026-07-25、last=2026-07-29（25+4），且未设 begin/end_harvestdate
        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<PlantDetails>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        org.mockito.Mockito.verify(detailsMapper).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertThat(sqlSet).contains("earliest_harvestdate");
        assertThat(sqlSet).contains("last_harvestdate");
        assertThat(sqlSet).doesNotContain("begin_harvestdate");
        assertThat(sqlSet).doesNotContain("end_harvestdate");
    }
}
