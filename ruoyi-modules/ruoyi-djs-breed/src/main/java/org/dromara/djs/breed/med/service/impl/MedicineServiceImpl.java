package org.dromara.djs.breed.med.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.med.domain.Medicine;
import org.dromara.djs.breed.med.domain.bo.MedicineBo;
import org.dromara.djs.breed.med.domain.query.MedicineQuery;
import org.dromara.djs.breed.med.domain.vo.MedicineVo;
import org.dromara.djs.breed.med.mapper.MedicineMapper;
import org.dromara.djs.breed.med.service.IMedicineService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.medicine.api.MedicineProductDto;
import org.dromara.djs.common.medicine.api.MedicineStockProvider;
import org.dromara.djs.common.validate.BizReferenceChecker;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
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
        // 药品归仓库：列表读仓库药品商品（buy_class='medicine'）+ 实时库存，取代已弃用的 t_breed_medicine_info。
        // 药品目录规模小（百级），一次性返回全部匹配行（total = 全量），mp「药品领用」列表直接渲染。
        return TableDataInfo.build(queryList(query));
    }

    @Override
    public List<MedicineVo> queryList(MedicineQuery query) {
        String keyword = query == null ? null : query.getMedicineName();
        return medicineStockProvider.listMedicineProducts(keyword).stream()
            .map(this::toVo)
            .collect(Collectors.toList());
    }

    /** {@link MedicineProductDto}（仓库药品商品）→ {@link MedicineVo}（养殖端「药品领用」契约）。 */
    private MedicineVo toVo(MedicineProductDto dto) {
        MedicineVo vo = new MedicineVo();
        vo.setId(dto.getId());
        vo.setMedicineName(dto.getName());
        vo.setUnit(dto.getUnit());
        vo.setSpec(dto.getSpec());
        vo.setImageUrl(dto.getImageUrl());
        vo.setCurrentStock(dto.getStock());
        vo.setMedStatus(1);
        return vo;
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

}
