package org.dromara.djs.plant.pick.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.pick.domain.bo.PickAdjustBatchBo;
import org.dromara.djs.plant.pick.domain.bo.PickDetailAdjustBo;
import org.dromara.djs.plant.pick.domain.query.PickPlanQuery;
import org.dromara.djs.plant.pick.domain.vo.PickPlanGroupVo;
import org.dromara.djs.plant.pick.mapper.PickPlanMapper;
import org.dromara.djs.plant.pick.service.IPickPlanService;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.domain.vo.PlantDetailsVo;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采摘计划 Service 实现（PLT-PLAN-002）。
 *
 * <p>核心职责（doc/10 §F-PLT-05）：</p>
 * <ol>
 *   <li>{@link #listByCrop(PickPlanQuery)}：admin 列表，按作物聚合 plant_details（对齐原型按作物维度）</li>
 *   <li>{@link #listDetailsByPlanCrop(Long, Long)}：admin 调整页 step1，拉行</li>
 *   <li>{@link #adjustDetails(PickAdjustBatchBo)}：admin 调整页 step2，批量 UPDATE 4 时间字段 + is_pick + harvest_by</li>
 * </ol>
 *
 * <p>本 Service **不** extends DjsBaseServiceImpl（无独立主表 entity；纯查询 + UPDATE 操作 plant_details）。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PickPlanServiceImpl implements IPickPlanService {

    private final PickPlanMapper pickPlanMapper;
    private final PlantDetailsMapper detailsMapper;
    private final CropInfoMapper cropMapper;
    private final PlotInfoMapper plotMapper;
    private final PlantWorkTeamMapper teamMapper;
    private final ImageUrlResolver imageUrlResolver;

    /** 作物 L2 默认图统一走果蔬（IMG-LIB-001）。 */
    private static final String CROP_BELONG_TYPE = "vegetable";

    @Override
    public List<PickPlanGroupVo> listByCrop(PickPlanQuery query) {
        String tenantId = currentTenantSafe();
        PickPlanQuery q = (query == null) ? new PickPlanQuery() : query;
        List<PickPlanGroupVo> rows = pickPlanMapper.aggregateByCrop(
            tenantId,
            LocalDate.now().getYear(),
            q.getCropId(),
            q.getHarvestStatus()
        );
        enrichCropImage(rows);
        return rows;
    }

    /** 作物主图：L1 image_oss_id → resolver 4 层兜底转 public URL，批量禁 N+1。 */
    private void enrichCropImage(List<PickPlanGroupVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        List<ImageUrlResolver.Item> items = rows.stream()
            .map(v -> new ImageUrlResolver.Item(v.getImageOssId(), CROP_BELONG_TYPE))
            .toList();
        List<String> urls = imageUrlResolver.resolveList(items);
        if (urls.size() == rows.size()) {
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).setCropImageUrl(urls.get(i));
            }
        }
    }

    @Override
    public List<PlantDetailsVo> listDetailsByPlanCrop(Long planId, Long cropId) {
        if (planId == null || cropId == null) {
            throw new ServiceException("planId / cropId 必填");
        }
        List<PlantDetailsVo> details = detailsMapper.selectVoList(
            buildDetailsQuery(planId, cropId));
        enrich(details);
        return details;
    }

    @Override
    public List<PlantDetailsVo> listDetailsByCrop(Long cropId) {
        if (cropId == null) {
            throw new ServiceException("cropId 必填");
        }
        List<PlantDetailsVo> details = detailsMapper.selectVoList(
            buildDetailsQuery(null, cropId));
        enrich(details);
        return details;
    }

    /**
     * 调整页拉行公共 query 片段：按 cropId 过滤（必），planId 可选（为空则跨计划聚合）。
     *
     * @param planId 计划 id（null = 不拼 planId 过滤，跨计划聚合）
     * @param cropId 作物 id（必填，调用方已校验）
     */
    private LambdaQueryWrapper<PlantDetails> buildDetailsQuery(Long planId, Long cropId) {
        return new LambdaQueryWrapper<PlantDetails>()
            .eq(planId != null, PlantDetails::getPlantId, planId)
            .eq(PlantDetails::getCropId, cropId)
            .orderByAsc(PlantDetails::getPlantMonth)
            .orderByAsc(PlantDetails::getPlantPeriod);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int adjustDetails(PickAdjustBatchBo bo) {
        if (bo == null || CollUtil.isEmpty(bo.getRows())) {
            return 0;
        }
        Set<Long> ids = bo.getRows().stream()
            .map(PickDetailAdjustBo::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return 0;
        }
        // 安全校验：所有 row.id 必须属于 (plantId, cropId)，并一次性取出现有行用于「发布必填」校验
        List<PlantDetails> owned = detailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .in(PlantDetails::getId, ids)
                .eq(PlantDetails::getPlantId, bo.getPlantId())
                .eq(PlantDetails::getCropId, bo.getCropId()));
        if (owned.size() != ids.size()) {
            throw new ServiceException("调整行不全属于指定计划/作物，已拒绝（防越权）");
        }
        Map<Long, PlantDetails> ownedMap = owned.stream()
            .collect(Collectors.toMap(PlantDetails::getId, d -> d, (a, b) -> a));

        Long updateBy = currentUserIdSafe();
        Date now = new Date();
        int updated = 0;
        for (PickDetailAdjustBo row : bo.getRows()) {
            if (row.getIsPick() != null && row.getIsPick() != 1 && row.getIsPick() != 2) {
                throw new ServiceException("is_pick 仅允许 1 或 2（明细 id=" + row.getId() + "）");
            }
            // D3：采摘班组发布必填（源头杜绝 mp 空 picker）。
            // 普通采收明细（effective is_pick=2）调整后必须有 harvest_by；游客采摘活动（is_pick=1）不强制。
            PlantDetails existing = ownedMap.get(row.getId());
            Integer effectiveIsPick = row.getIsPick() != null ? row.getIsPick()
                : (existing != null ? existing.getIsPick() : null);
            Long effectiveHarvestBy = row.getHarvestBy() != null ? row.getHarvestBy()
                : (existing != null ? existing.getHarvestBy() : null);
            boolean isGuestPick = effectiveIsPick != null && effectiveIsPick == 1;
            if (!isGuestPick && effectiveHarvestBy == null) {
                throw new ServiceException("请指派采摘班组后再发布（明细 id=" + row.getId() + "）");
            }

            // 「设置采摘计划」modal：用户显式给定开始/结束采摘日期（earliest/last_harvestdate）。
            //   - 两端都给：直接采用（结束 ≥ 开始 校验）。
            //   - 只给开始：计划最晚按作物采摘周期窗口（创建时固化 = 原 last-earliest 天数）由最早派生重算。
            // admin 不接收实际采摘起止（begin/end_harvestdate）—— 那由小程序采收录入回写。
            LocalDate newEarliest = row.getEarliestHarvestdate();
            LocalDate newLast;
            if (row.getLastHarvestdate() != null) {
                newLast = row.getLastHarvestdate();
                LocalDate effEarliest = newEarliest != null ? newEarliest
                    : (existing != null ? existing.getEarliestHarvestdate() : null);
                if (effEarliest != null && newLast.isBefore(effEarliest)) {
                    throw new ServiceException("结束采摘日期不得早于开始采摘日期（明细 id=" + row.getId() + "）");
                }
            } else if (newEarliest != null) {
                long windowDays = 0L;
                if (existing != null && existing.getEarliestHarvestdate() != null && existing.getLastHarvestdate() != null) {
                    windowDays = java.time.temporal.ChronoUnit.DAYS.between(
                        existing.getEarliestHarvestdate(), existing.getLastHarvestdate());
                    if (windowDays < 0) {
                        windowDays = 0L;
                    }
                }
                newLast = newEarliest.plusDays(windowDays);
            } else {
                newLast = null;
            }

            updated += detailsMapper.update(null,
                Wrappers.<PlantDetails>update()
                    .eq("id", row.getId())
                    .eq("del_flag", "0")
                    .set(newEarliest != null, "earliest_harvestdate", newEarliest)
                    .set(newLast     != null, "last_harvestdate",     newLast)
                    .set(row.getIsPick()    != null, "is_pick",     row.getIsPick())
                    .set(row.getHarvestBy() != null, "harvest_by",  row.getHarvestBy())
                    .set("update_by", updateBy)
                    .set("update_time", now));
        }
        return updated;
    }

    // ============================================================
    // enrich：plot_name / crop_name / harvest_team_name 批量补
    // ============================================================
    private void enrich(List<PlantDetailsVo> details) {
        if (CollUtil.isEmpty(details)) {
            return;
        }
        Set<Long> plotIds = details.stream().map(PlantDetailsVo::getPlotId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> cropIds = details.stream().map(PlantDetailsVo::getCropId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> teamIds = new HashSet<>();
        details.forEach(d -> {
            if (d.getPlantBy() != null) { teamIds.add(d.getPlantBy()); }
            if (d.getHarvestBy() != null) { teamIds.add(d.getHarvestBy()); }
        });

        Map<Long, PlotInfo> plotMap = plotIds.isEmpty() ? Map.of()
            : plotMapper.selectByIds(plotIds).stream().collect(Collectors.toMap(PlotInfo::getId, p -> p, (a, b) -> a));
        Map<Long, String> cropMap = cropIds.isEmpty() ? Map.of()
            : cropMapper.selectByIds(cropIds).stream().collect(Collectors.toMap(CropInfo::getId, CropInfo::getCropName, (a, b) -> a));
        Map<Long, String> teamMap = teamIds.isEmpty() ? Map.of()
            : teamMapper.selectByIds(teamIds).stream().collect(Collectors.toMap(PlantWorkTeam::getId, PlantWorkTeam::getTeamName, (a, b) -> a));

        for (PlantDetailsVo d : details) {
            PlotInfo plot = plotMap.get(d.getPlotId());
            if (plot != null) {
                d.setPlotName(plot.getPlotName());
                d.setPlotCode(plot.getPlotCode());
            }
            d.setCropName(cropMap.get(d.getCropId()));
            d.setPlantTeamName(d.getPlantBy() == null ? null : teamMap.get(d.getPlantBy()));
            d.setHarvestTeamName(d.getHarvestBy() == null ? null : teamMap.get(d.getHarvestBy()));
            // 种植日期 = 实际开始种植日期（采摘计划详情列展示用）
            d.setPlantDate(d.getBeginActualdate());
        }
    }

    private String currentTenantSafe() {
        try {
            String t = LoginHelper.getTenantId();
            return (t != null && !t.isBlank()) ? t : "1001";
        } catch (Exception e) {
            return "1001";
        }
    }

    private Long currentUserIdSafe() {
        try {
            Long id = LoginHelper.getUserId();
            return id != null ? id : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
