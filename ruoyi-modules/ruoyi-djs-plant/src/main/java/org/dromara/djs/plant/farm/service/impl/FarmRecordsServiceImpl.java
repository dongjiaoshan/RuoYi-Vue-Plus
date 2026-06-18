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

    public FarmRecordsServiceImpl(FarmRecordsMapper baseMapper,
                                  PlotInfoMapper plotInfoMapper,
                                  PlotZoneMapper plotZoneMapper,
                                  CropInfoMapper cropInfoMapper,
                                  PlantDetailsMapper plantDetailsMapper,
                                  PlantWorkTeamMapper teamMapper,
                                  PlantWorkPeopleMapper peopleMapper,
                                  ImageUrlResolver imageUrlResolver,
                                  IPlantActivityService plantActivityService) {
        super(baseMapper);
        this.plotInfoMapper = plotInfoMapper;
        this.plotZoneMapper = plotZoneMapper;
        this.cropInfoMapper = cropInfoMapper;
        this.plantDetailsMapper = plantDetailsMapper;
        this.teamMapper = teamMapper;
        this.peopleMapper = peopleMapper;
        this.imageUrlResolver = imageUrlResolver;
        this.plantActivityService = plantActivityService;
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
        // 整地子类型强校验：tillage_prepare 必须带 tillage_type + tillage_method
        if ("tillage_prepare".equals(bo.getFarmType())) {
            if (StringUtils.isBlank(bo.getTillageType())) {
                throw new ServiceException("整地农事必须选择整地类型");
            }
            if (StringUtils.isBlank(bo.getTillageMethod())) {
                throw new ServiceException("整地农事必须选择整地方式");
            }
        }
        FarmRecords r = new FarmRecords();
        buildBase(r, bo.getFarmType(), bo.getPlotId(), null, null, bo.getFarmBy(), bo.getFarmDate(),
            bo.getProofOssIds(), bo.getRemark());
        r.setTillageType(bo.getTillageType());
        r.setTillageMethod(bo.getTillageMethod());
        baseMapper.insert(r);
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitGrow(GrowRecordBo bo) {
        if (!isPlainGrowFarmType(bo.getFarmType())) {
            throw new ServiceException("不支持的生长阶段农事类型: " + bo.getFarmType());
        }
        FarmRecords r = new FarmRecords();
        buildBase(r, bo.getFarmType(), bo.getPlotId(), bo.getCropId(), bo.getPlantId(), bo.getFarmBy(),
            bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
        r.setOperatorUserId(bo.getOperatorUserId());
        baseMapper.insert(r);
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitDisaster(DisasterRecordBo bo) {
        FarmRecords r = new FarmRecords();
        buildBase(r, "disaster", bo.getPlotId(), bo.getCropId(), bo.getPlantId(), bo.getFarmBy(),
            bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
        r.setDisasterType(bo.getDisasterType());
        r.setLossRate(bo.getLossRate());
        // 损失产量由系统按 预计产量 × 损失率/100（保留两位）算出，不信任前端传值
        BigDecimal lossYield = computeLossYield(bo.getPlantId(), bo.getPlotId(), bo.getCropId(), bo.getLossRate());
        r.setLossYield(lossYield);
        r.setIsWarning(bo.getLossRate() != null && bo.getLossRate().compareTo(WARNING_LOSS_RATE) >= 0 ? 1 : 2);
        baseMapper.insert(r);

        // 副作用：累加 plant_details.loss_yield（按 plantId + plotId + cropId 定位未结束 details）
        accumulateLossYield(bo.getPlantId(), bo.getPlotId(), bo.getCropId(), lossYield);
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitTransplant(TransplantRecordBo bo) {
        if (bo.getTransplantPercent() != null && bo.getTransplantPercent() > 60) {
            throw new ServiceException("移栽百分比不能超过 60%");
        }
        FarmRecords r = new FarmRecords();
        buildBase(r, "transplant", bo.getPlotId(), bo.getCropId(), bo.getPlantId(), bo.getFarmBy(),
            bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
        r.setTransplantPlot(bo.getTransplantPlot());
        r.setTransplantPercent(bo.getTransplantPercent());
        baseMapper.insert(r);
        return r.getId();
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
        buildBase(r, "rotation", bo.getPlotId(), bo.getCropId(), bo.getPlantId(), bo.getFarmBy(),
            bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
        baseMapper.insert(r);

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
                bo.getFarmBy(), bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
            baseMapper.insert(r);
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
        int count = 0;
        for (DisasterBatchBo.PlotTarget t : bo.getTargets()) {
            FarmRecords r = new FarmRecords();
            buildBase(r, "disaster", t.getPlotId(), t.getCropId(), t.getPlantId(), bo.getFarmBy(),
                bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
            r.setDisasterType(bo.getDisasterType());
            r.setLossRate(bo.getLossRate());
            // 各地块损失产量由系统按 该地块预计产量 × 批量损失率/100（保留两位）算出，不信任前端传值
            BigDecimal lossYield = computeLossYield(t.getPlantId(), t.getPlotId(), t.getCropId(), bo.getLossRate());
            r.setLossYield(lossYield);
            r.setIsWarning(warning);
            baseMapper.insert(r);
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
        buildBase(r, "harvest_activity", bo.getPlotId(), bo.getCropId(), bo.getPlantId(), bo.getFarmBy(),
            bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
        r.setHarvestWeight(bo.getHarvestWeight());
        // 采摘人员（先选班组后从该班组成员中选）落 operator_user_id，与 farm_by(班组) 并存
        r.setOperatorUserId(bo.getOperatorUserId());
        baseMapper.insert(r);
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
        // 幂等拦截（231）：已退茬变空地（plot_status=1）的地块拒绝重复退茬。
        if (plot.getPlotStatus() == null || plot.getPlotStatus() != 3) {
            throw new ServiceException("该地块已退茬，无需重复退茬");
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

        // enrich 片区（FIX-PLT-MP-ROTATION-ZONE-001）：批量按 zone_id 取 zoneCode/zoneName，禁 N+1。
        Set<Long> zoneIds = plotMap.values().stream()
            .map(PlotInfo::getZoneId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PlotZone> zoneMap = zoneIds.isEmpty() ? Map.of()
            : plotZoneMapper.selectByIds(zoneIds).stream()
            .collect(Collectors.toMap(PlotZone::getId, z -> z, (a, b) -> a));

        // 移栽进度层：返各地块累计已移% + 上次时间（FIX-PLT-MP-WORK-BATCH-001 移栽单块录入用）
        Map<Long, Integer> transplantedMap = new HashMap<>();
        if ("transplant".equals(farmType)) {
            for (Map<String, Object> row : baseMapper.selectTransplantedPercentByCrop(cropId)) {
                Object pid = row.get("plotId");
                Object pct = row.get("transplantedPercent");
                if (pid != null) {
                    transplantedMap.put(((Number) pid).longValue(), pct instanceof Number n ? n.intValue() : 0);
                }
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
            if ("transplant".equals(farmType)) {
                vo.setTransplantedPercent(transplantedMap.getOrDefault(d.getPlotId(), 0));
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
     * <p>一次 {@code selectByIds} 取 image_oss_id，再一次 {@code resolveList} 转 public URL，禁 N+1。</p>
     */
    private void fillCropImg(List<FarmCropTargetCardVo> cards, List<Long> cropIds) {
        if (CollUtil.isEmpty(cards) || CollUtil.isEmpty(cropIds)) {
            return;
        }
        Map<Long, String> ossIdMap = cropInfoMapper.selectByIds(cropIds).stream()
            .filter(c -> c.getId() != null)
            .collect(Collectors.toMap(CropInfo::getId, c -> c.getImageOssId() == null ? "" : c.getImageOssId(), (a, b) -> a));
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
        // 定位该地块 + 作物下未结束采摘的明细（harvest_status != completed）
        List<PlantDetails> details = plantDetailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlotId, bo.getPlotId())
                .eq(PlantDetails::getCropId, bo.getCropId()));
        if (CollUtil.isEmpty(details)) {
            throw new ServiceException("该地块下无对应作物的种植明细，无法调整采摘状态");
        }
        Long updateBy = currentUserSafe();
        boolean toCompleted = "completed".equals(bo.getPickStatus());
        LambdaUpdateWrapper<PlantDetails> uw = new LambdaUpdateWrapper<PlantDetails>()
            .eq(PlantDetails::getPlotId, bo.getPlotId())
            .eq(PlantDetails::getCropId, bo.getCropId())
            .set(PlantDetails::getHarvestStatus, bo.getPickStatus())
            .set(PlantDetails::getUpdateBy, updateBy);
        if (toCompleted) {
            uw.set(PlantDetails::getEndHarvestdate, bo.getAdjustDate());
        }
        int affected = plantDetailsMapper.update(null, uw);

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
        w.like(StringUtils.isNotBlank(query.getRecordNo()), FarmRecords::getRecordNo, query.getRecordNo())
            .in(hasWorkTypes, FarmRecords::getFarmType, query.getFarmWorkTypes())
            .eq(!hasWorkTypes && StringUtils.isNotBlank(query.getFarmType()), FarmRecords::getFarmType, query.getFarmType())
            .eq(query.getPlotId() != null, FarmRecords::getPlotId, query.getPlotId())
            // 地块名命中 → IN plotIds；命中为空 → IN (-1) 哨兵 id，确保返回空结果
            .in(filterByPlotName, FarmRecords::getPlotId, CollUtil.isEmpty(plotIdsByName) ? List.of(-1L) : plotIdsByName)
            // 地块编号命中 → IN plotIds；命中为空 → IN (-1) 哨兵 id，确保返回空结果
            .in(filterByPlotCode, FarmRecords::getPlotId, CollUtil.isEmpty(plotIdsByCode) ? List.of(-1L) : plotIdsByCode)
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
     * VO 批量 enrich：plotName / plotCode / teamName / transplantPlotName。
     *
     * <p>cropName 已落表，farmType / disasterType / tillageType 走 ruoyi Excel/前端字典翻译，
     * service 不再额外翻译。</p>
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

        for (FarmRecordsVo vo : list) {
            PlotInfo plot = plotMap.get(vo.getPlotId());
            if (plot != null) {
                vo.setPlotName(plot.getPlotName());
                vo.setPlotCode(plot.getPlotCode());
            }
            PlotInfo transplant = vo.getTransplantPlot() == null ? null : plotMap.get(vo.getTransplantPlot());
            if (transplant != null) {
                vo.setTransplantPlotName(transplant.getPlotName());
            }
            vo.setTeamName(vo.getFarmBy() == null ? null : teamMap.get(vo.getFarmBy()));
        }
    }

    // ============================================================
    // 入参类型分组
    // ============================================================

    private boolean isEmptyFarmType(String type) {
        return "tillage_break".equals(type) || "tillage_prepare".equals(type) || "fertilize".equals(type);
    }

    private boolean isPlainGrowFarmType(String type) {
        // 生长阶段 7 类剔除 rotation / transplant / disaster（独立路径）
        return "water_fertilize".equals(type) || "irrigation".equals(type) || "weed".equals(type)
            || "pest_control".equals(type) || "pruning".equals(type) || "harvest_activity".equals(type);
    }

    /** 仅供测试 inspect。 */
    Map<String, Object> debugStateForTest() {
        Map<String, Object> m = new HashMap<>();
        m.put("warningLossRate", WARNING_LOSS_RATE);
        m.put("farmTypes12", FARM_TYPES_12);
        return m;
    }
}
