package org.dromara.djs.breed.event.castrate.service.impl;

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
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.castrate.domain.CastrateRecord;
import org.dromara.djs.breed.event.castrate.domain.bo.CastrateBo;
import org.dromara.djs.breed.event.castrate.domain.query.CastrateQuery;
import org.dromara.djs.breed.event.castrate.domain.vo.CastrateRecordVo;
import org.dromara.djs.breed.event.castrate.mapper.CastrateRecordMapper;
import org.dromara.djs.breed.event.castrate.service.ICastrateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 阉割事件 Service 实现（BRD-EVENT-004 CASTRATE）。
 *
 * <h3>性别校验</h3>
 * <p>service 层提前校验 pig_sex='M'：状态机内部也会兜底校验，但 service 层早出错可避免脏 INSERT。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CastrateServiceImpl implements ICastrateService {

    private final CastrateRecordMapper castrateMapper;
    private final PigMapper pigMapper;
    private final IPigCoreService pigCoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CastrateRecordVo recordCastrate(CastrateBo bo) {
        Objects.requireNonNull(bo, "CastrateBo must not be null");

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

        if (!"M".equals(pig.getPigSex())) {
            throw new ServiceException(I18nMessages.t("castrate.male_only", pig.getId()));
        }

        CastrateRecord entity = new CastrateRecord();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setCastrateDate(bo.getCastrateDate());
        entity.setCastrater(StringUtils.isNotBlank(bo.getCastrater()) ? bo.getCastrater().trim() : null);
        entity.setRemark(bo.getRemark());
        entity.setOperatorId(LoginHelper.getUserId());
        entity.setDelFlag("0");
        castrateMapper.insert(entity);

        PigEventBo eventBo = new PigEventBo();
        eventBo.setPigId(pig.getId());
        eventBo.setEventType(PigStatusEvent.CASTRATE);
        eventBo.setRelatedEventId(entity.getId());
        eventBo.setEventAt(bo.getCastrateDate());
        pigCoreService.fireEvent(eventBo);

        log.info("[BRD-EVENT-004] recordCastrate pigId={} earNo={} castrateId={}",
            pig.getId(), pig.getEarNo(), entity.getId());

        return toVo(entity);
    }

    @Override
    public TableDataInfo<CastrateRecordVo> queryPage(CastrateQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<CastrateRecord> w = Wrappers.<CastrateRecord>lambdaQuery()
            .eq(query.getPigId() != null, CastrateRecord::getPigId, query.getPigId())
            .eq(StringUtils.isNotBlank(query.getEarNo()), CastrateRecord::getEarNo, query.getEarNo())
            .ge(query.getBeginDate() != null, CastrateRecord::getCastrateDate, query.getBeginDate())
            .le(query.getEndDate() != null, CastrateRecord::getCastrateDate, query.getEndDate())
            .orderByDesc(CastrateRecord::getCastrateDate, CastrateRecord::getId);
        Page<CastrateRecordVo> page = castrateMapper.selectVoPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    private CastrateRecordVo toVo(CastrateRecord e) {
        CastrateRecordVo v = new CastrateRecordVo();
        v.setId(e.getId());
        v.setPigId(e.getPigId());
        v.setEarNo(e.getEarNo());
        v.setCastrateDate(e.getCastrateDate());
        v.setOperatorId(e.getOperatorId());
        v.setCastrater(e.getCastrater());
        v.setRemark(e.getRemark());
        return v;
    }
}
