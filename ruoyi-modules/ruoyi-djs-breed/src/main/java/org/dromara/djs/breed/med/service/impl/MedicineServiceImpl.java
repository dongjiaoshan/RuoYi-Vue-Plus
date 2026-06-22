package org.dromara.djs.breed.med.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.med.domain.Medicine;
import org.dromara.djs.breed.med.domain.bo.MedicineBo;
import org.dromara.djs.breed.med.domain.query.MedicineQuery;
import org.dromara.djs.breed.med.domain.vo.MedicineVo;
import org.dromara.djs.breed.med.mapper.MedicineMapper;
import org.dromara.djs.breed.med.service.IMedicineService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.medicine.api.MedicineStockProvider;
import org.dromara.djs.common.validate.BizReferenceChecker;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 药品库 Service 实现（BRD-MED-001）。
 *
 * <p>软删走基类 {@link DjsBaseServiceImpl#softDelete}（{@code update(entity, wrapper)} +
 * {@code setSql("del_flag = '1'")} 绕过 {@code @TableLogic} 剥离，D03 _open-issues #18 教训）。</p>
 *
 * <p>{@link #deleteWithValidByIds(Collection)} 软删前经 {@link BizReferenceChecker} 反查
 * "是否被 {@code t_breed_medicine_usage.medicine_id} / {@code t_breed_medicine_record.medicine_id}
 * / {@code t_breed_medicine_batch.medicine_id} 引用"，命中则抛 {@code ServiceException} 阻止软删。
 * 引用关系在 {@link #registerReferences()} 启动期注册（D02 _open-issues #12 声明式注册决策）。</p>
 *
 * @author djs
 * @since BRD-MED-001
 */
@Slf4j
@Service
public class MedicineServiceImpl extends DjsBaseServiceImpl<MedicineMapper, Medicine> implements IMedicineService {

    private static final String SRC_TABLE = "t_breed_medicine_info";

    private final MedicineStockProvider medicineStockProvider;
    private final BizReferenceChecker bizReferenceChecker;

    public MedicineServiceImpl(MedicineMapper baseMapper, MedicineStockProvider medicineStockProvider,
                               BizReferenceChecker bizReferenceChecker) {
        super(baseMapper);
        this.medicineStockProvider = medicineStockProvider;
        this.bizReferenceChecker = bizReferenceChecker;
    }

    @PostConstruct
    public void registerReferences() {
        bizReferenceChecker.register(SRC_TABLE, "t_breed_medicine_usage", "medicine_id");
        bizReferenceChecker.register(SRC_TABLE, "t_breed_medicine_record", "medicine_id");
        bizReferenceChecker.register(SRC_TABLE, "t_breed_medicine_batch", "medicine_id");
    }

    @Override
    public TableDataInfo<MedicineVo> queryPageList(MedicineQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Medicine> wrapper = buildQueryWrapper(query);
        Page<MedicineVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillStockFromWarehouse(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 用仓库 location_stock 聚合回填 {@code currentStock}（ADR-0012：库存真值在仓库，
     * VO.currentStock 语义从"主数据 current_stock"改为"仓库药品库位库存合计"）。
     * 无库存行的药品缺省按 {@link BigDecimal#ZERO}。空列表跳过。
     */
    private void fillStockFromWarehouse(List<MedicineVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> medicineIds = rows.stream().map(MedicineVo::getId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (medicineIds.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> stocks = medicineStockProvider.getStocks(medicineIds);
        for (MedicineVo vo : rows) {
            vo.setCurrentStock(stocks.getOrDefault(vo.getId(), BigDecimal.ZERO));
        }
    }

    @Override
    public List<MedicineVo> queryList(MedicineQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public MedicineVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public int insertByBo(MedicineBo bo) {
        Medicine entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("药品入参转换失败");
        }
        if (entity.getMedStatus() == null) {
            entity.setMedStatus(1);
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(MedicineBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("药品 ID 不能为空");
        }
        Medicine exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("药品不存在或已删除：" + bo.getId());
        }
        Medicine entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("药品入参转换失败");
        }
        // medicine_code 不允许通过编辑端点修改（DB UNIQUE 兜底，应用层显式锁住语义）
        entity.setMedicineCode(exists.getMedicineCode());
        return baseMapper.updateById(entity);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus。
     *
     * <p>抽成 protected 方便 Mockito 单测覆盖（避免启 Spring 上下文）。</p>
     */
    protected Medicine toEntity(MedicineBo bo) {
        return MapstructUtils.convert(bo, Medicine.class);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 软删前反查领用 / 治疗用药 / 批次引用，命中则阻止删除（避免悬空引用）
        for (Long id : ids) {
            if (bizReferenceChecker.isReferenced(SRC_TABLE, id)) {
                throw new ServiceException("药品被领用 / 用药记录 / 批次引用，无法删除：id=" + id);
            }
        }
        return softDelete(ids);
    }

    /**
     * 构造查询条件：medicineName like / medicineCode eq / medicineType eq / supplierId eq / medStatus eq。
     */
    private LambdaQueryWrapper<Medicine> buildQueryWrapper(MedicineQuery query) {
        LambdaQueryWrapper<Medicine> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(Medicine::getId);
        }
        wrapper.like(StringUtils.isNotBlank(query.getMedicineName()), Medicine::getMedicineName, query.getMedicineName())
            .eq(StringUtils.isNotBlank(query.getMedicineCode()), Medicine::getMedicineCode, query.getMedicineCode())
            .eq(StringUtils.isNotBlank(query.getMedicineType()), Medicine::getMedicineType, query.getMedicineType())
            .eq(Objects.nonNull(query.getSupplierId()), Medicine::getSupplierId, query.getSupplierId())
            .eq(Objects.nonNull(query.getMedStatus()), Medicine::getMedStatus, query.getMedStatus())
            .orderByDesc(Medicine::getId);
        return wrapper;
    }

}
