package org.dromara.djs.breed.event.intro.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigCreateBo;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.intro.domain.PigIntroduce;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBatchBo;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBo;
import org.dromara.djs.breed.event.intro.domain.vo.PigIntroResultVo;
import org.dromara.djs.breed.event.intro.domain.vo.PigIntroduceVo;
import org.dromara.djs.breed.event.intro.mapper.PigIntroduceMapper;
import org.dromara.djs.breed.event.intro.service.IPigIntroService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public PigIntroServiceImpl(PigIntroduceMapper introduceMapper,
                               IPigCoreService pigCoreService,
                               IBizCodeGenerator bizCodeGenerator,
                               SupplierMapper supplierMapper,
                               BarnMapper barnMapper,
                               PenMapper penMapper,
                               BizReferenceChecker bizReferenceChecker) {
        this.introduceMapper = introduceMapper;
        this.pigCoreService = pigCoreService;
        this.bizCodeGenerator = bizCodeGenerator;
        this.supplierMapper = supplierMapper;
        this.barnMapper = barnMapper;
        this.penMapper = penMapper;
        this.bizReferenceChecker = bizReferenceChecker;
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
        Barn barn = loadBarn(bo.getBarnId());

        // 单头分配 1 个 EAR_NO
        String earNo = generateEarNo(barn);
        Pig pig = createOnePig(bo, earNo, intro.getId());
        incrementPenCount(bo.getPenId(), 1);

        return buildResult(intro, List.of(pig));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigIntroResultVo introduceBatch(PigIntroBatchBo bo) {
        resolveBarnPenCodes(bo);
        int count = bo.getPigCount();
        validate(bo, count);

        log.info("[BRD-EVENT-001] {} batch={}", I18nMessages.t("intro.batch_started", count), count);
        PigIntroduce intro = persistIntroduce(bo, count, bo.getStartEarNo(), bo.getPigSex());
        Barn barn = loadBarn(bo.getBarnId());

        // 批量一次性分配 N 个连续耳号
        List<String> earNos = bizCodeGenerator.generateBatch(BizCodeType.EAR_NO, buildEarCtx(barn), count);

        List<Pig> pigs = new ArrayList<>(count);
        for (String earNo : earNos) {
            pigs.add(createOnePig(bo, earNo, intro.getId()));
        }
        incrementPenCount(bo.getPenId(), count);

        return buildResult(intro, pigs);
    }

    /**
     * mp 端用户输的是栋舍编码 / 栏位编码（admin 可见短码），转成主键 id 再走原逻辑。
     * <p>id 已传则跳过；id / code 都没传则抛错（JSR-303 不再强制 id 必填）。</p>
     * <p>pen_code 在所属 barn 下 unique，必须先 resolve barn 后再查 pen。</p>
     */
    private void resolveBarnPenCodes(PigIntroBo bo) {
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
     * 单头耳号生成（批量走 generateBatch，但单头也走 EAR_NO 编码生成器保持一致）。
     */
    private String generateEarNo(Barn barn) {
        return bizCodeGenerator.generate(BizCodeType.EAR_NO, buildEarCtx(barn));
    }

    /**
     * EAR_NO 占位符上下文：{@code farmCode} 默认 "01"（V1 单农场）/ {@code barnCode} 取 barn.barn_code 前 2 位。
     * BizCodeGeneratorImpl#resolveCtx2 会自动截断 / 补零到 2 位。
     */
    private Map<String, Object> buildEarCtx(Barn barn) {
        Map<String, Object> ctx = new HashMap<>(4);
        ctx.put("farmCode", "01");
        if (barn != null && StringUtils.isNotBlank(barn.getBarnCode())) {
            ctx.put("barnCode", barn.getBarnCode());
        }
        return ctx;
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
