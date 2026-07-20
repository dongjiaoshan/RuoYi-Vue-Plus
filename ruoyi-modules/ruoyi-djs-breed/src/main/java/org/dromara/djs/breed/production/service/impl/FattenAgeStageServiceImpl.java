package org.dromara.djs.breed.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.production.domain.FattenAgeStage;
import org.dromara.djs.breed.production.domain.bo.FattenAgeStageBo;
import org.dromara.djs.breed.production.domain.vo.FattenAgeStageVo;
import org.dromara.djs.breed.production.mapper.FattenAgeStageMapper;
import org.dromara.djs.breed.production.service.IFattenAgeStageService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 育肥日龄阶段配置 Service 实现（A1 Tab2）。
 *
 * <p>{@link #batchSave} 整表覆盖：先软删全部旧行，再逐行 INSERT 入参。包在一个事务里
 * 保证「半删半灌」不会落库。{@link #queryList} 按 {@code start_age} 升序，给前端连续日龄段展示。</p>
 *
 * <p>软删走 {@link DjsBaseServiceImpl#softDelete}。</p>
 *
 * @author djs
 * @since A1
 */
@Slf4j
@Service
public class FattenAgeStageServiceImpl
    extends DjsBaseServiceImpl<FattenAgeStageMapper, FattenAgeStage>
    implements IFattenAgeStageService {

    public FattenAgeStageServiceImpl(FattenAgeStageMapper baseMapper) {
        super(baseMapper);
    }

    @Override
    public List<FattenAgeStageVo> queryList() {
        return baseMapper.selectVoList(new LambdaQueryWrapper<FattenAgeStage>()
            .orderByAsc(FattenAgeStage::getStartAge)
            .orderByAsc(FattenAgeStage::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSave(List<FattenAgeStageBo> stages) {
        // 1. 软删全部旧行（整表覆盖语义）
        List<Long> oldIds = baseMapper.selectList(new LambdaQueryWrapper<FattenAgeStage>())
            .stream().map(FattenAgeStage::getId).collect(Collectors.toList());
        if (!oldIds.isEmpty()) {
            softDelete(oldIds);
        }

        // 2. 重灌新行（空入参 = 仅清空）
        if (stages == null || stages.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (FattenAgeStageBo bo : stages) {
            validate(bo);
            FattenAgeStage entity = toEntity(bo);
            count += baseMapper.insert(entity);
        }
        return count;
    }

    @Override
    public int removeById(Long id) {
        if (id == null) {
            return 0;
        }
        return softDelete(Collections.singletonList(id));
    }

    /**
     * 入参校验：起始 / 截止日龄非空且 end ≥ start。
     */
    private void validate(FattenAgeStageBo bo) {
        if (bo.getStartAge() == null || bo.getEndAge() == null) {
            throw new ServiceException("起始日龄 / 截止日龄不能为空");
        }
        if (bo.getEndAge() < bo.getStartAge()) {
            throw new ServiceException("截止日龄不能小于起始日龄（起始 " + bo.getStartAge() + " / 截止 " + bo.getEndAge() + "）");
        }
    }

    /**
     * BO → Entity；不走 MapStruct 以便单测无 Spring 上下文也能跑（与 PigCoreServiceImpl 手工 copy 一致）。
     * 新增行不显式赋 tenant_id（走 InjectionMetaObjectHandler）；recordGrowth 缺省 0。
     */
    protected FattenAgeStage toEntity(FattenAgeStageBo bo) {
        FattenAgeStage e = new FattenAgeStage();
        e.setStartAge(bo.getStartAge());
        e.setEndAge(bo.getEndAge());
        e.setRecordGrowth(Optional.ofNullable(bo.getRecordGrowth()).orElse(0));
        e.setRemark(bo.getRemark());
        e.setDelFlag("0");
        e.setDelUnique(0L);
        return e;
    }
}
