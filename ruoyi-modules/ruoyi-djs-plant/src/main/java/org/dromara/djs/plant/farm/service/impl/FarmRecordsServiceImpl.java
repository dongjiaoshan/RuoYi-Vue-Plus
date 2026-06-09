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
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.farm.domain.FarmRecords;
import org.dromara.djs.plant.farm.domain.bo.DisasterRecordBo;
import org.dromara.djs.plant.farm.domain.bo.EmptyRecordBo;
import org.dromara.djs.plant.farm.domain.bo.GrowBatchBo;
import org.dromara.djs.plant.farm.domain.bo.GrowRecordBo;
import org.dromara.djs.plant.farm.domain.bo.PlotPickStatusBo;
import org.dromara.djs.plant.farm.domain.bo.RotationRecordBo;
import org.dromara.djs.plant.farm.domain.bo.TransplantRecordBo;
import org.dromara.djs.plant.farm.domain.query.FarmRecordsQuery;
import org.dromara.djs.plant.farm.domain.vo.DispatchSummaryVo;
import org.dromara.djs.plant.farm.domain.vo.FarmCropPlotVo;
import org.dromara.djs.plant.farm.domain.vo.FarmRecordsVo;
import org.dromara.djs.plant.farm.mapper.FarmRecordsMapper;
import org.dromara.djs.plant.farm.service.IFarmRecordsService;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 *   <li>{@link #submitRotation}：置 {@code plot_info.plot_status=1} + 同 plot 未结束 details {@code plant_status='completed'} + {@code end_actualdate=今日}</li>
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

    private final PlotInfoMapper plotInfoMapper;
    private final CropInfoMapper cropInfoMapper;
    private final PlantDetailsMapper plantDetailsMapper;
    private final PlantWorkTeamMapper teamMapper;

    public FarmRecordsServiceImpl(FarmRecordsMapper baseMapper,
                                  PlotInfoMapper plotInfoMapper,
                                  CropInfoMapper cropInfoMapper,
                                  PlantDetailsMapper plantDetailsMapper,
                                  PlantWorkTeamMapper teamMapper) {
        super(baseMapper);
        this.plotInfoMapper = plotInfoMapper;
        this.cropInfoMapper = cropInfoMapper;
        this.plantDetailsMapper = plantDetailsMapper;
        this.teamMapper = teamMapper;
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
        r.setLossYield(bo.getLossYield());
        r.setIsWarning(bo.getLossRate() != null && bo.getLossRate().compareTo(WARNING_LOSS_RATE) >= 0 ? 1 : 2);
        baseMapper.insert(r);

        // 副作用：累加 plant_details.loss_yield（按 plantId + plotId + cropId 定位未结束 details）
        accumulateLossYield(bo.getPlantId(), bo.getPlotId(), bo.getCropId(), bo.getLossYield());
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
        FarmRecords r = new FarmRecords();
        buildBase(r, "rotation", bo.getPlotId(), bo.getCropId(), bo.getPlantId(), bo.getFarmBy(),
            bo.getFarmDate(), bo.getProofOssIds(), bo.getRemark());
        baseMapper.insert(r);

        // 副作用 1：plot_info.plot_status 回 1（空闲）
        PlotInfo plot = plotInfoMapper.selectById(bo.getPlotId());
        if (plot == null) {
            throw new ServiceException("地块不存在: " + bo.getPlotId());
        }
        plot.setPlotStatus(1);
        plotInfoMapper.updateById(plot);

        // 副作用 2：同 plant + plot 下未结束 details → completed + end_actualdate=今日
        LambdaUpdateWrapper<PlantDetails> uw = new LambdaUpdateWrapper<PlantDetails>()
            .eq(PlantDetails::getPlantId, bo.getPlantId())
            .eq(PlantDetails::getPlotId, bo.getPlotId())
            .isNull(PlantDetails::getEndActualdate)
            .set(PlantDetails::getPlantStatus, "completed")
            .set(PlantDetails::getEndActualdate, LocalDate.now());
        plantDetailsMapper.update(null, uw);
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
            // 退茬副作用：每条 plot_status=1（空闲）+ 同 plant+plot 未结束 details 完结
            if (isRotation) {
                applyRotationSideEffect(target.getPlantId(), target.getPlotId());
            }
        }
        return count;
    }

    /**
     * 退茬副作用（与 {@link #submitRotation} 单条一致，抽出供批量复用）：
     * plot_info.plot_status=1（空闲）+ 同 plant+plot 未结束 details → completed + end_actualdate=今日。
     */
    private void applyRotationSideEffect(Long plantId, Long plotId) {
        PlotInfo plot = plotInfoMapper.selectById(plotId);
        if (plot == null) {
            throw new ServiceException("地块不存在: " + plotId);
        }
        plot.setPlotStatus(1);
        plotInfoMapper.updateById(plot);

        LambdaUpdateWrapper<PlantDetails> uw = new LambdaUpdateWrapper<PlantDetails>()
            .eq(PlantDetails::getPlantId, plantId)
            .eq(PlantDetails::getPlotId, plotId)
            .isNull(PlantDetails::getEndActualdate)
            .set(PlantDetails::getPlantStatus, "completed")
            .set(PlantDetails::getEndActualdate, LocalDate.now());
        plantDetailsMapper.update(null, uw);
    }

    @Override
    public List<FarmCropPlotVo> listCropPlots(Long cropId, String farmType) {
        if (cropId == null) {
            return List.of();
        }
        // 退茬只列采摘完成的地块；其余生长活动列进行中（ongoing）地块
        boolean isRotation = "rotation".equals(farmType);
        String targetPlantStatus = isRotation ? "completed" : "ongoing";
        List<PlantDetails> details = plantDetailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getCropId, cropId)
                .eq(PlantDetails::getPlantStatus, targetPlantStatus)
                .orderByAsc(PlantDetails::getPlotId)
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

        LocalDate today = LocalDate.now();
        List<FarmCropPlotVo> result = new ArrayList<>(details.size());
        for (PlantDetails d : details) {
            FarmCropPlotVo vo = new FarmCropPlotVo();
            vo.setDetailId(d.getId());
            vo.setPlantId(d.getPlantId());
            vo.setPlotId(d.getPlotId());
            vo.setCropId(d.getCropId());
            vo.setPlantStatus(d.getPlantStatus());
            PlotInfo plot = plotMap.get(d.getPlotId());
            if (plot != null) {
                vo.setPlotCode(plot.getPlotCode());
                vo.setPlotName(plot.getPlotName());
            }
            LocalDate last = lastDateMap.get(d.getPlotId());
            if (last != null) {
                vo.setLastFarmDate(last);
                vo.setIntervalDays((int) ChronoUnit.DAYS.between(last, today));
            }
            result.add(vo);
        }
        return result;
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
        baseMapper.insert(trace);
        return affected;
    }

    // ============================================================
    // mp 中央分发台 + 我的记录
    // ============================================================

    @Override
    public DispatchSummaryVo dispatchSummary() {
        LocalDate today = LocalDate.now();
        // 一次 selectList 拿今日所有 farm_type，service 内 group by（V1 数据量小，不写 COUNT GROUP BY SQL）
        List<FarmRecords> todayList = baseMapper.selectList(
            new LambdaQueryWrapper<FarmRecords>()
                .eq(FarmRecords::getFarmDate, today)
                .select(FarmRecords::getId, FarmRecords::getFarmType));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String type : FARM_TYPES_12) {
            counts.put(type, 0);
        }
        for (FarmRecords r : todayList) {
            counts.merge(r.getFarmType(), 1, Integer::sum);
        }
        DispatchSummaryVo vo = new DispatchSummaryVo();
        vo.setCounts(counts);
        return vo;
    }

    @Override
    public TableDataInfo<FarmRecordsVo> myRecords(FarmRecordsQuery query, PageQuery pageQuery) {
        // mp 我的记录：按 operatorId（=登录用户）反查所属班组，再过滤 farmBy
        if (query.getOperatorId() != null) {
            List<Long> teamIds = teamMapper.selectList(
                new LambdaQueryWrapper<PlantWorkTeam>()
                    .eq(PlantWorkTeam::getLeaderId, query.getOperatorId())
                    .select(PlantWorkTeam::getId))
                .stream().map(PlantWorkTeam::getId).collect(Collectors.toList());
            if (CollUtil.isEmpty(teamIds)) {
                return TableDataInfo.build(new Page<>());
            }
            Page<FarmRecordsVo> page = baseMapper.selectVoPage(pageQuery.build(),
                new LambdaQueryWrapper<FarmRecords>()
                    .in(FarmRecords::getFarmBy, teamIds)
                    .orderByDesc(FarmRecords::getFarmDate)
                    .orderByDesc(FarmRecords::getId));
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
        w.like(StringUtils.isNotBlank(query.getRecordNo()), FarmRecords::getRecordNo, query.getRecordNo())
            .in(hasWorkTypes, FarmRecords::getFarmType, query.getFarmWorkTypes())
            .eq(!hasWorkTypes && StringUtils.isNotBlank(query.getFarmType()), FarmRecords::getFarmType, query.getFarmType())
            .eq(query.getPlotId() != null, FarmRecords::getPlotId, query.getPlotId())
            .eq(query.getCropId() != null, FarmRecords::getCropId, query.getCropId())
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
            PlotInfo transplant = plotMap.get(vo.getTransplantPlot());
            if (transplant != null) {
                vo.setTransplantPlotName(transplant.getPlotName());
            }
            vo.setTeamName(teamMap.get(vo.getFarmBy()));
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
