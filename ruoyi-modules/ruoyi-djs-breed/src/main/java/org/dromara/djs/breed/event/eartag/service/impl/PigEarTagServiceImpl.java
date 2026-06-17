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
import org.dromara.djs.breed.breeding.domain.BreedConfig;
import org.dromara.djs.breed.breeding.mapper.BreedConfigMapper;
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBatchEarTagBo;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletEarTagItem;
import org.dromara.djs.breed.event.eartag.domain.query.PigletEarTagQuery;
import org.dromara.djs.breed.event.eartag.domain.vo.EarNoPreviewVo;
import org.dromara.djs.breed.event.eartag.domain.vo.FarrowEarTagStatVo;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletnoVo;
import org.dromara.djs.breed.event.eartag.mapper.PigPigletnoMapper;
import org.dromara.djs.breed.event.eartag.service.IPigEarTagService;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
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
 * <p>EAR_NO 规则 {@code {品系}-{品种2}-{出生yyMMdd6}-{当天序号3}}（如 {@code 4-04-260508-001}，ADR-0011，
 * 6/15 起耳号不再编码性别）。本 service 走 {@link EarNoAllocator} 整批一次性拿连续 N 号（序号源 = DB max
 * 同前缀 + 1，当天同前缀连续耳标不撞 UNIQUE）。出生日取分娩日；性别仅存 pig.pig_sex 列。</p>
 *
 * <p>仔猪品种/品系码取育种配置表 {@code t_farm_breed_config} 按【母本码 × 父本码】查仔代码
 * （品种 breed_strain=1 / 品系 breed_strain=2 各查一次，母父码 2 位零填充对齐配置表存码）。
 * 父本耳号空（人工授精无父猪行）、父猪查不到、或配置表无该杂交组合时，对应维度回落继承母猪码
 * 并 log.warn，不抛异常阻断打标流程（邓博 2026-06-17 #23）。</p>
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
    private final EarNoAllocator earNoAllocator;
    private final BreedConfigMapper breedConfigMapper;

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

        // 2.5 解析仔代品种/品系码（母本 × 父本 → 育种配置表；缺则回落母猪）
        String[] cub = resolveCubBreedStrain(mother, fatherEar);
        String cubBreedCode = cub[0];
        String cubStrainCode = cub[1];

        // 3. 生成 N 个耳号（仔代品系/品种码 + 出生日 = 分娩日；6/15 起耳号不编码性别 → 整批连号按原序）
        LocalDateTime tagAt = LocalDateTime.now();
        LocalDate birthDate = farrow.getFarrowDate() == null ? tagAt.toLocalDate() : farrow.getFarrowDate().toLocalDate();
        List<String> earNos = allocatePigletEarNos(cubStrainCode, cubBreedCode, bo.getPiglets().size(), birthDate);

        // 4. 同事务循环 INSERT pig + pigletno
        List<PigletEarTagVo> result = new ArrayList<>(newCount);
        for (int i = 0; i < newCount; i++) {
            PigletEarTagItem item = bo.getPiglets().get(i);
            String earNo = earNos.get(i);

            Pig piglet = new Pig();
            // 耳号全号写入：ear_no（UNIQUE 连号源）+ ear_tag（耳号全版列）均存全号，
            // 以全号为权威；短号仅在展示层（mp）按需截取，不在库内拆分。
            piglet.setEarNo(earNo);
            piglet.setEarTag(earNo);
            piglet.setLifecycleId(1);
            piglet.setRecyclable(0);
            piglet.setPigSex(item.getPigletSex());
            piglet.setPigType("piglet");
            piglet.setPigBreedCode(cubBreedCode);
            piglet.setPigStrainCode(cubStrainCode);
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
            // 打标人员：优先取前端所选 operatorId，空则回落登录态（兼容旧入参不带人员）
            log.setOperatorId(bo.getOperatorId() != null ? bo.getOperatorId() : LoginHelper.getUserId());
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

    /** 预览序号补零位宽（与 EarNoAllocator SEQ_WIDTH 对齐，6/15 起 3 位）。 */
    private static final int PREVIEW_SEQ_WIDTH = 3;

    /** 段间分隔符（与 EarNoAllocator SEG_SEP 对齐）。 */
    private static final String PREVIEW_SEG_SEP = "-";

    @Override
    public EarNoPreviewVo previewEarNos(Long farrowId, int maleCount, int femaleCount) {
        if (farrowId == null) {
            throw new ServiceException(I18nMessages.t("pigletno.farrow_id.required"));
        }
        PigFarrow farrow = farrowMapper.selectById(farrowId);
        if (farrow == null) {
            throw new ServiceException(I18nMessages.t("pigletno.farrow.not_found", farrowId));
        }
        Pig mother = pigMapper.selectById(farrow.getPigId());
        if (mother == null) {
            throw new ServiceException(I18nMessages.t("pigletno.mother.not_found", farrow.getPigId()));
        }
        LocalDate birthDate = farrow.getFarrowDate() == null
            ? LocalDate.now()
            : farrow.getFarrowDate().toLocalDate();
        // 预览前缀与正式打标同口径：仔代品系/品种码取育种配置表（母 × 父），缺则回落母猪
        String fatherEar = farrow.getBreedingId() == null
            ? null
            : farrowMapper.selectBoarEarByBreedingId(farrow.getBreedingId());
        String[] cub = resolveCubBreedStrain(mother, fatherEar);

        EarNoPreviewVo vo = new EarNoPreviewVo();
        // 6/15 起耳号不再编码性别 → 公母共用同一连号序列：前 maleCount 个给公、后 femaleCount 个给母（按预览顺序）。
        int male = Math.max(0, maleCount);
        int female = Math.max(0, femaleCount);
        List<String> all = previewSeq(cub[1], cub[0], birthDate, male + female);
        vo.setMaleEarNos(new ArrayList<>(all.subList(0, male)));
        vo.setFemaleEarNos(new ArrayList<>(all.subList(male, male + female)));
        return vo;
    }

    /**
     * 预览整批下一批连号：{@code 前缀 + (DB max 同前缀 + 1 .. +count)}。性别不再入前缀，公母同序列。
     * 仅读不锁不占号；count ≤ 0 返空列表。前缀用解析后的仔代品系/品种码。
     */
    private List<String> previewSeq(String cubStrainCode, String cubBreedCode, LocalDate birthDate, int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        String prefix = earNoAllocator.buildPrefix(cubStrainCode, cubBreedCode, null, birthDate);
        long nextSeq = earNoAllocator.nextSeqForPrefix(prefix);
        List<String> earNos = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            earNos.add(prefix + PREVIEW_SEG_SEP + String.format("%0" + PREVIEW_SEQ_WIDTH + "d", nextSeq + i));
        }
        return earNos;
    }

    /**
     * 为一批仔猪分配耳号：用解析后的仔代品系/品种码 + 出生日 = 分娩日。
     *
     * <p>6/15 起耳号不再编码性别 → 同批公母仔猪同前缀，一次 {@code allocate(count)} 连号即可，
     * 按原始索引顺序对齐返回（不再按性别分组）。</p>
     */
    private List<String> allocatePigletEarNos(String cubStrainCode, String cubBreedCode, int count, LocalDate birthDate) {
        if (count <= 0) {
            return new ArrayList<>();
        }
        return earNoAllocator.allocate(cubStrainCode, cubBreedCode, null, birthDate, count);
    }

    /**
     * 解析仔猪品种/品系码：取育种配置表 {@code t_farm_breed_config} 按【母本码 × 父本码】查仔代码
     * （品种 breed_strain=1 / 品系 breed_strain=2 各查一次）。父本耳号空、父猪查不到、或配置表无该
     * 组合时，对应维度回落继承母猪码（保打标流程不因配置缺失中断；邓博 2026-06-17 #23）。
     *
     * @param mother    母猪（提供母本品种/品系码）
     * @param fatherEar 父猪耳号（可空，自然 / 人工授精）
     * @return {@code {仔代品种码, 仔代品系码}}
     */
    private String[] resolveCubBreedStrain(Pig mother, String fatherEar) {
        String motherBreed = mother.getPigBreedCode();
        String motherStrain = mother.getPigStrainCode();
        // 默认回落：无父本信息时继承母猪
        String cubBreed = motherBreed;
        String cubStrain = motherStrain;
        if (StringUtils.isBlank(fatherEar)) {
            return new String[]{cubBreed, cubStrain};
        }
        // LIMIT 1：耳号复用（lifecycle_id 不同）时同一 ear_no 可有多条 del_flag='0' 活跃行，
        // 取最新一条防 selectOne 多行抛异常中断打标（与 SowDetailServiceImpl.findByEarNo 同范式）。
        Pig boar = pigMapper.selectOne(new LambdaQueryWrapper<Pig>()
            .eq(Pig::getEarNo, fatherEar)
            .eq(Pig::getDelFlag, "0")
            .orderByDesc(Pig::getId)
            .last("LIMIT 1"));
        if (boar == null) {
            log.warn("[BRD-EVENT-003] 父猪耳号 {} 未查到 pig_info，仔猪品种/品系回落继承母猪", fatherEar);
            return new String[]{cubBreed, cubStrain};
        }
        String cb = lookupCubCode(1, motherBreed, boar.getPigBreedCode());
        if (StringUtils.isNotBlank(cb)) {
            cubBreed = cb;
        } else {
            log.warn("[BRD-EVENT-003] 育种配置缺品种组合 母={} 父={}，仔猪品种回落继承母猪 {}",
                motherBreed, boar.getPigBreedCode(), motherBreed);
        }
        String cs = lookupCubCode(2, motherStrain, boar.getPigStrainCode());
        if (StringUtils.isNotBlank(cs)) {
            cubStrain = cs;
        } else {
            log.warn("[BRD-EVENT-003] 育种配置缺品系组合 母={} 父={}，仔猪品系回落继承母猪 {}",
                motherStrain, boar.getPigStrainCode(), motherStrain);
        }
        return new String[]{cubBreed, cubStrain};
    }

    /**
     * 查育种配置表得仔代码：{@code breed_strain + mother_code + father_code → cub_code}。
     * 母/父码按 2 位零填充后匹配（pig_info 品系码存 1 位 dict_value '1'，配置表存 2 位 '01'）。
     * 无匹配返 {@code null}（由调用方回落继承母猪）。
     */
    private String lookupCubCode(int breedStrain, String motherCode, String fatherCode) {
        if (StringUtils.isBlank(motherCode) || StringUtils.isBlank(fatherCode)) {
            return null;
        }
        BreedConfig cfg = breedConfigMapper.selectOne(new LambdaQueryWrapper<BreedConfig>()
            .eq(BreedConfig::getBreedStrain, breedStrain)
            .eq(BreedConfig::getMotherCode, pad2(motherCode))
            .eq(BreedConfig::getFatherCode, pad2(fatherCode))
            .eq(BreedConfig::getDelFlag, "0"));
        return cfg == null ? null : cfg.getCubCode();
    }

    /** 品种/品系码 2 位零填充（{@code '1'→'01'}，{@code '01'→'01'}），对齐育种配置表存码格式。 */
    private static String pad2(String code) {
        String c = code == null ? "" : code.trim();
        return c.length() == 1 ? "0" + c : c;
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
        LocalDateTime tagBeginAt = query.getBeginDate() != null ? query.getBeginDate().atStartOfDay() : null;
        LocalDateTime tagEndBefore = query.getEndDate() != null ? query.getEndDate().plusDays(1).atStartOfDay() : null;
        LambdaQueryWrapper<PigPigletno> wrapper = Wrappers.<PigPigletno>lambdaQuery()
            .like(StringUtils.isNotBlank(query.getPigletEarNo()), PigPigletno::getPigletEarNo, query.getPigletEarNo())
            .eq(StringUtils.isNotBlank(query.getMotherEarNo()), PigPigletno::getMotherEarNo, query.getMotherEarNo())
            .eq(query.getFarrowId() != null, PigPigletno::getFarrowId, query.getFarrowId())
            .eq(StringUtils.isNotBlank(query.getPigletSex()), PigPigletno::getPigletSex, query.getPigletSex())
            .ge(tagBeginAt != null, PigPigletno::getTagDate, tagBeginAt)
            .lt(tagEndBefore != null, PigPigletno::getTagDate, tagEndBefore)
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
