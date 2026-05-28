package org.dromara.djs.plant.organic.service.impl;

import cn.hutool.core.collection.CollUtil;
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
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.organic.domain.CropOrganic;
import org.dromara.djs.plant.organic.domain.bo.CropOrganicBo;
import org.dromara.djs.plant.organic.domain.query.CropOrganicQuery;
import org.dromara.djs.plant.organic.domain.vo.CropOrganicVo;
import org.dromara.djs.plant.organic.mapper.CropOrganicMapper;
import org.dromara.djs.plant.organic.service.ICropOrganicService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 果蔬有机证书 Service 实现（PLT-MD-003）。
 *
 * <p>简单 CRUD：一证书对一作物（{@code crop_id} FK），无多对多关联表。</p>
 *
 * @author djs
 * @since PLT-MD-003
 */
@Slf4j
@Service
public class CropOrganicServiceImpl extends DjsBaseServiceImpl<CropOrganicMapper, CropOrganic>
    implements ICropOrganicService {

    private final CropInfoMapper cropInfoMapper;

    public CropOrganicServiceImpl(CropOrganicMapper baseMapper, CropInfoMapper cropInfoMapper) {
        super(baseMapper);
        this.cropInfoMapper = cropInfoMapper;
    }

    @Override
    public TableDataInfo<CropOrganicVo> queryPageList(CropOrganicQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<CropOrganic> wrapper = buildQueryWrapper(query);
        Page<CropOrganicVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichCropNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<CropOrganicVo> queryList(CropOrganicQuery query) {
        List<CropOrganicVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        enrichCropNames(list);
        return list;
    }

    @Override
    public CropOrganicVo queryById(Long id) {
        CropOrganicVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getCropId() != null) {
            CropInfo crop = cropInfoMapper.selectById(vo.getCropId());
            if (crop != null) {
                vo.setCropName(crop.getCropName());
            }
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(CropOrganicBo bo) {
        validateCropId(bo.getCropId());
        CropOrganic entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("果蔬有机证书入参转换失败");
        }
        if (entity.getIsWarning() == null) {
            entity.setIsWarning(2);
        }
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(CropOrganicBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("果蔬有机证书 ID 不能为空");
        }
        CropOrganic exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("果蔬有机证书不存在或已删除：" + bo.getId());
        }
        validateCropId(bo.getCropId());

        CropOrganic entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("果蔬有机证书入参转换失败");
        }
        // crop_cert_no 不允许修改
        entity.setCropCertNo(exists.getCropCertNo());
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        return softDelete(ids);
    }

    private void validateCropId(Long cropId) {
        if (cropId == null) {
            return;
        }
        CropInfo crop = cropInfoMapper.selectById(cropId);
        if (crop == null) {
            throw new ServiceException("关联作物不存在或已删除：" + cropId);
        }
    }

    private void enrichCropNames(List<CropOrganicVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        Set<Long> cropIds = list.stream()
            .map(CropOrganicVo::getCropId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (cropIds.isEmpty()) {
            return;
        }
        List<CropInfo> crops = cropInfoMapper.selectByIds(cropIds);
        Map<Long, String> nameMap = new HashMap<>();
        for (CropInfo c : crops) {
            nameMap.put(c.getId(), c.getCropName());
        }
        for (CropOrganicVo vo : list) {
            if (vo.getCropId() != null) {
                vo.setCropName(nameMap.get(vo.getCropId()));
            }
        }
    }

    protected CropOrganic toEntity(CropOrganicBo bo) {
        return MapstructUtils.convert(bo, CropOrganic.class);
    }

    private LambdaQueryWrapper<CropOrganic> buildQueryWrapper(CropOrganicQuery query) {
        LambdaQueryWrapper<CropOrganic> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(CropOrganic::getId);
        }
        wrapper.like(StringUtils.isNotBlank(query.getCropCertNo()), CropOrganic::getCropCertNo, query.getCropCertNo())
            .like(StringUtils.isNotBlank(query.getCropCertCompany()), CropOrganic::getCropCertCompany, query.getCropCertCompany())
            .eq(query.getCropId() != null, CropOrganic::getCropId, query.getCropId())
            .eq(query.getIsWarning() != null, CropOrganic::getIsWarning, query.getIsWarning())
            .orderByDesc(CropOrganic::getId);
        return wrapper;
    }
}
