package org.dromara.djs.plant.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.plant.dashboard.domain.vo.CropPlantStatItemVo;
import org.dromara.djs.plant.dashboard.domain.vo.FarmWorkCountVo;
import org.dromara.djs.plant.dashboard.domain.vo.GanttItemVo;
import org.dromara.djs.plant.dashboard.domain.vo.MonthCompletionItemVo;
import org.dromara.djs.plant.dashboard.domain.vo.OrganicCertOverviewVo;
import org.dromara.djs.plant.dashboard.domain.vo.PlantDashboardSummaryVo;
import org.dromara.djs.plant.dashboard.mapper.PlantDashboardMapper;
import org.dromara.djs.plant.dashboard.service.IPlantDashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 种植看板聚合实现（PLT-DASH-001）。
 *
 * <p>只读聚合 service（非 {@code DjsBaseServiceImpl}，无 CRUD / softDelete）。每块 1~2 个
 * 聚合 query，所有 null 用 0 / ZERO / 空列表兜底。</p>
 *
 * @author djs
 * @since PLT-DASH-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlantDashboardServiceImpl implements IPlantDashboardService {

    private static final String DEFAULT_TENANT = "1001";

    /** 种植状态 → 进度映射（字典 djs_plant_plan_status）。 */
    private static final int PROGRESS_PENDING = 0;
    private static final int PROGRESS_ONGOING = 50;
    private static final int PROGRESS_COMPLETED = 100;
    private static final int PROGRESS_DELAYED = 80;
    /** 采摘进行中（djs_pick_status = picking）进度。 */
    private static final int PROGRESS_PICKING = 50;

    private final PlantDashboardMapper dashboardMapper;

    @Override
    public PlantDashboardSummaryVo getSummary() {
        String tenantId = currentTenant();
        PlantDashboardSummaryVo vo = new PlantDashboardSummaryVo();

        // 块 ① 土地总览
        PlantDashboardMapper.PlotOverviewRow overview = dashboardMapper.selectPlotOverview(tenantId);
        if (overview == null) {
            vo.setIdlePlotCount(0);
            vo.setPlantingPlotCount(0);
            vo.setHarvestingPlotCount(0);
            vo.setTotalPlotCount(0);
            vo.setTotalPlotArea(BigDecimal.ZERO);
        } else {
            vo.setIdlePlotCount(nz(overview.getIdleCount()));
            vo.setPlantingPlotCount(nz(overview.getPlantingCount()));
            vo.setHarvestingPlotCount(nz(overview.getHarvestingCount()));
            vo.setTotalPlotCount(nz(overview.getTotalCount()));
            vo.setTotalPlotArea(nzBd(overview.getTotalArea()));
        }
        vo.setPendingPlotCount(nz(dashboardMapper.countPendingPlot(tenantId)));
        vo.setCurrentPlantingArea(nzBd(dashboardMapper.selectCurrentPlantingArea(tenantId)));
        vo.setCurrentExpectedYield(nzBd(dashboardMapper.selectCurrentExpectedYield(tenantId)));

        // 块 ① 今日农事
        List<FarmWorkCountVo> todayFarmWork = dashboardMapper.selectTodayFarmWork(tenantId);
        vo.setTodayFarmWork(todayFarmWork == null ? List.of() : todayFarmWork);
        vo.setTodayFarmWorkTotal(nz(dashboardMapper.countTodayFarmWorkTotal(tenantId)));

        // 块 ② 当月完成率
        List<MonthCompletionItemVo> monthCompletion = dashboardMapper.selectMonthCompletion(tenantId);
        vo.setMonthCompletion(monthCompletion == null ? List.of() : monthCompletion);

        // 块 ④ 实时种植物统计（by 作物）
        List<CropPlantStatItemVo> cropPlantStat = dashboardMapper.selectCropPlantStat(tenantId);
        vo.setCropPlantStat(cropPlantStat == null ? List.of() : cropPlantStat);

        // 块 ③ 有机证书情况一览
        vo.setOrganicCertOverview(buildOrganicCertOverview(tenantId));

        return vo;
    }

    /**
     * 组装有机证书情况一览（区块 ③）。
     *
     * @param tenantId 租户
     * @return 4 数字 VO，无证书时到期天数 null、计数 0
     */
    private OrganicCertOverviewVo buildOrganicCertOverview(String tenantId) {
        OrganicCertOverviewVo cert = new OrganicCertOverviewVo();
        cert.setPlotCertMinDays(dashboardMapper.selectPlotCertMinDays(tenantId));
        cert.setCropCertMinDays(dashboardMapper.selectCropCertMinDays(tenantId));
        cert.setCropNoCertCount(nz(dashboardMapper.selectCropNoCertCount(tenantId)));
        cert.setCropReservedCount(nz(dashboardMapper.selectCropTotalCount(tenantId)));
        return cert;
    }

    @Override
    public List<GanttItemVo> getGantt() {
        String tenantId = currentTenant();
        List<PlantDashboardMapper.GanttRow> rows = dashboardMapper.selectGanttRows(tenantId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<GanttItemVo> items = new ArrayList<>(rows.size() * 2);
        for (PlantDashboardMapper.GanttRow row : rows) {
            String text = buildText(row.getCropName(), row.getPlotName());
            String idStr = row.getId() == null ? "" : String.valueOf(row.getId());

            // 种植段（绿）：begin/end 都非 null 才画
            if (row.getBeginActualdate() != null && row.getEndActualdate() != null) {
                items.add(buildItem(idStr + "-plant", text,
                    row.getBeginActualdate(), row.getEndActualdate(),
                    plantProgress(row.getPlantStatus()), "plant"));
            }
            // 采摘段（黄）：begin/end 都非 null 才画
            if (row.getBeginHarvestdate() != null && row.getEndHarvestdate() != null) {
                items.add(buildItem(idStr + "-pick", text,
                    row.getBeginHarvestdate(), row.getEndHarvestdate(),
                    pickProgress(row.getHarvestStatus()), "pick"));
            }
        }
        return items;
    }

    /**
     * 组装单个甘特条。
     *
     * @param id       甘特条 id（已含 -plant / -pick 后缀）
     * @param text     作物-地块文本
     * @param start    段开始日期
     * @param end      段结束日期
     * @param progress 进度 0~100
     * @param type     plant / pick
     * @return 甘特条 VO
     */
    private GanttItemVo buildItem(String id, String text, LocalDate start, LocalDate end, int progress, String type) {
        GanttItemVo item = new GanttItemVo();
        item.setId(id);
        item.setText(text);
        item.setStartDate(start);
        item.setEndDate(end);
        item.setProgress(progress);
        item.setType(type);
        return item;
    }

    /**
     * 拼"作物-地块"文本，任一为空用占位。
     *
     * @param cropName 作物名（可空）
     * @param plotName 地块名（可空）
     * @return 文本
     */
    private String buildText(String cropName, String plotName) {
        String crop = cropName == null || cropName.isBlank() ? "未知作物" : cropName;
        String plot = plotName == null || plotName.isBlank() ? "未知地块" : plotName;
        return crop + "-" + plot;
    }

    /**
     * 种植状态 → 进度（字典 djs_plant_plan_status：pending/ongoing/completed/delayed）。
     *
     * @param status 种植状态 code（可空）
     * @return 进度 0~100
     */
    private int plantProgress(String status) {
        if (status == null) {
            return PROGRESS_PENDING;
        }
        return switch (status) {
            case "ongoing" -> PROGRESS_ONGOING;
            case "completed" -> PROGRESS_COMPLETED;
            case "delayed" -> PROGRESS_DELAYED;
            default -> PROGRESS_PENDING;
        };
    }

    /**
     * 采摘状态 → 进度（字典 djs_pick_status：pending/picking/completed/delayed）。
     *
     * @param status 采摘状态 code（可空）
     * @return 进度 0~100
     */
    private int pickProgress(String status) {
        if (status == null) {
            return PROGRESS_PENDING;
        }
        return switch (status) {
            case "picking" -> PROGRESS_PICKING;
            case "completed" -> PROGRESS_COMPLETED;
            case "delayed" -> PROGRESS_DELAYED;
            default -> PROGRESS_PENDING;
        };
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
            log.warn("[PlantDashboard] 获取租户失败，回退默认租户", e);
            return DEFAULT_TENANT;
        }
    }

}
