package org.dromara.djs.breed.event.eartag.service.impl;

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
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.EarNoAllocator;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBatchEarTagBo;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletEarTagItem;
import org.dromara.djs.breed.event.eartag.domain.query.PigletEarTagQuery;
import org.dromara.djs.breed.event.eartag.domain.vo.FarrowEarTagStatVo;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletnoVo;
import org.dromara.djs.breed.event.eartag.mapper.PigPigletnoMapper;
import org.dromara.djs.breed.event.eartag.service.IPigEarTagService;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 仔猪批量耳标 Service 实现（BRD-EVENT-003）。
 *
 * <h3>事务边界</h3>
 * <p>{@link #batchTag} 标 {@code @Transactional} — N 头仔猪 pig_info INSERT + N 条 pigletno INSERT
 * 同生共死，任一失败回滚（含耳号分配器内部异常，已由 redisson lease 兜底）。</p>
 *
 * <h3>耳号生成</h3>
 * <p>EAR_NO 规则 {@code {farmCode2}{barnCode2}{yyMM}{seq4}}。本 service 走 {@link EarNoAllocator}
 * 一次性拿连续 N 号（序号源 = DB max 同前缀同月 + 1，月内连续耳标不撞 UNIQUE）。
 * {@code barnCode2} 取自母猪 barn_id 关联的 Barn.barnCode 前 2 位；{@code farmCode2}
 * 固定 "01"（V1 单农场，详见 ADR-0001）。</p>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PigEarTagServiceImpl implements IPigEarTagService {

    private final PigMapper pigMapper;
    private final PigPigletnoMapper pigletnoMapper;
    private final PigFarrowMapper farrowMapper;
    private final BarnMapper barnMapper;
    private final EarNoAllocator earNoAllocator;

    @Override
    public FarrowEarTagStatVo statByFarrow(Long farrowId) {
        if (farrowId == null) {
            throw new ServiceException(I18nMessages.t("pigletno.farrow_id.required"));
        }
        PigFarrow farrow = farrowMapper.selectById(farrowId);
        if (farrow == null) {
            throw new ServiceException(I18nMessages.t("pigletno.farrow.not_found", farrowId));
        }
        Pig mother = pigMapper.selectById(farrow.getPigId());

        List<PigPigletno> existing = pigletnoMapper.selectList(
            Wrappers.<PigPigletno>lambdaQuery()
                .eq(PigPigletno::getFarrowId, farrowId)
                .orderByAsc(PigPigletno::getTagDate, PigPigletno::getId));
        int tagged = existing.size();
        int liveBorn = Optional.ofNullable(farrow.getLiveBorn()).orElse(0);

        FarrowEarTagStatVo vo = new FarrowEarTagStatVo();
        vo.setFarrowId(farrowId);
        vo.setMotherPigId(farrow.getPigId());
        vo.setMotherEar(farrow.getEarNo());
        vo.setFarrowDate(farrow.getFarrowDate());
        vo.setParity(farrow.getParity());
        vo.setLiveBorn(liveBorn);
        vo.setTagged(tagged);
        vo.setRemaining(Math.max(0, liveBorn - tagged));

        // 已贴清单（含 pig_info 字段拼装；pig_id 可能为 null 仅在历史 mock 场景，正常路径全有）
        List<PigletEarTagVo> taggedList = new ArrayList<>(tagged);
        if (tagged > 0) {
            Map<Long, Pig> pigById = loadPigsByIds(existing.stream().map(PigPigletno::getPigId).toList());
            for (PigPigletno log : existing) {
                Pig p = log.getPigId() == null ? null : pigById.get(log.getPigId());
                if (p == null) {
                    p = new Pig();
                    p.setId(log.getPigId());
                    p.setCurrentStatus(PigLifecycle.HB.name());
                    p.setBirthDate(farrow.getFarrowDate() == null ? null : farrow.getFarrowDate().toLocalDate());
                }
                taggedList.add(PigletEarTagVo.from(p, log));
            }
        }
        vo.setTaggedList(taggedList);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PigletEarTagVo> batchTag(PigletBatchEarTagBo bo) {
        if (bo == null || bo.getFarrowId() == null || bo.getPiglets() == null || bo.getPiglets().isEmpty()) {
            throw new ServiceException(I18nMessages.t("pigletno.bo.required"));
        }

        // 1. 校验 farrow 存在 + 母猪存在 + 已贴 + 本批 ≤ live_born
        PigFarrow farrow = farrowMapper.selectById(bo.getFarrowId());
        if (farrow == null) {
            throw new ServiceException(I18nMessages.t("pigletno.farrow.not_found", bo.getFarrowId()));
        }
        Pig mother = pigMapper.selectById(farrow.getPigId());
        if (mother == null) {
            throw new ServiceException(I18nMessages.t("pigletno.mother.not_found", farrow.getPigId()));
        }

        Long tagged = pigletnoMapper.selectCount(
            new LambdaQueryWrapper<PigPigletno>().eq(PigPigletno::getFarrowId, bo.getFarrowId()));
        int newCount = bo.getPiglets().size();
        int liveBorn = Optional.ofNullable(farrow.getLiveBorn()).orElse(0);
        long requested = tagged + newCount;
        if (requested > liveBorn) {
            throw new ServiceException(
                I18nMessages.t("pigletno.exceeds_live_born", requested, liveBorn));
        }

        // 2. 反查父猪耳号（自然/人工授精均可，可空）
        String fatherEar = farrow.getBreedingId() == null
            ? null
            : farrowMapper.selectBoarEarByBreedingId(farrow.getBreedingId());

        // 3. 一次性批量生成 N 个连续耳号（序号源 = DB max 同前缀同月，避免日级计数器月内撞 UNIQUE）
        List<String> earNos = earNoAllocator.allocate("01", motherBarnCode(mother), newCount);

        // 4. 同事务循环 INSERT pig + pigletno
        LocalDateTime tagAt = LocalDateTime.now();
        LocalDate birthDate = farrow.getFarrowDate() == null ? null : farrow.getFarrowDate().toLocalDate();
        List<PigletEarTagVo> result = new ArrayList<>(newCount);
        for (int i = 0; i < newCount; i++) {
            PigletEarTagItem item = bo.getPiglets().get(i);
            String earNo = earNos.get(i);

            Pig piglet = new Pig();
            piglet.setEarNo(earNo);
            piglet.setLifecycleId(1);
            piglet.setRecyclable(0);
            piglet.setPigSex(item.getPigletSex());
            piglet.setPigType("piglet");
            piglet.setPigBreedCode(mother.getPigBreedCode());
            piglet.setPigStrainCode(mother.getPigStrainCode());
            piglet.setCurrentStatus(PigLifecycle.HB.name());
            piglet.setStatusStartedAt(tagAt);
            piglet.setFatherEar(fatherEar);
            piglet.setMotherEar(mother.getEarNo());
            piglet.setBirthDate(birthDate);
            piglet.setParity(0);
            piglet.setBarnId(mother.getBarnId());
            piglet.setPenId(mother.getPenId());
            piglet.setIsAppointed(0);
            piglet.setRemark(item.getRemark());
            piglet.setDelFlag("0");
            piglet.setDelUnique(0L);
            piglet.setVersion(0);
            pigMapper.insert(piglet);

            PigPigletno log = new PigPigletno();
            log.setPigletEarNo(earNo);
            log.setMotherEarNo(mother.getEarNo());
            log.setFatherEarNo(fatherEar);
            log.setFarrowId(bo.getFarrowId());
            log.setTagDate(tagAt);
            log.setPigletSex(item.getPigletSex());
            log.setBirthWeight(item.getBirthWeight());
            log.setPigId(piglet.getId());
            log.setRemark(item.getRemark());
            log.setOperatorId(LoginHelper.getUserId());
            log.setDelFlag("0");
            log.setDelUnique(0L);
            pigletnoMapper.insert(log);

            result.add(PigletEarTagVo.from(piglet, log));
        }

        log.info("[BRD-EVENT-003] batchTag farrowId={} motherEar={} count={} earNos=[{}..{}]",
            bo.getFarrowId(), mother.getEarNo(), newCount,
            earNos.get(0), earNos.get(newCount - 1));

        // VO 列表按耳号 asc 返回，便于前端展示连续序号
        result.sort(Comparator.comparing(PigletEarTagVo::getPigletEarNo));
        return result;
    }

    /**
     * 取母猪所在栋舍编码（EAR_NO 前缀 barnCode2 段）。
     *
     * <p>母猪未关联 barn / barn 缺 code 时返 null，由 {@link EarNoAllocator} 回落 "00"
     * （V1 单农场场景 farmCode 固定 "01"）。</p>
     */
    private String motherBarnCode(Pig mother) {
        if (mother != null && mother.getBarnId() != null) {
            Barn barn = barnMapper.selectById(mother.getBarnId());
            if (barn != null) {
                return barn.getBarnCode();
            }
        }
        return null;
    }

    private Map<Long, Pig> loadPigsByIds(List<Long> ids) {
        List<Long> filtered = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (filtered.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Pig> pigs = pigMapper.selectByIds(filtered);
        Map<Long, Pig> m = new HashMap<>(pigs.size() * 2);
        for (Pig p : pigs) {
            m.put(p.getId(), p);
        }
        return m;
    }

    @Override
    public TableDataInfo<PigletnoVo> queryPage(PigletEarTagQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigPigletno> wrapper = Wrappers.<PigPigletno>lambdaQuery()
            .eq(StringUtils.isNotBlank(query.getPigletEarNo()), PigPigletno::getPigletEarNo, query.getPigletEarNo())
            .eq(StringUtils.isNotBlank(query.getMotherEarNo()), PigPigletno::getMotherEarNo, query.getMotherEarNo())
            .eq(query.getFarrowId() != null, PigPigletno::getFarrowId, query.getFarrowId())
            .eq(StringUtils.isNotBlank(query.getPigletSex()), PigPigletno::getPigletSex, query.getPigletSex())
            .ge(query.getBeginDate() != null, PigPigletno::getTagDate, query.getBeginDate())
            .le(query.getEndDate() != null, PigPigletno::getTagDate, query.getEndDate())
            .orderByDesc(PigPigletno::getId);
        Page<PigletnoVo> page = pigletnoMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichFarrowDate(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 列表 enrich：分娩日期替代裸 farrowId（母猪耳号 motherEarNo 已平铺在 pigletno 表）。
     * 收集去重 farrowId 一次性批查，避免 N+1。
     */
    private void enrichFarrowDate(List<PigletnoVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> farrowIds = rows.stream().map(PigletnoVo::getFarrowId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (farrowIds.isEmpty()) {
            return;
        }
        Map<Long, LocalDateTime> farrowDates = farrowMapper.selectByIds(farrowIds).stream()
            .filter(f -> f.getId() != null && f.getFarrowDate() != null)
            .collect(Collectors.toMap(PigFarrow::getId, PigFarrow::getFarrowDate, (a, b) -> a));
        for (PigletnoVo vo : rows) {
            vo.setFarrowDate(farrowDates.get(vo.getFarrowId()));
        }
    }
}
