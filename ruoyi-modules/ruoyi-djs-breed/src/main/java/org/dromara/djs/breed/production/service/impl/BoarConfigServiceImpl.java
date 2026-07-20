package org.dromara.djs.breed.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.production.domain.BoarConfig;
import org.dromara.djs.breed.production.domain.bo.BoarConfigBo;
import org.dromara.djs.breed.production.domain.query.BoarConfigQuery;
import org.dromara.djs.breed.production.domain.vo.BoarConfigVo;
import org.dromara.djs.breed.production.mapper.BoarConfigMapper;
import org.dromara.djs.breed.production.service.IBoarConfigService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 精液 / 公猪配置 Service 实现（BRD-MD-003 Tab2）。
 *
 * <p>V1 单条"通用配置"（{@code boar_id=NULL}），后续 V2 可针对具体公猪覆盖。
 * 软删走基类 {@link DjsBaseServiceImpl#softDelete}（D03 教训）。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Slf4j
@Service
public class BoarConfigServiceImpl
    extends DjsBaseServiceImpl<BoarConfigMapper, BoarConfig>
    implements IBoarConfigService {

    public BoarConfigServiceImpl(BoarConfigMapper baseMapper) {
        super(baseMapper);
    }

    @Override
    public TableDataInfo<BoarConfigVo> queryPageList(BoarConfigQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<BoarConfig> wrapper = buildQueryWrapper(query);
        Page<BoarConfigVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BoarConfigVo> queryList(BoarConfigQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public BoarConfigVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public int insertByBo(BoarConfigBo bo) {
        BoarConfig entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("公猪配置入参转换失败");
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(BoarConfigBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("公猪配置 ID 不能为空");
        }
        BoarConfig exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("公猪配置不存在或已删除：" + bo.getId());
        }
        BoarConfig entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("公猪配置入参转换失败");
        }
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        return softDelete(ids);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus。
     */
    protected BoarConfig toEntity(BoarConfigBo bo) {
        return MapstructUtils.convert(bo, BoarConfig.class);
    }

    /**
     * 构造查询条件：boarId eq（null 时不加条件）。
     */
    private LambdaQueryWrapper<BoarConfig> buildQueryWrapper(BoarConfigQuery query) {
        LambdaQueryWrapper<BoarConfig> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(BoarConfig::getId);
        }
        wrapper.eq(Objects.nonNull(query.getBoarId()), BoarConfig::getBoarId, query.getBoarId())
            .orderByDesc(BoarConfig::getId);
        return wrapper;
    }

}
