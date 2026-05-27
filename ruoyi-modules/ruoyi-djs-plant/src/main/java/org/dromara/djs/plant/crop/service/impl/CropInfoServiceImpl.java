package org.dromara.djs.plant.crop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.domain.bo.CropInfoBo;
import org.dromara.djs.plant.crop.domain.query.CropInfoQuery;
import org.dromara.djs.plant.crop.domain.vo.CropInfoVo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.crop.service.ICropInfoService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 作物 Service 实现（PLT-MD-001）。
 *
 * <p>删除前校验：D8 阶段 stub。t_plant_plant_plan / t_plant_pick_activity 在 D9+ 落地后启用。</p>
 *
 * @author djs
 * @since PLT-MD-001
 */
@Slf4j
@Service
public class CropInfoServiceImpl extends DjsBaseServiceImpl<CropInfoMapper, CropInfo> implements ICropInfoService {

    public CropInfoServiceImpl(CropInfoMapper baseMapper) {
        super(baseMapper);
    }

    @Override
    public TableDataInfo<CropInfoVo> queryPageList(CropInfoQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<CropInfo> wrapper = buildQueryWrapper(query);
        Page<CropInfoVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
    }

    @Override
    public List<CropInfoVo> queryList(CropInfoQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public CropInfoVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public int insertByBo(CropInfoBo bo) {
        CropInfo entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("作物入参转换失败");
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(CropInfoBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("作物 ID 不能为空");
        }
        CropInfo exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("作物不存在或已删除：" + bo.getId());
        }
        CropInfo entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("作物入参转换失败");
        }
        // crop_code 不允许修改
        entity.setCropCode(exists.getCropCode());
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // D8 阶段 stub：t_plant_plant_plan / t_plant_pick_activity / t_plant_plant_details 0 行，
        // D9+ PLT-PLAN-001 / PLT-PICK-001 落地后启用：
        //   long active = planMapper.selectCount(... crop_id IN(ids) AND del_flag=0) + ...
        //   if (active > 0) throw new ServiceException("plant.crop.has_business_data");
        return softDelete(ids);
    }

    protected CropInfo toEntity(CropInfoBo bo) {
        return MapstructUtils.convert(bo, CropInfo.class);
    }

    private LambdaQueryWrapper<CropInfo> buildQueryWrapper(CropInfoQuery query) {
        LambdaQueryWrapper<CropInfo> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(CropInfo::getId);
        }
        wrapper.eq(StringUtils.isNotBlank(query.getCropCode()), CropInfo::getCropCode, query.getCropCode())
            .like(StringUtils.isNotBlank(query.getCropName()), CropInfo::getCropName, query.getCropName())
            .like(StringUtils.isNotBlank(query.getVarietyName()), CropInfo::getVarietyName, query.getVarietyName())
            .eq(StringUtils.isNotBlank(query.getCropFamily()), CropInfo::getCropFamily, query.getCropFamily())
            .like(StringUtils.isNotBlank(query.getPlantingSeason()), CropInfo::getPlantingSeason, query.getPlantingSeason())
            .orderByDesc(CropInfo::getId);
        return wrapper;
    }
}
