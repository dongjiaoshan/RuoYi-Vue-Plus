package org.dromara.djs.plant.overview.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.plant.overview.domain.vo.CropDetailVo;
import org.dromara.djs.plant.overview.domain.vo.CropOverviewCardVo;
import org.dromara.djs.plant.overview.domain.vo.PlantOverviewSummaryVo;
import org.dromara.djs.plant.overview.mapper.PlantOverviewMapper;
import org.dromara.djs.plant.overview.service.IPlantOverviewService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public PlantOverviewSummaryVo getSummary() {
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

        // 作物卡片（kg，service 不换算）
        List<CropOverviewCardVo> crops = overviewMapper.selectCropCards(tenantId);
        vo.setCrops(crops == null ? List.of() : crops);

        return vo;
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
