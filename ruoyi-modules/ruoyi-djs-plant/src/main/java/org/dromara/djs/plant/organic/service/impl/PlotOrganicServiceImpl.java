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
import org.dromara.djs.plant.organic.domain.OrganicPlotno;
import org.dromara.djs.plant.organic.domain.PlotOrganic;
import org.dromara.djs.plant.organic.domain.bo.PlotOrganicBo;
import org.dromara.djs.plant.organic.domain.query.PlotOrganicQuery;
import org.dromara.djs.plant.organic.domain.vo.PlotOrganicVo;
import org.dromara.djs.plant.organic.domain.vo.PlotRefVo;
import org.dromara.djs.plant.organic.mapper.OrganicPlotnoMapper;
import org.dromara.djs.plant.organic.mapper.PlotOrganicMapper;
import org.dromara.djs.plant.organic.service.IPlotOrganicService;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
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
 * 土地有机证书 Service 实现（PLT-MD-003）。
 *
 * <p>关键事务流（add / update）：</p>
 * <ol>
 *   <li>INSERT/UPDATE {@code t_plant_plot_organic} 证书主行</li>
 *   <li>软删该证书原有的 {@code t_plant_organic_plotno} 关联（{@code del_flag='1'} + {@code del_unique=id}）</li>
 *   <li>批量 INSERT 新关联（来自 BO.plotIds）</li>
 * </ol>
 *
 * <p>软删旧关联是为了不与 UNIQUE(tenant_id, organic_id, plot_id, del_unique) 冲突；
 * 当再次关联同一对 (organicId, plotId)，老行因 del_unique=id 不再占用 (organic_id, plot_id, 0) 槽位。</p>
 *
 * @author djs
 * @since PLT-MD-003
 */
@Slf4j
@Service
public class PlotOrganicServiceImpl extends DjsBaseServiceImpl<PlotOrganicMapper, PlotOrganic>
    implements IPlotOrganicService {

    private final OrganicPlotnoMapper plotnoMapper;
    private final PlotInfoMapper plotInfoMapper;

    public PlotOrganicServiceImpl(PlotOrganicMapper baseMapper,
                                  OrganicPlotnoMapper plotnoMapper,
                                  PlotInfoMapper plotInfoMapper) {
        super(baseMapper);
        this.plotnoMapper = plotnoMapper;
        this.plotInfoMapper = plotInfoMapper;
    }

    @Override
    public TableDataInfo<PlotOrganicVo> queryPageList(PlotOrganicQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PlotOrganic> wrapper = buildQueryWrapper(query);
        Page<PlotOrganicVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichRelatedPlots(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PlotOrganicVo> queryList(PlotOrganicQuery query) {
        List<PlotOrganicVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        enrichRelatedPlots(list);
        return list;
    }

    @Override
    public PlotOrganicVo queryById(Long id) {
        PlotOrganicVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            enrichRelatedPlots(Collections.singletonList(vo));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PlotOrganicBo bo) {
        PlotOrganic entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("土地有机证书入参转换失败");
        }
        // 默认 isWarning=2（正常）
        if (entity.getIsWarning() == null) {
            entity.setIsWarning(2);
        }
        // 关联地块校验
        validatePlotIds(bo.getPlotIds());

        int rows = baseMapper.insert(entity);
        if (rows > 0 && entity.getId() != null) {
            setRelatedPlots(entity.getId(), bo.getPlotIds());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PlotOrganicBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("土地有机证书 ID 不能为空");
        }
        PlotOrganic exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("土地有机证书不存在或已删除：" + bo.getId());
        }
        validatePlotIds(bo.getPlotIds());

        PlotOrganic entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("土地有机证书入参转换失败");
        }
        // organic_no 不允许修改（即使前端传了，强制锁回）
        entity.setOrganicNo(exists.getOrganicNo());
        int rows = baseMapper.updateById(entity);
        setRelatedPlots(bo.getId(), bo.getPlotIds());
        return rows;
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        // 同事务：先软删证书 + 该证书所有关联
        int rows = softDelete(ids);
        if (rows > 0) {
            softDeletePlotnosByOrganicIds(ids);
        }
        return rows;
    }

    /**
     * 关联管理核心：先软删旧关联，再批量插入新关联（同事务）。
     *
     * <p>软删 UpdateWrapper 用 {@code setSql("del_unique = id")} 把 token 同步成行自身 id，
     * 让 UNIQUE 约束不阻塞下次重新关联同对。</p>
     */
    private void setRelatedPlots(Long organicId, List<Long> plotIds) {
        // 1. 软删该证书已有关联
        softDeletePlotnosByOrganicIds(Collections.singletonList(organicId));

        // 2. 插入新关联
        if (CollUtil.isEmpty(plotIds)) {
            return;
        }
        Set<Long> distinctPlotIds = new HashSet<>(plotIds);
        List<OrganicPlotno> rows = new ArrayList<>(distinctPlotIds.size());
        for (Long plotId : distinctPlotIds) {
            OrganicPlotno r = new OrganicPlotno();
            r.setOrganicId(organicId);
            r.setPlotId(plotId);
            rows.add(r);
        }
        plotnoMapper.insertBatch(rows);
    }

    /**
     * 软删指定证书 id 列表对应的所有 plotno 关联（先删后插的"先删"步骤；删证书时的"级联软删"）。
     *
     * <p>显式 wrapper update 写入 del_flag/del_unique/update_by/update_time，避开 MP 默认软删 SQL
     * 不写 del_unique 的坑（参 DjsBaseServiceImpl Javadoc）。</p>
     */
    private void softDeletePlotnosByOrganicIds(Collection<Long> organicIds) {
        if (CollUtil.isEmpty(organicIds)) {
            return;
        }
        Long updateBy = currentUserIdSafe();
        Date now = new Date();
        for (Long organicId : organicIds) {
            UpdateWrapper<OrganicPlotno> wrapper = Wrappers.<OrganicPlotno>update()
                .eq("organic_id", organicId)
                .eq("del_flag", "0")
                .setSql("del_unique = id")
                .set("del_flag", "1")
                .set("update_by", updateBy)
                .set("update_time", now);
            plotnoMapper.update(null, wrapper);
        }
    }

    private void validatePlotIds(List<Long> plotIds) {
        if (CollUtil.isEmpty(plotIds)) {
            return;
        }
        Set<Long> distinct = new HashSet<>(plotIds);
        List<PlotInfo> found = plotInfoMapper.selectByIds(distinct);
        if (found == null || found.size() != distinct.size()) {
            throw new ServiceException("关联地块包含不存在或已删除的 plotId");
        }
    }

    private void enrichRelatedPlots(List<PlotOrganicVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        Set<Long> organicIds = list.stream()
            .map(PlotOrganicVo::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (organicIds.isEmpty()) {
            return;
        }
        List<OrganicPlotno> rels = plotnoMapper.selectList(
            new LambdaQueryWrapper<OrganicPlotno>().in(OrganicPlotno::getOrganicId, organicIds));
        if (CollUtil.isEmpty(rels)) {
            list.forEach(vo -> vo.setRelatedPlots(Collections.emptyList()));
            return;
        }
        Set<Long> plotIds = rels.stream().map(OrganicPlotno::getPlotId).collect(Collectors.toSet());
        List<PlotInfo> plots = plotInfoMapper.selectByIds(plotIds);
        Map<Long, String> plotNameMap = new HashMap<>();
        for (PlotInfo p : plots) {
            plotNameMap.put(p.getId(), p.getPlotName());
        }
        Map<Long, List<PlotRefVo>> byOrganic = new HashMap<>();
        for (OrganicPlotno r : rels) {
            byOrganic.computeIfAbsent(r.getOrganicId(), k -> new ArrayList<>())
                .add(new PlotRefVo(r.getPlotId(), plotNameMap.get(r.getPlotId())));
        }
        for (PlotOrganicVo vo : list) {
            vo.setRelatedPlots(byOrganic.getOrDefault(vo.getId(), Collections.emptyList()));
        }
    }

    protected PlotOrganic toEntity(PlotOrganicBo bo) {
        return MapstructUtils.convert(bo, PlotOrganic.class);
    }

    private LambdaQueryWrapper<PlotOrganic> buildQueryWrapper(PlotOrganicQuery query) {
        LambdaQueryWrapper<PlotOrganic> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PlotOrganic::getId);
        }
        wrapper.like(StringUtils.isNotBlank(query.getOrganicNo()), PlotOrganic::getOrganicNo, query.getOrganicNo())
            .like(StringUtils.isNotBlank(query.getOrganicCompany()), PlotOrganic::getOrganicCompany, query.getOrganicCompany())
            .eq(query.getIsWarning() != null, PlotOrganic::getIsWarning, query.getIsWarning())
            .orderByDesc(PlotOrganic::getId);
        return wrapper;
    }
}
