package org.dromara.djs.breed.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.FarrowByParityVo;
import org.dromara.djs.breed.core.domain.vo.PedigreeVo;
import org.dromara.djs.breed.core.domain.vo.SowPerformanceMpVo;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.mapper.SowDetailAggMapper;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.core.service.ISowDetailService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link ISowDetailService} 实现（BRD-FIX-MP-DETAIL-SPLIT-001）。
 *
 * <p>独立 service，{@code @RequiredArgsConstructor} 注入聚合 mapper + PigMapper + DictService，
 * **不动 {@code PigCoreServiceImpl} 的显式构造器**（避免改其构造签名连带改单测）。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-DETAIL-SPLIT-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SowDetailServiceImpl implements ISowDetailService {

    /** 字典：猪只品种（谱系卡品种 label）。 */
    private static final String DICT_PIG_BREED = "djs_pig_breed";

    private final SowDetailAggMapper aggMapper;
    private final PigMapper pigMapper;
    private final DictService dictService;

    @Override
    public SowPerformanceMpVo querySowPerformance(Long pigId) {
        Pig pig = requirePig(pigId);

        SowPerformanceMpVo vo = new SowPerformanceMpVo();

        int totalBorn = nz(aggMapper.sumTotalBorn(pigId));
        int healthy = nz(aggMapper.sumHealthy(pigId));
        int liveBorn = nz(aggMapper.sumLiveBorn(pigId));
        int weaned = nz(aggMapper.sumWeaned(pigId));

        // 总产仔：无分娩记录 → null（mp 显 —）
        vo.setTotalBorn(totalBorn > 0 ? totalBorn : null);

        // 健仔率 = 健仔 / 总产仔；分母 0 → null
        vo.setHealthyRate(totalBorn > 0
            ? BigDecimal.valueOf(healthy).divide(BigDecimal.valueOf(totalBorn), 4, RoundingMode.HALF_UP)
            : null);

        // 平均窝重：无 total_weight 数据列 → mapper 返 null（降级）
        BigDecimal avgWeight = aggMapper.avgLitterWeight(pigId);
        vo.setAvgLitterWeight(avgWeight != null
            ? avgWeight.setScale(2, RoundingMode.HALF_UP) : null);

        // 断奶成活率 = 累计断奶 / 累计活产；分母 0 → null
        vo.setWeanSurvivalRate(liveBorn > 0
            ? BigDecimal.valueOf(weaned).divide(BigDecimal.valueOf(liveBorn), 4, RoundingMode.HALF_UP)
            : null);

        // 当前胎次 = max(farrow.parity) 与 pig.parity 取大；均 0/缺 → null
        Integer maxParity = aggMapper.maxParity(pigId);
        int pigParity = nz(pig.getParity());
        int parity = Math.max(nz(maxParity), pigParity);
        vo.setCurrentParity(parity > 0 ? parity : null);

        // NPD（V1 粗口径）：今天 − 最近一次配种 / 分娩日期（取较近的那个，即天数较小者）；均缺 → null
        vo.setNpd(calcNpd(pigId));

        return vo;
    }

    @Override
    public List<FarrowByParityVo> queryFarrowByParity(Long pigId) {
        requirePig(pigId);
        List<FarrowByParityVo> rows = aggMapper.farrowByParity(pigId);
        return rows != null ? rows : Collections.emptyList();
    }

    @Override
    public PedigreeVo queryPedigree(Long pigId) {
        Pig pig = requirePig(pigId);
        PedigreeVo vo = new PedigreeVo();
        LocalDate today = LocalDate.now();

        // 母系：本猪 mother_ear → 反查母猪只
        Pig dam = findByEarNo(pig.getMotherEar());
        if (dam != null) {
            vo.setDamPigId(dam.getId());
            vo.setDamEarNo(dam.getEarNo());
            vo.setDamBreed(translateBreed(dam.getPigBreedCode()));
            vo.setDamAgeDays(calcAgeDays(dam, today));
            vo.setDamParity(nz(dam.getParity()) > 0 ? dam.getParity() : null);
        } else if (StringUtils.isNotBlank(pig.getMotherEar())) {
            // 关联猪不存在但耳号有值 → 至少回填耳号，其它字段降级 null
            vo.setDamEarNo(pig.getMotherEar());
        }

        // 父系：本猪 father_ear → 反查公猪只（无胎次）
        Pig sire = findByEarNo(pig.getFatherEar());
        if (sire != null) {
            vo.setSirePigId(sire.getId());
            vo.setSireEarNo(sire.getEarNo());
            vo.setSireBreed(translateBreed(sire.getPigBreedCode()));
            vo.setSireAgeDays(calcAgeDays(sire, today));
        } else if (StringUtils.isNotBlank(pig.getFatherEar())) {
            vo.setSireEarNo(pig.getFatherEar());
        }

        return vo;
    }

    // ----------------------------------------------------------------------

    private Pig requirePig(Long pigId) {
        if (pigId == null) {
            throw new ServiceException(I18nMessages.t("pig.id.required"));
        }
        Pig pig = pigMapper.selectById(pigId);
        if (pig == null) {
            throw new ServiceException(I18nMessages.t("pig.not_found", pigId));
        }
        return pig;
    }

    /** 耳号反查猪只（最近一条未软删）；耳号空 / 查不到 → null。 */
    private Pig findByEarNo(String earNo) {
        if (StringUtils.isBlank(earNo)) {
            return null;
        }
        return pigMapper.selectOne(new LambdaQueryWrapper<Pig>()
            .eq(Pig::getEarNo, earNo)
            .orderByDesc(Pig::getId)
            .last("LIMIT 1"));
    }

    /** 品种字典翻译；翻不到回落 code，code 空 → null。 */
    private String translateBreed(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String label = dictService.getDictLabel(DICT_PIG_BREED, code);
        return StringUtils.isNotBlank(label) ? label : code;
    }

    /** 日龄 = NOW − birthDate（缺 birthDate fallback introduceDate）；均空 → null。 */
    private Integer calcAgeDays(Pig p, LocalDate today) {
        LocalDate base = p.getBirthDate() != null ? p.getBirthDate() : p.getIntroduceDate();
        if (base == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(base, today);
        return (int) Math.max(days, 0L);
    }

    /**
     * NPD 非生产天数（V1 粗口径）：今天 − 最近配种 / 分娩日期中较近的那一天。
     * 两个基准均缺 → null。仅取近似（非严格 NPD = 周期天数 − 妊娠 − 哺乳，V1 不细算）。
     */
    private Integer calcNpd(Long pigId) {
        LocalDate today = LocalDate.now();
        LocalDate lastFarrow = parseDate(aggMapper.lastFarrowDate(pigId));
        LocalDate lastBreeding = parseDate(aggMapper.lastBreedingDate(pigId));

        LocalDate base = null;
        if (lastFarrow != null && lastBreeding != null) {
            // 取较近的（天数小者）→ 取两日期中较晚的
            base = lastFarrow.isAfter(lastBreeding) ? lastFarrow : lastBreeding;
        } else if (lastFarrow != null) {
            base = lastFarrow;
        } else if (lastBreeding != null) {
            base = lastBreeding;
        }
        if (base == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(base, today);
        return (int) Math.max(days, 0L);
    }

    private LocalDate parseDate(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            log.warn("[BRD-FIX-MP-DETAIL] bad date string from agg query: {}", s);
            return null;
        }
    }

    private int nz(Integer v) {
        return Optional.ofNullable(v).orElse(0);
    }
}
