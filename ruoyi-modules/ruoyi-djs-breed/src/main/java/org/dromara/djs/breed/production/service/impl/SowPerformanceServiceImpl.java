package org.dromara.djs.breed.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.SowPerformanceMpVo;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.mapper.SowDetailAggMapper;
import org.dromara.djs.breed.core.service.ISowDetailService;
import org.dromara.djs.breed.production.domain.SowPerformance;
import org.dromara.djs.breed.production.domain.vo.SowPerformanceVo;
import org.dromara.djs.breed.production.mapper.SowPerformanceMapper;
import org.dromara.djs.breed.production.service.ISowPerformanceService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * 母猪生产指标 Service 实现（BRD-LIST-001 详情 tab2，只读）。
 *
 * @author djs
 * @since BRD-LIST-001
 */
@Service
@RequiredArgsConstructor
public class SowPerformanceServiceImpl implements ISowPerformanceService {

    private final SowPerformanceMapper sowPerformanceMapper;
    private final SowDetailAggMapper sowDetailAggMapper;
    private final PigMapper pigMapper;
    private final ISowDetailService sowDetailService;

    @Override
    public List<SowPerformanceVo> listByPigId(Long pigId) {
        if (pigId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SowPerformance> w = new LambdaQueryWrapper<SowPerformance>()
            .eq(SowPerformance::getPigId, pigId)
            .orderByDesc(SowPerformance::getParity);
        List<SowPerformanceVo> persisted = sowPerformanceMapper.selectVoList(w);
        if (persisted != null && !persisted.isEmpty()) {
            return persisted;
        }

        // 夜间 T-1 汇总尚未覆盖的新分娩：只读实时聚合，不在 GET 中写表或触发整套 dashboard 作业。
        // 未分娩母猪仍保持原空态，避免把全 0 误呈现为一条生产指标。
        if (sowDetailAggMapper.countFarrowRecords(pigId) <= 0) {
            return Collections.emptyList();
        }
        Pig pig = pigMapper.selectById(pigId);
        if (pig == null || !"sow".equals(pig.getPigType())) {
            return Collections.emptyList();
        }
        SowPerformanceMpVo live = sowDetailService.querySowPerformance(pigId);
        if (live == null) {
            return Collections.emptyList();
        }
        return List.of(toAdminVo(pig, live));
    }

    /** 小程序与 admin 共用实时指标结果，仅补 admin 表格所需的猪只快照字段。 */
    private SowPerformanceVo toAdminVo(Pig pig, SowPerformanceMpVo live) {
        SowPerformanceVo vo = new SowPerformanceVo();
        vo.setPigId(pig.getId());
        vo.setEarNo(pig.getEarNo());
        vo.setParity(pig.getParity());
        vo.setTotalBorn(live.getTotalBorn());
        vo.setTotalLiveBorn(live.getTotalLiveBorn());
        vo.setTotalWeaned(live.getTotalWeaned());
        vo.setAvgBornWeight(live.getAvgBornWeight());
        vo.setAvgWeanedWeight(live.getAvgWeanedWeight());
        vo.setAvgGestationDays(live.getAvgGestationDays());
        vo.setWeanBreedDays(live.getWeanBreedDays());
        vo.setAbnormalTotal(live.getAbnormalTotal());
        vo.setAvgBornPerLitter(live.getAvgBornPerLitter());
        vo.setAvgLiveBornPerLitter(live.getAvgLiveBornPerLitter());
        vo.setAvgWeanedPerLitter(live.getAvgWeanedPerLitter());
        vo.setNpd(live.getNpd());
        vo.setLastUpdateDate(LocalDate.now());
        return vo;
    }
}
