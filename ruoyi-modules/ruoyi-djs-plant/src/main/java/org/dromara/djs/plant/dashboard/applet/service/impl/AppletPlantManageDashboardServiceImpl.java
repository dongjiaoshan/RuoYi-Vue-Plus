package org.dromara.djs.plant.dashboard.applet.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.plant.dashboard.applet.domain.vo.CropAreaShareVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.MonthYieldRankVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PickRecordVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantAnnualPlanVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantManageOverviewVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantPlanTimelineVo;
import org.dromara.djs.plant.dashboard.applet.domain.vo.PlantRecordVo;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.dashboard.applet.mapper.AppletPlantManageDashboardMapper;
import org.dromara.djs.plant.dashboard.applet.service.IAppletPlantManageDashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link IAppletPlantManageDashboardService} 实现（FIX-MGMT-MP-PLT-001）。
 *
 * <p>只读聚合 service（非 {@code DjsBaseServiceImpl}，无 CRUD / softDelete）。每端点 1~2 个聚合 query，
 * 所有 null 用 0 / ZERO / 空列表兜底；产量 kg→吨 /1000 在此层换算（2 位小数）。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-PLT-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppletPlantManageDashboardServiceImpl implements IAppletPlantManageDashboardService {

    private static final String DEFAULT_TENANT = "1001";

    /** kg → 吨换算分母。 */
    private static final BigDecimal KG_PER_TON = new BigDecimal("1000");

    /** is_pick = 1 → 游客采摘活动。 */
    private static final int PICK_TYPE_ACTIVITY = 1;

    /** 作物 L2 默认图统一走果蔬（IMG-LIB-001，Kevin 拍板作物统一果蔬默认图）。 */
    private static final String CROP_BELONG_TYPE = "vegetable";

    private final AppletPlantManageDashboardMapper dashboardMapper;
    private final ImageUrlResolver imageUrlResolver;

    @Override
    public PlantManageOverviewVo getOverview() {
        String tenantId = currentTenant();
        PlantManageOverviewVo vo = new PlantManageOverviewVo();

        AppletPlantManageDashboardMapper.PlotCountRow plot = dashboardMapper.selectPlotCount(tenantId);
        if (plot == null) {
            vo.setTotalPlotCount(0);
            vo.setPlantingPlotCount(0);
            vo.setIdlePlotCount(0);
        } else {
            vo.setTotalPlotCount(nz(plot.getTotalPlotCount()));
            vo.setPlantingPlotCount(nz(plot.getPlantingPlotCount()));
            vo.setIdlePlotCount(nz(plot.getIdlePlotCount()));
        }

        vo.setPlantedCropCount(nz(dashboardMapper.countPlantedCrop(tenantId)));
        vo.setCurrentPlantArea(nzBd(dashboardMapper.sumCurrentPlantArea(tenantId)));
        vo.setHarvestableCropCount(nz(dashboardMapper.countHarvestableCrop(tenantId)));
        return vo;
    }

    @Override
    public PlantAnnualPlanVo getAnnualPlan(Integer year) {
        String tenantId = currentTenant();
        int y = resolveYear(year);
        PlantAnnualPlanVo vo = new PlantAnnualPlanVo();

        AppletPlantManageDashboardMapper.AnnualPlanRow row = dashboardMapper.selectAnnualPlan(tenantId, y);
        if (row == null) {
            vo.setPlanArea(BigDecimal.ZERO);
            vo.setPlanCropCount(0);
            vo.setExpectedYieldTon(BigDecimal.ZERO);
            vo.setPlantedArea(BigDecimal.ZERO);
            vo.setPlantedCropCount(0);
            vo.setHarvestedYieldTon(BigDecimal.ZERO);
            return vo;
        }
        vo.setPlanArea(nzBd(row.getPlanArea()));
        vo.setPlanCropCount(nz(row.getPlanCropCount()));
        vo.setExpectedYieldTon(toTon(row.getExpectedYieldKg()));
        vo.setPlantedArea(nzBd(row.getPlantedArea()));
        vo.setPlantedCropCount(nz(row.getPlantedCropCount()));
        vo.setHarvestedYieldTon(toTon(row.getHarvestedYieldKg()));
        return vo;
    }

    @Override
    public List<CropAreaShareVo> getCropAreaShare() {
        List<CropAreaShareVo> list = dashboardMapper.selectCropAreaShare(currentTenant());
        return list == null ? List.of() : list;
    }

    @Override
    public List<MonthYieldRankVo> getYieldRank3m() {
        List<MonthYieldRankVo> list = dashboardMapper.selectYieldRank3m(currentTenant());
        return list == null ? List.of() : list;
    }

    @Override
    public List<PlantPlanTimelineVo> getPlanTimeline(Integer year) {
        String tenantId = currentTenant();
        int y = resolveYear(year);
        return buildTimeline(
            dashboardMapper.selectPlanMonthGroups(tenantId, y),
            dashboardMapper.selectPlanMonthCrops(tenantId, y));
    }

    @Override
    public TableDataInfo<PlantRecordVo> getPlanRecords(Integer month, Long cropId, PageQuery pageQuery) {
        // PageQuery 构造参数顺序为 (pageSize, pageNum)
        PageQuery pq = pageQuery != null ? pageQuery : new PageQuery(10, 1);
        IPage<PlantRecordVo> page = dashboardMapper.selectPlanRecordsPage(pq.build(), currentTenant(), month, cropId);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PlantPlanTimelineVo> getPickTimeline(Integer year) {
        String tenantId = currentTenant();
        int y = resolveYear(year);
        return buildTimeline(
            dashboardMapper.selectPickMonthGroups(tenantId, y),
            dashboardMapper.selectPickMonthCrops(tenantId, y));
    }

    @Override
    public TableDataInfo<PickRecordVo> getPickRecords(Integer month, Long cropId, Integer pickType, PageQuery pageQuery) {
        // PageQuery 构造参数顺序为 (pageSize, pageNum)
        PageQuery pq = pageQuery != null ? pageQuery : new PageQuery(10, 1);
        IPage<AppletPlantManageDashboardMapper.PickRecordRow> page =
            dashboardMapper.selectPickRecordsPage(pq.build(), currentTenant(), month, cropId, pickType);

        List<PickRecordVo> rows = new ArrayList<>(page.getRecords().size());
        for (AppletPlantManageDashboardMapper.PickRecordRow r : page.getRecords()) {
            PickRecordVo vo = new PickRecordVo();
            vo.setDate(r.getDate());
            vo.setPickType(pickTypeLabel(r.getIsPick()));
            vo.setCropName(r.getCropName());
            vo.setPlotCode(r.getPlotCode());
            vo.setWeightKg(nzBd(r.getWeightKg()));
            vo.setEmployee(r.getEmployee());
            rows.add(vo);
        }
        return new TableDataInfo<>(rows, page.getTotal());
    }

    /**
     * 把月度汇总行 + 月度作物行组装成时间轴（按月分组，crops 数组挂到对应月）。
     *
     * @param groupRows 每月汇总行（可空）
     * @param cropRows  每月各作物行（可空）
     * @return 月度时间轴列表，无则空列表
     */
    private List<PlantPlanTimelineVo> buildTimeline(List<AppletPlantManageDashboardMapper.MonthGroupRow> groupRows,
                                                    List<AppletPlantManageDashboardMapper.MonthCropRow> cropRows) {
        if (groupRows == null || groupRows.isEmpty()) {
            return List.of();
        }
        // 月份 → VO（保持 SQL 已排序的月份顺序）
        Map<Integer, PlantPlanTimelineVo> byMonth = new LinkedHashMap<>();
        for (AppletPlantManageDashboardMapper.MonthGroupRow g : groupRows) {
            PlantPlanTimelineVo vo = new PlantPlanTimelineVo();
            vo.setMonth(g.getMonth());
            vo.setCropCount(nz(g.getCropCount()));
            vo.setPlotCount(nz(g.getPlotCount()));
            vo.setTotalArea(nzBd(g.getTotalArea()));
            vo.setCrops(new ArrayList<>());
            byMonth.put(g.getMonth(), vo);
        }
        if (cropRows != null) {
            for (AppletPlantManageDashboardMapper.MonthCropRow c : cropRows) {
                PlantPlanTimelineVo parent = byMonth.get(c.getMonth());
                if (parent == null) {
                    continue;
                }
                PlantPlanTimelineVo.CropItem item = new PlantPlanTimelineVo.CropItem();
                item.setCropName(c.getCropName());
                // cropImg 此处暂存作物 image_oss_id（SQL 取的是 c.image_oss_id），下面统一走 resolver
                item.setCropImg(c.getCropImg());
                item.setPlotCount(nz(c.getPlotCount()));
                item.setArea(nzBd(c.getArea()));
                parent.getCrops().add(item);
            }
        }
        List<PlantPlanTimelineVo> result = new ArrayList<>(byMonth.values());
        fillCropImageUrls(result);
        return result;
    }

    /**
     * 批量回填时间轴各月作物卡 cropImg（IMG-LIB-001 4 层 resolver，禁 N+1）。
     *
     * <p>cropImg 入参当前承载作物 {@code image_oss_id}（L1），统一兜底作物默认图（L2 vegetable）→
     * 全局默认图（L3），转成 public URL 后回写同字段。</p>
     *
     * @param timeline 时间轴列表（其 {@code crops} 内每项 cropImg = image_oss_id）
     */
    private void fillCropImageUrls(List<PlantPlanTimelineVo> timeline) {
        List<PlantPlanTimelineVo.CropItem> items = new ArrayList<>();
        for (PlantPlanTimelineVo month : timeline) {
            if (month.getCrops() != null) {
                items.addAll(month.getCrops());
            }
        }
        if (items.isEmpty()) {
            return;
        }
        List<ImageUrlResolver.Item> resolveItems = items.stream()
            .map(it -> new ImageUrlResolver.Item(it.getCropImg(), CROP_BELONG_TYPE))
            .toList();
        List<String> urls = imageUrlResolver.resolveList(resolveItems);
        if (urls.size() != items.size()) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setCropImg(urls.get(i));
        }
    }

    /**
     * is_pick → 采摘类型中文。
     *
     * @param isPick 1=活动 / 2=基地（其余 / null → 基地）
     * @return 中文标签
     */
    private String pickTypeLabel(Integer isPick) {
        return isPick != null && isPick == PICK_TYPE_ACTIVITY ? "活动" : "基地";
    }

    /**
     * kg → 吨（/1000，2 位小数 HALF_UP）。
     *
     * @param kg 千克（可空）
     * @return 吨，null → ZERO
     */
    private BigDecimal toTon(BigDecimal kg) {
        if (kg == null) {
            return BigDecimal.ZERO;
        }
        return kg.divide(KG_PER_TON, 2, RoundingMode.HALF_UP);
    }

    /**
     * 解析年份：空 → 当前年。
     *
     * @param year 入参年份（可空）
     * @return 有效年份
     */
    private int resolveYear(Integer year) {
        return year == null ? LocalDate.now().getYear() : year;
    }

    /**
     * null → 0。
     *
     * @param v 可空整数
     * @return 非空整数
     */
    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * null → ZERO。
     *
     * @param v 可空 BigDecimal
     * @return 非空 BigDecimal
     */
    private BigDecimal nzBd(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 取当前租户；V1 单租户场景或异常时回退 {@value #DEFAULT_TENANT}。
     *
     * @return 当前租户 ID
     */
    private String currentTenant() {
        try {
            String t = TenantHelper.getTenantId();
            return t == null || t.isEmpty() ? DEFAULT_TENANT : t;
        } catch (Exception e) {
            log.warn("[AppletPlantManageDashboard] 获取租户失败，回退默认租户", e);
            return DEFAULT_TENANT;
        }
    }

}
