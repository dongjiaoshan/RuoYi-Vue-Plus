package org.dromara.djs.breed.event.heat.service.impl;

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
import org.dromara.djs.breed.event.heat.domain.PigHeat;
import org.dromara.djs.breed.event.heat.domain.bo.HeatBo;
import org.dromara.djs.breed.event.heat.domain.query.HeatQuery;
import org.dromara.djs.breed.event.heat.domain.vo.PigHeatVo;
import org.dromara.djs.breed.event.heat.mapper.PigHeatMapper;
import org.dromara.djs.breed.event.heat.service.IHeatService;
import org.dromara.djs.breed.event.weaning.domain.PigWeaning;
import org.dromara.djs.breed.event.weaning.mapper.PigWeaningMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final PigWeaningMapper weaningMapper;
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
        // 录入人：mp EmployeePicker 传 operator(userId string) 优先；空/非法回落登录态（602-5，仿 SLAUGHTER 范式）
        Long operatorId = LoginHelper.getUserId();
        if (StringUtils.isNotBlank(bo.getOperator())) {
            try {
                operatorId = Long.parseLong(bo.getOperator().trim());
            } catch (NumberFormatException ex) {
                log.warn("[BRD-EVENT-002] recordHeat 非法 operator={} 回落登录态", bo.getOperator());
            }
        }
        entity.setOperatorId(operatorId);
        entity.setDelFlag("0");
        heatMapper.insert(entity);

        // 2. confirmed=true → 触发 OESTRUS（状态保持 PZ，仅落审计，见 ADR-0010）；false → 不调（仅写记录）
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
        LocalDateTime beginAt = query.getBeginDate() != null ? query.getBeginDate().atStartOfDay() : null;
        LocalDateTime endBefore = query.getEndDate() != null ? query.getEndDate().plusDays(1).atStartOfDay() : null;
        LambdaQueryWrapper<PigHeat> w = Wrappers.<PigHeat>lambdaQuery()
            .eq(query.getPigId() != null, PigHeat::getPigId, query.getPigId())
            .like(StringUtils.isNotBlank(query.getEarNo()), PigHeat::getEarNo, query.getEarNo())
            .eq(query.getIsPregnantConfirmed() != null,
                PigHeat::getIsPregnantConfirmed, query.getIsPregnantConfirmed())
            .ge(beginAt != null, PigHeat::getHeatDate, beginAt)
            .lt(endBefore != null, PigHeat::getHeatDate, endBefore)
            .orderByDesc(PigHeat::getHeatDate, PigHeat::getId);
        Page<PigHeatVo> page = heatMapper.selectVoPage(pageQuery.build(), w);
        enrichSowMetrics(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 记录列表行级 enrich（row97 + row30）：断奶后天数 / 母猪日龄 / 胎次 / 查情时状态 / 状态持续天数。
     *
     * <ul>
     *   <li>断奶后天数 = 查情日期 - 该母猪「最近一条断奶记录」weaning_date（按 pigId 批量取最近一条，避免 N+1）；</li>
     *   <li>日龄 = 查情日期 - 母猪 birth_date；胎次 = t_farm_pig_info.parity（按 pigId 批量取）；</li>
     *   <li>查情时状态 = pig.currentStatus（近似，字典 djs_pig_lifecycle）；状态持续天数 = 查情日期 - pig.statusStartedAt。</li>
     * </ul>
     *
     * <p>任一来源缺失（无断奶记录 / 无出生日期 / statusStartedAt 为 null）对应字段留 null，前端兜底 — 占位。</p>
     */
    private void enrichSowMetrics(List<PigHeatVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> pigIds = rows.stream()
            .map(PigHeatVo::getPigId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (pigIds.isEmpty()) {
            return;
        }

        // 母猪基础信息（日龄基准 birth_date + 胎次 parity）：批量按 id 查，回 map
        Map<Long, Pig> pigMap = pigMapper.selectByIds(pigIds).stream()
            .collect(Collectors.toMap(Pig::getId, p -> p, (a, b) -> a));

        // 每头母猪最近一条断奶日期：批量查未软删断奶记录后 group 取 max（避免逐行查 DB）
        Map<Long, LocalDateTime> lastWeaningMap = new HashMap<>();
        List<PigWeaning> weanings = weaningMapper.selectList(
            Wrappers.<PigWeaning>lambdaQuery()
                .select(PigWeaning::getPigId, PigWeaning::getWeaningDate)
                .in(PigWeaning::getPigId, pigIds)
                .isNotNull(PigWeaning::getWeaningDate));
        for (PigWeaning wn : weanings) {
            if (wn.getPigId() == null || wn.getWeaningDate() == null) {
                continue;
            }
            lastWeaningMap.merge(wn.getPigId(), wn.getWeaningDate(),
                (cur, cand) -> cand.isAfter(cur) ? cand : cur);
        }

        for (PigHeatVo row : rows) {
            Long pigId = row.getPigId();
            if (pigId == null) {
                continue;
            }
            LocalDate heatDay = row.getHeatDate() != null ? row.getHeatDate().toLocalDate() : null;

            // 断奶后天数
            LocalDateTime lastWeaning = lastWeaningMap.get(pigId);
            if (heatDay != null && lastWeaning != null) {
                long days = ChronoUnit.DAYS.between(lastWeaning.toLocalDate(), heatDay);
                row.setDaysAfterWeaning((int) days);
            }

            // 日龄 + 胎次 + 查情时母猪状态 + 状态持续天数
            Pig pig = pigMap.get(pigId);
            if (pig != null) {
                if (heatDay != null && pig.getBirthDate() != null) {
                    long age = ChronoUnit.DAYS.between(pig.getBirthDate(), heatDay);
                    row.setDayAge((int) age);
                }
                row.setParity(pig.getParity());

                // 查情时母猪状态：近似取 currentStatus（OESTRUS 方案A 不切母猪态，查情后状态不变）
                row.setHeatStatus(pig.getCurrentStatus());

                // 状态持续天数 = 查情日期 - 进入当前状态时间（statusStartedAt 为 null 留 null）
                if (heatDay != null && pig.getStatusStartedAt() != null) {
                    long statusDays = ChronoUnit.DAYS.between(
                        pig.getStatusStartedAt().toLocalDate(), heatDay);
                    row.setStatusDays((int) statusDays);
                }
            }
        }
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
