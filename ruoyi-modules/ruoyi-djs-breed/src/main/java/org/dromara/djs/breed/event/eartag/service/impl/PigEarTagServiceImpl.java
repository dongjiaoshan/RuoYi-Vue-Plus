package org.dromara.djs.breed.event.eartag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.satoken.utils.LoginHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.EarNoAllocator;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.breeding.domain.BreedConfig;
import org.dromara.djs.breed.breeding.mapper.BreedConfigMapper;
import org.dromara.djs.common.constant.DictTypeConstants;
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBatchEarTagBo;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBirthWeightBo;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBirthWeightItem;
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
import org.dromara.djs.breed.farm.service.PenCountUpdater;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * <p>EAR_NO 规则 {@code {品系}-{品种2}-{性别1}-{出生yyMMdd6}-{当天序号3}}（如 {@code 4-04-1-260508-001}，ADR-0011，
 * 性别码 {@code 1=公(M) / 2=母(F)}，与外部引种同格式）。同批公母混合时，公组/母组因性别段不同而前缀分裂，但<b>共享
 * 当天全场同一连续序号空间</b>——由 {@link EarNoAllocator#allocateBatchByPrefixes} 在单锁内整批一次性算连续 N 号
 * （序号源 = DB max 同出生日段 + 1，公母交叉不撞 UNIQUE），按原始 {@code piglets} 索引回填。出生日取分娩日。</p>
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
    private final PenCountUpdater penCountUpdater;
    private final DictService dictService;

    /** 字典 {@code djs_piglet_default_weight} 下「出生重」那一项的标签（同字典另有「仔猪断奶重」项）。 */
    private static final String BIRTH_WEIGHT_DICT_LABEL = "仔猪出生重";

    /** 出生重兜底值 kg：字典缺失 / 值非数字时用（D-0107 fallback，与 seed 值一致）。 */
    private static final BigDecimal DEFAULT_BIRTH_WEIGHT = new BigDecimal("2");

    @Override
    public FarrowEarTagStatVo statByFarrow(Long farrowId) {
        if (farrowId == null) {
            throw new ServiceException(I18nMessages.t("pigletno.farrow_id.required"));
        }
        PigFarrow farrow = farrowMapper.selectById(farrowId);
        if (farrow == null) {
            throw new ServiceException(I18nMessages.t("pigletno.farrow.not_found", String.valueOf(farrowId)));
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
        // 【旧小程序兼容】自动建档后 tagged == liveBorn，remaining 会是 0，
        // 旧 mp 据此铺 0 行并把提交键置灰（eartag/index.vue:208,579）——整页变死路。
        // 已建满时回报 liveBorn，让旧 mp 铺满行走 batchTag 的订正降级路径。
        // 新 mp 不读这个字段做铺行（它按 taggedList 的真实耳号铺），不受影响。
        vo.setRemaining(tagged > 0 && tagged >= liveBorn ? liveBorn : Math.max(0, liveBorn - tagged));

        // 已贴清单（含 pig_info 字段拼装；pig_id 可能为 null 仅在历史 mock 场景，正常路径全有）
        List<PigletEarTagVo> taggedList = new ArrayList<>(tagged);
        if (tagged > 0) {
            Map<Long, Pig> pigById = loadPigsByIds(existing.stream().map(PigPigletno::getPigId).toList());
            for (PigPigletno log : existing) {
                Pig p = log.getPigId() == null ? null : pigById.get(log.getPigId());
                if (p == null) {
                    p = new Pig();
                    p.setId(log.getPigId());
                    p.setCurrentStatus("");
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
        PigFarrow farrow = farrowMapper.selectById(bo.getFarrowId());
        if (farrow == null) {
            throw new ServiceException(I18nMessages.t("pigletno.farrow.not_found", String.valueOf(bo.getFarrowId())));
        }
        // 【旧小程序兼容】V6 行242 起分娩提交即自动建档，整窝 tagged == live_born，
        // 旧 mp 走到这里再建就会撞 exceeds_live_born。旧 mp 的提交体只有 pigletSex + birthWeight、
        // 不带耳号（耳号本来由后端生成），所以只能按性别顺序把重量贴到已建好的那几头上。
        // 没有这段，从后端上线到小程序审核通过这段窗口里，工人录完分娩会被旧 mp 自动带到
        // 一张 0 行、提交键点不动的耳标页，出生重无处可录。
        List<PigPigletno> existing = selectLitterPiglets(farrow.getId());
        if (!existing.isEmpty()) {
            return adjustBirthWeightsBySexOrder(farrow, existing, bo.getPiglets());
        }
        return createPigletsForFarrow(farrow, bo.getPiglets(), bo.getOperatorId());
    }

    /**
     * 旧小程序兼容路径：按性别顺序把提交上来的出生重贴到已建档的仔猪上。
     *
     * <p>新客户端走 {@code POST /djs/breed/event/eartag/birth-weight}（按耳号定位，精确）；
     * 这条只服务「提交体里没有耳号」的旧 mp。同性别内按耳号升序一一对应——耳号是建档时
     * 连号分配的，顺序与旧 mp 铺行顺序（先公后母）一致。</p>
     *
     * <p>提交头数多于已建档头数时只贴前 N 头并 warn，不新建、不报错：旧 mp 的行数是按
     * live_born 铺的，正常等长；不等长只可能是并发或脏数据，此时保住已有档案比报错更有用。</p>
     */
    private List<PigletEarTagVo> adjustBirthWeightsBySexOrder(PigFarrow farrow,
                                                              List<PigPigletno> existing,
                                                              List<PigletEarTagItem> submitted) {
        Map<String, List<PigPigletno>> bySex = new HashMap<>();
        for (PigPigletno row : existing) {
            bySex.computeIfAbsent(row.getPigletSex(), k -> new ArrayList<>()).add(row);
        }
        bySex.values().forEach(l -> l.sort(Comparator.comparing(PigPigletno::getPigletEarNo,
            Comparator.nullsLast(Comparator.naturalOrder()))));
        Map<String, Integer> cursor = new HashMap<>();

        int applied = 0;
        for (PigletEarTagItem item : submitted) {
            List<PigPigletno> pool = bySex.get(item.getPigletSex());
            int idx = cursor.merge(item.getPigletSex(), 1, Integer::sum) - 1;
            if (pool == null || idx >= pool.size()) {
                continue;
            }
            BigDecimal bw = normalizeBirthWeight(item.getBirthWeight());
            if (bw == null) {
                continue;
            }
            PigPigletno row = pool.get(idx);
            PigPigletno logUpdate = new PigPigletno();
            logUpdate.setId(row.getId());
            logUpdate.setBirthWeight(bw);
            pigletnoMapper.updateById(logUpdate);
            if (row.getPigId() != null) {
                Pig pigUpdate = new Pig();
                pigUpdate.setId(row.getPigId());
                pigUpdate.setBirthWeight(bw);
                pigMapper.updateById(pigUpdate);
            }
            applied++;
        }
        if (applied < submitted.size()) {
            log.warn("[V6-R242-compat] farrowId={} 提交 {} 头、已建档 {} 头，只订正了前 {} 头",
                farrow.getId(), submitted.size(), existing.size(), applied);
        } else {
            log.info("[V6-R242-compat] farrowId={} 旧客户端提交，已建档窝降级为按性别顺序订正出生重 {} 头",
                farrow.getId(), applied);
        }
        syncFarrowWeights(farrow.getId());
        return statByFarrow(farrow.getId()).getTaggedList();
    }

    /**
     * 建档整窝仔猪：分配耳号 → INSERT N 行 {@code t_farm_pig_info} + N 行 {@code t_farm_pig_pigletno}
     * → 母猪所在栏在场头数 +N → 回写该窝 total_weight / avg_weight。
     *
     * <p>两个入口共用：mp 旧「批量贴耳标」({@link #batchTag}) 与 V6 行242 的分娩录入自动建档
     * ({@link #autoCreatePigletsForFarrow})——同一段落库逻辑只此一份。</p>
     *
     * <p>仔猪<b>不参与状态机</b>（无 fireEvent），current_status 留空（ADR-0016）。</p>
     */
    private List<PigletEarTagVo> createPigletsForFarrow(PigFarrow farrow, List<PigletEarTagItem> piglets,
                                                        Long operatorId) {
        Long farrowId = farrow.getId();

        // 1. 校验母猪存在 + 已建档 + 本批 ≤ live_born
        Pig mother = pigMapper.selectById(farrow.getPigId());
        if (mother == null) {
            throw new ServiceException(I18nMessages.t("pigletno.mother.not_found", String.valueOf(farrow.getPigId())));
        }

        long tagged = Optional.ofNullable(pigletnoMapper.selectCount(
            new LambdaQueryWrapper<PigPigletno>().eq(PigPigletno::getFarrowId, farrowId))).orElse(0L);
        int newCount = piglets.size();
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

        // 3. 生成 N 个耳号（仔代品系/品种码 + 每头性别 + 出生日 = 分娩日；公母混批共享当天全场连号，按原序回填）
        LocalDateTime tagAt = LocalDateTime.now();
        LocalDate birthDate = farrow.getFarrowDate() == null ? tagAt.toLocalDate() : farrow.getFarrowDate().toLocalDate();
        List<String> earNos = allocatePigletEarNos(cubStrainCode, cubBreedCode, piglets, birthDate);

        // 4. 同事务循环 INSERT pig + pigletno
        List<PigletEarTagVo> result = new ArrayList<>(newCount);
        for (int i = 0; i < newCount; i++) {
            PigletEarTagItem item = piglets.get(i);
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
            piglet.setCurrentStatus("");   // 仔猪空状态（非种母猪无繁殖状态，ADR-0016）
            piglet.setStatusStartedAt(tagAt);
            piglet.setFatherEar(fatherEar);
            piglet.setMotherEar(mother.getEarNo());
            piglet.setBirthDate(birthDate);
            // 出生重按头录入（PigletEarTagItem.birthWeight），同步落主表供详情展示
            piglet.setBirthWeight(normalizeBirthWeight(item.getBirthWeight()));
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
            log.setFarrowId(farrowId);
            log.setTagDate(tagAt);
            log.setPigletSex(item.getPigletSex());
            log.setBirthWeight(normalizeBirthWeight(item.getBirthWeight()));
            log.setPigId(piglet.getId());
            log.setRemark(item.getRemark());
            // 记录人员：优先取调用方传入的 operatorId（mp 旧打标页所选 / 分娩录入的录入人员），空则回落登录态
            log.setOperatorId(operatorId != null ? operatorId : LoginHelper.getUserId());
            log.setDelFlag("0");
            log.setDelUnique(0L);
            pigletnoMapper.insert(log);

            result.add(PigletEarTagVo.from(piglet, log));
        }

        // 仔猪落母猪所在栏 → 该栏在场头数 += 本批头数（FIX-BRD-PENCOUNT-001；断奶转栏 / 死淘时按
        // TRANSFER / 终止事件对应减回，两侧配对才不漂）。母猪只落栋舍未落栏时 penId 为 null → 空操作。
        penCountUpdater.increase(mother.getPenId(), newCount);

        // R159：耳标提交后同步分娩表该窝的产仔总重 total_weight + 平均出生重 avg_weight
        // （按本窝全部已标仔猪的出生重累计，含本批 + 历史批；同事务可见刚 INSERT 的行）。
        syncFarrowWeights(farrowId);

        log.info("[BRD-EVENT-003] createPiglets farrowId={} motherEar={} count={} earNos=[{}..{}]",
            farrowId, mother.getEarNo(), newCount,
            earNos.get(0), earNos.get(newCount - 1));

        // VO 列表按耳号 asc 返回，便于前端展示连续序号
        result.sort(Comparator.comparing(PigletEarTagVo::getPigletEarNo));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PigletEarTagVo> autoCreatePigletsForFarrow(PigFarrow farrow, Long operatorId) {
        if (farrow == null || farrow.getId() == null) {
            throw new ServiceException(I18nMessages.t("pigletno.farrow_id.required"));
        }
        List<PigletEarTagItem> piglets = buildDefaultPiglets(farrow);
        if (piglets.isEmpty()) {
            // 无活产仔（死胎/木乃伊全窝）→ 不建档也不报错，分娩记录照常成立
            log.info("[V6-R242] farrowId={} liveBorn={} 无活产仔，跳过自动建档",
                farrow.getId(), farrow.getLiveBorn());
            return List.of();
        }
        return createPigletsForFarrow(farrow, piglets, operatorId);
    }

    /**
     * 按窝派生「每头的性别 + 出生重」：公 {@code farrow.male_count} 头 + 母 {@code farrow.female_count} 头，
     * 每头出生重取字典默认值。
     *
     * <p>公母数落库时已由分娩录入的「健仔公+弱仔留养公 / 健仔母+弱仔留养母」派生
     * （{@code FarrowServiceImpl.recordFarrow}）。两者都为 0（老数据 / 调用方没下发细分）时
     * 整窝按公组铺 {@code live_born} 头 —— 与 mp 耳标页原预铺行的退化规则一致，
     * 保证有耳号可订正，性别后续可在猪只档案改。</p>
     */
    private List<PigletEarTagItem> buildDefaultPiglets(PigFarrow farrow) {
        int liveBorn = Optional.ofNullable(farrow.getLiveBorn()).orElse(0);
        if (liveBorn <= 0) {
            return List.of();
        }
        int male = Math.max(0, Optional.ofNullable(farrow.getMaleCount()).orElse(0));
        int female = Math.max(0, Optional.ofNullable(farrow.getFemaleCount()).orElse(0));
        if (male == 0 && female == 0) {
            male = liveBorn;
        } else if (male + female != liveBorn) {
            // live_born 才是「该建几头档案」的权威，公母只是性别拆分。拆分之和对不上时按 live_born 收敛，
            // 而不是让耳标域的 exceeds_live_born 把整条分娩记录一起否掉 —— 甲方要的是分娩录入一步走完，
            // 一个性别拆分不一致不该导致分娩事件根本记不上。放弃的是「严格拒绝不一致输入」。
            // 收敛规则与 mp 端 resyncRows 的退化规则一致：按比例缩，余数优先补公组（先公后母）。
            int total = male + female;
            int newMale = total == 0 ? liveBorn : (int) Math.round((double) male * liveBorn / total);
            newMale = Math.min(Math.max(newMale, 0), liveBorn);
            log.warn("[V6-R242] farrowId={} 公母拆分 {}+{} 与活产数 {} 对不上，按活产数收敛为 {}+{}",
                farrow.getId(), male, female, liveBorn, newMale, liveBorn - newMale);
            male = newMale;
            female = liveBorn - newMale;
        }
        BigDecimal birthWeight = defaultBirthWeight();
        List<PigletEarTagItem> piglets = new ArrayList<>(male + female);
        for (int i = 0; i < male; i++) {
            piglets.add(mkDefaultItem("M", birthWeight));
        }
        for (int i = 0; i < female; i++) {
            piglets.add(mkDefaultItem("F", birthWeight));
        }
        return piglets;
    }

    private PigletEarTagItem mkDefaultItem(String sex, BigDecimal birthWeight) {
        PigletEarTagItem item = new PigletEarTagItem();
        item.setPigletSex(sex);
        item.setBirthWeight(birthWeight);
        return item;
    }

    /**
     * 自动建档时每头仔猪的出生重：字典 {@code djs_piglet_default_weight} 的「仔猪出生重」当前值（kg）。
     *
     * <p><b>依据 D-0107</b>——分娩录入表单上没有出生重这一栏，但 {@code pig_info.birth_weight} 与
     * 窝级 {@code total_weight / avg_weight} 都要有初值；写字典默认值与现行耳号标记页的逐头预填值
     * 完全一致，随后由出生重订正页改准。留空的代价是窝均初生重先显示 0 再跳变，工人会以为数据丢了。</p>
     *
     * <p>字典未 seed / 值非数字 → 回落 {@link #DEFAULT_BIRTH_WEIGHT}（2kg）并 warn，不阻断分娩录入。</p>
     */
    /**
     * 出生重按 2 位小数归一 —— 两张表存同一个事实却精度不同：
     * {@code t_farm_pig_info.birth_weight} 是 DECIMAL(6,2)、{@code t_farm_pig_pigletno.birth_weight} 是 DECIMAL(8,3)。
     * 不归一就会出现工人输 1.555、猪只详情显示 1.56、而窝级总重按 1.555 汇总的错位
     * （窝级取的是 pigletno 那张）。统一取窄的那个精度，让「看到的」和「算出来的」永远一致。
     */
    private static BigDecimal normalizeBirthWeight(BigDecimal raw) {
        return raw == null ? null : raw.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal defaultBirthWeight() {
        String raw = dictService.getDictValue(DictTypeConstants.PIGLET_DEFAULT_WEIGHT, BIRTH_WEIGHT_DICT_LABEL);
        if (StringUtils.isBlank(raw)) {
            log.warn("[V6-R242] 字典 {} 无「{}」项，出生重回落默认 {}kg",
                DictTypeConstants.PIGLET_DEFAULT_WEIGHT, BIRTH_WEIGHT_DICT_LABEL, DEFAULT_BIRTH_WEIGHT);
            return DEFAULT_BIRTH_WEIGHT;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("[V6-R242] 字典 {} 的「{}」值 [{}] 不是数字，出生重回落默认 {}kg",
                DictTypeConstants.PIGLET_DEFAULT_WEIGHT, BIRTH_WEIGHT_DICT_LABEL, raw, DEFAULT_BIRTH_WEIGHT, e);
            return DEFAULT_BIRTH_WEIGHT;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FarrowEarTagStatVo ensureLitterCreated(Long farrowId) {
        if (farrowId == null) {
            throw new ServiceException(I18nMessages.t("pigletno.farrow_id.required"));
        }
        PigFarrow farrow = farrowMapper.selectById(farrowId);
        if (farrow == null) {
            throw new ServiceException(I18nMessages.t("pigletno.farrow.not_found", String.valueOf(farrowId)));
        }
        if (selectLitterPiglets(farrowId).isEmpty()) {
            // D-0110：老窝进来时就补建，否则订正页是一张没有耳号的空表单，工人无从下手
            autoCreatePigletsForFarrow(farrow, LoginHelper.getUserId());
        }
        return statByFarrow(farrowId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PigletEarTagVo> adjustBirthWeights(PigletBirthWeightBo bo) {
        if (bo == null || bo.getFarrowId() == null || bo.getItems() == null || bo.getItems().isEmpty()) {
            throw new ServiceException(I18nMessages.t("pigletno.items.required"));
        }
        Long farrowId = bo.getFarrowId();
        PigFarrow farrow = farrowMapper.selectById(farrowId);
        if (farrow == null) {
            throw new ServiceException(I18nMessages.t("pigletno.farrow.not_found", String.valueOf(farrowId)));
        }

        List<PigPigletno> litter = selectLitterPiglets(farrowId);
        if (litter.isEmpty()) {
            // D-0110：行242 上线前已分娩、从未建档的老窝 —— 按同一套规则补建整窝再订正，
            // 新老窝走同一条路径不写两套分支。补建后仍为空（live_born=0）则下面逐条匹配会报耳号不属于本窝。
            autoCreatePigletsForFarrow(farrow, LoginHelper.getUserId());
            litter = selectLitterPiglets(farrowId);
        }
        Map<String, PigPigletno> byEarNo = new HashMap<>(litter.size() * 2);
        for (PigPigletno row : litter) {
            byEarNo.put(row.getPigletEarNo(), row);
        }

        // 同一耳号在一次提交里出现两次：静默取最后一个等于把工人先填的那个值悄悄扔掉，
        // 他不会知道自己录重了。整单拒绝，让他自己看清楚改哪一头。
        Set<String> seen = new java.util.HashSet<>();
        for (PigletBirthWeightItem dup : bo.getItems()) {
            String e = StringUtils.trim(dup.getPigletEarNo());
            if (!seen.add(e)) {
                throw new ServiceException(
                    I18nMessages.t("pigletno.ear_no.duplicated", e), 400);
            }
        }

        for (PigletBirthWeightItem item : bo.getItems()) {
            String earNo = StringUtils.trim(item.getPigletEarNo());
            PigPigletno row = byEarNo.get(earNo);
            if (row == null) {
                // farrowId 必须转成字符串再进 MessageFormat —— 传 Long 会被按数字格式化成
                // 「2,101,987,292,334,813,185」这种带千分位的雪花 ID，直接显给工人看。
                throw new ServiceException(
                    I18nMessages.t("pigletno.ear_no.not_in_litter", earNo, String.valueOf(farrowId)), 400);
            }
            // 两张表存同一个事实（既有重复存储）：pigletno.birth_weight 是窝级总重/均重的汇总源，
            // pig_info.birth_weight 是猪只详情/追溯的展示源 —— 只改一边两处口径会对不上。
            PigPigletno logUpdate = new PigPigletno();
            logUpdate.setId(row.getId());
            BigDecimal bw = normalizeBirthWeight(item.getBirthWeight());
            logUpdate.setBirthWeight(bw);
            pigletnoMapper.updateById(logUpdate);
            if (row.getPigId() != null) {
                Pig pigUpdate = new Pig();
                pigUpdate.setId(row.getPigId());
                pigUpdate.setBirthWeight(bw);
                pigMapper.updateById(pigUpdate);
            }
        }

        // 窝级总重/均重按本窝全部仔猪重算（绝对赋值，故反复提交幂等）
        syncFarrowWeights(farrowId);
        log.info("[V6-R243] adjustBirthWeights farrowId={} 订正 {} 头 / 本窝共 {} 头",
            farrowId, bo.getItems().size(), litter.size());

        List<PigletEarTagVo> result = statByFarrow(farrowId).getTaggedList();
        result.sort(Comparator.comparing(PigletEarTagVo::getPigletEarNo));
        return result;
    }

    /** 本窝已建档仔猪（耳号 + 性别 + 当前出生重的权威行），按 id asc = 建档顺序。 */
    private List<PigPigletno> selectLitterPiglets(Long farrowId) {
        return pigletnoMapper.selectList(Wrappers.<PigPigletno>lambdaQuery()
            .eq(PigPigletno::getFarrowId, farrowId)
            .eq(PigPigletno::getDelFlag, "0")
            .orderByAsc(PigPigletno::getId));
    }

    /**
     * 回写分娩表该窝产仔总重 + 平均出生重（R159）：按本窝全部已标仔猪 {@code t_farm_pig_pigletno.birth_weight}
     * 累计 total_weight = Σ出生重，avg_weight = total_weight / 有出生重头数（无则 0）。
     * <p>仅 update 这两列（MP updateById 跳 null 字段），不动分娩表其余字段。</p>
     */
    private void syncFarrowWeights(Long farrowId) {
        List<PigPigletno> all = pigletnoMapper.selectList(
            Wrappers.<PigPigletno>lambdaQuery().eq(PigPigletno::getFarrowId, farrowId));
        BigDecimal total = BigDecimal.ZERO;
        int weighed = 0;
        for (PigPigletno p : all) {
            if (p.getBirthWeight() != null) {
                total = total.add(p.getBirthWeight());
                weighed++;
            }
        }
        PigFarrow update = new PigFarrow();
        update.setId(farrowId);
        update.setTotalWeight(total);
        update.setAvgWeight(weighed == 0
            ? BigDecimal.ZERO
            : total.divide(BigDecimal.valueOf(weighed), 2, java.math.RoundingMode.HALF_UP));
        farrowMapper.updateById(update);
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
            throw new ServiceException(I18nMessages.t("pigletno.farrow.not_found", String.valueOf(farrowId)));
        }
        Pig mother = pigMapper.selectById(farrow.getPigId());
        if (mother == null) {
            throw new ServiceException(I18nMessages.t("pigletno.mother.not_found", String.valueOf(farrow.getPigId())));
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
        // 耳号编入性别段：公母前缀不同（公 -1- / 母 -2-），但共享当天全场同一连号起点（与正式 batchTag 落库同口径）。
        // 公组占 nextSeq..nextSeq+male-1，母组接着占 nextSeq+male..nextSeq+male+female-1 —— 序号在全场连续不交叉。
        int male = Math.max(0, maleCount);
        int female = Math.max(0, femaleCount);
        // R47：序号按出生日全场递增（不分品系品种性别），预览与正式打标同口径，仅读不占号
        long nextSeq = earNoAllocator.nextSeqForDate(birthDate);
        String malePrefix = earNoAllocator.buildPrefix(cub[1], cub[0], "M", birthDate);
        String femalePrefix = earNoAllocator.buildPrefix(cub[1], cub[0], "F", birthDate);
        vo.setMaleEarNos(buildPreviewSeq(malePrefix, nextSeq, male));
        vo.setFemaleEarNos(buildPreviewSeq(femalePrefix, nextSeq + male, female));
        return vo;
    }

    /**
     * 预览拼号：{@code prefix-startSeq .. prefix-(startSeq+count-1)}（仅展示，不读 DB、不占号）。
     * 起点序号由调用方按「全场起点 + 已分配偏移」算好传入，保证公母两组在全场范围内连续不交叉。
     */
    private List<String> buildPreviewSeq(String prefix, long startSeq, int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        List<String> earNos = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            earNos.add(prefix + PREVIEW_SEG_SEP + String.format("%0" + PREVIEW_SEQ_WIDTH + "d", startSeq + i));
        }
        return earNos;
    }

    /**
     * 为一批仔猪分配耳号：用解析后的仔代品系/品种码 + 每头性别 + 出生日 = 分娩日。
     *
     * <p>耳号编入性别段（{@code 品系-品种2-性别1-yyMMdd6-序号3}），同批公母混合时公组/母组前缀因性别段不同而分裂。
     * 按原始 {@code piglets} 索引顺序为每头算各自 5 段前缀（公 {@code -1-} / 母 {@code -2-}），交 {@link
     * EarNoAllocator#allocateBatchByPrefixes} 在单锁内整批一次性分配——<b>公母两组共享当天全场同一连续序号空间</b>
     * （不会因两组各读 max 而撞号），返回与 {@code piglets} 等长、索引对齐的耳号列表（第 i 头耳号即 {@code result.get(i)}，
     * 性别段与 {@code piglets.get(i).getPigletSex()} 一致）。</p>
     */
    private List<String> allocatePigletEarNos(
        String cubStrainCode, String cubBreedCode, List<PigletEarTagItem> piglets, LocalDate birthDate) {
        if (piglets == null || piglets.isEmpty()) {
            return new ArrayList<>();
        }
        // 按原始索引顺序为每头算 5 段前缀（公 -1- / 母 -2-），保证与 piglets 索引一一对齐
        List<String> prefixes = new ArrayList<>(piglets.size());
        for (PigletEarTagItem item : piglets) {
            prefixes.add(earNoAllocator.buildPrefix(cubStrainCode, cubBreedCode, item.getPigletSex(), birthDate));
        }
        return earNoAllocator.allocateBatchByPrefixes(prefixes, birthDate);
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
