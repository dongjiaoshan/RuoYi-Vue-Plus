package org.dromara.djs.breed.event.growth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.satoken.utils.LoginHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.event.growth.domain.PigGrowth;
import org.dromara.djs.breed.event.growth.domain.bo.GrowthBo;
import org.dromara.djs.breed.event.growth.domain.query.GrowthQuery;
import org.dromara.djs.breed.event.growth.domain.vo.PigGrowthVo;
import org.dromara.djs.breed.event.growth.mapper.PigGrowthMapper;
import org.dromara.djs.breed.event.growth.service.IPigGrowthService;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;

/**
 * 生长记录 Service 实现（BRD-EVENT-005 GROWTH）。
 *
 * <h3>事务边界</h3>
 * <p>{@link #addGrowthRecord} 标 {@code @Transactional}：仅 INSERT t_farm_pig_growth，
 * 不调用状态机（生长记录只是测量数据）。</p>
 *
 * <h3>校验</h3>
 * <ul>
 *   <li>pig 必须存在（否则 ServiceException）</li>
 *   <li>weight 必填 > 0（@Valid 已校验）</li>
 *   <li>删除：3 天内（含当天）可删，超过抛 growth.delete.expired</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-005
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PigGrowthServiceImpl implements IPigGrowthService {

    /** 删除生长记录的时效（创建后 N 天内可删）。 */
    private static final long DELETE_WINDOW_DAYS = 3L;

    private final PigGrowthMapper growthMapper;
    private final PigMapper pigMapper;
    private final BarnMapper barnMapper;
    private final PenMapper penMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigGrowthVo addGrowthRecord(GrowthBo bo) {
        Objects.requireNonNull(bo, "GrowthBo must not be null");

        // 二选一支持：mp 端传 earNo；admin 端传 pigId（与 D5 BRD-EVENT-001 supplierCode 模式一致）
        if (bo.getPigId() == null) {
            if (bo.getEarNo() == null || bo.getEarNo().isBlank()) {
                throw new ServiceException(I18nMessages.t("pig.id_or_ear_required"), 400);
            }
            Long resolved = pigMapper.selectIdByEarNo(bo.getEarNo());
            if (resolved == null) {
                throw new ServiceException(I18nMessages.t("pig.not_found_by_ear", bo.getEarNo()), 400);
            }
            bo.setPigId(resolved);
        }
        Pig pig = pigMapper.selectById(bo.getPigId());
        if (pig == null) {
            throw new ServiceException(I18nMessages.t("pig.not_found", bo.getPigId()));
        }

        PigGrowth entity = new PigGrowth();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setMeasureDate(bo.getMeasureDate());
        entity.setWeight(bo.getWeight());
        entity.setBackfatThickness(bo.getBackfatThickness());
        entity.setBackHeight(bo.getBackHeight());
        entity.setPhotoOssIds(bo.getPhotoOssIds());
        entity.setBarnName(resolveBarnName(pig.getBarnId()));
        entity.setPenName(resolvePenName(pig.getPenId()));
        entity.setRemark(bo.getRemark());
        entity.setOperatorId(LoginHelper.getUserId());
        entity.setDelFlag("0");
        growthMapper.insert(entity);

        log.info("[BRD-EVENT-005] addGrowthRecord pigId={} earNo={} measureDate={} weight={} growthId={}",
            pig.getId(), pig.getEarNo(), bo.getMeasureDate(), bo.getWeight(), entity.getId());

        return toVo(entity);
    }

    @Override
    public TableDataInfo<PigGrowthVo> queryPage(GrowthQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigGrowth> w = Wrappers.<PigGrowth>lambdaQuery()
            .eq(query.getPigId() != null, PigGrowth::getPigId, query.getPigId())
            .eq(StringUtils.isNotBlank(query.getEarNo()), PigGrowth::getEarNo, query.getEarNo())
            .ge(query.getBeginDate() != null, PigGrowth::getMeasureDate, query.getBeginDate())
            .le(query.getEndDate() != null, PigGrowth::getMeasureDate, query.getEndDate())
            .orderByDesc(PigGrowth::getMeasureDate, PigGrowth::getId);
        Page<PigGrowthVo> page = growthMapper.selectVoPage(pageQuery.build(), w);
        enrichAgeDays(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 列表 enrich 测量时日龄（mp timeline「42 日龄」用）。
     *
     * <p>日龄 = measureDate - pig.birthDate（缺时 fallback introduceDate）；批查去重 pigId 避免 N+1。
     * 基准日期均空 → ageDays 留 null（mp 端该格不渲染）。</p>
     */
    private void enrichAgeDays(java.util.List<PigGrowthVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        java.util.List<Long> pigIds = rows.stream()
            .map(PigGrowthVo::getPigId).filter(Objects::nonNull).distinct().toList();
        if (pigIds.isEmpty()) {
            return;
        }
        java.util.Map<Long, Pig> pigById = new java.util.HashMap<>();
        for (Pig p : pigMapper.selectByIds(pigIds)) {
            pigById.put(p.getId(), p);
        }
        for (PigGrowthVo vo : rows) {
            Pig p = vo.getPigId() == null ? null : pigById.get(vo.getPigId());
            if (p == null || vo.getMeasureDate() == null) {
                continue;
            }
            LocalDate base = p.getBirthDate() != null ? p.getBirthDate() : p.getIntroduceDate();
            if (base != null) {
                vo.setAgeDays((int) ChronoUnit.DAYS.between(base, vo.getMeasureDate()));
            }
        }
    }

    @Override
    public PigGrowthVo getById(Long id) {
        if (id == null) {
            throw new ServiceException(I18nMessages.t("growth.id.required"));
        }
        PigGrowth entity = growthMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException(I18nMessages.t("growth.not_found", id));
        }
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 3 天内可删：逐条校验 createTime，超期抛 ServiceException
        LocalDate today = LocalDate.now();
        for (Long id : ids) {
            PigGrowth entity = growthMapper.selectById(id);
            if (entity == null) {
                continue;
            }
            Date createTime = entity.getCreateTime();
            if (createTime != null) {
                LocalDate created = createTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                long daysSince = ChronoUnit.DAYS.between(created, today);
                if (daysSince > DELETE_WINDOW_DAYS) {
                    throw new ServiceException(I18nMessages.t("growth.delete.expired", id, DELETE_WINDOW_DAYS));
                }
            }
        }
        return growthMapper.deleteByIds(ids);
    }

    private String resolveBarnName(Long barnId) {
        if (barnId == null) {
            return null;
        }
        Barn b = barnMapper.selectById(barnId);
        return b == null ? null : b.getBarnName();
    }

    private String resolvePenName(Long penId) {
        if (penId == null) {
            return null;
        }
        Pen p = penMapper.selectById(penId);
        return p == null ? null : p.getPenName();
    }

    private PigGrowthVo toVo(PigGrowth e) {
        PigGrowthVo v = new PigGrowthVo();
        v.setId(e.getId());
        v.setPigId(e.getPigId());
        v.setEarNo(e.getEarNo());
        v.setMeasureDate(e.getMeasureDate());
        v.setWeight(e.getWeight());
        v.setBackfatThickness(e.getBackfatThickness());
        v.setBackHeight(e.getBackHeight());
        v.setPhotoOssIds(e.getPhotoOssIds());
        v.setOperatorId(e.getOperatorId());
        v.setBarnName(e.getBarnName());
        v.setPenName(e.getPenName());
        v.setRemark(e.getRemark());
        Date created = e.getCreateTime();
        if (created != null) {
            v.setCreateTime(created.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        return v;
    }
}
