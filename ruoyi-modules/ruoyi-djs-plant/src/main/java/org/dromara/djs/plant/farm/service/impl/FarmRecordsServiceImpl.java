package org.dromara.djs.plant.farm.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.activity.service.IPlantActivityService;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.farm.domain.FarmRecords;
import org.dromara.djs.plant.farm.domain.bo.DisasterBatchBo;
import org.dromara.djs.plant.farm.domain.bo.DisasterRecordBo;
import org.dromara.djs.plant.farm.domain.bo.EmptyRecordBo;
import org.dromara.djs.plant.farm.domain.bo.GrowBatchBo;
import org.dromara.djs.plant.farm.domain.bo.GrowRecordBo;
import org.dromara.djs.plant.farm.domain.bo.HarvestWeightBo;
import org.dromara.djs.plant.farm.domain.bo.PlotPickStatusBo;
import org.dromara.djs.plant.farm.domain.bo.RotationRecordBo;
import org.dromara.djs.plant.farm.domain.bo.TransplantFinalizeBo;
import org.dromara.djs.plant.farm.domain.bo.TransplantRecordBo;
import org.dromara.djs.plant.farm.domain.query.FarmRecordsQuery;
import org.dromara.djs.plant.farm.domain.vo.DispatchSummaryVo;
import org.dromara.djs.plant.farm.domain.vo.FarmCropPlotVo;
import org.dromara.djs.plant.farm.domain.vo.CropZoneCountVo;
import org.dromara.djs.plant.farm.domain.vo.FarmCropTargetCardVo;
import org.dromara.djs.plant.farm.domain.vo.FarmRecordsVo;
import org.dromara.djs.plant.farm.mapper.FarmRecordsMapper;
import org.dromara.djs.plant.farm.service.IFarmRecordsService;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plan.service.IPlantPlanService;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.zone.domain.PlotZone;
import org.dromara.djs.plant.zone.mapper.PlotZoneMapper;
import org.dromara.djs.plant.team.domain.PlantWorkPeople;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.mapper.PlantWorkPeopleMapper;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 农事记录 Service 实现（PLT-WORK-001）。
 *
 * <p>5 类提交统一走 {@link #buildBase} 装填基础字段（record_no / plot_type / crop_name / proof / remark），
 * 再各自补 type-specific 字段。灾害和退茬有副作用：</p>
 *
 * <ul>
 *   <li>{@link #submitDisaster}：累加 {@code plant_details.loss_yield}（按 plantId + plotId + cropId 定位最新一条 details）</li>
 *   <li>{@link #submitRotation}：置 {@code plot_info.plot_status=1}（plant_status='completed' + end_actualdate 归「种植完成」finishPlant 独占，退茬不写）</li>
 * </ul>
 *
 * <p>业务码 {@code record_no} 格式 {@code FRyyyyMMddNNNN}（NNNN 当日 4 位序号，inline mapper.selectMaxRecordNoByPrefix）。
 * 序号锁通过 record_no UNIQUE 在 DB 层兜底（并发场景下后写者拿 SQLIntegrityConstraintViolationException → service 上抛
 * ServiceException 由 mp 重试）。V1 单农场 mp 并发量极低，不引入 Redisson 分布式锁（与 PlantPlanServiceImpl 同范式）。</p>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Slf4j
@Service
public class FarmRecordsServiceImpl extends DjsBaseServiceImpl<FarmRecordsMapper, FarmRecords>
    implements IFarmRecordsService {

    private static final DateTimeFormatter DATE_NUMERIC = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 损失率预警阈值（≥30% 自动置 isWarning=1）。 */
    private static final BigDecimal WARNING_LOSS_RATE = new BigDecimal("30");

    /** 12 类农事 farm_type 全集，dispatchSummary onShow 用。 */
    private static final List<String> FARM_TYPES_12 = List.of(
        "tillage_break", "tillage_prepare", "fertilize",
        "transplant", "water_fertilize", "irrigation", "weed", "pest_control", "pruning", "rotation",
        "disaster", "harvest_activity"
    );

    /** 作物图 belongType（与 CropInfoServiceImpl 默认图口径一致）。 */
    private static final String CROP_BELONG_TYPE = "vegetable";

    private final PlotInfoMapper plotInfoMapper;
    private final PlotZoneMapper plotZoneMapper;
    private final CropInfoMapper cropInfoMapper;
    private final PlantDetailsMapper plantDetailsMapper;
    private final PlantWorkTeamMapper teamMapper;
    private final PlantWorkPeopleMapper peopleMapper;
    private final ImageUrlResolver imageUrlResolver;
    private final IPlantActivityService plantActivityService;
    private final org.dromara.djs.plant.team.service.PlantTeamLinkService teamLinkService;
    private final IPlantPlanService plantPlanService;

    public FarmRecordsServiceImpl(FarmRecordsMapper baseMapper,
                                  PlotInfoMapper plotInfoMapper,
                                  PlotZoneMapper plotZoneMapper,
                                  CropInfoMapper cropInfoMapper,
                                  PlantDetailsMapper plantDetailsMapper,
                                  PlantWorkTeamMapper teamMapper,
                                  PlantWorkPeopleMapper peopleMapper,
                                  ImageUrlResolver imageUrlResolver,
                                  IPlantActivityService plantActivityService,
                                  org.dromara.djs.plant.team.service.PlantTeamLinkService teamLinkService,
                                  IPlantPlanService plantPlanService) {
        super(baseMapper);
        this.plotInfoMapper = plotInfoMapper;
        this.plotZoneMapper = plotZoneMapper;
        this.cropInfoMapper = cropInfoMapper;
        this.plantDetailsMapper = plantDetailsMapper;
        this.teamMapper = teamMapper;
        this.peopleMapper = peopleMapper;
        this.imageUrlResolver = imageUrlResolver;
        this.plantActivityService = plantActivityService;
        this.teamLinkService = teamLinkService;
        this.plantPlanService = plantPlanService;
    }

    /**
     * 处理班组多选取值（G1-TEAMS-MULTISELECT，row37）：list 非空 → 去空去重；否则回落单值。
     */
    private List<Long> effFarmTeams(java.util.List<Long> ids, Long single) {
        if (CollUtil.isNotEmpty(ids)) {
            return ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        }
        return single == null ? java.util.Collections.emptyList() : List.of(single);
    }

    /** 旧单列 farm_by = 多选第一个（过渡兼容）；空 → null。 */
    private Long firstFarmTeam(List<Long> eff) {
        return CollUtil.isEmpty(eff) ? null : eff.get(0);
    }

    /**
     * 农事记录班组多选落地：旧单列已由 buildBase 写「第一个」，此处 sync 中间表全集。
     */
    private void applyFarmTeamLinks(Long recordId, java.util.List<Long> farmByIds, Long farmBy) {
        teamLinkService.syncFarmTeams(recordId, effFarmTeams(farmByIds, farmBy));
    }

    // ============================================================
    // 5 类提交
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitEmpty(EmptyRecordBo bo) {
        if (!isEmptyFarmType(bo.getFarmType())) {
            throw new ServiceException("不支持的空地农事类型: " + bo.getFarmType());
        }
        FarmRecords r = new FarmRecords();
        buildBase(r, bo.getFarmType(), bo.getPlotId(), null, null,
            firstFarmTeam(effFarmTeams(bo.getFarmByIds(), bo.getFarmBy())), bo.getFarmDate(),
            bo.getProofOssIds(), bo.getRemark());
        r.setTillageType(bo.getTillageType());
        r.setTillageMethod(bo.getTillageMethod());
        baseMapper.insert(r);
        applyFarmTeamLinks(r.getId(), bo.getFarmByIds(), bo.getFarmBy());
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitGrow(GrowRecordBo bo) {
        if (!isPlainGrowFarmType(bo.getFarmType())) {
            throw new ServiceException("不支持的生长阶段农事类型: " + bo.getFarmType());
        }
        FarmRecords r = new FarmRecords();
        buildBase(r, bo.getFarmType(), bo.getPlotId(), bo.getCropId(), bo.getPlantId(),
            firstFarmTeam(effFarmTeams(bo.getFarmByIds(), bo.getFarmBy())),
            bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
        r.setOperatorUserId(bo.getOperatorUserId());
        baseMapper.insert(r);
        applyFarmTeamLinks(r.getId(), bo.getFarmByIds(), bo.getFarmBy());
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitDisaster(DisasterRecordBo bo) {
        // 多次录入累计校验（row79）：同批次（crop+plot+plant）历史损失率 + 本次 不得超过 100%。
        requireDisasterLossWithinCap(bo.getCropId(), bo.getPlotId(), bo.getPlantId(), bo.getLossRate());
        FarmRecords r = new FarmRecords();
        buildBase(r, "disaster", bo.getPlotId(), bo.getCropId(), bo.getPlantId(),
            firstFarmTeam(effFarmTeams(bo.getFarmByIds(), bo.getFarmBy())),
            bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
        r.setDisasterType(bo.getDisasterType());
        r.setLossRate(bo.getLossRate());
        // 损失产量由系统按 预计产量 × 损失率/100（保留两位）算出，不信任前端传值
        BigDecimal lossYield = computeLossYield(bo.getPlantId(), bo.getPlotId(), bo.getCropId(), bo.getLossRate());
        r.setLossYield(lossYield);
        r.setIsWarning(bo.getLossRate() != null && bo.getLossRate().compareTo(WARNING_LOSS_RATE) >= 0 ? 1 : 2);
        baseMapper.insert(r);
        applyFarmTeamLinks(r.getId(), bo.getFarmByIds(), bo.getFarmBy());

        // 副作用：累加 plant_details.loss_yield（按 plantId + plotId + cropId 定位未结束 details）
        accumulateLossYield(bo.getPlantId(), bo.getPlotId(), bo.getCropId(), lossYield);
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitTransplant(TransplantRecordBo bo) {
        // 多次累计校验（row12）：历史累计 + 本次 不得超过 100%；本次上限 = 100 − 历史累计。
        // 历史累计与 INSERT 后状态机判定共用 sumTransplantedPercent，避免两处口径漂移。
        int moved = sumTransplantedPercent(bo.getCropId(), bo.getPlotId());
        int percent = bo.getTransplantPercent() == null ? 0 : bo.getTransplantPercent();
        if (moved + percent > 100) {
            throw new ServiceException(
                "本次移栽百分比超出剩余可移 " + (100 - moved) + "%（已累计 " + moved + "%）");
        }
        FarmRecords r = new FarmRecords();
        buildBase(r, "transplant", bo.getPlotId(), bo.getCropId(), bo.getPlantId(),
            firstFarmTeam(effFarmTeams(bo.getFarmByIds(), bo.getFarmBy())),
            bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
        r.setTransplantPlot(bo.getTransplantPlot());
        r.setTransplantPercent(bo.getTransplantPercent());
        baseMapper.insert(r);
        applyFarmTeamLinks(r.getId(), bo.getFarmByIds(), bo.getFarmBy());

        // 累计达 100% → 自动落地（PLT-TRANSPLANT-REDO-001）。本次已 INSERT，重查含本次累计；
        // 仅在「刚好跨过 100%」时触发（previousMoved < 100 ≤ now），幂等：本就 ≥100 应已被 row12 拦掉。
        int now = sumTransplantedPercent(bo.getCropId(), bo.getPlotId());
        if (moved < 100 && now >= 100) {
            applyTransplantCompleteSideEffect(bo.getPlantId(), bo.getCropId(), bo.getPlotId(), bo.getFarmDate());
        }
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int finalizeTransplant(TransplantFinalizeBo bo) {
        // 无移栽记录不能结束移栽（避免误把从未移栽的地块置采摘态）
        int moved = sumTransplantedPercent(bo.getCropId(), bo.getPlotId());
        if (moved <= 0) {
            throw new ServiceException("该地块无移栽记录，无法结束移栽");
        }
        // 幂等：源育苗明细已采摘完成（累计 100% 或前次结束移栽已落地）→ 直接返 0，不重复落地
        boolean landed = plantDetailsMapper.selectCount(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlantId, bo.getPlantId())
                .eq(PlantDetails::getCropId, bo.getCropId())
                .eq(PlantDetails::getPlotId, bo.getPlotId())
                .eq(PlantDetails::getHarvestStatus, "completed")) > 0;
        if (landed) {
            return 0;
        }
        applyTransplantCompleteSideEffect(bo.getPlantId(), bo.getCropId(), bo.getPlotId(), bo.getFarmDate());
        return 1;
    }

    /**
     * 取某 (作物, 源地块) 的累计移栽百分比（移栽校验 + 状态机判定共用，一处定义）。
     *
     * @return 累计已移百分比（无移栽记录返 0）
     */
    private int sumTransplantedPercent(Long cropId, Long plotId) {
        if (cropId == null || plotId == null) {
            return 0;
        }
        return baseMapper.sumTransplantedPercent(cropId, plotId);
    }

    /**
     * 移栽累计达 100%（或手动结束移栽）落地（PLT-TRANSPLANT-REDO-001）。三步，同事务：
     * <ul>
     *   <li>① 目标地块入计划：取该 (作物, 源地块) 的全部转移目标地块（{@code DISTINCT transplant_plot}），
     *       交 {@link IPlantPlanService#materializeTransplantTargets} 为每块建一条「满种」明细
     *       （日期沿用源育苗明细、面积按目标地块、{@code transplant_adjusted=1}），目标地块 {@code plot_status=2}。</li>
     *   <li>② 源育苗明细标采摘完成：{@code harvest_status='completed'} + {@code end_harvestdate=farmDate}
     *       + {@code begin_harvestdate} NULL-safe 回填，where plant_id + crop_id + plot_id（保留明细，供退茬列表命中）。</li>
     *   <li>③ 源育苗地块 {@code plot_status=3}（采摘态/待退茬）——进退茬列表，工作人员退茬后转空地(1)。</li>
     * </ul>
     * 幂等：目标已有本计划明细则跳过（materializeTransplantTargets 内），源置态为绝对 set，重跑无害。
     * 无目标记录（防御）时只做源标完成 + 转退茬。
     */
    private void applyTransplantCompleteSideEffect(Long planId, Long cropId, Long sourcePlotId, LocalDate farmDate) {
        Long updateBy = currentUserSafe();
        // ① 目标地块入计划（满种明细）——plan 模块构建/校验/插入 + 目标 plot_status=2 + recalc
        List<Long> targetPlotIds = baseMapper.selectTransplantTargetPlots(cropId, sourcePlotId);
        if (CollUtil.isNotEmpty(targetPlotIds)) {
            plantPlanService.materializeTransplantTargets(planId, sourcePlotId, cropId, targetPlotIds);
        }
        // ② 源育苗明细标采摘完成（保留明细走退茬）。{0} 为 MP 参数绑定占位（LocalDate），非拼接，无注入面。
        plantDetailsMapper.update(null,
            new LambdaUpdateWrapper<PlantDetails>()
                .eq(PlantDetails::getPlantId, planId)
                .eq(PlantDetails::getCropId, cropId)
                .eq(PlantDetails::getPlotId, sourcePlotId)
                .set(PlantDetails::getHarvestStatus, "completed")
                .set(PlantDetails::getEndHarvestdate, farmDate)
                .setSql("begin_harvestdate = COALESCE(begin_harvestdate, {0})", farmDate)
                .set(PlantDetails::getUpdateBy, updateBy));
        // ③ 源育苗地块转采摘态(3) → 进退茬列表（原直接置空(1)跳过退茬的行为改此）
        plotInfoMapper.update(null,
            new LambdaUpdateWrapper<PlotInfo>()
                .eq(PlotInfo::getId, sourcePlotId)
                .set(PlotInfo::getPlotStatus, 3)
                .set(PlotInfo::getUpdateBy, updateBy));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitRotation(RotationRecordBo bo) {
        // 幂等拦截（231）：地块 plot_status 须为 3（采摘态），已退茬变空地（plot_status=1）的地块拒绝重复退茬。
        PlotInfo plot = plotInfoMapper.selectById(bo.getPlotId());
        if (plot == null) {
            throw new ServiceException("地块不存在: " + bo.getPlotId());
        }
        if (plot.getPlotStatus() == null || plot.getPlotStatus() != 3) {
            throw new ServiceException("该地块已退茬，无需重复退茬");
        }

        FarmRecords r = new FarmRecords();
        buildBase(r, "rotation", bo.getPlotId(), bo.getCropId(), bo.getPlantId(),
            firstFarmTeam(effFarmTeams(bo.getFarmByIds(), bo.getFarmBy())),
            bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
        baseMapper.insert(r);
        applyFarmTeamLinks(r.getId(), bo.getFarmByIds(), bo.getFarmBy());

        // 副作用：plot_info.plot_status 回 1（空闲）。plant_status='completed' + end_actualdate
        // 归「种植完成」finishPlant 独占，退茬不再重复写。
        plot.setPlotStatus(1);
        plotInfoMapper.updateById(plot);
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitGrowBatch(GrowBatchBo bo) {
        String type = bo.getFarmType();
        boolean isRotation = "rotation".equals(type);
        if (!isRotation && !isPlainGrowFarmType(type)) {
            throw new ServiceException("不支持的批量农事类型: " + type);
        }
        int count = 0;
        for (GrowBatchBo.PlotTarget target : bo.getTargets()) {
            FarmRecords r = new FarmRecords();
            buildBase(r, type, target.getPlotId(), target.getCropId(), target.getPlantId(),
                firstFarmTeam(effFarmTeams(bo.getFarmByIds(), bo.getFarmBy())),
                bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
            baseMapper.insert(r);
            applyFarmTeamLinks(r.getId(), bo.getFarmByIds(), bo.getFarmBy());
            count++;
            // 退茬副作用：每条 plot_status=1（空闲）。plant_status/end_actualdate 归「种植完成」独占。
            if (isRotation) {
                applyRotationSideEffect(target.getPlotId());
            }
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitDisasterBatch(DisasterBatchBo bo) {
        int warning = bo.getLossRate() != null && bo.getLossRate().compareTo(WARNING_LOSS_RATE) >= 0 ? 1 : 2;
        // 多次录入累计校验（row79）：整事务前逐地块各自校验（每地块历史累计不同，同一 lossRate 可能部分地块超）。
        // 全部通过才 INSERT，避免先写了一部分地块再撞超限留下半截批量。
        for (DisasterBatchBo.PlotTarget t : bo.getTargets()) {
            requireDisasterLossWithinCap(t.getCropId(), t.getPlotId(), t.getPlantId(), bo.getLossRate());
        }
        int count = 0;
        for (DisasterBatchBo.PlotTarget t : bo.getTargets()) {
            FarmRecords r = new FarmRecords();
            buildBase(r, "disaster", t.getPlotId(), t.getCropId(), t.getPlantId(),
                firstFarmTeam(effFarmTeams(bo.getFarmByIds(), bo.getFarmBy())),
                bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
            r.setDisasterType(bo.getDisasterType());
            r.setLossRate(bo.getLossRate());
            // 各地块损失产量由系统按 该地块预计产量 × 批量损失率/100（保留两位）算出，不信任前端传值
            BigDecimal lossYield = computeLossYield(t.getPlantId(), t.getPlotId(), t.getCropId(), bo.getLossRate());
            r.setLossYield(lossYield);
            r.setIsWarning(warning);
            baseMapper.insert(r);
            applyFarmTeamLinks(r.getId(), bo.getFarmByIds(), bo.getFarmBy());
            count++;
            // 副作用：各地块累加对应 plant_details.loss_yield
            accumulateLossYield(t.getPlantId(), t.getPlotId(), t.getCropId(), lossYield);
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitHarvestWeight(HarvestWeightBo bo) {
        // 1. INSERT 一行 harvest_activity 记录，携带本次 harvest_weight（供记录 tab 展示 作物+重量kg）
        FarmRecords r = new FarmRecords();
        buildBase(r, "harvest_activity", bo.getPlotId(), bo.getCropId(), bo.getPlantId(),
            firstFarmTeam(effFarmTeams(bo.getFarmByIds(), bo.getFarmBy())),
            bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
        r.setHarvestWeight(bo.getHarvestWeight());
        baseMapper.insert(r);
        applyFarmTeamLinks(r.getId(), bo.getFarmByIds(), bo.getFarmBy());
        // 2. 副作用：累加对应 plant_details.actual_yield（#3=a 采摘重量唯一录入口，采收 tab 已去重量）
        accumulateActualYield(bo.getPlantId(), bo.getPlotId(), bo.getCropId(), bo.getHarvestWeight());
        // 3. 副作用：写一行采摘活动流水（t_plant_plant_activity，邓博权威 spec：报表 SUM(daily_weight) GROUP BY crop_id,activity_date）
        plantActivityService.recordDailyWeight(bo.getCropId(), bo.getFarmDate(), bo.getHarvestWeight(), bo.getFarmBy());
        return r.getId();
    }

    /**
     * 退茬副作用（与 {@link #submitRotation} 单条一致，抽出供批量复用）：
     * plot_info.plot_status=1（空闲）。plant_status='completed' + end_actualdate 归「种植完成」独占，退茬不写。
     */
    private void applyRotationSideEffect(Long plotId) {
        PlotInfo plot = plotInfoMapper.selectById(plotId);
        if (plot == null) {
            throw new ServiceException("地块不存在: " + plotId);
        }
        // 幂等拦截（231）：已退茬变空地（plot_status=1）的地块拒绝重复退茬；
        // 其余非采摘态（如 2=种植）给准确文案，不误报「已退茬」。
        if (plot.getPlotStatus() == null || plot.getPlotStatus() != 3) {
            if (plot.getPlotStatus() != null && plot.getPlotStatus() == 1) {
                throw new ServiceException("该地块已退茬，无需重复退茬");
            }
            throw new ServiceException("地块状态不满足退茬（需处于采摘状态）");
        }
        plot.setPlotStatus(1);
        plotInfoMapper.updateById(plot);
    }

    @Override
    public List<FarmCropPlotVo> listCropPlots(Long cropId, String farmType) {
        if (cropId == null) {
            return List.of();
        }
        // 工种 → 状态规则（与 listCropTargetCards 共用 resolveStatusFilter，避免口径漂移）：
        // 退茬 → harvest_status='completed'（采摘完成）；
        // 其余 → 产出期 plant_status='completed' AND harvest_status<>'completed'（种植完成但未采完，与列表卡同口径）。
        StatusFilter filter = resolveStatusFilter(farmType);
        LambdaQueryWrapper<PlantDetails> dw = new LambdaQueryWrapper<PlantDetails>()
            .eq(PlantDetails::getCropId, cropId);
        if (filter.byHarvestStatus()) {
            dw.eq(PlantDetails::getHarvestStatus, filter.harvestStatus());
            // 退茬口径（231）：地块须 plot_status=3（仍采摘态、未退茬变空地）。
            // 已退茬地块 plot_status=1 应排除，否则空地仍出现在退茬多选页并可被重复退茬。
            // 与列表卡/片区胶囊（selectCropTargetCardsForRotation / selectCropZoneCountsForRotation）同口径。
            if ("rotation".equals(farmType)) {
                List<PlotInfo> activePlots = plotInfoMapper.selectList(
                    new LambdaQueryWrapper<PlotInfo>().eq(PlotInfo::getPlotStatus, 3));
                Set<Long> activePlotIds = activePlots.stream()
                    .map(PlotInfo::getId).filter(Objects::nonNull).collect(Collectors.toSet());
                if (activePlotIds.isEmpty()) {
                    return List.of();
                }
                dw.in(PlantDetails::getPlotId, activePlotIds);
            }
        } else {
            dw.eq(PlantDetails::getPlantStatus, filter.plantStatus())
                .ne(PlantDetails::getHarvestStatus, filter.harvestStatus());
            // 移栽（row16）：追加「保育地块（plot_type='nursery'）且累计移栽未满（<100%）」过滤——
            // 与列表卡 selectCropTargetCardsForTransplant 同候选口径（一处定义 selectTransplantCandidatePlotIds），
            // 保证列表卡数字与点进去逐行行数一致。候选为空 → 直接返空，不查明细。
            if ("transplant".equals(farmType)) {
                List<Long> candidatePlotIds = baseMapper.selectTransplantCandidatePlotIds(cropId);
                if (CollUtil.isEmpty(candidatePlotIds)) {
                    return List.of();
                }
                dw.in(PlantDetails::getPlotId, candidatePlotIds);
            }
        }
        List<PlantDetails> details = plantDetailsMapper.selectList(
            dw.orderByAsc(PlantDetails::getPlotId)
                .orderByAsc(PlantDetails::getId));
        if (CollUtil.isEmpty(details)) {
            return List.of();
        }

        // 上次同类农事日期（plotId → lastFarmDate），farmType 为空时不算间隔
        Map<Long, LocalDate> lastDateMap = new HashMap<>();
        if (StringUtils.isNotBlank(farmType)) {
            List<Map<String, Object>> rows = baseMapper.selectLastFarmDateByCropType(cropId, farmType);
            for (Map<String, Object> row : rows) {
                Object pid = row.get("plotId");
                Object last = row.get("lastFarmDate");
                if (pid != null && last instanceof java.sql.Date sqlDate) {
                    lastDateMap.put(((Number) pid).longValue(), sqlDate.toLocalDate());
                } else if (pid != null && last instanceof LocalDate ld) {
                    lastDateMap.put(((Number) pid).longValue(), ld);
                }
            }
        }

        // enrich plotCode/plotName
        Set<Long> plotIds = details.stream().map(PlantDetails::getPlotId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PlotInfo> plotMap = plotIds.isEmpty() ? Map.of()
            : plotInfoMapper.selectByIds(plotIds).stream()
            .collect(Collectors.toMap(PlotInfo::getId, p -> p, (a, b) -> a));

        // r65：每地块「上次农事记录的班组」（批量录入弹框默认班组），一次 GROUP BY 批量取，禁 N+1
        Map<Long, Long> lastTeamByPlot = plotIds.isEmpty()
            ? Map.of()
            : baseMapper.selectLastFarmTeamByPlot(new ArrayList<>(plotIds)).stream()
                .filter(m -> m.get("plotId") != null && m.get("lastTeamId") != null)
                .collect(Collectors.toMap(
                    m -> ((Number) m.get("plotId")).longValue(),
                    m -> ((Number) m.get("lastTeamId")).longValue(),
                    (a, b) -> a));

        // enrich 片区（FIX-PLT-MP-ROTATION-ZONE-001）：批量按 zone_id 取 zoneCode/zoneName，禁 N+1。
        Set<Long> zoneIds = plotMap.values().stream()
            .map(PlotInfo::getZoneId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PlotZone> zoneMap = zoneIds.isEmpty() ? Map.of()
            : plotZoneMapper.selectByIds(zoneIds).stream()
            .collect(Collectors.toMap(PlotZone::getId, z -> z, (a, b) -> a));

        // 移栽进度层：返各地块累计已移% + 上次时间（FIX-PLT-MP-WORK-BATCH-001 移栽单块录入用）
        Map<Long, Integer> transplantedMap = new HashMap<>();
        // row103：各源地块「上次转移目标」地块 id + 其片区 id（二次移栽录入弹窗默认回填）
        Map<Long, Long> lastTransplantPlotMap = new HashMap<>();
        Map<Long, Long> lastTransplantZoneMap = new HashMap<>();
        if ("transplant".equals(farmType)) {
            for (Map<String, Object> row : baseMapper.selectTransplantedPercentByCrop(cropId)) {
                Object pid = row.get("plotId");
                Object pct = row.get("transplantedPercent");
                if (pid != null) {
                    transplantedMap.put(((Number) pid).longValue(), pct instanceof Number n ? n.intValue() : 0);
                }
            }
            for (Map<String, Object> row : baseMapper.selectLastTransplantTargetByCrop(cropId)) {
                Object pid = row.get("plotId");
                if (pid == null) {
                    continue;
                }
                Long srcPlot = ((Number) pid).longValue();
                Object tPlot = row.get("lastTransplantPlotId");
                Object tZone = row.get("lastTransplantZoneId");
                if (tPlot instanceof Number n) {
                    lastTransplantPlotMap.put(srcPlot, n.longValue());
                }
                if (tZone instanceof Number n) {
                    lastTransplantZoneMap.put(srcPlot, n.longValue());
                }
            }
        }

        // r56：灾害选地块页——按 plot 聚合最近 3 条灾害记录（farm_type='disaster'，farm_date 倒序 + id 倒序），
        // 地块名下逐行展示「灾害 损失率% 录入时间」。仅 disaster 工种聚合，避免其他工种多余查询。
        Map<Long, List<FarmCropPlotVo.DisasterRecord>> disasterByPlot = new HashMap<>();
        if ("disaster".equals(farmType) && !plotIds.isEmpty()) {
            int maxPerPlot = 3;
            List<FarmRecords> disasterRows = baseMapper.selectList(
                new LambdaQueryWrapper<FarmRecords>()
                    .eq(FarmRecords::getFarmType, "disaster")
                    .in(FarmRecords::getPlotId, plotIds)
                    .orderByDesc(FarmRecords::getFarmDate)
                    .orderByDesc(FarmRecords::getId));
            for (FarmRecords r : disasterRows) {
                Long pid = r.getPlotId();
                if (pid == null) {
                    continue;
                }
                List<FarmCropPlotVo.DisasterRecord> recs = disasterByPlot.computeIfAbsent(pid, k -> new ArrayList<>());
                // DB 端已倒序，按 plot 累计到上限即跳过余下（保留最近 3 条）
                if (recs.size() >= maxPerPlot) {
                    continue;
                }
                FarmCropPlotVo.DisasterRecord rec = new FarmCropPlotVo.DisasterRecord();
                rec.setDisasterType(r.getDisasterType());
                rec.setLossRate(r.getLossRate());
                rec.setFarmDate(r.getFarmDate());
                recs.add(rec);
            }
        }

        LocalDate today = LocalDate.now();
        List<FarmCropPlotVo> result = new ArrayList<>(details.size());
        for (PlantDetails d : details) {
            FarmCropPlotVo vo = new FarmCropPlotVo();
            vo.setDetailId(d.getId());
            vo.setPlantId(d.getPlantId());
            vo.setPlotId(d.getPlotId());
            vo.setCropId(d.getCropId());
            vo.setPlantStatus(d.getPlantStatus());
            // 180-2：退茬多选页每行展示「采摘状态 / 采摘完成时间」（earliest/last/end harvestdate）。
            vo.setHarvestStatus(d.getHarvestStatus());
            vo.setEarliestHarvestdate(d.getEarliestHarvestdate());
            vo.setLastHarvestdate(d.getLastHarvestdate());
            vo.setEndHarvestdate(d.getEndHarvestdate());
            PlotInfo plot = plotMap.get(d.getPlotId());
            if (plot != null) {
                vo.setPlotCode(plot.getPlotCode());
                vo.setPlotName(plot.getPlotName());
                PlotZone zone = plot.getZoneId() == null ? null : zoneMap.get(plot.getZoneId());
                if (zone != null) {
                    vo.setZoneCode(zone.getZoneCode());
                    vo.setZoneName(zone.getZoneName());
                }
            }
            LocalDate last = lastDateMap.get(d.getPlotId());
            if (last != null) {
                vo.setLastFarmDate(last);
                vo.setIntervalDays((int) ChronoUnit.DAYS.between(last, today));
            }
            // r65：上次农事班组（批量录入弹框默认班组回填）
            vo.setLastTeamId(lastTeamByPlot.get(d.getPlotId()));
            if ("transplant".equals(farmType)) {
                vo.setTransplantedPercent(transplantedMap.getOrDefault(d.getPlotId(), 0));
                // row103：二次移栽默认回填上次转移目标 + 其片区（无历史移栽记录留空）
                vo.setLastTransplantPlotId(lastTransplantPlotMap.get(d.getPlotId()));
                vo.setLastTransplantZoneId(lastTransplantZoneMap.get(d.getPlotId()));
            }
            if ("disaster".equals(farmType)) {
                vo.setDisasterRecords(disasterByPlot.getOrDefault(d.getPlotId(), List.of()));
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<FarmCropTargetCardVo> listCropTargetCards(String farmType, Long zoneId, String plotCode) {
        if (StringUtils.isBlank(farmType)) {
            return List.of();
        }
        String code = StringUtils.isBlank(plotCode) ? null : plotCode;
        // 工种 → 聚合 @Select 分支（rotation 用 harvest_status；transplant 叠 plot_type='nursery'；
        // 其余产出期 plant_status='completed' AND harvest_status<>'completed'）
        List<Map<String, Object>> rows;
        if ("rotation".equals(farmType)) {
            rows = baseMapper.selectCropTargetCardsForRotation(farmType, zoneId, code);
        } else if ("transplant".equals(farmType)) {
            rows = baseMapper.selectCropTargetCardsForTransplant(farmType, zoneId, code);
        } else {
            rows = baseMapper.selectCropTargetCardsForGrow(farmType, zoneId, code);
        }
        if (CollUtil.isEmpty(rows)) {
            return List.of();
        }

        List<FarmCropTargetCardVo> result = new ArrayList<>(rows.size());
        List<Long> cropIds = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            FarmCropTargetCardVo vo = new FarmCropTargetCardVo();
            Object cropId = row.get("cropId");
            if (cropId != null) {
                vo.setId(((Number) cropId).longValue());
                cropIds.add(vo.getId());
            }
            vo.setCropName(row.get("cropName") == null ? null : row.get("cropName").toString());
            vo.setCropCode(row.get("cropCode") == null ? null : row.get("cropCode").toString());
            Object cnt = row.get("plotCount");
            vo.setPlotCount(cnt == null ? 0 : ((Number) cnt).intValue());
            Object last = row.get("lastFarmDate");
            if (last instanceof java.sql.Date sqlDate) {
                vo.setLastFarmDate(sqlDate.toLocalDate());
            } else if (last instanceof LocalDate ld) {
                vo.setLastFarmDate(ld);
            }
            result.add(vo);
        }

        // 批量回填作物图（IMG-LIB-001 resolver，禁 N+1）
        fillCropImg(result, cropIds);
        return result;
    }

    @Override
    public List<CropZoneCountVo> listCropZoneCounts(String farmType) {
        if (StringUtils.isBlank(farmType)) {
            return List.of();
        }
        // 工种 → 片区胶囊聚合 @Select 分支（与 listCropTargetCards 同口径）
        List<Map<String, Object>> rows;
        if ("rotation".equals(farmType)) {
            rows = baseMapper.selectCropZoneCountsForRotation();
        } else if ("transplant".equals(farmType)) {
            rows = baseMapper.selectCropZoneCountsForTransplant();
        } else {
            rows = baseMapper.selectCropZoneCountsForGrow();
        }
        if (CollUtil.isEmpty(rows)) {
            return List.of();
        }
        List<CropZoneCountVo> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            CropZoneCountVo vo = new CropZoneCountVo();
            Object zoneId = row.get("zoneId");
            if (zoneId != null) {
                vo.setZoneId(((Number) zoneId).longValue());
            }
            vo.setZoneName(row.get("zoneName") == null ? null : row.get("zoneName").toString());
            Object cnt = row.get("plotCount");
            vo.setPlotCount(cnt == null ? 0 : ((Number) cnt).intValue());
            result.add(vo);
        }
        return result;
    }

    /**
     * 批量回填作物卡图 public URL（IMG-LIB-001 4 层 resolver）。
     *
     * <p>一次 {@code selectByIds} 取图 ossId，再一次 {@code resolveList} 转 public URL，禁 N+1。</p>
     *
     * <p>取图 ossId 多字段兜底 {@code COALESCE(image_oss_id, crop_image_preview, crop_image_url 首段)}：
     * admin 端用户上传作物图实际落 {@code crop_image_preview}/{@code crop_image_url}（存 ossId），
     * 而 {@code image_oss_id} 仅图库自动匹配/手选时写入，常为空——只读 image_oss_id 会漏掉用户传的图
     * （表现为移栽作物卡「暂无图」）。与「产品双图片字段」同款修法。</p>
     */
    private void fillCropImg(List<FarmCropTargetCardVo> cards, List<Long> cropIds) {
        if (CollUtil.isEmpty(cards) || CollUtil.isEmpty(cropIds)) {
            return;
        }
        // 手工建 map：resolveCropImageOssId 对无图作物返 null，Collectors.toMap 遇 null value 会抛 NPE
        // （HashMap.merge 不接受 null）→ 凡作物卡含一个无图作物即 500。HashMap.put + putIfAbsent 容忍 null value，
        // 保留首条优先（与原 (a,b)->a 同义；无图作物 → map 值 null，下游按「无 ossId」走默认图）。
        Map<Long, String> ossIdMap = new HashMap<>();
        for (CropInfo c : cropInfoMapper.selectByIds(cropIds)) {
            if (c.getId() != null) {
                ossIdMap.putIfAbsent(c.getId(), resolveCropImageOssId(c));
            }
        }
        List<ImageUrlResolver.Item> items = new ArrayList<>(cards.size());
        for (FarmCropTargetCardVo card : cards) {
            String ossId = card.getId() == null ? null : ossIdMap.get(card.getId());
            items.add(new ImageUrlResolver.Item(StringUtils.isBlank(ossId) ? null : ossId, CROP_BELONG_TYPE));
        }
        List<String> urls = imageUrlResolver.resolveList(items);
        if (urls.size() != cards.size()) {
            return;
        }
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).setCropImg(urls.get(i));
        }
    }

    /**
     * 取作物图 ossId（row15①）：{@code image_oss_id → crop_image_preview → crop_image_url 首段} 兜底。
     * 全空返 null（交 resolver 走默认图/无图）。{@code crop_image_url} 逗号分隔多张，取第一张。
     */
    private String resolveCropImageOssId(CropInfo c) {
        if (StringUtils.isNotBlank(c.getImageOssId())) {
            return c.getImageOssId();
        }
        if (StringUtils.isNotBlank(c.getCropImagePreview())) {
            return c.getCropImagePreview();
        }
        if (StringUtils.isNotBlank(c.getCropImageUrl())) {
            String first = c.getCropImageUrl().split(",")[0].trim();
            return StringUtils.isBlank(first) ? null : first;
        }
        return null;
    }

    /**
     * 工种 → 作物卡状态过滤规则（listCropPlots 多选页 + listCropTargetCards 列表卡两处共用，避免口径漂移）。
     *
     * <ul>
     *   <li>rotation（退茬）→ {@code harvest_status='completed'}（采摘完成）</li>
     *   <li>其余（含 transplant / 生长类）→ 产出期 {@code plant_status='completed' AND harvest_status<>'completed'}
     *       （种植完成但未采完）</li>
     * </ul>
     *
     * <p>移栽（transplant）的额外 {@code plot_type='nursery'} 约束只在列表卡聚合 SQL 里叠加（多选页按作物已选定、
     * 不重复地块类型过滤），故 transplant 在此仍归产出期口径。</p>
     */
    private StatusFilter resolveStatusFilter(String farmType) {
        if ("rotation".equals(farmType)) {
            return new StatusFilter(true, null, "completed");
        }
        return new StatusFilter(false, "completed", "completed");
    }

    /**
     * 状态过滤口径：{@code byHarvestStatus=true} 时按 {@code harvest_status='完成'} 单条件过滤（退茬）；
     * 否则按产出期双条件 {@code plant_status=plantStatus AND harvest_status<>harvestStatus} 过滤。
     */
    private record StatusFilter(boolean byHarvestStatus, String plantStatus, String harvestStatus) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int adjustPlotPickStatus(PlotPickStatusBo bo) {
        if (!"picking".equals(bo.getPickStatus()) && !"completed".equals(bo.getPickStatus())) {
            throw new ServiceException("非法采摘状态（仅支持 picking 采摘中 / completed 采摘完成）：" + bo.getPickStatus());
        }
        // 定位该地块 + 作物下的采摘活动明细（本接口是「采摘活动管理」路径，只动 is_pick=1 游客活动行，
        // 不碰 is_pick=2 普通采收行——普通采收状态由 submitPick 的 pending→picking/completed CAS 独占推进）
        List<PlantDetails> details = plantDetailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlotId, bo.getPlotId())
                .eq(PlantDetails::getCropId, bo.getCropId())
                .eq(PlantDetails::getIsPick, 1));
        if (CollUtil.isEmpty(details)) {
            throw new ServiceException("该地块下无对应作物的种植明细，无法调整采摘状态");
        }
        Long updateBy = currentUserSafe();
        boolean toCompleted = "completed".equals(bo.getPickStatus());
        // completed 方向只动未完成行（不覆写已完成行的 end_harvestdate 历史完成日期）；
        // picking 方向对全部活动行生效（completed→picking 重开是本接口的本职功能）。
        List<Long> targetIds = details.stream()
            .filter(d -> !toCompleted || !"completed".equals(d.getHarvestStatus()))
            .map(PlantDetails::getId)
            .toList();
        int affected = 0;
        if (CollUtil.isNotEmpty(targetIds)) {
            // 按 select 出的行 id 集合更新，与上面的定位口径严格一致
            LambdaUpdateWrapper<PlantDetails> uw = new LambdaUpdateWrapper<PlantDetails>()
                .in(PlantDetails::getId, targetIds)
                .set(PlantDetails::getHarvestStatus, bo.getPickStatus())
                .set(PlantDetails::getUpdateBy, updateBy);
            // begin_harvestdate NULL-safe 回填（两方向都补）：首次进入采摘（picking / 未开采直接 completed）
            // 以本次调整日期作为开始采摘日期；已有值不覆写（口径对齐 submitPick 的「首次采收回填 begin_harvestdate」）。
            // {0} 为 MP 参数绑定占位（adjustDate 是 LocalDate），非字符串拼接，无注入面。
            uw.setSql("begin_harvestdate = COALESCE(begin_harvestdate, {0})", bo.getAdjustDate());
            if (toCompleted) {
                uw.set(PlantDetails::getEndHarvestdate, bo.getAdjustDate());
            } else {
                // row160④：首次采摘录入（待采摘 → 采摘中）时把所选班组回写 plant_details.harvest_by，
                // 作为「首次采摘录入班组」权威源。第二次录入（采摘中 → 采摘完成）时前端读回该班组预填并锁定，
                // 保证同一地块二次录入班组默认第一次的且不可调整。完成态不再改写班组。
                uw.set(PlantDetails::getHarvestBy, bo.getTeamId());
            }
            affected = plantDetailsMapper.update(null, uw);
        }

        // row4（何涛 2026-07-17）：地块进入采摘（采摘中 / 采摘完成）后，plot_info.plot_status 从 2（种植）
        //   置 3（采摘），字典 djs_plot_status：1=空闲 / 2=种植 / 3=采摘。与 submitPick 首次开采同口径，
        //   使地块状态随采摘进度流转（不再停留种植态），并让后续退茬（submitRotation 要求 plot_status=3）可执行。
        //   仅当前为 2（种植）时翻一次；已是 3（多次调整）/ 1（异常）不动。
        PlotInfo plot = bo.getPlotId() == null ? null : plotInfoMapper.selectById(bo.getPlotId());
        if (plot != null && plot.getPlotStatus() != null && plot.getPlotStatus() == 2) {
            plotInfoMapper.update(null,
                new LambdaUpdateWrapper<PlotInfo>()
                    .eq(PlotInfo::getId, bo.getPlotId())
                    .set(PlotInfo::getPlotStatus, 3)
                    .set(PlotInfo::getUpdateBy, updateBy));
        }

        // 写一条 harvest_activity 农事记录留痕（不录重量）
        Long cropPlantId = details.get(0).getPlantId();
        FarmRecords trace = new FarmRecords();
        buildBase(trace, "harvest_activity", bo.getPlotId(), bo.getCropId(), cropPlantId,
            bo.getTeamId(), bo.getAdjustDate(), null,
            "采摘状态调整为" + (toCompleted ? "采摘完成" : "采摘中"));
        // 采摘人员（先选班组后从该班组成员中选）落 operator_user_id，与 farm_by(班组) 并存
        trace.setOperatorUserId(bo.getOperatorUserId());
        baseMapper.insert(trace);
        return affected;
    }

    // ============================================================
    // mp 中央分发台 + 我的记录
    // ============================================================

    @Override
    public DispatchSummaryVo dispatchSummary() {
        LocalDate today = LocalDate.now();
        // 已处理：当日已处理地块去重数（COUNT(DISTINCT plot_id) GROUP BY farm_type），12 工种统一去重不特判
        Map<String, Integer> processedCount = new LinkedHashMap<>();
        for (String type : FARM_TYPES_12) {
            processedCount.put(type, 0);
        }
        for (Map<String, Object> row : baseMapper.selectTodayProcessedPlotCount(today)) {
            Object type = row.get("farmType");
            Object cnt = row.get("plotCount");
            if (type != null && processedCount.containsKey(type.toString())) {
                processedCount.put(type.toString(), cnt == null ? 0 : ((Number) cnt).intValue());
            }
        }
        // 待处理：plot_status=1 空地池（12 工种共享同一空地池）
        int idleTotal = Math.toIntExact(plotInfoMapper.selectCount(
            new LambdaQueryWrapper<PlotInfo>().eq(PlotInfo::getPlotStatus, 1)));
        Map<String, Integer> pendingCount = new LinkedHashMap<>();
        for (String type : FARM_TYPES_12) {
            pendingCount.put(type, idleTotal);
        }
        DispatchSummaryVo vo = new DispatchSummaryVo();
        vo.setProcessedCount(processedCount);
        vo.setPendingCount(pendingCount);
        return vo;
    }

    @Override
    public TableDataInfo<FarmRecordsVo> myRecords(FarmRecordsQuery query, PageQuery pageQuery) {
        // mp 我的记录：按 operatorId（=登录用户）经 work_people 成员表反查所属班组，再过滤 farmBy。
        // 成员关系唯一权威源是 t_plant_work_people（user_id↔team_id，含队长/组员），
        // 不是 t_plant_work_team.leader_id（该列 V1 未启用，全 NULL）。
        if (query.getOperatorId() != null) {
            List<Long> teamIds = peopleMapper.selectList(
                new LambdaQueryWrapper<PlantWorkPeople>()
                    .eq(PlantWorkPeople::getUserId, query.getOperatorId())
                    .select(PlantWorkPeople::getTeamId))
                .stream().map(PlantWorkPeople::getTeamId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
            if (CollUtil.isEmpty(teamIds)) {
                return TableDataInfo.build(new Page<>());
            }
            // 复用 buildWrapper：内含 farmType eq（前端传的工种过滤）+ 排序，再叠加班组 farmBy IN
            LambdaQueryWrapper<FarmRecords> w = buildWrapper(query);
            w.in(FarmRecords::getFarmBy, teamIds);
            Page<FarmRecordsVo> page = baseMapper.selectVoPage(pageQuery.build(), w);
            enrichRefs(page.getRecords());
            return TableDataInfo.build(page);
        }
        // 未登录态 / 测试场景退化为全部
        return queryPageList(query, pageQuery);
    }

    // ============================================================
    // admin 列表 / 详情
    // ============================================================

    @Override
    public TableDataInfo<FarmRecordsVo> queryPageList(FarmRecordsQuery query, PageQuery pageQuery) {
        Page<FarmRecordsVo> page = baseMapper.selectVoPage(pageQuery.build(), buildWrapper(query));
        enrichRefs(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<FarmRecordsVo> queryList(FarmRecordsQuery query) {
        List<FarmRecordsVo> list = baseMapper.selectVoList(buildWrapper(query));
        enrichRefs(list);
        return list;
    }

    @Override
    public FarmRecordsVo queryById(Long id) {
        FarmRecordsVo vo = baseMapper.selectVoById(id);
        if (vo == null) {
            return null;
        }
        enrichRefs(List.of(vo));
        return vo;
    }

    // ============================================================
    // 内部：装填基础字段 / 业务码 / enrich
    // ============================================================

    /**
     * 装填 5 类提交共有字段；专属字段（tillage / disaster / transplant）由调用方各自设置。
     */
    private void buildBase(FarmRecords r,
                           String farmType,
                           Long plotId,
                           Long cropId,
                           Long plantId,
                           Long farmBy,
                           LocalDate farmDate,
                           String proofOssIds,
                           String remark) {
        r.setRecordNo(generateRecordNo());
        r.setFarmType(farmType);
        r.setPlotId(plotId);
        r.setCropId(cropId);
        r.setPlantId(plantId);
        r.setFarmBy(farmBy);
        r.setFarmDate(farmDate);
        r.setProofOssIds(proofOssIds);
        r.setRemark(remark);

        // 冗余 plot_type
        if (plotId != null) {
            PlotInfo plot = plotInfoMapper.selectById(plotId);
            if (plot != null) {
                r.setPlotType(plot.getPlotStatus());
            }
        }
        // 冗余 crop_name
        if (cropId != null) {
            CropInfo crop = cropInfoMapper.selectById(cropId);
            if (crop != null) {
                r.setCropName(crop.getCropName());
            }
        }
        r.setIsWarning(2);
    }

    /**
     * 业务码 {@code FRyyyyMMddNNNN}（NNNN 当日 4 位序号）。
     * UNIQUE(tenant_id, record_no, del_unique) 在 DB 层兜底并发。
     */
    private String generateRecordNo() {
        String tenantId = TenantHelper.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            tenantId = "1001";
        }
        String prefix = "FR" + LocalDate.now().format(DATE_NUMERIC);
        String maxRecordNo = baseMapper.selectMaxRecordNoByPrefix(tenantId, prefix);
        int next = 1;
        if (StringUtils.isNotBlank(maxRecordNo) && maxRecordNo.length() == prefix.length() + 4) {
            try {
                next = Integer.parseInt(maxRecordNo.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                // 旧脏数据兜底，next=1
            }
        }
        return prefix + String.format("%04d", next);
    }

    /**
     * 灾害副作用：累加 plant_details.loss_yield。
     *
     * <p>策略：取 (plantId, plotId, cropId) + end_actualdate IS NULL 的最新一行 details；
     * 若无未结束行（极端：客户先标完结再补登记灾害），取最新一行做累加。</p>
     */
    private void accumulateLossYield(Long plantId, Long plotId, Long cropId, BigDecimal lossYield) {
        if (lossYield == null || lossYield.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        PlantDetails target = locatePlantDetails(plantId, plotId, cropId);
        if (target == null) {
            log.warn("submitDisaster: no plant_details for plantId={} plotId={} cropId={}, skip loss_yield accumulate",
                plantId, plotId, cropId);
            return;
        }
        BigDecimal current = target.getLossYield() == null ? BigDecimal.ZERO : target.getLossYield();
        BigDecimal updated = current.add(lossYield);
        Long currentUser = currentUserSafe();
        plantDetailsMapper.update(null,
            new LambdaUpdateWrapper<PlantDetails>()
                .eq(PlantDetails::getId, target.getId())
                .set(PlantDetails::getLossYield, updated)
                .set(PlantDetails::getUpdateBy, currentUser));
    }

    /**
     * 定位 (plantId, plotId, cropId) 对应的 plant_details 行：优先 end_actualdate IS NULL 的最新一行；
     * 无未结束行时（极端：先标完结再补登记灾害）退回最新一行。无任何行返回 null。
     */
    private PlantDetails locatePlantDetails(Long plantId, Long plotId, Long cropId) {
        PlantDetails target = plantDetailsMapper.selectOne(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlantId, plantId)
                .eq(PlantDetails::getPlotId, plotId)
                .eq(PlantDetails::getCropId, cropId)
                .isNull(PlantDetails::getEndActualdate)
                .orderByDesc(PlantDetails::getId)
                .last("LIMIT 1"));
        if (target == null) {
            target = plantDetailsMapper.selectOne(
                new LambdaQueryWrapper<PlantDetails>()
                    .eq(PlantDetails::getPlantId, plantId)
                    .eq(PlantDetails::getPlotId, plotId)
                    .eq(PlantDetails::getCropId, cropId)
                    .orderByDesc(PlantDetails::getId)
                    .last("LIMIT 1"));
        }
        return target;
    }

    /**
     * 灾害损失率累计校验（row79）：同批次（crop+plot+plant）历史累计损失率 + 本次 不得超过 100%。
     *
     * <p>与 {@code computeLossYield}/{@code accumulateLossYield} 同批次口径，换茬复种不掺旧茬。
     * 本次损失率为空按 0 处理（不拦）。超限抛 {@link ServiceException}，事务回滚。</p>
     */
    private void requireDisasterLossWithinCap(Long cropId, Long plotId, Long plantId, BigDecimal lossRate) {
        BigDecimal current = baseMapper.sumDisasterLossRate(cropId, plotId, plantId);
        if (current == null) {
            current = BigDecimal.ZERO;
        }
        BigDecimal add = lossRate == null ? BigDecimal.ZERO : lossRate;
        if (current.add(add).compareTo(new BigDecimal("100")) > 0) {
            BigDecimal remain = new BigDecimal("100").subtract(current);
            if (remain.compareTo(BigDecimal.ZERO) < 0) {
                remain = BigDecimal.ZERO;
            }
            throw new ServiceException("本次损失率超出剩余可录 " + remain.stripTrailingZeros().toPlainString()
                + "%（已累计 " + current.stripTrailingZeros().toPlainString() + "%）");
        }
    }

    /**
     * 损失产量计算口径：损失产量 = 预计产量(plant_details.expected_yield) × 损失率(0-100) / 100，
     * 保留两位小数（HALF_UP）。预计产量缺失或 ≤0 时返回 BigDecimal.ZERO（不入库假值）。
     *
     * <p>不再信任前端传入的 loss_yield，由系统按地块预计产量统一算出。</p>
     */
    private BigDecimal computeLossYield(Long plantId, Long plotId, Long cropId, BigDecimal lossRate) {
        if (lossRate == null) {
            return BigDecimal.ZERO;
        }
        PlantDetails target = locatePlantDetails(plantId, plotId, cropId);
        if (target == null || target.getExpectedYield() == null
            || target.getExpectedYield().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("submitDisaster: no expected_yield for plantId={} plotId={} cropId={}, loss_yield=0",
                plantId, plotId, cropId);
            return BigDecimal.ZERO;
        }
        return target.getExpectedYield()
            .multiply(lossRate)
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /**
     * 采摘活动管理副作用：累加 plant_details.actual_yield（FIX-PLT-MP-HARVEST-001 #3=a）。
     *
     * <p>定位策略同 {@link #accumulateLossYield}：取 (plantId, plotId, cropId) + end_actualdate IS NULL
     * 的最新一行 details；无未结束行则取最新一行。</p>
     */
    private void accumulateActualYield(Long plantId, Long plotId, Long cropId, BigDecimal weight) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        PlantDetails target = locatePlantDetails(plantId, plotId, cropId);
        if (target == null) {
            log.warn("submitHarvestWeight: no plant_details for plantId={} plotId={} cropId={}, skip actual_yield accumulate",
                plantId, plotId, cropId);
            return;
        }
        BigDecimal current = target.getActualYield() == null ? BigDecimal.ZERO : target.getActualYield();
        plantDetailsMapper.update(null,
            new LambdaUpdateWrapper<PlantDetails>()
                .eq(PlantDetails::getId, target.getId())
                .set(PlantDetails::getActualYield, current.add(weight))
                .set(PlantDetails::getUpdateBy, currentUserSafe()));
    }

    private Long currentUserSafe() {
        try {
            Long uid = LoginHelper.getUserId();
            return uid != null ? uid : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private LambdaQueryWrapper<FarmRecords> buildWrapper(FarmRecordsQuery query) {
        LambdaQueryWrapper<FarmRecords> w = new LambdaQueryWrapper<>();
        if (query == null) {
            return w.orderByDesc(FarmRecords::getFarmDate).orderByDesc(FarmRecords::getId);
        }
        // PLT-WORK-002 admin 5 Tab：farmWorkTypes 多选优先（IN）；为空时退回 farmType 单值（mp / 旧调用方）
        boolean hasWorkTypes = CollUtil.isNotEmpty(query.getFarmWorkTypes());
        // 地块名称模糊搜索：plot_name 不在主表，经 plot_info 反查 plotId 列表再 IN（无命中则强制空结果）
        boolean filterByPlotName = StringUtils.isNotBlank(query.getPlotName());
        List<Long> plotIdsByName = filterByPlotName
            ? plotInfoMapper.selectList(new LambdaQueryWrapper<PlotInfo>()
                .select(PlotInfo::getId)
                .like(PlotInfo::getPlotName, query.getPlotName()))
            .stream().map(PlotInfo::getId).collect(Collectors.toList())
            : null;
        // 地块编号模糊搜索：plot_code 不在主表，经 plot_info 反查 plotId 列表再 IN（与 plotName IN 叠加为 AND）
        boolean filterByPlotCode = StringUtils.isNotBlank(query.getPlotCode());
        List<Long> plotIdsByCode = filterByPlotCode
            ? plotInfoMapper.selectList(new LambdaQueryWrapper<PlotInfo>()
                .select(PlotInfo::getId)
                .like(PlotInfo::getPlotCode, query.getPlotCode()))
            .stream().map(PlotInfo::getId).collect(Collectors.toList())
            : null;
        // 地块片区筛选：zone_id 不在主表，经 plot_info.zone_id eq 反查 plotId 列表再 IN（与 plotName/plotCode IN 叠加为 AND）
        boolean filterByZone = query.getZoneId() != null;
        List<Long> plotIdsByZone = filterByZone
            ? plotInfoMapper.selectList(new LambdaQueryWrapper<PlotInfo>()
                .select(PlotInfo::getId)
                .eq(PlotInfo::getZoneId, query.getZoneId()))
            .stream().map(PlotInfo::getId).collect(Collectors.toList())
            : null;
        w.like(StringUtils.isNotBlank(query.getRecordNo()), FarmRecords::getRecordNo, query.getRecordNo())
            .in(hasWorkTypes, FarmRecords::getFarmType, query.getFarmWorkTypes())
            .eq(!hasWorkTypes && StringUtils.isNotBlank(query.getFarmType()), FarmRecords::getFarmType, query.getFarmType())
            .eq(query.getPlotId() != null, FarmRecords::getPlotId, query.getPlotId())
            // 地块名命中 → IN plotIds；命中为空 → IN (-1) 哨兵 id，确保返回空结果
            .in(filterByPlotName, FarmRecords::getPlotId, CollUtil.isEmpty(plotIdsByName) ? List.of(-1L) : plotIdsByName)
            // 地块编号命中 → IN plotIds；命中为空 → IN (-1) 哨兵 id，确保返回空结果
            .in(filterByPlotCode, FarmRecords::getPlotId, CollUtil.isEmpty(plotIdsByCode) ? List.of(-1L) : plotIdsByCode)
            // 地块片区命中 → IN plotIds；命中为空 → IN (-1) 哨兵 id，确保返回空结果
            .in(filterByZone, FarmRecords::getPlotId, CollUtil.isEmpty(plotIdsByZone) ? List.of(-1L) : plotIdsByZone)
            .eq(query.getCropId() != null, FarmRecords::getCropId, query.getCropId())
            .like(StringUtils.isNotBlank(query.getCropName()), FarmRecords::getCropName, query.getCropName())
            .eq(query.getFarmBy() != null, FarmRecords::getFarmBy, query.getFarmBy())
            // PLT-WORK-003 灾害子页：disaster_type / is_warning 筛选
            .eq(StringUtils.isNotBlank(query.getDisasterType()), FarmRecords::getDisasterType, query.getDisasterType())
            .eq(query.getIsWarning() != null, FarmRecords::getIsWarning, query.getIsWarning())
            .ge(query.getFarmDateBegin() != null, FarmRecords::getFarmDate, query.getFarmDateBegin())
            .le(query.getFarmDateEnd() != null, FarmRecords::getFarmDate, query.getFarmDateEnd())
            .orderByDesc(FarmRecords::getFarmDate)
            .orderByDesc(FarmRecords::getId);
        return w;
    }

    /**
     * VO 批量 enrich：plotName / plotCode / teamName / transplantPlotName + 转移前后片区名（row17）。
     *
     * <p>cropName 已落表，farmType / disasterType / tillageType 走 ruoyi Excel/前端字典翻译，
     * service 不再额外翻译。片区名按 zone_id 批量取（仿 listCropPlots 的 zoneMap，禁 N+1）。</p>
     */
    private void enrichRefs(List<FarmRecordsVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        Set<Long> plotIds = list.stream()
            .flatMap(v -> java.util.stream.Stream.of(v.getPlotId(), v.getTransplantPlot()))
            .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> teamIds = list.stream().map(FarmRecordsVo::getFarmBy).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, PlotInfo> plotMap = plotIds.isEmpty() ? Map.of()
            : plotInfoMapper.selectByIds(plotIds).stream()
            .collect(Collectors.toMap(PlotInfo::getId, p -> p, (a, b) -> a));
        Map<Long, String> teamMap = teamIds.isEmpty() ? Map.of()
            : teamMapper.selectByIds(teamIds).stream()
            .collect(Collectors.toMap(PlantWorkTeam::getId, PlantWorkTeam::getTeamName, (a, b) -> a));

        // 片区名（row17 移栽记录卡转移前/后片区）：收集源 + 转移地块的 zoneId 批量取，禁 N+1。
        Set<Long> zoneIds = plotMap.values().stream()
            .map(PlotInfo::getZoneId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> zoneMap = zoneIds.isEmpty() ? Map.of()
            : plotZoneMapper.selectByIds(zoneIds).stream()
            .collect(Collectors.toMap(PlotZone::getId, PlotZone::getZoneName, (a, b) -> a));

        // 班组多选中间表 enrich（G1-TEAMS-MULTISELECT，row37）：批量取各记录班组名/id
        Set<Long> recordIds = list.stream().map(FarmRecordsVo::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, List<String>> farmNamesMap = teamLinkService.farmTeamNames(recordIds);
        Map<Long, List<Long>> farmIdsMap = teamLinkService.farmTeamIds(recordIds);

        for (FarmRecordsVo vo : list) {
            PlotInfo plot = plotMap.get(vo.getPlotId());
            if (plot != null) {
                vo.setPlotName(plot.getPlotName());
                vo.setPlotCode(plot.getPlotCode());
                if (plot.getZoneId() != null) {
                    vo.setPlotZoneName(zoneMap.get(plot.getZoneId()));
                }
            }
            PlotInfo transplant = vo.getTransplantPlot() == null ? null : plotMap.get(vo.getTransplantPlot());
            if (transplant != null) {
                vo.setTransplantPlotName(transplant.getPlotName());
                // row101.2：移栽记录卡「转移后」优先显地块编号（前端 plotCode ?? plotName 回落）
                vo.setTransplantPlotCode(transplant.getPlotCode());
                if (transplant.getZoneId() != null) {
                    vo.setTransplantPlotZoneName(zoneMap.get(transplant.getZoneId()));
                }
            }
            vo.setTeamName(vo.getFarmBy() == null ? null : teamMap.get(vo.getFarmBy()));
            List<String> names = farmNamesMap.get(vo.getId());
            vo.setTeamNames(CollUtil.isNotEmpty(names) ? names
                : (vo.getTeamName() == null ? java.util.Collections.emptyList() : List.of(vo.getTeamName())));
            List<Long> ids = farmIdsMap.get(vo.getId());
            vo.setFarmByIds(CollUtil.isNotEmpty(ids) ? ids
                : (vo.getFarmBy() == null ? java.util.Collections.emptyList() : List.of(vo.getFarmBy())));
        }
    }

    // ============================================================
    // 入参类型分组
    // ============================================================

    private boolean isEmptyFarmType(String type) {
        return "tillage_break".equals(type) || "tillage_prepare".equals(type) || "fertilize".equals(type);
    }

    private boolean isPlainGrowFarmType(String type) {
        // 生长阶段类剔除 rotation / transplant / disaster（独立路径）。
        // harvest_activity = 游客采摘活动(is_pick=1)；harvest = 普通采收(is_pick=2)：
        // 两者都由 AppletPickServiceImpl.submitPick 经 submitGrow 写一行可追溯农事记录（FIX-PLT-AD-PICK-FARMTYPE-001）。
        return "water_fertilize".equals(type) || "irrigation".equals(type) || "weed".equals(type)
            || "pest_control".equals(type) || "pruning".equals(type)
            || "harvest_activity".equals(type) || "harvest".equals(type);
    }

    /** 仅供测试 inspect。 */
    Map<String, Object> debugStateForTest() {
        Map<String, Object> m = new HashMap<>();
        m.put("warningLossRate", WARNING_LOSS_RATE);
        m.put("farmTypes12", FARM_TYPES_12);
        return m;
    }
}
