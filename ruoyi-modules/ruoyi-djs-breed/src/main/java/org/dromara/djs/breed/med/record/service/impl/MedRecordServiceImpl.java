package org.dromara.djs.breed.med.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.med.domain.MedBatch;
import org.dromara.djs.breed.med.mapper.MedBatchMapper;
import org.dromara.djs.breed.med.record.domain.MedRecord;
import org.dromara.djs.breed.med.record.domain.bo.MedRecordBatchBo;
import org.dromara.djs.breed.med.record.domain.bo.MedRecordBo;
import org.dromara.djs.breed.med.record.domain.query.MedRecordQuery;
import org.dromara.djs.breed.med.record.domain.vo.MedRecordVo;
import org.dromara.djs.breed.med.record.domain.vo.UsableBatchVo;
import org.dromara.djs.breed.med.record.mapper.MedRecordMapper;
import org.dromara.djs.breed.med.record.service.IMedRecordService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.medicine.api.MedicineProductDto;
import org.dromara.djs.common.medicine.api.MedicineStockProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用药治疗流水 Service 实现（BRD-MED-003）。
 *
 * <p>核心逻辑（药品废弃批次 · 迁药品维度）：</p>
 * <ul>
 *   <li><b>使用药品下拉</b>：{@link MedicineStockProvider#listRecentPickedMedicineIds} 取近 3 天已领用的
 *       distinct 药品（数据源 = 仓库领用出库流水 dept_pick_out，覆盖疫苗药品页 + 物资领用药品库两个领用入口，
 *       row131；按 operatorId 限定 mp 当前用户），药品名/单位/规格/库存由仓库
 *       {@link MedicineStockProvider#listMedicineProductsByIds} 解析。不再依赖批次/t_breed_medicine_info。</li>
 *   <li><b>用药 = 纯记录，不扣库存</b>：库存扣减只在药品「领用/退回」（MedUsage）做，用药只能选
 *       3 天内已领药品（领用时已扣过库存），用药再扣会双扣，故用药仅落台账。{@code dosageUnit}
 *       为纯记录/展示字段（g/mg/ml/L/kg/片等），不做规格换算、不参与扣减。</li>
 *   <li><b>事务包</b>：INSERT master + N detail 单事务，任一步失败全回滚。
 *       N ≤ 200（DDL 已限定，BO {@code @Size(max=200)}）。</li>
 *   <li><b>operator_id 必填</b>：ADR-0007，所有 INSERT 取 LoginHelper.getUserId
 *       + 同步快照 sys_user.nick_name 到 operator_name。</li>
 *   <li><b>软删语义</b>：与 MedUsage 同（用药历史不可逆）。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-MED-003
 */
@Slf4j
@Service
public class MedRecordServiceImpl extends DjsBaseServiceImpl<MedRecordMapper, MedRecord> implements IMedRecordService {

    /** 单批次用药最大头数（与 BO {@code @Size(max=200)} 一致）。 */
    private static final int MAX_BATCH_SIZE = 200;

    private final MedBatchMapper medBatchMapper;
    private final PigMapper pigMapper;
    private final MedicineStockProvider medicineStockProvider;

    public MedRecordServiceImpl(MedRecordMapper baseMapper,
                                MedBatchMapper medBatchMapper,
                                PigMapper pigMapper,
                                MedicineStockProvider medicineStockProvider) {
        super(baseMapper);
        this.medBatchMapper = medBatchMapper;
        this.pigMapper = pigMapper;
        this.medicineStockProvider = medicineStockProvider;
    }

    // ---------------------------------------------------------------
    // 写入入口
    // ---------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addSingle(MedRecordBo bo) {
        // 1. 解析药品（药品即仓库商品，废弃批次；名从仓库 provider 拿）
        String medicineName = resolveMedicineName(bo.getMedicineId());

        // 2. 校验 pig 存在 + 非终态
        Pig pig = pigMapper.selectById(bo.getPigId());
        if (pig == null || "1".equals(pig.getDelFlag())) {
            throw new ServiceException("猪只不存在或已删除：" + bo.getPigId());
        }
        if (PigLifecycle.END.name().equals(pig.getCurrentStatus())) {
            throw new ServiceException("猪只已处终态（END）不可用药：" + pig.getEarNo());
        }

        // 3. INSERT 单只 record（drug_type=1）
        MedRecord entity = toEntity(bo);
        entity.setDrugType(1);
        entity.setEarNo(pig.getEarNo());
        // row139：落用药时的猪只类型 + 状态快照（供列表显示 + 按类型搜索）
        entity.setPigType(pig.getPigType());
        entity.setPigStatus(pig.getCurrentStatus());
        entity.setMedicineName(medicineName);
        fillOperator(entity, bo.getOperatorId(), bo.getOperatorName());
        baseMapper.insert(entity);

        log.info("[BRD-MED-003] addSingle pigId={} earNo={} medicineId={} dosage={} recordId={}",
            pig.getId(), pig.getEarNo(), bo.getMedicineId(), bo.getMedicineDosage(), entity.getId());
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addBatch(MedRecordBatchBo bo) {
        int n = bo.getPigIds().size();
        if (n > MAX_BATCH_SIZE) {
            throw new ServiceException("批量用药一次最多 " + MAX_BATCH_SIZE + " 头：当前 " + n);
        }

        // 1. 解析药品（药品即仓库商品，废弃批次）
        String medicineName = resolveMedicineName(bo.getMedicineId());

        // 2. 校验全部 pig 存在 + 非终态
        Set<Long> pigIdSet = new HashSet<>(bo.getPigIds());
        if (pigIdSet.size() != n) {
            throw new ServiceException("批量用药猪只列表有重复");
        }
        List<Pig> pigs = pigMapper.selectByIds(pigIdSet);
        if (pigs.size() != n) {
            throw new ServiceException("部分猪只不存在：传 " + n + " 头，查到 " + pigs.size() + " 头");
        }
        for (Pig p : pigs) {
            if (PigLifecycle.END.name().equals(p.getCurrentStatus())) {
                throw new ServiceException("批量用药包含终态猪只：" + p.getEarNo());
            }
        }

        // 3. 计算合计用量（仅记录用，不扣库存）
        BigDecimal totalDosage = bo.getMedicineDosage().multiply(BigDecimal.valueOf(n));

        // 4. INSERT 1 条 master + N 条 detail
        MedRecord master = new MedRecord();
        master.setUseDate(bo.getUseDate());
        master.setDrugType(2);
        master.setMedicineType(bo.getMedicineType());
        master.setVaccineType(bo.getVaccineType());
        master.setMedicineReason(bo.getMedicineReason());
        master.setMedicineWay(bo.getMedicineWay());
        master.setMedicineId(bo.getMedicineId());
        master.setMedicineName(medicineName);
        master.setBatchId(bo.getBatchId());
        master.setUsageId(bo.getUsageId());
        master.setScheduleId(bo.getScheduleId());
        master.setMedicineDosage(totalDosage); // master 行存合计
        master.setDosageUnit(bo.getDosageUnit());
        master.setRemark(bo.getRemark());
        fillOperator(master, bo.getOperatorId(), bo.getOperatorName());
        baseMapper.insert(master);

        // detail rows
        List<MedRecord> details = new ArrayList<>(n);
        for (Pig p : pigs) {
            MedRecord d = new MedRecord();
            d.setUseDate(bo.getUseDate());
            d.setPigId(p.getId());
            d.setEarNo(p.getEarNo());
            // row139：detail 行落每头猪当时的类型 + 状态快照
            d.setPigType(p.getPigType());
            d.setPigStatus(p.getCurrentStatus());
            d.setMasterId(master.getId());
            d.setDrugType(3);
            d.setMedicineType(bo.getMedicineType());
            d.setVaccineType(bo.getVaccineType());
            d.setMedicineReason(bo.getMedicineReason());
            d.setMedicineWay(bo.getMedicineWay());
            d.setMedicineId(bo.getMedicineId());
            d.setMedicineName(medicineName);
            d.setBatchId(bo.getBatchId());
            d.setUsageId(bo.getUsageId());
            d.setScheduleId(bo.getScheduleId());
            d.setMedicineDosage(bo.getMedicineDosage());
            d.setDosageUnit(bo.getDosageUnit());
            d.setRemark(bo.getRemark());
            fillOperator(d, bo.getOperatorId(), bo.getOperatorName());
            details.add(d);
        }
        baseMapper.insertBatch(details);

        log.info("[BRD-MED-003] addBatch n={} medicineId={} totalDosage={} masterId={}",
            n, bo.getMedicineId(), totalDosage, master.getId());
        return master.getId();
    }

    // ---------------------------------------------------------------
    // 查询
    // ---------------------------------------------------------------

    @Override
    public TableDataInfo<MedRecordVo> queryPage(MedRecordQuery query, PageQuery pageQuery) {
        Page<MedRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), buildWrapper(query));
        enrichBatchNo(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<MedRecordVo> queryList(MedRecordQuery query) {
        List<MedRecordVo> list = baseMapper.selectVoList(buildWrapper(query));
        enrichBatchNo(list);
        return list;
    }

    @Override
    public MedRecordVo queryById(Long id) {
        MedRecordVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            enrichBatchNo(List.of(vo));
        }
        return vo;
    }

    @Override
    public List<MedRecordVo> queryByMasterId(Long masterId) {
        LambdaQueryWrapper<MedRecord> w = new LambdaQueryWrapper<MedRecord>()
            .eq(MedRecord::getMasterId, masterId)
            .orderByAsc(MedRecord::getId);
        List<MedRecordVo> list = baseMapper.selectVoList(w);
        enrichBatchNo(list);
        return list;
    }

    /**
     * 列表 enrich：批次号替代裸 batchId（earNo/medicineName/operatorName 已在写入时落库）。
     * 收集去重 batchId 一次性批查，避免 N+1。
     */
    private void enrichBatchNo(List<MedRecordVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> batchIds = rows.stream().map(MedRecordVo::getBatchId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (batchIds.isEmpty()) {
            return;
        }
        Map<Long, String> batchNos = medBatchMapper.selectByIds(batchIds).stream()
            .filter(b -> b.getId() != null && b.getBatchNo() != null)
            .collect(Collectors.toMap(MedBatch::getId, MedBatch::getBatchNo, (a, b) -> a));
        for (MedRecordVo vo : rows) {
            vo.setBatchNo(batchNos.get(vo.getBatchId()));
        }
    }

    @Override
    public List<UsableBatchVo> listUsableBatches(Long operatorId) {
        // 药品废弃批次：取近 3 天已领用的 distinct 药品 id，再经仓库 provider 解析名/单位/规格/库存。
        // row131：数据源改为仓库领用出库流水（覆盖疫苗药品页 + 物资领用药品库两个领用入口），
        // 取代原「只查 t_breed_medicine_usage」（只覆盖疫苗药品页入口）。
        List<Long> ids = medicineStockProvider.listRecentPickedMedicineIds(operatorId);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, MedicineProductDto> dtoMap = medicineStockProvider.listMedicineProductsByIds(ids).stream()
            .collect(Collectors.toMap(MedicineProductDto::getId, Function.identity(), (a, b) -> a));
        List<UsableBatchVo> list = new ArrayList<>(ids.size());
        for (Long id : ids) { // 保持 mapper 的"最近领用倒序"
            MedicineProductDto dto = dtoMap.get(id);
            if (dto == null) {
                continue; // 药品已软删/不在药品库 → 跳过
            }
            UsableBatchVo vo = new UsableBatchVo();
            vo.setMedicineId(dto.getId());
            vo.setMedicineName(dto.getName());
            vo.setSpec(dto.getSpec());
            vo.setUnit(dto.getUnit());
            vo.setQuantity(dto.getStock());
            list.add(vo);
        }
        return list;
    }

    @Override
    public int deleteByIds(Collection<Long> ids) {
        // 软删不回滚库存（与 MedUsage 同语义）
        return softDelete(ids);
    }

    // ---------------------------------------------------------------
    // 内部
    // ---------------------------------------------------------------

    /**
     * BO → Entity 转换钩子（抽 protected 方便单测覆盖）。
     */
    protected MedRecord toEntity(MedRecordBo bo) {
        MedRecord entity = MapstructUtils.convert(bo, MedRecord.class);
        if (entity == null) {
            throw new ServiceException("用药入参转换失败");
        }
        return entity;
    }

    /**
     * 解析药品名（药品即仓库商品，废弃 t_breed_medicine_info 目录）。
     * 药品不存在（软删/非药品库）→ 抛业务异常。
     */
    private String resolveMedicineName(Long medicineId) {
        if (medicineId == null) {
            throw new ServiceException("药品 ID 不能为空");
        }
        List<MedicineProductDto> products =
            medicineStockProvider.listMedicineProductsByIds(List.of(medicineId));
        if (products.isEmpty()) {
            throw new ServiceException("药品不存在或已删除：" + medicineId);
        }
        return products.get(0).getName();
    }

    /**
     * 填充 operator_id + operator_name（ADR-0007 强制）。
     *
     * <p>从 sa-token {@link LoginHelper} 拿 LoginUser，避免引入 ruoyi-system 模块依赖。</p>
     */
    private void fillOperator(MedRecord entity, Long boOperatorId, String boOperatorName) {
        // 前端选了用药人（默认当前登录人，可改选其他员工）→ 用所选；EmployeePicker 一并传 name 快照，免跨模块查 sys_user。
        if (boOperatorId != null) {
            entity.setOperatorId(boOperatorId);
            if (StringUtils.isNotBlank(boOperatorName)) {
                entity.setOperatorName(boOperatorName);
            }
            return;
        }
        // 未选 → 回落当前登录人（ADR-0007）。
        Long userId;
        LoginUser loginUser;
        try {
            userId = LoginHelper.getUserId();
            loginUser = LoginHelper.getLoginUser();
        } catch (Exception ignore) {
            return; // 单测 / 后台任务场景，跳过
        }
        if (userId == null) {
            return;
        }
        entity.setOperatorId(userId);
        if (loginUser != null) {
            String name = StringUtils.isNotBlank(loginUser.getNickname())
                ? loginUser.getNickname() : loginUser.getUsername();
            entity.setOperatorName(name);
        }
    }

    private QueryWrapper<MedRecord> buildWrapper(MedRecordQuery q) {
        QueryWrapper<MedRecord> w = new QueryWrapper<>();
        if (q == null) {
            return w.orderByDesc("use_date").orderByDesc("id");
        }
        w.eq(q.getPigId() != null, "pig_id", q.getPigId())
            .like(StringUtils.isNotBlank(q.getEarNo()), "ear_no", q.getEarNo())
            .like(StringUtils.isNotBlank(q.getMedicineName()), "medicine_name", q.getMedicineName())
            .eq(StringUtils.isNotBlank(q.getPigType()), "pig_type", q.getPigType())
            .eq(StringUtils.isNotBlank(q.getMedicineType()), "medicine_type", q.getMedicineType())
            .eq(q.getDrugType() != null, "drug_type", q.getDrugType())
            .eq(q.getBatchId() != null, "batch_id", q.getBatchId())
            .eq(q.getMedicineId() != null, "medicine_id", q.getMedicineId())
            .eq(q.getOperatorId() != null, "operator_id", q.getOperatorId())
            .ge(q.getBeginDate() != null, "use_date",
                q.getBeginDate() != null ? LocalDateTime.of(q.getBeginDate(), java.time.LocalTime.MIN) : null)
            .le(q.getEndDate() != null, "use_date",
                q.getEndDate() != null ? LocalDateTime.of(q.getEndDate(), java.time.LocalTime.MAX) : null)
            .orderByDesc("use_date")
            .orderByDesc("id");
        return w;
    }

}
