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
import org.dromara.djs.breed.med.domain.Medicine;
import org.dromara.djs.breed.med.mapper.MedBatchMapper;
import org.dromara.djs.breed.med.mapper.MedicineMapper;
import org.dromara.djs.breed.med.record.domain.MedRecord;
import org.dromara.djs.breed.med.record.domain.bo.MedRecordBatchBo;
import org.dromara.djs.breed.med.record.domain.bo.MedRecordBo;
import org.dromara.djs.breed.med.record.domain.query.MedRecordQuery;
import org.dromara.djs.breed.med.record.domain.vo.MedRecordVo;
import org.dromara.djs.breed.med.record.domain.vo.UsableBatchVo;
import org.dromara.djs.breed.med.record.mapper.MedRecordMapper;
import org.dromara.djs.breed.med.record.service.IMedRecordService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
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
import java.util.stream.Collectors;

/**
 * 用药治疗流水 Service 实现（BRD-MED-003）。
 *
 * <p>核心逻辑：</p>
 * <ul>
 *   <li><b>3 天领用窗口校验</b>：addSingle / addBatch 入口校验 batchId 必须出现在
 *       {@link MedRecordMapper#selectUsableBatchesByPig} 返回集合中（按 operatorId 限定
 *       当前 mp 用户领过），否则 {@code medicine.batch.expired_for_use}。admin 端
 *       不限 operator，允许跨用户用药。</li>
 *   <li><b>批量原子扣减</b>：addBatch 触发的 quantity 扣减 = dosage × N，
 *       走 {@link MedBatchMapper#decrementQuantity}（WHERE quantity ≥ qty 防超扣）；
 *       失败抛 {@code medicine.batch.insufficient}。</li>
 *   <li><b>事务包</b>：扣减库存 + INSERT master + N detail 单事务，
 *       任一步失败全回滚。N ≤ 200（DDL 已限定，BO {@code @Size(max=200)}）。</li>
 *   <li><b>operator_id 必填</b>：ADR-0007，所有 INSERT 取 LoginHelper.getUserId
 *       + 同步快照 sys_user.nick_name 到 operator_name。</li>
 *   <li><b>软删不回滚库存</b>：与 MedUsage 同语义（用药历史不可逆）。</li>
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
    private final MedicineMapper medicineMapper;
    private final PigMapper pigMapper;

    public MedRecordServiceImpl(MedRecordMapper baseMapper,
                                MedBatchMapper medBatchMapper,
                                MedicineMapper medicineMapper,
                                PigMapper pigMapper) {
        super(baseMapper);
        this.medBatchMapper = medBatchMapper;
        this.medicineMapper = medicineMapper;
        this.pigMapper = pigMapper;
    }

    // ---------------------------------------------------------------
    // 写入入口
    // ---------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addSingle(MedRecordBo bo) {
        // 1. 校验药品 / 批次 / 归属
        Medicine medicine = checkMedicine(bo.getMedicineId());
        MedBatch batch = checkBatch(bo.getBatchId(), bo.getMedicineId());

        // 2. 校验 pig 存在 + 非终态
        Pig pig = pigMapper.selectById(bo.getPigId());
        if (pig == null || "1".equals(pig.getDelFlag())) {
            throw new ServiceException("猪只不存在或已删除：" + bo.getPigId());
        }
        if (PigLifecycle.END.name().equals(pig.getCurrentStatus())) {
            throw new ServiceException("猪只已处终态（END）不可用药：" + pig.getEarNo());
        }

        // 3. 3 天领用窗口校验（仅 mp 调用走 LoginHelper；admin 跳过）
        checkBatchUsableForCurrentUser(bo.getBatchId());

        // 4. 扣 batch.quantity（原子）
        decrementBatch(batch, bo.getMedicineDosage());

        // 5. INSERT 单只 record（drug_type=1）
        MedRecord entity = toEntity(bo);
        entity.setDrugType(1);
        entity.setEarNo(pig.getEarNo());
        entity.setMedicineName(medicine.getMedicineName());
        fillOperator(entity);
        baseMapper.insert(entity);

        log.info("[BRD-MED-003] addSingle pigId={} earNo={} batchId={} dosage={} recordId={}",
            pig.getId(), pig.getEarNo(), batch.getId(), bo.getMedicineDosage(), entity.getId());
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addBatch(MedRecordBatchBo bo) {
        int n = bo.getPigIds().size();
        if (n > MAX_BATCH_SIZE) {
            throw new ServiceException("批量用药一次最多 " + MAX_BATCH_SIZE + " 头：当前 " + n);
        }

        // 1. 校验药品 / 批次
        Medicine medicine = checkMedicine(bo.getMedicineId());
        MedBatch batch = checkBatch(bo.getBatchId(), bo.getMedicineId());

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

        // 3. 3 天领用窗口校验
        checkBatchUsableForCurrentUser(bo.getBatchId());

        // 4. 一次扣 dosage × N
        BigDecimal totalDosage = bo.getMedicineDosage().multiply(BigDecimal.valueOf(n));
        decrementBatch(batch, totalDosage);

        // 5. INSERT 1 条 master + N 条 detail
        MedRecord master = new MedRecord();
        master.setUseDate(bo.getUseDate());
        master.setDrugType(2);
        master.setMedicineType(bo.getMedicineType());
        master.setVaccineType(bo.getVaccineType());
        master.setMedicineReason(bo.getMedicineReason());
        master.setMedicineWay(bo.getMedicineWay());
        master.setMedicineId(bo.getMedicineId());
        master.setMedicineName(medicine.getMedicineName());
        master.setBatchId(bo.getBatchId());
        master.setUsageId(bo.getUsageId());
        master.setScheduleId(bo.getScheduleId());
        master.setMedicineDosage(totalDosage); // master 行存合计
        master.setRemark(bo.getRemark());
        fillOperator(master);
        baseMapper.insert(master);

        // detail rows
        List<MedRecord> details = new ArrayList<>(n);
        for (Pig p : pigs) {
            MedRecord d = new MedRecord();
            d.setUseDate(bo.getUseDate());
            d.setPigId(p.getId());
            d.setEarNo(p.getEarNo());
            d.setMasterId(master.getId());
            d.setDrugType(3);
            d.setMedicineType(bo.getMedicineType());
            d.setVaccineType(bo.getVaccineType());
            d.setMedicineReason(bo.getMedicineReason());
            d.setMedicineWay(bo.getMedicineWay());
            d.setMedicineId(bo.getMedicineId());
            d.setMedicineName(medicine.getMedicineName());
            d.setBatchId(bo.getBatchId());
            d.setUsageId(bo.getUsageId());
            d.setScheduleId(bo.getScheduleId());
            d.setMedicineDosage(bo.getMedicineDosage());
            d.setRemark(bo.getRemark());
            fillOperator(d);
            details.add(d);
        }
        baseMapper.insertBatch(details);

        log.info("[BRD-MED-003] addBatch n={} batchId={} totalDosage={} masterId={}",
            n, batch.getId(), totalDosage, master.getId());
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
        return baseMapper.selectUsableBatchesByPig(operatorId);
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

    private Medicine checkMedicine(Long medicineId) {
        Medicine medicine = medicineMapper.selectById(medicineId);
        if (medicine == null || "1".equals(medicine.getDelFlag())) {
            throw new ServiceException("药品不存在或已删除：" + medicineId);
        }
        return medicine;
    }

    private MedBatch checkBatch(Long batchId, Long medicineId) {
        MedBatch batch = medBatchMapper.selectById(batchId);
        if (batch == null || "1".equals(batch.getDelFlag())) {
            throw new ServiceException("批次不存在或已删除：" + batchId);
        }
        if (!Objects.equals(batch.getMedicineId(), medicineId)) {
            throw new ServiceException("药品 ID 与批次归属不一致：batch.medicineId=" + batch.getMedicineId());
        }
        return batch;
    }

    /**
     * 校验"当前 mp 用户 3 天内领过该批次"。LoginHelper 取不到（admin 端直传）时跳过。
     */
    private void checkBatchUsableForCurrentUser(Long batchId) {
        Long userId;
        try {
            userId = LoginHelper.getUserId();
        } catch (Exception ignore) {
            return; // admin 端 / 单测环境
        }
        if (userId == null) {
            return;
        }
        List<UsableBatchVo> usable = baseMapper.selectUsableBatchesByPig(userId);
        boolean ok = usable.stream().anyMatch(v -> Objects.equals(v.getBatchId(), batchId));
        if (!ok) {
            throw new ServiceException("批次不在 3 天内已领可用范围：batchId=" + batchId);
        }
    }

    /**
     * 原子扣减批次 quantity，失败抛 {@code medicine.batch.insufficient}。
     */
    private void decrementBatch(MedBatch batch, BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw new ServiceException("用药剂量必须大于 0");
        }
        int affected = medBatchMapper.decrementQuantity(batch.getId(), qty);
        if (affected == 0) {
            throw new ServiceException("批次库存不足：batchId=" + batch.getId()
                + "，剩余=" + batch.getQuantity() + "，申请=" + qty);
        }
    }

    /**
     * 填充 operator_id + operator_name（ADR-0007 强制）。
     *
     * <p>从 sa-token {@link LoginHelper} 拿 LoginUser，避免引入 ruoyi-system 模块依赖。</p>
     */
    private void fillOperator(MedRecord entity) {
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
