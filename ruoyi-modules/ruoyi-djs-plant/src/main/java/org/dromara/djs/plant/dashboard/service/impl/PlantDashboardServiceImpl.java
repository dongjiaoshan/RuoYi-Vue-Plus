package org.dromara.djs.plant.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.plant.dashboard.domain.vo.CropPlantStatItemVo;
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

    /** 进度兜底值（total 为 0 / 无完成时返回 0）。 */
    private static final int PROGRESS_PENDING = 0;

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

        // 块 ① 今日工作（固定 6 格：种植 / 采摘 / 空地管理 / 种植管理 / 灾害损失 / 采摘活动）
        vo.setTodayPlantingPlotCount(nz(dashboardMapper.countTodayPlanting(tenantId)));
        vo.setTodayHarvestPlotCount(nz(dashboardMapper.countTodayHarvest(tenantId)));
        vo.setTodayIdleMgmtPlotCount(nz(dashboardMapper.countTodayIdleMgmt(tenantId)));
        vo.setTodayPlantMgmtPlotCount(nz(dashboardMapper.countTodayPlantMgmt(tenantId)));
        vo.setTodayDisasterPlotCount(nz(dashboardMapper.countTodayDisaster(tenantId)));
        // 今日采摘活动量 V1 恒 0：原口径取 t_plant_pick_activity SUM(total_yield)，该表已废弃（采摘活动改只读报表，
        // 不再写采摘量明细）；VO 字段保留（前端仍读），直接置 0 避免查废表。
        vo.setTodayPickActivityWeight(BigDecimal.ZERO);

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
     * 组装有机证书一览（区块 ③，果蔬证书口径）。
     *
     * <ul>
     *   <li>子项1 果蔬有机证到期日 = 最新一张果蔬证书到期日（{@code MAX(crop_cert_valid)}）。</li>
     *   <li>子项2 果蔬有机证书到期天数 = {@code DATEDIFF(MAX(crop_cert_valid), CURDATE())}。</li>
     *   <li>子项3 作物无证书品类数 = 在种作物总数 − 在最新证书覆盖品类里的在种作物数。</li>
     *   <li>子项4 果蔬有机证书品类数 = 在种作物中在最新证书覆盖品类里的数量。</li>
     * </ul>
     *
     * @param tenantId 租户
     * @return 证书一览 VO，无证书时到期日/天数 null、计数 0
     */
    private OrganicCertOverviewVo buildOrganicCertOverview(String tenantId) {
        OrganicCertOverviewVo cert = new OrganicCertOverviewVo();

        PlantDashboardMapper.CropCertLatestRow latest = dashboardMapper.selectLatestCropCert(tenantId);
        if (latest == null || latest.getCertDate() == null) {
            cert.setCropCertExpiryDate(null);
            cert.setCropCertDaysToExpiry(null);
        } else {
            cert.setCropCertExpiryDate(latest.getCertDate().toString());
            cert.setCropCertDaysToExpiry(latest.getDaysToExpiry());
        }

        int inCert = nz(dashboardMapper.selectInLatestCertCropCount(tenantId));
        int activeTotal = nz(dashboardMapper.selectActiveCropCount(tenantId));
        cert.setCropCertCategoryCount(inCert);
        cert.setCropNoCertCount(Math.max(activeTotal - inCert, 0));

        return cert;
    }

    @Override
    public List<GanttItemVo> getGantt() {
        String tenantId = currentTenant();
        List<GanttItemVo> items = new ArrayList<>();
        items.addAll(buildPlantGantt(tenantId));
        items.addAll(buildPickGantt(tenantId));
        return items;
    }

    /**
     * 种植甘特段（按 作物 维度聚合）：每个作物一条整段，y 轴类目 = 作物名。
     * 开始 = {@code MIN(实际开始 / 计划开始旬日兜底)}、结束 = {@code MAX(COALESCE(实际结束, 计划最早采摘日))}
     * （SQL 内聚合，旬日兜底口径与原明细维度一致）；进度 = 该作物种植完成明细占比。
     *
     * @param tenantId 租户
     * @return 种植甘特条列表
     */
    private List<GanttItemVo> buildPlantGantt(String tenantId) {
        List<PlantDashboardMapper.PlantGanttCropRow> rows = dashboardMapper.selectPlantGanttByCrop(tenantId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<GanttItemVo> items = new ArrayList<>(rows.size());
        for (PlantDashboardMapper.PlantGanttCropRow row : rows) {
            LocalDate start = row.getStartDate();
            LocalDate end = row.getEndDate() != null ? row.getEndDate() : start;
            // 整段无任何可用开始（无实际且无计划推算）则跳过该作物
            if (start == null) {
                continue;
            }
            // 结束早于开始（计划日历异常）时夹紧为开始日，避免负宽
            if (end.isBefore(start)) {
                end = start;
            }
            String text = row.getCropName() == null || row.getCropName().isBlank()
                ? "未知作物" : row.getCropName();
            String idStr = row.getCropId() == null ? "" : String.valueOf(row.getCropId());
            items.add(buildItem(idStr + "-plant", text, start, end,
                pickRatioProgress(row.getCompletedCount(), row.getTotalCount()), "plant"));
        }
        return items;
    }

    /**
     * 采摘甘特段（按 作物 维度聚合）：每个作物一条整段
     * {@code MIN(begin_harvestdate) .. MAX(end_harvestdate)}，进度 = 该作物已采摘完成明细占比。
     * y 轴类目 = 作物名。
     *
     * @param tenantId 租户
     * @return 采摘甘特条列表
     */
    private List<GanttItemVo> buildPickGantt(String tenantId) {
        List<PlantDashboardMapper.PickGanttCropRow> rows = dashboardMapper.selectPickGanttByCrop(tenantId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<GanttItemVo> items = new ArrayList<>(rows.size());
        for (PlantDashboardMapper.PickGanttCropRow row : rows) {
            LocalDate start = row.getBeginHarvestdate();
            // 整段未结束（无任何 end_harvestdate）时用开始日兜底，至少画出起点
            LocalDate end = row.getEndHarvestdate() != null ? row.getEndHarvestdate() : start;
            if (start == null) {
                continue;
            }
            if (end.isBefore(start)) {
                end = start;
            }
            String text = row.getCropName() == null || row.getCropName().isBlank()
                ? "未知作物" : row.getCropName();
            String idStr = row.getCropId() == null ? "" : String.valueOf(row.getCropId());
            items.add(buildItem(idStr + "-pick", text, start, end,
                pickRatioProgress(row.getCompletedCount(), row.getTotalCount()), "pick"));
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
     * 完成占比进度（作物聚合后）：完成明细占比 = {@code completed / total × 100}（四舍五入 0~100）。
     * 种植段用 {@code plant_status='completed'} 占比、采摘段用 {@code harvest_status='completed'} 占比。
     *
     * @param completed 完成明细数（可空）
     * @param total     该作物明细总数（可空 / 0）
     * @return 进度 0~100；total 为 0 时返回 0
     */
    private int pickRatioProgress(Integer completed, Integer total) {
        int t = nz(total);
        if (t <= 0) {
            return PROGRESS_PENDING;
        }
        int c = Math.min(nz(completed), t);
        return (int) Math.round(c * 100.0 / t);
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
