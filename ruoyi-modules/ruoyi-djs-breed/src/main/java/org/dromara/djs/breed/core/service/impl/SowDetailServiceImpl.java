package org.dromara.djs.breed.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.FarrowByParityVo;
import org.dromara.djs.breed.core.domain.vo.FarrowRecordMpVo;
import org.dromara.djs.breed.core.domain.vo.PedigreeVo;
import org.dromara.djs.breed.core.domain.vo.PigMedRecordMpVo;
import org.dromara.djs.breed.core.domain.vo.PigTransferMpVo;
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
import java.util.Optional;

/**
 * {@link ISowDetailService} 实现（BRD-FIX-MP-DETAIL-SPLIT-001 + DJS-FIX-6-14）。
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
    /** 字典：用药原因（用药记录 reason label）。 */
    private static final String DICT_MED_REASON = "djs_medicine_reason";
    /** 字典：用药方式（用药记录 way label）。 */
    private static final String DICT_MED_WAY = "djs_medicine_way";

    private final SowDetailAggMapper aggMapper;
    private final PigMapper pigMapper;
    private final DictService dictService;

    @Override
    public SowPerformanceMpVo querySowPerformance(Long pigId) {
        requirePig(pigId);

        SowPerformanceMpVo vo = new SowPerformanceMpVo();

        // 平均怀孕天数 = avg(分娩 − 配种)；无配对 → null
        vo.setAvgGestationDays(scale2(aggMapper.avgGestationDays(pigId)));

        // 断奶-配种天数 = avg(下次配种 − 上次断奶)；无配对 → null
        vo.setWeanToBreedDays(scale2(aggMapper.avgWeanToBreedDays(pigId)));

        // NPD 总天数（V1 粗口径）：今天 − 最近配种 / 分娩较晚者；均缺 → null
        vo.setNpdTotalDays(calcNpd(pigId));

        // 返空流总数（返情 + 流产 + 空怀累计次数）；无 → 0
        vo.setNullReturnTotal(nz(aggMapper.countNullReturn(pigId)));

        // 窝均产仔数；无分娩 → null
        vo.setAvgBornPerLitter(scale2(aggMapper.avgBornPerLitter(pigId)));

        // 窝均断奶数；无断奶 → null
        vo.setAvgWeanedPerLitter(scale2(aggMapper.avgWeanedPerLitter(pigId)));

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

    @Override
    public List<FarrowRecordMpVo> queryFarrowRecords(Long pigId) {
        requirePig(pigId);
        List<FarrowRecordMpVo> rows = aggMapper.farrowRecords(pigId);
        return rows != null ? rows : Collections.emptyList();
    }

    @Override
    public List<PigMedRecordMpVo> queryMedRecords(Long pigId) {
        requirePig(pigId);
        List<PigMedRecordMpVo> rows = aggMapper.medRecords(pigId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        // 原因 / 方式 code → 中文 label（mp 无字典翻译能力，service 翻好下发）
        for (PigMedRecordMpVo r : rows) {
            r.setReason(translateDict(DICT_MED_REASON, r.getReason()));
            r.setWay(translateDict(DICT_MED_WAY, r.getWay()));
        }
        return rows;
    }

    @Override
    public List<PigTransferMpVo> queryTransferRecords(Long pigId) {
        requirePig(pigId);
        List<PigTransferMpVo> rows = aggMapper.transferRecords(pigId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        // 旧栏位驻留区间（开始 / 结束时间）：
        // rows 已按 transfer_date 倒序。对第 i 条（本次转移 = 离开旧栏位）：
        //   结束时间(toDate) = 本次转移日期；
        //   开始时间(fromDate) = 上一次转移日期（时间上更早，即列表中第 i+1 条）；无前驱 → null。
        for (int i = 0; i < rows.size(); i++) {
            PigTransferMpVo cur = rows.get(i);
            cur.setToDate(cur.getTransferDate());
            cur.setFromDate(i + 1 < rows.size() ? rows.get(i + 1).getTransferDate() : null);
        }
        return rows;
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
        return translateDict(DICT_PIG_BREED, code);
    }

    /** 通用字典翻译；翻不到回落 code，code 空 → null。 */
    private String translateDict(String dictType, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String label = dictService.getDictLabel(dictType, code);
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
     * NPD 非生产天数（V1 粗口径）：今天 − 最近配种 / 分娩日期中较晚的那一天。
     * 两个基准均缺 → null。
     */
    private Integer calcNpd(Long pigId) {
        LocalDate today = LocalDate.now();
        LocalDate lastFarrow = parseDate(aggMapper.lastFarrowDate(pigId));
        LocalDate lastBreeding = parseDate(aggMapper.lastBreedingDate(pigId));

        LocalDate base = null;
        if (lastFarrow != null && lastBreeding != null) {
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
            log.warn("[DJS-FIX-6-14] bad date string from agg query: {}", s);
            return null;
        }
    }

    /** BigDecimal 保留 2 位（HALF_UP）；null 透传 null。 */
    private BigDecimal scale2(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : null;
    }

    private int nz(Integer v) {
        return Optional.ofNullable(v).orElse(0);
    }
}
