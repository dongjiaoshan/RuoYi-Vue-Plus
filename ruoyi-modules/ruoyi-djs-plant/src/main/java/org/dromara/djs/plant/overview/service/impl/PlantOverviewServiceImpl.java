package org.dromara.djs.plant.overview.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.plant.overview.domain.vo.CropDetailVo;
import org.dromara.djs.plant.overview.domain.vo.CropOverviewCardVo;
import org.dromara.djs.plant.overview.domain.vo.CropOverviewExportVo;
import org.dromara.djs.plant.overview.domain.vo.PlantOverviewSummaryVo;
import org.dromara.djs.plant.overview.mapper.PlantOverviewMapper;
import org.dromara.djs.plant.overview.service.IPlantOverviewService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 种植总览聚合实现（FIX-PLT-AD-OVERVIEW-001）。
 *
 * <p>纯只读聚合 service（非 {@code DjsBaseServiceImpl}，无 CRUD / softDelete）。所有 null 用
 * 0 / ZERO / 空列表兜底。KPI 单位吨（kg→吨在此换算，决策#7：除以 1000 保留 2 位）；卡片/明细 kg。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-OVERVIEW-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlantOverviewServiceImpl implements IPlantOverviewService {

    private static final String DEFAULT_TENANT = "1001";

    /** kg→吨换算因子。 */
    private static final BigDecimal KG_PER_TON = BigDecimal.valueOf(1000);

    private final PlantOverviewMapper overviewMapper;

    @Override
    public PlantOverviewSummaryVo getSummary(String cropName) {
        String tenantId = currentTenant();
        PlantOverviewSummaryVo vo = new PlantOverviewSummaryVo();

        // 地块状态计数（空地块 / 已种植地块）
        PlantOverviewMapper.PlotCountRow plotCount = overviewMapper.selectPlotCount(tenantId);
        if (plotCount == null) {
            vo.setIdlePlotCount(0);
            vo.setPlantedPlotCount(0);
        } else {
            vo.setIdlePlotCount(nz(plotCount.getIdlePlotCount()));
            vo.setPlantedPlotCount(nz(plotCount.getPlantedPlotCount()));
        }

        // 产量汇总（kg）→ 换算吨
        PlantOverviewMapper.YieldSumRow yieldSum = overviewMapper.selectYieldSum(tenantId);
        BigDecimal harvestedKg = yieldSum == null ? BigDecimal.ZERO : nzBd(yieldSum.getHarvestedKg());
        BigDecimal expectedKg = yieldSum == null ? BigDecimal.ZERO : nzBd(yieldSum.getExpectedKg());
        BigDecimal remainingKg = expectedKg.subtract(harvestedKg);
        if (remainingKg.signum() < 0) {
            remainingKg = BigDecimal.ZERO;
        }
        vo.setHarvestedTotalTon(toTon(harvestedKg));
        vo.setExpectedTotalTon(toTon(expectedKg));
        vo.setRemainingExpectedTon(toTon(remainingKg));

        // 作物卡片（kg，service 不换算）；cropName 只过滤卡片，不影响上面的 KPI
        List<CropOverviewCardVo> crops = overviewMapper.selectCropCards(tenantId, normalizeKeyword(cropName));
        vo.setCrops(crops == null ? List.of() : crops);

        return vo;
    }

    @Override
    public List<CropOverviewExportVo> getCropCardExportList(String cropName) {
        // 与 getSummary 复用同一条查询，保证屏幕卡片与 Excel 同源同过滤
        List<CropOverviewCardVo> cards = overviewMapper.selectCropCards(currentTenant(), normalizeKeyword(cropName));
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        List<CropOverviewExportVo> rows = new ArrayList<>(cards.size());
        for (CropOverviewCardVo c : cards) {
            CropOverviewExportVo row = new CropOverviewExportVo();
            row.setCropName(c.getCropName());
            row.setCropCode(c.getCropCode());
            row.setCompletionRate(completionRate(c));
            row.setPlanPlotCount(nz(c.getPlanPlotCount()));
            row.setPlanArea(nzBd(c.getPlanArea()));
            row.setPlanExpectedYield(nzBd(c.getPlanExpectedYield()));
            row.setDonePlotCount(nz(c.getDonePlotCount()));
            row.setDoneArea(nzBd(c.getDoneArea()));
            row.setDoneHarvestYield(nzBd(c.getDoneHarvestYield()));
            rows.add(row);
        }
        // 与页面 sortedCrops 一致：计划完成率升序；同率保持 mapper 的 crop_name 升序（List.sort 稳定）
        rows.sort(Comparator.comparing(CropOverviewExportVo::getCompletionRate));
        return rows;
    }

    @Override
    public TableDataInfo<CropDetailVo> getCropDetailPage(Long cropId, PageQuery pageQuery) {
        // PageQuery 构造参数顺序为 (pageSize, pageNum)
        PageQuery pq = pageQuery != null ? pageQuery : new PageQuery(20, 1);
        if (cropId == null) {
            return TableDataInfo.build(List.of());
        }
        IPage<CropDetailVo> page = overviewMapper.selectCropDetailPage(pq.build(), currentTenant(), cropId);
        return TableDataInfo.build(page);
    }

    @Override
    public List<CropDetailVo> getCropDetailList(Long cropId) {
        if (cropId == null) {
            return List.of();
        }
        List<CropDetailVo> list = overviewMapper.selectCropDetailList(currentTenant(), cropId);
        return list == null ? List.of() : list;
    }

    @Override
    public String getCropName(Long cropId) {
        if (cropId == null) {
            return "";
        }
        String name = overviewMapper.selectCropName(currentTenant(), cropId);
        return name == null ? "" : name;
    }

    /**
     * 计划完成率（% 数值，2 位小数）：计划地块数 &lt;= 0 时兜底 0.00 防除零。
     *
     * <p>与前端 {@code overview/index.vue completionRate()} 逐字对齐（done/plan*100，HALF_UP 2 位），
     * 避免屏幕与 Excel 差 0.01。</p>
     *
     * @param c 作物卡片
     * @return 完成率百分数
     */
    private BigDecimal completionRate(CropOverviewCardVo c) {
        int plan = nz(c.getPlanPlotCount());
        if (plan <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(nz(c.getDonePlotCount()))
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(plan), 2, RoundingMode.HALF_UP);
    }

    /**
     * 作物名称关键字归一：null/空白 → 空串。
     *
     * <p>{@code selectCropCards} 是裸 {@code @Select}，{@code #{cropName}} 传 null 会因无法推断
     * JdbcType 抛 SQLException，故此处统一兜底成空串（SQL 里空串 = 不过滤）。</p>
     *
     * @param cropName 原始关键字
     * @return 去空白后的关键字，或空串
     */
    private String normalizeKeyword(String cropName) {
        return cropName == null || cropName.isBlank() ? "" : cropName.trim();
    }

    /**
     * kg → 吨（除 1000 保留 2 位，HALF_UP）。
     *
     * @param kg 千克值（非空）
     * @return 吨值
     */
    private BigDecimal toTon(BigDecimal kg) {
        return kg.divide(KG_PER_TON, 2, RoundingMode.HALF_UP);
    }

    /**
     * null → 0。
     */
    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * null → ZERO。
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
            log.warn("[PlantOverview] 获取租户失败，回退默认租户", e);
            return DEFAULT_TENANT;
        }
    }
}
