package org.dromara.djs.breed.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.production.domain.MedScheduleConfig;
import org.dromara.djs.breed.production.domain.bo.MedScheduleConfigBo;
import org.dromara.djs.breed.production.domain.query.MedScheduleConfigQuery;
import org.dromara.djs.breed.production.domain.vo.MedScheduleConfigVo;
import org.dromara.djs.breed.production.mapper.MedScheduleConfigMapper;
import org.dromara.djs.breed.production.service.IMedScheduleConfigService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 药品 / 疫苗周期配置 Service 实现（BRD-MD-003 Tab3）。
 *
 * <p>下游 BRD-MED-002 自动产生"待打疫苗 / 待用药"任务时按 {@code eventTrigger}
 * 查询本表。配置只决定"建议时机"，不会自动 trigger（v1.2 无定时任务）。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Slf4j
@Service
public class MedScheduleConfigServiceImpl
    extends DjsBaseServiceImpl<MedScheduleConfigMapper, MedScheduleConfig>
    implements IMedScheduleConfigService {

    public MedScheduleConfigServiceImpl(MedScheduleConfigMapper baseMapper) {
        super(baseMapper);
    }

    @Override
    public TableDataInfo<MedScheduleConfigVo> queryPageList(MedScheduleConfigQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<MedScheduleConfig> wrapper = buildQueryWrapper(query);
        Page<MedScheduleConfigVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
    }

    @Override
    public List<MedScheduleConfigVo> queryList(MedScheduleConfigQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public MedScheduleConfigVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public int insertByBo(MedScheduleConfigBo bo) {
        MedScheduleConfig entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("药品周期配置入参转换失败");
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(MedScheduleConfigBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("药品周期配置 ID 不能为空");
        }
        MedScheduleConfig exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("药品周期配置不存在或已删除：" + bo.getId());
        }
        MedScheduleConfig entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("药品周期配置入参转换失败");
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
    protected MedScheduleConfig toEntity(MedScheduleConfigBo bo) {
        return MapstructUtils.convert(bo, MedScheduleConfig.class);
    }

    /**
     * 构造查询条件：medType eq + eventTrigger eq。
     */
    private LambdaQueryWrapper<MedScheduleConfig> buildQueryWrapper(MedScheduleConfigQuery query) {
        LambdaQueryWrapper<MedScheduleConfig> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(MedScheduleConfig::getId);
        }
        wrapper.eq(StringUtils.isNotBlank(query.getMedType()), MedScheduleConfig::getMedType, query.getMedType())
            .eq(StringUtils.isNotBlank(query.getEventTrigger()), MedScheduleConfig::getEventTrigger, query.getEventTrigger())
            .orderByDesc(MedScheduleConfig::getId);
        return wrapper;
    }

}
