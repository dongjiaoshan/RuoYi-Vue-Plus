package org.dromara.djs.breed.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.breeding.domain.BreedConfig;
import org.dromara.djs.breed.breeding.domain.BreedInfo;
import org.dromara.djs.breed.breeding.domain.bo.BreedConfigBo;
import org.dromara.djs.breed.breeding.domain.query.BreedConfigQuery;
import org.dromara.djs.breed.breeding.domain.vo.BreedConfigVo;
import org.dromara.djs.breed.breeding.mapper.BreedConfigMapper;
import org.dromara.djs.breed.breeding.mapper.BreedInfoMapper;
import org.dromara.djs.breed.breeding.service.IBreedConfigService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 育种配置 Service 实现（BRD-MD-001 / CR-20260519-06）。
 *
 * <p>配种关系建模决策（doc/02-changes.md CR-20260519-06）：
 * {@code motherCode / fatherCode / cubCode} 字符串字段引用 {@code t_farm_breed_info.breed_strain_code}，
 * <strong>不是 FK ID</strong>。优势：历史记录改名不污染。</p>
 *
 * <p>插入 / 编辑前置校验：</p>
 * <ul>
 *   <li>三个 code 必须在 {@code t_farm_breed_info} 内存在（同 {@code tenant_id} + 同 {@code breed_strain}）</li>
 *   <li>仔代品种<strong>必须先</strong>在 {@code t_farm_breed_info} 建好</li>
 * </ul>
 *
 * @author djs
 * @since BRD-MD-001
 */
@Slf4j
@Service
public class BreedConfigServiceImpl extends DjsBaseServiceImpl<BreedConfigMapper, BreedConfig> implements IBreedConfigService {

    private final BreedInfoMapper breedInfoMapper;

    public BreedConfigServiceImpl(BreedConfigMapper baseMapper, BreedInfoMapper breedInfoMapper) {
        super(baseMapper);
        this.breedInfoMapper = breedInfoMapper;
    }

    @Override
    public TableDataInfo<BreedConfigVo> queryPageList(BreedConfigQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<BreedConfig> wrapper = buildQueryWrapper(query);
        Page<BreedConfigVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BreedConfigVo> queryList(BreedConfigQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public BreedConfigVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public int insertByBo(BreedConfigBo bo) {
        validateReferencedCodes(bo);
        BreedConfig entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("育种配置入参转换失败");
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(BreedConfigBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("育种配置 ID 不能为空");
        }
        BreedConfig exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("育种配置不存在或已删除：" + bo.getId());
        }
        validateReferencedCodes(bo);
        BreedConfig entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("育种配置入参转换失败");
        }
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        // 当前 v1 配种关系无下游事件流水引用（事件表存 ear_no 直接指猪只），可直接软删。
        // 后续若 BRD-EVENT-002 配种事件引用本表 id，需在此校验。
        return softDelete(ids);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus。
     */
    protected BreedConfig toEntity(BreedConfigBo bo) {
        return MapstructUtils.convert(bo, BreedConfig.class);
    }

    /**
     * 校验 motherCode / fatherCode / cubCode 在 {@code t_farm_breed_info} 中是否存在
     * （同 {@code breed_strain} 范畴内）。仔代必须事先建好。
     */
    private void validateReferencedCodes(BreedConfigBo bo) {
        ensureCodeExists(bo.getBreedStrain(), bo.getMotherCode(), "母本");
        ensureCodeExists(bo.getBreedStrain(), bo.getFatherCode(), "父本");
        ensureCodeExists(bo.getBreedStrain(), bo.getCubCode(), "仔代");
    }

    private void ensureCodeExists(Integer breedStrain, String code, String role) {
        Long count = breedInfoMapper.selectCount(new LambdaQueryWrapper<BreedInfo>()
            .eq(BreedInfo::getBreedStrain, breedStrain)
            .eq(BreedInfo::getBreedStrainCode, code));
        if (count == null || count == 0L) {
            throw new ServiceException(role + "编码 [" + code + "] 在育种信息中不存在，请先到品种/品系页登记");
        }
    }

    /**
     * 构造查询条件：breedStrain eq / *_code eq。
     */
    private LambdaQueryWrapper<BreedConfig> buildQueryWrapper(BreedConfigQuery query) {
        LambdaQueryWrapper<BreedConfig> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(BreedConfig::getId);
        }
        wrapper.eq(Objects.nonNull(query.getBreedStrain()), BreedConfig::getBreedStrain, query.getBreedStrain())
            .eq(StringUtils.isNotBlank(query.getMotherCode()), BreedConfig::getMotherCode, query.getMotherCode())
            .eq(StringUtils.isNotBlank(query.getFatherCode()), BreedConfig::getFatherCode, query.getFatherCode())
            .eq(StringUtils.isNotBlank(query.getCubCode()), BreedConfig::getCubCode, query.getCubCode())
            .ge(Objects.nonNull(query.getCreateTimeBegin()), BreedConfig::getCreateTime, query.getCreateTimeBegin())
            .le(Objects.nonNull(query.getCreateTimeEnd()), BreedConfig::getCreateTime, query.getCreateTimeEnd())
            .orderByDesc(BreedConfig::getId);
        return wrapper;
    }

}
