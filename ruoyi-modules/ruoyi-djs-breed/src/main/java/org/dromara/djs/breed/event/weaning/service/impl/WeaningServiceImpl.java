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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        entity.setOperatorId(bo.getOperatorId() != null ? bo.getOperatorId() : LoginHelper.getUserId());
        entity.setDelFlag("0");
        LocalDate _evt = entity.getWeaningDate() != null ? entity.getWeaningDate().toLocalDate() : null;
        entity.setAgeDays(PigAgeUtil.ageDaysAt(pig, _evt));
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

        // 4. 断奶后转移（FIX-WEAN-001 #32a 决策 a：断奶时内联填转移，一步到位）
        //    同事务复用 ITransferService 把母猪 + 该分娩已贴标仔猪转到目标 barn/pen（写转移历史 + 更新 pig 位置）。
        //    复用转移事件而非加断奶表列：转移历史 + pig 位置更新原子落地，无需 DDL。
        //    row22（客户 0618）：母猪与仔猪转移目标「独立」，可不同栋舍——反转既有决策 G4(a)「母猪仔猪同目标」。
        //    仔猪目标取 bo.pigletTransfer*，缺省回退母猪目标（向后兼容老调用方 / admin 端）。
        maybeTransferAfterWean(pig.getId(), farrow.getId(), bo);

        // 5. 断奶即把该窝已贴标仔猪翻成育肥猪（FIX-BRD-PIGTYPE-001，原型「仔猪断奶操作→生成育肥猪档案」）。
        //    断奶为主触发；转栏 newPigType 翻转保留作幂等兜底。仅改类型不动状态（最小改）。
        flipWeanedPigletsToFattening(farrow.getId());

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
     * 各自给了目标栋舍才触发对应转移；仔猪取自 {@code t_farm_pig_pigletno} 中该分娩 farrowId 下已落
     * pig_id（已建 pig_info 行）的仔猪，逐头复用 {@link ITransferService}。
     */
    private void maybeTransferAfterWean(Long sowPigId, Long farrowId, WeaningBo bo) {
        transferSow(sowPigId, bo);
        transferPiglets(farrowId, bo);
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
     */
    private void transferPiglets(Long farrowId, WeaningBo bo) {
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
        List<PigPigletno> piglets = pigletnoMapper.selectList(
            Wrappers.<PigPigletno>lambdaQuery()
                .eq(PigPigletno::getFarrowId, farrowId)
                .isNotNull(PigPigletno::getPigId));
        int transferred = 0;
        for (PigPigletno piglet : piglets) {
            if (piglet.getPigId() == null) {
                continue;
            }
            transferOne(piglet.getPigId(), barnId, barnCode, penId, penCode, bo.getWeaningDate());
            transferred++;
        }
        log.info("[row22] piglet transfer after wean farrowId={} pigletCount={} independentTarget={} → barnCode={} barnId={} penCode={} penId={}",
            farrowId, transferred, hasPigletTarget, barnCode, barnId, penCode, penId);
    }

    /**
     * 断奶即把该窝已贴标仔猪翻成育肥猪（FIX-BRD-PIGTYPE-001，原型「仔猪断奶操作→生成育肥猪档案」）。
     * <p>取 {@code t_farm_pig_pigletno} 中该分娩已落 {@code pig_id} 的仔猪，一次性条件 update：
     * 仅 {@code pig_type='piglet'} 且非终止(END) 的翻成 {@code 'fattening'}（只改类型不动状态，最小改）。
     * 条件 update 幂等——重跑翻 0 行；与断奶主事务同生共死。尚未贴标的仔猪（pig_id 为空）不在此集合，
     * 按现有流程贴标后归 piglet，由后续断奶/存量回填覆盖。</p>
     */
    private void flipWeanedPigletsToFattening(Long farrowId) {
        List<Long> pigletPigIds = pigletnoMapper.selectList(
                Wrappers.<PigPigletno>lambdaQuery()
                    .eq(PigPigletno::getFarrowId, farrowId)
                    .isNotNull(PigPigletno::getPigId))
            .stream().map(PigPigletno::getPigId).filter(Objects::nonNull).toList();
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
        List<WeaningPigletVo> vos = new ArrayList<>(rows.size());
        int seq = 1;
        for (PigPigletno r : rows) {
            WeaningPigletVo vo = new WeaningPigletVo();
            vo.setPigletSeq(seq++);
            vo.setEarNo(r.getPigletEarNo());
            vo.setPigletSex(r.getPigletSex());
            vos.add(vo);
        }
        return vos;
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
