package org.dromara.djs.breed.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.FarrowByParityVo;
import org.dromara.djs.breed.core.domain.vo.FarrowRecordMpVo;
import org.dromara.djs.breed.core.domain.vo.PedigreeVo;
import org.dromara.djs.breed.core.domain.vo.PigMedRecordMpVo;
import org.dromara.djs.breed.core.domain.vo.PigTransferMpVo;
import org.dromara.djs.breed.core.domain.vo.SowPerformanceMpVo;
import org.dromara.djs.breed.breeding.domain.BreedInfo;
import org.dromara.djs.breed.breeding.mapper.BreedInfoMapper;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.mapper.SowDetailAggMapper;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.core.service.ISowDetailService;
import org.dromara.djs.breed.production.domain.SowPerformance;
import org.dromara.djs.breed.production.mapper.SowPerformanceMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    /** 字典：用药原因（用药记录 reason label）。 */
    private static final String DICT_MED_REASON = "djs_medicine_reason";
    /** 字典：用药方式（用药记录 way label）。 */
    private static final String DICT_MED_WAY = "djs_medicine_way";

    /** 默认租户/农场 ID（V1 全 '1001'，取不到 tenant 时兜底）。 */
    private static final String DEFAULT_TENANT = "1001";

    private final SowDetailAggMapper aggMapper;
    private final PigMapper pigMapper;
    private final DictService dictService;
    private final BreedInfoMapper breedInfoMapper;
    private final SowPerformanceMapper sowPerformanceMapper;

    @Override
    public SowPerformanceMpVo querySowPerformance(Long pigId) {
        requirePig(pigId);

        // 优先读 t_farm_sow_performance（定时任务填的 row11 12 项指标，含修过的 NPD）。
        SowPerformance row = sowPerformanceMapper.selectLatestByPigId(pigId, currentTenant());
        if (row != null) {
            return fromPerformanceRow(row);
        }

        // 行不存在（该母猪还没被定时任务覆盖到）→ fallback 当场实时聚合，保证不空白。
        return calcSowPerformanceOnTheFly(pigId);
    }

    /** t_farm_sow_performance 一行 → 出参 VO（字段直映；缺值 null 透传，mp 显 —）。 */
    private SowPerformanceMpVo fromPerformanceRow(SowPerformance row) {
        SowPerformanceMpVo vo = new SowPerformanceMpVo();
        vo.setAvgGestationDays(row.getAvgGestationDays());
        vo.setWeanBreedDays(row.getWeanBreedDays());
        vo.setNpd(row.getNpd());
        vo.setAbnormalTotal(row.getAbnormalTotal());
        vo.setAvgBornPerLitter(row.getAvgBornPerLitter());
        vo.setAvgLiveBornPerLitter(row.getAvgLiveBornPerLitter());
        vo.setAvgWeanedPerLitter(row.getAvgWeanedPerLitter());
        vo.setTotalBorn(row.getTotalBorn());
        vo.setTotalLiveBorn(row.getTotalLiveBorn());
        vo.setTotalWeaned(row.getTotalWeaned());
        vo.setAvgBornWeight(row.getAvgBornWeight());
        vo.setAvgWeanedWeight(row.getAvgWeanedWeight());
        return vo;
    }

    /**
     * fallback：从本母猪分娩 / 断奶 / 配种 / 异常记录当场实时聚合（V1 粗口径）。
     * 仅 t_farm_sow_performance 行不存在时走此路径；无对应源数据的指标保持 null → mp 显 —。
     */
    private SowPerformanceMpVo calcSowPerformanceOnTheFly(Long pigId) {
        SowPerformanceMpVo vo = new SowPerformanceMpVo();

        // 累计产仔 / 活仔 / 平均出生重 + 累计断奶 / 平均断奶重。
        // 与 admin fallback 共用本方法，避免两端在汇总表尚未生成时口径分叉。
        // MySQL 除法会多带 4 位小数（2.2100000），夜间汇总批存的是 scale3（2.210）；
        // 这里必须同样 scale3，否则同一指标「当天实时算」与「次日读汇总」显示不同。
        SowPerformanceMpVo farrowTotals = aggMapper.farrowProductionTotals(pigId);
        if (farrowTotals != null) {
            vo.setTotalBorn(farrowTotals.getTotalBorn());
            vo.setTotalLiveBorn(farrowTotals.getTotalLiveBorn());
            vo.setAvgLiveBornPerLitter(scale3(farrowTotals.getAvgLiveBornPerLitter()));
            vo.setAvgBornWeight(scale3(farrowTotals.getAvgBornWeight()));
        }
        SowPerformanceMpVo weanTotals = aggMapper.weanProductionTotals(pigId);
        if (weanTotals != null) {
            vo.setTotalWeaned(weanTotals.getTotalWeaned());
            vo.setAvgWeanedWeight(scale3(weanTotals.getAvgWeanedWeight()));
        }

        // 平均怀孕天数 = avg(分娩 − 配种)；无配对 → null
        vo.setAvgGestationDays(scale2(aggMapper.avgGestationDays(pigId)));

        // 断奶-配种天数 = avg(下次配种 − 上次断奶)；无配对 → null
        vo.setWeanBreedDays(scale2(aggMapper.avgWeanToBreedDays(pigId)));

        // NPD（V1 粗口径）：今天 − 最近配种 / 分娩较晚者；均缺 → null
        Integer npdDays = calcNpd(pigId);
        vo.setNpd(npdDays != null ? new BigDecimal(npdDays) : null);

        // 返空流总数（返情 + 流产 + 空怀累计次数）；无 → 0
        vo.setAbnormalTotal(nz(aggMapper.countNullReturn(pigId)));

        // 窝均产仔数；无分娩 → null
        vo.setAvgBornPerLitter(scale2(aggMapper.avgBornPerLitter(pigId)));

        // 窝均断奶数；无断奶 → null
        vo.setAvgWeanedPerLitter(scale2(aggMapper.avgWeanedPerLitter(pigId)));

        return vo;
    }

    /** 当前租户/农场 ID（V1 全 '1001'）；取不到 → 默认 '1001'。 */
    private String currentTenant() {
        try {
            String t = TenantHelper.getTenantId();
            return StringUtils.isNotBlank(t) ? t : DEFAULT_TENANT;
        } catch (Exception e) {
            return DEFAULT_TENANT;
        }
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
        // 品种/品系名优先取 t_farm_breed_info 主数据（外部引种 BreedInfoPicker 写的 2 位码如 "01"），
        // 原始 code 回落（旧静态字典已废）；与列表 PigCoreServiceImpl 同口径，避免品系显示成纯 code。
        Map<String, String> breedNameMap = breedStrainNameMap(1);
        Map<String, String> strainNameMap = breedStrainNameMap(2);

        // 母系：本猪 mother_ear → 反查母猪只
        Pig dam = findByEarNo(pig.getMotherEar());
        if (dam != null) {
            vo.setDamPigId(dam.getId());
            vo.setDamEarNo(dam.getEarNo());
            vo.setDamBreed(resolveBreedStrainName(breedNameMap, dam.getPigBreedCode()));
            vo.setDamStrain(resolveBreedStrainName(strainNameMap, dam.getPigStrainCode()));
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
            vo.setSireBreed(resolveBreedStrainName(breedNameMap, sire.getPigBreedCode()));
            vo.setSireStrain(resolveBreedStrainName(strainNameMap, sire.getPigStrainCode()));
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
    /** 通用字典翻译；翻不到回落 code，code 空 → null。 */
    private String translateDict(String dictType, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String label = dictService.getDictLabel(dictType, code);
        return StringUtils.isNotBlank(label) ? label : code;
    }

    /** t_farm_breed_info 主数据 code→中文名 映射（breedStrain 1=品种 / 2=品系）；谱系卡两系各预载一次。 */
    private Map<String, String> breedStrainNameMap(Integer breedStrain) {
        return breedInfoMapper.selectList(new LambdaQueryWrapper<BreedInfo>()
                .eq(BreedInfo::getBreedStrain, breedStrain)
                .eq(BreedInfo::getDelFlag, "0"))
            .stream()
            .filter(b -> StringUtils.isNotBlank(b.getBreedStrainCode()) && StringUtils.isNotBlank(b.getBreedStrainName()))
            .collect(Collectors.toMap(BreedInfo::getBreedStrainCode, BreedInfo::getBreedStrainName, (a, b) -> a));
    }

    /** 品种/品系名解析：主数据优先（2 位码权威名）→ 字典回落（历史 1 位码）→ 原始 code；与列表口径一致。 */
    private String resolveBreedStrainName(Map<String, String> infoNameMap, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String name = infoNameMap.get(code);
        return StringUtils.isNotBlank(name) ? name : code;
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

    /** BigDecimal 保留 3 位（HALF_UP）；null 透传 null。与夜间汇总批口径一致。 */
    private BigDecimal scale3(BigDecimal v) {
        return v != null ? v.setScale(3, RoundingMode.HALF_UP) : null;
    }

    private int nz(Integer v) {
        return Optional.ofNullable(v).orElse(0);
    }
}
