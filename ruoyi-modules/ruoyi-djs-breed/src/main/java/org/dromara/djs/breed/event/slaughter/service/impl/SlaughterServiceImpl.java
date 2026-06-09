package org.dromara.djs.breed.event.slaughter.service.impl;

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
import org.dromara.djs.breed.core.event.PigMarketingEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.slaughter.domain.PigMarketing;
import org.dromara.djs.breed.event.slaughter.domain.bo.SlaughterBo;
import org.dromara.djs.breed.event.slaughter.domain.query.SlaughterQuery;
import org.dromara.djs.breed.event.slaughter.domain.vo.PigMarketingVo;
import org.dromara.djs.breed.event.slaughter.mapper.PigMarketingMapper;
import org.dromara.djs.breed.event.slaughter.service.ISlaughterService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 出栏事件 Service 实现（BRD-EVENT-004 SLAUGHTER）。
 *
 * <h3>事务边界</h3>
 * <p>{@link #recordSlaughter} 标 {@code @Transactional}：INSERT marketing + fireEvent(SLAUGHTER)
 * + publishEvent(PigMarketingEvent) 同生共死。下游 CROSS-FLOW-001 消费者
 * {@code PigMarketingEventListener} 按 {@code @TransactionalEventListener(AFTER_COMMIT)} 在事务提交后
 * 自动 INSERT 白条（{@code t_warehouse_bar_info}，status=pending_singe，回填 ear_no/marketing_time/marketing_weight），
 * 这是猪肉追溯链 marketing 事件时间戳的来源。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaughterServiceImpl implements ISlaughterService {

    private final PigMarketingMapper marketingMapper;
    private final PigMapper pigMapper;
    private final IPigCoreService pigCoreService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigMarketingVo recordSlaughter(SlaughterBo bo) {
        Objects.requireNonNull(bo, "SlaughterBo must not be null");

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

        if (StringUtils.isBlank(bo.getOssIds())) {
            throw new ServiceException(I18nMessages.t("slaughter.photo.required"));
        }

        PigMarketing entity = new PigMarketing();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setMarketingDate(bo.getMarketingDate());
        entity.setOutWeight(bo.getOutWeight());
        entity.setOutDest(bo.getOutDest());
        entity.setStoreId(bo.getStoreId());
        entity.setIsRoom(0);
        entity.setOssIds(bo.getOssIds());
        entity.setRemark(bo.getRemark());
        entity.setOperator(bo.getOperator());            // EmployeePicker 所选 userId（可空）
        entity.setOperatorId(LoginHelper.getUserId());   // 登录态审计（不动）
        entity.setDelFlag("0");
        marketingMapper.insert(entity);

        // 触发状态机 (非 END) → END，end_reason=MARKET
        PigEventBo eventBo = new PigEventBo();
        eventBo.setPigId(pig.getId());
        eventBo.setEventType(PigStatusEvent.SLAUGHTER);
        eventBo.setRelatedEventId(entity.getId());
        eventBo.setEventAt(bo.getMarketingDate());
        pigCoreService.fireEvent(eventBo);

        // publish 跨域事件给燎毛接收（CROSS-FLOW-001 监听）；V1 消费者未实现
        eventPublisher.publishEvent(new PigMarketingEvent(this, entity));

        log.info("[BRD-EVENT-004] recordSlaughter pigId={} earNo={} marketingId={} weight={} dest={}",
            pig.getId(), pig.getEarNo(), entity.getId(), bo.getOutWeight(), bo.getOutDest());

        return toVo(entity);
    }

    @Override
    public TableDataInfo<PigMarketingVo> queryPage(SlaughterQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigMarketing> w = Wrappers.<PigMarketing>lambdaQuery()
            .eq(query.getPigId() != null, PigMarketing::getPigId, query.getPigId())
            .eq(StringUtils.isNotBlank(query.getEarNo()), PigMarketing::getEarNo, query.getEarNo())
            .eq(StringUtils.isNotBlank(query.getOutDest()), PigMarketing::getOutDest, query.getOutDest())
            .ge(query.getBeginDate() != null, PigMarketing::getMarketingDate, query.getBeginDate())
            .le(query.getEndDate() != null, PigMarketing::getMarketingDate, query.getEndDate())
            .orderByDesc(PigMarketing::getMarketingDate, PigMarketing::getId);
        Page<PigMarketingVo> page = marketingMapper.selectVoPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    private PigMarketingVo toVo(PigMarketing e) {
        PigMarketingVo v = new PigMarketingVo();
        v.setId(e.getId());
        v.setPigId(e.getPigId());
        v.setEarNo(e.getEarNo());
        v.setMarketingDate(e.getMarketingDate());
        v.setOutWeight(e.getOutWeight());
        v.setOutDest(e.getOutDest());
        v.setStoreId(e.getStoreId());
        v.setIsRoom(e.getIsRoom());
        v.setOssIds(e.getOssIds());
        v.setOperatorId(e.getOperatorId());
        v.setOperator(e.getOperator());
        v.setRemark(e.getRemark());
        return v;
    }
}
