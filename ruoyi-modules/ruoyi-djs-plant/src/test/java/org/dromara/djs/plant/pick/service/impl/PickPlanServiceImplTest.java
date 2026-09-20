package org.dromara.djs.plant.pick.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.dromara.djs.plant.common.domain.vo.DateWindowStatusStatVo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.pick.domain.bo.PickAdjustBatchBo;
import org.dromara.djs.plant.pick.domain.bo.PickDetailAdjustBo;
import org.dromara.djs.plant.pick.domain.query.PickPlanQuery;
import org.dromara.djs.plant.pick.domain.vo.PickPlanGroupVo;
import org.dromara.djs.plant.pick.mapper.PickPlanMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.dromara.djs.plant.team.service.PlantTeamLinkService;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * {@link PickPlanServiceImpl} 单测（FIX-PLT-AD-PICK-001 / K182）。
 *
 * <h3>覆盖 case（happy path）</h3>
 * <ol>
 *   <li>{@code adjustDetails}：用户改「计划最早采摘日期」→ 后端按创建时固化的窗口天数（原 last-earliest）
 *       派生重算「计划最晚」，不接收/不改实际采摘起止（begin/end_harvestdate）。</li>
 *   <li>{@code listByCrop}：按最早/最晚采摘日期现算五档状态 + 按状态筛选。</li>
 *   <li>{@code statusStat}：顶部统计版块五档计数。</li>
 * </ol>
 *
 * @author djs
 * @since FIX-PLT-AD-PICK-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PickPlanServiceImpl 单元测试")
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
    private PlantTeamLinkService teamLinkService;
    @Mock
    private ImageUrlResolver imageUrlResolver;

    private PickPlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PickPlanServiceImpl(pickPlanMapper, detailsMapper, cropMapper, plotMapper, teamMapper, teamLinkService, imageUrlResolver);
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

    @Test
    @DisplayName("列表现算状态：按最早/最晚采摘日期落五档，中文名走采摘域说法")
    void listByCropFillsPickStatus() {
        LocalDate today = LocalDate.now();
        when(pickPlanMapper.aggregateByCrop(anyString(), anyInt(), any(), any(), any(), any(), any()))
            .thenReturn(rows(
                cropRow(1L, today.plusDays(60), today.plusDays(200)),   // pending  待采摘
                cropRow(2L, today.plusDays(10), today.plusDays(200)),   // upcoming 即将采摘
                cropRow(3L, today.minusDays(1), today.plusDays(40)),    // on_sale  采摘中
                cropRow(4L, today.minusDays(1), today.plusDays(5)),     // ending   即将结束采摘
                cropRow(5L, today.minusDays(90), today.minusDays(1)),   // off_shelf 完成采摘
                cropRow(6L, null, null)));                              // 没排计划 → 状态留空

        List<PickPlanGroupVo> list = service.listByCrop(new PickPlanQuery());

        assertThat(list).hasSize(6);
        assertThat(list).extracting(PickPlanGroupVo::getPickStatus)
            .containsExactly("pending", "upcoming", "on_sale", "ending", "off_shelf", null);
        assertThat(list).extracting(PickPlanGroupVo::getPickStatusName)
            .containsExactly("待采摘", "即将采摘", "采摘中", "即将结束采摘", "完成采摘", null);
    }

    @Test
    @DisplayName("状态筛选：只留选中那一档（本列表不分页，过滤后即全量，导出同口径）")
    void listByCropFiltersByPickStatus() {
        LocalDate today = LocalDate.now();
        when(pickPlanMapper.aggregateByCrop(anyString(), anyInt(), any(), any(), any(), any(), any()))
            .thenReturn(rows(
                cropRow(1L, today.plusDays(10), today.plusDays(200)),
                cropRow(2L, today.plusDays(20), today.plusDays(200)),
                cropRow(3L, today.minusDays(1), today.plusDays(40))));

        PickPlanQuery query = new PickPlanQuery();
        query.setPickStatus("upcoming");

        List<PickPlanGroupVo> list = service.listByCrop(query);

        assertThat(list).hasSize(2);
        assertThat(list).extracting(PickPlanGroupVo::getCropId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("统计版块：五档计数；状态条件本身被忽略，状态为空的行不计入任何一档")
    void statusStatCountsAllFiveBuckets() {
        LocalDate today = LocalDate.now();
        when(pickPlanMapper.aggregateByCrop(anyString(), anyInt(), any(), any(), any(), any(), any()))
            .thenReturn(rows(
                cropRow(1L, today.plusDays(60), today.plusDays(200)),
                cropRow(2L, today.plusDays(10), today.plusDays(200)),
                cropRow(3L, today.plusDays(20), today.plusDays(200)),
                cropRow(4L, today.minusDays(1), today.plusDays(40)),
                cropRow(5L, today.minusDays(1), today.plusDays(5)),
                cropRow(6L, today.minusDays(90), today.minusDays(1)),
                cropRow(7L, null, null)));

        PickPlanQuery query = new PickPlanQuery();
        query.setPickStatus("on_sale");

        DateWindowStatusStatVo stat = service.statusStat(query);

        assertThat(stat.getPending()).isEqualTo(1);
        assertThat(stat.getUpcoming()).isEqualTo(2);
        assertThat(stat.getOnSale()).isEqualTo(1);
        assertThat(stat.getEnding()).isEqualTo(1);
        assertThat(stat.getOffShelf()).isEqualTo(1);
    }

    @Test
    @DisplayName("统计版块：mapper 返 null → 五档全 0，不抛")
    void statusStatOnNullResultGivesZeros() {
        when(pickPlanMapper.aggregateByCrop(anyString(), anyInt(), any(), any(), any(), any(), any()))
            .thenReturn(null);

        DateWindowStatusStatVo stat = service.statusStat(null);

        assertThat(stat.getPending()).isZero();
        assertThat(stat.getUpcoming()).isZero();
        assertThat(stat.getOnSale()).isZero();
        assertThat(stat.getEnding()).isZero();
        assertThat(stat.getOffShelf()).isZero();
    }

    /** mapper 返回的是可变 list，service 会往行上回写状态。 */
    private List<PickPlanGroupVo> rows(PickPlanGroupVo... items) {
        return new ArrayList<>(List.of(items));
    }

    /** 造一行只关心采摘日期窗口的聚合行（状态由 service 现算）。 */
    private PickPlanGroupVo cropRow(Long cropId, LocalDate earliest, LocalDate latest) {
        PickPlanGroupVo vo = new PickPlanGroupVo();
        vo.setCropId(cropId);
        vo.setCropName("作物" + cropId);
        vo.setPlanEarliest(earliest);
        vo.setPlanLatest(latest);
        return vo;
    }
}
