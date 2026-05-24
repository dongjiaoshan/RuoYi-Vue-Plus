package org.dromara.djs.breed.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.breeding.domain.BreedInfo;
import org.dromara.djs.breed.breeding.domain.bo.BreedInfoBo;
import org.dromara.djs.breed.breeding.domain.query.BreedInfoQuery;
import org.dromara.djs.breed.breeding.domain.vo.BreedInfoVo;
import org.dromara.djs.breed.breeding.mapper.BreedInfoMapper;
import org.dromara.djs.breed.breeding.service.IBreedInfoService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 育种信息 Service 实现（BRD-MD-001 / CR-20260519-06）。
 *
 * <p>软删走基类 {@link DjsBaseServiceImpl#softDelete}（{@code update(entity, wrapper)} +
 * {@code setSql("del_flag = '1'")} 绕过 {@code @TableLogic} 剥离，D03 _open-issues #18 教训）。</p>
 *
 * <p>下游业务表 wire 后需在 {@link #deleteWithValidByIds(Collection)} 前置校验
 * "是否被 {@code t_farm_pig_info} / {@code t_farm_breed_config} 引用"。</p>
 *
 * @author djs
 * @since BRD-MD-001
 */
@Slf4j
@Service
public class BreedInfoServiceImpl extends DjsBaseServiceImpl<BreedInfoMapper, BreedInfo> implements IBreedInfoService {

    public BreedInfoServiceImpl(BreedInfoMapper baseMapper) {
        super(baseMapper);
    }

    @Override
    public TableDataInfo<BreedInfoVo> queryPageList(BreedInfoQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<BreedInfo> wrapper = buildQueryWrapper(query);
        Page<BreedInfoVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BreedInfoVo> queryList(BreedInfoQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public BreedInfoVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public int insertByBo(BreedInfoBo bo) {
        BreedInfo entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("育种信息入参转换失败");
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(BreedInfoBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("育种信息 ID 不能为空");
        }
        BreedInfo exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("育种信息不存在或已删除：" + bo.getId());
        }
        BreedInfo entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("育种信息入参转换失败");
        }
        return baseMapper.updateById(entity);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus。
     *
     * <p>抽成 protected 方便 Mockito 单测覆盖（避免启 Spring 上下文）。</p>
     */
    protected BreedInfo toEntity(BreedInfoBo bo) {
        return MapstructUtils.convert(bo, BreedInfo.class);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        // TODO BRD-MD-001 → D05 BRD-CORE-001：业务表 wire 后，删除前需校验
        // (1) 是否被 t_farm_pig_info.{pig_breed_code, pig_strain_code} 引用 → 有则抛 BizException
        // (2) 是否被 t_farm_breed_config.{mother_code, father_code, cub_code} 引用 → 有则抛 BizException
        // D05 BRD-EVENT-001 抽 BizReferenceChecker 后，本处统一改为声明式注册。
        return softDelete(ids);
    }

    /**
     * 构造查询条件：breedStrain eq / code eq / name like / parentCode eq。
     */
    private LambdaQueryWrapper<BreedInfo> buildQueryWrapper(BreedInfoQuery query) {
        LambdaQueryWrapper<BreedInfo> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(BreedInfo::getId);
        }
        wrapper.eq(Objects.nonNull(query.getBreedStrain()), BreedInfo::getBreedStrain, query.getBreedStrain())
            .eq(StringUtils.isNotBlank(query.getBreedStrainCode()), BreedInfo::getBreedStrainCode, query.getBreedStrainCode())
            .like(StringUtils.isNotBlank(query.getBreedStrainName()), BreedInfo::getBreedStrainName, query.getBreedStrainName())
            .eq(StringUtils.isNotBlank(query.getParentCode()), BreedInfo::getParentCode, query.getParentCode())
            .ge(Objects.nonNull(query.getCreateTimeBegin()), BreedInfo::getCreateTime, query.getCreateTimeBegin())
            .le(Objects.nonNull(query.getCreateTimeEnd()), BreedInfo::getCreateTime, query.getCreateTimeEnd())
            .orderByDesc(BreedInfo::getId);
        return wrapper;
    }

}
