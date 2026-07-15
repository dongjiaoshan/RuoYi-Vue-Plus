package org.dromara.djs.breed.core.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.InventoryBarnMatrixVo;
import org.dromara.djs.breed.core.domain.vo.InventoryBoarItemVo;
import org.dromara.djs.breed.core.domain.vo.InventoryDistItemVo;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IInventoryAppletService;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.breeding.mapper.PigBreedingMapper;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.production.domain.vo.FattenAgeStageVo;
import org.dromara.djs.breed.production.service.IFattenAgeStageService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 库存看板聚合服务实现 - 小程序端（BRD-INVENTORY-001）。
 *
 * <p>独立 service（@RequiredArgsConstructor），不改动现有 ServiceImpl 构造器，
 * 避免连带改单测（参考波次2-B SowDetail 范式）。read-only 纯 query 聚合，无 DDL。</p>
 *
 * <p>字段映射（t_farm_pig_info / Pig 实体）：lifecycle = current_status；
 * 品种 = pig_breed_code；胎次 = parity(Integer)；出生日期 = birthDate(LocalDate)；
 * 引种日期 = introduceDate(LocalDate)。Barn 主键 = id，名 = barnName。</p>
 */
@Service
@RequiredArgsConstructor
public class InventoryAppletServiceImpl implements IInventoryAppletService {

    private final PigMapper pigMapper;
    private final BarnMapper barnMapper;
    private final IFattenAgeStageService fattenAgeStageService;
    private final PigBreedingMapper pigBreedingMapper;
    /** 品种/品系 code→中文名解析（复用 PigCore 口径：t_farm_breed_info 主表优先 → 字典回落 → code 回落）。 */
    private final IPigCoreService pigCoreService;


    /** 后备段标记：前端传 pigType=reserve，后端按 current_status='HB' 过滤（非 pig_type） */
    private static final String RESERVE = "reserve";
    private static final String RESERVE_STATUS = "HB";
    private static final String SOW = "sow";
    private static final String BOAR = "boar";
    private static final String PIGLET = "piglet";
    private static final String FATTENING = "fattening";

    /** 离场状态：库存分布栋舍卡按客户口径排除（不计头数、不计状态） */
    private static final String STATUS_END = "END";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 按 pigType 段拉取活猪。reserve 段走 current_status='HB'；其它段走 pig_type。
     */
    private List<Pig> loadPigs(String pigType) {
        var wrapper = Wrappers.<Pig>lambdaQuery().eq(Pig::getDelFlag, "0");
        if (RESERVE.equals(pigType)) {
            wrapper.eq(Pig::getCurrentStatus, RESERVE_STATUS);
        } else if (pigType != null && !pigType.isEmpty()) {
            wrapper.eq(Pig::getPigType, pigType);
        }
        return pigMapper.selectList(wrapper);
    }

    @Override
    public List<InventoryBarnMatrixVo> barnMatrix(String pigType) {
        List<Pig> pigs = loadPigs(pigType);
        if (pigs.isEmpty()) {
            return Collections.emptyList();
        }
        // 母猪段 byStatus 维度 = current_status；肥猪/仔猪/后备段 byAge 维度 = 日龄/周龄分段；
        boolean withStatus = SOW.equals(pigType);
        boolean withAge = FATTENING.equals(pigType) || PIGLET.equals(pigType) || RESERVE.equals(pigType);

        // 肥猪/仔猪/后备分段器（一次性算好，全栋复用，保证段顺序与日龄分布图一致）
        Bucketing bucketing = withAge ? bucketingFor(pigType) : null;
        String[] ageLabels = withAge ? bucketing.labels() : null;
        LocalDate today = LocalDate.now();

        Map<Long, Map<String, Integer>> statusMatrix = new LinkedHashMap<>();
        Map<Long, int[]> ageMatrix = new LinkedHashMap<>();
        Map<Long, Integer> totalByBarn = new LinkedHashMap<>();
        for (Pig p : pigs) {
            if (p.getBarnId() == null) {
                continue;
            }
            String status = p.getCurrentStatus();
            // 客户口径（206③）：库存分布栋舍卡排除离场 END（不计头数、不计分段）
            if (STATUS_END.equals(status)) {
                continue;
            }
            Long barnId = p.getBarnId();
            totalByBarn.merge(barnId, 1, Integer::sum);
            if (withStatus) {
                if (status != null && !status.isEmpty()) {
                    statusMatrix.computeIfAbsent(barnId, k -> new LinkedHashMap<>())
                          .merge(status, 1, Integer::sum);
                }
            } else if (withAge) {
                LocalDate birthDate = p.getBirthDate();
                if (birthDate == null) {
                    continue;
                }
                long days = ChronoUnit.DAYS.between(birthDate, today);
                if (days < 0) {
                    continue;
                }
                int[] buckets = ageMatrix.computeIfAbsent(barnId, k -> new int[ageLabels.length]);
                buckets[bucketing.indexOf(days)]++;
            }
        }
        if (totalByBarn.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, String> barnNameMap = resolveBarnNames(totalByBarn.keySet());

        List<InventoryBarnMatrixVo> result = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : totalByBarn.entrySet()) {
            Long barnId = e.getKey();
            InventoryBarnMatrixVo vo = new InventoryBarnMatrixVo();
            vo.setBarnId(String.valueOf(barnId));
            vo.setBarnName(barnNameMap.getOrDefault(barnId, "未分配"));
            vo.setCount(e.getValue());
            vo.setByStatus(statusMatrix.getOrDefault(barnId, Collections.emptyMap()));
            if (withAge) {
                vo.setByAge(toAgeMap(ageLabels, ageMatrix.get(barnId)));
            } else {
                vo.setByAge(Collections.emptyMap());
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 把某栋舍的分段计数数组装成有序 label→count（按分段标签顺序，6/7 段全列，缺省 0）。
     * 与原型一致：每段都显示（含 0），保证各栋卡片分段位置对齐。
     */
    private Map<String, Integer> toAgeMap(String[] labels, int[] counts) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < labels.length; i++) {
            map.put(labels[i], counts == null ? 0 : counts[i]);
        }
        return map;
    }

    @Override
    public List<InventoryDistItemVo> parityDist(String pigType) {
        // 胎次仅母猪有意义
        if (!SOW.equals(pigType)) {
            return Collections.emptyList();
        }
        List<Pig> pigs = loadPigs(pigType);
        // TreeMap 保证胎次升序
        Map<Integer, Integer> byParity = new TreeMap<>();
        for (Pig p : pigs) {
            Integer parity = p.getParity();
            if (parity == null) {
                continue;
            }
            byParity.merge(parity, 1, Integer::sum);
        }
        if (byParity.isEmpty()) {
            return Collections.emptyList();
        }
        List<InventoryDistItemVo> result = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : byParity.entrySet()) {
            InventoryDistItemVo item = new InventoryDistItemVo();
            item.setLabel(e.getKey() + "胎");
            item.setCount(e.getValue());
            result.add(item);
        }
        return result;
    }

    @Override
    public List<InventoryDistItemVo> ageDist(String pigType) {
        List<Pig> pigs = loadPigs(pigType);
        LocalDate today = LocalDate.now();
        Bucketing bucketing = bucketingFor(pigType);
        String[] bucketLabels = bucketing.labels();
        int[] counts = new int[bucketLabels.length];
        boolean any = false;
        for (Pig p : pigs) {
            LocalDate birthDate = p.getBirthDate();
            if (birthDate == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(birthDate, today);
            if (days < 0) {
                continue;
            }
            counts[bucketing.indexOf(days)]++;
            any = true;
        }
        if (!any) {
            return Collections.emptyList();
        }
        List<InventoryDistItemVo> result = new ArrayList<>();
        for (int i = 0; i < bucketLabels.length; i++) {
            InventoryDistItemVo item = new InventoryDistItemVo();
            item.setLabel(bucketLabels[i]);
            item.setCount(counts[i]);
            result.add(item);
        }
        return result;
    }

    /**
     * 分桶器：labels 与 indexOf 同源，保证日龄分布图与库存分布按段细分口径完全一致。
     *
     * @param labels    分段中文标签（图表 X 轴 / 栋舍卡分段 key）
     * @param upper     第 i 段上界（含，天数 ≤ upper[i] 落第 i 段）；最后一段为兜底 → 不参与比较
     */
    private record Bucketing(String[] labels, long[] upper) {
        int indexOf(long days) {
            // 最后一段为「X 以上」兜底，只比前 n-1 段上界
            for (int i = 0; i < upper.length; i++) {
                if (days <= upper[i]) {
                    return i;
                }
            }
            return labels.length - 1;
        }
    }

    /**
     * 各 pigType 的分桶器（标签 + 上界一体，杜绝 labels/index 两处口径漂移）。
     *
     * <ul>
     *   <li>仔猪：按日龄 6 段（1-5/6-10/11-15/16-20/21-25/25日以上，客户 6/14 反馈）。</li>
     *   <li>后备：按周龄 6 段（15周以下/16-20/21-25/26-30/31-35/36周以上，客户 6/14 反馈）。
     *       末段口径与原型对齐为「36周以上」。</li>
     *   <li>肥猪：动态读后台「育肥日龄阶段配置」{@code t_farm_fatten_age_stage}（客户原文「取后台生产配置里的日龄分布」）：
     *       配置 n 行 → 「保育期」(< 首行起始) + n 个「{start}-{end}日」段 + 「{末行截止}日以上」共 n+2 段。
     *       配置为空时退回默认日龄 5 段。</li>
     *   <li>其它：默认日龄 5 段。</li>
     * </ul>
     */
    private Bucketing bucketingFor(String pigType) {
        if (PIGLET.equals(pigType)) {
            return new Bucketing(
                new String[]{"1-5日", "6-10日", "11-15日", "16-20日", "21-25日", "25日以上"},
                new long[]{5, 10, 15, 20, 25}
            );
        }
        if (RESERVE.equals(pigType)) {
            // 周龄分段：上界用「天数」表达（weeks*7 + 6，保证整周内全部落同段）
            return new Bucketing(
                new String[]{"15周以下", "16-20周", "21-25周", "26-30周", "31-35周", "36周以上"},
                new long[]{15 * 7 + 6, 20 * 7 + 6, 25 * 7 + 6, 30 * 7 + 6, 35 * 7 + 6}
            );
        }
        if (FATTENING.equals(pigType)) {
            Bucketing fatten = fattenBucketing();
            if (fatten != null) {
                return fatten;
            }
        }
        return new Bucketing(
            new String[]{"0-30天", "31-60天", "61-90天", "91-180天", "180天以上"},
            new long[]{30, 60, 90, 180}
        );
    }

    /**
     * 从「育肥日龄阶段配置」动态构建肥猪分桶器。配置缺失（空表）返回 null → 上层退回默认 5 段。
     */
    private Bucketing fattenBucketing() {
        List<FattenAgeStageVo> stages = fattenAgeStageService.queryList();
        if (stages == null || stages.isEmpty()) {
            return null;
        }
        // queryList 已按 start_age 升序；过滤掉 start/end 缺失的脏行
        List<FattenAgeStageVo> valid = new ArrayList<>();
        for (FattenAgeStageVo s : stages) {
            if (s.getStartAge() != null && s.getEndAge() != null && s.getEndAge() >= s.getStartAge()) {
                valid.add(s);
            }
        }
        if (valid.isEmpty()) {
            return null;
        }
        int lastEnd = valid.get(valid.size() - 1).getEndAge();

        // 段：n 个配置段 + 「{末段截止}日以上」（育肥猪无「保育期」阶段 —— 6/15 测试 row63：
        // 去掉首档「保育期」恒 0 列；小于首段起始的极少数低日龄并入首个配置段，不丢数据不显保育期）。
        List<String> labels = new ArrayList<>();
        List<Long> upper = new ArrayList<>();
        for (FattenAgeStageVo s : valid) {
            labels.add(s.getStartAge() + "-" + s.getEndAge() + "日");
            upper.add((long) (int) s.getEndAge());
        }
        labels.add(lastEnd + "日以上"); // 兜底段（不入 upper，indexOf 末段）

        long[] upperArr = new long[upper.size()];
        for (int i = 0; i < upper.size(); i++) {
            upperArr[i] = upper.get(i);
        }
        return new Bucketing(labels.toArray(new String[0]), upperArr);
    }

    @Override
    public List<InventoryBoarItemVo> boarList() {
        List<Pig> boars = pigMapper.selectList(
            Wrappers.<Pig>lambdaQuery()
                .eq(Pig::getDelFlag, "0")
                .eq(Pig::getPigType, BOAR)
                .orderByAsc(Pig::getEarNo)
        );
        if (boars.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> barnIds = boars.stream()
            .map(Pig::getBarnId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, String> barnNameMap = resolveBarnNames(barnIds);
        // 配种次数 + 最近配种日期：按本批公猪耳号一次性聚合（t_farm_pig_breeding，row210）
        Map<String, int[]> matingCountByEar = new LinkedHashMap<>();
        Map<String, LocalDate> lastMatingByEar = new LinkedHashMap<>();
        List<String> earNos = boars.stream()
            .map(Pig::getEarNo)
            .filter(e -> e != null && !e.isEmpty())
            .distinct()
            .toList();
        if (!earNos.isEmpty()) {
            for (Map<String, Object> row : pigBreedingMapper.aggregateMatingByBoarEarNo(earNos)) {
                Object earNoObj = row.get("boarEarNo");
                if (earNoObj == null) {
                    continue;
                }
                String earNo = String.valueOf(earNoObj);
                Object cnt = row.get("matingCount");
                matingCountByEar.put(earNo, new int[]{cnt == null ? 0 : ((Number) cnt).intValue()});
                LocalDate last = toLocalDate(row.get("lastMatingDate"));
                if (last != null) {
                    lastMatingByEar.put(earNo, last);
                }
            }
        }
        LocalDate today = LocalDate.now();
        // 品种/品系 code→中文名映射批量预载一次（防 N+1），口径与 PigCore 一致：
        // t_farm_breed_info 主表（客户配的权威名，如 04=国寿黑）优先 → 字典回落 → 原 code 回落。
        Map<String, String> breedNameMap = pigCoreService.loadBreedStrainNameMap(1);
        Map<String, String> strainNameMap = pigCoreService.loadBreedStrainNameMap(2);

        List<InventoryBoarItemVo> result = new ArrayList<>();
        for (Pig p : boars) {
            InventoryBoarItemVo vo = new InventoryBoarItemVo();
            vo.setPigId(String.valueOf(p.getId()));
            vo.setEarNo(p.getEarNo());
            vo.setBreedCode(p.getPigBreedCode());
            vo.setStrainCode(p.getPigStrainCode());
            vo.setBreedName(pigCoreService.resolveBreedStrainName(breedNameMap, p.getPigBreedCode()));
            vo.setStrainName(pigCoreService.resolveBreedStrainName(strainNameMap, p.getPigStrainCode()));
            vo.setBarnName(p.getBarnId() == null ? "未分配"
                : barnNameMap.getOrDefault(p.getBarnId(), "未分配"));
            vo.setAgeDays(p.getBirthDate() == null ? null
                : (int) ChronoUnit.DAYS.between(p.getBirthDate(), today));
            vo.setEntryDate(formatDate(p.getIntroduceDate()));
            int[] cnt = p.getEarNo() == null ? null : matingCountByEar.get(p.getEarNo());
            vo.setMatingCount(cnt == null ? 0 : cnt[0]);
            vo.setLastMatingDate(p.getEarNo() == null ? null
                : formatDate(lastMatingByEar.get(p.getEarNo())));
            result.add(vo);
        }
        return result;
    }

    /** 批量解析栋舍名（空集合安全返回空 map）。Barn 主键 = id。 */
    private Map<Long, String> resolveBarnNames(Collection<Long> barnIds) {
        if (barnIds == null || barnIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Barn> barns = barnMapper.selectBatchIds(barnIds);
        Map<Long, String> map = new LinkedHashMap<>();
        for (Barn b : barns) {
            map.put(b.getId(), b.getBarnName());
        }
        return map;
    }

    private String formatDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(DATE_FMT);
    }

    /**
     * 把 MyBatis 通用 Map 取出的日期对象（MAX(datetime) 聚合可能是 LocalDateTime / LocalDate /
     * java.sql.Timestamp / java.util.Date）统一转 LocalDate；无法识别返回 null。
     */
    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.toLocalDate();
        }
        if (value instanceof LocalDate ld) {
            return ld;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }
}
