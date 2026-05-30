package org.dromara.djs.breed.med.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.med.domain.MedBatch;
import org.dromara.djs.breed.med.domain.Medicine;
import org.dromara.djs.breed.med.domain.bo.MedBatchBo;
import org.dromara.djs.breed.med.domain.query.MedBatchQuery;
import org.dromara.djs.breed.med.domain.vo.MedBatchVo;
import org.dromara.djs.breed.med.mapper.MedBatchMapper;
import org.dromara.djs.breed.med.mapper.MedicineMapper;
import org.dromara.djs.breed.med.service.IMedBatchService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.springframework.stereotype.Service;

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

    private final MedicineMapper medicineMapper;

    public MedBatchServiceImpl(MedBatchMapper baseMapper, MedicineMapper medicineMapper) {
        super(baseMapper);
        this.medicineMapper = medicineMapper;
    }

    @Override
    public TableDataInfo<MedBatchVo> queryPageList(MedBatchQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<MedBatch> wrapper = buildQueryWrapper(query);
        Page<MedBatchVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichMedicineName(page.getRecords());
        return TableDataInfo.build(page);
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
    public int insertByBo(MedBatchBo bo) {
        MedBatch entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("药品批次入参转换失败");
        }
        return baseMapper.insert(entity);
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
