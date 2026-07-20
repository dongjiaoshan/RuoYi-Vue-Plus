package org.dromara.djs.plant.organic.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import org.dromara.djs.plant.organic.domain.OrganicCropno;
import org.dromara.djs.plant.organic.domain.bo.CropOrganicBo;
import org.dromara.djs.plant.organic.domain.bo.CropOrganicRelateBo;
import org.dromara.djs.plant.organic.domain.query.CropOrganicQuery;
import org.dromara.djs.plant.organic.domain.vo.CropOrganicVo;
import org.dromara.djs.plant.organic.domain.vo.CropRefVo;
import org.dromara.djs.plant.organic.mapper.CropOrganicMapper;
import org.dromara.djs.plant.organic.mapper.OrganicCropnoMapper;
import org.dromara.djs.plant.organic.service.ICropOrganicService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 果蔬有机证书 Service 实现（PLT-MD-003 / FIX-PLT-AD-INFO-LIST-001）。
 *
 * <p>一证多作物多对多：证书-作物关联表 {@code t_plant_crop_organic_rel}，
 * add / update / relate 走"先软删旧关联（del_unique=id），再批量插新关联"（同事务），
 * 与土地证书 {@link PlotOrganicServiceImpl} 同范式。</p>
 *
 * @author djs
 * @since PLT-MD-003
 */
@Slf4j
@Service
public class CropOrganicServiceImpl extends DjsBaseServiceImpl<CropOrganicMapper, CropOrganic>
    implements ICropOrganicService {

    private final CropInfoMapper cropInfoMapper;
    private final OrganicCropnoMapper cropnoMapper;

    public CropOrganicServiceImpl(CropOrganicMapper baseMapper,
                                  CropInfoMapper cropInfoMapper,
                                  OrganicCropnoMapper cropnoMapper) {
        super(baseMapper);
        this.cropInfoMapper = cropInfoMapper;
        this.cropnoMapper = cropnoMapper;
    }

    @Override
    public TableDataInfo<CropOrganicVo> queryPageList(CropOrganicQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<CropOrganic> wrapper = buildQueryWrapper(query);
        Page<CropOrganicVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichRelatedCrops(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<CropOrganicVo> queryList(CropOrganicQuery query) {
        List<CropOrganicVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        enrichRelatedCrops(list);
        return list;
    }

    @Override
    public CropOrganicVo queryById(Long id) {
        CropOrganicVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            enrichRelatedCrops(Collections.singletonList(vo));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(CropOrganicBo bo) {
        List<Long> cropIds = resolveCropIds(bo);
        validateCropIds(cropIds);
        CropOrganic entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("果蔬有机证书入参转换失败");
        }
        if (entity.getIsWarning() == null) {
            entity.setIsWarning(2);
        }
        int rows = baseMapper.insert(entity);
        if (rows > 0 && entity.getId() != null) {
            setRelatedCrops(entity.getId(), cropIds);
        }
        return rows;
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
        CropOrganic entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("果蔬有机证书入参转换失败");
        }
        // crop_cert_no 不允许修改
        entity.setCropCertNo(exists.getCropCertNo());
        int rows = baseMapper.updateById(entity);
        // 编辑证书基本字段/图片时绝不 touch 关联作物 junction —— 关联只由「关联作物」入口（relateCrops）维护。
        // 仅当编辑请求显式带非空 cropIds 时才同步关联（admin 编辑表单不带 cropIds → 恒跳过，
        // 不再把已配好的关联作物清空，row197 客户实证反复被清 73 条软删残骸）。
        if (CollUtil.isNotEmpty(bo.getCropIds())) {
            List<Long> cropIds = bo.getCropIds();
            validateCropIds(cropIds);
            setRelatedCrops(bo.getId(), cropIds);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int relateCrops(CropOrganicRelateBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("果蔬有机证书 ID 不能为空");
        }
        CropOrganic exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("果蔬有机证书不存在或已删除：" + bo.getId());
        }
        List<Long> cropIds = bo.getCropIds();
        validateCropIds(cropIds);
        setRelatedCrops(bo.getId(), cropIds);
        // 关联成功（含清空全部）固定返 1，避免 cropIds 为空时 toAjax(0) 误判失败
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        // 同事务：先软删证书 + 该证书所有关联
        int rows = softDelete(ids);
        if (rows > 0) {
            softDeleteCropnosByOrganicIds(ids);
        }
        return rows;
    }

    /**
     * 优先取 {@code cropIds}（一证多作物）；为空时兼容旧单值 {@code cropId}。
     */
    private List<Long> resolveCropIds(CropOrganicBo bo) {
        if (CollUtil.isNotEmpty(bo.getCropIds())) {
            return bo.getCropIds();
        }
        if (bo.getCropId() != null) {
            return Collections.singletonList(bo.getCropId());
        }
        return Collections.emptyList();
    }

    /**
     * 关联管理核心：先软删旧关联，再批量插入新关联（同事务）。
     */
    private void setRelatedCrops(Long organicId, List<Long> cropIds) {
        // 1. 软删该证书已有关联
        softDeleteCropnosByOrganicIds(Collections.singletonList(organicId));

        // 2. 插入新关联
        if (CollUtil.isEmpty(cropIds)) {
            return;
        }
        Set<Long> distinctCropIds = new HashSet<>(cropIds);
        List<OrganicCropno> rows = new ArrayList<>(distinctCropIds.size());
        for (Long cropId : distinctCropIds) {
            OrganicCropno r = new OrganicCropno();
            r.setOrganicId(organicId);
            r.setCropId(cropId);
            rows.add(r);
        }
        cropnoMapper.insertBatch(rows);
    }

    /**
     * 软删指定证书 id 列表对应的所有 cropno 关联（先删后插的"先删"；删证书时的"级联软删"）。
     *
     * <p>显式 wrapper update 写入 del_flag/del_unique/update_by/update_time，避开 MP 默认软删 SQL
     * 不写 del_unique 的坑（参 DjsBaseServiceImpl Javadoc）。</p>
     */
    private void softDeleteCropnosByOrganicIds(Collection<Long> organicIds) {
        if (CollUtil.isEmpty(organicIds)) {
            return;
        }
        Long updateBy = currentUserIdSafe();
        Date now = new Date();
        for (Long organicId : organicIds) {
            UpdateWrapper<OrganicCropno> wrapper = Wrappers.<OrganicCropno>update()
                .eq("organic_id", organicId)
                .eq("del_flag", "0")
                .setSql("del_unique = id")
                .set("del_flag", "1")
                .set("update_by", updateBy)
                .set("update_time", now);
            cropnoMapper.update(null, wrapper);
        }
    }

    private void validateCropIds(List<Long> cropIds) {
        if (CollUtil.isEmpty(cropIds)) {
            return;
        }
        Set<Long> distinct = new HashSet<>(cropIds);
        List<CropInfo> found = cropInfoMapper.selectByIds(distinct);
        if (found == null || found.size() != distinct.size()) {
            throw new ServiceException("关联作物包含不存在或已删除的 cropId");
        }
    }

    /**
     * 批量 enrich 证书的关联作物列表（relatedCrops）+ 兼容旧单值 cropName。
     */
    private void enrichRelatedCrops(List<CropOrganicVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        Set<Long> organicIds = list.stream()
            .map(CropOrganicVo::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (organicIds.isEmpty()) {
            return;
        }
        List<OrganicCropno> rels = cropnoMapper.selectList(
            new LambdaQueryWrapper<OrganicCropno>().in(OrganicCropno::getOrganicId, organicIds));

        // 收集所有需要查名的 cropId：关联表 cropId + 旧单值 cropId
        Set<Long> cropIds = rels.stream().map(OrganicCropno::getCropId).collect(Collectors.toSet());
        list.stream().map(CropOrganicVo::getCropId).filter(Objects::nonNull).forEach(cropIds::add);

        Map<Long, String> cropNameMap = new HashMap<>();
        if (CollUtil.isNotEmpty(cropIds)) {
            List<CropInfo> crops = cropInfoMapper.selectByIds(cropIds);
            for (CropInfo c : crops) {
                cropNameMap.put(c.getId(), c.getCropName());
            }
        }

        Map<Long, List<CropRefVo>> byOrganic = new HashMap<>();
        for (OrganicCropno r : rels) {
            byOrganic.computeIfAbsent(r.getOrganicId(), k -> new ArrayList<>())
                .add(new CropRefVo(r.getCropId(), cropNameMap.get(r.getCropId())));
        }
        for (CropOrganicVo vo : list) {
            vo.setRelatedCrops(byOrganic.getOrDefault(vo.getId(), Collections.emptyList()));
            // 旧单值 cropName 兼容回填
            if (vo.getCropId() != null) {
                vo.setCropName(cropNameMap.get(vo.getCropId()));
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
