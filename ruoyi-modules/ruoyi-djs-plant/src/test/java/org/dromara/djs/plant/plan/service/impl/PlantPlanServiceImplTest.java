package org.dromara.djs.plant.plan.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.domain.PlantPlan;
import org.dromara.djs.plant.plan.domain.bo.PlantDetailAdjustBo;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.plan.domain.bo.PlantDetailInputBo;
import org.dromara.djs.plant.plan.domain.bo.PlantPlanCreateBo;
import org.dromara.djs.plant.plan.domain.bo.PlantFinishBo;
import org.dromara.djs.plant.plan.domain.bo.PlantPlanUpdateBo;
import org.dromara.djs.plant.plan.domain.bo.PlantStartBo;
import org.dromara.djs.plant.plan.domain.query.PlantPlanQuery;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plan.mapper.PlantPlanMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlantPlanServiceImpl} 单测（PLT-PLAN-001）。
 *
 * <h3>覆盖 case</h3>
 * <ol>
 *   <li>happy create：1 plan + 3 details → recalc 被触发 + plan_no 序号正确</li>
 *   <li>earliest 公式：plant_month=4 / period=05 / crop.min_cycle=60 → 4/5 + 60d = 6/4</li>
 *   <li>ongoing 改作物：抛 ServiceException</li>
 *   <li>delete with valid: 有 begin_actualdate 行 → 抛 ServiceException</li>
 *   <li>nextPlanNo: 同年序号连续递增</li>
 *   <li>periodToDay 非法值：抛 ServiceException</li>
 * </ol>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlantPlanServiceImpl 单元测试")
class PlantPlanServiceImplTest {

    @Mock
    private PlantPlanMapper planMapper;
    @Mock
    private PlantDetailsMapper detailsMapper;
    @Mock
    private CropInfoMapper cropMapper;
    @Mock
    private PlotInfoMapper plotMapper;
    @Mock
    private PlotZoneMapper zoneMapper;
    @Mock
    private PlantWorkTeamMapper teamMapper;
    @Mock
    private IBizCodeGenerator bizCodeGenerator;
    @Mock
    private org.dromara.djs.plant.team.service.PlantTeamLinkService teamLinkService;

    private PlantPlanServiceImpl service;

    /**
     * MyBatis-Plus 单测 entity cache 预热：startPlant 走 LambdaQueryWrapper / LambdaUpdateWrapper
     * （PlantDetails / PlotInfo），mock 路径下也会触发 TableInfoHelper.getTableInfo() 解析 lambda 列名。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, PlantPlan.class);
        TableInfoHelper.initTableInfo(assistant, PlantDetails.class);
        TableInfoHelper.initTableInfo(assistant, PlotInfo.class);
    }

    @BeforeEach
    void setUp() {
        service = new PlantPlanServiceImpl(planMapper, detailsMapper, cropMapper, plotMapper, zoneMapper, teamMapper, bizCodeGenerator, teamLinkService);
    }

    @Test
    @DisplayName("createByBo: 1 plan + 3 details，触发 recalcAggregates，plan_no 取年度首条")
    void createHappy() {
        CropInfo crop = mockCrop(100L, 60, 90, new BigDecimal("2000.000"));
        when(cropMapper.selectById(100L)).thenReturn(crop);
        when(plotMapper.selectByIds(any())).thenReturn(List.of(
            mockPlot(10L, new BigDecimal("3.50")),
            mockPlot(20L, new BigDecimal("1.20")),
            mockPlot(30L, new BigDecimal("2.80"))));
        when(bizCodeGenerator.generate(eq(BizCodeType.PLAN_NO), any())).thenReturn("PLAN-2026-001");
        when(planMapper.insert(any(PlantPlan.class))).thenAnswer(inv -> {
            PlantPlan plan = inv.getArgument(0);
            plan.setId(999L);
            return 1;
        });
        when(detailsMapper.insert(any(PlantDetails.class))).thenReturn(1);

        PlantPlanCreateBo bo = new PlantPlanCreateBo();
        bo.setPlanYear(2026);
        bo.setPlanSeason("spring");
        bo.setCropId(100L);
        bo.setDetails(List.of(
            mockInput(10L, 4, "05"),
            mockInput(20L, 4, "15"),
            mockInput(30L, 5, "05")));

        Long id = service.createByBo(bo);

        assertThat(id).isEqualTo(999L);
        ArgumentCaptor<PlantPlan> planCap = ArgumentCaptor.forClass(PlantPlan.class);
        verify(planMapper).insert(planCap.capture());
        assertThat(planCap.getValue().getPlanNo()).isEqualTo("PLAN-2026-001");
        assertThat(planCap.getValue().getPlantStatus()).isEqualTo("pending");

        verify(detailsMapper, times(3)).insert(any(PlantDetails.class));
        verify(planMapper).recalcAggregates(999L);
    }

    @Test
    @DisplayName("buildDetail: earliest = 4/5 + 60d = 6/4；last = 4/5 + 90d = 7/4；expected = 3.5 × 2000")
    void detailDerivedFormulas() {
        CropInfo crop = mockCrop(100L, 60, 90, new BigDecimal("2000.000"));
        when(cropMapper.selectById(100L)).thenReturn(crop);
        when(plotMapper.selectByIds(any())).thenReturn(List.of(mockPlot(10L, new BigDecimal("3.50"))));
        when(bizCodeGenerator.generate(eq(BizCodeType.PLAN_NO), any())).thenReturn("PLAN-2026-001");
        when(planMapper.insert(any(PlantPlan.class))).thenAnswer(inv -> {
            ((PlantPlan) inv.getArgument(0)).setId(1L);
            return 1;
        });

        ArgumentCaptor<PlantDetails> detailCap = ArgumentCaptor.forClass(PlantDetails.class);
        when(detailsMapper.insert(detailCap.capture())).thenReturn(1);

        PlantPlanCreateBo bo = new PlantPlanCreateBo();
        bo.setPlanYear(2026);
        bo.setPlanSeason("spring");
        bo.setCropId(100L);
        bo.setDetails(List.of(mockInput(10L, 4, "05")));
        service.createByBo(bo);

        PlantDetails saved = detailCap.getValue();
        assertThat(saved.getEarliestHarvestdate()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(saved.getLastHarvestdate()).isEqualTo(LocalDate.of(2026, 7, 4));
        assertThat(saved.getExpectedYield()).isEqualByComparingTo(new BigDecimal("7000.000"));
        assertThat(saved.getPlantStatus()).isEqualTo("pending");
        assertThat(saved.getHarvestStatus()).isEqualTo("pending");
        assertThat(saved.getIsPick()).isEqualTo(2);
    }

    @Test
    @DisplayName("updateByBo: ongoing 计划改 cropId 抛 ServiceException")
    void updateOngoingChangeCropRejected() {
        PlantPlan existing = new PlantPlan();
        existing.setId(1L);
        existing.setPlanYear(2026);
        existing.setCropId(100L);
        existing.setPlantStatus("ongoing");
        when(planMapper.selectById(1L)).thenReturn(existing);

        PlantPlanUpdateBo bo = new PlantPlanUpdateBo();
        bo.setId(1L);
        bo.setCropId(200L);  // 不同作物
        bo.setPlanSeason("spring");

        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已开始执行的计划不允许修改作物");
    }

    @Test
    @DisplayName("deleteWithValidByIds: 关联明细已 begin_actualdate 抛 ServiceException")
    void deleteRejectedWhenStarted() {
        when(detailsMapper.selectCount(any(Wrapper.class))).thenReturn(2L);
        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(1L, 2L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("无法删除");
        verify(planMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("nextPlanNo: 委托给 BizCodeGenerator(PLAN_NO)，序号续增")
    void nextPlanNoIncrement() {
        when(bizCodeGenerator.generate(eq(BizCodeType.PLAN_NO), any())).thenReturn("PLAN-2026-006");
        assertThat(service.nextPlanNo()).isEqualTo("PLAN-2026-006");
    }

    @Test
    @DisplayName("nextPlanNo: 委托给 BizCodeGenerator(PLAN_NO)，首条返 001")
    void nextPlanNoFirst() {
        when(bizCodeGenerator.generate(eq(BizCodeType.PLAN_NO), any())).thenReturn("PLAN-2027-001");
        assertThat(service.nextPlanNo()).isEqualTo("PLAN-2027-001");
    }

    @Test
    @DisplayName("startPlant happy: 仅未开工明细被回写 ongoing + plot_status=2，返回开工行数")
    void startPlantHappy() {
        PlantDetails d1 = new PlantDetails();
        d1.setId(11L);
        d1.setPlotId(101L);
        d1.setBeginActualdate(null);   // 未开工
        PlantDetails d2 = new PlantDetails();
        d2.setId(12L);
        d2.setPlotId(102L);
        d2.setBeginActualdate(LocalDate.of(2026, 5, 1));   // 已开工，应被跳过
        when(detailsMapper.selectList(any())).thenReturn(List.of(d1, d2));
        when(detailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(plotMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        PlantStartBo bo = new PlantStartBo();
        bo.setDetailIds(List.of(11L, 12L));
        bo.setBeginActualdate(LocalDate.of(2026, 6, 9));
        bo.setPlantBy(7L);

        int affected = service.startPlant(bo);

        assertThat(affected).isEqualTo(1);   // 仅 d1 开工
        verify(detailsMapper).update(isNull(), any(Wrapper.class));   // 批量回写明细
        verify(plotMapper).update(isNull(), any(Wrapper.class));      // 同步地块 plot_status=2
    }

    @Test
    @DisplayName("startPlant: 传入 detailIds 部分不存在（跨租户/已删）抛 ServiceException")
    void startPlantMissingDetailRejected() {
        PlantDetails d1 = new PlantDetails();
        d1.setId(11L);
        d1.setPlotId(101L);
        when(detailsMapper.selectList(any())).thenReturn(List.of(d1));   // 只查到 1 条

        PlantStartBo bo = new PlantStartBo();
        bo.setDetailIds(List.of(11L, 99L));   // 99 不存在
        bo.setBeginActualdate(LocalDate.of(2026, 6, 9));
        bo.setPlantBy(7L);

        assertThatThrownBy(() -> service.startPlant(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不存在");
        verify(detailsMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("finishPlant 一步落地：待种植(begin 空)直接完成 + 补班组/采摘窗口 + plot_status=2，已完成跳过")
    void finishPlantOneStep() {
        PlantDetails d1 = new PlantDetails();   // 待种植：begin 空 → 一步完成时补 begin/班组/窗口/plot_status
        d1.setId(11L);
        d1.setPlotId(101L);
        d1.setCropId(30L);
        d1.setBeginActualdate(null);
        d1.setEndActualdate(null);
        d1.setPlantStatus("pending");
        PlantDetails d2 = new PlantDetails();   // 已完成 → 跳过
        d2.setId(12L);
        d2.setPlotId(102L);
        d2.setPlantStatus("completed");
        when(detailsMapper.selectList(any())).thenReturn(List.of(d1, d2));
        when(detailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(plotMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(cropMapper.selectByIds(any())).thenReturn(List.of(mockCrop(30L, 60, 90, new BigDecimal("2000.000"))));

        PlantFinishBo bo = new PlantFinishBo();
        bo.setDetailIds(List.of(11L, 12L));
        bo.setEndActualdate(LocalDate.of(2026, 6, 18));
        bo.setPlantBy(7L);

        int affected = service.finishPlant(bo);

        assertThat(affected).isEqualTo(1);   // 仅 d1 完成（d2 已完成跳过）
        verify(plotMapper).update(isNull(), any(Wrapper.class));   // 待种植地块一步落地补 plot_status=2
    }

    // ============================================================
    // 列表排序 + 计划月份多选（admin row173）
    // ============================================================

    /**
     * 跑一次 {@code queryPageList} 并把 service 内部构造的 wrapper 抓出来。
     *
     * <p>{@code buildWrapper} 是私有方法，只能从公开入口反抓：ArgumentCaptor 拿到传给
     * {@code selectVoPage} 的 wrapper，再断言它最终会渲染成什么 SQL 片段
     * （{@code getSqlSegment()} = WHERE 段 + last 段，last 段就是 ORDER BY）。</p>
     */
    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<PlantPlan> captureListWrapper(PlantPlanQuery query) {
        when(planMapper.selectVoPage(any(), any())).thenReturn(new Page<>());
        service.queryPageList(query, new PageQuery(10, 1));
        ArgumentCaptor<LambdaQueryWrapper<PlantPlan>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(planMapper).selectVoPage(any(), cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("列表排序: ORDER BY 计划年份 → 最早明细(月份+旬别) → id，且子查询自带租户谓词")
    void queryPageList_ordersByPlantingPeriodDesc_withTenantPredicate() {
        String sql = captureListWrapper(new PlantPlanQuery()).getSqlSegment();

        assertThat(sql).contains("ORDER BY plan_year DESC");
        // 排序键与列表「计划种植日期」列同源：最早那条明细的 月份补零 + 旬别
        assertThat(sql).contains("MIN(CONCAT(LPAD(d.plant_month, 2, '0'), d.plant_period))");
        // ⚠️ 回归守门：MP 租户拦截器不进 ORDER BY 子查询，这个谓词必须自己写死在 SQL 里。
        // 少了它 t_plant_plant_details 的索引（首列 tenant_id）最左前缀失效 → 逐行全表扫。
        assertThat(sql).contains("d.tenant_id = t_plant_plant_plan.tenant_id");
        // 同键行的分页稳定性兜底
        assertThat(sql).endsWith("id DESC");
    }

    @Test
    @DisplayName("列表排序: 请求参数 orderByColumn/isAsc 被丢弃，压不过固定排序")
    @SuppressWarnings("unchecked")
    void queryPageList_ignoresRequestOrderByParams() {
        when(planMapper.selectVoPage(any(), any())).thenReturn(new Page<>());
        PageQuery pageQuery = new PageQuery(10, 1);
        pageQuery.setOrderByColumn("planYear");
        pageQuery.setIsAsc("asc");

        service.queryPageList(new PlantPlanQuery(), pageQuery);

        // MP 分页拦截器把 page.orders() 拼在 wrapper 的 ORDER BY **之前**，
        // 留着就等于任何人加个 ?orderByColumn=... 就能静默改掉甲方要求的首排序键。
        ArgumentCaptor<Page<PlantPlan>> cap = ArgumentCaptor.forClass(Page.class);
        verify(planMapper).selectVoPage(cap.capture(), any());
        assertThat(cap.getValue().orders()).isEmpty();
    }

    @Test
    @DisplayName("计划月份多选: 传 [7,3] → EXISTS(... plant_month IN (?,?))，两个月份都进参数")
    void queryPageList_planMonths_multiSelect() {
        PlantPlanQuery q = new PlantPlanQuery();
        q.setPlanMonths(List.of(7, 3));

        LambdaQueryWrapper<PlantPlan> wrapper = captureListWrapper(q);

        assertThat(wrapper.getSqlSegment()).contains("d.plant_month IN (");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(7, 3);
    }

    @Test
    @DisplayName("计划月份多选: null（没选月份）→ 不加月份条件")
    void queryPageList_planMonths_null_noFilter() {
        // 注意断言 "d.plant_month IN"（筛选条件）而不是 "plant_month"：
        // ORDER BY 的排序键里本来就有 LPAD(d.plant_month,...)，后者永远命中。
        PlantPlanQuery q = new PlantPlanQuery();
        q.setPlanMonths(null);

        assertThat(captureListWrapper(q).getSqlSegment()).doesNotContain("d.plant_month IN");
    }

    @Test
    @DisplayName("计划月份多选: 空 list（下拉清空）→ 不加月份条件")
    void queryPageList_planMonths_empty_noFilter() {
        PlantPlanQuery q = new PlantPlanQuery();
        q.setPlanMonths(Collections.emptyList());

        assertThat(captureListWrapper(q).getSqlSegment()).doesNotContain("d.plant_month IN");
    }

    @Test
    @DisplayName("计划月份多选: 0 / 13 / null 越界值静默丢弃，只剩合法月份进 IN")
    void queryPageList_planMonths_outOfRange_dropped() {
        PlantPlanQuery q = new PlantPlanQuery();
        q.setPlanMonths(Arrays.asList(0, 7, 13, null));

        LambdaQueryWrapper<PlantPlan> wrapper = captureListWrapper(q);

        assertThat(wrapper.getSqlSegment()).contains("d.plant_month IN (");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(7).doesNotContain(0, 13);
    }

    @Test
    @DisplayName("计划月份多选: 全部越界(0/13) → 退化成不加月份条件，不生成空 IN ()")
    void queryPageList_planMonths_allInvalid_noFilter() {
        PlantPlanQuery q = new PlantPlanQuery();
        q.setPlanMonths(List.of(0, 13));

        assertThat(captureListWrapper(q).getSqlSegment()).doesNotContain("d.plant_month IN");
    }

    @Test
    @DisplayName("排序键语义: CONCAT(LPAD(月,2,'0'), 旬别) 倒序 == 月份降序 + 下旬→中旬→上旬")
    void plantingPeriodSortKey_semantics() {
        // 与 applyPlantingPeriodOrder 的 SQL 表达式同构：月份补零后拼旬别（05/15/25），按字符串比较。
        // 甲方要求「先按月份降序，同月份内下旬→中旬→上旬」，靠的就是 25 > 15 > 05 这个字典序巧合。
        record Row(int month, String period, String label) {
            String sortKey() {
                return "%02d%s".formatted(month, period);
            }
        }
        List<Row> rows = new ArrayList<>(List.of(
            new Row(7, "05", "7月上旬"),
            new Row(8, "05", "8月上旬"),
            new Row(7, "25", "7月下旬"),
            new Row(6, "15", "6月中旬"),
            new Row(7, "15", "7月中旬")));

        rows.sort(Comparator.comparing(Row::sortKey).reversed());

        assertThat(rows.stream().map(Row::label))
            .containsExactly("8月上旬", "7月下旬", "7月中旬", "7月上旬", "6月中旬");
    }

    // ============================================================
    // 已种植地块后台调整 adjustPlantedDetail（V6-R36）—— 四个保存分支各一
    // ============================================================

    /** 已种植的地块明细（begin_actualdate 非空、采摘未开始），四个分支共用的起点。 */
    private PlantDetails plantedDetail() {
        PlantDetails d = new PlantDetails();
        d.setId(11L);
        d.setPlantId(1L);
        d.setPlotId(101L);
        d.setCropId(30L);
        d.setPlantStatus("completed");
        d.setHarvestStatus("pending");
        d.setBeginActualdate(LocalDate.of(2026, 5, 1));
        d.setPlantBy(7L);
        return d;
    }

    /** teamLinkService.detailTeamIds(11) → role=plant 现存班组全集。 */
    private void stubCurrentPlantTeams(Long... teamIds) {
        when(teamLinkService.detailTeamIds(any()))
            .thenReturn(Map.of(11L, Map.of("plant", List.of(teamIds))));
    }

    /**
     * 班组存在性 stub —— adjustPlantedDetail 会先 requireTeamsExist 反查 teamMapper。
     * 不 stub 的话 Mockito 默认返空列表，任何 plantByIds 都会被判「班组不存在」。
     */
    private void stubTeamsExist(Long... teamIds) {
        List<PlantWorkTeam> teams = java.util.Arrays.stream(teamIds).map(id -> {
            PlantWorkTeam t = new PlantWorkTeam();
            t.setId(id);
            return t;
        }).toList();
        when(teamMapper.selectByIds(anyCollection())).thenReturn(teams);
    }

    @Test
    @DisplayName("adjust 分支①改回待种植: 明细回 pending + 清种植/采摘日期 + 清班组 + 地块回空闲")
    void adjustRevertToPending() {
        when(detailsMapper.selectById(11L)).thenReturn(plantedDetail());
        when(detailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(detailsMapper.selectCount(any())).thenReturn(0L);   // 该地块无其它在种明细 → 释放地块
        when(plotMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        PlantDetailAdjustBo bo = new PlantDetailAdjustBo();
        bo.setDetailId(11L);
        bo.setPlantState("pending");

        assertThat(service.adjustPlantedDetail(bo)).isEqualTo(1);

        Map<String, Object> set = captureDetailUpdateSet();
        assertThat(set).containsEntry("plant_status", "pending");
        assertThat(set).containsEntry("begin_actualdate", null);
        assertThat(set).containsEntry("end_actualdate", null);
        assertThat(set).containsEntry("earliest_harvestdate", null);   // 甲方：计划采摘日期调整为空
        assertThat(set).containsEntry("last_harvestdate", null);
        assertThat(set).containsEntry("plant_by", null);
        assertThat(set).containsEntry("change_type", "admin");
        // 种植班组关联清空 + 地块放回空闲（否则 assertPlotsIdle 会把这块地永久锁死）
        verify(teamLinkService).syncDetailTeams(eq(11L), eq("plant"), eq(Collections.emptyList()));
        verify(plotMapper).update(isNull(), any(Wrapper.class));
        verify(planMapper).recalcAggregates(1L);
        verify(planMapper).recalcPlanStatus(1L);
    }

    @Test
    @DisplayName("adjust 分支①拒绝: 采摘已开始的明细不许改回待种植")
    void adjustRevertRejectedWhenPicked() {
        PlantDetails d = plantedDetail();
        d.setHarvestStatus("picking");
        when(detailsMapper.selectById(11L)).thenReturn(d);

        PlantDetailAdjustBo bo = new PlantDetailAdjustBo();
        bo.setDetailId(11L);
        bo.setPlantState("pending");

        assertThatThrownBy(() -> service.adjustPlantedDetail(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已开始采摘");
        verify(detailsMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("adjust 入口拒绝(V6-R38): 采摘中的明细连改日期都不许 —— 挡「弹框开着期间小程序开采」的时序")
    void adjustDateRejectedWhenPicking() {
        PlantDetails d = plantedDetail();
        d.setHarvestStatus("picking");
        when(detailsMapper.selectById(11L)).thenReturn(d);

        PlantDetailAdjustBo bo = new PlantDetailAdjustBo();
        bo.setDetailId(11L);
        bo.setPlantState("planted");
        bo.setBeginActualdate(LocalDate.of(2026, 5, 10));

        assertThatThrownBy(() -> service.adjustPlantedDetail(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已开始采摘");
        verify(detailsMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("adjust 入口拒绝(V6-R38): 已有实际产量的明细连仅改班组都不许")
    void adjustTeamRejectedWhenHasYield() {
        PlantDetails d = plantedDetail();
        d.setActualYield(new BigDecimal("12.500"));
        when(detailsMapper.selectById(11L)).thenReturn(d);

        PlantDetailAdjustBo bo = new PlantDetailAdjustBo();
        bo.setDetailId(11L);
        bo.setPlantState("planted");
        bo.setPlantByIds(List.of(9L));

        assertThatThrownBy(() -> service.adjustPlantedDetail(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已开始采摘");
        verify(detailsMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("adjust 分支②改种植日期: 回写 begin_actualdate + 按 crop cycle 重算采摘窗口 + change_type=后台调整")
    void adjustChangePlantDate() {
        when(detailsMapper.selectById(11L)).thenReturn(plantedDetail());
        when(detailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(cropMapper.selectById(30L)).thenReturn(mockCrop(30L, 60, 90, new BigDecimal("2000.000")));
        stubCurrentPlantTeams(7L);
        stubTeamsExist(7L);

        PlantDetailAdjustBo bo = new PlantDetailAdjustBo();
        bo.setDetailId(11L);
        bo.setPlantState("planted");
        bo.setBeginActualdate(LocalDate.of(2026, 5, 10));   // 5/1 → 5/10
        bo.setPlantByIds(List.of(7L));                      // 班组没动

        assertThat(service.adjustPlantedDetail(bo)).isEqualTo(1);

        Map<String, Object> set = captureDetailUpdateSet();
        assertThat(set).containsEntry("begin_actualdate", LocalDate.of(2026, 5, 10));
        assertThat(set).containsEntry("earliest_harvestdate", LocalDate.of(2026, 7, 9));    // 5/10 + 60d
        assertThat(set).containsEntry("last_harvestdate", LocalDate.of(2026, 8, 8));        // 5/10 + 90d
        assertThat(set).containsEntry("change_type", "admin");
        assertThat(set).containsKey("update_by");
        // 采摘窗口变了 → 主表聚合重算；状态没变，不重算 plant_status
        verify(planMapper).recalcAggregates(1L);
        verify(planMapper, never()).recalcPlanStatus(any());
    }

    @Test
    @DisplayName("adjust 分支③仅改班组: 只动 plant_by + 中间表，不碰日期/采摘窗口，change_type=后台班组调整")
    void adjustChangeTeamOnly() {
        when(detailsMapper.selectById(11L)).thenReturn(plantedDetail());
        when(detailsMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        stubCurrentPlantTeams(7L);
        stubTeamsExist(8L, 9L);

        PlantDetailAdjustBo bo = new PlantDetailAdjustBo();
        bo.setDetailId(11L);
        bo.setPlantState("planted");
        bo.setBeginActualdate(LocalDate.of(2026, 5, 1));   // 与库里一致 = 没改
        bo.setPlantByIds(List.of(8L, 9L));                 // 班组改了

        assertThat(service.adjustPlantedDetail(bo)).isEqualTo(1);

        Map<String, Object> set = captureDetailUpdateSet();
        assertThat(set).containsEntry("plant_by", 8L);              // 旧单列 = 多选第一个
        assertThat(set).containsEntry("change_type", "admin_team");
        assertThat(set.keySet()).doesNotContain("begin_actualdate", "earliest_harvestdate", "last_harvestdate", "plant_status");
        verify(teamLinkService).syncDetailTeams(eq(11L), eq("plant"), eq(List.of(8L, 9L)));
        // 仅改班组不影响任何派生聚合
        verify(planMapper, never()).recalcAggregates(any());
        verify(planMapper, never()).recalcPlanStatus(any());
    }

    @Test
    @DisplayName("adjust 分支④三项都没变: 直接返 0，不写库、不留任何变更痕迹")
    void adjustNoChangeIsNoop() {
        when(detailsMapper.selectById(11L)).thenReturn(plantedDetail());
        stubCurrentPlantTeams(7L);
        stubTeamsExist(7L);

        PlantDetailAdjustBo bo = new PlantDetailAdjustBo();
        bo.setDetailId(11L);
        bo.setPlantState("planted");
        bo.setBeginActualdate(LocalDate.of(2026, 5, 1));   // 同库
        bo.setPlantByIds(List.of(7L));                     // 同库

        assertThat(service.adjustPlantedDetail(bo)).isEqualTo(0);

        verify(detailsMapper, never()).update(isNull(), any(Wrapper.class));
        verify(teamLinkService, never()).syncDetailTeams(any(), any(), any());
        verify(plotMapper, never()).update(isNull(), any(Wrapper.class));
        verify(planMapper, never()).recalcAggregates(any());
        verify(planMapper, never()).recalcPlanStatus(any());
    }

    @Test
    @DisplayName("adjust 入口门槛: 未种植（begin_actualdate 空）的明细拒绝后台调整")
    void adjustRejectsNotPlantedDetail() {
        PlantDetails d = plantedDetail();
        d.setBeginActualdate(null);
        when(detailsMapper.selectById(11L)).thenReturn(d);

        PlantDetailAdjustBo bo = new PlantDetailAdjustBo();
        bo.setDetailId(11L);
        bo.setPlantState("planted");

        assertThatThrownBy(() -> service.adjustPlantedDetail(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("尚未种植");
    }

    /**
     * 抓 adjustPlantedDetail 里唯一那次明细 update，把 SET 段还原成「列名 → 实际写入值」。
     *
     * <p>MP 的 {@code getSqlSet()} 只给出 {@code col=#{ew.paramNameValuePairs.MPGENVALn}} 占位符，
     * 真值在 {@code getParamNameValuePairs()} 里；直接断言字符串会把「写 null」和「写某值」混为一谈
     * （清空日期这条正是要断言值确实是 null），故在这里配对还原。</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> captureDetailUpdateSet() {
        ArgumentCaptor<LambdaUpdateWrapper<PlantDetails>> cap = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(detailsMapper).update(isNull(), cap.capture());
        LambdaUpdateWrapper<PlantDetails> w = cap.getValue();
        Map<String, Object> pairs = w.getParamNameValuePairs();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (String seg : w.getSqlSet().split(",")) {
            int eq = seg.indexOf('=');
            String col = seg.substring(0, eq).trim();
            String expr = seg.substring(eq + 1).trim();
            String prefix = "#{ew.paramNameValuePairs.";
            result.put(col, expr.startsWith(prefix)
                ? pairs.get(expr.substring(prefix.length(), expr.length() - 1))
                : expr);
        }
        return result;
    }

    // ============================================================
    // helpers
    // ============================================================

    private CropInfo mockCrop(Long id, Integer minCycle, Integer maxCycle, BigDecimal predictedPer) {
        CropInfo c = new CropInfo();
        c.setId(id);
        c.setCropName("白菜-" + id);
        c.setMinCycle(minCycle);
        c.setMaxCycle(maxCycle);
        c.setPredictedPer(predictedPer);
        return c;
    }

    private PlotInfo mockPlot(Long id, BigDecimal area) {
        PlotInfo p = new PlotInfo();
        p.setId(id);
        p.setPlotName("地块-" + id);
        p.setPlotCode("P" + id);
        p.setPlotArea(area);
        p.setZoneId(1L);
        return p;
    }

    private PlantDetailInputBo mockInput(Long plotId, Integer month, String period) {
        PlantDetailInputBo bo = new PlantDetailInputBo();
        bo.setPlotId(plotId);
        bo.setPlantMonth(month);
        bo.setPlantPeriod(period);
        return bo;
    }
}
