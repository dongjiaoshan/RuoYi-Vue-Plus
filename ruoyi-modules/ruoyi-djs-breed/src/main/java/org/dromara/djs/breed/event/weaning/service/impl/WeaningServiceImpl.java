package org.dromara.djs.breed.event.weaning.service.impl;

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
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
import org.dromara.djs.breed.event.weaning.domain.PigWeaning;
import org.dromara.djs.breed.event.weaning.domain.PigWeaningDetail;
import org.dromara.djs.breed.event.weaning.domain.bo.WeaningBo;
import org.dromara.djs.breed.event.weaning.domain.bo.WeaningDetailBo;
import org.dromara.djs.breed.event.weaning.domain.query.WeaningQuery;
import org.dromara.djs.breed.event.weaning.domain.vo.PigWeaningDetailVo;
import org.dromara.djs.breed.event.weaning.domain.vo.PigWeaningVo;
import org.dromara.djs.breed.event.weaning.mapper.PigWeaningDetailMapper;
import org.dromara.djs.breed.event.weaning.mapper.PigWeaningMapper;
import org.dromara.djs.breed.event.weaning.service.IWeaningService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 断奶事件 Service 实现（BRD-EVENT-002 WEAN）。
 *
 * <h3>事务边界</h3>
 * <p>{@link #recordWeaning} 标 {@code @Transactional}：INSERT weaning + fireEvent(WEAN) 同生共死。</p>
 *
 * <h3>OQ-11 fallback</h3>
 * <p>V1 仅录"母猪汇总"，不写仔猪个体 {@code t_farm_wean_weight}（CR-20260524-11 + OQ-11 已决）。
 * 若客户要求逐头，单独 hotfix 加明细表 + N 行 INSERT。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeaningServiceImpl implements IWeaningService {

    private final PigWeaningMapper weaningMapper;
    private final PigWeaningDetailMapper weaningDetailMapper;
    private final PigMapper pigMapper;
    private final PigFarrowMapper farrowMapper;
    private final IPigCoreService pigCoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigWeaningVo recordWeaning(WeaningBo bo) {
        Objects.requireNonNull(bo, "WeaningBo must not be null");

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

        PigFarrow farrow = farrowMapper.selectById(bo.getFarrowId());
        if (farrow == null) {
            throw new ServiceException(I18nMessages.t("weaning.farrow_not_found", bo.getFarrowId()));
        }
        if (!Objects.equals(farrow.getPigId(), pig.getId())) {
            throw new ServiceException(I18nMessages.t("weaning.farrow_pig_mismatch",
                bo.getFarrowId(), bo.getPigId()));
        }
        if (bo.getWeanedCount() != null && farrow.getLiveBorn() != null
            && bo.getWeanedCount() > farrow.getLiveBorn()) {
            throw new ServiceException(I18nMessages.t("weaning.count_exceeds_live_born",
                bo.getWeanedCount(), farrow.getLiveBorn()));
        }

        // 1. 写 t_farm_pig_weaning
        PigWeaning entity = new PigWeaning();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setFarrowId(bo.getFarrowId());
        entity.setBreedingId(farrow.getBreedingId());
        entity.setWeaningDate(bo.getWeaningDate());
        entity.setWeanedCount(bo.getWeanedCount());
        entity.setWeanedWeight(bo.getWeanedWeight());
        entity.setAvgWeanedWeight(resolveAvg(bo));
        entity.setRemark(bo.getRemark());
        entity.setOperatorId(LoginHelper.getUserId());
        entity.setDelFlag("0");
        weaningMapper.insert(entity);

        // 2. 逐头录重明细（BRD-FIX-MP-EVENT-BREED-IA-001）：同事务批量 INSERT；details 空 → 退化仅汇总
        List<PigWeaningDetail> savedDetails = insertDetails(entity.getId(), bo.getDetails());

        // 3. 触发状态机 FM → DN
        PigEventBo eventBo = new PigEventBo();
        eventBo.setPigId(pig.getId());
        eventBo.setEventType(PigStatusEvent.WEAN);
        eventBo.setRelatedEventId(entity.getId());
        eventBo.setEventAt(bo.getWeaningDate());
        pigCoreService.fireEvent(eventBo);

        log.info("[BRD-EVENT-002] recordWeaning pigId={} earNo={} weaningId={} count={} detailRows={}",
            pig.getId(), pig.getEarNo(), entity.getId(), bo.getWeanedCount(), savedDetails.size());

        return toVo(entity, savedDetails);
    }

    /**
     * 逐头明细批量 INSERT（与主记录同事务）。details 为空时直接返空列表（向后兼容汇总录入）。
     * piglet_seq 缺省时按下发顺序从 1 补；tenant_id / 公共字段由 MP 自动填充。
     */
    private List<PigWeaningDetail> insertDetails(Long weaningId, List<WeaningDetailBo> details) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        List<PigWeaningDetail> rows = new ArrayList<>(details.size());
        int seq = 1;
        for (WeaningDetailBo d : details) {
            PigWeaningDetail row = new PigWeaningDetail();
            row.setWeaningId(weaningId);
            row.setPigletSeq(d.getPigletSeq() != null ? d.getPigletSeq() : seq);
            row.setEarNo(d.getEarNo());
            row.setWeight(d.getWeight());
            row.setDelFlag("0");
            rows.add(row);
            seq++;
        }
        weaningDetailMapper.insertBatch(rows);
        return rows;
    }

    @Override
    public TableDataInfo<PigWeaningVo> queryPage(WeaningQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigWeaning> w = Wrappers.<PigWeaning>lambdaQuery()
            .eq(query.getPigId() != null, PigWeaning::getPigId, query.getPigId())
            .eq(StringUtils.isNotBlank(query.getEarNo()), PigWeaning::getEarNo, query.getEarNo())
            .eq(query.getFarrowId() != null, PigWeaning::getFarrowId, query.getFarrowId())
            .ge(query.getBeginDate() != null, PigWeaning::getWeaningDate, query.getBeginDate())
            .le(query.getEndDate() != null, PigWeaning::getWeaningDate, query.getEndDate())
            .orderByDesc(PigWeaning::getWeaningDate, PigWeaning::getId);
        Page<PigWeaningVo> page = weaningMapper.selectVoPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    /** avg 优先取 BO 给的；若 weanedWeight + weanedCount 都有则算（保留 3 位小数）。 */
    private BigDecimal resolveAvg(WeaningBo bo) {
        if (bo.getAvgWeanedWeight() != null) {
            return bo.getAvgWeanedWeight();
        }
        int n = Optional.ofNullable(bo.getWeanedCount()).orElse(0);
        if (n <= 0 || bo.getWeanedWeight() == null) {
            return null;
        }
        return bo.getWeanedWeight().divide(BigDecimal.valueOf(n), 3, RoundingMode.HALF_UP);
    }

    private PigWeaningVo toVo(PigWeaning e, List<PigWeaningDetail> details) {
        PigWeaningVo v = new PigWeaningVo();
        v.setId(e.getId());
        v.setPigId(e.getPigId());
        v.setEarNo(e.getEarNo());
        v.setFarrowId(e.getFarrowId());
        v.setBreedingId(e.getBreedingId());
        v.setWeaningDate(e.getWeaningDate());
        v.setWeanedCount(e.getWeanedCount());
        v.setWeanedWeight(e.getWeanedWeight());
        v.setAvgWeanedWeight(e.getAvgWeanedWeight());
        v.setOperatorId(e.getOperatorId());
        v.setRemark(e.getRemark());
        v.setDetails(toDetailVos(details));
        return v;
    }

    private List<PigWeaningDetailVo> toDetailVos(List<PigWeaningDetail> details) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        List<PigWeaningDetailVo> vos = new ArrayList<>(details.size());
        for (PigWeaningDetail d : details) {
            PigWeaningDetailVo vo = new PigWeaningDetailVo();
            vo.setId(d.getId());
            vo.setWeaningId(d.getWeaningId());
            vo.setPigletSeq(d.getPigletSeq());
            vo.setEarNo(d.getEarNo());
            vo.setWeight(d.getWeight());
            vo.setRemark(d.getRemark());
            vos.add(vo);
        }
        return vos;
    }
}
