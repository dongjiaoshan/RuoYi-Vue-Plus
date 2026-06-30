package org.dromara.djs.breed.med.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.dromara.djs.breed.med.domain.MedBatch;
import org.dromara.djs.breed.med.domain.MedUsage;
import org.dromara.djs.breed.med.domain.Medicine;
import org.dromara.djs.breed.med.domain.bo.MedUsageBo;
import org.dromara.djs.breed.med.domain.query.MedUsageQuery;
import org.dromara.djs.breed.med.domain.vo.MedUsageVo;
import org.dromara.djs.breed.med.mapper.MedBatchMapper;
import org.dromara.djs.breed.med.mapper.MedUsageMapper;
import org.dromara.djs.breed.med.mapper.MedicineMapper;
import org.dromara.djs.breed.med.service.IMedUsageService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.medicine.api.MedicineStockProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 药品领用台账 Service 实现（BRD-MED-002）。
 *
 * <p>核心逻辑：</p>
 * <ul>
 *   <li><b>库存真值在仓库</b>（ADR-0012 药品归仓库库位统一）：use / loss 走
 *       {@link MedicineStockProvider#deduct}（落仓库 location_stock，库存不足抛业务异常自然回滚），
 *       return 走 {@link MedicineStockProvider#add}。批次行 {@code t_breed_medicine_batch.quantity}
 *       退化为入库快照（不再随领用扣减），但 batchId 仍写台账行作溯源。</li>
 *   <li><b>幂等性</b>：本方法不做幂等去重，依赖 Controller 上的
 *       {@code @RepeatSubmit} 防同一用户短时重复 POST。</li>
 *   <li><b>软删不回滚库存</b>：已领用历史不可逆，对账不允许凭空消失（约束 #5）。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-MED-002
 */
@Slf4j
@Service
public class MedUsageServiceImpl extends DjsBaseServiceImpl<MedUsageMapper, MedUsage> implements IMedUsageService {

    private static final String TYPE_USE = "use";
    private static final String TYPE_RETURN = "return";
    private static final String TYPE_LOSS = "loss";

    private final MedBatchMapper medBatchMapper;
    private final MedicineMapper medicineMapper;
    private final PigMapper pigMapper;
    private final PenMapper penMapper;
    private final MedicineStockProvider medicineStockProvider;

    public MedUsageServiceImpl(MedUsageMapper baseMapper, MedBatchMapper medBatchMapper,
                               MedicineMapper medicineMapper, PigMapper pigMapper, PenMapper penMapper,
                               MedicineStockProvider medicineStockProvider) {
        super(baseMapper);
        this.medBatchMapper = medBatchMapper;
        this.medicineMapper = medicineMapper;
        this.pigMapper = pigMapper;
        this.penMapper = penMapper;
        this.medicineStockProvider = medicineStockProvider;
    }

    @Override
    public TableDataInfo<MedUsageVo> queryPageList(MedUsageQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<MedUsage> wrapper = buildQueryWrapper(query);
        Page<MedUsageVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrich(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<MedUsageVo> queryList(MedUsageQuery query) {
        List<MedUsageVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        enrich(list);
        return list;
    }

    @Override
    public MedUsageVo queryById(Long id) {
        MedUsageVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            enrich(List.of(vo));
        }
        return vo;
    }

    /**
     * 列表 enrich：批量装配业务码/名称替代裸 snowflake id（药品名 / 批次号 / 耳号 / 栏位编码）。
     * <p>按列收集去重 id 一次性批查，避免 N+1。</p>
     */
    private void enrich(List<MedUsageVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<Long, String> medicineNames = batchLookup(rows, MedUsageVo::getMedicineId,
            ids -> medicineMapper.selectByIds(ids), Medicine::getId, Medicine::getMedicineName);
        Map<Long, String> batchNos = batchLookup(rows, MedUsageVo::getBatchId,
            ids -> medBatchMapper.selectByIds(ids), MedBatch::getId, MedBatch::getBatchNo);
        Map<Long, String> earNos = batchLookup(rows, MedUsageVo::getPigId,
            ids -> pigMapper.selectByIds(ids), Pig::getId, Pig::getEarNo);
        Map<Long, String> penCodes = batchLookup(rows, MedUsageVo::getRelatedPenId,
            ids -> penMapper.selectByIds(ids), Pen::getId, Pen::getPenCode);
        for (MedUsageVo vo : rows) {
            vo.setMedicineName(medicineNames.get(vo.getMedicineId()));
            vo.setBatchNo(batchNos.get(vo.getBatchId()));
            vo.setEarNo(earNos.get(vo.getPigId()));
            vo.setPenCode(penCodes.get(vo.getRelatedPenId()));
        }
    }

    /**
     * 通用批查辅助：从 rows 收集非空 id → 一次 selectByIds → 映射成 id→displayValue。
     */
    private <E> Map<Long, String> batchLookup(List<MedUsageVo> rows,
                                              Function<MedUsageVo, Long> idGetter,
                                              Function<Collection<Long>, List<E>> loader,
                                              Function<E, Long> entityId,
                                              Function<E, String> entityValue) {
        Set<Long> ids = rows.stream().map(idGetter).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            // 必须用 emptyMap（get(null) 返 null），不能用 Map.of()（不可变 Map 对 null key 的 get 抛 NPE）：
            // 领用行 pig_id / related_pen_id 可为空，整页全空时 ids 为空走这里，下游按 vo.getPigId()(可能 null) 取值
            return Collections.emptyMap();
        }
        return loader.apply(ids).stream()
            .filter(e -> entityId.apply(e) != null && entityValue.apply(e) != null)
            .collect(Collectors.toMap(entityId, entityValue, (a, b) -> a));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(MedUsageBo bo) {
        // r51 去批次：batchId 可选。传了则校验批次归属（兼容旧链路 + 写台账作溯源），
        //   没传则直接按 bo.medicineId 落账。库存扣减始终按 medicineId（真值在仓库 location_stock）。
        Long medicineId = bo.getMedicineId();
        if (bo.getBatchId() != null) {
            MedBatch batch = medBatchMapper.selectById(bo.getBatchId());
            if (batch == null || "1".equals(batch.getDelFlag())) {
                throw new ServiceException("批次不存在或已删除：" + bo.getBatchId());
            }
            // 批次药品归属强校验：前端传错也按 batch 实际归属落账
            if (!Objects.equals(batch.getMedicineId(), bo.getMedicineId())) {
                throw new ServiceException("药品 ID 与批次归属不一致：batch.medicineId=" + batch.getMedicineId());
            }
            medicineId = batch.getMedicineId();
        }

        // 2. 按 usageType 扣减或归还仓库库存（ADR-0012：库存真值在仓库 location_stock，按 medicineId）
        String type = StringUtils.isBlank(bo.getUsageType()) ? TYPE_USE : bo.getUsageType();
        BigDecimal qty = bo.getUsageQty();
        Long operatorId = LoginHelper.getUserId();
        switch (type) {
            // deduct 库存不足自抛 ServiceException → @Transactional 自然回滚（不 catch 吞）
            case TYPE_USE, TYPE_LOSS -> medicineStockProvider.deduct(medicineId, qty, operatorId);
            case TYPE_RETURN -> medicineStockProvider.add(medicineId, qty, operatorId);
            default -> throw new ServiceException("无效的领用类型：" + type);
        }

        // 3. INSERT 台账
        MedUsage entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("药品领用入参转换失败");
        }
        entity.setUsageType(type);
        return baseMapper.insert(entity);
    }

    /**
     * BO → Entity 转换钩子（抽 protected 方便单测覆盖，规避 MapStruct Spring 上下文依赖）。
     */
    protected MedUsage toEntity(MedUsageBo bo) {
        return MapstructUtils.convert(bo, MedUsage.class);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        // 注意：软删不回滚库存（约束 #5；已领用历史不可逆，对账不允许凭空消失）
        return softDelete(ids);
    }

    @Override
    public Map<String, BigDecimal> todayStat() {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<MedUsage> wrapper = new LambdaQueryWrapper<MedUsage>()
            .eq(MedUsage::getUseDate, today);
        List<MedUsageVo> todays = baseMapper.selectVoList(wrapper);

        Map<String, BigDecimal> stat = new LinkedHashMap<>();
        stat.put(TYPE_USE, BigDecimal.ZERO);
        stat.put(TYPE_RETURN, BigDecimal.ZERO);
        stat.put(TYPE_LOSS, BigDecimal.ZERO);
        for (MedUsageVo vo : todays) {
            String t = vo.getUsageType();
            BigDecimal q = vo.getUsageQty();
            if (t == null || q == null || !stat.containsKey(t)) {
                continue;
            }
            stat.put(t, stat.get(t).add(q));
        }
        return stat;
    }

    /**
     * 构造查询条件。
     */
    private LambdaQueryWrapper<MedUsage> buildQueryWrapper(MedUsageQuery query) {
        LambdaQueryWrapper<MedUsage> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(MedUsage::getId);
        }
        wrapper.eq(Objects.nonNull(query.getMedicineId()), MedUsage::getMedicineId, query.getMedicineId())
            .eq(Objects.nonNull(query.getBatchId()), MedUsage::getBatchId, query.getBatchId())
            .eq(StringUtils.isNotBlank(query.getUsageType()), MedUsage::getUsageType, query.getUsageType())
            .eq(Objects.nonNull(query.getPigId()), MedUsage::getPigId, query.getPigId())
            .ge(Objects.nonNull(query.getUseDateFrom()), MedUsage::getUseDate, query.getUseDateFrom())
            .le(Objects.nonNull(query.getUseDateTo()), MedUsage::getUseDate, query.getUseDateTo())
            .orderByDesc(MedUsage::getUseDate)
            .orderByDesc(MedUsage::getId);
        return wrapper;
    }
}
