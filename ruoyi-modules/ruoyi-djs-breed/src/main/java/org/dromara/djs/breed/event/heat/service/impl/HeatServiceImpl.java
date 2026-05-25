package org.dromara.djs.breed.event.heat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.dromara.djs.breed.event.heat.domain.PigHeat;
import org.dromara.djs.breed.event.heat.domain.bo.HeatBo;
import org.dromara.djs.breed.event.heat.domain.query.HeatQuery;
import org.dromara.djs.breed.event.heat.domain.vo.PigHeatVo;
import org.dromara.djs.breed.event.heat.mapper.PigHeatMapper;
import org.dromara.djs.breed.event.heat.service.IHeatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 查情事件 Service 实现（BRD-EVENT-002 OESTRUS）。
 *
 * <h3>分流</h3>
 * <p>关键决策：{@code isPregnantConfirmed=false} 时**不调 fireEvent**，只写记录（按 doc/06 BRD-EVENT-002 + CR-20260524-07）。
 * 这是状态机不变的设计——保持纯函数 + 副作用集中在 service 层判断。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeatServiceImpl implements IHeatService {

    private final PigHeatMapper heatMapper;
    private final PigMapper pigMapper;
    private final IPigCoreService pigCoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigHeatVo recordHeat(HeatBo bo) {
        Objects.requireNonNull(bo, "HeatBo must not be null");

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

        boolean confirmed = Boolean.TRUE.equals(bo.getIsPregnantConfirmed());

        // 1. 无论是否 confirmed 都写一行 heat 记录
        PigHeat entity = new PigHeat();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setHeatDate(bo.getHeatDate());
        entity.setHeatResult(bo.getHeatResult());
        entity.setIsPregnantConfirmed(confirmed ? 1 : 0);
        entity.setRemark(bo.getRemark());
        entity.setDelFlag("0");
        heatMapper.insert(entity);

        // 2. confirmed=true → 触发状态机 PZ → PH；false → 不调（仅写记录）
        if (confirmed) {
            Map<String, Object> payload = new HashMap<>(2);
            payload.put("isPregnantConfirmed", Boolean.TRUE);

            PigEventBo eventBo = new PigEventBo();
            eventBo.setPigId(pig.getId());
            eventBo.setEventType(PigStatusEvent.OESTRUS);
            eventBo.setRelatedEventId(entity.getId());
            eventBo.setEventAt(bo.getHeatDate());
            eventBo.setPayload(payload);
            pigCoreService.fireEvent(eventBo);
        }

        log.info("[BRD-EVENT-002] recordHeat pigId={} earNo={} heatId={} confirmed={}",
            pig.getId(), pig.getEarNo(), entity.getId(), confirmed);

        return toVo(entity);
    }

    @Override
    public TableDataInfo<PigHeatVo> queryPage(HeatQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigHeat> w = Wrappers.<PigHeat>lambdaQuery()
            .eq(query.getPigId() != null, PigHeat::getPigId, query.getPigId())
            .eq(StringUtils.isNotBlank(query.getEarNo()), PigHeat::getEarNo, query.getEarNo())
            .eq(query.getIsPregnantConfirmed() != null,
                PigHeat::getIsPregnantConfirmed, query.getIsPregnantConfirmed())
            .ge(query.getBeginDate() != null, PigHeat::getHeatDate, query.getBeginDate())
            .le(query.getEndDate() != null, PigHeat::getHeatDate, query.getEndDate())
            .orderByDesc(PigHeat::getHeatDate, PigHeat::getId);
        Page<PigHeatVo> page = heatMapper.selectVoPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    private PigHeatVo toVo(PigHeat e) {
        PigHeatVo v = new PigHeatVo();
        v.setId(e.getId());
        v.setPigId(e.getPigId());
        v.setEarNo(e.getEarNo());
        v.setHeatDate(e.getHeatDate());
        v.setHeatResult(e.getHeatResult());
        v.setIsPregnantConfirmed(e.getIsPregnantConfirmed());
        v.setOperatorId(e.getOperatorId());
        v.setRemark(e.getRemark());
        return v;
    }
}
