package org.dromara.djs.plant.perf.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.plant.perf.domain.PlantWorkPerformance;
import org.dromara.djs.plant.perf.domain.query.PlantWorkPerformanceQuery;
import org.dromara.djs.plant.perf.domain.vo.PerfAggRow;
import org.dromara.djs.plant.perf.domain.vo.PlantWorkPerformanceVo;
import org.dromara.djs.plant.perf.mapper.PlantWorkPerformanceMapper;
import org.dromara.djs.plant.perf.service.IPlantWorkPerformanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 班组绩效结算 Service 实现（PLT-PERF-001）。
 *
 * <p>核心 {@code generate(statMonth)}：幂等软删该月旧行 → 聚合 details.actual_yield →
 * 读 crop.pick_unit_price 作单价快照算金额 → 批量 INSERT。
 * 单价取快照（不实时 JOIN），后续改价不污染历史月。</p>
 *
 * @author djs
 * @since PLT-PERF-001
 */
@Slf4j
@Service
public class PlantWorkPerformanceServiceImpl
    extends DjsBaseServiceImpl<PlantWorkPerformanceMapper, PlantWorkPerformance>
    implements IPlantWorkPerformanceService {

    /**
     * 统计月份格式 yyyy-MM。
     */
    private static final Pattern STAT_MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");

    /**
     * 软删值（区别于普通 del_flag='1'：'2'=被重新生成覆盖）。MP @TableLogic 仍按 '0' 过滤活动行。
     */
    private static final String DEL_FLAG_SUPERSEDED = "2";

    public PlantWorkPerformanceServiceImpl(PlantWorkPerformanceMapper baseMapper) {
        super(baseMapper);
    }

    @Override
    public TableDataInfo<PlantWorkPerformanceVo> queryPageList(PlantWorkPerformanceQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PlantWorkPerformance> wrapper = buildQueryWrapper(query);
        Page<PlantWorkPerformanceVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrich(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PlantWorkPerformanceVo> queryList(PlantWorkPerformanceQuery query) {
        List<PlantWorkPerformanceVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        enrich(list);
        return list;
    }

    @Override
    public PlantWorkPerformanceVo queryById(Long id) {
        PlantWorkPerformanceVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            enrich(Collections.singletonList(vo));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generate(String statMonth) {
        if (StringUtils.isBlank(statMonth) || !STAT_MONTH_PATTERN.matcher(statMonth).matches()) {
            throw new ServiceException("统计月份格式必须为 yyyy-MM（如 2026-04）");
        }
        // 1. 幂等清理：软删该月已有结算行（del_flag '0' → '2'）
        Long updateBy = currentUserIdSafe();
        Date now = new Date();
        UpdateWrapper<PlantWorkPerformance> clearWrapper = Wrappers.<PlantWorkPerformance>update()
            .eq("stat_month", statMonth)
            .eq("del_flag", "0")
            .set("del_flag", DEL_FLAG_SUPERSEDED)
            .set("update_by", updateBy)
            .set("update_time", now);
        baseMapper.update(null, clearWrapper);

        // 2. 聚合该月 班组 × 作物 采摘总量
        List<PerfAggRow> aggRows = baseMapper.aggregateByMonth(statMonth);
        if (aggRows.isEmpty()) {
            return 0;
        }

        // 3. 批量取作物单价快照（一次查询）
        Set<Long> cropIds = aggRows.stream()
            .map(PerfAggRow::getCropId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
        Map<Long, BigDecimal> cropPriceMap = loadCropPrices(cropIds);

        // 4. 逐组算金额 + 批量 INSERT
        int inserted = 0;
        List<PlantWorkPerformance> rows = new ArrayList<>(aggRows.size());
        for (PerfAggRow agg : aggRows) {
            BigDecimal pickWeight = agg.getPickWeight() != null ? agg.getPickWeight() : BigDecimal.ZERO;
            BigDecimal unitPrice = cropPriceMap.getOrDefault(agg.getCropId(), BigDecimal.ZERO);
            // 金额 = 采摘总量 × 单价快照，保留 2 位（元）
            BigDecimal amount = pickWeight.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

            PlantWorkPerformance row = new PlantWorkPerformance();
            row.setStatMonth(statMonth);
            row.setTeamId(agg.getTeamId());
            row.setCropId(agg.getCropId());
            row.setPickWeight(pickWeight);
            row.setUnitPriceSnapshot(unitPrice);
            row.setPerformanceAmount(amount);
            row.setPerformanceRule(unitPrice.stripTrailingZeros().toPlainString() + " 元/斤");
            // del_flag / tenant_id / 审计字段走 MP MetaObjectHandler + @TableLogic 默认，不手工赋 tenant_id
            rows.add(row);
        }
        for (PlantWorkPerformance row : rows) {
            inserted += baseMapper.insert(row);
        }
        return inserted;
    }

    /**
     * 批量取作物单价快照映射。
     */
    private Map<Long, BigDecimal> loadCropPrices(Set<Long> cropIds) {
        Map<Long, BigDecimal> map = new HashMap<>();
        if (cropIds.isEmpty()) {
            return map;
        }
        List<Map<String, Object>> priceRows = baseMapper.selectCropUnitPrices(cropIds);
        for (Map<String, Object> r : priceRows) {
            Object idObj = r.get("cropId");
            Object priceObj = r.get("pickUnitPrice");
            if (idObj instanceof Number nid && priceObj instanceof BigDecimal price) {
                map.put(nid.longValue(), price);
            } else if (idObj instanceof Number nid && priceObj != null) {
                map.put(nid.longValue(), new BigDecimal(priceObj.toString()));
            }
        }
        return map;
    }

    /**
     * 批量给 VO 补 teamName / cropName（避免列表 N+1）。
     */
    private void enrich(List<PlantWorkPerformanceVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Long> teamIds = list.stream()
            .map(PlantWorkPerformanceVo::getTeamId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> teamNameMap = new HashMap<>();
        if (!teamIds.isEmpty()) {
            for (Map<String, Object> r : baseMapper.selectTeamNames(teamIds)) {
                if (r.get("teamId") instanceof Number n) {
                    teamNameMap.put(n.longValue(), (String) r.get("teamName"));
                }
            }
        }
        Set<Long> cropIds = list.stream()
            .map(PlantWorkPerformanceVo::getCropId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> cropNameMap = new HashMap<>();
        if (!cropIds.isEmpty()) {
            for (Map<String, Object> r : baseMapper.selectCropNames(cropIds)) {
                if (r.get("cropId") instanceof Number n) {
                    cropNameMap.put(n.longValue(), (String) r.get("cropName"));
                }
            }
        }
        for (PlantWorkPerformanceVo vo : list) {
            if (vo.getTeamId() != null) {
                vo.setTeamName(teamNameMap.get(vo.getTeamId()));
            }
            if (vo.getCropId() != null) {
                vo.setCropName(cropNameMap.get(vo.getCropId()));
            }
        }
    }

    private LambdaQueryWrapper<PlantWorkPerformance> buildQueryWrapper(PlantWorkPerformanceQuery query) {
        LambdaQueryWrapper<PlantWorkPerformance> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PlantWorkPerformance::getStatMonth).orderByAsc(PlantWorkPerformance::getTeamId);
        }
        wrapper.eq(StringUtils.isNotBlank(query.getStatMonth()), PlantWorkPerformance::getStatMonth, query.getStatMonth())
            .eq(query.getTeamId() != null, PlantWorkPerformance::getTeamId, query.getTeamId())
            .eq(query.getCropId() != null, PlantWorkPerformance::getCropId, query.getCropId())
            .orderByDesc(PlantWorkPerformance::getStatMonth)
            .orderByAsc(PlantWorkPerformance::getTeamId);
        return wrapper;
    }
}
