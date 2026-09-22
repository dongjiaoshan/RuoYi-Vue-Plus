package org.dromara.djs.breed.event.weaning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.core.service.DictService;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.core.util.PigAgeUtil;
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;
import org.dromara.djs.breed.event.eartag.mapper.PigPigletnoMapper;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
import org.dromara.djs.breed.event.transfer.domain.bo.TransferBo;
import org.dromara.djs.breed.event.transfer.service.ITransferService;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.dromara.djs.breed.event.weaning.domain.PigWeaning;
import org.dromara.djs.breed.event.weaning.domain.PigWeaningDetail;
import org.dromara.djs.breed.event.weaning.domain.bo.WeaningBo;
import org.dromara.djs.breed.event.weaning.domain.bo.WeaningDetailBo;
import org.dromara.djs.breed.event.weaning.domain.query.WeaningQuery;
import org.dromara.djs.breed.event.weaning.domain.vo.PigWeaningDetailVo;
import org.dromara.djs.breed.event.weaning.domain.vo.PigWeaningVo;
import org.dromara.djs.breed.event.weaning.domain.vo.UnweanedLitterRowVo;
import org.dromara.djs.breed.event.weaning.domain.vo.UnweanedLitterVo;
import org.dromara.djs.breed.event.weaning.domain.vo.WeaningPigletVo;
import org.dromara.djs.breed.event.weaning.mapper.PigWeaningDetailMapper;
import org.dromara.djs.breed.event.weaning.mapper.PigWeaningMapper;
import org.dromara.djs.breed.event.weaning.service.IWeaningService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /**
     * 「这头母猪已经走出哺乳期了」—— 断奶事件不再推状态机的判据（(FM, WEAN) → DN 是 WEAN 唯一的合法流转）。
     *
     * <p>都是断奶之后才到得了的状态：断奶本身、以及断奶后再配种/流产/空怀/返情。停在这些状态的母猪
     * 仍可能有上一窝没断完的仔猪要补断，这时只落断奶记录、不再推她自己的状态。</p>
     *
     * <p>刻意**不包含** {@code HB}（后备，从没哺乳过）和 {@code YF}（已转育肥）：那种猪来断奶是数据错乱，
     * 必须让状态机的非法流转去拒，不能顺手放行。</p>
     */
    private static final Set<String> POST_LACTATION_STATUSES = Set.of(
        PigLifecycle.DN.name(), PigLifecycle.PZ.name(), PigLifecycle.LC.name(),
        PigLifecycle.KH.name(), PigLifecycle.FQ.name());

    private final PigWeaningMapper weaningMapper;
    private final PigWeaningDetailMapper weaningDetailMapper;
    private final PigMapper pigMapper;
    private final PigFarrowMapper farrowMapper;
    private final PigPigletnoMapper pigletnoMapper;
    private final IPigCoreService pigCoreService;
    private final ITransferService transferService;
    private final BarnMapper barnMapper;
    private final PenMapper penMapper;
    private final DictService dictService;

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

        // 关联分娩（FIX-BRD-MP-WEAN-FORM-001 K065/K079，决策 Y2(b)）：
        //   farrowId 给了 → 按 id 取并校验属于该母猪；
        //   farrowId 空（mp 反查不到 / 未传）→ 自动取该母猪最近一次分娩兜底，仍无才抛明确异常。
        PigFarrow farrow;
        if (bo.getFarrowId() != null) {
            farrow = farrowMapper.selectById(bo.getFarrowId());
            if (farrow == null) {
                throw new ServiceException(I18nMessages.t("weaning.farrow_not_found", bo.getFarrowId()));
            }
            if (!Objects.equals(farrow.getPigId(), pig.getId())) {
                throw new ServiceException(I18nMessages.t("weaning.farrow_pig_mismatch",
                    bo.getFarrowId(), bo.getPigId()));
            }
        } else {
            farrow = farrowMapper.selectOne(
                Wrappers.<PigFarrow>lambdaQuery()
                    .eq(PigFarrow::getPigId, pig.getId())
                    .orderByDesc(PigFarrow::getFarrowDate, PigFarrow::getId)
                    .last("LIMIT 1"));
            if (farrow == null) {
                throw new ServiceException(I18nMessages.t("weaning.no_farrow_for_pig", pig.getEarNo()), 400);
            }
            bo.setFarrowId(farrow.getId());
        }
        // 本窝既有断奶记录（BRD-WEAN-SELECT-001）：断奶从「整窝一起断」改成「按所选仔猪断」后，
        // 同一窝会分多次断完，于是 ① 头数守恒要按「历史累计 + 本次」判 ② 状态机只在第一次推进。
        List<PigWeaning> priorWeanings = weaningMapper.selectList(
            Wrappers.<PigWeaning>lambdaQuery().eq(PigWeaning::getFarrowId, farrow.getId()));

        int lactationDeath = bo.getLactationDeathCount() == null ? 0 : bo.getLactationDeathCount();

        // 逐头去重（BRD-WEAN-SELECT-001）：同一头仔猪不能断两次。
        //   改造前 (DN, WEAN) 不在状态机 transition 表里，第二次提交被非法流转挡住，顺带也挡住了重复断头；
        //   分批补断要求跳过状态机之后这道天然屏障没了。重复断一头会多一行明细、覆盖个体断奶重，
        //   还会吃掉头数守恒的预算把整窝永久卡死（幽灵头数占满 live_born，真实剩下的再也断不掉）。
        //   判据与待断奶列表共用 PigWeaningMapper.ALREADY_WEANED，两边不会各自漂移。
        Set<String> submitEarNos = bo.getDetails() == null ? Set.of() : bo.getDetails().stream()
            .map(WeaningDetailBo::getEarNo)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        // 同一笔里同一个耳号只许出现一次：submitEarNos 是 Set，重复的在那里被吞掉，
        // 但明细是按 bo.getDetails() 原样落库的 —— 不拦的话会多出一行明细、覆盖个体断奶重，
        // 而头数守恒只按去重后的集合计一头，两边对不上。
        if (bo.getDetails() != null) {
            List<String> named = bo.getDetails().stream().map(WeaningDetailBo::getEarNo)
                .filter(StringUtils::isNotBlank).toList();
            if (named.size() != submitEarNos.size()) {
                Set<String> seen = new HashSet<>();
                String repeated = named.stream().filter(e -> !seen.add(e)).findFirst().orElse("");
                throw new ServiceException(I18nMessages.t("weaning.piglet_duplicated", repeated), 400);
            }
        }

        if (!submitEarNos.isEmpty()) {
            List<String> dup = weaningMapper.selectAlreadyWeanedEarNos(
                TenantHelper.getTenantId(), submitEarNos);
            if (dup != null && !dup.isEmpty()) {
                throw new ServiceException(I18nMessages.t("weaning.piglet_already_weaned",
                    String.join("、", dup)), 400);
            }
        }

        // 断奶数 + 哺乳期死淘数 不得超过该窝活产仔数（D-0065）。
        //   开了寄养之后（D-0113）不能再拿 weanedCount 去撞 —— 那个数含寄养进来的头，而寄养头属于生母那一窝，
        //   不吃养母窝 live_born 的预算，照撞会把正常的寄养断奶拦死。所以**只用本窝自己的头数**去撞。
        //   「本窝历史已断了几头」有两把尺子，都不能单用，取二者较大值：
        //     · 按明细上记死的出生窝数（`birth_farrow_id`）—— 编造耳号、匿名行都算得到，但迁移前的历史行是 NULL；
        //     · 按逐头判（`ALREADY_WEANED`）—— 盖得住迁移前的历史行、也盖得住被寄养走断在别人名下的本窝仔猪，
        //       但它查的是 pigletno，那头猪死亡后行被软删就数不到了，于是「断完再死」会凭空释放名额。
        //   两把尺子各有一段盲区，取 max 才封得住（独立验收实测：单用后者能把 15 头、17 头记进 live_born=9 的窝）。
        List<String> litterEarNos = pigletnoMapper.selectList(
                Wrappers.<PigPigletno>lambdaQuery().eq(PigPigletno::getFarrowId, farrow.getId()))
            .stream().map(PigPigletno::getPigletEarNo).filter(StringUtils::isNotBlank).toList();
        // 本次提交里每个耳号的出生窝（D-0113）：查不到的（匿名行 / 库里没有这个耳号）都记为本窝。
        Map<String, Long> birthFarrowByEarNo = submitEarNos.isEmpty() ? Map.of()
            : pigletnoMapper.selectList(Wrappers.<PigPigletno>lambdaQuery()
                    .in(PigPigletno::getPigletEarNo, submitEarNos))
                .stream()
                .filter(r -> StringUtils.isNotBlank(r.getPigletEarNo()) && r.getFarrowId() != null)
                .collect(Collectors.toMap(PigPigletno::getPigletEarNo, PigPigletno::getFarrowId, (a, b) -> a));
        // 本次落在本窝头上的头数 = 明细里「出生窝 = 本窝」的耳号数 + 没带耳号的那几行（匿名占位只可能是本窝自己的）。
        // 🔴 归属判定必须与 insertDetails 写进 birth_farrow_id 的那一套**逐字一致**：认不出出生窝的
        // （库里根本没有这个耳号）一律算本窝。不一致的后果实测过 —— 按「在不在本窝 pigletno 里」判的话，
        // 30 个编造耳号会被算成 0 本窝头，一发就给 live_born=9 的窝录出 30 头断奶。
        int namedOwn = (int) submitEarNos.stream()
            .filter(e -> Objects.equals(birthFarrowByEarNo.getOrDefault(e, farrow.getId()), farrow.getId()))
            .count();
        int weanedCount = bo.getWeanedCount() == null ? 0 : bo.getWeanedCount();
        // 匿名行直接数明细，不拿 weanedCount 去减 —— 减出来的数在「明细行数与 weanedCount 对不上」时会失真
        // （独立验收实测：12 条匿名明细配 weanedCount=1，能把 12 头记进 live_born=9 的窝）。
        // 整笔没有明细（admin 汇总录入）才退回 weanedCount。
        int anonymousOwn = bo.getDetails() == null ? 0
            : (int) bo.getDetails().stream().filter(d -> StringUtils.isBlank(d.getEarNo())).count();
        int ownThisTime = (bo.getDetails() == null || bo.getDetails().isEmpty())
            ? weanedCount
            : namedOwn + anonymousOwn;
        // 尺子一：历史明细上记死的出生窝。birth_farrow_id 为空 = 迁移前的历史行（那时还没有寄养），按本窝算。
        // 那一批压根没落逐头明细（admin 汇总录入）→ 只能整笔算本窝。
        List<Long> priorIds = priorWeanings.stream().map(PigWeaning::getId)
            .filter(Objects::nonNull).toList();
        Map<Long, List<PigWeaningDetail>> priorDetails = priorIds.isEmpty() ? Map.of()
            : weaningDetailMapper.selectList(Wrappers.<PigWeaningDetail>lambdaQuery()
                    .in(PigWeaningDetail::getWeaningId, priorIds))
                .stream().collect(Collectors.groupingBy(PigWeaningDetail::getWeaningId));
        int recordedOwnBefore = 0;
        for (PigWeaning prior : priorWeanings) {
            List<PigWeaningDetail> rows = priorDetails.get(prior.getId());
            if (rows == null || rows.isEmpty()) {
                recordedOwnBefore += prior.getWeanedCount() == null ? 0 : prior.getWeanedCount();
                continue;
            }
            recordedOwnBefore += (int) rows.stream()
                .filter(r -> r.getBirthFarrowId() == null
                    || Objects.equals(r.getBirthFarrowId(), farrow.getId()))
                .count();
        }
        // 尺子二：逐头判。只有贴标窝有逐头身份，未贴标窝这把尺子恒为 0，用不上。
        int perPigletOwnBefore = litterEarNos.isEmpty() ? 0
            : weaningMapper.selectAlreadyWeanedEarNos(TenantHelper.getTenantId(), litterEarNos).size();
        int ownBefore = Math.max(recordedOwnBefore, perPigletOwnBefore);
        int litterUsed = ownBefore + ownThisTime
            + sumInt(priorWeanings, PigWeaning::getLactationDeathCount) + lactationDeath;
        if (farrow.getLiveBorn() != null && litterUsed > farrow.getLiveBorn()) {
            throw new ServiceException(I18nMessages.t("weaning.count_exceeds_live_born",
                litterUsed, farrow.getLiveBorn()), 400);
        }

        // 1. 写 t_farm_pig_weaning
        PigWeaning entity = new PigWeaning();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setFarrowId(bo.getFarrowId());
        entity.setBreedingId(farrow.getBreedingId());
        entity.setWeaningDate(bo.getWeaningDate());
        entity.setWeanedCount(bo.getWeanedCount());
        entity.setLactationDeathCount(lactationDeath);
        entity.setWeanedWeight(bo.getWeanedWeight());
        entity.setAvgWeanedWeight(resolveAvg(bo));
        entity.setRemark(bo.getRemark());
        entity.setOperatorId(bo.getOperatorId() != null ? bo.getOperatorId() : LoginHelper.getUserId());
        entity.setDelFlag("0");
        LocalDate _evt = entity.getWeaningDate() != null ? entity.getWeaningDate().toLocalDate() : null;
        entity.setAgeDays(PigAgeUtil.ageDaysAt(pig, _evt));
        weaningMapper.insert(entity);

        // 2. 逐头录重明细（BRD-FIX-MP-EVENT-BREED-IA-001）：同事务批量 INSERT；details 空 → 退化仅汇总
        List<PigWeaningDetail> savedDetails = insertDetails(entity.getId(), pig.getEarNo(),
            bo.getDetails(), farrow.getId(), birthFarrowByEarNo);

        // 3. 触发状态机 FM → DN。母猪断的是它自己，**一次哺乳只推一次**：已经是 DN 就跳过 ——
        //    (DN, WEAN) 不在 transition 表里，再推会直接抛非法流转。下次分娩她会重新变回 FM，那时再推。
        //    🔴 判据是「**她是不是已经走出哺乳期了**」，不是「本窝有没有断奶记录」也不是「她是不是 DN」。
        //    (FM, WEAN) → DN 是 WEAN 唯一的合法流转，已经走出哺乳期的母猪再推必撞非法流转。
        //    只认「本窝有断奶记录 且 已 DN」挡不住 ① 多窝母猪先断新窝转 DN、再回头断老窝（priorWeanings(老窝) 为空）
        //    ② 断奶记录被软删；只认「已 DN」又挡不住 ③ 断过一次后被配种转成 PZ（或流产/空怀/返情），
        //    而她上一窝还剩仔猪没断 —— 三条在页面上是同一个症状：明明列在待断奶里、点进去提交必 400，
        //    还要等到她下一胎分娩回 FM 才解锁（≥114 天）。三条都是独立验收实测复现出来的。
        //    ⚠️ 反过来，**没跳过就必须让状态机去拒**：后备(HB)/育肥(YF) 这类压根没哺乳过的猪来断奶是数据错乱，
        //    那道非法流转就是唯一的防线，不能连它一起放掉。
        if (POST_LACTATION_STATUSES.contains(pig.getCurrentStatus())) {
            log.info("[BRD-WEAN-SELECT-001] 母猪当前 {} 已走出哺乳期，本次不推状态机 pigId={} farrowId={} priorWeanings={}",
                pig.getCurrentStatus(), pig.getId(), farrow.getId(), priorWeanings.size());
        } else {
            PigEventBo eventBo = new PigEventBo();
            eventBo.setPigId(pig.getId());
            eventBo.setEventType(PigStatusEvent.WEAN);
            eventBo.setRelatedEventId(entity.getId());
            eventBo.setEventAt(bo.getWeaningDate());
            pigCoreService.fireEvent(eventBo);
        }

        // 本次真正断掉的仔猪（BRD-WEAN-SELECT-001 行238 第6点）：明细带耳号 → 只取这几头；
        // 明细无耳号（未贴标窝按活产仔数铺匿名行 / admin 汇总录入）→ 退化为整窝，与改造前一致。
        // 没被选中的仔猪保持哺乳、留在原栏，日后可再断一次（D-0098 fallback）。
        Set<String> weanedEarNos = savedDetails.stream()
            .map(PigWeaningDetail::getEarNo)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toSet());
        List<PigPigletno> weanedPiglets = loadWeanedPiglets(farrow.getId(), weanedEarNos);

        // 4. 断奶后转移（FIX-WEAN-001 #32a 决策 a：断奶时内联填转移，一步到位）
        //    同事务复用 ITransferService 把母猪 + 本次断奶的仔猪转到目标 barn/pen（写转移历史 + 更新 pig 位置）。
        //    复用转移事件而非加断奶表列：转移历史 + pig 位置更新原子落地，无需 DDL。
        //    row22（客户 0618）：母猪与仔猪转移目标「独立」，可不同栋舍——反转既有决策 G4(a)「母猪仔猪同目标」。
        //    仔猪目标取 bo.pigletTransfer*，缺省回退母猪目标（向后兼容老调用方 / admin 端）。
        maybeTransferAfterWean(pig.getId(), weanedPiglets, bo);

        // 4b. D-0115：寄养把生母那一窝掏空了怎么办。
        //     生母自己名下一条断奶记录都没有、也再没有仔猪可断，会永久停在 FM ——
        //     FM 的非终态出口只有 (FM, WEAN) → DN，她断不了奶就转不出 FM，也就再也配不了种
        //     （BREED 的可入状态是 HB/DN/LC/KH/FQ，不含 FM），只剩死亡/淘汰/出栏。
        //     这是寄养这个新能力自带的死胡同，不是存量问题，所以在同一事务里替她走完那一步：
        //     走的是**既有的合法流转**，PigStateMachine 一个字不改。业务上也对 —— 她的哺乳确实结束了。
        closeLactationForEmptiedBirthSows(farrow.getId(), weanedPiglets, bo.getWeaningDate());

        // 5. 断奶即把本次断奶的已贴标仔猪翻成育肥猪（FIX-BRD-PIGTYPE-001，原型「仔猪断奶操作→生成育肥猪档案」）。
        //    断奶为主触发；转栏 newPigType 翻转保留作幂等兜底。仅改类型不动状态（最小改）。
        flipWeanedPigletsToFattening(farrow.getId(), weanedPiglets);

        // 6. 断奶称重 hook（BRD-STAT-FIX-001）：把逐头明细个体断奶重 + 断奶日回写到对应育肥猪 pig_info
        //    快照（wean_weight/wean_date），供出栏净增重 / 日增重溯源（育肥猪是断奶仔猪翻成的不同实体，
        //    出栏统计读自己的快照而非母猪断奶记录）。仅在有逐头明细时回写；同事务。
        if (!savedDetails.isEmpty()) {
            String tenantId = TenantHelper.getTenantId();
            int snap = pigMapper.updateWeanSnapshotByWeaningId(tenantId, entity.getId());
            log.info("[BRD-STAT-FIX-001] wean snapshot back-fill weaningId={} updatedFatteningPigs={}",
                entity.getId(), snap);
        }

        log.info("[BRD-EVENT-002] recordWeaning pigId={} earNo={} weaningId={} count={} detailRows={}",
            pig.getId(), pig.getEarNo(), entity.getId(), bo.getWeanedCount(), savedDetails.size());

        return toVo(entity, savedDetails);
    }

    /**
     * 断奶后内联转移母猪 + 该分娩已贴标仔猪（FIX-WEAN-001 #32a / FIX-BRD-MP-WEAN-FORM-001 K071）。
     * 与断奶主记录同事务，任一失败整体回滚。母猪转移目标二选一：{@code transferBarnCode}（mp）/
     * {@code transferBarnId}（admin）；仔猪转移目标独立：{@code pigletTransferBarnCode/pigletTransferBarnId}
     * （row22 客户 0618：母猪仔猪可转到不同栋舍，反转决策 G4(a)），仔猪目标缺省时回退母猪目标（向后兼容）。
     * 各自给了目标栋舍才触发对应转移；仔猪 = 本次断奶选中的那几头（已落 pig_id 的），逐头复用
     * {@link ITransferService}。没被选中的仔猪留在原栏，不跟着走。
     *
     * @param weanedPiglets 本次断奶涉及的已建档仔猪（见 {@link #loadLitterPiglets}）
     */
    private void maybeTransferAfterWean(Long sowPigId, List<PigPigletno> weanedPiglets, WeaningBo bo) {
        transferSow(sowPigId, bo);
        transferPiglets(weanedPiglets, bo);
    }

    /**
     * 母猪转移：给了母猪目标栋舍（{@code transferBarnId}/{@code transferBarnCode}）才触发，
     * 转到 {@code transferBarnCode/transferPenCode}（或 admin 端的 id 版）。
     */
    private void transferSow(Long sowPigId, WeaningBo bo) {
        boolean hasTarget = bo.getTransferBarnId() != null
            || (bo.getTransferBarnCode() != null && !bo.getTransferBarnCode().isBlank());
        if (!hasTarget) {
            return;
        }
        transferOne(sowPigId, bo.getTransferBarnId(), bo.getTransferBarnCode(),
            bo.getTransferPenId(), bo.getTransferPenCode(), bo.getWeaningDate());
        log.info("[FIX-WEAN-001] inline transfer sow after wean sowPigId={} → barnCode={} barnId={} penCode={} penId={}",
            sowPigId, bo.getTransferBarnCode(), bo.getTransferBarnId(), bo.getTransferPenCode(), bo.getTransferPenId());
    }

    /**
     * 仔猪转移（K071 + row22 独立目标）：取该分娩已贴标且已建 pig_info 行的仔猪，逐头转到
     * 仔猪专用目标 {@code pigletTransferBarnCode/pigletTransferPenCode}（或 id 版）；仔猪目标缺省
     * 时回退母猪目标 {@code transferBarnCode/transferPenCode}（向后兼容老调用方）。两者皆空则不转。
     * 转的是 {@code weanedPiglets}（本次断奶选中的那几头），不是整窝。
     */
    private void transferPiglets(List<PigPigletno> weanedPiglets, WeaningBo bo) {
        // 仔猪目标优先 piglet 专用；缺则回退母猪目标（向后兼容）
        boolean hasPigletTarget = bo.getPigletTransferBarnId() != null
            || (bo.getPigletTransferBarnCode() != null && !bo.getPigletTransferBarnCode().isBlank());
        Long barnId = hasPigletTarget ? bo.getPigletTransferBarnId() : bo.getTransferBarnId();
        String barnCode = hasPigletTarget ? bo.getPigletTransferBarnCode() : bo.getTransferBarnCode();
        Long penId = hasPigletTarget ? bo.getPigletTransferPenId() : bo.getTransferPenId();
        String penCode = hasPigletTarget ? bo.getPigletTransferPenCode() : bo.getTransferPenCode();

        boolean hasTarget = barnId != null || (barnCode != null && !barnCode.isBlank());
        if (!hasTarget) {
            return;
        }
        int transferred = 0;
        for (PigPigletno piglet : weanedPiglets) {
            if (piglet.getPigId() == null) {
                continue;
            }
            transferOne(piglet.getPigId(), barnId, barnCode, penId, penCode, bo.getWeaningDate());
            transferred++;
        }
        log.info("[row22] piglet transfer after wean pigletCount={} independentTarget={} → barnCode={} barnId={} penCode={} penId={}",
            transferred, hasPigletTarget, barnCode, barnId, penCode, penId);
    }

    /**
     * 寄养把生母那一窝掏空之后，替生母结束哺乳（D-0115）。
     *
     * <p>只处理本次断掉的仔猪里**属于别窝**的那些生母，且必须同时满足两条：她那一窝已经一头未断奶的仔猪都不剩
     * （判据复用 {@code UNWEANED_PIGLET_COUNT}，与待断奶列表同源），并且她自己还停在 {@code FM}。
     * 两条缺一就不动她 —— 还有仔猪在哺乳的母猪不该被提前推走，已经不是 FM 的母猪推了会撞非法流转。</p>
     *
     * <p>与断奶主记录同事务：养母这一笔断奶成立，生母的哺乳才算结束。</p>
     */
    private void closeLactationForEmptiedBirthSows(Long hostFarrowId, List<PigPigletno> weanedPiglets,
                                                   LocalDateTime weaningDate) {
        Set<Long> birthFarrowIds = weanedPiglets.stream()
            .map(PigPigletno::getFarrowId)
            .filter(Objects::nonNull)
            .filter(id -> !Objects.equals(id, hostFarrowId))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long birthFarrowId : birthFarrowIds) {
            Integer left = weaningMapper.countUnweanedPigletsInLitter(birthFarrowId);
            if (left != null && left > 0) {
                continue;
            }
            PigFarrow birthFarrow = farrowMapper.selectById(birthFarrowId);
            if (birthFarrow == null || birthFarrow.getPigId() == null) {
                continue;
            }
            Pig birthSow = pigMapper.selectById(birthFarrow.getPigId());
            if (birthSow == null || !PigLifecycle.FM.name().equals(birthSow.getCurrentStatus())) {
                continue;
            }
            PigEventBo eventBo = new PigEventBo();
            eventBo.setPigId(birthSow.getId());
            eventBo.setEventType(PigStatusEvent.WEAN);
            eventBo.setEventAt(weaningDate);
            try {
                pigCoreService.fireEvent(eventBo);
            } catch (RuntimeException e) {
                // 整笔回滚是对的（半截落地 = 养母断了、生母永久卡 FM，正是 D-0115 要消灭的东西），
                // 但异常本身说的是另一头母猪的事，不带耳号工人无从下手 —— 这里补上现场。
                throw new ServiceException(I18nMessages.t("weaning.birth_sow_close_failed",
                    birthSow.getEarNo(), e.getMessage()), 400);
            }
            log.info("[D-0115] 生母那一窝已被寄养断完，同事务替她结束哺乳 birthSowId={} birthFarrowId={} hostFarrowId={}",
                birthSow.getId(), birthFarrowId, hostFarrowId);
        }
    }

    /**
     * 断奶即把本次断奶的已贴标仔猪翻成育肥猪（FIX-BRD-PIGTYPE-001，原型「仔猪断奶操作→生成育肥猪档案」）。
     * <p>取 {@code weanedPiglets}（本次选中、已落 {@code pig_id} 的仔猪），一次性条件 update：
     * 仅 {@code pig_type='piglet'} 且非终止(END) 的翻成 {@code 'fattening'}（只改类型不动状态，最小改）。
     * 条件 update 幂等——重跑翻 0 行；与断奶主事务同生共死。尚未贴标的仔猪（pig_id 为空）不在此集合，
     * 按现有流程贴标后归 piglet，由后续断奶/存量回填覆盖。</p>
     */
    private void flipWeanedPigletsToFattening(Long farrowId, List<PigPigletno> weanedPiglets) {
        List<Long> pigletPigIds = weanedPiglets.stream()
            .map(PigPigletno::getPigId).filter(Objects::nonNull).toList();
        if (pigletPigIds.isEmpty()) {
            return;
        }
        int flipped = pigMapper.update(null,
            Wrappers.<Pig>lambdaUpdate()
                .in(Pig::getId, pigletPigIds)
                .eq(Pig::getPigType, "piglet")
                .ne(Pig::getCurrentStatus, PigLifecycle.END.name())
                .set(Pig::getPigType, "fattening"));
        log.info("[FIX-BRD-PIGTYPE-001] 断奶翻育肥 farrowId={} pigletCandidates={} flippedToFattening={}",
            farrowId, pigletPigIds.size(), flipped);
    }

    /** 把单头猪转移到指定目标 barn/pen（复用 ITransferService，断奶事务内联）。 */
    private void transferOne(Long pigId, Long barnId, String barnCode,
                             Long penId, String penCode, LocalDateTime transferDate) {
        TransferBo transfer = new TransferBo();
        transfer.setPigId(pigId);
        transfer.setTransferDate(transferDate);
        transfer.setNewBarnId(barnId);
        transfer.setNewBarnCode(barnCode);
        transfer.setNewPenId(penId);
        transfer.setNewPenCode(penCode);
        transfer.setTransferReason("weaning");
        transferService.recordTransfer(transfer);
    }

    /**
     * 逐头明细批量 INSERT（与主记录同事务）。details 为空时直接返空列表（向后兼容汇总录入）。
     * piglet_seq 缺省时按下发顺序从 1 补；tenant_id / 公共字段由 MP 自动填充。
     */
    private List<PigWeaningDetail> insertDetails(Long weaningId, String sowEarNo, List<WeaningDetailBo> details,
                                                 Long hostFarrowId, Map<String, Long> birthFarrowByEarNo) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        List<PigWeaningDetail> rows = new ArrayList<>(details.size());
        int seq = 1;
        for (WeaningDetailBo d : details) {
            PigWeaningDetail row = new PigWeaningDetail();
            row.setWeaningId(weaningId);
            row.setSowEarNo(sowEarNo);
            row.setPigletSeq(d.getPigletSeq() != null ? d.getPigletSeq() : seq);
            row.setEarNo(d.getEarNo());
            // D-0113：归属在提交这一刻定死。认不出出生窝的（匿名行 / 库里没这个耳号）一律算本窝 ——
            // 算本窝是保守的一侧：它会正常占用本窝的 live_born 名额，不会被当成寄养头白拿一个免费额度。
            row.setBirthFarrowId(StringUtils.isBlank(d.getEarNo())
                ? hostFarrowId
                : birthFarrowByEarNo.getOrDefault(d.getEarNo(), hostFarrowId));
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
        LocalDateTime beginAt = query.getBeginDate() != null ? query.getBeginDate().atStartOfDay() : null;
        LocalDateTime endBefore = query.getEndDate() != null ? query.getEndDate().plusDays(1).atStartOfDay() : null;
        LambdaQueryWrapper<PigWeaning> w = Wrappers.<PigWeaning>lambdaQuery()
            .eq(query.getPigId() != null, PigWeaning::getPigId, query.getPigId())
            .like(StringUtils.isNotBlank(query.getEarNo()), PigWeaning::getEarNo, query.getEarNo())
            .eq(query.getFarrowId() != null, PigWeaning::getFarrowId, query.getFarrowId())
            .ge(beginAt != null, PigWeaning::getWeaningDate, beginAt)
            .lt(endBefore != null, PigWeaning::getWeaningDate, endBefore)
            .orderByDesc(PigWeaning::getWeaningDate, PigWeaning::getId);
        Page<PigWeaningVo> page = weaningMapper.selectVoPage(pageQuery.build(), w);
        enrichRows(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 断奶记录列表 enrich（row24，mp 记录卡第 2/3 行）：母猪断奶时日龄/胎次/栋舍栏位、仔猪品系品种中文名。
     *
     * <p>批查去重 pigId / farrowId / barnId / penId 各一次避免 N+1；operatorName 由 VO 上
     * {@code @Translation} 按 operatorId 序列化时翻译，本方法不处理。</p>
     *
     * <p>口径（断奶表 {@code t_farm_pig_weaning} 无日龄/胎次/位置快照列，无法精确还原断奶当时值）：
     * 日龄 = weaningDate - 母猪 birth_date（按断奶日反算，历史快照口径）；胎次优先取关联分娩
     * {@code farrow.parity}（当时快照），缺分娩回退母猪当前 parity；栋舍栏位退化取母猪「当前」位置
     * （断奶后若已转移则与断奶当时不符）；仔猪品系品种 = 同窝母猪 pig_strain_code / pig_breed_code
     * 经 t_farm_breed_info 主表名解析（同窝仔猪品系一致），缺则字典回落。</p>
     */
    private void enrichRows(List<PigWeaningVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> pigIds = rows.stream().map(PigWeaningVo::getPigId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<Long> farrowIds = rows.stream().map(PigWeaningVo::getFarrowId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (pigIds.isEmpty()) {
            return;
        }
        Map<Long, Pig> pigById = pigMapper.selectByIds(pigIds).stream()
            .collect(Collectors.toMap(Pig::getId, Function.identity(), (a, b) -> a));
        Map<Long, PigFarrow> farrowById = farrowIds.isEmpty() ? Map.of()
            : farrowMapper.selectByIds(farrowIds).stream()
                .collect(Collectors.toMap(PigFarrow::getId, Function.identity(), (a, b) -> a));

        // 批查栋舍/栏位（母猪当前位置）
        Set<Long> barnIds = pigById.values().stream().map(Pig::getBarnId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<Long> penIds = pigById.values().stream().map(Pig::getPenId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, Barn> barnById = barnIds.isEmpty() ? Map.of()
            : barnMapper.selectBatchIds(barnIds).stream()
                .collect(Collectors.toMap(Barn::getId, Function.identity(), (a, b) -> a));
        Map<Long, Pen> penById = penIds.isEmpty() ? Map.of()
            : penMapper.selectBatchIds(penIds).stream()
                .collect(Collectors.toMap(Pen::getId, Function.identity(), (a, b) -> a));

        // 仔猪品系/品种取该窝已耳标仔猪真实 code（母×父 → 育种配置派生，与母猪可能不同），未耳标窝回落母猪 code。
        Map<Long, String[]> cubCodeByFarrow = loadCubStrainBreedByFarrow(farrowIds);
        // 品种/品系主数据 code→中文名（一次预载，避免逐头查 N+1；缺回落字典）
        Map<String, String> breedNameMap = pigCoreService.loadBreedStrainNameMap(1);
        Map<String, String> strainNameMap = pigCoreService.loadBreedStrainNameMap(2);

        LocalDate today = LocalDate.now();
        for (PigWeaningVo vo : rows) {
            Pig pig = vo.getPigId() == null ? null : pigById.get(vo.getPigId());
            if (pig == null) {
                continue;
            }
            // 日龄 = 断奶日期 - 母猪出生日期（缺出生日期 → null）
            LocalDate weanDay = vo.getWeaningDate() != null ? vo.getWeaningDate().toLocalDate() : today;
            if (pig.getBirthDate() != null) {
                vo.setAgeDaysAtWean((int) Math.max(ChronoUnit.DAYS.between(pig.getBirthDate(), weanDay), 0L));
            }
            // 胎次：优先关联分娩快照，缺则母猪当前 parity
            PigFarrow farrow = vo.getFarrowId() == null ? null : farrowById.get(vo.getFarrowId());
            vo.setParityAtWean(farrow != null && farrow.getParity() != null
                ? farrow.getParity() : pig.getParity());
            // 栋舍栏位（母猪当前位置，退化口径）
            if (pig.getBarnId() != null) {
                Barn barn = barnById.get(pig.getBarnId());
                if (barn != null) {
                    vo.setBarnName(barn.getBarnName());
                }
            }
            if (pig.getPenId() != null) {
                Pen pen = penById.get(pig.getPenId());
                if (pen != null) {
                    vo.setPenName(pen.getPenName());
                }
            }
            // 仔猪品系品种：优先窝内已耳标仔猪真实 code（母×父 → 育种配置派生），未耳标回落母猪 code。
            String[] cub = vo.getFarrowId() == null ? null : cubCodeByFarrow.get(vo.getFarrowId());
            String strainCode = cub != null && cub[0] != null ? cub[0] : pig.getPigStrainCode();
            String breedCode = cub != null && cub[1] != null ? cub[1] : pig.getPigBreedCode();
            vo.setPigStrainName(resolveBreedStrainName(strainNameMap, strainCode));
            vo.setPigBreedName(resolveBreedStrainName(breedNameMap, breedCode));
        }
    }

    /**
     * 按 farrowId 批量取该窝已耳标仔猪的真实品系/品种 code（同窝一致，取一头即可）。
     *
     * <p>仔猪 code 在耳标时按【母本 × 父本 → 育种配置表】派生（{@code PigEarTagServiceImpl.resolveCubBreedStrain}），
     * 与母猪 code 可能不同，故断奶记录卡「仔猪品系品种」须取仔猪真实 code、非母猪代理。
     * 走 {@code t_farm_pig_pigletno.farrow_id → pig_id → t_farm_pig_info.pig_strain_code/pig_breed_code}。
     * 未耳标窝（无 pigletno）不入表，由调用方回落母猪 code。</p>
     *
     * @return {@code farrowId → [strainCode, breedCode]}（任一维缺则该维为 null）
     */
    private Map<Long, String[]> loadCubStrainBreedByFarrow(Set<Long> farrowIds) {
        if (farrowIds == null || farrowIds.isEmpty()) {
            return Map.of();
        }
        List<PigPigletno> piglets = pigletnoMapper.selectList(Wrappers.<PigPigletno>lambdaQuery()
            .in(PigPigletno::getFarrowId, farrowIds)
            .isNotNull(PigPigletno::getPigId)
            .eq(PigPigletno::getDelFlag, "0"));
        if (piglets.isEmpty()) {
            return Map.of();
        }
        // farrowId → 该窝任一仔猪 pig_id（同窝品系品种一致，取第一头）
        Map<Long, Long> onePigletPigIdByFarrow = new java.util.HashMap<>();
        for (PigPigletno pn : piglets) {
            if (pn.getFarrowId() != null && pn.getPigId() != null) {
                onePigletPigIdByFarrow.putIfAbsent(pn.getFarrowId(), pn.getPigId());
            }
        }
        Set<Long> pigletPigIds = new java.util.HashSet<>(onePigletPigIdByFarrow.values());
        Map<Long, Pig> pigletById = pigletPigIds.isEmpty() ? Map.of()
            : pigMapper.selectByIds(pigletPigIds).stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(Pig::getId, Function.identity(), (a, b) -> a));
        Map<Long, String[]> result = new java.util.HashMap<>();
        onePigletPigIdByFarrow.forEach((farrowId, pigId) -> {
            Pig piglet = pigletById.get(pigId);
            if (piglet != null) {
                result.put(farrowId, new String[]{piglet.getPigStrainCode(), piglet.getPigBreedCode()});
            }
        });
        return result;
    }

    /**
     * 品种/品系 code→中文名解析：主数据权威源 = t_farm_breed_info（{@code infoNameMap}）；
     * 缺则回落字典（{@code dictType}）；再缺回落原始 code；code 空 → null。
     * 与 PigCoreServiceImpl.resolveBreedStrainName 同口径（#13 mp 选猪卡品系显中文不显码）。
     */
    private String resolveBreedStrainName(Map<String, String> infoNameMap, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String name = infoNameMap.get(code);
        if (StringUtils.isNotBlank(name)) {
            return name;
        }
        return code;
    }

    @Override
    public List<WeaningPigletVo> listPigletsByFarrow(Long farrowId) {
        if (farrowId == null) {
            return List.of();
        }
        List<PigPigletno> rows = pigletnoMapper.selectList(
            Wrappers.<PigPigletno>lambdaQuery()
                .eq(PigPigletno::getFarrowId, farrowId)
                .orderByAsc(PigPigletno::getPigletEarNo));
        // 已断奶与已终止（死亡 / 淘汰 / 出栏）的仔猪不再铺行 —— 行238 第4点：只留还在哺乳的那几头。
        // 🔴 判据必须走 selectAlreadyWeanedEarNos（不绑窝、三信号），不能只看「本窝那几条断奶记录的明细」：
        //    寄养走的仔猪跟着养母断，明细挂在养母那一窝（D-0113）。按本窝找会漏判，于是它在生母的录入页上
        //    照样铺出一行；整窝被寄养走时更糟 —— 母猪留在选猪列表、点进去铺满已断的行、提交必 400、
        //    叉光了又「无可断奶仔猪」，而 FM 不在配种的可入状态里，这头母猪就永久卡死了。
        Set<String> weaned = alreadyWeanedEarNos(rows);
        Set<Long> ended = endedPigIds(rows);
        List<WeaningPigletVo> vos = new ArrayList<>(rows.size());
        int seq = 1;
        for (PigPigletno r : rows) {
            if (weaned.contains(r.getPigletEarNo())) {
                continue;
            }
            if (r.getPigId() != null && ended.contains(r.getPigId())) {
                continue;
            }
            WeaningPigletVo vo = new WeaningPigletVo();
            vo.setPigletSeq(seq++);
            vo.setEarNo(r.getPigletEarNo());
            vo.setPigletSex(r.getPigletSex());
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public List<UnweanedLitterVo> listUnweanedLitters() {
        List<UnweanedLitterRowVo> rows = weaningMapper.selectUnweanedLitterRows(TenantHelper.getTenantId());
        if (rows.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        // SQL 已按 分娩日期倒序 + 仔猪耳号升序 排好，LinkedHashMap 保住这个顺序
        Map<Long, UnweanedLitterVo> byFarrow = new LinkedHashMap<>();
        for (UnweanedLitterRowVo r : rows) {
            UnweanedLitterVo litter = byFarrow.computeIfAbsent(r.getFarrowId(), id -> {
                UnweanedLitterVo v = new UnweanedLitterVo();
                v.setFarrowId(id);
                v.setSowPigId(r.getSowPigId());
                v.setSowEarNo(r.getSowEarNo());
                v.setFarrowDate(r.getFarrowDate());
                v.setParity(r.getParity());
                v.setSowAgeDays(PigAgeUtil.ageDaysAt(r.getSowBirthDate(), r.getSowIntroduceDate(), today));
                v.setBarnCode(r.getBarnCode());
                v.setBarnName(r.getBarnName());
                v.setPenCode(r.getPenCode());
                v.setPenName(r.getPenName());
                v.setMaleCount(0);
                v.setFemaleCount(0);
                v.setPiglets(new ArrayList<>());
                return v;
            });
            WeaningPigletVo piglet = new WeaningPigletVo();
            piglet.setPigletSeq(litter.getPiglets().size() + 1);
            piglet.setEarNo(r.getPigletEarNo());
            piglet.setPigletSex(r.getPigletSex());
            litter.getPiglets().add(piglet);
            if ("M".equals(r.getPigletSex())) {
                litter.setMaleCount(litter.getMaleCount() + 1);
            } else if ("F".equals(r.getPigletSex())) {
                litter.setFemaleCount(litter.getFemaleCount() + 1);
            }
        }
        List<UnweanedLitterVo> litters = new ArrayList<>(byFarrow.values());
        litters.forEach(v -> v.setTotalCount(v.getPiglets().size()));
        return litters;
    }

    /**
     * 本窝已断奶的仔猪耳号集合（BRD-WEAN-SELECT-001）。
     *
     * <p>分批断奶下同一 farrowId 可有多条断奶主记录，逐头是否断过只能按明细耳号判，
     * 不能按「本窝有没有断奶记录」整窝判。明细无耳号的行（匿名铺行）不参与判定。</p>
     */
    private Set<String> alreadyWeanedEarNos(List<PigPigletno> litter) {
        List<String> earNos = litter.stream()
            .map(PigPigletno::getPigletEarNo)
            .filter(StringUtils::isNotBlank)
            .toList();
        if (earNos.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(weaningMapper.selectAlreadyWeanedEarNos(TenantHelper.getTenantId(), earNos));
    }

    /** 这批打标行里已终止（死亡 / 淘汰 / 出栏）的仔猪 pig_id 集合；未建档案行（pig_id 空）不参与。 */
    private Set<Long> endedPigIds(List<PigPigletno> pigletnoRows) {
        Set<Long> pigIds = pigletnoRows.stream()
            .map(PigPigletno::getPigId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (pigIds.isEmpty()) {
            return Set.of();
        }
        return pigMapper.selectByIds(pigIds).stream()
            .filter(p -> PigLifecycle.END.name().equals(p.getCurrentStatus()))
            .map(Pig::getId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    /**
     * 本次断奶实际涉及的已建档仔猪（BRD-WEAN-SELECT-001 行238 第6点）。
     *
     * @param farrowId       关联分娩
     * @param weanedEarNos   本次明细里的仔猪耳号；空集 = 明细无耳号（未贴标窝匿名铺行 /
     *                       admin 汇总录入）→ 退化为整窝，与「整窝一起断」的老行为一致
     */
    private List<PigPigletno> loadWeanedPiglets(Long farrowId, Set<String> weanedEarNos) {
        if (weanedEarNos.isEmpty()) {
            // 明细无耳号（未贴标窝按活产仔数铺的匿名行 / admin 汇总录入）→ 退化为整窝
            return pigletnoMapper.selectList(
                Wrappers.<PigPigletno>lambdaQuery()
                    .eq(PigPigletno::getFarrowId, farrowId)
                    .isNotNull(PigPigletno::getPigId));
        }
        // D-0113 寄养：按耳号认猪，不按窝认。寄养进来的那几头档案挂在生母那一窝，按 farrowId 查会漏掉，
        // 结果是它们没被转栏、没翻成育肥猪，而断奶快照（updateWeanSnapshotByWeaningId 按耳号 join）
        // 却写进去了 —— 同一头猪一半断了一半没断，谁也说不清它在哪。
        return pigletnoMapper.selectList(
            Wrappers.<PigPigletno>lambdaQuery()
                .in(PigPigletno::getPigletEarNo, weanedEarNos)
                .isNotNull(PigPigletno::getPigId));
    }

    /** 对一组断奶记录按给定取数器求和（null 计 0）。 */
    private int sumInt(List<PigWeaning> rows, Function<PigWeaning, Integer> getter) {
        return rows.stream().map(getter).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
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
        v.setLactationDeathCount(e.getLactationDeathCount());
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
            vo.setSowEarNo(d.getSowEarNo());
            vo.setEarNo(d.getEarNo());
            vo.setWeight(d.getWeight());
            vo.setRemark(d.getRemark());
            vos.add(vo);
        }
        return vos;
    }
}
