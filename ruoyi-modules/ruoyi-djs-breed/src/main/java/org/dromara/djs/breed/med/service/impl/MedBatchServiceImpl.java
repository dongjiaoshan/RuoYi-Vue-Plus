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
import org.dromara.djs.breed.med.domain.MedBatch;
import org.dromara.djs.breed.med.domain.MedUsage;
import org.dromara.djs.breed.med.domain.Medicine;
import org.dromara.djs.breed.med.domain.bo.MedBatchBo;
import org.dromara.djs.breed.med.domain.query.MedBatchQuery;
import org.dromara.djs.breed.med.domain.vo.MedBatchVo;
import org.dromara.djs.breed.med.mapper.MedBatchMapper;
import org.dromara.djs.breed.med.mapper.MedUsageMapper;
import org.dromara.djs.breed.med.mapper.MedicineMapper;
import org.dromara.djs.breed.med.service.IMedBatchService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.medicine.api.MedicineStockProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 药品批次 Service 实现（BRD-MED-001）。
 *
 * <p>软删走基类 {@link DjsBaseServiceImpl#softDelete}（D03 教训）。</p>
 *
 * @author djs
 * @since BRD-MED-001
 */
@Slf4j
@Service
public class MedBatchServiceImpl extends DjsBaseServiceImpl<MedBatchMapper, MedBatch> implements IMedBatchService {

    /**
     * mp 用药领用「近 N 天已出库批次」过滤窗口（含今天），与 BRD-MED-003
     * {@link org.dromara.djs.breed.med.record.mapper.MedRecordMapper#selectUsableBatchesByPig}
     * 的「3 天内已领可用批次」口径一致。
     */
    private static final long RECENT_USED_DAYS = 3L;

    private final MedicineMapper medicineMapper;
    private final MedicineStockProvider medicineStockProvider;

    /**
     * 领用台账 mapper（mp「近 3 天已出库批次」过滤用）。字段注入而非构造注入：
     * 现有单测 {@code TestableMedBatchServiceImpl} 走 3 参构造器，新增构造参数会破坏其编译；
     * 该依赖仅 {@code recentUsedOnly} 路径使用，字段注入兼容单测且不影响既有 happy path。
     */
    @org.springframework.beans.factory.annotation.Autowired
    private MedUsageMapper medUsageMapper;

    public MedBatchServiceImpl(MedBatchMapper baseMapper, MedicineMapper medicineMapper,
                               MedicineStockProvider medicineStockProvider) {
        super(baseMapper);
        this.medicineMapper = medicineMapper;
        this.medicineStockProvider = medicineStockProvider;
    }

    @Override
    public TableDataInfo<MedBatchVo> queryPageList(MedBatchQuery query, PageQuery pageQuery) {
        return queryPageList(query, pageQuery, false);
    }

    /**
     * 分页查询批次列表，支持「近 3 天已出库批次」过滤（mp 用药领用专用）。
     *
     * <p>{@code recentUsedOnly=true} 时只返「近 3 天内有过领用出库（{@code usage_type='use'}）」的批次：
     * 药品库存真值落仓库唯一药品库位（{@code location_type='medicine'}，V1 单库位 L0012），但
     * {@code t_warehouse_stock_flow} 不记药品/批次维流水（药品出入库经
     * {@link MedicineStockProvider} 直接增减 {@code t_warehouse_location_stock}），故批次的「出库」
     * 事实落在领用台账 {@code t_breed_medicine_usage}。本过滤以该台账为准，等价于「3 天内从药品库领过的批次」，
     * 与 {@link org.dromara.djs.breed.med.record.mapper.MedRecordMapper#selectUsableBatchesByPig} 同源同窗口。</p>
     *
     * <p>实现：先查近 3 天 {@code use} 台账去重出 {@code batchId} 集合，再用 {@code id IN (...)} 收敛到主查询
     * 的 wrapper，从而保持分页 total 正确（非取页后再 filter）。集合为空 → 直接返空页（无批次符合）。</p>
     *
     * @param query          原查询条件
     * @param pageQuery      分页参数
     * @param recentUsedOnly true=只返近 3 天已出库批次；false=全部（admin 列表口径）
     * @return 批次分页结果
     */
    public TableDataInfo<MedBatchVo> queryPageList(MedBatchQuery query, PageQuery pageQuery, boolean recentUsedOnly) {
        LambdaQueryWrapper<MedBatch> wrapper = buildQueryWrapper(query);
        if (recentUsedOnly) {
            Set<Long> recentBatchIds = selectRecentUsedBatchIds();
            if (recentBatchIds.isEmpty()) {
                long pageNum = pageQuery.getPageNum() == null ? PageQuery.DEFAULT_PAGE_NUM : pageQuery.getPageNum();
                long pageSize = pageQuery.getPageSize() == null ? PageQuery.DEFAULT_PAGE_SIZE : pageQuery.getPageSize();
                return TableDataInfo.build(new Page<>(pageNum, pageSize, 0));
            }
            wrapper.in(MedBatch::getId, recentBatchIds);
        }
        Page<MedBatchVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichMedicineName(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 查近 3 天内（含今天）有 {@code use} 领用出库的批次 ID 集合（去重）。
     *
     * <p>口径同 {@code MedRecordMapper.selectUsableBatchesByPig}：{@code usage_type='use'} 且
     * {@code use_date >= CURDATE() - 3 天}；{@code del_flag='0'} 由 MP {@code @TableLogic} 自动追加，
     * {@code tenant_id} 由多租户拦截器注入。仅取 {@code batch_id} 列减小回传。</p>
     *
     * @return 近 3 天已出库批次 ID 集合（可能为空）
     */
    private Set<Long> selectRecentUsedBatchIds() {
        LocalDate since = LocalDate.now().minusDays(RECENT_USED_DAYS);
        LambdaQueryWrapper<MedUsage> w = new LambdaQueryWrapper<MedUsage>()
            .select(MedUsage::getBatchId)
            .eq(MedUsage::getUsageType, "use")
            .ge(MedUsage::getUseDate, since)
            .isNotNull(MedUsage::getBatchId);
        return medUsageMapper.selectList(w).stream()
            .map(MedUsage::getBatchId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    @Override
    public List<MedBatchVo> queryList(MedBatchQuery query) {
        List<MedBatchVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        enrichMedicineName(list);
        return list;
    }

    @Override
    public MedBatchVo queryById(Long id) {
        MedBatchVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            enrichMedicineName(List.of(vo));
        }
        return vo;
    }

    /**
     * 列表 enrich：药品名称替代裸 medicineId。收集去重 medicineId 一次性批查，避免 N+1。
     */
    private void enrichMedicineName(List<MedBatchVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> medicineIds = rows.stream().map(MedBatchVo::getMedicineId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (medicineIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = medicineMapper.selectByIds(medicineIds).stream()
            .filter(m -> m.getId() != null && m.getMedicineName() != null)
            .collect(Collectors.toMap(Medicine::getId, Medicine::getMedicineName, (a, b) -> a));
        for (MedBatchVo vo : rows) {
            vo.setMedicineName(names.get(vo.getMedicineId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(MedBatchBo bo) {
        MedBatch entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("药品批次入参转换失败");
        }
        int rows = baseMapper.insert(entity);
        // ADR-0012：采购入库同步把入库量增到仓库库存（batch 行 quantity 仅作入库快照）
        if (entity.getMedicineId() != null && entity.getQuantity() != null
            && entity.getQuantity().signum() > 0) {
            medicineStockProvider.add(entity.getMedicineId(), entity.getQuantity(), LoginHelper.getUserId());
        }
        return rows;
    }

    @Override
    public int updateByBo(MedBatchBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("批次 ID 不能为空");
        }
        MedBatch exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("批次不存在或已删除：" + bo.getId());
        }
        MedBatch entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("药品批次入参转换失败");
        }
        // medicine_id 不允许通过编辑端点修改（批次归属固定，要换药品请新建批次）
        entity.setMedicineId(exists.getMedicineId());
        // ADR-0012：编辑修正 quantity 暂不联动仓库库存（入库修正是边角场景，本次不处理；
        // 要纠正仓库库存走仓库盘点。batch.quantity 仅为入库快照，不再被领用/治疗扣减）
        return baseMapper.updateById(entity);
    }

    /**
     * BO → Entity 转换钩子。
     */
    protected MedBatch toEntity(MedBatchBo bo) {
        return MapstructUtils.convert(bo, MedBatch.class);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        // V2 BRD-MED-002 领用扣减批次时，按 FIFO 锁批次；本表 softDelete 仅用于"录错纠正"。
        return softDelete(ids);
    }

    /**
     * 构造查询条件：medicineId eq / batchNo like。
     */
    private LambdaQueryWrapper<MedBatch> buildQueryWrapper(MedBatchQuery query) {
        LambdaQueryWrapper<MedBatch> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(MedBatch::getId);
        }
        wrapper.eq(Objects.nonNull(query.getMedicineId()), MedBatch::getMedicineId, query.getMedicineId())
            .like(StringUtils.isNotBlank(query.getBatchNo()), MedBatch::getBatchNo, query.getBatchNo())
            .orderByDesc(MedBatch::getId);
        return wrapper;
    }

}
