package org.dromara.djs.breed.core.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.InventoryBarnMatrixVo;
import org.dromara.djs.breed.core.domain.vo.InventoryBoarItemVo;
import org.dromara.djs.breed.core.domain.vo.InventoryDistItemVo;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IInventoryAppletService;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

    /** 后备段标记：前端传 pigType=reserve，后端按 current_status='HB' 过滤（非 pig_type） */
    private static final String RESERVE = "reserve";
    private static final String RESERVE_STATUS = "HB";
    private static final String SOW = "sow";
    private static final String BOAR = "boar";

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
        // 母猪段 byStatus 维度 = current_status；其它段无细分状态 → byStatus 留空（mp 仅展示头数）
        boolean withStatus = SOW.equals(pigType);

        Map<Long, Map<String, Integer>> matrix = new LinkedHashMap<>();
        Map<Long, Integer> totalByBarn = new LinkedHashMap<>();
        for (Pig p : pigs) {
            if (p.getBarnId() == null) {
                continue;
            }
            Long barnId = p.getBarnId();
            totalByBarn.merge(barnId, 1, Integer::sum);
            if (withStatus) {
                String status = p.getCurrentStatus();
                if (status != null && !status.isEmpty()) {
                    matrix.computeIfAbsent(barnId, k -> new LinkedHashMap<>())
                          .merge(status, 1, Integer::sum);
                }
            }
        }
        if (totalByBarn.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, String> barnNameMap = resolveBarnNames(totalByBarn.keySet());

        List<InventoryBarnMatrixVo> result = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : totalByBarn.entrySet()) {
            InventoryBarnMatrixVo vo = new InventoryBarnMatrixVo();
            vo.setBarnId(String.valueOf(e.getKey()));
            vo.setBarnName(barnNameMap.getOrDefault(e.getKey(), "未分配"));
            vo.setCount(e.getValue());
            vo.setByStatus(matrix.getOrDefault(e.getKey(), Collections.emptyMap()));
            result.add(vo);
        }
        return result;
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
        String[] bucketLabels = {"0-30天", "31-60天", "61-90天", "91-180天", "180天以上"};
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
            counts[bucketIndex(days)]++;
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

    private int bucketIndex(long days) {
        if (days <= 30) {
            return 0;
        } else if (days <= 60) {
            return 1;
        } else if (days <= 90) {
            return 2;
        } else if (days <= 180) {
            return 3;
        }
        return 4;
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

        List<InventoryBoarItemVo> result = new ArrayList<>();
        for (Pig p : boars) {
            InventoryBoarItemVo vo = new InventoryBoarItemVo();
            vo.setPigId(String.valueOf(p.getId()));
            vo.setEarNo(p.getEarNo());
            vo.setBreedCode(p.getPigBreedCode());
            vo.setBarnName(p.getBarnId() == null ? "未分配"
                : barnNameMap.getOrDefault(p.getBarnId(), "未分配"));
            vo.setEntryDate(formatDate(p.getIntroduceDate()));
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
}
