package org.dromara.djs.breed.event.transfer.service.impl;

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
import org.dromara.djs.breed.event.transfer.domain.PigTransfer;
import org.dromara.djs.breed.event.transfer.domain.bo.TransferBatchBo;
import org.dromara.djs.breed.event.transfer.domain.bo.TransferBo;
import org.dromara.djs.breed.event.transfer.domain.query.TransferQuery;
import org.dromara.djs.breed.event.transfer.domain.vo.PigTransferVo;
import org.dromara.djs.breed.event.transfer.mapper.PigTransferMapper;
import org.dromara.djs.breed.event.transfer.service.ITransferService;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 转移事件 Service 实现（BRD-EVENT-004 TRANSFER）。
 *
 * <h3>side effect：仔猪→育肥舍 切 pig_type（D5 audit a-2）</h3>
 * <p>若目标 barn.barn_type='fattening' 且当前 pig.pig_type='piglet'，payload
 * 追加 newPigType='fattening'，由 PigCoreServiceImpl.applyEventSideEffects 写回
 * pig.pig_type。否则 dashboard 育肥猪存栏（BRD-DASH-001）漏算。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements ITransferService {

    private final PigTransferMapper transferMapper;
    private final PigMapper pigMapper;
    private final BarnMapper barnMapper;
    private final PenMapper penMapper;
    private final IPigCoreService pigCoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigTransferVo recordTransfer(TransferBo bo) {
        Objects.requireNonNull(bo, "TransferBo must not be null");

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

        // 二选一：admin 传 newBarnId / mp 传 newBarnCode
        if (bo.getNewBarnId() == null) {
            if (bo.getNewBarnCode() == null || bo.getNewBarnCode().isBlank()) {
                throw new ServiceException(I18nMessages.t("transfer.new_barn.required"), 400);
            }
            Barn b = barnMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Barn>lambdaQuery()
                    .eq(Barn::getBarnCode, bo.getNewBarnCode()).last("LIMIT 1"));
            if (b == null) {
                throw new ServiceException(I18nMessages.t("transfer.new_barn.not_found", bo.getNewBarnCode()), 400);
            }
            bo.setNewBarnId(b.getId());
        }
        Barn newBarn = barnMapper.selectById(bo.getNewBarnId());
        if (newBarn == null) {
            throw new ServiceException(I18nMessages.t("transfer.new_barn.not_found", bo.getNewBarnId()), 400);
        }
        // 二选一：admin 传 newPenId / mp 传 newPenCode（pen 限定在新栋舍内查找）
        if (bo.getNewPenId() == null && bo.getNewPenCode() != null && !bo.getNewPenCode().isBlank()) {
            Pen p = penMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Pen>lambdaQuery()
                    .eq(Pen::getBarnId, newBarn.getId())
                    .eq(Pen::getPenCode, bo.getNewPenCode()).last("LIMIT 1"));
            if (p == null) {
                throw new ServiceException(I18nMessages.t("transfer.new_pen.not_found", bo.getNewPenCode()), 400);
            }
            bo.setNewPenId(p.getId());
        }
        Pen newPen = bo.getNewPenId() == null ? null : penMapper.selectById(bo.getNewPenId());
        if (bo.getNewPenId() != null && newPen == null) {
            throw new ServiceException(I18nMessages.t("transfer.new_pen.not_found", bo.getNewPenId()), 400);
        }

        // 1. 记录转移历史
        PigTransfer entity = new PigTransfer();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setTransferDate(bo.getTransferDate());
        entity.setOldBarnId(pig.getBarnId());
        entity.setOldPenId(pig.getPenId());
        entity.setOldBarnName(resolveBarnName(pig.getBarnId()));
        entity.setOldPenName(resolvePenName(pig.getPenId()));
        entity.setNewBarnId(bo.getNewBarnId());
        entity.setNewPenId(bo.getNewPenId());
        entity.setNewBarnName(newBarn.getBarnName());
        entity.setNewPenName(newPen == null ? null : newPen.getPenName());
        entity.setTransferReason(bo.getTransferReason());
        entity.setRemark(bo.getRemark());
        // 转移人员：优先 EmployeePicker 所选 userId（bo.operator 数字串），未选 / 非数字回落登录用户
        entity.setOperatorId(resolveOperatorId(bo.getOperator()));
        entity.setDelFlag("0");
        transferMapper.insert(entity);

        // 2. payload：newBarnId/newPenId 必传；piglet → fattening 切 pig_type
        Map<String, Object> payload = new HashMap<>(4);
        payload.put("newBarnId", bo.getNewBarnId());
        if (bo.getNewPenId() != null) {
            payload.put("newPenId", bo.getNewPenId());
        }
        if ("fattening".equals(newBarn.getBarnType()) && "piglet".equals(pig.getPigType())) {
            payload.put("newPigType", "fattening");
        }

        PigEventBo eventBo = new PigEventBo();
        eventBo.setPigId(pig.getId());
        eventBo.setEventType(PigStatusEvent.TRANSFER);
        eventBo.setRelatedEventId(entity.getId());
        eventBo.setEventAt(bo.getTransferDate());
        eventBo.setPayload(payload);
        pigCoreService.fireEvent(eventBo);

        log.info("[BRD-EVENT-004] recordTransfer pigId={} earNo={} transferId={} {} → {} newPigType={}",
            pig.getId(), pig.getEarNo(), entity.getId(),
            pig.getBarnId(), bo.getNewBarnId(), payload.get("newPigType"));

        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PigTransferVo> recordTransferBatch(TransferBatchBo batchBo) {
        Objects.requireNonNull(batchBo, "TransferBatchBo must not be null");
        if (batchBo.getEarNos() == null || batchBo.getEarNos().isEmpty()) {
            throw new ServiceException(I18nMessages.t("transfer.batch.empty"), 400);
        }
        // 逐头复用单只 recordTransfer：每只都触发完整 side effect（barn/pen 切换 +
        // piglet→fattening 的 pig_type 切换）；整批同一事务，任一失败全回滚。
        List<PigTransferVo> results = new ArrayList<>(batchBo.getEarNos().size());
        for (String earNo : batchBo.getEarNos()) {
            if (earNo == null || earNo.isBlank()) {
                continue;
            }
            TransferBo single = new TransferBo();
            single.setEarNo(earNo.trim());
            single.setTransferDate(batchBo.getTransferDate());
            single.setNewBarnId(batchBo.getNewBarnId());
            single.setNewBarnCode(batchBo.getNewBarnCode());
            single.setNewPenId(batchBo.getNewPenId());
            single.setNewPenCode(batchBo.getNewPenCode());
            single.setTransferReason(batchBo.getTransferReason());
            single.setOperator(batchBo.getOperator());
            single.setRemark(batchBo.getRemark());
            results.add(recordTransfer(single));
        }
        if (results.isEmpty()) {
            throw new ServiceException(I18nMessages.t("transfer.batch.empty"), 400);
        }
        log.info("[BRD-FIX-MP-EVENT-LEAVE-IA-001] recordTransferBatch count={} newBarn={}",
            results.size(), batchBo.getNewBarnCode() != null ? batchBo.getNewBarnCode() : batchBo.getNewBarnId());
        return results;
    }

    @Override
    public TableDataInfo<PigTransferVo> queryPage(TransferQuery query, PageQuery pageQuery) {
        LocalDateTime beginAt = query.getBeginDate() != null ? query.getBeginDate().atStartOfDay() : null;
        LocalDateTime endBefore = query.getEndDate() != null ? query.getEndDate().plusDays(1).atStartOfDay() : null;
        LambdaQueryWrapper<PigTransfer> w = Wrappers.<PigTransfer>lambdaQuery()
            .eq(query.getPigId() != null, PigTransfer::getPigId, query.getPigId())
            .like(StringUtils.isNotBlank(query.getEarNo()), PigTransfer::getEarNo, query.getEarNo())
            .eq(query.getOldBarnId() != null, PigTransfer::getOldBarnId, query.getOldBarnId())
            .eq(query.getNewBarnId() != null, PigTransfer::getNewBarnId, query.getNewBarnId())
            .ge(beginAt != null, PigTransfer::getTransferDate, beginAt)
            .lt(endBefore != null, PigTransfer::getTransferDate, endBefore)
            .orderByDesc(PigTransfer::getTransferDate, PigTransfer::getId);
        Page<PigTransferVo> page = transferMapper.selectVoPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    /**
     * 解析转移人员 id：EmployeePicker 选中传 userId（数字串）→ 解析；空 / 非数字（无效输入）→ 回落登录用户。
     * 防止非数字 operator（历史脏数据或异常前端）触发 NumberFormatException 500。
     */
    private Long resolveOperatorId(String operator) {
        if (operator != null && operator.matches("\\d+")) {
            return Long.parseLong(operator);
        }
        return LoginHelper.getUserId();
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

    private PigTransferVo toVo(PigTransfer e) {
        PigTransferVo v = new PigTransferVo();
        v.setId(e.getId());
        v.setPigId(e.getPigId());
        v.setEarNo(e.getEarNo());
        v.setTransferDate(e.getTransferDate());
        v.setOldBarnId(e.getOldBarnId());
        v.setOldPenId(e.getOldPenId());
        v.setOldBarnName(e.getOldBarnName());
        v.setOldPenName(e.getOldPenName());
        v.setNewBarnId(e.getNewBarnId());
        v.setNewPenId(e.getNewPenId());
        v.setNewBarnName(e.getNewBarnName());
        v.setNewPenName(e.getNewPenName());
        v.setTransferReason(e.getTransferReason());
        v.setOperatorId(e.getOperatorId());
        v.setRemark(e.getRemark());
        return v;
    }
}
