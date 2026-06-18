package org.dromara.djs.breed.event.intro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.service.OssService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigCreateBo;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.EarNoAllocator;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.intro.domain.PigIntroduce;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBatchBo;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBo;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroInternalBo;
import org.dromara.djs.breed.event.intro.domain.query.PigIntroQuery;
import org.dromara.djs.breed.event.intro.domain.vo.IntroRecordVo;
import org.dromara.djs.breed.event.intro.domain.vo.PigIntroResultVo;
import org.dromara.djs.breed.event.intro.domain.vo.PigIntroduceVo;
import org.dromara.djs.breed.event.intro.mapper.PigIntroduceMapper;
import org.dromara.djs.breed.event.intro.service.IPigIntroService;
import org.dromara.djs.breed.event.transfer.domain.bo.TransferBo;
import org.dromara.djs.breed.event.transfer.service.ITransferService;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.supplier.domain.Supplier;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.common.validate.BizReferenceChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 引种业务 Service 实现（BRD-EVENT-001）。
 *
 * <p>校验顺序（前置校验在事务外抛错更友好，但本实现为简洁起见全部放进 {@code @Transactional}
 * — 校验抛错会自动回滚未发生的 INSERT，等价。任一阶段失败整批回滚）：</p>
 * <ol>
 *   <li>外部引种 supplier + proof 必填</li>
 *   <li>supplier 存在且 supplier_type='breed'</li>
 *   <li>barn / pen 存在 + pen.barn_id 对齐传入 barn_id</li>
 *   <li>pen.capacity - pen.current_count >= pigCount（capacity 为 NULL 时不限制）</li>
 *   <li>生成引种单号 INTRO_NO</li>
 *   <li>INSERT t_farm_pig_introduce</li>
 *   <li>循环 / 批量分配耳号 EAR_NO + pigCoreService.createPig 每头</li>
 *   <li>pen.current_count += pigCount（wrapper SQL setSql 原子加法）</li>
 * </ol>
 *
 * <p>启动时 {@code @PostConstruct} 注册 supplier 引用关系给 {@link BizReferenceChecker}，
 * 让 SYS-MD-003 SupplierServiceImpl 软删能反查阻断。</p>
 *
 * @author djs
 * @since BRD-EVENT-001
 */
@Slf4j
@Service
public class PigIntroServiceImpl implements IPigIntroService {

    private final PigIntroduceMapper introduceMapper;
    private final IPigCoreService pigCoreService;
    private final IBizCodeGenerator bizCodeGenerator;
    private final SupplierMapper supplierMapper;
    private final BarnMapper barnMapper;
    private final PenMapper penMapper;
    private final BizReferenceChecker bizReferenceChecker;
    private final PigMapper pigMapper;
    private final DictService dictService;
    private final EarNoAllocator earNoAllocator;
    private final OssService ossService;
    private final ITransferService transferService;

    public PigIntroServiceImpl(PigIntroduceMapper introduceMapper,
                               IPigCoreService pigCoreService,
                               IBizCodeGenerator bizCodeGenerator,
                               SupplierMapper supplierMapper,
                               BarnMapper barnMapper,
                               PenMapper penMapper,
                               BizReferenceChecker bizReferenceChecker,
                               PigMapper pigMapper,
                               DictService dictService,
                               EarNoAllocator earNoAllocator,
                               OssService ossService,
                               ITransferService transferService) {
        this.introduceMapper = introduceMapper;
        this.pigCoreService = pigCoreService;
        this.bizCodeGenerator = bizCodeGenerator;
        this.supplierMapper = supplierMapper;
        this.barnMapper = barnMapper;
        this.penMapper = penMapper;
        this.bizReferenceChecker = bizReferenceChecker;
        this.pigMapper = pigMapper;
        this.dictService = dictService;
        this.earNoAllocator = earNoAllocator;
        this.ossService = ossService;
        this.transferService = transferService;
    }

    @PostConstruct
    public void registerReferences() {
        bizReferenceChecker.register("t_md_supplier", "t_farm_pig_introduce", "supplier_id");
        bizReferenceChecker.register("t_md_supplier", "t_farm_pig_info", "supplier_id");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigIntroResultVo introduce(PigIntroBo bo) {
        resolveBarnPenCodes(bo);
        validate(bo, 1);

        PigIntroduce intro = persistIntroduce(bo, 1, null, bo.getPigSex());
        loadBarn(bo.getBarnId());

        // 单头分配 1 个 EAR_NO（ADR-0011：品系+品种+公母+出生日 当天级 max+1；单头 BO 无 startEarNo 字段，纯后端生成）
        String earNo = earNoAllocator.allocateOne(bo.getPigStrainCode(), bo.getPigBreedCode(), bo.getPigSex(), bo.getBirthDate());
        Pig pig = createOnePig(bo, earNo, intro.getId());
        incrementPenCount(bo.getPenId(), 1);

        return buildResult(intro, List.of(pig));
    }

    @Override
    public TableDataInfo<PigIntroduceVo> queryPage(PigIntroQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigIntroduce> wrapper = Wrappers.<PigIntroduce>lambdaQuery()
            .likeRight(StringUtils.isNotBlank(query.getIntroduceNo()), PigIntroduce::getIntroduceNo, query.getIntroduceNo())
            .eq(StringUtils.isNotBlank(query.getIntroduceType()), PigIntroduce::getIntroduceType, query.getIntroduceType())
            .eq(query.getSupplierId() != null, PigIntroduce::getSupplierId, query.getSupplierId())
            .eq(StringUtils.isNotBlank(query.getPigSex()), PigIntroduce::getPigSex, query.getPigSex())
            .ge(query.getBeginDate() != null, PigIntroduce::getIntroduceDate, query.getBeginDate())
            .le(query.getEndDate() != null, PigIntroduce::getIntroduceDate, query.getEndDate())
            .orderByDesc(PigIntroduce::getId);
        Page<PigIntroduceVo> page = introduceMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichSupplier(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 列表 enrich：供应商编码 + 名称替代裸 supplierId（内部引种 supplierId 为空时不装配）。
     * 收集去重 supplierId 一次性批查，避免 N+1。
     */
    private void enrichSupplier(List<PigIntroduceVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> supplierIds = rows.stream().map(PigIntroduceVo::getSupplierId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (supplierIds.isEmpty()) {
            return;
        }
        Map<Long, Supplier> suppliers = supplierMapper.selectByIds(supplierIds).stream()
            .filter(s -> s.getId() != null)
            .collect(Collectors.toMap(Supplier::getId, Function.identity(), (a, b) -> a));
        for (PigIntroduceVo vo : rows) {
            Supplier s = suppliers.get(vo.getSupplierId());
            if (s != null) {
                vo.setSupplierCode(s.getSupplierCode());
                vo.setSupplierName(s.getSupplierName());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigIntroResultVo introduceBatch(PigIntroBatchBo bo) {
        resolveBarnPenCodes(bo);
        int count = bo.getPigCount();
        validate(bo, count);

        log.info("[BRD-EVENT-001] {} batch={}", I18nMessages.t("intro.batch_started", count), count);
        PigIntroduce intro = persistIntroduce(bo, count, bo.getStartEarNo(), bo.getPigSex());
        loadBarn(bo.getBarnId());

        // 批量一次性分配 N 个连续耳号（ADR-0011：品系+品种+公母+出生日 当天级 max+1 连号）
        List<String> earNos = allocateEarNos(bo, count);

        List<Pig> pigs = new ArrayList<>(count);
        for (String earNo : earNos) {
            pigs.add(createOnePig(bo, earNo, intro.getId()));
        }
        incrementPenCount(bo.getPenId(), count);

        return buildResult(intro, pigs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigIntroResultVo introduceInternal(PigIntroInternalBo bo) {
        // 内部引种 = 留种：登记一头已存在的肥猪进引种台账，不新建猪 / 不动 pen.current_count。
        // 来源 fattening 肥猪由 internalIntroToReserve 按性别重定类型 + 初始种猪态（同事务）：
        // 母→sow/HB（后备母猪，可进配种选猪）/ 公→boar + 空状态（种公猪）；
        // 非 fattening / END 终态由 internalIntroToReserve 内部幂等跳过。
        Pig pig = pigMapper.selectById(bo.getPigId());
        if (pig == null) {
            throw new ServiceException(I18nMessages.t("pig.not_found", bo.getPigId()));
        }

        // R45（李婷）：内部引种新增必填「引入栋舍 / 引入栏位」，提交时把猪只转移到该目标位置并落转移记录。
        // 先把 mp 端传的 barnCode / penCode 解析成目标 id（barn 先于 pen，pen 在所属 barn 下 unique）。
        Barn targetBarn = barnMapper.selectOne(
            Wrappers.<Barn>lambdaQuery().eq(Barn::getBarnCode, bo.getBarnCode()).last("LIMIT 1"));
        if (targetBarn == null) {
            throw new ServiceException(I18nMessages.t("intro.barn_not_found", bo.getBarnCode()));
        }
        Pen targetPen = penMapper.selectOne(
            Wrappers.<Pen>lambdaQuery()
                .eq(Pen::getBarnId, targetBarn.getId())
                .eq(Pen::getPenCode, bo.getPenCode())
                .last("LIMIT 1"));
        if (targetPen == null) {
            throw new ServiceException(I18nMessages.t("intro.pen_not_found", bo.getPenCode()));
        }

        String introNo = bizCodeGenerator.generate(BizCodeType.INTRO_NO, Map.of());
        PigIntroduce intro = new PigIntroduce();
        intro.setIntroduceNo(introNo);
        intro.setIntroduceType("internal");
        intro.setIntroduceDate(Optional.ofNullable(bo.getIntroduceDate()).orElseGet(LocalDate::now));
        intro.setPigCount(1);
        intro.setStartEarNo(pig.getEarNo());
        intro.setPigBreedCode(pig.getPigBreedCode());
        intro.setPigStrainCode(pig.getPigStrainCode());
        intro.setPigSex(pig.getPigSex());
        // R45：引种台账记的是「引入后」的目标位置，而非猪当前位置。
        intro.setBarnId(targetBarn.getId());
        intro.setPenId(targetPen.getId());
        intro.setPigId(pig.getId());
        intro.setOperator(bo.getOperator());
        intro.setIntroduceWeight(bo.getIntroduceWeight());
        intro.setRemark(null);
        intro.setDelFlag("0");
        intro.setDelUnique(0L);
        introduceMapper.insert(intro);

        // FIX-INTRO-001 #1：fattening 来源猪触发 → HB（写 status_record + update current_status，同事务）
        pigCoreService.internalIntroToReserve(pig.getId());

        // R45（李婷）：同一事务内完成猪只转移并落转移记录（写 t_farm_pig_transfer + 更新 pig.barn_id/pen_id）。
        // 复用 ITransferService.recordTransfer：传目标 barn/pen id + code，转移人员=引种人员，事由=内部引种。
        TransferBo transferBo = new TransferBo();
        transferBo.setPigId(pig.getId());
        transferBo.setTransferDate(LocalDateTime.now());
        transferBo.setNewBarnId(targetBarn.getId());
        transferBo.setNewBarnCode(bo.getBarnCode());
        transferBo.setNewPenId(targetPen.getId());
        transferBo.setNewPenCode(bo.getPenCode());
        transferBo.setTransferReason("内部引种");
        transferBo.setOperator(bo.getOperator());
        transferService.recordTransfer(transferBo);

        // 重读取得最新 current_status（若已转 HB）+ 最新 barn/pen，让 buildResult 返回的 pigs[0] 状态/位置正确
        Pig refreshed = pigMapper.selectById(pig.getId());
        return buildResult(intro, List.of(refreshed != null ? refreshed : pig));
    }

    @Override
    public TableDataInfo<IntroRecordVo> queryAppletRecords(PigIntroQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigIntroduce> wrapper = Wrappers.<PigIntroduce>lambdaQuery()
            .likeRight(StringUtils.isNotBlank(query.getIntroduceNo()), PigIntroduce::getIntroduceNo, query.getIntroduceNo())
            .eq(StringUtils.isNotBlank(query.getIntroduceType()), PigIntroduce::getIntroduceType, query.getIntroduceType())
            .ge(query.getBeginDate() != null, PigIntroduce::getIntroduceDate, query.getBeginDate())
            .le(query.getEndDate() != null, PigIntroduce::getIntroduceDate, query.getEndDate())
            .orderByDesc(PigIntroduce::getIntroduceDate)
            .orderByDesc(PigIntroduce::getId);
        Page<PigIntroduce> page = introduceMapper.selectPage(pageQuery.build(), wrapper);

        // 内部引种记录的日龄取关联猪只 t_farm_pig.birth_date 算（601-6）：批量查避免 N+1。
        // 外部引种无单头 pigId（批量新建多猪），日龄置 null（前端 '-' 兜底）。
        Set<Long> pigIds = page.getRecords().stream()
            .map(PigIntroduce::getPigId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, LocalDate> birthDateById = pigIds.isEmpty()
            ? Map.of()
            : pigMapper.selectBatchIds(pigIds).stream()
                .filter(p -> p.getBirthDate() != null)
                .collect(Collectors.toMap(Pig::getId, Pig::getBirthDate, (a, b) -> a));

        // 邓博 2026-06-17 #22：品种/品系名优先取 t_farm_breed_info 主表（客户配的权威名 04=国寿黑），
        // 字典 djs_pig_breed(04=杜洛克)/djs_pig_strain 仅回落。批量预载一次防 N+1。
        Map<String, String> breedNameMap = pigCoreService.loadBreedStrainNameMap(1);
        Map<String, String> strainNameMap = pigCoreService.loadBreedStrainNameMap(2);
        List<IntroRecordVo> rows = new ArrayList<>(page.getRecords().size());
        for (PigIntroduce e : page.getRecords()) {
            IntroRecordVo vo = new IntroRecordVo();
            vo.setIntroduceNo(e.getIntroduceNo());
            vo.setIntroduceType(e.getIntroduceType());
            vo.setIntroduceTypeLabel("internal".equals(e.getIntroduceType()) ? "内部"
                : "external".equals(e.getIntroduceType()) ? "外部" : e.getIntroduceType());
            vo.setEarNo(e.getStartEarNo());
            vo.setPigCount(e.getPigCount());
            vo.setPigBreedLabel(translateBreed(breedNameMap, e.getPigBreedCode()));
            vo.setPigStrainLabel(translateStrain(strainNameMap, e.getPigStrainCode()));
            vo.setAgeDays(calcAgeDays(e.getPigId() != null ? birthDateById.get(e.getPigId()) : null));
            vo.setIntroduceDate(e.getIntroduceDate());
            vo.setOperator(e.getOperator());
            vo.setProofOssIds(e.getProofOssIds());
            vo.setProofImageUrls(resolveProofUrls(e.getProofOssIds()));
            rows.add(vo);
        }

        Page<IntroRecordVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(rows);
        return TableDataInfo.build(voPage);
    }

    /**
     * 凭证图 ossId（逗号分隔）→ 可访问 URL 列表（外部引种有；内部引种 / 无凭证返空 list）。
     * <p>mp {@code <image>} 无法携 Bearer token 取鉴权下载端点，故后端 JOIN sys_oss 预解析；
     * {@link OssService#selectUrlByIds} 返逗号拼接 URL 串，按 {@code ,} 拆成 list。</p>
     */
    private List<String> resolveProofUrls(String proofOssIds) {
        if (StringUtils.isBlank(proofOssIds)) {
            return List.of();
        }
        String urls = ossService.selectUrlByIds(proofOssIds);
        if (StringUtils.isBlank(urls)) {
            return List.of();
        }
        return Arrays.stream(urls.split(","))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
    }

    /** 品种名解析（#22）：t_farm_breed_info 主表优先 → djs_pig_breed 字典回落 → 原始 code 回落。 */
    private String translateBreed(Map<String, String> breedNameMap, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String name = breedNameMap.get(code);
        if (StringUtils.isNotBlank(name)) {
            return name;
        }
        String label = dictService.getDictLabel("djs_pig_breed", code);
        return StringUtils.isNotBlank(label) ? label : code;
    }

    /** 品系名解析（#22）：t_farm_breed_info 主表优先 → djs_pig_strain 字典回落 → 原始 code 回落（601-6）。 */
    private String translateStrain(Map<String, String> strainNameMap, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String name = strainNameMap.get(code);
        if (StringUtils.isNotBlank(name)) {
            return name;
        }
        String label = dictService.getDictLabel("djs_pig_strain", code);
        return StringUtils.isNotBlank(label) ? label : code;
    }

    /** 日龄（天）= NOW - birthDate；birthDate 为 null（外部引种 / 无出生日期）返 null（601-6）。 */
    private Integer calcAgeDays(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(birthDate, LocalDate.now());
    }

    /**
     * mp 端用户输的是栋舍编码 / 栏位编码 / 供应商编码（admin 可见短码），转成主键 id 再走原逻辑。
     * <p>id 已传则跳过；id 不传且 code 不传，由 validate 阶段统一报错（supplier 内部引种 null OK）。</p>
     * <p>pen_code 在所属 barn 下 unique，必须先 resolve barn 后再查 pen。</p>
     */
    private void resolveBarnPenCodes(PigIntroBo bo) {
        if (bo.getSupplierId() == null && StringUtils.isNotBlank(bo.getSupplierCode())) {
            Supplier sup = supplierMapper.selectOne(
                Wrappers.<Supplier>lambdaQuery().eq(Supplier::getSupplierCode, bo.getSupplierCode()).last("LIMIT 1"));
            if (sup == null) {
                throw new ServiceException(I18nMessages.t("intro.supplier_not_found", bo.getSupplierCode()));
            }
            // mp 端工人手输 supplierCode（无 breed 过滤 picker），在解析处即兜底校验类型，
            // 误填非 breed 供应商立即返工人可读的友好报错（携带其输入的编码），不是裸 500。
            if (!"breed".equals(sup.getSupplierType())) {
                throw new ServiceException(I18nMessages.t("intro.supplier_code_type_invalid", bo.getSupplierCode()));
            }
            bo.setSupplierId(sup.getId());
        }
        if (bo.getBarnId() == null) {
            if (StringUtils.isBlank(bo.getBarnCode())) {
                throw new ServiceException(I18nMessages.t("intro.barn.required"));
            }
            Barn barn = barnMapper.selectOne(
                Wrappers.<Barn>lambdaQuery().eq(Barn::getBarnCode, bo.getBarnCode()).last("LIMIT 1"));
            if (barn == null) {
                throw new ServiceException(I18nMessages.t("intro.barn_not_found", bo.getBarnCode()));
            }
            bo.setBarnId(barn.getId());
        }
        if (bo.getPenId() == null) {
            if (StringUtils.isBlank(bo.getPenCode())) {
                throw new ServiceException(I18nMessages.t("intro.pen.required"));
            }
            Pen pen = penMapper.selectOne(
                Wrappers.<Pen>lambdaQuery()
                    .eq(Pen::getBarnId, bo.getBarnId())
                    .eq(Pen::getPenCode, bo.getPenCode())
                    .last("LIMIT 1"));
            if (pen == null) {
                throw new ServiceException(I18nMessages.t("intro.pen_not_found", bo.getPenCode()));
            }
            bo.setPenId(pen.getId());
        }
    }

    /**
     * 通用前置校验。{@code count} = 单头 1 / 批量 ≥ 2。
     */
    private void validate(PigIntroBo bo, int count) {
        // 外部引种必填校验（注：JSR-303 已经做了 introduceType / introduceDate / barn / pen 的非空 + 模式校验）
        if ("external".equals(bo.getIntroduceType())) {
            if (bo.getSupplierId() == null) {
                throw new ServiceException(I18nMessages.t("intro.external.supplier_required"));
            }
            if (StringUtils.isBlank(bo.getProofOssIds())) {
                throw new ServiceException(I18nMessages.t("intro.external.proof_required"));
            }
        }

        // Supplier 存在 + 类型=breed
        if (bo.getSupplierId() != null) {
            Supplier sup = supplierMapper.selectById(bo.getSupplierId());
            if (sup == null) {
                throw new ServiceException(I18nMessages.t("intro.supplier_not_found", bo.getSupplierId()));
            }
            if (!"breed".equals(sup.getSupplierType())) {
                throw new ServiceException(I18nMessages.t("intro.supplier_type_invalid", bo.getSupplierId()));
            }
        }

        // Barn / Pen 存在 + 一致 + 容量
        Barn barn = barnMapper.selectById(bo.getBarnId());
        if (barn == null) {
            throw new ServiceException(I18nMessages.t("intro.barn_not_found", bo.getBarnId()));
        }
        Pen pen = penMapper.selectById(bo.getPenId());
        if (pen == null) {
            throw new ServiceException(I18nMessages.t("intro.pen_not_found", bo.getPenId()));
        }
        if (!bo.getBarnId().equals(pen.getBarnId())) {
            throw new ServiceException(I18nMessages.t("intro.pen_barn_mismatch", bo.getPenId(), bo.getBarnId()));
        }
        Integer capacity = pen.getCapacity();
        if (capacity != null && capacity > 0) {
            int current = Optional.ofNullable(pen.getCurrentCount()).orElse(0);
            int remaining = capacity - current;
            if (remaining < count) {
                throw new ServiceException(
                    I18nMessages.t("intro.pen_capacity_exceeded", bo.getPenId(), remaining, count));
            }
        }
    }

    /**
     * 写 t_farm_pig_introduce 一行（单头 / 批量共用）。
     */
    private PigIntroduce persistIntroduce(PigIntroBo bo, int count, String startEarNo, String pigSex) {
        String introNo = bizCodeGenerator.generate(BizCodeType.INTRO_NO, Map.of());

        PigIntroduce intro = new PigIntroduce();
        intro.setIntroduceNo(introNo);
        intro.setIntroduceType(bo.getIntroduceType());
        intro.setIntroduceDate(Optional.ofNullable(bo.getIntroduceDate()).orElseGet(LocalDate::now));
        intro.setSupplierId(bo.getSupplierId());
        intro.setPigCount(count);
        intro.setStartEarNo(startEarNo);
        intro.setPigBreedCode(bo.getPigBreedCode());
        intro.setPigStrainCode(bo.getPigStrainCode());
        intro.setPigSex(pigSex);
        intro.setProofOssIds(bo.getProofOssIds());
        intro.setBarnId(bo.getBarnId());
        intro.setPenId(bo.getPenId());
        intro.setOperator(bo.getOperator());
        intro.setIntroduceWeight(bo.getIntroduceWeight());
        intro.setRemark(bo.getRemark());
        intro.setDelFlag("0");
        intro.setDelUnique(0L);
        introduceMapper.insert(intro);
        return intro;
    }

    /**
     * 单头 createPig（pig_type 按 pigSex 推导：F→sow / M→boar）。
     */
    private Pig createOnePig(PigIntroBo bo, String earNo, Long introduceId) {
        PigCreateBo createBo = new PigCreateBo();
        createBo.setEarNo(earNo);
        createBo.setEarTag(earNo);
        createBo.setPigSex(bo.getPigSex());
        createBo.setPigType("F".equals(bo.getPigSex()) ? "sow" : "boar");
        createBo.setPigBreedCode(bo.getPigBreedCode());
        createBo.setPigStrainCode(bo.getPigStrainCode());
        createBo.setBirthDate(bo.getBirthDate());
        createBo.setIntroduceDate(Optional.ofNullable(bo.getIntroduceDate()).orElseGet(LocalDate::now));
        createBo.setIntroduceType(bo.getIntroduceType());
        createBo.setSupplierId(bo.getSupplierId());
        createBo.setBarnId(bo.getBarnId());
        createBo.setPenId(bo.getPenId());
        createBo.setRemark(bo.getRemark());
        return pigCoreService.createPig(createBo);
    }

    /**
     * 批量/单头分配 N 个耳号（ADR-0011）。
     *
     * <p>外部引种"用户可改首号"（601-5）：{@code bo} 为 {@link PigIntroBatchBo} 且用户填了合法 {@code startEarNo} 时，
     * 以用户首号为起点连号（首号 = startEarNo，后续 +1）；首号校带分隔符格式 + UNIQUE 探测，撞了报友好错。
     * 未填则走 allocator 当天级 max+1 后端生成。</p>
     */
    private List<String> allocateEarNos(PigIntroBo bo, int count) {
        String userStart = bo instanceof PigIntroBatchBo b ? b.getStartEarNo() : null;
        if (StringUtils.isNotBlank(userStart)) {
            return allocateFromUserStart(userStart, count);
        }
        return earNoAllocator.allocate(bo.getPigStrainCode(), bo.getPigBreedCode(), bo.getPigSex(), bo.getBirthDate(), count);
    }

    /**
     * 以用户给定首号为起点连号（首号校格式 + UNIQUE；后续 N-1 头序号 +1，逐头探测 UNIQUE 撞则报错）。
     * <p>前缀 = 末段 {@code -} 之前（品系-品种2-yyMMdd6，6/15 起不再编码性别），序号 = 末段；与 {@link EarNoAllocator}
     * 同口径只看末段，不按定长截位（根除旧位长不一致导致解析出 &gt;999 的自相矛盾）。格式校验须与 allocator 输出
     * 一致：{@code 品系{1,2}-品种2-yyMMdd6-序号3}（品系位宽 1~2 兼容历史 1 位 + 当前 2 位字典码）。</p>
     */
    private List<String> allocateFromUserStart(String startEarNo, int count) {
        if (!startEarNo.matches("^\\d{1,2}-\\d{2}-\\d{6}-\\d{3}$")) {
            throw new ServiceException(I18nMessages.t("intro.start_ear_no.pattern"));
        }
        int sepIdx = startEarNo.lastIndexOf('-');
        String prefix = startEarNo.substring(0, sepIdx);
        long startSeq;
        try {
            startSeq = Long.parseLong(startEarNo.substring(sepIdx + 1));
        } catch (NumberFormatException e) {
            throw new ServiceException(I18nMessages.t("intro.start_ear_no.pattern"));
        }
        // 甲方：用户填的数量编号不得小于后台当前最小可用号（防 FE 绕过 / 重号）。
        // R47：按出生日全场 max+1（不分品系品种）。出生日段 = 首号前缀末段 yyMMdd（与候选 earNo 同口径，
        // 直接取段字符串而非转 LocalDate，避免对用户输入做额外日期合法性约束）。
        String dateSeg = prefix.substring(prefix.lastIndexOf('-') + 1);
        long minSeq = earNoAllocator.nextSeqForDateSegment(dateSeg);
        if (startSeq < minSeq) {
            throw new ServiceException(I18nMessages.t("intro.start_ear_no.too_small", minSeq));
        }
        List<String> earNos = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // 序号补零须与 EarNoAllocator.SEQ_WIDTH(=3) 同口径，否则连号 earNo 与 allocator 自动生成的位宽不一致
            String earNo = prefix + "-" + String.format("%03d", startSeq + i);
            if (pigMapper.existsEarNo(earNo) != null) {
                throw new ServiceException(I18nMessages.t("intro.start_ear_no.duplicate", earNo));
            }
            earNos.add(earNo);
        }
        return earNos;
    }

    @Override
    public String previewNextEarNo(String strainCode, String breedCode, String pigSex, LocalDate birthDate) {
        // 预生成"下一个可用首耳号"返给前端预填，不落库；并发下提交时再由 allocator 锁 + UNIQUE 兜底重算
        LocalDate birth = Optional.ofNullable(birthDate).orElseGet(LocalDate::now);
        return earNoAllocator.allocateOne(strainCode, breedCode, pigSex, birth);
    }

    private Barn loadBarn(Long barnId) {
        Barn barn = barnMapper.selectById(barnId);
        if (barn == null) {
            throw new ServiceException(I18nMessages.t("intro.barn_not_found", barnId));
        }
        return barn;
    }

    /**
     * Pen.current_count 原子加 N（wrapper-only update，避免 read-modify-write race）。
     */
    private void incrementPenCount(Long penId, int delta) {
        penMapper.update(null,
            Wrappers.<Pen>update()
                .eq("id", penId)
                .setSql("current_count = COALESCE(current_count, 0) + " + delta));
    }

    /**
     * Result VO 装配（手工映射，与 PigCoreServiceImpl 的设计原则一致 — 避免 MapstructUtils 单测耦合）。
     */
    private PigIntroResultVo buildResult(PigIntroduce intro, List<Pig> pigs) {
        PigIntroResultVo result = new PigIntroResultVo();
        result.setIntroduce(toVo(intro));
        List<PigIntroResultVo.PigSummary> summaries = new ArrayList<>(pigs.size());
        for (Pig pig : pigs) {
            PigIntroResultVo.PigSummary s = new PigIntroResultVo.PigSummary();
            s.setId(pig.getId());
            s.setEarNo(pig.getEarNo());
            s.setEarTag(pig.getEarTag());
            s.setPigSex(pig.getPigSex());
            s.setPigType(pig.getPigType());
            s.setCurrentStatus(pig.getCurrentStatus());
            summaries.add(s);
        }
        result.setPigs(summaries);
        return result;
    }

    private PigIntroduceVo toVo(PigIntroduce e) {
        PigIntroduceVo v = new PigIntroduceVo();
        v.setId(e.getId());
        v.setIntroduceNo(e.getIntroduceNo());
        v.setIntroduceType(e.getIntroduceType());
        v.setIntroduceDate(e.getIntroduceDate());
        v.setSupplierId(e.getSupplierId());
        v.setPigCount(e.getPigCount());
        v.setStartEarNo(e.getStartEarNo());
        v.setPigBreedCode(e.getPigBreedCode());
        v.setPigStrainCode(e.getPigStrainCode());
        v.setPigSex(e.getPigSex());
        v.setProofOssIds(e.getProofOssIds());
        v.setBarnId(e.getBarnId());
        v.setPenId(e.getPenId());
        v.setRemark(e.getRemark());
        v.setCreateTime(e.getCreateTime());
        return v;
    }
}
